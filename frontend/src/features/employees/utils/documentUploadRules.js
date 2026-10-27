const DEFAULT_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png'];
export const MAX_DOCUMENT_SIZE_MB = 50;

export function getAllowedDocumentExtensions(type) {
    const extensions = String(type?.extensionAutorisee || DEFAULT_EXTENSIONS.join(','))
        .split(',')
        .map(extension => extension.trim().replace(/^\./, '').toLowerCase())
        .filter(extension => /^[a-z0-9]+$/.test(extension));
    return extensions.length ? [...new Set(extensions)] : DEFAULT_EXTENSIONS;
}

export function validateDocumentFile(file, type) {
    if (!file) return 'Veuillez choisir un fichier à téléverser.';
    const extensions = getAllowedDocumentExtensions(type);
    const extension = file.name?.split('.').pop()?.toLowerCase();
    if (!extension || !extensions.includes(extension)) {
        return `Format non autorisé. Formats acceptés : ${extensions.map(value => `.${value}`).join(', ')}.`;
    }

    const configuredLimit = Number(type?.tailleMaxMo);
    const limitMb = Number.isFinite(configuredLimit) && configuredLimit > 0
        ? Math.min(configuredLimit, MAX_DOCUMENT_SIZE_MB)
        : 10;
    if (file.size > limitMb * 1024 * 1024) {
        return `Le fichier dépasse la taille maximale autorisée de ${limitMb} Mo.`;
    }
    return '';
}

export function formatDocumentExtensions(type) {
    return getAllowedDocumentExtensions(type).map(extension => `.${extension.toUpperCase()}`).join(', ');
}
