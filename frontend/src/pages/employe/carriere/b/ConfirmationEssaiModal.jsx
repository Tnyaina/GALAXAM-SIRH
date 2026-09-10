import { useState, useEffect } from 'react';
import { UserCheck, ShieldAlert, X, FileSignature, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import contratService from '@/api/employe/contratService';
import {
    DocumentFrame, DocumentHeader, Clause, DocumentFooter
} from '@/components/carriere/ContractDocument';
import { fmtDateFr, fmtDateInput } from '@/utils/carriereUtils';

// ── COMPOSANT APERÇU AVENANT (Déclaré hors du render pour préserver le state) ──
function ConfirmationPreview({
    className = '',
    nomEmploye,
    dateEffet,
}) {
    return (
        <DocumentFrame watermark="Avenant" className={className}>
            <DocumentHeader
                eyebrow="Confirmation d'essai"
                title="Avenant au Contrat"
            />

            <div className="pt-3 text-[12.5px] leading-relaxed space-y-2 font-normal">
                <p>
                    Il est confirmé que <strong>{nomEmploye || '……………'}</strong> occupe désormais son poste à titre définitif.
                </p>

                <div className="space-y-1 pt-1">
                    <Clause
                        label="Statut"
                        value="Essai levé — confirmation définitive"
                    />
                    <Clause
                        label="Date d'effet"
                        value={fmtDateFr(dateEffet)}
                        className="text-brand-500 font-bold"
                    />
                    <Clause
                        label="Poste, site, salaire"
                        value="Inchangés"
                    />
                </div>
            </div>

            <DocumentFooter status="Brouillon (en attente de validation)" />
        </DocumentFrame>
    );
}

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
export default function ConfirmationEssaiModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    etatEssai = null,
    initialData = null,
    onSuccess,
}) {
    const isEditMode = !!initialData;
    const wfId = initialData?.idWorkflow || initialData?.workflow?.idWorkflow;
    const isActionAllowed = isEditMode || etatEssai?.canConfirm !== false;

    const [dateEffet, setDateEffet] = useState('');
    const [commentaire, setCommentaire] = useState('Période d\'essai concluante. Confirmation définitive à l\'emploi.');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [mounted, setMounted] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const defaultDate = contratActif?.dateFinEssai
                ? fmtDateInput(contratActif.dateFinEssai)
                : fmtDateInput(new Date());

            if (initialData) {
                setDateEffet(fmtDateInput(initialData.dateEffet || initialData.dateEffetConfirmation) || defaultDate);
                setCommentaire(initialData.commentaire || 'Période d\'essai concluante. Confirmation définitive à l\'emploi.');
            } else {
                setDateEffet(defaultDate);
                setCommentaire('Période d\'essai concluante. Confirmation définitive à l\'emploi.');
            }

            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
        setShowPreviewMobile(false);
    }, [isOpen, initialData, contratActif]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setErrorMsg('');

        if (!dateEffet) return setErrorMsg('La date de confirmation est obligatoire.');

        setSubmitting(true);
        try {
            // Remplacer l'objet payload par celui-ci :
            const payload = {
                codeTypeEvenement: 'CONFIRMATION_ESSAI',
                idContratSource: contratActif?.idContrat || initialData?.idContratSource || null,
                idAffectationSource: affectationActive?.idAffectation || initialData?.idAffectationSource || null,
                idFiliale: contratActif?.idFiliale || initialData?.idFiliale || null,
                idTypeContrat: contratActif?.idTypeContrat || initialData?.idTypeContrat || null,
                idTypeIntervenant: contratActif?.idTypeIntervenant || initialData?.idTypeIntervenant || null,
                idSite: affectationActive?.idSite || initialData?.idSite || null,
                idDepartement: affectationActive?.idDepartement || initialData?.idDepartement || null,
                idService: affectationActive?.idService || initialData?.idService || null,
                idFonction: affectationActive?.idFonction || initialData?.idFonction || null,
                idResponsableHierarchique: affectationActive?.idResponsableHierarchique || initialData?.idResponsableHierarchique || null,
                idCategorieProfessionnelle: affectationActive?.idCategorieProfessionnelle || initialData?.idCategorieProfessionnelle || null,
                idModeCalculPaie: affectationActive?.idModeCalculPaie || initialData?.idModeCalculPaie || null,
                salaireBase: affectationActive?.salaireBase || initialData?.salaireBase || null,
                dateEffet: dateEffet ? dateEffet : null, // Securite chaine vide -> null
                motif: 'Confirmation définitive de la période d\'essai',
                commentaire: commentaire ? commentaire.trim() : null,
            };

            const res = await contratService.confirmerEssaiDirectement(idEmploye, payload);

            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error('Erreur confirmation essai:', err);
            const errData = err.response?.data;
            const msg = typeof errData === 'string' ? errData : (errData?.message || 'Erreur lors de la confirmation d\'essai.');
            setErrorMsg(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const previewProps = {
        nomEmploye,
        dateEffet,
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px]">
            {/* Overlay cliquable */}
            <div className="absolute inset-0" onClick={onClose} />

            {/* Modal Centré Split-Screen 50 / 50 */}
            <div
                className={`
                    relative bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-none shadow-2xl
                    w-full max-w-[94vw] h-[90vh] overflow-hidden flex flex-col
                    transform   
                    ${mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'}
                `}
            >
                {/* Header (fixe) */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-success-500/10 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-success-600 dark:text-success-400 font-bold text-lg">
                            <UserCheck size={20} />
                            <span>Confirmation de la Période d'Essai</span>
                            {nomEmploye && (
                                <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowPreviewMobile(v => !v)}
                                className="lg:hidden flex items-center gap-1.5 text-xs font-semibold text-success-600 border border-success-500/30 rounded-none px-2.5 py-1.5"
                            >
                                {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                                {showPreviewMobile ? 'Masquer l\'aperçu' : 'Voir l\'avenant'}
                            </button>
                            <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-none">
                                <X size={20} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Corps : Split Screen 50 / 50 */}
                <div className="flex-1 overflow-hidden flex flex-col lg:flex-row min-h-0">

                    {/* COLONNE GAUCHE (50%) : Formulaire */}
                    <form onSubmit={handleSubmit} className="w-full lg:w-1/2 min-w-0 overflow-y-auto p-4 sm:p-5 space-y-4 border-b lg:border-b-0 lg:border-r border-ink-150 dark:border-ink-700">
                        {errorMsg && (
                            <div className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-3 rounded-none text-xs font-semibold flex items-center gap-2">
                                <ShieldAlert size={16} className="flex-shrink-0" />
                                <span>{errorMsg}</span>
                            </div>
                        )}

                        {/* Aperçu mobile repliable */}
                        {showPreviewMobile && (
                            <ConfirmationPreview {...previewProps} className="lg:hidden mb-6" />
                        )}

                        <p className="text-xs text-ink-600 dark:text-ink-300 font-normal">
                            Confirme officiellement l'essai pour <span className="font-bold text-ink-900 dark:text-white">{nomEmploye}</span> : le poste, le site et le salaire actuels sont reconduits sans changement, seul le statut d'essai est levé.
                        </p>

                        <Input
                            type="date"
                            id="dateEffetConfirmation"
                            name="dateEffet"
                            label="Date d'effet de la confirmation *"
                            value={dateEffet}
                            onChange={e => setDateEffet(e.target.value)}
                            required
                        />

                        <Textarea
                            id="commentaireConfirmation"
                            name="commentaire"
                            label="Commentaire / évaluation d'essai"
                            value={commentaire}
                            onChange={e => setCommentaire(e.target.value)}
                            rows={3}
                            placeholder="Remarques du manager ou RH..."
                        />
                    </form>

                    {/* COLONNE DROITE (50%) : Aperçu Avenant */}
                    <div className="hidden lg:block w-1/2 flex-shrink-0 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-4 sm:p-5 flex flex-col items-center">
                        <div className="w-full max-w-[560px]">
                            <div className="flex items-center gap-2 mb-4 text-xs font-bold uppercase tracking-wider text-ink-400">
                                <FileSignature size={14} /> Aperçu de l'avenant en temps réel
                            </div>
                            <ConfirmationPreview {...previewProps} />
                        </div>
                    </div>
                </div>

                {/* Footer Actions (fixe) */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex items-center justify-between gap-3">
                    <p className="text-[11px] text-ink-400 font-normal">
                        Cette décision sera enregistrée en <span className="font-bold text-warning-600">BROUILLON</span> pour validation.
                    </p>
                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="success"
                            size="sm"
                            loading={submitting}
                            disabled={!isActionAllowed || submitting}
                            iconLeft={<UserCheck size={16} />}
                        >
                            Valider la Confirmation
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
