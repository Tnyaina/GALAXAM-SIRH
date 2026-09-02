import { Clock } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { fmtDate, fmtMoney } from '@/utils/carriereUtils';

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
    const uniteLabel = isHoraire ? 'Ar / h' : isJournalier ? 'Ar / j' : 'Ar / mois';
    const salaireValeur = affectationActive?.salaireBase !== null && affectationActive?.salaireBase !== undefined
        ? `${fmtMoney(affectationActive.salaireBase)} ${uniteLabel}`
        : null;

    return (
        <div className="space-y-4">
            {/* 1. Situation Contractuelle & Rémunération (Format Horizontal Aéré selon le Mockup) */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-5 sm:p-6 space-y-4">
                <div className="flex justify-between items-center">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                        Situation Contractuelle & Rémunération
                    </h3>
                    {onRefresh && (
                        <button
                            type="button"
                            onClick={onRefresh}
                            className="px-3 py-1 text-xs font-semibold border border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] text-[var(--color-fg-muted)] transition-colors cursor-pointer"
                        >
                            Actualiser
                        </button>
                    )}
                </div>

                {/* Métadonnées horizontales réparties */}
                <div className="grid grid-cols-2 sm:grid-cols-5 gap-4 pt-1">
                    <div>
                        <p className="text-[11px] font-medium text-[var(--color-fg-muted)] mb-1.5">Statut actuel</p>
                        {contratActif ? (
                            <Badge variant="success" size="sm" className="font-bold">Actif</Badge>
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

                {/* Séparateur doux & Ligne Salaire */}
                <div className="pt-3 border-t border-[var(--color-border-subtle)] flex items-baseline gap-2 text-xs">
                    <span className="text-[var(--color-fg-muted)]">Salaire actuel ({periodiciteLabel})</span>
                    <span className="text-sm sm:text-base font-extrabold text-[var(--color-fg)] font-data">
                        {salaireValeur || '—'}
                    </span>
                    <span className="text-[var(--color-fg-subtle)] text-[11px]">{rawMode}</span>
                </div>
            </div>

            {/* ⚠️ BANNIÈRE PÉRIODE D'ESSAI EN COURS */}
            {(etatEssai?.aPeriodeEssai || etatEssai?.isEssaiActif) && (etatEssai?.dateFinEssai || etatEssai?.dateFinEssaiStr) && (
                <div className={`p-4 rounded-none border flex flex-col md:flex-row md:items-center justify-between gap-4 ${etatEssai.isEssaiEchu
                    ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300'
                    : 'bg-warning-500/10 border-warning-500/30 text-warning-900 dark:text-warning-200'
                    }`}>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-none bg-white dark:bg-ink-900 shadow-sm flex-shrink-0 mt-0.5">
                            <Clock className={etatEssai.isEssaiEchu ? 'text-danger-500' : 'text-warning-500'} size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h4 className="text-xs font-bold uppercase tracking-wider">
                                    Période d'essai en cours
                                    {etatEssai.periodeActuelle > 1 && ` (Renouvellement ${etatEssai.nombreRenouvellements}/${etatEssai.maxRenouvellements})`}
                                </h4>
                                {Number.isFinite(etatEssai.joursRestants) && (
                                    <Badge variant={etatEssai.isEssaiEchu ? 'danger' : 'warning'} size="xs">
                                        {etatEssai.isEssaiEchu
                                            ? `Dépassée de ${Math.abs(etatEssai.joursRestants)} jour(s)`
                                            : etatEssai.joursRestants === 0
                                                ? `Dernier jour`
                                                : `Reste ${etatEssai.joursRestants} jour(s)`}
                                    </Badge>
                                )}
                            </div>
                            <p className="text-xs mt-1 text-ink-600 dark:text-ink-300">
                                Échéance prévue le <strong>{fmtDate(etatEssai.dateFinEssai || etatEssai.dateFinEssaiStr)}</strong>.
                                {workflowPlanifieFutur
                                    ? ` Un événement (${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement}) est déjà planifié au ${fmtDate(workflowPlanifieFutur.dateEffet)}.`
                                    : ' Aucune action RH de confirmation ou de rupture n\'a encore été validée pour cet essai.'}
                            </p>
                        </div>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-2 self-end md:self-auto flex-shrink-0">
                            {etatEssai?.canRenew === true && (
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    onClick={onOpenRenouvellement}
                                >
                                    Renouveller l'essai
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
                </div>
            )}

            {/* 🔔 BANNIÈRE ÉCHÉANCE CONTRAT EN COURS */}
            {contratActif?.dateFin && (contratActif?.isFinProche || contratActif?.isFinEchue || (contratActif?.joursRestantsFin !== null && contratActif?.joursRestantsFin <= 60)) && (
                <div className={`p-4 rounded-none border flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                    contratActif.isFinEchue || (contratActif.joursRestantsFin !== null && contratActif.joursRestantsFin <= 7)
                        ? 'bg-danger-500/10 border-danger-500/30 text-danger-800 dark:text-danger-300'
                        : 'bg-warning-500/10 border-warning-500/30 text-warning-900 dark:text-warning-200'
                }`}>
                    <div className="flex items-start gap-3">
                        <div className="p-2 rounded-none bg-white dark:bg-ink-900 shadow-sm flex-shrink-0 mt-0.5">
                            <Clock className={contratActif.isFinEchue || (contratActif.joursRestantsFin !== null && contratActif.joursRestantsFin <= 7) ? 'text-danger-500' : 'text-warning-500'} size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h4 className="text-xs font-bold uppercase tracking-wider">
                                    Échéance de contrat ({contratActif.libelleTypeContrat || 'CDD'})
                                </h4>
                                {contratActif.joursRestantsFin !== null && (
                                    <Badge variant={contratActif.isFinEchue || contratActif.joursRestantsFin <= 7 ? 'danger' : 'warning'} size="xs">
                                        {contratActif.isFinEchue
                                            ? `Contrat échu de ${Math.abs(contratActif.joursRestantsFin)} jour(s)`
                                            : contratActif.joursRestantsFin === 0
                                                ? `Expire aujourd'hui`
                                                : `Expire dans ${contratActif.joursRestantsFin} jour(s)`}
                                    </Badge>
                                )}
                            </div>
                            <p className="text-xs mt-1 text-ink-600 dark:text-ink-300">
                                Date de fin contractuelle : <strong>{fmtDate(contratActif.dateFin)}</strong>.
                                {workflowPlanifieFutur
                                    ? ` Un événement (${workflowPlanifieFutur.libelleTypeEvenement || workflowPlanifieFutur.codeTypeEvenement}) est déjà planifié au ${fmtDate(workflowPlanifieFutur.dateEffet)}.`
                                    : ' Pensez à anticiper le renouvellement, la titularisation ou la clôture du contrat.'}
                            </p>
                        </div>
                    </div>

                    {canWrite && !workflowPlanifieFutur && !brouillonWf && (
                        <div className="flex items-center gap-2 self-end md:self-auto flex-shrink-0">
                            {onOpenTransition && (
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    onClick={onOpenTransition}
                                >
                                    Renouveler / Titulariser
                                </Button>
                            )}
                            {onOpenRupture && (
                                <Button
                                    variant="ghost"
                                    size="xs"
                                    className="text-danger-600 hover:bg-danger-500/10"
                                    onClick={onOpenRupture}
                                >
                                    Clôturer le contrat
                                </Button>
                            )}
                        </div>
                    )}
                </div>
            )}
        </div>
    );
}
