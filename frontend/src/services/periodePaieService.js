import apiClient from '@/api/apiClient';

const periodePaieService = {
    /**
     * Récupère la liste de toutes les périodes de paie
     */
    getAllPeriodes: async () => {
        const response = await apiClient.get('/periodes');
        return response.data;
    },

    /**
     * Crée une nouvelle période de paie
     */
    createPeriode: async (periodeData) => {
        const response = await apiClient.post('/periodes', periodeData);
        return response.data;
    },

    /**
     * Met à jour le statut d'une période de paie
     */
    updateStatut: async (id, statutCode) => {
        const response = await apiClient.put(`/periodes/${id}/statut`, { statutCode });
        return response.data;
    }
};

export default periodePaieService;
