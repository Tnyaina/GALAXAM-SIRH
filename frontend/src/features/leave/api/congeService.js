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
   * Récupère la liste des pools annuels partagés et leurs plafonds configurés.
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
   * Récupère la ventilation d'imputation RG-CONGE-06 (stock historique restant vs droits datés)
   */
  getImputationSolde: async (idEmploye) => {
    const response = await apiClient.get(`/conges/solde/imputation/${idEmploye}`);
    return response.data;
  },

  /**
   * Récupère consommation, réservations et engagement annuel du pool d'un employé.
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
   * Initialise le solde historique d'un salarié (reprise de données / migration sans historique détaillé)
   */
  initialiserSoldeHistorique: async (data) => {
    const response = await apiClient.post('/conges/solde/initialisation', data);
    return response.data;
  },

  /**
   * Enregistre ou ajuste un taux journalier de congé historique de migration (KAJY_MIGRATION)
   */
  enregistrerTauxMigration: async (data) => {
    const response = await apiClient.post('/conges/taux-applicable/migration', data);
    return response.data;
  },

  /**
   * Récupère la situation complète canonique d'un salarié (Solde, TCONGE applicable, Période & Accumulateurs)
   */
  getSituationEmploye: async (idEmploye, dateReference = null) => {
    const params = dateReference ? { dateReference } : {};
    const response = await apiClient.get(`/conges/solde/employe/${idEmploye}/situation`, { params });
    return response.data;
  },

  /**
   * Enregistre un cutover snapshot initial (AGGREGATED_OPENING ou MINIMAL_OPENING)
   */
  cutoverOpening: async (payload) => {
    const response = await apiClient.post('/conges/solde/cutover-opening', payload);
    return response.data;
  },

  getValorisationLots: async (idEmploye) => {
    const response = await apiClient.get(`/conges/solde/valorisation-lots/${idEmploye}`);
    return response.data;
  },

  corrigerReliquatCutover: async (idEmploye, payload) => {
    const response = await apiClient.post(`/conges/solde/cutover/${idEmploye}/corriger`, payload);
    return response.data;
  },

  getExamenExpiration: async (idEmploye) => {
    const response = await apiClient.get(`/conges/expiration/employe/${idEmploye}`);
    return response.data;
  },

  reporterExamenExpiration: async (idEmploye, payload) => {
    const response = await apiClient.post(`/conges/expiration/employe/${idEmploye}/reporter`, payload);
    return response.data;
  },

  confirmerExpiration: async (idEmploye, payload) => {
    const response = await apiClient.post(`/conges/expiration/employe/${idEmploye}/confirmer`, payload);
    return response.data;
  },

  /**
   * Récupère la configuration et le taux TCONGE applicable d'un collaborateur
   */
  getTcongeConfiguration: async (idEmploye, dateReference = null) => {
    const params = dateReference ? { dateReference } : {};
    const response = await apiClient.get(`/conges/periodes-reference/employe/${idEmploye}/statut-configuration`, { params });
    return response.data;
  },

  /**
   * Récupère la liste des périodes de référence d'un collaborateur
   */
  getPeriodesReferenceEmploye: async (idEmploye) => {
    const response = await apiClient.get(`/conges/periodes-reference/employe/${idEmploye}`);
    return response.data;
  },

  /**
   * Télécharge l'état annuel officiel des congés au format Excel (.xlsx) basé sur le modèle réglementaire
   * pour une période de référence donnée ou la période courante
   */
  telechargerEtatCongesExcel: async (idEmploye, idPeriodeReference = null, customFilename = null) => {
    const params = idPeriodeReference ? { idPeriodeReference } : {};
    const response = await apiClient.get(`/conges/periodes-reference/employe/${idEmploye}/export-excel`, {
      params,
      responseType: 'blob'
    });

    let filename = customFilename;
    const contentDisposition = response.headers?.['content-disposition'];
    if (!filename && contentDisposition) {
      const match = contentDisposition.match(/filename=["']?([^"';]+)["']?/);
      if (match && match[1]) filename = match[1];
    }
    if (!filename) {
      filename = `ETAT_CONGES_EMP${idEmploye}.xlsx`;
    }

    const blob = new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
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

  telechargerGrandRecapCongesExcel: async (dateDebut, dateFin) => {
    const response = await apiClient.get('/conges/periodes-reference/grand-recap/export-excel', {
      params: { dateDebut, dateFin },
      responseType: 'blob',
    });
    const header = response.headers?.['content-disposition'] || '';
    const filename = header.match(/filename=["']?([^"';]+)["']?/)?.[1]
      || `GRAND_RECAP_CONGES_${dateDebut}_${dateFin}.xlsx`;
    const url = window.URL.createObjectURL(new Blob([response.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    }));
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(url);
  },

  // =========================================================================
  // DEMANDES DE CONGÉS & SIMULATION
  // =========================================================================

  /**
   * Simule le décompte continu en jours calendaires stricts (date_fin - date_debut + 1)
   */
  // Dans congeService.js
  simulerDemande: async (demande) => {
    const response = await apiClient.post('/conges/demandes/simuler', demande);
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
