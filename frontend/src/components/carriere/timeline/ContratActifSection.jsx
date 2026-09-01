import { Briefcase, Clock, FileCheck, RefreshCw, UserMinus, Plus, Building, Landmark, CheckCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate, fmtMoney } from '@/utils/carriereUtils';

export default function ContratActifSection({
    contratActif,
    affectationActive,
    workflowCplActif,
    workflowsList = [],
    contratsList = [],
    brouillonWf,
    workflowPlanifieFutur,
    etatEssai,
    _estEssaiRenouveleFlag,
    canWrite,
    onOpenCreateEmbauche,
    onOpenTransition,
    onOpenRupture,
    onOpenConfirmation,
    onOpenRenouvellement,
    onOpenAttestation,
}) {
    if (!contratActif) {
        if (brouillonWf) return null;

        const hasHistory = Array.isArray(contratsList) && contratsList.length > 0;

        return (
            /* ⚪ EMPTY STATE : PAS DE CONTRAT ACTIF */
            <div className={`p-8 text-center bg-white dark:bg-ink-900 border rounded-none space-y-4 ${hasHistory ? 'border-amber-200 dark:border-amber-800/60 bg-amber-500/[0.02]' : 'border-dashed border-ink-200 dark:border-ink-700'}`}>
                <div className={`w-14 h-14 rounded-none flex items-center justify-center mx-auto ${hasHistory ? 'bg-amber-500/10 text-amber-600 dark:text-amber-400' : 'bg-brand-500/10 text-brand-500'}`}>
                    <Briefcase size={28} />
                </div>
                <div className="max-w-md mx-auto space-y-1">
                    <h3 className="text-base font-bold text-ink-900 dark:text-white flex items-center justify-center gap-2">
                        <span>{hasHistory ? 'Collaborateur Inactif (Aucun Contrat en cours)' : 'Aucun Contrat Actif'}</span>
                        {hasHistory && (
                            <span className="text-[10px] uppercase tracking-wider font-semibold px-2 py-0.5 bg-amber-500/10 text-amber-700 dark:text-amber-400 border border-amber-500/20">
                                Inactif
                            </span>
                        )}
                    </h3>
                    <p className="text-xs text-ink-400">
                        {hasHistory
                            ? "Cet employé dispose d'un historique de contrats passés. Vous pouvez initier une nouvelle réembauche, une réintégration avec reprise d'ancienneté ou un contrat de consultant."
                            : "Cet employé ne dispose pas encore de contrat de travail enregistré. Vous pouvez initier son embauche initiale."}
                    </p>
                </div>
                {canWrite && (
                    <Button
                        onClick={onOpenCreateEmbauche}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        {hasHistory ? "Contractualiser (Réembauche / Réintégration)" : "Initier l'Embauche (Créer Contrat)"}
                    </Button>
                )}
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-ink-900 border border-slate-200/90 dark:border-ink-700 shadow-xs p-5 sm:p-6 space-y-5">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 pb-4 border-b border-slate-100 dark:border-ink-800">
                <div>
                    <div className="flex items-center gap-2.5">
                        <h3 className="text-base font-bold text-slate-900 dark:text-white">
                            Contrat actif N° {contratActif.numeroContrat}
                        </h3>
                        <Badge variant="success" size="sm" className="font-bold">Actif</Badge>
                        {(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai) && !workflowsList.some(w => (w.isActif ?? (w.codeStatutWorkflow === 'ACTIF')) && w.codeTypeEvenement === 'CONFIRMATION_ESSAI' && (Number(w.idContratCible) === Number(contratActif.idContrat) || Number(w.idContratSource) === Number(contratActif.idContrat))) && (
                            <Badge variant="warning" size="sm" icon={<Clock size={12} />}>
                                Période d'Essai (Fin le {fmtDate(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai)})
                            </Badge>
                        )}
                    </div>
                    <p className="text-xs text-slate-400 mt-0.5">Relation contractuelle et juridique en cours d'exécution</p>
                </div>

                <div className="flex flex-wrap items-center gap-2.5">
                    {onOpenAttestation && (
                        <Button
                            onClick={() => onOpenAttestation(contratActif)}
                            variant="outline"
                            size="sm"
                            iconLeft={<FileCheck size={15} />}
                            className="text-xs font-semibold"
                        >
                            Attestation de travail
                        </Button>
                    )}
                    {canWrite && !brouillonWf && (
                        workflowPlanifieFutur ? (
                            <Badge variant="primary" size="sm" icon={<Clock size={12} />}>
                                {workflowPlanifieFutur.codeTypeEvenement === 'CONFIRMATION_ESSAI'
                                    ? `Confirmation d'Essai Planifiée au ${fmtDate(workflowPlanifieFutur.dateEffet)}`
                                    : workflowPlanifieFutur.codeTypeEvenement === 'RENOUVELLEMENT_ESSAI'
                                        ? `Renouvellement d'Essai Planifié au ${fmtDate(workflowPlanifieFutur.dateEffet)}`
                                        : `Événement RH (${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement}) Planifié au ${fmtDate(workflowPlanifieFutur.dateEffet)}`}
                            </Badge>
                        ) : (
                            <>
                                <Button
                                    onClick={onOpenTransition}
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<RefreshCw size={15} />}
                                    className="text-xs font-semibold"
                                >
                                    Mouvement / transition RH
                                </Button>
                                <Button
                                    onClick={onOpenRupture}
                                    variant="outline"
                                    size="sm"
                                    iconLeft={<UserMinus size={15} className="text-rose-500" />}
                                    className="text-xs font-semibold text-rose-600 dark:text-rose-400 hover:bg-rose-500/10 border-rose-200 dark:border-rose-900/40"
                                >
                                    Rupture / fin de contrat
                                </Button>
                            </>
                        )
                    )}
                </div>
            </div>

            {/* Grille Contrat + Affectation en 2 blocs nettement séparés */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 pt-1">
                {/* Bloc 1 : Termes du Contrat */}
                <div className="bg-slate-50/60 dark:bg-ink-950/40 border border-slate-200/80 dark:border-ink-800 p-4 sm:p-5 rounded-none space-y-2">
                    <h4 className="font-bold text-slate-700 dark:text-slate-200 uppercase tracking-wider text-xs pb-2 border-b border-slate-200/80 dark:border-ink-800 flex items-center gap-1.5">
                        <Building size={13} className="text-brand-500" /> Termes Contractuels
                    </h4>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Type de contrat :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{contratActif?.libelleTypeContrat || workflowCplActif?.libelleTypeContrat || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Filiale employeuse :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{contratActif?.libelleFiliale || workflowCplActif?.libelleFiliale || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Date de début :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{fmtDate(contratActif.dateDebut)}</span>
                    </div>
                    {etatEssai?.isEssaiActif && (
                        <div className={`py-2 px-3 my-2 border rounded-none space-y-2 ${etatEssai.isEssaiEchu
                            ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300'
                            : 'bg-warning-500/10 border-warning-500/20 text-warning-800 dark:text-warning-300'
                            }`}>
                            <div className="flex justify-between items-center font-medium text-xs">
                                <span className="flex items-center gap-1.5 font-bold">
                                    <Clock size={13} /> {
                                        etatEssai.isEssaiEchu
                                            ? 'Période d\'Essai Échue :'
                                            : (etatEssai.isDernierJour ? 'Dernier Jour d\'Essai :' : 'Période d\'Essai :')
                                    }
                                </span>
                                <span className="font-bold">
                                    {etatEssai.dureeEssaiMois} Mois
                                    {etatEssai.isEssaiEchu
                                        ? ` (Finie le ${fmtDate(etatEssai.dateFinEssaiStr)})`
                                        : (etatEssai.isDernierJour ? ' (Échéance ce soir à 23h59)' : ` (Fin le ${fmtDate(etatEssai.dateFinEssaiStr)})`)
                                    }
                                </span>
                            </div>
                            {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                                <div className="pt-2 border-t border-current/20 flex flex-wrap items-center gap-2">
                                    {etatEssai?.canConfirm === true && (
                                        <Button
                                            onClick={onOpenConfirmation}
                                            variant="success"
                                            size="xs"
                                            iconLeft={<CheckCircle size={13} />}
                                        >
                                            Confirmer l'Essai
                                        </Button>
                                    )}
                                    {etatEssai?.canRenew === true ? (
                                        <Button
                                            onClick={onOpenRenouvellement}
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<RefreshCw size={13} />}
                                        >
                                            Renouveller l'Essai
                                        </Button>
                                    ) : null}
                                </div>
                            )}
                        </div>
                    )}
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Date de fin :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{contratActif.dateFin ? fmtDate(contratActif.dateFin) : '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Date signature :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{contratActif.dateSignature ? fmtDate(contratActif.dateSignature) : '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs">
                        <span className="text-slate-500 dark:text-ink-400">Effectif légal :</span>
                        <span className="font-bold text-slate-900 dark:text-white">
                            {contratActif.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Hors effectif)'}
                        </span>
                    </div>
                </div>

                {/* Bloc 2 : Affectation Courante */}
                <div className="bg-slate-50/60 dark:bg-ink-950/40 border border-slate-200/80 dark:border-ink-800 p-4 sm:p-5 rounded-none space-y-2">
                    <h4 className="font-bold text-slate-700 dark:text-slate-200 uppercase tracking-wider text-xs pb-2 border-b border-slate-200/80 dark:border-ink-800 flex items-center gap-1.5">
                        <Landmark size={13} className="text-brand-500" /> Affectation Courante
                    </h4>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Site :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{affectationActive?.libelleSite || workflowCplActif?.libelleSite || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Département :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{affectationActive?.libelleDepartement || workflowCplActif?.libelleDepartement || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Fonction / poste :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{affectationActive?.libelleFonction || workflowCplActif?.libelleFonction || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Catégorie pro :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{affectationActive?.libelleCategoriePro || affectationActive?.libelleCategorieProfessionnelle || workflowCplActif?.libelleCategoriePro || '—'}</span>
                    </div>
                    <div className="py-1.5 flex justify-between items-baseline text-xs border-b border-slate-200/50 dark:border-ink-800/50">
                        <span className="text-slate-500 dark:text-ink-400">Supérieur N+1 :</span>
                        <span className="font-bold text-slate-900 dark:text-white">{affectationActive?.nomPrenomResponsable || affectationActive?.nomResponsableHierarchique || workflowCplActif?.nomPrenomResponsable || '—'}</span>
                    </div>
                    <div className="py-2.5 flex justify-between items-baseline border-t border-slate-200 dark:border-ink-800 mt-2 pt-3">
                        <span className="text-slate-500 dark:text-ink-400 text-xs">Salaire de base :</span>
                        <span className="font-extrabold text-slate-900 dark:text-white font-data text-sm sm:text-base">
                            {fmtMoney(affectationActive?.salaireBase)} {
                                (affectationActive?.libelleModeCalculPaie || workflowCplActif?.libelleModeCalculPaie || '').toLowerCase().includes('horaire')
                                    ? '/ h'
                                    : (affectationActive?.libelleModeCalculPaie || workflowCplActif?.libelleModeCalculPaie || '').toLowerCase().includes('journal')
                                        ? '/ j'
                                        : '/ mois'
                            }
                        </span>
                    </div>
                </div>
            </div>
        </div>
    );
}
