import { useCallback, useEffect, useMemo, useState } from 'react';
import { ArrowLeft, CheckCircle2, Plus, RefreshCw, Search, Settings2, ShieldCheck } from 'lucide-react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '@/context/AuthContext';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { getErrorMessage } from '@/api/apiClient';
import paieService from '@/features/payroll/api/paieService';

const emptyType = {
    code: '', libelle: '', sens: 'GAIN', categorie: 'PRIME', idRubrique: '',
    description: '', estProratise: false, estPlafonneDixieme: false,
    estActif: true, ordreAffichage: 100,
};

export default function VariableTypesConfig() {
    const navigate = useNavigate();
    const location = useLocation();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE') || hasPermission('ROLE_ADMIN');
    const returnTo = location.state?.from?.startsWith('/paie/') ? location.state.from : null;
    const [types, setTypes] = useState([]);
    const [rubriques, setRubriques] = useState([]);
    const [selectedId, setSelectedId] = useState(null);
    const [form, setForm] = useState(emptyType);
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [query, setQuery] = useState('');

    const load = useCallback(async () => {
        setLoading(true);
        setError('');
        try {
            const catalogue = await paieService.getCatalogueVariables();
            setTypes(Array.isArray(catalogue?.types) ? catalogue.types : []);
            setRubriques(Array.isArray(catalogue?.rubriques) ? catalogue.rubriques : []);
        } catch (cause) {
            setError(getErrorMessage(cause, 'Impossible de charger le catalogue.'));
        } finally {
            setLoading(false);
        }
    }, []);
    useEffect(() => { load(); }, [load]);

    const selectedType = types.find(type => type.id === selectedId);
    const selectedRubrique = rubriques.find(rubrique => Number(rubrique.id) === Number(form.idRubrique));
    const compatibleRubriques = useMemo(() => rubriques.filter(rubrique =>
        (rubrique.sens === form.sens && rubrique.estActif && !rubrique.estFixe && rubrique.typeValeur === 'MONTANT')
            || Number(rubrique.id) === Number(form.idRubrique)
    ), [rubriques, form.sens, form.idRubrique]);
    const filteredTypes = useMemo(() => types.filter(type => `${type.libelle} ${type.code}`.toLowerCase().includes(query.toLowerCase().trim())), [types, query]);
    const isSpecial = selectedType && selectedType.nature !== 'VARIABLE_DAF';

    const selectType = (type) => {
        setSelectedId(type?.id ?? null);
        setForm(type ? {
            code: type.code, libelle: type.libelle, sens: type.sens,
            categorie: type.categorie, idRubrique: type.idRubrique ?? '',
            description: type.description ?? '', estProratise: Boolean(type.estProratise),
            estPlafonneDixieme: Boolean(type.estPlafonneDixieme), estActif: Boolean(type.estActif),
            ordreAffichage: type.ordreAffichage ?? 100,
        } : emptyType);
        setError('');
        setNotice('');
    };

    const save = async (event) => {
        event.preventDefault();
        if (!form.code.trim() || !form.libelle.trim() || !form.idRubrique) {
            setError('Renseignez le code, le libellé et la rubrique de calcul.');
            return;
        }
        if (!selectedRubrique || (selectedRubrique.sens !== form.sens && Number(selectedRubrique.id) !== Number(selectedType?.idRubrique))) {
            setError('La rubrique doit avoir le même sens que le type de variable.');
            return;
        }
        setSaving(true);
        setError('');
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
            setTypes(updatedTypes);
            setRubriques(Array.isArray(catalogue?.rubriques) ? catalogue.rubriques : []);
            selectType(updatedTypes.find(type => type.code === saved.code));
            setNotice('Type enregistré dans le catalogue.');
        } catch (cause) {
            setError(getErrorMessage(cause, 'Enregistrement du type impossible.'));
        } finally {
            setSaving(false);
        }
    };

    return (
        <section className="w-full space-y-5 pb-8">
            {returnTo && <button type="button" onClick={() => navigate(returnTo)} className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-700)] hover:underline"><ArrowLeft size={16} /> Retour aux variables de la campagne</button>}
            <header className="flex flex-wrap items-start justify-between gap-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-5 sm:p-7">
                <div className="flex items-start gap-3"><span className="bg-[var(--color-brand-500)]/10 p-2.5 text-[var(--color-brand-700)]"><Settings2 size={22} /></span><div>
                    <p className="text-xs font-bold uppercase tracking-widest text-[var(--color-brand-700)]">Paramétrage de paie</p>
                    <h1 className="mt-1 text-2xl font-bold text-[var(--color-fg)]">Types de variables</h1>
                    <p className="mt-1 max-w-2xl text-sm text-[var(--color-fg-muted)]">Définissez les éléments saisissables et vérifiez leur place dans le calcul avant de les utiliser dans une campagne.</p>
                </div></div>
                <Button type="button" size="sm" variant="secondary" iconLeft={<RefreshCw size={15} />} onClick={load} disabled={loading}>Actualiser</Button>
            </header>
            {error && <div role="alert" className="border border-[var(--color-danger-500)]/40 bg-[var(--color-danger-500)]/10 p-4 text-sm text-[var(--color-danger-700)]">{error} {types.length === 0 && <button type="button" onClick={load} className="ml-2 font-bold underline">Réessayer</button>}</div>}
            {notice && <div role="status" className="flex items-center gap-2 border border-[var(--color-success-500)]/40 bg-[var(--color-success-500)]/10 p-4 text-sm text-[var(--color-success-700)]"><CheckCircle2 size={16} />{notice}</div>}
            <div className="grid items-start gap-5 lg:grid-cols-[minmax(280px,0.8fr)_minmax(0,1.2fr)]">
                <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]" aria-label="Catalogue des types">
                    <div className="space-y-3 border-b border-[var(--color-border-subtle)] p-4 sm:p-5">
                        <div className="flex items-center justify-between gap-3"><div><h2 className="text-base font-bold text-[var(--color-fg)]">Catalogue</h2><p className="text-xs text-[var(--color-fg-muted)]">{types.length} types · {types.filter(type => type.estActif).length} actifs</p></div>
                            {canWrite && <Button type="button" size="sm" variant="primary" iconLeft={<Plus size={14} />} onClick={() => selectType(null)}>Nouveau</Button>}
                        </div>
                        <Input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Rechercher un type…" iconLeft={<Search size={15} />} aria-label="Rechercher un type de variable" />
                    </div>
                    {loading ? <p className="p-5 text-sm text-[var(--color-fg-muted)]">Chargement du catalogue…</p> : types.length === 0 ? <p className="p-5 text-sm text-[var(--color-fg-muted)]">Catalogue indisponible. Actualisez lorsque le serveur répond.</p> : (
                        <div className="max-h-[65vh] overflow-y-auto">
                            {filteredTypes.map(type => (
                                <button key={type.id ?? type.code} type="button" onClick={() => selectType(type)} aria-current={selectedId === type.id ? 'true' : undefined}
                                    className={`flex w-full items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] p-4 text-left hover:bg-[var(--color-bg-subtle)] ${selectedId === type.id ? 'border-l-4 border-l-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5' : ''}`}>
                                    <span className="min-w-0"><strong className="block truncate text-sm text-[var(--color-fg)]">{type.libelle}</strong><span className="mt-1 block text-xs text-[var(--color-fg-muted)]">{type.code} · {type.sens === 'GAIN' ? 'Gain' : 'Retenue'} · {type.nature === 'VARIABLE_DAF' ? 'Manuel' : 'Circuit dédié'}</span></span>
                                    <span className={`shrink-0 text-[11px] font-bold ${type.estActif ? 'text-[var(--color-success-700)]' : 'text-[var(--color-fg-muted)]'}`}>{type.estActif ? 'ACTIF' : 'INACTIF'}</span>
                                </button>
                            ))}
                            {filteredTypes.length === 0 && <p className="p-5 text-sm text-[var(--color-fg-muted)]">Aucun résultat pour cette recherche.</p>}
                        </div>
                    )}
                </section>
                <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]" aria-label="Détail du type">
                    <div className="border-b border-[var(--color-border-subtle)] p-4 sm:p-5"><p className="text-xs font-bold uppercase tracking-widest text-[var(--color-brand-700)]">{selectedId ? 'Type existant' : 'Nouveau type manuel'}</p><h2 className="mt-1 text-lg font-bold text-[var(--color-fg)]">{selectedId ? form.libelle : 'Définir un type de variable'}</h2><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Le code et le sens d’un type existant restent stables pour protéger les mouvements enregistrés.</p></div>
                <form onSubmit={save} className="space-y-5 p-4 sm:p-6">
                    <h3 className="text-sm font-bold text-[var(--color-fg)]">1. Identification</h3>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Input label="Code stable" required maxLength={40} value={form.code} disabled={!canWrite || Boolean(selectedId)} onChange={event => setForm(previous => ({ ...previous, code: event.target.value.toUpperCase() }))} placeholder="Ex. PRIME_PROJET" />
                        <Input label="Libellé affiché" required maxLength={150} value={form.libelle} disabled={!canWrite} onChange={event => setForm(previous => ({ ...previous, libelle: event.target.value }))} placeholder="Ex. Prime de projet" />
                    </div>
                    <div className="border-t border-[var(--color-border-subtle)] pt-4"><h3 className="text-sm font-bold text-[var(--color-fg)]">2. Place dans le calcul</h3></div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Select label="Sens" value={form.sens} disabled={!canWrite || Boolean(selectedId)} onChange={sens => setForm(previous => ({ ...previous, sens, idRubrique: '' }))} options={[{ value: 'GAIN', label: 'Gain' }, { value: 'RETENUE', label: 'Retenue sur net' }]} />
                        <Select label="Rubrique de calcul" value={String(form.idRubrique)} disabled={!canWrite || Boolean(isSpecial) || rubriques.length === 0} onChange={idRubrique => setForm(previous => ({ ...previous, idRubrique }))} options={[{ value: '', label: 'Choisir une rubrique…' }, ...compatibleRubriques.map(rubrique => ({ value: String(rubrique.id), label: `${rubrique.libelle} (${rubrique.code})` }))]} />
                    </div>
                    {selectedRubrique ? <div className="flex gap-3 border border-[var(--color-brand-500)]/25 bg-[var(--color-brand-500)]/5 p-4 text-sm"><ShieldCheck size={18} className="shrink-0 text-[var(--color-brand-700)]" /><div><strong className="text-[var(--color-fg)]">Effet sur le bulletin</strong><p className="mt-1 text-[var(--color-fg-muted)]">{form.sens === 'RETENUE' ? 'Déduction du net à payer, après impôt.' : `Ajout ${selectedRubrique.soumisIrsa ? 'dans le brut, avant impôt' : 'après impôt'} ; ${selectedRubrique.soumisIrsa ? 'soumis' : 'non soumis'} à l’IRSA ; ${selectedRubrique.soumisCnaps ? 'soumis' : 'non soumis'} aux cotisations sociales.`}</p></div></div> : <p className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4 text-sm text-[var(--color-fg-muted)]">Sélectionnez une rubrique pour voir son traitement avant d’enregistrer.</p>}
                    {isSpecial && <p className="text-xs text-[var(--color-fg-muted)]">Ce type suit un circuit métier dédié ; sa rubrique est fixe.</p>}
                    <label className="block space-y-1">
                        <span className="font-bold uppercase tracking-wider text-[var(--color-fg)]">Description et consignes</span>
                        <textarea className="w-full min-h-20 border border-[var(--color-border)] bg-[var(--color-surface)] p-3 text-sm text-[var(--color-fg)] disabled:bg-[var(--color-bg-subtle)]" value={form.description} disabled={!canWrite} onChange={event => setForm(previous => ({ ...previous, description: event.target.value }))} placeholder="Quand utiliser cette variable et quelle justification fournir…" />
                    </label>
                    <div className="flex flex-wrap gap-4">
                        <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.estActif} disabled={!canWrite} onChange={event => setForm(previous => ({ ...previous, estActif: event.target.checked }))} className="accent-[var(--color-brand-600)]" /> Disponible dans le catalogue</label>
                    </div>
                    <div className="flex justify-end gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                        {canWrite && <Button type="submit" variant="primary" disabled={saving || loading || !form.idRubrique}>{saving ? 'Enregistrement…' : selectedId ? 'Enregistrer les modifications' : 'Créer le type'}</Button>}
                    </div>
                </form>
                </section>
            </div>
        </section>
    );
}
