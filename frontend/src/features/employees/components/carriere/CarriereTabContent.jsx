import { useToast } from '@/components/ui/feedback/Toast';
import Loading from '@/components/ui/feedback/Loading';
import Alert from '@/components/ui/feedback/Alert';

// Hooks
import { useCarriere } from '@/features/employees/hooks/useCarriere';
import { useCarriereModals } from '@/features/employees/hooks/useCarriereModals';

// Composants de vue
import CarriereHeaderCard from '@/features/employees/components/carriere/CarriereHeaderCard';
import CarriereTimeline from '@/features/employees/components/carriere/CarriereTimeline';
import CarriereModalCoordinator from '@/features/employees/components/carriere/modals/CarriereModalCoordinator';

// Utilitaires
import { trouverWorkflowPlanifieFutur } from '@/features/employees/utils/carriereWorkflowUtils';
import { getTodayDateString } from '@/utils/dateUtils';

export default function CarriereTab({
    idEmploye,
    employe,
    nomEmploye,
    matriculeEmploye,
    canWrite = true,
    canValidate = false,
    autoOpenWorkflowId = null,
    onEmployeUpdated,
}) {
    const toast = useToast();
    
    const {
        loading, errorMsg, brouillonWf, contratActif, affectationActive,
        contratsList, workflowsList, etatEssai, refreshData,
    } = useCarriere(idEmploye);

    const modalsState = useCarriereModals({
        idEmploye, contratActif, affectationActive, contratsList, workflowsList,
        brouillonWf, loading, autoOpenWorkflowId, refreshData, onEmployeUpdated
    });

    const workflowsValidesDuContrat = contratActif
        ? workflowsList
            .filter((wf) => Boolean(wf.isActif) && (
                Number(wf.idContratCible) === Number(contratActif.idContrat) || 
                (Number(wf.idContratSource) === Number(contratActif.idContrat) && !wf.idContratCible)
            ))
            .sort((a, b) => new Date(b.dateEffet || b.dateDemande || 0) - new Date(a.dateEffet || a.dateDemande || 0))
        : [];
    const workflowCplActif = workflowsValidesDuContrat[0] || null;

    const todayStr = getTodayDateString();
    const workflowPlanifieFutur = trouverWorkflowPlanifieFutur(workflowsList, contratActif?.idContrat, todayStr);
    const estEssaiRenouveleFlag = etatEssai?.estRenouvele;

    if (loading) {
        return (
            <div className="py-16 flex justify-center">
                <Loading size="md" label="Chargement du dossier carrière & contrat..." />
            </div>
        );
    }

    return (
        <div className="space-y-3.5">
            {/* Header carte contrat + alerte période d'essai */}
            <CarriereHeaderCard
                employe={employe}
                contratActif={contratActif}
                affectationActive={affectationActive}
                brouillonWf={brouillonWf}
                etatEssai={etatEssai}
                workflowPlanifieFutur={workflowPlanifieFutur}
                estEssaiRenouveleFlag={estEssaiRenouveleFlag}
                canWrite={canWrite}
                onRefresh={() => {
                    refreshData();
                    if (toast) toast({ title: 'Actualisé', message: 'Les données de la carrière ont été mises à jour.', type: 'info', duration: 2000 });
                }}
                onOpenConfirmation={modalsState.handleOpenConfirmationModal}
                onOpenRenouvellement={modalsState.handleOpenRenouvellementModal}
                onOpenTransition={() => modalsState.handleOpenTransitionModal()}
                onOpenRupture={() => modalsState.handleOpenRuptureModal()}
                onOpenRecap={() => modalsState.setIsRecapModalOpen(true)}
            />

            {/* Messages de Succès / Erreur */}
            {errorMsg && <Alert type="error" message={errorMsg} />}

            {/* Timeline, bannières brouillon/mouvement et tables d'historique */}
            <CarriereTimeline
                contratActif={contratActif}
                affectationActive={affectationActive}
                workflowCplActif={workflowCplActif}
                workflowsList={workflowsList}
                contratsList={contratsList}
                brouillonWf={brouillonWf}
                workflowPlanifieFutur={workflowPlanifieFutur}
                etatEssai={etatEssai}
                estEssaiRenouveleFlag={estEssaiRenouveleFlag}
                canWrite={canWrite}
                onOpenCreateEmbauche={modalsState.handleOpenCreateModal}
                onOpenTransition={modalsState.handleOpenTransitionModal}
                onOpenRupture={modalsState.handleOpenRuptureModal}
                onOpenConfirmation={modalsState.handleOpenConfirmationModal}
                onOpenRenouvellement={modalsState.handleOpenRenouvellementModal}
                onOpenEditModal={modalsState.handleOpenEditModal}
                onSelectWorkflowRecap={modalsState.handleSelectWorkflowRecap}
                onOpenCertificat={modalsState.handleOpenCertificat}
                onOpenAttestation={modalsState.handleOpenAttestation}
                onOpenContrat={modalsState.handleOpenContrat}
                onOpenStc={modalsState.handleOpenStc}
            />

            {/* Gestionnaire Centralisé de Modales */}
            <CarriereModalCoordinator
                idEmploye={idEmploye}
                employe={employe}
                nomEmploye={nomEmploye}
                matriculeEmploye={matriculeEmploye}
                contratActif={contratActif}
                affectationActive={affectationActive}
                workflowCplActif={workflowCplActif}
                contratsList={contratsList}
                brouillonWf={brouillonWf}
                etatEssai={etatEssai}
                canWrite={canWrite}
                canValidate={canValidate}
                modalsState={modalsState}
                refreshData={refreshData}
            />
        </div>
    );
}
