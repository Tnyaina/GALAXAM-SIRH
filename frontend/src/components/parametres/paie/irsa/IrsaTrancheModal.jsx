import { useState } from 'react';
import { Percent, ShieldAlert, Layers } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDateInput } from '@/utils/paieUtils';

export default function IrsaTrancheModal({
    isOpen,
    onClose,
    onSubmit,
    editingIrsa,
    formData,
    setFormData,
    existingTranches = [],
    actionError,
    submitting = false,
}) {
    const [localError, setLocalError] = useState(null);

    const minVal = parseFloat(formData.trancheMin);
    const maxVal = formData.trancheMax !== '' && formData.trancheMax !== null && formData.trancheMax !== undefined ? parseFloat(formData.trancheMax) : null;
    const numTranche = parseInt(formData.numeroTranche, 10);

    // Overlap and continuity detection in real time against existing tranches
    const otherTranches = existingTranches.filter(t => !editingIrsa || t.idTranche !== editingIrsa.idTranche);
    
    // Duplicate number check
    const isNumDuplicate = otherTranches.some(t => parseInt(t.numeroTranche, 10) === numTranche);

    // Overlap check
    let overlapConflict = null;
    if (!isNaN(minVal) && minVal >= 0) {
        for (const ot of otherTranches) {
            const otMin = parseFloat(ot.trancheMin);
            const otMax = ot.trancheMax !== null && ot.trancheMax !== undefined && ot.trancheMax !== '' ? parseFloat(ot.trancheMax) : null;

            let overlaps = false;
            if (maxVal === null && otMax === null) {
                overlaps = true;
            } else if (maxVal === null) {
                if (minVal <= otMax) overlaps = true;
            } else if (otMax === null) {
                if (otMin <= maxVal) overlaps = true;
            } else {
                const startMax = Math.max(minVal, otMin);
                const endMin = Math.min(maxVal, otMax);
                if (startMax <= endMin) overlaps = true;
            }

            if (overlaps) {
                overlapConflict = ot;
                break;
            }
        }
    }

    const isIntervalInvalid = maxVal !== null && !isNaN(maxVal) && !isNaN(minVal) && maxVal <= minVal;

    const handleSubmit = (e) => {
        e.preventDefault();
        setLocalError(null);

        if (isNaN(minVal) || minVal < 0) {
            setLocalError("Le montant minimum ne peut pas être négatif.");
            return;
        }

        if (maxVal !== null) {
            if (isNaN(maxVal)) {
                setLocalError("Le montant maximum saisi est invalide.");
                return;
            }
            if (maxVal <= minVal) {
                setLocalError(`Le montant maximum (${maxVal.toLocaleString('fr-FR')} Ar) doit être strictement supérieur au montant minimum (${minVal.toLocaleString('fr-FR')} Ar).`);
                return;
            }
        }

        if (isNumDuplicate) {
            setLocalError(`Le numéro de tranche #${numTranche} est déjà utilisé par une autre tranche.`);
            return;
        }

        if (overlapConflict) {
            const otMaxStr = overlapConflict.trancheMax ? `${parseFloat(overlapConflict.trancheMax).toLocaleString('fr-FR')} Ar` : 'Illimité';
            setLocalError(`Chevauchement détecté avec la tranche #${overlapConflict.numeroTranche} [${parseFloat(overlapConflict.trancheMin).toLocaleString('fr-FR')} Ar - ${otMaxStr}]. Les tranches doivent être strictement disjointes et continues.`);
            return;
        }

        const tauxVal = parseFloat(formData.taux);
        if (isNaN(tauxVal) || tauxVal < 0 || tauxVal > 100) {
            setLocalError("Le taux IRSA doit être compris entre 0% et 100%.");
            return;
        }

        if (formData.dateFin && formData.dateDebut && formData.dateFin < formData.dateDebut) {
            setLocalError("La date de fin ne peut pas être antérieure à la date de début.");
            return;
        }

        onSubmit(e);
    };

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={editingIrsa ? `Modifier la Tranche IRSA #${formData.numeroTranche}` : "Nouvelle Tranche IRSA"}
            icon={<Percent size={18} />}
            size="md"
            footer={
                <>
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                        Annuler
                    </Button>
                    <Button type="submit" form="irsa-tranche-form" variant="primary" size="sm" loading={submitting} disabled={isIntervalInvalid || isNumDuplicate}>
                        {editingIrsa ? "Enregistrer" : "Créer la tranche"}
                    </Button>
                </>
            }
        >
            <form id="irsa-tranche-form" onSubmit={handleSubmit} className="space-y-4">
                {/* Visual guide of existing tranches */}
                {otherTranches.length > 0 && (
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1.5 rounded-none">
                        <div className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <Layers size={13} className="text-[var(--color-brand-500)]" />
                            Continuité du barème ({otherTranches.length} tranche{otherTranches.length > 1 ? 's' : ''})
                        </div>
                        <div className="flex flex-wrap gap-1.5 text-xs font-mono">
                            {otherTranches
                                .sort((a, b) => (a.numeroTranche || 0) - (b.numeroTranche || 0))
                                .map(t => (
                                    <span key={t.idTranche} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-2 py-0.5 text-[11px]">
                                        T{t.numeroTranche}: {parseFloat(t.trancheMin).toLocaleString('fr-FR')} → {t.trancheMax ? `${parseFloat(t.trancheMax).toLocaleString('fr-FR')}` : '∞'} ({t.taux}%)
                                    </span>
                                ))
                            }
                        </div>
                    </div>
                )}

                {(actionError || localError) && (
                    <Alert type="error" message={localError || actionError} />
                )}

                {overlapConflict && !localError && (
                    <div className="bg-warning-500/10 text-warning-700 dark:text-warning-300 border border-warning-500/20 rounded-none p-2.5 text-xs flex items-center gap-2">
                        <ShieldAlert size={15} className="shrink-0 text-warning-500" />
                        <span>Attention : chevauchement potentiel avec la tranche #{overlapConflict.numeroTranche}.</span>
                    </div>
                )}

                <Input
                    label="Numéro d'Ordre de la Tranche"
                    type="number"
                    min="1"
                    required
                    value={formData.numeroTranche}
                    onChange={(e) => setFormData({ ...formData, numeroTranche: parseInt(e.target.value) || 1 })}
                    error={isNumDuplicate ? "Numéro déjà existant" : undefined}
                    hint="Ordre croissant d'imposition (ex: 1, 2, 3...)"
                />

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Montant Min (Ar)"
                        type="number"
                        min="0"
                        step="1"
                        required
                        value={formData.trancheMin}
                        onChange={(e) => setFormData({ ...formData, trancheMin: parseFloat(e.target.value) || 0 })}
                    />
                    <Input
                        label="Montant Max (Ar)"
                        type="number"
                        min={!isNaN(minVal) ? minVal + 1 : 0}
                        step="1"
                        value={formData.trancheMax}
                        onChange={(e) => setFormData({ ...formData, trancheMax: e.target.value })}
                        placeholder="Vide si illimité"
                        error={isIntervalInvalid ? "Doit être > Min" : undefined}
                        hint={maxVal === null ? "Dernière tranche sans plafond (∞)" : undefined}
                    />
                </div>

                <Input
                    label="Taux IRSA Applicable (%)"
                    type="number"
                    min="0"
                    max="100"
                    step="0.01"
                    required
                    value={formData.taux}
                    onChange={(e) => setFormData({ ...formData, taux: parseFloat(e.target.value) || 0 })}
                    hint="Ex: 0, 5, 10, 15, 20%"
                />

                <div className="grid grid-cols-2 gap-4">
                    <Input
                        label="Date Début"
                        type="date"
                        required
                        value={fmtDateInput(formData.dateDebut)}
                        onChange={(e) => setFormData({ ...formData, dateDebut: e.target.value })}
                    />
                    <Input
                        label="Date Fin (Optionnel)"
                        type="date"
                        min={fmtDateInput(formData.dateDebut)}
                        value={fmtDateInput(formData.dateFin)}
                        onChange={(e) => setFormData({ ...formData, dateFin: e.target.value })}
                    />
                </div>
            </form>
        </Modal>
    );
}