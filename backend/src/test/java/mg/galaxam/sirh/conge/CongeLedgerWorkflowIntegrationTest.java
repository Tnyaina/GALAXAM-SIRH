package mg.galaxam.sirh.conge;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CongeLedgerWorkflowIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @Order(1)
    @DisplayName("CNG-01 : Bonifications légales d'ancienneté (+1j par tranche de 5 ans)")
    void testBonificationsAnciennete() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Emp 1 (20 ans d'ancienneté) : +4 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_employe = 1 AND type_mouvement = 'BONIFICATION'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(4.0, rs.getDouble("nb_jours"), 0.01, "20 ans d'ancienneté = +4 jours de bonification");
            }

            // Emp 2 (12 ans d'ancienneté) : +2 jours
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_employe = 2 AND type_mouvement = 'BONIFICATION'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(2.0, rs.getDouble("nb_jours"), 0.01, "12 ans d'ancienneté (palier 10 ans) = +2 jours");
            }

            // Emp 3 (7 ans d'ancienneté) : +1 jour
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge WHERE id_employe = 3 AND type_mouvement = 'BONIFICATION'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(1.0, rs.getDouble("nb_jours"), 0.01, "7 ans d'ancienneté (palier 5 ans) = +1 jour");
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("CNG-02 : Prise de congés validée d'Août 2026 pour Voahirana Mialy (5 jours ouvrables)")
    void testDemandeCongeAoutEmp3() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Demande validée
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT date_debut, date_fin, nb_jours_decomptes, id_statut_demande_conge " +
                    "FROM demande_conge WHERE id_employe = 3 AND date_debut = '2026-08-03'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "La demande de congé d'Août doit exister");
                assertEquals("2026-08-03", rs.getString("date_debut"));
                assertEquals("2026-08-07", rs.getString("date_fin"));
                assertEquals(5.0, rs.getDouble("nb_jours_decomptes"), 0.01, "5 jours ouvrables décomptés");
                assertEquals(3, rs.getInt("id_statut_demande_conge"), "Statut VALIDEE");
            }

            // Débit dans le grand livre mouvement_solde_conge
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT nb_jours FROM mouvement_solde_conge " +
                    "WHERE id_employe = 3 AND type_mouvement = 'PRISE' AND id_demande_conge = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(-5.0, rs.getDouble("nb_jours"), 0.01, "Débit négatif de -5.0 jours");
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("CNG-03 : Solde net des congés calculé via la vue officielle v_solde_conge_employe")
    void testSoldesNetsVueOfficielle() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Emp 1 : 31 + 4 = 35j (au moins 35j avec acquisition mensuelle)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getDouble("solde_actuel") >= 35.0, "Le solde net doit être d'au moins 35 jours");
            }

            // Emp 2 : 26 + 2 = 28j (au moins 28j)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = 2")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getDouble("solde_actuel") >= 28.0, "Le solde net doit être d'au moins 28 jours");
            }

            // Emp 3 : 24 + 1 - 5 = 20j (au moins 20j)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT solde_actuel FROM v_solde_conge_employe WHERE id_employe = 3")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getDouble("solde_actuel") >= 20.0, "Le solde net doit être d'au moins 20 jours");
            }
        }
    }
}
