import apiClient from '../apiClient';

export const pointageEmployeService = {
    getFicheEmploye: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}`, { params });
        return response.data;
    }
};

export default pointageEmployeService;
