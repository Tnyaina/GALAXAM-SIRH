/**
 * congeService.js
 * Service API Frontend pour le module Congés & Absences.
 * Interagit avec les endpoints backend /api/conges/*
 */
import apiClient from '@/api/apiClient';

const congeService = {
  // =========================================================================
  // RÉFÉRENTIELS CONGÉS & ABSENCES
  // =========================================================================

  /**
   * Récupère tous les référentiels des congés (pools, types, motifs, statuts, paramètres)
   */
  getAllReferentiels: async () => {
    const response = await apiClient.get('/conges/referentiels/all');
    return response.data;
  },

  /**
   * Récupère les types de congés (ex: ANNUEL, MALADIE, MATERNITE, etc.)
   */
  getTypesConge: async (actifUniquement = true) => {
    const response = await apiClient.get('/conges/referentiels/types', {
      params: { actifUniquement },
    });
    return response.data;
  },

  /**
   * Récupère les motifs d'événements familiaux (MARIAGE, NAISSANCE, DECES...)
   */
  getMotifsFamiliaux: async (actifUniquement = true) => {
    const response = await apiClient.get('/conges/referentiels/motifs', {
      params: { actifUniquement },
    });
    return response.data;
  },

  // =========================================================================
  // CRUD RÉFÉRENTIELS (TYPES & MOTIFS)
  // =========================================================================

  creerTypeConge: async (data) => {
    const response = await apiClient.post('/conges/referentiels/types', data);
    return response.data;
  },

  modifierTypeConge: async (id, data) => {
    const response = await apiClient.put(`/conges/referentiels/types/${id}`, data);
    return response.data;
  },

  supprimerTypeConge: async (id) => {
    const response = await apiClient.delete(`/conges/referentiels/types/${id}`);
    return response.data;
  },

  desactiverTypeConge: async (id) => {
    const response = await apiClient.put(`/conges/referentiels/types/${id}/desactiver`);
    return response.data;
  },

  activerTypeConge: async (id) => {
    const response = await apiClient.put(`/conges/referentiels/types/${id}/activer`);
    return response.data;
  },

  creerMotifFamilial: async (data) => {
    const response = await apiClient.post('/conges/referentiels/motifs', data);
    return response.data;
  },

  modifierMotifFamilial: async (id, data) => {
    const response = await apiClient.put(`/conges/referentiels/motifs/${id}`, data);
    return response.data;
  },

  supprimerMotifFamilial: async (id) => {
    const response = await apiClient.delete(`/conges/referentiels/motifs/${id}`);
    return response.data;
  },

  desactiverMotifFamilial: async (id) => {
    const response = await apiClient.put(`/conges/referentiels/motifs/${id}/desactiver`);
    return response.data;
  },

  activerMotifFamilial: async (id) => {
    const response = await apiClient.put(`/conges/referentiels/motifs/${id}/activer`);
    return response.data;
  },

  /**
   * Récupère la liste des pools annuels partagés (PERMISSIONS_FAMILIALES 10j/an)
   */
  getPools: async () => {
    const response = await apiClient.get('/conges/referentiels/pools');
    return response.data;
  },

  /**
   * Récupère les statuts de demande (BROUILLON, EN_ATTENTE, VALIDE, REFUSE, ANNULE)
   */
  getStatutsDemande: async () => {
    const response = await apiClient.get('/conges/referentiels/statuts');
    return response.data;
  },

  // =========================================================================
  // SOLDES & HISTORIQUE LEDGER DES MOUVEMENTS
  // =========================================================================

  /**
   * Récupère la situation du solde de congé d'un employé (solde actuel, acquis, pris, bonifié, expiré)
   */
  getSoldeEmploye: async (idEmploye) => {
    const response = await apiClient.get(`/conges/solde/employe/${idEmploye}`);
    return response.data;
  },

  /**
   * Récupère l'historique comptable (Ledger) des mouvements de solde d'un employé
   */
  getMouvementsEmploye: async (idEmploye) => {
    const response = await apiClient.get(`/conges/mouvements/employe/${idEmploye}`);
    return response.data;
  },

  /**
   * Récupère la consommation annuelle du pool d'un employé (ex: 10j permissions exceptionnelles)
   */
  getConsommationPool: async (idEmploye, annee = null) => {
    const response = await apiClient.get(`/conges/pool-consommation/employe/${idEmploye}`, {
      params: { annee },
    });
    return response.data;
  },

  /**
   * Effectue un mouvement d'ajustement manuel du solde par la RH/DAF
   */
  creerAjustementManuel: async (data) => {
    const response = await apiClient.post('/conges/solde/ajustement', data);
    return response.data;
  },

  /**
   * Déclenche la purge légale triennale des congés prescrits (+36 mois)
   */
  declencherPurgeExpiration: async () => {
    const response = await apiClient.post('/conges/purge-expiration');
    return response.data;
  },

  // =========================================================================
  // DEMANDES DE CONGÉS & SIMULATION
  // =========================================================================

  /**
   * Simule le décompte continu en jours calendaires stricts (date_fin - date_debut + 1)
   */
  // Dans congeService.js
  simulerDemande: async (dateDebut, dateFin, estDemiJournee = false) => {
    const response = await apiClient.post('/conges/demandes/simuler', {
      dateDebut,
      dateFin,
      estDemiJournee: Boolean(estDemiJournee),
    });
    return response.data;
  },

  /**
   * Soumet une nouvelle demande de congé
   */
  creerDemande: async (demandeData) => {
    const response = await apiClient.post('/conges/demandes', demandeData);
    return response.data;
  },

  /**
   * Récupère la liste des demandes de congé d'un employé
   */
  getDemandesEmploye: async (idEmploye, params = {}) => {
    const response = await apiClient.get(`/conges/demandes/employe/${idEmploye}`, { params });
    return response.data;
  },

  /**
   * Récupère le détail d'une demande de congé par son ID
   */
  getDemandeParId: async (id) => {
    const response = await apiClient.get(`/conges/demandes/${id}`);
    return response.data;
  },

  /**
   * Modifie une demande de congé existante
   */
  updateDemande: async (id, demandeData) => {
    const response = await apiClient.put(`/conges/demandes/${id}`, demandeData);
    return response.data;
  },

  // =========================================================================
  // CIRCUIT DE VALIDATION RH & DAF
  // =========================================================================

  /**
   * Récupère toutes les demandes de congé de l'entreprise
   */
  getAllDemandes: async (params = {}) => {
    const response = await apiClient.get('/conges/demandes/all', { params });
    return response.data;
  },

  /**
   * Récupère toutes les demandes en attente de validation (statut EN_ATTENTE)
   */
  getDemandesAValider: async (params = {}) => {
    const response = await apiClient.get('/conges/demandes/validation', { params });
    return response.data;
  },

  /**
   * Valide une demande de congé (génère automatiquement le débit PRISE dans le ledger)
   */
  validerDemande: async (id, options = {}) => {
    const params = new URLSearchParams();
    if (options.justificatifFourni) {
      params.append('justificatifFourni', 'true');
    }
    if (options.estRemunere !== undefined && options.estRemunere !== null) {
      params.append('estRemunere', String(options.estRemunere));
    }
    if (options.derogationSoldeNegatif) {
      params.append('derogationSoldeNegatif', 'true');
    }
    const queryString = params.toString();
    const url = `/conges/demandes/${id}/valider${queryString ? `?${queryString}` : ''}`;
    const response = await apiClient.post(url);
    return response.data;
  },

  /**
   * Refuse une demande de congé avec motif explicite
   */
  refuserDemande: async (id, motifRefus) => {
    const response = await apiClient.post(`/conges/demandes/${id}/refuser`, { motifRefus });
    return response.data;
  },

  /**
   * Annule une demande de congé (si validée, génère le crédit ANNULATION dans le ledger)
   */
  annulerDemande: async (id, motif) => {
    const payload = typeof motif === 'string' ? { motif } : (motif || {});
    const response = await apiClient.post(`/conges/demandes/${id}/annuler`, payload);
    return response.data;
  },

  // Alias rétrocompatibilité
  getAllDemandesGlobales: async (params = {}) => {
    const response = await apiClient.get('/conges/demandes/all', { params });
    return response.data;
  },

  getReferentielsConge: async () => {
    const response = await apiClient.get('/conges/referentiels/all');
    return response.data;
  },

  /**
   * Vérifie les conflits de continuité de service (collègues même poste), 
   * les chevauchements personnels et fournit le décompte calendaire détaillé.
   */
  verifierConflitsDemande: async (data) => {
    const response = await apiClient.post('/conges/demandes/verifier-conflits', data);
    return response.data;
  },

  /**
   * Récupère la matrice planning de tous les collaborateurs actifs avec leurs congés
   */
  getPlanningCollaborateurs: async (params = {}, options = {}) => {
    const response = await apiClient.get('/conges/planning', {
      params,
      signal: options?.signal,
    });
    return response.data;
  },
};

export default congeService;
