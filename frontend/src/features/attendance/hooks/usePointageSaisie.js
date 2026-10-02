import { useState, useEffect, useCallback, useRef } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { isRowDataModified } from '@/features/attendance/utils/pointageCalculations';
import { getTodayDateString } from '@/utils/dateUtils';

export function usePointageSaisie(initialSiteId = 1, initialDate = getTodayDateString()) {
    // État local : site et date sélectionnés.
    const [selectedSite, setSelectedSite] = useState(() => (Number(initialSiteId) > 0 ? Number(initialSiteId) : null));
    const [selectedDate, setSelectedDate] = useState(initialDate);

    // Effets : synchronisent les valeurs initiales fournies par le parent.
    useEffect(() => {
        if (initialDate && initialDate !== selectedDate) {
            setSelectedDate(initialDate);
        }
    }, [initialDate]); // eslint-disable-line react-hooks/exhaustive-deps

    useEffect(() => {
        const nextSiteId = Number(initialSiteId);
        if (nextSiteId > 0 && nextSiteId !== Number(selectedSite)) {
            setSelectedSite(nextSiteId);
        }
    }, [initialSiteId]); // eslint-disable-line react-hooks/exhaustive-deps
    // État local : grille, paramètres et états de requête.
    const [grilleData, setGrilleData] = useState([]);
    const [initialGrilleData, setInitialGrilleData] = useState([]);
    const [siteParams, setSiteParams] = useState(null);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [periodeInfo, setPeriodeInfo] = useState(null);
    const [statsJour, setStatsJour] = useState(null);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);
    const previewTimers = useRef(new Map());

    // Comparateur précis délégué à l'utilitaire pur
    const isRowModified = isRowDataModified;

    const resetAllChanges = useCallback(() => {
        if (initialGrilleData && initialGrilleData.length > 0) {
            setGrilleData(JSON.parse(JSON.stringify(initialGrilleData)));
        }
    }, [initialGrilleData]);

    const markForPreview = (row) => ({
        ...row,
        totalHeures: null,
        heuresMatin: null,
        heuresAm: null,
        heuresNuit: null,
        minutesRetard: null,
        hasValidationError: false,
        validationMessages: [],
        _previewPending: true,
        _previewError: null
    });

    const loadData = useCallback(async () => {
        const siteId = Number(selectedSite);
        if (!siteId || siteId <= 0) {
            setGrilleData([]);
            setInitialGrilleData([]);
            setSiteParams(null);
            setSiteIndemnites([]);
            setLoading(false);
            return;
        }
        setLoading(true);
        setError(null);
        try {
            const data = await attendanceService.getGrilleSaisie({
                idSite: siteId,
                date: selectedDate
            });
            if (data) {
                const sParams = data.siteParams || null;
                setSiteParams(sParams);
                setSiteIndemnites(data.siteIndemnites || []);
                if (data.grilleData) {
                    setGrilleData(data.grilleData);
                    setInitialGrilleData(JSON.parse(JSON.stringify(data.grilleData)));
                }
                setPeriodeInfo(data.periodeInfo || null);
                if (data.statsJour) {
                    setStatsJour(data.statsJour);
                }
            }
        } catch (err) {
            console.error('Erreur chargement saisie pointages:', err);
            const serverMessage = err?.response?.data?.message;
            setError(serverMessage || "Impossible de charger les données de pointage pour ce site.");
        } finally {
            setLoading(false);
        }
    }, [selectedSite, selectedDate]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    useEffect(() => {
        const timers = previewTimers.current;
        grilleData.filter((row) => row._previewPending).forEach((row) => {
            const id = row.idEmploye;
            const existingTimer = timers.get(id);
            if (existingTimer) clearTimeout(existingTimer);
            const inputSignature = JSON.stringify([
                row.idEmploye, row.idSite, row.datePointage,
                row.heureEntreeMatin, row.heureSortieMatin, row.heureEntreeAm, row.heureSortieAm,
                row.codeStatut, row.typeCongeJour, row.horaireContinu, row.estAutonome, row.ferieJour,
                row.congePaye, row.congeDecompteSolde
            ]);
            const timer = setTimeout(async () => {
                try {
                    const preview = await attendanceService.previewDay(row);
                    setGrilleData((current) => current.map((item) => {
                        if (item.idEmploye !== id || !item._previewPending) return item;
                        const currentSignature = JSON.stringify([
                            item.idEmploye, item.idSite, item.datePointage,
                            item.heureEntreeMatin, item.heureSortieMatin, item.heureEntreeAm, item.heureSortieAm,
                            item.codeStatut, item.typeCongeJour, item.horaireContinu, item.estAutonome, item.ferieJour,
                            item.congePaye, item.congeDecompteSolde
                        ]);
                        if (currentSignature !== inputSignature) return item;

                        // The preview endpoint returns the full DTO, including the editable
                        // times. Keep the values currently being edited and apply only its
                        // calculated/validation fields; otherwise an async preview can
                        // replace a user's draft with normalized or stale input values.
                        return {
                            ...item,
                            heuresMatin: preview.heuresMatin,
                            heuresAm: preview.heuresAm,
                            totalHeures: preview.totalHeures,
                            heuresNuit: preview.heuresNuit,
                            minutesRetard: preview.minutesRetard,
                            codeStatut: preview.codeStatut,
                            libelleStatut: preview.libelleStatut,
                            hasValidationError: preview.hasValidationError,
                            validationMessages: preview.validationMessages || [],
                            statusActions: preview.statusActions || [],
                            statutBadgeVariant: preview.statutBadgeVariant,
                            statutBadgeLabel: preview.statutBadgeLabel,
                            present: preview.present,
                            retard: preview.retard,
                            conge: preview.conge,
                            absenceJustifiee: preview.absenceJustifiee,
                            absenceNonJustifiee: preview.absenceNonJustifiee,
                            nonSaisi: preview.nonSaisi,
                            anomalie: preview.anomalie,
                            dispensePointage: preview.dispensePointage,
                            ferieTravaille: preview.ferieTravaille,
                            ferieChome: preview.ferieChome,
                            samediTravaille: preview.samediTravaille,
                            dimancheTravaille: preview.dimancheTravaille,
                            repos: preview.repos,
                            _previewPending: false,
                            _previewError: null
                        };
                    }));
                } catch (previewError) {
                    setGrilleData((current) => current.map((item) => item.idEmploye === id && item._previewPending
                        ? { ...item, _previewPending: false, _previewError: getErrorMessage(previewError, 'Prévisualisation indisponible.') }
                        : item));
                } finally {
                    previewTimers.current.delete(id);
                }
            }, 250);
            timers.set(id, timer);
        });
        return () => {
            timers.forEach(clearTimeout);
            timers.clear();
        };
    }, [grilleData]);

    const updateRowHours = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = { ...row, [field]: value, codeStatut: 'NON_SAISI' };
                return markForPreview(updated);
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
                    horaireContinuJour: nextVal,
                    codeStatut: 'NON_SAISI'
                };
                return markForPreview(updated);
            })
        );
    };

    const updateRowStatut = (idEmploye, codeStatut, motifAjustement = '') => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = {
                    ...row,
                    codeStatut,
                    motifAjustement: motifAjustement || '',
                    typeCongeJour: codeStatut === 'CONGE' ? 'FULL_DAY' : null
                };
                return markForPreview(updated);
            })
        );
    };

    const bulkApplyStandardHours = (empIds) => {
        if (!siteParams || !empIds || empIds.size === 0) return;
        const { heureDebutMatin: hDebM, heureFinMatin: hFinM,
            heureDebutAm: hDebA, heureFinAm: hFinA } = siteParams;
        if (![hDebM, hFinM, hDebA, hFinA].every(Boolean)) return;

        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;

                const filled = {
                    ...row,
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA,
                    codeStatut: 'NON_SAISI',
                };
                return markForPreview(filled);
            })
        );
    };

    const bulkApplyStatut = (empIds, codeStatut, motifAjustement = '') => {
        if (!empIds || empIds.size === 0) return;
        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;
                const updated = {
                    ...row,
                    codeStatut,
                    motifAjustement: motifAjustement || '',
                    typeCongeJour: codeStatut === 'CONGE' ? 'FULL_DAY' : null
                };
                return markForPreview(updated);
            })
        );
    };

    const bulkApplyIndemnite = (empIds, idIndemnite, fractionnable) => {
        if (!empIds || empIds.size === 0) return;
        setGrilleData((prev) => prev.map((row) => {
            if (!empIds.has(row.idEmploye)) return row;
            const key = String(idIndemnite);
            const current = row.dynamicIndemnites?.[key] || {};
            return markForPreview({
                ...row,
                dynamicIndemnites: {
                    ...(row.dynamicIndemnites || {}),
                    [key]: {
                        ...current,
                        accordee: true,
                        fractionJour: 1,
                        ...(fractionnable ? { matin: true, am: true } : {}),
                    },
                },
            });
        }));
    };

    const updateRowDynamicIndemnite = (idEmploye, idIndemnite, value, fraction) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const existingDyn = { ...(row.dynamicIndemnites || {}) };
                const current = { ...(existingDyn[String(idIndemnite)] || {}) };
                const updated = { ...row };
                const boolVal = Boolean(value);

                if (fraction === 'MATIN') {
                    current.matin = boolVal;
                } else if (fraction === 'APRES_MIDI') {
                    current.am = boolVal;
                } else {
                    current.accordee = boolVal;
                }

                if (fraction === 'MATIN' || fraction === 'APRES_MIDI') {
                    current.accordee = Boolean(current.matin || current.am);
                    current.fractionJour = (current.matin ? 0.5 : 0) + (current.am ? 0.5 : 0);
                } else {
                    current.fractionJour = boolVal ? 1 : 0;
                }

                existingDyn[String(idIndemnite)] = current;
                updated.dynamicIndemnites = existingDyn;
                return markForPreview(updated);
            })
        );
    };

    const autoFillStandardHours = () => {
        if (!siteParams) return;
        const { heureDebutMatin: hDebM, heureFinMatin: hFinM,
            heureDebutAm: hDebA, heureFinAm: hFinA } = siteParams;
        if (![hDebM, hFinM, hDebA, hFinA].every(Boolean)) return;

        setGrilleData((prev) =>
            prev.map((row) => {
                const isBlank = !row.heureEntreeMatin && !row.heureSortieMatin && !row.heureEntreeAm && !row.heureSortieAm;
                if (!isBlank) return row;

                const filled = {
                    ...row,
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA,
                    codeStatut: 'NON_SAISI'
                };
                return markForPreview(filled);
            })
        );
    };

    const autoDeduceAllPauses = () => {
        if (!siteParams) return;
        const hFinM = siteParams.heureFinMatin;
        const hDebA = siteParams.heureDebutAm;

        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm)) {
                    const filled = {
                        ...row,
                        heureSortieMatin: row.heureSortieMatin || hFinM,
                        heureEntreeAm: row.heureEntreeAm || hDebA,
                        codeStatut: 'NON_SAISI'
                    };
                    return markForPreview(filled);
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
                payload = grilleData.filter((row) => {
                    // Un autonome sans pointage persisté doit toujours être inclus :
                    // ses indemnités (cochées par défaut) ne sont jamais en DB et ne seront
                    // jamais détectées comme "modifiées" par isRowModified car initial == courant.
                    // Exception : on exclut les week-ends et fériés chômés sans pointage existant
                    // (le backend les skipperait de toute façon, inutile de les envoyer).
                    const isWeekendOrFerie = row.codeStatut === 'REPOS' || row.codeStatut === 'FERIE_CHOME';
                    const isAutonomeNonPersiste = row.estAutonome && !row.idPointage && !isWeekendOrFerie;
                    return isAutonomeNonPersiste || isRowModified(row, initialGrilleData);
                });
            }

            if (payload.length === 0) {
                return true;
            }

            const res = await attendanceService.enregistrerBatch(payload);
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
