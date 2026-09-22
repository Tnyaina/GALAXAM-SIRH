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

            {/* 2. Bloc En-tête Format Compact Galaxam */}
            <div className="relative overflow-visible bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-xs p-3.5 sm:p-4 flex flex-col md:flex-row md:items-center justify-between gap-3.5">
                {/* Décoration d'arrière-plan discrète */}
                <div className="absolute top-0 right-0 w-48 h-48 bg-[var(--color-brand-500)]/5 rounded-none blur-2xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />

                <div className="relative flex items-center gap-3.5 min-w-0">
                    {Icon && (
                        <div className="w-9 h-9 sm:w-10 sm:h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-xs shrink-0">
                            <Icon size={19} strokeWidth={2.2} />
                        </div>
                    )}
                    <div className="min-w-0">
                        <h1 className="text-base sm:text-lg font-bold text-[var(--color-fg)] tracking-tight truncate">
                            {title}
                        </h1>
                        {subtitle && (
                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal truncate">
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
                    <div className="relative z-10 flex flex-wrap items-center gap-2 shrink-0">
                        {actions}
                    </div>
                )}
            </div>
        </div>
    );
}
