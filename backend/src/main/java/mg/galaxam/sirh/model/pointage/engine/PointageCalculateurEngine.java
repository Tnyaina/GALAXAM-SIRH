package mg.galaxam.sirh.model.pointage.engine;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul des règles de pointage, présence, compensation et majorations.
 * Réf. Code du Travail Malagasy & Conventions d'entreprise GALAXAM.
 * Tous les calculs sont pilotés dynamiquement par les paramètres globaux (table parametre_global).
 */
@Component
public class PointageCalculateurEngine {

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
     * Écrête l'heure d'arrivée matinale au plafond autorisé (ref_tolerance_arrivee_pointage).
     * Si l'employé arrive avant le plafond (ex: 07:21 alors que le plafond est 07:30),
     * l'heure retenue pour le calcul du temps effectif est ramenée au plafond (07:30).
     *
     * @param arrivee        heure réelle d'arrivée (ex: 07:21)
     * @param heurePlafond   seuil minimal autorisé (ex: 07:30)
     * @return heure effective d'arrivée pour le calcul des heures travaillées
     */
    public LocalTime ecreterArriveeAuPlafond(LocalTime arrivee, LocalTime heurePlafond) {
        if (arrivee == null) return null;
        if (heurePlafond != null && arrivee.isBefore(heurePlafond)) {
            return heurePlafond;
        }
        return arrivee;
    }

    /**
     * Détecte et calcule les heures de nuit selon les seuils configurés.
     */
    public double calculerHeuresNuit(LocalTime sortie, LocalTime debutNuit, LocalTime finNuit) {
        if (sortie == null) return 0.0;
        LocalTime seuilDebut = debutNuit != null ? debutNuit : LocalTime.of(22, 0);
        LocalTime seuilFin = finNuit != null ? finNuit : LocalTime.of(5, 0);

        if (sortie.isAfter(seuilDebut)) {
            long mins = Duration.between(seuilDebut, sortie).toMinutes();
            return Math.round((mins / 60.0) * 100.0) / 100.0;
        } else if (sortie.isBefore(seuilFin)) {
            long mins = Duration.between(seuilDebut, LocalTime.MAX).toMinutes() + 1 + Duration.between(LocalTime.MIN, sortie).toMinutes();
            return Math.round((mins / 60.0) * 100.0) / 100.0;
        }
        return 0.0;
    }

    /**
     * Calcule le montant net du repas journalier incluant les majorations pour sortie tardive
     * piloté par les seuils et montants configurés dans parametre_global.
     */
    public double calculerTotalRepasJour(
            boolean repasMidiCoche,
            double montantMidi,
            LocalTime sortieSoir,
            LocalTime seuil2eRepas1,
            double montant2eRepas1,
            LocalTime seuil2eRepas2,
            double montant2eRepas2
    ) {
        double total = repasMidiCoche ? montantMidi : 0.0;
        if (sortieSoir != null) {
            LocalTime s2 = seuil2eRepas2 != null ? seuil2eRepas2 : LocalTime.of(21, 0);
            LocalTime s1 = seuil2eRepas1 != null ? seuil2eRepas1 : LocalTime.of(19, 30);

            if (!sortieSoir.isBefore(s2)) {
                total += montant2eRepas2;
            } else if (!sortieSoir.isBefore(s1)) {
                total += montant2eRepas1;
            }
        }
        return total;
    }

    /**
     * Calcule l'indemnité nette de déplacement journalière (méthode maîtresse unifiée pour tous les sites).
     *
     * @param fractionJour             fraction de présence pour le transport (1.0 = jour entier, 0.5 = demi-journée, 0.0 = absent)
     * @param tarifJourDeplacement     tarif journalier de référence du site (ex: 53 200 / 22 = 2 418.18 Ar à TMM, 31 200 / 22 = 1 418.18 Ar à TNR)
     * @param aPrisRepasSurSite        indique si l'employé a bénéficié d'un repas de midi sur site ce jour-là
     * @param reductionSiRepasSurSite  montant de la déduction si repas pris (clé REDUCTION_DEPLACT_REPAS_MIDI pour sites discontinus, 0 Ar si continu)
     * @return indemnité nette de déplacement du jour arrondie à 2 décimales (plafonnée à min 0.0 Ar)
     */
    public double calculerDeplacementJour(
            double fractionJour,
            double tarifJourDeplacement,
            boolean aPrisRepasSurSite,
            double reductionSiRepasSurSite
    ) {
        if (fractionJour <= 0.0 || tarifJourDeplacement <= 0.0) {
            return 0.0;
        }
        double brut = fractionJour * tarifJourDeplacement;
        if (aPrisRepasSurSite && reductionSiRepasSurSite > 0.0 && brut > 0.0) {
            brut = Math.max(0.0, brut - reductionSiRepasSurSite);
        }
        return Math.round(brut * 100.0) / 100.0;
    }

    /**
     * Surcharge rétrocompatible acceptant les indicateurs booléens matin/soir et le montant par demi-trajet.
     * Délègue à la méthode maîtresse unifiée.
     */
    public double calculerDeplacementJour(
            boolean trajetMatin,
            boolean trajetSoir,
            double montantParTrajet,
            boolean aPrisRepasSurSite,
            double reductionSiRepasSurSite
    ) {
        double fractionJour = (trajetMatin ? 0.5 : 0.0) + (trajetSoir ? 0.5 : 0.0);
        double tarifJourDeplacement = montantParTrajet * 2.0;
        return calculerDeplacementJour(fractionJour, tarifJourDeplacement, aPrisRepasSurSite, reductionSiRepasSurSite);
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

        // Compensation  des retards par le surplus d'heures de la semaine
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
