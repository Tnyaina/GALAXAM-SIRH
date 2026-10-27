/**
 * dashboardService.js
 * Service frontend pour le Dashboard RH et les Notifications.
 */
import apiClient from '@/api/apiClient';

const dashboardService = {
    /**
     * Récupère toutes les données du dashboard en un seul appel.
     * GET /dashboard/summary
     */
    getDashboardSummary: async (signal) => {
        const response = await apiClient.get('/dashboard/summary', { signal });
        return response.data;
    },

    /**
     * Récupère les notifications non lues de l'utilisateur courant.
     * GET /notifications
     */
    getNotifications: async (signal) => {
        const response = await apiClient.get('/notifications', { signal });
        return response.data;
    },

    /**
     * Marquer TOUTES les notifications comme lues.
     * PUT /notifications/marquer-lues
     */
    marquerToutesLues: async () => {
        const response = await apiClient.put('/notifications/marquer-lues');
        return response.data;
    },

    /**
     * Marquer UNE notification comme lue.
     * PUT /notifications/{id}/lire
     */
    marquerLue: async (idNotification) => {
        const response = await apiClient.put(`/notifications/${idNotification}/lire`);
        return response.data;
    },
};

export default dashboardService;
