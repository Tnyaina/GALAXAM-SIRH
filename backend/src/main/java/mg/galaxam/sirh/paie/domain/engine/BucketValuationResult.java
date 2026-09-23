package mg.galaxam.sirh.paie.domain.engine;

import mg.galaxam.sirh.paie.persistence.*;
import mg.galaxam.sirh.paie.domain.engine.*;
import mg.galaxam.sirh.paie.domain.dto.*;
import mg.galaxam.sirh.paie.domain.dto.export.*;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paie.application.workflow.*;
import mg.galaxam.sirh.paie.application.export.*;
import mg.galaxam.sirh.paie.api.dto.*;
import mg.galaxam.sirh.paie.api.dto.stc.*;

import java.math.BigDecimal;
import mg.galaxam.sirh.pointage.domain.calculation.QualifiedTimeBucket;

/**
 * Traçabilité explicable et unitaire de la valorisation monétaire d'un QualifiedTimeBucket (Phase 1C-B.1).
 *
 * Sémantique stricte des montants :
 * - overtimeBaseAmount : Base horaire (1.00) de l'heure supplémentaire (0 si heure normale REGULAR).
 * - overtimePremiumAmount : Incrément de majoration HS (+30% ou +50%) (0 si REGULAR).
 * - nightPremiumAmount : Incrément de majoration pour travail de nuit (+30% habituel) (0 sinon).
 * - sundayPremiumAmount : Incrément de majoration pour travail le dimanche (+40%) (0 sinon).
 * - holidayPremiumAmount : Incrément de majoration pour jour férié chômé (+100%) (0 sinon).
 * - payrollAdditionalAmount : EXACTEMENT ce que le moteur de paie doit ajouter au salaire mensuel de base.
 *
 * Invariants monétaires absolus :
 * - REGULAR DAY : payrollAdditionalAmount = 0
 * - REGULAR + Sujétion : payrollAdditionalAmount = Somme des incréments de sujétion applicables
 * - OVERTIME : payrollAdditionalAmount = overtimeBaseAmount + overtimePremiumAmount + Sujétion applicable
 * - La base horaire (1.00) n'est JAMAIS ajoutée pour les heures normales REGULAR (déjà dans SALAIRE_BASE).
 */
public record BucketValuationResult(
        QualifiedTimeBucket sourceBucket,
        BigDecimal hours,
        BigDecimal hourlyRate,
        BigDecimal overtimeBaseAmount,
        BigDecimal overtimePremiumAmount,
        BigDecimal nightPremiumAmount,
        BigDecimal sundayPremiumAmount,
        BigDecimal holidayPremiumAmount,
        BigDecimal payrollAdditionalAmount,
        BigDecimal overtimeRate,
        BigDecimal nightRate,
        BigDecimal sundayRate,
        BigDecimal holidayRate,
        String explanation
) {
    public BucketValuationResult {
        if (sourceBucket == null) {
            throw new IllegalArgumentException("sourceBucket est obligatoire.");
        }
        hours = hours == null ? BigDecimal.ZERO : hours;
        hourlyRate = hourlyRate == null ? BigDecimal.ZERO : hourlyRate;
        overtimeBaseAmount = overtimeBaseAmount == null ? BigDecimal.ZERO : overtimeBaseAmount;
        overtimePremiumAmount = overtimePremiumAmount == null ? BigDecimal.ZERO : overtimePremiumAmount;
        nightPremiumAmount = nightPremiumAmount == null ? BigDecimal.ZERO : nightPremiumAmount;
        sundayPremiumAmount = sundayPremiumAmount == null ? BigDecimal.ZERO : sundayPremiumAmount;
        holidayPremiumAmount = holidayPremiumAmount == null ? BigDecimal.ZERO : holidayPremiumAmount;
        payrollAdditionalAmount = payrollAdditionalAmount == null ? BigDecimal.ZERO : payrollAdditionalAmount;
        overtimeRate = overtimeRate == null ? BigDecimal.ZERO : overtimeRate;
        nightRate = nightRate == null ? BigDecimal.ZERO : nightRate;
        sundayRate = sundayRate == null ? BigDecimal.ZERO : sundayRate;
        holidayRate = holidayRate == null ? BigDecimal.ZERO : holidayRate;

        // Invariant de composition stricte anti-double base
        BigDecimal expectedAddition = overtimeBaseAmount
                .add(overtimePremiumAmount)
                .add(nightPremiumAmount)
                .add(sundayPremiumAmount)
                .add(holidayPremiumAmount);
        if (payrollAdditionalAmount.compareTo(expectedAddition) != 0) {
            throw new IllegalStateException("INVARIANT_VIOLATION: payrollAdditionalAmount (" + payrollAdditionalAmount +
                    ") != somme des composantes (" + expectedAddition + ") pour le bucket " + sourceBucket);
        }
    }

    // Accesseurs de compatibilité rétrocompatible
    public BigDecimal baseAmount() { return overtimeBaseAmount; }
    public BigDecimal overtimeAmount() { return overtimeBaseAmount.add(overtimePremiumAmount); }
    public BigDecimal nightAmount() { return nightPremiumAmount; }
    public BigDecimal sundayAmount() { return sundayPremiumAmount; }
    public BigDecimal holidayAmount() { return holidayPremiumAmount; }
    public BigDecimal totalAmount() { return payrollAdditionalAmount; }
}
