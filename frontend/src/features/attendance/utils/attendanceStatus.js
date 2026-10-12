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
    ANOMALIE_SAISIE: { label: 'Incomplet', variant: 'warning' },
    RETARD: { label: 'Retard', variant: 'warning' },
    DEMI_JOURNEE: { label: 'Demi-journée', variant: 'sky' },
    SAMEDI_TRAVAILLE: { label: 'Samedi travaillé', variant: 'primary' },
    DIMANCHE_TRAVAILLE: { label: 'Dimanche travaillé', variant: 'primary' }
};

export const getAttendanceStatus = (code) => ATTENDANCE_STATUS[String(code || 'NON_SAISI').toUpperCase()] || {
    label: String(code || 'Non saisi'), variant: 'neutral'
};

export const getDefaultAttendanceStatusActions = (autonomous = false) => autonomous ? [
    { code: 'DISPENSE_POINTAGE', label: 'Autonome (8h présumé)', description: 'Présomption légale de travail 8h' },
    { code: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée', description: 'Absence autorisée avec motif', requiresMotif: true },
    { code: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée', description: 'Absence non justifiée constatée' },
    { code: 'CONGE', label: 'Congé payé', description: 'Imputer sur solde congé payé' }
] : [
    { code: 'PRESENT', label: 'Présent', description: 'Collaborateur présent sur le site' },
    { code: 'ABSENCE_JUSTIFIEE', label: 'Absence justifiée', description: 'Absence autorisée avec motif', requiresMotif: true },
    { code: 'ABSENCE_NON_JUSTIFIEE', label: 'Absence non justifiée', description: 'Absence non justifiée constatée' },
    { code: 'CONGE', label: 'Congé payé', description: 'Imputer sur solde congé payé' }
];

/** Normalise les anciens statuts de fiche sans transformer une absence explicitement enregistrée. */
export function resolveAttendanceStatus({ status, hasHours, weekend, holiday, leave, autonomous }) {
    const code = String(status || '').toUpperCase();
    if (code && code !== 'NON_SAISI' && !(code === 'ABSENT' && !hasHours)) return code;
    if (holiday) return hasHours ? 'FERIE_TRAVAILLE' : 'FERIE_CHOME';
    if (weekend) return hasHours ? 'SAMEDI_TRAVAILLE' : 'REPOS';
    if (leave) return 'CONGE';
    if (autonomous) return 'DISPENSE_POINTAGE';
    return hasHours ? 'PRESENT' : 'NON_SAISI';
}

/** Défaut UI d'indemnité aligné sur la décision renvoyée par le backend. */
export function isAllowanceGrantedByDefault(allowance, { weekend = false, holiday = false, leave = false, excluded = false } = {}) {
    return allowance?.cocheParDefaut !== false && !weekend && !holiday && !leave && !excluded;
}

export const isAttendancePresent = (row) => Boolean(row?.present || row?.dispensePointage || row?.ferieTravaille || row?.samediTravaille || row?.dimancheTravaille);
export const isAttendanceLeave = (row) => Boolean(row?.conge || row?.absenceJustifiee);
export const isAttendanceUnentered = (row) => Boolean(row?.nonSaisi || row?.codeStatut === 'NON_SAISI' || !row?.codeStatut);
export const isAttendanceLate = (row) => Boolean(row?.retard || Number(row?.minutesRetard) > 0);
export const isAttendanceUnjustifiedAbsence = (row) => Boolean(row?.absenceNonJustifiee || row?.codeStatut === 'ABSENCE_NON_JUSTIFIEE');

/** Drapeaux métier d'une ligne, avec priorité aux valeurs explicites de l'API. */
export function deriveAttendanceFlags(row = {}, { isWeekend = false, isSunday = false, isSaturday = false, hasWorked = false } = {}) {
    const statut = String(row.statut || row.codeStatut || '').toUpperCase();
    const isAbsent = Boolean(row.isAbsent ?? (statut === 'ABSENT' || statut === 'ABSENCE_NON_JUSTIFIEE'));
    const isConge = Boolean(row.isConge ?? (statut === 'CONGE' || Boolean(row.typeConge)));
    const isRetard = Boolean(row.isRetard ?? (statut === 'RETARD' || (row.ecart && row.ecart.startsWith('-') && !row.ecartPositif) || Number(row.minutesRetard) > 0));
    const isRepos = Boolean(row.isRepos ?? ((isWeekend && !hasWorked) || statut === 'REPOS'));
    const isDimancheTravaille = Boolean(row.isDimancheTravaille ?? (isSunday && hasWorked));
    const isSamediTravaille = Boolean(row.isSamediTravaille ?? (isSaturday && hasWorked));
    const isPlanifie = Boolean(row.isPlanifie ?? (statut === 'PLANIFIE' || statut === 'NON_ECHU'));
    const isAutonome = Boolean(row.isDispensePointage ?? ((statut === 'AUTONOME' || statut === 'DISPENSE_POINTAGE') && !isWeekend));
    const isPresent = Boolean(row.isPresent ?? ((statut === 'PRESENT' || hasWorked || row.present) && !isRepos && !isDimancheTravaille && !isSamediTravaille));
    const hasAnomalie = Boolean(row.isAnomalie ?? (row.anomalieSaisie || isAbsent || isRetard || statut === 'ANOMALIE'));
    return { isAbsent, isConge, isRetard, isRepos, isDimancheTravaille, isSamediTravaille, isPlanifie, isAutonome, isPresent, hasAnomalie };
}

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
