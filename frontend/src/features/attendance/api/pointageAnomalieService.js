import apiClient from '@/api/apiClient';

export const pointageAnomalieService = {
    getAnomalies: async (params, options = {}) => {
        const response = await apiClient.get('/pointages/anomalies', {
            params,
            signal: options?.signal
        });
        return response.data;
    },

    regulariser: async (idPointage, payload) => {
        const response = await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload);
        return response.data;
    }
};

export default pointageAnomalieService;
