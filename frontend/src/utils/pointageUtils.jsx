import Badge from '@/components/ui/data-display/Badge';

/**
 * Formate un nombre d'heures décimales (ex: 8.5) en chaîne lisible "08h30" ou "8h30".
 * Robuste contre les erreurs d'arrondi flottant (ex: évite 08h60).
 * 
 * @param {number|string} hoursDecimal Valeur décimale d'heures (ex: 8.5, 4.25)
 * @param {boolean} padZero Si true, préfixe les heures à 2 chiffres ("08h30"), sinon "8h30"
 * @returns {string} Heure formatée (ex: "08h30")
 */
export const formatHeuresMinutes = (hoursDecimal, padZero = true) => {
    if (hoursDecimal === null || hoursDecimal === undefined || hoursDecimal === '' || isNaN(hoursDecimal)) {
        return padZero ? '00h00' : '0h00';
    }
    const val = Number(hoursDecimal);
    if (val <= 0) return padZero ? '00h00' : '0h00';

    const totalMinutes = Math.round(val * 60);
    const h = Math.floor(totalMinutes / 60);
    const m = totalMinutes % 60;

    const hStr = padZero ? String(h).padStart(2, '0') : String(h);
    const mStr = String(m).padStart(2, '0');

    return `${hStr}h${mStr}`;
};

/**
 * Formate un montant monétaire en Ariary (ex: 1 418 Ar).
 */
export const formatMonnaie = (val) => {
    if (!val || isNaN(val) || Number(val) === 0) return '0 Ar';
    return new Intl.NumberFormat('fr-FR').format(Math.round(Number(val))) + ' Ar';
};

/**
 * Formate les minutes de retard.
 */
export const formatMinutesRetard = (mins) => {
    const m = Number(mins) || 0;
    if (m <= 0) return '0m';
    return `+${m}m`;
};

/**
 * Mappe un code de statut de pointage (issu de ref_statut_pointage_jour)
 * vers son badge visuel correspondant du Design System.
 * 
 * @param {string} codeStatut Code du statut issu de la BDD
 * @param {number} minutesRetard
 * @returns {JSX.Element} Badge UI prêt à afficher
 */
export const getStatutPointageBadge = (codeStatut, minutesRetard = 0) => {
    if (!codeStatut || codeStatut === 'NON_SAISI') return <Badge variant="neutral">Non saisi</Badge>;

    const upper = codeStatut.toUpperCase();

    // En BDD, le retard est qualifié par le statut PRESENT avec minutes_retard > 0
    if ((upper === 'PRESENT' || upper === 'RETARD') && minutesRetard > 0) {
        return <Badge variant="warning">Retard ({minutesRetard} min)</Badge>;
    }

    switch (upper) {
        case 'PRESENT':
            return <Badge variant="success">Présent</Badge>;
        case 'ANOMALIE_SAISIE':
            return <Badge variant="warning">Incomplet</Badge>;
        case 'ABSENCE_NON_JUSTIFIEE':
        case 'ABSENT':
            return <Badge variant="danger">Absent</Badge>;
        case 'ABSENCE_JUSTIFIEE':
            return <Badge variant="warning">Absence justifiée (non payée)</Badge>;
        case 'CONGE':
            return <Badge variant="sky">Congé / Permission</Badge>;
        case 'DISPENSE_POINTAGE':
            return <Badge variant="info">Autonome</Badge>;
        case 'FERIE_TRAVAILLE':
            return <Badge variant="success">Férié Travaillé</Badge>;
        case 'FERIE_CHOME':
            return <Badge variant="neutral">Férié Chômé</Badge>;
        case 'SAMEDI_TRAVAILLE':
            return <Badge variant="primary">Samedi Travaillé</Badge>;
        case 'DIMANCHE_TRAVAILLE':
            return <Badge variant="primary">Dimanche Travaillé</Badge>;
        case 'REPOS':
            return <Badge variant="neutral">Repos</Badge>;
        case 'NON_ECHU':
            return <Badge variant="neutral">—</Badge>;
        default:
            return <Badge variant="neutral">{codeStatut}</Badge>;
    }
};

/**
 * Détermine si la ligne de pointage doit être visualement grisée (Repos / Férié chômé / Non échu).
 * 
 * @param {string} codeStatut 
 * @returns {boolean}
 */
