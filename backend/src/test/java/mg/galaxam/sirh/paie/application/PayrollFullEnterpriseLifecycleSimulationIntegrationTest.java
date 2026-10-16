package mg.galaxam.sirh.paie.application;

import mg.galaxam.sirh.conge.application.CongeAnnuelExcelExportService;
import mg.galaxam.sirh.conge.application.CongeGrandRecapExcelExportService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.CongeModeReprise;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService.CutoverOpeningRequest;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.AttestationTravailDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.CertificatTravailDTO;
import mg.galaxam.sirh.contrat.api.dto.DocumentRhDTO.ContratTravailDocumentDTO;
import mg.galaxam.sirh.contrat.application.CarriereExportService;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculResultDTO;
import mg.galaxam.sirh.paie.api.dto.stc.StcRecuDTO;
import mg.galaxam.sirh.paie.application.export.BulletinDocumentService;
import mg.galaxam.sirh.paie.application.export.AnnualPayrollExportService;
import mg.galaxam.sirh.paie.application.export.DntsExportService;
import mg.galaxam.sirh.paie.application.export.EtatSocialMensuelExportService;
import mg.galaxam.sirh.paie.application.export.GrandLivrePaieExportService;
import mg.galaxam.sirh.paie.application.export.IrsaExcelExportService;
import mg.galaxam.sirh.paie.application.export.QuarterlySocialDataService;
import mg.galaxam.sirh.paie.application.export.SafiExportService;
import mg.galaxam.sirh.paie.application.export.VirementBancaireExportService;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.PayrollBatchResult;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.pointage.api.dto.FichePointageEmployeDTO;
import mg.galaxam.sirh.pointage.api.dto.PointageJourDTO;
import mg.galaxam.sirh.pointage.application.PointageDocumentData;
import mg.galaxam.sirh.pointage.application.PointageExcelExportService;
import mg.galaxam.sirh.pointage.application.PointagePdfExportService;
import mg.galaxam.sirh.pointage.application.PointageSyntheseService;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SIMULATION COMPLÈTE DU CYCLE DE VIE DE L'ENTREPRISE SUR LE LONG TERME.
 */
@SpringBootTest
@ActiveProfiles("test")
public class PayrollFullEnterpriseLifecycleSimulationIntegrationTest {

    @Autowired private DataSource dataSource;
    @Autowired private PayrollService payrollService;
    @Autowired private StcService stcService;
    @Autowired private PointageSyntheseService pointageSyntheseService;
    @Autowired private PointageExcelExportService pointageExcelExportService;
    @Autowired private PointagePdfExportService pointagePdfExportService;
    @Autowired private VirementBancaireExportService virementBancaireExportService;
    @Autowired private SafiExportService safiExportService;
    @Autowired private mg.galaxam.sirh.paie.application.export.SafiXlsExportService safiXlsExportService;
    @Autowired private IrsaExcelExportService irsaExcelExportService;
    @Autowired private GrandLivrePaieExportService grandLivrePaieExportService;
    @Autowired private BulletinDocumentService bulletinDocumentService;
    @Autowired private DntsExportService dntsExportService;
    @Autowired private QuarterlySocialDataService quarterlySocialDataService;
    @Autowired private EtatSocialMensuelExportService etatSocialMensuelExportService;
    @Autowired private AnnualPayrollExportService annualPayrollExportService;
    @Autowired private DocumentRhService documentRhService;
    @Autowired private CarriereExportService carriereExportService;
    @Autowired private CongeTauxApplicableService congeTauxApplicableService;
    @Autowired private CongeAnnuelExcelExportService congeAnnuelExcelExportService;
    @Autowired private CongeGrandRecapExcelExportService congeGrandRecapExcelExportService;

    @Test
    @DisplayName("Simulation exhaustive de la vie de l'entreprise : cutover, RH, pointage, congés, paie, STC et documents officiels")
    void simulationVieEntrepriseLongTermeEtFichiers() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertTestDatabase(c);
            c.setAutoCommit(false);

            try {
                CongeTestFixtureHelper.cleanDatabase(c);

                // =========================================================================
                // ÉTAPE 1 : FONDATION DE L'ENTREPRISE & EMBAUCHE DE LA COHORTE
                // =========================================================================
                int adminId = CongeTestFixtureHelper.createAdminUser(c, "super_daf_" + System.nanoTime());

                // Désactiver les employés des autres suites uniquement dans cette transaction
                try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = FALSE")) {
                    ps.executeUpdate();
                }

