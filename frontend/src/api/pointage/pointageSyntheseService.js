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
    },

    getSemaineDetail: async ({ idPeriodePaie, dateDebut, dateFin, idSite }) => {
        const response = await apiClient.get('/pointages/synthese/semaine-detail', {
            params: { idPeriodePaie, dateDebut, dateFin, idSite }
        });
        return response.data;
    },

    validerSemaine: async ({ idPeriodePaie, dateDebut, dateFin, idSite }) => {
        const response = await apiClient.post('/pointages/synthese/semaines/valider', {
            idPeriodePaie,
            dateDebut,
            dateFin,
            idSite
        });
        return response.data;
    },

    devaliderSemaine: async ({ idPeriodePaie, dateDebut }) => {
        const response = await apiClient.post('/pointages/synthese/semaines/devalider', {
            idPeriodePaie,
            dateDebut
        });
        return response.data;
    }
};

export default pointageSyntheseService;
