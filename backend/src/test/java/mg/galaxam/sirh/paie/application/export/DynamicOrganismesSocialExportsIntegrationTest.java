package mg.galaxam.sirh.paie.application.export;

import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import mg.galaxam.sirh.paieconfig.application.SocialContributionContextResolver;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class DynamicOrganismesSocialExportsIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private EtatSocialMensuelExportService monthlyService;
    @Autowired private QuarterlySocialDataService quarterlyService;

    private void verifierBaseTestUniquement(Connection c) throws Exception {
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertEquals("sirh_galaxam_test", rs.getString(1),
                    "SÉCURITÉ : Les tests doivent s'exécuter EXCLUSIVEMENT sur sirh_galaxam_test.");
        }
    }

    @Test
    @DisplayName("Validation complète de l'organisme dynamique AMIT (Site TLR) et gestion des avertissements non configurés")
    void testAmitEtAvertissementOrganismeNonConfigure() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            verifierBaseTestUniquement(c);
            c.setAutoCommit(false);
            try {
                // 1. Charger fixtures Q1 de base
                try (var is = getClass().getResourceAsStream("/q1_test_fixtures_inserts.sql")) {
                    assertNotNull(is, "Fichier q1_test_fixtures_inserts.sql manquant");
                    String sql = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    for (String stmt : sql.split(";")) {
                        String trimmed = stmt.trim();
                        if (!trimmed.isBlank()) {
                            try (var s = c.createStatement()) {
                                s.execute(trimmed);
                            }
                        }
                    }
                }

                // 2. Vérifier que ref_organisme_social contient AMIT
                try (var s = c.createStatement();
                     var rs = s.executeQuery("SELECT id_organisme, code, libelle FROM ref_organisme_social WHERE code = 'AMIT'")) {
                    assertTrue(rs.next(), "L'organisme AMIT doit exister dans ref_organisme_social");
                    int idAmit = rs.getInt("id_organisme");
                    assertTrue(idAmit > 0);
                }

                // 3. Configurer un site avec AMIT (Site Tuléar - TLR)
                int idTypeSante;
                try (var s = c.createStatement();
                     var rs = s.executeQuery("SELECT id_type_cotisation FROM ref_type_cotisation WHERE code = 'OSTIE' OR categorie = 'SANTE' LIMIT 1")) {
                    assertTrue(rs.next());
                    idTypeSante = rs.getInt(1);
                }
                int idAmitOrg;
                try (var s = c.createStatement();
                     var rs = s.executeQuery("SELECT id_organisme FROM ref_organisme_social WHERE code = 'AMIT'")) {
                    assertTrue(rs.next());
                    idAmitOrg = rs.getInt(1);
                }

                int idSiteTlr;
                try (var s = c.createStatement();
                     var rs = s.executeQuery("INSERT INTO ref_site (code_site, libelle, id_filiale_rattachement) " +
                             "VALUES ('TLR', 'Site Tuléar', 1) " +
                             "ON CONFLICT (code_site) DO UPDATE SET libelle = 'Site Tuléar' RETURNING id_site")) {
                    assertTrue(rs.next());
                    idSiteTlr = rs.getInt(1);
                }

                // Associer le site TLR à AMIT via site_cotisation
                try (var s = c.createStatement()) {
                    s.execute("DELETE FROM site_cotisation WHERE id_site = " + idSiteTlr + " AND id_type_cotisation = " + idTypeSante);
                    s.execute("INSERT INTO site_cotisation (id_site, id_type_cotisation, id_organisme, est_plafonne_sante, est_actif) " +
                            "VALUES (" + idSiteTlr + ", " + idTypeSante + ", " + idAmitOrg + ", true, true)");
                }

                // 4. Vérifier la résolution de contexte pour le site TLR
                var socialContextResolver = new SocialContributionContextResolver();
                var resolvedSante = socialContextResolver.healthRate("TLR", LocalDate.of(2026, 1, 15), c);
                assertNotNull(resolvedSante, "La cotisation santé du site TLR doit être résolue");
                assertEquals("AMIT", resolvedSante.organismeCode(), "L'organisme résolu pour le site TLR doit être AMIT");
                assertEquals(idAmitOrg, resolvedSante.organismeId(), "L'id de l'organisme résolu doit être AMIT");

                // 5. Mettre à jour une ligne de bulletin pour simuler une cotisation AMIT
                // Sur le bulletin 1, marquer les lignes santé avec code_organisme = 'AMIT'
                try (var s = c.createStatement()) {
                    s.execute("ALTER TABLE bulletin_paie_ligne DISABLE TRIGGER trg_bulletin_paie_ligne_immutabilite");
                    s.execute("UPDATE bulletin_paie_ligne SET id_organisme = " + idAmitOrg + ", code_organisme = 'AMIT' " +
                            "WHERE id_bulletin = 1 AND (code_rubrique = 'COTISATION_OSTIE_SAL' OR code_rubrique = 'COTISATION_OSTIE_PAT')");
                    s.execute("ALTER TABLE bulletin_paie_ligne ENABLE TRIGGER trg_bulletin_paie_ligne_immutabilite");
                }

                // 6. Tester l'export social mensuel (Période 1)
                EtatSocialMensuelExportService.Result monthlyResult = monthlyService.export(1, c);
                assertNotNull(monthlyResult);
                assertTrue(monthlyResult.bytes().length > 0);

                try (Workbook monthlyBook = WorkbookFactory.create(new ByteArrayInputStream(monthlyResult.bytes()))) {
                    Sheet summarySheet = monthlyBook.getSheet("Synthèse sociale");
                    assertNotNull(summarySheet);
                    boolean foundAmit = false;
                    for (int r = 0; r <= summarySheet.getLastRowNum(); r++) {
                        var row = summarySheet.getRow(r);
                        if (row != null && row.getCell(0) != null) {
                            String val = row.getCell(0).getStringCellValue();
                            if ("AMIT".equals(val)) {
                                foundAmit = true;
                                // Salariés concernés = 1
                                assertEquals(1, (int) row.getCell(1).getNumericCellValue());
                                assertTrue(row.getCell(4).getNumericCellValue() > 0, "Total cotisations AMIT > 0");
                            }
                        }
                    }
                    assertTrue(foundAmit, "L'organisme AMIT doit figurer sur la synthèse sociale mensuelle");

                    Sheet detailSheet = monthlyBook.getSheet("Détail nominatif");
                    assertNotNull(detailSheet);
                    boolean foundAmitInDetail = false;
                    for (int r = 0; r <= detailSheet.getLastRowNum(); r++) {
                        var row = detailSheet.getRow(r);
                        if (row != null && row.getCell(5) != null) {
                            if ("AMIT".equals(row.getCell(5).getStringCellValue())) {
                                foundAmitInDetail = true;
                            }
                        }
                    }
                    assertTrue(foundAmitInDetail, "L'organisme AMIT doit figurer dans le détail nominatif mensuel");
                }

                // 7. Tester la consolidation trimestrielle avec AMIT
                QuarterlySocialDataService.Data qData = quarterlyService.load(2026, 1, c);
                assertNotNull(qData);
                assertTrue(qData.distinctOrganismes().contains("AMIT"),
                        "Les organismes distincts doivent contenir AMIT. Trouvés: " + qData.distinctOrganismes());

                byte[] consolidationBytes = quarterlyService.render(qData, "consolidation");
                assertNotNull(consolidationBytes);

                try (Workbook consBook = WorkbookFactory.create(new ByteArrayInputStream(consolidationBytes))) {
                    Sheet qSummary = consBook.getSheet("Synthèse");
                    assertNotNull(qSummary);
                    boolean amitInConsolidationSummary = false;
                    for (int r = 0; r <= qSummary.getLastRowNum(); r++) {
                        var row = qSummary.getRow(r);
                        if (row != null && row.getCell(0) != null) {
                            if ("AMIT".equals(row.getCell(0).getStringCellValue())) {
                                amitInConsolidationSummary = true;
                                assertTrue(row.getCell(4).getNumericCellValue() > 0, "Total cotisations AMIT consolidé > 0");
                            }
                        }
                    }
                    assertTrue(amitInConsolidationSummary, "AMIT doit figurer dans la feuille Synthèse de la consolidation");

                    Sheet qDetail = consBook.getSheet("Par salarié et mois");
                    assertNotNull(qDetail);
                    var headerRow = qDetail.getRow(2); // row 2 ou 3
                    boolean hasAmitHeader = false;
                    for (int col = 0; col < headerRow.getLastCellNum(); col++) {
                        var cell = headerRow.getCell(col);
                        if (cell != null && cell.getStringCellValue().contains("AMIT")) {
                            hasAmitHeader = true;
                            break;
                        }
                    }
                    assertTrue(hasAmitHeader, "Des colonnes AMIT doivent être générées dynamiquement dans le détail trimestriel");
                }

                // 8. Tester une cotisation NON CONFIGURÉE et vérifier la présence de l'AVERTISSEMENT
                try (var s = c.createStatement()) {
                    s.execute("ALTER TABLE bulletin_paie_ligne DISABLE TRIGGER trg_bulletin_paie_ligne_immutabilite");
                    // Insérer une ligne de cotisation sans code_organisme et avec code inconnu
                    s.execute("INSERT INTO bulletin_paie_ligne (id_ligne, id_bulletin, id_rubrique, code_rubrique, libelle_rubrique, sens, categorie, base_calcul, montant_salarial, montant_patronal, ordre, imposable, soumis_cotisation) " +
                            "VALUES (9999, 1, 1, 'COTISATION_NON_CONFIG_SAL', 'Cotisation Spéciale Inconnue', 'RETENUE', 'COTISATION_SALARIALE', 100000, 2000, 0, 99, false, false)");
                    s.execute("ALTER TABLE bulletin_paie_ligne ENABLE TRIGGER trg_bulletin_paie_ligne_immutabilite");
                }

                // Exporter à nouveau le mois 1
                EtatSocialMensuelExportService.Result monthlyWarnResult = monthlyService.export(1, c);
                try (Workbook warnBook = WorkbookFactory.create(new ByteArrayInputStream(monthlyWarnResult.bytes()))) {
                    Sheet summarySheet = warnBook.getSheet("Synthèse sociale");
                    // La ligne d'avertissement doit être présente
                    boolean foundWarning = false;
                    boolean foundUnconfiguredOrg = false;
                    for (int r = 0; r <= summarySheet.getLastRowNum(); r++) {
                        var row = summarySheet.getRow(r);
                        if (row != null && row.getCell(0) != null) {
                            String val = row.getCell(0).getStringCellValue();
                            if (val.contains("AVERTISSEMENT") || val.contains("non configuré")) foundWarning = true;
                            if (EtatSocialMensuelExportService.UNCONFIGURED_ORGANISME.equals(val)) foundUnconfiguredOrg = true;
                        }
                    }
                    assertTrue(foundWarning, "L'avertissement d'organisme non configuré doit être affiché");
                    assertTrue(foundUnconfiguredOrg, "Le groupe 'Organisme non configuré' doit être affiché");
                }

                // Consolidation avec la ligne non configurée
                QuarterlySocialDataService.Data qWarnData = quarterlyService.load(2026, 1, c);
                assertTrue(qWarnData.hasUnconfigured(), "Le jeu trimestriel doit détecter des cotisations non configurées");
                byte[] consWarnBytes = quarterlyService.render(qWarnData, "consolidation");
                try (Workbook warnConsBook = WorkbookFactory.create(new ByteArrayInputStream(consWarnBytes))) {
                    Sheet qSummary = warnConsBook.getSheet("Synthèse");
                    boolean foundConsWarning = false;
                    for (int r = 0; r <= qSummary.getLastRowNum(); r++) {
                        var row = qSummary.getRow(r);
                        if (row != null && row.getCell(0) != null) {
                            String val = row.getCell(0).getStringCellValue();
                            if (val.contains("AVERTISSEMENT") || val.contains("non configuré")) foundConsWarning = true;
                        }
                    }
                    assertTrue(foundConsWarning, "L'avertissement doit être présent dans la consolidation trimestrielle");
                }

            } finally {
                c.rollback();
            }
        }
    }
}
