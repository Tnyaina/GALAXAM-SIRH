import { Edit2 } from 'lucide-react';

export default function FilialesList({
    filiales = [],
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                            <th className="px-6 py-4">Code</th>
                            <th className="px-6 py-4">Raison Sociale</th>
                            <th className="px-6 py-4">NIF</th>
                            <th className="px-6 py-4">STAT</th>
                            <th className="px-6 py-4">Seuil Alerte Effectif</th>
                            <th className="px-6 py-4">Statut</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {filiales.length > 0 ? (
                            filiales.map((f) => (
                                <tr key={f.idFiliale} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                    <td className="px-6 py-4 font-mono font-semibold text-brand-500">{f.codeFiliale}</td>
                                    <td className="px-6 py-4 font-bold">{f.raisonSociale}</td>
                                    <td className="px-6 py-4 font-mono text-xs">{f.nif || '-'}</td>
                                    <td className="px-6 py-4 font-mono text-xs">{f.stat || '-'}</td>
                                    <td className="px-6 py-4 text-xs">
                                        <span className="px-2 py-0.5 bg-ink-100 dark:bg-ink-800 text-ink-600 dark:text-ink-300 rounded-sm font-semibold">
                                            {f.seuilEffectifAlerte ?? 19} pers.
                                        </span>
                                    </td>
                                    <td className="px-6 py-4">
                                        {f.estActif ? (
                                            <span className="inline-flex items-center gap-1.5 text-success-500 font-semibold text-xs">
                                                <span className="w-1.5 h-1.5 rounded-full bg-success-500"></span>
                                                Actif
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center gap-1.5 text-ink-400 font-semibold text-xs">
                                                <span className="w-1.5 h-1.5 rounded-full bg-ink-400"></span>
                                                Désactivé
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => onOpenEdit(f)}
                                                className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                title="Modifier"
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                            <button
                                                onClick={() => onToggleStatus(f)}
                                                className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                    f.estActif
                                                        ? 'text-danger-500 hover:bg-danger-500/10'
                                                        : 'text-success-500 hover:bg-success-500/10'
                                                }`}
                                            >
                                                {f.estActif ? "Désactiver" : "Activer"}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="7" className="px-6 py-12 text-center text-ink-400">
                                    Aucune filiale enregistrée.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
