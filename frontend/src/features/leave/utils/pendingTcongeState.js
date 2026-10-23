export function resolvePendingTcongeResult(result) {
  if (result.status === 'rejected') {
    return { status: 'error', periods: [], error: result.reason };
  }
  if (!Array.isArray(result.value)) {
    return { status: 'error', periods: [], error: new Error('Réponse TCONGE invalide.') };
  }
  return { status: 'success', periods: result.value, error: null };
}
