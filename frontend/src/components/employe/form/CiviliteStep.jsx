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
            <div className="flex gap-2 items-end">
                <Input 
                    label="Matricule" 
                    required 
                    value={formData.matricule} 
                    onChange={e => set('matricule', e.target.value)} 
                    onBlur={onBlurMatricule}
                    error={!!matriculeError}
                    placeholder="Ex: 0001" 
                    hint={matriculeError || "Saisie libre ou générée"}
                    containerClassName="flex-1"
                />
                <Button 
                    type="button" 
                    variant="outline" 
                    onClick={onGenerateMatricule}
                    loading={generatingMatricule}
                    className="mb-[22px] px-3 shrink-0"
                    title="Générer automatiquement"
                >
                    <Wand2 size={16} />
                </Button>
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
                hint={`Âge légal minimum : ${REGLES_FISCALES_IRSA.AGE_MIN_LEGAL_EMPLOYE} ans`}
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
            <Input label="Nom & Prénom du Père" value={formData.pereNomPrenom} onChange={e => set('pereNomPrenom', e.target.value)} placeholder="Nom du père" />
            <Input label="Nom & Prénom de la Mère" value={formData.mereNomPrenom} onChange={e => set('mereNomPrenom', e.target.value)} placeholder="Nom de la mère" />
        </div>
    );
}
