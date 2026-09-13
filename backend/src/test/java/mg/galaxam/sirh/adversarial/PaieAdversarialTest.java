package mg.galaxam.sirh.adversarial;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculEmployeRequest;
import mg.galaxam.sirh.api.dto.paie.stc.StcCalculRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Adversarial & Security Tests - Module Paie & STC (Immutabilité, RBAC, Intégrité)")
class PaieAdversarialTest extends BaseIntegrationTest {

    private int idPeriode;
    private CreatedEmploye employe;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-01",
                LocalDate.of(2025, 12, 26),
                LocalDate.of(2026, 1, 25),
                "EN_COURS"
        );

        employe = employeFixtures.createEmployeWithSalary(
                "EMP-ADV-01",
                "RAKOTO",
                "Adversarial",
                1,
                new BigDecimal("600000.00"),
                "PERMANENT",
                0
        );

        creerCumulPointage(employe.idEmploye, idPeriode, 22, 22, 173.33);
    }

    private void creerCumulPointage(int idEmploye, int idPeriode, int ouvrables, int presents, double heures) {
        String sql = "INSERT INTO pointage_cumul_periode (id_employe, id_periode_paie, nb_jours_ouvrables, nb_jours_presents, "
                + "nb_jours_absence, nb_jours_conge, nb_jours_ferie_chome, nb_jours_ferie_travaille, "
                + "total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, heures_absence_retenues, "
                + "nb_jours_deplacement, montant_deplacement, nb_jours_repas, montant_repas, gele_par, gele_at) "
                + "VALUES (?, ?, ?, ?, 0, 0, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, 0, 1, CURRENT_TIMESTAMP)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            ps.setInt(2, idPeriode);
            ps.setInt(3, ouvrables);
            ps.setInt(4, presents);
            ps.setDouble(5, heures);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("ADVERSARIAL: Tentative de recalcul d'un bulletin clôturé -> Rejet 409 Conflict (Immutabilité)")
    void testRecalculBulletinClotureRejetteConflict() throws Exception {
        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setSimulation(false);

        // 1. Calcul réel
        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk());

        // 2. Clôture de la période
        ActionPaiePeriodeRequest reqCloture = new ActionPaiePeriodeRequest();
        reqCloture.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/cloturer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCloture)))
                .andExpect(status().isOk());

        // 3. Tentative frauduleuse de recalculer le bulletin clôturé
        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("clôturé et strictement immuable")));
    }

    @Test
    @DisplayName("ADVERSARIAL: Tentative de calcul pour employé inexistant -> Rejet 400 Bad Request")
    void testCalculEmployeInexistantRejetteBadRequest() throws Exception {
        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(999999); // Inexistant
        req.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @DisplayName("ADVERSARIAL: Tentative de calcul pour employé sans affectation active -> Rejet 400 Bad Request")
    void testCalculEmployeSansContratActifRejetteBadRequest() throws Exception {
        int idSansContrat = employeFixtures.createEmployeSansContrat("Inactif", "SansContrat");

        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(idSansContrat);
        req.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("affectation active introuvable")));
    }

    @Test
    @DisplayName("ADVERSARIAL: Réouverture de période sans motif ou motif < 5 caractères -> Rejet 400 Bad Request")
    void testReouvertureSansMotifTropCourtRejetteBadRequest() throws Exception {
        ActionPaiePeriodeRequest req = new ActionPaiePeriodeRequest();
        req.setIdPeriodePaie(idPeriode);
        req.setMotif("abc"); // Moins de 5 caractères

        mockMvc.perform(post("/paie/rouvrir-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error", containsString("min 5 caractères")));
    }

    @Test
    @DisplayName("ADVERSARIAL: Simulation STC sur employé inexistant -> Rejet 400 Bad Request")
    void testStcEmployeInexistantRejetteBadRequest() throws Exception {
        StcCalculRequest req = new StcCalculRequest();
        req.setIdEmploye(999999);
        req.setIdPeriodePaie(idPeriode);
        req.setDateDepart(Date.valueOf("2026-01-20"));
        req.setMotifRupture("DEMISSION");

        mockMvc.perform(post("/paie/stc/calculer")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is(true)))
                .andExpect(jsonPath("$.message", containsString("introuvable")));
    }

    @Test
    @DisplayName("SECURITY / RBAC: ROLE_AGENT_POINTAGE tente d'exécuter des actions de Paie -> Rejet 403 Forbidden")
    void testRbacPointageAgentInterditSurPaie() throws Exception {
        CalculEmployeRequest reqCalc = new CalculEmployeRequest();
        reqCalc.setIdEmploye(employe.idEmploye);
        reqCalc.setIdPeriodePaie(idPeriode);

        // 1. Calcul bulletin refusé
        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isForbidden());

        // 2. Clôture de période refusée
        ActionPaiePeriodeRequest reqAct = new ActionPaiePeriodeRequest();
        reqAct.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/cloturer-periode")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqAct)))
                .andExpect(status().isForbidden());

        // 3. Validation STC refusée
        StcCalculRequest reqStc = new StcCalculRequest();
        reqStc.setIdEmploye(employe.idEmploye);
        reqStc.setDateDepart(Date.valueOf("2026-01-20"));
        reqStc.setMotifRupture("DEMISSION");

        mockMvc.perform(post("/paie/stc/valider")
                        .header("Authorization", "Bearer " + getPointageToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqStc)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("SECURITY: Requête non authentifiée (sans Token) -> Rejet 403 Forbidden")
    void testAppelSansTokenRejetteUnauthorized() throws Exception {
        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(employe.idEmploye);
        req.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/calculer-employe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isForbidden());
    }
}
