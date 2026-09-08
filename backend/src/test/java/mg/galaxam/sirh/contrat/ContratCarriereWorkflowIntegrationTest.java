package mg.galaxam.sirh.contrat;

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
public class ContratCarriereWorkflowIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @Order(1)
    @DisplayName("CAR-01 : Carrière sur 20 ans d'Andry Nirina (Aide-Comptable -> Chef Comptable -> DAF Cadre)")
    void testCarriereVingtAnsEmp1() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // 3 contrats successifs
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_contrat, numero_contrat, id_statut_rh, date_debut, date_fin " +
                    "FROM contrat_travail WHERE id_employe = 1 ORDER BY date_debut ASC")) {
                ResultSet rs = ps.executeQuery();
                
                // Contrat 1 : 2006 (Clôturé)
                assertTrue(rs.next());
                assertEquals("CT-2006-CDI-001", rs.getString("numero_contrat"));
                assertEquals(7, rs.getInt("id_statut_rh"), "Le contrat 2006 doit être clôturé");
                assertEquals("2006-02-01", rs.getString("date_debut"));
                assertEquals("2011-02-28", rs.getString("date_fin"));

                // Contrat 2 : 2011 (Clôturé)
                assertTrue(rs.next());
                assertEquals("CT-2011-CDI-002", rs.getString("numero_contrat"));
                assertEquals(7, rs.getInt("id_statut_rh"), "Le contrat 2011 doit être clôturé");
                assertEquals("2011-03-01", rs.getString("date_debut"));
                assertEquals("2017-12-31", rs.getString("date_fin"));

                // Contrat 3 : 2018 (Actif DAF)
                assertTrue(rs.next());
                assertEquals("CT-2018-CDI-003", rs.getString("numero_contrat"));
                assertEquals(4, rs.getInt("id_statut_rh"), "Le contrat 2018 doit être actif");
                assertEquals("2018-01-01", rs.getString("date_debut"));
                assertNull(rs.getString("date_fin"), "Contrat CDI en cours sans date de fin");
                
                assertFalse(rs.next(), "Exactement 3 contrats pour l'employé 1");
            }

            // Affectation active : DAF, Salaire 3 800 000 Ar, Site TNR
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.salaire_base, f.code_fonction, s.code_site " +
                    "FROM employe_affectation_carriere a " +
                    "JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                    "JOIN ref_site s ON a.id_site = s.id_site " +
                    "WHERE a.id_employe = 1 AND a.id_statut_rh = 4")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals(3800000.00, rs.getDouble("salaire_base"), 0.01);
                assertEquals("DAF", rs.getString("code_fonction"));
                assertEquals("TNR", rs.getString("code_site"));
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("CAR-02 : Mutation géographique de Jean Jacques (Siège TNR -> Port Tamatave TMM)")
    void testMutationGeographiqueEmp2() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Vérification de l'ancienne affectation TNR (clôturée) et de la nouvelle TMM (active)
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT a.id_site, s.code_site, a.id_statut_rh, a.salaire_base, a.date_debut, a.date_fin " +
                    "FROM employe_affectation_carriere a " +
                    "JOIN ref_site s ON a.id_site = s.id_site " +
                    "WHERE a.id_employe = 2 ORDER BY a.date_debut ASC")) {
                ResultSet rs = ps.executeQuery();

                // 1. CDD initial TNR (2014)
                assertTrue(rs.next());
                assertEquals("TNR", rs.getString("code_site"));
                assertEquals(7, rs.getInt("id_statut_rh"));
                assertEquals(700000.00, rs.getDouble("salaire_base"), 0.01);

                // 2. CDI TNR (2015-2021)
                assertTrue(rs.next());
                assertEquals("TNR", rs.getString("code_site"));
                assertEquals(7, rs.getInt("id_statut_rh"));
                assertEquals(1100000.00, rs.getDouble("salaire_base"), 0.01);

                // 3. Mutation TMM (2022 - Actif)
                assertTrue(rs.next());
                assertEquals("TMM", rs.getString("code_site"), "La nouvelle affectation doit être sur le site TMM");
                assertEquals(4, rs.getInt("id_statut_rh"), "L'affectation TMM doit être active");
                assertEquals(1850000.00, rs.getDouble("salaire_base"), 0.01, "Salaire revalorisé lors de la mutation");
                assertEquals("2022-01-01", rs.getString("date_debut"));
                assertNull(rs.getString("date_fin"));
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("CAR-03 : Nouvel embauché en cours de mois (Faly Christian le 17 Août 2026 avec période d'essai)")
    void testNouvelEmbauchePeriodeEssaiEmp5() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT c.numero_contrat, c.date_debut, c.duree_essai_mois, c.date_fin_essai, a.salaire_base, fil.code_filiale " +
                    "FROM contrat_travail c " +
                    "JOIN employe_affectation_carriere a ON c.id_contrat = a.id_contrat " +
                    "JOIN ref_filiale fil ON c.id_filiale = fil.id_filiale " +
                    "WHERE c.id_employe = 5 AND c.id_statut_rh = 4")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertEquals("CT-2026-CDI-005", rs.getString("numero_contrat"));
                assertEquals("2026-08-17", rs.getString("date_debut"), "Date d'embauche au 17 Août 2026");
                assertEquals(3, rs.getInt("duree_essai_mois"), "Période d'essai de 3 mois");
                assertEquals("2026-11-16", rs.getString("date_fin_essai"));
                assertEquals(1050000.00, rs.getDouble("salaire_base"), 0.01);
                assertEquals("VA", rs.getString("code_filiale"), "Embauché dans la filiale Vina sy Asa");
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("CAR-04 : Unicité stricte du contrat actif par collaborateur (Règle d'intégrité métier)")
    void testUniciteContratActif() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_employe, COUNT(*) as nb_actifs " +
                    "FROM contrat_travail WHERE id_statut_rh = 4 " +
                    "GROUP BY id_employe")) {
                ResultSet rs = ps.executeQuery();
                int count = 0;
                while (rs.next()) {
                    count++;
                    assertEquals(1, rs.getInt("nb_actifs"), "Chaque collaborateur ne peut avoir qu'un seul contrat actif simultanément");
                }
                assertTrue(count >= 5, "Au moins 5 salariés doivent avoir exactement 1 contrat actif");
            }
        }
    }
}
