import { useState } from 'react';
import { Clock, Table, GitCommit } from 'lucide-react';
import TimelineBanners from './timeline/TimelineBanners';
import ContratActifSection from './timeline/ContratActifSection';
import ParcoursChronologique from './timeline/ParcoursChronologique';
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
    onOpenContrat,
    onOpenStc,
}) {
    // État local
    const [modeAffichage, setModeAffichage] = useState('tables'); // 'tables' par défaut | 'parcours'

    // Données dérivées : liste fiable des contrats à afficher.
    const safeContrats = Array.isArray(contratsList) ? contratsList : (contratsList?.items || contratsList?.content || []);
    const displayContrats = safeContrats.filter(ct => ct && ct.codeStatutContrat !== 'BROUILLON');

    // Rendu
    return (
        <div className="space-y-3.5">
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

            {/* Sélecteur de mode d'affichage de l'historique */}
            <div className="pt-3 border-t border-ink-150 dark:border-ink-700 space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-ink-900 dark:text-white flex items-center gap-2">
                        <Clock size={14} className="text-brand-500" />
                        Historique de Carrière & Contrats
                    </h3>

                    <div className="inline-flex border border-ink-200 dark:border-ink-700 bg-ink-50 dark:bg-ink-950 p-0.5">
                        <button
                            type="button"
                            onClick={() => setModeAffichage('parcours')}
                            className={`
                                flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold tracking-wide uppercase transition-all duration-150
                                ${modeAffichage === 'parcours'
                                    ? 'bg-white dark:bg-ink-800 text-brand-500 shadow-sm border border-ink-200/50 dark:border-ink-600'
                                    : 'text-ink-500 hover:text-ink-900 dark:hover:text-white'
                                }
                            `}
                        >
                            <GitCommit size={13} />
                            <span>Parcours Chronologique</span>
                        </button>

                        <button
                            type="button"
                            onClick={() => setModeAffichage('tables')}
                            className={`
                                flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold tracking-wide uppercase transition-all duration-150
                                ${modeAffichage === 'tables'
                                    ? 'bg-white dark:bg-ink-800 text-brand-500 shadow-sm border border-ink-200/50 dark:border-ink-600'
                                    : 'text-ink-500 hover:text-ink-900 dark:hover:text-white'
                                }
                            `}
                        >
                            <Table size={13} />
                            <span>Tableaux Détaillés</span>
                        </button>
                    </div>
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
                            onOpenCertificat={onOpenCertificat}
                            onOpenAttestation={onOpenAttestation}
                            onOpenContrat={onOpenContrat}
                            onOpenStc={onOpenStc}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}
