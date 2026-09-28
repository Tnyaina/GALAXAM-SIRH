import { useState, useEffect } from 'react';
import {
    FileText, Building, Landmark, CheckCircle, Edit2, XCircle,
    AlertTriangle, ShieldAlert, Clock, History, X
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import Alert from '@/components/ui/feedback/Alert';
import { Textarea } from '@/components/ui/forms/Input';
import { useAuth } from '@/context/AuthContext';
import HasPermission from '@/features/auth/components/HasPermission';
import contratService from '@/features/employees/api/contratService';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { getWorkflowLibellesUI, calculerDateFinEssaiContextuelle } from '@/features/employees/utils/carriereWorkflowUtils';

function InfoRow({ label, value, highlight = false, badge = null }) {
    return (
        <div className="py-1.5 flex justify-between items-center text-xs border-b border-[var(--color-border-subtle)] last:border-0">
            <span className="text-[var(--color-fg-subtle)] font-medium">{label}</span>
            <div className="flex items-center gap-2">
                {badge && <span className="flex-shrink-0">{badge}</span>}
                <span className={`font-semibold ${highlight ? 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-mono text-sm' : 'text-[var(--color-fg)]'}`}>
                    {value || '-'}
                </span>
            </div>
        </div>
    );
}

export default function WorkflowDraftSummaryModal({
    isOpen,
    onClose,
    workflowCpl,
    initialCancelMode = false,
    canWrite = true,
    canValidate = false,
    onEdit,
    onActionSuccess,
}) {
    // Contexte et droits d'accès
    const { hasPermission } = useAuth();
    const effectiveCanValidate = canValidate || hasPermission('EMPLOYE_VALIDATE');
    const effectiveCanWrite = canWrite || hasPermission('EMPLOYE_WRITE');

    // État local : brouillon, mode annulation et soumission.
    const [activeWf, setActiveWf] = useState(workflowCpl);
    const [isCancelling, setIsCancelling] = useState(Boolean(initialCancelMode));
    const [commentaireValidation, setCommentaireValidation] = useState('');
    const [motifAnnulation, setMotifAnnulation] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    // Effets : synchronisent le brouillon fourni et le mode d'ouverture.
    useEffect(() => {
        setActiveWf(workflowCpl);
        setIsCancelling(Boolean(initialCancelMode));
        setErrorMsg('');
        setCommentaireValidation('');
        setMotifAnnulation('');
    }, [workflowCpl, initialCancelMode]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    // Données dérivées
    const currentWf = activeWf || workflowCpl;

    const handleValiderConfirm = async () => {
        if (!currentWf?.idWorkflow) return;
        setSubmitting(true);
        setErrorMsg('');
        try {
            await contratService.validerWorkflow(currentWf.idWorkflow, commentaireValidation.trim() || null);
            let freshWf = null;
            try {
                freshWf = await contratService.getWorkflow(currentWf.idWorkflow);
            } catch {
                freshWf = { ...currentWf, codeStatutWorkflow: 'ACTIF', libelleStatutWorkflow: 'ACTIF', isActif: true, isBrouillon: false };
            }
            if (freshWf) setActiveWf(freshWf);
            if (onActionSuccess) {
                onActionSuccess('Workflow RH validé avec succès !', freshWf, { silent: true });
            }
        } catch (err) {
            console.error('Erreur validation workflow:', err);
            const apiError = typeof err.response?.data === 'string' ? err.response.data : err.response?.data?.message;
            setErrorMsg(apiError || err.message || 'Erreur lors de la validation.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleAnnulerConfirm = async () => {
        if (!currentWf?.idWorkflow) return;
        if (!motifAnnulation?.trim()) {
            return setErrorMsg("Le motif d'annulation est obligatoire.");
        }
        setSubmitting(true);
        setErrorMsg('');
        try {
            await contratService.annulerWorkflow(currentWf.idWorkflow, motifAnnulation.trim());
            let freshWf = null;
            try {
                freshWf = await contratService.getWorkflow(currentWf.idWorkflow);
            } catch {
                freshWf = { ...currentWf, codeStatutWorkflow: 'ANNULE', libelleStatutWorkflow: 'ANNULÉ' };
            }
            if (freshWf) setActiveWf(freshWf);
            setIsCancelling(false);
            if (onActionSuccess) {
                onActionSuccess('Workflow RH annulé avec succès.', freshWf, { silent: true });
            }
        } catch (err) {
            console.error('Erreur annulation workflow:', err);
            const apiError = typeof err.response?.data === 'string' ? err.response.data : err.response?.data?.message;
            setErrorMsg(apiError || err.message || 'Erreur lors de l\'annulation.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleAnnulerPlanificationConfirm = async () => {
        if (!currentWf?.idWorkflow) return;
        if (!motifAnnulation?.trim()) {
            return setErrorMsg("Le motif d'annulation est obligatoire.");
        }
        setSubmitting(true);
        setErrorMsg('');
        try {
            await contratService.annulerWorkflowPlanifie(currentWf.idWorkflow, motifAnnulation.trim());
            let freshWf = null;
            try {
                freshWf = await contratService.getWorkflow(currentWf.idWorkflow);
            } catch {
                freshWf = { ...currentWf, codeStatutWorkflow: 'ANNULE', libelleStatutWorkflow: 'ANNULÉ' };
            }
            if (freshWf) setActiveWf(freshWf);
            setIsCancelling(false);
            if (onActionSuccess) {
                onActionSuccess('Planification RH annulée avec succès.', freshWf, { silent: true });
            }
        } catch (err) {
            console.error('Erreur annulation planification workflow:', err);
            setErrorMsg(err.response?.data?.message || err.message || 'Erreur lors de l\'annulation.');
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen || !currentWf) return null;

    // Backend-driven : les champs booléens d'état proviennent de WorkflowRhCPL, avec fallback défensif
    const isBrouillon = !!(currentWf.isBrouillon ?? currentWf.brouillon ?? (currentWf.codeStatutWorkflow === 'BROUILLON'));
    const isPlanifie = !!(currentWf.isPlanifie ?? currentWf.planifie ?? (currentWf.codeStatutWorkflow === 'PLANIFIE'));
    const isActif = !!(currentWf.isActif ?? currentWf.actif ?? (currentWf.codeStatutWorkflow === 'ACTIF' || currentWf.codeStatutWorkflow === 'VALIDE'));
    const isHistorise = !!(currentWf.isHistorise ?? currentWf.historise ?? (currentWf.codeStatutWorkflow === 'HISTORISE' || currentWf.codeStatutWorkflow === 'CLOTURE'));
    const isAnnule = !!(currentWf.isAnnule ?? currentWf.annule ?? (currentWf.codeStatutWorkflow === 'ANNULE'));

    const {
        isEmbauche, isConfirmation: isConfirmationEssai,
        isRenouvellement: isRenouvellementEssai,
        title: eventTitle, description: eventDescription
    } = getWorkflowLibellesUI(currentWf);

    const getDateFinEssaiContextuelle = () => calculerDateFinEssaiContextuelle(currentWf);
    const isPlanifieFutur = isPlanifie;

    return (
        <div 
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto"
        >
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-4xl w-full my-8 overflow-hidden flex flex-col max-h-[90vh]">

                {/* Header */}
                <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] rounded-none flex items-center justify-center font-bold">
                            <FileText size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h2 className="font-bold text-base text-[var(--color-fg)]">
                                    {isBrouillon ? eventTitle : 'Détail de l\'Événement RH'}
                                </h2>
                                <StatusBadge
                                    status={currentWf.codeStatutWorkflow}
                                    label={currentWf.libelleStatutWorkflow}
                                    size="sm"
                                />
                            </div>
                            <p className="text-xs text-[var(--color-fg-subtle)]">
                                Contrat N° <span className="font-mono font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">{currentWf.numeroContratCible || currentWf.numeroContratSource || 'Nouveau'}</span> — {currentWf.nomPrenomEmploye} ({currentWf.matriculeEmploye})
                            </p>
                            <p className="text-[11px] text-[var(--color-fg-subtle)] flex items-center gap-3 mt-0.5">
                                <span>
                                    Type d'événement : <span className="font-semibold text-[var(--color-fg-muted)]">{currentWf.libelleTypeEvenement || currentWf.codeTypeEvenement || '-'}</span>
                                </span>
                                <span>
                                    Date de prise d'effet : <span className="font-mono font-semibold text-[var(--color-fg-muted)]">{fmtDate(currentWf.dateEffet)}</span>
                                </span>
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="text-[var(--color-fg-subtle)] hover:text-[var(--color-fg)] p-1 rounded-none">
                        <X size={18} />
                    </button>
                </div>

                {/* Body Content */}
                <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-4">
                    {errorMsg && (
                        <Alert type="error" message={errorMsg} />
                    )}

                    {/* Alerte Information */}
                    {isBrouillon ? (
                        <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 p-4 rounded-none flex items-start gap-3">
                            <Clock size={18} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                    {eventTitle}
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    {eventDescription}
                                </p>
                            </div>
                        </div>
                    ) : isPlanifie ? (
                        <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 p-4 rounded-none flex items-start gap-3">
                            <Clock size={18} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)]">
                                    Événement RH Planifié (Prise d'effet le {fmtDate(currentWf.dateEffet)})
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    Ce mouvement RH a été validé et prendra automatiquement effet le {fmtDate(currentWf.dateEffet)}.
                                </p>
                            </div>
                        </div>
                    ) : isActif ? (
                        <div className="bg-success-500/10 border border-success-500/20 p-4 rounded-none flex items-start gap-3">
                            <CheckCircle size={18} className="text-success-600 flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-success-700 dark:text-success-400">
                                    {isEmbauche ? 'Embauche Initiale Validée et Appliquée' : isConfirmationEssai ? 'Confirmation d\'Essai Validée avec Succès' : isRenouvellementEssai ? 'Renouvellement d\'Essai Validé et Appliqué' : 'Événement RH Validé et Actif'}
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    {isEmbauche ? 'Ce contrat d\'embauche a été validé. Le contrat et l\'affectation sont actifs.' : isConfirmationEssai ? 'La période d\'essai de cet employé a été confirmée et validée.' : isRenouvellementEssai ? 'Le renouvellement de l\'essai a été validé et la date de fin a été mise à jour en BDD.' : 'Ce mouvement RH est actuellement en vigueur dans le dossier de l\'employé.'}
                                </p>
                            </div>
                        </div>
                    ) : isHistorise ? (
                        <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 rounded-none flex items-start gap-3">
                            <History size={18} className="text-[var(--color-fg-muted)] flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-[var(--color-fg)]">
                                    Mouvement RH Historisé (Passé)
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    Ce mouvement RH a été appliqué avec succès le {fmtDate(currentWf.dateEffet)}. Il est archivé dans l'historique suite à la prise d'effet d'un mouvement ultérieur.
                                </p>
                            </div>
                        </div>
                    ) : isAnnule ? (
                        <div className="bg-danger-500/10 border border-danger-500/30 p-4 rounded-none flex items-start gap-3">
                            <XCircle size={18} className="text-danger-600 flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-danger-700 dark:text-danger-400">
                                    Mouvement RH Annulé / Rejeté
                                </p>
                                <p className="text-[var(--color-fg-muted)]">
                                    {currentWf.commentaire || currentWf.motif ? (
                                        <>
                                            <span className="font-semibold text-danger-600 dark:text-danger-400">Motif :</span> {currentWf.commentaire || currentWf.motif}
                                        </>
                                    ) : (
                                        "Ce mouvement RH a été annulé et n'a produit aucun effet contractuel."
                                    )}
                                </p>
                                {currentWf.updatedAt && (
                                    <p className="text-[11px] text-[var(--color-fg-subtle)] font-mono">
                                        Annulé le {fmtDate(currentWf.updatedAt)}
                                    </p>
                                )}
                            </div>
                        </div>
                    ) : null}

                    {/* Bloc Synthèse Période d'Essai si l'événement concerne l'essai */}
                    {(isConfirmationEssai || isRenouvellementEssai || (isEmbauche && (currentWf.dureeEssaiMois || currentWf.dateFinEssai))) && (
                        <div className="bg-warning-500/10 border border-warning-500/30 p-4 rounded-none space-y-2">
                            <div className="flex justify-between items-center text-xs font-bold text-warning-800 dark:text-warning-300">
                                <span className="flex items-center gap-1.5">
                                    <Clock size={16} /> Synthèse Période d'Essai — {currentWf.libelleTypeEvenement || 'Mouvement'}
                                </span>
                                <Badge variant={isConfirmationEssai ? 'success' : isRenouvellementEssai ? 'warning' : 'primary'} size="sm">
                                    {isConfirmationEssai ? 'ESSAI CONFIRMÉ' : isRenouvellementEssai ? 'ESSAI RENOUVELÉ' : 'PÉRIODE D\'ESSAI INITIALE'}
                                </Badge>
                            </div>
                            <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 text-xs pt-1 border-t border-warning-500/20">
                                <div>
                                    <span className="text-[var(--color-fg-subtle)] text-[10px] block font-semibold uppercase">Durée d'Essai :</span>
                                    <span className="font-bold text-[var(--color-fg)]">{currentWf.dureeEssaiMois || '-'} Mois</span>
                                </div>
                                <div>
                                    <span className="text-[var(--color-fg-subtle)] text-[10px] block font-semibold uppercase">
                                        {isEmbauche ? 'Fin d\'Essai Initiale :' : isRenouvellementEssai ? 'Nouvelle Fin d\'Essai :' : 'Date Fin d\'Essai :'}
                                    </span>
                                    <span className="font-bold text-[var(--color-fg)]">
                                        {fmtDate(getDateFinEssaiContextuelle())}
                                    </span>
                                </div>
                                <div>
                                    <span className="text-[var(--color-fg-subtle)] text-[10px] block font-semibold uppercase">Date de Prise d'Effet :</span>
                                    <span className="font-bold text-[var(--color-fg)]">{fmtDate(currentWf.dateEffet)}</span>
                                </div>
                            </div>
                        </div>
                    )}

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">

                        {/* Carte 1 : Contrat de Travail */}
                        <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none space-y-3">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                <Building size={14} /> Contrat de Travail
                            </h3>
                            <div className="space-y-1">
                                <InfoRow label="N° de Contrat" value={currentWf.numeroContratCible || currentWf.numeroContratSource} highlight />
                                <InfoRow label="Type de Contrat" value={currentWf.libelleTypeContrat} />
                                <InfoRow label="Type d'Intervenant" value={currentWf.libelleTypeIntervenant} />
                                <InfoRow label="Filiale Employeuse" value={currentWf.libelleFiliale} />
                                <InfoRow label="Date de Début" value={fmtDate(currentWf.dateDebutContrat)} />
                                {currentWf.dateFinContrat && (
                                    <InfoRow label="Date de Fin" value={fmtDate(currentWf.dateFinContrat)} />
                                )}
                                <InfoRow label="Date de Signature" value={fmtDate(currentWf.dateSignature)} />
                                <InfoRow
                                    label="Comptabilisé Effectif"
                                    value={currentWf.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Stage/Consultant)'}
                                    badge={<Badge variant={currentWf.comptabiliseEffectif ? 'success' : 'default'} size="sm">{currentWf.comptabiliseEffectif ? 'Effectif' : 'Hors-effectif'}</Badge>}
                                />
                            </div>
                        </div>

                        {/* Carte 2 : Affectation & Rémunération */}
                        <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none space-y-3">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                <Landmark size={14} /> Affectation &amp; Rémunération
                            </h3>
                            <div className="space-y-1">
                                <InfoRow label="Site / Implantation" value={currentWf.libelleSite} />
                                <InfoRow label="Département" value={currentWf.libelleDepartement} />
                                <InfoRow label="Service" value={currentWf.libelleService} />
                                <InfoRow label="Fonction / Poste" value={currentWf.libelleFonction} />
                                <InfoRow label="Catégorie Pro" value={currentWf.libelleCategoriePro} />
                                <InfoRow label="Supérieur Hiérarchique" value={currentWf.nomPrenomResponsable} />
                                <InfoRow label="Mode Calcul Paie" value={currentWf.libelleModeCalculPaie} />
                                <HasPermission authority="PAIE_READ">
                                    <InfoRow label="Salaire de Base" value={fmtMoney(currentWf.salaireBase)} highlight />
                                </HasPermission>
                                <InfoRow label="Date de Prise d'Effet" value={fmtDate(currentWf.dateEffet)} />
                            </div>
                        </div>
                    </div>

                    {/* Bloc 3 : Motif & Justification */}
                    {(currentWf.motif || currentWf.commentaire) && (
                        <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none space-y-2 text-xs">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] border-b border-[var(--color-border-subtle)] pb-2">
                                {isEmbauche ? 'Motif & Justification de l\'embauche' : 'Motif & Justification de la décision'}
                            </h3>
                            {currentWf.motif && (
                                <p className="text-xs text-[var(--color-fg)]">{currentWf.motif}</p>
                            )}
                            {currentWf.commentaire && (
                                <p className="text-xs text-[var(--color-fg-subtle)] border-t border-[var(--color-border-subtle)] pt-2">
                                    <span className="font-semibold">Remarque :</span> {currentWf.commentaire}
                                </p>
                            )}
                        </div>
                    )}

                    {/* Commentaire de validation — visible en continu, pas d'étape intermédiaire pour valider */}
                    {isBrouillon && !isCancelling && (
                        <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none space-y-1">
                            <Textarea
                                id="commentaireValidation"
                                name="commentaireValidation"
                                label="Commentaire de validation (optionnel)"
                                rows={2}
                                value={commentaireValidation}
                                onChange={(e) => setCommentaireValidation(e.target.value)}
                                placeholder="Ex: Dossier vérifié et validé..."
                            />
                        </div>
                    )}

                    {/* Zone de confirmation pour l'annulation — motif obligatoire, seule action qui justifie une étape dédiée */}
                    {(isBrouillon || isPlanifieFutur) && isCancelling && (
                        <div className="p-4 border rounded-none space-y-3 bg-danger-500/10 border-danger-500/30">
                            <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-2 text-danger-700 dark:text-danger-400">
                                <AlertTriangle size={16} />
                                {isPlanifieFutur ? 'Confirmation de l\'annulation de la planification RH' : 'Confirmation de l\'annulation du brouillon'}
                            </h4>
                            <Textarea
                                id="motifAnnulation"
                                name="motifAnnulation"
                                label="Motif de l'annulation (obligatoire)"
                                rows={2}
                                value={motifAnnulation}
                                onChange={(e) => setMotifAnnulation(e.target.value)}
                                placeholder="Ex: Projet décalé ou annulé, rétractation RH..."
                                autoFocus
                            />
                            <div className="flex justify-end gap-2 pt-1">
                                <Button type="button" onClick={() => { setIsCancelling(false); setMotifAnnulation(''); setErrorMsg(''); }} variant="ghost" size="sm">
                                    Retour
                                </Button>
                                <Button
                                    type="button"
                                    onClick={isPlanifieFutur ? handleAnnulerPlanificationConfirm : handleAnnulerConfirm}
                                    variant="danger"
                                    size="sm"
                                    loading={submitting}
                                >
                                    Confirmer l'Annulation
                                </Button>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer Actions */}
                {isBrouillon ? (
                    !isCancelling && (
                        <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between gap-4">
                            {(effectiveCanWrite || effectiveCanValidate) ? (
                                <Button
                                    type="button"
                                    onClick={() => setIsCancelling(true)}
                                    variant="danger"
                                    size="md"
                                    iconLeft={<XCircle size={16} />}
                                    className="shrink-0"
                                >
                                    Annuler ce Brouillon
                                </Button>
                            ) : (
                                <div />
                            )}

                            <div className="flex items-center gap-3 shrink-0">
                                {onEdit && effectiveCanWrite && (
                                    <Button
                                        type="button"
                                        onClick={() => {
                                            onClose();
                                            onEdit(currentWf);
                                        }}
                                        variant="outline"
                                        size="md"
                                        iconLeft={<Edit2 size={16} />}
                                    >
                                        Corriger les données
                                    </Button>
                                )}

                                {effectiveCanValidate ? (
                                    <Button
                                        type="button"
                                        onClick={handleValiderConfirm}
                                        variant="success"
                                        size="md"
                                        iconLeft={<CheckCircle size={16} />}
                                        loading={submitting}
                                    >
                                        {isConfirmationEssai ? 'Valider la Confirmation d\'Essai' : isRenouvellementEssai ? 'Valider le Renouvellement d\'Essai' : isEmbauche ? 'Valider Définitivement (Activer)' : 'Valider le Mouvement RH'}
                                    </Button>
                                ) : (
                                    <div className="flex items-center gap-2 px-3 py-1.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)] rounded-none">
                                        <ShieldAlert size={14} className="text-warning-500 shrink-0" />
                                        <span>Validation réservée aux responsables habilités</span>
                                    </div>
                                )}
                            </div>
                        </div>
                    )
                ) : (
                    !isCancelling && (
                        <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex justify-between items-center">
                            {isPlanifieFutur && (effectiveCanWrite || effectiveCanValidate) ? (
                                <Button
                                    type="button"
                                    onClick={() => setIsCancelling(true)}
                                    variant="danger"
                                    size="md"
                                    iconLeft={<XCircle size={16} />}
                                >
                                    Annuler la Planification RH
                                </Button>
                            ) : (
                                <div />
                            )}
                            <Button type="button" onClick={onClose} variant="outline" size="md">
                                Fermer
                            </Button>
                        </div>
                    )
                )}
            </div>
        </div>
    );
}
