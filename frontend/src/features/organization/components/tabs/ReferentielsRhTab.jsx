import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { FileText, UserCheck, Landmark } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import SideTabs from '@/components/ui/navigation/SideTabs';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

import TypesContratList from '../views/TypesContratList';
import TypesIntervenantList from '../views/TypesIntervenantList';
import BanquesList from '../views/BanquesList';
import TypeContratFormModal from '../modals/TypeContratFormModal';
import TypeIntervenantFormModal from '../modals/TypeIntervenantFormModal';
import BanqueFormModal from '../modals/BanqueFormModal';

export default function ReferentielsRhTab() {
    // Contexte et navigation
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedSection = searchParams.get('subtab');
    const activeSection = ['contrats', 'intervenants', 'banques'].includes(requestedSection) ? requestedSection : 'contrats';
    const { toast } = useToast();

    const setActiveSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'rh');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    // État local
    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [banques, setBanques] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    // État de confirmation (désactivation / activation)
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        loading: false,
        onConfirm: () => {}
    });

    // Modale Contrat
    const [showContratModal, setShowContratModal] = useState(false);
    const [editingContrat, setEditingContrat] = useState(null);
    const [contratFormData, setContratFormData] = useState({ code: '', libelle: '', exigeDateFin: false });
    const [contratFieldErrors, setContratFieldErrors] = useState({});

    // Modale Intervenant
    const [showIntervenantModal, setShowIntervenantModal] = useState(false);
    const [editingIntervenant, setEditingIntervenant] = useState(null);
    const [intervenantFormData, setIntervenantFormData] = useState({
        code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true
    });
    const [intervenantFieldErrors, setIntervenantFieldErrors] = useState({});

    // Modale Banque
    const [showBanqueModal, setShowBanqueModal] = useState(false);
    const [editingBanque, setEditingBanque] = useState(null);
    const [banqueFormData, setBanqueFormData] = useState({ codeBanque: '', nomBanque: '', codeSwift: '' });
    const [banqueFieldErrors, setBanqueFieldErrors] = useState({});

    // Chargement des référentiels
    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [tcData, tiData, bData] = await Promise.all([
                organisationService.getTypesContrat(),
                organisationService.getTypesIntervenant(),
                organisationService.getBanques()
            ]);
            setTypesContrat(tcData || []);
            setTypesIntervenant(tiData || []);
            setBanques(bData || []);
        } catch (err) {
            setError("Erreur lors du chargement des référentiels RH. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge les référentiels à l'ouverture de l'onglet.
    useEffect(() => {
        let isMounted = true;
        const fetchData = async () => {
            setLoading(true);
            setError(null);
            try {
                const [tcData, tiData, bData] = await Promise.all([
                    organisationService.getTypesContrat(),
                    organisationService.getTypesIntervenant(),
                    organisationService.getBanques()
                ]);
                if (isMounted) {
                    setTypesContrat(tcData || []);
                    setTypesIntervenant(tiData || []);
                    setBanques(bData || []);
                }
            } catch (err) {
                if (isMounted) {
                    setError("Erreur lors du chargement des référentiels RH. Veuillez réessayer.");
                    console.error(err);
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };
        fetchData();
        return () => {
            isMounted = false;
        };
    }, []);

    // Actions utilisateur : types de contrat, intervenants et banques.
    const handleOpenEditContrat = (tc) => {
        setEditingContrat(tc);
        setContratFormData({ code: tc.code, libelle: tc.libelle, exigeDateFin: tc.exigeDateFin !== false });
        setContratFieldErrors({});
        setActionError(null);
        setShowContratModal(true);
    };

    const handleSaveContrat = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!contratFormData.libelle.trim()) {
            errors.libelle = "Le libellé du type de contrat est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setContratFieldErrors(errors);
            return;
        }
        setContratFieldErrors({});

        if (!editingContrat) return;
        setSubmitting(true);
        try {
            await organisationService.updateTypeContrat(editingContrat.idTypeContrat, contratFormData);
            toast({ title: 'Succès', message: `Type de contrat "${contratFormData.libelle}" modifié.`, type: 'success' });
            setShowContratModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde du type de contrat."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Type Intervenant
    const handleOpenEditIntervenant = (ti) => {
        setEditingIntervenant(ti);
        setIntervenantFormData({
            code: ti.code,
            libelle: ti.libelle,
            soumisIrsa: ti.soumisIrsa !== false,
            soumisIsi5: ti.soumisIsi5 === true,
            soumisCnaps: ti.soumisCnaps !== false,
            soumisSante: ti.soumisSante !== false
        });
        setIntervenantFieldErrors({});
        setActionError(null);
        setShowIntervenantModal(true);
    };

    const handleSaveIntervenant = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!intervenantFormData.libelle.trim()) {
            errors.libelle = "Le libellé du profil intervenant est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setIntervenantFieldErrors(errors);
            return;
        }
        setIntervenantFieldErrors({});

        if (!editingIntervenant) return;
        setSubmitting(true);
        try {
            await organisationService.updateTypeIntervenant(editingIntervenant.idTypeIntervenant, intervenantFormData);
            toast({ title: 'Succès', message: `Type d'intervenant "${intervenantFormData.libelle}" modifié.`, type: 'success' });
            setShowIntervenantModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde du profil intervenant."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Banque
    const handleOpenCreateBanque = () => {
        setEditingBanque(null);
        setBanqueFormData({ codeBanque: '', nomBanque: '', codeSwift: '' });
        setBanqueFieldErrors({});
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleOpenEditBanque = (b) => {
        setEditingBanque(b);
        setBanqueFormData({ codeBanque: b.codeBanque, nomBanque: b.nomBanque, codeSwift: b.codeSwift || '' });
        setBanqueFieldErrors({});
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleSaveBanque = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!banqueFormData.codeBanque.trim()) {
            errors.codeBanque = "Le code banque est obligatoire.";
        }
        if (!banqueFormData.nomBanque.trim()) {
            errors.nomBanque = "Le nom de la banque est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setBanqueFieldErrors(errors);
            return;
        }
        setBanqueFieldErrors({});

        setSubmitting(true);
        try {
            if (editingBanque) {
                await organisationService.updateBanque(editingBanque.idBanque, banqueFormData);
                toast({ title: 'Succès', message: `Banque "${banqueFormData.nomBanque}" modifiée.`, type: 'success' });
            } else {
                await organisationService.createBanque(banqueFormData);
                toast({ title: 'Succès', message: `Banque "${banqueFormData.nomBanque}" créée.`, type: 'success' });
            }
            setShowBanqueModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde de la banque."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Activation / Désactivation
    const handleToggleContratStatus = (tc) => {
        const isDeactivating = tc.estActif !== false;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le type de contrat "${tc.libelle}" ?` : `Réactiver le type de contrat "${tc.libelle}" ?`,
            message: isDeactivating
                ? `La désactivation de ce type de contrat empêchera sa sélection lors de la création ou modification de contrats. Les contrats existants resteront valides et historisés.`
                : `Ce type de contrat sera de nouveau disponible immédiatement pour les contrats des collaborateurs.`,
            confirmLabel: isDeactivating ? "Désactiver" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateTypeContrat(tc.idTypeContrat);
                        toast({ title: 'Succès', message: `Type de contrat "${tc.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateTypeContrat(tc.idTypeContrat);
                        toast({ title: 'Succès', message: `Type de contrat "${tc.libelle}" réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setActionError(getErrorMessage(err, "Erreur lors du changement de statut."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const handleToggleIntervenantStatus = (ti) => {
        const isDeactivating = ti.estActif !== false;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le type d'intervenant "${ti.libelle}" ?` : `Réactiver le type d'intervenant "${ti.libelle}" ?`,
            message: isDeactivating
                ? `La désactivation de ce régime intervenant empêchera son attribution aux nouveaux dossiers. Les données existantes resteront historisées.`
                : `Ce type d'intervenant sera de nouveau disponible immédiatement.`,
            confirmLabel: isDeactivating ? "Désactiver" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateTypeIntervenant(ti.idTypeIntervenant);
                        toast({ title: 'Succès', message: `Type d'intervenant "${ti.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateTypeIntervenant(ti.idTypeIntervenant);
                        toast({ title: 'Succès', message: `Type d'intervenant "${ti.libelle}" réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setActionError(getErrorMessage(err, "Erreur lors du changement de statut."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const handleToggleBanqueStatus = (b) => {
        const isDeactivating = b.estActif !== false;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver la banque "${b.nomBanque}" ?` : `Réactiver la banque "${b.nomBanque}" ?`,
            message: isDeactivating
                ? `La désactivation de cet établissement bancaire empêchera sa sélection pour les nouveaux comptes bancaires employés. Les coordonnées existantes resteront intactes.`
                : `Cette banque sera de nouveau sélectionnable immédiatement pour les règlements et virements de paie.`,
            confirmLabel: isDeactivating ? "Désactiver" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateBanque(b.idBanque);
                        toast({ title: 'Succès', message: `Banque "${b.nomBanque}" désactivée.`, type: 'success' });
                    } else {
                        await organisationService.activateBanque(b.idBanque);
                        toast({ title: 'Succès', message: `Banque "${b.nomBanque}" réactivée.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setActionError(getErrorMessage(err, "Erreur lors du changement de statut."));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    if (loading && typesContrat.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={6} columns={4} />
            </div>
        );
    }

    return (
        <SideTabs
            tabs={[
                { id: 'contrats', label: 'Types de contrat', icon: <FileText size={16} />, count: typesContrat.length },
                { id: 'intervenants', label: 'Régimes intervenants', icon: <UserCheck size={16} />, count: typesIntervenant.length },
                { id: 'banques', label: 'Banques & agences', icon: <Landmark size={16} />, count: banques.length }
            ]}
            activeTab={activeSection}
            onChange={setActiveSection}
            className="space-y-6"
        >
            {error && <Alert type="error" message={error} />}

            {activeSection === 'contrats' && (
                <TypesContratList
                    typesContrat={typesContrat}
                    onOpenEdit={handleOpenEditContrat}
                    onToggleStatus={handleToggleContratStatus}
                />
            )}

            {activeSection === 'intervenants' && (
                <TypesIntervenantList
                    typesIntervenant={typesIntervenant}
                    onOpenEdit={handleOpenEditIntervenant}
                    onToggleStatus={handleToggleIntervenantStatus}
                />
            )}

            {activeSection === 'banques' && (
                <BanquesList
                    banques={banques}
                    onOpenCreate={handleOpenCreateBanque}
                    onOpenEdit={handleOpenEditBanque}
                    onToggleStatus={handleToggleBanqueStatus}
                />
            )}

            <TypeContratFormModal
                isOpen={showContratModal}
                onClose={() => setShowContratModal(false)}
                onSubmit={handleSaveContrat}
                editingContrat={editingContrat}
                formData={contratFormData}
                setFormData={setContratFormData}
                actionError={actionError}
                fieldErrors={contratFieldErrors}
                setFieldErrors={setContratFieldErrors}
                submitting={submitting}
            />

            <TypeIntervenantFormModal
                isOpen={showIntervenantModal}
                onClose={() => setShowIntervenantModal(false)}
                onSubmit={handleSaveIntervenant}
                editingIntervenant={editingIntervenant}
                formData={intervenantFormData}
                setFormData={setIntervenantFormData}
                actionError={actionError}
                fieldErrors={intervenantFieldErrors}
                setFieldErrors={setIntervenantFieldErrors}
                submitting={submitting}
            />

            <BanqueFormModal
                isOpen={showBanqueModal}
                onClose={() => setShowBanqueModal(false)}
                onSubmit={handleSaveBanque}
                editingBanque={editingBanque}
                formData={banqueFormData}
                setFormData={setBanqueFormData}
                actionError={actionError}
                fieldErrors={banqueFieldErrors}
                setFieldErrors={setBanqueFieldErrors}
                submitting={submitting}
            />

            <ConfirmDialog
                isOpen={confirmState.isOpen}
                title={confirmState.title}
                message={confirmState.message}
                confirmLabel={confirmState.confirmLabel}
                variant={confirmState.variant}
                loading={confirmState.loading}
                onConfirm={confirmState.onConfirm}
                onCancel={() => setConfirmState(prev => ({ ...prev, isOpen: false }))}
            />
        </SideTabs>
    );
}
