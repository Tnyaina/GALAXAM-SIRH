import { useState } from 'react';
import { Send, CheckSquare, Square, ChevronDown, ChevronRight } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

export default function PointageTransmissionDafSection({
    periodeInfo = null,
    totalAnomalies = 0,
    isReadOnly = false,
    submittingDaf = false,
    onRequestSubmitDaf
}) {
    const [open, setOpen] = useState(false);

    const isEnCours = periodeInfo?.statut_code === 'EN_COURS' || !periodeInfo?.statut_code;
    const periodeLibelle = periodeInfo?.libelle || periodeInfo?.code || 'la période active';

    const conditions = [
        { id: 1, label: 'Saisie quotidienne effectuée', done: true },
        { id: 2, label: 'Anomalies résolues', done: totalAnomalies === 0 },
        { id: 3, label: 'Semaines synthétisées', done: true }
    ];

    const allDone = conditions.every(c => c.done);
    const canSubmit = isEnCours && allDone && !isReadOnly;

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-l-2 border-l-brand-600 shadow-xs overflow-hidden">
            {/* Header cliquable */}
            <button
                type="button"
                onClick={() => setOpen(p => !p)}
                className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-[var(--color-bg-subtle)]/60 transition-colors cursor-pointer"
            >
                <div className="flex items-center gap-2.5">
                    <Send size={14} className="text-brand-600 shrink-0" />
                    <span className="text-[12.5px] font-bold text-[var(--color-fg)]">
                        Transmission à la DAF
                    </span>
                    {allDone ? (
                        <span className="text-[11px] font-semibold px-1.5 py-0.5 bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-400)] font-mono">
                            Prêt à transmettre
                        </span>
                    ) : (
                        <span className="text-[11px] font-semibold px-1.5 py-0.5 bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] font-mono">
                            {conditions.filter(c => !c.done).length} condition(s) manquante(s)
                        </span>
                    )}
                </div>
                {open
                    ? <ChevronDown size={14} className="text-[var(--color-fg-muted)]" />
                    : <ChevronRight size={14} className="text-[var(--color-fg-muted)]" />
                }
            </button>

            {/* Corps */}
            {open && (
                <div className="px-4 pb-4 border-t border-[var(--color-border-subtle)]">
                    <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-5 mt-3">
                        <div className="flex-1">
                            <p className="text-[12.5px] text-[var(--color-fg-muted)] leading-relaxed max-w-xl">
                                Une fois transmis, le mois de <strong className="text-[var(--color-fg)]">{periodeLibelle}</strong> passe en lecture seule. La DAF pourra vérifier chaque fiche et procéder à la clôture paie.
                            </p>

                            <ul className="mt-3 space-y-1.5">
                                {conditions.map((c) => (
                                    <li key={c.id} className="flex items-center gap-2 text-[12.5px]">
                                        {c.done ? (
                                            <CheckSquare size={13} className="text-[var(--color-success-600)] shrink-0" />
                                        ) : (
                                            <Square size={13} className="text-[var(--color-border-strong)] shrink-0" />
                                        )}
                                        <span className={c.done ? 'text-[var(--color-fg)] font-medium' : 'text-[var(--color-fg-muted)]'}>
                                            {c.label}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        </div>

                        <div className="shrink-0">
                            <Button
                                type="button"
                                variant="primary"
                                size="sm"
                                disabled={!canSubmit || submittingDaf}
                                loading={submittingDaf}
                                onClick={onRequestSubmitDaf}
                                className="font-bold flex items-center gap-2 shadow-xs"
                            >
                                <Send size={13} />
                                <span>Transmettre à la DAF</span>
                            </Button>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-1.5 max-w-[220px]">
                                {canSubmit
                                    ? 'Toutes les conditions sont remplies.'
                                    : 'Régularisez les anomalies pour débloquer.'}
                            </p>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
