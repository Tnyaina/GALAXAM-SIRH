package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import org.junit.jupiter.api.Test;

/** Integration characterization of payroll close → monthly leave acquisition on isolated PostgreSQL. */
class PayrollLeaveAcquisitionPostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");
    private static BigDecimal d(String value) { return new BigDecimal(value); }

    @Test
    void clotureCreeJournalMouvementEtLotPuisResteIdempotente() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c);
                contract(c, employee, "2026-03-01");
                int period = payrollPeriod(c, "2026-03-01", "2026-03-31");
                bulletin(c, employee, period, "300000", "30", "0");

                PayrollService payroll = payrollService();
                payroll.cloturerDonneesPaie(period, null, c);

                int journal = scalarInt(c, "SELECT id_journal FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee);
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION' AND date_mouvement BETWEEN DATE '2026-03-01' AND DATE '2026-03-31'", employee));
                decimal("2.5000", scalarDecimal(c, "SELECT jours_droit_acquis FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("25000.00", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("25000.00", scalarDecimal(c, "SELECT valeur_source FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION'", employee));
                assertEquals("NEW_HIRE", scalarString(c, "SELECT mode_reprise FROM conge_periode_reference WHERE id_employe=?", employee));
                var situation = new mg.galaxam.sirh.conge.application.CongeLotValorisationService()
                        .situation(employee, java.time.LocalDate.of(2026, 3, 31), c);
                decimal("2.5", situation.soldeJours());
                decimal("25000", situation.soldeValorisation());

                payroll.cloturerDonneesPaie(period, null, c);
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION' AND date_mouvement BETWEEN DATE '2026-03-01' AND DATE '2026-03-31'", employee));

                payroll.rouvrirDonneesPaie(period, "Correction test acquisition", null, c);
                try (PreparedStatement ps = c.prepareStatement("UPDATE bulletin_paie SET salaire_base=320000 WHERE id_periode_paie=? AND id_employe=?")) {
                    ps.setInt(1, period); ps.setInt(2, employee); ps.executeUpdate();
                }
                IllegalStateException conflict = assertThrows(IllegalStateException.class, () -> payroll.cloturerDonneesPaie(period, null, c));
                assertTrue(conflict.getMessage().contains("CONGE_ACQUISITION_RECALCULATION_CONFLICT"));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee));
            } finally { c.rollback(); }
        }
    }

    @Test
    void clotureProratiseUnMoisAvecAbsenceEtConstruitLeCumulAnnuel() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c);
                contract(c, employee, "2026-03-01");
                int period = payrollPeriod(c, "2026-03-01", "2026-03-31");
                bulletin(c, employee, period, "300000", "20", "3");
                payrollService().cloturerDonneesPaie(period, null, c);

                int journal = scalarInt(c, "SELECT id_journal FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee);
                // 30 jours de service - 3 jours d'absence = 27/30 × 2,5 = 2,25 jours.
                decimal("27.000000", scalarDecimal(c, "SELECT jours_travailles_formule FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("2.2500", scalarDecimal(c, "SELECT jours_droit_acquis FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("22500.00", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("2.2500", scalarDecimal(c, "SELECT cumul_jours_droit FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("22500.00", scalarDecimal(c, "SELECT cumul_valorisation FROM conge_journal_droit WHERE id_journal=?", journal));
            } finally { c.rollback(); }
        }
    }

    @Test
    void clotureApresCutoverAjouteUneSeuleAcquisitionAuxCumulsEtAuLotRepris() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c);
                contract(c, employee, "2026-01-01");
                CongeTauxApplicableService rates = new CongeTauxApplicableService();
                var opening = new CongeTauxApplicableService.CutoverOpeningRequest();
                opening.setIdEmploye(employee);
                opening.setDateCutover(java.time.LocalDate.of(2026, 3, 15));
                opening.setModeReprise(CongeTauxApplicableService.CongeModeReprise.AGGREGATED_OPENING);
                opening.setSoldeJours(d("4"));
                opening.setValeurSoldeOuverture(d("50000"));
                opening.setTauxJournalierApplicable(d("12000"));
                opening.setDateDebutReference(java.time.LocalDate.of(2026, 1, 1));
                opening.setCumulDroitsOuverture(d("7.5"));
                opening.setCumulValorisationOuverture(d("75000"));
                opening.setReferenceArrete("IT-DAF-CUTOVER");
                rates.enregistrerCutoverSnapshot(opening, null, c);

                int period = payrollPeriod(c, "2026-03-16", "2026-03-31");
                bulletin(c, employee, period, "300000", "20", "0");
                payrollService().cloturerDonneesPaie(period, null, c);

                int journal = scalarInt(c, "SELECT id_journal FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee);
                decimal("2.5000", scalarDecimal(c, "SELECT jours_droit_acquis FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("25000.00", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("10.0000", scalarDecimal(c, "SELECT cumul_jours_droit FROM conge_journal_droit WHERE id_journal=?", journal));
                decimal("100000.00", scalarDecimal(c, "SELECT cumul_valorisation FROM conge_journal_droit WHERE id_journal=?", journal));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION'", employee));
                var situation = new mg.galaxam.sirh.conge.application.CongeLotValorisationService()
                        .situation(employee, java.time.LocalDate.of(2026, 3, 31), c);
                decimal("6.5", situation.soldeJours());
                decimal("75000", situation.soldeValorisation());
                assertEquals("REPRISE_CUTOVER", situation.lots().get(0).origine());
                assertEquals("ACQUISITION", situation.lots().get(1).origine());
            } finally { c.rollback(); }
        }
    }

    @Test
    void correctionDafDuMoisEstTraceeEtUneReclotureNeLecrasePas() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c);
                contract(c, employee, "2026-01-01");
                int period = payrollPeriod(c, "2026-03-01", "2026-03-31");
                bulletin(c, employee, period, "300000", "30", "0");
                PayrollService payroll = payrollService();
                payroll.cloturerDonneesPaie(period, null, c);
                int journal = scalarInt(c, "SELECT id_journal FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=3", employee);
                int reference = scalarInt(c, "SELECT id_periode_reference FROM conge_journal_droit WHERE id_journal=?", journal);
                int movement = scalarInt(c, "SELECT id_mouvement FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION'", employee);
                int user = insertTestUser(c);

                var correction = new mg.galaxam.sirh.conge.application.CongeValorisationMensuelleService();
                Field lots = mg.galaxam.sirh.conge.application.CongeValorisationMensuelleService.class.getDeclaredField("lots");
                lots.setAccessible(true);
                lots.set(correction, new mg.galaxam.sirh.conge.application.CongeLotValorisationService());
                correction.corriger(reference, journal, d("26000"), "Rectification DAF de recette", user, c);
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_valorisation_mensuelle_correction WHERE id_journal=?", journal));
                decimal("26000.00", scalarDecimal(c, "SELECT valeur_source FROM mouvement_solde_conge WHERE id_mouvement=?", movement));

                payroll.rouvrirDonneesPaie(period, "Test recloture après correction DAF", null, c);
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        () -> payroll.cloturerDonneesPaie(period, null, c));
                assertTrue(refusal.getMessage().contains("CONGE_ACQUISITION_RECALCULATION_CONFLICT"));
                decimal("26000.00", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_valorisation_mensuelle_correction WHERE id_journal=?", journal));
            } finally { c.rollback(); }
        }
    }

    private static PayrollService payrollService() throws Exception {
        PayrollService payroll = new PayrollService();
        Field service = PayrollService.class.getDeclaredField("payrollLeaveJournalService");
        service.setAccessible(true);
        service.set(payroll, new PayrollLeaveJournalService(new CongeService(new CongeCalculateurEngine()), new CongeTauxApplicableService()));
        return payroll;
    }

    private static int employee(Connection c) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) VALUES (?, 'TEST', 'Acquisition', 'M', '1990-01-01', 'Test', ?, '2010-01-01', 'Test', 'Adresse test', '000000') RETURNING id_employe")) {
            ps.setString(1, "PA-" + suffix); ps.setString(2, "PA" + suffix);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static int insertTestUser(Connection c) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO utilisateur (identifiant,email,mot_de_passe_hache,id_role) VALUES (?,?, 'test-hash',1) RETURNING id_utilisateur")) {
            ps.setString(1, "it-" + suffix); ps.setString(2, "it-" + suffix + "@example.invalid");
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static void contract(Connection c, int employee, String start) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrat_travail (id_employe,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,numero_contrat,date_debut) VALUES (?,1,1,1,4,?,?)")) {
            ps.setInt(1, employee); ps.setString(2, "IT-" + UUID.randomUUID()); ps.setObject(3, java.time.LocalDate.parse(start)); ps.executeUpdate();
        }
    }

    private static int payrollPeriod(Connection c, String start, String end) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO periode_paie (code,libelle,date_debut,date_fin) VALUES (?, 'Test acquisition', ?, ?) RETURNING id_periode_paie")) {
            ps.setString(1, "P" + UUID.randomUUID().toString().substring(0, 12)); ps.setObject(2, java.time.LocalDate.parse(start)); ps.setObject(3, java.time.LocalDate.parse(end));
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private static void bulletin(Connection c, int employee, int period, String salary, String worked, String absence) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,jours_travailles,jours_absence,salaire_base,taux_horaire) SELECT ?,e.id_employe,'MENSUEL',?,e.matricule,e.nom||' '||e.prenom,'Test','Test','PERMANENT',?,?,?,1000 FROM employe e WHERE e.id_employe=?")) {
            ps.setInt(1, period); ps.setString(2, "IT-" + UUID.randomUUID()); ps.setBigDecimal(3, d(worked)); ps.setBigDecimal(4, d(absence)); ps.setBigDecimal(5, d(salary)); ps.setInt(6, employee); ps.executeUpdate();
        }
    }

    private static int scalarInt(Connection c, String sql, int value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setInt(1, value); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); } }
    }
    private static BigDecimal scalarDecimal(Connection c, String sql, int value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setInt(1, value); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getBigDecimal(1); } }
    }
    private static String scalarString(Connection c, String sql, int value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql)) { ps.setInt(1, value); try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); } }
    }
    private static void decimal(String expected, BigDecimal actual) {
        assertNotNull(actual); assertEquals(0, d(expected).compareTo(actual));
    }
    private static void assumeDatabaseConfigured() {
        assumeTrue(URL != null && !URL.isBlank(), "Requires an isolated disposable PostgreSQL database");
    }
    private static Connection connection() throws Exception { return DriverManager.getConnection(URL, USER, PASSWORD); }
}
