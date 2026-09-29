import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, Eye, Briefcase, Power } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox } from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';

function FonctionFormModal({ isOpen, onClose, onSubmit, editingFonction, formData, setFormData, actionError, submitting = false }) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingFonction ? "Modifier le Poste / Fonction" : "Nouveau Poste / Fonction"}
            icon={<Briefcase size={18} />}
            size="lg"
            footer={<><Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>Annuler</Button><Button type="submit" form="fonction-form" variant="primary" size="sm" loading={submitting}>{editingFonction ? "Enregistrer les modifications" : "Créer le poste"}</Button></>}
        >
            <form id="fonction-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}
                <Input label="Code Fonction / Poste" required disabled={!!editingFonction} value={formData.codeFonction} onChange={(event) => setFormData({ ...formData, codeFonction: event.target.value.toUpperCase() })} placeholder="Ex: DG, DAF, DECLARANT, CHAUFFEUR" />
                <Input label="Intitulé du Poste" required value={formData.libelle} onChange={(event) => setFormData({ ...formData, libelle: event.target.value })} placeholder="Ex: Responsable des Opérations Douanières" />
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1">
                    <Checkbox label="Dispense de pointage (Poste / Cadre autonome)" checked={formData.dispensePointage} onChange={(event) => setFormData({ ...formData, dispensePointage: event.target.checked })} />
                    <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">Les collaborateurs occupant ce poste ne seront pas assujettis au pointage quotidien et ne généreront pas d'anomalies de retard.</p>
                </div>
            </form>
        </Modal>
    );
}

export default function FonctionsTab() {
    // État local
    const [fonctions, setFonctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');
    const [showInactive, setShowInactive] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // État de confirmation et de modale
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        onConfirm: null,
        loading: false
    });

    const [showModal, setShowModal] = useState(false);
    const [editingFonction, setEditingFonction] = useState(null);
    const [formData, setFormData] = useState({
        codeFonction: '',
        libelle: '',
        dispensePointage: false
    });

    // Chargement des données
    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await organisationService.getFonctions();
            setFonctions(data || []);
        } catch (err) {
            setError("Erreur lors du chargement des fonctions/postes.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge les fonctions à l'ouverture de l'onglet.
    useEffect(() => {
        loadData();
    }, []);

    // Actions utilisateur
    const handleOpenCreate = () => {
        setEditingFonction(null);
        setFormData({ codeFonction: '', libelle: '', dispensePointage: false });
        setActionError(null);
        setShowModal(true);
    };

    const handleOpenEdit = (fonction) => {
        setEditingFonction(fonction);
        setFormData({
            codeFonction: fonction.codeFonction,
            libelle: fonction.libelle,
            dispensePointage: fonction.dispensePointage || false
        });
        setActionError(null);
        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setActionSuccess(null);

        if (!formData.codeFonction.trim()) {
            setActionError("Le code de la fonction est obligatoire.");
            return;
        }
        if (!formData.libelle.trim()) {
            setActionError("L'intitulé du poste est obligatoire.");
            return;
        }

        setSubmitting(true);
        try {
            if (editingFonction) {
                await organisationService.updateFonction(editingFonction.idFonction, formData);
                setActionSuccess(`Poste "${formData.libelle}" modifié avec succès.`);
            } else {
                await organisationService.createFonction(formData);
                setActionSuccess(`Poste "${formData.libelle}" créé avec succès.`);
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde de la fonction."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleStatus = (fonction) => {
        const isDeactivating = fonction.estActif;

        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le poste "${fonction.libelle}" ?` : `Réactiver le poste "${fonction.libelle}" ?`,
            message: isDeactivating
                ? `Attention : La désactivation de ce poste empêchera son affectation lors de l'embauche ou du renouvellement de contrats. Les contrats existants resteront valides.`
                : `Le poste redeviendra immédiatement sélectionnable dans les fiches collaborateurs et contrats.`,
            confirmLabel: isDeactivating ? "Désactiver le poste" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await organisationService.deactivateFonction(fonction.idFonction);
                        setActionSuccess(`Poste "${fonction.libelle}" désactivé.`);
                    } else {
                        await organisationService.activateFonction(fonction.idFonction);
                        setActionSuccess(`Poste "${fonction.libelle}" réactivé.`);
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch {
                    setError("Erreur lors de la modification du statut du poste.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    // Données dérivées
    const filteredFonctions = fonctions.filter(f =>
        (showInactive || f.estActif !== false) &&
        (f.codeFonction?.toLowerCase().includes(search.toLowerCase()) ||
         f.libelle?.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && fonctions.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={8} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}
            {error && <Alert type="error" message={error} />}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="flex items-center gap-3 flex-1 max-w-lg">
                    <div className="flex-1">
                        <Input
                            placeholder="Rechercher par intitulé de poste, code..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            icon={<Search size={16} />}
                        />
                    </div>
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        <Eye size={14} />
                        <span>Afficher inactifs</span>
                    </label>
                </div>
                <Button
                    onClick={handleOpenCreate}
                    variant="primary"
                    size="sm"
                    iconLeft={<Plus size={16} />}
                >
                    Nouveau Poste / Fonction
                </Button>
            </div>

            {/* Tableau des fonctions */}
            <Table>
                <Thead>
                    <Th>Code Poste</Th>
                    <Th>Intitulé du Poste / Fonction</Th>
                    <Th>Régime Pointage</Th>
                    <Th>Statut</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {filteredFonctions.length > 0 ? (
                        filteredFonctions.map((f) => (
                            <Tr key={f.idFonction}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{f.codeFonction}</Td>
                                <Td className="font-bold text-[var(--color-fg)]">
                                    <div className="flex items-center gap-2">
                                        <Briefcase size={15} className="text-[var(--color-fg-muted)] shrink-0" />
                                        <span>{f.libelle}</span>
                                    </div>
                                </Td>
                                <Td>
                                    {f.dispensePointage ? (
                                        <Badge variant="warning" size="sm">
                                            Dispense de pointage
                                        </Badge>
                                    ) : (
                                        <Badge variant="default" size="sm">
                                            Pointage standard
                                        </Badge>
                                    )}
                                </Td>
                                <Td>
                                    <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                        {f.estActif ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => handleOpenEdit(f)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier le poste"
                                            aria-label={`Modifier ${f.libelle}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => requestToggleStatus(f)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                f.estActif
                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                            title={f.estActif ? "Désactiver le poste" : "Réactiver le poste"}
                                            aria-label={f.estActif ? `Désactiver ${f.libelle}` : `Activer ${f.libelle}`}
                                        >
                                            <Power size={15} />
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={5} className="p-0">
                                <EmptyState
                                    icon={Briefcase}
                                    title="Aucun poste ou fonction trouvé"
                                    description="Ajoutez les fonctions et postes de l'organigramme pour les lier aux contrats."
                                    actionLabel="Créer un poste"
                                    onAction={handleOpenCreate}
                                />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale Fonction */}
            <FonctionFormModal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                onSubmit={handleSave}
                editingFonction={editingFonction}
                formData={formData}
                setFormData={setFormData}
                actionError={actionError}
                submitting={submitting}
            />

            {/* Boîte de confirmation sécurisée */}
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
