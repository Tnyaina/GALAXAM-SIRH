/** Prépare une intention de saisie à partir de l'horaire fourni par le serveur. */
export function getConfiguredStandardHours(siteParams) {
    if (siteParams?.horaireContinu === true) {
        if (!siteParams.heureDebutMatin || !siteParams.heureFinAm) return null;
        return {
            heureEntreeMatin: siteParams.heureDebutMatin,
            heureSortieMatin: '',
            heureEntreeAm: '',
            heureSortieAm: siteParams.heureFinAm,
            horaireContinu: true,
            horaireContinuJour: true
        };
    }
    const values = [
        siteParams?.heureDebutMatin,
        siteParams?.heureFinMatin,
        siteParams?.heureDebutAm,
        siteParams?.heureFinAm
    ];
    if (!values.every(Boolean)) return null;
    return {
        heureEntreeMatin: values[0],
        heureSortieMatin: values[1],
        heureEntreeAm: values[2],
        heureSortieAm: values[3]
    };
}

export function getConfiguredPause(siteParams) {
    if (siteParams?.horaireContinu || !siteParams?.heureFinMatin || !siteParams?.heureDebutAm) return null;
    return {
        heureSortieMatin: siteParams.heureFinMatin,
        heureEntreeAm: siteParams.heureDebutAm
    };
}
