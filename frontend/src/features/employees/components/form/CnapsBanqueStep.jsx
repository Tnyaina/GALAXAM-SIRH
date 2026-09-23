import { Shield, Landmark } from 'lucide-react';
import Input, { AutocompleteInput } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { AGENCES_BANCAIRES } from '@/constants';

export default function CnapsBanqueStep({
    formData,
    set,
    setNested,
    banques = [],
}) {
    return (
        <div className="space-y-4">
            <Card padding className="space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Shield size={14} className="text-brand-500" />
                        Sécurité Sociale (CNaPS)
                    </h3>
                    <Badge variant="default" size="xs">Optionnel</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="N° Affiliation CNaPS" value={formData.numAffiliationCnaps} onChange={e => set('numAffiliationCnaps', e.target.value)} placeholder="N° CNaPS" />
                    <Input label="Date Déclaration" type="date" value={formData.dateDeclaration} onChange={e => set('dateDeclaration', e.target.value)} />
                </div>
            </Card>

            <Card padding className="space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Landmark size={14} className="text-brand-500" />
                        Compte Bancaire Principal
                    </h3>
                    <Badge variant="default" size="xs">Optionnel</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <SearchSelect
                        label="Banque"
                        value={formData.compteBanquePrincipal.idBanque}
                        onChange={(val) => setNested('compteBanquePrincipal', 'idBanque', val)}
                        options={banques
                            .filter(b => b.estActif !== false && b.actif !== false || String(b.idBanque) === String(formData.compteBanquePrincipal.idBanque))
                            .map((b) => ({
                                value: b.idBanque,
                                label: b.nomBanque,
                                sublabel: b.codeBanque,
                            }))}
                        placeholder="Rechercher une banque…"
                    />
                    <AutocompleteInput 
                        label="Agence" 
                        value={formData.compteBanquePrincipal.agence} 
                        onChange={e => setNested('compteBanquePrincipal', 'agence', e.target.value)} 
                        placeholder="Ex: Ankorondrano, Analakely, Antsirabe..." 
                        options={AGENCES_BANCAIRES}
                        listId="agences-bancaires-list"
                    />
                    <Input label="N° Compte / RIB" value={formData.compteBanquePrincipal.numCompteRib} onChange={e => setNested('compteBanquePrincipal', 'numCompteRib', e.target.value)} placeholder="00000-00000-00000000000-00" />
                    <Input label="Titulaire du Compte" value={formData.compteBanquePrincipal.titulaireCompte} onChange={e => setNested('compteBanquePrincipal', 'titulaireCompte', e.target.value)} placeholder="Nom du titulaire" />
                </div>
            </Card>
        </div>
    );
}
