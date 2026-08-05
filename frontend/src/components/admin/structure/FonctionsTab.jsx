import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, CheckCircle, XCircle, Eye } from 'lucide-react';
import organisationService from '@/api/organisationService';

export default function FonctionsTab() {
    const [fonctions, setFonctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');

    const [showModal, setShowModal] = useState(false);
    const [editingFonction, setEditingFonction] = useState(null);
    const [formData, setFormData] = useState({
        codeFonction: '',
        libelle: ''
    });

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getFonctions();
            setFonctions(data);
        } catch (err) {
            setError("Erreur lors du chargement des fonctions/postes.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const handleOpenCreate = () => {
        setEditingFonction(null);
        setFormData({ codeFonction: '', libelle: '' });
        setActionError(null);
        setShowModal(true);
    };

    const handleOpenEdit = (fonction) => {
        setEditingFonction(fonction);
        setFormData({
            codeFonction: fonction.codeFonction,
            libelle: fonction.libelle
        });
        setActionError(null);
        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!formData.codeFonction.trim()) {
            setActionError("Le code de la fonction est obligatoire.");
            return;
        }
        if (!formData.libelle.trim()) {
            setActionError("Le libellé de la fonction est obligatoire.");
            return;
        }

        try {
            if (editingFonction) {
                await organisationService.updateFonction(editingFonction.idFonction, formData);
                setActionSuccess(`Intitulé de poste "${formData.libelle}" modifié.`);
            } else {
                await organisationService.createFonction(formData);
                setActionSuccess(`Intitulé de poste "${formData.libelle}" créé.`);
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    const handleToggleStatus = async (fonction) => {
        setActionSuccess(null);
        try {
            if (fonction.estActif) {
                await organisationService.deactivateFonction(fonction.idFonction);
                setActionSuccess(`Poste "${fonction.libelle}" désactivé.`);
            } else {
                await organisationService.activateFonction(fonction.idFonction);
                setActionSuccess(`Poste "${fonction.libelle}" réactivé.`);
            }
            loadData();
        } catch (err) {
            setError("Erreur lors du changement de statut.");
        }
    };

    const [showInactive, setShowInactive] = useState(false);

    const filteredFonctions = fonctions.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFonction.toLowerCase().includes(search.toLowerCase()) ||
         f.libelle.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && fonctions.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="flex items-center gap-3 flex-1 max-w-lg">
                    <div className="relative flex-1">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={18} />
                        <input
                            type="text"
                            placeholder="Rechercher un poste, code..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm text-ink-900 dark:text-white focus:outline-none focus:border-brand-500"
                        />
                    </div>
                    <label className="flex items-center gap-2 text-xs font-semibold text-ink-500 dark:text-ink-300 cursor-pointer px-2 py-1 hover:bg-ink-50 dark:hover:bg-ink-950 rounded-sm whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 text-brand-500 rounded-sm"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                </div>
                <button
                    onClick={handleOpenCreate}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <Plus size={16} />
                    Nouveau Poste / Fonction
                </button>
            </div>

            {/* Tableau des fonctions */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                <th className="px-6 py-4">Code Fonction</th>
                                <th className="px-6 py-4">Intitulé du Poste</th>
                                <th className="px-6 py-4">Statut</th>
                                <th className="px-6 py-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {filteredFonctions.length > 0 ? (
                                filteredFonctions.map((f) => (
                                    <tr key={f.idFonction} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-6 py-4 font-mono font-semibold text-brand-500">{f.codeFonction}</td>
                                        <td className="px-6 py-4 font-bold">{f.libelle}</td>
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
                                                    onClick={() => handleOpenEdit(f)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                                <button
                                                    onClick={() => handleToggleStatus(f)}
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
                                    <td colSpan="4" className="px-6 py-12 text-center text-ink-400">
                                        Aucune fonction enregistrée.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Modale Fonction */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingFonction ? "Modifier le Poste" : "Nouveau Poste / Fonction"}
                        </div>
                        <form onSubmit={handleSave}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Fonction</label>
                                    <input
                                        type="text"
                                        value={formData.codeFonction}
                                        onChange={(e) => setFormData({ ...formData, codeFonction: e.target.value })}
                                        disabled={!!editingFonction}
                                        placeholder="Ex: DG, DAF, CHEF_DECLARANT"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Intitulé du Poste</label>
                                    <input
                                        type="text"
                                        value={formData.libelle}
                                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                        placeholder="Ex: Directeur Général"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowModal(false)}
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
            )}
        </div>
    );
}
