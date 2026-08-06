import apiClient from './apiClient';

const documentRhService = {
    // Referentiel Types de documents
    getTypesDocument: async (actifUniquement = false) => {
        const response = await apiClient.get('/ref-types-documents', {
            params: { actifUniquement }
        });
        return response.data;
    },

    createTypeDocument: async (data) => {
        const response = await apiClient.post('/ref-types-documents', data);
        return response.data;
    },

    updateTypeDocument: async (id, data) => {
        const response = await apiClient.put(`/ref-types-documents/${id}`, data);
        return response.data;
    },

    deleteTypeDocument: async (id) => {
        const response = await apiClient.delete(`/ref-types-documents/${id}`);
        return response.data;
    },

    // Documents RH par employé
    getDocumentsByEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/documents`);
        return response.data; // { documents, obligatoiresManquants, totalDocuments }
    },

    uploadDocument: async (idEmploye, file, idTypeDocument, dateDocument, commentaire) => {
        const formData = new FormData();
        formData.append('file', file);
        formData.append('idTypeDocument', idTypeDocument);
        if (dateDocument) formData.append('dateDocument', dateDocument);
        if (commentaire) formData.append('commentaire', commentaire);

        const response = await apiClient.post(`/employes/${idEmploye}/documents`, formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        });
        return response.data;
    },

    deleteDocument: async (idDocument) => {
        const response = await apiClient.delete(`/documents-rh/${idDocument}`);
        return response.data;
    },

    downloadDocument: async (idDocument, nomOriginal) => {
        const response = await apiClient.get(`/documents-rh/${idDocument}/download`, {
            responseType: 'blob'
        });
        const blob = new Blob([response.data], { 
            type: response.headers['content-type'] || 'application/octet-stream' 
        });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', nomOriginal || `document-${idDocument}`);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    }
};

export default documentRhService;
