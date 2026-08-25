import Badge from '@/components/ui/data-display/Badge';

/**
 * Mappe un code de statut de pointage (issu de ref_statut_pointage_jour)
 * vers son badge visuel correspondant du Design System.
 * 
 * @param {string} codeStatut Code du statut issu de la BDD
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
            return <Badge variant="info">Absence justifiée</Badge>;
        case 'CONGE':
            return <Badge variant="secondary">Congé</Badge>;
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
