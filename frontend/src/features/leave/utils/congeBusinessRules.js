/**
 * Utilitaires & Règles Métier des Congés et Absences (SIRH GALAXAM).
 * 
 * Centralise les règles légales malagasy (Art. 13, 85, 88, 97 de la Loi 2003-044),
 * les délais de préavis, calculs de durées fixes et évaluations d'éligibilité.
 */

// ─── Utilitaires Date Canoniques (Délégués à dateUtils) ─────────────────────
import { formatDateForInput, isDateAnterieure } from '@/utils/dateUtils';
export { formatDateForInput, isDateAnterieure };


/**
 * Évalue le respect du délai de préavis légal et la rétroactivité d'une demande.
 * 
 * Règles (RG-CON-06) :
 * - Si date passée : Rétroactif (dérogation requise).
 * - Types non soumis à préavis (ex: Maladie, Maternité, Paternité, Événement familial) : Pas de préavis requis.
 * - Demande courte (<= 3 jours décomptés) : 2 jours de préavis min.
 * - Demande longue (> 3 jours décomptés) : 15 jours de préavis min.
 * 
 * @param {string} dateDebut Date de début (YYYY-MM-DD)
 * @param {number} nbJoursDecomptes Nombre de jours décomptés
 * @param {object|null} selectedType Type de congé sélectionné
 * @returns {{ isDepasse: boolean, preavisRequis: number, joursConstates: number, isRetroactif: boolean }}
 */
export const evaluerPreavis = (dateDebut, nbJoursDecomptes, selectedType) => {
  if (!dateDebut) {
    return { isDepasse: false, preavisRequis: 0, joursConstates: 0, isRetroactif: false };
  }

  const [y, m, d] = String(dateDebut).substring(0, 10).split('-').map(Number);
  const dDebut = (y && m && d) ? new Date(y, m - 1, d, 0, 0, 0, 0) : new Date(dateDebut);
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  const joursConstates = Math.ceil((dDebut.getTime() - today.getTime()) / (1000 * 3600 * 24));

  // Cas 1 : Rétroactivité (date de début déjà passée)
  if (joursConstates < 0) {
    return { isDepasse: true, preavisRequis: 0, joursConstates, isRetroactif: true };
  }

  // Cas 2 : Type soumis à préavis réglementaire
  if (selectedType?.soumisDelaiPreavis) {
    const seuilLong = 3;
    const preavisRequis = (Number(nbJoursDecomptes) > seuilLong) ? 15 : 2;
    if (joursConstates < preavisRequis) {
      return { isDepasse: true, preavisRequis, joursConstates, isRetroactif: false };
    }
  }

  return { isDepasse: false, preavisRequis: 0, joursConstates, isRetroactif: false };
};

/**
 * Calcule la date de fin automatique pour les congés à durée légale fixe
 * (ex: Maternité standard 98j / pathologique 119j, Paternité 3j, Mariage 3j, Décès).
 * 
 * @param {string} dateDebut Date de début (YYYY-MM-DD)
 * @param {object|null} selectedType Type de congé
 * @param {object|null} motifFamilial Motif sélectionné pour congé exceptionnel
 * @param {boolean} extensionPathologiqueMaternite
 * @returns {string|null} Date de fin calculée au format YYYY-MM-DD ou null si durée libre
 */
export const calculerDateFinAutomatique = (dateDebut, selectedType, motifFamilial, extensionPathologiqueMaternite) => {
  if (!dateDebut || !selectedType) return null;

  let nbJoursFixes = null;
  const isMaternite = selectedType.code === 'MATERNITE';
  const isExceptionnel = selectedType.code === 'EXCEPTIONNEL';

  if (selectedType.dureeFixeJours) {
    nbJoursFixes = Number(selectedType.dureeFixeJours);
    if (isMaternite && extensionPathologiqueMaternite) {
      nbJoursFixes = 119; // 14 semaines + 3 semaines patho (Art. 97)
    }
  } else if (isExceptionnel && motifFamilial?.nbJoursAutorises) {
    nbJoursFixes = Number(motifFamilial.nbJoursAutorises);
  }

  if (nbJoursFixes && nbJoursFixes > 0) {
    const [y, m, d] = dateDebut.split('-').map(Number);
    const dDebut = new Date(y, m - 1, d);
    const dFin = new Date(dDebut);
    dFin.setDate(dFin.getDate() + nbJoursFixes - 1);
    return formatDateForInput(dFin);
  }

  return null;
};

/**
 * Évalue la suffisance du solde pour une demande de congé à décompte.
 * 
 * @param {number|string|null} soldeDisponible Solde d'heures/jours disponible issu de l'API
 * @param {number} nbJoursDecomptes Jours décomptés demandés
 * @param {boolean} isDecompteSoldeAcquis Si le type consomme le solde
 * @returns {{ isSoldeInsuffisant: boolean, deficit: number, soldeNum: number }}
 */
export const evaluerSuffisanceSolde = (soldeDisponible, nbJoursDecomptes, isDecompteSoldeAcquis) => {
  if (!isDecompteSoldeAcquis || soldeDisponible === null || soldeDisponible === undefined) {
    return { isSoldeInsuffisant: false, deficit: 0, soldeNum: 0 };
  }
  const soldeNum = Number(soldeDisponible);
  const joursDemandés = Number(nbJoursDecomptes);
  const isSoldeInsuffisant = joursDemandés > soldeNum;
  const deficit = isSoldeInsuffisant ? Math.round((joursDemandés - soldeNum) * 100) / 100 : 0;
  return { isSoldeInsuffisant, deficit, soldeNum };
};
