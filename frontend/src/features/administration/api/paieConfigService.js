import apiClient from '@/api/apiClient';

const paieConfigService = {
    // ----------------------------------------------------
    // EXERCICES FISCAUX & RÉGLEMENTATION LÉGALE (paie_config_legale)
    // ----------------------------------------------------
    /** Liste tous les exercices fiscaux (actifs + archivés) */
    getAllConfigsLegales: async () => {
        const response = await apiClient.get('/paie/config-legale');
        return response.data;
    },
    /** Retourne l'exercice actif à la date du jour */
    getConfigLegaleActive: async () => {
        const response = await apiClient.get('/paie/config-legale/active');
        return response.data;
    },
    /** Retourne l'exercice actif à une date donnée (format YYYY-MM-DD) */
    getConfigLegaleAt: async (date) => {
        const response = await apiClient.get('/paie/config-legale/active-at', { params: { date } });
        return response.data;
    },
    /** Crée un premier exercice fiscal (création initiale) */
    createConfigLegale: async (data) => {
        const response = await apiClient.post('/paie/config-legale', data);
        return response.data;
    },
    /**
     * Revalorisation par date d'effet.
     * Clôt automatiquement l'exercice actif (date_fin = dateDebut - 1j)
     * et crée le nouvel exercice à partir de dateDebut.
     */
    revaloriserConfigLegale: async (data) => {
        const response = await apiClient.post('/paie/config-legale/revaloriser', data);
        return response.data;
    },
    /** Correction immédiate d'un champ (sans créer de nouvelle version) */
    updateConfigLegale: async (id, data) => {
        const response = await apiClient.put(`/paie/config-legale/${id}`, data);
        return response.data;
    },
    /** Annule un exercice PROGRAMMÉ (date_debut dans le futur) — interdit sur EN_VIGUEUR et ARCHIVÉ */
    annulerConfigProgrammee: async (id) => {
        const response = await apiClient.delete(`/paie/config-legale/${id}`);
        return response.data;
    },

    /**
     * Liste les cotisations sociales.
     * @param {string|null} at - Date ISO (YYYY-MM-DD) d'effectivité optionnelle.
     *   Si fournie, retourne uniquement les taux actifs à cette date (résolution temporelle).
     *   Utiliser periode.startDate pour les déclarations historiques.
     *   Si absente, retourne tous les taux (pour la configuration admin).
     */
    getCotisations: async (at = null) => {
        const params = at ? { at } : {};
        const response = await apiClient.get('/referentiel/cotisations', { params });
        return response.data;
    },

    createCotisation: async (data) => {
        const response = await apiClient.post('/referentiel/cotisations', data);
        return response.data;
    },
    updateCotisation: async (id, data) => {
        const response = await apiClient.put(`/referentiel/cotisations/${id}`, data);
        return response.data;
    },
    deactivateCotisation: async (id) => {
        const response = await apiClient.put(`/referentiel/cotisations/${id}/desactiver`);
        return response.data;
    },
    activateCotisation: async (id) => {
        const response = await apiClient.put(`/referentiel/cotisations/${id}/activer`);
        return response.data;
    },
    getSiteCotisations: async () => {
        const response = await apiClient.get('/referentiel/cotisations/site-config');
        return response.data;
    },
    createSiteCotisation: async (data) => {
        const response = await apiClient.post('/referentiel/cotisations/site-config', data);
        return response.data;
    },
    deactivateSiteCotisation: async (id) => {
        const response = await apiClient.put(`/referentiel/cotisations/site-config/${id}/desactiver`);
        return response.data;
    },
    activateSiteCotisation: async (id) => {
        const response = await apiClient.put(`/referentiel/cotisations/site-config/${id}/activer`);
        return response.data;
    },
    deleteSiteCotisation: async (id) => {
        const response = await apiClient.delete(`/referentiel/cotisations/site-config/${id}`);
        return response.data;
    },

    // ----------------------------------------------------
    // BAREME IRSA
    // ----------------------------------------------------
    getBaremesIrsa: async () => {
        const response = await apiClient.get('/referentiel/bareme-irsa');
        return response.data;
    },
    createBaremeIrsa: async (data) => {
        const response = await apiClient.post('/referentiel/bareme-irsa', data);
        return response.data;
    },
    updateBaremeIrsa: async (id, data) => {
        const response = await apiClient.put(`/referentiel/bareme-irsa/${id}`, data);
        return response.data;
    },
    revaloriserBaremeIrsa: async (data) => {
        const response = await apiClient.post('/referentiel/bareme-irsa/revaloriser', data);
        return response.data;
    },

    // ----------------------------------------------------
    // TAUX DE MAJORATION (HEURES SUPP & NUIT)
    // ----------------------------------------------------
    getTauxMajoration: async (params = {}) => {
        const response = await apiClient.get('/referentiel/taux-majoration', { params });
        return response.data;
    },
    createTauxMajoration: async (data) => {
        const response = await apiClient.post('/referentiel/taux-majoration', data);
        return response.data;
    },
    updateTauxMajoration: async (id, data) => {
        const response = await apiClient.put(`/referentiel/taux-majoration/${id}`, data);
        return response.data;
    },

    // ----------------------------------------------------
    // INDEMNITES & CONFIGURATION SITE
    // ----------------------------------------------------
    getIndemnites: async () => {
        const response = await apiClient.get('/referentiel/indemnites');
        return response.data;
    },
    createIndemnite: async (data) => {
        const response = await apiClient.post('/referentiel/indemnites', data);
        return response.data;
    },
    updateIndemnite: async (id, data) => {
        const response = await apiClient.put(`/referentiel/indemnites/${id}`, data);
        return response.data;
    },
    deactivateIndemnite: async (id) => {
        const response = await apiClient.put(`/referentiel/indemnites/${id}/desactiver`);
        return response.data;
    },
    activateIndemnite: async (id) => {
        const response = await apiClient.put(`/referentiel/indemnites/${id}/activer`);
        return response.data;
    },
    getSiteIndemnites: async () => {
        const response = await apiClient.get('/referentiel/indemnites/site-config');
        return response.data;
    },
    createSiteIndemnite: async (data) => {
        const response = await apiClient.post('/referentiel/indemnites/site-config', data);
        return response.data;
    },
    updateSiteIndemnite: async (id, data) => {
        const response = await apiClient.put(`/referentiel/indemnites/site-config/${id}`, data);
        return response.data;
    },
    deactivateSiteIndemnite: async (id) => {
        const response = await apiClient.put(`/referentiel/indemnites/site-config/${id}/desactiver`);
        return response.data;
    },
    activateSiteIndemnite: async (id) => {
        const response = await apiClient.put(`/referentiel/indemnites/site-config/${id}/activer`);
        return response.data;
    },

    // ----------------------------------------------------
    // PARAMETRES GLOBAUX DE PAIE
    // ----------------------------------------------------
    getParametresGlobaux: async (categorie = null, signal) => {
        const response = await apiClient.get('/referentiel/parametres-globaux', { params: categorie ? { categorie } : {}, signal });
        return response.data;
    },
    createParametreGlobal: async (data) => {
        const response = await apiClient.post('/referentiel/parametres-globaux', data);
        return response.data;
    },
    updateParametreGlobal: async (id, data) => {
        const response = await apiClient.put(`/referentiel/parametres-globaux/${id}`, data);
        return response.data;
    },
    toggleParametreGlobalActif: async (id, estActif) => {
        const response = await apiClient.put(`/referentiel/parametres-globaux/${id}/toggle-actif`, { estActif });
        return response.data;
    },

    // ----------------------------------------------------
    // CALENDRIER DES JOURS FERIES & CHOMES
    // ----------------------------------------------------
    getCalendrierFerie: async () => {
        const response = await apiClient.get('/referentiel/calendrier-ferie');
        return response.data;
    },
    createCalendrierFerie: async (data) => {
        const response = await apiClient.post('/referentiel/calendrier-ferie', data);
        return response.data;
    },
    updateCalendrierFerie: async (id, data) => {
        const response = await apiClient.put(`/referentiel/calendrier-ferie/${id}`, data);
        return response.data;
    },
    deleteCalendrierFerie: async (id) => {
        const response = await apiClient.delete(`/referentiel/calendrier-ferie/${id}`);
        return response.data;
    },
    deactivateCalendrierFerie: async (id) => {
        const response = await apiClient.put(`/referentiel/calendrier-ferie/${id}/desactiver`);
        return response.data;
    },
    activateCalendrierFerie: async (id) => {
        const response = await apiClient.put(`/referentiel/calendrier-ferie/${id}/activer`);
        return response.data;
    },
    getSiteFeries: async () => {
        const response = await apiClient.get('/referentiel/calendrier-ferie/site-config');
        return response.data;
    },
    createSiteFerie: async (data) => {
        const response = await apiClient.post('/referentiel/calendrier-ferie/site-config', data);
        return response.data;
    },
    deleteSiteFerie: async (id) => {
        const response = await apiClient.delete(`/referentiel/calendrier-ferie/site-config/${id}`);
        return response.data;
    },
    getPeriodesChomees: async () => {
        const response = await apiClient.get('/referentiel/calendrier-ferie/periodes');
        return response.data;
    },
    createPeriodeChomee: async (data) => {
        const response = await apiClient.post('/referentiel/calendrier-ferie/periodes', data);
        return response.data;
    },
    updatePeriodeChomee: async (id, data) => {
        const response = await apiClient.put(`/referentiel/calendrier-ferie/periodes/${id}`, data);
        return response.data;
    },
    deletePeriodeChomee: async (id) => {
        const response = await apiClient.delete(`/referentiel/calendrier-ferie/periodes/${id}`);
        return response.data;
    }
};

export default paieConfigService;
