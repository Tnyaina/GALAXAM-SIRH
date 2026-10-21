export const API_MISMATCH_MESSAGE =
    "Le serveur a renvoyé une page Web au lieu de l’API GALAXAM. Vérifiez l’adresse du serveur et le routage /api/.";

export function isHtmlApiResponse(data, contentType = '') {
    if (/\btext\/html\b/i.test(contentType)) return true;
    if (typeof data !== 'string') return false;
    return /^\s*(?:<!doctype\s+html|<html\b)/i.test(data);
}

export function isGalaxamHealthResponse(response) {
    const contentType = response?.headers?.['content-type'] || '';
    return response?.status === 200
        && !isHtmlApiResponse(response.data, contentType)
        && response.data?.status === 'UP';
}
