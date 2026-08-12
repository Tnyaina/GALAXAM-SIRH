import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, Clock, Sliders } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';

export default function MajorationsParametresTab() {
    const [subSection, setSubSection] = useState('majorations'); // 'majorations' | 'parametres'

    const [majorations, setMajorations] = useState([]);
    const [parametres, setParametres] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Modale Majoration
    const [showMajorationModal, setShowMajorationModal] = useState(false);
    const [editingMajoration, setEditingMajoration] = useState(null);
    const [majorationFormData, setMajorationFormData] = useState({
        typeMajoration: '', taux: 0.30, dateDebut: '2026-01-01', dateFin: ''
    });

    // Modale Paramètre Global
    const [showParametreModal, setShowParametreModal] = useState(false);
    const [editingParametre, setEditingParametre] = useState(null);
    const [parametreFormData, setParametreFormData] = useState({
        codeParametre: '', valeur: '', description: '', dateDebut: '2026-01-01', dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [majData, paramData] = await Promise.all([
                paieConfigService.getTauxMajoration(),
                paieConfigService.getParametresGlobaux()
            ]);
            setMajorations(majData);
            setParametres(paramData);
        } catch (err) {
            setError("Erreur lors du chargement des majorations et paramètres.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Majoration
    const handleSaveMajoration = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingMajoration) {
                await paieConfigService.updateTauxMajoration(editingMajoration.idTauxMajoration, majorationFormData);
                setActionSuccess(`Taux de majoration "${majorationFormData.typeMajoration}" modifié.`);
            } else {
                await paieConfigService.createTauxMajoration(majorationFormData);
                setActionSuccess(`Taux de majoration "${majorationFormData.typeMajoration}" créé.`);
            }
            setShowMajorationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Paramètre Global
    const handleSaveParametre = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingParametre) {
                await paieConfigService.updateParametreGlobal(editingParametre.idParametre, parametreFormData);
                setActionSuccess(`Paramètre global "${parametreFormData.codeParametre}" modifié.`);
            } else {
                await paieConfigService.createParametreGlobal(parametreFormData);
                setActionSuccess(`Paramètre global "${parametreFormData.codeParametre}" créé.`);
            }
            setShowParametreModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    if (loading) {
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

            {/* Onglets internes */}
            <div className="flex border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 rounded-sm p-2 gap-2 shadow-sm">
                <button
                    onClick={() => setSubSection('majorations')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        subSection === 'majorations'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <Clock size={16} />
                    Taux Majorations Heures Supp / Spéciales ({majorations.length})
                </button>
                <button
                    onClick={() => setSubSection('parametres')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        subSection === 'parametres'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <Sliders size={16} />
                    Paramètres Globaux de Paie ({parametres.length})
                </button>
            </div>

            {/* SECTION 1: MAJORATIONS */}
            {subSection === 'majorations' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Taux de Majoration des Heures Supplémentaires, Nuit, Dimanche, Fériés</h4>
                        <button
                            onClick={() => {
                                setEditingMajoration(null);
                                setMajorationFormData({ typeMajoration: '', taux: 0.30, dateDebut: '2026-01-01', dateFin: '' });
                                setActionError(null);
                                setShowMajorationModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouveau Taux de Majoration
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Type de Majoration</th>
                                    <th className="px-4 py-3 text-right">Taux (%)</th>
                                    <th className="px-4 py-3 text-right">Taux Multiplicateur</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {majorations.map((m) => {
                                    const percent = ((m.taux || 0) * 100).toFixed(0);
                                    const mult = (1 + (m.taux || 0)).toFixed(2);
                                    return (
                                        <tr key={m.idTauxMajoration} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-mono font-bold text-brand-500">{m.typeMajoration}</td>
                                            <td className="px-4 py-3 text-right font-mono font-bold text-amber-500">+{percent}%</td>
                                            <td className="px-4 py-3 text-right font-mono text-ink-400">x{mult}</td>
                                            <td className="px-4 py-3 text-right">
                                                <button
                                                    onClick={() => {
                                                        setEditingMajoration(m);
                                                        setMajorationFormData({
                                                            typeMajoration: m.typeMajoration,
                                                            taux: m.taux,
                                                            dateDebut: m.dateDebut || '2026-01-01',
                                                            dateFin: m.dateFin || ''
                                                        });
                                                        setActionError(null);
                                                        setShowMajorationModal(true);
                                                    }}
                                                    className="p-1 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm cursor-pointer"
                                                >
                                                    <Edit2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* SECTION 2: PARAMETRES GLOBAUX */}
            {subSection === 'parametres' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <div>
                            <h4 className="font-bold text-ink-900 dark:text-white text-md">Paramètres Globaux du Moteur de Paie</h4>
                            <p className="text-xs text-ink-400">Constantes légales et seuils de calcul (SME, diviseurs horaires, seuils repas).</p>
                        </div>
                        <button
                            onClick={() => {
                                setEditingParametre(null);
                                setParametreFormData({ codeParametre: '', valeur: '', description: '', dateDebut: '2026-01-01', dateFin: '' });
                                setActionError(null);
                                setShowParametreModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouveau Paramètre
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code Paramètre</th>
                                    <th className="px-4 py-3">Valeur</th>
                                    <th className="px-4 py-3">Description</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {parametres.map((p) => (
                                    <tr key={p.idParametre} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-bold text-brand-500">{p.codeParametre}</td>
                                        <td className="px-4 py-3 font-mono font-semibold text-lg text-emerald-600 dark:text-emerald-400">{p.valeur}</td>
                                        <td className="px-4 py-3 text-xs text-ink-500">{p.description || '-'}</td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingParametre(p);
                                                    setParametreFormData({
                                                        codeParametre: p.codeParametre,
                                                        valeur: p.valeur,
                                                        description: p.description || '',
                                                        dateDebut: p.dateDebut || '2026-01-01',
                                                        dateFin: p.dateFin || ''
                                                    });
                                                    setActionError(null);
                                                    setShowParametreModal(true);
                                                }}
                                                className="p-1 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm cursor-pointer"
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Modale Majoration */}
            {showMajorationModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingMajoration ? "Modifier le Taux de Majoration" : "Nouveau Taux de Majoration"}
                        </div>
                        <form onSubmit={handleSaveMajoration}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Type Majoration</label>
                                    <input
                                        type="text"
                                        value={majorationFormData.typeMajoration}
                                        onChange={(e) => setMajorationFormData({ ...majorationFormData, typeMajoration: e.target.value })}
                                        placeholder="Ex: HS_30, NUIT, FERIE"
                                        disabled={!!editingMajoration}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Taux (Ex: 0.30 pour +30%)</label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        value={majorationFormData.taux}
                                        onChange={(e) => setMajorationFormData({ ...majorationFormData, taux: parseFloat(e.target.value) || 0 })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowMajorationModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm cursor-pointer"
                                >
                                    Enregistrer
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale Paramètre Global */}
            {showParametreModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingParametre ? "Modifier le Paramètre Global" : "Nouveau Paramètre Global"}
                        </div>
                        <form onSubmit={handleSaveParametre}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Paramètre</label>
                                    <input
                                        type="text"
                                        value={parametreFormData.codeParametre}
                                        onChange={(e) => setParametreFormData({ ...parametreFormData, codeParametre: e.target.value })}
                                        placeholder="Ex: SME_COURANT, SEUIL_HEURES_SEMAINE"
                                        disabled={!!editingParametre}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Valeur</label>
                                    <input
                                        type="text"
                                        value={parametreFormData.valeur}
                                        onChange={(e) => setParametreFormData({ ...parametreFormData, valeur: e.target.value })}
                                        placeholder="Ex: 262000.00"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Description</label>
                                    <textarea
                                        value={parametreFormData.description}
                                        onChange={(e) => setParametreFormData({ ...parametreFormData, description: e.target.value })}
                                        placeholder="Description du paramètre..."
                                        rows="3"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    ></textarea>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowParametreModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm cursor-pointer"
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
