/** Les motifs familiaux décrivent l'événement ; seul le type peut imposer une durée. */
export function hasFixedLeaveDuration({ fixedDays, maternityExtension = false, isMaternity = false }) {
  return Boolean(fixedDays || (maternityExtension && isMaternity));
}
