package mg.galaxam.sirh.organisation;

import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.common.exception.SirhBusinessException;
import mg.galaxam.sirh.common.exception.SirhErrorCode;
import mg.galaxam.sirh.organisation.api.dto.FilialeRequest;
import mg.galaxam.sirh.organisation.api.dto.SiteRequest;
import mg.galaxam.sirh.organisation.application.OrganisationService;
import mg.galaxam.sirh.organisation.persistence.RefFiliale;
import mg.galaxam.sirh.organisation.persistence.RefSite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class OrganisationServiceIntegrationTest {

    @Autowired
    private OrganisationService organisationService;

    @Test
    @DisplayName("OrganisationService : cycle de vie filiale et garde-fous d'intégrité POO")
    void testFilialeLifecycleAndIntegrity() {
        // 1. Création d'une nouvelle filiale
        String codeUnique = "TST" + (System.currentTimeMillis() % 1000);
        FilialeRequest req = new FilialeRequest();
        req.setCodeFiliale(codeUnique);
        req.setRaisonSociale("Filiale Test POO " + codeUnique);
        req.setEmail("test." + codeUnique.toLowerCase() + "@galaxam.mg");

        RefFiliale creee = organisationService.creerFiliale(req);
        assertNotNull(creee.getIdFiliale());
        assertTrue(creee.isEstActif());

        // 2. Recherche avec filtre
        List<RefFiliale> filtres = organisationService.listerFiliales(true, codeUnique);
        assertEquals(1, filtres.size());
        assertEquals(codeUnique, filtres.get(0).getCodeFiliale());

        // 3. Rattachement d'un site actif
        SiteRequest siteReq = new SiteRequest();
        siteReq.setCodeSite("S-" + codeUnique);
        siteReq.setLibelle("Site Exploitation " + codeUnique);
        siteReq.setIdFilialeRattachement(creee.getIdFiliale());
        siteReq.setHoraireContinu(true);

        RefSite siteCree = organisationService.creerSite(siteReq);
        assertNotNull(siteCree.getIdSite());
        assertTrue(siteCree.isEstActif());

        // 4. Tentative de désactivation de la filiale -> Doit échouer car le site est actif (Garde-fou POO)
        SirhBusinessException ex = assertThrows(SirhBusinessException.class, () -> {
            organisationService.desactiverFiliale(creee.getIdFiliale());
        });
        assertEquals(SirhErrorCode.DATA_REFERENCED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("site(s) actif(s)"));

        // 5. Désactivation du site
        organisationService.desactiverSite(siteCree.getIdSite());
        RefSite siteMaj = organisationService.getSite(siteCree.getIdSite());
        assertFalse(siteMaj.isEstActif());

        // 6. La désactivation de la filiale est maintenant possible
        RefFiliale filialeDesactivee = organisationService.desactiverFiliale(creee.getIdFiliale());
        assertFalse(filialeDesactivee.isEstActif());

        // 7. Réactivation
        RefFiliale filialeReactivee = organisationService.activerFiliale(creee.getIdFiliale());
        assertTrue(filialeReactivee.isEstActif());
    }

    @Test
    @DisplayName("OrganisationService : configuration du plancher d'arrivée site")
    void testSitePlancherConfiguration() {
        String code = "PL" + (System.currentTimeMillis() % 1000);
        SiteRequest req = new SiteRequest();
        req.setCodeSite(code);
        req.setLibelle("Site Plancher " + code);
        req.setPlancherArriveeActif(false);

        RefSite site = organisationService.creerSite(req);
        assertFalse(site.isPlancherArriveeActif());

        // Configuration plancher actif avec heure
        RefSite updated = organisationService.configurerPlancherSite(site.getIdSite(), Map.of(
                "actif", true,
                "heureEntreeMinimaleValorisee", "07:30"
        ));
        assertTrue(updated.isPlancherArriveeActif());
        assertNotNull(updated.getHeureEntreeMinimaleValorisee());
    }
}
