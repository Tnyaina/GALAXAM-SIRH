import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {   Calendar, MapPin, Search, Filter } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import paieConfigService from '@/features/administration/api/paieConfigService';
import organisationService from '@/features/organization/api/organisationService';
import { fmtDateInput } from '@/utils/dateUtils';
import SideTabs from '@/components/ui/navigation/SideTabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import FormModal from '@/components/ui/overlays/FormModal';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import { useToast } from '@/components/ui/feedback/Toast';
import useConfirm from '@/hooks/useConfirm';

import JoursFeriesList from './feries/JoursFeriesList';
import SiteFeriesList from './feries/SiteFeriesList';
import SiteFerieFormModal from './feries/SiteFerieFormModal';
import PeriodeChomeeList from './feries/PeriodeChomeeList';
import PeriodeChomeeFormModal from './feries/PeriodeChomeeFormModal';

function JoursFerieFormModal({ isOpen, onClose, onSubmit, editingFerie, formData, setFormData, actionError, fieldErrors = {}, setFieldErrors, submitting = false }) {
    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title={editingFerie ? 'Modifier le Jour Férié' : 'Nouveau Jour Férié'}
            icon={<Calendar size={18} />}
            size="md"
            isEdit={!!editingFerie}
            loading={submitting}
            error={actionError}
        >
            <div className="space-y-4">
                <Input
                    label="Date exacte du jour férié"
                    type="date"
                    required
                    value={fmtDateInput(formData.dateFerie)}
                    onChange={(event) => {
                        setFormData({ ...formData, dateFerie: event.target.value });
                        if (fieldErrors.dateFerie && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, dateFerie: undefined }));
                        }
                    }}
                    error={fieldErrors.dateFerie}
                />
                <Input
                    label="Libellé / Fête célébrée"
                    required
                    value={formData.libelle}
                    onChange={(event) => {
                        setFormData({ ...formData, libelle: event.target.value });
                        if (fieldErrors.libelle && setFieldErrors) {
                            setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }
                    }}
                    error={fieldErrors.libelle}
                    placeholder="Ex: Fête Nationale de l'Indépendance"
                />
                <div className="space-y-2 p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none">
                    <Checkbox label="Jour férié chômé et payé" checked={formData.estChome} onChange={(event) => setFormData({ ...formData, estChome: event.target.checked })} />
                    <Checkbox label="Férié national (applicable d'office à l'ensemble des sites)" checked={formData.estNational} onChange={(event) => setFormData({ ...formData, estNational: event.target.checked })} />
                </div>
            </div>
        </FormModal>
    );
}

