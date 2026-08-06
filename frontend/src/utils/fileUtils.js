/**
 * Construit l'URL complète d'une photo / fichier uploadé.
 * S'adapte automatiquement à l'environnement (Local vs Production Render).
 *
 * @param {string} url - Chemin relatif ou URL absolue de la photo
 * @returns {string|null} URL absolue utilisable dans une balise <img src="..." />
 */
export const getFullPhotoUrl = (url) => {
    if (!url) return null;
    if (url.startsWith('http://') || url.startsWith('https://')) return url;

    // Récupération de l'URL d'API configurée (Vite env)
    const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/';

    let domain;
    try {
        const parsed = new URL(apiBaseUrl);
        domain = parsed.origin;
    } catch {
        domain = window.location.origin;
    }

    const cleanPath = url.startsWith('/api') ? url : `/api${url.startsWith('/') ? '' : '/'}${url}`;
    return `${domain}${cleanPath}`;
};
