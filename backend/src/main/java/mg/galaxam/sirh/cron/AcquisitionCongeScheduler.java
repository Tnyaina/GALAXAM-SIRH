package mg.galaxam.sirh.cron;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.paieconfig.persistence.PaieConfigLegale;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private mg.galaxam.sirh.conge.application.CongeService congeService;

    @Autowired
    private SchedulerExecutionRegistry executionRegistry;

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
        double nbJours;

        public MouvementInfo(String typeMouvement, LocalDate dateMouvement, double nbJours) {
            this.typeMouvement = typeMouvement;
            this.dateMouvement = dateMouvement;
            this.nbJours = nbJours;
        }
    }

    @Scheduled(cron = "0 0 1 * * ?", zone = "Indian/Antananarivo")
    public void acquerirCongesMensuels() {
        executionRegistry.runMonthly("ACQUISITION_CONGES", this::acquerirCongesMensuelsInterne);
    }

    private void acquerirCongesMensuelsInterne() throws Exception {
        log.info("[AcquisitionCongeScheduler] Vérification de l'acquisition mensuelle des congés (Moteur Java)...");
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                LocalDate today = LocalDate.now();
                boolean isDernierJourMois = today.plusDays(1).getMonth() != today.getMonth();

                // 1. Chargement de la configuration légale active (taux d'acquisition mensuel)
                PaieConfigLegale configLegale = null;
                try {
                    configLegale = PaieConfigLegale.findActiveAt(c, today);
                } catch (Exception e) {
                    log.warn("[AcquisitionCongeScheduler] Impossible de charger PaieConfigLegale : {}", e.getMessage());
                }

                double tauxAcquisitionMensuel = (configLegale != null && configLegale.getAcquisitionCongeMensuelJours() != null)
                        ? configLegale.getAcquisitionCongeMensuelJours().doubleValue()
                        : CongeCalculateurEngine.TAUX_ACQUISITION_MENSUEL.doubleValue();

                // 2. Chargement des paramètres d'organisation interne (plafond de cumul et bonification)
                Map<String, ParametreGlobal.ParametreEtat> etatMap = ParametreGlobal.loadEtatMap(c);
                double plafondCumulSolde = ParametreGlobal.getDouble(etatMap, "PLAFOND_CUMUL_SOLDE_CONGE_JOURS", 90.0, 90.0);
                boolean bonificationActive = ParametreGlobal.isActif(etatMap, "SEUIL_ANCIENNETE_BONIFICATION_ANS", true);
                int seuilAnciennete = bonificationActive ? ParametreGlobal.getInt(etatMap, "SEUIL_ANCIENNETE_BONIFICATION_ANS", 5, 999) : 999;
                int palierAnciennete = ParametreGlobal.getInt(etatMap, "PALIER_ANNEES_BONIFICATION", 5, 5);
                int joursParPalier = bonificationActive ? ParametreGlobal.getInt(etatMap, "JOURS_BONIFICATION_PAR_PALIER", 1, 0) : 0;

                // 3. Extraction brute des contrats actifs
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

                String sqlMouvements = "SELECT type_mouvement, date_mouvement, nb_jours FROM mouvement_solde_conge WHERE id_employe = ?";
                String insertMouvement = "INSERT INTO mouvement_solde_conge " +
                                         "(id_employe, type_mouvement, nb_jours, date_mouvement, commentaire) " +
                                         "VALUES (?, ?, ?, CURRENT_DATE, ?)";

                try (PreparedStatement psMvt = c.prepareStatement(sqlMouvements);
                     PreparedStatement psIn = c.prepareStatement(insertMouvement)) {

                    for (ContratActifInfo ct : contrats) {
                        // Éligibilité temporelle calculée en mémoire Java
                        boolean eligible1Mois = !ct.dateDebut.isAfter(today.minusMonths(1));
                        boolean jourAtteint = today.getDayOfMonth() >= ct.dateDebut.getDayOfMonth() ||
                                             (isDernierJourMois && ct.dateDebut.getDayOfMonth() > today.getDayOfMonth());

                        if (!eligible1Mois || !jourAtteint) {
                            continue;
                        }

                        // Récupération des mouvements existants
                        psMvt.setInt(1, ct.idEmploye);
                        List<MouvementInfo> mvts = new ArrayList<>();
                        try (ResultSet rsMvt = psMvt.executeQuery()) {
                            while (rsMvt.next()) {
                                Date dMvt = rsMvt.getDate("date_mouvement");
                                if (dMvt != null) {
                                    mvts.add(new MouvementInfo(
                                        rsMvt.getString("type_mouvement"),
                                        dMvt.toLocalDate(),
                                        rsMvt.getDouble("nb_jours")
                                    ));
                                }
                            }
                        }

                        boolean dejaAcquisCeMois = mvts.stream().anyMatch(m ->
                            "ACQUISITION".equals(m.typeMouvement) &&
                            m.dateMouvement.getYear() == today.getYear() &&
                            m.dateMouvement.getMonth() == today.getMonth()
                        );

                        double soldeActuel = mvts.stream().mapToDouble(m -> m.nbJours).sum();

                        // 4. Crédit canonique de l'acquisition mensuelle légale (avec respect du plafond et idempotence)
                        if (!dejaAcquisCeMois) {
                            java.math.BigDecimal acquis = congeService.enregistrerAcquisitionMensuelle(
                                    ct.idEmploye, today.getYear(), today.getMonthValue(), java.math.BigDecimal.ZERO, null, c);
                            soldeActuel += acquis.doubleValue();
                            log.info("[AcquisitionCongeScheduler] +{}j acquis canoniquement pour employé ID {}", acquis, ct.idEmploye);
                        }

                        // 5. Calcul de bonification d'ancienneté paramétrable si anniversaire d'embauche échu dans l'année
                        int ancienneteAns = Period.between(ct.dateDebut, today).getYears();
                        int joursBonus = congeCalculateurEngine.calculerBonificationAnciennete(ancienneteAns, seuilAnciennete, palierAnciennete, joursParPalier);
                        boolean anniversaireEchuOuActuel = today.getMonthValue() >= ct.dateDebut.getMonthValue();

                        if (joursBonus > 0 && anniversaireEchuOuActuel) {
                            boolean dejaBonifieCetteAnnee = mvts.stream().anyMatch(m ->
                                "BONIFICATION".equals(m.typeMouvement) &&
                                m.dateMouvement.getYear() == today.getYear()
                            );

                            if (!dejaBonifieCetteAnnee) {
                                double bonusACrediter = joursBonus;
                                if (soldeActuel >= plafondCumulSolde) {
                                    bonusACrediter = 0.0;
                                    log.info("[AcquisitionCongeScheduler] Employé ID {} a atteint le plafond ({}j). Bonification d'ancienneté non créditée.",
                                            ct.idEmploye, plafondCumulSolde);
                                } else if (soldeActuel + bonusACrediter > plafondCumulSolde) {
                                    bonusACrediter = Math.round((plafondCumulSolde - soldeActuel) * 100.0) / 100.0;
                                }

                                if (bonusACrediter > 0) {
                                    psIn.setInt(1, ct.idEmploye);
                                    psIn.setString(2, "BONIFICATION");
                                    psIn.setDouble(3, bonusACrediter);
                                    String comm = "Bonification d'ancienneté (" + ancienneteAns + " ans : +" + bonusACrediter + "j)"
                                            + (bonusACrediter < joursBonus ? " [Plafonné à " + plafondCumulSolde + "j]" : "");
                                    psIn.setString(4, comm);
                                    psIn.executeUpdate();
                                    soldeActuel += bonusACrediter;

                                    log.info("[AcquisitionCongeScheduler] +{}j de bonification d'ancienneté pour employé ID {}", bonusACrediter, ct.idEmploye);
                                }
                            }
                        }
                    }
                }

                c.commit();
                log.info("[AcquisitionCongeScheduler] Traitement d'acquisition terminé avec succès.");
            } catch (Exception e) {
                c.rollback();
                log.error("[AcquisitionCongeScheduler] Erreur lors de l'acquisition des congés : {}", e.getMessage(), e);
                throw e;
            }
        } catch (Exception e) {
            log.error("[AcquisitionCongeScheduler] Erreur connexion BDD : {}", e.getMessage(), e);
            throw e;
        }
    }
}
