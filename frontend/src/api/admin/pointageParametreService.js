import apiClient from '@/api/apiClient';

const pointageParametreService = {
    // ----------------------------------------------------
    // TOLÉRANCES D'ARRIVÉE ANTICIPÉE PAR SITE
    // ----------------------------------------------------
    getTolerances: async () => {
        const response = await apiClient.get('/pointages/parametres/tolerances');
        return response.data;
    },
    saveTolerance: async (data) => {
        const response = await apiClient.post('/pointages/parametres/tolerances', data);
        return response.data;
    },

    // ----------------------------------------------------
    // DÉROGATIONS INDIVIDUELLES D'INDEMNITÉS (NIVEAU 2)
    // ----------------------------------------------------
    getDerogations: async () => {
        const response = await apiClient.get('/pointages/parametres/derogations');
        return response.data;
    },
    createDerogation: async (data) => {
        const response = await apiClient.post('/pointages/parametres/derogations', data);
        return response.data;
    },
    updateDerogation: async (id, data) => {
        const response = await apiClient.put(`/pointages/parametres/derogations/${id}`, data);
        return response.data;
    },
    deleteDerogation: async (id) => {
        const response = await apiClient.delete(`/pointages/parametres/derogations/${id}`);
        return response.data;
    }
};

export default pointageParametreService;
