package mg.galaxam.sirh.integration.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.paie.ActionPaiePeriodeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculEmployeRequest;
import mg.galaxam.sirh.api.dto.paie.CalculPeriodeRequest;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures.CreatedEmploye;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("Integration Tests - API PaieController (Calculs, Cycle de Vie, Exports)")
class PaieControllerIntegrationTest extends BaseIntegrationTest {

    private int idPeriode;
    private CreatedEmploye empPermanent;
    private CreatedEmploye empConsultant;

    @BeforeEach
    void setUp() {
        databaseCleaner.cleanTransactionalData();

        // 1. Période de paie standard (26 Décembre 2025 au 25 Janvier 2026 - Conforme au modèle Excel)
        idPeriode = employeFixtures.createPeriodePaie(
                "PER-2026-01",
                LocalDate.of(2025, 12, 26),
                LocalDate.of(2026, 1, 25),
                "VALIDEE"
        );

        // 2. Collaborateur permanent (Salarié CDI - Base 625 000 Ar, conforme Ohatra)
        empPermanent = employeFixtures.createEmployeWithSalary(
                "EMP-PERM-01",
                "RAKETAKA",
                "Soafara",
                1,
                new BigDecimal("625000.00"),
                "PERMANENT",
                0
        );

        // 3. Prestataire / Consultant (Régime ISI 5% - Base 1 500 000 Ar)
        empConsultant = employeFixtures.createEmployeWithSalary(
                "CONS-01",
                "ANDRIA",
                "Expert",
                1,
                new BigDecimal("1500000.00"),
                "CONSULTANT",
                0
        );

        // 4. Cumul pointage gelé requis pour le calcul réel
        creerCumulPointage(empPermanent.idEmploye, idPeriode, 22, 22, 173.33);
        creerCumulPointage(empConsultant.idEmploye, idPeriode, 22, 22, 173.33);
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
    @DisplayName("POST /paie/calculer-employe - Simulation sans validation ni imputation d'avance")
    void testCalculEmployeSimulation() throws Exception {
        // Enregistrer une avance approuvée
        int idAvance = employeFixtures.createAvanceAcompte(
                empPermanent.idEmploye,
                idPeriode,
                "ACOMPTE_15",
                "RETENUE",
                new BigDecimal("50000.00"),
                "APPROUVE"
        );

        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(empPermanent.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setSimulation(true);

        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulletin.matricule", is("EMP-PERM-01")))
                .andExpect(jsonPath("$.bulletin.salaireBase", is(625000.0)))
                .andExpect(jsonPath("$.bulletin.statut", is("CALCULE")))
                .andExpect(jsonPath("$.lignes", not(empty())));

        // Vérification en base : La simulation n'impute pas définitivement l'avance
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut, id_bulletin FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("APPROUVE", rs.getString("statut"), "Une simulation ne doit pas imputer les avances");
                assertNull(rs.getObject("id_bulletin"));
            }
        }
    }

    @Test
    @DisplayName("POST /paie/calculer-employe - Calcul réel avec persistance et imputation d'avance")
    void testCalculEmployeReelAvecImputationAvance() throws Exception {
        // Enregistrer une avance de 50 000 Ar approuvée pour cet employé
        int idAvance = employeFixtures.createAvanceAcompte(
                empPermanent.idEmploye,
                idPeriode,
                "ACOMPTE_15",
                "RETENUE",
                new BigDecimal("50000.00"),
                "APPROUVE"
        );

        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(empPermanent.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setSimulation(false); // Calcul et validation réelle

        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulletin.matricule", is("EMP-PERM-01")))
                .andExpect(jsonPath("$.bulletin.salaireBase", is(625000.0)))
                .andExpect(jsonPath("$.bulletin.statut", is("CALCULE")));

        // Vérification en base : Le bulletin et ses lignes doivent exister
        Integer idBulletinCree = null;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_bulletin, total_gains_bruts, net_a_payer FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, empPermanent.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Le bulletin doit être persisté en base");
                idBulletinCree = rs.getInt("id_bulletin");
                assertTrue(rs.getBigDecimal("total_gains_bruts").compareTo(BigDecimal.ZERO) > 0);
            }
        }

        assertNotNull(idBulletinCree);

