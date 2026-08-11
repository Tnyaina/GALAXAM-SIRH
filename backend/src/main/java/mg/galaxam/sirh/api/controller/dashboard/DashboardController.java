package mg.galaxam.sirh.api.controller.dashboard;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * DashboardController — Endpoint d'agrégation RH pour le tableau de bord.
 *
 * Pattern professionnel : une seule requête SQL consolidée (ou quelques requêtes
 * ciblées) plutôt que N appels côté frontend. Retourne un objet DashboardSummary
 * avec tous les KPIs et alertes en un seul appel.
 *
 * GET /dashboard/summary
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Autowired
    private DataSource dataSource;

    /**
     * GET /dashboard/summary
     * Retourne un objet JSON avec :
     *   - kpis              : métriques agrégées (effectif, contrats, essais, brouillons)
     *   - alertesCritiques  : contrats expirant aujourd'hui + essais échéant aujourd'hui
     *   - alertes7j         : contrats / essais expirant dans 7 jours
     *   - alertes30j        : contrats expirant dans 30 jours
     *   - brouillonsEnAttente : workflows BROUILLON > 48h sans validation
     *   - activiteRecente   : 5 dernières embauches / derniers mouvements validés
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('EMPLOYE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getDashboardSummary() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> summary = new LinkedHashMap<>();

            summary.put("kpis",             buildKpis(c));
            summary.put("alertesCritiques", buildAlertesCritiques(c));
            summary.put("alertes7j",        buildAlertes7j(c));
            summary.put("alertes30j",       buildAlertes30j(c));
            summary.put("brouillons",       buildBrouillons(c));
            summary.put("activiteRecente",  buildActiviteRecente(c));

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors du chargement du dashboard : " + e.getMessage());
        }
    }

    // =========================================================================
    // KPIs — Métriques clés
    // =========================================================================

    private Map<String, Object> buildKpis(Connection c) throws Exception {
        Map<String, Object> kpis = new LinkedHashMap<>();

        // Effectif actif total (comptabilisé)
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM employe WHERE est_actif = TRUE")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("totalEffectifActif", rs.next() ? rs.getInt(1) : 0);
        }

        // Effectif actif comptabilisé (hors stagiaires/consultants)
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(DISTINCT ct.id_employe) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.comptabilise_effectif = TRUE")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("effectifComptabilise", rs.next() ? rs.getInt(1) : 0);
        }

        // Nouveaux employés ce mois
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM employe " +
                "WHERE est_actif = TRUE " +
                "AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', CURRENT_DATE)")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("nouvellesEmbauchesMois", rs.next() ? rs.getInt(1) : 0);
        }

        // Contrats CDD/Stage expirant dans les 30 jours
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND ct.date_fin IS NOT NULL " +
                "AND ct.date_fin BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("contratsExpirant30j", rs.next() ? rs.getInt(1) : 0);
        }

        // Essais actifs en cours
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai >= CURRENT_DATE")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("essaisEnCours", rs.next() ? rs.getInt(1) : 0);
        }

        // Brouillons en attente de validation
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM workflow_rh wf " +
                "JOIN ref_statut_workflow rsw ON wf.id_statut_workflow = rsw.id_statut_workflow " +
                "WHERE rsw.code = 'BROUILLON'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("brouillonsEnAttente", rs.next() ? rs.getInt(1) : 0);
        }

        return kpis;
    }

    // =========================================================================
    // ALERTES CRITIQUES — Expirations aujourd'hui
    // =========================================================================

    private List<Map<String, Object>> buildAlertesCritiques(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        // Contrats à terme fixe expirant AUJOURD'HUI (exclusion CDI)
        String sqlContratsJ = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin = CURRENT_DATE " +
                "ORDER BY e.nom";
        try (PreparedStatement ps = c.prepareStatement(sqlContratsJ);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "CONTRAT_EXPIRE_J");
                row.put("priorite", "CRITIQUE");
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeContrat", rs.getString("type_contrat"));
                row.put("numeroContrat", rs.getString("numero_contrat"));
                row.put("dateEcheance", rs.getDate("date_fin"));
                row.put("joursRestants", 0);
                alertes.add(row);
            }
        }

        // Essais expirant AUJOURD'HUI
        String sqlEssaisJ = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin_essai " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai = CURRENT_DATE " +
                "ORDER BY e.nom";
        try (PreparedStatement ps = c.prepareStatement(sqlEssaisJ);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "ESSAI_EXPIRE_J");
                row.put("priorite", "CRITIQUE");
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeContrat", "Essai");
                row.put("dateEcheance", rs.getDate("date_fin_essai"));
                row.put("joursRestants", 0);
                alertes.add(row);
            }
        }

        return alertes;
    }

    // =========================================================================
    // ALERTES 7 JOURS
    // =========================================================================

    private List<Map<String, Object>> buildAlertes7j(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        // Contrats à terme fixe expirant dans les 7 jours (exclusion CDI)
        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "ct.date_fin - CURRENT_DATE AS jours_restants " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE " +
                "AND ct.date_fin <= CURRENT_DATE + INTERVAL '7 days' " +
                "ORDER BY ct.date_fin ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "CONTRAT_EXPIRE_7J");
                row.put("priorite", "HAUTE");
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeContrat", rs.getString("type_contrat"));
                row.put("numeroContrat", rs.getString("numero_contrat"));
                row.put("dateEcheance", rs.getDate("date_fin"));
                row.put("joursRestants", rs.getInt("jours_restants"));
                alertes.add(row);
            }
        }

        // Essais expirant dans 7 jours
        String sqlEssai = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin_essai, " +
                "ct.date_fin_essai - CURRENT_DATE AS jours_restants " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND ct.date_fin_essai > CURRENT_DATE " +
                "AND ct.date_fin_essai <= CURRENT_DATE + INTERVAL '7 days' " +
                "ORDER BY ct.date_fin_essai ASC";
        try (PreparedStatement ps = c.prepareStatement(sqlEssai);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "ESSAI_EXPIRE_7J");
                row.put("priorite", "HAUTE");
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeContrat", "Essai");
                row.put("dateEcheance", rs.getDate("date_fin_essai"));
                row.put("joursRestants", rs.getInt("jours_restants"));
                alertes.add(row);
            }
        }

        return alertes;
    }

    // =========================================================================
    // ALERTES 30 JOURS
    // =========================================================================

    private List<Map<String, Object>> buildAlertes30j(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        // Contrats à terme fixe expirant entre J+7 et J+30 (exclusion CDI)
        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "ct.date_fin - CURRENT_DATE AS jours_restants " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE + INTERVAL '7 days' " +
                "AND ct.date_fin <= CURRENT_DATE + INTERVAL '30 days' " +
                "ORDER BY ct.date_fin ASC " +
                "LIMIT 20";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "CONTRAT_EXPIRE_30J");
                row.put("priorite", "NORMALE");
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeContrat", rs.getString("type_contrat"));
                row.put("numeroContrat", rs.getString("numero_contrat"));
                row.put("dateEcheance", rs.getDate("date_fin"));
                row.put("joursRestants", rs.getInt("jours_restants"));
                alertes.add(row);
            }
        }

        return alertes;
    }

    // =========================================================================
    // BROUILLONS EN ATTENTE
    // =========================================================================

    private List<Map<String, Object>> buildBrouillons(Connection c) throws Exception {
        List<Map<String, Object>> brouillons = new ArrayList<>();

        String sql = "SELECT wf.id_workflow, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, tev.libelle AS type_evenement, wf.date_demande, wf.date_effet, " +
                "EXTRACT(DAY FROM CURRENT_TIMESTAMP - wf.created_at)::INT AS jours_attente " +
                "FROM workflow_rh wf " +
                "JOIN employe e ON wf.id_employe = e.id_employe " +
                "JOIN ref_type_evenement_rh tev ON wf.id_type_evenement_rh = tev.id_type_evenement_rh " +
                "JOIN ref_statut_workflow rsw ON wf.id_statut_workflow = rsw.id_statut_workflow " +
                "WHERE rsw.code = 'BROUILLON' " +
                "ORDER BY wf.created_at ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idWorkflow", rs.getInt("id_workflow"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeEvenement", rs.getString("type_evenement"));
                row.put("dateDemande", rs.getDate("date_demande"));
                row.put("dateEffet", rs.getDate("date_effet"));
                row.put("joursAttente", rs.getInt("jours_attente"));
                brouillons.add(row);
            }
        }

        return brouillons;
    }

    // =========================================================================
    // ACTIVITÉ RÉCENTE — 8 derniers mouvements validés
    // =========================================================================

    private List<Map<String, Object>> buildActiviteRecente(Connection c) throws Exception {
        List<Map<String, Object>> activite = new ArrayList<>();

        String sql = "SELECT wf.id_workflow, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, tev.libelle AS type_evenement, rsw.libelle AS statut, " +
                "wf.date_effet, wf.updated_at " +
                "FROM workflow_rh wf " +
                "JOIN employe e ON wf.id_employe = e.id_employe " +
                "JOIN ref_type_evenement_rh tev ON wf.id_type_evenement_rh = tev.id_type_evenement_rh " +
                "JOIN ref_statut_workflow rsw ON wf.id_statut_workflow = rsw.id_statut_workflow " +
                "WHERE rsw.code IN ('VALIDE', 'ANNULE') " +
                "ORDER BY wf.updated_at DESC " +
                "LIMIT 8";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idWorkflow", rs.getInt("id_workflow"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeEvenement", rs.getString("type_evenement"));
                row.put("statut", rs.getString("statut"));
                row.put("dateEffet", rs.getDate("date_effet"));
                row.put("updatedAt", rs.getTimestamp("updated_at"));
                activite.add(row);
            }
        }

        return activite;
    }
}
