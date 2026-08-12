import { Edit2 } from 'lucide-react';

export default function SitesList({
    sites = [],
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                            <th className="px-6 py-4">Code Site</th>
                            <th className="px-6 py-4">Nom du Site</th>
                            <th className="px-6 py-4">Filiale Rattachement</th>
                            <th className="px-6 py-4">Régime Horaire</th>
                            <th className="px-6 py-4">Horaires Matin / Après-midi</th>
                            <th className="px-6 py-4">Statut</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {sites.length > 0 ? (
                            sites.map((s) => (
                                <tr key={s.idSite} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                    <td className="px-6 py-4 font-mono font-semibold text-brand-500">{s.codeSite}</td>
                                    <td className="px-6 py-4 font-bold">{s.libelle}</td>
                                    <td className="px-6 py-4 font-medium text-xs">
                                        {s.filialeRaisonSociale ? (
                                            <span className="px-2 py-0.5 bg-brand-500/10 text-brand-500 rounded-sm">
                                                {s.filialeRaisonSociale}
                                            </span>
                                        ) : (
                                            <span className="text-ink-300">-</span>
                                        )}
                                    </td>
                                    <td className="px-6 py-4">
                                        {s.horaireContinu ? (
                                            <span className="text-xs px-2 py-0.5 bg-sky-500/10 text-sky-500 font-semibold rounded-sm">
                                                Horaire Continu
                                            </span>
                                        ) : (
                                            <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 font-semibold rounded-sm">
                                                Discontinu (Pause)
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-6 py-4 text-xs font-mono text-ink-500">
                                        <div>Matin : {s.heureDebutMatin || '--'} - {s.heureFinMatin || '--'}</div>
                                        <div>A-Midi : {s.heureDebutApresMidi || '--'} - {s.heureFinApresMidi || '--'}</div>
                                    </td>
                                    <td className="px-6 py-4">
                                        {s.estActif ? (
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
                                                onClick={() => onOpenEdit(s)}
                                                className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                title="Modifier"
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                            <button
                                                onClick={() => onToggleStatus(s)}
                                                className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                    s.estActif
                                                        ? 'text-danger-500 hover:bg-danger-500/10'
                                                        : 'text-success-500 hover:bg-success-500/10'
                                                }`}
                                            >
                                                {s.estActif ? "Désactiver" : "Activer"}
                                            </button>
                                        </div>
                                    </td>
                                </tr>
                            ))
                        ) : (
                            <tr>
                                <td colSpan="7" className="px-6 py-12 text-center text-ink-400">
                                    Aucun site d'exploitation enregistré.
                                </td>
                            </tr>
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
