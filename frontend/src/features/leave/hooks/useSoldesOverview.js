import { useState, useMemo, useEffect, useCallback } from 'react';
import congeService from '@/features/leave/api/congeService';
import useDebounce from '@/hooks/useDebounce';
import { getTodayDateString, fmtDateInput } from '@/utils/dateUtils';

const getMonday = (d) => {
  const date = new Date(d);
  const day = date.getDay();
  const diff = date.getDate() - day + (day === 0 ? -6 : 1);
  const monday = new Date(date.setDate(diff));
  monday.setHours(0, 0, 0, 0);
  return monday;
};

export function useSoldesOverview() {
  // État local : mode, navigation, recherche, pagination et planning.
  // Modes d'affichage
  const [viewMode, setViewMode] = useState('SEMAINE'); // Vue par défaut, plus lisible pour suivre l'équipe
  const [filterMode, setFilterMode] = useState('TOUS'); // 'TOUS' | 'ABSENTS'

  // Navigation Mensuelle
  const [currentMonth, setCurrentMonth] = useState(() => getTodayDateString().slice(0, 7)); // YYYY-MM
  // Données dérivées : calendrier mensuel et hebdomadaire.
  const [yearStr, monthStr] = currentMonth.split('-');
  const year = parseInt(yearStr, 10);
  const month = parseInt(monthStr, 10);
  const daysInMonth = new Date(year, month, 0).getDate();
  const daysArray = useMemo(() => Array.from({ length: daysInMonth }, (_, i) => i + 1), [daysInMonth]);
  const isMoisActuel = currentMonth === getTodayDateString().slice(0, 7);

  // Navigation Hebdomadaire
  const [currentWeekMonday, setCurrentWeekMonday] = useState(() => getMonday(new Date()));

  const weekDaysArray = useMemo(() => {
    return Array.from({ length: 7 }, (_, i) => {
      const d = new Date(currentWeekMonday);
      d.setDate(d.getDate() + i);
      const dateStr = fmtDateInput(d);
      const dayOfWeek = d.toLocaleDateString('fr-FR', { weekday: 'short' });
      const dayNumber = d.getDate();
      const isWeekend = d.getDay() === 0 || d.getDay() === 6;
      const isToday = d.toDateString() === new Date().toDateString();
      return { date: dateStr, dateObj: d, dayNumber, dayOfWeek, isWeekend, isToday };
    });
  }, [currentWeekMonday]);

  const weekStartStr = weekDaysArray[0].date;
  const weekEndStr = weekDaysArray[6].date;
  const isSemaineActuelle = fmtDateInput(getMonday(new Date())) === fmtDateInput(currentWeekMonday);

  // Actions de navigation
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

  const handleTodayMonth = () => {
    setCurrentMonth(getTodayDateString().slice(0, 7));
  };

  const handleMonthChange = (monthStr) => {
    if (/^\d{4}-\d{2}$/.test(monthStr)) setCurrentMonth(monthStr);
  };

  const handlePrevWeek = () => {
    const nextMon = new Date(currentWeekMonday);
    nextMon.setDate(nextMon.getDate() - 7);
    setCurrentWeekMonday(nextMon);
  };

  const handleNextWeek = () => {
    const nextMon = new Date(currentWeekMonday);
    nextMon.setDate(nextMon.getDate() + 7);
    setCurrentWeekMonday(nextMon);
  };

  const handleTodayWeek = () => {
    setCurrentWeekMonday(getMonday(new Date()));
  };

  const handleWeekDateChange = (dateStr) => {
    if (!dateStr) return;
    const [selectedYear, selectedMonth, selectedDay] = dateStr.split('-').map(Number);
    setCurrentWeekMonday(getMonday(new Date(selectedYear, selectedMonth - 1, selectedDay)));
  };

  // États pour recherche et pagination Gantt
  const [ganttSearch, setGanttSearch] = useState('');
  const debouncedSearch = useDebounce(ganttSearch, 350); // Conforme runtime.md §4
  const [ganttPage, setGanttPage] = useState(1);
  const [ganttPageSize, setGanttPageSize] = useState(10);

  // Données Planning issues de l'API dédiée
  const [planningApiData, setPlanningApiData] = useState(null);
  const [planningLoading, setPlanningLoading] = useState(false);
  const [planningError, setPlanningError] = useState(false);

  // Chargement du planning complet backend-driven
  const loadPlanning = useCallback(async (signal) => {
    setPlanningLoading(true);
    setPlanningError(false);
    try {
      let params = {
        page: ganttPage,
        size: ganttPageSize,
        filterMode: filterMode,
      };
      if (debouncedSearch && debouncedSearch.trim()) {
        params.search = debouncedSearch.trim();
      }
      if (viewMode === 'MOIS') {
        params.annee = year;
        params.mois = month;
      } else {
        params.dateDebut = weekStartStr;
        params.dateFin = weekEndStr;
      }
      const data = await congeService.getPlanningCollaborateurs(params, { signal });
      if (signal?.aborted) return;
      setPlanningApiData(data);
    } catch (err) {
      if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED' || err?.name === 'AbortError') {
        return;
      }
      console.warn('Erreur chargement planning collaborateurs :', err);
      setPlanningApiData(null);
      setPlanningError(true);
    } finally {
      if (!signal?.aborted) setPlanningLoading(false);
    }
  }, [viewMode, year, month, weekStartStr, weekEndStr, debouncedSearch, filterMode, ganttPage, ganttPageSize]);

  useEffect(() => {
    const controller = new AbortController();
    loadPlanning(controller.signal);
    return () => controller.abort();
  }, [loadPlanning]);

  const LANE_HEIGHT = 36;

  // Calcul des lignes du Gantt (regroupées avec gestion des lanes et de tous les employés)
  const ganttRows = useMemo(() => {
    // Si l'API Planning a retourné des données (tous les employés actifs)
    if (planningApiData?.employes && Array.isArray(planningApiData.employes)) {
      return planningApiData.employes
        .map((emp) => {
          const rawItems = emp.demandes || [];
          const sortedItems = [...rawItems].sort((a, b) => new Date(a.dateDebut) - new Date(b.dateDebut));
          const laneEnds = [];

          const itemsWithPosition = sortedItems.map((item) => {
            const start = new Date(item.dateDebut);
            const end = new Date(item.dateFin);

            let startCol;
            let endCol;
            let daySpan;
            let leftPercent;
            let widthPercent;

            if (viewMode === 'MOIS') {
              const monthStart = new Date(year, month - 1, 1);
              const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);
              startCol = start < monthStart ? 1 : start.getDate();
              endCol = end > monthEnd ? daysInMonth : end.getDate();
              daySpan = item.estDemiJournee ? 0.5 : (endCol - startCol + 1);

              const isMatin = item.momentDemiJournee === 'MATIN';
              const offset = item.estDemiJournee && !isMatin ? (startCol - 1 + 0.5) : (startCol - 1);
              leftPercent = (offset / daysInMonth) * 100;
              widthPercent = (daySpan / daysInMonth) * 100;
            } else {
              // Mode SEMAINE (7 colonnes, index 0 à 6)
              const wStart = new Date(weekStartStr);
              wStart.setHours(0, 0, 0, 0);
              const wEnd = new Date(weekEndStr);
              wEnd.setHours(23, 59, 59, 999);

              const s = start < wStart ? wStart : start;
              const e = end > wEnd ? wEnd : end;

              const idxStart = Math.max(0, Math.min(6, Math.floor((s.getTime() - wStart.getTime()) / (1000 * 3600 * 24))));
              const idxEnd = Math.max(0, Math.min(6, Math.floor((e.getTime() - wStart.getTime()) / (1000 * 3600 * 24))));
              startCol = idxStart + 1;
              endCol = idxEnd + 1;
              daySpan = item.estDemiJournee ? 0.5 : (idxEnd - idxStart + 1);

              const isMatin = item.momentDemiJournee === 'MATIN';
              const offset = item.estDemiJournee && !isMatin ? (idxStart + 0.5) : idxStart;
              leftPercent = (offset / 7) * 100;
              widthPercent = (daySpan / 7) * 100;
            }

            // Allocation de lanes
            let laneIndex = laneEnds.findIndex((e) => e < startCol);
            if (laneIndex === -1) {
              laneIndex = laneEnds.length;
              laneEnds.push(endCol);
            } else {
              laneEnds[laneIndex] = endCol;
            }

            const jours = item.nbJoursDecomptes == null ? null : Number(item.nbJoursDecomptes);

            return {
              id: item.idDemandeConge,
              type: item.codeTypeConge || item.typeConge || 'Absence',
              typeLibelle: item.typeConge,
              couleur: item.couleur,
              statut: item.statut,
              originalStart: item.dateDebut,
              originalEnd: item.dateFin,
              estDemiJournee: item.estDemiJournee,
              momentDemiJournee: item.momentDemiJournee,
              dureeJours: jours,
              startCol,
              endCol,
              daySpan,
              leftPercent,
              widthPercent,
              laneIndex
            };
          });

          return {
            idEmploye: emp.idEmploye,
            matricule: emp.matricule,
            employe: emp.nomComplet || `${emp.prenom || ''} ${emp.nom || ''}`.trim(),
            fonction: emp.fonction,
            departement: emp.departement,
            site: emp.site,
            photoUrl: emp.photoUrl,
            items: itemsWithPosition,
            laneCount: Math.max(1, laneEnds.length)
          };
        });
    }

    return [];
  }, [planningApiData, viewMode, year, month, daysInMonth, weekStartStr, weekEndStr]);

  // Données plates pour le tableau récapitulatif
  const ganttData = useMemo(() => {
    return ganttRows.flatMap((row) =>
      row.items.map((it) => ({
        ...it,
        employe: row.employe,
        idEmploye: row.idEmploye,
        departement: row.departement,
        fonction: row.fonction
      }))
    );
  }, [ganttRows]);

  const nbEmployesConcernes = ganttRows.length;

  // Pagination Gantt - réinitialisation sur changement de critères
  useEffect(() => {
    setGanttPage(1);
  }, [debouncedSearch, currentMonth, currentWeekMonday, viewMode, filterMode]);

  const totalGanttItems = planningApiData?.total ?? ganttRows.length;
  const totalGanttPages = planningApiData?.totalPages ?? (Math.ceil(totalGanttItems / ganttPageSize) || 1);

  useEffect(() => {
    if (ganttPage > totalGanttPages && totalGanttPages > 0) {
      setGanttPage(1);
    }
  }, [totalGanttPages, ganttPage]);

  // Si le backend a déjà paginé (planningApiData.page), ganttRows contient exactement les enregistrements de la page
  const paginatedGanttRows = useMemo(() => {
    if (planningApiData?.page) {
      return ganttRows;
    }
    return ganttRows.slice((ganttPage - 1) * ganttPageSize, ganttPage * ganttPageSize);
  }, [ganttRows, planningApiData?.page, ganttPage, ganttPageSize]);

  const filteredGanttRows = ganttRows;

  // Tableau récapitulatif
  const [tableSortField, setTableSortField] = useState('employe');
  const [tableSortOrder, setTableSortOrder] = useState('asc');
  const [tablePage, setTablePage] = useState(1);
  const [tablePageSize, setTablePageSize] = useState(10);

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

  const totalTableItems = sortedTableData.length;
  const paginatedTableData = useMemo(() => {
    return sortedTableData.slice((tablePage - 1) * tablePageSize, tablePage * tablePageSize);
  }, [sortedTableData, tablePage, tablePageSize]);

  return {
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
    handleToday: handleTodayMonth,
    handleMonthChange,

    // Navigation Semaine
    currentWeekMonday,
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

    // Table State
    sortedTableData,
    paginatedTableData,
    totalTableItems,
    tableSortField,
    tableSortOrder,
    handleTableSort,
    tablePage,
    setTablePage,
    tablePageSize,
    setTablePageSize
  };
}
