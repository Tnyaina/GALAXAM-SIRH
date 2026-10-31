import { changePointageAllowance } from '@/features/attendance/utils/pointageRow';

export default function PointageAllowanceSelector({ allowances, selections, onChange, absence = false }) {
    if (!allowances || allowances.length === 0) return null;

    return (
        <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-3 flex flex-wrap items-center gap-x-6 gap-y-2 text-xs">
            {allowances.map((allowance, index) => {
                const key = String(allowance.idIndemnite);
                const selection = selections[key] || {};
                const fractionable = allowance.estFractionnable ?? allowance.fractionnable;
                const update = (value, moment) => onChange((previous) => ({
                    ...previous,
                    [key]: changePointageAllowance(previous[key], value, moment)
                }));
                return (
                    <div key={key} className="flex items-center gap-3">
                        {index > 0 && <div className="h-4 w-px bg-[var(--color-border)] hidden sm:block" />}
                        <span className="text-[var(--color-fg)] font-medium">
                            {(allowance.libelle || allowance.code || '').replace(/^Indemnité de\s+/i, '')}
                        </span>
                        <div className="flex items-center gap-3">
                            {fractionable ? (
                                <>
                                    <label className="flex items-center gap-1.5 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={Boolean(selection.matin)}
                                            onChange={(event) => update(event.target.checked, 'MATIN')}
                                            className="w-3.5 h-3.5 accent-[var(--color-brand-600)] rounded-none"
                                        />
                                        <span className="text-[var(--color-fg)]">Matin</span>
                                    </label>
                                    <label className="flex items-center gap-1.5 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={Boolean(selection.am)}
                                            onChange={(event) => update(event.target.checked, 'APRES_MIDI')}
                                            className="w-3.5 h-3.5 accent-[var(--color-brand-600)] rounded-none"
                                        />
                                        <span className="text-[var(--color-fg)]">Après-midi</span>
                                    </label>
                                </>
                            ) : (
                                <label className="flex items-center gap-1.5 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        checked={Boolean(selection.accordee)}
                                        onChange={(event) => update(event.target.checked)}
                                        className="w-3.5 h-3.5 accent-[var(--color-brand-600)] rounded-none"
                                    />
                                    <span className="text-[var(--color-fg)]">
                                        {absence ? 'Accorder exceptionnellement' : 'Accorder'}
                                    </span>
                                </label>
                            )}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
