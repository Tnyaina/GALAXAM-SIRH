import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
    ArrowRight, ChevronDown, HelpCircle,
    Plus, Search, Settings2, X
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import FieldError from '@/components/ui/forms/FieldError';
import FormModal from '@/components/ui/overlays/FormModal';
import SlideOver from '@/components/ui/overlays/SlideOver';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import PageHeader from '@/components/ui/navigation/PageHeader';
import { getErrorMessage } from '@/api/apiClient';
import { useAuth } from '@/context/AuthContext';
import paieService from '@/features/payroll/api/paieService';
import { fmtDate } from '@/utils/dateUtils';
import RubriqueCyclePreview from './RubriqueCyclePreview';

function categorizeType(item) {
    const code = (item.code || '').toUpperCase();
    const cat = (item.categorie || item.nature || '').toUpperCase();
    const lib = (item.libelle || '').toUpperCase();

    if (cat.includes('REGUL') || code.includes('REGUL') || lib.includes('RÉGULARISATION') || lib.includes('REGULARISATION')) {
        return { key: 'REGULARISATIONS', label: 'RÉGULARISATIONS', order: 2 };
    }
    if (cat.includes('AVANCE') || cat.includes('ACOMPTE') || code.includes('AVANCE') || code.includes('ACOMPTE') || lib.includes('AVANCE') || lib.includes('ACOMPTE')) {
        return { key: 'AVANCES', label: 'AVANCES & ACOMPTES', order: 3 };
    }
    if (item.sens === 'RETENUE') {
        return { key: 'RETENUES', label: 'RETENUES', order: 4 };
    }
    if (item.sens === 'GAIN') {
        return { key: 'GAINS', label: 'GAINS', order: 1 };
    }
    return { key: 'AUTRES', label: 'AUTRES ÉLÉMENTS', order: 5 };
}

