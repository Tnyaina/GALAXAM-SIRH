package mg.galaxam.sirh.contrat.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.http.ResponseEntity;

class ContratTravailControllerPostgresTest {
    private static final String READER_ROLE = "phase4c_contract_reader";

    @Test
    void enrichitHistoriqueEtPropageLesErreursDeReference() throws Exception {
        String url = System.getenv("GALAXAM_CONTRAT_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire dédiée requise");
        String user = System.getenv("GALAXAM_CONTRAT_TEST_DB_USER");
        String password = System.getenv("GALAXAM_CONTRAT_TEST_DB_PASSWORD");
        int employeSansContrat;
        int employeAvecUnContrat;
        int employeAvecHistorique;
        String cinSans = cinUnique();
        String cinUn = cinUnique();
        String cinMulti = cinUnique();
        int idFiliale;
        int idTypeContrat;
        int idIntervenant;
        int idStatut;

        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            idFiliale = entier(c, "SELECT id_filiale FROM ref_filiale ORDER BY id_filiale LIMIT 1");
            idTypeContrat = entier(c, "SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI' LIMIT 1");
            idIntervenant = entier(c, "SELECT id_type_intervenant FROM ref_type_intervenant ORDER BY id_type_intervenant LIMIT 1");
            idStatut = entier(c, "SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF' LIMIT 1");
            employeSansContrat = insererEmploye(c, cinSans);
            employeAvecUnContrat = insererEmploye(c, cinUn);
            employeAvecHistorique = insererEmploye(c, cinMulti);
            insererContrat(c, employeAvecUnContrat, idFiliale, idTypeContrat, idIntervenant, idStatut,
                    "CTR-TEST-" + UUID.randomUUID(), "2022-01-01", null);
            insererContrat(c, employeAvecHistorique, idFiliale, idTypeContrat, idIntervenant, idStatut,
                    "CTR-TEST-" + UUID.randomUUID(), "2018-01-01", "2020-01-01");
            insererContrat(c, employeAvecHistorique, idFiliale, idTypeContrat, idIntervenant, idStatut,
                    "CTR-TEST-" + UUID.randomUUID(), "2021-01-01", null);
            c.commit();
        }

