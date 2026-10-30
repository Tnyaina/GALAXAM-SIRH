package mg.galaxam.sirh.paie.application.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.paie.application.PayrollPopulationResolver;
import mg.galaxam.sirh.paie.application.workflow.PayrollWorkflowCommands.PayrollAction;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Payroll validation freshness after a leave source changes, using disposable PostgreSQL. */
class PayrollLeaveFreshnessPostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");

    @Test
    void validationRefuseUnBulletinCalculeAvantModificationDuCongeJusquAuRecalcul() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Use GALAXAM_CONGE_TEST_DB_URL pointing to disposable PostgreSQL");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            int employee = -1;
            int period = -1;
            int bulletin = -1;
            int demand = -1;
            try {
                employee = insertEmployee(c);
                insertEmploymentFixture(c, employee);
                period = insertPeriod(c);
                final int testPeriodId = period;
                isolatePayrollPopulation(c, employee);
                insertOpeningAcquisition(c, employee);
                bulletin = insertCalculatedBulletin(c, employee, period);
                int annualLeave = scalarInt(c, "SELECT id_type_conge FROM ref_type_conge "
                        + "WHERE decompte_solde_acquis=TRUE ORDER BY id_type_conge LIMIT 1");
                demand = insertValidatedDemand(c, employee, annualLeave, "2028-09-10", "2028-09-12");
                insertLeaveMovement(c, employee, demand, "PRISE", "-3");
                ensurePopulationBulletins(c, period);

                new CongeService(new CongeCalculateurEngine(), new CalendrierOuvrableEngine())
                        .annulerDemande(demand, null, "Correction avant gel du pointage", c);
                try (PreparedStatement ps = c.prepareStatement("UPDATE periode_paie SET statut='VALIDEE' WHERE id_periode_paie=?")) {
                    ps.setInt(1, testPeriodId); ps.executeUpdate();
                }
                c.commit();
                DataSource dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
                var workflow = new PayrollPeriodWorkflowService(dataSource, null, null, new PayrollPopulationResolver());
                var daf = new PayrollPeriodUserContext(1, Set.of("ROLE_ADMIN"));
                var beforeRecalculation = workflow.describeActions(testPeriodId, "VALIDEE", daf, c);
                assertFalse(beforeRecalculation.get(PayrollAction.VALIDATE.name()).allowed());
                assertTrue(beforeRecalculation.get(PayrollAction.VALIDATE.name()).reasonCodes()
                        .contains("PAYROLL_CALCULATION_STALE"),
                        () -> "Unexpected validation reasons: " + beforeRecalculation.get(PayrollAction.VALIDATE.name()).reasonCodes());
                var staleFailure = assertThrows(mg.galaxam.sirh.common.exception.SirhBusinessException.class,
                        () -> workflow.requirePayrollAction(testPeriodId, PayrollAction.VALIDATE, daf));
                assertEquals("PAYROLL_PERIOD_PREREQUISITE_BLOCKED", staleFailure.getErrorCode().name());

                try (PreparedStatement ps = c.prepareStatement("UPDATE bulletin_paie SET date_calcul=CURRENT_TIMESTAMP + INTERVAL '3 hours', "
                        + "updated_at=CURRENT_TIMESTAMP + INTERVAL '3 hours' WHERE id_periode_paie=?")) {
                    ps.setInt(1, testPeriodId); ps.executeUpdate();
                }
                c.commit();
                var afterRecalculation = workflow.describeActions(testPeriodId, "VALIDEE", daf, c);
                assertTrue(afterRecalculation.get(PayrollAction.VALIDATE.name()).allowed());
                assertDoesNotThrow(() -> workflow.requirePayrollAction(testPeriodId, PayrollAction.VALIDATE, daf));
            } finally {
                c.rollback();
                cleanup(c, employee, period, bulletin);
            }
        }
    }

    private static int insertEmployee(Connection c) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,"
                + "cin_lieu_delivrance,adresse,telephone) VALUES (?, 'FRAICHEUR', 'Test', 'M', '1990-01-01', 'Test', ?, "
                + "'2010-01-01', 'Test', 'Adresse test', '000000') RETURNING id_employe")) {
            ps.setString(1, "IT-FR-" + suffix); ps.setString(2, "ITFR" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int insertPeriod(Connection c) throws Exception {
        String code = "IT-FRESH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO periode_paie(code,libelle,date_debut,date_fin,statut) "
                + "VALUES (?, 'Test fraîcheur congé', '2028-09-01', '2028-09-30', 'EN_COURS') RETURNING id_periode_paie")) {
            ps.setString(1, code); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int insertCalculatedBulletin(Connection c, int employee, int period) throws Exception {
        String reference = "IT-FRESH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bulletin_paie "
                + "(id_periode_paie,id_employe,reference_bulletin,matricule,nom_prenom,intitule_poste,nom_site,"
                + "regime_intervenant,salaire_base,taux_horaire,statut,date_calcul,updated_at) "
                + "VALUES (?,?,?,?,'Test Fraîcheur','Test','Test Site','SALARIE',300000,10000,'CALCULE',"
                + "TIMESTAMPTZ '2025-01-01 00:00:00+00',TIMESTAMPTZ '2025-01-01 00:00:00+00') RETURNING id_bulletin")) {
            ps.setInt(1, period); ps.setInt(2, employee); ps.setString(3, reference); ps.setString(4, "ITF" + reference.substring(8));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void cleanup(Connection c, int employee, int period, int bulletin) throws Exception {
        c.setAutoCommit(false);
        if (period > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM bulletin_paie WHERE id_periode_paie=?")) {
                ps.setInt(1, period); ps.executeUpdate();
            }
        }
        if (employee > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mouvement_solde_conge WHERE id_employe=?")) {
                ps.setInt(1, employee); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM demande_conge WHERE id_employe=?")) {
                ps.setInt(1, employee); ps.executeUpdate();
            }
        }
        if (period > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM periode_paie WHERE id_periode_paie=?")) {
                ps.setInt(1, period); ps.executeUpdate();
            }
        }
        if (employee > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM employe WHERE id_employe=?")) {
                ps.setInt(1, employee); ps.executeUpdate();
            }
        }
        c.commit();
    }

    private static void insertEmploymentFixture(Connection c, int employee) throws Exception {
        int contract;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrat_travail "
                + "(id_employe,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,id_type_evenement_initial,"
                + "numero_contrat,date_signature,date_debut,duree_essai_mois,comptabilise_effectif,updated_at) "
                + "SELECT ?,f.id_filiale,tc.id_type_contrat,ti.id_type_intervenant,s.id_statut_rh,e.id_type_evenement_rh,"
                + "?,DATE '2024-01-01',DATE '2024-01-01',0,TRUE,TIMESTAMP '2024-01-01 00:00:00' "
                + "FROM ref_filiale f CROSS JOIN ref_type_contrat tc CROSS JOIN ref_type_intervenant ti "
                + "CROSS JOIN ref_statut_rh s CROSS JOIN ref_type_evenement_rh e "
                + "WHERE f.code_filiale='GLX' AND tc.code='CDI' AND ti.code='PERMANENT' AND s.code='ACTIF' "
                + "AND e.code='EMBAUCHE_INITIALE' "
                + "RETURNING id_contrat")) {
            ps.setInt(1, employee); ps.setString(2, "IT-FR-CON-" + UUID.randomUUID().toString().substring(0, 8));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); contract = rs.getInt(1); }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe_affectation_carriere "
                + "(id_employe,id_contrat,id_statut_rh,id_motif_mouvement,id_site,id_departement,id_service,id_fonction,"
                + "id_categorie_professionnelle,id_mode_calcul_paie,salaire_base,date_debut,updated_at) "
                + "SELECT ?,?,s.id_statut_rh,m.id_motif_mouvement,site.id_site,d.id_departement,svc.id_service,"
                + "fn.id_fonction,cat.id_categorie_professionnelle,mode.id_mode_calcul_paie,300000,DATE '2024-01-01',TIMESTAMP '2024-01-01 00:00:00' "
                + "FROM ref_statut_rh s CROSS JOIN ref_motif_mouvement m CROSS JOIN ref_site site "
                + "CROSS JOIN ref_departement d CROSS JOIN ref_service svc CROSS JOIN ref_fonction fn "
                + "CROSS JOIN ref_categorie_professionnelle cat CROSS JOIN ref_mode_calcul_paie mode "
                + "WHERE s.code='ACTIF' AND m.code='EMBAUCHE' AND site.code_site='TNR' "
                + "AND d.code_departement='LOG' AND svc.code_service='ADM' AND fn.code_fonction='AIDE_COMPTABLE' "
                + "AND cat.code='A' AND mode.code='MENSUEL'")) {
            ps.setInt(1, employee); ps.setInt(2, contract); assertEquals(1, ps.executeUpdate());
        }
    }

    private static void ensurePopulationBulletins(Connection c, int period) throws Exception {
        var ids = new PayrollPopulationResolver().resolve(period, c).includedEmployeeIds();
        for (int employee : ids) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM bulletin_paie "
                    + "WHERE id_periode_paie=? AND id_employe=? AND COALESCE(type_bulletin,'MENSUEL')='MENSUEL'")) {
                ps.setInt(1, period); ps.setInt(2, employee);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) continue; }
            }
            insertCalculatedBulletin(c, employee, period);
        }
    }

    private static void isolatePayrollPopulation(Connection c, int employee) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif=FALSE WHERE id_employe<>?")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("UPDATE employe_affectation_carriere "
                + "SET date_fin=DATE '2027-12-31' WHERE id_employe<>? AND (date_fin IS NULL OR date_fin>DATE '2027-12-31')")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
    }

    private static void insertOpeningAcquisition(Connection c, int employee) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) "
                + "VALUES (?,'ACQUISITION',10,DATE '2028-01-01','Fixture isolée')")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
    }

    private static void insertLeaveMovement(Connection c, int employee, int demand, String type, String days) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,id_demande_conge,date_mouvement) VALUES (?,?,?,?,DATE '2028-09-10')")) {
            ps.setInt(1, employee); ps.setString(2, type); ps.setBigDecimal(3, new java.math.BigDecimal(days));
            ps.setInt(4, demand); ps.executeUpdate();
        }
    }

    private static int insertValidatedDemand(Connection c, int employee, int leaveType, String start, String end) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                + "(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes,est_remunere) "
                + "VALUES (?, ?, (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code='VALIDE'), ?, ?, 3, TRUE) "
                + "RETURNING id_demande_conge")) {
            ps.setInt(1, employee); ps.setInt(2, leaveType);
            ps.setObject(3, LocalDate.parse(start)); ps.setObject(4, LocalDate.parse(end));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int scalarInt(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next()); return rs.getInt(1);
        }
    }
}
