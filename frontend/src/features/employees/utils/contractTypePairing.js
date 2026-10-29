const CONTRACT_TO_INTERVENANT = {
    STAGE: 'STAGIAIRE',
    TEMPORAIRE: 'TEMPORAIRE',
};

const INTERVENANT_TO_CONTRACT = {
    STAGIAIRE: 'STAGE',
    TEMPORAIRE: 'TEMPORAIRE',
};

export function pairContractAndIntervenant(formData, field, value, typesContrat, typesIntervenant, {
    lockTypeContrat = false,
    lockTypeIntervenant = false,
    allowedTypesContrat,
    allowedTypesIntervenant,
} = {}) {
    const updated = { ...formData, [field]: value };

    if (field === 'idTypeContrat' && !lockTypeIntervenant) {
        const selected = typesContrat.find(type => String(type.idTypeContrat) === String(value));
        const targetCode = CONTRACT_TO_INTERVENANT[selected?.code];
        if (targetCode && (!allowedTypesIntervenant || allowedTypesIntervenant.includes(targetCode))) {
            const target = typesIntervenant.find(type => type.code === targetCode);
            if (target) updated.idTypeIntervenant = String(target.idTypeIntervenant);
        }
    }

    if (field === 'idTypeIntervenant' && !lockTypeContrat) {
        const selected = typesIntervenant.find(type => String(type.idTypeIntervenant) === String(value));
        const targetCode = INTERVENANT_TO_CONTRACT[selected?.code];
        if (targetCode && (!allowedTypesContrat || allowedTypesContrat.includes(targetCode))) {
            const target = typesContrat.find(type => type.code === targetCode);
            if (target) updated.idTypeContrat = String(target.idTypeContrat);
        }
    }

    return updated;
}
