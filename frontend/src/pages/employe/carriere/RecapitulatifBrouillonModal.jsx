import { useState } from 'react';
import { 
    FileText, Building, Landmark, CheckCircle, Edit2, XCircle, 
    AlertTriangle, ShieldAlert, Clock, X
} from 'lucide-react';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { Textarea } from '@/components/ui/Input';
import contratService from '@/api/employe/contratService';

function InfoRow({ label, value, highlight = false, badge = null }) {
    return (
        <div className="py-1.5 flex justify-between items-center text-xs border-b border-ink-100 dark:border-ink-800/60 last:border-0">
            <span className="text-ink-400 font-medium">{label}</span>
            <div className="flex items-center gap-2">
                {badge && <span className="flex-shrink-0">{badge}</span>}
                <span className={`font-semibold ${highlight ? 'text-brand-500 font-mono text-sm' : 'text-ink-800 dark:text-ink-100'}`}>
                    {value || '-'}
                </span>
            </div>
        </div>
    );
}

export default function RecapitulatifBrouillonModal({
    isOpen,
    onClose,
    workflowCpl,
    onEdit,
    onActionSuccess,
}) {
    const [isValidating, setIsValidating] = useState(false);
    const [isCancelling, setIsCancelling] = useState(false);
    const [commentaireValidation, setCommentaireValidation] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    const fmtDate = (d) => {
        if (!d) return '-';
        const str = typeof d === 'string' ? d.substring(0, 10) : new Date(d).toISOString().substring(0, 10);
        const [yyyy, mm, dd] = str.split('-');
        return `${dd}/${mm}/${yyyy}`;
    };

    const fmtMoney = (amount) => {
        if (amount === undefined || amount === null) return '-';
        return new Intl.NumberFormat('fr-MG', { style: 'currency', currency: 'MGA', maximumFractionDigits: 0 }).format(amount);
    };

    const handleValiderConfirm = async () => {
        setErrorMsg('');
        setSubmitting(true);
        try {
            const res = await contratService.validerWorkflow(workflowCpl.idWorkflow, commentaireValidation);
            if (onActionSuccess) onActionSuccess('Workflow RH validé avec succès ! Le contrat et l\'affectation sont désormais ACTIFS.', res);
            onClose();
        } catch (err) {
            console.error('Erreur validation workflow:', err);
            setErrorMsg(err.response?.data || 'Erreur lors de la validation du workflow.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleAnnulerConfirm = async () => {
        setErrorMsg('');
        setSubmitting(true);
        try {
            const res = await contratService.annulerWorkflow(workflowCpl.idWorkflow, commentaireValidation || 'Annulation par la DAF');
            if (onActionSuccess) onActionSuccess('Le brouillon de workflow a été annulé.', res);
            onClose();
        } catch (err) {
            console.error('Erreur annulation workflow:', err);
            setErrorMsg(err.response?.data || 'Erreur lors de l\'annulation du workflow.');
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen || !workflowCpl) return null;

    const isBrouillon = workflowCpl.codeStatutWorkflow === 'BROUILLON';
    const isValide = workflowCpl.codeStatutWorkflow === 'VALIDE';

    const getStatutBadgeVariant = (code) => {
        if (code === 'VALIDE' || code === 'ACTIF') return 'success';
        if (code === 'ANNULE' || code === 'ROMPU') return 'danger';
        if (code === 'BROUILLON') return 'warning';
        return 'default';
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-2xl max-w-3xl w-full my-8 overflow-hidden flex flex-col max-h-[90vh]">
                
                {/* Header */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-ink-50/80 dark:bg-ink-950/60 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-brand-500/10 text-brand-500 rounded-full flex items-center justify-center font-bold">
                            <FileText size={20} />
                        </div>
                        <div>
                            <div className="flex items-center gap-2">
                                <h2 className="font-bold text-base text-ink-900 dark:text-white">
                                    {isBrouillon ? 'Récapitulatif & Pre-Validation DAF' : 'Détail de l\'Événement RH'}
                                </h2>
                                <Badge variant={getStatutBadgeVariant(workflowCpl.codeStatutWorkflow)} size="sm">
                                    {workflowCpl.libelleStatutWorkflow || workflowCpl.codeStatutWorkflow || 'BROUILLON'}
                                </Badge>
                            </div>
                            <p className="text-xs text-ink-400">
                                Embauche N° <span className="font-mono font-bold text-brand-500">{workflowCpl.numeroContratCible || 'Nouveau'}</span> — {workflowCpl.nomPrenomEmploye} ({workflowCpl.matriculeEmploye})
                            </p>
                            <p className="text-[11px] text-ink-400 flex items-center gap-3 mt-0.5">
                                <span>
                                    Type d'événement : <span className="font-semibold text-ink-600 dark:text-ink-300">{workflowCpl.libelleTypeEvenement || workflowCpl.codeTypeEvenement || '-'}</span>
                                </span>
                                <span>
                                    Date d'effet : <span className="font-mono font-semibold text-ink-600 dark:text-ink-300">{fmtDate(workflowCpl.dateEffet)}</span>
                                </span>
                            </p>
                        </div>
                    </div>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                        <X size={18} />
                    </button>
                </div>

                {/* Body Content */}
                <div className="flex-1 overflow-y-auto p-6 space-y-6">
                    {errorMsg && (
                        <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-4 text-xs flex items-center gap-2 font-medium">
                            <ShieldAlert size={16} className="flex-shrink-0" />
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    {/* Alerte Information */}
                    {isBrouillon ? (
                        <div className="bg-brand-500/10 border border-brand-500/20 p-4 rounded-sm flex items-start gap-3">
                            <Clock size={18} className="text-brand-500 flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-brand-600 dark:text-brand-400">
                                    Vérification de l'embauche avant officialisation
                                </p>
                                <p className="text-ink-500 dark:text-ink-300">
                                    Veuillez relire l'ensemble des conditions contractuelles et financières ci-dessous. En cliquant sur "Valider définitivement", le contrat passera au statut <span className="font-bold text-success-600">ACTIF</span>.
                                </p>
                            </div>
                        </div>
                    ) : isValide ? (
                        <div className="bg-success-500/10 border border-success-500/20 p-4 rounded-sm flex items-start gap-3">
                            <CheckCircle size={18} className="text-success-600 flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-success-700 dark:text-success-400">
                                    Événement RH validé et appliqué
                                </p>
                                <p className="text-ink-500 dark:text-ink-300">
                                    Ce workflow d'embauche a été validé. Le contrat et l'affectation sont actifs dans le dossier carrière de l'employé.
                                </p>
                            </div>
                        </div>
                    ) : (
                        <div className="bg-danger-500/10 border border-danger-500/20 p-4 rounded-sm flex items-start gap-3">
                            <XCircle size={18} className="text-danger-600 flex-shrink-0 mt-0.5" />
                            <div className="text-xs space-y-1">
                                <p className="font-bold text-danger-700 dark:text-danger-400">
                                    Événement RH annulé
                                </p>
                                <p className="text-ink-500 dark:text-ink-300">
                                    Ce brouillon a été annulé et n'a produit aucun effet contractuel.
                                </p>
                            </div>
                        </div>
                    )}

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                        
                        {/* Carte 1 : Contrat de Travail */}
                        <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-700/80 rounded-sm space-y-3">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 border-b border-ink-150 dark:border-ink-700 pb-2 flex items-center gap-1.5">
                                <Building size={14} /> Contrat de Travail
                            </h3>
                            <div className="space-y-1">
                                <InfoRow label="N° de Contrat" value={workflowCpl.numeroContratCible} highlight />
                                <InfoRow label="Type de Contrat" value={workflowCpl.libelleTypeContrat} />
                                <InfoRow label="Type d'Intervenant" value={workflowCpl.libelleTypeIntervenant} />
                                <InfoRow label="Filiale Employeuse" value={workflowCpl.libelleFiliale} />
                                <InfoRow label="Date de Début" value={fmtDate(workflowCpl.dateDebutContrat)} />
                                {workflowCpl.dateFinContrat && (
                                    <InfoRow label="Date de Fin" value={fmtDate(workflowCpl.dateFinContrat)} />
                                )}
                                <InfoRow label="Date de Signature" value={fmtDate(workflowCpl.dateSignature)} />
                                <InfoRow 
                                    label="Comptabilisé Effectif" 
                                    value={workflowCpl.comptabiliseEffectif ? 'Oui (Salarié)' : 'Non (Stage/Consultant)'}
                                    badge={<Badge variant={workflowCpl.comptabiliseEffectif ? 'success' : 'default'} size="sm">{workflowCpl.comptabiliseEffectif ? 'Effectif' : 'Hors-effectif'}</Badge>}
                                />
                            </div>
                        </div>

                        {/* Carte 2 : Affectation & Rémunération */}
                        <div className="bg-ink-50/50 dark:bg-ink-950/40 p-4 border border-ink-150 dark:border-ink-700/80 rounded-sm space-y-3">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 border-b border-ink-150 dark:border-ink-700 pb-2 flex items-center gap-1.5">
                                <Landmark size={14} /> Affectation & Rémunération
                            </h3>
                            <div className="space-y-1">
                                <InfoRow label="Site / Implantation" value={workflowCpl.libelleSite} />
                                <InfoRow label="Département" value={workflowCpl.libelleDepartement} />
                                <InfoRow label="Service" value={workflowCpl.libelleService} />
                                <InfoRow label="Fonction / Poste" value={workflowCpl.libelleFonction} />
                                <InfoRow label="Catégorie Pro" value={workflowCpl.libelleCategoriePro} />
                                <InfoRow label="Supérieur Hiérarchique" value={workflowCpl.nomPrenomResponsable} />
                                <InfoRow label="Mode Calcul Paie" value={workflowCpl.libelleModeCalculPaie} />
                                <InfoRow label="Salaire de Base" value={fmtMoney(workflowCpl.salaireBase)} highlight />
                                <InfoRow label="Date d'Effet" value={fmtDate(workflowCpl.dateEffet)} />
                            </div>
                        </div>
                    </div>

                    {/* Context & Remarks */}
                    <div className="bg-ink-50/30 dark:bg-ink-950/20 p-4 border border-ink-150 dark:border-ink-700 rounded-sm space-y-2">
                        <p className="text-[10px] font-bold uppercase tracking-wider text-ink-400">Motif & Justification de l'embauche</p>
                        <p className="text-xs text-ink-800 dark:text-ink-200 italic">{workflowCpl.motif || 'Aucun motif renseigné.'}</p>
                        {workflowCpl.commentaire && (
                            <p className="text-xs text-ink-400 border-t border-ink-150 dark:border-ink-800 pt-2">
                                <span className="font-semibold">Remarque :</span> {workflowCpl.commentaire}
                            </p>
                        )}
                    </div>

                    {/* Zone de confirmation pour Validation ou Annulation (seulement si brouillon) */}
                    {isBrouillon && (isValidating || isCancelling) && (
                        <div className={`p-4 border rounded-sm space-y-3 ${
                            isValidating ? 'bg-success-500/10 border-success-500/30' : 'bg-danger-500/10 border-danger-500/30'
                        }`}>
                            <h4 className={`text-xs font-bold uppercase tracking-wider flex items-center gap-2 ${
                                isValidating ? 'text-success-700 dark:text-success-400' : 'text-danger-700 dark:text-danger-400'
                            }`}>
                                {isValidating ? <CheckCircle size={16} /> : <AlertTriangle size={16} />}
                                {isValidating ? 'Confirmation de la validation DAF' : 'Confirmation de l\'annulation du brouillon'}
                            </h4>
                            <Textarea
                                id="commentaireValidation"
                                name="commentaireValidation"
                                label={isValidating ? "Commentaire de validation (optionnel)" : "Motif de l'annulation (obligatoire)"}
                                rows={2}
                                value={commentaireValidation}
                                onChange={(e) => setCommentaireValidation(e.target.value)}
                                placeholder={isValidating ? "Ex: Dossier vérifié et validé par la DAF..." : "Ex: Candidat désisté, embauche annulée..."}
                            />
                            <div className="flex justify-end gap-2 pt-1">
                                <Button type="button" onClick={() => { setIsValidating(false); setIsCancelling(false); }} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                {isValidating ? (
                                    <Button type="button" onClick={handleValiderConfirm} variant="success" size="sm" loading={submitting}>
                                        Confirmer la Validation
                                    </Button>
                                ) : (
                                    <Button type="button" onClick={handleAnnulerConfirm} variant="danger" size="sm" loading={submitting}>
                                        Confirmer l'Annulation
                                    </Button>
                                )}
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer Actions */}
                {isBrouillon ? (
                    !isValidating && !isCancelling && (
                        <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-between items-center">
                            <Button
                                type="button"
                                onClick={() => setIsCancelling(true)}
                                variant="danger"
                                size="sm"
                                iconLeft={<XCircle size={16} />}
                            >
                                Annuler ce Brouillon
                            </Button>

                            <div className="flex items-center gap-3">
                                <Button
                                    type="button"
                                    onClick={() => { onClose(); if (onEdit) onEdit(workflowCpl); }}
                                    variant="outline"
                                    size="sm"
                                    iconLeft={<Edit2 size={16} />}
                                >
                                    Corriger / Modifier
                                </Button>

                                <Button
                                    type="button"
                                    onClick={() => setIsValidating(true)}
                                    variant="success"
                                    size="sm"
                                    iconLeft={<CheckCircle size={16} />}
                                >
                                    Valider Définitivement (Activer)
                                </Button>
                            </div>
                        </div>
                    )
                ) : (
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Fermer
                        </Button>
                    </div>
                )}
            </div>
        </div>
    );
}