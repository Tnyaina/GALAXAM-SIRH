package mg.galaxam.sirh.adversarial;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

public class ContratAdversarialTest extends BaseIntegrationTest {

    private int idEmploye;
    private int idContratActif;

    @BeforeEach
    public void setUp() {
        databaseCleaner.cleanTransactionalData();
        var emp = employeFixtures.createActiveEmploye("EMP-ADV-CTR", "RAKOTO", "Jean", 1);
        idEmploye = emp.idEmploye;
        idContratActif = emp.idContrat;
    }

    @Test
    @DisplayName("Adversarial API Contrat : Rejet d'une 2nde embauche initiale pour un employé ayant déjà un contrat ACTIF")
    public void testRejetDoubleContratActif() throws Exception {
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setIdFiliale(1);
        req.setIdTypeContrat(1); // CDI
        req.setIdTypeIntervenant(1);
        req.setDateDebut(Date.valueOf(LocalDate.now()));
        req.setNumeroContrat("CTR-DOUBLON");
        req.setComptabiliseEffectif(true);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdCategorieProfessionnelle(1);
        req.setIdModeCalculPaie(1);
        req.setSalaireBase(BigDecimal.valueOf(800000.00));

        mockMvc.perform(post("/employes/" + idEmploye + "/workflows/embauche-initiale")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("possède déjà un contrat ACTIF")));
    }

    @Test
    @DisplayName("Adversarial API Contrat : Rejet d'un renouvellement CDD excédant le plafond légal de 24 mois")
    public void testRejetCddSuperieurA24Mois() throws Exception {
        // Convertir le contrat actif en CDD
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE contrat_travail SET id_type_contrat = (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), " +
                             "date_fin = CURRENT_DATE + INTERVAL '6 months' WHERE id_contrat = ?")) {
            ps.setInt(1, idContratActif);
            ps.executeUpdate();
        }

        LocalDate debut = LocalDate.now();
        LocalDate finTropLongue = debut.plusMonths(25); // 25 mois > 24 mois

        TransitionCarriereRequest req = new TransitionCarriereRequest();
        req.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
        req.setIdContratSource(idContratActif);
        req.setIdFiliale(1);
        req.setIdTypeContrat(2); // CDD
        req.setIdTypeIntervenant(1);
        req.setDateDebut(Date.valueOf(debut));
        req.setDateFin(Date.valueOf(finTropLongue));
        req.setNumeroContrat("CTR-CDD-ILLEGAL");
        req.setComptabiliseEffectif(true);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdCategorieProfessionnelle(1);
        req.setIdModeCalculPaie(1);
        req.setSalaireBase(BigDecimal.valueOf(800000.00));

        mockMvc.perform(post("/employes/" + idEmploye + "/workflows/transition")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("24 mois")));
    }

    @Test
    @DisplayName("Adversarial API Contrat : Rejet d'un renouvellement CDD d'une durée inférieure au minimum légal de 1 mois")
    public void testRejetCddInferieurA1Mois() throws Exception {
        // Convertir le contrat actif en CDD
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE contrat_travail SET id_type_contrat = (SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'), " +
                             "date_fin = CURRENT_DATE + INTERVAL '6 months' WHERE id_contrat = ?")) {
            ps.setInt(1, idContratActif);
            ps.executeUpdate();
        }

        LocalDate debut = LocalDate.now();
        LocalDate finTropCourte = debut.plusDays(10); // 10 jours < 1 mois

        TransitionCarriereRequest req = new TransitionCarriereRequest();
        req.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
        req.setIdContratSource(idContratActif);
        req.setIdFiliale(1);
        req.setIdTypeContrat(2); // CDD
        req.setIdTypeIntervenant(1);
        req.setDateDebut(Date.valueOf(debut));
        req.setDateFin(Date.valueOf(finTropCourte));
        req.setNumeroContrat("CTR-CDD-COURT");
        req.setComptabiliseEffectif(true);
        req.setIdSite(1);
        req.setIdDepartement(1);
        req.setIdService(1);
        req.setIdFonction(1);
        req.setIdCategorieProfessionnelle(1);
        req.setIdModeCalculPaie(1);
        req.setSalaireBase(BigDecimal.valueOf(800000.00));

        mockMvc.perform(post("/employes/" + idEmploye + "/workflows/transition")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("durée minimale d'un mois")));
    }

    @Test
    @DisplayName("Adversarial API Contrat : Rejet d'une transition sans contrat source spécifié")
    public void testRejetTransitionSansContratSource() throws Exception {
        TransitionCarriereRequest req = new TransitionCarriereRequest();
        req.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
        req.setIdContratSource(null); // Contrat source manquant
        req.setIdFiliale(1);
        req.setIdTypeContrat(2);
        req.setIdTypeIntervenant(1);
        req.setDateDebut(Date.valueOf(LocalDate.now()));
        req.setDateFin(Date.valueOf(LocalDate.now().plusMonths(6)));

        mockMvc.perform(post("/employes/" + idEmploye + "/workflows/transition")
                .header("Authorization", "Bearer " + getAdminToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(req)))
                .andExpect(status().isBadRequest());
    }
}
