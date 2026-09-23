/**
 * Moteur de Calcul & Règles Métier du Pointage Quotidien (Frontend).
 * 
 * Centralise les algorithmes de parsing d'heures, de calcul de pauses automatiques,
 * de retards, de conformité chronologique et de qualification d'état de pointage.
 */

/**
 * Parse une heure "HH:mm" en nombre total de minutes depuis minuit.
 * Retourne null si la chaîne est invalide ou vide.
 * 
 * @param {string|null} str Ex: "08:30"
 * @returns {number|null} Minutes (ex: 510) ou null
 */
export const parseMinutes = (str) => {
  if (!str || typeof str !== 'string') return null;
  const cleaned = str.trim();
  if (cleaned === '' || cleaned === '—' || cleaned === '--:--' || !cleaned.includes(':')) return null;
  const parts = cleaned.split(':');
  if (parts.length < 2) return null;
  const h = parseInt(parts[0], 10);
  const m = parseInt(parts[1], 10);
  if (isNaN(h) || isNaN(m) || h < 0 || h > 23 || m < 0 || m > 59) return null;
  return h * 60 + m;
};

/**
 * Détecte si une ligne de saisie de pointage a été modifiée par rapport à l'état serveur initial.
 * 
 * @param {object} row Ligne actuelle
 * @param {Array<object>} initialList Liste de référence initiale
 * @returns {boolean} true si la ligne a changé
 */
export const isRowDataModified = (row, initialList) => {
  if (!row || !initialList) return false;
  const initial = initialList.find(r => r.idEmploye === row.idEmploye);
  if (!initial) return false;

  const normalizeTime = (t) => (t === null || t === undefined || t === '—' || t === '--:--' || t === '') ? '' : String(t).trim();

  if (normalizeTime(row.heureEntreeMatin) !== normalizeTime(initial.heureEntreeMatin)) return true;
  if (normalizeTime(row.heureSortieMatin) !== normalizeTime(initial.heureSortieMatin)) return true;
  if (normalizeTime(row.heureEntreeAm) !== normalizeTime(initial.heureEntreeAm)) return true;
  if (normalizeTime(row.heureSortieAm) !== normalizeTime(initial.heureSortieAm)) return true;

  if (Boolean(row.deplacementMatin) !== Boolean(initial.deplacementMatin)) return true;
  if (Boolean(row.deplacementAm) !== Boolean(initial.deplacementAm)) return true;
  if (Boolean(row.indemniteRepasRecue) !== Boolean(initial.indemniteRepasRecue)) return true;
  if (Boolean(row.horaireContinu) !== Boolean(initial.horaireContinu)) return true;

  if ((row.codeStatut || '') !== (initial.codeStatut || '')) return true;
  if ((row.motifAjustement || '') !== (initial.motifAjustement || '')) return true;

  const curDyn = JSON.stringify(row.dynamicIndemnites || {});
  const initDyn = JSON.stringify(initial.dynamicIndemnites || {});
  if (curDyn !== initDyn) return true;

  return false;
};

/**
 * Calcule en temps réel les totaux d'heures, retards, anomalies et qualifications
 * d'une ligne de pointage pour un collaborateur et un site donné.
 * 
 * @param {object} row Données de la ligne
 * @param {object|null} sParams Paramètres horaires du site
 * @param {string} selectedDate Date de pointage (YYYY-MM-DD)
 * @returns {object} Ligne enrichie avec totaux, statut calculé et indicateurs booléens
 */
