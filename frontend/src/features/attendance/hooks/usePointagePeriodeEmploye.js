import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';
import { getTodayDateString } from '@/utils/dateUtils';
import { toPointageBatchPayload } from '@/features/attendance/utils/pointageBatchPayload';
import { getConfiguredPause, getConfiguredStandardHours } from '@/features/attendance/utils/pointageStandardHours';
import { importHalfDayMoment, importLineNeedsReview } from '@/features/attendance/utils/pointageImport';
import { usePointageBatchPreview } from '@/features/attendance/hooks/usePointageBatchPreview';
import { changePointageAllowance, changePointageHours, changePointageStatus, cleanPointageTime, clearPointageAllowances, isPointageRowModified, markPointageForPreview, POINTAGE_TIME_FIELDS } from '@/features/attendance/utils/pointageRow';

// ─── Helpers purs ─────────────────────────────────────────────────────────────

const cleanTime = cleanPointageTime;
const periodRowKey = row => row.datePointage;
const decoratePeriodPreview = (row, preview) => ({
    ...row,
    heuresFaites: preview.totalHeures != null ? formatHeuresMinutes(preview.totalHeures) : row.heuresFaites,
    totalHeuresDecimal: preview.totalHeures != null ? preview.totalHeures : row.totalHeuresDecimal,
    minutesRetard: preview.minutesRetard ?? 0
});

export const isUnenteredNewDay = (row) => !row?.idPointage
    && row?.codeStatut === 'NON_SAISI'
    && !POINTAGE_TIME_FIELDS.some(field => cleanTime(row[field]));

export const clearDynamicAllowances = clearPointageAllowances;

const parseHhMmToDecimal = (str) => {
    if (!str || typeof str !== 'string') return 0.0;
    const clean = str.replace('h', ':').trim();
    const parts = clean.split(':');
    if (parts.length >= 2) {
        const h = parseInt(parts[0], 10) || 0;
        const m = parseInt(parts[1], 10) || 0;
        return Math.round((h + m / 60) * 100) / 100;
    }
    return parseFloat(str) || 0.0;
};

/**
 * Comparateur de modification pour une ligne journalière.
 * Aligné sur isRowDataModified de usePointageSaisie.
 */
export const isDayRowModified = isPointageRowModified;

// ─── Hook principal ───────────────────────────────────────────────────────────

