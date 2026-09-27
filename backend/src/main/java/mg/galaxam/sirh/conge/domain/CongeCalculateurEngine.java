package mg.galaxam.sirh.conge.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul et de validation métier des congés et absences.
 * Encapsule les règles du Code du Travail Malagasy (Loi n° 2003-044, Art. 13, 85, 86, 87, 88).
 */
@Component
public class CongeCalculateurEngine {

    public static final String LEAVE_BALANCE_INSUFFICIENT = "LEAVE_BALANCE_INSUFFICIENT";
    public static final String LEAVE_NOTICE_NOT_RESPECTED = "LEAVE_NOTICE_NOT_RESPECTED";
    public static final String LEAVE_POOL_EXCEEDED = "LEAVE_POOL_EXCEEDED";

    /**
     * Données déjà résolues par le service (référentiel, ledger et paramètres).
     * Le moteur ne charge rien en base et ne modifie aucun état.
     */
    public record SimulationInput(
            LocalDate dateDebut,
            LocalDate dateFin,
            boolean estDemiJournee,
            BigDecimal soldeAvant,
            boolean decompteSoldeAcquis,
            BigDecimal joursDebites,
            BigDecimal nbJoursDemande,
            boolean derogationSoldeNegatif,
            boolean soumisDelaiPreavis,
            boolean derogationDelaiPreavis,
            LocalDate dateDemande,
            int seuilPreavisLong,
            int delaiPreavisCourt,
            int delaiPreavisLong,
            BigDecimal consommationPool,
            BigDecimal plafondPool) {
    }

    /** Résultat canonique, explicable et directement sérialisable par Spring. */
    public record SimulationResult(
            LocalDate dateDebut,
            LocalDate dateFin,
            BigDecimal nbJours,
            BigDecimal soldeAvant,
            BigDecimal soldeApres,
            BigDecimal joursDebites,
            BigDecimal quotaAvant,
            BigDecimal quotaApres,
            int joursPreavisConstates,
            int joursPreavisRequis,
            List<String> warnings,
            List<String> violations,
            List<String> requirements,
            boolean requiresOverride,
            boolean canSubmit) {
    }

    /** Taux d'acquisition mensuel légal : 2,5 jours calendaires par mois de service effectif */
    public static final BigDecimal TAUX_ACQUISITION_MENSUEL = new BigDecimal("2.5");

    /** Plafond d'arrêt maladie continu avec maintien de salaire (Art. 13) */
    public static final int PLAFOND_MALADIE_MAINTIEN_JOURS = 30;

    /**
     * Calcule le nombre de jours décomptés (calendaires stricts).
     */
    public BigDecimal calculerNombreJours(LocalDate dateDebut, LocalDate dateFin, boolean estDemiJournee) {
        if (dateDebut == null || dateFin == null) {
            throw new IllegalArgumentException("Les dates de début et de fin sont obligatoires.");
        }
        if (estDemiJournee) {
            if (!dateDebut.equals(dateFin)) {
                throw new IllegalArgumentException("Une demi-journée doit avoir une date de début et de fin identiques.");
            }
            return new BigDecimal("0.5");
        }
        if (dateFin.isBefore(dateDebut)) {
            return BigDecimal.ZERO;
        }
        long jours = ChronoUnit.DAYS.between(dateDebut, dateFin) + 1;
        return BigDecimal.valueOf(jours).setScale(1);
    }

    /**
     * Calcule le nombre de jours de congé valorisés en paie pour une tranche de dates (RG-CONGE-05).
     * Règle actuelle : décompte calendaire strict (week-ends et fériés inclus).
     */
    public BigDecimal calculerNbJoursCongeValorises(LocalDate dateDebut, LocalDate dateFin, boolean estDemiJournee) {
        return calculerNombreJours(dateDebut, dateFin, estDemiJournee);
    }

    /**
     * Calcule l'acquisition mensuelle de congés avec taux paramétrable.
     */
    public BigDecimal calculerAcquisitionMensuelle(int moisTravailles, BigDecimal tauxMensuel) {
        if (moisTravailles <= 0) return BigDecimal.ZERO;
        BigDecimal taux = tauxMensuel != null ? tauxMensuel : TAUX_ACQUISITION_MENSUEL;
        return taux.multiply(BigDecimal.valueOf(moisTravailles));
    }

