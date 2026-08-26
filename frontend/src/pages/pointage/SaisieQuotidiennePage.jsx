import { useRef, useState, useCallback } from 'react';
import { usePointageSaisie } from '@/hooks/pointage/usePointageSaisie';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Composants modulaires découplés
import PointageHeader from '@/components/pointage/PointageHeader';
import PointagePeriodeBanner from '@/components/pointage/PointagePeriodeBanner';
import PointageStatsCards from '@/components/pointage/PointageStatsCards';
import PointageToolbar from '@/components/pointage/PointageToolbar';
import PointageGridTable from '@/components/pointage/PointageGridTable';
import PointageGanttView from '@/components/pointage/gantt/PointageGanttView';

import PointageSubNav from '@/components/pointage/PointageSubNav';

export default function SaisieQuotidiennePage() {
    const {
        sitesList,
        selectedSite,
        setSelectedSite,
        selectedDate,
        setSelectedDate,
        viewMode,
        setViewMode,
        ganttPeriod,
        setGanttPeriod,
        searchTerm,
        setSearchTerm,
        currentPage,
        setCurrentPage,
        grilleData,
        ganttData,
        periodeInfo,
        siteParams,
        kpis,
        loading,
        saving,
        error,
        successMsg,
        updateRowHours,
        updateRowIndemnite,
        autoFillStandardHours,
        autoDeduceAllPauses,
        updatePeriodeStatutDirect,
        savePointages
    } = usePointageSaisie();

    const { user } = useAuth();
    const isAdmin = user?.role === 'ROLE_ADMIN';
    const toast = useToast();

    // Modale de confirmation pour actions de statut sur la période
    const [confirmModal, setConfirmModal] = useState({
        isOpen: false,
        targetStatut: null,
        title: '',
        message: '',
        variant: 'primary',
        confirmLabel: 'Confirmer'
    });

    const isLocked = periodeInfo?.statut_code === 'VALIDEE' || periodeInfo?.statut_code === 'CLOTUREE';
    const isAValider = periodeInfo?.statut_code === 'A_VALIDER';
    const isReadOnlyForUser = isLocked || (isAValider && !isAdmin) || !periodeInfo || periodeInfo?.statut_code === 'BROUILLON';

    const handleTriggerStatut = (targetStatut, title, message) => {
        let variant = 'primary';
        let confirmLabel = 'Confirmer';
        if (targetStatut === 'VALIDEE') {
            variant = 'primary';
            confirmLabel = 'Valider et verrouiller';
        } else if (targetStatut === 'EN_COURS') {
            variant = 'warning';
            confirmLabel = 'Oui, rouvrir la saisie';
        } else if (targetStatut === 'A_VALIDER') {
            variant = 'warning';
            confirmLabel = 'Transmettre';
        }

        setConfirmModal({
            isOpen: true,
            targetStatut,
            title,
            message,
            variant,
            confirmLabel
        });
    };

    const handleConfirmStatutChange = async () => {
        if (!periodeInfo?.id_periode_paie || !confirmModal.targetStatut) return;
        const success = await updatePeriodeStatutDirect(periodeInfo.id_periode_paie, confirmModal.targetStatut);
        setConfirmModal((prev) => ({ ...prev, isOpen: false }));
        if (success) {
            toast({
                title: 'Statut de la période mis à jour',
                message: `La période est désormais au statut : ${confirmModal.targetStatut}`,
                type: 'success'
            });
        }
    };

    // Navigation Clavier Haute Vitesse (Mode tableur)
    const inputRefs = useRef({});
    const getInputKey = useCallback((empId, fieldIndex) => `${empId}-${fieldIndex}`, []);

    const handleFocusNext = (empId, currentFieldIndex) => {
        const fieldsCount = 4;
        let nextEmpId = empId;
        let nextFieldIndex = currentFieldIndex + 1;

        if (nextFieldIndex >= fieldsCount) {
            const empIndex = grilleData.findIndex((r) => r.idEmploye === empId);
            if (empIndex >= 0 && empIndex < grilleData.length - 1) {
                nextEmpId = grilleData[empIndex + 1].idEmploye;
                nextFieldIndex = 0;
            } else {
                return;
            }
        }

        const nextKey = getInputKey(nextEmpId, nextFieldIndex);
        if (inputRefs.current[nextKey]) {
            inputRefs.current[nextKey].focus();
        }
    };

    const handleFocusPrev = (empId, currentFieldIndex) => {
        const fieldsCount = 4;
        let prevEmpId = empId;
        let prevFieldIndex = currentFieldIndex - 1;

        if (prevFieldIndex < 0) {
            const empIndex = grilleData.findIndex((r) => r.idEmploye === empId);
            if (empIndex > 0) {
                prevEmpId = grilleData[empIndex - 1].idEmploye;
                prevFieldIndex = fieldsCount - 1;
            } else {
                return;
            }
        }

        const prevKey = getInputKey(prevEmpId, prevFieldIndex);
        if (inputRefs.current[prevKey]) {
            inputRefs.current[prevKey].focus();
        }
    };

    const handleFocusRow = (empId, currentFieldIndex, direction) => {
        const empIndex = grilleData.findIndex((r) => r.idEmploye === empId);
        const targetIndex = empIndex + direction;
        if (targetIndex >= 0 && targetIndex < grilleData.length) {
            const targetEmpId = grilleData[targetIndex].idEmploye;
            const targetKey = getInputKey(targetEmpId, currentFieldIndex);
            if (inputRefs.current[targetKey]) {
                inputRefs.current[targetKey].focus();
            }
        }
    };

    const handleKeyDown = (e, empId, fieldIndex) => {
        if (e.key === 'ArrowRight') {
            e.preventDefault();
            handleFocusNext(empId, fieldIndex);
        } else if (e.key === 'ArrowLeft') {
            e.preventDefault();
            handleFocusPrev(empId, fieldIndex);
        } else if (e.key === 'ArrowDown') {
            e.preventDefault();
            handleFocusRow(empId, fieldIndex, 1);
        } else if (e.key === 'ArrowUp') {
            e.preventDefault();
            handleFocusRow(empId, fieldIndex, -1);
        }
    };

    const handleApplyStandardHours = () => {
        autoFillStandardHours();
        toast({
            title: 'Horaires standards appliqués',
            message: 'Les employés non saisis ont été complétés avec les horaires normaux du site.',
            type: 'info'
        });
    };

    const handleApplyAutoPauses = () => {
        autoDeduceAllPauses();
        toast({
            title: 'Pauses déduites',
            message: `Pause de ${siteParams?.horaireContinu ? '1h' : '2h'} appliquée sur les pointages jour.`,
            type: 'info'
        });
    };

    // Formatage Date
    let isToday = false;
    let dateLongue = '';
    if (selectedDate) {
        const d = new Date(selectedDate);
        if (!Number.isNaN(d.getTime())) {
            isToday = d.toDateString() === new Date().toDateString();
            dateLongue = d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
        }
    }

    const currentSiteObj = (sitesList || []).find(s => s.idSite === selectedSite);
    const siteLabel = currentSiteObj 
        ? `${currentSiteObj.codeSite || ''} — ${currentSiteObj.libelle || currentSiteObj.nomSite || ''}` 
        : `Site ${selectedSite}`;

    const handleRegisterInputRef = useCallback((idEmploye, colIndex, el) => {
        if (inputRefs?.current) {
            inputRefs.current[getInputKey(idEmploye, colIndex)] = el;
        }
    }, [inputRefs, getInputKey]);

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            {/* 0. Barre de sous-navigation Pointage */}
            <PointageSubNav />

            {/* 1. En-tête de la Page */}
            <PointageHeader
                siteLabel={siteLabel}
                dateLongue={dateLongue}
                isToday={isToday}
                viewMode={viewMode}
                onViewModeChange={setViewMode}
            />

            {/* 2. Notifications d'état */}
            {error && <Alert type="danger" message={error} />}
            {successMsg && <Alert type="success" message={successMsg} />}

            {/* 3. Bandeau Contexte de Période & Workflow */}
            <PointagePeriodeBanner
                periodeInfo={periodeInfo}
                selectedDate={selectedDate}
                isAdmin={isAdmin}
                onTriggerStatut={handleTriggerStatut}
            />

            {/* 4. Cartes KPIs Statistiques */}
            <PointageStatsCards kpis={kpis} />

            {/* 5. Barre d'outils (Site, Date, Recherche & Actions) */}
            <PointageToolbar
                sitesList={sitesList}
                selectedSite={selectedSite}
                onSiteChange={(site) => {
                    setSelectedSite(site);
                    setCurrentPage(1);
                }}
                selectedDate={selectedDate}
                onDateChange={setSelectedDate}
                isToday={isToday}
                siteParams={siteParams}
                searchTerm={searchTerm}
                onSearchChange={(term) => {
                    setSearchTerm(term);
                    setCurrentPage(1);
                }}
                viewMode={viewMode}
                isReadOnlyForUser={isReadOnlyForUser}
                saving={saving}
                loading={loading}
                onApplyStandardHours={handleApplyStandardHours}
                onApplyAutoPauses={handleApplyAutoPauses}
                onSave={savePointages}
            />

            {/* 6. Contenu Principal : Grille de Saisie ou Vue Gantt */}
            {loading && grilleData.length === 0 && viewMode === 'grille' ? (
                <SkeletonTable rows={8} cols={9} />
            ) : viewMode === 'gantt' ? (
                <div className={`transition-opacity duration-200 ${loading ? 'opacity-50 pointer-events-none' : 'opacity-100'}`}>
                    <PointageGanttView
                        ganttData={ganttData}
                        ganttPeriod={ganttPeriod}
                        onPeriodChange={setGanttPeriod}
                    />
                </div>
            ) : (
                <div className={`transition-opacity duration-200 ${loading ? 'opacity-50 pointer-events-none' : 'opacity-100'}`}>
                    <PointageGridTable
                        grilleData={grilleData}
                        siteParams={siteParams}
                        currentPage={currentPage}
                        onPageChange={setCurrentPage}
                        isReadOnlyForUser={isReadOnlyForUser}
                        onRegisterInputRef={handleRegisterInputRef}
                        onUpdateHours={updateRowHours}
                        onUpdateIndemnite={updateRowIndemnite}
                        onKeyDown={handleKeyDown}
                        onFocusNext={handleFocusNext}
                    />
                </div>
            )}

            {/* Modale de Confirmation de Changement de Statut de Période */}
            <ConfirmDialog
                isOpen={confirmModal.isOpen}
                title={confirmModal.title}
                message={confirmModal.message}
                variant={confirmModal.variant}
                confirmLabel={confirmModal.confirmLabel}
                onConfirm={handleConfirmStatutChange}
                onClose={() => setConfirmModal((prev) => ({ ...prev, isOpen: false }))}
            />
        </div>
    );
}