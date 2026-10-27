import { ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import Breadcrumb from '@/components/ui/navigation/Breadcrumb';

export default function PageHeader({
    title,
    subtitle,
    icon: Icon,
    backLink,
    backLabel,
    breadcrumbs,
    tags,
    actions,
    className = ''
}) {
    return (
        <div className={`space-y-2.5 ${className}`}>
            {/* 1. Fil d'Ariane ou Lien Retour */}
            {breadcrumbs && breadcrumbs.length > 0 ? (
                <Breadcrumb items={breadcrumbs} />
            ) : backLink ? (
                <Link
                    to={backLink}
                    className="inline-flex items-center gap-1.5 text-xs text-[var(--color-brand-500)] hover:underline font-semibold transition-colors"
                >
                    <ArrowLeft size={14} />
                    {backLabel || 'Retour'}
                </Link>
            ) : null}

            {/* 2. En-tête de page : titre et actions, sans surface de carte. */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3.5">
                <div className="relative flex items-center gap-3.5 min-w-0">
                    {Icon && (
                        <div className="w-9 h-9 sm:w-10 sm:h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-[var(--color-on-color)] rounded-none flex items-center justify-center shadow-xs shrink-0">
                            <Icon size={19} strokeWidth={2.2} />
                        </div>
                    )}
                    <div className="min-w-0">
                        <h1 className="text-base sm:text-lg font-bold text-[var(--color-fg)] tracking-tight whitespace-normal break-words">
                            {title}
                        </h1>
                        {subtitle && (
                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal whitespace-normal">
                                {subtitle}
                            </p>
                        )}
                        {tags && (
                            <div className="mt-1.5 flex flex-wrap items-center gap-1.5">
                                {tags}
                            </div>
                        )}
                    </div>
                </div>

                {/* 3. Boutons d'Action & Outils */}
                {actions && (
                    <div className="relative z-10 flex max-w-full flex-wrap items-center gap-2 md:justify-end">
                        {actions}
                    </div>
                )}
            </div>
        </div>
    );
}
