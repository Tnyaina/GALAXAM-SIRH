import { createContext, useContext, useState, useEffect, useRef, useCallback } from 'react';
import apiClient, { setAccessToken, getAccessToken, getErrorMessage } from '@/api/apiClient';
import { IDLE_ACTIVITY_STORAGE_KEY, USER_ACTIVITY_EVENTS } from '@/context/idleSession';

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
                        identifiant: decoded.identifiant || decoded.sub,
                        email: decoded.sub,
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

    const handleTokenUpdate = useCallback((token, idleExpiresAt = null) => {
        setAccessToken(token);
        if (token) {
            const decoded = parseJwt(token);
            if (decoded) {
                const loggedUser = {
                    identifiant: decoded.identifiant || decoded.sub,
                    email: decoded.sub,
                    role: decoded.role,
                    permissions: Array.isArray(decoded.permissions) ? decoded.permissions : [],
                    exp: decoded.exp,
                };
                setUser(loggedUser);
                localStorage.setItem('galaxam-user', JSON.stringify(loggedUser));
                if (Number.isFinite(Number(idleExpiresAt)) && Number(idleExpiresAt) > 0) {
                    localStorage.setItem(IDLE_ACTIVITY_STORAGE_KEY, String(Number(idleExpiresAt)));
                }
                scheduleTokenRefresh(token);
            } else {
                setUser(null);
                localStorage.removeItem('galaxam-user');
                clearRefreshTimer();
            }
        } else {
            setUser(null);
            localStorage.removeItem('galaxam-user');
            localStorage.removeItem(IDLE_ACTIVITY_STORAGE_KEY);
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
    const login = async (email, password) => {
        try {
            const response = await apiClient.post('/auth/login', {
                email,
                motDePasse: password,
            });
            const { accessToken, idleExpiresAt } = response.data;
            handleTokenUpdate(accessToken, idleExpiresAt);
            return { success: true };
        } catch (error) {
            let message = "E-mail ou mot de passe incorrect. Veuillez vérifier votre saisie.";
            if (error.code === 'API_INCOMPATIBLE_RESPONSE') {
                message = getErrorMessage(error);
            } else if (error.response) {
                if (error.response.status === 401) {
                    message = "E-mail ou mot de passe incorrect. Veuillez vérifier votre saisie.";
                } else if (error.response.status === 403) {
                    message = "Votre compte utilisateur est désactivé. Veuillez contacter votre administrateur.";
                } else if (error.response.status >= 500) {
                    message = "Le serveur est temporairement inaccessible. Veuillez réessayer dans un instant.";
                } else {
                    message = getErrorMessage(error, message);
                }
            } else if (error.request) {
                message = "Impossible de contacter le serveur. Veuillez vérifier votre connexion réseau.";
            }
            return { success: false, message };
        }
    };

    // Déconnexion utilisateur
    const logout = async () => {
        localStorage.removeItem('galaxam-auth-message');
        handleTokenUpdate(null);
        try {
            await apiClient.post('/auth/logout');
        } catch (error) {
            console.error("Erreur lors de la déconnexion sur le serveur", error);
        }
    };

    const expireIdleSession = useCallback(async () => {
        localStorage.setItem('galaxam-auth-message', 'Votre session a expiré après 15 minutes d’inactivité. Veuillez vous reconnecter.');
        handleTokenUpdate(null);
        try { await apiClient.post('/auth/logout'); } catch { /* la session peut déjà être révoquée */ }
    }, [handleTokenUpdate]);

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
                const { accessToken, idleExpiresAt } = response.data;
                handleTokenUpdate(accessToken, idleExpiresAt);
            } catch (error) {
                // Déconnecter seulement si la session est expirée/invalide
                if (error.response && (error.response.status === 401 || error.response.status === 403)) {
                    if (error.response.data?.code === 'IDLE_SESSION_EXPIRED') {
                        localStorage.setItem('galaxam-auth-message', 'Votre session a expiré après 15 minutes d’inactivité. Veuillez vous reconnecter.');
                    }
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

        const handleAuthExpired = (event) => {
            if (event.detail?.code === 'IDLE_SESSION_EXPIRED') {
                localStorage.setItem('galaxam-auth-message', 'Votre session a expiré après 15 minutes d’inactivité. Veuillez vous reconnecter.');
            }
            handleTokenUpdate(null);
        };

        const handleTokenRefreshed = (e) => {
            if (e.detail?.accessToken) {
                handleTokenUpdate(e.detail.accessToken, e.detail.idleExpiresAt);
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

    // Only explicit human input is reported to the backend. No mousemove, API, timer or refresh activity counts.
    useEffect(() => {
        if (!user) return undefined;
        let pendingActivity = null;
        let lastSignalAt = 0;
        let ending = false;

        const currentExpiry = () => Number(localStorage.getItem(IDLE_ACTIVITY_STORAGE_KEY));
        const checkIdle = () => {
            const expiresAt = currentExpiry();
            if (Number.isFinite(expiresAt) && expiresAt > 0 && Date.now() >= expiresAt && !ending) {
                ending = true;
                expireIdleSession();
            }
        };
        const sendActivity = async () => {
            pendingActivity = null;
            if (ending || !getAccessToken()) return;
            try {
                const response = await apiClient.post('/auth/activity');
                const expiry = Number(response.data?.idleExpiresAt);
                if (expiry > 0) localStorage.setItem(IDLE_ACTIVITY_STORAGE_KEY, String(expiry));
            } catch (error) {
                if (error.response?.status === 401 || error.response?.status === 403) {
                    ending = true;
                    expireIdleSession();
                }
            }
        };
        const onUserActivity = () => {
            const now = Date.now();
            if (now - lastSignalAt >= 2000) {
                lastSignalAt = now;
                sendActivity();
            } else if (!pendingActivity) {
                pendingActivity = setTimeout(() => {
                    lastSignalAt = Date.now();
                    sendActivity();
                }, 2000 - (now - lastSignalAt));
            }
        };
        const onVisibility = () => {
            if (document.visibilityState === 'visible') checkIdle();
        };
        const onStorage = (event) => {
            if (event.key === IDLE_ACTIVITY_STORAGE_KEY) checkIdle();
            if (event.key === 'galaxam-user' && !event.newValue) handleTokenUpdate(null);
        };

        USER_ACTIVITY_EVENTS.forEach((eventName) => window.addEventListener(eventName, onUserActivity, { passive: true }));
        window.addEventListener('pageshow', checkIdle);
        window.addEventListener('focus', checkIdle);
        window.addEventListener('visibilitychange', onVisibility);
        window.addEventListener('storage', onStorage);
        const interval = setInterval(checkIdle, 1000);
        checkIdle();

        return () => {
            USER_ACTIVITY_EVENTS.forEach((eventName) => window.removeEventListener(eventName, onUserActivity));
            window.removeEventListener('pageshow', checkIdle);
            window.removeEventListener('focus', checkIdle);
            window.removeEventListener('visibilitychange', onVisibility);
            window.removeEventListener('storage', onStorage);
            clearInterval(interval);
            if (pendingActivity) clearTimeout(pendingActivity);
        };
    }, [user, expireIdleSession, handleTokenUpdate]);

    const getDefaultHomeRoute = useCallback(() => {
        if (!user) return '/';
        // Le centre de paie est l'accueil opérationnel de l'administrateur.
        if (user.role === 'ROLE_ADMIN') {
            return '/paie';
        }
        if (user.permissions && user.permissions.includes('EMPLOYE_READ')) {
            return '/dashboard';
        }
        if (hasPermission('POINTAGE_READ')) {
            return '/pointages';
        }
        if (hasPermission('CONGE_READ')) {
            return '/conges';
        }
        if (hasPermission('PAIE_READ')) {
            return '/paie/dossiers-sortie';
        }
        return '/non-autorise';
    }, [user, hasPermission]);

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
        getDefaultHomeRoute,
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
