import { Plus, Edit2 } from 'lucide-react';

export default function CotisationsList({
    cotisations = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-base">Règles & Cotisations Sociales</h3>
                    <p className="text-xs text-ink-400">Taux salariaux et patronaux applicables pour CNaPS, Santé et autres organismes.</p>
                </div>
                <button
                    onClick={onOpenCreate}
                    className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <Plus size={16} />
                    Nouvelle Cotisation
                </button>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                            <th className="px-6 py-4">Code</th>
                            <th className="px-6 py-4">Libellé</th>
                            <th className="px-6 py-4">Taux Salarial</th>
                            <th className="px-6 py-4">Taux Patronal</th>
                            <th className="px-6 py-4">Taux Total</th>
                            <th className="px-6 py-4">Période d'Application</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {cotisations.map((c) => (
                            <tr key={c.idTypeCotisation} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                <td className="px-6 py-4 font-mono font-semibold text-brand-500">{c.code}</td>
                                <td className="px-6 py-4 font-bold">{c.libelle}</td>
                                <td className="px-6 py-4 font-mono text-xs text-sky-500 font-bold">{c.tauxSalarial}%</td>
                                <td className="px-6 py-4 font-mono text-xs text-amber-500 font-bold">{c.tauxPatronal}%</td>
                                <td className="px-6 py-4 font-mono text-xs text-brand-500 font-bold">{(c.tauxSalarial + c.tauxPatronal).toFixed(2)}%</td>
                                <td className="px-6 py-4 text-xs font-mono text-ink-400">
                                    Du {c.dateDebut} {c.dateFin ? `au ${c.dateFin}` : '(Toujours actif)'}
                                </td>
                                <td className="px-6 py-4 text-right">
                                    <button
                                        onClick={() => onOpenEdit(c)}
                                        className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
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
