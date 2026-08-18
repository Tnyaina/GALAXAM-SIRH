import { useState, useEffect, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle, XCircle, Calendar, MapPin, Search, Filter } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import Tabs from '@/components/ui/navigation/Tabs';

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
    const [actionSuccess, setActionSuccess] = useState(null);

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
            if (sf.dateFerie) {
                const y = sf.dateFerie.substring(0, 4);
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
        setActionError(null);
        setActionSuccess(null);
        try {
            const payload = {
                ...periodeFormData,
                idSite: periodeFormData.idSite ? parseInt(periodeFormData.idSite) : null,
                dateFin: periodeFormData.dateFin || null
            };

            if (editingPeriode) {
                await paieConfigService.updatePeriodeChomee(editingPeriode.idPeriode, payload);
                setActionSuccess(`Période "${periodeFormData.libelle}" modifiée.`);
            } else {
                await paieConfigService.createPeriodeChomee(payload);
                setActionSuccess(`Période "${periodeFormData.libelle}" ajoutée.`);
            }
            setShowPeriodeModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur lors de la sauvegarde de la période.");
        }
    };

    const handleDeletePeriode = async (id) => {
        setActionSuccess(null);
        setActionError(null);
        try {
            await paieConfigService.deletePeriodeChomee(id);
            setActionSuccess("Période supprimée.");
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
        const matchesYear = selectedYear === 'tous' || (sf.dateFerie && sf.dateFerie.startsWith(selectedYear));
        const matchesSite = !selectedSiteFilter || String(sf.idSite) === String(selectedSiteFilter);
        const matchesSearch = !search || (sf.libelleFerie && sf.libelleFerie.toLowerCase().includes(search.toLowerCase())) ||
            (sf.libelleSite && sf.libelleSite.toLowerCase().includes(search.toLowerCase()));
        return matchesYear && matchesSite && matchesSearch;
    });

    const filteredPeriodes = periodes.filter(p => {
        const matchesSite = !selectedSiteFilter || String(p.idSite) === String(selectedSiteFilter);
        const matchesSearch = !search || p.libelle.toLowerCase().includes(search.toLowerCase());
        return matchesSite && matchesSearch;
    });

    if (loading) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-[var(--color-brand-500)] border-t-transparent rounded-none animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
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
                            {sites.map(s => (
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
            />
        </div>
    );
}