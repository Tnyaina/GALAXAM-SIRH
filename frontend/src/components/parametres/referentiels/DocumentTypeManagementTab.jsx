import { useState, useEffect } from 'react';
import { Plus, Edit2, FileText, Power } from 'lucide-react';
import documentRhService from '@/api/employe/documentRhService';
import Input, { Textarea, Select, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';

const CATEGORIES = ['Administratif', 'Identité', 'Recrutement', 'Contrat', 'Formation', 'Paie', 'Autre'];

export default function DocumentTypeManagementTab() {
    const [types, setTypes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [modalError, setModalError] = useState('');
    const toast = useToast();

    const [showModal, setShowModal] = useState(false);
    const [selectedType, setSelectedType] = useState(null);
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

    const emptyForm = {
        libelle: '', categorie: 'Administratif', description: '',
        obligatoire: false, extensionAutorisee: 'pdf,jpg,jpeg,png', tailleMaxMo: 10, actif: true
    };
    const [formData, setFormData] = useState({ ...emptyForm });

    const loadData = async () => {
        setLoading(true);
        setError('');
        try {
            const data = await documentRhService.getTypesDocument(false);
            setTypes(data || []);
        } catch (err) {
            console.error('Erreur chargement types documents:', err);
            setError('Impossible de charger les types de documents.');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreate = () => {
        setSelectedType(null);
        setFormData({ ...emptyForm });
        setModalError('');
        setShowModal(true);
    };

    const handleOpenEdit = (t) => {
        setSelectedType(t);
        setFormData({
            libelle: t.libelle || '',
            categorie: t.categorie || 'Administratif',
            description: t.description || '',
            obligatoire: t.obligatoire !== undefined ? t.obligatoire : false,
            extensionAutorisee: t.extensionAutorisee || 'pdf,jpg,jpeg,png',
            tailleMaxMo: t.tailleMaxMo || 10,
            actif: t.actif !== undefined ? t.actif : true
        });
        setModalError('');
        setShowModal(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError('');
        if (!formData.libelle.trim()) {
            setModalError('Le libellé est obligatoire.');
            return;
        }

        setSubmitting(true);
        try {
            if (selectedType) {
                await documentRhService.updateTypeDocument(selectedType.idTypeDocument, formData);
                toast({ title: 'Succès', message: `Type de document "${formData.libelle}" modifié avec succès.`, type: 'success' });
            } else {
                await documentRhService.createTypeDocument(formData);
                toast({ title: 'Succès', message: `Nouveau type de document "${formData.libelle}" créé.`, type: 'success' });
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            console.error('Erreur enregistrement type document:', err);
            setModalError(err.response?.data || "Erreur lors de l'enregistrement.");
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleActif = (t) => {
        const isDeactivating = t.actif;
        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le type de document "${t.libelle}" ?` : `Réactiver le type de document "${t.libelle}" ?`,
            message: isDeactivating
                ? `Ce type de document ne sera plus proposé lors du dépôt de nouveaux justificatifs RH. Les documents existants restent conservés.`
                : `Ce type de document redeviendra disponible pour les nouveaux téléversements.`,
            confirmLabel: isDeactivating ? "Désactiver le type" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await documentRhService.deleteTypeDocument(t.idTypeDocument);
                        toast({ title: 'Succès', message: `Type "${t.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await documentRhService.updateTypeDocument(t.idTypeDocument, { ...t, actif: true });
                        toast({ title: 'Succès', message: `Type "${t.libelle}" réactivé.`, type: 'success' });
                    }
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    console.error('Erreur changement statut:', err);
                    setError('Erreur lors du changement de statut.');
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm gap-4">
                <div>
                    <h2 className="text-base font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <FileText size={18} className="text-[var(--color-brand-500)]" />
                        Référentiel des Types de Documents RH
                    </h2>
                    <p className="text-xs text-[var(--color-fg-muted)]">Définissez la nomenclature des pièces justificatives exigées ou acceptées</p>
                </div>
                <Button
                    onClick={handleOpenCreate}
                    variant="primary"
                    size="sm"
                    iconLeft={<Plus size={16} />}
                >
                    Nouveau Type
                </Button>
            </div>

            {/* Tableau */}
            {loading ? (
                <div className="p-4">
                    <SkeletonTable rows={8} columns={6} />
                </div>
            ) : types.length === 0 ? (
                <div className="p-8">
                    <EmptyState 
                        icon={FileText}
                        title="Aucun type de document configuré"
                        description="Définissez les types de documents nécessaires pour les dossiers de vos collaborateurs."
                        actionLabel="Créer un type de document"
                        onAction={handleOpenCreate}
                    />
                </div>
            ) : (
                <Table>
                    <Thead>
                        <Th>Libellé</Th>
                        <Th>Catégorie</Th>
                        <Th>Obligatoire</Th>
                        <Th>Formats & Taille</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {types.map((t) => (
                            <Tr key={t.idTypeDocument}>
                                <Td>
                                    <p className="font-bold text-[var(--color-fg)]">{t.libelle}</p>
                                    {t.description && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{t.description}</p>}
                                </Td>
                                <Td>
                                    <Badge variant="primary" size="sm">{t.categorie}</Badge>
                                </Td>
                                <Td>
                                    <Badge variant={t.obligatoire ? 'warning' : 'default'} size="sm">
                                        {t.obligatoire ? 'Obligatoire' : 'Facultatif'}
                                    </Badge>
                                </Td>
                                <Td isData muted className="text-xs">
                                    <span>{t.extensionAutorisee || 'Tous'}</span>
                                    <span className="ml-2">({t.tailleMaxMo || 10} Mo max)</span>
                                </Td>
                                <Td>
                                    <Badge variant={t.actif ? 'success' : 'default'} size="sm" dot>
                                        {t.actif ? 'Actif' : 'Inactif'}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => handleOpenEdit(t)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier le type"
                                            aria-label={`Modifier ${t.libelle}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => requestToggleActif(t)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                t.actif
                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                            title={t.actif ? 'Désactiver le type' : 'Réactiver le type'}
                                            aria-label={t.actif ? `Désactiver ${t.libelle}` : `Activer ${t.libelle}`}
                                        >
                                            <Power size={15} />
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}

            {/* Modale d'ajout/modification */}
            <Modal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                title={selectedType ? 'Modifier le Type de Document' : 'Nouveau Type de Document'}
                icon={<FileText size={18} />}
                size="md"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="doc-type-form" variant="primary" size="sm" loading={submitting}>
                            {selectedType ? "Enregistrer" : "Créer le type"}
                        </Button>
                    </>
                }
            >
                <form id="doc-type-form" onSubmit={handleSubmit} className="space-y-4">
                    {modalError && <Alert type="error" message={modalError} />}
                    <Input
                        label="Libellé du document"
                        required
                        value={formData.libelle}
                        onChange={e => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Ex: Extrait de Casier Judiciaire (Bulletin n°3)"
                    />
                    <Select
                        label="Catégorie de rattachement"
                        required
                        value={formData.categorie}
                        onChange={e => setFormData({ ...formData, categorie: e.target.value })}
                    >
                        {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                    </Select>
                    <Textarea
                        label="Description / Consignes (Optionnel)"
                        rows={2}
                        value={formData.description}
                        onChange={e => setFormData({ ...formData, description: e.target.value })}
                        placeholder="Précisions pour les collaborateurs ou RH..."
                    />
                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            label="Extensions autorisées"
                            value={formData.extensionAutorisee}
                            onChange={e => setFormData({ ...formData, extensionAutorisee: e.target.value })}
                            placeholder="pdf,jpg,jpeg,png"
                            hint="Séparées par des virgules"
                        />
                        <Input
                            label="Taille max (Mo)"
                            type="number"
                            value={formData.tailleMaxMo}
                            onChange={e => setFormData({ ...formData, tailleMaxMo: parseInt(e.target.value) || 10 })}
                        />
                    </div>
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-1">
                        <Checkbox
                            label="Document obligatoire lors de l'onboarding / embauche"
                            checked={formData.obligatoire}
                            onChange={e => setFormData({ ...formData, obligatoire: e.target.checked })}
                        />
                        <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">
                            Une alerte de conformité documentaire sera levée tant que ce document n'est pas fourni.
                        </p>
                    </div>
                </form>
            </Modal>

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