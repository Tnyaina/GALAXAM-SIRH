import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ShieldCheck, MapPin, Eye } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import organisationService from '@/api/admin/organisationService';
import { fmtDateInput } from '@/utils/paieUtils';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';

import CotisationsList from './cotisations/CotisationsList';
import CotisationFormModal from './cotisations/CotisationFormModal';
import SiteCotisationsList from './cotisations/SiteCotisationsList';
import SiteCotisationFormModal from './cotisations/SiteCotisationFormModal';

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

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();
    const [showInactive, setShowInactive] = useState(false);

    // Confirmation Dialog state
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        onConfirm: null,
        loading: false
    });

    // Modale Cotisation
    const [showCotisationModal, setShowCotisationModal] = useState(false);
    const [editingCotisation, setEditingCotisation] = useState(null);
    const [cotisationFormData, setCotisationFormData] = useState({
        code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, portee: 'SITE', dateDebut: todayStr, dateFin: ''
    });

    // Modale Site Cotisation
    const [showSiteCotisationModal, setShowSiteCotisationModal] = useState(false);
    const [siteCotisationFormData, setSiteCotisationFormData] = useState({
        idSite: '', idTypeCotisation: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [cotData, siteCotData, sitesData] = await Promise.all([
                paieConfigService.getCotisations(),
                paieConfigService.getSiteCotisations(),
                organisationService.getSites()
            ]);
            setCotisations(cotData);
            setSiteCotisations(siteCotData);
            setSites(sitesData);
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
        setCotisationFormData({ code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, portee: 'SITE', dateDebut: todayStr, dateFin: '' });
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
            portee: c.portee || 'SITE',
            dateDebut: fmtDateInput(c.dateDebut) || todayStr,
            dateFin: fmtDateInput(c.dateFin) || ''
        });
        setActionError(null);
        setShowCotisationModal(true);
    };

    const handleSaveCotisation = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
        try {
            if (editingCotisation) {
                await paieConfigService.updateCotisation(editingCotisation.idTypeCotisation, cotisationFormData);
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.code}" modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createCotisation(cotisationFormData);
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.code}" créée.`, type: 'success' });
            }
            setShowCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        } finally {
            setSubmitting(false);
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
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
        try {
            await paieConfigService.createSiteCotisation({
                idSite: parseInt(siteCotisationFormData.idSite, 10),
                idTypeCotisation: parseInt(siteCotisationFormData.idTypeCotisation, 10)
            });
            toast({ title: 'Succès', message: 'Cotisation rattachée au site.', type: 'success' });
            setShowSiteCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de rattachement.");
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleCotisationStatus = (item) => {
        const isActif = item.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        const message = isActif
            ? `Êtes-vous sûr de vouloir désactiver la cotisation "${item.libelle}" (${item.code}) ?\n\nATTENTION : Cela désactivera automatiquement toutes ses instances rattachées aux sites d'exploitation.`
            : `Êtes-vous sûr de vouloir réactiver la cotisation "${item.libelle}" (${item.code}) ?`;

        setConfirmState({
            isOpen: true,
            title: `${actionText} la cotisation`,
            message,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isActif) {
                        await paieConfigService.deactivateCotisation(item.idTypeCotisation);
                        toast({
                            title: 'Cotisation désactivée',
                            message: `La cotisation "${item.code}" et ses rattachements par site ont été désactivés.`,
                            type: 'success'
                        });
                    } else {
                        await paieConfigService.activateCotisation(item.idTypeCotisation);
                        toast({
                            title: 'Cotisation réactivée',
                            message: `La cotisation "${item.code}" a été réactivée avec succès.`,
                            type: 'success'
                        });
                    }
                    await loadData();
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                } catch (err) {
                    setError("Erreur lors de la modification du statut de la cotisation.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    console.error(err);
                }
            }
        });
    };

    const requestToggleSiteCotisationStatus = (item) => {
        const actionText = item.estActif ? 'Désactiver' : 'Activer';
        setConfirmState({
            isOpen: true,
            title: `${actionText} l'association`,
            message: `Êtes-vous sûr de vouloir ${actionText.toLowerCase()} la cotisation "${item.cotisationLibelle}" pour le site "${item.siteLibelle}" ?`,
            confirmLabel: actionText,
            variant: item.estActif ? 'danger' : 'primary',
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (item.estActif) {
                        await paieConfigService.deactivateSiteCotisation(item.idSiteCotisation);
                        toast({ title: 'Succès', message: `Association "${item.siteLibelle} - ${item.cotisationLibelle}" désactivée.`, type: 'success' });
                    } else {
                        await paieConfigService.activateSiteCotisation(item.idSiteCotisation);
                        toast({ title: 'Succès', message: `Association "${item.siteLibelle} - ${item.cotisationLibelle}" réactivée.`, type: 'success' });
                    }
                    setSiteCotisations(prev =>
                        prev.map(sc =>
                            sc.idSiteCotisation === item.idSiteCotisation
                                ? { ...sc, estActif: !item.estActif }
                                : sc
                        )
                    );
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                } catch (err) {
                    setError("Erreur lors de la modification du statut.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    console.error(err);
                }
            }
        });
    };

    const filteredSiteCotisations = showInactive
        ? siteCotisations
        : siteCotisations.filter(sc => sc.estActif !== false);

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

            {/* Onglets internes + Filtre d'affichage */}
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] rounded-none p-2 gap-3 shadow-sm">
                <Tabs
                    tabs={[
                        { id: 'cotisations', label: 'Cotisations Sociales Référentielles', icon: <ShieldCheck size={16} />, count: cotisations.length },
                        { id: 'site_cotisations', label: 'Rattachement par Site', icon: <MapPin size={16} />, count: filteredSiteCotisations.length },
                    ]}
                    activeTab={subSection === 'irsa' ? 'cotisations' : subSection}
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

            {(subSection === 'cotisations' || subSection === 'irsa') && (
                <CotisationsList
                    cotisations={cotisations}
                    onOpenCreate={handleOpenCreateCotisation}
                    onOpenEdit={handleOpenEditCotisation}
                    onToggleStatus={requestToggleCotisationStatus}
                />
            )}

            {subSection === 'site_cotisations' && (
                <SiteCotisationsList
                    siteCotisations={filteredSiteCotisations}
                    onOpenCreate={handleOpenCreateSiteCotisation}
                    onToggleStatus={requestToggleSiteCotisationStatus}
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
                submitting={submitting}
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
                submitting={submitting}
            />

            {/* Boîte de confirmation */}
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