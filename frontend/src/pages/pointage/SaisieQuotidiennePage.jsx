import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { Save, PlusCircle, Clock, AlertCircle } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';

import PointageGuideStepper from '@/components/pointage/PointageGuideStepper';
import PointageFerieAlert from '@/components/pointage/PointageFerieAlert';
import PointageStatsCards from '@/components/pointage/PointageStatsCards';
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
        if (!periodeInfo) return true; // Bloqué si aucune période ouverte pour cette date !
        const statut = periodeInfo.statut_code;
        if (statut === 'VALIDEE' || statut === 'CLOTUREE') return true;
        if (statut === 'A_VALIDER' && isAgent) return true;
        return false;
    }, [periodeInfo, isAgent]);

    const currentSiteObj = sites.find(s => s.idSite === selectedSite);
    const siteLabel = currentSiteObj
        ? `${currentSiteObj.codeSite || currentSiteObj.code || 'Site'} — ${currentSiteObj.libelle}`
        : (sites.length > 0 ? `${sites[0].codeSite || sites[0].code || 'Site'} — ${sites[0].libelle}` : 'Site sélectionné');

    // Statistiques calculées en temps réel sur la journée (Priorité aux données certifiées backend)
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

            <div className="space-y-5 max-w-[1440px] mx-auto pb-20">
                {/* 1. Header de Page */}
                <PageHeader
                    title="Pointage & présence quotidienne"
                    subtitle="Saisissez les horaires du jour, vérifiez les anomalies, puis transmettez le mois à la DAF."
                    breadcrumbs={[
                        { label: 'Accueil', href: '/' },
                        { label: 'Pointages', href: '/pointages/saisie' },
                        { label: 'Saisie quotidienne' }
                    ]}
                    actions={
                        <div className="flex items-center gap-2.5">
                            <Button
                                type="button"
                                variant="outline"
                                size="md"
                                onClick={() => navigate('/conges/demandes')}
                                className="font-semibold flex items-center gap-1.5"
                            >
                                <PlusCircle size={15} />
                                <span>Ajouter une absence</span>
                            </Button>
                            <Button
                                type="button"
                                variant="primary"
                                size="md"
                                disabled={isReadOnly || saving || loading}
                                loading={saving}
                                onClick={handleRequestSave}
                                className="font-semibold flex items-center gap-2 shadow-xs"
                            >
                                <Save size={15} />
                                <span>Enregistrer les pointages</span>
                                <span className="opacity-70 font-normal text-[11px]">(Ctrl+S)</span>
                            </Button>
                        </div>
                    }
                />

                {/* 2. Étape Guidée (Stepper en 4 étapes) */}
                <PointageGuideStepper
                    periodeInfo={periodeInfo}
                    totalWeeks={4}
                    currentStep={1}
                />

                {/* Alerte si aucune période de paie n'englobe la date sélectionnée */}
                {!periodeInfo && !loading && (
                    <div className="bg-amber-500/10 border-l-4 border-l-amber-500 p-4 text-xs text-amber-900 dark:text-amber-200 flex items-start gap-3 shadow-xs">
                        <AlertCircle size={18} className="shrink-0 text-amber-600 mt-0.5" />
                        <div>
                            <p className="font-bold text-[13px]">Aucune période de paie ouverte pour la date du {selectedDate}</p>
                            <p className="text-[12px] opacity-90 mt-0.5">
                                La saisie et l'enregistrement des pointages sont verrouillés tant qu'une période de paie active n'englobe pas cette date.
                            </p>
                        </div>
                    </div>
                )}

                {/* 3. Bandeau Info Jour (Férié / Période Chômée) */}
                <PointageFerieAlert
                    isFerie={statsJour.isFerie}
                    libelleFerie={statsJour.libelleFerie}
                    siteLabel={statsJour.siteLabel}
                />

                {/* 4. Cartes Métriques Synthétiques du Jour */}
                <PointageStatsCards
                    totalInscrits={statsJour.totalInscrits}
                    totalPresents={statsJour.totalPresents}
                    totalConges={statsJour.totalConges}
                    totalRetards={statsJour.totalRetards}
                    cumulMinutesRetard={statsJour.cumulMinutesRetard}
                    totalHeuresTravaillees={statsJour.totalHeuresTravaillees}
                    siteLabel={statsJour.siteLabel}
                />

                {/* 5. Filtres et Paramètres de Saisie */}
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
                />

                {/* 6. Tableau de Saisie Principal */}
                {loading ? (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-xs font-bold text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                        <Clock size={16} className="animate-spin text-brand-500" />
                        Chargement des pointages...
                    </div>
                ) : error ? (
                    <div className="bg-rose-500/10 border border-rose-500/30 p-4 text-xs font-bold text-rose-700 dark:text-rose-400 flex items-center gap-2">
                        <AlertCircle size={16} />
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

                {/* 7. Contrôle des Anomalies */}
                <PointageAnomaliesSection
                    totalAnomalies={statsJour.totalAnomalies}
                />

                {/* 8. Synthèse Hebdomadaire (4 semaines réelles via Backend) */}
                <PointageWeeklySyntheseSection
                    periodeInfo={periodeInfo}
                    selectedSite={selectedSite}
                />

                {/* 9. Transmission Finale à la DAF */}
                <PointageTransmissionDafSection
                    periodeInfo={periodeInfo}
                    totalAnomalies={statsJour.totalAnomalies}
                    isReadOnly={isReadOnly}
                    submittingDaf={submittingDaf}
                    onRequestSubmitDaf={() => setIsSubmitModalOpen(true)}
                />
            </div>

            {/* 10. Barre Sticky Persistante (Intégrée au contenu principal, ne recouvre JAMAIS la sidebar) */}
            {dirtyCount > 0 && (
                <div className="sticky bottom-[-24px] -mx-6 -mb-6 bg-[#0F2942] text-white z-30 shadow-2xl border-t border-white/10 animate-in fade-in slide-in-from-bottom-2 duration-200">
                    <div className="max-w-[1440px] mx-auto px-6 py-3 flex flex-col sm:flex-row items-center justify-between gap-3">
                        <p className="text-[13px] text-white/90">
                            <span className="font-bold text-amber-400">{dirtyCount}</span> salarié{dirtyCount > 1 ? 's' : ''} modifié{dirtyCount > 1 ? 's' : ''} non enregistré{dirtyCount > 1 ? 's' : ''} · N'oubliez pas d'enregistrer avant de changer de date ou de site.
                        </p>
                        <div className="flex items-center gap-2.5 shrink-0">
                            <button
                                type="button"
                                onClick={resetAllChanges}
                                disabled={saving}
                                className="bg-white/10 hover:bg-white/20 text-white px-3.5 py-2 text-[13px] font-semibold border border-white/20 transition-colors cursor-pointer"
                            >
                                Annuler les modifications
                            </button>
                            <button
                                type="button"
                                onClick={handleRequestSave}
                                disabled={saving}
                                className="bg-white text-[#0F2942] hover:bg-neutral-100 px-4 py-2 text-[13px] font-bold shadow-sm transition-colors cursor-pointer"
                            >
                                Enregistrer les pointages
                            </button>
                        </div>
                    </div>
                </div>
            )}

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
