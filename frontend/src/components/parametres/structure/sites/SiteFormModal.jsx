import { MapPin, X, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Select, Checkbox } from '@/components/ui/Input';

export default function SiteFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedSite,
    formData,
    setFormData,
    filiales = [],
    actionError,
}) {
    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                    <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        {selectedSite ? "Modifier le Site" : "Nouveau Site"}
                    </h3>
                    <button onClick={onClose} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={onSubmit} className="p-6 space-y-4 max-h-[500px] overflow-y-auto">
                    {actionError && (
                        <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                            <ShieldAlert size={14} />
                            {actionError}
                        </div>
                    )}

                    <Input
                        label="Code Site"
                        required
                        disabled={!!selectedSite}
                        value={formData.codeSite}
                        onChange={(e) => setFormData({ ...formData, codeSite: e.target.value.toUpperCase() })}
                        placeholder="Ex: TNR, TMM"
                    />

                    <Input
                        label="Nom / Libellé"
                        required
                        value={formData.libelle}
                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                        placeholder="Ex: Antananarivo - Siège"
                    />

                    <Select
                        label="Filiale de Rattachement"
                        value={formData.idFilialeRattachement}
                        onChange={(e) => setFormData({ ...formData, idFilialeRattachement: e.target.value })}
                    >
                        <option value="">-- Sélectionner une filiale --</option>
                        {filiales.map((f) => (
                            <option key={f.idFiliale} value={f.idFiliale}>
                                {f.raisonSociale} ({f.codeFiliale})
                            </option>
                        ))}
                    </Select>

                    <div className="pt-1">
                        <Checkbox
                            label="Régime Horaire Continu"
                            checked={formData.horaireContinu}
                            onChange={(e) => setFormData({ ...formData, horaireContinu: e.target.checked })}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4 pt-3 border-t border-[var(--color-border-subtle)]">
                        <Input
                            label="Début Matin"
                            type="time"
                            value={formData.heureDebutMatin}
                            onChange={(e) => setFormData({ ...formData, heureDebutMatin: e.target.value })}
                        />
                        <Input
                            label="Fin Matin"
                            type="time"
                            value={formData.heureFinMatin}
                            onChange={(e) => setFormData({ ...formData, heureFinMatin: e.target.value })}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-4">
                        <Input
                            label="Début Après-midi"
                            type="time"
                            value={formData.heureDebutApresMidi}
                            onChange={(e) => setFormData({ ...formData, heureDebutApresMidi: e.target.value })}
                        />
                        <Input
                            label="Fin Après-midi"
                            type="time"
                            value={formData.heureFinApresMidi}
                            onChange={(e) => setFormData({ ...formData, heureFinApresMidi: e.target.value })}
                        />
                    </div>

                    <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm">
                            Enregistrer
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}