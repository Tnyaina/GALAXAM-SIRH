export function describePointageImportError(error, fallback = 'Le fichier de pointage n’a pas pu être traité.') {
    const detail = typeof error?.response?.data?.message === 'string' && error.response.data.message.trim()
        ? error.response.data.message.trim()
        : typeof error?.message === 'string' && !/^Request failed|^Network Error$/i.test(error.message)
            ? error.message.trim() : fallback;
    const lower = detail.toLocaleLowerCase('fr');
    if (lower.includes('correspond au') && lower.includes('sélection actuelle')) {
        return { title: 'Le fichier ne correspond pas à la sélection', detail, action: 'Téléchargez le modèle du collaborateur et de la période actuellement sélectionnés, puis reportez vos données dans ce modèle.' };
    }
    if (lower.includes('identification du modèle') || lower.includes('feuille pointage') || lower.includes('colonnes fixes') || lower.includes('en-tête du modèle')) {
        return { title: 'Modèle de pointage non reconnu', detail, action: 'Téléchargez un nouveau modèle depuis cette fiche. Conservez ses feuilles et ses colonnes fixes.' };
    }
    if (lower.includes('xlsx') || lower.includes('fichier') || lower.includes('colonnes en double')) {
        return { title: 'Fichier de pointage à corriger', detail, action: 'Vérifiez le format XLSX, la taille du fichier et les colonnes indiquées, puis relancez l’import.' };
    }
    return { title: 'Import de pointage impossible', detail, action: 'Corrigez le point indiqué, puis réessayez. Si le problème persiste, téléchargez un nouveau modèle.' };
}
