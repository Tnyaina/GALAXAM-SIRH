package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
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

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler nocturne pour la purge triennale des droits aux congés selon la règle FIFO stricte.
 * 100% de la logique d'agrégation et de calcul FIFO est exécutée en mémoire Java.
 */
@Component
public class PurgeCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeCongeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PurgeFifoEngine purgeFifoEngine;

    @Autowired
    private NotificationRhService notificationService;

    public static class EmployeSoldeAccumulateur {
        public double acquisAnciens = 0.0;
        public double totalPris = 0.0;
        public double totalDejaExpire = 0.0;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void purgerCongesExpires() {
        log.info("[PurgeCongeScheduler] Lancement de la purge triennale FIFO (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Calcul de la date seuil de prescription (dynamisée depuis parametre_global, défaut 36 mois)
                mg.galaxam.sirh.model.paieconfig.ParametreGlobal pgDummy = null;
                java.util.Map<String, String> params = mg.galaxam.sirh.model.paieconfig.ParametreGlobal.loadActiveMap(c);
                int moisPrescription = mg.galaxam.sirh.model.paieconfig.ParametreGlobal.getInt(params, "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS", 36);
                LocalDate seuilPrescription = LocalDate.now().minusMonths(moisPrescription);

                // 2. Extraction brute des mouvements sans logique complexe en SQL
                String sql = "SELECT id_employe, type_mouvement, nb_jours, date_mouvement FROM mouvement_solde_conge";
                Map<Integer, EmployeSoldeAccumulateur> soldesMap = new HashMap<>();

                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int idEmploye = rs.getInt("id_employe");
                        String typeMouvement = rs.getString("type_mouvement");
                        double nbJours = rs.getDouble("nb_jours");
                        Date dateMvtSql = rs.getDate("date_mouvement");
                        LocalDate dateMvt = dateMvtSql != null ? dateMvtSql.toLocalDate() : LocalDate.now();

                        EmployeSoldeAccumulateur acc = soldesMap.computeIfAbsent(idEmploye, k -> new EmployeSoldeAccumulateur());

                        if (("ACQUISITION".equals(typeMouvement) || "BONIFICATION".equals(typeMouvement)) && dateMvt.isBefore(seuilPrescription)) {
                            acc.acquisAnciens += nbJours;
                        } else if ("PRISE".equals(typeMouvement) || ("AJUSTEMENT".equals(typeMouvement) && nbJours < 0)) {
                            acc.totalPris += (-nbJours);
                        } else if ("EXPIRATION".equals(typeMouvement)) {
                            acc.totalDejaExpire += (-nbJours);
                        }
                    }
                }

                // 3. Application du Domain Engine FIFO et persistance des expirations
                String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                         "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                         "VALUES (?, 'EXPIRATION', ?, CURRENT_DATE, ?)";

                try (PreparedStatement psIn = c.prepareStatement(insertMouvement)) {

                    for (Map.Entry<Integer, EmployeSoldeAccumulateur> entry : soldesMap.entrySet()) {
                        int idEmploye = entry.getKey();
                        EmployeSoldeAccumulateur acc = entry.getValue();

                        double joursAExpirer = purgeFifoEngine.calculerJoursAExpier(acc.acquisAnciens, acc.totalPris, acc.totalDejaExpire);

                        if (joursAExpirer > 0.001) {
                            psIn.setInt(1, idEmploye);
                            psIn.setDouble(2, -joursAExpirer);
                            psIn.setString(3, "Purge légale FIFO (droits de +" + moisPrescription + " mois prescrits)");
                            psIn.executeUpdate();

                            notificationService.creer(c, "ALERTE_ECHEANCE", "HAUTE",
                                "Congés expirés purgés (FIFO)",
                                "Attention, " + String.format("%.2f", joursAExpirer)
                                    + " jours de congés acquis il y a plus de " + moisPrescription + " mois ont atteint leur limite d'utilisation et ont été expirés.",
                                "/employes/" + idEmploye + "?tab=conges",
                                idEmploye);

                            log.info("[PurgeCongeScheduler] Employé ID {} : {} jours expirés purgés selon FIFO.", idEmploye, joursAExpirer);
                        }
                    }
                }

                c.commit();
                log.info("[PurgeCongeScheduler] Purge triennale FIFO effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[PurgeCongeScheduler] Erreur lors de la purge des congés : {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[PurgeCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
        }
    }
}