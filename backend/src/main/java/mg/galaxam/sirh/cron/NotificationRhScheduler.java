package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.conge.engine.PurgeFifoEngine;
import mg.galaxam.sirh.model.dashboard.service.NotificationRhService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NotificationRhScheduler — Génère les notifications RH persistées en base.
 * 100% de la logique temporelle, calculs de délais et de seuils FIFO s'exécutent en mémoire Java.
 */
@Component
public class NotificationRhScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRhScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PurgeFifoEngine purgeFifoEngine;

    @Autowired
    private NotificationRhService notificationService;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 30 2 * * ?")
    public void genererNotificationsRh() {
        log.info("[NotificationRhScheduler] Lancement du cycle de génération des notifications RH (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Supprimer les anciennes notifications non critiques
                purguerAnciennesNotifications(c);

                // 2. Alertes Contrats & Périodes d'essai (Calculs en mémoire Java)
                genererAlertesContrats(c);
                genererAlertesEssais(c);

                // 3. Alertes Process RH (Brouillons & Congés 48h)
                genererAlerteBrouillons48h(c);
                genererAlerteCongesEnAttente48h(c);
                genererAlertePiecesManquantes(c);
                
                // 4. Alertes Congés
                genererAlerteSoldeFaible(c);
                genererAlerteRetourCongeJour(c);
                genererAlerteMaladieProlongee(c);
                genererAlerteExpirationProche(c);

                // 5. Alertes Pointage
                genererAlertesPointageAnomalies(c);

                c.commit();
                log.info("[NotificationRhScheduler] Notifications RH générées avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[NotificationRhScheduler] Erreur lors de la génération : {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[NotificationRhScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
        }
    }

    // =========================================================================
    // Purge
    // =========================================================================

    private void purguerAnciennesNotifications(Connection c) throws Exception {
        Timestamp seuil7j = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
        Timestamp seuil30j = Timestamp.valueOf(LocalDateTime.now().minusDays(30));

        // 1. Supprimer les notifications lues de plus de 7 jours
        String sqlLues = "DELETE FROM notification_rh WHERE est_lue = TRUE AND date_creation < ?";
        try (PreparedStatement ps = c.prepareStatement(sqlLues)) {
            ps.setTimestamp(1, seuil7j);
            ps.executeUpdate();
        }

        // 2. Supprimer les notifications non lues très anciennes (+30 jours)
        String sqlNonLues = "DELETE FROM notification_rh WHERE est_lue = FALSE AND date_creation < ?";
        try (PreparedStatement ps = c.prepareStatement(sqlNonLues)) {
            ps.setTimestamp(1, seuil30j);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Alertes Contrats
    // =========================================================================

    private void genererAlertesContrats(Connection c) throws Exception {
        LocalDate today = LocalDate.now();

        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "e.matricule, rtc.libelle AS type_contrat, ct.numero_contrat, ct.date_fin " +
                     "FROM contrat_travail ct " +
                     "JOIN employe e ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                     "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                     "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE AND ct.date_fin IS NOT NULL";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date dFin = rs.getDate("date_fin");
                if (dFin == null) continue;

                LocalDate dateFin = dFin.toLocalDate();
                long jours = ChronoUnit.DAYS.between(today, dateFin);
                if (jours < 0 || jours > 30) continue;

                String nomPrenom = rs.getString("nom_prenom");
                String typeContrat = rs.getString("type_contrat");
                String numero = rs.getString("numero_contrat");
                int idEmploye = rs.getInt("id_employe");

                if (jours == 0) {
                    insererNotification(c, null, "CONTRAT_EXPIRE_J", "CRITIQUE",
                        "Contrat expirant AUJOURD'HUI - " + nomPrenom,
                        "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " arrive à échéance ce soir à minuit. Une action RH est requise.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours <= 7) {
                    insererNotification(c, null, "CONTRAT_EXPIRE_7J", "HAUTE",
                        "Contrat expirant dans " + jours + " jour(s) - " + nomPrenom,
                        "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " expire dans " + jours + " jour(s). Prévoir le renouvellement ou la clôture.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else {
                    insererNotification(c, null, "CONTRAT_EXPIRE_30J", "NORMALE",
                        "Contrat expirant dans " + jours + " jours - " + nomPrenom,
                        "Le contrat de " + nomPrenom + " expire dans " + jours + " jours.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                }
            }
        }
    }

    // =========================================================================
    // Alertes Périodes d'essai
    // =========================================================================

    private void genererAlertesEssais(Connection c) throws Exception {
        LocalDate today = LocalDate.now();

        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, ct.date_fin_essai " +
                     "FROM contrat_travail ct " +
                     "JOIN employe e ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                     "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai IS NOT NULL";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date dEssai = rs.getDate("date_fin_essai");
                if (dEssai == null) continue;

                LocalDate dateFinEssai = dEssai.toLocalDate();
                long jours = ChronoUnit.DAYS.between(today, dateFinEssai);
                if (jours < 0 || jours > 30) continue;

                String nomPrenom = rs.getString("nom_prenom");
                int idEmploye = rs.getInt("id_employe");

                if (jours == 0) {
                    insererNotification(c, null, "ESSAI_EXPIRE_J", "CRITIQUE",
                        "Fin de periode d'essai AUJOURD'HUI - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine ce soir. Décision requise : Confirmer ou Rompre l'essai.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours <= 7) {
                    insererNotification(c, null, "ESSAI_EXPIRE_7J", "HAUTE",
                        "Fin d'essai dans " + jours + " jour(s) - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jour(s). Anticipez la décision RH.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours <= 15) {
                    insererNotification(c, null, "ESSAI_EXPIRE_15J", "NORMALE",
                        "Fin d'essai dans " + jours + " jours - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jours.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else {
                    insererNotification(c, null, "ESSAI_EXPIRE_30J", "NORMALE",
                        "Fin d'essai dans " + jours + " jours - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jours.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                }
            }
        }
    }

    private void genererAlerteBrouillons48h(Connection c) throws Exception {
        String sql = "SELECT wf.id_workflow, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "tev.libelle AS type_evenement, wf.created_at " +
                     "FROM workflow_rh wf " +
                     "JOIN employe e ON wf.id_employe = e.id_employe " +
                     "JOIN ref_type_evenement_rh tev ON wf.id_type_evenement_rh = tev.id_type_evenement_rh " +
                     "JOIN ref_statut_rh rsw ON wf.id_statut_rh = rsw.id_statut_rh " +
                     "WHERE rsw.code = 'BROUILLON'";

        Instant now = Instant.now();

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp createdAt = rs.getTimestamp("created_at");
                if (createdAt == null) continue;

                long heuresAttente = Duration.between(createdAt.toInstant(), now).toHours();
                if (heuresAttente >= 48) {
                    int jours = (int) (heuresAttente / 24);
                    String nomPrenom = rs.getString("nom_prenom");
                    int idEmploye = rs.getInt("id_employe");
                    int idWorkflow = rs.getInt("id_workflow");
                    String typeEv = rs.getString("type_evenement");

                    insererNotification(c, null, "BROUILLON_EN_ATTENTE", "HAUTE",
                        "Brouillon en attente depuis " + jours + "j - " + nomPrenom,
                        "Le brouillon " + typeEv + " pour " + nomPrenom + " n'a pas encore été validé ou annulé (créé il y a " + jours + " jour(s)).",
                        "/employes/" + idEmploye + "?tab=carriere&wf=" + idWorkflow, idEmploye);
                }
            }
        }
    }

    private void genererAlerteCongesEnAttente48h(Connection c) throws Exception {
        String sql = "SELECT dc.id_demande_conge, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "rtc.libelle AS type_conge, dc.date_demande " +
                     "FROM demande_conge dc " +
                     "JOIN employe e ON dc.id_employe = e.id_employe " +
                     "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                     "JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                     "WHERE rsd.code = 'EN_ATTENTE'";

        Instant now = Instant.now();

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Timestamp dateDemande = rs.getTimestamp("date_demande");
                if (dateDemande == null) continue;

                long heuresAttente = Duration.between(dateDemande.toInstant(), now).toHours();
                if (heuresAttente >= 48) {
                    int jours = (int) (heuresAttente / 24);
                    String nomPrenom = rs.getString("nom_prenom");
                    int idEmploye = rs.getInt("id_employe");
                    String typeConge = rs.getString("type_conge");

                    insererNotification(c, null, "CONGE_EN_ATTENTE", "HAUTE",
                        "Demande de conge en attente depuis " + jours + "j - " + nomPrenom,
                        "La demande de congé (" + typeConge + ") pour " + nomPrenom + " n'a pas encore été validée (soumise il y a " + jours + " jour(s)).",
                        "/conges?tab=demandes", idEmploye);
                }
            }
        }
    }

    private void genererAlertePiecesManquantes(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "COUNT(td.id_type_document) AS nb_manquants " +
                     "FROM employe e " +
                     "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                     "CROSS JOIN ref_type_document td " +
                     "LEFT JOIN document_rh doc ON doc.id_employe = e.id_employe AND doc.id_type_document = td.id_type_document " +
                     "WHERE rsc.code = 'ACTIF' AND td.obligatoire = TRUE AND td.actif = TRUE AND doc.id_document IS NULL " +
                     "GROUP BY e.id_employe, e.nom, e.prenom " +
                     "HAVING COUNT(td.id_type_document) > 0";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int nb = rs.getInt("nb_manquants");
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");

                insererNotification(c, null, "DOCUMENTS_MANQUANTS", "NORMALE",
                    "Documents obligatoires manquants (" + nb + ") - " + nomPrenom,
                    "Le dossier de " + nomPrenom + " comporte " + nb + " document(s) obligatoire(s) non fourni(s).",
                    "/employes/" + idEmploye + "?tab=documents", idEmploye);
            }
        }
    }

    private void genererAlerteSoldeFaible(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, v.solde_actuel " +
                     "FROM v_solde_conge_employe v " +
                     "JOIN employe e ON v.id_employe = e.id_employe " +
                     "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                     "WHERE rsc.code = 'ACTIF' AND v.solde_actuel < 0";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                double solde = rs.getDouble("solde_actuel");
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");

                insererNotification(c, null, "SOLDE_CONGE_NEGATIF", "HAUTE",
                    "Solde de congé négatif (" + solde + "j) - " + nomPrenom,
                    "Le solde de congés de " + nomPrenom + " est négatif (" + solde + " jours). Régularisation nécessaire.",
                    "/employes/" + idEmploye + "?tab=conges", idEmploye);
            }
        }
    }

    private void genererAlerteRetourCongeJour(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, dc.date_fin " +
                     "FROM demande_conge dc " +
                     "JOIN employe e ON dc.id_employe = e.id_employe " +
                     "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                     "WHERE rsd.code = 'VALIDE' AND dc.date_fin = CURRENT_DATE";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");

                insererNotification(c, null, "RETOUR_CONGE_J", "INFO",
                    "Dernier jour de congé aujourd'hui - " + nomPrenom,
                    nomPrenom + " termine ses congés aujourd'hui et reprendra le travail dès demain.",
                    "/employes/" + idEmploye + "?tab=conges", idEmploye);
            }
        }
    }

    private void genererAlerteMaladieProlongee(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, dc.nb_jours_decomptes " +
                     "FROM demande_conge dc " +
                     "JOIN employe e ON dc.id_employe = e.id_employe " +
                     "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                     "JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                     "WHERE rsd.code = 'VALIDE' AND rtc.code = 'MALADIE' " +
                     "AND dc.nb_jours_decomptes >= 30 " +
                     "AND dc.date_debut <= CURRENT_DATE AND dc.date_fin >= CURRENT_DATE";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                double nbJours = rs.getDouble("nb_jours_decomptes");

                insererNotification(c, null, "MALADIE_PLAFOND_EPISODE_ATTEINT", "HAUTE",
                    "Arrêt Maladie > 30 jours (Bascule) - " + nomPrenom,
                    nomPrenom + " est en arrêt maladie prolongé (" + nbJours + " jours). Rappel: au-delà de 30 jours continus, maintien de salaire suspendu (CNaPS).",
                    "/employes/" + idEmploye + "?tab=conges", idEmploye);
            }
        }
    }

    private void genererAlerteExpirationProche(Connection c) throws Exception {
        LocalDate seuil30Jours = LocalDate.now().plusDays(30).minusMonths(36);

        String sql = "SELECT m.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "m.type_mouvement, m.nb_jours, m.date_mouvement " +
                     "FROM mouvement_solde_conge m " +
                     "JOIN employe e ON m.id_employe = e.id_employe " +
                     "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                     "WHERE rsc.code = 'ACTIF'";

        Map<Integer, PurgeCongeScheduler.EmployeSoldeAccumulateur> soldesMap = new HashMap<>();
        Map<Integer, String> nomsMap = new HashMap<>();

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                nomsMap.putIfAbsent(idEmploye, rs.getString("nom_prenom"));
                String typeMouvement = rs.getString("type_mouvement");
                double nbJours = rs.getDouble("nb_jours");
                Date dMvt = rs.getDate("date_mouvement");
                LocalDate dateMvt = dMvt != null ? dMvt.toLocalDate() : LocalDate.now();

                PurgeCongeScheduler.EmployeSoldeAccumulateur acc = soldesMap.computeIfAbsent(idEmploye, k -> new PurgeCongeScheduler.EmployeSoldeAccumulateur());

                if (("ACQUISITION".equals(typeMouvement) || "BONIFICATION".equals(typeMouvement)) && dateMvt.isBefore(seuil30Jours)) {
                    acc.acquisAnciens += nbJours;
                } else if ("PRISE".equals(typeMouvement) || ("AJUSTEMENT".equals(typeMouvement) && nbJours < 0)) {
                    acc.totalPris += (-nbJours);
                } else if ("EXPIRATION".equals(typeMouvement)) {
                    acc.totalDejaExpire += (-nbJours);
                }
            }
        }

        for (Map.Entry<Integer, PurgeCongeScheduler.EmployeSoldeAccumulateur> entry : soldesMap.entrySet()) {
            int idEmploye = entry.getKey();
            PurgeCongeScheduler.EmployeSoldeAccumulateur acc = entry.getValue();
            double joursRisques = purgeFifoEngine.calculerJoursAExpier(acc.acquisAnciens, acc.totalPris, acc.totalDejaExpire);

            if (joursRisques > 0.001) {
                String nomPrenom = nomsMap.get(idEmploye);
                insererNotification(c, null, "CONGE_EXPIRATION_PROCHE", "HAUTE",
                    "Expiration de congés imminente - " + nomPrenom,
                    "Attention, " + String.format("%.2f", joursRisques) + " jours de congés acquis par " + nomPrenom + " il y a presque 3 ans risquent d'expirer dans les 30 prochains jours s'ils ne sont pas pris.",
                    "/employes/" + idEmploye + "?tab=conges", idEmploye);
            }
        }
    }

    private void genererAlertesPointageAnomalies(Connection c) throws Exception {
        String sql = "SELECT pp.id_periode_paie, pp.code, pp.libelle, pp.date_fin, " +
                     "COUNT(p.id_pointage) AS nb_anomalies " +
                     "FROM periode_paie pp " +
                     "JOIN ref_statut_periode_paie rsp ON pp.id_statut_periode = rsp.id_statut_periode " +
                     "JOIN pointage_jour p ON pp.id_periode_paie = p.id_periode_paie " +
                     "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                     "WHERE rsp.code = 'EN_COURS' " +
                     "AND (st.code = 'ANOMALIE_SAISIE' OR (p.heure_entree_matin IS NOT NULL AND p.heure_sortie_matin IS NULL) OR st.code = 'ABSENCE_NON_JUSTIFIEE') " +
                     "GROUP BY pp.id_periode_paie, pp.code, pp.libelle, pp.date_fin " +
                     "HAVING COUNT(p.id_pointage) > 0";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int nbAnomalies = rs.getInt("nb_anomalies");
                String codePeriode = rs.getString("code");
                String libellePeriode = rs.getString("libelle");

                insererNotification(c, null, "ROLE_AGENT_POINTAGE", "POINTAGE_MANQUANT", "HAUTE",
                    "Anomalies de pointage en attente - " + codePeriode,
                    "La période " + libellePeriode + " comporte " + nbAnomalies + " anomalie(s) de pointage non résolue(s) avant la clôture de paie.",
                    "/pointages/anomalies", null);
            }
        }
    }

    // =========================================================================
    // Helper — Insertion d'une notification
    // =========================================================================

    private void insererNotification(Connection c, Integer idUtilisateur, String typeAlerte,
                                     String priorite, String titre, String message,
                                     String lien, Integer idEmploye) throws Exception {
        insererNotification(c, idUtilisateur, null, typeAlerte, priorite, titre, message, lien, idEmploye);
    }

    private void insererNotification(Connection c, Integer idUtilisateur, String cibleRole, String typeAlerte,
                                     String priorite, String titre, String message,
                                     String lien, Integer idEmploye) throws Exception {
        // 1. Anti-doublon non lue
        String checkUnreadSql = "SELECT 1 FROM notification_rh WHERE type_alerte = ? " +
                                "AND id_employe_concerne IS NOT DISTINCT FROM ?::INTEGER " +
                                "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                                "AND titre = ? AND est_lue = FALSE";
        try (PreparedStatement chk = c.prepareStatement(checkUnreadSql)) {
            chk.setString(1, typeAlerte);
            if (idEmploye != null) chk.setInt(2, idEmploye); else chk.setNull(2, java.sql.Types.INTEGER);
            if (idUtilisateur != null) chk.setInt(3, idUtilisateur); else chk.setNull(3, java.sql.Types.INTEGER);
            chk.setString(4, titre);
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    return; // Déjà présente non lue
                }
            }
        }

        // 2. Anti-resurfaçage 7 jours (avec paramètre Timestamp en Java pur)
        Timestamp seuil7j = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
        String checkReadSql = "SELECT 1 FROM notification_rh WHERE type_alerte = ? " +
                              "AND id_employe_concerne IS NOT DISTINCT FROM ?::INTEGER " +
                              "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                              "AND titre = ? AND est_lue = TRUE AND date_creation > ?";
        try (PreparedStatement chk = c.prepareStatement(checkReadSql)) {
            chk.setString(1, typeAlerte);
            if (idEmploye != null) chk.setInt(2, idEmploye); else chk.setNull(2, java.sql.Types.INTEGER);
            if (idUtilisateur != null) chk.setInt(3, idUtilisateur); else chk.setNull(3, java.sql.Types.INTEGER);
            chk.setString(4, titre);
            chk.setTimestamp(5, seuil7j);
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    return; // Déjà acquittée récemment
                }
            }
        }

        // 3. INSERT via source unique de vérité
        notificationService.creer(c, typeAlerte, priorite, titre, message, lien, idEmploye, idUtilisateur, cibleRole);
    }
}
