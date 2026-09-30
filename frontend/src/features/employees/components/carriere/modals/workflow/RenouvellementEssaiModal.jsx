import { useState, useEffect } from 'react';
import { RefreshCw } from 'lucide-react';
import FormError from '@/components/ui/feedback/FormError';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import contratService from '@/features/employees/api/contratService';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';

export default function RenouvellementEssaiModal({
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
    const isActionAllowed = isEditMode || etatEssai?.canRenew !== false;

    const [dureeProlongationMois, setDureeProlongationMois] = useState('2');
    const [dateFinInitiale, setDateFinInitiale] = useState('');
    const [dateEffet, setDateEffet] = useState('');
    const [nouvelleDateFin, setNouvelleDateFin] = useState('');
    const [motif, setMotif] = useState('Prolongation de la période d\'évaluation du collaborateur');
    const [commentaire, setCommentaire] = useState('');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const dateFinEssaiContrat = contratActif?.dateFinEssai
                ? fmtDateInput(contratActif.dateFinEssai)
                : '';

            setDateFinInitiale(dateFinEssaiContrat);

            if (initialData) {
                const duree = initialData.dureeEssaiMois ? String(initialData.dureeEssaiMois) : '2';
                setDureeProlongationMois(duree);
                setDateEffet(fmtDateInput(initialData.dateEffet) || dateFinEssaiContrat || fmtDateInput(new Date()));
                setNouvelleDateFin(fmtDateInput(initialData.dateFinEssai) || '');
                setMotif(initialData.motif || 'Prolongation de la période d\'évaluation du collaborateur');
                setCommentaire(initialData.commentaire || '');
            } else {
                setDureeProlongationMois('2');
                const defaultDate = dateFinEssaiContrat || fmtDateInput(new Date());
                setDateEffet(defaultDate);
                setMotif('Prolongation de la période d\'évaluation du collaborateur');
                setCommentaire('');
            }
            setErrorMsg('');
        }
    }, [isOpen, initialData, contratActif]);

    useEffect(() => {
        if (!initialData && dateFinInitiale && dureeProlongationMois) {
            const cleanStr = fmtDateInput(dateFinInitiale);
            if (cleanStr) {
                const [y, m, day] = cleanStr.split('-').map(Number);
                if (y && m && day) {
                    const d = new Date(y, m - 1 + Number(dureeProlongationMois), day);
                    setNouvelleDateFin(fmtDateInput(d));
                }
            }
        }
    }, [dateFinInitiale, dureeProlongationMois, initialData]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setErrorMsg('');

        if (!nouvelleDateFin) return setErrorMsg('La nouvelle date de fin d\'essai est obligatoire.');
        if (!dateEffet) return setErrorMsg('La date d\'effet de la décision est obligatoire.');

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: 'RENOUVELLEMENT_ESSAI',
                idContratSource: contratActif?.idContrat || initialData?.idContratSource || null,
                idAffectationSource: affectationActive?.idAffectation || initialData?.idAffectationSource || null,
                idFiliale: contratActif?.idFiliale || initialData?.idFiliale || null,
                idTypeContrat: contratActif?.idTypeContrat || initialData?.idTypeContrat || null,
                idTypeIntervenant: contratActif?.idTypeIntervenant || initialData?.idTypeIntervenant || null,
                dureeEssaiMois: dureeProlongationMois ? Number(dureeProlongationMois) : null,
                dateFinEssai: nouvelleDateFin ? nouvelleDateFin : null,
                idSite: affectationActive?.idSite || initialData?.idSite || null,
                idDepartement: affectationActive?.idDepartement || initialData?.idDepartement || null,
                idService: affectationActive?.idService || initialData?.idService || null,
                idFonction: affectationActive?.idFonction || initialData?.idFonction || null,
                idResponsableHierarchique: affectationActive?.idResponsableHierarchique || initialData?.idResponsableHierarchique || null,
                idCategorieProfessionnelle: affectationActive?.idCategorieProfessionnelle || initialData?.idCategorieProfessionnelle || null,
                idModeCalculPaie: affectationActive?.idModeCalculPaie || initialData?.idModeCalculPaie || null,
                salaireBase: affectationActive?.salaireBase || initialData?.salaireBase || null,
                dateEffet: dateEffet ? dateEffet : null,
                motif: motif ? motif.trim() : null,
                commentaire: commentaire ? commentaire.trim() : null,
            };

            const res = await contratService.confirmerEssaiDirectement(idEmploye, payload);

            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error('Erreur renouvellement essai:', err);
            const errData = err.response?.data;
            const msg = typeof errData === 'string' ? errData : (errData?.message || 'Erreur lors du renouvellement d\'essai.');
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
                    form="renouvellement-essai-form"
                    variant="primary"
                    size="sm"
                    loading={submitting}
                    disabled={!isActionAllowed || submitting}
                    iconLeft={<RefreshCw size={14} />}
                >
                    Renouveler l'essai
                </Button>
            </div>
        </div>
    );

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={nomEmploye ? `Renouvellement d'essai · ${nomEmploye}` : "Renouvellement de la période d'essai"}
            icon={<RefreshCw size={18} />}
            size="lg"
            footer={modalFooter}
        >
            <form id="renouvellement-essai-form" onSubmit={handleSubmit} className="space-y-4">
                {errorMsg && <FormError message={errorMsg} />}

                {/* Synthèse contractuelle compacte */}
                <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs space-y-1.5">
                    <div className="flex justify-between items-baseline">
                        <span className="text-[var(--color-fg-muted)]">Poste actuel :</span>
                        <span className="font-semibold text-[var(--color-fg)]">
                            {affectationActive?.libelleFonction || 'Non renseigné'}
                        </span>
                    </div>
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
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                    <Select
                        label="Prolongation accordée"
                        value={dureeProlongationMois}
                        onChange={e => setDureeProlongationMois(e.target.value)}
                        required
                    >
                        <option value="1">+ 1 mois</option>
                        <option value="2">+ 2 mois</option>
                        <option value="3">+ 3 mois</option>
                    </Select>

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
            </form>
        </Modal>
    );
}
