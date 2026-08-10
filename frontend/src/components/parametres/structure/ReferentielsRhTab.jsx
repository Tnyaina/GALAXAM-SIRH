import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, FileText, UserCheck, Landmark } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';

export default function ReferentielsRhTab() {
    const [activeSection, setActiveSection] = useState('contrats'); // 'contrats' | 'intervenants' | 'banques'
    
    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [banques, setBanques] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Modales
    const [showContratModal, setShowContratModal] = useState(false);
    const [editingContrat, setEditingContrat] = useState(null);
    const [contratFormData, setContratFormData] = useState({ code: '', libelle: '', exigeDateFin: false });

    const [showIntervenantModal, setShowIntervenantModal] = useState(false);
    const [editingIntervenant, setEditingIntervenant] = useState(null);
    const [intervenantFormData, setIntervenantFormData] = useState({
        code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true
    });

    const [showBanqueModal, setShowBanqueModal] = useState(false);
    const [editingBanque, setEditingBanque] = useState(null);
    const [banqueFormData, setBanqueFormData] = useState({ codeBanque: '', nomBanque: '', codeSwift: '' });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [tcData, tiData, bData] = await Promise.all([
                organisationService.getTypesContrat(),
                organisationService.getTypesIntervenant(),
                organisationService.getBanques()
            ]);
            setTypesContrat(tcData);
            setTypesIntervenant(tiData);
            setBanques(bData);
        } catch (err) {
            setError("Erreur lors du chargement des référentiels RH.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Contrat Handlers
    const handleSaveContrat = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!contratFormData.code.trim() || !contratFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        try {
            if (editingContrat) {
                await organisationService.updateTypeContrat(editingContrat.idTypeContrat, contratFormData);
                setActionSuccess(`Type de contrat "${contratFormData.libelle}" modifié.`);
            } else {
                await organisationService.createTypeContrat(contratFormData);
                setActionSuccess(`Type de contrat "${contratFormData.libelle}" créé.`);
            }
            setShowContratModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Intervenant Handlers
    const handleSaveIntervenant = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!intervenantFormData.code.trim() || !intervenantFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        try {
            if (editingIntervenant) {
                await organisationService.updateTypeIntervenant(editingIntervenant.idTypeIntervenant, intervenantFormData);
                setActionSuccess(`Type d'intervenant "${intervenantFormData.libelle}" modifié.`);
            } else {
                await organisationService.createTypeIntervenant(intervenantFormData);
                setActionSuccess(`Type d'intervenant "${intervenantFormData.libelle}" créé.`);
            }
            setShowIntervenantModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Banque Handlers
    const handleSaveBanque = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!banqueFormData.codeBanque.trim() || !banqueFormData.nomBanque.trim()) {
            setActionError("Le code et le nom de banque sont obligatoires.");
            return;
        }
        try {
            if (editingBanque) {
                await organisationService.updateBanque(editingBanque.idBanque, banqueFormData);
                setActionSuccess(`Banque "${banqueFormData.nomBanque}" modifiée.`);
            } else {
                await organisationService.createBanque(banqueFormData);
                setActionSuccess(`Banque "${banqueFormData.nomBanque}" créée.`);
            }
            setShowBanqueModal(false);
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

            {/* Barre d'onglets internes */}
            <div className="flex border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 rounded-sm p-2 gap-2 shadow-sm">
                <button
                    onClick={() => setActiveSection('contrats')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'contrats'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <FileText size={16} />
                    Types de Contrat ({typesContrat.length})
                </button>
                <button
                    onClick={() => setActiveSection('intervenants')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'intervenants'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <UserCheck size={16} />
                    Types d'Intervenant ({typesIntervenant.length})
                </button>
                <button
                    onClick={() => setActiveSection('banques')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        activeSection === 'banques'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <Landmark size={16} />
                    Établissements Bancaires ({banques.length})
                </button>
            </div>

            {/* SECTION 1: TYPES DE CONTRAT */}
            {activeSection === 'contrats' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Référentiel des Contrats de Travail</h4>
                        <button
                            onClick={() => {
                                setEditingContrat(null);
                                setContratFormData({ code: '', libelle: '', exigeDateFin: false });
                                setActionError(null);
                                setShowContratModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouveau Type de Contrat
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code</th>
                                    <th className="px-4 py-3">Libellé</th>
                                    <th className="px-4 py-3">Exige une Date de Fin</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {typesContrat.map((tc) => (
                                    <tr key={tc.idTypeContrat} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-semibold text-brand-500">{tc.code}</td>
                                        <td className="px-4 py-3 font-medium">{tc.libelle}</td>
                                        <td className="px-4 py-3">
                                            {tc.exigeDateFin ? (
                                                <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 font-semibold rounded-sm">
                                                    Oui (ex: CDD, Prestation)
                                                </span>
                                            ) : (
                                                <span className="text-xs px-2 py-0.5 bg-ink-100 dark:bg-ink-800 text-ink-400 rounded-sm">
                                                    Non (ex: CDI)
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingContrat(tc);
                                                    setContratFormData({ code: tc.code, libelle: tc.libelle, exigeDateFin: tc.exigeDateFin });
                                                    setActionError(null);
                                                    setShowContratModal(true);
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

            {/* SECTION 2: TYPES D'INTERVENANT */}
            {activeSection === 'intervenants' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Typologie et Assujettissements des Intervenants</h4>
                        <button
                            onClick={() => {
                                setEditingIntervenant(null);
                                setIntervenantFormData({ code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true });
                                setActionError(null);
                                setShowIntervenantModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouveau Type d'Intervenant
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code</th>
                                    <th className="px-4 py-3">Libellé</th>
                                    <th className="px-4 py-3 text-center">Soumis IRSA</th>
                                    <th className="px-4 py-3 text-center">Soumis ISI 5%</th>
                                    <th className="px-4 py-3 text-center">Soumis CNaPS</th>
                                    <th className="px-4 py-3 text-center">Soumis Santé</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {typesIntervenant.map((ti) => (
                                    <tr key={ti.idTypeIntervenant} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-semibold text-brand-500">{ti.code}</td>
                                        <td className="px-4 py-3 font-medium">{ti.libelle}</td>
                                        <td className="px-4 py-3 text-center font-bold">{ti.soumisIrsa ? 'Oui' : '-'}</td>
                                        <td className="px-4 py-3 text-center font-bold">{ti.soumisIsi5 ? 'Oui' : '-'}</td>
                                        <td className="px-4 py-3 text-center font-bold">{ti.soumisCnaps ? 'Oui' : '-'}</td>
                                        <td className="px-4 py-3 text-center font-bold">{ti.soumisSante ? 'Oui' : '-'}</td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingIntervenant(ti);
                                                    setIntervenantFormData({
                                                        code: ti.code,
                                                        libelle: ti.libelle,
                                                        soumisIrsa: ti.soumisIrsa,
                                                        soumisIsi5: ti.soumisIsi5,
                                                        soumisCnaps: ti.soumisCnaps,
                                                        soumisSante: ti.soumisSante
                                                    });
                                                    setActionError(null);
                                                    setShowIntervenantModal(true);
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

            {/* SECTION 3: BANQUES */}
            {activeSection === 'banques' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Référentiel des Établissements Bancaires</h4>
                        <button
                            onClick={() => {
                                setEditingBanque(null);
                                setBanqueFormData({ codeBanque: '', nomBanque: '', codeSwift: '' });
                                setActionError(null);
                                setShowBanqueModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouvelle Banque
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code Banque</th>
                                    <th className="px-4 py-3">Nom de la Banque</th>
                                    <th className="px-4 py-3">Code SWIFT</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {banques.map((b) => (
                                    <tr key={b.idBanque} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-semibold text-brand-500">{b.codeBanque}</td>
                                        <td className="px-4 py-3 font-bold">{b.nomBanque}</td>
                                        <td className="px-4 py-3 font-mono text-xs text-ink-500">{b.codeSwift || '-'}</td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingBanque(b);
                                                    setBanqueFormData({ codeBanque: b.codeBanque, nomBanque: b.nomBanque, codeSwift: b.codeSwift || '' });
                                                    setActionError(null);
                                                    setShowBanqueModal(true);
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

            {/* Modale Type Contrat */}
            {showContratModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingContrat ? "Modifier le Type de Contrat" : "Nouveau Type de Contrat"}
                        </div>
                        <form onSubmit={handleSaveContrat}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code</label>
                                    <input
                                        type="text"
                                        value={contratFormData.code}
                                        onChange={(e) => setContratFormData({ ...contratFormData, code: e.target.value })}
                                        placeholder="Ex: CDI, CDD"
                                        disabled={!!editingContrat}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé</label>
                                    <input
                                        type="text"
                                        value={contratFormData.libelle}
                                        onChange={(e) => setContratFormData({ ...contratFormData, libelle: e.target.value })}
                                        placeholder="Ex: Contrat à Durée Indéterminée"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="flex items-center gap-2 pt-2">
                                    <input
                                        type="checkbox"
                                        id="exigeDateFin"
                                        checked={contratFormData.exigeDateFin}
                                        onChange={(e) => setContratFormData({ ...contratFormData, exigeDateFin: e.target.checked })}
                                        className="w-4 h-4 text-brand-500 border-ink-300 dark:border-ink-700 rounded-sm focus:ring-brand-500"
                                    />
                                    <label htmlFor="exigeDateFin" className="text-sm text-ink-900 dark:text-white font-medium cursor-pointer">
                                        Exige une date de fin (Obligatoire pour CDD)
                                    </label>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowContratModal(false)}
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

            {/* Modale Type Intervenant */}
            {showIntervenantModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingIntervenant ? "Modifier le Type d'Intervenant" : "Nouveau Type d'Intervenant"}
                        </div>
                        <form onSubmit={handleSaveIntervenant}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code</label>
                                    <input
                                        type="text"
                                        value={intervenantFormData.code}
                                        onChange={(e) => setIntervenantFormData({ ...intervenantFormData, code: e.target.value })}
                                        placeholder="Ex: PERMANENT, CONSULTANT"
                                        disabled={!!editingIntervenant}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé</label>
                                    <input
                                        type="text"
                                        value={intervenantFormData.libelle}
                                        onChange={(e) => setIntervenantFormData({ ...intervenantFormData, libelle: e.target.value })}
                                        placeholder="Ex: Salarié Permanent"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-2 pt-2 border-t border-ink-100 dark:border-ink-800">
                                    <p className="text-xs font-semibold text-ink-400 uppercase">Assujettissements Légaux</p>
                                    <div className="grid grid-cols-2 gap-2 text-sm">
                                        <label className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={intervenantFormData.soumisIrsa}
                                                onChange={(e) => setIntervenantFormData({ ...intervenantFormData, soumisIrsa: e.target.checked })}
                                                className="w-4 h-4 text-brand-500 rounded-sm"
                                            />
                                            <span>Soumis IRSA</span>
                                        </label>
                                        <label className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={intervenantFormData.soumisIsi5}
                                                onChange={(e) => setIntervenantFormData({ ...intervenantFormData, soumisIsi5: e.target.checked })}
                                                className="w-4 h-4 text-brand-500 rounded-sm"
                                            />
                                            <span>Soumis ISI 5%</span>
                                        </label>
                                        <label className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={intervenantFormData.soumisCnaps}
                                                onChange={(e) => setIntervenantFormData({ ...intervenantFormData, soumisCnaps: e.target.checked })}
                                                className="w-4 h-4 text-brand-500 rounded-sm"
                                            />
                                            <span>Soumis CNaPS</span>
                                        </label>
                                        <label className="flex items-center gap-2 cursor-pointer">
                                            <input
                                                type="checkbox"
                                                checked={intervenantFormData.soumisSante}
                                                onChange={(e) => setIntervenantFormData({ ...intervenantFormData, soumisSante: e.target.checked })}
                                                className="w-4 h-4 text-brand-500 rounded-sm"
                                            />
                                            <span>Soumis Santé (OSTIE)</span>
                                        </label>
                                    </div>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowIntervenantModal(false)}
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

            {/* Modale Banque */}
            {showBanqueModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingBanque ? "Modifier la Banque" : "Nouvelle Banque"}
                        </div>
                        <form onSubmit={handleSaveBanque}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Banque</label>
                                    <input
                                        type="text"
                                        value={banqueFormData.codeBanque}
                                        onChange={(e) => setBanqueFormData({ ...banqueFormData, codeBanque: e.target.value })}
                                        placeholder="Ex: BOA, BNI, BFV"
                                        disabled={!!editingBanque}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Nom de la Banque</label>
                                    <input
                                        type="text"
                                        value={banqueFormData.nomBanque}
                                        onChange={(e) => setBanqueFormData({ ...banqueFormData, nomBanque: e.target.value })}
                                        placeholder="Ex: Bank of Africa Madagascar"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code SWIFT (Optionnel)</label>
                                    <input
                                        type="text"
                                        value={banqueFormData.codeSwift}
                                        onChange={(e) => setBanqueFormData({ ...banqueFormData, codeSwift: e.target.value })}
                                        placeholder="Ex: AFRIMGMG"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowBanqueModal(false)}
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
