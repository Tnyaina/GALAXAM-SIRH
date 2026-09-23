package mg.galaxam.sirh.adversarial;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.pointage.api.dto.SaisiePointageJourDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class PointageAdversarialTest extends BaseIntegrationTest {

    private int idEmploye;
    private int idPeriodeOuverte;

    @BeforeEach
    public void setUp() throws Exception {
        databaseCleaner.cleanTransactionalData();
        idEmploye = employeFixtures.createActiveEmploye("EMP-ADV", "RAMIANDRISOA", "Hery", 1).idEmploye;

        // Créer une période de paie OUVERTE couvrant le mois courant
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                            "VALUES ('PER-OUV', 'Période active', CURRENT_DATE - INTERVAL '15 days', CURRENT_DATE + INTERVAL '15 days', " +
                            "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'EN_COURS')) RETURNING id_periode_paie")) {
                var rs = ps.executeQuery();
                rs.next();
                idPeriodeOuverte = rs.getInt(1);
            }
            c.commit();
        }
    }

    @Test
    @DisplayName("Adversarial API Pointage : Rejet absolu de la saisie d'un pointage sur une date future (HTTP 409 Conflict)")
    public void testRejetPointageDateFuture() throws Exception {
        LocalDate dateFuture = LocalDate.now().plusDays(2);
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(idEmploye);
        dto.setIdSite(1);
        dto.setDatePointage(dateFuture.toString());
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("future")));
    }

    @Test
    @DisplayName("Adversarial API Pointage : Rejet de la saisie sur une période verrouillée/validée par le DAF (HTTP 409 Conflict)")
    public void testRejetPointageSurPeriodeValidee() throws Exception {
        LocalDate hier = LocalDate.now().minusDays(1);

        // Créer une période de paie couvrant 'hier' et la verrouiller en statut VALIDEE
        int idPeriode;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO periode_paie (code, libelle, date_debut, date_fin, id_statut_periode) " +
                            "VALUES ('PER-LOCK', 'Période verrouillée', ?, ?, " +
                            "(SELECT id_statut_periode FROM ref_statut_periode_paie WHERE code = 'VALIDEE')) RETURNING id_periode_paie")) {
                ps.setDate(1, Date.valueOf(hier.minusDays(5)));
                ps.setDate(2, Date.valueOf(hier.plusDays(5)));
                var rs = ps.executeQuery();
                rs.next();
                idPeriode = rs.getInt(1);
            }
            c.commit();
        }

        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(idEmploye);
        dto.setIdSite(1);
        dto.setDatePointage(hier.toString());
        dto.setIdPeriodePaie(idPeriode);
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Adversarial API Pointage : Rejet lors d'un conflit de concurrence optimiste (OCC stale version, HTTP 409 Conflict)")
    public void testRejetConflitOptimisticLocking() throws Exception {
        LocalDate hier = LocalDate.now().minusDays(1);

        // 1. Premier enregistrement via API (version initiale = 1)
        SaisiePointageJourDTO dto1 = new SaisiePointageJourDTO();
        dto1.setIdEmploye(idEmploye);
        dto1.setIdSite(1);
        dto1.setIdPeriodePaie(idPeriodeOuverte);
        dto1.setDatePointage(hier.toString());
        dto1.setHeureEntreeMatin("08:00");
        dto1.setHeureSortieMatin("12:00");
        dto1.setHeureEntreeAm("13:00");
        dto1.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto1))))
                .andExpect(status().isOk());

        int idPointage;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id_pointage FROM pointage_jour WHERE id_employe = ? AND date_pointage = ?")) {
            ps.setInt(1, idEmploye);
            ps.setDate(2, Date.valueOf(hier));
            var rs = ps.executeQuery();
            rs.next();
            idPointage = rs.getInt(1);
        }

        // 2. Simuler une mise à jour concurrente incrémentant la version en base (version passe à 2)
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("UPDATE pointage_jour SET version = 2 WHERE id_pointage = ?")) {
            ps.setInt(1, idPointage);
            ps.executeUpdate();
        }

        // 3. L'utilisateur tente d'enregistrer avec l'ancienne version périmée (version = 1) via API
        SaisiePointageJourDTO dtoStale = new SaisiePointageJourDTO();
        dtoStale.setIdPointage(idPointage);
        dtoStale.setVersion(1); // Ancienne version
        dtoStale.setIdEmploye(idEmploye);
        dtoStale.setIdSite(1);
        dtoStale.setIdPeriodePaie(idPeriodeOuverte);
        dtoStale.setDatePointage(hier.toString());
        dtoStale.setHeureEntreeMatin("08:15");
        dtoStale.setHeureSortieMatin("12:00");
        dtoStale.setHeureEntreeAm("13:00");
        dtoStale.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dtoStale))))
                .andExpect(status().isConflict())
                .andExpect(content().string(containsString("recharger")));
    }

    @Test
    @DisplayName("Adversarial API Pointage : Rejet de pointage pour employé non affecté sur le site (HTTP 409 Conflict)")
    public void testRejetPointageEmployeNonAffecteSurSite() throws Exception {
        LocalDate hier = LocalDate.now().minusDays(1);

        // L'employé est affecté au site 1. On tente un pointage sur le site 2 (Toamasina) via API
        SaisiePointageJourDTO dto = new SaisiePointageJourDTO();
        dto.setIdEmploye(idEmploye);
        dto.setIdSite(2); // Site non concordant
        dto.setDatePointage(hier.toString());
        dto.setHeureEntreeMatin("08:00");
        dto.setHeureSortieMatin("12:00");
        dto.setHeureEntreeAm("13:00");
        dto.setHeureSortieAm("17:00");

        mockMvc.perform(post("/pointages/saisie/batch")
                .header("Authorization", "Bearer " + getPointageToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(List.of(dto))))
                .andExpect(status().isConflict());
    }
}
