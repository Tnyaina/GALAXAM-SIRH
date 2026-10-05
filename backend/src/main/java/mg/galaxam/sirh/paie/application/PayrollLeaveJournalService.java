package mg.galaxam.sirh.paie.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.application.CongeTauxApplicableService;
import mg.galaxam.sirh.paie.persistence.BulletinPaie;
import mg.galaxam.sirh.paie.persistence.BulletinPaieLigne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Pont transactionnel Paie → Congés pour l'acquisition et la valorisation mensuelles. */
@Service
public class PayrollLeaveJournalService {
    private static final Logger log = LoggerFactory.getLogger(PayrollLeaveJournalService.class);
    private final CongeService congeService;
    private final CongeTauxApplicableService congeTauxApplicableService;

    public PayrollLeaveJournalService(CongeService congeService,
            CongeTauxApplicableService congeTauxApplicableService) {
        this.congeService = congeService;
        this.congeTauxApplicableService = congeTauxApplicableService;
    }

    // Congés : journal mensuel acquis à la date de fin de campagne.
    /**
     * Alimente le grand-livre de valorisation des droits à congés (OHATRA KAJY DROITS CONGES)
     * lors de la clôture de la paie.
     * Enregistre les valorisations mensuelles bornées à la période de référence active (max 12 mois).
     */
    public void mettreAJourJournalConge(Integer idPeriodePaie, Connection c) throws Exception {
        String sqlPeriode = "SELECT date_debut, date_fin, EXTRACT(YEAR FROM date_fin)::INT AS annee, EXTRACT(MONTH FROM date_fin)::INT AS mois " +
                "FROM periode_paie WHERE id_periode_paie = ?";
        LocalDate dateDebutPaie = null;
        LocalDate dateFinPaie = null;
        int annee = 0;
        int mois = 0;
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriodePaie);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    Date dd = rsP.getDate("date_debut");
                    if (dd != null) dateDebutPaie = dd.toLocalDate();
                    Date df = rsP.getDate("date_fin");
                    if (df != null) dateFinPaie = df.toLocalDate();
                    annee = rsP.getInt("annee");
                    mois = rsP.getInt("mois");
                }
            }
        }
        if (dateDebutPaie == null || dateFinPaie == null) {
            throw new IllegalStateException("Période de paie absente ou sans dates complètes : " + idPeriodePaie);
        }

        // 1. Résolution stricte du paramètre DUREE_REFERENCE_TCONGE_MOIS (aucun fallback silencieux)
        Map<String, String> params = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.loadActiveMapAtDate(
                Date.valueOf(dateFinPaie), c);
        int dureeReferenceMax = mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.requirePositiveInt(
                params, "DUREE_REFERENCE_TCONGE_MOIS");
        BigDecimal diviseurConge = BigDecimal.valueOf(
                mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.requirePositiveInt(
                        params, "DIVISEUR_JOURS_CALENDAIRE_CONGE"));

        List<BulletinPaie> bulletins = BulletinPaie.listerParPeriode(idPeriodePaie, c);
        for (BulletinPaie bp : bulletins) {
            // Ignorer les bulletins STC : le Solde de Tout Compte valorise et liquide les congés
            // dans le reçu de départ, il ne doit pas générer d'acquisition indue ni écraser le journal
            if ("STC".equalsIgnoreCase(bp.getTypeBulletin())) {
                continue;
            }

            // Uniquement les salariés soumis aux congés payés légaux
            if ("CONSULTANT".equalsIgnoreCase(bp.getRegimeIntervenant()) || "ISI_5".equalsIgnoreCase(bp.getRegimeIntervenant())) {
                continue;
            }

            int idEmp = bp.getIdEmploye();
            // Un cutover est un arrêté des droits déjà repris par la DAF.
            // La campagne dont la date d'acquisition est antérieure ou égale à cet arrêté
            // ne doit pas recréer une acquisition présente dans les cumuls d'ouverture.
            if (congeTauxApplicableService.acquisitionDejaReprise(idEmp, dateFinPaie, c)) {
                log.info("Acquisition congés ignorée pour l'employé {} : clôture {} déjà comprise dans le cutover",
                        idEmp, dateFinPaie);
                continue;
            }
            BigDecimal base = bp.getSalaireBase() != null ? bp.getSalaireBase() : BigDecimal.ZERO;
            BigDecimal jAbs = bp.getJoursAbsence() != null ? bp.getJoursAbsence() : BigDecimal.ZERO;

            // Dette KAJY résolue : jAbs et jCge sont déjà mutuellement exclusifs dans le modèle Pointage.
            // Les congés pris ne doivent jamais effacer les absences injustifiées.
            BigDecimal absInjustifiees = jAbs.max(BigDecimal.ZERO);
            BigDecimal formule = congeService.calculerFormuleAcquisition(
                    idEmp, dateDebutPaie, dateFinPaie, absInjustifiees, bp.getJoursTravailles(), c);

            // Garantir l'existence d'une période de référence congé avant résolution stricte.
            // Idempotent : ne crée qu'une seule fois ; erreur bloquante si configuration ambiguë ou successeur manquant.
            congeTauxApplicableService.ensureLeaveReferencePeriod(idEmp, dateFinPaie, c);

            var refActive = congeTauxApplicableService.resoudrePeriodeReferenceStrict(idEmp, dateFinPaie, c);

            // Acquisition canonique unifiée (RG-CONGE, propriété du Domaine Congés)
            BigDecimal droitMois = congeService.enregistrerAcquisitionMensuelle(
                    idEmp, annee, mois, dateDebutPaie, dateFinPaie, absInjustifiees,
                    bp.getJoursTravailles(), bp.getCalculePar(), c);

            // Récupérer les lignes du bulletin pour net_base et hs
            List<BulletinPaieLigne> lignes = BulletinPaieLigne.listerParBulletin(bp.getIdBulletin(), c);
            BigDecimal netBase = base;
            BigDecimal mntHs = BigDecimal.ZERO;
            for (BulletinPaieLigne l : lignes) {
                String codeRub = l.getCodeRubrique() != null ? l.getCodeRubrique().toUpperCase() : "";
                if ("RETENUE_ABSENCE".equals(codeRub) || "RETENUE_CONGE".equals(codeRub) || "DEDUCTION_CONGE_BASE".equals(codeRub)) {
                    if (l.getMontantSalarial() != null) netBase = netBase.subtract(l.getMontantSalarial());
                } else if ("INDEMNITE_CONGE".equals(codeRub) || "INDEMNITE_CONGE_PAYE".equals(codeRub)) {
                    if (l.getMontantSalarial() != null) netBase = netBase.add(l.getMontantSalarial());
                } else if ("HEURES_SUP".equalsIgnoreCase(l.getCategorie()) || "HEURES_SUPPLEMENTAIRES".equalsIgnoreCase(l.getCategorie()) || codeRub.startsWith("HS_") || "HS".equals(codeRub)) {
                    if (l.getMontantSalarial() != null) mntHs = mntHs.add(l.getMontantSalarial());
                }
            }

            BigDecimal baseTotale = netBase.add(mntHs).max(BigDecimal.ZERO);
            BigDecimal valMois = baseTotale.multiply(droitMois).divide(diviseurConge, 2, RoundingMode.HALF_UP);

            // 2. Résolution de la période de référence bornée KAJY pour l'employé
            // Calcul du numéro de mois dans la référence à partir de la position calendaire
            int numeroMoisRef = dateFinPaie.getMonthValue();
            if (numeroMoisRef < 1 || numeroMoisRef > dureeReferenceMax) {
                throw new IllegalStateException("TCONGE_REFERENCE_CAPACITY_EXCEEDED: la date de paie " + dateFinPaie +
                        " correspond au mois " + numeroMoisRef + " de la référence [" + refActive.getDateDebut() + ", " +
                        refActive.getDateFin() + "], excédant la durée maximale autorisée de " + dureeReferenceMax + " mois.");
            }

            // 3. Cumuls STRICTEMENT BORNÉS à la période de référence active (suppression du cumul infini tous temps)
            BigDecimal cumulJoursPrec = BigDecimal.ZERO;
            BigDecimal cumulValPrec = BigDecimal.ZERO;
            String sqlPrec = "SELECT COALESCE(SUM(jours_droit_acquis), 0) AS cumul_droits, " +
                    "COALESCE(SUM(valorisation_mensuelle), 0) AS cumul_valo " +
                    "FROM conge_journal_droit " +
                    "WHERE id_periode_reference = ? AND id_employe = ? " +
                    "  AND (annee < ? OR (annee = ? AND mois < ?))";
            try (PreparedStatement psPrec = c.prepareStatement(sqlPrec)) {
                psPrec.setInt(1, refActive.getIdPeriodeReference());
                psPrec.setInt(2, idEmp);
                psPrec.setInt(3, annee);
                psPrec.setInt(4, annee);
                psPrec.setInt(5, mois);
                try (ResultSet rsPrec = psPrec.executeQuery()) {
                    if (rsPrec.next()) {
                        cumulJoursPrec = rsPrec.getBigDecimal("cumul_droits");
                        cumulValPrec = rsPrec.getBigDecimal("cumul_valo");
                    }
                }
            }

            // Prise en compte des cumuls d'ouverture (cutover AGGREGATED_OPENING)
            if (refActive.getCumulDroitsOuverture() != null) {
                cumulJoursPrec = (cumulJoursPrec != null ? cumulJoursPrec : BigDecimal.ZERO).add(refActive.getCumulDroitsOuverture());
            }
            if (refActive.getCumulValorisationOuverture() != null) {
                cumulValPrec = (cumulValPrec != null ? cumulValPrec : BigDecimal.ZERO).add(refActive.getCumulValorisationOuverture());
            }

            BigDecimal cumulJours = (cumulJoursPrec != null ? cumulJoursPrec : BigDecimal.ZERO).add(droitMois);
            BigDecimal cumulVal = (cumulValPrec != null ? cumulValPrec : BigDecimal.ZERO).add(valMois);
            BigDecimal tauxJournalier = cumulJours.compareTo(BigDecimal.ZERO) > 0
                    ? cumulVal.divide(cumulJours, 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO; // Aucun droit valorisé : pas de TCONGE moyen inventé.

            // Une rectification DAF historisée ne doit jamais être écrasée par une nouvelle clôture.
            try (PreparedStatement guard = c.prepareStatement("SELECT 1 FROM conge_journal_droit j "
                    + "JOIN conge_valorisation_mensuelle_correction x ON x.id_journal=j.id_journal "
                    + "WHERE j.id_employe=? AND j.annee=? AND j.mois=? LIMIT 1")) {
                guard.setInt(1, idEmp); guard.setInt(2, annee); guard.setInt(3, mois);
                try (ResultSet rs = guard.executeQuery()) {
                    if (rs.next()) throw new IllegalStateException(
                            "CONGE_VALORISATION_CORRIGEE: régularisation DAF requise avant reclôture de la paie");
                }
            }
            String sqlUpsert = "INSERT INTO conge_journal_droit (" +
                    "id_employe, id_periode_paie, annee, mois, salaire_base, jours_travailles_formule, " +
                    "jours_droit_acquis, base_valorisation, valorisation_mensuelle, cumul_jours_droit, " +
                    "cumul_valorisation, taux_journalier_conge, id_periode_reference, numero_mois_reference" +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (id_employe, annee, mois) DO UPDATE SET " +
                    "id_periode_paie = EXCLUDED.id_periode_paie, " +
                    "salaire_base = EXCLUDED.salaire_base, " +
                    "jours_travailles_formule = EXCLUDED.jours_travailles_formule, " +
                    "jours_droit_acquis = EXCLUDED.jours_droit_acquis, " +
                    "base_valorisation = EXCLUDED.base_valorisation, " +
                    "valorisation_mensuelle = EXCLUDED.valorisation_mensuelle, " +
                    "cumul_jours_droit = EXCLUDED.cumul_jours_droit, " +
                    "cumul_valorisation = EXCLUDED.cumul_valorisation, " +
                    "taux_journalier_conge = EXCLUDED.taux_journalier_conge, " +
                    "id_periode_reference = EXCLUDED.id_periode_reference, " +
                    "numero_mois_reference = EXCLUDED.numero_mois_reference";

            try (PreparedStatement psUp = c.prepareStatement(sqlUpsert)) {
                psUp.setInt(1, idEmp);
                psUp.setInt(2, idPeriodePaie);
                psUp.setInt(3, annee);
                psUp.setInt(4, mois);
                psUp.setBigDecimal(5, base);
                psUp.setBigDecimal(6, formule);
                psUp.setBigDecimal(7, droitMois);
                psUp.setBigDecimal(8, baseTotale);
                psUp.setBigDecimal(9, valMois);
                psUp.setBigDecimal(10, cumulJours);
                psUp.setBigDecimal(11, cumulVal);
                psUp.setBigDecimal(12, tauxJournalier);
                psUp.setInt(13, refActive.getIdPeriodeReference());
                psUp.setInt(14, numeroMoisRef);
                psUp.executeUpdate();
            }
            try (PreparedStatement snapshot = c.prepareStatement(
                    "UPDATE mouvement_solde_conge SET valeur_source = ? "
                    + "WHERE id_employe = ? AND type_mouvement = 'ACQUISITION' "
                    + "AND date_mouvement BETWEEN ? AND ? AND valeur_source IS NULL")) {
                snapshot.setBigDecimal(1, valMois);
                snapshot.setInt(2, idEmp);
                snapshot.setDate(3, Date.valueOf(dateDebutPaie));
                snapshot.setDate(4, Date.valueOf(dateFinPaie));
                snapshot.executeUpdate();
            }

            // 4. Clôture de la référence à l'atteinte du terme de 12 mois
            // Fige le TAUX_CALCULE dans conge_periode_reference sans auto-activation à date arbitraire
            if (numeroMoisRef == dureeReferenceMax) {
                if ("MINIMAL_OPENING".equals(refActive.getModeReprise()) && refActive.getCumulDroitsOuverture() == null) {
                    log.warn("[PayrollService] Terme de la période {} atteint pour l'employé {} en MINIMAL_OPENING. Arbitrage RH explicite requis (CYCLE_ACCUMULATORS_UNKNOWN).",
                            refActive.getIdPeriodeReference(), idEmp);
                } else {
                    congeTauxApplicableService.cloturerPeriodeReference(
                            refActive.getIdPeriodeReference(),
                            cumulJours,
                            cumulVal,
                            tauxJournalier,
                            c
                    );
                }
            }
        }
    }

}
