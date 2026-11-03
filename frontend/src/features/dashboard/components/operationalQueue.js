export const OPERATIONAL_GROUPS = [
    { id: 'decisions', label: 'Décisions attendues', description: 'Demandes et dossiers à valider' },
    { id: 'anomalies', label: 'Anomalies à résoudre', description: 'Écarts à corriger avant la suite du cycle' },
    { id: 'echeances', label: 'Échéances à anticiper', description: 'Situations à préparer dans les prochains jours' },
];

const GROUP_BY_CODE = {
    CONGES_A_VALIDER: 'decisions',
    BROUILLONS_RH: 'decisions',
    ANOMALIES_POINTAGE: 'anomalies',
    ECHEANCES_CONTRATS: 'echeances',
};

export function visibleOperationalActions(actions, hasPermission) {
    return actions.filter(item => !item.requiredAuthority || hasPermission(item.requiredAuthority));
}

export function groupOperationalActions(actions) {
    return OPERATIONAL_GROUPS.map(group => ({
        ...group,
        items: actions.filter(item => (GROUP_BY_CODE[item.code] || 'anomalies') === group.id),
    })).filter(group => group.items.length > 0);
}
