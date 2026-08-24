import { useState, useEffect, useCallback } from 'react';
import organisationService from '@/api/admin/organisationService';
import pointageParametreService from '@/api/admin/pointageParametreService';
import { useToast } from '@/components/ui/feedback/Toast';

export function useToleranceArrivee() {
    const [sites, setSites] = useState([]);
    const [tolerances, setTolerances] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [editingSiteId, setEditingSiteId] = useState(null);
    const [editHeure, setEditHeure] = useState('');
    const [saving, setSaving] = useState(false);

    const toast = useToast();

    const loadData = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const [sitesData, tolData] = await Promise.all([
                organisationService.getSites(),
                pointageParametreService.getTolerances()
            ]);
            setSites(sitesData || []);
            setTolerances(tolData || []);
        } catch (err) {
            setError("Erreur lors du chargement des tolérances d'arrivée des sites.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const startEdit = (site, currentTolerance) => {
        setEditingSiteId(site.idSite);
        const heure = currentTolerance?.heurePlafond 
            ? currentTolerance.heurePlafond.substring(0, 5) 
            : (site.heureDebutMatin ? site.heureDebutMatin.substring(0, 5) : '07:30');
        setEditHeure(heure);
    };

    const cancelEdit = () => {
        setEditingSiteId(null);
        setEditHeure('');
    };

    const saveTolerance = async (siteId) => {
        if (!editHeure) return;
        setSaving(true);
        try {
            await pointageParametreService.saveTolerance({
                idSite: siteId,
                heurePlafond: editHeure
            });
            toast.success("Tolérance d'arrivée enregistrée avec succès.");
            setEditingSiteId(null);
            await loadData();
        } catch (err) {
            toast.error(err?.response?.data || "Erreur lors de l'enregistrement de la tolérance.");
        } finally {
            setSaving(false);
        }
    };

    return {
        sites,
        tolerances,
        loading,
        error,
        editingSiteId,
        editHeure,
        setEditHeure,
        saving,
        startEdit,
        cancelEdit,
        saveTolerance,
        reload: loadData
    };
}
