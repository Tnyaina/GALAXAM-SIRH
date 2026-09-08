import { useState, useEffect } from 'react';
import { Check, X, FileText, XSquare, Pencil, AlertTriangle } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Textarea, Checkbox } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import { fmtDate } from '@/utils/dateUtils';

const formatDate = (ts) => fmtDate(ts);

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

  showAnnulerModal,
  onCloseAnnuler,
  onOpenAnnuler,
  onConfirmAnnuler,
  motifAnnulation,
  setMotifAnnulation,

  actionLoading,
  actionError
}) {
  const [justificatifFourni, setJustificatifFourni] = useState(false);
  const [estRemunere, setEstRemunere] = useState(true);
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);

  useEffect(() => {
    if (showValiderModal && selectedDemande) {
      setJustificatifFourni(Boolean(selectedDemande.justificatifFourni));
      setEstRemunere(selectedDemande.estRemunere !== undefined && selectedDemande.estRemunere !== null
        ? Boolean(selectedDemande.estRemunere)
        : Boolean(selectedDemande.estPaye));
      setDerogationSoldeNegatif(Boolean(selectedDemande.derogationSoldeNegatif));
    }
  }, [showValiderModal, selectedDemande]);

  return (
    <>
      {/* ── Modal : Detail demande ─────────────────────────────────────────── */}
      <Modal
        isOpen={showDetailModal && !!selectedDemande}
        onClose={onCloseDetail}
        title={`Détail de la demande #${selectedDemande?.idDemandeConge || ''}`}
        icon={<FileText size={20} />}
        size="xl"
        footer={
          selectedDemande ? (
            <div className="flex flex-wrap items-center justify-between gap-3 w-full">
              <div>
                {selectedDemande.codeStatutDemande === 'VALIDE' && onOpenAnnuler && (
                  <Button
                    variant="danger"
                    size="sm"
                    iconLeft={<X size={14} />}
                    onClick={() => {
                      onCloseDetail();
                      onOpenAnnuler(selectedDemande);
                    }}
                  >
                    Annuler ce congé
                  </Button>
                )}
              </div>
              <div className="flex flex-wrap items-center gap-2">
                {selectedDemande?.codeStatutDemande === 'VALIDE' && selectedDemande?.dateDebut && new Date(selectedDemande.dateDebut).setHours(0,0,0,0) >= new Date().setHours(0,0,0,0) && onOpenModifier && (
                  <Button
                    variant="outline"
                    size="sm"
                    iconLeft={<Pencil size={14} />}
                    onClick={() => {
                      onCloseDetail();
                      onOpenModifier(selectedDemande);
                    }}
                  >
                    Modifier les dates
                  </Button>
                )}

                {(selectedDemande?.isEnAttente ?? (selectedDemande?.codeStatutDemande === 'EN_ATTENTE')) && (
                  <>
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
                        if (onOpenRefus) onOpenRefus(selectedDemande);
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
                        if (onOpenValider) onOpenValider(selectedDemande);
                      }}
                    >
                      Valider
                    </Button>
                  </>
                )}
              </div>
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
        onConfirm={() => onConfirmValider(justificatifFourni, estRemunere, derogationSoldeNegatif)}
        onClose={onCloseValider}
        loading={actionLoading}
      >
        <div className="mt-4 pt-4 border-t border-[var(--color-border-subtle)] space-y-3">
          {actionError && (
            <Alert
              type="error"
              message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)}
            />
          )}
          <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
            <input
              type="checkbox"
              checked={estRemunere}
              onChange={(e) => setEstRemunere(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">
              Maintien de la rémunération (Rémunéré à 100%)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                {estRemunere
                  ? 'Rémunération maintenue : 100% du salaire préservé lors du calcul de paie.'
                  : 'Non rémunéré (sans solde) : déduction proportionnelle au temps de travail sur le bulletin.'}
              </span>
            </span>
          </label>
          <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
            <input
              type="checkbox"
              checked={derogationSoldeNegatif}
              onChange={(e) => setDerogationSoldeNegatif(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-warning-500 cursor-pointer shrink-0"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">
              Accorder la dérogation RH : Solde négatif / Anticipation
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                Permet d'autoriser la validation même si le solde acquis du collaborateur est inférieur aux jours demandés.
              </span>
            </span>
          </label>
          {!selectedDemande?.justificatifFourni && (
            <Checkbox
              label="Marquer le justificatif comme fourni (Requis pour valider certains congés comme Maladie)"
              checked={justificatifFourni}
              onChange={(e) => setJustificatifFourni(e.target.checked)}
            />
          )}
        </div>
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

      {/* ── Modal : Annulation d'un congé avec motif obligatoire ─ */}
      <Modal
        isOpen={Boolean(showAnnulerModal && selectedDemande)}
        onClose={onCloseAnnuler}
        title={
          selectedDemande?.codeStatutDemande === 'VALIDE'
            ? `Annulation du congé validé #${selectedDemande?.idDemandeConge}`
            : `Annulation de la demande #${selectedDemande?.idDemandeConge}`
        }
        icon={<AlertTriangle size={20} className="text-danger-500" />}
        size="md"
        footer={
          <>
            <Button variant="ghost" onClick={onCloseAnnuler} disabled={actionLoading}>
              Conserver le congé
            </Button>
            <Button
              variant="danger"
              onClick={() => onConfirmAnnuler(motifAnnulation)}
              loading={actionLoading}
              disabled={!motifAnnulation?.trim()}
            >
              Confirmer l'annulation
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {actionError && <Alert type="error" message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}

          {selectedDemande?.codeStatutDemande === 'VALIDE' ? (
            <Alert
              type="warning"
              message={`Ce congé est validé. L'annulation va ré-incrémenter automatiquement ${selectedDemande.nbJoursDecomptes || selectedDemande.dureeJours || 0} jour(s) sur le solde acquis de ${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}. Un motif justificatif est obligatoire pour la traçabilité RH.`}
            />
          ) : (
            <Alert
              type="info"
              message="Veuillez renseigner la raison de l'annulation de cette demande de congé pour l'historique RH."
            />
          )}

          {/* Raccourcis de motifs fréquents */}
          <div className="space-y-1.5">
            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Motifs fréquents
            </span>
            <div className="flex flex-wrap gap-1.5">
              {[
                'Impératif opérationnel / rappel en service',
                'Demande du collaborateur (report)',
                'Arrêt maladie survenu avant les congés',
                'Erreur de saisie de dates',
                'Accord mutuel employeur / salarié'
              ].map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  onClick={() => setMotifAnnulation && setMotifAnnulation(suggestion)}
                  className={`text-[11px] px-2.5 py-1 border transition-colors cursor-pointer text-left ${
                    motifAnnulation === suggestion
                      ? 'border-primary-500 bg-primary-500/10 text-primary-600 dark:text-primary-400 font-semibold'
                      : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:border-[var(--color-border)] hover:text-[var(--color-fg)]'
                  }`}
                >
                  {suggestion}
                </button>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
              Motif de l'annulation *
            </label>
            <Textarea
              value={motifAnnulation || ''}
              onChange={(e) => setMotifAnnulation && setMotifAnnulation(e.target.value)}
              placeholder="Explicitez la raison de l'annulation (ex: Report convenu avec le collaborateur, impératif de planning...)"
              rows={3}
              disabled={actionLoading}
            />
            {!motifAnnulation?.trim() && (
              <p className="text-[11px] text-danger-500">
                La saisie du motif est obligatoire pour valider l'annulation.
              </p>
            )}
          </div>
        </div>
      </Modal>
    </>
  );
}
