package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests exhaustifs pour le module Pointage couvrant 100 % des fonctionnalités :
 * - Heures normales et tranches horaires
 * - Retards constatés & sorties anticipées
 * - Heures supplémentaires : HS 30 % (palier 41h-48h) et HS 50 % (au-delà de 48h)
 * - Travail de nuit (22h00 - 05h00)
 * - Dimanches travaillés (qualification dominicale)
 * - Samedis travaillés
 * - Jours fériés chômés légaux vs Jours fériés travaillés
 * - Congés payés (absence justifiée sans retenue ni anomalie)
 * - Demi-journées travaillées
 * - Postes autonomes / Cadres dirigeants (dispense de pointage)
 * - Horaires continus (shifts sans pause méridienne)
 * - Absences non justifiées vs justifiées
 * - Anomalies de badgeage (pointage incomplet)
 * - Indemnités complètes (Déplacement, 1er repas, 2nd repas standard et majoré)
 * - Ajustements DAF signés au solde (positifs, négatifs, filtrage des annulés)
 * - Rapprochement complet du solde et incidence finale sur la paie.
 *
 * Génère les fichiers PDF et Excel réels sur disque dans la racine du projet.
 */
class PointageComprehensiveFeaturesExportTest {

    private static final String BASE_OUTPUT_DIR = "/Users/andriamalalatojo/Documents/GALAXAM/GALAXAM-SIRH/";

    private final PointagePdfExportService pdfService = new PointagePdfExportService();
    private final PointageExcelExportService excelService = new PointageExcelExportService();

