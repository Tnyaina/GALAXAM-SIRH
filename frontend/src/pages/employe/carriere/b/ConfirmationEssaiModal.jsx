import { useState } from 'react';
import { UserCheck, ShieldAlert, X } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea } from '@/components/ui/Input';
import contratService from '@/api/employe/contratService';

export default function ConfirmationEssaiModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    onSuccess,
}) {
    const [dateEffet, setDateEffet] = useState(
        contratActif?.dateFinEssai ? new Date(contratActif.dateFinEssai).toISOString().substring(0, 10) : new Date().toISOString().substring(0, 10)
    );
    const [commentaire, setCommentaire] = useState('Période d\'essai concluante. Confirmation définitive à l\'emploi.');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!dateEffet) return setErrorMsg('La date de confirmation est obligatoire.');

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: 'CONFIRMATION_ESSAI',
                idContratSource: contratActif?.idContrat || null,
                idAffectationSource: affectationActive?.idAffectation || null,
                // Même affectation et contrat reconduits
                idFiliale: contratActif?.idFiliale || null,
                idTypeContrat: contratActif?.idTypeContrat || null,
                idTypeIntervenant: contratActif?.idTypeIntervenant || null,
                idSite: affectationActive?.idSite,
                idDepartement: affectationActive?.idDepartement,
                idService: affectationActive?.idService,
                idFonction: affectationActive?.idFonction,
                idResponsableHierarchique: affectationActive?.idResponsableHierarchique || null,
                idCategorieProfessionnelle: affectationActive?.idCategorieProfessionnelle,
                idModeCalculPaie: affectationActive?.idModeCalculPaie,
                salaireBase: affectationActive?.salaireBase,
                dateEffet: dateEffet,
                motif: 'Confirmation définitive de la période d\'essai',
                commentaire: commentaire,
            };

            const res = await contratService.creerBrouillonTransition(idEmploye, payload);
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

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-sm shadow-2xl w-full max-w-md overflow-hidden">
                <div className="px-5 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-base text-ink-900 dark:text-white flex items-center justify-between bg-success-500/10">
                    <div className="flex items-center gap-2 text-success-600 dark:text-success-400">
                        <UserCheck size={20} />
                        <span>Confirmation de la Période d'Essai</span>
                    </div>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1">
                        <X size={18} />
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="p-5 space-y-4 text-xs">
                    {errorMsg && (
                        <div className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-3 rounded text-xs font-semibold flex items-center gap-2">
                            <ShieldAlert size={16} />
                            <span>{errorMsg}</span>
                        </div>
                    )}

                    <p className="text-ink-600 dark:text-ink-300">
                        Confirmer officiellement l'essai pour <span className="font-bold text-ink-900 dark:text-white">{nomEmploye}</span>. Le statut d'essai sera levé avec succès.
                    </p>

                    <Input
                        type="date"
                        id="dateEffetConfirmation"
                        name="dateEffet"
                        label="Date d'Effet de la Confirmation *"
                        value={dateEffet}
                        onChange={e => setDateEffet(e.target.value)}
                        required
                    />

                    <Textarea
                        id="commentaireConfirmation"
                        name="commentaire"
                        label="Commentaire / Évaluation d'essai"
                        value={commentaire}
                        onChange={e => setCommentaire(e.target.value)}
                        rows={3}
                        placeholder="Remarques du manager ou RH..."
                    />

                    <div className="pt-3 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-2">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button type="submit" variant="success" size="sm" loading={submitting} iconLeft={<UserCheck size={16} />}>
                            Valider la Confirmation
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
