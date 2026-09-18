import apiClient from '@/api/apiClient';

/**
 * Service API pour le Solde de Tout Compte (STC) & Quittance Libératoire — GALAXAM SIRH
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
        if (dateDepart) params.dateDepart = dateDepart;
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
     * - Clôture et apure intégralement les prêts d'entreprise en cours
     * - Solde les avances sur salaire
     * - Enregistre la quittance libératoire dans paie_stc
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
     * Récupère la quittance libératoire financière officielle d'un STC validé.
     *
     * @param {number} idStc
     */
    obtenirRecuStc: async (idStc) => {
        const response = await apiClient.get(`paie/stc/${idStc}/recu`);
        return response.data;
    },
};

export default stcService;
