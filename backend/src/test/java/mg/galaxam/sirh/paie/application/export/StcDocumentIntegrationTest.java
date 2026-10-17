package mg.galaxam.sirh.paie.application.export;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.time.LocalDate;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.contrat.application.DocumentRhService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class StcDocumentIntegrationTest {
    @Autowired DataSource source;
    @Autowired StcDocumentService documents;
    @Autowired DocumentRhService documentsRh;

    @Test
    void documentsFinanciersUtilisentLeScelleEtLesEcrituresDuJournal() throws Exception {
        try (Connection c = source.getConnection()) {
            c.setAutoCommit(false);
            try {
                String unique = UUID.randomUUID().toString().substring(0, 8);
                int employee = CongeTestFixtureHelper.createEmploye(c, "DOC-" + unique, "HISTORIQUE", "Agent");
                int contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2026, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, employee, contract, LocalDate.of(2026, 1, 1), new BigDecimal("600"));
                int otherEmployee = CongeTestFixtureHelper.createEmploye(c, "DOC-O-" + unique, "AUTRE", "Agent");
                int otherContract = CongeTestFixtureHelper.createContratActif(c, otherEmployee, LocalDate.of(2026, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, otherEmployee, otherContract, LocalDate.of(2026, 1, 1), new BigDecimal("600"));
                int period = CongeTestFixtureHelper.createPeriodePaie(c, "DOC-" + unique,
                        LocalDate.of(2026, 7, 26), LocalDate.of(2026, 8, 25), "EN_COURS");
                int bulletin = insert(c, "INSERT INTO bulletin_paie "
                        + "(id_periode_paie,id_employe,type_bulletin,reference_bulletin,matricule,nom_prenom,"
                        + "intitule_poste,nom_site,regime_intervenant,salaire_base,taux_horaire) "
                        + "VALUES (?,?, 'STC',?,'TEST','NOM HISTORIQUE','Agent','Tana','SALARIE',600,10) RETURNING id_bulletin",
                        period, employee, "DOC-B-" + unique);
                execute(c, "INSERT INTO bulletin_paie_employeur_snapshot "
                        + "(id_bulletin,id_filiale,raison_sociale) VALUES (?,1,'EMPLOYEUR HISTORIQUE')", bulletin);
                int stc = insert(c, "INSERT INTO paie_stc (id_employe,id_contrat,id_periode_paie,id_bulletin,"
                        + "numero_recu,date_depart,motif_rupture,salaire_base,taux_horaire,salaire_prorata,"
                        + "indemnite_conges_payes,total_brut_stc,net_a_payer_stc,statut,date_validation) "
                        + "VALUES (?,?,?,? ,?,'2026-08-15','DEMISSION',600,10,600,400,1000,1000,'VALIDE',now()) "
                        + "RETURNING id_stc", employee, contract, period, bulletin, "DOC-STC-" + unique);
                int dossier = insert(c, "INSERT INTO paie_dossier_sortie "
                        + "(id_employe,id_contrat,date_depart,motif_rupture,id_stc) "
                        + "VALUES (?,?,'2026-08-15','DEMISSION',?) RETURNING id_dossier_sortie", employee, contract, stc);
                String decompte = pdfText(documents.pdf(dossier, StcDocumentService.Type.DECOMPTE, c));
                assertTrue(decompte.contains("1000.00 Ar"));
                assertTrue(decompte.contains("EMPLOYEUR HISTORIQUE"));
                assertTrue(decompte.contains("NOM HISTORIQUE"));
                assertTrue(decompte.contains("ne constitue pas une preuve de paiement"));

                execute(c, "UPDATE employe SET nom='NOM ACTUEL' WHERE id_employe=?", employee);
                execute(c, "UPDATE contrat_travail SET description='Salaire actuel modifie' WHERE id_contrat=?", contract);
                execute(c, "UPDATE ref_filiale SET raison_sociale='EMPLOYEUR ACTUEL' WHERE id_filiale=1");
                assertEquals(decompte, pdfText(documents.pdf(dossier, StcDocumentService.Type.DECOMPTE, c)));

                int first = insert(c, "INSERT INTO paie_stc_reglement "
                        + "(id_stc,date_reglement,montant_regle,mode_reglement,reference_reglement,created_at) "
                        + "VALUES (?,'2026-08-16',300,'VIREMENT',?, '2026-08-16 10:00:00+03') RETURNING id_reglement",
                        stc, "PAY-1-" + unique);
                String one = pdfText(documents.justificatif(dossier, first, c));
                assertTrue(one.contains("300.00 Ar"));
                assertTrue(one.contains("700.00 Ar"));
                insert(c, "INSERT INTO paie_stc_reglement "
                        + "(id_stc,date_reglement,montant_regle,mode_reglement,reference_reglement,created_at) "
                        + "VALUES (?,'2026-08-17',200,'VIREMENT',?, '2026-08-17 10:00:00+03') RETURNING id_reglement",
                        stc, "PAY-2-" + unique);
                assertTrue(pdfText(documents.justificatif(dossier, first, c)).contains("700.00 Ar"));
                String recu = pdfText(documents.pdf(dossier, StcDocumentService.Type.RECU, c));
                assertTrue(recu.contains("500.00 Ar"));
                assertTrue(recu.contains("PROJET"));
                assertFalse(recu.contains("integralement acquitte"));

                execute(c, "UPDATE paie_stc_reglement SET est_annule=TRUE, "
                        + "date_annulation='2026-08-18 10:00:00+03',motif_annulation='Erreur bancaire' "
                        + "WHERE id_reglement=?", first);
                String cancelled = pdfText(documents.justificatif(dossier, first, c));
                assertTrue(cancelled.contains("ECRITURE ANNULEE"));
                assertTrue(cancelled.contains("Erreur bancaire"));

                int autreStc = insert(c, "INSERT INTO paie_stc "
                        + "(id_employe,id_contrat,id_periode_paie,numero_recu,date_depart,motif_rupture,"
                        + "salaire_base,taux_horaire,net_a_payer_stc,statut,date_validation) "
                        + "VALUES (?,?,?,?,'2026-08-15','DEMISSION',600,10,100,'VALIDE',now()) RETURNING id_stc",
                        otherEmployee, otherContract, period, "DOC-STC-OTHER-" + unique);
                int autrePaiement = insert(c, "INSERT INTO paie_stc_reglement "
                        + "(id_stc,date_reglement,montant_regle,mode_reglement,reference_reglement) "
                        + "VALUES (?,'2026-08-16',50,'VIREMENT',?) RETURNING id_reglement",
                        autreStc, "PAY-OTHER-" + unique);
                assertThrows(SirhBusinessException.class,
                        () -> documents.justificatif(dossier, autrePaiement, c));

                int simulation = insert(c, "INSERT INTO paie_stc "
                        + "(id_employe,id_contrat,id_periode_paie,numero_recu,date_depart,motif_rupture,"
                        + "salaire_base,taux_horaire,statut) "
                        + "VALUES (?,?,?,?,'2026-08-16','DEMISSION',600,10,'SIMULATION') RETURNING id_stc",
                        otherEmployee, otherContract, period, "DOC-STC-SIM-" + unique);
                int dossierSimulation = insert(c, "INSERT INTO paie_dossier_sortie "
                        + "(id_employe,id_contrat,date_depart,motif_rupture,id_stc) "
                        + "VALUES (?,?,'2026-08-16','DEMISSION',?) RETURNING id_dossier_sortie", otherEmployee, otherContract, simulation);
                assertThrows(SirhBusinessException.class,
                        () -> documents.pdf(dossierSimulation, StcDocumentService.Type.DECOMPTE, c));

                execute(c, "UPDATE contrat_travail SET date_fin='2026-08-15',id_statut_rh=8 WHERE id_contrat=?", contract);
                String certificat = pdfText(documents.pdf(dossier, StcDocumentService.Type.CERTIFICAT, c));
                assertTrue(certificat.contains("CERTIFICAT DE TRAVAIL"));
                assertFalse(certificat.contains("1000.00"));
                assertFalse(certificat.contains("DEMISSION"));
                assertTrue(documents.certificatDocx(dossier, c).length > 1000);
                var ancienCertificat = documentsRh.preparerCertificat(contract, "contrat", c);
                ByteArrayOutputStream ancienDocx = new ByteArrayOutputStream();
                documentsRh.genererCertificatDocx(ancienCertificat, ancienDocx);
                try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(ancienDocx.toByteArray()))) {
                    String contenu = word.getParagraphs().stream().map(p -> p.getText())
                            .reduce("", (a, b) -> a + " " + b);
                    assertFalse(contenu.contains("libre de toute dette"));
                    assertFalse(contenu.contains("sérieux et professionnalisme"));
                    assertFalse(contenu.contains("né(e) le"));
                }
                assertTrue(pdfText(documents.pdf(dossier, StcDocumentService.Type.BORDEREAU, c))
                        .contains("ne constate aucune remise ni signature"));
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT net_a_payer_stc FROM paie_stc WHERE id_stc=?")) {
                    ps.setInt(1, stc);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(0, new BigDecimal("1000.00").compareTo(rs.getBigDecimal(1)));
                    }
                }
                execute(c, "DELETE FROM bulletin_paie_employeur_snapshot WHERE id_bulletin=?", bulletin);
                assertThrows(SirhBusinessException.class,
                        () -> documents.pdf(dossier, StcDocumentService.Type.DECOMPTE, c),
                        "Un snapshot employeur manquant doit empêcher un document financier présenté comme historique.");
            } finally {
                c.rollback();
            }
        }
    }

    private static String pdfText(byte[] bytes) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(bytes)) { return new PDFTextStripper().getText(pdf); }
    }

    private static int insert(Connection c, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static void execute(Connection c, String sql, Object... args) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }
}
