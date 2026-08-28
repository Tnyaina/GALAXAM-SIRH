import { Clock, Eye, AlertCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueWorkflowsTable({ workflowsList = [], onSelectWorkflowRecap }) {
    const getTypeContratBadgeVariant = (type) => {
        if (!type) return 'default';
        const t = type.toUpperCase();
        if (t.includes('CDI')) return 'success';
        if (t.includes('CDD')) return 'warning';
        if (t.includes('STAGE')) return 'info';
        if (t.includes('PRESTATION') || t.includes('CONSULTANT')) return 'primary';
        return 'default';
    };

    return (
        <div className="space-y-3">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2 pb-2 border-b border-ink-150 dark:border-ink-700">
                <Clock size={14} /> Historique des Événements & Workflows RH ({workflowsList.length})
            </h3>

            {workflowsList.length === 0 ? (
                <p className="text-xs text-ink-400">Aucun événement RH enregistré.</p>
            ) : (
                <div className="overflow-x-auto border border-ink-150 dark:border-ink-700 rounded-none">
                    <table className="w-full text-xs text-left">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase tracking-widest text-[10px]">
                                <th className="px-4 py-3">Événement RH</th>
                                <th className="px-4 py-3">Type & N° Contrat</th>
                                <th className="px-4 py-3">Poste / Affectation</th>
                                <th className="px-4 py-3">Date d'Effet</th>
                                <th className="px-4 py-3">Statut Workflow</th>
                                <th className="px-4 py-3 text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {workflowsList.map((wf) => {
                                const isBrouillon = Boolean(wf.isBrouillon);
                                const isAnnule = Boolean(wf.isAnnule);
                                const motifAnnulation = wf.motif || wf.commentaire;

                                return (
                                    <tr key={wf.idWorkflow} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-semibold">
                                            <div>{wf.libelleTypeEvenement || wf.codeTypeEvenement}</div>
                                            {wf.motif && !isAnnule && (
                                                <div className="text-[10px] text-ink-400 font-normal truncate max-w-[160px]">
                                                    {wf.motif}
                                                </div>
                                            )}
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="flex items-center gap-1.5 flex-wrap">
                                                {wf.libelleTypeContrat && (
                                                    <Badge variant={getTypeContratBadgeVariant(wf.libelleTypeContrat)} size="xs">
                                                        {wf.libelleTypeContrat}
                                                    </Badge>
                                                )}
                                                <span className="font-mono font-bold text-brand-500">
                                                    {wf.numeroContratCible || wf.numeroContratSource || '-'}
                                                </span>
                                            </div>
                                        </td>
                                        <td className="px-4 py-3">
                                            <div className="font-medium">{wf.libelleFonction || '-'}</div>
                                            <div className="text-[11px] text-ink-400">
                                                {wf.libelleSite || '-'}{wf.libelleDepartement ? ` · ${wf.libelleDepartement}` : ''}
                                            </div>
                                        </td>
                                        <td className="px-4 py-3 font-mono">
                                            {fmtDate(wf.dateEffet)}
                                        </td>
                                        <td className="px-4 py-3">
                                            <StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="sm" />
                                            {isAnnule && motifAnnulation && (
                                                <div className="text-[10px] text-danger-500 font-normal mt-0.5 flex items-center gap-1 max-w-[180px] truncate" title={`Motif : ${motifAnnulation}`}>
                                                    <AlertCircle size={10} className="shrink-0" />
                                                    <span className="truncate">Motif : {motifAnnulation}</span>
                                                </div>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            {isBrouillon ? (
                                                <Button
                                                    onClick={() => onSelectWorkflowRecap(wf)}
                                                    variant="ghost"
                                                    size="xs"
                                                >
                                                    Relecture
                                                </Button>
                                            ) : (
                                                <Button
                                                    onClick={() => onSelectWorkflowRecap(wf)}
                                                    variant="ghost"
                                                    size="xs"
                                                    iconLeft={<Eye size={14} />}
                                                >
                                                    Détails
                                                </Button>
                                            )}
                                        </td>
                                    </tr>
                                );
                            })}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
