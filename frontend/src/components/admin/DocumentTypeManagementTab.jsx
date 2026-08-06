import { useState, useEffect } from 'react';
import { Plus, Edit2, CheckCircle, XCircle, ShieldAlert, FileText, AlertCircle } from 'lucide-react';
import documentRhService from '@/api/documentRhService';
import Input, { Textarea, Select, Checkbox } from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';

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

    useEffect(() => {
        loadData();
    }, []);

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
            {/* Action Success / Error */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && !showModal && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm gap-4">
                <div>
                    <h2 className="text-base font-bold text-ink-900 dark:text-white flex items-center gap-2">
                        <FileText size={18} className="text-brand-500" />
                        Référentiel des Types de Documents RH
                    </h2>
                    <p className="text-xs text-ink-400">Définissez la nomenclature des pièces justificatives exigées ou acceptées</p>
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
                <div className="flex justify-center items-center py-12">
                    <Loading size="lg" label="Chargement du référentiel..." />
                </div>
            ) : types.length === 0 ? (
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-12 text-center">
                    <FileText size={40} className="mx-auto text-ink-300 mb-3" />
                    <p className="text-sm font-semibold text-ink-800 dark:text-white">Aucun type de document configuré</p>
                </div>
            ) : (
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                    <th className="px-6 py-4">Libellé</th>
                                    <th className="px-6 py-4">Catégorie</th>
                                    <th className="px-6 py-4">Obligatoire</th>
                                    <th className="px-6 py-4">Formats & Taille</th>
                                    <th className="px-6 py-4">Statut</th>
                                    <th className="px-6 py-4 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {types.map((t) => (
                                    <tr key={t.idTypeDocument} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-6 py-4">
                                            <p className="font-semibold text-ink-900 dark:text-white">{t.libelle}</p>
                                            {t.description && <p className="text-xs text-ink-400 mt-0.5">{t.description}</p>}
                                        </td>
                                        <td className="px-6 py-4">
                                            <Badge variant="primary" size="sm">{t.categorie}</Badge>
                                        </td>
                                        <td className="px-6 py-4">
                                            {t.obligatoire ? (
                                                <Badge variant="warning" size="sm">Obligatoire</Badge>
                                            ) : (
                                                <Badge variant="default" size="sm">Facultatif</Badge>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-xs font-mono">
                                            <span>{t.extensionAutorisee || 'Tous'}</span>
                                            <span className="text-ink-400 ml-2">({t.tailleMaxMo || 10} Mo max)</span>
                                        </td>
                                        <td className="px-6 py-4">
                                            {t.actif ? (
                                                <span className="inline-flex items-center gap-1.5 text-success-500 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-success-500"></span>
                                                    Actif
                                                </span>
                                            ) : (
                                                <span className="inline-flex items-center gap-1.5 text-ink-400 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-ink-400"></span>
                                                    Inactif
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleOpenEdit(t)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                                <button
                                                    onClick={() => handleToggleActif(t)}
                                                    className={`px-2 py-1 text-xs font-bold rounded-sm transition-all cursor-pointer ${
                                                        t.actif ? 'text-danger-500 hover:bg-danger-500/10' : 'text-success-500 hover:bg-success-500/10'
                                                    }`}
                                                >
                                                    {t.actif ? 'Désactiver' : 'Activer'}
                                                </button>
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

            {/* Modale d'ajout/modification */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedType ? 'Modifier le Type de Document' : 'Nouveau Type de Document'}
                        </div>
                        <form onSubmit={handleSubmit}>
                            <div className="p-6 space-y-4">
                                {error && (
                                    <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {error}
                                    </div>
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
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
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
