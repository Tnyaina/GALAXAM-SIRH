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

                    <div className="inline-flex border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-0.5">
                        <button
                            type="button"
                            onClick={() => setModeAffichage('parcours')}
                            className={`
                                flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold tracking-wide uppercase transition-all duration-150
                                ${modeAffichage === 'parcours'
                                    ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shadow-sm border border-[var(--color-border-subtle)]'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }
                            `}
                        >
                            <GitCommit size={13} />
                            <span>Parcours chronologique</span>
                        </button>

                        <button
                            type="button"
                            onClick={() => setModeAffichage('tables')}
                            className={`
                                flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold tracking-wide uppercase transition-all duration-150
                                ${modeAffichage === 'tables'
                                    ? 'bg-[var(--color-surface)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shadow-sm border border-[var(--color-border-subtle)]'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }
                            `}
                        >
                            <Table size={13} />
                            <span>Tableaux détaillés</span>
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
            </details>
        </div>
    );
}
