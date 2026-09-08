package mg.galaxam.sirh.model.conge.engine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * Moteur de calcul et de validation métier des congés et absences.
 * Encapsule les règles du Code du Travail Malagasy (Loi n° 2003-044, Art. 13, 85, 86, 87, 88).
 */
@Component
public class CongeCalculateurEngine {

    /** Taux d'acquisition mensuel légal : 2,5 jours calendaires par mois de service effectif */
    public static final BigDecimal TAUX_ACQUISITION_MENSUEL = new BigDecimal("2.5");

    /** Plafond annuel partagé pour les permissions exceptionnelles & paternité (Art. 88) */
    public static final BigDecimal PLAFOND_POOL_ANNUEL_FAMILIAL = new BigDecimal("10.0");

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
     * Calcule l'acquisition mensuelle de congés (base pleine forfaitaire).
     */
    public BigDecimal calculerAcquisitionMensuelle(int moisTravailles) {
        if (moisTravailles <= 0) return BigDecimal.ZERO;
        return TAUX_ACQUISITION_MENSUEL.multiply(BigDecimal.valueOf(moisTravailles));
    }

    /**
     * Calcule l'acquisition mensuelle de congés proratisée aux jours d'activité effective (base 30 jours).
     * Modèle OHATRA KAJY DROITS CONGES : nbre droit = 2.5 * formule / 30.0
     */
    public BigDecimal calculerAcquisitionMensuelleProratisee(BigDecimal joursTravaillesTrentieme) {
        if (joursTravaillesTrentieme == null || joursTravaillesTrentieme.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal ratio = joursTravaillesTrentieme.divide(new BigDecimal("30.0"), 6, java.math.RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);
        return TAUX_ACQUISITION_MENSUEL.multiply(ratio).setScale(4, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Calcule la valorisation mensuelle du droit acquis (R*S/30).
     * Modèle OHATRA KAJY DROITS CONGES : valorisation = base_totale * nbre_droit / 30.0
     * où base_totale = NET_BASE + HS
     */
    public BigDecimal calculerValorisationDroitMensuel(BigDecimal baseTotale, BigDecimal joursDroit) {
        if (baseTotale == null || joursDroit == null || baseTotale.compareTo(BigDecimal.ZERO) <= 0 || joursDroit.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return baseTotale.multiply(joursDroit)
                .divide(new BigDecimal("30.0"), 2, java.math.RoundingMode.HALF_UP);
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
     * Calcule la bonification d'ancienneté (+1j par tranche de 5 ans révolus).
     */
    public int calculerBonificationAnciennete(int anneesAnciennete) {
        if (anneesAnciennete < 5) return 0;
        return anneesAnciennete / 5;
    }

    /**
     * Détermine si un arrêt maladie dépasse le plafond de maintien de 30 jours et doit être scindé.
     */
    public boolean doitScinderArretMaladie(BigDecimal totalJours) {
        if (totalJours == null) return false;
        return totalJours.compareTo(BigDecimal.valueOf(PLAFOND_MALADIE_MAINTIEN_JOURS)) > 0;
    }

    /**
     * Vérifie si une demande sur le pool annuel familial (10j/an) est admissible.
     */
    public boolean verifierEligibilitePoolFamilial(BigDecimal consommationActuelle, BigDecimal demandeJours) {
        BigDecimal total = (consommationActuelle != null ? consommationActuelle : BigDecimal.ZERO)
                .add(demandeJours != null ? demandeJours : BigDecimal.ZERO);
        return total.compareTo(PLAFOND_POOL_ANNUEL_FAMILIAL) <= 0;
    }
}
