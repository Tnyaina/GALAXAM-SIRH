import { FileText, XCircle, Calendar, CheckCircle2, Clock, UserCheck, ShieldAlert, AlertCircle } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { fmtDate } from '@/utils/dateUtils';

// Helper pour styliser les types de congés de façon cohérente
const getTypeBadgeConfig = (code, d = null) => {
  if (d?.badgeVariant) {
    const label = d.isCp ? 'Congé annuel payé'
      : d.isMaladie ? 'Maladie / Santé'
      : d.isEvenementFamilial ? 'Événement familial'
      : d.isSansSolde ? 'Congé sans solde'
      : null;
    return { variant: d.badgeVariant, label };
  }
  switch (code) {
    case 'CONGE_PAYE':
    case 'ANNUEL':
      return { variant: 'brand', label: 'Congé annuel payé' };
    case 'MALADIE':
    case 'ACCIDENT_TRAVAIL':
      return { variant: 'info', label: 'Maladie / Santé' };
    case 'EVENEMENT_FAMILIAL':
    case 'FAMILLE':
      return { variant: 'warning', label: 'Événement familial' };
    case 'MATERNITE':
    case 'PATERNITE':
      return { variant: 'purple', label: 'Maternité / Paternité' };
    case 'SANS_SOLDE':
      return { variant: 'outline', label: 'Congé sans solde' };
    default:
      return { variant: 'default', label: null };
  }
};