export default function PayrollEngineMap() {
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const { hasPermission, hasRole } = useAuth();
    const toast = useToast();
    const canWriteRubrique = hasRole ? hasRole('ROLE_ADMIN') : hasPermission('ROLE_ADMIN');
    const canWriteType = canWriteRubrique || hasPermission('PAIE_WRITE');

    const mode = params.get('mode') === 'types' || params.get('vue') === 'types'
        ? 'types'
        : 'rubriques';

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [reload, setReload] = useState(0);

    // Guide pédagogique
    const [showGuide, setShowGuide] = useState(false);

    // Filtres
    const [query, setQuery] = useState('');
    const [filterEtape, setFilterEtape] = useState('');
    const [filterNiveau, setFilterNiveau] = useState('');
    const [filterAlimentation, setFilterAlimentation] = useState('');
    const [filterSens, setFilterSens] = useState('');
    const [filterStatut, setFilterStatut] = useState('');

    // Accordéon des groupes (rétractables)
    const [collapsedGroups, setCollapsedGroups] = useState({});

    // Fiche détaillée (SlideOver)
    const [detailItem, setDetailItem] = useState(null); // { domaine: 'types' | 'rubriques', item: object }

    // Modal éditeur (création / modification)
    const [editor, setEditor] = useState(null); // { domaine: 'types' | 'rubriques', item: null | object, version: boolean }
    const [catalogueMeta, setCatalogueMeta] = useState(null);
    const [form, setForm] = useState({});
    const [rubriqueGuide, setRubriqueGuide] = useState('');
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState('');
    const [saving, setSaving] = useState(false);
    const submitLock = useRef(false);

    useEffect(() => {
        const controller = new AbortController();
        paieService.getParcoursConfiguration(controller.signal)
            .then(result => {
                if (!controller.signal.aborted) {
                    setData(result);
                    setError('');
                }
            })
            .catch(cause => {
                if (!controller.signal.aborted) {
                    setError(getErrorMessage(cause, 'Impossible de charger la configuration de paie.'));
                }
            })
            .finally(() => {
                if (!controller.signal.aborted) setLoading(false);
            });
        return () => controller.abort();
    }, [reload]);

    // Préchargement métadonnées catalogue
    useEffect(() => {
        if (typeof paieService.getConfigurationCatalogue === 'function') {
            paieService.getConfigurationCatalogue({ domaine: 'rubriques', taille: 10 })
                ?.then(res => setCatalogueMeta(res))
                ?.catch(() => {});
        }
    }, []);

    const phaseList = useMemo(() => data?.phases || [], [data?.phases]);
    const knownPhases = useMemo(() => new Set(phaseList.map(p => p.code)), [phaseList]);

    // Rubriques filtrées
    const filteredRubriques = useMemo(() => {
        if (!data?.rubriques) return [];
        const needle = query.trim().toLocaleLowerCase('fr');
        return data.rubriques.filter(item => {
            if (needle) {
                const matches = `${item.code} ${item.libelle} ${item.description || ''}`
                    .toLocaleLowerCase('fr')
                    .includes(needle);
                if (!matches) return false;
            }
            if (filterEtape) {
                const stage = item.phaseCalcul || item.etapeCalcul;
                if (stage !== filterEtape) return false;
            }
            if (filterNiveau) {
                if (item.niveauConfiguration !== filterNiveau) return false;
            }
            if (filterAlimentation) {
                const hasTypes = (data.types || []).some(t => t.idRubrique === item.id);
                if (filterAlimentation === 'VARIABLE' && !hasTypes) return false;
                if (filterAlimentation === 'AUTO' && hasTypes) return false;
            }
            return true;
        });
    }, [data?.rubriques, data?.types, query, filterEtape, filterNiveau, filterAlimentation]);

    // Groupes de rubriques
    const rubriqueGroups = useMemo(() => {
        if (!data?.rubriques) return [];
        const phaseKey = item => item.phaseCalcul || item.etapeCalcul || 'DOMAINE';

        const groups = [
            ...phaseList.map(phase => ({
                code: phase.code,
                libelle: phase.libelle,
                description: phase.description,
                rows: filteredRubriques.filter(item => phaseKey(item) === phase.code),
            })),
            ...[...new Set(filteredRubriques.map(phaseKey).filter(code => !knownPhases.has(code)))].map(code => ({
                code,
                libelle: filteredRubriques.find(item => phaseKey(item) === code)?.etapeLibelle || code,
                rows: filteredRubriques.filter(item => phaseKey(item) === code),
            })),
        ].filter(group => group.rows.length > 0);

        return groups;
    }, [filteredRubriques, phaseList, knownPhases, data?.rubriques]);

    // Types filtrés
    const filteredTypes = useMemo(() => {
        if (!data?.types) return [];
        const needle = query.trim().toLocaleLowerCase('fr');
        return data.types.filter(item => {
            if (needle) {
                const targetRubrique = item.rubrique || data?.rubriques?.find(r => r.id === item.idRubrique);
                const matches = `${item.code} ${item.libelle} ${targetRubrique?.code || ''} ${targetRubrique?.libelle || ''}`
                    .toLocaleLowerCase('fr')
                    .includes(needle);
                if (!matches) return false;
            }
            if (filterSens) {
                if (item.sens !== filterSens) return false;
            }
            if (filterStatut) {
                if (filterStatut === 'MANUAL' && !item.saisieManuelleAutorisee) return false;
                if (filterStatut === 'RESTRICTED' && item.saisieManuelleAutorisee) return false;
                if (filterStatut === 'ACTIVE' && item.estActif === false) return false;
            }
            return true;
        });
    }, [data?.types, data?.rubriques, query, filterSens, filterStatut]);

    // Groupes de types
    const typeGroups = useMemo(() => {
        if (!data?.types) return [];
        const map = new Map();

        filteredTypes.forEach(item => {
            const cat = categorizeType(item);
            if (!map.has(cat.key)) {
                map.set(cat.key, { code: cat.key, libelle: cat.label, order: cat.order, rows: [] });
            }
            map.get(cat.key).rows.push(item);
        });

        return Array.from(map.values()).sort((a, b) => a.order - b.order);
    }, [filteredTypes, data?.types]);

    // Options étapes pour le filtre
    const etapeOptions = useMemo(() => {
        if (!data?.phases && !data?.rubriques) return [];
        const opts = (data.phases || []).map(p => ({ value: p.code, label: p.libelle }));
        const existingCodes = new Set(opts.map(o => o.value));
        (data.rubriques || []).forEach(r => {
            const code = r.phaseCalcul || r.etapeCalcul;
            if (code && !existingCodes.has(code)) {
                existingCodes.add(code);
                opts.push({ value: code, label: r.etapeLibelle || code });
            }
        });
        return opts;
    }, [data?.phases, data?.rubriques]);

    const changeMode = next => {
        setParams(previous => {
            const updated = new URLSearchParams(previous);
            updated.delete('vue');
            updated.set('mode', next);
            return updated;
        });
        setQuery('');
        setFilterEtape('');
        setFilterNiveau('');
        setFilterAlimentation('');
        setFilterSens('');
        setFilterStatut('');
    };

    const toggleGroup = code => {
        setCollapsedGroups(prev => ({ ...prev, [code]: !prev[code] }));
    };

    const toggleAllGroups = (currentGroups) => {
        const allCollapsed = currentGroups.every(g => collapsedGroups[g.code]);
        const nextState = {};
        currentGroups.forEach(g => {
            nextState[g.code] = !allCollapsed;
        });
        setCollapsedGroups(nextState);
    };

    // Réaction aux paramètres URL
    useEffect(() => {
        if (!data) return;
        if (params.get('vt_creer') === '1') {
            const rubId = params.get('vt_rubrique');
            openEditor('types', rubId ? Number(rubId) : null, 'create');
        } else if (params.get('rb_creer') === '1') {
            openEditor('rubriques', null, 'create');
        } else if (params.get('rb_selection')) {
            const target = data.rubriques?.find(r => String(r.id) === String(params.get('rb_selection')));
            if (target) setDetailItem({ domaine: 'rubriques', item: target });
        } else if (params.get('vt_selection')) {
            const target = data.types?.find(t => String(t.id) === String(params.get('vt_selection')));
            if (target) setDetailItem({ domaine: 'types', item: target });
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [data, params]);

    // Gestion de l'éditeur modal
    async function openEditor(domaine, targetItem = null, intent = 'create') {
        const isTypes = domaine === 'types';
        const isVersion = intent === 'version';
        let item = null;
        if (intent === 'create') {
            item = null;
        } else if (targetItem && typeof targetItem === 'object') {
            item = targetItem;
        } else if (targetItem && data) {
            item = (isTypes ? data.types : data.rubriques).find(i => i.id === Number(targetItem)) || null;
        }

        let meta = catalogueMeta;
        if (!meta && typeof paieService.getConfigurationCatalogue === 'function') {
            try {
                meta = await paieService.getConfigurationCatalogue({ domaine, taille: 10 });
                setCatalogueMeta(meta);
            } catch (e) {
                console.error(e);
            }
        }

        const defaults = isTypes
            ? (meta?.defautsType || { sens: 'GAIN', categorie: 'PRIME', estActif: true, ordreAffichage: 100 })
            : (meta?.defautsRubrique || {
                categorie: 'PRIME', sens: 'GAIN', etapeCalcul: 'BRUT',
                methodeCalcul: 'MONTANT', politiqueArrondi: 'CENTIME', conditionApplication: 'TOUS',
                soumisIrsa: true, soumisCnaps: true, soumisSante: true, soumisFmfp: true,
                estActif: true, ordreAffichage: 10,
            });

        const initialForm = {
            code: '',
            libelle: '',
            description: '',
            referenceReglementaire: '',
            dateDebutEffet: '',
            dateFinEffet: '',
            idRubrique: '',
            ...defaults,
            ...(item || {}),
            ...(isVersion ? { code: '', dateDebutEffet: '', dateFinEffet: '', idRubriqueOrigine: item?.idRubriqueOrigine || item?.id } : {}),
        };

        if (intent === 'create' && isTypes) {
            const linkedRubrique = data?.rubriques?.find(r => String(r.id) === String(targetItem?.idRubrique ?? targetItem));
            if (linkedRubrique) {
                initialForm.idRubrique = String(linkedRubrique.id);
                initialForm.sens = linkedRubrique.sens;
                initialForm.categorie = linkedRubrique.categorie;
            }
        }

        setForm(initialForm);
        setRubriqueGuide('');
        setErrors({});
        setFormError('');
        setEditor({ domaine, item, version: isVersion, intent });
    }

    const isEditingTypes = editor?.domaine === 'types';
    const modalOptions = catalogueMeta?.options || {};
    const treatmentLocked = editor?.item?.traitementVerrouille && !editor?.version;
    const identityLocked = isEditingTypes && !!editor?.item;
    const isCodeLocked = (!!editor?.item && !editor?.version);
    const chosenStage = modalOptions.etapes?.find(o => o.value === form.etapeCalcul);

    const availableRubriques = useMemo(() => {
        const fromMeta = catalogueMeta?.rubriquesCompatibles || [];
        if (fromMeta.length > 0) return fromMeta;
        return (data?.rubriques || []).filter(r => r.niveauConfiguration === 'CONFIGURABLE'
            && r.estActif !== false && (r.typeValeur == null || r.typeValeur === 'MONTANT'));
    }, [catalogueMeta?.rubriquesCompatibles, data?.rubriques]);

    function validateField(name, value, draft = form) {
        if (treatmentLocked && name !== 'libelle') return '';
        if (['code', 'libelle'].includes(name) && !String(value || '').trim()) return 'Renseignez ce champ.';
        if (name === 'code' && value && !/^[A-Z][A-Z0-9_]{2,39}$/.test(value)) {
            return catalogueMeta?.validation?.codeMessage || 'Utilisez 3 à 40 caractères : lettres majuscules, chiffres ou tirets bas ; commencez par une lettre.';
        }
        if (!isEditingTypes && name === 'referenceReglementaire' && !String(value || '').trim()) {
            return 'Indiquez le texte, l’accord ou la décision qui justifie le traitement.';
        }
        if (!isEditingTypes && name === 'dateDebutEffet' && !value && (!editor?.item || editor.version)) {
            return 'Choisissez la date de début d’application.';
        }
        if (name === 'dateFinEffet' && value && (!draft.dateDebutEffet || value < draft.dateDebutEffet)) {
            return 'La date de fin doit suivre la date de début.';
        }
        if (!isEditingTypes && name === 'etapeCalcul' && !modalOptions.etapes?.some(o => o.value === value && o.sens === draft.sens)) {
            return 'Choisissez l’étape d’intervention.';
        }
        if (isEditingTypes && name === 'idRubrique' && !value) {
            return 'Choisissez la rubrique de calcul.';
        }
        return '';
    }

    function handleField(name, value) {
        setForm(previous => ({
            ...previous,
            [name]: value,
            ...(name === 'soumisCnaps' ? { soumisFmfp: value } : {}),
        }));
        if (errors[name]) setErrors(previous => ({ ...previous, [name]: validateField(name, value) }));
    }

    function handleBlur(name) {
        setErrors(previous => ({ ...previous, [name]: validateField(name, form[name]) }));
    }

    async function handleSubmit(event) {
        event.preventDefault();
        if (submitLock.current || saving) return;
        const isTypes = editor.domaine === 'types';
        const fields = isTypes
            ? ['code', 'libelle', 'idRubrique']
            : ['code', 'libelle', 'etapeCalcul', 'referenceReglementaire', 'dateDebutEffet', 'dateFinEffet'];
        const invalid = Object.fromEntries(fields.map(name => [name, validateField(name, form[name])]));
        setErrors(invalid);
        if (Object.values(invalid).some(Boolean)) return;

        submitLock.current = true;
        setSaving(true);
        setFormError('');
        try {
            const payload = { ...form, code: form.code.trim().toUpperCase(), libelle: form.libelle.trim() };
            if (isTypes) {
                payload.idRubrique = Number(form.idRubrique);
                const linkedRubrique = availableRubriques.find(r => String(r.id) === String(form.idRubrique));
                payload.categorie = editor.item?.categorie || linkedRubrique?.categorie;
                if (editor.item) await paieService.modifierTypeVariable(editor.item.id, payload);
                else await paieService.creerTypeVariable(payload);
            } else {
                payload.dateDebutEffet = form.dateDebutEffet || null;
                payload.dateFinEffet = form.dateFinEffet || null;
                payload.soumisFmfp = payload.soumisCnaps;
                if (editor.version) await paieService.creerVersionRubrique(editor.item.id, payload);
                else if (editor.item) await paieService.modifierRubriqueVariable(editor.item.id, payload);
                else await paieService.creerRubriqueVariable(payload);
            }
            toast.success(editor.version ? 'Nouvelle version enregistrée.' : 'Configuration enregistrée.');
            setEditor(null);
            setReload(v => v + 1);
        } catch (cause) {
            setFormError(getErrorMessage(cause, 'L’enregistrement n’a pas abouti. Vérifiez les champs puis réessayez.'));
        } finally {
            submitLock.current = false;
            setSaving(false);
        }
    }

    const inputProps = name => ({
        value: form[name] ?? '',
        onChange: e => handleField(name, e.target.value),
        onBlur: () => handleBlur(name),
        error: errors[name],
    });

    const activeGroups = mode === 'types' ? typeGroups : rubriqueGroups;
    const areAllGroupsCollapsed = activeGroups.length > 0 && activeGroups.every(g => collapsedGroups[g.code]);

    return (
        <div className="space-y-6">
            {/* ── En-tête de la page conforme au Design System ── */}
            <PageHeader
                title="Paramétrage de la paie"
                subtitle="Configurez les règles du moteur et les éléments saisissables séparément."
                icon={Settings2}
                breadcrumbs={[
                    { label: 'Paie', href: '/paie' },
                    { label: 'Configuration de paie' },
                ]}
                actions={
                    <div className="flex items-center gap-2">
                        {!showGuide && (
                            <Button
                                variant="ghost"
                                size="sm"
                                iconLeft={<HelpCircle size={15} />}
                                onClick={() => setShowGuide(true)}
                            >
                                Guide
                            </Button>
                        )}
                        {mode === 'types'
                            ? canWriteType && (
                                <Button
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<Plus size={15} />}
                                    onClick={() => openEditor('types', null, 'create')}
                                >
                                    Nouveau type
                                </Button>
                            )
                            : canWriteRubrique && (
                                <Button
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<Plus size={15} />}
                                    onClick={() => openEditor('rubriques', null, 'create')}
                                >
                                    Nouvelle règle
                                </Button>
                            )}
                    </div>
                }
            />

            {/* ── Bannière d'information ── */}
            {showGuide && (
                <div className="p-3.5 sm:p-4 bg-[var(--color-brand-50)]/60 dark:bg-[var(--color-brand-950)]/20 border border-[var(--color-brand-500)]/30 flex items-start justify-between gap-3 text-xs leading-relaxed">
                    <div className="flex items-start gap-2.5">
                        <HelpCircle size={16} className="text-[var(--color-brand-600)] shrink-0 mt-0.5" />
                        <div>
                            <p className="font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] text-sm">
                                Types de variable et Rubriques de calcul
                            </p>
                            <div className="mt-1 text-[var(--color-fg-muted)] space-y-1">
                                <p>
                                    • <strong>Types de variable</strong> : éléments saisis lors des campagnes (primes, avances, régularisations).
                                </p>
                                <p>
                                    • <strong>Rubriques de calcul</strong> : règles de calcul appliquées au bulletin (cotisations, base imposable, déductions).
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    Un type de variable doit être rattaché à une rubrique de calcul pour être pris en compte sur le bulletin.
                                </p>
                            </div>
                        </div>
                    </div>
                    <button
                        type="button"
                        onClick={() => setShowGuide(false)}
                        aria-label="Masquer l'information"
                        className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] shrink-0 cursor-pointer"
                    >
                        <X size={15} />
                    </button>
                </div>
            )}

            {/* Navigation compacte entre les deux catalogues */}
            <nav aria-label="Catalogues de configuration" className="flex overflow-x-auto border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                {[
                    { id: 'rubriques', label: 'Rubriques de calcul', count: data?.rubriques?.length ?? 0 },
                    { id: 'types', label: 'Types de variable', count: data?.types?.length ?? 0 },
                ].map(tab => <button key={tab.id} type="button" aria-current={mode === tab.id ? 'page' : undefined}
                    onClick={() => changeMode(tab.id)}
                    className={`flex min-h-11 shrink-0 items-center gap-2 border-b-2 px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)] ${mode === tab.id ? 'border-[var(--color-brand-600)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]' : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}>
                    {tab.label}<Badge variant={mode === tab.id ? 'primary' : 'neutral'} size="sm">{tab.count}</Badge>
                </button>)}
            </nav>

            {error && <Alert type="error" title="Référentiel indisponible" message={error} />}

            {loading ? (
                <SkeletonTable rows={8} columns={4} />
            ) : !data ? null : (
                <section aria-label="Étapes du moteur" className="space-y-5">
                    {/* ═════════════════════════════════════════════════════════ */}
                    {/* VUE 1 : RUBRIQUES DE CALCUL                             */}
                    {/* ═════════════════════════════════════════════════════════ */}
                    {mode === 'rubriques' && (
                        <div className="space-y-4">
                            {/* En-tête de section */}
                            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-1">
                                <div>
                                    <div className="flex items-center gap-2.5">
                                        <h2 className="text-sm font-bold tracking-wider uppercase text-[var(--color-fg)]">
                                            Rubriques de calcul
                                        </h2>
                                        <Badge variant="neutral" size="sm">
                                            {filteredRubriques.length} rubrique{filteredRubriques.length > 1 ? 's' : ''}
                                        </Badge>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        Règles métier exécutées par le moteur de paie
                                    </p>
                                </div>
                                {rubriqueGroups.length > 0 && (
                                    <button
                                        type="button"
                                        onClick={() => toggleAllGroups(rubriqueGroups)}
                                        className="text-xs text-[var(--color-brand-600)] hover:underline font-semibold self-start sm:self-auto cursor-pointer"
                                    >
                                        {areAllGroupsCollapsed ? 'Tout déplier' : 'Tout replier'}
                                    </button>
                                )}
                            </div>

                            {/* Barre de recherche et filtres via Input Search & SearchSelect */}
                            <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
                                <Input
                                    aria-label="Rechercher une rubrique"
                                    placeholder="Rechercher une rubrique…"
                                    iconLeft={<Search size={16} />}
                                    value={query}
                                    onChange={e => setQuery(e.target.value)}
                                    containerClassName="flex-1"
                                />
                                <div className="w-full sm:w-52">
                                    <SearchSelect
                                        aria-label="Filtrer par étape"
                                        placeholder="Toutes les étapes"
                                        options={[{ value: '', label: 'Toutes les étapes' }, ...etapeOptions]}
                                        value={filterEtape}
                                        onChange={val => setFilterEtape(val || '')}
                                    />
                                </div>
                                <div className="w-full sm:w-48">
                                    <SearchSelect
                                        aria-label="Filtrer par niveau"
                                        placeholder="Tous les niveaux"
                                        options={[
                                            { value: '', label: 'Tous les niveaux' },
                                            { value: 'SYSTEM', label: 'Système' },
                                            { value: 'CONFIGURABLE', label: 'Configurable' },
                                            { value: 'SEMI_CONFIGURABLE', label: 'Semi-configurable' },
                                        ]}
                                        value={filterNiveau}
                                        onChange={val => setFilterNiveau(val || '')}
                                    />
                                </div>
                                <div className="w-full sm:w-56">
                                    <SearchSelect
                                        aria-label="Filtrer par alimentation"
                                        placeholder="Alimentation : Toutes"
                                        options={[
                                            { value: '', label: 'Alimentation : Toutes' },
                                            { value: 'VARIABLE', label: 'Avec variable liée (saisie RH)' },
                                            { value: 'AUTO', label: 'Sans type de variable lié' },
                                        ]}
                                        value={filterAlimentation}
                                        onChange={val => setFilterAlimentation(val || '')}
                                    />
                                </div>
                            </div>

                            {/* Groupes de rubriques (rétractables) */}
                            {rubriqueGroups.length === 0 ? (
                                <EmptyState
                                    bordered
                                    title="Aucune rubrique trouvée"
                                    description="Aucune rubrique ne correspond à vos critères de recherche."
                                    actionLabel="Effacer les filtres"
                                    onAction={() => {
                                        setQuery('');
                                        setFilterEtape('');
                                        setFilterNiveau('');
                                        setFilterAlimentation('');
                                    }}
                                />
                            ) : (
                                <div className="space-y-4">
                                    {rubriqueGroups.map(group => {
                                        const isCollapsed = Boolean(collapsedGroups[group.code]);
                                        return (
                                            <Card key={group.code} className="overflow-hidden">
                                                {/* En-tête rétractable du groupe */}
                                                <button
                                                    type="button"
                                                    aria-expanded={!isCollapsed}
                                                    onClick={() => toggleGroup(group.code)}
                                                    className="w-full flex items-center justify-between px-4 py-2.5 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-colors cursor-pointer text-left"
                                                >
                                                    <div className="flex items-center gap-2">
                                                        <ChevronDown
                                                            size={16}
                                                            className={`text-[var(--color-fg-muted)] transition-transform duration-200 ${isCollapsed ? '-rotate-90' : ''}`}
                                                        />
                                                        <span className="text-xs font-bold tracking-wider uppercase text-[var(--color-fg)]">
                                                            {group.libelle}
                                                        </span>
                                                    </div>
                                                    <span className="text-xs text-[var(--color-fg-muted)] font-medium">
                                                        {group.rows.length} rubrique{group.rows.length > 1 ? 's' : ''}
                                                    </span>
                                                </button>

                                                {/* Liste des rubriques */}
                                                {!isCollapsed && (
                                                    <ul className="divide-y divide-[var(--color-border-subtle)]">
                                                        {group.rows.map(item => {
                                                            const linkedTypes = data.types?.filter(t => t.idRubrique === item.id) || [];
                                                            const isConfigurable = item.niveauConfiguration === 'CONFIGURABLE';
                                                            const isSystem = item.niveauConfiguration === 'SYSTEM';
                                                            const niveauLabel = item.niveauLibelle || (isSystem ? 'SYSTÈME' : isConfigurable ? 'CONFIGURABLE' : item.niveauConfiguration);
                                                            const hasLinkedTypes = linkedTypes.length > 0;

                                                            return (
                                                                <li key={item.id}>
                                                                    <div
                                                                        role="button"
                                                                        tabIndex={0}
                                                                        onClick={() => setDetailItem({ domaine: 'rubriques', item })}
                                                                        onKeyDown={e => {
                                                                            if (e.key === 'Enter' || e.key === ' ') {
                                                                                e.preventDefault();
                                                                                setDetailItem({ domaine: 'rubriques', item });
                                                                            }
                                                                        }}
                                                                        className="flex items-start justify-between gap-3 px-4 py-3.5 hover:bg-[var(--color-bg-subtle)]/60 cursor-pointer transition-colors sm:items-center"
                                                                    >
                                                                        {/* Libellé et code */}
                                                                        <div className="min-w-0 flex-1">
                                                                            <p className="text-sm font-semibold text-[var(--color-fg)] leading-snug">
                                                                                {item.libelle}
                                                                            </p>
                                                                            <p className="text-[11px] font-mono text-[var(--color-fg-subtle)] mt-1">
                                                                                Code : {item.code}
                                                                            </p>
                                                                        </div>

                                                                        {/* Niveau */}
                                                                        <div className="hidden shrink-0 w-44 sm:block">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-subtle)]">Configuration</p>
                                                                            <Badge variant={isConfigurable ? 'info' : 'neutral'} size="sm" className="mt-1">{niveauLabel}</Badge>
                                                                        </div>

                                                                        {/* Mode d'alimentation */}
                                                                        <div className="hidden shrink-0 w-40 sm:block">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-subtle)]">Traitement</p>
                                                                            <span className="mt-1 block text-xs text-[var(--color-fg-muted)]">
                                                                                {hasLinkedTypes ? 'Variable liée' : isConfigurable ? 'Type à créer' : 'Circuit dédié'}
                                                                            </span>
                                                                        </div>

                                                                        {/* Bouton flèche */}
                                                                        <div className="shrink-0">
                                                                            <button
                                                                                type="button"
                                                                                aria-label={`Ouvrir le détail de ${item.libelle}`}
                                                                                onClick={e => {
                                                                                    e.stopPropagation();
                                                                                    setDetailItem({ domaine: 'rubriques', item });
                                                                                }}
                                                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                                                                            >
                                                                                <ArrowRight size={16} />
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                </li>
                                                            );
                                                        })}
                                                    </ul>
                                                )}
                                            </Card>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    )}

                    {/* ═════════════════════════════════════════════════════════ */}
                    {/* VUE 2 : TYPES DE VARIABLE                               */}
                    {/* ═════════════════════════════════════════════════════════ */}
                    {mode === 'types' && (
                        <div className="space-y-4">
                            {/* En-tête de section */}
                            <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2 pb-1">
                                <div>
                                    <div className="flex items-center gap-2.5">
                                        <h2 className="text-sm font-bold tracking-wider uppercase text-[var(--color-fg)]">
                                            Types de variable
                                        </h2>
                                        <Badge variant="neutral" size="sm">
                                            {filteredTypes.length} type{filteredTypes.length > 1 ? 's' : ''}
                                        </Badge>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        Éléments que l’on peut saisir dans une campagne de paie
                                    </p>
                                </div>
                                {typeGroups.length > 0 && (
                                    <button
                                        type="button"
                                        onClick={() => toggleAllGroups(typeGroups)}
                                        className="text-xs text-[var(--color-brand-600)] hover:underline font-semibold self-start sm:self-auto cursor-pointer"
                                    >
                                        {areAllGroupsCollapsed ? 'Tout déplier' : 'Tout replier'}
                                    </button>
                                )}
                            </div>

                            {/* Barre de recherche et filtres via Input Search & SearchSelect */}
                            <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center">
                                <Input
                                    aria-label="Rechercher un type"
                                    placeholder="Rechercher un type…"
                                    iconLeft={<Search size={16} />}
                                    value={query}
                                    onChange={e => setQuery(e.target.value)}
                                    containerClassName="flex-1"
                                />
                                <div className="w-full sm:w-52">
                                    <SearchSelect
                                        aria-label="Filtrer par sens"
                                        placeholder="Tous les sens"
                                        options={[
                                            { value: '', label: 'Tous les sens' },
                                            { value: 'GAIN', label: 'Gains (+)' },
                                            { value: 'RETENUE', label: 'Retenues (-)' },
                                        ]}
                                        value={filterSens}
                                        onChange={val => setFilterSens(val || '')}
                                    />
                                </div>
                                <div className="w-full sm:w-60">
                                    <SearchSelect
                                        aria-label="Filtrer par statut"
                                        placeholder="Tous les statuts"
                                        options={[
                                            { value: '', label: 'Tous les statuts' },
                                            { value: 'MANUAL', label: 'Saisie manuelle autorisée' },
                                            { value: 'RESTRICTED', label: 'Saisie restreinte' },
                                            { value: 'ACTIVE', label: 'Actif dans le catalogue' },
                                        ]}
                                        value={filterStatut}
                                        onChange={val => setFilterStatut(val || '')}
                                    />
                                </div>
                            </div>

                            {/* Groupes de types de variable (rétractables) */}
                            {typeGroups.length === 0 ? (
                                <EmptyState
                                    bordered
                                    title="Aucun type trouvé"
                                    description="Aucun type de variable ne correspond à vos critères de recherche."
                                    actionLabel="Effacer les filtres"
                                    onAction={() => {
                                        setQuery('');
                                        setFilterSens('');
                                        setFilterStatut('');
                                    }}
                                />
                            ) : (
                                <div className="space-y-4">
                                    {typeGroups.map(group => {
                                        const isCollapsed = Boolean(collapsedGroups[group.code]);
                                        return (
                                            <Card key={group.code} className="overflow-hidden">
                                                {/* En-tête rétractable du groupe */}
                                                <button
                                                    type="button"
                                                    aria-expanded={!isCollapsed}
                                                    onClick={() => toggleGroup(group.code)}
                                                    className="w-full flex items-center justify-between px-4 py-2.5 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] transition-colors cursor-pointer text-left"
                                                >
                                                    <div className="flex items-center gap-2">
                                                        <ChevronDown
                                                            size={16}
                                                            className={`text-[var(--color-fg-muted)] transition-transform duration-200 ${isCollapsed ? '-rotate-90' : ''}`}
                                                        />
                                                        <span className="text-xs font-bold tracking-wider uppercase text-[var(--color-fg)]">
                                                            {group.libelle}
                                                        </span>
                                                    </div>
                                                    <span className="text-xs text-[var(--color-fg-muted)] font-medium">
                                                        {group.rows.length} type{group.rows.length > 1 ? 's' : ''}
                                                    </span>
                                                </button>

                                                {/* Liste des types */}
                                                {!isCollapsed && (
                                                    <ul className="divide-y divide-[var(--color-border-subtle)]">
                                                        {group.rows.map(item => {
                                                            const targetRubrique = item.rubrique || data.rubriques?.find(r => r.id === item.idRubrique);
                                                            const saisieLabel = item.saisieManuelleAutorisee ? 'Saisie manuelle' : 'Montant saisi';

                                                            return (
                                                                <li key={item.id}>
                                                                    <div
                                                                        role="button"
                                                                        tabIndex={0}
                                                                        onClick={() => setDetailItem({ domaine: 'types', item })}
                                                                        onKeyDown={e => {
                                                                            if (e.key === 'Enter' || e.key === ' ') {
                                                                                e.preventDefault();
                                                                                setDetailItem({ domaine: 'types', item });
                                                                            }
                                                                        }}
                                                                        className="flex items-start justify-between gap-3 px-4 py-3.5 hover:bg-[var(--color-bg-subtle)]/60 cursor-pointer transition-colors sm:items-center"
                                                                    >
                                                                        {/* Libellé, code et tag Gain / Retenue */}
                                                                        <div className="min-w-0 flex-1">
                                                                            <div className="flex items-center gap-2 flex-wrap">
                                                                                <p className="text-sm font-semibold text-[var(--color-fg)] leading-snug">
                                                                                    {item.libelle}
                                                                                </p>
                                                                                <span className={`text-[10px] px-1.5 py-0.2 font-semibold tracking-wider ${
                                                                                    item.sens === 'GAIN'
                                                                                        ? 'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                                                                        : 'bg-[var(--color-warning-500)]/15 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]'
                                                                                }`}>
                                                                                    {item.sens === 'GAIN' ? '+ Gain' : '- Retenue'}
                                                                                </span>
                                                                            </div>
                                                                            <p className="text-[11px] font-mono text-[var(--color-fg-subtle)] mt-1">
                                                                                Code : {item.code}
                                                                            </p>
                                                                        </div>

                                                                        {/* Mode de saisie */}
                                                                        <div className="hidden shrink-0 w-36 sm:block">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-subtle)]">Saisie</p>
                                                                            <span className="mt-1 block text-xs text-[var(--color-fg-muted)]">
                                                                                {saisieLabel}
                                                                            </span>
                                                                        </div>

                                                                        {/* Rubrique cible liée */}
                                                                        <div className="hidden min-w-0 w-64 sm:block">
                                                                            <p className="text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-subtle)]">Rubrique liée</p>
                                                                            {targetRubrique ? (
                                                                                <span className="mt-1 text-xs font-medium text-[var(--color-fg)] inline-flex items-center gap-1.5 truncate">
                                                                                    <span className="truncate">{targetRubrique.libelle}</span>
                                                                                </span>
                                                                            ) : (
                                                                                <Badge variant="warning" size="xs" className="mt-1">Non liée</Badge>
                                                                            )}
                                                                        </div>

                                                                        {/* Bouton flèche */}
                                                                        <div className="shrink-0">
                                                                            <button
                                                                                type="button"
                                                                                aria-label={`Ouvrir le détail de ${item.libelle}`}
                                                                                onClick={e => {
                                                                                    e.stopPropagation();
                                                                                    setDetailItem({ domaine: 'types', item });
                                                                                }}
                                                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                                                                            >
                                                                                <ArrowRight size={16} />
                                                                            </button>
                                                                        </div>
                                                                    </div>
                                                                </li>
                                                            );
                                                        })}
                                                    </ul>
                                                )}
                                            </Card>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    )}
                </section>
            )}

            {/* ═════════════════════════════════════════════════════════ */}
            {/* FICHE DÉTAILLÉE (SLIDEOVER DRAWER)                       */}
            {/* ═════════════════════════════════════════════════════════ */}
            <SlideOver
                isOpen={!!detailItem}
                onClose={() => setDetailItem(null)}
                title={detailItem?.domaine === 'types' ? 'Type de variable' : 'Rubrique de calcul'}
                subtitle={
                    detailItem?.domaine === 'types'
                        ? 'Élément saisissable de campagne'
                        : 'Règle métier exécutée par le moteur'
                }
                icon={<Settings2 size={18} />}
                width="xl"
                footer={
                    <div className="flex items-center justify-between w-full">
                        <div>
                            {detailItem?.domaine === 'types' ? (
                                canWriteType && detailItem?.item?.peutModifier && (
                                    <Button
                                        size="sm"
                                        onClick={() => {
                                            const target = detailItem.item;
                                            setDetailItem(null);
                                            openEditor('types', target, 'edit');
                                        }}
                                    >
                                        Modifier le type
                                    </Button>
                                )
                            ) : (
                                <div className="flex items-center gap-2">
                                    {canWriteRubrique && detailItem?.item?.peutModifier && (
                                        <Button
                                            size="sm"
                                            onClick={() => {
                                                const target = detailItem.item;
                                                setDetailItem(null);
                                                openEditor('rubriques', target, 'edit');
                                            }}
                                        >
                                            Modifier la rubrique
                                        </Button>
                                    )}
                                    {canWriteRubrique && detailItem?.item?.peutVersionner && (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => {
                                                const target = detailItem.item;
                                                setDetailItem(null);
                                                openEditor('rubriques', target, 'version');
                                            }}
                                        >
                                            Nouvelle version
                                        </Button>
                                    )}
                                    {detailItem?.item?.parametrageUrl && (
                                        <Button
                                            variant="outline"
                                            size="sm"
                                            onClick={() => navigate(detailItem.item.parametrageUrl)}
                                        >
                                            {detailItem.item.parametrageUrl.includes('parametres_globaux')
                                                ? 'Paramètres globaux'
                                                : detailItem.item.parametrageUrl.includes('reglementation_fiscale')
                                                    ? 'Réglementation fiscale'
                                                    : detailItem.item.parametrageUrl.includes('cotisations_irsa')
                                                        ? 'Barème cotisations'
                                                        : 'Paramétrage dédié'}
                                        </Button>
                                    )}
                                </div>
                            )}
                        </div>
                        <Button variant="ghost" size="sm" onClick={() => setDetailItem(null)}>
                            Fermer
                        </Button>
                    </div>
                }
            >
                {detailItem && (
                    <aside aria-label="Détail de configuration" className="space-y-6 min-w-0 max-w-full">
                        {/* ── Cas 1 : Détail d'un TYPE DE VARIABLE ── */}
                        {detailItem.domaine === 'types' && (() => {
                            const type = detailItem.item;
                            const targetRubrique = type.rubrique || data?.rubriques?.find(r => r.id === type.idRubrique);

                            return (
                                <div className="space-y-6">
                                    <div>
                                        <div className="flex items-center gap-2">
                                            <Badge variant="brand" size="sm">
                                                TYPE DE VARIABLE
                                            </Badge>
                                            <span className={`text-[10px] px-2 py-0.5 font-bold tracking-wider ${
                                                type.sens === 'GAIN'
                                                    ? 'bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                                    : 'bg-[var(--color-warning-500)]/15 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]'
                                            }`}>
                                                {type.sens === 'GAIN' ? '+ Gain pour le salarié' : '- Retenue sur salaire'}
                                            </span>
                                        </div>
                                        <h3 className="text-lg font-bold text-[var(--color-fg)] mt-2 leading-snug">
                                            {type.libelle}
                                        </h3>
                                        <p className="text-xs font-mono text-[var(--color-fg-muted)] mt-0.5">
                                            Code : {type.code}
                                        </p>
                                        <div className="mt-2.5">
                                            <Badge variant={type.saisieManuelleAutorisee ? 'success' : 'neutral'} size="sm">
                                                {type.saisieManuelleAutorisee ? 'Saisie manuelle autorisée' : 'Saisie restreinte / auto'}
                                            </Badge>
                                        </div>
                                    </div>

                                    {/* Mini-schéma de parcours de flux */}
                                    <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                        <span className="text-[10px] font-bold tracking-wider uppercase text-[var(--color-fg-muted)] block">
                                            Parcours de la donnée
                                        </span>
                                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                                            <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-fg-muted)] block">Étape 1</span>
                                                <span className="block font-bold text-[var(--color-fg)] truncate">Saisie RH</span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {type.saisieManuelleAutorisee ? 'Montant saisi' : 'Calculé'}
                                                </span>
                                            </div>
                                            <div className="p-2.5 bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/30 border border-[var(--color-brand-500)]/40 text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] block">Étape 2</span>
                                                <span className="block font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] truncate">
                                                    {targetRubrique ? targetRubrique.libelle : 'Règle liée'}
                                                </span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {targetRubrique ? (targetRubrique.etapeLibelle || targetRubrique.phaseCalcul) : 'Non liée'}
                                                </span>
                                            </div>
                                            <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-fg-muted)] block">Étape 3</span>
                                                <span className="block font-bold text-[var(--color-fg)] truncate">Bulletin</span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {type.sens === 'GAIN' ? '+ Net à payer' : '- Déduction net'}
                                                </span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Bloc explicite : Rubrique alimentée */}
                                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-3">
                                        <span className="text-[10px] font-bold tracking-wider uppercase text-[var(--color-fg-muted)] block">
                                            Rubrique alimentée
                                        </span>
                                        <div>
                                            <p className="text-base font-bold text-[var(--color-fg)]">
                                                {targetRubrique ? targetRubrique.libelle : 'Aucune rubrique liée'}
                                            </p>
                                            {targetRubrique?.code && (
                                                <p className="text-xs font-mono text-[var(--color-fg-muted)] mt-0.5">
                                                    Code : {targetRubrique.code}
                                                </p>
                                            )}
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                            {targetRubrique?.description || 'Cette rubrique applique le montant saisi au calcul du bulletin.'}
                                        </p>
                                        {targetRubrique && (
                                            <div className="pt-1">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    iconRight={<ArrowRight size={14} />}
                                                    onClick={() => {
                                                        changeMode('rubriques');
                                                        setDetailItem({ domaine: 'rubriques', item: targetRubrique });
                                                    }}
                                                >
                                                    Ouvrir la rubrique
                                                </Button>
                                            </div>
                                        )}
                                    </div>

                                    {/* Propriétés complémentaires du type */}
                                    <div>
                                        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-3">
                                            Caractéristiques de saisie
                                        </h4>
                                        <dl className="divide-y divide-[var(--color-border-subtle)] text-sm">
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Sens</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{type.sens === 'GAIN' ? 'Gain (+)' : 'Retenue (-)'}</dd>
                                            </div>
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Nature / Catégorie</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{type.categorie || type.nature || 'Variable'}</dd>
                                            </div>
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Ordre catalogue</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{type.ordreAffichage ?? '—'}</dd>
                                            </div>
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Statut catalogue</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{type.estActif !== false ? 'Actif' : 'Inactif'}</dd>
                                            </div>
                                            {type.description && (
                                                <div className="py-2">
                                                    <dt className="text-[var(--color-fg-muted)] mb-1">Consignes</dt>
                                                    <dd className="text-xs text-[var(--color-fg)] leading-relaxed break-words">{type.description}</dd>
                                                </div>
                                            )}
                                        </dl>
                                    </div>
                                </div>
                            );
                        })()}

                        {/* ── Cas 2 : Détail d'une RUBRIQUE DE CALCUL (Configurable ou Système) ── */}
                        {detailItem.domaine === 'rubriques' && (() => {
                            const rubrique = detailItem.item;
                            const linkedTypes = data?.types?.filter(t => t.idRubrique === rubrique.id) || [];
                            const isConfigurable = rubrique.niveauConfiguration === 'CONFIGURABLE';
                            const isSystem = rubrique.niveauConfiguration === 'SYSTEM';
                            const niveauLabel = rubrique.niveauLibelle || (isSystem ? 'SYSTÈME' : isConfigurable ? 'CONFIGURABLE' : rubrique.niveauConfiguration);

                            return (
                                <div className="space-y-6">
                                    <div>
                                        <div className="flex items-center gap-2">
                                            <Badge variant="neutral" size="sm">
                                                RUBRIQUE DE CALCUL
                                            </Badge>
                                            <Badge variant={isConfigurable ? 'info' : 'neutral'} size="sm">
                                                {niveauLabel}
                                            </Badge>
                                        </div>
                                        <h3 className="text-lg font-bold text-[var(--color-fg)] mt-2 leading-snug">
                                            {rubrique.libelle}
                                        </h3>
                                        <p className="text-xs font-mono text-[var(--color-fg-muted)] mt-0.5">
                                            Code : {rubrique.code}
                                        </p>
                                        <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                            Étape : {rubrique.etapeLibelle || rubrique.phaseCalcul}
                                        </p>
                                    </div>

                                    {/* Mini-schéma de parcours moteur */}
                                    <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                        <span className="text-[10px] font-bold tracking-wider uppercase text-[var(--color-fg-muted)] block">
                                            Parcours de calcul dans le moteur
                                        </span>
                                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
                                            <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-fg-muted)] block">Étape 1</span>
                                                <span className="block font-bold text-[var(--color-fg)] truncate">Données d'entrée</span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {linkedTypes.length > 0 ? `${linkedTypes.length} type(s) lié(s)` : isConfigurable ? 'Aucun type lié' : 'Circuit dédié'}
                                                </span>
                                            </div>
                                            <div className="p-2.5 bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/30 border border-[var(--color-brand-500)]/40 text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] block">Étape 2</span>
                                                <span className="block font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] truncate">
                                                    {rubrique.methodeLibelle || rubrique.sourceCalcul || 'Calcul'}
                                                </span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {rubrique.arrondiLibelle || 'Arrondi centime'}
                                                </span>
                                            </div>
                                            <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-center min-w-0">
                                                <span className="text-[10px] uppercase font-bold tracking-wider text-[var(--color-fg-muted)] block">Étape 3</span>
                                                <span className="block font-bold text-[var(--color-fg)] truncate">Impact paie</span>
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block truncate mt-0.5">
                                                    {rubrique.etapeLibelle || rubrique.phaseCalcul}
                                                </span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* 1. Bloc Assiettes sociales & fiscales soumises */}
                                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-3">
                                        <span className="text-[10px] font-bold tracking-wider uppercase text-[var(--color-fg-muted)] block">
                                            Assiettes de cotisations et fiscalité
                                        </span>
                                        <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                            {isConfigurable
                                                ? "Assiettes applicables à cette rubrique :"
                                                : "Assiettes légales appliquées pour cette rubrique système :"}
                                        </p>
                                        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1">
                                            {[
                                                { key: 'soumisIrsa', label: 'IRSA' },
                                                { key: 'soumisCnaps', label: 'CNaPS' },
                                                { key: 'soumisSante', label: 'Santé' },
                                                { key: 'soumisFmfp', label: 'FMFP' },
                                            ].map(({ key, label }) => {
                                                const isSubject = Boolean(rubrique[key]);
                                                return (
                                                    <div
                                                        key={key}
                                                        className={`p-2 border text-center flex flex-col items-center justify-center gap-0.5 ${
                                                            isSubject
                                                                ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/30 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                                                : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] opacity-60'
                                                        }`}
                                                    >
                                                        <span className="text-xs font-bold">{label}</span>
                                                        <span className="text-[10px] uppercase font-semibold">
                                                            {isSubject ? 'Soumis' : 'Exonéré'}
                                                        </span>
                                                    </div>
                                                );
                                            })}
                                        </div>
                                    </div>

                                    {/* 2. Bloc explicite : Variables alimentant cette règle */}
                                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-3">
                                        <span className="text-[10px] font-bold tracking-wider uppercase text-[var(--color-fg-muted)] block">
                                            {linkedTypes.length > 1 ? 'Types de variable alimentant cette règle' : 'Type de variable alimentant cette règle'}
                                        </span>
                                        {linkedTypes.length === 0 ? (
                                            <div className="text-xs text-[var(--color-fg-muted)] space-y-3">
                                                <p>{isConfigurable ? 'Aucun type de variable ne saisit encore cette rubrique. Créez un type lié pour l’utiliser dans une campagne de paie.' : 'Aucun type de variable ne saisit directement cette règle. Elle est exécutée par son circuit métier.'}</p>
                                                {canWriteType && isConfigurable && (
                                                    <Button
                                                        variant="outline"
                                                        size="sm"
                                                        iconLeft={<Plus size={14} />}
                                                        onClick={() => {
                                                            const target = rubrique;
                                                            setDetailItem(null);
                                                            openEditor('types', { idRubrique: target.id }, 'create');
                                                        }}
                                                    >
                                                        Créer un type lié
                                                    </Button>
                                                )}
                                            </div>
                                        ) : (
                                            <div className="space-y-2.5">
                                                <p className="text-xs text-[var(--color-fg-muted)]">
                                                    Champs de saisie RH déversant leurs montants dans cette règle lors des campagnes :
                                                </p>
                                                {linkedTypes.map(t => (
                                                    <div
                                                        key={t.id}
                                                        className="flex items-center justify-between gap-3 p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]"
                                                    >
                                                        <div>
                                                            <p className="text-xs font-bold text-[var(--color-fg)]">{t.libelle}</p>
                                                            <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">Code : {t.code}</p>
                                                        </div>
                                                        <Button
                                                            variant="outline"
                                                            size="xs"
                                                            iconRight={<ArrowRight size={12} />}
                                                            onClick={() => {
                                                                changeMode('types');
                                                                setDetailItem({ domaine: 'types', item: t });
                                                            }}
                                                        >
                                                            Ouvrir le type
                                                        </Button>
                                                    </div>
                                                ))}
                                            </div>
                                        )}
                                    </div>

                                    {/* 3. Description de la règle */}
                                    {rubrique.description && (
                                        <div>
                                            <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-1.5">
                                                Règle métier
                                            </h4>
                                            <p className="text-xs text-[var(--color-fg)] leading-relaxed bg-[var(--color-bg-subtle)] p-3 border border-[var(--color-border-subtle)]">
                                                {rubrique.description}
                                            </p>
                                        </div>
                                    )}

                                    {/* 4. Paramètres d'exécution du moteur */}
                                    <div>
                                        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-3">
                                            Paramètres du moteur
                                        </h4>
                                        <dl className="divide-y divide-[var(--color-border-subtle)] text-sm">
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Méthode</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{rubrique.methodeLibelle || rubrique.sourceCalcul || 'Montant saisi'}</dd>
                                            </div>
                                            <div className="flex justify-between gap-4 py-2">
                                                <dt className="text-[var(--color-fg-muted)] shrink-0">Arrondi</dt>
                                                <dd className="font-medium text-right min-w-0 break-words">{rubrique.arrondiLibelle || rubrique.arrondiCalculateur || 'Centime'}</dd>
                                            </div>
                                            {rubrique.conditionLibelle && (
                                                <div className="flex justify-between gap-4 py-2">
                                                    <dt className="text-[var(--color-fg-muted)] shrink-0">Condition</dt>
                                                    <dd className="font-medium text-right min-w-0 break-words">{rubrique.conditionLibelle}</dd>
                                                </div>
                                            )}
                                            {rubrique.dateDebutEffet && (
                                                <div className="flex justify-between gap-4 py-2">
                                                    <dt className="text-[var(--color-fg-muted)] shrink-0">Début d’effet</dt>
                                                    <dd className="font-medium text-right min-w-0 break-words">{fmtDate(rubrique.dateDebutEffet)}</dd>
                                                </div>
                                            )}
                                            {rubrique.dateFinEffet && (
                                                <div className="flex justify-between gap-4 py-2">
                                                    <dt className="text-[var(--color-fg-muted)] shrink-0">Fin d’effet</dt>
                                                    <dd className="font-medium text-right min-w-0 break-words">{fmtDate(rubrique.dateFinEffet)}</dd>
                                                </div>
                                            )}
                                            {rubrique.referenceReglementaire && (
                                                <div className="flex justify-between gap-4 py-2">
                                                    <dt className="text-[var(--color-fg-muted)] shrink-0">Référence légale</dt>
                                                    <dd className="font-medium text-right min-w-0 break-words">{rubrique.referenceReglementaire}</dd>
                                                </div>
                                            )}
                                        </dl>
                                    </div>
                                </div>
                            );
                        })()}
                    </aside>
                )}
            </SlideOver>

            {/* ═════════════════════════════════════════════════════════ */}
            {/* MODAL DE CRÉATION / MODIFICATION INTÉGRÉE                */}
            {/* ═════════════════════════════════════════════════════════ */}
            <FormModal
                noValidate
                isOpen={!!editor}
                onClose={() => { if (!submitLock.current) setEditor(null); }}
                onSubmit={handleSubmit}
                loading={saving}
                error={formError}
                title={
                    editor?.version
                        ? 'Nouvelle version de la rubrique'
                        : editor?.item
                            ? `Modifier ${isEditingTypes ? 'le type' : 'la rubrique'}`
                            : `Créer ${isEditingTypes ? 'un type de variable' : 'une règle manuelle'}`
                }
                size="4xl"
                submitLabel={editor?.version ? 'Enregistrer la nouvelle version' : 'Enregistrer'}
            >
                <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.6fr)_minmax(260px,1fr)] gap-8">
                    <div className="space-y-6 min-w-0">
                        <fieldset disabled={saving} className="space-y-4">
                            <legend className="font-semibold mb-3">1. Identification</legend>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Input
                                    label="Code"
                                    placeholder="Ex. PRIME_PERFORMANCE"
                                    value={form.code ?? ''}
                                    onChange={e => handleField('code', e.target.value.toUpperCase().replace(/\s+/g, '_'))}
                                    onBlur={() => handleBlur('code')}
                                    error={errors.code}
                                    disabled={isCodeLocked || treatmentLocked}
                                    required
                                    hint={
                                        isCodeLocked
                                            ? "Identifiant technique figé après création pour garantir l'historique des calculs."
                                            : "Identifiant en majuscules (ex : PRIME_PERFORMANCE). Figé après enregistrement."
                                    }
                                />
                                <Input
                                    label="Libellé"
                                    placeholder="Ex. Prime de performance"
                                    {...inputProps('libelle')}
                                    required
                                />
                            </div>
                            {isEditingTypes ? (
                                <SearchSelect
                                    label="Sens"
                                    value={form.sens ?? ''}
                                    onChange={value => {
                                        handleField('sens', value);
                                        const currentRub = availableRubriques.find(r => String(r.id) === String(form.idRubrique));
                                        if (currentRub && currentRub.sens !== value) {
                                            handleField('idRubrique', '');
                                        }
                                    }}
                                    disabled={identityLocked}
                                    options={modalOptions.sens}
                                    hint={
                                        identityLocked
                                            ? "Le sens (Gain / Retenue) est fixé à la création pour garantir l'exactitude de l'historique comptable."
                                            : "Un Gain s'ajoute à la rémunération (+), une Retenue en est déduite (-)."
                                    }
                                />
                            ) : (
                                <div className="space-y-4">
                                    {!editor?.item && (
                                        <>
                                            <SearchSelect
                                                label="Aide : que représente ce versement ?"
                                                value={rubriqueGuide}
                                                onChange={setRubriqueGuide}
                                                options={[
                                                    { value: 'remuneration', label: 'Complément de rémunération' },
                                                    { value: 'frais', label: 'Remboursement de frais engagés' },
                                                    { value: 'mixte', label: 'Frais et rémunération dans un même montant' },
                                                    { value: 'autre', label: 'Autre fondement à vérifier' },
                                                ]}
                                                placeholder="Choisir un cas"
                                            />
                                            {rubriqueGuide === 'remuneration' && (
                                                <p className="text-xs text-[var(--color-fg-muted)]">Une prime ou indemnité qui complète le salaire entre en principe dans le brut et les assiettes. Vérifiez les exceptions applicables.</p>
                                            )}
                                            {rubriqueGuide === 'frais' && (
                                                <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">Pour des frais ponctuels réellement remboursés, utilisez le circuit de notes de frais avec justificatif. Une rubrique manuelle ne vérifie pas les dépenses engagées.</p>
                                            )}
                                            {rubriqueGuide === 'mixte' && (
                                                <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">Séparez les deux montants : une rubrique applique le même traitement à toute la somme saisie.</p>
                                            )}
                                            {rubriqueGuide === 'autre' && (
                                                <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">Vérifiez le texte applicable et la nature du versement avant de choisir les assiettes.</p>
                                            )}
                                        </>
                                    )}
                                    <SearchSelect
                                        label="Nature"
                                        value={form.categorie ?? ''}
                                        disabled={treatmentLocked || !!editor?.version}
                                        options={modalOptions.categories}
                                        hint="Pré-remplit le traitement ; le nom de la catégorie ne prouve pas une exonération."
                                        onChange={value => {
                                            const cat = modalOptions.categories?.find(o => o.value === value);
                                            setForm(p => ({ ...p, categorie: value, ...cat?.defauts }));
                                        }}
                                    />
                                </div>
                            )}
                        </fieldset>

                        <fieldset
                            disabled={saving || (!isEditingTypes && treatmentLocked)}
                            className="space-y-4 border-t border-[var(--color-border-subtle)] pt-5"
                        >
                            <legend className="font-semibold pt-3">
                                2. {isEditingTypes ? 'Règle associée' : 'Calcul et assiettes'}
                            </legend>
                            {isEditingTypes ? (
                                <>
                                    <SearchSelect
                                        label="Rubrique de calcul"
                                        required
                                        value={form.idRubrique ?? ''}
                                        onChange={value => handleField('idRubrique', value)}
                                        disabled={!!editor?.item?.liaisonVerrouillee}
                                        error={errors.idRubrique}
                                        placeholder="Rechercher une rubrique manuelle…"
                                        hint="Le montant saisi hérite du brut/net et des assiettes de cette rubrique. Vérifiez son aperçu avant d'enregistrer."
                                        options={[
                                            ...availableRubriques
                                                .filter(r => (!form.sens || r.sens === form.sens)
                                                    && (!editor?.item || r.categorie === editor.item.categorie))
                                                .map(r => {
                                                    const assiettes = [
                                                        r.soumisIrsa ? 'IRSA' : null,
                                                        r.soumisCnaps ? 'CNaPS' : null,
                                                        r.soumisSante ? 'Santé' : null,
                                                        (r.soumisFmfp ?? r.soumisCnaps) ? 'FMFP' : null,
                                                    ].filter(Boolean).join(', ');
                                                    const fiscalInfo = assiettes ? `Soumis : ${assiettes}` : 'Hors charges et impôt';
                                                    return {
                                                        value: String(r.id),
                                                        label: r.libelle,
                                                        sublabel: `${r.code} · ${r.etapeLibelle || r.etapeCalcul} (${fiscalInfo})`,
                                                    };
                                                }),
                                            ...(editor?.item && editor.item.rubrique && !availableRubriques.some(r => r.id === editor.item.rubrique.id)
                                                ? [{ value: String(editor.item.rubrique.id), label: editor.item.rubrique.libelle, sublabel: editor.item.rubrique.code }]
                                                : []),
                                        ]}
                                    />
                                    <FieldError id="rubrique-field-error">{errors.idRubrique}</FieldError>
                                </>
                            ) : (
                                <>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <div>
                                            <SearchSelect
                                                label="Étape d'intervention"
                                                value={form.etapeCalcul ?? ''}
                                                placeholder="Choisir une étape"
                                                error={errors.etapeCalcul}
                                                options={modalOptions.etapes?.filter(o => o.sens === form.sens)}
                                                hint="Dans le brut : les assiettes se règlent ci-dessous. Au net : aucune assiette n'est alimentée."
                                                onChange={value => {
                                                    const stage = modalOptions.etapes?.find(o => o.value === value);
                                                    setForm(p => ({
                                                        ...p,
                                                        etapeCalcul: value,
                                                        ...(!stage?.assiettesModifiables
                                                            ? { soumisIrsa: false, soumisCnaps: false, soumisSante: false, soumisFmfp: false }
                                                            : {}),
                                                    }));
                                                }}
                                            />
                                            <FieldError id="etape-field-error">{errors.etapeCalcul}</FieldError>
                                        </div>
                                        <SearchSelect
                                            label="Méthode"
                                            value={form.methodeCalcul ?? ''}
                                            options={modalOptions.methodes}
                                            onChange={val => handleField('methodeCalcul', val)}
                                            hint="Formule utilisée pour déterminer le montant."
                                        />
                                    </div>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <SearchSelect
                                            label="Arrondi de la ligne"
                                            value={form.politiqueArrondi ?? ''}
                                            options={modalOptions.arrondis}
                                            onChange={val => handleField('politiqueArrondi', val)}
                                        />
                                        <SearchSelect
                                            label="Condition d'application"
                                            value={form.conditionApplication ?? ''}
                                            options={modalOptions.conditions}
                                            onChange={val => handleField('conditionApplication', val)}
                                        />
                                    </div>
                                    <div className="space-y-2 pt-1">
                                        <div className="flex items-center justify-between">
                                            <p className="text-sm font-semibold">Assiettes alimentées</p>
                                            {chosenStage?.assiettesModifiables ? (
                                                <span className="text-xs text-[var(--color-brand-600)] font-medium">Modifiable dans le brut</span>
                                            ) : (
                                                <span className="text-xs text-[var(--color-fg-muted)] font-medium">Étape au net</span>
                                            )}
                                        </div>

                                        {!chosenStage?.assiettesModifiables ? (
                                            <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                                Cette rubrique intervient après impôt (au net). Les cotisations sociales et l'IRSA ne s'appliquent pas à ce niveau. Pour assujettir cette rubrique, sélectionnez l'étape « Dans le brut ».
                                            </div>
                                        ) : (
                                            <p className="text-xs text-[var(--color-fg-muted)]">
                                                Sélectionnez les cotisations et impôts applicables à cette rubrique :
                                            </p>
                                        )}

                                        {(modalOptions.assiettes || []).map(({ key, label, heriteDe }) => {
                                            const isFmfp = key === 'soumisFmfp';
                                            const isDisabled = !chosenStage?.assiettesModifiables || treatmentLocked || isFmfp;
                                            return (
                                                <label
                                                    key={key}
                                                    className={`flex items-center justify-between gap-3 py-2.5 border-b border-[var(--color-border-subtle)] text-sm ${
                                                        isDisabled ? 'opacity-60 cursor-not-allowed' : 'cursor-pointer hover:bg-[var(--color-bg-subtle)]/50'
                                                    }`}
                                                >
                                                    <span>
                                                        {label}
                                                        {isFmfp ? ' · suit la CNaPS' : heriteDe && form[key] == null ? ' · Traitement hérité' : ''}
                                                    </span>
                                                    <input
                                                        type="checkbox"
                                                        className="w-4 h-4 accent-[var(--color-brand-500)] cursor-pointer disabled:cursor-not-allowed"
                                                        checked={Boolean(isFmfp ? form.soumisCnaps : form[key] ?? form[heriteDe])}
                                                        disabled={isDisabled}
                                                        onChange={e => handleField(key, e.target.checked)}
                                                    />
                                                </label>
                                            );
                                        })}
                                        {chosenStage?.assiettesModifiables && form.soumisSante !== form.soumisCnaps && (
                                            <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">
                                                Les assiettes Santé et CNaPS diffèrent. Vérifiez le régime applicable : l'assiette des services médicaux interentreprises suit normalement les salaires définis par le Code de prévoyance sociale.
                                            </p>
                                        )}
                                        {(!form.soumisIrsa || !form.soumisCnaps) && form.sens === 'GAIN' && (
                                            <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">
                                                Une exclusion d'assiette doit correspondre à la nature réelle du versement. Précisez le fondement du traitement et les justificatifs attendus.
                                            </p>
                                        )}
                                    </div>
                                </>
                            )}
                        </fieldset>

                        {!isEditingTypes && (
                            <fieldset
                                disabled={saving || treatmentLocked}
                                className="space-y-4 border-t border-[var(--color-border-subtle)] pt-5"
                            >
                                <legend className="font-semibold pt-3">3. Justification et validité</legend>
                                <Input
                                    label="Référence du traitement"
                                    {...inputProps('referenceReglementaire')}
                                    required
                                    placeholder="Texte, accord ou décision interne"
                                    hint="Indiquez le texte applicable. Une décision interne ne suffit pas à créer une exonération fiscale ou sociale."
                                />
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <Input
                                        label="Début d'application"
                                        type="date"
                                        {...inputProps('dateDebutEffet')}
                                        required={!editor?.item || !!editor.version}
                                    />
                                    <Input
                                        label="Fin d'application (facultative)"
                                        type="date"
                                        {...inputProps('dateFinEffet')}
                                        min={form.dateDebutEffet || undefined}
                                    />
                                </div>
                                <RubriqueCyclePreview start={form.dateDebutEffet} end={form.dateFinEffet} />
                            </fieldset>
                        )}

                        <Input
                            label="Ordre dans le catalogue"
                            type="number"
                            value={form.ordreAffichage ?? ''}
                            onChange={e => handleField('ordreAffichage', e.target.value === '' ? '' : Number(e.target.value))}
                            disabled={saving}
                            hint="Ordre d'affichage sur le bulletin et dans la grille de saisie."
                        />
                        <Textarea
                            label="Description et consignes"
                            placeholder="Précisez l’usage et les justificatifs attendus."
                            value={form.description || ''}
                            onChange={e => handleField('description', e.target.value)}
                            disabled={saving}
                            rows={3}
                        />
                        <label className="flex gap-3 items-center py-2 text-sm cursor-pointer">
                            <input
                                type="checkbox"
                                className="w-4 h-4 accent-[var(--color-brand-500)]"
                                checked={!!form.estActif}
                                disabled={saving}
                                onChange={e => handleField('estActif', e.target.checked)}
                            />
                            Actif dans le catalogue
                        </label>
                    </div>

                    {/* Aperçu à droite du modal */}
                    <aside className="border-l-2 border-[var(--color-brand-500)] pl-5 space-y-5 min-w-0">
                        <div>
                            <h4 className="font-semibold text-[var(--color-fg)]">
                                {editor?.version
                                    ? 'Évolution de la règle'
                                    : isEditingTypes
                                        ? 'Impact de la variable'
                                        : 'Traitement configuré'}
                            </h4>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-1.5 leading-relaxed">
                                {isEditingTypes
                                    ? 'Paramètres appliqués lors de la saisie de cette variable.'
                                    : 'Paramètres appliqués au calcul du bulletin.'}
                            </p>
                        </div>

                        {isEditingTypes ? (
                            (() => {
                                const selectedRub = availableRubriques.find(r => String(r.id) === String(form.idRubrique))
                                    || (data?.rubriques || []).find(r => String(r.id) === String(form.idRubrique))
                                    || (editor?.item?.rubrique);

                                if (!selectedRub) {
                                    return (
                                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-dashed border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] space-y-1 text-center">
                                            <p className="font-semibold text-[var(--color-fg)]">Aucune rubrique sélectionnée</p>
                                            <p>Sélectionnez une rubrique de calcul pour afficher ses paramètres fiscaux et sociaux.</p>
                                        </div>
                                    );
                                }

                                const stageLabel = selectedRub.etapeLibelle || selectedRub.etapeCalcul || 'Dans le brut';
                                const isBrut = (selectedRub.etapeCalcul || '').includes('BRUT') || stageLabel.toLowerCase().includes('brut');

                                return (
                                    <div className="space-y-4">
                                        {/* Parcours résumé */}
                                        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1">
                                            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                                Règle moteur associée
                                            </span>
                                            <p className="text-xs font-bold text-[var(--color-fg)]">{selectedRub.libelle}</p>
                                            <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">{selectedRub.code}</p>
                                        </div>

                                        <RubriqueCyclePreview start={selectedRub.dateDebutEffet} end={selectedRub.dateFinEffet} />

                                        <dl className="space-y-2.5 text-xs">
                                            <div className="flex justify-between gap-3">
                                                <dt className="text-[var(--color-fg-muted)]">Sens comptable</dt>
                                                <dd className="font-medium text-right">
                                                    {form.sens === 'GAIN' ? '+ Gain (ajoute au salaire)' : '- Retenue (déduit du salaire)'}
                                                </dd>
                                            </div>
                                            <div className="flex justify-between gap-3">
                                                <dt className="text-[var(--color-fg-muted)]">Étape d’intervention</dt>
                                                <dd className="font-medium text-right">{stageLabel}</dd>
                                            </div>
                                            <div className="flex justify-between gap-3">
                                                <dt className="text-[var(--color-fg-muted)]">Méthode de calcul</dt>
                                                <dd className="font-medium text-right">
                                                    {selectedRub.methodeLibelle || selectedRub.sourceCalcul || 'Montant saisi en campagne'}
                                                </dd>
                                            </div>
                                        </dl>

                                        {/* Bloc Assiettes pour la variable */}
                                        <div className="pt-3 border-t border-[var(--color-border-subtle)] space-y-2">
                                            <span className="text-xs font-semibold text-[var(--color-fg)] block">
                                                Cotisations et fiscalité associées
                                            </span>
                                            {isBrut ? (
                                                <div className="grid grid-cols-2 gap-1.5 text-xs">
                                                    {[
                                                        { k: 'IRSA', v: selectedRub.soumisIrsa },
                                                        { k: 'CNaPS', v: selectedRub.soumisCnaps },
                                                        { k: 'Santé', v: selectedRub.soumisSante },
                                                        { k: 'FMFP', v: selectedRub.soumisFmfp ?? selectedRub.soumisCnaps },
                                                    ].map(({ k, v }) => (
                                                        <div
                                                            key={k}
                                                            className={`p-1.5 border text-center flex items-center justify-between px-2 ${
                                                                v
                                                                    ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)]/30 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]'
                                                                    : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] opacity-60'
                                                            }`}
                                                        >
                                                            <span className="font-bold">{k}</span>
                                                            <span className="text-[10px] uppercase font-semibold">
                                                                {v ? 'Soumis' : 'Hors assiette'}
                                                            </span>
                                                        </div>
                                                    ))}
                                                </div>
                                            ) : (
                                                <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)]">
                                                    Rubrique au net : non soumise aux cotisations sociales et à l’IRSA.
                                                </div>
                                            )}
                                            {form.sens === 'GAIN' && (!selectedRub.soumisIrsa || !selectedRub.soumisCnaps) && (
                                                <p className="text-xs text-[var(--color-warning-800)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] p-3">
                                                    Cette variable hérite d'une exclusion d'assiette. Vérifiez que le versement et ses justificatifs correspondent au traitement de la rubrique liée.
                                                </p>
                                            )}
                                        </div>
                                    </div>
                                );
                            })()
                        ) : (
                            <div className="space-y-4">
                                <dl className="space-y-3 text-sm">
                                    {[
                                        ['Intervention', form.etapeCalcul || chosenStage?.label],
                                        ['Méthode', modalOptions.methodes?.find(o => o.value === form.methodeCalcul)?.label || form.methodeCalcul],
                                        ['Arrondi', modalOptions.arrondis?.find(o => o.value === form.politiqueArrondi)?.label || form.politiqueArrondi],
                                        ['Application', modalOptions.conditions?.find(o => o.value === form.conditionApplication)?.label || form.conditionApplication],
                                    ].filter(([, v]) => v).map(([l, val]) => (
                                        <div key={l} className="flex justify-between gap-4">
                                            <dt className="text-[var(--color-fg-muted)]">{l}</dt>
                                            <dd className="text-right font-medium">{val}</dd>
                                        </div>
                                    ))}
                                </dl>
                            </div>
                        )}

                        {treatmentLocked && (
                            <p className="text-xs text-[var(--color-fg-muted)] mt-2">
                                Le traitement est verrouillé par l’historique. Vous pouvez modifier le libellé et les consignes ; utilisez une nouvelle version pour changer la règle.
                            </p>
                        )}
                    </aside>
                </div>
            </FormModal>
        </div>
    );
}
