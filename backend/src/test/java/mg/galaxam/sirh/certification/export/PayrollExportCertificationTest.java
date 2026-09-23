package mg.galaxam.sirh.certification.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.certification.dataset.CertificationDataset;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet;
import mg.galaxam.sirh.paie.domain.dto.export.DntsRowDto;
import mg.galaxam.sirh.paie.domain.dto.export.SafiRowDto;
import mg.galaxam.sirh.paie.domain.dto.export.VirementRowDto;
import mg.galaxam.sirh.paie.application.PayrollService;
import mg.galaxam.sirh.paie.application.export.PaieExportService;
import mg.galaxam.sirh.oracle.PayrollIndependentOracle;

@DisplayName("CERTIFICATION EXPORTS & JOURNAL DE PAIE — SAFI ODS/CSV, DNTS, VIREMENT, IMMUTABILITÉ")
public class PayrollExportCertificationTest extends BaseIntegrationTest {

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private PaieExportService paieExportService;

    private int idPeriode;
    private LocalDate debutPeriode;
    private LocalDate finPeriode;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        debutPeriode = LocalDate.of(2026, 9, 1);
        finPeriode = LocalDate.of(2026, 9, 30);

        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-09-EXP",
                debutPeriode,
                finPeriode,
                "EN_COURS"
        );
    }

    private void insererPointageStandard(int idEmp, double heuresTravaillees, double hAbsence) {
        String sql = "INSERT INTO pointage_cumul_periode (" +
                "id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents, " +
                "total_heures_travaillees, heures_absence_retenues, total_minutes_retard, " +
                "heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, " +
                "nb_jours_conge, montant_deplacement, gele_at) " +
                "VALUES (?, ?, 1, 'GELE', 22, 22, ?, ?, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, idEmp);
            ps.setDouble(3, heuresTravaillees);
            ps.setDouble(4, hAbsence);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("Erreur pointage standard : " + e.getMessage(), e);
        }
    }

    @Test
    @DisplayName("Certification globale : SAFI (ODS/CSV), DNTS, Virement, Balance Journal de Paie & Anti-Recalculation")
    void testExportsEtJournalDePaie() throws Exception {
        // =========================================================================
        // 1. POPULATION CERTIFIÉE : EMP001 (Standard) + EMP006 (Complexe)
        // =========================================================================
        CertificationDataset.EmployeeProfile p1 = CertificationDataset.EMP001_STANDARD;
        CreatedEmploye emp1 = employeFixtures.createEmployeWithSalary(
                p1.matricule(), p1.nom(), p1.prenom(), 1,
                p1.salaireBaseContractuel(), "PERMANENT", p1.nbEnfants()
        );
        insererPointageStandard(emp1.idEmploye, 173.33, 0.0);

        CertificationDataset.EmployeeProfile p6 = CertificationDataset.EMP006_COMPLEX;
        CreatedEmploye emp6 = employeFixtures.createEmployeWithSalary(
                p6.matricule(), p6.nom(), p6.prenom(), 2,
                p6.salaireBaseContractuel(), "PERMANENT", p6.nbEnfants()
        );
        employeFixtures.crediterSoldeConge(emp6.idEmploye, 15.0, new BigDecimal("20000.00"));

        // Pointage et buckets pour EMP006
        int idCumul6;
        String sqlCumul6 = "INSERT INTO pointage_cumul_periode (" +
                "id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents, " +
                "total_heures_travaillees, heures_absence_retenues, total_minutes_retard, " +
                "heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, " +
                "nb_jours_conge, montant_deplacement, gele_at) " +
                "VALUES (?, ?, 1, 'GELE', 22, 22, 186.00, 2.00, 120, 10.00, 4.00, 2.00, 4.00, 1.00, 30000.00, CURRENT_TIMESTAMP) " +
                "RETURNING id_cumul";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sqlCumul6)) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, emp6.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                idCumul6 = rs.getInt(1);
            }

            String sqlBucket = "INSERT INTO pointage_snapshot_bucket (" +
                    "id_cumul, id_periode_paie, date_debut, date_fin, minutes, overtime_tier, day_special_type, night_work_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psB = c.prepareStatement(sqlBucket)) {
                // Dimanche
                psB.setInt(1, idCumul6);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-06 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-06 12:00:00"));
                psB.setInt(5, 240);
                psB.setString(6, "REGULAR");
                psB.setString(7, "SUNDAY");
                psB.setString(8, "NONE");
                psB.addBatch();

                // Nuit
                psB.setInt(1, idCumul6);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-08 22:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-09 00:00:00"));
                psB.setInt(5, 120);
                psB.setString(6, "REGULAR");
                psB.setString(7, "STANDARD");
                psB.setString(8, "HABITUEL");
                psB.addBatch();

                // HS 30%
                psB.setInt(1, idCumul6);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-11 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-11 18:00:00"));
                psB.setInt(5, 600);
                psB.setString(6, "OVERTIME_30");
                psB.setString(7, "STANDARD");
                psB.setString(8, "NONE");
                psB.addBatch();

                // HS 50%
                psB.setInt(1, idCumul6);
                psB.setInt(2, idPeriode);
                psB.setTimestamp(3, java.sql.Timestamp.valueOf("2026-09-18 08:00:00"));
                psB.setTimestamp(4, java.sql.Timestamp.valueOf("2026-09-18 12:00:00"));
                psB.setInt(5, 240);
                psB.setString(6, "OVERTIME_50");
                psB.setString(7, "STANDARD");
                psB.setString(8, "NONE");
                psB.addBatch();

                psB.executeBatch();
            }

            // Variables de paie pour EMP006 : AUTIMP 40k, Acompte 50k
            String sqlVar1 = "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, sens, montant, date_demande, statut, motif) " +
                    "VALUES (?, ?, 'AUTIMP', 'GAIN', 40000.00, ?, 'APPROUVE', 'Prime rendement')";
            try (PreparedStatement psV = c.prepareStatement(sqlVar1)) {
                psV.setInt(1, emp6.idEmploye);
                psV.setInt(2, idPeriode);
                psV.setDate(3, Date.valueOf(debutPeriode));
                psV.executeUpdate();
            }

            String sqlVar2 = "INSERT INTO paie_avance_acompte (id_employe, id_periode_paie, type_mouvement, sens, montant, date_demande, statut, motif) " +
                    "VALUES (?, ?, 'ACOMPTE_15', 'RETENUE', 50000.00, ?, 'APPROUVE', 'Acompte')";
            try (PreparedStatement psV = c.prepareStatement(sqlVar2)) {
                psV.setInt(1, emp6.idEmploye);
                psV.setInt(2, idPeriode);
                psV.setDate(3, Date.valueOf(LocalDate.of(2026, 9, 15)));
                psV.executeUpdate();
            }
        }

        // =========================================================================
        // 2. EXÉCUTION & VALIDATION DE LA PAIE POUR LA PÉRIODE
        // =========================================================================
        BulletinPaieComplet bpc1 = payrollService.calculerBulletinEmploye(emp1.idEmploye, idPeriode, false, 1);
        BulletinPaieComplet bpc6 = payrollService.calculerBulletinEmploye(emp6.idEmploye, idPeriode, false, 1);
        assertNotNull(bpc1);
        assertNotNull(bpc6);

        payrollService.validerPaiePeriode(idPeriode, 1);

        // =========================================================================
        // 3. CERTIFICATION SAFI (ÉTAT 301 IRSA - DGI MADAGASCAR)
        // =========================================================================
        List<SafiRowDto> safiRows = paieExportService.obtenirDonneesSafi(idPeriode);
        assertEquals(2, safiRows.size(), "SAFI doit contenir exactement 2 lignes");

        SafiRowDto safi1 = safiRows.stream().filter(r -> p1.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("400000.00"), safi1.getSalaireBase());
        assertEquals(new BigDecimal("400000.00"), safi1.getSalaireBrut());
        assertEquals(new BigDecimal("4000.00"), safi1.getDeductionsCnaps()); // 1%
        assertEquals(new BigDecimal("4000.00"), safi1.getDeductionsSante()); // 1% OSTIE
        assertEquals(new BigDecimal("392000.00"), safi1.getSalaireNet());
        assertEquals(new BigDecimal("392000.00"), safi1.getMontantImposable());
        assertEquals(new BigDecimal("3000.00"), safi1.getImpotDu(), "Minimum de perception LF 2026 (3 000 Ar)");

        SafiRowDto safi6 = safiRows.stream().filter(r -> p6.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("527306.29"), safi6.getSalaireBase(), "Base ventilée SAFI après déductions");
        assertEquals(new BigDecimal("706462.82"), safi6.getSalaireBrut());
        assertEquals(new BigDecimal("7064.63"), safi6.getDeductionsCnaps()); // 1%
        assertEquals(new BigDecimal("10596.94"), safi6.getDeductionsSante()); // 1.5% OMSI
        assertEquals(new BigDecimal("688801.25"), safi6.getSalaireNet());
        assertEquals(new BigDecimal("28100.00"), safi6.getImpotDu());

        // Règle arithmétique stricte DGI e-Hetra : Q (Brut) = I (Base) + J (Indem Impos) + N (HS) + O (Primes) + P (Autres)
        for (SafiRowDto r : safiRows) {
            BigDecimal brutReconstitue = r.getSalaireBase()
                    .add(r.getIndemnitesImposables())
                    .add(r.getHeuresSupplementaires())
                    .add(r.getPrimesEtGratifications())
                    .add(r.getAutres());
            assertEquals(r.getSalaireBrut(), brutReconstitue, "Équation stricte DGI e-Hetra SAFI : Q = I + J + N + O + P");
        }

        // Test CSV SAFI (Format conforme 25 colonnes délimitées par point-virgule)
        String safiCsv = paieExportService.exporterSafiCsv(idPeriode);
        assertNotNull(safiCsv);
        assertTrue(safiCsv.contains("ETAT 301 - ANNEXE DE DECLARATION DE L'IRSA (SAFI)"), "Titre officiel SAFI");
        assertTrue(safiCsv.contains("N° Ordre;N° Mle;N° d'affiliation (CNaPS);Nom et prenoms"), "En-tête officiel colonnes SAFI");
        assertTrue(safiCsv.contains(p1.matricule()));
        assertTrue(safiCsv.contains(p6.matricule()));

        // Test ODS SAFI (Génération OpenDocument Spreadsheet binaire conforme e-Hetra)
        byte[] safiOds = paieExportService.exporterSafiOds(idPeriode);
        assertNotNull(safiOds);
        assertTrue(safiOds.length > 500, "Le fichier SAFI ODS doit avoir une taille significative");
        // Signature ZIP magic bytes PK (0x50, 0x4B, 0x03, 0x04)
        assertEquals((byte) 0x50, safiOds[0]);
        assertEquals((byte) 0x4B, safiOds[1]);
        assertEquals((byte) 0x03, safiOds[2]);
        assertEquals((byte) 0x04, safiOds[3]);

        // =========================================================================
        // 4. CERTIFICATION DNTS (CNaPS & FMFP)
        // =========================================================================
        List<DntsRowDto> dntsRows = paieExportService.obtenirDonneesDnts(idPeriode);
        assertEquals(2, dntsRows.size(), "DNTS doit contenir 2 salariés");

        DntsRowDto dnts1 = dntsRows.stream().filter(r -> p1.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("400000.00"), dnts1.getSalaireBrutReel());
        assertEquals(new BigDecimal("4000.00"), dnts1.getCnapsSalarial());
        assertEquals(new BigDecimal("52000.00"), dnts1.getCnapsPatronal()); // 13%
        assertEquals(new BigDecimal("4000.00"), dnts1.getFmfpPatronal());  // 1%

        DntsRowDto dnts6 = dntsRows.stream().filter(r -> p6.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("706462.82"), dnts6.getSalaireBrutReel());
        assertEquals(new BigDecimal("7064.63"), dnts6.getCnapsSalarial());
        assertEquals(new BigDecimal("91840.17"), dnts6.getCnapsPatronal()); // 13%
        assertEquals(new BigDecimal("7064.63"), dnts6.getFmfpPatronal());  // 1%

        String dntsCsv = paieExportService.exporterDntsCsv(idPeriode);
        assertNotNull(dntsCsv);
        assertTrue(dntsCsv.contains("CNAPS - CAISSE NATIONALE DE PREVOYANCE SOCIALE & FMFP"), "Titre officiel DNTS");
        assertTrue(dntsCsv.contains("N° Ordre;Matricule;N° CNaPS;Nom et Prenom"), "En-tête colonnes DNTS officiel");

        // =========================================================================
        // 5. CERTIFICATION VIREMENT BANCAIRE
        // =========================================================================
        List<VirementRowDto> virementRows = paieExportService.obtenirDonneesVirement(idPeriode);
        assertEquals(2, virementRows.size(), "Virement bancaire doit contenir 2 lignes");

        VirementRowDto vir1 = virementRows.stream().filter(r -> p1.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(bpc1.getBulletin().getNetAPayer(), vir1.getNetAPayer());
        assertEquals("RIB-" + p1.matricule(), vir1.getRibComplet());

        VirementRowDto vir6 = virementRows.stream().filter(r -> p6.matricule().equals(r.getMatricule())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("640700.00"), vir6.getNetAPayer());
        assertEquals("RIB-" + p6.matricule(), vir6.getRibComplet());

        String virementCsv = paieExportService.exporterVirementCsv(idPeriode);
        assertNotNull(virementCsv);
        assertTrue(virementCsv.contains("N° Ordre;Matricule;Beneficiaire;Banque"), "En-tête virement");

        // =========================================================================
        // 6. BALANCE COMPTABLE DU JOURNAL DE PAIE (ÉGALITÉ PARFAITE DES MASSES)
        // =========================================================================
        List<BulletinPaie> bulletins;
        try (Connection c = dataSource.getConnection()) {
            bulletins = BulletinPaie.listerParPeriode(idPeriode, c);
        }

        BigDecimal sumBase = bulletins.stream().map(BulletinPaie::getSalaireBase).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumBrut = bulletins.stream().map(BulletinPaie::getTotalGainsBruts).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumCotis = bulletins.stream().map(BulletinPaie::getTotalCotisationsSalariales).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumIrsa = bulletins.stream().map(BulletinPaie::getIrsaNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumNet = bulletins.stream().map(BulletinPaie::getNetAPayer).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumSafiBrut = safiRows.stream().map(SafiRowDto::getSalaireBrut).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumSafiCotis = safiRows.stream().map(r -> r.getDeductionsCnaps().add(r.getDeductionsSante())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumSafiIrsa = safiRows.stream().map(SafiRowDto::getImpotDu).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumDntsBrut = dntsRows.stream().map(DntsRowDto::getSalaireBrutReel).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDntsCnaps = dntsRows.stream().map(DntsRowDto::getCnapsSalarial).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal sumVirNet = virementRows.stream().map(VirementRowDto::getNetAPayer).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Preuve 1 : Conservation du salaire de base contractuel
        assertEquals(new BigDecimal("1000000.00"), sumBase);

        // Preuve 2 : Conservation du brut social
        assertEquals(sumBrut, sumSafiBrut, "Conservation Brut : Bulletins == SAFI");
        assertEquals(sumBrut, sumDntsBrut, "Conservation Brut : Bulletins == DNTS");
        assertEquals(new BigDecimal("1106462.82"), sumBrut);

        // Preuve 3 : Conservation des cotisations salariales
        assertEquals(sumCotis, sumSafiCotis, "Conservation Cotisations : Bulletins == SAFI");
        assertEquals(new BigDecimal("25661.57"), sumCotis);

        // Preuve 4 : Conservation de l'IRSA
        assertEquals(sumIrsa, sumSafiIrsa, "Conservation IRSA : Bulletins == SAFI");
        assertEquals(new BigDecimal("31100.00"), sumIrsa);

        // Preuve 5 : Conservation du Net à Payer
        assertEquals(sumNet, sumVirNet, "Conservation Net à Payer : Bulletins == Virement Bancaire");

        // =========================================================================
        // 7. ANTI-RECALCULATION & SCELLAGE IMMUABLE
        // =========================================================================
        try (Connection c = dataSource.getConnection()) {
            payrollService.cloturerDonneesPaie(idPeriode, 1, c);
        }

        // Vérification que les bulletins sont scellés en statut CLOTURE
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut FROM bulletin_paie WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    assertEquals("CLOTURE", rs.getString("statut"), "Tous les bulletins doivent être CLOTURE");
                }
            }
        }

        // Mutation contrôlée de paramètres légaux futurs (ex: insertion d'une fausse loi future 2027)
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement psDel = c.prepareStatement("DELETE FROM paie_config_legale WHERE code_version = 'LF_2027_TEST'")) {
                psDel.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO paie_config_legale (code_version, libelle, date_debut, sme_national, sme_agricole, seuil_exoneration_irsa, minimum_perception_irsa, deduction_charge_famille, diviseur_mensuel_heures) " +
                    "VALUES ('LF_2027_TEST', 'Loi Future 2027', '2027-01-01', 500000.00, 400000.00, 500000.00, 5000.00, 3000.00, 160.00)")) {
                ps.executeUpdate();
            }

            // Vérification de non-régression : ré-interrogation des exports scellés
            List<SafiRowDto> safiAfter = paieExportService.obtenirDonneesSafi(idPeriode);
            assertEquals(sumSafiBrut, safiAfter.stream().map(SafiRowDto::getSalaireBrut).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "Immutabilité : Le brut SAFI scellé est invariant après mutation de configurations futures");

            List<VirementRowDto> virAfter = paieExportService.obtenirDonneesVirement(idPeriode);
            assertEquals(sumVirNet, virAfter.stream().map(VirementRowDto::getNetAPayer).reduce(BigDecimal.ZERO, BigDecimal::add),
                    "Immutabilité : Le net bancaire scellé est invariant après mutation de configurations futures");

            // Nettoyage de la configuration de test
            try (PreparedStatement psDel = c.prepareStatement("DELETE FROM paie_config_legale WHERE code_version = 'LF_2027_TEST'")) {
                psDel.executeUpdate();
            }
        }
    }
}
