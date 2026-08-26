package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * NotificationRhScheduler — Génère les notifications RH persistées en base.
 *
 * Pattern professionnel : le scheduler tourne au démarrage et chaque nuit APRÈS le scheduler
 * de mouvements (00:05:00) pour que les données soient à jour.
 * Il supprime les anciennes notifications du même type avant d'en créer
 * de nouvelles (évite les doublons).
 */
@Component
public class NotificationRhScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationRhScheduler.class);

    @Autowired
    private DataSource dataSource;

    /**
     * Exécution automatique au démarrage de l'application (rattrapage immédiat)
     * et chaque nuit à 00:05:00.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 5 0 * * ?")
    public void genererNotificationsRh() {
        log.info("[SCHEDULER] Lancement du cycle de génération des notifications RH...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Supprimer les anciennes notifications non critiques (J-1)
                purguerAnciennesNotifications(c);

                // 2. Générer les nouvelles alertes
                genererAlerteContratsJour(c);
                genererAlerteContrats7j(c);
                genererAlerteContrats30j(c);
                genererAlerteEssaisJour(c);
                genererAlerteEssais7j(c);
                genererAlerteEssais15j(c);
                genererAlerteEssais30j(c);
                genererAlerteBrouillons48h(c);
                genererAlerteCongesEnAttente48h(c);
                genererAlertePiecesManquantes(c);
                
                // Alertes Congés
                genererAlerteSoldeFaible(c);
                genererAlerteRetourCongeJour(c);
                genererAlerteFractionnement(c);
                genererAlerteMaladieProlongee(c);
                genererAlerteExpirationProche(c);

                // Alertes Pointage
                genererAlertesPointageAnomalies(c);

                c.commit();
                System.out.println("[NotificationRhScheduler] Notifications RH générées avec succès.");
            } catch (Exception e) {
                c.rollback();
                System.err.println("[NotificationRhScheduler] Erreur lors de la génération : " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("[NotificationRhScheduler] Erreur connexion BDD : " + e.getMessage());
        }
    }

    // =========================================================================
    // Purge
    // =========================================================================

    private void purguerAnciennesNotifications(Connection c) throws Exception {
        // 1. Supprimer les notifications lues de plus de 7 jours (l'anti-resurfaçage les protège pendant 7j)
        String sqlLues = "DELETE FROM notification_rh WHERE est_lue = TRUE AND date_creation < CURRENT_TIMESTAMP - INTERVAL '7 days'";
        try (PreparedStatement ps = c.prepareStatement(sqlLues)) {
            ps.executeUpdate();
        }

        // 2. Supprimer les notifications non lues très anciennes (+30 jours)
        String sqlNonLues = "DELETE FROM notification_rh WHERE est_lue = FALSE AND date_creation < CURRENT_TIMESTAMP - INTERVAL '30 days'";
        try (PreparedStatement ps = c.prepareStatement(sqlNonLues)) {
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Génération des alertes
    // =========================================================================

    private void genererAlerteContratsJour(Connection c) throws Exception {
        // Exclusion CDI (exige_date_fin = FALSE) : un CDI n'a pas de date de fin par définition
        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, rtc.libelle AS type_contrat, ct.numero_contrat " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin = CURRENT_DATE";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                String typeContrat = rs.getString("type_contrat");
                String numero = rs.getString("numero_contrat");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null, // broadcast
                    "CONTRAT_EXPIRE_J",
                    "CRITIQUE",
                    "Contrat expirant AUJOURD'HUI - " + nomPrenom,
                    "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " arrive à échéance ce soir à minuit. Une action RH est requise.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteContrats7j(Connection c) throws Exception {
        // Exclusion CDI
        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "e.matricule, rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "ct.date_fin - CURRENT_DATE AS jours " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE " +
                "AND ct.date_fin <= CURRENT_DATE + INTERVAL '7 days'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours");
                int idEmploye = rs.getInt("id_employe");
                String numero = rs.getString("numero_contrat");
                String typeContrat = rs.getString("type_contrat");

                insererNotification(c,
                    null,
                    "CONTRAT_EXPIRE_7J",
                    "HAUTE",
                    "Contrat expirant dans " + jours + " jour(s) - " + nomPrenom,
                    "Le contrat " + typeContrat + " N°" + (numero != null ? numero : "?") +
                        " de " + nomPrenom + " expire dans " + jours + " jour(s). Prévoir le renouvellement ou la clôture.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteContrats30j(Connection c) throws Exception {
        // Exclusion CDI
        String sql = "SELECT ct.id_contrat, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "rtc.libelle AS type_contrat, ct.numero_contrat, " +
                "ct.date_fin - CURRENT_DATE AS jours " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "JOIN ref_type_contrat rtc ON ct.id_type_contrat = rtc.id_type_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND rtc.exige_date_fin = TRUE " +
                "AND ct.date_fin > CURRENT_DATE + INTERVAL '7 days' " +
                "AND ct.date_fin <= CURRENT_DATE + INTERVAL '30 days'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null,
                    "CONTRAT_EXPIRE_30J",
                    "NORMALE",
                    "Contrat expirant dans " + jours + " jours - " + nomPrenom,
                    "Le contrat de " + nomPrenom + " expire dans " + jours + " jours.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteEssaisJour(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND ct.date_fin_essai = CURRENT_DATE";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null,
                    "ESSAI_EXPIRE_J",
                    "CRITIQUE",
                    "Fin de periode d'essai AUJOURD'HUI - " + nomPrenom,
                    "La période d'essai de " + nomPrenom + " se termine ce soir. Décision requise : Confirmer ou Rompre l'essai.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteEssais7j(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "ct.date_fin_essai - CURRENT_DATE AS jours " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND ct.date_fin_essai > CURRENT_DATE " +
                "AND ct.date_fin_essai <= CURRENT_DATE + INTERVAL '7 days'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null,
                    "ESSAI_EXPIRE_7J",
                    "HAUTE",
                    "Fin d'essai dans " + jours + " jour(s) - " + nomPrenom,
                    "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jour(s). Anticipez la décision RH.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteEssais15j(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "ct.date_fin_essai - CURRENT_DATE AS jours " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND ct.date_fin_essai > CURRENT_DATE + INTERVAL '7 days' " +
                "AND ct.date_fin_essai <= CURRENT_DATE + INTERVAL '15 days'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null,
                    "ESSAI_EXPIRE_15J",
                    "NORMALE",
                    "Fin d'essai dans " + jours + " jours - " + nomPrenom,
                    "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jours.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteEssais30j(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "ct.date_fin_essai - CURRENT_DATE AS jours " +
                "FROM contrat_travail ct " +
                "JOIN employe e ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND ct.date_fin_essai > CURRENT_DATE + INTERVAL '15 days' " +
                "AND ct.date_fin_essai <= CURRENT_DATE + INTERVAL '30 days'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours");
                int idEmploye = rs.getInt("id_employe");

                insererNotification(c,
                    null,
                    "ESSAI_EXPIRE_30J",
                    "NORMALE",
                    "Fin d'essai dans " + jours + " jours - " + nomPrenom,
                    "La période d'essai de " + nomPrenom + " se termine dans " + jours + " jours.",
                    "/employes/" + idEmploye + "?tab=carriere",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteBrouillons48h(Connection c) throws Exception {
        String sql = "SELECT wf.id_workflow, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "tev.libelle AS type_evenement, " +
                "EXTRACT(DAY FROM CURRENT_TIMESTAMP - wf.created_at)::INT AS jours_attente " +
                "FROM workflow_rh wf " +
                "JOIN employe e ON wf.id_employe = e.id_employe " +
                "JOIN ref_type_evenement_rh tev ON wf.id_type_evenement_rh = tev.id_type_evenement_rh " +
                "JOIN ref_statut_workflow rsw ON wf.id_statut_workflow = rsw.id_statut_workflow " +
                "WHERE rsw.code = 'BROUILLON' " +
                "AND wf.created_at < CURRENT_TIMESTAMP - INTERVAL '48 hours'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours_attente");
                int idEmploye = rs.getInt("id_employe");
                int idWorkflow = rs.getInt("id_workflow");
                String typeEv = rs.getString("type_evenement");

                insererNotification(c,
                    null,
                    "BROUILLON_EN_ATTENTE",
                    "HAUTE",
                    "Brouillon en attente depuis " + jours + "j - " + nomPrenom,
                    "Le brouillon " + typeEv + " pour " + nomPrenom + " n'a pas encore été validé ou annulé (créé il y a " + jours + " jour(s)).",
                    "/employes/" + idEmploye + "?tab=carriere&wf=" + idWorkflow,
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteCongesEnAttente48h(Connection c) throws Exception {
        String sql = "SELECT dc.id_demande_conge, e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "rtc.libelle AS type_conge, " +
                "EXTRACT(DAY FROM CURRENT_TIMESTAMP - dc.date_demande)::INT AS jours_attente " +
                "FROM demande_conge dc " +
                "JOIN employe e ON dc.id_employe = e.id_employe " +
                "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                "WHERE rsd.code = 'EN_ATTENTE' " +
                "AND dc.date_demande < CURRENT_TIMESTAMP - INTERVAL '48 hours'";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nomPrenom = rs.getString("nom_prenom");
                int jours = rs.getInt("jours_attente");
                int idEmploye = rs.getInt("id_employe");
                String typeConge = rs.getString("type_conge");

                insererNotification(c,
                    null,
                    "CONGE_EN_ATTENTE",
                    "HAUTE",
                    "Demande de conge en attente depuis " + jours + "j - " + nomPrenom,
                    "La demande de congé (" + typeConge + ") pour " + nomPrenom + " n'a pas encore été validée (soumise il y a " + jours + " jour(s)).",
                    "/conges?tab=demandes", // Redirige vers le registre des demandes
                    idEmploye
                );
            }
        }
    }

    private void genererAlertePiecesManquantes(Connection c) throws Exception {
        // Sélectionner les employés actifs et regrouper la liste de leurs documents obligatoires manquants
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "STRING_AGG(rd.libelle, ', ') AS docs_manquants " +
                "FROM employe e " +
                "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "CROSS JOIN ref_type_document rd " +
                "WHERE rsc.code = 'ACTIF' AND rd.obligatoire = TRUE AND rd.actif = TRUE " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM document_rh d " +
                "  WHERE d.id_employe = e.id_employe AND d.id_type_document = rd.id_type_document" +
                ") " +
                "GROUP BY e.id_employe, e.nom, e.prenom";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                String docs = rs.getString("docs_manquants");

                insererNotification(c,
                    null,
                    "DOCUMENTS_MANQUANTS",
                    "HAUTE",
                    "Dossier incomplet - " + nomPrenom,
                    "Pièces obligatoires manquantes : " + docs + ".",
                    "/employes/" + idEmploye + "?tab=documents",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteSoldeFaible(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, s.solde_actuel " +
                "FROM employe e " +
                "JOIN v_solde_conge_employe s ON e.id_employe = s.id_employe " +
                "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' AND s.solde_actuel > 0 AND s.solde_actuel < 5";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                double solde = rs.getDouble("solde_actuel");

                insererNotification(c,
                    null,
                    "SOLDE_CONGE_FAIBLE",
                    "INFO",
                    "Solde de congé faible - " + nomPrenom,
                    "Le solde de congés de " + nomPrenom + " est faible (" + solde + " jours restants).",
                    "/employes/" + idEmploye + "?tab=conges",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteRetourCongeJour(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "rtc.libelle AS type_conge " +
                "FROM demande_conge dc " +
                "JOIN employe e ON dc.id_employe = e.id_employe " +
                "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                "WHERE rsd.code = 'VALIDE' AND dc.date_fin = CURRENT_DATE";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                String typeConge = rs.getString("type_conge");

                insererNotification(c,
                    null,
                    "RETOUR_CONGE_J",
                    "INFO",
                    "Fin de congé aujourd'hui - " + nomPrenom,
                    nomPrenom + " termine son congé (" + typeConge + ") ce soir. Reprise prévue demain.",
                    "/employes/" + idEmploye + "?tab=conges",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteFractionnement(Connection c) throws Exception {
        // Ne tourne qu'en Novembre et Décembre (pour alerter en fin d'année)
        java.time.LocalDate today = java.time.LocalDate.now();
        if (today.getMonthValue() < 11) return;

        // Cherche les employés n'ayant aucune demande de > 11 jours cette année
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom " +
                "FROM employe e " +
                "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                "WHERE rsc.code = 'ACTIF' " +
                "AND NOT EXISTS (" +
                "  SELECT 1 FROM demande_conge dc " +
                "  JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "  JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                "  WHERE dc.id_employe = e.id_employe " +
                "  AND rsd.code = 'VALIDE' AND rtc.code = 'ANNUEL' " +
                "  AND EXTRACT(YEAR FROM dc.date_debut) = EXTRACT(YEAR FROM CURRENT_DATE) " +
                "  AND dc.nb_jours_decomptes >= 12" +
                ")";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");

                insererNotification(c,
                    null,
                    "FRACTIONNEMENT_NON_RESPECTE",
                    "HAUTE",
                    "Fractionnement obligatoire non respecté - " + nomPrenom,
                    nomPrenom + " n'a pas posé sa tranche continue de 12 jours cette année (Art. 85). Veuillez régulariser.",
                    "/employes/" + idEmploye + "?tab=conges",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteMaladieProlongee(Connection c) throws Exception {
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                "dc.nb_jours_decomptes " +
                "FROM demande_conge dc " +
                "JOIN employe e ON dc.id_employe = e.id_employe " +
                "JOIN ref_statut_demande_conge rsd ON dc.id_statut_demande_conge = rsd.id_statut_demande_conge " +
                "JOIN ref_type_conge rtc ON dc.id_type_conge = rtc.id_type_conge " +
                "WHERE rsd.code = 'VALIDE' AND rtc.code = 'MALADIE' " +
                "AND dc.nb_jours_decomptes >= 30 " +
                "AND CURRENT_DATE BETWEEN dc.date_debut AND dc.date_fin";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                double nbJours = rs.getDouble("nb_jours_decomptes");

                insererNotification(c,
                    null,
                    "MALADIE_PLAFOND_EPISODE_ATTEINT",
                    "CRITIQUE",
                    "Arrêt Maladie > 30 jours (Bascule) - " + nomPrenom,
                    nomPrenom + " est en arrêt maladie prolongé (" + nbJours + " jours). Rappel: au-delà de 30 jours continus, maintien de salaire suspendu (CNaPS).",
                    "/employes/" + idEmploye + "?tab=conges",
                    idEmploye
                );
            }
        }
    }

    private void genererAlerteExpirationProche(Connection c) throws Exception {
        // Prévision à J+30 de ce qui va expirer selon la règle FIFO
        String sql = "SELECT e.id_employe, e.nom || ' ' || e.prenom AS nom_prenom, " +
                     "COALESCE(SUM(m.nb_jours) FILTER (WHERE m.type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                     "                               AND m.date_mouvement < (CURRENT_DATE + INTERVAL '30 days') - INTERVAL '36 months'), 0) AS total_acquis_anciens, " +
                     "COALESCE(SUM(-m.nb_jours) FILTER (WHERE m.nb_jours < 0), 0) AS total_consomme " +
                     "FROM mouvement_solde_conge m " +
                     "JOIN employe e ON m.id_employe = e.id_employe " +
                     "JOIN contrat_travail ct ON ct.id_employe = e.id_employe " +
                     "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                     "WHERE rsc.code = 'ACTIF' " +
                     "GROUP BY e.id_employe, e.nom, e.prenom " +
                     "HAVING COALESCE(SUM(m.nb_jours) FILTER (WHERE m.type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                     "                                       AND m.date_mouvement < (CURRENT_DATE + INTERVAL '30 days') - INTERVAL '36 months'), 0) " +
                     "       - COALESCE(SUM(-m.nb_jours) FILTER (WHERE m.nb_jours < 0), 0) > 0";

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                String nomPrenom = rs.getString("nom_prenom");
                double acquis = rs.getDouble("total_acquis_anciens");
                double consomme = rs.getDouble("total_consomme");
                double joursRisques = acquis - consomme;

                insererNotification(c,
                    null,
                    "CONGE_EXPIRATION_PROCHE",
                    "HAUTE",
                    "Expiration de congés imminente - " + nomPrenom,
                    "Attention, " + joursRisques + " jours de congés acquis par " + nomPrenom + " il y a presque 3 ans risquent d'expirer dans les 30 prochains jours s'ils ne sont pas pris.",
                    "/employes/" + idEmploye + "?tab=conges",
                    idEmploye
                );
            }
        }
    }

    private void genererAlertesPointageAnomalies(Connection c) throws Exception {
        // Détecter les périodes en cours proches de l'échéance (25 du mois) avec des anomalies non résolues
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

                insererNotification(c,
                    null,
                    "POINTAGE_MANQUANT",
                    "HAUTE",
                    "Anomalies de pointage en attente - " + codePeriode,
                    "La période " + libellePeriode + " comporte " + nbAnomalies + " anomalie(s) de pointage non résolue(s) avant la clôture de paie.",
                    "/pointages/anomalies",
                    null
                );
            }
        }
    }

    // =========================================================================
    // Helper — Insertion d'une notification
    // =========================================================================

    private void insererNotification(Connection c, Integer idUtilisateur, String typeAlerte,
                                     String priorite, String titre, String message,
                                     String lien, Integer idEmploye) throws Exception {
        // 1. Anti-doublon non lue : ne pas réinsérer si une alerte identique non lue existe déjà
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
                    return; // Déjà présente non lue, ignorer le doublon
                }
            }
        }

        // 2. Anti-resurfaçage : si l'utilisateur l'a déjà lue / acquittée dans les 7 derniers jours, ne pas la recréer
        String checkReadSql = "SELECT 1 FROM notification_rh WHERE type_alerte = ? " +
                "AND id_employe_concerne IS NOT DISTINCT FROM ?::INTEGER " +
                "AND id_utilisateur IS NOT DISTINCT FROM ?::INTEGER " +
                "AND titre = ? AND est_lue = TRUE AND date_creation > CURRENT_TIMESTAMP - INTERVAL '7 days'";
        try (PreparedStatement chk = c.prepareStatement(checkReadSql)) {
            chk.setString(1, typeAlerte);
            if (idEmploye != null) chk.setInt(2, idEmploye); else chk.setNull(2, java.sql.Types.INTEGER);
            if (idUtilisateur != null) chk.setInt(3, idUtilisateur); else chk.setNull(3, java.sql.Types.INTEGER);
            chk.setString(4, titre);
            try (ResultSet rs = chk.executeQuery()) {
                if (rs.next()) {
                    return; // Déjà acquittée récemment par l'utilisateur, ne pas re-notifier
                }
            }
        }

        String sql = "INSERT INTO notification_rh " +
                "(id_utilisateur, type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, idUtilisateur);
            ps.setString(2, typeAlerte);
            ps.setString(3, priorite);
            ps.setString(4, titre);
            ps.setString(5, message);
            ps.setString(6, lien);
            ps.setObject(7, idEmploye);
            ps.executeUpdate();
        }
    }
}
