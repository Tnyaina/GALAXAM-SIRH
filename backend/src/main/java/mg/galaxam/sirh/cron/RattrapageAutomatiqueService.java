package mg.galaxam.sirh.cron;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Service centralisé assurant le rattrapage quotidien autonome des traitements de fond :
 * 1. Basculement des mouvements RH échus (contrats, affectations, ruptures).
 * 2. Génération des alertes et notifications RH.
 * 3. Acquisitions mensuelles de congés.
 * 4. Purges triennales de congés échus.
 * 5. Nettoyage des sessions et refresh tokens expirés.
 *
 * Fonctionne de manière 100% autonome :
 * - Au démarrage initial de l'application (ApplicationReadyEvent).
 * - Par battement de cœur régulier toutes les 15 minutes (fixedDelay) après sortie de veille du serveur.
 * - En filet de sécurité paresseux lors de la première requête HTTP du jour.
 */
@Service
public class RattrapageAutomatiqueService {

    private static final Logger log = LoggerFactory.getLogger(RattrapageAutomatiqueService.class);

    private final AtomicReference<LocalDate> dernierRattrapageJournalier = new AtomicReference<>(null);
    private final AtomicBoolean enCoursExecution = new AtomicBoolean(false);

    @Autowired
    @Lazy
    private MouvementRhScheduler mouvementRhScheduler;

    @Autowired
    @Lazy
    private NotificationRhScheduler notificationRhScheduler;

    @Autowired
    @Lazy
    private AcquisitionCongeScheduler acquisitionCongeScheduler;

    @Autowired
    @Lazy
    private PurgeCongeScheduler purgeCongeScheduler;

    @Autowired
    @Lazy
    private SessionSecurityScheduler sessionSecurityScheduler;

    @Autowired
    @Lazy
    private AuditLogPurgeScheduler auditLogPurgeScheduler;

    /**
     * Exécution automatique au démarrage du serveur.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        log.info("[RATTRAPAGE] Exécution du rattrapage initial au démarrage du serveur...");
        verifierEtExecuterRattrapage(false);
    }

    /**
     * Battement de cœur régulier (toutes les 15 minutes), côté SERVEUR uniquement.
     * Indépendant de React/SPA : aucune requête HTTP client n'est nécessaire pour déclencher le rattrapage.
     * Si le serveur ne redémarre pas mais que minuit est passé (ex: serveur en veille, weekend sans trafic),
     * cette tâche détecte automatiquement le changement de date et lance le cycle de rattrapage du jour.
     */
    @Scheduled(fixedDelay = 900000, initialDelay = 30000)
    public void battementCoeurPeriodique() {
        LocalDate aujourdhui = LocalDate.now();
        if (!aujourdhui.equals(dernierRattrapageJournalier.get())) {
            log.info("[RATTRAPAGE] Battement de cœur périodique : rattrapage requis pour la date du {}", aujourdhui);
            verifierEtExecuterRattrapage(false);
        }
    }

    /**
     * Déclencheur asynchrone sécurisé pour les requêtes HTTP (Filtre JWT / Refresh Token).
     * N'effectue aucun calcul bloquant si le rattrapage du jour a déjà eu lieu.
     */
    public void verifierEtExecuterRattrapageAsync() {
        LocalDate aujourdhui = LocalDate.now();
        if (!aujourdhui.equals(dernierRattrapageJournalier.get())) {
            CompletableFuture.runAsync(() -> verifierEtExecuterRattrapage(false));
        }
    }

    /**
     * Exécute l'ensemble des rattrapages quotidiens de manière séquentielle et idempotente.
     * @param forcer true pour forcer l'exécution même si elle a déjà eu lieu aujourd'hui.
     */
    public void verifierEtExecuterRattrapage(boolean forcer) {
        LocalDate aujourdhui = LocalDate.now();

        if (!forcer && aujourdhui.equals(dernierRattrapageJournalier.get())) {
            return;
        }

        if (!enCoursExecution.compareAndSet(false, true)) {
            log.info("[RATTRAPAGE] Un cycle de rattrapage est déjà en cours d'exécution. Ignoré.");
            return;
        }

        try {
            log.info("[RATTRAPAGE] Démarrage du cycle de rattrapage quotidien autonome pour le {}...", aujourdhui);

            // 1. Mouvements RH échus (contrats planifiés -> actifs, ruptures, etc.)
            try {
                mouvementRhScheduler.effectuerBasculementMouvementsPlanifies();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur basculement mouvements RH : {}", e.getMessage());
            }

            // 2. Alertes et notifications RH (échéances d'essai, CDD, etc.)
            try {
                notificationRhScheduler.genererNotificationsRh();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur génération alertes RH : {}", e.getMessage());
            }

            // 3. Acquisition mensuelle de congés
            try {
                acquisitionCongeScheduler.acquerirCongesMensuels();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur acquisition mensuelle congés : {}", e.getMessage());
            }

            // 4. Purge des congés échus
            try {
                purgeCongeScheduler.purgerCongesExpires();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur purge congés expirés : {}", e.getMessage());
            }

            // 5. Nettoyage de sécurité (sessions + tokens expirés)
            try {
                sessionSecurityScheduler.nettoyerSecurite();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur nettoyage sécurité : {}", e.getMessage());
            }

            // 6. Purge des logs d'audit (mensuelle, sans risque de doublon : idempotente par seuil date)
            try {
                auditLogPurgeScheduler.purgerAnciensLogsAudit();
            } catch (Exception e) {
                log.error("[RATTRAPAGE] Erreur purge logs audit : {}", e.getMessage());
            }

            dernierRattrapageJournalier.set(aujourdhui);
            log.info("[RATTRAPAGE] Cycle de rattrapage quotidien terminé avec succès pour le {}.", aujourdhui);

        } finally {
            enCoursExecution.set(false);
        }
    }

    public LocalDate getDernierRattrapageJournalier() {
        return dernierRattrapageJournalier.get();
    }
}
