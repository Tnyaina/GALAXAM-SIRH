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
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
    private CongeService congeService;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

    @Scheduled(cron = "0 30 2 * * ?", zone = "Indian/Antananarivo")
    public void genererNotificationsRh() {
        executionRegistry.runDaily("NOTIFICATIONS_RH", this::genererNotificationsRhInterne);
    }

    public boolean rattraperNotifications() {
        return executionRegistry.runDailyCatchUp("NOTIFICATIONS_RH", java.time.LocalTime.of(2, 30),
                this::genererNotificationsRhInterne);
    }

    private void genererNotificationsRhInterne() throws Exception {
        log.info("[NotificationRhScheduler] Lancement du cycle de génération des notifications RH (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Le contrôle anti-doublon notification est un SELECT puis INSERT.
                // Le verrou transactionnel rend cette séquence sérialisée même si
                // le bail du registre expire pendant une génération longue.
                try (PreparedStatement lock = c.prepareStatement("SELECT pg_advisory_xact_lock(1195467864, 1)")) {
                    lock.execute();
                }
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
                throw e;
            }
        } catch (Exception e) {
            log.error("[NotificationRhScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
            throw e;
        }
    }

    // =========================================================================
    // Purge
    // =========================================================================

    private void purguerAnciennesNotifications(Connection c) throws Exception {
        Timestamp seuil30j = Timestamp.valueOf(LocalDateTime.now().minusDays(30));
        Timestamp seuil90j = Timestamp.valueOf(LocalDateTime.now().minusDays(90));

        // 1. Supprimer les notifications lues de plus de 30 jours
        String sqlLues = "DELETE FROM notification_rh WHERE est_lue = TRUE AND date_creation < ?";
        try (PreparedStatement ps = c.prepareStatement(sqlLues)) {
            ps.setTimestamp(1, seuil30j);
            ps.executeUpdate();
        }

        // 2. Supprimer les notifications non lues très anciennes (+90 jours)
        String sqlNonLues = "DELETE FROM notification_rh WHERE est_lue = FALSE AND date_creation < ?";
        try (PreparedStatement ps = c.prepareStatement(sqlNonLues)) {
            ps.setTimestamp(1, seuil90j);
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
                     "WHERE rsc.code = 'ACTIF' AND rtc.exige_date_fin = TRUE " +
                     "AND ct.date_fin BETWEEN ? AND ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today.minusDays(60)));
            ps.setDate(2, Date.valueOf(today.plusDays(30)));
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date dFin = rs.getDate("date_fin");
                if (dFin == null) continue;

                LocalDate dateFin = dFin.toLocalDate();
                long jours = ChronoUnit.DAYS.between(today, dateFin);
                if (jours < -60 || jours > 30) continue;

                String nomPrenom = rs.getString("nom_prenom");
                String typeContrat = rs.getString("type_contrat");
                String numero = rs.getString("numero_contrat");
                int idEmploye = rs.getInt("id_employe");

                if (jours < 0) {
                    long joursEchus = Math.abs(jours);
                    insererNotification(c, null, "CONTRAT_ECHU_NON_REGULARISE", "CRITIQUE",
                        "Contrat CDD échu non régularisé - " + nomPrenom,
                        "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " est arrivé à échéance depuis " + joursEchus + " jour(s) (le " + dateFin + ") sans clôture ni renouvellement validé.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours == 0) {
                    insererNotification(c, null, "CONTRAT_EXPIRE_J", "CRITIQUE",
                        "Contrat expirant AUJOURD'HUI - " + nomPrenom,
                        "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " arrive à échéance ce soir à minuit. Une action est requise.",
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
                     "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai BETWEEN ? AND ?";

        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(today.minusDays(30)));
            ps.setDate(2, Date.valueOf(today.plusDays(30)));
            try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Date dEssai = rs.getDate("date_fin_essai");
                if (dEssai == null) continue;

                LocalDate dateFinEssai = dEssai.toLocalDate();
                long jours = ChronoUnit.DAYS.between(today, dateFinEssai);
                if (jours < -30 || jours > 30) continue;

                String nomPrenom = rs.getString("nom_prenom");
                int idEmploye = rs.getInt("id_employe");

                if (jours < 0) {
                    long joursEchus = Math.abs(jours);
                    insererNotification(c, null, "ESSAI_ECHU_SANS_DECISION", "CRITIQUE",
                        "Période d'essai échue sans décision - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " a pris fin depuis " + joursEchus + " jour(s) (le " + dateFinEssai + ") sans décision formelle.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours == 0) {
                    insererNotification(c, null, "ESSAI_EXPIRE_J", "CRITIQUE",
                        "Fin de periode d'essai AUJOURD'HUI - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine ce soir. Décision requise : Confirmer ou Rompre l'essai.",
                        "/employes/" + idEmploye + "?tab=carriere", idEmploye);
                } else if (jours <= 7) {
                    insererNotification(c, null, "ESSAI_EXPIRE_7J", "HAUTE",
                        "Fin d'essai dans " + jours + " jour(s) - " + nomPrenom,
                        "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jour(s). Anticipez la décision.",
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
        LocalDate echeanceMax = LocalDate.now().plusDays(30);
        for (Map.Entry<Integer, Double> entry : congeService.calculerExpirationsPotentielles(echeanceMax, c).entrySet()) {
            int idEmploye = entry.getKey();
            String nomPrenom = "Employé #" + idEmploye;
            try (PreparedStatement ps = c.prepareStatement("SELECT nom || ' ' || prenom FROM employe WHERE id_employe = ?")) {
                ps.setInt(1, idEmploye);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) nomPrenom = rs.getString(1);
                }
            }
            insererNotification(c, null, "CONGE_EXPIRATION_PROCHE", "HAUTE",
                "Droits de congé à examiner - " + nomPrenom,
                String.format("%.2f", entry.getValue()) + " jours de congé approchent du délai d'examen configuré. Vérifiez la planification avec le salarié ; aucun retrait automatique n'est effectué.",
                "/employes/" + idEmploye + "?tab=conges", idEmploye);
        }
    }

    private void genererAlertesPointageAnomalies(Connection c) throws Exception {
        String sql = "SELECT pp.id_periode_paie, pp.code, pp.libelle, pp.date_fin, " +
                     "COUNT(p.id_pointage) AS nb_anomalies " +
                     "FROM periode_paie pp " +
                     "JOIN pointage_jour p ON pp.id_periode_paie = p.id_periode_paie " +
                     "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                     "WHERE pp.statut = 'EN_COURS' " +
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
        // 1. Anti-doublon non lue : si une notification non lue du même type existe déjà pour cet employé/contexte,
        // on met à jour son titre, message, priorité et lien au lieu d'empiler des alertes redondantes.
        String checkUnreadSql;
        if (idEmploye != null) {
            checkUnreadSql = "SELECT id_notification FROM notification_rh WHERE type_alerte = ? " +
                             "AND id_employe_concerne = ? " +
                             "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                             "AND est_lue = FALSE LIMIT 1";
        } else {
            checkUnreadSql = "SELECT id_notification FROM notification_rh WHERE type_alerte = ? " +
                             "AND id_employe_concerne IS NULL " +
                             "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                             "AND (lien = ? OR titre = ?) AND est_lue = FALSE LIMIT 1";
        }

        try (PreparedStatement chk = c.prepareStatement(checkUnreadSql)) {
            int p = 1;
            chk.setString(p++, typeAlerte);
            if (idEmploye != null) {
                chk.setInt(p++, idEmploye);
            }
            if (idUtilisateur != null) chk.setInt(p++, idUtilisateur); else chk.setNull(p++, java.sql.Types.INTEGER);
            if (idEmploye == null) {
                chk.setString(p++, lien != null ? lien : "");
                chk.setString(p++, titre != null ? titre : "");
            }
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    int existingId = rs.getInt(1);
                    String updateSql = "UPDATE notification_rh SET titre = ?, message = ?, priorite = ?, lien = ? WHERE id_notification = ?";
                    try (PreparedStatement upd = c.prepareStatement(updateSql)) {
                        upd.setString(1, titre);
                        upd.setString(2, message);
                        upd.setString(3, priorite);
                        upd.setString(4, lien);
                        upd.setInt(5, existingId);
                        upd.executeUpdate();
                    }
                    return; // Mise à jour effectuée, pas de doublon inséré
                }
            }
        }

        // 2. Anti-resurfaçage 7 jours : si l'alerte a déjà été lue/acquittée récemment (< 7 jours),
        // on ne la ressuscite pas, même si le texte du titre a varié d'un jour ou d'un compteur.
        Timestamp seuil7j = Timestamp.valueOf(LocalDateTime.now().minusDays(7));
        String checkReadSql;
        if (idEmploye != null) {
            checkReadSql = "SELECT 1 FROM notification_rh WHERE type_alerte = ? " +
                           "AND id_employe_concerne = ? " +
                           "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                           "AND est_lue = TRUE AND COALESCE(date_lecture, date_creation) > ? LIMIT 1";
        } else {
            checkReadSql = "SELECT 1 FROM notification_rh WHERE type_alerte = ? " +
                           "AND id_employe_concerne IS NULL " +
                           "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                           "AND (lien = ? OR titre = ?) AND est_lue = TRUE AND COALESCE(date_lecture, date_creation) > ? LIMIT 1";
        }

        try (PreparedStatement chk = c.prepareStatement(checkReadSql)) {
            int p = 1;
            chk.setString(p++, typeAlerte);
            if (idEmploye != null) {
                chk.setInt(p++, idEmploye);
            }
            if (idUtilisateur != null) chk.setInt(p++, idUtilisateur); else chk.setNull(p++, java.sql.Types.INTEGER);
            if (idEmploye == null) {
                chk.setString(p++, lien != null ? lien : "");
                chk.setString(p++, titre != null ? titre : "");
            }
            chk.setTimestamp(p++, seuil7j);
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    return; // Déjà acquittée récemment par l'utilisateur
                }
            }
        }

        // 3. INSERT via source unique de vérité
        NotificationRhService.creer(c, typeAlerte, priorite, titre, message, lien, idEmploye, idUtilisateur, cibleRole);
    }
}
