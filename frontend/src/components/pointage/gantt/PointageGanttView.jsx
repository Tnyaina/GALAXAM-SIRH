import EmptyState from '@/components/ui/data-display/EmptyState';
import { Calendar, Clock } from 'lucide-react';
import { getStatutPointageBadge } from '@/utils/pointageUtils';

// --- Échelle horaire du Gantt ---
// Amplitude d'affichage des barres : 6h à 20h (couvre largement les horaires de bureau usuels).
const DAY_START_HOUR = 6;
const DAY_END_HOUR = 20;
const DAY_SPAN_HOURS = DAY_END_HOUR - DAY_START_HOUR;

// Repères horaires affichés sur la frise (uniquement à titre indicatif, purement visuel).
const HOUR_TICKS = [9, 12, 15, 18].map((hour) => ({
    hour,
    pct: ((hour - DAY_START_HOUR) / DAY_SPAN_HOURS) * 100,
}));

// Couleurs de barre alignées sur les variantes déjà utilisées par le design system (Badge/Alert).
const VARIANT_BAR_COLORS = {
    success: 'bg-[var(--color-success-500)]',
    warning: 'bg-[var(--color-warning-500)]',
    danger: 'bg-[var(--color-danger-500)]',
    info: 'bg-[var(--color-brand-500)]',
};
const DEFAULT_BAR_COLOR = 'bg-[var(--color-fg-muted)]';

/** Convertit "HH:MM" en heure décimale. Retourne null si le format est inattendu. */
function parseHeureEnDecimal(heure) {
    if (typeof heure !== 'string') return null;
    const match = heure.match(/^(\d{1,2}):(\d{2})$/);
    if (!match) return null;
    const h = parseInt(match[1], 10);
    const m = parseInt(match[2], 10);
    if (Number.isNaN(h) || Number.isNaN(m)) return null;
    return h + m / 60;
}

/** Calcule la position (left %) et la largeur (width %) de la barre sur l'échelle 6h-20h. */
function getBarStyle(debut, fin) {
    const start = parseHeureEnDecimal(debut);
    const end = parseHeureEnDecimal(fin);
    if (start === null || end === null) return null;

    const clamp = (v) => Math.min(100, Math.max(0, v));
    const left = clamp(((start - DAY_START_HOUR) / DAY_SPAN_HOURS) * 100);
    const rawWidth = ((end - start) / DAY_SPAN_HOURS) * 100;
    const width = Math.max(clamp(rawWidth), 2); // largeur plancher pour rester visible/cliquable

    return { left, width };
}

