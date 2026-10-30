package mg.galaxam.sirh.paie.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import mg.galaxam.sirh.paie.api.dto.stc.StcCalculRequest;
import mg.galaxam.sirh.paie.domain.dto.PayrollAttendanceInput;
import mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput;
import mg.galaxam.sirh.paie.domain.engine.DailyPayProration;
import mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StcIntervenantModePostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");
    private static final Date DEPARTURE = Date.valueOf("2026-03-31");

    @Test
    void stageEstRefuseEtLesSalariesGardentLeurModeDansLaPreparation() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Requires isolated PostgreSQL");
        for (String[] scenario : new String[][] {
                {"STAGIAIRE", "JOURNALIER", "20000"},
                {"STAGIAIRE", "MENSUEL", "300000"},
                {"STAGIAIRE", "HORAIRE", "1000"},
                {"CONSULTANT", "HORAIRE", "1000"},
                {"CONSULTANT", "JOURNALIER", "20000"},
                {"CONSULTANT", "MENSUEL", "300000"},
                {"PERMANENT", "MENSUEL", "300000"},
                {"PERMANENT", "JOURNALIER", "20000"},
                {"PERMANENT", "HORAIRE", "1000"},
                {"TEMPORAIRE", "MENSUEL", "300000"},
                {"TEMPORAIRE", "JOURNALIER", "20000"},
                {"TEMPORAIRE", "HORAIRE", "1000"},
                {"CONSULTANT_SALARIE", "MENSUEL", "300000"},
                {"CONSULTANT_SALARIE", "JOURNALIER", "20000"},
                {"CONSULTANT_SALARIE", "HORAIRE", "1000"}}) {
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                c.setAutoCommit(false);
                try {
                    int employee = fixture(c, scenario[0], scenario[1], scenario[2]);
                    StcService service = new StcService();
                    if ("STAGIAIRE".equals(scenario[0]) || "CONSULTANT".equals(scenario[0])) {
                        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                                () -> service.preparerStc(employee, DEPARTURE, "FIN_CONTRAT", c));
                        assertTrue(error.getMessage().contains("STC") || error.getMessage().contains("stage"));
                    } else {
                        var prep = service.preparerStc(employee, DEPARTURE, "FIN_CONTRAT", c);
                        assertEquals(scenario[1], prep.getModeCalculPaie());
                        if (!"MENSUEL".equals(scenario[1])) {
                            assertEquals(0, prep.getSalaireProrataEstime().compareTo(BigDecimal.ZERO));
                            var request = new StcCalculRequest();
                            request.setIdEmploye(employee);
                            request.setDateDepart(DEPARTURE);
                            request.setMotifRupture("FIN_CONTRAT");
                            IllegalStateException error = assertThrows(IllegalStateException.class,
                                    () -> service.simulerStc(request, c));
                            assertTrue(error.getMessage().contains("STC_NON_MONTHLY_FINAL_PAYSLIP_REQUIRED"));
                        }
                    }
                } finally { c.rollback(); }
            }
        }
    }

    @Test
    void bulletinFinalEtCongesRestantsSontRapprochesPourChaqueModeSalarie() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Requires isolated PostgreSQL");
        for (String[] scenario : new String[][] {
                {"PERMANENT", "MENSUEL", "300000"},
                {"PERMANENT", "JOURNALIER", "20000"},
                {"PERMANENT", "HORAIRE", "1000"},
                {"TEMPORAIRE", "MENSUEL", "300000"},
                {"TEMPORAIRE", "JOURNALIER", "20000"},
                {"TEMPORAIRE", "HORAIRE", "1000"},
                {"CONSULTANT_SALARIE", "MENSUEL", "300000"},
                {"CONSULTANT_SALARIE", "JOURNALIER", "20000"},
                {"CONSULTANT_SALARIE", "HORAIRE", "1000"}}) {
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                c.setAutoCommit(false);
                try {
                    int employee = fixture(c, scenario[0], scenario[1], scenario[2]);
                    int period = payrollPeriod(c);
                    finalBulletin(c, employee, period, scenario[0], scenario[1]);
                    StcService service = new StcService();
                    var withoutLeave = service.simulerStc(request(employee), c);
                    assertEquals(0, withoutLeave.getIndemniteCongesPayes().compareTo(BigDecimal.ZERO));
                    assertEquals(0, withoutLeave.getSalaireProrata().compareTo(BigDecimal.ZERO));
                    try (PreparedStatement ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                            + "(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source,commentaire) "
                            + "VALUES (?,'ACQUISITION',2,DATE '2026-02-28',20000,'Droits STC de test')")) {
                        ps.setInt(1, employee); ps.executeUpdate();
                    }

                    StcCalculRequest unpaid = request(employee);
                    var beforePayment = service.simulerStc(unpaid, c);
                    assertEquals(0, beforePayment.getSalaireProrata().compareTo(BigDecimal.ZERO));
                    assertEquals(0, beforePayment.getSoldeBulletinARegler().compareTo(new BigDecimal("100000")));
                    assertEquals(0, beforePayment.getIndemniteCongesPayes().compareTo(new BigDecimal("20000")));

                    StcCalculRequest partiallyPaid = request(employee);
                    partiallyPaid.setMontantBulletinDejaPaye(new BigDecimal("40000"));
                    partiallyPaid.setDatePaiementBulletin(Date.valueOf("2026-03-30"));
                    partiallyPaid.setReferencePaiementBulletin("REGLEMENT-IT");
                    var afterPayment = service.simulerStc(partiallyPaid, c);
                    assertEquals(0, afterPayment.getSoldeBulletinARegler().compareTo(new BigDecimal("60000")));
                    assertEquals(0, beforePayment.getNetAPayerStc().subtract(afterPayment.getNetAPayerStc())
                            .compareTo(new BigDecimal("40000")));
                    assertEquals(0, afterPayment.getIndemniteCongesPayes().compareTo(new BigDecimal("20000")));
                } finally { c.rollback(); }
            }
        }
    }

    @Test
    void ancienStageNeComptePasDansAncienneteDuStcSalarie() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Requires isolated PostgreSQL");
        try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
            c.setAutoCommit(false);
            try {
                int employee = fixture(c, "PERMANENT", "MENSUEL", "300000");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE contrat_travail SET date_debut=DATE '2026-02-01' WHERE id_employe=?")) {
                    ps.setInt(1, employee); assertEquals(1, ps.executeUpdate());
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE employe_affectation_carriere SET date_debut=DATE '2026-02-01' WHERE id_employe=?")) {
                    ps.setInt(1, employee); assertEquals(1, ps.executeUpdate());
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrat_travail "
                        + "(id_employe,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,numero_contrat,"
                        + "date_debut,date_fin) SELECT ?,1,tc.id_type_contrat,ti.id_type_intervenant,4,?,"
                        + "DATE '2026-01-01',DATE '2026-01-31' FROM ref_type_contrat tc "
                        + "CROSS JOIN ref_type_intervenant ti WHERE tc.code='STAGE' AND ti.code='STAGIAIRE'")) {
                    ps.setInt(1, employee); ps.setString(2, "PRE-STAGE-" + UUID.randomUUID());
                    assertEquals(1, ps.executeUpdate());
                }
                var prep = new StcService().preparerStc(employee, DEPARTURE, "DEMISSION", c);
                assertEquals(Date.valueOf("2026-02-01"), prep.getDateEmbauche());
            } finally { c.rollback(); }
        }
    }

    @Test
    void pointageBulletinEtStcPourEntreeOuSortieEnCoursDePeriode() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Requires isolated PostgreSQL");
        Date departure = Date.valueOf("2026-03-15");
        for (String mode : new String[] {"MENSUEL", "JOURNALIER", "HORAIRE"}) {
            for (boolean nouveau : new boolean[] {true, false}) {
                try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                    c.setAutoCommit(false);
                    try {
                        String rate = "MENSUEL".equals(mode) ? "300000"
                                : "JOURNALIER".equals(mode) ? "20000" : "1000";
                        int employee = fixture(c, "PERMANENT", mode, rate);
                        Date start = Date.valueOf(nouveau ? "2026-03-15" : "2026-01-01");
                        try (PreparedStatement ps = c.prepareStatement(
                                "UPDATE contrat_travail SET date_debut=?,date_fin=? WHERE id_employe=?")) {
                            ps.setDate(1, start); ps.setDate(2, departure); ps.setInt(3, employee);
                            assertEquals(1, ps.executeUpdate());
                        }
                        if (nouveau) {
                            try (PreparedStatement ps = c.prepareStatement(
                                    "UPDATE employe_affectation_carriere SET date_debut=? WHERE id_employe=?")) {
                                ps.setDate(1, start); ps.setInt(2, employee); assertEquals(1, ps.executeUpdate());
                            }
                        }
                        int period = payrollPeriod(c);
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pointage_jour "
                                + "(id_employe,id_periode_paie,id_site,date_pointage,id_statut_jour,total_heures) "
                                + "SELECT ?,?,id_site,DATE '2026-03-15',1,479.0/60 "
                                + "FROM ref_site WHERE code_site='TNR'")) {
                            ps.setInt(1, employee); ps.setInt(2, period); assertEquals(1, ps.executeUpdate());
                        }
                        int minutes;
                        try (PreparedStatement ps = c.prepareStatement(
                                "SELECT ROUND(SUM(total_heures)*60)::int FROM pointage_jour WHERE id_employe=? AND id_periode_paie=?")) {
                            ps.setInt(1, employee); ps.setInt(2, period);
                            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); minutes = rs.getInt(1); }
                        }
                        assertEquals(479, minutes);
                        var input = payrollInput(employee, period, mode, rate, minutes, nouveau ? 1 : 15);
                        var calculated = new PayrollExecutionEngine().calculerBulletin(input);
                        BigDecimal base = calculated.getMontantLigneSalariale("SALAIRE_BASE");
                        BigDecimal expected = "MENSUEL".equals(mode)
                                ? new BigDecimal(nouveau ? "10000" : "150000")
                                : "JOURNALIER".equals(mode) ? new BigDecimal("19958.33")
                                : new BigDecimal("7983.33");
                        assertEquals(0, expected.compareTo(base));
                        finalBulletin(c, employee, period, "PERMANENT", mode);
                        try (PreparedStatement ps = c.prepareStatement("UPDATE bulletin_paie SET salaire_base=?,"
                                + "total_gains_bruts=?,net_a_payer=? WHERE id_employe=? AND id_periode_paie=?")) {
                            ps.setBigDecimal(1, new BigDecimal(rate));
                            ps.setBigDecimal(2, calculated.getBulletin().getTotalGainsBruts());
                            ps.setBigDecimal(3, calculated.getBulletin().getNetAPayer());
                            ps.setInt(4, employee); ps.setInt(5, period); assertEquals(1, ps.executeUpdate());
                        }
                        var request = request(employee);
                        request.setDateDepart(departure);
                        var stc = new StcService().simulerStc(request, c);
                        assertEquals(0, stc.getSalaireProrata().compareTo(BigDecimal.ZERO));
                        assertEquals(0, stc.getSoldeBulletinARegler()
                                .compareTo(calculated.getBulletin().getNetAPayer()));
                    } finally { c.rollback(); }
                }
            }
        }
    }

    @Test
    void multiJoursIrreguliersAvecHsEtCongeSontReprisUneSeuleFoisAuStc() throws Exception {
        assumeTrue(URL != null && !URL.isBlank(), "Requires isolated PostgreSQL");
        for (String mode : new String[] {"MENSUEL", "JOURNALIER", "HORAIRE"}) {
            try (Connection c = DriverManager.getConnection(URL, USER, PASSWORD)) {
                c.setAutoCommit(false);
                try {
                    String rate = "MENSUEL".equals(mode) ? "300000" : "JOURNALIER".equals(mode) ? "20000" : "1000";
                    int employee = fixture(c, "PERMANENT", mode, rate);
                    Date departure = Date.valueOf("2026-03-20");
                    try (PreparedStatement ps = c.prepareStatement("UPDATE contrat_travail SET date_debut=DATE '2026-03-16',date_fin=? WHERE id_employe=?")) {
                        ps.setDate(1, departure); ps.setInt(2, employee); assertEquals(1, ps.executeUpdate());
                    }
                    try (PreparedStatement ps = c.prepareStatement("UPDATE employe_affectation_carriere SET date_debut=DATE '2026-03-16' WHERE id_employe=?")) {
                        ps.setInt(1, employee); assertEquals(1, ps.executeUpdate());
                    }
                    int period = payrollPeriod(c);
                    long[] worked = {479, 443, 480, 360};
                    long totalMinutes = 0;
                    for (int i = 0; i < worked.length; i++) {
                        java.time.LocalDate date = java.time.LocalDate.of(2026, 3, 16).plusDays(i);
                        try (PreparedStatement ps = c.prepareStatement("INSERT INTO pointage_jour (id_employe,id_periode_paie,id_site,date_pointage,id_statut_jour,total_heures) SELECT ?,?,id_site,?,1,? / 60.0 FROM ref_site WHERE code_site='TNR'")) {
                            ps.setInt(1, employee); ps.setInt(2, period); ps.setObject(3, date); ps.setLong(4, worked[i]); assertEquals(1, ps.executeUpdate());
                        }
                        totalMinutes += worked[i];
                    }
                    var input = payrollInput(employee, period, mode, rate, (int) totalMinutes, 5);
                    input.setHeuresHs30(new BigDecimal("1"));
                    input.setNbJoursConges(BigDecimal.ONE);
                    input.setIndemniteCongeLots(new BigDecimal("12000"));
                    input.setAvancesAcomptes(List.of());
                    var calculated = new PayrollExecutionEngine().calculerBulletin(input);
                    assertEquals(0, new BigDecimal("1").compareTo(calculated.getMontantLigneSalariale("HEURES_SUP_30")
                            .divide(input.getTauxHoraireEffectif(), 8, java.math.RoundingMode.HALF_UP)));
                    assertEquals(0, new BigDecimal("12000").compareTo(calculated.getMontantLigneSalariale("INDEMNITE_CONGE_PAYE")));
                    assertEquals(0, BigDecimal.ZERO.compareTo(calculated.getMontantLigneSalariale("DEDUCTION_CONGE_BASE")));

                    finalBulletin(c, employee, period, "PERMANENT", mode);
                    try (PreparedStatement ps = c.prepareStatement("UPDATE bulletin_paie SET total_gains_bruts=?,net_a_payer=? WHERE id_employe=? AND id_periode_paie=?")) {
                        ps.setBigDecimal(1, calculated.getBulletin().getTotalGainsBruts());
                        ps.setBigDecimal(2, calculated.getBulletin().getNetAPayer());
                        ps.setInt(3, employee); ps.setInt(4, period); assertEquals(1, ps.executeUpdate());
                    }
                    var request = request(employee); request.setDateDepart(departure);
                    var stc = new StcService().simulerStc(request, c);
                    assertEquals(0, stc.getSalaireProrata().compareTo(BigDecimal.ZERO));
                    assertEquals(0, stc.getSoldeBulletinARegler().compareTo(calculated.getBulletin().getNetAPayer()));
                    assertEquals(0, stc.getIndemniteCongesPayes().compareTo(BigDecimal.ZERO));
                } finally { c.rollback(); }
            }
        }
    }

    private static PayrollCalculationInput payrollInput(int employee, int period, String mode,
            String rate, int minutes, int daysUnderContract) {
        var input = new PayrollCalculationInput();
        input.setIdEmploye(employee);
        input.setIdPeriodePaie(period);
        input.setMatricule("STC-TEST-" + employee);
        input.setNomPrenom("Test STC");
        input.setCodePeriode("2026-03");
        input.setDateDebutPeriode(Date.valueOf("2026-03-01"));
        input.setDateFinPeriode(Date.valueOf("2026-03-31"));
        input.setModeCalculPaie(mode);
        input.setSalaireBaseContractuel(new BigDecimal(rate));
        input.setHeuresNormalesParJour(new BigDecimal("8"));
        input.setHeuresNormales(new BigDecimal("176"));
        input.setNbJoursOuvrables(new BigDecimal("22"));
        input.setNbJoursPresents(BigDecimal.ONE);
        input.setJoursSousContrat(new BigDecimal(daysUnderContract));
        input.setEntreeSortieEnCoursDeMois(true);
        input.setSoumisCnaps(false);
        input.setSoumisSante(false);
        input.setSoumisIrsa(false);
        input.setAvancesAcomptes(List.of());
        if ("JOURNALIER".equals(mode)) input.setJoursPayablesJournaliers(DailyPayProration.payableDays(
                List.of(new DailyPayProration.Day(minutes, 0, 480))));
        if (!"MENSUEL".equals(mode)) input.setAttendanceInput(new PayrollAttendanceInput(1, 1,
                employee, period, Instant.now(), minutes, 0, 0, 0, 0, 0, 0, 0, 0,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        return input;
    }

    private static StcCalculRequest request(int employee) {
        StcCalculRequest request = new StcCalculRequest();
        request.setIdEmploye(employee);
        request.setDateDepart(DEPARTURE);
        request.setMotifRupture("FIN_CONTRAT");
        return request;
    }

    private static int payrollPeriod(Connection c) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO periode_paie "
                + "(code,libelle,date_debut,date_fin) VALUES (?, 'Test STC',DATE '2026-03-01',DATE '2026-03-31') "
                + "RETURNING id_periode_paie")) {
            ps.setString(1, "STC" + UUID.randomUUID().toString().substring(0, 12));
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); return rs.getInt(1); }
        }
    }

    private static void finalBulletin(Connection c, int employee, int period, String regime, String mode) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO bulletin_paie "
                + "(id_periode_paie,id_employe,type_bulletin,reference_bulletin,matricule,nom_prenom,"
                + "intitule_poste,nom_site,regime_intervenant,mode_calcul_paie,statut,salaire_base,taux_horaire,"
                + "total_gains_bruts,net_a_payer) "
                + "SELECT ?,e.id_employe,'MENSUEL',?,e.matricule,e.nom||' '||e.prenom,'Test','Test',?,?,"
                + "'VALIDE',100000,1000,100000,100000 FROM employe e WHERE e.id_employe=?")) {
            ps.setInt(1, period); ps.setString(2, "STC-B-" + UUID.randomUUID());
            ps.setString(3, regime); ps.setString(4, mode); ps.setInt(5, employee);
            assertEquals(1, ps.executeUpdate());
        }
    }

    private static int fixture(Connection c, String regime, String mode, String salary) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        int employee;
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                + "VALUES (?, 'TEST', 'STC', 'M', DATE '1990-01-01', 'Test', ?, DATE '2010-01-01', 'Test', 'Adresse test', '000000') RETURNING id_employe")) {
            ps.setString(1, "STC-" + suffix); ps.setString(2, "STC-" + suffix);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); employee = rs.getInt(1); }
        }
        int contract;
        String contractType = "STAGIAIRE".equals(regime) ? "STAGE" : "TEMPORAIRE".equals(regime) ? "TEMPORAIRE" : "CDI";
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO contrat_travail "
                + "(id_employe,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,id_type_evenement_initial,numero_contrat,date_signature,date_debut,date_fin,duree_essai_mois,comptabilise_effectif) "
                + "SELECT ?,f.id_filiale,tc.id_type_contrat,ti.id_type_intervenant,s.id_statut_rh,e.id_type_evenement_rh,?,"
                + "DATE '2026-01-01',DATE '2026-01-01',?,0,TRUE "
                + "FROM ref_filiale f CROSS JOIN ref_type_contrat tc CROSS JOIN ref_type_intervenant ti "
                + "CROSS JOIN ref_statut_rh s CROSS JOIN ref_type_evenement_rh e "
                + "WHERE f.code_filiale='GLX' AND tc.code=? AND ti.code=? AND s.code='ACTIF' "
                + "AND e.code='EMBAUCHE_INITIALE' RETURNING id_contrat")) {
            ps.setInt(1, employee); ps.setString(2, "STC-CON-" + suffix);
            if ("PERMANENT".equals(regime)) ps.setNull(3, java.sql.Types.DATE);
            else ps.setDate(3, DEPARTURE);
            ps.setString(4, contractType); ps.setString(5, regime);
            try (ResultSet rs = ps.executeQuery()) { assertTrue(rs.next()); contract = rs.getInt(1); }
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe_affectation_carriere "
                + "(id_employe,id_contrat,id_statut_rh,id_motif_mouvement,id_site,id_departement,id_service,id_fonction,"
                + "id_categorie_professionnelle,id_mode_calcul_paie,salaire_base,date_debut) "
                + "SELECT ?,?,s.id_statut_rh,m.id_motif_mouvement,site.id_site,d.id_departement,svc.id_service,"
                + "fn.id_fonction,cat.id_categorie_professionnelle,mode.id_mode_calcul_paie,?,DATE '2026-01-01' "
                + "FROM ref_statut_rh s CROSS JOIN ref_motif_mouvement m CROSS JOIN ref_site site "
                + "CROSS JOIN ref_departement d CROSS JOIN ref_service svc CROSS JOIN ref_fonction fn "
                + "CROSS JOIN ref_categorie_professionnelle cat CROSS JOIN ref_mode_calcul_paie mode "
                + "WHERE s.code='ACTIF' AND m.code='EMBAUCHE' AND site.code_site='TNR' "
                + "AND d.code_departement='LOG' AND svc.code_service='ADM' AND fn.code_fonction='AIDE_COMPTABLE' "
                + "AND cat.code='A' AND mode.code=?")) {
            ps.setInt(1, employee); ps.setInt(2, contract); ps.setBigDecimal(3, new BigDecimal(salary));
            ps.setString(4, mode); assertEquals(1, ps.executeUpdate());
        }
        return employee;
    }
}
