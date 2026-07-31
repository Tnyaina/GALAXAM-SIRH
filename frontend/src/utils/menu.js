/**
 * Cherche le libellé du menu correspondant à un href donné, en descendant
 * récursivement dans l'arbre (sousMenus). Utilisé pour afficher le titre
 * de page dans le Topbar sans map statique.
 */
export function findMenuTitleByHref(menuTree, href) {
    if (!Array.isArray(menuTree)) return null;

    for (const item of menuTree) {
        if (item.href === href) return item.libelle;

        if (item.sousMenus?.length) {
            const found = findMenuTitleByHref(item.sousMenus, href);
            if (found) return found;
        }
    }

    return null;
}
