import apiClient, { getAccessToken } from '@/api/apiClient';
import { createReadCache, dispatchCacheEvent, sessionCacheKey } from '@/utils/readCache';

const pointageParameterReads = createReadCache(60_000);

function invalidatePointageParameters() {
    pointageParameterReads.invalidate();
    dispatchCacheEvent('pointage-data-updated');
}

async function cachedRead(key, request) {
    return pointageParameterReads.get(sessionCacheKey(key, getAccessToken()), async () => (await request()).data);
}

async function writeParameter(request) {
    const response = await request;
    invalidatePointageParameters();
    return response.data;
}

const pointageParametreService = {
    // ----------------------------------------------------
    // TOLÉRANCES D'ARRIVÉE ANTICIPÉE PAR SITE
    // ----------------------------------------------------
    getTolerances: async () => {
        return cachedRead('tolerances', () => apiClient.get('/pointages/parametres/tolerances'));
    },
    saveTolerance: async (data) => writeParameter(apiClient.post('/pointages/parametres/tolerances', data)),

    // ----------------------------------------------------
    // DÉROGATIONS INDIVIDUELLES D'INDEMNITÉS (NIVEAU 2)
    // ----------------------------------------------------
    getDerogations: async () => {
        return cachedRead('derogations', () => apiClient.get('/pointages/parametres/derogations'));
    },
    createDerogation: async (data) => writeParameter(apiClient.post('/pointages/parametres/derogations', data)),
    updateDerogation: async (id, data) => writeParameter(apiClient.put(`/pointages/parametres/derogations/${id}`, data)),
    deleteDerogation: async (id) => writeParameter(apiClient.delete(`/pointages/parametres/derogations/${id}`))
};

export default pointageParametreService;
