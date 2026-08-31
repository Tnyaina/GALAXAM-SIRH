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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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

    public static class EmployeSoldeAccumulateur {
        public double acquisAnciens = 0.0;
        public double totalPris = 0.0;
        public double totalDejaExpire = 0.0;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @EventListener(ApplicationReadyEvent.class)
    public void purgerCongesExpires() {
        log.info("[PurgeCongeScheduler] Lancement de la purge triennale FIFO (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Calcul de la date seuil des 36 mois en Java pur
                LocalDate seuilPrescription = LocalDate.now().minusMonths(36);

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
                                         "VALUES (?, 'EXPIRATION', ?, CURRENT_DATE, 'Purge légale triennale FIFO (droits de +36 mois prescrits)')";

                String insertNotif = "INSERT INTO notification_rh " +
                                     "(type_alerte, priorite, titre, message, lien, id_employe_concerne) " +
                                     "VALUES ('ALERTE_ECHEANCE', 'HAUTE', ?, ?, ?, ?)";

                try (PreparedStatement psIn = c.prepareStatement(insertMouvement);
                     PreparedStatement psNotif = c.prepareStatement(insertNotif)) {

                    for (Map.Entry<Integer, EmployeSoldeAccumulateur> entry : soldesMap.entrySet()) {
                        int idEmploye = entry.getKey();
                        EmployeSoldeAccumulateur acc = entry.getValue();

                        double joursAExpirer = purgeFifoEngine.calculerJoursAExpier(acc.acquisAnciens, acc.totalPris, acc.totalDejaExpire);

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