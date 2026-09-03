import { useState, useEffect, useMemo } from 'react';
import {
    Sliders, Search, LayoutGrid, List,
    Edit2, Plus, Calendar, ShieldCheck
} from 'lucide-react';
import paieConfigService from '@/api/admin/paieConfigService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import { Card, CardHeader, CardBody, CardFooter } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDateInput } from '@/utils/paieUtils';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { useToast } from '@/components/ui/feedback/Toast';

function getStatut(param) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    const debut = param.dateDebut ? new Date(param.dateDebut) : null;
    if (debut) debut.setHours(0, 0, 0, 0);

    const fin = param.dateFin ? new Date(param.dateFin) : null;
    if (fin) fin.setHours(0, 0, 0, 0);

    if (!debut) return 'EN_VIGUEUR';
    if (debut > today) return 'PROGRAMME';
    if (!fin || fin >= today) return 'EN_VIGUEUR';
    return 'ARCHIVE';
}

function StatutBadge({ statut }) {
    const styles = {
        EN_VIGUEUR: 'bg-[var(--color-success-500)]/12 text-[var(--color-success-600)] border-[var(--color-success-500)]/25',
        PROGRAMME:  'bg-[var(--color-brand-500)]/12 text-[var(--color-brand-500)] border-[var(--color-brand-500)]/25',
        ARCHIVE:    'bg-[var(--color-fg-muted)]/10 text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
    };
    const labels = {
        EN_VIGUEUR: 'En vigueur',
        PROGRAMME:  'Programme',
        ARCHIVE:    'Archive'
    };
    return (
        <span className={`inline-flex items-center px-2 py-0.5 text-[10px] font-bold border rounded-none uppercase tracking-wide ${styles[statut] || styles.ARCHIVE}`}>
            {labels[statut] || statut}
        </span>
    );
}

function formatPeriodeValidite(dateDebut, dateFin) {
    const fmt = (d) => d ? new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' }) : null;
    const dStr = fmt(dateDebut) || 'Origine';
    const fStr = fmt(dateFin) || 'En vigueur';
    return `${dStr} - ${fStr}`;
}

function formatValeurAffichage(code, valeur) {
    if (valeur == null || valeur === '') return '-';
    const c = (code || '').toUpperCase();
    const v = String(valeur).trim();

    if (v.includes(':')) {
        return `${v}`;
    }
    if (c.includes('MONTANT') || c.includes('REDUCTION') || c.includes('SME') || c.includes('INDEMNITE')) {
        const num = Number(v);
        if (!isNaN(num)) {
            return `${num.toLocaleString('fr-MG')} Ar`;
        }
    }
    if (c.includes('HEURE') || c.includes('SEUIL_HS') || c.includes('DUREE_HEBDO')) {
        return `${v} h`;
    }
    if (c.includes('MIN')) {
        return `${v} min`;
    }
    if (c.includes('JOURS') || c.includes('JOUR')) {
        return `${v} j`;
    }
    if (c.includes('MOIS')) {
        return `${v} mois`;
    }
    if (c.includes('ANS') || c.includes('ANNEES')) {
        return `${v} ans`;
    }
    return v;
}