export default function PointageGanttView({
    ganttData = [],
    ganttPeriod = 'semaine',
    onPeriodChange
}) {
    if (!ganttData || ganttData.length === 0) {
        return (
            <EmptyState
                icon={Calendar}
                title="Aucune donnée de planning"
                description="Aucun employé ou pointage enregistré pour ce site sur la période."
            />
        );
    }

    return (
        <div className="space-y-4">
            {/* Barre de granularité Gantt */}
            <div className="bg-[var(--color-surface)] p-3 border border-[var(--color-border-subtle)] flex items-center justify-between shadow-sm">
                <span className="text-xs font-bold text-[var(--color-fg)] uppercase tracking-wider">
                    Vue Gantt Chronologique — {ganttPeriod.toUpperCase()}
                </span>
                <div className="flex bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-0.5">
                    <button
                        onClick={() => onPeriodChange('jour')}
                        className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${ganttPeriod === 'jour' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                    >
                        Jour
                    </button>
                    <button
                        onClick={() => onPeriodChange('semaine')}
                        className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${ganttPeriod === 'semaine' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                    >
                        Semaine
                    </button>
                    <button
                        onClick={() => onPeriodChange('mois')}
                        className={`px-3 py-1 text-xs font-semibold uppercase tracking-wider transition-colors ${ganttPeriod === 'mois' ? 'bg-[var(--color-brand-500)] text-white shadow-xs' : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                    >
                        Mois
                    </button>
                </div>
            </div>

            {/* Frise horaire & légende */}
            <div className="bg-[var(--color-surface)] p-3 border border-[var(--color-border-subtle)] flex flex-wrap items-center justify-between gap-3 shadow-sm text-[11px] text-[var(--color-fg-muted)]">
                <div className="flex items-center gap-1.5">
                    <Clock size={13} />
                    <span>Chaque barre représente la plage travaillée (entrée → sortie), sur une amplitude de {DAY_START_HOUR}h à {DAY_END_HOUR}h.</span>
                </div>
                <div className="flex items-center gap-3">
                    <span className="font-bold uppercase tracking-wider text-[var(--color-fg)]">Couleur :</span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-[var(--color-success-500)] inline-block" /> Normal</span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-[var(--color-warning-500)] inline-block" /> Retard</span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-[var(--color-danger-500)] inline-block" /> Anomalie</span>
                    <span className="flex items-center gap-1.5"><span className="w-2.5 h-2.5 bg-[var(--color-fg-muted)] inline-block" /> Absence</span>
                </div>
            </div>

            {/* Grille Gantt */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm overflow-x-auto">
                <table className="w-full text-left border-collapse min-w-[700px]">
                    <thead>
                        <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)]">
                            <th className="p-3 text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] w-64 border-r border-[var(--color-border-subtle)] sticky top-0 left-0 z-20 bg-[var(--color-bg-subtle)] shadow-[4px_0_6px_-4px_rgba(0,0,0,0.15)]">
                                Employé
                            </th>
                            {ganttData[0]?.blocs?.map((bloc, idx) => (
                                <th key={idx} className="p-2 text-center text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-r border-[var(--color-border-subtle)] min-w-[110px] sticky top-0 z-10 bg-[var(--color-bg-subtle)]">
                                    {bloc.libelleDate}
                                </th>
                            ))}
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-[var(--color-border-subtle)]">
                        {ganttData.map((emp, empIdx) => (
                            <tr
                                key={emp.idEmploye}
                                className={`hover:bg-[var(--color-bg-subtle)]/40 transition-colors ${empIdx % 2 === 1 ? 'bg-[var(--color-bg-subtle)]/15' : ''}`}
                            >
                                <td className="p-3 border-r border-[var(--color-border-subtle)] sticky left-0 z-10 bg-[var(--color-surface)] shadow-[4px_0_6px_-4px_rgba(0,0,0,0.15)]">
                                    <div className="flex items-center gap-3">
                                        <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)] text-white flex items-center justify-center font-bold text-xs shrink-0">
                                            {emp.nomPrenom?.substring(0, 2).toUpperCase()}
                                        </div>
                                        <div className="overflow-hidden">
                                            <p className="text-xs font-bold text-[var(--color-fg)] truncate">{emp.nomPrenom}</p>
                                            <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{emp.matricule} • {emp.fonction || 'Employé'}</p>
                                        </div>
                                    </div>
                                </td>
                                {emp.blocs?.map((bloc, idx) => {
                                    const badge = getStatutPointageBadge(bloc.codeStatut, bloc.minutesRetard);
                                    const variant = badge?.props?.variant;
                                    const barColorClass = VARIANT_BAR_COLORS[variant] || DEFAULT_BAR_COLOR;
                                    const hasHoraires = bloc.debut !== '—';
                                    const barStyle = hasHoraires ? getBarStyle(bloc.debut, bloc.fin) : null;

                                    return (
                                        <td key={idx} className="p-2 border-r border-[var(--color-border-subtle)] text-center align-top">
                                            <div className="flex flex-col items-center gap-1.5">
                                                {badge}

                                                {/* Barre chronologique proportionnelle (frise 6h-20h) */}
                                                <div
                                                    className="w-full h-4 relative bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] overflow-hidden"
                                                    title={hasHoraires ? `${bloc.debut} - ${bloc.fin}` : 'Aucun horaire enregistré'}
                                                >
                                                    {HOUR_TICKS.map((tick) => (
                                                        <div
                                                            key={tick.hour}
                                                            className="absolute top-0 bottom-0 w-px bg-[var(--color-border-subtle)]"
                                                            style={{ left: `${tick.pct}%` }}
                                                        />
                                                    ))}
                                                    {barStyle && (
                                                        <div
                                                            className={`absolute top-0 bottom-0 ${barColorClass}`}
                                                            style={{ left: `${barStyle.left}%`, width: `${barStyle.width}%` }}
                                                        />
                                                    )}
                                                </div>

                                                {hasHoraires && (
                                                    <p className="font-mono text-[10px] text-[var(--color-fg-muted)]">
                                                        {bloc.debut} - {bloc.fin}
                                                    </p>
                                                )}
                                            </div>
                                        </td>
                                    );
                                })}
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}