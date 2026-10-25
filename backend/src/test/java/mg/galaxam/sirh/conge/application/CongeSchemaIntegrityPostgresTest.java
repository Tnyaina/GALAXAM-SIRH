package mg.galaxam.sirh.conge.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

/** Checks canonical leave integrity constraints against an isolated PostgreSQL database. */
class CongeSchemaIntegrityPostgresTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");

    @Test
    void refuseMouvementPourUnAutreSalarieEtSigneIncoherent() throws Exception {
        assumeTrue(URL != null && !URL.isBlank());
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            try {
                int employeeA = employee(c, "A");
                int employeeB = employee(c, "B");
                int demand = demand(c, employeeA);
                assertConstraintRejects(c,
                        "INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,id_demande_conge) VALUES (?, 'PRISE', -1, ?)", employeeB, demand);
                assertConstraintRejects(c,
                        "INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours) VALUES (?, 'PRISE', 1)", employeeA);
                int movement = movement(c, employeeA);
                int journal = journal(c, employeeA, movement);
                assertConstraintRejects(c,
                        "INSERT INTO conge_valorisation_mensuelle_correction(id_journal,id_employe,id_mouvement_acquisition,ancienne_valeur,nouvelle_valeur,motif,corrige_par) VALUES (?,?,?,100,110,'test',?)",
                        journal, employeeB, movement, user(c));
            } finally { c.rollback(); }
        }
    }

    @Test
    void refuseTauxApplicablesQuiSeChevauchentPourUnSalarie() throws Exception {
        assumeTrue(URL != null && !URL.isBlank());
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c, "RATE");
                execute(c, "INSERT INTO conge_taux_applicable(id_employe,taux_journalier,date_effet,date_fin,source_provenance) VALUES (?,100,'2030-01-01','2030-02-01','INITIALISATION_BASE_30')", employee);
                assertThrows(SQLException.class, () -> execute(c,
                        "INSERT INTO conge_taux_applicable(id_employe,taux_journalier,date_effet,date_fin,source_provenance) VALUES (?,110,'2030-01-31','2030-03-01','INITIALISATION_BASE_30')", employee));
            } finally { c.rollback(); }
        }
    }

    private static int employee(Connection c, String name) throws Exception {
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) VALUES (?,?,'Schema','M','1990-01-01','Test',?,'2010-01-01','Test','Test','000') RETURNING id_employe")) {
            ps.setString(1, "IT-SC-" + suffix); ps.setString(2, name); ps.setString(3, "ITS" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int demand(Connection c, int employee) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes) SELECT ?,t.id_type_conge,s.id_statut_demande_conge,'2030-05-01','2030-05-01',1 FROM ref_type_conge t CROSS JOIN ref_statut_demande_conge s WHERE t.code='ANNUEL' AND s.code='EN_ATTENTE' RETURNING id_demande_conge")) {
            ps.setInt(1, employee);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int journal(Connection c, int employee, int movement) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO conge_journal_droit(id_employe,annee,mois,salaire_base,base_valorisation,valorisation_mensuelle,cumul_jours_droit,cumul_valorisation,taux_journalier_conge,id_periode_paie) VALUES (?,2030,1,100,100,100,1,100,100,?) RETURNING id_journal")) {
            ps.setInt(1, employee); ps.setInt(2, payrollPeriod(c)); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int movement(Connection c, int employee) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,valeur_source) VALUES (?,'ACQUISITION',1,100) RETURNING id_mouvement")) {
            ps.setInt(1, employee); try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int user(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO utilisateur(identifiant,email,mot_de_passe_hache,id_role) VALUES (?,?, 'test', 1) RETURNING id_utilisateur")) {
            String suffix=java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            ps.setString(1,"IT-"+suffix); ps.setString(2,"it-"+suffix+"@example.test");
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static int payrollPeriod(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO periode_paie(code,libelle,date_debut,date_fin,statut) VALUES (?, 'Test schema congé','2030-01-01','2030-01-31','EN_COURS') RETURNING id_periode_paie")) {
            ps.setString(1,"ITSC"+java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10)); try (ResultSet rs=ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            ps.executeUpdate();
        }
    }

    private static void assertConstraintRejects(Connection c, String sql, Object... params) throws Exception {
        var savepoint = c.setSavepoint();
        try {
            execute(c, sql, params);
            fail("Expected a database constraint violation.");
        } catch (SQLException expected) {
            c.rollback(savepoint);
        }
    }
}
