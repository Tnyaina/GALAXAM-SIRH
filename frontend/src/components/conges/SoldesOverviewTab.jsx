import { useState } from 'react';
import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Hook Logique
import { useSoldesOverview } from '@/hooks/conges/useSoldesOverview';

// Sous-composants
import SoldesKpiBar from './soldes/SoldesKpiBar';
import SoldesGantt from './soldes/SoldesGantt';
import SoldesTable from './soldes/SoldesTable';
import DemandeCongeModal from './DemandeCongeModal';

export default function SoldesOverviewTab({ loading, demandesGlobales = [], referentiels, onReload }) {
  // Modal de création directe au clic sur le planning
  const [demandeModalState, setDemandeModalState] = useState({
    isOpen: false,
    idEmploye: null,
    dateDebut: null,
    dateFin: null
  });

  const {
    // Mode et Filtres
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

    // Navigation Semaine
    weekDaysArray,
    weekStartStr,
    weekEndStr,
    isSemaineActuelle,
    handlePrevWeek,
    handleNextWeek,
    handleTodayWeek,

    // Gantt Data & State
    planningLoading,
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
    setTablePageSize,

    // KPIs
    kpiEnAttente,
    kpiValideCeMois,
    kpiAbsentsAuj,
    kpiTotalDemandes
  } = useSoldesOverview(demandesGlobales);

  // État de chargement initial (Skeletons)
  if (loading && !planningLoading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
        <SkeletonCard />
        <SkeletonTable rows={5} />
      </div>
    );
  }

  const handleCellClick = (idEmploye, dateDebut, dateFin) => {
    setDemandeModalState({
      isOpen: true,
      idEmploye: idEmploye ? Number(idEmploye) : null,
      dateDebut: dateDebut,
      dateFin: dateFin || dateDebut
    });
  };

  const handleModalSuccess = () => {
    loadPlanning();
    if (onReload) onReload();
  };

  return (
    <div className="space-y-6">
      {/* Barre de KPIs */}
      <SoldesKpiBar
        kpiEnAttente={kpiEnAttente}
        kpiAbsentsAuj={kpiAbsentsAuj}
        kpiValideCeMois={kpiValideCeMois}
        kpiTotalDemandes={kpiTotalDemandes}
      />

      {/* Planning Gantt Interactif */}
      <SoldesGantt
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
        weekDaysArray={weekDaysArray}
        weekStartStr={weekStartStr}
        weekEndStr={weekEndStr}
        isSemaineActuelle={isSemaineActuelle}
        handlePrevWeek={handlePrevWeek}
        handleNextWeek={handleNextWeek}
        handleTodayWeek={handleTodayWeek}
        onReload={() => {
          loadPlanning();
          if (onReload) onReload();
        }}
        planningLoading={planningLoading}
        onCellClick={handleCellClick}
      />

      {/* Tableau Récapitulatif */}
      <SoldesTable
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
      />

      {/* Modale de demande au clic sur une cellule */}
      {demandeModalState.isOpen && (
        <DemandeCongeModal
          isOpen={demandeModalState.isOpen}
          onClose={() =>
            setDemandeModalState({ isOpen: false, idEmploye: null, dateDebut: null, dateFin: null })
          }
          idEmploye={demandeModalState.idEmploye}
          initialDateDebut={demandeModalState.dateDebut}
          initialDateFin={demandeModalState.dateFin}
          referentiels={referentiels}
          onSuccess={handleModalSuccess}
        />
      )}
    </div>
  );
}