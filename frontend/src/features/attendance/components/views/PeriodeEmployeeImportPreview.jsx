import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';

export default function PeriodeEmployeeImportPreview({
    importPreview,
    importSelections = {},
    setImportSelections,
    grilleRows = [],
    onClose,
    onApply
}) {
    if (!importPreview) return null;

    const formatHours = (obj, imported) => imported
        ? [obj?.heureEntreeMatin, obj?.heureSortieMatin, obj?.heureEntreeAm, obj?.heureSortieAm].map(x => x || '—').join(' · ')
        : [obj?.entree, obj?.sortiePause, obj?.retourPause, obj?.sortie].map(x => x || '—').join(' · ');

    const hasSelection = Object.values(importSelections).some(Boolean);

    return (
        <section
            role="dialog"
            aria-label="Aperçu de l'import de pointage"
            className="border border-[var(--color-brand-600)] bg-[var(--color-surface)] p-4 space-y-3 shadow-sm"
        >
            <div className="flex items-start justify-between gap-3">
                <div>
                    <h3 className="font-bold text-sm text-[var(--color-fg)]">Aperçu de l’import</h3>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                        Choisissez les journées à charger dans la grille. Aucune donnée n’est enregistrée maintenant.
                    </p>
                </div>
                <Button
                    variant="ghost"
                    size="xs"
                    onClick={onClose}
                >
                    Fermer
                </Button>
            </div>

            <div className="max-h-80 overflow-auto border border-[var(--color-border-subtle)]">
                <Table density="compact" className="w-full text-xs text-left">
                    <Thead>
                        <Tr className="sticky top-0 bg-[var(--color-bg-subtle)] text-[10px] uppercase font-bold text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)]">
                            <Th className="p-2 w-12 text-center">Choix</Th>
                            <Th className="p-2 w-28">Date</Th>
                            <Th className="p-2">Fichier importé</Th>
                            <Th className="p-2">Déjà enregistré</Th>
                            <Th className="p-2">Contrôle</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {(importPreview.lines || []).map(line => {
                            const p = line.proposed;
                            const e = line.existing;
                            const row = grilleRows.find(r => r.datePointage === line.date);
                            const isSelectable = ['IMPORT', 'SIGNAL'].includes(line.decision) &&
                                row?.canCorrect !== false &&
                                !Boolean(e?.congeApprouve && line.eventCode);

                            return (
                                <Tr key={line.ligne} className="border-b border-[var(--color-border-subtle)] align-top hover:bg-[var(--color-bg-subtle)]/30">
                                    <Td className="p-2 text-center">
                                        <input
                                            type="checkbox"
                                            checked={Boolean(importSelections[line.ligne])}
                                            disabled={!isSelectable}
                                            onChange={event => setImportSelections(prev => ({ ...prev, [line.ligne]: event.target.checked }))}
                                            className="cursor-pointer accent-[var(--color-brand-600)]"
                                        />
                                    </Td>
                                    <Td className="p-2 whitespace-nowrap font-semibold">
                                        {line.date}
                                        {line.eventCode && (
                                            <span className="ml-1.5 px-1 py-0.2 border border-[var(--color-warning-600)] text-[10px] text-[var(--color-warning-700)] bg-[var(--color-warning-50)]">
                                                {line.eventCode === 'C' ? 'Congé' : 'Absence'}
                                            </span>
                                        )}
                                    </Td>
                                    <Td className="p-2 font-mono">
                                        {p && line.decision === 'IMPORT' ? formatHours(p, true) : 'Aucune heure'}
                                        {p && line.decision === 'IMPORT' && p.totalHeures != null && (
                                            <div className="mt-1 font-sans text-[11px] text-[var(--color-fg)]">
                                                Calcul : <strong>{formatHeuresMinutes(p.totalHeures)}</strong> · {p.libelleStatut || p.codeStatut}
                                            </div>
                                        )}
                                        {p && ['IMPORT', 'SIGNAL'].includes(line.decision) && (
                                            <div className="mt-1 font-sans text-[11px] text-[var(--color-fg-muted)]">
                                                {(line.allowances || []).filter(ind => p.dynamicIndemnites?.[String(ind.idIndemnite)]?.accordee)
                                                    .map(ind => {
                                                        const selected = p.dynamicIndemnites[String(ind.idIndemnite)];
                                                        return `${ind.code} ${selected.fractionJour ?? 1}`;
                                                    }).join(' · ') || 'Aucune indemnité'}
                                            </div>
                                        )}
                                    </Td>
                                    <Td className="p-2 font-mono">
                                        {line.conflict ? formatHours(e, false) : e?.statut || '—'}
                                        {line.conflict && (
                                            <div className="mt-1 font-sans text-[11px] text-[var(--color-fg-muted)]">
                                                {Object.entries(e?.indemnites || {}).filter(([, item]) => item?.accordee).map(([code, item]) => `${code} ${item.quantite ?? 1}`).join(' · ') || 'Aucune indemnité'}
                                            </div>
                                        )}
                                    </Td>
                                    <Td className="p-2">
                                        {line.errors?.map((message, index) => (
                                            <div key={index} className="text-[var(--color-danger-600)] font-medium text-[11px]">{message}</div>
                                        ))}
                                        {line.warnings?.map((message, index) => (
                                            <div key={index} className="text-[var(--color-warning-700)] text-[11px]">{message}</div>
                                        ))}
                                        {['IMPORT', 'SIGNAL'].includes(line.decision) && !line.conflict && !line.errors?.length && (
                                            <Badge variant="success" size="sm">
                                                {line.decision === 'SIGNAL' ? 'À régulariser' : 'Prête'}
                                            </Badge>
                                        )}
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            </div>

            <div className="flex justify-end gap-2">
                <Button
                    variant="secondary"
                    size="sm"
                    onClick={onClose}
                >
                    Annuler
                </Button>
                <Button
                    variant="primary"
                    size="sm"
                    onClick={onApply}
                    disabled={!hasSelection}
                >
                    Charger les journées sélectionnées
                </Button>
            </div>
        </section>
    );
}
