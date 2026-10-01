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
export function shiftIsoDate(value, days) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(value || '')) return '';
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(Date.UTC(year, month - 1, day + days));
    return date.toISOString().slice(0, 10);
}

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

        // Cas 4 : Format français DD/MM/YYYY ou D/M/YYYY
        const frMatch = s.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})$/);
        if (frMatch) {
            const day = frMatch[1].padStart(2, '0');
            const month = frMatch[2].padStart(2, '0');
            const year = frMatch[3];
            return `${year}-${month}-${day}`;
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
 * Calcule l'âge révolu pour une date civile, sans décalage de fuseau horaire.
 */
export function calculateAge(dateNaissance, referenceDate = new Date()) {
    const birthDate = fmtDateInput(dateNaissance);
    const reference = fmtDateInput(referenceDate);
    if (!birthDate || !reference) return null;
    const [birthYear, birthMonth, birthDay] = birthDate.split('-').map(Number);
    const [referenceYear, referenceMonth, referenceDay] = reference.split('-').map(Number);
    let age = referenceYear - birthYear;
    if (referenceMonth < birthMonth || (referenceMonth === birthMonth && referenceDay < birthDay)) age--;
    return age >= 0 ? age : null;
}

/**
 * Formate une date/heure en temps relatif pour les notifications et journaux UI.
 */
export function fmtRelative(dateStr) {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    if (isNaN(date.getTime())) return '';
    const diffMinutes = Math.floor((new Date() - date) / 60000);
    if (diffMinutes < 0) return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
    if (diffMinutes < 1) return "À l'instant";
    if (diffMinutes < 60) return `Il y a ${diffMinutes} min`;
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) return `Il y a ${diffHours}h`;
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays === 1) return 'Hier';
    if (diffDays < 7) return `Il y a ${diffDays} jours`;
    return new Intl.DateTimeFormat('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' }).format(date);
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

/**
 * Retourne la date courante sous forme "YYYY-MM-DD" de manière timezone-safe (fuseau local du navigateur).
 * Évite le bug classique de new Date().toISOString() qui renvoie la veille avant 03h00 du matin à Madagascar (UTC+3).
 * 
 * @returns {string} Ex: "2026-09-09"
 */
export function getTodayDateString() {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const dd = String(now.getDate()).padStart(2, '0');
    return `${yyyy}-${mm}-${dd}`;
}

/**
 * Vérifie si une date (YYYY-MM-DD ou Date) est strictement dans le futur par rapport à aujourd'hui.
 * 
 * @param {string|Date|null|undefined} dateStr
 * @returns {boolean}
 */
export function isDateInFuture(dateStr) {
    if (!dateStr) return false;
    const str = fmtDateInput(dateStr);
    if (!str || str.length < 10) return false;
    return str.substring(0, 10) > getTodayDateString();
}

/**
 * Vérifie si une date correspond exactement à aujourd'hui.
 * 
 * @param {string|Date|null|undefined} dateStr
 * @returns {boolean}
 */
export function isDateToday(dateStr) {
    if (!dateStr) return false;
    const str = fmtDateInput(dateStr);
    if (!str || str.length < 10) return false;
    return str.substring(0, 10) === getTodayDateString();
}

/**
 * Vérifie si une date est strictement antérieure à aujourd'hui.
 * 
 * @param {string|Date|null|undefined} dateStr
 * @returns {boolean}
 */
export function isDateInPast(dateStr) {
    if (!dateStr) return false;
    const str = fmtDateInput(dateStr);
    if (!str || str.length < 10) return false;
    return str.substring(0, 10) < getTodayDateString();
}

/**
 * Vérifie si une semaine entière (définie par dateDebut) commence strictement dans le futur.
 * 
 * @param {string} dateDebutSemaine 
 * @returns {boolean}
 */
export function isWeekInFuture(dateDebutSemaine) {
    if (!dateDebutSemaine) return false;
    return isDateInFuture(dateDebutSemaine);
}
