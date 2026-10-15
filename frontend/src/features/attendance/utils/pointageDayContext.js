/** Charge une journée et ses indemnités pour les parcours de correction et de régularisation. */
export async function loadPointageDayContext(getGrid, { idSite, date, idEmploye }) {
    const grid = await getGrid({ idSite, date });
    const row = grid?.grilleData?.find(item => Number(item.idEmploye) === Number(idEmploye));
    if (!row) throw new Error('La journée du salarié est introuvable dans la grille.');
    return { grid, row, allowances: grid.siteIndemnites || [] };
}

export function selectPointageDayAllowances(allowances, row, { overrides = {}, clearWithoutHours = false } = {}) {
    return Object.fromEntries(allowances.map(allowance => {
        const id = String(allowance.idIndemnite);
        const current = row.dynamicIndemnites?.[id] || {};
        const imported = overrides[id] || {};
        const matin = imported.matin ?? (!clearWithoutHours && Boolean(current.matin));
        const am = imported.am ?? (!clearWithoutHours && Boolean(current.am));
        const fractionnable = allowance.fractionnable ?? allowance.estFractionnable;
        const hasOverride = Object.keys(imported).length > 0;
        return [id, {
            accordee: imported.accordee ?? (hasOverride && fractionnable
                ? (matin || am) : (!clearWithoutHours && Boolean(current.accordee))),
            matin, am
        }];
    }));
}