export const calculerLignePointageTempsReel = (row, sParams, selectedDate) => {
  const em = parseMinutes(row.heureEntreeMatin);
  const sm = parseMinutes(row.heureSortieMatin);
  const ea = parseMinutes(row.heureEntreeAm);
  const sa = parseMinutes(row.heureSortieAm);

  const isFerie = !!row.ferieJour;
  const isCongeFull = row.typeCongeJour === 'FULL_DAY' || row.codeStatut === 'CONGE';
  const isCongeMatin = row.typeCongeJour === 'MATIN';
  const isCongeAm = row.typeCongeJour === 'APRES_MIDI';

  const isJourneeContinue = Boolean(row.horaireContinu || row.horaireContinuJour);

  // Cas Pause automatique (Entrée matin et sortie soir uniquement) — uniquement si PAS journée continue
  const isPauseAuto = (!isJourneeContinue && em !== null && sa !== null && sm === null && ea === null);
  let smEffectif = sm;
  let eaEffectif = ea;

  let totalMins;
  let hMatin;
  let hAm;

  if (isJourneeContinue && em !== null && sa !== null) {
    totalMins = (sa >= em) ? (sa - em) : 0;
    hMatin = totalMins;
    hAm = 0;
  } else {
    if (isPauseAuto) {
      smEffectif = parseMinutes(sParams?.heureFinMatin || '12:00');
      eaEffectif = parseMinutes(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));
    }

    hMatin = (em !== null && smEffectif !== null && smEffectif >= em) ? (smEffectif - em) : 0;
    hAm = (eaEffectif !== null && sa !== null && sa >= eaEffectif) ? (sa - eaEffectif) : 0;

    // RÈGLE CRITIQUE : Si férié chômé ou congé complet sans heures saisies -> 0h de travail effectif
    totalMins = (isFerie && em === null && ea === null) || isCongeFull ? 0 : (hMatin + hAm);
  }
  let totalH = Math.round((totalMins / 60.0) * 100) / 100;

  // Validation chronologique des badgeages
  let hasValidationError = false;
  if (em !== null && sm !== null && em >= sm) hasValidationError = true;
  if (ea !== null && sa !== null && ea >= sa) hasValidationError = true;
  if (sm !== null && ea !== null && sm > ea) hasValidationError = true;
  if (em !== null && ea !== null && em >= ea) hasValidationError = true;
  if (em !== null && sa !== null && em >= sa) hasValidationError = true;

  const startMatinNormal = parseMinutes(sParams?.heureDebutMatin || '08:00');
  const endMatinNormal = parseMinutes(sParams?.heureFinMatin || '12:00');
  const startAmNormal = parseMinutes(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));

  let retMatin = (em !== null && startMatinNormal !== null && em > startMatinNormal) ? (em - startMatinNormal) : 0;
  let retAm = 0;
  if (!isJourneeContinue && !isPauseAuto && ea !== null && startAmNormal !== null) {
    if (sm !== null && endMatinNormal !== null && startAmNormal > endMatinNormal) {
      const pauseTheorique = startAmNormal - endMatinNormal;
      if (sm > endMatinNormal) {
        const pauseReelle = ea - sm;
        if (pauseReelle > pauseTheorique) {
          retAm = pauseReelle - pauseTheorique;
        } else {
          retAm = 0;
        }
      } else {
        retAm = Math.max(0, ea - startAmNormal);
      }
    } else {
      retAm = Math.max(0, ea - startAmNormal);
    }
  }

  const isExemptRetard = isFerie || isCongeFull || !!row.estAutonome;
  let totalMinutesRetard = isExemptRetard ? 0 : (retMatin + retAm);

  const dStr = row.datePointage || selectedDate;
  let dayOfWeek = null;
  if (dStr) {
    const parts = dStr.split('-');
    if (parts.length === 3) {
      const dObj = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
      dayOfWeek = dObj.getDay();
    }
  }

  // Détermination du code de statut selon la hiérarchie stricte
  let codeStatut;
  if (isFerie) {
    if (em !== null || sm !== null || ea !== null || sa !== null) {
      codeStatut = 'FERIE_TRAVAILLE';
    } else {
      codeStatut = 'FERIE_CHOME';
    }
  } else if (isCongeFull) {
    if (row.congePaye === false || row.codeStatut === 'ABSENCE_JUSTIFIEE') {
      codeStatut = 'ABSENCE_JUSTIFIEE';
    } else if (row.congeDecompteSolde === false || row.codeStatut === 'DISPENSE_POINTAGE') {
      codeStatut = 'DISPENSE_POINTAGE';
    } else {
      codeStatut = 'CONGE';
    }
  } else if (hasValidationError) {
    codeStatut = 'ANOMALIE_SAISIE';
  } else if (em === null && sm === null && ea === null && sa === null) {
    if (row.estAutonome || row.codeStatut === 'DISPENSE_POINTAGE') {
      codeStatut = (dayOfWeek === 0 || dayOfWeek === 6) ? 'NON_SAISI' : 'DISPENSE_POINTAGE';
    } else if (row.codeStatut === 'ABSENCE_JUSTIFIEE') {
      codeStatut = 'ABSENCE_JUSTIFIEE';
    } else if (row.codeStatut === 'ABSENCE_NON_JUSTIFIEE') {
      codeStatut = 'ABSENCE_NON_JUSTIFIEE';
    } else if (row.codeStatut === 'CONGE') {
      codeStatut = 'CONGE';
    } else if (dayOfWeek === 0 || dayOfWeek === 6) {
      codeStatut = 'NON_SAISI';
    } else if (row.idPointage) {
      codeStatut = 'ABSENCE_NON_JUSTIFIEE';
    } else {
      codeStatut = 'NON_SAISI';
    }
  } else if (isJourneeContinue || isPauseAuto) {
    if (dayOfWeek === 6) codeStatut = 'SAMEDI_TRAVAILLE';
    else if (dayOfWeek === 0) codeStatut = 'DIMANCHE_TRAVAILLE';
    else codeStatut = totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT';
  } else if ((em !== null && sm === null) || (ea !== null && sa === null) || (em === null && sm !== null) || (ea === null && sa !== null)) {
    codeStatut = 'ANOMALIE_SAISIE';
  } else if (em !== null && sm !== null && ea === null && sa === null) {
    codeStatut = isCongeAm ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : (row.motifAjustement || row.codeStatut === 'DEMI_JOURNEE' ? 'DEMI_JOURNEE' : 'ANOMALIE_SAISIE');
  } else if (em === null && sm === null && ea !== null && sa !== null) {
    codeStatut = isCongeMatin ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : (row.motifAjustement || row.codeStatut === 'DEMI_JOURNEE' ? 'DEMI_JOURNEE' : 'ANOMALIE_SAISIE');
  } else if (totalH === 0.0) {
    if (row.codeStatut === 'ABSENCE_JUSTIFIEE') {
      codeStatut = 'ABSENCE_JUSTIFIEE';
    } else if (row.codeStatut === 'ABSENCE_NON_JUSTIFIEE') {
      codeStatut = 'ABSENCE_NON_JUSTIFIEE';
    } else if (row.codeStatut === 'CONGE') {
      codeStatut = 'CONGE';
    } else {
      codeStatut = (dayOfWeek === 0 || dayOfWeek === 6) ? 'NON_SAISI' : 'ABSENCE_NON_JUSTIFIEE';
    }
  } else if (dayOfWeek === 6 && totalH > 0) {
    codeStatut = 'SAMEDI_TRAVAILLE';
  } else if (dayOfWeek === 0 && totalH > 0) {
    codeStatut = 'DIMANCHE_TRAVAILLE';
  } else if (totalMinutesRetard > 0) {
    codeStatut = 'RETARD';
  } else {
    codeStatut = row.codeStatut === 'DEMI_JOURNEE' ? 'DEMI_JOURNEE' : 'PRESENT';
  }

  let libelleStatut = row.libelleStatut;
  if (codeStatut === 'FERIE_CHOME') libelleStatut = row.libelleFerie || 'Férié chômé';
  else if (codeStatut === 'FERIE_TRAVAILLE') libelleStatut = (row.libelleFerie ? `${row.libelleFerie} (Travaillé)` : 'Férié travaillé (+100%)');
  else if (codeStatut === 'SAMEDI_TRAVAILLE') libelleStatut = 'Samedi travaillé';
  else if (codeStatut === 'DIMANCHE_TRAVAILLE') libelleStatut = 'Dimanche travaillé (+40%)';
  else if (codeStatut === 'DISPENSE_POINTAGE') libelleStatut = 'Cadre autonome (8h)';
  else if (codeStatut === 'CONGE') libelleStatut = row.libelleConge || 'Congé';
  else if (codeStatut === 'ABSENCE_JUSTIFIEE') libelleStatut = row.motifAjustement ? `Absence justifiée (${row.motifAjustement})` : 'Absence justifiée';
  else if (codeStatut === 'DEMI_JOURNEE') libelleStatut = row.motifAjustement ? `Demi-journée (${row.motifAjustement})` : 'Demi-journée travaillée';
  else if (codeStatut === 'PRESENT') libelleStatut = 'Présent';
  else if (codeStatut === 'RETARD') libelleStatut = `Retard (${totalMinutesRetard} min)`;
  else if (codeStatut === 'ANOMALIE_SAISIE') libelleStatut = 'Incomplet / Anomalie';
  else if (codeStatut === 'ABSENCE_NON_JUSTIFIEE') libelleStatut = 'Absent';

  const isPres = codeStatut === 'PRESENT';
  const isRet = codeStatut === 'RETARD' || totalMinutesRetard > 0;
  const isCong = codeStatut === 'CONGE' || isCongeFull || isCongeMatin || isCongeAm;
  const isAbsJust = codeStatut === 'ABSENCE_JUSTIFIEE';
  const isAbsNonJust = codeStatut === 'ABSENCE_NON_JUSTIFIEE';
  const isFerieTrav = codeStatut === 'FERIE_TRAVAILLE';
  const isFerieCh = codeStatut === 'FERIE_CHOME' || (isFerie && !isFerieTrav);
  const isDispensePtg = !isFerieCh && !isCong && (row.estAutonome || codeStatut === 'DISPENSE_POINTAGE');

  return {
    ...row,
    heuresMatin: Math.round((hMatin / 60.0) * 100) / 100,
    heuresAm: Math.round((hAm / 60.0) * 100) / 100,
    totalHeures: totalH,
    totalHeuresTravaillees: totalH,
    minutesRetard: totalMinutesRetard,
    totalMinutesRetard,
    codeStatut,
    libelleStatut,
    statutBadgeVariant: isFerieTrav ? 'error' : isFerieCh ? 'neutral' : (isRet || isAbsNonJust) ? 'danger' : (isCong || isAbsJust) ? 'warning' : isPres ? 'success' : isDispensePtg ? 'brand' : 'neutral',
    statutBadgeLabel: libelleStatut || 'Non saisi',
    hasValidationError,
    isPauseAuto,
    present: isPres,
    retard: isRet,
    conge: isCong,
    absenceJustifiee: isAbsJust,
    absenceNonJustifiee: isAbsNonJust,
    ferieTravaille: isFerieTrav,
    ferieChome: isFerieCh,
    dispensePointage: isDispensePtg,
    anomalie: codeStatut === 'ANOMALIE_SAISIE',
    isFullDayLeave: isCongeFull,
    isHalfDayMorningLeave: isCongeMatin,
    isHalfDayAfternoonLeave: isCongeAm,
    isFerieChome: isFerieCh,
    isFerieTravaille: isFerieTrav,
    isPresent: isPres,
    isRetard: isRet,
    isAbsenceJustifiee: isAbsJust,
    isDispensePointage: isDispensePtg
  };
};
