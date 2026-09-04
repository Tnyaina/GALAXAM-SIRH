/**
 * Matrice des Transitions de Carrière RH
 * Définit pour chaque type de contrat source :
 * - Les événements éligibles ou bloqués avec motif explicatif
 * - Les auto-sélections et verrouillages des champs Type de Contrat et Type d'Intervenant
 */

export const TRANSITION_RULES_BY_SOURCE = {
    STAGE: {
        allowedEvents: ['EMBAUCHE_APRES_STAGE'],
        disabledReasons: {
            TRANSFORMATION_CDI: 'Nécessite un contrat CDD actif.',
            RENOUVELLEMENT_CDD: 'Nécessite un contrat CDD actif.',
            REPRISE_ANCIENNETE: 'Non applicable à un stagiaire (utiliser Passage Stage vers CDD/CDI).',
            REPRISE_CONSULTANT: 'Non applicable à un stagiaire.',
            MUTATION: 'Un stagiaire doit d\'abord être embauché pour être muté.',
            PROMOTION: 'Un stagiaire doit d\'abord être embauché pour obtenir une classification.',
            REVALORISATION: 'Une convention de stage fait l\'objet d\'une gratification fixe.',
            REORGANISATION: 'Non applicable à un contrat de stage.',
        },
    },
    CDD: {
        allowedEvents: [
            'TRANSFORMATION_CDI',
            'RENOUVELLEMENT_CDD',
            'REPRISE_ANCIENNETE',
            'MUTATION',
            'PROMOTION',
            'REVALORISATION',
            'REORGANISATION',
        ],
        disabledReasons: {
            EMBAUCHE_APRES_STAGE: 'Le collaborateur est déjà sous contrat de travail (CDD).',
            REPRISE_CONSULTANT: 'Réservé aux fins de carrière / départs à la retraite.',
        },
    },
    CDI: {
        allowedEvents: [
            'REPRISE_ANCIENNETE',
            'MUTATION',
            'PROMOTION',
            'REVALORISATION',
            'REORGANISATION',
            'REPRISE_CONSULTANT',
        ],
        disabledReasons: {
            TRANSFORMATION_CDI: 'Le collaborateur est déjà sous contrat à durée indéterminée (CDI).',
            RENOUVELLEMENT_CDD: 'Non applicable à un contrat CDI en cours.',
            EMBAUCHE_APRES_STAGE: 'Le collaborateur est déjà sous contrat de travail permanent (CDI).',
        },
    },
    PRESTATION: {
        allowedEvents: [
            'TRANSFORMATION_CDI',
            'REPRISE_ANCIENNETE',
            'MUTATION',
            'PROMOTION',
            'REVALORISATION',
            'REORGANISATION',
        ],
        disabledReasons: {
            EMBAUCHE_APRES_STAGE: 'Réservé aux stagiaires conventionnés.',
            RENOUVELLEMENT_CDD: 'Nécessite un contrat CDD actif.',
        },
    },
    TEMPORAIRE: {
        allowedEvents: [
            'TRANSFORMATION_CDI',
            'REPRISE_ANCIENNETE',
            'MUTATION',
            'PROMOTION',
            'REVALORISATION',
            'REORGANISATION',
        ],
        disabledReasons: {
            EMBAUCHE_APRES_STAGE: 'Réservé aux stagiaires conventionnés.',
            RENOUVELLEMENT_CDD: 'Nécessite un contrat CDD actif.',
        },
    },
};

/**
 * Configuration cible par événement :
 * - defaultTypeContrat : code par défaut
 * - allowedTypesContrat : liste des codes autorisés
 * - lockTypeContrat : boolean (si true, choix figé)
 * - defaultTypeIntervenant : code par défaut
 * - allowedTypesIntervenant : liste des codes autorisés
 * - lockTypeIntervenant : boolean (si true, choix figé)
 */
export const EVENT_TARGET_CONFIG = {
    EMBAUCHE_APRES_STAGE: {
        defaultTypeContrat: 'CDI',
        allowedTypesContrat: ['CDI', 'CDD'],
        lockTypeContrat: false, // Laisse le choix entre CDI ou CDD
        defaultTypeIntervenant: 'PERMANENT',
        allowedTypesIntervenant: ['PERMANENT'],
        lockTypeIntervenant: true,
        autoCheckEssai: true,
        defaultEssaiMois: '3',
    },
    TRANSFORMATION_CDI: {
        defaultTypeContrat: 'CDI',
        allowedTypesContrat: ['CDI'],
        lockTypeContrat: true, // Forcé sur CDI
        defaultTypeIntervenant: 'PERMANENT',
        allowedTypesIntervenant: ['PERMANENT'],
        lockTypeIntervenant: true,
        autoCheckEssai: false,
        defaultEssaiMois: '0',
    },
    RENOUVELLEMENT_CDD: {
        defaultTypeContrat: 'CDD',
        allowedTypesContrat: ['CDD'],
        lockTypeContrat: true, // Forcé sur CDD
        defaultTypeIntervenant: 'PERMANENT',
        allowedTypesIntervenant: ['PERMANENT'],
        lockTypeIntervenant: true,
        autoCheckEssai: false, // Strictement interdit légalement
        defaultEssaiMois: '0',
    },
    REPRISE_CONSULTANT: {
        defaultTypeContrat: 'PRESTATION',
        allowedTypesContrat: ['PRESTATION'],
        lockTypeContrat: true,
        defaultTypeIntervenant: 'CONSULTANT',
        allowedTypesIntervenant: ['CONSULTANT'],
        lockTypeIntervenant: true,
        autoCheckEssai: false,
        defaultEssaiMois: '0',
    },
    REPRISE_ANCIENNETE: {
        defaultTypeContrat: 'CDI',
        allowedTypesContrat: ['CDI', 'CDD'],
        lockTypeContrat: false,
        defaultTypeIntervenant: 'PERMANENT',
        allowedTypesIntervenant: ['PERMANENT'],
        lockTypeIntervenant: true,
        autoCheckEssai: false,
        defaultEssaiMois: '0',
    },
};

/**
 * Retourne l'éligibilité d'un événement selon le code de contrat source
 */
export function getEventEligibility(eventCode, sourceContractCode) {
    const normSource = (sourceContractCode || 'CDI').toUpperCase();
    const rules = TRANSITION_RULES_BY_SOURCE[normSource] || TRANSITION_RULES_BY_SOURCE.CDI;

    const isEligible = rules.allowedEvents.includes(eventCode);
    const disabledReason = isEligible ? null : (rules.disabledReasons[eventCode] || 'Incompatible avec le contrat actuel.');

    return { isEligible, disabledReason };
}
