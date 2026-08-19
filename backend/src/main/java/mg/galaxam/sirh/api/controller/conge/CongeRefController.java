package mg.galaxam.sirh.api.controller.conge;

import java.sql.Connection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import mg.galaxam.sirh.model.conge.RefMotifEvenementFamilial;
import mg.galaxam.sirh.model.conge.RefPoolCongeAnnuel;
import mg.galaxam.sirh.model.conge.RefStatutDemandeConge;
import mg.galaxam.sirh.model.conge.RefTypeConge;
import mg.galaxam.sirh.model.paieconfig.ParametreGlobal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/conges/referentiels")
public class CongeRefController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/all")
    public ResponseEntity<?> getAllReferentielsConge() {
        try (Connection c = dataSource.getConnection()) {
            Map<String, Object> result = new HashMap<>();
            result.put("pools", RefPoolCongeAnnuel.listAll(c));
            result.put("typesConge", RefTypeConge.listAll(c, true));
            result.put("motifsFamiliaux", RefMotifEvenementFamilial.listAll(c, true));
            result.put("statutsDemande", RefStatutDemandeConge.listAll(c));
            result.put("parametresGlobal", ParametreGlobal.listByCategorie("CONGE", c));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des référentiels congés : " + e.getMessage());
        }
    }

    @GetMapping("/types")
    public ResponseEntity<?> getTypesConge(@RequestParam(value = "actifUniquement", required = false, defaultValue = "true") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeConge> list = RefTypeConge.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des types de congés : " + e.getMessage());
        }
    }

    @GetMapping("/motifs")
    public ResponseEntity<?> getMotifsFamiliaux(@RequestParam(value = "actifUniquement", required = false, defaultValue = "true") Boolean actifUniquement) {
        try (Connection c = dataSource.getConnection()) {
            List<RefMotifEvenementFamilial> list = RefMotifEvenementFamilial.listAll(c, actifUniquement);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des motifs d'événements familiaux : " + e.getMessage());
        }
    }

    @GetMapping("/pools")
    public ResponseEntity<?> getPools() {
        try (Connection c = dataSource.getConnection()) {
            List<RefPoolCongeAnnuel> list = RefPoolCongeAnnuel.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des pools de congés : " + e.getMessage());
        }
    }

    @GetMapping("/statuts")
    public ResponseEntity<?> getStatutsDemande() {
        try (Connection c = dataSource.getConnection()) {
            List<RefStatutDemandeConge> list = RefStatutDemandeConge.listAll(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la récupération des statuts de demande de congé : " + e.getMessage());
        }
    }

    // =========================================================================
    // CRUD TYPE DE CONGE
    // =========================================================================

    @PostMapping("/types")
    public ResponseEntity<?> createTypeConge(@RequestBody RefTypeConge typeConge) {
        try (Connection c = dataSource.getConnection()) {
            typeConge.controler(c);
            typeConge.save(c);
            return ResponseEntity.status(HttpStatus.CREATED).body(typeConge);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création du type de congé : " + e.getMessage());
        }
    }

    @PutMapping("/types/{id}")
    public ResponseEntity<?> updateTypeConge(@PathVariable Integer id, @RequestBody RefTypeConge typeConge) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeConge existant = RefTypeConge.rechercherParId(id, c);
            if (existant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de congé introuvable.");
            }
            typeConge.setIdTypeConge(id);
            typeConge.controler(c);
            typeConge.update(c);
            return ResponseEntity.ok(typeConge);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification du type de congé : " + e.getMessage());
        }
    }

    @DeleteMapping("/types/{id}")
    public ResponseEntity<?> deactivateTypeConge(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeConge existant = RefTypeConge.rechercherParId(id, c);
            if (existant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Type de congé introuvable.");
            }
            existant.setEstActif(false);
            existant.update(c); // On fait une désactivation logique (soft delete)
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la désactivation du type de congé : " + e.getMessage());
        }
    }

    // =========================================================================
    // CRUD MOTIF EVENEMENT FAMILIAL
    // =========================================================================

    @PostMapping("/motifs")
    public ResponseEntity<?> createMotifFamilial(@RequestBody RefMotifEvenementFamilial motif) {
        try (Connection c = dataSource.getConnection()) {
            motif.controler(c);
            motif.save(c);
            return ResponseEntity.status(HttpStatus.CREATED).body(motif);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création du motif familial : " + e.getMessage());
        }
    }

    @PutMapping("/motifs/{id}")
    public ResponseEntity<?> updateMotifFamilial(@PathVariable Integer id, @RequestBody RefMotifEvenementFamilial motif) {
        try (Connection c = dataSource.getConnection()) {
            RefMotifEvenementFamilial existant = RefMotifEvenementFamilial.rechercherParId(id, c);
            if (existant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Motif familial introuvable.");
            }
            motif.setIdMotif(id);
            motif.controler(c);
            motif.update(c);
            return ResponseEntity.ok(motif);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la modification du motif familial : " + e.getMessage());
        }
    }

    @DeleteMapping("/motifs/{id}")
    public ResponseEntity<?> deactivateMotifFamilial(@PathVariable Integer id) {
        try (Connection c = dataSource.getConnection()) {
            RefMotifEvenementFamilial existant = RefMotifEvenementFamilial.rechercherParId(id, c);
            if (existant == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Motif familial introuvable.");
            }
            existant.setEstActif(false);
            existant.update(c); // Désactivation logique
            return ResponseEntity.ok(existant);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la désactivation du motif familial : " + e.getMessage());
        }
    }
}
