import apiClient from '@/api/apiClient';

const employeService = {
    getEmployes: async (params = {}) => {
        const response = await apiClient.get('/employes', { params });
        return response.data;
    },

    getEmployeStats: async (params = {}) => {
        const response = await apiClient.get('/employes/stats', { params });
        return response.data;
    },

    getEmployeFicheDetail: async (id) => {
        const response = await apiClient.get(`/employes/${id}`);
        return response.data;
    },

    getNextMatricule: async () => {
        const response = await apiClient.get('/employes/matricule/next');
        return response.data.matricule;
    },

    checkMatriculeExists: async (matricule) => {
        const response = await apiClient.get('/employes/matricule/check', { params: { matricule } });
        return response.data.exists;
    },

    checkMatriculeAvailable: async (matricule) => {
        const response = await apiClient.get('/employes/matricule/check', { params: { matricule } });
        return !response.data.exists;
    },

    createEmploye: async (data) => {
        const response = await apiClient.post('/employes', data);
        window.dispatchEvent(new Event('refresh-notifications'));
        return response.data;
    },

    updateEmploye: async (id, data) => {
        const response = await apiClient.put(`/employes/${id}`, data);
        return response.data;
    },

    desactiverEmploye: async (id, motif) => {
        const response = await apiClient.put(`/employes/${id}/desactiver`, { motif });
        return response.data;
    },

    reactiverEmploye: async (id) => {
        const response = await apiClient.put(`/employes/${id}/activer`);
        return response.data;
    },

    uploadPhoto: async (file) => {
        const formData = new FormData();
        formData.append('file', file);
        const response = await apiClient.post('/employes/upload-photo', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return response.data; // { photoUrl: "/employes/photos/uuid.ext" }
    }
};

export default employeService;
