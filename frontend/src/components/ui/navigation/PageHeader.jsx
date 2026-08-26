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
        <div className={`space-y-4 ${className}`}>
            {/* 1. Fil d'Ariane ou Lien Retour */}
            {breadcrumbs && breadcrumbs.length > 0 ? (
                <Breadcrumb items={breadcrumbs} />
            ) : backLink ? (
                <Link
                    to={backLink}
                    className="inline-flex items-center gap-2 text-sm text-[var(--color-brand-500)] hover:underline font-semibold transition-colors"
                >
                    <ArrowLeft size={16} />
                    {backLabel || 'Retour'}
                </Link>
            ) : null}

            {/* 2. Bloc En-tête Format Standard Galaxam */}
            <div className="relative overflow-hidden bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-sm p-6 sm:p-8 flex flex-col md:flex-row md:items-center justify-between gap-6">
                {/* Décoration d'arrière-plan */}
                <div className="absolute top-0 right-0 w-64 h-64 bg-[var(--color-brand-500)]/5 rounded-none blur-3xl -translate-y-1/2 translate-x-1/3 pointer-events-none" />

                <div className="relative flex items-center gap-5">
                    {Icon && (
                        <div className="w-14 h-14 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white rounded-none flex items-center justify-center shadow-lg shadow-[var(--color-brand-500)]/20 shrink-0">
                            <Icon size={26} strokeWidth={2.5} />
                        </div>
                    )}
                    <div>
                        <h1 className="text-2xl sm:text-3xl font-extrabold text-[var(--color-fg)] tracking-tight">
                            {title}
                        </h1>
                        {subtitle && (
                            <p className="text-sm text-[var(--color-fg-muted)] mt-1 font-medium">
                                {subtitle}
                            </p>
                        )}
                        {tags && (
                            <div className="mt-2.5 flex flex-wrap items-center gap-2">
                                {tags}
                            </div>
                        )}
                    </div>
                </div>

                {/* 3. Boutons d'Action & Outils */}
                {actions && (
                    <div className="relative flex flex-wrap items-center gap-2 shrink-0">
                        {actions}
                    </div>
                )}
            </div>
        </div>
    );
}
