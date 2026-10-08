import apiClient from '@/api/apiClient';

const organisationService = {
    getReferentielsFormulaireEmploye: async () => {
        const response = await apiClient.get('/referentiel/formulaire-employe');
        return response.data;
    },

    // ----------------------------------------------------
    // FILIALES
    // ----------------------------------------------------
    getFiliales: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/filiales', { params: queryParams });
        return response.data;
    },
    createFiliale: async (data) => {
        const response = await apiClient.post('/referentiel/filiales', data);
        return response.data;
    },
    updateFiliale: async (id, data) => {
        const response = await apiClient.put(`/referentiel/filiales/${id}`, data);
        return response.data;
    },
    deactivateFiliale: async (id) => {
        const response = await apiClient.put(`/referentiel/filiales/${id}/desactiver`);
        return response.data;
    },
    activateFiliale: async (id) => {
        const response = await apiClient.put(`/referentiel/filiales/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // SITES
    // ----------------------------------------------------
    getSites: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/sites', { params: queryParams });
        return response.data;
    },
    createSite: async (data) => {
        const response = await apiClient.post('/referentiel/sites', data);
        return response.data;
    },
    updateSite: async (id, data) => {
        const response = await apiClient.put(`/referentiel/sites/${id}`, data);
        return response.data;
    },
    deactivateSite: async (id) => {
        const response = await apiClient.put(`/referentiel/sites/${id}/desactiver`);
        return response.data;
    },
    activateSite: async (id) => {
        const response = await apiClient.put(`/referentiel/sites/${id}/activer`);
        return response.data;
    },
    toggleSitePlancher: async (id, data) => {
        const response = await apiClient.put(`/referentiel/sites/${id}/plancher`, data);
        return response.data;
    },

    // ----------------------------------------------------
    // DÉPARTEMENTS
    // ----------------------------------------------------
    getDepartements: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/departements', { params: queryParams });
        return response.data;
    },
    createDepartement: async (data) => {
        const response = await apiClient.post('/referentiel/departements', data);
        return response.data;
    },
    updateDepartement: async (id, data) => {
        const response = await apiClient.put(`/referentiel/departements/${id}`, data);
        return response.data;
    },
    deactivateDepartement: async (id) => {
        const response = await apiClient.put(`/referentiel/departements/${id}/desactiver`);
        return response.data;
    },
    activateDepartement: async (id) => {
        const response = await apiClient.put(`/referentiel/departements/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // SERVICES
    // ----------------------------------------------------
    getServices: async (arg) => {
        let params = {};
        if (typeof arg === 'number' || (typeof arg === 'string' && /^\d+$/.test(arg))) {
            params = { idDepartement: arg };
        } else if (typeof arg === 'boolean') {
            params = { actifUniquement: arg };
        } else if (arg && typeof arg === 'object') {
            params = arg;
        }
        const response = await apiClient.get('/referentiel/services', { params });
        return response.data;
    },
    createService: async (data) => {
        const response = await apiClient.post('/referentiel/services', data);
        return response.data;
    },
    updateService: async (id, data) => {
        const response = await apiClient.put(`/referentiel/services/${id}`, data);
        return response.data;
    },
    deactivateService: async (id) => {
        const response = await apiClient.put(`/referentiel/services/${id}/desactiver`);
        return response.data;
    },
    activateService: async (id) => {
        const response = await apiClient.put(`/referentiel/services/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // FONCTIONS / POSTES
    // ----------------------------------------------------
    getFonctions: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/fonctions', { params: queryParams });
        return response.data;
    },
    createFonction: async (data) => {
        const response = await apiClient.post('/referentiel/fonctions', data);
        return response.data;
    },
    updateFonction: async (id, data) => {
        const response = await apiClient.put(`/referentiel/fonctions/${id}`, data);
        return response.data;
    },
    deactivateFonction: async (id) => {
        const response = await apiClient.put(`/referentiel/fonctions/${id}/desactiver`);
        return response.data;
    },
    activateFonction: async (id) => {
        const response = await apiClient.put(`/referentiel/fonctions/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // TYPES CONTRAT
    // ----------------------------------------------------
    getTypesContrat: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/types-contrat', { params: queryParams });
        return response.data;
    },
    createTypeContrat: async (data) => {
        const response = await apiClient.post('/referentiel/types-contrat', data);
        return response.data;
    },
    updateTypeContrat: async (id, data) => {
        const response = await apiClient.put(`/referentiel/types-contrat/${id}`, data);
        return response.data;
    },
    deactivateTypeContrat: async (id) => {
        const response = await apiClient.put(`/referentiel/types-contrat/${id}/desactiver`);
        return response.data;
    },
    activateTypeContrat: async (id) => {
        const response = await apiClient.put(`/referentiel/types-contrat/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // TYPES INTERVENANT
    // ----------------------------------------------------
    getTypesIntervenant: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/types-intervenant', { params: queryParams });
        return response.data;
    },
    createTypeIntervenant: async (data) => {
        const response = await apiClient.post('/referentiel/types-intervenant', data);
        return response.data;
    },
    updateTypeIntervenant: async (id, data) => {
        const response = await apiClient.put(`/referentiel/types-intervenant/${id}`, data);
        return response.data;
    },
    deactivateTypeIntervenant: async (id) => {
        const response = await apiClient.put(`/referentiel/types-intervenant/${id}/desactiver`);
        return response.data;
    },
    activateTypeIntervenant: async (id) => {
        const response = await apiClient.put(`/referentiel/types-intervenant/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // BANQUES
    // ----------------------------------------------------
    getBanques: async (params) => {
        const queryParams = typeof params === 'boolean' ? { actifUniquement: params } : params;
        const response = await apiClient.get('/referentiel/banques', { params: queryParams });
        return response.data;
    },
    createBanque: async (data) => {
        const response = await apiClient.post('/referentiel/banques', data);
        return response.data;
    },
    updateBanque: async (id, data) => {
        const response = await apiClient.put(`/referentiel/banques/${id}`, data);
        return response.data;
    },
    deactivateBanque: async (id) => {
        const response = await apiClient.put(`/referentiel/banques/${id}/desactiver`);
        return response.data;
    },
    activateBanque: async (id) => {
        const response = await apiClient.put(`/referentiel/banques/${id}/activer`);
        return response.data;
    }
};

export default organisationService;
