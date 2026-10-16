import apiClient from '@/api/apiClient';

/**
 * Service API pour la gestion et le suivi des dossiers de sortie — GALAXAM SIRH
 */
export const dossierSortieService = {
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
     * Met à jour les formalités d'offboarding (restitution matériel, badge, clés).
     *
     * @param {number} id
     * @param {Object} formalites - clé / valeur des restitutions
     */
    mettreAJourOffboarding: async (id, formalites) => {
        const response = await apiClient.put(`paie/dossier-sortie/${id}/offboarding`, formalites);
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
};

export default dossierSortieService;
