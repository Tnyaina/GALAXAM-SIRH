import { useState, useEffect, useCallback } from 'react';
import pointageSyntheseService from '@/features/attendance/api/pointageSyntheseService';

export function usePointageSynthese(initialPeriodeId = 1, initialSiteId = 0) {
    const [selectedPeriode, setSelectedPeriode] = useState(initialPeriodeId);
    const [selectedSite, setSelectedSite] = useState(initialSiteId);
    const [syntheseData, setSyntheseData] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const loadSynthese = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        setError(null);
        try {
            const res = await pointageSyntheseService.getSynthese({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            setSyntheseData(res?.synthese || []);
        } catch (err) {
            console.error('Erreur chargement synthèse:', err);
            setError('Impossible de charger la synthèse mensuelle.');
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite]);

    useEffect(() => {
        loadSynthese();
    }, [loadSynthese]);

    return {
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        syntheseData,
        loading,
        error,
        loadSynthese
    };
}

export default usePointageSynthese;
