import { Send, CheckSquare, Square } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

export default function PointageTransmissionDafSection({
    periodeInfo = null,
    totalAnomalies = 0,
    isReadOnly = false,
    submittingDaf = false,
    onRequestSubmitDaf
}) {
    const isEnCours = periodeInfo?.statut_code === 'EN_COURS' || !periodeInfo?.statut_code;
    const periodeLibelle = periodeInfo?.libelle || periodeInfo?.code || 'la période active';

    const conditions = [
        { id: 1, label: 'Saisie quotidienne effectuée', done: true },
        { id: 2, label: 'Anomalies résolues', done: totalAnomalies === 0 },
        { id: 3, label: 'Semaines synthétisées', done: true }
    ];

    const canSubmit = isEnCours && totalAnomalies === 0 && !isReadOnly;

    return (
        <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 border-l-4 border-l-brand-600 shadow-xs">
            <div className="flex flex-col lg:flex-row lg:items-start justify-between gap-6">
                <div>
                    <h2 className="text-[15px] font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <Send size={16} className="text-brand-500" />
                        <span>Transmission à la DAF</span>
                    </h2>
                    <p className="text-[12.5px] text-[var(--color-fg-muted)] mt-1 max-w-xl leading-relaxed">
                        Une fois transmis, le mois de <strong className="text-[var(--color-fg)]">{periodeLibelle}</strong> passe en lecture seule pour la saisie agent : la DAF pourra vérifier chaque fiche de présence et procéder à la clôture paie.
                    </p>

                    <p className="text-[12px] text-[var(--color-fg-muted)] mt-3 font-semibold uppercase tracking-wider">
                        Conditions préalables à la transmission :
                    </p>

                    <ul className="mt-1.5 space-y-1.5 text-[13px] text-[var(--color-fg)]">
                        {conditions.map((c) => (
                            <li key={c.id} className="flex items-center gap-2">
                                {c.done ? (
                                    <CheckSquare size={15} className="text-emerald-600 shrink-0" />
                                ) : (
                                    <Square size={15} className="text-[var(--color-border-strong)] shrink-0" />
                                )}
                                <span className={c.done ? 'text-[var(--color-fg)] font-medium' : 'text-[var(--color-fg-muted)]'}>
                                    {c.label}
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>

                <div className="text-left lg:text-right shrink-0">
                    <Button
                        type="button"
                        variant="primary"
                        size="md"
                        disabled={!canSubmit || submittingDaf}
                        loading={submittingDaf}
                        onClick={onRequestSubmitDaf}
                        className="font-bold flex items-center gap-2 px-5 py-2.5 shadow-xs"
                    >
                        <Send size={15} />
                        <span>Transmettre le mois à la DAF</span>
                    </Button>
                    <p className="text-[11.5px] text-[var(--color-fg-muted)] mt-2 max-w-[240px] lg:ml-auto">
                        {canSubmit
                            ? 'Toutes les conditions sont remplies pour la transmission.'
                            : 'Régularisez les anomalies et vérifiez les saisies pour débloquer l\'envoi.'}
                    </p>
                </div>
            </div>
        </section>
    );
}
