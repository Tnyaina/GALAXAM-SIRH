import { useState } from 'react';
import { Clock, Table, GitCommit } from 'lucide-react';
import TimelineBanners from './timeline/TimelineBanners';
import ContratActifSection from './timeline/ContratActifSection';
import ParcoursChronologique from './timeline/ParcoursChronologique';
import HistoriqueWorkflowsTable from './timeline/HistoriqueWorkflowsTable';
import HistoriqueContratsTable from './timeline/HistoriqueContratsTable';
import Tabs from '@/components/ui/navigation/Tabs';

export default function CarriereTimeline({
    contratActif,
    affectationActive,
    workflowCplActif,
    workflowsList = [],
    stcList = [],
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
    onOpenContrat,
    onOpenStc,
}) {
    // État local
    const [modeAffichage, setModeAffichage] = useState('tables'); // 'tables' par défaut | 'parcours'

    // Données dérivées : liste fiable des contrats à afficher.
    const safeContrats = Array.isArray(contratsList) ? contratsList : (contratsList?.items || contratsList?.content || []);
    const displayContrats = safeContrats.filter(Boolean);

    // Rendu
    return (
        <div className="space-y-3.5">
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
                onOpenContrat={onOpenContrat}
            />

            {(brouillonWf || workflowPlanifieFutur) && <section aria-label="Changements en cours" className="space-y-3">
                <h3 className="text-sm font-semibold">Changements en cours</h3>
                <TimelineBanners
                    brouillonWf={brouillonWf}
                    workflowPlanifieFutur={workflowPlanifieFutur}
                    canWrite={canWrite}
                    onOpenEditModal={onOpenEditModal}
                    onSelectWorkflowRecap={onSelectWorkflowRecap}
                    onOpenContrat={onOpenContrat}
                    contratBrouillon={displayContrats.find(c => Number(c.idContrat) === Number(brouillonWf?.idContratCible))}
                />

            </section>}

            {/* Sélecteur de mode d'affichage de l'historique */}
            <details className="pt-3 border-t border-[var(--color-border-subtle)] space-y-3">
                <summary className="cursor-pointer text-sm font-semibold text-[var(--color-fg)] hover:text-[var(--color-brand-600)] dark:hover:text-[var(--color-brand-400)] transition-colors">
                    Historique de carrière · {displayContrats.length} contrats · {workflowsList.length} mouvements
                </summary>
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                        <Clock size={14} className="text-[var(--color-brand-500)]" />
                        Historique de carrière et contrats
                    </h3>

                    <Tabs
                        tabs={[
                            { id: 'parcours', label: 'Parcours chronologique', icon: <GitCommit size={15} /> },
                            { id: 'tables', label: 'Tableaux détaillés', icon: <Table size={15} /> },
                        ]}
                        activeTab={modeAffichage}
                        onChange={setModeAffichage}
                        variant="pills"
                        bg="surface"
                        ariaLabel="Mode d'affichage de l'historique"
                        className="flex-nowrap border border-[var(--color-border-subtle)] p-0.5 gap-0.5"
                    />
                </div>

                {/* Vue 1 : Parcours Chronologique narratif */}
                {modeAffichage === 'parcours' && (
                    <ParcoursChronologique
                        workflowsList={workflowsList}
                        contratsList={displayContrats}
                        contratActif={contratActif}
                        onSelectWorkflowRecap={onSelectWorkflowRecap}
                        onOpenCertificat={onOpenCertificat}
                        onOpenAttestation={onOpenAttestation}
                    />
                )}

                {/* Vue 2 : Tableaux Détaillés d'Audit */}
                {modeAffichage === 'tables' && (
                    <div className="space-y-6">
                        <HistoriqueWorkflowsTable
                            workflowsList={workflowsList}
                            onSelectWorkflowRecap={onSelectWorkflowRecap}
                        />

                        <HistoriqueContratsTable
                            displayContrats={displayContrats}
                            contratActif={contratActif}
                            workflowsList={workflowsList}
                            stcList={stcList}
                            onOpenCertificat={onOpenCertificat}
                            onOpenAttestation={onOpenAttestation}
                            onOpenContrat={onOpenContrat}
                            onOpenStc={onOpenStc}
                        />
                    </div>
                )}
            </details>
        </div>
    );
}
