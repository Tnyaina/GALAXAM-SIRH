package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.conge.engine.CongeCalculateurEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler pour l'acquisition mensuelle des droits aux congés.
 * 100% de la logique d'éligibilité temporelle, d'acquisition et de bonification est en Java.
 */
@Component
public class AcquisitionCongeScheduler {

    private static final Logger log = LoggerFactory.getLogger(AcquisitionCongeScheduler.class);

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CongeCalculateurEngine congeCalculateurEngine;

    public static class ContratActifInfo {
        int idEmploye;
        int idContrat;
        LocalDate dateDebut;

        public ContratActifInfo(int idEmploye, int idContrat, LocalDate dateDebut) {
            this.idEmploye = idEmploye;
            this.idContrat = idContrat;
            this.dateDebut = dateDebut;
        }
    }

    public static class MouvementInfo {
        String typeMouvement;
        LocalDate dateMouvement;

        public MouvementInfo(String typeMouvement, LocalDate dateMouvement) {
            this.typeMouvement = typeMouvement;
            this.dateMouvement = dateMouvement;
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(cron = "0 0 1 * * ?")
    public void acquerirCongesMensuels() {
        log.info("[AcquisitionCongeScheduler] Vérification de l'acquisition mensuelle des congés (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                LocalDate today = LocalDate.now();
                boolean isDernierJourMois = today.plusDays(1).getMonth() != today.getMonth();

                // 1. Extraction brute des contrats actifs
                String sqlContrats = "SELECT ct.id_employe, ct.id_contrat, ct.date_debut " +
                                     "FROM contrat_travail ct " +
                                     "JOIN ref_statut_rh rsc ON ct.id_statut_rh = rsc.id_statut_rh " +
                                     "WHERE rsc.code = 'ACTIF'";

                List<ContratActifInfo> contrats = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sqlContrats);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Date dDebut = rs.getDate("date_debut");
                        if (dDebut != null) {
                            contrats.add(new ContratActifInfo(
                                rs.getInt("id_employe"),
                                rs.getInt("id_contrat"),
                                dDebut.toLocalDate()
                            ));
                        }
                    }
                }

                String sqlMouvements = "SELECT type_mouvement, date_mouvement FROM mouvement_solde_conge WHERE id_employe = ?";
                String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                         "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                         "VALUES (?, ?, ?, CURRENT_DATE, ?)";

                try (PreparedStatement psMvt = c.prepareStatement(sqlMouvements);
                     PreparedStatement psIn = c.prepareStatement(insertMouvement)) {

                    for (ContratActifInfo ct : contrats) {
                        // 2. Éligibilité temporelle calculée en mémoire Java
                        boolean eligible1Mois = !ct.dateDebut.isAfter(today.minusMonths(1));
                        boolean jourAtteint = today.getDayOfMonth() >= ct.dateDebut.getDayOfMonth() ||
                                             (isDernierJourMois && ct.dateDebut.getDayOfMonth() > today.getDayOfMonth());

                        if (!eligible1Mois || !jourAtteint) {
                            continue;
                        }

                        // 3. Récupération des mouvements existants
                        psMvt.setInt(1, ct.idEmploye);
                        List<MouvementInfo> mvts = new ArrayList<>();
                        try (ResultSet rsMvt = psMvt.executeQuery()) {
                            while (rsMvt.next()) {
                                Date dMvt = rsMvt.getDate("date_mouvement");
                                if (dMvt != null) {
                                    mvts.add(new MouvementInfo(rsMvt.getString("type_mouvement"), dMvt.toLocalDate()));
                                }
                            }
                        }

                        boolean dejaAcquisCeMois = mvts.stream().anyMatch(m ->
                            "ACQUISITION".equals(m.typeMouvement) &&
                            m.dateMouvement.getYear() == today.getYear() &&
                            m.dateMouvement.getMonth() == today.getMonth()
                        );

                        // 4. Crédit de l'acquisition mensuelle standard (2.5 jours)
                        if (!dejaAcquisCeMois) {
                            psIn.setInt(1, ct.idEmploye);
                            psIn.setString(2, "ACQUISITION");
                            psIn.setDouble(3, CongeCalculateurEngine.TAUX_ACQUISITION_MENSUEL.doubleValue());
                            psIn.setString(4, "Acquisition mensuelle légale (2.5j)");
                            psIn.executeUpdate();

                            log.info("[AcquisitionCongeScheduler] +2.5j acquis pour employé ID {}", ct.idEmploye);
                        }

                        // 5. Calcul de bonification d'ancienneté (+1j par 5 ans) si anniversaire d'embauche échu dans l'année
                        int ancienneteAns = Period.between(ct.dateDebut, today).getYears();
                        int joursBonus = congeCalculateurEngine.calculerBonificationAnciennete(ancienneteAns);
                        boolean anniversaireEchuOuActuel = today.getMonthValue() >= ct.dateDebut.getMonthValue();

                        if (joursBonus > 0 && anniversaireEchuOuActuel) {
                            boolean dejaBonifieCetteAnnee = mvts.stream().anyMatch(m ->
                                "BONIFICATION".equals(m.typeMouvement) &&
                                m.dateMouvement.getYear() == today.getYear()
                            );

                            if (!dejaBonifieCetteAnnee) {
                                psIn.setInt(1, ct.idEmploye);
                                psIn.setString(2, "BONIFICATION");
                                psIn.setDouble(3, (double) joursBonus);
                                psIn.setString(4, "Bonification d'ancienneté (" + ancienneteAns + " ans d'ancienneté : +" + joursBonus + "j)");
                                psIn.executeUpdate();

                                log.info("[AcquisitionCongeScheduler] +{}j de bonification d'ancienneté pour employé ID {}", joursBonus, ct.idEmploye);
                            }
                        }
                    }
                }

                c.commit();
                log.info("[AcquisitionCongeScheduler] Traitement d'acquisition terminé avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[AcquisitionCongeScheduler] Erreur lors de l'acquisition des congés : {}", e.getMessage(), e);
            }
        } catch (Exception e) {
            log.error("[AcquisitionCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
        }
    }
}
