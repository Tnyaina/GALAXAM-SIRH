import { ArrowLeft, ArrowRight } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '@/components/ui/forms/Button';

/**
 * PayrollNavigationFooter — Barre de navigation basse standardisée pour le workflow de paie (Sprint 2B).
 *
 * Apparaît en bas de chaque page d'étape pour guider la DAF de manière linéaire (wizard)
 * sans casser le statut de pages indépendantes.
 *
 * @prop {Object}   [prevStep]       - { label: string, destination: string }
 * @prop {Object}   [nextStep]       - { label: string, destination: string, disabled?: boolean, disabledReason?: string }
 * @prop {Object}   [primaryAction]  - { label: string, onClick: Function, loading?: boolean, disabled?: boolean, icon?: ReactNode, variant?: string }
 * @prop {ReactNode} [centerContent] - Contenu libre au centre (ex: résumé, avertissements)
 */
export default function PayrollNavigationFooter({
    prevStep,
    nextStep,
    primaryAction,
    centerContent,
    className = '',
}) {
    const navigate = useNavigate();

    return (
        <div className={`sticky bottom-0 z-20 mt-auto shrink-0 bg-[var(--color-bg)]/95 backdrop-blur-md border-t border-[var(--color-border-subtle)] px-4 py-3 shadow-lg -mx-3.5 sm:-mx-4.5 -mb-3.5 sm:-mb-4.5 ${className}`}>
            <div className="max-w-7xl mx-auto flex items-center justify-between gap-4 flex-wrap">
                {/* Bouton retour / étape précédente */}
                <div>
                    {prevStep ? (
                        <Button
                            variant="outline"
                            size="sm"
                            iconLeft={<ArrowLeft size={14} />}
                            onClick={() => navigate(prevStep.destination)}
                        >
                            {prevStep.label ? `Précédent : ${prevStep.label}` : 'Précédent'}
                        </Button>
                    ) : (
                        <div />
                    )}
                </div>

                {/* Contenu central optionnel */}
                {centerContent && (
                    <div className="text-xs text-[var(--color-fg-muted)] text-center hidden md:block">
                        {centerContent}
                    </div>
                )}

                {/* Actions principales droite */}
                <div className="flex items-center gap-2.5">
                    {/* Action principale métier si fournie (ex: Lancer le calcul, Valider les bulletins) */}
                    {primaryAction && (
                        <Button
                            variant={primaryAction.variant || 'primary'}
                            size="sm"
                            loading={primaryAction.loading}
                            disabled={primaryAction.disabled}
                            iconLeft={primaryAction.icon}
                            onClick={primaryAction.onClick}
                            title={primaryAction.disabledReason}
                        >
                            {primaryAction.label}
                        </Button>
                    )}

                    {/* Bouton étape suivante */}
                    {nextStep && (
                        <Button
                            variant={primaryAction ? 'secondary' : 'primary'}
                            size="sm"
                            iconRight={<ArrowRight size={14} />}
                            disabled={nextStep.disabled}
                            title={nextStep.disabledReason || nextStep.label}
                            onClick={() => !nextStep.disabled && navigate(nextStep.destination)}
                        >
                            {nextStep.label ? `Continuer vers ${nextStep.label}` : 'Suivant'}
                        </Button>
                    )}
                </div>
            </div>
        </div>
    );
}
