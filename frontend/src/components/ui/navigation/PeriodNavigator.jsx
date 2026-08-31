import { useRef } from 'react';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon, RotateCcw } from 'lucide-react';

/**
 * PeriodNavigator — Composant professionnel de navigation temporelle.
 * Adapte automatiquement son affichage selon le mode sélectionné :
 * - Jour : "< 30 août 2026 📅 >"
 * - Semaine : "< Semaine du 24 au 30 août 2026 📅 >"
 * - Mois : "< Août 2026 📅 >"
 */
export default function PeriodNavigator({
    viewPeriod = 'jour', // 'jour' | 'semaine' | 'mois'
    selectedDate,        // Format 'YYYY-MM-DD'
    onDateChange,        // Callback (newDateStr)
    allowFutureDates = true,
    showTodayButton = false,
    className = '',
}) {
    const dateInputRef = useRef(null);

    const currDate = new Date(selectedDate || new Date());

    // Navigation précédent / suivant selon la granularité
    const handleNavigate = (delta) => {
        const d = new Date(currDate);
        if (viewPeriod === 'jour') {
            d.setDate(d.getDate() + delta);
        } else if (viewPeriod === 'semaine') {
            d.setDate(d.getDate() + delta * 7);
        } else if (viewPeriod === 'mois') {
            d.setMonth(d.getMonth() + delta);
        }
        onDateChange(d.toISOString().substring(0, 10));
    };

    const handleToday = () => {
        onDateChange(new Date().toISOString().substring(0, 10));
    };

    // Formater l'étiquette affichée
    const getFormattedLabel = () => {
        if (isNaN(currDate.getTime())) return selectedDate;

        if (viewPeriod === 'jour') {
            const dayStr = currDate.toLocaleDateString('fr-FR', {
                day: 'numeric',
                month: 'long',
                year: 'numeric'
            }).replace(/\s+/g, ' ');
            return dayStr;
        }

        if (viewPeriod === 'semaine') {
            const monday = new Date(currDate);
            monday.setDate(currDate.getDate() - currDate.getDay() + (currDate.getDay() === 0 ? -6 : 1));
            const sunday = new Date(monday);
            sunday.setDate(monday.getDate() + 6);

            const monthStr = monday.toLocaleDateString('fr-FR', { month: 'long' }).replace(/\s+/g, ' ');
            const yearStr = monday.getFullYear();

            return `Semaine du ${monday.getDate()} au ${sunday.getDate()} ${monthStr} ${yearStr}`;
        }

        if (viewPeriod === 'mois') {
            const monthStr = currDate.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' }).replace(/\s+/g, ' ');
            return monthStr;
        }

        return selectedDate;
    };

    return (
        <div className={`inline-flex items-center gap-2 ${className}`}>
            {showTodayButton && (
                <button
                    type="button"
                    onClick={handleToday}
                    title="Revenir à aujourd'hui"
                    className="px-2 py-1.5 text-[11px] font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] bg-[var(--color-surface)] border border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)] transition-colors flex items-center gap-1 cursor-pointer"
                >
                    <RotateCcw size={12} />
                    Aujourd'hui
                </button>
            )}

            {/* Boîtier de navigation exact : < | date 📅 | > */}
            <div className="inline-flex items-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                {/* Flèche Précédent */}
                <button
                    type="button"
                    onClick={() => handleNavigate(-1)}
                    className="px-2.5 py-1.5 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                    title="Précédent"
                >
                    <ChevronLeft size={14} />
                </button>

                {/* Séparateur vertical discret */}
                <span className="h-4 w-[1px] bg-[var(--color-border-subtle)]" />

                {/* Libellé de la période + Icône Calendrier cliquable */}
                <div
                    onClick={() => dateInputRef.current?.showPicker()}
                    className="flex items-center justify-between gap-3 px-3 py-1.5 cursor-pointer hover:bg-[var(--color-bg-subtle)] transition-colors select-none"
                >
                    <span className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)] tracking-tight">
                        {getFormattedLabel()}
                    </span>

                    <CalendarIcon size={14} className="text-[var(--color-fg)] hover:text-[var(--color-brand-500)] shrink-0 transition-colors" />

                    {/* Input invisible pour ouvrir le DatePicker natif */}
                    <input
                        ref={dateInputRef}
                        type="date"
                        value={selectedDate}
                        max={!allowFutureDates ? new Date().toISOString().substring(0, 10) : undefined}
                        onChange={(e) => e.target.value && onDateChange(e.target.value)}
                        className="sr-only"
                    />
                </div>

                {/* Séparateur vertical discret */}
                <span className="h-4 w-[1px] bg-[var(--color-border-subtle)]" />

                {/* Flèche Suivant */}
                <button
                    type="button"
                    onClick={() => handleNavigate(1)}
                    disabled={!allowFutureDates && viewPeriod === 'jour' && currDate >= new Date(new Date().setHours(0,0,0,0))}
                    className={`px-2.5 py-1.5 text-[var(--color-fg)] transition-colors cursor-pointer ${
                        (!allowFutureDates && viewPeriod === 'jour' && currDate >= new Date(new Date().setHours(0,0,0,0)))
                        ? 'opacity-40 cursor-not-allowed'
                        : 'hover:bg-[var(--color-bg-subtle)]'
                    }`}
                    title="Suivant"
                >
                    <ChevronRight size={14} />
                </button>
            </div>
        </div>
    );
}
