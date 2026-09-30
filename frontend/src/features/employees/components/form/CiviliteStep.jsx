import Input, { Select, AutocompleteInput } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Wand2 } from 'lucide-react';
import { fmtDateInput } from '@/utils/dateUtils';
import { GENRES_SEXE, SITUATIONS_FAMILIALES, REGLES_FISCALES_IRSA } from '@/constants/employeConstants';

export default function CiviliteStep({
    formData,
    set,
    villesStandards,
    nationalitesStandards,
    _isCustomNat,
    _customNationalite,
    _setCustomNationalite,
    _handleNationaliteChange,
    onGenerateMatricule,
    onBlurMatricule,
    matriculeError,
    generatingMatricule
}) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex flex-col gap-1">
                <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Matricule <span className="text-[var(--color-danger-500)] ml-1">*</span>
                </label>
                <div className="flex gap-2">
                    <Input 
                        value={formData.matricule} 
                        onChange={e => set('matricule', e.target.value)} 
                        onBlur={onBlurMatricule}
                        error={Boolean(matriculeError)}
                        placeholder="Ex: 0001" 
                        containerClassName="flex-1"
                    />
                    <Button 
                        type="button" 
                        variant="outline" 
                        onClick={onGenerateMatricule}
                        loading={generatingMatricule}
                        className="px-3 shrink-0 self-stretch flex items-center justify-center border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)]"
                        title="Générer automatiquement"
                    >
                        <Wand2 size={15} />
                    </Button>
                </div>
                {matriculeError && (
                    <p className="text-[11px] text-[var(--color-danger-500)] mt-0.5">{matriculeError}</p>
                )}
            </div>
            <Select label="Sexe" required value={formData.sexe} onChange={e => set('sexe', e.target.value)}>
                {GENRES_SEXE.map(g => (
                    <option key={g.value} value={g.value}>{g.label}</option>
                ))}
            </Select>
            <Input label="Nom" required value={formData.nom} onChange={e => set('nom', e.target.value)} placeholder="Nom de famille" />
            <Input label="Prénom(s)" required value={formData.prenom} onChange={e => set('prenom', e.target.value)} placeholder="Prénoms" />
            <Input 
                label="Date de Naissance" 
                required 
                type="date" 
                max={fmtDateInput(new Date(new Date().setFullYear(new Date().getFullYear() - REGLES_FISCALES_IRSA.AGE_MIN_LEGAL_EMPLOYE)))}
                value={formData.dateNaissance} 
                onChange={e => set('dateNaissance', e.target.value)} 
                hint={`Âge minimum légal : ${REGLES_FISCALES_IRSA.AGE_MIN_LEGAL_EMPLOYE} ans`}
            />
            
            <AutocompleteInput 
                label="Lieu de Naissance" 
                required 
                value={formData.lieuNaissance} 
                onChange={e => set('lieuNaissance', e.target.value)} 
                placeholder="Ex: Antananarivo, Toamasina..."
                options={villesStandards}
                listId="villes-list"
            />

            <AutocompleteInput 
                label="Nationalité" 
                required 
                value={formData.nationalite} 
                onChange={e => set('nationalite', e.target.value)} 
                placeholder="Ex: Malgache, Française..."
                options={nationalitesStandards}
                listId="nationalites-list"
            />

            <Select label="Situation Familiale" value={formData.situationFamiliale} onChange={e => set('situationFamiliale', e.target.value)}>
                {SITUATIONS_FAMILIALES.map(s => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                ))}
            </Select>
            <Input label="Père" value={formData.pereNomPrenom} onChange={e => set('pereNomPrenom', e.target.value)} placeholder="Nom et prénom du père" />
            <Input label="Mère" value={formData.mereNomPrenom} onChange={e => set('mereNomPrenom', e.target.value)} placeholder="Nom et prénom de la mère" />
        </div>
    );
}
