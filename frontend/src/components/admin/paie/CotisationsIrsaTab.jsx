import { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, CheckCircle, XCircle, ShieldCheck, Percent, MapPin, Eye } from 'lucide-react';
import paieConfigService from '@/api/paieConfigService';
import organisationService from '@/api/organisationService';

export default function CotisationsIrsaTab() {
    const [subSection, setSubSection] = useState('cotisations'); // 'cotisations' | 'site_cotisations' | 'irsa'

    const [cotisations, setCotisations] = useState([]);
    const [siteCotisations, setSiteCotisations] = useState([]);
    const [sites, setSites] = useState([]);
    const [tranchesIrsa, setTranchesIrsa] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Filtre pour afficher les éléments désactivés/archivés
    const [showInactive, setShowInactive] = useState(false);

    // Modale Cotisation
    const [showCotisationModal, setShowCotisationModal] = useState(false);
    const [editingCotisation, setEditingCotisation] = useState(null);
    const [cotisationFormData, setCotisationFormData] = useState({
        code: '', libelle: '', tauxSalarial: 1.0, tauxPatronal: 13.0, dateDebut: '2026-01-01', dateFin: ''
    });

    // Modale Site Cotisation
    const [showSiteCotisationModal, setShowSiteCotisationModal] = useState(false);
    const [siteCotisationFormData, setSiteCotisationFormData] = useState({
        idSite: '', idTypeCotisation: ''
    });

    // Modale IRSA
    const [showIrsaModal, setShowIrsaModal] = useState(false);
    const [editingIrsa, setEditingIrsa] = useState(null);
    const [irsaFormData, setIrsaFormData] = useState({
        numeroTranche: 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: '2026-01-01', dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [cotData, siteCotData, sitesData, irsaData] = await Promise.all([
                paieConfigService.getCotisations(),
                paieConfigService.getSiteCotisations(),
                organisationService.getSites(),
                paieConfigService.getBaremesIrsa()
            ]);
            setCotisations(cotData);
            setSiteCotisations(siteCotData);
            setSites(sitesData);
            setTranchesIrsa(irsaData);
        } catch (err) {
            setError("Erreur lors du chargement des données. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Cotisation
    const handleSaveCotisation = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingCotisation) {
                await paieConfigService.updateCotisation(editingCotisation.idTypeCotisation, cotisationFormData);
                setActionSuccess(`Cotisation "${cotisationFormData.libelle}" modifiée.`);
            } else {
                await paieConfigService.createCotisation(cotisationFormData);
                setActionSuccess(`Cotisation "${cotisationFormData.libelle}" créée.`);
            }
            setShowCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Site Cotisation
    const handleSaveSiteCotisation = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        const payload = {
            idSite: parseInt(siteCotisationFormData.idSite),
            idTypeCotisation: parseInt(siteCotisationFormData.idTypeCotisation)
        };
        try {
            await paieConfigService.createSiteCotisation(payload);
            setActionSuccess("Cotisation associée au site.");
            setShowSiteCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur d'affectation.");
        }
    };

    const handleDeleteSiteCotisation = async (id) => {
        setActionSuccess(null);
        setActionError(null);
        try {
            await paieConfigService.deleteSiteCotisation(id);
            setActionSuccess("Association supprimée.");
            setSiteCotisations(prev => prev.filter(item => item.idSiteCotisation !== id));
        } catch (err) {
            setActionError("Erreur lors de la suppression.");
            console.error(err);
        }
    };

    // Handlers IRSA
    const handleSaveIrsa = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        const payload = {
            ...irsaFormData,
            trancheMax: irsaFormData.trancheMax !== '' ? parseFloat(irsaFormData.trancheMax) : null
        };
        try {
            if (editingIrsa) {
                await paieConfigService.updateBaremeIrsa(editingIrsa.idTranche, payload);
                setActionSuccess(`Tranche IRSA #${irsaFormData.numeroTranche} modifiée.`);
            } else {
                await paieConfigService.createBaremeIrsa(payload);
                setActionSuccess(`Tranche IRSA #${irsaFormData.numeroTranche} créée.`);
            }
            setShowIrsaModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Filtrage désactivés
    const filteredSiteCotisations = showInactive
        ? siteCotisations
        : siteCotisations.filter(sc => sc.estActif !== false);

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

            {/* Onglets internes + Filtre d'affichage */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 rounded-sm p-2 gap-3 shadow-sm">
                <div className="flex gap-2 flex-wrap">
                    <button
                        onClick={() => setSubSection('cotisations')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            subSection === 'cotisations'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <ShieldCheck size={16} />
                        Cotisations Sociales & Santé ({cotisations.length})
                    </button>
                    <button
                        onClick={() => setSubSection('site_cotisations')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            subSection === 'site_cotisations'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <MapPin size={16} />
                        Cotisations par Site ({filteredSiteCotisations.length})
                    </button>
                    <button
                        onClick={() => setSubSection('irsa')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            subSection === 'irsa'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <Percent size={16} />
                        Barème d'Impôt IRSA ({tranchesIrsa.length} tranches)
                    </button>
                </div>

                {/* Checkbox Filtre Désactivés */}
                <label className="flex items-center gap-2 text-xs font-semibold text-ink-500 dark:text-ink-300 cursor-pointer px-2 py-1 hover:bg-ink-50 dark:hover:bg-ink-950 rounded-sm">
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

            {/* SECTION 1: COTISATIONS */}
            {subSection === 'cotisations' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h4 className="font-bold text-ink-900 dark:text-white text-md">Organismes Cotisants & Taux Légaux</h4>
                        <button
                            onClick={() => {
                                setEditingCotisation(null);
                                setCotisationFormData({ code: '', libelle: '', tauxSalarial: 1.0, tauxPatronal: 13.0, dateDebut: '2026-01-01', dateFin: '' });
                                setActionError(null);
                                setShowCotisationModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouvelle Cotisation
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Code Organisme</th>
                                    <th className="px-4 py-3">Libellé</th>
                                    <th className="px-4 py-3 text-right">Taux Salarial (%)</th>
                                    <th className="px-4 py-3 text-right">Taux Patronal (%)</th>
                                    <th className="px-4 py-3 text-center">Taux Global (%)</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {cotisations.map((c) => {
                                    const total = ((c.tauxSalarial || 0) + (c.tauxPatronal || 0)).toFixed(2);
                                    return (
                                        <tr key={c.idTypeCotisation} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-mono font-semibold text-brand-500">{c.code}</td>
                                            <td className="px-4 py-3 font-bold">{c.libelle}</td>
                                            <td className="px-4 py-3 text-right font-mono font-semibold text-amber-500">{c.tauxSalarial}%</td>
                                            <td className="px-4 py-3 text-right font-mono font-semibold text-sky-500">{c.tauxPatronal}%</td>
                                            <td className="px-4 py-3 text-center font-mono font-bold">
                                                <span className="px-2 py-0.5 bg-ink-100 dark:bg-ink-800 rounded-sm">
                                                    {total}%
                                                </span>
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                <button
                                                    onClick={() => {
                                                        setEditingCotisation(c);
                                                        setCotisationFormData({
                                                            code: c.code,
                                                            libelle: c.libelle,
                                                            tauxSalarial: c.tauxSalarial,
                                                            tauxPatronal: c.tauxPatronal,
                                                            dateDebut: c.dateDebut || '2026-01-01',
                                                            dateFin: c.dateFin || ''
                                                        });
                                                        setActionError(null);
                                                        setShowCotisationModal(true);
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

            {/* SECTION 2: COTISATIONS PAR SITE */}
            {subSection === 'site_cotisations' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <div>
                            <h4 className="font-bold text-ink-900 dark:text-white text-md">Organismes Cotisants rattachés par Site</h4>
                            <p className="text-xs text-ink-400">Permet d'affecter les organismes de santé/prévoyance spécifiques (ex. OSTIE à Antananarivo vs AMIT à Tamatave).</p>
                        </div>
                        <button
                            onClick={() => {
                                setSiteCotisationFormData({
                                    idSite: sites.length > 0 ? sites[0].idSite : '',
                                    idTypeCotisation: cotisations.length > 0 ? cotisations[0].idTypeCotisation : ''
                                });
                                setActionError(null);
                                setShowSiteCotisationModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Associer à un Site
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Site d'Exploitation</th>
                                    <th className="px-4 py-3">Organisme Cotisant</th>
                                    <th className="px-4 py-3 text-center">Statut</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {filteredSiteCotisations.length > 0 ? (
                                    filteredSiteCotisations.map((sc) => (
                                        <tr key={sc.idSiteCotisation} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-bold flex items-center gap-2">
                                                <MapPin size={15} className="text-brand-500" />
                                                {sc.siteLibelle}
                                            </td>
                                            <td className="px-4 py-3 font-medium">
                                                <span className="font-mono text-brand-500 font-bold mr-2">{sc.cotisationCode}</span>
                                                {sc.cotisationLibelle}
                                            </td>
                                            <td className="px-4 py-3 text-center">
                                                {sc.estActif !== false ? (
                                                    <span className="text-xs px-2 py-0.5 bg-emerald-500/10 text-emerald-500 font-semibold rounded-sm">
                                                        Actif
                                                    </span>
                                                ) : (
                                                    <span className="text-xs px-2 py-0.5 bg-danger-500/10 text-danger-500 font-semibold rounded-sm">
                                                        Désactivé
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                <button
                                                    onClick={() => handleDeleteSiteCotisation(sc.idSiteCotisation)}
                                                    className="p-1 text-danger-500 hover:bg-danger-500/10 rounded-sm cursor-pointer"
                                                    title="Supprimer l'association"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="4" className="px-4 py-8 text-center text-ink-400">
                                            Aucune association de cotisation enregistrée.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* SECTION 3: BAREME IRSA */}
            {subSection === 'irsa' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <div>
                            <h4 className="font-bold text-ink-900 dark:text-white text-md">Barème Tranches d'Imposition IRSA (Madagascar)</h4>
                            <p className="text-xs text-ink-400">Barème progressif sur les revenus nets imposables.</p>
                        </div>
                        <button
                            onClick={() => {
                                setEditingIrsa(null);
                                setIrsaFormData({ numeroTranche: tranchesIrsa.length + 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: '2026-01-01', dateFin: '' });
                                setActionError(null);
                                setShowIrsaModal(true);
                            }}
                            className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                        >
                            <Plus size={16} />
                            Nouvelle Tranche IRSA
                        </button>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Tranche #</th>
                                    <th className="px-4 py-3">Montant Min (Ar)</th>
                                    <th className="px-4 py-3">Montant Max (Ar)</th>
                                    <th className="px-4 py-3 text-right">Taux (%)</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {tranchesIrsa.map((t) => (
                                    <tr key={t.idTranche} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-4 py-3 font-mono font-bold text-brand-500">Tranche {t.numeroTranche}</td>
                                        <td className="px-4 py-3 font-mono">{t.trancheMin?.toLocaleString()} Ar</td>
                                        <td className="px-4 py-3 font-mono font-semibold">
                                            {t.trancheMax ? `${t.trancheMax.toLocaleString()} Ar` : <span className="text-amber-500 italic">Au-delà (Infini)</span>}
                                        </td>
                                        <td className="px-4 py-3 text-right font-mono font-bold text-brand-500 text-base">{t.taux}%</td>
                                        <td className="px-4 py-3 text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingIrsa(t);
                                                    setIrsaFormData({
                                                        numeroTranche: t.numeroTranche,
                                                        trancheMin: t.trancheMin,
                                                        trancheMax: t.trancheMax ?? '',
                                                        taux: t.taux,
                                                        dateDebut: t.dateDebut || '2026-01-01',
                                                        dateFin: t.dateFin || ''
                                                    });
                                                    setActionError(null);
                                                    setShowIrsaModal(true);
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

            {/* Modale Cotisation */}
            {showCotisationModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingCotisation ? "Modifier la Cotisation" : "Nouvelle Cotisation"}
                        </div>
                        <form onSubmit={handleSaveCotisation}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Cotisation</label>
                                    <input
                                        type="text"
                                        value={cotisationFormData.code}
                                        onChange={(e) => setCotisationFormData({ ...cotisationFormData, code: e.target.value })}
                                        placeholder="Ex: CNAPS, OSTIE"
                                        disabled={!!editingCotisation}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé Organisme</label>
                                    <input
                                        type="text"
                                        value={cotisationFormData.libelle}
                                        onChange={(e) => setCotisationFormData({ ...cotisationFormData, libelle: e.target.value })}
                                        placeholder="Ex: Caisse Nationale de Prévoyance Sociale"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Taux Salarial (%)</label>
                                        <input
                                            type="number"
                                            step="0.01"
                                            value={cotisationFormData.tauxSalarial}
                                            onChange={(e) => setCotisationFormData({ ...cotisationFormData, tauxSalarial: parseFloat(e.target.value) || 0 })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Taux Patronal (%)</label>
                                        <input
                                            type="number"
                                            step="0.01"
                                            value={cotisationFormData.tauxPatronal}
                                            onChange={(e) => setCotisationFormData({ ...cotisationFormData, tauxPatronal: parseFloat(e.target.value) || 0 })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowCotisationModal(false)}
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

            {/* Modale Site Cotisation */}
            {showSiteCotisationModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            Associer Cotisation à un Site
                        </div>
                        <form onSubmit={handleSaveSiteCotisation}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Site d'Exploitation</label>
                                    <select
                                        value={siteCotisationFormData.idSite}
                                        onChange={(e) => setSiteCotisationFormData({ ...siteCotisationFormData, idSite: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {sites.map((s) => (
                                            <option key={s.idSite} value={s.idSite}>{s.libelle} ({s.codeSite})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Organisme Cotisant</label>
                                    <select
                                        value={siteCotisationFormData.idTypeCotisation}
                                        onChange={(e) => setSiteCotisationFormData({ ...siteCotisationFormData, idTypeCotisation: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {cotisations.map((c) => (
                                            <option key={c.idTypeCotisation} value={c.idTypeCotisation}>{c.code} - {c.libelle}</option>
                                        ))}
                                    </select>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowSiteCotisationModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm cursor-pointer"
                                >
                                    Associer
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale IRSA */}
            {showIrsaModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingIrsa ? "Modifier Tranche IRSA" : "Nouvelle Tranche IRSA"}
                        </div>
                        <form onSubmit={handleSaveIrsa}>
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
                                        value={irsaFormData.numeroTranche}
                                        onChange={(e) => setIrsaFormData({ ...irsaFormData, numeroTranche: parseInt(e.target.value) || 1 })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Tranche Min (Ar)</label>
                                        <input
                                            type="number"
                                            value={irsaFormData.trancheMin}
                                            onChange={(e) => setIrsaFormData({ ...irsaFormData, trancheMin: parseFloat(e.target.value) || 0 })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Tranche Max (Vide = Infini)</label>
                                        <input
                                            type="number"
                                            value={irsaFormData.trancheMax}
                                            onChange={(e) => setIrsaFormData({ ...irsaFormData, trancheMax: e.target.value })}
                                            placeholder="Ex: 500000"
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Taux Imposition (%)</label>
                                    <input
                                        type="number"
                                        step="0.01"
                                        value={irsaFormData.taux}
                                        onChange={(e) => setIrsaFormData({ ...irsaFormData, taux: parseFloat(e.target.value) || 0 })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowIrsaModal(false)}
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
