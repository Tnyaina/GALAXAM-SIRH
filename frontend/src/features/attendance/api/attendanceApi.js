import apiClient from '@/api/apiClient';

/** Tous les appels Pointage et son contrat de période, regroupés par intention. */
const attendanceApi = {
    daily: {
        getGrid: async ({ idSite, date }) => (await apiClient.get('/pointages/saisie', { params: { idSite, date } })).data,
        preview: async (pointage) => (await apiClient.post('/pointages/saisie/preview', pointage)).data,
        saveBatch: async (pointages) => (await apiClient.post('/pointages/saisie/batch', pointages)).data
    },
    anomalies: {
        list: async (params, options = {}) => (await apiClient.get('/pointages/anomalies', { params, signal: options?.signal })).data,
        regularize: async (idPointage, payload) => (await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload)).data
    },
    employees: {
        getSheet: async (idEmploye, params = {}) => (await apiClient.get(`/pointages/employes/${idEmploye}`, { params })).data,
        exportExcel: async (idEmploye, params = {}) => (await apiClient.get(`/pointages/employes/${idEmploye}/export/excel`, { params, responseType: 'blob' })).data
    },
    summary: {
        getPeriod: async ({ idPeriodePaie, idSite, search, filtre, page = 1, size = 25 }) => (await apiClient.get('/pointages/synthese', { params: { idPeriodePaie, idSite, search, filtre, page, size } })).data,
        getWeeks: async ({ idPeriodePaie, idSite }) => (await apiClient.get('/pointages/synthese/semaines', { params: { idPeriodePaie, idSite } })).data,
        getWeekDetail: async ({ idPeriodePaie, dateDebut, dateFin, idSite }) => (await apiClient.get('/pointages/synthese/semaine-detail', { params: { idPeriodePaie, dateDebut, dateFin, idSite } })).data,
        recalculateWeeks: async ({ idPeriodePaie, idSite }) => (await apiClient.post('/pointages/synthese/calculer-tout', {
            idPeriodePaie: Number(idPeriodePaie),
            idSite: idSite != null && idSite !== '' ? Number(idSite) : undefined
        })).data
    },
    period: {
        list: async () => (await apiClient.get('/periodes')).data,
        getContext: async (periodId = null, date = null) => {
            const params = {};
            if (periodId) params.periodId = periodId;
            if (date) params.date = date;
            return (await apiClient.get('/periodes/context', { params })).data;
        },
        create: async (periodData) => (await apiClient.post('/periodes', periodData)).data,
        execute: async (id, command, payload) => (await apiClient.post(`/periodes/${id}/actions/${command}`, payload)).data,
        simulate: async (dateDebut, dateFin) => (await apiClient.get('/periodes/simuler', { params: { dateDebut, dateFin } })).data
    }
};

// Noms existants conservés dans les composants jusqu'à leur migration vers les groupes ci-dessus.
export const attendanceService = {
    getGrilleSaisie: attendanceApi.daily.getGrid,
    previewDay: attendanceApi.daily.preview,
    enregistrerBatch: attendanceApi.daily.saveBatch,
    getAnomalies: attendanceApi.anomalies.list,
    regulariser: attendanceApi.anomalies.regularize,
    getFicheEmploye: attendanceApi.employees.getSheet,
    exportExcel: attendanceApi.employees.exportExcel
};
export const pointageSyntheseService = {
    getSynthese: attendanceApi.summary.getPeriod,
    getSemaines: attendanceApi.summary.getWeeks,
    getSemaineDetail: attendanceApi.summary.getWeekDetail,
    calculerTout: attendanceApi.summary.recalculateWeeks,
    calculerSemaines: attendanceApi.summary.recalculateWeeks
};
export const periodePaieService = {
    getAllPeriodes: attendanceApi.period.list,
    getContext: attendanceApi.period.getContext,
    createPeriode: attendanceApi.period.create,
    executeCommand: attendanceApi.period.execute,
    simulerPeriode: attendanceApi.period.simulate
};

export default attendanceApi;
