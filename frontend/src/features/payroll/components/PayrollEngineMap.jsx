import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ArrowRight, GitBranch, Plus, RefreshCw, Search, Settings2 } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Card from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { getErrorMessage } from '@/api/apiClient';
import { useAuth } from '@/context/AuthContext';
import paieService from '@/features/payroll/api/paieService';
import { fmtDate } from '@/utils/dateUtils';

const phaseKey = item => item?.rubrique?.phaseCalcul || item?.phaseCalcul || 'DOMAINE';
const matching = (item, query) => !query || `${item.code} ${item.libelle} ${item.rubrique?.code || ''} ${item.rubrique?.libelle || ''}`.toLocaleLowerCase('fr').includes(query);

export default function PayrollEngineMap() {
    const navigate = useNavigate();
    const [params, setParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const canWriteRubrique = hasPermission('ROLE_ADMIN');
    const canWriteType = canWriteRubrique || hasPermission('PAIE_WRITE');
    const mode = params.get('mode') === 'types' || params.get('vue') === 'regles' ? 'types' : 'rubriques';
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [reload, setReload] = useState(0);
    const [query, setQuery] = useState('');
    const [selectedKey, setSelectedKey] = useState(null);

    useEffect(() => {
        const controller = new AbortController();
        paieService.getParcoursConfiguration(controller.signal)
            .then(result => { if (!controller.signal.aborted) { setData(result); setError(''); } })
            .catch(cause => { if (!controller.signal.aborted) setError(getErrorMessage(cause, 'Impossible de charger la configuration de paie.')); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [reload]);

    const rows = useMemo(() => {
        if (!data) return [];
        const items = mode === 'types' ? data.types : data.rubriques;
        const needle = query.trim().toLocaleLowerCase('fr');
        return items.filter(item => matching(item, needle));
    }, [data, mode, query]);
    const phaseList = data?.phases || [];
    const known = new Set(phaseList.map(phase => phase.code));
    const groups = [
        ...phaseList.map(phase => ({ ...phase, rows: rows.filter(item => phaseKey(item) === phase.code) })),
        ...[...new Set(rows.map(phaseKey).filter(code => !known.has(code)))].map(code => ({ code, libelle: rows.find(item => phaseKey(item) === code)?.rubrique?.etapeLibelle || rows.find(item => phaseKey(item) === code)?.etapeLibelle || code, rows: rows.filter(item => phaseKey(item) === code) })),
    ];
    const selected = rows.find(item => `${mode}:${item.id}` === selectedKey) || rows[0] || null;
    const rubrique = mode === 'types' ? selected?.rubrique : selected;
    const linkedTypes = data?.types?.filter(type => type.idRubrique === rubrique?.id) || [];
    const level = rubrique?.niveauConfiguration || selected?.niveauConfiguration;
    const levelLabel = rubrique?.niveauLibelle || selected?.niveauLibelle || level;
    const treatmentEditable = level === 'CONFIGURABLE';
    const changeMode = next => {
        setParams(previous => { const updated = new URLSearchParams(previous); updated.delete('vue'); updated.set('mode', next); return updated; });
        setSelectedKey(null);
        setQuery('');
    };
    const openEditor = (domaine, id, intent) => {
        const next = new URLSearchParams({ vue: domaine });
        if (intent === 'create') {
            next.set(domaine === 'types' ? 'vt_creer' : 'rb_creer', '1');
            if (domaine === 'types' && id) next.set('vt_rubrique', String(id));
        } else if (id) next.set(domaine === 'types' ? 'vt_selection' : 'rb_selection', String(id));
        if (intent === 'version') next.set('rb_versionner', '1');
        if (intent === 'edit') next.set(domaine === 'types' ? 'vt_modifier' : 'rb_modifier', '1');
        navigate(`/paie/configuration?${next.toString()}`);
    };

    return <div className="space-y-5">
        <div className="flex flex-wrap items-end justify-between gap-4">
            <div><p className="text-xs font-semibold tracking-wider text-[var(--color-brand-500)]">RÉFÉRENTIEL DE PAIE</p>
                <h2 className="text-xl font-semibold mt-1">Traitements dans le moteur</h2>
                <p className="text-sm text-[var(--color-fg-muted)] mt-1">Type de variable → rubrique → mouvement approuvé → bulletin.</p></div>
            <div className="flex flex-wrap gap-2">
                <Button variant="ghost" className="min-h-[44px]" iconLeft={<RefreshCw size={16} />} onClick={() => { setLoading(!data); setReload(value => value + 1); }}>Actualiser</Button>
                {((mode === 'rubriques' && canWriteRubrique) || (mode === 'types' && canWriteType)) && <Button className="min-h-[44px]" iconLeft={<Plus size={16} />} onClick={() => openEditor(mode, null, 'create')}>{mode === 'types' ? 'Nouveau type' : 'Nouvelle règle manuelle'}</Button>}
            </div>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-4 border-b border-[var(--color-border-subtle)] pb-3">
            <Tabs
                tabs={[
                    { id: 'rubriques', label: 'Par rubrique' },
                    { id: 'types', label: 'Par type de variable' }
                ]}
                activeTab={mode}
                onChange={changeMode}
                variant="pills"
                className="border-b-0 p-0"
            />
            <div className="w-full sm:w-72"><Input aria-label="Rechercher une rubrique ou un type" placeholder="Rechercher dans le référentiel…" iconLeft={<Search size={16} />} value={query} onChange={event => setQuery(event.target.value)} className="min-h-[44px]" /></div>
        </div>
        {error && <Alert type="error" title="Référentiel indisponible" message={error} />}
        {loading ? <SkeletonTable rows={8} columns={5} /> : !data ? null : <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.8fr)_minmax(320px,0.9fr)] gap-5 items-start">
            <Card as="section" aria-label="Étapes du moteur" className="min-w-0 overflow-hidden">
                <div className="hidden lg:grid grid-cols-[110px_minmax(0,1.3fr)_minmax(0,1fr)_120px_130px] gap-3 px-4 py-3 bg-[var(--color-bg-subtle)] text-[11px] font-semibold tracking-wider text-[var(--color-fg-muted)]">
                    <span>ÉTAPE</span><span>{mode === 'types' ? 'TYPE DE VARIABLE' : 'RUBRIQUE'}</span><span>{mode === 'types' ? 'RUBRIQUE LIÉE' : 'TYPE ASSOCIÉ'}</span><span>NIVEAU</span><span>ASSIETTES</span>
                </div>
                {rows.length === 0 ? <EmptyState bordered={false} title="Aucun élément trouvé" description="Essayez une autre recherche." actionLabel="Effacer la recherche" onAction={() => setQuery('')} /> : groups.map((group, index) => <div key={group.code}>
                    <div className={`px-4 py-3 flex items-center gap-3 ${known.has(group.code) ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)]'}`}>
                        <span className="w-6 h-6 shrink-0 flex items-center justify-center border border-current text-xs font-bold">{known.has(group.code) ? index + 1 : '—'}</span>
                        <span className="font-semibold text-sm">{group.libelle}</span>
                        {group.description && <span className="hidden sm:inline text-xs opacity-80">{group.description}</span>}
                        <span className="ml-auto text-xs opacity-80">{group.rows.length}</span>
                    </div>
                    {group.rows.length ? <ul className="divide-y divide-[var(--color-border-subtle)]">{group.rows.map(item => {
                        const rule = mode === 'types' ? item.rubrique : item;
                        const types = mode === 'types' ? [] : data.types.filter(type => type.idRubrique === item.id);
                        const active = selected?.id === item.id;
                        return <li key={item.id}><button type="button" aria-pressed={active} onClick={() => setSelectedKey(`${mode}:${item.id}`)} className={`w-full text-left grid grid-cols-1 lg:grid-cols-[110px_minmax(0,1.3fr)_minmax(0,1fr)_120px_130px] gap-2 lg:gap-3 px-4 py-3 min-h-[64px] border-l-[3px] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)] ${active ? 'bg-[var(--color-bg-subtle)] border-l-[var(--color-brand-500)]' : 'border-l-transparent hover:bg-[var(--color-bg-subtle)]'}`}>
                            <span className="text-xs text-[var(--color-fg-muted)]">{group.libelle}</span>
                            <span><span className="block text-sm font-semibold">{item.libelle}</span><span className="block text-xs font-data text-[var(--color-fg-muted)] mt-0.5">{item.code}</span></span>
                            <span className="text-xs self-center text-[var(--color-fg-muted)]">{mode === 'types' ? rule?.libelle || 'Aucune rubrique liée' : types.length ? types.map(type => type.code).join(', ') : 'Aucun type lié'}</span>
                            <span className="self-center"><Badge variant={rule?.niveauConfiguration === 'CONFIGURABLE' ? 'info' : 'neutral'}>{rule?.niveauLibelle || item.niveauLibelle || '—'}</Badge></span>
                            <span className="self-center flex flex-wrap gap-1">{rule?.niveauConfiguration === 'CONFIGURABLE' && rule?.phaseCalcul === 'BRUT' ? [['IRSA', rule.soumisIrsa], ['CNaPS', rule.soumisCnaps], ['Santé', rule.soumisSante], ['FMFP', rule.soumisFmfp ?? rule.soumisCnaps]].filter(([, enabled]) => enabled).map(([label]) => <Badge key={label} size="sm" variant="neutral">{label}</Badge>) : <span className="text-xs text-[var(--color-fg-muted)]">—</span>}</span>
                        </button></li>;
                    })}</ul> : <p className="px-4 py-3 text-xs text-[var(--color-fg-muted)]">Aucun élément de ce catalogue à cette étape.</p>}
                </div>)}
            </Card>
            <Card as="aside" aria-label="Détail de configuration" padding className="xl:sticky xl:top-5 min-w-0">
                {!selected ? <EmptyState bordered={false} title="Choisissez un élément" description="Sélectionnez une ligne pour consulter son traitement." /> : <>
                    <p className="text-xs font-semibold tracking-wider text-[var(--color-fg-muted)]">{phaseList.find(phase => phase.code === phaseKey(selected))?.libelle || rubrique?.etapeLibelle}</p>
                    <h3 className="text-xl font-semibold mt-2">{selected.libelle}</h3>
                    <p className="text-xs font-data text-[var(--color-fg-muted)] mt-1">{selected.code}</p>
                    <div className="mt-3"><Badge variant={treatmentEditable ? 'info' : 'neutral'}>{levelLabel}</Badge></div>
                    <div className="mt-5 space-y-2 text-sm">
                        {mode === 'types' && <div className="p-3 border border-[var(--color-border-subtle)]">Type · {selected.libelle}</div>}
                        <div className="p-3 border-l-[3px] border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)] font-semibold">Rubrique · {rubrique?.libelle || 'Non liée'}</div>
                        <div className="p-3 border border-[var(--color-border-subtle)]">{mode === 'types' ? selected.saisieManuelleAutorisee ? 'Saisie manuelle autorisée' : 'Saisie manuelle non autorisée' : linkedTypes.length ? `${linkedTypes.length} type${linkedTypes.length > 1 ? 's' : ''} lié${linkedTypes.length > 1 ? 's' : ''}` : 'Aucun type lié'}</div>
                        <div className="p-3 border border-[var(--color-border-subtle)]">{rubrique?.etapeLibelle || 'Calculateur métier'}</div>
                    </div>
                    {rubrique?.description && <p className="mt-5 text-sm text-[var(--color-fg-muted)]">{rubrique.description}</p>}
                    <dl className="mt-5 divide-y divide-[var(--color-border-subtle)] text-sm">{[
                        ['Méthode', rubrique?.methodeLibelle || rubrique?.sourceCalcul],
                        ['Arrondi', rubrique?.arrondiLibelle || rubrique?.arrondiCalculateur],
                        ['Condition', rubrique?.conditionLibelle],
                        ['Début d’effet', rubrique?.dateDebutEffet ? fmtDate(rubrique.dateDebutEffet) : null],
                        ['Fin d’effet', rubrique?.dateFinEffet ? fmtDate(rubrique.dateFinEffet) : null],
                        ['Référence', rubrique?.referenceReglementaire],
                    ].filter(([, value]) => value).map(([label, value]) => <div key={label} className="flex justify-between gap-4 py-2"><dt className="text-[var(--color-fg-muted)]">{label}</dt><dd className="text-right font-medium break-words">{value}</dd></div>)}</dl>
                    <div className="mt-6 flex flex-wrap gap-2 border-t border-[var(--color-border-subtle)] pt-5">
                        {mode === 'types' ? rubrique && <Button variant="outline" className="min-h-[44px]" iconLeft={<Settings2 size={15} />} onClick={() => openEditor('rubriques', rubrique.id)}>Voir la rubrique</Button>
                            : canWriteType && treatmentEditable && <Button variant="outline" className="min-h-[44px]" iconLeft={<GitBranch size={15} />} onClick={() => openEditor('types', rubrique.id, 'create')}>Créer un type lié</Button>}
                        {((mode === 'rubriques' && canWriteRubrique && rubrique?.peutModifier) || (mode === 'types' && canWriteType && selected.peutModifier)) && <Button className="min-h-[44px]" iconRight={<ArrowRight size={15} />} onClick={() => openEditor(mode, selected.id, 'edit')}>Modifier</Button>}
                        {mode === 'rubriques' && canWriteRubrique && rubrique?.peutVersionner && <Button className="min-h-[44px]" onClick={() => openEditor('rubriques', rubrique.id, 'version')}>Nouvelle version</Button>}
                        {mode === 'rubriques' && rubrique?.parametrageUrl && <Button variant="outline" className="min-h-[44px]" onClick={() => navigate(rubrique.parametrageUrl)}>Paramétrage dédié</Button>}
                    </div>
                </>}
            </Card>
        </div>}
    </div>;
}
