package mg.galaxam.sirh.temporal;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.Properties;
import mg.galaxam.sirh.conge.application.*;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import mg.galaxam.sirh.contrat.application.EmploymentContextResolver;
import mg.galaxam.sirh.paie.application.*;
import mg.galaxam.sirh.paieconfig.persistence.ParametreGlobal;
import mg.galaxam.sirh.pointage.application.PointageService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

/** JDBC sans démarrage de l'application : aucun scheduler, fixtures transactionnelles annulées. */
class PayrollLeaveYearBoundaryIntegrationTest {
    Connection c;
    int employee, contract, assignment;
    final CongeTauxApplicableService rates = new CongeTauxApplicableService();
    final CongeService leave = new CongeService(new CongeCalculateurEngine());
    final PayrollService payroll = new PayrollService();
    final EmploymentContextResolver employmentContext = new EmploymentContextResolver();

    @BeforeEach void setup() throws Exception {
        Properties properties = new Properties();
        try (var input = getClass().getResourceAsStream("/application-test.properties")) { properties.load(input); }
        String url = System.getProperty("spring.datasource.url", properties.getProperty("spring.datasource.url"));
        assertTrue(url.startsWith("jdbc:postgresql:"));
        c = DriverManager.getConnection(url,
                System.getProperty("spring.datasource.username", properties.getProperty("spring.datasource.username")),
                System.getProperty("spring.datasource.password", properties.getProperty("spring.datasource.password")));
        // Première requête, avant toute fixture ou mutation.
        try (var statement = c.createStatement(); var result = statement.executeQuery("SELECT current_database()")) {
            assertTrue(result.next()); assertEquals("sirh_galaxam_test", result.getString(1));
        }
        c.setAutoCommit(false);
        try (var ps=c.prepareStatement("INSERT INTO employe(matricule,nom,prenom,sexe,date_naissance,cin_numero,lieu_naissance,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                + "VALUES (?, 'TEMPORAL','TEST','M','1990-01-01',?,'TNR','2010-01-01','TNR','Test','0340000000') RETURNING id_employe")) {
            ps.setString(1,"TY"+System.nanoTime()); ps.setString(2,Long.toString(System.nanoTime()));
            try (var rs=ps.executeQuery()) { rs.next(); employee=rs.getInt(1); }
        }
        contract = CongeTestFixtureHelper.createContratActif(c, employee, LocalDate.of(2025, 1, 1));
        assignment = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                LocalDate.of(2025, 1, 1), new BigDecimal("300000"));
        ReflectionTestUtils.setField(payroll, "congeService", leave);
        ReflectionTestUtils.setField(payroll, "payrollLeaveJournalService",
                new PayrollLeaveJournalService(leave, rates));
        ReflectionTestUtils.setField(payroll, "payrollLegalContextResolver", new PayrollLegalContextResolver());
        ReflectionTestUtils.setField(payroll, "employmentContextResolver", employmentContext);
    }
    @AfterEach void rollback() throws Exception { if (c != null) { c.rollback(); c.close(); } }
    LocalDate date(String value) { return LocalDate.parse(value); }

