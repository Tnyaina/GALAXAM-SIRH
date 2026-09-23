import { lazy, Suspense } from 'react';
import Loading from '@/components/ui/feedback/Loading';

// Chargement différé (lazy loading) des 9 modales de carrière pour réduire la taille du bundle initial
const EmbaucheModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/EmbaucheModal'));
const TransitionModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/TransitionModal'));
const WorkflowDraftSummaryModal = lazy(() => import('@/features/employees/components/carriere/modals/WorkflowDraftSummaryModal'));
const ConfirmationEssaiModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/ConfirmationEssaiModal'));
const RenouvellementEssaiModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/RenouvellementEssaiModal'));
const RuptureContratModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/RuptureContratModal'));
const CertificatTravailModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/CertificatTravailModal'));
const AttestationTravailModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/AttestationTravailModal'));
const ContratTravailModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/ContratTravailModal'));
const StcPreparationModal = lazy(() => import('@/features/payroll/components/modals/stc/StcPreparationModal'));

export default function CarriereModalCoordinator({
    // Données de base
    idEmploye, employe, nomEmploye, matriculeEmploye,
    contratActif, affectationActive, workflowCplActif, contratsList, brouillonWf,
    etatEssai, canWrite = true, canValidate = false,
    
    // États et Hooks
    modalsState,
}) {
    const {
        isTransitioning, editInitialData, selectedWfModal, setSelectedWfModal,
        isEmbaucheModalOpen, setIsEmbaucheModalOpen,
        isTransitionModalOpen, setIsTransitionModalOpen,
        isRecapModalOpen, setIsRecapModalOpen,
        isRecapCancelMode, setIsRecapCancelMode,
        isConfirmationModalOpen, setIsConfirmationModalOpen,
        isRenouvellementModalOpen, setIsRenouvellementModalOpen,
        isRuptureModalOpen, setIsRuptureModalOpen,
        isCertificatModalOpen, setIsCertificatModalOpen,
        selectedCertificatContrat, selectedCertificatAffectation, loadingCertificatAffectation,
        isAttestationModalOpen, setIsAttestationModalOpen,
        selectedAttestationContrat, selectedAttestationAffectation, loadingAttestationAffectation,
        isContratModalOpen, setIsContratModalOpen,
        selectedContratDoc, selectedContratAffectation, loadingContratAffectation,
        isStcModalOpen, setIsStcModalOpen,
        selectedStcContrat,
        handleSuccessAction, handleOpenEditModal, handleOpenStc
    } = modalsState;

    return (
        <Suspense fallback={null}>
            {isEmbaucheModalOpen && (
                <EmbaucheModal
                    isOpen={isEmbaucheModalOpen}
                    onClose={() => setIsEmbaucheModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratsList={contratsList}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon d\'embauche enregistré avec succès !', res)}
                />
            )}

            {isRecapModalOpen && (
                <WorkflowDraftSummaryModal
                    isOpen={isRecapModalOpen}
                    onClose={() => {
                        setIsRecapModalOpen(false);
                        setSelectedWfModal(null);
                        if (setIsRecapCancelMode) setIsRecapCancelMode(false);
                    }}
                    workflowCpl={selectedWfModal || brouillonWf}
                    initialCancelMode={isRecapCancelMode}
                    canWrite={canWrite}
                    canValidate={canValidate}
                    onEdit={(wf) => handleOpenEditModal(wf)}
                    onActionSuccess={(msg) => handleSuccessAction(msg)}
                />
            )}

            {isTransitionModalOpen && (
                <TransitionModal
                    isOpen={isTransitionModalOpen}
                    onClose={() => setIsTransitionModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    workflowActif={workflowCplActif}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de transition enregistré avec succès !', res)}
                />
            )}

            {isConfirmationModalOpen && (
                <ConfirmationEssaiModal
                    isOpen={isConfirmationModalOpen}
                    onClose={() => setIsConfirmationModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    etatEssai={etatEssai}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de confirmation d\'essai enregistré avec succès !', res)}
                />
            )}

            {isRenouvellementModalOpen && (
                <RenouvellementEssaiModal
                    isOpen={isRenouvellementModalOpen}
                    onClose={() => setIsRenouvellementModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    etatEssai={etatEssai}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de renouvellement d\'essai enregistré avec succès !', res)}
                />
            )}

            {isRuptureModalOpen && (
                <RuptureContratModal
                    isOpen={isRuptureModalOpen}
                    onClose={() => setIsRuptureModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    contratActif={contratActif}
                    affectationActive={affectationActive}
                    workflowActif={workflowCplActif}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon de rupture et fin de contrat créé avec succès !', res)}
                    onOpenStc={(stcParams) => {
                        setIsRuptureModalOpen(false);
                        handleOpenStc(stcParams);
                    }}
                />
            )}

            {isCertificatModalOpen && (
                <CertificatTravailModal
                    isOpen={isCertificatModalOpen}
                    onClose={() => setIsCertificatModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedCertificatContrat || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    contratsList={contratsList}
                    affectation={selectedCertificatAffectation}
                    loadingAffectation={loadingCertificatAffectation}
                    filiale={selectedCertificatContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={
                        contratsList?.length > 0
                            ? [...contratsList].sort((a, b) => new Date(a.dateDebut || 0) - new Date(b.dateDebut || 0))[0]?.dateDebut
                            : selectedCertificatContrat?.dateDebut
                    }
                />
            )}

            {isAttestationModalOpen && (
                <AttestationTravailModal
                    isOpen={isAttestationModalOpen}
                    onClose={() => setIsAttestationModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedAttestationContrat || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    contratsList={contratsList}
                    affectation={selectedAttestationAffectation || affectationActive}
                    loadingAffectation={loadingAttestationAffectation}
                    filiale={selectedAttestationContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={
                        contratsList?.length > 0
                            ? [...contratsList].sort((a, b) => new Date(a.dateDebut || 0) - new Date(b.dateDebut || 0))[0]?.dateDebut
                            : selectedAttestationContrat?.dateDebut
                    }
                />
            )}

            {isContratModalOpen && (
                <ContratTravailModal
                    isOpen={isContratModalOpen}
                    onClose={() => setIsContratModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedContratDoc || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    affectation={selectedContratAffectation || affectationActive}
                    loadingAffectation={loadingContratAffectation}
                    filiale={selectedContratDoc?.filiale || contratActif?.filiale}
                />
            )}

            {isStcModalOpen && (
                <StcPreparationModal
                    isOpen={isStcModalOpen}
                    onClose={() => setIsStcModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    dateDepartInitiale={selectedStcContrat?.dateDepart || selectedStcContrat?.dateFin || null}
                    motifInitial={selectedStcContrat?.motifRupture || (selectedStcContrat?.dateFin ? 'FIN_CONTRAT' : 'DEMISSION')}
                    moisPreavisInitial={selectedStcContrat?.moisPreavis ?? null}
                    indemnitePreavisInitiale={selectedStcContrat?.indemnitePreavis ?? null}
                    onStcValide={() => {
                        handleSuccessAction('Solde de Tout Compte validé avec succès !');
                    }}
                />
            )}

            {/* Overlay de chargement fluide */}
            {isTransitioning && (
                <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-ink-950/70 backdrop-blur-sm">
                    <div className="flex flex-col items-center gap-4 p-6 bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-800 rounded-none shadow-2xl max-w-sm w-full text-center">
                        <Loading size="md" label="Enregistrement en cours..." />
                        <p className="text-[11px] text-ink-400 -mt-2">
                            Préparation du récapitulatif de validation...
                        </p>
                    </div>
                </div>
            )}
        </Suspense>
    );
}
