import { useState, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useToast } from '@/components/ui/feedback/Toast';
import contratService from '@/features/employees/api/contratService';
import { isEmbaucheEvent, isRuptureEvent, isConfirmationEssaiEvent, isRenouvellementEssaiEvent } from '@/features/employees/utils/carriereWorkflowUtils';

export function useCarriereModals({
    idEmploye,
    contratActif,
    affectationActive,
    contratsList,
    workflowsList,
    brouillonWf,
    loading,
    autoOpenWorkflowId,
    refreshData,
    onEmployeUpdated
}) {
    // Contexte et navigation
    const [, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const toast = useToast();

    // État local : modales de carrière et éléments sélectionnés.
    const [isTransitioning, setIsTransitioning] = useState(false);
    const [editInitialData, setEditInitialData] = useState(null);

    // États des modales
    const [isEmbaucheModalOpen, setIsEmbaucheModalOpen] = useState(false);
    const [isTransitionModalOpen, setIsTransitionModalOpen] = useState(false);
    const [isRecapModalOpen, setIsRecapModalOpen] = useState(false);
    const [isConfirmationModalOpen, setIsConfirmationModalOpen] = useState(false);
    const [isRenouvellementModalOpen, setIsRenouvellementModalOpen] = useState(false);
    const [isRuptureModalOpen, setIsRuptureModalOpen] = useState(false);
    
    // Certificat
    const [isCertificatModalOpen, setIsCertificatModalOpen] = useState(false);
    const [selectedCertificatContrat, setSelectedCertificatContrat] = useState(null);
    const [selectedCertificatAffectation, setSelectedCertificatAffectation] = useState(null);
    const [loadingCertificatAffectation, setLoadingCertificatAffectation] = useState(false);

    // Attestation
    const [isAttestationModalOpen, setIsAttestationModalOpen] = useState(false);
    const [selectedAttestationContrat, setSelectedAttestationContrat] = useState(null);
    const [selectedAttestationAffectation, setSelectedAttestationAffectation] = useState(null);
    const [loadingAttestationAffectation, setLoadingAttestationAffectation] = useState(false);

    // Contrat de Travail
    const [isContratModalOpen, setIsContratModalOpen] = useState(false);
    const [selectedContratDoc, setSelectedContratDoc] = useState(null);
    const [selectedContratAffectation, setSelectedContratAffectation] = useState(null);
    const [loadingContratAffectation, setLoadingContratAffectation] = useState(false);

    // STC (Solde de Tout Compte)
    const [isStcModalOpen, setIsStcModalOpen] = useState(false);
    const [selectedStcContrat, setSelectedStcContrat] = useState(null);

    const [selectedWfModal, setSelectedWfModal] = useState(null);

    // -------------------------------------------------------------------------------------------------
    // Actions de succès
    // -------------------------------------------------------------------------------------------------
    const handleSuccessAction = async (msg, createdWf = null, opts = {}) => {
        if (toast) {
            toast({ title: 'Succès', message: msg, type: 'success' });
        }

        if (createdWf && createdWf.idWorkflow && !opts?.silent) {
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

        refreshData({ silent: opts?.silent });
        if (onEmployeUpdated) {
            onEmployeUpdated();
        }
    };

    // -------------------------------------------------------------------------------------------------
    // Auto-ouverture (URL /?wf=X)
    // -------------------------------------------------------------------------------------------------
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
            setSearchParams(prev => {
                if (!prev.has('wf') && !prev.has('action')) return prev;
                const next = new URLSearchParams(prev);
                if (next.get('action') === 'new_contract') {
                    setIsEmbaucheModalOpen(true);
                }
                next.delete('wf');
                next.delete('action');
                return next;
            }, { replace: true });
        } catch (e) {
            console.error('Erreur nettoyage URL wf/action:', e);
        }
    }, [loading, autoOpenWorkflowId, workflowsList, brouillonWf, setSearchParams]);

    // -------------------------------------------------------------------------------------------------
    // Openers
    // -------------------------------------------------------------------------------------------------
    const handleOpenCreateModal = () => {
        setEditInitialData(null);
        setIsEmbaucheModalOpen(true);
    };

    const handleOpenTransitionModal = () => {
        setEditInitialData(null);
        setIsTransitionModalOpen(true);
    };

    const handleOpenRuptureModal = () => {
        setEditInitialData(null);
        setIsRuptureModalOpen(true);
    };

    const handleOpenConfirmationModal = () => {
        setEditInitialData(null);
        setIsConfirmationModalOpen(true);
    };

    const handleOpenRenouvellementModal = () => {
        setEditInitialData(null);
        setIsRenouvellementModalOpen(true);
    };

    const handleOpenEditModal = (data) => {
        setEditInitialData(data);
        const code = data?.codeTypeEvenement;
        if (data?.idContratSource || data?.workflow?.idContratSource) {
            setIsTransitionModalOpen(true);
        } else if (isEmbaucheEvent(code)) {
            setIsEmbaucheModalOpen(true);
        } else if (isRuptureEvent(code)) {
            setIsRuptureModalOpen(true);
        } else if (isConfirmationEssaiEvent(code)) {
            setIsConfirmationModalOpen(true);
        } else if (isRenouvellementEssaiEvent(code)) {
            setIsRenouvellementModalOpen(true);
        } else {
            setIsTransitionModalOpen(true);
        }
    };

    const [isRecapCancelMode, setIsRecapCancelMode] = useState(false);

    const handleSelectWorkflowRecap = (wf, { startInCancelMode = false } = {}) => {
        setSelectedWfModal(wf);
        setIsRecapCancelMode(startInCancelMode);
        setIsRecapModalOpen(true);
    };

    // -------------------------------------------------------------------------------------------------
    // Documents Administratifs
    // -------------------------------------------------------------------------------------------------
    const handleOpenCertificat = async (contrat = null) => {
        const cible = contrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null);
        if (!cible) return;

        setSelectedCertificatContrat(cible);
        setSelectedCertificatAffectation(null);
        setIsCertificatModalOpen(true);

        if (contratActif && Number(cible.idContrat) === Number(contratActif.idContrat)) {
            setSelectedCertificatAffectation(affectationActive);
            return;
        }

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

    const handleOpenAttestation = async (contrat = null) => {
        const cible = contrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null);
        if (!cible) return;

        setSelectedAttestationContrat(cible);
        setSelectedAttestationAffectation(null);
        setIsAttestationModalOpen(true);

        if (contratActif && Number(cible.idContrat) === Number(contratActif.idContrat)) {
            setSelectedAttestationAffectation(affectationActive);
            return;
        }

        setLoadingAttestationAffectation(true);
        try {
            const aff = await contratService.getAffectationParContrat(idEmploye, cible.idContrat);
            setSelectedAttestationAffectation(aff);
        } catch (err) {
            console.error('Erreur récupération affectation pour attestation:', err);
            setSelectedAttestationAffectation(null);
            if (toast) {
                toast({ title: 'Erreur', message: 'Impossible de charger l\'affectation de ce contrat.', type: 'error' });
            }
        } finally {
            setLoadingAttestationAffectation(false);
        }
    };

    const handleOpenContrat = async (contrat = null) => {
        const cible = contrat || contratActif || (contratsList.length > 0 ? contratsList[0] : null);
        if (!cible) return;

        setSelectedContratDoc(cible);
        setSelectedContratAffectation(null);
        setIsContratModalOpen(true);

        if (contratActif && Number(cible.idContrat) === Number(contratActif.idContrat) && affectationActive) {
            setSelectedContratAffectation(affectationActive);
            return;
        }

        setLoadingContratAffectation(true);
        try {
            const aff = await contratService.getAffectationParContrat(idEmploye, cible.idContrat);
            setSelectedContratAffectation(aff);
        } catch (err) {
            console.error('Erreur récupération affectation pour contrat:', err);
            setSelectedContratAffectation(null);
        } finally {
            setLoadingContratAffectation(false);
        }
    };

    const handleOpenStc = (contrat = null) => {
        if (!contrat?.simulationOnly) {
            const params = new URLSearchParams({ employeId: String(idEmploye) });
            const dateDepart = contrat?.dateDepart || contrat?.dateFin;
            if (dateDepart) params.set('dateDepart', String(dateDepart).slice(0, 10));
            if (contrat?.motifRupture) params.set('motif', contrat.motifRupture);
            navigate(`/paie/dossiers-sortie/nouveau?${params.toString()}`);
            return;
        }
        setSelectedStcContrat(contrat);
        setIsStcModalOpen(true);
    };

    return {
        // États d'orchestration
        isTransitioning, setIsTransitioning,
        editInitialData, setEditInitialData,
        selectedWfModal, setSelectedWfModal,

        // Modales Visibilité
        isEmbaucheModalOpen, setIsEmbaucheModalOpen,
        isTransitionModalOpen, setIsTransitionModalOpen,
        isRecapModalOpen, setIsRecapModalOpen,
        isRecapCancelMode, setIsRecapCancelMode,
        isConfirmationModalOpen, setIsConfirmationModalOpen,
        isRenouvellementModalOpen, setIsRenouvellementModalOpen,
        isRuptureModalOpen, setIsRuptureModalOpen,
        
        // Données Certificat / Attestation / Contrat / STC
        isCertificatModalOpen, setIsCertificatModalOpen,
        selectedCertificatContrat, selectedCertificatAffectation, loadingCertificatAffectation,
        isAttestationModalOpen, setIsAttestationModalOpen,
        selectedAttestationContrat, selectedAttestationAffectation, loadingAttestationAffectation,
        isContratModalOpen, setIsContratModalOpen,
        selectedContratDoc, selectedContratAffectation, loadingContratAffectation,
        isStcModalOpen, setIsStcModalOpen,
        selectedStcContrat, setSelectedStcContrat,

        // Actions
        handleSuccessAction,
        handleOpenCreateModal,
        handleOpenTransitionModal,
        handleOpenRuptureModal,
        handleOpenConfirmationModal,
        handleOpenRenouvellementModal,
        handleOpenEditModal,
        handleSelectWorkflowRecap,
        handleOpenCertificat,
        handleOpenAttestation,
        handleOpenContrat,
        handleOpenStc
    };
}
