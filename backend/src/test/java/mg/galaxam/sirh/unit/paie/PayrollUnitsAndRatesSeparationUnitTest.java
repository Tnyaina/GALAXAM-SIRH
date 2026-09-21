package mg.galaxam.sirh.unit.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.BulletinPaieComplet;
import mg.galaxam.sirh.model.paie.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.model.paie.dto.PayrollCalculationInput;
import mg.galaxam.sirh.model.paie.engine.MoneyRoundingPolicy;
import mg.galaxam.sirh.model.paie.engine.PayrollExecutionEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Test Unitaire — Séparation Stricte des Unités (/30 vs 173.33 vs TCONGE) & Anti-Double Comptage")
public class PayrollUnitsAndRatesSeparationUnitTest {

    private PayrollExecutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PayrollExecutionEngine();
    }

    private PayrollCalculationInput buildBaseInput(BigDecimal baseSalary) {
        PayrollCalculationInput in = new PayrollCalculationInput();
        in.setIdEmploye(1);
        in.setIdPeriodePaie(10);
        in.setMatricule("EMP-001");
        in.setNomPrenom("Test Employe");
        in.setSalaireBaseContractuel(baseSalary);
        in.setDiviseurMensuelHeures(new BigDecimal("173.3300"));
        in.setModeCalculPaie("MENSUEL");
        in.setRegimeIntervenant("PERMANENT");
        in.setCategorieProfessionnelle("OP1A");
        in.setDateEmbauche(java.sql.Date.valueOf(LocalDate.of(2023, 1, 1)));
        in.setNbEnfantsEligibles(0);
        in.setNbJoursOuvrables(new BigDecimal("21"));
        in.setNbJoursPresents(new BigDecimal("21"));

        in.setSmeNational(new BigDecimal("262680.00"));
        in.setMinimumPerceptionIrsa(new BigDecimal("3000.00"));
        in.setDeductionParEnfant(new BigDecimal("2000.00"));
        in.setTranchesIrsa(List.of(
                new mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche(new BigDecimal("0"), new BigDecimal("350000"), BigDecimal.ZERO),
                new mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche(new BigDecimal("350000"), new BigDecimal("400000"), new BigDecimal("0.05")),
                new mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche(new BigDecimal("400000"), new BigDecimal("500000"), new BigDecimal("0.10")),
                new mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche(new BigDecimal("500000"), new BigDecimal("600000"), new BigDecimal("0.15")),
                new mg.galaxam.sirh.model.paie.engine.IrsaCalculator.Tranche(new BigDecimal("600000"), null, new BigDecimal("0.20"))
        ));
        in.setTauxCnapsSalarial(new BigDecimal("0.01"));
        in.setTauxCnapsPatronal(new BigDecimal("0.13"));
        in.setTauxSanteSalarial(new BigDecimal("0.01"));
        in.setTauxSantePatronal(new BigDecimal("0.05"));
        in.setTauxFmfpPatronal(new BigDecimal("0.01"));
        in.setSoumisCnaps(true);
        in.setSoumisSante(true);
        in.setSoumisIrsa(true);
        return in;
    }

    @Nested
    @DisplayName("1. Responsabilité stricte du diviseur /30 (JOURS)")
    class TrentiemeDivisorTests {

        @Test
        @DisplayName("1.1 Prorata entrée/sortie : jours calendaires sous contrat / 30")
        void testProrataEntreeSortie() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setEntreeSortieEnCoursDeMois(true);
            in.setJoursSousContrat(new BigDecimal("15")); // 15 jours calendaires

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal baseProratisee = engine.calculerSalaireBase(in, bp, lignes);

            // 600 000 * 15 / 30 = 300 000.00 Ar
            assertEquals(new BigDecimal("300000.00"), baseProratisee);
            assertEquals(1, lignes.size());
            assertEquals("SALAIRE_BASE", lignes.get(0).getCodeRubrique());
        }

        @Test
        @DisplayName("1.2 Déduction congé du salaire de base : strictement NBCONGE * Base / 30 (Zéro 173.33)")
        void testDeductionCongeTrentieme() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setNbJoursConges(new BigDecimal("3.0"));
            in.setTauxJournalierConge(new BigDecimal("25000.00")); // TCONGE différent de Base/30

            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal netConges = engine.calculerConges(in, bp, lignes);

            // Déduction Base/30 = 3 * 600 000 / 30 = 60 000.00 Ar
            // Indemnité TCONGE = 3 * 25 000 = 75 000.00 Ar
            // Net = 75 000 - 60 000 = +15 000.00 Ar
            BulletinPaieLigne ligneDed = lignes.stream()
                    .filter(l -> "DEDUCTION_CONGE_BASE".equals(l.getCodeRubrique()))
                    .findFirst().orElseThrow();
            BulletinPaieLigne ligneInd = lignes.stream()
                    .filter(l -> "INDEMNITE_CONGE_PAYE".equals(l.getCodeRubrique()))
                    .findFirst().orElseThrow();

            assertEquals(new BigDecimal("600000.00"), ligneDed.getBaseCalcul());
            assertEquals(new BigDecimal("3.0"), ligneDed.getQuantite());
            assertEquals(new BigDecimal("60000.00"), ligneDed.getMontantSalarial());

            assertEquals(new BigDecimal("25000.00"), ligneInd.getBaseCalcul());
            assertEquals(new BigDecimal("3.0"), ligneInd.getQuantite());
            assertEquals(new BigDecimal("75000.00"), ligneInd.getMontantSalarial());

            assertEquals(new BigDecimal("15000.00"), netConges);
        }
    }

    @Nested
    @DisplayName("2. Responsabilité stricte du diviseur horaire 173.33 (HEURES / MINUTES)")
    class HourlyDivisorTests {

        @Test
        @DisplayName("2.1 Absence : valorisation au taux horaire à pleine précision sans setScale(2) prématuré")
        void testAbsenceHautePrecision() {
            BigDecimal base = new BigDecimal("600000.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.3300"));

            // Cas de 10 minutes d'absence (snapshot pointage)
            PayrollAttendanceInput att = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(),
                    10000L, 0L, 0L, 0L, 0L, 0L, 0L,
                    10L, 10,
                    new BigDecimal("21"), new BigDecimal("21"), BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att);

            // Moteur : SalaireBase * 10 / (173.33 * 60) = 6 000 000 / 10 399.80 = 576.93417... Ar -> 576.93 Ar
            BulletinPaie bp = engine.initialiserEnTete(in);
            List<BulletinPaieLigne> lignes = new ArrayList<>();
            BigDecimal retenue = engine.calculerRetenueAbsence(in, base, bp.getTauxHoraire(), bp, lignes);

            assertEquals(new BigDecimal("576.93"), retenue);
            assertEquals(1, lignes.size());
            assertEquals(new BigDecimal("0.17"), lignes.get(0).getQuantite()); // 10 min -> 0.17h affichée
            assertEquals(new BigDecimal("576.93"), lignes.get(0).getMontantSalarial());
        }

        @Test
        @DisplayName("2.2 Non-double comptage : déficit net de présence vs minutes de retard")
        void testAntiDoubleComptageDeficitRetard() {
            BigDecimal base = new BigDecimal("520000.00");
            PayrollCalculationInput in = buildBaseInput(base);

            // Salarié avec 120 minutes de retard brut dont 60 minutes ont été compensées par des heures sup
            // Déficit net à retenir financièrement = 60 minutes (1.00h)
            // Retard brut total = 120 minutes
            PayrollAttendanceInput att = new PayrollAttendanceInput(
                    1, 1, 1, 10, Instant.now(),
                    9600L, 0L, 0L, 0L, 0L, 0L, 0L,
                    60L, 120, // 60 min deficit net retenu, 120 min retard brut total
                    new BigDecimal("21"), new BigDecimal("20"), BigDecimal.ONE, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()
            );
            in.setAttendanceInput(att);

            BulletinPaieComplet complet = engine.calculer(in);

            // Taux horaire = 520 000 / 173.33 = 3000.0577 Ar/h
            // Retenue financière = 520 000 * 60 / (173.33 * 60) = 520 000 / 173.33 = 3000.06 Ar
            // SEULE la retenue pour 60 min (3000.06 Ar) doit être déduite du brut.
            // Le retard de 120 min NE doit PAS faire l'objet d'une deuxième retenue de 6000.12 Ar !
            BulletinPaieLigne ligneAbsence = complet.trouverLigne("RETENUE_ABSENCE");
            assertNotNull(ligneAbsence, "La ligne RETENUE_ABSENCE doit exister");
            assertEquals(new BigDecimal("3000.06"), ligneAbsence.getMontantSalarial());

            assertNull(complet.trouverLigne("RETENUE_RETARD"), "Il ne doit pas exister de ligne RETENUE_RETARD en doublon");

            // Explication backend-driven : expose à la fois les minutes retenues et le retard pour transparence
            assertNotNull(complet.getExplication());
            assertEquals(60L, complet.getExplication().minutesAbsenceRetenues());
            assertEquals(120, complet.getExplication().minutesRetard());
            assertEquals(new BigDecimal("3000.06"), complet.getExplication().montantRetenueAbsence());
        }
    }

    @Nested
    @DisplayName("3. Détection et intégrité de configuration")
    class ConfigIntegrityTests {

        @Test
        @DisplayName("3.1 Le diviseur mensuel est résolu dynamiquement et configurable sans altérer les formules")
        void testDiviseurConfigurable() {
            BigDecimal base = new BigDecimal("346660.00");
            PayrollCalculationInput in = buildBaseInput(base);
            in.setDiviseurMensuelHeures(new BigDecimal("173.3300"));

            // 346 660 / 173.3300 = 2 000.0000 Ar/h
            assertEquals(new BigDecimal("2000.0000"), in.getTauxHoraireEffectif());

            // Si la convention ou un autre régime utilisait 173.3333 :
            in.setDiviseurMensuelHeures(new BigDecimal("173.3333"));
            assertEquals(new BigDecimal("1999.9619"), in.getTauxHoraireEffectif());
        }
    }
}
