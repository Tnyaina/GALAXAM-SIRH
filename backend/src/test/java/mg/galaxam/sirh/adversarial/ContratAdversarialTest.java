package mg.galaxam.sirh.adversarial;

import mg.galaxam.sirh.BaseIntegrationTest;
import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.model.contrat.WorkflowRhService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

public class ContratAdversarialTest extends BaseIntegrationTest {

    @Autowired
    private WorkflowRhService workflowRhService;

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
    @DisplayName("Adversarial Contrat : Rejet d'une 2nde embauche initiale pour un employé ayant déjà un contrat ACTIF")
    public void testRejetDoubleContratActif() {
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

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    workflowRhService.creerBrouillonEmbauche(idEmploye, req, 1, c));
            assertTrue(ex.getMessage().contains("possède déjà un contrat ACTIF"),
                    "Le message d'erreur doit signaler la présence d'un contrat actif");
        } catch (Exception e) {
            fail("Erreur inattendue : " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Adversarial Contrat : Rejet d'un renouvellement CDD excédant le plafond légal de 24 mois")
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

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    workflowRhService.creerBrouillonTransition(idEmploye, req, 1, c));
            assertTrue(ex.getMessage().contains("24 mois"),
                    "L'exception doit expliciter le plafond de 24 mois du Code du travail malgache");
        }
    }

    @Test
    @DisplayName("Adversarial Contrat : Rejet d'un renouvellement CDD d'une durée inférieure au minimum légal de 1 mois")
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

        try (Connection c = dataSource.getConnection()) {
            Exception ex = assertThrows(IllegalArgumentException.class, () ->
                    workflowRhService.creerBrouillonTransition(idEmploye, req, 1, c));
            assertTrue(ex.getMessage().contains("durée minimale d'un mois"),
                    "L'exception doit mentionner la durée minimale de 1 mois");
        }
    }

    @Test
    @DisplayName("Adversarial Contrat : Rejet d'une transition sans contrat source spécifié")
    public void testRejetTransitionSansContratSource() {
        TransitionCarriereRequest req = new TransitionCarriereRequest();
        req.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
        req.setIdContratSource(null); // Contrat source manquant
        req.setIdFiliale(1);
        req.setIdTypeContrat(2);
        req.setIdTypeIntervenant(1);
        req.setDateDebut(Date.valueOf(LocalDate.now()));
        req.setDateFin(Date.valueOf(LocalDate.now().plusMonths(6)));

        try (Connection c = dataSource.getConnection()) {
            assertThrows(IllegalArgumentException.class, () ->
                    workflowRhService.creerBrouillonTransition(idEmploye, req, 1, c));
        } catch (Exception e) {
            fail("Erreur inattendue : " + e.getMessage());
        }
    }
}
