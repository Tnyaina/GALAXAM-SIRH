import { useState, useRef, useMemo, useEffect, useCallback, lazy, Suspense } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { AlertCircle, Calendar, Clock, Sparkles } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/features/organization/api/organisationService';
import { getTodayDateString, isDateInFuture } from '@/utils/dateUtils';

import PointageToolbar from '@/features/attendance/components/controls/PointageToolbar';
import PointageGridTable from '@/features/attendance/components/views/PointageGridTable';
import usePointageSaisie from '@/features/attendance/hooks/usePointageSaisie';
import useDebounce from '@/hooks/useDebounce';

const PointageRecapModal = lazy(() => import('@/features/attendance/components/modals/PointageRecapModal'));

export default function SaisieQuotidiennePage({ activePeriode = null, _onSelectPeriode = null }) {
    const { toast } = useToast();
    const { hasPermission } = useAuth();
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const [sites, setSites] = useState([]);
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

    const initialSite = paramSite ? Number(paramSite) : 1;

    useEffect(() => {
        const fetchSites = async () => {
            try {
                const res = await organisationService.getSites();
                if (res) setSites(res);
            } catch (err) {
                console.error('Erreur chargement sites:', err);
            }
        };
        fetchSites();
    }, []);

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

    // Changement de site ou de date
    const handleSiteChange = (siteId) => {
        setSelectedSite(siteId);
        setCurrentPage(1);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('site', String(siteId));
            return next;
        });
    };

    const handleDateChange = (dateStr) => {
        setSelectedDate(dateStr);
        setCurrentPage(1);
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('date', dateStr);
            return next;
        });
    };

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
        if (e.key === 'ArrowRight') {
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
        () => activePeriode?.availableActions?.EDIT_POINTAGE?.allowed !== true,
        [activePeriode]
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
                title="Pointage & Présence Quotidienne | GALAXAM SIRH"
                description="Saisie opérationnelle, suivi des retards, heures de nuit et indemnités journalières."
            />

            <div className="space-y-2.5 w-full pb-6">

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

                {/* 1.bis Alert : Date future */}
                {isFuture && (
                    <div className="bg-[var(--color-warning-500)]/10 border-l-4 border-l-[var(--color-warning-500)] p-3 text-xs text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)] flex items-center justify-between gap-3 shadow-xs flex-wrap">
                        <div className="flex items-center gap-2.5">
                            <AlertCircle size={16} className="shrink-0 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]" />
                            <div>
                                <span className="font-bold">Date future ({selectedDate})</span>
                                <span className="ml-2 text-[11px] opacity-85">— Conformément aux règles d'intégrité temporelle du SIRH, la saisie et la modification des pointages sont strictement désactivées pour les dates non échues.</span>
                            </div>
                        </div>
                        <button
                            type="button"
                            onClick={() => handleDateChange(getTodayDateString())}
                            className="px-3 py-1.5 text-xs font-bold bg-[var(--color-warning-600)] hover:bg-[var(--color-warning-700)] text-white rounded-none cursor-pointer transition-colors shrink-0 shadow-xs"
                        >
                            Revenir à aujourd'hui ({getTodayDateString()})
                        </button>
                    </div>
                )}

                {/* 2. Alert : Aucune période de paie ouverte */}
                {!periodeInfo && !loading && (
                    <div className="bg-[var(--color-warning-500)]/10 border-l-4 border-l-[var(--color-warning-500)] p-3 text-xs text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)] flex items-center justify-between gap-3 shadow-xs flex-wrap">
                        <div className="flex items-center gap-2.5">
                            <AlertCircle size={16} className="shrink-0 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]" />
                            <div>
                                <span className="font-bold">Aucune période de paie ouverte pour le {selectedDate}</span>
                                <span className="ml-2 text-[11px] opacity-85">— La saisie est verrouillée en lecture seule.</span>
                            </div>
                        </div>
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
                )}

                {/* 4. Information locale à cette grille : pas de composant séparé à rechercher. */}
                {statsJour.isFerie && (
                    <div className="bg-[var(--color-warning-500)]/10 border-l-4 border-l-[var(--color-warning-500)] p-4 shadow-xs text-xs text-[var(--color-warning-700)] dark:text-[var(--color-warning-200)]">
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                            <div className="flex items-start sm:items-center gap-2.5">
                                <Calendar size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5 sm:mt-0" />
                                <div>
                                    <span className="font-bold text-[13px]">
                                        Jour férié chômé · {statsJour.libelleFerie || 'Férié officiel'}
                                    </span>
                                    <span className="text-[12px] opacity-90 block sm:inline sm:ml-2">
                                        sur le site {statsJour.siteLabel}.
                                    </span>
                                </div>
                            </div>
                            <div className="flex items-center gap-1.5 text-[11.5px] font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-500)]/15 px-2.5 py-1 border border-[var(--color-warning-500)]/30 shrink-0">
                                <Sparkles size={13} className="text-[var(--color-warning-600)]" />
                                <span>Saisie ouverte : les heures saisies compteront en Férié travaillé (+100%)</span>
                            </div>
                        </div>
                    </div>
                )}

                {/* 5. Table de Saisie DOMINANTE (scroll interne) */}
                {loading ? (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-xs font-bold text-[var(--color-fg-muted)] flex items-center justify-center gap-2" style={{ minHeight: 'calc(100vh - 260px)' }}>
                        <Clock size={16} className="animate-spin text-brand-500" />
                        Chargement des pointages…
                    </div>
                ) : error ? (
                    <div className="bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 p-4 text-xs font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] flex items-center gap-2">
                        <AlertCircle size={15} />
                        {error}
                    </div>
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
                        isReadOnly={isReadOnly}
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
                        siteIndemnites={siteIndemnites}
                        selectedDate={selectedDate}
                        siteLabel={siteLabel}
                    />
                </Suspense>
            )}
        </>
    );
}
