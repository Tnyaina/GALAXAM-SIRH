package mg.galaxam.sirh.model.pointage.engine;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul des règles de pointage, présence, compensation et majorations.
 * Réf. Code du Travail Malagasy & Conventions d'entreprise GALAXAM.
 */
@Component
public class PointageCalculateurEngine {

    public static final LocalTime SEUIL_NUIT_DEFAUT = LocalTime.of(22, 0);
    public static final LocalTime SEUIL_2ND_REPAS_1 = LocalTime.of(19, 30);
    public static final LocalTime SEUIL_2ND_REPAS_2 = LocalTime.of(21, 0);

    /**
     * Calcule le retard exact en minutes avec tolérance zéro.
     */
    public int calculerMinutesRetard(LocalTime arrivee, LocalTime debutTheorique, LocalTime plafondTolerance) {
        if (arrivee == null || debutTheorique == null) return 0;

        // Arrivée avant le plafond (ex: 07:15 alors que plafond = 07:30) -> non pris en compte pour avancer
        // Arrivée après l'heure de début théorique (ex: 08:05 vs 08:00) -> Retard
        if (arrivee.isAfter(debutTheorique)) {
            return (int) Duration.between(debutTheorique, arrivee).toMinutes();
        }
        return 0;
    }

    /**
     * Détecte et calcule les heures de nuit (au-delà de 22h00).
     */
    public double calculerHeuresNuit(LocalTime sortie, LocalTime seuilNuit) {
        if (sortie == null) return 0.0;
        LocalTime seuil = seuilNuit != null ? seuilNuit : SEUIL_NUIT_DEFAUT;
        if (sortie.isAfter(seuil)) {
            long mins = Duration.between(seuil, sortie).toMinutes();
            return Math.round((mins / 60.0) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Calcule le montant net du repas journalier incluant les majorations pour sortie tardive.
     */
    public double calculerTotalRepasJour(boolean repasMidiCoche, double montantMidi, LocalTime sortieSoir,
                                         double primeTardif1, double primeTardif2) {
        double total = repasMidiCoche ? montantMidi : 0.0;
        if (sortieSoir != null) {
            if (sortieSoir.isAfter(SEUIL_2ND_REPAS_2)) {
                total += primeTardif2;
            } else if (sortieSoir.isAfter(SEUIL_2ND_REPAS_1)) {
                total += primeTardif1;
            }
        }
        return total;
    }

    /**
     * Calcule l'indemnité nette de déplacement journalière.
     */
    public double calculerDeplacementJour(boolean trajetMatin, boolean trajetSoir, double montantParTrajet,
                                          boolean aPrisRepasSurSite, double reductionSiRepasSurSite) {
        int nbTrajets = (trajetMatin ? 1 : 0) + (trajetSoir ? 1 : 0);
        double brut = nbTrajets * montantParTrajet;
        if (aPrisRepasSurSite && reductionSiRepasSurSite > 0 && brut > 0) {
            brut = Math.max(0.0, brut - reductionSiRepasSurSite);
        }
        return Math.round(brut * 100.0) / 100.0;
    }

    /**
     * Calcule le bilan d'un tronçon hebdomadaire (Semaine ISO / 40h).
     */
    public BilanTronconHebdo calculerTroncon(
            LocalDate debutSemaine,
            int nbJoursOuvres,
            double totalHeures,
            double minutesRetard,
            boolean isCadre,
            double seuilHeuresJour,
            double seuilHsTranche1
    ) {
        double seuilTroncon = nbJoursOuvres * seuilHeuresJour;
        double heuresRetard = Math.round((minutesRetard / 60.0) * 100.0) / 100.0;
        double hsBrutes = Math.max(0.0, Math.round((totalHeures - seuilTroncon) * 100.0) / 100.0);

        // Compensation 1:1 des retards par le surplus d'heures de la semaine
        double retardCompense = Math.min(heuresRetard, hsBrutes);
        double deficitNet = Math.round((heuresRetard - retardCompense) * 100.0) / 100.0;

        // Pour les cadres : forfait contractuel, 0 HS payée, mais compensation active
        double hsNettes = isCadre ? 0.0 : Math.round((hsBrutes - retardCompense) * 100.0) / 100.0;

        // Ventilation HS 30% (jusqu'à seuilHsTranche1) et HS 50% (au-delà)
        double hs30 = Math.min(hsNettes, seuilHsTranche1);
        double hs50 = Math.max(0.0, Math.round((hsNettes - seuilHsTranche1) * 100.0) / 100.0);

        return new BilanTronconHebdo(
                debutSemaine,
                nbJoursOuvres,
                seuilTroncon,
                totalHeures,
                heuresRetard,
                hsBrutes,
                retardCompense,
                hsNettes,
                deficitNet,
                hs30,
                hs50
        );
    }

    public record BilanTronconHebdo(
            LocalDate debutSemaine,
            int nbJoursOuvres,
            double seuilTroncon,
            double totalHeures,
            double heuresRetard,
            double hsBrutes,
            double retardCompense,
            double hsNettes,
            double deficitNet,
            double hs30,
            double hs50
    ) {}
}
