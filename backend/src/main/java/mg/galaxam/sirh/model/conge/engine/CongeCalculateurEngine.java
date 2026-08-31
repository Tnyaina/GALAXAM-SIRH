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
     * Calcule l'acquisition mensuelle de congés.
     */
    public BigDecimal calculerAcquisitionMensuelle(int moisTravailles) {
        if (moisTravailles <= 0) return BigDecimal.ZERO;
        return TAUX_ACQUISITION_MENSUEL.multiply(BigDecimal.valueOf(moisTravailles));
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
