package mg.galaxam.sirh.paie;

import mg.galaxam.sirh.model.paie.BulletinPaie;
import mg.galaxam.sirh.model.paie.BulletinPaieLigne;
import mg.galaxam.sirh.model.paie.dto.PayrollBatchResult;
import mg.galaxam.sirh.model.paie.dto.export.DntsRowDto;
import mg.galaxam.sirh.model.paie.dto.export.SafiRowDto;
import mg.galaxam.sirh.model.paie.dto.export.VirementRowDto;
import mg.galaxam.sirh.model.paie.service.PayrollService;
import mg.galaxam.sirh.model.paie.service.export.DntsExportService;
import mg.galaxam.sirh.model.paie.service.export.EdiIsiExportService;
import mg.galaxam.sirh.model.paie.service.export.SafiExportService;
import mg.galaxam.sirh.model.paie.service.export.VirementBancaireExportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@DisplayName("ConsultantPrestationAlignmentIntegrationTest — Validation de l'Alignement Consultant / Prestation sous Bulletin de Paie")
class ConsultantPrestationAlignmentIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PayrollService payrollService;

    @Autowired
    private VirementBancaireExportService virementBancaireExportService;

    @Autowired
    private SafiExportService safiExportService;

    @Autowired
    private EdiIsiExportService ediIsiExportService;

    @Autowired
    private DntsExportService dntsExportService;

    private void creerConsultantTest(Connection c) throws Exception {
        String sql = "INSERT INTO employe (id_employe, matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, telephone, email, est_actif) " +
                "VALUES (99, 'CONS-0099', 'RAZAFY', 'Hery Consultant', 'M', '1962-04-15', 'Antananarivo', '101999888777', '1980-05-20', 'Antananarivo', 'Lot Ankorondrano', 'Antananarivo', '+261340100099', 'hery.razafy@consultant.mg', true); " +
                "INSERT INTO contrat_travail (id_contrat, numero_contrat, id_employe, id_filiale, id_type_contrat, id_type_intervenant, id_statut_rh, date_debut, date_fin) " +
                "VALUES (99, 'CT-PREST-2026-99', 99, 1, " +
                "    (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'PRESTATION'), " +
                "    (SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'CONSULTANT'), " +
                "    (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), " +
                "    '2026-08-01', '2026-12-31'); " +
                "INSERT INTO employe_affectation_carriere (id_affectation, id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie, date_debut, salaire_base) " +
                "VALUES (99, 99, 99, (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), 1, 1, 1, 1, 1, 1, 1, '2026-08-01', 2500000.00); " +
                "INSERT INTO employe_compte_banque (id_compte_banque, id_employe, id_banque, num_compte_rib, est_principal) " +
                "VALUES (99, 99, 1, '00004 00123 00000000099 45', true); " +
                "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables, nb_jours_presents, nb_jours_absence, total_heures_travaillees, gele_at) " +
                "VALUES (99, 1, 22, 22, 0, 173.33, CURRENT_TIMESTAMP);";

        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    @Test
    @DisplayName("1. Le consultant en prestation reçoit un bulletin complet dans le calcul collectif de paie")
    void testConsultantPrestationRecoitBulletinCompletDansBatch() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                creerConsultantTest(c);

                // Exécution du calcul batch sur la période 1 (Août 2026)
                PayrollBatchResult batch = payrollService.calculerPaiePeriode(1, true, 1, c);
                assertNotNull(batch);

                // 6 personnes calculées (les 5 permanents + le consultant ID 99)
                assertTrue(batch.getNbBulletinsCalcules() >= 6, 
                        "Le batch doit inclure les consultants en contrat de prestation : " + batch.getNbBulletinsCalcules());

                // Récupération du bulletin du consultant
                List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(1, c);
                BulletinPaie bpConsultant = bulletins.stream()
                        .filter(b -> b.getIdEmploye().equals(99))
                        .findFirst()
                        .orElse(null);

                assertNotNull(bpConsultant, "Un BulletinPaie officiel doit être généré pour le consultant ID 99");
                assertEquals("CONSULTANT", bpConsultant.getRegimeIntervenant());
                assertEquals("CALCULE", bpConsultant.getStatut());
                assertEquals("CONS-0099", bpConsultant.getMatricule());
                assertEquals(new BigDecimal("2500000.00"), bpConsultant.getSalaireBase());
                assertEquals(new BigDecimal("2500000.00"), bpConsultant.getTotalGainsBruts());

                // Récupération des lignes de paie
                List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bpConsultant.getIdBulletin(), c);
                assertFalse(lignes.isEmpty());

                // Vérification CNaPS & FMFP : Exonération totale (piloté par soumis_cnaps = false)
                boolean hasCnaps = lignes.stream().anyMatch(l -> "COTISATION_CNAPS_SAL".equals(l.getCodeRubrique()));
                boolean hasFmfp = lignes.stream().anyMatch(l -> "COTISATION_FMFP_PAT".equals(l.getCodeRubrique()));
                assertFalse(hasCnaps, "Le consultant ne doit pas cotiser à la CNaPS");
                assertFalse(hasFmfp, "Le consultant ne doit pas cotiser au FMFP");

                // Vérification Santé : Soumis OSTIE selon ref_type_intervenant (soumis_sante = true)
                BulletinPaieLigne ligneOstieSal = lignes.stream()
                        .filter(l -> "COTISATION_OSTIE_SAL".equals(l.getCodeRubrique()))
                        .findFirst().orElse(null);
                assertNotNull(ligneOstieSal, "Le consultant doit avoir une cotisation santé OSTIE");
                assertTrue(ligneOstieSal.getMontantSalarial().compareTo(BigDecimal.ZERO) > 0);

                // Vérification Fiscalité : Retenue Libératoire ISI 5% calculée sur 2 500 000 = 125 000 Ar
                BigDecimal isiAttendu = new BigDecimal("125000.00");

                BulletinPaieLigne ligneIsi = lignes.stream()
                        .filter(l -> "RETENUE_ISI_5".equals(l.getCodeRubrique()))
                        .findFirst().orElse(null);
                assertNotNull(ligneIsi, "La retenue libératoire ISI 5% doit être présente");
                assertEquals(isiAttendu, ligneIsi.getMontantSalarial());
                assertEquals(isiAttendu, bpConsultant.getRetenueIsi5());
                assertEquals(BigDecimal.ZERO.setScale(2), bpConsultant.getIrsaNet());

                // Vérification de l'équilibre comptable universel : Total Gains - Total Retenues == Net à Payer
                BigDecimal totalGains = bpConsultant.getTotalGainsBruts().add(bpConsultant.getTotalIndemnitesNonImposables());
                BigDecimal totalRetenues = bpConsultant.getTotalCotisationsSalariales()
                        .add(bpConsultant.getRetenueIsi5())
                        .add(bpConsultant.getIrsaNet())
                        .add(bpConsultant.getTotalRetenuesAcomptePret());
                BigDecimal netCalcule = totalGains.subtract(totalRetenues);
                assertEquals(bpConsultant.getNetAvantArrondi(), netCalcule, "Équilibre comptable strict avant arrondi respecté pour le consultant");
                assertEquals(bpConsultant.getNetAPayer(), BulletinPaie.arrondirDizaineAriary(netCalcule), "Arrondi bancaire officiel à la dizaine d'Ariary respecté");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("2. Le virement bancaire intègre uniformément consultants et permanents")
    void testVirementBancaireInclutConsultantEtPermanent() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                creerConsultantTest(c);
                payrollService.calculerPaiePeriode(1, true, 1, c);

                List<VirementRowDto> virementRows = virementBancaireExportService.genererDonneesVirement(1, c);
                assertNotNull(virementRows);
                assertTrue(virementRows.size() >= 6, "Le virement bancaire doit contenir au moins 6 personnes");

                VirementRowDto rowConsultant = virementRows.stream()
                        .filter(r -> "CONS-0099".equals(r.getMatricule()))
                        .findFirst().orElse(null);

                assertNotNull(rowConsultant, "Le consultant doit figurer sur le fichier d'ordre de virement bancaire");
                assertTrue(rowConsultant.getNetAPayer().compareTo(new BigDecimal("2000000.00")) > 0);
                assertEquals("00004 00123 00000000099 45", rowConsultant.getRibComplet());

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("3. La déclaration SAFI IRSA exclut le consultant sous régime ISI 5%")
    void testSafiExclutConsultantSousIsi() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                creerConsultantTest(c);
                payrollService.calculerPaiePeriode(1, true, 1, c);

                List<SafiRowDto> safiRows = safiExportService.genererDonneesSafi(1, c);
                assertNotNull(safiRows);

                // Aucun consultant sous ISI ne doit être dans SAFI (IRSA)
                boolean consultantPresentDansSafi = safiRows.stream()
                        .anyMatch(r -> "CONS-0099".equals(r.getMatricule()));
                assertFalse(consultantPresentDansSafi, "Le consultant assujetti à l'ISI ne doit pas figurer dans l'état IRSA SAFI 301");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("4. La déclaration EDI ISI intègre le consultant avec sa retenue ISI 5%")
    void testEdiIsiContientConsultant() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                creerConsultantTest(c);
                payrollService.calculerPaiePeriode(1, true, 1, c);

                String xml = ediIsiExportService.exporterEdiXml(1, c);
                assertNotNull(xml);
                assertTrue(xml.contains("101999888777"), "Le XML EDI ISI doit contenir le CIN du consultant");
                assertTrue(xml.contains("RAZAFY"), "Le XML EDI ISI doit contenir le nom du consultant");

            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("5. La déclaration DNTS CNaPS exclut le consultant (exonéré de retraite CNaPS)")
    void testDntsExclutConsultant() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                creerConsultantTest(c);
                payrollService.calculerPaiePeriode(1, true, 1, c);

                List<DntsRowDto> dntsRows = dntsExportService.genererDonneesDnts(1, c);
                assertNotNull(dntsRows);

                boolean consultantPresentDansDnts = dntsRows.stream()
                        .anyMatch(r -> "CONS-0099".equals(r.getMatricule()));
                assertFalse(consultantPresentDansDnts, "Le consultant sans cotisation CNaPS ne doit pas figurer dans la DNTS CNaPS");

            } finally {
                c.rollback();
            }
        }
    }
}
