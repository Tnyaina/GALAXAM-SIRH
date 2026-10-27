/**
 * Constantes et référentiels pour la gestion des dossiers collaborateurs (SIRH GALAXAM).
 * Centralise les listes de référence, statuts légaux et suggestions d'autocomplétion.
 */

export const LIENS_PARENTE = [
    { value: 'Conjoint', label: 'Conjoint(e)' },
    { value: 'Pere', label: 'Père' },
    { value: 'Mere', label: 'Mère' },
    { value: 'Enfant', label: 'Enfant' },
    { value: 'Frere', label: 'Frère' },
    { value: 'Soeur', label: 'Sœur' },
    { value: 'Autre', label: 'Autre' }
];

export const LIENS_PARENTE_SUGGESTIONS = [
    'Conjoint',
    'Pere',
    'Mere',
    'Enfant',
    'Frere',
    'Soeur',
    'Autre'
];

export const SITUATIONS_FAMILIALES = [
    { value: 'Celibataire', label: 'Célibataire' },
    { value: 'Marie', label: 'Marié(e)' },
    { value: 'Divorce', label: 'Divorcé(e)' },
    { value: 'Veuf', label: 'Veuf / Veuve' }
];

export const GENRES_SEXE = [
    { value: 'M', label: 'Masculin (M)' },
    { value: 'F', label: 'Féminin (F)' }
];

export const GENRES_ENFANT = [
    { value: 'M', label: 'Garçon (M)' },
    { value: 'F', label: 'Fille (F)' }
];

/**
 * Valeurs de secours (fallback) pour les règles fiscales IRSA (Loi de Finances malgache).
 * En production, ces paramètres sont chargés dynamiquement depuis la base de données
 * (table paie_config_legale via useFiscalConfig / paieConfigService).
 */
export const REGLES_FISCALES_IRSA_DEFAUT = {
    DEDUCTION_PAR_CHARGE_AR: 2000,
    AGE_LIMITE_DEFAUT: 21,
    AGE_LIMITE_ETUDIANT: 25,
    AGE_MIN_LEGAL_EMPLOYE: 16
};

// Rétro-compatibilité
export const REGLES_FISCALES_IRSA = REGLES_FISCALES_IRSA_DEFAUT;

export const PROFESSIONS_CONJOINT_SUGGESTIONS = [
    'Comptable',
    'Enseignant(e) / Professeur',
    'Médecin / Personnel de santé',
    'Infirmier(e)',
    'Pharmacien(ne)',
    'Ingénieur(e)',
    'Développeur(se) / Informaticien',
    'Commerçant(e)',
    'Agent Commercial / Vendeur',
    'Fonctionnaire / Agent public',
    'Cadre Administratif',
    'Secrétaire / Assistant(e)',
    'Juriste / Avocat(e)',
    'Artisan(e)',
    'Technicien(ne)',
    'Chauffeur / Transporteur',
    'Agriculteur(rice) / Éleveur',
    'Chef d\'entreprise / Gérant',
    'Étudiant(e)',
    'Retraité(e)',
    'Sans profession / Au foyer',
    'Autre'
];
