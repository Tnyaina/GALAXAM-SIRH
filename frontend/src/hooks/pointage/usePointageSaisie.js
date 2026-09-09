import { useState, useEffect, useCallback } from 'react';
import pointageSaisieService from '@/api/pointage/pointageSaisieService';
import { isRowDataModified, calculerLignePointageTempsReel } from '@/utils/pointageCalculations';
import { getTodayDateString } from '@/utils/dateUtils';

export function usePointageSaisie(initialSiteId = 1, initialDate = getTodayDateString()) {
    const [selectedSite, setSelectedSite] = useState(initialSiteId);
    const [selectedDate, setSelectedDate] = useState(initialDate);

    useEffect(() => {
        if (initialDate && initialDate !== selectedDate) {
            setSelectedDate(initialDate);
        }
    }, [initialDate]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        if (initialSiteId && initialSiteId !== selectedSite) {
            setSelectedSite(initialSiteId);
        }
    }, [initialSiteId]); // eslint-disable-line react-hooks/exhaustive-deps
    const [grilleData, setGrilleData] = useState([]);
    const [initialGrilleData, setInitialGrilleData] = useState([]);
    const [siteParams, setSiteParams] = useState(null);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [periodeInfo, setPeriodeInfo] = useState(null);
    const [statsJour, setStatsJour] = useState(null);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // Comparateur précis délégué à l'utilitaire pur
    const isRowModified = isRowDataModified;

    const resetAllChanges = useCallback(() => {
        if (initialGrilleData && initialGrilleData.length > 0) {
            setGrilleData(JSON.parse(JSON.stringify(initialGrilleData)));
        }
    }, [initialGrilleData]);

    // Calcul temps réel délégué à l'utilitaire pur
    const calculateRowValues = useCallback((row, sParams) => {
        return calculerLignePointageTempsReel(row, sParams, selectedDate);
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

    const toggleRowHoraireContinu = (idEmploye) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const nextVal = !row.horaireContinu;
                const updated = {
                    ...row,
                    horaireContinu: nextVal,
                    horaireContinuJour: nextVal
                };
                if (nextVal) {
                    updated.heureSortieMatin = '';
                    updated.heureEntreeAm = '';
                }
                return calculateRowValues(updated, siteParams);
            })
        );
    };

    const updateRowStatut = (idEmploye, codeStatut, motifAjustement = '') => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                let updated = {
                    ...row,
                    codeStatut,
                    motifAjustement: motifAjustement || ''
                };

                if (codeStatut === 'ABSENCE_JUSTIFIEE' || codeStatut === 'ABSENCE_NON_JUSTIFIEE' || codeStatut === 'CONGE') {
                    updated.heureEntreeMatin = '';
                    updated.heureSortieMatin = '';
                    updated.heureEntreeAm = '';
                    updated.heureSortieAm = '';
                    updated.deplacementMatin = false;
                    updated.deplacementAm = false;
                    updated.indemniteDeplacementRecue = false;
                    updated.indemniteRepasRecue = false;
                    updated.typeCongeJour = codeStatut === 'CONGE' ? 'FULL_DAY' : null;
                    const dynReset = {};
                    Object.keys(updated.dynamicIndemnites || {}).forEach(k => {
                        dynReset[k] = { ...updated.dynamicIndemnites[k], accordee: false, fractionJour: 0.0, matin: false, am: false, montantApplique: 0.0 };
                    });
                    updated.dynamicIndemnites = dynReset;
                } else if (codeStatut === 'PRESENT') {
                    const isBlank = !updated.heureEntreeMatin && !updated.heureSortieMatin && !updated.heureEntreeAm && !updated.heureSortieAm;
                    if (isBlank && siteParams) {
                        updated.heureEntreeMatin = siteParams.heureDebutMatin || '08:00';
                        updated.heureSortieMatin = siteParams.heureFinMatin || '12:00';
                        updated.heureEntreeAm = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
                        updated.heureSortieAm = siteParams.heureFinAm || '17:00';
                        updated.deplacementMatin = true;
                        updated.deplacementAm = true;
                        updated.indemniteDeplacementRecue = true;
                    }
                    updated.typeCongeJour = null;
                }

                return calculateRowValues(updated, siteParams);
            })
        );
    };

    const updateRowIndemnite = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const boolVal = Boolean(value);
                const updated = { ...row, [field]: boolVal };
                const existingDyn = { ...(row.dynamicIndemnites || {}) };
                const siteInds = siteIndemnites || [];

                if (field === 'deplacementMatin' || field === 'deplacementAm') {
                    const depInd = siteInds.find(i => i.code === 'DEPLACEMENT' || i.code === 'TRANSPORT');
                    if (depInd) {
                        const idKey = String(depInd.idIndemnite);
                        const curEntry = { ...(existingDyn[idKey] || {}) };
                        const newMatin = field === 'deplacementMatin' ? boolVal : Boolean(updated.deplacementMatin);
                        const newAm = field === 'deplacementAm' ? boolVal : Boolean(updated.deplacementAm);
                        curEntry.matin = newMatin;
                        curEntry.am = newAm;
                        curEntry.accordee = newMatin || newAm;
                        curEntry.fractionJour = (newMatin ? 0.5 : 0.0) + (newAm ? 0.5 : 0.0);
                        const mntJour = depInd.montantJour || depInd.montantReference || 0;
                        curEntry.montantApplique = Math.round(curEntry.fractionJour * mntJour * 100.0) / 100.0;
                        existingDyn[idKey] = curEntry;
                        updated.indemniteDeplacementRecue = newMatin || newAm;
                    }
                } else if (field === 'indemniteRepasRecue') {
                    const repasInd = siteInds.find(i => i.code === 'REPAS');
                    if (repasInd) {
                        const idKey = String(repasInd.idIndemnite);
                        const curEntry = { ...(existingDyn[idKey] || {}) };
                        curEntry.accordee = boolVal;
                        curEntry.fractionJour = boolVal ? 1.0 : 0.0;
                        const mntRepas = repasInd.useGlobalIfZero && boolVal 
                            ? (repasInd.montantGlobal || 2000) 
                            : (repasInd.montantJour || repasInd.montantReference || 0);
                        curEntry.montantApplique = boolVal ? mntRepas : 0.0;
                        existingDyn[idKey] = curEntry;
                        updated.montantRepas = boolVal ? mntRepas : 0.0;
                    }
                }

                updated.dynamicIndemnites = existingDyn;
                return updated;
            })
        );
    };

    const bulkApplyStandardHours = (empIds) => {
        if (!siteParams || !empIds || empIds.size === 0) return;
        const hDebM = siteParams.heureDebutMatin || '08:00';
        const hFinM = siteParams.heureFinMatin || '12:00';
        const hDebA = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
        const hFinA = siteParams.heureFinAm || '17:00';

        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;
                if (row.codeStatut === 'CONGE' || row.estAutonome || row.ferieJour) return row;

                const filled = {
                    ...row,
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA,
                    codeStatut: 'PRESENT',
                    deplacementMatin: true,
                    deplacementAm: true,
                    indemniteDeplacementRecue: true
                };
                return calculateRowValues(filled, siteParams);
            })
        );
    };

    const bulkApplyStatut = (empIds, codeStatut, motifAjustement = '') => {
        if (!empIds || empIds.size === 0) return;
        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;
                // Protection des congés approuvés et des fériés chômés
                if (row.conge || row.codeStatut === 'CONGE' || row.ferieJour || row.ferieChome) return row;

                let updated = {
                    ...row,
                    codeStatut,
                    motifAjustement: motifAjustement || ''
                };

                if (codeStatut === 'ABSENCE_JUSTIFIEE' || codeStatut === 'ABSENCE_NON_JUSTIFIEE' || codeStatut === 'CONGE') {
                    updated.heureEntreeMatin = '';
                    updated.heureSortieMatin = '';
                    updated.heureEntreeAm = '';
                    updated.heureSortieAm = '';
                    updated.deplacementMatin = false;
                    updated.deplacementAm = false;
                    updated.indemniteDeplacementRecue = false;
                    updated.indemniteRepasRecue = false;
                    updated.typeCongeJour = codeStatut === 'CONGE' ? 'FULL_DAY' : null;
                    const dynReset = {};
                    Object.keys(updated.dynamicIndemnites || {}).forEach(k => {
                        dynReset[k] = { ...updated.dynamicIndemnites[k], accordee: false, fractionJour: 0.0, matin: false, am: false, montantApplique: 0.0 };
                    });
                    updated.dynamicIndemnites = dynReset;
                } else if (codeStatut === 'PRESENT') {
                    const isBlank = !updated.heureEntreeMatin && !updated.heureSortieMatin && !updated.heureEntreeAm && !updated.heureSortieAm;
                    if (isBlank && siteParams) {
                        updated.heureEntreeMatin = siteParams.heureDebutMatin || '08:00';
                        updated.heureSortieMatin = siteParams.heureFinMatin || '12:00';
                        updated.heureEntreeAm = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
                        updated.heureSortieAm = siteParams.heureFinAm || '17:00';
                        updated.deplacementMatin = true;
                        updated.deplacementAm = true;
                        updated.indemniteDeplacementRecue = true;
                    }
                    updated.typeCongeJour = null;
                }

                return calculateRowValues(updated, siteParams);
            })
        );
    };

    const bulkApplyIndemnite = (empIds, field, value) => {
        if (!empIds || empIds.size === 0) return;
        empIds.forEach(idEmp => {
            updateRowIndemnite(idEmp, field, value);
        });
    };

    const updateRowDynamicIndemnite = (idEmploye, idIndemnite, value, fraction) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const existingDyn = { ...(row.dynamicIndemnites || {}) };
                const current = { ...(existingDyn[String(idIndemnite)] || {}) };
                const updated = { ...row };
                const siteInds = siteIndemnites || [];
                const indConfig = siteInds.find(i => i.idIndemnite === idIndemnite);
                const isRep = indConfig?.code === 'REPAS';
                const boolVal = Boolean(value);

                if (fraction === 'MATIN') {
                    current.matin = boolVal;
                    updated.deplacementMatin = boolVal;
                    const curAm = updated.deplacementAm !== undefined ? Boolean(updated.deplacementAm) : Boolean(current.am);
                    current.accordee = boolVal || curAm;
                    current.fractionJour = (boolVal ? 0.5 : 0.0) + (curAm ? 0.5 : 0.0);
                    updated.indemniteDeplacementRecue = current.accordee;
                } else if (fraction === 'APRES_MIDI') {
                    current.am = boolVal;
                    updated.deplacementAm = boolVal;
                    const curMat = updated.deplacementMatin !== undefined ? Boolean(updated.deplacementMatin) : Boolean(current.matin);
                    current.accordee = curMat || boolVal;
                    current.fractionJour = (curMat ? 0.5 : 0.0) + (boolVal ? 0.5 : 0.0);
                    updated.indemniteDeplacementRecue = current.accordee;
                } else {
                    current.accordee = boolVal;
                    current.fractionJour = boolVal ? 1.0 : 0.0;
                    if (isRep) {
                        updated.indemniteRepasRecue = boolVal;
                        const mnt = indConfig?.useGlobalIfZero && boolVal 
                            ? (indConfig?.montantGlobal || 2000) 
                            : (indConfig?.montantJour || indConfig?.montantReference || 0);
                        current.montantApplique = boolVal ? mnt : 0.0;
                        updated.montantRepas = boolVal ? mnt : 0.0;
                    }
                }

                existingDyn[String(idIndemnite)] = current;
                updated.dynamicIndemnites = existingDyn;
                return updated;
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

    const saveAll = async (targetEmpIds = null) => {
        setSaving(true);
        try {
            let payload = grilleData;
            // Si une liste d'employés modifiés est fournie, ou si on filtre intelligemment :
            if (targetEmpIds && targetEmpIds.size > 0) {
                payload = grilleData.filter(r => targetEmpIds.has(r.idEmploye));
            } else if (initialGrilleData && initialGrilleData.length > 0) {
                // Détecter un weekend : en weekend, on n'envoie que les employés ayant des heures ou explicitement modifiés
                const dStr = selectedDate;
                let isWeekend = false;
                if (dStr) {
                    const parts = dStr.split('-');
                    if (parts.length === 3) {
                        const dObj = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
                        isWeekend = (dObj.getDay() === 0 || dObj.getDay() === 6);
                    }
                }
                if (isWeekend) {
                    payload = grilleData.filter(r => {
                        const hasHours = (r.heureEntreeMatin || r.heureSortieMatin || r.heureEntreeAm || r.heureSortieAm);
                        const isMod = isRowModified(r, initialGrilleData);
                        return hasHours || isMod;
                    });
                }
            }

            if (payload.length === 0) {
                return true;
            }

            const res = await pointageSaisieService.enregistrerBatch(payload);
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
        toggleRowHoraireContinu,
        updateRowStatut,
        updateRowIndemnite,
        updateRowDynamicIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        bulkApplyStandardHours,
        bulkApplyStatut,
        bulkApplyIndemnite,
        saveAll
    };
}

export default usePointageSaisie;
