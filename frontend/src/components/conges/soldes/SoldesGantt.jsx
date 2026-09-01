import { Link } from 'react-router-dom';
import { Search, X, CalendarDays, ChevronLeft, ChevronRight, RefreshCw, ExternalLink, Calendar } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';

const formatDate = (timestamp) => {
  if (!timestamp) return '—';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

export default function SoldesGantt({
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
  currentMonth: _currentMonth,
  setCurrentMonth: _setCurrentMonth,
  year,
  month,
  daysInMonth,
  daysArray,
  isMoisActuel,
  handlePrevMonth,
  handleNextMonth,
  handleToday,
  onReload
}) {
  const { hasPermission } = useAuth();
  const canViewEmploye = hasPermission('EMPLOYE_READ');

  // Libellé du mois en français (ex: "Octobre 2026")
  const monthName = new Date(year, month - 1, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  const formattedMonthLabel = monthName.charAt(0).toUpperCase() + monthName.slice(1);

  return (
    <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-4">
      {/* En-tête du Planning */}
      <div className="flex flex-col gap-3 pb-3 border-b border-slate-100 dark:border-ink-800">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-2.5">
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-800 dark:text-white flex items-center gap-2">
              <CalendarDays size={15} className="text-brand-500" />
              Planning Mensuel des Absences
            </h3>
            {ganttRows.length > 0 && (
              <Badge variant="default" size="xs" className="font-bold">
                {nbEmployesConcernes} collaborateur{nbEmployesConcernes > 1 ? 's' : ''}
              </Badge>
            )}
          </div>

          <div className="flex items-center gap-2.5 flex-wrap">
            {/* Barre de recherche d'employé dans le Gantt */}
            <div className="relative flex items-center">
              <Search className="w-3.5 h-3.5 absolute left-2.5 text-slate-400 pointer-events-none" />
              <input
                type="text"
                value={ganttSearch}
                onChange={(e) => {
                  setGanttSearch(e.target.value);
                  setGanttPage(1);
                }}
                placeholder="Filtrer par collaborateur…"
                className="pl-8 pr-7 py-1 bg-slate-50 dark:bg-ink-950 border border-slate-200 dark:border-ink-700 text-xs text-slate-900 dark:text-white placeholder:text-slate-400 outline-none focus:border-brand-500 h-8 w-44 sm:w-56 transition-colors"
              />
              {ganttSearch && (
                <button
                  type="button"
                  onClick={() => {
                    setGanttSearch('');
                    setGanttPage(1);
                  }}
                  className="absolute right-2 text-slate-400 hover:text-slate-600 p-0.5"
                  title="Effacer la recherche"
                >
                  <X size={13} />
                </button>
              )}
            </div>

            {/* Sélecteur de mois compact unifié */}
            <div className="inline-flex items-center border border-slate-200 dark:border-ink-700 bg-slate-50 dark:bg-ink-950 h-8 text-xs">
              <button
                type="button"
                onClick={handlePrevMonth}
                className="px-2 h-full hover:bg-slate-200/60 dark:hover:bg-ink-800 transition-colors text-slate-700 dark:text-ink-200"
                title="Mois précédent"
                aria-label="Mois précédent"
              >
                <ChevronLeft size={16} />
              </button>
              <span className="px-3 font-bold text-slate-800 dark:text-white min-w-[120px] text-center select-none">
                {formattedMonthLabel}
              </span>
              <button
                type="button"
                onClick={handleNextMonth}
                className="px-2 h-full hover:bg-slate-200/60 dark:hover:bg-ink-800 transition-colors text-slate-700 dark:text-ink-200"
                title="Mois suivant"
                aria-label="Mois suivant"
              >
                <ChevronRight size={16} />
              </button>
            </div>

            {!isMoisActuel && (
              <Button
                variant="outline"
                size="xs"
                onClick={handleToday}
                className="h-8 text-xs font-semibold"
              >
                Aujourd'hui
              </Button>
            )}

            <Button
              variant="outline"
              size="xs"
              iconLeft={<RefreshCw size={13} />}
              onClick={onReload}
              className="h-8 text-xs font-semibold"
            >
              Actualiser
            </Button>
          </div>
        </div>

        {/* Légende douce et compacte */}
        {ganttData.length > 0 && (
          <div className="flex items-center flex-wrap gap-x-6 gap-y-1.5 text-[11px] text-slate-500 dark:text-ink-400 pt-1">
            <span className="flex items-center gap-1.5 font-medium">
              <span className="inline-block w-3 h-3 bg-brand-500 border border-brand-600 shadow-2xs" />
              Congé validé
            </span>
            <span className="flex items-center gap-1.5 font-medium">
              <span className="inline-block w-3 h-3 bg-amber-400 border border-amber-500 shadow-2xs" />
              En attente de validation
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-3 h-3 bg-slate-100 dark:bg-ink-800 border border-slate-200 dark:border-ink-700" />
              Week-end
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-3 h-3 bg-brand-50 dark:bg-brand-950/40 border border-brand-300" />
              Aujourd'hui
            </span>
          </div>
        )}
      </div>

      {/* Grille Gantt */}
      <div className="overflow-x-auto border border-slate-200/80 dark:border-ink-800">
        {ganttData.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={Calendar}
              title="Aucun congé prévu"
              description="Il n'y a aucune absence enregistrée sur ce mois."
            />
          </div>
        ) : filteredGanttRows.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={Search}
              title="Aucun collaborateur trouvé"
              description={`Aucun résultat ne correspond à "${ganttSearch}".`}
            />
          </div>
        ) : (
          <div className="min-w-[850px]">
            {/* Header de la grille : Jours du mois */}
            <div className="flex bg-slate-100/75 dark:bg-ink-800/60 border-b border-slate-200/80 dark:border-ink-800">
              <div className="w-60 shrink-0 p-2.5 text-[11px] font-bold uppercase tracking-wider text-slate-500 border-r border-slate-200/80 dark:border-ink-800">
                Collaborateur
              </div>
              <div className="flex-1 flex">
                {daysArray.map((day) => {
                  const dateObj = new Date(year, month - 1, day);
                  const dayOfWeek = dateObj.toLocaleDateString('fr-FR', { weekday: 'short' }).charAt(0).toUpperCase();
                  const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                  const isToday = dateObj.toDateString() === new Date().toDateString();

                  return (
                    <div
                      key={day}
                      title={dateObj.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}
                      className={`flex-1 min-w-[26px] text-center py-1.5 text-xs font-data border-r border-slate-200/60 dark:border-ink-800/60 last:border-0 ${
                        isToday
                          ? 'bg-brand-50 dark:bg-brand-950/40 text-brand-700 dark:text-brand-300 font-bold border-b-2 border-b-brand-500'
                          : isWeekend
                            ? 'bg-slate-50/70 dark:bg-ink-950/40 text-slate-400'
                            : 'text-slate-600 dark:text-ink-300'
                      }`}
                    >
                      <div className={`text-[10px] ${isToday ? 'font-bold' : 'opacity-60'}`}>{dayOfWeek}</div>
                      <div>{day}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Lignes du Gantt par collaborateur */}
            {paginatedGanttRows.map((row, idx) => {
              const rowHeight = Math.max(row.laneCount, 1) * LANE_HEIGHT;
              return (
                <div
                  key={idx}
                  className="flex border-b border-slate-100 dark:border-ink-800/60 last:border-0 hover:bg-slate-50/60 dark:hover:bg-ink-800/30 transition-colors"
                >
                  <div
                    className="w-60 shrink-0 p-2.5 text-xs font-bold text-slate-900 dark:text-white border-r border-slate-200/80 dark:border-ink-800 truncate flex items-center justify-between group"
                    title={`Voir la fiche de ${row.employe}`}
                    style={{ minHeight: `${rowHeight + 8}px` }}
                  >
                    {row.idEmploye && canViewEmploye ? (
                      <Link
                        to={`/employes/${row.idEmploye}?tab=conges`}
                        className="hover:text-brand-600 hover:underline truncate flex items-center gap-1.5 font-bold text-slate-900 dark:text-white"
                      >
                        <span className="truncate">{row.employe}</span>
                        <ExternalLink size={12} className="opacity-0 group-hover:opacity-100 transition-opacity shrink-0 text-brand-600" />
                      </Link>
                    ) : (
                      <span className="truncate">{row.employe}</span>
                    )}
                    {row.items.length > 1 && (
                      <span className="ml-1 text-[10px] font-normal text-slate-400 shrink-0">
                        ({row.items.length})
                      </span>
                    )}
                  </div>

                  <div className="flex-1 flex relative" style={{ minHeight: `${rowHeight + 8}px` }}>
                    {daysArray.map((day) => {
                      const dateObj = new Date(year, month - 1, day);
                      const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                      const isToday = dateObj.toDateString() === new Date().toDateString();

                      return (
                        <div
                          key={day}
                          className={`flex-1 min-w-[26px] border-r border-slate-100 dark:border-ink-800/40 last:border-0 ${
                            isWeekend ? 'bg-slate-50/50 dark:bg-ink-950/20' : ''
                          } ${isToday ? 'bg-brand-50/30 dark:bg-brand-950/20' : ''}`}
                        />
                      );
                    })}

                    {row.items.map((item) => (
                      <div
                        key={item.id}
                        role="img"
                        aria-label={`${row.employe} — ${item.type}, ${item.statut === 'EN_ATTENTE' ? 'en attente' : 'validé'}, ${item.dureeJours} jour(s)`}
                        className={`absolute flex items-center overflow-hidden border shadow-xs cursor-help ${
                          (item.endDay - item.startDay + 1) >= 3 ? 'px-2' : 'px-0.5 justify-center'
                        } ${
                          item.statut === 'EN_ATTENTE'
                            ? 'bg-amber-400 border-amber-500 text-slate-900 font-semibold'
                            : 'bg-brand-500 border-brand-600 text-white font-semibold'
                        }`}
                        style={{
                          left: `${((item.startDay - 1) / daysInMonth) * 100}%`,
                          width: `${((item.endDay - item.startDay + 1) / daysInMonth) * 100}%`,
                          top: `${4 + item.laneIndex * LANE_HEIGHT}px`,
                          height: `${LANE_HEIGHT - 6}px`,
                        }}
                        title={`${item.type} — ${item.statut === 'EN_ATTENTE' ? 'En attente de validation' : 'Validé'} (${item.dureeJours} jour${item.dureeJours > 1 ? 's' : ''}) du ${formatDate(item.originalStart)} au ${formatDate(item.originalEnd)}`}
                      >
                        <span className="text-[11px] truncate">
                          {(item.endDay - item.startDay + 1) >= 3
                            ? `${item.type}${item.statut === 'EN_ATTENTE' ? ' (en attente)' : ''}`
                            : item.type.slice(0, 3)}
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Pagination du Gantt */}
      {totalGanttItems > 0 && (
        <Pagination
          currentPage={ganttPage}
          totalItems={totalGanttItems}
          pageSize={ganttPageSize}
          pageSizeOptions={[5, 10, 20, 50]}
          onPageChange={setGanttPage}
          onPageSizeChange={(size) => {
            setGanttPageSize(size);
            setGanttPage(1);
          }}
          className="bg-transparent border-t-0 pt-1"
        />
      )}
    </div>
  );
}
