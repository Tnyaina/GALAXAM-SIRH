import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
    ArrowRight, Check, ChevronDown, ChevronRight,
    FileSpreadsheet, LoaderCircle, SkipForward
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import TimeInput from '@/components/ui/forms/TimeInput';
import Badge from '@/components/ui/data-display/Badge';
import { Table } from '@/components/ui/data-display/Table';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';
import { formatRoundedMoneyAr } from '@/utils/moneyUtils';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { importLineNeedsReview } from '@/features/attendance/utils/pointageImport';
import { toPointageBatchPayload } from '@/features/attendance/utils/pointageBatchPayload';
import { POINTAGE_TIME_FIELDS } from '@/features/attendance/utils/pointageRow';

const TIME_FIELDS = POINTAGE_TIME_FIELDS.map((field, index) => [field, ['Entrée', 'Sortie', 'Retour', 'Fin'][index]]);

// ─── Helpers ────────────────────────────────────────────────────────────────

const fmtAr = (val) => {
    if (val == null || Number.isNaN(Number(val))) return '—';
    if (Number(val) === 0) return '0 Ar';
    return formatRoundedMoneyAr(val);
};

/** Calcule le montant total des indemnités d'une ligne (preview serveur ou estimation montantJour). */
function computeLineIndemnitesTotal(line) {
    const proposed = line.proposed;
    if (!proposed || !line.allowances?.length) return 0;
    let total = 0;
    for (const allowance of line.allowances) {
        const key = String(allowance.idIndemnite);
        const preview = proposed.indemnitesCalculeesPreview?.[key];
        if (preview) {
            total += Number(preview.montantApplique || 0) - Number(preview.deductionRepas || 0);
        } else {
            const sel = proposed.dynamicIndemnites?.[key] || {};
            const fraction = Number(sel.fractionJour || 0) || (sel.accordee ? 1 : 0);
            total += fraction * Number(allowance.montantJour || 0);
        }
    }
    return total;
}

/** Détecte si une date ISO est samedi (6) ou dimanche (0). */
function isWeekendDate(isoDate) {
    const d = new Date(`${isoDate}T12:00:00`);
    const day = d.getDay();
    return day === 0 || day === 6;
}

/** Formate ISO → "Sam.", "Lun.", etc. avec date DD/MM */
function fmtDayLabel(isoDate) {
    const d = new Date(`${isoDate}T12:00:00`);
    const weekday = d.toLocaleDateString('fr-FR', { weekday: 'short' });
    return `${weekday.charAt(0).toUpperCase() + weekday.slice(1)} ${d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}`;
}

/** Jours fériés nationaux à date fixe (Madagascar). */
const FIXED_HOLIDAYS_MG = {
    '01-01': 'Nouvel An',
    '03-08': 'Journée de la Femme',
    '03-29': 'Commémoration 1947',
    '05-01': 'Fête du Travail',
    '06-26': 'Fête Nationale',
    '08-15': 'Assomption',
    '11-01': 'Toussaint',
    '12-25': 'Noël'
};

/**
 * Résout les informations de jour férié d'une ligne d'import :
 * 1) libellé serveur via line.existing?.libelleFerie
 * 2) libellé de la grille contextuelle via row?.libelleFerie
 * 3) calendrier légal malgache (FIXED_HOLIDAYS_MG) pour dates fixes (ex: Assomption le 15 août)
 */
function getLineFerieInfo(line, grilleRows) {
    if (!line) return { isFerie: false, isFerieChome: false, libelleFerie: null };
    const existing = line.existing;
    const row = Array.isArray(grilleRows) ? grilleRows.find(r => r.datePointage === line.date) : null;
    const mmDd = line.date ? line.date.slice(5) : '';
    const fallbackLabel = FIXED_HOLIDAYS_MG[mmDd] || null;
    const libelleFerie = existing?.libelleFerie || row?.libelleFerie || fallbackLabel;

    const isFerie = Boolean(
        libelleFerie ||
        existing?.isFerie ||
        existing?.isFerieChome ||
        existing?.isFerieTravaille ||
        ['FERIE', 'FERIE_CHOME', 'FERIE_TRAVAILLE'].includes(existing?.statut) ||
        row?.ferie ||
        row?.libelleFerie ||
        ['FERIE', 'FERIE_CHOME', 'FERIE_TRAVAILLE'].includes(row?.codeStatut)
    );

    const isFerieChome = Boolean(
        isFerie && (
            existing?.isFerieChome ||
            existing?.statut === 'FERIE_CHOME' ||
            row?.codeStatut === 'FERIE_CHOME' ||
            (existing?.statut !== 'FERIE_TRAVAILLE' && row?.codeStatut !== 'FERIE_TRAVAILLE')
        )
    );

    return {
        isFerie,
        isFerieChome,
        libelleFerie: libelleFerie || (isFerie ? 'Jour férié' : null)
    };
}

// ─── Sub-components ─────────────────────────────────────────────────────────

