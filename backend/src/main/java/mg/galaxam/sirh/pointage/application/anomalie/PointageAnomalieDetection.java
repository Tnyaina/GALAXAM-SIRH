package mg.galaxam.sirh.pointage.application.anomalie;

import java.sql.Date;
import java.util.ArrayList;
import java.util.List;

/** Sélectionne les journées déjà enregistrées qui nécessitent un examen. */
public final class PointageAnomalieDetection {
    private PointageAnomalieDetection() {}

    public record Requete(String sql, java.util.List<Object> parametres) {}

    /** Charge les champs nécessaires, puis applique les critères de détection et les filtres d'écran. */
    public static Requete construireRequete(Integer idSite, Integer idPeriode,
            String datePointage, String search) {
        StringBuilder sql = new StringBuilder(
            "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "p.id_site, s.libelle AS site_nom, s.horaire_continu AS site_horaire_continu, " +
            "s.heure_debut_matin AS heure_debut_matin_site, s.heure_fin_matin AS heure_fin_matin_site, " +
            "s.heure_debut_apres_midi AS heure_debut_am_site, s.heure_fin_apres_midi AS heure_fin_am_site, p.date_pointage, " +
            "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
            "p.total_heures, p.minutes_correction_temps, p.minutes_retard, st.code AS statut_code, st.libelle AS statut_libelle, " +
            "p.motif_ajustement, p.date_ajustement, p.id_demande_conge, " +
            "ltr_valide.id_demande_conge AS demande_validee_id, p.montant_total_indemnites, " +
            "pij_repas.accorde AS repas_accorde, pij_dep.accorde AS dep_accorde, pij_dep.quantite AS dep_quantite, pij_dep.fraction AS dep_fraction, " +
            "ltr_conge.libelle_conge AS demande_en_attente_libelle, " +
            "p.id_periode_paie, per.code AS periode_code, per.libelle AS periode_libelle, per.statut AS statut_periode_code, " +
            "COALESCE(p.version, 1) AS version, " +
            "COALESCE(vsc.solde_actuel, 0.0) AS solde_restant, " +
            "COALESCE(vsc.total_acquis, 0.0) AS solde_acquis, " +
            "COALESCE(vsc.total_pris, 0.0) AS solde_pris " +
            "FROM pointage_jour p " +
            "JOIN employe e ON p.id_employe = e.id_employe " +
            "LEFT JOIN ref_site s ON p.id_site = s.id_site " +
            "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
            "LEFT JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
            "LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage " +
            "    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1) " +
            "LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage " +
            "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DEPLACEMENT', 'TRANSPORT') LIMIT 1) " +
            "LEFT JOIN LATERAL (" +
            "   SELECT tc.libelle AS libelle_conge FROM demande_conge dc " +
            "   JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
            "   JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
            "   WHERE dc.id_employe = p.id_employe AND p.date_pointage BETWEEN dc.date_debut AND dc.date_fin " +
            "   AND sc.code = 'EN_ATTENTE' LIMIT 1" +
            ") ltr_conge ON TRUE " +
            "LEFT JOIN LATERAL (" +
            "   SELECT dc.id_demande_conge FROM demande_conge dc " +
            "   JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
            "   WHERE dc.id_employe = p.id_employe AND p.date_pointage BETWEEN dc.date_debut AND dc.date_fin " +
            "   AND sc.code = 'VALIDE' LIMIT 1" +
            ") ltr_valide ON TRUE " +
            "LEFT JOIN v_solde_conge_employe vsc ON p.id_employe = vsc.id_employe " +
            predicatSql()
        );

        List<Object> params = new ArrayList<>();
        if (idSite != null) {
            sql.append("AND p.id_site = ? ");
            params.add(idSite);
        }
        if (idPeriode != null && idPeriode > 0) {
            sql.append("AND p.id_periode_paie = ? ");
            params.add(idPeriode);
        }
        if (datePointage != null && !datePointage.trim().isEmpty()) {
            sql.append("AND p.date_pointage = ? ");
            params.add(Date.valueOf(datePointage));
        }
        if (search != null && !search.trim().isEmpty()) {
            sql.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term);
            params.add(term);
            params.add(term);
        }

        sql.append("ORDER BY p.date_pointage DESC, e.nom, e.prenom");

        return new Requete(sql.toString(), params);
    }

    private static String predicatSql() {
        return "WHERE ("
                + " st.code = 'ANOMALIE_SAISIE'"
                + " OR st.code = 'DEMI_JOURNEE'"
                + " OR (st.code = 'ABSENCE_NON_JUSTIFIEE' AND EXTRACT(DOW FROM p.date_pointage) NOT IN (0, 6))"
                + " OR (st.code = 'ABSENCE_JUSTIFIEE' AND EXTRACT(DOW FROM p.date_pointage) NOT IN (0, 6))"
                + " OR p.minutes_retard > 0"
                + " OR (p.motif_ajustement IS NOT NULL AND TRIM(p.motif_ajustement) != '')"
                + ") ";
    }
}