export default function CalendrierFerieTab() {
    // Contexte, navigation et confirmation
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedSubSection = searchParams.get('subtab');
    const subSection = ['feries', 'site_feries', 'periodes'].includes(requestedSubSection) ? requestedSubSection : 'feries';

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'calendrier');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const todayStr = fmtDateInput(new Date());

    // État local : données, filtres et modales.
    const [feries, setFeries] = useState([]);
    const [siteFeries, setSiteFeries] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const { toast } = useToast();
    const { confirm, ConfirmModal } = useConfirm();

    const [ferieFieldErrors, setFerieFieldErrors] = useState({});
    const [siteFerieFieldErrors, setSiteFerieFieldErrors] = useState({});
    const [periodeFieldErrors, setPeriodeFieldErrors] = useState({});

    // Filtres
    const [search, setSearch] = useState('');
    const currentYear = String(new Date().getFullYear());
    const [selectedYear, setSelectedYear] = useState(currentYear);
    const [selectedSiteFilter, setSelectedSiteFilter] = useState('');

    // Années disponibles calculées dynamiquement
    const availableYears = useMemo(() => {
        const yearsSet = new Set();
        const cur = new Date().getFullYear();
        yearsSet.add(String(cur - 1));
        yearsSet.add(String(cur));
        yearsSet.add(String(cur + 1));

        feries.forEach(f => {
            if (f.dateFerie) {
                const y = f.dateFerie.substring(0, 4);
                if (y && !isNaN(y)) yearsSet.add(y);
            }
        });

        siteFeries.forEach(sf => {
            if (sf.ferieDate) {
                const y = sf.ferieDate.substring(0, 4);
                if (y && !isNaN(y)) yearsSet.add(y);
            }
        });

        return Array.from(yearsSet).sort((a, b) => Number(b) - Number(a));
    }, [feries, siteFeries]);

    // Modale Férié
    const [showFerieModal, setShowFerieModal] = useState(false);
    const [editingFerie, setEditingFerie] = useState(null);
    const [ferieFormData, setFerieFormData] = useState({
        dateFerie: todayStr, libelle: '', estChome: true, estNational: true
    });
    const [submitting, setSubmitting] = useState(false);

    // Modale Période
    const [showPeriodeModal, setShowPeriodeModal] = useState(false);
    const [editingPeriode, setEditingPeriode] = useState(null);
    const [periodeFormData, setPeriodeFormData] = useState({
        libelle: '', idSite: '', dateDebut: todayStr, dateFin: '', estPaye: true, tauxPaiement: 100
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
            const [feriesData, sfData, sitesData, periodesData] = await Promise.all([
                paieConfigService.getCalendrierFerie(),
                paieConfigService.getSiteFeries(),
                organisationService.getSites(),
                paieConfigService.getPeriodesChomees()
            ]);
            setFeries(feriesData);
            setSiteFeries(sfData);
            setSites(sitesData);
            setPeriodes(periodesData);
        } catch (err) {
            setError("Erreur lors du chargement du calendrier des jours fériés.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge le calendrier et ses référentiels à l'ouverture.
    useEffect(() => {
        loadData();
    }, []);

    // Handlers Férié
    const handleOpenCreateFerie = () => {
        setEditingFerie(null);
        setFerieFormData({ dateFerie: todayStr, libelle: '', estChome: true, estNational: true });
        setFerieFieldErrors({});
        setActionError(null);
        setShowFerieModal(true);
    };

    const handleOpenEditFerie = (f) => {
        setEditingFerie(f);
        setFerieFormData({
            dateFerie: fmtDateInput(f.dateFerie) || todayStr,
            libelle: f.libelle,
            estChome: f.estChome !== false,
            estNational: f.estNational !== false
        });
        setFerieFieldErrors({});
        setActionError(null);
        setShowFerieModal(true);
    };

    const handleToggleFerieStatus = (f) => {
        const isActif = f.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} le jour férié`,
            message: isActif
                ? `Êtes-vous sûr de vouloir désactiver le jour férié "${f.libelle}" (${f.dateFerie}) ?\n\nIl ne sera plus comptabilisé comme jour chômé dans les décomptes légaux de congés et de pointage.`
                : `Êtes-vous sûr de vouloir réactiver le jour férié "${f.libelle}" (${f.dateFerie}) ?`,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    if (isActif) {
                        await paieConfigService.deactivateCalendrierFerie(f.idFerie);
                        toast({ title: 'Succès', message: `Jour férié "${f.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await paieConfigService.activateCalendrierFerie(f.idFerie);
                        toast({ title: 'Succès', message: `Jour férié "${f.libelle}" réactivé.`, type: 'success' });
                    }
                    loadData();
                } catch (err) {
                    toast({
                        title: 'Erreur',
                        message: getErrorMessage(err, "Erreur lors du changement de statut."),
                        type: 'error'
                    });
                }
            }
        });
    };

    const handleSaveFerie = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!ferieFormData.dateFerie) {
            errors.dateFerie = "La date du jour férié est obligatoire.";
        }
        if (!ferieFormData.libelle.trim()) {
            errors.libelle = "Le libellé de la fête est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setFerieFieldErrors(errors);
            return;
        }
        setFerieFieldErrors({});

        setSubmitting(true);
        try {
            if (editingFerie) {
                await paieConfigService.updateCalendrierFerie(editingFerie.idFerie, ferieFormData);
                toast({ title: 'Succès', message: `Jour férié "${ferieFormData.libelle}" modifié.`, type: 'success' });
            } else {
                await paieConfigService.createCalendrierFerie(ferieFormData);
                toast({ title: 'Succès', message: `Jour férié "${ferieFormData.libelle}" ajouté.`, type: 'success' });
            }
            setShowFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Site Férié
    const handleOpenCreateSiteFerie = () => {
        setSiteFerieFormData({ idSite: '', idFerie: '' });
        setSiteFerieFieldErrors({});
        setActionError(null);
        setShowSiteFerieModal(true);
    };

    const handleSaveSiteFerie = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!siteFerieFormData.idSite) {
            errors.idSite = "Veuillez sélectionner un site d'exploitation.";
        }
        if (!siteFerieFormData.idFerie) {
            errors.idFerie = "Veuillez sélectionner un jour férié régional.";
        }

        if (Object.keys(errors).length > 0) {
            setSiteFerieFieldErrors(errors);
            return;
        }
        setSiteFerieFieldErrors({});

        const payload = {
            idSite: parseInt(siteFerieFormData.idSite),
            idFerie: parseInt(siteFerieFormData.idFerie)
        };
        setSubmitting(true);
        try {
            await paieConfigService.createSiteFerie(payload);
            toast({ title: 'Succès', message: "Jour férié associé au site.", type: 'success' });
            setShowSiteFerieModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur d'affectation."));
        } finally {
            setSubmitting(false);
        }
    };

    const handleDeleteSiteFerie = async (id) => {
        setActionError(null);
        try {
            await paieConfigService.deleteSiteFerie(id);
            toast({ title: 'Succès', message: "Association supprimée.", type: 'success' });
            setSiteFeries(prev => prev.filter(item => item.idSiteFerie !== id));
        } catch (err) {
            setActionError("Erreur lors de la suppression.");
            console.error(err);
        }
    };

    // Handlers Périodes Chômées
    const handleOpenCreatePeriode = () => {
        setEditingPeriode(null);
        setPeriodeFormData({ libelle: '', idSite: '', dateDebut: todayStr, dateFin: '', estPaye: true, tauxPaiement: 100 });
        setPeriodeFieldErrors({});
        setActionError(null);
        setShowPeriodeModal(true);
    };

    const handleOpenEditPeriode = (p) => {
        setEditingPeriode(p);
        setPeriodeFormData({
            libelle: p.libelle,
            idSite: p.idSite || '',
            dateDebut: fmtDateInput(p.dateDebut),
            dateFin: p.dateFin ? fmtDateInput(p.dateFin) : '',
            estPaye: p.estPaye,
            tauxPaiement: p.tauxPaiement
        });
        setPeriodeFieldErrors({});
        setActionError(null);
        setShowPeriodeModal(true);
    };

    const handleSavePeriode = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!periodeFormData.libelle.trim()) {
            errors.libelle = "L'intitulé de l'événement est obligatoire.";
        }
        if (!periodeFormData.dateDebut) {
            errors.dateDebut = "La date de début de suspension est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setPeriodeFieldErrors(errors);
            return;
        }
        setPeriodeFieldErrors({});

        setSubmitting(true);
        try {
            const payload = {
                ...periodeFormData,
                idSite: periodeFormData.idSite ? parseInt(periodeFormData.idSite) : null,
                dateFin: periodeFormData.dateFin || null
            };

            if (editingPeriode) {
                await paieConfigService.updatePeriodeChomee(editingPeriode.idPeriode, payload);
                toast({ title: 'Succès', message: `Période "${periodeFormData.libelle}" modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createPeriodeChomee(payload);
                toast({ title: 'Succès', message: `Période "${periodeFormData.libelle}" ajoutée.`, type: 'success' });
            }
            setShowPeriodeModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur lors de la sauvegarde de la période."));
        } finally {
            setSubmitting(false);
        }
    };

    const handleDeletePeriode = async (id) => {
        setActionError(null);
        try {
            await paieConfigService.deletePeriodeChomee(id);
            toast({ title: 'Succès', message: "Période supprimée.", type: 'success' });
            setPeriodes(prev => prev.filter(p => p.idPeriode !== id));
        } catch (err) {
            setActionError("Erreur lors de la suppression.");
            console.error(err);
        }
    };

    const filteredFeries = feries.filter(f => {
        const matchesYear = selectedYear === 'tous' || (f.dateFerie && f.dateFerie.startsWith(selectedYear));
        const matchesSearch = !search || f.libelle.toLowerCase().includes(search.toLowerCase());
        return matchesYear && matchesSearch;
    });

    const filteredSiteFeries = siteFeries.filter(sf => {
        const matchesYear = selectedYear === 'tous' || (sf.ferieDate && sf.ferieDate.startsWith(selectedYear));
        const matchesSite = !selectedSiteFilter || String(sf.idSite) === String(selectedSiteFilter);
        const matchesSearch = !search || (sf.ferieLibelle && sf.ferieLibelle.toLowerCase().includes(search.toLowerCase())) ||
            (sf.siteLibelle && sf.siteLibelle.toLowerCase().includes(search.toLowerCase()));
        return matchesYear && matchesSite && matchesSearch;
    });

    const filteredPeriodes = periodes.filter(p => {
        const matchesSite = !selectedSiteFilter || String(p.idSite) === String(selectedSiteFilter);
        const matchesSearch = !search || p.libelle.toLowerCase().includes(search.toLowerCase());
        return matchesSite && matchesSearch;
    });

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={5} />
            </div>
        );
    }

    // Rendu
    return (
        <SideTabs
            tabs={[
                { id: 'feries', label: 'Jours fériés nationaux', icon: <Calendar size={16} />, count: filteredFeries.length },
                { id: 'site_feries', label: 'Fériés spécifiques par site', icon: <MapPin size={16} />, count: filteredSiteFeries.length },
                { id: 'periodes', label: 'Périodes exceptionnelles', icon: <Calendar size={16} />, count: filteredPeriodes.length }
            ]}
            activeTab={subSection}
            onChange={setSubSection}
            className="space-y-6"
        >
            {error && <Alert type="error" message={error} />}

            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
                <div className="flex items-center gap-2.5 flex-wrap w-full md:w-auto">
                    <div className="relative flex-1 sm:w-64 sm:flex-initial">
                        <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
                        <input
                            type="text"
                            placeholder="Rechercher un férié..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="w-full pl-8 pr-3 h-9 text-xs bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)] transition-colors"
                        />
                    </div>

                    <div className="flex items-center gap-1.5 h-9 bg-[var(--color-surface)] px-2.5 border border-[var(--color-border)] rounded-none">
                        <Filter size={13} className="text-[var(--color-fg-muted)] shrink-0" />
                        <select
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                            className="bg-transparent text-xs font-semibold text-[var(--color-fg)] focus:outline-none cursor-pointer h-full"
                        >
                            <option value="tous">Toutes les années</option>
                            {availableYears.map((year) => (
                                <option key={year} value={year}>
                                    Année {year}
                                </option>
                            ))}
                        </select>
                    </div>

                    {subSection === 'site_feries' && (
                        <select
                            value={selectedSiteFilter}
                            onChange={(e) => setSelectedSiteFilter(e.target.value)}
                            className="h-9 bg-[var(--color-surface)] text-xs font-semibold text-[var(--color-fg)] px-3 border border-[var(--color-border)] rounded-none focus:outline-none cursor-pointer transition-colors"
                        >
                            <option value="">Tous les sites</option>
                            {sites
                                .filter(s => s.estActif !== false && s.actif !== false)
                                .map(s => (
                                    <option key={s.idSite} value={s.idSite}>{s.libelle}</option>
                                ))}
                        </select>
                    )}
                </div>
            </div>

            {subSection === 'feries' && (
                <JoursFeriesList
                    feries={filteredFeries}
                    selectedYear={selectedYear}
                    search={search}
                    onOpenCreate={handleOpenCreateFerie}
                    onOpenEdit={handleOpenEditFerie}
                    onToggleStatus={handleToggleFerieStatus}
                />
            )}

            {subSection === 'site_feries' && (
                <SiteFeriesList
                    siteFeries={filteredSiteFeries}
                    selectedYear={selectedYear}
                    search={search}
                    onOpenCreate={handleOpenCreateSiteFerie}
                    onDelete={handleDeleteSiteFerie}
                />
            )}

            {subSection === 'periodes' && (
                <PeriodeChomeeList
                    periodes={filteredPeriodes}
                    sites={sites}
                    onOpenCreate={handleOpenCreatePeriode}
                    onOpenEdit={handleOpenEditPeriode}
                    onDelete={handleDeletePeriode}
                />
            )}

            <JoursFerieFormModal
                isOpen={showFerieModal}
                onClose={() => setShowFerieModal(false)}
                onSubmit={handleSaveFerie}
                editingFerie={editingFerie}
                formData={ferieFormData}
                setFormData={setFerieFormData}
                actionError={actionError}
                fieldErrors={ferieFieldErrors}
                setFieldErrors={setFerieFieldErrors}
                submitting={submitting}
            />

            <SiteFerieFormModal
                isOpen={showSiteFerieModal}
                onClose={() => setShowSiteFerieModal(false)}
                onSubmit={handleSaveSiteFerie}
                formData={siteFerieFormData}
                setFormData={setSiteFerieFormData}
                sites={sites}
                feries={feries}
                actionError={actionError}
                fieldErrors={siteFerieFieldErrors}
                setFieldErrors={setSiteFerieFieldErrors}
                submitting={submitting}
            />

            <PeriodeChomeeFormModal
                isOpen={showPeriodeModal}
                onClose={() => setShowPeriodeModal(false)}
                onSubmit={handleSavePeriode}
                editingPeriode={editingPeriode}
                formData={periodeFormData}
                setFormData={setPeriodeFormData}
                sites={sites}
                actionError={actionError}
                fieldErrors={periodeFieldErrors}
                setFieldErrors={setPeriodeFieldErrors}
                submitting={submitting}
            />

            {/* Modale de confirmation globale */}
            <ConfirmModal />
        </SideTabs>
    );
}
