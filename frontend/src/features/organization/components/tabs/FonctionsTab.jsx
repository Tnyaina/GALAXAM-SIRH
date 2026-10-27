import { useState, useEffect, useCallback } from 'react';
import { Search, Plus, Eye } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import organisationService from '@/features/organization/api/organisationService';
import Card from '@/components/ui/data-display/Card';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';

import FonctionsList from '../views/FonctionsList';
import FonctionFormModal from '../modals/FonctionFormModal';

export default function FonctionsTab() {
    const { toast } = useToast();
    // État local
    const [fonctions, setFonctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
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
    const [fieldErrors, setFieldErrors] = useState({});

    // Chargement des données
    const loadData = useCallback(async () => {
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
    }, []);

    // Effet : charge les fonctions à l'ouverture de l'onglet.
    useEffect(() => {
        loadData();
    }, [loadData]);

    // Actions utilisateur
    const handleOpenCreate = () => {
        setEditingFonction(null);
        setFormData({ codeFonction: '', libelle: '', dispensePointage: false });
        setFieldErrors({});
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
        setFieldErrors({});
        setActionError(null);
        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);

        const errors = {};
        if (!formData.codeFonction.trim()) {
            errors.codeFonction = "Le code de la fonction est obligatoire.";
        }
        if (!formData.libelle.trim()) {
            errors.libelle = "L'intitulé du poste est obligatoire.";
        }

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }
        setFieldErrors({});

        setSubmitting(true);
        try {
            if (editingFonction) {
                await organisationService.updateFonction(editingFonction.idFonction, formData);
                toast({ title: 'Succès', message: `Poste "${formData.libelle}" modifié avec succès.`, type: 'success' });
            } else {
                await organisationService.createFonction(formData);
                toast({ title: 'Succès', message: `Poste "${formData.libelle}" créé avec succès.`, type: 'success' });
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
                        toast({ title: 'Succès', message: `Poste "${fonction.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await organisationService.activateFonction(fonction.idFonction);
                        toast({ title: 'Succès', message: `Poste "${fonction.libelle}" réactivé.`, type: 'success' });
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
        <div className="space-y-4">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Barre d'outils */}
            <Card className="overflow-hidden p-0" shadow="none">
                <div className="flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center p-3.5 border-b border-[var(--color-border-subtle)]">
                    <div className="flex items-center gap-3 flex-1 max-w-lg">
                        <div className="flex-1">
                            <Input
                                placeholder="Rechercher par intitulé de poste, code..."
                                value={search}
                                onChange={(e) => setSearch(e.target.value)}
                                iconLeft={<Search size={16} />}
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
                    {fonctions.length > 0 && (
                        <Button
                            onClick={handleOpenCreate}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Nouveau Poste / Fonction
                        </Button>
                    )}
                </div>

                {/* Tableau des fonctions */}
                <FonctionsList
                    fonctions={filteredFonctions}
                    onEdit={handleOpenEdit}
                    onToggleStatus={requestToggleStatus}
                    onCreate={handleOpenCreate}
                />
            </Card>

            {/* Modale Fonction */}
            <FonctionFormModal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                onSubmit={handleSave}
                editingFonction={editingFonction}
                formData={formData}
                setFormData={setFormData}
                actionError={actionError}
                fieldErrors={fieldErrors}
                setFieldErrors={setFieldErrors}
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
