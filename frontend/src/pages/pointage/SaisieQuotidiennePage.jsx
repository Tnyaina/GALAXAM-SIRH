import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Clock, Sparkles } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/api/admin/organisationService';

import PointageFerieAlert from '@/components/pointage/PointageFerieAlert';
import PointageToolbar from '@/components/pointage/PointageToolbar';
import PointageGridTable from '@/components/pointage/PointageGridTable';
import PointageRecapModal from '@/components/pointage/PointageRecapModal';
import usePointageSaisie from '@/hooks/pointage/usePointageSaisie';

export default function SaisieQuotidiennePage() {
    const { toast } = useToast();
    const { hasPermission } = useAuth();
    const navigate = useNavigate();
    const [sites, setSites] = useState([]);
    const canWrite = hasPermission('POINTAGE_WRITE');
    const canValidate = hasPermission('POINTAGE_VALIDATE');

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
        updateRowIndemnite,
        updateRowDynamicIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        saveAll
    } = usePointageSaisie(1, new Date().toISOString().split('T')[0]);

    const [searchTerm, setSearchTerm] = useState('');
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
    };

    const handleDateChange = (dateStr) => {
        setSelectedDate(dateStr);
        setCurrentPage(1);
    };

    // Modales
    const [isRecapModalOpen, setIsRecapModalOpen] = useState(false);

    // Actions
    const handleUpdateHours = (idEmploye, field, value) => {
        updateRowHours(idEmploye, field, value);
    };

    const handleUpdateIndemnite = (idEmploye, field, value) => {
        updateRowIndemnite(idEmploye, field, value);
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
        const nextIndex = fieldIndex + 1;
        if (nextIndex <= 3) {
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
        if (e.key === 'ArrowRight' && fieldIndex < 3) {
            inputRefs.current[idEmploye]?.[fieldIndex + 1]?.focus();
        } else if (e.key === 'ArrowLeft' && fieldIndex > 0) {
            inputRefs.current[idEmploye]?.[fieldIndex - 1]?.focus();
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

    // Comptages par statut pour les onglets (100% basé sur les flags booléens DTO)
    const statusCounts = useMemo(() => {
        const list = Array.isArray(grilleData) ? grilleData : [];
        const all = list.length;
        const presents = list.filter(r => r.present || r.dispensePointage || r.ferieTravaille || (r.totalHeures && r.totalHeures > 0)).length;
        const conges = list.filter(r => r.conge).length;
        const nonSaisis = list.filter(r => (!r.totalHeures || r.totalHeures === 0) && !r.conge && !r.dispensePointage && !r.ferieChome).length;
        const retards = list.filter(r => r.retard || (r.minutesRetard && r.minutesRetard > 0)).length;

        return { ALL: all, PRESENT: presents, CONGE: conges, NON_SAISI: nonSaisis, RETARD: retards };
    }, [grilleData]);

    // Filtrage dynamique des données (recherche texte + filtre de statut)
    const filteredData = useMemo(() => {
        let list = Array.isArray(grilleData) ? grilleData : [];

        if (statutFilter === 'PRESENT') {
            list = list.filter(r => r.present || r.dispensePointage || r.ferieTravaille || (r.totalHeures && r.totalHeures > 0));
        } else if (statutFilter === 'CONGE') {
            list = list.filter(r => r.conge);
        } else if (statutFilter === 'NON_SAISI') {
            list = list.filter(r => (!r.totalHeures || r.totalHeures === 0) && !r.conge && !r.dispensePointage && !r.ferieChome);
        } else if (statutFilter === 'RETARD') {
            list = list.filter(r => r.retard || (r.minutesRetard && r.minutesRetard > 0));
        }

        if (!searchTerm.trim()) return list;
        const term = searchTerm.toLowerCase().trim();
        return list.filter(row =>
            (row.nom && row.nom.toLowerCase().includes(term)) ||
            (row.prenom && row.prenom.toLowerCase().includes(term)) ||
            (row.matricule && row.matricule.toLowerCase().includes(term))
        );
    }, [grilleData, statutFilter, searchTerm]);

    const isReadOnly = useMemo(() => {
        if (!canWrite) return true;
        if (!periodeInfo) return true;
        const statut = periodeInfo.statut_code;
        if (statut === 'VALIDEE' || statut === 'CLOTUREE') return true;
        if (statut === 'A_VALIDER' && !canValidate) return true;
        return false;
    }, [periodeInfo, canValidate, canWrite]);

    const currentSiteObj = sites.find(s => s.idSite === selectedSite);
    const siteLabel = currentSiteObj
        ? `${currentSiteObj.codeSite || currentSiteObj.code || 'Site'} — ${currentSiteObj.libelle}`
        : (sites.length > 0 ? `${sites[0].codeSite || sites[0].code || 'Site'} — ${sites[0].libelle}` : 'Site sélectionné');

    // Statistiques calculées en temps réel
    const statsJour = useMemo(() => {
        if (backendStatsJour && modifiedEmpIds.size === 0) {
            return {
                ...backendStatsJour,
                isFerie: grilleData[0]?.ferieJour || false,
                libelleFerie: grilleData[0]?.libelleFerie || null,
                siteLabel
            };
        }
        const safeList = Array.isArray(grilleData) ? grilleData : [];
        const totalInscrits = safeList.length;
        const totalPresents = safeList.filter(r => r.present || r.dispensePointage || r.ferieTravaille || (r.totalHeures && r.totalHeures > 0)).length;
        const totalConges = safeList.filter(r => r.conge).length;
        const totalRetards = safeList.filter(r => r.retard || (r.minutesRetard && r.minutesRetard > 0)).length;
        const cumulMinutesRetard = safeList.reduce((acc, r) => acc + (Number(r.minutesRetard) || 0), 0);
        const totalHeuresTravaillees = safeList.reduce((acc, r) => acc + (Number(r.totalHeures) || 0), 0);
        let isWeekend = false;
        if (selectedDate) {
            const dObj = new Date(selectedDate);
            const day = dObj.getDay();
            isWeekend = (day === 0 || day === 6);
        }
        const totalAnomalies = safeList.filter(r => r.anomalie || (!isWeekend && r.absenceNonJustifiee)).length;
        const isFerie = safeList[0]?.ferieJour || false;
        const libelleFerie = safeList[0]?.libelleFerie || null;

        return {
            totalInscrits,
            totalPresents,
            totalConges,
            totalRetards,
            cumulMinutesRetard,
            totalHeuresTravaillees: Math.round(totalHeuresTravaillees * 100.0) / 100.0,
            totalAnomalies,
            isFerie,
            libelleFerie,
            siteLabel
        };
    }, [backendStatsJour, modifiedEmpIds.size, grilleData, siteLabel, selectedDate]);

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

            <div className="space-y-2 max-w-[1440px] mx-auto pb-6">

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
                    onAddConge={() => navigate('/conges?tab=demandes')}
                    isReadOnly={isReadOnly}
                    onSave={handleRequestSave}
                    saving={saving}
                    dirtyCount={dirtyCount}
                />

                {/* 2. Alert : Aucune période de paie ouverte */}
                {!periodeInfo && !loading && (
                    <div className="bg-amber-500/10 border-l-4 border-l-amber-500 p-3 text-xs text-amber-900 dark:text-amber-200 flex items-center justify-between gap-3 shadow-xs flex-wrap">
                        <div className="flex items-center gap-2.5">
                            <AlertCircle size={16} className="shrink-0 text-amber-600 dark:text-amber-400" />
                            <div>
                                <span className="font-bold">Aucune période de paie ouverte pour le {selectedDate}</span>
                                <span className="ml-2 text-[11px] opacity-85">— La saisie est verrouillée en lecture seule.</span>
                            </div>
                        </div>
                        {canValidate && (
                            <Button
                                size="xs"
                                variant="primary"
                                onClick={() => navigate(`/pointages?tab=periodes&action=create&targetDate=${selectedDate}`)}
                                icon={<Sparkles size={13} className="text-amber-300" />}
                                className="shrink-0"
                            >
                                Créer la période ({(() => {
                                    if (!selectedDate) return 'Suivante';
                                    const parts = selectedDate.split('-');
                                    if (parts.length === 3) {
                                        const mIdx = parseInt(parts[1], 10) - 1;
                                        const moisFr = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
                                        return `${moisFr[mIdx]} ${parts[0]}`;
                                    }
                                    return 'Suivante';
                                })()})
                            </Button>
                        )}
                    </div>
                )}

                {/* 4. Alert Jour Férié */}
                <PointageFerieAlert
                    isFerie={statsJour.isFerie}
                    libelleFerie={statsJour.libelleFerie}
                    siteLabel={statsJour.siteLabel}
                />

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
                        onUpdateIndemnite={handleUpdateIndemnite}
                        onUpdateDynamicIndemnite={handleUpdateDynamicIndemnite}
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

            {/* Modale Récapitulative & Validation Avant Sauvegarde */}
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
        </>
    );
}
