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
 * Scheduler nocturne pour la purge triennale des droits aux congés selon la règle FIFO stricte.
 * S'exécute chaque nuit à 02:00:00 (et déclenchable manuellement par la DAF).
 */
@Component
public class PurgeCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(PurgeCongeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Scheduled(cron = "0 0 2 * * ?")
    @EventListener(ApplicationReadyEvent.class)
    public void purgerCongesExpires() {
        log.info("[PurgeCongeScheduler] Lancement de la purge triennale FIFO...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Algorithme FIFO strict :
                // 1. Isole les droits acquis il y a plus de 36 mois (total_acquis_anciens).
                // 2. Isole les consommations effectives de congés (total_pris).
                // 3. Isole les expirations déjà actées (total_deja_expire).
                String sql = "SELECT id_employe, " +
                             "COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                             "                               AND date_mouvement < CURRENT_DATE - INTERVAL '36 months'), 0) AS total_acquis_anciens, " +
                             "COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'PRISE' OR (type_mouvement = 'AJUSTEMENT' AND nb_jours < 0)), 0) AS total_pris, " +
                             "COALESCE(SUM(-nb_jours) FILTER (WHERE type_mouvement = 'EXPIRATION'), 0) AS total_deja_expire " +
                             "FROM mouvement_solde_conge " +
                             "GROUP BY id_employe";

                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                             "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                             "VALUES (?, 'EXPIRATION', ?, CURRENT_DATE, 'Purge légale triennale FIFO (droits de +36 mois prescrits)')";
                    
                    String insertNotif = "INSERT INTO notification_rh " +
                                         "(type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                                         "VALUES ('ALERTE_ECHEANCE', 'HAUTE', ?, ?, ?, ?)";
                    
                    try (PreparedStatement psIn = c.prepareStatement(insertMouvement);
                         PreparedStatement psNotif = c.prepareStatement(insertNotif)) {
                        
                        while (rs.next()) {
                            int idEmploye = rs.getInt("id_employe");
                            double acquisAnciens = rs.getDouble("total_acquis_anciens");
                            double totalPris = rs.getDouble("total_pris");
                            double totalDejaExpire = rs.getDouble("total_deja_expire");

                            // Règle FIFO : les congés pris consomment en priorité les droits les plus anciens
                            double consommeSurAnciens = Math.min(acquisAnciens, totalPris);
                            double anciensNonConsommes = Math.max(0.0, acquisAnciens - consommeSurAnciens);

                            // Le reliquat à expirer est ce qui n'a pas encore été purgé
                            double joursAExpirer = Math.max(0.0, anciensNonConsommes - totalDejaExpire);
                            
                            if (joursAExpirer > 0.001) {
                                psIn.setInt(1, idEmploye);
                                psIn.setDouble(2, -joursAExpirer);
                                psIn.executeUpdate();
                                
                                psNotif.setString(1, "Congés expirés purgés (FIFO)");
                                psNotif.setString(2, "Attention, " + String.format("%.2f", joursAExpirer) + " jours de congés acquis il y a plus de 3 ans ont atteint leur limite d'utilisation et ont été expirés.");
                                psNotif.setString(3, "/employes/" + idEmploye + "?tab=conges");
                                psNotif.setInt(4, idEmploye);
                                psNotif.executeUpdate();
                                
                                log.info("[PurgeCongeScheduler] Employé ID {} : {} jours expirés purgés selon FIFO.", idEmploye, joursAExpirer);
                            }
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