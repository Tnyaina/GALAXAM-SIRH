import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';

/** Présentation commune des codes de statut utilisés par les vues de pointage. */
export const ATTENDANCE_STATUS = {
    PRESENT: { label: 'Présent', variant: 'success', icon: CheckCircle2, dotColor: 'bg-[var(--color-success-500)]', textColor: 'text-[var(--color-success-700)] dark:text-[var(--color-success-400)]', bgHover: 'hover:bg-[var(--color-success-500)]/10' },
    DISPENSE_POINTAGE: { label: 'Autonome', variant: 'purple', icon: CheckCircle2, dotColor: 'bg-[var(--color-purple-500)]', textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]', bgHover: 'hover:bg-[var(--color-purple-500)]/10' },
    AUTONOME: { label: 'Autonome', variant: 'purple', icon: CheckCircle2, dotColor: 'bg-[var(--color-purple-500)]', textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]', bgHover: 'hover:bg-[var(--color-purple-500)]/10' },
    ABSENCE_JUSTIFIEE: { label: 'Absence justifiée', variant: 'warning', icon: AlertTriangle, dotColor: 'bg-[var(--color-warning-500)]', textColor: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]', bgHover: 'hover:bg-[var(--color-warning-500)]/10', requiresMotif: true },
    ABSENCE_NON_JUSTIFIEE: { label: 'Absent', variant: 'danger', icon: XCircle, dotColor: 'bg-[var(--color-danger-500)]', textColor: 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]', bgHover: 'hover:bg-[var(--color-danger-500)]/10' },
    ABSENT: { label: 'Absent', variant: 'danger' },
    CONGE: { label: 'Congé', variant: 'info', icon: CheckCircle2, dotColor: 'bg-[var(--color-sky-500)]', textColor: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]', bgHover: 'hover:bg-[var(--color-sky-500)]/10' },
    CONGE_PAYE: { label: 'Congé', variant: 'info', icon: CheckCircle2, dotColor: 'bg-[var(--color-sky-500)]', textColor: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]', bgHover: 'hover:bg-[var(--color-sky-500)]/10' },
    FERIE_CHOME: { label: 'Férié chômé', variant: 'neutral', icon: CheckCircle2, dotColor: 'bg-[var(--color-purple-500)]', textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]', bgHover: 'hover:bg-[var(--color-bg-subtle)]' },
    FERIE_TRAVAILLE: { label: 'Férié +100%', variant: 'purple', icon: CheckCircle2, dotColor: 'bg-[var(--color-purple-500)]', textColor: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]', bgHover: 'hover:bg-[var(--color-purple-500)]/10' },
    REPOS: { label: 'Repos', variant: 'neutral', icon: CheckCircle2, dotColor: 'bg-[var(--color-fg-subtle)]', textColor: 'text-[var(--color-fg-muted)]', bgHover: 'hover:bg-[var(--color-bg-subtle)]' },
    NON_SAISI: { label: 'Non saisi', variant: 'neutral', icon: AlertTriangle, dotColor: 'bg-[var(--color-fg-subtle)]', textColor: 'text-[var(--color-fg-muted)]', bgHover: 'hover:bg-[var(--color-bg-subtle)]' },
    NON_ECHU: { label: 'Non échu', variant: 'neutral' },
    PLANIFIE: { label: 'Planifié', variant: 'neutral' },
    ANOMALIE_SAISIE: { label: 'Anomalie de pointage', variant: 'warning', icon: AlertTriangle, dotColor: 'bg-[var(--color-warning-500)]', textColor: 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]', bgHover: 'hover:bg-[var(--color-warning-500)]/10' },
    RETARD: { label: 'Retard', variant: 'warning' },
    DEMI_JOURNEE: { label: 'Demi-journée', variant: 'sky', icon: CheckCircle2, dotColor: 'bg-[var(--color-sky-500)]', textColor: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]', bgHover: 'hover:bg-[var(--color-sky-500)]/10', requiresMotif: true },
    SAMEDI_TRAVAILLE: { label: 'Samedi travaillé', variant: 'primary' },
    DIMANCHE_TRAVAILLE: { label: 'Dimanche travaillé', variant: 'primary' }
};

export const getAttendanceStatus = (code) => ATTENDANCE_STATUS[String(code || 'NON_SAISI').toUpperCase()] || {
    label: String(code || 'Non saisi'), variant: 'neutral'
};

export const isAttendancePresent = (row) => Boolean(row?.present || row?.dispensePointage || row?.ferieTravaille || row?.samediTravaille || row?.dimancheTravaille);
export const isAttendanceLeave = (row) => Boolean(row?.conge || row?.absenceJustifiee);
export const isAttendanceUnentered = (row) => Boolean(row?.nonSaisi || row?.codeStatut === 'NON_SAISI' || !row?.codeStatut);
export const isAttendanceLate = (row) => Boolean(row?.retard || Number(row?.minutesRetard) > 0);
export const isAttendanceUnjustifiedAbsence = (row) => Boolean(row?.absenceNonJustifiee
    || ['ABSENT', 'ABSENCE_NON_JUSTIFIEE'].includes(String(row?.statut || row?.codeStatut || '').toUpperCase()));

export function countAttendanceStatuses(rows = []) {
    const list = Array.isArray(rows) ? rows : [];
    return {
        ALL: list.length,
        PRESENT: list.filter(isAttendancePresent).length,
        NON_SAISI: list.filter(isAttendanceUnentered).length,
        RETARD: list.filter(isAttendanceLate).length,
        CONGE: list.filter(isAttendanceLeave).length
    };
}
