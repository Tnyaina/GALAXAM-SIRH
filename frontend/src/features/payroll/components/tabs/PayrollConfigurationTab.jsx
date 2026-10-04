import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowLeft, ArrowRight, GitBranch, Plus, RefreshCw, Search, Settings2 } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import FieldError from '@/components/ui/forms/FieldError';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import FormModal from '@/components/ui/overlays/FormModal';
import Pagination from '@/components/ui/navigation/Pagination';
import useDebounce from '@/hooks/useDebounce';
import usePayrollConfiguration from '@/features/payroll/hooks/usePayrollConfiguration';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import { fmtDate } from '@/utils/dateUtils';
import RubriqueCyclePreview from '@/features/payroll/components/RubriqueCyclePreview';

export default function PayrollConfigurationTab({ domaine = 'rubriques' }) {
    const [params, setParams] = useSearchParams();
    const navigate = useNavigate();
    const location = useLocation();
    const toast = useToast();
    const { hasPermission, hasRole } = useAuth();
    const isTypes = domaine === 'types';
    const canWrite = (hasRole ? hasRole('ROLE_ADMIN') : hasPermission('ROLE_ADMIN')) || (isTypes && hasPermission('PAIE_WRITE'));
    const prefix = isTypes ? 'vt_' : 'rb_';
    const read = (key, fallback = '') => params.get(prefix + key) || fallback;
    const query = read('q');
    const debouncedQuery = useDebounce(query, 300);
    const selectedId = Number(read('selection')) || undefined;
    const { data, loading, refreshing, error, refresh } = usePayrollConfiguration({
        domaine, recherche: debouncedQuery, sens: read('sens'), niveau: read('niveau'), statut: read('statut'),
        tri: read('tri', 'ordre'), page: Number(read('page', '1')), taille: Number(read('taille', '10')), selection: selectedId,
    });
    const [editor, setEditor] = useState(null);
    const [form, setForm] = useState({});
    const [errors, setErrors] = useState({});
    const [formError, setFormError] = useState('');
    const [saving, setSaving] = useState(false);
    const submitLock = useRef(false);
    useEffect(() => {
        if (!editor) return;
        const previous = document.activeElement;
        const dialog = document.querySelector('[role="dialog"]');
        const focusable = () => [...dialog.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]')].filter(element => !element.closest('fieldset:disabled'));
        focusable().find(element => element.tagName === 'INPUT')?.focus();
        const trap = event => {
            if (event.key !== 'Tab') return;
            const controls = focusable();
            const first = controls[0], last = controls.at(-1);
            if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
            else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
        };
        dialog.addEventListener('keydown', trap);
        return () => { dialog.removeEventListener('keydown', trap); previous?.focus(); };
    }, [editor]);
    const items = data?.items || [];
    const options = data?.options || {};
    const selected = data?.selection || (selectedId ? items.find(i => i.id === selectedId) : items[0]);
    const detailRef = useRef(null);
    useEffect(() => {
        if (selected?.id === selectedId && window.matchMedia?.('(max-width: 1279px)').matches) {
            detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }, [selected?.id, selectedId]);
    const associated = isTypes ? selected?.rubrique : selected;
    const treatmentLocked = editor?.item?.traitementVerrouille && !editor?.version;
    const identityLocked = isTypes && !!editor?.item;
    const chosenStage = options.etapes?.find(option => option.value === form.etapeCalcul);
    const selectedRubrique = data?.rubriquesCompatibles?.find(r => Number(r.id) === Number(form.idRubrique)) || selected?.rubrique;
    const returnTo = location.state?.from?.startsWith('/paie/') ? location.state.from : null;

    useEffect(() => {
        if (!isTypes || !data || params.get('vt_creer') !== '1') return;
        setForm({ code: '', libelle: '', description: '',
            ...data.defautsType, idRubrique: params.get('vt_rubrique') || '' });
        setEditor({ item: null, version: false });
    }, [isTypes, data, params]);

    function handleFilter(key, value) {
        setParams(previous => {
            const next = new URLSearchParams(previous);
            value ? next.set(prefix + key, String(value)) : next.delete(prefix + key);
            if (key !== 'page' && key !== 'selection') next.delete(prefix + 'page');
            return next;
        }, { replace: true });
    }
    function handleOpen(item = null, version = false) {
        if (!data || refreshing) return;
        const defaults = isTypes ? data.defautsType : data.defautsRubrique;
        setForm({ code: '', libelle: '', description: '', referenceReglementaire: '', dateDebutEffet: '', dateFinEffet: '', idRubrique: '',
            ...defaults, ...item, ...(version ? { code: '', dateDebutEffet: '', dateFinEffet: '', idRubriqueOrigine: undefined } : {}) });
        setEditor({ item, version }); setErrors({}); setFormError('');
    }
    function validateField(name, value, draft = form) {
        if (treatmentLocked && name !== 'libelle') return '';
        if (['code', 'libelle'].includes(name) && !String(value || '').trim()) return 'Renseignez ce champ.';
        if (name === 'code' && value && !new RegExp(`^${data.validation.codePattern}$`).test(value)) return data.validation.codeMessage;
        if (!isTypes && name === 'referenceReglementaire' && !String(value || '').trim()) return 'Indiquez le texte, l’accord ou la décision qui justifie le traitement.';
        if (!isTypes && name === 'dateDebutEffet' && !value && (!editor?.item || editor.version)) return 'Choisissez la date de début d’application.';
        if (name === 'dateFinEffet' && value && (!draft.dateDebutEffet || value < draft.dateDebutEffet)) return 'La date de fin doit suivre la date de début.';
        if (!isTypes && name === 'etapeCalcul' && !options.etapes?.some(option => option.value === value && option.sens === draft.sens)) return 'Choisissez l’étape d’intervention.';
        if (isTypes && name === 'idRubrique' && !value) return 'Choisissez la rubrique de calcul.';
        return '';
    }
    function handleField(name, value) {
        setForm(previous => ({ ...previous, [name]: value }));
        if (errors[name]) setErrors(previous => ({ ...previous, [name]: validateField(name, value) }));
    }
    function handleBlur(name) { setErrors(previous => ({ ...previous, [name]: validateField(name, form[name]) })); }
    function handleClose() { if (!submitLock.current) setEditor(null); }
    async function handleSubmit(event) {
        event.preventDefault();
        if (submitLock.current || saving) return;
        const fields = isTypes ? ['code','libelle','idRubrique'] : ['code','libelle','etapeCalcul','referenceReglementaire','dateDebutEffet','dateFinEffet'];
        const invalid = Object.fromEntries(fields.map(name => [name, validateField(name, form[name])]));
        setErrors(invalid);
        if (Object.values(invalid).some(Boolean)) return;
        submitLock.current = true; setSaving(true); setFormError('');
        try {
            const payload = { ...form, code: form.code.trim(), libelle: form.libelle.trim() };
            let saved;
            if (isTypes) {
                payload.idRubrique = Number(form.idRubrique);
                payload.categorie = editor.item?.categorie || options.sens.find(o => o.value === form.sens)?.categorieType;
                if (editor.item) saved = await paieService.modifierTypeVariable(editor.item.id, payload);
                else saved = await paieService.creerTypeVariable(payload);
            } else {
                payload.dateDebutEffet = form.dateDebutEffet || null;
                payload.dateFinEffet = form.dateFinEffet || null;
                if (editor.version) saved = await paieService.creerVersionRubrique(editor.item.id, payload);
                else if (editor.item) saved = await paieService.modifierRubriqueVariable(editor.item.id, payload);
                else saved = await paieService.creerRubriqueVariable(payload);
            }
            const savedId = isTypes ? saved?.idTypeMouvement : saved?.idRubrique;
            if (savedId) handleFilter('selection', savedId);
            setEditor(null); refresh();
            toast.success(editor.version ? 'Nouvelle version enregistrée.' : 'Configuration enregistrée.');
        } catch (cause) {
            setFormError(getErrorMessage(cause, 'L’enregistrement n’a pas abouti. Vos saisies sont conservées ; vérifiez les champs puis réessayez.'));
        } finally { submitLock.current = false; setSaving(false); }
    }
    const selectProps = name => ({ value: form[name] ?? '', onChange: value => handleField(name,value), onBlur: () => handleBlur(name), className: 'min-h-[44px]' });
    const inputProps = name => ({ value: form[name] ?? '', onChange: e => handleField(name,e.target.value), onBlur: () => handleBlur(name), error: errors[name], className: 'min-h-[44px]' });
    const clearFilters = () => setParams(previous => { const next = new URLSearchParams(previous); ['q','sens','niveau','statut','page'].forEach(k => next.delete(prefix+k)); return next; });

    return <div className="space-y-5 text-[var(--color-fg)]">
        {returnTo && <Button variant="ghost" className="min-h-[44px]" iconLeft={<ArrowLeft size={16} />} onClick={() => navigate(returnTo)}>Retour à la campagne</Button>}
        <header className="flex flex-wrap items-start justify-between gap-4">
            <div><h2 className="text-xl font-semibold">{isTypes ? 'Types de variables' : 'Rubriques et règles de calcul'}</h2>
                <p className="text-sm text-[var(--color-fg-muted)] mt-1 max-w-3xl">{isTypes ? 'Définissez les éléments saisissables et associez chacun à sa règle de calcul.' : 'Consultez les traitements, leurs assiettes et leurs versions avant de modifier une règle.'}</p></div>
            <Button variant="ghost" className="min-h-[44px]" loading={refreshing && !loading} iconLeft={<RefreshCw size={16} />} onClick={refresh}>Actualiser</Button>
        </header>
        <div className="space-y-2 border-l-2 border-[var(--color-brand-500)] pl-4 py-1 text-sm text-[var(--color-fg-muted)]">
            <p><span className="font-semibold text-[var(--color-fg)]">Cas courant :</span> {isTypes ? 'Le type définit la saisie. La rubrique associée définit le calcul et le traitement fiscal et social.' : 'Les règles manuelles sont configurables ici. Les calculateurs dédiés utilisent les paramètres de leur domaine.'}</p>
            <p><span className="font-semibold text-[var(--color-fg)]">Cas exceptionnel :</span> traitement absent du catalogue → nouvelle règle manuelle.</p>
        </div>
        {error && <Alert type="error" title="Catalogue indisponible" message={error} />}
        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.4fr)_minmax(340px,1fr)] gap-5 items-start">
            <section aria-label="Catalogue" aria-busy={loading || refreshing} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] min-w-0">
                <div className="p-4 space-y-4">
                    <div className="flex items-center justify-between gap-3"><h3 className="font-semibold">Catalogue <span className="text-sm font-normal text-[var(--color-fg-muted)]">{data ? `· ${data.totalElements}` : ''}</span></h3>
                        {canWrite && <Button disabled={refreshing || !data} className="min-h-[44px]" iconLeft={<Plus size={16} />} onClick={() => handleOpen()}>{isTypes ? 'Nouveau type' : 'Nouvelle règle manuelle'}</Button>}</div>
                    <Input aria-label="Rechercher dans le catalogue" placeholder="Rechercher un code ou un libellé…" iconLeft={<Search size={16} />} value={query} onChange={e => handleFilter('q',e.target.value)} className="min-h-[44px]" />
                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                        <Select aria-label="Filtrer par sens" value={read('sens')} onChange={v => handleFilter('sens',v)} options={[{value:'',label:'Gains et retenues'},...(options.sens || [])]} className="min-h-[44px]" />
                        <Select aria-label="Filtrer par statut" value={read('statut')} onChange={v => handleFilter('statut',v)} options={[{value:'',label:'Tous les statuts'},...(options.statuts || [])]} className="min-h-[44px]" />
                        {!isTypes && <Select aria-label="Filtrer par niveau de configuration" value={read('niveau')} onChange={v => handleFilter('niveau',v)} options={[{value:'',label:'Tous les niveaux'},...(options.niveaux || [])]} className="min-h-[44px]" />}
                    </div>
                    <Select label="Trier par" value={read('tri','ordre')} onChange={v => handleFilter('tri',v)} options={options.tris || []} className="min-h-[44px]" />
                </div>
                {loading ? <SkeletonTable rows={5} columns={3} bordered={false} /> : error && !data ? <p className="p-5 text-sm text-[var(--color-fg-muted)]">Le catalogue n’a pas été chargé. Utilisez Actualiser pour réessayer.</p> : items.length === 0 ? <EmptyState bordered={false} icon={Settings2} title="Aucun résultat" description="Aucun élément ne correspond à cette sélection." actionLabel={query || read('sens') || read('niveau') || read('statut') ? 'Effacer les filtres' : canWrite ? 'Créer un élément' : undefined} onAction={query || read('sens') || read('niveau') || read('statut') ? clearFilters : () => handleOpen()} /> : <>
                    <ul aria-label={isTypes ? 'Types de variables' : 'Rubriques de paie'} className="divide-y divide-[var(--color-border-subtle)]">
                        {items.map(item => <li key={item.id}><button type="button" aria-pressed={selected?.id === item.id} onClick={() => handleFilter('selection',item.id)}
                            className={`w-full min-h-[70px] text-left px-4 py-3 flex items-center gap-3 border-l-[3px] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)] ${selected?.id === item.id ? 'border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)]' : 'border-transparent hover:bg-[var(--color-bg-subtle)]'}`}>
                            <span className="min-w-0 flex-1"><span className="block text-sm font-semibold leading-snug">{item.libelle}</span>
                                <span className="block text-xs font-data text-[var(--color-fg-muted)] mt-1">{item.code} · {isTypes ? item.rubrique?.etapeLibelle || item.niveauLibelle : item.etapeLibelle}</span></span>
                            <span className="shrink-0"><Badge variant={item.estActif ? 'success' : 'neutral'}>{item.estActif ? 'Actif' : 'Inactif'}</Badge></span>
                        </button></li>)}
                    </ul>
                    <Pagination currentPage={data.page} totalElements={data.totalElements} pageSize={data.taille} pageSizeOptions={[10,25,50]} onPageChange={v => handleFilter('page',v)} onPageSizeChange={v => handleFilter('taille',v)} className="[&_button]:!min-h-[44px] [&_button]:!min-w-[44px] [&_select]:min-h-[44px]" />
                </>}
            </section>
            <section ref={detailRef} aria-label="Fiche de configuration" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 sm:p-6 min-w-0 xl:sticky xl:top-4">
                {!selected ? <EmptyState bordered={false} icon={Settings2} title="Choisissez un élément" description="Sélectionnez une ligne du catalogue pour consulter son traitement et ses paramètres." /> : <>
                    <div className="space-y-2"><Badge variant="neutral">{selected.niveauLibelle}</Badge><h3 className="text-xl font-semibold break-words">{selected.libelle}</h3><p className="text-xs font-data text-[var(--color-fg-muted)]">{selected.code}</p></div>
                    {selected.description && <p className="text-sm leading-relaxed text-[var(--color-fg-muted)] mt-4">{selected.description}</p>}
                    <ConfigurationDetailsView rubrique={associated} assiettes={options.assiettes} />
                    {!isTypes && <div className="border-t border-[var(--color-border-subtle)] pt-4 mt-5"><h4 className="font-semibold text-sm mb-3">Validité et versions</h4><p className="text-sm">{selected.dateDebutEffet ? `Du ${fmtDate(selected.dateDebutEffet)}` : 'Début historique non renseigné'}{selected.dateFinEffet ? ` au ${fmtDate(selected.dateFinEffet)}` : ' · Sans date de fin'}</p>
                        {selected.referenceReglementaire && <p className="text-sm text-[var(--color-fg-muted)] mt-2">{selected.referenceReglementaire}</p>}
                        {data.famille?.length > 1 && <ol className="mt-3 space-y-3">{data.famille.map(version => <li key={version.id} className="text-sm"><Button variant="ghost" className="min-h-[44px] !normal-case" onClick={() => handleFilter('selection',version.id)}>{version.code} · {version.dateDebutEffet ? fmtDate(version.dateDebutEffet) : 'Historique'}</Button></li>)}</ol>}
                    </div>}
                    <div className="flex flex-wrap gap-2 border-t border-[var(--color-border-subtle)] pt-4 mt-5">
                        {canWrite && selected.peutModifier && <Button disabled={refreshing} variant="outline" className="min-h-[44px]" onClick={() => handleOpen(selected)}>Modifier</Button>}
                        {canWrite && selected.peutVersionner && <Button disabled={refreshing} className="min-h-[44px]" iconLeft={<GitBranch size={16} />} onClick={() => handleOpen(selected,true)}>Nouvelle version</Button>}
                        {!isTypes && !selected.peutModifier && selected.parametrageUrl && <Button variant="outline" className="min-h-[44px]" iconRight={<ArrowRight size={16} />} onClick={() => navigate(selected.parametrageUrl)}>Ouvrir le paramétrage dédié</Button>}
                    </div>
                </>}
            </section>
        </div>
        <FormModal noValidate isOpen={!!editor} onClose={handleClose} onSubmit={handleSubmit} loading={saving} error={formError} title={editor?.version ? 'Nouvelle version de la rubrique' : editor?.item ? `Modifier ${isTypes ? 'le type' : 'la rubrique'}` : `Créer ${isTypes ? 'un type de variable' : 'une rubrique'}`} size="4xl" className="[&_button]:min-h-[44px] [&_button]:min-w-[44px]" submitLabel={editor?.version ? 'Enregistrer la nouvelle version' : 'Enregistrer'}>
            <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.6fr)_minmax(260px,1fr)] gap-8">
                <div className="space-y-6 min-w-0">
                    <fieldset disabled={saving} className="space-y-4"><legend className="font-semibold mb-3">1. Identification</legend>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4"><Input label="Code" placeholder="Ex. PRIME_PERFORMANCE" {...inputProps('code')} disabled={identityLocked || treatmentLocked} required hint="Identifiant stable de la règle." /><Input label="Libellé" placeholder="Ex. Prime de performance" {...inputProps('libelle')} required /></div>
                        {isTypes ? <Select label="Sens" {...selectProps('sens')} disabled={identityLocked} options={options.sens} /> : <Select label="Nature" {...selectProps('categorie')} disabled={treatmentLocked || !!editor?.version} options={options.categories} onChange={value => { const category = options.categories.find(o => o.value === value); setForm(previous => ({...previous,categorie:value,...category.defauts})); }} />}
                    </fieldset>
                    <fieldset disabled={saving || (!isTypes && treatmentLocked)} className="space-y-4 border-t border-[var(--color-border-subtle)] pt-5"><legend className="font-semibold pt-3">2. {isTypes ? 'Règle associée' : 'Calcul et assiettes'}</legend>
                        {isTypes ? <>
                            <SearchSelect
                                label="Rubrique de calcul"
                                required
                                value={form.idRubrique ?? ''}
                                onChange={value => handleField('idRubrique', value)}
                                disabled={!!editor?.item?.liaisonVerrouillee}
                                error={errors.idRubrique}
                                placeholder="Rechercher une rubrique manuelle…"
                                options={[
                                    ...(data?.rubriquesCompatibles || []).filter(r => r.sens === form.sens).map(r => ({
                                        value: String(r.id),
                                        label: r.libelle,
                                        sublabel: `${r.code} · ${r.etapeLibelle || r.etapeCalcul}`,
                                    })),
                                    ...(selected?.rubrique && !data?.rubriquesCompatibles?.some(r => r.id === selected.rubrique.id)
                                        ? [{ value: String(selected.rubrique.id), label: selected.rubrique.libelle }]
                                        : []),
                                ]}
                            />
                            <FieldError id="rubrique-field-error">{errors.idRubrique}</FieldError>
                        </> : <>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4"><div><Select label="Étape d’intervention" {...selectProps('etapeCalcul')} placeholder="Choisir une étape" aria-invalid={!!errors.etapeCalcul} aria-describedby="etape-field-error" options={options.etapes?.filter(o => o.sens === form.sens)} onChange={value => { const stage=options.etapes.find(o => o.value === value); setForm(previous => ({...previous,etapeCalcul:value,...(!stage?.assiettesModifiables ? {soumisIrsa:false,soumisCnaps:false,soumisSante:false,soumisFmfp:false} : {})})); }} /><FieldError id="etape-field-error">{errors.etapeCalcul}</FieldError></div><Select label="Méthode" {...selectProps('methodeCalcul')} options={options.methodes} /></div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4"><Select label="Arrondi de la ligne" {...selectProps('politiqueArrondi')} options={options.arrondis} /><Select label="Condition d’application" {...selectProps('conditionApplication')} options={options.conditions} /></div>
                            <div className="space-y-1"><p className="text-sm font-semibold">Assiettes alimentées</p><p className="text-xs text-[var(--color-fg-muted)]">Chaque inclusion s’applique à cette rubrique. L’arrondi final du net reste indépendant.</p>
                                {(options.assiettes || []).map(({key,label,heriteDe}) => <label key={key} className="flex items-center justify-between gap-3 min-h-[44px] border-b border-[var(--color-border-subtle)] text-sm"><span>{label}{heriteDe && form[key] == null ? ' · Traitement hérité'  : ''}</span><input type="checkbox" className="w-5 h-5 accent-[var(--color-brand-500)]" checked={Boolean(form[key] ?? form[heriteDe])} disabled={!chosenStage?.assiettesModifiables} onChange={e => handleField(key,e.target.checked)} /></label>)}
                            </div>
                        </>}
                    </fieldset>
                    {!isTypes && <fieldset disabled={saving || treatmentLocked} className="space-y-4 border-t border-[var(--color-border-subtle)] pt-5"><legend className="font-semibold pt-3">3. Justification et validité</legend><Input label="Référence du traitement" {...inputProps('referenceReglementaire')} required placeholder="Texte, accord ou décision interne" /><div className="grid grid-cols-1 sm:grid-cols-2 gap-4"><Input label="Début d’application" type="date" {...inputProps('dateDebutEffet')} required={!editor?.item || !!editor.version} /><Input label="Fin d’application (facultative)" type="date" {...inputProps('dateFinEffet')} min={form.dateDebutEffet || undefined} /></div><RubriqueCyclePreview start={form.dateDebutEffet} end={form.dateFinEffet} /></fieldset>}
                    <Input label="Ordre dans le catalogue" type="number" value={form.ordreAffichage ?? ''} onChange={e => handleField('ordreAffichage', e.target.value === '' ? '' : Number(e.target.value))} disabled={saving} className="min-h-[44px]" hint="Priorité utilisée lorsque le catalogue est trié par ordre." /><Textarea label="Description et consignes" placeholder="Précisez l’usage et les justificatifs attendus." value={form.description || ''} onChange={e => handleField('description',e.target.value)} disabled={saving} rows={3} />
                    <label className="flex gap-3 items-center min-h-[44px] text-sm"><input type="checkbox" className="w-5 h-5 accent-[var(--color-brand-500)]" checked={!!form.estActif} disabled={saving} onChange={e => handleField('estActif',e.target.checked)} />Actif dans le catalogue</label>
                </div>
                <aside className="border-l-2 border-[var(--color-brand-500)] pl-5 space-y-5 min-w-0"><div><h4 className="font-semibold">{editor?.version ? 'Évolution de la règle' : 'Traitement configuré'}</h4><p className="text-sm text-[var(--color-fg-muted)] mt-2">{isTypes ? 'Le montant saisi suivra la rubrique associée.' : 'Vérifiez l’intervention dans la paie et les assiettes avant d’enregistrer.'}</p></div>
                    <ConfigurationDetailsView rubrique={isTypes ? selectedRubrique : {...form,etapeLibelle:chosenStage?.label,methodeLibelle:options.methodes?.find(o=>o.value===form.methodeCalcul)?.label,arrondiLibelle:options.arrondis?.find(o=>o.value===form.politiqueArrondi)?.label,conditionLibelle:options.conditions?.find(o=>o.value===form.conditionApplication)?.label}} assiettes={options.assiettes} />
                    {treatmentLocked && <p className="text-sm text-[var(--color-fg-muted)]">Le traitement est verrouillé par l’historique. Vous pouvez modifier le libellé et les consignes ; utilisez une nouvelle version pour changer la règle.</p>}
                    {isTypes && editor?.item?.liaisonVerrouillee && <p className="text-sm text-[var(--color-fg-muted)]">La rubrique est conservée : ce type possède des mouvements ou suit un circuit dédié.</p>}
                    {editor?.version && <div className="space-y-3 text-sm border-t border-[var(--color-border-subtle)] pt-4"><h5 className="font-semibold">Version précédente</h5><p>{editor.item.code} · {editor.item.etapeLibelle}</p><details><summary className="cursor-pointer min-h-[44px] flex items-center">Consulter le traitement précédent</summary><ConfigurationDetailsView rubrique={editor.item} assiettes={options.assiettes} /></details><p>La nouvelle date d’effet clôture la version précédente à la veille. Les périodes historiques restent protégées.</p></div>}
                </aside>
            </div>
        </FormModal>
    </div>;
}

