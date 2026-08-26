package mg.galaxam.sirh.model.dashboard.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

@Service
public class DashboardService {

    private final DataSource dataSource;

    public DashboardService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Génère la synthèse complète des KPIs, alertes RH et activités récentes.
     */
    public Map<String, Object> getDashboardSummary() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            return getDashboardSummary(c);
        }
    }

    public Map<String, Object> getDashboardSummary(Connection c) throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();

        summary.put("kpis", buildKpis(c));
        summary.put("alertesCritiques", buildAlertesCritiques(c));
        summary.put("alertes7j", buildAlertes7j(c));
        summary.put("alertes30j", buildAlertes30j(c));
        summary.put("brouillons", buildBrouillons(c));
        summary.put("activiteRecente", buildActiviteRecente(c));
        summary.put("absentsAujourdhui", buildAbsentsAujourdhui(c));
        summary.put("demandesCongeAValider", buildDemandesCongeAValider(c));

        return summary;
    }

    public List<Map<String, Object>> buildDemandesCongeAValider(Connection c) throws Exception {
        List<Map<String, Object>> demandes = new ArrayList<>();
        String sql = "SELECT d.id_demande_conge, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, e.matricule, " +
                "tc.libelle AS type_conge, d.date_debut, d.date_fin, d.nb_jours_decomptes, d.commentaire " +
                "FROM demande_conge d " +
                "JOIN employe e ON d.id_employe = e.id_employe " +
                "JOIN ref_type_conge tc ON d.id_type_conge = tc.id_type_conge " +
                "JOIN ref_statut_demande_conge rsd ON d.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "WHERE rsd.code = 'EN_ATTENTE' " +
                "ORDER BY d.created_at DESC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idDemandeConge", rs.getInt("id_demande_conge"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeConge", rs.getString("type_conge"));
                row.put("dateDebut", rs.getDate("date_debut"));
                row.put("dateFin", rs.getDate("date_fin"));
                row.put("nbJoursDecomptes", rs.getBigDecimal("nb_jours_decomptes"));
                row.put("commentaire", rs.getString("commentaire"));
                demandes.add(row);
            }
        }
        return demandes;
    }

    public List<Map<String, Object>> buildAbsentsAujourdhui(Connection c) throws Exception {
        List<Map<String, Object>> absents = new ArrayList<>();
        String sql = "SELECT d.id_demande_conge, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, e.matricule, " +
                "tc.libelle AS type_conge, d.date_debut, d.date_fin, d.nb_jours_decomptes " +
                "FROM demande_conge d " +
                "JOIN employe e ON d.id_employe = e.id_employe " +
                "JOIN ref_type_conge tc ON d.id_type_conge = tc.id_type_conge " +
                "JOIN ref_statut_demande_conge rsd ON d.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "WHERE rsd.code = 'VALIDE' AND CURRENT_DATE BETWEEN d.date_debut AND d.date_fin " +
                "ORDER BY d.date_fin ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idDemandeConge", rs.getInt("id_demande_conge"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("typeConge", rs.getString("type_conge"));
                row.put("dateDebut", rs.getDate("date_debut"));
                row.put("dateFin", rs.getDate("date_fin"));
                row.put("nbJoursDecomptes", rs.getBigDecimal("nb_jours_decomptes"));
                absents.add(row);
            }
        }
        return absents;
    }

    public Map<String, Object> buildKpis(Connection c) throws Exception {
        Map<String, Object> kpis = new LinkedHashMap<>();

        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM employe WHERE est_actif = TRUE")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("totalEffectifActif", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(DISTINCT ct.id_employe) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.comptabilise_effectif = TRUE")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("effectifComptabilise", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM employe WHERE est_actif = TRUE AND DATE_TRUNC('month', created_at) = DATE_TRUNC('month', CURRENT_DATE)")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("nouvellesEmbauchesMois", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.date_fin IS NOT NULL AND ct.date_fin BETWEEN CURRENT_DATE AND CURRENT_DATE + INTERVAL '30 days'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("contratsExpirant30j", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM contrat_travail ct " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN contrat_periode_essai cpe ON ct.id_contrat = cpe.id_contrat " +
                "WHERE rsc.code = 'ACTIF' AND cpe.statut = 'EN_COURS'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("essaisEnCours", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM workflow_rh wf " +
                "JOIN ref_statut_workflow rsw ON wf.id_statut_workflow = rsw.id_statut_workflow " +
                "WHERE rsw.code = 'BROUILLON'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("brouillonsEnAttente", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM demande_conge d " +
                "JOIN ref_statut_demande_conge rsd ON d.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "WHERE rsd.code = 'EN_ATTENTE'")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("demandesCongeEnAttente", rs.next() ? rs.getInt(1) : 0);
        }

        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COUNT(*) FROM demande_conge d " +
                "JOIN ref_statut_demande_conge rsd ON d.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "WHERE rsd.code = 'VALIDE' AND CURRENT_DATE BETWEEN d.date_debut AND d.date_fin")) {
            ResultSet rs = ps.executeQuery();
            kpis.put("employesEnCongeAujourdhui", rs.next() ? rs.getInt(1) : 0);
        }

        return kpis;
    }

    public List<Map<String, Object>> buildAlertesCritiques(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        String sqlContratsJ = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE AND ct.date_fin = CURRENT_DATE " +
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

        String sqlEssaisJ = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin_essai " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN contrat_periode_essai cpe ON ct.id_contrat = cpe.id_contrat " +
                "WHERE rsc.code = 'ACTIF' AND cpe.statut = 'EN_COURS' AND ct.date_fin_essai = CURRENT_DATE " +
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
                row.put("dateEcheance", rs.getDate("date_fin_essai"));
                row.put("joursRestants", 0);
                alertes.add(row);
            }
        }

        return alertes;
    }

    public List<Map<String, Object>> buildAlertes7j(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        String sqlContrats7j = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "(ct.date_fin - CURRENT_DATE) AS jours_restants " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE AND ct.date_fin <= CURRENT_DATE + INTERVAL '7 days' " +
                "ORDER BY ct.date_fin ASC";
        try (PreparedStatement ps = c.prepareStatement(sqlContrats7j);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "CONTRAT_ECHEANCE_7J");
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

        return alertes;
    }

    public List<Map<String, Object>> buildAlertes30j(Connection c) throws Exception {
        List<Map<String, Object>> alertes = new ArrayList<>();

        String sqlContrats30j = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, ct.date_fin, rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "(ct.date_fin - CURRENT_DATE) AS jours_restants " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE + INTERVAL '7 days' AND ct.date_fin <= CURRENT_DATE + INTERVAL '30 days' " +
                "ORDER BY ct.date_fin ASC";
        try (PreparedStatement ps = c.prepareStatement(sqlContrats30j);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("typeAlerte", "CONTRAT_ECHEANCE_30J");
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

    public List<Map<String, Object>> buildBrouillons(Connection c) throws Exception {
        List<Map<String, Object>> brouillons = new ArrayList<>();

        String sql = "SELECT id_workflow, libelle_type_evenement AS type_evenement, " +
                "id_employe, nom_prenom_employe AS nom_prenom, matricule_employe AS matricule, " +
                "numero_contrat_cible AS reference_dossier, " +
                "created_at AS date_creation, " +
                "EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - created_at)) / 3600 AS heures_attente " +
                "FROM v_workflow_rh_cpl " +
                "WHERE code_statut_workflow = 'BROUILLON' " +
                "ORDER BY created_at ASC";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idWorkflow", rs.getInt("id_workflow"));
                row.put("typeEvenement", rs.getString("type_evenement"));
                row.put("referenceDossier", rs.getString("reference_dossier"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("dateCreation", rs.getTimestamp("date_creation"));
                row.put("heuresAttente", Math.round(rs.getDouble("heures_attente")));
                brouillons.add(row);
            }
        }

        return brouillons;
    }

    public List<Map<String, Object>> buildActiviteRecente(Connection c) throws Exception {
        List<Map<String, Object>> activite = new ArrayList<>();

        String sql = "SELECT id_workflow, libelle_type_evenement AS type_evenement, " +
                "id_employe, nom_prenom_employe AS nom_prenom, matricule_employe AS matricule, " +
                "numero_contrat_cible AS reference_dossier, " +
                "code_statut_workflow AS statut_code, libelle_statut_workflow AS statut_libelle, " +
                "date_effet, " +
                "updated_at AS date_action " +
                "FROM v_workflow_rh_cpl " +
                "WHERE code_statut_workflow IN ('VALIDE', 'REJETE') " +
                "ORDER BY updated_at DESC " +
                "LIMIT 5";
        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("idWorkflow", rs.getInt("id_workflow"));
                row.put("typeEvenement", rs.getString("type_evenement"));
                row.put("referenceDossier", rs.getString("reference_dossier"));
                row.put("idEmploye", rs.getInt("id_employe"));
                row.put("nomPrenom", rs.getString("nom_prenom"));
                row.put("matricule", rs.getString("matricule"));
                row.put("statutCode", rs.getString("statut_code"));
                row.put("statutLibelle", rs.getString("statut_libelle"));
                row.put("dateEffet", rs.getDate("date_effet"));
                row.put("dateAction", rs.getTimestamp("date_action"));
                activite.add(row);
            }
        }

        return activite;
    }
}
