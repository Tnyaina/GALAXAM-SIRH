package mg.galaxam.sirh.model.employe.engine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmployeValidationEngineTest {

    private EmployeValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EmployeValidationEngine();
    }

    @Test
    @DisplayName("Validation de l'âge minimum légal (16 ans révolus)")
    void testValidationAgeLegal() {
        LocalDate today = LocalDate.now();
        List<String> erreurs = new ArrayList<>();

        // Cas 1 : Salarié de 15 ans -> doit lever une erreur
        LocalDate naissance15ans = today.minusYears(15);
        engine.validerAgeLegal(naissance15ans, today, EmployeValidationEngine.AGE_MINIMUM_LEGAL, erreurs);
        assertFalse(erreurs.isEmpty(), "Un jeune de 15 ans ne peut pas être employé (âge min: 16 ans).");

        // Cas 2 : Salarié de 16 ans -> valide
        erreurs.clear();
        LocalDate naissance16ans = today.minusYears(16);
        engine.validerAgeLegal(naissance16ans, today, EmployeValidationEngine.AGE_MINIMUM_LEGAL, erreurs);
        assertTrue(erreurs.isEmpty(), "Un jeune de 16 ans révolus est légalement admissible.");

        // Cas 3 : Salarié adulte de 25 ans -> valide
        erreurs.clear();
        LocalDate naissance25ans = today.minusYears(25);
        engine.validerAgeLegal(naissance25ans, today, EmployeValidationEngine.AGE_MINIMUM_LEGAL, erreurs);
        assertTrue(erreurs.isEmpty(), "Un adulte de 25 ans est valide.");
    }

    @Test
    @DisplayName("Validation du format CIN Malagasy (12 chiffres) et cohérence date")
    void testValidationCin() {
        List<String> erreurs = new ArrayList<>();
        LocalDate naissance = LocalDate.of(1995, 5, 10);

        // Cas 1 : CIN valide 12 chiffres
        engine.validerCinFormatEtDate("101234567890", LocalDate.of(2015, 6, 1), naissance, erreurs);
        assertTrue(erreurs.isEmpty(), "Un CIN de 12 chiffres délivré à 20 ans doit être accepté.");

        // Cas 2 : CIN avec format erroné (10 chiffres)
        erreurs.clear();
        engine.validerCinFormatEtDate("1012345678", LocalDate.of(2015, 6, 1), naissance, erreurs);
        assertFalse(erreurs.isEmpty(), "Un CIN de moins de 12 chiffres doit être refusé.");

        // Cas 3 : CIN délivré avant l'âge légal (ex: à 10 ans)
        erreurs.clear();
        engine.validerCinFormatEtDate("101234567890", LocalDate.of(2000, 1, 1), naissance, erreurs);
        assertFalse(erreurs.isEmpty(), "Un CIN délivré à un enfant de 5 ans est incohérent.");
    }

    @Test
    @DisplayName("Validation globale d'un EmployeRequest complet conforme")
    void testValidationEmployeConforme() {
        EmployeRequest req = new EmployeRequest();
        req.setNom("RABEMANANJARA");
        req.setPrenom("Hery");
        req.setSexe("M");
        req.setDateNaissance(java.sql.Date.valueOf(LocalDate.of(1990, 8, 15)));
        req.setLieuNaissance("Antananarivo");
        req.setAdresse("Lot IV B 12 Ambohijatovo");
        req.setTelephone("+261 34 00 123 45");
        req.setEmail("hery.rabemananjara@galaxam.mg");
        req.setCinNumero("101234567890");
        req.setCinDateDelivrance(java.sql.Date.valueOf(LocalDate.of(2010, 9, 1)));

        assertDoesNotThrow(() -> engine.validerEmploye(req));
    }

    @Test
    @DisplayName("Rejet d'un EmployeRequest avec champs manquants")
    void testRejetEmployeIncomplet() {
        EmployeRequest req = new EmployeRequest();
        req.setNom(""); // nom vide
        req.setPrenom("Jean");

        Exception ex = assertThrows(IllegalArgumentException.class, () -> engine.validerEmploye(req));
        assertTrue(ex.getMessage().contains("nom"), "L'erreur doit mentionner l'absence de nom.");
    }
}
