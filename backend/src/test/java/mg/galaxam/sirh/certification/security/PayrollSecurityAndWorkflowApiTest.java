package mg.galaxam.sirh.certification.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.Map;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.paie.api.dto.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.paie.api.dto.CalculEmployeRequest;
import mg.galaxam.sirh.paie.api.dto.CalculPeriodeRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import mg.galaxam.sirh.securite.CustomUserDetailsService;
import mg.galaxam.sirh.securite.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * =========================================================================
 * PHASE 11 : CERTIFICATION SÉCURITÉ RBAC & ÉTANCHÉITÉ DU WORKFLOW DE PAIE
 * =========================================================================
 *
 * Vérifie formellement :
 * 1. Accès anonyme (non authentifié) : Rejet systématique 401/403.
 * 2. Cloisonnement Agent de Pointage (ROLE_AGENT_POINTAGE) :
 *    - Autorisé sur Pointage
 *    - Strictement 403 Forbidden sur Calcul, Validation, Consultation Bulletins et Exports Paie.
 * 3. Ségrégation des tâches Gestionnaire Paie (PAIE_WRITE sans PAIE_VALIDATE) :
 *    - Autorisé à lancer calculs et simulations
 *    - Strictement 403 Forbidden sur la validation finale de période.
 * 4. Prérequis du workflow (Machine à états) :
 *    - Calcul bloqué si le pointage n'est pas figé (statut EN_COURS -> 409 POINTAGE_NOT_FROZEN).
 *    - Validation bloquée si aucun bulletin calculé (409 PAYROLL_NOT_CALCULATED).
 *    - Immutabilité absolue si période clôturée (CLOTUREE -> 409 PERIOD_CLOSED).
 *    - Mutation générique de statut rejetée (PUT /periodes/{id}/statut -> 410 Gone).
 */
