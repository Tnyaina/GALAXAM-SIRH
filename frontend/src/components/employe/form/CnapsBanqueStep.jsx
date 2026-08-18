import Input, { Select } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';

export default function CnapsBanqueStep({
    formData,
    set,
    setNested,
    banques = [],
}) {
    return (
        <div className="space-y-6">
            <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Sécurité Sociale</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="N° Affiliation CNaPS" value={formData.numAffiliationCnaps} onChange={e => set('numAffiliationCnaps', e.target.value)} placeholder="N° CNaPS" />
                    <Input label="Date Déclaration" type="date" value={formData.dateDeclaration} onChange={e => set('dateDeclaration', e.target.value)} />
                </div>
            </div>
            <div className="border-t border-ink-150 dark:border-ink-700 pt-4">
                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Compte Bancaire Principal</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <SearchSelect
                        label="Banque"
                        value={formData.compteBanquePrincipal.idBanque}
                        onChange={(val) => setNested('compteBanquePrincipal', 'idBanque', val)}
                        options={banques.map((b) => ({
                            value: b.idBanque,
                            label: b.nomBanque,
                            sublabel: b.codeBanque,
                        }))}
                        placeholder="Rechercher une banque…"
                    />
                    <Input label="Agence" value={formData.compteBanquePrincipal.agence} onChange={e => setNested('compteBanquePrincipal', 'agence', e.target.value)} placeholder="Agence" />
                    <Input label="N° Compte / RIB" value={formData.compteBanquePrincipal.numCompteRib} onChange={e => setNested('compteBanquePrincipal', 'numCompteRib', e.target.value)} placeholder="00000-00000-00000000000-00" />
                    <Input label="Titulaire du Compte" value={formData.compteBanquePrincipal.titulaireCompte} onChange={e => setNested('compteBanquePrincipal', 'titulaireCompte', e.target.value)} placeholder="Nom du titulaire" />
                </div>
            </div>
        </div>
    );
}
