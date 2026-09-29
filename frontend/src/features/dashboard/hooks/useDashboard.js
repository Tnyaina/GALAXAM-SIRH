import { useState, useEffect, useCallback } from 'react';
import dashboardService from '@/features/dashboard/api/dashboardService';
import { useAuth } from '@/context/AuthContext';
import { getErrorMessage } from '@/api/apiClient';

/**
 * useDashboard — Hook custom pour les données du dashboard.
 * Gère le chargement, le refresh, et les états de loading/erreur.
 */
export function useDashboard() {
    // État local du serveur
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [lastRefresh, setLastRefresh] = useState(null);

    // Action de chargement réutilisée par le premier affichage et le rafraîchissement.
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
            setError(getErrorMessage(err, 'Erreur lors du chargement du dashboard.'));
        } finally {
            setLoading(false);
        }
    }, []);

    // Effet : charge les données et annule la requête au démontage.
    useEffect(() => {
        const controller = new AbortController();
        load(controller.signal);
        return () => controller.abort();
    }, [load]);

    return { data, loading, error, refresh: () => load(), lastRefresh };
}

/**
 * useNotifications — Hook pour les notifications RH.
 * Intègre l'Optimistic UI et la gestion du Rollback.
 */
export function useNotifications() {
    // Contexte
    const { isAuthenticated, hasPermission } = useAuth();

    // État local du serveur
    const [notifications, setNotifications] = useState([]);
    const [totalNonLues, setTotalNonLues] = useState(0);
    const [loading, setLoading] = useState(true);

    // Actions de chargement et de mise à jour optimiste
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
            if (err.code === 'ERR_NETWORK' || err.message === 'Network Error') {
                console.warn('[Notifications] Serveur en cours de reconnexion...');
                return;
            }
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

    // Effet : charge, rafraîchit périodiquement et nettoie les abonnements.
    useEffect(() => {
        const controller = new AbortController();
        load(false, controller.signal);
        
        // Rafraîchissement automatique en arrière-plan toutes les 15 secondes
        const timer = setInterval(() => {
            load(true);
        }, 15000);

        // Rafraîchissement manuel déclenché par d'autres composants
        const handleForceRefresh = () => {
            load(true);
        };
        window.addEventListener('refresh-notifications', handleForceRefresh);

        return () => {
            controller.abort();
            clearInterval(timer);
            window.removeEventListener('refresh-notifications', handleForceRefresh);
        };
    }, [load]);

    return { 
        notifications, 
        totalNonLues, 
        loading, 
        refresh: () => load(false), 
        marquerToutesLues, 
        marquerUne,
        annulerMarquage
    };
}
