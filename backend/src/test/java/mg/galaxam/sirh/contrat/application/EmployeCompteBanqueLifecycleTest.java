package mg.galaxam.sirh.contrat.application;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import mg.galaxam.sirh.contrat.api.dto.EmployeRequest;
import mg.galaxam.sirh.contrat.domain.EmployeValidationEngine;
import mg.galaxam.sirh.contrat.domain.MatriculeGeneratorEngine;
import mg.galaxam.sirh.contrat.persistence.EmployeCompteBanque;
import org.postgresql.ds.PGSimpleDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmployeCompteBanqueLifecycleTest {
    private final EmployeService service = new EmployeService(null, null, null);
    private Connection connection;

    @BeforeEach
    void ouvrirTransaction() throws Exception {
        String url = System.getenv("GALAXAM_BANQUE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "Base PostgreSQL temporaire requise");
        connection = DriverManager.getConnection(url,
                System.getenv("GALAXAM_BANQUE_TEST_DB_USER"),
                System.getenv("GALAXAM_BANQUE_TEST_DB_PASSWORD"));
        connection.setAutoCommit(false);
        EmployeCompteBanque.deleteByEmployeId(1, connection);
    }

    @AfterEach
    void annulerTransaction() throws Exception {
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    private static EmployeRequest demande(Integer id, String rib) {
        EmployeRequest req = new EmployeRequest();
        EmployeRequest.CompteBanqueRequest compte = new EmployeRequest.CompteBanqueRequest();
        compte.setIdCompteBanque(id);
        compte.setIdBanque(2);
        compte.setAgence("Test");
        compte.setNumCompteRib(rib);
        compte.setTitulaireCompte("Titulaire test");
        req.setCompteBanquePrincipal(compte);
        return req;
    }

    @Test
    void postEtPutCompletPreserventLeCompteJusquaSuppressionExplicite() throws Exception {
        PGSimpleDataSource source = new PGSimpleDataSource();
        source.setUrl(System.getenv("GALAXAM_BANQUE_TEST_DB_URL"));
        source.setUser(System.getenv("GALAXAM_BANQUE_TEST_DB_USER"));
        source.setPassword(System.getenv("GALAXAM_BANQUE_TEST_DB_PASSWORD"));
        EmployeService complet = new EmployeService(source, new EmployeValidationEngine(), new MatriculeGeneratorEngine());
        EmployeRequest fiche = new EmployeRequest();
        fiche.setMatricule("TEST-" + UUID.randomUUID().toString().substring(0, 8));
        fiche.setNom("Test");
        fiche.setPrenom("Compte");
        fiche.setSexe("M");
        fiche.setDateNaissance(Date.valueOf("1985-01-01"));
        fiche.setLieuNaissance("Antananarivo");
        fiche.setCinNumero("123456789013");
        fiche.setCinDateDelivrance(Date.valueOf("2005-01-01"));
        fiche.setCinLieuDelivrance("Antananarivo");
        fiche.setAdresse("Test");
        fiche.setTelephone("+261340000001");
        int idEmploye = complet.creerEmploye(fiche).getIdEmploye();
        assertNull(EmployeCompteBanque.findByEmployeId(idEmploye, connection));
        fiche.setCompteBanquePrincipal(demande(null, "RIB-1").getCompteBanquePrincipal());
        complet.modifierEmploye(idEmploye, fiche);
        int idCompte = EmployeCompteBanque.findByEmployeId(idEmploye, connection).getIdCompteBanque();
        fiche.setCompteBanquePrincipal(null);
        fiche.setVille("Ville modifiée");
        complet.modifierEmploye(idEmploye, fiche);
        assertEquals(idCompte, EmployeCompteBanque.findByEmployeId(idEmploye, connection).getIdCompteBanque());
        fiche.setCompteBanquePrincipal(demande(idCompte, "RIB-2").getCompteBanquePrincipal());
        complet.modifierEmploye(idEmploye, fiche);
        assertEquals(idCompte, EmployeCompteBanque.findByEmployeId(idEmploye, connection).getIdCompteBanque());
        assertEquals("RIB-2", EmployeCompteBanque.findByEmployeId(idEmploye, connection).getNumCompteRib());
        fiche.setCompteBanquePrincipal(null);
        fiche.setSupprimerCompteBanque(true);
        complet.modifierEmploye(idEmploye, fiche);
        assertNull(EmployeCompteBanque.findByEmployeId(idEmploye, connection));
    }

    @Test
    void absenceCreationModificationEtConservationDeLIdentifiant() throws Exception {
        assertNull(EmployeCompteBanque.findByEmployeId(1, connection));
        service.appliquerCompteBanque(1, demande(null, "RIB-1"), connection);
        EmployeCompteBanque initial = EmployeCompteBanque.findByEmployeId(1, connection);
        assertNotNull(initial);
        int id = initial.getIdCompteBanque();
        service.appliquerCompteBanque(1, demande(id, "RIB-2"), connection);
        EmployeCompteBanque modifie = EmployeCompteBanque.findByEmployeId(1, connection);
        assertEquals(id, modifie.getIdCompteBanque());
        assertEquals(initial.getCreatedAt(), modifie.getCreatedAt());
        assertEquals("RIB-2", modifie.getNumCompteRib());
        service.appliquerCompteBanque(1, new EmployeRequest(), connection);
        EmployeRequest nul = new EmployeRequest();
        nul.setCompteBanquePrincipal(null);
        service.appliquerCompteBanque(1, nul, connection);
        assertEquals(id, EmployeCompteBanque.findByEmployeId(1, connection).getIdCompteBanque());
        assertEquals(1, EmployeCompteBanque.listByEmployeId(1, connection).size());
    }

    @Test
    void suppressionEstExpliciteEtContradictionRefusee() throws Exception {
        service.appliquerCompteBanque(1, demande(null, "RIB-1"), connection);
        EmployeRequest contradiction = demande(null, "RIB-2");
        contradiction.setSupprimerCompteBanque(true);
        assertThrows(IllegalArgumentException.class,
                () -> service.appliquerCompteBanque(1, contradiction, connection));
        assertNotNull(EmployeCompteBanque.findByEmployeId(1, connection));
        EmployeRequest suppression = new EmployeRequest();
        suppression.setSupprimerCompteBanque(true);
        service.appliquerCompteBanque(1, suppression, connection);
        assertNull(EmployeCompteBanque.findByEmployeId(1, connection));
    }

    @Test
    void identifiantEtrangerEstRefuse() throws Exception {
        int autreId = EmployeCompteBanque.findByEmployeId(2, connection).getIdCompteBanque();
        assertThrows(IllegalArgumentException.class,
                () -> service.appliquerCompteBanque(1, demande(autreId, "RIB-2"), connection));
    }

    @Test
    void contrainteSqlRefuseUnSecondCompte() throws Exception {
        service.appliquerCompteBanque(1, demande(null, "RIB-1"), connection);
        EmployeCompteBanque second = new EmployeCompteBanque();
        second.setIdEmploye(1);
        second.setIdBanque(2);
        second.setNumCompteRib("RIB-2");
        SQLException erreur = assertThrows(SQLException.class, () -> second.save(connection));
        assertEquals("23505", erreur.getSQLState());
    }
}