function ConfigurationDetailsView({ rubrique, assiettes = [] }) {
    if (!rubrique) return <p className="text-sm text-[var(--color-fg-muted)] mt-4">Choisissez une rubrique pour afficher son traitement.</p>;
    return <div className="space-y-4 mt-5">
        <dl className="space-y-3 text-sm">{[['Intervention',rubrique.etapeLibelle || rubrique.phaseCalcul],['Méthode',rubrique.methodeLibelle || rubrique.sourceCalcul],['Arrondi',rubrique.arrondiLibelle || rubrique.arrondiCalculateur],['Application',rubrique.conditionLibelle]].filter(([,value])=>value).map(([label,value])=><div key={label} className="flex justify-between gap-4"><dt className="text-[var(--color-fg-muted)]">{label}</dt><dd className="text-right font-medium">{value}</dd></div>)}</dl>
        <div className="border-t border-[var(--color-border-subtle)] pt-4"><h4 className="text-sm font-semibold mb-2">Traitement fiscal et social</h4><dl className="space-y-2 text-sm">{assiettes.map(({key,label,heriteDe})=><div key={label} className="flex justify-between gap-4"><dt className="text-[var(--color-fg-muted)]">{label}</dt><dd>{(rubrique[key] ?? rubrique[heriteDe]) ? 'Dans l’assiette' : 'Hors assiette'}</dd></div>)}</dl></div>
    </div>;
}
