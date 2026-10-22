package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import mg.galaxam.sirh.contrat.domain.MatriculeGeneratorEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeContactUrgence;
import mg.galaxam.sirh.contrat.persistence.EmployeEnfant;
import org.postgresql.ds.PGSimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeSatelliteLifecycleTest {
    private final EmployeService service = new EmployeService(null, null, null);
    private Connection connection;

    @BeforeEach
    void ouvrirTransaction() throws Exception {
        String url = System.getenv("GALAXAM_SATELLITE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire requise");
        connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_SATELLITE_TEST_DB_USER"),
                System.getenv("GALAXAM_SATELLITE_TEST_DB_PASSWORD"));
        connection.setAutoCommit(false);
    }

    @AfterEach
    void annulerTransaction() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    private static EmployeRequest.EnfantRequest enfant(String nom) {
        EmployeRequest.EnfantRequest enfant = new EmployeRequest.EnfantRequest();
        enfant.setNom(nom);
        enfant.setPrenom("Test");
        enfant.setSexe("M");
        enfant.setDateNaissance(Date.valueOf("2015-01-01"));
        return enfant;
    }

    private static EmployeRequest.ContactUrgenceRequest contact(String nom) {
        EmployeRequest.ContactUrgenceRequest contact = new EmployeRequest.ContactUrgenceRequest();
        contact.setNom(nom);
        contact.setPrenom("Test");
        contact.setTelephone("+261340000001");
        contact.setLienParente("Autre");
        return contact;
    }

    @Test
    void creationEtModificationCompletesParServiceConserventLesIdentifiants() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(System.getenv("GALAXAM_SATELLITE_TEST_DB_URL"));
        source.setUser(System.getenv("GALAXAM_SATELLITE_TEST_DB_USER"));
        source.setPassword(System.getenv("GALAXAM_SATELLITE_TEST_DB_PASSWORD"));
        EmployeService serviceComplet = new EmployeService(source, new EmployeValidationEngine(), new MatriculeGeneratorEngine());
        EmployeRequest creation = new EmployeRequest();
        creation.setMatricule("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        creation.setNom("Test");
        creation.setPrenom("Satellite");
        creation.setSexe("M");
        creation.setDateNaissance(Date.valueOf("1985-01-01"));
        creation.setLieuNaissance("Antananarivo");
        creation.setCinNumero("123456789012");
        creation.setCinDateDelivrance(Date.valueOf("2005-01-01"));
        creation.setCinLieuDelivrance("Antananarivo");
        creation.setAdresse("Test");
        creation.setTelephone("+261340000001");
        creation.setEnfants(List.of(enfant("A"), enfant("B")));
        creation.setContactsUrgence(List.of(contact("C"), contact("D")));
        int idEmploye = serviceComplet.creerEmploye(creation).getIdEmploye();
        List<EmployeEnfant> enfants = EmployeEnfant.listByEmployeId(idEmploye, connection);
        List<EmployeContactUrgence> contacts = EmployeContactUrgence.listByEmployeId(idEmploye, connection);
        assertEquals(2, enfants.size());
        assertEquals(2, contacts.size());
        int idEnfant = enfants.get(0).getIdEnfant();
        int idContact = contacts.get(0).getIdContact();
        creation.setEnfants(null);
        creation.setContactsUrgence(null);
        serviceComplet.modifierEmploye(idEmploye, creation);
        assertEquals(idEnfant, EmployeEnfant.listByEmployeId(idEmploye, connection).get(0).getIdEnfant());
        assertEquals(idContact, EmployeContactUrgence.listByEmployeId(idEmploye, connection).get(0).getIdContact());
    }

    @Test
    void creePlusieursElementsPuisLesConserveQuandListesAbsentesOuVides() throws Exception {
        EmployeRequest creation = new EmployeRequest();
        creation.setEnfants(List.of(enfant("A"), enfant("B")));
        creation.setContactsUrgence(List.of(contact("C"), contact("D")));
        service.appliquerEnfants(1, creation, connection);
        service.appliquerContactsUrgence(1, creation, connection);
        List<EmployeEnfant> enfants = EmployeEnfant.listByEmployeId(1, connection);
        List<EmployeContactUrgence> contacts = EmployeContactUrgence.listByEmployeId(1, connection);
        assertEquals(2, enfants.size());
        assertEquals(2, contacts.size());
        service.appliquerEnfants(1, new EmployeRequest(), connection);
        service.appliquerContactsUrgence(1, new EmployeRequest(), connection);
        EmployeRequest vide = new EmployeRequest();
        vide.setEnfants(List.of());
        vide.setContactsUrgence(List.of());
        service.appliquerEnfants(1, vide, connection);
        service.appliquerContactsUrgence(1, vide, connection);
        assertEquals(enfants.get(0).getIdEnfant(), EmployeEnfant.listByEmployeId(1, connection).get(0).getIdEnfant());
        assertEquals(2, EmployeContactUrgence.listByEmployeId(1, connection).size());
    }

    @Test
    void modificationAjoutEtSuppressionCiblesConserventLesAutresIdentifiants() throws Exception {
        EmployeRequest creation = new EmployeRequest();
        creation.setEnfants(List.of(enfant("A"), enfant("B")));
        creation.setContactsUrgence(List.of(contact("C"), contact("D")));
        service.appliquerEnfants(1, creation, connection);
        service.appliquerContactsUrgence(1, creation, connection);
        List<EmployeEnfant> enfants = EmployeEnfant.listByEmployeId(1, connection);
        List<EmployeContactUrgence> contacts = EmployeContactUrgence.listByEmployeId(1, connection);
        int enfantModifie = enfants.get(0).getIdEnfant();
        int enfantSupprime = enfants.get(1).getIdEnfant();
        int contactModifie = contacts.get(0).getIdContact();
        int contactSupprime = contacts.get(1).getIdContact();
        EmployeRequest miseAJour = new EmployeRequest();
        EmployeRequest.EnfantRequest e = enfant("A modifié");
        e.setIdEnfant(enfantModifie);
        EmployeRequest.ContactUrgenceRequest c = contact("C modifié");
        c.setIdContact(contactModifie);
        miseAJour.setEnfants(List.of(e, enfant("Nouveau")));
        miseAJour.setContactsUrgence(List.of(c, contact("Nouveau")));
        miseAJour.setEnfantsASupprimer(List.of(enfantSupprime));
        miseAJour.setContactsUrgenceASupprimer(List.of(contactSupprime));
        service.appliquerEnfants(1, miseAJour, connection);
        service.appliquerContactsUrgence(1, miseAJour, connection);
        assertEquals("A modifié", new EmployeEnfant().rechercherParId(enfantModifie, connection).getNom());
        assertEquals("C modifié", new EmployeContactUrgence().rechercherParId(contactModifie, connection).getNom());
        assertNull(new EmployeEnfant().rechercherParId(enfantSupprime, connection));
        assertNull(new EmployeContactUrgence().rechercherParId(contactSupprime, connection));
        assertEquals(2, EmployeEnfant.listByEmployeId(1, connection).size());
        assertEquals(2, EmployeContactUrgence.listByEmployeId(1, connection).size());
    }

    @Test
    void refuseElementsEtrangersEtDemandesContradictoires() throws Exception {
        EmployeRequest creation = new EmployeRequest();
        creation.setEnfants(List.of(enfant("Autre")));
        creation.setContactsUrgence(List.of(contact("Autre")));
        service.appliquerEnfants(2, creation, connection);
        service.appliquerContactsUrgence(2, creation, connection);
        int idEnfant = EmployeEnfant.listByEmployeId(2, connection).get(0).getIdEnfant();
        int idContact = EmployeContactUrgence.listByEmployeId(2, connection).get(0).getIdContact();
        EmployeRequest modification = new EmployeRequest();
        EmployeRequest.EnfantRequest e = enfant("Interdit");
        e.setIdEnfant(idEnfant);
        modification.setEnfants(List.of(e));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerEnfants(1, modification, connection));
        modification.setEnfantsASupprimer(List.of(idEnfant));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerEnfants(1, modification, connection));
        modification.setEnfants(null);
        assertThrows(IllegalArgumentException.class, () -> service.appliquerEnfants(1, modification, connection));
        modification.setContactsUrgenceASupprimer(List.of(idContact));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerContactsUrgence(1, modification, connection));
        EmployeRequest introuvable = new EmployeRequest();
        introuvable.setEnfantsASupprimer(List.of(Integer.MAX_VALUE));
        introuvable.setContactsUrgenceASupprimer(List.of(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerEnfants(1, introuvable, connection));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerContactsUrgence(1, introuvable, connection));
        modification.setContactsUrgenceASupprimer(null);
        EmployeRequest.ContactUrgenceRequest c = contact("Interdit");
        c.setIdContact(idContact);
        modification.setContactsUrgence(List.of(c));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerContactsUrgence(1, modification, connection));
        modification.setContactsUrgenceASupprimer(List.of(idContact));
        assertThrows(IllegalArgumentException.class, () -> service.appliquerContactsUrgence(1, modification, connection));
    }
}
