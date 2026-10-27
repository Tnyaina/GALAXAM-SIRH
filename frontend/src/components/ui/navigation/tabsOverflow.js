export function hasHorizontalOverflow(element) {
  return Boolean(element && element.scrollWidth > element.clientWidth + 1);
}
