package mg.galaxam.sirh.organisation.api;

import java.util.List;
import java.util.Map;
import mg.galaxam.sirh.organisation.api.dto.*;
import mg.galaxam.sirh.organisation.application.OrganisationService;
import mg.galaxam.sirh.organisation.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur Unique d'API REST pour le Domaine Organisationnel.
 * Regroupe tous les points d'entrée HTTP pour l'organisation et délègue l'intégralité
 * de la logique métier et transactionnelle à OrganisationService.
 */
@RestController
@RequestMapping("/referentiel")
public class OrganisationController {

    @Autowired
    private OrganisationService organisationService;

    @GetMapping("/formulaire-employe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getReferentielsFormulaireEmploye() {
        return ResponseEntity.ok(organisationService.listerReferentielsFormulaireEmploye());
    }

    // =========================================================================
    // I. FILIALES
    // =========================================================================

    @GetMapping("/filiales")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefFiliale>> getAllFiliales(
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerFiliales(actifUniquement, search));
    }

    @GetMapping("/filiales/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefFiliale> getFilialeById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getFiliale(id));
    }

    @PostMapping("/filiales")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFiliale> createFiliale(@RequestBody FilialeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerFiliale(request));
    }

    @PutMapping("/filiales/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFiliale> updateFiliale(@PathVariable int id, @RequestBody FilialeRequest request) {
        return ResponseEntity.ok(organisationService.modifierFiliale(id, request));
    }

    @PutMapping("/filiales/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFiliale> deactivateFiliale(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverFiliale(id));
    }

    @PutMapping("/filiales/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFiliale> activateFiliale(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerFiliale(id));
    }

    // =========================================================================
    // II. SITES D'EXPLOITATION
    // =========================================================================

    @GetMapping("/sites")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefSite>> getAllSites(
            @RequestParam(required = false) Integer idFiliale,
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerSites(idFiliale, actifUniquement, search));
    }

    @GetMapping("/sites/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefSite> getSiteById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getSite(id));
    }

    @PostMapping("/sites")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefSite> createSite(@RequestBody SiteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerSite(request));
    }

    @PutMapping("/sites/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefSite> updateSite(@PathVariable int id, @RequestBody SiteRequest request) {
        return ResponseEntity.ok(organisationService.modifierSite(id, request));
    }

    @PutMapping("/sites/{id}/plancher")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefSite> toggleSitePlancher(@PathVariable int id, @RequestBody Map<String, Object> payload) {
        return ResponseEntity.ok(organisationService.configurerPlancherSite(id, payload));
    }

    @PutMapping("/sites/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefSite> deactivateSite(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverSite(id));
    }

    @PutMapping("/sites/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefSite> activateSite(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerSite(id));
    }

    // =========================================================================
    // III. DÉPARTEMENTS
    // =========================================================================

    @GetMapping("/departements")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefDepartement>> getAllDepartements(
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerDepartements(actifUniquement, search));
    }

    @GetMapping("/departements/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefDepartement> getDepartementById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getDepartement(id));
    }

    @PostMapping("/departements")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefDepartement> createDepartement(@RequestBody DepartementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerDepartement(request));
    }

    @PutMapping("/departements/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefDepartement> updateDepartement(@PathVariable int id, @RequestBody DepartementRequest request) {
        return ResponseEntity.ok(organisationService.modifierDepartement(id, request));
    }

    @PutMapping("/departements/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefDepartement> deactivateDepartement(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverDepartement(id));
    }

    @PutMapping("/departements/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefDepartement> activateDepartement(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerDepartement(id));
    }

    // =========================================================================
    // IV. SERVICES
    // =========================================================================

    @GetMapping("/services")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefService>> getAllServices(
            @RequestParam(required = false) Integer idDepartement,
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerServices(idDepartement, actifUniquement, search));
    }

    @GetMapping("/services/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefService> getServiceById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getService(id));
    }

    @PostMapping("/services")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefService> createService(@RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerService(request));
    }

    @PutMapping("/services/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefService> updateService(@PathVariable int id, @RequestBody ServiceRequest request) {
        return ResponseEntity.ok(organisationService.modifierService(id, request));
    }

    @PutMapping("/services/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefService> deactivateService(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverService(id));
    }

    @PutMapping("/services/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefService> activateService(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerService(id));
    }

    // =========================================================================
    // V. FONCTIONS / POSTES
    // =========================================================================

    @GetMapping("/fonctions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefFonction>> getAllFonctions(
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerFonctions(actifUniquement, search));
    }

    @GetMapping("/fonctions/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefFonction> getFonctionById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getFonction(id));
    }

    @PostMapping("/fonctions")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFonction> createFonction(@RequestBody FonctionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerFonction(request));
    }

    @PutMapping("/fonctions/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFonction> updateFonction(@PathVariable int id, @RequestBody FonctionRequest request) {
        return ResponseEntity.ok(organisationService.modifierFonction(id, request));
    }

    @PutMapping("/fonctions/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFonction> deactivateFonction(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverFonction(id));
    }

    @PutMapping("/fonctions/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefFonction> activateFonction(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerFonction(id));
    }

    // =========================================================================
    // VI. ÉTABLISSEMENTS BANCAIRES
    // =========================================================================

    @GetMapping("/banques")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefBanque>> getAllBanques(
            @RequestParam(required = false) Boolean actifUniquement,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(organisationService.listerBanques(actifUniquement, search));
    }

    @GetMapping("/banques/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefBanque> getBanqueById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getBanque(id));
    }

    @PostMapping("/banques")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefBanque> createBanque(@RequestBody RefBanque request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerBanque(request));
    }

    @PutMapping("/banques/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefBanque> updateBanque(@PathVariable int id, @RequestBody RefBanque request) {
        return ResponseEntity.ok(organisationService.modifierBanque(id, request));
    }

    @PutMapping("/banques/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefBanque> deactivateBanque(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverBanque(id));
    }

    @PutMapping("/banques/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefBanque> activateBanque(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerBanque(id));
    }

    // =========================================================================
    // VII. TYPES DE CONTRAT
    // =========================================================================

    @GetMapping("/types-contrat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefTypeContrat>> getAllTypesContrat(
            @RequestParam(required = false) Boolean actifUniquement) {
        return ResponseEntity.ok(organisationService.listerTypesContrat(actifUniquement));
    }

    @GetMapping("/types-contrat/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefTypeContrat> getTypeContratById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getTypeContrat(id));
    }

    @PostMapping("/types-contrat")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeContrat> createTypeContrat(@RequestBody RefTypeContrat request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerTypeContrat(request));
    }

    @PutMapping("/types-contrat/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeContrat> updateTypeContrat(@PathVariable int id, @RequestBody RefTypeContrat request) {
        return ResponseEntity.ok(organisationService.modifierTypeContrat(id, request));
    }

    @PutMapping("/types-contrat/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeContrat> deactivateTypeContrat(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverTypeContrat(id));
    }

    @PutMapping("/types-contrat/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeContrat> activateTypeContrat(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerTypeContrat(id));
    }

    // =========================================================================
    // VIII. TYPES D'INTERVENANT
    // =========================================================================

    @GetMapping("/types-intervenant")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RefTypeIntervenant>> getAllTypesIntervenant(
            @RequestParam(required = false) Boolean actifUniquement) {
        return ResponseEntity.ok(organisationService.listerTypesIntervenant(actifUniquement));
    }

    @GetMapping("/types-intervenant/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RefTypeIntervenant> getTypeIntervenantById(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.getTypeIntervenant(id));
    }

    @PostMapping("/types-intervenant")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeIntervenant> createTypeIntervenant(@RequestBody RefTypeIntervenant request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.creerTypeIntervenant(request));
    }

    @PutMapping("/types-intervenant/{id}")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeIntervenant> updateTypeIntervenant(@PathVariable int id, @RequestBody RefTypeIntervenant request) {
        return ResponseEntity.ok(organisationService.modifierTypeIntervenant(id, request));
    }

    @PutMapping("/types-intervenant/{id}/desactiver")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeIntervenant> deactivateTypeIntervenant(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.desactiverTypeIntervenant(id));
    }

    @PutMapping("/types-intervenant/{id}/activer")
    @PreAuthorize("hasAuthority('ORGANISATION_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<RefTypeIntervenant> activateTypeIntervenant(@PathVariable int id) {
        return ResponseEntity.ok(organisationService.activerTypeIntervenant(id));
    }
}
