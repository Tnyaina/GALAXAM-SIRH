import { useState, useEffect } from 'react';
import { UserCheck, RefreshCw } from 'lucide-react';
import FormError from '@/components/ui/feedback/FormError';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Modal from '@/components/ui/overlays/Modal';
import contratService from '@/features/employees/api/contratService';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';

/**
 * Modal unifiée d'arbitrage de la période d'essai (Confirmation définitive ou Renouvellement).
 * Fusionne ConfirmationEssaiModal et RenouvellementEssaiModal avec zéro duplication.
 */
export default function ArbitrageEssaiModal({
    isOpen,
    onClose,
    mode = 'CONFIRMATION', // 'CONFIRMATION' | 'RENOUVELLEMENT'
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    etatEssai = null,
    initialData = null,
    onSuccess,
}) {
    const isConfirmation = mode === 'CONFIRMATION';
    const isEditMode = !!initialData;
    const isActionAllowed = isEditMode || (isConfirmation ? etatEssai?.canConfirm !== false : etatEssai?.canRenew !== false);

    // États communs
    const [dateEffet, setDateEffet] = useState('');
    const [motif, setMotif] = useState('');
    const [commentaire, setCommentaire] = useState('');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // États spécifiques au renouvellement
    const [dureeProlongationMois, setDureeProlongationMois] = useState('2');
    const [dateFinInitiale, setDateFinInitiale] = useState('');
    const [nouvelleDateFin, setNouvelleDateFin] = useState('');

    useEffect(() => {
        if (!isOpen) return;

        const dateFinEssaiContrat = contratActif?.dateFinEssai
            ? fmtDateInput(contratActif.dateFinEssai)
            : '';
        const defaultDate = dateFinEssaiContrat || fmtDateInput(new Date());

        setDateFinInitiale(dateFinEssaiContrat);
        setErrorMsg('');

        if (isConfirmation) {
            if (initialData) {
                setDateEffet(fmtDateInput(initialData.dateEffet || initialData.dateEffetConfirmation) || defaultDate);
                setCommentaire(initialData.commentaire || 'Période d\'essai concluante. Confirmation définitive à l\'emploi.');
            } else {
                setDateEffet(defaultDate);
                setCommentaire('Période d\'essai concluante. Confirmation définitive à l\'emploi.');
            }
        } else {
            // Mode RENOUVELLEMENT
            if (initialData) {
                const duree = initialData.dureeEssaiMois ? String(initialData.dureeEssaiMois) : '2';
                setDureeProlongationMois(duree);
                setDateEffet(fmtDateInput(initialData.dateEffet) || defaultDate);
                setNouvelleDateFin(fmtDateInput(initialData.dateFinEssai) || '');
                setMotif(initialData.motif || 'Prolongation de la période d\'évaluation du collaborateur');
                setCommentaire(initialData.commentaire || '');
            } else {
                setDureeProlongationMois('2');
                setDateEffet(defaultDate);
                setMotif('Prolongation de la période d\'évaluation du collaborateur');
                setCommentaire('');
            }
        }
    }, [isOpen, isConfirmation, initialData, contratActif]);

    // Recalcul automatique de la date de fin d'essai lors d'un renouvellement
    useEffect(() => {
        if (!isConfirmation && !initialData && dateFinInitiale && dureeProlongationMois) {
            const cleanStr = fmtDateInput(dateFinInitiale);
            if (cleanStr) {
                const [y, m, day] = cleanStr.split('-').map(Number);
                if (y && m && day) {
                    const d = new Date(y, m - 1 + Number(dureeProlongationMois), day);
                    setNouvelleDateFin(fmtDateInput(d));
                }
            }
        }
    }, [isConfirmation, dateFinInitiale, dureeProlongationMois, initialData]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setErrorMsg('');

        if (isConfirmation) {
            if (!dateEffet) return setErrorMsg('La date de confirmation est obligatoire.');
        } else {
            if (!nouvelleDateFin) return setErrorMsg('La nouvelle date de fin d\'essai est obligatoire.');
            if (!dateEffet) return setErrorMsg('La date d\'effet de la décision est obligatoire.');
        }

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: isConfirmation ? 'CONFIRMATION_ESSAI' : 'RENOUVELLEMENT_ESSAI',
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
                motif: isConfirmation ? 'Confirmation définitive de la période d\'essai' : (motif ? motif.trim() : null),
                commentaire: commentaire ? commentaire.trim() : null,
                ...(isConfirmation
                    ? {}
                    : {
                        dureeEssaiMois: dureeProlongationMois ? Number(dureeProlongationMois) : null,
                        dateFinEssai: nouvelleDateFin ? nouvelleDateFin : null,
                    }),
            };

            const res = await contratService.confirmerEssaiDirectement(idEmploye, payload);
            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error(`Erreur ${isConfirmation ? 'confirmation' : 'renouvellement'} essai:`, err);
            const errData = err.response?.data;
            const msg = typeof errData === 'string' ? errData : (errData?.message || `Erreur lors de l'action sur la période d'essai.`);
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
                    form="arbitrage-essai-form"
                    variant={isConfirmation ? 'success' : 'primary'}
                    size="sm"
                    loading={submitting}
                    disabled={!isActionAllowed || submitting}
                    iconLeft={isConfirmation ? <UserCheck size={15} /> : <RefreshCw size={14} />}
                >
                    {isConfirmation ? "Confirmer l'essai" : "Renouveler l'essai"}
                </Button>
            </div>
        </div>
    );

    const title = isConfirmation
        ? (nomEmploye ? `Confirmation d'essai · ${nomEmploye}` : "Confirmation de la période d'essai")
        : (nomEmploye ? `Renouvellement d'essai · ${nomEmploye}` : "Renouvellement de la période d'essai");

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={title}
            icon={isConfirmation ? <UserCheck size={18} /> : <RefreshCw size={18} />}
            size="lg"
            footer={modalFooter}
        >
            <form id="arbitrage-essai-form" onSubmit={handleSubmit} className="space-y-4">
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
                    {isConfirmation ? (
                        contratActif?.dateFinEssai && (
                            <div className="flex justify-between items-baseline pt-1 border-t border-[var(--color-border-subtle)]">
                                <span className="text-[var(--color-fg-muted)]">Fin d'essai prévue :</span>
                                <span className="font-semibold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                    {fmtDateFr(contratActif.dateFinEssai)}
                                </span>
                            </div>
                        )
                    ) : (
                        <>
                            {dateFinInitiale && (
                                <div className="flex justify-between items-baseline">
                                    <span className="text-[var(--color-fg-muted)]">Fin d'essai initiale :</span>
                                    <span className="font-semibold text-[var(--color-fg)]">
                                        {fmtDateFr(dateFinInitiale)}
                                    </span>
                                </div>
                            )}
                            {nouvelleDateFin && (
                                <div className="flex justify-between items-baseline pt-1 border-t border-[var(--color-border-subtle)]">
                                    <span className="text-[var(--color-fg-muted)]">Nouvelle échéance prévue :</span>
                                    <span className="font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                        {fmtDateFr(nouvelleDateFin)}
                                    </span>
                                </div>
                            )}
                        </>
                    )}
                </div>

                {isConfirmation ? (
                    <>
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
                    </>
                ) : (
                    <>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                            <SearchSelect
                                label="Prolongation accordée"
                                value={dureeProlongationMois}
                                onChange={val => setDureeProlongationMois(val)}
                                required
                                options={[
                                    { value: '1', label: '+ 1 mois' },
                                    { value: '2', label: '+ 2 mois' },
                                    { value: '3', label: '+ 3 mois' }
                                ]}
                                placeholder="-- Choisir la durée --"
                            />

                            <Input
                                type="date"
                                label="Date de prise d'effet"
                                value={dateEffet}
                                onChange={e => setDateEffet(e.target.value)}
                                required
                            />
                        </div>

                        <Input
                            type="date"
                            label="Nouvelle fin d'essai"
                            value={nouvelleDateFin}
                            onChange={e => setNouvelleDateFin(e.target.value)}
                            required
                            hint="Calculée automatiquement selon la prolongation"
                        />

                        <Input
                            label="Motif du renouvellement"
                            value={motif}
                            onChange={e => setMotif(e.target.value)}
                            required
                            placeholder="Justification RH du renouvellement..."
                        />

                        <Textarea
                            label="Observations complémentaires (optionnel)"
                            value={commentaire}
                            onChange={e => setCommentaire(e.target.value)}
                            rows={2}
                            placeholder="Précisions administratives..."
                        />
                    </>
                )}
            </form>
        </Modal>
    );
}
