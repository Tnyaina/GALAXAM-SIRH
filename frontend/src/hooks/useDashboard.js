import { useState, useEffect, useCallback } from 'react';
import dashboardService from '@/api/dashboardService';
import { useAuth } from '@/context/AuthContext';

/**
 * useDashboard — Hook custom pour les données du dashboard.
 * Gère le chargement, le refresh, et les états de loading/erreur.
 */
export function useDashboard() {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [lastRefresh, setLastRefresh] = useState(null);

    const load = useCallback(async (signal) => {
        setLoading(true);
        setError(null);
        try {
            const result = await dashboardService.getDashboardSummary(signal);
            setData(result);
            setLastRefresh(new Date());
        } catch (err) {
            if (err.name === 'CanceledError' || err.__CANCEL__) return;
            console.error('[Dashboard] Erreur chargement:', err);
            setError(err.response?.data || err.message || 'Erreur lors du chargement du dashboard.');
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        const controller = new AbortController();
        load(controller.signal);
        return () => controller.abort();
    }, [load]);

    return { data, loading, error, refresh: (sig) => load(sig), lastRefresh };
}

/**
 * useNotifications — Hook pour les notifications RH.
 * Intègre l'Optimistic UI et la gestion du Rollback.
 */
export function useNotifications() {
    const { isAuthenticated, hasPermission } = useAuth();
    const [notifications, setNotifications] = useState([]);
    const [totalNonLues, setTotalNonLues] = useState(0);
    const [loading, setLoading] = useState(true);

    const load = useCallback(async (isBackground = false, signal) => {
        if (!isAuthenticated || !hasPermission('EMPLOYE_READ')) {
            setLoading(false);
            return;
        }
        if (!isBackground) setLoading(true);
        try {
            const result = await dashboardService.getNotifications(signal);
            setNotifications(result.notifications || []);
            setTotalNonLues(result.totalNonLues || 0);
        } catch (err) {
            if (err.name === 'CanceledError' || err.__CANCEL__) return;
            console.error('[Notifications] Erreur:', err);
        } finally {
            if (!isBackground) setLoading(false);
        }
    }, [isAuthenticated, hasPermission]);

    const marquerToutesLues = async () => {
        const previousNotifs = notifications;
        const previousTotal = totalNonLues;

        // Optimistic UI : Vider immédiatement
        setNotifications([]);
        setTotalNonLues(0);

        try {
            await dashboardService.marquerToutesLues();
        } catch (err) {
            console.error('[Notifications] Erreur marquage:', err);
            // Rollback en cas d'échec
            setNotifications(previousNotifs);
            setTotalNonLues(previousTotal);
        }
    };

    const marquerUne = async (id) => {
        const previousNotifs = notifications;
        const previousTotal = totalNonLues;

        // Optimistic UI : Supprimer immédiatement l'élément sélectionné
        setNotifications(prev => prev.filter(n => n.idNotification !== id));
        setTotalNonLues(prev => Math.max(0, prev - 1));

        try {
            await dashboardService.marquerLue(id);
        } catch (err) {
            console.error('[Notifications] Erreur marquage unique:', err);
            // Rollback en cas d'échec
            setNotifications(previousNotifs);
            setTotalNonLues(previousTotal);
        }
    };

    // Permet d'annuler le marquage d'une notification (Undo)
    const annulerMarquage = async (notification) => {
        if (!notification) return;
        setNotifications(prev => [notification, ...prev]);
        setTotalNonLues(prev => prev + 1);

        try {
            if (dashboardService.marquerNonLue) {
                await dashboardService.marquerNonLue(notification.idNotification);
            }
        } catch (err) {
            console.error('[Notifications] Erreur annulation marquage:', err);
        }
    };

    useEffect(() => {
        const controller = new AbortController();
        load(false, controller.signal);
        
        // Rafraîchissement automatique en arrière-plan toutes les 15 secondes
        const timer = setInterval(() => {
            load(true);
        }, 15000);

        return () => {
            controller.abort();
            clearInterval(timer);
        };
    }, [load]);

    return { 
        notifications, 
        totalNonLues, 
        loading, 
        refresh: load, 
        marquerToutesLues, 
        marquerUne,
        annulerMarquage
    };
}