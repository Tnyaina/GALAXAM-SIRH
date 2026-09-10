import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ShieldCheck, Percent, MapPin, Eye } from 'lucide-react';
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
import BaremeIrsaList from './irsa/BaremeIrsaList';
import IrsaTrancheModal from './irsa/IrsaTrancheModal';
import IrsaRevalorisationModal from './irsa/IrsaRevalorisationModal';

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

    // Modale IRSA
    const [showIrsaModal, setShowIrsaModal] = useState(false);
    const [editingIrsa, setEditingIrsa] = useState(null);
    const [irsaFormData, setIrsaFormData] = useState({
        numeroTranche: 1, trancheMin: 0, trancheMax: '', taux: 0, dateDebut: todayStr, dateFin: ''
    });
    // Modale Revalorisation IRSA (Loi de Finances)
    const [showIrsaRevaloModal, setShowIrsaRevaloModal] = useState(false);
    const [revaloActionError, setRevaloActionError] = useState(null);

    // Filtre période IRSA
    const [filtreIrsaPeriode, setFiltreIrsaPeriode] = useState('all'); // 'all' | '<dateDebut>|<dateFin>'

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
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.libelle}" modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createCotisation(cotisationFormData);
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.libelle}" créée.`, type: 'success' });
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
        if (!siteCotisationFormData.idSite || !siteCotisationFormData.idTypeCotisation) {
            setActionError("Veuillez sélectionner un site et une cotisation.");
            return;
        }
        const payload = {
            idSite: parseInt(siteCotisationFormData.idSite),
            idTypeCotisation: parseInt(siteCotisationFormData.idTypeCotisation)
        };
        setSubmitting(true);
        try {
            await paieConfigService.createSiteCotisation(payload);
            toast({ title: 'Succès', message: "Cotisation associée au site.", type: 'success' });
            setShowSiteCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur d'affectation.");
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleSiteCotisationStatus = (item) => {
        const isDeactivating = item.estActif;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver l'association de cotisation ?` : `Réactiver l'association ?`,
            message: isDeactivating
                ? `La cotisation "${item.cotisationLibelle}" ne sera plus prélevée pour les salariés rattachés au site "${item.siteLibelle}".`
                : `La cotisation redeviendra active pour ce site.`,
            confirmLabel: isDeactivating ? "Désactiver" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
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

    // Handlers IRSA
    const handleOpenCreateIrsa = () => {
        setEditingIrsa(null);
        const sorted = [...tranchesIrsa].sort((a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0));
        const lastTranche = sorted.length > 0 ? sorted[sorted.length - 1] : null;
        const nextNum = lastTranche ? (lastTranche.numeroTranche || 0) + 1 : 1;
        const nextMin = lastTranche && lastTranche.trancheMax !== null && lastTranche.trancheMax !== undefined && lastTranche.trancheMax !== ''
            ? parseFloat(lastTranche.trancheMax) + 1
            : 0;

        setIrsaFormData({
            numeroTranche: nextNum,
            trancheMin: nextMin,
            trancheMax: '',
            taux: 0,
            dateDebut: todayStr,
            dateFin: ''
        });
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
        if (submitting) return;
        setActionError(null);
        const payload = {
            ...irsaFormData,
            trancheMax: irsaFormData.trancheMax !== '' ? parseFloat(irsaFormData.trancheMax) : null
        };
        setSubmitting(true);
        try {
            if (editingIrsa) {
                await paieConfigService.updateBaremeIrsa(editingIrsa.idTranche, payload);
                toast({ title: 'Succès', message: `Tranche IRSA #${irsaFormData.numeroTranche} modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createBaremeIrsa(payload);
                toast({ title: 'Succès', message: `Tranche IRSA #${irsaFormData.numeroTranche} créée.`, type: 'success' });
            }
            setShowIrsaModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleOpenRevaloriserIrsa = () => {
        setRevaloActionError(null);
        setShowIrsaRevaloModal(true);
    };

    const handleSaveRevalorisationIrsa = async (payload) => {
        setSubmitting(true);
        setRevaloActionError(null);
        try {
            await paieConfigService.revaloriserBaremeIrsa(payload);
            toast({
                title: 'Succès',
                message: `Nouvelle grille IRSA programmée avec succès à partir du ${payload.nouvelleDateDebut}.`,
                type: 'success'
            });
            setShowIrsaRevaloModal(false);
            loadData();
        } catch (err) {
            setRevaloActionError(err.response?.data || "Erreur lors de la revalorisation du barème IRSA.");
        } finally {
            setSubmitting(false);
        }
    };

    // Filtrage IRSA par période
    const irsaGroupes = [...new Set(tranchesIrsa.map(t => `${t.dateDebut || ''}|${t.dateFin || ''}`))];
    const tranchesFiltered = filtreIrsaPeriode === 'all'
        ? tranchesIrsa
        : tranchesIrsa.filter(t => `${t.dateDebut || ''}|${t.dateFin || ''}` === filtreIrsaPeriode);

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

                {subSection === 'irsa' && irsaGroupes.length > 1 && (
                    <select
                        value={filtreIrsaPeriode}
                        onChange={e => setFiltreIrsaPeriode(e.target.value)}
                        className="text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] px-2 py-1 focus:outline-none focus:border-[var(--color-brand-500)]"
                    >
                        <option value="all">Toutes les périodes ({tranchesIrsa.length})</option>
                        {irsaGroupes.map(key => {
                            const [debut, fin] = key.split('|');
                            const label = `${debut ? new Date(debut).toLocaleDateString('fr-FR') : '?'} → ${fin ? new Date(fin).toLocaleDateString('fr-FR') : 'En vigueur'}`;
                            const count = tranchesIrsa.filter(t => `${t.dateDebut || ''}|${t.dateFin || ''}` === key).length;
                            return (
                                <option key={key} value={key}>{label} ({count})</option>
                            );
                        })}
                    </select>
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
                    onToggleStatus={requestToggleSiteCotisationStatus}
                />
            )}

            {subSection === 'irsa' && (
                <BaremeIrsaList
                    tranchesIrsa={tranchesFiltered}
                    onOpenCreate={handleOpenCreateIrsa}
                    onOpenEdit={handleOpenEditIrsa}
                    onOpenRevaloriser={handleOpenRevaloriserIrsa}
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

            <IrsaTrancheModal
                isOpen={showIrsaModal}
                onClose={() => setShowIrsaModal(false)}
                onSubmit={handleSaveIrsa}
                editingIrsa={editingIrsa}
                formData={irsaFormData}
                setFormData={setIrsaFormData}
                existingTranches={tranchesIrsa}
                actionError={actionError}
                submitting={submitting}
            />

            <IrsaRevalorisationModal
                isOpen={showIrsaRevaloModal}
                onClose={() => setShowIrsaRevaloModal(false)}
                onSubmit={handleSaveRevalorisationIrsa}
                currentTranches={tranchesIrsa.filter(t => {
                    const today = new Date(); today.setHours(0, 0, 0, 0);
                    const debut = t.dateDebut ? new Date(t.dateDebut) : null;
                    if (debut) debut.setHours(0, 0, 0, 0);
                    const fin = t.dateFin ? new Date(t.dateFin) : null;
                    if (fin) fin.setHours(23, 59, 59, 999);
                    return debut && debut <= today && (!fin || fin >= today);
                })}
                actionError={revaloActionError}
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