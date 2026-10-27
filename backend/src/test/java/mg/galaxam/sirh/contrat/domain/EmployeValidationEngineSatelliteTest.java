package mg.galaxam.sirh.contrat.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Date;
import java.util.Arrays;
import java.util.List;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import org.junit.jupiter.api.Test;

class EmployeValidationEngineSatelliteTest {
    private final EmployeValidationEngine engine = new EmployeValidationEngine();

    @Test
    void refuseUnEnfantOuContactNullAvecUnMessageDeValidation() {
        EmployeRequest enfantNull = demandeValide();
        enfantNull.setEnfants(Arrays.asList((EmployeRequest.EnfantRequest) null));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> engine.validerEmploye(enfantNull))
                .getMessage().contains("L'enfant n°1 n'est pas renseigné."));

        EmployeRequest contactNull = demandeValide();
        contactNull.setContactsUrgence(Arrays.asList((EmployeRequest.ContactUrgenceRequest) null));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> engine.validerEmploye(contactNull))
                .getMessage().contains("Le contact d'urgence n°1 n'est pas renseigné."));
    }

    @Test
    void refuseLesChampsObligatoiresManquantsPourUnEnfant() {
        EmployeRequest.EnfantRequest enfant = enfantValide();
        enfant.setNom(" ");
        enfant.setPrenom(null);
        enfant.setDateNaissance((Date) null);
        EmployeRequest demande = demandeValide();
        demande.setEnfants(List.of(enfant));

        String message = assertThrows(IllegalArgumentException.class, () -> engine.validerEmploye(demande))
                .getMessage();
        assertTrue(message.contains("nom de l'enfant n°1 est obligatoire"));
        assertTrue(message.contains("prénom de l'enfant n°1 est obligatoire"));
        assertTrue(message.contains("date de naissance de l'enfant n°1 est obligatoire"));
    }

    @Test
    void refuseLesChampsObligatoiresManquantsPourUnContact() {
        EmployeRequest.ContactUrgenceRequest contact = contactValide();
        contact.setNom(null);
        contact.setPrenom(" ");
        contact.setTelephone(null);
        contact.setAdresse("Adresse partielle");
        EmployeRequest demande = demandeValide();
        demande.setContactsUrgence(List.of(contact));

        String message = assertThrows(IllegalArgumentException.class, () -> engine.validerEmploye(demande))
                .getMessage();
        assertTrue(message.contains("nom du contact d'urgence n°1 est obligatoire"));
        assertTrue(message.contains("prénom du contact d'urgence n°1 est obligatoire"));
        assertTrue(message.contains("téléphone du contact d'urgence n°1 est obligatoire"));
    }

    @Test
    void acceptePlusieursEnfantsEtContactsAvecLesChampsFacultatifsAbsents() {
        EmployeRequest demande = demandeValide();
        demande.setEnfants(List.of(enfantValide(), enfantValide()));
        demande.setContactsUrgence(List.of(contactValide(), contactValide()));
        assertDoesNotThrow(() -> engine.validerEmploye(demande));
    }

    @Test
    void unContactEntierementVideResteFacultatifCommeLeFormulaireExistant() {
        EmployeRequest demande = demandeValide();
        EmployeRequest.ContactUrgenceRequest ligneVide = new EmployeRequest.ContactUrgenceRequest();
        ligneVide.setLienParente("Autre");
        demande.setContactsUrgence(List.of(ligneVide));
        assertDoesNotThrow(() -> engine.validerEmploye(demande));
    }

    private static EmployeRequest demandeValide() {
        EmployeRequest demande = new EmployeRequest();
        demande.setNom("Employe");
        demande.setPrenom("Test");
        demande.setSexe("M");
        demande.setDateNaissance(Date.valueOf("1985-01-01"));
        demande.setCinNumero("123456789012");
        demande.setAdresse("Adresse");
        demande.setTelephone("+261340000001");
        return demande;
    }

    private static EmployeRequest.EnfantRequest enfantValide() {
        EmployeRequest.EnfantRequest enfant = new EmployeRequest.EnfantRequest();
        enfant.setNom("Enfant");
        enfant.setPrenom("Test");
        enfant.setDateNaissance(Date.valueOf("2015-01-01"));
        return enfant;
    }

    private static EmployeRequest.ContactUrgenceRequest contactValide() {
        EmployeRequest.ContactUrgenceRequest contact = new EmployeRequest.ContactUrgenceRequest();
        contact.setNom("Contact");
        contact.setPrenom("Test");
        contact.setTelephone("+261340000001");
        return contact;
    }
}
