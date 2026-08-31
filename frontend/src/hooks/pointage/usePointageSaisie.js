import { useState, useEffect, useCallback } from 'react';
import pointageSaisieService from '@/api/pointage/pointageSaisieService';

export function usePointageSaisie(initialSiteId = 1, initialDate = new Date().toISOString().split('T')[0]) {
    const [selectedSite, setSelectedSite] = useState(initialSiteId);
    const [selectedDate, setSelectedDate] = useState(initialDate);
    const [grilleData, setGrilleData] = useState([]);
    const [initialGrilleData, setInitialGrilleData] = useState([]);
    const [siteParams, setSiteParams] = useState(null);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [periodeInfo, setPeriodeInfo] = useState(null);
    const [statsJour, setStatsJour] = useState(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // Comparateur précis pour détecter les vraies modifications par rapport à l'état initial serveur
    const isRowModified = useCallback((row, initialList) => {
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

        const curDyn = JSON.stringify(row.dynamicIndemnites || {});
        const initDyn = JSON.stringify(initial.dynamicIndemnites || {});
        if (curDyn !== initDyn) return true;

        return false;
    }, []);

    const resetAllChanges = useCallback(() => {
        if (initialGrilleData && initialGrilleData.length > 0) {
            setGrilleData(JSON.parse(JSON.stringify(initialGrilleData)));
        }
    }, [initialGrilleData]);

    // Fonction pure de calcul pour une ligne
    const calculateRowValues = useCallback((row, sParams) => {
        const parseMins = (str) => {
            if (!str || str.length < 5 || str === '—' || str === '--:--') return null;
            const parts = str.split(':');
            return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
        };

        const em = parseMins(row.heureEntreeMatin);
        const sm = parseMins(row.heureSortieMatin);
        const ea = parseMins(row.heureEntreeAm);
        const sa = parseMins(row.heureSortieAm);

        const isFerie = !!row.ferieJour;
        const isCongeFull = row.typeCongeJour === 'FULL_DAY' || row.codeStatut === 'CONGE';
        const isCongeMatin = row.typeCongeJour === 'MATIN';
        const isCongeAm = row.typeCongeJour === 'APRES_MIDI';

        // Cas Pause automatique (Entrée matin et sortie soir uniquement)
        const isPauseAuto = (em !== null && sa !== null && sm === null && ea === null);
        let smEffectif = sm;
        let eaEffectif = ea;

        if (isPauseAuto) {
            smEffectif = parseMins(sParams?.heureFinMatin || '12:00');
            eaEffectif = parseMins(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));
        }

        let hMatin = (em !== null && smEffectif !== null && smEffectif >= em) ? (smEffectif - em) : 0;
        let hAm = (eaEffectif !== null && sa !== null && sa >= eaEffectif) ? (sa - eaEffectif) : 0;

        // RÈGLE CRITIQUE : Si férié chômé ou congé complet sans heures saisies -> 0h de travail effectif
        let totalMins = (isFerie && em === null && ea === null) || isCongeFull ? 0 : (hMatin + hAm);
        let totalH = Math.round((totalMins / 60.0) * 100) / 100;

        // Validation chronologique
        let hasValidationError = false;
        if (em !== null && sm !== null && em >= sm) hasValidationError = true;
        if (ea !== null && sa !== null && ea >= sa) hasValidationError = true;
        if (sm !== null && ea !== null && sm > ea) hasValidationError = true;
        if (em !== null && ea !== null && em >= ea) hasValidationError = true;
        if (em !== null && sa !== null && em >= sa) hasValidationError = true;

        const startMatinNormal = parseMins(sParams?.heureDebutMatin || '08:00');
        const endMatinNormal = parseMins(sParams?.heureFinMatin || '12:00');
        const startAmNormal = parseMins(sParams?.heureDebutAm || (sParams?.horaireContinu ? '13:00' : '14:00'));

        let retMatin = (em !== null && startMatinNormal !== null && em > startMatinNormal) ? (em - startMatinNormal) : 0;
        let retAm = 0;
        if (!isPauseAuto && ea !== null && startAmNormal !== null) {
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

        // Détermination du statut selon la hiérarchie stricte
        let codeStatut;
        if (isFerie) {
            if (em !== null || sm !== null || ea !== null || sa !== null) {
                codeStatut = 'FERIE_TRAVAILLE';
            } else {
                codeStatut = 'FERIE_CHOME';
            }
        } else if (isCongeFull) {
            codeStatut = 'CONGE';
        } else if (hasValidationError) {
            codeStatut = 'ANOMALIE_SAISIE';
        } else if (em === null && sm === null && ea === null && sa === null) {
            if (row.estAutonome) codeStatut = 'DISPENSE_POINTAGE';
            else if (row.idPointage) codeStatut = 'ABSENCE_NON_JUSTIFIEE';
            else codeStatut = 'NON_SAISI';
        } else if (isPauseAuto) {
            if (dayOfWeek === 6) codeStatut = 'SAMEDI_TRAVAILLE';
            else if (dayOfWeek === 0) codeStatut = 'DIMANCHE_TRAVAILLE';
            else codeStatut = totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT';
        } else if ((em !== null && sm === null) || (ea !== null && sa === null) || (em === null && sm !== null) || (ea === null && sa !== null)) {
            codeStatut = 'ANOMALIE_SAISIE';
        } else if (em !== null && sm !== null && ea === null && sa === null) {
            codeStatut = isCongeAm ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : 'ANOMALIE_SAISIE';
        } else if (em === null && sm === null && ea !== null && sa !== null) {
            codeStatut = isCongeMatin ? (totalMinutesRetard > 0 ? 'RETARD' : 'PRESENT') : 'ANOMALIE_SAISIE';
        } else if (totalH === 0.0) {
            codeStatut = 'ABSENCE_NON_JUSTIFIEE';
        } else if (dayOfWeek === 6 && totalH > 0) {
            codeStatut = 'SAMEDI_TRAVAILLE';
        } else if (dayOfWeek === 0 && totalH > 0) {
            codeStatut = 'DIMANCHE_TRAVAILLE';
        } else if (totalMinutesRetard > 0) {
            codeStatut = 'RETARD';
        } else {
            codeStatut = 'PRESENT';
        }

        let libelleStatut = row.libelleStatut;
        if (codeStatut === 'FERIE_CHOME') libelleStatut = row.libelleFerie || 'Férié chômé';
        else if (codeStatut === 'FERIE_TRAVAILLE') libelleStatut = (row.libelleFerie ? `${row.libelleFerie} (Travaillé)` : 'Férié travaillé (+100%)');
        else if (codeStatut === 'SAMEDI_TRAVAILLE') libelleStatut = 'Samedi travaillé';
        else if (codeStatut === 'DIMANCHE_TRAVAILLE') libelleStatut = 'Dimanche travaillé (+40%)';
        else if (codeStatut === 'DISPENSE_POINTAGE') libelleStatut = 'Cadre autonome (8h)';
        else if (codeStatut === 'CONGE') libelleStatut = row.libelleConge || 'Congé';
        else if (codeStatut === 'PRESENT') libelleStatut = 'Présent';
        else if (codeStatut === 'RETARD') libelleStatut = `Retard (${totalMinutesRetard} min)`;
        else if (codeStatut === 'ANOMALIE_SAISIE') libelleStatut = 'Incomplet / Anomalie';
        else if (codeStatut === 'ABSENCE_NON_JUSTIFIEE') libelleStatut = 'Absent';

        const isPres = codeStatut === 'PRESENT';
        const isRet = codeStatut === 'RETARD' || totalMinutesRetard > 0;
        const isCong = codeStatut === 'CONGE' || isCongeFull || isCongeMatin || isCongeAm;
        const isFerieTrav = codeStatut === 'FERIE_TRAVAILLE';
        const isFerieCh = codeStatut === 'FERIE_CHOME' || (isFerie && !isFerieTrav);
        const isDispensePtg = !isFerieCh && !isCong && (row.estAutonome || codeStatut === 'DISPENSE_POINTAGE');

        return {
            ...row,
            heuresMatin: Math.round((hMatin / 60.0) * 100) / 100,
            heuresAm: Math.round((hAm / 60.0) * 100) / 100,
            totalHeures: totalH,
            minutesRetard: totalMinutesRetard,
            codeStatut,
            libelleStatut,
            hasValidationError,
            present: isPres,
            retard: isRet,
            conge: isCong,
            ferieTravaille: isFerieTrav,
            ferieChome: isFerieCh,
            dispensePointage: isDispensePtg,
            anomalie: codeStatut === 'ANOMALIE_SAISIE',
            absenceNonJustifiee: codeStatut === 'ABSENCE_NON_JUSTIFIEE'
        };
    }, [selectedDate]);

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await pointageSaisieService.getGrilleSaisie({
                idSite: selectedSite,
                date: selectedDate
            });
            if (data) {
                const sParams = data.siteParams || {
                    heureDebutMatin: '08:00',
                    heureFinMatin: '12:00',
                    heureDebutAm: '13:00',
                    heureFinAm: '17:00',
                    horaireContinu: false,
                    siteDeplacementActif: true,
                    siteRepasActif: false
                };
                setSiteParams(sParams);
                setSiteIndemnites(data.siteIndemnites || []);
                if (data.grilleData) {
                    const computedData = data.grilleData.map((row) => calculateRowValues(row, sParams));
                    setGrilleData(computedData);
                    setInitialGrilleData(JSON.parse(JSON.stringify(computedData)));
                }
                setPeriodeInfo(data.periodeInfo || null);
                if (data.statsJour) {
                    setStatsJour(data.statsJour);
                }
            }
        } catch (err) {
            console.error('Erreur chargement saisie pointages:', err);
            setError("Impossible de charger les données de pointage pour ce site.");
        } finally {
            setLoading(false);
        }
    }, [selectedSite, selectedDate, calculateRowValues]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const updateRowHours = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = { ...row, [field]: value };
                return calculateRowValues(updated, siteParams);
            })
        );
    };

    const updateRowIndemnite = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                return { ...row, [field]: value };
            })
        );
    };

    const updateRowDynamicIndemnite = (idEmploye, idIndemnite, value, fraction) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const existingDyn = row.dynamicIndemnites || {};
                const current = existingDyn[String(idIndemnite)] || {};
                let updatedEntry = { ...current };

                if (fraction === 'MATIN') {
                    updatedEntry.matin = value;
                } else if (fraction === 'APRES_MIDI') {
                    updatedEntry.am = value;
                } else {
                    updatedEntry.accordee = value;
                    updatedEntry.fractionJour = value ? 1.0 : 0.0;
                }

                const updatedDyn = {
                    ...existingDyn,
                    [String(idIndemnite)]: updatedEntry
                };
                return { ...row, dynamicIndemnites: updatedDyn };
            })
        );
    };

    const autoFillStandardHours = () => {
        if (!siteParams) return;
        const hDebM = siteParams.heureDebutMatin || '08:00';
        const hFinM = siteParams.heureFinMatin || '12:00';
        const hDebA = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
        const hFinA = siteParams.heureFinAm || '17:00';

        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.codeStatut === 'CONGE' || row.estAutonome || row.ferieJour) return row;
                const isBlank = !row.heureEntreeMatin && !row.heureSortieMatin && !row.heureEntreeAm && !row.heureSortieAm;
                if (!isBlank) return row;

                const filled = {
                    ...row,
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA
                };
                return calculateRowValues(filled, siteParams);
            })
        );
    };

    const autoDeduceAllPauses = () => {
        if (!siteParams) return;
        const hFinM = siteParams.heureFinMatin || '12:00';
        const hDebA = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');

        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.codeStatut === 'CONGE' || row.estAutonome || row.ferieJour) return row;
                if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm)) {
                    const filled = {
                        ...row,
                        heureSortieMatin: row.heureSortieMatin || hFinM,
                        heureEntreeAm: row.heureEntreeAm || hDebA
                    };
                    return calculateRowValues(filled, siteParams);
                }
                return row;
            })
        );
    };

    const saveAll = async () => {
        setSaving(true);
        try {
            const res = await pointageSaisieService.enregistrerBatch(grilleData);
            if (res && res.success) {
                await loadData();
                return true;
            }
            return false;
        } catch (err) {
            console.error('Erreur sauvegarde pointages:', err);
            return false;
        } finally {
            setSaving(false);
        }
    };

    return {
        selectedSite,
        setSelectedSite,
        selectedDate,
        setSelectedDate,
        grilleData,
        initialGrilleData,
        siteParams,
        siteIndemnites,
        periodeInfo,
        statsJour,
        loading,
        saving,
        error,
        loadData,
        isRowModified,
        resetAllChanges,
        updateRowHours,
        updateRowIndemnite,
        updateRowDynamicIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        saveAll
    };
}

export default usePointageSaisie;
