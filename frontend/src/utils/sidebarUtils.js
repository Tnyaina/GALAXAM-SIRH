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
 * Évalue l'adéquation d'un item de menu (feuille) avec la localisation courante.
 *
 * Priorité conceptuelle requise :
 * 1. Href exact + query exacte (Tier 1)
 * 2. Pathname exact (Tier 2 : sans query, Tier 3 : candidat query avec rang par défaut, Tier 4 : query additionnelle dans l'URL)
 * 3. Parent pathname le plus spécifique (Tier 5 : sous-chemin hiérarchique, le plus long gagne, départagé par rang)
 *
 * Aucune chaîne métier en dur (zéro 'campagne', 'saisie', 'soldes', 'paie', etc.).
 */
export const evaluateMatch = (item, location) => {
    if (!item?.href || item.href === '#') return null;

    const [targetBasePath, targetQueryString] = item.href.split('?');
    const targetBase = normalizePath(targetBasePath);
    const currentBase = normalizePath(location?.pathname);

    const currentSearch = location?.search || '';
    const currentParams = new URLSearchParams(currentSearch);
    const targetParams = new URLSearchParams(targetQueryString || '');

    const hasTargetQuery = Boolean(targetQueryString && targetParams.toString());
    const hasCurrentQuery = Boolean(currentSearch && currentParams.toString());

    // Vérification de la correspondance des query params du target
    let queryMatches = false;
    let queryMatchCount = 0;
    if (hasTargetQuery && hasCurrentQuery) {
        let allMatch = true;
        for (const [k, v] of targetParams.entries()) {
            if (currentParams.get(k) !== v) {
                allMatch = false;
                break;
            }
            queryMatchCount++;
        }
        queryMatches = allMatch;
    }

    const rang = Number.isFinite(item.rang) ? item.rang : 999;

    // 1. Correspondance exacte sur le chemin de base
    if (targetBase === currentBase) {
        // 1.1 Href exact avec query string qui correspond
        if (hasTargetQuery && queryMatches) {
            return {
                tier: 1, // EXACT_HREF_AND_QUERY
                queryMatchCount,
                pathLength: targetBase.length,
                rang,
                idMenu: item.idMenu,
            };
        }

        // 1.2 Pathname exact et ni l'un ni l'autre n'a de query string
        if (!hasTargetQuery && !hasCurrentQuery) {
            return {
                tier: 2, // EXACT_PATHNAME_NO_QUERY
                queryMatchCount: 0,
                pathLength: targetBase.length,
                rang,
                idMenu: item.idMenu,
            };
        }

        // 1.3 Pathname exact : l'URL n'a pas de query string, mais ce sous-menu en a un
        //     -> Candidat pour le sous-menu par défaut de cette page (départagé par son rang)
        if (hasTargetQuery && !hasCurrentQuery) {
            return {
                tier: 3, // DEFAULT_CHILD_ON_BASE
                queryMatchCount: 0,
                pathLength: targetBase.length,
                rang,
                idMenu: item.idMenu,
            };
        }

        // 1.4 Pathname exact : le sous-menu n'a pas de query string, mais l'URL en a une additionnelle
        if (!hasTargetQuery && hasCurrentQuery) {
            return {
                tier: 4, // EXACT_BASE_EXTRA_URL_QUERY
                queryMatchCount: 0,
                pathLength: targetBase.length,
                rang,
                idMenu: item.idMenu,
            };
        }

        return null;
    }

    // 2. Correspondance hiérarchique de sous-chemin (ex: currentBase="/employes/42", targetBase="/employes")
    if (targetBase !== '/' && currentBase.startsWith(targetBase + '/')) {
        return {
            tier: 5, // PARENT_PATH_MATCH
            queryMatchCount: (hasTargetQuery && queryMatches) ? queryMatchCount : 0,
            pathLength: targetBase.length,
            rang,
            idMenu: item.idMenu,
        };
    }

    return null;
};

/**
 * Compare deux résultats d'adéquation pour ordonner strictement le meilleur candidat.
 * Retourne < 0 si a est meilleur que b, > 0 si b est meilleur que a, 0 si équivalents.
 */
export const compareMatches = (a, b) => {
    if (!a && !b) return 0;
    if (!a) return 1;
    if (!b) return -1;

    // 1. Priorité au niveau (tier le plus petit en premier)
    if (a.tier !== b.tier) {
        return a.tier - b.tier;
    }

    // 2. Pour Tier 1 (EXACT_HREF_AND_QUERY) : le plus grand nombre de paramètres concordants gagne
    if (a.tier === 1 && a.queryMatchCount !== b.queryMatchCount) {
        return b.queryMatchCount - a.queryMatchCount;
    }

    // 3. Pour Tier 5 (PARENT_PATH_MATCH) : le chemin parent le plus spécifique (le plus long) gagne
    if (a.tier === 5 && a.pathLength !== b.pathLength) {
        return b.pathLength - a.pathLength;
    }

    // 4. Départage déterministe par rang (rang 1, 2, 3...)
    if (a.rang !== b.rang) {
        return a.rang - b.rang;
    }

    return 0;
};

/**
 * Calcule un score numérique dérivé de evaluateMatch pour compatibilité rétroactive.
 */
export const computeMatchScore = (href, location, rang = 999) => {
    const match = evaluateMatch({ href, rang }, location);
    if (!match) return -1;
    switch (match.tier) {
        case 1:
            return 1000 + match.queryMatchCount * 10;
        case 2:
            return 900;
        case 3:
            return 800 - Math.min(match.rang, 100);
        case 4:
            return 700;
        case 5:
            return 100 + match.pathLength - (match.rang * 0.01);
        default:
            return -1;
    }
};

/**
 * Trouve l'ID du menu feuille UNIQUE qui correspond le mieux à l'URL courante.
 */
export const findActiveLeafId = (menu = [], location) => {
    const leaves = getLeafNodes(menu);
    let bestMatch = null;
    let bestId = null;

    for (const leaf of leaves) {
        const match = evaluateMatch(leaf, location);
        if (match && compareMatches(match, bestMatch) < 0) {
            bestMatch = match;
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
