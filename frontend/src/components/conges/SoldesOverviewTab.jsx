import { useState, useMemo } from 'react';
import { Users, CheckCircle, Clock, CalendarDays, RefreshCw, ChevronLeft, ChevronRight, CalendarClock, Search, ArrowUpDown, ArrowUp, ArrowDown, X } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmpyState';
import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Pagination from '@/components/ui/navigation/Pagination';

// Utility to format timestamp to readable date
const formatDate = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

export default function SoldesOverviewTab({ loading, demandesGlobales = [], onReload }) {
  const [currentMonth, setCurrentMonth] = useState(new Date().toISOString().slice(0, 7)); // YYYY-MM

  // États pour la recherche et la pagination du Gantt
  const [ganttSearch, setGanttSearch] = useState('');
  const [ganttPage, setGanttPage] = useState(1);
  const [ganttPageSize, setGanttPageSize] = useState(10);

  // États pour le tri et la pagination du tableau récapitulatif
  const [tableSortField, setTableSortField] = useState('employe');
  const [tableSortOrder, setTableSortOrder] = useState('asc');
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(10);

  // Generate days of the month for Gantt
  const [yearStr, monthStr] = currentMonth.split('-');
  const year = parseInt(yearStr, 10);
  const month = parseInt(monthStr, 10);
  const daysInMonth = new Date(year, month, 0).getDate();
  const daysArray = Array.from({ length: daysInMonth }, (_, i) => i + 1);

  const isMoisActuel = currentMonth === new Date().toISOString().slice(0, 7);

  const handlePrevMonth = () => {
    let m = month - 1;
    let y = year;
    if (m < 1) { m = 12; y--; }
    setCurrentMonth(`${y}-${m.toString().padStart(2, '0')}`);
  };

  const handleNextMonth = () => {
    let m = month + 1;
    let y = year;
    if (m > 12) { m = 1; y++; }
    setCurrentMonth(`${y}-${m.toString().padStart(2, '0')}`);
  };

  const handleToday = () => {
    setCurrentMonth(new Date().toISOString().slice(0, 7));
  };

  // Compute Gantt Data based on the real endpoint (mémoïsé)
  const ganttData = useMemo(() => {
    return (demandesGlobales || []).filter(d => {
      if (d.codeStatutDemande === 'REFUSE' || d.codeStatutDemande === 'ANNULE') return false;

      const start = new Date(d.dateDebut);
      const end = new Date(d.dateFin);
      const monthStart = new Date(year, month - 1, 1);
      const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);

      return start <= monthEnd && end >= monthStart;
    }).map(d => {
      const start = new Date(d.dateDebut);
      const end = new Date(d.dateFin);
      const monthStart = new Date(year, month - 1, 1);
      const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);

      const viewStart = start < monthStart ? 1 : start.getDate();
      const viewEnd = end > monthEnd ? daysInMonth : end.getDate();

      return {
        employe: `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé #${d.idEmploye}`,
        idEmploye: d.idEmploye,
        id: d.idDemandeConge,
        type: d.codeTypeConge || 'ANNUEL',
        startDay: viewStart,
        endDay: viewEnd,
        statut: d.codeStatutDemande,
        originalStart: d.dateDebut,
        originalEnd: d.dateFin,
        dureeJours: d.nbJoursDecomptes
      };
    });
  }, [demandesGlobales, year, month, daysInMonth]);

  const LANE_HEIGHT = 32;

  // Regroupe les demandes par employé avec allocation de lanes (mémoïsé)
  const ganttRows = useMemo(() => {
    return Object.values(
      ganttData.reduce((acc, item) => {
        const key = item.idEmploye ?? item.employe;
        if (!acc[key]) acc[key] = { employe: item.employe, items: [] };
        acc[key].items.push(item);
        return acc;
      }, {})
    )
      .sort((a, b) => a.employe.localeCompare(b.employe, 'fr'))
      .map((row) => {
        const sortedItems = [...row.items].sort((a, b) => a.startDay - b.startDay);
        const laneEnds = [];
        const itemsWithLane = sortedItems.map((item) => {
          let laneIndex = laneEnds.findIndex((end) => end < item.startDay);
          if (laneIndex === -1) {
            laneIndex = laneEnds.length;
            laneEnds.push(item.endDay);
          } else {
            laneEnds[laneIndex] = item.endDay;
          }
          return { ...item, laneIndex };
        });
        return { employe: row.employe, items: itemsWithLane, laneCount: laneEnds.length };
      });
  }, [ganttData]);

  const nbEmployesConcernes = ganttRows.length;

  // Filtrage et pagination mémoïsés des lignes du Gantt par nom d'employé
  const filteredGanttRows = useMemo(() => {
    return ganttRows.filter((row) =>
      row.employe.toLowerCase().includes(ganttSearch.toLowerCase().trim())
    );
  }, [ganttRows, ganttSearch]);

  const totalGanttItems = filteredGanttRows.length;

  const paginatedGanttRows = useMemo(() => {
    return filteredGanttRows.slice(
      (ganttPage - 1) * ganttPageSize,
      ganttPage * ganttPageSize
    );
  }, [filteredGanttRows, ganttPage, ganttPageSize]);

  // Tri du tableau récapitulatif
  const handleTableSort = (field) => {
    if (tableSortField === field) {
      setTableSortOrder(tableSortOrder === 'asc' ? 'desc' : 'asc');
    } else {
      setTableSortField(field);
      setTableSortOrder('asc');
    }
    setTablePage(1);
  };

  const renderSortIcon = (field) => {
    if (tableSortField !== field) return <ArrowUpDown size={13} className="text-[var(--color-fg-subtle)] opacity-40 ml-1 inline-block" />;
    return tableSortOrder === 'asc'
      ? <ArrowUp size={13} className="text-[var(--color-brand-500)] ml-1 inline-block font-bold" />
      : <ArrowDown size={13} className="text-[var(--color-brand-500)] ml-1 inline-block font-bold" />;
  };

  const sortedTableData = useMemo(() => {
    return [...ganttData].sort((a, b) => {
      let valA = a[tableSortField];
      let valB = b[tableSortField];

      if (typeof valA === 'string') {
        const res = valA.localeCompare(valB || '', 'fr');
        return tableSortOrder === 'asc' ? res : -res;
      }
      if (valA instanceof Date || typeof valA === 'number') {
        return tableSortOrder === 'asc' ? (valA > valB ? 1 : -1) : (valA < valB ? 1 : -1);
      }
      return 0;
    });
  }, [ganttData, tableSortField, tableSortOrder]);

  // Pagination du tableau récapitulatif
  const totalTableItems = sortedTableData.length;
  const paginatedTableData = useMemo(() => {
    return sortedTableData.slice(
      (tablePage - 1) * tablePageSize,
      tablePage * tablePageSize
    );
  }, [sortedTableData, tablePage, tablePageSize]);

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

  // Calculate KPIs
  const kpiEnAttente = (demandesGlobales || []).filter(d => d.codeStatutDemande === 'EN_ATTENTE').length;

  const kpiValideCeMois = (demandesGlobales || []).filter(d => {
    if (d.codeStatutDemande !== 'VALIDE') return false;
    const end = new Date(d.dateFin);
    const monthStart = new Date(year, month - 1, 1);
    const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);
    return end >= monthStart && end <= monthEnd;
  }).length;

  const today = new Date();
  today.setHours(0,0,0,0);
  const kpiAbsentsAuj = (demandesGlobales || []).filter(d => {
    if (d.codeStatutDemande !== 'VALIDE') return false;
    const start = new Date(d.dateDebut);
    start.setHours(0,0,0,0);
    const end = new Date(d.dateFin);
    end.setHours(23,59,59,999);
    return today >= start && today <= end;
  }).length;

  const kpiTotalDemandes = (demandesGlobales || []).length;

  return (
    <div className="space-y-6">
      {/* Barre de KPIs */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-2">
        <div className="rounded-none">
          <StatCard title="Demandes en attente" value={kpiEnAttente} icon={<Clock size={24} />} color="warning" />
        </div>
        <div className="rounded-none">
          <StatCard title="Absents aujourd'hui" value={kpiAbsentsAuj} icon={<Users size={24} />} color="danger" />
        </div>
        <div className="rounded-none">
          <StatCard title="Validées ce mois" value={kpiValideCeMois} icon={<CheckCircle size={24} />} color="success" />
        </div>
        <div className="rounded-none">
          <StatCard title="Total demandes" value={kpiTotalDemandes} icon={<CalendarDays size={24} />} color="info" />
        </div>
      </div>

      {/* Planning Gantt CSS */}
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
                      className="w-48 shrink-0 p-3 text-sm font-semibold text-[var(--color-fg)] border-r border-[var(--color-border-subtle)] truncate flex items-center"
                      title={row.employe}
                      style={{ minHeight: `${rowHeight + 8}px` }}
                    >
                      {row.employe}
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

      {/* Tableau Récapitulatif Consolide */}
      <Card>
        <CardHeader>
          <CardTitle>Dernières absences du mois</CardTitle>
        </CardHeader>
        <CardBody className="p-0">
          {ganttData.length === 0 ? (
            <EmptyState title="Aucune donnée" description="Aucune absence validée ou en attente ce mois-ci." />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('employe')}>
                    Employé {renderSortIcon('employe')}
                  </Th>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('type')}>
                    Type {renderSortIcon('type')}
                  </Th>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('originalStart')}>
                    Du {renderSortIcon('originalStart')}
                  </Th>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('originalEnd')}>
                    Au {renderSortIcon('originalEnd')}
                  </Th>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('dureeJours')}>
                    Jours {renderSortIcon('dureeJours')}
                  </Th>
                  <Th className="cursor-pointer select-none hover:text-[var(--color-brand-500)]" onClick={() => handleTableSort('statut')}>
                    Statut {renderSortIcon('statut')}
                  </Th>
                </Tr>
              </Thead>
              <Tbody>
                {paginatedTableData.map((item, idx) => (
                  <Tr key={idx}>
                    <Td className="font-semibold text-[var(--color-fg)]">{item.employe}</Td>
                    <Td><Badge variant="brand">{item.type}</Badge></Td>
                    <Td className="font-data">{formatDate(item.originalStart)}</Td>
                    <Td className="font-data">{formatDate(item.originalEnd)}</Td>
                    <Td className="font-data">{item.dureeJours}</Td>
                    <Td>
                      <Badge variant={item.statut === 'VALIDE' ? 'success' : 'warning'} dot>
                        {item.statut === 'VALIDE' ? 'Validé' : 'En attente'}
                      </Badge>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </CardBody>

        {/* Pagination du tableau récapitulatif */}
        {totalTableItems > 0 && (
          <Pagination
            currentPage={tablePage}
            totalItems={totalTableItems}
            pageSize={tablePageSize}
            pageSizeOptions={[5, 10, 25, 50]}
            onPageChange={setTablePage}
            onPageSizeChange={(size) => {
              setTablePageSize(size);
              setTablePage(1);
            }}
          />
        )}
      </Card>
    </div>
  );
}