import { Clock, Building, Edit2, Check } from 'lucide-react';
import { useToleranceArrivee } from '@/features/attendance/hooks/useToleranceArrivee';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';

export default function ToleranceArriveeSiteTab() {
    const {
        sites,
        tolerances,
        loading,
        error,
        editingSiteId,
        editHeure,
        setEditHeure,
        saving,
        startEdit,
        cancelEdit,
        saveTolerance
    } = useToleranceArrivee();

    if (loading) return <SkeletonTable rows={4} cols={5} />;

    return (
        <div className="space-y-6">
            <div className="bg-[var(--color-surface)] p-5 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-start gap-3">
                    <div className="p-2.5 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] shrink-0">
                        <Clock size={22} />
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-[var(--color-fg)]">Heure d'arrivée minimale comptabilisée par site</h3>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 leading-relaxed">
                            Si un employé pointe en avance le matin, son temps de travail commence à partir de l'heure définie ci-dessous. 
                            Les arrivées trop matinales avant cette heure ne génèrent pas d'heures d'avance indues.
                        </p>
                    </div>
                </div>
            </div>

            {error && <Alert type="error" message={error} />}

            <Table>
                <Thead>
                    <Tr>
                        <Th>Code Site</Th>
                        <Th>Libellé du Site</Th>
                        <Th>Horaire Officiel Matin</Th>
                        <Th>Heure minimale comptabilisée</Th>
                        <Th className="text-right">Actions</Th>
                    </Tr>
                </Thead>
                <Tbody>
                    {sites.map((site) => {
                        const tol = tolerances.find(t => t.idSite === site.idSite);
                        const isEditing = editingSiteId === site.idSite;

                        return (
                            <Tr key={site.idSite}>
                                <Td isData className="font-mono font-bold text-xs text-[var(--color-brand-500)]">{site.codeSite}</Td>
                                <Td className="font-semibold text-[var(--color-fg)]">
                                    <div className="flex items-center gap-2">
                                        <Building size={16} className="text-[var(--color-fg-muted)]" />
                                        {site.libelle}
                                    </div>
                                </Td>
                                <Td isData muted className="text-xs font-mono">
                                    {site.heureDebutMatin ? site.heureDebutMatin.substring(0, 5) : '08:00'} - {site.heureFinMatin ? site.heureFinMatin.substring(0, 5) : '12:00'}
                                </Td>
                                <Td>
                                    {isEditing ? (
                                        <div className="flex items-center gap-2 max-w-[140px]">
                                            <Input
                                                type="time"
                                                value={editHeure}
                                                onChange={(e) => setEditHeure(e.target.value)}
                                                className="py-1 text-sm font-semibold"
                                            />
                                        </div>
                                    ) : (
                                        <span className="inline-flex items-center gap-1.5 font-mono text-xs font-bold text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 px-2.5 py-1">
                                            <Clock size={13} />
                                            {tol?.heurePlafond ? tol.heurePlafond.substring(0, 5) : 'Non défini (théorique)'}
                                        </span>
                                    )}
                                </Td>
                                <Td className="text-right">
                                    {isEditing ? (
                                        <div className="flex justify-end gap-1.5">
                                            <Button
                                                size="sm"
                                                variant="primary"
                                                className="shrink-0 whitespace-nowrap"
                                                loading={saving}
                                                onClick={() => saveTolerance(site.idSite)}
                                            >
                                                <Check size={14} /> Enregistrer
                                            </Button>
                                            <Button
                                                size="sm"
                                                variant="ghost"
                                                className="shrink-0 whitespace-nowrap"
                                                onClick={cancelEdit}
                                            >
                                                Annuler
                                            </Button>
                                        </div>
                                    ) : (
                                        <button
                                            onClick={() => startEdit(site, tol)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors inline-flex items-center gap-1 text-xs font-semibold"
                                            title="Modifier la tolérance"
                                            aria-label={`Modifier tolérance pour ${site.libelle}`}
                                        >
                                            <Edit2 size={14} /> Modifier
                                        </button>
                                    )}
                                </Td>
                            </Tr>
                        );
                    })}
                </Tbody>
            </Table>
        </div>
    );
}

