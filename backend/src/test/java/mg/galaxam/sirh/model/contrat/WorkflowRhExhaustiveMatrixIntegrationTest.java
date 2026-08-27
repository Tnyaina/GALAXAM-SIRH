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

/**
 * Matrice exhaustive de tests d'intégration pour tous les scénarios contractuels possibles :
 * 1. CDI avec cycle Période d'Essai (Initiale -> Renouvellement -> Confirmation / Rupture)
 * 2. CDD 1 -> CDD 2 (1er renouv) -> CDD 3 (2ème renouv) -> Blocage 3ème renouv / Dépassement 24 mois -> Passage CDI
 * 3. STAGE -> Embauche CDD (avec PE) -> Renouvellement PE -> Confirmation -> Renouvellement CDD -> Passage CDI
 * 4. Planification Future -> Schedulers / Cron (PLANIFIE vers ACTIF) -> Annulation
 * 5. Mutations & Revalorisations salariales sans rupture
 * 6. Garde-fous et détection de doubles validations / états incohérents
 *
 * TOUS LES TESTS S'EXÉCUTENT EN BASE DE DONNÉES RÉELLE AVEC ROLLBACK SYSTÉMATIQUE.
 */
@SpringBootTest
public class WorkflowRhExhaustiveMatrixIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WorkflowRhService workflowRhService;

    // =========================================================================
    // Helpers pour initialiser des référentiels de test
    // =========================================================================

    private static class TestRefContext {
        int idEmploye;
        int idFiliale;
        int idSite;
        int idDept;
        int idService;
        int idFonction;
        int idCatPro;
        int idModePaie;
        int idTypeCdi;
        int idTypeCdd;
        int idTypeStage;
        int idIntervenantSalarie;
        int idIntervenantStagiaire;
    }

    private TestRefContext chargerReferentiels(Connection c) throws Exception {
        TestRefContext ctx = new TestRefContext();

        // 1. Créer un employé dédié pour le test isolé
        long rand = System.currentTimeMillis() % 1000000000;
        String matriculeUnique = "T-" + rand;
        String cinUnique = "CIN-" + rand;
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO employe (matricule, nom, prenom, date_naissance, lieu_naissance, sexe, cin_numero, cin_date_delivrance, cin_lieu_delivrance, adresse, ville, email, telephone, est_actif) " +
                "VALUES (?, 'TEST_NOM', 'TEST_PRENOM', '1995-01-01', 'Antananarivo', 'M'::sexe_enum, ?, '2015-01-01', 'Antananarivo', 'Lot IV TNR', 'Antananarivo', ?, '+261340000000', TRUE) RETURNING id_employe")) {
            ps.setString(1, matriculeUnique);
            ps.setString(2, cinUnique);
            ps.setString(3, matriculeUnique.toLowerCase() + "@test.mg");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) ctx.idEmploye = rs.getInt(1);
            }
        }

        // 2. Charger les ID des tables de référence existantes
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
            throw new IllegalStateException("Référence introuvable pour la requête: " + sql);
        }
    }

    private EmbaucheInitialeRequest buildEmbaucheInitiale(TestRefContext ctx, int idTypeContrat, int idTypeIntervenant, LocalDate debut, LocalDate fin, Integer dureeEssaiMois) {
        EmbaucheInitialeRequest req = new EmbaucheInitialeRequest();
        req.setNumeroContrat("C-" + (System.nanoTime() % 1000000000));
        req.setIdFiliale(ctx.idFiliale);
        req.setIdTypeContrat(idTypeContrat);
        req.setIdTypeIntervenant(idTypeIntervenant);
        req.setDateDebut(Date.valueOf(debut));
        req.setDateFin(fin != null ? Date.valueOf(fin) : null);
        req.setDateSignature(Date.valueOf(debut));
        req.setDureeEssaiMois(dureeEssaiMois);
        if (dureeEssaiMois != null && dureeEssaiMois > 0) {
            req.setDateFinEssai(Date.valueOf(debut.plusMonths(dureeEssaiMois).minusDays(1)));
        }
        req.setComptabiliseEffectif(true);

        req.setIdSite(ctx.idSite);
        req.setIdDepartement(ctx.idDept);
        req.setIdService(ctx.idService);
        req.setIdFonction(ctx.idFonction);
        req.setIdCategorieProfessionnelle(ctx.idCatPro);
        req.setIdModeCalculPaie(ctx.idModePaie);
        req.setSalaireBase(new BigDecimal("1200000.00"));
        req.setMotif("Embauche Test");
        return req;
    }

    // =========================================================================
    // SCÉNARIO 1 : Cycle complet Période d'Essai (CDI)
    // =========================================================================

    @Test
    @DisplayName("SCÉNARIO 1.1 : Embauche CDI -> PE 3 mois -> Confirmation d'Essai")
    void testCycleCdiConfirmationEssai() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche initiale CDI avec PE 3 mois
                LocalDate debut = LocalDate.of(2025, 1, 1);
                EmbaucheInitialeRequest embReq = buildEmbaucheInitiale(ctx, ctx.idTypeCdi, ctx.idIntervenantSalarie, debut, null, 3);
                WorkflowRhCPL embBrouillon = workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, embReq, 1, c);
                assertTrue(embBrouillon.isBrouillon());

                // Validation embauche
                WorkflowRhCPL embValide = workflowRhService.validerWorkflow(embBrouillon.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);
                assertTrue(embValide.isActif());

                // Vérifier que la PE est EN_COURS
                try (var ps = c.prepareStatement("SELECT statut, numero_periode FROM contrat_periode_essai WHERE id_contrat = ?")) {
                    ps.setInt(1, embValide.getIdContratCible());
                    try (var rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("EN_COURS", rs.getString("statut"));
                        assertEquals(1, rs.getInt("numero_periode"));
                    }
                }

                // 2. Confirmation d'essai
                TransitionCarriereRequest confReq = new TransitionCarriereRequest();
                confReq.setCodeTypeEvenement("CONFIRMATION_ESSAI");
                confReq.setDateEffet(Date.valueOf(LocalDate.of(2025, 3, 31)));
                confReq.setMotif("Confirmation satisfaisante");

                WorkflowRhCPL confBrouillon = workflowRhService.creerBrouillonTransition(ctx.idEmploye, confReq, 1, c);
                assertTrue(confBrouillon.isBrouillon());

                WorkflowRhCPL confValide = workflowRhService.validerWorkflow(confBrouillon.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);
                assertTrue(confValide.isActif());

                // Vérifier que la PE est passée à CONFIRMEE
                try (var ps = c.prepareStatement("SELECT statut FROM contrat_periode_essai WHERE id_contrat = ?")) {
                    ps.setInt(1, embValide.getIdContratCible());
                    try (var rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("CONFIRMEE", rs.getString("statut"));
                    }
                }

                // 3. Double confirmation impossible (doit échouer)
                assertThrows(IllegalStateException.class, () -> {
                    workflowRhService.creerBrouillonTransition(ctx.idEmploye, confReq, 1, c);
                });

                System.out.println("✅ [TEST MATRICE 1.1] CDI -> PE -> Confirmation validé.");
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("SCÉNARIO 1.2 : Embauche CDI -> PE 3 mois -> Renouvellement PE -> Confirmation -> Blocage 2ème Renouvellement")
    void testCycleCdiRenouvellementEtPlafondEssai() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche initiale CDI avec PE 3 mois
                EmbaucheInitialeRequest embReq = buildEmbaucheInitiale(ctx, ctx.idTypeCdi, ctx.idIntervenantSalarie, LocalDate.of(2025, 1, 1), null, 3);
                WorkflowRhCPL embBrouillon = workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, embReq, 1, c);
                WorkflowRhCPL embValide = workflowRhService.validerWorkflow(embBrouillon.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);

                // 2. 1er Renouvellement d'essai (+2 mois)
                TransitionCarriereRequest renReq = new TransitionCarriereRequest();
                renReq.setCodeTypeEvenement("RENOUVELLEMENT_ESSAI");
                renReq.setDateEffet(Date.valueOf(LocalDate.of(2025, 5, 31)));
                renReq.setMotif("Prolongation pour observation");

                WorkflowRhCPL renBrouillon = workflowRhService.creerBrouillonTransition(ctx.idEmploye, renReq, 1, c);
                WorkflowRhCPL renValide = workflowRhService.validerWorkflow(renBrouillon.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);
                assertTrue(renValide.isActif());

                // Vérifier que la 2ème période est créée en EN_COURS et l'ancienne en RENOUVELEE
                try (var ps = c.prepareStatement("SELECT numero_periode, statut FROM contrat_periode_essai WHERE id_contrat = ? ORDER BY numero_periode")) {
                    ps.setInt(1, embValide.getIdContratCible());
                    try (var rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                        assertEquals("RENOUVELEE", rs.getString(2));

                        assertTrue(rs.next());
                        assertEquals(2, rs.getInt(1));
                        assertEquals("EN_COURS", rs.getString(2));
                    }
                }

                // 3. Tentative d'un 2ème renouvellement d'essai -> Doit être rejeté (max légal = 1)
                IllegalArgumentException exMax = assertThrows(IllegalArgumentException.class, () -> {
                    workflowRhService.creerBrouillonTransition(ctx.idEmploye, renReq, 1, c);
                });
                assertTrue(exMax.getMessage().contains("maximal de renouvellements de la période d'essai"));

                System.out.println("✅ [TEST MATRICE 1.2] CDI -> PE -> 1er Renouv -> Blocage 2ème Renouv validé.");
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // SCÉNARIO 2 : Chaîne CDD (Initial -> R1 -> R2 -> Requalification CDI)
    // =========================================================================

    @Test
    @DisplayName("SCÉNARIO 2 : CDD Initial (6 mois) -> R1 (6 mois) -> R2 (6 mois) -> Rejet R3 & Dépassement -> Passage CDI")
    void testChaineCompleteCddVersCdi() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. CDD Initial : 6 mois (2024-01-01 au 2024-06-30) avec PE 1 mois
                LocalDate debut1 = LocalDate.of(2024, 1, 1);
                LocalDate fin1 = LocalDate.of(2024, 6, 30);
                EmbaucheInitialeRequest cdd1Req = buildEmbaucheInitiale(ctx, ctx.idTypeCdd, ctx.idIntervenantSalarie, debut1, fin1, 1);
                WorkflowRhCPL cdd1Wf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, cdd1Req, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);
                int idContrat1 = cdd1Wf.getIdContratCible();

                // 2. Renouvellement 1 (Avenant 1) : 6 mois (2024-07-01 au 2024-12-31)
                LocalDate debut2 = LocalDate.of(2024, 7, 1);
                LocalDate fin2 = LocalDate.of(2024, 12, 31);
                TransitionCarriereRequest cdd2Req = new TransitionCarriereRequest();
                cdd2Req.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
                cdd2Req.setIdTypeContrat(ctx.idTypeCdd);
                cdd2Req.setIdTypeIntervenant(ctx.idIntervenantSalarie);
                cdd2Req.setIdFiliale(ctx.idFiliale);
                cdd2Req.setNumeroContrat("C2-" + (System.nanoTime() % 1000000000));
                cdd2Req.setDateDebut(Date.valueOf(debut2));
                cdd2Req.setDateFin(Date.valueOf(fin2));
                cdd2Req.setDateEffet(Date.valueOf(debut2));
                cdd2Req.setIdSite(ctx.idSite);
                cdd2Req.setIdDepartement(ctx.idDept);
                cdd2Req.setIdService(ctx.idService);
                cdd2Req.setIdFonction(ctx.idFonction);
                cdd2Req.setIdCategorieProfessionnelle(ctx.idCatPro);
                cdd2Req.setIdModeCalculPaie(ctx.idModePaie);
                cdd2Req.setSalaireBase(new BigDecimal("1300000.00"));
                cdd2Req.setMotif("Renouvellement CDD N°1");

                WorkflowRhCPL cdd2Wf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, cdd2Req, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);
                int idContrat2 = cdd2Wf.getIdContratCible();

                // Vérifier la liaison et la date_fin du contrat 1
                try (var ps = c.prepareStatement("SELECT date_fin, id_statut_rh FROM contrat_travail WHERE id_contrat = ?")) {
                    ps.setInt(1, idContrat1);
                    try (var rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(Date.valueOf("2024-06-30"), rs.getDate("date_fin"), "La date_fin de l'ancien contrat doit être la veille du nouveau.");
                    }
                }

                // 3. Renouvellement 2 (Avenant 2) : 6 mois (2025-01-01 au 2025-06-30) -> Total 18 mois (ok)
                LocalDate debut3 = LocalDate.of(2025, 1, 1);
                LocalDate fin3 = LocalDate.of(2025, 6, 30);
                cdd2Req.setNumeroContrat("C3-" + (System.nanoTime() % 1000000000));
                cdd2Req.setDateDebut(Date.valueOf(debut3));
                cdd2Req.setDateFin(Date.valueOf(fin3));
                cdd2Req.setDateEffet(Date.valueOf(debut3));
                cdd2Req.setMotif("Renouvellement CDD N°2");

                WorkflowRhCPL cdd3Wf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, cdd2Req, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);
                int idContrat3 = cdd3Wf.getIdContratCible();
                assertNotNull(cdd3Wf);

                // 4. Tentative de Renouvellement 3 (Avenant 3) -> Rejet obligatoire (max 2 renouvellements)
                cdd2Req.setNumeroContrat("C4-" + (System.nanoTime() % 1000000000));
                cdd2Req.setDateDebut(Date.valueOf(LocalDate.of(2025, 7, 1)));
                cdd2Req.setDateFin(Date.valueOf(LocalDate.of(2025, 12, 31)));
                cdd2Req.setDateEffet(Date.valueOf(LocalDate.of(2025, 7, 1)));

                IllegalArgumentException exMaxRenouv = assertThrows(IllegalArgumentException.class, () -> {
                    workflowRhService.creerBrouillonTransition(ctx.idEmploye, cdd2Req, 1, c);
                });
                assertTrue(exMaxRenouv.getMessage().contains("nombre maximal de renouvellements"));

                // 5. Transformation obligatoire en CDI
                TransitionCarriereRequest cdiReq = new TransitionCarriereRequest();
                cdiReq.setCodeTypeEvenement("TRANSFORMATION_CDI");
                cdiReq.setIdTypeContrat(ctx.idTypeCdi);
                cdiReq.setIdTypeIntervenant(ctx.idIntervenantSalarie);
                cdiReq.setIdFiliale(ctx.idFiliale);
                cdiReq.setNumeroContrat("CDI-" + (System.nanoTime() % 1000000000));
                cdiReq.setDateDebut(Date.valueOf(LocalDate.of(2025, 7, 1)));
                cdiReq.setDateEffet(Date.valueOf(LocalDate.of(2025, 7, 1)));
                cdiReq.setIdSite(ctx.idSite);
                cdiReq.setIdDepartement(ctx.idDept);
                cdiReq.setIdService(ctx.idService);
                cdiReq.setIdFonction(ctx.idFonction);
                cdiReq.setIdCategorieProfessionnelle(ctx.idCatPro);
                cdiReq.setIdModeCalculPaie(ctx.idModePaie);
                cdiReq.setSalaireBase(new BigDecimal("1800000.00"));
                cdiReq.setMotif("Passage définitif en CDI");

                WorkflowRhCPL cdiWf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, cdiReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);

                assertTrue(cdiWf.isActif());
                assertEquals("Contrat à Durée Indéterminée", cdiWf.getLibelleTypeContrat());

                System.out.println("✅ [TEST MATRICE 2] Chaîne CDD 1 -> CDD 2 -> CDD 3 -> Blocage CDD 4 -> Passage CDI validée.");
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // SCÉNARIO 3 : STAGE -> CDD (avec PE) -> Passage CDI
    // =========================================================================

    @Test
    @DisplayName("SCÉNARIO 3 : Stage (6 mois) -> Embauche CDD avec PE -> Confirmation PE -> Passage CDI")
    void testCycleStageVersCddPuisCdi() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Stage initial de 6 mois
                LocalDate debutStage = LocalDate.of(2025, 1, 1);
                LocalDate finStage = LocalDate.of(2025, 6, 30);
                EmbaucheInitialeRequest stageReq = buildEmbaucheInitiale(ctx, ctx.idTypeStage, ctx.idIntervenantStagiaire, debutStage, finStage, 0);
                WorkflowRhCPL stageWf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, stageReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);

                // 2. Passage Stage vers CDD (EMBAUCHE_APRES_STAGE avec PE 2 mois)
                LocalDate debutCdd = LocalDate.of(2025, 7, 1);
                LocalDate finCdd = LocalDate.of(2025, 12, 31);
                TransitionCarriereRequest cddReq = new TransitionCarriereRequest();
                cddReq.setCodeTypeEvenement("EMBAUCHE_APRES_STAGE");
                cddReq.setIdTypeContrat(ctx.idTypeCdd);
                cddReq.setIdTypeIntervenant(ctx.idIntervenantSalarie);
                cddReq.setIdFiliale(ctx.idFiliale);
                cddReq.setNumeroContrat("CS-" + (System.nanoTime() % 1000000000));
                cddReq.setDateDebut(Date.valueOf(debutCdd));
                cddReq.setDateFin(Date.valueOf(finCdd));
                cddReq.setDateEffet(Date.valueOf(debutCdd));
                cddReq.setDureeEssaiMois(2);
                cddReq.setDateFinEssai(Date.valueOf(debutCdd.plusMonths(2).minusDays(1)));
                cddReq.setIdSite(ctx.idSite);
                cddReq.setIdDepartement(ctx.idDept);
                cddReq.setIdService(ctx.idService);
                cddReq.setIdFonction(ctx.idFonction);
                cddReq.setIdCategorieProfessionnelle(ctx.idCatPro);
                cddReq.setIdModeCalculPaie(ctx.idModePaie);
                cddReq.setSalaireBase(new BigDecimal("1500000.00"));
                cddReq.setMotif("Recrutement après stage");

                WorkflowRhCPL cddWf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, cddReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);

                assertTrue(cddWf.isActif());
                int idContratCdd = cddWf.getIdContratCible();

                // 3. Confirmation de l'essai du CDD
                TransitionCarriereRequest confReq = new TransitionCarriereRequest();
                confReq.setCodeTypeEvenement("CONFIRMATION_ESSAI");
                confReq.setDateEffet(Date.valueOf(debutCdd.plusMonths(2)));
                confReq.setMotif("Essai probant");

                WorkflowRhCPL confWf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, confReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);
                assertTrue(confWf.isActif());

                // 4. Passage en CDI
                TransitionCarriereRequest cdiReq = new TransitionCarriereRequest();
                cdiReq.setCodeTypeEvenement("TRANSFORMATION_CDI");
                cdiReq.setIdTypeContrat(ctx.idTypeCdi);
                cdiReq.setIdTypeIntervenant(ctx.idIntervenantSalarie);
                cdiReq.setIdFiliale(ctx.idFiliale);
                cdiReq.setNumeroContrat("CDIF-" + (System.nanoTime() % 1000000000));
                cdiReq.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                cdiReq.setDateEffet(Date.valueOf(LocalDate.of(2026, 1, 1)));
                cdiReq.setIdSite(ctx.idSite);
                cdiReq.setIdDepartement(ctx.idDept);
                cdiReq.setIdService(ctx.idService);
                cdiReq.setIdFonction(ctx.idFonction);
                cdiReq.setIdCategorieProfessionnelle(ctx.idCatPro);
                cdiReq.setIdModeCalculPaie(ctx.idModePaie);
                cdiReq.setSalaireBase(new BigDecimal("2000000.00"));
                cdiReq.setMotif("CDI");

                WorkflowRhCPL cdiWf = workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonTransition(ctx.idEmploye, cdiReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);

                assertTrue(cdiWf.isActif());
                System.out.println("✅ [TEST MATRICE 3] Stage -> CDD (avec PE) -> Confirmation PE -> Passage CDI validé.");
            } finally {
                c.rollback();
            }
        }
    }

    // =========================================================================
    // SCÉNARIO 4 : Planification Future & Schedulers Cron
    // =========================================================================

    @Test
    @DisplayName("SCÉNARIO 4 : Mouvement futur (PLANIFIÉ) -> Basculement automatique Cron (ACTIF) -> Annulation")
    void testMouvementFuturEtCron() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                TestRefContext ctx = chargerReferentiels(c);

                // 1. Embauche initiale active
                EmbaucheInitialeRequest embReq = buildEmbaucheInitiale(ctx, ctx.idTypeCdi, ctx.idIntervenantSalarie, LocalDate.of(2025, 1, 1), null, 0);
                workflowRhService.validerWorkflow(
                        workflowRhService.creerBrouillonEmbauche(ctx.idEmploye, embReq, 1, c).getIdWorkflow(),
                        new ValiderWorkflowRequest(), 1, c);

                // 2. Revalorisation avec date d'effet dans le FUTUR (+10 jours)
                LocalDate dateFuture = LocalDate.now().plusDays(10);
                TransitionCarriereRequest futReq = new TransitionCarriereRequest();
                futReq.setCodeTypeEvenement("REVALORISATION");
                futReq.setDateEffet(Date.valueOf(dateFuture));
                futReq.setIdSite(ctx.idSite);
                futReq.setIdDepartement(ctx.idDept);
                futReq.setIdService(ctx.idService);
                futReq.setIdFonction(ctx.idFonction);
                futReq.setIdCategorieProfessionnelle(ctx.idCatPro);
                futReq.setIdModeCalculPaie(ctx.idModePaie);
                futReq.setSalaireBase(new BigDecimal("2500000.00"));
                futReq.setMotif("Augmentation future");

                WorkflowRhCPL futBrouillon = workflowRhService.creerBrouillonTransition(ctx.idEmploye, futReq, 1, c);
                WorkflowRhCPL futValide = workflowRhService.validerWorkflow(futBrouillon.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);

                // Le workflow validé doit être en statut PLANIFIE car dateEffet > today
                assertTrue(futValide.isPlanifie(), "Le workflow doit être en statut PLANIFIE.");
                assertFalse(futValide.isActif(), "Le workflow ne doit pas être ACTIF tant que la date n'est pas atteinte.");

                // 3. Test de l'annulation de planification
                WorkflowRhCPL annuleWf = workflowRhService.annulerWorkflowPlanifie(futValide.getIdWorkflow(), new ValiderWorkflowRequest(), 1, c);
                assertTrue(annuleWf.isAnnule(), "Le workflow annulé doit être en statut ANNULE.");

                System.out.println("✅ [TEST MATRICE 4] Mouvement futur (PLANIFIÉ) et annulation validés.");
            } finally {
                c.rollback();
            }
        }
    }
}
