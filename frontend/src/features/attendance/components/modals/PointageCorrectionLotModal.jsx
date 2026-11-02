import { useEffect, useState } from 'react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';

export default function PointageCorrectionLotModal({ isOpen, onClose, days, onConfirm, saving, error }) {
    const [minutes, setMinutes] = useState('');
    const [motif, setMotif] = useState('');
    useEffect(() => {
        if (isOpen) {
            setMinutes('');
            setMotif('');
        }
    }, [isOpen]);
    const hasMinutes = minutes.trim() !== '';
    const target = hasMinutes && Number.isFinite(Number(minutes)) ? Number(minutes) : null;
    const valid = hasMinutes && Number.isInteger(target) && target >= -480 && target <= 480 && motif.trim().length >= 3;
    const previousTotal = days.reduce((sum, day) => sum + (Number(day.minutesCorrectionTemps) || 0), 0);
    const newTotal = target === null ? null : days.length * target;

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Corriger le temps en lot" size="2xl" closeOnBackdrop={!saving} closeOnEscape={!saving}>
            <form className="space-y-5" onSubmit={(event) => { event.preventDefault(); if (valid && days.length) onConfirm(target, motif.trim()); }}>
                <p className="text-sm text-[var(--color-fg-muted)]">
                    {days.length} journée{days.length > 1 ? 's' : ''} sélectionnée{days.length > 1 ? 's' : ''}. La valeur saisie remplace la correction actuelle de chaque journée.
                </p>
                {days.some(day => !day.heureEntreeAm || !day.heureSortieAm) && (
                    <Alert type="warning" message="La sélection contient une demi-journée. Vérifiez qu'elle doit aussi recevoir cette correction." />
                )}
                <div className="grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                    <label className="block text-sm font-medium">Correction par jour (minutes) <span className="text-[var(--color-danger-500)]">*</span>
                        <input type="number" min="-480" max="480" step="1" required value={minutes} onChange={event => setMinutes(event.target.value)}
                            placeholder="Ex. -7 ou +7" className="mt-2 w-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2" />
                        <span className="mt-2 block text-xs font-normal text-[var(--color-fg-muted)]">− retire des minutes, + en ajoute ; 0 efface la correction.</span>
                    </label>
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4 text-sm" aria-live="polite">
                        <div className="flex flex-wrap justify-between gap-2"><span>Correction totale après application</span><strong>{newTotal === null ? '—' : `${newTotal > 0 ? '+' : ''}${newTotal} min`}</strong></div>
                        <div className="mt-2 flex flex-wrap justify-between gap-2"><span>Variation par rapport à l’actuel</span><strong>{newTotal === null ? '—' : `${newTotal - previousTotal > 0 ? '+' : ''}${newTotal - previousTotal} min`}</strong></div>
                    </div>
                </div>
                <label className="block text-sm font-medium">Motif <span className="text-[var(--color-danger-500)]">*</span>
                    <input type="text" required minLength={3} value={motif} onChange={event => setMotif(event.target.value)}
                        placeholder="Ex. Ajustement du temps" className="mt-2 w-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2" />
                </label>
                <div className="border border-[var(--color-border-subtle)]">
                    <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]"><span>Journée</span><span>Correction actuelle → nouvelle</span></div>
                    <div className="max-h-48 overflow-y-auto divide-y divide-[var(--color-border-subtle)] text-sm">
                        {days.map(day => <div key={day.datePointage} className="flex justify-between gap-3 px-4 py-2"><span>{day.date}</span><span className="tabular-nums font-medium">{Number(day.minutesCorrectionTemps) || 0} min → {target === null ? '—' : `${target} min`}</span></div>)}
                    </div>
                </div>
                {error && <Alert type="error" message={error} />}
                <div className="flex justify-end gap-2">
                    <Button type="button" variant="outline" disabled={saving} onClick={onClose}>Annuler</Button>
                    <Button type="submit" disabled={!valid || !days.length || saving}>{saving ? 'Enregistrement…' : 'Appliquer au lot'}</Button>
                </div>
            </form>
        </Modal>
    );
}
