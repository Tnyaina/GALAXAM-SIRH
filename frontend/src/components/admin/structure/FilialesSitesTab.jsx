import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, CheckCircle, XCircle, Building2, MapPin, Eye } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';

export default function FilialesSitesTab() {
    const [filiales, setFiliales] = useState([]);
    const [sites, setSites] = useState([]);
    const [activeSubTab, setActiveSubTab] = useState('filiales'); // 'filiales' | 'sites'
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');

    // Modales Filiale
    const [showFilialeModal, setShowFilialeModal] = useState(false);
    const [selectedFiliale, setSelectedFiliale] = useState(null);
    const [filialeFormData, setFilialeFormData] = useState({
        codeFiliale: '',
        raisonSociale: '',
        nif: '',
        stat: '',
        seuilEffectifAlerte: 19
    });

    // Modales Site
    const [showSiteModal, setShowSiteModal] = useState(false);
    const [selectedSite, setSelectedSite] = useState(null);
    const [siteFormData, setSiteFormData] = useState({
        codeSite: '',
        libelle: '',
        idFilialeRattachement: '',
        horaireContinu: true,
        heureDebutMatin: '08:00',
        heureFinMatin: '12:00',
        heureDebutApresMidi: '13:00',
        heureFinApresMidi: '17:00'
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [filialesData, sitesData] = await Promise.all([
                organisationService.getFiliales(),
                organisationService.getSites()
            ]);
            setFiliales(filialesData);
            setSites(sitesData);
        } catch (err) {
            setError("Erreur lors du chargement des données des filiales et sites.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Filiale Handlers
    const handleOpenCreateFiliale = () => {
        setSelectedFiliale(null);
        setFilialeFormData({
            codeFiliale: '',
            raisonSociale: '',
            nif: '',
            stat: '',
            seuilEffectifAlerte: 19
        });
        setActionError(null);
        setShowFilialeModal(true);
    };

    const handleOpenEditFiliale = (filiale) => {
        setSelectedFiliale(filiale);
        setFilialeFormData({
            codeFiliale: filiale.codeFiliale,
            raisonSociale: filiale.raisonSociale,
            nif: filiale.nif || '',
            stat: filiale.stat || '',
            seuilEffectifAlerte: filiale.seuilEffectifAlerte ?? 19
        });
        setActionError(null);
        setShowFilialeModal(true);
    };

    const handleSaveFiliale = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!filialeFormData.codeFiliale.trim()) {
            setActionError("Le code filiale est obligatoire.");
            return;
        }
        if (!filialeFormData.raisonSociale.trim()) {
            setActionError("La raison sociale est obligatoire.");
            return;
        }

        try {
            if (selectedFiliale) {
                await organisationService.updateFiliale(selectedFiliale.idFiliale, filialeFormData);
                setActionSuccess(`La filiale "${filialeFormData.raisonSociale}" a été modifiée.`);
            } else {
                await organisationService.createFiliale(filialeFormData);
                setActionSuccess(`La filiale "${filialeFormData.raisonSociale}" a été créée.`);
            }
            setShowFilialeModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    const handleToggleFilialeStatus = async (filiale) => {
        setActionError(null);
        setActionSuccess(null);
        try {
            if (filiale.estActif) {
                await organisationService.deactivateFiliale(filiale.idFiliale);
                setActionSuccess(`Filiale "${filiale.raisonSociale}" désactivée.`);
            } else {
                await organisationService.activateFiliale(filiale.idFiliale);
                setActionSuccess(`Filiale "${filiale.raisonSociale}" réactivée.`);
            }
            loadData();
        } catch (err) {
            setError(err.response?.data || "Erreur lors du changement de statut.");
        }
    };

    // Site Handlers
    const handleOpenCreateSite = () => {
        setSelectedSite(null);
        setSiteFormData({
            codeSite: '',
            libelle: '',
            idFilialeRattachement: filiales.length > 0 ? filiales[0].idFiliale : '',
            horaireContinu: true,
            heureDebutMatin: '08:00',
            heureFinMatin: '12:00',
            heureDebutApresMidi: '13:00',
            heureFinApresMidi: '17:00'
        });
        setActionError(null);
        setShowSiteModal(true);
    };

    const handleOpenEditSite = (site) => {
        setSelectedSite(site);
        setSiteFormData({
            codeSite: site.codeSite,
            libelle: site.libelle,
            idFilialeRattachement: site.idFilialeRattachement || '',
            horaireContinu: site.horaireContinu,
            heureDebutMatin: site.heureDebutMatin || '08:00',
            heureFinMatin: site.heureFinMatin || '12:00',
            heureDebutApresMidi: site.heureDebutApresMidi || '13:00',
            heureFinApresMidi: site.heureFinApresMidi || '17:00'
        });
        setActionError(null);
        setShowSiteModal(true);
    };

    const handleSaveSite = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!siteFormData.codeSite.trim()) {
            setActionError("Le code du site est obligatoire.");
            return;
        }
        if (!siteFormData.libelle.trim()) {
            setActionError("Le libellé du site est obligatoire.");
            return;
        }

        const payload = {
            ...siteFormData,
            idFilialeRattachement: siteFormData.idFilialeRattachement ? parseInt(siteFormData.idFilialeRattachement) : null,
            heureDebutMatin: siteFormData.heureDebutMatin ? siteFormData.heureDebutMatin + (siteFormData.heureDebutMatin.length === 5 ? ':00' : '') : null,
            heureFinMatin: siteFormData.heureFinMatin ? siteFormData.heureFinMatin + (siteFormData.heureFinMatin.length === 5 ? ':00' : '') : null,
            heureDebutApresMidi: siteFormData.heureDebutApresMidi ? siteFormData.heureDebutApresMidi + (siteFormData.heureDebutApresMidi.length === 5 ? ':00' : '') : null,
            heureFinApresMidi: siteFormData.heureFinApresMidi ? siteFormData.heureFinApresMidi + (siteFormData.heureFinApresMidi.length === 5 ? ':00' : '') : null
        };

        try {
            if (selectedSite) {
                await organisationService.updateSite(selectedSite.idSite, payload);
                setActionSuccess(`Le site "${siteFormData.libelle}" a été modifié.`);
            } else {
                await organisationService.createSite(payload);
                setActionSuccess(`Le site "${siteFormData.libelle}" a été créé.`);
            }
            setShowSiteModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde du site.");
        }
    };

    const handleToggleSiteStatus = async (site) => {
        setActionError(null);
        setActionSuccess(null);
        try {
            if (site.estActif) {
                await organisationService.deactivateSite(site.idSite);
                setActionSuccess(`Site "${site.libelle}" désactivé.`);
            } else {
                await organisationService.activateSite(site.idSite);
                setActionSuccess(`Site "${site.libelle}" réactivé.`);
            }
            loadData();
        } catch (err) {
            setError(err.response?.data || "Erreur lors du changement de statut.");
        }
    };

    const [showInactive, setShowInactive] = useState(false);

    const filteredFiliales = filiales.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFiliale.toLowerCase().includes(search.toLowerCase()) ||
         f.raisonSociale.toLowerCase().includes(search.toLowerCase()))
    );

    const filteredSites = sites.filter(s =>
        (showInactive || s.estActif !== false) &&
        (s.codeSite.toLowerCase().includes(search.toLowerCase()) ||
         s.libelle.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && filiales.length === 0) {
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

            {/* Barre d'outils et sous-onglets */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="flex gap-2">
                    <button
                        onClick={() => setActiveSubTab('filiales')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            activeSubTab === 'filiales'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <Building2 size={16} />
                        Filiales ({filiales.length})
                    </button>
                    <button
                        onClick={() => setActiveSubTab('sites')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-sm transition-all cursor-pointer ${
                            activeSubTab === 'sites'
                                ? 'bg-brand-500 text-white'
                                : 'bg-ink-50 dark:bg-ink-950 text-ink-600 dark:text-ink-300 hover:bg-ink-100 dark:hover:bg-ink-800'
                        }`}
                    >
                        <MapPin size={16} />
                        Sites & Horaires ({sites.length})
                    </button>
                </div>

                <div className="flex gap-3 items-center">
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
                    <div className="relative flex-1 sm:w-64">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={16} />
                        <input
                            type="text"
                            placeholder="Rechercher..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-9 pr-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                        />
                    </div>
                    {activeSubTab === 'filiales' ? (
                        <button
                            onClick={handleOpenCreateFiliale}
                            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                        >
                            <Plus size={16} />
                            Nouvelle Filiale
                        </button>
                    ) : (
                        <button
                            onClick={handleOpenCreateSite}
                            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer shrink-0"
                        >
                            <Plus size={16} />
                            Nouveau Site
                        </button>
                    )}
                </div>
            </div>

            {/* TAB 1: FILIALES */}
            {activeSubTab === 'filiales' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-6 py-4">Code</th>
                                    <th className="px-6 py-4">Raison Sociale</th>
                                    <th className="px-6 py-4">NIF</th>
                                    <th className="px-6 py-4">STAT</th>
                                    <th className="px-6 py-4">Seuil Alerte Effectif</th>
                                    <th className="px-6 py-4">Statut</th>
                                    <th className="px-6 py-4 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {filteredFiliales.length > 0 ? (
                                    filteredFiliales.map((f) => (
                                        <tr key={f.idFiliale} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-6 py-4 font-mono font-semibold text-brand-500">{f.codeFiliale}</td>
                                            <td className="px-6 py-4 font-bold">{f.raisonSociale}</td>
                                            <td className="px-6 py-4 font-mono text-xs">{f.nif || '-'}</td>
                                            <td className="px-6 py-4 font-mono text-xs">{f.stat || '-'}</td>
                                            <td className="px-6 py-4 text-xs">
                                                <span className="px-2 py-0.5 bg-ink-100 dark:bg-ink-800 text-ink-600 dark:text-ink-300 rounded-sm font-semibold">
                                                    {f.seuilEffectifAlerte ?? 19} pers.
                                                </span>
                                            </td>
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
                                                        onClick={() => handleOpenEditFiliale(f)}
                                                        className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={16} />
                                                    </button>
                                                    <button
                                                        onClick={() => handleToggleFilialeStatus(f)}
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
                                        <td colSpan="7" className="px-6 py-12 text-center text-ink-400">
                                            Aucune filiale enregistrée.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* TAB 2: SITES */}
            {activeSubTab === 'sites' && (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-6 py-4">Code Site</th>
                                    <th className="px-6 py-4">Nom du Site</th>
                                    <th className="px-6 py-4">Filiale Rattachement</th>
                                    <th className="px-6 py-4">Régime Horaire</th>
                                    <th className="px-6 py-4">Horaires Matin / Après-midi</th>
                                    <th className="px-6 py-4">Statut</th>
                                    <th className="px-6 py-4 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {filteredSites.length > 0 ? (
                                    filteredSites.map((s) => (
                                        <tr key={s.idSite} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-6 py-4 font-mono font-semibold text-brand-500">{s.codeSite}</td>
                                            <td className="px-6 py-4 font-bold">{s.libelle}</td>
                                            <td className="px-6 py-4 font-medium text-xs">
                                                {s.filialeRaisonSociale ? (
                                                    <span className="px-2 py-0.5 bg-brand-500/10 text-brand-500 rounded-sm">
                                                        {s.filialeRaisonSociale}
                                                    </span>
                                                ) : (
                                                    <span className="text-ink-300">-</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                {s.horaireContinu ? (
                                                    <span className="text-xs px-2 py-0.5 bg-sky-500/10 text-sky-500 font-semibold rounded-sm">
                                                        Horaire Continu
                                                    </span>
                                                ) : (
                                                    <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 font-semibold rounded-sm">
                                                        Discontinu (Pause)
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 text-xs font-mono text-ink-500">
                                                <div>Matin : {s.heureDebutMatin || '--'} - {s.heureFinMatin || '--'}</div>
                                                <div>A-Midi : {s.heureDebutApresMidi || '--'} - {s.heureFinApresMidi || '--'}</div>
                                            </td>
                                            <td className="px-6 py-4">
                                                {s.estActif ? (
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
                                                        onClick={() => handleOpenEditSite(s)}
                                                        className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={16} />
                                                    </button>
                                                    <button
                                                        onClick={() => handleToggleSiteStatus(s)}
                                                        className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                            s.estActif
                                                                ? 'text-danger-500 hover:bg-danger-500/10'
                                                                : 'text-success-500 hover:bg-success-500/10'
                                                        }`}
                                                    >
                                                        {s.estActif ? "Désactiver" : "Activer"}
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    ))
                                ) : (
                                    <tr>
                                        <td colSpan="7" className="px-6 py-12 text-center text-ink-400">
                                            Aucun site d'exploitation enregistré.
                                        </td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Modale Filiale */}
            {showFilialeModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedFiliale ? "Modifier la Filiale" : "Nouvelle Filiale"}
                        </div>
                        <form onSubmit={handleSaveFiliale}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs">
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Filiale</label>
                                    <input
                                        type="text"
                                        value={filialeFormData.codeFiliale}
                                        onChange={(e) => setFilialeFormData({ ...filialeFormData, codeFiliale: e.target.value })}
                                        placeholder="Ex: LOG, VA"
                                        disabled={!!selectedFiliale}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Raison Sociale</label>
                                    <input
                                        type="text"
                                        value={filialeFormData.raisonSociale}
                                        onChange={(e) => setFilialeFormData({ ...filialeFormData, raisonSociale: e.target.value })}
                                        placeholder="Ex: Galaxam Logistics SARL"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">NIF</label>
                                        <input
                                            type="text"
                                            value={filialeFormData.nif}
                                            onChange={(e) => setFilialeFormData({ ...filialeFormData, nif: e.target.value })}
                                            placeholder="Numéro NIF"
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">STAT</label>
                                        <input
                                            type="text"
                                            value={filialeFormData.stat}
                                            onChange={(e) => setFilialeFormData({ ...filialeFormData, stat: e.target.value })}
                                            placeholder="Numéro STAT"
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Seuil d'Alerte Effectif</label>
                                    <input
                                        type="number"
                                        value={filialeFormData.seuilEffectifAlerte}
                                        onChange={(e) => setFilialeFormData({ ...filialeFormData, seuilEffectifAlerte: parseInt(e.target.value) || 0 })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowFilialeModal(false)}
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

            {/* Modale Site */}
            {showSiteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedSite ? "Modifier le Site" : "Nouveau Site"}
                        </div>
                        <form onSubmit={handleSaveSite}>
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
                                        value={siteFormData.codeSite}
                                        onChange={(e) => setSiteFormData({ ...siteFormData, codeSite: e.target.value })}
                                        disabled={!!selectedSite}
                                        placeholder="Ex: TNR, TMM"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Nom / Libellé</label>
                                    <input
                                        type="text"
                                        value={siteFormData.libelle}
                                        onChange={(e) => setSiteFormData({ ...siteFormData, libelle: e.target.value })}
                                        placeholder="Ex: Antananarivo - Siège"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Filiale de Rattachement</label>
                                    <select
                                        value={siteFormData.idFilialeRattachement}
                                        onChange={(e) => setSiteFormData({ ...siteFormData, idFilialeRattachement: e.target.value })}
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
                                        checked={siteFormData.horaireContinu}
                                        onChange={(e) => setSiteFormData({ ...siteFormData, horaireContinu: e.target.checked })}
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
                                            value={siteFormData.heureDebutMatin}
                                            onChange={(e) => setSiteFormData({ ...siteFormData, heureDebutMatin: e.target.value })}
                                            className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Fin Matin</label>
                                        <input
                                            type="time"
                                            value={siteFormData.heureFinMatin}
                                            onChange={(e) => setSiteFormData({ ...siteFormData, heureFinMatin: e.target.value })}
                                            className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Début Après-midi</label>
                                        <input
                                            type="time"
                                            value={siteFormData.heureDebutApresMidi}
                                            onChange={(e) => setSiteFormData({ ...siteFormData, heureDebutApresMidi: e.target.value })}
                                            className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Fin Après-midi</label>
                                        <input
                                            type="time"
                                            value={siteFormData.heureFinApresMidi}
                                            onChange={(e) => setSiteFormData({ ...siteFormData, heureFinApresMidi: e.target.value })}
                                            className="w-full px-3 py-1.5 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowSiteModal(false)}
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
