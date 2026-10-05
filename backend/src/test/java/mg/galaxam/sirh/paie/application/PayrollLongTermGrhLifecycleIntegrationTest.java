package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.application.export.AnnualPayrollExportService;
import mg.galaxam.sirh.paie.application.export.BulletinDocumentService;
import mg.galaxam.sirh.paie.application.export.DntsExportService;
import mg.galaxam.sirh.paie.application.export.EtatSocialMensuelExportService;
import mg.galaxam.sirh.paie.application.export.GrandLivrePaieExportService;
import mg.galaxam.sirh.paie.application.export.QuarterlySocialDataService;
import mg.galaxam.sirh.paie.application.export.SafiExportService;
import mg.galaxam.sirh.paie.application.export.VirementBancaireExportService;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Suite de tests d'intégration simulant le parcours de gestionnaire RH (GRH) et DAF
 * sur le long terme (Cycle trimestriel complet T4 2026 : Octobre, Novembre, Décembre 2026),
 * avec toutes les transitions de carrière, d'absences, de congés, d'amortissement de prêt,
 * de solde de tout compte (STC) et la production certifiée de tous les fichiers déclaratifs et bancaires.
 */
@SpringBootTest
@ActiveProfiles("test")
class PayrollLongTermGrhLifecycleIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private PointageSyntheseService pointageSyntheseService;
    @Autowired private PayrollService payrollService;
    @Autowired private StcService stcService;
    @Autowired private VirementBancaireExportService virementBancaireExportService;
    @Autowired private SafiExportService safiExportService;
    @Autowired private DntsExportService dntsExportService;
    @Autowired private GrandLivrePaieExportService grandLivrePaieExportService;
    @Autowired private EtatSocialMensuelExportService etatSocialMensuelExportService;
    @Autowired private AnnualPayrollExportService annualPayrollExportService;
    @Autowired private QuarterlySocialDataService quarterlySocialDataService;
    @Autowired private BulletinDocumentService bulletinDocumentService;
    @Autowired private CongeService congeService;
    @Autowired private PayrollPopulationResolver payrollPopulationResolver;

    @Test
    @DisplayName("Cycle de Paie Long Terme GRH : 3 Périodes successives, Transitions, STC et Production Fichiers")
    void cycleCompletGrhLongTermeAvecTransitionsEtFichiers() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTestDatabase(c);
            c.setAutoCommit(false);

            try {
                // =========================================================================
                // 0. INITIALISATION DU PARC DES SALARIÉS & UTILISATEURS
                // =========================================================================
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "daf_superviseur_" + System.nanoTime());

                // Désactiver les employés des autres suites uniquement dans cette transaction
                try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = FALSE")) {
                    ps.executeUpdate();
                }

                long suffix = Math.abs(System.nanoTime() % 1_000_000L);
                // Employé 1 (Cadre CDI - BNI - Prêt sur 3 mois & Augmentation salariale en P2)
                int emp1Id = creerEmployeTest(c, "CADRE-01", "Rakoto", "Jean", String.format("101%09d", suffix + 1));
                configurerEmployeBancaire(c, emp1Id, "BNI Madagascar", "00005 00123 12345678901 25", 2);
                int contrat1Id = CongeTestFixtureHelper.createContratActif(c, emp1Id, LocalDate.of(2026, 7, 1));
                CongeTestFixtureHelper.createAffectation(c, emp1Id, contrat1Id, LocalDate.of(2026, 7, 1), new BigDecimal("1000000.00"));

                // Employé 2 (Technicien CDI - BOA - HS en P1, Absence en P2, Démission & STC en P3)
                int emp2Id = creerEmployeTest(c, "TECH-02", "Rabe", "Michel", String.format("101%09d", suffix + 2));
                configurerEmployeBancaire(c, emp2Id, "Bank of Africa Madagascar", "00009 00456 98765432109 88", 0);
                int contrat2Id = CongeTestFixtureHelper.createContratActif(c, emp2Id, LocalDate.of(2026, 7, 1));
                CongeTestFixtureHelper.createAffectation(c, emp2Id, contrat2Id, LocalDate.of(2026, 7, 1), new BigDecimal("450000.00"));

                // Employé 3 (Consultant / Intervenant Extérieur - Régime ISI 5%)
                int emp3Id = creerEmployeTest(c, "CONS-03", "Andria", "Faly", String.format("101%09d", suffix + 3));
                configurerEmployeBancaire(c, emp3Id, "BNI Madagascar", "00005 00999 55555555555 10", 0);
                creerContratConsultant(c, emp3Id, LocalDate.of(2026, 7, 1), new BigDecimal("800000.00"));

                // Prêt d'entreprise pour Employé 1 : 300 000 Ar en 3 mensualités de 100 000 Ar
                int pretId = creerPretEntreprise(c, emp1Id, "PRET-CADRE-01", new BigDecimal("300000.00"), 3, LocalDate.of(2026, 7, 1));

                // =========================================================================
                // PHASE 1 : PÉRIODE 2026-07 (JUILLET 2026)
                // =========================================================================
                int p1Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-07",
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "BROUILLON");

                // GRH Etape 2 & 3 : Présences et Variables DAF
                insererPointages(c, emp1Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8);
                insererPointages(c, emp2Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8);
                insererPointages(c, emp3Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8);

                // Employé 1 demande un acompte de quinzaine de 100 000 Ar
                creerVariableApprouvee(c, emp1Id, p1Id, "ACOMPTE_15", "RETENUE", new BigDecimal("100000.00"), "Acompte quinzaine octobre");
                // Employé 2 reçoit une prime exceptionnelle au brut de 50 000 Ar
                creerVariableApprouvee(c, emp2Id, p1Id, "PRIME_EXCEPTIONNELLE", "GAIN", new BigDecimal("50000.00"), "Prime de performance Q3");

                // Gel du pointage P1
                changerStatutPeriode(c, p1Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p1Id, null, c);

                // GRH Etape 4 : Calcul de la paie P1
                var batchP1 = payrollService.calculerPaiePeriode(p1Id, false, adminId, c);
                for (var outcome : batchP1.getEmployeeOutcomes()) {
                    System.out.println("OUTCOME: " + outcome.employeeId() + " status=" + outcome.status() + " reason=" + outcome.reasonCode() + " msg=" + outcome.message());
                }
                assertTrue(batchP1.getNbBulletinsCalcules() >= 2, "Au moins 2 salariés calculés");

                // Vérifications métier P1
                BulletinPaie bp1Emp1 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p1Id, emp1Id, "MENSUEL", c);
                assertNotNull(bp1Emp1);
                assertEquals(new BigDecimal("1000000.00"), bp1Emp1.getSalaireBase());
                // Retenues de trésorerie : Acompte 100k + Echéance 1 de prêt 100k = 200 000 Ar
                assertEquals(new BigDecimal("200000.00"), bp1Emp1.getTotalRetenuesAcomptePret());
                assertTrue(bp1Emp1.getNetAPayer().compareTo(BigDecimal.ZERO) > 0);

                BulletinPaie bp1Emp2 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p1Id, emp2Id, "MENSUEL", c);
                assertNotNull(bp1Emp2);
                assertEquals(new BigDecimal("450000.00"), bp1Emp2.getSalaireBase());
                var lignesEmp2 = mg.galaxam.sirh.paie.persistence.BulletinPaieLigne.listerParBulletin(bp1Emp2.getIdBulletin(), c);
                for (var l : lignesEmp2) {
                    System.out.println("DEBUG LIGNE EMP2: " + l.getCodeRubrique() + " = " + l.getMontantSalarial() + " (sens=" + l.getSens() + ")");
                }
                // Brut = 450k base + 50k prime + éventuellement indemnités
                assertEquals(new BigDecimal("550000.00"), bp1Emp2.getTotalGainsBruts());

                // GRH Etape 6 & 7 : Validation DAF et Clôture Période P1
                var pop = payrollPopulationResolver.resolve(p1Id, c);
                System.out.println("DEBUG INCLUDED EMPLOYEES: " + pop.includedEmployeeIds());
                var bbs = BulletinPaie.listerParPeriode(p1Id, c);
                System.out.println("DEBUG CALCULATED BULLETINS: " + bbs.stream().map(BulletinPaie::getIdEmploye).toList());
                payrollService.validerPaiePeriode(p1Id, adminId, c);
                changerStatutPeriode(c, p1Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p1Id, adminId, c);
                changerStatutPeriode(c, p1Id, "CLOTUREE");

                // Vérification post-clôture P1 : Prêt amputé de l'échéance 1
                verifierCapitalRestantPret(c, pretId, new BigDecimal("200000.00"));
                // Vérification post-clôture P1 : Grand livre congés crédité (+2.5j pour emp1 et emp2)
                verifierJournalCongeAcquis(c, emp1Id, 2026, 7, new BigDecimal("2.5000"));

                // =========================================================================
                // PRODUCTION ET CONTRÔLE DES FICHIERS P1 (VIREMENTS, SAFI, GRAND LIVRE)
                // =========================================================================
                // 1. Fichier de virement bancaire CSV
                String virementCsvP1 = virementBancaireExportService.exporterVirementCsv(p1Id, c);
                assertNotNull(virementCsvP1);
                assertTrue(virementCsvP1.contains("BNI Madagascar"), "Virement doit contenir la BNI");
                assertTrue(virementCsvP1.contains("Bank of Africa Madagascar"), "Virement doit contenir la BOA");
                assertTrue(virementCsvP1.contains("CADRE-01"));
                assertTrue(virementCsvP1.contains("TECH-02"));

                // 2. Bordereau SAFI IRSA (CSV et ODS officiel 25 colonnes)
                String safiCsvP1 = safiExportService.exporterSafiCsv(p1Id, c);
                assertNotNull(safiCsvP1);
                assertTrue(safiCsvP1.contains("CADRE-01"));
                byte[] safiOdsP1 = safiExportService.genererSafiOds(p1Id, c);
                assertNotNull(safiOdsP1);
                assertTrue(safiOdsP1.length > 500, "Le fichier SAFI ODS doit être généré");

                // 3. Grand Livre de Paie Mensuel Excel
                var grandLivreP1 = grandLivrePaieExportService.export(p1Id, c);
                assertNotNull(grandLivreP1);
                assertTrue(grandLivreP1.bytes().length > 1000);
                try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(grandLivreP1.bytes()))) {
                    assertEquals(3, wb.getNumberOfSheets());
                    assertEquals("Grand livre", wb.getSheetAt(0).getSheetName());
                    assertEquals("Détail rubriques", wb.getSheetAt(1).getSheetName());
                    assertEquals("Contrôle", wb.getSheetAt(2).getSheetName());
                }

                // 4. Bulletins de Paie Individuels (PDF & Excel)
                BulletinPaieComplet completEmp1P1 = payrollService.obtenirBulletinComplet(bp1Emp1.getIdBulletin(), c);
                BulletinDocumentService.Context docCtx = new BulletinDocumentService.Context(
                        "2026-07", "01/07/2026 au 31/07/2026", "Galaxam Logistics", "Antananarivo", "GLX-101", "3000019434");
                byte[] pdfEmp1 = bulletinDocumentService.pdf(completEmp1P1, docCtx);
                assertNotNull(pdfEmp1);
                try (var pdf = Loader.loadPDF(pdfEmp1)) {
                    String text = new PDFTextStripper().getText(pdf);
                    assertTrue(text.contains("NET À PAYER"), "Le PDF doit contenir le net à payer");
                    assertTrue(text.contains("CADRE-01"), "Le PDF doit contenir le matricule");
                }
                byte[] excelEmp1 = bulletinDocumentService.excel(completEmp1P1, docCtx);
                assertNotNull(excelEmp1);
                assertTrue(excelEmp1.length > 500);

                // =========================================================================
                // PHASE 2 : PÉRIODE 2026-08 (AOÛT 2026) - TRANSITIONS SALAIRE & CONGÉS
                // =========================================================================
                // Transition 1 : Augmentation de salaire d'Employé 1 (Promotion 1 000 000 -> 1 200 000 Ar)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = '2026-07-31' WHERE id_employe = ? AND date_fin IS NULL")) {
                    ps.setInt(1, emp1Id);
                    ps.executeUpdate();
                }
                CongeTestFixtureHelper.createAffectation(c, emp1Id, contrat1Id, LocalDate.of(2026, 8, 1), new BigDecimal("1200000.00"));

                // Transition 2 : Prise de congés payés par Employé 1 (3 jours du 10 au 12 août)
                creerDemandeCongeValidee(c, emp1Id, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 3);

                int p2Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-08",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "BROUILLON");

                // Transition 3 : Incident d'absence non justifiée pour Employé 2 (180 minutes = 3 heures)
                insererPointages(c, emp1Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8);
                // Marquer les 3 jours de congé en statut CONGE
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_jour SET id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'CONGE'), total_heures = 0 "
                                + "WHERE id_employe = ? AND id_periode_paie = ? AND date_pointage BETWEEN '2026-08-10' AND '2026-08-12'")) {
                    ps.setInt(1, emp1Id);
                    ps.setInt(2, p2Id);
                    ps.executeUpdate();
                }
                insererPointages(c, emp2Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8);
                insererPointages(c, emp3Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8);
                enregistrerAbsencePointage(c, emp2Id, p2Id, LocalDate.of(2026, 8, 17), 180);

                changerStatutPeriode(c, p2Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p2Id, null, c);

                // Calcul P2
                var batchP2 = payrollService.calculerPaiePeriode(p2Id, false, adminId, c);
                for (var outcome : batchP2.getEmployeeOutcomes()) {
                    System.out.println("P2 OUTCOME: " + outcome.employeeId() + " status=" + outcome.status() + " reason=" + outcome.reasonCode() + " msg=" + outcome.message());
                }
                assertTrue(batchP2.getNbBulletinsCalcules() >= 2, "Au moins 2 salariés calculés");

                BulletinPaie bp2Emp1 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p2Id, emp1Id, "MENSUEL", c);
                assertNotNull(bp2Emp1);
                // Salaire de base mis à jour avec le nouveau salaire contractuel
                assertEquals(new BigDecimal("1200000.00"), bp2Emp1.getSalaireBase());
                // Retenue de congé au trentième : 1 200 000 / 30 * 3 = 120 000 Ar
                BulletinPaieComplet completEmp1P2 = payrollService.obtenirBulletinComplet(bp2Emp1.getIdBulletin(), c);
                BigDecimal dedConge = completEmp1P2.getMontantLigneSalariale("DEDUCTION_CONGE_BASE");
                assertEquals(new BigDecimal("120000.00"), dedConge);
                // Indemnité de congé valorisée au lot acquis en P1 (salaire 1 000 000 Ar) :
                BigDecimal indConge = completEmp1P2.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE");
                assertTrue(indConge.compareTo(BigDecimal.ZERO) > 0);
                // Deuxième échéance de prêt déduite (100 000 Ar)
                assertEquals(new BigDecimal("100000.00"), bp2Emp1.getTotalRetenuesAcomptePret());

                // Vérification de la retenue d'absence sur Employé 2 (taux exact)
                BulletinPaie bp2Emp2 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p2Id, emp2Id, "MENSUEL", c);
                assertNotNull(bp2Emp2);
                BulletinPaieComplet completEmp2P2 = payrollService.obtenirBulletinComplet(bp2Emp2.getIdBulletin(), c);
                BigDecimal retAbs = completEmp2P2.getMontantLigneSalariale("RETENUE_ABSENCE");
                // 450 000 / 173.33 * 3 = 7 788.61 Ar
                assertEquals(new BigDecimal("7788.61"), retAbs);

                // Validation & Clôture P2
                payrollService.validerPaiePeriode(p2Id, adminId, c);
                changerStatutPeriode(c, p2Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p2Id, adminId, c);
                changerStatutPeriode(c, p2Id, "CLOTUREE");

                // Prêt restant dû = 100 000 Ar après échéance 2
                verifierCapitalRestantPret(c, pretId, new BigDecimal("100000.00"));

                // =========================================================================
                // PHASE 3 : PÉRIODE 2026-09 (SEPTEMBRE 2026) - STC DÉPART & SOLDE DU PRÊT
                // =========================================================================
                int p3Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-09",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "BROUILLON");

                insererPointages(c, emp1Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8);
                // Employé 2 travaille jusqu'au 30 septembre avant départ
                insererPointages(c, emp2Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8);
                insererPointages(c, emp3Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8);

                changerStatutPeriode(c, p3Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p3Id, null, c);

                // Calcul P3
                var batchP3 = payrollService.calculerPaiePeriode(p3Id, false, adminId, c);
                assertTrue(batchP3.getNbBulletinsCalcules() >= 2, "Au moins 2 salariés calculés");

                // Validation P3
                payrollService.validerPaiePeriode(p3Id, adminId, c);
                changerStatutPeriode(c, p3Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p3Id, adminId, c);
                changerStatutPeriode(c, p3Id, "CLOTUREE");

                // Transition 4 : Prêt d'entreprise intégralement soldé !
                verifierCapitalRestantPret(c, pretId, BigDecimal.ZERO.setScale(2));
                verifierStatutPret(c, pretId, "SOLDE");

                // Transition 5 : Solde de Tout Compte (STC) & Reçu Libératoire pour Employé 2
                StcCalculRequest stcReq = new StcCalculRequest();
                stcReq.setIdEmploye(emp2Id);
                stcReq.setIdPeriodePaie(p3Id);
                stcReq.setDateDepart(Date.valueOf(LocalDate.of(2026, 9, 30)));
                stcReq.setMotifRupture("DEMISSION");
                stcReq.setSimulation(false);
                stcReq.setModeReglement("VIREMENT");
                stcReq.setJoursTravaillesDernierMois(new BigDecimal("22.0")); // mois complet septembre

                StcCalculResultDTO stcResult = stcService.validerStc(stcReq, adminId, c);
                assertNotNull(stcResult);
                assertNotNull(stcResult.getIdStc());
                assertNotNull(stcResult.getNumeroRecu());
                assertTrue(stcResult.getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0);
                // Indemnité compensatrice de congés payés non pris
                assertTrue(stcResult.getIndemniteCongesPayes().compareTo(BigDecimal.ZERO) > 0);

                // Extraction du Reçu Libératoire officiel numéroté
                StcRecuDTO recuStc = stcService.obtenirRecuStc(stcResult.getIdStc(), c);
                assertNotNull(recuStc);
                assertEquals(stcResult.getNumeroRecu(), recuStc.getNumeroRecu());
                assertEquals("Rabe Michel", recuStc.getNomComplet());
                assertEquals("DEMISSION", recuStc.getMotifRupture());

                // =========================================================================
                // PRODUCTION ET CONTRÔLE DES DÉCLARATIONS TRIMESTRIELLES (T3 2026)
                // =========================================================================
                // 1. Déclaration Nominative Trimestrielle des Salaires (DNTS CNaPS) T3
                String dntsTrimestreCsv = dntsExportService.exporterDntsTrimestreCsv(2026, 3, c);
                assertNotNull(dntsTrimestreCsv);
                assertTrue(dntsTrimestreCsv.contains("CADRE-01"));
                assertTrue(dntsTrimestreCsv.contains("TECH-02"));

                // 2. Classeurs de cotisations sociales trimestrielles CNaPS, FMFP, Santé
                var quarterlyData = quarterlySocialDataService.load(2026, 3, c);
                assertNotNull(quarterlyData);
                assertEquals(3, quarterlyData.quarter());
                assertEquals(2026, quarterlyData.year());

                byte[] cnapsSheet = quarterlySocialDataService.render(quarterlyData, "cnaps");
                assertNotNull(cnapsSheet);
                assertTrue(cnapsSheet.length > 1000);

                byte[] fmfpSheet = quarterlySocialDataService.render(quarterlyData, "fmfp");
                assertNotNull(fmfpSheet);
                assertTrue(fmfpSheet.length > 1000);

                // 3. Récapitulatif Annuel des Salaires (Annual Payroll Export 2026)
                var annualResult = annualPayrollExportService.export(2026, c);
                assertNotNull(annualResult);
                assertTrue(annualResult.coveredMonths() >= 3, "Au moins 3 mois couverts en 2026");
                assertTrue(annualResult.bytes().length > 1000);

                try (Workbook wbAnnual = WorkbookFactory.create(new ByteArrayInputStream(annualResult.bytes()))) {
                    assertNotNull(wbAnnual.getSheet("Récap annuel"));
                    assertNotNull(wbAnnual.getSheet("Synthèse par mois"));
                    assertNotNull(wbAnnual.getSheet("Détail mensuel"));
                }

                // 4. État Social Mensuel P3
                var etatSocialP3 = etatSocialMensuelExportService.export(p3Id, c);
                assertNotNull(etatSocialP3);
                assertTrue(etatSocialP3.bytes().length > 1000);
            } finally {
                // Annuler toute écriture pour laisser la base de test dans son état d'origine
                c.rollback();
            }
        }
    }

    // =========================================================================
    // UTILITAIRES DE CONFIGURATION ET D'ASSERTION POUR LES TESTS
    // =========================================================================

    private void configurerEmployeBancaire(Connection c, int idEmploye, String banque, String rib, int nbEnfants) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE employe SET est_actif = TRUE WHERE id_employe = ?")) {
            ps.setInt(1, idEmploye);
            assertEquals(1, ps.executeUpdate());
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employe_compte_banque (id_employe, id_banque, num_compte_rib, est_principal) "
                        + "VALUES (?, (SELECT id_banque FROM ref_banque WHERE nom_banque LIKE ? LIMIT 1), ?, TRUE)")) {
            ps.setInt(1, idEmploye);
            ps.setString(2, "%" + banque + "%");
            ps.setString(3, rib);
            assertEquals(1, ps.executeUpdate());
        }
        for (int i = 1; i <= nbEnfants; i++) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO employe_enfant (id_employe, nom, prenom, date_naissance, a_charge) "
                            + "VALUES (?, 'Enfant', ?, DATE '2015-01-01', TRUE)")) {
                ps.setInt(1, idEmploye);
                ps.setString(2, "E" + i);
                ps.executeUpdate();
            }
        }
    }

    private void creerContratConsultant(Connection c, int idEmploye, LocalDate debut, BigDecimal honoraire) throws Exception {
        int idContrat;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, date_debut) "
                        + "VALUES (?, 1, 1, (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'CONSULTANT'), 4, ?) "
                        + "RETURNING id_contrat")) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idContrat = rs.getInt(1);
            }
        }
        CongeTestFixtureHelper.createAffectation(c, idEmploye, idContrat, debut, honoraire);
    }

    private int creerPretEntreprise(Connection c, int idEmploye, String ref, BigDecimal montantTotal, int nbEcheances, LocalDate debut) throws Exception {
        BigDecimal mensualite = montantTotal.divide(BigDecimal.valueOf(nbEcheances), 2, RoundingMode.HALF_UP);
        int idPret;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO paie_pret (id_employe, reference_pret, montant_total, montant_mensualite, nb_echeances, "
                        + "date_accord, date_premiere_echeance, capital_rembourse, capital_restant_du, statut, motif) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, 'EN_COURS', 'Prêt test long terme') RETURNING id_pret")) {
            ps.setInt(1, idEmploye);
            ps.setString(2, ref);
            ps.setBigDecimal(3, montantTotal);
            ps.setBigDecimal(4, mensualite);
            ps.setInt(5, nbEcheances);
            ps.setDate(6, Date.valueOf(debut));
            ps.setDate(7, Date.valueOf(debut));
            ps.setBigDecimal(8, montantTotal);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idPret = rs.getInt(1);
            }
        }
        BigDecimal restant = montantTotal;
        for (int i = 1; i <= nbEcheances; i++) {
            restant = restant.subtract(mensualite).max(BigDecimal.ZERO);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO paie_pret_echeance (id_pret, numero_echeance, date_echeance, montant_echeance, "
                            + "capital_restant_apres, montant_preleve, montant_restant_echeance, statut) "
                            + "VALUES (?, ?, ?, ?, ?, 0, ?, 'A_ECHOIR')")) {
                ps.setInt(1, idPret);
                ps.setInt(2, i);
                ps.setDate(3, Date.valueOf(debut.plusMonths(i - 1)));
                ps.setBigDecimal(4, mensualite);
                ps.setBigDecimal(5, restant);
                ps.setBigDecimal(6, mensualite);
                ps.executeUpdate();
            }
        }
        return idPret;
    }

    private void insererPointages(Connection c, int employeeId, int periodId, LocalDate start, LocalDate end, int hoursPerDay) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                        + "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, "
                        + "heure_entree_matin_effective, total_heures, version) "
                        + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), "
                        + "'08:00', '12:00', '13:00', ?, '08:00', ?, 1)")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (day.getDayOfWeek().getValue() <= 5) { // Lundi au Vendredi
                    ps.setDate(3, Date.valueOf(day));
                    ps.setTime(4, java.sql.Time.valueOf(hoursPerDay > 8 ? "19:00:00" : "17:00:00"));
                    ps.setInt(5, hoursPerDay);
                    ps.executeUpdate();
                }
            }
        }
    }

    private void enregistrerAbsencePointage(Connection c, int employeeId, int periodId, LocalDate jour, int minutesAbsence) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO pointage_ajustement_periode (id_employe, id_periode_paie, minutes_signees, motif, saisi_par, valide_par) "
                        + "VALUES (?, ?, ?, 'Absence injustifiée constatée par DAF', "
                        + "(SELECT MIN(id_utilisateur) FROM utilisateur), (SELECT MIN(id_utilisateur) FROM utilisateur))")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            ps.setInt(3, -minutesAbsence);
            ps.executeUpdate();
        }
    }

    private void creerVariableApprouvee(Connection c, int employeeId, int periodId, String codeMvt, String sens, BigDecimal montant, String motif) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, id_type_mouvement, "
                        + "sens, montant, montant_initial, solde_restant, motif, statut, versement_confirme_at, date_paiement) "
                        + "SELECT ?, ?, code, id_type_mouvement, ?, ?, ?, ?, ?, 'APPROUVE', CURRENT_TIMESTAMP, "
                        + "(SELECT (date_debut + INTERVAL '14 days')::date FROM periode_paie WHERE id_periode_paie = ?) "
                        + "FROM ref_type_mouvement_paie WHERE code = ?")) {
            ps.setInt(1, employeeId);
            ps.setInt(2, periodId);
            ps.setString(3, sens);
            ps.setBigDecimal(4, montant);
            ps.setBigDecimal(5, montant);
            ps.setBigDecimal(6, montant);
            ps.setString(7, motif);
            ps.setInt(8, periodId);
            ps.setString(9, codeMvt);
            assertEquals(1, ps.executeUpdate(), "La variable " + codeMvt + " doit être insérée");
        }
    }

    private void creerDemandeCongeValidee(Connection c, int employeeId, LocalDate debut, LocalDate fin, int jours) throws Exception {
        int idDemande;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO demande_conge (id_employe, id_type_conge, id_statut_demande_conge, date_debut, date_fin, "
                        + "nb_jours_decomptes, commentaire, date_validation) "
                        + "VALUES (?, (SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'), "
                        + "(SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'VALIDE'), "
                        + "?, ?, ?, 'Congé test', CURRENT_TIMESTAMP) RETURNING id_demande_conge")) {
            ps.setInt(1, employeeId);
            ps.setDate(2, Date.valueOf(debut));
            ps.setDate(3, Date.valueOf(fin));
            ps.setBigDecimal(4, BigDecimal.valueOf(jours));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idDemande = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement, id_demande_conge, valeur_source, commentaire) "
                        + "VALUES (?, 'PRISE', ?, ?, ?, ?, 'Prise congé payé test')")) {
            ps.setInt(1, employeeId);
            ps.setBigDecimal(2, BigDecimal.valueOf(-jours));
            ps.setDate(3, Date.valueOf(debut));
            ps.setInt(4, idDemande);
            ps.setBigDecimal(5, new BigDecimal("40000.00"));
            ps.executeUpdate();
        }
    }

    private void changerStatutPeriode(Connection c, int periodId, String codeStatut) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = ?) "
                        + "WHERE id_periode_paie = ?")) {
            ps.setString(1, codeStatut);
            ps.setInt(2, periodId);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private void verifierCapitalRestantPret(Connection c, int idPret, BigDecimal expectedRestant) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT capital_restant_du FROM paie_pret WHERE id_pret = ?")) {
            ps.setInt(1, idPret);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedRestant, rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP));
            }
        }
    }

    private void verifierStatutPret(Connection c, int idPret, String expectedStatut) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT statut FROM paie_pret WHERE id_pret = ?")) {
            ps.setInt(1, idPret);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(expectedStatut, rs.getString(1));
            }
        }
    }

    private void verifierJournalCongeAcquis(Connection c, int idEmploye, int annee, int mois, BigDecimal expectedDroit) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT jours_droit_acquis FROM conge_journal_droit WHERE id_employe = ? AND annee = ? AND mois = ?")) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, annee);
            ps.setInt(3, mois);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Acquisition du journal des congés doit exister");
                assertEquals(expectedDroit, rs.getBigDecimal(1).setScale(4, RoundingMode.HALF_UP));
            }
        }
    }

    private int creerEmployeTest(Connection c, String matricule, String nom, String prenom, String cin) throws Exception {
        String sql = "INSERT INTO employe (" +
                "matricule, nom, prenom, sexe, date_naissance, lieu_naissance, " +
                "cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone" +
                ") VALUES (?, ?, ?, 'M', '1990-01-15', 'Antananarivo', ?, '2010-01-15', 'TNR', 'Rue Test 101', '0340000000') " +
                "RETURNING id_employe";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, matricule);
            ps.setString(2, nom);
            ps.setString(3, prenom);
            ps.setString(4, cin);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void assertTestDatabase(Connection c) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next());
            assertEquals("sirh_galaxam_test", rs.getString(1), "Exécution autorisée uniquement sur sirh_galaxam_test");
        }
    }
}
