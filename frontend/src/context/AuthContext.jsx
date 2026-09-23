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
    // État local, effets de session et actions d'authentification.
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

    const hasPermission = useCallback((permissionCode) => {
        if (!user) return false;
        if (user.role === 'ROLE_ADMIN') return true;
        if (!user.permissions) return false;
        if (Array.isArray(permissionCode)) {
            return permissionCode.some((p) => user.permissions.includes(p));
        }
        return user.permissions.includes(permissionCode);
    }, [user]);

    const hasAnyPermission = useCallback((...permissions) => {
        if (!user) return false;
        if (user.role === 'ROLE_ADMIN') return true;
        if (!user.permissions) return false;
        const flat = permissions.flat();
        return flat.some((p) => user.permissions.includes(p));
    }, [user]);

    const hasAllPermissions = useCallback((...permissions) => {
        if (!user) return false;
        if (user.role === 'ROLE_ADMIN') return true;
        if (!user.permissions) return false;
        const flat = permissions.flat();
        return flat.every((p) => user.permissions.includes(p));
    }, [user]);

    const hasRole = useCallback((roleName) => {
        if (!user) return false;
        if (Array.isArray(roleName)) {
            return roleName.includes(user.role);
        }
        return user.role === roleName;
    }, [user]);

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
            let message = "Identifiant ou mot de passe incorrect. Veuillez vérifier votre saisie.";
            if (error.response) {
                if (typeof error.response.data === 'string' && error.response.data.trim() !== '') {
                    message = error.response.data;
                } else if (error.response.data?.message) {
                    message = error.response.data.message;
                } else if (error.response.status === 401) {
                    message = "Identifiant ou mot de passe incorrect. Veuillez vérifier votre saisie.";
                } else if (error.response.status === 403) {
                    message = "Votre compte utilisateur est désactivé. Veuillez contacter le service RH.";
                } else if (error.response.status >= 500) {
                    message = "Le serveur est temporairement inaccessible. Veuillez réessayer dans un instant.";
                }
            } else if (error.request) {
                message = "Impossible de contacter le serveur. Veuillez vérifier votre connexion réseau.";
            }
            return { success: false, message };
        }
    };

    // Déconnexion utilisateur
    const logout = async () => {
        handleTokenUpdate(null);
        try {
            await apiClient.post('/auth/logout');
        } catch (error) {
            console.error("Erreur lors de la déconnexion sur le serveur", error);
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
        hasAnyPermission,
        hasAllPermissions,
        hasRole,
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
