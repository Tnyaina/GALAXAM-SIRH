/**
 * congeService.js
 * Service API Frontend pour le module Congés & Absences.
 * Interagit avec les endpoints backend /api/conges/*
 */
import apiClient from '@/api/apiClient';

const notifyLeaveUpdated = () => {
  if (typeof window !== 'undefined') window.dispatchEvent(new Event('conge-data-updated'));
};

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

  getPeriodesEnAttenteActivation: async () => {
    const response = await apiClient.get('/conges/periodes-reference/en-attente-activation');
    return response.data;
  },

  getPeriodesReferenceEmploye: async (idEmploye) => {
    const response = await apiClient.get(`/conges/periodes-reference/employe/${idEmploye}`);
    return response.data;
  },

  activerTauxCalculeReference: async (idPeriodeReference, dateEffet) => {
    const response = await apiClient.post(
      `/conges/periodes-reference/${idPeriodeReference}/activer-taux`, { dateEffet }
    );
    return response.data;
  },

  getMoisReference: async (idPeriodeReference) => {
    const response = await apiClient.get(`/conges/periodes-reference/${idPeriodeReference}/mois`);
    return response.data;
  },

  getCorrectionsValorisation: async (idPeriodeReference) => {
    const response = await apiClient.get(`/conges/periodes-reference/${idPeriodeReference}/corrections-valorisation`);
    return response.data;
  },

  corrigerValorisationMois: async (idPeriodeReference, idJournal, payload) => {
    const response = await apiClient.post(
      `/conges/periodes-reference/${idPeriodeReference}/mois/${idJournal}/corriger-valorisation`, payload
    );
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
   * Nettoie une date pour s'assurer d'envoyer uniquement YYYY-MM-DD sans composante heure/fuseau.
   */
  toIsoDateString: (val) => {
    if (!val) return val;
    if (typeof val === 'string') {
      return val.length >= 10 ? val.slice(0, 10) : val;
    }
    if (val instanceof Date) {
      return val.toISOString().slice(0, 10);
    }
    return val;
  },

  /**
   * Simule le décompte continu en jours calendaires stricts (date_fin - date_debut + 1)
   */
  simulerDemande: async (demande) => {
    const payload = demande ? {
      ...demande,
      dateDebut: congeService.toIsoDateString(demande.dateDebut),
      dateFin: congeService.toIsoDateString(demande.dateFin),
    } : demande;
    const response = await apiClient.post('/conges/demandes/simuler', payload);
    return response.data;
  },

  simulerValorisation: async (demande) => {
    const payload = demande ? {
      ...demande,
      dateDebut: congeService.toIsoDateString(demande.dateDebut),
      dateFin: congeService.toIsoDateString(demande.dateFin),
    } : demande;
    const response = await apiClient.post('/conges/demandes/simuler-valorisation', payload);
    return response.data;
  },

  /**
   * Soumet une nouvelle demande de congé
   */
  creerDemande: async (demandeData) => {
    const payload = demandeData ? {
      ...demandeData,
      dateDebut: congeService.toIsoDateString(demandeData.dateDebut),
      dateFin: congeService.toIsoDateString(demandeData.dateFin),
    } : demandeData;
    const response = await apiClient.post('/conges/demandes', payload);
    notifyLeaveUpdated();
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
    const payload = demandeData ? {
      ...demandeData,
      dateDebut: congeService.toIsoDateString(demandeData.dateDebut),
      dateFin: congeService.toIsoDateString(demandeData.dateFin),
    } : demandeData;
    const response = await apiClient.put(`/conges/demandes/${id}`, payload);
    notifyLeaveUpdated();
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
    notifyLeaveUpdated();
    return response.data;
  },

  /**
   * Refuse une demande de congé avec motif explicite
   */
  refuserDemande: async (id, motifRefus) => {
    const response = await apiClient.post(`/conges/demandes/${id}/refuser`, { motifRefus });
    notifyLeaveUpdated();
    return response.data;
  },

  /**
   * Annule une demande de congé (si validée, génère le crédit ANNULATION dans le ledger)
   */
  annulerDemande: async (id, motif) => {
    const payload = typeof motif === 'string' ? { motif } : (motif || {});
    const response = await apiClient.post(`/conges/demandes/${id}/annuler`, payload);
    notifyLeaveUpdated();
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
    const payload = data ? {
      ...data,
      dateDebut: congeService.toIsoDateString(data.dateDebut),
      dateFin: congeService.toIsoDateString(data.dateFin),
    } : data;
    const response = await apiClient.post('/conges/demandes/verifier-conflits', payload);
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
