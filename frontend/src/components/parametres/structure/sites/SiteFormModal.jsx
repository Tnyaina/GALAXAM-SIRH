export default function SiteFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedSite,
    formData,
    setFormData,
    filiales = [],
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                    {selectedSite ? "Modifier le Site" : "Nouveau Site"}
                </div>
                <form onSubmit={onSubmit}>
                    <div className="p-6 space-y-4 max-h-[450px] overflow-y-auto">
                        {actionError && (
                            <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                {actionError}
                            </div>
                        )}
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Code Site</label>
                            <input
                                type="text"
                                value={formData.codeSite}
                                onChange={(e) => setFormData({ ...formData, codeSite: e.target.value })}
                                disabled={!!selectedSite}
                                placeholder="Ex: TNR, TMM"
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                            />
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Nom / Libellé</label>
                            <input
                                type="text"
                                value={formData.libelle}
                                onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                placeholder="Ex: Antananarivo - Siège"
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            />
                        </div>
                        <div className="space-y-1">
                            <label className="text-xs font-semibold text-ink-400 uppercase">Filiale de Rattachement</label>
                            <select
                                value={formData.idFilialeRattachement}
                                onChange={(e) => setFormData({ ...formData, idFilialeRattachement: e.target.value })}
                                className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                            >
                                <option value="">Sélectionner une filiale</option>
                                {filiales.map((f) => (
                                    <option key={f.idFiliale} value={f.idFiliale}>
                                        {f.raisonSociale} ({f.codeFiliale})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <div className="flex items-center gap-2 pt-1">
                            <input
                                type="checkbox"
                                id="horaireContinu"
                                checked={formData.horaireContinu}
                                onChange={(e) => setFormData({ ...formData, horaireContinu: e.target.checked })}
                                className="w-4 h-4 text-brand-500 border-ink-300 dark:border-ink-700 rounded-sm focus:ring-brand-500"
                            />
                            <label htmlFor="horaireContinu" className="text-sm text-ink-900 dark:text-white font-medium cursor-pointer">
                                Régime Horaire Continu
                            </label>
                        </div>
                        <div className="grid grid-cols-2 gap-4 pt-2 border-t border-ink-100 dark:border-ink-800">
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Début Matin</label>
                                <input
                                    type="time"
                                    value={formData.heureDebutMatin}
                                    onChange={(e) => setFormData({ ...formData, heureDebutMatin: e.target.value })}
                                    className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Fin Matin</label>
                                <input
                                    type="time"
                                    value={formData.heureFinMatin}
                                    onChange={(e) => setFormData({ ...formData, heureFinMatin: e.target.value })}
                                    className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Début Après-midi</label>
                                <input
                                    type="time"
                                    value={formData.heureDebutApresMidi}
                                    onChange={(e) => setFormData({ ...formData, heureDebutApresMidi: e.target.value })}
                                    className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                />
                            </div>
                            <div className="space-y-1">
                                <label className="text-xs font-semibold text-ink-400 uppercase">Fin Après-midi</label>
                                <input
                                    type="time"
                                    value={formData.heureFinApresMidi}
                                    onChange={(e) => setFormData({ ...formData, heureFinApresMidi: e.target.value })}
                                    className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
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
