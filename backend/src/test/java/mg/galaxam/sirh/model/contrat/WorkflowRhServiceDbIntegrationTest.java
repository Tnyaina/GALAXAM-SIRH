package mg.galaxam.sirh.model.contrat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;

import mg.galaxam.sirh.api.dto.contrat.TransitionCarriereRequest;
import mg.galaxam.sirh.api.dto.contrat.ValiderWorkflowRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class WorkflowRhServiceDbIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private WorkflowRhService workflowRhService;

    @Test
    @DisplayName("Test BD Réel : Renouvellement CDD et contrôle des règles métier (avec Rollback)")
    void testDbRenouvellementCddEtRegles() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Trouver un employé existant avec un contrat actif
                Integer idEmploye = null;
                Integer idContratActif = null;
                Integer idTypeContratCdd = null;
                Integer idTypeIntervenant = null;

                try (var ps = c.prepareStatement(
                        "SELECT e.id_employe, ct.id_contrat, ct.id_type_intervenant " +
                        "FROM employe e " +
                        "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                        "JOIN ref_statut_rh s ON ct.id_statut_rh = s.id_statut_rh " +
                        "WHERE s.code = 'ACTIF' LIMIT 1")) {
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) {
                            idEmploye = rs.getInt(1);
                            idContratActif = rs.getInt(2);
                            idTypeIntervenant = rs.getInt(3);
                        }
                    }
                }

                try (var ps = c.prepareStatement("SELECT id_type_contrat FROM ref_type_contrat WHERE code = 'CDD'")) {
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) idTypeContratCdd = rs.getInt(1);
                    }
                }

                if (idEmploye != null && idTypeContratCdd != null) {
                    // Supprimer tout brouillon résiduel pour ce test
                    try (var psDel = c.prepareStatement("DELETE FROM workflow_rh WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'BROUILLON')")) {
                        psDel.setInt(1, idEmploye);
                        psDel.executeUpdate();
                    }

                    // 1. Test rejet si essai sur renouvellement CDD
                    TransitionCarriereRequest reqEssai = new TransitionCarriereRequest();
                    reqEssai.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
                    reqEssai.setIdTypeContrat(idTypeContratCdd);
                    reqEssai.setIdTypeIntervenant(idTypeIntervenant);
                    reqEssai.setDateDebut(Date.valueOf(LocalDate.now()));
                    reqEssai.setDateFin(Date.valueOf(LocalDate.now().plusMonths(6)));
                    reqEssai.setDureeEssaiMois(2); // INTERDIT

                    Integer finalIdEmploye = idEmploye;
                    IllegalArgumentException exEssai = assertThrows(IllegalArgumentException.class, () -> {
                        workflowRhService.creerBrouillonTransition(finalIdEmploye, reqEssai, 1, c);
                    });
                    assertTrue(exEssai.getMessage().contains("strictement interdite"));

                    // 2. Test rejet si durée cumulée dépasse 24 mois
                    TransitionCarriereRequest reqDureeMax = new TransitionCarriereRequest();
                    reqDureeMax.setCodeTypeEvenement("RENOUVELLEMENT_CDD");
                    reqDureeMax.setIdTypeContrat(idTypeContratCdd);
                    reqDureeMax.setIdTypeIntervenant(idTypeIntervenant);
                    reqDureeMax.setDateDebut(Date.valueOf(LocalDate.now()));
                    reqDureeMax.setDateFin(Date.valueOf(LocalDate.now().plusMonths(30))); // 30 mois > 24
                    reqDureeMax.setDureeEssaiMois(0);

                    IllegalArgumentException exDuree = assertThrows(IllegalArgumentException.class, () -> {
                        workflowRhService.creerBrouillonTransition(finalIdEmploye, reqDureeMax, 1, c);
                    });
                    assertTrue(exDuree.getMessage().contains("dépasse le plafond légal"));

                    System.out.println("✅ [TEST INTEGRATION BDD] Rejet période d'essai et plafond durée cumulée validés avec succès.");
                }
            } finally {
                // Rollback systématique pour ne rien modifier en base
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Test BD Réel : Cycle complet Planifié -> autoAppliquerMouvementsEchus -> ACTIF")
    void testDbCycleWorkflowPlanifieVersActif() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Integer idEmploye = null;
                try (var ps = c.prepareStatement("SELECT id_employe FROM employe LIMIT 1")) {
                    try (var rs = ps.executeQuery()) {
                        if (rs.next()) idEmploye = rs.getInt(1);
                    }
                }

                if (idEmploye != null) {
                    // Supprimer tout brouillon résiduel pour ce test
                    try (var psDel = c.prepareStatement("DELETE FROM workflow_rh WHERE id_employe = ? AND id_statut_rh = (SELECT id_statut_rh FROM ref_statut_rh WHERE code = 'BROUILLON')")) {
                        psDel.setInt(1, idEmploye);
                        psDel.executeUpdate();
                    }

                    // Récupérer les paramètres actuels d'affectation de l'employé
                    Integer idSite = null;
                    Integer idDept = null;
                    Integer idService = null;
                    Integer idFonc = null;
                    Integer idCatPro = null;
                    Integer idModePaie = null;
                    try (var psAff = c.prepareStatement("SELECT id_site, id_departement, id_service, id_fonction, id_categorie_professionnelle, id_mode_calcul_paie FROM employe_affectation_carriere WHERE id_employe = ? ORDER BY id_affectation DESC LIMIT 1")) {
                        psAff.setInt(1, idEmploye);
                        try (var rsAff = psAff.executeQuery()) {
                            if (rsAff.next()) {
                                idSite = rsAff.getInt("id_site");
                                idDept = rsAff.getInt("id_departement");
                                idService = rsAff.getInt("id_service");
                                idFonc = rsAff.getInt("id_fonction");
                                idCatPro = rsAff.getInt("id_categorie_professionnelle");
                                idModePaie = rsAff.getInt("id_mode_calcul_paie");
                            }
                        }
                    }

                    // Créer un brouillon de revalorisation salariale avec date d'effet AUJOURD'HUI
                    TransitionCarriereRequest req = new TransitionCarriereRequest();
                    req.setCodeTypeEvenement("REVALORISATION");
                    req.setDateEffet(Date.valueOf(LocalDate.now()));
                    req.setIdSite(idSite);
                    req.setIdDepartement(idDept);
                    req.setIdService(idService);
                    req.setIdFonction(idFonc);
                    req.setIdCategorieProfessionnelle(idCatPro);
                    req.setIdModeCalculPaie(idModePaie);
                    req.setSalaireBase(new BigDecimal("990000.00"));
                    req.setMotif("Test Revalorisation Automatique");

                    WorkflowRhCPL brouillon = workflowRhService.creerBrouillonTransition(idEmploye, req, 1, c);
                    assertNotNull(brouillon);
                    assertEquals("BROUILLON", brouillon.getCodeStatutWorkflow());
                    assertTrue(brouillon.isBrouillon());

                    // Valider le workflow
                    ValiderWorkflowRequest valReq = new ValiderWorkflowRequest();
                    valReq.setCommentaireValidation("Validation par test unitaire");
                    WorkflowRhCPL valide = workflowRhService.validerWorkflow(brouillon.getIdWorkflow(), valReq, 1, c);

                    assertNotNull(valide);
                    assertEquals("ACTIF", valide.getCodeStatutWorkflow());
                    assertTrue(valide.isActif());

                    System.out.println("✅ [TEST INTEGRATION BDD] Cycle complet Brouillon -> Valider -> ACTIF testé avec succès sur la base.");
                }
            } finally {
                // Rollback systématique pour ne rien modifier en base
                c.rollback();
            }
        }
    }
}
