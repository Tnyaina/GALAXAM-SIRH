import { useState, lazy, Suspense } from 'react';
import { CalendarDays, ChevronDown, ChevronUp } from 'lucide-react';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';
import congeService from '@/features/leave/api/congeService';
import { fmtDateInput } from '@/utils/dateUtils';

// Hook Logique
import { useSoldesOverview } from '@/features/leave/hooks/useSoldesOverview';

// Sous-composants
import SoldesGantt from './soldes/SoldesGantt';
import SoldesTable from './soldes/SoldesTable';

// Modales chargées à la demande
const DemandeCongeModal = lazy(() => import('./DemandeCongeModal'));
const ValidationModals = lazy(() => import('./validation/ValidationModals'));

export default function SoldesOverviewTab({ nbEnAttente, referentiels, onReload, onOpenDemandes }) {
  // Contexte
  const toast = useToast();
  const { hasPermission } = useAuth();
  const canWrite = hasPermission('CONGE_WRITE');

  // État local : uniquement les modales ; les données de soldes vivent dans useSoldesOverview.
  const [demandeModalState, setDemandeModalState] = useState({
    isOpen: false,
    idEmploye: null,
    dateDebut: null,
    dateFin: null,
    initialDemande: null
  });

  // Modal de détails et actions (Validation / Refus / Annulation)
  const [selectedDemande, setSelectedDemande] = useState(null);
  const [showDetailModal, setShowDetailModal] = useState(false);
  const [showValiderModal, setShowValiderModal] = useState(false);
  const [showRefusModal, setShowRefusModal] = useState(false);
  const [showAnnulerModal, setShowAnnulerModal] = useState(false);
  const [motifRefus, setMotifRefus] = useState('');
  const [motifAnnulation, setMotifAnnulation] = useState('');
  const [actionLoading, setActionLoading] = useState(false);
  const [actionError, setActionError] = useState(null);
  const [showRecap, setShowRecap] = useState(false);

  // Logique métier et données dérivées
  const {
    // Mode et filtres
    viewMode,
    setViewMode,
    filterMode,
    setFilterMode,

    // Navigation Mois
    currentMonth,
    setCurrentMonth,
    year,
    month,
    daysInMonth,
    daysArray,
    isMoisActuel,
    handlePrevMonth,
    handleNextMonth,
    handleToday,
    handleMonthChange,

    // Navigation Semaine
    weekDaysArray,
    weekStartStr,
    weekEndStr,
    isSemaineActuelle,
    handlePrevWeek,
    handleNextWeek,
    handleTodayWeek,
    handleWeekDateChange,

    // Gantt Data & State
    planningLoading,
    planningError,
    ganttData,
    ganttRows,
    filteredGanttRows,
    paginatedGanttRows,
    totalGanttItems,
    nbEmployesConcernes,
    ganttSearch,
    setGanttSearch,
    ganttPage,
    setGanttPage,
    ganttPageSize,
    setGanttPageSize,
    LANE_HEIGHT,
    loadPlanning,

    // Table Data & State
    paginatedTableData,
    totalTableItems,
    tableSortField,
    tableSortOrder,
    handleTableSort,
    tablePage,
    setTablePage,
    tablePageSize,
    setTablePageSize
  } = useSoldesOverview();

  // Rendu : garde de chargement initial
  if (planningLoading && ganttRows.length === 0) {
    return (
      <div className="space-y-3">
        <SkeletonTable rows={5} />
      </div>
    );
  }

  // Actions utilisateur
  const handleCellClick = (idEmploye, dateDebut, dateFin) => {
    setDemandeModalState({
      isOpen: true,
      idEmploye: idEmploye ? Number(idEmploye) : null,
      dateDebut: dateDebut,
      dateFin: dateFin || dateDebut,
      initialDemande: null
    });
  };

  const handleLeaveClick = async (item) => {
    if (!item?.id) return;
    try {
      setActionLoading(true);
      setActionError(null);
      const demande = await congeService.getDemandeParId(item.id);
      setSelectedDemande(demande);
      setShowDetailModal(true);
    } catch (err) {
      toast({
        title: 'Erreur',
        message: getErrorMessage(err, 'Impossible de charger les détails du congé.'),
        type: 'error',
        duration: 3000
      });
    } finally {
      setActionLoading(false);
    }
  };

  const openValider = (d) => { setSelectedDemande(d); setActionError(null); setShowValiderModal(true); };
  const openRefus = (d) => { setSelectedDemande(d); setActionError(null); setMotifRefus(''); setShowRefusModal(true); };
  const openAnnuler = (d) => { setSelectedDemande(d); setActionError(null); setMotifAnnulation(''); setShowAnnulerModal(true); };
  const openModifier = (d) => {
    setShowDetailModal(false);
    setDemandeModalState({
      isOpen: true,
      idEmploye: d?.idEmploye || null,
      dateDebut: d?.dateDebut ? fmtDateInput(d.dateDebut) : null,
      dateFin: d?.dateFin ? fmtDateInput(d.dateFin) : null,
      initialDemande: d
    });
  };
  const closeDetail = () => { setShowDetailModal(false); setSelectedDemande(null); };

  const onConfirmValider = async (justificatifFourni, estRemunere, derogationSoldeNegatif) => {
    if (!selectedDemande) return;
    try {
      setActionLoading(true);
      setActionError(null);
      await congeService.validerDemande(selectedDemande.idDemandeConge, { justificatifFourni, estRemunere, derogationSoldeNegatif });
      toast({ title: 'Demande validée', message: `La demande #${selectedDemande.idDemandeConge} a été validée.`, type: 'success', duration: 3000 });
      setShowValiderModal(false);
      setShowDetailModal(false);
      setSelectedDemande(null);
      loadPlanning();
      if (onReload) onReload();
    } catch (err) {
      const msg = getErrorMessage(err, 'Erreur lors de la validation de la demande.');
      setActionError(msg);
      toast({ title: 'Validation impossible', message: msg, type: 'error', duration: 6000 });
    } finally {
      setActionLoading(false);
    }
  };

  const onConfirmRefus = async () => {
    if (!motifRefus.trim()) { setActionError('Le motif de refus est obligatoire.'); return; }
    try {
      setActionLoading(true);
      setActionError(null);
      await congeService.refuserDemande(selectedDemande.idDemandeConge, motifRefus.trim());
      toast({ title: 'Demande refusée', message: `La demande #${selectedDemande.idDemandeConge} a été refusée.`, type: 'success', duration: 3000 });
      setShowRefusModal(false);
      setShowDetailModal(false);
      setSelectedDemande(null);
      setMotifRefus('');
      loadPlanning();
      if (onReload) onReload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'Erreur lors du refus de la demande.'));
    } finally {
      setActionLoading(false);
    }
  };

  const onConfirmAnnuler = async () => {
    if (!selectedDemande) return;
    if (!motifAnnulation.trim()) {
      setActionError("Le motif d'annulation est obligatoire.");
      return;
    }
    try {
      setActionLoading(true);
      setActionError(null);
      await congeService.annulerDemande(selectedDemande.idDemandeConge, motifAnnulation.trim());
      toast({ title: 'Congé annulé', message: `La demande #${selectedDemande.idDemandeConge} a été annulée.`, type: 'success', duration: 3000 });
      setShowAnnulerModal(false);
      setShowDetailModal(false);
      setSelectedDemande(null);
      setMotifAnnulation('');
      loadPlanning();
      if (onReload) onReload();
    } catch (err) {
      setActionError(getErrorMessage(err, "Erreur lors de l'annulation de la demande."));
    } finally {
      setActionLoading(false);
    }
  };

  const handleModalSuccess = () => {
    setDemandeModalState({ isOpen: false, idEmploye: null, dateDebut: null, dateFin: null, initialDemande: null });
    loadPlanning();
    if (onReload) onReload();
  };

  // Rendu : synthèse, planning, tableau et modales.
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-4 py-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-fg)]">Planning d'équipe</h2>
          <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Congés validés et demandes en attente sur la période affichée.</p>
        </div>
        <Button variant="outline" size="sm" onClick={onOpenDemandes}>
          {nbEnAttente === null ? 'Voir les demandes' : `Demandes en attente : ${nbEnAttente}`}
        </Button>
      </div>

      {planningError && <Alert type="error">
        Le planning est indisponible. <Button variant="outline" size="xs" onClick={() => loadPlanning()}>Réessayer</Button>
      </Alert>}

      {/* Planning Gantt Interactif */}
      {!planningError && <SoldesGantt
        viewMode={viewMode}
        setViewMode={setViewMode}
        filterMode={filterMode}
        setFilterMode={setFilterMode}
        ganttData={ganttData}
        ganttRows={ganttRows}
        filteredGanttRows={filteredGanttRows}
        paginatedGanttRows={paginatedGanttRows}
        totalGanttItems={totalGanttItems}
        nbEmployesConcernes={nbEmployesConcernes}
        ganttSearch={ganttSearch}
        setGanttSearch={setGanttSearch}
        ganttPage={ganttPage}
        setGanttPage={setGanttPage}
        ganttPageSize={ganttPageSize}
        setGanttPageSize={setGanttPageSize}
        LANE_HEIGHT={LANE_HEIGHT}
        currentMonth={currentMonth}
        setCurrentMonth={setCurrentMonth}
        year={year}
        month={month}
        daysInMonth={daysInMonth}
        daysArray={daysArray}
        isMoisActuel={isMoisActuel}
        handlePrevMonth={handlePrevMonth}
        handleNextMonth={handleNextMonth}
        handleToday={handleToday}
        onMonthChange={handleMonthChange}
        weekDaysArray={weekDaysArray}
        weekStartStr={weekStartStr}
        weekEndStr={weekEndStr}
        isSemaineActuelle={isSemaineActuelle}
        handlePrevWeek={handlePrevWeek}
        handleNextWeek={handleNextWeek}
        handleTodayWeek={handleTodayWeek}
        onWeekDateChange={handleWeekDateChange}
        onReload={() => {
          loadPlanning();
          if (onReload) onReload();
        }}
        planningLoading={planningLoading}
        onCellClick={canWrite ? handleCellClick : undefined}
        onLeaveClick={handleLeaveClick}
      />}

      {!planningError && <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] px-4 py-2.5">
          <div className="flex min-w-0 items-center gap-2 text-[var(--color-fg)]">
            <CalendarDays size={15} className="shrink-0 text-[var(--color-brand-600)]" />
            <h3 className="text-xs font-bold">Absences sur la période affichée</h3>
            <Badge variant="default" size="xs" className="font-data">{totalTableItems}</Badge>
          </div>
          <Button
            type="button"
            variant="ghost"
            size="xs"
            className="shrink-0"
            onClick={() => setShowRecap(value => !value)}
            iconRight={showRecap ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
            aria-expanded={showRecap}
          >
            {showRecap ? 'Masquer la liste' : 'Afficher la liste'}
          </Button>
        </div>
        {showRecap && <SoldesTable
          ganttData={ganttData}
          paginatedTableData={paginatedTableData}
          totalTableItems={totalTableItems}
          tableSortField={tableSortField}
          tableSortOrder={tableSortOrder}
          handleTableSort={handleTableSort}
          tablePage={tablePage}
          setTablePage={setTablePage}
          tablePageSize={tablePageSize}
          setTablePageSize={setTablePageSize}
        />}
      </div>}

      {/* Modale de demande au clic sur une cellule ou modification */}
      {demandeModalState.isOpen && (
        <Suspense fallback={null}>
          <DemandeCongeModal
            isOpen={demandeModalState.isOpen}
            onClose={() =>
              setDemandeModalState({ isOpen: false, idEmploye: null, dateDebut: null, dateFin: null, initialDemande: null })
            }
            idEmploye={demandeModalState.idEmploye}
            initialDateDebut={demandeModalState.dateDebut}
            initialDateFin={demandeModalState.dateFin}
            initialDemande={demandeModalState.initialDemande}
            referentiels={referentiels}
            onSuccess={handleModalSuccess}
          />
        </Suspense>
      )}

      {/* Modale de détails et actions contextuelles (Valider, Refuser, Annuler) */}
      {(showDetailModal || showValiderModal || showRefusModal || showAnnulerModal) && (
        <Suspense fallback={null}>
          <ValidationModals
            selectedDemande={selectedDemande}
            showDetailModal={showDetailModal}
            onCloseDetail={closeDetail}
            onOpenValider={openValider}
            onOpenRefus={openRefus}
            onOpenModifier={openModifier}
            onOpenAnnuler={openAnnuler}

            showValiderModal={showValiderModal}
            onCloseValider={() => { setShowValiderModal(false); }}
            onConfirmValider={onConfirmValider}

            showRefusModal={showRefusModal}
            onCloseRefus={() => { setShowRefusModal(false); setMotifRefus(''); }}
            onConfirmRefus={onConfirmRefus}
            motifRefus={motifRefus}
            setMotifRefus={setMotifRefus}

            showAnnulerModal={showAnnulerModal}
            onCloseAnnuler={() => { setShowAnnulerModal(false); setMotifAnnulation(''); }}
            onConfirmAnnuler={onConfirmAnnuler}
            motifAnnulation={motifAnnulation}
            setMotifAnnulation={setMotifAnnulation}

            actionLoading={actionLoading}
            actionError={actionError}
          />
        </Suspense>
      )}
    </div>
  );
}
