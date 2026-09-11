package mg.galaxam.sirh.adversarial;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.model.conge.DemandeConge;
import mg.galaxam.sirh.model.conge.service.CongeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class CongeAdversarialTest extends BaseIntegrationTest {

    @Autowired
    private CongeService congeService;

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
    @DisplayName("Adversarial Congé : Rejet d'une demande avec date de fin antérieure à la date de début")
    public void testRejetDateFinAnterieureDateDebut() {
        LocalDate debut = LocalDate.now().plusWeeks(3);
        LocalDate finAnterieure = debut.minusDays(2); // Inversion illégale

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(idEmploye);
        d.setIdTypeConge(idTypeCongeAnnuel);
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(finAnterieure));

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    congeService.creerDemande(d, 1, c));
            assertTrue(ex.getMessage().contains("antérieure à la date de début"));
        } catch (Exception e) {
            fail("Erreur inattendue : " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Adversarial Congé : Rejet pour solde insuffisant sans dérogation solde négatif")
    public void testRejetSoldeInsuffisantSansDerogation() {
        // Solde initial = 0
        LocalDate debut = LocalDate.now().plusWeeks(4);
        LocalDate fin = debut.plusDays(4); // 5 jours

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(idEmploye);
        d.setIdTypeConge(idTypeCongeAnnuel);
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(fin));
        d.setDerogationSoldeNegatif(false);

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    congeService.creerDemande(d, 1, c));
            assertTrue(ex.getMessage().contains("Solde de congé insuffisant"));
        } catch (Exception e) {
            fail("Erreur inattendue : " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Adversarial Congé : Rejet pour non-respect du délai de préavis sans dérogation préavis")
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

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(idEmploye);
        d.setIdTypeConge(idTypeCongeAnnuel);
        d.setDateDebut(Date.valueOf(debutDans3Jours));
        d.setDateFin(Date.valueOf(fin));
        d.setDerogationSoldeNegatif(false);
        d.setDerogationDelaiPreavis(false);

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    congeService.creerDemande(d, 1, c));
            assertTrue(ex.getMessage().contains("préavis minimum requis"),
                    "L'exception doit expliciter le manquement au préavis légal");
        }
    }

    @Test
    @DisplayName("Adversarial Congé : Rejet lors d'un chevauchement avec une demande existante")
    public void testRejetChevauchementDemandes() throws Exception {
        // 1. Créditer le solde de 20 jours
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, date_mouvement) " +
                             "VALUES (?, 'ACQUISITION', 20.0, CURRENT_DATE)")) {
            ps.setInt(1, idEmploye);
            ps.executeUpdate();
        }

        // 2. Créer une première demande valide du 20 au 25 du mois prochain
        LocalDate debut1 = LocalDate.now().plusWeeks(3);
        LocalDate fin1 = debut1.plusDays(5);

        DemandeConge d1 = new DemandeConge();
        d1.setIdEmploye(idEmploye);
        d1.setIdTypeConge(idTypeCongeAnnuel);
        d1.setDateDebut(Date.valueOf(debut1));
        d1.setDateFin(Date.valueOf(fin1));

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            congeService.creerDemande(d1, 1, c);
            c.commit();
        }

        // 3. Tenter une 2nde demande chevauchant la première (ex: commence 2 jours avant la fin de d1)
        LocalDate debut2 = fin1.minusDays(2);
        LocalDate fin2 = debut2.plusDays(4);

        DemandeConge d2 = new DemandeConge();
        d2.setIdEmploye(idEmploye);
        d2.setIdTypeConge(idTypeCongeAnnuel);
        d2.setDateDebut(Date.valueOf(debut2));
        d2.setDateFin(Date.valueOf(fin2));

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    congeService.creerDemande(d2, 1, c));
            assertTrue(ex.getMessage().contains("Chevauchement détecté"),
                    "Le moteur de congé doit interdire les périodes qui se chevauchent pour le même employé");
        }
    }

    @Test
    @DisplayName("Adversarial Congé : Rejet pour ancienneté insuffisante (< 12 mois de service pour congé annuel)")
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

        DemandeConge d = new DemandeConge();
        d.setIdEmploye(empRecent.idEmploye);
        d.setIdTypeConge(idTypeCongeAnnuel);
        d.setDateDebut(Date.valueOf(debut));
        d.setDateFin(Date.valueOf(fin));
        d.setDerogationSoldeNegatif(false);

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    congeService.creerDemande(d, 1, c));
            assertTrue(ex.getMessage().contains("ancienneté") || ex.getMessage().contains("service effectif"),
                    "L'exception doit expliciter l'obligation des 12 mois de service de l'Art. 85 du Code du Travail");
        }
    }
}
