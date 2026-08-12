export default function SiteCotisationFormModal({
    isOpen,
    onClose,
    onSubmit,
    formData,
    setFormData,
    sites = [],
    cotisations = [],
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                    Associer une Cotisation à un Site
                </div>
                <form onSubmit={onSubmit}>
                    <div className="p-6 space-y-4">
                        {actionError && (
                            <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                {actionError}
                            </div>
                        )}
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Site d'Exploitation</label>
                            <select
                                value={formData.idSite}
                                onChange={(e) => setFormData({ ...formData, idSite: e.target.value })}
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            >
                                <option value="">-- Sélectionner un site --</option>
                                {sites.map((s) => (
                                    <option key={s.idSite} value={s.idSite}>
                                        {s.libelle} ({s.codeSite})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Type de Cotisation</label>
                            <select
                                value={formData.idTypeCotisation}
                                onChange={(e) => setFormData({ ...formData, idTypeCotisation: e.target.value })}
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            >
                                <option value="">-- Sélectionner une cotisation --</option>
                                {cotisations.map((c) => (
                                    <option key={c.idTypeCotisation} value={c.idTypeCotisation}>
                                        {c.code} - {c.libelle} (S: {c.tauxSalarial}%, P: {c.tauxPatronal}%)
                                    </option>
                                ))}
                            </select>
                        </div>
                    </div>
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                        >
                            Annuler
                        </button>
                        <button
                            type="submit"
                            className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            Associer
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
