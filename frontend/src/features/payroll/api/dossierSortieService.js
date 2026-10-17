import apiClient from '@/api/apiClient';

/**
 * Service API pour la gestion et le suivi des dossiers de sortie — GALAXAM SIRH
 */
export const dossierSortieService = {
    /** Identité minimale des salariés actifs, accessible au rôle paie pour ouvrir un dossier. */
    rechercherEmployes: async (q = '') => {
        const response = await apiClient.get('paie/dossier-sortie/employes', { params: { q } });
        return response.data;
    },
    /**
     * Liste tous les dossiers de sortie enregistrés.
     */
    listerDossiers: async () => {
        const response = await apiClient.get('paie/dossier-sortie/liste');
        return response.data;
    },

    /**
     * Récupère un dossier de sortie par son identifiant avec sa checklist dynamique.
     *
     * @param {number} id
     */
    obtenirDossier: async (id) => {
        const response = await apiClient.get(`paie/dossier-sortie/${id}`);
        return response.data;
    },

    /**
     * Récupère ou prépare la vue du dossier de sortie d'un salarié.
     *
     * @param {number} idEmploye
     * @param {string} [dateDepart] - format YYYY-MM-DD
     * @param {string} [motif]
     */
    obtenirDossierEmploye: async (idEmploye, dateDepart = null, motif = null) => {
        const params = {};
        if (dateDepart) params.dateDepart = dateDepart;
        if (motif) params.motif = motif;
        const response = await apiClient.get(`paie/dossier-sortie/employe/${idEmploye}`, { params });
        return response.data;
    },

    /**
     * Sauvegarde (création ou mise à jour) du dossier préparatoire de sortie.
     *
     * @param {Object} payload - DossierSortieSaveRequest
     */
    sauvegarderDossier: async (payload) => {
        const response = await apiClient.post('paie/dossier-sortie', payload);
        return response.data;
    },

    /**
     * Enregistre la délivrance du certificat de travail légal (Art. 50 Loi 2024).
     *
     * @param {number} id
     * @param {Object} payload - { dateDelivrance, modeRemise }
     */
    enregistrerDelivranceCertificat: async (id, payload) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/certificat`, payload);
        return response.data;
    },



    /**
     * Lance la simulation du calcul financier du STC lié au dossier.
     *
     * @param {number} id - idDossierSortie
     * @returns {Promise<Object>} { referenceSimulation, version, decompte, snapshotData }
     */
    simulerStc: async (id) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/simuler`);
        return response.data;
    },

    /**
     * Valide et scelle transactionnellement le STC après confirmation explicite.
     *
     * @param {number} id - idDossierSortie
     * @param {Object} payload - { referenceSimulation, version, commentaire }
     * @returns {Promise<Object>} DossierSortieDTO mis à jour avec idStc et statut VALIDE
     */
    scellerStc: async (id, payload) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/sceller`, payload);
        return response.data;
    },

    obtenirSituationReglements: async (id) => {
        const response = await apiClient.get(`paie/dossier-sortie/${id}/reglements`);
        return response.data;
    },

    enregistrerReglement: async (id, payload, idempotencyKey) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/reglement`, payload, {
            headers: { 'X-Idempotency-Key': idempotencyKey },
        });
        return response.data;
    },

    annulerReglement: async (id, idReglement, motifAnnulation) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/reglement/${idReglement}/annuler`, {
            motifAnnulation,
        });
        return response.data;
    },

    cloturerDossier: async (id, payload) => {
        const response = await apiClient.post(`paie/dossier-sortie/${id}/cloturer`, payload);
        return response.data;
    },

    telechargerDocument: async (id, typeDocument, idReglement = null) => {
        const routes = {
            decompte: ['decompte/pdf', 'pdf'],
            recuProjet: ['recu-stc/pdf', 'pdf'],
            certificatPdf: ['certificat/pdf', 'pdf'],
            certificatDocx: ['certificat/docx', 'docx'],
            bordereau: ['bordereau-remise/pdf', 'pdf'],
            quittance: [`quittance/${idReglement}/pdf`, 'pdf'],
        };
        const selected = routes[typeDocument];
        if (!selected || (typeDocument === 'quittance' && !idReglement)) throw new Error('Document inconnu.');
        let response;
        try {
            response = await apiClient.get(`paie/dossier-sortie/${id}/documents/${selected[0]}`, {
                responseType: 'blob',
            });
        } catch (error) {
            if (error.response?.data instanceof Blob) {
                try { error.response.data = JSON.parse(await error.response.data.text()); }
                catch { /* Une réponse non JSON conserve le message HTTP standard. */ }
            }
            throw error;
        }
        const url = URL.createObjectURL(response.data);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${typeDocument}-${id}${idReglement ? `-${idReglement}` : ''}.${selected[1]}`;
        document.body.appendChild(link);
        link.click();
        link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    },
};

export default dossierSortieService;
