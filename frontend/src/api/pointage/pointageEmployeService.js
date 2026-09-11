import apiClient from '../apiClient';

export const pointageEmployeService = {
    getFicheEmploye: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}`, { params });
        return response.data;
    },

    exportExcel: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}/export/excel`, {
            params,
            responseType: 'blob'
        });
        return response.data;
    }
};

export default pointageEmployeService;
