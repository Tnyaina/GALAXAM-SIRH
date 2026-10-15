import { Fragment, useEffect, useMemo, useState } from 'react';
import { CalendarDays, Check, FileSpreadsheet, LoaderCircle } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import TimeInput from '@/components/ui/forms/TimeInput';
import Badge from '@/components/ui/data-display/Badge';
import { Table } from '@/components/ui/data-display/Table';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { importLineNeedsReview } from '@/features/attendance/utils/pointageImport';
import { toPointageBatchPayload } from '@/features/attendance/utils/pointageBatchPayload';
import { POINTAGE_TIME_FIELDS } from '@/features/attendance/utils/pointageRow';

const TIME_FIELDS = POINTAGE_TIME_FIELDS.map((field, index) => [field, ['Entrée', 'Sortie', 'Retour', 'Fin'][index]]);

function AllowanceEditor({ allowance, value = {}, disabled, onChange }) {
    const fractionable = allowance.fractionnable ?? allowance.estFractionnable;
    const granted = Boolean(value.accordee);
    const inputClass = 'accent-[var(--color-brand-600)] cursor-pointer';
    return (
        <div className="flex min-w-[126px] flex-col gap-0.5 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-1.5 py-1">
            <span className="truncate text-[9px] font-semibold text-[var(--color-fg)]" title={allowance.code || allowance.libelle}>
                {allowance.code || allowance.libelle}
                {allowance.montantJour != null && <span className="ml-1 text-[9px] font-normal text-[var(--color-fg-muted)]">{allowance.montantJour} Ar/j</span>}
            </span>
            {fractionable ? (
                <div className="flex gap-1.5 text-[9px]">
                    {[
                        ['matin', 'Matin'], ['am', 'Après-midi']
                    ].map(([field, label]) => (
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
                <label className={`inline-flex items-center gap-1 text-[10px] font-medium ${disabled ? 'opacity-50' : 'cursor-pointer'}`}>
                    <input
                        type="checkbox"
                        className={inputClass}
                        disabled={disabled}
                        checked={granted}
                        onChange={event => onChange({ ...value, accordee: event.target.checked, fractionJour: event.target.checked ? 1 : 0 })}
                    />
                    Accordée
                </label>
            )}
        </div>
    );
}

function ImportSkeleton() {
    return (
        <div className="space-y-2" aria-label="Chargement de l’aperçu d’import" aria-busy="true">
            <div className="grid grid-cols-4 gap-2 border border-[var(--color-border-subtle)] p-3">
                {[1, 2, 3, 4].map(item => <Skeleton key={item} className="h-9 w-full animate-pulse" />)}
            </div>
            <div className="border border-[var(--color-border-subtle)]">
                <div className="grid grid-cols-6 gap-3 bg-[var(--color-bg-subtle)] p-3">{[1, 2, 3, 4, 5, 6].map(item => <Skeleton key={item} className="h-3 w-full" />)}</div>
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
    onRegularize
}) {
    const [filter, setFilter] = useState('ALL');
    const [expanded, setExpanded] = useState({});
    const [approvedConflicts, setApprovedConflicts] = useState({});
    const lines = useMemo(() => importPreview?.lines || [], [importPreview]);
    const selectedImportCount = lines.filter(line => importSelections[line.ligne] && line.decision === 'IMPORT' && !line.eventCode).length;
    const selectedSignalCount = lines.filter(line => importSelections[line.ligne] && (line.decision === 'SIGNAL' || (line.decision === 'IMPORT' && line.eventCode))).length;
    const selectedCount = selectedImportCount + selectedSignalCount;
    const readyCount = lines.filter(line => line.decision === 'IMPORT' && !line.errors?.length && !line._previewPending && !line._previewError && !line.proposed?.hasValidationError).length;
    const signalCount = lines.filter(line => line.decision === 'SIGNAL').length;
    const restCount = lines.filter(line => line.decision === 'IGNORE').length;
    const missingCount = lines.filter(line => line.decision === 'MISSING').length;
    const conflictCount = lines.filter(line => line.conflict && line.decision === 'IMPORT').length;
    const selectedNeedsReview = lines.some(line => importSelections[line.ligne] && importLineNeedsReview(line, approvedConflicts));
    const visibleLines = useMemo(() => lines.filter(line => filter === 'ALL'
        || (filter === 'READY' && line.decision === 'IMPORT' && !line.errors?.length)
        || (filter === 'ISSUE' && (line.decision === 'SIGNAL' || line.decision === 'ERROR' || line.decision === 'MISSING' || line._previewError))
        || (filter === 'CONFLICT' && line.conflict)
        || (filter === 'IGNORE' && line.decision === 'IGNORE')), [lines, filter]);
    useEffect(() => {
        const pending = lines.filter(line => line._previewPending && line.proposed);
        if (!pending.length) return undefined;
        let cancelled = false;
        const timer = setTimeout(async () => {
            try {
                const results = await attendanceService.daily.previewBatch(pending.map(line => toPointageBatchPayload(line.proposed)));
                if (cancelled) return;
                const byId = new Map(pending.map((line, index) => [line.ligne, results?.[index]]));
                setImportPreview(current => current ? ({ ...current, lines: current.lines.map(line => {
                    const result = byId.get(line.ligne);
                    if (!result || !line._previewPending || !pending.some(item => item.ligne === line.ligne && item.proposed === line.proposed)) return line;
                    return { ...line, proposed: result.preview || line.proposed, _previewPending: false,
                        _previewError: result.error || (result.preview?.hasValidationError ? result.preview.validationMessages?.[0] : null) };
                }) }) : current);
            } catch (error) {
                if (!cancelled) setImportPreview(current => current ? ({ ...current, lines: current.lines.map(line =>
                    pending.some(item => item.ligne === line.ligne && item.proposed === line.proposed)
                        ? { ...line, _previewPending: false, _previewError: error?.response?.data?.message || 'Calcul indisponible' } : line) }) : current);
            }
        }, 350);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [lines, setImportPreview]);
    const groups = useMemo(() => {
        const result = [];
        visibleLines.forEach(line => {
            const date = new Date(`${line.date}T12:00:00`);
            const monday = new Date(date);
            monday.setDate(date.getDate() - ((date.getDay() + 6) % 7));
            const key = monday.toISOString().slice(0, 10);
            let group = result.find(item => item.key === key);
            if (!group) {
                group = { key, lines: [] };
                result.push(group);
            }
            group.lines.push(line);
        });
        return result;
    }, [visibleLines]);

    const updateLine = (ligne, update) => setImportPreview(current => ({
        ...current,
        lines: current.lines.map(item => item.ligne === ligne ? update(item) : item)
    }));

    const toggleAll = (checked) => setImportSelections(Object.fromEntries(lines.map(line => [
        line.ligne,
        checked && ['IMPORT', 'SIGNAL'].includes(line.decision) && !line.errors?.length &&
        grilleRows.find(row => row.datePointage === line.date)?.canCorrect !== false
    ])));

    return (
        <Modal
            isOpen={Boolean(importPreview || importPreviewLoading)}
            onClose={importPreviewLoading ? undefined : onClose}
            title={importPreviewLoading ? 'Lecture du fichier d’import' : `Aperçu de l’import · ${lines.length} journées`}
            icon={importPreviewLoading ? <LoaderCircle size={17} className="animate-spin" /> : <FileSpreadsheet size={17} />}
            size="full"
            closeOnBackdrop={!importPreviewLoading}
            closeOnEscape={!importPreviewLoading}
            className="!max-w-[88vw] w-[88vw] max-h-[88vh]"
        >
            {importPreviewLoading ? (
                <div className="space-y-4">
                    <div className="border border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5 p-4">
                        <div className="flex items-center gap-3 text-sm font-semibold text-[var(--color-fg)]">
                            <LoaderCircle size={18} className="animate-spin text-[var(--color-brand-600)]" />
                            Analyse du fichier et préparation des journées…
                        </div>
                        <p className="mt-1 pl-7 text-xs text-[var(--color-fg-muted)]">Les données restent en local jusqu’à leur chargement dans la grille.</p>
                    </div>
                    <ImportSkeleton />
                </div>
            ) : (
                <>
                    <div className="mb-3 flex flex-wrap items-center justify-between gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2">
                        <div className="flex min-w-0 items-center gap-2 text-xs text-[var(--color-fg-muted)]">
                            <CalendarDays size={15} className="shrink-0 text-[var(--color-brand-600)]" />
                            <span className="truncate">{importPreview?.employeNom || importPreview?.nomEmploye || 'Collaborateur'}{importPreview?.filename ? ` · ${importPreview.filename}` : ''}</span>
                            {importPreview?.dateDebut && <span className="hidden sm:inline">· {importPreview.dateDebut} au {importPreview.dateFin}</span>}
                        </div>
                        <div className="flex flex-wrap gap-2 text-[11px]">
                            <Badge variant="success" size="sm">{readyCount} prêtes</Badge>
                            <Badge variant="warning" size="sm">{signalCount} à régulariser</Badge>
                            <Badge variant="warning" size="sm">{missingCount} absentes du fichier</Badge>
                            <Badge variant="default" size="sm">{restCount} ignorées</Badge>
                        </div>
                    </div>

                    <div className="mb-2 flex items-center justify-between gap-3">
                        <p className="text-xs text-[var(--color-fg-muted)]">Vérifiez les heures et indemnités. Les modifications sont recalculées avant l’enregistrement.</p>
                        <label className="inline-flex shrink-0 cursor-pointer items-center gap-2 text-xs font-semibold">
                            <input type="checkbox" checked={lines.length > 0 && lines.filter(line => ['IMPORT', 'SIGNAL'].includes(line.decision) && !line.errors?.length).every(line => importSelections[line.ligne])} onChange={event => toggleAll(event.target.checked)} className="accent-[var(--color-brand-600)]" />
                            Tout sélectionner
                        </label>
                    </div>

                    <div className="mb-2 flex flex-wrap gap-1" aria-label="Filtrer les journées d’import">
                        {[
                            ['ALL', `Toutes (${lines.length})`], ['READY', `Prêtes (${readyCount})`],
                            ['ISSUE', `À traiter (${signalCount + missingCount + lines.filter(line => line.decision === 'ERROR').length})`],
                            ['CONFLICT', `Conflits (${conflictCount})`], ['IGNORE', `Ignorées (${restCount})`]
                        ].map(([value, label]) => <Button key={value} variant={filter === value ? 'primary' : 'secondary'} size="xs" onClick={() => setFilter(value)}>{label}</Button>)}
                    </div>

                    <Table maxHeight="52vh" className="w-full" tableClassName="w-full min-w-[940px] border-collapse text-left text-xs" aria-label="Journées importées et valeurs modifiables">
                            <thead className="sticky top-0 z-10 bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase text-[var(--color-fg-muted)] shadow-sm">
                                <tr>
                                    <th className="w-10 p-2 text-center">Choix</th>
                                    <th className="w-28 p-2">Date</th>
                                    <th className="w-[410px] p-2">Horaires importés / modifiables</th>
                                    <th className="w-24 p-2">Durée</th>
                                    <th className="min-w-[260px] p-2">Indemnités</th>
                                    <th className="w-28 p-2">Contrôle</th>
                                </tr>
                            </thead>
                            {groups.map(group => (
                                <tbody key={group.key}>
                                    <tr className="bg-[var(--color-bg-subtle)]/70">
                                        <td colSpan={6} className="border-y border-[var(--color-border-subtle)] px-3 py-1.5 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">
                                            Semaine du {new Date(`${group.key}T12:00:00`).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}
                                        </td>
                                    </tr>
                                    {group.lines.map(line => {
                                        const proposed = line.proposed;
                                        const existing = line.existing;
                                        const row = grilleRows.find(item => item.datePointage === line.date);
                                        const selectable = ['IMPORT', 'SIGNAL'].includes(line.decision) && row?.canCorrect !== false && !line.errors?.length && !(existing?.congeApprouve && line.eventCode);
                                        const selected = Boolean(importSelections[line.ligne]);
                                        const editable = selected && line.decision === 'IMPORT' && Boolean(proposed) && selectable;
                                        const updateTime = (field, value) => updateLine(line.ligne, current => ({
                                            ...current,
                                            proposed: { ...current.proposed, [field]: value || null }, _previewPending: true, _previewError: null
                                        }));
                                        const updateAllowance = (id, value) => updateLine(line.ligne, current => ({
                                            ...current,
                                            proposed: { ...current.proposed, dynamicIndemnites: { ...current.proposed.dynamicIndemnites, [String(id)]: value } },
                                            _previewPending: true, _previewError: null
                                        }));
                                        return (<Fragment key={line.ligne}>
                                            <tr className={`border-b border-[var(--color-border-subtle)] align-middle ${selected ? 'bg-[var(--color-brand-500)]/5' : 'bg-[var(--color-surface)]'} hover:bg-[var(--color-bg-subtle)]/40`}>
                                                <td className="p-2 text-center">
                                                    <input type="checkbox" checked={selected} disabled={!selectable} onChange={event => setImportSelections(current => ({ ...current, [line.ligne]: event.target.checked }))} className="accent-[var(--color-brand-600)]" />
                                                </td>
                                                <td className="whitespace-nowrap p-2 font-semibold text-[var(--color-fg)]">
                                                    {line.date}
                                                    <div className="text-[10px] font-normal text-[var(--color-fg-muted)]">{new Date(`${line.date}T12:00:00`).toLocaleDateString('fr-FR', { weekday: 'short' })}</div>
                                                    {line.eventCode && <Badge variant="warning" size="sm">{line.eventCode === 'C' ? 'Congé' : 'Absence'}</Badge>}
                                                </td>
                                                <td className="px-2 py-1.5">
                                                    {proposed && line.decision === 'IMPORT' ? (
                                                        <div className="flex flex-wrap items-end gap-x-1.5 gap-y-0.5">
                                                            {TIME_FIELDS.map(([field, label]) => (
                                                                <label key={field} className="flex flex-col gap-0.5 text-[9px] font-semibold text-[var(--color-fg-muted)]">
                                                                    {label}
                                                                    <TimeInput value={proposed[field] || ''} disabled={!editable} onChange={value => updateTime(field, value)} className="h-7 w-[68px]" />
                                                                </label>
                                                            ))}
                                                        </div>
                                                    ) : line.decision === 'MISSING' && proposed ? (
                                                        <Button variant="secondary" size="xs" onClick={() => {
                                                            updateLine(line.ligne, current => ({ ...current, decision: 'IMPORT', _previewPending: true,
                                                                proposed: { ...current.proposed, codeStatut: 'PRESENT' } }));
                                                            setImportSelections(current => ({ ...current, [line.ligne]: true }));
                                                        }}>Compléter cette journée</Button>
                                                    ) : <span className="text-[var(--color-fg-muted)]">{proposed ? 'À régulariser' : 'Aucune heure'}</span>}
                                                </td>
                                                <td className="whitespace-nowrap p-2 font-mono font-semibold text-[var(--color-fg)]">
                                                    {line._previewPending ? '…' : proposed?.totalHeures != null ? formatHeuresMinutes(proposed.totalHeures) : '—'}
                                                    {proposed?.libelleStatut && <div className="mt-1 font-sans text-[10px] font-normal text-[var(--color-fg-muted)]">{proposed.libelleStatut}</div>}
                                                </td>
                                                <td className="px-2 py-1.5">
                                                    {(line.allowances || []).length > 0 ? (
                                                        <div className="flex flex-wrap gap-1.5">
                                                            {line.allowances.map(allowance => {
                                                                const value = proposed?.dynamicIndemnites?.[String(allowance.idIndemnite)] || {};
                                                                return <AllowanceEditor key={allowance.idIndemnite} allowance={allowance} value={value} disabled={!editable} onChange={next => updateAllowance(allowance.idIndemnite, next)} />;
                                                            })}
                                                        </div>
                                                    ) : <span className="text-[var(--color-fg-muted)]">—</span>}
                                                </td>
                                                <td className="px-2 py-1.5">
                                                    {line._previewPending ? <Badge variant="warning" size="sm">Calcul…</Badge>
                                                        : line._previewError || line.errors?.length || proposed?.hasValidationError ? <Badge variant="danger" size="sm">À corriger</Badge>
                                                            : line.conflict ? <Badge variant="warning" size="sm">Conflit</Badge>
                                                                : line.decision === 'SIGNAL' ? <Badge variant="warning" size="sm">À régulariser</Badge>
                                                                    : line.decision === 'MISSING' ? <Badge variant="default" size="sm">Absente</Badge>
                                                                        : line.decision === 'IMPORT' ? <Badge variant="success" size="sm">Prête</Badge>
                                                                            : <Badge variant="default" size="sm">Ignorée</Badge>}
                                                    {(line.conflict || line.errors?.length || line.warnings?.length || line._previewError) &&
                                                        <Button variant="ghost" size="xs" onClick={() => setExpanded(current => ({ ...current, [line.ligne]: !current[line.ligne] }))}>{expanded[line.ligne] ? 'Masquer' : 'Détails'}</Button>}
                                                    {line.decision === 'SIGNAL' && !line.errors?.length && row?.canCorrect !== false &&
                                                        <Button variant="secondary" size="xs" onClick={() => onRegularize(line)}>Régulariser</Button>}
                                                </td>
                                            </tr>
                                            {expanded[line.ligne] && <tr key={`${line.ligne}-detail`} className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                                                <td colSpan={6} className="px-3 py-2 text-xs">
                                                    {line.conflict && <div className="mb-2 flex flex-wrap items-center gap-3">
                                                        <span>Enregistré : {existing?.statut || 'Pointage'} · {[existing?.heureEntreeMatin, existing?.heureSortieMatin, existing?.heureEntreeAm, existing?.heureSortieAm].filter(Boolean).join(' / ') || 'sans heures'}</span>
                                                        <span>Fichier : {[proposed?.heureEntreeMatin, proposed?.heureSortieMatin, proposed?.heureEntreeAm, proposed?.heureSortieAm].filter(Boolean).join(' / ') || 'sans heures'}</span>
                                                        {line.decision === 'IMPORT' && <label className="inline-flex items-center gap-1 font-semibold"><input type="checkbox" checked={Boolean(approvedConflicts[line.ligne])} onChange={event => setApprovedConflicts(current => ({ ...current, [line.ligne]: event.target.checked }))} /> Remplacer le pointage enregistré</label>}
                                                    </div>}
                                                    {line.errors?.map((message, index) => <div key={`e${index}`} className="text-[var(--color-danger-600)]">{message}</div>)}
                                                    {line._previewError && <div className="text-[var(--color-danger-600)]">{line._previewError}</div>}
                                                    {line.warnings?.map((message, index) => <div key={`w${index}`} className="text-[var(--color-warning-700)]">{message}</div>)}
                                                </td>
                                            </tr>}
                                        </Fragment>);
                                    })}
                                </tbody>
                            ))}
                    </Table>

                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border-subtle)] pt-3">
                        <p className="max-w-2xl text-xs text-[var(--color-fg-muted)]">
                            <strong className="text-[var(--color-fg)]">{selectedImportCount} pointage(s) · {selectedSignalCount} signalement(s) sélectionné(s).</strong> Les congés et absences signalés demandent une régularisation dans la grille avant d’être réglés.
                        </p>
                        <div className="flex gap-2">
                            <Button variant="secondary" size="sm" onClick={onClose} disabled={importApplying}>Annuler</Button>
                            <Button variant="secondary" size="sm" onClick={() => onApply(approvedConflicts)} disabled={!selectedCount || importApplying || selectedNeedsReview}>
                                Charger dans la grille
                            </Button>
                            <Button variant="primary" size="sm" iconLeft={<Check size={14} />} onClick={() => onSave(approvedConflicts)} disabled={!selectedImportCount || selectedSignalCount > 0 || importApplying || selectedNeedsReview} loading={importApplying}>
                                Enregistrer {selectedImportCount} pointage{selectedImportCount > 1 ? 's' : ''}
                            </Button>
                        </div>
                    </div>
                </>
            )}
        </Modal>
    );
}
