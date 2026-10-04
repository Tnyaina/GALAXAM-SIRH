/**
 * Calcule automatiquement la date de fin déterministe d'une période de référence de 12 mois.
 * Règle légale : dateDebut + 12 mois - 1 jour.
 */
export function calculerDateFinAutomatique(dateDebutStr) {
  if (!dateDebutStr) return '';
  const d = new Date(dateDebutStr + 'T00:00:00');
  if (isNaN(d.getTime())) return '';
  const month = d.getMonth();
  d.setFullYear(d.getFullYear() + 1);
  // Java LocalDate.plusMonths borne le 29 février au 28 février.
  if (d.getMonth() !== month) d.setDate(0);
  d.setDate(d.getDate() - 1);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