        try {
            ContratTravailController controller = controller(newSource(url, user, password));
            List<?> sansContrat = corpsListe(controller.getContratsEmploye(employeSansContrat));
            assertTrue(sansContrat.isEmpty());

            List<?> unContrat = corpsListe(controller.getContratsEmploye(employeAvecUnContrat));
            assertEquals(1, unContrat.size());
            Map<?, ?> contratSimple = assertInstanceOf(Map.class, unContrat.get(0));
            verifierPayloadReferences(url, user, password, contratSimple, idFiliale, idTypeContrat, idStatut);

            List<?> historique = corpsListe(controller.getContratsEmploye(employeAvecHistorique));
            assertEquals(2, historique.size());
            assertTrue(((Date) ((Map<?, ?>) historique.get(0)).get("dateDebut"))
                    .after((Date) ((Map<?, ?>) historique.get(1)).get("dateDebut")));
            assertTrue(((Map<?, ?>) historique.get(0)).containsKey("ancienneteEffectiveTexte"));
            assertTrue(((Map<?, ?>) historique.get(1)).containsKey("ancienneteEffectiveTexte"));

            try (Connection c = DriverManager.getConnection(url, user, password)) {
                SQLException fk = assertThrows(SQLException.class, () -> insererContrat(c, employeAvecUnContrat,
                        Integer.MAX_VALUE, idTypeContrat, idIntervenant, idStatut, "CTR-TEST-FK", "2024-01-01", null));
                assertEquals("23503", fk.getSQLState(), "Les FK empêchent les références de référentiel manquantes.");
            }

            testerErreurDeLecture(url, user, password, employeAvecUnContrat);
        } finally {
            try (Connection c = DriverManager.getConnection(url, user, password);
                 PreparedStatement ps = c.prepareStatement("DELETE FROM employe WHERE cin_numero IN (?, ?, ?)")) {
                ps.setString(1, cinSans);
                ps.setString(2, cinUn);
                ps.setString(3, cinMulti);
                ps.executeUpdate();
            }
            supprimerRoleLecture(url, user, password);
        }
    }

    private static void verifierPayloadReferences(String url, String user, String password, Map<?, ?> payload,
            int idFiliale, int idTypeContrat, int idStatut) throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password);
             PreparedStatement ps = c.prepareStatement("SELECT rf.raison_sociale, rf.nif, rf.stat, rtc.libelle, "
                     + "rtc.code, rs.code, rs.libelle FROM ref_filiale rf CROSS JOIN ref_type_contrat rtc "
                     + "CROSS JOIN ref_statut_rh rs WHERE rf.id_filiale = ? AND rtc.id_type_contrat = ? "
                     + "AND rs.id_statut_rh = ?")) {
            ps.setInt(1, idFiliale);
            ps.setInt(2, idTypeContrat);
            ps.setInt(3, idStatut);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(rs.getString(1), payload.get("libelleFiliale"));
                Map<?, ?> filiale = assertInstanceOf(Map.class, payload.get("filiale"));
                assertEquals(rs.getString(1), filiale.get("libelle"));
                assertEquals(rs.getString(2), filiale.get("nif"));
                assertEquals(rs.getString(3), filiale.get("stat"));
                assertEquals(rs.getString(4), payload.get("libelleTypeContrat"));
                assertEquals(rs.getString(5), payload.get("codeTypeContrat"));
                assertEquals(rs.getString(6), payload.get("codeStatutContrat"));
                assertEquals(rs.getString(7), payload.get("libelleStatutContrat"));
                assertEquals(Boolean.TRUE, payload.get("isActif"));
                assertNotNull(payload.get("idContrat"));
            }
        }
    }

    private static void testerErreurDeLecture(String url, String user, String password, int idEmploye)
            throws Exception {
        try (Connection c = DriverManager.getConnection(url, user, password);
             var statement = c.createStatement()) {
            statement.execute("CREATE ROLE " + READER_ROLE + " LOGIN PASSWORD 'phase4c_reader_only'");
            statement.execute("GRANT USAGE ON SCHEMA public TO " + READER_ROLE);
            statement.execute("GRANT SELECT ON ALL TABLES IN SCHEMA public TO " + READER_ROLE);
            statement.execute("REVOKE SELECT ON ref_statut_rh FROM " + READER_ROLE);
        }
        PGSimpleDataSource restricted = newSource(url, READER_ROLE, "phase4c_reader_only");
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> controller(restricted).getContratsEmploye(idEmploye));
        assertFalse(failure.getMessage() == null || failure.getMessage().isBlank());
    }

    private static void supprimerRoleLecture(String url, String user, String password) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, user, password);
             var statement = c.createStatement()) {
            statement.execute("DROP OWNED BY " + READER_ROLE);
            statement.execute("DROP ROLE " + READER_ROLE);
        }
    }

    private static ContratTravailController controller(DataSource source) throws Exception {
        ContratTravailController controller = new ContratTravailController();
        Field field = ContratTravailController.class.getDeclaredField("dataSource");
        field.setAccessible(true);
        field.set(controller, source);
        return controller;
    }

    private static PGSimpleDataSource newSource(String url, String user, String password) {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(url);
        source.setUser(user);
        source.setPassword(password);
        return source;
    }

    private static List<?> corpsListe(ResponseEntity<?> response) {
        assertEquals(200, response.getStatusCode().value());
        return assertInstanceOf(List.class, response.getBody());
    }

    private static int insererEmploye(Connection c, String cin) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule, nom, prenom, sexe, date_naissance, lieu_naissance, cin_numero, "
                + "cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) "
                + "VALUES (?, 'EmployeTest', 'ContratTest', 'M', DATE '1990-01-01', 'Test', ?, "
                + "DATE '2010-01-01', 'Test', 'Adresse synthétique', '0000000000') RETURNING id_employe")) {
            ps.setString(1, "CTRTEST-" + UUID.randomUUID().toString().substring(0, 8));
            ps.setString(2, cin);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void insererContrat(Connection c, int idEmploye, int idFiliale, int idType, int idIntervenant,
            int idStatut, String numero, String debut, String fin) throws SQLException {
        String sql = "INSERT INTO contrat_travail (id_employe, id_filiale, id_type_contrat, id_type_intervenant, "
                + "id_statut_rh, numero_contrat, date_debut, date_fin) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idFiliale);
            ps.setInt(3, idType);
            ps.setInt(4, idIntervenant);
            ps.setInt(5, idStatut);
            ps.setString(6, numero);
            ps.setDate(7, Date.valueOf(debut));
            ps.setDate(8, fin == null ? null : Date.valueOf(fin));
            ps.executeUpdate();
        }
    }

    private static int entier(Connection c, String sql) throws SQLException {
        try (var statement = c.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            if (!rs.next()) throw new SQLException("Référentiel de test absent pour " + sql);
            return rs.getInt(1);
        }
    }

    private static String cinUnique() {
        return String.format("%012d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000_000L));
    }
}
