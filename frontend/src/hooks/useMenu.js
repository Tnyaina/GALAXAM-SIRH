import { useCallback, useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext.jsx';
import apiClient from '@/api/apiClient';

/**
 * Récupère l'arborescence de menu déjà filtrée par rôle côté back
 * (GET /auth/menu — voir AuthController#getMenu).
 *
 * Le back renvoie déjà l'arbre construit (sousMenus imbriqués),
 * pas la peine de le reconstruire côté front.
 */
export function useMenu() {
    const { isAuthenticated } = useAuth();
    const [menu, setMenu] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const fetchMenu = useCallback(async () => {
        if (!isAuthenticated) {
            setMenu([]);
            setLoading(false);
            return;
        }

        setLoading(true);
        setError(null);

        try {
            const response = await apiClient.get('/auth/menu');
            setMenu(Array.isArray(response.data) ? response.data : []);
        } catch (err) {
            setError(err);
            setMenu([]);
        } finally {
            setLoading(false);
        }
    }, [isAuthenticated]);

    useEffect(() => {
        fetchMenu();
    }, [fetchMenu]);

    return { menu, loading, error, refetch: fetchMenu };
}