export function usePointagePeriodeEmploye({
    selectedEmployeId,
    selectedPeriodeId,
    selectedSiteId,
    currentEmpSummary,
    periodesList,
    toast
}) {
    const notify = useCallback((args) => {
        if (!toast) return;
        if (typeof toast === 'function') {
            toast(args);
        } else if (typeof toast?.toast === 'function') {
            toast.toast(args);
        }
    }, [toast]);

    // ── État grille ──────────────────────────────────────────────────────────
    const [ficheData, setFicheData] = useState(null);
    const [siteParams, setSiteParams] = useState(null);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [extraImportIndemnites, setExtraImportIndemnites] = useState([]);
    const [grilleRows, setGrilleRows] = useState([]);
    const [initialRows, setInitialRows] = useState([]);
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // ── État import ──────────────────────────────────────────────────────────
    const [importPreview, setImportPreview] = useState(null);
    const [importSelections, setImportSelections] = useState({});
    const [importSignals, setImportSignals] = useState({});
    const [importLoading, setImportLoading] = useState(false);
    const [importPreviewLoading, setImportPreviewLoading] = useState(false);
    const [importApplying, setImportApplying] = useState(false);

    // ── Refs internes ────────────────────────────────────────────────────────
    const loadReqId = useRef(0);
    const importApplyLock = useRef(false);

    useEffect(() => { setImportSignals({}); }, [selectedEmployeId, selectedPeriodeId]);

    // ── Indemnités affichées (site + import fusionnés) ───────────────────────
    const displayedIndemnites = useMemo(() => {
        const byId = new Map();
        [...siteIndemnites, ...extraImportIndemnites].forEach(ind => byId.set(String(ind.idIndemnite), ind));
        return [...byId.values()];
    }, [siteIndemnites, extraImportIndemnites]);

    // ── Champ calculé : autonome ─────────────────────────────────────────────
    const isAutonome = Boolean(
        ficheData?.estAutonome || ficheData?.dispensePointage ||
        currentEmpSummary?.estAutonome || currentEmpSummary?.dispensePointage
    );

    // ── Chargement de la fiche employé ───────────────────────────────────────
    const loadFicheEmploye = useCallback(async ({ keepImportPreview = false } = {}) => {
        if (!selectedEmployeId || !selectedPeriodeId) {
            setFicheData(null); setGrilleRows([]); setInitialRows([]); setImportPreview(null);
            setLoading(false); return;
        }
        const reqId = ++loadReqId.current;
        setLoading(true); setError(null);
        try {
            const fiche = await attendanceService.getFicheEmploye(selectedEmployeId, {
                idPeriode: selectedPeriodeId, propositionsIndemnites: true
            });
            const selectedPeriod = periodesList.find(p => Number(p.idPeriodePaie) === Number(selectedPeriodeId));
            const referenceDate = selectedPeriod?.dateDebut || fiche?.dateDebut || getTodayDateString();

            const firstDatedSite = fiche?.pointagesData?.find(day => Number(day.idSite) > 0);
            const referenceSiteId = Number(selectedSiteId) > 0 ? Number(selectedSiteId)
                : Number(currentEmpSummary?.idSite) > 0 ? Number(currentEmpSummary.idSite)
                : Number(firstDatedSite?.idSite) > 0 ? Number(firstDatedSite.idSite) : null;
            const siteReferenceDay = fiche?.pointagesData?.find(day => Number(day.idSite) === referenceSiteId);
            const siteGrid = referenceSiteId ? await attendanceService.getGrilleSaisie({
                idSite: referenceSiteId, idEmploye: selectedEmployeId,
                date: siteReferenceDay?.dateComplete || referenceDate
            }) : null;

            if (reqId !== loadReqId.current) return;

            setFicheData(fiche);
            const sParams = siteGrid?.siteParams || null;
            setSiteParams(sParams);
            const allowanceCatalog = new Map();
            (fiche?.pointagesData || []).forEach(day => {
                Object.entries(day.indemnitesApplicables || {}).forEach(([key, applicable]) => {
                    allowanceCatalog.set(key, {
                        idIndemnite: applicable.idIndemnite, code: applicable.code,
                        libelle: applicable.libelle, fractionnable: applicable.fractionnable,
                        estFractionnable: applicable.fractionnable,
                        periodicite: applicable.periodicite, montantJour: applicable.montantJour,
                        montantTrajet: applicable.montantTrajet
                    });
                });
            });
            const sIndemnites = [...allowanceCatalog.values()];
            setSiteIndemnites(sIndemnites);
            if (!keepImportPreview) {
                setExtraImportIndemnites([]);
                setImportPreview(null);
            }

            const empAutonome = Boolean(
                fiche?.estAutonome || fiche?.dispensePointage ||
                currentEmpSummary?.estAutonome || currentEmpSummary?.dispensePointage
            );

            const rows = (fiche?.pointagesData || []).map((ptg) => {
                const isWeekend = Boolean(ptg.weekend || ptg.jour?.startsWith('Sam') || ptg.jour?.startsWith('Dim')
                    || (ptg.dateComplete && [0, 6].includes(new Date(`${ptg.dateComplete}T00:00:00`).getDay())));
                const isFerie = Boolean(ptg.libelleFerie || ptg.isFerieChome || ptg.isFerie || ['FERIE_CHOME','FERIE','FERIE_TRAVAILLE'].includes(ptg.statut));
                const isConge = Boolean(ptg.isConge || ptg.statut === 'CONGE' || ptg.statut === 'CONGE_PAYE');

                const rawEntree = cleanTime(ptg.entree);
                const rawSortieMatin = cleanTime(ptg.sortiePause);
                const rawEntreeAm = cleanTime(ptg.retourPause);
                const rawSortieAm = cleanTime(ptg.sortie);
                const hasSavedHours = Boolean(rawEntree || rawSortieAm);
                // La fiche serveur est la source des heures et du statut. Un horaire
                // standard ne représente jamais un pointage réellement enregistré.
                const codeStatut = ptg.statut || 'NON_SAISI';

                const dateAllowances = Object.values(ptg.indemnitesApplicables || {}).map(applicable => ({
                    idIndemnite: applicable.idIndemnite, code: applicable.code,
                    libelle: applicable.libelle, fractionnable: applicable.fractionnable,
                    estFractionnable: applicable.fractionnable,
                    periodicite: applicable.periodicite, montantJour: applicable.montantJour,
                    montantTrajet: applicable.montantTrajet
                }));
                const dynamicIndemnites = {};
                dateAllowances.forEach(ind => {
                    const key = String(ind.idIndemnite);
                    const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
                    const saved = ptg.indemnites?.[ind.code] || ptg.indemnites?.[key];
                    const proposed = ptg.indemnitesProposees?.[key];
                    if (!saved && !proposed) return;
                    if (saved && typeof saved === 'object') {
                        const acc = Boolean(saved.accordee ?? saved.accorde);
                        const qte = Number(saved.quantite ?? (acc ? 1 : 0));
                        dynamicIndemnites[key] = {
                            accordee: acc, fractionJour: qte,
                            matin: saved.matin ?? (fractionnable ? (acc && Boolean(ptg.deplacementMatin ?? true)) : undefined),
                            am: saved.am ?? (fractionnable ? (acc && Boolean(ptg.deplacementAm ?? true)) : undefined),
                            motifSuggestion: proposed?.motifSuggestion,
                            montantApplique: saved.montant || ind.montantJour
                        };
                    } else {
                        const shouldAccord = Boolean(proposed?.accordee);
                        dynamicIndemnites[key] = {
                            accordee: shouldAccord, fractionJour: shouldAccord ? 1 : 0,
                            matin: fractionnable ? Boolean(proposed?.matin) : undefined,
                            am: fractionnable ? Boolean(proposed?.am) : undefined,
                            motifSuggestion: proposed?.motifSuggestion,
                            montantApplique: ind.montantJour
                        };
                    }
                });

                const hasServerHours = ptg.heures && ptg.heures !== '—' && ptg.heures !== '-';
                const totalHeuresDec = ptg.totalHeuresDecimal != null
                    ? Number(ptg.totalHeuresDecimal) || 0
                    : (hasServerHours ? parseHhMmToDecimal(ptg.heures) : 0);
                const heuresFaitesStr = hasServerHours ? ptg.heures : formatHeuresMinutes(totalHeuresDec);

                const repasInd = dateAllowances.find(i => i.code === 'REPAS');
                const depInd = dateAllowances.find(i => ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(i.code));

                return {
                    idPointage: ptg.idPointage || null, version: ptg.version ?? null,
                    idEmploye: selectedEmployeId,
                    idPeriodePaie: selectedPeriodeId,
                    idSite: ptg.idSite || selectedSiteId || fiche?.idSite || 1,
                    datePointage: ptg.dateComplete, date: ptg.date, jour: ptg.jour,
                    weekend: isWeekend, ferie: isFerie, libelleFerie: ptg.libelleFerie || null, conge: isConge,
                    congeApprouve: Boolean(ptg.congeApprouve || (isConge && ptg.statut === 'CONGE' && !hasSavedHours && ptg.congeValide)),
                    canCorrect: ptg.canCorrect !== false,
                    heureEntreeMatin: rawEntree, heureSortieMatin: rawSortieMatin,
                    heureEntreeAm: rawEntreeAm, heureSortieAm: rawSortieAm,
                    horaireContinu: Boolean(ptg.horaireContinuJour),
                    codeStatut, motifAjustement: ptg.observation || '',
                    heuresFaites: heuresFaitesStr, totalHeuresDecimal: totalHeuresDec,
                    minutesRetard: ptg.minutesRetard ?? 0,
                    indemniteRepas: repasInd ? Boolean(dynamicIndemnites[String(repasInd.idIndemnite)]?.accordee)
                        : Boolean(ptg.indemniteRepas),
                    deplacementMatin: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.matin)
                        : Boolean(ptg.deplacementMatin),
                    deplacementAm: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.am)
                        : Boolean(ptg.deplacementAm),
                    dynamicIndemnites, _allowanceDefaults: ptg.indemnitesProposees || {},
                    _dateAllowances: dateAllowances,
                    estAutonome: empAutonome,
                    _previewPending: false, _isModified: false
                };
            });

            setGrilleRows(rows);
            setInitialRows(JSON.parse(JSON.stringify(rows)));
        } catch (err) {
            if (reqId === loadReqId.current) {
                console.error('Erreur chargement fiche saisie employé:', err);
                setError(err?.response?.data?.message || 'Impossible de charger la fiche de ce collaborateur.');
            }
        } finally {
            if (reqId === loadReqId.current) setLoading(false);
        }
    }, [selectedEmployeId, selectedPeriodeId, selectedSiteId, currentEmpSummary, periodesList]);

    useEffect(() => { loadFicheEmploye(); }, [loadFicheEmploye]);

    // Même prévisualisation que la saisie quotidienne, avec contrôle des réponses périmées.
    usePointageBatchPreview(grilleRows, setGrilleRows, periodRowKey, decoratePeriodPreview, 300);

    // ── Lignes modifiées ─────────────────────────────────────────────────────
    const modifiedRows = useMemo(() => {
        if (!initialRows || initialRows.length === 0) return [];
        return grilleRows.filter(row => {
            const initial = initialRows.find(i => i.datePointage === row.datePointage);
            return !isUnenteredNewDay(row) && isDayRowModified(row, initial);
        });
    }, [grilleRows, initialRows]);
    const dirtyCount = modifiedRows.length;

    // ── canEditPeriod : aligné sur EDIT_POINTAGE (comme SaisieQuotidiennePage) ──
    const canEditPeriod = useMemo(() => {
        if (!ficheData) return false;
        const actions = ficheData.availableActions || {};
        if (actions.EDIT_POINTAGE !== undefined) return actions.EDIT_POINTAGE?.allowed !== false;
        return actions.CORRECT_DAY !== false;
    }, [ficheData]);

    // ── syncRowIndemnites ────────────────────────────────────────────────────
    const syncRowIndemnites = useCallback((row, hours, indList) => {
        const hasMatin = Boolean(hours.heureEntreeMatin || hours.heureSortieMatin);
        const hasAm = Boolean(hours.heureEntreeAm || hours.heureSortieAm);
        const hasHours = hasMatin || hasAm;
        const nextDyn = {};

        indList.forEach(ind => {
            const key = String(ind.idIndemnite);
            const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
            let matVal = false, amVal = false, accordeeVal = false;
            if (row._allowanceDefaults?.[key]?.accordee) {
                if (fractionnable) {
                    matVal = hasHours ? hasMatin : true;
                    amVal = hasHours ? hasAm : true;
                    accordeeVal = matVal || amVal;
                } else {
                    accordeeVal = true;
                }
            }
            nextDyn[key] = {
                accordee: accordeeVal,
                fractionJour: fractionnable ? ((matVal ? 0.5 : 0) + (amVal ? 0.5 : 0)) : (accordeeVal ? 1 : 0),
                matin: fractionnable ? matVal : undefined,
                am: fractionnable ? amVal : undefined,
                motifSuggestion: row._allowanceDefaults?.[key]?.motifSuggestion,
                montantApplique: ind.montantJour
            };
        });

        const repasInd = indList.find(i => i.code === 'REPAS');
        const depInd = indList.find(i => ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(i.code));
        const repasObj = repasInd ? nextDyn[String(repasInd.idIndemnite)] : null;
        const depObj = depInd ? nextDyn[String(depInd.idIndemnite)] : null;
        return {
            dynamicIndemnites: nextDyn,
            indemniteRepas: repasObj ? repasObj.accordee : false,
            deplacementMatin: depObj ? Boolean(depObj.matin) : false,
            deplacementAm: depObj ? Boolean(depObj.am) : false
        };
    }, []);

    // ── Mutations de la grille ───────────────────────────────────────────────
    const updateDayHours = useCallback((datePointage, field, value) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            const updatedHours = {
                heureEntreeMatin: field === 'heureEntreeMatin' ? value : row.heureEntreeMatin,
                heureSortieMatin: field === 'heureSortieMatin' ? value : row.heureSortieMatin,
                heureEntreeAm:    field === 'heureEntreeAm'    ? value : row.heureEntreeAm,
                heureSortieAm:    field === 'heureSortieAm'    ? value : row.heureSortieAm,
            };
            const indList = row._dateAllowances || siteIndemnites;
            const syncedIndemnites = syncRowIndemnites(row, updatedHours, indList);
            return {
                ...changePointageHours(row, updatedHours),
                ...syncedIndemnites
            };
        }));
    }, [siteIndemnites, syncRowIndemnites]);

    const toggleDayContinu = useCallback((datePointage) => {
        setGrilleRows(prev => prev.map(row => row.datePointage !== datePointage ? row
            : changePointageHours(row, { horaireContinu: !row.horaireContinu, horaireContinuJour: !row.horaireContinu })));
    }, []);

    const updateDayStatut = useCallback((datePointage, codeStatut, motif = '') => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            return changePointageStatus(row, codeStatut, motif);
        }));
    }, []);

    const updateRowDynamicIndemnite = useCallback((datePointage, idIndemnite, value, moment) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            const key = String(idIndemnite);
            const existingDyn = { ...(row.dynamicIndemnites || {}) };
            const previous = existingDyn[key] || {};
            const allowance = (row._dateAllowances || siteIndemnites).find(i => String(i.idIndemnite) === key);
            const fractionnable = allowance?.estFractionnable !== undefined ? allowance.estFractionnable : allowance?.fractionnable;
            const defaultGranted = Boolean(row._allowanceDefaults?.[key]?.accordee);
            const current = {
                accordee: Boolean(previous.accordee ?? defaultGranted),
                matin: previous.matin ?? (fractionnable ? defaultGranted : undefined),
                am: previous.am ?? (fractionnable ? defaultGranted : undefined),
                ...previous
            };
            const next = changePointageAllowance(current, value, moment);
            existingDyn[key] = next;
            const ind = allowance;
            return {
                ...markPointageForPreview(row), dynamicIndemnites: existingDyn,
                ...(ind?.code === 'REPAS' ? { indemniteRepas: next.accordee } : {}),
                ...(['DEPLACEMENT','DPLCT','TRANSPORT'].includes(ind?.code)
                    ? { deplacementMatin: Boolean(next.matin), deplacementAm: Boolean(next.am) } : {})
            };
        }));
    }, [siteIndemnites]);

    // ── Assistants ───────────────────────────────────────────────────────────
    const handleApplyStandardHours = useCallback(() => {
        const stdHours = getConfiguredStandardHours(siteParams);
        if (!stdHours) return;
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            const isBlank = !row.heureEntreeMatin && !row.heureSortieMatin && !row.heureEntreeAm && !row.heureSortieAm;
            if (!isBlank) return row;
            count++;
            return { ...changePointageHours(row, stdHours),
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites) };
        }));
        notify(count > 0
            ? { title: 'Horaire standard appliqué', message: `${count} journée(s) mise(s) à jour.`, type: 'success' }
            : { title: 'Information', message: 'Toutes les journées ouvrées sont déjà renseignées.', type: 'info' });
    }, [siteParams, siteIndemnites, syncRowIndemnites, notify]);

    const handleDeducePauses = useCallback(() => {
        const pause = getConfiguredPause(siteParams);
        if (!pause) return;
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm) && !row.horaireContinu) {
                count++;
                return changePointageHours(row, pause);
            }
            return row;
        }));
        notify(count > 0
            ? { title: 'Pause déjeuner déduite', message: `${count} journée(s) mise(s) à jour.`, type: 'success' }
            : { title: 'Information', message: 'Aucune pause à déduire.', type: 'info' });
    }, [siteParams, notify]);

    const handleResetDay = useCallback((datePointage) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage || !row.canCorrect) return row;
            const emptyHours = { heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '' };
            return { ...row, ...emptyHours, horaireContinu: false,
                codeStatut: row.weekend ? 'REPOS' : row.ferie ? 'FERIE_CHOME' : row.conge ? 'CONGE' : 'NON_SAISI',
                motifAjustement: '',
                totalHeuresDecimal: 0, heuresFaites: '00h00', minutesRetard: 0,
                ...syncRowIndemnites(row, emptyHours, row._dateAllowances || siteIndemnites),
                _previewPending: true };
        }));
    }, [siteIndemnites, syncRowIndemnites]);

    const handleApplyDayStandard = useCallback((datePointage) => {
        const stdHours = getConfiguredStandardHours(siteParams);
        if (!stdHours) return;
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage || !row.canCorrect) return row;
            return { ...changePointageHours(row, stdHours),
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites) };
        }));
    }, [siteParams, siteIndemnites, syncRowIndemnites]);

    const handleApplyWeekStandard = useCallback((weekRows) => {
        const stdHours = getConfiguredStandardHours(siteParams);
        if (!stdHours) return;
        const weekDates = new Set(weekRows.filter(r => r.canCorrect && !r.weekend && !r.ferie && !r.conge).map(r => r.datePointage));
        if (weekDates.size === 0) return;
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!weekDates.has(row.datePointage) || !row.canCorrect) return row;
            count++;
            return { ...changePointageHours(row, stdHours),
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites) };
        }));
        if (count > 0) notify({ title: 'Semaine complétée', message: `${count} jour(s) mis à jour.`, type: 'success' });
    }, [siteParams, siteIndemnites, syncRowIndemnites, notify]);

    // ── Sauvegarde ───────────────────────────────────────────────────────────
    const handleSaveBatch = useCallback(async () => {
        if (!canEditPeriod) return false;
        if (modifiedRows.length === 0) {
            notify({ title: 'Information', message: 'Aucune modification à enregistrer.', type: 'info' });
            return false;
        }
        setSaving(true);
        try {
            const payload = modifiedRows.map(toPointageBatchPayload);
            const res = await attendanceService.daily.saveBatch(payload);
            if (res && res.success !== false) {
                notify({ title: 'Enregistrement confirmé', message: `${payload.length} journée(s) enregistrée(s).`, type: 'success' });
                await loadFicheEmploye();
                return true;
            } else {
                notify({ title: 'Erreur', message: res?.message || "Erreur lors de l'enregistrement.", type: 'error' });
                return false;
            }
        } catch (err) {
            console.error('Erreur sauvegarde batch période:', err);
            notify({ title: 'Erreur', message: err?.response?.data?.message || "Impossible d'enregistrer.", type: 'error' });
            return false;
        } finally { setSaving(false); }
    }, [canEditPeriod, modifiedRows, loadFicheEmploye, notify]);

    // ── Régularisation ───────────────────────────────────────────────────────
    const handleSaveRegularisation = useCallback(async (regularisationRow, payload) => {
        if (!regularisationRow || !payload) return false;
        if (!canEditPeriod || regularisationRow.canCorrect === false) {
            const message = 'Cette journée ou cette période est en lecture seule.';
            notify({ title: 'Correction indisponible', message, type: 'error' });
            return { error: message };
        }
        try {
            const idPtg = regularisationRow.idPointage ? Number(regularisationRow.idPointage) : 0;
            const res = await attendanceService.regulariser(idPtg, {
                ...payload, idEmploye: selectedEmployeId,
                idSite: regularisationRow.idSite || selectedSiteId || 1,
                idPeriodePaie: selectedPeriodeId, datePointage: regularisationRow.datePointage
            });
            if (res && (res.success || res === true)) {
                notify({ title: 'Régularisation effectuée', message: res.message || 'Pointage régularisé avec succès.', type: 'success' });
                setImportSignals(prev => { const next = { ...prev }; delete next[regularisationRow.datePointage]; return next; });
                setImportPreview(current => {
                    if (!current) return null;
                    if (current.phase === 'REGULARIZE') {
                        const remaining = current.lines.filter(line => line.date !== regularisationRow.datePointage);
                        return remaining.length ? { ...current, lines: remaining } : null;
                    }
                    return { ...current, lines: current.lines.map(line =>
                        line.date === regularisationRow.datePointage && line.decision === 'SIGNAL'
                            ? { ...line, decision: 'IGNORE', warnings: ['Régularisation enregistrée'] } : line) };
                });
                setImportSelections(current => ({ ...current, ...Object.fromEntries(
                    (importPreview?.lines || []).filter(line => line.date === regularisationRow.datePointage && line.decision === 'SIGNAL')
                        .map(line => [line.ligne, false])) }));
                const pendingSignals = importPreview?.phase === 'REGULARIZE'
                    ? importPreview.lines.filter(line => line.date !== regularisationRow.datePointage).length : null;
                await loadFicheEmploye({ keepImportPreview: pendingSignals !== 0 });
                return true;
            } else {
                const errorMsg = res?.error || res?.message || 'Erreur lors de la régularisation.';
                notify({ title: 'Erreur', message: errorMsg, type: 'error' });
                return res || false;
            }
        } catch (err) {
            const msg = err?.response?.data?.message || err?.message || 'Erreur lors de la régularisation.';
            notify({ title: 'Erreur', message: msg, type: 'error' });
            return { error: msg };
        }
    }, [canEditPeriod, selectedEmployeId, selectedSiteId, selectedPeriodeId, importPreview, loadFicheEmploye, notify]);

    // ── Import/Export ────────────────────────────────────────────────────────
    const handleDownloadImportTemplate = useCallback(async () => {
        if (!selectedEmployeId || !selectedPeriodeId) return;
        setImportLoading(true);
        try {
            const { blob, filename } = await attendanceService.getImportTemplate(selectedEmployeId, selectedPeriodeId);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url; link.download = filename; link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (err) {
            notify({ title: 'Erreur', message: err?.response?.data?.message || 'Impossible de créer le modèle.', type: 'error' });
        } finally { setImportLoading(false); }
    }, [selectedEmployeId, selectedPeriodeId, notify]);

    const handleImportFile = useCallback(async (file, dirtyCountVal) => {
        if (!file) return;
        if (dirtyCountVal > 0) {
            notify({ title: 'Information', message: 'Enregistrez les modifications avant de prévisualiser un import.', type: 'info' }); return;
        }
        setImportLoading(true);
        setImportPreviewLoading(true);
        try {
            const preview = await attendanceService.previewImport(selectedEmployeId, selectedPeriodeId, file);
            setImportPreview(preview);
            setImportSelections(Object.fromEntries((preview.lines || []).map(line => [line.ligne, line.selectedByDefault])));
        } catch (err) {
            notify({ title: 'Erreur', message: err?.response?.data?.message || 'Impossible de lire le fichier.', type: 'error' });
        } finally { setImportLoading(false); setImportPreviewLoading(false); }
    }, [selectedEmployeId, selectedPeriodeId, notify]);

    const handleApplyImport = useCallback(async (approvedConflicts = {}) => {
        if (importApplyLock.current) return;
        const unresolved = (importPreview?.lines || []).some(line => importSelections[line.ligne]
            && importLineNeedsReview(line, approvedConflicts));
        if (unresolved) {
            notify({ title: 'Import à vérifier', message: 'Corrigez les journées et confirmez chaque remplacement avant de charger la grille.', type: 'error' });
            return;
        }
        importApplyLock.current = true;
        setImportApplying(true);
        await new Promise(resolve => window.setTimeout(resolve, 0));
        try {
        const selected = (importPreview?.lines || []).filter(line =>
            importSelections[line.ligne] && ['IMPORT', 'SIGNAL'].includes(line.decision) && !line.errors?.length);
        const current = new Map(grilleRows.map(row => [row.datePointage, row]));
        const importByDate = new Map();
        const signalsByDate = {};
        const allAllowances = [];
        selected.forEach(line => {
            const row = current.get(line.date);
            if (!row || !row.canCorrect || !line.proposed) return;
            if (line.eventCode) {
                signalsByDate[line.date] = {
                    code: line.eventCode, allowanceOverrides: line.allowanceOverrides || {},
                    halfDayMoment: importHalfDayMoment(line.proposed)
                };
            }
            if (line.decision === 'SIGNAL') return;
            if (row.congeApprouve && !row.conge) return;
            const p = line.proposed; const dyn = p.dynamicIndemnites || {};
            const repas = line.allowances.find(ind => ind.code === 'REPAS');
            const dep = line.allowances.find(ind => ['DPLCT','DEPLACEMENT','TRANSPORT'].includes(ind.code));
            allAllowances.push(...line.allowances);
            importByDate.set(line.date, {
                ...row,
                heureEntreeMatin: p.heureEntreeMatin || '', heureSortieMatin: p.heureSortieMatin || '',
                heureEntreeAm: p.heureEntreeAm || '', heureSortieAm: p.heureSortieAm || '',
                horaireContinu: Boolean(p.horaireContinu), codeStatut: p.codeStatut || row.codeStatut,
                heuresFaites: p.totalHeures != null ? formatHeuresMinutes(p.totalHeures) : '00h00',
                totalHeuresDecimal: Number(p.totalHeures) || 0,
                minutesRetard: Number(p.minutesRetard) || 0,
                libelleStatut: p.libelleStatut,
                hasValidationError: Boolean(p.hasValidationError),
                validationMessages: p.validationMessages || [],
                dynamicIndemnites: dyn,
                indemniteRepas: repas ? Boolean(dyn[String(repas.idIndemnite)]?.accordee) : false,
                deplacementMatin: dep ? Boolean(dyn[String(dep.idIndemnite)]?.matin) : false,
                deplacementAm: dep ? Boolean(dyn[String(dep.idIndemnite)]?.am) : false,
                _dateAllowances: line.allowances, _importPending: true,
                _previewPending: Boolean(p.heureEntreeMatin || p.heureSortieMatin || p.heureEntreeAm || p.heureSortieAm)
            });
        });
        setGrilleRows(rows => rows.map(row => importByDate.get(row.datePointage) || row));
        setImportSignals(prev => ({ ...prev, ...signalsByDate }));
        setExtraImportIndemnites(old => [...old, ...allAllowances]);
        setImportPreview(null);
        notify({ title: 'Import chargé', message: `${importByDate.size} journée(s) chargée(s), ${Object.keys(signalsByDate).length} signalement(s) à régulariser.`, type: 'success' });
        } finally {
            importApplyLock.current = false;
            setImportApplying(false);
        }
    }, [importPreview, importSelections, grilleRows, notify]);

    const handleStartImportRegularisation = useCallback(() => {
        const pending = (importPreview?.lines || []).filter(line =>
            line.decision === 'SIGNAL' && !line.errors?.length);
        if (!pending.length) return;
        setImportPreview(current => current ? { ...current, phase: 'REGULARIZE', lines: pending } : null);
        setImportSelections(Object.fromEntries(pending.map(line => [line.ligne, true])));
    }, [importPreview]);

    const handleSaveImport = useCallback(async (approvedConflicts = {}) => {
        if (importApplyLock.current || !canEditPeriod) return false;
        const selected = (importPreview?.lines || []).filter(line => importSelections[line.ligne]
            && line.decision === 'IMPORT' && !line.eventCode);
        if (!selected.length) return false;
        if (selected.some(line => line.eventCode || importLineNeedsReview(line, approvedConflicts))) {
            notify({ title: 'Import à vérifier', message: 'Régularisez les événements, corrigez les journées et confirmez chaque remplacement avant d’enregistrer.', type: 'error' });
            return false;
        }
        const current = new Map(grilleRows.map(row => [row.datePointage, row]));
        if (selected.some(line => !line.proposed || !current.get(line.date) || current.get(line.date).canCorrect === false)) {
            notify({ title: 'Import à vérifier', message: 'Une journée sélectionnée n’est pas modifiable.', type: 'error' });
            return false;
        }
        importApplyLock.current = true;
        setImportApplying(true);
        try {
            const payload = selected.map(line => toPointageBatchPayload({ ...current.get(line.date), ...line.proposed,
                datePointage: line.date, idPointage: current.get(line.date)?.idPointage,
                version: current.get(line.date)?.version,
                dynamicIndemnites: line.proposed.dynamicIndemnites || {} }));
            const verification = await attendanceService.daily.previewBatch(payload);
            if (!Array.isArray(verification) || verification.length !== payload.length) {
                throw new Error('Le contrôle du lot est incomplet. Aucun pointage n’a été enregistré.');
            }
            const invalid = verification?.find(result => result.error || !result.preview || result.preview.hasValidationError);
            if (invalid) {
                notify({ title: 'Import à vérifier', message: invalid.error || invalid.preview?.validationMessages?.[0] || 'Une journée est invalide.', type: 'error' });
                return false;
            }
            const response = await attendanceService.daily.saveBatch(payload);
            if (response?.success === false) throw new Error(response.message || 'Enregistrement refusé.');
            const pendingSignals = (importPreview?.lines || []).filter(line =>
                line.decision === 'SIGNAL' && !line.errors?.length);
            setImportPreview(current => pendingSignals.length && current
                ? { ...current, phase: 'REGULARIZE', savedCount: payload.length, lines: pendingSignals } : null);
            setImportSelections(Object.fromEntries(pendingSignals.map(line => [line.ligne, true])));
            await loadFicheEmploye({ keepImportPreview: pendingSignals.length > 0 });
            notify({ title: 'Import enregistré', message: pendingSignals.length
                ? `${payload.length} journée(s) enregistrée(s). Traitez maintenant les ${pendingSignals.length} signalement(s).`
                : `${payload.length} journée(s) enregistrée(s).`, type: 'success' });
            return true;
        } catch (error) {
            notify({ title: 'Erreur d’import', message: error?.response?.data?.message || error.message || 'Impossible d’enregistrer l’import.', type: 'error' });
            return false;
        } finally {
            importApplyLock.current = false;
            setImportApplying(false);
        }
    }, [canEditPeriod, importPreview, importSelections, grilleRows, loadFicheEmploye, notify]);

    // ── KPIs ─────────────────────────────────────────────────────────────────
    const kpis = useMemo(() => {
        const joursOuvres = grilleRows.filter(r => !r.weekend && !r.ferie);
        return {
            totalPrevus: joursOuvres.length,
            totalFeries: grilleRows.filter(r => r.ferie).length,
            totalRenseignes: joursOuvres.filter(r => r.totalHeuresDecimal > 0 || r.conge || r.heureEntreeMatin).length,
            totalRetardMins: grilleRows.reduce((acc, r) => acc + (r.minutesRetard || 0), 0),
            totalHeures: grilleRows.reduce((acc, r) => acc + (Number(r.totalHeuresDecimal) || 0), 0)
        };
    }, [grilleRows]);

    return {
        ficheData, siteParams, siteIndemnites, displayedIndemnites,
        grilleRows, initialRows, modifiedRows, dirtyCount,
        loading, saving, error, isAutonome, canEditPeriod, kpis,
        importPreview, importSelections, importSignals, importLoading, importPreviewLoading, importApplying,
        setImportSelections, setImportPreview,
        updateDayHours, toggleDayContinu, updateDayStatut, updateRowDynamicIndemnite,
        handleApplyStandardHours, handleDeducePauses, handleResetDay,
        handleApplyDayStandard, handleApplyWeekStandard,
        handleSaveBatch, handleSaveRegularisation,
        handleDownloadImportTemplate, handleImportFile, handleApplyImport, handleSaveImport,
        handleStartImportRegularisation,
        loadFicheEmploye
    };
}

export default usePointagePeriodeEmploye;
