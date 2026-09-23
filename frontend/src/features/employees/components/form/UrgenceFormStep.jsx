import { Plus, Trash2, ShieldAlert } from 'lucide-react';
import Input, { Select } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { LIENS_PARENTE } from '@/constants/employeConstants';

export default function UrgenceFormStep({
    formData,
    addContact,
    removeContact,
    setContact,
}) {
    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between">
                <div>
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <ShieldAlert size={14} className="text-brand-500" />
                        Contacts d'urgence ({formData.contactsUrgence.length})
                    </h3>
                    <p className="text-[11px] text-[var(--color-fg-subtle)]">
                        Personnes à contacter en priorité en cas d'accident ou d'urgence.
                    </p>
                </div>
                <Button type="button" onClick={addContact} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                    Ajouter
                </Button>
            </div>

            {formData.contactsUrgence.length === 0 ? (
                <EmptyState
                    icon={ShieldAlert}
                    title="Aucun contact d'urgence"
                    description="Ajoutez les coordonnées des proches à joindre en cas d'incident."
                    compact
                    actionLabel="Ajouter un contact"
                    onAction={addContact}
                    actionIcon={<Plus size={14} />}
                />
            ) : (
                <div className="space-y-3">
                    {formData.contactsUrgence.map((ct, idx) => (
                        <Card key={idx} padding className="space-y-3 relative">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2 pr-8">
                                <span className="text-xs font-bold text-[var(--color-fg)]">Contact #{idx + 1}</span>
                                {formData.contactsUrgence.length > 1 && (
                                    <Button
                                        type="button"
                                        onClick={() => removeContact(idx)}
                                        variant="ghost"
                                        size="xs"
                                        className="absolute top-3 right-3 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 !p-1 !min-h-0 !h-7 !w-7"
                                        aria-label="Supprimer le contact"
                                        icon={<Trash2 size={14} />}
                                    />
                                )}
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                <Input label="Nom" value={ct.nom} onChange={e => setContact(idx, 'nom', e.target.value)} placeholder="Nom" />
                                <Input label="Prénom" value={ct.prenom} onChange={e => setContact(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                <Select 
                                    label="Lien de Parenté" 
                                    value={ct.lienParente || 'Autre'} 
                                    onChange={e => setContact(idx, 'lienParente', e.target.value)}
                                >
                                    {LIENS_PARENTE.map(l => (
                                        <option key={l.value} value={l.value}>{l.label}</option>
                                    ))}
                                </Select>
                                <Input label="Téléphone" value={ct.telephone} onChange={e => setContact(idx, 'telephone', e.target.value)} placeholder="Téléphone" />
                                <div className="md:col-span-2">
                                    <Input label="Adresse" value={ct.adresse} onChange={e => setContact(idx, 'adresse', e.target.value)} placeholder="Adresse" />
                                </div>
                            </div>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}
