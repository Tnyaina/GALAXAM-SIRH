import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlertCircle, Clock, PlusCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';

import PointageGuideStepper from '@/components/pointage/PointageGuideStepper';
import PointageFerieAlert from '@/components/pointage/PointageFerieAlert';
import PointageToolbar from '@/components/pointage/PointageToolbar';
import PointageGridTable from '@/components/pointage/PointageGridTable';
import PointageAnomaliesSection from '@/components/pointage/PointageAnomaliesSection';
import PointageWeeklySyntheseSection from '@/components/pointage/PointageWeeklySyntheseSection';
import PointageTransmissionDafSection from '@/components/pointage/PointageTransmissionDafSection';
import PointageRecapModal from '@/components/pointage/PointageRecapModal';
import usePointageSaisie from '@/hooks/pointage/usePointageSaisie';

export default function SaisieQuotidiennePage() {
    const { toast } = useToast();
    const { user } = useAuth();
    const navigate = useNavigate();
    const [sites, setSites] = useState([]);
    const isAgent = user?.roles?.includes('ROLE_AGENT_POINTAGE') && !user?.roles?.includes('ROLE_ADMIN');

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
        loadData,
        isRowModified,
        resetAllChanges,
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
    const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
    const [submittingDaf, setSubmittingDaf] = useState(false);

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
        if (!periodeInfo) return true;
        const statut = periodeInfo.statut_code;
        if (statut === 'VALIDEE' || statut === 'CLOTUREE') return true;
        if (statut === 'A_VALIDER' && isAgent) return true;
        return false;
    }, [periodeInfo, isAgent]);

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
        const totalAnomalies = safeList.filter(r => r.anomalie || r.absenceNonJustifiee).length;
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
    }, [backendStatsJour, modifiedEmpIds.size, grilleData, siteLabel]);

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

    const handleSubmitToDaf = async () => {
        if (!periodeInfo || !periodeInfo.id_periode_paie) return;
        setSubmittingDaf(true);
        try {
            await saveAll();
            setModifiedEmpIds(new Set());
            await periodePaieService.updateStatut(periodeInfo.id_periode_paie, 'A_VALIDER');
            toast({
                title: 'Période transmise à la DAF',
                message: 'La période a été transmise avec succès pour validation finale.',
                type: 'success'
            });
            setIsSubmitModalOpen(false);
            await loadData();
        } catch (err) {
            console.error('Erreur transmission DAF:', err);
            toast({
                title: 'Erreur de transmission',
                message: 'Impossible de transmettre la période à la DAF.',
                type: 'error'
            });
        } finally {
            setSubmittingDaf(false);
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

                {/* En-tête minimal : titre + lien rapide */}
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-[17px] font-bold text-[var(--color-fg)]">Pointage & présence quotidienne</h1>
                        <p className="text-[11.5px] text-[var(--color-fg-muted)] mt-0.5">
                            Saisissez les horaires du jour, vérifiez les anomalies, transmettez à la DAF.
                        </p>
                    </div>
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => navigate('/conges/demandes')}
                        className="font-semibold flex items-center gap-1.5 shrink-0"
                    >
                        <PlusCircle size={13} />
                        <span>Ajouter une absence</span>
                    </Button>
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
                    isReadOnly={isReadOnly}
                    onSave={handleRequestSave}
                    saving={saving}
                    dirtyCount={dirtyCount}
                />

                {/* 2. Slim Banner Période + Stepper */}
                <PointageGuideStepper
                    periodeInfo={periodeInfo}
                    totalWeeks={4}
                    currentStep={1}
                />

                {/* 3. Alert : Aucune période de paie ouverte */}
                {!periodeInfo && !loading && (
                    <div className="bg-amber-500/10 border-l-4 border-l-amber-500 p-3.5 text-xs text-amber-900 dark:text-amber-200 flex items-start gap-3 shadow-xs">
                        <AlertCircle size={15} className="shrink-0 text-amber-600 mt-0.5" />
                        <div>
                            <p className="font-bold text-[12.5px]">Aucune période de paie ouverte pour le {selectedDate}</p>
                            <p className="text-[11.5px] opacity-90 mt-0.5">
                                La saisie est verrouillée tant qu'aucune période active n'englobe cette date.
                            </p>
                        </div>
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
                    <div className="bg-rose-500/10 border border-rose-500/30 p-4 text-xs font-bold text-rose-700 dark:text-rose-400 flex items-center gap-2">
                        <AlertCircle size={15} />
                        {error}
                    </div>
                ) : (
                    <PointageGridTable
                        grilleData={filteredData}
                        siteParams={siteParams}
                        siteIndemnites={siteIndemnites}
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

                {/* 6. Sections secondaires en accordéon */}
                <PointageAnomaliesSection
                    totalAnomalies={statsJour.totalAnomalies}
                />

                <PointageWeeklySyntheseSection
                    periodeInfo={periodeInfo}
                    selectedSite={selectedSite}
                />

                <PointageTransmissionDafSection
                    periodeInfo={periodeInfo}
                    totalAnomalies={statsJour.totalAnomalies}
                    isReadOnly={isReadOnly}
                    submittingDaf={submittingDaf}
                    onRequestSubmitDaf={() => setIsSubmitModalOpen(true)}
                />
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

            {/* Modale de Confirmation de Transmission DAF */}
            <ConfirmDialog
                isOpen={isSubmitModalOpen}
                title="Confirmer la transmission de la période à la DAF ?"
                message={`Vous êtes sur le point de transmettre la période "${periodeInfo?.libelle || periodeInfo?.code}" à la DAF. La saisie quotidienne sera verrouillée pour les agents jusqu'à validation finale.`}
                confirmLabel="Confirmer et Transmettre"
                confirmVariant="primary"
                onConfirm={handleSubmitToDaf}
                onCancel={() => setIsSubmitModalOpen(false)}
            />
        </>
    );
}
