import apiClient, { getAccessToken } from '@/api/apiClient';

async function exportDocument(idEmploye, format, params) {
    const response = await apiClient.get(`/pointages/employes/${idEmploye}/export/${format}`, { params, responseType: 'blob' });
    const disposition = response.headers?.['content-disposition'] || '';
    const filename = disposition.match(/filename="([^"]+)"/)?.[1] || `Pointage.${format === 'excel' ? 'xlsx' : 'pdf'}`;
    return { blob: response.data, filename };
}

const cleanSite = (site) => (Number(site) > 0 ? Number(site) : undefined);

// Une fiche est coûteuse à calculer. Les vues de la même période partagent
// brièvement sa lecture ; toute écriture de pointage vide ce cache.
const employeeSheetRequests = new Map();
const employeeSheetCache = new Map();
const SHEET_TTL_MS = 15_000;
let sheetRevision = 0;
let cacheToken = getAccessToken();

function invalidateEmployeeSheets() {
    sheetRevision += 1;
    employeeSheetRequests.clear();
    employeeSheetCache.clear();
}

function notifyPointageUpdated() {
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('pointage-data-updated'));
    }
}

function getEmployeeSheet(idEmploye, params = {}) {
    let cleanId = idEmploye;
    let cleanParams = params;
    if (typeof idEmploye === 'object' && idEmploye !== null) {
        cleanId = idEmploye.idEmploye;
        cleanParams = { ...idEmploye };
        delete cleanParams.idEmploye;
    }
    const token = getAccessToken();
    if (token !== cacheToken) {
        invalidateEmployeeSheets();
        cacheToken = token;
    }
    const periodId = Number(cleanParams.idPeriode);
    const key = periodId > 0
        ? `${cleanId}:periode:${periodId}`
        : `${cleanId}:${cleanParams.period || 'mois'}:${cleanParams.date || ''}`;
    const cached = employeeSheetCache.get(key);
    if (cached && cached.expiresAt > Date.now()) return Promise.resolve(cached.data);
    if (!employeeSheetRequests.has(key)) {
        const revision = sheetRevision;
        const request = apiClient.get(`/pointages/employes/${cleanId}`, { params: cleanParams })
            .then((response) => {
                if (revision === sheetRevision) {
                    employeeSheetCache.set(key, { data: response.data, expiresAt: Date.now() + SHEET_TTL_MS });
                }
                return response.data;
            })
            .finally(() => {
                if (employeeSheetRequests.get(key) === request) employeeSheetRequests.delete(key);
            });
        employeeSheetRequests.set(key, request);
    }
    return employeeSheetRequests.get(key);
}

