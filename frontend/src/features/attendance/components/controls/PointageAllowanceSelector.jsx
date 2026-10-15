import { changePointageAllowance } from '@/features/attendance/utils/pointageRow';

export default function PointageAllowanceSelector({ allowances, selections, onChange, absence = false }) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {allowances.map((allowance) => {
                const key = String(allowance.idIndemnite);
                const selection = selections[key] || {};
                const fractionable = allowance.estFractionnable ?? allowance.fractionnable;
                const update = (value, moment) => onChange((previous) => ({
                    ...previous,
                    [key]: changePointageAllowance(previous[key], value, moment)
                }));
                return (
                    <div key={key} className="border border-[var(--color-border)] bg-[var(--color-surface)] p-3 space-y-2 text-xs">
                        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                            <strong className="text-[var(--color-brand-700)] shrink-0">{allowance.code}</strong>
                            <span className="text-[var(--color-fg)]" title={allowance.libelle}>{allowance.libelle}</span>
                        </div>
                        <div className="flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-[var(--color-border-subtle)] pt-2 text-[var(--color-fg)]">
                            {fractionable ? (
                                <>
                                    <label className="flex items-center gap-1.5 cursor-pointer"><input type="checkbox" checked={Boolean(selection.matin)} onChange={(event) => update(event.target.checked, 'MATIN')} /> Matin</label>
                                    <label className="flex items-center gap-1.5 cursor-pointer"><input type="checkbox" checked={Boolean(selection.am)} onChange={(event) => update(event.target.checked, 'APRES_MIDI')} /> Après-midi</label>
                                </>
                            ) : (
                                <label className="flex items-center gap-1.5 cursor-pointer"><input type="checkbox" checked={Boolean(selection.accordee)} onChange={(event) => update(event.target.checked)} /> {absence ? 'Accorder exceptionnellement' : 'Accorder'}</label>
                            )}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
