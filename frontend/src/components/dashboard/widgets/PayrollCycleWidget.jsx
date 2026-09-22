import { Link } from 'react-router-dom';
import { ArrowRight, CalendarRange, CircleAlert, WalletCards } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';

const stateVariant = (state) => ({ COMPLETED: 'success', CURRENT: 'primary', WARNING: 'warning', STALE: 'warning', BLOCKED: 'danger', LOCKED: 'neutral' }[state] || 'neutral');

export default function PayrollCycleWidget({ className = '' }) {
    const { context, loading } = usePayrollCampaign();
    const period = context?.selectedPeriod || context?.currentPeriod;
    const nextStep = context?.nextStep;
    const visibleSteps = (context?.campaignSteps || []).slice(0, 5);
    const issues = context?.issues || [];
    const primaryIssue = issues[0];
    const issueMessage = typeof primaryIssue === 'string'
        ? primaryIssue
        : (primaryIssue?.message || primaryIssue?.label || primaryIssue?.code || null);

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center justify-between">
                <div className="flex items-center gap-2"><WalletCards size={16} className="text-[var(--color-brand-600)]" /><CardTitle>Cycle Pointage → Paie</CardTitle></div>
                {period && <Badge variant="primary" size="sm">{period.code || period.libelle}</Badge>}
            </CardHeader>
            <CardBody className="flex-1">
                {loading && <div className="space-y-3"><Skeleton className="h-5 w-2/3" /><Skeleton className="h-14 w-full" /></div>}
                {!loading && !period && <EmptyState compact description="Aucune période de paie sélectionnée. Créez ou ouvrez une période pour démarrer le cycle." />}
                {!loading && period && <>
                    <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-1.5"><CalendarRange size={14} /> {period.startDate || '—'} → {period.endDate || '—'}</p>
                    <div className="mt-4 grid grid-cols-2 sm:grid-cols-5 border border-[var(--color-border-subtle)]">
                        {visibleSteps.map((step) => <div key={step.code} className="p-2.5 border-b sm:border-b-0 sm:border-r last:border-r-0 border-[var(--color-border-subtle)]">
                            <Badge variant={stateVariant(step.state)} size="xs">{step.state === 'COMPLETED' ? 'Terminé' : step.state === 'CURRENT' ? 'En cours' : 'À venir'}</Badge>
                            <p className="mt-1 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg)] truncate" title={step.label}>{step.label}</p>
                        </div>)}
                    </div>
                    {issueMessage && <p className="mt-3 flex gap-2 text-[11px] text-[var(--color-danger-600)]"><CircleAlert size={14} className="shrink-0" />{issueMessage}</p>}
                    {nextStep?.destination && <Link to={nextStep.destination} className="mt-4 inline-flex items-center gap-2 text-xs font-bold text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)]">{nextStep.label || 'Reprendre le cycle'} <ArrowRight size={14} /></Link>}
                </>}
            </CardBody>
        </Card>
    );
}
