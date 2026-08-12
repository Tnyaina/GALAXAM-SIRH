import { useState, useEffect } from 'react';
import { CheckCircle, XCircle, Calendar, MapPin, Search, Filter } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';

import JoursFeriesList from './feries/JoursFeriesList';
import JoursFerieFormModal from './feries/JoursFerieFormModal';
import SiteFeriesList from './feries/SiteFeriesList';
import SiteFerieFormModal from './feries/SiteFerieFormModal';

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

    // Modale Site Férié
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
    const handleOpenCreateFerie = () => {
        setEditingFerie(null);
        setFerieFormData({ dateFerie: `${selectedYear}-01-01`, libelle: '', estChome: true, estNational: true });
        setActionError(null);
        setShowFerieModal(true);
    };

    const handleOpenEditFerie = (f) => {
        setEditingFerie(f);
        setFerieFormData({
            dateFerie: formatDateForInput(f.dateFerie),
            libelle: f.libelle,
            estChome: f.estChome !== false,
            estNational: f.estNational !== false
        });
        setActionError(null);
        setShowFerieModal(true);
    };

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
                setActionSuccess(`Jour férié "${ferieFormData.libelle}" ajouté.`);
            }
            setShowFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    // Handlers Site Férié
    const handleOpenCreateSiteFerie = () => {
        setSiteFerieFormData({ idSite: '', idFerie: '' });
        setActionError(null);
        setShowSiteFerieModal(true);
    };

    const handleSaveSiteFerie = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        if (!siteFerieFormData.idSite || !siteFerieFormData.idFerie) {
            setActionError("Le site et le jour férié sont obligatoires.");
            return;
        }
        const payload = {
            idSite: parseInt(siteFerieFormData.idSite),
            idFerie: parseInt(siteFerieFormData.idFerie)
        };
        try {
            await paieConfigService.createSiteFerie(payload);
            setActionSuccess("Jour férié associé au site.");
            setShowSiteFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur d'affectation.");
        }
    };

    const handleDeleteSiteFerie = async (id) => {
        setActionSuccess(null);
        setActionError(null);
        try {
            await paieConfigService.deleteSiteFerie(id);
            setActionSuccess("Association supprimée.");
            setSiteFeries(prev => prev.filter(item => item.idSiteFerie !== id));
        } catch (err) {
            setActionError("Erreur lors de la suppression.");
            console.error(err);
        }
    };

    // Filtrage dynamique
    const filteredFeries = feries.filter(f => {
        const matchesYear = selectedYear === 'tous' || (f.dateFerie && f.dateFerie.startsWith(selectedYear));
        const matchesSearch = !search || f.libelle.toLowerCase().includes(search.toLowerCase());
        return matchesYear && matchesSearch;
    });

    const filteredSiteFeries = siteFeries.filter(sf => {
        const matchesYear = selectedYear === 'tous' || (sf.dateFerie && sf.dateFerie.startsWith(selectedYear));
        const matchesSite = !selectedSiteFilter || String(sf.idSite) === String(selectedSiteFilter);
        const matchesSearch = !search || (sf.libelleFerie && sf.libelleFerie.toLowerCase().includes(search.toLowerCase())) ||
            (sf.libelleSite && sf.libelleSite.toLowerCase().includes(search.toLowerCase()));
        return matchesYear && matchesSite && matchesSearch;
    });

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

            {/* Onglets internes + Filtres */}
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center border-b border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 rounded-sm p-2 gap-3 shadow-sm">
                <div className="flex gap-2 flex-wrap">
                    <button
                        onClick={() => setSubSection('feries')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            subSection === 'feries'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <Calendar size={16} />
                        Jours Fériés Nationaux ({filteredFeries.length})
                    </button>
                    <button
                        onClick={() => setSubSection('sites_feries')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            subSection === 'sites_feries'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <MapPin size={16} />
                        Exceptions par Site ({filteredSiteFeries.length})
                    </button>
                </div>

                <div className="flex items-center gap-2 flex-wrap w-full md:w-auto">
                    {/* Recherche textuelle */}
                    <div className="relative flex-1 md:flex-initial">
                        <Search size={14} className="absolute left-2.5 top-2.5 text-ink-400" />
                        <input
                            type="text"
                            placeholder="Rechercher un férié..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="pl-8 pr-3 py-1.5 text-xs bg-ink-50 dark:bg-ink-950 border border-ink-200 dark:border-ink-700 rounded-sm text-ink-900 dark:text-white focus:outline-none focus:border-brand-500"
                        />
                    </div>

                    {/* Filtre Année */}
                    <div className="flex items-center gap-1 bg-ink-50 dark:bg-ink-950 px-2 py-1 border border-ink-200 dark:border-ink-700 rounded-sm">
                        <Filter size={12} className="text-ink-400" />
                        <select
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                            className="bg-transparent text-xs font-semibold text-ink-800 dark:text-ink-200 focus:outline-none cursor-pointer"
                        >
                            <option value="2026">Année 2026</option>
                            <option value="2025">Année 2025</option>
                            <option value="2027">Année 2027</option>
                            <option value="tous">Toutes les années</option>
                        </select>
                    </div>

                    {/* Filtre Site si sous-section sites_feries */}
                    {subSection === 'sites_feries' && (
                        <select
                            value={selectedSiteFilter}
                            onChange={(e) => setSelectedSiteFilter(e.target.value)}
                            className="bg-ink-50 dark:bg-ink-950 text-xs font-semibold text-ink-800 dark:text-ink-200 px-2 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm focus:outline-none cursor-pointer"
                        >
                            <option value="">Tous les sites</option>
                            {sites.map(s => (
                                <option key={s.idSite} value={s.idSite}>{s.libelle}</option>
                            ))}
                        </select>
                    )}
                </div>
            </div>

            {/* SubSection 1: Fériés Nationaux */}
            {subSection === 'feries' && (
                <JoursFeriesList
                    feries={filteredFeries}
                    selectedYear={selectedYear}
                    search={search}
                    onOpenCreate={handleOpenCreateFerie}
                    onOpenEdit={handleOpenEditFerie}
                />
            )}

            {/* SubSection 2: Exceptions par Site */}
            {subSection === 'sites_feries' && (
                <SiteFeriesList
                    siteFeries={filteredSiteFeries}
                    selectedYear={selectedYear}
                    search={search}
                    onOpenCreate={handleOpenCreateSiteFerie}
                    onDelete={handleDeleteSiteFerie}
                />
            )}

            {/* Modale Férié */}
            <JoursFerieFormModal
                isOpen={showFerieModal}
                onClose={() => setShowFerieModal(false)}
                onSubmit={handleSaveFerie}
                editingFerie={editingFerie}
                formData={ferieFormData}
                setFormData={setFerieFormData}
                actionError={actionError}
            />

            {/* Modale Site Férié */}
            <SiteFerieFormModal
                isOpen={showSiteFerieModal}
                onClose={() => setShowSiteFerieModal(false)}
                onSubmit={handleSaveSiteFerie}
                formData={siteFerieFormData}
                setFormData={setSiteFerieFormData}
                sites={sites}
                feries={feries}
                actionError={actionError}
            />
        </div>
    );
}
