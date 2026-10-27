const ACOMPTE_CODES = new Set(['RETENUE_ACOMPTE_15', 'ACOMPTE_15', 'AVANCE_QUINZAINE']);
const AVANCE_CODES = new Set(['RETENUE_AVANCE_SPECIALE', 'AVANCE_SPECIALE']);

export function summarizeNetDeductions(lines, total) {
    let acomptes = 0;
    let avances = 0;
    for (const line of Array.isArray(lines) ? lines : []) {
        if (String(line.sens || '').toUpperCase() !== 'RETENUE') continue;
        const code = String(line.codeRubrique ?? line.code_rubrique ?? '').toUpperCase();
        const amount = Number(line.montantSalarial ?? line.montant_salarial ?? 0);
        if (ACOMPTE_CODES.has(code)) acomptes += amount;
        else if (AVANCE_CODES.has(code)) avances += amount;
    }
    const totalRetenues = Number(total || 0);
    return { acomptes, avances, autres: totalRetenues - acomptes - avances };
}
