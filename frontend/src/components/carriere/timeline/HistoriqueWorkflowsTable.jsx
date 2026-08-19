import { Clock, Eye } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate } from '@/utils/carriereUtils';

export default function HistoriqueWorkflowsTable({ workflowsList = [], onSelectWorkflowRecap }) {
    const getStatutBadge = (statutCode) => {
        switch (statutCode) {
            case 'ACTIF':
            case 'ACTIVE':
            case 'VALIDE':
                return <Badge variant="success" size="sm">{statutCode}</Badge>;
            case 'BROUILLON':
            case 'PLANIFIEE':
                return <Badge variant="warning" size="sm">{statutCode}</Badge>;
            case 'ANNULE':
            case 'ANNULEE':
            case 'ROMPU':
                return <Badge variant="danger" size="sm">{statutCode}</Badge>;
            case 'CLOTURE':
            case 'HISTORISEE':
                return <Badge variant="default" size="sm">{statutCode}</Badge>;
            default:
                return <Badge variant="primary" size="sm">{statutCode}</Badge>;
        }
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
                                <th className="px-4 py-3">Contrat Cible</th>
                                <th className="px-4 py-3">Poste / Fonction</th>
                                <th className="px-4 py-3">Date d'Effet</th>
                                <th className="px-4 py-3">Statut Workflow</th>
                                <th className="px-4 py-3 text-right">Action</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {workflowsList.map((wf) => (
                                <tr key={wf.idWorkflow} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                    <td className="px-4 py-3 font-semibold">
                                        {wf.libelleTypeEvenement || wf.codeTypeEvenement}
                                    </td>
                                    <td className="px-4 py-3 font-mono font-bold text-brand-500">
                                        {wf.numeroContratCible || '-'}
                                    </td>
                                    <td className="px-4 py-3">
                                        {wf.libelleFonction || '-'} ({wf.libelleSite || '-'})
                                    </td>
                                    <td className="px-4 py-3 font-mono">
                                        {fmtDate(wf.dateEffet)}
                                    </td>
                                    <td className="px-4 py-3">
                                        {getStatutBadge(wf.codeStatutWorkflow)}
                                    </td>
                                    <td className="px-4 py-3 text-right">
                                        {wf.codeStatutWorkflow === 'BROUILLON' ? (
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
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
        </div>
    );
}
