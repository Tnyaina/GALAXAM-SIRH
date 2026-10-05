import { useState, useEffect } from 'react';
import { 
  FileText, X, Check, Pencil, Calculator, Minus, Equal, AlertCircle, ShieldAlert 
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import congeService from '@/features/leave/api/congeService';

const formatDate = (dateVal) => {
  if (!dateVal) return '-';
  const d = new Date(dateVal);
  if (isNaN(d.getTime())) return String(dateVal);
  return d.toLocaleDateString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric'
  });
};

export default function DemandeDetailsModal({
  isOpen,
  onClose,
  demande,
  selectedDemande,
  _canWrite = false,
  onEdit,
  onOpenModifier,
  onCancel,
  onOpenAnnuler,
  onOpenValider,
  onOpenRefus,
}) {
  const targetDemande = demande || selectedDemande;
  const [soldeEmploye, setSoldeEmploye] = useState(null);
  const [simulation, setSimulation] = useState(null);

  useEffect(() => {
    let isMounted = true;
    if (!isOpen || !targetDemande?.idEmploye) {
      setSoldeEmploye(null);
      setSimulation(null);
      return;
    }

    const fetchSolde = async () => {
      try {
        const res = await congeService.getSoldeEmploye(targetDemande.idEmploye);
        if (isMounted) setSoldeEmploye(res);
      } catch {
        if (isMounted) setSoldeEmploye(null);
      }
      try {
        const result = await congeService.simulerDemande({
          idEmploye: targetDemande.idEmploye,
          idTypeConge: targetDemande.idTypeConge,
          idMotifEvenementFamilial: targetDemande.idMotifEvenementFamilial,
          dateDebut: typeof targetDemande.dateDebut === 'string' ? targetDemande.dateDebut.slice(0, 10) : targetDemande.dateDebut,
          dateFin: typeof targetDemande.dateFin === 'string' ? targetDemande.dateFin.slice(0, 10) : targetDemande.dateFin,
          estDemiJournee: Boolean(targetDemande.estDemiJournee),
          derogationSoldeNegatif: Boolean(targetDemande.derogationSoldeNegatif),
          derogationDelaiPreavis: Boolean(targetDemande.derogationDelaiPreavis),
          extensionPathologiqueMaternite: Boolean(targetDemande.extensionPathologiqueMaternite),
        });
        if (isMounted) setSimulation(result);
      } catch {
        if (isMounted) setSimulation(null);
      }
    };

    fetchSolde();
    return () => { isMounted = false; };
  }, [
    isOpen,
    targetDemande?.idEmploye,
    targetDemande?.idTypeConge,
    targetDemande?.dateDebut,
    targetDemande?.dateFin,
    targetDemande?.estDemiJournee,
    targetDemande?.idMotifEvenementFamilial,
    targetDemande?.derogationSoldeNegatif,
    targetDemande?.derogationDelaiPreavis,
    targetDemande?.extensionPathologiqueMaternite,
  ]);

  if (!targetDemande) return null;

  const joursDemandes = Number(targetDemande.nbJoursDecomptes ?? 0);
  const soldeAvant = soldeEmploye?.soldeRestant ?? soldeEmploye?.soldeActuel ?? targetDemande.soldeRestant ?? targetDemande.soldeActuel ?? null;
  const joursDebites = simulation?.joursDebites ?? null;
  const soldeApres = simulation?.soldeApres ?? null;
  const prefixeRegularisation = '[REGULARISATION_POINTAGE]';
  const estRegularisationPointage = Boolean(targetDemande.isRegularisationPointage || targetDemande.commentaire?.includes(prefixeRegularisation));
  const commentaireAffiche = targetDemande.commentaire
    ?.replace(/\[REGULARISATION_POINTAGE\]\s*/gi, '')
    .trim();

  const estRemunere = targetDemande.estRemunere !== undefined && targetDemande.estRemunere !== null
    ? Boolean(targetDemande.estRemunere)
    : targetDemande.estPaye !== undefined
      ? Boolean(targetDemande.estPaye)
      : true;

  // Calcul du solde prévisionnel robuste (évite le "Non calculé" quand le nombre de jours décomptés est déjà connu)
  const effectifsJoursDebites = joursDebites != null
    ? Number(joursDebites)
    : (estRemunere ? joursDemandes : 0);

  const effectifSoldeApres = soldeApres != null
    ? Number(soldeApres)
    : (soldeAvant != null ? Math.max(0, Number(soldeAvant) - effectifsJoursDebites) : null);

  const hasDerogations = Boolean(
    targetDemande.derogationSoldeNegatif ||
    targetDemande.derogationDelaiPreavis ||
    targetDemande.derogationContinuiteService ||
    targetDemande.extensionPathologiqueMaternite ||
    targetDemande.suspendAcquisitionSolde
  );

  const handleEdit = onOpenModifier || onEdit;
  const handleCancel = onOpenAnnuler || onCancel;

  const isValide = targetDemande.codeStatutDemande === 'VALIDE';
  const canApprove = targetDemande.availableActions?.APPROVE?.allowed === true && !!onOpenValider;
  const canReject = targetDemande.availableActions?.REJECT?.allowed === true && !!onOpenRefus;
  const canModifyDates = targetDemande.availableActions?.MODIFY_DATES?.allowed === true && !!handleEdit;
  const canEdit = targetDemande.availableActions?.EDIT?.allowed === true && !!handleEdit;
  const canCancel = targetDemande.availableActions?.CANCEL?.allowed === true && !!handleCancel;

  // Règle ergonomique :
  // Sur une demande en attente examinée par un valideur/manager, les actions décisionnelles sont Valider et Refuser.
  // "Annuler ce congé" est réservé aux congés déjà validés (isValide) ou au retrait par l'employé lorsqu'il n'a pas les droits de validation.
  const showCancelAction = canCancel && (isValide || (!canApprove && !canReject));

  return (
    <Modal
      isOpen={isOpen && !!targetDemande}
      onClose={onClose}
      title={`Demande de congé #${targetDemande.idDemandeConge || ''}`}
      icon={<FileText size={20} />}
      size="xl"
      footer={
        <div className="flex w-full flex-wrap items-center justify-between gap-2">
          <div>
            {showCancelAction && (
              <Button
                variant="danger"
                size="sm"
                iconLeft={<X size={14} />}
                onClick={() => {
                  onClose();
                  handleCancel(targetDemande);
                }}
              >
                {isValide ? 'Annuler ce congé' : 'Annuler la demande'}
              </Button>
            )}
          </div>
          <div className="flex flex-wrap items-center gap-2">
            {/* Si congé validé : modification des dates uniquement */}
            {isValide && canModifyDates && (
              <Button
                variant="outline"
                size="sm"
                iconLeft={<Pencil size={14} />}
                onClick={() => {
                  onClose();
                  handleEdit(targetDemande);
                }}
              >
                Modifier les dates
              </Button>
            )}

            {/* Décision valideur / DAF */}
            {canApprove && (
              <>
                {canEdit && (
                  <Button
                    variant="outline"
                    size="sm"
                    iconLeft={<Pencil size={14} />}
                    onClick={() => {
                      onClose();
                      handleEdit(targetDemande);
                    }}
                  >
                    Modifier
                  </Button>
                )}
                {canReject && (
                  <Button
                    variant="danger"
                    size="sm"
                    iconLeft={<X size={14} />}
                    onClick={() => {
                      onClose();
                      onOpenRefus(targetDemande);
                    }}
                  >
                    Refuser
                  </Button>
                )}
                <Button
                  variant="success"
                  size="sm"
                  iconLeft={<Check size={14} />}
                  onClick={() => {
                    onClose();
                    onOpenValider(targetDemande);
                  }}
                >
                  Valider
                </Button>
              </>
            )}

            {/* Hors validation et non validé : modification simple */}
            {!canApprove && !isValide && canEdit && (
              <Button
                variant="primary"
                size="sm"
                iconLeft={<Pencil size={14} />}
                onClick={() => {
                  onClose();
                  handleEdit(targetDemande);
                }}
              >
                Modifier
              </Button>
            )}

            <Button variant="ghost" size="sm" onClick={onClose}>
              Fermer
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4">
        {/* Identité + statut */}
        <div className="flex flex-wrap items-start justify-between gap-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3">
          <div className="min-w-0">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Collaborateur</span>
            <h3 className="text-base font-bold text-[var(--color-fg)]">
              {`${targetDemande.prenom || ''} ${targetDemande.nom || ''}`.trim() || `Employé #${targetDemande.idEmploye}`}
            </h3>
            <div className="mt-1 flex flex-wrap items-center gap-2">
              {targetDemande.matricule && <span className="text-xs text-[var(--color-fg-subtle)] font-data">Matricule {targetDemande.matricule}</span>}
              <span className="text-xs font-medium text-[var(--color-fg-muted)]">{targetDemande.libelleTypeConge || targetDemande.codeTypeConge}</span>
              {estRegularisationPointage && <Badge variant="warning" size="xs">Régularisation de pointage</Badge>}
            </div>
          </div>
          <StatusBadge
            status={targetDemande.codeStatutDemande}
            label={targetDemande.libelleStatutDemande}
            size="md"
          />
        </div>

        {/* Grille dates */}
        <div className="grid grid-cols-2 gap-x-6 gap-y-4">
          {[
            ['Date de début',      formatDate(targetDemande.dateDebut)],
            ['Date de fin',        formatDate(targetDemande.dateFin)],
            ['Jours décomptés', targetDemande.estDemiJournee ? `0,5 jour (${targetDemande.momentDemiJournee === 'APRES_MIDI' ? 'Après-midi' : 'Matin'})` : `${targetDemande.nbJoursDecomptes} jours`],
            ['Date de soumission', formatDate(targetDemande.dateDemande)],
          ].map(([label, value]) => (
            <div key={label}>
              <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">{label}</span>
              <p className="font-data text-sm font-semibold text-[var(--color-fg)] mt-0.5">{value}</p>
            </div>
          ))}
        </div>

        {/* Motif familial */}
        {targetDemande.libelleMotifEvenement && (
          <div className="p-3 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/5 text-xs text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-semibold">
            Motif d'événement familial : {targetDemande.libelleMotifEvenement}
          </div>
        )}

        {/* Commentaire */}
        {commentaireAffiche && (
          <div className="space-y-1 pt-2 border-t border-[var(--color-border-subtle)]">
            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Commentaire de l'employé</span>
            <p className="text-xs text-[var(--color-fg)] bg-[var(--color-bg-subtle)] p-3 border border-[var(--color-border-subtle)]">
              {commentaireAffiche}
            </p>
          </div>
        )}

        {/* Motif de refus si refusé */}
        {(targetDemande.codeStatutDemande === 'REFUSE' || targetDemande.isRefuse) && (
          <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 space-y-1">
            <div className="flex items-center gap-2 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold text-xs">
              <AlertCircle size={15} />
              <span>Demande refusée</span>
            </div>
            <p className="text-xs text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] pl-6">
              <strong>Motif :</strong> {targetDemande.motifRefus || 'Aucun motif renseigné.'}
            </p>
          </div>
        )}

        {/* Vérification du solde */}
        <section aria-label="Vérification du solde de congés payés" className="space-y-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 text-xs">
          <h4 className="flex items-center gap-1.5 text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
            <Calculator size={13} className="text-[var(--color-brand-500)]" /> Vérification du solde
          </h4>
          <div className="grid grid-cols-1 items-center gap-2 sm:grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)_auto_minmax(0,1fr)]">
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 text-center">
              <span className="block text-[10px] text-[var(--color-fg-muted)]">Solde avant</span>
              <span className="font-mono text-xs font-bold text-[var(--color-fg)]">
                {soldeAvant != null ? `${Number(soldeAvant).toFixed(2)} j` : '—'}
              </span>
            </div>
            <Minus size={14} className="hidden text-[var(--color-fg-muted)] sm:block" aria-hidden="true" />
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 text-center">
              <span className="block text-[10px] text-[var(--color-fg-muted)]">Jours débités</span>
              <span className="font-mono text-xs font-bold text-[var(--color-danger-600)]">
                {effectifsJoursDebites != null ? `${Number(effectifsJoursDebites).toFixed(2)} j` : '0.00 j'}
              </span>
            </div>
            <Equal size={14} className="hidden text-[var(--color-fg-muted)] sm:block" aria-hidden="true" />
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 text-center">
              <span className="block text-[10px] text-[var(--color-fg-muted)]">Solde après</span>
              <span className={`font-mono text-xs font-bold ${effectifSoldeApres != null && effectifSoldeApres < 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                {effectifSoldeApres != null ? `${Number(effectifSoldeApres).toFixed(2)} j` : '—'}
              </span>
            </div>
          </div>
          <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1 border-t border-[var(--color-border-subtle)] pt-2 text-[11px] text-[var(--color-fg-subtle)]">
            <span>Jours demandés : <strong className="font-mono text-[var(--color-fg)]">{Number(joursDemandes).toFixed(2)} j</strong></span>
            <span>Impact Paie : <strong>{estRemunere ? 'Congé Rémunéré (TCONGE valorisé)' : 'Sans Solde (Retenue absence)'}</strong></span>
            {soldeEmploye?.tauxJournalierConge && (
              <span>TCONGE : <strong className="font-mono">{Number(soldeEmploye.tauxJournalierConge).toLocaleString('fr-FR')} Ar/j</strong></span>
            )}
          </div>
        </section>

        {/* Particularités & Dérogations RH */}
        {hasDerogations && (
          <div className="space-y-1.5 p-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
            <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
              <ShieldAlert size={12} className="text-[var(--color-warning-500)]" />
              Particularités réglementaires accordées
            </span>
            <div className="flex flex-wrap gap-1.5 pt-1">
              {targetDemande.derogationSoldeNegatif && (
                <Badge variant="warning" size="xs">Dérogation solde négatif</Badge>
              )}
              {targetDemande.derogationDelaiPreavis && (
                <Badge variant="warning" size="xs">Dérogation préavis</Badge>
              )}
              {targetDemande.derogationContinuiteService && (
                <Badge variant="warning" size="xs">Dérogation continuité service</Badge>
              )}
              {targetDemande.extensionPathologiqueMaternite && (
                <Badge variant="danger" size="xs">Extension pathologique</Badge>
              )}
              {targetDemande.suspendAcquisitionSolde && (
                <Badge variant="default" size="xs">Suspension acquisition solde</Badge>
              )}
            </div>
          </div>
        )}

        {/* Justificatif */}
        <div className="flex items-center justify-between border-t border-[var(--color-border-subtle)] pt-3 text-xs">
          <span className="text-[var(--color-fg-muted)] font-medium">Justificatif fourni :</span>
          <Badge variant={targetDemande.justificatifFourni ? 'success' : 'neutral'} size="xs">
            {targetDemande.justificatifFourni ? 'OUI' : 'NON'}
          </Badge>
        </div>
      </div>
    </Modal>
  );
}
