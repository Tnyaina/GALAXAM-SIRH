import { useState, useEffect, useCallback } from 'react';
import pointageAnomalieService from '@/services/pointageAnomalieService';
import periodePaieService from '@/services/periodePaieService';
import organisationService from '@/api/admin/organisationService';
import useDebounce from '@/hooks/useDebounce';

export function usePointageAnomalies() {
    const [anomalies, setAnomalies] = useState([]);
    const [stats, setStats] = useState({ total: 0, incomplets: 0, retards: 0, absences: 0 });
    const [periodes, setPeriodes] = useState([]);
    const [sites, setSites] = useState([]);

    const [selectedSite, setSelectedSite] = useState(0); // 0 = Tous
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [dateFilterMode, setDateFilterMode] = useState('ALL'); // 'ALL' | 'SPECIFIC' | 'INTERVAL'
    const [selectedDate, setSelectedDate] = useState('');
    const [dateDebut, setDateDebut] = useState('');
    const [dateFin, setDateFin] = useState('');
    const [typeFilter, setTypeFilter] = useState('ALL'); // 'ALL' | 'INCOMPLET' | 'RETARD' | 'ABSENCE'
    const [searchTerm, setSearchTerm] = useState('');
    const debouncedSearch = useDebounce(searchTerm, 350);

    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState(null);

    // Pagination
    const [currentPage, setCurrentPage] = useState(1);
    const pageSize = 10;

    const loadPeriodesEtSites = useCallback(async () => {
        try {
            const [periodesData, sitesData] = await Promise.all([
                periodePaieService.getAllPeriodes(),
                organisationService.getSites()
            ]);
            const periodesList = Array.isArray(periodesData) ? periodesData : (periodesData?.items || periodesData?.content || []);
            const sitesList = Array.isArray(sitesData) ? sitesData : (sitesData?.items || sitesData?.content || []);
            setPeriodes(periodesList);
            setSites(sitesList);
            if (periodesList.length > 0) {
                // Sélectionner par défaut la période en cours ou la plus récente
                const active = periodesList.find(p => p.statutCode === 'EN_COURS' || p.statutCode === 'A_VALIDER') || periodesList[0];
                setSelectedPeriode(prev => prev || (active ? active.idPeriodePaie : ''));
            }
        } catch (err) {
            console.error('Erreur chargement périodes et sites pour anomalies:', err);
        }
    }, []);

    const loadAnomalies = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const params = {
                idSite: selectedSite > 0 ? selectedSite : undefined,
                idPeriodePaie: selectedPeriode ? selectedPeriode : undefined,
                typeAnomalie: typeFilter,
                search: debouncedSearch.trim() || undefined
            };

            if (dateFilterMode === 'SPECIFIC' && selectedDate) {
                params.datePointage = selectedDate;
            } else if (dateFilterMode === 'INTERVAL') {
                if (dateDebut) params.dateDebut = dateDebut;
                if (dateFin) params.dateFin = dateFin;
            }

            const res = await pointageAnomalieService.getAnomalies(params);
            if (res) {
                const list = Array.isArray(res.anomalies) ? res.anomalies : (res.anomalies?.items || res.anomalies?.content || []);
                setAnomalies(list);
                setStats(res.stats || { total: 0, incomplets: 0, retards: 0, absences: 0 });
            }
        } catch (err) {
            console.error('Erreur chargement anomalies:', err);
            setError("Impossible de charger la liste des anomalies de pointage.");
        } finally {
            setLoading(false);
        }
    }, [selectedSite, selectedPeriode, dateFilterMode, selectedDate, dateDebut, dateFin, typeFilter, debouncedSearch]);

    // Réinitialiser la pagination si les filtres changent
    useEffect(() => {
        setCurrentPage(1);
    }, [selectedSite, selectedPeriode, dateFilterMode, selectedDate, dateDebut, dateFin, typeFilter, debouncedSearch]);

    useEffect(() => {
        loadPeriodesEtSites();
    }, [loadPeriodesEtSites]);

    useEffect(() => {
        loadAnomalies();
    }, [loadAnomalies]);

    const regulariser = async (idPointage, payload) => {
        setSaving(true);
        setError(null);
        try {
            const res = await pointageAnomalieService.regulariserAnomalie(idPointage, payload);
            if (res.success) {
                await loadAnomalies();
                return true;
            } else {
                setError(res.message);
                return false;
            }
        } catch (err) {
            setError(err.response?.data?.message || "Erreur lors de la régularisation de l'anomalie.");
            return false;
        } finally {
            setSaving(false);
        }
    };

    const regulariserStandard = async (idPointage) => {
        return regulariser(idPointage, { action: 'APPLY_STANDARD' });
    };

    const justifierRetardRapide = async (idPointage, motif) => {
        return regulariser(idPointage, { action: 'JUSTIFIER_RETARD', motifAjustement: motif });
    };

    const regulariserBatch = async (ids, action, motifAjustement) => {
        if (!ids || ids.length === 0) return { success: false, message: "Aucune anomalie sélectionnée." };
        setSaving(true);
        setError(null);
        try {
            const res = await pointageAnomalieService.regulariserBatch({ ids, action, motifAjustement });
            if (res.success) {
                await loadAnomalies();
                return res;
            } else {
                setError(res.message);
                return { success: false, message: res.message };
            }
        } catch (err) {
            const msg = err.response?.data?.message || "Erreur lors du traitement par lot des anomalies.";
            setError(msg);
            return { success: false, message: msg };
        } finally {
            setSaving(false);
        }
    };

    return {
        anomalies,
        stats,
        periodes,
        sites,
        selectedSite,
        setSelectedSite,
        selectedPeriode,
        setSelectedPeriode,
        dateFilterMode,
        setDateFilterMode,
        selectedDate,
        setSelectedDate,
        dateDebut,
        setDateDebut,
        dateFin,
        setDateFin,
        typeFilter,
        setTypeFilter,
        searchTerm,
        setSearchTerm,
        currentPage,
        setCurrentPage,
        pageSize,
        loading,
        saving,
        error,
        setError,
        loadAnomalies,
        regulariser,
        regulariserStandard,
        justifierRetardRapide,
        regulariserBatch
    };
}
