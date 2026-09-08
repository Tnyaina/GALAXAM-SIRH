/**
 * Utilitaires & Formatteurs de Dates (Timezone-Safe) — SIRH GALAXAM.
 * 
 * Source de Vérité Unique pour le formatage et la manipulation des dates.
 * Remplace et unifie les implémentations dispersées (carriereUtils, paieUtils, congeBusinessRules).
 */

/**
 * Formate une date pour les champs <input type="date" /> (format "YYYY-MM-DD") de manière timezone-safe.
 * Supporte les objets Date, les chaînes ISO avec timezone ("2026-01-01T00:00:00.000+03:00") et les chaînes simples.
 * 
 * @param {string|Date|null|undefined} d
 * @returns {string} Date au format "YYYY-MM-DD" ou "" si vide/invalide
 */
export function fmtDateInput(d) {
    if (!d) return '';
    if (d instanceof Date) {
        if (isNaN(d.getTime())) return '';
        const yyyy = d.getFullYear();
        const mm = String(d.getMonth() + 1).padStart(2, '0');
        const dd = String(d.getDate()).padStart(2, '0');
        return `${yyyy}-${mm}-${dd}`;
    }
    if (typeof d === 'string') {
        const s = d.trim();
        // Cas 1 : Date pure YYYY-MM-DD (préserver la date sans distorsion de fuseau)
        if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;

        // Cas 2 : Timestamp avec fuseau horaire (Z ou offset +/-HH:MM)
        // Ex: "2026-01-31T21:30:00.000Z" -> converti selon le fuseau horaire local (ex: Madagascar UTC+3 -> 01/02)
        if (s.includes('Z') || /[+-]\d{2}(:\d{2})?$/.test(s)) {
            const parsed = new Date(s);
            if (!isNaN(parsed.getTime())) {
                const yyyy = parsed.getFullYear();
                const mm = String(parsed.getMonth() + 1).padStart(2, '0');
                const dd = String(parsed.getDate()).padStart(2, '0');
                return `${yyyy}-${mm}-${dd}`;
            }
        }

        // Cas 3 : Chaîne avec séparateur T ou espace
        if (s.includes('T')) {
            const parts = s.split('T');
            const timePart = parts[1] || '';
            // Si l'heure est non-nulle, parser en Date pour préserver l'horodatage effectif
            if (timePart && !/^00:00(:00(\.000)?)?$/.test(timePart)) {
                const parsed = new Date(s);
                if (!isNaN(parsed.getTime())) {
                    const yyyy = parsed.getFullYear();
                    const mm = String(parsed.getMonth() + 1).padStart(2, '0');
                    const dd = String(parsed.getDate()).padStart(2, '0');
                    return `${yyyy}-${mm}-${dd}`;
                }
            }
            if (/^\d{4}-\d{2}-\d{2}$/.test(parts[0])) return parts[0];
        }
        if (s.includes(' ')) {
            const parts = s.split(' ');
            const timePart = parts[1] || '';
            if (timePart && !/^00:00(:00(\.000)?)?$/.test(timePart)) {
                const parsed = new Date(s);
                if (!isNaN(parsed.getTime())) {
                    const yyyy = parsed.getFullYear();
                    const mm = String(parsed.getMonth() + 1).padStart(2, '0');
                    const dd = String(parsed.getDate()).padStart(2, '0');
                    return `${yyyy}-${mm}-${dd}`;
                }
            }
            if (/^\d{4}-\d{2}-\d{2}$/.test(parts[0])) return parts[0];
        }

        const parsed = new Date(s);
        if (!isNaN(parsed.getTime())) {
            const yyyy = parsed.getFullYear();
            const mm = String(parsed.getMonth() + 1).padStart(2, '0');
            const dd = String(parsed.getDate()).padStart(2, '0');
            return `${yyyy}-${mm}-${dd}`;
        }
        return '';
    }
    return '';
}

/**
 * Alias de fmtDateInput pour compatibilité historique avec les modules congés.
 */
export const formatDateForInput = fmtDateInput;

/**
 * Formate une date ISO ou standard en "DD/MM/YYYY" pour l'affichage UI.
 * 
 * @param {string|Date|null|undefined} d
 * @returns {string} Ex: "15/01/2026" ou "-" si vide
 */
export function fmtDate(d) {
    if (!d) return '-';
    const str = fmtDateInput(d);
    if (!str || str.length < 10) return String(d);
    const [year, month, day] = str.substring(0, 10).split('-');
    if (!year || !month || !day) return String(d);
    return `${day}/${month}/${year}`;
}

/**
 * Formate une date en format français "DD/MM/YYYY" (retourne chaîne vide si nulle).
 * 
 * @param {string|Date|null|undefined} d
 * @returns {string} Ex: "15/01/2026" ou "" si vide
 */
export function fmtDateFr(d) {
    const str = fmtDateInput(d);
    if (!str) return '';
    const [yyyy, mm, dd] = str.substring(0, 10).split('-');
    if (!yyyy || !mm || !dd) return str;
    return `${dd}/${mm}/${yyyy}`;
}

/**
 * Vérifie si une date est strictement antérieure à aujourd'hui (00:00:00).
 * Indique une demande de congé rétroactive ou une régularisation passée.
 * 
 * @param {string|Date|null|undefined} dateStr
 * @returns {boolean}
 */
export function isDateAnterieure(dateStr) {
    if (!dateStr) return false;
    let dDebut;
    if (typeof dateStr === 'string' && /^\d{4}-\d{2}-\d{2}/.test(dateStr)) {
        const [y, m, d] = dateStr.substring(0, 10).split('-').map(Number);
        dDebut = new Date(y, m - 1, d, 0, 0, 0, 0);
    } else {
        dDebut = new Date(dateStr);
    }
    if (isNaN(dDebut.getTime())) return false;
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    return dDebut < today;
}
