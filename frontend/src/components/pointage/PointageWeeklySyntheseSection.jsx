import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Layers, ArrowRight, CheckCircle2, Clock, AlertTriangle } from 'lucide-react';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';

export default function PointageWeeklySyntheseSection({
    periodeInfo = null,
    selectedSite = null
}) {
    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        if (!periodeInfo?.id_periode_paie) return;

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
    }, [periodeInfo?.id_periode_paie, selectedSite]);

    return (
        <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-5 shadow-xs">
            <div className="flex items-center justify-between gap-3 mb-4">
                <div className="flex items-center gap-3">
                    <div className="w-[34px] h-[34px] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)] flex items-center justify-center font-bold">
                        <Layers size={17} />
                    </div>
                    <div>
                        <h2 className="text-[15px] font-bold text-[var(--color-fg)]">Synthèse hebdomadaire</h2>
                        <p className="text-[12px] text-[var(--color-fg-muted)]">
                            Consolidation des heures normales, calcul des HS (30 %/50 %) et compensation 1:1 des retards.
                        </p>
                    </div>
                </div>

                <Link
                    to="/pointages/synthese"
                    className="text-xs font-bold text-brand-600 dark:text-brand-400 hover:underline flex items-center gap-1 shrink-0"
                >
                    <span>Vue globale</span>
                    <ArrowRight size={13} />
                </Link>
            </div>

            {loading ? (
                <div className="p-8 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                    <Clock size={14} className="animate-spin text-brand-500" />
                    Chargement du découpage hebdomadaire...
                </div>
            ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3.5">
                    {semaines.map((w) => {
                        const isReady = w.ready;
                        const hasAnomalies = w.nbAnomalies > 0;

                        return (
                            <div
                                key={w.id}
                                className={`border p-4 transition-all ${
                                    isReady
                                        ? 'border-brand-500/40 bg-brand-500/5'
                                        : hasAnomalies
                                        ? 'border-amber-500/40 bg-amber-500/5'
                                        : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] opacity-70'
                                }`}
                            >
                                <div className="flex items-center justify-between">
                                    <p className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                        {w.title}
                                    </p>
                                    {isReady ? (
                                        <CheckCircle2 size={13} className="text-emerald-600" />
                                    ) : hasAnomalies ? (
                                        <AlertTriangle size={13} className="text-amber-600" />
                                    ) : null}
                                </div>

                                <p className="text-xs font-mono text-[var(--color-fg-muted)] mt-0.5">
                                    {w.dates} ({w.nbJoursOuvrables}j ouvrés)
                                </p>

                                <p className={`text-[13px] font-bold mt-1.5 ${isReady ? 'text-brand-700 dark:text-brand-300' : hasAnomalies ? 'text-amber-700' : 'text-[var(--color-fg)]'}`}>
                                    {w.statut}
                                </p>

                                <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5 leading-snug">
                                    {w.desc}
                                </p>

                                <Link
                                    to="/pointages/synthese"
                                    className={`inline-flex items-center gap-1 mt-3 text-[12px] font-bold ${
                                        isReady
                                            ? 'text-brand-600 dark:text-brand-400 hover:underline'
                                            : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                    }`}
                                >
                                    <span>Valider la semaine</span>
                                    <ArrowRight size={12} />
                                </Link>
                            </div>
                        );
                    })}
                </div>
            )}
        </section>
    );
}
