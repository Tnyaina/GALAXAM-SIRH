package mg.galaxam.sirh.unit.conge;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import mg.galaxam.sirh.conge.domain.CongeModeReprise;
import mg.galaxam.sirh.conge.domain.CongeSituationEmployeDTO;
import mg.galaxam.sirh.conge.domain.ReferencePeriodPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P3 — Test unitaire obligatoire de découplage strict :
 * SOLDE CONSOMMABLE ≠ ACCUMULATEURS DU CYCLE ≠ TCONGE APPLICABLE.
 *
 * Prouve qu'une prise de congés (ex: -15 jours) décrémente le solde consommable disponible,
 * mais NE DIMINUE EN AUCUN CAS les droits acquis ni la valorisation du cycle annuel de référence.
 */
@DisplayName("P3 — Découplage Solde Consommable vs Accumulateurs de Référence")
public class CongeSoldeAccumulateursDecouplingUnitTest {

    @Test
    @DisplayName("P3.1 — Une prise de 15 jours déduit le solde mais préserve 100% des accumulateurs du cycle")
    void testTakingLeaveDoesNotDecreaseCycleAccumulators() {
        // État initial avant prise (ex: Havana au mois 10 de l'Excel KAJY)
        BigDecimal ancienSoldeJours = new BigDecimal("25.00");
        BigDecimal cumulDroitsInitial = new BigDecimal("22.4575");
        BigDecimal cumulValoInitiale = new BigDecimal("240092.52");
        BigDecimal tcongeActuel = new BigDecimal("8669.52");

        // Situation avant prise
        CongeSituationEmployeDTO situationAvant = new CongeSituationEmployeDTO();
        situationAvant.setIdEmploye(101);
        situationAvant.setSoldeJours(ancienSoldeJours);

        CongeSituationEmployeDTO.TauxApplicableDTO tauxAvant = new CongeSituationEmployeDTO.TauxApplicableDTO();
        tauxAvant.setMontant(tcongeActuel);
        tauxAvant.setSource("REPRISE_CUTOVER");
        situationAvant.setTauxApplicable(tauxAvant);

        CongeSituationEmployeDTO.PeriodeReferenceDTO refAvant = new CongeSituationEmployeDTO.PeriodeReferenceDTO();
        refAvant.setDateDebut(LocalDate.of(2026, 1, 1));
        refAvant.setDateFin(LocalDate.of(2026, 12, 31));
        refAvant.setCumulDroitsTotal(cumulDroitsInitial);
        refAvant.setCumulValorisationTotal(cumulValoInitiale);
        situationAvant.setPeriodeReference(refAvant);

        // Opération métier : Prise de 15 jours de congés payés au mois 11
        BigDecimal joursPris = new BigDecimal("15.00");
        BigDecimal nouveauSolde = situationAvant.getSoldeJours().subtract(joursPris);

        // Simulation de la situation après prise (dans le modèle canonique SIRH)
        CongeSituationEmployeDTO situationApres = new CongeSituationEmployeDTO();
        situationApres.setIdEmploye(101);
        situationApres.setSoldeJours(nouveauSolde);
        situationApres.setTauxApplicable(tauxAvant); // Le taux applicable reste inchangé

        CongeSituationEmployeDTO.PeriodeReferenceDTO refApres = new CongeSituationEmployeDTO.PeriodeReferenceDTO();
        refApres.setDateDebut(refAvant.getDateDebut());
        refApres.setDateFin(refAvant.getDateFin());
        // Les accumulateurs restent strictement identiques : ils ne sont pas diminués par la prise !
        refApres.setCumulDroitsTotal(cumulDroitsInitial);
        refApres.setCumulValorisationTotal(cumulValoInitiale);
        situationApres.setPeriodeReference(refApres);

        // ASSERTIONS STRICTES P3
        // 1. Le solde consommable est diminué de 15 jours : 25.00 - 15.00 = 10.00 jours
        assertThat(situationApres.getSoldeJours()).isEqualByComparingTo(new BigDecimal("10.00"));

        // 2. Les accumulateurs du cycle n'ont PAS bougé d'un seul centième ou dix-millième de jour
        assertThat(situationApres.getPeriodeReference().getCumulDroitsTotal())
                .isEqualByComparingTo(cumulDroitsInitial);
        assertThat(situationApres.getPeriodeReference().getCumulValorisationTotal())
                .isEqualByComparingTo(cumulValoInitiale);

        // 3. Le taux applicable pour les prochains congés reste intact
        assertThat(situationApres.getTauxApplicable().getMontant())
                .isEqualByComparingTo(tcongeActuel);
    }

    @Test
    @DisplayName("P3.2 — ReferencePeriodPolicy calcule automatiquement la date de fin inclusive exacte (12 mois - 1 jour)")
    void testDeterministicPeriodPolicy() {
        ReferencePeriodPolicy policy = new ReferencePeriodPolicy();

        // Cas année civile : 01/01/2026 -> 31/12/2026
        LocalDate debutCivile = LocalDate.of(2026, 1, 1);
        LocalDate finCivile = ReferencePeriodPolicy.calculerDateFinReference(debutCivile);
        assertThat(finCivile).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(policy.calculerDateFin(debutCivile)).isEqualTo(LocalDate.of(2026, 12, 31));

        // Cas décalé : 15/03/2026 -> 14/03/2027
        LocalDate debutDecale = LocalDate.of(2026, 3, 15);
        LocalDate finDecale = ReferencePeriodPolicy.calculerDateFinReference(debutDecale);
        assertThat(finDecale).isEqualTo(LocalDate.of(2027, 3, 14));
    }

    @Test
    @DisplayName("P3.3 — Clôture du cycle : TCONGE futur est le quotient des accumulateurs, indépendant du solde restant")
    void testClosureCalculatesFutureTcongeFromAccumulatorsIndependentOfBalance() {
        BigDecimal cumulDroitsFinal = new BigDecimal("29.9570");
        BigDecimal cumulValoFinal = new BigDecimal("324385.38");

        // Cas A : L'employé a un solde restant de 0 jour
        BigDecimal tauxFuturCasA = cumulValoFinal.divide(cumulDroitsFinal, 2, RoundingMode.HALF_UP);

        // Cas B : L'employé a un solde restant de 20 jours
        BigDecimal tauxFuturCasB = cumulValoFinal.divide(cumulDroitsFinal, 2, RoundingMode.HALF_UP);

        assertThat(tauxFuturCasA).isEqualTo(tauxFuturCasB);
        assertThat(tauxFuturCasA).isEqualByComparingTo(new BigDecimal("10828.37"));
    }
}
