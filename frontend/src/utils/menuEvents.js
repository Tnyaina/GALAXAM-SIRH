export const MENU_CHANGED_EVENT = 'galaxam:menu-changed';
const CACHE_KEY_PREFIX = 'galaxam-menu-cache-';

/**
 * Génère la clé de cache unique par utilisateur et rôle.
 */
export const getMenuCacheKey = (user) => {
    const id = user?.identifiant || 'anon';
    const role = user?.role || 'none';
    return `${CACHE_KEY_PREFIX}${id}-${role}`;
};

/**
 * Lecture synchrone du cache de menu depuis sessionStorage pour un affichage instantané.
 */
export const readMenuCache = (user) => {
    if (typeof window === 'undefined' || !user) return null;
    try {
        const item = window.sessionStorage.getItem(getMenuCacheKey(user));
        return item ? JSON.parse(item) : null;
    } catch {
        return null;
    }
};

/**
 * Sauvegarde du menu dans sessionStorage.
 */
export const writeMenuCache = (user, data) => {
    if (typeof window === 'undefined' || !user) return;
    try {
        window.sessionStorage.setItem(getMenuCacheKey(user), JSON.stringify(data));
    } catch {
        // Ignorer en cas de quota ou navigation restreinte
    }
};

/**
 * Nettoyage du cache de menu (à la déconnexion ou lors d'un changement de session).
 */
export const clearMenuCache = (user) => {
    if (typeof window === 'undefined') return;
    try {
        if (user) {
            window.sessionStorage.removeItem(getMenuCacheKey(user));
        } else {
            Object.keys(window.sessionStorage).forEach((key) => {
                if (key.startsWith(CACHE_KEY_PREFIX)) {
                    window.sessionStorage.removeItem(key);
                }
            });
        }
    } catch {
        // Ignorer
    }
};

/**
 * Notifie l'application entière (sidebar, layout, etc.) qu'une action affectant les menus
 * a eu lieu, déclenchant ainsi un rechargement automatique en arrière-plan sans rechargement de page.
 */
export const notifyMenuChanged = () => {
    if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent(MENU_CHANGED_EVENT));
    }
};
