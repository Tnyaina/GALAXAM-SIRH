import { ArrowRight, CheckCircle2, AlertTriangle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

/** Action prioritaire d'un écran de travail, dérivée de son état métier. */
export default function NextAction({ title, description, actionLabel, onAction, tone = 'brand' }) {
    const Icon = tone === 'success' ? CheckCircle2 : tone === 'warning' ? AlertTriangle : ArrowRight;
    return (
        <section aria-label="Prochaine action" className={`ui-next-action ui-next-action--${tone}`}>
            <div className="flex min-w-0 items-start gap-3">
                <span className="ui-next-action-icon"><Icon size={19} aria-hidden="true" /></span>
                <div className="min-w-0">
                    <p className="ui-next-action-kicker">{tone === 'warning' ? 'À traiter' : 'Étape suivante'}</p>
                    <h2 className="ui-section-title">{title}</h2>
                    {description && <p className="ui-supporting-text">{description}</p>}
                </div>
            </div>
            {actionLabel && onAction && <Button type="button" variant="primary" size="sm" onClick={onAction} iconRight={<ArrowRight size={15} />}>{actionLabel}</Button>}
        </section>
    );
}
