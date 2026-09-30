import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { ArrowDownRight, ArrowLeft,ArrowUpRight, Calculator, Info, Plus, Power, RotateCcw, Search, Sliders, Tag, } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';
import paieService from '@/features/payroll/api/paieService';

const emptyType = {
    code: '',
    libelle: '',
    sens: 'GAIN',
    categorie: 'PRIME',
    idRubrique: '',
    description: '',
    estProratise: false,
    estPlafonneDixieme: false,
    estActif: true,
    ordreAffichage: 100,
};

export default function VariableTypesConfig() {
    const navigate = useNavigate();
    const location = useLocation();
    const toast = useToast();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE') || hasPermission('ROLE_ADMIN');
    const returnTo = location.state?.from?.startsWith('/paie/') ? location.state.from : null;

    // Références pour la gestion du focus
    const codeInputRef = useRef(null);
    const libelleInputRef = useRef(null);

    const [types, setTypes] = useState([]);
    const [rubriques, setRubriques] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [isCreatingNew, setIsCreatingNew] = useState(false);
    const [form, setForm] = useState(emptyType);

    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [formError, setFormError] = useState('');
    const [notice, setNotice] = useState('');

    // Filtres du volet gauche
    const [query, setQuery] = useState('');
    const [filterSens, setFilterSens] = useState('ALL'); // 'ALL' | 'GAIN' | 'RETENUE'

    // Chargement initial des données sans dépendance sur selectedId
    const load = useCallback(async (preferredSelectId = null) => {
        setLoading(true);
        setError('');
        try {
            const catalogue = await paieService.getCatalogueVariables();
            const loadedTypes = Array.isArray(catalogue?.types) ? catalogue.types : [];
            const loadedRubriques = Array.isArray(catalogue?.rubriques) ? catalogue.rubriques : [];
            setTypes(loadedTypes);
            setRubriques(loadedRubriques);

            if (loadedTypes.length > 0) {
                const target = preferredSelectId
                    ? loadedTypes.find(t => t.id === preferredSelectId) || loadedTypes[0]
                    : loadedTypes[0];

                setSelectedId(target.id);
                setIsCreatingNew(false);
                setForm({
                    code: target.code || '',
                    libelle: target.libelle || '',
                    sens: target.sens || 'GAIN',
                    categorie: target.categorie || 'PRIME',
                    idRubrique: target.idRubrique ?? '',
                    description: target.description ?? '',
                    estProratise: Boolean(target.estProratise),
                    estPlafonneDixieme: Boolean(target.estPlafonneDixieme),
                    estActif: Boolean(target.estActif),
                    ordreAffichage: target.ordreAffichage ?? 100,
                });
            } else {
                setSelectedId(null);
                setIsCreatingNew(true);
                setForm({ ...emptyType });
            }
        } catch (cause) {
            setError(getErrorMessage(cause, 'Impossible de charger le catalogue des variables.'));
        } finally {
            setLoading(false);
        }
    }, []);

    // Déclenché uniquement une fois au montage
    useEffect(() => {
        load();
    }, [load]);

    const selectedType = useMemo(() => types.find(type => type.id === selectedId), [types, selectedId]);
    const selectedRubrique = useMemo(() => rubriques.find(r => Number(r.id) === Number(form.idRubrique)), [rubriques, form.idRubrique]);
    const rubriquesById = useMemo(() => new Map(rubriques.map(r => [Number(r.id), r])), [rubriques]);

    const compatibleRubriques = useMemo(() => rubriques.filter(rubrique =>
        (rubrique.sens === form.sens && rubrique.estActif && !rubrique.estFixe && rubrique.typeValeur === 'MONTANT')
        || Number(rubrique.id) === Number(form.idRubrique)
    ), [rubriques, form.sens, form.idRubrique]);

    // Filtrage dynamique
    const filteredTypes = useMemo(() => {
        return types.filter(type => {
            if (filterSens !== 'ALL' && type.sens !== filterSens) return false;
            if (query.trim()) {
                const q = query.toLowerCase().trim();
                const matches = `${type.libelle} ${type.code} ${type.description || ''}`.toLowerCase().includes(q);
                if (!matches) return false;
            }
            return true;
        });
    }, [types, filterSens, query]);

    const isSpecial = selectedType && selectedType.nature !== 'VARIABLE_DAF';

    // Sélection d'un élément existant (purement local, aucun reload ni reset)
    const handleSelectType = (type) => {
        setIsCreatingNew(false);
        setSelectedId(type.id);
        setForm({
            code: type.code || '',
            libelle: type.libelle || '',
            sens: type.sens || 'GAIN',
            categorie: type.categorie || 'PRIME',
            idRubrique: type.idRubrique ?? '',
            description: type.description ?? '',
            estProratise: Boolean(type.estProratise),
            estPlafonneDixieme: Boolean(type.estPlafonneDixieme),
            estActif: Boolean(type.estActif),
            ordreAffichage: type.ordreAffichage ?? 100,
        });
        setFormError('');
        setNotice('');

        // Focus immédiat et propre sur le libellé pour éditer
        setTimeout(() => {
            libelleInputRef.current?.focus();
        }, 50);
    };

    // Déclenchement de la création avec focus sur le code
    const handleStartCreate = () => {
        setIsCreatingNew(true);
        setSelectedId(null);
        setForm({ ...emptyType });
        setFormError('');
        setNotice('');

        // Focus immédiat sur le code
        setTimeout(() => {
            codeInputRef.current?.focus();
        }, 50);
    };

    // Annuler création ou rétablir
    const handleCancelForm = () => {
        setFormError('');
        if (isCreatingNew) {
            setIsCreatingNew(false);
            if (types.length > 0) {
                handleSelectType(types[0]);
            }
        } else if (selectedType) {
            handleSelectType(selectedType);
        }
    };

    // Bascule rapide d'activation (met à jour le state localement sans recharger toute la page)
    const handleToggleActif = async (type, e) => {
        e?.stopPropagation();
        try {
            await paieService.modifierTypeVariable(type.id, {
                ...type,
                estActif: !type.estActif,
            });
            toast.success(`Type « ${type.libelle} » ${!type.estActif ? 'activé' : 'désactivé'}.`);
            setTypes(prev => prev.map(t => t.id === type.id ? { ...t, estActif: !t.estActif } : t));
            if (selectedId === type.id) {
                setForm(f => ({ ...f, estActif: !f.estActif }));
            }
        } catch (err) {
            toast.error(getErrorMessage(err, 'Erreur lors du changement de statut.'));
        }
    };

    // Soumission du formulaire (création ou mise à jour)
    const handleSubmit = async (event) => {
        event.preventDefault();
        if (!form.code.trim() || !form.libelle.trim() || !form.idRubrique) {
            setFormError('Renseignez le code, le libellé et la rubrique de calcul.');
            return;
        }
        if (!selectedRubrique || (selectedRubrique.sens !== form.sens && Number(selectedRubrique.id) !== Number(selectedType?.idRubrique))) {
            setFormError('La rubrique associée doit impérativement avoir le même sens (Gain ou Retenue) que le type de variable.');
            return;
        }

        setSaving(true);
        setFormError('');
        setNotice('');

        try {
            const payload = {
                ...form,
                code: form.code.trim().toUpperCase(),
                libelle: form.libelle.trim(),
                categorie: selectedId ? form.categorie : (form.sens === 'GAIN' ? 'PRIME' : 'RETENUE_DIVERSE'),
                idRubrique: Number(form.idRubrique),
                ordreAffichage: Number(form.ordreAffichage) || 100,
            };

            const saved = selectedId
                ? await paieService.modifierTypeVariable(selectedId, payload)
                : await paieService.creerTypeVariable(payload);

            const catalogue = await paieService.getCatalogueVariables();
            const updatedTypes = Array.isArray(catalogue?.types) ? catalogue.types : [];
            const savedType = updatedTypes.find(t => t.id === saved?.id || t.code === (saved?.code || payload.code));

            setTypes(updatedTypes);
            setRubriques(Array.isArray(catalogue?.rubriques) ? catalogue.rubriques : []);

            setIsCreatingNew(false);
            if (savedType) {
                setSelectedId(savedType.id);
                setForm({
                    code: savedType.code || '',
                    libelle: savedType.libelle || '',
                    sens: savedType.sens || 'GAIN',
                    categorie: savedType.categorie || 'PRIME',
                    idRubrique: savedType.idRubrique ?? '',
                    description: savedType.description ?? '',
                    estProratise: Boolean(savedType.estProratise),
                    estPlafonneDixieme: Boolean(savedType.estPlafonneDixieme),
                    estActif: Boolean(savedType.estActif),
                    ordreAffichage: savedType.ordreAffichage ?? 100,
                });
            }

            toast.success(`Type « ${savedType?.libelle || payload.libelle} » enregistré avec succès.`);
        } catch (cause) {
            setFormError(getErrorMessage(cause, 'Impossible d’enregistrer le type de variable.'));
        } finally {
            setSaving(false);
        }
    };

    const rubriqueLabel = (type) => {
        if (type.nature !== 'VARIABLE_DAF') return 'Circuit métier dédié';
        const rubrique = rubriquesById.get(Number(type.idRubrique));
        return rubrique ? `${rubrique.libelle} (${rubrique.code})` : 'Non définie';
    };

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-5 font-sans">
            {returnTo && (
                <Button type="button" variant="ghost" size="sm" iconLeft={<ArrowLeft size={15} />} onClick={() => navigate(returnTo)}>
                    Retour à la campagne de paie
                </Button>
            )}

            {/* En-tête principal uniforme */}
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h4 className="font-bold text-[var(--color-fg)] text-base">Types de variables de paie</h4>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Nomenclature des primes, retenues et indemnités saisissables en campagne de paie.
                    </p>
                </div>
                {canWrite && (
                    <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                        onClick={handleStartCreate}
                    >
                        Nouveau Type
                    </Button>
                )}
            </div>

            {error && <Alert type="error" message={error} />}
            {notice && <Alert type="success" message={notice} onClose={() => setNotice('')} />}

            {loading ? (
                <SkeletonTable rows={6} columns={2} />
            ) : (
                /* Grille Master-Detail (2 volets) */
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">

                    {/* ═══ VOLET GAUCHE (Catalogue / Master) ═══ */}
                    <div className="lg:col-span-5 space-y-3">
                        <div className="flex items-center justify-between">
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Catalogue ({filteredTypes.length} / {types.length})
                            </span>
                        </div>

                        {/* Barre de recherche */}
                        <div className="relative">
                            <Input
                                type="search"
                                value={query}
                                onChange={e => setQuery(e.target.value)}
                                placeholder="Rechercher par nom, code..."
                                iconLeft={<Search size={14} />}
                                className="text-xs py-1.5"
                            />
                        </div>

                        {/* Filtres par onglets pilules */}
                        <div className="flex items-center gap-1.5 border-b border-[var(--color-border-subtle)] pb-2 text-xs">
                            <button
                                type="button"
                                onClick={() => setFilterSens('ALL')}
                                className={`px-2.5 py-1 text-xs font-semibold rounded-none transition-colors cursor-pointer ${filterSens === 'ALL' ? 'bg-[var(--color-fg)] text-[var(--color-bg-surface)]' : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                            >
                                Tous ({types.length})
                            </button>
                            <button
                                type="button"
                                onClick={() => setFilterSens('GAIN')}
                                className={`px-2.5 py-1 text-xs font-semibold rounded-none flex items-center gap-1 transition-colors cursor-pointer ${filterSens === 'GAIN' ? 'bg-[var(--color-success-600)] text-white' : 'bg-[var(--color-bg-subtle)] text-[var(--color-success-700)] dark:text-[var(--color-success-400)] hover:bg-[var(--color-success-500)]/10'}`}
                            >
                                <ArrowUpRight size={12} /> Gains ({types.filter(t => t.sens === 'GAIN').length})
                            </button>
                            <button
                                type="button"
                                onClick={() => setFilterSens('RETENUE')}
                                className={`px-2.5 py-1 text-xs font-semibold rounded-none flex items-center gap-1 transition-colors cursor-pointer ${filterSens === 'RETENUE' ? 'bg-[var(--color-warning-600)] text-white' : 'bg-[var(--color-bg-subtle)] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] hover:bg-[var(--color-warning-500)]/10'}`}
                            >
                                <ArrowDownRight size={12} /> Retenues ({types.filter(t => t.sens === 'RETENUE').length})
                            </button>
                        </div>

                        {/* Liste des cartes cliquables */}
                        <div className="space-y-2">
                            {filteredTypes.length === 0 ? (
                                <EmptyState
                                    icon={Search}
                                    title="Aucun type trouvé"
                                    description="Aucun type de variable ne correspond aux critères de recherche."
                                />
                            ) : (
                                filteredTypes.map((type) => {
                                    const isSelected = selectedId === type.id && !isCreatingNew;
                                    const isActif = type.estActif !== false;

                                    return (
                                        <button
                                            type="button"
                                            key={type.id ?? type.code}
                                            onClick={() => handleSelectType(type)}
                                            className={`w-full text-left p-3 border cursor-pointer transition-all text-xs rounded-none block focus:outline-none focus:ring-2 focus:ring-[var(--color-brand-500)] ${isSelected
                                                ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5 border-l-4 border-l-[var(--color-brand-500)] shadow-xs'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:border-[var(--color-brand-400)] hover:bg-[var(--color-bg-subtle)]/40'
                                                }`}
                                        >
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="min-w-0 flex-1">
                                                    <div className="flex items-center gap-2 flex-wrap">
                                                        <span className="font-bold text-[var(--color-fg)] truncate">
                                                            {type.libelle}
                                                        </span>
                                                        <Badge
                                                            variant={type.sens === 'GAIN' ? 'success' : 'warning'}
                                                            size="xs"
                                                        >
                                                            {type.sens === 'GAIN' ? 'Gain' : 'Retenue'}
                                                        </Badge>
                                                    </div>
                                                    <span className="font-mono text-[11px] font-semibold text-[var(--color-brand-500)] block mt-0.5">
                                                        {type.code}
                                                    </span>
                                                </div>

                                                <div className="flex items-center gap-1.5 shrink-0">
                                                    <Badge
                                                        variant={isActif ? 'success' : 'default'}
                                                        size="xs"
                                                        dot
                                                    >
                                                        {isActif ? 'Actif' : 'Inactif'}
                                                    </Badge>
                                                </div>
                                            </div>

                                            <div className="mt-2 pt-2 border-t border-[var(--color-border-subtle)]/60 flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                                                <span className="truncate max-w-[200px]" title={rubriqueLabel(type)}>
                                                    {rubriqueLabel(type)}
                                                </span>
                                                <span className="text-[10px] uppercase font-semibold text-[var(--color-fg-subtle)]">
                                                    {type.nature === 'VARIABLE_DAF' ? 'Manuel' : 'Circuit'}
                                                </span>
                                            </div>
                                        </button>
                                    );
                                })
                            )}
                        </div>
                    </div>

                    {/* ═══ VOLET DROIT (Détail / Formulaire / Simulation) ═══ */}
                    <div className="lg:col-span-7 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-5 space-y-5 rounded-none shadow-xs">

                        {/* En-tête du volet droit */}
                        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                            <div>
                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] block">
                                    {isCreatingNew ? 'Création de Type' : 'Paramétrage & Règle de Calcul'}
                                </span>
                                <h3 className="text-base font-bold text-[var(--color-fg)] mt-0.5 flex items-center gap-2">
                                    {isCreatingNew ? 'Nouveau type de variable' : form.libelle || 'Type sans libellé'}
                                    {!isCreatingNew && (
                                        <Badge variant={form.sens === 'GAIN' ? 'success' : 'warning'} size="xs">
                                            {form.sens === 'GAIN' ? 'Gain' : 'Retenue'}
                                        </Badge>
                                    )}
                                </h3>
                            </div>

                            {!isCreatingNew && selectedType && (
                                <div className="flex items-center gap-2">
                                    <button
                                        type="button"
                                        onClick={(e) => handleToggleActif(selectedType, e)}
                                        className={`inline-flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold rounded-none border transition-colors cursor-pointer ${form.estActif
                                            ? 'border-[var(--color-danger-500)]/30 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] hover:bg-[var(--color-danger-500)]/10'
                                            : 'border-[var(--color-success-500)]/30 text-[var(--color-success-600)] dark:text-[var(--color-success-400)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                        title={form.estActif ? 'Désactiver ce type' : 'Activer ce type'}
                                    >
                                        <Power size={13} />
                                        <span>{form.estActif ? 'Désactiver' : 'Activer'}</span>
                                    </button>
                                </div>
                            )}
                        </div>

                        {formError && <FormError message={formError} />}

                        <form onSubmit={handleSubmit} className="space-y-5 text-xs font-sans">

                            {/* SECTION 1 : Identification */}
                            <div className="space-y-3 p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
                                    <Tag size={13} className="text-[var(--color-brand-500)]" />
                                    Identification & Nomenclature
                                </span>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                    <div>
                                        <label htmlFor="config-code-technique" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                            Code technique *
                                        </label>
                                        <Input
                                            id="config-code-technique"
                                            ref={codeInputRef}
                                            required
                                            maxLength={40}
                                            value={form.code}
                                            disabled={!canWrite || (!isCreatingNew && Boolean(selectedId))}
                                            onChange={e => setForm(prev => ({ ...prev, code: e.target.value.toUpperCase() }))}
                                            placeholder="Ex. PRIME_EXCELLENCE"
                                            className="font-mono uppercase font-bold"
                                        />
                                        {!isCreatingNew && (
                                            <p className="text-[10px] text-[var(--color-fg-subtle)] mt-1">
                                                Code technique immuable (clé de référence en paie).
                                            </p>
                                        )}
                                    </div>

                                    <div>
                                        <label htmlFor="config-libelle-affiche" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                            Libellé affiché *
                                        </label>
                                        <Input
                                            id="config-libelle-affiche"
                                            ref={libelleInputRef}
                                            required
                                            maxLength={150}
                                            value={form.libelle}
                                            disabled={!canWrite}
                                            onChange={e => setForm(prev => ({ ...prev, libelle: e.target.value }))}
                                            placeholder="Ex. Prime de performance"
                                        />
                                        <p className="text-[10px] text-[var(--color-fg-subtle)] mt-1">
                                            Intitulé clair affiché sur les bulletins et exports.
                                        </p>
                                    </div>
                                </div>

                                <div>
                                    <label htmlFor="config-description-consigne" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                        Consigne d'utilisation / Justification
                                    </label>
                                    <Textarea
                                        id="config-description-consigne"
                                        rows={2}
                                        value={form.description}
                                        disabled={!canWrite}
                                        onChange={e => setForm(prev => ({ ...prev, description: e.target.value }))}
                                        placeholder="Précisez les conditions d'octroi ou les justificatifs attendus..."
                                        className="text-xs"
                                    />
                                </div>
                            </div>

                            {/* SECTION 2 : Calcul de paie & Simulation d'impact */}
                            <div className="space-y-3.5 p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
                                    <Calculator size={13} className="text-[var(--color-brand-500)]" />
                                    Traitement & Rubrique de Paie
                                </span>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                    <div>
                                        <label htmlFor="config-sens-variable" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                            Sens de la variable *
                                        </label>
                                        <Select
                                            id="config-sens-variable"
                                            value={form.sens}
                                            disabled={!canWrite || (!isCreatingNew && Boolean(selectedId))}
                                            onChange={sens => setForm(prev => ({ ...prev, sens, idRubrique: '' }))}
                                            options={[
                                                { value: 'GAIN', label: 'Gain (Ajout au salaire)' },
                                                { value: 'RETENUE', label: 'Retenue (Déduction sur net)' }
                                            ]}
                                        />
                                    </div>

                                    <div>
                                        <label htmlFor="config-rubrique-associee" className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                            Rubrique de calcul associée *
                                        </label>
                                        <Select
                                            id="config-rubrique-associee"
                                            value={String(form.idRubrique)}
                                            disabled={!canWrite || Boolean(isSpecial) || rubriques.length === 0}
                                            onChange={idRubrique => setForm(prev => ({ ...prev, idRubrique }))}
                                            options={[
                                                { value: '', label: 'Choisir une rubrique de calcul…' },
                                                ...compatibleRubriques.map(rubrique => ({
                                                    value: String(rubrique.id),
                                                    label: `${rubrique.libelle} (${rubrique.code})`
                                                }))
                                            ]}
                                        />
                                        {isSpecial && (
                                            <p className="text-[10px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] mt-1 font-medium">
                                                Ce type suit un circuit métier dédié (la rubrique est verrouillée).
                                            </p>
                                        )}
                                    </div>
                                </div>

                                {/* CARTOUCHE PREMIUM : Simulation d'impact sur le bulletin */}
                                <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                    <div className="flex items-center gap-1.5 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold text-xs">
                                        <Info size={14} />
                                        <span>Impact sur le bulletin de paie</span>
                                    </div>

                                    {selectedRubrique ? (
                                        <div className="space-y-2 text-xs">
                                            <p className="text-[var(--color-fg)]">
                                                {form.sens === 'RETENUE' ? (
                                                    'Déduction directe sur le net à payer, après calcul de l’impôt (IRSA) et cotisations.'
                                                ) : (
                                                    `Ajouté ${selectedRubrique.soumisIrsa ? 'dans le salaire brut (imposable)' : 'après impôt (exonéré)'}.`
                                                )}
                                            </p>

                                            <div className="flex flex-wrap items-center gap-2 pt-1 border-t border-[var(--color-border-subtle)]/60">
                                                <Badge
                                                    variant={selectedRubrique.soumisIrsa ? 'warning' : 'success'}
                                                    size="xs"
                                                >
                                                    IRSA : {selectedRubrique.soumisIrsa ? 'Imposable' : 'Exonéré'}
                                                </Badge>
                                                <Badge
                                                    variant={selectedRubrique.soumisCnaps ? 'warning' : 'neutral'}
                                                    size="xs"
                                                >
                                                    CNAPS : {selectedRubrique.soumisCnaps ? 'Cotisable' : 'Non cotisable'}
                                                </Badge>
                                                <Badge variant="default" size="xs">
                                                    Rubrique : {selectedRubrique.code}
                                                </Badge>
                                            </div>
                                        </div>
                                    ) : (
                                        <p className="text-[var(--color-fg-muted)] text-[11px]">
                                            Sélectionnez une rubrique de calcul pour visualiser en direct son traitement fiscal et social.
                                        </p>
                                    )}
                                </div>
                            </div>

                            {/* SECTION 3 : Paramètres avancés & Disponibilité */}
                            <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-3">
                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
                                    <Sliders size={13} className="text-[var(--color-brand-500)]" />
                                    Disponibilité & Options
                                </span>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-center">
                                    <div className="flex items-center gap-2">
                                        <input
                                            id="config-disponible-saisie"
                                            type="checkbox"
                                            checked={form.estActif}
                                            disabled={!canWrite}
                                            onChange={e => setForm(prev => ({ ...prev, estActif: e.target.checked }))}
                                            className="rounded border-[var(--color-border-subtle)] text-[var(--color-brand-600)] focus:ring-[var(--color-brand-500)] cursor-pointer"
                                        />
                                        <label htmlFor="config-disponible-saisie" className="cursor-pointer">
                                            <span className="font-semibold text-xs text-[var(--color-fg)] block">
                                                Disponible en saisie
                                            </span>
                                            <span className="text-[10px] text-[var(--color-fg-muted)]">
                                                Visible dans la grille de saisie des variables de paie
                                            </span>
                                        </label>
                                    </div>

                                    <div>
                                        <label htmlFor="config-ordre-affichage" className="block text-[11px] font-semibold text-[var(--color-fg)] mb-1">
                                            Priorité d'ordre d'affichage
                                        </label>
                                        <Input
                                            id="config-ordre-affichage"
                                            type="number"
                                            value={String(form.ordreAffichage)}
                                            disabled={!canWrite}
                                            onChange={e => setForm(prev => ({ ...prev, ordreAffichage: e.target.value }))}
                                            className="w-28 font-mono text-xs"
                                        />
                                    </div>
                                </div>
                            </div>

                            {/* Barre d'actions du formulaire */}
                            {canWrite && (
                                <div className="flex items-center justify-between pt-3 border-t border-[var(--color-border-subtle)]">
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        iconLeft={<RotateCcw size={13} />}
                                        onClick={handleCancelForm}
                                        disabled={saving}
                                    >
                                        {isCreatingNew ? 'Annuler' : 'Rétablir'}
                                    </Button>

                                    <Button
                                        type="submit"
                                        variant="primary"
                                        size="sm"
                                        loading={saving}
                                        disabled={saving || !form.idRubrique}
                                    >
                                        {isCreatingNew ? 'Créer le type de variable' : 'Enregistrer les modifications'}
                                    </Button>
                                </div>
                            )}
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}

