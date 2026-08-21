import { useState, useEffect, useMemo } from 'react';
import { Edit2, Sliders, X, Search, Filter, ArrowUpDown } from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/paieUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';

// Catégories correspondant directement aux modules du SIRH et aux graines SQL de la BDD
const DEFAULT_CATEGORIES = [
    { value: 'RH_GENERAL', label: 'Règles RH Générales' },
    { value: 'CONGES', label: 'Congés & Absences' },
    { value: 'POINTAGE', label: 'Pointage & Temps de Travail' },
    { value: 'PAIE', label: 'Paie & Rémunération' }
];

export default function ParametresGlobauxTab({ fixedCategory }) {
    const todayStr = fmtDateInput(new Date());

    const [parametres, setParametres] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // Filtres & Tri
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedCategory, setSelectedCategory] = useState(fixedCategory || 'ALL');
    const [sortBy, setSortBy] = useState('CODE_ASC');

    // Modale Paramètre Global
    const [showModal, setShowModal] = useState(false);
    const [editingParametre, setEditingParametre] = useState(null);
    const [formData, setFormData] = useState({
        codeParametre: '',
        valeur: '',
        description: '',
        categorie: fixedCategory || 'PAIE',
        dateDebut: todayStr,
        dateFin: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const paramData = await paieConfigService.getParametresGlobaux();
            setParametres(paramData || []);
        } catch (err) {
            setError("Erreur lors du chargement des paramètres globaux.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    // Extraction dynamique des catégories existantes sans doublons (harmonisation CONGES -> CONGE)
    const availableCategories = useMemo(() => {
        const categoriesSet = new Set(DEFAULT_CATEGORIES.map(c => c.value));
        parametres.forEach(p => {
            if (p.categorie) {
                const normCat = p.categorie === 'CONGE' ? 'CONGES' : p.categorie;
                categoriesSet.add(normCat);
            }
        });
        return Array.from(categoriesSet).map(catKey => {
            const found = DEFAULT_CATEGORIES.find(c => c.value === catKey);
            return {
                value: catKey,
                label: found ? found.label : catKey
            };
        });
    }, [parametres]);

    // Filtrage et Tri des paramètres
    const filteredAndSortedParametres = useMemo(() => {
        return parametres
            .filter(item => {
                const itemCatNorm = item.categorie === 'CONGE' ? 'CONGES' : (item.categorie || 'RH_GENERAL');

                // Filtre par catégorie
                if (selectedCategory !== 'ALL') {
                    if (itemCatNorm !== selectedCategory) {
                        return false;
                    }
                }
                // Recherche textuelle
                if (searchTerm.trim() !== '') {
                    const term = searchTerm.toLowerCase();
                    const matchCode = (item.codeParametre || '').toLowerCase().includes(term);
                    const matchDesc = (item.description || '').toLowerCase().includes(term);
                    const matchVal = (item.valeur || '').toLowerCase().includes(term);
                    const matchCat = (item.categorie || '').toLowerCase().includes(term);
                    if (!matchCode && !matchDesc && !matchVal && !matchCat) {
                        return false;
                    }
                }
                return true;
            })
            .sort((a, b) => {
                if (sortBy === 'CODE_ASC') {
                    return (a.codeParametre || '').localeCompare(b.codeParametre || '');
                }
                if (sortBy === 'CODE_DESC') {
                    return (b.codeParametre || '').localeCompare(a.codeParametre || '');
                }
                if (sortBy === 'CAT_ASC') {
                    return (a.categorie || 'GENERAL').localeCompare(b.categorie || 'GENERAL');
                }
                if (sortBy === 'DATE_DESC') {
                    return new Date(b.dateDebut || 0) - new Date(a.dateDebut || 0);
                }
                return 0;
            });
    }, [parametres, selectedCategory, searchTerm, sortBy]);

    // Sauvegarde (Modification uniquement)
    const handleSave = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);
        try {
            if (editingParametre) {
                await paieConfigService.updateParametreGlobal(editingParametre.idParametre, formData);
                setActionSuccess(`Paramètre global "${formData.codeParametre}" modifié avec succès.`);
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur lors de la sauvegarde du paramètre.");
        }
    };

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={6} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Feedback messages */}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}
            {error && <Alert type="error" message={error} />}

            {/* En-tête & Barre de Contrôles / Filtres */}
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
                    <div>
                        <h4 className="font-bold text-[var(--color-fg)] text-lg flex items-center gap-2">
                            <Sliders size={20} className="text-[var(--color-brand-500)]" />
                            Paramètres Globaux du SIRH & de la Paie
                        </h4>
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            Constantes réglementaires, taux légaux, plafonds CNaPS, diviseurs horaires et variables du moteur.
                        </p>
                    </div>
                    {/* Le bouton "Nouveau" a été retiré, car la création de paramètres globaux 
                        nécessite une intervention dans le code source pour être utilisée. */}
                </div>

                {/* Barre de Recherche, Filtre par Catégorie et Tri */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4 pt-2">
                    {/* Input Recherche */}
                    <div className="relative">
                        <Input
                            placeholder="Rechercher par code, description..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            iconLeft={<Search size={16} className="text-[var(--color-fg-muted)]" />}
                        />
                    </div>

                    {/* Select Filtre par Catégorie */}
                    {!fixedCategory && (
                        <div className="flex items-center gap-2">
                            <Filter size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                            <Select
                                value={selectedCategory}
                                onChange={(e) => setSelectedCategory(e.target.value)}
                            >
                                <option value="ALL">Toutes les catégories</option>
                                {availableCategories.map((c) => (
                                    <option key={c.value} value={c.value}>{c.label}</option>
                                ))}
                            </Select>
                        </div>
                    )}

                    {/* Select Tri */}
                    <div className="flex items-center gap-2">
                        <ArrowUpDown size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                        <Select
                            value={sortBy}
                            onChange={(e) => setSortBy(e.target.value)}
                        >
                            <option value="CODE_ASC">Code Paramètre (A → Z)</option>
                            <option value="CODE_DESC">Code Paramètre (Z → A)</option>
                            <option value="DATE_DESC">Date Début (Plus récents)</option>
                        </Select>
                    </div>
                </div>

                {/* Tableau de présentation */}
                <Table>
                    <Thead>
                        <Th>Code Paramètre</Th>
                        <Th>Catégorie</Th>
                        <Th>Valeur</Th>
                        <Th>Description</Th>
                        <Th>Date Prise d'effet</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {filteredAndSortedParametres.length === 0 ? (
                            <Tr>
                                <Td colSpan={6} className="p-0">
                                    <EmptyState title="Aucun paramètre" description="Aucun paramètre global ne correspond à vos critères de recherche." />
                                </Td>
                            </Tr>
                        ) : (
                            filteredAndSortedParametres.map((p) => {
                                const catLabel = availableCategories.find(c => c.value === p.categorie)?.label || p.categorie || 'GÉNÉRAL';
                                return (
                                    <Tr key={p.idParametre}>
                                        <Td isData className="font-bold text-[var(--color-brand-500)]">
                                            {p.codeParametre}
                                        </Td>
                                        <Td isData>
                                            <span className="inline-flex items-center px-2 py-0.5 text-xs font-semibold rounded-none bg-[var(--color-bg-subtle)] text-[var(--color-brand-500)] border border-[var(--color-border-subtle)]">
                                                {catLabel}
                                            </span>
                                        </Td>
                                        <Td isData className="font-bold text-[var(--color-success-500)]">
                                            {p.valeur}
                                        </Td>
                                        <Td muted className="text-xs">
                                            {p.description || '-'}
                                        </Td>
                                        <Td muted className="text-xs">
                                            {fmtDateInput(p.dateDebut) || '-'}
                                        </Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => {
                                                    setEditingParametre(p);
                                                    setFormData({
                                                        codeParametre: p.codeParametre,
                                                        valeur: p.valeur,
                                                        description: p.description || '',
                                                        categorie: p.categorie || 'PAIE',
                                                        dateDebut: fmtDateInput(p.dateDebut) || todayStr,
                                                        dateFin: fmtDateInput(p.dateFin) || ''
                                                    });
                                                    setActionError(null);
                                                    setShowModal(true);
                                                }}
                                                className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                                title="Modifier le paramètre"
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                        </Td>
                                    </Tr>
                                );
                            })
                        )}
                    </Tbody>
                </Table>
            </div>

            {/* Modale d'Édition */}
            {showModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-lg w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Sliders size={18} className="text-[var(--color-brand-500)]" />
                                Modifier la Valeur du Paramètre
                            </h3>
                            <button type="button" onClick={() => setShowModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSave} className="p-6 space-y-5">
                            {actionError && <Alert type="error" message={actionError} />}

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <Input
                                    label="Code Paramètre"
                                    value={formData.codeParametre}
                                    readOnly
                                    disabled
                                    className="bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]"
                                />

                                <Input
                                    label="Valeur *"
                                    required
                                    value={formData.valeur}
                                    onChange={(e) => setFormData({ ...formData, valeur: e.target.value })}
                                    placeholder="Ex: 262000.00"
                                />

                                <div className="col-span-1 md:col-span-2">
                                    <Textarea
                                        label="Description"
                                        value={formData.description}
                                        readOnly
                                        disabled
                                        rows={2}
                                        className="bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]"
                                    />
                                </div>
                            </div>

                            <div className="grid grid-cols-2 gap-4">
                                <Input
                                    label="Date Début (Prise d'effet)"
                                    type="date"
                                    required
                                    value={fmtDateInput(formData.dateDebut)}
                                    onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                                />
                                <Input
                                    label="Date Fin (Optionnel)"
                                    type="date"
                                    value={fmtDateInput(formData.dateFin)}
                                    onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                                />
                            </div>

                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm">
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
