import { useState, useEffect } from 'react';
import { Check, X, FileText, XSquare, Pencil } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Textarea, Checkbox } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';

const formatDate = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleDateString('fr-FR');
};

export default function ValidationModals({
  selectedDemande,
  
  showDetailModal,
  onCloseDetail,
  onOpenValider,
  onOpenRefus,
  onOpenModifier,

  showValiderModal,
  onCloseValider,
  onConfirmValider,
  
  showRefusModal,
  onCloseRefus,
  onConfirmRefus,
  motifRefus,
  setMotifRefus,

  actionLoading,
  actionError
}) {
  const [justificatifFourni, setJustificatifFourni] = useState(false);

  useEffect(() => {
    if (showValiderModal) {
      setJustificatifFourni(false);
    }
  }, [showValiderModal]);

  return (
    <>
      {/* ── Modal : Detail demande ─────────────────────────────────────────── */}
      <Modal
        isOpen={showDetailModal && !!selectedDemande}
        onClose={onCloseDetail}
        title={selectedDemande ? `Détail de la demande #${selectedDemande.idDemandeConge}` : 'Détail de la demande'}
        icon={<FileText size={16} />}
        size="lg"
        footer={
          (selectedDemande?.isEnAttente ?? (selectedDemande?.codeStatutDemande === 'EN_ATTENTE')) ? (
            <div className="flex items-center justify-end gap-2 w-full">
              <Button
                variant="outline"
                size="sm"
                iconLeft={<Pencil size={14} />}
                onClick={() => {
                  onCloseDetail();
                  if (onOpenModifier) onOpenModifier(selectedDemande);
                }}
              >
                Modifier
              </Button>
              <Button
                variant="danger"
                size="sm"
                iconLeft={<X size={14} />}
                onClick={() => {
                  onCloseDetail();
                  onOpenRefus(selectedDemande);
                }}
              >
                Refuser
              </Button>
              <Button
                variant="success"
                size="sm"
                iconLeft={<Check size={14} />}
                onClick={() => {
                  onCloseDetail();
                  onOpenValider(selectedDemande);
                }}
              >
                Valider
              </Button>
            </div>
          ) : null
        }
      >
        {selectedDemande && (
          <div className="space-y-5">
            {/* Identité + statut */}
            <div className="flex items-start justify-between pb-4 border-b border-[var(--color-border-subtle)]">
              <div>
                <h3 className="text-base font-bold text-[var(--color-fg)]">
                  {`${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}`.trim() || `Employé ${selectedDemande.idEmploye}`}
                </h3>
                {selectedDemande.matricule && <p className="text-xs text-[var(--color-fg-subtle)] font-data">{selectedDemande.matricule}</p>}
                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 font-medium">{selectedDemande.libelleTypeConge || selectedDemande.codeTypeConge}</p>
              </div>
              <StatusBadge
                status={selectedDemande.codeStatutDemande}
                label={selectedDemande.libelleStatutDemande}
                size="md"
              />
            </div>

            {/* Grille dates */}
            <div className="grid grid-cols-2 gap-x-6 gap-y-4">
              {[
                ['Date de début',      formatDate(selectedDemande.dateDebut)],
                ['Date de fin',        formatDate(selectedDemande.dateFin)],
                ['Décompte calendaire strict', selectedDemande.estDemiJournee ? `0,5 jour (${selectedDemande.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'})` : `${selectedDemande.nbJoursDecomptes} jours`],
                ['Date de soumission', formatDate(selectedDemande.dateDemande)],
              ].map(([label, value]) => (
                <div key={label}>
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">{label}</span>
                  <p className="font-data text-sm font-semibold text-[var(--color-fg)] mt-0.5">{value}</p>
                </div>
              ))}
            </div>

            {/* Motif familial */}
            {selectedDemande.libelleMotifEvenement && (
              <div className="p-3 border border-warning-500/30 bg-warning-500/5 text-xs text-warning-600 dark:text-warning-400 font-semibold">
                Motif d'événement familial : {selectedDemande.libelleMotifEvenement}
              </div>
            )}

            {/* Commentaire */}
            {selectedDemande.commentaire && (
              <div className="space-y-1 pt-2 border-t border-[var(--color-border-subtle)]">
                <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Commentaire de l'employé</span>
                <p className="text-xs text-[var(--color-fg)] bg-[var(--color-bg-subtle)] p-3 rounded-none border border-[var(--color-border-subtle)]">
                  "{selectedDemande.commentaire}"
                </p>
              </div>
            )}

            {/* Justificatif */}
            <div className="flex items-center gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
              <span className="text-xs font-bold text-[var(--color-fg-muted)]">Justificatif fourni :</span>
              <Badge variant={selectedDemande.justificatifFourni ? 'success' : 'neutral'} size="sm">
                {selectedDemande.justificatifFourni ? 'Oui' : 'Non'}
              </Badge>
            </div>

            {/* Motif de refus si refusé */}
            {Boolean(selectedDemande.isRefuse ?? (selectedDemande.codeStatutDemande === 'REFUSE')) && selectedDemande.motifRefus && (
              <div className="p-3 border border-danger-500/30 bg-danger-500/5 space-y-1">
                <span className="text-xs font-bold text-danger-500 uppercase tracking-wider">Motif de refus</span>
                <p className="text-xs text-danger-600 dark:text-danger-400 font-medium">{selectedDemande.motifRefus}</p>
              </div>
            )}
          </div>
        )}
      </Modal>

      {/* ── Confirmation validation ────────────────────────────────────────── */}
      <ConfirmDialog
        isOpen={showValiderModal}
        title="Confirmer la validation"
        message={`Valider la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''} (${selectedDemande?.estDemiJournee ? `0,5 jour ${selectedDemande?.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'}` : `${selectedDemande?.nbJoursDecomptes} jours`} de ${selectedDemande?.codeTypeConge}) ?`}
        confirmLabel="Valider la demande"
        confirmVariant="success"
        onConfirm={() => onConfirmValider(justificatifFourni)}
        onClose={onCloseValider}
        loading={actionLoading}
      >
        {!selectedDemande?.justificatifFourni && (
          <div className="mt-4 pt-4 border-t border-[var(--color-border-subtle)]">
            <Checkbox
              label="Marquer le justificatif comme fourni (Requis pour valider certains congés comme Maladie)"
              checked={justificatifFourni}
              onChange={(e) => setJustificatifFourni(e.target.checked)}
            />
          </div>
        )}
      </ConfirmDialog>

      {/* ── Modal refus avec motif ─────────────────────────────────────────── */}
      <Modal
        isOpen={showRefusModal}
        onClose={onCloseRefus}
        title={`Refuser la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''}`}
        icon={<XSquare size={20} />}
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={onCloseRefus}>Annuler</Button>
            <Button variant="danger" onClick={onConfirmRefus} loading={actionLoading}>
              Confirmer le refus
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {actionError && <Alert type="error" message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}
          <Alert type="warning" message="Le refus d'une demande de congé doit être justifié." />
          <div className="space-y-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Motif du refus *
            </label>
            <Textarea
              value={motifRefus}
              onChange={(e) => setMotifRefus(e.target.value)}
              placeholder="Indiquez la raison du refus (ex: nécessité de service, effectif insuffisant...)"
              rows={4}
              disabled={actionLoading}
            />
          </div>
        </div>
      </Modal>
    </>
  );
}
