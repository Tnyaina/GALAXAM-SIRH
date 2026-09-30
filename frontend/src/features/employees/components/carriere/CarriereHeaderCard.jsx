import { Briefcase, RefreshCw, Banknote } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import Alert from '@/components/ui/feedback/Alert';
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
        <div className="space-y-4 font-sans">
            {/* 1. Situation Contractuelle & Rémunération */}
            <Card padding bordered className="space-y-4">
                <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                    <h3 className="text-xs font-bold tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Briefcase size={15} className="text-[var(--color-brand-500)] shrink-0" />
                        Situation contractuelle &amp; Rémunération
                    </h3>
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
                            {contratActif?.libelleTypeContrat || <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Intervenant</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)] truncate">
                            {contratActif?.libelleTypeIntervenant || <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Date d'entrée</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">
                            {contratActif?.dateDebut ? fmtDate(contratActif.dateDebut) : <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                        </p>
                    </div>
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1">Ancienneté</p>
                        <p className="text-xs sm:text-sm font-bold text-[var(--color-fg)]">
                            {employe?.ancienneteEffectiveTexte || contratActif?.ancienneteEffectiveTexte || <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
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
                            {salaireValeur || <span className="text-[var(--color-fg-subtle)] font-normal text-xs">Non renseigné</span>}
                        </span>
                        <span className="text-[var(--color-fg-subtle)] text-[11px]">({rawMode})</span>
                    </div>
                </HasPermission>
            </Card>

            {/* BANDEAU PERIODE D'ESSAI EN COURS (Composant Alert) */}
            {(etatEssai?.aPeriodeEssai || etatEssai?.isEssaiActif) && (etatEssai?.dateFinEssai || etatEssai?.dateFinEssaiStr) && (
                <Alert
                    variant={etatEssai.isEssaiEchu ? "danger" : "warning"}
                    className="py-2 px-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
                >
                    <div className="flex items-center gap-2 min-w-0 flex-wrap">
                        <span className="font-bold text-[11px]">Période d'essai</span>
                        {Number.isFinite(etatEssai.joursRestants) && (
                            <Badge variant={etatEssai.isEssaiEchu ? 'danger' : 'warning'} size="xs">
                                {etatEssai.isEssaiEchu
                                    ? `Dépassée de ${Math.abs(etatEssai.joursRestants)}j`
                                    : etatEssai.joursRestants === 0
                                        ? `Dernier jour`
                                        : `Reste ${etatEssai.joursRestants}j`}
                            </Badge>
                        )}
                        <span className="text-[var(--color-fg-muted)] truncate hidden md:inline text-xs">
                            · Échéance le <strong className="text-[var(--color-fg)]">{fmtDate(etatEssai.dateFinEssai || etatEssai.dateFinEssaiStr)}</strong>
                            {workflowPlanifieFutur && ` (${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement} planifié)`}
                        </span>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto">
                            {etatEssai?.canRenew === true && (
                                <Button
                                    variant="outline"
                                    size="xs"
                                    onClick={onOpenRenouvellement}
                                >
                                    Renouveler
                                </Button>
                            )}
                            {etatEssai?.canConfirm === true && (
                                <Button
                                    variant="primary"
                                    size="xs"
                                    onClick={onOpenConfirmation}
                                >
                                    Confirmer l'essai
                                </Button>
                            )}
                        </div>
                    )}
                </Alert>
            )}

            {/* BANDEAU ECHEANCE CONTRAT EN COURS (Composant Alert) */}
            {contratActif?.dateFin && (contratActif?.isFinProche || contratActif?.isFinEchue || (contratActif?.joursRestantsFin !== null && contratActif?.joursRestantsFin <= 60)) && (
                <Alert
                    variant={contratActif.isFinEchue || (contratActif.joursRestantsFin !== null && contratActif.joursRestantsFin <= 7) ? "danger" : "warning"}
                    className="py-2 px-3 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
                >
                    <div className="flex items-center gap-2 min-w-0 flex-wrap">
                        <span className="font-bold text-[11px]">Échéance {contratActif.libelleTypeContrat || 'CDD'}</span>
                        {contratActif.joursRestantsFin !== null && (
                            <Badge variant={contratActif.isFinEchue || contratActif.joursRestantsFin <= 7 ? 'danger' : 'warning'} size="xs">
                                {contratActif.isFinEchue
                                    ? `Échu de ${Math.abs(contratActif.joursRestantsFin)}j`
                                    : contratActif.joursRestantsFin === 0
                                        ? `Expire aujourd'hui`
                                        : `Dans ${contratActif.joursRestantsFin}j`}
                            </Badge>
                        )}
                        <span className="text-[var(--color-fg-muted)] truncate hidden md:inline text-xs">
                            · Échéance le <strong className="text-[var(--color-fg)]">{fmtDate(contratActif.dateFin)}</strong>
                        </span>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-1.5 shrink-0 self-end sm:self-auto">
                            {onOpenTransition && (
                                <Button
                                    variant="outline"
                                    size="xs"
                                    onClick={onOpenTransition}
                                >
                                    Renouveler / Titulariser
                                </Button>
                            )}
                            {onOpenRupture && (
                                <Button
                                    variant="danger"
                                    size="xs"
                                    onClick={onOpenRupture}
                                >
                                    Clôturer
                                </Button>
                            )}
                        </div>
                    )}
                </Alert>
            )}
        </div>
    );
}
