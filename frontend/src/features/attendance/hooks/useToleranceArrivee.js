import { useState, useEffect, useCallback } from 'react';
import organisationService from '@/features/organization/api/organisationService';
import pointageParametreService from '@/features/administration/api/pointageParametreService';
import { useToast } from '@/components/ui/feedback/Toast';

export function useToleranceArrivee() {
    // État local : sites, tolérances et édition en cours.
    const [sites, setSites] = useState([]);
    const [tolerances, setTolerances] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [editingSiteId, setEditingSiteId] = useState(null);
    const [editHeure, setEditHeure] = useState('');
    const [saving, setSaving] = useState(false);

    // Contexte
    const toast = useToast();

    // Chargement et actions d'édition.
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
            : '';
        setEditHeure(heure);
    };

    const cancelEdit = () => {
        setEditingSiteId(null);
        setEditHeure('');
    };

    const saveTolerance = async (siteId) => {
        if (!editHeure) return;
        const existing = tolerances.find(t => t.idSite === siteId);
        setSaving(true);
        try {
            await pointageParametreService.saveTolerance({
                idSite: siteId,
                heurePlafond: editHeure,
                estActif: existing ? existing.estActif !== false : true
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

    const setToleranceEnabled = async (siteId, enabled) => {
        const existing = tolerances.find(t => t.idSite === siteId);
        if (!existing?.heurePlafond) return;
        setSaving(true);
        try {
            await pointageParametreService.saveTolerance({
                idSite: siteId,
                heurePlafond: existing.heurePlafond.substring(0, 5),
                estActif: enabled
            });
            toast.success(enabled ? "Heure minimale activée." : "Heure minimale désactivée.");
            await loadData();
        } catch (err) {
            toast.error(err?.response?.data?.message || "Erreur lors du changement d'état.");
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
        setToleranceEnabled,
        reload: loadData
    };
}
