/** Même contrat de sauvegarde pour la grille et l'import Excel. */
export const toPointageBatchPayload = (row) => ({
    idPointage: row.idPointage, version: row.version,
    idEmploye: row.idEmploye, idSite: row.idSite, datePointage: row.datePointage,
    heureEntreeMatin: row.heureEntreeMatin || null, heureSortieMatin: row.heureSortieMatin || null,
    heureEntreeAm: row.heureEntreeAm || null, heureSortieAm: row.heureSortieAm || null,
    horaireContinu: Boolean(row.horaireContinu), horaireContinuJour: Boolean(row.horaireContinu),
    codeStatut: row.codeStatut || 'PRESENT', motifAjustement: row.motifAjustement || null,
    estAutonome: Boolean(row.estAutonome), estCadre: Boolean(row.estCadre),
    typeCongeJour: row.typeCongeJour || null,
    ferieJour: Boolean(row.ferieJour ?? row.ferie),
    congePaye: row.congePaye ?? true, congeDecompteSolde: row.congeDecompteSolde ?? true,
    idPeriodePaie: row.idPeriodePaie || null,
    dynamicIndemnites: row.dynamicIndemnites || {}
});
