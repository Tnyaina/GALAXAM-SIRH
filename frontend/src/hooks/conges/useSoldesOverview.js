import { useState, useMemo, useEffect } from 'react';

export function useSoldesOverview(demandesGlobales = []) {
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
    const rawList = Array.isArray(demandesGlobales) 
      ? demandesGlobales 
      : (demandesGlobales?.items || demandesGlobales?.content || demandesGlobales?.data || []);
    const daysInMonthCount = new Date(year, month, 0).getDate();
    return rawList.filter(d => {
      if (!d || d.codeStatutDemande === 'REFUSE' || d.codeStatutDemande === 'ANNULE') return false;

      const start = new Date(d.dateDebut);
      const end = new Date(d.dateFin);
      const monthStart = new Date(year, month - 1, 1);
      const monthEnd = new Date(year, month - 1, daysInMonthCount, 23, 59, 59);

      return start <= monthEnd && end >= monthStart;
    }).map(d => {
      const start = new Date(d.dateDebut);
      const end = new Date(d.dateFin);
      const monthStart = new Date(year, month - 1, 1);
      const monthEnd = new Date(year, month - 1, daysInMonthCount, 23, 59, 59);

      const viewStart = start < monthStart ? 1 : start.getDate();
      const viewEnd = end > monthEnd ? daysInMonthCount : end.getDate();

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
        dureeJours: d.nbJoursDecomptes,
        estDemiJournee: !!d.estDemiJournee,
        momentDemiJournee: d.momentDemiJournee
      };
    });
  }, [demandesGlobales, year, month]);

  const LANE_HEIGHT = 32;

  // Regroupe les demandes par employé avec allocation de lanes (mémoïsé)
  const ganttRows = useMemo(() => {
    return Object.values(
      ganttData.reduce((acc, item) => {
        const key = item.idEmploye ?? item.employe;
        if (!acc[key]) acc[key] = { employe: item.employe, idEmploye: item.idEmploye, items: [] };
        acc[key].items.push(item);
        return acc;
      }, {})
    )
      .sort((a, b) => a.employe.localeCompare(b.employe, 'fr'))
      .map((row) => {
        const sortedItems = [...row.items].sort((a, b) => a.startDay - b.startDay);
        const laneEnds = [];
        let totalJoursPris = 0;
        const itemsWithLane = sortedItems.map((item) => {
          let laneIndex = laneEnds.findIndex((end) => end < item.startDay);
          if (laneIndex === -1) {
            laneIndex = laneEnds.length;
            laneEnds.push(item.endDay);
          } else {
            laneEnds[laneIndex] = item.endDay;
          }
          const jours = Number(item.dureeJours) || (item.endDay - item.startDay + 1);
          totalJoursPris += jours;
          return { ...item, laneIndex, calcJours: jours };
        });
        return {
          employe: row.employe,
          idEmploye: row.idEmploye,
          items: itemsWithLane,
          laneCount: laneEnds.length,
          totalPrisMois: totalJoursPris % 1 === 0 ? totalJoursPris : totalJoursPris.toFixed(1)
        };
      });
  }, [ganttData]);

  const nbEmployesConcernes = ganttRows.length;

  // Filtrage et pagination mémoïsés des lignes du Gantt par nom d'employé
  const filteredGanttRows = useMemo(() => {
    return ganttRows.filter((row) =>
      row.employe.toLowerCase().includes(ganttSearch.toLowerCase().trim())
    );
  }, [ganttRows, ganttSearch]);

  // Réinitialiser la page Gantt si la recherche ou le mois change
  useEffect(() => {
    setGanttPage(1);
  }, [ganttSearch, currentMonth]);

  const totalGanttItems = filteredGanttRows.length;
  const totalGanttPages = Math.ceil(totalGanttItems / ganttPageSize) || 1;

  // Sécurité ajustement Gantt : si ganttPage > totalGanttPages
  useEffect(() => {
    if (ganttPage > totalGanttPages && totalGanttPages > 0) {
      setGanttPage(1);
    }
  }, [totalGanttPages, ganttPage]);

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
  const totalTablePages = Math.ceil(totalTableItems / tablePageSize) || 1;

  // Sécurité ajustement Tableau : si tablePage > totalTablePages
  useEffect(() => {
    if (tablePage > totalTablePages && totalTablePages > 0) {
      setTablePage(1);
    }
  }, [totalTablePages, tablePage]);

  const paginatedTableData = useMemo(() => {
    return sortedTableData.slice(
      (tablePage - 1) * tablePageSize,
      tablePage * tablePageSize
    );
  }, [sortedTableData, tablePage, tablePageSize]);

  // Calculate KPIs
  const safeDemandesList = Array.isArray(demandesGlobales)
    ? demandesGlobales
    : (demandesGlobales?.items || demandesGlobales?.content || demandesGlobales?.data || []);

  const kpiEnAttente = safeDemandesList.filter(d => d && (d.isEnAttente ?? (d.codeStatutDemande === 'EN_ATTENTE' || d.statutCode === 'EN_ATTENTE'))).length;

  const kpiValideCeMois = safeDemandesList.filter(d => {
    if (!d || !(d.isValide ?? (d.codeStatutDemande === 'VALIDE' || d.statutCode === 'VALIDE'))) return false;
    const end = new Date(d.dateFin);
    const monthStart = new Date(year, month - 1, 1);
    const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);
    return end >= monthStart && end <= monthEnd;
  }).length;

  const today = new Date();
  today.setHours(0,0,0,0);
  const kpiAbsentsAuj = safeDemandesList.filter(d => {
    if (!d || !(d.isValide ?? (d.codeStatutDemande === 'VALIDE' || d.statutCode === 'VALIDE'))) return false;
    const start = new Date(d.dateDebut);
    start.setHours(0,0,0,0);
    const end = new Date(d.dateFin);
    end.setHours(23,59,59,999);
    return today >= start && today <= end;
  }).length;

  const kpiTotalDemandes = safeDemandesList.length;

  return {
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
    sortedTableData,
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
  };
}
