import { Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import { useToast } from '@/components/ui/feedback/Toast';
import { Textarea } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';

// Hooks
import { useDemandeCongeForm } from '@/features/leave/hooks/useDemandeCongeForm';

// Sections UI
import TypeCongeSection from './formulaire/TypeCongeSection';
import DatesCongeSection from './formulaire/DatesCongeSection';
import OptionsRHSection from './formulaire/OptionsRHSection';
import ConflitsPostePreview from './formulaire/ConflitsPostePreview';

export default function DemandeCongeModal({ 
  idEmploye: idEmployeProp, 
  initialDemande, 
  initialDateDebut, 
  initialDateFin, 
  referentiels, 
  isOpen, 
  onClose, 
  onSuccess 
}) {
  const toast = useToast();

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
    nbJoursDecomptes, nbJoursWeekEnd, nbJoursOuvres,
    conflitReport, checkingConflits,
    loading, error, fieldErrors, setFieldErrors,
    
    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,
    
    // Méthodes
    handleSubmit, inputErrorClass
  } = useDemandeCongeForm({ 
    idEmployeProp, 
    initialDemande, 
    initialDateDebut, 
    initialDateFin, 
    referentiels, 
    isOpen, 
    onClose, 
    onSuccess 
  });

  if (!isOpen) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={initialDemande?.idDemandeConge 
        ? (initialDemande.codeStatutDemande === 'VALIDE' 
            ? `Modifier les dates du congé validé #${initialDemande.idDemandeConge}` 
            : `Modifier la demande #${initialDemande.idDemandeConge}`) 
        : "Nouvelle demande de congé ou d'absence"}
      icon={<Calendar className="w-5 h-5 text-brand-500" />}
      size="full"
      className="max-w-[1380px] w-[95vw]"
      footer={
        <div className="flex items-center justify-between w-full">
          <span className="text-xs text-[var(--color-fg-subtle)]">
            <span className="text-danger-500">*</span> Champs obligatoires
          </span>
          <div className="flex items-center gap-3">
            <Button type="button" variant="ghost" size="sm" onClick={onClose} className="rounded-none">
              Annuler
            </Button>
            <Button
              type="button"
              variant="primary"
              size="sm"
              loading={loading}
              onClick={(e) => handleSubmit(e, toast)}
              className="rounded-none font-bold"
            >
              {loading ? 'Enregistrement…' : initialDemande?.idDemandeConge ? 'Enregistrer les modifications' : 'Soumettre la demande'}
            </Button>
          </div>
        </div>
      }
    >
      <form onSubmit={(e) => handleSubmit(e, toast)} className="text-sm">
        {/* Message d'erreur global */}
        {error && (
          <div className="mb-4">
            <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
          </div>
        )}

        {/* Layout 2 Colonnes : Formulaire ciblé (5 cols) et Grand Aperçu Gantt (7 cols) */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
          {/* Colonne Gauche (Formulaire de saisie) */}
          <div className="lg:col-span-5 space-y-4">
            {/* Section 0 : Employé concerné */}
            {!idEmployeProp && (
              initialDemande ? (
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between">
                  <div>
                    <span className="text-[10px] text-[var(--color-fg-subtle)] block uppercase font-bold tracking-wider">Employé concerné</span>
                    <span className="font-semibold text-sm text-[var(--color-fg)]">
                      {initialDemande.prenom} {initialDemande.nom} {initialDemande.matricule ? `(${initialDemande.matricule})` : ''}
                    </span>
                  </div>
                  {initialDemande.codeStatutDemande && (
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
              )
            )}

            {/* Section 1 : Type de congé */}
            <TypeCongeSection
              idTypeConge={idTypeConge}
              setIdTypeConge={setIdTypeConge}
              idMotifEvenementFamilial={idMotifEvenementFamilial}
              setIdMotifEvenementFamilial={setIdMotifEvenementFamilial}
              typesConge={typesConge}
              motifsFamiliaux={motifsFamiliaux}
              isExceptionnel={isExceptionnel}
              fieldErrors={fieldErrors}
              setFieldErrors={setFieldErrors}
              inputErrorClass={inputErrorClass}
            />

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
              selectedType={selectedType}
              isMaternite={isMaternite}
              extensionPathologiqueMaternite={extensionPathologiqueMaternite}
              setExtensionPathologiqueMaternite={setExtensionPathologiqueMaternite}
              preavisInfo={preavisInfo}
              derogationDelaiPreavis={derogationDelaiPreavis}
              setDerogationDelaiPreavis={setDerogationDelaiPreavis}
              conflitReport={conflitReport}
              derogationSoldeNegatif={derogationSoldeNegatif}
              setDerogationSoldeNegatif={setDerogationSoldeNegatif}
              setShowOptionsAvancees={setShowOptionsAvancees}
              fieldErrors={fieldErrors}
              setFieldErrors={setFieldErrors}
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

            {/* Section 4 : Options avancées RH */}
            <OptionsRHSection
              showOptionsAvancees={showOptionsAvancees}
              setShowOptionsAvancees={setShowOptionsAvancees}
              derogationSoldeNegatif={derogationSoldeNegatif}
              setDerogationSoldeNegatif={setDerogationSoldeNegatif}
              derogationDelaiPreavis={derogationDelaiPreavis}
              setDerogationDelaiPreavis={setDerogationDelaiPreavis}
              derogationContinuiteService={derogationContinuiteService}
              setDerogationContinuiteService={setDerogationContinuiteService}
              estRemunere={estRemunere}
              setEstRemunere={setEstRemunere}
            />
          </div>

          {/* Colonne Droite (Grand Aperçu Gantt & Continuité de Service) */}
          <div className="lg:col-span-7 sticky top-2">
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
  );
}