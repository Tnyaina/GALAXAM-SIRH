import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowRight, Eye, Search } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import { Card, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import Tabs from '@/components/ui/navigation/Tabs';
import Pagination from '@/components/ui/navigation/Pagination';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

const BulletinPaieDrawer = lazy(() => import('@/features/payroll/components/views/BulletinPaieDrawer'));
const PAGE_SIZE = 20;
const money = value => value == null ? 'Non renseigné' : formatMoneyAr(value);

export default function ControleStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: contextLoading, refreshSilent } = usePayrollCampaign(numericPeriodId);
    const [cockpit, setCockpit] = useState(null);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [query, setQuery] = useState('');
    const [debouncedQuery, setDebouncedQuery] = useState('');
    const [filter, setFilter] = useState('ALL');
    const [page, setPage] = useState(1);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const requestSequence = useRef(0);

    useEffect(() => {
        const timer = window.setTimeout(() => setDebouncedQuery(query.trim()), 250);
        return () => window.clearTimeout(timer);
    }, [query]);

    const loadCockpit = useCallback(async () => {
        if (!numericPeriodId) return;
        const requestId = ++requestSequence.current;
        setLoading(true);
        setError(null);
        try {
            const response = await paieService.getCockpitPeriode(numericPeriodId, {
                page,
                size: PAGE_SIZE,
                q: debouncedQuery || undefined,
                filter,
            });
            if (requestId === requestSequence.current) setCockpit(response);
        } catch (cause) {
            if (requestId === requestSequence.current) {
                setCockpit(null);
                setError(getErrorMessage(cause, 'Impossible de charger les bulletins.'));
            }
        } finally {
            if (requestId === requestSequence.current) setLoading(false);
        }
    }, [numericPeriodId, page, debouncedQuery, filter]);

    useEffect(() => { loadCockpit(); }, [loadCockpit]);
    useEffect(() => {
        const refreshOnFocus = () => {
            if (document.visibilityState !== 'visible') return;
            refreshSilent();
            loadCockpit();
        };
        document.addEventListener('visibilitychange', refreshOnFocus);
        return () => document.removeEventListener('visibilitychange', refreshOnFocus);
    }, [refreshSilent, loadCockpit]);

    const bulletins = Array.isArray(cockpit?.bulletins) ? cockpit.bulletins : [];
    const totalBulletins = cockpit?.kpis?.effectifCalcule ?? 0;
    const alerts = cockpit?.readiness?.nbAnomalies ?? 0;
    const blockers = context?.issueSummary?.blockerCount ?? 0;
    const matchingCount = cockpit?.bulletinsPage?.totalElements ?? bulletins.length;
    const canContinue = context?.availableActions?.VALIDATE?.allowed === true;
    const filterTabs = [
        { id: 'ALL', label: 'Tous', count: totalBulletins },
        { id: 'ALERTS', label: 'À examiner', count: alerts },
    ];

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="CONTROL"
            loading={contextLoading}
            title="Contrôle des bulletins"
            subtitle="Examinez les montants et les signaux avant validation."
            primaryAction={{
                label: 'Continuer vers la validation',
                onClick: () => navigate(`/paie/periodes/${numericPeriodId}/validation`),
                disabled: !canContinue,
                icon: <ArrowRight size={14} />,
            }}
        >
            <SEO title="Contrôle des bulletins | GALAXAM SIRH" description="Revue nominative des bulletins de paie." />

            <div className="space-y-4">
                {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

                <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
                    <StatCard title="Bulletins calculés" value={totalBulletins} color="neutral" />
                    <StatCard title="À examiner" value={alerts} color="neutral" subtext="Signaux du calcul" />
                    <StatCard title="Points bloquants" value={blockers} color="neutral" subtext="Campagne" />
                    <StatCard title="Net à payer" value={money(cockpit?.kpis?.totalNetAPayer)} color="brand" />
                </div>

                <Card shadow="none">
                    <CardHeader className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
                        <div>
                            <CardTitle>État nominatif</CardTitle>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Montants enregistrés pour la campagne.</p>
                        </div>
                        <div className="w-full lg:w-72">
                            <Input
                                aria-label="Rechercher un salarié"
                                placeholder="Nom ou matricule"
                                value={query}
                                onChange={event => { setQuery(event.target.value); setPage(1); }}
                                iconLeft={<Search size={14} />}
                            />
                        </div>
                    </CardHeader>

                    <Tabs
                        tabs={filterTabs}
                        activeTab={filter}
                        onChange={value => { setFilter(value); setPage(1); }}
                    />

                    {loading ? (
                        <div className="p-4"><SkeletonTable rows={6} cols={6} /></div>
                    ) : error ? null : bulletins.length === 0 ? (
                        <p className="px-4 py-8 text-sm text-[var(--color-fg-muted)]">
                            {filter === 'ALERTS' ? 'Aucun bulletin à examiner.' : 'Aucun bulletin trouvé.'}
                        </p>
                    ) : (
                        <Table bordered={false} density="compact" aria-label="Bulletins de la campagne">
                            <Thead>
                                <Tr>
                                    <Th>Salarié</Th>
                                    <Th className="text-right">Brut</Th>
                                    <Th className="text-right">Cotisations</Th>
                                    <Th className="text-right">IRSA</Th>
                                    <Th className="text-right">Net à payer</Th>
                                    <Th>Signal</Th>
                                    <Th className="text-right">Détail</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {bulletins.map(bulletin => (
                                    <Tr key={bulletin.idBulletin}>
                                        <Td className="min-w-48">
                                            <div className="font-semibold text-[var(--color-fg)]">{bulletin.nomPrenom || 'Nom non renseigné'}</div>
                                            <div className="text-[11px] text-[var(--color-fg-muted)]">
                                                {[bulletin.matricule && `Matricule ${bulletin.matricule}`, bulletin.nomSite].filter(Boolean).join(' · ')}
                                            </div>
                                        </Td>
                                        <Td className="text-right font-data tabular-nums whitespace-nowrap">{money(bulletin.totalGainsBruts)}</Td>
                                        <Td className="text-right font-data tabular-nums whitespace-nowrap">{money(bulletin.totalCotisationsSalariales)}</Td>
                                        <Td className="text-right font-data tabular-nums whitespace-nowrap">{money(bulletin.irsaNet)}</Td>
                                        <Td className="text-right font-data font-bold tabular-nums whitespace-nowrap text-[var(--color-brand-700)]">{money(bulletin.netAPayer)}</Td>
                                        <Td>
                                            {bulletin.aAlerte === true
                                                ? <Badge variant="warning" size="xs">À examiner</Badge>
                                                : <span className="text-xs text-[var(--color-fg-muted)]">Aucun signal</span>}
                                        </Td>
                                        <Td className="text-right">
                                            <Button variant="outline" size="xs" iconLeft={<Eye size={13} />} onClick={() => setSelectedBulletinId(bulletin.idBulletin)}>
                                                Voir
                                            </Button>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    )}

                    {matchingCount > PAGE_SIZE && (
                        <Pagination
                            currentPage={page}
                            pageSize={PAGE_SIZE}
                            totalItems={matchingCount}
                            onPageChange={setPage}
                        />
                    )}
                </Card>
            </div>

            <Suspense fallback={null}>
                {selectedBulletinId && (
                    <BulletinPaieDrawer
                        isOpen
                        idBulletin={selectedBulletinId}
                        onClose={() => setSelectedBulletinId(null)}
                    />
                )}
            </Suspense>
        </PayrollCampaignShell>
    );
}
