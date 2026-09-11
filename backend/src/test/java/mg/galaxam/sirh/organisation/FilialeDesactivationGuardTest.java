package mg.galaxam.sirh.organisation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.controller.organisation.FilialeController;
import mg.galaxam.sirh.model.organisation.RefFiliale;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("FilialeDesactivationGuardTest — Validation du garde-fou de désactivation d'une filiale")
class FilialeDesactivationGuardTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FilialeController filialeController;

    @BeforeEach
    void setUpSecurity() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pass",
                        List.of(new SimpleGrantedAuthority("ORGANISATION_WRITE"), new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }

    @Test
    @DisplayName("Garde-fou : Refus de désactiver une filiale ayant des contrats ou sites actifs")
    void testRefusDesactivationFilialeAvecDependances() throws Exception {
        // La filiale 1 (Galaxam SARL) a des sites et des contrats actifs dans le jeu de test
        ResponseEntity<?> response = filialeController.deactivateFiliale(1);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "La désactivation d'une filiale avec dépendances doit être rejetée en HTTP 400");
        String message = (String) response.getBody();
        assertNotNull(message);
        assertTrue(message.contains("Impossible de désactiver la filiale"), "Le message doit expliquer le blocage");
    }

    @Test
    @DisplayName("Succès : Désactivation autorisée pour une filiale sans site actif ni contrat")
    void testDesactivationFilialeSansDependance() throws Exception {
        int idFilialeTest;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            RefFiliale f = new RefFiliale();
            f.setCodeFiliale("FIL_ISOL");
            f.setRaisonSociale("Filiale Isolée Test");
            f.setNif("1234567890");
            f.setStat("123456789012345");
            f.setEstActif(true);
            f.save(c);
            idFilialeTest = f.getIdFiliale();
            c.commit();
        }

        try {
            ResponseEntity<?> response = filialeController.deactivateFiliale(idFilialeTest);
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                    "La désactivation d'une filiale orpheline doit réussir en HTTP 200");
            RefFiliale desactivee = (RefFiliale) response.getBody();
            assertNotNull(desactivee);
            assertFalse(desactivee.isEstActif(), "La filiale doit être marquée inactive");

            // Réactivation pour vérifier le cycle complet
            ResponseEntity<?> responseActive = filialeController.activateFiliale(idFilialeTest);
            assertEquals(HttpStatus.OK, responseActive.getStatusCode());
            RefFiliale activee = (RefFiliale) responseActive.getBody();
            assertTrue(activee.isEstActif(), "La filiale doit être réactivée");
        } finally {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_filiale WHERE id_filiale = ?")) {
                    ps.setInt(1, idFilialeTest);
                    ps.executeUpdate();
                }
                c.commit();
            }
        }
    }
}
