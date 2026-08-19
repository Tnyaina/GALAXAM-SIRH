import { Briefcase, Clock, FileCheck, RefreshCw, UserMinus, Plus, Building, Landmark, CheckCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate, fmtMoney } from '@/utils/carriereUtils';

export default function ContratActifSection({
    contratActif,
    affectationActive,
    workflowCplActif,
    workflowsList,
    brouillonWf,
    etatEssai,
    estEssaiRenouveleFlag,
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
        
        return (
            /* ⚪ EMPTY STATE : PAS DE CONTRAT ACTIF */
            <div className="p-10 text-center bg-white dark:bg-ink-900 border border-dashed border-ink-200 dark:border-ink-700 rounded-none space-y-4">
                <div className="w-14 h-14 bg-brand-500/10 text-brand-500 rounded-none flex items-center justify-center mx-auto">
                    <Briefcase size={28} />
                </div>
                <div className="max-w-md mx-auto space-y-1">
                    <h3 className="text-base font-bold text-ink-900 dark:text-white">Aucun Contrat Actif</h3>
                    <p className="text-xs text-ink-400">
                        Cet employé ne dispose pas encore de contrat de travail enregistré. Vous pouvez initier son embauche.
                    </p>
                </div>
                {canWrite && (
                    <Button
                        onClick={onOpenCreateEmbauche}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        Initier l'Embauche (Créer Contrat)
                    </Button>
                )}
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-none shadow-sm p-6 space-y-6">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-4 border-b border-ink-150 dark:border-ink-700">
                <div className="flex items-center gap-3">
                    <div className="w-10 h-10 bg-success-500/10 text-success-600 rounded-none flex items-center justify-center font-bold">
                        <Briefcase size={20} />
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <h3 className="text-base font-bold text-ink-900 dark:text-white">
                                Contrat Actif N° {contratActif.numeroContrat}
                            </h3>
                            <Badge variant="success" size="sm">ACTIF</Badge>
                            {(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai) && !workflowsList.some(w => w.codeStatutWorkflow === 'VALIDE' && w.codeTypeEvenement === 'CONFIRMATION_ESSAI' && (Number(w.idContratCible) === Number(contratActif.idContrat) || Number(w.idContratSource) === Number(contratActif.idContrat))) && (
                                <Badge variant="warning" size="sm" icon={<Clock size={12} />}>
                                    Période d'Essai (Fin le {fmtDate(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai)})
                                </Badge>
                            )}
                        </div>
                        <p className="text-xs text-ink-400">Relation contractuelle et juridique en cours d'exécution</p>
                    </div>
                </div>

                <div className="flex flex-wrap items-center gap-2">
                    {onOpenAttestation && (
                        <Button
                            onClick={() => onOpenAttestation(contratActif)}
                            variant="outline"
                            size="sm"
                            iconLeft={<FileCheck size={16} />}
                        >
                            Attestation de Travail
                        </Button>
                    )}
                    {canWrite && !brouillonWf && (
                        <>
                            <Button
                                onClick={onOpenTransition}
                                variant="primary"
                                size="sm"
                                iconLeft={<RefreshCw size={16} />}
                            >
                                Mouvement / Transition RH
                            </Button>
                            <Button
                                onClick={onOpenRupture}
                                variant="danger"
                                size="sm"
                                iconLeft={<UserMinus size={16} />}
                            >
                                Rupture / Fin de Contrat
                            </Button>
                        </>
                    )}
                </div>
            </div>

            {/* Grille Contrat + Affectation */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Bloc 1 : Termes du Contrat */}
                <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-800 rounded-none space-y-2 text-xs">
                    <h4 className="font-bold text-brand-500 uppercase tracking-wider text-[10px] pb-1 border-b border-ink-150 dark:border-ink-800 flex items-center gap-1.5">
                        <Building size={12} /> Termes Contractuels
                    </h4>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Type de Contrat :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleTypeContrat || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Filiale Employeuse :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleFiliale || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Date de Début :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateDebut)}</span>
                    </div>
                    {etatEssai?.isEssaiActif && (
                        <div className={`py-2 px-2.5 my-1.5 border rounded-none space-y-2 ${etatEssai.isEssaiEchu
                                ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300'
                                : 'bg-warning-500/10 border-warning-500/20 text-warning-800 dark:text-warning-300'
                            }`}>
                            <div className="flex justify-between items-center font-medium">
                                <span className="flex items-center gap-1.5 font-bold">
                                    <Clock size={13} /> {
                                        etatEssai.isEssaiEchu
                                            ? 'Période d\'Essai Échue (Action requise) :'
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
                            {canWrite && (!brouillonWf || !['CONFIRMATION_ESSAI', 'RENOUVELLEMENT_ESSAI'].includes(brouillonWf?.codeTypeEvenement)) && (
                                <div className="pt-1.5 border-t border-current/20 flex flex-wrap items-center gap-2">
                                    <Button
                                        onClick={onOpenConfirmation}
                                        variant="success"
                                        size="xs"
                                        iconLeft={<CheckCircle size={13} />}
                                    >
                                        Confirmer l'Essai
                                    </Button>
                                    {!estEssaiRenouveleFlag ? (
                                        <Button
                                            onClick={onOpenRenouvellement}
                                            variant="outline"
                                            size="xs"
                                            iconLeft={<RefreshCw size={13} />}
                                        >
                                            Renouveler l'Essai
                                        </Button>
                                    ) : (
                                        <Badge variant="warning" size="sm">
                                            2ème Période (Renouvelée — Max légal atteint)
                                        </Badge>
                                    )}
                                </div>
                            )}
                        </div>
                    )}
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Date de Fin :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateFin)}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Date Signature :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{fmtDate(contratActif.dateSignature)}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Effectif Légal :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">
                            {contratActif.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Hors effectif)'}
                        </span>
                    </div>
                </div>

                {/* Bloc 2 : Affectation & Paie */}
                <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-800 rounded-none space-y-2 text-xs">
                    <h4 className="font-bold text-brand-500 uppercase tracking-wider text-[10px] pb-1 border-b border-ink-150 dark:border-ink-800 flex items-center gap-1.5">
                        <Landmark size={12} /> Affectation Courante
                    </h4>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Site :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleSite || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Département :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleDepartement || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Service :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleService || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Fonction / Poste :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleFonction || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Catégorie Pro :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.libelleCategoriePro || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between">
                        <span className="text-ink-400">Supérieur N+1 :</span>
                        <span className="font-semibold text-ink-800 dark:text-ink-100">{workflowCplActif?.nomPrenomResponsable || '-'}</span>
                    </div>
                    <div className="py-1 flex justify-between border-t border-ink-150 dark:border-ink-800 mt-1 pt-2">
                        <span className="text-ink-400">Salaire de Base :</span>
                        <span className="font-bold text-brand-500 font-mono text-sm">{fmtMoney(affectationActive?.salaireBase)}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}
