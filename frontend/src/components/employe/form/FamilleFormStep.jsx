import { Plus, Trash2, Baby, Users } from 'lucide-react';
import Input, { Select, Checkbox, AutocompleteInput } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDateInput, getTodayDateString } from '@/utils/dateUtils';
import { VILLES_MADAGASCAR } from '@/constants/madagascarGeo';
import {
    GENRES_ENFANT,
    PROFESSIONS_CONJOINT_SUGGESTIONS
} from '@/constants/employeConstants';
import useFiscalConfig, { calculateAge } from '@/hooks/useFiscalConfig';

function evaluateIrsaEligibilityWithConfig(enf, fiscalConfig) {
    if (!enf.aCharge) {
        return { eligible: false, age: null, motif: 'Non déclaré à charge du foyer' };
    }
    if (enf.estHandicape) {
        return { eligible: true, age: null, motif: 'Éligible sans limite d\'âge (Situation de handicap attestée)' };
    }
    if (!enf.dateNaissance) {
        return { eligible: false, age: null, motif: 'Date de naissance requise pour calcul fiscal' };
    }
    const age = calculateAge(enf.dateNaissance);
    if (age === null) {
        return { eligible: false, age: null, motif: 'Date de naissance invalide' };
    }
    const now = new Date();
    const ageLimiteDefaut = fiscalConfig?.ageLimiteDefaut ?? 21;
    const ageLimiteEtudiant = fiscalConfig?.ageLimiteEtudiant ?? 25;

    if (age < ageLimiteDefaut) {
        return { eligible: true, age, motif: `Éligible d'office (< ${ageLimiteDefaut} ans révolus : ${age} ans)` };
    }
    if (age < ageLimiteEtudiant && enf.estEtudiant) {
        if (!enf.certificatFourni) {
            return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat de scolarité officiel requis` };
        }
        if (enf.dateFinScolarite) {
            const dFin = new Date(enf.dateFinScolarite);
            if (dFin < now) {
                return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat de scolarité expiré` };
            }
        }
        return { eligible: true, age, motif: `Éligible étudiant majeur (${ageLimiteDefaut}-${ageLimiteEtudiant} ans : ${age} ans avec certificat valide)` };
    }
    return { eligible: false, age, motif: `Non déductible fiscalement (${age} ans, limite légale de ${ageLimiteDefaut} ans atteinte)` };
}

