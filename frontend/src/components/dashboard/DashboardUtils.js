export function fmtDate(d) {
    if (!d) return '—';
    return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
}

export function fmtRelative(ts) {
    if (!ts) return '';
    const d = new Date(ts);
    const diffH = Math.floor((Date.now() - d) / 3600000);
    if (diffH < 1) return 'il y a quelques min';
    if (diffH < 24) return `il y a ${diffH}h`;
    return `il y a ${Math.floor(diffH / 24)}j`;
}
