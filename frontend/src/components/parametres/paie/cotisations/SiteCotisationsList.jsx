import { Plus, Trash2 } from 'lucide-react';

export default function SiteCotisationsList({
    siteCotisations = [],
    onOpenCreate,
    onDelete,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-base">Cotisations Rattachées aux Sites d'Exploitation</h3>
                    <p className="text-xs text-ink-400">Associez des règles de cotisation spécifiques selon les sites géographiques.</p>
                </div>
                <button
                    onClick={onOpenCreate}
                    className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <Plus size={16} />
                    Associer un Site
                </button>
            </div>
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                            <th className="px-6 py-4">Nom du Site</th>
                            <th className="px-6 py-4">Filiale</th>
                            <th className="px-6 py-4">Cotisation Rattachée</th>
                            <th className="px-6 py-4">Statut</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {siteCotisations.length > 0 ? (
                            siteCotisations.map((sc) => (
                                <tr key={sc.idSiteCotisation} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                    <td className="px-6 py-4 font-bold">{sc.siteLibelle} ({sc.siteCode})</td>
                                    <td className="px-6 py-4 text-xs font-semibold text-ink-400">{sc.filialeRaisonSociale || '-'}</td>
                                    <td className="px-6 py-4 font-mono font-semibold text-brand-500">{sc.cotisationCode} - {sc.cotisationLibelle}</td>
                                    <td className="px-6 py-4">
                                        {sc.estActif ? (
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
                                        <button
                                            onClick={() => onDelete(sc.idSiteCotisation)}
                                            className="p-1.5 text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                            title="Supprimer l'association"
                                        >
                                            <Trash2 size={16} />
                                        </button>
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="5" className="px-6 py-12 text-center text-ink-400">
                                    Aucune association site - cotisation enregistrée.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
