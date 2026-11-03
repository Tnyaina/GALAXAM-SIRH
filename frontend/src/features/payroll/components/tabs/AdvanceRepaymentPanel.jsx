import { useCallback, useEffect, useState } from 'react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

export default function AdvanceRepaymentPanel({ idPeriode, onChanged, onItemsChange }) {
    const [items, setItems] = useState([]);
    const [error, setError] = useState('');
    const [selected, setSelected] = useState(null);
    const [mode, setMode] = useState(null);
    const [montant, setMontant] = useState('');
    const [motif, setMotif] = useState('');
    const [saving, setSaving] = useState(false);
    const [simulatedNet, setSimulatedNet] = useState(null);

    const load = useCallback(async () => {
        if (!idPeriode) return;
        try { setItems(await paieService.listerRetenuesAvances(idPeriode)); setError(''); }
        catch (err) { setError(getErrorMessage(err, 'Échéancier indisponible.')); }
    }, [idPeriode]);
    useEffect(() => { load(); }, [load]);

    const open = useCallback(item => {
        setSimulatedNet(null);
        setSelected(item);
        const hasChoice = Boolean(item.motifChoix?.trim());
        const chosen = Number(item.retenueChoisie);
        setMode(!hasChoice ? null : chosen === 0 ? 'REPORT'
            : chosen === Number(item.echeancePrevue) ? 'PREVU' : 'LIBRE');
        setMontant(hasChoice && chosen !== Number(item.echeancePrevue) && chosen > 0 ? String(chosen) : '');
        setMotif(item.motifChoix || '');
    }, []);

    useEffect(() => {
        onItemsChange?.(items.map(item => ({ ...item, ouvrirAjustement: () => open(item) })));
    }, [items, onItemsChange, open]);

    const amount = !selected ? 0
        : mode === 'REPORT' ? 0
            : mode === 'PREVU' ? Number(selected.echeancePrevue)
                : mode === 'LIBRE' ? Number(montant) : 0;
    const valid = selected && mode && Number.isFinite(amount)
        && amount >= 0 && amount <= Number(selected.soldeRestant)
        && motif.trim().length >= 3;

    const save = async event => {
        event.preventDefault();
        if (!valid) return;
        setSaving(true);
        try {
            await paieService.choisirRetenueAvance(selected.idAvance, idPeriode, {
                montant: amount,
                motif: motif.trim(),
            });
            try {
                const simulated = await paieService.calculerBulletinEmploye(selected.idEmploye, idPeriode, true);
                const net = simulated?.bulletin?.netAPayer ?? simulated?.bulletin?.net_a_payer;
                setSimulatedNet(net != null ? { employee: selected.nomComplet, net } : null);
            } catch { setSimulatedNet(null); }
            setSelected(null);
            await load();
            onChanged?.();
        } catch (err) { setError(getErrorMessage(err, 'Retenue non enregistrée.')); }
        finally { setSaving(false); }
    };

    if (!items.length && !error && !selected && !simulatedNet) return null;
    return <>
        {error && <Alert type="error" message={error} />}
        {simulatedNet && <Alert type="info" message={`Simulation ${simulatedNet.employee} : net à payer ${formatMoneyAr(simulatedNet.net)}. Recalculez le bulletin pour enregistrer la retenue.`} />}
        <Modal isOpen={Boolean(selected)} onClose={() => setSelected(null)} title="Ajuster la retenue de cette paie" size="md"
            footer={<div className="flex justify-end gap-2"><Button variant="outline" onClick={() => setSelected(null)}>Annuler</Button><Button type="submit" form="advance-repayment-choice" disabled={!valid || saving} loading={saving}>Enregistrer l’ajustement</Button></div>}>
            {selected && <form id="advance-repayment-choice" onSubmit={save} className="space-y-4 text-sm">
                <div className="border-l-2 border-l-[var(--color-info-500)] bg-[var(--color-info-500)]/5 p-3 text-xs text-[var(--color-fg-muted)]">
                    Sans ajustement, l’échéance de <strong className="text-[var(--color-fg)]">{formatMoneyAr(selected.echeancePrevue)}</strong> sera retenue automatiquement. Ce choix est facultatif.
                </div>
                <p className="text-xs text-[var(--color-fg-muted)]">{selected.nomComplet} · Reste dû : <strong className="text-[var(--color-fg)]">{formatMoneyAr(selected.soldeRestant)}</strong></p>
                <fieldset className="space-y-2">
                    <legend className="mb-2 font-semibold">Choisir une exception pour cette paie</legend>
                    {[
                        ...(selected.motifChoix?.trim() ? [['PREVU', `Revenir à l’échéance prévue · ${formatMoneyAr(selected.echeancePrevue)}`]] : []),
                        ['REPORT', 'Reporter la retenue · 0 Ar'],
                        ['LIBRE', 'Choisir un autre montant'],
                    ].map(([value, label]) => <label key={value} className={`flex cursor-pointer items-center gap-2 border p-3 ${mode === value ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5' : 'border-[var(--color-border-subtle)]'}`}>
                        <input type="radio" name="repayment-mode" checked={mode === value} onChange={() => setMode(value)} />
                        <span>{label}</span>
                    </label>)}
                </fieldset>
                {mode === 'LIBRE' && <MoneyInput label="Montant à retenir" value={montant} onValueChange={setMontant} min="0.01" max={selected.soldeRestant} />}
                {mode && <Input label="Motif de l’ajustement" required value={motif} onChange={event => setMotif(event.target.value)} placeholder="Ex. report demandé par le salarié" />}
                {mode && <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 text-xs">
                    <span className="block">Retenue sur cette paie : <strong>{formatMoneyAr(amount)}</strong></span>
                    <span className="block">Solde après retenue : <strong>{formatMoneyAr(Math.max(0, Number(selected.soldeRestant) - amount))}</strong></span>
                    <span className="mt-1 block text-[var(--color-fg-muted)]">La retenue réelle peut être limitée par le net disponible. Vérifiez le bulletin après recalcul.</span>
                </div>}
            </form>}
        </Modal>
    </>;
}
