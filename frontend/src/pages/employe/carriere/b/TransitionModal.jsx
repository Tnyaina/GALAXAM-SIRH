import { useEffect } from 'react';
import { RefreshCw, ShieldAlert, Save, X, Eye, EyeOff, FileSignature, Building2, MessageSquareText } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';

// Hook Logique
import { useTransitionForm } from '@/hooks/carriere/useTransitionForm';

// Sections UI
import FormSectionHeader from '@/components/carriere/form/FormSectionHeader';
import TransitionTypeSection from '@/components/carriere/form/TransitionTypeSection';
import ContratFieldsSection from '@/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/components/carriere/form/AffectationFieldsSection';
import PeriodeEssaiFieldsSection from '@/components/carriere/form/PeriodeEssaiFieldsSection';
import AvenantPreviewDocument from '@/components/carriere/preview/AvenantPreviewDocument';

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
    const {
        formData, setField,
        submitting, errorMsg,
        showPreviewMobile, setShowPreviewMobile,
        mounted, exigeNouveauContrat, autorisePeriodeEssai, isEditMode,
        loadingRef, handleSubmit,
        relevantEvTypes, typeEvObj,
        avantTypeContrat, apresTypeContrat, avantSite, apresSite,
        avantDepartement, apresDepartement, avantService, apresService,
        avantFonction, apresFonction, avantCategorie, apresCategorie,
        avantResponsable, apresResponsable, avantModePaie, apresModePaie,
        avantSalaire,
        filiales, typesContrat, typesIntervenant, sites, departements,
        services, fonctions, categoriesPro, modesPaie, responsables
    } = useTransitionForm({
        isOpen, onClose, idEmploye, typeEvenement,
        affectationActive, contratActif, initialData, onSuccess
    });

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    const stepContrat = 2;
    const stepAffectation = exigeNouveauContrat ? 3 : 2;
    const stepMotif = exigeNouveauContrat ? 4 : 3;

    return (
        <div 
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            className={`fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-2 sm:p-4 overflow-y-auto transition-opacity duration-200 ${mounted ? 'opacity-100' : 'opacity-0'}`}
        >
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-[1440px] max-h-[95vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-7 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between bg-ink-50/50 dark:bg-ink-950/40">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-brand-500 text-white rounded-none flex items-center justify-center shadow-md">
                            <RefreshCw size={20} />
                        </div>
                        <div>
                            <h2 className="font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                                {isEditMode ? 'Corriger le Brouillon de Transition' : (typeEvObj?.libelle || 'Transition de Carrière')}
                                <span className="text-xs px-2 py-0.5 bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] border border-[var(--color-warning-500)]/20 font-mono rounded-none">
                                    BROUILLON
                                </span>
                            </h2>
                            <p className="text-xs text-ink-400">
                                Mouvement RH pour <strong className="text-ink-800 dark:text-white">{nomEmploye}</strong>
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setShowPreviewMobile(!showPreviewMobile)}
                            className="lg:hidden p-2 text-xs font-semibold bg-ink-100 dark:bg-ink-800 text-ink-700 dark:text-ink-200 rounded-none flex items-center gap-1"
                        >
                            {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                            {showPreviewMobile ? 'Masquer Aperçu' : 'Aperçu'}
                        </button>
                        <button onClick={onClose} className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-none cursor-pointer">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Body split 2 colonnes */}
                <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-ink-150 dark:divide-ink-700">
                    {/* Formulaire (6/12) */}
                    <div className={`p-4 sm:p-5 space-y-4 lg:col-span-6 ${showPreviewMobile ? 'hidden lg:block' : 'block'}`}>
                        {loadingRef ? (
                            <Loading display="page" label="Chargement des référentiels..." />
                        ) : (
                            <form id="transition-form" onSubmit={handleSubmit} className="space-y-4">
                                {errorMsg && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-none p-3 text-xs flex items-center gap-2">
                                        <ShieldAlert size={16} />
                                        {errorMsg}
                                    </div>
                                )}
                                
                                <TransitionTypeSection 
                                    relevantEvTypes={relevantEvTypes} 
                                    formData={formData} 
                                    setField={setField} 
                                />

                                {/* Nouveau Contrat si nécessaire */}
                                {exigeNouveauContrat && (
                                    <section className="space-y-5 pb-6 border-b border-ink-150 dark:border-ink-700">
                                        <FormSectionHeader step={stepContrat} icon={FileSignature} title="Nouveau Contrat" />
                                        
                                        <div className="bg-brand-50 dark:bg-brand-950/40 p-3.5 border border-brand-200 dark:border-brand-800/60 rounded-none text-xs text-brand-800 dark:text-brand-300 flex items-start gap-2.5">
                                            <RefreshCw size={16} className="shrink-0 mt-0.5 text-brand-600 dark:text-brand-400" />
                                            <div>
                                                <p className="font-bold">Clôture et Chaînage Automatique du Contrat Précédent</p>
                                                <p className="text-[11px] text-brand-700/80 dark:text-brand-300/80 mt-0.5">
                                                    La validation de ce mouvement clôturera automatiquement le contrat actuel et activera ce nouveau contrat tout en préservant l'ancienneté historique du collaborateur.
                                                </p>
                                            </div>
                                        </div>

                                        <ContratFieldsSection
                                            formData={formData}
                                            setField={setField}
                                            filiales={filiales}
                                            typesContrat={typesContrat}
                                            typesIntervenant={typesIntervenant}
                                        />

                                        {autorisePeriodeEssai ? (
                                            <PeriodeEssaiFieldsSection
                                                formData={formData}
                                                setField={setField}
                                            />
                                        ) : (
                                            <div className="bg-ink-50 dark:bg-ink-950/60 p-3.5 border border-ink-150 dark:border-ink-800 text-xs text-ink-500 dark:text-ink-400 flex items-center gap-2">
                                                <span className="font-bold text-ink-700 dark:text-ink-200">Période d'essai :</span>
                                                <span>Non applicable sur ce type de mouvement (ancienneté et compétences déjà validées).</span>
                                            </div>
                                        )}
                                    </section>
                                )}

                                {/* Affectation Carrière */}
                                <section className="space-y-5 pb-6 border-b border-ink-150 dark:border-ink-700">
                                    <FormSectionHeader step={stepAffectation} icon={Building2} title="Affectation" />
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

                                {/* Motif */}
                                <section className="space-y-3">
                                    <FormSectionHeader step={stepMotif} icon={MessageSquareText} title="Motif du Mouvement" />
                                    <Textarea
                                        label="Motif / Justification du Mouvement"
                                        rows={2}
                                        value={formData.motif}
                                        onChange={(e) => setField('motif', e.target.value)}
                                        placeholder="Motif détaillé de la promotion ou mutation..."
                                    />
                                </section>
                            </form>
                        )}
                    </div>

                    {/* Aperçu Avenant (6/12) */}
                    <div className={`p-7 bg-ink-50/50 dark:bg-ink-950/30 lg:col-span-6 overflow-y-auto space-y-4 ${showPreviewMobile ? 'block' : 'hidden lg:block'}`}>
                        <div className="flex items-center justify-between pb-2 border-b border-ink-200 dark:border-ink-700">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-ink-500 dark:text-ink-400 flex items-center gap-2">
                                <FileSignature size={14} className="text-brand-500" />
                                Aperçu de l'Avenant RH
                            </h3>
                        </div>

                        <AvenantPreviewDocument
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
                    </div>
                </div>

                {/* Footer Boutons */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-between items-center">
                    <p className="text-xs text-ink-400 hidden sm:block">
                        Le mouvement RH reste au statut <strong className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]">BROUILLON</strong> tant qu'il n'est pas validé.
                    </p>
                    <div className="flex gap-3 ml-auto">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            form="transition-form"
                            variant="primary"
                            size="sm"
                            loading={submitting}
                            iconLeft={<Save size={16} />}
                        >
                            {isEditMode ? 'Enregistrer les modifications' : 'Créer le Brouillon de Transition'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}