    /**
     * Calcule l'acquisition mensuelle de congés (base pleine forfaitaire légale 2.5j).
     */
    public BigDecimal calculerAcquisitionMensuelle(int moisTravailles) {
        return calculerAcquisitionMensuelle(moisTravailles, TAUX_ACQUISITION_MENSUEL);
    }

    /**
     * Calcule l'acquisition mensuelle de congés proratisée aux jours d'activité effective avec taux et diviseur configurables.
     */
    public BigDecimal calculerAcquisitionMensuelleProratisee(BigDecimal joursTravaillesTrentieme, BigDecimal tauxMensuel, BigDecimal diviseur) {
        if (joursTravaillesTrentieme == null || joursTravaillesTrentieme.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal div = (diviseur != null && diviseur.compareTo(BigDecimal.ZERO) > 0) ? diviseur : new BigDecimal("30.0");
        BigDecimal taux = tauxMensuel != null ? tauxMensuel : TAUX_ACQUISITION_MENSUEL;
        BigDecimal ratio = joursTravaillesTrentieme.divide(div, 6, java.math.RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        return taux.multiply(ratio).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule l'acquisition mensuelle de congés proratisée aux jours d'activité effective (base 30 jours, taux 2.5j).
     * Modèle OHATRA KAJY DROITS CONGES : nbre droit = 2.5 * formule / 30.0
     */
    public BigDecimal calculerAcquisitionMensuelleProratisee(BigDecimal joursTravaillesTrentieme) {
        return calculerAcquisitionMensuelleProratisee(joursTravaillesTrentieme, TAUX_ACQUISITION_MENSUEL, new BigDecimal("30.0"));
    }

    /**
     * Calcule la valorisation mensuelle du droit acquis (R*S/diviseur).
     */
    public BigDecimal calculerValorisationDroitMensuel(BigDecimal baseTotale, BigDecimal joursDroit, int diviseurValorisation) {
        if (baseTotale == null || joursDroit == null || baseTotale.compareTo(BigDecimal.ZERO) <= 0 || joursDroit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        int div = diviseurValorisation > 0 ? diviseurValorisation : 30;
        return baseTotale.multiply(joursDroit)
                .divide(BigDecimal.valueOf(div), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule la valorisation mensuelle du droit acquis (R*S/30).
     * Modèle OHATRA KAJY DROITS CONGES : valorisation = base_totale * nbre_droit / 30.0
     * où base_totale = NET_BASE + HS
     */
    public BigDecimal calculerValorisationDroitMensuel(BigDecimal baseTotale, BigDecimal joursDroit) {
        return calculerValorisationDroitMensuel(baseTotale, joursDroit, 30);
    }

    /**
     * Calcule le taux journalier de congé moyen (TCONGE).
     * Modèle OHATRA KAJY DROITS CONGES : TCONGE = Cumul Valorisation / Cumul Droits Acquis
     */
    public BigDecimal calculerTauxJournalierMoyen(BigDecimal cumulValorisation, BigDecimal cumulDroits) {
        if (cumulValorisation == null || cumulDroits == null || cumulDroits.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return cumulValorisation.divide(cumulDroits, 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule la bonification d'ancienneté avec seuil, palier et jours par palier dynamiques.
     */
    public int calculerBonificationAnciennete(int anneesAnciennete, int seuilMin, int palierAnnees, int joursParPalier) {
        if (palierAnnees <= 0 || joursParPalier <= 0 || anneesAnciennete < seuilMin) return 0;
        return (anneesAnciennete / palierAnnees) * joursParPalier;
    }

    /**
     * Calcule la bonification d'ancienneté (+1j par tranche de 5 ans révolus).
     */
    public int calculerBonificationAnciennete(int anneesAnciennete) {
        return calculerBonificationAnciennete(anneesAnciennete, 5, 5, 1);
    }

    /**
     * Détermine si un arrêt maladie dépasse le plafond de maintien de 30 jours et doit être scindé.
     */
    public boolean doitScinderArretMaladie(BigDecimal totalJours) {
        if (totalJours == null) return false;
        return totalJours.compareTo(BigDecimal.valueOf(PLAFOND_MALADIE_MAINTIEN_JOURS)) > 0;
    }

    /** Vérifie une projection de pool contre le plafond fourni par la configuration persistée. */
    public boolean verifierEligibilitePool(BigDecimal joursEngages, BigDecimal demandeJours, BigDecimal plafond) {
        if (plafond == null) throw new IllegalArgumentException("Le plafond du pool est obligatoire.");
        BigDecimal total = (joursEngages != null ? joursEngages : BigDecimal.ZERO)
                .add(demandeJours != null ? demandeJours : BigDecimal.ZERO);
        return total.compareTo(plafond) <= 0;
    }

    public BigDecimal calculerSoldeProjete(BigDecimal soldeAvant, BigDecimal jours, boolean decompteSoldeAcquis) {
        BigDecimal solde = soldeAvant != null ? soldeAvant : BigDecimal.ZERO;
        if (!decompteSoldeAcquis) return solde;
        return solde.subtract(jours != null ? jours : BigDecimal.ZERO);
    }

    public int calculerPreavisRequis(BigDecimal nbJours, boolean soumis, int seuilLong,
            int delaiCourt, int delaiLong) {
        if (!soumis) return 0;
        BigDecimal jours = nbJours != null ? nbJours : BigDecimal.ZERO;
        return jours.compareTo(BigDecimal.valueOf(seuilLong)) > 0 ? delaiLong : delaiCourt;
    }

    public LocalDate calculerDateFinFixe(LocalDate dateDebut, BigDecimal dureeJours) {
        if (dateDebut == null || dureeJours == null || dureeJours.compareTo(BigDecimal.ZERO) <= 0) return null;
        return dateDebut.plusDays(dureeJours.longValueExact() - 1L);
    }

    public SimulationResult simuler(SimulationInput input) {
        if (input == null) throw new IllegalArgumentException("Les données de simulation sont obligatoires.");
        BigDecimal nbJours = calculerNombreJours(input.dateDebut(), input.dateFin(), input.estDemiJournee());
        BigDecimal soldeAvant = input.soldeAvant() != null ? input.soldeAvant() : BigDecimal.ZERO;
        BigDecimal joursDemandes = input.nbJoursDemande() != null ? input.nbJoursDemande() : nbJours;
        BigDecimal joursDebites = input.joursDebites() != null ? input.joursDebites()
                : (input.decompteSoldeAcquis() ? nbJours : BigDecimal.ZERO);
        BigDecimal soldeApres = calculerSoldeProjete(soldeAvant, joursDebites, true);
        BigDecimal quotaAvant = input.consommationPool();
        BigDecimal quotaApres = quotaAvant != null ? quotaAvant.add(joursDemandes) : null;

        LocalDate dateDemande = input.dateDemande() != null ? input.dateDemande() : LocalDate.now();
        int preavisConstate = Math.toIntExact(ChronoUnit.DAYS.between(dateDemande, input.dateDebut()));
        int preavisRequis = calculerPreavisRequis(nbJours, input.soumisDelaiPreavis(),
                input.seuilPreavisLong(), input.delaiPreavisCourt(), input.delaiPreavisLong());

        List<String> warnings = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> requirements = new ArrayList<>();
        boolean override = false;

        if (input.decompteSoldeAcquis() && soldeApres.compareTo(BigDecimal.ZERO) < 0) {
            (input.derogationSoldeNegatif() ? warnings : violations).add(LEAVE_BALANCE_INSUFFICIENT);
            requirements.add("BALANCE_OVERRIDE");
            override = true;
        }
        if (input.soumisDelaiPreavis() && preavisConstate < preavisRequis) {
            (input.derogationDelaiPreavis() ? warnings : violations).add(LEAVE_NOTICE_NOT_RESPECTED);
            requirements.add("NOTICE_OVERRIDE");
            override = true;
        }
        if (quotaApres != null && input.plafondPool() != null
                && quotaApres.compareTo(input.plafondPool()) > 0) {
            (input.derogationSoldeNegatif() ? warnings : violations).add(LEAVE_POOL_EXCEEDED);
            requirements.add("POOL_OVERRIDE");
            override = true;
        }

        return new SimulationResult(input.dateDebut(), input.dateFin(), joursDemandes, soldeAvant, soldeApres, joursDebites,
                quotaAvant, quotaApres, preavisConstate, preavisRequis,
                List.copyOf(warnings), List.copyOf(violations), List.copyOf(requirements),
                override, violations.isEmpty());
    }
}