    int getOrCreateAuteur() throws SQLException {
        try (var ps = c.prepareStatement("SELECT id_utilisateur FROM utilisateur ORDER BY id_utilisateur LIMIT 1");
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return CongeTestFixtureHelper.createAdminUser(c, "admin_test_" + System.nanoTime());
    }

    @ParameterizedTest
    @CsvSource({"2025-10-26,2025-11-25,11", "2025-12-26,2026-01-25,1", "2026-01-26,2026-02-25,2"})
    void acquisitionsUtilisentLaClotureEtTraversentLAnnee(String start, String end, int month) throws Exception {
        int reference = rates.ensureLeaveReferencePeriod(employee, date(end), c);
        int period = bulletin(start, end);
        payroll.mettreAJourJournalConge(period, c);
        try (var ps = c.prepareStatement("SELECT m.date_mouvement,j.mois,j.id_periode_reference,j.numero_mois_reference "
                + "FROM mouvement_solde_conge m JOIN conge_journal_droit j ON j.id_employe=m.id_employe "
                + "WHERE m.id_employe=? AND m.type_mouvement='ACQUISITION'")) {
            ps.setInt(1, employee);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next()); assertEquals(date(end), rs.getDate(1).toLocalDate());
                assertEquals(month, rs.getInt(2)); assertEquals(reference, rs.getInt(3));
                assertEquals(end.equals("2025-11-25") ? 11 : end.equals("2026-01-25") ? 1 : 2, rs.getInt(4));
            }
        }
    }

    @Test void reference26DecembreCommenceAuMoisUnEnJanvier() throws Exception {
        rates.creerPeriodeReference(employee, date("2026-01-01"), date("2026-12-31"), null, c);
        payroll.mettreAJourJournalConge(bulletin("2025-12-26", "2026-01-25"), c);
        try (var ps = c.prepareStatement("SELECT numero_mois_reference FROM conge_journal_droit WHERE id_employe=?")) {
            ps.setInt(1, employee); try (var rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(1, rs.getInt(1)); }
        }
    }

    @Test void dafRectifieValeurJanvierSansChangerJoursEtConserveUnAudit() throws Exception {
        int reference = rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c);
        int periode = bulletin("2025-12-26", "2026-01-25");
        payroll.mettreAJourJournalConge(periode, c);
        int journal;
        BigDecimal jours;
        try (var ps=c.prepareStatement("SELECT id_journal,jours_droit_acquis FROM conge_journal_droit WHERE id_employe=?")) {
            ps.setInt(1,employee);
            try(var rs=ps.executeQuery()) { assertTrue(rs.next()); journal=rs.getInt(1); jours=rs.getBigDecimal(2); }
        }
        int auteur = getOrCreateAuteur();
        var service=new CongeValorisationMensuelleService();
        var lots=new CongeLotValorisationService();
        ReflectionTestUtils.setField(service,"lots",lots);
        var resultat=service.corriger(reference,journal,new BigDecimal("27000.00"),
                "Rapprochement Excel DAF",auteur,c);
        decimal("27000",(BigDecimal)resultat.get("nouvelleValeur"));
        var historique=service.historique(reference,c);
        assertEquals(1,historique.size());
        assertEquals("Rapprochement Excel DAF",historique.get(0).motif());
        decimal("27000",historique.get(0).nouvelleValeur());
        try(var ps=c.prepareStatement("SELECT j.jours_droit_acquis,j.valorisation_mensuelle,"
                + "j.cumul_valorisation,m.valeur_source FROM conge_journal_droit j "
                + "JOIN mouvement_solde_conge m ON m.id_employe=j.id_employe "
                + "AND m.date_mouvement='2026-01-25' AND m.type_mouvement='ACQUISITION' "
                + "WHERE j.id_journal=?")) {
            ps.setInt(1,journal);
            try(var rs=ps.executeQuery()) {
                assertTrue(rs.next()); assertEquals(0,jours.compareTo(rs.getBigDecimal(1)));
                decimal("27000",rs.getBigDecimal(2)); decimal("27000",rs.getBigDecimal(3));
                decimal("27000",rs.getBigDecimal(4));
            }
        }
        assertThrows(IllegalStateException.class,()->payroll.mettreAJourJournalConge(periode,c));
    }

    @Test void dafNeRectifiePasUnLotMensuelDejaConsomme() throws Exception {
        int reference=rates.ensureLeaveReferencePeriod(employee,date("2026-01-25"),c);
        payroll.mettreAJourJournalConge(bulletin("2025-12-26","2026-01-25"),c);
        int journal;
        try(var ps=c.prepareStatement("SELECT id_journal FROM conge_journal_droit WHERE id_employe=?")) {
            ps.setInt(1,employee); try(var rs=ps.executeQuery()) { assertTrue(rs.next()); journal=rs.getInt(1); }
        }
        try(var ps=c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) "
                + "VALUES (?,'PRISE',-1,'2026-02-01','Test consommation FIFO')")) {
            ps.setInt(1,employee); ps.executeUpdate();
        }
        int auteur = getOrCreateAuteur();
        var service=new CongeValorisationMensuelleService();
        ReflectionTestUtils.setField(service,"lots",new CongeLotValorisationService());
        var ex=assertThrows(IllegalStateException.class,()->service.corriger(reference,journal,
                new BigDecimal("27000"),"Contrôle DAF",auteur,c));
        assertTrue(ex.getMessage().contains("CONGE_LOT_ALREADY_USED"));
    }

    @Test void premiereAcquisitionJanvierCreeReferenceCivileUneSeuleFois() throws Exception {
        update("UPDATE contrat_travail SET date_debut='2026-01-01' WHERE id_contrat=?", contract);
        update("UPDATE employe_affectation_carriere SET date_debut='2026-01-01' WHERE id_affectation=?", assignment);
        int first = rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c);
        int second = rates.ensureLeaveReferencePeriod(employee, date("2026-02-25"), c);
        assertEquals(first, second);
        var reference = rates.resoudrePeriodeReferenceStrict(employee, date("2026-01-25"), c);
        assertEquals(date("2026-01-01"), reference.getDateDebut());
        assertEquals(date("2026-12-31"), reference.getDateFin());
        try (var ps = c.prepareStatement("SELECT count(*) FROM conge_periode_reference WHERE id_employe=?")) {
            ps.setInt(1, employee);
            try (var rs = ps.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
        }
        try (var ps = c.prepareStatement("SELECT mode_reprise FROM conge_periode_reference WHERE id_periode_reference=?")) {
            ps.setInt(1, first);
            try (var rs = ps.executeQuery()) { rs.next(); assertEquals("NEW_HIRE", rs.getString(1)); }
        }
    }

    @Test void ancienContratSansHistoriqueCongeNestPasUnNewHire() throws Exception {
        int id = rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c);
        try (var ps = c.prepareStatement("SELECT mode_reprise FROM conge_periode_reference WHERE id_periode_reference=?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) { rs.next(); assertEquals("FULL_HISTORY", rs.getString(1)); }
        }
    }

    @Test void stcValoriseLesLotsAnciensSansActivationGlobaleEnJanvier() throws Exception {
        try (var ps = c.prepareStatement("INSERT INTO mouvement_solde_conge " +
                "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire,valeur_source) " +
                "VALUES (?,'AJUSTEMENT',2.5,'2025-12-25','[SOLDE_INITIAL] Reprise',25000)")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
        var prep = new StcService().preparerStc(employee, java.sql.Date.valueOf(date("2026-01-25")),
                "DEMISSION", c);
        decimal("2.5", prep.getSoldeCongesRestant());
        decimal("25000", prep.getIccpEstimee());
        decimal("10000", prep.getTauxJournalierConge());
    }

    @Test void premiereReferenceAvecDroitsHistoriquesResteFullHistory() throws Exception {
        try (var ps = c.prepareStatement("INSERT INTO mouvement_solde_conge " +
                "(id_employe,type_mouvement,nb_jours,date_mouvement,commentaire) " +
                "VALUES (?,'AJUSTEMENT',3,'2025-12-31','[SOLDE_INITIAL] Reprise historique')")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
        int id = rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c);
        try (var ps = c.prepareStatement("SELECT mode_reprise FROM conge_periode_reference WHERE id_periode_reference=?")) {
            ps.setInt(1, id);
            try (var rs = ps.executeQuery()) { rs.next(); assertEquals("FULL_HISTORY", rs.getString(1)); }
        }
    }

    @Test void listeReferenceOuverteAfficheAcquisitionsCourantesSansTauxFige() throws Exception {
        payroll.mettreAJourJournalConge(bulletin("2025-12-26", "2026-01-25"), c);
        var period = rates.listerPeriodesReference(employee, c).get(0);
        assertEquals("OUVERTE", period.get("statut"));
        decimal("2.5", (BigDecimal) period.get("cumulDroitsCourant"));
        decimal("0", (BigDecimal) period.get("cumulDroitsCloture"));
    }

    @Test void activationDafExposeSonEtatEtRefuseUneDateDansLeCycle() throws Exception {
        int reference = rates.creerPeriodeReference(employee, date("2025-01-01"), date("2025-12-31"), null, c);
        try (var ps = c.prepareStatement("UPDATE conge_periode_reference SET statut='CLOTUREE',"
                + "taux_calcule_cloture=50000 WHERE id_periode_reference=?")) {
            ps.setInt(1, reference); ps.executeUpdate();
        }
        rate("2025-01-01", null, "40000");
        var avant = rates.listerPeriodesReference(employee, c).get(0);
        assertFalse(avant.containsKey("dateEffetActivation"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> rates.activerTauxCalcule(reference, date("2025-12-31"), null, c))
                .getMessage().contains("TCONGE_ACTIVATION_INVALID_DATE_EFFET"));
        int taux = rates.activerTauxCalcule(reference, date("2026-01-01"), null, c);
        assertEquals(taux, rates.activerTauxCalcule(reference, date("2026-01-01"), null, c));
        var apres = rates.listerPeriodesReference(employee, c).get(0);
        assertEquals("2026-01-01", apres.get("dateEffetActivation"));
        decimal("50000", (BigDecimal) apres.get("tauxActive"));
        decimal("40000", resolveRate("2025-12-31"));
        decimal("50000", resolveRate("2026-01-01"));
    }

    @Test void activationDafNeRetroagitPasSurUneAcquisitionDeJanvierCloturee() throws Exception {
        int reference = rates.creerPeriodeReference(employee, date("2025-01-01"), date("2025-12-31"), null, c);
        try (var ps = c.prepareStatement("UPDATE conge_periode_reference SET statut='CLOTUREE',"
                + "taux_calcule_cloture=50000 WHERE id_periode_reference=?")) {
            ps.setInt(1, reference); ps.executeUpdate();
        }
        rate("2025-01-01", null, "40000");
        try (var ps = c.prepareStatement("INSERT INTO mouvement_solde_conge "
                + "(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source) "
                + "VALUES (?,'ACQUISITION',2.5,'2026-01-25',125000)")) {
            ps.setInt(1, employee); ps.executeUpdate();
        }
        assertEquals("2026-01-25", rates.listerPeriodesReference(employee, c).get(0).get("derniereAcquisition"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> rates.activerTauxCalcule(reference, date("2026-01-01"), null, c))
                .getMessage().contains("TCONGE_ACTIVATION_RETROACTIVE"));
        rates.activerTauxCalcule(reference, date("2026-01-26"), null, c);
        decimal("40000", resolveRate("2026-01-25"));
        decimal("50000", resolveRate("2026-01-26"));
    }

    @Test void situationCongesUtiliseLeSalaireDuContexteEmploiADate() throws Exception {
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?", assignment);
        CongeTestFixtureHelper.createAffectation(c, employee, contract,
                date("2026-01-01"), new BigDecimal("450000"));
        decimal("300000", rates.obtenirSituationComplete(employee, date("2025-12-31"), c).getSalaireBase());
        decimal("450000", rates.obtenirSituationComplete(employee, date("2026-01-01"), c).getSalaireBase());
    }

    @Test void saisiePointageUtiliseLeSiteReelAuPassageAnnee() throws Exception {
        int siteJanvier = temporalSite("PTG");
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?", assignment);
        int nouvelleAffectation = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                date("2026-01-01"), new BigDecimal("400000"));
        try (var ps = c.prepareStatement("UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1, siteJanvier);
            ps.setInt(2, nouvelleAffectation);
            ps.executeUpdate();
        }
        var pointage = new PointageService();
        pointage.verifierEligibilitePointage(c, employee, 1, Date.valueOf("2025-12-31"));
        pointage.verifierEligibilitePointage(c, employee, siteJanvier, Date.valueOf("2026-01-01"));
        var sites = employmentContext.resolveSitesByDay(employee, date("2025-12-31"), date("2026-01-02"), c);
        assertEquals(1, sites.get(date("2025-12-31")));
        assertEquals(siteJanvier, sites.get(date("2026-01-01")));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> pointage.verifierEligibilitePointage(c, employee, 1, Date.valueOf("2026-01-01")))
                .getMessage().contains("n'est pas rattaché"));
    }

    @Test void contexteHistoriqueConserveLeContratCloture() throws Exception {
        update("UPDATE contrat_travail SET date_fin='2025-12-31', id_statut_rh=7 WHERE id_contrat=?", contract);
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31', id_statut_rh=6 "
                + "WHERE id_affectation=?", assignment);
        var decembre = employmentContext.resolveContext(employee, date("2025-12-31"), c);
        assertEquals(contract, decembre.idContrat());
        assertEquals(assignment, decembre.idAffectation());
        assertTrue(decembre.estEnPoste());
        assertNull(employmentContext.resolveContext(employee, date("2026-01-01"), c).idContrat());
    }

    @Test void sitesJournaliersRefusentDeuxAffectationsChevauchantes() throws Exception {
        CongeTestFixtureHelper.createAffectation(c, employee, contract,
                date("2026-01-01"), new BigDecimal("400000"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> employmentContext.resolveSitesByDay(employee,
                        date("2026-01-01"), date("2026-01-02"), c))
                .getMessage().contains("AMBIGUOUS_CONFIGURATION"));
    }

    @Test void dispensePointageSuitLaFonctionDeChaqueJour() throws Exception {
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?", assignment);
        int janvier = CongeTestFixtureHelper.createAffectation(c, employee, contract,
                date("2026-01-01"), new BigDecimal("400000"));
        update("UPDATE employe_affectation_carriere SET id_fonction=1 WHERE id_affectation=?", janvier);
        var jours = employmentContext.resolvePointageDays(employee,
                date("2025-12-31"), date("2026-01-01"), c);
        assertFalse(jours.get(date("2025-12-31")).dispensePointage());
        assertTrue(jours.get(date("2026-01-01")).dispensePointage());
    }

    @Test void referenceGlissanteHistoriqueBloqueRenouvellementAutomatiqueSansAlteration() throws Exception {
        int legacy = rates.creerPeriodeReference(employee, date("2025-12-26"), date("2026-12-25"), null, c);
        var error = assertThrows(IllegalStateException.class,
                () -> rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c));
        assertTrue(error.getMessage().contains("TCONGE_REFERENCE_ANNUAL_CONFLICT"));
        assertEquals(legacy, rates.resoudrePeriodeReferenceStrict(employee, date("2026-01-25"), c).getIdPeriodeReference());
        try (var ps = c.prepareStatement("SELECT count(*) FROM conge_periode_reference WHERE id_employe=?")) {
            ps.setInt(1, employee);
            try (var rs = ps.executeQuery()) { rs.next(); assertEquals(1, rs.getInt(1)); }
        }
    }

    @Test void tauxChangeAuPremierJanvierSansTrou() throws Exception {
        rate("2025-01-01", "2026-01-01", "10000"); // fin technique exclusive du TCONGE
        rate("2026-01-01", null, "12000");
        decimal("10000", resolveRate("2025-12-31")); decimal("12000", resolveRate("2026-01-01"));
        decimal("12000", resolveRate("2026-01-25"));
    }
    @Test void tauxInchangePeutTraverserDeuxAnnees() throws Exception {
        rate("2025-01-01", null, "10000");
        decimal("10000", resolveRate("2025-12-31")); decimal("10000", resolveRate("2026-01-25"));
    }
    @Test void trouTauxNeReprendJamaisLaDerniereValeurNiBase30() throws Exception {
        rate("2025-01-01", "2026-01-01", "10000");
        var error = assertThrows(IllegalStateException.class, () -> rates.resoudreTauxCongeApplicable(
                employee, date("2026-01-01"), new BigDecimal("300000"), c));
        assertTrue(error.getMessage().contains("2026-01-01"));
    }
    @Test void tauxChevauchantsSontAmbigus() throws Exception {
        rate("2025-01-01", null, "10000"); rate("2026-01-01", null, "12000");
        assertTrue(assertThrows(IllegalStateException.class, () -> resolveRate("2026-01-01"))
                .getMessage().contains("AMBIGUOUS_CONFIGURATION"));
    }
    @Test void referencesChevauchantesSontAmbigues() throws Exception {
        rates.creerPeriodeReference(employee, date("2026-01-01"), date("2026-12-31"), null, c);
        try (var ps = c.prepareStatement("INSERT INTO conge_periode_reference(id_employe,date_debut,date_fin) VALUES (?, '2026-01-01','2026-12-31')")) {
            ps.setInt(1, employee);
            assertEquals("23P01", assertThrows(SQLException.class, ps::executeUpdate).getSQLState());
        }
    }
    @Test void incidentReference2025ExpireeTransitionExplicitePreserveTauxEtHistorique() throws Exception {
        rates.creerPeriodeReference(employee, date("2025-01-01"), date("2025-12-31"), null, c);
        rate("2025-01-01", null, "10000");
        int december = bulletin("2025-11-26", "2025-12-25");
        payroll.mettreAJourJournalConge(december, c); // clôt le douzième mois, cumuls figés
        int january = bulletin("2025-12-26", "2026-01-25");
        payroll.mettreAJourJournalConge(january, c);
        int next = rates.resoudrePeriodeReferenceStrict(employee, date("2026-01-25"), c).getIdPeriodeReference();
        assertEquals(date("2026-01-01"), rates.resoudrePeriodeReferenceStrict(employee, date("2026-01-25"), c).getDateDebut());
        assertEquals(date("2026-12-31"), rates.resoudrePeriodeReferenceStrict(employee, date("2026-01-25"), c).getDateFin());
        assertEquals(next, rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c));
        decimal("10000", resolveRate("2026-01-25"));
        try (var ps = c.prepareStatement("SELECT COUNT(*),SUM(jours_droit_acquis) FROM conge_journal_droit WHERE id_employe=?")) {
            ps.setInt(1, employee); try (var rs = ps.executeQuery()) {
                assertTrue(rs.next()); assertEquals(2, rs.getInt(1)); decimal("5.00", rs.getBigDecimal(2));
            }
        }
        assertEquals(next, rates.ensureLeaveReferencePeriod(employee, date("2026-01-25"), c));
    }

    @Test void entreeDecembreAcquisitionJanvierProratisee() throws Exception {
        update("UPDATE contrat_travail SET date_debut='2025-12-30' WHERE id_contrat=?", contract);
        update("UPDATE employe_affectation_carriere SET date_debut='2025-12-30' WHERE id_affectation=?", assignment);
        rates.creerPeriodeReference(employee, date("2026-01-01"), date("2026-12-31"), null, c);
        var coverage = employmentContext.resolveCoverage(employee, date("2025-12-26"), date("2026-01-25"), c);
        assertEquals(27, coverage.days());
        decimal("2.1774", leave.enregistrerAcquisitionMensuelle(employee, 2026, 1,
                date("2025-12-26"), date("2026-01-25"), BigDecimal.ZERO, null, c));
    }
    @Test void changementSalaireAuPremierJanvierNeSimulePasUneEntree() throws Exception {
        changeAssignment();
        var coverage = employmentContext.resolveCoverage(employee, date("2025-12-26"), date("2026-01-25"), c);
        assertEquals(31, coverage.days()); assertEquals(date("2025-12-26"), coverage.firstDay());
        decimal("400000", rates.obtenirSituationComplete(employee, date("2026-01-01"), c).getSalaireBase());
        decimal("300000", rates.obtenirSituationComplete(employee, date("2025-12-31"), c).getSalaireBase());
    }
    @Test void changementSiteAuPremierJanvierSansChevauchement() throws Exception {
        int next = changeAssignment();
        int newSite;
        try (var ps = c.prepareStatement("INSERT INTO ref_site(code_site,libelle) VALUES (?, 'Temporal site') RETURNING id_site")) {
            ps.setString(1, "TS" + employee); try (var rs=ps.executeQuery()) { rs.next(); newSite=rs.getInt(1); }
        }
        try (var ps=c.prepareStatement("UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1,newSite); ps.setInt(2,next); ps.executeUpdate();
        }
        assertEquals(31, employmentContext.resolveCoverage(employee, date("2025-12-26"), date("2026-01-25"), c).days());
        try (var ps=c.prepareStatement("SELECT id_site FROM employe_affectation_carriere WHERE id_employe=? AND date_debut<=? AND (date_fin IS NULL OR date_fin>=?)")) {
            ps.setInt(1,employee); ps.setDate(2,Date.valueOf("2026-01-01")); ps.setDate(3,Date.valueOf("2026-01-01"));
            try (var rs=ps.executeQuery()) { assertTrue(rs.next()); assertEquals(newSite,rs.getInt(1)); assertFalse(rs.next()); }
        }
    }
    @Test void trouParametreNeChargePasUneVersionHorsValidite() throws Exception {
        parameter("TEMPORAL_TEST", "2025-01-01", "2025-12-31");
        assertFalse(ParametreGlobal.loadActiveMapAtDate(Date.valueOf("2026-01-01"),c).containsKey("TEMPORAL_TEST"));
        assertFalse(ParametreGlobal.loadEtatMapAtDate(Date.valueOf("2026-01-01"),c).containsKey("TEMPORAL_TEST"));
        var error=assertThrows(IllegalStateException.class,()->ParametreGlobal.requirePositiveInt(
                ParametreGlobal.loadActiveMapAtDate(Date.valueOf("2026-01-01"),c),"TEMPORAL_TEST"));
        assertTrue(error.getMessage().contains("2026-01-01")); assertTrue(error.getMessage().contains("2025-12-31"));
    }
    @Test void parametresChevauchantsSontRejetesParPostgres() throws Exception {
        parameter("TEMPORAL_TEST", "2025-01-01", null);
        java.sql.SQLException overlap = assertThrows(java.sql.SQLException.class,
                () -> parameter("TEMPORAL_TEST", "2026-01-01", null));
        assertEquals("23P01", overlap.getSQLState(), "PostgreSQL doit refuser les périodes de paramètres qui se chevauchent.");
    }
    @Test void referenceBissextileRespectePlusMonths() throws Exception {
        assertEquals(date("2025-02-27"), CongeTauxApplicableService.calculerDateFinReference(date("2024-02-29")));
        rates.validerDureeReference(date("2024-02-29"),date("2025-02-27"),c);
    }
    @Test void priseDecembreNePeutConsommerUneAcquisitionDeJanvier() throws Exception {
        leave.enregistrerAcquisitionMensuelle(employee,2026,1,date("2025-12-26"),date("2026-01-25"),BigDecimal.ZERO,null,c);
        var situation = new CongeLotValorisationService().situation(employee,date("2025-12-31"),c);
        assertEquals(0, situation.soldeJours().signum());
    }

    @Test void populationAccepteLesMutationsSuccessives() throws Exception {
        changeAssignment();
        int period=bulletin("2025-12-26","2026-01-25");
        var population=new PayrollPopulationResolver().resolve(period,c);
        var member=population.members().stream().filter(m -> m.employeeId()==employee).findFirst().orElseThrow();
        assertEquals("INCLUDED",member.decision());
    }
    @Test void affectationsChevauchantesRestentBloquantes() throws Exception {
        CongeTestFixtureHelper.createAffectation(c,employee,contract,date("2026-01-01"),new BigDecimal("400000"));
        assertTrue(assertThrows(IllegalStateException.class, () -> employmentContext.resolveCoverage(
                employee,date("2025-12-26"),date("2026-01-25"),c)).getMessage().contains("2026-01-01"));
    }
    @Test void inputPaieMutationJanvierVentileSalaireSansSimulerEntree() throws Exception {
        changeAssignment();
        int period=bulletin("2025-12-26","2026-01-25");
        snapshotVide(period);
        var input=payroll.preparerInput(employee,period,true,c);
        var emploiJanvier=employmentContext.resolveContext(employee,date("2026-01-25"),c);
        assertEquals(emploiJanvier.codeSite(),input.getCodeSite());
        assertEquals(emploiJanvier.categorieProCode(),input.getCategorieProfessionnelle());
        assertEquals(emploiJanvier.modeCalculPaieCode(),input.getModeCalculPaie());
        assertEquals(emploiJanvier.cinNumero(),input.getCinNumero());
        decimal("400000",input.getSalaireBaseContractuel());
        assertEquals(12,input.getAncienneteMois());
        assertFalse(input.isEntreeSortieEnCoursDeMois());
        decimal("380645.16",new mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine()
                .calculerSalaireBase(input,new mg.galaxam.sirh.paie.persistence.BulletinPaie(),new java.util.ArrayList<>()));
    }

    @Test void preparationStcUtiliseLeContexteEmploiDeLaDateDeDepart() throws Exception {
        changeAssignment();
        var contexte=employmentContext.resolveContext(employee,date("2026-01-25"),c);
        var stc=new StcService().preparerStc(employee,Date.valueOf("2026-01-25"),"DEMISSION",c);
        assertEquals(contexte.codeSite(),stc.getCodeSite());
        assertEquals(contexte.categorieProCode(),stc.getCategorieProfessionnelle());
        decimal("400000",stc.getSalaireBase());
    }

    @Test void exportDntsMensuelUtiliseLesTauxSociauxDeLaFinDeCampagne() throws Exception {
        int period=bulletin("2025-12-26","2026-01-25");
        var rates=new mg.galaxam.sirh.paieconfig.application.SocialContributionContextResolver()
                .companyRates(date("2026-01-25"),c);
        String csv=new mg.galaxam.sirh.paie.application.export.DntsExportService()
                .exporterDntsCsv(period,c);
        assertTrue(csv.contains(rates.cnapsEmployee().movePointRight(2).stripTrailingZeros().toPlainString()+"%"));
        assertTrue(csv.contains(rates.fmfpEmployer().movePointRight(2).stripTrailingZeros().toPlainString()+"%"));
    }

    @Test void exportsDntsEtSafiBornentLaDateDeDebauchageALaCampagne() throws Exception {
        int period=bulletin("2025-12-26","2026-01-25");
        update("UPDATE contrat_travail SET date_fin='2025-12-31' WHERE id_contrat=?",contract);
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?",assignment);
        var dnts=new mg.galaxam.sirh.paie.application.export.DntsExportService();
        var safi=new mg.galaxam.sirh.paie.application.export.SafiExportService();
        assertEquals("31/12/2025",dnts.genererDonneesDnts(period,c).get(0).getDateDebauchage());
        assertEquals("31/12/2025",safi.genererDonneesSafi(period,c).get(0).getDateDebauchage());

        int nouveauContrat=CongeTestFixtureHelper.createContratActif(c,employee,date("2026-01-01"));
        CongeTestFixtureHelper.createAffectation(c,employee,nouveauContrat,date("2026-01-01"),new BigDecimal("400000"));
        assertEquals("",dnts.genererDonneesDnts(period,c).get(0).getDateDebauchage());
        assertEquals("",safi.genererDonneesSafi(period,c).get(0).getDateDebauchage());
    }

    @Test void identiteEmployeurExportSuitLeContratDansLaCampagneSansPrendreUnContratFutur() throws Exception {
        var resolver=new mg.galaxam.sirh.contrat.application.EmployerIdentityContextResolver();
        var oldEmployer=resolver.resolve(employee,date("2025-12-26"),date("2026-01-25"),c);
        int futureContract=CongeTestFixtureHelper.createContratActif(c,employee,date("2026-02-01"));
        int futureEmployer;
        try (var ps=c.prepareStatement("INSERT INTO ref_filiale(code_filiale,raison_sociale,nif) "
                + "VALUES (?,?,?) RETURNING id_filiale")) {
            ps.setString(1,"TF"+employee);ps.setString(2,"Employeur futur " + employee);
            ps.setString(3,"NIF"+employee);
            try(var rs=ps.executeQuery()){rs.next();futureEmployer=rs.getInt(1);}
        }
        try(var ps=c.prepareStatement("UPDATE contrat_travail SET id_filiale=? WHERE id_contrat=?")) {
            ps.setInt(1,futureEmployer);ps.setInt(2,futureContract);ps.executeUpdate();
        }
        assertEquals(oldEmployer.id(),resolver.resolve(employee,date("2025-12-26"),date("2026-01-25"),c).id());
        int period=bulletin("2025-12-26","2026-01-25");
        String csv=new mg.galaxam.sirh.paie.application.export.DntsExportService().exporterDntsCsv(period,c);
        assertTrue(csv.contains(oldEmployer.name()));
        assertFalse(csv.contains("Employeur futur " + employee));
        var header=new mg.galaxam.sirh.paie.persistence.BulletinPaie();
        header.setIdEmploye(employee);header.setIdPeriodePaie(period);
        var document=new mg.galaxam.sirh.paie.application.export.BulletinDocumentService()
                .context(new mg.galaxam.sirh.paie.domain.dto.BulletinPaieComplet(header,java.util.List.of()),c);
        assertEquals(oldEmployer.name(),document.entreprise());
    }

    @Test void exportSansBulletinNeDevinePasLaFilialeEmployeur() throws Exception {
        int period=CongeTestFixtureHelper.createPeriodePaie(c,"TY"+System.nanoTime(),
                date("2025-12-26"),date("2026-01-25"),"BROUILLON");
        assertTrue(assertThrows(IllegalStateException.class,
                () -> new mg.galaxam.sirh.paie.application.export.DntsExportService()
                        .exporterDntsCsv(period,c)).getMessage().contains("aucun bulletin"));
    }

    @Test void ancienneteAuVingtCinqJanvierNInclutPasLaFinFutureDuContrat() throws Exception {
        update("UPDATE contrat_travail SET date_fin='2026-12-31' WHERE id_contrat=?", contract);
        int period=bulletin("2025-12-26","2026-01-25");
        snapshotVide(period);
        assertEquals(12,payroll.preparerInput(employee,period,true,c).getAncienneteMois());
    }

    @Test void salaireInchangeVautUnMoisCompletMalgreLesJoursDeCampagne() throws Exception {
        int period=bulletin("2026-01-26","2026-02-25");
        snapshotVide(period);
        var input=payroll.preparerInput(employee,period,true,c);
        decimal("300000",input.getSalaireBaseCampagne());
        assertFalse(input.isEntreeSortieEnCoursDeMois());
    }

    @Test void salaireApplicableDesLeVingtSixCouvreTouteLaCampagne() throws Exception {
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-25' WHERE id_affectation=?",assignment);
        CongeTestFixtureHelper.createAffectation(c,employee,contract,date("2025-12-26"),new BigDecimal("400000"));
        int period=bulletin("2025-12-26","2026-01-25");
        snapshotVide(period);
        decimal("400000",payroll.preparerInput(employee,period,true,c).getSalaireBaseCampagne());
    }

    @Test void entreeEnCoursDeCampagneConserveLeProrataGalaxamSurTrente() throws Exception {
        update("UPDATE contrat_travail SET date_debut='2025-12-30' WHERE id_contrat=?",contract);
        update("UPDATE employe_affectation_carriere SET date_debut='2025-12-30' WHERE id_affectation=?",assignment);
        int period=bulletin("2025-12-26","2026-01-25");
        snapshotVide(period);
        var input=payroll.preparerInput(employee,period,true,c);
        assertTrue(input.isEntreeSortieEnCoursDeMois());
        decimal("270000",input.getSalaireBaseCampagne());
    }

    @Test void changementSalaireLeDixJanvierVentileAuJourExact() throws Exception {
        update("UPDATE employe_affectation_carriere SET date_fin='2026-01-09' WHERE id_affectation=?",assignment);
        CongeTestFixtureHelper.createAffectation(c,employee,contract,date("2026-01-10"),new BigDecimal("400000"));
        int period=bulletin("2025-12-26","2026-01-25");
        snapshotVide(period);
        var input=payroll.preparerInput(employee,period,true,c);
        decimal("351612.90",input.getSalaireBaseCampagne());
    }

    @Test void absenceNonDateeAvecDeuxSalairesBloquePlutotQueChoisirLeDernierTaux() throws Exception {
        changeAssignment();
        int period=bulletin("2025-12-26","2026-01-25");
        try(var ps=c.prepareStatement("INSERT INTO pointage_cumul_periode"
                + "(id_employe,id_periode_paie,nb_jours_ouvrables,heures_absence_retenues) VALUES (?,?,22,1)")) {
            ps.setInt(1,employee);ps.setInt(2,period);ps.executeUpdate();
        }
        var input=payroll.preparerInput(employee,period,true,c);
        var error=assertThrows(mg.galaxam.sirh.common.exception.SirhBusinessException.class,
                ()->new mg.galaxam.sirh.paie.domain.engine.PayrollExecutionEngine().calculerRetenueAbsence(
                        input,input.getSalaireBaseCampagne(),input.getTauxHoraireEffectif(),
                        new mg.galaxam.sirh.paie.persistence.BulletinPaie(),new java.util.ArrayList<>()));
        assertTrue(error.getMessage().contains("PAYROLL_ABSENCE_SALARY_SPLIT_UNRESOLVED"));
    }

    @Test void organismeSanteResoluALaClotureEtAuDepart() throws Exception {
        String code="HS"+employee;
        int site;
        try(var ps=c.prepareStatement("INSERT INTO ref_site(code_site,libelle) VALUES (?,?) RETURNING id_site")) {
            ps.setString(1,code);ps.setString(2,code);try(var rs=ps.executeQuery()){rs.next();site=rs.getInt(1);}
        }
        for (int version=0;version<2;version++) {
            int rateId;
            try(var ps=c.prepareStatement("INSERT INTO ref_type_cotisation(code,libelle,categorie,taux_salarial,taux_patronal,date_debut,date_fin) "
                    + "VALUES (?,?,'SANTE',?,?,?,?) RETURNING id_type_cotisation")) {
                ps.setString(1,code+version);ps.setString(2,code);ps.setBigDecimal(3,BigDecimal.valueOf(version+1));
                ps.setBigDecimal(4,BigDecimal.valueOf(version+2));ps.setDate(5,Date.valueOf(version==0?"2025-01-01":"2026-01-01"));
                ps.setDate(6,version==0?Date.valueOf("2025-12-31"):null);
                try(var rs=ps.executeQuery()){rs.next();rateId=rs.getInt(1);}
            }
            try(var ps=c.prepareStatement("INSERT INTO site_cotisation(id_site,id_type_cotisation) VALUES (?,?)")) {
                ps.setInt(1,site);ps.setInt(2,rateId);ps.executeUpdate();
            }
        }
        var input=new mg.galaxam.sirh.paie.domain.dto.PayrollCalculationInput();
        input.setIdEmploye(employee);input.setCodeSite(code);input.setNomSite(code);input.setSoumisSante(true);
        var resolver=new PayrollLegalContextResolver();
        ReflectionTestUtils.invokeMethod(resolver,"resolveHealthContribution",input,date("2025-12-31"),c);
        decimal("0.01",input.getTauxSanteSalarialDecimal());
        ReflectionTestUtils.invokeMethod(resolver,"resolveHealthContribution",input,date("2026-01-25"),c);
        decimal("0.02",input.getTauxSanteSalarialDecimal());
        Object stc=ReflectionTestUtils.invokeMethod(new StcService(),"resolveHealthContributionRate",code,code,date("2026-01-01"),c);
        decimal("2",ReflectionTestUtils.invokeMethod(stc,"employeeRatePercent"));
    }

    @Test void tauxJanvierNeRevalorisePasLeStockDecembre() throws Exception {
        rate("2025-01-01","2026-01-01","10000");rate("2026-01-01",null,"12000");
        try(var ps=c.prepareStatement("INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source) "
                + "VALUES (?,'AJUSTEMENT',2,'2025-12-15',20000)")) {
            ps.setInt(1,employee);ps.executeUpdate();
        }
        decimal("20000",new CongeLotValorisationService().situation(employee,date("2026-01-25"),c).soldeValorisation());
    }

    @Test void priseDecembreEtAcquisitionJanvierGardentLeursLotsHistoriques() throws Exception {
        rate("2025-01-01","2026-01-01","10000");rate("2026-01-01",null,"12000");
        try(var ps=c.prepareStatement("INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source) "
                + "VALUES (?,'AJUSTEMENT',3,'2025-12-15',30000),(?,'PRISE',-1,'2025-12-31',NULL)")) {
            ps.setInt(1,employee);ps.setInt(2,employee);ps.executeUpdate();
        }
        rates.creerPeriodeReference(employee,date("2026-01-01"),date("2026-12-31"),null,c);
        payroll.mettreAJourJournalConge(bulletin("2025-12-26","2026-01-25"),c);
        var lots=new CongeLotValorisationService();
        decimal("20000",lots.situation(employee,date("2025-12-31"),c).soldeValorisation());
        decimal("45000",lots.situation(employee,date("2026-01-25"),c).soldeValorisation());
        decimal("4.5",lots.situation(employee,date("2026-01-25"),c).soldeJours());
    }
    @Test void cutoverApresClotureDansLeMemeMoisNeDoublePasAcquisition() throws Exception {
        try(var ps=c.prepareStatement("INSERT INTO mouvement_solde_conge(id_employe,type_mouvement,nb_jours,date_mouvement,valeur_source,commentaire) "
                + "VALUES (?,'AJUSTEMENT',3,'2026-01-26',30000,'[SOLDE_INITIAL] Reprise arrêtée')")) {
            ps.setInt(1,employee);ps.executeUpdate();
        }
        decimal("0",leave.enregistrerAcquisitionMensuelle(employee,2026,1,date("2025-12-26"),date("2026-01-25"),BigDecimal.ZERO,null,c));
    }

    @Test void cutoverRefuseDesCumulsAnnuelsContradictoiresAvantTouteEcriture() throws Exception {
        var reprise = new CongeTauxApplicableService.CutoverOpeningRequest();
        reprise.setIdEmploye(employee);
        reprise.setDateCutover(date("2026-09-25"));
        reprise.setReferenceArrete("DAF-TEST-TEMPORAL");
        reprise.setDateDebutReference(date("2026-01-01"));
        reprise.setSoldeJours(new BigDecimal("14.5"));
        reprise.setValeurSoldeOuverture(new BigDecimal("240000"));
        reprise.setTauxJournalierApplicable(new BigDecimal("16000"));
        reprise.setCumulDroitsOuverture(BigDecimal.ZERO);
        reprise.setCumulValorisationOuverture(new BigDecimal("315000"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> rates.enregistrerCutoverSnapshot(reprise, null, c))
                .getMessage().contains("CONGE_CUTOVER_ACCUMULATORS_INCONSISTENT"));
        try (var ps = c.prepareStatement("SELECT COUNT(*) FROM conge_periode_reference WHERE id_employe=?")) {
            ps.setInt(1, employee);
            try (var rs = ps.executeQuery()) { assertTrue(rs.next()); assertEquals(0, rs.getInt(1)); }
        }
    }

    @Test void cutoverExigeEtHistoriseLaReferenceDeLArreteDaf() throws Exception {
        var reprise = new CongeTauxApplicableService.CutoverOpeningRequest();
        reprise.setIdEmploye(employee);
        reprise.setDateCutover(date("2026-09-25"));
        reprise.setDateDebutReference(date("2026-01-01"));
        reprise.setSoldeJours(new BigDecimal("14.5"));
        reprise.setValeurSoldeOuverture(new BigDecimal("240000"));
        reprise.setTauxJournalierApplicable(new BigDecimal("16000"));
        reprise.setCumulDroitsOuverture(new BigDecimal("22.5"));
        reprise.setCumulValorisationOuverture(new BigDecimal("315000"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> rates.enregistrerCutoverSnapshot(reprise, null, c))
                .getMessage().contains("CONGE_CUTOVER_SOURCE_REQUIRED"));
        reprise.setReferenceArrete("DAF-RH-2026-14");
        rates.enregistrerCutoverSnapshot(reprise, null, c);
        try (var ps = c.prepareStatement("SELECT t.commentaire, m.commentaire FROM conge_taux_applicable t "
                + "JOIN mouvement_solde_conge m ON m.id_employe=t.id_employe "
                + "WHERE t.id_employe=? AND t.source_provenance='REPRISE_CUTOVER'")) {
            ps.setInt(1, employee);
            try (var rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertTrue(rs.getString(1).contains("DAF-RH-2026-14"));
                assertTrue(rs.getString(2).contains("DAF-RH-2026-14"));
            }
        }
    }

    @Test void configurationJournalierePointageDetecteChevauchement() throws Exception {
        int allowance;
        try(var ps=c.prepareStatement("INSERT INTO ref_indemnite(code,libelle,imposable_irsa,soumis_cotisation,fractionnable) "
                + "VALUES (?,'Temporal',FALSE,FALSE,FALSE) RETURNING id_indemnite")) {
            ps.setString(1,"PT"+employee);try(var rs=ps.executeQuery()){rs.next();allowance=rs.getInt(1);}
        }
        try(var ps=c.prepareStatement("INSERT INTO site_indemnite_config(id_site,id_indemnite,periodicite,montant_reference,date_debut) "
                + "VALUES (1,?,'FIXE_MENSUEL',30000,'2025-01-01'),(1,?,'FIXE_MENSUEL',40000,'2026-01-01')")) {
            ps.setInt(1,allowance);ps.setInt(2,allowance);
            assertEquals("23P01", assertThrows(SQLException.class, ps::executeUpdate).getSQLState());
        }
    }

    @Test void pointageAutonomeUtiliseTarifEtSiteDeChaqueJour() throws Exception {
        // Isoler le changement de site/tarif des jours fériés du référentiel de test.
        try (var ps = c.prepareStatement("UPDATE ref_calendrier_ferie SET est_chome=FALSE "
                + "WHERE date_ferie BETWEEN DATE '2025-12-26' AND DATE '2026-01-25'")) {
            ps.executeUpdate();
        }
        int firstSite=temporalSite("DA"),secondSite=temporalSite("DB");
        update("UPDATE employe_affectation_carriere SET id_fonction=1 WHERE id_affectation=?", assignment);
        try(var ps=c.prepareStatement("UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1,firstSite);ps.setInt(2,assignment);ps.executeUpdate();
        }
        int next=changeAssignment();
        update("UPDATE employe_affectation_carriere SET id_fonction=1 WHERE id_affectation=?", next);
        try(var ps=c.prepareStatement("UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1,secondSite);ps.setInt(2,next);ps.executeUpdate();
        }
        try(var ps=c.prepareStatement("INSERT INTO site_indemnite_config(id_site,id_indemnite,periodicite,montant_reference,date_debut,date_fin) "
                + "SELECT ?,id_indemnite,'JOURNALIER',1000,DATE '2025-01-01',DATE '2025-12-31' FROM ref_indemnite WHERE code='REPAS' "
                + "UNION ALL SELECT ?,id_indemnite,'JOURNALIER',2000,DATE '2026-01-01',NULL FROM ref_indemnite WHERE code='REPAS'")) {
            ps.setInt(1,firstSite);ps.setInt(2,secondSite);assertEquals(2,ps.executeUpdate());
        }
        int period=bulletin("2025-12-26","2026-01-25");
        var sitesAutonomes = employmentContext.resolveSitesByDay(employee,
                date("2025-12-26"), date("2026-01-25"), c);
        assertEquals(firstSite, sitesAutonomes.get(date("2025-12-26")));
        assertEquals(secondSite, sitesAutonomes.get(date("2026-01-02")));
        var summary=new mg.galaxam.sirh.pointage.application.PointageSyntheseService().calculerSyntheseEmploye(
                employee,"TEST","Temporal","Test",null,firstSite,"DA","DA","AGENT",false,true,
                date("2025-12-26"),date("2026-01-25"),period,"TEST","TEST","EN_COURS",21,null,java.util.Map.of(),c);
        decimal("38000",summary.getMontantRepas()); // 4 jours décembre × 1000 + 17 jours janvier × 2000
    }

    @Test void syntheseActiveLaDispenseSeulementApresChangementDeFonction() throws Exception {
        try (var ps = c.prepareStatement("UPDATE ref_calendrier_ferie SET est_chome=FALSE "
                + "WHERE date_ferie BETWEEN DATE '2025-12-26' AND DATE '2026-01-25'")) {
            ps.executeUpdate();
        }
        int siteJanvier = temporalSite("AUT");
        int janvier = changeAssignment();
        try (var ps = c.prepareStatement("UPDATE employe_affectation_carriere "
                + "SET id_site=?, id_fonction=1 WHERE id_affectation=?")) {
            ps.setInt(1, siteJanvier); ps.setInt(2, janvier); ps.executeUpdate();
        }
        try (var ps = c.prepareStatement("INSERT INTO site_indemnite_config "
                + "(id_site,id_indemnite,periodicite,montant_reference,date_debut) "
                + "SELECT ?,id_indemnite,'JOURNALIER',2000,DATE '2026-01-01' "
                + "FROM ref_indemnite WHERE code='REPAS'")) {
            ps.setInt(1, siteJanvier); assertEquals(1, ps.executeUpdate());
        }
        int period = bulletin("2025-12-26", "2026-01-25");
        var summary = new mg.galaxam.sirh.pointage.application.PointageSyntheseService().calculerSyntheseEmploye(
                employee, "TEST", "Temporal", "Test", null, 1, "TNR", "TNR", "AGENT", false, false,
                date("2025-12-26"), date("2026-01-25"), period, "TEST", "TEST", "EN_COURS", 21,
                null, java.util.Map.of(), c);
        decimal("34000", summary.getMontantRepas());
        assertTrue(summary.isDispensePointage());
    }

    @Test void objectifHebdomadaireCouvreLesDeuxAffectationsAutourDuPremierJanvier() throws Exception {
        try (var ps = c.prepareStatement("UPDATE ref_calendrier_ferie SET est_chome=FALSE "
                + "WHERE date_ferie BETWEEN DATE '2025-12-26' AND DATE '2026-01-25'")) {
            ps.executeUpdate();
        }
        int janvier = changeAssignment();
        int nouveauSite = temporalSite("WK");
        try (var ps = c.prepareStatement("UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1, nouveauSite); ps.setInt(2, janvier); ps.executeUpdate();
        }
        int period = bulletin("2025-12-26", "2026-01-25");
        var summary = new mg.galaxam.sirh.pointage.application.PointageSyntheseService().calculerSyntheseEmploye(
                employee, "TEST", "Temporal", "Test", null, 1, "TNR", "TNR", "AGENT", false, false,
                date("2025-12-26"), date("2026-01-25"), period, "TEST", "TEST", "EN_COURS", 21,
                null, java.util.Map.of(), c);
        var transition = summary.getTronconsSemaine().stream()
                .filter(week -> "2025-12-29".equals(week.getDebutSemaine()))
                .findFirst().orElseThrow();
        // 29-31 décembre (ancienne affectation) et 1-2 janvier (nouvelle) : cinq jours requis.
        assertEquals(5, transition.getNbJoursTroncon());
        assertEquals(40.0, transition.getSeuilHeuresTroncon(), 0.001);
    }
    int temporalSite(String prefix) throws Exception {
        try(var ps=c.prepareStatement("INSERT INTO ref_site(code_site,libelle) VALUES (?,?) RETURNING id_site")) {
            ps.setString(1,prefix+employee);ps.setString(2,prefix+employee);
            try(var rs=ps.executeQuery()){rs.next();return rs.getInt(1);}
        }
    }

    @Test void finContratDecembreDansCampagneJanvierNeDoublePasLeCredit() throws Exception {
        update("UPDATE contrat_travail SET date_fin='2025-12-31' WHERE id_contrat=?",contract);
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?",assignment);
        rates.creerPeriodeReference(employee,date("2026-01-01"),date("2026-12-31"),null,c);
        int period=bulletin("2025-12-26","2026-01-25");
        payroll.mettreAJourJournalConge(period,c);payroll.mettreAJourJournalConge(period,c);
        try(var ps=c.prepareStatement("SELECT COUNT(*),MIN(date_mouvement),MIN(valeur_source) FROM mouvement_solde_conge "
                + "WHERE id_employe=? AND type_mouvement='ACQUISITION'")) {
            ps.setInt(1,employee);try(var rs=ps.executeQuery()) {
                rs.next();assertEquals(1,rs.getInt(1));assertEquals(date("2025-12-31"),rs.getDate(2).toLocalDate());
                assertNotNull(rs.getBigDecimal(3));
            }
        }
    }
    @Test void projectionDecembreNeComprendPasLesDroitsCloturesEnJanvier() throws Exception {
        rates.creerPeriodeReference(employee,date("2025-01-01"),date("2025-12-31"),null,c);
        rates.creerPeriodeReference(employee,date("2026-01-01"),date("2026-12-31"),null,c);
        payroll.mettreAJourJournalConge(bulletin("2025-12-26","2026-01-25"),c);
        var december=rates.obtenirSituationComplete(employee,date("2025-12-31"),c);
        decimal("0",december.getSoldeJours());decimal("0",december.getPeriodeReference().getCumulDroitsPostCutover());
        var january=rates.obtenirSituationComplete(employee,date("2026-01-25"),c);
        decimal("2.5",january.getSoldeJours());decimal("2.5",january.getPeriodeReference().getCumulDroitsPostCutover());
    }

    int changeAssignment() throws Exception {
        update("UPDATE employe_affectation_carriere SET date_fin='2025-12-31' WHERE id_affectation=?",assignment);
        return CongeTestFixtureHelper.createAffectation(c,employee,contract,date("2026-01-01"),new BigDecimal("400000"));
    }
    void snapshotVide(int period) throws Exception {
        try(var ps=c.prepareStatement("INSERT INTO pointage_cumul_periode"
                + "(id_employe,id_periode_paie,nb_jours_ouvrables) VALUES (?,?,22)")) {
            ps.setInt(1,employee);ps.setInt(2,period);ps.executeUpdate();
        }
    }
    void update(String sql,int id) throws Exception { try(var ps=c.prepareStatement(sql)){ps.setInt(1,id);ps.executeUpdate();} }
    void decimal(String value,BigDecimal actual){ assertEquals(0,new BigDecimal(value).compareTo(actual)); }
    BigDecimal resolveRate(String day){ return rates.resoudreTauxCongeApplicable(employee,date(day),null,c).getTauxJournalier(); }
    void rate(String start,String end,String value) throws Exception {
        CongeTestFixtureHelper.creerTcongeApplicable(c,employee,new BigDecimal(value),date(start),end==null?null:date(end),"KAJY_MIGRATION");
    }
    void parameter(String code,String start,String end) throws Exception {
        try(var ps=c.prepareStatement("INSERT INTO parametre_global(code_parametre,valeur,date_debut,date_fin,categorie) VALUES (?, '30', ?, ?, 'CONGE')")){
            ps.setString(1,code);ps.setDate(2,Date.valueOf(start));ps.setDate(3,end==null?null:Date.valueOf(end));ps.executeUpdate();
        }
    }
    int bulletin(String start,String end) throws Exception {
        int period=CongeTestFixtureHelper.createPeriodePaie(c,"TY"+System.nanoTime(),date(start),date(end),"BROUILLON");
        try(var ps=c.prepareStatement("INSERT INTO bulletin_paie(id_periode_paie,id_employe,reference_bulletin,matricule,nom_prenom,intitule_poste,nom_site,regime_intervenant,salaire_base,taux_horaire) "
                + "VALUES (?, ?, ?, 'TEMPORAL','Temporal test','Test','TNR','SALARIE',300000,1000)")){
            ps.setInt(1,period);ps.setInt(2,employee);ps.setString(3,"TY"+System.nanoTime());ps.executeUpdate();
        }
        return period;
    }
}
