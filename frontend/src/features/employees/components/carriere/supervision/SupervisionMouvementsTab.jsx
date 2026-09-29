import { useState, useEffect, useCallback, memo } from 'react';
import { Search, RefreshCw, Layers, RotateCcw, Eye } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import contratService from '@/features/employees/api/contratService';
import organisationService from '@/features/organization/api/organisationService';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Pagination from '@/components/ui/navigation/Pagination';
import SlideOver from '@/components/ui/overlays/SlideOver';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { fmtDate, fmtAriary } from '@/features/dashboard/components/dashboardPresentation';
import HasPermission from '@/features/auth/components/HasPermission';
import useDebounce from '@/hooks/useDebounce';

const PAGE_SIZE = 15;

function SupervisionMouvementsTab() {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [workflows, setWorkflows] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [selectedType, setSelectedType] = useState('ALL');
    const [selectedStatut, setSelectedStatut] = useState('ALL');
    const [selectedSite, setSelectedSite] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [typesEvenements, setTypesEvenements] = useState([]);
    const [statuts, setStatuts] = useState([]);
    const [sites, setSites] = useState([]);
    const [selected, setSelected] = useState(null);

    useEffect(() => {
        Promise.all([
            contratService.getTypesEvenementRh(),
            contratService.getStatutsWorkflows(),
            organisationService.getSites(),
        ]).then(([types, states, siteList]) => {
            setTypesEvenements(Array.isArray(types) ? types : []);
            setStatuts(Array.isArray(states) ? states : []);
            setSites(Array.isArray(siteList) ? siteList : []);
        }).catch(() => setError('Référentiels de recherche indisponibles.'));
    }, []);

    const fetchWorkflows = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await contratService.getWorkflowsGlobal({
                page: currentPage,
                size: PAGE_SIZE,
                q: debouncedSearch.trim() || undefined,
                codeTypeEvenement: selectedType !== 'ALL' ? selectedType : undefined,
                codeStatut: selectedStatut !== 'ALL' ? selectedStatut : undefined,
                idSite: selectedSite ? Number(selectedSite) : undefined,
            });
            setWorkflows(data?.content || []);
            setTotalElements(data?.totalElements || 0);
        } catch {
            setError('Impossible de charger les mouvements de carrière.');
            setWorkflows([]);
            setTotalElements(0);
        } finally {
            setLoading(false);
        }
    }, [currentPage, debouncedSearch, selectedType, selectedStatut, selectedSite]);

    useEffect(() => { fetchWorkflows(); }, [fetchWorkflows]);

    const resetFilters = () => {
        setSearchQuery('');
        setSelectedType('ALL');
        setSelectedStatut('ALL');
        setSelectedSite('');
        setCurrentPage(1);
    };
    const hasActiveFilters = Boolean(searchQuery || selectedType !== 'ALL' || selectedStatut !== 'ALL' || selectedSite);
    const typeOptions = [{ value: 'ALL', label: 'Tous les mouvements' },
        ...typesEvenements.map(type => ({ value: type.code, label: type.libelle }))];
    const statutOptions = [{ value: 'ALL', label: 'Tous les états' },
        ...statuts.map(state => ({ value: state.code, label: state.libelle }))];
    const siteOptions = [{ value: '', label: 'Tous les sites' },
        ...sites.filter(site => site.estActif !== false && site.actif !== false)
            .map(site => ({ value: String(site.idSite), label: site.libelle || site.nomSite || site.codeSite }))];

    return <div className="space-y-3.5">
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 sm:p-4 space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-[minmax(220px,1fr)_repeat(3,minmax(150px,auto))] gap-2">
                <Input aria-label="Rechercher un mouvement" value={searchQuery}
                    onChange={event => { setSearchQuery(event.target.value); setCurrentPage(1); }}
                    placeholder="Matricule, nom ou motif…" iconLeft={<Search size={14} />} />
                <Select aria-label="Type de mouvement" value={selectedType} options={typeOptions}
                    onChange={value => { setSelectedType(value); setCurrentPage(1); }} />
                <Select aria-label="État du mouvement" value={selectedStatut} options={statutOptions}
                    onChange={value => { setSelectedStatut(value); setCurrentPage(1); }} />
                <Select aria-label="Site du mouvement" value={selectedSite} options={siteOptions}
                    onChange={value => { setSelectedSite(value); setCurrentPage(1); }} />
            </div>
            <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-fg-muted)]">
                <span><strong className="text-[var(--color-fg)]">{totalElements}</strong> mouvement(s) trouvé(s)</span>
                <div className="flex gap-2">
                    {hasActiveFilters && <Button variant="ghost" size="xs" iconLeft={<RotateCcw size={12} />} onClick={resetFilters}>Réinitialiser</Button>}
                    <Button variant="outline" size="xs" iconLeft={<RefreshCw size={12} className={loading ? 'animate-spin' : ''} />} onClick={fetchWorkflows}>Actualiser</Button>
                </div>
            </div>
        </div>

        {error && <Alert type="error" message={error} />}
        {loading && <SkeletonTable rows={8} />}
        {!loading && !error && workflows.length === 0 && <EmptyState icon={Layers} title="Aucun mouvement trouvé"
            description="Modifiez les filtres pour consulter d'autres mouvements."
            action={hasActiveFilters && <Button variant="outline" size="sm" onClick={resetFilters}>Réinitialiser</Button>} />}
        {!loading && !error && workflows.length > 0 && <div className="overflow-x-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
            <Table bordered={false}>
                <Thead><Tr>
                    <Th>Date d'effet</Th><Th>Collaborateur</Th><Th>Mouvement</Th><Th>Poste / site</Th><Th>État</Th><Th align="right">Détail</Th>
                </Tr></Thead>
                <Tbody>{workflows.map(wf => <Tr key={wf.idWorkflow}>
                    <Td className="font-data whitespace-nowrap">{fmtDate(wf.dateEffet || wf.dateDemande)}</Td>
                    <Td><span className="block font-semibold text-[var(--color-fg)]">{wf.nomPrenomEmploye}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">#{wf.matriculeEmploye}</span></Td>
                    <Td><span className="block font-semibold">{wf.libelleTypeEvenement || wf.codeTypeEvenement}</span>
                        {wf.motif && <span className="block max-w-48 truncate text-[11px] text-[var(--color-fg-muted)]" title={wf.motif}>{wf.motif}</span>}</Td>
                    <Td><span className="block">{wf.libelleFonction || '—'}</span>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">{wf.libelleSite || '—'}</span></Td>
                    <Td><StatusBadge status={wf.codeStatutWorkflow} label={wf.libelleStatutWorkflow} size="xs" />
                        {wf.isPlanifie && <span className="block mt-1 text-[11px] text-[var(--color-brand-600)]">Effet planifié</span>}</Td>
                    <Td align="right"><Button variant="outline" size="xs" iconLeft={<Eye size={12} />} onClick={() => setSelected(wf)}>Voir</Button></Td>
                </Tr>)}</Tbody>
            </Table>
        </div>}
        {totalElements > PAGE_SIZE && <Pagination currentPage={currentPage} totalItems={totalElements}
            pageSize={PAGE_SIZE} onPageChange={setCurrentPage} />}

        <SlideOver isOpen={Boolean(selected)} onClose={() => setSelected(null)}
            title={selected?.libelleTypeEvenement || 'Mouvement RH'}
            subtitle={selected && `${selected.nomPrenomEmploye} · #${selected.matriculeEmploye}`} width="lg">
            {selected && <div className="space-y-4 text-sm">
                <div className="flex flex-wrap gap-2">
                    <Badge variant="primary" size="xs">{selected.libelleTypeEvenement || selected.codeTypeEvenement}</Badge>
                    <StatusBadge status={selected.codeStatutWorkflow} label={selected.libelleStatutWorkflow} size="xs" />
                </div>
                <dl className="grid grid-cols-[minmax(120px,1fr)_2fr] gap-x-3 gap-y-2 border-y border-[var(--color-border-subtle)] py-3">
                    <dt className="text-[var(--color-fg-muted)]">Date d'effet</dt><dd>{fmtDate(selected.dateEffet || selected.dateDemande)}</dd>
                    <dt className="text-[var(--color-fg-muted)]">Contrat</dt><dd>{selected.numeroContratCible || selected.numeroContratSource || '—'}{selected.libelleTypeContrat ? ` · ${selected.libelleTypeContrat}` : ''}</dd>
                    <dt className="text-[var(--color-fg-muted)]">Fonction</dt><dd>{selected.libelleFonction || '—'}</dd>
                    <dt className="text-[var(--color-fg-muted)]">Site</dt><dd>{selected.libelleSite || '—'}</dd>
                    <dt className="text-[var(--color-fg-muted)]">Département</dt><dd>{selected.libelleDepartement || '—'}</dd>
                    <HasPermission authority="PAIE_READ">{selected.salaireBase != null && <>
                        <dt className="text-[var(--color-fg-muted)]">Salaire de base</dt>
                        <dd>{fmtAriary(selected.salaireBase)} Ar</dd>
                    </>}</HasPermission>
                </dl>
                {selected.motif && <p><strong>Motif :</strong> {selected.motif}</p>}
                {selected.isAnnule && selected.commentaire && <Alert type="warning" message={selected.commentaire} />}
                <Button onClick={() => navigate(`/employes/${selected.idEmploye}?tab=carriere&wf=${selected.idWorkflow}`)}>
                    Ouvrir la carrière
                </Button>
            </div>}
        </SlideOver>
    </div>;
}

export default memo(SupervisionMouvementsTab);
