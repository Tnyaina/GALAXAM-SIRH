package mg.galaxam.sirh.adversarial;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class CongeAdversarialTest extends BaseIntegrationTest {

    private int idEmploye;
    private int idTypeCongeAnnuel;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        // Employé avec 2 ans d'ancienneté pour satisfaire les critères de base
        var emp = employeFixtures.createActiveEmploye("EMP-ADV-CNG", "ANDRIAMAMPIANINA", "Tahina", 1, LocalDate.now().minusYears(2));
        idEmploye = emp.idEmploye;

        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL'")) {
            var rs = ps.executeQuery();
            rs.next();
            idTypeCongeAnnuel = rs.getInt(1);
        }
    }

    @Test
    @DisplayName("Adversarial API Congé : Rejet d'une demande avec date de fin antérieure à la date de début")
    public void testRejetDateFinAnterieureDateDebut() throws Exception {
        LocalDate debut = LocalDate.now().plusWeeks(3);
        LocalDate finAnterieure = debut.minusDays(2); // Inversion illégale

        Map<String, Object> body = new HashMap<>();
        body.put("idEmploye", idEmploye);
        body.put("idTypeConge", idTypeCongeAnnuel);
        body.put("dateDebut", debut.toString());
        body.put("dateFin", finAnterieure.toString());

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("antérieure à la date de début")));
    }

    @Test
    @DisplayName("Adversarial API Congé : Rejet pour solde insuffisant sans dérogation solde négatif")
    public void testRejetSoldeInsuffisantSansDerogation() throws Exception {
        // Solde initial = 0
        LocalDate debut = LocalDate.now().plusWeeks(4);
        LocalDate fin = debut.plusDays(4); // 5 jours

        Map<String, Object> body = new HashMap<>();
        body.put("idEmploye", idEmploye);
        body.put("idTypeConge", idTypeCongeAnnuel);
        body.put("dateDebut", debut.toString());
        body.put("dateFin", fin.toString());
        body.put("derogationSoldeNegatif", false);

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Solde de congé insuffisant")));
    }

    @Test
    @DisplayName("Adversarial API Congé : Rejet pour non-respect du délai de préavis sans dérogation préavis")
    public void testRejetPreavisInsuffisantSansDerogation() throws Exception {
        // Créditer le solde de congé pour éliminer l'erreur de solde
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement) " +
                             "VALUES (?, 'ACQUISITION', 20.0, CURRENT_DATE)")) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }

        // Préavis court : départ dans 3 jours pour un congé de 5 jours (préavis légal requis = 15 jours)
        LocalDate debutDans3Jours = LocalDate.now().plusDays(3);
        LocalDate fin = debutDans3Jours.plusDays(4);

        Map<String, Object> body = new HashMap<>();
        body.put("idEmploye", idEmploye);
        body.put("idTypeConge", idTypeCongeAnnuel);
        body.put("dateDebut", debutDans3Jours.toString());
        body.put("dateFin", fin.toString());
        body.put("derogationSoldeNegatif", false);
        body.put("derogationDelaiPreavis", false);

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("préavis minimum requis")));
    }

    @Test
    @DisplayName("Adversarial API Congé : Rejet lors d'un chevauchement avec une demande existante")
    public void testRejetChevauchementDemandes() throws Exception {
        // 1. Créditer le solde de 20 jours
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement) " +
                             "VALUES (?, 'ACQUISITION', 20.0, CURRENT_DATE)")) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }

        // 2. Créer une première demande valide du 20 au 25 du mois prochain via API
        LocalDate debut1 = LocalDate.now().plusWeeks(3);
        LocalDate fin1 = debut1.plusDays(5);

        Map<String, Object> body1 = new HashMap<>();
        body1.put("idEmploye", idEmploye);
        body1.put("idTypeConge", idTypeCongeAnnuel);
        body1.put("dateDebut", debut1.toString());
        body1.put("dateFin", fin1.toString());

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body1)))
                .andExpect(status().isCreated());

        // 3. Tenter une 2nde demande chevauchant la première via API
        LocalDate debut2 = fin1.minusDays(2);
        LocalDate fin2 = debut2.plusDays(4);

        Map<String, Object> body2 = new HashMap<>();
        body2.put("idEmploye", idEmploye);
        body2.put("idTypeConge", idTypeCongeAnnuel);
        body2.put("dateDebut", debut2.toString());
        body2.put("dateFin", fin2.toString());

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body2)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("Chevauchement détecté")));
    }

    @Test
    @DisplayName("Adversarial API Congé : Rejet pour ancienneté insuffisante (< 12 mois de service pour congé annuel)")
    public void testRejetAncienneteInsuffisantePourCongeAnnuel() throws Exception {
        // Recréer un employé ayant seulement 3 mois d'ancienneté
        databaseCleaner.cleanTransactionalData();
        var empRecent = employeFixtures.createActiveEmploye("EMP-RECENT", "RABARY", "Mamy", 1, LocalDate.now().minusMonths(3));

        // Même s'il a un solde acquis
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement) " +
                             "VALUES (?, 'ACQUISITION', 7.5, CURRENT_DATE)")) {
            ps.setInt(1, empRecent.idEmploye);
            ps.executeUpdate();
        }

        LocalDate debut = LocalDate.now().plusWeeks(3);
        LocalDate fin = debut.plusDays(4);

        Map<String, Object> body = new HashMap<>();
        body.put("idEmploye", empRecent.idEmploye);
        body.put("idTypeConge", idTypeCongeAnnuel);
        body.put("dateDebut", debut.toString());
        body.put("dateFin", fin.toString());
        body.put("derogationSoldeNegatif", false);

        mockMvc.perform(post("/conges/demandes")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(body)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("ancienneté")));
    }
}