export default function ParametresGlobauxTab({ fixedCategory }) {
    const todayStr = fmtDateInput(new Date());

    const [parametres, setParametres] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();

    // Filtres, vue & tri
    const [searchTerm, setSearchTerm] = useState('');
    const [selectedCategory, setSelectedCategory] = useState(fixedCategory || 'ALL');
    const [selectedStatut, setSelectedStatut] = useState('ALL');
    const [viewMode, setViewMode] = useState('CARDS'); // 'CARDS' ou 'TABLE'
    const [sortBy, setSortBy] = useState('CODE_ASC');

    // Modale d'édition
    const [showModal, setShowModal] = useState(false);
    const [editingParametre, setEditingParametre] = useState(null);
    const [isNouvelleVersion, setIsNouvelleVersion] = useState(false);
    const [formData, setFormData] = useState({
        codeParametre: '',
        valeur: '',
        description: '',
        categorie: fixedCategory || 'POINTAGE',
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

    // Catégories dynamiques extraites directement des données de la base
    const availableCategories = useMemo(() => {
        const catMap = new Map();
        parametres.forEach(p => {
            if (p.categorie) {
                const rawCat = p.categorie.trim();
                if (!catMap.has(rawCat)) {
                    catMap.set(rawCat, rawCat);
                }
            }
        });
        return Array.from(catMap.keys()).map(cat => ({
            value: cat,
            label: cat
        }));
    }, [parametres]);

function matchCategory(itemCat, targetCat) {
    if (!itemCat || !targetCat) return false;
    const norm = (c) => c.trim().toUpperCase().replace(/S$/, '');
    return norm(itemCat) === norm(targetCat);
}

    // Filtrage et Tri
    const filteredParametres = useMemo(() => {
        return parametres.filter(item => {
            // Filtre catégorie
            if (fixedCategory) {
                if (!matchCategory(item.categorie, fixedCategory)) return false;
            } else if (selectedCategory !== 'ALL') {
                if (!matchCategory(item.categorie, selectedCategory)) return false;
            }

            // Filtre statut temporel
            if (selectedStatut !== 'ALL') {
                const st = getStatut(item);
                if (st !== selectedStatut) return false;
            }

            // Recherche textuelle
            if (searchTerm.trim() !== '') {
                const term = searchTerm.toLowerCase();
                const matchCode = (item.codeParametre || '').toLowerCase().includes(term);
                const matchDesc = (item.description || '').toLowerCase().includes(term);
                const matchVal = (item.valeur || '').toLowerCase().includes(term);
                const matchCat = (item.categorie || '').toLowerCase().includes(term);
                if (!matchCode && !matchDesc && !matchVal && !matchCat) return false;
            }

            return true;
        }).sort((a, b) => {
            if (sortBy === 'CODE_ASC') {
                return (a.codeParametre || '').localeCompare(b.codeParametre || '');
            }
            if (sortBy === 'CODE_DESC') {
                return (b.codeParametre || '').localeCompare(a.codeParametre || '');
            }
            if (sortBy === 'CAT_ASC') {
                return (a.categorie || '').localeCompare(b.categorie || '');
            }
            if (sortBy === 'DATE_DESC') {
                return new Date(b.dateDebut || 0) - new Date(a.dateDebut || 0);
            }
            return 0;
        });
    }, [parametres, fixedCategory, selectedCategory, selectedStatut, searchTerm, sortBy]);

    // Métriques de synthèse
    const metrics = useMemo(() => {
        const relevant = fixedCategory 
            ? parametres.filter(p => matchCategory(p.categorie, fixedCategory)) 
            : parametres;
        let vig = 0;
        let prog = 0;
        let arch = 0;
        relevant.forEach(p => {
            const s = getStatut(p);
            if (s === 'EN_VIGUEUR') vig++;
            else if (s === 'PROGRAMME') prog++;
            else if (s === 'ARCHIVE') arch++;
        });
        return { total: relevant.length, enVigueur: vig, programmes: prog, archives: arch };
    }, [parametres, fixedCategory]);

    // Paramètres groupés par catégorie pour la vue Cartes
    const groupedParametres = useMemo(() => {
        const groups = new Map();
        filteredParametres.forEach(p => {
            const cat = p.categorie || 'GENERAL';
            if (!groups.has(cat)) {
                groups.set(cat, []);
            }
            groups.get(cat).push(p);
        });
        return groups;
    }, [filteredParametres]);

    const handleOpenEdit = (p, modeNouvelleVersion = false) => {
        setEditingParametre(p);
        setIsNouvelleVersion(modeNouvelleVersion);
        setFormData({
            codeParametre: p.codeParametre,
            valeur: p.valeur,
            description: p.description || '',
            categorie: p.categorie || (fixedCategory || 'POINTAGE'),
            dateDebut: modeNouvelleVersion ? todayStr : (fmtDateInput(p.dateDebut) || todayStr),
            dateFin: modeNouvelleVersion ? '' : (fmtDateInput(p.dateFin) || '')
        });
        setModalError(null);
        setShowModal(true);
    };

    const handleSave = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);
        setSubmitting(true);
        try {
            if (isNouvelleVersion) {
                // Création d'une nouvelle version avec date_debut distincte
                await paieConfigService.createParametreGlobal(formData);
                toast({
                    title: 'Succès',
                    message: `Nouvelle version programmée pour ${formData.codeParametre} à compter du ${formData.dateDebut}.`,
                    type: 'success'
                });
            } else if (editingParametre) {
                // Modification de la version existante
                await paieConfigService.updateParametreGlobal(editingParametre.idParametre, formData);
                toast({
                    title: 'Succès',
                    message: `Paramètre ${formData.codeParametre} mis à jour avec succès.`,
                    type: 'success'
                });
            }
            setShowModal(false);
            loadData();
        } catch (err) {
            setModalError(err.response?.data || "Erreur lors de l'enregistrement du paramètre.");
        } finally {
            setSubmitting(false);
        }
    };

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {error && <Alert type="error" message={error} />}

            {/* Barre de synthèse & KPIs rapides */}
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider">Total Parametres</div>
                    <div className="text-xl font-bold text-[var(--color-fg)] mt-0.5">{metrics.total}</div>
                </div>
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <div className="text-[10px] text-[var(--color-success-600)] uppercase tracking-wider">En Vigueur</div>
                    <div className="text-xl font-bold text-[var(--color-success-600)] mt-0.5">{metrics.enVigueur}</div>
                </div>
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <div className="text-[10px] text-[var(--color-brand-500)] uppercase tracking-wider">Programmes</div>
                    <div className="text-xl font-bold text-[var(--color-brand-500)] mt-0.5">{metrics.programmes}</div>
                </div>
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider">Archives</div>
                    <div className="text-xl font-bold text-[var(--color-fg-muted)] mt-0.5">{metrics.archives}</div>
                </div>
            </div>

            {/* Barre d'actions & Filtres */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3">
                <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3">
                    <div className="relative flex-1">
                        <Input
                            placeholder="Rechercher un code, une description..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            iconLeft={<Search size={15} className="text-[var(--color-fg-muted)]" />}
                        />
                    </div>

                    <div className="flex flex-wrap items-center gap-2">
                        {!fixedCategory && (
                            <Select
                                value={selectedCategory}
                                onChange={(e) => setSelectedCategory(e.target.value)}
                                className="w-44 text-xs"
                            >
                                <option value="ALL">Toutes categories</option>
                                {availableCategories.map(c => (
                                    <option key={c.value} value={c.value}>{c.label}</option>
                                ))}
                            </Select>
                        )}

                        <Select
                            value={selectedStatut}
                            onChange={(e) => setSelectedStatut(e.target.value)}
                            className="w-36 text-xs"
                        >
                            <option value="ALL">Tous statuts</option>
                            <option value="EN_VIGUEUR">En vigueur</option>
                            <option value="PROGRAMME">Programme</option>
                            <option value="ARCHIVE">Archive</option>
                        </Select>

                        <Select
                            value={sortBy}
                            onChange={(e) => setSortBy(e.target.value)}
                            className="w-44 text-xs"
                        >
                            <option value="CODE_ASC">Code (A - Z)</option>
                            <option value="CODE_DESC">Code (Z - A)</option>
                            <option value="DATE_DESC">Date debut (Recent)</option>
                        </Select>

                        {/* Bascule vue Cartes / vue Tableau */}
                        <div className="flex border border-[var(--color-border-subtle)] overflow-hidden">
                            <button
                                type="button"
                                onClick={() => setViewMode('CARDS')}
                                className={`p-2 transition-colors cursor-pointer ${
                                    viewMode === 'CARDS'
                                        ? 'bg-[var(--color-brand-500)] text-white'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Vue Cartes Metier"
                                aria-label="Vue Cartes Metier"
                            >
                                <LayoutGrid size={15} />
                            </button>
                            <button
                                type="button"
                                onClick={() => setViewMode('TABLE')}
                                className={`p-2 transition-colors cursor-pointer ${
                                    viewMode === 'TABLE'
                                        ? 'bg-[var(--color-brand-500)] text-white'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                                title="Vue Tableau Detaille"
                                aria-label="Vue Tableau Detaille"
                            >
                                <List size={15} />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Vue Cartes Métier (par défaut) */}
            {viewMode === 'CARDS' && (
                <div className="space-y-6">
                    {filteredParametres.length === 0 ? (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8">
                            <EmptyState
                                icon={Sliders}
                                title="Aucun parametre trouve"
                                description="Aucun parametre ne correspond aux criteres de recherche selectionnes."
                            />
                        </div>
                    ) : (
                        Array.from(groupedParametres.entries()).map(([categorieKey, items]) => (
                            <div key={categorieKey} className="space-y-3">
                                <div className="flex items-center gap-2 pb-1.5 border-b border-[var(--color-border-subtle)]">
                                    <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-500)]">
                                        {categorieKey}
                                    </span>
                                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                                        ({items.length} parametres)
                                    </span>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3.5">
                                    {items.map(param => {
                                        const statut = getStatut(param);
                                        const valeurAffichee = formatValeurAffichage(param.codeParametre, param.valeur);
                                        return (
                                            <Card key={param.idParametre} bordered hover className="flex flex-col justify-between">
                                                <CardHeader className="py-2.5 px-4 flex items-center justify-between gap-2">
                                                    <span className="font-mono text-[11px] sm:text-xs font-bold text-[var(--color-fg)] break-words leading-tight" title={param.codeParametre}>
                                                        {param.codeParametre}
                                                    </span>
                                                    <div className="shrink-0">
                                                        <StatutBadge statut={statut} />
                                                    </div>
                                                </CardHeader>

                                                <CardBody className="py-3 px-4 space-y-2 flex-1">
                                                    <div>
                                                        <div className="text-[10px] text-[var(--color-fg-muted)] uppercase tracking-wider">
                                                            Valeur Active
                                                        </div>
                                                        <div className="text-xl font-black text-[var(--color-fg)] mt-0.5 tracking-tight">
                                                            {valeurAffichee}
                                                        </div>
                                                    </div>

                                                    <div className="text-xs text-[var(--color-fg-muted)] line-clamp-2 leading-relaxed">
                                                        {param.description || '-'}
                                                    </div>

                                                    <div className="pt-2 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                                                        <span className="flex items-center gap-1">
                                                            <Calendar size={12} />
                                                            {formatPeriodeValidite(param.dateDebut, param.dateFin)}
                                                        </span>
                                                    </div>
                                                </CardBody>

                                                <CardFooter className="py-2 px-4 flex items-center justify-end gap-2 bg-[var(--color-bg-subtle)]">
                                                    <Button
                                                        variant="ghost"
                                                        size="xs"
                                                        onClick={() => handleOpenEdit(param, true)}
                                                        title="Programmer une nouvelle version a date d'effet future"
                                                    >
                                                        <Plus size={13} className="mr-1" />
                                                        Versionner
                                                    </Button>
                                                    <Button
                                                        variant="secondary"
                                                        size="xs"
                                                        onClick={() => handleOpenEdit(param, false)}
                                                    >
                                                        <Edit2 size={13} className="mr-1" />
                                                        Modifier
                                                    </Button>
                                                </CardFooter>
                                            </Card>
                                        );
                                    })}
                                </div>
                            </div>
                        ))
                    )}
                </div>
            )}

            {/* Vue Tableau Détaillé */}
            {viewMode === 'TABLE' && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm">
                    <Table>
                        <Thead>
                            <Th>Code Parametre</Th>
                            <Th>Categorie</Th>
                            <Th>Valeur</Th>
                            <Th>Description</Th>
                            <Th>Periode de Validite</Th>
                            <Th>Statut</Th>
                            <Th className="text-right">Actions</Th>
                        </Thead>
                        <Tbody>
                            {filteredParametres.length === 0 ? (
                                <Tr>
                                    <Td colSpan={7} className="p-0">
                                        <EmptyState
                                            icon={Sliders}
                                            title="Aucun parametre trouve"
                                            description="Aucun parametre ne correspond aux criteres de recherche."
                                        />
                                    </Td>
                                </Tr>
                            ) : (
                                filteredParametres.map((p) => {
                                    const statut = getStatut(p);
                                    const valeurAffichee = formatValeurAffichage(p.codeParametre, p.valeur);
                                    return (
                                        <Tr key={p.idParametre}>
                                            <Td isData className="font-mono text-xs font-bold text-[var(--color-brand-500)]">
                                                {p.codeParametre}
                                            </Td>
                                            <Td isData>
                                                <Badge variant="primary" size="sm">
                                                    {p.categorie || 'GENERAL'}
                                                </Badge>
                                            </Td>
                                            <Td isData className="font-bold text-[var(--color-fg)]">
                                                {valeurAffichee}
                                            </Td>
                                            <Td muted className="text-xs max-w-xs truncate" title={p.description}>
                                                {p.description || '-'}
                                            </Td>
                                            <Td isData muted className="text-xs">
                                                {formatPeriodeValidite(p.dateDebut, p.dateFin)}
                                            </Td>
                                            <Td isData>
                                                <StatutBadge statut={statut} />
                                            </Td>
                                            <Td className="text-right">
                                                <div className="inline-flex items-center gap-1.5">
                                                    <button
                                                        onClick={() => handleOpenEdit(p, true)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                                                        title="Programmer une nouvelle version a date future"
                                                        aria-label={`Programmer nouvelle version pour ${p.codeParametre}`}
                                                    >
                                                        <Plus size={15} />
                                                    </button>
                                                    <button
                                                        onClick={() => handleOpenEdit(p, false)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] cursor-pointer"
                                                        title="Modifier cette version"
                                                        aria-label={`Modifier ${p.codeParametre}`}
                                                    >
                                                        <Edit2 size={15} />
                                                    </button>
                                                </div>
                                            </Td>
                                        </Tr>
                                    );
                                })
                            )}
                        </Tbody>
                    </Table>
                </div>
            )}

            {/* Modale d'Édition & Versionnement */}
            <Modal
                isOpen={showModal}
                onClose={() => setShowModal(false)}
                title={isNouvelleVersion ? "Programmer une Nouvelle Version" : "Modifier le Parametre Global"}
                icon={<Sliders size={18} />}
                size="xl"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="param-global-form" variant="primary" size="sm" loading={submitting}>
                            {isNouvelleVersion ? "Enregistrer Nouvelle Version" : "Enregistrer Modifications"}
                        </Button>
                    </>
                }
            >
                <form id="param-global-form" onSubmit={handleSave} className="space-y-4">
                    {modalError && <Alert type="error" message={modalError} />}

                    {/* Encadré d'information sur la portée comptable */}
                    <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-xs text-[var(--color-fg-muted)] space-y-1">
                        <div className="font-bold text-[var(--color-fg)] flex items-center gap-1.5">
                            <ShieldCheck size={14} className="text-[var(--color-brand-500)]" />
                            Regle d'Integrite Comptable & Historique
                        </div>
                        <div>
                            Les periodes de paie et de pointage validees ou cloturees sont scellees de facon immuable. La modification s'applique uniquement a la periode en cours et aux periodes futures.
                        </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
                        <Input
                            label="Code Parametre"
                            value={formData.codeParametre}
                            readOnly
                            disabled
                        />

                        <Input
                            label="Categorie"
                            value={formData.categorie}
                            readOnly
                            disabled
                        />

                        <div className="col-span-1 md:col-span-2">
                            <Input
                                label="Valeur du Parametre"
                                required
                                value={formData.valeur}
                                onChange={(e) => setFormData({ ...formData, valeur: e.target.value })}
                                placeholder="Ex: 20:00 ou 4000"
                            />
                        </div>

                        <div className="col-span-1 md:col-span-2">
                            <Textarea
                                label="Description du role metier"
                                value={formData.description}
                                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                                rows={2}
                            />
                        </div>

                        <Input
                            label="Date Debut (Prise d'effet)"
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
                </form>
            </Modal>
        </div>
    );
}
