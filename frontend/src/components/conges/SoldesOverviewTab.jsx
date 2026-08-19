import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';

// Hook Logique
import { useSoldesOverview } from '@/hooks/conges/useSoldesOverview';

// Sous-composants
import SoldesKpiBar from './soldes/SoldesKpiBar';
import SoldesGantt from './soldes/SoldesGantt';
import SoldesTable from './soldes/SoldesTable';

export default function SoldesOverviewTab({ loading, demandesGlobales = [], onReload }) {
  const {
    // Current Month State
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

    // Gantt Data & State
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
  if (loading) {
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

  return (
    <div className="space-y-6">
      {/* Barre de KPIs */}
      <SoldesKpiBar
        kpiEnAttente={kpiEnAttente}
        kpiAbsentsAuj={kpiAbsentsAuj}
        kpiValideCeMois={kpiValideCeMois}
        kpiTotalDemandes={kpiTotalDemandes}
      />

      {/* Planning Gantt */}
      <SoldesGantt
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
        onReload={onReload}
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
    </div>
  );
}