function FilterTab({ label, count, active, danger, warning, onClick }) {
    const colorClass = active
        ? 'border-b-2 border-[var(--color-brand-600)] text-[var(--color-brand-700)] font-bold bg-[var(--color-brand-50)]'
        : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]';
    return (
        <button
            type="button"
            onClick={onClick}
            className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs transition-colors cursor-pointer ${colorClass}`}
        >
            {label}
            <span className={`inline-flex items-center justify-center rounded-none px-1.5 py-0.5 text-[10px] font-bold min-w-[18px] ${
                active ? 'bg-[var(--color-brand-600)] text-white'
                    : danger ? 'bg-[var(--color-danger-100)] text-[var(--color-danger-700)]'
                        : warning ? 'bg-[var(--color-warning-100)] text-[var(--color-warning-700)]'
                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
            }`}>
                {count}
            </span>
        </button>
    );
}

function ControlBadge({ line, expanded, onToggle }) {
    if (line._previewPending) {
        return <Badge variant="default" size="sm">Calcul…</Badge>;
    }
    if (line._previewError || line.errors?.length || line.proposed?.hasValidationError) {
        return (
            <div className="flex flex-col items-start gap-0.5">
                <Badge variant="danger" size="sm">À CORRIGER</Badge>
                <button type="button" onClick={onToggle} className="text-[10px] text-[var(--color-danger-600)] underline cursor-pointer">
                    {expanded ? 'Masquer' : 'Détails'}
                </button>
            </div>
        );
    }
    if (line.conflict) {
        return (
            <div className="flex flex-col items-start gap-0.5">
                <Badge variant="warning" size="sm">CONFLIT</Badge>
                <button type="button" onClick={onToggle} className="text-[10px] text-[var(--color-warning-700)] underline cursor-pointer">
                    {expanded ? 'Masquer' : 'Détails'}
                </button>
            </div>
        );
    }
    if (line.decision === 'SIGNAL') {
        return <Badge variant="warning" size="sm">À RÉGULARISER</Badge>;
    }
    if (line.decision === 'MISSING') {
        return <Badge variant="default" size="sm">ABSENTE</Badge>;
    }
    if (line.decision === 'IMPORT') {
        return <Badge variant="success" size="sm">PRÊTE</Badge>;
    }
    return <Badge variant="default" size="sm">IGNORÉE</Badge>;
}

function AllowanceEditor({ allowance, value = {}, amountPreview, disabled, onChange }) {
    const fractionable = allowance.fractionnable ?? allowance.estFractionnable;
    const granted = Boolean(value.accordee);
    const inputClass = 'accent-[var(--color-brand-600)] cursor-pointer';

    // Affichage compact : DPLCT · 1 560 Ar ou Repas · 2 000 Ar
    const shortCode = (allowance.code || allowance.libelle || '').replace(/^Indemnité\s+(de\s+)?/i, '').trim();
    const montantJour = Number(allowance.montantJour || 0);

    return (
        <div className="inline-flex flex-col gap-0.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-2 py-1 min-w-[120px]">
            <span className="text-[9px] font-bold text-[var(--color-fg)] truncate" title={allowance.code || allowance.libelle}>
                {shortCode}
                {montantJour > 0 && (
                    <span className="ml-1 font-normal text-[var(--color-fg-muted)]">
                        · {formatRoundedMoneyAr(montantJour)}
                    </span>
                )}
            </span>
            {fractionable ? (
                <div className="flex gap-2 text-[9px]">
                    {[['matin', 'Matin'], ['am', 'Après-midi']].map(([field, label]) => (
                        <label key={field} className={`inline-flex items-center gap-1 ${disabled ? 'opacity-50' : 'cursor-pointer'}`}>
                            <input
                                type="checkbox"
                                className={inputClass}
                                disabled={disabled}
                                checked={Boolean(value[field])}
                                onChange={event => onChange({
                                    ...value,
                                    [field]: event.target.checked,
                                    accordee: field === 'matin' ? event.target.checked || Boolean(value.am) : Boolean(value.matin) || event.target.checked,
                                    fractionJour: ((field === 'matin' ? event.target.checked : Boolean(value.matin)) ? 0.5 : 0) +
                                        ((field === 'am' ? event.target.checked : Boolean(value.am)) ? 0.5 : 0)
                                })}
                            />
                            {label}
                        </label>
                    ))}
                </div>
            ) : (
                <label className={`inline-flex items-center gap-1 text-[9px] font-medium ${disabled ? 'opacity-50' : 'cursor-pointer'}`}>
                    <input
                        type="checkbox"
                        className={inputClass}
                        disabled={disabled}
                        checked={granted}
                        onChange={event => onChange({ ...value, accordee: event.target.checked, fractionJour: event.target.checked ? 1 : 0 })}
                    />
                    Accorder
                </label>
            )}
            {allowance.code === 'REPAS' && montantJour === 0 && granted && (
                <span className="text-[9px] font-semibold text-[var(--color-warning-700)]">Repas fourni · 0 Ar</span>
            )}
            {amountPreview && (
                <span className="text-[9px] font-semibold text-[var(--color-fg-muted)]">
                    {amountPreview.deductionRepas > 0 ? (
                        <>
                            <span className="text-[var(--color-warning-700)]">−{formatRoundedMoneyAr(amountPreview.deductionRepas)}</span>
                            <span> · Net {formatRoundedMoneyAr(amountPreview.montantApplique)}</span>
                        </>
                    ) : <>Appliqué {formatRoundedMoneyAr(amountPreview.montantApplique)}</>}
                </span>
            )}
        </div>
    );
}

function ImportSkeleton() {
    return (
        <div className="space-y-2" aria-label="Chargement de l'aperçu d'import" aria-busy="true">
            <div className="grid grid-cols-4 gap-2 border border-[var(--color-border-subtle)] p-3">
                {[1, 2, 3, 4].map(item => <Skeleton key={item} className="h-9 w-full animate-pulse" />)}
            </div>
            <div className="border border-[var(--color-border-subtle)]">
                <div className="grid grid-cols-6 gap-3 bg-[var(--color-bg-subtle)] p-3">
                    {[1, 2, 3, 4, 5, 6].map(item => <Skeleton key={item} className="h-3 w-full" />)}
                </div>
                {Array.from({ length: 6 }).map((_, index) => (
                    <div key={index} className="grid grid-cols-6 items-center gap-3 border-t border-[var(--color-border-subtle)] p-3">
                        <Skeleton className="h-4 w-4" />
                        <Skeleton className="h-4 w-20" />
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-4 w-16" />
                    </div>
                ))}
            </div>
        </div>
    );
}

// ─── Main export ─────────────────────────────────────────────────────────────

export default function PeriodeEmployeeImportPreview({
    importPreview,
    importPreviewLoading = false,
    importApplying = false,
    importSelections = {},
    setImportSelections,
    setImportPreview,
    grilleRows = [],
    onClose,
    onApply,
    onSave,
    onStartRegularisation,
    onRegularize
}) {
    const [filter, setFilter] = useState('ALL');
    const [collapsedWeeks, setCollapsedWeeks] = useState({});
    const [expanded, setExpanded] = useState({});
    const [approvedConflicts, setApprovedConflicts] = useState({});
    const tableScrollRef = useRef(null);

    const lines = useMemo(() => importPreview?.lines || [], [importPreview]);
    const isRegularisationPhase = importPreview?.phase === 'REGULARIZE';

    // ── Compteurs ─────────────────────────────────────────────────────────────
    const readyCount = useMemo(() =>
        lines.filter(l => l.decision === 'IMPORT' && !l.errors?.length && !l._previewPending && !l._previewError && !l.proposed?.hasValidationError).length,
    [lines]);
    const signalCount = useMemo(() => lines.filter(l => l.decision === 'SIGNAL').length, [lines]);
    const issueCount = useMemo(() =>
        lines.filter(l => l.decision === 'SIGNAL' || l.decision === 'ERROR' || l.decision === 'MISSING' || l._previewError).length,
    [lines]);
    const conflictCount = useMemo(() => lines.filter(l => l.conflict && l.decision === 'IMPORT').length, [lines]);
    const restCount = useMemo(() => lines.filter(l => l.decision === 'IGNORE').length, [lines]);
    const missingCount = useMemo(() => lines.filter(l => l.decision === 'MISSING').length, [lines]);

    const selectedImportCount = useMemo(() =>
        lines.filter(l => importSelections[l.ligne] && l.decision === 'IMPORT' && !l.eventCode).length,
    [lines, importSelections]);
    const selectedSignalCount = useMemo(() =>
        lines.filter(l => importSelections[l.ligne] && (l.decision === 'SIGNAL' || (l.decision === 'IMPORT' && l.eventCode))).length,
    [lines, importSelections]);
    const selectedCount = selectedImportCount + selectedSignalCount;

    const selectedImportNeedsReview = useMemo(() =>
        lines.some(l => importSelections[l.ligne] && l.decision === 'IMPORT' && !l.eventCode && importLineNeedsReview(l, approvedConflicts)),
    [lines, importSelections, approvedConflicts]);
    const selectedNeedsReview = useMemo(() =>
        lines.some(l => importSelections[l.ligne] && importLineNeedsReview(l, approvedConflicts)),
    [lines, importSelections, approvedConflicts]);

    /** Total des indemnités des lignes sélectionnées (IMPORT uniquement) */
    const totalIndemnitesAEnregistrer = useMemo(() =>
        lines.reduce((sum, l) => {
            if (!importSelections[l.ligne] || l.decision !== 'IMPORT') return sum;
            return sum + computeLineIndemnitesTotal(l);
        }, 0),
    [lines, importSelections]);

    // ── Lignes visibles selon filtre ──────────────────────────────────────────
    const visibleLines = useMemo(() => lines.filter(l =>
        isRegularisationPhase || filter === 'ALL'
        || (filter === 'READY' && l.decision === 'IMPORT' && !l.errors?.length)
        || (filter === 'ISSUE' && (l.decision === 'SIGNAL' || l.decision === 'ERROR' || l.decision === 'MISSING' || l._previewError))
        || (filter === 'CONFLICT' && l.conflict)
        || (filter === 'IGNORE' && l.decision === 'IGNORE')
    ), [lines, filter, isRegularisationPhase]);

    // ── Groupement par semaine ────────────────────────────────────────────────
    const groups = useMemo(() => {
        const result = [];
        visibleLines.forEach(line => {
            const date = new Date(`${line.date}T12:00:00`);
            const monday = new Date(date);
            monday.setDate(date.getDate() - ((date.getDay() + 6) % 7));
            const key = monday.toISOString().slice(0, 10);
            let group = result.find(g => g.key === key);
            if (!group) {
                group = { key, lines: [] };
                result.push(group);
            }
            group.lines.push(line);
        });
        return result;
    }, [visibleLines]);

    // ── Preview batch ─────────────────────────────────────────────────────────
    useEffect(() => {
        const pending = lines.filter(l => l._previewPending && l.proposed);
        if (!pending.length) return undefined;
        let cancelled = false;
        const timer = setTimeout(async () => {
            try {
                const results = await attendanceService.daily.previewBatch(
                    pending.map(l => toPointageBatchPayload({ ...l.proposed, inclureIndemnitesCalculeesPreview: true }))
                );
                if (cancelled) return;
                const byId = new Map(pending.map((l, i) => [l.ligne, results?.[i]]));
                setImportPreview(current => current ? ({
                    ...current,
                    lines: current.lines.map(l => {
                        const result = byId.get(l.ligne);
                        if (!result || !l._previewPending || !pending.some(p => p.ligne === l.ligne && p.proposed === l.proposed)) return l;
                        return {
                            ...l, proposed: result.preview || l.proposed, _previewPending: false,
                            _previewError: result.error || (result.preview?.hasValidationError ? result.preview.validationMessages?.[0] : null)
                        };
                    })
                }) : current);
            } catch (error) {
                if (!cancelled) setImportPreview(current => current ? ({
                    ...current,
                    lines: current.lines.map(l =>
                        pending.some(p => p.ligne === l.ligne && p.proposed === l.proposed)
                            ? { ...l, _previewPending: false, _previewError: error?.response?.data?.message || 'Calcul indisponible' } : l)
                }) : current);
            }
        }, 350);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [lines, setImportPreview]);

    // ── Mutations ────────────────────────────────────────────────────────────
    const updateLine = useCallback((ligne, update) => setImportPreview(current => ({
        ...current,
        lines: current.lines.map(item => item.ligne === ligne ? update(item) : item)
    })), [setImportPreview]);

    const toggleAll = (checked) => setImportSelections(Object.fromEntries(
        lines.map(l => [
            l.ligne,
            checked && ['IMPORT', 'SIGNAL'].includes(l.decision) && !l.errors?.length &&
            grilleRows.find(r => r.datePointage === l.date)?.canCorrect !== false
        ])
    ));

    /** Sauter à la prochaine anomalie visible dans le tableau */
    const jumpToNextAnomaly = useCallback(() => {
        const anomalyLines = lines.filter(l =>
            l.decision === 'SIGNAL' || l.decision === 'ERROR' || l.decision === 'MISSING'
            || l._previewError || l.errors?.length || l.proposed?.hasValidationError
        );
        if (!anomalyLines.length) return;
        // Trouver la première anomalie non encore sélectionnée ou la première
        const target = anomalyLines[0];
        const el = tableScrollRef.current?.querySelector(`[data-ligne="${CSS.escape(String(target.ligne))}"]`);
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, [lines]);

    /** Replier tous les week-ends et fériés non travaillés */
    const collapseWeekends = useCallback(() => {
        const newCollapsed = {};
        groups.forEach(g => {
            const allRest = g.lines.every(l => {
                const ferieInfo = getLineFerieInfo(l, grilleRows);
                return (isWeekendDate(l.date) || ferieInfo.isFerie) && l.decision === 'IGNORE';
            });
            if (allRest) newCollapsed[g.key] = true;
        });
        setCollapsedWeeks(newCollapsed);
    }, [groups, grilleRows]);

    const toggleWeek = (key) => setCollapsedWeeks(prev => ({ ...prev, [key]: !prev[key] }));

    // ── Semaine : totaux ──────────────────────────────────────────────────────
    function weekSummary(groupLines) {
        const pretes = groupLines.filter(l => l.decision === 'IMPORT' && !l.errors?.length && !l._previewPending && !l._previewError && !l.proposed?.hasValidationError).length;
        const aRegul = groupLines.filter(l => l.decision === 'SIGNAL').length;
        const totalIndemnites = groupLines.reduce((sum, l) => sum + computeLineIndemnitesTotal(l), 0);
        return { pretes, aRegul, totalIndemnites };
    }

    // ─── Rendu ─────────────────────────────────────────────────────────────────
    return (
        <Modal
            isOpen={Boolean(importPreview || importPreviewLoading)}
            onClose={importPreviewLoading ? undefined : onClose}
            title={importPreviewLoading ? "Lecture du fichier d'import"
                : isRegularisationPhase
                    ? `Signalements à régulariser · ${lines.length}`
                    : `Aperçu de l'import · ${lines.length} journées`}
            icon={importPreviewLoading ? <LoaderCircle size={17} className="animate-spin" /> : <FileSpreadsheet size={17} />}
            size="full"
            closeOnBackdrop={!importPreviewLoading}
            closeOnEscape={!importPreviewLoading}
            className={isRegularisationPhase ? '!max-w-[72vw] w-[72vw] max-h-[88vh]' : '!max-w-[92vw] w-[92vw] max-h-[92vh]'}
        >
            {importPreviewLoading ? (
                <div className="space-y-4">
                    <div className="border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 p-4">
                        <div className="flex items-center gap-3 text-sm font-semibold text-[var(--color-fg)]">
                            <LoaderCircle size={18} className="animate-spin text-[var(--color-brand-600)]" />
                            Analyse du fichier et préparation des journées…
                        </div>
                        <p className="mt-1 pl-7 text-xs text-[var(--color-fg-muted)]">
                            Les données restent en local jusqu'à leur chargement dans la grille.
                        </p>
                    </div>
                    <ImportSkeleton />
                </div>
            ) : (
                <div className="flex flex-col gap-0 h-full">

                    {/* Phase régularisation : bandeau */}
                    {isRegularisationPhase && (
                        <div className="mb-3 border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 p-3 text-xs text-[var(--color-fg)]">
                            {importPreview?.savedCount ? `${importPreview.savedCount} pointage(s) enregistré(s). ` : ''}
                            Traitez maintenant les congés et absences détectés. Chaque régularisation est enregistrée immédiatement ; l'aperçu se fermera après la dernière.
                        </div>
                    )}

                    {/* ── Bandeau collaborateur ─────────────────────────────── */}
                    <div className="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border)] bg-[var(--color-surface)] px-4 py-2.5 mb-0">
                        <div className="flex items-center gap-3 min-w-0">
                            <div className="w-8 h-8 bg-[var(--color-brand-600)] text-white font-bold text-xs flex items-center justify-center shrink-0">
                                {((importPreview?.employeNom || importPreview?.nomEmploye || '')[0] || 'A').toUpperCase()}
                            </div>
                            <div className="min-w-0">
                                <div className="font-bold text-sm text-[var(--color-fg)] truncate uppercase tracking-wide">
                                    {importPreview?.employeNom || importPreview?.nomEmploye || 'Collaborateur'}
                                </div>
                                <div className="text-[11px] text-[var(--color-fg-muted)]">
                                    Vérifiez les heures et indemnités. Les modifications sont recalculées avant l'enregistrement.
                                    {importPreview?.dateDebut && (
                                        <span className="hidden sm:inline"> · {importPreview.dateDebut} → {importPreview.dateFin}</span>
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* Actions rapides à droite du bandeau */}
                        {!isRegularisationPhase && (
                            <div className="flex items-center gap-3 shrink-0">
                                {/* Tout sélectionner */}
                                <label className="inline-flex items-center gap-1.5 text-xs font-semibold cursor-pointer select-none">
                                    <input
                                        type="checkbox"
                                        checked={lines.length > 0 && lines.filter(l => ['IMPORT', 'SIGNAL'].includes(l.decision) && !l.errors?.length).every(l => importSelections[l.ligne])}
                                        onChange={e => toggleAll(e.target.checked)}
                                        className="accent-[var(--color-brand-600)]"
                                    />
                                    Tout sélectionner
                                </label>

                                <div className="h-4 w-px bg-[var(--color-border)]" />

                                {/* Replier week-ends et fériés */}
                                <button
                                    type="button"
                                    onClick={collapseWeekends}
                                    className="inline-flex items-center gap-1 text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] cursor-pointer"
                                    title="Replier les semaines composées uniquement de week-ends non travaillés"
                                >
                                    <ChevronDown size={13} />
                                    Replier week-ends et fériés
                                </button>

                                {/* Absents du fichier */}
                                <div className="text-[11px] text-[var(--color-fg-muted)]">
                                    Absents du fichier : <strong className="text-[var(--color-fg)]">{missingCount}</strong>
                                </div>
                            </div>
                        )}
                    </div>

                    {/* ── Barre filtres + bouton prochaine anomalie ─────────── */}
                    {!isRegularisationPhase && (
                        <div className="flex items-center justify-between border-x border-b border-[var(--color-border)] bg-[var(--color-surface)] mb-1">
                            <div className="flex items-center overflow-x-auto">
                                <FilterTab value="ALL" label="Toutes" count={lines.length} active={filter === 'ALL'} onClick={() => setFilter('ALL')} />
                                <FilterTab value="READY" label="Prêtes" count={readyCount} active={filter === 'READY'} onClick={() => setFilter('READY')} />
                                <FilterTab value="ISSUE" label="À traiter" count={issueCount} active={filter === 'ISSUE'} warning={issueCount > 0} onClick={() => setFilter('ISSUE')} />
                                <FilterTab value="CONFLICT" label="Conflits" count={conflictCount} active={filter === 'CONFLICT'} danger={conflictCount > 0} onClick={() => setFilter('CONFLICT')} />
                                <FilterTab value="IGNORE" label="Ignorées" count={restCount} active={filter === 'IGNORE'} onClick={() => setFilter('IGNORE')} />
                            </div>

                            <div className="flex items-center gap-2 px-3">
                                {issueCount > 0 && (
                                    <Button
                                        variant="secondary"
                                        size="xs"
                                        iconLeft={<SkipForward size={12} />}
                                        onClick={jumpToNextAnomaly}
                                        title="Aller à la prochaine anomalie"
                                    >
                                        Prochaine anomalie
                                    </Button>
                                )}
                                {selectedImportCount > 0 && (
                                    <Button
                                        variant="primary"
                                        size="xs"
                                        iconLeft={<Check size={12} />}
                                        onClick={() => onSave(approvedConflicts)}
                                        disabled={importApplying || selectedImportNeedsReview}
                                        loading={importApplying}
                                    >
                                        Régulariser les {selectedImportCount}
                                    </Button>
                                )}
                            </div>
                        </div>
                    )}

                    {/* ── Tableau ───────────────────────────────────────────── */}
                    <Table
                        ref={tableScrollRef}
                        maxHeight="54vh"
                        className="w-full flex-1"
                        tableClassName="w-full min-w-[980px] border-collapse text-left text-xs"
                        aria-label="Journées importées et valeurs modifiables"
                    >
                        <thead className="sticky top-0 z-10 bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)] shadow-sm">
                            <tr>
                                <th className="w-8 p-2 text-center">
                                    <span className="sr-only">Choix</span>
                                </th>
                                <th className="w-32 p-2">DATE</th>
                                <th className="w-[380px] p-2">ENTRÉE &nbsp; SORTIE &nbsp; RETOUR &nbsp; FIN</th>
                                <th className="w-24 p-2">DURÉE</th>
                                <th className="min-w-[240px] p-2">INDEMNITÉS</th>
                                <th className="w-24 p-2 text-right">TOTAL JOUR</th>
                                <th className="w-28 p-2">CONTRÔLE</th>
                            </tr>
                        </thead>

                        {groups.map(group => {
                            const collapsed = Boolean(collapsedWeeks[group.key]);
                            const { pretes, aRegul, totalIndemnites } = weekSummary(group.lines);
                            const weekLabel = `Semaine du ${new Date(`${group.key}T12:00:00`).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}`;

                            return (
                                <tbody key={group.key}>
                                    {/* Ligne d'en-tête de semaine cliquable */}
                                    <tr
                                        className="bg-[var(--color-bg-subtle)] cursor-pointer hover:bg-[var(--color-bg-subtle)]/80 transition-colors select-none"
                                        onClick={() => toggleWeek(group.key)}
                                    >
                                        <td colSpan={5} className="border-y border-[var(--color-border-subtle)] px-3 py-1.5">
                                            <div className="flex items-center gap-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">
                                                {collapsed
                                                    ? <ChevronRight size={12} className="shrink-0" />
                                                    : <ChevronDown size={12} className="shrink-0" />}
                                                {weekLabel}
                                                <span className="font-normal normal-case">
                                                    {pretes > 0 && <><span className="text-[var(--color-success-700)]">{pretes} prêtes</span></>}
                                                    {aRegul > 0 && <><span className="mx-1 opacity-40">·</span><span className="text-[var(--color-warning-700)]">{aRegul} à vérifier</span></>}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="border-y border-[var(--color-border-subtle)] px-2 py-1.5 text-right">
                                            {totalIndemnites > 0 && (
                                                <span className="text-[10px] font-bold text-[var(--color-fg)]">
                                                    {fmtAr(totalIndemnites)}
                                                </span>
                                            )}
                                        </td>
                                        <td className="border-y border-[var(--color-border-subtle)] px-2 py-1.5" />
                                    </tr>

                                    {/* Lignes du groupe (masquées si replié) */}
                                    {!collapsed && group.lines.map(line => {
                                        const proposed = line.proposed;
                                        const existing = line.existing;
                                        const row = grilleRows.find(r => r.datePointage === line.date);
                                        const isWeekend = isWeekendDate(line.date);
                                        const { isFerie, libelleFerie } = getLineFerieInfo(line, grilleRows);

                                        const selectable = ['IMPORT', 'SIGNAL'].includes(line.decision)
                                            && row?.canCorrect !== false
                                            && !line.errors?.length
                                            && !(existing?.congeApprouve && line.eventCode);
                                        const selected = Boolean(importSelections[line.ligne]);
                                        const editable = selected && line.decision === 'IMPORT' && Boolean(proposed) && selectable;
                                        const editableSignalAllowance = selected && line.decision === 'SIGNAL' && selectable;

                                        const lineTotal = computeLineIndemnitesTotal(line);

                                        const updateTime = (field, value) => updateLine(line.ligne, current => ({
                                            ...current,
                                            proposed: { ...current.proposed, [field]: value || null },
                                            _previewPending: true, _previewError: null
                                        }));

                                        const updateAllowance = (id, value) => updateLine(line.ligne, current => {
                                            const dynamicIndemnites = { ...(current.proposed?.dynamicIndemnites || {}), [String(id)]: value };
                                            const isLeaveSignal = current.decision === 'SIGNAL' && current.eventCode === 'C';
                                            const isAbsenceSignal = current.decision === 'SIGNAL' && current.eventCode === 'A';
                                            return {
                                                ...current,
                                                ...(current.decision === 'SIGNAL' ? {
                                                    allowanceOverrides: { ...(current.allowanceOverrides || {}), [String(id)]: value }
                                                } : {}),
                                                proposed: {
                                                    ...current.proposed, dynamicIndemnites,
                                                    ...(isLeaveSignal ? { codeStatut: 'CONGE', typeCongeJour: 'FULL_DAY' } : {}),
                                                    ...(isAbsenceSignal ? { codeStatut: 'ABSENCE_JUSTIFIEE', typeCongeJour: null } : {})
                                                },
                                                _previewPending: true, _previewError: null
                                            };
                                        });

                                        // ── Ligne férié chômé non travaillé (Style Photo 2) ──────────────
                                        if (isFerie && line.decision === 'IGNORE' && !line.eventCode) {
                                            const dayMonth = new Date(`${line.date}T12:00:00`).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' });
                                            const weekdayRaw = new Date(`${line.date}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'short' });
                                            const weekdayName = `${weekdayRaw.charAt(0).toUpperCase() + weekdayRaw.slice(1).replace(/\.$/, '')}..`;

                                            return (
                                                <tr
                                                    key={line.ligne}
                                                    data-ligne={line.ligne}
                                                    className="border-b border-[var(--color-border-subtle)] bg-[var(--color-purple-500)]/[0.04] hover:bg-[var(--color-purple-500)]/[0.08] transition-colors"
                                                >
                                                    {/* CHOIX */}
                                                    <td className="p-2 text-center">
                                                        <input
                                                            type="checkbox"
                                                            disabled
                                                            className="opacity-30"
                                                        />
                                                    </td>

                                                    {/* DATE : 15/08 CORRIGER + Sam.. */}
                                                    <td className="whitespace-nowrap p-2">
                                                        <div className="flex items-center gap-1.5">
                                                            <span className="font-bold text-xs text-[var(--color-fg)]">
                                                                {dayMonth}
                                                            </span>
                                                            {row?.canCorrect !== false && !existing?.congeApprouve && (
                                                                <button
                                                                    type="button"
                                                                    onClick={() => {
                                                                        if (!row) return;
                                                                        const dateAllowances = line.allowances?.length ? line.allowances : row._dateAllowances || [];
                                                                        const dynamicIndemnites = Object.fromEntries(dateAllowances.map(a => {
                                                                            const key = String(a.idIndemnite);
                                                                            const proposal = row._allowanceDefaults?.[key] || {};
                                                                            const fractionable = a.fractionnable ?? a.estFractionnable;
                                                                            const accordee = Boolean(proposal.accordee);
                                                                            return [key, { accordee, fractionJour: fractionable ? (accordee ? (Number(proposal.fractionJour) || 1) : 0) : (accordee ? 1 : 0), ...(fractionable ? { matin: Boolean(proposal.matin), am: Boolean(proposal.am) } : {}) }];
                                                                        }));
                                                                        updateLine(line.ligne, current => ({
                                                                            ...current, decision: 'IMPORT',
                                                                            proposed: { ...row, datePointage: line.date, codeStatut: 'FERIE_TRAVAILLE', heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '', dynamicIndemnites, inclureIndemnitesCalculeesPreview: true, totalHeures: null },
                                                                            allowances: dateAllowances, _previewPending: true, _previewError: null,
                                                                            warnings: ["Renseignez les horaires avant l'enregistrement."]
                                                                        }));
                                                                        setImportSelections(current => ({ ...current, [line.ligne]: true }));
                                                                    }}
                                                                    className="text-[10px] font-bold text-[var(--color-fg-muted)] hover:text-[var(--color-brand-600)] transition-colors cursor-pointer uppercase tracking-tight"
                                                                    title="Saisir des heures pour ce jour férié"
                                                                >
                                                                    CORRIGER
                                                                </button>
                                                            )}
                                                        </div>
                                                        <div className="text-[10px] text-[var(--color-fg-muted)]">
                                                            {weekdayName}
                                                        </div>
                                                    </td>

                                                    {/* Situation centrale : Férié chômé · Assomption · Jour non ouvré */}
                                                    <td colSpan={4} className="px-2 py-2 text-center">
                                                        <div className="flex items-center justify-center gap-1.5 text-xs text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]">
                                                            <span className="font-semibold">Férié chômé</span>
                                                            <span className="opacity-60">·</span>
                                                            <span className="font-semibold">{libelleFerie || 'Férié'}</span>
                                                            <span className="opacity-60">·</span>
                                                            <span className="font-normal text-[11px] text-[var(--color-purple-600)] dark:text-[var(--color-purple-400)]">
                                                                {isWeekend ? 'Jour non ouvré' : 'Chômé payé'}
                                                            </span>
                                                        </div>
                                                    </td>

                                                    {/* CONTRÔLE : Badge FÉRIÉ CHÔMÉ */}
                                                    <td className="p-2 text-right">
                                                        <Badge variant="neutral" size="sm">FÉRIÉ CHÔMÉ</Badge>
                                                    </td>
                                                </tr>
                                            );
                                        }

                                        // ── Ligne week-end ordinaire non travaillé ────────────────────────
                                        if (isWeekend && line.decision === 'IGNORE' && !line.eventCode) {
                                            return (
                                                <tr
                                                    key={line.ligne}
                                                    data-ligne={line.ligne}
                                                    className="border-b border-[var(--color-border-subtle)]/60 bg-[var(--color-bg-subtle)]/40"
                                                >
                                                    <td className="p-2 text-center">
                                                        <input
                                                            type="checkbox"
                                                            disabled
                                                            className="opacity-30"
                                                        />
                                                    </td>
                                                    <td colSpan={5} className="px-2 py-1.5 text-[11px] text-[var(--color-fg-muted)] italic">
                                                        {fmtDayLabel(line.date)} — non travaillé
                                                    </td>
                                                    <td className="px-2 py-1.5 text-right">
                                                        {row?.canCorrect !== false && !existing?.congeApprouve && (
                                                            <Button
                                                                variant="ghost"
                                                                size="xs"
                                                                onClick={() => {
                                                                    if (!row) return;
                                                                    const dateAllowances = line.allowances?.length ? line.allowances : row._dateAllowances || [];
                                                                    const dynamicIndemnites = Object.fromEntries(dateAllowances.map(a => {
                                                                        const key = String(a.idIndemnite);
                                                                        const proposal = row._allowanceDefaults?.[key] || {};
                                                                        const fractionable = a.fractionnable ?? a.estFractionnable;
                                                                        const accordee = Boolean(proposal.accordee);
                                                                        return [key, { accordee, fractionJour: fractionable ? (accordee ? (Number(proposal.fractionJour) || 1) : 0) : (accordee ? 1 : 0), ...(fractionable ? { matin: Boolean(proposal.matin), am: Boolean(proposal.am) } : {}) }];
                                                                    }));
                                                                    updateLine(line.ligne, current => ({
                                                                        ...current, decision: 'IMPORT',
                                                                        proposed: { ...row, datePointage: line.date, codeStatut: 'PRESENT', heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '', dynamicIndemnites, inclureIndemnitesCalculeesPreview: true, totalHeures: null },
                                                                        allowances: dateAllowances, _previewPending: true, _previewError: null,
                                                                        warnings: ["Renseignez les horaires avant l'enregistrement."]
                                                                    }));
                                                                    setImportSelections(current => ({ ...current, [line.ligne]: true }));
                                                                }}
                                                            >
                                                                Déclarer travaillé
                                                            </Button>
                                                        )}
                                                    </td>
                                                </tr>
                                            );
                                        }

                                        // ── Ligne normale ──────────────────────────────────────────────────
                                        const hasIssue = line._previewError || line.errors?.length || line.warnings?.length;
                                        const isAnomaly = line.decision === 'SIGNAL' || line.conflict || hasIssue;

                                        return (
                                            <Fragment key={line.ligne}>
                                                <tr
                                                    data-ligne={line.ligne}
                                                    className={`border-b border-[var(--color-border-subtle)] align-middle transition-colors ${
                                                        isAnomaly && !selected ? 'bg-[#fffbeb]'
                                                            : selected ? 'bg-[var(--color-brand-500)]/5'
                                                                : 'bg-[var(--color-surface)]'
                                                    } hover:bg-[var(--color-bg-subtle)]/40`}
                                                >
                                                    {/* Checkbox */}
                                                    <td className="p-2 text-center">
                                                        <input
                                                            type="checkbox"
                                                            checked={selected}
                                                            disabled={isRegularisationPhase || !selectable}
                                                            onChange={e => setImportSelections(current => ({ ...current, [line.ligne]: e.target.checked }))}
                                                            className="accent-[var(--color-brand-600)]"
                                                        />
                                                    </td>

                                                    {/* Date */}
                                                    <td className="whitespace-nowrap p-2">
                                                        <div className={`font-bold text-xs ${isAnomaly ? 'text-[var(--color-warning-700)]' : 'text-[var(--color-fg)]'}`}>
                                                            {line.date}
                                                        </div>
                                                        <div className="text-[10px] font-normal text-[var(--color-fg-muted)]">
                                                            {new Date(`${line.date}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'short' }).replace(/\.$/, '').toLowerCase()}
                                                        </div>
                                                        {isFerie && (
                                                            <Badge variant="warning" size="sm">{libelleFerie || 'Jour férié'}</Badge>
                                                        )}
                                                        {line.eventCode && (
                                                            <Badge variant="warning" size="sm">{line.eventCode === 'C' ? 'Congé' : 'Absence'}</Badge>
                                                        )}
                                                    </td>

                                                    {/* Horaires */}
                                                    <td className="px-2 py-1.5">
                                                        {proposed && line.decision === 'IMPORT' ? (
                                                            <div className="flex flex-wrap items-end gap-x-2 gap-y-1">
                                                                {TIME_FIELDS.map(([field, label]) => (
                                                                    <label key={field} className="flex flex-col gap-0.5 text-[9px] font-semibold text-[var(--color-fg-muted)]">
                                                                        {label}
                                                                        <TimeInput
                                                                            value={proposed[field] || ''}
                                                                            disabled={!editable}
                                                                            onChange={value => updateTime(field, value)}
                                                                            className="h-7 w-[66px]"
                                                                        />
                                                                    </label>
                                                                ))}
                                                            </div>
                                                        ) : line.decision === 'IGNORE' && !line.eventCode && row?.canCorrect !== false && !existing?.congeApprouve ? (
                                                            <Button variant="secondary" size="xs" onClick={() => {
                                                                if (!row) return;
                                                                const dateAllowances = line.allowances?.length ? line.allowances : row._dateAllowances || [];
                                                                const dynamicIndemnites = Object.fromEntries(dateAllowances.map(a => {
                                                                    const key = String(a.idIndemnite);
                                                                    const proposal = row._allowanceDefaults?.[key] || {};
                                                                    const fractionable = a.fractionnable ?? a.estFractionnable;
                                                                    const accordee = Boolean(proposal.accordee);
                                                                    return [key, { accordee, fractionJour: fractionable ? (accordee ? (Number(proposal.fractionJour) || 1) : 0) : (accordee ? 1 : 0), ...(fractionable ? { matin: Boolean(proposal.matin), am: Boolean(proposal.am) } : {}) }];
                                                                }));
                                                                updateLine(line.ligne, current => ({
                                                                    ...current, decision: 'IMPORT',
                                                                    proposed: { ...row, datePointage: line.date, codeStatut: isFerie ? 'FERIE_TRAVAILLE' : 'PRESENT', heureEntreeMatin: '', heureSortieMatin: '', heureEntreeAm: '', heureSortieAm: '', dynamicIndemnites, inclureIndemnitesCalculeesPreview: true, totalHeures: null },
                                                                    allowances: dateAllowances, _previewPending: true, _previewError: null,
                                                                    warnings: ["Renseignez les horaires avant l'enregistrement."]
                                                                }));
                                                                setImportSelections(current => ({ ...current, [line.ligne]: true }));
                                                            }}>{isFerie ? 'Déclarer travaillé (férié)' : 'Déclarer travaillé'}</Button>
                                                        ) : line.decision === 'MISSING' && proposed ? (
                                                            <Button variant="secondary" size="xs" onClick={() => {
                                                                updateLine(line.ligne, current => ({ ...current, decision: 'IMPORT', _previewPending: true, proposed: { ...current.proposed, codeStatut: 'PRESENT' } }));
                                                                setImportSelections(current => ({ ...current, [line.ligne]: true }));
                                                            }}>Compléter</Button>
                                                        ) : line.decision === 'SIGNAL' && line.eventCode ? (
                                                            <span className="text-[11px] text-[var(--color-warning-700)] font-medium">
                                                                {line.eventCode === 'C'
                                                                    ? 'Congé signalé : choisir la date de reprise dans la régularisation.'
                                                                    : 'Absence signalée : à régulariser.'}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-muted)]">—</span>
                                                        )}
                                                    </td>

                                                    {/* Durée */}
                                                    <td className="whitespace-nowrap p-2">
                                                        <div className={`font-data font-semibold text-xs ${line.decision === 'SIGNAL' && !line.proposed?.totalHeures ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-fg)]'}`}>
                                                            {line._previewPending ? '…'
                                                                : proposed?.totalHeures != null ? formatHeuresMinutes(proposed.totalHeures)
                                                                    : '00h00'}
                                                        </div>
                                                        {proposed?.libelleStatut && (
                                                            <div className={`mt-0.5 text-[10px] font-normal ${isAnomaly ? 'text-[var(--color-warning-700)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                                {proposed.libelleStatut}
                                                            </div>
                                                        )}
                                                    </td>

                                                    {/* Indemnités */}
                                                    <td className="px-2 py-1.5">
                                                        {(line.allowances || []).length > 0 ? (
                                                            <div className="flex flex-wrap gap-1.5">
                                                                {line.allowances.map(allowance => {
                                                                    const value = proposed?.dynamicIndemnites?.[String(allowance.idIndemnite)] || {};
                                                                    const selectedValue = line.decision === 'SIGNAL'
                                                                        ? line.allowanceOverrides?.[String(allowance.idIndemnite)] || value : value;
                                                                    const amountPreview = proposed?.indemnitesCalculeesPreview?.[String(allowance.idIndemnite)];
                                                                    return (
                                                                        <AllowanceEditor
                                                                            key={allowance.idIndemnite}
                                                                            allowance={allowance}
                                                                            value={selectedValue}
                                                                            amountPreview={amountPreview}
                                                                            disabled={!editable && !editableSignalAllowance}
                                                                            onChange={next => updateAllowance(allowance.idIndemnite, next)}
                                                                        />
                                                                    );
                                                                })}
                                                            </div>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-muted)]">—</span>
                                                        )}
                                                    </td>

                                                    {/* Total jour */}
                                                    <td className="p-2 text-right whitespace-nowrap">
                                                        {lineTotal > 0 && (
                                                            <span className="font-data font-bold text-xs text-[var(--color-fg)]">
                                                                {fmtAr(lineTotal)}
                                                            </span>
                                                        )}
                                                    </td>

                                                    {/* Contrôle */}
                                                    <td className="px-2 py-1.5">
                                                        <div className="flex flex-col items-start gap-1">
                                                            <ControlBadge
                                                                line={line}
                                                                expanded={Boolean(expanded[line.ligne])}
                                                                onToggle={() => setExpanded(c => ({ ...c, [line.ligne]: !c[line.ligne] }))}
                                                            />
                                                            {line.decision === 'SIGNAL' && !line.errors?.length && row?.canCorrect !== false && (
                                                                <Button
                                                                    variant="secondary"
                                                                    size="xs"
                                                                    disabled={selectedImportCount > 0 || importApplying}
                                                                    title={selectedImportCount > 0
                                                                        ? "Enregistrez d'abord les pointages prêts avant de régulariser le congé ou l'absence."
                                                                        : undefined}
                                                                    onClick={() => onRegularize(line)}
                                                                >
                                                                    RÉGULARISER
                                                                </Button>
                                                            )}
                                                        </div>
                                                    </td>
                                                </tr>

                                                {/* Ligne de détails (expand) */}
                                                {expanded[line.ligne] && (
                                                    <tr key={`${line.ligne}-detail`} className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                                                        <td colSpan={7} className="px-4 py-2 text-xs">
                                                            {line.conflict && (
                                                                <div className="mb-2 flex flex-wrap items-center gap-4 text-[var(--color-fg-muted)]">
                                                                    <span>
                                                                        <strong>Enregistré :</strong> {existing?.statut || 'Pointage'} ·{' '}
                                                                        {[existing?.heureEntreeMatin, existing?.heureSortieMatin, existing?.heureEntreeAm, existing?.heureSortieAm].filter(Boolean).join(' / ') || 'sans heures'}
                                                                    </span>
                                                                    <ArrowRight size={12} className="shrink-0" />
                                                                    <span>
                                                                        <strong>Fichier :</strong>{' '}
                                                                        {[proposed?.heureEntreeMatin, proposed?.heureSortieMatin, proposed?.heureEntreeAm, proposed?.heureSortieAm].filter(Boolean).join(' / ') || 'sans heures'}
                                                                    </span>
                                                                    {line.decision === 'IMPORT' && (
                                                                        <label className="inline-flex items-center gap-1 font-semibold text-[var(--color-fg)]">
                                                                            <input
                                                                                type="checkbox"
                                                                                checked={Boolean(approvedConflicts[line.ligne])}
                                                                                onChange={e => setApprovedConflicts(c => ({ ...c, [line.ligne]: e.target.checked }))}
                                                                            />
                                                                            Remplacer le pointage enregistré
                                                                        </label>
                                                                    )}
                                                                </div>
                                                            )}
                                                            {line.errors?.map((msg, i) => (
                                                                <div key={`e${i}`} className="text-[var(--color-danger-600)]">{msg}</div>
                                                            ))}
                                                            {line._previewError && (
                                                                <div className="text-[var(--color-danger-600)]">{line._previewError}</div>
                                                            )}
                                                            {line.warnings?.map((msg, i) => (
                                                                <div key={`w${i}`} className="text-[var(--color-warning-700)]">{msg}</div>
                                                            ))}
                                                        </td>
                                                    </tr>
                                                )}
                                            </Fragment>
                                        );
                                    })}
                                </tbody>
                            );
                        })}
                    </Table>

                    {/* ── Footer ────────────────────────────────────────────── */}
                    <div className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border)] pt-3 mt-2">
                        {/* Résumé de sélection + total indemnités */}
                        {!isRegularisationPhase && (
                            <div className="flex items-center gap-4 min-w-0">
                                <p className="max-w-xl text-xs text-[var(--color-fg-muted)]">
                                    <strong className="text-[var(--color-fg)]">
                                        {selectedImportCount} pointage{selectedImportCount > 1 ? 's' : ''} · {selectedSignalCount} signalement{selectedSignalCount > 1 ? 's' : ''} sélectionné{selectedCount > 1 ? 's' : ''}.
                                    </strong>
                                    {' '}Enregistrez d'abord les pointages prêts, puis régularisez les congés et absences.
                                </p>
                                {totalIndemnitesAEnregistrer > 0 && (
                                    <div className="shrink-0 text-right">
                                        <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Indemnités à enregistrer</div>
                                        <div className="font-data font-bold text-sm text-[var(--color-fg)]">{fmtAr(totalIndemnitesAEnregistrer)}</div>
                                    </div>
                                )}
                            </div>
                        )}
                        {isRegularisationPhase && (
                            <p className="max-w-xl text-xs text-[var(--color-fg-muted)]">
                                Les indemnités restent modifiables avant chaque régularisation. Vous pouvez terminer plus tard ; les signalements non traités ne sont pas enregistrés.
                            </p>
                        )}

                        {/* Boutons d'action */}
                        <div className="flex gap-2 shrink-0">
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={onClose}
                                disabled={importApplying}
                                className="rounded-none font-bold text-xs uppercase"
                            >
                                {isRegularisationPhase ? 'Terminer plus tard' : 'ANNULER'}
                            </Button>

                            {!isRegularisationPhase && (
                                <Button
                                    variant="secondary"
                                    size="sm"
                                    onClick={() => onApply(approvedConflicts)}
                                    disabled={!selectedCount || importApplying || selectedNeedsReview}
                                    className="rounded-none font-bold text-xs uppercase"
                                >
                                    CHARGER DANS LA GRILLE
                                </Button>
                            )}

                            {!isRegularisationPhase && selectedImportCount === 0 && signalCount > 0 && (
                                <Button
                                    variant="primary"
                                    size="sm"
                                    onClick={onStartRegularisation}
                                    className="rounded-none font-bold text-xs uppercase"
                                >
                                    Traiter les signalements
                                </Button>
                            )}

                            {!isRegularisationPhase && selectedImportCount > 0 && (
                                <Button
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<Check size={14} />}
                                    onClick={() => onSave(approvedConflicts)}
                                    disabled={importApplying || selectedImportNeedsReview}
                                    loading={importApplying}
                                    className="rounded-none font-bold text-xs uppercase"
                                >
                                    ENREGISTRER {selectedImportCount} POINTAGE{selectedImportCount > 1 ? 'S' : ''}
                                </Button>
                            )}
                        </div>
                    </div>
                </div>
            )}
        </Modal>
    );
}
