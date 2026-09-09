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
                    updated.indemniteRepasRecue = false;
                    updated.typeCongeJour = codeStatut === 'CONGE' ? 'FULL_DAY' : null;
                } else if (codeStatut === 'PRESENT') {
                    const isBlank = !updated.heureEntreeMatin && !updated.heureSortieMatin && !updated.heureEntreeAm && !updated.heureSortieAm;
                    if (isBlank && siteParams) {
                        updated.heureEntreeMatin = siteParams.heureDebutMatin || '08:00';
                        updated.heureSortieMatin = siteParams.heureFinMatin || '12:00';
                        updated.heureEntreeAm = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
                        updated.heureSortieAm = siteParams.heureFinAm || '17:00';
                        updated.deplacementMatin = true;
                        updated.deplacementAm = true;
                    }
                    updated.typeCongeJour = null;
                } else if (codeStatut === 'DISPENSE_POINTAGE') {
                    updated.heureEntreeMatin = '';
                    updated.heureSortieMatin = '';
                    updated.heureEntreeAm = '';
                    updated.heureSortieAm = '';
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
                return { ...row, [field]: value };
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
                    deplacementAm: true
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
                    updated.indemniteRepasRecue = false;
                    updated.typeCongeJour = codeStatut === 'CONGE' ? 'FULL_DAY' : null;
                } else if (codeStatut === 'PRESENT') {
                    const isBlank = !updated.heureEntreeMatin && !updated.heureSortieMatin && !updated.heureEntreeAm && !updated.heureSortieAm;
                    if (isBlank && siteParams) {
                        updated.heureEntreeMatin = siteParams.heureDebutMatin || '08:00';
                        updated.heureSortieMatin = siteParams.heureFinMatin || '12:00';
                        updated.heureEntreeAm = siteParams.heureDebutAm || (siteParams.horaireContinu ? '13:00' : '14:00');
                        updated.heureSortieAm = siteParams.heureFinAm || '17:00';
                        updated.deplacementMatin = true;
                        updated.deplacementAm = true;
                    }
                    updated.typeCongeJour = null;
                }

                return calculateRowValues(updated, siteParams);
            })
        );
    };

    const bulkApplyIndemnite = (empIds, field, value) => {
        if (!empIds || empIds.size === 0) return;
        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;
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
