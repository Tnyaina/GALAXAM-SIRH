import { useMemo } from 'react';
import { CalendarDays, Check, FileSpreadsheet, LoaderCircle } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import TimeInput from '@/components/ui/forms/TimeInput';
import Badge from '@/components/ui/data-display/Badge';
import { Table } from '@/components/ui/data-display/Table';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';

const TIME_FIELDS = [
    ['heureEntreeMatin', 'Entrée'],
    ['heureSortieMatin', 'Sortie'],
    ['heureEntreeAm', 'Retour'],
    ['heureSortieAm', 'Fin']
];

const getDayMinutes = (p) => {
    const minutes = value => {
        if (!value || !/^\d{1,2}:\d{2}$/.test(value)) return null;
        const [hours, mins] = value.split(':').map(Number);
        return hours * 60 + mins;
    };
    const start = minutes(p?.heureEntreeMatin);
    const end = minutes(p?.heureSortieAm);
    if (p?.horaireContinu) return start != null && end != null && end >= start ? end - start : null;
    const morningEnd = minutes(p?.heureSortieMatin);
    const afternoonStart = minutes(p?.heureEntreeAm);
    if ([start, morningEnd, afternoonStart, end].every(x => x != null) && morningEnd >= start && end >= afternoonStart) {
        return morningEnd - start + end - afternoonStart;
    }
    return null;
};

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
    onApply
}) {
    const lines = useMemo(() => importPreview?.lines || [], [importPreview]);
    const hasSelection = Object.values(importSelections).some(Boolean);
    const selectedCount = lines.filter(line => importSelections[line.ligne] && ['IMPORT', 'SIGNAL'].includes(line.decision)).length;
    const readyCount = lines.filter(line => line.decision === 'IMPORT' && !line.errors?.length).length;
    const signalCount = lines.filter(line => line.decision === 'SIGNAL').length;
    const restCount = lines.filter(line => line.decision === 'SKIP').length;
    const groups = useMemo(() => {
        const result = [];
        lines.forEach(line => {
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
    }, [lines]);

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
            className="!max-w-[90vw] w-[90vw] max-h-[92vh]"
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
                            <Badge variant="default" size="sm">{restCount} repos / ignorées</Badge>
                        </div>
                    </div>

                    <div className="mb-2 flex items-center justify-between gap-3">
                        <p className="text-xs text-[var(--color-fg-muted)]">Modifiez les horaires et indemnités avant de charger. Les changements restent dans la grille et ne sont pas encore enregistrés.</p>
                        <label className="inline-flex shrink-0 cursor-pointer items-center gap-2 text-xs font-semibold">
                            <input type="checkbox" checked={lines.length > 0 && lines.filter(line => ['IMPORT', 'SIGNAL'].includes(line.decision) && !line.errors?.length).every(line => importSelections[line.ligne])} onChange={event => toggleAll(event.target.checked)} className="accent-[var(--color-brand-600)]" />
                            Tout sélectionner
                        </label>
                    </div>

                    <Table maxHeight="58vh" className="w-full" tableClassName="w-full min-w-[1120px] border-collapse text-left text-xs" aria-label="Journées importées et valeurs modifiables">
                            <thead className="sticky top-0 z-10 bg-[var(--color-bg-subtle)] text-[10px] font-bold uppercase text-[var(--color-fg-muted)] shadow-sm">
                                <tr>
                                    <th className="w-10 p-2 text-center">Choix</th>
                                    <th className="w-28 p-2">Date</th>
                                    <th className="w-[410px] p-2">Horaires importés / modifiables</th>
                                    <th className="w-24 p-2">Durée</th>
                                    <th className="w-40 p-2">Déjà enregistré</th>
                                    <th className="min-w-[300px] p-2">Indemnités du fichier</th>
                                    <th className="min-w-[160px] p-2">Contrôle</th>
                                </tr>
                            </thead>
                            {groups.map(group => (
                                <tbody key={group.key}>
                                    <tr className="bg-[var(--color-bg-subtle)]/70">
                                        <td colSpan={7} className="border-y border-[var(--color-border-subtle)] px-3 py-1.5 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">
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
                                        const minutes = getDayMinutes(proposed || {});
                                        const updateTime = (field, value) => updateLine(line.ligne, current => ({
                                            ...current,
                                            proposed: { ...current.proposed, [field]: value, totalHeures: getDayMinutes({ ...current.proposed, [field]: value }) == null ? current.proposed.totalHeures : getDayMinutes({ ...current.proposed, [field]: value }) / 60 }
                                        }));
                                        const updateAllowance = (id, value) => updateLine(line.ligne, current => ({
                                            ...current,
                                            proposed: { ...current.proposed, dynamicIndemnites: { ...current.proposed.dynamicIndemnites, [String(id)]: value } }
                                        }));
                                        return (
                                            <tr key={line.ligne} className={`border-b border-[var(--color-border-subtle)] align-middle ${selected ? 'bg-[var(--color-brand-500)]/5' : 'bg-[var(--color-surface)]'} hover:bg-[var(--color-bg-subtle)]/40`}>
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
                                                    ) : <span className="text-[var(--color-fg-muted)]">{proposed ? 'Signalement sans chargement direct' : 'Aucune heure'}</span>}
                                                </td>
                                                <td className="whitespace-nowrap p-2 font-mono font-semibold text-[var(--color-fg)]">
                                                    {minutes != null ? formatHeuresMinutes(minutes / 60) : proposed?.totalHeures != null ? formatHeuresMinutes(proposed.totalHeures) : '—'}
                                                    {proposed?.libelleStatut && <div className="mt-1 font-sans text-[10px] font-normal text-[var(--color-fg-muted)]">{proposed.libelleStatut}</div>}
                                                </td>
                                                <td className="p-2 text-[11px] text-[var(--color-fg-muted)]">
                                                    {line.conflict ? `${existing?.statut || 'Pointage existant'}${existing?.heureEntreeMatin ? ` · ${existing.heureEntreeMatin}–${existing.heureSortieAm || '—'}` : ''}` : existing?.statut || '—'}
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
                                                    {line.errors?.map((message, index) => <div key={index} className="mb-1 text-[10px] font-medium text-[var(--color-danger-600)]">{message}</div>)}
                                                    {line.warnings?.map((message, index) => <div key={index} className="mb-1 text-[10px] text-[var(--color-warning-700)]">{message}</div>)}
                                                    {['IMPORT', 'SIGNAL'].includes(line.decision) && !line.conflict && !line.errors?.length && <Badge variant={line.decision === 'SIGNAL' ? 'warning' : 'success'} size="sm">{line.decision === 'SIGNAL' ? 'À régulariser' : 'Prête'}</Badge>}
                                                    {line.conflict && <Badge variant="warning" size="sm">À vérifier</Badge>}
                                                    {!selectable && !line.errors?.length && !line.conflict && <span className="text-[10px] text-[var(--color-fg-muted)]">Non chargée</span>}
                                                </td>
                                            </tr>
                                        );
                                    })}
                                </tbody>
                            ))}
                    </Table>

                    <div className="mt-3 flex flex-wrap items-center justify-between gap-3 border-t border-[var(--color-border-subtle)] pt-3">
                        <p className="max-w-2xl text-xs text-[var(--color-fg-muted)]">
                            <strong className="text-[var(--color-fg)]">{selectedCount} journée(s) sélectionnée(s).</strong> Elles seront chargées dans la grille et resteront à enregistrer. Les journées à régulariser se traitent depuis la grille.
                        </p>
                        <div className="flex gap-2">
                            <Button variant="secondary" size="sm" onClick={onClose} disabled={importApplying}>Annuler</Button>
                            <Button variant="primary" size="sm" iconLeft={<Check size={14} />} onClick={onApply} disabled={!hasSelection || importApplying} loading={importApplying}>
                                Charger {selectedCount} journée{selectedCount > 1 ? 's' : ''} dans la grille
                            </Button>
                        </div>
                    </div>
                </>
            )}
        </Modal>
    );
}
