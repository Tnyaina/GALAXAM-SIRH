package mg.galaxam.sirh.conge;

import mg.galaxam.sirh.conge.application.CongeAnnuelExcelExportService;
import mg.galaxam.sirh.conge.application.CongeGrandRecapExcelExportService;
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test de simulation long terme du module Congés :
 * - Reprise historique / Cutover initial (solde d'ouverture, réserve valorisée, TCONGE initial)
 * - Cycle annuel complet Janvier - Décembre (12 mois d'acquisitions de droits)
 * - Évolution salariale en milieu d'année (hausse du salaire impactant la valorisation mensuelle)
 * - Prises de congés multiples avec décompte FIFO sur les lots
 * - Étape de mi-année (Juin) avec export de l'État individuel et du Grand Récapitulatif DAF
 * - Clôture annuelle de fin d'année (Décembre) avec consolidation du cumul des droits et de la valorisation
 * - Calcul du nouveau taux moyen TCONGE annuel et activation automatique au 1er janvier suivant
 * - Transition de cycle vers la nouvelle année
 * - Génération de tous les récapitulatifs Excel sur disque pour audit visuel.
 */
@SpringBootTest
@ActiveProfiles("test")
class CongeLongTermSimulationTest {

    private static final String BASE_OUTPUT_DIR = "/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeTauxApplicableService congeTauxApplicableService;

    @Autowired
    private CongeService congeService;

    @Autowired
    private CongeAnnuelExcelExportService congeAnnuelExcelExportService;

    @Autowired
    private CongeGrandRecapExcelExportService congeGrandRecapExcelExportService;

    @Autowired
    private CongeLotValorisationService congeLotValorisationService;

    private int adminUserId;
    private int employeId;
    private int contratId;

    @BeforeEach
    void setUp() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            adminUserId = CongeTestFixtureHelper.createAdminUser(c, "admin.longterm@galaxam.mg");
            employeId = CongeTestFixtureHelper.createEmploye(c, "EMP-DG-001", "RANDRIANASOLO", "Eric");
            contratId = CongeTestFixtureHelper.createContratActif(c, employeId, LocalDate.of(2025, 1, 1));
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 1, 1), new BigDecimal("300000.00"));
        }
    }

    @Test
    @DisplayName("Simulation Long Terme Congés : Cutover -> 12 Mois -> Mi-Année -> Fin d'Année -> Transition Annuelle & Nouveaux Droits")
    void testSimulationCompleteCycleAnnuelEtTransition() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            System.out.println("==========================================================================");
            System.out.println("SIMULATION LONG TERME : CYCLE ANNUEL COMPLET CONGÉS & TRANSITION DE CYCLE");
            System.out.println("==========================================================================");

            // ========================================================================================
            // ÉTAPE 1 : CUTOVER INITIAL AU 01/01/2025 (Reprise de stock et de taux d'ouverture)
            // ========================================================================================
            LocalDate dateCutover = LocalDate.of(2025, 1, 1);
            BigDecimal soldeInitial = new BigDecimal("10.00");
            BigDecimal tcongeInitial = new BigDecimal("10000.00");
            BigDecimal valeurSoldeInitial = new BigDecimal("100000.00");

            var cutoverReq = new CongeTauxApplicableService.CutoverOpeningRequest();
            cutoverReq.setIdEmploye(employeId);
            cutoverReq.setDateCutover(dateCutover);
            cutoverReq.setReferenceArrete("ARRETE-DAF-CUTOVER-2025");
            cutoverReq.setDateDebutReference(dateCutover);
            cutoverReq.setSoldeJours(soldeInitial);
            cutoverReq.setValeurSoldeOuverture(valeurSoldeInitial);
            cutoverReq.setTauxJournalierApplicable(tcongeInitial);
            cutoverReq.setModeReprise(CongeTauxApplicableService.CongeModeReprise.AGGREGATED_OPENING);
            cutoverReq.setCumulDroitsOuverture(BigDecimal.ZERO);
            cutoverReq.setCumulValorisationOuverture(BigDecimal.ZERO);
            cutoverReq.setCommentaire("Cutover d'ouverture 2025 - Initialisation historique");

            int idRef2025 = congeTauxApplicableService.enregistrerCutoverSnapshot(cutoverReq, adminUserId, c);
            assertTrue(idRef2025 > 0, "La période de référence 2025 doit être créée");

            // Vérification de la situation initiale
            var situationInit = congeLotValorisationService.situation(employeId, dateCutover, c);
            assertEquals(0, soldeInitial.compareTo(situationInit.soldeJours()), "Le solde initial doit être de 10 jours");
            assertEquals(0, valeurSoldeInitial.compareTo(situationInit.soldeValorisation()), "La réserve valorisée initiale doit être de 100 000 Ar");

            System.out.println("\n[ÉTAPE 1 : CUTOVER INITIAL au 01/01/2025]");
            System.out.println("  -> Solde d'ouverture : " + soldeInitial + " jours");
            System.out.println("  -> TCONGE initial actif : " + tcongeInitial + " Ar/jour");
            System.out.println("  -> Réserve valorisée : " + valeurSoldeInitial + " Ar");

            // ========================================================================================
            // ÉTAPE 2 : PREMIER SEMESTRE 2025 (Janvier à Juin 2025 - 6 mois)
            // ========================================================================================
            // Salaire de base : 300 000 Ar. Droit mensuel : 2.50 j.
            // Valorisation mensuelle : (300 000 / 30) * 2.5 = 25 000.00 Ar.
            BigDecimal cumulJoursS1 = BigDecimal.ZERO;
            BigDecimal cumulValoS1 = BigDecimal.ZERO;

            for (int mois = 1; mois <= 6; mois++) {
                cumulJoursS1 = cumulJoursS1.add(new BigDecimal("2.50"));
                cumulValoS1 = cumulValoS1.add(new BigDecimal("25000.00"));
                BigDecimal tauxMoyen = cumulValoS1.divide(cumulJoursS1, 2, RoundingMode.HALF_UP);

                LocalDate dateMois = LocalDate.of(2025, mois, 25);
                insererJournalDroit(c, employeId, idRef2025, 2025, mois,
                        new BigDecimal("300000.00"), new BigDecimal("2.50"), new BigDecimal("25000.00"),
                        cumulJoursS1, cumulValoS1, tauxMoyen);

                insererMouvement(c, employeId, "ACQUISITION", new BigDecimal("2.50"), dateMois,
                        "Acquisition mensuelle paie " + mois + "/2025", new BigDecimal("25000.00"));
            }

            // Prise de congés au mois de Mai (5.0 jours)
            LocalDate datePriseMai = LocalDate.of(2025, 5, 20);
            insererMouvement(c, employeId, "PRISE", new BigDecimal("-5.00"), datePriseMai,
                    "Congé annuel de repos (5 jours)", null);

            // Situation au 30/06/2025 (Mi-Année)
            LocalDate dateMiAnnee = LocalDate.of(2025, 6, 30);
            var situationMiAnnee = congeLotValorisationService.situation(employeId, dateMiAnnee, c);

            // Solde attendu : 10j initial - 5j pris + (6 * 2.5j = 15j acquis) = 20.00 jours
            assertEquals(0, new BigDecimal("20.00").compareTo(situationMiAnnee.soldeJours()),
                    "Le solde net au 30/06/2025 doit être de 20.00 jours");

            // Valeur du solde attendue selon FIFO :
            // Lot 1 (Reprise) : 10j - 5j = 5j restants à 10 000 Ar = 50 000 Ar
            // Lots 2025 (Jan à Juin) : 15j à 10 000 Ar = 150 000 Ar
            // Total réserve valorisée = 200 000 Ar
            assertEquals(0, new BigDecimal("200000.00").compareTo(situationMiAnnee.soldeValorisation()),
                    "La valorisation du stock au 30/06/2025 doit être de 200 000 Ar");

            System.out.println("\n[ÉTAPE 2 : SITUATION À MI-ANNÉE au 30/06/2025]");
            System.out.println("  -> Droits acquis S1 : 15.00 jours (cumul valorisation : 150 000.00 Ar)");
            System.out.println("  -> Prise de congé : 5.00 jours imputés");
            System.out.println("  -> Solde disponible net : 20.00 jours");
            System.out.println("  -> Réserve valorisée du solde : 200 000.00 Ar");

            // ========================================================================================
            // ÉTAPE 3 : GÉNÉRATION DES RÉCAPITULATIFS DE MI-ANNÉE 2025
            // ========================================================================================
            // 1. État individuel à mi-année
            byte[] etatIndividuelMiAnnee = congeAnnuelExcelExportService.genererEtatCongesIndividuel(employeId, idRef2025, c);
            assertNotNull(etatIndividuelMiAnnee);
            assertTrue(etatIndividuelMiAnnee.length > 5000);
            Path pathEtatMiAnnee = Path.of(BASE_OUTPUT_DIR + "Conge_ETAT_INDIVIDUEL_MI_ANNEE_JUIN_2025.xlsx");
            Files.write(pathEtatMiAnnee, etatIndividuelMiAnnee);
            assertTrue(Files.exists(pathEtatMiAnnee));

            // Vérification du contenu du classeur État individuel
            try (var book = new XSSFWorkbook(new ByteArrayInputStream(etatIndividuelMiAnnee))) {
                assertEquals(4, book.getNumberOfSheets());
                var sheet1 = book.getSheetAt(0);
                assertTrue(sheet1.getRow(2).getCell(0).getStringCellValue().contains("EMP-DG-001"));
                assertTrue(sheet1.getRow(2).getCell(2).getStringCellValue().contains("RANDRIANASOLO"));
            }

            // 2. Grand Récapitulatif DAF à mi-année
            byte[] grandRecapMiAnnee = congeGrandRecapExcelExportService.generer(LocalDate.of(2025, 1, 1), dateMiAnnee, c);
            assertNotNull(grandRecapMiAnnee);
            assertTrue(grandRecapMiAnnee.length > 5000);
            Path pathGrandRecapMiAnnee = Path.of(BASE_OUTPUT_DIR + "Conge_GRAND_RECAP_DAF_MI_ANNEE_JUIN_2025.xlsx");
            Files.write(pathGrandRecapMiAnnee, grandRecapMiAnnee);
            assertTrue(Files.exists(pathGrandRecapMiAnnee));

            // ========================================================================================
            // ÉTAPE 4 : AUGMENTATION SALARIALE EN MI-ANNÉE AU 01/07/2025 (+20%)
            // ========================================================================================
            // Le salaire de base passe de 300 000 Ar à 360 000 Ar.
            // On borne l'affectation précédente au 30/06/2025 pour respecter la continuité temporelle
            try (PreparedStatement psCap = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET date_fin = ? WHERE id_employe = ? AND date_fin IS NULL")) {
                psCap.setDate(1, Date.valueOf(LocalDate.of(2025, 6, 30)));
                psCap.setInt(2, employeId);
                psCap.executeUpdate();
            }
            CongeTestFixtureHelper.createAffectation(c, employeId, contratId, LocalDate.of(2025, 7, 1), new BigDecimal("360000.00"));

            System.out.println("\n[ÉTAPE 4 : AUGMENTATION DE SALAIRE AU 01/07/2025]");
            System.out.println("  -> Salaire de base : 360 000.00 Ar (+20 %)");
            System.out.println("  -> Nouvelle valorisation mensuelle des droits : 30 000.00 Ar/mois (au lieu de 25 000 Ar)");

            // ========================================================================================
            // ÉTAPE 5 : SECOND SEMESTRE 2025 (Juillet à Décembre 2025 - 6 mois)
            // ========================================================================================
            BigDecimal cumulJoursTotal = cumulJoursS1;
            BigDecimal cumulValoTotal = cumulValoS1;

            for (int mois = 7; mois <= 12; mois++) {
                cumulJoursTotal = cumulJoursTotal.add(new BigDecimal("2.50"));
                cumulValoTotal = cumulValoTotal.add(new BigDecimal("30000.00"));
                BigDecimal tauxMoyen = cumulValoTotal.divide(cumulJoursTotal, 2, RoundingMode.HALF_UP);

                LocalDate dateMois = LocalDate.of(2025, mois, 25);
                insererJournalDroit(c, employeId, idRef2025, 2025, mois,
                        new BigDecimal("360000.00"), new BigDecimal("2.50"), new BigDecimal("30000.00"),
                        cumulJoursTotal, cumulValoTotal, tauxMoyen);

                insererMouvement(c, employeId, "ACQUISITION", new BigDecimal("2.50"), dateMois,
                        "Acquisition mensuelle paie " + mois + "/2025", new BigDecimal("30000.00"));
            }

            // Prise de congés au mois d'Octobre (6.0 jours)
            // Épuise les 5.0 jours restants du stock initial, et consomme 1.0 jour sur l'acquisition 2025
            LocalDate datePriseOctobre = LocalDate.of(2025, 10, 15);
            insererMouvement(c, employeId, "PRISE", new BigDecimal("-6.00"), datePriseOctobre,
                    "Congé annuel d'automne (6 jours)", null);

            // Bilan annuel des 12 mois
            // Total droits acquis 2025 = 12 * 2.5 = 30.00 jours
            // Total valorisation 2025 = (6 * 25 000) + (6 * 30 000) = 150 000 + 180 000 = 330 000.00 Ar
            // TCONGE consolidé de la période = 330 000 / 30 = 11 000.00 Ar/jour
            assertEquals(0, new BigDecimal("30.00").compareTo(cumulJoursTotal));
            assertEquals(0, new BigDecimal("330000.00").compareTo(cumulValoTotal));

            LocalDate dateFinAnnee = LocalDate.of(2025, 12, 31);
            var situationFinAnnee = congeLotValorisationService.situation(employeId, dateFinAnnee, c);

            // Solde net au 31/12/2025 : 10j initial - 11j pris au total + 30j acquis = 29.00 jours
            assertEquals(0, new BigDecimal("29.00").compareTo(situationFinAnnee.soldeJours()),
                    "Le solde net au 31/12/2025 doit être de 29.00 jours");

            System.out.println("\n[ÉTAPE 5 : ACHÈVEMENT DU CYCLE ANNUEL 2025 au 31/12/2025]");
            System.out.println("  -> Total droits acquis 2025 : 30.00 jours");
            System.out.println("  -> Cumul valorisation 2025 : 330 000.00 Ar");
            System.out.println("  -> Total congés pris : 11.00 jours (stock initial épuisé à 100 %)");
            System.out.println("  -> Solde disponible net restant : 29.00 jours");

            // ========================================================================================
            // ÉTAPE 6 : CLÔTURE DU CYCLE 2025 & ACTIVATION DU NOUVEAU TCONGE 2026
            // ========================================================================================
            BigDecimal nouveauTcongeCalcule = congeTauxApplicableService.cloturerPeriodeReference(idRef2025, adminUserId, c);
            assertNotNull(nouveauTcongeCalcule);
            assertEquals(0, new BigDecimal("11000.00").compareTo(nouveauTcongeCalcule),
                    "Le nouveau TCONGE calculé doit être exactement de 11 000.00 Ar/jour (330 000 Ar / 30 j)");

            // Activation du nouveau taux au 01/01/2026
            LocalDate dateEffet2026 = LocalDate.of(2026, 1, 1);
            int idTaux2026 = congeTauxApplicableService.activerTauxCalcule(idRef2025, dateEffet2026, adminUserId, c);
            assertTrue(idTaux2026 > 0, "L'activation du nouveau taux 2026 doit réussir");

            // Vérification de la résolution par la Paie au 15/01/2026
            CongeTauxApplicableService.TauxCongeResolution res2026 = congeTauxApplicableService.resoudreTauxCongeApplicable(
                    employeId, LocalDate.of(2026, 1, 15), new BigDecimal("360000.00"), c);
            assertEquals(0, new BigDecimal("11000.00").compareTo(res2026.getTauxJournalier()),
                    "Le taux résolu en Janvier 2026 doit être le nouveau TCONGE consolidé de 11 000 Ar/jour");
            assertEquals(CongeTauxApplicableService.TcongeSourceProvenance.CALCUL_PERIODE_REFERENCE,
                    res2026.getSourceProvenance(), "La provenance doit être CALCUL_PERIODE_REFERENCE");

            System.out.println("\n[ÉTAPE 6 : CLÔTURE & BASCULE DU TAUX APPLICABLE TCONGE]");
            System.out.println("  -> Ancien TCONGE 2025 (Cutover) : 10 000.00 Ar/jour");
            System.out.println("  -> NOUVEAU TCONGE 2026 CALCULÉ : " + nouveauTcongeCalcule + " Ar/jour (Hausse de +10 %)");
            System.out.println("  -> Date d'effet du nouveau taux : " + dateEffet2026);
            System.out.println("  -> Source certifiée : " + res2026.getSourceProvenance());

            // ========================================================================================
            // ÉTAPE 7 : GÉNÉRATION DES RÉCAPITULATIFS DE FIN D'ANNÉE 2025
            // ========================================================================================
            // 1. État individuel annuel clôturé 2025
            byte[] etatIndividuelFinAnnee = congeAnnuelExcelExportService.genererEtatCongesIndividuel(employeId, idRef2025, c);
            assertNotNull(etatIndividuelFinAnnee);
            assertTrue(etatIndividuelFinAnnee.length > 5000);
            Path pathEtatFinAnnee = Path.of(BASE_OUTPUT_DIR + "Conge_ETAT_INDIVIDUEL_FIN_ANNEE_DECEMBRE_2025.xlsx");
            Files.write(pathEtatFinAnnee, etatIndividuelFinAnnee);
            assertTrue(Files.exists(pathEtatFinAnnee));

            // 2. Grand Récapitulatif DAF de l'année 2025
            byte[] grandRecapFinAnnee = congeGrandRecapExcelExportService.generer(LocalDate.of(2025, 1, 1), dateFinAnnee, c);
            assertNotNull(grandRecapFinAnnee);
            assertTrue(grandRecapFinAnnee.length > 5000);
            Path pathGrandRecapFinAnnee = Path.of(BASE_OUTPUT_DIR + "Conge_GRAND_RECAP_DAF_FIN_ANNEE_DECEMBRE_2025.xlsx");
            Files.write(pathGrandRecapFinAnnee, grandRecapFinAnnee);
            assertTrue(Files.exists(pathGrandRecapFinAnnee));

            // ========================================================================================
            // ÉTAPE 8 : PROJECTION CYCLE 2026 & CONTRÔLE MI-ANNÉE 2026
            // ========================================================================================
            // Ouverture de la période 2026
            int idRef2026 = congeTauxApplicableService.ensureLeaveReferencePeriod(
                    employeId, LocalDate.of(2026, 1, 25), c);
            assertTrue(idRef2026 > 0, "La période de référence 2026 doit être créée");

            // 6 mois d'acquisition en 2026 (Janvier à Juin 2026 à 360 000 Ar)
            BigDecimal cumul2026 = BigDecimal.ZERO;
            BigDecimal valo2026 = BigDecimal.ZERO;
            for (int mois = 1; mois <= 6; mois++) {
                cumul2026 = cumul2026.add(new BigDecimal("2.50"));
                valo2026 = valo2026.add(new BigDecimal("30000.00"));
                BigDecimal tauxMoyen2026 = valo2026.divide(cumul2026, 2, RoundingMode.HALF_UP);

                LocalDate dateMois = LocalDate.of(2026, mois, 25);
                insererJournalDroit(c, employeId, idRef2026, 2026, mois,
                        new BigDecimal("360000.00"), new BigDecimal("2.50"), new BigDecimal("30000.00"),
                        cumul2026, valo2026, tauxMoyen2026);

                insererMouvement(c, employeId, "ACQUISITION", new BigDecimal("2.50"), dateMois,
                        "Acquisition mensuelle paie " + mois + "/2026", new BigDecimal("30000.00"));
            }

            // Prise de congés en Mars 2026 (4 jours valorisés au nouveau TCONGE 11 000 Ar/j)
            insererMouvement(c, employeId, "PRISE", new BigDecimal("-4.00"), LocalDate.of(2026, 3, 10),
                    "Congé pris en 2026 valorisé au nouveau taux", null);

            LocalDate dateMiAnnee2026 = LocalDate.of(2026, 6, 30);
            var situation2026 = congeLotValorisationService.situation(employeId, dateMiAnnee2026, c);

            // Solde net 2026 : 29j reportés de 2025 - 4j pris + (6 * 2.5 = 15j acquis) = 40.00 jours
            assertEquals(0, new BigDecimal("40.00").compareTo(situation2026.soldeJours()),
                    "Le solde au 30/06/2026 doit être de 40.00 jours");

            // Génération de l'État Individuel Mi-Année 2026
            byte[] etat2026 = congeAnnuelExcelExportService.genererEtatCongesIndividuel(employeId, idRef2026, c);
            Path pathEtat2026 = Path.of(BASE_OUTPUT_DIR + "Conge_ETAT_INDIVIDUEL_MI_ANNEE_JUIN_2026.xlsx");
            Files.write(pathEtat2026, etat2026);
            assertTrue(Files.exists(pathEtat2026));

            // Génération du Grand Récapitulatif DAF Mi-Année 2026
            byte[] grandRecap2026 = congeGrandRecapExcelExportService.generer(LocalDate.of(2026, 1, 1), dateMiAnnee2026, c);
            Path pathGrandRecap2026 = Path.of(BASE_OUTPUT_DIR + "Conge_GRAND_RECAP_DAF_MI_ANNEE_JUIN_2026.xlsx");
            Files.write(pathGrandRecap2026, grandRecap2026);
            assertTrue(Files.exists(pathGrandRecap2026));

            System.out.println("\n[ÉTAPE 8 : PROJECTION RÉUSSIE SUR 2026]");
            System.out.println("  -> Solde disponible au 30/06/2026 : 40.00 jours");
            System.out.println("  -> Nouveaux classeurs 2026 générés avec succès.");
            System.out.println("==========================================================================\n");
        }
    }

    private void insererJournalDroit(Connection c, int idEmp, int idRef, int annee, int mois,
                                      BigDecimal base, BigDecimal droit, BigDecimal valMois,
                                      BigDecimal cumulJours, BigDecimal cumulVal, BigDecimal tauxMoyen) throws Exception {
        String sql = "INSERT INTO conge_journal_droit (" +
                "id_employe, annee, mois, salaire_base, base_valorisation, jours_droit_acquis, " +
                "valorisation_mensuelle, cumul_jours_droit, cumul_valorisation, taux_journalier_conge, " +
                "id_periode_reference" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmp);
            ps.setInt(2, annee);
            ps.setInt(3, mois);
            ps.setBigDecimal(4, base);
            ps.setBigDecimal(5, base);
            ps.setBigDecimal(6, droit);
            ps.setBigDecimal(7, valMois);
            ps.setBigDecimal(8, cumulJours);
            ps.setBigDecimal(9, cumulVal);
            ps.setBigDecimal(10, tauxMoyen);
            ps.setInt(11, idRef);
            ps.executeUpdate();
        }
    }

    private void insererMouvement(Connection c, int idEmp, String typeMvt, BigDecimal nbJours,
                                  LocalDate dateMvt, String commentaire, BigDecimal valeurSource) throws Exception {
        String sql = "INSERT INTO mouvement_solde_conge (" +
                "id_employe, type_mouvement, nb_jours, date_mouvement, commentaire, valeur_source" +
                ") VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmp);
            ps.setString(2, typeMvt);
            ps.setBigDecimal(3, nbJours);
            ps.setDate(4, Date.valueOf(dateMvt));
            ps.setString(5, commentaire);
            if (valeurSource != null) {
                ps.setBigDecimal(6, valeurSource);
            } else {
                ps.setNull(6, java.sql.Types.NUMERIC);
            }
            ps.executeUpdate();
        }
    }
}
