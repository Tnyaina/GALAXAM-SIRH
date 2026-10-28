import { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Plus, Building2, MapPin, Eye, LayoutGrid, Table2 } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SideTabs from '@/components/ui/navigation/SideTabs';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';

import FilialesList from '../views/FilialesList';
import SitesList from '../views/SitesList';
import FilialesCardsView from '../views/FilialesCardsView';
import SitesCardsView from '../views/SitesCardsView';
import FilialeFormModal from '../modals/FilialeFormModal';
import SiteFormModal from '../modals/SiteFormModal';

export default function FilialesSitesTab() {
    const { toast } = useToast();
    const [searchParams, setSearchParams] = useSearchParams();

    // Onglet interne via l'URL
    const activeSubTab = searchParams.get('subtab') || 'filiales';
    const setActiveSubTab = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'filiales');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    // État local
    const [filiales, setFiliales] = useState([]);
    const [sites, setSites] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [search, setSearch] = useState('');
    const [showInactive, setShowInactive] = useState(false);
    const [filterFilialeId, setFilterFilialeId] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [viewMode, setViewMode] = useState('cards');

    // État de confirmation
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        onConfirm: null,
        loading: false
    });

    // Modales Filiale
    const [showFilialeModal, setShowFilialeModal] = useState(false);
    const [selectedFiliale, setSelectedFiliale] = useState(null);
    const [filialeFormData, setFilialeFormData] = useState({
        codeFiliale: '', raisonSociale: '', nif: '', stat: '', adresseSiege: '', rcs: '', telephone: '', email: ''
    });
    const [filialeFieldErrors, setFilialeFieldErrors] = useState({});

    // Modales Site
    const [showSiteModal, setShowSiteModal] = useState(false);
    const [selectedSite, setSelectedSite] = useState(null);
    const [siteFormData, setSiteFormData] = useState({
        codeSite: '', libelle: '', idFilialeRattachement: '',
        horaireContinu: true,
        heureDebutMatin: '08:00', heureFinMatin: '12:30',
        heureDebutApresMidi: '13:00', heureFinApresMidi: '16:30',
        plancherArriveeActif: false,
        heureEntreeMinimaleValorisee: '07:30'
    });
    const [siteFieldErrors, setSiteFieldErrors] = useState({});

    // Chargement des données
    const loadData = useCallback(async () => {
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
            setError("Erreur lors du chargement des structures. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, []);

    // Effet : charge les filiales et sites à l'ouverture de l'onglet.
    useEffect(() => {
        loadData();
    }, [loadData]);

    // Actions utilisateur : filiales, sites et confirmations.
    const handleOpenCreateFiliale = () => {
        setSelectedFiliale(null);
        setFilialeFormData({ codeFiliale: '', raisonSociale: '', nif: '', stat: '', adresseSiege: '', rcs: '', telephone: '', email: '' });
        setFilialeFieldErrors({});
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
            adresseSiege: filiale.adresseSiege || '',
            rcs: filiale.rcs || '',
            telephone: filiale.telephone || '',
            email: filiale.email || ''
        });
        setFilialeFieldErrors({});
        setActionError(null);
        setShowFilialeModal(true);
    };

    const handleSaveFiliale = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!filialeFormData.codeFiliale.trim()) {
            errors.codeFiliale = "Le code filiale est obligatoire.";
        }
        if (!filialeFormData.raisonSociale.trim()) {
            errors.raisonSociale = "La raison sociale est obligatoire.";
        }
        if (filialeFormData.nif && !/^\d{10}$/.test(filialeFormData.nif.replace(/\s/g, ''))) {
            errors.nif = "Le NIF doit comporter exactement 10 chiffres.";
        }

        if (Object.keys(errors).length > 0) {
            setFilialeFieldErrors(errors);
            return;
        }
        setFilialeFieldErrors({});

        setSubmitting(true);
        try {
            const payload = { ...filialeFormData };
            if (selectedFiliale) {
                await organisationService.updateFiliale(selectedFiliale.idFiliale, payload);
                toast({ title: 'Succès', message: `La filiale "${filialeFormData.raisonSociale}" a été modifiée.`, type: 'success' });
            } else {
                await organisationService.createFiliale(payload);
                toast({ title: 'Succès', message: `La filiale "${filialeFormData.raisonSociale}" a été créée.`, type: 'success' });
            }
            setShowFilialeModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde de la filiale."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleFilialeStatus = (filiale) => {
        const isDeactivating = filiale.estActif;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver la filiale "${filiale.raisonSociale}" ?` : `Réactiver la filiale "${filiale.raisonSociale}" ?`,
            message: isDeactivating
                ? `Attention : La désactivation de cette filiale empêchera l'affectation de nouveaux contrats et la gestion des sites qui lui sont rattachés. Les données existantes resteront historisées.`
                : `Cette filiale redeviendra immédiatement disponible pour les contrats, sites et déclarations.`,
            confirmLabel: isDeactivating ? "Désactiver la filiale" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateFiliale(filiale.idFiliale);
                        toast({ title: 'Succès', message: `La filiale "${filiale.raisonSociale}" a été désactivée.`, type: 'success' });
                    } else {
                        await organisationService.activateFiliale(filiale.idFiliale);
                        toast({ title: 'Succès', message: `La filiale "${filiale.raisonSociale}" a été réactivée.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setError(getErrorMessage(err, "Erreur lors du changement de statut."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    // Site Handlers
    const handleOpenCreateSite = () => {
        setSelectedSite(null);
        setSiteFormData({
            codeSite: '', libelle: '',
            idFilialeRattachement: filterFilialeId || '',
            horaireContinu: true,
            heureDebutMatin: '08:00', heureFinMatin: '12:30',
            heureDebutApresMidi: '13:00', heureFinApresMidi: '16:30',
            plancherArriveeActif: false,
            heureEntreeMinimaleValorisee: '07:30'
        });
        setSiteFieldErrors({});
        setActionError(null);
        setShowSiteModal(true);
    };

    const handleOpenEditSite = (site) => {
        setSelectedSite(site);
        const isContinu = site.horaireContinu !== false;
        setSiteFormData({
            codeSite: site.codeSite,
            libelle: site.libelle,
            idFilialeRattachement: site.idFilialeRattachement || '',
            horaireContinu: isContinu,
            heureDebutMatin: site.heureDebutMatin ? site.heureDebutMatin.substring(0, 5) : '08:00',
            heureFinMatin: site.heureFinMatin ? site.heureFinMatin.substring(0, 5) : (isContinu ? '12:30' : '12:00'),
            heureDebutApresMidi: site.heureDebutApresMidi ? site.heureDebutApresMidi.substring(0, 5) : (isContinu ? '13:00' : '14:00'),
            heureFinApresMidi: site.heureFinApresMidi ? site.heureFinApresMidi.substring(0, 5) : (isContinu ? '16:30' : '18:00'),
            plancherArriveeActif: !!site.plancherArriveeActif,
            heureEntreeMinimaleValorisee: site.heureEntreeMinimaleValorisee ? site.heureEntreeMinimaleValorisee.substring(0, 5) : '07:30'
        });
        setSiteFieldErrors({});
        setActionError(null);
        setShowSiteModal(true);
    };

    const handleSaveSite = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!siteFormData.codeSite.trim()) {
            errors.codeSite = "Le code du site est obligatoire.";
        }
        if (!siteFormData.libelle.trim()) {
            errors.libelle = "Le nom du site est obligatoire.";
        }
        if (siteFormData.plancherArriveeActif && !siteFormData.heureEntreeMinimaleValorisee) {
            errors.heureEntreeMinimaleValorisee = "L'heure minimale valorisée est obligatoire lorsque le plancher d'arrivée est activé.";
        }

        if (Object.keys(errors).length > 0) {
            setSiteFieldErrors(errors);
            return;
        }
        setSiteFieldErrors({});

        const formatTime = (t, fallback) => {
            const val = t || fallback;
            return val.length === 5 ? val + ':00' : val;
        };

        const payload = {
            ...siteFormData,
            idFilialeRattachement: siteFormData.idFilialeRattachement ? parseInt(siteFormData.idFilialeRattachement) : null,
            heureDebutMatin: formatTime(siteFormData.heureDebutMatin, '08:00'),
            heureFinMatin: formatTime(siteFormData.heureFinMatin, '12:00'),
            heureDebutApresMidi: formatTime(siteFormData.heureDebutApresMidi, '13:00'),
            heureFinApresMidi: formatTime(siteFormData.heureFinApresMidi, '17:00'),
            plancherArriveeActif: !!siteFormData.plancherArriveeActif,
            heureEntreeMinimaleValorisee: siteFormData.plancherArriveeActif && siteFormData.heureEntreeMinimaleValorisee
                ? formatTime(siteFormData.heureEntreeMinimaleValorisee, '07:30')
                : null
        };

        setSubmitting(true);
        try {
            if (selectedSite) {
                await organisationService.updateSite(selectedSite.idSite, payload);
                toast({ title: 'Succès', message: `Le site "${siteFormData.libelle}" a été modifié.`, type: 'success' });
            } else {
                await organisationService.createSite(payload);
                toast({ title: 'Succès', message: `Le site "${siteFormData.libelle}" a été créé.`, type: 'success' });
            }
            setShowSiteModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde du site."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestTogglePlancher = (site) => {
        const isDeactivating = site.plancherArriveeActif;
        const heureAffichee = site.heureEntreeMinimaleValorisee ? site.heureEntreeMinimaleValorisee.substring(0, 5) : '07:30';
        setConfirmState({
            isOpen: true,
            title: isDeactivating
                ? `Désactiver le plancher d'arrivée pour "${site.libelle}" ?`
                : `Activer le plancher d'arrivée pour "${site.libelle}" ?`,
            message: isDeactivating
                ? `En désactivant le plancher d'arrivée, les collaborateurs de ce site seront comptabilisés dès leur heure brute de badgeage, même en cas d'arrivée très anticipée.`
                : `En activant le plancher d'arrivée (${heureAffichee}), toute arrivée matinale avant cette heure sera plafonnée à ${heureAffichee} pour le calcul du temps de travail effectif.`,
            confirmLabel: isDeactivating ? "Désactiver le plancher" : "Activer le plancher",
            variant: isDeactivating ? "warning" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    await organisationService.toggleSitePlancher(site.idSite, {
                        actif: !isDeactivating,
                        heureEntreeMinimaleValorisee: heureAffichee
                    });
                    toast({
                        title: 'Succès',
                        message: isDeactivating
                            ? `Le plancher d'arrivée du site "${site.libelle}" a été désactivé.`
                            : `Le plancher d'arrivée du site "${site.libelle}" est désormais actif (${heureAffichee}).`,
                        type: 'success'
                    });
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setError(getErrorMessage(err, "Erreur lors du changement d'état du plancher."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const requestToggleSiteStatus = (site) => {
        const isDeactivating = site.estActif;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le site "${site.libelle}" ?` : `Réactiver le site "${site.libelle}" ?`,
            message: isDeactivating
                ? `La désactivation du site entraînera la suspension des règles de pointage associées. Vérifiez qu'aucun employé n'y effectue actuellement ses heures.`
                : `Le site et ses horaires seront de nouveau actifs pour les affectations et le pointage.`,
            confirmLabel: isDeactivating ? "Désactiver le site" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateSite(site.idSite);
                        toast({ title: 'Succès', message: `Le site "${site.libelle}" a été désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateSite(site.idSite);
                        toast({ title: 'Succès', message: `Le site "${site.libelle}" a été réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setError(getErrorMessage(err, "Erreur lors du changement de statut."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const safeFiliales = Array.isArray(filiales) ? filiales : [];
    const safeSites = Array.isArray(sites) ? sites : [];

    const filteredFiliales = safeFiliales.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFiliale?.toLowerCase().includes(search.toLowerCase()) ||
         f.raisonSociale?.toLowerCase().includes(search.toLowerCase()))
    );

    const filteredSites = safeSites.filter(s =>
        (showInactive || s.estActif !== false) &&
        (!filterFilialeId || String(s.idFilialeRattachement) === String(filterFilialeId)) &&
        (s.codeSite?.toLowerCase().includes(search.toLowerCase()) ||
         s.libelle?.toLowerCase().includes(search.toLowerCase()) ||
         s.filialeRaisonSociale?.toLowerCase().includes(search.toLowerCase()))
    );

    const subTabs = [
        { id: 'filiales', label: 'Filiales & Structures', count: safeFiliales.length, icon: Building2 },
        { id: 'sites', label: "Sites d'Exploitation", count: safeSites.length, icon: MapPin }
    ];

    if (loading && filiales.length === 0 && sites.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={6} columns={4} />
            </div>
        );
    }

    return (
        <SideTabs
            tabs={subTabs}
            activeTab={activeSubTab}
            onChange={setActiveSubTab}
            title="Rubriques"
            className="space-y-4"
        >
            {error && <Alert type="error" message={error} />}

            {/* En-tête principal concis avec bouton d'action */}
            <div className="flex items-center justify-between gap-3 p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-center gap-2">
                    <h3 className="font-bold text-[var(--color-fg)] text-sm tracking-tight uppercase">
                        {activeSubTab === 'filiales' ? 'Filiales' : "Sites d'Exploitation"}
                    </h3>
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] px-2 py-0.5 border border-[var(--color-border-subtle)] rounded-none">
                        {activeSubTab === 'filiales' ? safeFiliales.length : safeSites.length}
                    </span>
                </div>
                <div>
                    {activeSubTab === 'filiales' ? (
                        <Button
                            onClick={handleOpenCreateFiliale}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={15} />}
                        >
                            Nouvelle Filiale
                        </Button>
                    ) : (
                        <Button
                            onClick={handleOpenCreateSite}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={15} />}
                        >
                            Nouveau Site
                        </Button>
                    )}
                </div>
            </div>

            {/* Barre d'outils unifiée (Recherche, Filtres, Inactifs & Switch Cartes/Tableau) */}
            <div className="flex flex-col lg:flex-row gap-3 justify-between items-stretch lg:items-center p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-center gap-3 flex-1 flex-wrap">
                    <div className="flex-1 min-w-[220px] max-w-sm">
                        <Input
                            placeholder={activeSubTab === 'filiales' ? "Rechercher une filiale..." : "Rechercher un site (nom, code)..."}
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            iconLeft={<Search size={15} />}
                        />
                    </div>

                    {activeSubTab === 'sites' && (
                        <div className="min-w-[200px]">
                            <Select
                                value={filterFilialeId}
                                onChange={(val) => setFilterFilialeId(val)}
                                placeholder="Toutes les filiales"
                                options={safeFiliales
                                    .filter(f => f.estActif !== false && f.actif !== false)
                                    .map(f => ({
                                        value: f.idFiliale,
                                        label: `${f.codeFiliale} - ${f.raisonSociale}`
                                    }))
                                }
                            />
                        </div>
                    )}
                </div>

                <div className="flex items-center gap-3 justify-between lg:justify-end">
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        <Eye size={14} />
                        <span>Afficher inactifs</span>
                    </label>

                    <div className="inline-flex border border-[var(--color-border-subtle)] p-0.5 bg-[var(--color-bg-subtle)] rounded-none">
                        <button
                            type="button"
                            onClick={() => setViewMode('cards')}
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs cursor-pointer transition-colors ${
                                viewMode === 'cards'
                                    ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs font-bold'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-medium'
                            }`}
                            title="Affichage en cartes"
                        >
                            <LayoutGrid size={14} />
                            <span>Cartes</span>
                        </button>
                        <button
                            type="button"
                            onClick={() => setViewMode('table')}
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs cursor-pointer transition-colors ${
                                viewMode === 'table'
                                    ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs font-bold'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-medium'
                            }`}
                            title="Affichage en tableau"
                        >
                            <Table2 size={14} />
                            <span>Tableau</span>
                        </button>
                    </div>
                </div>
            </div>

            {/* Corps de contenu (Cartes ou Tableau) */}
            {activeSubTab === 'filiales' && (
                viewMode === 'cards' ? (
                    <FilialesCardsView
                        filiales={filteredFiliales}
                        sites={safeSites}
                        onOpenEdit={handleOpenEditFiliale}
                        onToggleStatus={requestToggleFilialeStatus}
                        onOpenCreate={handleOpenCreateFiliale}
                    />
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                        <FilialesList
                            filiales={filteredFiliales}
                            onOpenEdit={handleOpenEditFiliale}
                            onToggleStatus={requestToggleFilialeStatus}
                            onOpenCreate={handleOpenCreateFiliale}
                        />
                    </div>
                )
            )}

            {activeSubTab === 'sites' && (
                viewMode === 'cards' ? (
                    <SitesCardsView
                        sites={filteredSites}
                        onOpenEdit={handleOpenEditSite}
                        onToggleStatus={requestToggleSiteStatus}
                        onOpenCreate={handleOpenCreateSite}
                    />
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                        <SitesList
                            sites={filteredSites}
                            onOpenEdit={handleOpenEditSite}
                            onToggleStatus={requestToggleSiteStatus}
                            onTogglePlancher={requestTogglePlancher}
                            onOpenCreate={handleOpenCreateSite}
                        />
                    </div>
                )
            )}

            <FilialeFormModal
                isOpen={showFilialeModal}
                onClose={() => setShowFilialeModal(false)}
                onSubmit={handleSaveFiliale}
                selectedFiliale={selectedFiliale}
                formData={filialeFormData}
                setFormData={setFilialeFormData}
                actionError={actionError}
                fieldErrors={filialeFieldErrors}
                setFieldErrors={setFilialeFieldErrors}
                submitting={submitting}
            />

            <SiteFormModal
                isOpen={showSiteModal}
                onClose={() => setShowSiteModal(false)}
                onSubmit={handleSaveSite}
                selectedSite={selectedSite}
                formData={siteFormData}
                setFormData={setSiteFormData}
                filiales={filiales}
                actionError={actionError}
                fieldErrors={siteFieldErrors}
                setFieldErrors={setSiteFieldErrors}
                submitting={submitting}
            />

            <ConfirmDialog
                isOpen={confirmState.isOpen}
                onClose={() => setConfirmState(prev => ({ ...prev, isOpen: false }))}
                onConfirm={confirmState.onConfirm}
                title={confirmState.title}
                message={confirmState.message}
                confirmLabel={confirmState.confirmLabel}
                variant={confirmState.variant}
                loading={confirmState.loading}
            />
        </SideTabs>
    );
}
