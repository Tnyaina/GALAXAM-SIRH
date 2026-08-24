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
 * Scheduler pour l'acquisition mensuelle des droits aux congés.
 * S'exécute chaque nuit à 01:00:00 pour créditer les employés 
 * dont c'est la date d'anniversaire de contrat.
 */
@Component
public class AcquisitionCongeScheduler {

    @Autowired
    private DataSource dataSource;

    /**
     * Rattrapage automatique au démarrage de l'application Spring Boot.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrageApplication() {
        acquerirCongesMensuels();
    }

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
                String sql = "SELECT ct.id_employe, ct.date_debut " +
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
                    
                    String checkMouvement = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_employe = ? AND type_mouvement = ? AND date_mouvement = CURRENT_DATE";
                    String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                             "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                             "VALUES (?, ?, ?, CURRENT_DATE, ?)";
                                             
                    String insertNotif = "INSERT INTO notification_rh " +
                                         "(type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                                         "VALUES ('BONIFICATION_ANCIENNETE_APPLIQUEE', 'BASSE', ?, ?, ?, ?)";
                    
                    try (PreparedStatement psCheck = c.prepareStatement(checkMouvement);
                         PreparedStatement psIn = c.prepareStatement(insertMouvement);
                         PreparedStatement psNotif = c.prepareStatement(insertNotif)) {
                        
                        java.time.LocalDate today = java.time.LocalDate.now();
                        
                        while (rs.next()) {
                            int idEmploye = rs.getInt("id_employe");
                            java.sql.Date dateDebutSql = rs.getDate("date_debut");
                            
                            // Vérifier si l'acquisition a déjà été faite aujourd'hui
                            psCheck.setInt(1, idEmploye);
                            psCheck.setString(2, "ACQUISITION");
                            boolean dejaAcquis = false;
                            try (ResultSet rsChk = psCheck.executeQuery()) {
                                if (rsChk.next() && rsChk.getInt(1) > 0) dejaAcquis = true;
                            }

                            if (!dejaAcquis) {
                                // Log Mouvement Acquisition (2.5j)
                                psIn.setInt(1, idEmploye);
                                psIn.setString(2, "ACQUISITION");
                                psIn.setDouble(3, 2.5);
                                psIn.setString(4, "Acquisition mensuelle légale (2.5j)");
                                psIn.executeUpdate();
                            }
                            
                            // Vérification Bonification d'Ancienneté (Option A)
                            if (dateDebutSql != null) {
                                java.time.LocalDate dateDebut = dateDebutSql.toLocalDate();
                                // Si c'est le mois anniversaire
                                if (dateDebut.getMonthValue() == today.getMonthValue()) {
                                    int anneesAnciennete = today.getYear() - dateDebut.getYear();
                                    
                                    // Règle: à partir de 3 ans, bonus = annees / 5
                                    if (anneesAnciennete >= 3) {
                                        int bonus = anneesAnciennete / 5;
                                        if (bonus > 0) {
                                            psCheck.setInt(1, idEmploye);
                                            psCheck.setString(2, "BONIFICATION");
                                            boolean dejaBonifie = false;
                                            try (ResultSet rsChk = psCheck.executeQuery()) {
                                                if (rsChk.next() && rsChk.getInt(1) > 0) dejaBonifie = true;
                                            }

                                            if (!dejaBonifie) {
                                                // 1. Mouvement BONIFICATION
                                                psIn.setInt(1, idEmploye);
                                                psIn.setString(2, "BONIFICATION");
                                                psIn.setDouble(3, bonus);
                                                psIn.setString(4, "Bonification d'ancienneté annuelle (" + anneesAnciennete + " ans = +" + bonus + "j)");
                                                psIn.executeUpdate();
                                                
                                                // 3. Notification RH
                                                psNotif.setString(1, "Bonification acquise - " + anneesAnciennete + " ans");
                                                psNotif.setString(2, "Félicitations, vous avez reçu " + bonus + " jour(s) de bonification pour vos " + anneesAnciennete + " ans d'ancienneté.");
                                                psNotif.setString(3, "/employes/" + idEmploye + "?tab=conges");
                                                psNotif.setInt(4, idEmploye);
                                                psNotif.executeUpdate();
                                                
                                                System.out.println("[AcquisitionCongeScheduler] Bonification accordée : Employé " + idEmploye + " -> +" + bonus + " jours.");
                                            }
                                        }
                                    }
                                }
                            }
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
