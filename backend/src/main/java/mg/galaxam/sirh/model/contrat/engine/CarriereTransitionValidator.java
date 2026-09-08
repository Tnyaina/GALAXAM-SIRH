package mg.galaxam.sirh.model.contrat.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validateur d'intégrité métier pour les transitions de carrière et avenants contractuels.
 * Miroir backend exhaustif et opposable de `carriereTransitionMatrix.js`.
 * 
 * Garantit qu'aucun mouvement contractuel illégal ou incohérent ne peut être
 * enregistré via l'API, indépendamment des contrôles d'interface utilisateur.
 */
public final class CarriereTransitionValidator {

    private CarriereTransitionValidator() {}

    // Règles d'éligibilité des événements par type de contrat source
    private static final Map<String, Set<String>> EVENEMENTS_AUTORISES_PAR_SOURCE = Map.of(
            "STAGE", Set.of("EMBAUCHE_APRES_STAGE"),
            "CDD", Set.of(
                    "TRANSFORMATION_CDI",
                    "RENOUVELLEMENT_CDD",
                    "REPRISE_ANCIENNETE",
                    "MUTATION",
                    "PROMOTION",
                    "REVALORISATION",
                    "REORGANISATION"
            ),
            "CDI", Set.of(
                    "REPRISE_ANCIENNETE",
                    "MUTATION",
                    "PROMOTION",
                    "REVALORISATION",
                    "REORGANISATION",
                    "REPRISE_CONSULTANT"
            ),
            "PRESTATION", Set.of(
                    "TRANSFORMATION_CDI",
                    "REPRISE_ANCIENNETE",
                    "MUTATION",
                    "PROMOTION",
                    "REVALORISATION",
                    "REORGANISATION"
            ),
            "TEMPORAIRE", Set.of(
                    "TRANSFORMATION_CDI",
                    "REPRISE_ANCIENNETE",
                    "MUTATION",
                    "PROMOTION",
                    "REVALORISATION",
                    "REORGANISATION"
            )
    );

    // Contraintes cibles par événement
    public record ContraintesEvenement(
            List<String> typesContratAutorises,
            List<String> typesIntervenantAutorises,
            boolean autorisePeriodeEssai
    ) {}

    private static final Map<String, ContraintesEvenement> CONTRAINTES_PAR_EVENEMENT = Map.of(
            "EMBAUCHE_APRES_STAGE", new ContraintesEvenement(List.of("CDI", "CDD"), List.of("PERMANENT"), true),
            "TRANSFORMATION_CDI", new ContraintesEvenement(List.of("CDI"), List.of("PERMANENT"), false),
            "RENOUVELLEMENT_CDD", new ContraintesEvenement(List.of("CDD"), List.of("PERMANENT"), false),
            "REPRISE_CONSULTANT", new ContraintesEvenement(List.of("PRESTATION"), List.of("CONSULTANT"), false)
    );

    /**
     * Valide l'éligibilité et la conformité légale d'une transition de carrière.
     *
     * @param codeTypeContratSource Code du type de contrat actuel (ex: STAGE, CDD, CDI, PRESTATION, TEMPORAIRE)
     * @param codeTypeEvenement Code de l'événement RH initié (ex: TRANSFORMATION_CDI, RENOUVELLEMENT_CDD)
     * @param codeTypeContratCible Code du nouveau contrat (si applicable)
     * @param codeTypeIntervenantCible Code du nouveau statut/régime intervenant (si applicable)
     * @param dureeEssaiMois Durée de la période d'essai stipulée (si applicable)
     * @throws IllegalArgumentException si la transition est illégale ou incohérente
     */
    public static void validerTransition(
            String codeTypeContratSource,
            String codeTypeEvenement,
            String codeTypeContratCible,
            String codeTypeIntervenantCible,
            Integer dureeEssaiMois
    ) {
        if (codeTypeEvenement == null || codeTypeEvenement.isBlank()) {
            throw new IllegalArgumentException("Le type d'événement RH est obligatoire.");
        }

        String source = codeTypeContratSource != null ? codeTypeContratSource.trim().toUpperCase() : "";
        String evenement = codeTypeEvenement.trim().toUpperCase();

        // 1. Vérification de l'éligibilité du contrat source
        if (!source.isEmpty() && EVENEMENTS_AUTORISES_PAR_SOURCE.containsKey(source)) {
            Set<String> autorises = EVENEMENTS_AUTORISES_PAR_SOURCE.get(source);
            if (!autorises.contains(evenement)) {
                throw new IllegalArgumentException(genererMessageIneligibilite(source, evenement));
            }
        }

        // Cas particulier Stagiaire : aucun avenant interne (mutation/promotion/revalorisation) autorisé sans embauche
        if ("STAGE".equals(source) && !"EMBAUCHE_APRES_STAGE".equals(evenement)) {
            throw new IllegalArgumentException(
                    "Un collaborateur sous convention de Stage ne peut pas faire l'objet d'un avenant interne. Veuillez procéder à un 'Passage Stage vers CDD/CDI'.");
        }

        // 2. Vérification des contraintes sur le nouveau contrat cible
        if (CONTRAINTES_PAR_EVENEMENT.containsKey(evenement)) {
            ContraintesEvenement regle = CONTRAINTES_PAR_EVENEMENT.get(evenement);

            // Vérification du type de contrat cible
            if (codeTypeContratCible != null && !codeTypeContratCible.isBlank()) {
                String cibleContrat = codeTypeContratCible.trim().toUpperCase();
                if (!regle.typesContratAutorises().contains(cibleContrat)) {
                    throw new IllegalArgumentException(
                            "Pour l'événement '" + evenement + "', le type de contrat cible doit être parmi "
                                    + regle.typesContratAutorises() + " (reçu: " + cibleContrat + ").");
                }
            }

            // Vérification du type d'intervenant cible
            if (codeTypeIntervenantCible != null && !codeTypeIntervenantCible.isBlank()) {
                String cibleIntervenant = codeTypeIntervenantCible.trim().toUpperCase();
                if (!regle.typesIntervenantAutorises().contains(cibleIntervenant)) {
                    throw new IllegalArgumentException(
                            "Pour l'événement '" + evenement + "', le régime d'intervenant cible doit être parmi "
                                    + regle.typesIntervenantAutorises() + " (reçu: " + cibleIntervenant + ").");
                }
            }

            // Vérification de la période d'essai
            if (!regle.autorisePeriodeEssai()) {
                if (dureeEssaiMois != null && dureeEssaiMois > 0) {
                    throw new IllegalArgumentException(
                            "Une nouvelle période d'essai est strictement interdite par le Code du Travail pour l'événement '"
                                    + evenement + "'.");
                }
            }
        }
    }

    private static String genererMessageIneligibilite(String source, String evenement) {
        return switch (evenement) {
            case "EMBAUCHE_APRES_STAGE" -> "L'événement 'Passage Stage vers CDD/CDI' est réservé exclusivement aux collaborateurs sous convention de Stage (contrat actuel: " + source + ").";
            case "TRANSFORMATION_CDI" -> "La transformation en CDI requiert un contrat source de type CDD, Prestation ou Temporaire (contrat actuel: " + source + ").";
            case "RENOUVELLEMENT_CDD" -> "Le renouvellement de CDD requiert un contrat source de type CDD (contrat actuel: " + source + ").";
            case "REPRISE_CONSULTANT" -> "Le passage en consultant indépendant requiert un contrat source de type CDI permanent (contrat actuel: " + source + ").";
            default -> "L'événement '" + evenement + "' n'est pas autorisé pour un contrat source de type '" + source + "'.";
        };
    }
}
