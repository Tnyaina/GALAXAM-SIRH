import Badge from '@/components/ui/data-display/Badge';

const STATUS_MAP = {
    // Statuts RH Unifiés (Contrats, Affectations, Workflows)
    'ACTIF': { variant: 'success', label: 'Actif', dot: true },
    'PLANIFIE': { variant: 'info', label: 'Planifié', dot: true },
    'BROUILLON': { variant: 'warning', label: 'Brouillon', dot: true },
    'A_SIGNER': { variant: 'info', label: 'À signer', dot: true },
    'SUSPENDU': { variant: 'warning', label: 'Suspendu', dot: true },
    'HISTORISE': { variant: 'secondary', label: 'Historisé', dot: false },
    'CLOTURE': { variant: 'secondary', label: 'Clôturé', dot: false },
    'ROMPU': { variant: 'danger', label: 'Rompu', dot: false },
    'ANNULE': { variant: 'secondary', label: 'Annulé', dot: false },

    // Congés (ref_statut_demande_conge)
    'EN_ATTENTE': { variant: 'warning', label: 'En attente', dot: true },
    'VALIDE': { variant: 'success', label: 'Validé', dot: true },
    'REFUSE': { variant: 'danger', label: 'Refusé', dot: false },

    // Pointage Jour
    'PRESENT': { variant: 'success', label: 'Présent', dot: true },
    'CONGE': { variant: 'info', label: 'Congé', dot: true },
    'FERIE': { variant: 'info', label: 'Férié', dot: false },
    'MISSION': { variant: 'info', label: 'Mission', dot: true },
    'ANOMALIE_SAISIE': { variant: 'danger', label: 'Anomalie', dot: true },
    'ABSENCE_NON_JUSTIFIEE': { variant: 'danger', label: 'Absence Injustifiée', dot: true },
    'RETARD_NON_JUSTIFIE': { variant: 'warning', label: 'Retard', dot: true },
    'NON_SAISI': { variant: 'default', label: 'Non saisi', dot: false },

    // Périodes de Paie
    'EN_COURS': { variant: 'primary', label: 'En cours', dot: true },
    'A_VALIDER': { variant: 'warning', label: 'À valider', dot: true },
    'VALIDEE': { variant: 'success', label: 'Validée (Gelée)', dot: true },
    'CLOTUREE': { variant: 'secondary', label: 'Clôturée', dot: false },
    'ARCHIVEE': { variant: 'default', label: 'Archivée', dot: false },

    // Statuts Booléens
    'TRUE': { variant: 'success', label: 'Actif', dot: true },
    'FALSE': { variant: 'danger', label: 'Inactif', dot: false }
};

export default function StatusBadge({
    status,
    label,
    size = 'sm',
    className = ''
}) {
    if (!status && !label) return null;

    const normalizedKey = String(status || label).trim().toUpperCase();
    const config = STATUS_MAP[normalizedKey] || {
        variant: 'default',
        label: label || status,
        dot: false
    };

    return (
        <Badge
            variant={config.variant}
            size={size}
            dot={config.dot}
            className={className}
        >
            {label || config.label}
        </Badge>
    );
}
