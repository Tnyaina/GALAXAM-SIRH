import { useSearchParams } from 'react-router-dom';
import { RefreshCw, Search } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Pagination from '@/components/ui/navigation/Pagination';
import useDebounce from '@/hooks/useDebounce';
import usePayrollConfiguration from '@/features/payroll/hooks/usePayrollConfiguration';

/** Vue de lecture : le catalogue serveur fournit les traitements et la pagination. */
export default function PayrollRulesReadOnly() {
    const [params, setParams] = useSearchParams();
    const read = (key, fallback = '') => params.get(`rg_${key}`) || fallback;
    const query = read('q');
    const debouncedQuery = useDebounce(query, 300);
    const { data, loading, refreshing, error, refresh } = usePayrollConfiguration({
        domaine: 'types', recherche: debouncedQuery, sens: read('sens'), niveau: read('niveau'),
        statut: read('statut'), tri: read('tri', 'ordre'), page: Number(read('page', '1')),
        taille: Number(read('taille', '10')), selection: Number(read('selection')) || undefined,
    });
    const options = data?.options || {};
    const setFilter = (key, value) => setParams(previous => {
        const next = new URLSearchParams(previous);
        if (value) next.set(`rg_${key}`, String(value)); else next.delete(`rg_${key}`);
        if (key !== 'page' && key !== 'selection') next.delete('rg_page');
        return next;
    }, { replace: true });
    const selected = data?.selection;
    const rubric = selected?.rubrique;

    return <div className="space-y-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
            <div><h2 className="text-lg font-semibold">Règles actuellement configurées</h2>
                <p className="mt-1 text-sm text-[var(--color-fg-muted)]">Correspondance entre les types et les rubriques. Cette vue ne modifie aucun calcul.</p></div>
            <Button variant="ghost" className="min-h-[44px]" iconLeft={<RefreshCw size={16} />}
                loading={refreshing && !loading} onClick={refresh}>Actualiser</Button>
        </div>
        {error && <Alert type="error" title="Règles indisponibles" message={error} />}
        <div className="grid grid-cols-1 xl:grid-cols-[minmax(0,1.5fr)_minmax(320px,1fr)] gap-5 items-start">
            <section aria-label="Catalogue des règles" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] min-w-0">
                <div className="p-4 space-y-4">
                    <h3 className="font-semibold">Correspondances <span className="font-normal text-sm text-[var(--color-fg-muted)]">· {data?.totalElements ?? '—'}</span></h3>
                    <Input aria-label="Rechercher une règle" placeholder="Rechercher un type ou une rubrique…" iconLeft={<Search size={16} />}
                        value={query} onChange={event => setFilter('q', event.target.value)} className="min-h-[44px]" />
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Select aria-label="Filtrer par sens" value={read('sens')} onChange={value => setFilter('sens', value)}
                            options={[{ value: '', label: 'Gains et retenues' }, ...(options.sens || [])]} className="min-h-[44px]" />
                        <Select aria-label="Filtrer par niveau" value={read('niveau')} onChange={value => setFilter('niveau', value)}
                            options={[{ value: '', label: 'Tous les circuits' }, ...(options.niveaux || [])]} className="min-h-[44px]" />
                        <Select aria-label="Filtrer par statut" value={read('statut')} onChange={value => setFilter('statut', value)}
                            options={[{ value: '', label: 'Tous les statuts' }, ...(options.statuts || [])]} className="min-h-[44px]" />
                    </div>
                </div>
                {loading ? <SkeletonTable rows={5} columns={4} bordered={false} /> : !data ? null : data.items.length === 0
                    ? <EmptyState bordered={false} title="Aucune règle trouvée" description="Modifiez la recherche ou les filtres." actionLabel="Effacer les filtres" onAction={() => {
                        setParams(previous => { const next = new URLSearchParams(previous); ['q', 'sens', 'niveau', 'statut', 'page'].forEach(key => next.delete(`rg_${key}`)); return next; });
                    }} />
                    : <div className="overflow-x-auto"><Table bordered={false} aria-label="Règles appliquées"><Thead>
                        <Tr><Th>Type de variable</Th><Th>Rubrique liée</Th><Th>Intervention</Th><Th>Statut</Th></Tr>
                    </Thead><Tbody>{data.items.map(type => <Tr key={type.id} className={selected?.id === type.id ? 'bg-[var(--color-bg-subtle)]' : ''}>
                        <Td><Button variant="ghost" className="min-h-[44px] !justify-start !text-left !normal-case !tracking-normal !whitespace-normal"
                            aria-pressed={selected?.id === type.id} onClick={() => setFilter('selection', type.id)}>{type.libelle}</Button></Td>
                        <Td>{type.rubrique?.libelle || 'À vérifier'}</Td>
                        <Td>{type.rubrique?.etapeLibelle || type.niveauLibelle}</Td>
                        <Td><Badge variant={type.estActif ? 'success' : 'neutral'}>{type.estActif ? 'Actif' : 'Inactif'}</Badge></Td>
                    </Tr>)}</Tbody></Table></div>}
                {data && data.totalElements > 0 && <Pagination currentPage={data.page} totalElements={data.totalElements} pageSize={data.taille}
                    pageSizeOptions={[10, 25, 50]} onPageChange={value => setFilter('page', value)} onPageSizeChange={value => setFilter('taille', value)} />}
            </section>
            <section aria-label="Détail de la règle" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 min-w-0 xl:sticky xl:top-4">
                {!selected ? <EmptyState bordered={false} title="Choisissez un type" description="Sélectionnez une ligne pour lire la rubrique et les assiettes appliquées." /> : <>
                    <Badge variant="neutral">{selected.niveauLibelle}</Badge>
                    <h3 className="mt-3 text-lg font-semibold">{selected.libelle}</h3>
                    <p className="mt-1 text-sm text-[var(--color-fg-muted)]">Rubrique : {rubric?.libelle || 'Aucune rubrique liée'}</p>
                    {rubric && <dl className="mt-5 divide-y divide-[var(--color-border-subtle)] text-sm">
                        {[
                            ['Étape', rubric.etapeLibelle || rubric.phaseCalcul],
                            ['Méthode', rubric.methodeLibelle || rubric.sourceCalcul],
                            ['Arrondi', rubric.arrondiLibelle || rubric.arrondiCalculateur],
                            ['Condition', rubric.conditionLibelle],
                            ...(selected.niveauConfiguration === 'CONFIGURABLE' ? [
                                ['IRSA', rubric.soumisIrsa ? 'Dans l’assiette' : 'Hors assiette'],
                                ['CNaPS', rubric.soumisCnaps ? 'Dans l’assiette' : 'Hors assiette'],
                                ['Santé', rubric.soumisSante ? 'Dans l’assiette' : 'Hors assiette'],
                                ['FMFP', (rubric.soumisFmfp ?? rubric.soumisCnaps) ? 'Dans l’assiette' : 'Hors assiette'],
                            ] : []),
                        ].filter(([, value]) => value).map(([label, value]) => <div key={label} className="flex justify-between gap-4 py-2"><dt className="text-[var(--color-fg-muted)]">{label}</dt><dd className="text-right font-medium">{value}</dd></div>)}
                    </dl>}
                    {rubric?.referenceReglementaire && <p className="mt-4 text-xs text-[var(--color-fg-muted)]">Référence : {rubric.referenceReglementaire}</p>}
                    {rubric?.dateDebutEffet && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Effet depuis le {rubric.dateDebutEffet}</p>}
                </>}
            </section>
        </div>
    </div>;
}
