import { createContext, useContext, useState, useEffect } from 'react';
import apiClient, { setAccessToken } from '@/api/apiClient';

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
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    const handleTokenUpdate = (token) => {
        setAccessToken(token);
        if (token) {
            const decoded = parseJwt(token);
            if (decoded) {
                setUser({
                    identifiant: decoded.sub,
                    role: decoded.role,
                    permissions: Array.isArray(decoded.permissions) ? decoded.permissions : [],
                });
            } else {
                setUser(null);
            }
        } else {
            setUser(null);
        }
    };

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

    // Vérification initiale de session au montage
    useEffect(() => {
        const verifySession = async () => {
            try {
                // Tente un rafraîchissement silencieux si le cookie est présent
                const response = await apiClient.post('/auth/refresh');
                const { accessToken } = response.data;
                handleTokenUpdate(accessToken);
            } catch {
                // Pas de session valide ou pas de cookie
                handleTokenUpdate(null);
            } finally {
                setLoading(false);
            }
        };

        verifySession();

        // Écouter l'événement d'expiration émis par apiClient
        const handleAuthExpired = () => {
            handleTokenUpdate(null);
        };

        window.addEventListener('auth-expired', handleAuthExpired);
        return () => {
            window.removeEventListener('auth-expired', handleAuthExpired);
        };
    }, []);

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
