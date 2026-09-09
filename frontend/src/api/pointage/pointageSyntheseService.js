import apiClient from '../apiClient';

export const pointageSyntheseService = {
    getSynthese: async ({ idPeriodePaie, idSite, search, filtre, page = 1, size = 25 }) => {
        const response = await apiClient.get('/pointages/synthese', {
            params: { idPeriodePaie, idSite, search, filtre, page, size }
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
            idPeriodePaie: Number(idPeriodePaie),
            dateDebut,
            dateFin,
            idSite: idSite != null && idSite !== '' ? Number(idSite) : undefined
        });
        return response.data;
    },

    devaliderSemaine: async ({ idPeriodePaie, dateDebut }) => {
        const response = await apiClient.post('/pointages/synthese/semaines/devalider', {
            idPeriodePaie: Number(idPeriodePaie),
            dateDebut
        });
        return response.data;
    },

    calculerTout: async ({ idPeriodePaie, idSite }) => {
        const response = await apiClient.post('/pointages/synthese/calculer-tout', {
            idPeriodePaie: Number(idPeriodePaie),
            idSite: idSite != null && idSite !== '' ? Number(idSite) : undefined
        });
        return response.data;
    },

    transmettreDaf: async ({ idPeriodePaie, idSite }) => {
        const response = await apiClient.post('/pointages/synthese/transmettre-daf', {
            idPeriodePaie: Number(idPeriodePaie),
            idSite: idSite != null && idSite !== '' ? Number(idSite) : undefined
        });
        return response.data;
    },

    validerDaf: async ({ idPeriodePaie }) => {
        const response = await apiClient.post('/pointages/synthese/valider-daf', {
            idPeriodePaie: Number(idPeriodePaie)
        });
        return response.data;
    },

    rejeterDaf: async ({ idPeriodePaie, motif }) => {
        const response = await apiClient.post('/pointages/synthese/rejeter-daf', {
            idPeriodePaie: Number(idPeriodePaie),
            motif
        });
        return response.data;
    }
};

export default pointageSyntheseService;
