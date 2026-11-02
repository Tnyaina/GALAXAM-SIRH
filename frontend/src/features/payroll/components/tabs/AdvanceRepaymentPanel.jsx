import { useCallback, useEffect, useState } from 'react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Modal from '@/components/ui/overlays/Modal';
import Alert from '@/components/ui/feedback/Alert';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

export default function AdvanceRepaymentPanel({ idPeriode, canMutate, onChanged }) {
    const [items, setItems] = useState([]);
    const [error, setError] = useState('');
    const [selected, setSelected] = useState(null);
    const [mode, setMode] = useState('PREVU');
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

    const open = item => {
        setSimulatedNet(null);
        setSelected(item);
        const chosen = Number(item.retenueChoisie);
        setMode(chosen === Number(item.echeancePrevue) ? 'PREVU' : chosen === Number(item.soldeRestant) ? 'SOLDE' : 'LIBRE');
        setMontant(String(chosen));
        setMotif(item.motifChoix || '');
    };
    const amount = selected ? mode === 'PREVU' ? Number(selected.echeancePrevue)
        : mode === 'SOLDE' ? Number(selected.soldeRestant) : Number(montant) : 0;
    const valid = selected && amount > 0 && amount <= Number(selected.soldeRestant) && motif.trim().length >= 3;
    const save = async event => {
        event.preventDefault();
        if (!valid) return;
        setSaving(true);
        try {
            await paieService.choisirRetenueAvance(selected.idAvance, idPeriode, { montant: amount, motif: motif.trim() });
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

    if (!items.length && !error) return null;
    return <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3" aria-label="Remboursement des avances">
        <h3 className="text-sm font-semibold text-[var(--color-fg)]">Remboursement des avances</h3>
        <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Échéances prévues pour cette paie. La DAF peut choisir une retenue plus élevée, jusqu’au solde dû.</p>
        {error && <Alert type="error" message={error} />}
        {simulatedNet && <Alert type="info" message={`Simulation ${simulatedNet.employee} : net à payer ${formatMoneyAr(simulatedNet.net)}. Recalculez le bulletin pour enregistrer la retenue.`} />}
        <div className="mt-3 divide-y divide-[var(--color-border-subtle)]">
            {items.map(item => <div key={item.idAvance} className="flex flex-wrap items-center gap-2 py-2 text-xs">
                <div className="min-w-40 flex-1"><strong className="block text-[var(--color-fg)]">{item.nomComplet}</strong><span className="text-[var(--color-fg-muted)]">{item.matricule} · {item.nombreMensualites} mensualité(s)</span></div>
                <span className="text-[var(--color-fg-muted)]">Prévu : <strong>{formatMoneyAr(item.echeancePrevue)}</strong></span>
                <span className="text-[var(--color-fg-muted)]">Reste dû : <strong>{formatMoneyAr(item.soldeRestant)}</strong></span>
                <span className="font-semibold text-[var(--color-fg)]">Cette paie : {formatMoneyAr(item.retenueChoisie)}</span>
                {canMutate && <Button size="xs" variant="outline" onClick={() => open(item)}>Modifier la retenue</Button>}
            </div>)}
        </div>
        <Modal isOpen={Boolean(selected)} onClose={() => setSelected(null)} title="Retenue de l’avance sur cette paie" size="md"
            footer={<div className="flex justify-end gap-2"><Button variant="outline" onClick={() => setSelected(null)}>Annuler</Button><Button type="submit" form="advance-repayment-choice" disabled={!valid || saving} loading={saving}>Enregistrer</Button></div>}>
            {selected && <form id="advance-repayment-choice" onSubmit={save} className="space-y-4 text-sm">
                <p className="text-[var(--color-fg-muted)]">{selected.nomComplet} · Reste dû : {formatMoneyAr(selected.soldeRestant)}</p>
                <fieldset className="space-y-2"><legend className="mb-2 font-semibold">Retenue de ce mois</legend>
                    {[
                        ['PREVU', `Échéance prévue · ${formatMoneyAr(selected.echeancePrevue)}`],
                        ['LIBRE', 'Montant personnalisé'],
                        ['SOLDE', `Solder l’avance · ${formatMoneyAr(selected.soldeRestant)}`],
                    ].map(([value, label]) => <label key={value} className="flex items-center gap-2 border border-[var(--color-border-subtle)] p-2">
                        <input type="radio" name="repayment-mode" checked={mode === value} onChange={() => setMode(value)} />{label}
                    </label>)}
                </fieldset>
                {mode === 'LIBRE' && <MoneyInput label="Montant à retenir" value={montant} onValueChange={setMontant} min="0.01" />}
                <Input label="Motif de la décision" required value={motif} onChange={event => setMotif(event.target.value)} placeholder="Ex. Prime versée ce mois" />
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 text-xs">
                    <span className="block">Retenue demandée : <strong>{formatMoneyAr(amount)}</strong></span>
                    <span className="block">Reste après cette retenue : <strong>{formatMoneyAr(Math.max(0, Number(selected.soldeRestant) - amount))}</strong></span>
                    <span className="block text-[var(--color-fg-muted)]">Le net disponible peut réduire la retenue réelle ; vérifiez le bulletin après recalcul.</span>
                </div>
            </form>}
        </Modal>
    </section>;
}
