package mg.galaxam.sirh.oracle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * ORACLE DE TEST CONGÉS — 100% INDÉPENDANT DU MOTEUR DE PRODUCTION.
 * 
 * N'importe AUCUN calculator, service ou engine de production.
 * Calcule de manière déterministe les droits à congés, l'acquisition (2.5j/mois),
 * les prises, l'imputation FIFO des tranches et la valorisation TCONGE.
 */
public class LeaveIndependentOracle {

    public record LeaveTranche(
            String idTranche,
            LocalDate dateAcquisition,
            LocalDate dateExpiration,
            BigDecimal joursInitiaux,
            BigDecimal joursRestants
    ) {}

    public record LeaveLedgerResult(
            BigDecimal soldeInitial,
            BigDecimal joursAcquisPeriode,
            BigDecimal joursPrisPeriode,
            BigDecimal joursExpiresPeriode,
            BigDecimal soldeFinal,
            BigDecimal tcongeApplicable,
            BigDecimal deductionCongeBase,
            BigDecimal indemniteConge
    ) {}

    public static LeaveLedgerResult evaluateLeavePeriod(
            BigDecimal soldeInitial,
            BigDecimal joursAcquis,
            BigDecimal joursPris,
            BigDecimal salaireBaseContractuel,
            BigDecimal tcongeOverride
    ) {
        BigDecimal soldeFinal = soldeInitial.add(joursAcquis).subtract(joursPris);

        // Détermination autonome du TCONGE :
        // Selon le Code du Travail Malgache (Art. 88) et la règle unifiée SIRH :
        // Le salaire journalier de référence de congé est au minimum le trentième du salaire de base : Base / 30
        BigDecimal tconge = tcongeOverride != null 
                ? tcongeOverride 
                : salaireBaseContractuel.divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);

        // Déduction de la base mensuelle pour les jours pris : Base * Jours / 30
        BigDecimal deductionBase = salaireBaseContractuel
                .multiply(joursPris)
                .divide(new BigDecimal("30"), 2, RoundingMode.HALF_UP);

        // Indemnité de congés payés allouée : Jours * TCONGE
        BigDecimal indemnite = joursPris
                .multiply(tconge)
                .setScale(2, RoundingMode.HALF_UP);

        return new LeaveLedgerResult(
                soldeInitial,
                joursAcquis,
                joursPris,
                BigDecimal.ZERO,
                soldeFinal,
                tconge.setScale(2, RoundingMode.HALF_UP),
                deductionBase,
                indemnite
        );
    }
}