/** Tous les appels Pointage et son contrat de période, regroupés par intention. */
const attendanceApi = {
    daily: {
        getGrid: async ({ idSite, date }) => {
            const parsedSite = Number(idSite);
            if (!parsedSite || parsedSite <= 0) {
                throw new Error(`Identifiant de site obligatoire et strictement positif pour la saisie (reçu: ${idSite}).`);
            }
            return (await apiClient.get('/pointages/saisie', { params: { idSite: parsedSite, date } })).data;
        },
        preview: async (pointage) => (await apiClient.post('/pointages/saisie/preview', pointage)).data,
        previewBatch: async (pointages) => (await apiClient.post('/pointages/saisie/preview/batch', pointages)).data,
        saveBatch: async (pointages) => {
            const data = (await apiClient.post('/pointages/saisie/batch', pointages)).data;
            invalidateEmployeeSheets();
            notifyPointageUpdated();
            return data;
        }
    },
    anomalies: {
        list: async (params = {}, options = {}) => {
            const cleanParams = { ...params };
            if (cleanParams.idSite != null && Number(cleanParams.idSite) <= 0) {
                delete cleanParams.idSite;
            }
            if (cleanParams.idPeriodePaie != null && Number(cleanParams.idPeriodePaie) <= 0) {
                delete cleanParams.idPeriodePaie;
            }
            if (cleanParams.datePointage === '' || cleanParams.datePointage == null) {
                delete cleanParams.datePointage;
            }
            if (cleanParams.search === '' || cleanParams.search == null) {
                delete cleanParams.search;
            }
            return (await apiClient.get('/pointages/anomalies', { params: cleanParams, signal: options?.signal })).data;
        },
        regularize: async (idPointage, payload) => {
            const data = (await apiClient.put(`/pointages/anomalies/${idPointage}/regulariser`, payload)).data;
            invalidateEmployeeSheets();
            notifyPointageUpdated();
            return data;
        }
    },
    employees: {
        getSheet: getEmployeeSheet,
        getImportTemplate: async (idEmploye, idPeriode) => {
            const response = await apiClient.get(`/pointages/employes/${idEmploye}/import/modele`, {
                params: { idPeriode }, responseType: 'blob'
            });
            return { blob: response.data, filename: `Modele_pointage_${idEmploye}_${idPeriode}.xlsx` };
        },
        previewImport: async (idEmploye, idPeriode, file) => {
            const body = new FormData();
            body.append('file', file);
            return (await apiClient.post(`/pointages/employes/${idEmploye}/import/apercu`, body,
                { params: { idPeriode } })).data;
        },
        exportExcel: (idEmploye, params = {}) => exportDocument(idEmploye, 'excel', params),
        exportPdf: (idEmploye, params = {}) => exportDocument(idEmploye, 'pdf', params)
    },
    summary: {
        getPeriod: async ({ idPeriodePaie, idSite, search, filtre, page = 1, size = 25 }) => (await apiClient.get('/pointages/synthese', { params: { idPeriodePaie, idSite: cleanSite(idSite), search, filtre, page, size } })).data,
        getWeeks: async ({ idPeriodePaie, idSite }) => (await apiClient.get('/pointages/synthese/semaines', { params: { idPeriodePaie, idSite: cleanSite(idSite) } })).data,
        getWeekDetail: async ({ idPeriodePaie, dateDebut, dateFin, idSite }) => (await apiClient.get('/pointages/synthese/semaine-detail', { params: { idPeriodePaie, dateDebut, dateFin, idSite: cleanSite(idSite) } })).data,
        getPreflight: async ({ idPeriodePaie }) => {
            const data = (await apiClient.get('/pointages/synthese/preflight', { params: { idPeriodePaie } })).data;
            if (typeof data?.pret !== 'boolean') throw new Error('Précontrôle Pointage invalide : décision serveur absente.');
            return data;
        },
        getAdjustments: async ({ idPeriodePaie, idEmploye }) => (await apiClient.get('/pointages/synthese/ajustements', { params: { idPeriodePaie, idEmploye } })).data,
        createAdjustment: async ({ idPeriodePaie, idEmploye, minutesSignees, motif }) => {
            const data = (await apiClient.post('/pointages/synthese/ajustements', { minutesSignees, motif }, { params: { idPeriodePaie, idEmploye } })).data;
            invalidateEmployeeSheets();
            notifyPointageUpdated();
            return data;
        },
        cancelAdjustment: async ({ idPeriodePaie, idEmploye, idAjustement, motif }) => {
            const data = (await apiClient.post(`/pointages/synthese/ajustements/${idAjustement}/annuler`, { motif }, { params: { idPeriodePaie, idEmploye } })).data;
            invalidateEmployeeSheets();
            notifyPointageUpdated();
            return data;
        },
        recalculateWeeks: async ({ idPeriodePaie, idSite }) => {
            const data = (await apiClient.post('/pointages/synthese/calculer-tout', {
                idPeriodePaie: Number(idPeriodePaie), idSite: cleanSite(idSite)
            })).data;
            invalidateEmployeeSheets();
            notifyPointageUpdated();
            return data;
        }
    },
    period: {
        list: async () => (await apiClient.get('/periodes')).data,
        options: async () => (await apiClient.get('/periodes', { params: { lightweight: true } })).data,
        getContext: async (periodId = null, date = null) => {
            const params = {};
            if (periodId) params.periodId = periodId;
            if (date) params.date = date;
            return (await apiClient.get('/periodes/context', { params })).data;
        },
        create: async (periodData) => (await apiClient.post('/periodes', periodData)).data,
        execute: async (id, command, payload) => {
            const data = (await apiClient.post(`/periodes/${id}/actions/${command}`, payload)).data;
            invalidateEmployeeSheets();
            return data;
        },
        simulate: async (dateDebut, dateFin) => (await apiClient.get('/periodes/simuler', { params: { dateDebut, dateFin } })).data
    }
};

// Noms existants conservés dans les composants jusqu'à leur migration vers les groupes ci-dessus.
export const attendanceService = {
    // La saisie par période utilise les méthodes regroupées sous `daily`.
    // Les alias ci-dessous restent disponibles pour les écrans plus anciens.
    daily: attendanceApi.daily,
    getGrilleSaisie: attendanceApi.daily.getGrid,
    previewDay: attendanceApi.daily.preview,
    previewDays: attendanceApi.daily.previewBatch,
    enregistrerBatch: attendanceApi.daily.saveBatch,
    getAnomalies: attendanceApi.anomalies.list,
    regulariser: attendanceApi.anomalies.regularize,
    getFicheEmploye: attendanceApi.employees.getSheet,
    getImportTemplate: attendanceApi.employees.getImportTemplate,
    previewImport: attendanceApi.employees.previewImport,
    exportExcel: attendanceApi.employees.exportExcel,
    exportPdf: attendanceApi.employees.exportPdf,
    getPreflight: attendanceApi.summary.getPreflight
};
export const pointageSyntheseService = {
    getSynthese: attendanceApi.summary.getPeriod,
    getSemaines: attendanceApi.summary.getWeeks,
    getSemaineDetail: attendanceApi.summary.getWeekDetail,
    getPreflight: attendanceApi.summary.getPreflight,
    getAjustements: attendanceApi.summary.getAdjustments,
    creerAjustement: attendanceApi.summary.createAdjustment,
    annulerAjustement: attendanceApi.summary.cancelAdjustment,
    calculerTout: attendanceApi.summary.recalculateWeeks,
    calculerSemaines: attendanceApi.summary.recalculateWeeks
};
export const periodePaieService = {
    getAllPeriodes: attendanceApi.period.list,
    getPeriodOptions: attendanceApi.period.options,
    getContext: attendanceApi.period.getContext,
    createPeriode: attendanceApi.period.create,
    executeCommand: attendanceApi.period.execute,
    simulerPeriode: attendanceApi.period.simulate
};

export default attendanceApi;
