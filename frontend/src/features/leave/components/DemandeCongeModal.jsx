import { useState } from 'react';
import { Calendar, ChevronDown, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import Badge from '@/components/ui/data-display/Badge';
import { useToast } from '@/components/ui/feedback/Toast';
import { Textarea } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';
import { useAuth } from '@/context/AuthContext';
import { defaultLeavePayable } from '@/features/leave/utils/defaultLeavePayable';

// Hooks
import { useDemandeCongeForm } from '@/features/leave/hooks/useDemandeCongeForm';

// Sections UI
import DatesCongeSection from './formulaire/DatesCongeSection';
import ConflitsPostePreview from './formulaire/ConflitsPostePreview';
import SimulationValorisationConge from './formulaire/SimulationValorisationConge';

export default function DemandeCongeModal({
  idEmploye: idEmployeProp,
  initialDemande,
  initialDateDebut,
  initialDateFin,
  employeeContext,
  referentiels,
  isOpen,
  onClose,
  onSuccess
}) {
  const toast = useToast();
  const [showCancelConfirm, setShowCancelConfirm] = useState(false);
  const { hasPermission, hasRole } = useAuth();
  const canWriteForOthers = hasPermission('CONGE_WRITE');
  const canOverrideRemuneration = hasRole('ROLE_ADMIN');

  const {
    // États et setters
    selectedEmployeId, setSelectedEmployeId,
    idTypeConge, setIdTypeConge,
    idMotifEvenementFamilial, setIdMotifEvenementFamilial,
    dateDebut, setDateDebut,
    dateFin, setDateFin,
    estDemiJournee, setEstDemiJournee,
    momentDemiJournee, setMomentDemiJournee,
    commentaire, setCommentaire,
    justificatifFourni, setJustificatifFourni,

    derogationSoldeNegatif, setDerogationSoldeNegatif,
    derogationDelaiPreavis, setDerogationDelaiPreavis,
    derogationContinuiteService, setDerogationContinuiteService,
    extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
    estRemunere, setEstRemunere,
    showOptionsAvancees, setShowOptionsAvancees,
    preavisInfo,

    // Variables calculées & Conflits
    nbJoursDecomptes, nbJoursWeekEnd, nbJoursOuvres, nbJoursFeries,
    conflitReport, simulationReport, simulationError, checkingConflits,
    simulationLotsReport, simulationLotsError, simulationLotsLoading,
    loading, error, fieldErrors, setFieldErrors,

    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,

    // Méthodes
    handleSubmit
  } = useDemandeCongeForm({
    idEmployeProp,
    canOverrideRemuneration,
    initialDemande,
    initialDateDebut,
    initialDateFin,
    referentiels,
    isOpen,
    onClose,
    onSuccess
  });

  const isDirty = Boolean(dateDebut || dateFin || commentaire);
  useUnsavedChanges(isOpen && isDirty, 'Une demande de congé est en cours de rédaction.');

  if (!isOpen) return null;

  return (
    <>
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={initialDemande?.idDemandeConge
        ? (initialDemande.codeStatutDemande === 'VALIDE'
          ? `Modifier les dates du congé #${initialDemande.idDemandeConge}`
          : `Modifier la demande #${initialDemande.idDemandeConge}`)
        : "Nouvelle demande de congé ou d'absence"}
      icon={<Calendar className="w-5 h-5 text-[var(--color-brand-500)]" />}
      size="full"
      className="max-w-5xl w-full"
      closeOnBackdrop={false}
      isDirty={isDirty}
      dirtyWarningTitle="Demande de congé en cours"
      dirtyWarningMessage="Une demande de congé est en cours de rédaction. Si vous quittez sans valider, vos informations seront perdues. Voulez-vous vraiment quitter ?"
      footer={
        <div className="flex items-center justify-between w-full">
          <span className="text-xs text-[var(--color-fg-subtle)]">
            <span className="text-[var(--color-danger-500)]">*</span> Champs obligatoires
          </span>
          <div className="flex items-center gap-3">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              onClick={() => {
                if (isDirty) {
                  setShowCancelConfirm(true);
                } else {
                  onClose();
                }
              }}
            >
              Annuler
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              loading={loading}
              onClick={(e) => handleSubmit(e, toast)}
              className="font-semibold"
            >
              {loading ? 'Enregistrement...' : initialDemande?.idDemandeConge ? 'Enregistrer les modifications' : 'Soumettre la demande'}
            </Button>
          </div>
        </div>
      }
    >
      <form onSubmit={(e) => handleSubmit(e, toast)} className="text-sm">
        {/* Message d'erreur global */}
        {error && (
          <div className="mb-4">
            <FormError message={typeof error === 'string' ? error : JSON.stringify(error)} />
          </div>
        )}
        {/* Erreur de simulation backend (TCONGE_APPLICABLE_AMBIGUOUS, etc.) */}
        {simulationError && !error && (
          <div className="mb-4">
            <Alert type="warning" message={simulationError} />
          </div>
        )}

        {/* Layout 2 Colonnes équilibré : Formulaire (7 cols) et Continuité de service (5 cols) */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-start">
          {/* Colonne Gauche (Formulaire de saisie) */}
          <div className="lg:col-span-7 space-y-4">
            {/* Section 0 : Employé concerné */}
            {(idEmployeProp || initialDemande || employeeContext || !canWriteForOthers) ? (
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between">
                  <div>
                    <span className="text-[11px] text-[var(--color-fg-muted)] block font-medium">Employé concerné</span>
                    <span className="font-semibold text-sm text-[var(--color-fg)]">
                      {employeeContext?.nom || `${initialDemande?.prenom || ''} ${initialDemande?.nom || ''}`.trim() || (canWriteForOthers ? 'Employé sélectionné' : 'Mon dossier')}
                      {(employeeContext?.matricule || initialDemande?.matricule) ? ` (${employeeContext?.matricule || initialDemande.matricule})` : ''}
                    </span>
                  </div>
                  {initialDemande?.codeStatutDemande && (
                    <Badge variant={initialDemande.codeStatutDemande === 'VALIDE' ? 'success' : 'brand'} size="xs">
                      {initialDemande.libelleStatutDemande || initialDemande.codeStatutDemande}
                    </Badge>
                  )}
                </div>
              ) : (
                <EmployeSearchSelect
                  label="Employé concerné"
                  required
                  value={selectedEmployeId}
                  onChange={(empId) => {
                    setSelectedEmployeId(empId);
                    setFieldErrors((prev) => ({ ...prev, idEmploye: null }));
                  }}
                  error={fieldErrors.idEmploye}
                />
              )}

            {/* Section 1 : type de congé et, si nécessaire, motif familial. */}
            <div className="space-y-1.5">
              <SearchSelect
                label="Type de congé / absence"
                required
                value={idTypeConge}
                onChange={(value) => {
                  setIdTypeConge(value);
                  setIdMotifEvenementFamilial('');
                  setFieldErrors((current) => ({ ...current, idTypeConge: null }));
                }}
                options={typesConge
                  .filter((type) => type.estActif !== false && type.actif !== false || String(type.idTypeConge) === String(idTypeConge))
                  .map((type) => ({
                    value: type.idTypeConge,
                    // Le code est interne; l'interface montre uniquement le libellé métier.
                    label: type.libelle || type.libelleTypeConge || String(type.idTypeConge),
                    sublabel: `${type.estPaye ? 'Rémunéré' : 'Sans solde'}${type.soumisDelaiPreavis ? ' · Préavis requis' : ''}`
                  }))}
                placeholder="Sélectionner un type de congé…"
                error={fieldErrors.idTypeConge}
              />
            </div>
            {isExceptionnel && (
              <div className="pl-3 border-l-2 border-[var(--color-warning-500)] space-y-1.5 mt-4">
                <SearchSelect
                  label="Motif d'événement familial"
                  required
                  value={idMotifEvenementFamilial}
                  onChange={(value) => {
                    setIdMotifEvenementFamilial(value);
                    setFieldErrors((current) => ({ ...current, idMotifEvenementFamilial: null }));
                  }}
                  options={motifsFamiliaux
                    .filter((motif) => motif.estActif !== false && motif.actif !== false || String(motif.idMotif) === String(idMotifEvenementFamilial))
                    .map((motif) => ({
                      value: motif.idMotif,
                      label: motif.libelle || motif.libelleMotif || motif.code
                    }))}
                  placeholder="Sélectionner un motif…"
                  error={fieldErrors.idMotifEvenementFamilial}
                />
              </div>
            )}

            {/* Section 2 : Dates */}
            <DatesCongeSection
              dateDebut={dateDebut}
              setDateDebut={setDateDebut}
              dateFin={dateFin}
              setDateFin={setDateFin}
              estDemiJournee={estDemiJournee}
              setEstDemiJournee={setEstDemiJournee}
              momentDemiJournee={momentDemiJournee}
              setMomentDemiJournee={setMomentDemiJournee}
              nbJoursDecomptes={nbJoursDecomptes}
              nbJoursOuvres={nbJoursOuvres}
              nbJoursWeekEnd={nbJoursWeekEnd}
              nbJoursFeries={nbJoursFeries}
              selectedType={selectedType}
              isMaternite={isMaternite}
              extensionPathologiqueMaternite={extensionPathologiqueMaternite}
              setExtensionPathologiqueMaternite={setExtensionPathologiqueMaternite}
              preavisInfo={preavisInfo}
              derogationDelaiPreavis={derogationDelaiPreavis}
              setDerogationDelaiPreavis={setDerogationDelaiPreavis}
              simulationReport={simulationReport}
              derogationSoldeNegatif={derogationSoldeNegatif}
              setDerogationSoldeNegatif={setDerogationSoldeNegatif}
              setShowOptionsAvancees={setShowOptionsAvancees}
              fieldErrors={fieldErrors}
              setFieldErrors={setFieldErrors}
            />

            <SimulationValorisationConge
              report={simulationLotsReport}
              error={simulationLotsError}
              loading={simulationLotsLoading}
            />

            {/* Section 3 : Justificatif & commentaire */}
            <div className="space-y-4 pt-1 border-t border-[var(--color-border-subtle)] mt-4">
              {selectedType?.exigeJustificatif && (
                <Alert type="warning" message="Un justificatif est requis pour ce type de congé (peut être fourni au retour si imprévu)." />
              )}

              <label className="flex items-start gap-3 pt-2 cursor-pointer">
                <input
                  type="checkbox"
                  id="justificatifFourni"
                  checked={justificatifFourni}
                  onChange={(e) => setJustificatifFourni(e.target.checked)}
                  className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                />
                <span className="text-sm text-[var(--color-fg)]">
                  Justificatif fourni
                  <span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">
                    Certificat médical, acte d'état civil, convocation, etc.
                  </span>
                </span>
              </label>

              <div className="space-y-1.5">
                <label className="block text-sm font-semibold text-[var(--color-fg)]">
                  Commentaire <span className="text-[var(--color-fg-subtle)] font-normal">(optionnel)</span>
                </label>
                <Textarea
                  rows="2"
                  placeholder="Remarques éventuelles…"
                  value={commentaire}
                  onChange={(e) => setCommentaire(e.target.value)}
                />
              </div>
            </div>

            {/* Section 4 : options avancées */}
            <div className="border border-[var(--color-border-subtle)] mt-4">
              <button
                type="button"
                onClick={() => setShowOptionsAvancees((value) => !value)}
                aria-expanded={showOptionsAvancees}
                className="w-full flex items-center justify-between px-3.5 py-2.5 text-left hover:bg-[var(--color-bg-subtle)] transition-colors"
              >
                <span className="text-xs font-semibold text-[var(--color-fg-muted)] flex items-center gap-1.5">
                  <ShieldAlert className="w-3.5 h-3.5 text-[var(--color-warning-500)]" />
                  Options avancées
                </span>
                <ChevronDown className={`w-3.5 h-3.5 text-[var(--color-fg-muted)] transition-transform ${showOptionsAvancees ? 'rotate-180' : ''}`} />
              </button>
              {showOptionsAvancees && (
                <div className="px-3.5 pb-3 pt-1 border-t border-[var(--color-border-subtle)] space-y-2.5 bg-[var(--color-surface)]">
                  {canOverrideRemuneration && (
                    <label className="flex items-start gap-2.5 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                      <input
                        type="checkbox"
                        checked={estRemunere == null ? defaultLeavePayable(selectedType) : estRemunere}
                        onChange={(event) => setEstRemunere(event.target.checked)}
                        className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0"
                      />
                      <span className="text-xs font-medium text-[var(--color-fg)]">
                        Congé rémunéré
                        <span className="block text-[11px] text-[var(--color-fg-muted)] mt-0.5 font-normal">
                          {estRemunere == null
                            ? `Valeur par défaut du type : ${defaultLeavePayable(selectedType) ? 'rémunéré' : 'sans solde'}. Cochez pour définir explicitement une autre valeur.`
                            : 'Valeur définie explicitement par un administrateur.'}
                        </span>
                      </span>
                    </label>
                  )}
                  {canOverrideRemuneration && estRemunere != null && (
                    <button type="button" className="text-xs underline" onClick={() => setEstRemunere(null)}>
                      Utiliser la valeur par défaut du type
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>

          {/* Colonne Droite (Continuité de Service & Aperçu Planning) */}
          <div className="lg:col-span-5 sticky top-2">
            <ConflitsPostePreview
              conflitReport={conflitReport}
              checkingConflits={checkingConflits}
              dateDebut={dateDebut}
              dateFin={dateFin}
              estDemiJournee={estDemiJournee}
              momentDemiJournee={momentDemiJournee}
              derogationContinuiteService={derogationContinuiteService}
              setDerogationContinuiteService={setDerogationContinuiteService}
            />
          </div>
        </div>
      </form>
    </Modal>

    <ConfirmDialog
      isOpen={showCancelConfirm}
      onClose={() => setShowCancelConfirm(false)}
      onConfirm={() => {
        setShowCancelConfirm(false);
        onClose();
      }}
      title="Demande de congé en cours"
      message="Une demande de congé est en cours de rédaction. Si vous quittez sans enregistrer, toutes vos saisies seront perdues."
      confirmLabel="Abandonner la demande"
      cancelLabel="Continuer la saisie"
      variant="danger"
    />
    </>
  );
}
