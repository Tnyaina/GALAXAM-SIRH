import { Plus, Edit2 } from 'lucide-react';

export default function BaremeIrsaList({
    tranchesIrsa = [],
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-base">Barème IRSA (Impôt sur les Revenus Salariaux & Assimilés)</h3>
                    <p className="text-xs text-ink-400">Tranches d'imposition progressives selon la législation fiscale malagasy.</p>
                </div>
                <button
                    onClick={onOpenCreate}
                    className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <Plus size={16} />
                    Nouvelle Tranche IRSA
                </button>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                            <th className="px-6 py-4">Tranche N°</th>
                            <th className="px-6 py-4">Seuil Minimum (Ar)</th>
                            <th className="px-6 py-4">Seuil Maximum (Ar)</th>
                            <th className="px-6 py-4">Taux d'imposition</th>
                            <th className="px-6 py-4">Statut / Validité</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {tranchesIrsa.map((t) => (
                            <tr key={t.idTranche} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                <td className="px-6 py-4 font-mono font-bold text-brand-500">Tranche {t.numeroTranche}</td>
                                <td className="px-6 py-4 font-mono text-xs font-semibold">{t.trancheMin ? t.trancheMin.toLocaleString('fr-FR') : '0'} Ar</td>
                                <td className="px-6 py-4 font-mono text-xs font-semibold">{t.trancheMax ? `${t.trancheMax.toLocaleString('fr-FR')} Ar` : 'Illimité'}</td>
                                <td className="px-6 py-4 font-mono font-bold text-xs text-danger-500">{t.taux}%</td>
                                <td className="px-6 py-4 text-xs font-mono text-ink-400">
                                    Valide depuis {t.dateDebut} {t.dateFin ? `jusqu'au ${t.dateFin}` : ''}
                                </td>
                                <td className="px-6 py-4 text-right">
                                    <button
                                        onClick={() => onOpenEdit(t)}
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
