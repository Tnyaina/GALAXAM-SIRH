package mg.galaxam.sirh.conge.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.transaction.TransactionRunner;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.domain.DemandeCongeRequests.ModifierDemandeRequest;
import mg.galaxam.sirh.conge.persistence.VDemandeCongeCpl;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import mg.galaxam.sirh.securite.CustomUserDetails;

/** Lock and rollback behavior against an isolated disposable PostgreSQL database. */
class CongePointageLockPostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");

    @Test
    void operationsDeCongesRespectentLeGelPointageEtNeBloquePasLesAutresPeriodes() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = insertEmployee(c);
                insertEmploymentFixture(c, employee, "2024-01-01");
                int leaveType = scalarInt(c, "SELECT id_type_conge FROM ref_type_conge "
                        + "WHERE decompte_solde_acquis=TRUE ORDER BY id_type_conge LIMIT 1");
                insertOpeningAcquisition(c, employee, "2028-01-01", "10");
                int lockedPeriod = insertPeriod(c, "VALIDEE", "2028-06-01", "2028-06-30");
                int lockedDemand = insertDemand(c, employee, leaveType, "VALIDE", "2028-06-10", "2028-06-12");
                insertMovement(c, employee, "PRISE", lockedDemand, "2028-06-10");
                CongeService service = new CongeService(new CongeCalculateurEngine(), new CalendrierOuvrableEngine());

                SirhBusinessException cancellation = assertThrows(SirhBusinessException.class,
                        () -> service.annulerDemande(lockedDemand, null, "test", c));
                assertEquals("CONGE_POINTAGE_VERROUILLE", cancellation.getErrorCode().name());
                assertEquals("VALIDE", demandStatus(c, lockedDemand));
                assertEquals(1, movementCount(c, lockedDemand));

                ModifierDemandeRequest changeDates = new ModifierDemandeRequest(null, null,
                        LocalDate.of(2028, 6, 11), LocalDate.of(2028, 6, 13), null, null,
                        null, null, null, null, null, null);
                assertThrows(SirhBusinessException.class,
                        () -> service.modifierDemande(lockedDemand, changeDates, null, true, c));
                assertEquals(LocalDate.of(2028, 6, 10), demandDate(c, lockedDemand, "date_debut"));
                assertEquals(1, movementCount(c, lockedDemand));

                int lateDemand = insertDemand(c, employee, leaveType, "EN_ATTENTE", "2028-06-20", "2028-06-20");
                assertThrows(SirhBusinessException.class,
                        () -> service.validerDemande(lateDemand, null, null, c));
                assertEquals("EN_ATTENTE", demandStatus(c, lateDemand));
                assertEquals(0, movementCount(c, lateDemand));

                VDemandeCongeCpl view = VDemandeCongeCpl.rechercherParId(lockedDemand, c);
                service.appliquerActionsDisponibles(view, true, true, c);
                assertFalse(view.getAvailableActions().get("CANCEL").allowed());
                assertTrue(view.getAvailableActions().get("CANCEL").reasonCodes()
                        .contains("CONGE_POINTAGE_VERROUILLE"));

                int snapshotPeriod = insertPeriod(c, "EN_COURS", "2028-07-01", "2028-07-31");
                insertFrozenSnapshot(c, employee, snapshotPeriod);
                int snapshotDemand = insertDemand(c, employee, leaveType, "VALIDE", "2028-07-08", "2028-07-08");
                insertMovement(c, employee, "PRISE", snapshotDemand, "2028-07-08");
                assertThrows(SirhBusinessException.class,
                        () -> service.annulerDemande(snapshotDemand, null, "test snapshot", c));
                assertEquals("VALIDE", demandStatus(c, snapshotDemand));
                assertEquals(1, movementCount(c, snapshotDemand));

                int openPeriod = insertPeriod(c, "EN_COURS", "2028-08-01", "2028-08-31");
                int openDemand = insertDemand(c, employee, leaveType, "VALIDE", "2028-08-10", "2028-08-10");
                insertMovement(c, employee, "PRISE", openDemand, "2028-08-10");
                assertEquals("EN_COURS", periodStatus(c, openPeriod));
                service.annulerDemande(openDemand, null, "annulation en période ouverte", c);
                assertEquals("ANNULE", demandStatus(c, openDemand));
                assertEquals(2, movementCount(c, openDemand));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void endpointAnnulationDirectDeclineEtRollbackQuandLePointageEstVerrouille() throws Exception {
        assumeDatabaseConfigured();
        DataSource dataSource = new DriverManagerDataSource(URL, USER, PASSWORD);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            int employee = -1;
            int period = -1;
            int demand = -1;
            try {
                employee = insertEmployee(c);
                insertEmploymentFixture(c, employee, "2024-01-01");
                int leaveType = scalarInt(c, "SELECT id_type_conge FROM ref_type_conge "
                        + "WHERE decompte_solde_acquis=TRUE ORDER BY id_type_conge LIMIT 1");
                insertOpeningAcquisition(c, employee, "2028-01-01", "10");
                period = insertPeriod(c, "EN_COURS", "2029-03-01", "2029-03-31");
                demand = insertDemand(c, employee, leaveType, "VALIDE", "2029-03-12", "2029-03-12");
                insertMovement(c, employee, "PRISE", demand, "2029-03-12");
                insertFrozenSnapshot(c, employee, period);
                c.commit();

                var controller = new mg.galaxam.sirh.conge.api.DemandeCongeController();
                CustomUserDetails testUser = new CustomUserDetails(1, "integration-test", "unused",
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), true);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        testUser, "unused", testUser.getAuthorities()));
                setField(controller, "dataSource", dataSource);
                setField(controller, "congeService", new CongeService(
                        new CongeCalculateurEngine(), new CalendrierOuvrableEngine()));
                setField(controller, "transactionRunner", new TransactionRunner(dataSource));
                int directDemandId = demand;
                assertThrows(SirhBusinessException.class, () -> controller.annulerDemande(directDemandId,
                        new mg.galaxam.sirh.conge.domain.DemandeCongeRequests.DecisionDemandeRequest(null, "API directe")));
                try (Connection verify = dataSource.getConnection()) {
                    assertEquals("VALIDE", demandStatus(verify, demand));
                    assertEquals(1, movementCount(verify, demand));
                }
                int pendingDemand = insertDemand(c, employee, leaveType, "EN_ATTENTE", "2029-03-18", "2029-03-18");
                c.commit();
                assertThrows(SirhBusinessException.class,
                        () -> controller.validerDemande(pendingDemand, false, null, false));
                try (Connection verify = dataSource.getConnection()) {
                    assertEquals("EN_ATTENTE", demandStatus(verify, pendingDemand));
                    assertEquals(0, movementCount(verify, pendingDemand));
                }
            } finally {
                if (!c.getAutoCommit()) {
                    c.rollback();
                    cleanupControllerFixture(c, employee, period, demand);
                }
            }
        }
    }

    private static void cleanupControllerFixture(Connection c, int employee, int period, int demand) throws Exception {
        c.setAutoCommit(false);
        if (employee > 0) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM mouvement_solde_conge WHERE id_employe=?")) {
                ps.setInt(1, employee); ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM demande_conge WHERE id_employe=?")) {
                ps.setInt(1, employee); ps.executeUpdate();
            }
        }
        if (employee > 0 && period > 0) {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM pointage_cumul_periode WHERE id_employe=? AND id_periode_paie=?")) {
                ps.setInt(1, employee); ps.setInt(2, period); ps.executeUpdate();
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

    private static void assumeDatabaseConfigured() {
        assumeTrue(URL != null && !URL.isBlank(), "Use GALAXAM_CONGE_TEST_DB_URL pointing to disposable PostgreSQL");
    }

    private static Connection connection() throws Exception { return DriverManager.getConnection(URL, USER, PASSWORD); }

    private static int insertEmployee(Connection c) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,"
                + "cin_lieu_delivrance,adresse,telephone) VALUES (?, 'LOCK', 'Test', 'M', '1990-01-01', 'Test', ?, "
                + "'2010-01-01', 'Test', 'Adresse test', '000000') RETURNING id_employe")) {
            ps.setString(1, "IT-LK-" + suffix); ps.setString(2, "ITLK" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void insertEmploymentFixture(Connection c, int employee, String startDate) throws Exception {
        int contract;
        String contractSql = "INSERT INTO contrat_travail (id_employe,id_filiale,id_type_contrat,id_type_intervenant,"
                + "id_statut_rh,id_type_evenement_initial,numero_contrat,date_signature,date_debut,duree_essai_mois,comptabilise_effectif) "
                + "SELECT ?,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,id_type_evenement_initial,?,?,?,0,TRUE "
                + "FROM contrat_travail ORDER BY id_contrat LIMIT 1 RETURNING id_contrat";
        try (PreparedStatement ps = c.prepareStatement(contractSql)) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            ps.setInt(1, employee); ps.setString(2, "IT-LK-CON-" + suffix);
            ps.setObject(3, LocalDate.parse(startDate)); ps.setObject(4, LocalDate.parse(startDate));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); contract = rs.getInt(1); }
        }
        String assignmentSql = "INSERT INTO employe_affectation_carriere (id_employe,id_contrat,id_statut_rh,"
                + "id_motif_mouvement,id_site,id_departement,id_service,id_fonction,id_categorie_professionnelle,"
                + "id_mode_calcul_paie,salaire_base,date_debut) "
                + "SELECT ?,?,id_statut_rh,id_motif_mouvement,id_site,id_departement,id_service,id_fonction,"
                + "id_categorie_professionnelle,id_mode_calcul_paie,300000,? "
                + "FROM employe_affectation_carriere ORDER BY id_affectation LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(assignmentSql)) {
            ps.setInt(1, employee); ps.setInt(2, contract); ps.setObject(3, LocalDate.parse(startDate));
            assertEquals(1, ps.executeUpdate());
        }
    }

    private static int insertPeriod(Connection c, String status, String start, String end) throws Exception {
        String code = "IT-LOCK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO periode_paie(code,libelle,date_debut,date_fin,statut) "
                + "VALUES (?, 'Test congé verrouillage', ?, ?, ?) RETURNING id_periode_paie")) {
            ps.setString(1, code); ps.setObject(2, LocalDate.parse(start)); ps.setObject(3, LocalDate.parse(end));
            ps.setString(4, status);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int insertDemand(Connection c, int employee, int leaveType, String status,
            String start, String end) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                + "(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes,est_remunere) "
                + "VALUES (?, ?, (SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code=?), ?, ?, 1, TRUE) "
                + "RETURNING id_demande_conge")) {
            ps.setInt(1, employee); ps.setInt(2, leaveType); ps.setString(3, status);
            ps.setObject(4, LocalDate.parse(start)); ps.setObject(5, LocalDate.parse(end));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void insertMovement(Connection c, int employee, String type, int demand, String date) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,id_demande_conge,date_mouvement) VALUES (?,?,?,?,?)")) {
            ps.setInt(1, employee); ps.setString(2, type); ps.setBigDecimal(3, type.equals("PRISE")
                    ? new java.math.BigDecimal("-1") : new java.math.BigDecimal("1"));
            ps.setInt(4, demand); ps.setObject(5, LocalDate.parse(date)); ps.executeUpdate();
        }
    }

    private static void insertOpeningAcquisition(Connection c, int employee, String date, String days) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) VALUES (?,'ACQUISITION',?,?,?)")) {
            ps.setInt(1, employee); ps.setBigDecimal(2, new java.math.BigDecimal(days));
            ps.setObject(3, LocalDate.parse(date)); ps.setString(4, "Fixture isolée"); ps.executeUpdate();
        }
    }

    private static void insertFrozenSnapshot(Connection c, int employee, int period) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pointage_cumul_periode "
                + "(id_employe,id_periode_paie,nb_jours_ouvrables,statut_snapshot) VALUES (?, ?, 22, 'GELE')")) {
            ps.setInt(1, employee); ps.setInt(2, period); ps.executeUpdate();
        }
    }

    private static String demandStatus(Connection c, int demand) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT s.code FROM demande_conge d "
                + "JOIN ref_statut_demande_conge s USING (id_statut_demande_conge) WHERE d.id_demande_conge=?")) {
            ps.setInt(1, demand); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getString(1); }
        }
    }

    private static LocalDate demandDate(Connection c, int demand, String column) throws Exception {
        assertTrue("date_debut".equals(column) || "date_fin".equals(column));
        try (PreparedStatement ps = c.prepareStatement("SELECT " + column + " FROM demande_conge WHERE id_demande_conge=?")) {
            ps.setInt(1, demand); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getDate(1).toLocalDate(); }
        }
    }

    private static int movementCount(Connection c, int demand) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge=?")) {
            ps.setInt(1, demand); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static String periodStatus(Connection c, int period) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT statut FROM periode_paie WHERE id_periode_paie=?")) {
            ps.setInt(1, period); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getString(1); }
        }
    }

    private static int scalarInt(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            assertTrue(rs.next()); return rs.getInt(1);
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }
}
