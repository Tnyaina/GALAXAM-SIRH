import { Plus, Edit2, UserCheck } from 'lucide-react';
import Button from '@/components/ui/Button';

export default function TypesIntervenantList({
    typesIntervenant = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                        <UserCheck size={18} className="text-brand-500" />
                        Types d'Intervenant ({typesIntervenant.length})
                    </h3>
                    <p className="text-xs text-ink-400">Salarié national, Expatrié, Consultant, Stagiaire...</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouveau Type
                </Button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                    <thead>
                        <tr className="border-b border-ink-150 dark:border-ink-800 text-xs font-bold text-ink-500 uppercase tracking-wider bg-ink-50/50 dark:bg-ink-950/40">
                            <th className="py-3 px-4">Code</th>
                            <th className="py-3 px-4">Libellé</th>
                            <th className="py-3 px-4 text-center">IRSA</th>
                            <th className="py-3 px-4 text-center">ISI 5%</th>
                            <th className="py-3 px-4 text-center">CNaPS</th>
                            <th className="py-3 px-4 text-center">Santé</th>
                            <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-150 dark:divide-ink-800">
                        {typesIntervenant.map(ti => (
                            <tr key={ti.idTypeIntervenant} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 transition-colors">
                                <td className="py-3 px-4 font-mono font-bold text-brand-500">{ti.code}</td>
                                <td className="py-3 px-4 font-medium text-ink-900 dark:text-ink-100">{ti.libelle}</td>
                                <td className="py-3 px-4 text-center">{ti.soumisIrsa ? '✅' : '❌'}</td>
                                <td className="py-3 px-4 text-center">{ti.soumisIsi5 ? '✅' : '❌'}</td>
                                <td className="py-3 px-4 text-center">{ti.soumisCnaps ? '✅' : '❌'}</td>
                                <td className="py-3 px-4 text-center">{ti.soumisSante ? '✅' : '❌'}</td>
                                <td className="py-3 px-4 text-right">
                                    <button
                                        onClick={() => onOpenEdit(ti)}
                                        className="p-1 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm text-ink-400 hover:text-ink-900 dark:hover:text-white cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={15} />
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
