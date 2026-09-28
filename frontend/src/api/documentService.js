import apiClient from './apiClient';

const documentService = {
    async getContratData(idContrat) {
        return (await apiClient.get(`documents/contrat/${idContrat}`)).data;
    },
    async downloadContratDocx(idContrat, filename = 'Contrat.docx') {
        let response;
        try {
            response = await apiClient.get(`documents/contrat/${idContrat}/docx`, { responseType: 'blob' });
        } catch (error) {
            if (error.response?.data instanceof Blob) {
                try { error.response.data = JSON.parse(await error.response.data.text()); } catch { /* conserve l'erreur HTTP */ }
            }
            throw error;
        }
        const url = URL.createObjectURL(response.data);
        const link = document.createElement('a');
        link.href = url; link.download = filename;
        document.body.appendChild(link); link.click(); link.remove();
        URL.revokeObjectURL(url);
    },
    /**
     * Récupère les données préparées pour l'Attestation de travail (100% backend-driven)
     */
    async getAttestationData(idEmploye, typeAnciennete = 'contrat', inclureSalaire = false) {
        const response = await apiClient.get(`documents/attestation/${idEmploye}`, {
            params: { typeAnciennete, inclureSalaire },
        });
        return response.data;
    },

    /**
     * Télécharge le fichier Word (.docx) officiel de l'Attestation de travail
     */
    async downloadAttestationDocx(idEmploye, typeAnciennete = 'contrat', inclureSalaire = false, filename = 'Attestation_Travail.docx') {
        const response = await apiClient.get(`documents/attestation/${idEmploye}/docx`, {
            params: { typeAnciennete, inclureSalaire },
            responseType: 'blob',
        });
        const blob = new Blob([response.data], {
            type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    },

    /**
     * Récupère les données préparées pour le Certificat de travail (100% backend-driven)
     */
    async getCertificatData(idContrat, typeAnciennete = 'contrat') {
        const response = await apiClient.get(`documents/certificat/${idContrat}`, {
            params: { typeAnciennete },
        });
        return response.data;
    },

    /**
     * Télécharge le fichier Word (.docx) officiel du Certificat de travail
     */
    async downloadCertificatDocx(idContrat, typeAnciennete = 'contrat', filename = 'Certificat_Travail.docx') {
        const response = await apiClient.get(`documents/certificat/${idContrat}/docx`, {
            params: { typeAnciennete },
            responseType: 'blob',
        });
        const blob = new Blob([response.data], {
            type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        });
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.setAttribute('download', filename);
        document.body.appendChild(link);
        link.click();
        link.remove();
        window.URL.revokeObjectURL(url);
    },
};

export default documentService;
