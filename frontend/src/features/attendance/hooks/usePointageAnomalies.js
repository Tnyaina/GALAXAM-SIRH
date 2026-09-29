import { useState, useEffect, useCallback } from 'react';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import useDebounce from '@/hooks/useDebounce';
import { getErrorMessage } from '@/api/apiClient';

export function usePointageAnomalies(initialFilters = {}) {
    // État local : filtres, données d'anomalies et chargement.
    const [filters, setFilters] = useState({
        idSite: 0,
        idPeriodePaie: 0,
        datePointage: '',
        search: '',
        ...initialFilters
    });

    const debouncedSearch = useDebounce(filters.search, 350);

    const [anomalies, setAnomalies] = useState([]);
    const [stats, setStats] = useState({ total: 0, incomplets: 0, retards: 0, absences: 0, critiques: 0, hautes: 0, moyennes: 0, faibles: 0, bloquantes: 0 });
    const [semaines, setSemaines] = useState([]);
    const [kpis, setKpis] = useState({});
    const [periode, setPeriode] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    // Chargement annulable
    const loadAnomalies = useCallback(async (signal) => {
        if (!(Number(filters.idPeriodePaie) > 0) && !filters.datePointage) return;
        setLoading(true);
        setError(null);
        try {
            const queryParams = {
                ...filters,
                search: debouncedSearch
            };
            const data = await attendanceService.getAnomalies(queryParams, { signal });
            if (data) {
                setAnomalies(data.anomalies || []);
                setStats(data.stats || { total: 0, incomplets: 0, retards: 0, absences: 0, critiques: 0, hautes: 0, moyennes: 0, faibles: 0, bloquantes: 0 });
                setSemaines(data.semaines || []);
                setKpis(data.kpis || {});
                setPeriode(data.periode || null);
            }
        } catch (err) {
            if (err?.name === 'CanceledError' || err?.code === 'ERR_CANCELED' || err?.name === 'AbortError') {
                return;
            }
            console.error('Erreur chargement anomalies:', err);
            setError('Impossible de charger les anomalies de pointage.');
        } finally {
            setLoading(false);
        }
    }, [filters, debouncedSearch]);

    useEffect(() => {
        const controller = new AbortController();
        loadAnomalies(controller.signal);
        return () => controller.abort();
    }, [loadAnomalies]);

    const regulariser = async (idPointage, payload, { reload = true } = {}) => {
        try {
            const res = await attendanceService.regulariser(idPointage, payload);
            if (res && (res.success || res.status === 200)) {
                if (reload) await loadAnomalies();
                return res;
            }
            return res || { success: false, error: 'Échec de la régularisation.' };
        } catch (err) {
            console.error('Erreur régularisation anomalie:', err);
            return {
                success: false,
                error: getErrorMessage(err, 'Erreur serveur lors de la régularisation.')
            };
        }
    };

    const updateFilters = (newFilters) => {
        setFilters(prev => ({ ...prev, ...newFilters }));
    };

    return {
        anomalies,
        stats,
        semaines,
        kpis,
        periode,
        filters,
        loading,
        error,
        updateFilters,
        loadAnomalies,
        regulariser
    };
}

export default usePointageAnomalies;
