import { Calendar, FileText, Clock, AlertTriangle, ShieldAlert, CheckCircle, XCircle, Info } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { fmtDate } from '@/utils/dateUtils';

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
      title={`Détail de la Demande de Congé #${demande.idDemandeConge}`}
      icon={<FileText className="w-5 h-5 text-[var(--color-brand-500)]" />}
      size="lg"
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
        <div className="flex items-start justify-between pb-3 border-b border-[var(--color-border-subtle)]">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Nature de l'absence
            </span>
            <h4 className="text-sm font-bold text-[var(--color-fg)] mt-0.5">
              {demande.libelleTypeConge || demande.codeTypeConge || 'Congé payé'}
            </h4>
            {demande.libelleMotifEvenement && (
              <p className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-medium mt-0.5">
                Motif familial : {demande.libelleMotifEvenement}
              </p>
            )}
          </div>
          <StatusBadge
            status={demande.codeStatutDemande}
            label={demande.libelleStatutDemande}
            size="sm"
          />
        </div>

        {/* Motif de refus en alerte si refusé */}
        {(demande.codeStatutDemande === 'REFUSE' || demande.isRefuse) && (
          <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 space-y-1">
            <div className="flex items-center gap-2 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold">
              <XCircle size={15} />
              <span>Demande refusée par la hiérarchie</span>
            </div>
            <p className="text-[11px] text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] pl-6">
              <strong>Motif :</strong> {demande.motifRefus || 'Aucun motif renseigné.'}
            </p>
          </div>
        )}

        {/* Grille des Informations Calendaires */}
        <div className="grid grid-cols-2 gap-3 p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Période d'absence
            </span>
            <div className="font-mono text-xs font-bold text-[var(--color-fg)] mt-1 flex items-center gap-1.5 flex-wrap">
              <span>{fmtDate(demande.dateDebut)}</span>
              {!isDemiJournee && demande.dateFin && (
                <>
                  <span className="text-[var(--color-fg-subtle)]">→</span>
                  <span>{fmtDate(demande.dateFin)}</span>
                </>
              )}
              {isDemiJournee && (
                <Badge variant="info" size="xs">
                  {momentText}
                </Badge>
              )}
            </div>
          </div>

          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Jours décomptés
            </span>
            <p className="font-mono text-xs font-bold text-[var(--color-fg)] mt-1">
              {demande.nbJoursDecomptes} jour{demande.nbJoursDecomptes > 1 ? 's' : ''}
              {demande.decompteSoldeAcquis === false && (
                <span className="text-[10px] text-[var(--color-fg-muted)] font-normal ml-1">
                  (non décompté du solde annuel)
                </span>
              )}
            </p>
          </div>

          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Date de demande
            </span>
            <p className="font-mono text-xs text-[var(--color-fg-muted)] mt-1">
              {demande.dateDemande ? fmtDate(demande.dateDemande) : '—'}
            </p>
          </div>

          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Date de décision / validation
            </span>
            <p className="font-mono text-xs text-[var(--color-fg-muted)] mt-1">
              {demande.dateValidation ? fmtDate(demande.dateValidation) : demande.dateDecision ? fmtDate(demande.dateDecision) : 'En attente'}
            </p>
          </div>
        </div>

        {/* Particularités & Dérogations RH */}
        {hasDerogations && (
          <div className="space-y-1.5 pt-2 border-t border-[var(--color-border-subtle)]">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Particularités réglementaires accordées
            </span>
            <div className="flex flex-wrap gap-1.5">
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

        {/* Commentaire de l'employé */}
        {demande.commentaire && (
          <div className="space-y-1 pt-2 border-t border-[var(--color-border-subtle)]">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
              Commentaire / Justification
            </span>
            <p className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg)] whitespace-pre-wrap">
              {demande.commentaire}
            </p>
          </div>
        )}

        {/* Statut justificatif */}
        <div className="flex items-center justify-between pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)]">
          <span>Justificatif légal :</span>
          <span className="font-semibold text-[var(--color-fg)]">
            {demande.justificatifFourni ? '✓ Fourni' : '— Non exigé ou en attente'}
          </span>
        </div>
      </div>
    </Modal>
  );
}
