import { Clock, Briefcase, RefreshCw, Banknote } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import HasPermission from '@/features/auth/components/HasPermission';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { isDateInFuture, isDateInPast } from '@/utils/dateUtils';

export default function CarriereHeaderCard({
    employe,
    contratActif,
    affectationActive,
    brouillonWf,
    etatEssai,
    workflowPlanifieFutur,
    canWrite,
    onRefresh,
    onOpenConfirmation,
    onOpenRenouvellement,
    onOpenTransition,
    onOpenRupture,
}) {
    const rawMode = affectationActive?.libelleModeCalculPaie || affectationActive?.libelleModePaie || 'Mensuel fixe';
    const isHoraire = rawMode.toLowerCase().includes('horaire');
    const isJournalier = rawMode.toLowerCase().includes('journal');
    const periodiciteLabel = isHoraire ? 'Horaire' : isJournalier ? 'Journalier' : 'Mensuel';
    const uniteLabel = isHoraire ? '/ h' : isJournalier ? '/ j' : '/ mois';
    const salaireValeur = affectationActive?.salaireBase !== null && affectationActive?.salaireBase !== undefined
        ? `${fmtMoney(affectationActive.salaireBase)} ${uniteLabel}`
        : null;

    const isContratFutur = contratActif?.dateDebut && isDateInFuture(contratActif.dateDebut);
    const isContratEchu = contratActif?.dateFin && isDateInPast(contratActif.dateFin);

    return (
        <div className="space-y-4">
            {/* 1. Situation Contractuelle & Rémunération */}
            <Card padding bordered className="space-y-4">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                    <div>
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                            <Briefcase size={15} className="text-[var(--color-brand-500)] shrink-0" />
                            Situation Contractuelle &amp; Rémunération
                        </h3>
                        <p className="text-xs text-[var(--color-fg-subtle)] mt-0.5">
                            Dossier contractuel, affectation active et conditions de rémunération
                        </p>
                    </div>
                    {onRefresh && (
                        <Button
                            type="button"
                            variant="outline"
                            size="xs"
                            iconLeft={<RefreshCw size={12} />}
                            onClick={onRefresh}
                        >
                            Actualiser
                        </Button>
                    )}
                </div>

                {/* Métadonnées horizontales réparties */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 pt-1">
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1.5">Statut actuel</p>
                        {contratActif ? (
                            isContratFutur ? (
                                <Badge variant="info" size="sm" className="font-bold" title={`Prend effet le ${fmtDate(contratActif.dateDebut)}`}>
                                    Planifié
                                </Badge>
                            ) : isContratEchu ? (
                                <Badge variant="danger" size="sm" className="font-bold">
                                    Échu
                                </Badge>
                            ) : (
                                <Badge variant="success" size="sm" className="font-bold">Actif</Badge>
                            )
                        ) : brouillonWf ? (
                            <Badge variant="warning" size="sm" className="font-bold">Brouillon</Badge>
                        ) : (
                            <Badge variant="default" size="sm" className="font-bold">Aucun contrat</Badge>
                        )}
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Type de contrat</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)] truncate">
                            {contratActif?.libelleTypeContrat || '—'}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Intervenant</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)] truncate">
                            {contratActif?.libelleTypeIntervenant || '—'}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Date d'entrée</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">
                            {contratActif?.dateDebut ? fmtDate(contratActif.dateDebut) : '—'}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Ancienneté</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">
                            {employe?.ancienneteEffectiveTexte || contratActif?.ancienneteEffectiveTexte || '—'}
                        </p>
                        {(employe?.nombreContratsChaine || contratActif?.nombreContratsChaine) > 1 && (
                            <p className="text-[10px] text-[var(--color-fg-subtle)] mt-0.5">
                                Cumul ({employe?.nombreContratsChaine || contratActif?.nombreContratsChaine} contrats)
                            </p>
                        )}
                    </div>
                </div>

                {/* Séparateur doux & Ligne Salaire (conditionné par la permission PAIE_READ) */}
                <HasPermission authority="PAIE_READ">
                    <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-center gap-2 text-xs flex-wrap">
                        <span className="inline-flex items-center gap-1.5 text-[var(--color-fg-muted)]">
                            <Banknote size={14} className="text-[var(--color-brand-500)] shrink-0" />
                            Salaire actuel ({periodiciteLabel})
                        </span>
                        <span className="text-sm sm:text-base font-extrabold text-[var(--color-fg)] font-data">
                            {salaireValeur || '—'}
                        </span>
                        <span className="text-[var(--color-fg-subtle)] text-[11px]">({rawMode})</span>
                    </div>
                </HasPermission>
            </Card>

            {/* BANDEAU COMPACT PERIODE D'ESSAI EN COURS */}
            {(etatEssai?.aPeriodeEssai || etatEssai?.isEssaiActif) && (etatEssai?.dateFinEssai || etatEssai?.dateFinEssaiStr) && (
                <div className={`py-2 px-3 border flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 text-xs ${
                    etatEssai.isEssaiEchu
                        ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 text-[var(--color-danger-600)] dark:text-[var(--color-danger-300)]'
                        : 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]'
                }`}>
                    <div className="flex items-center gap-2 min-w-0">
                        <Clock className={`shrink-0 ${etatEssai.isEssaiEchu ? 'text-[var(--color-danger-500)]' : 'text-[var(--color-warning-500)]'}`} size={15} />
                        <span className="font-bold uppercase tracking-wider text-[11px]">Période d'essai</span>
                        {Number.isFinite(etatEssai.joursRestants) && (
                            <Badge variant={etatEssai.isEssaiEchu ? 'danger' : 'warning'} size="xs">
                                {etatEssai.isEssaiEchu
                                    ? `Dépassée de ${Math.abs(etatEssai.joursRestants)}j`
                                    : etatEssai.joursRestants === 0
                                        ? `Dernier jour`
                                        : `Reste ${etatEssai.joursRestants}j`}
                            </Badge>
                        )}
                        <span className="text-[var(--color-fg-muted)] truncate hidden md:inline">
                            — Échéance au <strong>{fmtDate(etatEssai.dateFinEssai || etatEssai.dateFinEssaiStr)}</strong>
                            {workflowPlanifieFutur && ` (Événement ${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement} planifié)`}
                        </span>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto">
                            {etatEssai?.canRenew === true && (
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="h-6 px-2 text-[11px]"
                                    onClick={onOpenRenouvellement}
                                >
                                    Renouveler
                                </Button>
                            )}
                            {etatEssai?.canConfirm === true && (
                                <Button
                                    variant="primary"
                                    size="xs"
                                    className="h-6 px-2 text-[11px]"
                                    onClick={onOpenConfirmation}
                                >
                                    Confirmer l'essai
                                </Button>
                            )}
                        </div>
                    )}
                </div>
            )}

            {/* BANDEAU COMPACT ECHEANCE CONTRAT EN COURS */}
            {contratActif?.dateFin && (contratActif?.isFinProche || contratActif?.isFinEchue || (contratActif?.joursRestantsFin !== null && contratActif?.joursRestantsFin <= 60)) && (
                <div className={`py-2 px-3 border flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 text-xs ${
                    contratActif.isFinEchue || (contratActif.joursRestantsFin !== null && contratActif.joursRestantsFin <= 7)
                        ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 text-[var(--color-danger-600)] dark:text-[var(--color-danger-300)]'
                        : 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/30 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]'
                }`}>
                    <div className="flex items-center gap-2 min-w-0">
                        <Clock className={`shrink-0 ${contratActif.isFinEchue || (contratActif.joursRestantsFin !== null && contratActif.joursRestantsFin <= 7) ? 'text-[var(--color-danger-500)]' : 'text-[var(--color-warning-500)]'}`} size={15} />
                        <span className="font-bold uppercase tracking-wider text-[11px]">Échéance {contratActif.libelleTypeContrat || 'CDD'}</span>
                        {contratActif.joursRestantsFin !== null && (
                            <Badge variant={contratActif.isFinEchue || contratActif.joursRestantsFin <= 7 ? 'danger' : 'warning'} size="xs">
                                {contratActif.isFinEchue
                                    ? `Échu de ${Math.abs(contratActif.joursRestantsFin)}j`
                                    : contratActif.joursRestantsFin === 0
                                        ? `Expire aujourd'hui`
                                        : `Dans ${contratActif.joursRestantsFin}j`}
                            </Badge>
                        )}
                        <span className="text-[var(--color-fg-muted)] truncate hidden md:inline">
                            — Fin prévue au <strong>{fmtDate(contratActif.dateFin)}</strong>
                        </span>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto">
                            {onOpenTransition && (
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    className="h-6 px-2 text-[11px]"
                                    onClick={onOpenTransition}
                                >
                                    Renouveler / Titulariser
                                </Button>
                            )}
                            {onOpenRupture && (
                                <Button
                                    variant="ghost"
                                    size="xs"
                                    className="h-6 px-2 text-[11px] text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                    onClick={onOpenRupture}
                                >
                                    Clôturer
                                </Button>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
