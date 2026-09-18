package mg.galaxam.sirh.integration.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.conge.engine.TauxCongeResolution;
import mg.galaxam.sirh.model.conge.engine.TcongeApplicableRateStatus;
import mg.galaxam.sirh.model.conge.engine.TcongeConfigurationDTO;
import mg.galaxam.sirh.model.conge.engine.TcongeReferenceStatus;
import mg.galaxam.sirh.model.conge.engine.TcongeSourceProvenance;
import mg.galaxam.sirh.model.conge.service.CongeTauxApplicableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2A-4C.1 — Correction P2 : Détection et exposition de REFERENCE_SETUP_REQUIRED.
 * Prouve qu'une absence de période de référence ne devient pas un fallback permanent silencieux,
 * tout en permettant à un salarié en transition (KAJY_MIGRATION ou Base/30) d'être rémunéré.
 */
@DisplayName("Phase 2A-4C.1 — P2 : Anti-fallback silencieux permanent et diagnostic REFERENCE_SETUP_REQUIRED")
public class TcongeP2AntiSilentFallbackIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    private int testEmployeId;

    @BeforeEach
    void setup() {
        databaseCleaner.cleanTransactionalData();
        testEmployeId = employeFixtures.createActiveEmploye("EMP-P2-TCONGE", "ANDRIAMASY", "Tahina", 1).idEmploye;
    }

    @Test
    @DisplayName("Cas A : Salarié avec KAJY_MIGRATION mais sans référence -> Taux utilisable ET warning REFERENCE_SETUP_REQUIRED")
    void shouldResolveTauxMigrationAndExposeSetupRequiredWarning() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 1. Enregistrement d'un taux issu de KAJY_MIGRATION
            LocalDate dateEffet = LocalDate.of(2026, 1, 1);
            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId,
                    new BigDecimal("8669.52"),
                    dateEffet,
                    null,
                    "Reprise historique KAJY",
                    1,
                    c
            );

            // 2. Résolution du taux applicable au 01/03/2026 : le taux est parfaitement actif pour la paie
            TauxCongeResolution resTaux = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, LocalDate.of(2026, 3, 1), null, c
            );
            assertThat(resTaux.getTauxJournalier()).isEqualByComparingTo("8669.52");
            assertThat(resTaux.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.KAJY_MIGRATION);

            // 3. Évaluation multidimensionnelle de configuration :
            // Le taux est ACTIVE, mais la référence est MISSING, et le warning REFERENCE_SETUP_REQUIRED est visible
            TcongeConfigurationDTO config = congeTauxApplicableService.evaluerConfigurationTconge(
                    testEmployeId, LocalDate.of(2026, 3, 1), c
            );

            assertThat(config.getApplicableRateStatus()).isEqualTo(TcongeApplicableRateStatus.ACTIVE);
            assertThat(config.getReferenceStatus()).isEqualTo(TcongeReferenceStatus.MISSING);
            assertThat(config.getWarnings()).contains("REFERENCE_SETUP_REQUIRED");
        }
    }

    @Test
    @DisplayName("Cas A' : Nouveau salarié amorcé Base/30 -> Taux résolu ET warning REFERENCE_SETUP_REQUIRED")
    void shouldResolveBase30AndExposeSetupRequiredWarning() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            BigDecimal salaireBase = new BigDecimal("600000.00");
            LocalDate dateRef = LocalDate.of(2026, 3, 1);

            // 1. Résolution canonique pour nouveau collaborateur sans historique
            TauxCongeResolution res = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    testEmployeId, dateRef, salaireBase, c
            );
            // 600 000 / 30 = 20 000 Ar
            assertThat(res.getTauxJournalier()).isEqualByComparingTo("20000.00");
            assertThat(res.getSourceProvenance()).isEqualTo(TcongeSourceProvenance.INITIALISATION_BASE_30);

            // 2. Évaluation de configuration : REFERENCE_SETUP_REQUIRED doit être exposé pour ne pas rester un fallback éternel
            TcongeConfigurationDTO config = congeTauxApplicableService.evaluerConfigurationTconge(
                    testEmployeId, dateRef, c
            );
            assertThat(config.getReferenceStatus()).isEqualTo(TcongeReferenceStatus.MISSING);
            assertThat(config.getWarnings()).contains("REFERENCE_SETUP_REQUIRED");
        }
    }

    @Test
    @DisplayName("Cas B : Opération exigeant la construction canonique -> Lève explicitement TCONGE_REFERENCE_PERIOD_MISSING")
    void shouldThrowWhenCanonicalConstructionRequiresActiveReference() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> congeTauxApplicableService.resoudrePeriodeReferenceStrict(
                    testEmployeId, LocalDate.of(2026, 3, 1), c
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("TCONGE_REFERENCE_PERIOD_MISSING");
        }
    }

    @Test
    @DisplayName("Régime nominal : Période ACTIVE + Taux ACTIVE coexistent parfaitement sans warning")
    void shouldCoexistActivePeriodAndActiveRateWithoutSetupWarning() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Taux applicable actif
            congeTauxApplicableService.enregistrerTauxApplicableMigration(
                    testEmployeId, new BigDecimal("8669.52"), LocalDate.of(2026, 1, 1), null, "Taux actif", 1, c
            );

            // Période de référence active (12 mois : 01/01/2026 au 31/12/2026)
            congeTauxApplicableService.creerPeriodeReference(
                    testEmployeId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1, c
            );

            TcongeConfigurationDTO config = congeTauxApplicableService.evaluerConfigurationTconge(
                    testEmployeId, LocalDate.of(2026, 6, 1), c
            );

            assertThat(config.getApplicableRateStatus()).isEqualTo(TcongeApplicableRateStatus.ACTIVE);
            assertThat(config.getReferenceStatus()).isEqualTo(TcongeReferenceStatus.ACTIVE);
            assertThat(config.getWarnings()).doesNotContain("REFERENCE_SETUP_REQUIRED");
        }
    }
}
