import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import apiClient, { setAccessToken, getAccessToken } from '@/api/apiClient';

const AuthContext = createContext(null);

// Fonction utilitaire pour décoder le payload d'un JWT sans dépendance externe
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

export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(() => {
        try {
            const saved = localStorage.getItem('galaxam-user');
            return saved ? JSON.parse(saved) : null;
        } catch {
            return null;
        }
    });
    const [loading, setLoading] = useState(true);
    const refreshTimerRef = useRef(null);

    const clearRefreshTimer = useCallback(() => {
        if (refreshTimerRef.current) {
            clearTimeout(refreshTimerRef.current);
            refreshTimerRef.current = null;
        }
    }, []);

    const silentRefresh = useCallback(async () => {
        try {
            const response = await apiClient.post('/auth/refresh');
            const { accessToken } = response.data;
            if (accessToken) {
                setAccessToken(accessToken);
                const decoded = parseJwt(accessToken);
                if (decoded) {
                    const loggedUser = {
                        identifiant: decoded.sub,
                        role: decoded.role,
                        permissions: Array.isArray(decoded.permissions) ? decoded.permissions : [],
                        exp: decoded.exp,
                    };
                    setUser(loggedUser);
                    localStorage.setItem('galaxam-user', JSON.stringify(loggedUser));
                }
                return accessToken;
            }
            return null;
        } catch (error) {
            // Uniquement déconnecter si le serveur renvoie 401 ou 403 (session expirée/invalide)
            if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                setAccessToken(null);
                setUser(null);
                localStorage.removeItem('galaxam-user');
                clearRefreshTimer();
            }
            return null;
        }
    }, [clearRefreshTimer]);

    const scheduleTokenRefreshRef = useRef();

    const scheduleTokenRefresh = useCallback((token) => {
        clearRefreshTimer();
        if (!token) return;

        const decoded = parseJwt(token);
        if (!decoded || !decoded.exp) return;

        const timeUntilExpiry = (decoded.exp * 1000) - Date.now();
        // Rafraîchir 90 secondes avant l'expiration, ou dans 5s si déjà presque expiré
        const refreshBuffer = 90000;
        const delay = Math.max(timeUntilExpiry - refreshBuffer, 5000);

        refreshTimerRef.current = setTimeout(async () => {
            const newToken = await silentRefresh();
            if (newToken) {
                scheduleTokenRefreshRef.current?.(newToken);
            }
        }, delay);
    }, [clearRefreshTimer, silentRefresh]);

    useEffect(() => {
        scheduleTokenRefreshRef.current = scheduleTokenRefresh;
    }, [scheduleTokenRefresh]);

    const handleTokenUpdate = useCallback((token) => {
        setAccessToken(token);
        if (token) {
            const decoded = parseJwt(token);
            if (decoded) {
                const loggedUser = {
                    identifiant: decoded.sub,
                    role: decoded.role,
                    permissions: Array.isArray(decoded.permissions) ? decoded.permissions : [],
                    exp: decoded.exp,
                };
                setUser(loggedUser);
                localStorage.setItem('galaxam-user', JSON.stringify(loggedUser));
                scheduleTokenRefresh(token);
            } else {
                setUser(null);
                localStorage.removeItem('galaxam-user');
                clearRefreshTimer();
            }
        } else {
            setUser(null);
            localStorage.removeItem('galaxam-user');
            clearRefreshTimer();
        }
    }, [scheduleTokenRefresh, clearRefreshTimer]);

    const hasPermission = (permissionCode) => {
        if (!user) return false;
        if (user.role === 'ROLE_ADMIN') return true;
        if (!user.permissions) return false;
        return user.permissions.includes(permissionCode);
    };

    // Connexion utilisateur
    const login = async (identifiant, password) => {
        try {
            const response = await apiClient.post('/auth/login', {
                identifiant,
                motDePasse: password,
            });
            const { accessToken } = response.data;
            handleTokenUpdate(accessToken);
            return { success: true };
        } catch (error) {
            const message = error.response?.data || "Une erreur est survenue lors de la connexion.";
            return { success: false, message };
        }
    };

    // Déconnexion utilisateur
    const logout = async () => {
        try {
            await apiClient.post('/auth/logout');
        } catch (error) {
            console.error("Erreur lors de la déconnexion sur le serveur", error);
        } finally {
            handleTokenUpdate(null);
        }
    };

    // Verification initiale + ecouteurs d'evenements
    useEffect(() => {
        const verifySession = async () => {
            const savedUser = localStorage.getItem('galaxam-user');
            if (!savedUser) {
                setLoading(false);
                return;
            }

            try {
                const response = await apiClient.post('/auth/refresh');
                const { accessToken } = response.data;
                handleTokenUpdate(accessToken);
            } catch (error) {
                // Déconnecter seulement si la session est expirée/invalide
                if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                    handleTokenUpdate(null);
                } else {
                    // Sinon (erreur réseau), on garde la session locale
                    const currentToken = getAccessToken();
                    if (currentToken) {
                        scheduleTokenRefresh(currentToken);
                    }
                }
            } finally {
                setLoading(false);
            }
        };

        verifySession();

        const handleAuthExpired = () => {
            handleTokenUpdate(null);
        };

        const handleTokenRefreshed = (e) => {
            if (e.detail) {
                handleTokenUpdate(e.detail);
            }
        };

        // Rafraîchir si l'onglet redevient actif et que le token approche de son expiration (ou expiré)
        const handleFocusOrVisibility = () => {
            if (document.visibilityState === 'visible') {
                const currentToken = getAccessToken();
                if (currentToken) {
                    const decoded = parseJwt(currentToken);
                    if (decoded && decoded.exp) {
                        const timeUntilExpiry = (decoded.exp * 1000) - Date.now();
                        // Si le token expire dans moins de 2 minutes ou est expiré, rafraîchir immédiatement
                        if (timeUntilExpiry < 120000) {
                            silentRefresh().then((newToken) => {
                                if (newToken) {
                                    scheduleTokenRefresh(newToken);
                                }
                            });
                        }
                    }
                }
            }
        };

        window.addEventListener('auth-expired', handleAuthExpired);
        window.addEventListener('auth-token-refreshed', handleTokenRefreshed);
        window.addEventListener('visibilitychange', handleFocusOrVisibility);
        window.addEventListener('focus', handleFocusOrVisibility);

        return () => {
            clearRefreshTimer();
            window.removeEventListener('auth-expired', handleAuthExpired);
            window.removeEventListener('auth-token-refreshed', handleTokenRefreshed);
            window.removeEventListener('visibilitychange', handleFocusOrVisibility);
            window.removeEventListener('focus', handleFocusOrVisibility);
        };
    }, [handleTokenUpdate, clearRefreshTimer, scheduleTokenRefresh, silentRefresh]);

    const value = {
        user,
        isAuthenticated: !!user,
        loading,
        login,
        logout,
        hasPermission,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuth doit être utilisé à l'intérieur d'un AuthProvider");
    }
    return context;
};