        // Vérification des lignes ventilées
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM bulletin_paie_ligne WHERE id_bulletin = ?")) {
            ps.setInt(1, idBulletinCree);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getInt(1) > 0, "Les lignes de bulletin doivent être ventilées");
            }
        }

        // Vérification de l'imputation de l'avance : statut = 'IMPUTE_PAIE' et id_bulletin associé
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT statut, id_bulletin FROM paie_avance_acompte WHERE id_avance = ?")) {
            ps.setInt(1, idAvance);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("IMPUTE_PAIE", rs.getString("statut"));
                assertEquals(idBulletinCree, rs.getInt("id_bulletin"));
            }
        }
    }

    @Test
    @DisplayName("GET /paie/bulletin/{id} - Consultation d'un bulletin complet")
    void testObtenirBulletinComplet() throws Exception {
        // Créer un bulletin réel
        CalculEmployeRequest req = new CalculEmployeRequest();
        req.setIdEmploye(empPermanent.idEmploye);
        req.setIdPeriodePaie(idPeriode);
        req.setSimulation(false);

        mockMvc.perform(post("/paie/calculer-employe")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(req)))
                .andExpect(status().isOk());

        int idBulletin;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_bulletin FROM bulletin_paie WHERE id_periode_paie = ? AND id_employe = ?")) {
            ps.setInt(1, idPeriode);
            ps.setInt(2, empPermanent.idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                idBulletin = rs.getInt(1);
            }
        }

        // Appel GET
        mockMvc.perform(get("/paie/bulletin/" + idBulletin)
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulletin.idBulletin", is(idBulletin)))
                .andExpect(jsonPath("$.bulletin.matricule", is("EMP-PERM-01")))
                .andExpect(jsonPath("$.lignes", not(empty())));
    }

    @Test
    @DisplayName("GET /paie/periode/{id}/cockpit - données, règles et actions dans une réponse unique")
    void testCockpitUnifieLeContexteDeDecision() throws Exception {
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periode.idPeriodePaie", is(idPeriode)))
                .andExpect(jsonPath("$.readiness.checks", hasSize(4)))
                .andExpect(jsonPath("$.readiness.checks[0].status", is("PASS")))
                .andExpect(jsonPath("$.population", hasSize(2)))
                .andExpect(jsonPath("$.population[0].decision", is("INCLUDED")))
                .andExpect(jsonPath("$.validationSummary.population", is(2)))
                .andExpect(jsonPath("$.validationSummary.version", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.context.selectedPeriod.id", is(idPeriode)))
                .andExpect(jsonPath("$.context.availableActions.CALCULATE.allowed", is(true)))
                .andExpect(jsonPath("$.context.outputs", hasSize(6)))
                .andExpect(jsonPath("$.context.outputs[0].checksum", hasLength(64)))
                .andExpect(jsonPath("$.context.selectedPeriod.version", greaterThanOrEqualTo(0)));
    }

    @Test
    @DisplayName("Population ambiguë - le backend bloque le calcul et explique le salarié concerné")
    void testPopulationAmbigueBloqueLeCalcul() throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO employe_affectation_carriere "
                             + "(id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, "
                             + "id_departement, id_service, id_fonction, id_responsable_hierarchique, "
                             + "id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin) "
                             + "SELECT id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, "
                             + "id_departement, id_service, id_fonction, id_responsable_hierarchique, "
                             + "id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin "
                             + "FROM employe_affectation_carriere WHERE id_employe = ? LIMIT 1")) {
            statement.setInt(1, empPermanent.idEmploye);
            assertEquals(1, statement.executeUpdate());
        }

        mockMvc.perform(get("/paie/periode/" + idPeriode + "/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.population[?(@.employeeId == " + empPermanent.idEmploye + ")].decision",
                        contains("BLOCKED")))
                .andExpect(jsonPath("$.context.availableActions.CALCULATE.allowed", is(false)))
                .andExpect(jsonPath("$.context.availableActions.CALCULATE.reasonCodes[0]",
                        is("PAYROLL_POPULATION_BLOCKED")));
    }

    @Test
    @DisplayName("POST /paie/calculer-periode & Cycle de Vie (Validation -> Clôture -> Réouverture)")
    void testCycleDeViePeriodePaie() throws Exception {
        // 1. Calcul collectif de la période
        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(false);

        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nbBulletinsCalcules", is(2)))
                .andExpect(jsonPath("$.referencesBulletins", hasSize(2)));

        // Vérifier que le bordereau d'honoraires consultant (ISI 5%) a été généré
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM paie_honoraire_prestation WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Un bordereau honoraire ISI doit être généré pour le consultant");
            }
        }

        // 2. Validation de la période
        ActionPaiePeriodeRequest reqAction = new ActionPaiePeriodeRequest();
        reqAction.setIdPeriodePaie(idPeriode);

        mockMvc.perform(post("/paie/valider-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqAction)))
                .andExpect(status().isOk());

        // Une seconde validation n'est pas un succès silencieux : le backend explique le blocage.
        mockMvc.perform(post("/paie/valider-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqAction)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("PAYROLL_PERIOD_PREREQUISITE_BLOCKED")))
                .andExpect(jsonPath("$.details.reasonCodes[0]", is("PAYROLL_NOT_CALCULATED")));

        // 3. Clôture de la période
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/CLOSE")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of("version", getPayrollPeriodVersion(idPeriode)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("ALLOWED")));

        // Vérification en base : La période doit être CLOTUREE et les bulletins CLOTURE
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT r.code FROM periode_paie p JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode WHERE p.id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("CLOTUREE", rs.getString(1));
            }
        }

        // 4. Réouverture de la période (Contre-Audit d'urgence avec motif)
        reqAction.setMotif("Erreur de pointage nécessitant régularisation");
        mockMvc.perform(post("/periodes/" + idPeriode + "/actions/REOPEN")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(java.util.Map.of(
                                "version", getPayrollPeriodVersion(idPeriode),
                                "motif", reqAction.getMotif()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision", is("ALLOWED")));

        // Vérification en base : La période est de nouveau EN_COURS (réouverte)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT r.code FROM periode_paie p JOIN ref_statut_periode_paie r ON p.id_statut_periode = r.id_statut_periode WHERE p.id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("EN_COURS", rs.getString(1));
            }
        }
    }

    @Test
    @DisplayName("GET cockpit - pagination serveur et recherche ne masquent pas la population")
    void testCockpitPaginationServeur() throws Exception {
        CalculPeriodeRequest calculation = new CalculPeriodeRequest();
        calculation.setIdPeriodePaie(idPeriode);
        calculation.setSimulation(false);
        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(calculation)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/paie/periode/" + idPeriode + "/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .param("page", "2")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulletins", hasSize(1)))
                .andExpect(jsonPath("$.bulletinsPage.totalElements", is(2)))
                .andExpect(jsonPath("$.bulletinsPage.currentPage", is(2)))
                .andExpect(jsonPath("$.populationPage.totalElements", is(2)));

        mockMvc.perform(get("/paie/periode/" + idPeriode + "/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .param("q", "EMP-PERM-01")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulletins", hasSize(1)))
                .andExpect(jsonPath("$.bulletins[0].idEmploye", is(empPermanent.idEmploye)))
                .andExpect(jsonPath("$.bulletinsPage.totalElements", is(1)));
    }

    @Test
    @DisplayName("GET /paie/periode/{id}/export/* - Exports réglementaires (SAFI, EDI ISI XML, DNTS, Virement)")
    void testExportsReglementairesPaie() throws Exception {
        // Pré-calculer la période
        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(false);

        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isOk());

        // Les sorties officielles restent bloquées tant que la campagne n'est pas validée.
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/safi")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.details.reasonCodes[0]", is("PAYROLL_NOT_VALIDATED")));

        ActionPaiePeriodeRequest validation = new ActionPaiePeriodeRequest();
        validation.setIdPeriodePaie(idPeriode);
        mockMvc.perform(post("/paie/valider-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(validation)))
                .andExpect(status().isOk());

        // 1. Export SAFI 25 Colonnes (JSON data)
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/safi-data")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", not(empty())));

        // 2. Export SAFI CSV Téléchargeable
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/safi")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("etat_301_safi_periode_")));

        // 3. Export EDI ISI XML Téléchargeable (Régime 5% Consultant)
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/edi-isi")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));

        // 4. Export DNTS CNaPS CSV
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/dnts")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("dnts_cnaps_fmfp_periode_")));

        // 5. Export Ordre de Virement Bancaire CSV
        mockMvc.perform(get("/paie/periode/" + idPeriode + "/export/virement")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("ordre_virement_periode_")));
    }

    @Test
    @DisplayName("GET /paie/periode/{id}/cockpit - Récupération du cockpit DAF avec checklist et KPIs")
    void testObtenirCockpitPeriode() throws Exception {
        // Pré-calculer la période pour avoir des KPIs
        CalculPeriodeRequest reqCalc = new CalculPeriodeRequest();
        reqCalc.setIdPeriodePaie(idPeriode);
        reqCalc.setSimulation(false);

        mockMvc.perform(post("/paie/calculer-periode")
                        .header("Authorization", "Bearer " + getAdminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(reqCalc)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/paie/periode/" + idPeriode + "/cockpit")
                        .header("Authorization", "Bearer " + getAdminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periode.idPeriodePaie", is(idPeriode)))
                .andExpect(jsonPath("$.periode.code", is("PER-2026-01")))
                .andExpect(jsonPath("$.readiness.pointageScelle", is(true)))
                .andExpect(jsonPath("$.kpis.effectifCalcule", is(2)))
                .andExpect(jsonPath("$.bulletins", hasSize(2)));
    }
}