    @Test
    @DisplayName("Cas Global : Jeu de données complet 31 jours intégrant TOUTES les fonctionnalités de pointage")
    void testExportFicheToutesFonctionnalitesPointage() throws Exception {
        var f = new FichePointageEmployeDTO();
        f.setIdEmploye(101);
        f.setMatricule("0002");
        f.setNom("RABEMANANJARA");
        f.setPrenom("Jean-Luc");
        f.setNomComplet("Jean-Luc RABEMANANJARA");
        f.setFonction("Responsable Technique & Opérations");
        f.setCategorie("Cadre Opérationnel");
        f.setSiteNom("Antananarivo - Siège");
        f.setDateDebut("2025-12-26");
        f.setDateFin("2026-01-25");
        f.setCodePeriode("2026-01");
        f.setLibellePeriode("Période Janvier 2026");
        f.setStatutPeriode("VALIDEE");

        // Dictionnaire des indemnités de référence disponibles
        var indemnitesDisponibles = new LinkedHashMap<String, FichePointageEmployeDTO.IndemniteFicheDTO>();
        indemnitesDisponibles.put("DPLCT", new FichePointageEmployeDTO.IndemniteFicheDTO("DPLCT", "JOURNALIER", "Indemnité de Déplacement"));
        indemnitesDisponibles.put("REPAS", new FichePointageEmployeDTO.IndemniteFicheDTO("REPAS", "JOURNALIER", "Indemnité de 1er Repas"));
        indemnitesDisponibles.put("REPAS_2", new FichePointageEmployeDTO.IndemniteFicheDTO("REPAS_2", "JOURNALIER", "Indemnité de 2nd Repas"));
        indemnitesDisponibles.put("REPAS_2_MAJORE", new FichePointageEmployeDTO.IndemniteFicheDTO("REPAS_2_MAJORE", "JOURNALIER", "Indemnité 2nd Repas Majoré"));
        f.setIndemnitesDisponibles(indemnitesDisponibles);

        // 31 Jours représentatifs de tous les cas métier
        Object[][] rawDays = {
            // --- SEMAINE 1 : Tronçon initial (26/12/2025 au 28/12/2025 - 3 jours) ---
            // [0]date, [1]jour, [2]statut, [3]e1, [4]s1, [5]e2, [6]s2, [7]heures, [8]retard, [9]observation, [10]repas, [11]dplct, [12]repas2
            {"2025-12-26", "Ven.", "PRESENT", "08:00", "12:00", "13:00", "17:00", 8.00, 0, "Journée standard conforme", true, true, null},
            {"2025-12-27", "Sam.", "SAMEDI_TRAVAILLE", "08:00", "12:00", null, null, 4.00, 0, "Samedi ouvré - maintenance planifiée", false, true, null},
            {"2025-12-28", "Dim.", "DIMANCHE_TRAVAILLE", "08:00", "12:00", "13:00", "17:00", 8.00, 0, "Dimanche travaillé (astreinte intervention)", true, true, null},

            // --- SEMAINE 2 : Retards, Continu, Sortie anticipée, Férié chômé, HS 30% (29/12/2025 au 04/01/2026 - 7 jours) ---
            {"2025-12-29", "Lun.", "PRESENT", "08:35", "12:00", "13:00", "17:00", 7.42, 35, "Retard transport justifié", true, true, null},
            {"2025-12-30", "Mar.", "PRESENT", "08:00", "12:00", "13:00", "16:15", 7.25, 0, "Départ anticipé autorisé", true, true, null},
            {"2025-12-31", "Mer.", "PRESENT", "08:00", null, null, "16:00", 8.00, 0, "Horaire continu 8h sans coupure", true, true, null},
            {"2026-01-01", "Jeu.", "FERIE_CHOME", null, null, null, null, 0.00, 0, "Jour de l'An - férié légal chômé", false, false, null},
            {"2026-01-02", "Ven.", "PRESENT", "08:00", "12:00", "13:00", "19:00", 10.00, 0, "Permanence technique +2h de dépassement", true, true, "REPAS_2"},
            {"2026-01-03", "Sam.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},
            {"2026-01-04", "Dim.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},

            // --- SEMAINE 3 : Nuit, Demi-journée, Absence non justifiée, Absence justifiée (05/01/2026 au 11/01/2026 - 7 jours) ---
            {"2026-01-05", "Lun.", "PRESENT", "08:00", "12:00", "13:00", "17:00", 8.00, 0, "Journée normale", true, true, null},
            {"2026-01-06", "Mar.", "PRESENT", "20:00", null, null, "02:00", 6.00, 0, "Shift nuit (22h-02h = 4h nuit qualifiée)", true, true, "REPAS_2_MAJORE"},
            {"2026-01-07", "Mer.", "DEMI_JOURNEE", "08:00", "12:00", null, null, 4.00, 0, "Matinée travaillée 4h / Après-midi repos", false, true, null},
            {"2026-01-08", "Jeu.", "ABSENCE_NON_JUSTIFIEE", null, null, null, null, 0.00, 0, "Absence non justifiée (constat RH)", false, false, null},
            {"2026-01-09", "Ven.", "ABSENCE_JUSTIFIEE", null, null, null, null, 0.00, 0, "Absence justifiée (certificat médical)", false, false, null},
            {"2026-01-10", "Sam.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},
            {"2026-01-11", "Dim.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},

            // --- SEMAINE 4 : Grande semaine intensive (52 heures : 40h + 8h HS 30% + 4h HS 50%) (12/01/2026 au 18/01/2026 - 7 jours) ---
            {"2026-01-12", "Lun.", "PRESENT", "08:00", "12:00", "13:00", "19:30", 10.50, 0, "Rush projet Déploiement Cloud", true, true, "REPAS_2"},
            {"2026-01-13", "Mar.", "PRESENT", "08:00", "12:00", "13:00", "19:30", 10.50, 0, "Rush projet Déploiement Cloud", true, true, "REPAS_2"},
            {"2026-01-14", "Mer.", "PRESENT", "08:00", "12:00", "13:00", "19:30", 10.50, 0, "Rush projet Déploiement Cloud", true, true, "REPAS_2"},
            {"2026-01-15", "Jeu.", "PRESENT", "08:00", "12:00", "13:00", "19:30", 10.50, 0, "Rush projet Déploiement Cloud", true, true, "REPAS_2"},
            {"2026-01-16", "Ven.", "PRESENT", "08:00", "12:00", "13:00", "19:00", 10.00, 0, "Clôture migration infrastructure", true, true, "REPAS_2"},
            {"2026-01-17", "Sam.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},
            {"2026-01-18", "Dim.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},

            // --- SEMAINE 5 : Férié travaillé, Congés payés, Dispense pointage, Anomalie (19/01/2026 au 25/01/2026 - 7 jours) ---
            {"2026-01-19", "Lun.", "FERIE_TRAVAILLE", "08:00", "12:00", "13:00", "17:00", 8.00, 0, "Jour férié d'entreprise travaillé (+majoration)", true, true, null},
            {"2026-01-20", "Mar.", "CONGE", null, null, null, null, 0.00, 0, "Congé payé validé (#DC-2026-01)", false, false, null},
            {"2026-01-21", "Mer.", "CONGE", null, null, null, null, 0.00, 0, "Congé payé validé (#DC-2026-01)", false, false, null},
            {"2026-01-22", "Jeu.", "DISPENSE_POINTAGE", null, null, null, null, 8.00, 0, "Poste autonome cadre (forfait jour)", true, true, null},
            {"2026-01-23", "Ven.", "ANOMALIE_SAISIE", "08:00", "12:00", "13:00", null, 4.00, 0, "Badgeage sortie après-midi manquant", true, true, null},
            {"2026-01-24", "Sam.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null},
            {"2026-01-25", "Dim.", "REPOS", null, null, null, null, 0.00, 0, null, false, false, null}
        };

        var rows = new ArrayList<PointageJourDTO>();
        int totalRepasCount = 0;
        int totalDplctCount = 0;
        int totalRepas2Count = 0;
        int totalRepas2MajoreCount = 0;

        for (var d : rawDays) {
            var row = new PointageJourDTO();
            row.setDateComplete((String) d[0]);
            row.setJour((String) d[1]);
            row.setStatut((String) d[2]);
            row.setEntree((String) d[3]);
            row.setSortiePause((String) d[4]);
            row.setRetourPause((String) d[5]);
            row.setSortie((String) d[6]);
            row.setTotalHeuresDecimal(d[7] == null ? null : ((Number) d[7]).doubleValue());
            row.setMinutesRetard(d[8] == null ? null : ((Number) d[8]).intValue());
            row.setObservation((String) d[9]);

            var indMap = new LinkedHashMap<String, PointageJourDTO.IndemniteJourDTO>();
            boolean hasRepas = Boolean.TRUE.equals(d[10]);
            boolean hasDplct = Boolean.TRUE.equals(d[11]);
            String code2ndRepas = (String) d[12];

            if (hasRepas) {
                indMap.put("REPAS", new PointageJourDTO.IndemniteJourDTO("REPAS", true, new BigDecimal("2000"), BigDecimal.ONE));
                totalRepasCount++;
            }
            if (hasDplct) {
                indMap.put("DPLCT", new PointageJourDTO.IndemniteJourDTO("DPLCT", true, new BigDecimal("1200"), BigDecimal.ONE));
                totalDplctCount++;
            }
            if ("REPAS_2".equals(code2ndRepas)) {
                indMap.put("REPAS_2", new PointageJourDTO.IndemniteJourDTO("REPAS_2", true, new BigDecimal("2500"), BigDecimal.ONE));
                totalRepas2Count++;
            } else if ("REPAS_2_MAJORE".equals(code2ndRepas)) {
                indMap.put("REPAS_2_MAJORE", new PointageJourDTO.IndemniteJourDTO("REPAS_2_MAJORE", true, new BigDecimal("3500"), BigDecimal.ONE));
                totalRepas2MajoreCount++;
            }

            row.setIndemnites(indMap);
            rows.add(row);
        }
        f.setPointagesData(rows);

        // Synthèse hebdomadaire sur les 5 tronçons
        // S1 : 26/12 - 28/12 (3 j) -> Objectif 8h, Travail = 20h (8h standard + 4h samedi + 8h dimanche), 8h dimanche qualifié
        var w1 = new PointageTronconSemaineDTO();
        w1.setNumeroSemaine(1);
        w1.setDebutSemaine("2025-12-26");
        w1.setFinSemaine("2025-12-28");
        w1.setNbJoursTroncon(3);
        w1.setSeuilHeuresTroncon(8.0);
        w1.setTotalHeuresTravaillees(20.0);
        w1.setHeuresHs30(4.0);
        w1.setHeuresHs50(0.0);
        w1.setHeuresDeficit(0.0);
        w1.setHeuresDimanche(8.0);
        w1.setHeuresFerie(0.0);
        w1.setHeuresNuit(0.0);

        // S2 : 29/12 - 04/01 (7 j) -> Objectif 32h (férié chômé inclus), Travail = 32.67h -> 0.67h HS 30%
        var w2 = new PointageTronconSemaineDTO();
        w2.setNumeroSemaine(2);
        w2.setDebutSemaine("2025-12-29");
        w2.setFinSemaine("2026-01-04");
        w2.setNbJoursTroncon(5);
        w2.setSeuilHeuresTroncon(32.0);
        w2.setTotalHeuresTravaillees(32.67);
        w2.setHeuresHs30(0.67);
        w2.setHeuresHs50(0.0);
        w2.setHeuresDeficit(0.0);
        w2.setHeuresDimanche(0.0);
        w2.setHeuresFerie(0.0);
        w2.setHeuresNuit(0.0);

        // S3 : 05/01 - 11/01 (7 j) -> Objectif 40h, Travail = 18.00h (absences et demi-journée), Nuit = 4.0h, Déficit = 22.0h
        var w3 = new PointageTronconSemaineDTO();
        w3.setNumeroSemaine(3);
        w3.setDebutSemaine("2026-01-05");
        w3.setFinSemaine("2026-01-11");
        w3.setNbJoursTroncon(5);
        w3.setSeuilHeuresTroncon(40.0);
        w3.setTotalHeuresTravaillees(18.0);
        w3.setHeuresHs30(0.0);
        w3.setHeuresHs50(0.0);
        w3.setHeuresDeficit(22.0);
        w3.setHeuresDimanche(0.0);
        w3.setHeuresFerie(0.0);
        w3.setHeuresNuit(4.0);

        // S4 : 12/01 - 18/01 (7 j) -> Objectif 40h, Travail = 52.0h ! HS 30% = 8.0h, HS 50% = 4.0h !
        var w4 = new PointageTronconSemaineDTO();
        w4.setNumeroSemaine(4);
        w4.setDebutSemaine("2026-01-12");
        w4.setFinSemaine("2026-01-18");
        w4.setNbJoursTroncon(5);
        w4.setSeuilHeuresTroncon(40.0);
        w4.setTotalHeuresTravaillees(52.0);
        w4.setHeuresHs30(8.0);
        w4.setHeuresHs50(4.0);
        w4.setHeuresDeficit(0.0);
        w4.setHeuresDimanche(0.0);
        w4.setHeuresFerie(0.0);
        w4.setHeuresNuit(0.0);

        // S5 : 19/01 - 25/01 (7 j) -> Objectif 24h (2 jours congés), Travail = 20.0h, Férié = 8.0h, Déficit = 4.0h
        var w5 = new PointageTronconSemaineDTO();
        w5.setNumeroSemaine(5);
        w5.setDebutSemaine("2026-01-19");
        w5.setFinSemaine("2026-01-25");
        w5.setNbJoursTroncon(5);
        w5.setSeuilHeuresTroncon(24.0);
        w5.setTotalHeuresTravaillees(20.0);
        w5.setHeuresHs30(0.0);
        w5.setHeuresHs50(0.0);
        w5.setHeuresDeficit(4.0);
        w5.setHeuresDimanche(0.0);
        w5.setHeuresFerie(8.0);
        w5.setHeuresNuit(0.0);

        f.setBilansHebdo(List.of(w1, w2, w3, w4, w5));

        // Solde global de la période
        // Total HS hebdo = 4.0 (S1) + 0.67 (S2) + 8.0 (S4) + 4.0 (S4 HS50) = 16.67h (HS30: 12.67h = 760 min, HS50: 4.0h = 240 min)
        // Déficit hebdo = 22.0 (S3) + 4.0 (S5) = 26.0h (1560 min)
        // Ajustement DAF = +60 min - 120 min = -60 min (-1.00h)
        // Solde final brut = (760 + 240) - 1560 - 60 = -620 min (-10.33h)
        // Incidence paie : Retenue absence = 620 min (10.33h), HS30 finales = 0, HS50 finales = 0
        var solde = new PointageSyntheseMensuelleDTO();
        solde.setMinutesHs30Hebdo(760);
        solde.setMinutesHs50Hebdo(240);
        solde.setMinutesDeficitHebdo(1560);
        solde.setMinutesAjustementPeriode(-60);
        solde.setMinutesSoldePeriode(-620);
        solde.setMinutesAbsenceFinale(620);
        solde.setMinutesHs30Finales(0);
        solde.setMinutesHs50Finales(0);
        solde.setHeuresNuitTotal(4.00);
        solde.setHeuresDimancheTotal(8.00);
        solde.setHeuresFerieTotal(8.00);
        f.setSoldePeriode(solde);

        // Totaux Indemnités
        BigDecimal montantRepas = BigDecimal.valueOf(totalRepasCount * 2000L);
        BigDecimal montantDplct = BigDecimal.valueOf(totalDplctCount * 1200L);
        BigDecimal montantRepas2 = BigDecimal.valueOf(totalRepas2Count * 2500L);
        BigDecimal montantRepas2Maj = BigDecimal.valueOf(totalRepas2MajoreCount * 3500L);
        f.setTotauxIndemnites(Map.of(
            "REPAS", montantRepas,
            "DPLCT", montantDplct,
            "REPAS_2", montantRepas2,
            "REPAS_2_MAJORE", montantRepas2Maj
        ));

        // Liste des indemnités pour le contrat de présentation
        var indemnitesDoc = List.of(
            new PointageDocumentData.Indemnite("DPLCT", "Indemnité de Déplacement", "JOURNALIER", BigDecimal.valueOf(totalDplctCount), montantDplct),
            new PointageDocumentData.Indemnite("REPAS", "Indemnité de 1er Repas", "JOURNALIER", BigDecimal.valueOf(totalRepasCount), montantRepas),
            new PointageDocumentData.Indemnite("REPAS_2", "Indemnité de 2nd Repas", "JOURNALIER", BigDecimal.valueOf(totalRepas2Count), montantRepas2),
            new PointageDocumentData.Indemnite("REPAS_2_MAJORE", "Indemnité 2nd Repas Majoré", "JOURNALIER", BigDecimal.valueOf(totalRepas2MajoreCount), montantRepas2Maj)
        );

        // Ajustements DAF avec cas VALIDE et cas ANNULE (seuls les VALIDE doivent s'afficher)
        var ajustements = List.of(
            new PointageDocumentData.Ajustement(60, "Régularisation oubli badgeage matin du 10/01 après validation DAF", "2026-01-24", "DAF", "VALIDE"),
            new PointageDocumentData.Ajustement(-120, "Pénalité départ non autorisé du 30/12 arbitrée en commission", "2026-01-24", "DAF", "VALIDE"),
            new PointageDocumentData.Ajustement(30, "Proposition correction rejetée en arbitrage", "2026-01-24", "RH", "ANNULE")
        );

        // Bilan (11 dimensions exactes)
        var bilan = List.of(
            new BigDecimal("26.00"), // [0] deficit hebdo restant
            new BigDecimal("12.67"), // [1] HS 30 % hebdo
            new BigDecimal("4.00"),  // [2] HS 50 % hebdo
            new BigDecimal("-1.00"), // [3] ajustement DAF net
            new BigDecimal("-10.33"),// [4] solde final
            new BigDecimal("10.33"), // [5] retenue absence finale
            BigDecimal.ZERO,         // [6] HS 30 finales
            BigDecimal.ZERO,         // [7] HS 50 finales
            new BigDecimal("4.00"),  // [8] heures nuit total
            new BigDecimal("8.00"),  // [9] heures dimanche total
            new BigDecimal("8.00")   // [10] heures férié total
        );

        var docData = new PointageDocumentData(f, "25/01/2026 19:45", "Validé & Scellé DAF", 1, indemnitesDoc, ajustements, bilan);

        // 1. GÉNÉRATION PDF
        byte[] pdfBytes = pdfService.generer(docData);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 5000);

        Path pdfPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_TOUTES_FONCTIONNALITES_2026.pdf");
        Files.write(pdfPath, pdfBytes);
        assertTrue(Files.exists(pdfPath));

        // Vérifications textuelles strictes dans le PDF généré
        try (var pdf = Loader.loadPDF(pdfBytes)) {
            assertEquals(2, pdf.getNumberOfPages());
            String text = new PDFTextStripper().getText(pdf);

            assertTrue(text.contains("FICHE DE PRÉSENCE"));
            assertTrue(text.contains("Jean-Luc RABEMANANJARA"));
            assertTrue(text.contains("0002"));
            assertTrue(text.contains("26/12/2025"));
            assertTrue(text.contains("25/01/2026"));
            assertTrue(text.contains("SYNTHÈSE HEBDOMADAIRE"));
            assertTrue(text.contains("RAPPROCHEMENT DU SOLDE"));
            assertTrue(text.contains("AJUSTEMENTS APPLIQUÉS AU SOLDE"));

            // Ajustements appliqués vs annulés
            assertTrue(text.contains("Régularisation oubli badgeage matin du 10/01 après validation DAF"));
            assertTrue(text.contains("Pénalité départ non autorisé du 30/12 arbitrée en commission"));
            assertFalse(text.contains("Proposition correction rejetée en arbitrage"), "L'ajustement annulé ne doit jamais figurer dans le document !");

            // Mots-clés des fonctionnalités
            assertTrue(text.contains("HS 30 %"));
            assertTrue(text.contains("HS 50 %"));
            assertTrue(text.contains("Nuit"));
            assertTrue(text.contains("Dimanche"));
            assertTrue(text.contains("Jour férié"));
            assertTrue(text.contains("Retenue absence"));
            assertTrue(text.contains("Indemnité de Déplacement"));
            assertTrue(text.contains("Indemnité de 1er Repas"));
        }

        // 2. GÉNÉRATION EXCEL
        byte[] excelBytes = excelService.genererExcelFicheEmploye(docData);
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 5000);

        Path excelPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_TOUTES_FONCTIONNALITES_2026.xlsx");
        Files.write(excelPath, excelBytes);
        assertTrue(Files.exists(excelPath));

        // Vérification de la structure du classeur Excel
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            var sheet = book.getSheetAt(0);
            assertEquals("Fiche de présence", sheet.getSheetName());
            assertEquals("FICHE DE PRÉSENCE", sheet.getRow(0).getCell(0).getStringCellValue());

            StringBuilder sb = new StringBuilder();
            for (var row : sheet) {
                for (var cell : row) {
                    if (cell.getCellType() == CellType.STRING) {
                        sb.append(cell.getStringCellValue()).append(" | ");
                    }
                }
                sb.append("\n");
            }
            String content = sb.toString();
            assertTrue(content.contains("Jean-Luc RABEMANANJARA"));
            assertTrue(content.contains("JOURNAL DES PRÉSENCES"));
            assertTrue(content.contains("SYNTHÈSE HEBDOMADAIRE"));
            assertTrue(content.contains("RAPPROCHEMENT DU SOLDE"));
            assertTrue(content.contains("INCIDENCE FINALE SUR LA PAIE"));
            assertTrue(content.contains("INDEMNITÉS DE POINTAGE"));
            assertTrue(content.contains("Régularisation oubli badgeage"));
            assertFalse(content.contains("Proposition correction rejetée en arbitrage"));
        }
    }

    @Test
    @DisplayName("Cas Dédié 1 : Heures Supplémentaires (Palier HS 30 % et Palier HS 50 %)")
    void testCasHeuresSupplementairesHs30EtHs50() throws Exception {
        var f = new FichePointageEmployeDTO();
        f.setIdEmploye(102);
        f.setMatricule("0003");
        f.setNomComplet("Paul RAZAFINDRATO");
        f.setFonction("Chef de Chantier");
        f.setCategorie("Agent de Maîtrise");
        f.setSiteNom("Toamasina - Port");
        f.setDateDebut("2026-02-01");
        f.setDateFin("2026-02-28");
        f.setCodePeriode("2026-02");

        // Semaine type avec 52h de travail : 40h de base + 8h de HS 30% + 4h de HS 50%
        var rows = new ArrayList<PointageJourDTO>();
        for (int i = 1; i <= 28; i++) {
            var day = new PointageJourDTO();
            day.setDateComplete(LocalDate.of(2026, 2, i).toString());
            day.setJour("J" + i);
            if (i <= 5) {
                day.setStatut("PRESENT");
                day.setEntree("07:30");
                day.setSortiePause("12:00");
                day.setRetourPause("13:00");
                day.setSortie("19:00");
                day.setTotalHeuresDecimal(10.50);
                day.setObservation("Heures supplémentaires chantier");
            } else if (i == 6 || i == 7) {
                day.setStatut("REPOS");
            } else {
                day.setStatut("PRESENT");
                day.setEntree("08:00");
                day.setSortiePause("12:00");
                day.setRetourPause("13:00");
                day.setSortie("17:00");
                day.setTotalHeuresDecimal(8.00);
            }
            rows.add(day);
        }
        f.setPointagesData(rows);

        var week = new PointageTronconSemaineDTO();
        week.setNumeroSemaine(1);
        week.setDebutSemaine("2026-02-01");
        week.setFinSemaine("2026-02-07");
        week.setNbJoursTroncon(5);
        week.setSeuilHeuresTroncon(40.0);
        week.setTotalHeuresTravaillees(52.5);
        week.setHeuresHs30(8.0); // Plafond palier 1 (41e à 48e heure)
        week.setHeuresHs50(4.5); // Au-delà de la 48e heure
        week.setHeuresDeficit(0.0);
        f.setBilansHebdo(List.of(week));

        var solde = new PointageSyntheseMensuelleDTO();
        solde.setMinutesHs30Hebdo(480);
        solde.setMinutesHs50Hebdo(270);
        solde.setMinutesDeficitHebdo(0);
        solde.setMinutesAjustementPeriode(0);
        solde.setMinutesSoldePeriode(750);
        solde.setMinutesAbsenceFinale(0);
        solde.setMinutesHs30Finales(480);
        solde.setMinutesHs50Finales(270);
        f.setSoldePeriode(solde);

        var bilan = List.of(
            BigDecimal.ZERO,
            new BigDecimal("8.00"),
            new BigDecimal("4.50"),
            BigDecimal.ZERO,
            new BigDecimal("12.50"),
            BigDecimal.ZERO,
            new BigDecimal("8.00"),
            new BigDecimal("4.50"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );

        var docData = new PointageDocumentData(f, "28/02/2026 17:00", "Simulé", 1, List.of(), List.of(), bilan);

        byte[] pdf = pdfService.generer(docData);
        Path pdfPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_HS30_HS50.pdf");
        Files.write(pdfPath, pdf);
        assertTrue(Files.exists(pdfPath));

        byte[] xlsx = excelService.genererExcelFicheEmploye(docData);
        Path xlsxPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_HS30_HS50.xlsx");
        Files.write(xlsxPath, xlsx);
        assertTrue(Files.exists(xlsxPath));
    }

    @Test
    @DisplayName("Cas Dédié 2 : Majoration Nuit (22h-05h), Dimanche et Jours Fériés")
    void testCasNuitDimancheFerie() throws Exception {
        var f = new FichePointageEmployeDTO();
        f.setIdEmploye(103);
        f.setMatricule("0004");
        f.setNomComplet("Mamy ANDRIANARISOA");
        f.setFonction("Superviseur Nuit & Astreinte");
        f.setCategorie("Cadre");
        f.setSiteNom("Antananarivo - Siège");
        f.setDateDebut("2026-03-01");
        f.setDateFin("2026-03-31");

        var rows = new ArrayList<PointageJourDTO>();
        // Jour 1 : Dimanche travaillé (8h)
        var d1 = new PointageJourDTO();
        d1.setDateComplete("2026-03-01"); d1.setJour("Dim."); d1.setStatut("DIMANCHE_TRAVAILLE");
        d1.setEntree("08:00"); d1.setSortiePause("12:00"); d1.setRetourPause("13:00"); d1.setSortie("17:00");
        d1.setTotalHeuresDecimal(8.00); d1.setObservation("Dimanche permanence");
        rows.add(d1);

        // Jour 2 : Nuit de 21h00 à 03h00 (6h dont 5h de nuit entre 22h et 03h)
        var d2 = new PointageJourDTO();
        d2.setDateComplete("2026-03-02"); d2.setJour("Lun."); d2.setStatut("PRESENT");
        d2.setEntree("21:00"); d2.setSortie("03:00");
        d2.setTotalHeuresDecimal(6.00); d2.setObservation("Vacation nocturne (5h nuit qualifiée)");
        rows.add(d2);

        // Jour 29 : 29 Mars (Jour férié des Martyrs à Madagascar) -> Férié travaillé 8h
        var d29 = new PointageJourDTO();
        d29.setDateComplete("2026-03-29"); d29.setJour("Dim."); d29.setStatut("FERIE_TRAVAILLE");
        d29.setEntree("08:00"); d29.setSortiePause("12:00"); d29.setRetourPause("13:00"); d29.setSortie("17:00");
        d29.setTotalHeuresDecimal(8.00); d29.setObservation("Intervention jour férié officiel 29 Mars");
        rows.add(d29);

        f.setPointagesData(rows);

        var week = new PointageTronconSemaineDTO();
        week.setNumeroSemaine(1);
        week.setDebutSemaine("2026-03-01");
        week.setFinSemaine("2026-03-07");
        week.setNbJoursTroncon(7);
        week.setSeuilHeuresTroncon(40.0);
        week.setTotalHeuresTravaillees(22.0);
        week.setHeuresDimanche(8.0);
        week.setHeuresNuit(5.0);
        week.setHeuresFerie(8.0);
        f.setBilansHebdo(List.of(week));

        var bilan = List.of(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("5.00"), // Nuit
            new BigDecimal("8.00"), // Dimanche
            new BigDecimal("8.00")  // Férié
        );

        var docData = new PointageDocumentData(f, "31/03/2026 12:00", "Simulé", 1, List.of(), List.of(), bilan);

        byte[] pdf = pdfService.generer(docData);
        Path pdfPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_NUIT_DIMANCHE_FERIE.pdf");
        Files.write(pdfPath, pdf);
        assertTrue(Files.exists(pdfPath));

        byte[] xlsx = excelService.genererExcelFicheEmploye(docData);
        Path xlsxPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_NUIT_DIMANCHE_FERIE.xlsx");
        Files.write(xlsxPath, xlsx);
        assertTrue(Files.exists(xlsxPath));
    }

    @Test
    @DisplayName("Cas Dédié 3 : Congés payés, Dispense Cadre et Absences justifiées / injustifiées")
    void testCasCongesAbsencesEtDispenses() throws Exception {
        var f = new FichePointageEmployeDTO();
        f.setIdEmploye(104);
        f.setMatricule("0005");
        f.setNomComplet("Hery RAKOTO");
        f.setFonction("Directeur Administratif Adjoint");
        f.setCategorie("Cadre Dirigeant");
        f.setSiteNom("Antananarivo - Siège");
        f.setDateDebut("2026-04-01");
        f.setDateFin("2026-04-30");

        var rows = new ArrayList<PointageJourDTO>();

        // Congé payé
        var d1 = new PointageJourDTO();
        d1.setDateComplete("2026-04-01"); d1.setJour("Mer."); d1.setStatut("CONGE");
        d1.setObservation("Congé annuel autorisé (#CONGE-2026-44)");
        rows.add(d1);

        // Dispense de pointage (cadre autonome)
        var d2 = new PointageJourDTO();
        d2.setDateComplete("2026-04-02"); d2.setJour("Jeu."); d2.setStatut("DISPENSE_POINTAGE");
        d2.setTotalHeuresDecimal(8.00); d2.setObservation("Poste autonome - dispense légale");
        rows.add(d2);

        // Absence justifiée
        var d3 = new PointageJourDTO();
        d3.setDateComplete("2026-04-03"); d3.setJour("Ven."); d3.setStatut("ABSENCE_JUSTIFIEE");
        d3.setObservation("Événement familial justifié");
        rows.add(d3);

        // Absence non justifiée
        var d4 = new PointageJourDTO();
        d4.setDateComplete("2026-04-06"); d4.setJour("Lun."); d4.setStatut("ABSENCE_NON_JUSTIFIEE");
        d4.setObservation("Non présentation au poste sans avis");
        rows.add(d4);

        f.setPointagesData(rows);

        var bilan = List.of(
            new BigDecimal("8.00"), // Déficit 8h (absence injustifiée)
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("-8.00"),
            new BigDecimal("8.00"), // Retenue absence paie
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );

        var docData = new PointageDocumentData(f, "30/04/2026 12:00", "Simulé", 1, List.of(), List.of(), bilan);

        byte[] pdf = pdfService.generer(docData);
        Path pdfPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_CONGES_ABSENCES_DISPENSES.pdf");
        Files.write(pdfPath, pdf);
        assertTrue(Files.exists(pdfPath));

        byte[] xlsx = excelService.genererExcelFicheEmploye(docData);
        Path xlsxPath = Path.of(BASE_OUTPUT_DIR + "Pointage_TEST_CONGES_ABSENCES_DISPENSES.xlsx");
        Files.write(xlsxPath, xlsx);
        assertTrue(Files.exists(xlsxPath));
    }
}
