import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';
import { isAllowanceGrantedByDefault, resolveAttendanceStatus } from '@/features/attendance/utils/attendanceStatus';
import { getTodayDateString } from '@/utils/dateUtils';

// ─── Helpers purs ─────────────────────────────────────────────────────────────

const cleanTime = (t) => {
    if (!t || t === '—' || t === '-' || t === '--:--' || t === 'null' || t === 'undefined') return '';
    return String(t).trim();
};

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
export const isDayRowModified = (current, initial) => {
    if (!current || !initial) return false;
    const normalize = (t) => cleanTime(t);
    const timeFields = ['heureEntreeMatin', 'heureSortieMatin', 'heureEntreeAm', 'heureSortieAm'];
    if (timeFields.some(f => normalize(current[f]) !== normalize(initial[f]))) return true;
    if (Boolean(current.horaireContinu) !== Boolean(initial.horaireContinu)) return true;
    if ((current.codeStatut ?? '') !== (initial.codeStatut ?? '')) return true;
    if ((current.motifAjustement ?? '') !== (initial.motifAjustement ?? '')) return true;
    return JSON.stringify(current.dynamicIndemnites || {}) !== JSON.stringify(initial.dynamicIndemnites || {});
};

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
    const [derogationsMap, setDerogationsMap] = useState({});
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
    const previewTimers = useRef(new Map());
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
    const loadFicheEmploye = useCallback(async () => {
        if (!selectedEmployeId || !selectedPeriodeId) {
            setFicheData(null); setGrilleRows([]); setInitialRows([]); setImportPreview(null);
            setLoading(false); return;
        }
        const reqId = ++loadReqId.current;
        setLoading(true); setError(null);
        try {
            const fiche = await attendanceService.getFicheEmploye(selectedEmployeId, { idPeriode: selectedPeriodeId });
            const selectedPeriod = periodesList.find(p => Number(p.idPeriodePaie) === Number(selectedPeriodeId));
            const referenceDate = selectedPeriod?.dateDebut || fiche?.dateDebut || getTodayDateString();

            const firstDatedSite = fiche?.pointagesData?.find(day => Number(day.idSite) > 0);
            const referenceSiteId = Number(selectedSiteId) > 0 ? Number(selectedSiteId)
                : Number(currentEmpSummary?.idSite) > 0 ? Number(currentEmpSummary.idSite)
                : Number(firstDatedSite?.idSite) > 0 ? Number(firstDatedSite.idSite) : null;
            const siteReferenceDay = fiche?.pointagesData?.find(day => Number(day.idSite) === referenceSiteId);
            const siteGrid = referenceSiteId ? await attendanceService.getGrilleSaisie({
                idSite: referenceSiteId,
                date: siteReferenceDay?.dateComplete || referenceDate
            }) : null;

            if (reqId !== loadReqId.current) return;

            setFicheData(fiche);
            const sParams = siteGrid?.siteParams || null;
            setSiteParams(sParams);
            const sIndemnites = siteGrid?.siteIndemnites || [];
            setSiteIndemnites(sIndemnites);
            setExtraImportIndemnites([]);
            setImportPreview(null);

            const empInSiteGrid = siteGrid?.grilleData?.find(e => e.idEmploye === selectedEmployeId);
            const dMap = {};
            if (empInSiteGrid?.dynamicIndemnites) {
                Object.entries(empInSiteGrid.dynamicIndemnites).forEach(([k, v]) => {
                    if (v?.motifSuggestion) dMap[k] = v.motifSuggestion;
                });
            }
            if (empInSiteGrid?.estDerogeDeplacement) {
                const motifDep = empInSiteGrid.motifDerogationDeplacement || 'Véhicule de fonction';
                dMap['DEPLACEMENT'] = motifDep; dMap['DPLCT'] = motifDep; dMap['TRANSPORT'] = motifDep;
            }
            if (empInSiteGrid?.estDerogeRepas) { dMap['REPAS'] = empInSiteGrid.motifDerogationRepas || 'Exclu repas'; }
            setDerogationsMap(dMap);

            const empAutonome = Boolean(
                fiche?.estAutonome || fiche?.dispensePointage ||
                currentEmpSummary?.estAutonome || currentEmpSummary?.dispensePointage
            );

            const rows = (fiche?.pointagesData || []).map((ptg) => {
                const isWeekend = Boolean(ptg.weekend || ptg.jour?.startsWith('Sam') || ptg.jour?.startsWith('Dim')
                    || (ptg.dateComplete && [0, 6].includes(new Date(`${ptg.dateComplete}T00:00:00`).getDay())));
                const isFerie = Boolean(ptg.isFerieChome || ptg.isFerie || ['FERIE_CHOME','FERIE','FERIE_TRAVAILLE'].includes(ptg.statut));
                const isConge = Boolean(ptg.isConge || ptg.statut === 'CONGE' || ptg.statut === 'CONGE_PAYE');

                const rawEntree = cleanTime(ptg.entree);
                const rawSortieMatin = cleanTime(ptg.sortiePause);
                const rawEntreeAm = cleanTime(ptg.retourPause);
                const rawSortieAm = cleanTime(ptg.sortie);
                const hasSavedHours = Boolean(rawEntree || rawSortieAm);
                const isBlankOuvre = !isWeekend && !isFerie && !isConge && !hasSavedHours;

                const defaultEntreeMatin = isBlankOuvre && empAutonome ? (sParams?.heureDebutMatin || '08:00') : rawEntree;
                const defaultSortieMatin = isBlankOuvre && empAutonome ? (sParams?.heureFinMatin || '12:00') : rawSortieMatin;
                const defaultEntreeAm    = isBlankOuvre && empAutonome ? (sParams?.heureDebutAm || '13:00') : rawEntreeAm;
                const defaultSortieAm    = isBlankOuvre && empAutonome ? (sParams?.heureFinAm || '17:00') : rawSortieAm;

                const codeStatut = resolveAttendanceStatus({
                    status: ptg.statut, hasHours: hasSavedHours, weekend: isWeekend,
                    holiday: isFerie, leave: isConge, autonomous: empAutonome
                });

                const dynamicIndemnites = {};
                sIndemnites.forEach(ind => {
                    const key = String(ind.idIndemnite);
                    const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
                    const derogMotif = dMap[key] || dMap[ind.code];
                    const isExclu = Boolean(derogMotif);
                    const saved = ptg.indemnites?.[ind.code] || ptg.indemnites?.[key];
                    if (saved && typeof saved === 'object') {
                        const acc = Boolean(saved.accordee ?? saved.accorde);
                        const qte = Number(saved.quantite ?? (acc ? 1 : 0));
                        dynamicIndemnites[key] = {
                            accordee: acc, fractionJour: qte,
                            matin: saved.matin ?? (fractionnable ? (acc && Boolean(ptg.deplacementMatin ?? true)) : undefined),
                            am: saved.am ?? (fractionnable ? (acc && Boolean(ptg.deplacementAm ?? true)) : undefined),
                            motifSuggestion: derogMotif, montantApplique: saved.montant || ind.montantJour
                        };
                    } else {
                        const shouldAccord = isAllowanceGrantedByDefault(ind, {
                            weekend: isWeekend, holiday: isFerie, leave: isConge, excluded: isExclu
                        });
                        dynamicIndemnites[key] = {
                            accordee: shouldAccord, fractionJour: shouldAccord ? 1 : 0,
                            matin: fractionnable ? shouldAccord : undefined,
                            am: fractionnable ? shouldAccord : undefined,
                            motifSuggestion: derogMotif, montantApplique: ind.montantJour
                        };
                    }
                });

                let totalHeuresDec = 0.0, heuresFaitesStr = '00h00';
                if (!((isFerie || isWeekend) && !hasSavedHours)) {
                    if (isBlankOuvre && empAutonome) { totalHeuresDec = 8.0; heuresFaitesStr = '08h00'; }
                    else if (ptg.totalHeuresDecimal != null && !isNaN(ptg.totalHeuresDecimal)) {
                        totalHeuresDec = Number(ptg.totalHeuresDecimal);
                        heuresFaitesStr = ptg.heures && ptg.heures !== '—' && ptg.heures !== '-' ? ptg.heures : formatHeuresMinutes(totalHeuresDec);
                    } else if (ptg.heures && ptg.heures !== '—' && ptg.heures !== '-') {
                        heuresFaitesStr = ptg.heures;
                        totalHeuresDec = parseHhMmToDecimal(ptg.heures);
                    }
                }

                const repasInd = sIndemnites.find(i => i.code === 'REPAS');
                const depInd = sIndemnites.find(i => ['DEPLACEMENT', 'DPLCT', 'TRANSPORT'].includes(i.code));

                return {
                    idPointage: ptg.idPointage || null, version: ptg.version ?? null,
                    idEmploye: selectedEmployeId,
                    idSite: ptg.idSite || selectedSiteId || fiche?.idSite || 1,
                    datePointage: ptg.dateComplete, date: ptg.date, jour: ptg.jour,
                    weekend: isWeekend, ferie: isFerie, conge: isConge,
                    congeApprouve: Boolean(ptg.congeApprouve || (isConge && ptg.statut === 'CONGE' && !hasSavedHours && ptg.congeValide)),
                    canCorrect: ptg.canCorrect !== false,
                    heureEntreeMatin: defaultEntreeMatin, heureSortieMatin: defaultSortieMatin,
                    heureEntreeAm: defaultEntreeAm, heureSortieAm: defaultSortieAm,
                    horaireContinu: Boolean(ptg.horaireContinuJour),
                    codeStatut, motifAjustement: '',
                    heuresFaites: heuresFaitesStr, totalHeuresDecimal: totalHeuresDec,
                    minutesRetard: empAutonome ? 0 : (ptg.minutesRetard || 0),
                    indemniteRepas: repasInd ? Boolean(dynamicIndemnites[String(repasInd.idIndemnite)]?.accordee)
                        : (ptg.indemniteRepas ?? (isBlankOuvre && empAutonome)),
                    deplacementMatin: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.matin)
                        : (ptg.deplacementMatin ?? (isBlankOuvre && empAutonome)),
                    deplacementAm: depInd ? Boolean(dynamicIndemnites[String(depInd.idIndemnite)]?.am)
                        : (ptg.deplacementAm ?? (isBlankOuvre && empAutonome)),
                    dynamicIndemnites, estAutonome: empAutonome,
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

    // ── Preview batch (aligné sur usePointageSaisie) ─────────────────────────
    useEffect(() => {
        const pendingRows = grilleRows.filter(row => row._previewPending);
        if (!pendingRows.length) return undefined;
        const timers = previewTimers.current;
        timers.forEach(clearTimeout); timers.clear();

        const timer = setTimeout(async () => {
            try {
                const results = await attendanceService.daily.previewBatch(pendingRows);
                const byDate = new Map();
                (results || []).forEach((result, index) => {
                    const date = result?.preview?.datePointage || pendingRows[index]?.datePointage;
                    if (date) byDate.set(date, result);
                });
                setGrilleRows(current => current.map(item => {
                    if (!item._previewPending) return item;
                    const result = byDate.get(item.datePointage);
                    if (!result || result.error || !result.preview) {
                        const message = result?.error || 'Calcul de la journée indisponible. Vérifiez le site et les horaires.';
                        return {
                            ...item,
                            _previewPending: false,
                            _previewError: message,
                            hasValidationError: true,
                            validationMessages: [message]
                        };
                    }
                    const preview = result.preview;
                    return {
                        ...item,
                        heuresFaites: preview.totalHeures != null ? formatHeuresMinutes(preview.totalHeures) : item.heuresFaites,
                        totalHeuresDecimal: preview.totalHeures != null ? preview.totalHeures : item.totalHeuresDecimal,
                        minutesRetard: item.estAutonome ? 0 : (preview.minutesRetard || 0),
                        codeStatut: preview.codeStatut || item.codeStatut,
                        libelleStatut: preview.libelleStatut,
                        _previewError: null,
                        hasValidationError: preview.hasValidationError,
                        validationMessages: preview.validationMessages || [],
                        statusActions: preview.statusActions || [],
                        present: preview.present, retard: preview.retard, conge: preview.conge,
                        absenceJustifiee: preview.absenceJustifiee, absenceNonJustifiee: preview.absenceNonJustifiee,
                        nonSaisi: preview.nonSaisi, anomalie: preview.anomalie,
                        dispensePointage: preview.dispensePointage, ferieTravaille: preview.ferieTravaille,
                        ferieChome: preview.ferieChome, samediTravaille: preview.samediTravaille,
                        dimancheTravaille: preview.dimancheTravaille, repos: preview.repos,
                        _previewPending: false
                    };
                }));
            } catch {
                setGrilleRows(current => current.map(item =>
                    item._previewPending ? { ...item, _previewPending: false } : item
                ));
            } finally { timers.clear(); }
        }, 300);

        timers.set('batch', timer);
        return () => { timers.forEach(clearTimeout); timers.clear(); };
    }, [grilleRows]);

    // ── Lignes modifiées ─────────────────────────────────────────────────────
    const modifiedRows = useMemo(() => {
        if (!initialRows || initialRows.length === 0) return [];
        return grilleRows.filter(row => {
            const initial = initialRows.find(i => i.datePointage === row.datePointage);
            return isDayRowModified(row, initial);
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
    const syncRowIndemnites = useCallback((row, hours, indList, dMap) => {
        const hasMatin = Boolean(hours.heureEntreeMatin || hours.heureSortieMatin);
        const hasAm = Boolean(hours.heureEntreeAm || hours.heureSortieAm);
        const hasHours = hasMatin || hasAm;
        const nextDyn = {};

        indList.forEach(ind => {
            const key = String(ind.idIndemnite);
            const fractionnable = ind.estFractionnable !== undefined ? ind.estFractionnable : ind.fractionnable;
            const derogMotif = dMap[key] || dMap[ind.code];
            const isDerogeExclu = Boolean(derogMotif);
            let matVal = false, amVal = false, accordeeVal = false;
            if (isAllowanceGrantedByDefault(ind, {
                weekend: row.weekend, holiday: row.ferie, leave: row.conge, excluded: isDerogeExclu
            })) {
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
                motifSuggestion: derogMotif, montantApplique: ind.montantJour
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
            const hasHours = Object.values(updatedHours).some(Boolean);
            const indList = row._dateAllowances || siteIndemnites;
            const syncedIndemnites = syncRowIndemnites(row, updatedHours, indList, derogationsMap);
            return {
                ...row, ...updatedHours,
                codeStatut: hasHours
                    ? (row.codeStatut === 'NON_SAISI' ? 'PRESENT' : row.codeStatut)
                    : (row.weekend ? 'REPOS' : row.ferie ? 'FERIE_CHOME' : row.conge ? 'CONGE' : 'NON_SAISI'),
                ...syncedIndemnites, _previewPending: true
            };
        }));
    }, [siteIndemnites, derogationsMap, syncRowIndemnites]);

    const toggleDayContinu = useCallback((datePointage) => {
        setGrilleRows(prev => prev.map(row => row.datePointage !== datePointage ? row
            : { ...row, horaireContinu: !row.horaireContinu, _previewPending: true }));
    }, []);

    const updateDayStatut = useCallback((datePointage, codeStatut, motif = '') => {
        setGrilleRows(prev => prev.map(row => row.datePointage !== datePointage ? row
            : { ...row, codeStatut, motifAjustement: motif, _previewPending: true }));
    }, []);

    const updateRowDynamicIndemnite = useCallback((datePointage, idIndemnite, value, moment) => {
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage) return row;
            const key = String(idIndemnite);
            const existingDyn = { ...(row.dynamicIndemnites || {}) };
            const previous = existingDyn[key] || {};
            const allowance = (row._dateAllowances || siteIndemnites).find(i => String(i.idIndemnite) === key);
            const fractionnable = allowance?.estFractionnable !== undefined ? allowance.estFractionnable : allowance?.fractionnable;
            const defaultGranted = isAllowanceGrantedByDefault(allowance, {
                weekend: row.weekend, holiday: row.ferie, leave: row.conge
            });
            const current = {
                accordee: Boolean(previous.accordee ?? defaultGranted),
                matin: previous.matin ?? (fractionnable ? defaultGranted : undefined),
                am: previous.am ?? (fractionnable ? defaultGranted : undefined),
                ...previous
            };
            const boolVal = Boolean(value);
            if (moment === 'MATIN')           current.matin = boolVal;
            else if (moment === 'APRES_MIDI') current.am = boolVal;
            else                              current.accordee = boolVal;
            if (moment === 'MATIN' || moment === 'APRES_MIDI') {
                current.accordee = Boolean(current.matin || current.am);
                current.fractionJour = (current.matin ? 0.5 : 0) + (current.am ? 0.5 : 0);
            } else { current.fractionJour = boolVal ? 1 : 0; }
            existingDyn[key] = current;
            const ind = allowance;
            return {
                ...row, dynamicIndemnites: existingDyn,
                ...(ind?.code === 'REPAS' ? { indemniteRepas: current.accordee } : {}),
                ...(['DEPLACEMENT','DPLCT','TRANSPORT'].includes(ind?.code)
                    ? { deplacementMatin: Boolean(current.matin), deplacementAm: Boolean(current.am) } : {}),
                _previewPending: true
            };
        }));
    }, [siteIndemnites]);

    // ── Assistants ───────────────────────────────────────────────────────────
    const handleApplyStandardHours = useCallback(() => {
        const hDebM = siteParams?.heureDebutMatin || '08:00', hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00',   hFinA = siteParams?.heureFinAm || '17:00';
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            const isBlank = !row.heureEntreeMatin && !row.heureSortieMatin && !row.heureEntreeAm && !row.heureSortieAm;
            if (!isBlank) return row;
            count++;
            const stdHours = { heureEntreeMatin: hDebM, heureSortieMatin: hFinM, heureEntreeAm: hDebA, heureSortieAm: hFinA };
            return { ...row, ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : 'PRESENT',
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap),
                _previewPending: true };
        }));
        notify(count > 0
            ? { title: 'Horaire standard appliqué', message: `${count} journée(s) mise(s) à jour.`, type: 'success' }
            : { title: 'Information', message: 'Toutes les journées ouvrées sont déjà renseignées.', type: 'info' });
    }, [siteParams, siteIndemnites, derogationsMap, syncRowIndemnites, notify]);

    const handleDeducePauses = useCallback(() => {
        const hFinM = siteParams?.heureFinMatin || '12:00', hDebA = siteParams?.heureDebutAm || '13:00';
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!row.canCorrect || row.weekend || row.ferie || row.conge) return row;
            if (row.heureEntreeMatin && row.heureSortieAm && (!row.heureSortieMatin || !row.heureEntreeAm) && !row.horaireContinu) {
                count++;
                return { ...row, heureSortieMatin: hFinM, heureEntreeAm: hDebA, _previewPending: true };
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
                totalHeuresDecimal: 0, heuresFaites: '00h00', minutesRetard: 0,
                ...syncRowIndemnites(row, emptyHours, row._dateAllowances || siteIndemnites, derogationsMap),
                _previewPending: true };
        }));
    }, [siteIndemnites, derogationsMap, syncRowIndemnites]);

    const handleApplyDayStandard = useCallback((datePointage) => {
        const hDebM = siteParams?.heureDebutMatin || '08:00', hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00',   hFinA = siteParams?.heureFinAm || '17:00';
        setGrilleRows(prev => prev.map(row => {
            if (row.datePointage !== datePointage || !row.canCorrect) return row;
            const stdHours = { heureEntreeMatin: hDebM, heureSortieMatin: hFinM, heureEntreeAm: hDebA, heureSortieAm: hFinA };
            return { ...row, ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : (row.ferie ? 'FERIE_TRAVAILLE' : row.weekend ? 'SAMEDI_TRAVAILLE' : 'PRESENT'),
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap),
                _previewPending: true };
        }));
    }, [siteParams, siteIndemnites, derogationsMap, syncRowIndemnites]);

    const handleApplyWeekStandard = useCallback((weekRows) => {
        const hDebM = siteParams?.heureDebutMatin || '08:00', hFinM = siteParams?.heureFinMatin || '12:00';
        const hDebA = siteParams?.heureDebutAm || '13:00',   hFinA = siteParams?.heureFinAm || '17:00';
        const weekDates = new Set(weekRows.filter(r => r.canCorrect && !r.weekend && !r.ferie && !r.conge).map(r => r.datePointage));
        if (weekDates.size === 0) return;
        let count = 0;
        setGrilleRows(prev => prev.map(row => {
            if (!weekDates.has(row.datePointage) || !row.canCorrect) return row;
            count++;
            const stdHours = { heureEntreeMatin: hDebM, heureSortieMatin: hFinM, heureEntreeAm: hDebA, heureSortieAm: hFinA };
            return { ...row, ...stdHours,
                codeStatut: row.estAutonome ? 'DISPENSE_POINTAGE' : 'PRESENT',
                ...syncRowIndemnites(row, stdHours, row._dateAllowances || siteIndemnites, derogationsMap),
                _previewPending: true };
        }));
        if (count > 0) notify({ title: 'Semaine complétée', message: `${count} jour(s) mis à jour.`, type: 'success' });
    }, [siteParams, siteIndemnites, derogationsMap, syncRowIndemnites, notify]);

    // ── Sauvegarde ───────────────────────────────────────────────────────────
    const handleSaveBatch = useCallback(async () => {
        if (!canEditPeriod) return false;
        if (modifiedRows.length === 0) {
            notify({ title: 'Information', message: 'Aucune modification à enregistrer.', type: 'info' });
            return false;
        }
        setSaving(true);
        try {
            const payload = modifiedRows.map(row => ({
                idPointage: row.idPointage, version: row.version,
                idEmploye: row.idEmploye, idSite: row.idSite, datePointage: row.datePointage,
                heureEntreeMatin: row.heureEntreeMatin || null, heureSortieMatin: row.heureSortieMatin || null,
                heureEntreeAm: row.heureEntreeAm || null, heureSortieAm: row.heureSortieAm || null,
                horaireContinu: Boolean(row.horaireContinu), horaireContinuJour: Boolean(row.horaireContinu),
                codeStatut: row.codeStatut || 'PRESENT', motifAjustement: row.motifAjustement || null,
                estAutonome: Boolean(row.estAutonome), dynamicIndemnites: row.dynamicIndemnites || {}
            }));
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
                await loadFicheEmploye();
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
    }, [canEditPeriod, selectedEmployeId, selectedSiteId, selectedPeriodeId, loadFicheEmploye, notify]);

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

    const handleApplyImport = useCallback(async () => {
        if (importApplyLock.current) return;
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
                    halfDayMoment: line.proposed.heureEntreeMatin && line.proposed.heureSortieMatin
                        && !line.proposed.heureEntreeAm && !line.proposed.heureSortieAm ? 'APRES_MIDI'
                        : !line.proposed.heureEntreeMatin && !line.proposed.heureSortieMatin
                            && line.proposed.heureEntreeAm && line.proposed.heureSortieAm ? 'MATIN' : null
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
        ficheData, siteParams, siteIndemnites, displayedIndemnites, derogationsMap,
        grilleRows, initialRows, modifiedRows, dirtyCount,
        loading, saving, error, isAutonome, canEditPeriod, kpis,
        importPreview, importSelections, importSignals, importLoading, importPreviewLoading, importApplying,
        setImportSelections, setImportPreview,
        updateDayHours, toggleDayContinu, updateDayStatut, updateRowDynamicIndemnite,
        handleApplyStandardHours, handleDeducePauses, handleResetDay,
        handleApplyDayStandard, handleApplyWeekStandard,
        handleSaveBatch, handleSaveRegularisation,
        handleDownloadImportTemplate, handleImportFile, handleApplyImport,
        loadFicheEmploye
    };
}

export default usePointagePeriodeEmploye;
