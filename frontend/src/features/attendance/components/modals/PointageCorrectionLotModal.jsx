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
    const target = Number(minutes);
    const valid = Number.isInteger(target) && target >= -480 && target <= 480 && motif.trim().length >= 3;
    const previousTotal = days.reduce((sum, day) => sum + (Number(day.minutesCorrectionTemps) || 0), 0);
    const newTotal = days.length * target;

    return (
        <Modal isOpen={isOpen} onClose={onClose} title="Corriger le temps en lot" size="lg" closeOnBackdrop={!saving} closeOnEscape={!saving}>
            <form className="space-y-4 p-4" onSubmit={(event) => { event.preventDefault(); if (valid && days.length) onConfirm(target, motif.trim()); }}>
                <p className="text-sm text-[var(--color-fg-muted)]">
                    {days.length} journée{days.length > 1 ? 's' : ''} sélectionnée{days.length > 1 ? 's' : ''}. La valeur saisie remplace la correction actuelle de chaque journée.
                </p>
                {days.some(day => !day.heureEntreeAm || !day.heureSortieAm) && (
                    <Alert type="warning" message="La sélection contient une demi-journée. Vérifiez qu'elle doit aussi recevoir cette correction." />
                )}
                <div className="grid gap-3 sm:grid-cols-2">
                    <label className="text-sm font-medium">Correction par jour (minutes)
                        <input type="number" min="-480" max="480" step="1" required value={minutes} onChange={event => setMinutes(event.target.value)}
                            placeholder="Ex. -7" className="mt-1 w-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2" />
                    </label>
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 text-sm">
                        <div>Nouvelle correction totale : <strong>{Number.isFinite(newTotal) ? newTotal : 0} min</strong></div>
                        <div>Variation : <strong>{Number.isFinite(newTotal) ? newTotal - previousTotal : 0} min</strong></div>
                    </div>
                </div>
                <label className="block text-sm font-medium">Motif
                    <input type="text" required minLength={3} value={motif} onChange={event => setMotif(event.target.value)}
                        placeholder="Ex. correction des sorties" className="mt-1 w-full border border-[var(--color-border)] bg-[var(--color-surface)] px-3 py-2" />
                </label>
                <div className="max-h-36 overflow-y-auto border border-[var(--color-border-subtle)] p-2 text-xs">
                    {days.map(day => <div key={day.datePointage} className="flex justify-between py-0.5"><span>{day.date}</span><span>{Number(day.minutesCorrectionTemps) || 0} → {Number.isFinite(target) ? target : 0} min</span></div>)}
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
