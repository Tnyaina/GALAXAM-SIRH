import { fmtDate } from '@/utils/dateUtils';
import { cycleContaining, firstCoveredCycle, lastCoveredCycle } from './rubriqueCycle';

const cycleLabel = cycle => `${fmtDate(cycle.start)} – ${fmtDate(cycle.end)}`;

export default function RubriqueCyclePreview({ start, end }) {
    const first = firstCoveredCycle(start);
    const last = lastCoveredCycle(end);
    if (!first && !last) return <p className="text-xs text-[var(--color-fg-muted)]">Une rubrique manuelle doit couvrir tout le cycle de paie, du 26 au 25. Aucun prorata automatique.</p>;

    const startCutsCycle = first && start !== cycleContaining(start).start;
    const endCutsCycle = last && end !== cycleContaining(end).end;
    const noCompleteCycle = first && last && first.start > last.start;
    return <div aria-live="polite" className={`space-y-1 border p-3 text-xs ${startCutsCycle || endCutsCycle || noCompleteCycle ? 'border-[var(--color-warning-200)] bg-[var(--color-warning-50)] text-[var(--color-warning-800)]' : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>
        <p className="font-semibold">Application sur les cycles 26–25</p>
        {first && <p>Premier cycle entièrement couvert : {cycleLabel(first)}.</p>}
        {last && <p>Dernier cycle entièrement couvert : {cycleLabel(last)}.</p>}
        {noCompleteCycle
            ? <p>Aucun cycle complet entre ces dates : cette version ne pourra pas servir à une variable manuelle.</p>
            : (startCutsCycle || endCutsCycle) && <p>Une date coupe un cycle : la rubrique ne s’appliquera pas à ce cycle partiel.</p>}
    </div>;
}
