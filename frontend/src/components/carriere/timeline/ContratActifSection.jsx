import { Briefcase, Clock, FileCheck, RefreshCw, UserMinus, Plus, Building, Landmark, CheckCircle } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate, fmtMoney } from '@/utils/carriereUtils';

/**
 * InfoRow — Ligne label/valeur sans séparateur systématique.
 * La hiérarchie est assurée par l'espacement (space-y-2.5) et la typographie,
 * non par des lignes horizontales répétées.
 */
const InfoRow = ({ label, children }) => (
    <div className="flex justify-between items-baseline gap-2 min-w-0">
        <span className="text-xs text-[var(--color-fg-muted)] shrink-0">{label}</span>
        <span className="font-bold text-xs text-[var(--color-fg)] text-right truncate">{children}</span>
    </div>
);

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
            <div className={`p-8 text-center bg-[var(--color-surface)] border rounded-none space-y-4 ${hasHistory ? 'border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/[0.02]' : 'border-dashed border-[var(--color-border)]'}`}>
                <div className={`w-14 h-14 rounded-none flex items-center justify-center mx-auto ${hasHistory ? 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)]' : 'bg-brand-500/10 text-brand-500'}`}>
                    <Briefcase size={28} />
                </div>
                <div className="max-w-md mx-auto space-y-1">
                    <h3 className="text-base font-bold text-[var(--color-fg)] flex items-center justify-center gap-2">
                        <span>{hasHistory ? 'Collaborateur Inactif (Aucun Contrat en cours)' : 'Aucun Contrat Actif'}</span>
                        {hasHistory && (
                            <Badge variant="warning" size="xs">Inactif</Badge>
                        )}
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
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
                        {hasHistory ? 'Contractualiser (Réembauche / Réintégration)' : "Initier l'Embauche (Créer Contrat)"}
                    </Button>
                )}
            </div>
        );
    }

    return (
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-5">
            {/* En-tête : Titre + Statut + Actions */}
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-4 border-b border-[var(--color-border-subtle)]">
                <div className="space-y-1">
                    <div className="flex items-center gap-2.5 flex-wrap">
                        <h3 className="text-base font-bold text-[var(--color-fg)]">
                            Contrat actif N° {contratActif.numeroContrat}
                        </h3>
                        <Badge variant="success" size="sm" className="font-bold">Actif</Badge>
                        {(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai) && !workflowsList.some(w => (w.isActif ?? (w.codeStatutWorkflow === 'ACTIF')) && w.codeTypeEvenement === 'CONFIRMATION_ESSAI' && (Number(w.idContratCible) === Number(contratActif.idContrat) || Number(w.idContratSource) === Number(contratActif.idContrat))) && (
                            <Badge variant="warning" size="xs" icon={<Clock size={11} />} className="font-semibold">
                                Période d'Essai (Fin le {fmtDate(contratActif.dateFinEssai || workflowCplActif?.dateFinEssai)})
                            </Badge>
                        )}
                    </div>
                    <p className="text-xs text-[var(--color-fg-muted)]">Relation contractuelle et juridique en cours d'exécution</p>
                </div>

                <div className="flex items-center gap-2 flex-wrap shrink-0">
                    {onOpenAttestation && (
                        <Button
                            onClick={() => onOpenAttestation(contratActif)}
                            variant="outline"
                            size="sm"
                            iconLeft={<FileCheck size={14} />}
                            className="text-xs font-semibold whitespace-nowrap"
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
                                    iconLeft={<RefreshCw size={14} />}
                                    className="text-xs font-semibold whitespace-nowrap"
                                >
                                    Mouvement / transition RH
                                </Button>
                                <Button
                                    onClick={onOpenRupture}
                                    variant="outline"
                                    size="sm"
                                    iconLeft={<UserMinus size={14} className="text-[var(--color-danger-500)]" />}
                                    className="text-xs font-semibold text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 whitespace-nowrap"
                                >
                                    Rupture / fin de contrat
                                </Button>
                            </>
                        )
                    )}
                </div>
            </div>

            {/* Grille Contrat + Affectation — séparation par surface, pas par bordures répétées */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-5 pt-1">
                {/* Bloc 1 : Termes du Contrat */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2.5">
                    <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                        <Building size={13} className="text-brand-500" /> Termes Contractuels
                    </h4>
                    <div className="space-y-2">
                        <InfoRow label="Type de contrat">{contratActif?.libelleTypeContrat || workflowCplActif?.libelleTypeContrat || '—'}</InfoRow>
                        <InfoRow label="Filiale employeuse">{contratActif?.libelleFiliale || workflowCplActif?.libelleFiliale || '—'}</InfoRow>
                        <InfoRow label="Date de début">{fmtDate(contratActif.dateDebut)}</InfoRow>
                        <InfoRow label="Date de fin">{contratActif.dateFin ? fmtDate(contratActif.dateFin) : '—'}</InfoRow>
                        <InfoRow label="Date de signature">{contratActif.dateSignature ? fmtDate(contratActif.dateSignature) : '—'}</InfoRow>
                        <InfoRow label="Effectif légal">{contratActif.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Hors effectif)'}</InfoRow>
                    </div>

                    {/* Période d'Essai — séparation subtile par surface */}
                    {etatEssai?.isEssaiActif && (
                        <div className={`py-2 px-3 mt-1 border space-y-2 ${etatEssai.isEssaiEchu
                            ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 text-[var(--color-danger-600)]'
                            : 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/20 text-[var(--color-warning-700)]'
                            }`}>
                            <div className="flex justify-between items-center font-medium text-xs">
                                <span className="flex items-center gap-1.5 font-bold">
                                    <Clock size={13} /> {
                                        etatEssai.isEssaiEchu
                                            ? "Période d'Essai Échue :"
                                            : (etatEssai.isDernierJour ? "Dernier Jour d'Essai :" : "Période d'Essai :")
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
                </div>

                {/* Bloc 2 : Affectation Courante */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 sm:p-5 space-y-2.5">
                    <h4 className="font-bold text-[var(--color-fg)] uppercase tracking-wider text-xs pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-1.5">
                        <Landmark size={13} className="text-brand-500" /> Affectation Courante
                    </h4>
                    <div className="space-y-2">
                        <InfoRow label="Site">{affectationActive?.libelleSite || workflowCplActif?.libelleSite || '—'}</InfoRow>
                        <InfoRow label="Département">{affectationActive?.libelleDepartement || workflowCplActif?.libelleDepartement || '—'}</InfoRow>
                        <InfoRow label="Fonction / Poste">{affectationActive?.libelleFonction || workflowCplActif?.libelleFonction || '—'}</InfoRow>
                        <InfoRow label="Catégorie pro">{affectationActive?.libelleCategoriePro || affectationActive?.libelleCategorieProfessionnelle || workflowCplActif?.libelleCategoriePro || '—'}</InfoRow>
                        <InfoRow label="Supérieur N+1">{affectationActive?.nomPrenomResponsable || affectationActive?.nomResponsableHierarchique || workflowCplActif?.nomPrenomResponsable || '—'}</InfoRow>
                    </div>
                    {/* Salaire mis en avant — donnée prioritaire */}
                    <div className="pt-2 border-t border-[var(--color-border-subtle)] mt-2 flex justify-between items-baseline">
                        <span className="text-[var(--color-fg-muted)] text-xs">Salaire de base :</span>
                        <span className="font-extrabold text-[var(--color-fg)] font-data text-sm sm:text-base">
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
