import { Clock, AlertCircle, ChevronRight } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate } from '@/utils/dateUtils';

export default function HistoriqueWorkflowsTable({ workflowsList = [], onSelectWorkflowRecap }) {
    // Tous les types de contrats ont un style neutre uniforme (pas d'arc-en-ciel)
    const getTypeContratBadgeVariant = () => 'default';

    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex items-center gap-2">
                    <Clock size={14} /> Historique des Événements &amp; Workflows RH ({workflowsList.length})
                </h3>
                <span className="text-[11px] text-[var(--color-fg-subtle)]">Cliquez sur une ligne pour afficher la fiche récapitulative</span>
            </div>

            {workflowsList.length === 0 ? (
                <EmptyState
                    icon={Clock}
                    title="Aucun événement RH"
                    description="Aucun événement RH enregistré pour ce collaborateur."
                    compact
                />
            ) : (
                <Table>
                    <Thead>
                        <Tr>
                            <Th>Événement RH</Th>
                            <Th>Type &amp; N° Contrat</Th>
                            <Th>Poste / Affectation</Th>
                            <Th>Date d'Effet</Th>
                            <Th>Statut Workflow</Th>
                            <Th align="right">Action</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {workflowsList.map((wf) => {
                            const isBrouillon = Boolean(wf.isBrouillon);
                            const isAnnule = Boolean(wf.isAnnule);
                            const motifAnnulation = wf.motif || wf.commentaire;

                            return (
                                <Tr 
                                    key={wf.idWorkflow} 
                                    onClick={() => onSelectWorkflowRecap(wf)}
                                    className="cursor-pointer group"
                                    title="Cliquer pour afficher le récapitulatif détaillé"
                                >
                                    <Td>
                                        <div className="font-semibold text-[var(--color-fg)] group-hover:text-[var(--color-brand-600)] transition-colors">
                                            {wf.libelleTypeEvenement || wf.codeTypeEvenement}
                                        </div>
                                        {wf.motif && !isAnnule && (
                                            <div className="text-[10px] text-[var(--color-fg-subtle)] font-normal truncate max-w-[200px]">
                                                {wf.motif}
                                            </div>
                                        )}
                                    </Td>
                                    <Td>
                                        <div className="flex items-center gap-1.5 flex-wrap">
                                            {wf.libelleTypeContrat && (
                                                <Badge variant={getTypeContratBadgeVariant(wf.libelleTypeContrat)} size="xs">
                                                    {wf.libelleTypeContrat}
                                                </Badge>
                                            )}
                                            <span className="font-mono font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                                {wf.numeroContratCible || wf.numeroContratSource || '-'}
                                            </span>
                                        </div>
                                    </Td>
                                    <Td>
                                        <div className="font-medium text-[var(--color-fg)]">{wf.libelleFonction || '-'}</div>
                                        <div className="text-[11px] text-[var(--color-fg-subtle)]">
                                            {wf.libelleSite || '-'}{wf.libelleDepartement ? ` · ${wf.libelleDepartement}` : ''}
                                        </div>
                                    </Td>
                                    <Td className="font-mono text-[var(--color-fg-muted)]">
                                        {fmtDate(wf.dateEffet)}
                                    </Td>
                                    <Td>
                                        <StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="sm" />
                                        {isAnnule && motifAnnulation && (
                                            <div className="text-[10px] text-[var(--color-danger-500)] font-normal mt-0.5 flex items-center gap-1 max-w-[180px] truncate" title={`Motif : ${motifAnnulation}`}>
                                                <AlertCircle size={10} className="shrink-0" />
                                                <span className="truncate">Motif : {motifAnnulation}</span>
                                            </div>
                                        )}
                                    </Td>
                                    <Td align="right">
                                        {isBrouillon ? (
                                            <Button
                                                onClick={(e) => {
                                                    e.stopPropagation();
                                                    onSelectWorkflowRecap(wf);
                                                }}
                                                variant="outline"
                                                size="xs"
                                                className="border-[var(--color-warning-500)]/30 text-[var(--color-warning-600)] hover:bg-[var(--color-warning-500)]/10"
                                            >
                                                Relecture
                                            </Button>
                                        ) : (
                                            <span className="text-xs font-semibold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] group-hover:underline inline-flex items-center gap-0.5">
                                                Détails <ChevronRight size={14} className="group-hover:translate-x-0.5 transition-transform" />
                                            </span>
                                        )}
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}
