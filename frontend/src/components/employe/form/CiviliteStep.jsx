import Input, { Select, AutocompleteInput } from '@/components/ui/forms/Input';

export default function CiviliteStep({
    formData,
    set,
    villesStandards,
    nationalitesStandards,
}) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <Input 
                label="Matricule" 
                required 
                value={formData.matricule} 
                onChange={e => set('matricule', e.target.value)} 
                placeholder="Ex: 101, 1002 ou EMP-001" 
                hint="Saisie libre (code unique du dossier personnel)"
            />
            <Select label="Sexe" required value={formData.sexe} onChange={e => set('sexe', e.target.value)}>
                <option value="M">Masculin (M)</option>
                <option value="F">Féminin (F)</option>
            </Select>
            <Input label="Nom" required value={formData.nom} onChange={e => set('nom', e.target.value)} placeholder="Nom de famille" />
            <Input label="Prénom(s)" required value={formData.prenom} onChange={e => set('prenom', e.target.value)} placeholder="Prénoms" />
            <Input label="Date de Naissance" required type="date" value={formData.dateNaissance} onChange={e => set('dateNaissance', e.target.value)} />
            
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
                <option value="Celibataire">Célibataire</option>
                <option value="Marie">Marié(e)</option>
                <option value="Divorce">Divorcé(e)</option>
                <option value="Veuf">Veuf / Veuve</option>
            </Select>
            <Input label="Nom & Prénom du Père" value={formData.pereNomPrenom} onChange={e => set('pereNomPrenom', e.target.value)} placeholder="Nom du père" />
            <Input label="Nom & Prénom de la Mère" value={formData.mereNomPrenom} onChange={e => set('mereNomPrenom', e.target.value)} placeholder="Nom de la mère" />
        </div>
    );
}
