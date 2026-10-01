import { RefreshCw, Save, FileSignature, Building2, MessageSquareText } from 'lucide-react';
import FormError from '@/components/ui/feedback/FormError';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import SlideOver from '@/components/ui/overlays/SlideOver';

// Hook Logique
import { useTransitionForm } from '@/features/employees/hooks/useTransitionForm';

// Sections UI
import FormSectionHeader from '@/features/employees/components/carriere/form/FormSectionHeader';
import TransitionTypeSection from '@/features/employees/components/carriere/form/TransitionTypeSection';
import ContratFieldsSection, { PeriodeEssaiFieldsSection } from '@/features/employees/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/features/employees/components/carriere/form/AffectationFieldsSection';

import TransitionComparatifCard from '@/features/employees/components/carriere/preview/TransitionComparatifCard';

export default function TransitionModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    typeEvenement = null,
    affectationActive = null,
    contratActif = null,
    initialData = null,
    onSuccess,
}) {
    // Contexte : référentiels et états requis pour la transition.
    const {
        formData, setField,
        submitting, errorMsg,
        exigeNouveauContrat, autorisePeriodeEssai, isEditMode,
        loadingRef, handleSubmit,
        relevantEvTypes, typeEvObj,
        avantTypeContrat, apresTypeContrat, avantSite, apresSite,
        avantDepartement, apresDepartement, avantService, apresService,
        avantFonction, apresFonction, avantCategorie, apresCategorie,
        avantResponsable, apresResponsable, avantModePaie, apresModePaie,
        avantSalaire,
        filiales, typesContrat, typesIntervenant, sites, departements,
        services, fonctions, categoriesPro, modesPaie, responsables,
        lockTypeContrat, lockTypeIntervenant
    } = useTransitionForm({
        isOpen, onClose, idEmploye, typeEvenement,
        affectationActive, contratActif, initialData, onSuccess
    });

    if (!isOpen) return null;

    const stepContrat = 2;
    const stepAffectation = exigeNouveauContrat ? 3 : 2;
    const stepMotif = exigeNouveauContrat ? 4 : 3;

    const slideOverFooter = (
        <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-xs text-[var(--color-fg-muted)]">
                Enregistrement en brouillon · validation requise
            </p>
            <div className="flex flex-col-reverse gap-2 sm:flex-row sm:items-center">
                <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                    Annuler
                </Button>
                <Button
                    type="submit"
                    form="transition-form"
                    variant="primary"
                    size="sm"
                    loading={submitting}
                    iconLeft={<Save size={15} />}
                >
                    {isEditMode ? 'Enregistrer les modifications' : 'Créer le brouillon'}
                </Button>
            </div>
        </div>
    );

    return (
        <SlideOver
            isOpen={isOpen}
            onClose={onClose}
            title={isEditMode ? 'Modifier le brouillon d\'évolution' : (typeEvObj?.libelle || 'Évolution de situation')}
            subtitle={`Mouvement RH pour ${nomEmploye}`}
            icon={<RefreshCw size={18} />}
            width="3xl"
            footer={slideOverFooter}
        >
            {loadingRef ? (
                <Loading display="page" label="Chargement des référentiels..." />
            ) : (
                <form id="transition-form" onSubmit={handleSubmit} className="space-y-5">
                    {errorMsg && <FormError message={errorMsg} />}

                    {/* Section 1 : Type de transition */}
                    <TransitionTypeSection 
                        relevantEvTypes={relevantEvTypes} 
                        formData={formData} 
                        setField={setField} 
                        exigeNouveauContrat={exigeNouveauContrat}
                    />

                    {typeEvObj && (
                        <Alert
                            type="info"
                            title="Effet du mouvement"
                            message={exigeNouveauContrat
                                ? "À la validation, le contrat actuel sera clôturé et le nouveau contrat activé. L'ancienneté est conservée."
                                : "Le contrat actuel est conservé. Le mouvement porte sur l'affectation ou la rémunération."}
                        />
                    )}

                    {/* Section 2 (si requise) : Nouveau Contrat */}
                    {exigeNouveauContrat && (
                        <section className="space-y-4 pb-5 border-b border-[var(--color-border-subtle)]">
                            <FormSectionHeader step={stepContrat} icon={FileSignature} title="Nouveau contrat" />
                            
                            <ContratFieldsSection
                                formData={formData}
                                setField={setField}
                                filiales={filiales}
                                typesContrat={typesContrat}
                                typesIntervenant={typesIntervenant}
                                lockTypeContrat={lockTypeContrat}
                                lockTypeIntervenant={lockTypeIntervenant}
                                signatureOptional
                                dateDebutLabel="Début du nouveau contrat"
                                dateDebutHint="Cette date détermine aussi la prise d'effet du mouvement."
                            />

                            {autorisePeriodeEssai ? (
                                <PeriodeEssaiFieldsSection
                                    formData={formData}
                                    setField={setField}
                                />
                            ) : (
                                <div className="bg-[var(--color-bg-subtle)] p-3 border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] flex items-center gap-2">
                                    <span className="font-bold text-[var(--color-fg)]">Période d'essai :</span>
                                    <span>Non applicable sur ce type de mouvement.</span>
                                </div>
                            )}
                        </section>
                    )}

                    {/* Section Affectation & Poste */}
                    <section className="space-y-4 pb-5 border-b border-[var(--color-border-subtle)]">
                        <FormSectionHeader step={stepAffectation} icon={Building2} title="Affectation & Poste" />
                        <AffectationFieldsSection
                            formData={formData}
                            setField={setField}
                            sites={sites}
                            departements={departements}
                            services={services}
                            fonctions={fonctions}
                            categoriesPro={categoriesPro}
                            modesPaie={modesPaie}
                            responsables={responsables}
                        />
                    </section>

                    {/* Section Motif */}
                    <section className="space-y-3 pb-5 border-b border-[var(--color-border-subtle)]">
                        <FormSectionHeader step={stepMotif} icon={MessageSquareText} title="Motif de l'évolution" />
                        <Textarea
                            label="Motif / Justification"
                            rows={2}
                            value={formData.motif}
                            onChange={(e) => setField('motif', e.target.value)}
                            placeholder="Précisez le motif de l'évolution de situation..."
                        />
                    </section>

                    {/* Section Synthèse comparative Avant / Après */}
                    <section className="space-y-3 pt-1">
                        <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            Synthèse différentielle (Avant ➔ Après)
                        </h4>
                        <TransitionComparatifCard
                            typeEvenementActuel={typeEvObj}
                            nomEmploye={nomEmploye}
                            formData={formData}
                            exigeNouveauContrat={exigeNouveauContrat}
                            avantTypeContrat={avantTypeContrat}
                            apresTypeContrat={apresTypeContrat}
                            avantSite={avantSite}
                            apresSite={apresSite}
                            avantDepartement={avantDepartement}
                            apresDepartement={apresDepartement}
                            avantService={avantService}
                            apresService={apresService}
                            avantFonction={avantFonction}
                            apresFonction={apresFonction}
                            avantCategorie={avantCategorie}
                            apresCategorie={apresCategorie}
                            avantResponsable={avantResponsable}
                            apresResponsable={apresResponsable}
                            avantModePaie={avantModePaie}
                            apresModePaie={apresModePaie}
                            avantSalaire={avantSalaire}
                        />
                    </section>
                </form>
            )}
        </SlideOver>
    );
}
