import { useState, useEffect } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import {
    FileText, Building, Landmark, CheckCircle, Edit2, XCircle,
    AlertTriangle, ShieldAlert, Clock, History
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import FormError from '@/components/ui/feedback/FormError';
import Modal from '@/components/ui/overlays/Modal';
import { Textarea } from '@/components/ui/forms/Input';
import { useAuth } from '@/context/AuthContext';
import HasPermission from '@/features/auth/components/HasPermission';
import contratService from '@/features/employees/api/contratService';
import { fmtDate } from '@/utils/dateUtils';
import { fmtMoney } from '@/utils/moneyUtils';
import { getWorkflowLibellesUI, calculerDateFinEssaiContextuelle } from '@/features/employees/utils/carriereWorkflowUtils';

function InfoRow({ label, value, highlight = false, badge = null }) {
    const rawText = typeof value === 'string' ? value.trim() : value;
    const hasValue = rawText !== null && rawText !== undefined && rawText !== '' && rawText !== '-' && rawText !== '—';

    return (
        <div className="grid grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] items-center gap-2 py-1.5 text-xs border-b border-[var(--color-border-subtle)] last:border-0">
            <span className="text-[var(--color-fg-muted)] font-medium">{label}</span>
            <div className="flex min-w-0 items-center justify-end gap-2 text-right">
                {badge && <span className="shrink-0">{badge}</span>}
                <span className={`min-w-0 break-words ${highlight ? 'text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-mono text-sm' : hasValue ? 'text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)] font-normal'}`}>
                    {hasValue ? value : 'Non renseigné'}
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

    useEffect(() => {
        setActiveWf(workflowCpl);
        setIsCancelling(Boolean(initialCancelMode));
        setErrorMsg('');
        setCommentaireValidation('');
        setMotifAnnulation('');
    }, [workflowCpl, initialCancelMode]);

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
            setErrorMsg(getErrorMessage(err, 'Erreur lors de l\'annulation.'));
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen || !currentWf) return null;

    const isBrouillon = !!(currentWf.isBrouillon ?? currentWf.brouillon ?? (currentWf.codeStatutWorkflow === 'BROUILLON'));
    const isPlanifie = !!(currentWf.isPlanifie ?? currentWf.planifie ?? (currentWf.codeStatutWorkflow === 'PLANIFIE'));
    const isActif = !!(currentWf.isActif ?? currentWf.actif ?? (currentWf.codeStatutWorkflow === 'ACTIF' || currentWf.codeStatutWorkflow === 'VALIDE'));
    const isHistorise = !!(currentWf.isHistorise ?? currentWf.historise ?? (currentWf.codeStatutWorkflow === 'HISTORISE' || currentWf.codeStatutWorkflow === 'CLOTURE'));
    const isAnnule = !!(currentWf.isAnnule ?? currentWf.annule ?? (currentWf.codeStatutWorkflow === 'ANNULE'));

    const {
        isEmbauche, isConfirmation: isConfirmationEssai,
        isRenouvellement: isRenouvellementEssai,
        title: eventTitle, description: eventDescription,
        actionBtnLabel
    } = getWorkflowLibellesUI(currentWf);

    const getDateFinEssaiContextuelle = () => calculerDateFinEssaiContextuelle(currentWf);
    const isPlanifieFutur = isPlanifie;

    const modalFooter = isCancelling ? (
        <div className="flex items-center justify-between w-full gap-2">
            <Button
                type="button"
                onClick={() => { setIsCancelling(false); setMotifAnnulation(''); setErrorMsg(''); }}
                variant="ghost"
                size="sm"
            >
                Retour
            </Button>
            <Button
                type="button"
                onClick={isPlanifieFutur ? handleAnnulerPlanificationConfirm : handleAnnulerConfirm}
                variant="danger"
                size="sm"
                loading={submitting}
            >
                Confirmer le rejet
            </Button>
        </div>
    ) : isBrouillon ? (
            <div className="flex w-full flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
            {(effectiveCanWrite || effectiveCanValidate) ? (
                <Button
                    type="button"
                    onClick={() => setIsCancelling(true)}
                    variant="danger"
                    size="sm"
                    iconLeft={<XCircle size={15} />}
                >
                    Rejeter le brouillon
                </Button>
            ) : null}

            <div className="flex flex-wrap items-center justify-end gap-2">
                {onEdit && effectiveCanWrite && (
                    <Button
                        type="button"
                        onClick={() => {
                            onClose();
                            onEdit(currentWf);
                        }}
                        variant="outline"
                        size="sm"
                        iconLeft={<Edit2 size={15} />}
                    >
                        Corriger
                    </Button>
                )}

                {effectiveCanValidate ? (
                    <Button
                        type="button"
                        onClick={handleValiderConfirm}
                        variant="primary"
                        size="sm"
                        iconLeft={<CheckCircle size={15} />}
                        loading={submitting}
                    >
                        {actionBtnLabel || 'Valider'}
                    </Button>
                ) : (
                    <div className="flex items-center gap-1.5 px-2.5 py-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)]">
                        <ShieldAlert size={14} className="text-[var(--color-warning-500)] shrink-0" />
                        <span>Validation réservée aux responsables habilités</span>
                    </div>
                )}
            </div>
        </div>
    ) : (
        <div className="flex items-center justify-between w-full gap-2">
            {isPlanifieFutur && (effectiveCanWrite || effectiveCanValidate) ? (
                <Button
                    type="button"
                    onClick={() => setIsCancelling(true)}
                    variant="danger"
                    size="sm"
                    iconLeft={<XCircle size={15} />}
                >
                    Annuler la planification
                </Button>
            ) : <div />}
            <Button type="button" onClick={onClose} variant="ghost" size="sm">
                Fermer
            </Button>
        </div>
    );

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={isBrouillon ? eventTitle : 'Détail du mouvement RH'}
            icon={<FileText size={18} />}
            size="2xl"
            footer={modalFooter}
        >
            <div className="space-y-4">
                {errorMsg && <FormError message={errorMsg} />}

                {/* En-tête contextuel */}
                <div className="flex items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-3">
                    <div className="text-xs text-[var(--color-fg-muted)] space-y-0.5">
                        <p className="font-semibold text-[var(--color-fg)]">
                            Contrat N° <span className="font-mono text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">{currentWf.numeroContratCible || currentWf.numeroContratSource || 'Nouveau'}</span> · {currentWf.nomPrenomEmploye} {currentWf.matriculeEmploye ? `(${currentWf.matriculeEmploye})` : ''}
                        </p>
                        <p className="flex items-center gap-3">
                            <span>Événement : <strong className="text-[var(--color-fg)]">{currentWf.libelleTypeEvenement || currentWf.codeTypeEvenement || 'Mouvement'}</strong></span>
                            <span>Prise d'effet : <strong className="font-mono text-[var(--color-fg)]">{fmtDate(currentWf.dateEffet)}</strong></span>
                        </p>
                    </div>
                    <StatusBadge
                        status={currentWf.codeStatutWorkflow}
                        label={currentWf.libelleStatutWorkflow}
                        size="sm"
                    />
                </div>

                <div className="grid grid-cols-1 gap-2 sm:grid-cols-3" aria-label="Repères de validation">
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2">
                        <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Contrat</p>
                        <p className="mt-0.5 text-xs font-semibold text-[var(--color-fg)]">{currentWf.libelleTypeContrat || 'Type non renseigné'}</p>
                    </div>
                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2">
                        <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Poste</p>
                        <p className="mt-0.5 text-xs font-semibold text-[var(--color-fg)]">{currentWf.libelleFonction || 'Poste non renseigné'}</p>
                    </div>
                    <HasPermission authority="PAIE_READ">
                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2">
                            <p className="text-[10px] font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Salaire de base</p>
                            <p className="mt-0.5 font-mono text-sm font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">{fmtMoney(currentWf.salaireBase)}</p>
                        </div>
                    </HasPermission>
                </div>

                {/* Bannière d'état concise */}
                {isBrouillon ? (
                    <div className="p-3 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 text-xs flex items-start gap-2.5">
                        <Clock size={16} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shrink-0 mt-0.5" />
                        <p className="text-[var(--color-fg-muted)] leading-relaxed">
                            {eventDescription}
                        </p>
                    </div>
                ) : isPlanifie ? (
                    <div className="p-3 bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 text-xs flex items-start gap-2.5">
                        <Clock size={16} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] shrink-0 mt-0.5" />
                        <p className="text-[var(--color-fg-muted)]">
                            Mouvement validé avec prise d'effet automatique le <strong className="font-mono text-[var(--color-fg)]">{fmtDate(currentWf.dateEffet)}</strong>.
                        </p>
                    </div>
                ) : isActif ? (
                    <div className="p-3 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/20 text-xs flex items-start gap-2.5">
                        <CheckCircle size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                        <p className="text-[var(--color-fg-muted)]">
                            {isEmbauche ? 'Contrat et affectation validés et actifs.' : isConfirmationEssai ? 'Période d\'essai levée.' : isRenouvellementEssai ? 'Échéance d\'essai mise à jour.' : 'Mouvement RH en vigueur.'}
                        </p>
                    </div>
                ) : isHistorise ? (
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs flex items-start gap-2.5">
                        <History size={16} className="text-[var(--color-fg-muted)] shrink-0 mt-0.5" />
                        <p className="text-[var(--color-fg-muted)]">
                            Mouvement appliqué le {fmtDate(currentWf.dateEffet)}, désormais clôturé.
                        </p>
                    </div>
                ) : isAnnule ? (
                    <div className="p-3 bg-[var(--color-danger-500)]/10 border border-[var(--color-danger-500)]/30 text-xs flex items-start gap-2.5">
                        <XCircle size={16} className="text-[var(--color-danger-600)] shrink-0 mt-0.5" />
                        <div className="space-y-1">
                            <p className="text-[var(--color-fg-muted)]">
                                {currentWf.commentaire || currentWf.motif ? (
                                    <>Motif : <strong className="text-[var(--color-fg)]">{currentWf.commentaire || currentWf.motif}</strong></>
                                ) : (
                                    'Ce mouvement RH a été annulé sans effet contractuel.'
                                )}
                            </p>
                            {currentWf.updatedAt && (
                                <p className="text-[11px] font-mono text-[var(--color-fg-muted)]">
                                    Annulé le {fmtDate(currentWf.updatedAt)}
                                </p>
                            )}
                        </div>
                    </div>
                ) : null}

                {/* Synthèse Période d'Essai si applicable */}
                {(isConfirmationEssai || isRenouvellementEssai || (isEmbauche && (currentWf.dureeEssaiMois || currentWf.dateFinEssai))) && (
                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs space-y-2">
                        <div className="flex justify-between items-center font-bold text-[var(--color-warning-800)] dark:text-[var(--color-warning-300)]">
                            <span className="flex items-center gap-1.5">
                                <Clock size={15} /> Période d'essai
                            </span>
                            <Badge variant={isConfirmationEssai ? 'success' : isRenouvellementEssai ? 'warning' : 'primary'} size="sm">
                                {isConfirmationEssai ? 'ESSAI CONFIRMÉ' : isRenouvellementEssai ? 'ESSAI RENOUVELÉ' : 'PÉRIODE D\'ESSAI'}
                            </Badge>
                        </div>
                        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 pt-2 border-t border-[var(--color-warning-500)]/20">
                            <div>
                                <span className="text-[10px] uppercase text-[var(--color-fg-muted)] block">Durée :</span>
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {currentWf.dureeEssaiMois ? `${currentWf.dureeEssaiMois} mois` : 'Non renseignée'}
                                </span>
                            </div>
                            <div>
                                <span className="text-[10px] uppercase text-[var(--color-fg-muted)] block">
                                    {isEmbauche ? 'Fin d\'essai :' : isRenouvellementEssai ? 'Nouvelle fin :' : 'Échéance :'}
                                </span>
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {fmtDate(getDateFinEssaiContextuelle())}
                                </span>
                            </div>
                            <div>
                                <span className="text-[10px] uppercase text-[var(--color-fg-muted)] block">Prise d'effet :</span>
                                <span className="font-semibold text-[var(--color-fg)]">{fmtDate(currentWf.dateEffet)}</span>
                            </div>
                        </div>
                    </div>
                )}

                {/* Données organisées par décision de validation */}
                <div className="grid grid-cols-1 gap-3.5 md:grid-cols-2">
                    {/* Contrat et dates */}
                    <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-2.5">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-b border-[var(--color-border-subtle)] pb-1.5 flex items-center gap-1.5">
                            <Building size={14} /> Contrat de travail
                        </h3>
                        <div className="space-y-1">
                            <InfoRow label="Numéro" value={currentWf.numeroContratCible || currentWf.numeroContratSource} highlight />
                            <InfoRow label="Type de contrat" value={currentWf.libelleTypeContrat} />
                            <InfoRow label="Intervenant" value={currentWf.libelleTypeIntervenant} />
                            <InfoRow label="Filiale" value={currentWf.libelleFiliale} />
                            <InfoRow label="Début du contrat" value={fmtDate(currentWf.dateDebutContrat)} />
                            {currentWf.dateFinContrat && (
                                <InfoRow label="Date de fin" value={fmtDate(currentWf.dateFinContrat)} />
                            )}
                            <InfoRow label="Signature" value={fmtDate(currentWf.dateSignature)} />
                        </div>
                    </div>

                    {/* Poste et rattachement */}
                    <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-2.5">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-b border-[var(--color-border-subtle)] pb-1.5 flex items-center gap-1.5">
                            <Landmark size={14} /> Poste et rattachement
                        </h3>
                        <div className="space-y-1">
                            <InfoRow label="Site" value={currentWf.libelleSite} />
                            <InfoRow label="Département" value={currentWf.libelleDepartement} />
                            <InfoRow label="Service" value={currentWf.libelleService} />
                            <InfoRow label="Catégorie pro" value={currentWf.libelleCategoriePro} />
                            <InfoRow label="Supérieur hiérarchique" value={currentWf.nomPrenomResponsable} />
                            <InfoRow label="Effectif légal" value={currentWf.comptabiliseEffectif ? 'Oui' : 'Non'} badge={<Badge variant={currentWf.comptabiliseEffectif ? 'success' : 'default'} size="sm">{currentWf.comptabiliseEffectif ? 'Effectif' : 'Hors effectif'}</Badge>} />
                        </div>
                    </div>

                    {/* Rémunération */}
                    <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-2.5 md:col-span-2">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] border-b border-[var(--color-border-subtle)] pb-1.5 flex items-center gap-1.5">
                            <Landmark size={14} /> Rémunération
                        </h3>
                        <div className="grid grid-cols-1 gap-x-6 sm:grid-cols-2">
                            <InfoRow label="Mode de calcul" value={currentWf.libelleModeCalculPaie} />
                            <HasPermission authority="PAIE_READ">
                                <InfoRow label="Salaire de base" value={fmtMoney(currentWf.salaireBase)} highlight />
                            </HasPermission>
                            <InfoRow label="Date de prise d'effet" value={fmtDate(currentWf.dateEffet)} />
                        </div>
                    </div>
                </div>

                {/* Justification / Remarques */}
                {(currentWf.motif || currentWf.commentaire) && (
                    <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1.5 text-xs">
                        <h4 className="font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider text-[11px]">
                            {isEmbauche ? 'Motif de l\'embauche' : 'Motif de la décision'}
                        </h4>
                        {currentWf.motif && <p className="text-[var(--color-fg)]">{currentWf.motif}</p>}
                        {currentWf.commentaire && (
                            <p className="text-[var(--color-fg-muted)] pt-1 border-t border-[var(--color-border-subtle)]">
                                Remarque : {currentWf.commentaire}
                            </p>
                        )}
                    </div>
                )}

                {/* Champ commentaire validation */}
                {isBrouillon && !isCancelling && (
                    <div className="pt-1">
                        <Textarea
                            id="commentaireValidation"
                            name="commentaireValidation"
                            label="Commentaire de validation (optionnel)"
                            rows={2}
                            value={commentaireValidation}
                            onChange={(e) => setCommentaireValidation(e.target.value)}
                            placeholder="Remarque interne de validation..."
                        />
                    </div>
                )}

                {/* Zone de confirmation d'annulation / rejet */}
                {(isBrouillon || isPlanifieFutur) && isCancelling && (
                    <div className="p-4 border bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/30 space-y-3">
                        <h4 className="text-xs font-bold uppercase tracking-wider flex items-center gap-2 text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]">
                            <AlertTriangle size={15} />
                            {isPlanifieFutur ? 'Annulation de la planification' : 'Rejet du brouillon'}
                        </h4>
                        <Textarea
                            id="motifAnnulation"
                            name="motifAnnulation"
                            label="Motif d'annulation (obligatoire)"
                            rows={2}
                            value={motifAnnulation}
                            onChange={(e) => setMotifAnnulation(e.target.value)}
                            placeholder="Renseignez le motif du rejet..."
                            autoFocus
                        />
                    </div>
                )}
            </div>
        </Modal>
    );
}
