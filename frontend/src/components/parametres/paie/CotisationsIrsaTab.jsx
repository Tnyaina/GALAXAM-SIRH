import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle, XCircle, ShieldCheck, Percent, MapPin, Eye } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';

import CotisationsList from './cotisations/CotisationsList';
import CotisationFormModal from './cotisations/CotisationFormModal';
import SiteCotisationsList from './cotisations/SiteCotisationsList';
import SiteCotisationFormModal from './cotisations/SiteCotisationFormModal';
import BaremeIrsaList from './irsa/BaremeIrsaList';
import IrsaTrancheModal from './irsa/IrsaTrancheModal';

export default function CotisationsIrsaTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const subSection = searchParams.get('subtab') || 'cotisations'; // 'cotisations' | 'site_cotisations' | 'irsa'

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'cotisations_irsa');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const [cotisations, setCotisations] = useState([]);
    const [siteCotisations, setSiteCotisations] = useState([]);
    const [sites, setSites] = useState([]);
    const [tranchesIrsa, setTranchesIrsa] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
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
    const handleOpenCreateCotisation = () => {
        setEditingCotisation(null);
        setCotisationFormData({ code: '', libelle: '', tauxSalarial: 1.0, tauxPatronal: 13.0, dateDebut: '2026-01-01', dateFin: '' });
        setActionError(null);
        setShowCotisationModal(true);
    };

    const handleOpenEditCotisation = (c) => {
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
    };

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
    const handleOpenCreateSiteCotisation = () => {
        setSiteCotisationFormData({ idSite: '', idTypeCotisation: '' });
        setActionError(null);
        setShowSiteCotisationModal(true);
    };

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

    const handleToggleSiteCotisationStatus = async (item) => {
        setActionSuccess(null);
        setActionError(null);
        try {
            if (item.estActif) {
                await paieConfigService.deactivateSiteCotisation(item.idSiteCotisation);
                setActionSuccess(`Association "${item.siteLibelle} - ${item.cotisationLibelle}" désactivée.`);
            } else {
                await paieConfigService.activateSiteCotisation(item.idSiteCotisation);
                setActionSuccess(`Association "${item.siteLibelle} - ${item.cotisationLibelle}" réactivée.`);
            }
            setSiteCotisations(prev =>
                prev.map(sc =>
                    sc.idSiteCotisation === item.idSiteCotisation
                        ? { ...sc, estActif: !item.estActif }
                        : sc
                )
            );
        } catch (err) {
            setActionError("Erreur lors de la modification du statut.");
            console.error(err);
        }
    };

    // Handlers IRSA
    const handleOpenCreateIrsa = () => {
        setEditingIrsa(null);
        setIrsaFormData({ numeroTranche: tranchesIrsa.length + 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: '2026-01-01', dateFin: '' });
        setActionError(null);
        setShowIrsaModal(true);
    };

    const handleOpenEditIrsa = (t) => {
        setEditingIrsa(t);
        setIrsaFormData({
            numeroTranche: t.numeroTranche,
            trancheMin: t.trancheMin,
            trancheMax: t.trancheMax || '',
            taux: t.taux,
            dateDebut: t.dateDebut || '2026-01-01',
            dateFin: t.dateFin || ''
        });
        setActionError(null);
        setShowIrsaModal(true);
    };

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
                        Barème Impôt IRSA ({tranchesIrsa.length} tranches)
                    </button>
                </div>

                {subSection === 'site_cotisations' && (
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
                )}
            </div>

            {/* SubSection 1: Cotisations */}
            {subSection === 'cotisations' && (
                <CotisationsList
                    cotisations={cotisations}
                    onOpenCreate={handleOpenCreateCotisation}
                    onOpenEdit={handleOpenEditCotisation}
                />
            )}

            {/* SubSection 2: Site Cotisations */}
            {subSection === 'site_cotisations' && (
                <SiteCotisationsList
                    siteCotisations={filteredSiteCotisations}
                    onOpenCreate={handleOpenCreateSiteCotisation}
                    onToggleStatus={handleToggleSiteCotisationStatus}
                />
            )}

            {/* SubSection 3: IRSA */}
            {subSection === 'irsa' && (
                <BaremeIrsaList
                    tranchesIrsa={tranchesIrsa}
                    onOpenCreate={handleOpenCreateIrsa}
                    onOpenEdit={handleOpenEditIrsa}
                />
            )}

            {/* Modale Cotisation */}
            <CotisationFormModal
                isOpen={showCotisationModal}
                onClose={() => setShowCotisationModal(false)}
                onSubmit={handleSaveCotisation}
                editingCotisation={editingCotisation}
                formData={cotisationFormData}
                setFormData={setCotisationFormData}
                actionError={actionError}
            />

            {/* Modale Site Cotisation */}
            <SiteCotisationFormModal
                isOpen={showSiteCotisationModal}
                onClose={() => setShowSiteCotisationModal(false)}
                onSubmit={handleSaveSiteCotisation}
                formData={siteCotisationFormData}
                setFormData={setSiteCotisationFormData}
                sites={sites}
                cotisations={cotisations}
                actionError={actionError}
            />

            {/* Modale IRSA */}
            <IrsaTrancheModal
                isOpen={showIrsaModal}
                onClose={() => setShowIrsaModal(false)}
                onSubmit={handleSaveIrsa}
                editingIrsa={editingIrsa}
                formData={irsaFormData}
                setFormData={setIrsaFormData}
                actionError={actionError}
            />
        </div>
    );
}