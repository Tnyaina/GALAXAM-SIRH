import TimelineBanners from './timeline/TimelineBanners';
import ContratActifSection from './timeline/ContratActifSection';
import HistoriqueWorkflowsTable from './timeline/HistoriqueWorkflowsTable';
import HistoriqueContratsTable from './timeline/HistoriqueContratsTable';

export default function CarriereTimeline({
    contratActif,
    affectationActive,
    workflowCplActif,
    workflowsList = [],
    contratsList = [],
    brouillonWf,
    workflowPlanifieFutur,
    etatEssai,
    estEssaiRenouveleFlag,
    canWrite,
    onOpenCreateEmbauche,
    onOpenTransition,
    onOpenRupture,
    onOpenConfirmation,
    onOpenRenouvellement,
    onOpenEditModal,
    onSelectWorkflowRecap,
    onOpenCertificat,
    onOpenAttestation,
}) {
    const safeContrats = Array.isArray(contratsList) ? contratsList : (contratsList?.items || contratsList?.content || []);
    const displayContrats = safeContrats.filter(ct => ct && ct.codeStatutContrat !== 'BROUILLON');

    return (
        <div className="space-y-6">
            <TimelineBanners
                brouillonWf={brouillonWf}
                workflowPlanifieFutur={workflowPlanifieFutur}
                canWrite={canWrite}
                onOpenEditModal={onOpenEditModal}
                onSelectWorkflowRecap={onSelectWorkflowRecap}
            />

            <ContratActifSection
                contratActif={contratActif}
                affectationActive={affectationActive}
                workflowCplActif={workflowCplActif}
                workflowsList={workflowsList}
                contratsList={displayContrats}
                brouillonWf={brouillonWf}
                workflowPlanifieFutur={workflowPlanifieFutur}
                etatEssai={etatEssai}
                estEssaiRenouveleFlag={estEssaiRenouveleFlag}
                canWrite={canWrite}
                onOpenCreateEmbauche={onOpenCreateEmbauche}
                onOpenTransition={onOpenTransition}
                onOpenRupture={onOpenRupture}
                onOpenConfirmation={onOpenConfirmation}
                onOpenRenouvellement={onOpenRenouvellement}
                onOpenAttestation={onOpenAttestation}
            />

            <div className="space-y-6 pt-4">
                <HistoriqueWorkflowsTable
                    workflowsList={workflowsList}
                    onSelectWorkflowRecap={onSelectWorkflowRecap}
                />

                <HistoriqueContratsTable
                    displayContrats={displayContrats}
                    contratActif={contratActif}
                    onOpenCertificat={onOpenCertificat}
                    onOpenAttestation={onOpenAttestation}
                />
            </div>
        </div>
    );
}
