function dateParts(iso) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(iso || '')) return null;
    const [year, month, day] = iso.split('-').map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day
        ? { year, month, day } : null;
}

function isoDate(year, month, day) {
    const date = new Date(Date.UTC(year, month - 1, day));
    return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
}

function cycleStarting(year, month) {
    return { start: isoDate(year, month, 26), end: isoDate(year, month + 1, 25) };
}

export function cycleContaining(iso) {
    const parts = dateParts(iso);
    if (!parts) return null;
    return parts.day >= 26 ? cycleStarting(parts.year, parts.month) : cycleStarting(parts.year, parts.month - 1);
}

export function firstCoveredCycle(start) {
    const current = cycleContaining(start);
    if (!current) return null;
    if (start === current.start) return current;
    const [year, month] = current.start.split('-').map(Number);
    return cycleStarting(year, month + 1);
}

export function lastCoveredCycle(end) {
    const current = cycleContaining(end);
    if (!current) return null;
    if (end >= current.end) return current;
    const [year, month] = current.start.split('-').map(Number);
    return cycleStarting(year, month - 1);
}
