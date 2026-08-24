import React, { useRef } from 'react';
import { ChevronLeft, ChevronRight, Calendar as CalendarIcon, RotateCcw } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/**
 * PeriodNavigator — Composant professionnel de navigation temporelle.
 * Adapte automatiquement son affichage selon le mode sélectionné :
 * - Jour : "< 24/08/2026 📅 >"
 * - Semaine : "< 24 août — 30 août 2026 📅 >"
 * - Mois : "< Août 2026 📅 >"
 */
export default function PeriodNavigator({
    viewPeriod = 'jour', // 'jour' | 'semaine' | 'mois'
    selectedDate,        // Format 'YYYY-MM-DD'
    onDateChange,        // Callback (newDateStr)
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
            return currDate.toLocaleDateString('fr-FR', {
                day: '2-digit',
                month: '2-digit',
                year: 'numeric',
            });
        }

        if (viewPeriod === 'semaine') {
            const first = currDate.getDate() - (currDate.getDay() === 0 ? 6 : currDate.getDay() - 1);
            const monday = new Date(new Date(currDate).setDate(first));
            const sunday = new Date(new Date(currDate).setDate(first + 6));

            const mDay = monday.getDate();
            const sDay = sunday.getDate();
            const monthStr = monday.toLocaleDateString('fr-FR', { month: 'long' });
            const yearStr = monday.getFullYear();

            return `${mDay} ${monthStr} — ${sDay} ${monthStr} ${yearStr}`;
        }

        if (viewPeriod === 'mois') {
            const monthStr = currDate.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
            return monthStr.charAt(0).toUpperCase() + monthStr.slice(1);
        }

        return selectedDate;
    };

    return (
        <div className="inline-flex items-center gap-2">
            {/* Bouton Aujourd'hui */}
            <button
                type="button"
                onClick={handleToday}
                title="Revenir à aujourd'hui"
                className="px-2.5 py-1.5 text-xs font-semibold text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] bg-[var(--color-surface)] border border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)] transition-colors flex items-center gap-1.5"
            >
                <RotateCcw size={13} />
                Aujourd'hui
            </button>

            {/* Boîtier principal de navigation style Pro */}
            <div className="inline-flex items-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-sm font-sans">
                {/* Fleche Précédent */}
                <button
                    type="button"
                    onClick={() => handleNavigate(-1)}
                    className="p-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] border-r border-[var(--color-border-subtle)] transition-colors"
                    title="Période précédente"
                >
                    <ChevronLeft size={16} />
                </button>

                {/* Libellé de la période + Icône Calendrier Clicable */}
                <div
                    onClick={() => dateInputRef.current?.showPicker()}
                    className="flex items-center gap-3 px-4 py-1.5 cursor-pointer hover:bg-[var(--color-bg-subtle)] transition-colors select-none"
                >
                    <span className="text-sm font-bold text-[var(--color-fg)] font-mono tracking-tight">
                        {getFormattedLabel()}
                    </span>

                    <CalendarIcon size={16} className="text-[var(--color-brand-500)] shrink-0" />

                    {/* Input Date invisible pour le DatePicker natif */}
                    <input
                        ref={dateInputRef}
                        type="date"
                        value={selectedDate}
                        onChange={(e) => e.target.value && onDateChange(e.target.value)}
                        className="sr-only"
                    />
                </div>

                {/* Fleche Suivant */}
                <button
                    type="button"
                    onClick={() => handleNavigate(1)}
                    className="p-2 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] border-l border-[var(--color-border-subtle)] transition-colors"
                    title="Période suivante"
                >
                    <ChevronRight size={16} />
                </button>
            </div>
        </div>
    );
}
