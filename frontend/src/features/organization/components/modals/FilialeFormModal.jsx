import { Building2, MapPin, Hash, Phone, Mail, FileText } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';

export default function FilialeFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedFiliale,
    formData,
    setFormData,
    actionError,
    fieldErrors = {},
    setFieldErrors,
    submitting = false
}) {
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={selectedFiliale ? 'Modifier la filiale' : 'Nouvelle filiale'}
            icon={<Building2 size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="filiale-form" variant="primary" size="sm" loading={submitting}>
                        {selectedFiliale ? 'Enregistrer' : 'Créer la filiale'}
                    </Button>
                </>
            }
        >
            <form id="filiale-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <FormError message={actionError} />}

                {selectedFiliale && (
                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between text-xs">
                        <div className="flex items-center gap-2">
                            <Building2 size={15} className="text-[var(--color-brand-500)]" />
                            <span className="font-bold text-[var(--color-fg)]">{selectedFiliale.raisonSociale}</span>
                            <span className="px-1.5 py-0.5 text-[10px] font-mono font-bold bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                {selectedFiliale.codeFiliale}
                            </span>
                        </div>
                        <span className={`text-[11px] font-semibold ${selectedFiliale.estActif ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                            {selectedFiliale.estActif ? '● Filiale active' : '○ Inactive'}
                        </span>
                    </div>
                )}

                {/* Section 1 : Identification */}
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <Input
                        label="Code"
                        required
                        disabled={!!selectedFiliale}
                        value={formData.codeFiliale}
                        onChange={(event) => {
                            setFormData({ ...formData, codeFiliale: event.target.value.toUpperCase() });
                            if (fieldErrors.codeFiliale && setFieldErrors) {
                                setFieldErrors(prev => ({ ...prev, codeFiliale: undefined }));
                            }
                        }}
                        error={fieldErrors.codeFiliale}
                        placeholder="Ex: GLX"
                        icon={<Building2 size={14} />}
                        hint={!selectedFiliale ? "Code unique (2-5 lettres)" : undefined}
                    />
                    <div className="sm:col-span-2">
                        <Input
                            label="Raison sociale"
                            required
                            value={formData.raisonSociale}
                            onChange={(event) => {
                                setFormData({ ...formData, raisonSociale: event.target.value });
                                if (fieldErrors.raisonSociale && setFieldErrors) {
                                setFieldErrors(prev => ({ ...prev, raisonSociale: undefined }));
                            }
                            }}
                            error={fieldErrors.raisonSociale}
                            placeholder="Ex: Galaxam Logistics SARL"
                            icon={<Building2 size={14} />}
                        />
                    </div>
                </div>

                {/* Section 2 : Identité & Siège */}
                <div className="border-t border-[var(--color-border-subtle)] pt-3 space-y-3">
                    <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        <MapPin size={13} className="text-[var(--color-brand-500)]" />
                        <span>Identité et siège</span>
                    </div>
                    <Input
                        label="Adresse officielle du siège"
                        value={formData.adresseSiege}
                        onChange={(event) => setFormData({ ...formData, adresseSiege: event.target.value })}
                        placeholder="Ex: 3e étage, Tour Forello, Tanjombato, Antananarivo"
                        icon={<MapPin size={14} />}
                    />
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <Input
                            label="NIF"
                            value={formData.nif}
                            onChange={(event) => {
                                setFormData({ ...formData, nif: event.target.value });
                                if (fieldErrors.nif && setFieldErrors) {
                                    setFieldErrors(prev => ({ ...prev, nif: undefined }));
                                }
                            }}
                            error={fieldErrors.nif}
                            placeholder="Ex: 3000019434"
                            icon={<Hash size={14} />}
                            hint="10 chiffres"
                        />
                        <Input
                            label="STAT"
                            value={formData.stat}
                            onChange={(event) => setFormData({ ...formData, stat: event.target.value })}
                            placeholder="Ex: 49295 11 2018 0 10245"
                            icon={<FileText size={14} />}
                        />
                        <Input
                            label="RCS"
                            value={formData.rcs}
                            onChange={(event) => setFormData({ ...formData, rcs: event.target.value })}
                            placeholder="Ex: 2020 B 00123"
                            icon={<FileText size={14} />}
                        />
                    </div>
                </div>

                {/* Section 3 : Coordonnées */}
                <div className="border-t border-[var(--color-border-subtle)] pt-3 space-y-3">
                    <div className="flex items-center gap-1.5 text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        <Phone size={13} className="text-[var(--color-brand-500)]" />
                        <span>Coordonnées</span>
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Input
                            label="Téléphone"
                            type="tel"
                            value={formData.telephone}
                            onChange={(event) => setFormData({ ...formData, telephone: event.target.value })}
                            placeholder="Ex: +261 20 22 123 45"
                            icon={<Phone size={14} />}
                        />
                        <Input
                            label="Email"
                            type="email"
                            value={formData.email}
                            onChange={(event) => {
                                setFormData({ ...formData, email: event.target.value });
                                if (fieldErrors.email && setFieldErrors) {
                                    setFieldErrors(prev => ({ ...prev, email: undefined }));
                                }
                            }}
                            error={fieldErrors.email}
                            placeholder="Ex: contact@galaxam.mg"
                            icon={<Mail size={14} />}
                        />
                    </div>
                </div>
            </form>
        </Modal>
    );
}
