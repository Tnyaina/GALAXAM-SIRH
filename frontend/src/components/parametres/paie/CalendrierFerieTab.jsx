import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import {   Calendar, MapPin, Search, Filter } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import useConfirm from '@/hooks/useConfirm';

import JoursFeriesList from './feries/JoursFeriesList';
import JoursFerieFormModal from './feries/JoursFerieFormModal';
import SiteFeriesList from './feries/SiteFeriesList';
import SiteFerieFormModal from './feries/SiteFerieFormModal';
import PeriodeChomeeList from './feries/PeriodeChomeeList';
import PeriodeChomeeFormModal from './feries/PeriodeChomeeFormModal';

export default function CalendrierFerieTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const subSection = searchParams.get('subtab') || 'feries';

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'calendrier');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const todayStr = fmtDateInput(new Date());

    const [feries, setFeries] = useState([]);
    const [siteFeries, setSiteFeries] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [sites, setSites] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const toast = useToast();
    const { confirm, ConfirmModal } = useConfirm();

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

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Férié
    const handleOpenCreateFerie = () => {
        setEditingFerie(null);
        setFerieFormData({ dateFerie: todayStr, libelle: '', estChome: true, estNational: true });
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
                        message: err.response?.data?.message || err.response?.data || "Erreur lors du changement de statut.",
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
        if (!ferieFormData.dateFerie || !ferieFormData.libelle.trim()) {
            setActionError("La date et le libellé sont obligatoires.");
            return;
        }
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
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        } finally {
            setSubmitting(false);
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
        if (submitting) return;
        setActionError(null);
        if (!siteFerieFormData.idSite || !siteFerieFormData.idFerie) {
            setActionError("Le site et le jour férié sont obligatoires.");
            return;
        }
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
            setActionError(err.response?.data || "Erreur d'affectation.");
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
        setActionError(null);
        setShowPeriodeModal(true);
    };

    const handleSavePeriode = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
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
            setActionError(err.response?.data || "Erreur lors de la sauvegarde de la période.");
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

    return (
        <div className="space-y-6">
            {error && <Alert type="error" message={error} />}

            {/* Onglets internes + Filtres */}
            <div className="flex flex-col md:flex-row justify-between items-start md:items-center border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none p-2 gap-3 shadow-sm">
                <Tabs
                    tabs={[
                        { id: 'feries', label: 'Jours Fériés Nationaux', icon: <Calendar size={16} />, count: filteredFeries.length },
                        { id: 'site_feries', label: 'Fériés Spécifiques par Site', icon: <MapPin size={16} />, count: filteredSiteFeries.length },
                        { id: 'periodes', label: 'Périodes Exceptionnelles', icon: <Calendar size={16} />, count: filteredPeriodes.length }
                    ]}
                    activeTab={subSection}
                    onChange={setSubSection}
                    variant="pills"
                />

                <div className="flex items-center gap-2 flex-wrap w-full md:w-auto">
                    <div className="relative flex-1 md:flex-initial">
                        <Search size={14} className="absolute left-2.5 top-2.5 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Rechercher un férié..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            className="pl-8 pr-3 py-1.5 text-xs bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                        />
                    </div>

                    <div className="flex items-center gap-1 bg-[var(--color-surface)] px-2 py-1 border border-[var(--color-border-subtle)] rounded-none">
                        <Filter size={12} className="text-[var(--color-fg-muted)]" />
                        <select
                            value={selectedYear}
                            onChange={(e) => setSelectedYear(e.target.value)}
                            className="bg-transparent text-xs font-semibold text-[var(--color-fg)] focus:outline-none cursor-pointer"
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
                            className="bg-[var(--color-surface)] text-xs font-semibold text-[var(--color-fg)] px-2 py-1.5 border border-[var(--color-border)] rounded-none focus:outline-none cursor-pointer"
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
                submitting={submitting}
            />

            {/* Modale de confirmation globale */}
            <ConfirmModal />
        </div>
    );
}