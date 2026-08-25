import apiClient from '@/api/apiClient';

export const pointageSyntheseService = {
  getSyntheseMensuelle: async (idPeriodePaie, idSite = null, search = null) => {
    const params = { idPeriodePaie };
    if (idSite) params.idSite = idSite;
    if (search) params.search = search;
    const response = await apiClient.get('/pointages/synthese/mensuelle', { params });
    return response.data;
  },

  getSyntheseHebdomadaire: async (idPeriodePaie, numeroSemaine = null, idSite = null, search = null) => {
    const params = { idPeriodePaie };
    if (numeroSemaine) params.numeroSemaine = numeroSemaine;
    if (idSite) params.idSite = idSite;
    if (search) params.search = search;
    const response = await apiClient.get('/pointages/synthese/hebdomadaire', { params });
    return response.data;
  },

  getTronconsSemainesEmploye: async (idEmploye, idPeriodePaie) => {
    const response = await apiClient.get(`/pointages/synthese/employe/${idEmploye}/semaines`, {
      params: { idPeriodePaie }
    });
    return response.data;
  },

  getPreClotureCheck: async (idPeriodePaie) => {
    const response = await apiClient.get('/pointages/synthese/pre-cloture-check', {
      params: { idPeriodePaie }
    });
    return response.data;
  }
};
