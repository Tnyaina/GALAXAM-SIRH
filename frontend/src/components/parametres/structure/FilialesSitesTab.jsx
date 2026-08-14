import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Plus, CheckCircle, XCircle, Building2, MapPin, Eye } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';
import Button from '@/components/ui/Button';
import Loading from '@/components/ui/Loading';

import FilialesList from './filiales/FilialesList';
import FilialeFormModal from './filiales/FilialeFormModal';
import SitesList from './sites/SitesList';
import SiteFormModal from './sites/SiteFormModal';

export default function FilialesSitesTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeSubTab = searchParams.get('subtab') || 'filiales'; // 'filiales' | 'sites'

    const setActiveSubTab = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'filiales');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const [filiales, setFiliales] = useState([]);
    const [sites, setSites] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');
    const [showInactive, setShowInactive] = useState(false);

    // Modales Filiale
    const [showFilialeModal, setShowFilialeModal] = useState(false);
    const [selectedFiliale, setSelectedFiliale] = useState(null);
    const [filialeFormData, setFilialeFormData] = useState({
        codeFiliale: '', raisonSociale: '', nif: '', stat: '', seuilEffectifAlerte: 19
    });

    // Modales Site
    const [showSiteModal, setShowSiteModal] = useState(false);
    const [selectedSite, setSelectedSite] = useState(null);
    const [siteFormData, setSiteFormData] = useState({
        codeSite: '', libelle: '', idFilialeRattachement: '', horaireContinu: true,
        heureDebutMatin: '08:00', heureFinMatin: '12:00', heureDebutApresMidi: '13:00', heureFinApresMidi: '17:00'
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [filialesData, sitesData] = await Promise.all([
                organisationService.getFiliales(),
                organisationService.getSites()
            ]);
            setFiliales(filialesData || []);
            setSites(sitesData || []);
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
        setFilialeFormData({ codeFiliale: '', raisonSociale: '', nif: '', stat: '', seuilEffectifAlerte: 19 });
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
            codeSite: '', libelle: '',
            idFilialeRattachement: '',
            horaireContinu: true,
            heureDebutMatin: '08:00', heureFinMatin: '12:00',
            heureDebutApresMidi: '13:00', heureFinApresMidi: '17:00'
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
            horaireContinu: site.horaireContinu !== false,
            heureDebutMatin: site.heureDebutMatin ? site.heureDebutMatin.substring(0, 5) : '08:00',
            heureFinMatin: site.heureFinMatin ? site.heureFinMatin.substring(0, 5) : '12:00',
            heureDebutApresMidi: site.heureDebutApresMidi ? site.heureDebutApresMidi.substring(0, 5) : '13:00',
            heureFinApresMidi: site.heureFinApresMidi ? site.heureFinApresMidi.substring(0, 5) : '17:00'
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

    const filteredFiliales = filiales.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFiliale?.toLowerCase().includes(search.toLowerCase()) ||
         f.raisonSociale?.toLowerCase().includes(search.toLowerCase()))
    );

    const filteredSites = sites.filter(s =>
        (showInactive || s.estActif !== false) &&
        (s.codeSite?.toLowerCase().includes(search.toLowerCase()) ||
         s.libelle?.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && filiales.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <Loading size="md" label="Chargement des filiales et sites..." />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'outils et sous-onglets */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex gap-2">
                    <button
                        onClick={() => setActiveSubTab('filiales')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-none transition-all cursor-pointer ${
                            activeSubTab === 'filiales'
                                ? 'bg-[var(--color-brand-500)] text-white'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        <Building2 size={16} />
                        Filiales ({filiales.length})
                    </button>
                    <button
                        onClick={() => setActiveSubTab('sites')}
                        className={`flex items-center gap-2 px-4 py-2 text-sm font-semibold rounded-none transition-all cursor-pointer ${
                            activeSubTab === 'sites'
                                ? 'bg-[var(--color-brand-500)] text-white'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)]'
                        }`}
                    >
                        <MapPin size={16} />
                        Sites & Horaires ({sites.length})
                    </button>
                </div>

                <div className="flex gap-3 items-center flex-wrap">
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                    <div className="relative flex-1 sm:w-64">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" size={16} />
                        <input
                            type="text"
                            placeholder="Rechercher..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-9 pr-3 py-1.5 border border-[var(--color-border)] rounded-none bg-[var(--color-surface)] text-sm text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                        />
                    </div>
                    {activeSubTab === 'filiales' ? (
                        <Button
                            onClick={handleOpenCreateFiliale}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Nouvelle Filiale
                        </Button>
                    ) : (
                        <Button
                            onClick={handleOpenCreateSite}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Nouveau Site
                        </Button>
                    )}
                </div>
            </div>

            {/* SubTab 1: Filiales */}
            {activeSubTab === 'filiales' && (
                <FilialesList
                    filiales={filteredFiliales}
                    onOpenEdit={handleOpenEditFiliale}
                    onToggleStatus={handleToggleFilialeStatus}
                />
            )}

            {/* SubTab 2: Sites */}
            {activeSubTab === 'sites' && (
                <SitesList
                    sites={filteredSites}
                    onOpenEdit={handleOpenEditSite}
                    onToggleStatus={handleToggleSiteStatus}
                />
            )}

            {/* Modale Filiale */}
            <FilialeFormModal
                isOpen={showFilialeModal}
                onClose={() => setShowFilialeModal(false)}
                onSubmit={handleSaveFiliale}
                selectedFiliale={selectedFiliale}
                formData={filialeFormData}
                setFormData={setFilialeFormData}
                actionError={actionError}
            />

            {/* Modale Site */}
            <SiteFormModal
                isOpen={showSiteModal}
                onClose={() => setShowSiteModal(false)}
                onSubmit={handleSaveSite}
                selectedSite={selectedSite}
                formData={siteFormData}
                setFormData={setSiteFormData}
                filiales={filiales}
                actionError={actionError}
            />
        </div>
    );
}