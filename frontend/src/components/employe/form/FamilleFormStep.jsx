import { Plus, Trash2, CheckCircle2, AlertCircle } from 'lucide-react';
import Input, { Select, Checkbox } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { fmtDateInput, getTodayDateString } from '@/utils/dateUtils';

function evaluateIrsaEligibility(enf) {
    if (!enf.aCharge) {
        return { eligible: false, age: null, motif: 'Non déclaré à charge du foyer' };
    }
    if (enf.estHandicape) {
        return { eligible: true, age: null, motif: 'Éligible sans limite d\'âge (Situation de handicap)' };
    }
    if (!enf.dateNaissance) {
        return { eligible: false, age: null, motif: 'Date de naissance requise' };
    }
    const dob = new Date(enf.dateNaissance);
    if (isNaN(dob.getTime())) {
        return { eligible: false, age: null, motif: 'Date de naissance invalide' };
    }
    const now = new Date();
    let age = now.getFullYear() - dob.getFullYear();
    const m = now.getMonth() - dob.getMonth();
    if (m < 0 || (m === 0 && now.getDate() < dob.getDate())) {
        age--;
    }

    if (age < 21) {
        return { eligible: true, age, motif: `Éligible d'office (< 21 ans révolus : ${age} ans)` };
    }
    if (age < 25 && enf.estEtudiant) {
        if (!enf.certificatFourni) {
            return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat de scolarité manquant` };
        }
        if (enf.dateFinScolarite) {
            const dFin = new Date(enf.dateFinScolarite);
            if (dFin < now) {
                return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat expiré` };
            }
        }
        return { eligible: true, age, motif: `Éligible étudiant majeur (21-25 ans : ${age} ans avec certificat valide)` };
    }
    return { eligible: false, age, motif: `Non déductible fiscalement (${age} ans, limite 21 ans atteinte sans statut étudiant/handicap)` };
}

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
            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-3">Conjoint (Si marié)</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="Nom Conjoint" value={formData.conjoint.nom} onChange={e => setNested('conjoint', 'nom', e.target.value)} placeholder="Nom" />
                    <Input label="Prénom Conjoint" value={formData.conjoint.prenom} onChange={e => setNested('conjoint', 'prenom', e.target.value)} placeholder="Prénom" />
                    <Input label="Date Naissance" type="date" max={fmtDateInput(new Date(new Date().setFullYear(new Date().getFullYear() - 16)))} value={formData.conjoint.dateNaissance} onChange={e => setNested('conjoint', 'dateNaissance', e.target.value)} />
                    <Input label="Société / Employeur" value={formData.conjoint.societe} onChange={e => setNested('conjoint', 'societe', e.target.value)} placeholder="Société du conjoint" />
                    <Input label="Fonction / Profession" value={formData.conjoint.fonction} onChange={e => setNested('conjoint', 'fonction', e.target.value)} placeholder="Poste / Profession du conjoint" />
                </div>
            </div>

            {/* Enfants */}
            <div>
                <div className="flex items-center justify-between mb-3">
                    <div>
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            Enfants ({formData.enfants.length})
                        </h3>
                        <p className="text-[11px] text-[var(--color-fg-subtle)]">
                            Règle fiscale IRSA : réduction de 2 000 Ar/mois pour enfant &lt; 21 ans (ou &lt; 25 ans si étudiant avec certificat, ou sans limite d'âge si handicap).
                        </p>
                    </div>
                    <Button type="button" onClick={addEnfant} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                        Ajouter
                    </Button>
                </div>
                {formData.enfants.length === 0 ? (
                    <p className="text-xs text-[var(--color-fg-muted)] text-center py-6 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                        Aucun enfant enregistré.
                    </p>
                ) : (
                    <div className="space-y-4">
                        {formData.enfants.map((enf, idx) => {
                            const evalIrsa = evaluateIrsaEligibility(enf);
                            return (
                                <div key={idx} className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-4 relative">
                                    <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2 pr-8">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-bold text-[var(--color-fg)]">Enfant #{idx + 1}</span>
                                            {evalIrsa.age !== null && (
                                                <span className="text-[10px] px-2 py-0.5 font-mono font-medium bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                                                    {evalIrsa.age} ans
                                                </span>
                                            )}
                                        </div>
                                        <button
                                            type="button"
                                            onClick={() => removeEnfant(idx)}
                                            className="absolute top-3 right-3 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 p-1 cursor-pointer"
                                            aria-label="Supprimer l'enfant"
                                        >
                                            <Trash2 size={14} />
                                        </button>
                                    </div>

                                    {/* Données d'état civil */}
                                    <div className="grid grid-cols-1 md:grid-cols-4 gap-3">
                                        <Input label="Nom" value={enf.nom} onChange={e => setEnfant(idx, 'nom', e.target.value)} placeholder="Nom" />
                                        <Input label="Prénom" value={enf.prenom} onChange={e => setEnfant(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                        <Input label="Date Naissance" type="date" max={getTodayDateString()} value={enf.dateNaissance} onChange={e => setEnfant(idx, 'dateNaissance', e.target.value)} />
                                        <Select label="Sexe" value={enf.sexe} onChange={e => setEnfant(idx, 'sexe', e.target.value)}>
                                            <option value="M">Garçon (M)</option>
                                            <option value="F">Fille (F)</option>
                                        </Select>
                                    </div>

                                    {/* Statuts & Déductibilité fiscale */}
                                    <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-3">
                                        <div className="flex flex-wrap items-center gap-5 text-xs">
                                            <Checkbox
                                                label="Enfant à charge du foyer"
                                                checked={enf.aCharge}
                                                onChange={e => setEnfant(idx, 'aCharge', e.target.checked)}
                                            />
                                            <Checkbox
                                                label="Étudiant majeur (18 - 25 ans)"
                                                checked={enf.estEtudiant}
                                                onChange={e => setEnfant(idx, 'estEtudiant', e.target.checked)}
                                            />
                                            <Checkbox
                                                label="En situation de handicap / infirmité"
                                                checked={enf.estHandicape}
                                                onChange={e => setEnfant(idx, 'estHandicape', e.target.checked)}
                                            />
                                        </div>

                                        {/* Champs conditionnels si étudiant */}
                                        {enf.estEtudiant && (
                                            <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] grid grid-cols-1 md:grid-cols-2 gap-3 items-center">
                                                <Input
                                                    label="Date fin de scolarité"
                                                    type="date"
                                                    value={enf.dateFinScolarite || ''}
                                                    onChange={e => setEnfant(idx, 'dateFinScolarite', e.target.value)}
                                                />
                                                <div className="flex items-end pb-1 pt-4 md:pt-0">
                                                    <Checkbox
                                                        label="Certificat de scolarité officiel fourni"
                                                        checked={enf.certificatFourni}
                                                        onChange={e => setEnfant(idx, 'certificatFourni', e.target.checked)}
                                                    />
                                                </div>
                                            </div>
                                        )}

                                        {/* Indicateur visuel d'éligibilité fiscale IRSA */}
                                        <div className={`p-2.5 flex items-start gap-2 text-xs border ${
                                            evalIrsa.eligible
                                                ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-700 dark:text-emerald-300'
                                                : 'bg-amber-500/10 border-amber-500/30 text-amber-700 dark:text-amber-300'
                                        }`}>
                                            {evalIrsa.eligible ? (
                                                <CheckCircle2 size={15} className="mt-0.5 shrink-0 text-emerald-600 dark:text-emerald-400" />
                                            ) : (
                                                <AlertCircle size={15} className="mt-0.5 shrink-0 text-amber-600 dark:text-amber-400" />
                                            )}
                                            <div>
                                                <span className="font-bold mr-1">
                                                    {evalIrsa.eligible ? 'Déduction IRSA active (-2 000 Ar/mois) :' : 'Déduction fiscale non applicable :'}
                                                </span>
                                                <span className="opacity-90">{evalIrsa.motif}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
