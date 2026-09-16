// apiClient.js
import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';

let _accessToken = null;
let refreshPromise = null;

// Helper pour décoder le JWT
const parseJwt = (token) => {
    try {
        const base64Url = token.split('.')[1];
        const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
        const jsonPayload = decodeURIComponent(
            window
                .atob(base64)
                .split('')
                .map((c) => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2))
                .join('')
        );
        return JSON.parse(jsonPayload);
    } catch {
        return null;
    }
};

export const getAccessToken = () => _accessToken;
export const setAccessToken = (token) => {
    _accessToken = token;
};

// Fonction de refresh verrouillée (évite les appels multiples simultanés)
export const refreshTokenOnce = () => {
    if (!refreshPromise) {
        refreshPromise = axios.post(`${BASE_URL}auth/refresh`, {}, { withCredentials: true })
            .then((response) => {
                const { accessToken } = response.data;
                setAccessToken(accessToken);
                window.dispatchEvent(new CustomEvent('auth-token-refreshed', { detail: accessToken }));
                return accessToken;
            })
            .catch((err) => {
                setAccessToken(null);
                window.dispatchEvent(new Event('auth-expired'));
                throw err;
            })
            .finally(() => {
                refreshPromise = null;
            });
    }
    return refreshPromise;
};

const apiClient = axios.create({
    baseURL: BASE_URL,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
});

// Intercepteur de requête : Rafraîchissement proactif
apiClient.interceptors.request.use(
    async (config) => {
        if (
            config.url.includes('/auth/refresh') ||
            config.url.includes('/auth/login') ||
            config.url.includes('/auth/logout')
        ) {
            return config;
        }

        let token = _accessToken;
        if (token) {
            const decoded = parseJwt(token);
            if (decoded && decoded.exp) {
                const timeUntilExpiry = (decoded.exp * 1000) - Date.now();
                // Si le token est expiré ou expire dans moins de 10 secondes, on rafraîchit proactivement
                if (timeUntilExpiry < 10000) {
                    try {
                        const newToken = await refreshTokenOnce();
                        if (newToken) {
                            token = newToken;
                        }
                    } catch {
                        // L'échec sera géré par la réponse HTTP normale
                    }
                }
            }
        }

        if (token) {
            config.headers['Authorization'] = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// Intercepteur de réponse : Fallback en cas de 401
apiClient.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        if (
            !originalRequest ||
            originalRequest.url.includes('/auth/refresh') ||
            originalRequest.url.includes('/auth/login') ||
            originalRequest.url.includes('/auth/logout')
        ) {
            return Promise.reject(error);
        }

        // Si 401 inattendu (et qu'on a pas déjà retenté)
        if (error.response?.status === 401 && !originalRequest._retry) {
            // Si on n'a pas/plus de token d'accès en mémoire, ne tente pas de rafraîchir !
            if (!getAccessToken()) {
                window.dispatchEvent(new Event('auth-expired'));
                return Promise.reject(error);
            }

            originalRequest._retry = true;
            try {
                const newToken = await refreshTokenOnce();
                originalRequest.headers['Authorization'] = `Bearer ${newToken}`;
                return apiClient(originalRequest);
            } catch (refreshError) {
                return Promise.reject(refreshError);
            }
        }

        // Normalisation d'un message convivial et non technique pour l'utilisateur
        let userMessage = "Une erreur inattendue est survenue.";
        if (error.response) {
            const data = error.response.data;
            if (typeof data === 'string' && data.trim()) {
                userMessage = data.trim();
            } else if (data?.message && typeof data.message === 'string') {
                userMessage = data.message;
            } else if (error.response.status === 400) {
                userMessage = "Les informations fournies sont incomplètes ou invalides.";
            } else if (error.response.status === 403) {
                userMessage = "Vous ne disposez pas des autorisations nécessaires pour cette opération.";
            } else if (error.response.status === 404) {
                userMessage = "L'élément demandé est introuvable ou a été supprimé.";
            } else if (error.response.status === 409) {
                userMessage = "Cette opération est en conflit avec des données existantes (ex: doublon ou contrainte métier).";
            } else if (error.response.status >= 500) {
                userMessage = "Une anomalie temporaire est survenue côté serveur. Veuillez réessayer dans quelques instants.";
            }
        } else if (error.request) {
            userMessage = "Impossible de contacter le serveur. Veuillez vérifier votre connexion au réseau.";
        }

        error.userMessage = userMessage;
        error.message = userMessage;
        error.code = error.response?.data?.code || 'UNEXPECTED_ERROR';
        error.details = error.response?.data?.details || {};
        error.issues = error.response?.data?.issues || [];

        return Promise.reject(error);
    }
);

const RAW_HTTP_ERRORS = {
    'conflict': "Cette opération est en conflit avec l'état actuel des données (ex: période clôturée ou validée, contrainte métier).",
    'bad request': "Les données transmises sont incomplètes ou invalides.",
    'not found': "L'élément demandé est introuvable ou a été supprimé.",
    'forbidden': "Vous ne disposez pas des autorisations nécessaires pour cette opération.",
    'unauthorized': "Votre session a expiré ou vos identifiants sont invalides.",
    'internal server error': "Une anomalie temporaire est survenue côté serveur. Veuillez réessayer dans quelques instants.",
    'bad gateway': "Le serveur distant est momentanément indisponible.",
    'service unavailable': "Le service est temporairement indisponible.",
    'gateway timeout': "Le serveur a mis trop de temps à répondre."
};

/**
 * Compatibilité d'affichage : le backend fournit déjà un message utilisateur et un code stable.
 * Aucun texte technique ou code HTTP brut n'est affiché sans traduction.
 */
export const cleanUserFacingMessage = (rawMsg, fallback = "Une erreur inattendue est survenue.") => {
    if (!rawMsg || typeof rawMsg !== 'string') return fallback;
    const msg = rawMsg.trim();
    if (!msg) return fallback;

    const lower = msg.toLowerCase();
    if (RAW_HTTP_ERRORS[lower]) {
        return fallback !== "Une erreur inattendue est survenue." ? fallback : RAW_HTTP_ERRORS[lower];
    }

    return msg;
};

/**
 * Extrait systématiquement un message d'erreur lisible, soigné et compréhensible par un utilisateur final.
 */
export const getErrorMessage = (error, fallback = "Une erreur inattendue est survenue.") => {
    if (!error) return fallback;
    if (typeof error === 'string') return cleanUserFacingMessage(error, fallback);

    let candidate = null;
    if (error.userMessage && typeof error.userMessage === 'string') {
        candidate = error.userMessage;
    } else if (error.response?.data) {
        const d = error.response.data;
        if (typeof d === 'string' && d.trim() !== '') {
            candidate = d;
        } else if (d.message && typeof d.message === 'string') {
            candidate = d.message;
        } else if (d.error && typeof d.error === 'string' && !RAW_HTTP_ERRORS[d.error.toLowerCase()]) {
            candidate = d.error;
        }
    } else if (error.message && typeof error.message === 'string') {
        candidate = error.message;
    }

    return cleanUserFacingMessage(candidate, fallback);
};

export default apiClient;

