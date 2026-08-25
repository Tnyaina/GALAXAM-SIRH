import apiClient from '@/api/apiClient';

const pointageAnomalieService = {
    getAnomalies: async (params = {}) => {
        const response = await apiClient.get('/pointages/anomalies', { params });
        return response.data;
    },

    regulariserAnomalie: async (idPointage, payload) => {
        const response = await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload);
        return response.data;
    },

    regulariserBatch: async (items) => {
        const response = await apiClient.put('/pointages/anomalies/regulariser-batch', items);
        return response.data;
    }
};

export default pointageAnomalieService;
