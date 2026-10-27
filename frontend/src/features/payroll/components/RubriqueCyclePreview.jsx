import { useEffect, useState } from 'react';
import paieService from '@/features/payroll/api/paieService';
import { fmtDate } from '@/utils/dateUtils';

export default function RubriqueCyclePreview({ start, end }) {
    const [preview, setPreview] = useState(null);
    const [loading, setLoading] = useState(false);
    useEffect(() => {
        if (!start || !end) { setPreview(null); return undefined; }
        const controller = new AbortController();
        setLoading(true);
        paieService.getRubriqueCyclePreview(start, end, controller.signal)
            .then(setPreview).catch(error => { if (error.name !== 'CanceledError' && error.name !== 'AbortError') setPreview(null); })
            .finally(() => { if (!controller.signal.aborted) setLoading(false); });
        return () => controller.abort();
    }, [start, end]);
    if (!start || !end) return <p className="text-xs text-[var(--color-fg-muted)]">Une rubrique manuelle doit couvrir tout le cycle de paie, du 26 au 25. Aucun prorata automatique.</p>;
    if (loading || !preview) return <p className="text-xs text-[var(--color-fg-muted)]">{loading ? 'Vérification des cycles…' : 'Aperçu des cycles indisponible.'}</p>;
    const { first, last, startCutsCycle, endCutsCycle, noCompleteCycle } = preview;
    return <div aria-live="polite" className={`space-y-1 border p-3 text-xs ${startCutsCycle || endCutsCycle || noCompleteCycle ? 'border-[var(--color-warning-200)] bg-[var(--color-warning-50)] text-[var(--color-warning-800)]' : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>
        <p className="font-semibold">Application sur les cycles 26–25</p>
        {first && <p>Premier cycle entièrement couvert : {fmtDate(first.start)} – {fmtDate(first.end)}.</p>}
        {last && <p>Dernier cycle entièrement couvert : {fmtDate(last.start)} – {fmtDate(last.end)}.</p>}
        {noCompleteCycle
            ? <p>Aucun cycle complet entre ces dates : cette version ne pourra pas servir à une variable manuelle.</p>
            : (startCutsCycle || endCutsCycle) && <p>Une date coupe un cycle : la rubrique ne s’appliquera pas à ce cycle partiel.</p>}
    </div>;
}
