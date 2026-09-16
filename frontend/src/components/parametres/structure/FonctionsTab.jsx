import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, Eye, Briefcase, Power } from 'lucide-react';
import organisationService from '@/api/admin/organisationService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import FonctionFormModal from './fonctions/FonctionFormModal';

export default function FonctionsTab() {
    const [fonctions, setFonctions] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);
    const [search, setSearch] = useState('');
    const [showInactive, setShowInactive] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    // Confirmation dialog state
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

    useEffect(() => {
        loadData();
    }, []);

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
            setActionError(err.response?.data || "Erreur de sauvegarde de la fonction.");
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