export default function IrsaTrancheModal({
    isOpen,
    onClose,
    onSubmit,
    editingIrsa,
    formData,
    setFormData,
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                    {editingIrsa ? "Modifier la Tranche IRSA" : "Nouvelle Tranche IRSA"}
                </div>
                <form onSubmit={onSubmit}>
                    <div className="p-6 space-y-4">
                        {actionError && (
                            <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                {actionError}
                            </div>
                        )}
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Numéro de Tranche</label>
                            <input
                                type="number"
                                value={formData.numeroTranche}
                                onChange={(e) => setFormData({ ...formData, numeroTranche: parseInt(e.target.value) || 1 })}
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            />
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Montant Min (Ar)</label>
                                <input
                                    type="number"
                                    value={formData.trancheMin}
                                    onChange={(e) => setFormData({ ...formData, trancheMin: parseFloat(e.target.value) || 0 })}
                                    className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Montant Max (Ar)</label>
                                <input
                                    type="number"
                                    value={formData.trancheMax}
                                    onChange={(e) => setFormData({ ...formData, trancheMax: e.target.value })}
                                    placeholder="Vide si illimité"
                                    className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Taux IRSA (%)</label>
                            <input
                                type="number"
                                step="0.01"
                                value={formData.taux}
                                onChange={(e) => setFormData({ ...formData, taux: parseFloat(e.target.value) || 0 })}
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            />
                        </div>
                        <div className="grid grid-cols-2 gap-4">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Date Début Effectif</label>
                                <input
                                    type="date"
                                    value={formData.dateDebut}
                                    onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                                    className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Date Fin Effectif (Optionnel)</label>
                                <input
                                    type="date"
                                    value={formData.dateFin}
                                    onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                                    className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
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
                            Enregistrer
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}
