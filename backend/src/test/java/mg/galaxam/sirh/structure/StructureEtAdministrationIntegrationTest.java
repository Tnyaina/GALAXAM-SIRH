package mg.galaxam.sirh.structure;

import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.securite.Utilisateur;
import mg.galaxam.sirh.securite.CustomUserDetailsService;
import mg.galaxam.sirh.securite.JwtService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StructureEtAdministrationIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @org.junit.jupiter.api.BeforeEach
    void setUpPasswords() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String hash = passwordEncoder.encode("Pass1234");
            try (PreparedStatement ps = c.prepareStatement("UPDATE utilisateur SET mot_de_passe_hache = ? WHERE identifiant IN ('admin', 'pointage_agent')")) {
                ps.setString(1, hash);
                ps.executeUpdate();
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("STRUC-01 : Validation des filiales et sites réels d'entreprise (GLX, VA, TNR, TMM)")
    void testStructuresEntrepriseReelles() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // Filiales réelles
            try (PreparedStatement ps = c.prepareStatement("SELECT code_filiale, raison_sociale FROM ref_filiale ORDER BY id_filiale")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "La filiale GLX doit exister");
                assertEquals("GLX", rs.getString("code_filiale"));
                assertEquals("Galaxam Logistics", rs.getString("raison_sociale"));

                assertTrue(rs.next(), "La filiale VA doit exister");
                assertEquals("VA", rs.getString("code_filiale"));
                assertEquals("Vina sy Asa ", rs.getString("raison_sociale"));

                assertFalse(rs.next(), "Aucune filiale artificielle ne doit exister");
            }

            // Sites réels de base
            try (PreparedStatement ps = c.prepareStatement("SELECT code_site, libelle FROM ref_site ORDER BY id_site")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "Le site TNR Siège doit exister");
                assertEquals("TNR", rs.getString("code_site"));

                assertTrue(rs.next(), "Le site TMM Tamatave doit exister");
                assertEquals("TMM", rs.getString("code_site"));
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("STRUC-02 : Validation des fonctions clés & distinction Cadre (dispense) vs Non-cadre")
    void testFonctionsEtDispensePointage() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            // DAF : Cadre autonome avec dispense de pointage = true
            try (PreparedStatement ps = c.prepareStatement("SELECT dispense_pointage FROM ref_fonction WHERE code_fonction = 'DAF'")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next());
                assertTrue(rs.getBoolean("dispense_pointage"), "Le DAF doit bénéficier de la dispense légale de pointage");
            }

            // OPE Manager / Aide-Comptable : Soumis au pointage
            try (PreparedStatement ps = c.prepareStatement("SELECT dispense_pointage FROM ref_fonction WHERE code_fonction IN ('OPE_MGR', 'AIDE_CPT')")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    assertFalse(rs.getBoolean("dispense_pointage"), "Les fonctions d'exécution ne bénéficient pas de la dispense de pointage");
                }
            }
        }
    }

    @Test
    @Order(3)
    @DisplayName("STRUC-03 : Paramétrages légaux & seuils de paie dans parametre_global et paie_config_legale")
    void testParametresGlobauxLegaux() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            Map<String, String> params = ParametreGlobal.loadActiveMap(c);

            assertNull(params.get("SEUIL_HEURES_SEMAINE"), "SEUIL_HEURES_SEMAINE ne doit plus être dans parametre_global (migré dans paie_config_legale)");
            assertNull(params.get("SEUIL_HS_TRANCHE_1"), "SEUIL_HS_TRANCHE_1 ne doit plus être dans parametre_global (migré dans paie_config_legale)");
            assertEquals("8", params.get("SEUIL_HEURES_JOUR"), "Base organisationnelle journalière = 8 heures");
            assertEquals("22:00", params.get("SEUIL_NUIT_DEBUT"), "Début travail de nuit = 22h00");
            assertEquals("2000", params.get("MONTANT_REPAS_MIDI"), "Indemnité repas midi = 2000 Ar");
            assertEquals("1000", params.get("REDUCTION_DEPLACT_REPAS_MIDI"), "Déduction transport Tamatave si repas sur site = 1000 Ar");

            mg.galaxam.sirh.model.paieconfig.PaieConfigLegale configLegale =
                mg.galaxam.sirh.model.paieconfig.PaieConfigLegale.findActiveAt(c, LocalDate.of(2026, 1, 1));
            assertNotNull(configLegale, "Config légale 2026 doit exister");
            assertEquals(0, new BigDecimal("40.00").compareTo(configLegale.getDureeHebdoLegale()), "Base légale hebdomadaire = 40h");
            assertEquals(0, new BigDecimal("8.00").compareTo(configLegale.getSeuilHsTranche1()), "Tranche 1 HS (+30%) = 8h max");
            assertEquals(0, new BigDecimal("2.50").compareTo(configLegale.getAcquisitionCongeMensuelJours()), "Acquisition congé mensuelle = 2.5j");
            assertEquals(Integer.valueOf(30), configLegale.getDiviseurValorisationCongeJours(), "Diviseur valorisation congé = 30j");
            assertEquals(0, new BigDecimal("3000.00").compareTo(configLegale.getMinimumPerceptionIrsa()), "Perception minimale légale IRSA = 3000 Ar");
            assertEquals(0, new BigDecimal("2000.00").compareTo(configLegale.getDeductionChargeFamille()), "Déduction fiscale IRSA par enfant = 2000 Ar");
        }
    }

    @Test
    @Order(4)
    @DisplayName("STRUC-04 : Calendrier des jours fériés légaux (Assomption 15 Août 2026)")
    void testCalendrierFerieAssomption() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT f.libelle, f.est_chome FROM ref_calendrier_ferie f " +
                    "JOIN site_calendrier_ferie scf ON f.id_ferie = scf.id_ferie " +
                    "WHERE f.date_ferie = '2026-08-15' AND scf.id_site = 1")) {
                ResultSet rs = ps.executeQuery();
                assertTrue(rs.next(), "Le 15 Août (Assomption) doit être configuré pour le Site 1 (TNR)");
                assertEquals("Assomption", rs.getString("libelle"));
                assertTrue(rs.getBoolean("est_chome"));
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("STRUC-05 : Authentification Administrateur (daf@galaxam.com / admin) & Génération JWT")
    void testAuthentificationAdminJwt() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            String adminUsername = "daf@galaxam.com";
            Utilisateur u = Utilisateur.rechercherParIdentifiant(adminUsername, c);
            if (u == null) {
                adminUsername = "admin";
                u = Utilisateur.rechercherParIdentifiant(adminUsername, c);
            }
            assertNotNull(u, "L'utilisateur administrateur (" + adminUsername + ") doit exister");

            UserDetails userDetails = customUserDetailsService.loadUserByUsername(adminUsername);
            assertNotNull(userDetails);
            String token = jwtService.generateAccessToken(userDetails);
            assertNotNull(token);
            assertTrue(token.startsWith("eyJ"), "Le token JWT doit être valide et commencer par eyJ");
        }
    }
}
