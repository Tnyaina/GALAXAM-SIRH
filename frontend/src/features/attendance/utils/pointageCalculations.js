/** Comparaison d'édition locale uniquement; aucun calcul métier de pointage ici. */
export const isRowDataModified = (row, initialList) => {
  if (!row || !initialList) return false;
  const initial = initialList.find((item) => item.idEmploye === row.idEmploye);
  if (!initial) return false;

  const normalizeTime = (time) => (time == null || time === '—' || time === '--:--' || time === '')
    ? ''
    : String(time).trim();

  const timeFields = ['heureEntreeMatin', 'heureSortieMatin', 'heureEntreeAm', 'heureSortieAm'];
  if (timeFields.some((field) => normalizeTime(row[field]) !== normalizeTime(initial[field]))) return true;

  const intentFields = ['horaireContinu', 'codeStatut', 'motifAjustement'];
  if (intentFields.some((field) => (row[field] ?? '') !== (initial[field] ?? ''))) return true;

  return JSON.stringify(row.dynamicIndemnites || {}) !== JSON.stringify(initial.dynamicIndemnites || {});
};
