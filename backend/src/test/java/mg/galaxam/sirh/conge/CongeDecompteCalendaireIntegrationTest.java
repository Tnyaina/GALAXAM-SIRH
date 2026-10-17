package mg.galaxam.sirh.conge;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import javax.sql.DataSource;
import mg.galaxam.sirh.conge.application.CongeDecompteCalendaireResolver;
import mg.galaxam.sirh.conge.application.CongeLotValorisationService;
import mg.galaxam.sirh.conge.testutils.CongeTestFixtureHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class CongeDecompteCalendaireResolverIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void valorisationFifoSuitLesJoursDebitesAuPassageAnnee() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database()")) {
                assertTrue(rs.next());
                assertEquals("sirh_galaxam_test", rs.getString(1));
            }
            c.setAutoCommit(false);
            try {
                String suffixe = Long.toString(System.nanoTime(), 36);
                int emp;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,"
                        + "cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                        + "VALUES (?, 'Test','Fifo','M',DATE '1990-01-15','Antananarivo',?,"
                        + "DATE '2010-01-15','TNR','Rue Test','0340000000') RETURNING id_employe")) {
                    ps.setString(1, "EF-" + suffixe);
                    ps.setString(2, "CF-" + suffixe);
                    try (var rs = ps.executeQuery()) { assertTrue(rs.next()); emp = rs.getInt(1); }
                }
                int contrat = CongeTestFixtureHelper.createContratActif(c, emp, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, emp, contrat, LocalDate.of(2025, 1, 1),
                        new BigDecimal("300000"));
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ref_calendrier_ferie (date_ferie,libelle,est_chome,est_national,est_actif) "
                        + "VALUES (DATE '2026-01-01','Nouvel An FIFO',TRUE,TRUE,TRUE)")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE parametre_global SET date_fin=DATE '2025-12-31' "
                        + "WHERE code_parametre='CONGE_DECOMPTE_FERIES' AND date_debut=DATE '2001-07-01'")) {
                    assertEquals(1, ps.executeUpdate());
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO parametre_global (code_parametre,categorie,valeur,date_debut,est_actif,est_desactivable) "
                        + "VALUES ('CONGE_DECOMPTE_FERIES','CONGE','EXCLUS',DATE '2026-01-01',TRUE,TRUE)")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mouvement_solde_conge (id_employe,type_mouvement,nb_jours,"
                        + "date_mouvement,commentaire,valeur_source) "
                        + "VALUES (?, 'AJUSTEMENT', 2.00, DATE '2025-12-25','[SOLDE_INITIAL] FIFO test',20000.00)")) {
                    ps.setInt(1, emp); ps.executeUpdate();
                }
                int demande;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO demande_conge (id_employe,id_type_conge,id_statut_demande_conge,"
                        + "date_debut,date_fin,nb_jours_decomptes) VALUES (?,?,?,"
                        + "DATE '2025-12-31',DATE '2026-01-02',2.00) RETURNING id_demande_conge")) {
                    ps.setInt(1, emp);
                    ps.setInt(2, CongeTestFixtureHelper.getTypeCongeId(c, "ANNUEL"));
                    ps.setInt(3, CongeTestFixtureHelper.getStatutDemandeCongeId(c, "VALIDE"));
                    try (var rs = ps.executeQuery()) { assertTrue(rs.next()); demande = rs.getInt(1); }
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO mouvement_solde_conge (id_employe,type_mouvement,nb_jours,"
                        + "date_mouvement,id_demande_conge) VALUES (?, 'PRISE',-2.00,DATE '2025-12-31',?)")) {
                    ps.setInt(1, emp); ps.setInt(2, demande); ps.executeUpdate();
                }
                var service = new CongeLotValorisationService();
                assertEquals(0, service.valorisationPrisesSurPeriode(emp,
                        LocalDate.of(2025, 12, 26), LocalDate.of(2025, 12, 31), c)
                        .compareTo(new BigDecimal("10000.00")));
                assertEquals(0, service.valorisationPrisesSurPeriode(emp,
                        LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 25), c)
                        .compareTo(new BigDecimal("10000.00")));
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void politiqueDateeInclutPuisExclutLeFerieSansChangerLeWeekend() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            try (var st = c.createStatement(); var rs = st.executeQuery("SELECT current_database()")) {
                assertTrue(rs.next());
                assertEquals("sirh_galaxam_test", rs.getString(1));
            }
            c.setAutoCommit(false);
            try {
                int emp;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO employe (matricule,nom,prenom,sexe,date_naissance,lieu_naissance,"
                        + "cin_numero,cin_date_delivrance,cin_lieu_delivrance,adresse,telephone) "
                        + "VALUES ('EMP-FERIE-TEST','Test','Ferie','M',DATE '1990-01-15','Antananarivo',"
                        + "'CIN-FERIE-TEST',DATE '2010-01-15','TNR','Rue Test','0340000000') "
                        + "RETURNING id_employe")) {
                    try (var rs = ps.executeQuery()) { rs.next(); emp = rs.getInt(1); }
                }
                int contrat = CongeTestFixtureHelper.createContratActif(c, emp, LocalDate.of(2025, 1, 1));
                CongeTestFixtureHelper.createAffectation(c, emp, contrat, LocalDate.of(2025, 1, 1), new BigDecimal("300000"));
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO ref_calendrier_ferie (date_ferie,libelle,est_chome,est_national,est_actif) "
                        + "VALUES (DATE '2026-01-01','Nouvel An test',TRUE,TRUE,TRUE)")) {
                    ps.executeUpdate();
                }
                assertEquals(new BigDecimal("3.0"), CongeDecompteCalendaireResolver.calculer(
                        emp, LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2), false, c));
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE parametre_global SET date_fin=? WHERE code_parametre='CONGE_DECOMPTE_FERIES' AND date_debut=DATE '2001-07-01'")) {
                    ps.setDate(1, Date.valueOf("2025-12-31")); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO parametre_global (code_parametre,categorie,valeur,date_debut,est_actif,est_desactivable) "
                        + "VALUES ('CONGE_DECOMPTE_FERIES','CONGE','EXCLUS',DATE '2026-01-01',TRUE,TRUE)")) {
                    ps.executeUpdate();
                }
                assertEquals(new BigDecimal("2.0"), CongeDecompteCalendaireResolver.calculer(
                        emp, LocalDate.of(2025, 12, 31), LocalDate.of(2026, 1, 2), false, c));
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM parametre_global WHERE code_parametre='CONGE_DECOMPTE_FERIES' AND date_debut=DATE '2026-01-01'")) {
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO parametre_global (code_parametre,categorie,valeur,date_debut,est_actif,est_desactivable) "
                        + "VALUES ('CONGE_DECOMPTE_FERIES','CONGE','EXCLUS',DATE '2026-01-01',FALSE,TRUE)")) {
                    ps.executeUpdate();
                }
                var disabled = assertThrows(IllegalStateException.class, () -> CongeDecompteCalendaireResolver.calculer(
                        emp, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), false, c));
                assertTrue(disabled.getMessage().contains("CONFIGURATION_DISABLED"));
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM parametre_global WHERE code_parametre='CONGE_DECOMPTE_FERIES' AND date_debut=DATE '2026-01-01'")) {
                    ps.executeUpdate();
                }
                var missing = assertThrows(IllegalStateException.class, () -> CongeDecompteCalendaireResolver.calculer(
                        emp, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1), false, c));
                assertTrue(missing.getMessage().contains("CONFIGURATION_MISSING"));
                assertTrue(missing.getMessage().contains("2026-01-01"));
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO parametre_global (code_parametre,categorie,valeur,date_debut,est_actif,est_desactivable) "
                        + "VALUES ('CONGE_DECOMPTE_FERIES','CONGE','EXCLUS',DATE '2026-01-01',TRUE,TRUE),"
                        + "('CONGE_DECOMPTE_FERIES','CONGE','INCLUS',DATE '2026-01-02',TRUE,TRUE)")) {
                    java.sql.SQLException overlap = assertThrows(java.sql.SQLException.class, ps::executeUpdate);
                    assertEquals("23P01", overlap.getSQLState(), "PostgreSQL doit refuser les périodes qui se chevauchent.");
                }
            } finally {
                c.rollback();
            }
        }
    }
}
