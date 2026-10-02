import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowRight, Eye } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import StatCard from '@/components/ui/data-display/StatCard';
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
            <SEO title="Contrôle des bulletins | GALAXAM GRH" description="Revue nominative des bulletins de paie." />

            <div className="space-y-4">
                {error && <Alert type="error" message={error} onClose={() => setError(null)} />}

                <div className="grid grid-cols-2 xl:grid-cols-4 gap-3">
                    <StatCard title="Bulletins calculés" value={totalBulletins} color="neutral" />
                    <StatCard title="À examiner" value={alerts} color="neutral" subtext="Signaux du calcul" />
                    <StatCard title="Points bloquants" value={blockers} color="neutral" subtext="Campagne" />
                    <StatCard title="Net à payer" value={money(cockpit?.kpis?.totalNetAPayer)} color="brand" />
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
                        <div className="flex items-center gap-1 ml-2" aria-label="Filtrer les bulletins">
                            {filterTabs.map(tab => (
                                <button
                                    key={tab.id}
                                    type="button"
                                    onClick={() => { setFilter(tab.id); setPage(1); }}
                                    className={`px-2.5 py-0.5 text-xs font-medium border cursor-pointer transition-colors ${
                                        filter === tab.id
                                            ? 'border-[var(--color-brand-500)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]'
                                            : 'border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                >
                                    {tab.label}
                                </button>
                            ))}
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
                    loading={loading}
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
                        <tr
                            key={bulletin.idBulletin}
                            className="hover:bg-[var(--color-bg-subtle)] transition-colors group"
                        >
                            <td className="px-4 py-3.5">
                                <PayrollEmployeeCell
                                    name={bulletin.nomPrenom || 'Nom non renseigné'}
                                    matricule={bulletin.matricule}
                                    subtitle={bulletin.nomSite}
                                />
                            </td>
                            <td className="px-4 py-3.5 text-right">
                                <PayrollMoneyCell value={bulletin.totalGainsBruts} />
                            </td>
                            <td className="px-4 py-3.5 text-right">
                                <PayrollMoneyCell value={bulletin.totalCotisationsSalariales} type="deduction" />
                            </td>
                            <td className="px-4 py-3.5 text-right">
                                <PayrollMoneyCell value={bulletin.irsaNet} type="deduction" />
                            </td>
                            <td className="px-4 py-3.5 text-right">
                                <PayrollMoneyCell value={bulletin.netAPayer} type="net" />
                            </td>
                            <td className="px-4 py-3.5 text-center">
                                {bulletin.aAlerte === true ? (
                                    <Badge variant="warning" size="xs">⚠ À examiner</Badge>
                                ) : (
                                    <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)]">
                                        <span className="w-1.5 h-1.5 rounded-full bg-[var(--color-success-500)] inline-block" />
                                        OK
                                    </span>
                                )}
                            </td>
                            <td className="px-4 py-3.5 text-center">
                                <Button
                                    variant="outline"
                                    size="xs"
                                    iconLeft={<Eye size={13} />}
                                    onClick={() => setSelectedBulletinId(bulletin.idBulletin)}
                                    aria-label={`Voir le bulletin de ${bulletin.nomPrenom || bulletin.matricule}`}
                                >
                                    Voir
                                </Button>
                            </td>
                        </tr>
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
