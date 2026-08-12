import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, Gift, MapPin } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';

export default function IndemnitesSitesTab() {
    const [subSection, setSubSection] = useState('indemnites'); // 'indemnites' | 'sites_config'

    const [indemnites, setIndemnites] = useState([]);
    const [siteIndemnites, setSiteIndemnites] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Modale Indemnité
    const [showIndemniteModal, setShowIndemniteModal] = useState(false);
    const [editingIndemnite, setEditingIndemnite] = useState(null);
    const [indemniteFormData, setIndemniteFormData] = useState({
        code: '', libelle: '', imposableIrsa: false
    });

    // Modale Config Site
    const [showSiteIndemniteModal, setShowSiteIndemniteModal] = useState(false);
    const [editingSiteIndemnite, setEditingSiteIndemnite] = useState(null);
    const [siteIndemniteFormData, setSiteIndemniteFormData] = useState({
        idSite: '', idIndemnite: '', periodicite: 'JOURNALIER', montantReference: 0, dateDebut: '2026-01-01', dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [indData, siteIndData, sitesData] = await Promise.all([
                paieConfigService.getIndemnites(),
                paieConfigService.getSiteIndemnites(),
                organisationService.getSites()
            ]);
            setIndemnites(indData);
            setSiteIndemnites(siteIndData);
            setSites(sitesData);
        } catch (err) {
            setError("Erreur lors du chargement des indemnités.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Indemnité
    const handleSaveIndemnite = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingIndemnite) {
                await paieConfigService.updateIndemnite(editingIndemnite.idIndemnite, indemniteFormData);
                setActionSuccess(`Indemnité "${indemniteFormData.libelle}" modifiée.`);
            } else {
                await paieConfigService.createIndemnite(indemniteFormData);
                setActionSuccess(`Indemnité "${indemniteFormData.libelle}" créée.`);
            }
            setShowIndemniteModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Site Indemnité
    const handleSaveSiteIndemnite = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        const payload = {
            ...siteIndemniteFormData,
            idSite: parseInt(siteIndemniteFormData.idSite),
            idIndemnite: parseInt(siteIndemniteFormData.idIndemnite),
            montantReference: parseFloat(siteIndemniteFormData.montantReference) || 0
        };
        try {
            if (editingSiteIndemnite) {
                await paieConfigService.updateSiteIndemnite(editingSiteIndemnite.idSiteIndemnite, payload);
                setActionSuccess("Tarif d'indemnité par site modifié.");
            } else {
                await paieConfigService.createSiteIndemnite(payload);
                setActionSuccess("Nouveau tarif d'indemnité par site enregistré.");
            }
            setShowSiteIndemniteModal(false);
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
                    onClick={() => setSubSection('indemnites')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        subSection === 'indemnites'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <Gift size={16} />
                    Référentiel des Indemnités ({indemnites.length})
                </button>
                <button
                    onClick={() => setSubSection('sites_config')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                        subSection === 'sites_config'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                    }`}
                >
                    <MapPin size={16} />
                    Montants par Site d'Exploitation ({siteIndemnites.length})
                </button>
            </div>

            {/* SECTION 1: CATALOGUE INDEMNITES */}
            {subSection === 'indemnites' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Types d'Indemnités (Repas, Déplacement, Logement...)</h4>
                        <button
                            onClick={() => {
                                setEditingIndemnite(null);
                                setIndemniteFormData({ code: '', libelle: '', imposableIrsa: false });
                                setActionError(null);
                                setShowIndemniteModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouvelle Indemnité
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code</th>
                                    <th className="px-4 py-3">Libellé</th>
                                    <th className="px-4 py-3">Régime Fiscal IRSA</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {indemnites.map((ind) => (
                                    <tr key={ind.idIndemnite} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-semibold text-brand-500">{ind.code}</td>
                                        <td className="px-4 py-3 font-bold">{ind.libelle}</td>
                                        <td className="px-4 py-3">
                                            {ind.imposableIrsa ? (
                                                <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 font-semibold rounded-sm">
                                                    Imposable (Soumis à l'IRSA)
                                                </span>
                                            ) : (
                                                <span className="text-xs px-2 py-0.5 bg-success-500/10 text-success-500 font-semibold rounded-sm">
                                                    Exonéré (Non imposable)
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingIndemnite(ind);
                                                    setIndemniteFormData({ code: ind.code, libelle: ind.libelle, imposableIrsa: ind.imposableIrsa });
                                                    setActionError(null);
                                                    setShowIndemniteModal(true);
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

            {/* SECTION 2: CONFIGURATION PAR SITE */}
            {subSection === 'sites_config' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <div>
                            <h4 className="font-bold text-ink-900 dark:text-white text-md">Tarifs des Indemnités par Site d'Exploitation</h4>
                            <p className="text-xs text-ink-400">Montants mensuels ou journaliers appliqués selon le site d'affectation.</p>
                        </div>
                        <button
                            onClick={() => {
                                setEditingSiteIndemnite(null);
                                setSiteIndemniteFormData({
                                    idSite: sites.length > 0 ? sites[0].idSite : '',
                                    idIndemnite: indemnites.length > 0 ? indemnites[0].idIndemnite : '',
                                    periodicite: 'JOURNALIER',
                                    montantReference: 2000,
                                    dateDebut: '2026-01-01',
                                    dateFin: ''
                                });
                                setActionError(null);
                                setShowSiteIndemniteModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Configurer Tarif Site
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Site d'Exploitation</th>
                                    <th className="px-4 py-3">Indemnité</th>
                                    <th className="px-4 py-3">Périodicité</th>
                                    <th className="px-4 py-3 text-right">Montant Référence (Ar)</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {siteIndemnites.map((si) => (
                                    <tr key={si.idSiteIndemnite} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-bold flex items-center gap-2">
                                            <MapPin size={15} className="text-brand-500" />
                                            {si.siteLibelle}
                                        </td>
                                        <td className="px-4 py-3 font-medium">
                                            {si.indemniteLibelle} <span className="font-mono text-xs text-ink-400">({si.indemniteCode})</span>
                                        </td>
                                        <td className="px-4 py-3">
                                            <span className="text-xs px-2 py-0.5 bg-ink-100 dark:bg-ink-800 font-semibold rounded-sm">
                                                {si.periodicite}
                                            </span>
                                        </td>
                                        <td className="px-4 py-3 text-right font-mono font-bold text-brand-500 text-base">
                                            {si.montantReference?.toLocaleString()} Ar
                                        </td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingSiteIndemnite(si);
                                                    setSiteIndemniteFormData({
                                                        idSite: si.idSite,
                                                        idIndemnite: si.idIndemnite,
                                                        periodicite: si.periodicite,
                                                        montantReference: si.montantReference,
                                                        dateDebut: si.dateDebut || '2026-01-01',
                                                        dateFin: si.dateFin || ''
                                                    });
                                                    setActionError(null);
                                                    setShowSiteIndemniteModal(true);
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

            {/* Modale Indemnité */}
            {showIndemniteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingIndemnite ? "Modifier l'Indemnité" : "Nouvelle Indemnité"}
                        </div>
                        <form onSubmit={handleSaveIndemnite}>
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
                                        value={indemniteFormData.code}
                                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, code: e.target.value })}
                                        placeholder="Ex: REPAS, DEPLACEMENT"
                                        disabled={!!editingIndemnite}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé</label>
                                    <input
                                        type="text"
                                        value={indemniteFormData.libelle}
                                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, libelle: e.target.value })}
                                        placeholder="Ex: Indemnité de Repas"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="flex items-center gap-2 pt-2">
                                    <input
                                        type="checkbox"
                                        id="imposableIrsa"
                                        checked={indemniteFormData.imposableIrsa}
                                        onChange={(e) => setIndemniteFormData({ ...indemniteFormData, imposableIrsa: e.target.checked })}
                                        className="w-4 h-4 text-brand-500 border-ink-300 dark:border-ink-700 rounded-sm focus:ring-brand-500"
                                    />
                                    <label htmlFor="imposableIrsa" className="text-sm text-ink-900 dark:text-white font-medium cursor-pointer">
                                        Imposable à l'IRSA
                                    </label>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowIndemniteModal(false)}
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

            {/* Modale Site Indemnité */}
            {showSiteIndemniteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingSiteIndemnite ? "Modifier Tarif Site" : "Nouveau Tarif par Site"}
                        </div>
                        <form onSubmit={handleSaveSiteIndemnite}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Site d'Exploitation</label>
                                    <select
                                        value={siteIndemniteFormData.idSite}
                                        onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idSite: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {sites.map((s) => (
                                            <option key={s.idSite} value={s.idSite}>{s.libelle} ({s.codeSite})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Type d'Indemnité</label>
                                    <select
                                        value={siteIndemniteFormData.idIndemnite}
                                        onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, idIndemnite: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {indemnites.map((i) => (
                                            <option key={i.idIndemnite} value={i.idIndemnite}>{i.libelle} ({i.code})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Périodicité</label>
                                        <select
                                            value={siteIndemniteFormData.periodicite}
                                            onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, periodicite: e.target.value })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        >
                                            <option value="JOURNALIER">JOURNALIER</option>
                                            <option value="MENSUEL">MENSUEL</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Montant (Ar)</label>
                                        <input
                                            type="number"
                                            value={siteIndemniteFormData.montantReference}
                                            onChange={(e) => setSiteIndemniteFormData({ ...siteIndemniteFormData, montantReference: e.target.value })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowSiteIndemniteModal(false)}
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
