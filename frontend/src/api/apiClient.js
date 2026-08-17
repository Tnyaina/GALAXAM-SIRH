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

        if (error.response?.status === 403) {
            const defaultMsg = "Accès refusé (403) : Vous ne disposez pas des autorisations nécessaires pour effectuer cette opération.";
            if (!error.response?.data || typeof error.response.data === 'object') {
                error.response.data = defaultMsg;
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;