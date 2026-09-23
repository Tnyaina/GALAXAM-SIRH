package mg.galaxam.sirh.integration.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import mg.galaxam.sirh.paie.domain.engine.IrsaBracketResolver;
import mg.galaxam.sirh.paie.domain.engine.IrsaCalculator;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paieconfig.persistence.RefTauxMajoration;

@SpringBootTest
@ActiveProfiles("test")
public class PayrollTemporalPostgresCertificationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("1. Résolution PostgreSQL stricte pour Décembre 2025 (2025-12-31)")
    void testDecembre2025PostgresResolution() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateDec2025 = LocalDate.of(2025, 12, 31);
            PaieConfigLegale config = PaieConfigLegale.findActiveAt(c, dateDec2025);

            assertNotNull(config, "Une configuration légale doit exister pour Décembre 2025");
            assertEquals("LF_2024_2025", config.getCodeVersion());
            assertEquals(0, new BigDecimal("262680.00").compareTo(config.getSmeNational()), "SME Décembre 2025 = 262 680 Ar");
            assertEquals(0, new BigDecimal("2101440.00").compareTo(config.getSmeNational().multiply(new BigDecimal("8"))), "Plafond CNaPS 12/2025 = 2 101 440 Ar");
            assertEquals(0, new BigDecimal("2000.00").compareTo(config.getMinimumPerceptionIrsa()), "Min IRSA 12/2025 = 2 000 Ar");
            assertEquals(0, new BigDecimal("173.33").compareTo(config.getDiviseurMensuelHeures()), "Diviseur mensuel = 173.33 h");

            // Tranches IRSA 2025
            List<IrsaCalculator.Tranche> tranches = IrsaBracketResolver.resolve(config.getIdConfigLegale(), dateDec2025, c);
            assertEquals(5, tranches.size(), "Exactement 5 tranches en 2025 (pas de tranche 25%)");
            assertEquals(0, new BigDecimal("0.20").compareTo(tranches.get(4).taux().movePointLeft(2)), "Dernière tranche 2025 = 20%");
        }
    }

    @Test
    @DisplayName("2. Test Frontière SME / CNaPS PostgreSQL (Décret 2024-2026 vs Revalorisation 01/03/2026)")
    void testCnapsSmeFrontiereResolution() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 2025-12-31
            PaieConfigLegale cfgDec25 = PaieConfigLegale.findActiveAt(c, LocalDate.of(2025, 12, 31));
            assertNotNull(cfgDec25);
            assertEquals(0, new BigDecimal("262680.00").compareTo(cfgDec25.getSmeNational()), "SME au 31/12/2025 doit être 262 680 Ar");
            assertEquals(0, new BigDecimal("2101440.00").compareTo(cfgDec25.getSmeNational().multiply(new BigDecimal("8"))));

            // 2026-01-01 : Selon décret officiel CNaPS en vigueur jusqu'au 28/02/2026, SME = 262 680 Ar
            PaieConfigLegale cfgJan26 = PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 1, 1));
            assertNotNull(cfgJan26);
            assertEquals(0, new BigDecimal("262680.00").compareTo(cfgJan26.getSmeNational()), "SME au 01/01/2026 doit être 262 680 Ar (décret 2024-2026)");
            assertEquals(0, new BigDecimal("2101440.00").compareTo(cfgJan26.getSmeNational().multiply(new BigDecimal("8"))));

            // 2026-02-28 : Dernier jour du décret 2024-2026
            PaieConfigLegale cfgFev26 = PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 2, 28));
            assertNotNull(cfgFev26);
            assertEquals(0, new BigDecimal("262680.00").compareTo(cfgFev26.getSmeNational()), "SME au 28/02/2026 doit être 262 680 Ar");
            assertEquals(0, new BigDecimal("2101440.00").compareTo(cfgFev26.getSmeNational().multiply(new BigDecimal("8"))));

            // 2026-03-01 : Entrée en vigueur de la revalorisation à 300 000 Ar
            PaieConfigLegale cfgMar26 = PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 3, 1));
            assertNotNull(cfgMar26);
            assertEquals(0, new BigDecimal("300000.00").compareTo(cfgMar26.getSmeNational()), "SME au 01/03/2026 doit être 300 000 Ar");
            assertEquals(0, new BigDecimal("2400000.00").compareTo(cfgMar26.getSmeNational().multiply(new BigDecimal("8"))));

            // 2026-03-02
            PaieConfigLegale cfgMar02 = PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 3, 2));
            assertNotNull(cfgMar02);
            assertEquals(0, new BigDecimal("300000.00").compareTo(cfgMar02.getSmeNational()));
            assertEquals(0, new BigDecimal("2400000.00").compareTo(cfgMar02.getSmeNational().multiply(new BigDecimal("8"))));
        }
    }

    @Test
    @DisplayName("3. Configuration Manquante : date hors référentiel renvoie null / erreur explicite")
    void testMissingConfigurationThrowsError() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            LocalDate dateHorsScope = LocalDate.of(1990, 1, 1);
            PaieConfigLegale missing = PaieConfigLegale.findActiveAt(c, dateHorsScope);
            assertNull(missing, "findActiveAt doit renvoyer null lorsqu'aucune configuration n'existe");

            assertThrows(IllegalStateException.class, () -> {
                IrsaBracketResolver.resolve(null, dateHorsScope, c);
            }, "IrsaBracketResolver doit lever IRSA_CONFIGURATION_MISSING");
        }
    }
}
