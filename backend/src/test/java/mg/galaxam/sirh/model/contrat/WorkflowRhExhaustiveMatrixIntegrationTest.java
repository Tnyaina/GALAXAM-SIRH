package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;

import mg.galaxam.sirh.api.dto.contrat.EmbaucheInitialeRequest;
import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WorkflowRhExhaustiveMatrixIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WorkflowRhService workflowRhService;

    // =========================================================================
    // Helpers
    // =========================================================================

    private static class TestRefContext {
        int idEmploye;
        int idFiliale, idSite, idDept, idService, idFonction, idCatPro, idModePaie;
        int idTypeCdi, idTypeCdd, idTypeStage;
        int idIntervenantSalarie, idIntervenantStagiaire;
    }

    private TestRefContext chargerReferentiels(Connection c) throws Exception {
        TestRefContext ctx = new TestRefContext();
        long rand = System.currentTimeMillis() % 1000000;
        String mat = "QA-" + rand;
        
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employe (matricule, nom, prenom, date_naissance, lieu_naissance, sexe, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, telephone) " +
                "VALUES (?, 'TEST', 'QA', '1990-01-01', 'TNR', 'M'::sexe_enum, ?, '2010-01-01', 'TNR', 'TNR', '+261340000000') RETURNING id_employe")) {
            ps.setString(1, mat);
            ps.setString(2, "CIN-" + rand);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) ctx.idEmploye = rs.getInt(1);
            }
        }

        ctx.idFiliale = queryId(c, "SELECT id_filiale FROM ref_filiale LIMIT 1");
        ctx.idSite = queryId(c, "SELECT id_site FROM ref_site LIMIT 1");
        ctx.idDept = queryId(c, "SELECT id_departement FROM ref_departement LIMIT 1");
        ctx.idService = queryId(c, "SELECT id_service FROM ref_service LIMIT 1");
        ctx.idFonction = queryId(c, "SELECT id_fonction FROM ref_fonction LIMIT 1");
        ctx.idCatPro = queryId(c, "SELECT id_categorie_professionnelle FROM ref_categorie_professionnelle LIMIT 1");
        ctx.idModePaie = queryId(c, "SELECT id_mode_calcul_paie FROM ref_mode_calcul_paie LIMIT 1");
        ctx.idTypeCdi = queryId(c, "SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDI'");
        ctx.idTypeCdd = queryId(c, "SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'");
        ctx.idTypeStage = queryId(c, "SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'STAGE'");
        ctx.idIntervenantSalarie = queryId(c, "SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'PERMANENT'");
        ctx.idIntervenantStagiaire = queryId(c, "SELECT id_type_intervenant FROM ref_type_intervenant WHERE code = 'STAGIAIRE'");

        return ctx;
    }

    private int queryId(Connection c, String sql) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
            throw new IllegalStateException("Ref introuvable : " + sql);
        }
    }

    // =========================================================================
    // TESTS EXHAUSTIFS
    // =========================================================================

    @Test
    @DisplayName("MATRICE 1 : CDI -> PE -> Renouvellement -> Confirmation -> Double Confirmation (Fail)")
    void testMatriceCdiEssai() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche CDI avec PE (3 mois)
                EmbaucheInitialeRequest emb = new EmbaucheInitialeRequest();
                emb.setNumeroContrat("CDI-M1"); emb.setIdFiliale(ctx.idFiliale); emb.setIdTypeContrat(ctx.idTypeCdi);
                emb.setIdTypeIntervenant(ctx.idIntervenantSalarie); emb.setDateDebut(Date.valueOf("2026-01-01"));
                emb.setDureeEssaiMois(3); emb.setDateFinEssai(Date.valueOf("2026-03-31"));
                emb.setIdSite(ctx.idSite); emb.setIdDepartement(ctx.idDept); emb.setIdService(ctx.idService);
                emb.setIdFonction(ctx.idFonction); emb.setIdCategorieProfessionnelle(ctx.idCatPro);
                emb.setIdModeCalculPaie(ctx.idModePaie); emb.setSalaireBase(new BigDecimal("1000000"));
                
                WorkflowRhCPL wfEmb = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, emb, 1, c).getIdWorkflow(), 
                        new ValiderWorkflowRequest(), 1, c);

                // 2. Renouvellement PE
                TransitionCarriereRequest renouv = new TransitionCarriereRequest();
                renouv.setCodeTypeEvenement("RENOUVELLEMENT_ESSAI");
                renouv.setDateEffet(Date.valueOf("2026-04-01"));
                workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, renouv, 1, c).getIdWorkflow(), 
                        new ValiderWorkflowRequest(), 1, c);

                // 3. Confirmation
                TransitionCarriereRequest conf = new TransitionCarriereRequest();
                conf.setCodeTypeEvenement("CONFIRMATION_ESSAI");
                conf.setDateEffet(Date.valueOf("2026-05-01"));
                workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, conf, 1, c).getIdWorkflow(), 
                        new ValiderWorkflowRequest(), 1, c);

                // 4. Double Confirmation (DOIT ECHOUER)
                Exception e = assertThrows(IllegalStateException.class, () -> {
                    workflowRhService.creerBrouillonTransition(ctx.idEmploye, conf, 1, c);
                });
                assertTrue(e.getMessage().contains("déjà été confirmée"), "Double confirmation bloquée.");

            } finally { c.rollback(); }
        }
    }

    @Test
    @DisplayName("MATRICE 2 : Limites CDD (24 mois, 2 renouvellements) & PE interdite")
    void testMatriceLimitesCdd() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche CDD 12 mois
                EmbaucheInitialeRequest emb = new EmbaucheInitialeRequest();
                emb.setNumeroContrat("CDD-M2-1"); emb.setIdFiliale(ctx.idFiliale); emb.setIdTypeContrat(ctx.idTypeCdd);
                emb.setIdTypeIntervenant(ctx.idIntervenantSalarie); emb.setDateDebut(Date.valueOf("2025-01-01"));
                emb.setDateFin(Date.valueOf("2025-12-31")); emb.setDureeEssaiMois(1);
                emb.setIdSite(ctx.idSite); emb.setIdDepartement(ctx.idDept); emb.setIdService(ctx.idService);
                emb.setIdFonction(ctx.idFonction); emb.setIdCategorieProfessionnelle(ctx.idCatPro);
                emb.setIdModeCalculPaie(ctx.idModePaie); emb.setSalaireBase(new BigDecimal("1000000"));
                
                workflowRhService.validerWorkflow(workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, emb, 1, c).getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);

                // 2. Renouvellement 1 (6 mois) AVEC PE (DOIT ECHOUER)
                TransitionCarriereRequest renouv1 = new TransitionCarriereRequest();
                renouv1.setCodeTypeEvenement("RENOUVELLEMENT_CDD"); renouv1.setIdTypeContrat(ctx.idTypeCdd);
                renouv1.setIdTypeIntervenant(ctx.idIntervenantSalarie); renouv1.setIdFiliale(ctx.idFiliale);
                renouv1.setDateDebut(Date.valueOf("2026-01-01")); renouv1.setDateFin(Date.valueOf("2026-06-30"));
                renouv1.setDateEffet(Date.valueOf("2026-01-01")); renouv1.setDureeEssaiMois(1); // FAUTE LÉGALE
                renouv1.setIdSite(ctx.idSite); renouv1.setIdDepartement(ctx.idDept); renouv1.setIdService(ctx.idService);
                renouv1.setIdFonction(ctx.idFonction); renouv1.setIdCategorieProfessionnelle(ctx.idCatPro);
                renouv1.setIdModeCalculPaie(ctx.idModePaie); renouv1.setSalaireBase(new BigDecimal("1000000"));

                Exception e1 = assertThrows(IllegalArgumentException.class, () -> workflowRhService.creerBrouillonTransition(ctx.idEmploye, renouv1, 1, c));
                assertTrue(e1.getMessage().contains("strictement interdite"), "PE sur renouvellement CDD bloquée.");

                // 3. Renouvellement 1 Valide (6 mois)
                renouv1.setDureeEssaiMois(0);
                workflowRhService.validerWorkflow(workflowRhService.creerBrouillonTransition(ctx.idEmploye, renouv1, 1, c).getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);

                // 4. Renouvellement 2 (8 mois) -> Total 26 mois (DOIT ECHOUER DUREE)
                TransitionCarriereRequest renouv2 = new TransitionCarriereRequest();
                renouv2.setCodeTypeEvenement("RENOUVELLEMENT_CDD"); renouv2.setIdTypeContrat(ctx.idTypeCdd);
                renouv2.setIdTypeIntervenant(ctx.idIntervenantSalarie); renouv2.setIdFiliale(ctx.idFiliale);
                renouv2.setDateDebut(Date.valueOf("2026-07-01")); renouv2.setDateFin(Date.valueOf("2027-02-28"));
                renouv2.setDateEffet(Date.valueOf("2026-07-01")); renouv2.setDureeEssaiMois(0);
                renouv2.setIdSite(ctx.idSite); renouv2.setIdDepartement(ctx.idDept); renouv2.setIdService(ctx.idService);
                renouv2.setIdFonction(ctx.idFonction); renouv2.setIdCategorieProfessionnelle(ctx.idCatPro);
                renouv2.setIdModeCalculPaie(ctx.idModePaie); renouv2.setSalaireBase(new BigDecimal("1000000"));

                Exception e2 = assertThrows(IllegalArgumentException.class, () -> workflowRhService.creerBrouillonTransition(ctx.idEmploye, renouv2, 1, c));
                assertTrue(e2.getMessage().contains("dépasse le plafond légal de 24 mois"), "Plafond durée 24 mois bloqué.");

            } finally { c.rollback(); }
        }
    }

    @Test
    @DisplayName("MATRICE 3 : Scheduler Planifié & Annulation")
    void testMatricePlanificationCron() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche initiale
                EmbaucheInitialeRequest emb = new EmbaucheInitialeRequest();
                emb.setNumeroContrat("CDI-M3"); emb.setIdFiliale(ctx.idFiliale); emb.setIdTypeContrat(ctx.idTypeCdi);
                emb.setIdTypeIntervenant(ctx.idIntervenantSalarie); emb.setDateDebut(Date.valueOf(LocalDate.now().minusDays(10)));
                emb.setIdSite(ctx.idSite); emb.setIdDepartement(ctx.idDept); emb.setIdService(ctx.idService);
                emb.setIdFonction(ctx.idFonction); emb.setIdCategorieProfessionnelle(ctx.idCatPro);
                emb.setIdModeCalculPaie(ctx.idModePaie); emb.setSalaireBase(new BigDecimal("1000000"));
                
                workflowRhService.validerWorkflow(workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, emb, 1, c).getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);

                // 2. Promotion Future (J+5)
                TransitionCarriereRequest promo = new TransitionCarriereRequest();
                promo.setCodeTypeEvenement("PROMOTION");
                promo.setDateEffet(Date.valueOf(LocalDate.now().plusDays(5)));
                promo.setIdSite(ctx.idSite); promo.setIdDepartement(ctx.idDept); promo.setIdService(ctx.idService);
                promo.setIdFonction(ctx.idFonction); promo.setIdCategorieProfessionnelle(ctx.idCatPro);
                promo.setIdModeCalculPaie(ctx.idModePaie); promo.setSalaireBase(new BigDecimal("2000000"));

                WorkflowRhCPL wfPromo = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, promo, 1, c).getIdWorkflow(), 
                        new ValiderWorkflowRequest(), 1, c);
                
                assertTrue(wfPromo.isPlanifie(), "Le workflow doit être PLANIFIE");

                // 3. Annulation du planifié
                WorkflowRhCPL annuleWf = workflowRhService.annulerWorkflowPlanifie(wfPromo.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);
                assertTrue(annuleWf.isAnnule(), "L'annulation d'un planifié est effective.");

            } finally { c.rollback(); }
        }
    }
}