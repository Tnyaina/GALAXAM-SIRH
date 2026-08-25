import { useState, useEffect, useCallback, useMemo } from 'react';
import { pointageSyntheseService } from '@/services/pointageSyntheseService';
import periodePaieService from '@/services/periodePaieService';
import organisationService from '@/api/admin/organisationService';
import useDebounce from '@/hooks/useDebounce';

export function usePointageSynthese() {
    const [periodes, setPeriodes] = useState([]);
    const [sites, setSites] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [selectedSite, setSelectedSite] = useState('');
    const [searchTerm, setSearchTerm] = useState('');
    const [filterCategorie, setFilterCategorie] = useState('ALL'); // 'ALL' | 'CADRE' | 'NON_CADRE'

    // Données Mensuelles
    const [syntheseList, setSyntheseList] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    // Données Hebdomadaires
    const [syntheseHebdoList, setSyntheseHebdoList] = useState([]);
    const [selectedSemaine, setSelectedSemaine] = useState(''); // '' pour toutes ou 1, 2, 3, 4, 5
    const [loadingHebdo, setLoadingHebdo] = useState(false);

    // Pagination & Tri Mensuel
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [sortBy, setSortBy] = useState('matricule'); // 'matricule' | 'nom' | 'heures' | 'hs' | 'absences'
    const [sortOrder, setSortOrder] = useState('asc'); // 'asc' | 'desc'

    // Pagination & Tri Hebdomadaire
    const [currentHebdoPage, setCurrentHebdoPage] = useState(1);
    const [hebdoPageSize, setHebdoPageSize] = useState(25);
    const [hebdoSortBy, setHebdoSortBy] = useState('numeroSemaine');
    const [hebdoSortOrder, setHebdoSortOrder] = useState('asc');

    // Détail des semaines pour un employé sélectionné (Modale)
    const [selectedEmploye, setSelectedEmploye] = useState(null);
    const [troncons, setTroncons] = useState([]);
    const [loadingTroncons, setLoadingTroncons] = useState(false);

    // 1. Chargement des référentiels (périodes & sites)
    const loadRefs = useCallback(async () => {
        try {
            const [periodesData, sitesData] = await Promise.all([
                periodePaieService.getAllPeriodes(),
                organisationService.getSites()
            ]);
            setPeriodes(periodesData || []);
            setSites(sitesData || []);

            if (periodesData && periodesData.length > 0) {
                const active = periodesData.find(p => p.statutCode === 'EN_COURS') || periodesData[0];
                setSelectedPeriode(active.idPeriodePaie);
            }
        } catch (err) {
            console.error('Erreur chargement référentiels synthèse:', err);
            setError('Impossible de charger les périodes de paie ou les sites.');
        }
    }, []);

    useEffect(() => {
        loadRefs();
    }, [loadRefs]);

    const debouncedSearch = useDebounce(searchTerm, 350);

    // 2. Chargement de la synthèse mensuelle
    const loadSynthese = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        setError(null);
        try {
            const data = await pointageSyntheseService.getSyntheseMensuelle(
                selectedPeriode,
                selectedSite || null,
                debouncedSearch || null
            );
            setSyntheseList(data || []);
            setCurrentPage(1);
        } catch (err) {
            console.error('Erreur chargement synthèse:', err);
            setError('Erreur lors du calcul de la synthèse de pointage.');
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite, debouncedSearch]);

    // 3. Chargement de la synthèse hebdomadaire globale
    const loadSyntheseHebdo = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoadingHebdo(true);
        try {
            const data = await pointageSyntheseService.getSyntheseHebdomadaire(
                selectedPeriode,
                selectedSemaine ? Number(selectedSemaine) : null,
                selectedSite || null,
                debouncedSearch || null
            );
            setSyntheseHebdoList(data || []);
            setCurrentHebdoPage(1);
        } catch (err) {
            console.error('Erreur chargement synthèse hebdomadaire:', err);
        } finally {
            setLoadingHebdo(false);
        }
    }, [selectedPeriode, selectedSemaine, selectedSite, debouncedSearch]);

    useEffect(() => {
        loadSynthese();
        loadSyntheseHebdo();
    }, [selectedPeriode, selectedSite, selectedSemaine, debouncedSearch]);

    // 4. Chargement des tronçons pour un employé spécifique (modale)
    const loadTroncons = useCallback(async (emp) => {
        if (!emp || !selectedPeriode) return;
        setSelectedEmploye(emp);
        setLoadingTroncons(true);
        try {
            const data = await pointageSyntheseService.getTronconsSemainesEmploye(emp.idEmploye, selectedPeriode);
            setTroncons(data || []);
        } catch (err) {
            console.error('Erreur chargement tronçons:', err);
            setTroncons([]);
        } finally {
            setLoadingTroncons(false);
        }
    }, [selectedPeriode]);

    // Période courante
    const currentPeriode = useMemo(() => {
        return periodes.find(p => p.idPeriodePaie === Number(selectedPeriode));
    }, [periodes, selectedPeriode]);

    // Statistiques globales mensuelles
    const stats = useMemo(() => {
        return syntheseList.reduce((acc, row) => {
            acc.totalSalaries += 1;
            acc.totalHeures += row.totalHeuresTravaillees || 0;
            acc.totalHs30 += row.heuresHs30 || 0;
            acc.totalHs50 += row.heuresHs50 || 0;
            acc.totalDeplacement += row.montantDeplacement || 0;
            acc.totalRepas += row.montantRepas || 0;
            acc.totalAbsences += row.nbJoursAbsence || 0;
            acc.totalAnomalies += row.nbAnomaliesNonResolues || 0;
            return acc;
        }, {
            totalSalaries: 0,
            totalHeures: 0,
            totalHs30: 0,
            totalHs50: 0,
            totalDeplacement: 0,
            totalRepas: 0,
            totalAbsences: 0,
            totalAnomalies: 0
        });
    }, [syntheseList]);

    // Statistiques globales hebdomadaires
    const statsHebdo = useMemo(() => {
        return syntheseHebdoList.reduce((acc, row) => {
            acc.totalLignes += 1;
            acc.totalHeures += row.totalHeuresTravaillees || 0;
            acc.totalRetards += row.heuresRetard || 0;
            acc.totalCompensees += row.heuresCompensees || 0;
            acc.totalHs30 += row.heuresHs30 || 0;
            acc.totalHs50 += row.heuresHs50 || 0;
            acc.totalDeficit += row.heuresDeficit || 0;
            return acc;
        }, {
            totalLignes: 0,
            totalHeures: 0,
            totalRetards: 0,
            totalCompensees: 0,
            totalHs30: 0,
            totalHs50: 0,
            totalDeficit: 0
        });
    }, [syntheseHebdoList]);

    // Tri et filtrage Mensuel
    const sortedList = useMemo(() => {
        let list = [...syntheseList];

        if (filterCategorie === 'CADRE') {
            list = list.filter(r => r.estCadre);
        } else if (filterCategorie === 'NON_CADRE') {
            list = list.filter(r => !r.estCadre);
        }

        return list.sort((a, b) => {
            let valA, valB;
            switch (sortBy) {
                case 'nom':
                    valA = `${a.nom} ${a.prenom}`.toLowerCase();
                    valB = `${b.nom} ${b.prenom}`.toLowerCase();
                    break;
                case 'heures':
                    valA = a.totalHeuresTravaillees || 0;
                    valB = b.totalHeuresTravaillees || 0;
                    break;
                case 'hs':
                    valA = (a.heuresHs30 || 0) + (a.heuresHs50 || 0);
                    valB = (b.heuresHs30 || 0) + (b.heuresHs50 || 0);
                    break;
                case 'absences':
                    valA = a.nbJoursAbsence || 0;
                    valB = b.nbJoursAbsence || 0;
                    break;
                case 'matricule':
                default:
                    valA = a.matricule || '';
                    valB = b.matricule || '';
                    break;
            }

            if (valA < valB) return sortOrder === 'asc' ? -1 : 1;
            if (valA > valB) return sortOrder === 'asc' ? 1 : -1;
            return 0;
        });
    }, [syntheseList, sortBy, sortOrder, filterCategorie]);

    // Tri et filtrage Hebdomadaire
    const sortedHebdoList = useMemo(() => {
        let list = [...syntheseHebdoList];

        if (filterCategorie === 'CADRE') {
            list = list.filter(r => r.estCadre);
        } else if (filterCategorie === 'NON_CADRE') {
            list = list.filter(r => !r.estCadre);
        }

        return list.sort((a, b) => {
            let valA, valB;
            switch (hebdoSortBy) {
                case 'nom':
                    valA = `${a.nom} ${a.prenom}`.toLowerCase();
                    valB = `${b.nom} ${b.prenom}`.toLowerCase();
                    break;
                case 'heures':
                    valA = a.totalHeuresTravaillees || 0;
                    valB = b.totalHeuresTravaillees || 0;
                    break;
                case 'hs':
                    valA = (a.heuresHs30 || 0) + (a.heuresHs50 || 0);
                    valB = (b.heuresHs30 || 0) + (b.heuresHs50 || 0);
                    break;
                case 'retard':
                    valA = a.heuresRetard || 0;
                    valB = b.heuresRetard || 0;
                    break;
                case 'deficit':
                    valA = a.heuresDeficit || 0;
                    valB = b.heuresDeficit || 0;
                    break;
                case 'numeroSemaine':
                default:
                    valA = a.numeroSemaine || 0;
                    valB = b.numeroSemaine || 0;
                    break;
            }

            if (valA < valB) return hebdoSortOrder === 'asc' ? -1 : 1;
            if (valA > valB) return hebdoSortOrder === 'asc' ? 1 : -1;
            return 0;
        });
    }, [syntheseHebdoList, hebdoSortBy, hebdoSortOrder, filterCategorie]);

    // Données paginées Mensuel
    const paginatedList = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return sortedList.slice(start, start + pageSize);
    }, [sortedList, currentPage, pageSize]);

    // Données paginées Hebdomadaire
    const paginatedHebdoList = useMemo(() => {
        const start = (currentHebdoPage - 1) * hebdoPageSize;
        return sortedHebdoList.slice(start, start + hebdoPageSize);
    }, [sortedHebdoList, currentHebdoPage, hebdoPageSize]);

    const toggleSort = (col) => {
        if (sortBy === col) {
            setSortOrder(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setSortBy(col);
            setSortOrder('asc');
        }
    };

    const toggleHebdoSort = (col) => {
        if (hebdoSortBy === col) {
            setHebdoSortOrder(prev => prev === 'asc' ? 'desc' : 'asc');
        } else {
            setHebdoSortBy(col);
            setHebdoSortOrder('asc');
        }
    };

    return {
        periodes,
        sites,
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        searchTerm,
        setSearchTerm,
        filterCategorie,
        setFilterCategorie,
        currentPeriode,

        // Mensuel
        syntheseList,
        loading,
        error,
        stats,
        sortedList,
        paginatedList,
        currentPage,
        setCurrentPage,
        pageSize,
        setPageSize,
        sortBy,
        sortOrder,
        toggleSort,
        loadSynthese,

        // Hebdomadaire
        syntheseHebdoList,
        selectedSemaine,
        setSelectedSemaine,
        loadingHebdo,
        statsHebdo,
        sortedHebdoList,
        paginatedHebdoList,
        currentHebdoPage,
        setCurrentHebdoPage,
        hebdoPageSize,
        setHebdoPageSize,
        hebdoSortBy,
        hebdoSortOrder,
        toggleHebdoSort,
        loadSyntheseHebdo,

        // Détail modale
        selectedEmploye,
        setSelectedEmploye,
        troncons,
        loadingTroncons,
        loadTroncons
    };
}
