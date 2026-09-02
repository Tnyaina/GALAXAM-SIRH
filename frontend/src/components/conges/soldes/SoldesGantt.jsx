import { Link } from 'react-router-dom';
import { Search, X, CalendarDays, ChevronLeft, ChevronRight, RefreshCw, Calendar } from 'lucide-react';
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
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
      {/* En-tête du Planning */}
      <div className="flex flex-col gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-2.5">
            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
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
              <Search className="w-3.5 h-3.5 absolute left-2.5 text-[var(--color-fg-subtle)] pointer-events-none" />
              <input
                type="text"
                value={ganttSearch}
                onChange={(e) => {
                  setGanttSearch(e.target.value);
                  setGanttPage(1);
                }}
                placeholder="Filtrer par collaborateur…"
                className="pl-8 pr-7 py-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] outline-none focus:border-brand-500 h-8 w-44 sm:w-56 transition-colors"
              />
              {ganttSearch && (
                <button
                  type="button"
                  onClick={() => {
                    setGanttSearch('');
                    setGanttPage(1);
                  }}
                  className="absolute right-2 text-[var(--color-fg-subtle)] hover:text-[var(--color-fg)] p-0.5 cursor-pointer"
                  title="Effacer la recherche"
                >
                  <X size={13} />
                </button>
              )}
            </div>

            {/* Sélecteur de mois compact unifié */}
            <div className="inline-flex items-center border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] h-8 text-xs">
              <button
                type="button"
                onClick={handlePrevMonth}
                className="px-2 h-full hover:bg-[var(--color-surface-hover)] transition-colors text-[var(--color-fg-muted)] cursor-pointer"
                title="Mois précédent"
                aria-label="Mois précédent"
              >
                <ChevronLeft size={16} />
              </button>
              <span className="px-3 font-bold text-[var(--color-fg)] min-w-[120px] text-center select-none">
                {formattedMonthLabel}
              </span>
              <button
                type="button"
                onClick={handleNextMonth}
                className="px-2 h-full hover:bg-[var(--color-surface-hover)] transition-colors text-[var(--color-fg-muted)] cursor-pointer"
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
          <div className="flex items-center flex-wrap gap-x-5 gap-y-1.5 text-[11px] text-[var(--color-fg-muted)] pt-0.5">
            <span className="flex items-center gap-1.5 font-medium">
              <span className="inline-block w-2.5 h-2.5 bg-brand-600 rounded-xs shadow-2xs" />
              Congé validé
            </span>
            <span className="flex items-center gap-1.5 font-medium">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-warning-500)] rounded-xs shadow-2xs" />
              En attente de validation
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]" />
              Week-end
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 bg-brand-500/20 border border-brand-500/40" />
              Aujourd'hui
            </span>
          </div>
        )}
      </div>

      {/* Grille Gantt */}
      <div className="overflow-x-auto border border-[var(--color-border)] shadow-2xs">
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
            <div className="flex bg-[var(--color-bg-subtle)] border-b border-[var(--color-border)] select-none">
              <div className="w-56 shrink-0 p-2 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-r border-[var(--color-border)] flex items-center">
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
                      className={`flex-1 min-w-[24px] text-center py-1 text-xs font-data border-r border-[var(--color-border-subtle)] last:border-0 ${
                        isToday
                          ? 'bg-brand-500/15 text-brand-700 dark:text-brand-300 font-bold border-b-2 border-b-brand-600'
                          : isWeekend
                            ? 'bg-[var(--color-bg-subtle)]/80 text-[var(--color-fg-subtle)]'
                            : 'text-[var(--color-fg)]'
                      }`}
                    >
                      <div className={`text-[9.5px] ${isToday ? 'font-bold' : 'opacity-60'}`}>{dayOfWeek}</div>
                      <div className="text-[11px] leading-tight font-medium">{day}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* Corps du Gantt */}
            <div className="divide-y divide-[var(--color-border-subtle)] bg-[var(--color-surface)]">
              {paginatedGanttRows.map((row) => {
                const totalLanes = row.lanes?.length || 1;
                const rowHeight = Math.max(LANE_HEIGHT, totalLanes * LANE_HEIGHT + 6);

                return (
                  <div
                    key={row.idEmploye}
                    className="flex hover:bg-[var(--color-surface-hover)] transition-colors group"
                    style={{ minHeight: `${rowHeight}px` }}
                  >
                    {/* Colonne Employé */}
                    <div className="w-56 shrink-0 px-2.5 py-1.5 border-r border-[var(--color-border)] flex items-center justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        {canViewEmploye ? (
                          <Link
                            to={`/employes/${row.idEmploye}?tab=conges`}
                            className="font-bold text-xs text-[var(--color-fg)] hover:text-brand-600 truncate block transition-colors leading-snug"
                            title={`Consulter le dossier congé de ${row.employe}`}
                          >
                            {row.employe}
                          </Link>
                        ) : (
                          <span className="font-bold text-xs text-[var(--color-fg)] truncate block leading-snug">
                            {row.employe}
                          </span>
                        )}
                        <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono block truncate">
                          {row.departement}
                        </span>
                      </div>
                      <span className="text-[10px] font-mono font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] px-1.5 py-0.5 shrink-0 border border-[var(--color-border-subtle)]">
                        {row.totalPrisMois}j
                      </span>
                    </div>

                    {/* Grille Jours + Barres */}
                    <div className="flex-1 relative flex">
                      {daysArray.map((day) => {
                        const dateObj = new Date(year, month - 1, day);
                        const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                        const isToday = dateObj.toDateString() === new Date().toDateString();

                        return (
                          <div
                            key={day}
                            className={`flex-1 min-w-[24px] border-r border-[var(--color-border-subtle)]/40 last:border-0 ${
                              isWeekend ? 'bg-[var(--color-bg-subtle)]/40' : ''
                            } ${isToday ? 'bg-brand-500/5' : ''}`}
                          />
                        );
                      })}

                      {row.items.map((item) => {
                        const isDemi = !!item.estDemiJournee;
                        const isMatin = item.momentDemiJournee === 'MATIN';
                        const daySpan = isDemi ? 0.5 : (item.endDay - item.startDay + 1);
                        const leftOffset = isDemi && !isMatin ? (item.startDay - 1 + 0.5) : (item.startDay - 1);
                        const dureeLabel = isDemi
                          ? (isMatin ? '0.5j Matin' : '0.5j Après-midi')
                          : `${item.calcJours || item.dureeJours || (item.endDay - item.startDay + 1)}j`;

                        return (
                          <div
                            key={item.id}
                            role="img"
                            aria-label={`${row.employe} — ${item.type}, ${item.statut === 'EN_ATTENTE' ? 'en attente' : 'validé'}, ${dureeLabel}`}
                            className={`absolute flex items-center overflow-hidden rounded-xs border shadow-xs cursor-help transition-transform hover:scale-[1.01] ${
                              daySpan >= 2 ? 'px-2' : 'px-1 justify-center'
                            } ${
                              item.statut === 'EN_ATTENTE'
                                ? 'bg-[var(--color-warning-500)] border-[var(--color-warning-500)] text-white font-semibold'
                                : 'bg-brand-600 border-brand-700 text-white font-semibold'
                            }`}
                            style={{
                              left: `${(leftOffset / daysInMonth) * 100}%`,
                              width: `${(daySpan / daysInMonth) * 100}%`,
                              top: `${3 + item.laneIndex * LANE_HEIGHT}px`,
                              height: `${LANE_HEIGHT - 6}px`,
                            }}
                            title={`${item.type} — ${item.statut === 'EN_ATTENTE' ? 'En attente de validation' : 'Validé'} (${dureeLabel}) du ${formatDate(item.originalStart)} au ${formatDate(item.originalEnd)}`}
                          >
                            <span className="text-[10.5px] truncate tracking-tight font-medium flex items-center gap-1">
                              <span>{item.type}</span>
                              <span className="font-mono text-[9.5px] opacity-90 font-bold">({dureeLabel})</span>
                            </span>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                );
              })}
            </div>
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
