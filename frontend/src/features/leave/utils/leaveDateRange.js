import { shiftIsoDate } from '../../../utils/dateUtils.js';

export function getRepriseInputValue({ estDemiJournee, dateDebut, dateFin }) {
  return shiftIsoDate(estDemiJournee ? dateDebut : dateFin, 1);
}

export function getDateFinFromRepriseInput(dateReprise) {
  return shiftIsoDate(dateReprise, -1);
}
