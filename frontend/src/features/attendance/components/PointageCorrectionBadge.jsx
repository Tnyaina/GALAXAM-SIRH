export default function PointageCorrectionBadge({ minutes, sortieBadge }) {
    const value = Number(minutes) || 0;
    if (!value) return null;
    const sign = value > 0 ? '+' : '−';
    const match = /^([01]\d|2[0-3]):([0-5]\d)/.exec(String(sortieBadge || ''));
    const adjusted = match
        ? ((Number(match[1]) * 60 + Number(match[2]) + value + 1440) % 1440)
        : null;
    const retained = adjusted == null ? '' : `${String(Math.floor(adjusted / 60)).padStart(2, '0')}:${String(adjusted % 60).padStart(2, '0')}`;
    const detail = retained ? `Sortie badgée ${match[0]} · sortie retenue ${retained}` : 'Temps corrigé';
    return <span className="inline-flex items-center whitespace-nowrap border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] px-2 py-1 text-xs font-semibold leading-none text-[var(--color-brand-700)]" title={detail}>
        Corrigé {sign}{Math.abs(value)} min
    </span>;
}
