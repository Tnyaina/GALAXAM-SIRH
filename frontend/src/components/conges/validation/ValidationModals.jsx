import { Check, X, FileText, XSquare } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Textarea } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';

const formatDate = (ts) => {
  if (!ts) return '-';
  return new Date(ts).toLocaleDateString('fr-FR');
};

const BADGE_VARIANT = {
  VALIDE:     'success',
  REFUSE:     'danger',
  EN_ATTENTE: 'warning',
  ANNULE:     'neutral',
  BROUILLON:  'default',
};

const STATUT_LABEL = {
  VALIDE:     'Validé',
  REFUSE:     'Refusé',
  EN_ATTENTE: 'En attente',
  ANNULE:     'Annulé',
  BROUILLON:  'Brouillon',
};

export default function ValidationModals({
  selectedDemande,
  
  showDetailModal,
  onCloseDetail,
  onOpenValider,
  onOpenRefus,

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
  return (
    <>
      {/* ── Modal : Detail demande ─────────────────────────────────────────── */}
      {showDetailModal && selectedDemande && (
        <div className="fixed inset-0 bg-slate-950/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] max-w-lg w-full shadow-2xl overflow-hidden font-sans">
            {/* Header */}
            <div className="flex items-center justify-between p-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              <div className="flex items-center gap-2">
                <FileText size={14} className="text-[var(--color-brand-500)]" />
                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                  Détail de la demande #{selectedDemande.idDemandeConge}
                </h4>
              </div>
              <button onClick={onCloseDetail} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] font-bold text-lg leading-none">&times;</button>
            </div>

            {/* Corps */}
            <div className="p-6 space-y-5 overflow-y-auto max-h-[65vh]">
              {/* Identité + statut */}
              <div className="flex items-start justify-between pb-4 border-b border-[var(--color-border-subtle)]">
                <div>
                  <h3 className="text-base font-bold text-[var(--color-fg)]">
                    {`${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}`.trim() || `Employé ${selectedDemande.idEmploye}`}
                  </h3>
                  {selectedDemande.matricule && <p className="text-xs text-[var(--color-fg-subtle)] font-data">{selectedDemande.matricule}</p>}
                  <p className="text-xs text-[var(--color-fg-muted)] mt-0.5 font-medium">{selectedDemande.libelleTypeConge || selectedDemande.codeTypeConge}</p>
                </div>
                <Badge variant={BADGE_VARIANT[selectedDemande.codeStatutDemande] || 'default'} dot>
                  {STATUT_LABEL[selectedDemande.codeStatutDemande] || selectedDemande.codeStatutDemande}
                </Badge>
              </div>

              {/* Grille dates */}
              <div className="grid grid-cols-2 gap-x-6 gap-y-4">
                {[
                  ['Date de début',      formatDate(selectedDemande.dateDebut)],
                  ['Date de fin',        formatDate(selectedDemande.dateFin)],
                  ['Décompte calendaire strict', `${selectedDemande.nbJoursDecomptes} jours`],
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
                <div className="p-3 bg-warning-500/8 border border-warning-500/20 space-y-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-warning-500">Motif familial</span>
                  <p className="text-sm font-semibold text-[var(--color-fg)]">{selectedDemande.libelleMotifEvenement}</p>
                </div>
              )}

              {/* Commentaire */}
              {selectedDemande.commentaire && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Commentaire</span>
                  <p className="text-sm bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-[var(--color-fg)] leading-relaxed">
                    {selectedDemande.commentaire}
                  </p>
                </div>
              )}

              {/* Motif de refus */}
              {selectedDemande.motifRefus && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-danger-500">Motif du refus</span>
                  <p className="text-sm bg-danger-500/8 border border-danger-500/25 p-3 text-[var(--color-fg)] leading-relaxed">
                    {selectedDemande.motifRefus}
                  </p>
                </div>
              )}

              {/* Dérogations */}
              {(selectedDemande.derogationSoldeNegatif || selectedDemande.derogationDelaiPreavis || selectedDemande.extensionPathologiqueMaternite || selectedDemande.justificatifFourni) && (
                <div className="space-y-1.5">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Mentions</span>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedDemande.derogationSoldeNegatif        && <Badge variant="danger"  size="sm">Dérogation solde négatif</Badge>}
                    {selectedDemande.derogationDelaiPreavis        && <Badge variant="warning" size="sm">Dérogation préavis</Badge>}
                    {selectedDemande.extensionPathologiqueMaternite && <Badge variant="danger"  size="sm">Extension pathologique (119j)</Badge>}
                    {selectedDemande.justificatifFourni             && <Badge variant="success" size="sm">Justificatif fourni</Badge>}
                  </div>
                </div>
              )}
            </div>

            {/* Footer avec actions contextuelles */}
            <div className="flex items-center justify-between p-4 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
              {selectedDemande.codeStatutDemande === 'EN_ATTENTE' ? (
                <div className="flex items-center gap-2">
                  <Button variant="danger"  size="sm" iconLeft={<X size={14}/>}
                    onClick={() => { onCloseDetail(); onOpenRefus(selectedDemande); }}>Refuser</Button>
                  <Button variant="success" size="sm" iconLeft={<Check size={14}/>}
                    onClick={() => { onCloseDetail(); onOpenValider(selectedDemande); }}>Valider</Button>
                </div>
              ) : <span />}
              <Button variant="outline" size="sm" onClick={onCloseDetail}>Fermer</Button>
            </div>
          </div>
        </div>
      )}

      {/* ── Confirmation validation ────────────────────────────────────────── */}
      <ConfirmDialog
        isOpen={showValiderModal}
        title="Confirmer la validation"
        message={`Valider la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''} (${selectedDemande?.nbJoursDecomptes} jours de ${selectedDemande?.codeTypeConge}) ?`}
        confirmLabel="Valider la demande"
        confirmVariant="success"
        onConfirm={onConfirmValider}
        onClose={onCloseValider}
        loading={actionLoading}
      />

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
