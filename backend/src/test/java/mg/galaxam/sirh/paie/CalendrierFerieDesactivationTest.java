package mg.galaxam.sirh.paie;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Set;
import javax.sql.DataSource;
import mg.galaxam.sirh.api.controller.paieconfig.CalendrierFerieController;
import mg.galaxam.sirh.model.paieconfig.RefCalendrierFerie;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
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
@DisplayName("CalendrierFerieDesactivationTest — Validation de la désactivation du calendrier férié")
class CalendrierFerieDesactivationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CalendrierFerieController controller;

    @Autowired
    private CalendrierOuvrableEngine calendrierEngine;

    @BeforeEach
    void setUpSecurity() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "pass",
                        List.of(new SimpleGrantedAuthority("PAIE_WRITE"), new SimpleGrantedAuthority("ROLE_ADMIN")))
        );
    }

    @Test
    @DisplayName("Désactivation/Réactivation d'un jour férié et exclusion dans CalendrierOuvrableEngine")
    void testDesactivationCalendrierFerie() throws Exception {
        LocalDate testDate = LocalDate.of(2035, 7, 14);
        int idFerie;

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            RefCalendrierFerie f = new RefCalendrierFerie();
            f.setDateFerie(Date.valueOf(testDate));
            f.setLibelle("Fête Test Spéciale 2035");
            f.setEstChome(true);
            f.setEstNational(true);
            f.setEstActif(true);
            f.save(c);
            idFerie = f.getIdFerie();
            c.commit();
        }

        try (Connection c = dataSource.getConnection()) {
            // 1. Initialement actif : le moteur doit trouver la date
            Set<LocalDate> feriesActifs = calendrierEngine.getDatesFeriesEtChomees(testDate.minusDays(1), testDate.plusDays(1), null, c);
            assertTrue(feriesActifs.contains(testDate), "Le jour férié actif doit être inclus dans les dates chômées");

            // 2. Désactiver le jour férié
            ResponseEntity<?> respDesact = controller.deactivateCalendrierFerie(idFerie);
            assertEquals(HttpStatus.OK, respDesact.getStatusCode());
            RefCalendrierFerie desact = (RefCalendrierFerie) respDesact.getBody();
            assertNotNull(desact);
            assertFalse(desact.isEstActif(), "Le férié doit être inactif");

            // 3. Après désactivation : le moteur NE DOIT PLUS trouver la date
            Set<LocalDate> feriesApresDesact = calendrierEngine.getDatesFeriesEtChomees(testDate.minusDays(1), testDate.plusDays(1), null, c);
            assertFalse(feriesApresDesact.contains(testDate), "Le jour férié désactivé NE DOIT PAS être compté comme chômé");

            // 4. Réactiver le jour férié
            ResponseEntity<?> respAct = controller.activateCalendrierFerie(idFerie);
            assertEquals(HttpStatus.OK, respAct.getStatusCode());
            RefCalendrierFerie act = (RefCalendrierFerie) respAct.getBody();
            assertNotNull(act);
            assertTrue(act.isEstActif(), "Le férié doit être réactivé");

            // 5. De nouveau actif
            Set<LocalDate> feriesReactives = calendrierEngine.getDatesFeriesEtChomees(testDate.minusDays(1), testDate.plusDays(1), null, c);
            assertTrue(feriesReactives.contains(testDate), "Le jour férié réactivé doit de nouveau être inclus");
        } finally {
            try (Connection c = dataSource.getConnection()) {
                c.setAutoCommit(false);
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM ref_calendrier_ferie WHERE id_ferie = ?")) {
                    ps.setInt(1, idFerie);
                    ps.executeUpdate();
                }
                c.commit();
            }
        }
    }
}
