// Briques d'UI partagées pour donner à tous les formulaires de carrière (Embauche,
// Transition, Confirmation/Renouvellement d'essai) l'apparence d'un vrai document
// contractuel plutôt que d'une simple suite de champs de saisie.
//
// À placer par ex. dans src/components/carriere/ContractDocument.jsx et à importer
// depuis les modaux : import { Clause, ArticleHeader, DocumentFrame } from '@/components/carriere/ContractDocument';

// Plus d'utilitaires non-composants exportés ici pour respecter react-refresh/only-export-components

/** Ligne "clause" façon contrat papier : libellé ... ligne pointillée ... valeur */
export function Clause({ label, value, placeholder = '……………………', className = '', strike = false }) {
    const hasValue = value !== undefined && value !== null && value !== '';
    return (
        <div className={`flex items-baseline gap-2 py-1 text-[13px] ${className}`}>
            <span className="text-ink-500 dark:text-ink-400 whitespace-nowrap">{label}</span>
            <span className="flex-1 border-b border-dotted border-ink-300 dark:border-ink-700 translate-y-[-3px]" />
            <span className={
                strike
                    ? 'line-through text-ink-400 dark:text-ink-600 whitespace-nowrap text-right'
                    : hasValue
                        ? 'font-semibold text-ink-900 dark:text-ink-50 whitespace-nowrap text-right'
                        : 'font-normal italic text-ink-300 dark:text-ink-600 whitespace-nowrap text-right'
            }>
                {hasValue ? value : placeholder}
            </span>
        </div>
    );
}

/** Paire "avant -> après" pour les avenants/transitions, plus parlante qu'une simple Clause quand la valeur change */
export function ClauseDiff({ label, before, after, unit = '' }) {
    const changed = String(before ?? '') !== String(after ?? '') && after !== undefined && after !== '';
    return (
        <div className="flex items-baseline gap-2 py-1 text-[13px]">
            <span className="text-ink-500 dark:text-ink-400 whitespace-nowrap">{label}</span>
            <span className="flex-1 border-b border-dotted border-ink-300 dark:border-ink-700 translate-y-[-3px]" />
            {changed ? (
                <span className="whitespace-nowrap text-right">
                    <span className="line-through text-ink-400 dark:text-ink-600 mr-1.5">{before || '—'}{unit}</span>
                    <span className="font-bold text-brand-500">{after}{unit}</span>
                </span>
            ) : (
                <span className={`whitespace-nowrap text-right ${after ? 'font-semibold text-ink-900 dark:text-ink-50' : 'italic text-ink-300 dark:text-ink-600'}`}>
                    {after || before || '……………'}{unit}
                </span>
            )}
        </div>
    );
}

/** Titre d'article numéroté, réutilisé dans la colonne formulaire */
export function ArticleHeader({ n, icon, title, subtitle }) {
    return (
        <div className="flex items-start gap-3 pb-2 border-b border-ink-150 dark:border-ink-700">
            <div className="w-6 h-6 rounded-full bg-brand-500/10 text-brand-500 flex items-center justify-center text-[11px] font-bold flex-shrink-0 mt-0.5">
                {n}
            </div>
            <div>
                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-2">
                    {icon} {title}
                </h3>
                {subtitle && <p className="text-[11px] text-ink-400 mt-0.5">{subtitle}</p>}
            </div>
        </div>
    );
}

/** Cadre "papier" avec filigrane, utilisé pour tous les aperçus de document */
export function DocumentFrame({ children, watermark = 'Brouillon', className = '' }) {
    return (
        <div className={`relative bg-white dark:bg-ink-950 border border-ink-150 dark:border-ink-700 rounded-sm shadow-inner overflow-hidden ${className}`}>
            <div className="pointer-events-none select-none absolute inset-0 flex items-center justify-center overflow-hidden">
                <span className="text-[64px] font-black uppercase tracking-widest text-warning-500/[0.06] dark:text-warning-400/[0.05] rotate-[-25deg] whitespace-nowrap">
                    {watermark}
                </span>
            </div>
            <div className="relative p-6 font-serif text-ink-900 dark:text-ink-100">
                {children}
            </div>
        </div>
    );
}

export function DocumentHeader({ eyebrow, title, numero }) {
    return (
        <div className="text-center space-y-1 pb-4 border-b-2 border-double border-ink-300 dark:border-ink-700">
            {eyebrow && (
                <p className="text-[10px] tracking-[0.2em] uppercase text-ink-400 font-sans">{eyebrow}</p>
            )}
            <h2 className="text-lg font-bold uppercase tracking-wide">{title}</h2>
            {numero && <p className="text-xs font-sans text-brand-500 font-bold">N° {numero}</p>}
        </div>
    );
}

export function ArticleBlock({ n, title, children }) {
    return (
        <div className="space-y-1.5 pt-2">
            <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">Article {n} — {title}</p>
            {children}
        </div>
    );
}

export function DocumentFooter({ status = 'Brouillon (non actif)' }) {
    return (
        <div className="pt-6 mt-4 border-t border-ink-150 dark:border-ink-800 flex justify-between text-[11px] font-sans text-ink-400">
            <span>Fait en double exemplaire</span>
            <span className="font-bold text-warning-600">Statut : {status}</span>
        </div>
    );
}
