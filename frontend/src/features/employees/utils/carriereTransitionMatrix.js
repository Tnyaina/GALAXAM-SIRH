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
            'CONFIRMATION_ESSAI',
            'RENOUVELLEMENT_ESSAI',
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
            'CONFIRMATION_ESSAI',
            'RENOUVELLEMENT_ESSAI',
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
        ],
        disabledReasons: {
            MUTATION: 'Les prestataires externes ne peuvent pas faire l\'objet de mutations internes de salariés.',
            PROMOTION: 'Les prestataires externes ne peuvent pas faire l\'objet de promotions internes de salariés.',
            REVALORISATION: 'Les honoraires de prestation sont régis par négociation commerciale.',
            REORGANISATION: 'Non applicable à un contrat de prestation.',
            REPRISE_ANCIENNETE: 'Un prestataire externe ne cumule pas d\'ancienneté de salarié.',
            EMBAUCHE_APRES_STAGE: 'Réservé aux stagiaires conventionnés.',
            RENOUVELLEMENT_CDD: 'Nécessite un contrat CDD actif.',
        },
    },
    TEMPORAIRE: {
        allowedEvents: [
            'TRANSFORMATION_CDI',
        ],
        disabledReasons: {
            MUTATION: 'Non applicable à un travailleur temporaire.',
            PROMOTION: 'Non applicable à un travailleur temporaire.',
            REVALORISATION: 'Non applicable à un travailleur temporaire.',
            REORGANISATION: 'Non applicable à un travailleur temporaire.',
            REPRISE_ANCIENNETE: 'Non applicable à un travailleur temporaire.',
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
 * Codes d'événements RH exclus des formulaires de transition courante
 * (gérés par des modales dédiées ou réservés à des ruptures/embauches initiales).
 */
export const EXCLUDED_EVENT_CODES = [
    'EMBAUCHE_INITIALE', 'RUPTURE_CONTRAT', 'DEMISSION', 'LICENCIEMENT',
    'RETRAITE', 'FIN_CONTRAT', 'CONFIRMATION_ESSAI', 'RENOUVELLEMENT_ESSAI',
    'DECES', 'ABANDON_POSTE', 'RUPTURE_NEGOCIEE', 'LICENCIEMENT_FAUTE',
    'LICENCIEMENT_ECONOMIQUE', 'FIN_PERIODE_ESSAI', 'DEPART_RETRAITE',
    'RUPTURE_CONVENTIONNELLE', 'RUPTURE_ESSAI'
];

export const DEFAULT_RELEVANT_EVENTS = [
    { code: 'MUTATION', libelle: 'Changement de site ou de service' },
    { code: 'PROMOTION', libelle: 'Promotion ou changement de fonction' },
    { code: 'REVALORISATION', libelle: 'Revalorisation salariale' },
    { code: 'TRANSFORMATION_CDI', libelle: 'Passage CDD vers CDI' },
    { code: 'RENOUVELLEMENT_CDD', libelle: 'Renouvellement de CDD' },
    { code: 'EMBAUCHE_APRES_STAGE', libelle: 'Passage Stage vers CDD/CDI' },
    { code: 'REPRISE_CONSULTANT', libelle: 'Embauche d un retraité comme consultant' },
    { code: 'REPRISE_ANCIENNETE', libelle: 'Réintégration / Reprise d ancienneté' },
    { code: 'REORGANISATION', libelle: 'Réorganisation Interne' },
];

/**
 * Détecte le type de contrat source normalisé (CDI, CDD, STAGE, PRESTATION, TEMPORAIRE).
 * 
 * @param {object|null} contratActif
 * @param {Array<object>} typesContrat
 * @returns {string} Code normalisé
 */
export function detectSourceContractCode(contratActif, typesContrat = []) {
    if (!contratActif) return 'CDI';
    if (contratActif.codeTypeContrat) return String(contratActif.codeTypeContrat).toUpperCase();
    const found = typesContrat.find(tc => String(tc.idTypeContrat) === String(contratActif.idTypeContrat));
    if (found?.code) return String(found.code).toUpperCase();
    const lib = (contratActif.libelleTypeContrat || '').toUpperCase();
    if (lib.includes('STAGE')) return 'STAGE';
    if (lib.includes('CDD') || lib.includes('DÉTERMINÉE') || lib.includes('DETERMINEE')) return 'CDD';
    if (lib.includes('PRESTATION') || lib.includes('CONSULTANT')) return 'PRESTATION';
    if (lib.includes('TEMPORAIRE')) return 'TEMPORAIRE';
    return 'CDI';
}

/**
 * Retourne la liste des types d'événements pertinents avec leur statut d'éligibilité.
 * 
 * @param {Array<object>} typesEvenementList
 * @param {string} sourceContractCode
 * @returns {Array<object>}
 */
export function getRelevantEventTypes(typesEvenementList = [], sourceContractCode = 'CDI') {
    const available = typesEvenementList.filter(ev => {
        const code = (ev.code || '').toUpperCase();
        if (code === 'EMBAUCHE_APRES_STAGE' || code === 'REPRISE_ANCIENNETE') return true;
        if (EXCLUDED_EVENT_CODES.includes(code)) return false;
        if (
            code.startsWith('RUPTURE') ||
            code.startsWith('ESSAI') ||
            code.startsWith('FIN_') ||
            (code.startsWith('EMBAUCHE') && code !== 'EMBAUCHE_APRES_STAGE') ||
            code.startsWith('DEMISSION') ||
            code.startsWith('LICENCIEMENT')
        ) {
            return false;
        }
        return true;
    });

    const list = available.length > 0 ? available : DEFAULT_RELEVANT_EVENTS;

    return list.map(ev => {
        const { isEligible, disabledReason } = getEventEligibility(ev.code, sourceContractCode);
        return {
            ...ev,
            isEligible,
            disabledReason
        };
    });
}

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

