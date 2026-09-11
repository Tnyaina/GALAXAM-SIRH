package mg.galaxam.sirh.paie;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.time.LocalDate;
import javax.sql.DataSource;

import mg.galaxam.sirh.model.paieconfig.RefIndemnite;
import mg.galaxam.sirh.model.paieconfig.SiteIndemniteConfig;
import mg.galaxam.sirh.model.pointage.EmployeIndemniteDerogation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DisplayName("RefIndemniteDesactivationTest — Validation de la désactivation transactionnelle en cascade de l'indemnité, ses tarifs sites et dérogations")
class RefIndemniteDesactivationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Désactivation en cascade : désactive l'indemnité, ses tarifs de sites et clôture ses dérogations actives")
    void testDesactivationCascadeIndemnite() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                // 1. Créer une indemnité de test
                RefIndemnite testInd = new RefIndemnite();
                testInd.setCode("IND_TEST_TX");
                testInd.setLibelle("Indemnité Test Transaction");
                testInd.setImposableIrsa(false);
                testInd.setFractionnable(true);
                testInd.setEstActif(true);
                testInd.save(c);

                Integer idInd = testInd.getIdIndemnite();
                assertNotNull(idInd, "L'ID de l'indemnité doit être généré");

                // 2. Créer 2 configurations par site dans site_indemnite_config
                SiteIndemniteConfig sic1 = new SiteIndemniteConfig();
                sic1.setIdSite(1);
                sic1.setIdIndemnite(idInd);
                sic1.setPeriodicite("JOURNALIER");
                sic1.setMontantReference(new BigDecimal("5000.00"));
                sic1.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                sic1.setEstActif(true);
                sic1.save(c);

                SiteIndemniteConfig sic2 = new SiteIndemniteConfig();
                sic2.setIdSite(2);
                sic2.setIdIndemnite(idInd);
                sic2.setPeriodicite("JOURNALIER");
                sic2.setMontantReference(new BigDecimal("6000.00"));
                sic2.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                sic2.setEstActif(true);
                sic2.save(c);

                // 3. Créer une dérogation active dans employe_indemnite_derogation (pour un employé existant, ex: id 1 ou 2)
                int idEmp = 1;
                // Vérifions un employe existant
                try (var ps = c.prepareStatement("SELECT id_employe FROM employe LIMIT 1");
                     var rs = ps.executeQuery()) {
                    if (rs.next()) {
                        idEmp = rs.getInt(1);
                    }
                }

                EmployeIndemniteDerogation derog = new EmployeIndemniteDerogation();
                derog.setIdEmploye(idEmp);
                derog.setIdIndemnite(idInd);
                derog.setBeneficie(false);
                derog.setMotif("Véhicule de fonction test");
                derog.setDateDebut(Date.valueOf(LocalDate.of(2026, 1, 1)));
                derog.setDateFin(null); // dérogation permanente active
                derog.save(c);

                // Vérifier les états initiaux
                RefIndemnite indInit = (RefIndemnite) new RefIndemnite().rechercherParId(idInd, c);
                assertTrue(indInit.isEstActif(), "L'indemnité doit être active initialement");

                SiteIndemniteConfig sic1Init = (SiteIndemniteConfig) new SiteIndemniteConfig().rechercherParId(sic1.getIdSiteIndemnite(), c);
                assertTrue(sic1Init.isEstActif(), "Le tarif site 1 doit être actif");

                EmployeIndemniteDerogation derogInit = new EmployeIndemniteDerogation().rechercherParId(derog.getIdDerogation(), c);
                assertNull(derogInit.getDateFin(), "La dérogation ne doit pas avoir de date de fin initialement");

                // 4. Exécuter la méthode de désactivation transactionnelle en cascade
                int[] impacts = indInit.desactiver(c);
                assertEquals(2, impacts[0], "Exactement 2 configurations de site doivent être désactivées");
                assertEquals(1, impacts[1], "Exactement 1 dérogation doit être clôturée");

                // 5. Vérifier que l'indemnité est inactive
                RefIndemnite indPost = (RefIndemnite) new RefIndemnite().rechercherParId(idInd, c);
                assertFalse(indPost.isEstActif(), "L'indemnité doit être inactive après désactivation");

                // 6. Vérifier que les configs de sites sont inactives
                SiteIndemniteConfig sic1Post = (SiteIndemniteConfig) new SiteIndemniteConfig().rechercherParId(sic1.getIdSiteIndemnite(), c);
                SiteIndemniteConfig sic2Post = (SiteIndemniteConfig) new SiteIndemniteConfig().rechercherParId(sic2.getIdSiteIndemnite(), c);
                assertFalse(sic1Post.isEstActif(), "Le tarif site 1 doit être inactif");
                assertFalse(sic2Post.isEstActif(), "Le tarif site 2 doit être inactif");

                // 7. Vérifier que la dérogation a été clôturée avec date_fin renseignée
                EmployeIndemniteDerogation derogPost = new EmployeIndemniteDerogation().rechercherParId(derog.getIdDerogation(), c);
                assertNotNull(derogPost.getDateFin(), "La dérogation doit avoir une date de fin renseignée");

                // 8. Tester la réactivation
                indPost.activer(c);
                RefIndemnite indReactiv = (RefIndemnite) new RefIndemnite().rechercherParId(idInd, c);
                assertTrue(indReactiv.isEstActif(), "L'indemnité doit être réactivée");

            } finally {
                c.rollback();
            }
        }
    }
}
