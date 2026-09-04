/**
 * Utilitaires & Calculs d'Agrégation de Synthèse de Pointage (Frontend).
 * 
 * Centralise le calcul des KPIs hebdomadaires, l'évaluation de l'état de consolidation
 * mensuelle et les prérequis de transmission DAF.
 */

/**
 * Calcule les indicateurs agrégés (KPIs) pour une liste de lignes de détail de semaine.
 * 
 * @param {Array<object>} rows Lignes de collaborateurs filtrées pour la semaine
 * @returns {{ totalH: number, totalHs: number, totalHs30: number, totalHs50: number, totalRetenues: number, nbSalaries: number }}
 */
export const calculerKpisSemaine = (rows = []) => {
  const totalH = rows.reduce((acc, r) => acc + (Number(r.totalHeuresTravaillees) || 0), 0);
  const totalHs30 = rows.reduce((acc, r) => acc + (Number(r.heuresHs30) || 0), 0);
  const totalHs50 = rows.reduce((acc, r) => acc + (Number(r.heuresHs50) || 0), 0);
  const totalRetenues = rows.reduce((acc, r) => acc + (Number(r.heuresAbsenceRetenues) || 0), 0);

  return {
    totalH: Math.round(totalH * 100) / 100,
    totalHs: Math.round((totalHs30 + totalHs50) * 100) / 100,
    totalHs30: Math.round(totalHs30 * 100) / 100,
    totalHs50: Math.round(totalHs50 * 100) / 100,
    totalRetenues: Math.round(totalRetenues * 100) / 100,
    nbSalaries: rows.length
  };
};

/**
 * Évalue la complétude et l'état de consolidation mensuelle de la période de paie.
 * 
 * @param {Array<object>} semaines Liste des semaines de la période
 * @param {Array<object>} periodes Liste des périodes de paie
 * @param {number|string} selectedPeriodeId ID de la période sélectionnée
 * @returns {object} État de consolidation complet
 */
export const evaluerConsolidationStatus = (semaines = [], periodes = [], selectedPeriodeId) => {
  const totalSemaines = semaines.length;
  const semainesValideesCount = semaines.filter(s => Boolean(s.estValidee)).length;
  const totalAnomalies = semaines.reduce((acc, s) => acc + (Number(s.nbAnomalies) || 0), 0);
  const allValidees = totalSemaines > 0 && semainesValideesCount === totalSemaines;
  const currentPeriodeObj = periodes.find(p => String(p.idPeriodePaie) === String(selectedPeriodeId));

  const st = currentPeriodeObj?.statutCode;
  return {
    totalSemaines,
    semainesValideesCount,
    totalAnomalies,
    allValidees,
    currentPeriodeObj,
    isEnCours: st === 'EN_COURS' || !st,
    isAValider: st === 'A_VALIDER' || st === 'TRANSMIS_DAF',
    isValidee: st === 'VALIDEE',
    isCloturee: st === 'CLOTUREE'
  };
};
