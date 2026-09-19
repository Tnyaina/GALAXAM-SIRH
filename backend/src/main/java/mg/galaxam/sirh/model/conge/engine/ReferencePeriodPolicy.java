package mg.galaxam.sirh.model.conge.engine;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Règle métier déterministe de calcul des bornes d'une période de référence de congés (Correction 2).
 * Calcule automatiquement la date de fin à partir de la date de début et de la durée réglementaire.
 */
@Component
public class ReferencePeriodPolicy {

    public static final int DUREE_STANDARD_MOIS = 12;

    /**
     * Calcule la date de fin déterministe d'une période de référence pour une durée de 12 mois.
     * Exemple : 01/01/2026 -> 31/12/2026
     *          15/03/2026 -> 14/03/2027
     */
    public static LocalDate calculerDateFinReference(LocalDate dateDebut) {
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début de la période de référence est obligatoire.");
        }
        return dateDebut.plusMonths(DUREE_STANDARD_MOIS).minusDays(1);
    }

    public LocalDate calculerDateFin(LocalDate dateDebut) {
        return calculerDateFinReference(dateDebut);
    }

    /**
     * Calcule la date de fin déterministe d'une période de référence pour une durée donnée en mois.
     */
    public LocalDate calculerDateFin(LocalDate dateDebut, int dureeMois) {
        if (dateDebut == null) {
            throw new IllegalArgumentException("La date de début de la période de référence est obligatoire.");
        }
        int duree = dureeMois > 0 ? dureeMois : DUREE_STANDARD_MOIS;
        return dateDebut.plusMonths(duree).minusDays(1);
    }
}
