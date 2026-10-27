export const MOTIFS_SORTIE_STC = [
    'DEMISSION', 'LICENCIEMENT', 'LICENCIEMENT_ECONOMIQUE', 'CESSATION_ACTIVITE',
    'FIN_CONTRAT', 'RUPTURE_ESSAI', 'RUPTURE_CONVENTIONNELLE',
    'RETRAITE', 'DEPART_RETRAITE', 'AUTRE',
];

export const motifSortiePourEvenement = (code) => code === 'DEPART_RETRAITE' ? 'DEPART_RETRAITE' : code;

export const motifsPourEvenement = (code) => {
    if (code === 'LICENCIEMENT') return ['LICENCIEMENT', 'LICENCIEMENT_ECONOMIQUE', 'CESSATION_ACTIVITE'];
    if (code === 'DEPART_RETRAITE') return ['DEPART_RETRAITE', 'RETRAITE'];
    return [motifSortiePourEvenement(code)];
};

export const optionsMotifsSortie = (referentiel) => (Array.isArray(referentiel) ? referentiel : [])
    .filter(item => MOTIFS_SORTIE_STC.includes(item.code) && item.actif !== false)
    .map(item => ({ value: item.code, label: item.libelle }));
