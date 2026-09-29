import { TrendingUp, TrendingDown } from 'lucide-react';

/**
 * StatCard — Composant universel pour l'affichage de métriques et KPI du SIRH.
 * Supporte les modes statiques et interactifs (cliquables avec état actif pour les filtres rapides).
 *
 * @param {string} title - Libellé supérieur du KPI
 * @param {string|number} value - Valeur principale
 * @param {string} [subtext] - Sous-texte explicatif
 * @param {React.ReactNode} [icon] - Icône optionnelle
 * @param {string} [color='brand'] - Palette : 'brand' | 'success' | 'warning' | 'danger' | 'info' | 'neutral'
 * @param {number} [change] - Pourcentage de variation
 * @param {string} [changeLabel] - Libellé de variation
 * @param {boolean} [active=false] - Indique si le KPI est sélectionné comme filtre actif
 * @param {function} [onClick] - Gestionnaire de clic (transforme la carte en bouton interactif)
 * @param {boolean} [borderTop=false] - Bordure supérieure, neutre au repos et de marque si sélectionnée
 * @param {boolean} [bordered=true] - Désactive le cadre quand le KPI est dans une Card parente
 * @param {string} [className] - Classes CSS additionnelles
 */
export default function StatCard({
    title,
    value,
    subtext,
    icon,
    color = 'brand',
    change,
    changeLabel,
    active = false,
    onClick,
    borderTop = false,
    bordered = true,
    className = ''
}) {
    const isClickable = typeof onClick === 'function';
    const isPositive = change !== undefined ? change >= 0 : true;

    // Configuration des styles de couleurs et d'accents
    const colorStyles = {
        brand: {
            text: 'text-[var(--color-brand-600)]',
            iconBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/20'
        },
        success: {
            text: 'text-success-600 dark:text-success-400',
            label: 'text-success-700 dark:text-success-400',
            iconBg: 'bg-success-500/10 text-success-600 dark:text-success-400 border-success-500/20'
        },
        warning: {
            text: 'text-warning-600 dark:text-warning-400',
            label: 'text-warning-700 dark:text-warning-400',
            iconBg: 'bg-warning-500/10 text-warning-600 dark:text-warning-400 border-warning-500/20'
        },
        danger: {
            text: 'text-danger-600 dark:text-danger-400',
            label: 'text-danger-700 dark:text-danger-400',
            iconBg: 'bg-danger-500/10 text-danger-600 dark:text-danger-400 border-danger-500/20'
        },
        info: {
            text: 'text-info-600 dark:text-info-400',
            iconBg: 'bg-info-500/10 text-info-600 dark:text-info-400 border-info-500/20'
        },
        neutral: {
            text: 'text-[var(--color-fg)]',
            iconBg: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
        }
    };

    const style = colorStyles[color] || colorStyles.brand;

    // Calcul des classes de conteneur
    const baseClasses = `
        bg-[var(--color-surface)] text-left p-3 sm:p-3.5 transition-colors
        ${bordered ? 'border border-[var(--color-border-subtle)]' : 'border border-transparent bg-[var(--color-bg-subtle)]/50'}
        ${borderTop ? `border-t-2 ${active ? 'border-t-[var(--color-brand-500)]' : 'border-t-[var(--color-border-subtle)]'}` : ''}
        ${active && isClickable ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/[0.06]' : ''}
        ${isClickable ? 'cursor-pointer hover:border-[var(--color-brand-400)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-focus-ring)]' : ''}
        ${className}
    `.trim();

    const Content = (
        <>
            <div className="flex items-start justify-between gap-2.5 mb-1">
                <div className="min-w-0">
                    <p className={`text-[10px] font-bold uppercase tracking-wider ${style.label || 'text-[var(--color-fg-muted)]'} truncate`}>
                        {title}
                    </p>
                    <p className={`text-lg sm:text-xl font-extrabold font-data mt-0.5 ${style.text || 'text-[var(--color-fg)]'}`}>
                        {value ?? 0}
                    </p>
                </div>
                {icon && (
                    <div className={`w-8 h-8 border rounded-none ${style.iconBg} flex items-center justify-center shrink-0`}>
                        {icon}
                    </div>
                )}
            </div>

            {change !== undefined ? (
                <div className={`flex items-center gap-1.5 text-xs font-semibold font-data mt-1.5 ${isPositive ? 'text-success-600 dark:text-success-400' : 'text-danger-600 dark:text-danger-400'}`}>
                    {isPositive ? <TrendingUp size={13} /> : <TrendingDown size={13} />}
                    <span>{isPositive ? '+' : ''}{change}%</span>
                    {changeLabel && <span className="text-[var(--color-fg-subtle)] font-normal ml-1">{changeLabel}</span>}
                </div>
            ) : subtext ? (
                <p className="text-[11px] text-[var(--color-fg-subtle)] mt-1 font-medium leading-tight truncate">
                    {subtext}
                </p>
            ) : null}
        </>
    );

    if (isClickable) {
        return (
            <button
                type="button"
                onClick={onClick}
                aria-pressed={active}
                className={baseClasses}
            >
                {Content}
            </button>
        );
    }

    return (
        <div className={baseClasses}>
            {Content}
        </div>
    );
}
