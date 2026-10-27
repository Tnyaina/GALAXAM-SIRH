import Badge from '@/components/ui/data-display/Badge';

const STATUS_MAP = {
    // Statuts RH Unifiés (Contrats, Affectations, Workflows)
    'ACTIF': { variant: 'success', label: 'Actif', dot: true },
    'PLANIFIE': { variant: 'sky', label: 'Planifié', dot: true },
    'BROUILLON': { variant: 'default', label: 'Brouillon', dot: false },
    'A_SIGNER': { variant: 'purple', label: 'À signer', dot: true },
    'SUSPENDU': { variant: 'warning', label: 'Suspendu', dot: true },
    'HISTORISE': { variant: 'secondary', label: 'Historisé', dot: false },
    'CLOTURE': { variant: 'secondary', label: 'Clôturé', dot: false },
    'CLOTUREE': { variant: 'secondary', label: 'Clôturée', dot: false },
    'ROMPU': { variant: 'danger', label: 'Rompu', dot: false },
    'ANNULE': { variant: 'secondary', label: 'Annulé', dot: false },
    'ANNULEE': { variant: 'secondary', label: 'Annulée', dot: false },
    'PAYEE': { variant: 'success', label: 'Payée', dot: true },
    'BLOQUE': { variant: 'danger', label: 'Bloqué', dot: true },
    'BLOQUEE': { variant: 'danger', label: 'Bloquée', dot: true },

    // Congés (ref_statut_demande_conge)
    'EN_ATTENTE': { variant: 'warning', label: 'En attente', dot: true },
    'VALIDE': { variant: 'success', label: 'Validé', dot: true },
    'VALIDEE': { variant: 'success', label: 'Validée', dot: true },
    'REFUSE': { variant: 'danger', label: 'Refusé', dot: false },

    // Pointage Jour
    'PRESENT': { variant: 'success', label: 'Présent', dot: true },
    'DEMI_JOURNEE': { variant: 'sky', label: 'Demi-journée', dot: true },
    'CONGE': { variant: 'sky', label: 'Congé', dot: true },
    'FERIE': { variant: 'info', label: 'Férié', dot: false },
    'FERIE_CHOME': { variant: 'default', label: 'Férié Chômé', dot: false },
    'FERIE_TRAVAILLE': { variant: 'success', label: 'Férié Travaillé', dot: true },
    'MISSION': { variant: 'info', label: 'Mission', dot: true },
    'DISPENSE_POINTAGE': { variant: 'info', label: 'Autonome', dot: false },
    'AUTONOME': { variant: 'info', label: 'Autonome', dot: false },
    'ANOMALIE_SAISIE': { variant: 'warning', label: 'Anomalie', dot: true },
    'ABSENCE_NON_JUSTIFIEE': { variant: 'danger', label: 'Absence Injustifiée', dot: true },
    'ABSENCE_JUSTIFIEE': { variant: 'warning', label: 'Absence Justifiée', dot: true },
    'RETARD_NON_JUSTIFIE': { variant: 'warning', label: 'Retard', dot: true },
    'RETARD': { variant: 'warning', label: 'Retard', dot: true },
    'SAMEDI_TRAVAILLE': { variant: 'sky', label: 'Samedi Travaillé', dot: false },
    'DIMANCHE_TRAVAILLE': { variant: 'purple', label: 'Dimanche Travaillé', dot: false },
    'NON_SAISI': { variant: 'default', label: 'Non saisi', dot: false },
    'REPOS': { variant: 'default', label: 'Repos', dot: false },

    // Périodes de Paie
    'EN_COURS': { variant: 'info', label: 'En cours', dot: true },
    'A_VALIDER': { variant: 'warning', label: 'À valider', dot: true },
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
