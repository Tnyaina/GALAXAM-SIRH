import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowRight, Eye } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import StatCard from '@/components/ui/data-display/StatCard';
import { Tr, Td } from '@/components/ui/data-display/Table';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';
import PayrollStepTable, { PayrollEmployeeCell, PayrollMoneyCell } from '@/features/payroll/components/workflow/PayrollStepTable';

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
    const [loadedQuery, setLoadedQuery] = useState(null);
    const queryKey = JSON.stringify([numericPeriodId, page, debouncedQuery, filter]);
    const hasCurrentResults = loadedQuery === queryKey && cockpit !== null;

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
            if (requestId === requestSequence.current) {
                setCockpit(response);
                setLoadedQuery(JSON.stringify([numericPeriodId, page, debouncedQuery, filter]));
            }
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
            <SEO title="Contrôle des bulletins | GALAXAM GRH" description="Revue nominative des bulletins de paie." />

            <div className="space-y-4">
                {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                    <StatCard
                        title="Bulletins calculés"
                        value={totalBulletins}
                        color="brand"
                        subtext="Campagne courante"
                    />
                    <StatCard
                        title="À examiner"
                        value={alerts}
                        color={alerts > 0 ? "warning" : "neutral"}
                        subtext={alerts > 0 ? "Anomalies à contrôler" : "Aucun signal d'anomalie"}
                    />
                    <StatCard
                        title="Points bloquants"
                        value={blockers}
                        color={blockers > 0 ? "danger" : "neutral"}
                        subtext={blockers > 0 ? "Bloque la validation" : "Aucun blocage"}
                    />
                    <StatCard
                        title="Total Net à payer"
                        value={money(cockpit?.kpis?.totalNetAPayer)}
                        color="success"
                        subtext="Masse nette des virements"
                    />
                </div>

                <PayrollStepTable
                    title="État nominatif"
                    badgeCount={matchingCount}
                    subtitle="Montants enregistrés pour la campagne"
                    searchValue={query}
                    onSearchChange={eventOrValue => {
                        const val = typeof eventOrValue === 'string' ? eventOrValue : eventOrValue?.target?.value || '';
                        setQuery(val);
                        setPage(1);
                    }}
                    searchPlaceholder="Nom ou matricule…"
                    filterSlot={
                        <div className="flex items-center gap-1 text-xs" aria-label="Filtrer les bulletins">
                            {filterTabs.map(tab => {
                                const active = filter === tab.id;
                                return (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        onClick={() => { setFilter(tab.id); setPage(1); }}
                                        aria-pressed={active}
                                        className={`px-2.5 py-1 text-xs font-semibold inline-flex items-center gap-1.5 transition-colors cursor-pointer border ${
                                            active
                                                ? 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)] border-b-2 border-b-[var(--color-brand-500)] shadow-xs'
                                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)]/60'
                                        }`}
                                    >
                                        <span>{tab.label}</span>
                                        <span className="font-mono text-[10px] px-1 py-0.2 bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                                            {tab.count}
                                        </span>
                                    </button>
                                );
                            })}
                        </div>
                    }
                    columns={[
                        { key: 'salarie', label: 'Salarié', width: 'w-64' },
                        { key: 'brut', label: 'Salaire Brut', align: 'right' },
                        { key: 'cotisations', label: 'Cotisations', align: 'right' },
                        { key: 'irsa', label: 'IRSA', align: 'right' },
                        { key: 'net', label: 'Net à Payer', align: 'right' },
                        { key: 'signal', label: 'Signal', align: 'center' },
                        { key: 'action', label: 'Détail', align: 'center', width: 'w-20' },
                    ]}
                    loading={loading && !hasCurrentResults}
                    emptyMessage={filter === 'ALERTS' ? 'Aucun bulletin à examiner.' : 'Aucun bulletin trouvé.'}
                    pagination={{
                        currentPage: page,
                        totalItems: matchingCount,
                        pageSize: PAGE_SIZE,
                        onPageChange: setPage,
                    }}
                    ariaLabel="Bulletins de la campagne"
                >
                    {bulletins.map(bulletin => (
                        <Tr key={bulletin.idBulletin}>
                            <Td>
                                <PayrollEmployeeCell
                                    name={bulletin.nomPrenom || 'Nom non renseigné'}
                                    matricule={bulletin.matricule}
                                    subtitle={bulletin.nomSite}
                                />
                            </Td>
                            <Td className="text-right">
                                <PayrollMoneyCell value={bulletin.totalGainsBruts} />
                            </Td>
                            <Td className="text-right">
                                <PayrollMoneyCell value={bulletin.totalCotisationsSalariales} type="deduction" />
                            </Td>
                            <Td className="text-right">
                                <PayrollMoneyCell value={bulletin.irsaNet} type="deduction" />
                            </Td>
                            <Td className="text-right">
                                <PayrollMoneyCell value={bulletin.netAPayer} type="net" />
                            </Td>
                            <Td className="text-center">
                                {bulletin.aAlerte === true ? (
                                    <Badge variant="warning" size="xs" dot>À examiner</Badge>
                                ) : (
                                    <Badge variant="success" size="xs" dot>OK</Badge>
                                )}
                            </Td>
                            <Td className="text-center">
                                <Button
                                    variant="outline"
                                    size="xs"
                                    iconLeft={<Eye size={13} />}
                                    onClick={() => setSelectedBulletinId(bulletin.idBulletin)}
                                    aria-label={`Voir le bulletin de ${bulletin.nomPrenom || bulletin.matricule}`}
                                >
                                    Voir
                                </Button>
                            </Td>
                        </Tr>
                    ))}
                </PayrollStepTable>
            </div>

            <Suspense fallback={null}>
                {selectedBulletinId && (
                    <BulletinPaieDrawer
                        isOpen
                        idBulletin={selectedBulletinId}
                        onClose={() => setSelectedBulletinId(null)}
                        onRecalculateSuccess={async () => {
                            await refreshSilent();
                            await loadCockpit();
                        }}
                    />
                )}
            </Suspense>
        </PayrollCampaignShell>
    );
}
