import { useState, useEffect } from 'react';
import { CheckCircle, ShieldAlert } from 'lucide-react';
import Loading from '@/components/ui/feedback/Loading';
import contratService from '@/api/employe/contratService';
import EmbaucheModal from './b/EmbaucheModal';
import TransitionModal from './b/TransitionModal';
import RecapitulatifBrouillonModal from './RecapitulatifBrouillonModal';
import ConfirmationEssaiModal from './b/ConfirmationEssaiModal';
import RenouvellementEssaiModal from './b/RenouvellementEssaiModal';
import RuptureContratModal from './b/RuptureContratModal';
import CertificatTravailModal from './b/CertificatTravailModal';
import CarriereHeaderCard from '@/components/carriere/CarriereHeaderCard';
import CarriereTimeline from '@/components/carriere/CarriereTimeline';
import { useCarriere } from '@/hooks/useCarriere';
import { isEmbaucheEvent, isRuptureEvent, trouverWorkflowPlanifieFutur } from '@/utils/carriereUtils';
import { useToast } from '@/components/ui/feedback/Toast';

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
        loading,
        errorMsg,
        brouillonWf,
        contratActif,
        affectationActive,
        contratsList,
        workflowsList,
        etatEssai,
        refreshData,
    } = useCarriere(idEmploye);

    const [actionSuccessMsg, setActionSuccessMsg] = useState('');

    // Modales
    const [isEmbaucheModalOpen, setIsEmbaucheModalOpen] = useState(false);
    const [isTransitionModalOpen, setIsTransitionModalOpen] = useState(false);
    const [isRecapModalOpen, setIsRecapModalOpen] = useState(false);
    const [isConfirmationModalOpen, setIsConfirmationModalOpen] = useState(false);
    const [isRenouvellementModalOpen, setIsRenouvellementModalOpen] = useState(false);
    const [isRuptureModalOpen, setIsRuptureModalOpen] = useState(false);
    const [isCertificatModalOpen, setIsCertificatModalOpen] = useState(false);

    const [selectedCertificatContrat, setSelectedCertificatContrat] = useState(null);
    const [selectedCertificatAffectation, setSelectedCertificatAffectation] = useState(null);
    const [loadingCertificatAffectation, setLoadingCertificatAffectation] = useState(false);
    const [selectedWfModal, setSelectedWfModal] = useState(null);
    const [editInitialData, setEditInitialData] = useState(null);
    const [isTransitioning, setIsTransitioning] = useState(false);

    /**
     * Ouvre le certificat de travail pour un contrat donné.
     *
     * Le certificat ne se génère que pour un contrat qui n'est plus actif : son
     * affectation liée n'a donc jamais le statut ACTIVE et n'est pas fournie par
     * `affectationActive` (issu de useCarriere). On va la chercher explicitement
     * via /contrats/{idContrat}/affectation, qui retrouve l'affectation par id_contrat
     * quel que soit son statut (ACTIVE, HISTORISEE...).
     */
    const handleOpenCertificat = async (contrat = null) => {
        const cible = contrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null);
        if (!cible) return;

        setSelectedCertificatContrat(cible);
        setSelectedCertificatAffectation(null);
        setIsCertificatModalOpen(true);

        // Si c'est le contrat actif courant, son affectation active est déjà en mémoire.
        if (contratActif && Number(cible.idContrat) === Number(contratActif.idContrat)) {
            setSelectedCertificatAffectation(affectationActive);
            return;
        }

        // Sinon (cas normal du certificat : contrat historique / terminé),
        // on récupère l'affectation propre à ce contrat.
        setLoadingCertificatAffectation(true);
        try {
            const aff = await contratService.getAffectationParContrat(idEmploye, cible.idContrat);
            setSelectedCertificatAffectation(aff);
        } catch (err) {
            console.error('Erreur récupération affectation du contrat pour le certificat:', err);
            setSelectedCertificatAffectation(null);
            if (toast) {
                toast({ title: 'Erreur', message: 'Impossible de charger l\'affectation de ce contrat.', type: 'error' });
            }
        } finally {
            setLoadingCertificatAffectation(false);
        }
    };

    // Auto-ouverture du modal si un workflowId est passé via l'URL (?wf=)
    useEffect(() => {
        if (loading) return;
        if (!autoOpenWorkflowId) return;
        const targetId = Number(autoOpenWorkflowId);
        if (isNaN(targetId)) return;

        const wfFound = workflowsList.find(wf => Number(wf.idWorkflow) === targetId);
        if (wfFound) {
            setSelectedWfModal(wfFound);
            setIsRecapModalOpen(true);
        } else if (brouillonWf && Number(brouillonWf.idWorkflow) === targetId) {
            setSelectedWfModal(brouillonWf);
            setIsRecapModalOpen(true);
        }

        try {
            const url = new URL(window.location.href);
            if (url.searchParams.has('wf')) {
                url.searchParams.delete('wf');
                window.history.replaceState({}, '', url.pathname + url.search);
            }
        } catch (e) {
            console.error('Erreur nettoyage URL wf:', e);
        }
    }, [loading, autoOpenWorkflowId, workflowsList, brouillonWf]);

    const handleOpenCreateModal = () => {
        setEditInitialData(null);
        setIsEmbaucheModalOpen(true);
    };

    const handleOpenEditModal = (data) => {
        setEditInitialData(data);
        const code = data?.codeTypeEvenement;
        if (isEmbaucheEvent(code)) {
            setIsEmbaucheModalOpen(true);
        } else if (isRuptureEvent(code)) {
            setIsRuptureModalOpen(true);
        } else {
            setIsTransitionModalOpen(true);
        }
    };

    const handleSuccessAction = async (msg, createdWf = null) => {
        setActionSuccessMsg(msg);
        if (toast) {
            toast({ title: 'Succès', message: msg, type: 'success' });
        }
        setTimeout(() => setActionSuccessMsg(''), 6000);

        if (createdWf && createdWf.idWorkflow) {
            setIsTransitioning(true);
            try {
                const freshWf = await contratService.getWorkflow(createdWf.idWorkflow);
                setSelectedWfModal(freshWf || createdWf);
            } catch {
                setSelectedWfModal(createdWf);
            } finally {
                setIsTransitioning(false);
                setIsRecapModalOpen(true);
            }
        }

        refreshData();
        if (onEmployeUpdated) {
            onEmployeUpdated();
        }
    };

    const workflowsValidesDuContrat = contratActif
        ? workflowsList
            .filter((wf) => wf.codeStatutWorkflow === 'VALIDE' && (Number(wf.idContratCible) === Number(contratActif.idContrat) || Number(wf.idContratSource) === Number(contratActif.idContrat)))
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
            {/* Header carte contrat + alerte période d'essai */}
            <CarriereHeaderCard
                contratActif={contratActif}
                affectationActive={affectationActive}
                brouillonWf={brouillonWf}
                etatEssai={etatEssai}
                workflowPlanifieFutur={workflowPlanifieFutur}
                estEssaiRenouveleFlag={estEssaiRenouveleFlag}
                canWrite={canWrite}
                onOpenConfirmation={() => setIsConfirmationModalOpen(true)}
                onOpenRenouvellement={() => setIsRenouvellementModalOpen(true)}
                onOpenRecap={() => setIsRecapModalOpen(true)}
            />

            {/* Messages de Succès / Erreur */}
            {actionSuccessMsg && (
                <div className="bg-success-500/10 text-success-700 dark:text-success-400 border border-success-500/30 p-4 rounded-sm text-xs font-bold flex items-center gap-2">
                    <CheckCircle size={16} />
                    <span>{actionSuccessMsg}</span>
                </div>
            )}

            {errorMsg && (
                <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 p-4 rounded-sm text-xs flex items-center gap-2">
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
                onOpenCreateEmbauche={handleOpenCreateModal}
                onOpenTransition={() => setIsTransitionModalOpen(true)}
                onOpenRupture={() => setIsRuptureModalOpen(true)}
                onOpenConfirmation={() => setIsConfirmationModalOpen(true)}
                onOpenRenouvellement={() => setIsRenouvellementModalOpen(true)}
                onOpenEditModal={handleOpenEditModal}
                onSelectWorkflowRecap={(wf) => { setSelectedWfModal(wf); setIsRecapModalOpen(true); }}
                onOpenCertificat={handleOpenCertificat}
            />

            {/* Modales */}
            {isEmbaucheModalOpen && (
                <EmbaucheModal
                    isOpen={isEmbaucheModalOpen}
                    onClose={() => setIsEmbaucheModalOpen(false)}
                    idEmploye={idEmploye}
                    nomEmploye={nomEmploye}
                    initialData={editInitialData}
                    onSuccess={(res) => handleSuccessAction('Brouillon d\'embauche enregistré avec succès !', res)}
                />
            )}

            {isRecapModalOpen && (
                <RecapitulatifBrouillonModal
                    isOpen={isRecapModalOpen}
                    onClose={() => {
                        setIsRecapModalOpen(false);
                        setSelectedWfModal(null);
                    }}
                    workflowCpl={selectedWfModal || brouillonWf}
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
                    onSuccess={(res) => handleSuccessAction('Demande de confirmation d\'essai créée avec succès !', res)}
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
                    onSuccess={(res) => handleSuccessAction('Demande de renouvellement d\'essai créée avec succès !', res)}
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
                />
            )}

            {isCertificatModalOpen && (
                <CertificatTravailModal
                    isOpen={isCertificatModalOpen}
                    onClose={() => setIsCertificatModalOpen(false)}
                    employe={employe || { idEmploye, nom: nomEmploye, matricule: matriculeEmploye }}
                    contrat={selectedCertificatContrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null)}
                    affectation={selectedCertificatAffectation}
                    loadingAffectation={loadingCertificatAffectation}
                    filiale={selectedCertificatContrat?.filiale || contratActif?.filiale}
                    dateDebutInitiale={
                        contratsList.length > 0
                            ? [...contratsList].sort((a, b) => new Date(a.dateDebut || 0) - new Date(b.dateDebut || 0))[0]?.dateDebut
                            : selectedCertificatContrat?.dateDebut
                    }
                />
            )}

            {/* Overlay de chargement fluide */}
            {isTransitioning && (
                <div className="fixed inset-0 z-[9999] flex flex-col items-center justify-center bg-ink-950/70 backdrop-blur-sm transition-all duration-300">
                    <div className="flex flex-col items-center gap-4 p-6 bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-800 rounded-lg shadow-2xl max-w-sm w-full text-center">
                        <Loading size="md" label="Enregistrement en cours..." />
                        <p className="text-[11px] text-ink-400 -mt-2">
                            Préparation du récapitulatif DAF...
                        </p>
                    </div>
                </div>
            )}
        </div>
    );
}