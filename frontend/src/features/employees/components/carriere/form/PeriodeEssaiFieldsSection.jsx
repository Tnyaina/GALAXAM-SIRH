import Input, { Select, Checkbox } from '@/components/ui/forms/Input';

export default function PeriodeEssaiFieldsSection({
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
