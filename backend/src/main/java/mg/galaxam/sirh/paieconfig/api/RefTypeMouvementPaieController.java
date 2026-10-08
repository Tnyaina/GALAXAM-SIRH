package mg.galaxam.sirh.paieconfig.api;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import javax.sql.DataSource;
import mg.galaxam.sirh.paieconfig.api.dto.RefTypeMouvementPaieRequest;
import mg.galaxam.sirh.paieconfig.persistence.RefTypeMouvementPaie;
import mg.galaxam.sirh.securite.AuditLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/referentiel/types-mouvement-paie", "/api/v1/referentiel/types-mouvement-paie"})
public class RefTypeMouvementPaieController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private mg.galaxam.sirh.paie.application.PayrollService payrollService;

    @GetMapping("/catalogue")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getCatalogue() {
        try (Connection c = dataSource.getConnection()) {
            List<Map<String, Object>> types = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_type_mouvement, code, libelle, sens, categorie, id_rubrique, " +
                    "nature, est_actif, est_proratise, est_plafonne_dixieme, saisie_manuelle_autorisee, ordre_affichage, description " +
                    "FROM ref_type_mouvement_paie ORDER BY ordre_affichage, code");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id_type_mouvement"));
                    item.put("code", rs.getString("code"));
                    item.put("libelle", rs.getString("libelle"));
                    item.put("sens", rs.getString("sens"));
                    item.put("categorie", rs.getString("categorie"));
                    item.put("idRubrique", rs.getObject("id_rubrique"));
                    item.put("nature", rs.getString("nature"));
                    item.put("estActif", rs.getBoolean("est_actif"));
                    item.put("estProratise", rs.getBoolean("est_proratise"));
                    item.put("estPlafonneDixieme", rs.getBoolean("est_plafonne_dixieme"));
                    item.put("saisieManuelleAutorisee", rs.getBoolean("saisie_manuelle_autorisee"));
                    item.put("ordreAffichage", rs.getInt("ordre_affichage"));
                    item.put("description", rs.getString("description"));
                    types.add(item);
                }
            }
            List<Map<String, Object>> rubriques = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT id_rubrique, code, libelle, categorie, sens, type_valeur, soumis_irsa, " +
                    "soumis_cnaps, soumis_sante, est_fixe, est_actif, description, ordre_affichage, " +
                    "reference_reglementaire, date_debut_effet, etape_calcul, date_fin_effet, id_rubrique_origine, condition_application, methode_calcul, politique_arrondi, soumis_fmfp " +
                    "FROM paie_rubrique ORDER BY ordre_affichage, code");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", rs.getInt("id_rubrique"));
                    item.put("code", rs.getString("code"));
                    item.put("libelle", rs.getString("libelle"));
                    item.put("categorie", rs.getString("categorie"));
                    item.put("sens", rs.getString("sens"));
                    item.put("typeValeur", rs.getString("type_valeur"));
                    item.put("soumisIrsa", rs.getBoolean("soumis_irsa"));
                    item.put("soumisCnaps", rs.getBoolean("soumis_cnaps"));
                    item.put("soumisSante", rs.getBoolean("soumis_sante"));
                    item.put("estFixe", rs.getBoolean("est_fixe"));
                    item.put("estActif", rs.getBoolean("est_actif"));
                    item.put("description", rs.getString("description"));
                    item.put("referenceReglementaire", rs.getString("reference_reglementaire"));
                    item.put("dateDebutEffet", rs.getDate("date_debut_effet"));
                    item.put("etapeCalcul", rs.getString("etape_calcul"));
                    item.put("methodeCalcul",rs.getString("methode_calcul")); item.put("politiqueArrondi",rs.getString("politique_arrondi"));
                    item.put("soumisFmfp",rs.getObject("soumis_fmfp"));
                    item.put("dateFinEffet", rs.getDate("date_fin_effet"));
                    item.put("idRubriqueOrigine", rs.getObject("id_rubrique_origine"));
                    item.put("conditionApplication", rs.getString("condition_application"));
                    item.put("ordreAffichage", rs.getInt("ordre_affichage"));
                    var capability=mg.galaxam.sirh.paie.domain.engine.PayrollCalculatorRegistry.describe(rs.getString("code"),rs.getString("categorie"),rs.getBoolean("est_fixe"),rs.getString("etape_calcul"));
                    item.put("niveauConfiguration",capability.niveau());item.put("calculateur",capability.calculateur());
                    item.put("phaseCalcul",capability.phase());item.put("sourceCalcul",capability.source());item.put("arrondiCalculateur",capability.arrondi());
                    rubriques.add(item);
                }
            }
            return ResponseEntity.ok(Map.of("types", types, "rubriques", rubriques));
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    /** Catalogue de paramétrage : capacités et choix du moteur, filtrage et pagination serveur. */
    @GetMapping("/configuration/parcours")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> getParcoursConfiguration() {
        return ResponseEntity.ok(Map.of(
                "phases", mg.galaxam.sirh.paie.domain.engine.PayrollCalculatorRegistry.phases(),
                "rubriques", tousLesElements("rubriques"), "types", tousLesElements("types")));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> tousLesElements(String domaine) {
        var items = new ArrayList<Map<String,Object>>();
        int page = 1;
        do {
            Map<String,Object> result = (Map<String,Object>) getConfiguration(domaine, "", "", "", "", "ordre", page++, 1000, null).getBody();
            items.addAll((List<Map<String,Object>>) result.get("items"));
            if (items.size() >= ((Number) result.get("totalElements")).intValue()) break;
        } while (true);
        return items;
    }

    /** Catalogue de paramétrage : capacités et choix du moteur, filtrage et pagination serveur. */
    @GetMapping("/configuration")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> getConfiguration(
            @RequestParam(defaultValue="rubriques") String domaine,
            @RequestParam(defaultValue="") String recherche,
            @RequestParam(defaultValue="") String sens,
            @RequestParam(defaultValue="") String niveau,
            @RequestParam(defaultValue="") String statut,
            @RequestParam(defaultValue="ordre") String tri,
            @RequestParam(defaultValue="1") int page,
            @RequestParam(defaultValue="10") int taille,
            @RequestParam(required=false) Integer selection) {
        if (!List.of("rubriques", "types").contains(domaine) || page < 1
                || !List.of(10,25,50,1000).contains(taille) || !List.of("ordre","libelle","code").contains(tri))
            throw new IllegalArgumentException("Les critères du catalogue ne sont pas valides.");
        Map<String,Object> catalogue = (Map<String,Object>) getCatalogue().getBody();
        var rubriques = (List<Map<String,Object>>) catalogue.get("rubriques");
        var types = (List<Map<String,Object>>) catalogue.get("types");
        var etapeOptions = etapes();
        var methodeOptions = methodes();
        var arrondiOptions = arrondis();
        var conditionOptions = conditions();
        var niveauOptions = niveaux();
        var phaseLabels = labelIndex(choix("BRUT","Dans le brut","GAIN_NET","Ajout au net après impôt","RETENUE_NET","Retenue sur le net après impôt","COTISATIONS","Calcul des cotisations","IMPOT","Calcul de l’impôt","NET_FINAL","Net final","DOMAINE","Calculateur métier"));
        var etapeLabels = labelIndex(etapeOptions);
        var methodeLabels = labelIndex(methodeOptions);
        var arrondiLabels = labelIndex(arrondiOptions);
        var conditionLabels = labelIndex(conditionOptions);
        var niveauLabels = labelIndex(niveauOptions);
        Map<Object, Map<String,Object>> rubriqueParId = new java.util.HashMap<>();
        for (var rubrique : rubriques) rubriqueParId.put(rubrique.get("id"), rubrique);
        var rubriquesDediees = new java.util.HashSet<Object>();
        for (var type : types) {
            if (type.get("idRubrique") != null && !"VARIABLE_DAF".equals(type.get("nature"))) {
                rubriquesDediees.add(type.get("idRubrique"));
            }
        }
        try (Connection c = dataSource.getConnection()) {
            var lockedRubriques = new java.util.HashSet<Integer>();
            try (var ps = c.prepareStatement("SELECT id_rubrique FROM paie_avance_acompte a JOIN ref_type_mouvement_paie t USING(id_type_mouvement) WHERE id_rubrique IS NOT NULL UNION SELECT id_rubrique FROM bulletin_paie_ligne UNION SELECT id_rubrique_origine FROM paie_rubrique WHERE id_rubrique_origine IS NOT NULL UNION SELECT id_rubrique FROM paie_rubrique WHERE id_rubrique_origine IS NOT NULL"); var rs = ps.executeQuery()) {
                while (rs.next()) lockedRubriques.add(rs.getInt(1));
            }
            var usedTypes = new java.util.HashSet<Integer>();
            try (var ps = c.prepareStatement("SELECT DISTINCT id_type_mouvement FROM paie_avance_acompte WHERE id_type_mouvement IS NOT NULL"); var rs = ps.executeQuery()) {
                while (rs.next()) usedTypes.add(rs.getInt(1));
            }
            for (var r : rubriques) {
                boolean manual = "CONFIGURABLE".equals(r.get("niveauConfiguration"));
                r.put("peutModifier", manual);
                r.put("traitementVerrouille", !manual || lockedRubriques.contains((Integer)r.get("id")));
                boolean dedicated = rubriquesDediees.contains(r.get("id"));
                r.put("peutVersionner", manual && !dedicated && r.get("dateFinEffet")==null);
                r.put("etapeLibelle", manual ? (r.get("etapeCalcul")==null ? "Étape à renseigner" : label(etapeLabels, r.get("etapeCalcul"))) : label(phaseLabels, r.get("phaseCalcul")));
                r.put("methodeLibelle", manual ? label(methodeLabels, r.get("methodeCalcul")) : r.get("sourceCalcul"));
                r.put("arrondiLibelle", manual ? label(arrondiLabels, r.get("politiqueArrondi")) : r.get("arrondiCalculateur"));
                r.put("conditionLibelle", label(conditionLabels, r.get("conditionApplication")));
                r.put("niveauLibelle", "NOTES_DE_FRAIS".equals(r.get("calculateur")) ? "Circuit dédié" : label(niveauLabels, r.get("niveauConfiguration")));
                String calculator=Objects.toString(r.get("calculateur"),"");
                String code=Objects.toString(r.get("code"),"");
                String target=switch(calculator) {
                    case "CONGES_ET_RUPTURE" -> {
                        if ("RETENUE_CONGE".equals(code) || "INDEMNITE_CONGE".equals(code) || "DEDUCTION_CONGE_BASE".equals(code) || "INDEMNITE_CONGE_PAYE".equals(code)) {
                            yield "/admin/parametres?module=paie&tab=parametres_globaux_paie";
                        }
                        yield "/admin/parametres?module=conges&tab=regles_acquisition";
                    }
                    case "PRIMES_METIER" -> "/admin/parametres?module=paie&tab=parametres_globaux_paie";
                    case "COTISATIONS" -> "/admin/parametres?module=paie&tab=cotisations_irsa";
                    case "FISCALITE", "HEURES_MAJOREES" -> "/admin/parametres?module=paie&tab=reglementation_fiscale";
                    default -> null;
                };
                r.put("parametrageUrl",target);
            }
            for (var t : types) {
                boolean manual = "VARIABLE_DAF".equals(t.get("nature"));
                t.put("peutModifier", true);
                t.put("liaisonVerrouillee", !manual || usedTypes.contains((Integer)t.get("id")));
                t.put("identiteVerrouillee", true);
                t.put("niveauConfiguration", manual ? "CONFIGURABLE" : "SEMI_CONFIGURABLE");
                t.put("niveauLibelle", manual ? "Configurable" : "Circuit dédié");
                t.put("rubrique", rubriqueParId.get(t.get("idRubrique")));
            }
        } catch (Exception e) { throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e); }
        var all = "types".equals(domaine) ? types : rubriques;
        Map<Object, Map<String,Object>> itemParId = "types".equals(domaine)
                ? types.stream().collect(java.util.stream.Collectors.toMap(item -> item.get("id"), item -> item, (first, ignored) -> first))
                : rubriqueParId;
        Map<String,Object> selectionItem = selection == null ? null : itemParId.get(selection);
        String q = java.text.Normalizer.normalize(recherche.trim().toLowerCase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        var filtered = all.stream().filter(item -> {
            Object linked = item.get("rubrique");
            String linkedText = linked instanceof Map<?,?> rule
                    ? Objects.toString(rule.get("code"), "") + " " + Objects.toString(rule.get("libelle"), "") : "";
            String hay = java.text.Normalizer.normalize((item.get("code")+" "+item.get("libelle")+" "+Objects.toString(item.get("description"),"")+" "+linkedText).toLowerCase(java.util.Locale.ROOT),java.text.Normalizer.Form.NFD).replaceAll("\\p{M}","");
            return hay.contains(q) && (sens.isBlank() || sens.equals(item.get("sens")))
                && (niveau.isBlank() || niveau.equals(item.get("niveauConfiguration")))
                && (statut.isBlank() || ("actif".equals(statut) == Boolean.TRUE.equals(item.get("estActif"))));
        }).sorted((a,b) -> {
            if ("ordre".equals(tri)) { int cmp=Integer.compare(((Number)a.get("ordreAffichage")).intValue(),((Number)b.get("ordreAffichage")).intValue()); if(cmp!=0)return cmp; }
            String key="libelle".equals(tri)?"libelle":"code";
            return Objects.toString(a.get(key),"").compareToIgnoreCase(Objects.toString(b.get(key),""));
        }).toList();
        int actualPage=Math.min(page, Math.max(1,(filtered.size()+taille-1)/taille));
        int start=(actualPage-1)*taille;
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("items",filtered.subList(start,Math.min(start+taille,filtered.size())));
        result.put("totalElements",filtered.size()); result.put("page",actualPage); result.put("taille",taille);
        result.put("selection", selectionItem);
        result.put("options",Map.of("categories",categories(),"etapes",etapeOptions,"methodes",methodeOptions,"arrondis",arrondiOptions,"conditions",conditionOptions,"niveaux",niveauOptions,"sens",List.of(Map.of("value","GAIN","label","Gain","categorieType","PRIME"),Map.of("value","RETENUE","label","Retenue","categorieType","RETENUE_DIVERSE")),"statuts",choix("actif","Actifs","inactif","Inactifs"),"tris",choix("ordre","Ordre du catalogue","libelle","Libellé","code","Code"),"assiettes",List.of(Map.of("key","soumisIrsa","label","IRSA"),Map.of("key","soumisCnaps","label","CNaPS"),Map.of("key","soumisSante","label","Santé"),Map.of("key","soumisFmfp","label","FMFP","heriteDe","soumisCnaps"))));
        result.put("rubriquesCompatibles",rubriques.stream().filter(r -> Boolean.TRUE.equals(r.get("estActif")) && "CONFIGURABLE".equals(r.get("niveauConfiguration")) && "MONTANT".equals(r.get("typeValeur"))).toList());
        result.put("defautsRubrique",Map.ofEntries(Map.entry("categorie","PRIME"),Map.entry("sens","GAIN"),Map.entry("typeValeur","MONTANT"),Map.entry("etapeCalcul","BRUT"),Map.entry("methodeCalcul","MONTANT"),Map.entry("politiqueArrondi","CENTIME"),Map.entry("conditionApplication","TOUS"),Map.entry("soumisIrsa",true),Map.entry("soumisCnaps",true),Map.entry("soumisSante",true),Map.entry("soumisFmfp",true),Map.entry("estActif",true),Map.entry("estFixe",false),Map.entry("ordreAffichage",10)));
        result.put("defautsType",Map.of("sens","GAIN","categorie","PRIME","estActif",true,"ordreAffichage",100,"estProratise",false,"estPlafonneDixieme",false));
        result.put("validation", Map.of("codePattern","[A-Z][A-Z0-9_]{2,39}","codeMessage","Utilisez 3 à 40 caractères : lettres majuscules, chiffres ou tirets bas ; commencez par une lettre."));
        Object selectionFamilleId = selectionItem == null ? null
                : (selectionItem.get("idRubriqueOrigine") == null ? selectionItem.get("id") : selectionItem.get("idRubriqueOrigine"));
        result.put("famille", selectionFamilleId == null || "types".equals(domaine) ? List.of() : rubriques.stream().filter(r ->
                Objects.equals(r.get("idRubriqueOrigine") == null ? r.get("id") : r.get("idRubriqueOrigine"), selectionFamilleId))
                .sorted(java.util.Comparator.comparing(r -> Objects.toString(r.get("dateDebutEffet"), ""))).toList());
        return ResponseEntity.ok(result);
    }

    private static List<Map<String,Object>> choix(String... values) {
        var options = new ArrayList<Map<String,Object>>();
        for(int i=0;i<values.length;i+=2) options.add(Map.of("value",values[i],"label",values[i+1]));
        return options;
    }
    private static List<Map<String,Object>> categories() {
        var options=choix("PRIME","Prime","INDEMNITE_IMPOSABLE","Indemnité","INDEMNITE_NON_IMPOSABLE","Indemnité hors assiettes","RETENUE_DIVERSE","Retenue sur le net");
        return options.stream().map(o -> {
            var r=new LinkedHashMap<String,Object>(o);
            boolean retenue="RETENUE_DIVERSE".equals(o.get("value"));
            boolean hors=retenue || "INDEMNITE_NON_IMPOSABLE".equals(o.get("value"));
            r.put("defauts",Map.of("sens",retenue?"RETENUE":"GAIN","etapeCalcul",retenue?"RETENUE_NET":hors?"GAIN_NET":"BRUT","soumisIrsa",!hors,"soumisCnaps",!hors,"soumisSante",!hors,"soumisFmfp",!hors));
            return (Map<String,Object>)r;
        }).toList();
    }
    private static List<Map<String,Object>> etapes() {
        return List.of(Map.of("value","BRUT","label","Dans le brut","sens","GAIN","assiettesModifiables",true),Map.of("value","GAIN_NET","label","Ajout au net après impôt","sens","GAIN","assiettesModifiables",false),Map.of("value","RETENUE_NET","label","Retenue sur le net après impôt","sens","RETENUE","assiettesModifiables",false));
    }
    private static List<Map<String,Object>> methodes(){return choix("MONTANT","Montant saisi","QUANTITE_TAUX","Quantité × taux unitaire","POURCENTAGE_SALAIRE_BASE","Pourcentage du salaire de base");}
    private static List<Map<String,Object>> arrondis(){return choix("CENTIME","Au centime","ARIARY","À l’ariary","DIZAINE","À la dizaine d’ariary");}
    private static List<Map<String,Object>> conditions(){return choix("TOUS","Tous les salariés","ASSUJETTI_IRSA","Assujettis à l’IRSA","ASSUJETTI_CNAPS","Assujettis à la CNaPS","ASSUJETTI_SANTE","Assujettis à la santé");}
    private static List<Map<String,Object>> niveaux(){return choix("CONFIGURABLE","Configurable","SEMI_CONFIGURABLE","Partiellement configurable","SYSTEM","Système");}
    private static Map<Object, String> labelIndex(List<Map<String,Object>> options) {
        Map<Object, String> labels = new java.util.HashMap<>();
        for (var option : options) labels.putIfAbsent(option.get("value"), Objects.toString(option.get("label"), "Calculateur dédié"));
        return labels;
    }
    private static String label(Map<Object, String> labels, Object value) { return labels.getOrDefault(value, "Calculateur dédié"); }

    @GetMapping
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> listerTypesMouvement(@RequestParam(name = "tous", defaultValue = "false") boolean tous) {
        try (Connection c = dataSource.getConnection()) {
            List<RefTypeMouvementPaie> list = tous ? RefTypeMouvementPaie.listerTous(c) : RefTypeMouvementPaie.listerActifs(c);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_READ') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> getParId(@PathVariable int id) {
        try (Connection c = dataSource.getConnection()) {
            RefTypeMouvementPaie found = RefTypeMouvementPaie.trouverParId(id, c);
            if (found == null) {
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de mouvement introuvable.");
            }
            return ResponseEntity.ok(found);
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> creerTypeMouvement(@RequestBody RefTypeMouvementPaieRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                validateRequest(c, req, null);
                RefTypeMouvementPaie entity = new RefTypeMouvementPaie();
                entity.setCode(req.getCode());
                entity.setLibelle(req.getLibelle());
                entity.setSens(req.getSens());
                entity.setCategorie(req.getCategorie());
                entity.setIdRubrique(req.getIdRubrique());
                entity.setEstProratise(req.isEstProratise());
                entity.setEstPlafonneDixieme(req.isEstPlafonneDixieme());
                entity.setEstActif(req.isEstActif());
                entity.setOrdreAffichage(req.getOrdreAffichage());
                entity.setDescription(req.getDescription());
                entity.setNature("VARIABLE_DAF");
                entity.setOwnerCode("DAF_PAIE");
                entity.setSaisieManuelleAutorisee(true);
                entity.setPeriodeObligatoire(true);
                entity.setMotifObligatoire(true);
                entity.setProvenanceDefaut("VARIABLE_DAF");

                entity.save(c);
                AuditLog.audit(c, "CREATION", "ref_type_mouvement_paie", String.valueOf(entity.getIdTypeMouvement()), null, entity);

                c.commit();
                payrollService.invalidateRubriqueCache();
                return ResponseEntity.status(HttpStatus.CREATED).body(entity);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        ex instanceof IllegalArgumentException ? ex.getMessage() : "Impossible de créer ce type de mouvement de paie.");
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PAIE_WRITE') or hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> modifierTypeMouvement(@PathVariable int id, @RequestBody RefTypeMouvementPaieRequest req) {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                RefTypeMouvementPaie found = RefTypeMouvementPaie.trouverParId(id, c);
                if (found == null) {
                    throw new mg.galaxam.sirh.common.exception.SirhBusinessException(mg.galaxam.sirh.common.exception.SirhErrorCode.RESOURCE_NOT_FOUND, "Type de mouvement introuvable.");
                }

                validateRequest(c, req, found);

                found.setCode(req.getCode());
                found.setLibelle(req.getLibelle());
                found.setSens(req.getSens());
                found.setCategorie(req.getCategorie());
                found.setIdRubrique(req.getIdRubrique());
                found.setEstProratise(req.isEstProratise());
                found.setEstPlafonneDixieme(req.isEstPlafonneDixieme());
                found.setEstActif(req.isEstActif());
                found.setOrdreAffichage(req.getOrdreAffichage());
                found.setDescription(req.getDescription());

                found.update(c);
                AuditLog.audit(c, "MODIFICATION", "ref_type_mouvement_paie", String.valueOf(id), null, found);

                c.commit();
                payrollService.invalidateRubriqueCache();
                return ResponseEntity.ok(found);
            } catch (Exception ex) {
                c.rollback();
                throw new mg.galaxam.sirh.common.exception.SirhBusinessException(
                        mg.galaxam.sirh.common.exception.SirhErrorCode.VALIDATION_ERROR,
                        ex instanceof IllegalArgumentException ? ex.getMessage() : "Impossible de modifier ce type de mouvement de paie.");
            }
        } catch (Exception e) {
            throw mg.galaxam.sirh.common.exception.SirhExceptionTranslator.propagate(e);
        }
    }

    private void validateRequest(Connection c, RefTypeMouvementPaieRequest req, RefTypeMouvementPaie current) throws Exception {
        if (req == null || req.getCode() == null || !req.getCode().matches("[A-Z][A-Z0-9_]{2,39}")
                || req.getLibelle() == null || req.getLibelle().isBlank()
                || req.getCategorie() == null || req.getCategorie().isBlank()
                || req.getIdRubrique() == null || !("GAIN".equals(req.getSens()) || "RETENUE".equals(req.getSens()))) {
            throw new IllegalArgumentException("Code, libellé, sens et rubrique sont obligatoires.");
        }
        if (current != null && (!current.getCode().equals(req.getCode()) || !current.getSens().equals(req.getSens()))) {
            throw new IllegalArgumentException("Le code et le sens d’un type existant ne peuvent pas être modifiés.");
        }
        if (current == null && RefTypeMouvementPaie.trouverParCode(req.getCode(), c) != null) {
            throw new IllegalArgumentException("Ce code de variable existe déjà.");
        }
        if (current == null && !List.of("PRIME", "INDEMNITE_IMPOSABLE", "INDEMNITE_NON_IMPOSABLE", "RETENUE_DIVERSE").contains(req.getCategorie())) {
            throw new IllegalArgumentException("La catégorie du type de variable n’est pas prise en charge.");
        }
        if (current == null && !("GAIN".equals(req.getSens()) && List.of("PRIME", "INDEMNITE_IMPOSABLE", "INDEMNITE_NON_IMPOSABLE").contains(req.getCategorie())
                || "RETENUE".equals(req.getSens()) && "RETENUE_DIVERSE".equals(req.getCategorie()))) {
            throw new IllegalArgumentException("La catégorie doit correspondre au sens du nouveau type.");
        }
        boolean rubriqueChanged = current == null || !Objects.equals(current.getIdRubrique(), req.getIdRubrique());
        try (PreparedStatement ps = c.prepareStatement("SELECT code, categorie, sens, etape_calcul, est_actif, est_fixe, type_valeur FROM paie_rubrique WHERE id_rubrique = ?")) {
            ps.setInt(1, req.getIdRubrique());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || (!req.getSens().equals(rs.getString("sens"))
                        && !(current != null && Objects.equals(current.getIdRubrique(), req.getIdRubrique())))
                        || (rubriqueChanged && (!rs.getBoolean("est_actif") || rs.getBoolean("est_fixe")
                        || !"MONTANT".equals(rs.getString("type_valeur"))))) {
                    throw new IllegalArgumentException("La rubrique doit être active et avoir le même sens que la variable.");
                }
                if (current == null && (!req.getCategorie().equals(rs.getString("categorie"))
                        || !"CONFIGURABLE".equals(mg.galaxam.sirh.paie.domain.engine.PayrollCalculatorRegistry.describe(rs.getString("code"), rs.getString("categorie"), rs.getBoolean("est_fixe"), rs.getString("etape_calcul")).niveau())
                        || ("INDEMNITE_NON_IMPOSABLE".equals(req.getCategorie())
                            && !"GAIN_NET".equals(rs.getString("etape_calcul"))))) {
                    throw new IllegalArgumentException("Choisissez une rubrique compatible avec la catégorie et le circuit de cette variable.");
                }
            }
        }
        if (current != null && rubriqueChanged) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM paie_avance_acompte WHERE id_type_mouvement = ? LIMIT 1")) {
                ps.setInt(1, current.getIdTypeMouvement());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) throw new IllegalArgumentException("Ce type possède déjà des mouvements : sa rubrique ne peut plus être changée.");
                }
            }
        }
        if (current != null && !"VARIABLE_DAF".equals(current.getNature())
                && !Objects.equals(current.getIdRubrique(), req.getIdRubrique())) {
            throw new IllegalArgumentException("La rubrique d’un type métier dédié ne peut pas être modifiée ici.");
        }
    }
}
