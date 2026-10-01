import Input, { Select, Checkbox } from '@/components/ui/forms/Input';

export default function ContratFieldsSection({
    formData,
    setField,
    filiales = [],
    typesContrat = [],
    typesIntervenant = [],
    lockTypeContrat = false,
    lockTypeIntervenant = false,
    signatureOptional = false,
    dateDebutLabel = 'Date de début',
    dateDebutHint,
}) {
    const selectedType = typesContrat.find(
        (tc) => String(tc.idTypeContrat) === String(formData.idTypeContrat)
    );
    const exigeDateFin = selectedType?.exigeDateFin ?? false;

    return (
        <div className="space-y-4 font-sans">
            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] pb-1 border-b border-[var(--color-border-subtle)]">
                1. Contrat de travail
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Select
                    label="Filiale employeuse"
                    required
                    value={formData.idFiliale}
                    onChange={(e) => setField('idFiliale', e.target.value)}
                >
                    <option value="">Sélectionner une filiale</option>
                    {filiales.map((f, idx) => {
                        const idFil = f.idFiliale ?? idx;
                        return (
                            <option key={idFil} value={idFil}>
                                {f.codeFiliale} · {f.raisonSociale}
                            </option>
                        );
                    })}
                </Select>

                <Select
                    label={lockTypeContrat ? "Type de contrat (imposé)" : "Type de contrat"}
                    required
                    disabled={lockTypeContrat}
                    value={formData.idTypeContrat}
                    onChange={(e) => setField('idTypeContrat', e.target.value)}
                    hint={lockTypeContrat ? "Défini selon l'événement choisi" : undefined}
                >
                    <option value="">Sélectionner un type</option>
                    {typesContrat.map((tc, idx) => {
                        const idTc = tc.idTypeContrat ?? idx;
                        return (
                            <option key={idTc} value={idTc}>
                                {tc.code} · {tc.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Select
                    label={lockTypeIntervenant ? "Type d'intervenant (imposé)" : "Type d'intervenant"}
                    required
                    disabled={lockTypeIntervenant}
                    value={formData.idTypeIntervenant}
                    onChange={(e) => setField('idTypeIntervenant', e.target.value)}
                    hint={lockTypeIntervenant ? "Défini selon l'événement choisi" : undefined}
                >
                    <option value="">Sélectionner un intervenant</option>
                    {typesIntervenant.map((ti, idx) => {
                        const idTi = ti.idTypeIntervenant ?? idx;
                        return (
                            <option key={idTi} value={idTi}>
                                {ti.code} · {ti.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Input
                    label="Numéro de contrat"
                    value={formData.numeroContrat}
                    onChange={(e) => setField('numeroContrat', e.target.value)}
                    placeholder="Généré automatiquement si vide"
                    hint="Laisser vide pour génération automatique"
                />

            </div>
            <h4 className="text-sm font-medium text-[var(--color-fg)]">Dates du contrat</h4>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Input
                    label={dateDebutLabel}
                    type="date"
                    required
                    value={formData.dateDebut}
                    onChange={(e) => setField('dateDebut', e.target.value)}
                    hint={dateDebutHint}
                />

                <Input
                    label="Date de fin"
                    type="date"
                    required={exigeDateFin}
                    disabled={!exigeDateFin}
                    min={formData.dateDebut || undefined}
                    value={exigeDateFin ? formData.dateFin : ''}
                    onChange={(e) => setField('dateFin', e.target.value)}
                    hint={exigeDateFin ? "Obligatoire pour ce type de contrat" : "Sans date de fin pour ce type de contrat"}
                />

                <Input
                    label="Date de signature"
                    type="date"
                    required={!signatureOptional}
                    value={formData.dateSignature}
                    onChange={(e) => setField('dateSignature', e.target.value)}
                    hint={signatureOptional ? "À renseigner lorsque le contrat est signé" : undefined}
                />

                <div className="flex items-center">
                    <Checkbox
                        label="Comptabilisé dans l'effectif légal"
                        checked={formData.comptabiliseEffectif}
                        onChange={(e) => setField('comptabiliseEffectif', e.target.checked)}
                    />
                </div>
            </div>
        </div>
    );
}

export function PeriodeEssaiFieldsSection({
    formData,
    setField,
}) {
    return (
        <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] space-y-3">
            <Checkbox
                label="Ce contrat comporte une période d'essai initiale"
                checked={formData.aPeriodeEssai}
                onChange={(e) => setField('aPeriodeEssai', e.target.checked)}
            />

            {formData.aPeriodeEssai && (
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-2">
                    <Select
                        label="Durée de la période d'essai"
                        value={formData.dureeEssaiMois}
                        onChange={(e) => setField('dureeEssaiMois', e.target.value)}
                    >
                        <option value="1">1 mois</option>
                        <option value="2">2 mois</option>
                        <option value="3">3 mois</option>
                        <option value="6">6 mois</option>
                    </Select>

                    <Input
                        label="Date de fin d'essai"
                        type="date"
                        min={formData.dateDebut || undefined}
                        value={formData.dateFinEssai}
                        onChange={(e) => setField('dateFinEssai', e.target.value)}
                        hint="Calculée selon la date de début"
                    />
                </div>
            )}
        </div>
    );
}