export const isPointageRowGrayed = (codeStatut) => {
    if (!codeStatut) return false;
    const upper = codeStatut.toUpperCase();
    return upper === 'REPOS' || upper === 'FERIE_CHOME' || upper === 'NON_ECHU';
};

/**
 * Formate une date ISO (YYYY-MM-DD) en format français lisible (JJ/MM/AAAA).
 */
export const formatDate = (dateStr) => {
    if (!dateStr) return '—';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return String(dateStr);
        return d.toLocaleDateString('fr-FR', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric'
        });
    } catch {
        return String(dateStr);
    }
};

/**
 * Filtre les anomalies de pointage selon la catégorie d'incident (Incomplet, Retard, Absence).
 * 
 * @param {Array<object>} anomalies Liste brute des anomalies
 * @param {string} typeFilter 'ALL' | 'INCOMPLET' | 'RETARD' | 'ABSENCE'
 * @returns {Array<object>} Liste filtrée
 */
export const filtrerAnomaliesParType = (anomalies = [], typeFilter = 'ALL') => {
    return anomalies.filter((anom) => {
        if (typeFilter === 'ALL') return true;
        if (typeFilter === 'INCOMPLET') {
            return anom.typeAnomalie === 'INCOMPLET' || anom.statutCode === 'ANOMALIE_SAISIE';
        }
        if (typeFilter === 'RETARD') {
            return anom.typeAnomalie === 'RETARD' || (
                Number(anom.minutesRetard) > 0 &&
                anom.statutCode !== 'ANOMALIE_SAISIE' &&
                anom.statutCode !== 'ABSENCE_NON_JUSTIFIEE'
            );
        }
        if (typeFilter === 'ABSENCE') {
            return anom.typeAnomalie === 'ABSENCE' || anom.statutCode === 'ABSENCE_NON_JUSTIFIEE';
        }
        return true;
    });
};

/**
 * Formate un nombre d'heures décimales au plus proche avec 2 décimales (ex: 8.5 -> "8,50h" ou "8.50h").
 */
export const formatHeuresDecimal = (val, suffix = 'h') => {
    if (val === null || val === undefined || val === '' || isNaN(val)) {
        return `0.00${suffix}`;
    }
    const num = Number(val);
    const rounded = Math.round(num * 100) / 100;
    return `${rounded.toFixed(2)}${suffix}`;
};

/**
 * Formate un nombre d'heures décimales en double affichage : Horaire + Décimal
 * Exemple: 8.5 -> "08h30 (8.50h)"
 */
export const formatDualHours = (val, options = {}) => {
    const { primary = 'decimal', padZero = true } = options;
    if (val === null || val === undefined || val === '' || isNaN(val)) {
        return primary === 'decimal' ? '0.00h (00h00)' : '00h00 (0.00h)';
    }
    const hhmm = formatHeuresMinutes(val, padZero);
    const dec = formatHeuresDecimal(val);
    if (primary === 'decimal') {
        return `${dec} (${hhmm})`;
    }
    return `${hhmm} (${dec})`;
};

/**
 * Résout le libellé human-friendly d'une période pour le nommage des exports (ex: Decembre_2025).
 */
export const getPeriodeLabelForFilename = (libelle, code) => {
    if (libelle && typeof libelle === 'string' && libelle.trim().length > 0 && libelle.toLowerCase() !== 'periode') {
        const clean = libelle.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '')
            .replace(/[^a-zA-Z0-9_-]/g, '_').replace(/_+/g, '_');
        if (clean.length > 0) return clean;
    }
    if (code && typeof code === 'string' && /^\d{4}[-_]\d{2}$/.test(code.trim())) {
        const [year, monthStr] = code.trim().split(/[-_]/);
        const month = parseInt(monthStr, 10);
        const mois = ['Janvier', 'Fevrier', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Aout', 'Septembre', 'Octobre', 'Novembre', 'Decembre'];
        if (month >= 1 && month <= 12) {
            return `${mois[month - 1]}_${year}`;
        }
    }
    if (code && typeof code === 'string') {
        return code.trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9_-]/g, '_');
    }
    return 'Periode';
};
