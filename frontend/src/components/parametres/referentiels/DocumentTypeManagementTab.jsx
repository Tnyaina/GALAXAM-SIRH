import { useState, useEffect } from 'react';
import { Plus, Edit2, FileText, X } from 'lucide-react';
import documentRhService from '@/api/employe/documentRhService';
import Input, { Textarea, Select, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';

const CATEGORIES = ['Administratif', 'Identité', 'Recrutement', 'Contrat', 'Formation', 'Paie', 'Autre'];

export default function DocumentTypeManagementTab() {
    const [types, setTypes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const [showModal, setShowModal] = useState(false);
    const [selectedType, setSelectedType] = useState(null);
    const [submitting, setSubmitting] = useState(false);

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
        setError('');
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
        setError('');
        setShowModal(true);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        if (!formData.libelle.trim()) {
            setError('Le libellé est obligatoire.');
            return;
        }

        setSubmitting(true);
        try {
            if (selectedType) {
                await documentRhService.updateTypeDocument(selectedType.idTypeDocument, formData);
                setActionSuccess(`Type de document "${formData.libelle}" modifié avec succès.`);
            } else {
                await documentRhService.createTypeDocument(formData);
                setActionSuccess(`Nouveau type de document "${formData.libelle}" créé.`);
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            console.error('Erreur enregistrement type document:', err);
            setError(err.response?.data || "Erreur lors de l'enregistrement.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleToggleActif = async (t) => {
        try {
            if (t.actif) {
                await documentRhService.deleteTypeDocument(t.idTypeDocument);
                setActionSuccess(`Type "${t.libelle}" désactivé.`);
            } else {
                await documentRhService.updateTypeDocument(t.idTypeDocument, { ...t, actif: true });
                setActionSuccess(`Type "${t.libelle}" réactivé.`);
            }
            loadData();
        } catch (err) {
            console.error('Erreur changement statut:', err);
            setError('Erreur lors du changement de statut.');
        }
    };

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}
            {error && !showModal && <Alert type="error" message={error} />}

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
                                    <div className="flex justify-end items-center gap-2">
                                        <button
                                            onClick={() => handleOpenEdit}
                                            onClickCapture={() => handleOpenEdit(t)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={16} />
                                        </button>
                                        <button
                                            onClick={() => handleToggleActif(t)}
                                            className={`px-2 py-1 text-xs font-bold rounded-none  cursor-pointer uppercase ${
                                                t.actif ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10' : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                        >
                                            {t.actif ? 'Désactiver' : 'Activer'}
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}

            {/* Modale d'ajout/modification */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <FileText size={18} className="text-[var(--color-brand-500)]" />
                                {selectedType ? 'Modifier le Type de Document' : 'Nouveau Type de Document'}
                            </h3>
                            <button onClick={() => setShowModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            {error && (
                                <Alert type="error" message={error} />
                            )}
                            <Input
                                label="Libellé"
                                required
                                value={formData.libelle}
                                onChange={e => setFormData({ ...formData, libelle: e.target.value })}
                                placeholder="Ex: Extrait de Casier Judiciaire"
                            />
                            <Select
                                label="Catégorie"
                                required
                                value={formData.categorie}
                                onChange={e => setFormData({ ...formData, categorie: e.target.value })}
                            >
                                {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
                            </Select>
                            <Textarea
                                label="Description"
                                rows={2}
                                value={formData.description}
                                onChange={e => setFormData({ ...formData, description: e.target.value })}
                                placeholder="Précisions sur l'utilité du document..."
                            />
                            <Input
                                label="Extensions autorisées"
                                value={formData.extensionAutorisee}
                                onChange={e => setFormData({ ...formData, extensionAutorisee: e.target.value })}
                                placeholder="pdf,jpg,jpeg,png"
                                hint="Séparées par des virgules"
                            />
                            <Input
                                label="Taille maximale (Mo)"
                                type="number"
                                value={formData.tailleMaxMo}
                                onChange={e => setFormData({ ...formData, tailleMaxMo: parseInt(e.target.value) || 10 })}
                            />
                            <div className="pt-2">
                                <Checkbox
                                    label="Document obligatoire à l'embauche"
                                    checked={formData.obligatoire}
                                    onChange={e => setFormData({ ...formData, obligatoire: e.target.checked })}
                                />
                            </div>
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm" loading={submitting}>
                                    Enregistrer
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}