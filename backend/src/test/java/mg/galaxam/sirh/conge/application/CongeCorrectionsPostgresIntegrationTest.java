package mg.galaxam.sirh.conge.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.UUID;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.domain.CongeLotValorisationEngine;
import mg.galaxam.sirh.paie.application.PayrollLeaveJournalService;
import mg.galaxam.sirh.paie.application.PayrollService;
import org.junit.jupiter.api.Test;

/** DAF correction and history checks against disposable PostgreSQL. */
class CongeCorrectionsPostgresIntegrationTest {
    private static final String URL = System.getenv("GALAXAM_CONGE_TEST_DB_URL");
    private static final String USER = System.getenv("GALAXAM_CONGE_TEST_DB_USER");
    private static final String PASSWORD = System.getenv("GALAXAM_CONGE_TEST_DB_PASSWORD");
    private static BigDecimal d(String s) { return new BigDecimal(s); }

    @Test
    void correctionMensuelleNonConsommeeMetAJourJournalLotCumulsEtTrace() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c); contract(c, employee, "2026-01-01");
                int march = payrollPeriod(c, "2026-03-01", "2026-03-31");
                int april = payrollPeriod(c, "2026-04-01", "2026-04-30");
                bulletin(c, employee, march, "300000"); bulletin(c, employee, april, "300000");
                PayrollService payroll = payrollService();
                payroll.cloturerDonneesPaie(march, null, c); payroll.cloturerDonneesPaie(april, null, c);
                int firstJournal = journal(c, employee, 3);
                int secondJournal = journal(c, employee, 4);
                int movement = scalarInt(c, "SELECT id_mouvement FROM mouvement_solde_conge WHERE id_employe=? AND type_mouvement='ACQUISITION' AND date_mouvement=DATE '2026-03-31'", employee);
                int reference = scalarInt(c, "SELECT id_periode_reference FROM conge_journal_droit WHERE id_journal=?", firstJournal);
                int author = testUser(c);

                CongeValorisationMensuelleService correction = monthlyCorrectionService();
                correction.corriger(reference, firstJournal, d("26000"), "Correction DAF mensuelle", author, c);
                decimal("26000", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", firstJournal));
                decimal("26000", scalarDecimal(c, "SELECT valeur_source FROM mouvement_solde_conge WHERE id_mouvement=?", movement));
                decimal("51000", scalarDecimal(c, "SELECT cumul_valorisation FROM conge_journal_droit WHERE id_journal=?", secondJournal));
                decimal("51000", new CongeLotValorisationService().situation(employee, LocalDate.of(2026, 4, 30), c).soldeValorisation());
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_valorisation_mensuelle_correction WHERE id_journal=? AND id_mouvement_acquisition=?", firstJournal, movement));
                try (PreparedStatement ps = c.prepareStatement("SELECT ancienne_valeur,nouvelle_valeur,motif,corrige_par,date_correction FROM conge_valorisation_mensuelle_correction WHERE id_journal=?")) {
                    ps.setInt(1, firstJournal); try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next()); decimal("25000", rs.getBigDecimal(1)); decimal("26000", rs.getBigDecimal(2));
                        assertEquals("Correction DAF mensuelle", rs.getString(3)); assertEquals(author, rs.getInt(4)); assertNotNull(rs.getTimestamp(5));
                    }
                }
                var history = correction.historique(reference, c);
                assertEquals(1, history.size()); assertEquals(author, history.get(0).corrigePar());
            } finally { c.rollback(); }
        }
    }

    @Test
    void correctionsMensuellesRefuseesPourLotsPartiellementOuTotalementConsommes() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int author = testUser(c);
                String[] starts = {"2026-03-01", "2026-05-01"};
                String[] ends = {"2026-03-31", "2026-05-31"};
                for (int index = 0; index < 2; index++) {
                    String daysTaken = index == 0 ? "1" : "2.5";
                    int employee = employee(c); contract(c, employee, "2026-01-01");
                    int period = payrollPeriod(c, starts[index], ends[index]); bulletin(c, employee, period, "300000");
                    payrollService().cloturerDonneesPaie(period, null, c);
                    int journal = journal(c, employee, index == 0 ? 3 : 5);
                    int reference = scalarInt(c, "SELECT id_periode_reference FROM conge_journal_droit WHERE id_journal=?", journal);
                    int demand = demand(c, employee, daysTaken);
                    insertMovement(c, employee, "PRISE", "-" + daysTaken, "2026-06-01", demand, "Prise de test");
                    var service = monthlyCorrectionService();
                    IllegalStateException refusal = assertThrows(IllegalStateException.class,
                            () -> service.corriger(reference, journal, d("26000"), "Test lot consommé", author, c));
                    assertTrue(refusal.getMessage().contains("CONGE_LOT_ALREADY_USED"));
                    assertEquals(0, scalarInt(c, "SELECT COUNT(*) FROM conge_valorisation_mensuelle_correction WHERE id_journal=?", journal));
                    decimal("25000", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
                }
            } finally { c.rollback(); }
        }
    }

    @Test
    void correctionMensuelleRefuseeApresClotureDeLaReference() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c); contract(c, employee, "2026-01-01");
                int period = payrollPeriod(c, "2026-12-01", "2026-12-31"); bulletin(c, employee, period, "300000");
                payrollService().cloturerDonneesPaie(period, null, c);
                int journal = journal(c, employee, 12);
                int reference = scalarInt(c, "SELECT id_periode_reference FROM conge_journal_droit WHERE id_journal=?", journal);
                assertEquals("CLOTUREE", scalarString(c, "SELECT statut FROM conge_periode_reference WHERE id_periode_reference=?", reference));
                IllegalStateException refusal = assertThrows(IllegalStateException.class,
                        () -> monthlyCorrectionService().corriger(reference, journal, d("26000"), "Après clôture", testUser(c), c));
                assertTrue(refusal.getMessage().contains("CONGE_REFERENCE_CLOSED"));
                assertEquals(0, scalarInt(c, "SELECT COUNT(*) FROM conge_valorisation_mensuelle_correction WHERE id_journal=?", journal));
            } finally { c.rollback(); }
        }
    }

    @Test
    void correctionDuReliquatReprisePreservePriseHistoriqueEtCalculeRegularisationAExaminer() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c); int author = testUser(c);
                var opening = new CongeTauxApplicableService.CutoverOpeningRequest();
                opening.setIdEmploye(employee); opening.setDateCutover(LocalDate.of(2026, 1, 15));
                opening.setModeReprise(CongeTauxApplicableService.CongeModeReprise.AGGREGATED_OPENING);
                opening.setSoldeJours(d("5")); opening.setValeurSoldeOuverture(d("50000"));
                opening.setTauxJournalierApplicable(d("12000")); opening.setDateDebutReference(LocalDate.of(2026, 1, 1));
                opening.setCumulDroitsOuverture(d("0")); opening.setCumulValorisationOuverture(d("0"));
                opening.setReferenceArrete("IT-DAF-OPENING");
                new CongeTauxApplicableService().enregistrerCutoverSnapshot(opening, author, c);
                int openingMovement = scalarInt(c, "SELECT id_mouvement FROM mouvement_solde_conge WHERE id_employe=? AND commentaire LIKE '[SOLDE_INITIAL]%'", employee);
                int demand = demand(c, employee, "2");
                int consumption = insertMovement(c, employee, "PRISE", "-2", "2026-02-01", demand, "Consommation historique test");
                var before = new CongeLotValorisationService().situation(employee, LocalDate.of(2026, 2, 1), c);
                decimal("3", before.soldeJours()); decimal("30000", before.soldeValorisation());
                decimal("20000", before.imputations().stream().filter(i -> i.idMouvement() == consumption).map(CongeLotValorisationEngine.Imputation::valeur).reduce(BigDecimal.ZERO, BigDecimal::add));

                var fixed = new CongeLotValorisationService().corrigerReliquatCutover(employee, d("2"), d("18000"),
                        "Révision du reliquat certifié", author, LocalDate.of(2026, 3, 1), c);
                decimal("3", fixed.ancienReliquatJours()); decimal("2", fixed.nouveauReliquatJours());
                decimal("30000", fixed.ancienneValeur()); decimal("18000", fixed.nouvelleValeur());
                decimal("-2000", fixed.regularisationAExaminer());
                var after = new CongeLotValorisationService().situation(employee, LocalDate.of(2026, 3, 1), c);
                decimal("2", after.soldeJours()); decimal("18000", after.soldeValorisation());
                decimal("20000", after.imputations().stream().filter(i -> i.idMouvement() == consumption).map(CongeLotValorisationEngine.Imputation::valeur).reduce(BigDecimal.ZERO, BigDecimal::add));
                assertEquals(1, scalarInt(c, "SELECT COUNT(*) FROM conge_cutover_correction WHERE id_employe=? AND id_mouvement_reprise=?", employee, openingMovement));
                int correctionMovement = scalarInt(c, "SELECT id_mouvement_ajustement FROM conge_cutover_correction WHERE id_employe=?", employee);
                assertEquals("AJUSTEMENT", scalarString(c, "SELECT type_mouvement FROM mouvement_solde_conge WHERE id_mouvement=?", correctionMovement));
                assertTrue(scalarString(c, "SELECT commentaire FROM mouvement_solde_conge WHERE id_mouvement=?", correctionMovement).startsWith("Correction DAF du reliquat de reprise :"));
                decimal("5", scalarDecimal(c, "SELECT nb_jours FROM mouvement_solde_conge WHERE id_mouvement=?", openingMovement));
                decimal("50000", scalarDecimal(c, "SELECT valeur_source FROM mouvement_solde_conge WHERE id_mouvement=?", openingMovement));
                assertNull(scalarDecimal(c, "SELECT valeur_source FROM mouvement_solde_conge WHERE id_mouvement=?", consumption));
            } finally { c.rollback(); }
        }
    }

    @Test
    void reclotureApresCorrectionDAFRefuseAvantLaGardeSpecifiqueDeCorrection() throws Exception {
        assumeDatabaseConfigured();
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                int employee = employee(c); contract(c, employee, "2026-01-01");
                int period = payrollPeriod(c, "2026-03-01", "2026-03-31"); bulletin(c, employee, period, "300000");
                PayrollService payroll = payrollService(); payroll.cloturerDonneesPaie(period, null, c);
                int journal = journal(c, employee, 3);
                int reference = scalarInt(c, "SELECT id_periode_reference FROM conge_journal_droit WHERE id_journal=?", journal);
                monthlyCorrectionService().corriger(reference, journal, d("26000"), "Correction avant reclôture", testUser(c), c);
                payroll.rouvrirDonneesPaie(period, "Reclôture de contrôle", null, c);
                IllegalStateException refusal = assertThrows(IllegalStateException.class, () -> payroll.cloturerDonneesPaie(period, null, c));
                assertTrue(refusal.getMessage().contains("CONGE_ACQUISITION_RECALCULATION_CONFLICT"));
                assertFalse(refusal.getMessage().contains("CONGE_VALORISATION_CORRIGEE"));
                decimal("26000", scalarDecimal(c, "SELECT valorisation_mensuelle FROM conge_journal_droit WHERE id_journal=?", journal));
            } finally { c.rollback(); }
        }
    }

    private static CongeValorisationMensuelleService monthlyCorrectionService() throws Exception {
        var service = new CongeValorisationMensuelleService();
        Field lots = CongeValorisationMensuelleService.class.getDeclaredField("lots"); lots.setAccessible(true);
        lots.set(service, new CongeLotValorisationService()); return service;
    }
    private static PayrollService payrollService() throws Exception {
        var payroll = new PayrollService();
        Field service = PayrollService.class.getDeclaredField("payrollLeaveJournalService"); service.setAccessible(true);
        service.set(payroll, new PayrollLeaveJournalService(new CongeService(new CongeCalculateurEngine()), new CongeTauxApplicableService()));
        return payroll;
    }
    private static int employee(Connection c) throws Exception {
        String s = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (PreparedStatement ps=c.prepareStatement("INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) VALUES (?, 'TEST','Correction','M','1990-01-01','Test',?,'2010-01-01','Test','Test','000000') RETURNING id_employe")) {
            ps.setString(1,"CD-"+s); ps.setString(2,"CD"+s); try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
    private static void contract(Connection c,int employee,String start)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO contrat_travail (id_employe,id_filiale,id_type_contrat,id_type_intervenant,id_statut_rh,numero_contrat,date_debut) VALUES (?,1,1,1,4,?,?)")){
            ps.setInt(1,employee);ps.setString(2,"CT-"+UUID.randomUUID());ps.setObject(3,LocalDate.parse(start));ps.executeUpdate();
        }
    }
    private static int payrollPeriod(Connection c,String start,String end)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO periode_paie(code,libelle,date_debut,date_fin) VALUES (?, 'Test corrections', ?, ?) RETURNING id_periode_paie")){
            ps.setString(1,"C"+UUID.randomUUID().toString().substring(0,12));ps.setObject(2,LocalDate.parse(start));ps.setObject(3,LocalDate.parse(end));try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
    private static void bulletin(Connection c,int employee,int period,String salary)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO bulletin_paie (id_periode_paie,id_employe,type_bulletin,reference_bulletin,matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,jours_travailles,jours_absence,salaire_base,taux_horaire) SELECT ?,e.id_employe,'MENSUEL',?,e.matricule,e.nom||' '||e.prenom,'Test','Test','PERMANENT',30,0,?,1000 FROM employe e WHERE e.id_employe=?")){
            ps.setInt(1,period);ps.setString(2,"BC-"+UUID.randomUUID());ps.setBigDecimal(3,d(salary));ps.setInt(4,employee);ps.executeUpdate();
        }
    }
    private static int demand(Connection c,int employee,String days)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO demande_conge (id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,date_demande,nb_jours_decomptes,est_remunere) VALUES (?,5,3,'2026-02-01','2026-02-02','2026-01-01',?,TRUE) RETURNING id_demande_conge")){
            ps.setInt(1,employee);ps.setBigDecimal(2,d(days));try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
    private static int insertMovement(Connection c,int employee,String type,String days,String date,Integer demand,String comment)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,date_mouvement,id_demande_conge,commentaire) VALUES(?,?,?,?,?,?) RETURNING id_mouvement")){
            ps.setInt(1,employee);ps.setString(2,type);ps.setBigDecimal(3,d(days));ps.setObject(4,LocalDate.parse(date));ps.setInt(5,demand);ps.setString(6,comment);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
    private static int testUser(Connection c)throws Exception {
        String s=UUID.randomUUID().toString().replace("-", "").substring(0,12);
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO utilisateur(identifiant,email,mot_de_passe_hache,id_role) VALUES(?,?, 'test-hash',1) RETURNING id_utilisateur")){
            ps.setString(1,"it-"+s);ps.setString(2,"it-"+s+"@example.invalid");try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }
    private static int journal(Connection c,int employee,int month)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("SELECT id_journal FROM conge_journal_droit WHERE id_employe=? AND annee=2026 AND mois=?")){ps.setInt(1,employee);ps.setInt(2,month);try(ResultSet rs=ps.executeQuery()){assertTrue(rs.next());return rs.getInt(1);}}
    }
    private static int scalarInt(Connection c,String sql,int... values)throws Exception {
        try(PreparedStatement ps=c.prepareStatement(sql)){for(int i=0;i<values.length;i++)ps.setInt(i+1,values[i]);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getInt(1);}}
    }
    private static BigDecimal scalarDecimal(Connection c,String sql,int id)throws Exception {
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}}
    }
    private static String scalarString(Connection c,String sql,int id)throws Exception {
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getString(1);}}
    }
    private static void decimal(String expected,BigDecimal actual){assertNotNull(actual);assertEquals(0,d(expected).compareTo(actual));}
    private static void assumeDatabaseConfigured(){assumeTrue(URL!=null&&!URL.isBlank(),"Requires isolated disposable PostgreSQL");}
    private static Connection connection()throws Exception{return DriverManager.getConnection(URL,USER,PASSWORD);}
}
