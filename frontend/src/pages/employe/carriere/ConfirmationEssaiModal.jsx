import { useState } from 'react';
import { UserCheck, ShieldAlert, X, FileSignature } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea } from '@/components/ui/Input';
import contratService from '@/api/employe/contratService';
import { DocumentFrame, DocumentHeader, Clause, DocumentFooter } from '@/components/carriere/ContractDocument';
import { fmtDateFr } from '@/utils/carriereUtils';

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
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-sm shadow-2xl w-full max-w-3xl overflow-hidden max-h-[90vh] flex flex-col">
                <div className="px-5 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-base text-ink-900 dark:text-white flex items-center justify-between bg-success-500/10 flex-shrink-0">
                    <div className="flex items-center gap-2 text-success-600 dark:text-success-400">
                        <UserCheck size={20} />
                        <span>Confirmation de la Période d'Essai</span>
                    </div>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1">
                        <X size={18} />
                    </button>
                </div>

                <div className="flex-1 overflow-y-auto flex flex-col md:flex-row">
                    <form onSubmit={handleSubmit} className="flex-1 min-w-0 p-5 space-y-4 text-xs">
                        {errorMsg && (
                            <div className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-3 rounded text-xs font-semibold flex items-center gap-2">
                                <ShieldAlert size={16} />
                                <span>{errorMsg}</span>
                            </div>
                        )}

                        <p className="text-ink-600 dark:text-ink-300">
                            Confirme officiellement l'essai pour <span className="font-bold text-ink-900 dark:text-white">{nomEmploye}</span> : le poste, le site et le salaire actuels sont reconduits sans changement, seul le statut d'essai est levé.
                        </p>

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
                            label="Commentaire / évaluation d'essai"
                            value={commentaire}
                            onChange={e => setCommentaire(e.target.value)}
                            rows={3}
                            placeholder="Remarques du manager ou RH..."
                        />
                    </form>

                    {/* Aperçu avenant */}
                    <div className="md:w-[300px] flex-shrink-0 border-t md:border-t-0 md:border-l border-ink-150 dark:border-ink-700 bg-ink-50/60 dark:bg-ink-950/40 p-4">
                        <div className="flex items-center gap-2 mb-2 text-[11px] font-bold uppercase tracking-wider text-ink-400">
                            <FileSignature size={13} /> Aperçu de l'avenant
                        </div>
                        <DocumentFrame watermark="Avenant">
                            <DocumentHeader eyebrow="Confirmation d'essai" title="Avenant au Contrat" />
                            <div className="pt-3 text-[12.5px] leading-relaxed space-y-2">
                                <p>
                                    Il est confirmé que <strong>{nomEmploye || '……………'}</strong> occupe désormais son poste à titre définitif.
                                </p>
                                <Clause label="Statut" value="Essai levé — confirmation définitive" />
                                <Clause label="Date d'effet" value={fmtDateFr(dateEffet)} />
                                <Clause label="Poste, site, salaire" value="Inchangés" />
                            </div>
                            <DocumentFooter status="Brouillon (en attente de validation)" />
                        </DocumentFrame>
                    </div>
                </div>

                <div className="px-5 py-3 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-2 flex-shrink-0">
                    <Button type="button" onClick={onClose} variant="ghost" size="sm">
                        Annuler
                    </Button>
                    <Button type="button" onClick={handleSubmit} variant="success" size="sm" loading={submitting} iconLeft={<UserCheck size={16} />}>
                        Valider la Confirmation
                    </Button>
                </div>
            </div>
        </div>
    );
}
