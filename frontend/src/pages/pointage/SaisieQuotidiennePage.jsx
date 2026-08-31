import { useState, useRef, useMemo, useEffect, useCallback } from 'react';
import { Save, Send, Clock, ShieldCheck, AlertCircle } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';

import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import PointageToolbar from '@/components/pointage/PointageToolbar';
import PointagePeriodeBanner from '@/components/pointage/PointagePeriodeBanner';
import PointageGridTable from '@/components/pointage/PointageGridTable';
import usePointageSaisie from '@/hooks/pointage/usePointageSaisie';

export default function SaisieQuotidiennePage() {
    const { toast } = useToast();
    const { user } = useAuth();
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
        siteParams,
        siteIndemnites,
        periodeInfo,
        loading,
        saving,
        error,
        loadData,
        updateRowHours,
        updateRowIndemnite,
        updateRowDynamicIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        saveAll
    } = usePointageSaisie(1, new Date().toISOString().split('T')[0]);

    const [searchTerm, setSearchTerm] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [isSubmitModalOpen, setIsSubmitModalOpen] = useState(false);
    const [submittingDaf, setSubmittingDaf] = useState(false);

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
        // Fin de la ligne (champ 3) : passage automatique au premier champ actif de l'employé suivant
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

    // Filtrage des données par recherche employé
    const filteredData = useMemo(() => {
        if (!searchTerm.trim()) return grilleData;
        const term = searchTerm.toLowerCase().trim();
        return grilleData.filter(row =>
            (row.nom && row.nom.toLowerCase().includes(term)) ||
            (row.prenom && row.prenom.toLowerCase().includes(term)) ||
            (row.matricule && row.matricule.toLowerCase().includes(term))
        );
    }, [grilleData, searchTerm]);

    const isReadOnly = useMemo(() => {
        if (!periodeInfo) return false;
        const statut = periodeInfo.statut_code;
        if (statut === 'VALIDEE' || statut === 'CLOTUREE') return true;
        if (statut === 'A_VALIDER' && isAgent) return true;
        return false;
    }, [periodeInfo, isAgent]);

    const totalPresents = useMemo(() => {
        return grilleData.filter(r => r.codeStatut === 'PRESENT' || r.codeStatut === 'DISPENSE_POINTAGE' || r.codeStatut === 'RETARD').length;
    }, [grilleData]);

    const totalConges = useMemo(() => {
        return grilleData.filter(r => r.codeStatut === 'CONGE' || r.typeCongeJour).length;
    }, [grilleData]);

    const totalAnomalies = useMemo(() => {
        return grilleData.filter(r => r.codeStatut === 'ANOMALIE_SAISIE' || r.codeStatut === 'ABSENCE_NON_JUSTIFIEE').length;
    }, [grilleData]);

    const handleSave = useCallback(async () => {
        const ok = await saveAll();
        if (ok) {
            toast({
                title: 'Enregistrement réussi',
                message: 'Les pointages et indemnités du jour ont été enregistrés avec succès.',
                type: 'success'
            });
        } else {
            toast({
                title: 'Erreur',
                message: 'Une erreur est survenue lors de l\'enregistrement des pointages.',
                type: 'error'
            });
        }
    }, [saveAll, toast]);

    const handleSubmitToDaf = async () => {
        if (!periodeInfo || !periodeInfo.id_periode_paie) return;
        setSubmittingDaf(true);
        try {
            await saveAll();
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

    // Écouteur global pour raccourci clavier Ctrl+S / Cmd+S
    useEffect(() => {
        const handleKeyDownGlobal = (e) => {
            if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                e.preventDefault();
                if (!isReadOnly && !saving && !loading) {
                    handleSave();
                }
            }
        };
        window.addEventListener('keydown', handleKeyDownGlobal);
        return () => window.removeEventListener('keydown', handleKeyDownGlobal);
    }, [isReadOnly, saving, loading, handleSave]);

    return (
        <>
            <SEO
                title="Saisie Quotidienne des Pointages | GALAXAM SIRH"
                description="Module de saisie et de contrôle quotidien des heures et indemnités."
            />

            <div className="space-y-4 max-w-[1600px] mx-auto pb-24">
                {/* 1. Header de Page */}
                <PageHeader
                    title="Pointage & Présence Quotidienne"
                    subtitle="Saisie opérationnelle, suivi des retards, heures de nuit et indemnités journalières"
                    breadcrumbs={[
                        { label: 'Accueil', href: '/' },
                        { label: 'Pointages', href: '/pointages/saisie' },
                        { label: 'Saisie Quotidienne' }
                    ]}
                    actions={
                        <Button
                            type="button"
                            variant="primary"
                            size="md"
                            disabled={isReadOnly || saving || loading}
                            loading={saving}
                            onClick={handleSave}
                            className="font-bold flex items-center gap-2 shadow-xs"
                        >
                            <Save size={16} />
                            Enregistrer les pointages
                        </Button>
                    }
                />

                {/* 2. Barre de Navigation Guidée (Workflow en 4 étapes) */}
                <PointageWorkflowNav />

                {/* 3. Bandeau d'État de la Période */}
                <PointagePeriodeBanner
                    periodeInfo={periodeInfo}
                    totalEmployes={grilleData.length}
                    totalPresents={totalPresents}
                    totalConges={totalConges}
                    totalAnomalies={totalAnomalies}
                />

                {/* 4. Barre d'outils et Filtres */}
                <PointageToolbar
                    sites={sites}
                    selectedSite={selectedSite}
                    onSiteChange={(s) => { setSelectedSite(s); setCurrentPage(1); }}
                    selectedDate={selectedDate}
                    onDateChange={(d) => { setSelectedDate(d); setCurrentPage(1); }}
                    searchTerm={searchTerm}
                    onSearchChange={(t) => { setSearchTerm(t); setCurrentPage(1); }}
                    onAutoFillStandard={autoFillStandardHours}
                    onAutoDeducePauses={autoDeduceAllPauses}
                    isReadOnly={isReadOnly}
                />

                {/* 5. Grille de Saisie Principale */}
                {loading ? (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-xs font-bold text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                        <Clock size={16} className="animate-spin text-[var(--color-brand-500)]" />
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
                        onUpdateHours={updateRowHours}
                        onUpdateIndemnite={updateRowIndemnite}
                        onUpdateDynamicIndemnite={updateRowDynamicIndemnite}
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

                {/* 6. Carte de Clôture & Transmission à la DAF */}
                {periodeInfo && periodeInfo.statut_code === 'EN_COURS' && (
                    <div className="mt-8 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-6 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4">
                        <div className="flex items-start gap-4">
                            <div className="p-3 bg-amber-500/10 text-amber-600 rounded">
                                <ShieldCheck size={24} />
                            </div>
                            <div>
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                    Fin de cycle de pointage & Transmission à la DAF
                                </h3>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1 max-w-2xl">
                                    Lorsque tous les pointages du mois sont saisis et vérifiés sur l'ensemble des sites,
                                    transmettez la période pour vérification globale, synthèse hebdomadaire et clôture par la DAF.
                                </p>
                            </div>
                        </div>
                        <Button
                            type="button"
                            variant="outline"
                            size="md"
                            disabled={isReadOnly || submittingDaf}
                            onClick={() => setIsSubmitModalOpen(true)}
                            className="font-bold flex items-center gap-2 border-[var(--color-brand-500)] text-[var(--color-brand-600)] hover:bg-[var(--color-brand-500)]/10 shrink-0"
                        >
                            <Send size={16} />
                            Transmettre la période à la DAF
                        </Button>
                    </div>
                )}
            </div>

            {/* 7. Barre d'Action Flottante / Sticky en Bas d'Écran (Toujours visible au scroll) */}
            <div className="fixed bottom-0 left-0 right-0 z-40 bg-[var(--color-surface)] border-t border-[var(--color-border)] shadow-2xl py-3 px-6">
                <div className="max-w-[1600px] mx-auto flex flex-col sm:flex-row items-center justify-between gap-3">
                    <div className="flex items-center gap-3 text-xs">
                        <span className="font-bold text-[var(--color-fg)]">
                            {filteredData.length} collaborateur(s) listé(s)
                        </span>
                        <span className="text-[var(--color-fg-muted)]">•</span>
                        <span className="text-[var(--color-fg-muted)] flex items-center gap-1">
                            Raccourci : <kbd className="px-1.5 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] font-mono text-[10px] font-bold">Ctrl+S</kbd>
                        </span>
                    </div>

                    <div className="flex items-center gap-3">
                        <Button
                            type="button"
                            variant="primary"
                            size="md"
                            disabled={isReadOnly || saving || loading}
                            loading={saving}
                            onClick={handleSave}
                            className="font-bold flex items-center gap-2 shadow-md"
                        >
                            <Save size={16} />
                            Enregistrer les pointages
                        </Button>
                    </div>
                </div>
            </div>

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
