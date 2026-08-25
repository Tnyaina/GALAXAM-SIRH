import apiClient from '@/api/apiClient';

const pointageService = {
    /**
     * Récupère la fiche de présence dynamique d'un employé.
     * @param {number|string} idEmploye 
     * @param {object} params { date, period }
     */
    getFichePointageEmploye: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}`, { params });
        return response.data;
    }
};

export default pointageService;
