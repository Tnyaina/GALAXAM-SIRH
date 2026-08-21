import { CheckCircle, ShieldAlert, RefreshCw } from 'lucide-react';
import { useToast } from '@/components/ui/feedback/Toast';
import Loading from '@/components/ui/feedback/Loading';
import Button from '@/components/ui/forms/Button';

// Hooks
import { useCarriere } from '@/hooks/useCarriere';
import { useCarriereModals } from '@/hooks/carriere/useCarriereModals';

// Composants de vue
import CarriereHeaderCard from '@/components/carriere/CarriereHeaderCard';
import CarriereTimeline from '@/components/carriere/CarriereTimeline';
import CarriereModalsManager from '@/components/carriere/modals/CarriereModalsManager';

// Utilitaires
import { trouverWorkflowPlanifieFutur } from '@/utils/carriereUtils';

export default function CarriereTab({
    idEmploye,
    employe,
    nomEmploye,
    matriculeEmploye,
    canWrite = true,
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
            .filter((wf) => wf.codeStatutWorkflow === 'VALIDE' && (
                Number(wf.idContratCible) === Number(contratActif.idContrat) || 
                (Number(wf.idContratSource) === Number(contratActif.idContrat) && !wf.idContratCible)
            ))
            .sort((a, b) => new Date(b.dateEffet || b.dateDemande || 0) - new Date(a.dateEffet || a.dateDemande || 0))
        : [];
    const workflowCplActif = workflowsValidesDuContrat[0] || null;

    const todayStr = new Date().toISOString().substring(0, 10);
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
        <div className="space-y-8">
            <div className="flex justify-end">
                <Button variant="outline" size="sm" iconLeft={<RefreshCw size={14} />} onClick={() => {
                    refreshData();
                    if (toast) toast({ title: 'Actualisé', message: 'Les données de la carrière ont été mises à jour.', type: 'info', duration: 2000 });
                }}>
                    Actualiser
                </Button>
            </div>
            
            {/* Header carte contrat + alerte période d'essai */}
            <CarriereHeaderCard
                contratActif={contratActif}
                affectationActive={affectationActive}
                brouillonWf={brouillonWf}
                etatEssai={etatEssai}
                workflowPlanifieFutur={workflowPlanifieFutur}
                estEssaiRenouveleFlag={estEssaiRenouveleFlag}
                canWrite={canWrite}
                onOpenConfirmation={modalsState.handleOpenConfirmationModal}
                onOpenRenouvellement={modalsState.handleOpenRenouvellementModal}
                onOpenRecap={() => modalsState.setIsRecapModalOpen(true)}
            />

            {/* Messages de Succès / Erreur */}

            {errorMsg && (
                <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 p-4 rounded-none text-xs flex items-center gap-2">
                    <ShieldAlert size={16} />
                    <span>{errorMsg}</span>
                </div>
            )}

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
            />

            {/* Gestionnaire Centralisé de Modales */}
            <CarriereModalsManager 
                idEmploye={idEmploye}
                employe={employe}
                nomEmploye={nomEmploye}
                matriculeEmploye={matriculeEmploye}
                contratActif={contratActif}
                affectationActive={affectationActive}
                workflowCplActif={workflowCplActif}
                contratsList={contratsList}
                brouillonWf={brouillonWf}
                modalsState={modalsState}
            />
        </div>
    );
}