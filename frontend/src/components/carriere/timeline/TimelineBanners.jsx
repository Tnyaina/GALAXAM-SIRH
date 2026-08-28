import { Clock, Calendar, Eye, XCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate } from '@/utils/carriereUtils';

export default function TimelineBanners({
    brouillonWf,
    workflowPlanifieFutur,
    canWrite,
    onOpenEditModal,
    onSelectWorkflowRecap
}) {
    return (
        <>
            {/* 🟡 BANNIÈRE : BROUILLON D'EMBAUCHE / TRANSITION EN COURS */}
            {brouillonWf && Boolean(brouillonWf.isBrouillon ?? true) && (
                <div className="bg-warning-500/10 border border-warning-500/30 p-5 rounded-none flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-warning-500/20 text-warning-700 dark:text-warning-400 rounded-none flex items-center justify-center font-bold flex-shrink-0">
                            <Clock size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-bold text-ink-900 dark:text-white">
                                    {brouillonWf.isEmbauche || brouillonWf.codeTypeEvenement === 'EMBAUCHE_INITIALE'
                                        ? 'Brouillon d\'Embauche en attente de Validation'
                                        : brouillonWf.isConfirmationEssai || brouillonWf.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                            ? 'Confirmation d\'Essai en attente de Validation'
                                            : brouillonWf.isRenouvellementEssai || brouillonWf.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                                ? 'Renouvellement d\'Essai en attente de Validation'
                                                : `${brouillonWf.libelleTypeEvenement || 'Mouvement RH'} en attente de Validation`}
                                </h4>
                                <Badge variant="warning" size="sm">BROUILLON</Badge>
                            </div>
                            <p className="text-xs text-ink-500 dark:text-ink-300">
                                Contrat N° <span className="font-mono font-bold text-brand-500">{brouillonWf.numeroContratCible || brouillonWf.numeroContratSource || 'En cours'}</span> ({brouillonWf.libelleTypeContrat || 'Contrat'}) — Poste : <span className="font-semibold">{brouillonWf.libelleFonction || '-'}</span> à <span className="font-semibold">{brouillonWf.libelleSite || '-'}</span>
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 self-end sm:self-auto">
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
                            variant="primary"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                        >
                            Relecture & Validation
                        </Button>
                    </div>
                </div>
            )}

            {/* 🔵 BANNIÈRE : MOUVEMENT RH PLANIFIÉ À DATE ULTÉRIEURE */}
            {workflowPlanifieFutur && (
                <div className="bg-brand-500/10 border border-brand-500/30 p-5 rounded-none flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 mb-6">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-brand-500/20 text-brand-600 dark:text-brand-400 rounded-none flex items-center justify-center font-bold flex-shrink-0">
                            <Calendar size={20} />
                        </div>
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <h4 className="text-sm font-bold text-ink-900 dark:text-white">
                                    {workflowPlanifieFutur.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                        ? `Confirmation d'Essai Planifiée (Prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`
                                        : workflowPlanifieFutur.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                            ? `Renouvellement d'Essai Planifié (Prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`
                                            : `${workflowPlanifieFutur.libelleTypeEvenement || 'Mouvement RH'} Planifié (Prise d'effet le ${fmtDate(workflowPlanifieFutur.dateEffet)})`}
                                </h4>
                                <Badge variant="primary" size="sm">VALIDÉ — PLANIFIÉ</Badge>
                            </div>
                            <p className="text-xs text-ink-500 dark:text-ink-300">
                                {workflowPlanifieFutur.motif ? `Motif : ${workflowPlanifieFutur.motif} — ` : ''}
                                {workflowPlanifieFutur.libelleFonction ? (
                                    <>Poste cible : <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowPlanifieFutur.libelleFonction}</span> à <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowPlanifieFutur.libelleSite || '-'}</span></>
                                ) : (
                                    <span>L'événement a été validé et prendra effet automatiquement le {fmtDate(workflowPlanifieFutur.dateEffet)}.</span>
                                )}
                            </p>
                        </div>
                    </div>

                    <div className="flex flex-wrap items-center gap-2 self-end sm:self-auto">
                        {canWrite && (
                            <Button
                                onClick={() => onSelectWorkflowRecap(workflowPlanifieFutur, { startInCancelMode: true })}
                                variant="danger"
                                size="sm"
                                iconLeft={<XCircle size={15} />}
                            >
                                Annuler la Planification
                            </Button>
                        )}
                        <Button
                            onClick={() => onSelectWorkflowRecap(workflowPlanifieFutur)}
                            variant="outline"
                            size="sm"
                            iconLeft={<Eye size={16} />}
                        >
                            Consulter l'Événement
                        </Button>
                    </div>
                </div>
            )}
        </>
    );
}
