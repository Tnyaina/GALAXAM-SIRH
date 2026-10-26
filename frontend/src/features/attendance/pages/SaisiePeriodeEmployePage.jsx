import { useState, useEffect, useMemo, useRef, useCallback, lazy, Suspense } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Users, User, Save } from 'lucide-react';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import PeriodeNavigator from '@/components/ui/navigation/PeriodeNavigator';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { pointageSyntheseService, periodePaieService } from '@/features/attendance/api/attendanceApi';
import organisationService from '@/features/organization/api/organisationService';
import { useToast } from '@/components/ui/feedback/Toast';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';

import usePointagePeriodeEmploye, { isDayRowModified } from '@/features/attendance/hooks/usePointagePeriodeEmploye';
import PeriodeEmployeeEmptyState from '@/features/attendance/components/views/PeriodeEmployeeEmptyState';
import PeriodeEmployeeHeader from '@/features/attendance/components/views/PeriodeEmployeeHeader';
import PeriodeEmployeeTable from '@/features/attendance/components/views/PeriodeEmployeeTable';
import PeriodeEmployeeImportPreview from '@/features/attendance/components/views/PeriodeEmployeeImportPreview';
import { importHalfDayMoment } from '@/features/attendance/utils/pointageImport';
import PeriodeRecapModal from '@/features/attendance/components/modals/PeriodeRecapModal';
import { isAttendanceUnentered, isAttendanceUnjustifiedAbsence } from '@/features/attendance/utils/attendanceStatus';
import { isPointageException, isPointageAnomaly } from '@/features/attendance/utils/pointageDayFilter';

const AnomalieRegularisationModal = lazy(() => import('@/features/attendance/components/modals/AnomalieRegularisationModal'));

