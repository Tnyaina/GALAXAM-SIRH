import { useState, useEffect, useCallback } from 'react';
import dashboardService from '@/api/dashboardService';

/**
 * useDashboard — Hook custom pour les données du dashboard.
 * Gère le chargement, le refresh, et les états de loading/erreur.
 */
export function useDashboard() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [lastRefresh, setLastRefresh] = useState(null);

    const load = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const result = await dashboardService.getDashboardSummary();
            setData(result);
            setLastRefresh(new Date());
        } catch (err) {
            console.error('[Dashboard] Erreur chargement:', err);
            setError(err.response?.data || err.message || 'Erreur lors du chargement du dashboard.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        load();
    }, [load]);

    return { data, loading, error, refresh: load, lastRefresh };
}

/**
 * useNotifications — Hook pour les notifications RH.
 * Charge les notifs au montage, fournit des actions marquer-lue.
 */
export function useNotifications() {
    const [notifications, setNotifications] = useState([]);
    const [totalNonLues, setTotalNonLues] = useState(0);
    const [loading, setLoading] = useState(true);

    const load = useCallback(async () => {
        setLoading(true);
        try {
            const result = await dashboardService.getNotifications();
            setNotifications(result.notifications || []);
            setTotalNonLues(result.totalNonLues || 0);
        } catch (err) {
            console.error('[Notifications] Erreur:', err);
        } finally {
            setLoading(false);
        }
    }, []);

    const marquerToutesLues = async () => {
        try {
            await dashboardService.marquerToutesLues();
            setNotifications([]);
            setTotalNonLues(0);
        } catch (err) {
            console.error('[Notifications] Erreur marquage:', err);
        }
    };

    const marquerUne = async (id) => {
        try {
            await dashboardService.marquerLue(id);
            setNotifications(prev => prev.filter(n => n.idNotification !== id));
            setTotalNonLues(prev => Math.max(0, prev - 1));
        } catch (err) {
            console.error('[Notifications] Erreur marquage unique:', err);
        }
    };

    useEffect(() => {
        load();
    }, [load]);

    return { notifications, totalNonLues, loading, refresh: load, marquerToutesLues, marquerUne };
}
