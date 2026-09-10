package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageAnomalieDTO;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
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

@Service
public class PointageAnomalieService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PointageSaisieService pointageSaisieService;

    @Autowired
    private PointageSyntheseService pointageSyntheseService;

    @Autowired
    private CalendrierOuvrableEngine calendrierOuvrableEngine;

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
                String sqlFindPer = (datePointage != null && !datePointage.trim().isEmpty()) ?
                    "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin ORDER BY date_debut DESC LIMIT 1" :
                    "SELECT p.id_periode_paie FROM periode_paie p JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode WHERE s.code = 'OUVERTE' ORDER BY p.date_debut DESC LIMIT 1";
                try (PreparedStatement psF = c.prepareStatement(sqlFindPer)) {
                    if (datePointage != null && !datePointage.trim().isEmpty()) {
                        psF.setDate(1, Date.valueOf(datePointage));
                    }
                    try (ResultSet rsF = psF.executeQuery()) {
                        if (rsF.next()) {
                            effectivePeriodeId = rsF.getInt(1);
                        }
                    }
                } catch (Exception ignored) {}

                if (effectivePeriodeId == null || effectivePeriodeId <= 0) {
                    try (PreparedStatement psF2 = c.prepareStatement("SELECT id_periode_paie FROM periode_paie ORDER BY date_debut DESC LIMIT 1")) {
                        try (ResultSet rsF2 = psF2.executeQuery()) {
                            if (rsF2.next()) {
                                effectivePeriodeId = rsF2.getInt(1);
                            }
                        }
                    } catch (Exception ignored) {}
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

            if (periodeDebut == null) {
                periodeDebut = LocalDate.now().withDayOfMonth(1);
                periodeFin = LocalDate.now().withDayOfMonth(LocalDate.now().lengthOfMonth());
            }

            StringBuilder sql = new StringBuilder(
                "SELECT p.id_pointage, p.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
                "p.id_site, s.libelle AS site_nom, p.date_pointage, " +
                "p.heure_entree_matin, p.heure_sortie_matin, p.heure_entree_am, p.heure_sortie_am, " +
                "p.total_heures, p.minutes_retard, st.code AS statut_code, st.libelle AS statut_libelle, " +
                "p.motif_ajustement, p.montant_total_indemnites, " +
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
                "LEFT JOIN v_solde_conge_employe vsc ON p.id_employe = vsc.id_employe " +
                "WHERE (" +
                "   st.code = 'ANOMALIE_SAISIE' " +
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
                        double depQte = rs.getDouble("dep_quantite");
                        String depFrac = rs.getString("dep_fraction");
                        if (depAccorde) {
                            if ("MATIN".equalsIgnoreCase(depFrac)) {
                                dto.setDeplacementMatin(true);
                                dto.setDeplacementAm(false);
                            } else if ("APRES_MIDI".equalsIgnoreCase(depFrac)) {
                                dto.setDeplacementMatin(false);
                                dto.setDeplacementAm(true);
                            } else {
                                dto.setDeplacementMatin(depQte > 0);
                                dto.setDeplacementAm(depQte >= 1.0);
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

                        if ("ANOMALIE_SAISIE".equals(stCode)) {
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
                            if (dto.getDemandeEnAttenteLibelle() != null && !dto.getDemandeEnAttenteLibelle().isBlank()) {
                                dto.setDescriptionAnomalie("Absence : demande de " + dto.getDemandeEnAttenteLibelle() + " en attente de validation RH.");
                                dto.setSeverite("MOYENNE");
                                dto.setBloquantTransmission(false);
                                countMoyennes++;
                            } else {
                                dto.setDescriptionAnomalie("Absence non justifiée sur un jour ouvré.");
                                dto.setSeverite("HAUTE");
                                // Bloque la transmission tant qu'aucun arbitrage/motif n'est formalisé
                                dto.setBloquantTransmission(!hasMotif);
                                countHautes++;
                                if (!hasMotif) countBloquantes++;
                            }
                            dto.setStatutAnomalie(hasMotif ? "done" : "todo");
                            countAbsences++;
                        } else if ("ABSENCE_JUSTIFIEE".equals(stCode)) {
                            dto.setTypeAnomalie("ABSENCE");
                            dto.setDescriptionAnomalie("Absence justifiée avec motif.");
                            dto.setSeverite("FAIBLE");
                            dto.setBloquantTransmission(false);
                            dto.setStatutAnomalie("done");
                            countFaibles++;
                            countAbsences++;
                        } else if (dto.getMinutesRetard() != null && dto.getMinutesRetard() > 0) {
                            dto.setTypeAnomalie("RETARD");
                            dto.setDescriptionAnomalie("Retard de " + dto.getMinutesRetard() + " min sans motif régularisé.");
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
            double totalHeuresCumulees = 0.0;
            double totalIndemnites = 0.0;
            String sqlTot = "SELECT COUNT(*) AS nb_j, COALESCE(SUM(total_heures), 0) AS tot_h, COALESCE(SUM(montant_total_indemnites), 0) AS tot_ind " +
                            "FROM pointage_jour WHERE id_periode_paie = ? " +
                            (idSite != null && idSite > 0 ? "AND id_site = ? " : "");
            try (PreparedStatement psTot = c.prepareStatement(sqlTot)) {
                psTot.setInt(1, effectivePeriodeId);
                if (idSite != null && idSite > 0) psTot.setInt(2, idSite);
                try (ResultSet rsTot = psTot.executeQuery()) {
                    if (rsTot.next()) {
                        totalJoursSaisis = rsTot.getInt("nb_j");
                        totalHeuresCumulees = Math.round(rsTot.getDouble("tot_h") * 10.0) / 10.0;
                        totalIndemnites = Math.round(rsTot.getDouble("tot_ind"));
                    }
                }
            }

            // Jours ouvrables théoriques dans la période
            int joursOuvrablesPeriode = calendrierOuvrableEngine.compterJoursOuvrables(periodeDebut, periodeFin, idSite, c);
            int totalJoursTheoriques = totalEmployes * Math.max(1, joursOuvrablesPeriode);

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

                int joursSaisisSem = 0;
                double heuresSem = 0.0;
                String sqlW = "SELECT COUNT(*) AS nb_saisis, COALESCE(SUM(total_heures), 0) AS total_h " +
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
                            joursSaisisSem = rsW.getInt("nb_saisis");
                            heuresSem = Math.round(rsW.getDouble("total_h") * 10.0) / 10.0;
                        }
                    }
                }

                long nbAnomaliesSemaine = anomalies.stream()
                        .filter(a -> semCode.equals(a.getSemaineCode()))
                        .count();

                int joursOuvresSem = calendrierOuvrableEngine.compterJoursOuvrables(wDeb, wFin, idSite, c);
                int theoriqueSem = Math.max(1, totalEmployes * joursOuvresSem);
                int progressPercent = Math.min(100, (int) Math.round(((double) joursSaisisSem / theoriqueSem) * 100.0));

                boolean isCurrent = !today.isBefore(wDeb) && !today.isAfter(wFin);
                boolean isCloturee = today.isAfter(wFin) && nbAnomaliesSemaine == 0;

                Map<String, Object> wMap = new HashMap<>();
                wMap.put("semaineCode", semCode);
                wMap.put("titre", "Semaine " + wIndex);
                wMap.put("dates", datesLabel);
                wMap.put("dateDebut", wDeb.toString());
                wMap.put("dateFin", wFin.toString());
                wMap.put("joursSaisis", joursSaisisSem);
                wMap.put("joursTheoriques", theoriqueSem);
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
            kpis.put("joursSaisis", totalJoursSaisis);
            kpis.put("joursTheoriques", totalJoursTheoriques);
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
    public boolean regulariserPointage(int idPointage, Map<String, Object> payload) throws SQLException {
        String typeRegularisation = (String) payload.getOrDefault("typeRegularisation", "HEURES");
        String optionAbsence = (String) payload.getOrDefault("optionAbsence", "");
        String motif = (String) payload.get("motifAjustement");

        Time em = PointageSaisieService.parseTime((String) payload.get("heureEntreeMatin"));
        Time sm = PointageSaisieService.parseTime((String) payload.get("heureSortieMatin"));
        Time ea = PointageSaisieService.parseTime((String) payload.get("heureEntreeAm"));
        Time sa = PointageSaisieService.parseTime((String) payload.get("heureSortieAm"));

        boolean depM = Boolean.TRUE.equals(payload.get("deplacementMatin"));
        boolean depA = Boolean.TRUE.equals(payload.get("deplacementAm"));
        boolean repas = Boolean.TRUE.equals(payload.get("indemniteRepasRecue"));

        try (Connection c = dataSource.getConnection()) {
            LocalTime siteDebM = LocalTime.of(8, 0);
            LocalTime siteFinM = LocalTime.of(12, 0);
            LocalTime siteDebA = LocalTime.of(13, 0);
            LocalTime siteFinA = LocalTime.of(17, 0);

            Integer idSite = null;
            if (idPointage > 0) {
                String sqlSite = "SELECT s.id_site, s.heure_debut_matin, s.heure_fin_matin, s.heure_debut_apres_midi, s.heure_fin_apres_midi, " +
                                 "COALESCE(s.horaire_continu, TRUE) AS horaire_continu, t.heure_plafond " +
                                 "FROM pointage_jour p " +
                                 "JOIN ref_site s ON p.id_site = s.id_site " +
                                 "LEFT JOIN ref_tolerance_arrivee_pointage t ON s.id_site = t.id_site " +
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

                            if (hdm != null) siteDebM = hdm.toLocalTime();
                            if (hfm != null) siteFinM = hfm.toLocalTime();
                            if (hda != null) siteDebA = hda.toLocalTime();
                            else if (!cont) siteDebA = LocalTime.of(14, 0);
                            if (hfa != null) siteFinA = hfa.toLocalTime();
                            else if (!cont) siteFinA = LocalTime.of(18, 0);
                        }
                    }
                }
            } else if (payload.get("idSite") != null) {
                idSite = ((Number) payload.get("idSite")).intValue();
            }

            Boolean horaireContinuJour = payload.get("horaireContinuJour") != null
                    ? Boolean.parseBoolean(String.valueOf(payload.get("horaireContinuJour")))
                    : (payload.get("horaireContinu") != null ? Boolean.parseBoolean(String.valueOf(payload.get("horaireContinu"))) : null);
            boolean isJourneeContinue = Boolean.TRUE.equals(horaireContinuJour);

            // 1. Identification du pointage, employé, période, date
            Integer idEmp = null;
            Integer idPer = null;
            Date datePtg = null;
            Integer currentVersion = null;

            if (idPointage > 0) {
                String sqlFindPtg = "SELECT id_employe, id_site, id_periode_paie, date_pointage, COALESCE(version, 1) AS version FROM pointage_jour WHERE id_pointage = ?";
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
                if (payload.get("idEmploye") != null) idEmp = ((Number) payload.get("idEmploye")).intValue();
                if (payload.get("idPeriodePaie") != null) idPer = ((Number) payload.get("idPeriodePaie")).intValue();
                if (payload.get("datePointage") != null) datePtg = Date.valueOf((String) payload.get("datePointage"));
            }

            if (idEmp == null || datePtg == null) {
                throw new IllegalArgumentException("L'employé et la date de pointage sont requis.");
            }
            if (idSite == null || idSite <= 0) idSite = 1;

            // Contrôle d'éligibilité et verrouillage de période
            pointageSaisieService.verifierEligibilitePointage(c, idEmp, idSite, datePtg);

            if ((idPer == null || idPer <= 0) && datePtg != null) {
                String sqlFindPer2 = "SELECT id_periode_paie FROM periode_paie WHERE ? BETWEEN date_debut AND date_fin LIMIT 1";
                try (PreparedStatement psF2 = c.prepareStatement(sqlFindPer2)) {
                    psF2.setDate(1, datePtg);
                    try (ResultSet rsF2 = psF2.executeQuery()) {
                        if (rsF2.next()) idPer = rsF2.getInt(1);
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
                            if ("VALIDEE".equalsIgnoreCase(stCode) && !isAdminOrDaf) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est déjà validée.");
                            }
                            if (("A_VALIDER".equalsIgnoreCase(stCode) || "TRANSMIS_DAF".equalsIgnoreCase(stCode)) && !isAdminOrDaf) {
                                throw new IllegalStateException("Impossible de modifier les pointages : la période de paie est en attente de validation DAF.");
                            }
                        }
                    }
                }
            }

            // Détermination du statut cible et calcul des heures
            String codeStatut = (String) payload.get("statutCode");
            double hMatin = 0.0;
            double hAm = 0.0;
            double totalH = 0.0;
            int retMins = 0;
            double hNuit = 0.0;

            if ("ABSENCE".equalsIgnoreCase(typeRegularisation) || "CONGE_PAYE".equalsIgnoreCase(optionAbsence) || "PERMISSION_EXCEPTIONNELLE".equalsIgnoreCase(optionAbsence) || "ABSENCE_DEDUITE".equalsIgnoreCase(optionAbsence)) {
                // Régularisation d'absence
                em = null; sm = null; ea = null; sa = null;
                depM = false; depA = false; repas = false;

                if ("CONGE_PAYE".equalsIgnoreCase(optionAbsence) || "CONGE".equalsIgnoreCase(codeStatut)) {
                    codeStatut = "CONGE";
                    double nbJours = (payload.get("nbJoursCongeDecompte") != null) 
                            ? ((Number) payload.get("nbJoursCongeDecompte")).doubleValue() : 1.0;
                    if (nbJours <= 0) nbJours = 1.0;

                    // Créer la demande de congé annuel et débiter le solde dans mouvement_solde_conge
                    Integer idTypeCongeAnnuel = null;
                    try (PreparedStatement psTc = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code = 'ANNUEL' LIMIT 1")) {
                        try (ResultSet rsTc = psTc.executeQuery()) {
                            if (rsTc.next()) idTypeCongeAnnuel = rsTc.getInt(1);
                        }
                    }
                    Integer idStatutValide = null;
                    try (PreparedStatement psStc = c.prepareStatement("SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'VALIDE' LIMIT 1")) {
                        try (ResultSet rsStc = psStc.executeQuery()) {
                            if (rsStc.next()) idStatutValide = rsStc.getInt(1);
                        }
                    }

                    if (idTypeCongeAnnuel != null && idStatutValide != null) {
                        Integer idDemande = null;
                        try (PreparedStatement psCheckDc = c.prepareStatement(
                                "SELECT id_demande_conge FROM demande_conge WHERE id_employe = ? AND date_debut <= ? AND date_fin >= ? LIMIT 1")) {
                            psCheckDc.setInt(1, idEmp);
                            psCheckDc.setDate(2, datePtg);
                            psCheckDc.setDate(3, datePtg);
                            try (ResultSet rsDc = psCheckDc.executeQuery()) {
                                if (rsDc.next()) idDemande = rsDc.getInt(1);
                            }
                        }

                        if (idDemande == null) {
                            String insertDc = "INSERT INTO demande_conge (id_employe, id_type_conge, id_statut_demande_conge, date_debut, date_fin, date_demande, nb_jours_decomptes, est_demi_journee, commentaire, created_at, updated_at) " +
                                    "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id_demande_conge";
                            try (PreparedStatement psInsDc = c.prepareStatement(insertDc)) {
                                psInsDc.setInt(1, idEmp);
                                psInsDc.setInt(2, idTypeCongeAnnuel);
                                psInsDc.setInt(3, idStatutValide);
                                psInsDc.setDate(4, datePtg);
                                psInsDc.setDate(5, datePtg);
                                psInsDc.setDouble(6, nbJours);
                                psInsDc.setBoolean(7, nbJours < 1.0);
                                psInsDc.setString(8, (motif != null && !motif.isBlank()) ? motif : "Régularisation absence sur congé payé annuel");
                                try (ResultSet rsId = psInsDc.executeQuery()) {
                                    if (rsId.next()) idDemande = rsId.getInt(1);
                                }
                            }
                        }

                        if (idDemande != null) {
                            String sqlCheckMvt = "SELECT COUNT(*) FROM mouvement_solde_conge WHERE id_demande_conge = ? AND type_mouvement = 'PRISE'";
                            boolean mvtExists = false;
                            try (PreparedStatement psCm = c.prepareStatement(sqlCheckMvt)) {
                                psCm.setInt(1, idDemande);
                                try (ResultSet rsCm = psCm.executeQuery()) {
                                    if (rsCm.next() && rsCm.getInt(1) > 0) mvtExists = true;
                                }
                            }
                            if (!mvtExists) {
                                String sqlInsMvt = "INSERT INTO mouvement_solde_conge (id_employe, type_mouvement, nb_jours, id_demande_conge, date_mouvement, commentaire) " +
                                        "VALUES (?, 'PRISE', ?, ?, ?, ?)";
                                try (PreparedStatement psIm = c.prepareStatement(sqlInsMvt)) {
                                    psIm.setInt(1, idEmp);
                                    psIm.setDouble(2, -nbJours);
                                    psIm.setInt(3, idDemande);
                                    psIm.setDate(4, datePtg);
                                    psIm.setString(5, "Décompte régularisation pointage : " + (motif != null ? motif : "Congé payé"));
                                    psIm.executeUpdate();
                                }
                            }
                        }
                    }
                } else if ("PERMISSION_EXCEPTIONNELLE".equalsIgnoreCase(optionAbsence)) {
                    codeStatut = "CONGE";
                    Integer idTypePerm = null;
                    try (PreparedStatement psTp = c.prepareStatement("SELECT id_type_conge FROM ref_type_conge WHERE code IN ('PERMISSION', 'EXCEPTIONNEL') ORDER BY id_type_conge LIMIT 1")) {
                        try (ResultSet rsTp = psTp.executeQuery()) {
                            if (rsTp.next()) idTypePerm = rsTp.getInt(1);
                        }
                    }
                    Integer idStatutValide = null;
                    try (PreparedStatement psStc = c.prepareStatement("SELECT id_statut_demande_conge FROM ref_statut_demande_conge WHERE code = 'VALIDE' LIMIT 1")) {
                        try (ResultSet rsStc = psStc.executeQuery()) {
                            if (rsStc.next()) idStatutValide = rsStc.getInt(1);
                        }
                    }
                    if (idTypePerm != null && idStatutValide != null) {
                        String insertPerm = "INSERT INTO demande_conge (id_employe, id_type_conge, id_statut_demande_conge, date_debut, date_fin, date_demande, nb_jours_decomptes, est_demi_journee, commentaire, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, CURRENT_DATE, 1.0, false, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) " +
                                "ON CONFLICT DO NOTHING";
                        try (PreparedStatement psInsP = c.prepareStatement(insertPerm)) {
                            psInsP.setInt(1, idEmp);
                            psInsP.setInt(2, idTypePerm);
                            psInsP.setInt(3, idStatutValide);
                            psInsP.setDate(4, datePtg);
                            psInsP.setDate(5, datePtg);
                            psInsP.setString(6, (motif != null && !motif.isBlank()) ? motif : "Permission exceptionnelle pour événement familial (Art. 88)");
                            psInsP.executeUpdate();
                        }
                    }
                } else {
                    // Absence déduite (justifiée ou non justifiée)
                    if (codeStatut == null || codeStatut.trim().isEmpty() || "NON_SAISI".equals(codeStatut)) {
                        codeStatut = (motif != null && !motif.isBlank()) ? "ABSENCE_JUSTIFIEE" : "ABSENCE_NON_JUSTIFIEE";
                    }
                }
            } else {
                // Mode 1 : Saisie / Rectification des heures
                boolean isPauseAuto = (!isJourneeContinue && em != null && sa != null && sm == null && ea == null);
                if (isJourneeContinue) {
                    hMatin = PointageSaisieService.calcHours(em, sa);
                    hAm = 0.0;
                } else if (isPauseAuto) {
                    hMatin = PointageSaisieService.calcHours(em, Time.valueOf(siteFinM));
                    hAm = PointageSaisieService.calcHours(Time.valueOf(siteDebA), sa);
                } else {
                    hMatin = PointageSaisieService.calcHours(em, sm);
                    hAm = PointageSaisieService.calcHours(ea, sa);
                }
                totalH = hMatin + hAm;
                hNuit = PointageSaisieService.calcHeuresNuit(sa);

                int retMatin = PointageSaisieService.calcMinutesRetard(em, siteDebM);
                int retAm = 0;
                if (!isJourneeContinue && !isPauseAuto && ea != null) {
                    if (sm != null && siteDebA.isAfter(siteFinM)) {
                        long pauseTheorique = java.time.Duration.between(siteFinM, siteDebA).toMinutes();
                        LocalTime ltSm = sm.toLocalTime();
                        LocalTime ltEa = ea.toLocalTime();
                        if (ltSm.isAfter(siteFinM) && ltEa.isAfter(ltSm)) {
                            long pauseReelle = java.time.Duration.between(ltSm, ltEa).toMinutes();
                            retAm = (int) Math.max(0, pauseReelle - pauseTheorique);
                        } else {
                            retAm = PointageSaisieService.calcMinutesRetard(ea, siteDebA);
                        }
                    } else {
                        retAm = PointageSaisieService.calcMinutesRetard(ea, siteDebA);
                    }
                }
                retMins = retMatin + retAm;

                if (codeStatut == null || codeStatut.trim().isEmpty() || "NON_SAISI".equals(codeStatut) || "ANOMALIE_SAISIE".equals(codeStatut)) {
                    codeStatut = totalH > 0 ? "PRESENT" : "ABSENCE_NON_JUSTIFIEE";
                }
            }

            boolean isNonTravaille = "CONGE".equalsIgnoreCase(codeStatut)
                    || "FERIE_CHOME".equalsIgnoreCase(codeStatut)
                    || "ABSENCE_NON_JUSTIFIEE".equalsIgnoreCase(codeStatut)
                    || "ABSENCE_JUSTIFIEE".equalsIgnoreCase(codeStatut)
                    || "REPOS".equalsIgnoreCase(codeStatut)
                    || (datePtg != null && CalendrierOuvrableEngine.isWeekend(datePtg.toLocalDate()) && totalH <= 0.0);

            if (isNonTravaille) {
                depM = false;
                depA = false;
                repas = false;
            }

            double fractionDep = (depM ? 0.5 : 0.0) + (depA ? 0.5 : 0.0);
            double mntDepJour = Math.round(fractionDep * (31200.0 / 22.0) * 100.0) / 100.0;
            double mntRepasJour = repas ? 2000.0 : 0.0;
            double mntTotalInd = (repas ? mntRepasJour : 0.0) + ((depM || depA) ? mntDepJour : 0.0);

            if (idPointage <= 0) {
                String sqlInsert = "INSERT INTO pointage_jour (" +
                    "id_employe, id_periode_paie, id_site, date_pointage, " +
                    "heure_entree_matin, heure_sortie_matin, heure_entree_am, heure_sortie_am, " +
                    "heures_matin, heures_am, total_heures, minutes_retard, heures_nuit, " +
                    "motif_ajustement, id_statut_jour, montant_total_indemnites, horaire_continu_jour, version, created_at, updated_at " +
                    ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " +
                    "(SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                    "?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) RETURNING id_pointage";
                try (PreparedStatement psIns = c.prepareStatement(sqlInsert)) {
                    psIns.setInt(1, idEmp);
                    psIns.setObject(2, idPer);
                    psIns.setInt(3, idSite);
                    psIns.setDate(4, datePtg);
                    psIns.setTime(5, em);
                    psIns.setTime(6, sm);
                    psIns.setTime(7, ea);
                    psIns.setTime(8, sa);
                    psIns.setDouble(9, hMatin);
                    psIns.setDouble(10, hAm);
                    psIns.setDouble(11, totalH);
                    psIns.setInt(12, retMins);
                    psIns.setDouble(13, hNuit);
                    psIns.setString(14, motif);
                    psIns.setString(15, codeStatut);
                    psIns.setDouble(16, mntTotalInd);
                    psIns.setBoolean(17, isJourneeContinue);
                    try (ResultSet rsIns = psIns.executeQuery()) {
                        if (rsIns.next()) {
                            int newId = rsIns.getInt(1);
                            syncIndemniteJour(c, newId, repas, depM, depA, mntDepJour, mntRepasJour);
                            if (idPer != null && datePtg != null) {
                                pointageSyntheseService.recalculerSemainePourDate(idPer, datePtg.toLocalDate(), idSite, c);
                            }
                            return true;
                        }
                    }
                    return false;
                } catch (SQLException eIns) {
                    if (eIns.getMessage() != null && (eIns.getMessage().toLowerCase().contains("duplicate") || eIns.getMessage().toLowerCase().contains("unique"))) {
                        throw new IllegalStateException("Conflit : le pointage pour cet employé à cette date existe déjà.");
                    }
                    throw eIns;
                }
            } else {
                if (payload.get("version") == null) {
                    throw new IllegalStateException("Le champ 'version' est obligatoire pour régulariser le pointage existant ID " + idPointage + ".");
                }
                int providedVer = ((Number) payload.get("version")).intValue();
                if (currentVersion != null && providedVer != currentVersion) {
                    throw new IllegalStateException("Conflit de modification concurrente sur le pointage ID " + idPointage
                            + " (version attendue: " + currentVersion + ", version fournie: " + providedVer + "). Veuillez recharger.");
                }

                String sql = "UPDATE pointage_jour SET " +
                             "heure_entree_matin = ?, heure_sortie_matin = ?, heure_entree_am = ?, heure_sortie_am = ?, " +
                             "heures_matin = ?, heures_am = ?, total_heures = ?, minutes_retard = ?, heures_nuit = ?, " +
                             "motif_ajustement = ?, id_statut_jour = (SELECT id_statut_jour FROM ref_statut_pointage_jour WHERE code = ?), " +
                             "montant_total_indemnites = ?, horaire_continu_jour = ?, " +
                             "version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id_pointage = ? AND version = ?";

                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setTime(1, em);
                    ps.setTime(2, sm);
                    ps.setTime(3, ea);
                    ps.setTime(4, sa);
                    ps.setDouble(5, hMatin);
                    ps.setDouble(6, hAm);
                    ps.setDouble(7, totalH);
                    ps.setInt(8, retMins);
                    ps.setDouble(9, hNuit);
                    ps.setString(10, motif);
                    ps.setString(11, codeStatut);
                    ps.setDouble(12, mntTotalInd);
                    ps.setBoolean(13, isJourneeContinue);
                    ps.setInt(14, idPointage);
                    ps.setInt(15, providedVer);
                    int rows = ps.executeUpdate();
                    if (rows == 0) {
                        throw new IllegalStateException("Conflit de modification concurrente sur le pointage ID " + idPointage + ". La ligne a été modifiée par un tiers.");
                    }
                    syncIndemniteJour(c, idPointage, repas, depM, depA, mntDepJour, mntRepasJour);
                    if (idPer != null && datePtg != null) {
                        pointageSyntheseService.recalculerSemainePourDate(idPer, datePtg.toLocalDate(), idSite, c);
                    }
                    return rows > 0;
                }
            }
        }
    }

    private void syncIndemniteJour(Connection c, int idPointage, boolean repas, boolean depM, boolean depA, double mntDepJour, double mntRepasJour) {
        String sqlDyn = "INSERT INTO pointage_indemnite_jour (id_pointage, id_indemnite, fraction, quantite, montant_calcule, accorde, updated_at) " +
                "VALUES (?, (SELECT id_indemnite FROM ref_indemnite WHERE code = ? LIMIT 1), 'JOUR_ENTIER', ?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON CONFLICT (id_pointage, id_indemnite, fraction) DO UPDATE SET " +
                "quantite = EXCLUDED.quantite, montant_calcule = EXCLUDED.montant_calcule, accorde = EXCLUDED.accorde, updated_at = CURRENT_TIMESTAMP";

        try (PreparedStatement psDyn = c.prepareStatement(sqlDyn)) {
            // REPAS
            psDyn.setInt(1, idPointage);
            psDyn.setString(2, "REPAS");
            psDyn.setDouble(3, 1.0);
            psDyn.setDouble(4, mntRepasJour);
            psDyn.setBoolean(5, repas);
            psDyn.executeUpdate();

            // DEPLACEMENT
            double qteDep = (depM ? 0.5 : 0.0) + (depA ? 0.5 : 0.0);
            psDyn.setInt(1, idPointage);
            psDyn.setString(2, "DEPLACEMENT");
            psDyn.setDouble(3, qteDep);
            psDyn.setDouble(4, mntDepJour);
            psDyn.setBoolean(5, depM || depA);
            psDyn.executeUpdate();

            // Mise à jour du montant_total_indemnites dénormalisé
            try (PreparedStatement psUp = c.prepareStatement(
                    "UPDATE pointage_jour SET montant_total_indemnites = COALESCE((SELECT SUM(montant_calcule) FROM pointage_indemnite_jour WHERE id_pointage = ? AND accorde = true), 0) WHERE id_pointage = ?")) {
                psUp.setInt(1, idPointage);
                psUp.setInt(2, idPointage);
                psUp.executeUpdate();
            }
        } catch (Exception ignored) {
        }
    }
}
