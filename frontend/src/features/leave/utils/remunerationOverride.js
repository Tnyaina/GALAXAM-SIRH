export function remunerationOverridePayload({ administrator, value, originalValue, editing = false }) {
  if (!administrator) return {};
  if (value !== undefined && value !== null) return { estRemunere: Boolean(value) };
  if (editing && originalValue !== undefined && originalValue !== null) {
    return { utiliserRemunerationParDefaut: true };
  }
  return {};
}
