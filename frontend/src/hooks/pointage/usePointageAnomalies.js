import { useState, useEffect, useCallback } from 'react';
import pointageAnomalieService from '@/api/pointage/pointageAnomalieService';

export function usePointageAnomalies(initialFilters = {}) {
    const [filters, setFilters] = useState({
        idSite: 0,
        idPeriodePaie: 0,
        datePointage: '',
        search: '',
        ...initialFilters
    });

    const [anomalies, setAnomalies] = useState([]);
    const [stats, setStats] = useState({ total: 0, incomplets: 0, retards: 0, absences: 0 });
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const loadAnomalies = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await pointageAnomalieService.getAnomalies(filters);
            if (data) {
                setAnomalies(data.anomalies || []);
                setStats(data.stats || { total: 0, incomplets: 0, retards: 0, absences: 0 });
            }
        } catch (err) {
            console.error('Erreur chargement anomalies:', err);
            setError('Impossible de charger les anomalies de pointage.');
        } finally {
            setLoading(false);
        }
    }, [filters]);

    useEffect(() => {
        loadAnomalies();
    }, [loadAnomalies]);

    const regulariser = async (idPointage, payload) => {
        try {
            const res = await pointageAnomalieService.regulariser(idPointage, payload);
            if (res && res.success) {
                await loadAnomalies();
                return true;
            }
            return false;
        } catch (err) {
            console.error('Erreur régularisation anomalie:', err);
            return false;
        }
    };

    const updateFilters = (newFilters) => {
        setFilters(prev => ({ ...prev, ...newFilters }));
    };

    return {
        anomalies,
        stats,
        filters,
        loading,
        error,
        updateFilters,
        loadAnomalies,
        regulariser
    };
}

export default usePointageAnomalies;
