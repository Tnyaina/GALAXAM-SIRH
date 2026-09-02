import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, ArrowRight, CheckCircle2, Clock, AlertTriangle, ChevronDown, ChevronRight } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
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
                className="w-full flex items-center justify-between px-4 py-2.5 text-left hover:bg-[var(--color-bg-subtle)]/60 transition-colors cursor-pointer bg-[var(--color-bg-subtle)]/40"
            >
                <div className="flex items-center gap-2.5">
                    <Layers size={14} className="text-brand-600 shrink-0" />
                    <span className="text-[12.5px] font-bold text-[var(--color-fg)]">
                        Découpage &amp; Validation Séquentielle
                    </span>
                    <span className="text-[10.5px] font-mono px-1.5 py-0.5 font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)]">
                        HS 30%/50% · Retards
                    </span>
                </div>
                <div className="flex items-center gap-3">
                    <Link
                        to="/pointages/synthese"
                        onClick={(e) => e.stopPropagation()}
                        className="text-[11px] font-bold text-brand-600 hover:underline flex items-center gap-1"
                    >
                        <span>Synthèse détaillée</span>
                        <ArrowRight size={11} />
                    </Link>
                    {open
                        ? <ChevronDown size={14} className="text-[var(--color-fg-muted)]" />
                        : <ChevronRight size={14} className="text-[var(--color-fg-muted)]" />
                    }
                </div>
            </button>

            {/* Corps */}
            {open && (
                <div className="p-3 border-t border-[var(--color-border-subtle)]">
                    {loading ? (
                        <div className="py-6 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                            <Clock size={13} className="animate-spin text-brand-500" />
                            Chargement du découpage hebdomadaire…
                        </div>
                    ) : semaines.length === 0 ? (
                        <div className="py-4 text-center text-xs text-[var(--color-fg-muted)]">
                            Aucune semaine pour cette période.
                        </div>
                    ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
                            {semaines.map((sem) => {
                                const badgeVariant = sem.estValidee
                                    ? 'success'
                                    : sem.ready
                                        ? 'brand'
                                        : sem.nbAnomalies > 0
                                            ? 'warning'
                                            : 'default';

                                return (
                                    <Link
                                        key={sem.id}
                                        to={`/pointages/synthese?tab=hebdo&semaine=${sem.id}`}
                                        className={`p-2.5 text-left border transition-all flex flex-col justify-between gap-1.5 relative hover:shadow-xs ${
                                            sem.estValidee
                                                ? 'bg-emerald-500/[0.04] border-emerald-500/30 hover:border-emerald-500/60'
                                                : sem.ready
                                                    ? 'bg-brand-500/[0.04] border-brand-500/30 hover:border-brand-500/60'
                                                    : sem.nbAnomalies > 0
                                                        ? 'bg-amber-500/[0.04] border-amber-500/30 hover:border-amber-500/60'
                                                        : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] hover:border-[var(--color-border)]'
                                        }`}
                                    >
                                        {/* Titre & Coche de validation */}
                                        <div className="flex items-center justify-between gap-1">
                                            <span className="text-xs font-bold text-[var(--color-fg)]">
                                                {sem.title}
                                            </span>
                                            {sem.estValidee ? (
                                                <CheckCircle2 size={13} className="text-[var(--color-success-600)] shrink-0" />
                                            ) : sem.nbAnomalies > 0 ? (
                                                <AlertTriangle size={13} className="text-[var(--color-warning-600)] shrink-0" />
                                            ) : null}
                                        </div>

                                        {/* Dates */}
                                        <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] leading-tight truncate">
                                            {sem.dates}
                                        </p>

                                        {/* Badge épuré comme la 2e capture */}
                                        <div className="pt-1 border-t border-[var(--color-border-subtle)]/60 flex items-center justify-between gap-1">
                                            <Badge variant={badgeVariant} size="xs" className="!text-[9px] !px-1.5 font-bold truncate">
                                                {sem.estValidee ? 'VALIDÉE' : sem.ready ? 'PRÊTE À VALIDER' : sem.statut.toUpperCase()}
                                            </Badge>
                                        </div>
                                    </Link>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
