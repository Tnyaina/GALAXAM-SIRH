import { Clock, Calendar, Eye, XCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate } from '@/utils/dateUtils';
import { isRuptureEvent } from '@/features/employees/utils/carriereWorkflowUtils';

export default function TimelineBanners({
    brouillonWf,
    workflowPlanifieFutur,
    canWrite,
    onOpenEditModal,
    onSelectWorkflowRecap,
    onOpenContrat,
    contratBrouillon,
}) {
    const joursAvantPriseEffet = workflowPlanifieFutur?.dateEffet
        ? Math.max(0, Math.ceil((new Date(workflowPlanifieFutur.dateEffet).setHours(0, 0, 0, 0) - new Date().setHours(0, 0, 0, 0)) / (1000 * 60 * 60 * 24)))
        : null;

    const delaiTexte = joursAvantPriseEffet !== null
        ? (joursAvantPriseEffet === 0 ? "aujourd'hui" : joursAvantPriseEffet === 1 ? 'demain' : `dans ${joursAvantPriseEffet} jours`)
        : null;
    const brouillonEstRupture = isRuptureEvent(brouillonWf);

    return (
        <>
            {/* BANNIERE : BROUILLON D'EMBAUCHE / TRANSITION EN COURS */}
            {brouillonWf && Boolean(brouillonWf.isBrouillon ?? true) && (
                <div className="bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 p-4 sm:p-5 rounded-none flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-[var(--color-warning-500)]/20 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] rounded-none flex items-center justify-center font-bold shrink-0">
                            <Clock size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2 flex-wrap">
                                <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                    {brouillonWf.isEmbauche || brouillonWf.codeTypeEvenement === 'EMBAUCHE_INITIALE'
                                        ? 'Brouillon d\'embauche en attente de validation'
                                        : brouillonWf.isConfirmationEssai || brouillonWf.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                            ? 'Confirmation d\'essai en attente de validation'
                                            : brouillonWf.isRenouvellementEssai || brouillonWf.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                                ? 'Renouvellement d\'essai en attente de validation'
                                                : `${brouillonWf.libelleTypeEvenement || 'Mouvement RH'} en attente de validation`}
                                </h4>
                                <Badge variant="warning" size="sm">Brouillon</Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Contrat N° <span className="font-mono font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">{brouillonWf.numeroContratCible || brouillonWf.numeroContratSource || 'En cours'}</span> ({brouillonWf.libelleTypeContrat || 'Contrat'}) · Poste : <span className="font-semibold text-[var(--color-fg)]">{brouillonWf.libelleFonction || 'Non renseigné'}</span> à <span className="font-semibold text-[var(--color-fg)]">{brouillonWf.libelleSite || 'Non renseigné'}</span>
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 self-end sm:self-auto shrink-0">
                        {!brouillonEstRupture && contratBrouillon && onOpenContrat && <Button variant="primary" size="sm" onClick={() => onOpenContrat(contratBrouillon)}>Préparer le contrat</Button>}
                        {canWrite && (
                            <>
                                <Button
                                    onClick={() => onSelectWorkflowRecap(brouillonWf, { startInCancelMode: true })}
                                    variant="danger"
                                    size="sm"
                                    iconLeft={<XCircle size={15} />}
                                >
                                    Annuler
                                </Button>
                                <Button
                                    onClick={() => onOpenEditModal(brouillonWf)}
                                    variant="outline"
                                    size="sm"
                                >
                                    Corriger
                                </Button>
                            </>
                        )}
                        <Button
                            onClick={() => onSelectWorkflowRecap(brouillonWf)}
                            variant={!brouillonEstRupture && contratBrouillon ? "outline" : "primary"}
                            size="sm"
                            iconLeft={<Eye size={16} />}
                        >
                            Relecture et validation
                        </Button>
                    </div>
                </div>
            )}

            {/* BANNIERE : MOUVEMENT RH PLANIFIE A DATE ULTERIEURE */}
            {workflowPlanifieFutur && (
                <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 p-4 sm:p-5 rounded-none flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-4">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-[var(--color-brand-500)]/20 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] rounded-none flex items-center justify-center font-bold shrink-0">
                            <Calendar size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2 flex-wrap">
                                <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                    {workflowPlanifieFutur.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                        ? `Confirmation d'essai planifiée (prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`
                                        : workflowPlanifieFutur.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                            ? `Renouvellement d'essai planifié (prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`
                                            : `${workflowPlanifieFutur.libelleTypeEvenement || 'Mouvement RH'} planifié (prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`}
                                </h4>
                                <Badge variant="primary" size="sm">
                                    Planifié {delaiTexte ? `· ${delaiTexte}` : ''}
                                </Badge>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                {workflowPlanifieFutur.motif ? `Motif : ${workflowPlanifieFutur.motif} · ` : ''}
                                {workflowPlanifieFutur.libelleFonction ? (
                                    <>Poste cible : <span className="font-semibold text-[var(--color-fg)]">{workflowPlanifieFutur.libelleFonction}</span> à <span className="font-semibold text-[var(--color-fg)]">{workflowPlanifieFutur.libelleSite || 'Non renseigné'}</span></>
                                ) : (
                                    <span>L'événement a été validé et prendra effet automatiquement le {fmtDate(workflowPlanifieFutur.dateEffet)} ({delaiTexte}).</span>
                                )}
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 self-end sm:self-auto shrink-0">
                        {canWrite && (
                            <Button
                                onClick={() => onSelectWorkflowRecap(workflowPlanifieFutur, { startInCancelMode: true })}
                                variant="danger"
                                size="sm"
                                iconLeft={<XCircle size={15} />}
                            >
                                Annuler la planification
                            </Button>
                        )}
                        <Button
                            onClick={() => onSelectWorkflowRecap(workflowPlanifieFutur)}
                            variant="outline"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                        >
                            Consulter l'événement
                        </Button>
                    </div>
                </div>
            )}
        </>
    );
}
