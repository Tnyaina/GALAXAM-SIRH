let nextClientKey = 0;

export function createSatelliteClientKey() {
    if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
    nextClientKey += 1;
    return `satellite-${Date.now()}-${nextClientKey}`;
}

export function retirerElement(elements, suppressions, index, champId) {
    const element = elements[index];
    return {
        elements: elements.filter((_, i) => i !== index),
        suppressions: element?.[champId] ? [...suppressions, element] : suppressions,
    };
}

export function annulerSuppression(elements, suppressions, index) {
    return {
        elements: [...elements, suppressions[index]],
        suppressions: suppressions.filter((_, i) => i !== index),
    };
}
