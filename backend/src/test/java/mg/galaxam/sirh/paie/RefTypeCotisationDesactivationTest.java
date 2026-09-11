package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import javax.sql.DataSource;

import mg.galaxam.sirh.model.paieconfig.RefTypeCotisation;
import mg.galaxam.sirh.model.paieconfig.SiteCotisation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("RefTypeCotisationDesactivationTest — Test transactionnel de désactivation cotisation et cascade site_cotisation")
class RefTypeCotisationDesactivationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Désactivation transactionnelle : désactive la cotisation ET ses instances dans site_cotisation")
    void testDesactivationTransactionnelleCotisationEtSites() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Créer une cotisation de test
                RefTypeCotisation testCot = new RefTypeCotisation();
                testCot.setCode("COT_TEST_TX");
                testCot.setLibelle("Cotisation Test Transaction");
                testCot.setTauxSalarial(new BigDecimal("1.50"));
                testCot.setTauxPatronal(new BigDecimal("3.50"));
                testCot.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                testCot.setPortee("SITE");
                testCot.setEstActif(true);
                testCot.save(c);

                Integer idCot = testCot.getIdTypeCotisation();
                assertNotNull(idCot, "L'ID de la cotisation générée ne doit pas être nul");

                // Récupérer 2 sites existants
                int site1 = 1;
                int site2 = 2;

                // 2. Créer 2 instances dans site_cotisation pour cette cotisation
                SiteCotisation sc1 = new SiteCotisation();
                sc1.setIdSite(site1);
                sc1.setIdTypeCotisation(idCot);
                sc1.setEstActif(true);
                sc1.save(c);

                SiteCotisation sc2 = new SiteCotisation();
                sc2.setIdSite(site2);
                sc2.setIdTypeCotisation(idCot);
                sc2.setEstActif(true);
                sc2.save(c);

                // Vérifier que la cotisation et ses instances sont actives
                RefTypeCotisation checkInitial = (RefTypeCotisation) new RefTypeCotisation().rechercherParId(idCot, c);
                assertNotNull(checkInitial);
                assertTrue(checkInitial.isEstActif(), "La cotisation doit être active initialement");

                SiteCotisation sc1Check = (SiteCotisation) new SiteCotisation().rechercherParId(sc1.getIdSiteCotisation(), c);
                SiteCotisation sc2Check = (SiteCotisation) new SiteCotisation().rechercherParId(sc2.getIdSiteCotisation(), c);
                assertTrue(sc1Check.isEstActif(), "L'instance site 1 doit être active");
                assertTrue(sc2Check.isEstActif(), "L'instance site 2 doit être active");

                // 3. Exécuter la fonction transactionnelle de désactivation
                int nbSitesImpactes = checkInitial.desactiver(c);
                assertEquals(2, nbSitesImpactes, "Exactement 2 instances de site_cotisation doivent avoir été désactivées");

                // 4. Vérifier que la cotisation de référence est désormais inactive
                RefTypeCotisation checkPost = (RefTypeCotisation) new RefTypeCotisation().rechercherParId(idCot, c);
                assertFalse(checkPost.isEstActif(), "La cotisation doit être inactive après désactivation");

                // 5. Vérifier que TOUTES les instances associées dans site_cotisation sont inactives
                SiteCotisation sc1Post = (SiteCotisation) new SiteCotisation().rechercherParId(sc1.getIdSiteCotisation(), c);
                SiteCotisation sc2Post = (SiteCotisation) new SiteCotisation().rechercherParId(sc2.getIdSiteCotisation(), c);
                assertFalse(sc1Post.isEstActif(), "L'instance site 1 doit être inactive");
                assertFalse(sc2Post.isEstActif(), "L'instance site 2 doit être inactive");

                // 6. Tester la méthode statique de réactivation
                checkPost.activer(c);
                RefTypeCotisation checkReactiv = (RefTypeCotisation) new RefTypeCotisation().rechercherParId(idCot, c);
                assertTrue(checkReactiv.isEstActif(), "La cotisation doit être de nouveau active après réactivation");

            } finally {
                // Toujours rollback pour ne pas polluer la base de données
                c.rollback();
            }
        }
    }

    @Test
    @DisplayName("Isolation : la désactivation ne doit pas toucher aux instances d'autres cotisations")
    void testIsolationDesactivationCotisation() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // Cotisation A
                RefTypeCotisation cotA = new RefTypeCotisation();
                cotA.setCode("COT_ISO_A");
                cotA.setLibelle("Cotisation Isolation A");
                cotA.setTauxSalarial(BigDecimal.ONE);
                cotA.setTauxPatronal(BigDecimal.ONE);
                cotA.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                cotA.setEstActif(true);
                cotA.save(c);

                // Cotisation B
                RefTypeCotisation cotB = new RefTypeCotisation();
                cotB.setCode("COT_ISO_B");
                cotB.setLibelle("Cotisation Isolation B");
                cotB.setTauxSalarial(BigDecimal.ONE);
                cotB.setTauxPatronal(BigDecimal.ONE);
                cotB.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                cotB.setEstActif(true);
                cotB.save(c);

                // Site cotisation A et B
                SiteCotisation scA = new SiteCotisation();
                scA.setIdSite(1);
                scA.setIdTypeCotisation(cotA.getIdTypeCotisation());
                scA.setEstActif(true);
                scA.save(c);

                SiteCotisation scB = new SiteCotisation();
                scB.setIdSite(1);
                scB.setIdTypeCotisation(cotB.getIdTypeCotisation());
                scB.setEstActif(true);
                scB.save(c);

                // Désactiver uniquement Cotisation A
                cotA.desactiver(c);

                // Vérifier Cotisation A et son site
                RefTypeCotisation cotAPost = (RefTypeCotisation) new RefTypeCotisation().rechercherParId(cotA.getIdTypeCotisation(), c);
                SiteCotisation scAPost = (SiteCotisation) new SiteCotisation().rechercherParId(scA.getIdSiteCotisation(), c);
                assertFalse(cotAPost.isEstActif(), "Cotisation A doit être inactive");
                assertFalse(scAPost.isEstActif(), "Instance de site pour Cotisation A doit être inactive");

                // Vérifier Cotisation B et son site (doivent rester actifs !)
                RefTypeCotisation cotBPost = (RefTypeCotisation) new RefTypeCotisation().rechercherParId(cotB.getIdTypeCotisation(), c);
                SiteCotisation scBPost = (SiteCotisation) new SiteCotisation().rechercherParId(scB.getIdSiteCotisation(), c);
                assertTrue(cotBPost.isEstActif(), "Cotisation B doit rester active");
                assertTrue(scBPost.isEstActif(), "Instance de site pour Cotisation B doit rester active");

            } finally {
                c.rollback();
            }
        }
    }
}
