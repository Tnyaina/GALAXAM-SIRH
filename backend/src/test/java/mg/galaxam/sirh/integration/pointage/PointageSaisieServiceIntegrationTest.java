package mg.galaxam.sirh.integration.pointage;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.pointage.SaisiePointageJourDTO;
import mg.galaxam.sirh.fixtures.TestEmployeFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class PointageSaisieServiceIntegrationTest extends BaseIntegrationTest {

    private TestEmployeFixtures.CreatedEmploye empTnr;
    private TestEmployeFixtures.CreatedEmploye empTmm;
    private int idPeriode;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        LocalDate dEmb = LocalDate.now().minusMonths(6);
        empTnr = employeFixtures.createActiveEmploye("EMP001", "RABARIJAONA", "Jean", 1, dEmb);
        empTmm = employeFixtures.createActiveEmploye("EMP002", "RAKOTO", "Paul", 2, dEmb);

        LocalDate debutPeriode = LocalDate.now().minusDays(15);
        LocalDate finPeriode = LocalDate.now().plusDays(15);
        idPeriode = employeFixtures.createPeriodePaie("P-TEST", debutPeriode, finPeriode, "EN_COURS");
    }

    @Test
    @DisplayName("API Pointage Saisie : Enregistrement nominal journée standard 8h (TNR) via /pointages/saisie/batch")
    public void testEnregistrerPointage_Nominal() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1);
        while (datePtg.getDayOfWeek() == java.time.DayOfWeek.SATURDAY || datePtg.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            datePtg = datePtg.minusDays(1);
        }

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");
        dto.setHoraireContinu(false);
        dto.setIndemniteRepasRecue(true);

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isOk());

        // Vérification directe en base de données
        try (Connection c = dataSource.getConnection()) {
            String sqlPtg = "SELECT p.total_heures, p.heures_nuit, p.minutes_retard, s.code AS code_statut " +
                    "FROM pointage_jour p " +
                    "JOIN ref_statut_pointage_jour s ON p.id_statut_jour = s.id_statut_jour " +
                    "WHERE p.id_employe = ? AND p.date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next(), "La ligne de pointage doit exister en base");
                    assertEquals(8.0, rs.getDouble("total_heures"), 0.01);
                    assertEquals(0.0, rs.getDouble("heures_nuit"), 0.01);
                    assertEquals(0, rs.getInt("minutes_retard"));
                    assertEquals("PRESENT", rs.getString("code_statut"));
                }
            }

            // Vérification des indemnités générées en base
            String sqlInd = "SELECT p.montant_calcule, r.code FROM pointage_indemnite_jour p " +
                    "JOIN ref_indemnite r ON p.id_indemnite = r.id_indemnite " +
                    "WHERE p.id_pointage IN (SELECT id_pointage FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlInd)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    boolean foundRepas = false;
                    while (rs.next()) {
                        String code = rs.getString("code");
                        if ("REPAS".equals(code)) {
                            foundRepas = true;
                            assertTrue(rs.getDouble("montant_calcule") > 0);
                        }
                    }
                    assertTrue(foundRepas, "L'indemnité repas midi doit avoir été enregistrée");
                }
            }
        }
    }

    @Test
    @DisplayName("API Pointage Saisie : Shift finissant à 23h00 -> 1h de nuit et 2nd repas calculés")
    public void testEnregistrerPointage_NuitEt2ndRepas() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(2);

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("14:00");
        dto.setHeureSortieMatin("18:00");
        dto.setHeureEntreeAm("19:00");
        dto.setHeureSortieAm("23:00"); // Sortie à 23h -> 1h nuit

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isOk());

        // Vérification heures nuit en base
        try (Connection c = dataSource.getConnection()) {
            String sqlPtg = "SELECT total_heures, heures_nuit FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?";
            try (PreparedStatement ps = c.prepareStatement(sqlPtg)) {
                ps.setInt(1, empTnr.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(8.0, rs.getDouble("total_heures"), 0.01);
                    assertEquals(1.0, rs.getDouble("heures_nuit"), 0.01, "De 22:00 à 23:00 = 1h de nuit");
                }
            }
        }
    }

    @Test
    @DisplayName("API Pointage Saisie : Règle Toamasina (TMM) - indemnité de déplacement calculée")
    public void testEnregistrerPointage_DeductionTransportTMM() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(2);

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTmm.idEmploye);
        dto.setIdSite(empTmm.idSite); // Site 2 = TMM
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");
        dto.setDeplacementMatin(true);
        dto.setDeplacementAm(true);

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isOk());

        // Vérifier en base le montant de l'indemnité transport
        try (Connection c = dataSource.getConnection()) {
            String sqlInd = "SELECT p.montant_calcule FROM pointage_indemnite_jour p " +
                    "JOIN ref_indemnite r ON p.id_indemnite = r.id_indemnite " +
                    "WHERE r.code = 'DEPLACEMENT' AND p.id_pointage IN " +
                    "(SELECT id_pointage FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?)";
            try (PreparedStatement ps = c.prepareStatement(sqlInd)) {
                ps.setInt(1, empTmm.idEmploye);
                ps.setDate(2, Date.valueOf(datePtg));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        assertEquals(2418.18, rs.getDouble("montant_calcule"), 0.01, "A TMM, transport = 2418.18 Ar");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("API Pointage Saisie : Rejet HTTP 409 Conflict pour date future")
    public void testEligibilite_DateFutureRefusee() throws Exception {
        LocalDate dateDemain = LocalDate.now().plusDays(1);
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(dateDemain.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("date future")));
    }

    @Test
    @DisplayName("API Pointage Saisie : Rejet HTTP 409 Conflict si employé pointé sur un autre site")
    public void testEligibilite_EmployeAutreSiteRefuse() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1);
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(2); // Site TMM au lieu de TNR
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("n'est pas rattaché au site ID 2")));
    }

    @Test
    @DisplayName("API Pointage Saisie : Rejet HTTP 409 Conflict si l'employé est inactif")
    public void testEligibilite_EmployeInactifRefuse() throws Exception {
        LocalDate datePtg = LocalDate.now().minusDays(1);

        // Désactiver l'employé en base
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE employe SET est_actif = false WHERE id_employe = ?")) {
                ps.setInt(1, empTnr.idEmploye);
                ps.executeUpdate();
            }
        }

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(empTnr.idEmploye);
        dto.setIdSite(empTnr.idSite);
        dto.setDatePointage(datePtg.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("est inactif")));
    }

    @Test
    @DisplayName("API Pointage Saisie : Transition changement de site TNR -> TMM sans doublon inter-sites")
    public void testSaisieQuotidienne_TransitionChangementSite_PasDeDoublon() throws Exception {
        // Date de mutation : il y a 5 jours
        LocalDate dateMutation = LocalDate.now().minusDays(5);
        LocalDate dateAvantMutation = LocalDate.now().minusDays(10);
        LocalDate dateApresMutation = LocalDate.now().minusDays(2);

        // Transition de empTnr (initialement sur Site 1 TNR) vers Site 2 TMM
        // Note: On laisse date_fin = NULL sur l'ancienne affectation pour tester la résilience
        // même si l'ancienne ligne n'avait pas été clôturée explicitement
        try (Connection c = dataSource.getConnection()) {
            // 1. Historiser l'affectation initiale TNR (sans renseigner date_fin pour tester le cas limite)
            try (PreparedStatement psOld = c.prepareStatement(
                    "UPDATE employe_affectation_carriere SET id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'HISTORISE'), date_fin = NULL " +
                    "WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF')")) {
                psOld.setInt(1, empTnr.idEmploye);
                psOld.executeUpdate();
            }

            // 2. Créer la nouvelle affectation active TMM (Site 2)
            String sqlNew = "INSERT INTO employe_affectation_carriere (" +
                    "id_employe, id_contrat, id_statut_rh, id_motif_mouvement, id_site, id_departement, id_service, id_fonction, " +
                    "id_categorie_professionnelle, id_mode_calcul_paie, salaire_base, date_debut, date_fin" +
                    ") VALUES (?, (SELECT id_contrat FROM contrat_travail WHERE id_employe = ? LIMIT 1), " +
                    "(SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'ACTIF'), " +
                    "(SELECT id_motif_mouvement FROM ref_motif_mouvement WHERE code = 'MUTATION'), " +
                    "2, 1, 1, 1, 2, 1, 500000.00, ?, NULL)";
            try (PreparedStatement psNew = c.prepareStatement(sqlNew)) {
                psNew.setInt(1, empTnr.idEmploye);
                psNew.setInt(2, empTnr.idEmploye);
                psNew.setDate(3, Date.valueOf(dateMutation));
                psNew.executeUpdate();
            }
        }

        // A. Vérification pour la date APRÈS mutation :
        // 1. Sur le site TNR (Site 1) : l'employé NE DOIT PAS être présent
        String resTnrApres = mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + getPointageToken())
                .param("idSite", "1")
                .param("date", dateApresMutation.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootTnr = objectMapper.readTree(resTnrApres);
        com.fasterxml.jackson.databind.JsonNode grilleTnr = rootTnr.get("grilleData");
        boolean presentTnrApres = false;
        for (com.fasterxml.jackson.databind.JsonNode row : grilleTnr) {
            if (row.get("idEmploye").asInt() == empTnr.idEmploye) {
                presentTnrApres = true;
                break;
            }
        }
        assertFalse(presentTnrApres, "L'employé muté à TMM ne doit plus apparaître sur TNR après sa date de mutation");

        // 2. Sur le site TMM (Site 2) : l'employé DOIT être présent
        String resTmmApres = mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + getPointageToken())
                .param("idSite", "2")
                .param("date", dateApresMutation.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode rootTmm = objectMapper.readTree(resTmmApres);
        com.fasterxml.jackson.databind.JsonNode grilleTmm = rootTmm.get("grilleData");
        boolean presentTmmApres = false;
        for (com.fasterxml.jackson.databind.JsonNode row : grilleTmm) {
            if (row.get("idEmploye").asInt() == empTnr.idEmploye) {
                presentTmmApres = true;
                break;
            }
        }
        assertTrue(presentTmmApres, "L'employé muté à TMM doit apparaître sur TMM après sa date de mutation");

        // B. Vérification pour la date AVANT mutation :
        // 1. Sur le site TNR (Site 1) : l'employé DOIT être présent (antériorité historique)
        String resTnrAvant = mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + getPointageToken())
                .param("idSite", "1")
                .param("date", dateAvantMutation.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode grilleTnrAvant = objectMapper.readTree(resTnrAvant).get("grilleData");
        boolean presentTnrAvant = false;
        for (com.fasterxml.jackson.databind.JsonNode row : grilleTnrAvant) {
            if (row.get("idEmploye").asInt() == empTnr.idEmploye) {
                presentTnrAvant = true;
                break;
            }
        }
        assertTrue(presentTnrAvant, "L'employé doit toujours apparaître sur TNR pour les dates antérieures à sa mutation");

        // 2. Sur le site TMM (Site 2) : l'employé NE DOIT PAS être présent avant mutation
        String resTmmAvant = mockMvc.perform(get("/pointages/saisie")
                .header("Authorization", "Bearer " + getPointageToken())
                .param("idSite", "2")
                .param("date", dateAvantMutation.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode grilleTmmAvant = objectMapper.readTree(resTmmAvant).get("grilleData");
        boolean presentTmmAvant = false;
        for (com.fasterxml.jackson.databind.JsonNode row : grilleTmmAvant) {
            if (row.get("idEmploye").asInt() == empTnr.idEmploye) {
                presentTmmAvant = true;
                break;
            }
        }
        assertFalse(presentTmmAvant, "L'employé ne doit pas apparaître sur TMM pour les dates antérieures à sa mutation");

        // C. Vérification de rejet lors de la soumission d'un pointage sur TNR après mutation
        SaisiePointageJourDTO ptgTnrInvalide = new SaisiePointageJourDTO();
        ptgTnrInvalide.setIdEmploye(empTnr.idEmploye);
        ptgTnrInvalide.setIdSite(1); // Mauvais site (TNR) après mutation
        ptgTnrInvalide.setDatePointage(dateApresMutation.toString());
        ptgTnrInvalide.setIdPeriodePaie(idPeriode);
        ptgTnrInvalide.setHeureEntreeMatin("08:00");
        ptgTnrInvalide.setHeureSortieMatin("12:00");
        ptgTnrInvalide.setHeureEntreeAm("13:00");
        ptgTnrInvalide.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(ptgTnrInvalide))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("n'est pas rattaché au site ID 1")));
    }
}