                long suffix = Math.abs(System.nanoTime() % 1_000_000L);

                // Salarié 1 : Cadre Dirigeant (CDI) - BNI Madagascar, 2 enfants à charge
                int emp1Id = creerEmployeTest(c, "CADRE-01", "Rakoto", "Jean", String.format("101%09d", suffix + 1));
                configurerEmployeBancaire(c, emp1Id, "BNI Madagascar", "00005 00123 12345678901 25", 2);
                int contrat1Id = CongeTestFixtureHelper.createContratActif(c, emp1Id, LocalDate.of(2026, 7, 1));
                definirNumeroContrat(c, contrat1Id, "CTR-CDI-CADRE-01");
                CongeTestFixtureHelper.createAffectation(c, emp1Id, contrat1Id, LocalDate.of(2026, 7, 1), new BigDecimal("1200000.00"));

                // Salarié 2 : Technicien d'Atelier (CDI) - BOA Madagascar, 0 enfant
                int emp2Id = creerEmployeTest(c, "TECH-02", "Rabe", "Michel", String.format("101%09d", suffix + 2));
                configurerEmployeBancaire(c, emp2Id, "Bank of Africa Madagascar", "00009 00456 98765432109 88", 0);
                int contrat2Id = CongeTestFixtureHelper.createContratActif(c, emp2Id, LocalDate.of(2026, 7, 1));
                definirNumeroContrat(c, contrat2Id, "CTR-CDI-TECH-02");
                CongeTestFixtureHelper.createAffectation(c, emp2Id, contrat2Id, LocalDate.of(2026, 7, 1), new BigDecimal("500000.00"));

                // Salarié 3 : consultant sous contrat de travail, inclus dans la paie salariale.
                int emp3Id = creerEmployeTest(c, "CONS-03", "Andria", "Faly", String.format("101%09d", suffix + 3));
                configurerEmployeBancaire(c, emp3Id, "BNI Madagascar", "00005 00999 55555555555 10", 0);
                int contrat3Id = creerContratConsultant(c, emp3Id, LocalDate.of(2026, 7, 1), new BigDecimal("900000.00"));
                definirNumeroContrat(c, contrat3Id, "CTR-PREST-CONS-03");

                // Salarié 4 : Opérateur Polyvalent (CDD / Essai au 01/08/2026)
                int emp4Id = creerEmployeTest(c, "OPER-04", "Randria", "Solo", String.format("101%09d", suffix + 4));
                configurerEmployeBancaire(c, emp4Id, "Bank of Africa Madagascar", "00009 00777 11111111111 44", 1);
                int contrat4Id = CongeTestFixtureHelper.createContratActif(c, emp4Id, LocalDate.of(2026, 8, 1));
                definirNumeroContrat(c, contrat4Id, "CTR-CDD-OPER-04");
                CongeTestFixtureHelper.createAffectation(c, emp4Id, contrat4Id, LocalDate.of(2026, 8, 1), new BigDecimal("380000.00"));

                // -------------------------------------------------------------------------
                // Production Documentaire RH 1 : Génération des Contrats de Travail (.docx)
                // -------------------------------------------------------------------------
                ContratTravailDocumentDTO docContratDto = documentRhService.preparerContrat(contrat1Id, c);
                assertNotNull(docContratDto);
                ByteArrayOutputStream osContrat = new ByteArrayOutputStream();
                documentRhService.genererContratDocx(docContratDto, osContrat);
                byte[] contratBytes = osContrat.toByteArray();
                assertTrue(contratBytes.length > 2000, "Le contrat .docx généré doit contenir le texte du modèle");
                try (XWPFDocument xdoc = new XWPFDocument(new ByteArrayInputStream(contratBytes))) {
                    assertTrue(xdoc.getParagraphs().stream().anyMatch(p -> p.getText().contains("CADRE-01") || p.getText().contains("Rakoto")));
                }

                // -------------------------------------------------------------------------
                // Production Documentaire RH 2 : Registre Unique du Personnel (Contrats CSV)
                // -------------------------------------------------------------------------
                ByteArrayOutputStream osRegistre = new ByteArrayOutputStream();
                carriereExportService.exportContratsCsv(osRegistre, c);
                String registreCsv = osRegistre.toString("UTF-8");
                assertTrue(registreCsv.contains("CADRE-01"));
                assertTrue(registreCsv.contains("TECH-02"));
                assertTrue(registreCsv.contains("OPER-04"));

