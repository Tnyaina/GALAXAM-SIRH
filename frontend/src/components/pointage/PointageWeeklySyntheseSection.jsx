import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, ArrowRight, CheckCircle2, Clock, AlertTriangle, ChevronDown, ChevronRight } from 'lucide-react';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';

export default function PointageWeeklySyntheseSection({
    periodeInfo = null,
    selectedSite = null
}) {
    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(false);
    const [open, setOpen] = useState(false);

    useEffect(() => {
        if (!periodeInfo?.id_periode_paie || !open) return;

        const fetchSemaines = async () => {
            setLoading(true);
            try {
                const res = await pointageSyntheseService.getSemaines({
                    idPeriodePaie: periodeInfo.id_periode_paie,
                    idSite: selectedSite || undefined
                });
                if (res?.semaines) {
                    setSemaines(res.semaines);
                }
            } catch (err) {
                console.error('Erreur chargement semaines backend:', err);
            } finally {
                setLoading(false);
            }
        };

        fetchSemaines();
    }, [periodeInfo?.id_periode_paie, selectedSite, open]);

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
            {/* Header cliquable */}
            <button
                type="button"
                onClick={() => setOpen(p => !p)}
                className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-[var(--color-bg-subtle)]/60 transition-colors cursor-pointer"
            >
                <div className="flex items-center gap-2.5">
                    <Layers size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                    <span className="text-[12.5px] font-bold text-[var(--color-fg)]">
                        Synthèse hebdomadaire
                    </span>
                    <span className="text-[11px] font-mono px-1.5 py-0.5 font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                        HS 30%/50% · Retards
                    </span>
                </div>
                <div className="flex items-center gap-2">
                    <Link
                        to="/pointages/synthese"
                        onClick={(e) => e.stopPropagation()}
                        className="text-[11px] font-bold text-brand-600 hover:underline flex items-center gap-1"
                    >
                        Vue globale <ArrowRight size={11} />
                    </Link>
                    {open
                        ? <ChevronDown size={14} className="text-[var(--color-fg-muted)]" />
                        : <ChevronRight size={14} className="text-[var(--color-fg-muted)]" />
                    }
                </div>
            </button>

            {/* Corps (lazy-chargé à l'ouverture) */}
            {open && (
                <div className="px-4 pb-4 border-t border-[var(--color-border-subtle)]">
                    {loading ? (
                        <div className="py-6 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                            <Clock size={13} className="animate-spin text-brand-500" />
                            Chargement du découpage hebdomadaire…
                        </div>
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3 mt-3">
                            {semaines.map((w) => {
                                const isReady = w.ready;
                                const hasAnomalies = w.nbAnomalies > 0;

                                return (
                                    <div
                                        key={w.id}
                                        className={`border p-3.5 transition-all ${
                                            isReady
                                                ? 'border-brand-500/40 bg-brand-500/5'
                                                : hasAnomalies
                                                ? 'border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/5'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] opacity-70'
                                        }`}
                                    >
                                        <div className="flex items-center justify-between">
                                            <p className="text-[10.5px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                                {w.title}
                                            </p>
                                            {isReady ? (
                                                <CheckCircle2 size={12} className="text-[var(--color-success-600)]" />
                                            ) : hasAnomalies ? (
                                                <AlertTriangle size={12} className="text-[var(--color-warning-600)]" />
                                            ) : null}
                                        </div>

                                        <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] mt-0.5">
                                            {w.dates} ({w.nbJoursOuvrables}j)
                                        </p>

                                        <p className={`text-[13px] font-bold mt-1 ${isReady ? 'text-brand-700 dark:text-brand-300' : hasAnomalies ? 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]' : 'text-[var(--color-fg)]'}`}>
                                            {w.statut}
                                        </p>

                                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 leading-snug">
                                            {w.desc}
                                        </p>

                                        <Link
                                            to="/pointages/synthese"
                                            className={`inline-flex items-center gap-1 mt-2.5 text-[11px] font-bold ${
                                                isReady
                                                    ? 'text-brand-600 dark:text-brand-400 hover:underline'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                            }`}
                                        >
                                            <span>Valider</span>
                                            <ArrowRight size={11} />
                                        </Link>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
