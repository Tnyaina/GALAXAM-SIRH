/** Prépare une intention de saisie à partir de l'horaire fourni par le serveur. */
export function getConfiguredStandardHours(siteParams) {
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
        heureSortieAm: values[3],
        // La continuité se choisit explicitement par journée; le remplissage standard
        // suit toujours les quatre plages configurées du site.
        horaireContinu: false,
        horaireContinuJour: false
    };
}

/** Résumé fidèle des plages qui seront appliquées, sans déduire de pause implicite. */
export function getConfiguredHoursPreview(siteParams) {
    const hours = getConfiguredStandardHours(siteParams);
    if (!hours) return null;

    const periods = hours.horaireContinu
        ? [[hours.heureEntreeMatin, hours.heureSortieAm]]
        : [
            [hours.heureEntreeMatin, hours.heureSortieMatin],
            [hours.heureEntreeAm, hours.heureSortieAm]
        ];
    const minutesBetween = (start, end) => {
        if (!start || !end) return 0;
        const toMinutes = value => {
            const [h, m] = value.split(':').map(Number);
            return h * 60 + m;
        };
        const difference = toMinutes(end) - toMinutes(start);
        return difference < 0 ? difference + 24 * 60 : difference;
    };
    const totalMinutes = periods.reduce((total, [start, end]) => total + minutesBetween(start, end), 0);

    return {
        periods: periods.filter(([start, end]) => start && end),
        continuous: Boolean(hours.horaireContinu),
        totalMinutes,
        totalHoursLabel: `${Math.floor(totalMinutes / 60)} h${String(totalMinutes % 60).padStart(2, '0')}`
    };
}

export function getConfiguredPause(siteParams) {
    if (!siteParams?.heureFinMatin || !siteParams?.heureDebutAm) return null;
    return {
        heureSortieMatin: siteParams.heureFinMatin,
        heureEntreeAm: siteParams.heureDebutAm
    };
}
