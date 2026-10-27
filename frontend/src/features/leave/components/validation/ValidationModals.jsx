import { useState, useEffect } from 'react';
import { XSquare, AlertTriangle, Calculator } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { Textarea, Checkbox } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import congeService from '@/features/leave/api/congeService';
import DemandeDetailsModal from '@/features/leave/components/DemandeDetailsModal';
import { formatLeaveDaysPreview } from '@/features/leave/utils/leavePreviewPresentation';
import { defaultLeavePayable } from '@/features/leave/utils/defaultLeavePayable';
import { useAuth } from '@/context/AuthContext';

const formatDate = (value) => {
  if (!value) return 'date non renseignée';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleDateString('fr-FR');
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

  showAnnulerModal,
  onCloseAnnuler,
  onOpenAnnuler,
  onConfirmAnnuler,
  motifAnnulation,
  setMotifAnnulation,

  actionLoading,
  actionError
}) {
  const { hasRole } = useAuth();
  const canOverrideRemuneration = hasRole('ROLE_ADMIN');
  // État local : informations complémentaires demandées lors de la validation.
  const [justificatifFourni, setJustificatifFourni] = useState(false);
  const [estRemunere, setEstRemunere] = useState(null);
  const [derogationSoldeNegatif, setDerogationSoldeNegatif] = useState(false);
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [simulation, setSimulation] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState(false);
  const [typeDefaultRemuneration, setTypeDefaultRemuneration] = useState(null);

  useEffect(() => {
    let active = true;
    setTypeDefaultRemuneration(null);
    if (!selectedDemande?.idTypeConge) return () => { active = false; };
    congeService.getTypesConge(false).then((types) => {
      if (!active) return;
      const type = (Array.isArray(types) ? types : []).find(
        (item) => String(item.idTypeConge) === String(selectedDemande.idTypeConge)
      );
      if (type) setTypeDefaultRemuneration(defaultLeavePayable(type));
    }).catch(() => {
      // Le détail peut ne pas contenir le paramètre par défaut du type.
    });
    return () => { active = false; };
  }, [selectedDemande?.idTypeConge]);

  // Effets : réinitialise et charge le solde pour la demande sélectionnée.
  useEffect(() => {
    let isMounted = true;
    const fetchSolde = async () => {
      if (!selectedDemande?.idEmploye) {
        setSoldeEmploye(null);
        setSimulation(null);
        setPreviewLoading(false);
        setPreviewError(false);
        return;
      }
      setPreviewLoading(true);
      setPreviewError(false);
      const [soldeResult, simulationResult] = await Promise.allSettled([
        congeService.getSoldeEmploye(selectedDemande.idEmploye),
        congeService.simulerDemande({
          idEmploye: selectedDemande.idEmploye,
          idTypeConge: selectedDemande.idTypeConge,
          idMotifEvenementFamilial: selectedDemande.idMotifEvenementFamilial,
          dateDebut: typeof selectedDemande.dateDebut === 'string' ? selectedDemande.dateDebut.slice(0, 10) : selectedDemande.dateDebut,
          dateFin: typeof selectedDemande.dateFin === 'string' ? selectedDemande.dateFin.slice(0, 10) : selectedDemande.dateFin,
          estDemiJournee: Boolean(selectedDemande.estDemiJournee),
          derogationSoldeNegatif: Boolean(derogationSoldeNegatif),
          derogationDelaiPreavis: Boolean(selectedDemande.derogationDelaiPreavis),
          extensionPathologiqueMaternite: Boolean(selectedDemande.extensionPathologiqueMaternite),
          idDemandeConge: selectedDemande.idDemandeConge,
        }),
      ]);
      if (!isMounted) return;
      setSoldeEmploye(soldeResult.status === 'fulfilled' ? soldeResult.value : null);
      setSimulation(simulationResult.status === 'fulfilled' ? simulationResult.value : null);
      setPreviewError(soldeResult.status === 'rejected' || simulationResult.status === 'rejected');
      setPreviewLoading(false);
    };
    if (showValiderModal || showDetailModal) {
      fetchSolde();
    }
    return () => { isMounted = false; };
  }, [showValiderModal, showDetailModal, selectedDemande?.idDemandeConge, selectedDemande?.idEmploye, selectedDemande?.idTypeConge, selectedDemande?.dateDebut, selectedDemande?.dateFin, selectedDemande?.estDemiJournee, selectedDemande?.idMotifEvenementFamilial, selectedDemande?.derogationDelaiPreavis, selectedDemande?.extensionPathologiqueMaternite, derogationSoldeNegatif]);

  useEffect(() => {
    if (showValiderModal && selectedDemande) {
      setJustificatifFourni(Boolean(selectedDemande.justificatifFourni));
      setEstRemunere(selectedDemande.estRemunere == null ? null : Boolean(selectedDemande.estRemunere));
      setDerogationSoldeNegatif(Boolean(selectedDemande.derogationSoldeNegatif));
    }
  }, [showValiderModal, selectedDemande]);

  const joursDemandes = Number(selectedDemande?.nbJoursDecomptes ?? 0);
  const joursDemandesLabel = selectedDemande?.estDemiJournee
    ? `0,5 jour ${selectedDemande?.momentDemiJournee === 'APRES_MIDI' ? 'après-midi' : 'matin'}`
    : selectedDemande?.nbJoursDecomptes == null ? 'durée inconnue' : `${joursDemandes} jour${joursDemandes === 1 ? '' : 's'}`;
  const soldeAvant = soldeEmploye?.soldeRestant ?? soldeEmploye?.soldeActuel ?? selectedDemande?.soldeRestant ?? selectedDemande?.soldeActuel ?? null;
  const joursDebites = simulation?.joursDebites ?? null;
  const soldeApres = simulation?.soldeApres ?? null;
  const effectiveEstRemunere = estRemunere == null
    ? typeDefaultRemuneration ?? defaultLeavePayable({ estPaye: selectedDemande?.estPaye })
    : estRemunere;

  // Rendu
  return (
    <>
      {/* ── Modal : Detail demande (Délégué au composant unifié) ─────────── */}
      <DemandeDetailsModal
        isOpen={Boolean(showDetailModal && selectedDemande)}
        onClose={onCloseDetail}
        selectedDemande={selectedDemande}
        onOpenValider={onOpenValider}
        onOpenRefus={onOpenRefus}
        onOpenModifier={onOpenModifier}
        onOpenAnnuler={onOpenAnnuler}
      />


      {/* ── Confirmation validation ────────────────────────────────────────── */}
      <ConfirmDialog
        isOpen={showValiderModal}
        title="Confirmer la validation"
        message={`Valider la demande de ${selectedDemande?.prenom || ''} ${selectedDemande?.nom || ''} (${joursDemandesLabel} de ${selectedDemande?.libelleTypeConge || selectedDemande?.codeTypeConge || 'type non renseigné'}, du ${formatDate(selectedDemande?.dateDebut)} au ${formatDate(selectedDemande?.dateFin)}) ?`}
        confirmLabel="Valider la demande"
        variant="success"
        onConfirm={() => onConfirmValider(
          justificatifFourni,
          canOverrideRemuneration ? estRemunere : null,
          false,
          canOverrideRemuneration && selectedDemande?.estRemunere != null && estRemunere == null
        )}
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

          <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-2 text-xs">
            <p className="font-semibold text-[var(--color-fg)]">Conséquence de la décision</p>
            <p className="mt-0.5 text-[var(--color-fg-muted)]">
              {effectiveEstRemunere
                ? 'La demande validée sera rémunérée et valorisée selon les droits à congé applicables.'
                : 'La demande validée sera sans solde et entraînera une retenue d’absence en paie.'}
            </p>
          </div>
          {previewLoading && <p role="status" className="text-xs text-[var(--color-fg-muted)]">Actualisation du solde et de la simulation…</p>}
          {previewError && <Alert type="info" title="Aperçu partiellement indisponible" message="Une donnée de solde ou la simulation n’a pas pu être chargée. Les valeurs inconnues restent affichées comme indisponibles ; le serveur vérifiera la demande au moment de la décision." />}

          {/* Audit comptable solde dans la modale de validation */}
          <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 text-xs space-y-2">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block flex items-center gap-1.5">
              <Calculator size={13} className="text-[var(--color-brand-500)]" />
              Vérification du solde et impact paie
            </span>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Avant</span>
                <span className="font-mono font-bold text-xs text-[var(--color-fg)]">
                  {formatLeaveDaysPreview(soldeAvant, { loading: previewLoading, error: previewError })}
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
                  {formatLeaveDaysPreview(joursDebites > 0 ? -Number(joursDebites) : joursDebites, { loading: previewLoading, error: previewError })}
                </span>
              </div>
              <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                <span className="text-[10px] text-[var(--color-fg-muted)] block">Solde Après</span>
                <span className={`font-mono font-bold text-xs ${soldeApres != null && soldeApres < 0 ? 'text-[var(--color-danger-600)] font-bold' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                  {formatLeaveDaysPreview(soldeApres, { loading: previewLoading, error: previewError })}
                </span>
              </div>
            </div>
            {soldeApres != null && soldeApres < 0 && (
              <div className="text-[11px] text-[var(--color-danger-600)] font-semibold flex items-center gap-1 mt-1">
                <AlertTriangle size={12} />
                Solde insuffisant : cette demande ne peut pas être validée en congé payé.
              </div>
            )}
          </div>
          {canOverrideRemuneration && <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
            <input
              type="checkbox"
              checked={effectiveEstRemunere}
              onChange={(e) => setEstRemunere(e.target.checked)}
              className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
            />
            <span className="text-sm font-medium text-[var(--color-fg)]">
              Congé rémunéré (indemnisé sur les droits acquis)
              <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">
                {estRemunere == null && typeDefaultRemuneration == null
                  ? 'Valeur par défaut du type indisponible dans les données chargées.'
                  : effectiveEstRemunere
                  ? "Congé rémunéré : indemnité de congés payés versée selon la valorisation des droits acquis de l'employé."
                  : "Non rémunéré (congé sans solde) : retenue au taux d'absence sur le bulletin de paie."}
              </span>
            </span>
          </label>}
          {canOverrideRemuneration && estRemunere != null && (
            <button type="button" className="text-xs underline" onClick={() => setEstRemunere(null)}>
              Utiliser la valeur par défaut du type
            </button>
          )}
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
          {actionError && <FormError message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}
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
          {actionError && <FormError message={typeof actionError === 'string' ? actionError : JSON.stringify(actionError)} />}

          {selectedDemande?.codeStatutDemande === 'VALIDE' ? (
            <Alert
              type="warning"
              message={selectedDemande.estRemunere === false || selectedDemande.estPaye === false
                ? `Cette absence validée est sans solde. Son annulation ne recréditera aucun jour payé. Un motif justificatif est obligatoire pour la traçabilité.`
                : `Ce congé est validé. L'annulation va ré-incrémenter automatiquement ${selectedDemande.nbJoursDecomptes || selectedDemande.dureeJours || 0} jour(s) sur le solde acquis de ${selectedDemande.prenom || ''} ${selectedDemande.nom || ''}. Un motif justificatif est obligatoire pour la traçabilité.`}
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
