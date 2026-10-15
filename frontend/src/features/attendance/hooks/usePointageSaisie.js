import { useState, useEffect, useCallback, useRef } from 'react';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { getTodayDateString } from '@/utils/dateUtils';
import { usePointageBatchPreview } from '@/features/attendance/hooks/usePointageBatchPreview';
import { changePointageAllowance, changePointageHours, changePointageStatus, isPointageRowModified, markPointageForPreview } from '@/features/attendance/utils/pointageRow';
import { toPointageBatchPayload } from '@/features/attendance/utils/pointageBatchPayload';

const dailyRowKey = row => row.idEmploye;
const isRowDataModified = (row, initialList) => isPointageRowModified(
    row, initialList?.find(item => item.idEmploye === row?.idEmploye)
);

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
    const loadSequence = useRef(0);
    const [loadedSelection, setLoadedSelection] = useState(null);
    const selectionKey = `${selectedSite}:${selectedDate}`;
    const hasCurrentData = loadedSelection === selectionKey;

    // Comparateur précis délégué à l'utilitaire pur
    const isRowModified = isRowDataModified;

    const resetAllChanges = useCallback(() => {
        if (initialGrilleData && initialGrilleData.length > 0) {
            setGrilleData(JSON.parse(JSON.stringify(initialGrilleData)));
        }
    }, [initialGrilleData]);

    const markForPreview = (row) => ({
        ...markPointageForPreview(row),
        totalHeures: null,
        heuresMatin: null,
        heuresAm: null,
        heuresNuit: null,
        minutesRetard: null
    });

    const loadData = useCallback(async () => {
        const sequence = ++loadSequence.current;
        const siteId = Number(selectedSite);
        if (!siteId || siteId <= 0) {
            setGrilleData([]);
            setInitialGrilleData([]);
            setSiteParams(null);
            setSiteIndemnites([]);
            setLoadedSelection(null);
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
            if (sequence !== loadSequence.current) return;
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
                setLoadedSelection(`${siteId}:${selectedDate}`);
            }
        } catch (err) {
            if (sequence !== loadSequence.current) return;
            console.error('Erreur chargement saisie pointages:', err);
            const serverMessage = err?.response?.data?.message;
            setError(serverMessage || "Impossible de charger les données de pointage pour ce site.");
        } finally {
            if (sequence === loadSequence.current) setLoading(false);
        }
    }, [selectedSite, selectedDate]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    usePointageBatchPreview(grilleData, setGrilleData, dailyRowKey);

    const updateRowHours = (idEmploye, field, value) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = changePointageHours(row, { [field]: value });
                return markForPreview(updated);
            })
        );
    };

    const toggleRowHoraireContinu = (idEmploye) => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const nextVal = !row.horaireContinu;
                const updated = changePointageHours(row, {
                    horaireContinu: nextVal,
                    horaireContinuJour: nextVal
                });
                return markForPreview(updated);
            })
        );
    };

    const updateRowStatut = (idEmploye, codeStatut, motifAjustement = '') => {
        setGrilleData((prev) =>
            prev.map((row) => {
                if (row.idEmploye !== idEmploye) return row;
                const updated = changePointageStatus(row, codeStatut, motifAjustement || '');
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

                const filled = changePointageHours(row, {
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA
                });
                return markForPreview(filled);
            })
        );
    };

    const bulkApplyStatut = (empIds, codeStatut, motifAjustement = '') => {
        if (!empIds || empIds.size === 0) return;
        setGrilleData((prev) =>
            prev.map((row) => {
                if (!empIds.has(row.idEmploye)) return row;
                const updated = changePointageStatus(row, codeStatut, motifAjustement || '');
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
                const current = existingDyn[String(idIndemnite)] || {};
                const updated = { ...row };
                existingDyn[String(idIndemnite)] = changePointageAllowance(current, value, fraction);
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

                const filled = changePointageHours(row, {
                    heureEntreeMatin: hDebM,
                    heureSortieMatin: hFinM,
                    heureEntreeAm: hDebA,
                    heureSortieAm: hFinA
                });
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
                    const filled = changePointageHours(row, {
                        heureSortieMatin: row.heureSortieMatin || hFinM,
                        heureEntreeAm: row.heureEntreeAm || hDebA
                    });
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

            const res = await attendanceService.daily.saveBatch(payload.map(toPointageBatchPayload));
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
        hasCurrentData,
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
