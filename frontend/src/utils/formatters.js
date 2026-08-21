/**
 * Formate une date en chaîne relative (ex: "Il y a quelques min", "À l'instant", "Hier", "Il y a 3 jours")
 * @param {string|Date} dateStr - Date à formater
 * @returns {string} - Date formatée en temps relatif
 */
export function fmtRelative(dateStr) {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    const now = new Date();
    const diffMs = now - d;
    const diffMin = Math.floor(diffMs / 60000);

    // Futur (sécurité)
    if (diffMin < 0) {
        return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(d);
    }

    if (diffMin < 1) return "À l'instant";
    if (diffMin < 60) return `Il y a ${diffMin} min`;
    
    const diffH = Math.floor(diffMin / 60);
    if (diffH < 24) return `Il y a ${diffH}h`;
    
    const diffJ = Math.floor(diffH / 24);
    if (diffJ === 1) return 'Hier';
    if (diffJ < 7) return `Il y a ${diffJ} jours`;
    
    return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(d);
}
