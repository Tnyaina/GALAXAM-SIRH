import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { FileText, UserCheck, Landmark } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Tabs from '@/components/ui/navigation/Tabs';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';

import TypesContratList from './referentiels/TypesContratList';
import TypesIntervenantList from './referentiels/TypesIntervenantList';
import BanquesList from './referentiels/BanquesList';

function TypeContratFormModal({ isOpen, onClose, onSubmit, formData, setFormData, actionError, submitting = false }) {
    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Modifier le Type de Contrat" icon={<FileText size={18} />} size="lg" footer={<><Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>Annuler</Button><Button type="submit" form="contrat-form" variant="primary" size="sm" loading={submitting}>Enregistrer</Button></>}>
            <form id="contrat-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input label="Code du Contrat" required disabled value={formData.code} onChange={(event) => setFormData({ ...formData, code: event.target.value.toUpperCase() })} placeholder="Ex: CDI, CDD, STAGE, INTERIM" />
                <Input label="Libellé Complet" required value={formData.libelle} onChange={(event) => setFormData({ ...formData, libelle: event.target.value })} placeholder="Ex: Contrat de Travail à Durée Indéterminée" />
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1"><Checkbox label="Date de fin obligatoire lors de la saisie d'un contrat" checked={formData.exigeDateFin} onChange={(event) => setFormData({ ...formData, exigeDateFin: event.target.checked })} /><p className="text-[11px] text-[var(--color-fg-muted)] ml-6">Cochez cette option pour les contrats temporaires (CDD, stage, prestation) nécessitant impérativement un terme défini.</p></div>
            </form>
        </Modal>
    );
}

function TypeIntervenantFormModal({ isOpen, onClose, onSubmit, formData, setFormData, actionError, submitting = false }) {
    const toggles = [
        ['soumisIrsa', "Soumis à l'Impôt sur le Revenu Salarial (IRSA)"],
        ['soumisIsi5', "Soumis à la Retenue Libératoire ISI (Prestataires / Honoraires)"],
        ['soumisCnaps', "Cotise à la CNaPS (Caisse Nationale de Prévoyance Sociale)"],
        ['soumisSante', "Cotise aux organismes de santé interentreprises (OSTIE / OMSI)"],
    ];

    const handleToggle = (field, checked) => {
        const next = { ...formData, [field]: checked };
        // Exclusivité mutuelle IRSA (salarié) vs ISI (prestataire externe)
        if (field === 'soumisIrsa' && checked) {
            next.soumisIsi5 = false;
        } else if (field === 'soumisIsi5' && checked) {
            next.soumisIrsa = false;
        }
        setFormData(next);
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Modifier le Régime de l'Intervenant"
            icon={<UserCheck size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="intervenant-form" variant="primary" size="sm" loading={submitting}>
                        Enregistrer
                    </Button>
                </>
            }
        >
            <form id="intervenant-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input
                    label="Code du Type d'Intervenant"
                    required
                    disabled
                    value={formData.code}
                    onChange={(event) => setFormData({ ...formData, code: event.target.value.toUpperCase() })}
                    placeholder="Ex: SALARIE, CONSULTANT, STAGIAIRE"
                />
                <Input
                    label="Libellé Complet"
                    required
                    value={formData.libelle}
                    onChange={(event) => setFormData({ ...formData, libelle: event.target.value })}
                    placeholder="Ex: Salarié National de Droit Commun"
                />
                <div className="space-y-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <p className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                        Régime Fiscal & Cotisations Sociales
                    </p>
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                        {toggles.map(([field, label]) => (
                            <Checkbox
                                key={field}
                                label={label}
                                checked={formData[field]}
                                onChange={(event) => handleToggle(field, event.target.checked)}
                            />
                        ))}
                    </div>
                    <p className="text-[11px] text-[var(--color-fg-muted)]">
                        Règle légale : À Madagascar, un intervenant est soit assujetti à l'IRSA (salarié), soit à la retenue ISI (prestation/honoraire) ; les deux régimes d'imposition sont mutuellement exclusifs.
                    </p>
                </div>
            </form>
        </Modal>
    );
}

function BanqueFormModal({ isOpen, onClose, onSubmit, editingBanque, formData, setFormData, actionError, submitting = false }) {
    return <Modal isOpen={isOpen} onClose={onClose} title={editingBanque ? 'Modifier la Banque' : 'Nouvelle Banque Partenaire'} icon={<Landmark size={18} />} size="lg" footer={<><Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>Annuler</Button><Button type="submit" form="banque-form" variant="primary" size="sm" loading={submitting}>{editingBanque ? 'Enregistrer' : 'Créer la banque'}</Button></>}><form id="banque-form" onSubmit={onSubmit} className="space-y-4">{actionError && <FormError message={actionError} />}<Input label="Code Banque (5 chiffres normalisés)" required disabled={!!editingBanque} value={formData.codeBanque} onChange={(event) => setFormData({ ...formData, codeBanque: event.target.value.toUpperCase() })} placeholder="Ex: 00004, 00005" /><Input label="Raison Sociale / Nom de la Banque" required value={formData.nomBanque} onChange={(event) => setFormData({ ...formData, nomBanque: event.target.value })} placeholder="Ex: Bank of Africa Madagascar" /><Input label="Code SWIFT / BIC (Optionnel)" value={formData.codeSwift} onChange={(event) => setFormData({ ...formData, codeSwift: event.target.value.toUpperCase() })} placeholder="Ex: AFRIMGM1" /></form></Modal>;
}

export default function ReferentielsRhTab() {
    // Contexte et navigation
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
        loadData();
    }, []);

    // Actions utilisateur : types de contrat, intervenants et banques.
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
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
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
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
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
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
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
        <div className="space-y-6">
            <div>
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
