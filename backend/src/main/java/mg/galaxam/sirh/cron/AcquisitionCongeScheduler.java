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
 * Scheduler pour l'acquisition mensuelle des droits aux congés.
 * S'exécute chaque nuit à 01:00:00 pour créditer les employés.
 */
@Component
public class AcquisitionCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 1 * * ?")
    public void acquerirCongesMensuels() {
        log.info("[SCHEDULER] Vérification de l'acquisition mensuelle des congés...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                String sql = "SELECT ct.id_employe, ct.date_debut " +
                             "FROM contrat_travail ct " +
                             "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                             "WHERE rsc.code = 'ACTIF' " +
                             "AND ct.date_debut <= CURRENT_DATE - INTERVAL '1 month' " +
                             "AND (" +
                             "    EXTRACT(DAY FROM CURRENT_DATE) >= EXTRACT(DAY FROM ct.date_debut) " +
                             "    OR (" +
                             "        EXTRACT(MONTH FROM CURRENT_DATE + INTERVAL '1 day') != EXTRACT(MONTH FROM CURRENT_DATE) " +
                             "        AND EXTRACT(DAY FROM ct.date_debut) > EXTRACT(DAY FROM CURRENT_DATE)" +
                             "    )" +
                             ")";
                
                try (PreparedStatement ps = c.prepareStatement(sql);
                     ResultSet rs = ps.executeQuery()) {
                    
                    String checkMouvementMois = "SELECT COUNT(*) FROM mouvement_solde_conge " +
                                                "WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' " +
                                                "AND EXTRACT(YEAR FROM date_mouvement) = EXTRACT(YEAR FROM CURRENT_DATE) " +
                                                "AND EXTRACT(MONTH FROM date_mouvement) = EXTRACT(MONTH FROM CURRENT_DATE)";

                    String checkMouvementAn = "SELECT COUNT(*) FROM mouvement_solde_conge " +
                                              "WHERE id_employe = ? AND type_mouvement = 'BONIFICATION' " +
                                              "AND EXTRACT(YEAR FROM date_mouvement) = EXTRACT(YEAR FROM CURRENT_DATE)";

                    String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                             "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                             "VALUES (?, ?, ?, CURRENT_DATE, ?)";
                                             
                    String insertNotif = "INSERT INTO notification_rh " +
                                         "(type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                                         "VALUES ('MOUVEMENT_APPLIQUE', 'INFO', ?, ?, ?, ?)";
                    
                    try (PreparedStatement psCheckMois = c.prepareStatement(checkMouvementMois);
                         PreparedStatement psCheckAn = c.prepareStatement(checkMouvementAn);
                         PreparedStatement psIn = c.prepareStatement(insertMouvement);
                         PreparedStatement psNotif = c.prepareStatement(insertNotif)) {
                        
                        java.time.LocalDate today = java.time.LocalDate.now();
                        
                        while (rs.next()) {
                            int idEmploye = rs.getInt("id_employe");
                            java.sql.Date dateDebutSql = rs.getDate("date_debut");
                            
                            psCheckMois.setInt(1, idEmploye);
                            boolean dejaAcquisCeMois = false;
                            try (ResultSet rsChk = psCheckMois.executeQuery()) {
                                if (rsChk.next() && rsChk.getInt(1) > 0) dejaAcquisCeMois = true;
                            }

                            if (!dejaAcquisCeMois) {
                                psIn.setInt(1, idEmploye);
                                psIn.setString(2, "ACQUISITION");
                                psIn.setDouble(3, 2.5);
                                psIn.setString(4, "Acquisition mensuelle légale (2.5j)");
                                psIn.executeUpdate();
                            }
                            
                            if (dateDebutSql != null) {
                                java.time.LocalDate dateDebut = dateDebutSql.toLocalDate();
                                if (dateDebut.getMonthValue() == today.getMonthValue()) {
                                    int anneesAnciennete = java.time.Period.between(dateDebut, today).getYears();
                                    
                                    if (anneesAnciennete >= 3) {
                                        int bonus = anneesAnciennete / 5;
                                        if (bonus > 0) {
                                            psCheckAn.setInt(1, idEmploye);
                                            boolean dejaBonifieCetteAnnee = false;
                                            try (ResultSet rsChk = psCheckAn.executeQuery()) {
                                                if (rsChk.next() && rsChk.getInt(1) > 0) dejaBonifieCetteAnnee = true;
                                            }

                                            if (!dejaBonifieCetteAnnee) {
                                                psIn.setInt(1, idEmploye);
                                                psIn.setString(2, "BONIFICATION");
                                                psIn.setDouble(3, bonus);
                                                psIn.setString(4, "Bonification d'ancienneté annuelle (" + anneesAnciennete + " ans = +" + bonus + "j)");
                                                psIn.executeUpdate();
                                                
                                                psNotif.setString(1, "Bonification acquise - " + anneesAnciennete + " ans");
                                                psNotif.setString(2, "Félicitations, vous avez reçu " + bonus + " jour(s) de bonification pour vos " + anneesAnciennete + " ans d'ancienneté.");
                                                psNotif.setString(3, "/employes/" + idEmploye + "?tab=conges");
                                                psNotif.setInt(4, idEmploye);
                                                psNotif.executeUpdate();
                                                
                                                log.info("[AcquisitionCongeScheduler] Bonification accordée : Employé ID {} -> +{} jours.", idEmploye, bonus);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                c.commit();
                log.info("[AcquisitionCongeScheduler] Acquisition mensuelle des congés effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[AcquisitionCongeScheduler] Erreur lors de l'acquisition des congés : {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[AcquisitionCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
        }
    }
}
