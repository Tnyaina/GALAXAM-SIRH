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
import mg.galaxam.sirh.conge.domain.PurgeFifoEngine;
import mg.galaxam.sirh.dashboard.application.NotificationRhService;
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
    private SchedulerExecutionRegistry executionRegistry;

    public static class EmployeSoldeAccumulateur {
        public double stockHistoriqueInitial = 0.0;
        public List<PurgeFifoEngine.DroitDate> droitsDates = new ArrayList<>();
        public double totalPris = 0.0;
        public double totalAnnulations = 0.0;
        public double totalAjustementsNegatifs = 0.0;
        public double totalDejaExpire = 0.0;
        public double acquisAnciens = 0.0;

        public double getConsommationNette() {
            return Math.max(0.0, totalPris - totalAnnulations + totalAjustementsNegatifs);
        }
    }

    @Scheduled(cron = "0 0 2 * * ?", zone = "Indian/Antananarivo")
    public void purgerCongesExpires() {
        executionRegistry.runDaily("PURGE_CONGES", () -> purgerCongesExpiresInterne(LocalDate.now()));
    }

    public void purgerCongesExpires(LocalDate dateReference) {
        executionRegistry.run("PURGE_CONGES", dateReference != null ? dateReference : LocalDate.now(),
                () -> purgerCongesExpiresInterne(dateReference));
    }

    private void purgerCongesExpiresInterne(LocalDate dateReference) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();
        log.info("[PurgeCongeScheduler] Lancement de la purge triennale FIFO (Moteur Java) au {}...", ref);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                purgerCongesExpires(ref, c);
                c.commit();
                log.info("[PurgeCongeScheduler] Purge triennale FIFO effectuée avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[PurgeCongeScheduler] Erreur lors de la purge des congés : {}", e.getMessage(), e);
                throw e;
            }
        } catch (Exception e) {
            log.error("[PurgeCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
            throw e;
        }
    }

    public int purgerCongesExpires(LocalDate dateReference, Connection c) throws Exception {
        LocalDate ref = dateReference != null ? dateReference : LocalDate.now();

        // 1. Calcul de la date seuil de prescription (dynamisée depuis parametre_global, défaut 36 mois)
        java.util.Map<String, String> params = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.loadActiveMap(c);
        int moisPrescription = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.getInt(params, "DUREE_PRESCRIPTION_SOLDE_CONGE_MOIS", 36);
        LocalDate seuilPrescription = ref.minusMonths(moisPrescription);

        // 2. Extraction brute des mouvements sans logique complexe en SQL
        String sql = "SELECT id_employe, id_mouvement, type_mouvement, nb_jours, date_mouvement, commentaire " +
                     "FROM mouvement_solde_conge ORDER BY date_mouvement ASC, id_mouvement ASC";
        Map<Integer, EmployeSoldeAccumulateur> soldesMap = new HashMap<>();

        try (PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int idEmploye = rs.getInt("id_employe");
                int idMouvement = rs.getInt("id_mouvement");
                String typeMouvement = rs.getString("type_mouvement");
                double nbJours = rs.getDouble("nb_jours");
                Date dateMvtSql = rs.getDate("date_mouvement");
                LocalDate dateMvt = dateMvtSql != null ? dateMvtSql.toLocalDate() : ref;
                String commentaire = rs.getString("commentaire");

                EmployeSoldeAccumulateur acc = soldesMap.computeIfAbsent(idEmploye, k -> new EmployeSoldeAccumulateur());

                // RÈGLE CANONIQUE RG-CONGE-06 (Phase 2A-3D) :
                // Stock historique d'âge inconnu [SOLDE_INITIAL] -> Priorité 1 d'imputation, non purgeable automatiquement
                if ("AJUSTEMENT".equals(typeMouvement) && commentaire != null && commentaire.startsWith("[SOLDE_INITIAL]")) {
                    acc.stockHistoriqueInitial += nbJours;
                } else if ("ACQUISITION".equals(typeMouvement) || "BONIFICATION".equals(typeMouvement)) {
                    // Droits datés authentiques -> Priorité 2 d'imputation selon FIFO chronologique
                    acc.droitsDates.add(new PurgeFifoEngine.DroitDate(idMouvement, dateMvt, nbJours));
                    if (dateMvt.isBefore(seuilPrescription)) {
                        acc.acquisAnciens += nbJours;
                    }
                } else if ("PRISE".equals(typeMouvement)) {
                    acc.totalPris += (-nbJours);
                } else if ("ANNULATION".equals(typeMouvement)) {
                    acc.totalAnnulations += nbJours;
                } else if ("AJUSTEMENT".equals(typeMouvement) && nbJours < 0) {
                    acc.totalAjustementsNegatifs += (-nbJours);
                } else if ("EXPIRATION".equals(typeMouvement)) {
                    acc.totalDejaExpire += (-nbJours);
                }
            }
        }

        // 3. Application de l'imputation RG-CONGE-06 et calcul de l'expiration FIFO
        String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                 "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                 "VALUES (?, 'EXPIRATION', ?, ?, ?)";

        int nbPurges = 0;
        try (PreparedStatement psIn = c.prepareStatement(insertMouvement)) {
            for (Map.Entry<Integer, EmployeSoldeAccumulateur> entry : soldesMap.entrySet()) {
                int idEmploye = entry.getKey();
                EmployeSoldeAccumulateur acc = entry.getValue();

                PurgeFifoEngine.ImputationResult imputation = purgeFifoEngine.calculerImputation(
                        acc.stockHistoriqueInitial, acc.droitsDates, acc.getConsommationNette());

                double joursAExpirer = purgeFifoEngine.calculerJoursAExpierFifo(imputation, seuilPrescription, acc.totalDejaExpire);

                if (joursAExpirer > 0.001) {
                    psIn.setInt(1, idEmploye);
                    psIn.setDouble(2, -joursAExpirer);
                    psIn.setDate(3, Date.valueOf(ref));
                    psIn.setString(4, "Purge légale FIFO (droits datés de +" + moisPrescription + " mois prescrits)");
                    psIn.executeUpdate();

                    try {
                        NotificationRhService.creer(c, "ALERTE_ECHEANCE", "HAUTE",
                            "Congés expirés purgés (FIFO)",
                            "Attention, " + String.format("%.2f", joursAExpirer)
                                + " jours de congés acquis il y a plus de " + moisPrescription + " mois ont atteint leur limite d'utilisation et ont été expirés.",
                            "/employes/" + idEmploye + "?tab=conges",
                            idEmploye);
                    } catch (Exception eNotif) {
                        log.warn("[PurgeCongeScheduler] Impossible de créer la notification pour l'employé ID {}: {}", idEmploye, eNotif.getMessage());
                    }

                    log.info("[PurgeCongeScheduler] Employé ID {} : {} jours expirés purgés selon FIFO.", idEmploye, joursAExpirer);
                    nbPurges++;
                }
            }
        }

        return nbPurges;
    }
}
