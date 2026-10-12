import { useState, useRef, useMemo, useEffect, useCallback, lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Sparkles, Users, User } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/features/organization/api/organisationService';
import { getTodayDateString, isDateInFuture } from '@/utils/dateUtils';

import PointageToolbar from '@/features/attendance/components/controls/PointageToolbar';
import PointageGridTable from '@/features/attendance/components/views/PointageGridTable';
import usePointageSaisie from '@/features/attendance/hooks/usePointageSaisie';
import useDebounce from '@/hooks/useDebounce';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

const PointageRecapModal = lazy(() => import('@/features/attendance/components/modals/PointageRecapModal'));

export default function SaisieQuotidiennePage({ activePeriode = null, _onSelectPeriode = null, sitesDisponibles = null }) {
    const { toast } = useToast();
    const { hasPermission } = useAuth();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [sites, setSites] = useState(() => sitesDisponibles || []);
    const canWrite = hasPermission('POINTAGE_WRITE');
    const canValidate = hasPermission('POINTAGE_VALIDATE');
    const canReadConge = hasPermission('CONGE_READ');

    const todayStr = getTodayDateString();
    const paramDate = searchParams.get('date');
    const paramSite = searchParams.get('site');

    // Déterminer la date par défaut en cohérence avec la période active
    const defaultDate = useMemo(() => {
        if (paramDate) return paramDate;
        if (activePeriode?.dateDebut && activePeriode?.dateFin) {
            if (todayStr >= activePeriode.dateDebut && todayStr <= activePeriode.dateFin) {
                return todayStr;
            }
            if (todayStr > activePeriode.dateFin) {
                return activePeriode.dateFin;
            }
            return activePeriode.dateDebut;
        }
        return todayStr;
    }, [paramDate, activePeriode?.dateDebut, activePeriode?.dateFin, todayStr]);

    const parsedParamSite = paramSite ? Number(paramSite) : null;
    const listedSite = sitesDisponibles?.find(s => Number(s.idSite) === parsedParamSite && s.estActif !== false && s.actif !== false);
    const initialSite = listedSite?.idSite
        || sitesDisponibles?.find(s => s.estActif !== false && s.actif !== false)?.idSite
        || sitesDisponibles?.[0]?.idSite
        || (parsedParamSite && parsedParamSite > 0 ? parsedParamSite : 1);
    const validSites = useMemo(() => {
        const active = sites.filter(s => s.estActif !== false && s.actif !== false);
        return active.length > 0 ? active : sites;
    }, [sites]);

    useEffect(() => {
        if (sitesDisponibles) {
            setSites(sitesDisponibles);
            return;
        }
        const fetchSites = async () => {
            try {
                const res = await organisationService.getSites();
                setSites(res || []);
            } catch (err) {
                console.error('Erreur chargement sites:', err);
            }
        };
        fetchSites();
    }, [sitesDisponibles]);

    const {
        selectedSite,
        setSelectedSite,
        selectedDate,
        setSelectedDate,
        grilleData,
        initialGrilleData,
        siteParams,
        siteIndemnites,
        periodeInfo,
        statsJour: backendStatsJour,
        loading,
        hasCurrentData,
        saving,
        error,
        isRowModified,
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
    } = usePointageSaisie(initialSite, defaultDate);

    const [searchTerm, setSearchTerm] = useState('');
    const debouncedSearch = useDebounce(searchTerm, 350);
    const [statutFilter, setStatutFilter] = useState('ALL');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [modifiedEmpIds, setModifiedEmpIds] = useState(new Set());
    const dirtyCount = modifiedEmpIds.size;

    // Protection globale contre la fermeture d'onglet / rafraîchissement si pointages non enregistrés
    useUnsavedChanges(dirtyCount > 0, `${dirtyCount} pointage(s) modifié(s) ne sont pas encore enregistrés.`);

    // Ligne en cours de régularisation par congé ou absence

    // Navigation en attente de confirmation si données modifiées
    const [pendingNav, setPendingNav] = useState(null); // { type: 'site' | 'date', value: ... }
    const [showUnsavedNavConfirm, setShowUnsavedNavConfirm] = useState(false);

    // Recalcul temps réel du nombre exact de collaborateurs modifiés
    useEffect(() => {
        if (!initialGrilleData || initialGrilleData.length === 0) {
            setModifiedEmpIds(new Set());
            return;
        }
        const dirtySet = new Set();
        (grilleData || []).forEach((row) => {
            if (isRowModified(row, initialGrilleData)) {
                dirtySet.add(row.idEmploye);
            }
        });
        setModifiedEmpIds(dirtySet);
    }, [grilleData, initialGrilleData, isRowModified]);

    // Synchronisation automatique si le site initial n'est pas encore défini ou invalide
    useEffect(() => {
        if (validSites.length > 0) {
            const isCurrentValid = validSites.some(s => Number(s.idSite) === Number(selectedSite));
            if (!isCurrentValid) {
                const defaultSite = validSites[0];
                if (defaultSite?.idSite) {
                    setSelectedSite(defaultSite.idSite);
                    setSearchParams(prev => {
                        const next = new URLSearchParams(prev);
                        next.set('site', String(defaultSite.idSite));
                        return next;
                    }, { replace: true });
                }
            }
        }
    }, [validSites, selectedSite, setSelectedSite, setSearchParams]);

    // Application effective des changements de filtre
    const applySiteChange = useCallback((sid) => {
        setSelectedSite(sid);
        setCurrentPage(1);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('site', String(sid));
            return next;
        });
    }, [setSelectedSite, setSearchParams]);

    const applyDateChange = useCallback((dateStr) => {
        setSelectedDate(dateStr);
        setCurrentPage(1);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('date', dateStr);
            return next;
        });
    }, [setSelectedDate, setSearchParams]);

    // Changement de site ou de date avec protection anti-perte de saisie
    const handleSiteChange = (siteId) => {
        const sid = Number(siteId);
        if (sid <= 0 || sid === selectedSite) return;
        if (dirtyCount > 0) {
            setPendingNav({ type: 'site', value: sid });
            setShowUnsavedNavConfirm(true);
            return;
        }
        applySiteChange(sid);
    };

    const handleDateChange = useCallback((dateStr) => {
        if (!dateStr || dateStr === selectedDate) return;
        if (dirtyCount > 0) {
            setPendingNav({ type: 'date', value: dateStr });
            setShowUnsavedNavConfirm(true);
            return;
        }
        applyDateChange(dateStr);
    }, [applyDateChange, dirtyCount, selectedDate]);

    // Raccourci de navigation journalière : Cmd sur macOS, Ctrl ailleurs.
    // Ne capte pas les flèches quand l'utilisateur édite un champ ou navigue dans la grille.
    useEffect(() => {
        const handleGlobalDateShortcut = (event) => {
            if (!(event.metaKey || event.ctrlKey) || event.altKey || event.shiftKey) return;
            if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
            const target = event.target;
            if (target instanceof HTMLElement && target.closest(
                'input, textarea, select, button, [contenteditable="true"], [role="textbox"]'
            )) return;
            if (!selectedDate) return;
            event.preventDefault();

            const [year, month, day] = selectedDate.split('-').map(Number);
            const offset = event.key === 'ArrowLeft' ? -1 : 1;
            const nextDay = new Date(year, month - 1, day + offset);
            const nextDate = `${nextDay.getFullYear()}-${String(nextDay.getMonth() + 1).padStart(2, '0')}-${String(nextDay.getDate()).padStart(2, '0')}`;
            if (isDateInFuture(nextDate)) return;

            handleDateChange(nextDate);
        };

        window.addEventListener('keydown', handleGlobalDateShortcut);
        return () => window.removeEventListener('keydown', handleGlobalDateShortcut);
    }, [handleDateChange, selectedDate]);

    // Modales
    const [isRecapModalOpen, setIsRecapModalOpen] = useState(false);

    // Actions
    const handleUpdateHours = (idEmploye, field, value) => {
        updateRowHours(idEmploye, field, value);
    };

    const handleToggleHoraireContinu = (idEmploye) => {
        toggleRowHoraireContinu(idEmploye);
    };

    const handleUpdateStatut = (idEmploye, newStatut, motif) => {
        updateRowStatut(idEmploye, newStatut, motif);
    };

    const handleUpdateDynamicIndemnite = (idEmploye, idIndemnite, checked, moment) => {
        updateRowDynamicIndemnite(idEmploye, idIndemnite, checked, moment);
    };

    const handleAutoFill = () => {
        autoFillStandardHours();
    };

    const handleAutoPauses = () => {
        autoDeduceAllPauses();
    };

    // Refs pour la navigation clavier rapide dans la grille
    const inputRefs = useRef({});

    const registerInputRef = (idEmploye, fieldIndex, el) => {
        if (!inputRefs.current[idEmploye]) {
            inputRefs.current[idEmploye] = [];
        }
        inputRefs.current[idEmploye][fieldIndex] = el;
    };

    const focusNext = (idEmploye, fieldIndex) => {
        for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
            const nextEl = inputRefs.current[idEmploye]?.[nextIndex];
            if (nextEl && !nextEl.disabled) {
                nextEl.focus();
                return;
            }
        }
        const keys = Object.keys(inputRefs.current);
        const currIdx = keys.indexOf(String(idEmploye));
        if (currIdx !== -1 && currIdx < keys.length - 1) {
            const nextEmpId = keys[currIdx + 1];
            for (let f = 0; f <= 3; f++) {
                const targetEl = inputRefs.current[nextEmpId]?.[f];
                if (targetEl && !targetEl.disabled) {
                    targetEl.focus();
                    break;
                }
            }
        }
    };

    const handleKeyDown = (e, idEmploye, fieldIndex) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            handleRequestSave();
        } else if (e.key === 'ArrowRight') {
            for (let nextIndex = fieldIndex + 1; nextIndex <= 3; nextIndex++) {
                const nextEl = inputRefs.current[idEmploye]?.[nextIndex];
                if (nextEl && !nextEl.disabled) {
                    nextEl.focus();
                    break;
                }
            }
        } else if (e.key === 'ArrowLeft') {
            for (let prevIndex = fieldIndex - 1; prevIndex >= 0; prevIndex--) {
                const prevEl = inputRefs.current[idEmploye]?.[prevIndex];
                if (prevEl && !prevEl.disabled) {
                    prevEl.focus();
                    break;
                }
            }
        } else if (e.key === 'ArrowDown') {
            const keys = Object.keys(inputRefs.current);
            const currIdx = keys.indexOf(String(idEmploye));
            if (currIdx !== -1 && currIdx < keys.length - 1) {
                const nextEmpId = keys[currIdx + 1];
                inputRefs.current[nextEmpId]?.[fieldIndex]?.focus();
            }
        } else if (e.key === 'ArrowUp') {
            const keys = Object.keys(inputRefs.current);
            const currIdx = keys.indexOf(String(idEmploye));
            if (currIdx > 0) {
                const prevEmpId = keys[currIdx - 1];
                inputRefs.current[prevEmpId]?.[fieldIndex]?.focus();
            }
        }
    };

    // Comptages calculés à partir des statuts métier fournis par le backend.
    const statusCounts = useMemo(() => {
        const list = Array.isArray(grilleData) ? grilleData : [];
        const all = list.length;
        const presents = list.filter(r => r.present || r.dispensePointage || r.ferieTravaille || r.samediTravaille || r.dimancheTravaille).length;
        const conges = list.filter(r => r.conge || r.absenceJustifiee).length;
        const nonSaisis = list.filter(r => r.nonSaisi).length;
        const retards = list.filter(r => r.retard).length;

        return { ALL: all, PRESENT: presents, CONGE: conges, NON_SAISI: nonSaisis, RETARD: retards };
    }, [grilleData]);

    // Filtrage dynamique des données (recherche texte + filtre de statut)
    const filteredData = useMemo(() => {
        let list = Array.isArray(grilleData) ? grilleData : [];

        if (statutFilter === 'PRESENT') {
            list = list.filter(r => r.present || r.dispensePointage || r.ferieTravaille || r.samediTravaille || r.dimancheTravaille);
        } else if (statutFilter === 'CONGE') {
            list = list.filter(r => r.conge || r.absenceJustifiee);
        } else if (statutFilter === 'NON_SAISI') {
            list = list.filter(r => r.nonSaisi);
        } else if (statutFilter === 'RETARD') {
            list = list.filter(r => r.retard);
        }

        if (!debouncedSearch.trim()) return list;
        const term = debouncedSearch.toLowerCase().trim();
        return list.filter(row =>
            (row.nom && row.nom.toLowerCase().includes(term)) ||
            (row.prenom && row.prenom.toLowerCase().includes(term)) ||
            (row.matricule && row.matricule.toLowerCase().includes(term))
        );
    }, [grilleData, statutFilter, debouncedSearch]);

    const isFuture = isDateInFuture(selectedDate);

    const isReadOnly = useMemo(
        () => !activePeriode || !canWrite || activePeriode?.availableActions?.EDIT_POINTAGE?.allowed !== true,
        [canWrite, activePeriode]
    );

    const currentSiteObj = sites.find(s => s.idSite === selectedSite);
    const siteLabel = currentSiteObj
        ? `${currentSiteObj.codeSite || currentSiteObj.code || 'Site'} — ${currentSiteObj.libelle}`
        : (sites.length > 0 ? `${sites[0].codeSite || sites[0].code || 'Site'} — ${sites[0].libelle}` : 'Site sélectionné');

    // Les KPI de journée sont fournis par le backend ; en cas de saisie en cours, ils restent explicitement indisponibles.
    const statsJour = useMemo(() => {
        if (backendStatsJour) {
            return {
                ...backendStatsJour,
                isFerie: grilleData[0]?.ferieJour || false,
                libelleFerie: grilleData[0]?.libelleFerie || null,
                siteLabel,
                previewPending: grilleData.some((row) => row._previewPending)
            };
        }
        return { siteLabel, previewPending: grilleData.some((row) => row._previewPending) };
    }, [backendStatsJour, grilleData, siteLabel]);

    // Ouvre la modale de récapitulatif & validation
    const handleRequestSave = useCallback(() => {
        if (isReadOnly || saving || loading) return;
        setIsRecapModalOpen(true);
    }, [isReadOnly, saving, loading]);

    // Confirmation finale d'enregistrement via le récapitulatif
    const handleConfirmSave = async () => {
        if (saving || grilleData.some(row => row._previewPending || row._previewError)) return;
        const ok = await saveAll();
        if (ok) {
            setModifiedEmpIds(new Set());
            setIsRecapModalOpen(false);
            toast({
                title: 'Pointages enregistrés avec succès',
                message: 'Les horaires et indemnités ont été calculés et sauvegardés.',
                type: 'success'
            });
        } else {
            toast({
                title: 'Erreur d\'enregistrement',
                message: 'Une erreur est survenue lors de l\'enregistrement.',
                type: 'error'
            });
        }
    };

    // Raccourci clavier Ctrl+S / Cmd+S
    useEffect(() => {
        const handleKeyDownGlobal = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                handleRequestSave();
            }
        };
        window.addEventListener('keydown', handleKeyDownGlobal);
        return () => window.removeEventListener('keydown', handleKeyDownGlobal);
    }, [handleRequestSave]);

    return (
        <>
            <SEO
                title="Pointage & Présence Quotidienne | GALAXAM GRH"
                description="Saisie opérationnelle, suivi des retards, heures de nuit et indemnités journalières."
            />

            <div className="space-y-2.5 w-full pb-6">

                {/* Sélecteur de Mode : Saisie collective (par date) vs Saisie par collaborateur (période) */}
                <div className="flex items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-2">
                    <div className="inline-flex border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-0.5">
                        <button
                            type="button"
                            className="px-3 py-1.5 text-xs font-bold bg-[var(--color-brand-600)] text-[var(--color-on-color)] shadow-xs flex items-center gap-1.5 cursor-pointer"
                        >
                            <Users size={13} />
                            <span>Saisie collective (par date)</span>
                        </button>
                        <button
                            type="button"
                            onClick={() => {
                                setSearchParams(prev => {
                                    const next = new URLSearchParams(prev);
                                    next.set('tab', 'saisie');
                                    next.set('mode', 'employe');
                                    return next;
                                });
                            }}
                            className="px-3 py-1.5 text-xs font-medium text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] transition-colors cursor-pointer flex items-center gap-1.5"
                        >
                            <User size={13} />
                            <span>Saisie par collaborateur (période)</span>
                        </button>
                    </div>
                    <span className="text-xs text-[var(--color-fg-muted)] hidden sm:inline">
                        Mode de saisie opérationnelle
                    </span>
                </div>

                {/* 1. Barre de Commande Unifiée */}
                <PointageToolbar
                    sites={sites}
                    selectedSite={selectedSite}
                    onSiteChange={handleSiteChange}
                    selectedDate={selectedDate}
                    onDateChange={handleDateChange}
                    searchTerm={searchTerm}
                    onSearchChange={(t) => { setSearchTerm(t); setCurrentPage(1); }}
                    selectedStatutFilter={statutFilter}
                    onStatutFilterChange={(sf) => { setStatutFilter(sf); setCurrentPage(1); }}
                    statusCounts={statusCounts}
                    onAutoFillStandard={handleAutoFill}
                    onAutoDeducePauses={handleAutoPauses}
                    onAddConge={canReadConge ? () => navigate('/conges?tab=demandes') : null}
                    isReadOnly={isReadOnly}
                    onSave={handleRequestSave}
                    saving={saving}
                    dirtyCount={dirtyCount}
                />

                {/* 1.bis Alert : Consultation seule si pas de droit d'écriture */}
                {!canWrite && !loading && (
                    <Alert
                        type="info"
                        title="Mode consultation seule"
                    >
                        <span>Vous visualisez la grille de pointage en lecture seule. La saisie et l'enregistrement de présences nécessitent les droits d'écriture (permission POINTAGE_WRITE).</span>
                    </Alert>
                )}

                {/* 1.ter Alert : Date future */}
                {isFuture && (
                    <Alert
                        type="warning"
                        title={`Date future (${selectedDate})`}
                    >
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mt-1">
                            <span>Conformément aux règles d'intégrité temporelle du GRH, la saisie et la modification des pointages sont strictement désactivées pour les dates non échues.</span>
                            <Button
                                size="xs"
                                variant="outline"
                                onClick={() => handleDateChange(getTodayDateString())}
                                className="shrink-0"
                            >
                                Revenir à aujourd'hui ({getTodayDateString()})
                            </Button>
                        </div>
                    </Alert>
                )}

                {/* 2. Alert : Aucune période de paie ouverte */}
                {!periodeInfo && !loading && (
                    <Alert
                        type="warning"
                        title={`Aucune période de paie ouverte pour le ${selectedDate}`}
                    >
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mt-1">
                            <span>La saisie est verrouillée en lecture seule.</span>
                            {canValidate && (
                                <Button
                                    size="xs"
                                    variant="primary"
                                    onClick={() => navigate(`/paie?tab=periodes&action=create&targetDate=${selectedDate}`)}
                                    icon={<Sparkles size={13} className="text-[var(--color-warning-300)]" />}
                                    className="shrink-0"
                                >
                                    Créer la période ({(() => {
                                    if (!selectedDate) return 'Suivante';
                                    const parts = selectedDate.split('-');
                                    if (parts.length === 3) {
                                        const y = parseInt(parts[0], 10);
                                        const m = parseInt(parts[1], 10) - 1;
                                        const d = parseInt(parts[2], 10);
                                        const targetM = d >= 26 ? (m + 1) % 12 : m;
                                        const targetY = (d >= 26 && m === 11) ? y + 1 : y;
                                        const moisFr = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
                                        return `${moisFr[targetM]} ${targetY}`;
                                    }
                                    return 'Suivante';
                                })()})
                                </Button>
                            )}
                        </div>
                    </Alert>
                )}

                {statsJour.isFerie && (
                    <Alert variant="warning" className="p-3 sm:p-4">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 w-full">
                            <div>
                                <span className="font-bold text-[13px] text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)]">
                                    Jour férié chômé · {statsJour.libelleFerie || 'Férié officiel'}
                                </span>
                                <span className="text-[12px] opacity-90 block sm:inline sm:ml-2">
                                    sur le site {statsJour.siteLabel}.
                                </span>
                            </div>
                            <div className="flex items-center gap-1.5 text-[11.5px] font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-500)]/15 px-2.5 py-1 border border-[var(--color-warning-500)]/30 shrink-0">
                                <Sparkles size={13} className="text-[var(--color-warning-600)]" />
                                <span>Saisie ouverte : les heures saisies compteront en Férié travaillé (+100%)</span>
                            </div>
                        </div>
                    </Alert>
                )}

                {/* 5. Table de Saisie DOMINANTE (scroll interne) */}
                {loading && hasCurrentData && (
                    <p className="px-3 py-2 text-xs text-[var(--color-fg-muted)]" role="status">Actualisation de la grille…</p>
                )}
                {loading && !hasCurrentData ? (
                    <SkeletonTable rows={8} columns={10} />
                ) : error && !hasCurrentData ? (
                    <Alert type="error" title="Impossible de charger les pointages" message={error} />
                ) : (
                    <PointageGridTable
                        grilleData={filteredData}
                        siteParams={siteParams}
                        siteIndemnites={siteIndemnites}
                        selectedStatutFilter={statutFilter}
                        onStatutFilterChange={(sf) => { setStatutFilter(sf); setCurrentPage(1); }}
                        onUpdateHours={handleUpdateHours}
                        onToggleHoraireContinu={handleToggleHoraireContinu}
                        onUpdateStatut={handleUpdateStatut}
                        onUpdateDynamicIndemnite={handleUpdateDynamicIndemnite}
                        onBulkStandardHours={bulkApplyStandardHours}
                        onBulkStatut={bulkApplyStatut}
                        onBulkIndemnite={bulkApplyIndemnite}
                        onRegisterInputRef={registerInputRef}
                        onKeyDown={handleKeyDown}
                        onFocusNext={focusNext}
                        currentPage={currentPage}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                        onPageSizeChange={(sz) => { setPageSize(sz); setCurrentPage(1); }}
                        isReadOnly={isReadOnly || loading || Boolean(error)}
                    />
                )}
            </div>

            {/* Modale Récapitulative & Validation Avant Sauvegarde (Lazy Loaded) */}
            {isRecapModalOpen && (
                <Suspense fallback={null}>
                    <PointageRecapModal
                        isOpen={isRecapModalOpen}
                        onClose={() => setIsRecapModalOpen(false)}
                        onConfirm={handleConfirmSave}
                        saving={saving}
                        grilleData={grilleData}
                        modifiedEmpIds={modifiedEmpIds}
                        siteIndemnites={siteIndemnites}
                        selectedDate={selectedDate}
                        siteLabel={siteLabel}
                    />
                </Suspense>
            )}

            {/* Modale de Régularisation directe par Congé / Absence (Lazy Loaded) */}
            {/* Confirmation en cas de changement de filtre avec modifications en cours */}
            <ConfirmDialog
                isOpen={showUnsavedNavConfirm}
                onClose={() => {
                    setShowUnsavedNavConfirm(false);
                    setPendingNav(null);
                }}
                onConfirm={() => {
                    setShowUnsavedNavConfirm(false);
                    if (pendingNav?.type === 'site') {
                        applySiteChange(pendingNav.value);
                    } else if (pendingNav?.type === 'date') {
                        applyDateChange(pendingNav.value);
                    }
                    setPendingNav(null);
                }}
                title="Modifications non enregistrées"
                message={`Vous avez ${dirtyCount} pointage(s) modifié(s) en attente. Si vous changez de ${pendingNav?.type === 'site' ? 'site' : 'date'} sans enregistrer, vos modifications seront définitivement perdues.`}
                confirmLabel="Changer sans enregistrer"
                cancelLabel="Rester et enregistrer"
                variant="warning"
            />
        </>
    );
}
