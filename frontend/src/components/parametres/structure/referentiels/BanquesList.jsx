import { Plus, Edit2, Landmark } from 'lucide-react';
import Button from '@/components/ui/Button';

export default function BanquesList({
    banques = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                        <Landmark size={18} className="text-brand-500" />
                        Établissements Bancaires ({banques.length})
                    </h3>
                    <p className="text-xs text-ink-400">BOA, BNI, BMOI, Société Générale, AccèsBanque...</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle Banque
                </Button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                    <thead>
                        <tr className="border-b border-ink-150 dark:border-ink-800 text-xs font-bold text-ink-500 uppercase tracking-wider bg-ink-50/50 dark:bg-ink-950/40">
                            <th className="py-3 px-4">Code Banque</th>
                            <th className="py-3 px-4">Nom de la Banque</th>
                            <th className="py-3 px-4">Code SWIFT / BIC</th>
                            <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-150 dark:divide-ink-800">
                        {banques.map(b => (
                            <tr key={b.idBanque} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 transition-colors">
                                <td className="py-3 px-4 font-mono font-bold text-brand-500">{b.codeBanque}</td>
                                <td className="py-3 px-4 font-medium text-ink-900 dark:text-ink-100">{b.nomBanque}</td>
                                <td className="py-3 px-4 font-mono text-ink-500 text-xs">{b.codeSwift || '—'}</td>
                                <td className="py-3 px-4 text-right">
                                    <button
                                        onClick={() => onOpenEdit(b)}
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
