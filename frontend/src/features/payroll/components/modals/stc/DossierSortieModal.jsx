import { useState, useEffect, useCallback, useRef } from 'react';
import {
    ArrowLeft,
    BadgeCheck,
    Building2,
    Calculator,
    CalendarCheck,
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
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Alert from '@/components/ui/feedback/Alert';
import { Tabs } from '@/components/ui/navigation/Tabs';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { formatStcAnciennete } from '@/features/payroll/utils/stcDisplay';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import DossierSortieSuiviPanel from './DossierSortieSuiviPanel';
import DeparturePayrollPanel from './DeparturePayrollPanel';
import DossierSortieWorkspaceShell from './DossierSortieWorkspaceShell';
import HasPermission from '@/features/auth/components/HasPermission';
import { useAuth } from '@/context/AuthContext';
import contratService from '@/features/employees/api/contratService';
import { optionsMotifsSortie } from '@/features/employees/utils/motifsSortie';
import { getErrorMessage } from '@/api/apiClient';


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
    const [validatingRupture, setValidatingRupture] = useState(false);
    const [confirmRupture, setConfirmRupture] = useState(false);
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
    const [activeTab, setActiveTab] = useState('synthese'); // 'synthese' | 'checklist' | 'decompte' | 'suivi'
    const initialViewRef = useRef(false);

    // Formulaire de saisie éditable
    const [dateDepart, setDateDepart] = useState('');
    const [motifRupture, setMotifRupture] = useState('DEMISSION');
    const [montantBulletinDejaPaye, setMontantBulletinDejaPaye] = useState('0');
    const [datePaiementBulletin, setDatePaiementBulletin] = useState('');
    const [referencePaiementBulletin, setReferencePaiementBulletin] = useState('');

    // Chargement des motifs de rupture légaux
    useEffect(() => {
        if (!isOpen) return;
        contratService.getMotifsSortie()
            .then(data => setMotifsSortie(optionsMotifsSortie(data).filter(item => item.value !== 'AUTRE')))
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
                        : String(data.dateDepart || '').slice(0, 10) > getTodayDateString() ? 'decompte'
                        : data.referenceSimulation ? 'decompte'
                            : data.checklist?.some(item => item.niveau === 'BLOQUANT' && item.statut === 'NON_CONFORME')
                                ? 'checklist' : 'synthese');
                    initialViewRef.current = true;
                }
                // Le DTO recharge la référence de simulation, mais pas ses rubriques détaillées.
                setSimulationNeedsRefresh(Boolean(data.referenceSimulation && !data.idStc && !data.snapshotSimulation));
                setDateDepart(String(data.dateDepart || dateDepartInitiale || getTodayDateString()).slice(0, 10));
                setMotifRupture(data.motifRupture || motifInitial || 'DEMISSION');
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

    const handleValiderRupture = async () => {
        if (!dossier?.idWorkflowRh || dossier.statutWorkflowRh !== 'BROUILLON') return;
        setValidatingRupture(true);
        setErrorMsg(null);
        try {
            await contratService.validerWorkflow(dossier.idWorkflowRh);
            await chargerDossier();
            toast.success('Fin de contrat validée. Le dossier STC reste ouvert pour la suite des formalités.');
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible de valider la fin de contrat.'));
        } finally {
            setValidatingRupture(false);
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



    const nbBloquants = dossier?.checklist?.filter(c => c.niveau === 'BLOQUANT' && c.statut === 'NON_CONFORME').length || 0;
    const departFutur = Boolean(dateDepart && dateDepart > getTodayDateString());
    const ruptureFigee = Boolean(dossier?.idWorkflowRh && dossier?.statutWorkflowRh !== 'BROUILLON');
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
    );
    const prochaineEtape = !dossier?.idDossierSortie
        ? 'Indiquez la date de départ, puis enregistrez pour préparer une estimation.'
        : dossier?.idStc
            ? 'Le document final est prêt. Retrouvez les paiements et les documents dans le suivi.'
            : departFutur
                ? 'Le départ est à venir : préparez une estimation maintenant. Le document final sera disponible à partir du jour du départ, après vérification des données.'
            : nbBloquants > 0
                ? `${nbBloquants} point(s) restent à régler. Ouvrez « À vérifier » pour savoir quoi faire.`
                : simulationNeedsRefresh
                    ? 'La préparation a changé. Relancez la simulation avant tout scellement.'
                : simulationResult || dossier?.referenceSimulation
                    ? 'Vérifiez le montant estimé et son détail avant de confirmer le document final.'
                    : 'Le dossier est enregistré. Calculez une estimation, puis vérifiez le détail.';
    const prochaineSection = !dossier?.idDossierSortie ? 'synthese' : dossier?.idStc ? 'suivi' : departFutur ? 'decompte' : nbBloquants > 0 ? 'checklist' : 'decompte';
    const dossierTabs = [
        { id: 'synthese', label: '1. Départ', icon: FileSpreadsheet },
        { id: 'checklist', label: '2. Contrôles', icon: ShieldAlert, ...(nbBloquants > 0 ? { count: nbBloquants } : {}) },
        { id: 'paie-finale', label: '3. Dernier salaire', icon: Calculator },
        { id: 'decompte', label: '4. Estimation', icon: Calculator },
        { id: 'suivi', label: '5. Suivi & documents', icon: FileText },
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
                    {!pageMode && <section className={`flex flex-wrap items-center justify-between gap-3 border px-3.5 py-2.5 ${
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
                                        ? departFutur ? 'Estimation disponible — départ à venir' : 'Estimation disponible — à confirmer'
                                        : dossier?.pretAValider
                                            ? 'Avant le calcul STC'
                                            : 'Préparation du dossier de sortie'}
                            </h3>
                            <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">
                                {dossier?.idStc
                                    ? `Dossier de sortie scellé #${dossier.idStc}. Les montants et décomptes sont définitivement scellés.`
                                    : (simulationResult?.referenceSimulation || dossier?.referenceSimulation)
                                        ? departFutur ? 'Montant provisoire. Le document final sera confirmé après le départ.' : 'Vérifiez le détail avant de confirmer le document final.'
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
                                            Enregistrer le départ
                                        </Button>
                                    ) : bulletinEnAttente ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('synthese')}>
                                            Vérifier les données de paie
                                        </Button>
                                    ) : nbBloquants > 0 && !departFutur ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('checklist')}>
                                            Voir ce qui reste à faire
                                        </Button>
                                    ) : simulationNeedsRefresh || !dossier.referenceSimulation ? (
                                        <Button variant="primary" size="sm" onClick={handleSimulerStc} loading={simulating}
                                            disabled={saving || sealing} iconLeft={<Calculator size={15} />}>
                                            {simulationNeedsRefresh ? 'Mettre à jour l’estimation' : 'Calculer une estimation'}
                                        </Button>
                                    ) : activeTab !== 'decompte' ? (
                                        <Button variant="primary" size="sm" onClick={() => setActiveTab('decompte')}>
                                            Voir le montant estimé
                                        </Button>
                                    ) : (
                                        <Button variant="primary" size="sm" onClick={() => setConfirmScellement(true)} loading={sealing}
                                            disabled={departFutur || nbBloquants > 0}
                                            iconLeft={<ShieldCheck size={15} />}>
                                            Confirmer le document final
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
            <div className="space-y-4">
                {errorMsg && (
                    <Alert type="error" title="Erreur dossier de sortie" message={errorMsg} />
                )}
                {hasUnsavedPreparation && <Alert type="warning" title="Modifications non enregistrées"
                    message="Sauvegardez la préparation avant la simulation."
                    action={canWrite && <Button variant="outline" size="xs" onClick={handleSauvegarder} loading={saving}>Enregistrer maintenant</Button>} />}
                {loading && (
                    <div role="status" aria-label="Chargement du dossier de sortie" className="space-y-4 animate-pulse">
                        <div className="h-24 bg-[var(--color-bg-subtle)]" />
                        <div className="h-32 bg-[var(--color-bg-subtle)]" />
                        <div className="h-12 bg-[var(--color-bg-subtle)]" />
                        <span className="sr-only">Chargement du dossier de sortie…</span>
                    </div>
                )}
                {!loading && !dossier && (
                    <div role="alert" className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-5 text-sm text-[var(--color-fg)]">
                        Impossible d’afficher le dossier. Vérifiez les données du salarié ou revenez à la liste.
                    </div>
                )}
                {!loading && dossier && (
                    <section aria-label="État et prochaine étape du dossier" className="grid gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 md:grid-cols-[minmax(0,1fr)_auto] md:items-center md:p-4">
                        <div className="min-w-0">
                            <div className="flex flex-wrap items-center gap-2">
                                <span className="text-[11px] font-bold uppercase tracking-wide text-[var(--color-brand-700)]">Étape suivante</span>
                                {dossier.idStc && <Badge variant="success" size="xs">STC scellé</Badge>}
                            </div>
                            <h2 className="mt-1 text-base font-bold text-[var(--color-fg)]">{dossier.idStc ? 'Suivre les règlements et documents' : departFutur ? 'Préparer le départ' : nbBloquants > 0 ? 'Résoudre les points bloquants' : simulationNeedsRefresh ? 'Mettre à jour l’estimation' : dossier.referenceSimulation ? 'Vérifier le décompte' : 'Préparer le calcul STC'}</h2>
                            <p className="mt-1 max-w-3xl text-sm leading-relaxed text-[var(--color-fg-muted)]">{canWrite ? prochaineEtape : 'Consultation seule : vérifiez la situation, le calcul et le suivi.'}</p>
                            {dossier.idBulletinStc ? <Button type="button" variant="outline" size="sm"
                                onClick={async () => { try { await dossierSortieService.telechargerBulletinStc(dossier.idBulletinStc); } catch (err) { setErrorMsg(getErrorMessage(err, 'Téléchargement du bulletin impossible.')); } }}
                                className="mt-3">Télécharger le bulletin PDF</Button>
                                : dossier.idDossierSortie && activeTab !== prochaineSection && <Button type="button" variant="outline" size="sm" onClick={() => setActiveTab(prochaineSection)} className="mt-3">
                                    {prochaineSection === 'checklist' ? 'Voir les points à traiter' : 'Ouvrir l’estimation'}
                                </Button>}
                        </div>
                        {(dossier.idStc || simulationResult?.decompte || dossier.montantEstimeStc != null) && (
                            <div role="status" aria-label="Récapitulatif financier" className="min-w-[220px] border-t border-[var(--color-border-subtle)] pt-3 md:border-l md:border-t-0 md:pl-5 md:pt-0">
                                <p className="text-[11px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">{dossier.idStc ? 'Net à payer' : simulationNeedsRefresh ? 'Estimation à actualiser' : 'Estimation provisoire'}</p>
                                <p className="mt-1 font-data text-2xl font-bold tabular-nums text-[var(--color-fg)]">{formatMoneyAr(dossier.idStc ? dossier.netAPayerStc : simulationResult?.decompte?.netAPayerStc ?? dossier.montantEstimeStc)}</p>
                                {dossier.idStc && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Reste à payer : <strong className="text-[var(--color-fg)]">{formatMoneyAr(dossier.soldeRestantAPayer)}</strong></p>}
                            </div>
                        )}
                    </section>
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
                <Card shadow="none" className="p-3 sm:p-4">
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
                            {dossier?.idWorkflowRh && <div className="border-l border-[var(--color-border-subtle)] pl-3 text-xs">
                                <span className="block text-[var(--color-fg-muted)]">Fin de contrat · #{dossier.idWorkflowRh}</span>
                                <span className="font-semibold text-[var(--color-fg)]">{dossier.statutWorkflowRh === 'ACTIF' ? 'Validée' : dossier.statutWorkflowRh === 'PLANIFIE' ? 'Planifiée' : dossier.statutWorkflowRh === 'BROUILLON' ? 'À valider' : dossier.statutWorkflowRh || 'À vérifier'}</span>
                            </div>}
                            <Badge
                                variant={
                                    dossier?.statutDossier === 'CLOTURE' ? 'success' :
                                    dossier?.statutDossier === 'VALIDE' ? 'primary' :
                                    dossier?.statutDossier === 'PRET_A_VALIDER' ? 'info' : 'warning'
                                }
                                size="md"
                            >
                                    {dossier?.idStc ? 'Document final confirmé' : departFutur ? 'Départ à venir' : simulationNeedsRefresh ? 'Estimation à actualiser' : dossier?.referenceSimulation ? 'Estimation calculée' : 'En préparation'}
                            </Badge>
                            {dossier?.statutWorkflowRh === 'BROUILLON' && hasPermission('EMPLOYE_VALIDATE') && (
                                <Button type="button" variant="primary" size="sm" disabled={validatingRupture || hasUnsavedPreparation}
                                    onClick={() => setConfirmRupture(true)}>Valider la fin de contrat</Button>
                            )}
                        </div>
                    </div>

                    {/* Paramètres modifiables regroupés dans la première étape */}
                    {activeTab === 'synthese' && <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 mt-4 pt-3 border-t border-[var(--color-border-subtle)]">
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Date effective de départ *
                            </label>
                            <Input
                                type="date"
                                value={dateDepart}
                                onChange={(e) => setDateDepart(e.target.value)}
                                disabled={!canWrite || Boolean(dossier?.idStc) || ruptureFigee || loading || saving}
                            />
                        </div>
                        <div>
                            <label className="block text-[11px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider mb-1">
                                Motif de rupture légal *
                            </label>
                            <SearchSelect
                                options={motifsSortie.length > 0 ? motifsSortie : [
                                    { value: 'DEMISSION', label: 'Démission du salarié' },
                                    { value: 'LICENCIEMENT', label: 'Licenciement' },
                                    { value: 'LICENCIEMENT_ECONOMIQUE', label: 'Licenciement économique' },
                                    { value: 'FIN_CONTRAT', label: 'Fin de contrat (CDD)' },
                                    { value: 'RUPTURE_CONVENTIONNELLE', label: 'Rupture conventionnelle d’un commun accord' },
                                    { value: 'RETRAITE', label: 'Départ à la retraite' },
                                    { value: 'RUPTURE_ESSAI', label: 'Rupture de période d’essai' },
                                ]}
                                value={motifRupture}
                                onChange={(val) => setMotifRupture(val)}
                                disabled={!canWrite || Boolean(dossier?.idStc) || ruptureFigee || loading || saving}
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
                    </div>}
                    {activeTab === 'synthese' && ruptureFigee && <p className="mt-2 text-xs text-[var(--color-fg-muted)]">Date et motif figés après validation RH de la fin de contrat.</p>}
                </Card>

                <div className="min-w-0 space-y-4">
                    {pageMode ? (
                        <nav aria-label="Étapes du dossier de sortie" className="min-w-0 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-2 pt-2 sm:px-3">
                            <div role="tablist" aria-label="Sections du dossier" className="flex gap-1 overflow-x-auto lg:grid lg:grid-cols-5 lg:overflow-visible">
                                {dossierTabs.map(({ id, label, icon: Icon, count }) => (
                                    <button key={id} id={`tab-${id}`} role="tab" aria-selected={activeTab === id} aria-controls={`panel-${id}`} tabIndex={activeTab === id ? 0 : -1} type="button" onClick={() => setActiveTab(id)}
                                        onKeyDown={(event) => {
                                            const index = dossierTabs.findIndex(tab => tab.id === id);
                                            const next = event.key === 'ArrowDown' || event.key === 'ArrowRight' ? (index + 1) % dossierTabs.length
                                                : event.key === 'ArrowUp' || event.key === 'ArrowLeft' ? (index - 1 + dossierTabs.length) % dossierTabs.length
                                                    : event.key === 'Home' ? 0 : event.key === 'End' ? dossierTabs.length - 1 : -1;
                                            if (next < 0) return;
                                            event.preventDefault();
                                            setActiveTab(dossierTabs[next].id);
                                            event.currentTarget.parentElement.querySelectorAll('[role="tab"]')[next]?.focus();
                                        }}
                                        className={`flex min-h-9 shrink-0 items-center justify-center gap-2 border-b-2 px-2.5 py-1.5 text-xs font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--color-brand-600)] lg:min-w-0 ${activeTab === id ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-50)] text-[var(--color-brand-800)]' : 'border-transparent text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)] hover:text-[var(--color-fg)]'}`}>
                                        <Icon size={15} className="shrink-0" aria-hidden="true" />
                                        <span className="whitespace-nowrap">{label}</span>
                                        {count > 0 && <span className="ml-auto rounded-full bg-[var(--color-warning-100)] px-1.5 text-xs text-[var(--color-warning-800)]" aria-label={`${count} points à traiter`}>{count}</span>}
                                    </button>
                                ))}
                            </div>
                        </nav>
                    ) : (
                        <div className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] pt-1">
                            <Tabs tabs={dossierTabs} activeTab={activeTab} onChange={setActiveTab}
                                ariaLabel="Étapes du dossier de sortie" className="dossier-stc-tabs" />
                        </div>
                    )}
                    <div className="min-w-0 space-y-4" aria-label={dossierTabs.find(tab => tab.id === activeTab)?.label}>

                {/* CONTENU ONGLET 1 : Données sources en lecture */}
                {activeTab === 'synthese' && (
                    <div id="panel-synthese" role="tabpanel" aria-labelledby="tab-synthese" className="space-y-4">
                        <div className="flex flex-wrap items-end justify-between gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-3 sm:px-4">
                            <div>
                                <h3 className="text-base font-bold text-[var(--color-fg)]">Données du départ</h3>
                                <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">Sources utilisées pour préparer le dernier salaire et estimer le STC.</p>
                            </div>
                            <Badge variant={dossier?.pointageGele ? 'success' : 'warning'} size="sm">{dossier?.pointageGele ? 'Pointage figé' : 'Pointage à confirmer'}</Badge>
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                            {/* Pointage */}
                            <div className="space-y-2 border border-[var(--color-border-subtle)] border-t-[3px] border-t-[var(--color-brand-500)] bg-[var(--color-surface)] p-3.5">
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
                                            travaillés ({Number(dossier?.totalHeuresPointage || 0).toLocaleString('fr-FR', { maximumFractionDigits: 2 })} h)
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
                            <div className="space-y-2 border border-[var(--color-border-subtle)] border-t-[3px] border-t-[var(--color-brand-600)] bg-[var(--color-surface)] p-3.5">
                                <div className="flex items-center justify-between text-xs font-semibold text-[var(--color-fg-muted)]">
                                    <span className="flex items-center gap-1.5 text-[var(--color-brand-700)]">
                                        <CalendarCheck size={15} /> Congés payés (FIFO)
                                    </span>
                                    <span className="text-[10px] uppercase font-bold text-[var(--color-fg-subtle)]">
                                        Solde acquis
                                    </span>
                                </div>
                                <div className="pt-1">
                                    <span className="text-xl font-bold font-data text-[var(--color-brand-700)]">
                                        {dossier?.soldeCongesRestant ?? '0'} j
                                    </span>
                                    <span className="text-xs text-[var(--color-fg-muted)] ml-1">
                                        reliquat restant
                                    </span>
                                </div>
                                <div className="text-xs text-[var(--color-fg-muted)] flex justify-between border-t border-[var(--color-border-subtle)] pt-2">
                                    <span>ICCP estimée :</span>
                                    <strong className="font-data text-[var(--color-brand-700)]">
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
                            <div className="space-y-2 border border-[var(--color-border-subtle)] border-t-[3px] border-t-[var(--color-info-500)] bg-[var(--color-surface)] p-3.5">
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

                        <div className="grid gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 sm:grid-cols-3" aria-label="Parcours du règlement STC">
                            <div className="flex items-start gap-2"><span className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-brand-600)] text-xs font-bold text-white">1</span><div><strong className="block text-xs text-[var(--color-fg)]">Préparer</strong><span className="text-xs text-[var(--color-fg-muted)]">Pointage, congés et dernier salaire</span></div></div>
                            <div className="flex items-start gap-2"><span className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-brand-600)] text-xs font-bold text-white">2</span><div><strong className="block text-xs text-[var(--color-fg)]">Confirmer le STC</strong><span className="text-xs text-[var(--color-fg-muted)]">Vérifier puis sceller l’estimation</span></div></div>
                            <div className="flex items-start gap-2"><span className="flex h-6 w-6 shrink-0 items-center justify-center bg-[var(--color-brand-700)] text-xs font-bold text-white">3</span><div><strong className="block text-xs text-[var(--color-fg)]">Enregistrer le règlement</strong><span className="text-xs text-[var(--color-fg-muted)]">Dans Suivi & documents, avec justificatif</span></div></div>
                        </div>

                        {dossier?.idBulletinMensuel && bulletinValide && !dossier?.idStc && (
                            <Card as="section" shadow="none" className="space-y-4 border-l-[3px] border-l-[var(--color-info-500)] p-4" aria-label="Versement antérieur du bulletin mensuel">
                                <div className="flex flex-wrap items-start justify-between gap-2">
                                    <div>
                                        <h4 className="flex items-center gap-2 text-sm font-bold text-[var(--color-fg)]"><Receipt size={16} className="text-[var(--color-info-700)]" aria-hidden="true" />Versement antérieur du bulletin mensuel</h4>
                                        <p className="mt-1 text-xs leading-relaxed text-[var(--color-fg-muted)]">Bulletin #{dossier.idBulletinMensuel} · Indiquez uniquement une somme déjà payée avant le STC. Cette saisie ne crée pas un nouveau versement.</p>
                                    </div>
                                    <Badge variant="info" size="sm">Déclaration d’un paiement passé</Badge>
                                </div>
                                <div className="grid gap-px border border-[var(--color-border-subtle)] bg-[var(--color-border-subtle)] sm:grid-cols-3">
                                    <div className="bg-[var(--color-bg-subtle)] px-3 py-2"><span className="block text-[11px] text-[var(--color-fg-muted)]">Net du bulletin</span><strong className="font-data text-sm text-[var(--color-fg)]">{formatMoneyAr(dossier.netBulletinMensuel)}</strong></div>
                                    <div className="bg-[var(--color-bg-subtle)] px-3 py-2"><span className="block text-[11px] text-[var(--color-fg-muted)]">Déjà versé</span><strong className="font-data text-sm text-[var(--color-fg)]">− {formatMoneyAr(montantBulletinDejaPaye)}</strong></div>
                                    <div className="bg-[var(--color-brand-50)] px-3 py-2"><span className="block text-[11px] font-semibold text-[var(--color-brand-800)]">Repris dans le STC</span><strong className="font-data text-sm text-[var(--color-brand-800)]">{formatMoneyAr(Math.max(0, Number(dossier.netBulletinMensuel || 0) - Number(montantBulletinDejaPaye || 0)))}</strong></div>
                                </div>
                                <div className="grid gap-3 sm:grid-cols-3">
                                    <MoneyInput label="Montant payé avant le STC (Ar)" min={0} max={Number(dossier.netBulletinMensuel || 0)}
                                        placeholder="Ex. 250 000"
                                        value={montantBulletinDejaPaye} onValueChange={setMontantBulletinDejaPaye}
                                        disabled={!canWrite || saving || simulating} />
                                    <Input type="date" label="Date du paiement" value={datePaiementBulletin}
                                        onChange={(event) => setDatePaiementBulletin(event.target.value)}
                                        required={Number(montantBulletinDejaPaye || 0) > 0}
                                        disabled={!canWrite || saving || simulating || Number(montantBulletinDejaPaye || 0) === 0} />
                                    <Input label="Référence du paiement" placeholder="Ex. VIR-2026-001 ou reçu de caisse" value={referencePaiementBulletin}
                                        onChange={(event) => setReferencePaiementBulletin(event.target.value)}
                                        required={Number(montantBulletinDejaPaye || 0) > 0}
                                        disabled={!canWrite || saving || simulating || Number(montantBulletinDejaPaye || 0) === 0} />
                                </div>
                                <p className="border-t border-[var(--color-border-subtle)] pt-3 text-xs text-[var(--color-fg-muted)]">Après confirmation du STC, son règlement se saisit dans <strong className="text-[var(--color-fg)]">5. Suivi & documents</strong>, avec une référence et un justificatif. La part du bulletin y est affectée au même versement.</p>
                            </Card>
                        )}

                        {dossier?.idStc && <div className="flex flex-wrap items-center justify-between gap-3 border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] p-3 text-sm">
                            <div><strong className="text-[var(--color-brand-800)]">STC confirmé</strong><p className="text-xs text-[var(--color-fg-muted)]">Enregistrez ou consultez les versements et leur part liée au bulletin dans le suivi.</p></div>
                            <Button type="button" variant="outline" size="sm" onClick={() => setActiveTab('suivi')}>Ouvrir les règlements STC</Button>
                        </div>}

                        {/* STC scellé si existant */}
                        {dossier?.idStc && !pageMode && (
                            <div className="flex flex-wrap items-center justify-between gap-4 border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] p-3 text-[var(--color-brand-900)]">
                                <div className="flex items-center gap-3">
                                    <BadgeCheck size={22} className="shrink-0 text-[var(--color-brand-700)]" />
                                    <div>
                                        <h4 className="font-bold text-sm">STC scellé & validé : N° {dossier.numeroRecu}</h4>
                                        <p className="text-xs text-[var(--color-brand-800)]">
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
                    <div id="panel-checklist" role="tabpanel" aria-labelledby="tab-checklist" className="space-y-5">
                        {(() => {
                            const checks = dossier?.checklist || [];
                            const done = checks.filter(item => item.statut === 'CONFORME');
                            const later = checks.filter(item => departFutur && (item.code === 'L-01_DATE_DEPART' || item.titre?.includes('Certificat de travail')));
                            const todo = checks.filter(item => item.statut !== 'CONFORME' && !later.includes(item));
                            const renderAction = (item) => {
                                const cibleModule = item.lienModule === 'carriere' && dossier?.idEmploye && hasPermission('EMPLOYE_READ')
                                    ? `/employes/${dossier.idEmploye}/carriere`
                                    : item.lienModule === 'conges' && hasPermission('CONGE_READ') ? '/conges'
                                    : item.lienModule === 'pointage' && hasPermission('POINTAGE_READ') && ['ROLE_ADMIN', 'ROLE_AGENT_POINTAGE'].includes(user?.role) ? '/pointages'
                                    : null;
                                return <article key={item.code} className="flex flex-col gap-3 border border-[var(--color-border-subtle)] border-l-4 border-l-[var(--color-warning-500)] bg-[var(--color-surface)] p-4 sm:flex-row sm:items-start sm:justify-between">
                                    <div className="min-w-0 space-y-1">
                                        <h4 className="flex items-center gap-2 text-sm font-semibold text-[var(--color-fg)]"><ShieldAlert size={15} className="shrink-0 text-[var(--color-warning-700)]" aria-hidden="true" />{item.titre}</h4>
                                        <p className="text-sm leading-relaxed text-[var(--color-fg-muted)]">{item.actionRecommandee || item.message}</p>
                                        {item.actionRecommandee && <p className="text-xs text-[var(--color-fg-subtle)]">{item.message?.replace('(null)', '(non renseignée)')}</p>}
                                    </div>
                                    {item.lienModule === 'documents' && <Button type="button" variant="outline" size="sm" onClick={() => setActiveTab('suivi')} className="shrink-0 self-start">Voir les documents</Button>}
                                    {cibleModule && <a href={cibleModule} target="_blank" rel="noopener noreferrer" className="shrink-0 self-start text-sm font-semibold text-[var(--color-brand-700)] hover:underline inline-flex items-center gap-1">Ouvrir la page <ExternalLink size={14} /></a>}
                                </article>;
                            };
                            return <>
                                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-3 sm:px-4">
                                    <h3 className="text-base font-bold text-[var(--color-fg)]">Ce qu’il reste à faire</h3>
                                    <p className="mt-1 text-sm text-[var(--color-fg-muted)]">{todo.length ? `${todo.length} point${todo.length > 1 ? 's' : ''} à examiner avant le bulletin final.` : 'Aucune action immédiate. Vous pouvez poursuivre la préparation.'}</p>
                                </div>
                                {todo.length > 0 && <div className="space-y-2">{todo.map(renderAction)}</div>}
                                {later.length > 0 && <p className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-3 text-sm text-[var(--color-fg-muted)]">À la date du départ : confirmez le document final. L’estimation reste disponible d’ici là.</p>}
                                <details className="border-t border-[var(--color-border-subtle)] pt-3">
                                    <summary className="cursor-pointer text-sm font-medium text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]">{done.length} vérifications déjà effectuées</summary>
                                    <ul className="mt-3 space-y-2 pl-5 text-sm text-[var(--color-fg-muted)] list-disc">{done.map(item => <li key={item.code}>{item.titre}</li>)}</ul>
                                </details>
                            </>;
                        })()}
                    </div>
                )}


                {/* CONTENU ONGLET 4 : Décompte & Scellement STC */}
                {activeTab === 'decompte' && (
                    <div id="panel-decompte" role="tabpanel" aria-labelledby="tab-decompte" className="space-y-4">
                        {simulationNeedsRefresh && <Alert type="warning" title="Estimation à mettre à jour" message="Le détail doit être recalculé après le rechargement de la page ou une modification du dossier. Cliquez sur « Mettre à jour l’estimation »." />}
                        {/* En-tête de la simulation & Référence d'intégrité */}
                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-3">
                            <div className="flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center gap-2">
                                    <Calculator size={18} className="text-[var(--color-brand-600)]" />
                                    <div>
                                        <h4 className="text-sm font-bold text-[var(--color-fg)]">
                                            Décompte du solde de tout compte
                                        </h4>
                                        <p className="text-xs text-[var(--color-fg-muted)]">
                                            {departFutur ? 'Montant provisoire : vous pourrez confirmer le document après le départ.' : 'Vérifiez le montant et les rubriques avant la confirmation finale.'}
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
                                            {simulationResult || dossier?.referenceSimulation ? 'Mettre à jour l’estimation' : (!dossier?.idDossierSortie ? 'Enregistrer et calculer l’estimation' : 'Calculer une estimation')}
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
                                            : <>Aucune estimation disponible. Cliquez sur <strong>« Calculer une estimation »</strong> pour voir le montant et son détail.</>}
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
                            const totalDed = Number(decompte.soldeAvancesDeduit || 0);
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

                                    {/* Calcul complet consultable sans alourdir la lecture initiale */}
                                    <details className="group border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                                        <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-3 text-sm font-semibold text-[var(--color-fg)] marker:hidden hover:bg-[var(--color-bg-subtle)] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--color-brand-600)]">
                                            <span className="flex items-center gap-2"><Receipt size={16} className="text-[var(--color-brand-600)]" aria-hidden="true" />Voir le calcul des droits et cotisations</span>
                                            <span aria-hidden="true" className="text-[var(--color-brand-700)] group-open:rotate-180">⌄</span>
                                        </summary>
                                        <div className="grid grid-cols-1 gap-3.5 border-t border-[var(--color-border-subtle)] p-3 md:grid-cols-3">
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
                                                    <h5 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-800)] flex items-center gap-1.5">
                                                        <Wallet size={14} /> 3. Déductions & Net
                                                    </h5>
                                                    <span className="text-[10px] font-semibold text-[var(--color-brand-700)] uppercase font-bold">À verser</span>
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
                                                </div>
                                            </div>

                                            <div className="mt-2 border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] p-3">
                                                <div className="flex items-center justify-between">
                                                    <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-brand-900)]">
                                                        Net STC à Payer
                                                    </span>
                                                    <span className="bg-[var(--color-brand-700)] px-1.5 py-0.5 text-[10px] font-bold uppercase text-white">
                                                        Net final
                                                    </span>
                                                </div>
                                                <div className="font-data mt-1 text-xl font-bold tracking-tight text-[var(--color-brand-800)]">
                                                    {formatMoneyAr(decompte.netAPayerStc || 0)}
                                                </div>
                                            </div>
                                        </div>
                                        </div>
                                    </details>

                                    {/* Rubriques détaillées disponibles à la demande */}
                                    {decompte.lignes && decompte.lignes.length > 0 && (
                                        <details className="group border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                                            <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-4 text-sm font-semibold text-[var(--color-fg)] marker:hidden hover:bg-[var(--color-bg-subtle)] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--color-brand-600)]">
                                                <span>Voir les rubriques détaillées <span className="font-normal text-[var(--color-fg-muted)]">({decompte.lignes.length})</span></span>
                                                <span aria-hidden="true" className="text-[var(--color-brand-700)] group-open:rotate-180">⌄</span>
                                            </summary>
                                            <div className="space-y-4 border-t border-[var(--color-border-subtle)] p-4">
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
                                        </details>
                                    )}

                                    {/* Zone de scellement transactionnel si non scellé */}
                                    {departFutur ? (
                                        <Alert type="info" title="Estimation uniquement"
                                            message="Le montant affiché est provisoire. Revenez à partir du jour du départ pour vérifier les données et confirmer le document final." />
                                    ) : dossier?.statutDossier !== 'VALIDE' && dossier?.statutDossier !== 'CLOTURE' ? (
                                        <div className="space-y-2 border border-[var(--color-border-subtle)] border-l-[3px] border-l-[var(--color-brand-600)] bg-[var(--color-bg-subtle)] p-3">
                                            <div className="flex items-start gap-3">
                                                <div className="shrink-0 bg-[var(--color-brand-50)] p-1.5 text-[var(--color-brand-700)]">
                                                    <ShieldCheck size={18} />
                                                </div>
                                                <div className="space-y-1">
                                                    <h5 className="text-sm font-bold text-[var(--color-fg)]">
                                                        Confirmation et scellement transactionnel du STC
                                                    </h5>
                                                    <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                                        Le scellement effectue un contrôle de fraîcheur transactionnel sur les données de calcul (pointage, congés, contrat).
                                                        Le résultat net affiché de <strong className="font-data font-bold text-[var(--color-brand-800)]">{formatMoneyAr(decompte.netAPayerStc || 0)}</strong> sera scellé dans le registre financier officiel <code>paie_stc</code>.
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
                {activeTab === 'paie-finale' && <DeparturePayrollPanel dossier={dossier}
                    onDossierChange={updated => { setDossier(updated); onSaved?.(updated); }} />}
                    </div>
                </div>
            </div>
            <ConfirmDialog isOpen={confirmRupture} onClose={() => setConfirmRupture(false)}
                onConfirm={() => { setConfirmRupture(false); handleValiderRupture(); }}
                title="Valider la fin de contrat"
                message="La rupture sera appliquée à sa date d'effet. Le dossier STC restera ouvert pour préparer et régler le solde."
                confirmLabel="Valider la rupture" variant="warning" loading={validatingRupture} />
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