                // -------------------------------------------------------------------------
                // ÉTAPE 2 : CUTOVER INITIAL CONGÉS DAF CERTIFIÉ
                // -------------------------------------------------------------------------
                // Salarié 1 dispose d'un stock d'ouverture de 10 jours acquis avec un TCONGE certifié de 35 000 Ar/j
                CutoverOpeningRequest cutoverReq = new CutoverOpeningRequest();
                cutoverReq.setIdEmploye(emp1Id);
                cutoverReq.setDateCutover(LocalDate.of(2026, 7, 1));
                cutoverReq.setDateDebutReference(LocalDate.of(2026, 1, 1));
                cutoverReq.setReferenceArrete("ARRETE-DAF-CUTOVER-2026");
                cutoverReq.setSoldeJours(new BigDecimal("10.00"));
                cutoverReq.setValeurSoldeOuverture(new BigDecimal("350000.00"));
                cutoverReq.setTauxJournalierApplicable(new BigDecimal("35000.00"));
                cutoverReq.setModeReprise(CongeModeReprise.AGGREGATED_OPENING);
                cutoverReq.setCumulDroitsOuverture(BigDecimal.ZERO);
                cutoverReq.setCumulValorisationOuverture(BigDecimal.ZERO);
                cutoverReq.setCommentaire("Reprise cutover certifiée DAF");

                int refCutoverId = congeTauxApplicableService.enregistrerCutoverSnapshot(cutoverReq, adminId, c);
                assertTrue(refCutoverId > 0, "La référence de cutover initial doit être créée");

                // Prêt d'entreprise accordé au Cadre : 300 000 Ar en 3 mensualités de 100 000 Ar
                int pretId = creerPretEntreprise(c, emp1Id, "PRET-ENTREPRISE-CADRE", new BigDecimal("300000.00"), 3, LocalDate.of(2026, 7, 1));

