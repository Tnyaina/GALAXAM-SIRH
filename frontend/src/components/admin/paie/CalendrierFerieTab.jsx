import { useState, useEffect } from 'react';
import { Plus, Edit2, Trash2, CheckCircle, XCircle, Calendar, MapPin, Search, Filter, Info } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';

function formatDateDisplay(dateStr) {
    if (!dateStr) return '-';
    const cleaned = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
    const parts = cleaned.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`; // DD/MM/YYYY
    }
    return dateStr;
}

function formatDateForInput(dateStr) {
    if (!dateStr) return '';
    return dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
}

export default function CalendrierFerieTab() {
    const [subSection, setSubSection] = useState('feries'); // 'feries' | 'sites_feries'

    const [feries, setFeries] = useState([]);
    const [siteFeries, setSiteFeries] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Filtres
    const [search, setSearch] = useState('');
    const [selectedYear, setSelectedYear] = useState('2026');
    const [selectedSiteFilter, setSelectedSiteFilter] = useState('');

    // Modale Férié
    const [showFerieModal, setShowFerieModal] = useState(false);
    const [editingFerie, setEditingFerie] = useState(null);
    const [ferieFormData, setFerieFormData] = useState({
        dateFerie: '', libelle: '', estChome: true, estNational: true
    });

    // Modale Affectation Site
    const [showSiteFerieModal, setShowSiteFerieModal] = useState(false);
    const [siteFerieFormData, setSiteFerieFormData] = useState({
        idSite: '', idFerie: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [feriesData, sfData, sitesData] = await Promise.all([
                paieConfigService.getCalendrierFerie(),
                paieConfigService.getSiteFeries(),
                organisationService.getSites()
            ]);
            setFeries(feriesData);
            setSiteFeries(sfData);
            setSites(sitesData);
        } catch (err) {
            setError("Erreur lors du chargement du calendrier des jours fériés.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Férié
    const handleSaveFerie = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!ferieFormData.dateFerie || !ferieFormData.libelle.trim()) {
            setActionError("La date et le libellé sont obligatoires.");
            return;
        }
        try {
            if (editingFerie) {
                await paieConfigService.updateCalendrierFerie(editingFerie.idFerie, ferieFormData);
                setActionSuccess(`Jour férié "${ferieFormData.libelle}" modifié.`);
            } else {
                await paieConfigService.createCalendrierFerie(ferieFormData);
                setActionSuccess(`Jour férié "${ferieFormData.libelle}" créé.`);
            }
            setShowFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Site Férié
    const handleSaveSiteFerie = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!siteFerieFormData.idSite || !siteFerieFormData.idFerie) {
            setActionError("Veuillez sélectionner un site et un jour férié.");
            return;
        }

        const payload = {
            idSite: parseInt(siteFerieFormData.idSite),
            idFerie: parseInt(siteFerieFormData.idFerie)
        };
        try {
            await paieConfigService.createSiteFerie(payload);
            setActionSuccess("Jour férié régional associé au site.");
            setShowSiteFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur d'affectation.");
        }
    };

    const handleDeleteSiteFerie = async (id) => {
        setActionSuccess(null);
        try {
            await paieConfigService.deleteSiteFerie(id);
            setActionSuccess("Association du jour férié supprimée.");
            loadData();
        } catch {
            setError("Erreur lors de la suppression.");
        }
    };

    // Obtenir la liste des années disponibles
    const availableYears = Array.from(new Set(feries.map(f => {
        const cleaned = formatDateForInput(f.dateFerie);
        return cleaned.split('-')[0];
    }))).sort((a, b) => b - a);

    if (!availableYears.includes('2026')) {
        availableYears.unshift('2026');
    }

    // Filtrage des Jours Fériés
    const filteredFeries = feries.filter(f => {
        const cleanedDate = formatDateForInput(f.dateFerie);
        const year = cleanedDate.split('-')[0];
        const matchesSearch = f.libelle.toLowerCase().includes(search.toLowerCase()) || cleanedDate.includes(search);
        const matchesYear = selectedYear === 'TOUT' || year === selectedYear;
        return matchesSearch && matchesYear;
    });

    // Filtrage des Affectations par Site
    const filteredSiteFeries = siteFeries.filter(sf => {
        const cleanedDate = formatDateForInput(sf.ferieDate);
        const year = cleanedDate.split('-')[0];
        const matchesSearch = (sf.ferieLibelle || '').toLowerCase().includes(search.toLowerCase()) || (sf.siteLibelle || '').toLowerCase().includes(search.toLowerCase());
        const matchesYear = selectedYear === 'TOUT' || year === selectedYear;
        const matchesSite = !selectedSiteFilter || String(sf.idSite) === String(selectedSiteFilter);
        return matchesSearch && matchesYear && matchesSite;
    });

    // Fériés régionaux uniquement pour la modale d'affectation
    const regionalFeries = feries.filter(f => !f.estNational);

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
                    onClick={() => setSubSection('feries')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${subSection === 'feries'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                >
                    <Calendar size={16} />
                    Jours Fériés Officiels ({feries.length})
                </button>
                <button
                    onClick={() => setSubSection('sites_feries')}
                    className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${subSection === 'sites_feries'
                            ? 'bg-brand-500 text-white'
                            : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                >
                    <MapPin size={16} />
                    Fériés Régionaux par Site ({siteFeries.length})
                </button>
            </div>

            {/* Barre d'outils et Recherche / Filtres */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="flex flex-1 flex-col sm:flex-row gap-3">
                    <div className="relative flex-1 max-w-xs">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={16} />
                        <input
                            type="text"
                            placeholder="Rechercher par jour, date..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-9 pr-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                        />
                    </div>

                    {/* Filtre Année */}
                    <div className="flex items-center gap-2">
                        <Filter size={15} className="text-ink-400 shrink-0" />
                        <select
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                            className="px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm font-semibold focus:outline-none focus:border-brand-500"
                        >
                            <option value="TOUT">Toutes les années</option>
                            {availableYears.map(yr => (
                                <option key={yr} value={yr}>Année {yr}</option>
                            ))}
                        </select>
                    </div>

                    {/* Filtre Site si on est sur la section Site */}
                    {subSection === 'sites_feries' && (
                        <div className="flex items-center gap-2">
                            <select
                                value={selectedSiteFilter}
                                onChange={(e) => setSelectedSiteFilter(e.target.value)}
                                className="px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm font-semibold focus:outline-none focus:border-brand-500"
                            >
                                <option value="">Tous les sites</option>
                                {sites.map(s => (
                                    <option key={s.idSite} value={s.idSite}>{s.libelle}</option>
                                ))}
                            </select>
                        </div>
                    )}
                </div>

                {subSection === 'feries' ? (
                    <button
                        onClick={() => {
                            setEditingFerie(null);
                            setFerieFormData({ dateFerie: '', libelle: '', estChome: true, estNational: true });
                            setActionError(null);
                            setShowFerieModal(true);
                        }}
                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                    >
                        <Plus size={16} />
                        Nouveau Jour Férié
                    </button>
                ) : (
                    <button
                        onClick={() => {
                            setSiteFerieFormData({
                                idSite: sites.length > 0 ? sites[0].idSite : '',
                                idFerie: regionalFeries.length > 0 ? regionalFeries[0].idFerie : (feries.length > 0 ? feries[0].idFerie : '')
                            });
                            setActionError(null);
                            setShowSiteFerieModal(true);
                        }}
                        className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                    >
                        <Plus size={16} />
                        Affecter Férié à un Site
                    </button>
                )}
            </div>

            {/* SECTION 1: CALENDRIER FERIES */}
            {subSection === 'feries' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="bg-brand-500/10 border border-brand-500/20 text-brand-600 dark:text-brand-400 p-3 rounded-sm text-xs flex items-start gap-2">
                        <Info size={16} className="shrink-0 mt-0.5" />
                        <div>
                            <strong>Règle de Portée Légale :</strong> Les jours avec le statut
                            <span className="font-bold underline mx-1">National</span> s'appliquent automatiquement à
                            <strong> tous les sites d'exploitation</strong> de Madagascar. Seuls les jours <span className="font-bold underline mx-1">Régionaux</span> nécessitent d'être associés à un site spécifique.
                        </div>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Date (JJ/MM/AAAA)</th>
                                    <th className="px-4 py-3">Libellé du Jour Férié</th>
                                    <th className="px-4 py-3">Portée Légale</th>
                                    <th className="px-4 py-3">Régime de Paie</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {filteredFeries.length > 0 ? (
                                    filteredFeries.map((f) => (
                                        <tr key={f.idFerie} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-mono font-bold text-brand-500">
                                                {formatDateDisplay(f.dateFerie)}
                                            </td>
                                            <td className="px-4 py-3 font-bold">{f.libelle}</td>
                                            <td className="px-4 py-3">
                                                {f.estNational ? (
                                                    <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 bg-sky-500/10 text-sky-500 font-semibold rounded-sm">
                                                        National (Tous les sites)
                                                    </span>
                                                ) : (
                                                    <span className="inline-flex items-center gap-1 text-xs px-2 py-0.5 bg-purple-500/10 text-purple-500 font-semibold rounded-sm">
                                                        Régional / Local (Par site)
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3">
                                                {f.estChome ? (
                                                    <span className="text-xs px-2 py-0.5 bg-emerald-500/10 text-emerald-500 font-semibold rounded-sm">
                                                        Chômé & Payé (+100% si travaillé)
                                                    </span>
                                                ) : (
                                                    <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 font-semibold rounded-sm">
                                                        Travaillé (Taux normal)
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-4 py-3 text-right">
                                                <button
                                                    onClick={() => {
                                                        setEditingFerie(f);
                                                        setFerieFormData({
                                                            dateFerie: formatDateForInput(f.dateFerie),
                                                            libelle: f.libelle,
                                                            estChome: f.estChome,
                                                            estNational: f.estNational
                                                        });
                                                        setActionError(null);
                                                        setShowFerieModal(true);
                                                    }}
                                                    className="p-1 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm cursor-pointer"
                                                >
                                                    <Edit2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="5" className="px-4 py-8 text-center text-ink-400">
                                            Aucun jour férié trouvé pour ce filtre.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* SECTION 2: AFFECTATION PAR SITE */}
            {subSection === 'sites_feries' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <div>
                            <h4 className="font-bold text-ink-900 dark:text-white text-md">Fériés Régionaux attribués aux Sites</h4>
                            <p className="text-xs text-ink-400">Jours fériés spécifiques appliqués uniquement au personnel du site désigné.</p>
                        </div>
                    </div>

                    <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-4 py-3">Site d'Exploitation</th>
                                    <th className="px-4 py-3">Date (JJ/MM/AAAA)</th>
                                    <th className="px-4 py-3">Jour Férié</th>
                                    <th className="px-4 py-3 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {filteredSiteFeries.length > 0 ? (
                                    filteredSiteFeries.map((sf) => (
                                        <tr key={sf.idSiteFerie} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-4 py-3 font-bold flex items-center gap-2">
                                                <MapPin size={15} className="text-brand-500" />
                                                {sf.siteLibelle}
                                            </td>
                                            <td className="px-4 py-3 font-mono font-semibold text-brand-500">
                                                {formatDateDisplay(sf.ferieDate)}
                                            </td>
                                            <td className="px-4 py-3 font-medium">{sf.ferieLibelle}</td>
                                            <td className="px-4 py-3 text-right">
                                                <button
                                                    onClick={() => handleDeleteSiteFerie(sf.idSiteFerie)}
                                                    className="p-1 text-danger-500 hover:bg-danger-500/10 rounded-sm cursor-pointer"
                                                    title="Supprimer la liaison"
                                                >
                                                    <Trash2 size={15} />
                                                </button>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="4" className="px-4 py-8 text-center text-ink-400">
                                            Aucune affectation régionale spécifique enregistrée.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Modale Férié */}
            {showFerieModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingFerie ? "Modifier le Jour Férié" : "Nouveau Jour Férié"}
                        </div>
                        <form onSubmit={handleSaveFerie}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Date du Jour Férié</label>
                                    <input
                                        type="date"
                                        value={ferieFormData.dateFerie}
                                        onChange={(e) => setFerieFormData({ ...ferieFormData, dateFerie: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé / Désignation</label>
                                    <input
                                        type="text"
                                        value={ferieFormData.libelle}
                                        onChange={(e) => setFerieFormData({ ...ferieFormData, libelle: e.target.value })}
                                        placeholder="Ex: Fête de l'Indépendance"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-2 pt-2 border-t border-ink-100 dark:border-ink-800">
                                    <label className="flex items-center gap-2 cursor-pointer text-sm font-medium">
                                        <input
                                            type="checkbox"
                                            checked={ferieFormData.estNational}
                                            onChange={(e) => setFerieFormData({ ...ferieFormData, estNational: e.target.checked })}
                                            className="w-4 h-4 text-brand-500 rounded-sm"
                                        />
                                        <span>Férié National</span>
                                    </label>
                                    <label className="flex items-center gap-2 cursor-pointer text-sm font-medium">
                                        <input
                                            type="checkbox"
                                            checked={ferieFormData.estChome}
                                            onChange={(e) => setFerieFormData({ ...ferieFormData, estChome: e.target.checked })}
                                            className="w-4 h-4 text-brand-500 rounded-sm"
                                        />
                                        <span>Chômé & Payé</span>
                                    </label>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowFerieModal(false)}
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

            {/* Modale Site Férié */}
            {showSiteFerieModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            Associer Férié Régional à un Site
                        </div>
                        <form onSubmit={handleSaveSiteFerie}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Site d'Exploitation</label>
                                    <select
                                        value={siteFerieFormData.idSite}
                                        onChange={(e) => setSiteFerieFormData({ ...siteFerieFormData, idSite: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {sites.map((s) => (
                                            <option key={s.idSite} value={s.idSite}>{s.libelle} ({s.codeSite})</option>
                                        ))}
                                    </select>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Jour Férié (Régional / Local)</label>
                                    <select
                                        value={siteFerieFormData.idFerie}
                                        onChange={(e) => setSiteFerieFormData({ ...siteFerieFormData, idFerie: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        {feries.map((f) => (
                                            <option key={f.idFerie} value={f.idFerie}>
                                                {formatDateDisplay(f.dateFerie)} - {f.libelle} {f.estNational ? '(National)' : '(Régional)'}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowSiteFerieModal(false)}
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
        </div>
    );
}
