import { useRef } from 'react';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon, RotateCcw } from 'lucide-react';
import { getTodayDateString, isDateInFuture } from '@/utils/dateUtils';

/**
 * PeriodNavigator — Composant professionnel de navigation temporelle.
 * Adapte automatiquement son affichage selon le mode sélectionné :
 * - Jour : "< 30 août 2026 >"
 * - Semaine : "< Semaine du 24 au 30 août 2026 >"
 * - Mois : "< Août 2026 >"
 */
export default function PeriodNavigator({
    // Rendu et actions de navigation entre périodes.
    viewPeriod = 'jour', // 'jour' | 'semaine' | 'mois'
    selectedDate,        // Format 'YYYY-MM-DD'
    onDateChange,        // Callback (newDateStr)
    allowFutureDates = true,
    showTodayButton = false,
    className = 'h-[32px]',
}) {
    const dateInputRef = useRef(null);

    const parseDate = (dStr) => {
        if (!dStr) return new Date();
        if (typeof dStr === 'string' && dStr.includes('-')) {
            const parts = dStr.split('-');
            if (parts.length === 3) {
                return new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10));
            }
        }
        return new Date(dStr);
    };

    const formatDateToISO = (date) => {
        const y = date.getFullYear();
        const m = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        return `${y}-${m}-${d}`;
    };

    const currDate = parseDate(selectedDate);
    const todayStr = getTodayDateString();

    const isFutureBlocked = !allowFutureDates && (
        (viewPeriod === 'jour' && selectedDate >= todayStr) ||
        (viewPeriod === 'semaine' && isDateInFuture(selectedDate)) ||
        (viewPeriod === 'mois' && selectedDate?.substring(0, 7) >= todayStr.substring(0, 7))
    );

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
        const nextDateStr = formatDateToISO(d);
        if (!allowFutureDates && delta > 0 && nextDateStr > todayStr) {
            return;
        }
        onDateChange(nextDateStr);
    };

    const handleToday = () => {
        onDateChange(getTodayDateString());
    };


    // Formater l'étiquette affichée
    const getFormattedLabel = () => {
        if (isNaN(currDate.getTime())) return selectedDate;

        if (viewPeriod === 'jour') {
            const rawWeekday = currDate.toLocaleDateString('fr-FR', { weekday: 'short' });
            const weekday = rawWeekday ? (rawWeekday.charAt(0).toUpperCase() + rawWeekday.slice(1)) : '';
            const dayStr = currDate.toLocaleDateString('fr-FR', {
                day: 'numeric',
                month: 'long',
                year: 'numeric'
            }).replace(/\s+/g, ' ');
            return `${weekday} ${dayStr}`;
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
                    className="h-full px-2 text-[11px] font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] bg-[var(--color-surface)] border border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)] transition-colors flex items-center gap-1 cursor-pointer"
                >
                    <RotateCcw size={12} />
                    Aujourd'hui
                </button>
            )}

            {/* Boîtier de navigation exact : < | date | > */}
            <div className="inline-flex items-stretch h-full bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                {/* Flèche Précédent */}
                <button
                    type="button"
                    onClick={() => handleNavigate(-1)}
                    className="h-full px-2.5 flex items-center justify-center text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer"
                    title="Précédent"
                >
                    <ChevronLeft size={14} />
                </button>

                {/* Séparateur vertical discret */}
                <span className="self-center h-4 w-[1px] bg-[var(--color-border-subtle)] shrink-0" />

                {/* Libellé de la période + Icône Calendrier cliquable */}
                <div
                    onClick={() => dateInputRef.current?.showPicker()}
                    className="h-full flex items-center justify-between gap-3 px-3 cursor-pointer hover:bg-[var(--color-bg-subtle)] transition-colors select-none"
                >
                    <span className="text-xs sm:text-[13px] font-bold text-[var(--color-fg)] tracking-tight">
                        {getFormattedLabel() || 'Sélectionner une date'}
                    </span>

                    <CalendarIcon size={14} className="text-[var(--color-fg)] hover:text-[var(--color-brand-500)] shrink-0 transition-colors" />

                    {/* Input invisible pour ouvrir le DatePicker natif */}
                    <input
                        ref={dateInputRef}
                        type="date"
                        value={selectedDate}
                        max={!allowFutureDates ? todayStr : undefined}
                        onChange={(e) => e.target.value && onDateChange(e.target.value)}
                        className="sr-only"
                    />
                </div>

                {/* Séparateur vertical discret */}
                <span className="self-center h-4 w-[1px] bg-[var(--color-border-subtle)] shrink-0" />

                {/* Flèche Suivant */}
                <button
                    type="button"
                    onClick={() => handleNavigate(1)}
                    disabled={isFutureBlocked}
                    className={`h-full px-2.5 flex items-center justify-center text-[var(--color-fg)] transition-colors ${
                        isFutureBlocked
                            ? 'opacity-30 cursor-not-allowed pointer-events-none'
                            : 'hover:bg-[var(--color-bg-subtle)] cursor-pointer'
                    }`}
                    title={isFutureBlocked ? "Navigation future non autorisée pour la saisie" : "Suivant"}
                >
                    <ChevronRight size={14} />
                </button>
            </div>
        </div>
    );
}
