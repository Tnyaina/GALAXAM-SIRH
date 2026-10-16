package mg.galaxam.sirh.paie.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Valeurs canoniques et libellés des rubriques manuelles, partagés par validation et API. */
public final class ManualRubriqueCatalog {
    public static final List<String> CATEGORY_CODES = List.of("PRIME", "INDEMNITE_IMPOSABLE", "INDEMNITE_NON_IMPOSABLE", "RETENUE_DIVERSE");
    public static final List<String> CONDITION_CODES = List.of("TOUS", "ASSUJETTI_IRSA", "ASSUJETTI_CNAPS", "ASSUJETTI_SANTE");
    public static final List<String> METHOD_CODES = List.of("MONTANT", "QUANTITE_TAUX", "POURCENTAGE_SALAIRE_BASE");
    public static final List<String> ROUNDING_CODES = List.of("CENTIME", "ARIARY", "DIZAINE");
    public static final Set<String> CATEGORIES = Set.copyOf(CATEGORY_CODES);
    public static final Set<String> CONDITIONS = Set.copyOf(CONDITION_CODES);
    public static final Set<String> METHODS = Set.copyOf(METHOD_CODES);
    public static final Set<String> ROUNDING = Set.copyOf(ROUNDING_CODES);
    private ManualRubriqueCatalog() {}

    public static List<Map<String, Object>> choices(String kind) {
        return switch (kind) {
            case "categories" -> categories();
            case "etapes" -> List.of(
                    option("BRUT", "Dans le brut", "sens", "GAIN", "assiettesModifiables", true),
                    option("GAIN_NET", "Ajout au net après impôt", "sens", "GAIN", "assiettesModifiables", false),
                    option("RETENUE_NET", "Retenue sur le net après impôt", "sens", "RETENUE", "assiettesModifiables", false));
            case "methodes" -> List.of(option(METHOD_CODES.get(0), "Montant saisi"), option(METHOD_CODES.get(1), "Quantité × taux unitaire"), option(METHOD_CODES.get(2), "Pourcentage du salaire de base"));
            case "arrondis" -> List.of(option(ROUNDING_CODES.get(0), "Au centime"), option(ROUNDING_CODES.get(1), "À l’ariary"), option(ROUNDING_CODES.get(2), "À la dizaine d’ariary"));
            case "conditions" -> List.of(option(CONDITION_CODES.get(0), "Tous les salariés"), option(CONDITION_CODES.get(1), "Assujettis à l’IRSA"), option(CONDITION_CODES.get(2), "Assujettis à la CNaPS"), option(CONDITION_CODES.get(3), "Assujettis à la santé"));
            default -> List.of();
        };
    }

    private static List<Map<String, Object>> categories() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<String> labels = List.of("Prime", "Indemnité", "Indemnité hors assiettes", "Retenue sur le net");
        for (int i = 0; i < CATEGORY_CODES.size(); i++) {
            String code = CATEGORY_CODES.get(i);
            boolean deduction = "RETENUE_DIVERSE".equals(code);
            boolean exempt = deduction || "INDEMNITE_NON_IMPOSABLE".equals(code);
            Map<String, Object> item = new LinkedHashMap<>(option(code, labels.get(i)));
            item.put("defauts", Map.of("sens", deduction ? "RETENUE" : "GAIN", "etapeCalcul", deduction ? "RETENUE_NET" : exempt ? "GAIN_NET" : "BRUT",
                    "soumisIrsa", !exempt, "soumisCnaps", !exempt, "soumisSante", !exempt, "soumisFmfp", !exempt));
            result.add(item);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> option(String value, String label, Object... attributes) {
        Map<String, Object> item = new LinkedHashMap<>(); item.put("value", value); item.put("label", label);
        for (int i = 0; i < attributes.length; i += 2) item.put((String) attributes[i], attributes[i + 1]);
        return item;
    }
}
