import apiClient from '../apiClient';

export const pointageSyntheseService = {
    getSynthese: async ({ idPeriodePaie, idSite }) => {
        const response = await apiClient.get('/pointages/synthese', {
            params: { idPeriodePaie, idSite }
        });
        return response.data;
    },

    getSemaines: async ({ idPeriodePaie, idSite }) => {
        const response = await apiClient.get('/pointages/synthese/semaines', {
            params: { idPeriodePaie, idSite }
        });
        return response.data;
    }
};

export default pointageSyntheseService;
