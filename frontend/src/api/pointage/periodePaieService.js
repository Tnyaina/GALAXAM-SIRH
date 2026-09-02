import apiClient from '@/api/apiClient';

/**
 * Service API frontend canonique pour les périodes de paie.
 * Normalisé sous @/api/pointage/periodePaieService.js
 */
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
    },

    /**
     * Simule et calcule côté serveur le nombre de jours ouvrables et calendaires (Backend-Driven)
     */
    simulerPeriode: async (dateDebut, dateFin) => {
        const response = await apiClient.get('/periodes/simuler', {
            params: { dateDebut, dateFin }
        });
        return response.data;
    }
};

export default periodePaieService;
