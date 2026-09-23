import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { 
  Search, CalendarDays, ChevronLeft, ChevronRight, RefreshCw,
  Calendar as CalendarIcon, Users, UserCheck, Plus
} from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Pagination from '@/components/ui/navigation/Pagination';
import { useAuth } from '@/context/AuthContext';
import { fmtDate } from '@/utils/dateUtils';

const formatDate = (timestamp) => fmtDate(timestamp);

const getLeaveTooltip = (employeNom, item) => {
  const isDemi = Boolean(item.estDemiJournee);
  const isMatin = item.momentDemiJournee === 'MATIN';
  const dureeLabel = isDemi ? (isMatin ? '0.5j Matin' : '0.5j A-M') : `${item.dureeJours}j`;
  const isEnAttente = item.statut === 'EN_ATTENTE' || item.statut === 'SOUMIS';
  return `${employeNom} — ${item.typeLibelle || item.type} (${dureeLabel})\nStatut : ${isEnAttente ? 'En attente de validation' : 'Validé'}\nPériode : du ${formatDate(item.originalStart)} au ${formatDate(item.originalEnd)}`;
};

export default function SoldesGantt({
  viewMode = 'MOIS',
  setViewMode,
  filterMode = 'TOUS',
  setFilterMode,
  ganttRows = [],
  filteredGanttRows = [],
  paginatedGanttRows = [],
  totalGanttItems = 0,
  nbEmployesConcernes: _nbEmployesConcernes = 0,
  ganttSearch = '',
  setGanttSearch,
  ganttPage = 1,
  setGanttPage,
  ganttPageSize = 10,
  setGanttPageSize,
  LANE_HEIGHT = 36,
  year,
  month,
  daysArray = [],
  isMoisActuel,
  handlePrevMonth,
  handleNextMonth,
  handleToday,
  weekDaysArray = [],
  weekStartStr,
  weekEndStr,
  isSemaineActuelle,
  handlePrevWeek,
  handleNextWeek,
  handleTodayWeek,
  onReload,
  onCellClick,
  onLeaveClick,
  planningLoading = false
}) {
  // Contexte
  const { hasPermission } = useAuth();
  const canViewEmploye = hasPermission('EMPLOYE_READ');

  // État de glisser-déposer (drag-to-select) pour choisir directement un intervalle
  // État local : interaction de glisser-déposer dans le planning.
  const [dragState, setDragState] = useState({
    isDragging: false,
    idEmploye: null,
    startIndex: null,
    currentIndex: null,
    startDate: null,
    currentDate: null
  });

  // Gestion du relâchement de la souris n'importe où sur l'écran
  // Effet : réinitialise l'interaction lorsque le planning change.
  useEffect(() => {
    const handleGlobalMouseUp = () => {
      if (dragState.isDragging) {
        if (dragState.idEmploye && dragState.startDate && dragState.currentDate) {
          const d1 = dragState.startDate;
          const d2 = dragState.currentDate;
          const minDate = d1 <= d2 ? d1 : d2;
          const maxDate = d1 <= d2 ? d2 : d1;
          if (onCellClick) {
            onCellClick(dragState.idEmploye, minDate, maxDate);
          }
        }
        setDragState({
          isDragging: false,
          idEmploye: null,
          startIndex: null,
          currentIndex: null,
          startDate: null,
          currentDate: null
        });
      }
    };

    window.addEventListener('mouseup', handleGlobalMouseUp);
    return () => window.removeEventListener('mouseup', handleGlobalMouseUp);
  }, [dragState, onCellClick]);

  const handleCellMouseDown = (idEmploye, index, dateStr, e) => {
    if (e.button !== 0) return; // Uniquement clic gauche
    e.preventDefault();
    setDragState({
      isDragging: true,
      idEmploye,
      startIndex: index,
      currentIndex: index,
      startDate: dateStr,
      currentDate: dateStr
    });
  };

  const handleCellMouseEnter = (idEmploye, index, dateStr) => {
    if (dragState.isDragging && dragState.idEmploye === idEmploye) {
      setDragState((prev) => ({
        ...prev,
        currentIndex: index,
        currentDate: dateStr
      }));
    }
  };

  const handleCellMouseUp = (idEmploye, _index, dateStr, e) => {
    if (e.button !== 0) return;
    if (dragState.isDragging && dragState.idEmploye === idEmploye) {
      e.stopPropagation();
      const d1 = dragState.startDate;
      const d2 = dateStr || dragState.currentDate;
      const minDate = d1 <= d2 ? d1 : d2;
      const maxDate = d1 <= d2 ? d2 : d1;
      setDragState({
        isDragging: false,
        idEmploye: null,
        startIndex: null,
        currentIndex: null,
        startDate: null,
        currentDate: null
      });
      if (onCellClick) {
        onCellClick(idEmploye, minDate, maxDate);
      }
    }
  };

  // Libellé du mois en français
  const monthName = new Date(year, (month || 1) - 1, 1).toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  const formattedMonthLabel = monthName.charAt(0).toUpperCase() + monthName.slice(1);

  // Libellé de la semaine en français
  const formattedWeekLabel = weekStartStr && weekEndStr ? (() => {
    const d1 = new Date(weekStartStr);
    const d2 = new Date(weekEndStr);
    const j1 = d1.getDate();
    const m1 = d1.toLocaleDateString('fr-FR', { month: 'short' });
    const j2 = d2.getDate();
    const m2 = d2.toLocaleDateString('fr-FR', { month: 'short' });
    const y = d2.getFullYear();
    return `${j1} ${m1} — ${j2} ${m2} ${y}`;
  })() : '';

  // Rendu
  return (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4 select-none">
      {/* ── En-tête & Barre d'outils du Planning ── */}
      <div className="flex flex-col gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
        <div className="flex items-center justify-between flex-wrap gap-3">
          {/* Titre et Toggles de Vues (Sans emoji) */}
          <div className="flex items-center gap-3 flex-wrap">
            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
              <CalendarDays className="w-4 h-4 text-brand-600" />
              Planning des Collaborateurs
            </h3>

            {/* Toggle Vue : Mois vs Semaine */}
            <div className="inline-flex items-center bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setViewMode('MOIS')}
                className={`px-2.5 py-1 font-semibold transition-all cursor-pointer flex items-center gap-1.5 ${
                  viewMode === 'MOIS'
                    ? 'bg-brand-600 text-white shadow-xs'
                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                }`}
              >
                <CalendarIcon className="w-3.5 h-3.5" />
                <span>Vue Mois</span>
              </button>
              <button
                type="button"
                onClick={() => setViewMode('SEMAINE')}
                className={`px-2.5 py-1 font-semibold transition-all cursor-pointer flex items-center gap-1.5 ${
                  viewMode === 'SEMAINE'
                    ? 'bg-brand-600 text-white shadow-xs'
                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                }`}
              >
                <CalendarDays className="w-3.5 h-3.5" />
                <span>Vue Semaine</span>
              </button>
            </div>

            {/* Toggle Filtre : Tous vs Absents */}
            <div className="inline-flex items-center bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-0.5 text-xs">
              <button
                type="button"
                onClick={() => setFilterMode('TOUS')}
                className={`px-2.5 py-1 font-semibold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filterMode === 'TOUS'
                    ? 'bg-[var(--color-surface)] text-[var(--color-fg)] border border-[var(--color-border-subtle)] shadow-xs'
                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                }`}
                title="Afficher tous les collaborateurs actifs même sans congé"
              >
                <Users className="w-3.5 h-3.5" />
                <span>Tous</span>
              </button>
              <button
                type="button"
                onClick={() => setFilterMode('ABSENTS')}
                className={`px-2.5 py-1 font-semibold transition-all cursor-pointer flex items-center gap-1.5 ${
                  filterMode === 'ABSENTS'
                    ? 'bg-[var(--color-surface)] text-[var(--color-fg)] border border-[var(--color-border-subtle)] shadow-xs'
                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                }`}
                title="Afficher uniquement les collaborateurs ayant au moins une absence"
              >
                <UserCheck className="w-3.5 h-3.5" />
                <span>Absents</span>
              </button>
            </div>

            {totalGanttItems > 0 && (
              <Badge variant="default" size="xs" className="font-bold">
                {totalGanttItems} collaborateur{totalGanttItems > 1 ? 's' : ''}
              </Badge>
            )}
          </div>

          {/* Outils : Recherche + Navigateur temporel */}
          <div className="flex items-center gap-2.5 flex-wrap">
            {/* Barre de recherche */}
            <div className="w-48 sm:w-60">
              <Input
                type="text"
                value={ganttSearch}
                onChange={(e) => {
                  setGanttSearch(e.target.value);
                  setGanttPage(1);
                }}
                placeholder="Filtrer par nom, poste, dept…"
                icon={<Search size={14} />}
              />
            </div>

            {planningLoading && (
              <RefreshCw className="w-3.5 h-3.5 text-brand-600 animate-spin shrink-0" title="Chargement du planning…" />
            )}

            {/* Navigateur Temporel (Mois ou Semaine) */}
            {viewMode === 'MOIS' ? (
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
                <span className="px-3 font-bold text-[var(--color-fg)] min-w-[125px] text-center select-none">
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
            ) : (
              <div className="inline-flex items-center border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] h-8 text-xs">
                <button
                  type="button"
                  onClick={handlePrevWeek}
                  className="px-2 h-full hover:bg-[var(--color-surface-hover)] transition-colors text-[var(--color-fg-muted)] cursor-pointer"
                  title="Semaine précédente"
                  aria-label="Semaine précédente"
                >
                  <ChevronLeft size={16} />
                </button>
                <span className="px-3 font-bold text-[var(--color-fg)] min-w-[155px] text-center select-none font-mono text-[11px]">
                  {formattedWeekLabel}
                </span>
                <button
                  type="button"
                  onClick={handleNextWeek}
                  className="px-2 h-full hover:bg-[var(--color-surface-hover)] transition-colors text-[var(--color-fg-muted)] cursor-pointer"
                  title="Semaine suivante"
                  aria-label="Semaine suivante"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            )}

            {/* Bouton Aujourd'hui */}
            {((viewMode === 'MOIS' && !isMoisActuel) || (viewMode === 'SEMAINE' && !isSemaineActuelle)) && (
              <Button
                variant="outline"
                size="xs"
                onClick={viewMode === 'MOIS' ? handleToday : handleTodayWeek}
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
              title="Actualiser le planning"
            >
              Actualiser
            </Button>
          </div>
        </div>

        {/* Légende fidèle à index.css & Indication de Drag */}
        <div className="flex items-center justify-between flex-wrap gap-y-2 text-[11px] text-[var(--color-fg-muted)] pt-0.5 border-t border-[var(--color-border-subtle)]/50">
          <div className="flex items-center flex-wrap gap-x-4 gap-y-1">
            <span className="flex items-center gap-1.5 font-semibold text-[var(--color-fg)]">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-brand-600)] border border-[var(--color-brand-700)] shadow-2xs" />
              Congé validé
            </span>
            <span className="flex items-center gap-1.5 font-semibold text-[var(--color-fg)]">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-warning-500)] border border-[var(--color-warning-600)] shadow-2xs" />
              En attente
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-danger-600)] shadow-2xs" />
              Maladie
            </span>
            <span className="flex items-center gap-1.5">
              <span className="inline-block w-2.5 h-2.5 bg-[var(--color-orange-500)] shadow-2xs" />
              Exceptionnel
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

          <div className="text-[11px] text-brand-700 dark:text-brand-300 font-medium flex items-center gap-1 bg-brand-500/10 px-2 py-0.5 border border-brand-500/20">
            <Plus className="w-3 h-3 text-brand-600" />
            <span>Astuce : Glissez sur plusieurs cases ou cliquez pour poser un congé directement.</span>
          </div>
        </div>
      </div>

      {/* ── Grille Calendaire / Gantt ── */}
      <div className="overflow-x-auto border border-[var(--color-border)] shadow-2xs">
        {ganttRows.length === 0 ? (
          <div className="p-8">
            <EmptyState
              icon={CalendarIcon}
              title="Aucun résultat"
              description={filterMode === 'ABSENTS' 
                ? "Aucune absence enregistrée sur cette période. Basculez sur 'Tous' pour voir les effectifs."
                : "Aucun collaborateur éligible trouvé pour cette période."}
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
          <div className={viewMode === 'MOIS' ? "min-w-[920px]" : "min-w-[850px]"}>
            {/* Header de la grille */}
            <div className="flex bg-[var(--color-bg-subtle)] border-b border-[var(--color-border)] select-none">
              <div className="w-60 shrink-0 p-2.5 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-r border-[var(--color-border)] flex items-center justify-between">
                <span>Collaborateur</span>
                <span className="font-normal opacity-70">Poste & Dépt</span>
              </div>

              {viewMode === 'MOIS' ? (
                // Colonnes Jours du Mois
                <div className="flex-1 flex">
                  {daysArray.map((day) => {
                    const dateObj = new Date(year, (month || 1) - 1, day);
                    const dayOfWeek = dateObj.toLocaleDateString('fr-FR', { weekday: 'short' }).charAt(0).toUpperCase();
                    const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                    const isToday = dateObj.toDateString() === new Date().toDateString();

                    return (
                      <div
                        key={day}
                        title={dateObj.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' })}
                        className={`flex-1 min-w-[24px] text-center py-1 text-xs font-data border-r border-[var(--color-border-subtle)] last:border-0 ${
                          isToday
                            ? 'bg-brand-500/15 text-brand-800 dark:text-brand-200 font-bold border-b-2 border-b-brand-600'
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
              ) : (
                // Colonnes 7 Jours de la Semaine
                <div className="flex-1 flex">
                  {weekDaysArray.map((dayObj, idx) => (
                    <div
                      key={idx}
                      className={`flex-1 min-w-[90px] text-center py-2 px-1 border-r border-[var(--color-border-subtle)] last:border-0 ${
                        dayObj.isToday
                          ? 'bg-brand-500/15 text-brand-800 dark:text-brand-200 font-bold border-b-2 border-b-brand-600'
                          : dayObj.isWeekend
                            ? 'bg-[var(--color-bg-subtle)]/80 text-[var(--color-fg-subtle)]'
                            : 'text-[var(--color-fg)]'
                      }`}
                    >
                      <div className="text-[10px] uppercase font-bold tracking-wide opacity-75">
                        {dayObj.dayOfWeek}
                      </div>
                      <div className="text-xs font-mono font-bold mt-0.5">
                        {dayObj.dayNumber} {dayObj.dateObj.toLocaleDateString('fr-FR', { month: 'short' })}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Corps du Gantt (Lignes Collaborateurs) */}
            <div className="divide-y divide-[var(--color-border-subtle)] bg-[var(--color-surface)]">
              {paginatedGanttRows.map((row) => {
                const totalLanes = row.laneCount || 1;
                const rowHeight = Math.max(LANE_HEIGHT, totalLanes * LANE_HEIGHT + 6);

                const isRowBeingDragged = dragState.isDragging && dragState.idEmploye === row.idEmploye;
                const minDragIdx = isRowBeingDragged ? Math.min(dragState.startIndex, dragState.currentIndex) : -1;
                const maxDragIdx = isRowBeingDragged ? Math.max(dragState.startIndex, dragState.currentIndex) : -1;

                return (
                  <div
                    key={row.idEmploye}
                    className="flex hover:bg-[var(--color-surface-hover)]/60 transition-colors group"
                    style={{ minHeight: `${rowHeight}px` }}
                  >
                    {/* Colonne Collaborateur */}
                    <div className="w-60 shrink-0 px-3 py-1.5 border-r border-[var(--color-border)] flex items-center justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        {canViewEmploye ? (
                          <Link
                            to={`/employes/${row.idEmploye}?tab=conges`}
                            className="font-bold text-xs text-[var(--color-fg)] hover:text-brand-600 truncate block transition-colors leading-snug"
                            title={`Dossier de ${row.employe}`}
                          >
                            {row.employe}
                          </Link>
                        ) : (
                          <span className="font-bold text-xs text-[var(--color-fg)] truncate block leading-snug">
                            {row.employe}
                          </span>
                        )}
                        <span className="text-[10px] text-[var(--color-fg-subtle)] truncate block mt-0.5">
                          {row.fonction ? <span className="font-semibold text-[var(--color-fg-muted)]">{row.fonction}</span> : 'Sans poste'}
                          {row.departement ? ` • ${row.departement}` : ''}
                        </span>
                      </div>

                      {/* Badge total pris */}
                      <span className="text-[10px] font-mono font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] px-1.5 py-0.5 shrink-0 border border-[var(--color-border-subtle)]" title="Total congés sur la période">
                        {row.totalPrisMois}j
                      </span>
                    </div>

                    {/* Grille Jours + Support Glisser (Drag-to-select) + Barres */}
                    <div className="flex-1 relative flex">
                      {viewMode === 'MOIS' ? (
                        // Grille Mois avec sélection par glisser
                        daysArray.map((day, idx) => {
                          const dateObj = new Date(year, (month || 1) - 1, day);
                          const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                          const isToday = dateObj.toDateString() === new Date().toDateString();
                          const dateStr = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                          const isCellInDrag = isRowBeingDragged && idx >= minDragIdx && idx <= maxDragIdx;

                          // Détection des congés sur cette case
                          const leavesOnDay = row.items?.filter((item) => day >= item.startCol && day <= item.endCol) || [];
                          const hasLeave = leavesOnDay.length > 0;
                          const cellTitle = hasLeave
                            ? leavesOnDay.map((item) => getLeaveTooltip(row.employe, item)).join('\n\n')
                            : `Cliquer ou glisser pour poser un congé pour ${row.employe}`;

                          return (
                            <div
                              key={day}
                              onMouseDown={(e) => {
                                if (hasLeave) {
                                  e.stopPropagation();
                                  return;
                                }
                                handleCellMouseDown(row.idEmploye, idx, dateStr, e);
                              }}
                              onMouseEnter={() => handleCellMouseEnter(row.idEmploye, idx, dateStr)}
                              onMouseUp={(e) => {
                                if (hasLeave && !dragState.isDragging) {
                                  e.stopPropagation();
                                  return;
                                }
                                handleCellMouseUp(row.idEmploye, idx, dateStr, e);
                              }}
                              className={`flex-1 min-w-[24px] border-r border-[var(--color-border-subtle)]/40 last:border-0 relative ${hasLeave ? 'cursor-default' : 'cursor-pointer'} group/cell transition-colors ${
                                isCellInDrag
                                  ? 'bg-brand-500/30 border-y-2 border-brand-600 z-10'
                                  : isWeekend
                                    ? 'bg-[var(--color-bg-subtle)]/30 hover:bg-brand-500/15'
                                    : isToday
                                      ? 'bg-brand-500/5 hover:bg-brand-500/20'
                                      : 'hover:bg-brand-500/15'
                              }`}
                              title={cellTitle}
                            >
                              {!hasLeave && !isCellInDrag && (
                                <span className="hidden group-hover/cell:flex absolute inset-0 items-center justify-center text-[10px] text-brand-600 font-bold">
                                  +
                                </span>
                              )}
                            </div>
                          );
                        })
                      ) : (
                        // Grille Semaine avec sélection par glisser
                        weekDaysArray.map((dayObj, idx) => {
                          const isCellInDrag = isRowBeingDragged && idx >= minDragIdx && idx <= maxDragIdx;

                          // Détection des congés sur cette case en vue semaine
                          const leavesOnDay = row.items?.filter((item) => (idx + 1) >= item.startCol && (idx + 1) <= item.endCol) || [];
                          const hasLeave = leavesOnDay.length > 0;
                          const cellTitle = hasLeave
                            ? leavesOnDay.map((item) => getLeaveTooltip(row.employe, item)).join('\n\n')
                            : `Cliquer ou glisser pour poser un congé pour ${row.employe}`;

                          return (
                            <div
                              key={idx}
                              onMouseDown={(e) => {
                                if (hasLeave) {
                                  e.stopPropagation();
                                  return;
                                }
                                handleCellMouseDown(row.idEmploye, idx, dayObj.date, e);
                              }}
                              onMouseEnter={() => handleCellMouseEnter(row.idEmploye, idx, dayObj.date)}
                              onMouseUp={(e) => {
                                if (hasLeave && !dragState.isDragging) {
                                  e.stopPropagation();
                                  return;
                                }
                                handleCellMouseUp(row.idEmploye, idx, dayObj.date, e);
                              }}
                              className={`flex-1 min-w-[90px] border-r border-[var(--color-border-subtle)]/40 last:border-0 relative ${hasLeave ? 'cursor-default' : 'cursor-pointer'} group/cell transition-colors flex flex-col justify-end p-1.5 ${
                                isCellInDrag
                                  ? 'bg-brand-500/30 border-y-2 border-brand-600 z-10'
                                  : dayObj.isWeekend
                                    ? 'bg-[var(--color-bg-subtle)]/30 hover:bg-brand-500/15'
                                    : dayObj.isToday
                                      ? 'bg-brand-500/5 hover:bg-brand-500/20'
                                      : 'hover:bg-brand-500/15'
                              }`}
                              title={cellTitle}
                            >
                              {!hasLeave && !isCellInDrag && (
                                <span className="hidden group-hover/cell:flex items-center justify-center gap-1 text-[10px] text-brand-700 font-semibold bg-white dark:bg-black/60 py-0.5 border border-brand-300 shadow-2xs">
                                  <Plus className="w-3 h-3 text-brand-600" /> Poser
                                </span>
                              )}
                            </div>
                          );
                        })
                      )}

                      {/* Barres de congés conformes à index.css (aucun bleu vif) */}
                      {row.items?.map((item) => {
                        const isDemi = Boolean(item.estDemiJournee);
                        const isMatin = item.momentDemiJournee === 'MATIN';
                        const dureeLabel = isDemi
                          ? (isMatin ? '0.5j Matin' : '0.5j A-M')
                          : `${item.dureeJours}j`;

                        const isEnAttente = item.statut === 'EN_ATTENTE' || item.statut === 'SOUMIS';

                        return (
                          <div
                            key={item.id}
                            role="img"
                            aria-label={`${row.employe} — ${item.type}, ${isEnAttente ? 'en attente' : 'validé'}, ${dureeLabel}`}
                            onMouseDown={(e) => e.stopPropagation()}
                            onMouseUp={(e) => e.stopPropagation()}
                            onClick={(e) => {
                              e.stopPropagation();
                              if (onLeaveClick) onLeaveClick(item, row);
                            }}
                            className={`absolute flex items-center overflow-hidden rounded-none border shadow-xs transition-transform hover:scale-[1.01] hover:brightness-105 z-20 px-2 select-none pointer-events-auto cursor-pointer ${
                              isEnAttente
                                ? 'bg-[var(--color-warning-500)] border-[var(--color-warning-600)] text-white font-semibold'
                                : 'bg-[var(--color-brand-600)] border-[var(--color-brand-700)] text-white font-semibold'
                            }`}
                            style={{
                              left: `${item.leftPercent}%`,
                              width: `${Math.max(item.widthPercent, 2)}%`,
                              top: `${4 + item.laneIndex * LANE_HEIGHT}px`,
                              height: `${LANE_HEIGHT - 8}px`,
                              backgroundColor: !isEnAttente && item.couleur ? item.couleur : undefined
                            }}
                            title={getLeaveTooltip(row.employe, item)}
                          >
                            <span className="text-[11px] truncate tracking-tight font-medium flex items-center gap-1">
                              <span>{item.typeLibelle || item.type}</span>
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
