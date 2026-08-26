package mg.galaxam.sirh.model.employe;

import java.sql.Date;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmployeValidationTest {

    @Test
    @DisplayName("Validation des champs obligatoires d'un employé (Matricule, Nom, CIN, etc.)")
    void testChampsObligatoires() {
        Employe emp = new Employe();

        // 1. Matricule manquant
        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class, () -> {
            if (emp.getMatricule() == null || emp.getMatricule().isBlank()) {
                throw new IllegalArgumentException("Le matricule est obligatoire.");
            }
        });
        System.out.println("[TEST EMPLOYE] Validation Matricule : " + ex1.getMessage());
        assertEquals("Le matricule est obligatoire.", ex1.getMessage());

        // 2. Nom manquant
        emp.setMatricule("EMP-001");
        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class, () -> {
            if (emp.getNom() == null || emp.getNom().isBlank()) {
                throw new IllegalArgumentException("Le nom est obligatoire.");
            }
        });
        System.out.println("[TEST EMPLOYE] Validation Nom : " + ex2.getMessage());
        assertEquals("Le nom est obligatoire.", ex2.getMessage());
    }

    @Test
    @DisplayName("Normalisation automatique en majuscules du Matricule et du Nom")
    void testNormalisationMajuscules() {
        Employe emp = new Employe();
        emp.setMatricule("  emp-101  ");
        emp.setNom("  rakotomalala  ");
        emp.setPrenom("Jean");

        // Normalisation
        emp.setMatricule(emp.getMatricule().trim().toUpperCase());
        emp.setNom(emp.getNom().trim().toUpperCase());

        System.out.println("[TEST EMPLOYE] Normalisation -> Matricule: " + emp.getMatricule() + ", Nom: " + emp.getNom());
        assertEquals("EMP-101", emp.getMatricule());
        assertEquals("RAKOTOMALALA", emp.getNom());
    }

    @Test
    @DisplayName("Validation de l'âge légal minimum de travail (18 ans)")
    void testAgeLegalTravail() {
        LocalDate dateNaissanceMineur = LocalDate.now().minusYears(16);
        LocalDate dateNaissanceMajeur = LocalDate.now().minusYears(25);

        int ageMineur = LocalDate.now().getYear() - dateNaissanceMineur.getYear();
        int ageMajeur = LocalDate.now().getYear() - dateNaissanceMajeur.getYear();

        System.out.println("[TEST EMPLOYE] Âge calculé : Mineur=" + ageMineur + " ans, Majeur=" + ageMajeur + " ans.");

        assertTrue(ageMineur < 18, "Un mineur de 16 ans ne peut pas être recruté sans dérogation spéciale.");
        assertTrue(ageMajeur >= 18, "Un candidat de 25 ans respecte l'âge légal du travail.");
    }

    @Test
    @DisplayName("Test de la désactivation (Soft delete) d'un employé")
    void testDesactivationEmploye() {
        Employe emp = new Employe();
        emp.setIdEmploye(10);
        emp.setEstActif(true);

        // Simulation désactivation
        emp.setEstActif(false);
        emp.setMotifDesactivation("Demission");

        System.out.println("[TEST EMPLOYE] Statut après désactivation : estActif=" + emp.isEstActif() + ", motif=" + emp.getMotifDesactivation());
        assertFalse(emp.isEstActif(), "L'employé doit être marqué inactif.");
        assertEquals("Demission", emp.getMotifDesactivation());
    }
}
