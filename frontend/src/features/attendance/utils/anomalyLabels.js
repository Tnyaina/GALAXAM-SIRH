const ABSENCE_LABELS = {
    ABSENCE_JUSTIFIEE: 'Absence justifiée',
    ABSENCE_NON_JUSTIFIEE: 'Absence non justifiée',
};

export function getAnomalyTypeLabel(anomaly) {
    if (anomaly?.typeAnomalie !== 'ABSENCE') {
        if (anomaly?.typeAnomalie === 'INCOMPLET') return 'Pointage incomplet';
        if (anomaly?.typeAnomalie === 'RETARD') return `Retard (${anomaly.minutesRetard} min)`;
        return anomaly?.typeAnomalie || '';
    }

    if (anomaly.demandeEnAttenteLibelle) {
        return `Absence (${anomaly.demandeEnAttenteLibelle} en attente)`;
    }

    return anomaly.statutLibelle || ABSENCE_LABELS[anomaly.statutCode] || 'Absence';
}
