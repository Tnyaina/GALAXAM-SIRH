package mg.galaxam.sirh.conge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CongeLotValorisationIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired CongeTauxApplicableService tauxService;
    @Autowired CongeLotValorisationService lotService;

    @Test
    void reprisePuisAcquisitionsHistoriseesEtPriseFifo() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            if (!"sirh_galaxam_test".equals(c.getCatalog())) {
                throw new IllegalStateException("Test interdit hors de la base sirh_galaxam_test.");
            }
            CongeTestFixtureHelper.cleanDatabase(c);
            int utilisateur = CongeTestFixtureHelper.createAdminUser(c, "admin.lot@galaxam.mg");
            int employe = CongeTestFixtureHelper.createEmploye(c, "LOT-VAL-001", "LOT", "HISTORIQUE");
            var reprise = new CongeTauxApplicableService.CutoverOpeningRequest();
            reprise.setIdEmploye(employe);
            reprise.setDateCutover(LocalDate.of(2026, 1, 1));
            reprise.setDateDebutReference(LocalDate.of(2026, 1, 1));
            reprise.setSoldeJours(new BigDecimal("1.00"));
            reprise.setValeurSoldeOuverture(new BigDecimal("9000.00"));
            reprise.setTauxJournalierApplicable(new BigDecimal("9000.00"));
            reprise.setCumulDroitsOuverture(BigDecimal.ZERO);
            reprise.setCumulValorisationOuverture(BigDecimal.ZERO);
            int reference = tauxService.enregistrerCutoverSnapshot(reprise, utilisateur, c);

            ajouterAcquisition(c, employe, reference, 2026, 1, "300000.00", "25000.00");
            ajouterAcquisition(c, employe, reference, 2026, 2, "360000.00", "30000.00");
            int demande;
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                    + "(id_employe, id_type_conge, id_statut_demande_conge, date_debut, date_fin, "
                    + "nb_jours_decomptes, saisi_par, valide_par) VALUES (?, ?, ?, '2026-03-01', "
                    + "'2026-03-04', 4.00, ?, ?) RETURNING id_demande_conge")) {
                ps.setInt(1, employe);
                ps.setInt(2, CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL"));
                ps.setInt(3, CongeTestFixtureHelper.getStatutDemandeCongeId(c, "VALIDE"));
                ps.setInt(4, utilisateur); ps.setInt(5, utilisateur);
                try (var rs = ps.executeQuery()) { rs.next(); demande = rs.getInt(1); }
            }
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                    + "(id_employe, type_mouvement, nb_jours, date_mouvement, id_demande_conge, created_by) "
                    + "VALUES (?, 'PRISE', -4.00, '2026-03-01', ?, ?)")) {
                ps.setInt(1, employe);
                ps.setInt(2, demande); ps.setInt(3, utilisateur);
                ps.executeUpdate();
            }
            var situation = lotService.situation(employe, LocalDate.of(2026, 3, 2), c);
            assertEquals(0, situation.soldeJours().compareTo(new BigDecimal("2.00")));
            assertEquals(0, situation.soldeValorisation().compareTo(new BigDecimal("24000.00")));
            assertEquals(3, situation.imputations().size());
            assertEquals("REPRISE_CUTOVER", situation.imputations().get(0).origine());
            assertEquals(new BigDecimal("9000.00"), situation.imputations().get(0).valeur());
            assertEquals(new BigDecimal("25000.00"), situation.imputations().get(1).valeur());
            assertEquals(new BigDecimal("6000.00"), situation.imputations().get(2).valeur());
            assertEquals(0, lotService.valorisationPrisesSurPeriode(employe,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), c)
                    .compareTo(new BigDecimal("40000.00")));

            var correction = lotService.corrigerReliquatCutover(employe,
                    new BigDecimal("0.50"), new BigDecimal("5000.00"),
                    "Régularisation du reliquat repris", utilisateur, c);
            assertEquals(0, correction.nouveauReliquatJours().compareTo(new BigDecimal("0.50")));
            assertEquals(0, correction.nouvelleValeur().compareTo(new BigDecimal("5000.00")));
            assertEquals(0, correction.regularisationAExaminer().compareTo(new BigDecimal("1000.00")));
            var apres = lotService.situation(employe, LocalDate.now(), c);
            assertEquals(new BigDecimal("9000.00"), apres.imputations().get(0).valeur());
            assertEquals(0, apres.soldeValorisation().compareTo(new BigDecimal("29000.00")));
        }
    }

    @Test
    void testCutoverCycleDecale26Au25() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            CongeTestFixtureHelper.cleanDatabase(c);
            int utilisateur = CongeTestFixtureHelper.createAdminUser(c, "admin.cycle@galaxam.mg");
            int employe = CongeTestFixtureHelper.createEmploye(c, "CYCLE-26-001", "CYCLE", "DECALE");

            var reprise = new CongeTauxApplicableService.CutoverOpeningRequest();
            reprise.setIdEmploye(employe);
            // Cycle de paie démarrant le 26 septembre 2026
            LocalDate dateCutover = LocalDate.of(2026, 9, 26);
            reprise.setDateCutover(dateCutover);
            reprise.setDateDebutReference(LocalDate.of(2026, 1, 1));
            reprise.setSoldeJours(new BigDecimal("12.00"));
            reprise.setValeurSoldeOuverture(new BigDecimal("120000.00"));
            reprise.setTauxJournalierApplicable(new BigDecimal("10000.00"));
            reprise.setCumulDroitsOuverture(BigDecimal.ZERO);
            reprise.setCumulValorisationOuverture(BigDecimal.ZERO);

            // Doit réussir sans exiger le 1er du mois
            int reference = tauxService.enregistrerCutoverSnapshot(reprise, utilisateur, c);
            assertTrue(reference > 0);

            // Vérifier que le TCONGE est actif dès le 26/09/2026 (ex: demande posée le 28/09)
            var res = tauxService.resoudreTauxCongeApplicable(employe, LocalDate.of(2026, 9, 28), null, c);
            assertNotNull(res);
            assertEquals(0, res.getTauxJournalier().compareTo(new BigDecimal("10000.00")));
            assertEquals(CongeTauxApplicableService.TcongeSourceProvenance.REPRISE_CUTOVER, res.getSourceProvenance());
        }
    }

    private static void ajouterAcquisition(Connection c, int employe, int reference,
            int annee, int mois, String salaire, String valorisation) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_journal_droit "
                + "(id_employe, id_periode_reference, annee, mois, salaire_base, "
                + "jours_droit_acquis, base_valorisation, valorisation_mensuelle, "
                + "cumul_jours_droit, cumul_valorisation, taux_journalier_conge) "
                + "VALUES (?, ?, ?, ?, ?, 2.4992, ?, ?, 2.4992, ?, 10000.00)")) {
            ps.setInt(1, employe); ps.setInt(2, reference);
            ps.setInt(3, annee); ps.setInt(4, mois);
            ps.setBigDecimal(5, new BigDecimal(salaire));
            ps.setBigDecimal(6, new BigDecimal(salaire));
            ps.setBigDecimal(7, new BigDecimal(valorisation));
            ps.setBigDecimal(8, new BigDecimal(valorisation));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe, type_mouvement, nb_jours, date_mouvement) VALUES (?, 'ACQUISITION', 2.50, ?)")) {
            ps.setInt(1, employe);
            ps.setDate(2, Date.valueOf(LocalDate.of(annee, mois, 28)));
            ps.executeUpdate();
        }
    }
}
