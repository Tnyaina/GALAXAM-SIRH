package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Projections en lecture de l'onglet Paie d'un employé. */
@Service
public class PayrollEmployeeReadService {
    private static final Logger log = LoggerFactory.getLogger(PayrollEmployeeReadService.class);
    private final CongeTauxApplicableService congeTauxApplicableService;

    public PayrollEmployeeReadService(CongeTauxApplicableService congeTauxApplicableService) {
        this.congeTauxApplicableService = congeTauxApplicableService;
    }

    /**
     * Liste l'historique complet des bulletins de paie d'un collaborateur (Sprint Paie UI P0/P1).
     */
    public List<Map<String, Object>> listerBulletinsEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return Collections.emptyList();
        String sql = "SELECT b.id_bulletin, b.id_periode_paie, b.reference_bulletin, b.type_bulletin, b.statut, " +
                "b.salaire_base, b.total_gains_bruts, b.total_cotisations_salariales, b.salaire_net_imposable, " +
                "b.irsa_net, b.retenue_isi_5, b.net_a_payer, b.date_calcul, b.date_validation, b.date_cloture, " +
                "p.code AS code_periode, p.libelle AS libelle_periode, p.date_debut AS date_debut_periode, p.date_fin AS date_fin_periode " +
                "FROM bulletin_paie b " +
                "JOIN periode_paie p ON b.id_periode_paie = p.id_periode_paie " +
                "WHERE b.id_employe = ? " +
                "ORDER BY p.date_debut DESC, b.id_bulletin DESC";
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("idBulletin", rs.getInt("id_bulletin"));
                    map.put("idPeriodePaie", rs.getInt("id_periode_paie"));
                    map.put("referenceBulletin", rs.getString("reference_bulletin"));
                    map.put("typeBulletin", rs.getString("type_bulletin"));
                    String statut = rs.getString("statut");
                    map.put("statut", statut);
                    map.put("isValide", "VALIDE".equalsIgnoreCase(statut));
                    map.put("isCloture", "CLOTURE".equalsIgnoreCase(statut));
                    map.put("isCalcule", "CALCULE".equalsIgnoreCase(statut));
                    map.put("isStc", "STC".equalsIgnoreCase(rs.getString("type_bulletin")));
                    map.put("badgeVariant", "VALIDE".equalsIgnoreCase(statut) || "CLOTURE".equalsIgnoreCase(statut) ? "success" : "CALCULE".equalsIgnoreCase(statut) ? "info" : "default");
                    map.put("salaireBase", rs.getBigDecimal("salaire_base"));
                    map.put("totalGainsBruts", rs.getBigDecimal("total_gains_bruts"));
                    map.put("totalCotisationsSalariales", rs.getBigDecimal("total_cotisations_salariales"));
                    map.put("salaireNetImposable", rs.getBigDecimal("salaire_net_imposable"));
                    map.put("irsaNet", rs.getBigDecimal("irsa_net"));
                    map.put("retenueIsi5", rs.getBigDecimal("retenue_isi_5"));
                    map.put("netAPayer", rs.getBigDecimal("net_a_payer"));
                    map.put("dateCalcul", rs.getTimestamp("date_calcul"));
                    map.put("dateValidation", rs.getTimestamp("date_validation"));
                    map.put("dateCloture", rs.getTimestamp("date_cloture"));
                    map.put("codePeriode", rs.getString("code_periode"));
                    map.put("libellePeriode", rs.getString("libelle_periode"));
                    map.put("dateDebutPeriode", rs.getDate("date_debut_periode"));
                    map.put("dateFinPeriode", rs.getDate("date_fin_periode"));
                    list.add(map);
                }
            }
        }
        return list;
    }

    /**
     * Fournit une synthèse financière compacte et 100% backend-driven pour l'onglet Paie de l'employé.
     */
    public Map<String, Object> obtenirSynthesePaieEmploye(Integer idEmploye, Connection c) throws Exception {
        if (idEmploye == null) return Collections.emptyMap();
        Map<String, Object> synthese = new HashMap<>();

        // 1. Situation contractuelle et rémunération de base
        String sqlContrat = "SELECT e.matricule, e.nom, e.prenom, " +
                "eac.salaire_base, rmp.code AS mode_calcul_paie, ct.id_contrat, ct.numero_contrat, rsc.code AS code_statut_contrat, " +
                "ct.date_debut AS date_debut_contrat, ct.date_fin AS date_fin_contrat, " +
                "f.libelle AS fonction_nom, d.libelle AS dept_nom, s.libelle AS site_nom " +
                "FROM employe e " +
                "LEFT JOIN LATERAL (SELECT contrat.* FROM contrat_travail contrat " +
                "LEFT JOIN ref_statut_rh statut ON contrat.id_statut_rh = statut.id_statut_rh " +
                "WHERE contrat.id_employe = e.id_employe " +
                "ORDER BY CASE WHEN statut.code = 'ACTIF' THEN 0 ELSE 1 END, " +
                "contrat.date_debut DESC NULLS LAST, contrat.id_contrat DESC LIMIT 1) ct ON TRUE " +
                "LEFT JOIN LATERAL (SELECT affectation.* FROM employe_affectation_carriere affectation " +
                "LEFT JOIN ref_statut_rh statut_affectation ON affectation.id_statut_rh = statut_affectation.id_statut_rh " +
                "WHERE affectation.id_contrat = ct.id_contrat " +
                "ORDER BY CASE WHEN statut_affectation.code = 'ACTIF' THEN 0 ELSE 1 END, " +
                "affectation.date_debut DESC, affectation.id_affectation DESC LIMIT 1) eac ON TRUE " +
                "LEFT JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                "LEFT JOIN ref_fonction f ON eac.id_fonction = f.id_fonction " +
                "LEFT JOIN ref_departement d ON eac.id_departement = d.id_departement " +
                "LEFT JOIN ref_site s ON eac.id_site = s.id_site " +
                "LEFT JOIN ref_mode_calcul_paie rmp ON eac.id_mode_calcul_paie = rmp.id_mode_calcul_paie " +
                "WHERE e.id_employe = ? " +
                "LIMIT 1";

        BigDecimal salaireBase = BigDecimal.ZERO;
        try (PreparedStatement ps = c.prepareStatement(sqlContrat)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    salaireBase = rs.getBigDecimal("salaire_base") != null ? rs.getBigDecimal("salaire_base") : BigDecimal.ZERO;
                    synthese.put("modeCalculPaie", rs.getString("mode_calcul_paie"));
                    synthese.put("matricule", rs.getString("matricule"));
                    synthese.put("nomComplet", (rs.getString("nom") + " " + (rs.getString("prenom") != null ? rs.getString("prenom") : "")).trim());
                    synthese.put("fonction", rs.getString("fonction_nom"));
                    synthese.put("departement", rs.getString("dept_nom"));
                    synthese.put("site", rs.getString("site_nom"));
                    synthese.put("idContrat", rs.getObject("id_contrat"));
                    synthese.put("numeroContrat", rs.getString("numero_contrat"));
                    String codeStatut = rs.getString("code_statut_contrat");
                    synthese.put("codeStatutContrat", codeStatut);
                    synthese.put("dateDebutContrat", rs.getDate("date_debut_contrat"));
                    synthese.put("dateFinContrat", rs.getDate("date_fin_contrat"));
                    synthese.put("contratActif", "ACTIF".equalsIgnoreCase(codeStatut));
                }
            }
        }
        synthese.put("salaireBaseContractuel", salaireBase);

        // La décision STC reste côté serveur ; une simulation existante est reprise dans l'historique.
        boolean contratTermine = "CLOTURE".equals(synthese.get("codeStatutContrat"))
                || "ROMPU".equals(synthese.get("codeStatutContrat"));
        boolean stcExistant = false;
        if (contratTermine) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT EXISTS (SELECT 1 FROM paie_stc WHERE id_employe = ? AND statut <> 'ANNULE')")) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) stcExistant = rs.getBoolean(1);
                }
            }
        }
        synthese.put("stcActionRequise", contratTermine && !stcExistant);

        // 2. Taux horaire légal backend-driven (diviseur conventionnel légal)
        BigDecimal diviseur = new BigDecimal("173.3333");
        try {
            var conf = PaieConfigLegale.findActiveAt(c, mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today());
            if (conf != null && conf.getDiviseurMensuelHeures() != null && conf.getDiviseurMensuelHeures().compareTo(BigDecimal.ZERO) > 0) {
                diviseur = conf.getDiviseurMensuelHeures();
            }
        } catch (Exception ignored) {}
        String modeCalculPaie = (String) synthese.get("modeCalculPaie");
        BigDecimal tauxHoraire = salaireBase.compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ZERO
                : "HORAIRE".equalsIgnoreCase(modeCalculPaie) ? salaireBase
                : "JOURNALIER".equalsIgnoreCase(modeCalculPaie) ? null
                : salaireBase.divide(diviseur, 4, RoundingMode.HALF_UP);
        synthese.put("tauxHoraire", tauxHoraire);
        synthese.put("diviseurHeures", "MENSUEL".equalsIgnoreCase(modeCalculPaie) ? diviseur : null);

        // 3. TCONGE journalier applicable résolu par le moteur canonique
        BigDecimal tauxConge = null;
        try {
            if ("MENSUEL".equalsIgnoreCase(modeCalculPaie)
                    && congeTauxApplicableService != null && salaireBase.compareTo(BigDecimal.ZERO) > 0) {
                var res = congeTauxApplicableService.resoudreTauxCongeApplicable(idEmploye, mg.galaxam.sirh.paie.domain.PayrollBusinessTime.today(), salaireBase, c);
                if (res != null && res.getTauxJournalier() != null) {
                    tauxConge = res.getTauxJournalier();
                }
            }
        } catch (Exception ex) {
            log.warn("Impossible de résoudre TCONGE pour employe {}: {}", idEmploye, ex.getMessage());
            synthese.put("tauxJournalierCongeErreur", ex.getMessage());
        }
        synthese.put("tauxJournalierConge", tauxConge);

        // 4. Dernier bulletin, quel que soit son état. Une fin de période n'est pas une date de paiement.
        String sqlLastBulletin = "SELECT b.id_bulletin, b.total_gains_bruts, b.net_a_payer, b.statut, b.date_calcul, " +
                "p.code AS code_periode, p.libelle AS libelle_periode, p.date_fin AS date_fin_periode " +
                "FROM bulletin_paie b " +
                "JOIN periode_paie p ON b.id_periode_paie = p.id_periode_paie " +
                "WHERE b.id_employe = ? " +
                "ORDER BY p.date_debut DESC, b.id_bulletin DESC LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sqlLastBulletin)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    synthese.put("dernierBulletinId", rs.getInt("id_bulletin"));
                    synthese.put("dernierePeriodeCode", rs.getString("code_periode"));
                    synthese.put("dernierePeriodeLibelle", rs.getString("libelle_periode"));
                    synthese.put("derniereDateFinPeriode", rs.getDate("date_fin_periode"));
                    synthese.put("derniereDateCalcul", rs.getTimestamp("date_calcul"));
                    synthese.put("dernierBrut", rs.getBigDecimal("total_gains_bruts"));
                    synthese.put("dernierNet", rs.getBigDecimal("net_a_payer"));
                    synthese.put("dernierStatut", rs.getString("statut"));
                    String st = rs.getString("statut");
                    synthese.put("isDernierValide", "VALIDE".equalsIgnoreCase(st) || "CLOTURE".equalsIgnoreCase(st));
                    synthese.put("isDernierCalcule", "CALCULE".equalsIgnoreCase(st));
                    synthese.put("dernierBadgeVariant", "VALIDE".equalsIgnoreCase(st) || "CLOTURE".equalsIgnoreCase(st) ? "success" : "info");
                } else {
                    synthese.put("dernierBulletinId", null);
                    synthese.put("dernierePeriodeCode", null);
                    synthese.put("dernierePeriodeLibelle", null);
                    synthese.put("derniereDateFinPeriode", null);
                    synthese.put("derniereDateCalcul", null);
                    synthese.put("dernierBrut", null);
                    synthese.put("dernierNet", null);
                    synthese.put("dernierStatut", null);
                    synthese.put("isDernierValide", false);
                    synthese.put("isDernierCalcule", false);
                    synthese.put("dernierBadgeVariant", "default");
                }
            }
        }

        // 5. Distinguer les demandes et les soldes réels des acomptes/avances.
        // Les autres variables de paie (primes, retenues, frais) n'entrent pas dans ces montants.
        String sqlAvances = "SELECT " +
                "COALESCE(SUM(CASE WHEN t.categorie = 'ACOMPTE' AND a.statut IN ('APPROUVE', 'IMPUTE_PAIE') " +
                "AND a.versement_confirme_at IS NOT NULL " +
                "THEN a.solde_restant ELSE 0 END), 0) AS acomptes_restants, " +
                "COALESCE(SUM(CASE WHEN t.categorie = 'AVANCE' AND a.statut IN ('APPROUVE', 'IMPUTE_PAIE') " +
                "AND a.versement_confirme_at IS NOT NULL " +
                "THEN a.solde_restant ELSE 0 END), 0) AS avances_restantes, " +
                "COUNT(*) FILTER (WHERE t.categorie IN ('ACOMPTE', 'AVANCE') AND a.statut = 'EN_ATTENTE') AS demandes_en_attente " +
                "FROM paie_avance_acompte a " +
                "LEFT JOIN ref_type_mouvement_paie t ON t.id_type_mouvement = a.id_type_mouvement " +
                "WHERE a.id_employe = ?";
        try (PreparedStatement ps = c.prepareStatement(sqlAvances)) {
            ps.setInt(1, idEmploye);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    synthese.put("acomptesRestants", rs.getBigDecimal("acomptes_restants"));
                    synthese.put("avancesRestantes", rs.getBigDecimal("avances_restantes"));
                    synthese.put("demandesAcompteAvanceEnAttente", rs.getInt("demandes_en_attente"));
                }
            }
        }
        return synthese;
    }
}
