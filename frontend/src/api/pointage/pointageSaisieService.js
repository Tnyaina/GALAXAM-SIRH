import apiClient from '../apiClient';

export const pointageSaisieService = {
    getGrilleSaisie: async ({ idSite, date }) => {
        const response = await apiClient.get('/pointages/saisie', {
            params: { idSite, date }
        });
        return response.data;
    },

    enregistrerBatch: async (pointages) => {
        const response = await apiClient.post('/pointages/saisie/batch', pointages);
        return response.data;
    }
};

export default pointageSaisieService;
