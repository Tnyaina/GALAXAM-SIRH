import { Plus, Trash2 } from 'lucide-react';
import Input, { Select, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';

export default function FamilleFormStep({
    formData,
    setNested,
    addEnfant,
    removeEnfant,
    setEnfant,
}) {
    return (
        <div className="space-y-6">
            {/* Conjoint */}
            <div className="bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 p-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Conjoint (Si marié)</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="Nom Conjoint" value={formData.conjoint.nom} onChange={e => setNested('conjoint', 'nom', e.target.value)} placeholder="Nom" />
                    <Input label="Prénom Conjoint" value={formData.conjoint.prenom} onChange={e => setNested('conjoint', 'prenom', e.target.value)} placeholder="Prénom" />
                    <Input label="Date Naissance" type="date" max={new Date(new Date().setFullYear(new Date().getFullYear() - 16)).toISOString().split('T')[0]} value={formData.conjoint.dateNaissance} onChange={e => setNested('conjoint', 'dateNaissance', e.target.value)} />
                    <Input label="Société / Employeur" value={formData.conjoint.societe} onChange={e => setNested('conjoint', 'societe', e.target.value)} placeholder="Société du conjoint" />
                    <Input label="Fonction / Profession" value={formData.conjoint.fonction} onChange={e => setNested('conjoint', 'fonction', e.target.value)} placeholder="Poste / Profession du conjoint" />
                </div>
            </div>

            {/* Enfants */}
            <div>
                <div className="flex items-center justify-between mb-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400">Enfants ({formData.enfants.length})</h3>
                    <Button type="button" onClick={addEnfant} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                        Ajouter
                    </Button>
                </div>
                {formData.enfants.length === 0 ? (
                    <p className="text-xs text-ink-400 text-center py-6 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700">
                        Aucun enfant enregistré.
                    </p>
                ) : (
                    <div className="space-y-3">
                        {formData.enfants.map((enf, idx) => (
                            <div key={idx} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 relative">
                                <button type="button" onClick={() => removeEnfant(idx)} className="absolute top-3 right-3 text-danger-500 hover:bg-danger-500/10 p-1 rounded-none  cursor-pointer">
                                    <Trash2 size={14} />
                                </button>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pr-8">
                                    <Input label="Nom" value={enf.nom} onChange={e => setEnfant(idx, 'nom', e.target.value)} placeholder="Nom" />
                                    <Input label="Prénom" value={enf.prenom} onChange={e => setEnfant(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                    <Input label="Date Naissance" type="date" max={new Date().toISOString().split('T')[0]} value={enf.dateNaissance} onChange={e => setEnfant(idx, 'dateNaissance', e.target.value)} />
                                    <Select label="Sexe" value={enf.sexe} onChange={e => setEnfant(idx, 'sexe', e.target.value)}>
                                        <option value="M">Garçon (M)</option>
                                        <option value="F">Fille (F)</option>
                                    </Select>
                                    <div className="flex items-end pb-1">
                                        <Checkbox label="Enfant à charge" checked={enf.aCharge} onChange={e => setEnfant(idx, 'aCharge', e.target.checked)} />
                                    </div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
}
