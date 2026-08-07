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

    // Contrats de travail
    getContratsByEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/contrats`);
        return response.data;
    },

    getContratActif: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/contrat-actif`);
        return response.data;
    },

    createContrat: async (idEmploye, contratData) => {
        const response = await apiClient.post(`/employes/${idEmploye}/contrats`, contratData);
        return response.data;
    },

    updateContrat: async (idContrat, contratData) => {
        const response = await apiClient.put(`/contrats-travail/${idContrat}`, contratData);
        return response.data;
    },

    deleteContrat: async (idContrat) => {
        const response = await apiClient.delete(`/contrats-travail/${idContrat}`);
        return response.data;
    },

    // Affectations de carrière
    getAffectationsByEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/affectations`);
        return response.data;
    },

    getAffectationCourante: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/affectation-courante`);
        return response.data;
    },

    createAffectation: async (idEmploye, affectationData) => {
        const response = await apiClient.post(`/employes/${idEmploye}/affectations`, affectationData);
        return response.data;
    },

    updateAffectation: async (idAffectation, affectationData) => {
        const response = await apiClient.put(`/affectations-carriere/${idAffectation}`, affectationData);
        return response.data;
    }
};

export default contratService;
