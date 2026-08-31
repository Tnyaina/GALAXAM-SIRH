package mg.galaxam.sirh.model.pointage.service;

import mg.galaxam.sirh.api.dto.pointage.PointageSyntheseMensuelleDTO;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
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
import java.util.*;

@Service
public class PointageSyntheseService {

    @Autowired
    private DataSource dataSource;

    public PointageSyntheseService() {}

    public PointageSyntheseService(DataSource dataSource) {
        this.dataSource = dataSource;
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

        // Calcul des jours ouvrables de la période
        int nbJoursOuvrablesPeriode = 0;
        for (LocalDate d = dateDebut; !d.isAfter(dateFin); d = d.plusDays(1)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                nbJoursOuvrablesPeriode++;
            }
        }

        // 2. Charger les employés éligibles
        StringBuilder sqlEmp = new StringBuilder(
            "SELECT DISTINCT e.id_employe, e.matricule, e.nom, e.prenom, e.photo_url, " +
            "s.id_site, s.code_site AS site_code, s.libelle AS site_nom, " +
            "COALESCE(f.dispense_pointage, FALSE) AS est_autonome, " +
            "COALESCE(cp.code, '') AS code_categorie " +
            "FROM employe e " +
            "JOIN employe_affectation_carriere a ON e.id_employe = a.id_employe AND a.date_fin IS NULL " +
            "LEFT JOIN ref_fonction f ON a.id_fonction = f.id_fonction " +
            "LEFT JOIN ref_categorie_professionnelle cp ON a.id_categorie_professionnelle = cp.id_categorie_professionnelle " +
            "LEFT JOIN ref_site s ON a.id_site = s.id_site " +
            "WHERE 1=1 "
        );

        if (idSite != null && idSite > 0) {
            sqlEmp.append("AND a.id_site = ? ");
        }
        sqlEmp.append("ORDER BY e.nom, e.prenom");

        // 3. Traiter chaque employé
        try (PreparedStatement psE = c.prepareStatement(sqlEmp.toString())) {
            if (idSite != null && idSite > 0) {
                psE.setInt(1, idSite);
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
                                String stCode = rsPtg.getString("statut_code");
                                double th = rsPtg.getDouble("total_heures");
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
                                totalMinsRetard += ret;

                                if (repas) {
                                    repasCount++;
                                    montantRepasTotal += (mntRepas > 0 ? mntRepas : 2000.0);
                                }

                                if (depM) totalTrajets += 0.5;
                                if (depA) totalTrajets += 0.5;

                                LocalDate lundiSemaine = dPointage.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                                parSemaine.computeIfAbsent(lundiSemaine, k -> new ArrayList<>());

                                Map<String, Object> dayRow = new HashMap<>();
                                dayRow.put("date", dPointage);
                                dayRow.put("totalHeures", th);
                                dayRow.put("retardMins", ret);
                                parSemaine.get(lundiSemaine).add(dayRow);
                            }
                        }
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

                    String siteCode = rsE.getString("site_code");
                    String siteNom = rsE.getString("site_nom");
                    boolean isSiteTmm = (siteCode != null && "TMM".equalsIgnoreCase(siteCode.trim())) ||
                                        (siteNom != null && siteNom.toLowerCase().contains("tamatave"));
                    double mntDeplacement = Math.max(0.0, (totalTrajets * 1500.0) - (isSiteTmm ? repasCount * reductionDeplactTmm : 0.0));
                    dto.setMontantDeplacement(mntDeplacement);

                    list.add(dto);
                }
            }
        }

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
}
