import { useState, useEffect } from 'react';
import { UserCheck } from 'lucide-react';
import FormError from '@/components/ui/feedback/FormError';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import contratService from '@/features/employees/api/contratService';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';

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
    const isActionAllowed = isEditMode || etatEssai?.canConfirm !== false;

    const [dateEffet, setDateEffet] = useState('');
    const [commentaire, setCommentaire] = useState('Période d\'essai concluante. Confirmation définitive à l\'emploi.');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

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
            setErrorMsg('');
        }
    }, [isOpen, initialData, contratActif]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setErrorMsg('');

        if (!dateEffet) return setErrorMsg('La date de confirmation est obligatoire.');

        setSubmitting(true);
        try {
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
                dateEffet: dateEffet ? dateEffet : null,
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

    const modalFooter = (
        <div className="flex items-center justify-between w-full gap-3 flex-wrap">
            <p className="text-xs text-[var(--color-fg-muted)]">
                Statut initial : <span className="font-semibold text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]">Brouillon</span> (validation requise).
            </p>
            <div className="flex items-center gap-2">
                <Button type="button" onClick={onClose} variant="ghost" size="sm">
                    Annuler
                </Button>
                <Button
                    type="submit"
                    form="confirmation-essai-form"
                    variant="success"
                    size="sm"
                    loading={submitting}
                    disabled={!isActionAllowed || submitting}
                    iconLeft={<UserCheck size={15} />}
                >
                    Confirmer l'essai
                </Button>
            </div>
        </div>
    );

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={nomEmploye ? `Confirmation d'essai · ${nomEmploye}` : "Confirmation de la période d'essai"}
            icon={<UserCheck size={18} />}
            size="lg"
            footer={modalFooter}
        >
            <form id="confirmation-essai-form" onSubmit={handleSubmit} className="space-y-4">
                {errorMsg && <FormError message={errorMsg} />}

                {/* Synthèse contractuelle compacte */}
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs space-y-1.5">
                    <div className="flex justify-between items-baseline">
                        <span className="text-[var(--color-fg-muted)]">Poste actuel :</span>
                        <span className="font-semibold text-[var(--color-fg)]">
                            {affectationActive?.libelleFonction || 'Non renseigné'}
                        </span>
                    </div>
                    <div className="flex justify-between items-baseline">
                        <span className="text-[var(--color-fg-muted)]">Contrat :</span>
                        <span className="font-semibold text-[var(--color-fg)]">
                            {contratActif?.libelleTypeContrat || 'Contrat'} · {contratActif?.numeroContrat || 'En cours'}
                        </span>
                    </div>
                    {contratActif?.dateFinEssai && (
                        <div className="flex justify-between items-baseline pt-1 border-t border-[var(--color-border-subtle)]">
                            <span className="text-[var(--color-fg-muted)]">Fin d'essai prévue :</span>
                            <span className="font-semibold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                {fmtDateFr(contratActif.dateFinEssai)}
                            </span>
                        </div>
                    )}
                </div>

                <div className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                    Confirmation définitive du collaborateur dans son poste actuel (maintien des termes contractuels et levée de l'essai).
                </div>

                <Input
                    type="date"
                    id="dateEffetConfirmation"
                    name="dateEffet"
                    label="Date d'effet de la confirmation"
                    value={dateEffet}
                    onChange={e => setDateEffet(e.target.value)}
                    required
                />

                <Textarea
                    id="commentaireConfirmation"
                    name="commentaire"
                    label="Commentaire / évaluation d'essai (optionnel)"
                    value={commentaire}
                    onChange={e => setCommentaire(e.target.value)}
                    rows={3}
                    placeholder="Observations ou synthèse de validation..."
                />
            </form>
        </Modal>
    );
}
