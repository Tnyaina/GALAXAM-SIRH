package mg.galaxam.sirh.unit.employe;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Unit Tests - EmployeValidationEngine")
class EmployeValidationEngineTest {

    private EmployeValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EmployeValidationEngine();
    }

    private EmployeRequest createValidRequest() {
        EmployeRequest req = new EmployeRequest();
        req.setNom("RAKOTO");
        req.setPrenom("Jean");
        req.setSexe("M");
        req.setDateNaissance(Date.valueOf("1995-05-15"));
        req.setCinNumero("101211005678");
        req.setCinDateDelivrance(Date.valueOf("2014-06-10"));
        req.setAdresse("Lot IV B 20 Antananarivo");
        req.setTelephone("0341234567");
        req.setEmail("jean.rakoto@galaxam.com");
        return req;
    }

    @Test
    @DisplayName("validerEmploye : Requête valide acceptée sans exception")
    void testEmployeValide() {
        EmployeRequest req = createValidRequest();
        assertThatCode(() -> engine.validerEmploye(req)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validerEmploye : Nom, prénom et sexe obligatoires")
    void testChampsIdentiteObligatoires() {
        EmployeRequest req = createValidRequest();
        req.setNom(null);
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le nom de famille est obligatoire");

        req.setNom("RAKOTO");
        req.setPrenom("   ");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le prénom est obligatoire");

        req.setPrenom("Jean");
        req.setSexe("");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le sexe est obligatoire");
    }

    @Test
    @DisplayName("validerEmploye : Âge minimum légal de 16 ans révolus (Loi n° 2003-044)")
    void testAgeMinimumLegal() {
        EmployeRequest req = createValidRequest();
        // Né il y a 14 ans
        LocalDate tropJeune = LocalDate.now().minusYears(14);
        req.setDateNaissance(Date.valueOf(tropJeune));
        req.setCinDateDelivrance(Date.valueOf(LocalDate.now()));

        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("au minimum 16 ans révolus");
    }

    @Test
    @DisplayName("validerEmploye : Contrôle strict du format CIN (12 chiffres)")
    void testFormatCin() {
        EmployeRequest req = createValidRequest();

        // 11 chiffres au lieu de 12
        req.setCinNumero("10121100567");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doit comporter exactement 12 chiffres");

        // Lettres dans le numéro de CIN
        req.setCinNumero("10121100567A");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("doit comporter exactement 12 chiffres");
    }

    @Test
    @DisplayName("validerEmploye : Format téléphone et email invalides")
    void testFormatsCoordonnees() {
        EmployeRequest req = createValidRequest();

        req.setTelephone("abcd");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Le format du numéro de téléphone est invalide");

        req.setTelephone("0341234567");
        req.setEmail("email-invalide");
        assertThatThrownBy(() -> engine.validerEmploye(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("L'adresse email saisie est invalide");
    }
}
