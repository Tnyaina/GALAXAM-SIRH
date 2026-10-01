package mg.galaxam.sirh.pointage.application;

import mg.galaxam.sirh.pointage.api.dto.PointageAnomalieDTO;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.pointage.domain.engine.CalendrierOuvrableEngine;
import mg.galaxam.sirh.pointage.domain.calculation.AttendanceEngine;
import mg.galaxam.sirh.paie.domain.engine.MoneyRoundingPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class PointageAnomalieService {

    private static final Logger log = LoggerFactory.getLogger(PointageAnomalieService.class);

    private static LocalTime local(Time value) {
        return value == null ? null : value.toLocalTime();
    }

    private static double hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).doubleValue();
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageService pointageSaisieService;

    @Autowired
    private CalendrierOuvrableEngine calendrierOuvrableEngine;

    @Autowired
    private AttendanceEngine dailyAttendanceCalculator;

    @Autowired
    private CongeService congeService;

    /**
     * Calcule le code de semaine de paie (S1, S2, S3, S4, S5) relatif au début de la période
     */
    public static String computeSemaineCode(LocalDate date, LocalDate periodeDebut) {
        if (date == null || periodeDebut == null) return "S1";
        LocalDate firstMonday = periodeDebut.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate dateMonday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long weeksBetween = ChronoUnit.WEEKS.between(firstMonday, dateMonday);
        int weekNum = (int) (weeksBetween + 1);
        return "S" + Math.max(1, weekNum);
    }

    /**
     * Recherche les anomalies pour un filtre donné avec enrichissement complet :
     * sévérité, statut, semaines S1-S5, KPIs globaux, solde de congés.
     */
    public Map<String, Object> getAnomalies(
            Integer idSite,
            Integer idPeriode,
            String datePointage,
            String search
    ) throws SQLException {
        Map<String, Object> response = new HashMap<>();
        List<PointageAnomalieDTO> anomalies = new ArrayList<>();
        int countIncomplets = 0;
        int countRetards = 0;
        int countAbsences = 0;
        int countCritiques = 0;
        int countHautes = 0;
        int countMoyennes = 0;
        int countFaibles = 0;
        int countBloquantes = 0;

        try (Connection c = dataSource.getConnection()) {
            Integer effectivePeriodeId = idPeriode;
            if (effectivePeriodeId == null || effectivePeriodeId <= 0) {
                if (datePointage == null || datePointage.isBlank()) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                            mg.galaxam.sirh.common.exception.SirhErrorCode.POINTAGE_PERIOD_CONTEXT_REQUIRED);
                }
                String sqlFindPer = "SELECT id_periode_paie FROM periode_paie "
                        + "WHERE ? BETWEEN date_debut AND date_fin "
                        + "ORDER BY date_debut DESC, id_periode_paie DESC LIMIT 2";
                try (PreparedStatement psF = c.prepareStatement(sqlFindPer)) {
                    psF.setDate(1, Date.valueOf(datePointage));
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (!rsF.next()) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                                "Aucune période ne couvre la date de pointage demandée.");
                        effectivePeriodeId = rsF.getInt(1);
                        if (rsF.next()) throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                                mg.galaxam.sirh.common.exception.SirhErrorCode.POINTAGE_PERIOD_CONTEXT_AMBIGUOUS);
                    }
                }
            }

            // Informations de la période de paie
            LocalDate periodeDebut = null;
            LocalDate periodeFin = null;
            String periodeCode = null;
            String periodeLibelle = null;
            String statutPeriodeCode = "OUVERTE";
            String statutPeriodeLibelle = "Ouverte";

            if (effectivePeriodeId != null && effectivePeriodeId > 0) {
                String sqlPer = "SELECT p.id_periode_paie, p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code, s.libelle AS statut_libelle " +
                                "FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ?";
                try (PreparedStatement psP = c.prepareStatement(sqlPer)) {
                    psP.setInt(1, effectivePeriodeId);
                    try (ResultSet rsP = psP.executeQuery()) {
                        if (rsP.next()) {
                            Date dDeb = rsP.getDate("date_debut");
                            Date dFin = rsP.getDate("date_fin");
                            if (dDeb != null) periodeDebut = dDeb.toLocalDate();
                            if (dFin != null) periodeFin = dFin.toLocalDate();
                            periodeCode = rsP.getString("code");
                            periodeLibelle = rsP.getString("libelle");
                            statutPeriodeCode = rsP.getString("statut_code");
                            statutPeriodeLibelle = rsP.getString("statut_libelle");
                        }
                    }
                }
            }

            if (periodeDebut == null || periodeFin == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND,
                        "La période de paie demandée est introuvable ou incomplète.");
            }

            StringBuilder sql = new StringBuilder(
                "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "p.id_site, s.libelle AS site_nom, s.horaire_continu AS site_horaire_continu, " +
                "s.heure_debut_matin AS heure_debut_matin_site, s.heure_fin_matin AS heure_fin_matin_site, " +
                "s.heure_debut_apres_midi AS heure_debut_am_site, s.heure_fin_apres_midi AS heure_fin_am_site, p.date_pointage, " +
                "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
                "p.total_heures, p.minutes_retard, st.code AS statut_code, st.libelle AS statut_libelle, " +
                "p.motif_ajustement, p.date_ajustement, p.id_demande_conge, " +
                "ltr_valide.id_demande_conge AS demande_validee_id, p.montant_total_indemnites, " +
                "pij_repas.accorde AS repas_accorde, pij_dep.accorde AS dep_accorde, pij_dep.quantite AS dep_quantite, pij_dep.fraction AS dep_fraction, " +
                "ltr_conge.libelle_conge AS demande_en_attente_libelle, " +
                "p.id_periode_paie, per.code AS periode_code, per.libelle AS periode_libelle, per_st.code AS statut_periode_code, " +
                "COALESCE(p.version, 1) AS version, " +
                "COALESCE(vsc.solde_actuel, 0.0) AS solde_restant, " +
                "COALESCE(vsc.total_acquis, 0.0) AS solde_acquis, " +
                "COALESCE(vsc.total_pris, 0.0) AS solde_pris " +
                "FROM pointage_jour p " +
                "JOIN employe e ON p.id_employe = e.id_employe " +
                "LEFT JOIN ref_site s ON p.id_site = s.id_site " +
                "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                "LEFT JOIN periode_paie per ON p.id_periode_paie = per.id_periode_paie " +
                "LEFT JOIN ref_statut_periode_paie per_st ON per.id_statut_periode = per_st.id_statut_periode " +
                "LEFT JOIN pointage_indemnite_jour pij_repas ON p.id_pointage = pij_repas.id_pointage " +
                "    AND pij_repas.id_indemnite = (SELECT id_indemnite FROM ref_indemnite WHERE code = 'REPAS' LIMIT 1) " +
                "LEFT JOIN pointage_indemnite_jour pij_dep ON p.id_pointage = pij_dep.id_pointage " +
                "    AND pij_dep.id_indemnite IN (SELECT id_indemnite FROM ref_indemnite WHERE code IN ('DEPLACEMENT', 'TRANSPORT') LIMIT 1) " +
                "LEFT JOIN LATERAL (" +
                "   SELECT tc.libelle AS libelle_conge FROM demande_conge dc " +
                "   JOIN ref_type_conge tc ON dc.id_type_conge = tc.id_type_conge " +
                "   JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "   WHERE dc.id_employe = p.id_employe AND p.date_pointage BETWEEN dc.date_debut AND dc.date_fin " +
                "   AND sc.code = 'EN_ATTENTE' LIMIT 1" +
                ") ltr_conge ON TRUE " +
                "LEFT JOIN LATERAL (" +
                "   SELECT dc.id_demande_conge FROM demande_conge dc " +
                "   JOIN ref_statut_demande_conge sc ON dc.id_statut_demande_conge = sc.id_statut_demande_conge " +
                "   WHERE dc.id_employe = p.id_employe AND p.date_pointage BETWEEN dc.date_debut AND dc.date_fin " +
                "   AND sc.code = 'VALIDE' LIMIT 1" +
                ") ltr_valide ON TRUE " +
                "LEFT JOIN v_solde_conge_employe vsc ON p.id_employe = vsc.id_employe " +
                "WHERE (" +
                "   st.code = 'ANOMALIE_SAISIE' " +
                "   OR st.code = 'DEMI_JOURNEE' " +
                "   OR (st.code = 'ABSENCE_NON_JUSTIFIEE' AND EXTRACT(DOW FROM p.date_pointage) NOT IN (0, 6)) " +
                "   OR (st.code = 'ABSENCE_JUSTIFIEE' AND EXTRACT(DOW FROM p.date_pointage) NOT IN (0, 6)) " +
                "   OR (p.minutes_retard > 0) " +
                "   OR (p.motif_ajustement IS NOT NULL AND TRIM(p.motif_ajustement) != '')" +
                ") "
            );

            List<Object> params = new ArrayList<>();
            if (idSite != null && idSite > 0) {
                sql.append("AND p.id_site = ? ");
                params.add(idSite);
            }
            if (effectivePeriodeId != null && effectivePeriodeId > 0) {
                sql.append("AND p.id_periode_paie = ? ");
                params.add(effectivePeriodeId);
            }
            if (datePointage != null && !datePointage.trim().isEmpty()) {
                sql.append("AND p.date_pointage = ? ");
                params.add(Date.valueOf(datePointage));
            }
            if (search != null && !search.trim().isEmpty()) {
                sql.append("AND (LOWER(e.nom) LIKE ? OR LOWER(e.prenom) LIKE ? OR LOWER(e.matricule) LIKE ?) ");
                String term = "%" + search.trim().toLowerCase() + "%";
                params.add(term);
                params.add(term);
                params.add(term);
            }

            sql.append("ORDER BY p.date_pointage DESC, e.nom, e.prenom");

            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PointageAnomalieDTO dto = new PointageAnomalieDTO();
                        dto.setIdPointage(rs.getInt("id_pointage"));
                        dto.setIdEmploye(rs.getInt("id_employe"));
                        dto.setMatricule(rs.getString("matricule"));
                        dto.setNom(rs.getString("nom"));
                        dto.setPrenom(rs.getString("prenom"));
                        dto.setPhotoUrl(rs.getString("photo_url"));
                        dto.setIdSite(rs.getInt("id_site"));
                        dto.setSiteNom(rs.getString("site_nom"));
                        dto.setSiteHoraireContinu(rs.getBoolean("site_horaire_continu"));
                        Time debutMatinSite = rs.getTime("heure_debut_matin_site");
                        Time finMatinSite = rs.getTime("heure_fin_matin_site");
                        Time debutAmSite = rs.getTime("heure_debut_am_site");
                        Time finAmSite = rs.getTime("heure_fin_am_site");
                        dto.setHeureDebutMatinSite(debutMatinSite != null ? debutMatinSite.toString().substring(0, 5) : null);
                        dto.setHeureFinMatinSite(finMatinSite != null ? finMatinSite.toString().substring(0, 5) : null);
                        dto.setHeureDebutAmSite(debutAmSite != null ? debutAmSite.toString().substring(0, 5) : null);
                        dto.setHeureFinAmSite(finAmSite != null ? finAmSite.toString().substring(0, 5) : null);
                        Date d = rs.getDate("date_pointage");
                        LocalDate ldPointage = (d != null) ? d.toLocalDate() : null;
                        dto.setDatePointage(d != null ? d.toString() : null);

                        Time em = rs.getTime("heure_entree_matin");
                        Time sm = rs.getTime("heure_sortie_matin");
                        Time ea = rs.getTime("heure_entree_am");
                        Time sa = rs.getTime("heure_sortie_am");
                        dto.setHeureEntreeMatin(em != null ? em.toString().substring(0, 5) : "");
                        dto.setHeureSortieMatin(sm != null ? sm.toString().substring(0, 5) : "");
                        dto.setHeureEntreeAm(ea != null ? ea.toString().substring(0, 5) : "");
                        dto.setHeureSortieAm(sa != null ? sa.toString().substring(0, 5) : "");
                        dto.setTotalHeures(rs.getDouble("total_heures"));
                        dto.setMinutesRetard(rs.getInt("minutes_retard"));
                        dto.setStatutCode(rs.getString("statut_code"));
                        dto.setStatutLibelle(rs.getString("statut_libelle"));
                        dto.setMotifAjustement(rs.getString("motif_ajustement"));
                        dto.setIdPeriodePaie(rs.getInt("id_periode_paie"));
                        dto.setPeriodeCode(rs.getString("periode_code"));
                        dto.setPeriodeLibelle(rs.getString("periode_libelle"));
                        dto.setStatutPeriodeCode(rs.getString("statut_periode_code"));
                        dto.setVersion(rs.getInt("version"));

                        // Solde de congés
                        dto.setSoldeCongesRestant(rs.getDouble("solde_restant"));
                        dto.setSoldeCongesAcquis(rs.getDouble("solde_acquis"));
                        dto.setSoldeCongesPris(rs.getDouble("solde_pris"));

                        // Code semaine S1..S5
                        String semCode = computeSemaineCode(ldPointage, periodeDebut);
                        dto.setSemaineCode(semCode);

                        // Indemnités
                        boolean depAccorde = Boolean.TRUE.equals(rs.getObject("dep_accorde"));
                        BigDecimal depQte = zeroIfNull(rs.getBigDecimal("dep_quantite"));
                        String depFrac = rs.getString("dep_fraction");
                        if (depAccorde) {
                            if ("MATIN".equalsIgnoreCase(depFrac)) {
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(false);
                            } else if ("APRES_MIDI".equalsIgnoreCase(depFrac)) {
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(true);
                            } else {
                                dto.setDeplacementMatin(depQte.signum() > 0);
                                dto.setDeplacementAm(depQte.compareTo(BigDecimal.ONE) >= 0);
                            }
                        } else {
                            dto.setDeplacementMatin(false);
                            dto.setDeplacementAm(false);
                        }
                        dto.setIndemniteRepasRecue(Boolean.TRUE.equals(rs.getObject("repas_accorde")));
                        dto.setDemandeEnAttenteLibelle(rs.getString("demande_en_attente_libelle"));

                        // Qualification de l'anomalie, sévérité & blocage
                        String stCode = dto.getStatutCode();
                        boolean hasMotif = dto.getMotifAjustement() != null && !dto.getMotifAjustement().trim().isEmpty();
                        boolean absenceTraitee = rs.getTimestamp("date_ajustement") != null
                                || rs.getObject("id_demande_conge") != null
                                || rs.getObject("demande_validee_id") != null;

                        boolean hasMatinComplet = (em != null && sm != null && ea == null && sa == null);
                        boolean hasAmComplet = (em == null && sm == null && ea != null && sa != null);
                        boolean isDemiJournee = "DEMI_JOURNEE".equals(stCode) || hasMatinComplet || hasAmComplet;

                        if (isDemiJournee) {
                            dto.setTypeAnomalie("DEMI_JOURNEE");
                            dto.setDescriptionAnomalie(hasMotif 
                                    ? ("Demi-journée validée : " + dto.getMotifAjustement()) 
                                    : "Demi-journée travaillée : motif de régularisation conseillé.");
                            dto.setSeverite(hasMotif ? "FAIBLE" : "MOYENNE");
                            dto.setBloquantTransmission(false);
                            dto.setStatutAnomalie(hasMotif ? "done" : "todo");
                            if (hasMotif) countFaibles++; else countMoyennes++;
                        } else if ("ANOMALIE_SAISIE".equals(stCode)) {
                            dto.setTypeAnomalie("INCOMPLET");
                            dto.setDescriptionAnomalie("Pointage incomplet : heure d'entrée ou de sortie manquante.");
                            dto.setSeverite("CRITIQUE");
                            dto.setBloquantTransmission(true);
                            dto.setStatutAnomalie(hasMotif ? "done" : "todo");
                            countIncomplets++;
                            countCritiques++;
                            countBloquantes++;
                        } else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode)) {
                            dto.setTypeAnomalie("ABSENCE");
                            if (absenceTraitee) {
                                dto.setDescriptionAnomalie("Absence non justifiée confirmée" +
                                        (hasMotif ? " : " + dto.getMotifAjustement() : "."));
                                dto.setSeverite("FAIBLE");
                                countFaibles++;
                            } else if (dto.getDemandeEnAttenteLibelle() != null && !dto.getDemandeEnAttenteLibelle().isBlank()) {
                                dto.setDescriptionAnomalie("Absence : demande de " + dto.getDemandeEnAttenteLibelle() + " en attente de validation.");
                                dto.setSeverite("MOYENNE");
                                countMoyennes++;
                            } else if (!hasMotif) {
                                dto.setDescriptionAnomalie("Absence non justifiée (non régularisée sur jour ouvré).");
                                dto.setSeverite("CRITIQUE");
                                dto.setBloquantTransmission(true);
                                countCritiques++;
                            } else {
                                dto.setDescriptionAnomalie("Absence non justifiée avec proposition : " + dto.getMotifAjustement());
                                dto.setSeverite("HAUTE");
                                countHautes++;
                            }
                            dto.setBloquantTransmission(!absenceTraitee);
                            if (!absenceTraitee) countBloquantes++;
                            dto.setStatutAnomalie(absenceTraitee ? "done" : "todo");
                            countAbsences++;
                        } else if ("ABSENCE_JUSTIFIEE".equals(stCode)) {
                            dto.setTypeAnomalie("ABSENCE");
                            dto.setDescriptionAnomalie(absenceTraitee
                                    ? "Absence justifiée confirmée : " + dto.getMotifAjustement()
                                    : "Absence justifiée à vérifier : " + dto.getMotifAjustement());
                            dto.setSeverite(absenceTraitee ? "FAIBLE" : "MOYENNE");
                            dto.setBloquantTransmission(!absenceTraitee);
                            dto.setStatutAnomalie(absenceTraitee ? "done" : "todo");
                            if (absenceTraitee) countFaibles++; else { countMoyennes++; countBloquantes++; }
                            countAbsences++;
                        } else if (dto.getMinutesRetard() != null && dto.getMinutesRetard() > 0) {
                            dto.setTypeAnomalie("RETARD");
                            dto.setDescriptionAnomalie(hasMotif
                                    ? "Retard de " + dto.getMinutesRetard() + " min régularisé : " + dto.getMotifAjustement()
                                    : "Retard de " + dto.getMinutesRetard() + " min à vérifier : corriger les horaires s'ils sont erronés, sinon confirmer le pointage réel.");
                            dto.setSeverite("MOYENNE");
                            dto.setBloquantTransmission(false);
                            dto.setStatutAnomalie(hasMotif ? "done" : "todo");
                            countRetards++;
                            countMoyennes++;
                        } else {
                            dto.setTypeAnomalie("AUTRE");
                            dto.setDescriptionAnomalie("Pointage régularisé.");
                            dto.setSeverite("FAIBLE");
                            dto.setBloquantTransmission(false);
                            dto.setStatutAnomalie(hasMotif ? "done" : "todo");
                            countFaibles++;
                        }

                        anomalies.add(dto);
                    }
                }
            }

            // Calcul du nombre total d'employés suivis
            int totalEmployes = 0;
            String sqlCountEmp = "SELECT COUNT(DISTINCT id_employe) FROM pointage_jour WHERE id_periode_paie = ? " +
                    (idSite != null && idSite > 0 ? "AND id_site = ? " : "");
            try (PreparedStatement psCE = c.prepareStatement(sqlCountEmp)) {
                psCE.setInt(1, effectivePeriodeId);
                if (idSite != null && idSite > 0) psCE.setInt(2, idSite);
                try (ResultSet rsCE = psCE.executeQuery()) {
                    if (rsCE.next()) totalEmployes = rsCE.getInt(1);
                }
            }
            if (totalEmployes == 0) {
                try (PreparedStatement psFallback = c.prepareStatement("SELECT COUNT(*) FROM employe WHERE est_actif = TRUE")) {
                    try (ResultSet rsFb = psFallback.executeQuery()) {
                        if (rsFb.next()) totalEmployes = rsFb.getInt(1);
                    }
                }
            }
            if (totalEmployes == 0) totalEmployes = 1;

            // Calcul des données globales de pointage pour la période
            int totalJoursSaisis = 0;
            int totalJoursDistinctsSaisis = 0;
            double totalHeuresCumulees = 0.0;
            BigDecimal totalIndemnites = BigDecimal.ZERO;
            String sqlTot = "SELECT COUNT(DISTINCT date_pointage) AS nb_distinct_dates, COUNT(*) AS nb_j, COALESCE(SUM(total_heures), 0) AS tot_h, COALESCE(SUM(montant_total_indemnites), 0) AS tot_ind " +
                            "FROM pointage_jour WHERE id_periode_paie = ? " +
                            (idSite != null && idSite > 0 ? "AND id_site = ? " : "");
            try (PreparedStatement psTot = c.prepareStatement(sqlTot)) {
                psTot.setInt(1, effectivePeriodeId);
                if (idSite != null && idSite > 0) psTot.setInt(2, idSite);
                try (ResultSet rsTot = psTot.executeQuery()) {
                    if (rsTot.next()) {
                        totalJoursDistinctsSaisis = rsTot.getInt("nb_distinct_dates");
                        totalJoursSaisis = rsTot.getInt("nb_j");
                        totalHeuresCumulees = Math.round(rsTot.getDouble("tot_h") * 10.0) / 10.0;
                        totalIndemnites = MoneyRoundingPolicy.amount(rsTot.getBigDecimal("tot_ind"));
                    }
                }
            }

            // Jours ouvrables théoriques dans la période
            int joursOuvrablesPeriode = calendrierOuvrableEngine.compterJoursOuvrables(periodeDebut, periodeFin, idSite, c);
            int totalJoursTheoriques = Math.max(1, joursOuvrablesPeriode);

            // Construction de la progression hebdomadaire (S1 à S5)
            List<Map<String, Object>> semainesList = new ArrayList<>();
            LocalDate today = LocalDate.now();
            LocalDate firstMonday = periodeDebut.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate curMonday = firstMonday;
            int wIndex = 1;
            DateTimeFormatter dtfDay = DateTimeFormatter.ofPattern("d", Locale.FRENCH);
            DateTimeFormatter dtfMonth = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);

            while (!curMonday.isAfter(periodeFin) && wIndex <= 5) {
                LocalDate wDeb = curMonday.isBefore(periodeDebut) ? periodeDebut : curMonday;
                LocalDate sunday = curMonday.plusDays(6);
                LocalDate wFin = sunday.isAfter(periodeFin) ? periodeFin : sunday;
                String semCode = "S" + wIndex;

                String datesLabel;
                if (wDeb.getMonth() == wFin.getMonth()) {
                    datesLabel = wDeb.format(dtfDay) + " — " + wFin.format(dtfDay) + " " + wFin.format(dtfMonth);
                } else {
                    datesLabel = wDeb.format(dtfDay) + " " + wDeb.format(dtfMonth) + " — " + wFin.format(dtfDay) + " " + wFin.format(dtfMonth);
                }

                int joursDistinctsSem = 0;
                int joursSaisisSem = 0;
                double heuresSem = 0.0;
                String sqlW = "SELECT COUNT(DISTINCT date_pointage) AS nb_distinct_dates, COUNT(*) AS nb_saisis, COALESCE(SUM(total_heures), 0) AS total_h " +
                              "FROM pointage_jour WHERE date_pointage BETWEEN ? AND ? " +
                              (effectivePeriodeId != null ? "AND id_periode_paie = ? " : "") +
                              (idSite != null && idSite > 0 ? "AND id_site = ? " : "");
                try (PreparedStatement psW = c.prepareStatement(sqlW)) {
                    int pIdx = 1;
                    psW.setDate(pIdx++, Date.valueOf(wDeb));
                    psW.setDate(pIdx++, Date.valueOf(wFin));
                    if (effectivePeriodeId != null) psW.setInt(pIdx++, effectivePeriodeId);
                    if (idSite != null && idSite > 0) psW.setInt(pIdx++, idSite);
                    try (ResultSet rsW = psW.executeQuery()) {
                        if (rsW.next()) {
                            joursDistinctsSem = rsW.getInt("nb_distinct_dates");
                            joursSaisisSem = rsW.getInt("nb_saisis");
                            heuresSem = Math.round(rsW.getDouble("total_h") * 10.0) / 10.0;
                        }
                    }
                }

                long nbAnomaliesSemaine = anomalies.stream()
                        .filter(a -> semCode.equals(a.getSemaineCode()) && "todo".equals(a.getStatutAnomalie()))
                        .count();

                int joursOuvresSem = calendrierOuvrableEngine.compterJoursOuvrables(wDeb, wFin, idSite, c);
                int theoriqueSem = Math.max(1, joursOuvresSem);
                int progressPercent = Math.min(100, (int) Math.round(((double) joursDistinctsSem / theoriqueSem) * 100.0));

                boolean isCurrent = !today.isBefore(wDeb) && !today.isAfter(wFin);
                boolean isCloturee = today.isAfter(wFin) && nbAnomaliesSemaine == 0;

                Map<String, Object> wMap = new HashMap<>();
                wMap.put("semaineCode", semCode);
                wMap.put("titre", "Semaine " + wIndex);
                wMap.put("dates", datesLabel);
                wMap.put("dateDebut", wDeb.toString());
                wMap.put("dateFin", wFin.toString());
                wMap.put("joursSaisis", joursDistinctsSem);
                wMap.put("joursTheoriques", theoriqueSem);
                wMap.put("joursHommeSaisis", joursSaisisSem);
                wMap.put("joursHommeTheoriques", Math.max(1, totalEmployes * joursOuvresSem));
                wMap.put("progressPercent", progressPercent);
                wMap.put("heuresCumulees", heuresSem);
                wMap.put("nbAnomalies", nbAnomaliesSemaine);
                wMap.put("isCurrent", isCurrent);
                wMap.put("isCloturee", isCloturee);
                semainesList.add(wMap);

                curMonday = curMonday.plusWeeks(1);
                wIndex++;
            }

            // Statistiques et KPIs
            Map<String, Integer> stats = new HashMap<>();
            stats.put("total", anomalies.size());
            stats.put("incomplets", countIncomplets);
            stats.put("retards", countRetards);
            stats.put("absences", countAbsences);
            stats.put("critiques", countCritiques);
            stats.put("hautes", countHautes);
            stats.put("moyennes", countMoyennes);
            stats.put("faibles", countFaibles);
            stats.put("bloquantes", countBloquantes);

            Map<String, Object> kpis = new HashMap<>();
            kpis.put("collaborateursSuivis", totalEmployes);
            kpis.put("joursSaisis", totalJoursDistinctsSaisis);
            kpis.put("joursTheoriques", totalJoursTheoriques);
            kpis.put("joursHommeSaisis", totalJoursSaisis);
            kpis.put("joursHommeTheoriques", totalEmployes * totalJoursTheoriques);
            kpis.put("heuresCumulees", totalHeuresCumulees);
            kpis.put("anomaliesActives", anomalies.stream().filter(a -> "todo".equals(a.getStatutAnomalie())).count());
            kpis.put("anomaliesCritiques", countCritiques);
            kpis.put("anomaliesBloquantes", countBloquantes);
            kpis.put("indemnitesPrevisionnelles", totalIndemnites);

            Map<String, Object> perInfo = new HashMap<>();
            perInfo.put("idPeriodePaie", effectivePeriodeId);
            perInfo.put("code", periodeCode);
            perInfo.put("libelle", periodeLibelle);
            perInfo.put("dateDebut", periodeDebut.toString());
            perInfo.put("dateFin", periodeFin.toString());
            perInfo.put("statutCode", statutPeriodeCode);
            perInfo.put("statutLibelle", statutPeriodeLibelle);

            response.put("anomalies", anomalies);
            response.put("stats", stats);
            response.put("semaines", semainesList);
            response.put("kpis", kpis);
            response.put("periode", perInfo);
        }

        return response;
    }

    /**
     * Régularise une anomalie avec support :
     * - Mode 1 : Saisie/rectification des heures réelles (oubli badgeage)
     * - Mode 2 : Régularisation d'absence (Congé payé débit solde, Permission 10j, Absence déduite)
     * Recalcul automatique et immédiat des synthèses hebdomadaires et mensuelles.
     */
    public boolean regulariserPointage(int idPointage, Map<String, Object> payload) throws Exception {
        String typeRegularisation = payload.get("typeRegularisation") != null ? payload.get("typeRegularisation").toString() : "HEURES";
        if ("HEURES".equalsIgnoreCase(typeRegularisation)
                && !(payload.get("dynamicIndemnites") instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("La sélection des indemnités du site doit être chargée avant toute correction d'horaires.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> dynamicAllowances = payload.get("dynamicIndemnites") instanceof Map<?, ?> requested
                ? (Map<String, Object>) requested : Map.of();
        String optionAbsence = payload.get("optionAbsence") != null ? payload.get("optionAbsence").toString() : "";
        String motif = payload.get("motifAjustement") != null ? payload.get("motifAjustement").toString() : null;

        Time em = PointageService.parseTime(payload.get("heureEntreeMatin") != null ? payload.get("heureEntreeMatin").toString() : null);
        Time sm = PointageService.parseTime(payload.get("heureSortieMatin") != null ? payload.get("heureSortieMatin").toString() : null);
        Time ea = PointageService.parseTime(payload.get("heureEntreeAm") != null ? payload.get("heureEntreeAm").toString() : null);
        Time sa = PointageService.parseTime(payload.get("heureSortieAm") != null ? payload.get("heureSortieAm").toString() : null);


        try (Connection c = dataSource.getConnection()) {
            boolean originalAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                LocalTime siteDebM = null;
                LocalTime siteFinM = null;
                LocalTime siteDebA = null;
                LocalTime siteFinA = null;
                LocalTime heureMinimaleComptabilisee = null;
                boolean horaireContinuSite = false;

                Integer idSite = null;
                if (idPointage > 0) {
                    String sqlSite = "SELECT s.id_site, s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                                     "s.horaire_continu, s.plancher_arrivee_actif, s.heure_entree_minimale_valorisee " +
                                     "FROM pointage_jour p " +
                                     "JOIN ref_site s ON p.id_site = s.id_site " +
                                     "WHERE p.id_pointage = ?";
                    try (PreparedStatement psS = c.prepareStatement(sqlSite)) {
                        psS.setInt(1, idPointage);
                        try (ResultSet rsS = psS.executeQuery()) {
                            if (rsS.next()) {
                                idSite = rsS.getInt("id_site");
                                Time hdm = rsS.getTime("heure_debut_matin");
                                Time hfm = rsS.getTime("heure_fin_matin");
                                Time hda = rsS.getTime("heure_debut_apres_midi");
                                Time hfa = rsS.getTime("heure_fin_apres_midi");
                                boolean cont = rsS.getBoolean("horaire_continu");
                                if (rsS.wasNull()) {
                                    throw new IllegalStateException("Type d'horaire non configuré pour le site " + idSite + ".");
                                }
                                horaireContinuSite = cont;

                                siteDebM = hdm != null ? hdm.toLocalTime() : null;
                                siteFinM = hfm != null ? hfm.toLocalTime() : null;
                                siteDebA = hda != null ? hda.toLocalTime() : null;
                                siteFinA = hfa != null ? hfa.toLocalTime() : null;
                                boolean plancherActif = rsS.getBoolean("plancher_arrivee_actif");
                                Time arrivalFloor = plancherActif ? rsS.getTime("heure_entree_minimale_valorisee") : null;
                                heureMinimaleComptabilisee = arrivalFloor == null ? null : arrivalFloor.toLocalTime();
                                boolean horairesComplets = siteDebM != null && siteFinA != null
                                        && (cont || (siteFinM != null && siteDebA != null));
                                if (!horairesComplets) {
                                    throw new IllegalStateException("Horaires de travail incomplets pour le site " + idSite + ".");
                                }
                            }
                        }
                    }
                } else if (payload.get("idSite") != null) {
                    idSite = parseInteger(payload.get("idSite"));
                }

                boolean isJourneeContinue = false;
                if (payload.get("horaireContinuJour") != null) {
                    isJourneeContinue = parseBoolean(payload.get("horaireContinuJour"));
                } else if (payload.get("horaireContinu") != null) {
                    isJourneeContinue = parseBoolean(payload.get("horaireContinu"));
                } else {
                    isJourneeContinue = horaireContinuSite;
                }
                boolean horairesComplets = siteDebM != null && siteFinA != null
                        && (isJourneeContinue || (siteFinM != null && siteDebA != null));
                if (!horairesComplets) {
                    throw new IllegalStateException("Horaires de travail incomplets ou site introuvable pour le pointage " + idPointage + ".");
                }

                // 1. Identification du pointage, employé, période, date
                Integer idEmp = null;
                Integer idPer = null;
                Date datePtg = null;
                Integer currentVersion = null;

                if (idPointage > 0) {
                    String sqlFindPtg = "SELECT id_employe, id_site, id_periode_paie, date_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_pointage = ? FOR UPDATE";
                    try (PreparedStatement psF = c.prepareStatement(sqlFindPtg)) {
                        psF.setInt(1, idPointage);
                        try (ResultSet rsF = psF.executeQuery()) {
                            if (rsF.next()) {
                                idEmp = rsF.getInt("id_employe");
                                if (idSite == null || idSite <= 0) {
                                    idSite = rsF.getInt("id_site");
                                }
                                idPer = rsF.getInt("id_periode_paie");
                                datePtg = rsF.getDate("date_pointage");
                                currentVersion = rsF.getInt("version");
                            } else {
                                throw new IllegalArgumentException("Pointage introuvable (ID: " + idPointage + ").");
                            }
                        }
                    }
                } else {
                if (payload.get("idEmploye") != null) idEmp = parseInteger(payload.get("idEmploye"));
                if (payload.get("idPeriodePaie") != null) idPer = parseInteger(payload.get("idPeriodePaie"));
                if (payload.get("datePointage") != null) {
                    try {
                        datePtg = Date.valueOf(payload.get("datePointage").toString().trim());
                    } catch (Exception ignored) {}
                }
            }

            if (idEmp == null || datePtg == null) {
                throw new IllegalArgumentException("L'employé et la date de pointage sont requis.");
            }
            if (idSite == null || idSite <= 0) {
                throw new IllegalArgumentException("Le site du pointage est obligatoire ; aucun site par défaut n'est appliqué.");
            }

            // Contrôle d'éligibilité et verrouillage de période
            pointageSaisieService.verifierEligibilitePointage(c, idEmp, idSite, datePtg);

            if ((idPer == null || idPer <= 0) && datePtg != null) {
                String sqlFindPer2 = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
                try (PreparedStatement psF2 = c.prepareStatement(sqlFindPer2)) {
                    psF2.setDate(1, datePtg);
                    try (ResultSet rsF2 = psF2.executeQuery()) {
                        if (rsF2.next()) {
                            idPer = rsF2.getInt("id_periode_paie");
                        }
                    }
                }
            }

            if (idPer != null && idPer > 0) {
                String sqlCheckPer = "SELECT s.code FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE p.id_periode_paie = ? FOR SHARE";
                try (PreparedStatement psCP = c.prepareStatement(sqlCheckPer)) {
                    psCP.setInt(1, idPer);
                    try (ResultSet rsCP = psCP.executeQuery()) {
                        if (rsCP.next()) {
                            String stCode = rsCP.getString("code");
                            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                            boolean isAdminOrDaf = auth != null && auth.getAuthorities().stream().anyMatch(a -> 
                                "ROLE_ADMIN".equals(a.getAuthority()) || "POINTAGE_VALIDATE".equals(a.getAuthority())
                            );

                            if ("CLOTUREE".equalsIgnoreCase(stCode)) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est clôturée de manière définitive.");
                            }
                            if ("VALIDEE".equalsIgnoreCase(stCode) || "PAIE_VALIDEE".equalsIgnoreCase(stCode)) {
                                throw new IllegalStateException("Pointage déjà gelé pour la Paie : réouvrir la période par le workflow autorisé avant toute correction.");
                            }
                            if (("A_VALIDER".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) && !isAdminOrDaf) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est en attente de validation.");
                            }
                        }
                    }
                }
            }

            if ("RETARD_REEL".equalsIgnoreCase(typeRegularisation)) {
                Integer providedVersion = parseInteger(payload.get("version"));
                if (idPointage <= 0 || providedVersion == null || !providedVersion.equals(currentVersion)) {
                    throw new IllegalStateException("Le pointage a changé. Rechargez la journée avant de confirmer le retard.");
                }
                Integer userId = currentUserId();
                if (userId == null) throw new IllegalStateException("Utilisateur de régularisation introuvable.");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pointage_jour SET motif_ajustement = 'Retard réel confirmé', " +
                        "ajuste_par = ?, date_ajustement = CURRENT_TIMESTAMP, version = version + 1, " +
                        "updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ? AND version = ? AND minutes_retard > 0")) {
                    ps.setInt(1, userId);
                    ps.setInt(2, idPointage);
                    ps.setInt(3, providedVersion);
                    if (ps.executeUpdate() != 1) {
                        throw new IllegalStateException("Ce pointage ne contient plus de retard à confirmer.");
                    }
                }
                c.commit();
                return true;
            }

            // Détermination du statut cible et calcul des heures
            String codeStatut = (String) payload.get("statutCode");
            double hMatin = 0.0;
            double hAm = 0.0;
            double totalH = 0.0;
            int retMins = 0;
            double hNuit = 0.0;
            Time emEffectif = null;
            Integer validatedLeaveId = null;
            Date leaveEndDate = null;
            List<LeavePointageDay> extraLeaveDays = List.of();

            if ("ABSENCE".equalsIgnoreCase(typeRegularisation) || "CONGE_PAYE".equalsIgnoreCase(optionAbsence) || "PERMISSION_EXCEPTIONNELLE".equalsIgnoreCase(optionAbsence) || "ABSENCE_DEDUITE".equalsIgnoreCase(optionAbsence)) {
                // Régularisation d'absence
                em = null; sm = null; ea = null; sa = null;

                if ("CONGE_PAYE".equalsIgnoreCase(optionAbsence) || "CONGE".equalsIgnoreCase(codeStatut)) {
                    String returnDateValue = payload.get("dateReprise") != null ? payload.get("dateReprise").toString() : null;
                    LocalDate returnDate = returnDateValue == null || returnDateValue.isBlank()
                            ? datePtg.toLocalDate().plusDays(1) : LocalDate.parse(returnDateValue);
                    if (!returnDate.isAfter(datePtg.toLocalDate())) {
                        throw new IllegalArgumentException("La date de reprise doit être postérieure au premier jour de congé.");
                    }
                    leaveEndDate = Date.valueOf(returnDate.minusDays(1));
                    boolean halfDay = parseBoolean(payload.get("estDemiJournee"));
                    if (halfDay && !leaveEndDate.equals(datePtg)) {
                        throw new IllegalArgumentException("Une demi-journée ne peut pas couvrir plusieurs dates.");
                    }
                    double nbJours = halfDay ? 0.5 : ChronoUnit.DAYS.between(datePtg.toLocalDate(), returnDate);
                    if (nbJours > 1) {
                        extraLeaveDays = verifierIntervalleRegularisation(c, idEmp, datePtg.toLocalDate(), leaveEndDate.toLocalDate());
                    }

                    // Créer la demande de congé annuel et débiter le solde dans mouvement_solde_conge
                    String momentConge = payload.get("momentDemiJournee") != null
                            ? payload.get("momentDemiJournee").toString() : null;
                    mg.galaxam.sirh.conge.application.RegularisationCongeResult regRes = congeService.regulariserCongeAnnuelDepuisPointageStructure(
                            idEmp, datePtg, leaveEndDate, nbJours, momentConge, motif, currentUserId(), c);
                    validatedLeaveId = regRes.demandeId();

                    if (regRes.joursCouverts() >= nbJours) {
                        codeStatut = "CONGE";
                    } else if (regRes.joursCouverts() > 0.0) {
                        // Couverture partielle : statut DEMI_JOURNEE (synthèse), l'absence résiduelle est décomptée
                        codeStatut = "DEMI_JOURNEE";
                    } else {
                        // Solde nul : aucune couverture, absence non couverte
                        codeStatut = (motif != null && !motif.isBlank()) ? "ABSENCE_JUSTIFIEE" : "ABSENCE_NON_JUSTIFIEE";
                    }
                } else if ("PERMISSION_EXCEPTIONNELLE".equalsIgnoreCase(optionAbsence)) {
                    codeStatut = "CONGE";
                    validatedLeaveId = congeService.regulariserPermissionDepuisPointage(
                            idEmp, datePtg, motif, currentUserId(), c);
                } else {
                    // Absence déduite (justifiée ou non justifiée)
                    if (codeStatut == null || codeStatut.trim().isEmpty() || "NON_SAISI".equals(codeStatut)) {
                        codeStatut = (motif != null && !motif.isBlank()) ? "ABSENCE_JUSTIFIEE" : "ABSENCE_NON_JUSTIFIEE";
                    }
                }
            } else {
                // Mode 1 : Saisie / Rectification des heures
                LocalTime effectiveEntry = PointageService.effectiveMorningEntry(local(em), heureMinimaleComptabilisee);
                if (effectiveEntry != null && ((isJourneeContinue && sa != null && effectiveEntry.isAfter(sa.toLocalTime()))
                        || (!isJourneeContinue && sm != null && effectiveEntry.isAfter(sm.toLocalTime())))) {
                    throw new IllegalArgumentException("La sortie précède l'heure minimale comptabilisée du site.");
                }
                emEffectif = effectiveEntry == null ? null : Time.valueOf(effectiveEntry);
                Map<String, mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.ParametreEtat> parametresPointage =
                        mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal.loadEtatMap(c);
                AttendanceEngine.Result dailyResult = dailyAttendanceCalculator.calculateDay(new AttendanceEngine.Context(
                        idEmp, datePtg.toLocalDate(), effectiveEntry, local(sm), local(ea), local(sa),
                        siteDebM, siteFinM, siteDebA, siteFinA,
                        PointageService.resolveOptionalTimeRule(parametresPointage, "SEUIL_NUIT_DEBUT"),
                        PointageService.resolveOptionalTimeRule(parametresPointage, "SEUIL_NUIT_FIN"),
                        isJourneeContinue, false, false, AttendanceEngine.Context.LeavePart.NONE,
                        false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false));
                hMatin = hours(dailyResult.morningMinutes());
                hAm = hours(dailyResult.afternoonMinutes());
                totalH = hours(dailyResult.workedMinutes());
                hNuit = hours(dailyResult.nightMinutes());
                retMins = Math.toIntExact(dailyResult.lateMinutes());
                boolean isDemiMatin = (em != null && sm != null && ea == null && sa == null);
                boolean isDemiAm = (em == null && sm == null && ea != null && sa != null);
                if (codeStatut == null || codeStatut.trim().isEmpty() || "NON_SAISI".equals(codeStatut) || "ANOMALIE_SAISIE".equals(codeStatut)) {
                    if (isDemiMatin || isDemiAm) {
                        codeStatut = "DEMI_JOURNEE";
                    } else {
                        codeStatut = totalH > 0 ? "PRESENT" : "ABSENCE_NON_JUSTIFIEE";
                    }
                }

                String optionAutreDemiJournee = payload.get("optionAutreDemiJournee") != null ? payload.get("optionAutreDemiJournee").toString() : "";
                if ((isDemiMatin || isDemiAm || "DEMI_JOURNEE".equalsIgnoreCase(codeStatut))
                        && ("CONGE_PAYE".equalsIgnoreCase(optionAutreDemiJournee) || "CONGE".equalsIgnoreCase(optionAutreDemiJournee))) {
                    String momentAbsent = isDemiMatin ? "APRES_MIDI" : "MATIN";
                    validatedLeaveId = congeService.regulariserCongeAnnuelDepuisPointage(idEmp, datePtg, 0.5,
                            momentAbsent,
                            (motif != null && !motif.isBlank()) ? motif : "Demi-journée congé payé (pointage partiel)",
                            currentUserId(), c);
                }
            }

            int targetVer = 1;
            if (idPointage > 0) {
                Integer providedVer = parseInteger(payload.get("version"));
                if (providedVer == null) {
                    throw new IllegalArgumentException("La version de la journée est obligatoire pour éviter une correction concurrente.");
                } else if (currentVersion != null && !providedVer.equals(currentVersion)) {
                    throw new IllegalStateException("Le pointage a été modifié par un autre utilisateur ; rechargez la journée avant de régulariser.");
                } else {
                    targetVer = providedVer;
                }
            }

            int persistedId = pointageSaisieService.saveRegularizedDay(
                    new PointageService.RegularizedDayWrite(
                            idPointage, idEmp, idPer, idSite, datePtg, em, sm, ea, sa,
                            hMatin, hAm, totalH, retMins, hNuit, motif, codeStatut,
                            isJourneeContinue, targetVer,
                            emEffectif, dynamicAllowances, validatedLeaveId), c);
            try (PreparedStatement confirmed = c.prepareStatement(
                    "UPDATE pointage_jour SET ajuste_par = ?, date_ajustement = CURRENT_TIMESTAMP WHERE id_pointage = ?")) {
                confirmed.setObject(1, currentUserId());
                confirmed.setInt(2, persistedId);
                confirmed.executeUpdate();
            }
            if (!extraLeaveDays.isEmpty() && validatedLeaveId != null) {
                enregistrerJoursCongeSupplementaires(c, idEmp, extraLeaveDays, validatedLeaveId, motif);
            }
            if (idPer != null && datePtg != null) {
            }
            c.commit();
            return persistedId > 0;
        } catch (Exception ex) {
            try {
                c.rollback();
            } catch (SQLException exRollback) {
                log.warn("Erreur lors du rollback : {}", exRollback.getMessage());
            }
            throw ex;
        } finally {
            try {
                c.setAutoCommit(originalAutoCommit);
            } catch (Exception ignored) {}
        }
    }
}

    private record LeavePointageDay(LocalDate date, int siteId, int periodId, int pointageId, int version) {}

    private List<LeavePointageDay> verifierIntervalleRegularisation(Connection c, int employeeId,
            LocalDate start, LocalDate end) throws SQLException {
        var holidays = calendrierOuvrableEngine.getDatesFeriesEtChomeesEmploye(start, end, employeeId, c);
        List<LeavePointageDay> days = new ArrayList<>();
        for (LocalDate day = start.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            if (CalendrierOuvrableEngine.isWeekend(day) || holidays.contains(day)) continue;
            int siteId = calendrierOuvrableEngine.resolveSiteAt(employeeId, day, c);
            pointageSaisieService.verifierEligibilitePointage(c, employeeId, siteId, Date.valueOf(day));

            int periodId;
            try (PreparedStatement period = c.prepareStatement(
                    "SELECT p.id_periode_paie, s.code FROM periode_paie p " +
                    "JOIN ref_statut_periode_paie s ON s.id_statut_periode = p.id_statut_periode " +
                    "WHERE ? BETWEEN p.date_debut AND p.date_fin FOR SHARE OF p")) {
                period.setDate(1, Date.valueOf(day));
                try (ResultSet result = period.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Aucune période de pointage pour le " + day + ".");
                    periodId = result.getInt(1);
                    if (!"EN_COURS".equals(result.getString(2))) {
                        throw new IllegalStateException("La période du " + day + " n'est plus ouverte à la saisie.");
                    }
                }
            }

            int pointageId = 0;
            int version = 1;
            try (PreparedStatement existing = c.prepareStatement(
                    "SELECT p.id_pointage, COALESCE(p.version, 1), COALESCE(p.total_heures, 0), " +
                    "st.code, p.id_demande_conge FROM pointage_jour p " +
                    "JOIN ref_statut_pointage_jour st ON st.id_statut_jour = p.id_statut_jour " +
                    "WHERE p.id_employe = ? AND p.date_pointage = ? FOR UPDATE OF p")) {
                existing.setInt(1, employeeId);
                existing.setDate(2, Date.valueOf(day));
                try (ResultSet result = existing.executeQuery()) {
                    if (result.next()) {
                        String status = result.getString(4);
                        if (result.getDouble(3) > 0 || result.getObject(5) != null ||
                                !List.of("ABSENCE_NON_JUSTIFIEE", "ABSENCE_JUSTIFIEE", "NON_SAISI", "ANOMALIE_SAISIE").contains(status)) {
                            throw new IllegalStateException("Le " + day + " possède déjà un pointage ou un congé à examiner avant régularisation.");
                        }
                        pointageId = result.getInt(1);
                        version = result.getInt(2);
                    }
                }
            }
            days.add(new LeavePointageDay(day, siteId, periodId, pointageId, version));
        }
        return days;
    }

    private void enregistrerJoursCongeSupplementaires(Connection c, int employeeId,
            List<LeavePointageDay> days, int leaveId, String reason) throws SQLException {
        for (LeavePointageDay day : days) {
            pointageSaisieService.saveRegularizedDay(new PointageService.RegularizedDayWrite(
                    day.pointageId(), employeeId, day.periodId(), day.siteId(), Date.valueOf(day.date()),
                    null, null, null, null, 0, 0, 0, 0, 0, reason, "CONGE", false,
                    day.version(), null, Map.of(), leaveId), c);
        }
    }

    private static Integer parseInteger(Object val) {
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer currentUserId() {
        org.springframework.security.core.Authentication authentication =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof mg.galaxam.sirh.securite.CustomUserDetails user) {
            return user.getIdUtilisateur();
        }
        return null;
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean parseBoolean(Object val) {
        if (val == null) return false;
        if (val instanceof Boolean b) return b;
        return "true".equalsIgnoreCase(val.toString().trim());
    }
}
