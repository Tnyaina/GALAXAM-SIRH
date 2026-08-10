import apiClient from '@/api/apiClient';

const contratService = {
    // Referentiels Carrière
    getAllReferentielsCarriere: async () => {
        const response = await apiClient.get('/ref-carriere/all');
        return response.data;
    },

    getCategoriesPro: async (actifUniquement = false) => {
        const response = await apiClient.get('/ref-carriere/categories-pro', { params: { actifUniquement } });
        return response.data;
    },

    createCategoriePro: async (data) => {
        const response = await apiClient.post('/ref-carriere/categories-pro', data);
        return response.data;
    },

    updateCategoriePro: async (id, data) => {
        const response = await apiClient.put(`/ref-carriere/categories-pro/${id}`, data);
        return response.data;
    },

    getModesPaie: async () => {
        const response = await apiClient.get('/ref-carriere/modes-paie');
        return response.data;
    },

    getMotifsMouvement: async () => {
        const response = await apiClient.get('/ref-carriere/motifs-mouvement');
        return response.data;
    },

    getMotifsSortie: async () => {
        const response = await apiClient.get('/ref-carriere/motifs-sortie');
        return response.data;
    },

};

export default contratService;
