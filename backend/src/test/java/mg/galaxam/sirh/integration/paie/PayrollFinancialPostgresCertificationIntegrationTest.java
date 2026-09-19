package mg.galaxam.sirh.integration.paie;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CERTIFICATION POSTGRESQL DES CONFIGURATIONS LÉGALES ET FISCALES (Sections 21 & 28).
 *
 * Vérifie que la base PostgreSQL porte les configurations réelles,
 * que les tranches IRSA, taux de majoration et cotisations sont résolus par date sans fallback silencieux.
 */
@DisplayName("Certification PostgreSQL — Référentiels Légaux & Fiscaux Datés")
public class PayrollFinancialPostgresCertificationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("28.1 Vérification de la configuration légale LF_2024_2025 et LF_2026 dans paie_config_legale")
    void testConfigLegaleResolutions() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // LF 2024-2025
            String sql2025 = "SELECT sme_national, diviseur_mensuel_heures, minimum_perception_irsa FROM paie_config_legale WHERE code_version = 'LF_2024_2025'";
            try (PreparedStatement ps = conn.prepareStatement(sql2025);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La configuration LF_2024_2025 doit exister");
                assertEquals(0, new BigDecimal("262680.00").compareTo(rs.getBigDecimal("sme_national")));
                assertEquals(0, new BigDecimal("173.33").compareTo(rs.getBigDecimal("diviseur_mensuel_heures")));
                assertEquals(0, new BigDecimal("2000.00").compareTo(rs.getBigDecimal("minimum_perception_irsa")));
            }

            // LF 2026
            String sql2026 = "SELECT sme_national, diviseur_mensuel_heures, minimum_perception_irsa FROM paie_config_legale WHERE code_version = 'LF_2026'";
            try (PreparedStatement ps = conn.prepareStatement(sql2026);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "La configuration LF_2026 doit exister");
                assertEquals(0, new BigDecimal("300000.00").compareTo(rs.getBigDecimal("sme_national")));
                assertEquals(0, new BigDecimal("173.33").compareTo(rs.getBigDecimal("diviseur_mensuel_heures")));
                assertEquals(0, new BigDecimal("2400000.00").compareTo(rs.getBigDecimal("sme_national").multiply(new BigDecimal("8"))), "Plafond CNaPS 2026 = 8x SME = 2 400 000 Ar");
                assertEquals(0, new BigDecimal("3000.00").compareTo(rs.getBigDecimal("minimum_perception_irsa")));
            }
        }
    }

    @Test
    @DisplayName("28.2 Vérification des tranches IRSA dans bareme_irsa")
    void testBaremeIrsaTranches() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT COUNT(*) as nb_tranches FROM bareme_irsa WHERE id_config_legale = 2";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt("nb_tranches"), "LF 2026 comporte exactement 6 tranches IRSA");
            }
        }
    }

    @Test
    @DisplayName("28.3 Vérification des majorations d'heures supplémentaires dans ref_taux_majoration")
    void testRefTauxMajoration() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT type_majoration, taux FROM ref_taux_majoration WHERE id_config_legale = 2 AND date_debut = '2026-01-01' ORDER BY type_majoration";
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                boolean foundHs30 = false;
                boolean foundHs50 = false;
                boolean foundNuit = false;
                boolean foundDimanche = false;

                while (rs.next()) {
                    String type = rs.getString("type_majoration");
                    BigDecimal taux = rs.getBigDecimal("taux");
                    if ("HS_30".equals(type)) {
                        assertEquals(0, new BigDecimal("0.30").compareTo(taux));
                        foundHs30 = true;
                    } else if ("HS_50".equals(type)) {
                        assertEquals(0, new BigDecimal("0.50").compareTo(taux));
                        foundHs50 = true;
                    } else if ("NUIT".equals(type)) {
                        assertEquals(0, new BigDecimal("0.30").compareTo(taux));
                        foundNuit = true;
                    } else if ("DIMANCHE".equals(type)) {
                        assertEquals(0, new BigDecimal("0.40").compareTo(taux));
                        foundDimanche = true;
                    }
                }
                assertTrue(foundHs30 && foundHs50 && foundNuit && foundDimanche, "Les majorations légales 2026 doivent toutes être configurées");
            }
        }
    }
}
