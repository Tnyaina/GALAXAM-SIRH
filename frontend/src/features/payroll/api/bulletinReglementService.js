import apiClient from '@/api/apiClient';

const root = 'paie/reglements';

const bulletinReglementService = {
    periode: async (id) => (await apiClient.get(`${root}/periode/${id}`)).data,
    lots: async (id) => (await apiClient.get(`${root}/periode/${id}/lots`)).data,
    situation: async (id) => (await apiClient.get(`${root}/bulletin/${id}/situation`)).data,
    historique: async (id) => (await apiClient.get(`${root}/bulletin/${id}`)).data,
    enregistrer: async (data) => {
        const form = new FormData();
        ['idBulletin', 'montant', 'mode', 'date', 'reference', 'commentaire'].forEach(key => {
            if (data[key] !== undefined && data[key] !== null) form.append(key, data[key]);
        });
        form.append('file', data.file);
        return (await apiClient.post(`${root}/bulletin`, form, { headers: { 'Content-Type': 'multipart/form-data' } })).data;
    },
    annuler: async (id, motif) => apiClient.post(`${root}/bulletin/${id}/annuler`, { motif }),
    justificatif: async (idBulletin, idReglement) => (await apiClient.get(`${root}/bulletin/${idBulletin}/justificatif/${idReglement}`, { responseType: 'blob' })).data,
    preparerLot: async (id, bulletins) => (await apiClient.post(`${root}/periode/${id}/lots`, bulletins)).data,
    lignes: async (id) => (await apiClient.get(`${root}/lots/${id}/lignes`)).data,
    fichierLot: async (id) => (await apiClient.get(`${root}/lots/${id}/csv`, { responseType: 'blob' })).data,
    transmettreLot: async (id) => apiClient.post(`${root}/lots/${id}/transmettre`),
    annulerLot: async (id) => apiClient.post(`${root}/lots/${id}/annuler`),
    confirmerLigne: async (id, data) => {
        const form = new FormData();
        form.append('date', data.date);
        form.append('reference', data.reference);
        form.append('file', data.file);
        return apiClient.post(`${root}/lots/lignes/${id}/confirmer`, form, { headers: { 'Content-Type': 'multipart/form-data' } });
    },
    rejeterLigne: async (id, motif) => apiClient.post(`${root}/lots/lignes/${id}/rejeter`, { motif }),
};

export default bulletinReglementService;
