import { lazy, Suspense, useMemo } from 'react';
import Loading from '@/components/ui/feedback/Loading';

// Chargement différé (lazy loading) des 9 modales de carrière pour réduire la taille du bundle initial
const EmbaucheModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/EmbaucheModal'));
const TransitionModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/TransitionModal'));
const WorkflowDraftSummaryModal = lazy(() => import('@/features/employees/components/carriere/modals/WorkflowDraftSummaryModal'));
const ArbitrageEssaiModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/ArbitrageEssaiModal'));
const RuptureContratModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/RuptureContratModal'));
const DocumentOfficielModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/DocumentOfficielModal'));
const ContratTravailModal = lazy(() => import('@/features/employees/components/carriere/modals/workflow/ContratTravailModal'));
const StcPreparationModal = lazy(() => import('@/features/payroll/components/modals/stc/StcPreparationModal'));

export default function CarriereModalCoordinator({
    // Données de base
    idEmploye, employe, nomEmploye, matriculeEmploye,
    contratActif, affectationActive, workflowCplActif, contratsList, brouillonWf,
    etatEssai, canWrite = true, canValidate = false,
    refreshData,
    onContractUpdated,
    
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
    const dateDebutInitialeContrats = useMemo(() => {
        if (!contratsList?.length) return null;
        return contratsList.reduce((earliest, contrat) => {
            const date = contrat?.dateDebut;
            return date && (!earliest || new Date(date) < new Date(earliest)) ? date : earliest;
        }, null);
    }, [contratsList]);

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
                <ArbitrageEssaiModal
                    isOpen={isConfirmationModalOpen}
                    onClose={() => setIsConfirmationModalOpen(false)}
                    mode="CONFIRMATION"
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
                <ArbitrageEssaiModal
                    isOpen={isRenouvellementModalOpen}
                    onClose={() => setIsRenouvellementModalOpen(false)}
                    mode="RENOUVELLEMENT"
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
                <DocumentOfficielModal
                    isOpen={isCertificatModalOpen}
                    onClose={() => setIsCertificatModalOpen(false)}
                    typeDocument="CERTIFICAT"
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedCertificatContrat || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    contratsList={contratsList}
                    affectation={selectedCertificatAffectation}
                    loadingAffectation={loadingCertificatAffectation}
                    filiale={selectedCertificatContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={dateDebutInitialeContrats || selectedCertificatContrat?.dateDebut}
                />
            )}

            {isAttestationModalOpen && (
                <DocumentOfficielModal
                    isOpen={isAttestationModalOpen}
                    onClose={() => setIsAttestationModalOpen(false)}
                    typeDocument="ATTESTATION"
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedAttestationContrat || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    contratsList={contratsList}
                    affectation={selectedAttestationAffectation || affectationActive}
                    loadingAffectation={loadingAttestationAffectation}
                    filiale={selectedAttestationContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={dateDebutInitialeContrats || selectedAttestationContrat?.dateDebut}
                />
            )}

            {isContratModalOpen && (
                <ContratTravailModal
                    isOpen={isContratModalOpen}
                    onClose={() => setIsContratModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedContratDoc || contratActif || (contratsList?.length > 0 ? contratsList[0] : null)}
                    affectation={selectedContratAffectation}
                    onEdit={canWrite && brouillonWf && Number(brouillonWf.idContratCible) === Number(selectedContratDoc?.idContrat) ? () => { setIsContratModalOpen(false); handleOpenEditModal(brouillonWf); } : undefined}
                    loadingAffectation={loadingContratAffectation}
                    filiale={selectedContratDoc?.filiale || contratActif?.filiale}
                    onContractUpdated={onContractUpdated || refreshData}
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
                    modePreavisInitial={selectedStcContrat?.modePreavis ?? null}
                    moisPreavisInitial={selectedStcContrat?.moisPreavis ?? null}
                    indemnitePreavisInitiale={selectedStcContrat?.indemnitePreavis ?? null}
                    onStcValide={() => {
                        handleSuccessAction('Solde de tout compte validé avec succès !');
                    }}
                />
            )}

            {/* Overlay de chargement fluide */}
            {isTransitioning && (
                <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-[var(--color-overlay)]/60 backdrop-blur-xs">
                    <div className="flex flex-col items-center gap-4 p-6 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-2xl max-w-sm w-full text-center">
                        <Loading size="md" label="Enregistrement en cours..." />
                        <p className="text-[11px] text-[var(--color-fg-subtle)] -mt-2">
                            Préparation du récapitulatif de validation...
                        </p>
                    </div>
                </div>
            )}
        </Suspense>
    );
}
