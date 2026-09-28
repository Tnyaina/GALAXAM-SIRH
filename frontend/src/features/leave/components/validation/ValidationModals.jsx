import { useState, useEffect } from 'react';
import { Check, X, FileText, XSquare, Pencil, AlertTriangle, Calculator } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Textarea, Checkbox } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import { fmtDate } from '@/utils/dateUtils';
import congeService from '@/features/leave/api/congeService';

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
  // État local : informations complémentaires demandées lors de la validation.
  const [justificatifFourni, setJustificatifFourni] = useState(false);
  const [estRemunere, setEstRemunere] = useState(true);
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [simulation, setSimulation] = useState(null);

  // Effets : réinitialise et charge le solde pour la demande sélectionnée.
  useEffect(() => {
    let isMounted = true;
    const fetchSolde = async () => {
      if (!selectedDemande?.idEmploye) {
        setSoldeEmploye(null);
        setSimulation(null);
        return;
      }
      try {
        const res = await congeService.getSoldeEmploye(selectedDemande.idEmploye);
        if (isMounted) setSoldeEmploye(res);
      } catch {
        if (isMounted) setSoldeEmploye(null);
      }
      try {
        const result = await congeService.simulerDemande({
          idEmploye: selectedDemande.idEmploye,
          idTypeConge: selectedDemande.idTypeConge,
          idMotifEvenementFamilial: selectedDemande.idMotifEvenementFamilial,
          dateDebut: selectedDemande.dateDebut,
          dateFin: selectedDemande.dateFin,
          estDemiJournee: Boolean(selectedDemande.estDemiJournee),
          derogationSoldeNegatif: Boolean(derogationSoldeNegatif),
          derogationDelaiPreavis: Boolean(selectedDemande.derogationDelaiPreavis),
          extensionPathologiqueMaternite: Boolean(selectedDemande.extensionPathologiqueMaternite),
        });
        if (isMounted) setSimulation(result);
      } catch {
        if (isMounted) setSimulation(null);
      }
    };
    if (showValiderModal || showDetailModal) {
      fetchSolde();
    }
    return () => { isMounted = false; };
  }, [showValiderModal, showDetailModal, selectedDemande?.idEmploye, selectedDemande?.idTypeConge, selectedDemande?.dateDebut, selectedDemande?.dateFin, selectedDemande?.estDemiJournee, selectedDemande?.idMotifEvenementFamilial, selectedDemande?.derogationDelaiPreavis, selectedDemande?.extensionPathologiqueMaternite, derogationSoldeNegatif]);

  useEffect(() => {
    if (showValiderModal && selectedDemande) {
      setJustificatifFourni(Boolean(selectedDemande.justificatifFourni));
      setEstRemunere(selectedDemande.estRemunere !== undefined && selectedDemande.estRemunere !== null
        ? Boolean(selectedDemande.estRemunere)
        : Boolean(selectedDemande.estPaye));
      setDerogationSoldeNegatif(Boolean(selectedDemande.derogationSoldeNegatif));
    }
  }, [showValiderModal, selectedDemande]);

  const joursDemandes = Number(selectedDemande?.nbJoursDecomptes ?? 0);
  const soldeAvant = soldeEmploye?.soldeRestant ?? soldeEmploye?.soldeActuel ?? selectedDemande?.soldeRestant ?? selectedDemande?.soldeActuel ?? null;
  const joursDebites = simulation?.joursDebites ?? null;
  const soldeApres = simulation?.soldeApres ?? null;

  // Rendu
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
                {selectedDemande.availableActions?.CANCEL?.allowed === true && onOpenAnnuler && (
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
                {selectedDemande?.availableActions?.MODIFY_DATES?.allowed === true && onOpenModifier && (
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

                {selectedDemande?.availableActions?.APPROVE?.allowed === true && (
                  <>
                    {selectedDemande.availableActions?.EDIT?.allowed === true && <Button
                      variant="outline"
                      size="sm"
                      iconLeft={<Pencil size={14} />}
                      onClick={() => {
                        onCloseDetail();
                        if (onOpenModifier) onOpenModifier(selectedDemande);
                      }}
                    >
                      Modifier
                    </Button>}
                    {selectedDemande.availableActions?.REJECT?.allowed === true && <Button
                      variant="danger"
                      size="sm"
                      iconLeft={<X size={14} />}
                      onClick={() => {
                        onCloseDetail();
                        if (onOpenRefus) onOpenRefus(selectedDemande);
                      }}
                    >
                      Refuser
                    </Button>}
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

            {/* Audit comptable du solde de congé */}
            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-xs space-y-2">
              <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
                <Calculator size={13} className="text-brand-500" />
                Contrôle comptable du Solde de Congés Payés
              </span>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
                <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                  <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Avant</span>
                  <span className="font-mono font-bold text-xs text-[var(--color-fg)]">
                    {soldeAvant != null ? `${Number(soldeAvant).toFixed(2)} j` : '—'}
                  </span>
                </div>
                <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                  <span className="text-[10px] text-[var(--color-fg-muted)] block">Jours Demandés</span>
                  <span className="font-mono font-bold text-xs text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                    {Number(joursDemandes).toFixed(2)} j
                  </span>
                </div>
                <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                  <span className="text-[10px] text-[var(--color-fg-muted)] block">Jours Débités</span>
                  <span className="font-mono font-bold text-xs text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                    {joursDebites != null ? `-${Number(joursDebites).toFixed(2)} j` : '—'}
                  </span>
                </div>
                <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                  <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Après</span>
                  <span className={`font-mono font-bold text-xs ${soldeApres != null && soldeApres < 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                    {soldeApres != null ? `${Number(soldeApres).toFixed(2)} j` : 'Simulation indisponible'}
                  </span>
                </div>
              </div>
              <div className="text-[11px] text-[var(--color-fg-subtle)] pt-1 border-t border-[var(--color-border-subtle)] flex items-center justify-between">
                <span>Impact Paie : <strong>{estRemunere ? 'Congé Rémunéré (TCONGE valorisé)' : 'Sans Solde (Retenue absence)'}</strong></span>
                {soldeEmploye?.tauxJournalierConge && (
                  <span>TCONGE : <strong className="font-mono">{Number(soldeEmploye.tauxJournalierConge).toLocaleString('fr-FR')} Ar/j</strong></span>
                )}
              </div>
            </div>

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
        variant="success"
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

          {/* Audit comptable solde dans la modale de validation */}
          <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-xs space-y-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
              <Calculator size={13} className="text-brand-500" />
              Impact Comptable sur le Solde & Paie
            </span>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Avant</span>
                <span className="font-mono font-bold text-xs text-[var(--color-fg)]">
                  {soldeAvant != null ? `${Number(soldeAvant).toFixed(2)} j` : '—'}
                </span>
              </div>
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Demandés</span>
                <span className="font-mono font-bold text-xs text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                  {Number(joursDemandes).toFixed(2)} j
                </span>
              </div>
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Débités</span>
                <span className="font-mono font-bold text-xs text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                  {joursDebites != null ? `-${Number(joursDebites).toFixed(2)} j` : '—'}
                </span>
              </div>
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Après</span>
                <span className={`font-mono font-bold text-xs ${soldeApres != null && soldeApres < 0 ? 'text-[var(--color-danger-600)] font-bold' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                  {soldeApres != null ? `${Number(soldeApres).toFixed(2)} j` : 'Simulation indisponible'}
                </span>
              </div>
            </div>
            {soldeApres != null && soldeApres < 0 && !derogationSoldeNegatif && (
              <div className="text-[11px] text-[var(--color-danger-600)] font-semibold flex items-center gap-1 mt-1">
                <AlertTriangle size={12} />
                Solde insuffisant : cochez l’autorisation de dépassement pour valider par anticipation.
              </div>
            )}
          </div>
          <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
            <input
              type="checkbox"
              checked={estRemunere}
              onChange={(e) => setEstRemunere(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">
              Congé rémunéré (indemnisé sur les droits acquis)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                {estRemunere
                  ? "Congé rémunéré : indemnité de congés payés versée selon la valorisation des droits acquis de l'employé."
                  : "Non rémunéré (congé sans solde) : retenue au taux d'absence sur le bulletin de paie."}
              </span>
            </span>
          </label>
          <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
            <input
              type="checkbox"
              checked={derogationSoldeNegatif}
              onChange={(e) => setDerogationSoldeNegatif(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-warning-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">
              Autoriser le dépassement de solde (prise par anticipation)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                Permet de valider la demande même si le solde acquis est insuffisant. Le solde négatif sera régularisé par les prochaines acquisitions mensuelles.
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
              message={`Ce congé est validé. L'annulation va ré-incrémenter automatiquement ${selectedDemande.nbJoursDecomptes || selectedDemande.dureeJours || 0} jour(s) sur le solde acquis de ${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}. Un motif justificatif est obligatoire pour la traçabilité.`}
            />
          ) : (
            <Alert
              type="info"
              message="Veuillez renseigner la raison de l'annulation de cette demande de congé pour l'historique."
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
