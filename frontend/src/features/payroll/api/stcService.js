import apiClient from '@/api/apiClient';

/**
 * Service API pour le solde de tout compte et son décompte — GALAXAM SIRH
 * Signatures vérifiées sur mg.galaxam.sirh.api.controller.paie.StcController
 */
export const stcService = {
    /**
     * Récupère les données préparatoires administratives, contractuelles et financières
     * pour le STC d'un collaborateur (ancienneté, prorata, solde congés, TCONGE, prêts à solder).
     *
     * @param {number} idEmploye
     * @param {string} [dateDepart] - format YYYY-MM-DD
     * @param {string} [motif] - DEMISSION, LICENCIEMENT, FIN_CONTRAT, RETRAITE, etc.
     */
    preparerStc: async (idEmploye, dateDepart = null, motif = 'DEMISSION') => {
        const params = {};
        if (dateDepart) {
            let formattedDate = dateDepart;
            if (typeof dateDepart === 'string') {
                formattedDate = dateDepart.includes('T') ? dateDepart.split('T')[0] : dateDepart.substring(0, 10);
            } else if (dateDepart instanceof Date && !isNaN(dateDepart.getTime())) {
                formattedDate = `${dateDepart.getFullYear()}-${String(dateDepart.getMonth() + 1).padStart(2, '0')}-${String(dateDepart.getDate()).padStart(2, '0')}`;
            }
            params.dateDepart = formattedDate;
        }
        if (motif) params.motif = motif;
        const response = await apiClient.get(`paie/stc/preparer/${idEmploye}`, { params });
        return response.data;
    },

    /**
     * Simule le calcul du STC sans impacter les prêts ni persister d'enregistrement.
     *
     * @param {Object} payload - StcCalculRequest
     */
    simulerStc: async (payload) => {
        const response = await apiClient.post('paie/stc/calculer', {
            ...payload,
            simulation: true,
        });
        return response.data;
    },

    /**
     * Valide et émet définitivement le STC :
     * - Génère le reçu officiel numéroté (ex: STC-2026-0001)
     * - Impute les acomptes versés non encore imputés
     * - Enregistre le décompte dans paie_stc
     *
     * @param {Object} payload - StcCalculRequest
     */
    validerStc: async (payload) => {
        const response = await apiClient.post('paie/stc/valider', {
            ...payload,
            simulation: false,
        });
        return response.data;
    },

    enregistrerReglement: async (idStc, payload) => {
        await apiClient.post(`paie/stc/${idStc}/regler`, payload);
    },

    /**
     * Liste tous les reçus de solde de tout compte émis.
     */
    listerStc: async () => {
        const response = await apiClient.get('paie/stc/liste');
        return response.data;
    },

    /**
     * Récupère les STC émis pour un employé spécifique.
     *
     * @param {number} idEmploye
     */
    listerStcEmploye: async (idEmploye) => {
        const response = await apiClient.get(`paie/stc/employe/${idEmploye}`);
        return response.data;
    },

    /**
     * Récupère le décompte détaillé d'un STC validé.
     *
     * @param {number} idStc
     */
    obtenirRecuStc: async (idStc) => {
        const response = await apiClient.get(`paie/stc/${idStc}/recu`);
        return response.data;
    },
};

export default stcService;
