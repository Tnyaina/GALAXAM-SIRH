import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { FileText, UserCheck, Landmark } from 'lucide-react';
import organisationService from '@/features/organization/api/organisationService';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

import TypesContratList from './referentiels/TypesContratList';
import TypeContratFormModal from './referentiels/TypeContratFormModal';
import TypesIntervenantList from './referentiels/TypesIntervenantList';
import TypeIntervenantFormModal from './referentiels/TypeIntervenantFormModal';
import BanquesList from './referentiels/BanquesList';
import BanqueFormModal from './referentiels/BanqueFormModal';

export default function ReferentielsRhTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const activeSection = searchParams.get('subtab') || 'contrats'; // 'contrats' | 'intervenants' | 'banques'
    const toast = useToast();

    const setActiveSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'rh');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [banques, setBanques] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    // Confirmation de statut (désactivation / activation)
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

    // Modale Intervenant
    const [showIntervenantModal, setShowIntervenantModal] = useState(false);
    const [editingIntervenant, setEditingIntervenant] = useState(null);
    const [intervenantFormData, setIntervenantFormData] = useState({
        code: '', libelle: '', soumisIrsa: true, soumisIsi5: false, soumisCnaps: true, soumisSante: true
    });

    // Modale Banque
    const [showBanqueModal, setShowBanqueModal] = useState(false);
    const [editingBanque, setEditingBanque] = useState(null);
    const [banqueFormData, setBanqueFormData] = useState({ codeBanque: '', nomBanque: '', codeSwift: '' });

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

    useEffect(() => {
        loadData();
    }, []);

    // Handlers Type Contrat
    const handleOpenEditContrat = (tc) => {
        setEditingContrat(tc);
        setContratFormData({ code: tc.code, libelle: tc.libelle, exigeDateFin: tc.exigeDateFin !== false });
        setActionError(null);
        setShowContratModal(true);
    };

    const handleSaveContrat = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        if (!contratFormData.code.trim() || !contratFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        if (!editingContrat) return;
        setSubmitting(true);
        try {
            await organisationService.updateTypeContrat(editingContrat.idTypeContrat, contratFormData);
            toast({ title: 'Succès', message: `Type de contrat "${contratFormData.libelle}" modifié.`, type: 'success' });
            setShowContratModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
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
        setActionError(null);
        setShowIntervenantModal(true);
    };

    const handleSaveIntervenant = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        if (!intervenantFormData.code.trim() || !intervenantFormData.libelle.trim()) {
            setActionError("Le code et le libellé sont obligatoires.");
            return;
        }
        if (!editingIntervenant) return;
        setSubmitting(true);
        try {
            await organisationService.updateTypeIntervenant(editingIntervenant.idTypeIntervenant, intervenantFormData);
            toast({ title: 'Succès', message: `Type d'intervenant "${intervenantFormData.libelle}" modifié.`, type: 'success' });
            setShowIntervenantModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur de sauvegarde.");
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Banque
    const handleOpenCreateBanque = () => {
        setEditingBanque(null);
        setBanqueFormData({ codeBanque: '', nomBanque: '', codeSwift: '' });
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleOpenEditBanque = (b) => {
        setEditingBanque(b);
        setBanqueFormData({ codeBanque: b.codeBanque, nomBanque: b.nomBanque, codeSwift: b.codeSwift || '' });
        setActionError(null);
        setShowBanqueModal(true);
    };

    const handleSaveBanque = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        if (!banqueFormData.codeBanque.trim() || !banqueFormData.nomBanque.trim()) {
            setActionError("Le code banque et le nom de la banque sont obligatoires.");
            return;
        }
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
            setActionError(err.response?.data || "Erreur de sauvegarde.");
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
                    setActionError(err.response?.data || "Erreur lors du changement de statut.");
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
                    setActionError(err.response?.data || "Erreur lors du changement de statut.");
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
                    setActionError(err.response?.data || "Erreur lors du changement de statut.");
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
        <div className="space-y-6">
            <div className="border-b border-[var(--color-border-subtle)] pb-4">
                <Tabs
                    tabs={[
                        { id: 'contrats', label: `Types de Contrat (${typesContrat.length})`, icon: <FileText size={16} /> },
                        { id: 'intervenants', label: `Régimes Intervenants (${typesIntervenant.length})`, icon: <UserCheck size={16} /> },
                        { id: 'banques', label: `Banques & Agences (${banques.length})`, icon: <Landmark size={16} /> }
                    ]}
                    activeTab={activeSection}
                    onChange={setActiveSection}
                    variant="underline"
                />
            </div>

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
        </div>
    );
}