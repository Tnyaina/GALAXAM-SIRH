import { MapPin, Clock, Sun, Sunset } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input, { Select, Checkbox } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';

export default function SiteFormModal({
    isOpen,
    onClose,
    onSubmit,
    selectedSite,
    formData,
    setFormData,
    filiales = [],
    actionError,
    submitting = false,
}) {
    const isContinu = formData.horaireContinu !== false;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={selectedSite ? "Modifier le Site d'Exploitation" : "Nouveau Site d'Exploitation"}
            icon={<MapPin size={18} />}
            size="lg"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="site-form" variant="primary" size="sm" loading={submitting}>
                        {selectedSite ? "Enregistrer les modifications" : "Créer le site"}
                    </Button>
                </>
            }
        >
            <form id="site-form" onSubmit={onSubmit} className="space-y-4">
                {actionError && <Alert type="error" message={actionError} />}

                {/* Section 1 : Informations Générales */}
                <div className="space-y-3">
                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                        Rattachement & Identification
                    </h4>
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                        <div className="sm:col-span-1">
                            <Input
                                label="Code Site"
                                required
                                disabled={!!selectedSite}
                                value={formData.codeSite}
                                onChange={(e) => setFormData({ ...formData, codeSite: e.target.value.toUpperCase() })}
                                placeholder="Ex: TNR, TMM"
                            />
                        </div>
                        <div className="sm:col-span-2">
                            <Input
                                label="Nom / Libellé du Site"
                                required
                                value={formData.libelle}
                                onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                placeholder="Ex: Antananarivo - Siège & Dépôt"
                            />
                        </div>
                    </div>

                    <Select
                        label="Filiale de Rattachement Juridique"
                        value={formData.idFilialeRattachement}
                        onChange={(e) => setFormData({ ...formData, idFilialeRattachement: e.target.value })}
                    >
                        <option value="">-- Sélectionner une filiale --</option>
                        {filiales
                            .filter(f => f.estActif !== false && f.actif !== false || String(f.idFiliale) === String(formData.idFilialeRattachement))
                            .map((f) => (
                                <option key={f.idFiliale} value={f.idFiliale}>
                                    {f.raisonSociale} ({f.codeFiliale})
                                </option>
                            ))}
                    </Select>
                </div>

                {/* Section 2 : Régime & Plages Horaires */}
                <div className="space-y-3 pt-3 border-t border-[var(--color-border-subtle)]">
                    <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <Clock size={14} className="text-[var(--color-brand-500)]" />
                            Régime de Travail & Horaires de Référence
                        </h4>
                    </div>

                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none">
                        <Checkbox
                            label="Régime en Horaire Continu (pause repas prise sur site)"
                            checked={isContinu}
                            onChange={(e) => {
                                setFormData({
                                    ...formData,
                                    horaireContinu: e.target.checked
                                });
                            }}
                        />
                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 ml-6">
                            {isContinu 
                                ? "En horaire continu, les collaborateurs effectuent leur journée avec une pause repas courte sur site (ex: 08h00 - 12h30 & 13h00 - 16h30)." 
                                : "En horaire discontinu, la journée est scindée avec une coupure méridienne étendue (ex: 08h00 - 12h00 & 14h00 - 18h00)."}
                        </p>
                    </div>

                    {/* Affichage systématique des 4 bornes de référence */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                            <div className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-1.5">
                                <Sun size={14} className="text-[var(--color-warning-500)] shrink-0" />
                                <span>Plage 1 : Matinée</span>
                            </div>
                            <div className="space-y-2">
                                <Input
                                    label="Début Matin (Prise de poste)"
                                    type="time"
                                    required
                                    value={formData.heureDebutMatin || '08:00'}
                                    onChange={(e) => setFormData({ ...formData, heureDebutMatin: e.target.value })}
                                />
                                <Input
                                    label="Fin Matin (Départ pause)"
                                    type="time"
                                    required
                                    value={formData.heureFinMatin || '12:00'}
                                    onChange={(e) => setFormData({ ...formData, heureFinMatin: e.target.value })}
                                />
                            </div>
                        </div>

                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                            <div className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-1.5">
                                <Sunset size={14} className="text-orange-500 shrink-0" />
                                <span>Plage 2 : Après-midi</span>
                            </div>
                            <div className="space-y-2">
                                <Input
                                    label="Reprise Après-midi (Fin pause)"
                                    type="time"
                                    required
                                    value={formData.heureDebutApresMidi || '13:00'}
                                    onChange={(e) => setFormData({ ...formData, heureDebutApresMidi: e.target.value })}
                                />
                                <Input
                                    label="Fin Après-midi (Fin de journée)"
                                    type="time"
                                    required
                                    value={formData.heureFinApresMidi || '17:00'}
                                    onChange={(e) => setFormData({ ...formData, heureFinApresMidi: e.target.value })}
                                />
                            </div>
                        </div>
                    </div>
                </div>

                {/* Section 3 : Plancher d'arrivée valorisé (Pointage) */}
                <div className="space-y-3 pt-3 border-t border-[var(--color-border-subtle)]">
                    <div className="flex items-center justify-between">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <Clock size={14} className="text-[var(--color-brand-500)]" />
                            Plancher d'Arrivée Valorisé (Pointage)
                        </h4>
                    </div>

                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-3">
                        <Checkbox
                            label="Activer le plancher d'arrivée matinal"
                            checked={formData.plancherArriveeActif || false}
                            onChange={(e) => {
                                setFormData({
                                    ...formData,
                                    plancherArriveeActif: e.target.checked,
                                    heureEntreeMinimaleValorisee: formData.heureEntreeMinimaleValorisee || '07:30'
                                });
                            }}
                        />
                        <p className="text-[11px] text-[var(--color-fg-muted)] ml-6">
                            Si un collaborateur badge très tôt le matin, ses heures de présence ne commencent à être comptabilisées
                            qu'à partir de cette heure minimale. Empêche la génération d'heures indues avant la prise de poste.
                        </p>

                        {formData.plancherArriveeActif && (
                            <div className="ml-6 pt-2 border-t border-[var(--color-border-subtle)] max-w-xs">
                                <Input
                                    label="Heure minimale comptabilisée"
                                    type="time"
                                    required={formData.plancherArriveeActif}
                                    value={formData.heureEntreeMinimaleValorisee ? formData.heureEntreeMinimaleValorisee.substring(0, 5) : '07:30'}
                                    onChange={(e) => setFormData({ ...formData, heureEntreeMinimaleValorisee: e.target.value })}
                                    helperText="Ex: 07:30 pour une prise de poste officielle à 08:00."
                                />
                            </div>
                        )}
                    </div>
                </div>
            </form>
        </Modal>
    );
}