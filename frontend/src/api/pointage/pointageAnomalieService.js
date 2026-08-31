import apiClient from '../apiClient';

export const pointageAnomalieService = {
    getAnomalies: async (params) => {
        const response = await apiClient.get('/pointages/anomalies', { params });
        return response.data;
    },

    regulariser: async (idPointage, payload) => {
        const response = await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload);
        return response.data;
    }
};

export default pointageAnomalieService;
