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

/**
 * Scheduler nocturne pour la purge triennale des droits aux congés.
 * S'exécute chaque nuit à 02:00:00 pour purger au jour le jour les droits ayant atteint 36 mois.
 */
@Component
public class PurgeCongeScheduler {

    @Autowired
    private DataSource dataSource;

    /**
     * Exécuté chaque nuit à 02h00.
     * Calcule au jour le jour pour chaque employé s'il lui reste des jours de congés acquis il y a plus de 36 mois
     * qui n'ont pas encore été consommés (règle FIFO).
     * Si oui, génère un mouvement d'EXPIRATION.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @EventListener(ApplicationReadyEvent.class)
    public void purgerCongesExpires() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Requête FIFO : Jours Acquis il y a +36 mois VS Jours Consommés au total
                // Si la différence est positive, ces jours sont périmés.
                String sql = "SELECT id_employe, " +
                             "COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                             "                               AND date_mouvement < CURRENT_DATE - INTERVAL '36 months'), 0) AS total_acquis_anciens, " +
                             "COALESCE(SUM(-nb_jours) FILTER (WHERE nb_jours < 0), 0) AS total_consomme " +
                             "FROM mouvement_solde_conge " +
                             "GROUP BY id_employe " +
                             "HAVING COALESCE(SUM(nb_jours) FILTER (WHERE type_mouvement IN ('ACQUISITION', 'BONIFICATION') " +
                             "                                       AND date_mouvement < CURRENT_DATE - INTERVAL '36 months'), 0) " +
                             "       - COALESCE(SUM(-nb_jours) FILTER (WHERE nb_jours < 0), 0) > 0";

                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                             "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                             "VALUES (?, 'EXPIRATION', ?, CURRENT_DATE, 'Purge légale triennale (droits prescrits de +36 mois)')";
                    
                    try (PreparedStatement psIn = c.prepareStatement(insertMouvement)) {
                        while (rs.next()) {
                            int idEmploye = rs.getInt("id_employe");
                            double acquisAnciens = rs.getDouble("total_acquis_anciens");
                            double consomme = rs.getDouble("total_consomme");
                            double joursAExpirer = acquisAnciens - consomme;
                            
                            if (joursAExpirer > 0) {
                                psIn.setInt(1, idEmploye);
                                psIn.setDouble(2, -joursAExpirer); // Négatif car c'est un débit
                                psIn.executeUpdate();
                                System.out.println("[PurgeCongeScheduler] Employé ID " + idEmploye + " : " + joursAExpirer + " jours expirés purgés.");
                            }
                        }
                    }
                }
                
                c.commit();
                System.out.println("[PurgeCongeScheduler] Purge triennale FIFO effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                System.err.println("[PurgeCongeScheduler] Erreur lors de la purge des congés : " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("[PurgeCongeScheduler] Erreur connexion BDD : " + e.getMessage());
        }
    }
}
