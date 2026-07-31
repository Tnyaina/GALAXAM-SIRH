import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';

let _accessToken = null;

export const getAccessToken = () => _accessToken;
export const setAccessToken = (token) => {
    _accessToken = token;
};

const apiClient = axios.create({
    baseURL: BASE_URL,
    withCredentials: true,
    headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
    },
});

// Intercepteur de requête : attacher l'Access Token s'il est présent
apiClient.interceptors.request.use(
    (config) => {
        if (_accessToken) {
            config.headers['Authorization'] = `Bearer ${_accessToken}`;
        }
        return config;
    },
    (error) => {
        return Promise.reject(error);
    }
);

// Intercepteur de réponse : gérer l'expiration de session (401) et tenter un refresh automatique
apiClient.interceptors.response.use(
    (response) => {
        return response;
    },
    async (error) => {
        const originalRequest = error.config;

        // Évite une boucle infinie de rafraîchissement si la requête initiale est déjà le refresh ou le login
        if (
            originalRequest.url.includes('/auth/refresh') || 
            originalRequest.url.includes('/auth/login') ||
            originalRequest.url.endsWith('auth/refresh') ||
            originalRequest.url.endsWith('auth/login')
        ) {
            return Promise.reject(error);
        }

        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;

            try {
                // Utilise une instance Axios brute pour appeler /auth/refresh sans intercepteur
                const response = await axios.post(
                    `${BASE_URL}auth/refresh`,
                    {},
                    { withCredentials: true }
                );

                const { accessToken } = response.data;
                setAccessToken(accessToken);

                originalRequest.headers['Authorization'] = `Bearer ${accessToken}`;
                return apiClient(originalRequest);
            } catch (refreshError) {
                // Si le rafraîchissement échoue (ex: cookie expiré), on nettoie et notifie l'application
                setAccessToken(null);
                window.dispatchEvent(new Event('auth-expired'));
                return Promise.reject(refreshError);
            }
        }

        return Promise.reject(error);
    }
);

export default apiClient;