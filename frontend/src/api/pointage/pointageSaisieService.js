import apiClient from '@/api/apiClient';

const pointageSaisieService = {
    /**
     * Récupère la grille de saisie quotidienne pour un site et une date.
     */
    getGrilleSaisie: async (params = {}) => {
        const response = await apiClient.get('/pointages/saisie', { params });
        return response.data;
    },

    /**
     * Sauvegarde par lot les pointages de la journée.
     */
    saveGrilleSaisie: async (pointages) => {
        const response = await apiClient.post('/pointages/saisie', pointages);
        return response.data;
    },

    /**
     * Récupère la vue Gantt par site, date et granularité (jour, semaine, mois).
     */
    getGanttView: async (params = {}) => {
        const response = await apiClient.get('/pointages/gantt', { params });
        return response.data;
    }
};

export default pointageSaisieService;
