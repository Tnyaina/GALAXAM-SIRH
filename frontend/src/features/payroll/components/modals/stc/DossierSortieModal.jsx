import { useState, useEffect, useCallback, useRef } from 'react';
import {
    AlertTriangle,
    ArrowLeft,
    BadgeCheck,
    Banknote,
    Building2,
    Calculator,
    CalendarCheck,
    CheckCircle2,
    Clock,
    Coins,
    ExternalLink,
    FileSpreadsheet,
    FileText,
    Info,
    Lock,
    Receipt,
    Save,
    ShieldAlert,
    ShieldCheck,
    User,
    Wallet,
    XCircle
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import { Textarea } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import { Card, CardBody, CardFooter } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Alert from '@/components/ui/feedback/Alert';
import { Tabs } from '@/components/ui/navigation/Tabs';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { formatStcAnciennete } from '@/features/payroll/utils/stcDisplay';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import DossierSortieSuiviPanel from './DossierSortieSuiviPanel';
import DossierSortieWorkspaceShell from './DossierSortieWorkspaceShell';
import HasPermission from '@/features/auth/components/HasPermission';
import { useAuth } from '@/context/AuthContext';
import contratService from '@/features/employees/api/contratService';
import { optionsMotifsSortie } from '@/features/employees/utils/motifsSortie';
import { getErrorMessage } from '@/api/apiClient';

const OPTIONS_DECISION_PRET = [
    { value: 'MAINTIEN_ECHEANCIER', label: '1. Maintien de l’échéancier (Virement personnel du salarié)' },
    { value: 'REMBOURSEMENT_SEPARE', label: '2. Remboursement séparé direct (Chèque ou virement dédié)' },
    { value: 'RETENUE_STC_AUTORISEE', label: '3. Retenue sur STC (Dans la limite de la quotité saisissable légale)' },
    { value: 'CONTENTIEUX_RECOUVREMENT', label: '4. Dossier transmis au service recouvrement / contentieux' },
];

export default function DossierSortieModal({
    isOpen,
    onClose,
    pageMode = false,
    idDossier = null,
    idEmploye = null,
    nomEmploye = '',
    dateDepartInitiale = null,
    motifInitial = 'DEMISSION',
    onSaved,
    onOpenStcModal,
}) {
    const toast = useToast();
    const { hasPermission, user } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');
    const [loading, setLoading] = useState(false);
    const [saving, setSaving] = useState(false);
    const [simulating, setSimulating] = useState(false);
    const [sealing, setSealing] = useState(false);
    const [confirmScellement, setConfirmScellement] = useState(false);
    const [confirmAbandon, setConfirmAbandon] = useState(false);
    const [simulationResult, setSimulationResult] = useState(null);
    const [simulationNeedsRefresh, setSimulationNeedsRefresh] = useState(false);
    const [commentaireScellement, setCommentaireScellement] = useState('');
    const [errorMsg, setErrorMsg] = useState(null);
    const [dossier, setDossier] = useState(null);
    const [motifsSortie, setMotifsSortie] = useState([]);
    const [activeTab, setActiveTab] = useState('synthese'); // 'synthese' | 'checklist' | 'pret' | 'decompte' | 'suivi'
    const initialViewRef = useRef(false);

    // Formulaire de saisie éditable
    const [dateDepart, setDateDepart] = useState('');
    const [motifRupture, setMotifRupture] = useState('DEMISSION');
    const [decisionPret, setDecisionPret] = useState('MAINTIEN_ECHEANCIER');
    const [justificationDecisionPret, setJustificationDecisionPret] = useState('');
    const [montantPretRetenu, setMontantPretRetenu] = useState(0);
    const [montantBulletinDejaPaye, setMontantBulletinDejaPaye] = useState('0');
    const [datePaiementBulletin, setDatePaiementBulletin] = useState('');
    const [referencePaiementBulletin, setReferencePaiementBulletin] = useState('');

    // Chargement des motifs de rupture légaux
    useEffect(() => {
        if (!isOpen) return;
        contratService.getMotifsSortie()
            .then(data => setMotifsSortie(optionsMotifsSortie(data)))
            .catch(() => {});
    }, [isOpen]);

    // Chargement du dossier
    const chargerDossier = useCallback(async () => {
        if (!isOpen) return;
        setLoading(true);
        setErrorMsg(null);
        try {
            let data;
            if (idDossier) {
                data = await dossierSortieService.obtenirDossier(idDossier);
            } else if (idEmploye) {
                const dateRef = dateDepartInitiale || getTodayDateString();
                data = await dossierSortieService.obtenirDossierEmploye(idEmploye, dateRef, motifInitial);
            }
            if (data) {
                setDossier(data);
                if (!initialViewRef.current) {
                    setActiveTab(!data.idDossierSortie ? 'synthese' : data.idStc ? 'suivi'
                        : data.referenceSimulation ? 'decompte'
                            : data.checklist?.some(item => item.niveau === 'BLOQUANT' && item.statut === 'NON_CONFORME')
                                ? 'checklist' : 'synthese');
                    initialViewRef.current = true;
                }
                // Le DTO recharge la référence de simulation, mais pas ses rubriques détaillées.
                setSimulationNeedsRefresh(Boolean(data.referenceSimulation && !data.idStc && !data.snapshotSimulation));
                setDateDepart(String(data.dateDepart || dateDepartInitiale || getTodayDateString()).slice(0, 10));
                setMotifRupture(data.motifRupture || motifInitial || 'DEMISSION');
                setDecisionPret(data.decisionPret || 'MAINTIEN_ECHEANCIER');
                setJustificationDecisionPret(data.justificationDecisionPret || '');
                setMontantPretRetenu(data.montantPretRetenu || 0);
                setMontantBulletinDejaPaye(String(data.donneesPreparation?.montantBulletinDejaPaye ?? data.montantBulletinDejaPaye ?? 0));
                setDatePaiementBulletin(String(data.donneesPreparation?.datePaiementBulletin || data.datePaiementBulletin || '').slice(0, 10));
                setReferencePaiementBulletin(data.donneesPreparation?.referencePaiementBulletin || data.referencePaiementBulletin || '');
                setSimulationResult(data.snapshotSimulation ? {
                        referenceSimulation: data.referenceSimulation,
                        version: data.version,
                        decompte: data.snapshotSimulation,
                    } : null);
            }
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible de charger les données du dossier de sortie.'));
        } finally {
            setLoading(false);
        }
    }, [isOpen, idDossier, idEmploye, dateDepartInitiale, motifInitial]);

    useEffect(() => {
        if (isOpen) {
            chargerDossier();
        } else {
            setDossier(null);
            setErrorMsg(null);
            setSimulationResult(null);
            setActiveTab('synthese');
            initialViewRef.current = false;
        }
    }, [isOpen, chargerDossier]);

    // Sauvegarde du dossier préparatoire
    const handleSauvegarder = async () => {
        if (!dossier?.idEmploye) return;
        const montantPaye = Number(montantBulletinDejaPaye || 0);
        if (!Number.isFinite(montantPaye) || montantPaye < 0 || montantPaye > Number(dossier.netBulletinMensuel || 0)) {
            setErrorMsg('Le montant déjà payé doit être compris entre 0 et le net du bulletin mensuel.');
            return;
        }
        if (montantPaye > 0 && (!datePaiementBulletin || !referencePaiementBulletin.trim())) {
            setErrorMsg('Indiquez la date et la référence du paiement du bulletin mensuel.');
            return;
        }
        setSaving(true);
        setErrorMsg(null);
        try {
            const payload = {
                idDossierSortie: dossier.idDossierSortie || idDossier || null,
                idEmploye: dossier.idEmploye,
                idContrat: dossier.idContrat || null,
                version: dossier.version || 1,
                dateDepart: dateDepart,
                motifRupture: motifRupture,
                statutDossier: dossier.statutDossier || 'EN_PREPARATION',
                decisionPret: (dossier.capitalRestantPrets && Number(dossier.capitalRestantPrets) > 0) ? decisionPret : null,
                justificationDecisionPret: justificationDecisionPret || null,
                montantPretRetenu: montantPretRetenu ? Number(montantPretRetenu) : 0,
                donneesPreparation: {
                    dateDepart,
                    motifRupture,
                    montantBulletinDejaPaye: montantPaye,
                    datePaiementBulletin: montantPaye > 0 ? datePaiementBulletin : null,
                    referencePaiementBulletin: montantPaye > 0 ? referencePaiementBulletin.trim() : null,
                }
            };
            const updated = await dossierSortieService.sauvegarderDossier(payload);
            if (dossier.referenceSimulation) setSimulationNeedsRefresh(true);
            setDossier(updated);
            toast.success('Dossier préparatoire de sortie enregistré avec succès.');
            if (onSaved) onSaved(updated);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors de la sauvegarde du dossier de sortie.'));
        } finally {
            setSaving(false);
        }
    };

    // Calcul de la simulation STC
    const handleSimulerStc = async () => {
        let currentDossier = dossier;
        if (!currentDossier?.idEmploye) return;
        const montantPaye = Number(montantBulletinDejaPaye || 0);
        if (!Number.isFinite(montantPaye) || montantPaye < 0 || montantPaye > Number(currentDossier.netBulletinMensuel || 0)
            || (montantPaye > 0 && (!datePaiementBulletin || !referencePaiementBulletin.trim()))) {
            setErrorMsg('Vérifiez le montant, la date et la référence du paiement du bulletin mensuel.');
            setActiveTab('synthese');
            return;
        }
        if (!currentDossier?.idDossierSortie || hasUnsavedPreparation) {
            setSaving(true);
            setErrorMsg(null);
            try {
                const payload = {
                    idDossierSortie: currentDossier?.idDossierSortie || idDossier || null,
                    idEmploye: currentDossier.idEmploye,
                    idContrat: currentDossier.idContrat || null,
                    version: currentDossier.version || 1,
                    dateDepart: dateDepart,
                    motifRupture: motifRupture,
                    statutDossier: currentDossier.statutDossier || 'EN_PREPARATION',
                    decisionPret: (currentDossier.capitalRestantPrets && Number(currentDossier.capitalRestantPrets) > 0) ? decisionPret : null,
                    justificationDecisionPret: justificationDecisionPret || null,
                    montantPretRetenu: montantPretRetenu ? Number(montantPretRetenu) : 0,
                    donneesPreparation: {
                        dateDepart,
                        motifRupture,
                        montantBulletinDejaPaye: Number(montantBulletinDejaPaye || 0),
                        datePaiementBulletin: Number(montantBulletinDejaPaye || 0) > 0 ? datePaiementBulletin : null,
                        referencePaiementBulletin: Number(montantBulletinDejaPaye || 0) > 0 ? referencePaiementBulletin.trim() : null,
                    }
                };
                currentDossier = await dossierSortieService.sauvegarderDossier(payload);
                setDossier(currentDossier);
                if (onSaved) onSaved(currentDossier);
            } catch (err) {
                setErrorMsg(getErrorMessage(err, 'Erreur lors de la sauvegarde préalable du dossier de sortie.'));
                return;
            } finally {
                setSaving(false);
            }
        }
        if (!currentDossier?.idDossierSortie) {
            toast.warning('Veuillez d’abord sauvegarder le dossier avant de simuler le STC.');
            return;
        }
        setSimulating(true);
        setErrorMsg(null);
        try {
            const res = await dossierSortieService.simulerStc(currentDossier.idDossierSortie);
            setSimulationResult(res);
            setSimulationNeedsRefresh(false);
            setDossier(prev => ({
                ...prev,
                version: res.version,
                referenceSimulation: res.referenceSimulation,
                snapshotSimulation: res.decompte,
                statutDossier: 'PRET_A_VALIDER'
            }));
            setActiveTab('decompte');
            toast.success('Simulation STC calculée. Référence vérifiable générée.');
        } catch (err) {
            const message = getErrorMessage(err, 'Erreur lors du calcul de la simulation STC.');
            setErrorMsg(message.includes('AFFECTATION_CONTRAT')
                ? 'La simulation nécessite une affectation au contrat à la date de départ. Vérifiez les données du contrat et son affectation dans le dossier salarié, puis relancez la simulation.'
                : message);
        } finally {
            setSimulating(false);
        }
    };

    // Scellement transactionnel du STC
    const handleScellerStc = async () => {
        if (!dossier?.idDossierSortie) return;
        const refSim = simulationResult?.referenceSimulation || dossier?.referenceSimulation;
        if (!refSim) {
            toast.warning('Une simulation préalable est requise avant le scellement définitif.');
            return;
        }
        if (hasUnsavedPreparation || simulationNeedsRefresh) {
            setErrorMsg('Enregistrez les changements et relancez la simulation avant de sceller le STC.');
            return;
        }
        setSealing(true);
        setErrorMsg(null);
        try {
            const payload = {
                referenceSimulation: refSim,
                version: dossier.version || simulationResult?.version || 1,
                commentaire: commentaireScellement || null
            };
            const updated = await dossierSortieService.scellerStc(dossier.idDossierSortie, payload);
            setDossier(updated);
            toast.success('STC validé et scellé avec succès. Les résultats financiers sont verrouillés.');
            if (onSaved) onSaved(updated);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors du scellement du STC.'));
        } finally {
            setSealing(false);
        }
    };



    // Helpers d'affichage
    const getBadgeSeverity = (niveau) => {
        switch (niveau) {
            case 'BLOQUANT': return 'danger';
            case 'AVERTISSEMENT': return 'warning';
            default: return 'info';
        }
    };

    const getBadgeStatut = (statut) => {
        switch (statut) {
            case 'CONFORME': return { label: 'Conforme', variant: 'success', icon: CheckCircle2 };
            case 'A_VERIFIER': return { label: 'À vérifier', variant: 'warning', icon: AlertTriangle };
            case 'NON_CONFORME': return { label: 'Non conforme', variant: 'danger', icon: XCircle };
            default: return { label: statut, variant: 'default', icon: Info };
        }
    };

    const getBadgeSource = (source) => {
        switch (source) {
            case 'LEGAL_MADAGASCAR_2024': return { label: 'Cadre légal malgache', variant: 'info' };
            case 'REGLE_INTERNE_GALAXAM': return { label: 'Procédure GALAXAM', variant: 'primary' };
            case 'TECHNIQUE_SI': return { label: 'Cohérence système', variant: 'neutral' };
            default: return { label: source, variant: 'neutral' };
        }
    };

    const hasLoans = dossier?.capitalRestantPrets && Number(dossier.capitalRestantPrets) > 0;
    const nbBloquants = dossier?.checklist?.filter(c => c.niveau === 'BLOQUANT' && c.statut === 'NON_CONFORME').length || 0;
    const bulletinValide = ['VALIDE', 'CLOTURE'].includes(dossier?.statutBulletinMensuel);
    const bulletinEnAttente = Boolean(dossier?.idBulletinMensuel) && !bulletinValide;
    const hasUnsavedPreparation = Boolean(dossier) && !dossier.idStc && (
        dateDepart !== String(dossier.dateDepart || '').slice(0, 10)
        || motifRupture !== (dossier.motifRupture || 'DEMISSION')
        || Number(montantBulletinDejaPaye || 0) !== Number(dossier.donneesPreparation?.montantBulletinDejaPaye ?? 0)
        || (Number(montantBulletinDejaPaye || 0) > 0 && (
            datePaiementBulletin !== String(dossier.donneesPreparation?.datePaiementBulletin || '').slice(0, 10)
            || referencePaiementBulletin.trim() !== (dossier.donneesPreparation?.referencePaiementBulletin || '')
        ))
        || (Boolean(hasLoans) && (
            decisionPret !== (dossier.decisionPret || 'MAINTIEN_ECHEANCIER')
            || justificationDecisionPret !== (dossier.justificationDecisionPret || '')
            || Number(montantPretRetenu || 0) !== Number(dossier.montantPretRetenu || 0)
        ))
    );
    const prochaineEtape = !dossier?.idDossierSortie
        ? 'Vérifiez la date de départ et le contrat, puis sauvegardez le dossier pour lancer une simulation.'
        : dossier?.idStc
            ? 'Le STC est scellé. Consultez le suivi pour les règlements, la clôture administrative et les documents disponibles.'
            : nbBloquants > 0
                ? `Examinez la checklist : ${nbBloquants} point(s) bloquant(s) restent à résoudre avant le scellement.`
                : simulationNeedsRefresh
                    ? 'La préparation a changé. Relancez la simulation avant tout scellement.'
                : simulationResult || dossier?.referenceSimulation
                    ? 'Contrôlez le décompte simulé et ses rubriques avant de sceller le STC.'
                    : 'La préparation est enregistrée. Lancez la simulation, puis contrôlez le décompte.';
    const prochaineSection = !dossier?.idDossierSortie ? 'synthese' : dossier?.idStc ? 'suivi' : nbBloquants > 0 ? 'checklist' : 'decompte';
    const dossierTabs = [
        { id: 'synthese', label: 'Situation', icon: FileSpreadsheet },
        { id: 'checklist', label: 'Points à traiter', icon: ShieldAlert, ...(nbBloquants > 0 ? { count: nbBloquants } : {}) },
        { id: 'pret', label: 'Prêts', icon: Banknote },
        { id: 'decompte', label: 'Calcul STC', icon: Calculator },
        { id: 'suivi', label: 'Suivi et documents', icon: FileText },
    ];
    const DossierShell = pageMode ? DossierSortieWorkspaceShell : Modal;

    return (
        <DossierShell
            isOpen={isOpen}
            onClose={onClose}
            title={`Dossier de sortie · ${dossier?.nomComplet || nomEmploye || 'Salarié'}`}
            size="4xl"
            className="max-h-[calc(100dvh-2rem)]"
            isDirty={hasUnsavedPreparation}
            closeOnEscape={!confirmAbandon && !confirmScellement}
            closeOnBackdrop={!confirmAbandon && !confirmScellement}
            footer={pageMode && dossier?.idStc ? null :
                <div className="w-full space-y-4">
                    {/* 1. Bandeau de statut / prérequis inspiré du Centre de Paie */}
                    {(!pageMode || !dossier?.idStc) && <section className={`flex flex-wrap items-center justify-between gap-3 border px-3.5 py-2.5 ${
                        dossier?.idStc
                            ? 'border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5'
                            : (simulationResult?.referenceSimulation || dossier?.referenceSimulation)
                                ? 'border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/5'
                                : dossier?.pretAValider
                                    ? 'border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5'
                                    : 'border-[var(--color-warning-500)]/40 bg-[var(--color-warning-500)]/10'
                    }`} aria-live="polite">
                        <div>
                            <h3 className="text-xs font-bold text-[var(--color-fg)]">
                                {dossier?.idStc
                                    ? 'STC scellé & archivé'
                                    : (simulationResult?.referenceSimulation || dossier?.referenceSimulation)
                                        ? 'Simulation STC calculée — Prête pour le scellement'
                                        : dossier?.pretAValider
                                            ? 'Avant le calcul STC'
                                            : 'Préparation du dossier de sortie'}
                            </h3>
                            <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                                {dossier?.idStc
                                    ? `Dossier de sortie scellé #${dossier.idStc}. Les montants et décomptes sont définitivement scellés.`
                                    : (simulationResult?.referenceSimulation || dossier?.referenceSimulation)
                                        ? `Décompte simulé vérifié (${simulationResult?.referenceSimulation || dossier?.referenceSimulation}). Contrôlez les rubriques ci-dessus avant de sceller.`
                                        : dossier?.pretAValider
                                            ? 'Checklist conforme. Vous pouvez enregistrer le dossier et lancer la simulation.'
                                            : `${nbBloquants} point(s) bloquant(s) restent à résoudre avant de pouvoir calculer.`}
                            </p>
                        </div>
                        <Badge variant={dossier?.idStc ? 'success' : (simulationResult?.referenceSimulation || dossier?.referenceSimulation) ? 'brand' : dossier?.pretAValider ? 'success' : 'warning'} size="sm">
                            {dossier?.idStc ? 'Scellé' : (simulationResult?.referenceSimulation || dossier?.referenceSimulation) ? 'Simulé' : dossier?.pretAValider ? 'Prêt' : `${nbBloquants} à traiter`}
                        </Badge>
                    </section>}

                    {/* 2. Barre d'actions en bas de page, sur le modèle du Centre de Paie (PayrollCampaignShell) */}
                    <nav aria-label="Actions du dossier" className={pageMode ? "border-t border-[var(--color-border-subtle)] pt-4 pb-1" : "pt-1"}>
                        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                            {!pageMode && <div className="flex items-center gap-2">
                                <Button
                                    variant="outline"
                                    size="sm"
                                    iconLeft={<ArrowLeft size={14} />}
                                    onClick={() => hasUnsavedPreparation ? setConfirmAbandon(true) : onClose()}
                                    disabled={saving || simulating || sealing}
                                >
                                    {pageMode ? 'Retour aux dossiers' : 'Fermer'}
                                </Button>
                            </div>}
                            <div className="flex flex-wrap items-center gap-2.5">
                                {dossier && !dossier.idStc && dossier.statutDossier !== 'ANNULE' && canWrite && (
                                    !dossier.idDossierSortie || hasUnsavedPreparation ? (
                                        <Button variant="primary" size="sm" onClick={handleSauvegarder} loading={saving}
                                            disabled={simulating || sealing} iconLeft={<Save size={15} />}>
                                            Sauvegarder le dossier
                                        </Button>
                                    ) : bulletinEnAttente ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('synthese')}>
                                            Vérifier les données de paie
                                        </Button>
                                    ) : nbBloquants > 0 ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('checklist')}>
                                            Voir les points bloquants
                                        </Button>
                                    ) : simulationNeedsRefresh || !dossier.referenceSimulation ? (
                                        <Button variant="primary" size="sm" onClick={handleSimulerStc} loading={simulating}
                                            disabled={saving || sealing} iconLeft={<Calculator size={15} />}>
                                            {simulationNeedsRefresh ? 'Actualiser la simulation' : 'Simuler le STC'}
                                        </Button>
                                    ) : activeTab !== 'decompte' ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('decompte')}>
                                            Examiner le décompte
                                        </Button>
                                    ) : (
                                        <Button variant="primary" size="sm" onClick={() => setConfirmScellement(true)} loading={sealing}
                                            iconLeft={<ShieldCheck size={15} />}>
                                            Valider et sceller
                                        </Button>
                                    )
                                )}
                                {dossier?.idStc && activeTab !== 'suivi' && (
                                    <Button variant="primary" size="sm" onClick={() => setActiveTab('suivi')}>
                                        Suivre règlements et documents
                                    </Button>
                                )}
                            </div>
                        </div>
                    </nav>
                </div>
            }
        >
            <div className="space-y-5">
                {errorMsg && (
                    <Alert type="error" title="Erreur dossier de sortie" message={errorMsg} />
                )}
                {hasUnsavedPreparation && <Alert type="warning" title="Modifications non enregistrées"
                    message="Sauvegardez la préparation avant la simulation."
                    action={canWrite && <Button variant="outline" size="xs" onClick={handleSauvegarder} loading={saving}>Enregistrer maintenant</Button>} />}
                {!loading && !dossier && (
                    <div role="alert" className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-5 text-sm text-[var(--color-fg)]">
                        Impossible d’afficher le dossier. Vérifiez les données du salarié ou revenez à la liste.
                    </div>
                )}
                {!loading && dossier && (
                    <Card as="section" shadow="none" aria-label="État et prochaine étape du dossier">
                        <CardBody className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                            <div className="flex min-w-0 items-start gap-3">
                                <div className="flex h-9 w-9 shrink-0 items-center justify-center bg-[var(--color-brand-100)] text-[var(--color-brand-700)]">
                                    {dossier.idStc ? <BadgeCheck size={19} aria-hidden="true" /> : <Info size={19} aria-hidden="true" />}
                                </div>
                                <div>
                                    <div className="flex flex-wrap items-center gap-2">
                                        <h2 className="text-sm font-bold text-[var(--color-fg)]">{dossier.idStc ? 'STC scellé' : 'Prochaine étape'}</h2>
                                        {dossier.idStc && <Badge variant="success" size="xs">Validé</Badge>}
                                    </div>
                                    <p className="mt-1 max-w-2xl text-sm leading-relaxed text-[var(--color-fg-muted)]">
                                        {canWrite ? prochaineEtape : 'Consultation seule : vérifiez la situation, le calcul et le suivi.'}
                                    </p>
                                </div>
                            </div>
                            {dossier.idDossierSortie && <Button type="button" variant="primary" size="sm" onClick={() => setActiveTab(prochaineSection)} className="shrink-0 self-start sm:self-center">
                                {prochaineSection === 'checklist' ? 'Voir les points' : prochaineSection === 'suivi' ? 'Suivi et documents' : 'Voir le calcul'}
                            </Button>}
                        </CardBody>
                        {(dossier.idStc || (!simulationNeedsRefresh && simulationResult?.decompte)) && (
                            <CardFooter role="status" aria-label="Récapitulatif financier" className="grid gap-4 sm:grid-cols-2">
                                <div>
                                    <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">{dossier.idStc ? 'Net scellé' : 'Net simulé · provisoire'}</p>
                                    <p className="font-data text-xl font-bold text-[var(--color-fg)]">{formatMoneyAr(dossier.idStc ? dossier.netAPayerStc : simulationResult.decompte.netAPayerStc)}</p>
                                </div>
                                {dossier.idStc && <div className="sm:text-right">
                                    <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Reste à payer</p>
                                    <p className="font-data text-xl font-bold text-[var(--color-fg)]">{formatMoneyAr(dossier.soldeRestantAPayer)}</p>
                                </div>}
                            </CardFooter>
                        )}
                    </Card>
                )}

                {!loading && dossier && !dossier.idStc && (
                    <div className="grid grid-cols-1 gap-2 sm:grid-cols-3" aria-label="Avancement de la préparation financière">
                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                            <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Pointage</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{dossier.pointageGele ? 'Gelé' : 'À vérifier'}</p>
                            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{dossier.pointageGele ? 'Données de la période figées.' : 'Données encore susceptibles de changer.'}</p>
                        </div>
                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                            <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Bulletin mensuel</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{bulletinValide ? 'Validé' : bulletinEnAttente ? 'À valider' : 'Non calculé'}</p>
                            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{bulletinValide ? 'Rapproché lors du calcul STC.' : bulletinEnAttente ? 'La simulation attend sa validation.' : 'Le STC peut être estimé sans ce bulletin.'}</p>
                        </div>
                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                            <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Dossier STC</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{simulationNeedsRefresh ? 'À recalculer' : simulationResult || dossier.referenceSimulation ? 'Simulation disponible' : 'À simuler'}</p>
                            <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Le montant reste provisoire avant scellement.</p>
                        </div>
                    </div>
                )}

                {!loading && dossier && !dossier.idStc && (bulletinEnAttente || (!bulletinValide && dossier.pointageGele)) && (
                    <Alert
                        type="warning"
                        title={bulletinEnAttente ? 'Bulletin mensuel à valider' : 'Pointage gelé, bulletin mensuel absent'}
                        message={bulletinEnAttente
                            ? 'Un bulletin mensuel existe mais n’est pas validé. Le backend refuse son rapprochement avec le STC. Faites valider le bulletin, actualisez le dossier, puis relancez la simulation.'
                            : 'Une simulation STC reste possible avec les données de pointage et le prorata. Si un bulletin mensuel doit être établi, calculez et validez-le avant le scellement, puis relancez la simulation pour éviter un décompte sans rapprochement.'}
                    />
                )}
                {/* En-tête Salarié & Contrat */}
                <Card shadow="none" className="bg-[var(--color-bg-subtle)] p-4">
                    <div className="flex flex-wrap items-center justify-between gap-4">
                        <div className="flex min-w-0 items-center gap-3">
                            <div className="flex h-11 w-11 shrink-0 items-center justify-center border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-brand-600)] shadow-xs">
                                <User size={20} aria-hidden="true" />
                            </div>
                            <div className="min-w-0">
                                <h3 className="font-bold text-base text-[var(--color-fg)]">
                                    {dossier?.nomComplet || nomEmploye || 'Chargement...'}
                                </h3>
                                {dossier?.matricule && <p className="break-all text-xs text-[var(--color-fg-muted)]">Matricule : {dossier.matricule}</p>}
                                <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-2 flex-wrap">
                                    <span>{dossier?.intitulePoste || 'Poste non renseigné'}</span>
                                    <span>·</span>
                                    <span>{dossier?.departement || 'Département'}</span>
                                    <span>·</span>
                                    <span>Site : <strong className="text-[var(--color-fg)]">{dossier?.site || dossier?.codeSite || 'Siège'}</strong></span>
                                </p>
                            </div>
                        </div>

                        <div className="flex items-center gap-3 flex-wrap">
                            <div className="text-right text-xs">
                                <span className="text-[var(--color-fg-muted)] block">Type contrat & Ancienneté</span>
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {dossier?.typeContrat || 'CDI'} · {formatStcAnciennete(dossier?.ancienneteAnnees, dossier?.ancienneteMois)}
                                </span>
                            </div>
                            <Badge
                                variant={
                                    dossier?.statutDossier === 'CLOTURE' ? 'success' :
                                    dossier?.statutDossier === 'VALIDE' ? 'primary' :
                                    dossier?.statutDossier === 'PRET_A_VALIDER' ? 'info' : 'warning'
                                }
                                size="md"
                            >
                                {dossier?.statutDossier || 'EN_PREPARATION'}
                            </Badge>
                        </div>
                    </div>

                    {/* Paramètres de départ */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mt-4 pt-3 border-t border-[var(--color-border-subtle)]">
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Date effective de départ *
                            </label>
                            <Input
                                type="date"
                                value={dateDepart}
                                onChange={(e) => setDateDepart(e.target.value)}
                                disabled={!canWrite || Boolean(dossier?.idStc) || loading || saving}
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Motif de rupture légal *
                            </label>
                            <SearchSelect
                                options={motifsSortie.length > 0 ? motifsSortie : [
                                    { value: 'DEMISSION', label: 'Démission du salarié' },
                                    { value: 'LICENCIEMENT_SIMPLE', label: 'Licenciement simple' },
                                    { value: 'FIN_CONTRAT', label: 'Fin de contrat (CDD)' },
                                    { value: 'RUPTURE_CONVENTIONNELLE', label: 'Rupture conventionnelle d’un commun accord' },
                                    { value: 'RETRAITE', label: 'Départ à la retraite' },
                                    { value: 'FAUTE_LOURDE', label: 'Licenciement pour faute lourde' },
                                ]}
                                value={motifRupture}
                                onChange={(val) => setMotifRupture(val)}
                                disabled={!canWrite || Boolean(dossier?.idStc) || loading || saving}
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Salaire de base contractuel
                            </label>
                            <div className="h-[38px] px-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex items-center justify-between text-sm">
                                <span className="font-data font-semibold text-[var(--color-fg)]">
                                    {dossier?.salaireBase ? formatMoneyAr(dossier.salaireBase) : '–'}
                                </span>
                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.tauxHoraire ? `${formatMoneyAr(dossier.tauxHoraire)}/h` : ''}
                                </span>
                            </div>
                        </div>
                    </div>
                </Card>

                {/* Onglets du Dossier */}
                <Tabs tabs={dossierTabs} activeTab={activeTab} onChange={setActiveTab}
                    ariaLabel="Étapes du dossier de sortie" className="dossier-stc-tabs" />

                {/* CONTENU ONGLET 1 : Données sources en lecture */}
                {activeTab === 'synthese' && (
                    <div id="panel-synthese" role="tabpanel" aria-labelledby="tab-synthese" className="space-y-4">
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                            {/* Pointage */}
                            <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-[var(--color-brand-700)]">
                                        <Clock size={15} /> Pointage du mois
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-[var(--color-fg-subtle)]">
                                        {dossier?.sourcePointage || 'Pointage réel'}
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <div className="flex items-baseline gap-1.5">
                                        <span className="text-xl font-bold font-data text-[var(--color-fg)]">
                                            {dossier?.joursPointageReels ?? '0'} j
                                        </span>
                                        <span className="text-xs font-semibold text-[var(--color-fg-muted)]">
                                            travaillés ({dossier?.totalHeuresPointage ?? 0} h)
                                        </span>
                                    </div>
                                    <div className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        {dossier?.joursOuvresPeriode ? (
                                            <span>
                                                Sur <strong>{dossier.joursOuvresPeriode} j ouvrés</strong>
                                                {Number(dossier?.joursCongesPeriode || 0) > 0 && ` (+${dossier.joursCongesPeriode} j congé)`}
                                            </span>
                                        ) : (
                                            <span>Temps de présence période</span>
                                        )}
                                    </div>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] border-t border-[var(--color-border-subtle)] pt-2 space-y-1">
                                    <div className="flex justify-between">
                                        <span>Contrat sur période :</span>
                                        <strong className="font-data text-[var(--color-fg)]">
                                            {Number(dossier?.joursSousContrat || 30) >= 30 ? 'Mois complet (30 j)' : `${dossier?.joursSousContrat ?? 0} j calendaires`}
                                        </strong>
                                    </div>
                                    <div className="flex justify-between">
                                        <span>Retenue absence :</span>
                                        <strong className={`font-data ${Number(dossier?.heuresAbsenceRetenues || 0) > 0 ? 'text-[var(--color-danger-700)]' : 'text-[var(--color-fg)]'}`}>
                                            {Number(dossier?.heuresAbsenceRetenues || 0) > 0 ? `${dossier.heuresAbsenceRetenues} h` : '00h00 (0 Ar)'}
                                        </strong>
                                    </div>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.pointageGele ? 'Pointage gelé pour cette période.' : 'Pointage encore susceptible de changer.'}
                                </p>
                            </div>

                            {/* Congés payés */}
                            <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-[var(--color-success-700)]">
                                        <CalendarCheck size={15} /> Congés payés (FIFO)
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-[var(--color-fg-subtle)]">
                                        Solde acquis
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-[var(--color-success-700)]">
                                        {dossier?.soldeCongesRestant ?? '0'} j
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">
                                        reliquat restant
                                    </span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>ICCP estimée :</span>
                                    <strong className="font-data text-[var(--color-success-700)]">
                                        {dossier?.iccpEstimee ? formatMoneyAr(dossier.iccpEstimee) : '0 Ar'}
                                    </strong>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.demandesCongesEnAttente > 0 ? (
                                        <span className="text-[var(--color-warning-700)] font-medium">
                                            {dossier.demandesCongesEnAttente} demande(s) en attente de validation
                                        </span>
                                    ) : 'Aucune demande en attente.'}
                                </p>
                            </div>

                            {/* Paie mensuelle & Avances */}
                            <div className="p-3.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-[var(--color-info-700)]">
                                        <FileText size={15} /> Bulletin du mois
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-[var(--color-fg-subtle)]">
                                        {dossier?.statutBulletinMensuel || 'NON CALCULÉ'}
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-[var(--color-fg)]">
                                        {dossier?.netBulletinMensuel ? formatMoneyAr(dossier.netBulletinMensuel) : '0 Ar'}
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">Net</span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>Versé avant STC :</span>
                                    <strong className="font-data text-[var(--color-fg)]">
                                        {dossier?.montantBulletinDejaPaye ? formatMoneyAr(dossier.montantBulletinDejaPaye) : '0 Ar'}
                                    </strong>
                                </div>
                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                    {dossier?.soldeBulletinARegler ? (
                                        <span>{dossier.idStc ? (dossier.estIntegralementRegle ? 'Payé via le STC' : 'Repris dans le STC') : 'À intégrer au STC'} : <strong className="font-data">{formatMoneyAr(dossier.soldeBulletinARegler)}</strong></span>
                                    ) : bulletinValide ? 'Aucun solde mensuel indiqué.' : 'Aucun bulletin validé à rapprocher.'}
                                </p>
                            </div>
                        </div>

                        {dossier?.idBulletinMensuel && bulletinValide && !dossier?.idStc && (
                            <Card as="section" shadow="none" className="p-4 space-y-3" aria-label="Paiement du bulletin mensuel">
                                <div>
                                    <h4 className="text-sm font-bold text-[var(--color-fg)]">Paiement du bulletin mensuel</h4>
                                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                                        Bulletin #{dossier.idBulletinMensuel} · Net {formatMoneyAr(dossier.netBulletinMensuel)}. Le solde non payé sera repris dans le STC.
                                    </p>
                                </div>
                                <div className="grid gap-3 sm:grid-cols-3">
                                    <MoneyInput label="Montant déjà versé (Ar)" min={0} max={Number(dossier.netBulletinMensuel || 0)}
                                        value={montantBulletinDejaPaye} onValueChange={setMontantBulletinDejaPaye}
                                        disabled={!canWrite || saving || simulating} />
                                    <Input type="date" label="Date du paiement" value={datePaiementBulletin}
                                        onChange={(event) => setDatePaiementBulletin(event.target.value)}
                                        required={Number(montantBulletinDejaPaye || 0) > 0}
                                        disabled={!canWrite || saving || simulating || Number(montantBulletinDejaPaye || 0) === 0} />
                                    <Input label="Référence du paiement" value={referencePaiementBulletin}
                                        onChange={(event) => setReferencePaiementBulletin(event.target.value)}
                                        required={Number(montantBulletinDejaPaye || 0) > 0}
                                        disabled={!canWrite || saving || simulating || Number(montantBulletinDejaPaye || 0) === 0} />
                                </div>
                                <p className="text-xs font-medium text-[var(--color-fg)]">
                                    À reprendre dans le STC : {formatMoneyAr(Math.max(0, Number(dossier.netBulletinMensuel || 0) - Number(montantBulletinDejaPaye || 0)))}
                                </p>
                            </Card>
                        )}

                        {/* STC scellé si existant */}
                        {dossier?.idStc && !pageMode && (
                            <div className="p-4 bg-[var(--color-success-50)] border border-[var(--color-success-200)] text-[var(--color-success-950)] flex flex-wrap items-center justify-between gap-4">
                                <div className="flex items-center gap-3">
                                    <BadgeCheck size={24} className="text-[var(--color-success-700)] shrink-0" />
                                    <div>
                                        <h4 className="font-bold text-sm">STC scellé & validé : N° {dossier.numeroRecu}</h4>
                                        <p className="text-xs text-[var(--color-success-800)]">
                                            Montant net final : <strong className="font-data">{formatMoneyAr(dossier.netAPayerStc)}</strong>
                                            {dossier.dateReglement && ` · Réglé le ${fmtDate(dossier.dateReglement)} (${dossier.referenceReglement || 'Comptabilisé'})`}
                                        </p>
                                    </div>
                                </div>
                                <Badge variant="success" size="md">Scellé</Badge>
                            </div>
                        )}
                    </div>
                )}

                {/* CONTENU ONGLET 2 : Checklist dynamique */}
                {activeTab === 'checklist' && (
                    <div id="panel-checklist" role="tabpanel" aria-labelledby="tab-checklist" className="space-y-3">
                        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-[var(--color-fg-muted)] px-1">
                            <span>Contrôles du départ · les points à traiter apparaissent en priorité.</span>
                            <span className="font-medium text-[var(--color-fg)]">
                                {dossier?.checklist?.filter(c => c.statut === 'CONFORME').length || 0} / {dossier?.checklist?.length || 0} conformes
                            </span>
                        </div>

                        <div className="space-y-2">
                            {[...(dossier?.checklist || [])].sort((a, b) => (a.statut === 'CONFORME') - (b.statut === 'CONFORME')).map((item, idx) => {
                                const st = getBadgeStatut(item.statut);
                                const src = getBadgeSource(item.sourceNorme);
                                const IconComp = st.icon;
                                const cibleModule = item.lienModule === 'carriere' && dossier?.idEmploye && hasPermission('EMPLOYE_READ')
                                    ? `/employes/${dossier.idEmploye}?tab=carriere`
                                    : item.lienModule === 'conges' && hasPermission('CONGE_READ')
                                        ? '/conges'
                                        : item.lienModule === 'pointage' && hasPermission('POINTAGE_READ') && ['ROLE_ADMIN', 'ROLE_AGENT_POINTAGE'].includes(user?.role)
                                            ? '/pointages'
                                            : null;

                                return (
                                    <div
                                        key={item.code || idx}
                                        className={`px-3 py-2.5 border flex flex-col sm:flex-row sm:items-start justify-between gap-2 ${
                                            item.statut === 'NON_CONFORME' && item.niveau === 'BLOQUANT'
                                                ? 'bg-[var(--color-surface)] border-[var(--color-danger-500)] border-l-4'
                                                : item.statut !== 'CONFORME'
                                                    ? 'bg-[var(--color-surface)] border-[var(--color-warning-500)] border-l-4'
                                                    : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)]'
                                        }`}
                                    >
                                        <div className="flex items-start gap-2.5 min-w-0">
                                            <IconComp size={18} className={`shrink-0 mt-0.5 ${
                                                st.variant === 'danger' ? 'text-[var(--color-danger-600)]' :
                                                st.variant === 'warning' ? 'text-[var(--color-warning-600)]' : 'text-[var(--color-success-600)]'
                                            }`} />
                                            <div>
                                                <div className="flex items-center gap-2 flex-wrap mb-0.5">
                                                    <h4 className="text-xs font-bold text-[var(--color-fg)]">
                                                        {item.titre}
                                                    </h4>
                                                    {item.statut !== 'CONFORME' && <>
                                                        <Badge variant={src.variant} size="xs">{src.label}</Badge>
                                                        <Badge variant={getBadgeSeverity(item.niveau)} size="xs">{item.niveau}</Badge>
                                                    </>}
                                                </div>
                                                <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                                    {item.message}
                                                </p>
                                                {item.statut !== 'CONFORME' && item.actionRecommandee && (
                                                    <p className="text-[11px] text-[var(--color-fg)] font-medium mt-1">
                                                        → Action recommandée : {item.actionRecommandee}
                                                    </p>
                                                )}
                                            </div>
                                        </div>

                                        <div className="flex sm:flex-col items-end justify-between sm:justify-start gap-2 shrink-0">
                                            <Badge variant={st.variant} size="sm">
                                                {st.label}
                                            </Badge>
                                            {item.lienModule === 'documents' && (
                                                <button type="button" onClick={() => setActiveTab('suivi')}
                                                    className="text-[11px] font-semibold text-[var(--color-fg)] hover:underline">
                                                    Voir les documents
                                                </button>
                                            )}
                                            {cibleModule && (
                                                <a
                                                    href={cibleModule}
                                                    target="_blank"
                                                    rel="noopener noreferrer"
                                                    className="text-[11px] font-semibold text-[var(--color-fg)] hover:underline inline-flex items-center gap-1"
                                                >
                                                    Ouvrir le module <ExternalLink size={12} />
                                                </a>
                                            )}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 3 : Prêts & Règlements */}
                {activeTab === 'pret' && (
                    <div id="panel-pret" role="tabpanel" aria-labelledby="tab-pret" className="space-y-4">
                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3">
                            <h4 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Banknote size={16} className="text-[var(--color-brand-600)]" />
                                État des prêts & avances en cours
                            </h4>

                            {hasLoans ? (
                                <div className="space-y-3">
                                    <div className="p-3 bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] text-[var(--color-warning-900)] text-xs rounded-none flex items-start gap-2.5">
                                        <AlertTriangle size={18} className="text-[var(--color-warning-700)] shrink-0 mt-0.5" />
                                        <div>
                                            <p className="font-bold">Prêt d'entreprise en cours de remboursement</p>
                                            <p className="mt-0.5">
                                                Capital restant dû au départ : <strong className="font-data text-sm text-[var(--color-warning-950)]">{formatMoneyAr(dossier.capitalRestantPrets)}</strong>.
                                            </p>
                                            <p className="text-[11px] mt-1 text-[var(--color-warning-800)]">
                                                Règle juridique impérative : le solde d'un prêt ne peut pas être déduit d'office sans décision documentée et respect de la quotité saisissable légale.
                                            </p>
                                        </div>
                                    </div>

                                    <div className="space-y-3 pt-2">
                                        <div>
                                            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                Décision de gestion du prêt au départ du salarié *
                                            </label>
                                            <SearchSelect
                                                options={OPTIONS_DECISION_PRET}
                                                value={decisionPret}
                                                onChange={(val) => setDecisionPret(val)}
                                                disabled={!canWrite || saving}
                                            />
                                        </div>

                                        {decisionPret === 'RETENUE_STC_AUTORISEE' && (
                                            <div>
                                                <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                    Montant de retenue convenu sur le STC (dans la limite de la quotité légale) *
                                                </label>
                                                <Input
                                                    type="number"
                                                    value={montantPretRetenu}
                                                    onChange={(e) => setMontantPretRetenu(e.target.value)}
                                                    placeholder="Montant en Ariary"
                                                    disabled={!canWrite || saving}
                                                />
                                            </div>
                                        )}

                                        <div>
                                            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                                                Justification écrite & traçabilité de la décision *
                                            </label>
                                            <Textarea
                                                rows={3}
                                                placeholder="Indiquez les accords avec le collaborateur, les références de chèque de remboursement ou les termes de maintien..."
                                                value={justificationDecisionPret}
                                                onChange={(e) => setJustificationDecisionPret(e.target.value)}
                                                disabled={!canWrite || saving}
                                            />
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <Alert type="success" title="Aucun prêt en cours" message="Aucune décision de remboursement n'est nécessaire pour ce départ." />
                            )}
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 4 : Décompte & Scellement STC */}
                {activeTab === 'decompte' && (
                    <div id="panel-decompte" role="tabpanel" aria-labelledby="tab-decompte" className="space-y-4">
                        {simulationNeedsRefresh && <Alert type="warning" title="Simulation à actualiser" message="Le décompte détaillé n'est plus affichable après rechargement ou la préparation a changé. Relancez la simulation pour contrôler les rubriques avant le scellement." />}
                        {/* En-tête de la simulation & Référence d'intégrité */}
                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3">
                            <div className="flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center gap-2">
                                    <Calculator size={18} className="text-[var(--color-brand-600)]" />
                                    <div>
                                        <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                            Décompte financier Solde de Tout Compte (STC)
                                        </h4>
                                        <p className="text-xs text-[var(--color-fg-muted)]">
                                            Simulation à vérifier avant le scellement du résultat financier.
                                        </p>
                                    </div>
                                </div>
                                <div className="flex items-center gap-2">
                                    {dossier?.statutDossier === 'VALIDE' || dossier?.statutDossier === 'CLOTURE' ? (
                                        <Badge variant="success" size="md">
                                            <ShieldCheck size={14} className="inline mr-1" /> Scellé & Verrouillé
                                        </Badge>
                                    ) : (
                                        <Button
                                            variant="secondary"
                                            size="sm"
                                            onClick={handleSimulerStc}
                                            loading={simulating || saving}
                                            disabled={!canWrite || sealing || bulletinEnAttente}
                                            iconLeft={<Calculator size={14} />}
                                        >
                                            {simulationResult || dossier?.referenceSimulation ? 'Recalculer la simulation' : (!dossier?.idDossierSortie ? 'Enregistrer et calculer la simulation STC' : 'Calculer la simulation STC')}
                                        </Button>
                                    )}
                                </div>
                            </div>

                            {/* Référence vérifiable */}
                            {(simulationResult?.referenceSimulation || dossier?.referenceSimulation) ? (
                                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs flex flex-wrap items-center justify-between gap-2">
                                    <div className="flex items-center gap-2">
                                        <Lock size={15} className="text-[var(--color-fg-muted)]" />
                                        <div>
                                            <span className="font-semibold text-[var(--color-fg)]">Référence de calcul vérifiable : </span>
                                            <code className="font-mono bg-[var(--color-surface)] px-2 py-0.5 border border-[var(--color-border-subtle)] text-[var(--color-fg)] font-bold select-all">
                                                {simulationResult?.referenceSimulation || dossier?.referenceSimulation}
                                            </code>
                                        </div>
                                    </div>
                                    <div className="text-[var(--color-fg-subtle)] font-mono text-[11px]">
                                        Version dossier : <strong>v{dossier?.version || simulationResult?.version || 1}</strong>
                                    </div>
                                </div>
                            ) : (
                                <div className="p-4 bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] text-xs text-[var(--color-warning-900)] flex items-center gap-2">
                                    <Info size={16} className="text-[var(--color-warning-700)] shrink-0" />
                                    <span>
                                        {!dossier?.idDossierSortie
                                            ? <>Dossier préparatoire non encore scellé. Cliquez sur <strong>« Enregistrer et calculer la simulation STC »</strong> pour persister le dossier et générer le décompte.</>
                                            : <>Aucune simulation active. Cliquez sur <strong>« Calculer la simulation STC »</strong> pour générer l'empreinte vérifiable et le décompte chiffré.</>}
                                    </span>
                                </div>
                            )}
                        </div>

                        {/* Détail du Décompte */}
                        {(() => {
                            const decompte = simulationNeedsRefresh ? null : simulationResult?.decompte || dossier?.snapshotSimulation;
                            if (!decompte) return null;

                            const soldeBulletin = Number(decompte.soldeBulletinARegler || 0);
                            const netFinal = Number(decompte.netAPayerStc || 0);
                            const totalDed = Number(decompte.soldeAvancesDeduit || 0) + Number(decompte.soldePretsDeduit || 0);
                            const netIndemnites = Math.max(0, netFinal - soldeBulletin + totalDed);

                            // Séparation des rubriques salariales et patronales (comme dans le Bulletin de Paie)
                            const lignesSal = (decompte.lignes || []).filter(l => l.sens !== 'PATRONAL' && !l.code?.endsWith('_PAT'));
                            const lignesPat = (decompte.lignes || []).filter(l => l.sens === 'PATRONAL' || l.code?.endsWith('_PAT'));

                            return (
                                <div className="space-y-4">
                                    {/* Bandeau de synthèse : Provenance & Réconciliation du Net STC (inspiré du Bulletin de Paie) */}
                                    <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-3">
                                        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-2.5">
                                            <div className="flex items-center gap-2">
                                                <Calculator size={16} className="text-[var(--color-brand-600)]" />
                                                <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                                    Provenance & Réconciliation du Net STC à Payer
                                                </h5>
                                            </div>
                                            <span className="text-[11px] text-[var(--color-fg-muted)]">
                                                Décomposition financière officielle conforme au bulletin
                                            </span>
                                        </div>

                                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                            {/* Pilier 1 : Bulletin du mois clôturé */}
                                            <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-1">
                                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                                    1. Salaire net du dernier mois
                                                </span>
                                                <div className="font-data text-base font-bold text-[var(--color-fg)]">
                                                    +{formatMoneyAr(soldeBulletin)}
                                                </div>
                                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                                    Net issu du bulletin mensuel déjà clôturé, rattaché pour versement groupé.
                                                </p>
                                            </div>

                                            {/* Pilier 2 : Solde net des congés & indemnités */}
                                            <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-1">
                                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                                    2. Solde net congés & départ
                                                </span>
                                                <div className="font-data text-base font-bold text-[var(--color-fg)]">
                                                    +{formatMoneyAr(netIndemnites)}
                                                </div>
                                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                                    ICCP brut ({decompte.soldeCongesRestant ?? 0}j) moins déductions sociales & IRSA.
                                                </p>
                                            </div>

                                            {/* Pilier 3 : Déductions / Prêts / Avances */}
                                            <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-1">
                                                <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] block">
                                                    3. Déductions (Prêts / Acomptes)
                                                </span>
                                                <div className="font-data text-base font-bold text-[var(--color-danger-600)]">
                                                    -{formatMoneyAr(totalDed)}
                                                </div>
                                                <p className="text-[11px] text-[var(--color-fg-muted)]">
                                                    {totalDed > 0 ? 'Retenues appliquées sur le décompte de départ.' : 'Aucune déduction de prêt ou avance.'}
                                                </p>
                                            </div>

                                        </div>
                                    </div>

                                    {/* Grille des montants détaillés */}
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5">
                                        {/* Bloc 1: Droits bruts */}
                                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3">
                                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                                <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-700)] flex items-center gap-1.5">
                                                    <Coins size={14} /> 1. Droits Bruts
                                                </h5>
                                                <span className="text-[10px] font-semibold text-[var(--color-fg-subtle)] uppercase">STC</span>
                                            </div>
                                            <div className="space-y-2 text-xs">
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Salaire prorata :</span>
                                                    <span className="font-data font-semibold text-[var(--color-fg)]">{formatMoneyAr(decompte.salaireProrata || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">ICCP ({decompte.soldeCongesRestant ?? 0} j) :</span>
                                                    <span className="font-data font-semibold text-[var(--color-fg)]">{formatMoneyAr(decompte.indemniteCongesPayes || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Préavis ({decompte.moisPreavis ?? 0} m) :</span>
                                                    <span className="font-data font-semibold text-[var(--color-fg)]">{formatMoneyAr(decompte.indemnitePreavis || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Indemnité rupture :</span>
                                                    <span className="font-data font-semibold text-[var(--color-fg)]">{formatMoneyAr(decompte.indemniteLicenciement || 0)}</span>
                                                </div>
                                                <div className="pt-2.5 border-t border-[var(--color-border-subtle)] flex justify-between items-baseline font-bold">
                                                    <span className="text-[var(--color-fg)]">Total Brut STC :</span>
                                                    <span className="font-data text-sm text-[var(--color-brand-700)]">{formatMoneyAr(decompte.totalBrutStc || 0)}</span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Bloc 2: Cotisations & Fiscalité */}
                                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3">
                                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                                <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-700)] flex items-center gap-1.5">
                                                    <Receipt size={14} /> 2. Cotisations & Impôt
                                                </h5>
                                                <span className="text-[10px] font-semibold text-[var(--color-fg-subtle)] uppercase">Légal</span>
                                            </div>
                                            <div className="space-y-2 text-xs">
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">CNaPS Salariale (1%) :</span>
                                                    <span className="font-data font-semibold text-[var(--color-danger-600)]">-{formatMoneyAr(decompte.cotisationCnaps || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">Santé / OSTIE (1%) :</span>
                                                    <span className="font-data font-semibold text-[var(--color-danger-600)]">-{formatMoneyAr(decompte.cotisationSante || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center bg-[var(--color-bg-subtle)] px-2 py-1 border border-[var(--color-border-subtle)]">
                                                    <span className="text-[var(--color-fg-muted)] text-[11px]">Net Imposable :</span>
                                                    <span className="font-data font-semibold text-xs text-[var(--color-fg)]">{formatMoneyAr(decompte.netImposable || 0)}</span>
                                                </div>
                                                <div className="flex justify-between items-center">
                                                    <span className="text-[var(--color-fg-muted)]">IRSA net :</span>
                                                    <span className="font-data font-semibold text-[var(--color-danger-600)]">-{formatMoneyAr(decompte.irsaNet || 0)}</span>
                                                </div>
                                                <div className="pt-2.5 border-t border-[var(--color-border-subtle)] flex justify-between items-baseline font-bold">
                                                    <span className="text-[var(--color-fg)]">Déductions Légales :</span>
                                                    <span className="font-data text-sm text-[var(--color-danger-700)]">
                                                        -{formatMoneyAr((decompte.totalCotisationsSociales || 0) + (decompte.irsaNet || 0))}
                                                    </span>
                                                </div>
                                            </div>
                                        </div>

                                        {/* Bloc 3: Déductions & Net à payer */}
                                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3 flex flex-col justify-between">
                                            <div>
                                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                                    <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-success-800)] flex items-center gap-1.5">
                                                        <Wallet size={14} /> 3. Déductions & Net
                                                    </h5>
                                                    <span className="text-[10px] font-semibold text-[var(--color-success-700)] uppercase font-bold">À verser</span>
                                                </div>
                                                <div className="space-y-2 text-xs pt-1">
                                                    {soldeBulletin > 0 && (
                                                        <div className="flex justify-between items-center">
                                                            <span className="text-[var(--color-fg-muted)]">Bulletin mois à régler :</span>
                                                            <span className="font-data font-semibold text-[var(--color-fg)]">
                                                                +{formatMoneyAr(soldeBulletin)}
                                                            </span>
                                                        </div>
                                                    )}
                                                    <div className="flex justify-between items-center">
                                                        <span className="text-[var(--color-fg-muted)]">Solde net départ (ICCP) :</span>
                                                        <span className="font-data font-semibold text-[var(--color-fg)]">
                                                            +{formatMoneyAr(netIndemnites)}
                                                        </span>
                                                    </div>
                                                    <div className="flex justify-between items-center">
                                                        <span className="text-[var(--color-fg-muted)]">Avances déduites :</span>
                                                        <span className="font-data font-semibold text-[var(--color-danger-600)]">
                                                            {Number(decompte.soldeAvancesDeduit || 0) > 0 ? `-${formatMoneyAr(decompte.soldeAvancesDeduit)}` : '0 Ar'}
                                                        </span>
                                                    </div>
                                                    <div className="flex justify-between items-center">
                                                        <span className="text-[var(--color-fg-muted)]">Prêts déduits :</span>
                                                        <span className="font-data text-[var(--color-fg-muted)]">
                                                            {Number(decompte.soldePretsDeduit || 0) > 0 ? `-${formatMoneyAr(decompte.soldePretsDeduit)}` : '0 Ar (Maintien séparé)'}
                                                        </span>
                                                    </div>
                                                </div>
                                            </div>

                                            <div className="p-3 bg-[var(--color-success-50)] border-2 border-[var(--color-success-500)]/40 mt-2">
                                                <div className="flex items-center justify-between">
                                                    <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-success-900)]">
                                                        Net STC à Payer
                                                    </span>
                                                    <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 bg-[var(--color-success-600)] text-white">
                                                        Net final
                                                    </span>
                                                </div>
                                                <div className="font-data text-2xl font-black text-[var(--color-success-800)] mt-1 tracking-tight">
                                                    {formatMoneyAr(decompte.netAPayerStc || 0)}
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    {/* Lignes du décompte : inspiré de la clarté du Bulletin de Paie */}
                                    {decompte.lignes && decompte.lignes.length > 0 && (
                                        <div className="space-y-4 pt-1">
                                            {/* 1. Tableau Résultats Salarié */}
                                            <div className="space-y-2">
                                                <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] px-1">
                                                    <span className="font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                                        <FileSpreadsheet size={15} className="text-[var(--color-brand-600)]" />
                                                        Résultats salarié · {lignesSal.length} rubriques
                                                    </span>
                                                    <span>Montants en Ariary (MGA)</span>
                                                </div>
                                                <Table density="compact" bordered={true} rowHover={true} aria-label="Rubriques salarié du décompte STC">
                                                    <Thead>
                                                        <Tr>
                                                            <Th className="w-28">Nature</Th>
                                                            <Th>Désignation</Th>
                                                            <Th className="text-right w-36">Base ou taux</Th>
                                                            <Th className="text-right w-36">Gain</Th>
                                                            <Th className="text-right w-36">Retenue</Th>
                                                        </Tr>
                                                    </Thead>
                                                    <Tbody>
                                                        {lignesSal.map((l, idx) => {
                                                            const isRetenue = l.sens === 'RETENUE';
                                                            const isGain = l.sens === 'GAIN';
                                                            const isBulletinRapproche = l.code === 'BULLETIN_MENSUEL_RAPPROCHE';

                                                            // Base ou taux formaté
                                                            const baseOuTaux = l.base ? formatMoneyAr(l.base) : (l.taux ? `${l.taux}%` : '—');

                                                            // Montants
                                                            const gainVal = (isGain || isBulletinRapproche) ? l.montant : null;
                                                            const retVal = isRetenue ? l.montant : null;

                                                            return (
                                                                <Tr
                                                                    key={idx}
                                                                    className={
                                                                        isRetenue
                                                                            ? 'bg-[var(--color-danger-50)]/20'
                                                                            : isBulletinRapproche
                                                                                ? 'bg-[var(--color-brand-50)]/40 font-medium'
                                                                                : ''
                                                                    }
                                                                >
                                                                    <Td>
                                                                        {isBulletinRapproche ? (
                                                                            <span className="text-xs font-semibold text-[var(--color-brand-700)]">
                                                                                Mois en cours
                                                                            </span>
                                                                        ) : isGain ? (
                                                                            <Badge variant="success" size="xs">Gain</Badge>
                                                                        ) : isRetenue ? (
                                                                            <Badge variant="danger" size="xs">Retenue</Badge>
                                                                        ) : (
                                                                            <Badge variant="secondary" size="xs">Info</Badge>
                                                                        )}
                                                                    </Td>
                                                                    <Td className={`text-xs ${isBulletinRapproche ? 'font-semibold text-[var(--color-brand-900)]' : 'font-medium text-[var(--color-fg)]'}`}>
                                                                        {l.libelle}
                                                                    </Td>
                                                                    <Td className="text-right font-data text-xs text-[var(--color-fg-muted)]">
                                                                        {baseOuTaux}
                                                                    </Td>
                                                                    <Td className="text-right font-data font-semibold text-xs text-[var(--color-success-700)]">
                                                                        {gainVal != null ? formatMoneyAr(gainVal) : <span className="text-[var(--color-fg-subtle)] font-normal">—</span>}
                                                                    </Td>
                                                                    <Td className="text-right font-data font-semibold text-xs text-[var(--color-danger-600)]">
                                                                        {retVal != null ? `-${formatMoneyAr(retVal)}` : <span className="text-[var(--color-fg-subtle)] font-normal">—</span>}
                                                                    </Td>
                                                                </Tr>
                                                            );
                                                        })}
                                                    </Tbody>
                                                </Table>
                                            </div>

                                            {/* 2. Tableau Charges Patronales (Coût Employeur) */}
                                            {lignesPat.length > 0 && (
                                                <div className="space-y-2 pt-1">
                                                    <div className="flex items-center justify-between text-xs text-[var(--color-fg-muted)] px-1">
                                                        <span className="font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                                            <Building2 size={15} className="text-[var(--color-brand-600)]" />
                                                            Charges patronales · {lignesPat.length} rubriques (Coût Employeur)
                                                        </span>
                                                        <span className="text-[11px] text-[var(--color-fg-muted)]">À la charge exclusive de l’employeur</span>
                                                    </div>
                                                    <Table density="compact" bordered={true} rowHover={true} aria-label="Charges patronales STC">
                                                        <Thead>
                                                            <Tr>
                                                                <Th>Désignation</Th>
                                                                <Th className="text-right w-36">Base</Th>
                                                                <Th className="text-right w-24">Taux</Th>
                                                                <Th className="text-right w-36">Cotisation patronale</Th>
                                                            </Tr>
                                                        </Thead>
                                                        <Tbody>
                                                            {lignesPat.map((l, idx) => (
                                                                <Tr key={idx} className="bg-[var(--color-bg-subtle)]/50">
                                                                    <Td className="font-medium text-xs text-[var(--color-fg)]">
                                                                        {l.libelle}
                                                                    </Td>
                                                                    <Td className="text-right font-data text-xs text-[var(--color-fg-muted)]">
                                                                        {l.base ? formatMoneyAr(l.base) : '—'}
                                                                    </Td>
                                                                    <Td className="text-right font-data text-xs text-[var(--color-fg-muted)]">
                                                                        {l.taux ? `${l.taux}%` : '—'}
                                                                    </Td>
                                                                    <Td className="text-right font-data font-semibold text-xs text-[var(--color-fg)]">
                                                                        {formatMoneyAr(l.montant)}
                                                                    </Td>
                                                                </Tr>
                                                            ))}
                                                        </Tbody>
                                                    </Table>
                                                </div>
                                            )}
                                        </div>
                                    )}

                                    {/* Zone de scellement transactionnel si non scellé */}
                                    {dossier?.statutDossier !== 'VALIDE' && dossier?.statutDossier !== 'CLOTURE' ? (
                                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-success-500)]/40 shadow-sm space-y-3">
                                            <div className="flex items-start gap-3">
                                                <div className="p-2 bg-[var(--color-success-100)] text-[var(--color-success-700)] shrink-0">
                                                    <ShieldCheck size={20} />
                                                </div>
                                                <div className="space-y-1">
                                                    <h5 className="text-sm font-bold text-[var(--color-fg)]">
                                                        Confirmation et scellement transactionnel du STC
                                                    </h5>
                                                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                                        Le scellement effectue un contrôle de fraîcheur transactionnel sur les données de calcul (pointage, congés, contrat).
                                                        Le résultat net affiché de <strong className="font-data font-bold text-[var(--color-success-700)]">{formatMoneyAr(decompte.netAPayerStc || 0)}</strong> sera scellé dans le registre financier officiel <code>paie_stc</code>.
                                                    </p>
                                                </div>
                                            </div>

                                            {nbBloquants > 0 && (
                                                <Alert
                                                    type="error"
                                                    title="Scellement bloqué par la checklist"
                                                    message={`Le dossier comporte ${nbBloquants} point(s) bloquant(s). Veuillez les résoudre avant de sceller.`}
                                                />
                                            )}

                                            <div className="pt-1">
                                                <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] mb-1">
                                                    Commentaire ou référence de validation (optionnel)
                                                </label>
                                                <Input
                                                    type="text"
                                                    placeholder="Ex: Validation après vérification des heures et accord de sortie..."
                                                    value={commentaireScellement}
                                                    onChange={(e) => setCommentaireScellement(e.target.value)}
                                                    disabled={!canWrite || sealing}
                                                />
                                            </div>

                                            {!pageMode && <div className="flex items-center justify-end gap-3 pt-2">
                                                <HasPermission authority="PAIE_WRITE"><Button
                                                    variant="primary"
                                                    onClick={() => setConfirmScellement(true)}
                                                    loading={sealing}
                                                    disabled={nbBloquants > 0 || hasUnsavedPreparation || simulationNeedsRefresh || bulletinEnAttente || !(simulationResult?.referenceSimulation || dossier?.referenceSimulation)}
                                                    iconLeft={<ShieldCheck size={16} />}
                                                >
                                                    Confirmer et sceller définitivement le STC
                                                </Button></HasPermission>
                                            </div>}
                                        </div>
                                    ) : (
                                        <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-between">
                                            <div className="flex items-center gap-2.5">
                                                <Lock size={18} className="text-[var(--color-fg-muted)]" />
                                                <div>
                                                    <p className="text-xs font-bold text-[var(--color-fg)]">
                                                        STC scellé et verrouillé · N° STC : #{dossier.idStc}
                                                    </p>
                                                    <p className="text-[11px] text-[var(--color-fg-muted)]">
                                                        Ce décompte est définitivement figé et ne peut plus être recalculé.
                                                    </p>
                                                </div>
                                            </div>
                                            <div className="flex items-center gap-2">
                                                <Badge variant="success" size="md">
                                                    Scellé
                                                </Badge>
                                                {onOpenStcModal && (
                                                    <Button
                                                        variant="secondary"
                                                        size="sm"
                                                        onClick={() => onOpenStcModal(dossier)}
                                                        iconRight={<ExternalLink size={13} />}
                                                    >
                                                        Afficher le STC
                                                    </Button>
                                                )}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })()}
                    </div>
                )}

                {activeTab === 'suivi' && <div id="panel-suivi" role="tabpanel" aria-labelledby="tab-suivi"><DossierSortieSuiviPanel dossier={dossier} onDossierChange={updated => { setDossier(updated); onSaved?.(updated); }} /></div>}
            </div>
            <ConfirmDialog isOpen={confirmScellement} onClose={() => setConfirmScellement(false)}
                onConfirm={() => { setConfirmScellement(false); handleScellerStc(); }}
                title="Sceller définitivement le STC"
                message="Le résultat financier de cette simulation sera figé. Vérifiez les rubriques avant de confirmer."
                confirmLabel="Confirmer le scellement" variant="warning" loading={sealing} />
            <ConfirmDialog isOpen={confirmAbandon} onClose={() => setConfirmAbandon(false)}
                onConfirm={() => { setConfirmAbandon(false); onClose(); }}
                title="Quitter sans enregistrer"
                message="Les modifications de préparation non enregistrées seront perdues."
                confirmLabel="Quitter sans enregistrer" variant="warning" />
        </DossierShell>
    );
}
