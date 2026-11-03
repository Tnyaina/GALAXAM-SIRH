import { Link } from 'react-router-dom';
import { ArrowRight } from 'lucide-react';

/** Repère de travail commun aux parcours Pointage et Paie. */
export default function WorkflowFocus({ title, description, links = [] }) {
    return (
        <section aria-label="Repère de l'étape" className="border-l-4 border-l-[var(--color-brand-600)] border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-4 py-3">
            <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-3">
                <div className="min-w-0">
                    <h2 className="ui-section-title">{title}</h2>
                    <p className="ui-supporting-text">{description}</p>
                </div>
                {links.length > 0 && (
                    <nav aria-label="Tâches liées à cette étape" className="flex flex-wrap gap-2 shrink-0">
                        {links.map(link => (
                            <Link key={link.href} to={link.href} className="inline-flex min-h-10 items-center gap-1.5 border border-[var(--color-border)] px-3 py-2 text-xs font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-200)] hover:bg-[var(--color-bg-subtle)] focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]">
                                {link.label}<ArrowRight size={14} aria-hidden="true" />
                            </Link>
                        ))}
                    </nav>
                )}
            </div>
        </section>
    );
}