                // =========================================================================
                // PHASE 1 : CYCLE DU MOIS DE JUILLET 2026 (2026-07)
                // =========================================================================
                int p1Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-07",
                        LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), "BROUILLON");

                // Badgeages réels sur le mois de juillet
                insererPointagesComplets(c, emp1Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8, 0, 0, 0, false);
                // Employé 2 réalise des heures sup et du travail de nuit
                insererPointagesComplets(c, emp2Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8, 10, 5, 1, true);
                insererPointagesComplets(c, emp3Id, p1Id, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31), 8, 0, 0, 0, false);

                // Acompte de quinzaine accordé à Employé 1 (100 000 Ar)
                creerVariableApprouvee(c, emp1Id, p1Id, "ACOMPTE_15", "RETENUE", new BigDecimal("100000.00"), "Acompte quinzaine juillet");

                // Gel de la synthèse des pointages P1
                changerStatutPeriode(c, p1Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p1Id, null, c);

                // Production Fiches de présence individuelles mensuelles (Excel & PDF)
                FichePointageEmployeDTO ficheEmp2P1 = new FichePointageEmployeDTO();
                ficheEmp2P1.setIdEmploye(emp2Id);
                ficheEmp2P1.setMatricule("TECH-02");
                ficheEmp2P1.setNomComplet("Rabe Michel");
                ficheEmp2P1.setDateDebut("2026-07-01");
                ficheEmp2P1.setDateFin("2026-07-31");
                ficheEmp2P1.setStatutPeriode("VALIDEE");
                ficheEmp2P1.setFonction("Technicien");
                ficheEmp2P1.setCategorie("HC");
                ficheEmp2P1.setSiteNom("Siège");
                List<PointageJourDTO> journal = new ArrayList<>();
                for (int d = 1; d <= 22; d++) {
                    PointageJourDTO j = new PointageJourDTO();
                    j.setDateComplete(String.format("2026-07-%02d", d));
                    j.setDate(String.format("%02d/07", d));
                    j.setJour("Lun.");
                    j.setStatut("PRESENT");
                    j.setEntree("08:00");
                    j.setSortiePause("12:00");
                    j.setRetourPause("13:00");
                    j.setSortie("17:00");
                    j.setHeures("08h00");
                    j.setObservation("Travail effectif");
                    journal.add(j);
                }
                ficheEmp2P1.setPointagesData(journal);

                PointageDocumentData docDataEmp2P1 = PointageDocumentData.from(ficheEmp2P1);
                byte[] feuillePresenceExcel = pointageExcelExportService.genererExcelFicheEmploye(docDataEmp2P1);
                assertNotNull(feuillePresenceExcel);
                assertTrue(feuillePresenceExcel.length > 1000, "Le classeur de pointage Excel doit être généré");

                byte[] feuillePresencePdf = pointagePdfExportService.generer(docDataEmp2P1);
                assertNotNull(feuillePresencePdf);
                assertTrue(feuillePresencePdf.length > 1000, "La fiche de présence PDF doit être générée");
                try (var pdf = Loader.loadPDF(feuillePresencePdf)) {
                    String text = new PDFTextStripper().getText(pdf);
                    assertTrue(text.contains("FICHE DE PRÉSENCE") || text.contains("TECH-02"));
                }

                // Calcul et validation de la paie P1
                PayrollBatchResult batchP1 = payrollService.calculerPaiePeriode(p1Id, false, adminId, c);
                assertEquals(3, batchP1.getNbBulletinsCalcules(), "Les 3 salariés actifs en juillet doivent avoir un bulletin");

                payrollService.validerPaiePeriode(p1Id, adminId, c);
                changerStatutPeriode(c, p1Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p1Id, adminId, c);
                changerStatutPeriode(c, p1Id, "CLOTUREE");

                // Contrôle Trésorerie P1 : 1ère échéance de prêt déduite (100 000 Ar)
                verifierCapitalRestantPret(c, pretId, new BigDecimal("200000.00"));

                // -------------------------------------------------------------------------
                // Fichiers Réglementaires & Institutionnels P1
                // -------------------------------------------------------------------------
                // 1. Virement bancaire CSV
                String virementP1 = virementBancaireExportService.exporterVirementCsv(p1Id, c);
                assertNotNull(virementP1);
                assertTrue(virementP1.contains("BNI Madagascar"));
                assertTrue(virementP1.contains("Bank of Africa Madagascar"));

                // 2. Déclaration Fiscale SAFI IRSA (CSV 25 colonnes et XLS DGI)
                String safiCsvP1 = safiExportService.exporterSafiCsv(p1Id, c);
                assertNotNull(safiCsvP1);
                assertTrue(safiCsvP1.contains("TOTAL;"));
                byte[] safiXlsP1 = safiXlsExportService.export(p1Id, c);
                assertNotNull(safiXlsP1);
                assertTrue(safiXlsP1.length > 1000);
                try (var workbook = new org.apache.poi.hssf.usermodel.HSSFWorkbook(
                        new java.io.ByteArrayInputStream(safiXlsP1))) {
                    assertEquals("État 301 SAFI", workbook.getSheetAt(0).getSheetName());
                    assertEquals("N° Ordre", workbook.getSheetAt(0).getRow(5).getCell(0).getStringCellValue());
                }

                // 3. Déclaration IRSA officielle XLS
                var irsaXlsResult = irsaExcelExportService.export(p1Id, c);
                assertNotNull(irsaXlsResult);
                assertTrue(irsaXlsResult.bytes().length > 1000);

                // 4. Grand Livre de Paie mensuel Excel avec équilibre et contrôles
                var glpP1 = grandLivrePaieExportService.export(p1Id, c);
                assertNotNull(glpP1);
                assertTrue(glpP1.bytes().length > 1000);
                try (Workbook wbGlp = WorkbookFactory.create(new ByteArrayInputStream(glpP1.bytes()))) {
                    assertEquals(3, wbGlp.getNumberOfSheets());
                    assertEquals("Grand livre", wbGlp.getSheetAt(0).getSheetName());
                    assertEquals("Détail rubriques", wbGlp.getSheetAt(1).getSheetName());
                    assertEquals("Contrôle", wbGlp.getSheetAt(2).getSheetName());
                }

                // 6. Bulletins de paie mensuels (PDF & Excel)
                BulletinPaie bp1Emp1 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p1Id, emp1Id, "MENSUEL", c);
                assertNotNull(bp1Emp1);
                BulletinPaieComplet completEmp1P1 = payrollService.obtenirBulletinComplet(bp1Emp1.getIdBulletin(), c);
                BulletinDocumentService.Context docCtxP1 = bulletinDocumentService.context(completEmp1P1, c);
                byte[] pdfEmp1 = bulletinDocumentService.pdf(completEmp1P1, docCtxP1);
                assertNotNull(pdfEmp1);
                assertTrue(pdfEmp1.length > 1000);
                byte[] excelEmp1 = bulletinDocumentService.excel(completEmp1P1, docCtxP1);
                assertNotNull(excelEmp1);
                assertTrue(excelEmp1.length > 500);

                // =========================================================================
                // PHASE 2 : CYCLE DU MOIS D'AOÛT 2026 (2026-08) - ÉVÉNEMENTS & TRANSITIONS
                // =========================================================================
                // 1. Augmentation salariale pour Salarié 1 (1 200 000 -> 1 500 000 Ar)
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_fin = '2026-07-31' WHERE id_employe = ? AND date_fin IS NULL")) {
                    ps.setInt(1, emp1Id);
                    ps.executeUpdate();
                }
                CongeTestFixtureHelper.createAffectation(c, emp1Id, contrat1Id, LocalDate.of(2026, 8, 1), new BigDecimal("1500000.00"));

                // 2. Attestation de travail demandée par Salarié 1 (.docx)
                AttestationTravailDTO attestationDto = documentRhService.preparerAttestation(emp1Id, "contrat", true, c);
                assertNotNull(attestationDto);
                ByteArrayOutputStream osAttestation = new ByteArrayOutputStream();
                documentRhService.genererAttestationDocx(attestationDto, osAttestation);
                byte[] attestationBytes = osAttestation.toByteArray();
                assertTrue(attestationBytes.length > 2000, "L'attestation de travail .docx doit être générée");

                // 3. Prise de congés payés par Salarié 1 (3 jours du 10 au 12 août)
                creerDemandeCongeValidee(c, emp1Id, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), 3);

                // 4. Événement familial rémunéré pour Salarié 4 (Mariage : 3 jours du 24 au 26 août)
                creerDemandeEvenementFamilial(c, emp4Id, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26), 3);

                int p2Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-08",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "BROUILLON");

                // Saisie des pointages réels pour les 4 salariés
                insererPointagesComplets(c, emp1Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8, 0, 0, 0, false);
                marquerPointagesStatut(c, emp1Id, p2Id, LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 12), "CONGE");

                insererPointagesComplets(c, emp2Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8, 0, 0, 0, false);
                // Incident d'absence non justifiée pour Salarié 2 (180 minutes)
                enregistrerAbsencePointage(c, emp2Id, p2Id, LocalDate.of(2026, 8, 17), 180);

                insererPointagesComplets(c, emp3Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8, 0, 0, 0, false);

                // Salarié 4 est actif depuis le 01/08
                insererPointagesComplets(c, emp4Id, p2Id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 8, 0, 0, 0, false);
                marquerPointagesStatut(c, emp4Id, p2Id, LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26), "CONGE");

                // Gel de la synthèse des pointages P2
                changerStatutPeriode(c, p2Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p2Id, null, c);

                // Calcul et validation paie P2
                PayrollBatchResult batchP2 = payrollService.calculerPaiePeriode(p2Id, false, adminId, c);
                assertEquals(4, batchP2.getNbBulletinsCalcules(), "Les 4 salariés doivent avoir un bulletin en août");

                BulletinPaie bp2Emp1 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p2Id, emp1Id, "MENSUEL", c);
                assertNotNull(bp2Emp1);
                // Salaire de base contractuel actualisé à 1 500 000 Ar
                assertEquals(new BigDecimal("1500000.00"), bp2Emp1.getSalaireBase());
                // Retenue de congé au trentième : 1 500 000 / 30 * 3 = 150 000 Ar
                BulletinPaieComplet completEmp1P2 = payrollService.obtenirBulletinComplet(bp2Emp1.getIdBulletin(), c);
                BigDecimal dedCongeEmp1 = completEmp1P2.getMontantLigneSalariale("DEDUCTION_CONGE_BASE");
                assertEquals(new BigDecimal("150000.00"), dedCongeEmp1);

                // Retenue d'absence sur Salarié 2 (500 000 / 173.33 * 3h = 8 654.01 Ar)
                BulletinPaie bp2Emp2 = BulletinPaie.trouverParPeriodeEtEmployeEtType(p2Id, emp2Id, "MENSUEL", c);
                assertNotNull(bp2Emp2);
                BulletinPaieComplet completEmp2P2 = payrollService.obtenirBulletinComplet(bp2Emp2.getIdBulletin(), c);
                BigDecimal retAbsEmp2 = completEmp2P2.getMontantLigneSalariale("RETENUE_ABSENCE");
                assertEquals(new BigDecimal("8654.01"), retAbsEmp2);

                payrollService.validerPaiePeriode(p2Id, adminId, c);
                changerStatutPeriode(c, p2Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p2Id, adminId, c);
                changerStatutPeriode(c, p2Id, "CLOTUREE");

                // Contrôle Trésorerie P2 : 2ème mensualité de prêt déduite (reste 100 000 Ar)
                verifierCapitalRestantPret(c, pretId, new BigDecimal("100000.00"));

                // =========================================================================
                // PHASE 3 : CYCLE DU MOIS DE SEPTEMBRE 2026 (2026-09) - STC & SOLDE DU PRÊT
                // =========================================================================
                int p3Id = CongeTestFixtureHelper.createPeriodePaie(c, "2026-09",
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), "BROUILLON");

                insererPointagesComplets(c, emp1Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8, 0, 0, 0, false);
                insererPointagesComplets(c, emp2Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8, 0, 0, 0, false);
                insererPointagesComplets(c, emp3Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8, 0, 0, 0, false);
                insererPointagesComplets(c, emp4Id, p3Id, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), 8, 0, 0, 0, false);

                changerStatutPeriode(c, p3Id, "VALIDEE");
                pointageSyntheseService.gelerPeriode(p3Id, null, c);

                PayrollBatchResult batchP3 = payrollService.calculerPaiePeriode(p3Id, false, adminId, c);
                assertEquals(4, batchP3.getNbBulletinsCalcules());

                payrollService.validerPaiePeriode(p3Id, adminId, c);
                changerStatutPeriode(c, p3Id, "PAIE_VALIDEE");
                payrollService.cloturerDonneesPaie(p3Id, adminId, c);
                changerStatutPeriode(c, p3Id, "CLOTUREE");

                // Contrôle Trésorerie P3 : Prêt intégralement remboursé (0.00 Ar) et statut SOLDE
                verifierCapitalRestantPret(c, pretId, BigDecimal.ZERO.setScale(2));
                verifierStatutPret(c, pretId, "SOLDE");

                // -------------------------------------------------------------------------
                // ÉTAPE DE SORTIE : DÉMISSION DE SALARIÉ 2 & SOLDE DE TOUT COMPTE (STC)
                // -------------------------------------------------------------------------
                // Le salaire de septembre est déjà validé : le STC porte sur le
                // départ d'octobre, dans une période distincte, après la rupture.
                int periodeSortieId = CongeTestFixtureHelper.createPeriodePaie(c, "2026-10-STC",
                        LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), "BROUILLON");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_fin = DATE '2026-10-10' WHERE id_contrat = ?")) {
                    ps.setInt(1, contrat2Id);
                    ps.executeUpdate();
                }
                try (Statement migration = c.createStatement()) {
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_contrat INT REFERENCES contrat_travail(id_contrat)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS source_pointage VARCHAR(30)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS id_bulletin_mensuel INT REFERENCES bulletin_paie(id_bulletin)");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS net_bulletin_mensuel NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS montant_bulletin_deja_paye NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS solde_bulletin_a_regler NUMERIC(20,2) NOT NULL DEFAULT 0");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS date_paiement_bulletin DATE");
                    migration.execute("ALTER TABLE paie_stc ADD COLUMN IF NOT EXISTS reference_paiement_bulletin VARCHAR(255)");
                }
                StcCalculRequest stcReq = new StcCalculRequest();
                stcReq.setJustificationSalaireDernierMois("Simulation historique : jours sous contrat contrôlés sans pointage final");
                stcReq.setIdEmploye(emp2Id);
                stcReq.setIdPeriodePaie(periodeSortieId);
                stcReq.setDateDepart(Date.valueOf(LocalDate.of(2026, 10, 10)));
                stcReq.setMotifRupture("DEMISSION");
                stcReq.setSimulation(false);
                stcReq.setModeReglement("VIREMENT");
                stcReq.setJoursTravaillesDernierMois(new BigDecimal("10.0"));

                StcCalculResultDTO stcResult = stcService.validerStc(stcReq, adminId, c);
                assertNotNull(stcResult);
                assertNotNull(stcResult.getIdStc());
                assertNotNull(stcResult.getNumeroRecu());
                assertTrue(stcResult.getNetAPayerStc().compareTo(BigDecimal.ZERO) > 0);
                assertTrue(stcResult.getIndemniteCongesPayes().compareTo(BigDecimal.ZERO) > 0);

                // Vérification clé : le solde de congés est apuré et remis strictement à zéro
                mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye soldeApresStc =
                        mg.galaxam.sirh.conge.persistence.VSoldeCongeEmploye.rechercherParEmploye(emp2Id, c);
                assertNotNull(soldeApresStc);
                assertEquals(0, soldeApresStc.getSoldeActuel().compareTo(BigDecimal.ZERO),
                        "Le solde de congés doit être remis à 0.00 après versement de l'ICCP au STC");

                // Reçu Libératoire officiel numéroté pour Solde de Tout Compte
                StcRecuDTO recuStc = stcService.obtenirRecuStc(stcResult.getIdStc(), c);
                assertNotNull(recuStc);
                assertEquals(stcResult.getNumeroRecu(), recuStc.getNumeroRecu());
                assertEquals("Rabe Michel", recuStc.getNomComplet());
                assertEquals("DEMISSION", recuStc.getMotifRupture());

                // Production du Certificat de Travail officiel (.docx)
                CertificatTravailDTO certDto = documentRhService.preparerCertificat(contrat2Id, "contrat", c);
                assertNotNull(certDto);
                ByteArrayOutputStream osCert = new ByteArrayOutputStream();
                documentRhService.genererCertificatDocx(certDto, osCert);
                byte[] certBytes = osCert.toByteArray();
                assertTrue(certBytes.length > 2000, "Le certificat de travail .docx doit être généré");
                try (XWPFDocument xdocCert = new XWPFDocument(new ByteArrayInputStream(certBytes))) {
                    assertTrue(xdocCert.getParagraphs().stream().anyMatch(p -> p.getText().contains("Rabe Michel") || p.getText().contains("TECH-02")));
                }

                // =========================================================================
                // PHASE 4 : DÉCLARATIONS TRIMESTRIELLES INSTITUTIONNELLES (T3 2026)
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

                // =========================================================================
                // PHASE 5 : BASCULE ANNUELLE TEMPORELLE & CLÔTURE DE L'EXERCICE 2026
                // =========================================================================
                // 1. Récapitulatif Annuel des Salaires Excel (Annual Payroll Export 2026)
                var annualResult = annualPayrollExportService.export(2026, c);
                assertNotNull(annualResult);
                assertTrue(annualResult.coveredMonths() >= 3, "Au moins 3 mois couverts en 2026");
                assertTrue(annualResult.bytes().length > 1000);
                try (Workbook wbAnnual = WorkbookFactory.create(new ByteArrayInputStream(annualResult.bytes()))) {
                    assertNotNull(wbAnnual.getSheet("Récap annuel"));
                    assertNotNull(wbAnnual.getSheet("Synthèse par mois"));
                    assertNotNull(wbAnnual.getSheet("Détail mensuel"));
                }

                // 2. État Individuel Annuel des Congés Excel (intégrant les 9 recommandations DAF)
                byte[] etatIndividuelConge = congeAnnuelExcelExportService.genererEtatCongesIndividuel(emp1Id, null, c);
                assertNotNull(etatIndividuelConge);
                assertTrue(etatIndividuelConge.length > 1000);
                try (Workbook wbIndiv = WorkbookFactory.create(new ByteArrayInputStream(etatIndividuelConge))) {
                    assertTrue(wbIndiv.getNumberOfSheets() >= 1);
                }

                // 3. Grand Récapitulatif DAF Congés annuel
                byte[] grandRecapConge = congeGrandRecapExcelExportService.generer(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 30), c);
                assertNotNull(grandRecapConge);
                assertTrue(grandRecapConge.length > 1000);
                try (Workbook wbRecap = WorkbookFactory.create(new ByteArrayInputStream(grandRecapConge))) {
                    assertTrue(wbRecap.getNumberOfSheets() >= 1);
                }

                // 4. Bilan social mensuel P3
                var etatSocialP3 = etatSocialMensuelExportService.export(p3Id, c);
                assertNotNull(etatSocialP3);
                assertTrue(etatSocialP3.bytes().length > 1000);

                System.out.println("==========================================================================");
                System.out.println("SUCCÈS : SIMULATION COMPLÈTE DE VIE D'ENTREPRISE ET DOCUMENTS VALIDÉE !");
                System.out.println("==========================================================================");

            } finally {
                // Annuler toute écriture pour préserver la base de test
                c.rollback();
            }
        }
    }

    // =========================================================================
    // UTILITAIRES DE CRÉATION ET CONFIGURATION DES TESTS
    // =========================================================================

    private void assertTestDatabase(Connection connection) throws SQLException {
        String dbName = connection.getCatalog();
        assertTrue(dbName != null && dbName.toLowerCase().contains("test"),
                "Le test de simulation d'entreprise doit impérativement s'exécuter sur une base de test. Actuelle: " + dbName);
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

    private void configurerEmployeBancaire(Connection c, int idEmploye, String banque, String rib, int nbEnfants) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = TRUE WHERE id_employe = ?")) {
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
                assertEquals(1, ps.executeUpdate());
            }
        }
    }

    private void definirNumeroContrat(Connection c, int idContrat, String numero) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE contrat_travail SET numero_contrat = ? WHERE id_contrat = ?")) {
            ps.setString(1, numero);
            ps.setInt(2, idContrat);
            ps.executeUpdate();
        }
    }

    private int creerContratConsultant(Connection c, int idEmploye, LocalDate debut, BigDecimal honoraire) throws Exception {
        int idContrat;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, date_debut) "
                        + "VALUES (?, 1, (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI' LIMIT 1), "
                        + "(SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'CONSULTANT_SALARIE'), 4, ?) "
                        + "RETURNING id_contrat")) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(debut));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idContrat = rs.getInt(1);
            }
        }
        CongeTestFixtureHelper.createAffectation(c, idEmploye, idContrat, debut, honoraire);
        return idContrat;
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

    private void insererPointagesComplets(Connection c, int employeeId, int periodId, LocalDate start, LocalDate end,
                                          int baseHours, int extraHsHours, int nightHours, int sundayWorked, boolean repas) throws Exception {
        String sql = "INSERT INTO pointage_jour (id_employe, id_periode_paie, id_site, date_pointage, id_statut_jour, "
                + "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, "
                + "heure_entree_matin_effective, total_heures, heures_nuit, version) "
                + "VALUES (?, ?, 1, ?, (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = 'PRESENT'), "
                + "'08:00', '12:00', '13:00', '17:00', '08:00', ?, ?, 1) RETURNING id_pointage";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
                if (day.getDayOfWeek().getValue() <= 5) { // Lundi au Vendredi
                    ps.setInt(1, employeeId);
                    ps.setInt(2, periodId);
                    ps.setDate(3, Date.valueOf(day));
                    int dayHours = baseHours + (day.getDayOfMonth() == 10 ? extraHsHours : 0);
                    ps.setInt(4, dayHours);
                    ps.setInt(5, day.getDayOfMonth() == 12 ? nightHours : 0);
                    int idPtg;
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        idPtg = rs.getInt(1);
                    }
                    if (repas) {
                        try (PreparedStatement psRepas = c.prepareStatement(
                                "INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, accorde, montant_calcule) "
                                        + "VALUES (?, (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1), TRUE, 2500.00)")) {
                            psRepas.setInt(1, idPtg);
                            psRepas.executeUpdate();
                        }
                    }
                }
            }
        }
    }

    private void marquerPointagesStatut(Connection c, int employeeId, int periodId, LocalDate debut, LocalDate fin, String codeStatut) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE pointage_jour SET id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), total_heures = 0 "
                        + "WHERE id_employe = ? AND id_periode_paie = ? AND date_pointage BETWEEN ? AND ?")) {
            ps.setString(1, codeStatut);
            ps.setInt(2, employeeId);
            ps.setInt(3, periodId);
            ps.setDate(4, Date.valueOf(debut));
            ps.setDate(5, Date.valueOf(fin));
            ps.executeUpdate();
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

    private void creerDemandeEvenementFamilial(Connection c, int employeeId, LocalDate debut, LocalDate fin, int jours) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO demande_conge (id_employe, id_type_conge, id_motif_evenement_familial, id_statut_demande_conge, date_debut, date_fin, "
                        + "nb_jours_decomptes, justificatif_fourni, commentaire, date_validation) "
                        + "VALUES (?, (SELECT id_type_conge FROM ref_type_conge WHERE code = 'EXCEPTIONNEL' LIMIT 1), "
                        + "(SELECT id_motif FROM ref_motif_evenement_familial WHERE code = 'MARIAGE' LIMIT 1), "
                        + "(SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'VALIDE'), "
                        + "?, ?, ?, TRUE, 'Mariage du salarié', CURRENT_TIMESTAMP)")) {
            ps.setInt(1, employeeId);
            ps.setDate(2, Date.valueOf(debut));
            ps.setDate(3, Date.valueOf(fin));
            ps.setBigDecimal(4, BigDecimal.valueOf(jours));
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
}
