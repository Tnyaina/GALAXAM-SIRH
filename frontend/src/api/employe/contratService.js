/**
 * contratService.js
 * Service frontend pour les Contrats, Affectations et Workflows RH.
 * Correspond aux endpoints du WorkflowRhController et ContratTravailController.
 */
import apiClient from '@/api/apiClient';

const contratService = {

    // =========================================================================
    // RÉFÉRENTIELS CARRIÈRE (déjà existants, conservés intacts)
    // =========================================================================

    getAllReferentielsCarriere: async () => {
        const response = await apiClient.get('/ref-carriere/all');
        return response.data;
    },

    getCategoriesPro: async (actifUniquement = false) => {
        const response = await apiClient.get('/ref-carriere/categories-pro', { params: { actifUniquement } });
        return response.data;
    },

    createCategoriePro: async (data) => {
        const response = await apiClient.post('/ref-carriere/categories-pro', data);
        return response.data;
    },

    updateCategoriePro: async (id, data) => {
        const response = await apiClient.put(`/ref-carriere/categories-pro/${id}`, data);
        return response.data;
    },

    getModesPaie: async () => {
        const response = await apiClient.get('/ref-carriere/modes-paie');
        return response.data;
    },

    getMotifsMouvement: async () => {
        const response = await apiClient.get('/ref-carriere/motifs-mouvement');
        return response.data;
    },

    getMotifsSortie: async () => {
        const response = await apiClient.get('/ref-carriere/motifs-sortie');
        return response.data;
    },

    getTypesEvenementRh: async () => {
        const response = await apiClient.get('/ref-carriere/types-evenements');
        return response.data;
    },

    // =========================================================================
    // WORKFLOWS RH
    // =========================================================================

    /**
     * ÉTAPE 1 — Créer un brouillon d'embauche initiale pour un employé.
     * Crée atomiquement : contrat (BROUILLON) + affectation (PLANIFIEE) + workflow (BROUILLON).
     * Retourne le WorkflowRhCPL (vue enrichie) pour la page de récapitulatif DAF.
     *
     * POST /employes/{idEmploye}/workflows/embauche-initiale
     */
    creerBrouillonEmbauche: async (idEmploye, data) => {
        const response = await apiClient.post(`/employes/${idEmploye}/workflows/embauche-initiale`, data);
        return response.data;
    },

    /**
     * ÉTAPE 1 (Transition) — Créer un brouillon de transition (Mutation, Promotion, Transformation CDI...).
     * POST /employes/{idEmploye}/workflows/transition
     */
    creerBrouillonTransition: async (idEmploye, data) => {
        const response = await apiClient.post(`/employes/${idEmploye}/workflows/transition`, data);
        return response.data;
    },

    initierTransition: async (idEmploye, data) => {
        const response = await apiClient.post(`/employes/${idEmploye}/workflows/transition`, data);
        return response.data;
    },

    initierRupture: async (idEmploye, data) => {
        const response = await apiClient.post(`/employes/${idEmploye}/workflows/transition`, data);
        return response.data;
    },

    /**
     * ÉTAPE 2 — Valider un workflow BROUILLON (action DAF).
     * Pour EMBAUCHE_INITIALE : contrat → ACTIF, affectation → ACTIVE, workflow → VALIDE.
     *
     * PUT /workflows/{idWorkflow}/valider
     */
    validerWorkflow: async (idWorkflow, commentaire = null) => {
        const response = await apiClient.put(`/workflows/${idWorkflow}/valider`, {
            commentaireValidation: commentaire,
        });
        return response.data;
    },

    /**
     * Annuler un workflow BROUILLON.
     * Remet le contrat et l'affectation à l'état annulé.
     *
     * PUT /workflows/{idWorkflow}/annuler
     */
    annulerWorkflow: async (idWorkflow, motifAnnulation = null) => {
        const response = await apiClient.put(`/workflows/${idWorkflow}/annuler`, {
            commentaireValidation: motifAnnulation,
        });
        return response.data;
    },

    /**
     * Annuler un workflow planifié à date ultérieure (action DAF/RH).
     * PUT /workflows/{idWorkflow}/annuler-planification
     */
    annulerWorkflowPlanifie: async (idWorkflow, motifAnnulation = null) => {
        const response = await apiClient.put(`/workflows/${idWorkflow}/annuler-planification`, {
            commentaireValidation: motifAnnulation,
        });
        return response.data;
    },

    /**
     * Détail complet d'un workflow (enrichi avec tous les libellés).
     * Utilisé pour la page de récapitulatif DAF.
     *
     * GET /workflows/{idWorkflow}
     */
    getWorkflow: async (idWorkflow) => {
        const response = await apiClient.get(`/workflows/${idWorkflow}`);
        return response.data;
    },

    /**
     * Historique des workflows d'un employé (du plus récent au plus ancien).
     *
     * GET /employes/{idEmploye}/workflows
     */
    getWorkflowsParEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/workflows`);
        return response.data;
    },

    /**
     * Retourne le workflow BROUILLON en cours d'un employé (pour reprise de saisie).
     * Retourne 404 si aucun brouillon en cours.
     *
     * GET /employes/{idEmploye}/workflows/brouillon
     */
    getBrouillonEnCours: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/workflows/brouillon`);
        return response.data || null;
    },

    // =========================================================================
    // CONTRATS
    // =========================================================================

    /**
     * Historique complet des contrats d'un employé.
     * GET /employes/{idEmploye}/contrats
     */
    getContratsParEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/contrats`);
        return response.data;
    },

    /**
     * Contrat actif d'un employé.
     * GET /employes/{idEmploye}/contrats/actif
     */
    getContratActif: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/contrats/actif`);
        return response.data || null;
    },

    /**
     * Détail d'un contrat spécifique.
     * GET /employes/{idEmploye}/contrats/{idContrat}
     */
    getContrat: async (idEmploye, idContrat) => {
        const response = await apiClient.get(`/employes/${idEmploye}/contrats/${idContrat}`);
        return response.data;
    },

    /**
     * Modifier un contrat BROUILLON avant validation.
     * Retourne le workflow CPL mis à jour pour rafraîchir le récapitulatif.
     * PUT /employes/{idEmploye}/contrats/{idContrat}
     */
    modifierBrouillonContrat: async (idEmploye, idContrat, data) => {
        const response = await apiClient.put(`/employes/${idEmploye}/contrats/${idContrat}`, data);
        return response.data;
    },

    // =========================================================================
    // AFFECTATIONS
    // =========================================================================

    /**
     * Historique complet des affectations d'un employé.
     * GET /employes/{idEmploye}/affectations
     */
    getAffectationsParEmploye: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/affectations`);
        return response.data;
    },

    /**
     * Affectation ACTIVE courante d'un employé.
     * GET /employes/{idEmploye}/affectations/active
     */
    getAffectationActive: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/affectations/active`);
        return response.data || null;
    },

    /**
     * Évaluation complète de l'état de la période d'essai calculée côté serveur.
     * GET /employes/{idEmploye}/essai/etat
     */
    getEtatEssai: async (idEmploye) => {
        const response = await apiClient.get(`/employes/${idEmploye}/essai/etat`);
        return response.data;
    },
};

export default contratService;
