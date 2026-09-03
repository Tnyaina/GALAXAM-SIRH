import { createContext, useState, useEffect, useCallback, useRef } from 'react';
import { useAuth } from '@/context/AuthContext';
import apiClient from '@/api/apiClient';
import {
    MENU_CHANGED_EVENT,
    readMenuCache,
    writeMenuCache
} from '@/utils/menuEvents';

// eslint-disable-next-line react-refresh/only-export-components
export const MenuContext = createContext(null);

export function MenuProvider({ children }) {
    const { isAuthenticated, user } = useAuth();

    // Initialisation immédiate depuis le cache s'il existe (0ms, aucun flash)
    const [menu, setMenu] = useState(() => {
        if (!isAuthenticated || !user) return [];
        const cached = readMenuCache(user);
        return Array.isArray(cached) ? cached : [];
    });

    const [loading, setLoading] = useState(() => {
        if (!isAuthenticated) return false;
        const cached = readMenuCache(user);
        return !(Array.isArray(cached) && cached.length > 0);
    });

    const [error, setError] = useState(null);
    const hasInitialLoadedRef = useRef(false);

    const fetchMenu = useCallback(async () => {
        if (!isAuthenticated) {
            setMenu([]);
            setLoading(false);
            hasInitialLoadedRef.current = false;
            return;
        }

        // Si nous n'avons aucune donnée en cache ou en mémoire, afficher le loader
        if (!hasInitialLoadedRef.current && menu.length === 0) {
            setLoading(true);
        }

        try {
            const response = await apiClient.get('/auth/menu');
            const data = Array.isArray(response.data) ? response.data : [];
            setMenu(data);
            setError(null);
            writeMenuCache(user, data);
            hasInitialLoadedRef.current = true;
        } catch (err) {
            setError(err);
            // Conserver le cache si disponible, sinon vider si jamais chargé
            if (!hasInitialLoadedRef.current && menu.length === 0) {
                setMenu([]);
            }
        } finally {
            setLoading(false);
        }
    }, [isAuthenticated, user, menu.length]);

    // Chargement initial ou changement d'utilisateur (SWR: revalidation en arrière-plan)
    useEffect(() => {
        if (isAuthenticated && user) {
            const cached = readMenuCache(user);
            if (Array.isArray(cached) && cached.length > 0) {
                setMenu(cached);
                setLoading(false);
                hasInitialLoadedRef.current = true;
            }
            fetchMenu();
        } else {
            setMenu([]);
            setLoading(false);
            hasInitialLoadedRef.current = false;
        }
    }, [isAuthenticated, user]); // eslint-disable-line react-hooks/exhaustive-deps

    // Écouteur global pour les événements de mutation des menus (ajout, modification, suppression, droits rôles)
    useEffect(() => {
        const handleMenuChange = () => {
            fetchMenu();
        };

        window.addEventListener(MENU_CHANGED_EVENT, handleMenuChange);
        return () => {
            window.removeEventListener(MENU_CHANGED_EVENT, handleMenuChange);
        };
    }, [fetchMenu]);

    const value = {
        menu,
        loading,
        error,
        refreshMenu: fetchMenu,
        refetch: fetchMenu
    };

    return (
        <MenuContext.Provider value={value}>
            {children}
        </MenuContext.Provider>
    );
}

export default MenuContext;
