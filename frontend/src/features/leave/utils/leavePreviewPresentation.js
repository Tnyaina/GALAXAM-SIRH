export function formatLeaveDaysPreview(value, { loading = false, error = false } = {}) {
  if (value != null && Number.isFinite(Number(value))) {
    const number = Number(value);
    const sign = number < 0 ? '−' : '';
    return `${sign}${Math.abs(number).toFixed(2)} j`;
  }
  if (loading) return 'Chargement…';
  if (error) return 'Indisponible';
  return '—';
}
