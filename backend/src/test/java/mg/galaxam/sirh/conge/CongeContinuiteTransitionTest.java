package mg.galaxam.sirh.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.util.Properties;
import mg.galaxam.sirh.conge.application.CongeContinuiteService;
import mg.galaxam.sirh.conge.application.CongeService;
import mg.galaxam.sirh.conge.domain.CongeCalculateurEngine;
import mg.galaxam.sirh.conge.persistence.DemandeConge;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Fixtures locales annulées : vérification de la base avant la première écriture. */
class CongeContinuiteTransitionTest {
    private Connection c;

    @BeforeEach void ouvrir() throws Exception {
        Properties p = new Properties();
        try (var in = getClass().getResourceAsStream("/application-test.properties")) { p.load(in); }
        String url = p.getProperty("spring.datasource.url");
        assertEquals("jdbc:postgresql://localhost:5432/sirh_galaxam_test", url);
        c = DriverManager.getConnection(url, p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
        try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database()")) {
            assertTrue(rs.next()); assertEquals("sirh_galaxam_test", rs.getString(1));
        }
        c.setAutoCommit(false);
    }

    @AfterEach void annuler() throws Exception { if (c != null) { c.rollback(); c.close(); } }

    private int employe(String code, int site, LocalDate debut) throws Exception {
        int id;
        String suffixe = Long.toString(System.nanoTime(), 36);
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employe "
                + "(matricule,nom,prenom,sexe,date_naissance,lieu_naissance,cin_numero,"
                + "cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                + "VALUES (?,?,'Test','M',DATE '1990-01-01','TNR',?,DATE '2010-01-01',"
                + "'TNR','Test','0340000000') RETURNING id_employe")) {
            ps.setString(1, code + suffixe); ps.setString(2, code); ps.setString(3, suffixe);
            try (var rs = ps.executeQuery()) { assertTrue(rs.next()); id = rs.getInt(1); }
        }
        int contrat = CongeTestFixtureHelper.createContratActif(c, id, debut);
        int affectation = CongeTestFixtureHelper.createAffectation(c, id, contrat, debut,
                new BigDecimal("300000"));
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE employe_affectation_carriere SET id_site=? WHERE id_affectation=?")) {
            ps.setInt(1, site); ps.setInt(2, affectation); ps.executeUpdate();
        }
        return id;
    }

    private void absence(int employe, LocalDate debut, LocalDate fin) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO demande_conge "
                + "(id_employe,id_type_conge,id_statut_demande_conge,date_debut,date_fin,nb_jours_decomptes) "
                + "VALUES (?,?,?,?,?,1)")) {
            ps.setInt(1, employe);
            ps.setInt(2, CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL"));
            ps.setInt(3, CongeTestFixtureHelper.getStatutDemandeCongeId(c, "VALIDE"));
            ps.setDate(4, Date.valueOf(debut)); ps.setDate(5, Date.valueOf(fin));
            ps.executeUpdate();
        }
    }

    @Test void demandeTraverseDeuxSitesEtDetecteSeulementLesCollisionsReelles() throws Exception {
        LocalDate decembre = LocalDate.of(2025, 12, 31);
        LocalDate janvier = LocalDate.of(2026, 1, 1);
        int salarie = employe("CTE", 1, LocalDate.of(2025, 1, 1));
        try (PreparedStatement ps = c.prepareStatement("UPDATE employe_affectation_carriere "
                + "SET date_fin=DATE '2025-12-31' WHERE id_employe=?")) {
            ps.setInt(1, salarie); ps.executeUpdate();
        }
        int contrat;
        try (PreparedStatement ps = c.prepareStatement("SELECT id_contrat FROM contrat_travail WHERE id_employe=?")) {
            ps.setInt(1, salarie);
            try (var rs = ps.executeQuery()) { assertTrue(rs.next()); contrat = rs.getInt(1); }
        }
        int suivante = CongeTestFixtureHelper.createAffectation(c, salarie, contrat, janvier,
                new BigDecimal("350000"));
        try (PreparedStatement ps = c.prepareStatement("UPDATE employe_affectation_carriere "
                + "SET id_site=2 WHERE id_affectation=?")) {
            ps.setInt(1, suivante); ps.executeUpdate();
        }
        int collegueDecembre = employe("CDA", 1, LocalDate.of(2025, 1, 1));
        int collegueJanvier = employe("CJA", 2, LocalDate.of(2025, 1, 1));
        int autreSiteJanvier = employe("CSA", 1, LocalDate.of(2025, 1, 1));
        absence(collegueDecembre, decembre, decembre);
        absence(collegueJanvier, janvier, janvier);
        absence(autreSiteJanvier, janvier, janvier);

        var analyse = new CongeContinuiteService().analyser(salarie, decembre, janvier,
                null, false, null, c);
        assertTrue(analyse.actifSurPeriode());
        assertEquals(2, analyse.segments().size());
        assertEquals(1, analyse.segments().get(0).idSite());
        assertEquals(2, analyse.segments().get(1).idSite());
        assertEquals(2, analyse.collisions().size());
        assertTrue(analyse.collisions().stream().anyMatch(x -> x.idEmploye() == collegueDecembre));
        assertTrue(analyse.collisions().stream().anyMatch(x -> x.idEmploye() == collegueJanvier));
        assertFalse(analyse.collisions().stream().anyMatch(x -> x.idEmploye() == autreSiteJanvier));

        var simulation = new CongeService(new CongeCalculateurEngine()).verifierConflitsDemande(
                salarie, Date.valueOf(decembre), Date.valueOf(janvier), false, null, null, c);
        assertEquals(true, simulation.get("conflitPoste"));
        assertEquals(2, ((java.util.List<?>) simulation.get("colleguesAbsents")).size());

        DemandeConge demande = new DemandeConge();
        demande.setIdEmploye(salarie);
        demande.setIdTypeConge(CongeTestFixtureHelper.getTypeCongeId(c, "SANS_SOLDE"));
        demande.setDateDebut(Date.valueOf(decembre));
        demande.setDateFin(Date.valueOf(janvier));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> demande.controler(c))
                .getMessage().contains("Conflit de continuité de service"));
        demande.setDerogationContinuiteService(true);
        assertDoesNotThrow(() -> demande.controler(c));
    }
}
