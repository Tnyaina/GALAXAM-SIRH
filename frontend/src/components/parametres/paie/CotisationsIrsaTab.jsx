import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { CheckCircle, XCircle, ShieldCheck, Percent, MapPin, Eye } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import Tabs from '@/components/ui/navigation/Tabs';

import CotisationsList from './cotisations/CotisationsList';
import CotisationFormModal from './cotisations/CotisationFormModal';
import SiteCotisationsList from './cotisations/SiteCotisationsList';
import SiteCotisationFormModal from './cotisations/SiteCotisationFormModal';
import BaremeIrsaList from './irsa/BaremeIrsaList';
import IrsaTrancheModal from './irsa/IrsaTrancheModal';

export default function CotisationsIrsaTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const subSection = searchParams.get('subtab') || 'cotisations';

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'cotisations_irsa');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const todayStr = fmtDateInput(new Date());

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
        code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, dateDebut: todayStr, dateFin: ''
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
        numeroTranche: 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: todayStr, dateFin: ''
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
        setCotisationFormData({ code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, dateDebut: todayStr, dateFin: '' });
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
            dateDebut: fmtDateInput(c.dateDebut) || todayStr,
            dateFin: fmtDateInput(c.dateFin) || ''
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
        if (!siteCotisationFormData.idSite || !siteCotisationFormData.idTypeCotisation) {
            setActionError("Veuillez sélectionner un site et une cotisation.");
            return;
        }
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
        setIrsaFormData({ numeroTranche: tranchesIrsa.length + 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: todayStr, dateFin: '' });
        setActionError(null);
        setShowIrsaModal(true);
    };

    const handleOpenEditIrsa = (t) => {
        setEditingIrsa(t);
        setIrsaFormData({
            numeroTranche: t.numeroTranche,
            trancheMin: t.trancheMin,
            trancheMax: t.trancheMax ?? '',
            taux: t.taux,
            dateDebut: fmtDateInput(t.dateDebut) || todayStr,
            dateFin: fmtDateInput(t.dateFin) || ''
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

    const filteredSiteCotisations = showInactive
        ? siteCotisations
        : siteCotisations.filter(sc => sc.estActif !== false);

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

            {/* Onglets internes + Filtre d'affichage */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none p-2 gap-3 shadow-sm">
                <Tabs
                    tabs={[
                        { id: 'cotisations', label: 'Cotisations Sociales & Santé', icon: <ShieldCheck size={16} />, count: cotisations.length },
                        { id: 'site_cotisations', label: 'Cotisations par Site', icon: <MapPin size={16} />, count: filteredSiteCotisations.length },
                        { id: 'irsa', label: 'Barème IRSA (Progressif)', icon: <Percent size={16} />, count: tranchesIrsa.length }
                    ]}
                    activeTab={subSection}
                    onChange={setSubSection}
                    variant="pills"
                />

                {subSection === 'site_cotisations' && (
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 accent-[var(--color-brand-500)] rounded-none"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                )}
            </div>

            {subSection === 'cotisations' && (
                <CotisationsList
                    cotisations={cotisations}
                    onOpenCreate={handleOpenCreateCotisation}
                    onOpenEdit={handleOpenEditCotisation}
                />
            )}

            {subSection === 'site_cotisations' && (
                <SiteCotisationsList
                    siteCotisations={filteredSiteCotisations}
                    onOpenCreate={handleOpenCreateSiteCotisation}
                    onToggleStatus={handleToggleSiteCotisationStatus}
                />
            )}

            {subSection === 'irsa' && (
                <BaremeIrsaList
                    tranchesIrsa={tranchesIrsa}
                    onOpenCreate={handleOpenCreateIrsa}
                    onOpenEdit={handleOpenEditIrsa}
                />
            )}

            <CotisationFormModal
                isOpen={showCotisationModal}
                onClose={() => setShowCotisationModal(false)}
                onSubmit={handleSaveCotisation}
                editingCotisation={editingCotisation}
                formData={cotisationFormData}
                setFormData={setCotisationFormData}
                actionError={actionError}
            />

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