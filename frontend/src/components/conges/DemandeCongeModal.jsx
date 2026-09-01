import { Calendar } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import { useToast } from '@/components/ui/feedback/Toast';
import { Textarea } from '@/components/ui/forms/Input';

// Hooks
import { useDemandeCongeForm } from '@/hooks/conges/useDemandeCongeForm';

// Sections UI
import TypeCongeSection from './formulaire/TypeCongeSection';
import DatesCongeSection from './formulaire/DatesCongeSection';
import OptionsRHSection from './formulaire/OptionsRHSection';

export default function DemandeCongeModal({ idEmploye: idEmployeProp, initialDemande, referentiels, isOpen, onClose, onSuccess }) {
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
    extensionPathologiqueMaternite, setExtensionPathologiqueMaternite,
    showOptionsAvancees, setShowOptionsAvancees,

    // Variables calculées
    nbJoursDecomptes, nbJoursWeekEnd, nbJoursOuvres,
    loading, error, fieldErrors, setFieldErrors,
    
    typesConge, motifsFamiliaux, selectedType,
    isExceptionnel, isMaternite,
    
    // Méthodes
    handleSubmit, inputErrorClass
  } = useDemandeCongeForm({ idEmployeProp, initialDemande, referentiels, isOpen, onClose, onSuccess });

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 bg-slate-900/60 dark:bg-black/80 backdrop-blur-xs z-50 flex items-center justify-center p-4">
      <div className="bg-white dark:bg-ink-900 border border-slate-200 dark:border-ink-700 max-w-2xl w-full shadow-2xl max-h-[90vh] flex flex-col font-sans">

        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-100 dark:border-ink-800 shrink-0">
          <h3 className="text-sm font-bold text-slate-900 dark:text-white flex items-center gap-2">
            <Calendar className="w-4 h-4 text-brand-500" />
            {initialDemande?.idDemandeConge ? `Modifier la demande #${initialDemande.idDemandeConge}` : 'Nouvelle demande de congé ou d\'absence'}
          </h3>
          <button
            onClick={onClose}
            aria-label="Fermer"
            className="text-slate-400 hover:text-slate-700 dark:hover:text-white text-xl leading-none w-8 h-8 flex items-center justify-center cursor-pointer"
          >
            ×
          </button>
        </div>

        <form onSubmit={(e) => handleSubmit(e, toast)} className="flex flex-col overflow-hidden flex-1">
          <div className="px-6 py-5 space-y-6 overflow-y-auto text-sm">

            {/* Message d'erreur global */}
            {error && (
              <Alert type="error" message={typeof error === 'string' ? error : JSON.stringify(error)} />
            )}

            {/* ── Section 0 : Employé concerné ────────────────────── */}
            {!idEmployeProp && (
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

            {/* ── Section 1 : Type de congé ────────────────────────────── */}
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

            {/* ── Section 2 : Dates ────────────────────────────────────── */}
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
              fieldErrors={fieldErrors}
              setFieldErrors={setFieldErrors}
            />

            {/* ── Section 3 : Justificatif & commentaire ─────────────────── */}
            <div className="space-y-4 pt-1 border-t border-[var(--color-border-subtle)] mt-4">
              
              {selectedType?.exigeJustificatif && (
                <Alert type="warning" message="Un justificatif est requis pour ce type de congé (peut être fourni au retour si imprévu)." />
              )}

              <label className="flex items-start gap-3 pt-4 cursor-pointer">
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

            {/* ── Section 4 : Options avancées RH ─────────────────────────── */}
            <OptionsRHSection
              showOptionsAvancees={showOptionsAvancees}
              setShowOptionsAvancees={setShowOptionsAvancees}
              derogationSoldeNegatif={derogationSoldeNegatif}
              setDerogationSoldeNegatif={setDerogationSoldeNegatif}
              derogationDelaiPreavis={derogationDelaiPreavis}
              setDerogationDelaiPreavis={setDerogationDelaiPreavis}
            />
          </div>

          {/* Actions — fixées en bas, toujours visibles */}
          <div className="flex items-center justify-between gap-3 px-6 py-4 border-t border-[var(--color-border-subtle)] shrink-0 bg-[var(--color-surface)]">
            <span className="text-xs text-[var(--color-fg-subtle)]">
              <span className="text-danger-500">*</span> Champs obligatoires
            </span>
            <div className="flex items-center gap-3">
              <Button type="button" variant="ghost" size="sm" onClick={onClose} className="rounded-none">
                Annuler
              </Button>
              <Button
                type="submit"
                variant="primary"
                size="sm"
                loading={loading}
                className="rounded-none font-bold"
              >
                {loading ? 'Enregistrement…' : initialDemande?.idDemandeConge ? 'Enregistrer les modifications' : 'Soumettre la demande'}
              </Button>
            </div>
          </div>
        </form>
      </div>
    </div>
  );
}