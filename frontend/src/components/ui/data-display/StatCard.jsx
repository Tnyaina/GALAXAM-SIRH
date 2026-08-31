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
 * @param {boolean} [borderTop=true] - Bordure supérieure colorée
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
    borderTop = true,
    className = ''
}) {
    const isClickable = typeof onClick === 'function';
    const isPositive = change !== undefined ? change >= 0 : true;

    // Configuration des styles de couleurs et d'accents
    const colorStyles = {
        brand: {
            text: 'text-[var(--color-brand-600)]',
            border: 'border-t-[var(--color-brand-500)]',
            activeBorder: 'border-[var(--color-brand-500)]',
            activeBg: 'bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/30',
            iconBg: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] border-[var(--color-brand-500)]/20'
        },
        success: {
            text: 'text-success-600 dark:text-success-400',
            label: 'text-success-700 dark:text-success-400',
            border: 'border-t-success-500',
            activeBorder: 'border-success-500',
            activeBg: 'bg-success-50/60 dark:bg-success-950/20',
            iconBg: 'bg-success-500/10 text-success-600 dark:text-success-400 border-success-500/20'
        },
        warning: {
            text: 'text-warning-600 dark:text-warning-400',
            label: 'text-warning-700 dark:text-warning-400',
            border: 'border-t-warning-500',
            activeBorder: 'border-warning-500',
            activeBg: 'bg-warning-50/60 dark:bg-warning-950/20',
            iconBg: 'bg-warning-500/10 text-warning-600 dark:text-warning-400 border-warning-500/20'
        },
        danger: {
            text: 'text-danger-600 dark:text-danger-400',
            label: 'text-danger-700 dark:text-danger-400',
            border: 'border-t-danger-500',
            activeBorder: 'border-danger-500',
            activeBg: 'bg-danger-50/60 dark:bg-danger-950/20',
            iconBg: 'bg-danger-500/10 text-danger-600 dark:text-danger-400 border-danger-500/20'
        },
        info: {
            text: 'text-info-600 dark:text-info-400',
            border: 'border-t-info-500',
            activeBorder: 'border-info-500',
            activeBg: 'bg-info-50/60 dark:bg-info-950/20',
            iconBg: 'bg-info-500/10 text-info-600 dark:text-info-400 border-info-500/20'
        },
        neutral: {
            text: 'text-[var(--color-fg)]',
            border: 'border-t-[var(--color-border-subtle)]',
            activeBorder: 'border-[var(--color-brand-500)]',
            activeBg: 'bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/30',
            iconBg: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
        }
    };

    const style = colorStyles[color] || colorStyles.brand;

    // Calcul des classes de conteneur
    const baseClasses = `
        bg-[var(--color-surface)] border text-left p-3 sm:p-3.5 transition-all
        ${borderTop ? (active ? `${style.activeBorder} border-t-2` : `${style.border} border-t-2 border-[var(--color-border-subtle)]`) : 'border-[var(--color-border-subtle)]'}
        ${active ? style.activeBg : ''}
        ${isClickable ? 'cursor-pointer hover:shadow-xs hover:border-[var(--color-brand-500)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)]' : ''}
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