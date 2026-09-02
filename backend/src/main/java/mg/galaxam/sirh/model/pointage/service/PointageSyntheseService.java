package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PointageSyntheseService {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine;

    public PointageSyntheseService() {
        this.calendrierEngine = new CalendrierOuvrableEngine();
    }

    public PointageSyntheseService(DataSource dataSource) {
        this.dataSource = dataSource;
        this.calendrierEngine = new CalendrierOuvrableEngine();
    }

    public PointageSyntheseService(DataSource dataSource, mg.galaxam.sirh.model.pointage.engine.CalendrierOuvrableEngine calendrierEngine) {
        this.dataSource = dataSource;
        this.calendrierEngine = calendrierEngine != null ? calendrierEngine : new CalendrierOuvrableEngine();
    }

    /**
     * Résultat de calcul d'un tronçon hebdomadaire avec proratisation et compensation 1:1
     */
    public static class TronconCalculResult {
        public LocalDate debutSemaine;
        public int nbJoursTroncon;
        public double seuilHeuresTroncon;
        public double totalHeuresTravaillees;
        public double heuresRetard;
        public double heuresHsBrutes;
        public double retardCompense;
        public double heuresHsNettes;
        public double heuresDeficit;
        public double heuresHs30;
        public double heuresHs50;

        public TronconCalculResult(
                LocalDate debutSemaine,
                int nbJoursTroncon,
                double seuilHeuresTroncon,
                double totalHeuresTravaillees,
                double heuresRetard,
                double heuresHsBrutes,
                double retardCompense,
                double heuresHsNettes,
                double heuresDeficit,
                double heuresHs30,
                double heuresHs50
        ) {
            this.debutSemaine = debutSemaine;
            this.nbJoursTroncon = nbJoursTroncon;
            this.seuilHeuresTroncon = seuilHeuresTroncon;
            this.totalHeuresTravaillees = totalHeuresTravaillees;
            this.heuresRetard = heuresRetard;
            this.heuresHsBrutes = heuresHsBrutes;
            this.retardCompense = retardCompense;
            this.heuresHsNettes = heuresHsNettes;
            this.heuresDeficit = heuresDeficit;
            this.heuresHs30 = heuresHs30;
            this.heuresHs50 = heuresHs50;
        }
    }

    /**
     * Calcul métier pur pour un tronçon hebdomadaire donné
     * @param debutSemaine Lundi de la semaine ISO
     * @param nbJoursOuvres Nombre de jours ouvrés du tronçon dans la période
     * @param totalHeures Total des heures travaillées sur ce tronçon
     * @param minutesRetard Total des minutes de retard constatées sur ce tronçon
     * @param isCadre Salarié cadre (pas de HS payées, mais surplus compense retards)
     */
    public static TronconCalculResult calculerTroncon(
            LocalDate debutSemaine,
            int nbJoursOuvres,
            double totalHeures,
            double minutesRetard,
            boolean isCadre
    ) {
        return calculerTroncon(debutSemaine, nbJoursOuvres, totalHeures, minutesRetard, isCadre, 8.0, 8.0);
    }

    public static TronconCalculResult calculerTroncon(
            LocalDate debutSemaine,
            int nbJoursOuvres,
            double totalHeures,
            double minutesRetard,
            boolean isCadre,
            double seuilHeuresJour,
            double seuilHsTranche1
    ) {
        double seuilTroncon = nbJoursOuvres * seuilHeuresJour;
        double heuresRetard = Math.round((minutesRetard / 60.0) * 100.0) / 100.0;
        double hsBrutes = Math.max(0.0, Math.round((totalHeures - seuilTroncon) * 100.0) / 100.0);

        // Compensation 1:1 des retards par le surplus d'heures de la semaine
        double retardCompense = Math.min(heuresRetard, hsBrutes);
        double deficitNet = Math.round((heuresRetard - retardCompense) * 100.0) / 100.0;

        // Pour les cadres : forfait contractuel, 0 HS payée, mais compensation active
        double hsNettes = isCadre ? 0.0 : Math.round((hsBrutes - retardCompense) * 100.0) / 100.0;

        // Ventilation HS 30% (jusqu'à seuilHsTranche1) et HS 50% (au-delà)
        double hs30 = Math.min(hsNettes, seuilHsTranche1);
        double hs50 = Math.max(0.0, Math.round((hsNettes - seuilHsTranche1) * 100.0) / 100.0);

        return new TronconCalculResult(
                debutSemaine,
                nbJoursOuvres,
                seuilTroncon,
                totalHeures,
                heuresRetard,
                hsBrutes,
                retardCompense,
                hsNettes,
                deficitNet,
                hs30,
                hs50
        );
    }

    /**
     * Calcule la synthèse de pointage d'une période en logique métier pure Java
     */
    public List<PointageSyntheseMensuelleDTO> getSynthesePeriode(int idPeriode, Integer idSite) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSynthesePeriode(idPeriode, idSite, c);
        }
    }

    public List<PointageSyntheseMensuelleDTO> getSynthesePeriode(int idPeriode, Integer idSite, Connection c) throws SQLException {
        List<PointageSyntheseMensuelleDTO> list = new ArrayList<>();
        if (idPeriode <= 0) return list;

        // 0. Charger l'ensemble des paramètres globaux dynamiques
        Map<String, String> params = ParametreGlobal.loadActiveMap(c);
        double seuilHeuresJour = ParametreGlobal.getDouble(params, "SEUIL_HEURES_JOUR", 8.0);
        double seuilHsTranche1 = ParametreGlobal.getDouble(params, "SEUIL_HS_TRANCHE_1", 8.0);
        double reductionDeplactTmm = ParametreGlobal.getDouble(params, "REDUCTION_DEPLACT_REPAS_MIDI", 1000.0);

        // 1. Charger les dates de la période de paie
        LocalDate dateDebut = null;
        LocalDate dateFin = null;
        String codePeriode = null;
        String libellePeriode = null;
        String statutPeriode = null;

        String sqlPeriode = "SELECT p.code, p.libelle, p.date_debut, p.date_fin, s.code AS statut_code " +
                            "FROM periode_paie p " +
                            "JOIN ref_statut_periode_paie s ON p.id_statut_periode = s.id_statut_periode " +
                            "WHERE p.id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebut = rsP.getDate("date_debut").toLocalDate();
                    dateFin = rsP.getDate("date_fin").toLocalDate();
                    codePeriode = rsP.getString("code");
                    libellePeriode = rsP.getString("libelle");
                    statutPeriode = rsP.getString("statut_code");
                }
            }
        }

        if (dateDebut == null || dateFin == null) {
            return list;
        }

        // Calcul des jours ouvrables réels de la période via le moteur centralisé
        int nbJoursOuvrablesPeriode = calendrierEngine.compterJoursOuvrables(dateDebut, dateFin, idSite, c);
        if (nbJoursOuvrablesPeriode == 0) nbJoursOuvrablesPeriode = 22;

        // Charger les indemnités de déplacement/transport configurées par site (site_indemnite_config)
        Map<Integer, Double> siteDeplacementRefMap = new HashMap<>();
        String sqlSiteIndConfig = "SELECT c.id_site, c.montant_reference FROM site_indemnite_config c " +
                                  "JOIN ref_indemnite i ON c.id_indemnite = i.id_indemnite " +
                                  "WHERE i.code IN ('DEPLACEMENT', 'TRANSPORT') AND c.est_actif = TRUE";
        try (PreparedStatement psSIC = c.prepareStatement(sqlSiteIndConfig);
             ResultSet rsSIC = psSIC.executeQuery()) {
            while (rsSIC.next()) {
                siteDeplacementRefMap.put(rsSIC.getInt("id_site"), rsSIC.getDouble("montant_reference"));
            }
        } catch (Exception ignored) {}

        // 2. Charger les employés éligibles avec l'affectation active durant cette période (la plus récente si mutation en cours de période)
        StringBuilder sqlEmp = new StringBuilder(
            "SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "WHERE 1=1 "
        );

        if (idSite != null && idSite > 0) {
            sqlEmp.append("AND a.id_site = ? ");
        }
        sqlEmp.append("ORDER BY e.id_employe, a.date_debut DESC");

        // 3. Traiter chaque employé
        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            psE.setDate(pIdx++, Date.valueOf(dateFin));
            psE.setDate(pIdx++, Date.valueOf(dateDebut));
            if (idSite != null && idSite > 0) {
                psE.setInt(pIdx++, idSite);
            }
            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    boolean estAutonome = rsE.getBoolean("est_autonome");
                    String codeCat = rsE.getString("code_categorie");
                    boolean isCadre = estAutonome || "C".equalsIgnoreCase(codeCat) || "HC".equalsIgnoreCase(codeCat);

                    PointageSyntheseMensuelleDTO dto = new PointageSyntheseMensuelleDTO();
                    dto.setIdEmploye(idEmp);
                    dto.setMatricule(rsE.getString("matricule"));
                    dto.setNom(rsE.getString("nom"));
                    dto.setPrenom(rsE.getString("prenom"));
                    dto.setPhotoUrl(rsE.getString("photo_url"));
                    dto.setIdSite(rsE.getInt("id_site"));
                    dto.setSiteNom(rsE.getString("site_nom"));
                    dto.setIdPeriodePaie(idPeriode);
                    dto.setCodePeriode(codePeriode);
                    dto.setLibellePeriode(libellePeriode);
                    dto.setStatutPeriode(statutPeriode);
                    dto.setDispensePointage(estAutonome);
                    dto.setEstCadre(isCadre);
                    dto.setNbJoursOuvrables(nbJoursOuvrablesPeriode);

                    // Charger tous les pointages quotidiens de cet employé sur la période
                    String sqlPtg = "SELECT p.date_pointage, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                                    "p.deplacement_matin, p.deplacement_am, p.indemnite_deplacement_recue, " +
                                    "p.indemnite_repas_recue, p.montant_repas, st.code AS statut_code " +
                                    "FROM pointage_jour p " +
                                    "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                                    "WHERE p.id_employe = ? AND p.id_periode_paie = ? " +
                                    "ORDER BY p.date_pointage";

                    int presents = 0;
                    int conges = 0;
                    int absences = 0;
                    int feriesChomes = 0;
                    int feriesTravailles = 0;
                    int repasCount = 0;
                    double montantRepasTotal = 0.0;
                    double totalTrajets = 0.0;
                    double cumulHeuresTravaillees = 0.0;
                    double cumulHeuresNuit = 0.0;
                    int totalMinsRetard = 0;

                    // Regroupement par semaine civile ISO (Lundi du tronçon)
                    Map<LocalDate, List<Map<String, Object>>> parSemaine = new LinkedHashMap<>();

                    try (PreparedStatement psPtg = c.prepareStatement(sqlPtg)) {
                        psPtg.setInt(1, idEmp);
                        psPtg.setInt(2, idPeriode);
                        try (ResultSet rsPtg = psPtg.executeQuery()) {
                            while (rsPtg.next()) {
                                LocalDate dPointage = rsPtg.getDate("date_pointage").toLocalDate();
                                boolean isWeekend = (dPointage.getDayOfWeek() == DayOfWeek.SATURDAY || dPointage.getDayOfWeek() == DayOfWeek.SUNDAY);
                                String stCode = rsPtg.getString("statut_code");
                                double th = rsPtg.getDouble("total_heures");

                                if (("DISPENSE_POINTAGE".equals(stCode) || estAutonome)) {
                                    if (isWeekend && th <= 0.0) {
                                        th = 0.0;
                                        stCode = "REPOS";
                                    } else if (!isWeekend && th <= 0.0) {
                                        th = seuilHeuresJour;
                                    }
                                }

                                double hn = rsPtg.getDouble("heures_nuit");
                                int ret = rsPtg.getInt("minutes_retard");
                                boolean depM = rsPtg.getBoolean("deplacement_matin");
                                boolean depA = rsPtg.getBoolean("deplacement_am");
                                boolean repas = rsPtg.getBoolean("indemnite_repas_recue");
                                double mntRepas = rsPtg.getDouble("montant_repas");

                                if ("PRESENT".equals(stCode) || "DISPENSE_POINTAGE".equals(stCode)) presents++;
                                else if ("CONGE".equals(stCode)) conges++;
                                else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode)) absences++;
                                else if ("FERIE_CHOME".equals(stCode)) feriesChomes++;
                                else if ("FERIE_TRAVAILLE".equals(stCode)) { presents++; feriesTravailles++; }

                                cumulHeuresTravaillees += th;
                                cumulHeuresNuit += hn;
                                totalMinsRetard += (estAutonome ? 0 : ret);

                                if (repas || (estAutonome && !isWeekend)) {
                                    repasCount++;
                                    montantRepasTotal += (mntRepas > 0 ? mntRepas : 2000.0);
                                }

                                if (depM || (estAutonome && !isWeekend)) totalTrajets += 0.5;
                                if (depA || (estAutonome && !isWeekend)) totalTrajets += 0.5;

                                LocalDate lundiSemaine = dPointage.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                                parSemaine.computeIfAbsent(lundiSemaine, k -> new ArrayList<>());

                                Map<String, Object> dayRow = new HashMap<>();
                                dayRow.put("date", dPointage);
                                dayRow.put("totalHeures", th);
                                dayRow.put("retardMins", estAutonome ? 0 : ret);
                                parSemaine.get(lundiSemaine).add(dayRow);
                            }
                        }
                    }

                    // Si salarié autonome et aucune ligne de pointage enregistrée : base normale automatique
                    if (estAutonome && presents == 0 && conges == 0 && absences == 0) {
                        presents = nbJoursOuvrablesPeriode;
                        cumulHeuresTravaillees = nbJoursOuvrablesPeriode * seuilHeuresJour;
                    }

                    // Calcul de la compensation hebdomadaire par tronçon
                    double totalHs30 = 0.0;
                    double totalHs50 = 0.0;
                    double totalHeuresDeficit = 0.0;

                    for (Map.Entry<LocalDate, List<Map<String, Object>>> entry : parSemaine.entrySet()) {
                        LocalDate lundi = entry.getKey();
                        List<Map<String, Object>> jours = entry.getValue();

                        // Compter les jours ouvrés du tronçon dans la période
                        int joursOuvresTroncon = 0;
                        for (int i = 0; i < 7; i++) {
                            LocalDate cur = lundi.plusDays(i);
                            if (!cur.isBefore(dateDebut) && !cur.isAfter(dateFin)) {
                                if (cur.getDayOfWeek() != DayOfWeek.SATURDAY && cur.getDayOfWeek() != DayOfWeek.SUNDAY) {
                                    joursOuvresTroncon++;
                                }
                            }
                        }
                        if (joursOuvresTroncon == 0) joursOuvresTroncon = 1;

                        double hTotSemaine = 0.0;
                        double retMinsSemaine = 0.0;
                        for (Map<String, Object> j : jours) {
                            hTotSemaine += (double) j.get("totalHeures");
                            retMinsSemaine += ((Number) j.get("retardMins")).doubleValue();
                        }

                        TronconCalculResult res = calculerTroncon(lundi, joursOuvresTroncon, hTotSemaine, retMinsSemaine, isCadre, seuilHeuresJour, seuilHsTranche1);
                        totalHs30 += res.heuresHs30;
                        totalHs50 += res.heuresHs50;
                        totalHeuresDeficit += res.heuresDeficit;
                    }

                    dto.setNbJoursPresents(presents);
                    dto.setNbJoursConge(conges);
                    dto.setNbJoursAbsence(absences);
                    dto.setNbJoursFerieChome(feriesChomes);
                    dto.setNbJoursFerieTravaille(feriesTravailles);
                    dto.setTotalHeuresTravaillees(Math.round(cumulHeuresTravaillees * 100.0) / 100.0);
                    dto.setHeuresHs30(Math.round(totalHs30 * 100.0) / 100.0);
                    dto.setHeuresHs50(Math.round(totalHs50 * 100.0) / 100.0);
                    dto.setHeuresNuitTotal(Math.round(cumulHeuresNuit * 100.0) / 100.0);
                    dto.setHeuresAbsenceRetenues(Math.round(totalHeuresDeficit * 100.0) / 100.0);
                    dto.setTotalMinutesRetard(totalMinsRetard);
                    dto.setNbJoursRepas(repasCount);
                    dto.setMontantRepas(montantRepasTotal);
                    dto.setNbJoursDeplacement((int) Math.round(totalTrajets));

                    int empSiteId = rsE.getInt("id_site");
                    String siteCode = rsE.getString("site_code");
                    String siteNom = rsE.getString("site_nom");
                    boolean isSiteTmm = (siteCode != null && "TMM".equalsIgnoreCase(siteCode.trim())) ||
                                        (siteNom != null && siteNom.toLowerCase().contains("tamatave"));

                    double refDeplacementSite = siteDeplacementRefMap.getOrDefault(empSiteId, isSiteTmm ? 53200.0 : 31200.0);
                    double tarifJourDeplacement = calendrierEngine.calculerProrataJournalier(refDeplacementSite, nbJoursOuvrablesPeriode);
                    double brutDeplacement = totalTrajets * tarifJourDeplacement;
                    double mntDeplacement = Math.max(0.0, brutDeplacement - (isSiteTmm ? repasCount * reductionDeplactTmm : 0.0));
                    dto.setMontantDeplacement(Math.round(mntDeplacement * 100.0) / 100.0);

                    list.add(dto);
                }
            }
        }

        list.sort(Comparator.comparing(PointageSyntheseMensuelleDTO::getNom, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PointageSyntheseMensuelleDTO::getPrenom, Comparator.nullsLast(String::compareToIgnoreCase)));

        return list;
    }

    /**
     * Gèle les tronçons hebdomadaires et le cumul mensuel dans les tables immuables
     */
    public void gelerPeriode(int idPeriode, Integer userId, Connection c) throws SQLException {
        // Calculer la synthèse via notre logique métier pure
        List<PointageSyntheseMensuelleDTO> synthese = getSynthesePeriode(idPeriode, null, c);

        String sqlCumul = "INSERT INTO pointage_cumul_periode (" +
                          "id_employe, id_periode_paie, nb_jours_ouvrables, nb_jours_presents, " +
                          "nb_jours_absence, nb_jours_conge, nb_jours_ferie_chome, nb_jours_ferie_travaille, " +
                          "total_heures_travaillees, heures_hs_30, heures_hs_50, heures_nuit_total, " +
                          "heures_absence_retenues, nb_jours_deplacement, montant_deplacement, " +
                          "nb_jours_repas, montant_repas, gele_par, gele_at" +
                          ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP) " +
                          "ON CONFLICT (id_employe, id_periode_paie) DO UPDATE SET " +
                          "nb_jours_ouvrables = EXCLUDED.nb_jours_ouvrables, " +
                          "nb_jours_presents = EXCLUDED.nb_jours_presents, " +
                          "nb_jours_absence = EXCLUDED.nb_jours_absence, " +
                          "nb_jours_conge = EXCLUDED.nb_jours_conge, " +
                          "nb_jours_ferie_chome = EXCLUDED.nb_jours_ferie_chome, " +
                          "nb_jours_ferie_travaille = EXCLUDED.nb_jours_ferie_travaille, " +
                          "total_heures_travaillees = EXCLUDED.total_heures_travaillees, " +
                          "heures_hs_30 = EXCLUDED.heures_hs_30, " +
                          "heures_hs_50 = EXCLUDED.heures_hs_50, " +
                          "heures_nuit_total = EXCLUDED.heures_nuit_total, " +
                          "heures_absence_retenues = EXCLUDED.heures_absence_retenues, " +
                          "nb_jours_deplacement = EXCLUDED.nb_jours_deplacement, " +
                          "montant_deplacement = EXCLUDED.montant_deplacement, " +
                          "nb_jours_repas = EXCLUDED.nb_jours_repas, " +
                          "montant_repas = EXCLUDED.montant_repas, " +
                          "gele_par = EXCLUDED.gele_par, " +
                          "gele_at = CURRENT_TIMESTAMP";

        try (PreparedStatement ps = c.prepareStatement(sqlCumul)) {
            for (PointageSyntheseMensuelleDTO dto : synthese) {
                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriode);
                ps.setInt(3, dto.getNbJoursOuvrables());
                ps.setInt(4, dto.getNbJoursPresents());
                ps.setInt(5, dto.getNbJoursAbsence());
                ps.setInt(6, dto.getNbJoursConge());
                ps.setInt(7, dto.getNbJoursFerieChome());
                ps.setInt(8, dto.getNbJoursFerieTravaille());
                ps.setDouble(9, dto.getTotalHeuresTravaillees());
                ps.setDouble(10, dto.getHeuresHs30());
                ps.setDouble(11, dto.getHeuresHs50());
                ps.setDouble(12, dto.getHeuresNuitTotal());
                ps.setDouble(13, dto.getHeuresAbsenceRetenues());
                ps.setDouble(14, dto.getNbJoursDeplacement());
                ps.setDouble(15, dto.getMontantDeplacement());
                ps.setInt(16, dto.getNbJoursRepas());
                ps.setDouble(17, dto.getMontantRepas());
                if (userId != null) ps.setInt(18, userId); else ps.setNull(18, java.sql.Types.INTEGER);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Dégèle les données lors de la réouverture exceptionnelle d'une période
     */
    public void degelerPeriode(int idPeriode, Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_cumul_periode WHERE id_periode_paie = ?");
             PreparedStatement psSem = c.prepareStatement("DELETE FROM pointage_semaine_calcule WHERE id_periode_paie = ?")) {
            ps.setInt(1, idPeriode);
            ps.executeUpdate();
            psSem.setInt(1, idPeriode);
            psSem.executeUpdate();
        }
    }

    /**
     * Découpe autoritaire de la période de paie en ses semaines réelles avec statut de validation
     */
    public List<Map<String, Object>> getSemainesPeriode(int idPeriode, Integer idSite) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSemainesPeriode(idPeriode, idSite, c);
        }
    }

    public List<Map<String, Object>> getSemainesPeriode(int idPeriode, Integer idSite, Connection c) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        if (idPeriode <= 0) return result;

        LocalDate dateDebut = null;
        LocalDate dateFin = null;
        String sqlPeriode = "SELECT date_debut, date_fin FROM periode_paie WHERE id_periode_paie = ?";
        try (PreparedStatement psP = c.prepareStatement(sqlPeriode)) {
            psP.setInt(1, idPeriode);
            try (ResultSet rsP = psP.executeQuery()) {
                if (rsP.next()) {
                    dateDebut = rsP.getDate("date_debut").toLocalDate();
                    dateFin = rsP.getDate("date_fin").toLocalDate();
                }
            }
        }

        if (dateDebut == null || dateFin == null) return result;

        // Découpage en tronçons du Lundi au Dimanche
        LocalDate cur = dateDebut;
        int semNum = 1;
        while (!cur.isAfter(dateFin)) {
            LocalDate debutSemaine = cur.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate finSemaine = cur.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

            // Si le début du tronçon tombe un Samedi ou Dimanche et qu'il y a une semaine suivante dans la période,
            // on rattache ce weekend directement au dimanche de la semaine suivante (évite une semaine vide de 0 jour ouvré)
            if ((cur.getDayOfWeek() == DayOfWeek.SATURDAY || cur.getDayOfWeek() == DayOfWeek.SUNDAY) && finSemaine.plusDays(7).isBefore(dateFin.plusDays(7))) {
                finSemaine = finSemaine.plusDays(7);
            }

            LocalDate dateDebutTroncon = cur.isBefore(debutSemaine) ? debutSemaine : cur;
            if (dateDebutTroncon.isBefore(dateDebut)) dateDebutTroncon = dateDebut;

            LocalDate dateFinTroncon = finSemaine.isAfter(dateFin) ? dateFin : finSemaine;

            // Calcul des jours ouvrés du tronçon
            int nbOuvrables = 0;
            for (LocalDate d = dateDebutTroncon; !d.isAfter(dateFinTroncon); d = d.plusDays(1)) {
                if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                    nbOuvrables++;
                }
            }

            double seuilHeures = nbOuvrables * 8.0;

            // Statistiques des pointages réels saisis pour ce tronçon
            String sqlStats = "SELECT COUNT(*) AS total_points, " +
                              "COALESCE(SUM(p.total_heures), 0) AS somme_heures, " +
                              "COUNT(CASE WHEN st.code IN ('ANOMALIE_SAISIE', 'ABSENCE_NON_JUSTIFIEE') THEN 1 END) AS nb_anomalies " +
                              "FROM pointage_jour p " +
                              "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                              "WHERE p.id_periode_paie = ? AND p.date_pointage BETWEEN ? AND ? " +
                              (idSite != null && idSite > 0 ? "AND p.id_site = ? " : "");

            double heuresSaisies = 0.0;
            int totalPointages = 0;
            int nbAnomalies = 0;

            try (PreparedStatement psStats = c.prepareStatement(sqlStats)) {
                psStats.setInt(1, idPeriode);
                psStats.setDate(2, Date.valueOf(dateDebutTroncon));
                psStats.setDate(3, Date.valueOf(dateFinTroncon));
                if (idSite != null && idSite > 0) {
                    psStats.setInt(4, idSite);
                }
                try (ResultSet rsS = psStats.executeQuery()) {
                    if (rsS.next()) {
                        totalPointages = rsS.getInt("total_points");
                        heuresSaisies = rsS.getDouble("somme_heures");
                        nbAnomalies = rsS.getInt("nb_anomalies");
                    }
                }
            }

            // Compter les salariés actifs non dispensés pour calculer les pointages attendus
            int nbSalariesActifs = 0;
            String sqlNbEmp = "SELECT COUNT(DISTINCT e.id_employe) FROM employe e " +
                              "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
                              "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
                              "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
                              "WHERE COALESCE(f.dispense_pointage, FALSE) = FALSE " +
                              (idSite != null && idSite > 0 ? "AND a.id_site = ? " : "");
            try (PreparedStatement psEmp = c.prepareStatement(sqlNbEmp)) {
                int empIdx = 1;
                psEmp.setDate(empIdx++, Date.valueOf(dateFinTroncon));
                psEmp.setDate(empIdx++, Date.valueOf(dateDebutTroncon));
                if (idSite != null && idSite > 0) psEmp.setInt(empIdx++, idSite);
                try (ResultSet rsEmp = psEmp.executeQuery()) {
                    if (rsEmp.next()) nbSalariesActifs = rsEmp.getInt(1);
                }
            }

            int pointagesAttendus = nbSalariesActifs * nbOuvrables;

            // Vérifier si cette semaine est déjà validée / calculée dans pointage_semaine_calcule
            boolean estValidee = false;
            String sqlCheckValidee = "SELECT COUNT(*) FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND debut_semaine = ?";
            try (PreparedStatement psVal = c.prepareStatement(sqlCheckValidee)) {
                psVal.setInt(1, idPeriode);
                psVal.setDate(2, Date.valueOf(dateDebutTroncon));
                try (ResultSet rsVal = psVal.executeQuery()) {
                    if (rsVal.next() && rsVal.getInt(1) > 0) {
                        estValidee = true;
                    }
                }
            } catch (Exception ignored) {}

            Map<String, Object> semMap = new HashMap<>();
            semMap.put("id", semNum);
            semMap.put("numeroSemaine", semNum);
            semMap.put("title", "Semaine " + semNum);
            semMap.put("dateDebut", dateDebutTroncon.toString());
            semMap.put("dateFin", dateFinTroncon.toString());

            // Libellé court des dates
            String libelleDates = dateDebutTroncon.getDayOfMonth() + " " +
                    dateDebutTroncon.getMonth().toString().substring(0, 3).toLowerCase() + " – " +
                    dateFinTroncon.getDayOfMonth() + " " +
                    dateFinTroncon.getMonth().toString().substring(0, 3).toLowerCase();
            semMap.put("dates", libelleDates);
            semMap.put("nbJoursOuvrables", nbOuvrables);
            semMap.put("seuilHeures", seuilHeures);
            semMap.put("totalHeures", Math.round(heuresSaisies * 100.0) / 100.0);
            semMap.put("totalPointages", totalPointages);
            semMap.put("nbAnomalies", nbAnomalies);
            semMap.put("pointagesAttendus", pointagesAttendus);
            semMap.put("estValidee", estValidee);

            // Détermination du statut de la semaine
            boolean isReady = false;
            String statut;
            String desc;

            if (estValidee) {
                statut = "Validée";
                desc = "Heures supplémentaires et compensations consolidées.";
                isReady = false;
            } else if (nbAnomalies > 0) {
                statut = "Anomalies à corriger";
                desc = nbAnomalies + " anomalie(s) de saisie à régulariser.";
                isReady = false;
            } else if (totalPointages == 0) {
                statut = "Saisie en cours";
                desc = "Pointages quotidiens non encore saisis.";
                isReady = false;
            } else if (totalPointages < pointagesAttendus) {
                statut = "Saisie en cours";
                desc = "Saisie partielle (" + totalPointages + "/" + pointagesAttendus + " pointages réalisés).";
                isReady = false;
            } else if (totalPointages >= pointagesAttendus && pointagesAttendus > 0) {
                statut = "Prête à valider";
                desc = "Saisie complète, aucune anomalie.";
                isReady = true;
            } else {
                statut = "Saisie en cours";
                desc = "Pointages quotidiens en cours de saisie.";
                isReady = false;
            }

            semMap.put("statut", statut);
            semMap.put("desc", desc);
            semMap.put("ready", isReady);

            result.add(semMap);

            semNum++;
            cur = dateFinTroncon.plusDays(1);
        }

        return result;
    }

    /**
     * Calcule le détail nominatif des salariés sur un tronçon hebdomadaire précis
     */
    public List<PointageSyntheseMensuelleDTO> getSyntheseSemaineDetail(
            int idPeriode,
            LocalDate dateDebutTroncon,
            LocalDate dateFinTroncon,
            Integer idSite
    ) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            return getSyntheseSemaineDetail(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, c);
        }
    }

    public List<PointageSyntheseMensuelleDTO> getSyntheseSemaineDetail(
            int idPeriode,
            LocalDate dateDebutTroncon,
            LocalDate dateFinTroncon,
            Integer idSite,
            Connection c
    ) throws SQLException {
        List<PointageSyntheseMensuelleDTO> list = new ArrayList<>();
        if (idPeriode <= 0 || dateDebutTroncon == null || dateFinTroncon == null) return list;

        Map<String, String> params = ParametreGlobal.loadActiveMap(c);
        double seuilHeuresJour = ParametreGlobal.getDouble(params, "SEUIL_HEURES_JOUR", 8.0);
        double seuilHsTranche1 = ParametreGlobal.getDouble(params, "SEUIL_HS_TRANCHE_1", 8.0);

        // Compter les jours ouvrés du tronçon
        int nbOuvrables = 0;
        for (LocalDate d = dateDebutTroncon; !d.isAfter(dateFinTroncon); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                nbOuvrables++;
            }
        }

        StringBuilder sqlEmp = new StringBuilder(
            "SELECT DISTINCT ON (e.id_employe) e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe " +
            "    AND a.date_debut <= ? AND (a.date_fin IS NULL OR a.date_fin >= ?) " +
            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "WHERE 1=1 "
        );

        if (idSite != null && idSite > 0) {
            sqlEmp.append("AND a.id_site = ? ");
        }
        sqlEmp.append("ORDER BY e.id_employe, a.date_debut DESC");

        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            int pIdx = 1;
            psE.setDate(pIdx++, Date.valueOf(dateFinTroncon));
            psE.setDate(pIdx++, Date.valueOf(dateDebutTroncon));
            if (idSite != null && idSite > 0) {
                psE.setInt(pIdx++, idSite);
            }
            try (ResultSet rsE = psE.executeQuery()) {
                while (rsE.next()) {
                    int idEmp = rsE.getInt("id_employe");
                    boolean estAutonome = rsE.getBoolean("est_autonome");
                    String codeCat = rsE.getString("code_categorie");
                    boolean isCadre = estAutonome || "C".equalsIgnoreCase(codeCat) || "HC".equalsIgnoreCase(codeCat);

                    PointageSyntheseMensuelleDTO dto = new PointageSyntheseMensuelleDTO();
                    dto.setIdEmploye(idEmp);
                    dto.setMatricule(rsE.getString("matricule"));
                    dto.setNom(rsE.getString("nom"));
                    dto.setPrenom(rsE.getString("prenom"));
                    dto.setPhotoUrl(rsE.getString("photo_url"));
                    dto.setIdSite(rsE.getInt("id_site"));
                    dto.setSiteNom(rsE.getString("site_nom"));
                    dto.setIdPeriodePaie(idPeriode);
                    dto.setDispensePointage(estAutonome);
                    dto.setEstCadre(isCadre);
                    dto.setNbJoursOuvrables(nbOuvrables);

                    String sqlPtg = "SELECT p.date_pointage, p.total_heures, p.heures_nuit, p.minutes_retard, " +
                                    "p.deplacement_matin, p.deplacement_am, p.indemnite_deplacement_recue, " +
                                    "p.indemnite_repas_recue, p.montant_repas, st.code AS statut_code " +
                                    "FROM pointage_jour p " +
                                    "JOIN ref_statut_pointage_jour st ON p.id_statut_jour = st.id_statut_jour " +
                                    "WHERE p.id_employe = ? AND p.id_periode_paie = ? AND p.date_pointage BETWEEN ? AND ? " +
                                    "ORDER BY p.date_pointage";

                    int presents = 0;
                    int conges = 0;
                    int absences = 0;
                    int feriesChomes = 0;
                    int repasCount = 0;
                    double montantRepasTotal = 0.0;
                    double totalTrajets = 0.0;
                    double cumulHeures = 0.0;
                    double cumulNuit = 0.0;
                    int totalMinsRetard = 0;
                    int nbAnomalies = 0;

                    try (PreparedStatement psPtg = c.prepareStatement(sqlPtg)) {
                        psPtg.setInt(1, idEmp);
                        psPtg.setInt(2, idPeriode);
                        psPtg.setDate(3, Date.valueOf(dateDebutTroncon));
                        psPtg.setDate(4, Date.valueOf(dateFinTroncon));
                        try (ResultSet rsPtg = psPtg.executeQuery()) {
                            while (rsPtg.next()) {
                                LocalDate dtPtg = rsPtg.getDate("date_pointage").toLocalDate();
                                boolean isWeekend = (dtPtg.getDayOfWeek() == DayOfWeek.SATURDAY || dtPtg.getDayOfWeek() == DayOfWeek.SUNDAY);
                                String stCode = rsPtg.getString("statut_code");
                                double th = rsPtg.getDouble("total_heures");

                                if (("DISPENSE_POINTAGE".equals(stCode) || estAutonome)) {
                                    if (isWeekend && th <= 0.0) {
                                        th = 0.0;
                                        stCode = "REPOS";
                                    } else if (!isWeekend && th <= 0.0) {
                                        th = seuilHeuresJour;
                                    }
                                }

                                double hn = rsPtg.getDouble("heures_nuit");
                                int ret = rsPtg.getInt("minutes_retard");
                                boolean depM = rsPtg.getBoolean("deplacement_matin");
                                boolean depA = rsPtg.getBoolean("deplacement_am");
                                boolean repas = rsPtg.getBoolean("indemnite_repas_recue");
                                double mntRepas = rsPtg.getDouble("montant_repas");

                                if ("PRESENT".equals(stCode) || "DISPENSE_POINTAGE".equals(stCode) || "SAMEDI_TRAVAILLE".equals(stCode) || "DIMANCHE_TRAVAILLE".equals(stCode)) presents++;
                                else if ("CONGE".equals(stCode)) conges++;
                                else if ("ABSENCE_NON_JUSTIFIEE".equals(stCode)) absences++;
                                else if ("FERIE_CHOME".equals(stCode)) feriesChomes++;
                                else if ("ANOMALIE_SAISIE".equals(stCode)) nbAnomalies++;

                                cumulHeures += th;
                                cumulNuit += hn;
                                totalMinsRetard += (estAutonome ? 0 : ret);

                                if (repas || (estAutonome && !isWeekend)) {
                                    repasCount++;
                                    montantRepasTotal += (mntRepas > 0 ? mntRepas : 2000.0);
                                }
                                if (depM || (estAutonome && !isWeekend)) totalTrajets += 0.5;
                                if (depA || (estAutonome && !isWeekend)) totalTrajets += 0.5;
                            }
                        }
                    }

                    // Si salarié autonome et aucune ligne de pointage enregistrée : base normale automatique
                    if (estAutonome && presents == 0 && conges == 0 && absences == 0) {
                        presents = nbOuvrables;
                        cumulHeures = nbOuvrables * seuilHeuresJour;
                    }

                    TronconCalculResult res = calculerTroncon(
                        dateDebutTroncon,
                        nbOuvrables,
                        cumulHeures,
                        totalMinsRetard,
                        isCadre,
                        seuilHeuresJour,
                        seuilHsTranche1
                    );

                    dto.setNbJoursPresents(presents);
                    dto.setNbJoursConge(conges);
                    dto.setNbJoursAbsence(absences);
                    dto.setNbJoursFerieChome(feriesChomes);
                    dto.setHeuresNormales(res.seuilHeuresTroncon);
                    dto.setTotalHeuresTravaillees(Math.round(cumulHeures * 100.0) / 100.0);
                    dto.setSurplusBrut(Math.round(res.heuresHsBrutes * 100.0) / 100.0);
                    dto.setHeuresHs30(Math.round(res.heuresHs30 * 100.0) / 100.0);
                    dto.setHeuresHs50(Math.round(res.heuresHs50 * 100.0) / 100.0);
                    dto.setHeuresNuitTotal(Math.round(cumulNuit * 100.0) / 100.0);
                    dto.setRetardCompense(Math.round(res.retardCompense * 100.0) / 100.0);
                    dto.setHeuresAbsenceRetenues(Math.round(res.heuresDeficit * 100.0) / 100.0);
                    dto.setTotalMinutesRetard(totalMinsRetard);
                    dto.setNbJoursRepas(repasCount);
                    dto.setMontantRepas(montantRepasTotal);
                    dto.setNbJoursDeplacement((int) Math.round(totalTrajets));
                    dto.setNbAnomaliesNonResolues(nbAnomalies);

                    list.add(dto);
                }
            }
        }

        list.sort(Comparator.comparing(PointageSyntheseMensuelleDTO::getNom, Comparator.nullsLast(String::compareToIgnoreCase))
                .thenComparing(PointageSyntheseMensuelleDTO::getPrenom, Comparator.nullsLast(String::compareToIgnoreCase)));

        return list;
    }

    /**
     * Valide et fige une semaine de pointage dans pointage_semaine_calcule
     */
    public void validerSemaine(int idPeriode, LocalDate dateDebutTroncon, LocalDate dateFinTroncon, Integer idSite, Integer currentUserId) throws SQLException {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                validerSemaine(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, currentUserId, c);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void validerSemaine(int idPeriode, LocalDate dateDebutTroncon, LocalDate dateFinTroncon, Integer idSite, Integer currentUserId, Connection c) throws SQLException {
        if (idPeriode <= 0 || dateDebutTroncon == null || dateFinTroncon == null) return;

        Map<String, String> params = ParametreGlobal.loadActiveMap(c);
        double majorationHs30 = ParametreGlobal.getDouble(params, "MAJORATION_HS_30", 30.0);
        double majorationHs50 = ParametreGlobal.getDouble(params, "MAJORATION_HS_50", 50.0);
        double majorationNuit = ParametreGlobal.getDouble(params, "MAJORATION_NUIT", 30.0);
        double majorationDimanche = ParametreGlobal.getDouble(params, "MAJORATION_DIMANCHE", 40.0);
        double majorationFerie = ParametreGlobal.getDouble(params, "MAJORATION_FERIE", 100.0);

        List<PointageSyntheseMensuelleDTO> dtos = getSyntheseSemaineDetail(idPeriode, dateDebutTroncon, dateFinTroncon, idSite, c);

        String sqlUpsert = """
            INSERT INTO pointage_semaine_calcule (
                id_employe, id_periode_paie, debut_semaine, nb_jours_troncon, seuil_heures_troncon,
                total_heures_travaillees, heures_retard, heures_hs_brutes, heures_hs_nettes,
                heures_deficit, taux_hs_30_applique, taux_hs_50_applique, taux_nuit_applique,
                taux_dimanche_applique, taux_ferie_applique, heures_hs_30, heures_hs_50, gele_par, gele_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id_employe, id_periode_paie, debut_semaine)
            DO UPDATE SET
                nb_jours_troncon = EXCLUDED.nb_jours_troncon,
                seuil_heures_troncon = EXCLUDED.seuil_heures_troncon,
                total_heures_travaillees = EXCLUDED.total_heures_travaillees,
                heures_retard = EXCLUDED.heures_retard,
                heures_hs_brutes = EXCLUDED.heures_hs_brutes,
                heures_hs_nettes = EXCLUDED.heures_hs_nettes,
                heures_deficit = EXCLUDED.heures_deficit,
                taux_hs_30_applique = EXCLUDED.taux_hs_30_applique,
                taux_hs_50_applique = EXCLUDED.taux_hs_50_applique,
                taux_nuit_applique = EXCLUDED.taux_nuit_applique,
                taux_dimanche_applique = EXCLUDED.taux_dimanche_applique,
                taux_ferie_applique = EXCLUDED.taux_ferie_applique,
                heures_hs_30 = EXCLUDED.heures_hs_30,
                heures_hs_50 = EXCLUDED.heures_hs_50,
                gele_par = EXCLUDED.gele_par,
                gele_at = CURRENT_TIMESTAMP
        """;

        try (PreparedStatement ps = c.prepareStatement(sqlUpsert)) {
            for (PointageSyntheseMensuelleDTO dto : dtos) {
                double hsNettes = dto.isEstCadre() ? 0.0 : (dto.getHeuresHs30() + dto.getHeuresHs50());

                ps.setInt(1, dto.getIdEmploye());
                ps.setInt(2, idPeriode);
                ps.setDate(3, Date.valueOf(dateDebutTroncon));
                ps.setInt(4, dto.getNbJoursOuvrables());
                ps.setDouble(5, dto.getHeuresNormales());
                ps.setDouble(6, dto.getTotalHeuresTravaillees());
                ps.setDouble(7, dto.getTotalMinutesRetard() / 60.0);
                ps.setDouble(8, dto.getSurplusBrut());
                ps.setDouble(9, hsNettes);
                ps.setDouble(10, dto.getHeuresAbsenceRetenues());
                ps.setDouble(11, majorationHs30);
                ps.setDouble(12, majorationHs50);
                ps.setDouble(13, majorationNuit);
                ps.setDouble(14, majorationDimanche);
                ps.setDouble(15, majorationFerie);
                ps.setDouble(16, dto.getHeuresHs30());
                ps.setDouble(17, dto.getHeuresHs50());
                if (currentUserId != null) ps.setInt(18, currentUserId); else ps.setNull(18, java.sql.Types.INTEGER);

                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /**
     * Dévalide / déverrouille une semaine
     */
    public void devaliderSemaine(int idPeriode, LocalDate dateDebutTroncon) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM pointage_semaine_calcule WHERE id_periode_paie = ? AND debut_semaine = ?")) {
            ps.setInt(1, idPeriode);
            ps.setDate(2, Date.valueOf(dateDebutTroncon));
            ps.executeUpdate();
        }
    }
}