export default function FamilleFormStep({
    formData,
    setNested,
    addEnfant,
    removeEnfant,
    setEnfant,
    villesStandards = VILLES_MADAGASCAR,
}) {
    const { fiscalConfig } = useFiscalConfig();
    const evaluateIrsa = (enf) => evaluateIrsaEligibilityWithConfig(enf, fiscalConfig);
    const enfantsEligiblesCount = formData.enfants.filter(e => evaluateIrsa(e).eligible).length;
    const totalDeductionAr = enfantsEligiblesCount * fiscalConfig.deductionParCharge;
    return (
        <div className="space-y-6">
            {/* Conjoint */}
            <Card padding className="bg-[var(--color-bg-subtle)]/60">
                <div className="flex items-center justify-between mb-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Users size={14} className="text-brand-500" />
                        Conjoint (Si marié)
                    </h3>
                    <Badge variant="default" size="xs">
                        Optionnel
                    </Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="Nom Conjoint" value={formData.conjoint.nom} onChange={e => setNested('conjoint', 'nom', e.target.value)} placeholder="Nom" />
                    <Input label="Prénom Conjoint" value={formData.conjoint.prenom} onChange={e => setNested('conjoint', 'prenom', e.target.value)} placeholder="Prénom" />
                    <Input label="Date Naissance" type="date" max={fmtDateInput(new Date(new Date().setFullYear(new Date().getFullYear() - 16)))} value={formData.conjoint.dateNaissance} onChange={e => setNested('conjoint', 'dateNaissance', e.target.value)} />
                    <AutocompleteInput 
                        label="Lieu de Naissance" 
                        value={formData.conjoint.lieuNaissance || ''} 
                        onChange={e => setNested('conjoint', 'lieuNaissance', e.target.value)} 
                        placeholder="Ex: Antananarivo, Toamasina..." 
                        options={villesStandards}
                    />
                    <Input label="Société / Employeur" value={formData.conjoint.societe} onChange={e => setNested('conjoint', 'societe', e.target.value)} placeholder="Société du conjoint" />
                    <AutocompleteInput 
                        label="Fonction / Profession" 
                        value={formData.conjoint.fonction || ''} 
                        onChange={e => setNested('conjoint', 'fonction', e.target.value)} 
                        placeholder="Ex: Enseignant, Comptable..." 
                        options={PROFESSIONS_CONJOINT_SUGGESTIONS}
                    />
                </div>
            </Card>

            {/* Enfants */}
            <div className="space-y-3">
                <div className="flex items-center justify-between">
                    <div>
                        <div className="flex items-center gap-2">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                <Baby size={14} className="text-brand-500" />
                                Enfants ({formData.enfants.length})
                            </h3>
                            {enfantsEligiblesCount > 0 && (
                                <Badge variant="success" size="xs" dot>
                                    {enfantsEligiblesCount} déductible{enfantsEligiblesCount > 1 ? 's' : ''} (-{totalDeductionAr.toLocaleString('fr-FR')} Ar/mois • {fiscalConfig.codeVersion})
                                </Badge>
                            )}
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-subtle)]">
                            Règle fiscale IRSA ({fiscalConfig.codeVersion}) : déduction de {fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar/mois par enfant éligible (&lt; {fiscalConfig.ageLimiteDefaut} ans, ou &lt; {fiscalConfig.ageLimiteEtudiant} ans si étudiant certifié, ou sans limite si handicap).
                        </p>
                    </div>
                    <Button type="button" onClick={addEnfant} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                        Ajouter
                    </Button>
                </div>

                {formData.enfants.length === 0 ? (
                    <EmptyState
                        icon={Baby}
                        title="Aucun enfant enregistré"
                        compact
                        actionLabel="Ajouter un enfant"
                        onAction={addEnfant}
                        actionIcon={<Plus size={14} />}
                    />
                ) : (
                    <div className="space-y-4">
                        {formData.enfants.map((enf, idx) => {
                            const evalIrsa = evaluateIrsa(enf);
                            return (
                                <Card key={idx} padding className="space-y-4 relative">
                                    <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2 pr-8">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-bold text-[var(--color-fg)]">Enfant #{idx + 1}</span>
                                            {evalIrsa.age !== null && (
                                                <Badge variant="default" size="xs">
                                                    {evalIrsa.age} ans
                                                </Badge>
                                            )}
                                        </div>
                                        <Button
                                            type="button"
                                            onClick={() => removeEnfant(idx)}
                                            variant="ghost"
                                            size="xs"
                                            className="absolute top-3 right-3 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 !p-1 !min-h-0 !h-7 !w-7"
                                            aria-label="Supprimer l'enfant"
                                            icon={<Trash2 size={14} />}
                                        />
                                    </div>

                                    {/* Données d'état civil */}
                                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-6 gap-3">
                                        <div className="md:col-span-2">
                                            <Input label="Nom" value={enf.nom} onChange={e => setEnfant(idx, 'nom', e.target.value)} placeholder="Nom" />
                                        </div>
                                        <div className="md:col-span-2">
                                            <Input label="Prénom" value={enf.prenom} onChange={e => setEnfant(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                        </div>
                                        <div className="md:col-span-2">
                                            <Select label="Sexe" value={enf.sexe} onChange={e => setEnfant(idx, 'sexe', e.target.value)}>
                                                {GENRES_ENFANT.map(g => (
                                                    <option key={g.value} value={g.value}>{g.label}</option>
                                                ))}
                                            </Select>
                                        </div>
                                        <div className="md:col-span-3">
                                            <Input label="Date Naissance" type="date" max={getTodayDateString()} value={enf.dateNaissance} onChange={e => setEnfant(idx, 'dateNaissance', e.target.value)} />
                                        </div>
                                        <div className="md:col-span-3">
                                            <AutocompleteInput 
                                                label="Lieu de Naissance" 
                                                value={enf.lieuNaissance || ''} 
                                                onChange={e => setEnfant(idx, 'lieuNaissance', e.target.value)} 
                                                placeholder="Ex: Antananarivo, Toamasina..." 
                                                options={villesStandards}
                                            />
                                        </div>
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
                                                label={`Étudiant majeur (18 - ${fiscalConfig.ageLimiteEtudiant} ans)`}
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

                                        {/* Indicateur visuel d'éligibilité fiscale IRSA via le composant Alert standard */}
                                        <Alert
                                            type={evalIrsa.eligible ? 'success' : 'warning'}
                                            title={evalIrsa.eligible ? `Déduction IRSA active (-${fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar/mois • ${fiscalConfig.codeVersion})` : 'Déduction fiscale non applicable'}
                                            message={evalIrsa.motif}
                                        />
                                    </div>
                                </Card>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}
