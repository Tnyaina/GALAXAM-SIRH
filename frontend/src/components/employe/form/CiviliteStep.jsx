import Input, { Select } from '@/components/ui/forms/Input';

export default function CiviliteStep({
    formData,
    set,
    villesStandards,
    nationalitesStandards,
    isCustomNat,
    customNationalite,
    setCustomNationalite,
    handleNationaliteChange,
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
            
            {/* Lieu de Naissance avec liste des villes */}
            <Select label="Lieu de Naissance" required value={formData.lieuNaissance} onChange={e => set('lieuNaissance', e.target.value)}>
                {villesStandards.map(v => <option key={v} value={v}>{v}</option>)}
            </Select>

            {/* Nationalité avec Select standardisé */}
            <div>
                <Select 
                    label="Nationalité" 
                    value={isCustomNat ? 'Autre' : formData.nationalite} 
                    onChange={e => handleNationaliteChange(e.target.value)}
                >
                    {nationalitesStandards.map(n => <option key={n.value} value={n.value}>{n.label}</option>)}
                </Select>
                {isCustomNat && (
                    <div className="mt-2">
                        <Input 
                            label="Préciser la nationalité" 
                            value={customNationalite} 
                            onChange={e => {
                                setCustomNationalite(e.target.value);
                                set('nationalite', e.target.value);
                            }}
                            placeholder="Ex: Canadienne" 
                        />
                    </div>
                )}
            </div>

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
