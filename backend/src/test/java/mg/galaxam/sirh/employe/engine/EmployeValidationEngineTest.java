package mg.galaxam.sirh.employe.engine;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mg.galaxam.sirh.api.dto.employe.DesactivationRequest;
import mg.galaxam.sirh.api.dto.employe.EmployeRequest;
import mg.galaxam.sirh.model.employe.engine.EmployeValidationEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmployeValidationEngine — Tests unitaires de validation des dossiers salariés")
class EmployeValidationEngineTest {

    private EmployeValidationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new EmployeValidationEngine();
    }

    private EmployeRequest creerEmployeValide() {
        EmployeRequest req = new EmployeRequest();
        req.setNom("RAKOTO");
        req.setPrenom("Jean");
        req.setSexe("M");
        req.setDateNaissance(Date.valueOf(LocalDate.now().minusYears(25)));
        req.setCinNumero("101234567890");
        req.setCinDateDelivrance(Date.valueOf(LocalDate.now().minusYears(5)));
        req.setAdresse("Lot IV B 12 Ankadifotsy");
        req.setTelephone("+261 34 12 345 67");
        req.setEmail("jean.rakoto@galaxam.mg");
        return req;
    }

    @Nested
    @DisplayName("validerEmploye — Tests globaux")
    class ValiderEmployeGlobal {

        @Test
        @DisplayName("Employé valide : aucune exception")
        void employeValide() {
            EmployeRequest req = creerEmployeValide();
            assertDoesNotThrow(() -> engine.validerEmploye(req));
        }

        @Test
        @DisplayName("Requête nulle : exception")
        void requeteNulle() {
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.validerEmploye(null)
            );
            assertTrue(ex.getMessage().contains("ne peut pas être nulle"));
        }

        @Test
        @DisplayName("Nom ou prénom manquant : exception")
        void nomOuPrenomManquant() {
            EmployeRequest req = creerEmployeValide();
            req.setNom("   ");
            req.setPrenom(null);

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.validerEmploye(req)
            );
            assertTrue(ex.getMessage().contains("nom de famille est obligatoire"));
            assertTrue(ex.getMessage().contains("prénom est obligatoire"));
        }

        @Test
        @DisplayName("Sexe manquant : exception")
        void sexeManquant() {
            EmployeRequest req = creerEmployeValide();
            req.setSexe(" ");

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.validerEmploye(req)
            );
            assertTrue(ex.getMessage().contains("sexe est obligatoire"));
        }
    }

    @Nested
    @DisplayName("validerAgeLegal — Contrôle de l'âge minimum légal (16 ans)")
    class AgeLegal {

        @Test
        @DisplayName("Moins de 16 ans révolus : refus légal")
        void mineurRefuse() {
            EmployeRequest req = creerEmployeValide();
            req.setDateNaissance(Date.valueOf(LocalDate.now().minusYears(15).minusMonths(11)));

            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.validerEmploye(req)
            );
            assertTrue(ex.getMessage().contains("au minimum 16 ans révolus"));
        }

        @Test
        @DisplayName("Exactement 16 ans révolus aujourd'hui : accepté")
        void exactementSeizeAns() {
            List<String> erreurs = new ArrayList<>();
            LocalDate dob = LocalDate.now().minusYears(16);
            engine.validerAgeLegal(dob, LocalDate.now(), 16, erreurs);
            assertTrue(erreurs.isEmpty());
        }

        @Test
        @DisplayName("Date de naissance dans le futur : refus")
        void dateNaissanceFutur() {
            List<String> erreurs = new ArrayList<>();
            LocalDate dob = LocalDate.now().plusDays(1);
            engine.validerAgeLegal(dob, LocalDate.now(), 16, erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("postérieure à aujourd'hui"));
        }
    }

    @Nested
    @DisplayName("validerCinFormatEtDate — Format et cohérence du CIN")
    class CinValidation {

        @Test
        @DisplayName("CIN invalide (11 chiffres ou lettres) : rejeté")
        void cinFormatInvalide() {
            List<String> erreurs = new ArrayList<>();
            engine.validerCinFormatEtDate("12345678901", null, LocalDate.of(1990, 1, 1), erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("exactement 12 chiffres"));

            erreurs.clear();
            engine.validerCinFormatEtDate("12345678901A", null, LocalDate.of(1990, 1, 1), erreurs);
            assertFalse(erreurs.isEmpty());
        }

        @Test
        @DisplayName("CIN délivré avant les 16 ans du salarié : rejeté")
        void cinDelivreAvantAgeLegal() {
            List<String> erreurs = new ArrayList<>();
            LocalDate dob = LocalDate.of(2000, 1, 1);
            LocalDate delivrance = LocalDate.of(2014, 5, 1); // 14 ans
            engine.validerCinFormatEtDate("101234567890", delivrance, dob, erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("incompatible avec la date de naissance"));
        }

        @Test
        @DisplayName("CIN délivré dans le futur : rejeté")
        void cinDateFuture() {
            List<String> erreurs = new ArrayList<>();
            LocalDate dob = LocalDate.of(1995, 1, 1);
            LocalDate delivrance = LocalDate.now().plusDays(2);
            engine.validerCinFormatEtDate("101234567890", delivrance, dob, erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("dans le futur"));
        }
    }

    @Nested
    @DisplayName("validerCompteBancaire — Cohérence RIB et Banque")
    class CompteBancaire {

        @Test
        @DisplayName("Numéro de compte fourni sans banque : rejeté")
        void ribSansBanque() {
            List<String> erreurs = new ArrayList<>();
            EmployeRequest.CompteBanqueRequest cb = new EmployeRequest.CompteBanqueRequest();
            cb.setNumCompteRib("00001-00002-12345678901-45");
            cb.setIdBanque(null);

            engine.validerCompteBancaire(cb, erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("banque doit être sélectionnée"));
        }

        @Test
        @DisplayName("Banque sélectionnée sans RIB : rejeté")
        void banqueSansRib() {
            List<String> erreurs = new ArrayList<>();
            EmployeRequest.CompteBanqueRequest cb = new EmployeRequest.CompteBanqueRequest();
            cb.setIdBanque(2);
            cb.setNumCompteRib("   ");

            engine.validerCompteBancaire(cb, erreurs);
            assertFalse(erreurs.isEmpty());
            assertTrue(erreurs.get(0).contains("numéro de compte RIB est obligatoire"));
        }
    }

    @Nested
    @DisplayName("validerDesactivation — Départ / Désactivation salarié")
    class Desactivation {

        @Test
        @DisplayName("Motif renseigné : valide")
        void motifRenseigne() {
            DesactivationRequest req = new DesactivationRequest();
            req.setMotif("Démission volontaire avec préavis");
            assertDoesNotThrow(() -> engine.validerDesactivation(req));
        }

        @Test
        @DisplayName("Motif vide ou null : rejeté")
        void motifVide() {
            DesactivationRequest req = new DesactivationRequest();
            req.setMotif("   ");
            assertThrows(IllegalArgumentException.class, () -> engine.validerDesactivation(req));

            assertThrows(IllegalArgumentException.class, () -> engine.validerDesactivation(null));
        }
    }
}
