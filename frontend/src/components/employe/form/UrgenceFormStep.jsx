import { Plus, Trash2 } from 'lucide-react';
import Input, { Select } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';

export default function UrgenceFormStep({
    formData,
    addContact,
    removeContact,
    setContact,
}) {
    return (
        <div>
            <div className="flex items-center justify-between mb-3">
                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400">Contacts d'urgence</h3>
                <Button type="button" onClick={addContact} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                    Ajouter
                </Button>
            </div>
            <div className="space-y-3">
                {formData.contactsUrgence.map((ct, idx) => (
                    <div key={idx} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 relative">
                        {formData.contactsUrgence.length > 1 && (
                            <button type="button" onClick={() => removeContact(idx)} className="absolute top-3 right-3 text-danger-500 hover:bg-danger-500/10 p-1 rounded-none  cursor-pointer">
                                <Trash2 size={14} />
                            </button>
                        )}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                            <Input label="Nom" value={ct.nom} onChange={e => setContact(idx, 'nom', e.target.value)} placeholder="Nom" />
                            <Input label="Prénom" value={ct.prenom} onChange={e => setContact(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                            <Select label="Lien de Parenté" value={ct.lienParente} onChange={e => setContact(idx, 'lienParente', e.target.value)}>
                                <option value="Conjoint">Conjoint</option>
                                <option value="Pere">Père</option>
                                <option value="Mere">Mère</option>
                                <option value="Frere">Frère</option>
                                <option value="Soeur">Sœur</option>
                                <option value="Enfant">Enfant</option>
                                <option value="Autre">Autre</option>
                            </Select>
                            <Input label="Téléphone" value={ct.telephone} onChange={e => setContact(idx, 'telephone', e.target.value)} placeholder="Téléphone" />
                            <div className="md:col-span-2">
                                <Input label="Adresse" value={ct.adresse} onChange={e => setContact(idx, 'adresse', e.target.value)} placeholder="Adresse" />
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
