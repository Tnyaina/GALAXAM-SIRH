/**
 * Utilitaires pour la Sidebar et la résolution de l'état actif unique.
 *
 * Règle Sprint 2B :
 * À un instant donné, UNE SEULE entrée terminale (feuille) est active dans la Sidebar.
 * Une section dépliée (parent) n'est JAMAIS marquée comme page active.
 */

export const normalizePath = (p) => {
    return (p || '').split('?')[0].replace(/\/+$/, '') || '/';
};

/**
 * Collecte récursivement tous les nœuds feuilles (sans sous-menus) qui possèdent un href valide.
 */
export const getLeafNodes = (items = []) => {
    let leaves = [];
    for (const item of items) {
        if (!item.sousMenus || item.sousMenus.length === 0) {
            if (item.href && item.href !== '#') {
                leaves.push(item);
            }
        } else {
            leaves = leaves.concat(getLeafNodes(item.sousMenus));
        }
    }
    return leaves;
};

/**
 * Calcule un score d'adéquation entre un href de menu et la localisation actuelle.
 * Retourne -1 si non-correspondant.
 */
export const computeMatchScore = (href, location) => {
    if (!href || href === '#') return -1;

    const [targetBasePath, targetQueryString] = href.split('?');
    const targetBase = normalizePath(targetBasePath);
    const currentBase = normalizePath(location.pathname);

    // 1. Correspondance exacte sur le chemin de base
    if (targetBase === currentBase) {
        if (targetQueryString) {
            const targetParams = new URLSearchParams(targetQueryString);
            const currentParams = new URLSearchParams(location.search);
            let matches = true;
            let count = 0;
            for (const [k, v] of targetParams.entries()) {
                if (currentParams.get(k) !== v) {
                    matches = false;
                    break;
                }
                count++;
            }
            if (matches) return (1000 + count * 10);

            // Si l'URL n'a aucun query param mais que ce sous-menu représente l'onglet par défaut de la page
            if (!location.search && ['campagne', 'saisie', 'soldes'].includes(targetParams.get('tab'))) {
                return 850;
            }

            return -1;
        } else {
            // Le lien menu n'impose pas de query params
            if (!location.search) {
                // Correspondance exacte pure
                return 900;
            } else {
                // Même base, l'URL a des query params mais ce lien n'en a pas
                return 700;
            }
        }
    }

    // 2. Correspondance de sous-route préfixe (ex: currentBase="/employes/42", targetBase="/employes")
    if (targetBase !== '/' && currentBase.startsWith(targetBase + '/')) {
        let score = 100 + targetBase.length;
        if (targetQueryString) {
            const targetParams = new URLSearchParams(targetQueryString);
            const tabVal = targetParams.get('tab');
            if (tabVal) {
                if (currentBase.endsWith('/' + tabVal) || (tabVal === 'exports' && currentBase.endsWith('/sorties'))) {
                    score += 50; // Bonus pour étape spécifique (ex: /paie/.../variables ou /sorties)
                } else if (tabVal === 'campagne') {
                    score += 20; // Préférer "Campagne" pour les étapes générales du stepper
                }
            }
        }
        return score;
    }

    return -1;
};

/**
 * Trouve l'ID du menu feuille UNIQUE qui correspond le mieux à l'URL courante.
 */
export const findActiveLeafId = (menu = [], location) => {
    const leaves = getLeafNodes(menu);
    let bestId = null;
    let bestScore = -1;

    for (const leaf of leaves) {
        const score = computeMatchScore(leaf.href, location);
        if (score > bestScore) {
            bestScore = score;
            bestId = leaf.idMenu;
        }
    }

    return bestId;
};

/**
 * Vérifie si un nœud parent contient la feuille active parmi ses descendants.
 */
export const hasActiveDescendant = (node, activeLeafId) => {
    if (!node.sousMenus || !activeLeafId) return false;
    return node.sousMenus.some(
        (child) => child.idMenu === activeLeafId || hasActiveDescendant(child, activeLeafId)
    );
};
