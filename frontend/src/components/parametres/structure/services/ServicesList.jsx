import { Layers, Plus, Edit2, Building } from 'lucide-react';

export default function ServicesList({
    selectedDepartement,
    services = [],
    loadingServices = false,
    onOpenCreateService,
    onOpenEditService,
    onToggleServiceStatus,
}) {
    return (
        <div className="lg:col-span-2 bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
            {selectedDepartement ? (
                <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row justify-between sm:items-center pb-4 border-b border-ink-150 dark:border-ink-800 gap-3">
                        <div>
                            <h4 className="font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                                <Layers size={18} className="text-brand-500" />
                                Services rattachés à : <span className="text-brand-500">{selectedDepartement.libelle}</span>
                            </h4>
                            <p className="text-xs text-ink-400">Code Département : {selectedDepartement.codeDepartement}</p>
                        </div>
                        <button
                            onClick={onOpenCreateService}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                        >
                            <Plus size={16} />
                            Ajouter un Service
                        </button>
                    </div>

                    {loadingServices ? (
                        <div className="flex justify-center items-center py-12">
                            <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
                        </div>
                    ) : (
                        <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                            <table className="w-full border-collapse text-left text-sm">
                                <thead>
                                    <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                        <th className="px-4 py-3">Code Service</th>
                                        <th className="px-4 py-3">Nom du Service</th>
                                        <th className="px-4 py-3">Statut</th>
                                        <th className="px-4 py-3 text-right">Actions</th>
                                    </tr>
                                </thead>
                                <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                    {services.length > 0 ? (
                                        services.map((srv) => (
                                            <tr key={srv.idService} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                                <td className="px-4 py-3 font-mono font-semibold text-brand-500">{srv.codeService}</td>
                                                <td className="px-4 py-3 font-medium">{srv.libelle}</td>
                                                <td className="px-4 py-3">
                                                    {srv.estActif ? (
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
                                                <td className="px-4 py-3 text-right">
                                                    <div className="flex justify-end gap-2">
                                                        <button
                                                            onClick={() => onOpenEditService(srv)}
                                                            className="p-1 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                            title="Modifier"
                                                        >
                                                            <Edit2 size={15} />
                                                        </button>
                                                        <button
                                                            onClick={() => onToggleServiceStatus(srv)}
                                                            className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                                srv.estActif
                                                                    ? 'text-danger-500 hover:bg-danger-500/10'
                                                                    : 'text-success-500 hover:bg-success-500/10'
                                                            }`}
                                                        >
                                                            {srv.estActif ? "Désactiver" : "Activer"}
                                                        </button>
                                                    </div>
                                                </td>
                                            </tr>
                                        ))
                                    ) : (
                                        <tr>
                                            <td colSpan="4" className="px-4 py-8 text-center text-ink-400">
                                                Aucun service rattaché à ce département.
                                            </td>
                                        </tr>
                                    )}
                                </tbody>
                            </table>
                        </div>
                    )}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-20 text-ink-400">
                    <Building size={36} className="mb-2 opacity-50" />
                    <p className="text-sm">Veuillez sélectionner un département à gauche.</p>
                </div>
            )}
        </div>
    );
}
