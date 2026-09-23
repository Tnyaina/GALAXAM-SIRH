import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Plus, Building2, MapPin, Eye } from 'lucide-react';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';

import FilialesList from './filiales/FilialesList';
import SitesList from './sites/SitesList';
import SiteFormModal from './sites/SiteFormModal';

function FilialeFormModal({ isOpen, onClose, onSubmit, selectedFiliale, formData, setFormData, actionError, submitting = false }) {
    return <Modal isOpen={isOpen} onClose={onClose} title={selectedFiliale ? 'Modifier la Filiale' : 'Nouvelle Filiale'} icon={<Building2 size={18} />} size="md" footer={<><Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>Annuler</Button><Button type="submit" form="filiale-form" variant="primary" size="sm" loading={submitting}>{selectedFiliale ? 'Enregistrer les modifications' : 'Créer la filiale'}</Button></>}><form id="filiale-form" onSubmit={onSubmit} className="space-y-4">{actionError && <Alert type="error" message={actionError} />}<div className="space-y-3"><h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Identification Juridique</h4><div className="grid grid-cols-1 sm:grid-cols-3 gap-3"><div className="sm:col-span-1"><Input label="Code" required disabled={!!selectedFiliale} value={formData.codeFiliale} onChange={(event) => setFormData({ ...formData, codeFiliale: event.target.value.toUpperCase() })} placeholder="Ex: LOG" /></div><div className="sm:col-span-2"><Input label="Raison Sociale" required value={formData.raisonSociale} onChange={(event) => setFormData({ ...formData, raisonSociale: event.target.value })} placeholder="Ex: Galaxam Logistics " /></div></div></div><div className="space-y-3 pt-2 border-t border-[var(--color-border-subtle)]"><h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Immatriculations Fiscales & Légales</h4><div className="grid grid-cols-2 gap-3"><Input label="Numéro NIF" value={formData.nif} onChange={(event) => setFormData({ ...formData, nif: event.target.value })} placeholder="3000123456" /><Input label="Numéro STAT" value={formData.stat} onChange={(event) => setFormData({ ...formData, stat: event.target.value })} placeholder="49231 11 2020 0 10234" /></div></div><div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]"><h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Paramètres Sociaux & Alertes RH</h4><Input label="Seuil d'Alerte Effectif (Délégués du personnel / CSE)" type="number" min="0" value={formData.seuilEffectifAlerte ?? ''} onChange={(event) => { const value = event.target.value; setFormData({ ...formData, seuilEffectifAlerte: value === '' ? '' : Math.max(0, parseInt(value, 10) || 0) }); }} hint="Palier réglementaire (ex: 19 pour les délégués du personnel). Renseignez 0 pour désactiver l'alerte (sans seuil)." /></div></form></Modal>;
}

export default function FilialesSitesTab() {
    // Contexte et navigation
    const [searchParams, setSearchParams] = useSearchParams();
    const activeSubTab = searchParams.get('subtab') || 'filiales';
    const toast = useToast();

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
        codeFiliale: '', raisonSociale: '', nif: '', stat: '', seuilEffectifAlerte: 19
    });

    // Modales Site
    const [showSiteModal, setShowSiteModal] = useState(false);
    const [selectedSite, setSelectedSite] = useState(null);
    const [siteFormData, setSiteFormData] = useState({
        codeSite: '', libelle: '', idFilialeRattachement: '',
        horaireContinu: true,
        heureDebutMatin: '08:00', heureFinMatin: '12:30',
        heureDebutApresMidi: '13:00', heureFinApresMidi: '16:30'
    });

    // Chargement des données
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
            setError("Erreur lors du chargement des structures. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge les filiales et sites à l'ouverture de l'onglet.
    useEffect(() => {
        loadData();
    }, []);

    // Actions utilisateur : filiales, sites et confirmations.
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
        if (submitting) return;
        setActionError(null);

        if (!filialeFormData.codeFiliale.trim()) {
            setActionError("Le code filiale est obligatoire.");
            return;
        }
        if (!filialeFormData.raisonSociale.trim()) {
            setActionError("La raison sociale est obligatoire.");
            return;
        }

        setSubmitting(true);
        try {
            const payload = {
                ...filialeFormData,
                seuilEffectifAlerte: filialeFormData.seuilEffectifAlerte === '' || filialeFormData.seuilEffectifAlerte == null
                    ? 0
                    : Number(filialeFormData.seuilEffectifAlerte)
            };
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
            setActionError(err.response?.data || "Erreur de sauvegarde.");
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
                    setError(err.response?.data || "Erreur lors du changement de statut.");
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
            heureDebutApresMidi: '13:00', heureFinApresMidi: '16:30'
        });
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
            heureFinApresMidi: site.heureFinApresMidi ? site.heureFinApresMidi.substring(0, 5) : (isContinu ? '16:30' : '18:00')
        });
        setActionError(null);
        setShowSiteModal(true);
    };

    const handleSaveSite = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        if (!siteFormData.codeSite.trim()) {
            setActionError("Le code du site est obligatoire.");
            return;
        }
        if (!siteFormData.libelle.trim()) {
            setActionError("Le libellé du site est obligatoire.");
            return;
        }

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
            heureFinApresMidi: formatTime(siteFormData.heureFinApresMidi, '17:00')
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
            setActionError(err.response?.data || "Erreur de sauvegarde du site.");
        } finally {
            setSubmitting(false);
        }
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
                    setError(err.response?.data || "Erreur lors du changement de statut.");
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
        { id: 'filiales', label: `Filiales (${safeFiliales.length})`, icon: Building2 },
        { id: 'sites', label: `Sites d'Exploitation (${safeSites.length})`, icon: MapPin }
    ];

    if (loading && filiales.length === 0 && sites.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={6} columns={4} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-[var(--color-border-subtle)] pb-4">
                <Tabs
                    tabs={subTabs}
                    activeTab={activeSubTab}
                    onChange={setActiveSubTab}
                    variant="underline"
                />

                <div>
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

            {error && <Alert type="error" message={error} />}

            <div className="flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-3.5 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-center gap-3 flex-1 flex-wrap">
                    <div className="flex-1 min-w-[200px] max-w-sm">
                        <Input
                            placeholder={activeSubTab === 'filiales' ? "Rechercher par code, raison sociale..." : "Rechercher un site par code, nom..."}
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            icon={<Search size={15} />}
                        />
                    </div>

                    {activeSubTab === 'sites' && (
                        <div className="min-w-[220px]">
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

                <div className="flex items-center gap-3">
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
                </div>
            </div>

            {activeSubTab === 'filiales' && (
                <FilialesList
                    filiales={filteredFiliales}
                    onOpenEdit={handleOpenEditFiliale}
                    onToggleStatus={requestToggleFilialeStatus}
                    onOpenCreate={handleOpenCreateFiliale}
                />
            )}

            {activeSubTab === 'sites' && (
                <SitesList
                    sites={filteredSites}
                    onOpenEdit={handleOpenEditSite}
                    onToggleStatus={requestToggleSiteStatus}
                    onOpenCreate={handleOpenCreateSite}
                />
            )}

            <FilialeFormModal
                isOpen={showFilialeModal}
                onClose={() => setShowFilialeModal(false)}
                onSubmit={handleSaveFiliale}
                selectedFiliale={selectedFiliale}
                formData={filialeFormData}
                setFormData={setFilialeFormData}
                actionError={actionError}
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
        </div>
    );
}
