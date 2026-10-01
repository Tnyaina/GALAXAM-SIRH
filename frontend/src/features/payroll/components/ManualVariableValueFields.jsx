import Input from '@/components/ui/forms/Input';
import { manualMethod } from '../utils/manualVariableCalculation';

export default function ManualVariableValueFields({ type, form, onChange }) {
    const method = manualMethod(type);
    if (method === 'MONTANT') return null;
    return <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {method === 'QUANTITE_TAUX' && <Input label="Quantité" required inputMode="decimal" value={form.quantiteSaisie || ''} onChange={e => onChange('quantiteSaisie', e.target.value)} placeholder="Ex. 1,5" />}
        <Input label={method === 'QUANTITE_TAUX' ? 'Taux unitaire en Ar' : 'Pourcentage du salaire de base'} required inputMode="decimal" value={form.tauxSaisi || ''} onChange={e => onChange('tauxSaisi', e.target.value)} placeholder={method === 'QUANTITE_TAUX' ? 'Ex. 2 500,50' : 'Ex. 10'} />
        <p className="sm:col-span-2 text-xs text-[var(--color-fg-muted)]">Le serveur calcule le montant selon la version applicable et son arrondi. {method === 'POURCENTAGE_SALAIRE_BASE' ? 'La base est le salaire contractuel de la période, avant variables.' : 'Quantité et taux : six décimales maximum.'}</p>
    </div>;
}