export default function DemandeDetailsModal({
  isOpen,
  onClose,
  demande,
  canWrite = false,
  onEdit,
  onCancel,
}) {
  if (!demande) return null;

  const isDemiJournee = Boolean(demande.estDemiJournee);
  const momentText = demande.momentDemiJournee === 'MATIN'
    ? 'Matinée'
    : demande.momentDemiJournee === 'APRES_MIDI'
      ? 'Après-midi'
      : 'Demi-journée';

  const typeConfig = getTypeBadgeConfig(demande.codeTypeConge, demande);
  const isRegularisationPointage = Boolean(demande.isRegularisationPointage || demande.commentaire?.includes('[REGULARISATION_POINTAGE]'));
  const cleanCommentaire = demande.commentaire?.replace(/\[REGULARISATION_POINTAGE\]\s*/g, '').trim();

  const hasDerogations = Boolean(
    demande.derogationSoldeNegatif ||
    demande.derogationDelaiPreavis ||
    demande.derogationContinuiteService ||
    demande.extensionPathologiqueMaternite ||
    demande.suspendAcquisitionSolde
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`Détail de la Demande #${demande.idDemandeConge}`}
      icon={<FileText className="w-5 h-5 text-[var(--color-brand-500)]" />}
      size="xl"
      footer={
        <div className="flex items-center justify-between w-full">
          <div>
            {canWrite && demande.availableActions?.CANCEL?.allowed === true && onCancel && (
              <Button
                variant="outline"
                size="sm"
                className="text-[var(--color-danger-500)] border-[var(--color-danger-500)]/30 hover:bg-[var(--color-danger-500)]/10"
                onClick={() => {
                  onClose();
                  onCancel(demande);
                }}
              >
                Annuler cette demande
              </Button>
            )}
          </div>
          <div className="flex items-center gap-2">
            {canWrite && demande.availableActions?.EDIT?.allowed === true && onEdit && (
              <Button
                variant="primary"
                size="sm"
                onClick={() => {
                  onClose();
                  onEdit(demande);
                }}
              >
                Modifier la demande
              </Button>
            )}
            <Button variant="ghost" size="sm" onClick={onClose}>
              Fermer
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4 text-xs font-sans">
        {/* En-tête : Type + Statut */}
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
          <div className="space-y-1">
            <div className="flex items-center gap-2 flex-wrap">
              <h4 className="text-sm font-bold text-[var(--color-fg)]">
                {demande.libelleTypeConge || demande.codeTypeConge || 'Congé payé'}
              </h4>
              <Badge variant={typeConfig.variant} size="xs">
                {typeConfig.label || demande.codeTypeConge}
              </Badge>
              {isRegularisationPointage && (
                <Badge variant="purple" size="xs" className="gap-1">
                  <Clock size={11} />
                  Régularisation Pointage
                </Badge>
              )}
            </div>
            {demande.libelleMotifEvenement && (
              <p className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-medium">
                Motif familial : {demande.libelleMotifEvenement}
              </p>
            )}
          </div>
          <div className="shrink-0">
            <StatusBadge
              status={demande.codeStatutDemande}
              label={demande.libelleStatutDemande}
              size="sm"
            />
          </div>
        </div>

        {/* Bannière explicative pour régularisation de pointage */}
        {isRegularisationPointage && (
          <div className="p-3 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 flex items-start gap-2.5">
            <Clock className="w-4 h-4 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shrink-0 mt-0.5" />
            <div className="text-[11px] space-y-0.5">
              <p className="font-bold text-[var(--color-fg)]">Absence issue du Pointage</p>
              <p className="text-[var(--color-fg-muted)]">
                Cette demande a été générée automatiquement lors de l'arbitrage ou de la validation d'une anomalie de pointage.
              </p>
            </div>
          </div>
        )}

        {/* Motif de refus en alerte si refusé */}
        {(demande.codeStatutDemande === 'REFUSE' || demande.isRefuse) && (
          <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 space-y-1">
            <div className="flex items-center gap-2 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold">
              <XCircle size={15} />
              <span>Demande refusée</span>
            </div>
            <p className="text-[11px] text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] pl-6">
              <strong>Motif :</strong> {demande.motifRefus || 'Aucun motif renseigné.'}
            </p>
          </div>
        )}

        {/* Cartouches Clés : Période & Impact Solde */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Bloc Période */}
          <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-2">
            <div className="flex items-center gap-1.5 text-[var(--color-fg-muted)]">
              <Calendar size={13} />
              <span className="text-[10px] font-bold uppercase tracking-wider">Période d'absence</span>
            </div>
            <div className="font-mono text-sm font-bold text-[var(--color-fg)] flex items-center gap-2 flex-wrap">
              <span>{fmtDate(demande.dateDebut)}</span>
              {!isDemiJournee && demande.dateFin && (
                <>
                  <span className="text-[var(--color-fg-subtle)]">→</span>
                  <span>{fmtDate(demande.dateFin)}</span>
                </>
              )}
            </div>
            {isDemiJournee && (
              <Badge variant="info" size="xs">
                Demi-journée ({momentText})
              </Badge>
            )}
          </div>

          {/* Bloc Décompte et Impact Solde */}
          <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                Impact sur le solde
              </span>
              {demande.decompteSoldeAcquis === false ? (
                <Badge variant="neutral" size="xs">Non décompté</Badge>
              ) : (
                <Badge variant="warning" size="xs">Décompté</Badge>
              )}
            </div>
            <div className="font-mono text-sm font-bold text-[var(--color-fg)]">
              <span className={demande.decompteSoldeAcquis === false ? 'text-[var(--color-fg-muted)]' : 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]'}>
                {demande.decompteSoldeAcquis === false ? '' : '-'}
                {Number(demande.nbJoursDecomptes || 0).toFixed(2)} j
              </span>
              <span className="text-xs text-[var(--color-fg-muted)] font-normal ml-2">
                ({demande.nbJoursDecomptes} jour{demande.nbJoursDecomptes > 1 ? 's' : ''} d'absence)
              </span>
            </div>
            <p className="text-[10px] text-[var(--color-fg-subtle)]">
              {demande.estPaye !== false ? 'Absence rémunérée' : 'Absence non rémunérée'}
            </p>
          </div>
        </div>

        {/* Workflow & Traçabilité */}
        <div className="p-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] space-y-2.5">
          <div className="flex items-center gap-1.5 text-[var(--color-fg-muted)] pb-1.5 border-b border-[var(--color-border-subtle)]">
            <UserCheck size={13} />
            <span className="text-[10px] font-bold uppercase tracking-wider">Workflow & Validation</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-[11px]">
            <div>
              <span className="text-[10px] text-[var(--color-fg-muted)] block">Date de la demande</span>
              <span className="font-mono font-medium text-[var(--color-fg)]">
                {demande.dateDemande ? fmtDate(demande.dateDemande) : '-'}
              </span>
              {demande.saisiParIdentifiant && (
                <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                  par {demande.saisiParIdentifiant}
                </span>
              )}
            </div>
            <div>
              <span className="text-[10px] text-[var(--color-fg-muted)] block">Décision / Validation</span>
              <span className="font-mono font-medium text-[var(--color-fg)]">
                {demande.dateValidation ? fmtDate(demande.dateValidation) : demande.dateDecision ? fmtDate(demande.dateDecision) : 'En cours d\'instruction'}
              </span>
              {demande.valideParIdentifiant && (
                <span className="text-[10px] text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-medium block">
                  validé par {demande.valideParIdentifiant}
                </span>
              )}
            </div>
          </div>
        </div>

        {/* Particularités & Dérogations RH */}
        {hasDerogations && (
          <div className="space-y-1.5 p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
              <ShieldAlert size={12} className="text-[var(--color-warning-500)]" />
              Particularités réglementaires accordées
            </span>
            <div className="flex flex-wrap gap-1.5 pt-1">
              {demande.derogationSoldeNegatif && (
                <Badge variant="warning" size="xs">Dérogation solde négatif</Badge>
              )}
              {demande.derogationDelaiPreavis && (
                <Badge variant="warning" size="xs">Dérogation préavis</Badge>
              )}
              {demande.derogationContinuiteService && (
                <Badge variant="warning" size="xs">Dérogation continuité service</Badge>
              )}
              {demande.extensionPathologiqueMaternite && (
                <Badge variant="danger" size="xs">Extension pathologique</Badge>
              )}
              {demande.suspendAcquisitionSolde && (
                <Badge variant="default" size="xs">Suspension acquisition solde</Badge>
              )}
            </div>
          </div>
        )}

        {/* Commentaire de l'employé / motif d'arbitrage */}
        {cleanCommentaire && (
          <div className="space-y-1 pt-1">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Commentaire / Motif
            </span>
            <p className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg)] whitespace-pre-wrap">
              {cleanCommentaire}
            </p>
          </div>
        )}

        {/* Statut justificatif */}
        <div className="flex items-center justify-between p-2.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[11px]">
          <div className="flex items-center gap-1.5 text-[var(--color-fg-muted)]">
            {demande.justificatifFourni ? (
              <CheckCircle2 size={13} className="text-[var(--color-success-500)]" />
            ) : (
              <AlertCircle size={13} className="text-[var(--color-fg-subtle)]" />
            )}
            <span>Pièce justificative légale</span>
          </div>
          <Badge variant={demande.justificatifFourni ? 'success' : 'neutral'} size="xs">
            {demande.justificatifFourni ? 'Fournie et conforme' : 'Non exigée ou en attente'}
          </Badge>
        </div>
      </div>
    </Modal>
  );
}