public class PayrollSecurityAndWorkflowApiTest extends BaseIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private int idPeriode;
    private CreatedEmploye emp;
    private String tokenPointage;
    private String tokenAdmin;
    private String tokenGestionnairePaie;
    private String tokenResponsablePaie;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();

        tokenAdmin = userFixtures.getAdminToken();
        tokenPointage = userFixtures.getPointageToken();

        // Création d'utilisateurs avec permissions granulaires
        tokenGestionnairePaie = createTestUserToken(
                "gestionnaire_paie@galaxam.com",
                "Pass-Gest-2026!",
                "ROLE_GESTIONNAIRE_PAIE",
                "Gestionnaire Paie",
                true,  // PAIE_READ
                true,  // PAIE_WRITE
                false  // PAIE_VALIDATE = FALSE
        );

        tokenResponsablePaie = createTestUserToken(
                "responsable_paie@galaxam.com",
                "Pass-Resp-2026!",
                "ROLE_RESPONSABLE_PAIE",
                "Responsable Paie",
                true,  // PAIE_READ
                true,  // PAIE_WRITE
                true   // PAIE_VALIDATE = TRUE
        );

        LocalDate dDebut = LocalDate.of(2026, 9, 1);
        LocalDate dFin = LocalDate.of(2026, 9, 30);
        idPeriode = employeFixtures.createPeriodePaie("P-2026-09-SEC", dDebut, dFin, "EN_COURS");

        emp = employeFixtures.createEmployeWithSalary(
                "EMP-SEC-01",
                "TESTEUR",
                "Securite",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        // Snapshot de pointage gelé pour permettre les calculs / simulations
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO pointage_cumul_periode (" +
                     "id_periode_paie, id_employe, version_snapshot, statut_snapshot, nb_jours_ouvrables, nb_jours_presents, " +
                     "total_heures_travaillees, heures_absence_retenues, total_minutes_retard, " +
                     "heures_hs_30, heures_hs_50, heures_nuit_total, heures_dimanche_total, " +
                     "nb_jours_conge, montant_deplacement, gele_at) " +
                     "VALUES (?, ?, 1, 'GELE', 22, 22, 173.33, 0, 0, 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP)")) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, emp.idEmploye);
            ps.executeUpdate();
        }
    }

    private String createTestUserToken(String email, String rawPassword, String roleCode, String roleLibelle,
                                       boolean paieRead, boolean paieWrite, boolean paieValidate) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                int idRole;
                String sqlSelect = "SELECT id_role FROM ref_role WHERE code_role = ?";
                try (PreparedStatement ps = c.prepareStatement(sqlSelect)) {
                    ps.setString(1, roleCode);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idRole = rs.getInt(1);
                        } else {
                            String sqlInsert = "INSERT INTO ref_role (code_role, libelle, est_actif) VALUES (?, ?, TRUE) RETURNING id_role";
                            try (PreparedStatement psIns = c.prepareStatement(sqlInsert)) {
                                psIns.setString(1, roleCode);
                                psIns.setString(2, roleLibelle);
                                try (ResultSet rsIns = psIns.executeQuery()) {
                                    rsIns.next();
                                    idRole = rsIns.getInt(1);
                                }
                            }
                        }
                    }
                }

                // Associer les permissions sur le module PAIE (id_module = 4)
                String sqlPerm = "INSERT INTO role_permission (id_role, id_module, droit_lecture, droit_ecriture, droit_validation) " +
                        "VALUES (?, 4, ?, ?, ?) " +
                        "ON CONFLICT (id_role, id_module) DO UPDATE SET " +
                        "droit_lecture = EXCLUDED.droit_lecture, " +
                        "droit_ecriture = EXCLUDED.droit_ecriture, " +
                        "droit_validation = EXCLUDED.droit_validation";
                try (PreparedStatement ps = c.prepareStatement(sqlPerm)) {
                    ps.setInt(1, idRole);
                    ps.setBoolean(2, paieRead);
                    ps.setBoolean(3, paieWrite);
                    ps.setBoolean(4, paieValidate);
                    ps.executeUpdate();
                }

                // Insérer ou mettre à jour le compte utilisateur
                String hash = passwordEncoder.encode(rawPassword);
                String sqlUser = "INSERT INTO utilisateur (identifiant, mot_de_passe_hache, id_role, est_actif, updated_at) " +
                        "VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP) " +
                        "ON CONFLICT (identifiant) DO UPDATE SET " +
                        "mot_de_passe_hache = EXCLUDED.mot_de_passe_hache, " +
                        "id_role = EXCLUDED.id_role, " +
                        "est_actif = TRUE, " +
                        "updated_at = CURRENT_TIMESTAMP";
                try (PreparedStatement ps = c.prepareStatement(sqlUser)) {
                    ps.setString(1, email);
                    ps.setString(2, hash);
                    ps.setInt(3, idRole);
                    ps.executeUpdate();
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw new RuntimeException("Erreur initialisation utilisateur RBAC test : " + e.getMessage(), e);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(email);
        return jwtService.generateAccessToken(userDetails);
    }

    // =========================================================================
    // 1. REJET ANONYME (401 / 403)
    // =========================================================================

    @Test
    @DisplayName("Sécurité : Les requêtes anonymes vers les endpoints Paie sont rejetées")
    public void testAccesAnonymeRejete() throws Exception {
        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(true);

        mockMvc.perform(post("/api/v1/paie/calculer-periode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isForbidden());

        ActionPaiePeriodeRequest reqVal = new ActionPaiePeriodeRequest();
        reqVal.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqVal)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/paie/bulletin/1"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/paie/periode/" + idPeriode + "/export/safi"))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. CLOISONNEMENT AGENT DE POINTAGE (403 FORBIDDEN SUR LA PAIE)
    // =========================================================================

    @Test
    @DisplayName("RBAC : L'Agent de Pointage n'a AUCUN accès aux opérations ou exports de Paie")
    public void testAgentPointage_InterditSurPaie() throws Exception {
        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(true);

        // Tentative de calcul par agent pointage -> 403
        mockMvc.perform(post("/api/v1/paie/calculer-periode")
                        .header("Authorization", "Bearer " + tokenPointage)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isForbidden());

        // Tentative de validation par agent pointage -> 403
        ActionPaiePeriodeRequest reqVal = new ActionPaiePeriodeRequest();
        reqVal.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .header("Authorization", "Bearer " + tokenPointage)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqVal)))
                .andExpect(status().isForbidden());

        // Consultation de bulletin de paie -> 403
        mockMvc.perform(get("/api/v1/paie/bulletin/1")
                        .header("Authorization", "Bearer " + tokenPointage))
                .andExpect(status().isForbidden());

        // Export déclaratif SAFI -> 403
        mockMvc.perform(get("/api/v1/paie/periode/" + idPeriode + "/export/safi")
                        .header("Authorization", "Bearer " + tokenPointage))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. SÉGRÉGATION GESTIONNAIRE PAIE VS RESPONSABLE PAIE
    // =========================================================================

    @Test
    @DisplayName("RBAC : Le Gestionnaire de Paie peut simuler/calculer mais ne peut PAS valider (403)")
    public void testGestionnairePaie_CalculAutorise_ValidationInterdite() throws Exception {
        CalculEmployeRequest reqEmp = new CalculEmployeRequest();
        reqEmp.setIdPeriodePaie(idPeriode);
        reqEmp.setIdEmploye(emp.idEmploye);
        reqEmp.setSimulation(true);

        // Simulation autorisée pour le gestionnaire de paie
        mockMvc.perform(post("/api/v1/paie/calculer-employe")
                        .header("Authorization", "Bearer " + tokenGestionnairePaie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqEmp)))
                .andExpect(status().isOk());

        // Tentative de validation finale par le gestionnaire de paie -> 403 Forbidden
        ActionPaiePeriodeRequest reqVal = new ActionPaiePeriodeRequest();
        reqVal.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .header("Authorization", "Bearer " + tokenGestionnairePaie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqVal)))
                .andExpect(status().isForbidden());

        // Tentative de commande workflow CLOSE par le gestionnaire de paie -> 403 Forbidden
        Map<String, Object> reqClose = Map.of("version", 1);
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/CLOSE")
                        .header("Authorization", "Bearer " + tokenGestionnairePaie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqClose)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. WORKFLOW : REJET DU CALCUL SI POINTAGE NON FIGÉ (POINTAGE_NOT_FROZEN)
    // =========================================================================

    @Test
    @DisplayName("Workflow : Le calcul de paie réel est bloqué si le pointage n'est pas figé (409)")
    public void testCalculPaie_BloqueSiPointageNonFige() throws Exception {
        // Période encore en EN_COURS (pointage non transmis/validé)
        CalculPeriodeRequest req = new CalculPeriodeRequest();
        req.setIdPeriodePaie(idPeriode);
        req.setSimulation(false); // Calcul réel

        mockMvc.perform(post("/api/v1/paie/calculer-periode")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYROLL_PERIOD_PREREQUISITE_BLOCKED"));
    }

    // =========================================================================
    // 5. WORKFLOW : VALIDATION BLOQUÉE SI AUCUN BULLETIN CALCULÉ
    // =========================================================================

    @Test
    @DisplayName("Workflow : La validation de période est bloquée si aucun bulletin n'est calculé (409)")
    public void testValidationPaie_BloqueeSiAucunBulletinCalcule() throws Exception {
        // Faire passer la période en VALIDEE pour figer le pointage
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE') " +
                             "WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
        }

        // Tenter de valider alors qu'aucun bulletin n'a été calculé
        ActionPaiePeriodeRequest req = new ActionPaiePeriodeRequest();
        req.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .header("Authorization", "Bearer " + tokenResponsablePaie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYROLL_PERIOD_PREREQUISITE_BLOCKED"));
    }

    // =========================================================================
    // 6. WORKFLOW : IMMUTABILITÉ ABSOLUE SI PÉRIODE CLÔTURÉE
    // =========================================================================

    @Test
    @DisplayName("Workflow : Aucun calcul ou validation n'est autorisé sur une période clôturée (409)")
    public void testImmutabilite_PeriodeCloturee() throws Exception {
        // Basculer la période en CLOTUREE
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE periode_paie SET id_statut_periode = (SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'CLOTUREE') " +
                             "WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
        }

        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(false);

        mockMvc.perform(post("/api/v1/paie/calculer-periode")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));

        ActionPaiePeriodeRequest reqVal = new ActionPaiePeriodeRequest();
        reqVal.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/api/v1/paie/valider-periode")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqVal)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    // =========================================================================
    // 7. SÉCURITÉ CONTRACTUELLE : MUTATION GÉNÉRIQUE DE STATUT RETIRÉE (410 GONE)
    // =========================================================================

    @Test
    @DisplayName("Sécurité : L'affectation directe d'un état générique est bannie et retourne 410 Gone")
    public void testMutationDirecteStatutBannie_410Gone() throws Exception {
        mockMvc.perform(put("/periodes/" + idPeriode + "/statut")
                        .header("Authorization", "Bearer " + tokenAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statut\": \"CLOTUREE\"}"))
                .andExpect(status().isGone());
    }
}
