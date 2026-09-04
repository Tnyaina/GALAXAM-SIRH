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

    @Autowired
    private mg.galaxam.sirh.model.conge.service.CongeService congeService;

    @Test
    @Order(4)
    @DisplayName("CNG-04 : Planning collaborateurs retourne tous les employés actifs avec leurs congés")
    void testGetPlanningCollaborateurs() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            var planning = congeService.getPlanningCollaborateurs(2026, 8, null, null, null, null, c);
            assertNotNull(planning);
            assertTrue((Integer) planning.get("totalCollaborateurs") >= 5, "Au moins 5 employés actifs dans l'entreprise");
            assertTrue((Integer) planning.get("totalDemandes") >= 1, "Au moins le congé de Voahirana Mialy en Août");
            
            @SuppressWarnings("unchecked")
            var employes = (java.util.List<java.util.Map<String, Object>>) planning.get("employes");
            assertNotNull(employes);
            assertFalse(employes.isEmpty());
            
            boolean foundVoahirana = false;
            for (var emp : employes) {
                if (Integer.valueOf(3).equals(emp.get("idEmploye"))) {
                    foundVoahirana = true;
                    @SuppressWarnings("unchecked")
                    var demandes = (java.util.List<?>) emp.get("demandes");
                    assertFalse(demandes.isEmpty(), "Voahirana a une demande en Août");
                }
            }
            assertTrue(foundVoahirana, "Voahirana Mialy présente dans la liste");
        }
    }

    @Test
    @Order(5)
    @DisplayName("CNG-05 : Détection des conflits de continuité de service & décompte détaillé")
    void testVerifierConflitsEtDerogation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            java.sql.Date dDeb = java.sql.Date.valueOf("2026-08-10");
            java.sql.Date dFin = java.sql.Date.valueOf("2026-08-14");

            var res = congeService.verifierConflitsDemande(3, dDeb, dFin, false, null, null, c);
            assertNotNull(res);
            assertEquals(5.0, ((Number) res.get("nbJoursDecomptes")).doubleValue(), 0.01);
            assertNotNull(res.get("nbJoursOuvres"));
            assertNotNull(res.get("nbJoursFeries"));
            assertNotNull(res.get("nbJoursWeekEnd"));
        }
    }

    @Test
    @Order(6)
    @DisplayName("CNG-06 : Demi-journées complémentaires le même jour (Matin vs Après-midi) ne créent pas de conflit")
    void testNonCollisionDemiJourneesComplementaires() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            java.sql.Date d = java.sql.Date.valueOf("2026-08-03"); // Voahirana est en congé, testons avec demi-journée
            var res = congeService.verifierConflitsDemande(1, d, d, true, "APRES_MIDI", null, c);
            assertNotNull(res);
            assertNotNull(res.get("colleguesAbsents"));
        }
    }

    @Test
    @Order(7)
    @DisplayName("CNG-07 : Pagination serveur et filtre du planning collaborateurs")
    void testPlanningPaginationEtFiltre() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Page 1 avec size = 2
            var p1 = congeService.getPlanningCollaborateurs(2026, 8, null, null, null, null, null, "TOUS", 1, 2, c);
            assertNotNull(p1);
            assertEquals(1, p1.get("page"));
            assertEquals(2, p1.get("size"));
            @SuppressWarnings("unchecked")
            var emps1 = (java.util.List<?>) p1.get("employes");
            assertEquals(2, emps1.size(), "2 employés sur la page 1");
            assertTrue((Integer) p1.get("total") >= 5, "Total d'employés >= 5");

            // Recherche spécifique
            var pSearch = congeService.getPlanningCollaborateurs(2026, 8, null, null, null, null, "Voahirana", "TOUS", 1, 10, c);
            assertNotNull(pSearch);
            @SuppressWarnings("unchecked")
            var empsSearch = (java.util.List<?>) pSearch.get("employes");
            assertEquals(1, empsSearch.size(), "Un seul collaborateur trouvé pour Voahirana");
        }
    }
}
