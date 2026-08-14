import Input, { Select, Checkbox } from '@/components/ui/forms/Input';

export default function ContratFieldsSection({
    formData,
    setField,
    filiales = [],
    typesContrat = [],
    typesIntervenant = [],
}) {
    return (
        <div className="space-y-4">
            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-1 border-b border-ink-150 dark:border-ink-700">
                1. Contrat de Travail
            </h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <Select
                    label="Filiale d'Appartenance"
                    required
                    value={formData.idFiliale}
                    onChange={(e) => setField('idFiliale', e.target.value)}
                >
                    <option value="">-- Sélectionner une filiale --</option>
                    {filiales.map((f, idx) => {
                        const idFil = f.idFiliale ?? idx;
                        return (
                            <option key={idFil} value={idFil}>
                                {f.codeFiliale} — {f.raisonSociale}
                            </option>
                        );
                    })}
                </Select>

                <Select
                    label="Type de Contrat"
                    required
                    value={formData.idTypeContrat}
                    onChange={(e) => setField('idTypeContrat', e.target.value)}
                >
                    <option value="">-- Sélectionner un type --</option>
                    {typesContrat.map((tc, idx) => {
                        const idTc = tc.idTypeContrat ?? idx;
                        return (
                            <option key={idTc} value={idTc}>
                                {tc.code} — {tc.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Select
                    label="Type d'Intervenant"
                    required
                    value={formData.idTypeIntervenant}
                    onChange={(e) => setField('idTypeIntervenant', e.target.value)}
                >
                    <option value="">-- Sélectionner --</option>
                    {typesIntervenant.map((ti, idx) => {
                        const idTi = ti.idTypeIntervenant ?? idx;
                        return (
                            <option key={idTi} value={idTi}>
                                {ti.code} — {ti.libelle}
                            </option>
                        );
                    })}
                </Select>

                <Input
                    label="N° de Contrat"
                    value={formData.numeroContrat}
                    onChange={(e) => setField('numeroContrat', e.target.value)}
                    placeholder="Auto si vide (ex: CT-2026-001)"
                    hint="Laissez vide pour auto-génération"
                />

                <Input
                    label="Date de Signature"
                    type="date"
                    required
                    value={formData.dateSignature}
                    onChange={(e) => setField('dateSignature', e.target.value)}
                />

                <Input
                    label="Date de Début d'effet"
                    type="date"
                    required
                    value={formData.dateDebut}
                    onChange={(e) => setField('dateDebut', e.target.value)}
                />

                <Input
                    label="Date de Fin (CDD / Stage / Interim)"
                    type="date"
                    value={formData.dateFin}
                    onChange={(e) => setField('dateFin', e.target.value)}
                    hint="Optionnel si CDI"
                />

                <div className="flex items-end pb-2">
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
