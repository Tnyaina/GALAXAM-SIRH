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
 * S'exécute chaque nuit à 02:00:00.
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
                // Algorithme FIFO : Isole les droits de +36 mois et déduit les consommations 
                // qui n'ont pas pu être absorbées par les droits récents.
                String sql = "SELECT id_employe, " +
                             "COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                             "                               AND date_mouvement < CURRENT_DATE - INTERVAL '36 months'), 0) AS total_acquis_anciens, " +
                             "COALESCE(SUM(-nb_jours) FILTER (WHERE nb_jours < 0), 0) AS total_consomme, " +
                             "COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                             "                               AND date_mouvement >= CURRENT_DATE - INTERVAL '36 months'), 0) AS total_acquis_recents " +
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
                            double totalConsomme = rs.getDouble("total_consomme");
                            double acquisRecents = rs.getDouble("total_acquis_recents");

                            double consommeRestantSurAnciens = Math.max(0, totalConsomme - acquisRecents);
                            double joursAExpirer = Math.max(0, acquisAnciens - consommeRestantSurAnciens);
                            
                            if (joursAExpirer > 0) {
                                psIn.setInt(1, idEmploye);
                                psIn.setDouble(2, -joursAExpirer);
                                psIn.executeUpdate();
                                
                                psNotif.setString(1, "Congés expirés purgés (FIFO)");
                                psNotif.setString(2, "Attention, " + joursAExpirer + " jours de congés acquis il y a plus de 3 ans ont atteint leur limite d'utilisation et ont été expirés.");
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