import apiClient from '@/api/apiClient';

/** API unique du domaine Pointage : saisie, anomalies et fiche collaborateur. */
const attendanceService = {
    getGrilleSaisie: async ({ idSite, date }) => {
        const response = await apiClient.get('/pointages/saisie', { params: { idSite, date } });
        return response.data;
    },

    enregistrerBatch: async (pointages) => {
        const response = await apiClient.post('/pointages/saisie/batch', pointages);
        return response.data;
    },

    getAnomalies: async (params, options = {}) => {
        const response = await apiClient.get('/pointages/anomalies', { params, signal: options?.signal });
        return response.data;
    },

    regulariser: async (idPointage, payload) => {
        const response = await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload);
        return response.data;
    },

    getFicheEmploye: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}`, { params });
        return response.data;
    },

    exportExcel: async (idEmploye, params = {}) => {
        const response = await apiClient.get(`/pointages/employes/${idEmploye}/export/excel`, { params, responseType: 'blob' });
        return response.data;
    }
};

export default attendanceService;
