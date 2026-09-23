import { CreditCard, Award } from 'lucide-react';
import Input, { AutocompleteInput } from '@/components/ui/forms/Input';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';

export default function CinPermisStep({ formData, set, villesStandards }) {
    return (
        <div className="space-y-4">
            <Card padding className="space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <CreditCard size={14} className="text-brand-500" />
                        Carte d'Identité Nationale (CIN)
                    </h3>
                    <Badge variant="primary" size="xs">Obligatoire</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <Input label="N° CIN" required value={formData.cinNumero} onChange={e => set('cinNumero', e.target.value)} placeholder="Ex: 101 234 567 890" />
                    <Input label="Date de Délivrance" required type="date" value={formData.cinDateDelivrance} onChange={e => set('cinDateDelivrance', e.target.value)} />
                    <AutocompleteInput 
                        label="Lieu de Délivrance" 
                        required 
                        value={formData.cinLieuDelivrance} 
                        onChange={e => set('cinLieuDelivrance', e.target.value)} 
                        placeholder="Ex: Antananarivo, Toamasina..."
                        options={villesStandards}
                        listId="cin-villes-list"
                    />
                    <Input label="Duplicata – Date" type="date" value={formData.cinDuplicataDate} onChange={e => set('cinDuplicataDate', e.target.value)} />
                    <div className="md:col-span-2">
                        <AutocompleteInput 
                            label="Duplicata – Lieu" 
                            value={formData.cinDuplicataLieu} 
                            onChange={e => set('cinDuplicataLieu', e.target.value)} 
                            placeholder="Lieu de duplicata (Ex: Antananarivo)" 
                            options={villesStandards}
                            listId="cin-duplicata-villes-list"
                        />
                    </div>
                </div>
            </Card>

            <Card padding className="space-y-4">
                <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Award size={14} className="text-brand-500" />
                        Permis de Conduire
                    </h3>
                    <Badge variant="default" size="xs">Optionnel</Badge>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                    <Input label="N° Permis" value={formData.permisConduireNumero} onChange={e => set('permisConduireNumero', e.target.value)} placeholder="N° de permis" />
                    <Input label="Date Délivrance" type="date" value={formData.permisConduireDateDelivrance} onChange={e => set('permisConduireDateDelivrance', e.target.value)} />
                    <AutocompleteInput 
                        label="Lieu Délivrance" 
                        value={formData.permisConduireLieuDelivrance} 
                        onChange={e => set('permisConduireLieuDelivrance', e.target.value)} 
                        placeholder="Ex: Antananarivo" 
                        options={villesStandards}
                        listId="permis-villes-list"
                    />
                </div>
            </Card>
        </div>
    );
}
