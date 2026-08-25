import { Link } from 'react-router-dom';
import { Search, X, CalendarClock, ChevronLeft, ChevronRight, RefreshCw, ExternalLink } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';

const formatDate = (timestamp) => {
  if (!timestamp) return '-';
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
  onReload
}) {
  const { hasPermission } = useAuth();
  const canViewEmploye = hasPermission('EMPLOYE_READ');

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-col gap-3 w-full">
          <div className="flex items-center justify-between w-full flex-wrap gap-3">
            <div className="flex items-center gap-2">
              <CardTitle>Planning des absences</CardTitle>
              {ganttRows.length > 0 && (
                <Badge variant="brand">
                  {nbEmployesConcernes} employé{nbEmployesConcernes > 1 ? 's' : ''} concerné{nbEmployesConcernes > 1 ? 's' : ''}
                </Badge>
              )}
            </div>
            <div className="flex items-center gap-2 flex-wrap">
              {/* Barre de recherche d'employé dans le Gantt */}
              <div className="relative flex items-center">
                <Search className="w-3.5 h-3.5 absolute left-2.5 text-[var(--color-fg-muted)] pointer-events-none" />
                <input
                  type="text"
                  value={ganttSearch}
                  onChange={(e) => {
                    setGanttSearch(e.target.value);
                    setGanttPage(1);
                  }}
                  placeholder="Filtrer par employé…"
                  className="pl-8 pr-7 py-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg)] placeholder:text-[var(--color-fg-subtle)] outline-none focus:border-[var(--color-brand-500)] rounded-none h-8 w-44 sm:w-56"
                />
                {ganttSearch && (
                  <button
                    type="button"
                    onClick={() => {
                      setGanttSearch('');
                      setGanttPage(1);
                    }}
                    className="absolute right-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-0.5"
                    title="Effacer la recherche"
                  >
                    <X size={14} />
                  </button>
                )}
              </div>

              {!isMoisActuel && (
                <Button
                  variant="ghost"
                  size="sm"
                  iconLeft={<CalendarClock size={16} />}
                  onClick={handleToday}
                >
                  Aujourd'hui
                </Button>
              )}
              <div className="flex items-center rounded-none border border-[var(--color-border)] shadow-sm bg-[var(--color-surface)] overflow-hidden">
                <Button variant="ghost" size="sm" iconLeft={<ChevronLeft size={18} />} onClick={handlePrevMonth} className="!px-2 !rounded-none !border-0" title="Mois précédent" aria-label="Mois précédent" />
                <div className="w-[1px] h-5 bg-[var(--color-border-subtle)]" />
                <Input
                  type="month"
                  value={currentMonth}
                  onChange={(e) => setCurrentMonth(e.target.value)}
                  aria-label="Sélectionner le mois affiché"
                  className="w-36 !border-0 !shadow-none !bg-transparent text-center font-semibold focus:!ring-0 rounded-none h-8"
                />
                <div className="w-[1px] h-5 bg-[var(--color-border-subtle)]" />
                <Button variant="ghost" size="sm" iconLeft={<ChevronRight size={18} />} onClick={handleNextMonth} className="!px-2 !rounded-none !border-0" title="Mois suivant" aria-label="Mois suivant" />
              </div>
              <Button variant="outline" size="sm" iconLeft={<RefreshCw size={16}/>} onClick={onReload}>
                Actualiser
              </Button>
            </div>
          </div>

          {/* Légende */}
          {ganttData.length > 0 && (
            <div className="flex items-center flex-wrap gap-x-5 gap-y-1.5 text-xs text-[var(--color-fg-muted)] pt-1 border-t border-[var(--color-border-subtle)]">
              <span className="flex items-center gap-1.5">
                <span className="inline-block w-3 h-3 shrink-0 bg-[var(--color-brand-500)] border border-[var(--color-brand-600)]" />
                Congé validé
              </span>
              <span className="flex items-center gap-1.5">
                <span
                  className="inline-block w-3 h-3 shrink-0 bg-warning-500 border border-warning-600"
                  style={{ backgroundImage: 'repeating-linear-gradient(45deg, rgba(255,255,255,0.35), rgba(255,255,255,0.35) 2px, transparent 2px, transparent 4px)' }}
                />
                En attente de validation
              </span>
              <span className="flex items-center gap-1.5">
                <span className="inline-block w-3 h-3 shrink-0 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]" />
                Week-end
              </span>
              <span className="flex items-center gap-1.5">
                <span className="inline-block w-3 h-3 shrink-0 bg-[var(--color-brand-100)] border border-[var(--color-brand-500)]" />
                Aujourd'hui
              </span>
            </div>
          )}
        </div>
      </CardHeader>
      <CardBody className="p-0 overflow-x-auto">
        {ganttData.length === 0 ? (
          <EmptyState title="Aucun congé prévu" description="Il n'y a pas d'absences prévues sur ce mois." />
        ) : filteredGanttRows.length === 0 ? (
          <EmptyState title="Aucun résultat" description={`Aucun employé trouvé pour "${ganttSearch}"`} />
        ) : (
          <div className="min-w-[800px] border-b border-[var(--color-border-subtle)]">
            <div className="flex border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              <div className="w-48 shrink-0 p-3 text-xs font-bold uppercase text-[var(--color-fg-muted)] border-r border-[var(--color-border-subtle)]">
                Employé
              </div>
              <div className="flex-1 flex">
                {daysArray.map((day) => {
                  const dateObj = new Date(year, month - 1, day);
                  const dayOfWeek = dateObj.toLocaleDateString('fr-FR', { weekday: 'short' }).charAt(0).toUpperCase();
                  const isToday = dateObj.toDateString() === new Date().toDateString();

                  return (
                    <div
                      key={day}
                      title={dateObj.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}
                      className={`flex-1 min-w-[26px] text-center py-1.5 text-xs font-data border-r border-[var(--color-border-subtle)] last:border-0 ${isToday ? 'bg-[var(--color-brand-100)] text-[var(--color-brand-700)] border-b-2 border-b-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}`}
                    >
                      <div className={`font-bold text-[10px] ${isToday ? 'opacity-100' : 'opacity-50'}`}>{dayOfWeek}</div>
                      <div className={isToday ? 'font-bold' : ''}>{day}</div>
                    </div>
                  );
                })}
              </div>
            </div>

            {paginatedGanttRows.map((row, idx) => {
              const rowHeight = Math.max(row.laneCount, 1) * LANE_HEIGHT;
              return (
                <div key={idx} className="flex border-b border-[var(--color-border-subtle)] last:border-0 hover:bg-[var(--color-surface-hover)]">
                  <div
                    className="w-48 shrink-0 p-3 text-sm font-semibold text-[var(--color-fg)] border-r border-[var(--color-border-subtle)] truncate flex items-center justify-between group"
                    title={`Voir la fiche employé de ${row.employe}`}
                    style={{ minHeight: `${rowHeight + 8}px` }}
                  >
                    {row.idEmploye && canViewEmploye ? (
                      <Link
                        to={`/employes/${row.idEmploye}?tab=conges`}
                        className="hover:text-[var(--color-brand-500)] hover:underline truncate flex items-center gap-1 font-semibold text-[var(--color-fg)]"
                      >
                        <span className="truncate">{row.employe}</span>
                        <ExternalLink size={12} className="opacity-0 group-hover:opacity-100 transition-opacity shrink-0 text-[var(--color-brand-500)]" />
                      </Link>
                    ) : (
                      <span className="truncate">{row.employe}</span>
                    )}
                    {row.items.length > 1 && (
                      <span className="ml-1.5 text-[10px] font-normal text-[var(--color-fg-subtle)] shrink-0">
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
                        <div key={day} className={`flex-1 min-w-[26px] border-r border-[var(--color-border-subtle)] last:border-0 ${isWeekend ? 'bg-[var(--color-bg-subtle)]/50' : ''} ${isToday ? 'bg-[var(--color-brand-50)]/50 border-x-[var(--color-brand-200)] shadow-inner' : ''}`}>
                        </div>
                      );
                    })}
                    {row.items.map((item) => (
                      <div
                        key={item.id}
                        role="img"
                        aria-label={`${row.employe} — ${item.type}, ${item.statut === 'EN_ATTENTE' ? 'en attente' : 'validé'}, ${item.dureeJours} jour(s) du ${formatDate(item.originalStart)} au ${formatDate(item.originalEnd)}`}
                        className={`absolute rounded-none shadow-sm flex items-center overflow-hidden border hover:brightness-110 cursor-help ${
                          (item.endDay - item.startDay + 1) >= 3 ? 'px-2' : 'px-0.5 justify-center'
                        } ${
                          item.statut === 'EN_ATTENTE'
                            ? 'bg-warning-500 border-warning-600 text-white'
                            : 'bg-[var(--color-brand-500)] border-[var(--color-brand-600)] text-white'
                        }`}
                        style={{
                          left: `${((item.startDay - 1) / daysInMonth) * 100}%`,
                          width: `${((item.endDay - item.startDay + 1) / daysInMonth) * 100}%`,
                          top: `${4 + item.laneIndex * LANE_HEIGHT}px`,
                          height: `${LANE_HEIGHT - 6}px`,
                          backgroundImage: item.statut === 'EN_ATTENTE'
                            ? 'repeating-linear-gradient(45deg, rgba(255,255,255,0.15), rgba(255,255,255,0.15) 10px, transparent 10px, transparent 20px)'
                            : 'none'
                        }}
                        title={`${item.type} — ${item.statut === 'EN_ATTENTE' ? 'En attente' : 'Validé'} (${item.dureeJours} jour${item.dureeJours > 1 ? 's' : ''})`}
                      >
                        <span className="text-[11px] font-bold truncate drop-shadow-md">
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
      </CardBody>

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
        />
      )}
    </Card>
  );
}
