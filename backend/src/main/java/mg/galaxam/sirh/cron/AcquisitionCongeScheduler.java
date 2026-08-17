package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour l'acquisition mensuelle des droits aux congés.
 * S'exécute chaque nuit à 01:00:00 pour créditer les employés 
 * dont c'est la date d'anniversaire de contrat.
 */
@Component
public class AcquisitionCongeScheduler {

    @Autowired
    private DataSource dataSource;

    /**
     * Exécuté chaque jour à 01h00.
     * Pour chaque employé actif dont la date anniversaire d'embauche (date_debut du contrat)
     * correspond à la date du jour (gestion des 31/février incluse), on ajoute 2.5 jours.
     */
    @Scheduled(cron = "0 0 1 * * ?")
    public void acquerirCongesMensuels() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Récupérer les employés éligibles (anniversaire mensuel d'embauche aujourd'hui)
                String sql = "SELECT ct.id_employe " +
                             "FROM contrat_travail ct " +
                             "JOIN ref_statut_contrat rsc ON ct.id_statut_contrat = rsc.id_statut_contrat " +
                             "WHERE rsc.code = 'ACTIF' " +
                             "AND (" +
                             "    EXTRACT(DAY FROM ct.date_debut) = EXTRACT(DAY FROM CURRENT_DATE) " +
                             "    OR (" +
                             "        EXTRACT(MONTH FROM CURRENT_DATE + INTERVAL '1 day') != EXTRACT(MONTH FROM CURRENT_DATE) " +
                             "        AND EXTRACT(DAY FROM ct.date_debut) > EXTRACT(DAY FROM CURRENT_DATE)" +
                             "    )" +
                             ")";
                
                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    String updateSolde = "UPDATE solde_conge_employe " +
                                         "SET solde_actuel = solde_actuel + 2.5, " +
                                         "total_acquis = total_acquis + 2.5, " +
                                         "derniere_acquisition = CURRENT_DATE " +
                                         "WHERE id_employe = ?";
                    
                    String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                             "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                             "VALUES (?, 'ACQUISITION', 2.5, CURRENT_DATE, 'Acquisition mensuelle légale (2.5j)')";
                    
                    try (PreparedStatement psUp = c.prepareStatement(updateSolde);
                         PreparedStatement psIn = c.prepareStatement(insertMouvement)) {
                        
                        while (rs.next()) {
                            int idEmploye = rs.getInt("id_employe");
                            
                            // Maj Solde
                            psUp.setInt(1, idEmploye);
                            psUp.executeUpdate();
                            
                            // Log Mouvement
                            psIn.setInt(1, idEmploye);
                            psIn.executeUpdate();
                        }
                    }
                }
                
                c.commit();
                System.out.println("[AcquisitionCongeScheduler] Acquisition mensuelle des congés effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                System.err.println("[AcquisitionCongeScheduler] Erreur lors de l'acquisition des congés : " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("[AcquisitionCongeScheduler] Erreur connexion BDD : " + e.getMessage());
        }
    }
}
