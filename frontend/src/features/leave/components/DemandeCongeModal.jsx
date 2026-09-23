import { Calendar, ChevronDown, ShieldAlert } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import { useToast } from '@/components/ui/feedback/Toast';
import { Textarea } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';

// Hooks
import { useDemandeCongeForm } from '@/features/leave/hooks/useDemandeCongeForm';

// Sections UI
import DatesCongeSection from './formulaire/DatesCongeSection';
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
    conflitReport, simulationReport, checkingConflits,
    loading, error, fieldErrors, setFieldErrors,
    
    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,
    
    // Méthodes
    handleSubmit
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
                  .map((type) => ({ value: type.idTypeConge, label: type.libelle, sublabel: `${type.estPaye ? 'Rémunéré' : 'Sans solde'}${type.soumisDelaiPreavis ? ' · Préavis requis' : ''}` }))}
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
                    .map((motif) => ({ value: motif.idMotif, label: motif.libelle, sublabel: `${motif.nbJoursAutorises} jours autorisés` }))}
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

            {/* Section 4 : options RH propres à ce formulaire. */}
            <div className="border border-[var(--color-border-subtle)] mt-4">
              <button type="button" onClick={() => setShowOptionsAvancees((value) => !value)} aria-expanded={showOptionsAvancees} className="w-full flex items-center justify-between px-4 py-3 text-left">
                <span className="text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] flex items-center gap-1.5"><ShieldAlert className="w-4 h-4 text-warning-500" />Options avancées — RH uniquement</span>
                <ChevronDown className={`w-4 h-4 text-[var(--color-fg-muted)] transition-transform ${showOptionsAvancees ? 'rotate-180' : ''}`} />
              </button>
              {showOptionsAvancees && (
                <div className="px-4 pb-4 pt-1 border-t border-[var(--color-border-subtle)] space-y-3">
                  {setEstRemunere && <label className="flex items-start gap-3 cursor-pointer p-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]"><input type="checkbox" checked={estRemunere ?? true} onChange={(event) => setEstRemunere(event.target.checked)} className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0" /><span className="text-sm font-medium text-[var(--color-fg)]">Congé rémunéré (indemnisé sur les droits acquis)<span className="block text-xs text-[var(--color-fg-muted)] mt-0.5 font-normal">Coché : l'absence donne droit à l'indemnité légale de congés payés. Décoché : congé sans solde.</span></span></label>}
                  <label className="flex items-start gap-3 cursor-pointer"><input type="checkbox" checked={derogationSoldeNegatif} onChange={(event) => setDerogationSoldeNegatif(event.target.checked)} className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0" /><span className="text-sm text-[var(--color-fg)]">Autoriser le dépassement de solde (prise par anticipation)<span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">Autorise la demande même si le solde actuel est insuffisant ou si l'ancienneté est &lt; 1 an.</span></span></label>
                  <label className="flex items-start gap-3 cursor-pointer"><input type="checkbox" checked={derogationDelaiPreavis} onChange={(event) => setDerogationDelaiPreavis(event.target.checked)} className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0" /><span className="text-sm text-[var(--color-fg)]">Dérogation de préavis<span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">Autorise le dépôt ou la validation si le délai de préavis habituel n'est pas respecté.</span></span></label>
                  <label className="flex items-start gap-3 cursor-pointer"><input type="checkbox" checked={derogationContinuiteService} onChange={(event) => setDerogationContinuiteService(event.target.checked)} className="w-4 h-4 mt-0.5 accent-[var(--color-brand-500)] cursor-pointer shrink-0" /><span className="text-sm text-[var(--color-fg)]">Continuité d'activité : autoriser les absences simultanées<span className="block text-xs text-[var(--color-fg-muted)] mt-0.5">Autorise le congé même si d'autres collaborateurs occupant le même poste sont déjà absents.</span></span></label>
                </div>
              )}
            </div>
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