export default function SaisiePeriodeEmployePage({
    activePeriode: propActivePeriode = null,
    periodesDisponibles: propPeriodesList = null,
    sitesDisponibles: propSitesList = null
}) {
    const toast = useToast();
    const [searchParams, setSearchParams] = useSearchParams();

    // ── 1. Période & Sites ────────────────────────────────────────────────────
    const [periodesList, setPeriodesList] = useState(() => propPeriodesList || []);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(() => {
        const paramP = searchParams.get('periode');
        return paramP ? Number(paramP) : (propActivePeriode?.idPeriodePaie || null);
    });
    const [sitesList, setSitesList] = useState(() => propSitesList || []);
    const [sitesLoadError, setSitesLoadError] = useState(null);
    const [selectedSiteId, setSelectedSiteId] = useState(() => {
        const paramS = searchParams.get('site');
        return paramS ? Number(paramS) : null;
    });

    // ── 2. Collaborateurs & Recherche ─────────────────────────────────────────
    const [employesList, setEmployesList] = useState([]);
    const [selectedEmployeId, setSelectedEmployeId] = useState(() => {
        const paramE = searchParams.get('employe') || searchParams.get('idEmploye');
        return paramE ? Number(paramE) : null;
    });

    const [employeeSearchFilter, setEmployeeSearchFilter] = useState('');
    const [employeeSearchQuery, setEmployeeSearchQuery] = useState('');
    const [employeeListLoading, setEmployeeListLoading] = useState(false);
    const [employeeListError, setEmployeeListError] = useState(null);
    const [employeeListPage, setEmployeeListPage] = useState(1);
    const [employeeListTotalPages, setEmployeeListTotalPages] = useState(1);
    const [employeeListTotal, setEmployeeListTotal] = useState(0);
    const [employeeListReload, setEmployeeListReload] = useState(0);

    const employeeListReqId = useRef(0);
    const importFileRef = useRef(null);

    // Filtre des journées dans la grille.
    const [filterJour, setFilterJour] = useState('ALL');

    // Ligne en cours de régularisation (modale d'anomalie)
    const [regularisationRow, setRegularisationRow] = useState(null);

    // Modale de récapitulatif avant enregistrement batch
    const [showRecapModal, setShowRecapModal] = useState(false);

    // Navigation en attente de confirmation si données modifiées
    const [pendingNav, setPendingNav] = useState(null);
    const [showUnsavedConfirm, setShowUnsavedConfirm] = useState(false);

    // Employé actuellement sélectionné dans la liste
    const currentEmpIndex = useMemo(() => {
        if (!selectedEmployeId) return -1;
        return employesList.findIndex(e => e.idEmploye === selectedEmployeId);
    }, [employesList, selectedEmployeId]);

    const currentEmpSummary = useMemo(() => {
        if (currentEmpIndex >= 0) return employesList[currentEmpIndex];
        return null;
    }, [employesList, currentEmpIndex]);

    // ── 3. Hook métier centralisé ─────────────────────────────────────────────
    const {
        ficheData,
        siteParams,
        siteIndemnites,
        displayedIndemnites,
        grilleRows,
        initialRows,
        modifiedRows,
        dirtyCount,
        loading,
        saving,
        error,
        isAutonome,
        canEditPeriod,
        kpis,
        importPreview,
        importSelections,
        importSignals,
        importLoading,
        importPreviewLoading,
        importApplying,
        setImportSelections,
        setImportPreview,
        updateDayHours,
        toggleDayContinu,
        updateDayStatut,
        updateRowDynamicIndemnite,
        handleApplyStandardHours,
        handleDeducePauses,
        handleResetDay,
        handleApplyDayStandard,
        handleApplyWeekStandard,
        handleSaveBatch,
        handleSaveRegularisation,
        handleDownloadImportTemplate,
        handleImportFile,
        handleApplyImport,
        handleSaveImport,
        handleStartImportRegularisation,
        loadFicheEmploye
    } = usePointagePeriodeEmploye({
        selectedEmployeId,
        selectedPeriodeId,
        selectedSiteId,
        currentEmpSummary,
        periodesList,
        toast
    });

    // Protection globale contre la fermeture d'onglet / rafraîchissement
    useUnsavedChanges(dirtyCount > 0, `${dirtyCount} journée(s) modifiée(s) ne sont pas encore enregistrées.`);

    // ── 4. Initialisation des périodes & sites ─────────────────────────────────
    useEffect(() => {
        if (!propPeriodesList || propPeriodesList.length === 0) {
            periodePaieService.getPeriodOptions().then(data => {
                if (data && Array.isArray(data) && data.length > 0) {
                    setPeriodesList(data);
                    if (!selectedPeriodeId) {
                        const active = data.find(p => p.statutCode === 'EN_COURS') || data[0];
                        setSelectedPeriodeId(active.idPeriodePaie);
                    }
                }
            }).catch(console.error);
        } else {
            setPeriodesList(propPeriodesList);
        }
    }, [propPeriodesList, selectedPeriodeId]);

    useEffect(() => {
        if (!propSitesList || propSitesList.length === 0) {
            organisationService.getSites().then(data => {
                const list = (data || []).filter(s => s.estActif !== false && s.actif !== false);
                setSitesList(list);
                setSitesLoadError(null);
            }).catch(err => {
                console.error('Erreur chargement des sites:', err);
                setSitesLoadError('Impossible de charger les sites. Le filtre de site peut être incomplet.');
            });
        } else {
            const list = propSitesList.filter(s => s.estActif !== false && s.actif !== false);
            setSitesList(list);
            setSitesLoadError(null);
        }
    }, [propSitesList]);

    // Debounce recherche collaborateur
    useEffect(() => {
        const timer = setTimeout(() => setEmployeeSearchQuery(employeeSearchFilter.trim()), 300);
        return () => clearTimeout(timer);
    }, [employeeSearchFilter]);

    // Chargement de la liste des collaborateurs pour la période / site
    useEffect(() => {
        if (!selectedPeriodeId) return;
        if (employeeSearchQuery && employeeSearchQuery.length < 2) {
            setEmployesList([]);
            setEmployeeListTotal(0);
            setEmployeeListTotalPages(1);
            setEmployeeListPage(1);
            setEmployeeListLoading(false);
            setEmployeeListError(null);
            return;
        }

        let cancelled = false;
        const requestId = ++employeeListReqId.current;
        setEmployeeListLoading(true);
        setEmployeeListError(null);
        setEmployesList([]);
        setEmployeeListPage(1);

        pointageSyntheseService.getSynthese({
            idPeriodePaie: selectedPeriodeId,
            idSite: selectedSiteId || undefined,
            search: employeeSearchQuery || undefined,
            page: 1,
            size: 100
        }).then(res => {
            if (cancelled || requestId !== employeeListReqId.current) return;
            const items = res?.synthese || [];
            setEmployesList(items);
            setEmployeeListTotal(Number(res?.totalElements) || 0);
            setEmployeeListTotalPages(Math.max(1, Number(res?.totalPages) || 1));
        }).catch(err => {
            if (!cancelled && requestId === employeeListReqId.current) {
                console.error('Erreur chargement collaborateurs:', err);
                setEmployeeListError(err?.response?.data?.message || 'Impossible de charger la liste des collaborateurs.');
            }
        }).finally(() => {
            if (!cancelled && requestId === employeeListReqId.current) setEmployeeListLoading(false);
        });

        return () => { cancelled = true; };
    }, [selectedPeriodeId, selectedSiteId, employeeSearchQuery, employeeListReload]);

    const loadMoreEmployees = async () => {
        if (employeeListLoading || employeeListPage >= employeeListTotalPages) return;
        const requestId = employeeListReqId.current;
        const nextPage = employeeListPage + 1;
        setEmployeeListLoading(true);
        setEmployeeListError(null);
        try {
            const res = await pointageSyntheseService.getSynthese({
                idPeriodePaie: selectedPeriodeId,
                idSite: selectedSiteId || undefined,
                search: employeeSearchQuery || undefined,
                page: nextPage,
                size: 100
            });
            if (requestId !== employeeListReqId.current) return;
            const items = res?.synthese || [];
            setEmployesList(current => {
                const knownIds = new Set(current.map(e => e.idEmploye));
                return [...current, ...items.filter(e => !knownIds.has(e.idEmploye))];
            });
            setEmployeeListPage(nextPage);
        } catch (err) {
            if (requestId === employeeListReqId.current) {
                setEmployeeListError(err?.response?.data?.message || 'Erreur lors du chargement des collaborateurs suivants.');
            }
        } finally {
            if (requestId === employeeListReqId.current) setEmployeeListLoading(false);
        }
    };

    // ── 5. Navigation sécurisée avec ConfirmDialog ────────────────────────────
    const applyNav = useCallback((nav) => {
        if (!nav) return;
        if (nav.type === 'employe') {
            setSelectedEmployeId(nav.value);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                if (nav.value) next.set('employe', String(nav.value));
                else next.delete('employe');
                return next;
            });
        } else if (nav.type === 'periode') {
            setSelectedPeriodeId(nav.value);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('periode', String(nav.value));
                return next;
            });
        } else if (nav.type === 'site') {
            setSelectedSiteId(nav.value);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                if (nav.value) next.set('site', String(nav.value));
                else next.delete('site');
                return next;
            });
        } else if (nav.type === 'tab') {
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.set('tab', 'saisie');
                next.delete('mode');
                return next;
            });
        }
    }, [setSearchParams]);

    const requestNav = useCallback((nav) => {
        if (dirtyCount > 0) {
            setPendingNav(nav);
            setShowUnsavedConfirm(true);
        } else {
            applyNav(nav);
        }
    }, [dirtyCount, applyNav]);

    const handleNavigateEmploye = useCallback((direction) => {
        if (currentEmpIndex < 0) return;
        const newIndex = currentEmpIndex + direction;
        if (newIndex >= 0 && newIndex < employesList.length) {
            requestNav({ type: 'employe', value: employesList[newIndex].idEmploye });
        }
    }, [currentEmpIndex, employesList, requestNav]);

    // ── 6. Raccourcis clavier (Ctrl+S, Alt+Flèches) ───────────────────────────
    useEffect(() => {
        const handleGlobalKeyDown = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                if (dirtyCount > 0 && !saving && canEditPeriod) {
                    setShowRecapModal(true);
                }
            } else if (e.altKey && e.key === 'ArrowLeft') {
                e.preventDefault();
                handleNavigateEmploye(-1);
            } else if (e.altKey && e.key === 'ArrowRight') {
                e.preventDefault();
                handleNavigateEmploye(1);
            }
        };
        window.addEventListener('keydown', handleGlobalKeyDown);
        return () => window.removeEventListener('keydown', handleGlobalKeyDown);
    }, [dirtyCount, saving, canEditPeriod, handleNavigateEmploye]);

    // ── 7. Filtrage & Groupement par semaine ──────────────────────────────────
    const isMissingDay = useCallback(r => !r.weekend && !r.ferie && !r.conge && isAttendanceUnentered(r), []);
    const isAnomalyDay = useCallback(r => isPointageAnomaly(r) || isAttendanceUnjustifiedAbsence(r) || r.hasValidationError, []);
    const isExceptionDay = useCallback(r => r.weekend || r.ferie || r.conge || isPointageException(r), []);
    const isModifiedDay = useCallback(r => isDayRowModified(r, initialRows.find(initial => initial.datePointage === r.datePointage)), [initialRows]);

    const filterCounts = useMemo(() => ({
        ALL: grilleRows.length,
        MISSING: grilleRows.filter(isMissingDay).length,
        ANOMALY: grilleRows.filter(isAnomalyDay).length,
        EXCEPTION: grilleRows.filter(isExceptionDay).length,
        MODIFIED: grilleRows.filter(isModifiedDay).length
    }), [grilleRows, isMissingDay, isAnomalyDay, isExceptionDay, isModifiedDay]);

    const filteredRows = useMemo(() => grilleRows.filter(r => {
        if (filterJour === 'MISSING') return isMissingDay(r);
        if (filterJour === 'ANOMALY') return isAnomalyDay(r);
        if (filterJour === 'EXCEPTION') return isExceptionDay(r);
        if (filterJour === 'MODIFIED') return isModifiedDay(r);
        return true;
    }), [grilleRows, filterJour, isMissingDay, isAnomalyDay, isExceptionDay, isModifiedDay]);

    const groupedByWeeks = useMemo(() => {
        const weeks = [];
        let currentWeek = [];
        let currentWeekNum = null;

        grilleRows.forEach((row) => {
            const dateObj = new Date(`${row.datePointage}T00:00:00`);
            const dayOfWeek = dateObj.getDay() || 7;
            const monday = new Date(dateObj);
            monday.setDate(dateObj.getDate() - (dayOfWeek - 1));
            const weekKey = monday.toISOString().split('T')[0];

            if (currentWeekNum !== weekKey) {
                if (currentWeek.length > 0) {
                    weeks.push({ weekKey: currentWeekNum, rows: currentWeek });
                }
                currentWeek = [row];
                currentWeekNum = weekKey;
            } else {
                currentWeek.push(row);
            }
        });

        if (currentWeek.length > 0) {
            weeks.push({ weekKey: currentWeekNum, rows: currentWeek });
        }

        return weeks.map((w, index) => {
            const joursOuvresSemaine = w.rows.filter(r => !r.weekend && !r.ferie);
            const objectifHeuresSemaine = joursOuvresSemaine.length * 8.0;
            const totalFeriesSemaine = w.rows.filter(r => r.ferie).length;

            return {
                ...w,
                weekNumber: index + 1,
                summaryRows: w.rows,
                rows: w.rows.filter(row => filteredRows.includes(row)),
                objectifHeuresSemaine,
                totalFeriesSemaine
            };
        }).filter(week => week.rows.length > 0);
    }, [grilleRows, filteredRows]);

    // ── 8. Demande d'enregistrement avec récapitulatif ────────────────────────
    const handleRequestSave = () => {
        if (!canEditPeriod) return;
        if (dirtyCount === 0) {
            toast({ title: 'Information', message: 'Aucune modification à enregistrer.', type: 'info' });
            return;
        }
        setShowRecapModal(true);
    };

    const handleConfirmSave = async () => {
        const success = await handleSaveBatch();
        if (success) {
            setShowRecapModal(false);
        }
    };

    return (
        <div className="space-y-4 font-sans pb-16">
            {/* 1. Barre supérieure : Mode de Saisie, Période & Site */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs">
                <div className="attendance-mode-controls flex items-center gap-3 flex-wrap">
                    {/* Bascule Saisie collective vs Saisie par collaborateur */}
                    <div className="attendance-mode-switch inline-flex border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-0.5 gap-0.5">
                        <Button
                            variant="ghost"
                            size="sm"
                            wrapLabel
                            iconLeft={<Users size={13} />}
                            onClick={() => requestNav({ type: 'tab', value: 'saisie' })}
                            className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]"
                        >
                            Saisie collective (par date)
                        </Button>
                        <Button
                            variant="primary"
                            size="sm"
                            wrapLabel
                            iconLeft={<User size={13} />}
                            className="font-bold shadow-xs"
                        >
                            Saisie par collaborateur (période)
                        </Button>
                    </div>

                    <span className="h-5 w-px bg-[var(--color-border-subtle)] hidden sm:block" />

                    {/* Sélecteur de Période */}
                    <PeriodeNavigator
                        periodesList={periodesList}
                        selectedPeriodeId={selectedPeriodeId}
                        onSelectPeriode={(pId) => requestNav({ type: 'periode', value: pId })}
                        className="attendance-period-navigator"
                    />
                </div>

                {/* Filtre de Site */}
                <div className="flex items-center gap-2">
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] shrink-0">Site :</span>
                    <SearchSelect
                        options={sitesList.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || ''} — ${s.libelle}` }))}
                        value={selectedSiteId ? String(selectedSiteId) : ''}
                        onChange={(val) => requestNav({ type: 'site', value: val ? Number(val) : null })}
                        aria-label="Filtrer par site"
                        placeholder="Tous les sites"
                        size="sm"
                        className="min-w-[190px] text-xs"
                    />
                </div>
            </div>

            {sitesLoadError && <Alert type="error" title="Sites indisponibles" message={sitesLoadError} />}

            {/* CAS 1 : AUCUN COLLABORATEUR SÉLECTIONNÉ */}
            {!selectedEmployeId ? (
                <PeriodeEmployeeEmptyState
                    employeeSearchFilter={employeeSearchFilter}
                    setEmployeeSearchFilter={setEmployeeSearchFilter}
                    employeeSearchQuery={employeeSearchQuery}
                    employeeListLoading={employeeListLoading}
                    employeeListError={employeeListError}
                    employesList={employesList}
                    employeeListPage={employeeListPage}
                    employeeListTotalPages={employeeListTotalPages}
                    employeeListTotal={employeeListTotal}
                    loadMoreEmployees={loadMoreEmployees}
                    onSelectEmploye={(id) => requestNav({ type: 'employe', value: id })}
                    setEmployeeListReload={setEmployeeListReload}
                />
            ) : (
                /* CAS 2 : COLLABORATEUR SÉLECTIONNÉ */
                <>
                    {/* En-tête collaborateur, KPIs & Outils */}
                    <PeriodeEmployeeHeader
                        ficheData={ficheData}
                        isAutonome={isAutonome}
                        canEditPeriod={canEditPeriod}
                        dirtyCount={dirtyCount}
                        saving={saving}
                        employesList={employesList}
                        currentEmpIndex={currentEmpIndex}
                        selectedEmployeId={selectedEmployeId}
                        kpis={kpis}
                        filterJour={filterJour}
                        setFilterJour={setFilterJour}
                        grilleRows={grilleRows}
                        filterCounts={filterCounts}
                        importLoading={importLoading}
                        onNavigateEmploye={handleNavigateEmploye}
                        onSelectEmploye={(id) => requestNav({ type: 'employe', value: id })}
                        onBackToList={() => requestNav({ type: 'employe', value: null })}
                        onDownloadTemplate={handleDownloadImportTemplate}
                        onImportClick={() => importFileRef.current?.click()}
                        onApplyStandard={handleApplyStandardHours}
                        onDeducePauses={handleDeducePauses}
                        onSaveBatch={handleRequestSave}
                    />

                    <input
                        ref={importFileRef}
                        type="file"
                        accept=".xlsx"
                        className="hidden"
                        onChange={(e) => {
                            const file = e.target.files?.[0];
                            if (file) handleImportFile(file, dirtyCount);
                            e.target.value = '';
                        }}
                    />

                    {/* Signalements d'importation à régulariser */}
                    {Object.keys(importSignals).length > 0 && (
                        <section className="border border-[var(--color-warning-600)] bg-[var(--color-surface)] p-3 space-y-2" aria-label="Signalements importés à régulariser">
                            <p className="text-xs font-bold text-[var(--color-warning-700)]">Signalements importés à régulariser</p>
                            <div className="flex flex-wrap gap-2">
                                {Object.entries(importSignals).map(([date, signal]) => {
                                    const row = grilleRows.find(item => item.datePointage === date);
                                    return (
                                        <Button
                                            key={date}
                                            variant="outline"
                                            size="xs"
                                            disabled={!row?.canCorrect || !canEditPeriod || dirtyCount > 0}
                                            onClick={() => row && setRegularisationRow(row)}
                                            className="border-[var(--color-warning-600)] text-[var(--color-fg)] hover:bg-[var(--color-warning-50)]"
                                        >
                                            {date} · {signal.code === 'C' ? 'Congé signalé' : 'Absence signalée'} · Régulariser
                                        </Button>
                                    );
                                })}
                            </div>
                            {dirtyCount > 0 && (
                                <p className="text-xs text-[var(--color-fg-muted)]">Enregistrez les heures et indemnités importées avant de régulariser.</p>
                            )}
                        </section>
                    )}

                    {/* Aperçu de l'import Excel */}
                    {(importPreview || importPreviewLoading) && (
                        <PeriodeEmployeeImportPreview
                            importPreview={importPreview}
                            importPreviewLoading={importPreviewLoading}
                            importApplying={importApplying}
                            importSelections={importSelections}
                            setImportSelections={setImportSelections}
                            setImportPreview={setImportPreview}
                            grilleRows={grilleRows}
                            onClose={() => setImportPreview(null)}
                            onApply={handleApplyImport}
                            onSave={handleSaveImport}
                            onStartRegularisation={handleStartImportRegularisation}
                            onRegularize={(line) => {
                                const row = grilleRows.find(item => item.datePointage === line.date);
                                if (!row || row.canCorrect === false) return;
                                setRegularisationRow({ ...row, importEventCode: line.eventCode,
                                    importAllowanceOverrides: line.allowanceOverrides || {},
                                    importHalfDayMoment: importHalfDayMoment(line.proposed) });
                            }}
                        />
                    )}

                    {/* Tableau journalier par semaine */}
                    {loading ? (
                        <SkeletonTable rows={10} columns={10} />
                    ) : error ? (
                        <Alert type="error" message={error} />
                    ) : filteredRows.length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 text-center text-xs text-[var(--color-fg-muted)]">
                            Aucune journée ne correspond au filtre sélectionné pour cette période.
                        </div>
                    ) : (
                        <PeriodeEmployeeTable
                            key={selectedEmployeId}
                            groupedByWeeks={groupedByWeeks}
                            filterJour={filterJour}
                            canEditPeriod={canEditPeriod}
                            isAutonome={isAutonome}
                            siteParams={siteParams}
                            displayedIndemnites={displayedIndemnites}
                            initialRows={initialRows}
                            isDayRowModified={isDayRowModified}
                            toggleDayContinu={toggleDayContinu}
                            handleApplyDayStandard={handleApplyDayStandard}
                            handleApplyWeekStandard={handleApplyWeekStandard}
                            updateDayHours={updateDayHours}
                            updateDayStatut={updateDayStatut}
                            updateRowDynamicIndemnite={updateRowDynamicIndemnite}
                            handleResetDay={handleResetDay}
                            openRegularisation={(r) => setRegularisationRow(r)}
                        />
                    )}
                </>
            )}

            {/* Modale de régularisation par congé ou absence */}
            {regularisationRow && (
                <Suspense fallback={null}>
                    <AnomalieRegularisationModal
                        isOpen={Boolean(regularisationRow)}
                        directDayFlow
                        onClose={() => setRegularisationRow(null)}
                        anomalie={{
                            idPointage: regularisationRow.idPointage || 0,
                            idEmploye: selectedEmployeId,
                            idSite: regularisationRow.idSite || selectedSiteId || 1,
                            idPeriodePaie: selectedPeriodeId,
                            datePointage: regularisationRow.datePointage,
                            nom: ficheData?.nom || currentEmpSummary?.nom || '',
                            prenom: ficheData?.prenom || currentEmpSummary?.prenom || '',
                            matricule: ficheData?.matricule || currentEmpSummary?.matricule || '',
                            photoUrl: ficheData?.photoUrl || currentEmpSummary?.photoUrl,
                            statutCode: regularisationRow.codeStatut || 'NON_SAISI',
                            statutLibelle: regularisationRow.codeStatut,
                            typeAnomalie: regularisationRow.minutesRetard > 0 ? 'RETARD'
                                : isAttendanceUnjustifiedAbsence(regularisationRow) ? 'ABSENCE' : undefined,
                            minutesRetard: regularisationRow.minutesRetard || 0,
                            regularisationIntent: regularisationRow.regularisationIntent,
                            heureEntreeMatin: regularisationRow.heureEntreeMatin,
                            heureSortieMatin: regularisationRow.heureSortieMatin,
                            heureEntreeAm: regularisationRow.heureEntreeAm,
                            heureSortieAm: regularisationRow.heureSortieAm,
                            importEventCode: regularisationRow.importEventCode,
                            importHalfDayMoment: regularisationRow.importHalfDayMoment,
                            importAllowanceOverrides: regularisationRow.importAllowanceOverrides,
                            version: regularisationRow.version || 1
                        }}
                        onSuccess={(payload) => handleSaveRegularisation(regularisationRow, payload)}
                    />
                </Suspense>
            )}

            {/* Modale de récapitulatif avant confirmation batch */}
            <PeriodeRecapModal
                isOpen={showRecapModal}
                onClose={() => setShowRecapModal(false)}
                onConfirm={handleConfirmSave}
                saving={saving}
                modifiedRows={modifiedRows}
                employeNom={ficheData?.nomComplet || `${ficheData?.nom || ''} ${ficheData?.prenom || ''}`}
                siteIndemnites={siteIndemnites}
            />

            {/* Confirmation en cas de navigation avec modifications non enregistrées */}
            <ConfirmDialog
                isOpen={showUnsavedConfirm}
                onClose={() => {
                    setShowUnsavedConfirm(false);
                    setPendingNav(null);
                }}
                onConfirm={() => {
                    setShowUnsavedConfirm(false);
                    if (pendingNav) applyNav(pendingNav);
                    setPendingNav(null);
                }}
                title="Modifications non enregistrées"
                message={`Vous avez ${dirtyCount} journée(s) modifiée(s) en attente. Si vous changez sans enregistrer, vos modifications seront définitivement perdues.`}
                confirmLabel="Changer sans enregistrer"
                cancelLabel="Rester et enregistrer"
                variant="warning"
            />

            {/* Barre flottante inférieure de sauvegarde rapide */}
            {dirtyCount > 0 && (
                <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 bg-[var(--color-surface)] border border-[var(--color-border-strong)] shadow-2xl px-4 py-2.5 flex items-center gap-4 animate-in fade-in slide-in-from-bottom-3 backdrop-blur-md">
                    <div className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg)]">
                        <span className="w-2 h-2 rounded-full bg-[var(--color-warning-500)] animate-pulse" />
                        <span>{dirtyCount} modification{dirtyCount > 1 ? 's' : ''} non enregistrée{dirtyCount > 1 ? 's' : ''}</span>
                    </div>

                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={loadFicheEmploye}
                        disabled={saving}
                    >
                        Annuler
                    </Button>

                    <Button
                        variant="primary"
                        size="sm"
                        iconLeft={<Save size={13} />}
                        onClick={handleRequestSave}
                        disabled={saving || !canEditPeriod}
                        loading={saving}
                    >
                        Enregistrer les modifications (Ctrl+S)
                    </Button>
                </div>
            )}
        </div>
    );
}
