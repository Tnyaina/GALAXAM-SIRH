import { useState, useEffect } from 'react';
import { RefreshCw, ShieldAlert, X, FileSignature } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import contratService from '@/api/employe/contratService';
import { DocumentFrame, DocumentHeader, Clause, DocumentFooter } from '@/components/carriere/ContractDocument';
import { fmtDateFr } from '@/utils/carriereUtils';

export default function RenouvellementEssaiModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    onSuccess,
}) {
    const [dureeProlongationMois, setDureeProlongationMois] = useState('2');
    const [dateFinInitiale] = useState(
        contratActif?.dateFinEssai ? new Date(contratActif.dateFinEssai).toISOString().substring(0, 10) : ''
    );
    const [nouvelleDateFin, setNouvelleDateFin] = useState('');
    const [motif, setMotif] = useState('Prolongation de la période d\'évaluation du collaborateur');
    const [commentaire, setCommentaire] = useState('');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (dateFinInitiale && dureeProlongationMois) {
            const d = new Date(dateFinInitiale);
            if (!isNaN(d.getTime())) {
                d.setMonth(d.getMonth() + Number(dureeProlongationMois));
                setNouvelleDateFin(d.toISOString().substring(0, 10));
            }
        }
    }, [dateFinInitiale, dureeProlongationMois]);

    if (!isOpen) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!nouvelleDateFin) return setErrorMsg('La nouvelle date de fin d\'essai est obligatoire.');

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: 'RENOUVELLEMENT_ESSAI',
                idContratSource: contratActif?.idContrat || null,
                idAffectationSource: affectationActive?.idAffectation || null,
                // Même affectation et contrat reconduits
                idFiliale: contratActif?.idFiliale || null,
                idTypeContrat: contratActif?.idTypeContrat || null,
                idTypeIntervenant: contratActif?.idTypeIntervenant || null,
                dureeEssaiMois: Number(dureeProlongationMois),
                dateFinEssai: nouvelleDateFin,
                idSite: affectationActive?.idSite,
                idDepartement: affectationActive?.idDepartement,
                idService: affectationActive?.idService,
                idFonction: affectationActive?.idFonction,
                idResponsableHierarchique: affectationActive?.idResponsableHierarchique || null,
                idCategorieProfessionnelle: affectationActive?.idCategorieProfessionnelle,
                idModeCalculPaie: affectationActive?.idModeCalculPaie,
                salaireBase: affectationActive?.salaireBase,
                dateEffet: nouvelleDateFin,
                motif: motif,
                commentaire: commentaire,
            };

            const res = await contratService.creerBrouillonTransition(idEmploye, payload);
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

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-sm shadow-2xl w-full max-w-3xl overflow-hidden max-h-[90vh] flex flex-col">
                <div className="px-5 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-base text-ink-900 dark:text-white flex items-center justify-between bg-warning-500/10 flex-shrink-0">
                    <div className="flex items-center gap-2 text-warning-600 dark:text-warning-400">
                        <RefreshCw size={20} />
                        <span>Renouvellement de la Période d'Essai</span>
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
                            Prolonge une seule fois la période d'essai de <span className="font-bold text-ink-900 dark:text-white">{nomEmploye}</span>.
                        </p>

                        <div className="grid grid-cols-2 gap-4">
                            <Select
                                id="dureeProlongationMois"
                                name="dureeProlongationMois"
                                label="Durée de la prolongation"
                                value={dureeProlongationMois}
                                onChange={e => setDureeProlongationMois(e.target.value)}
                            >
                                <option value="1">+ 1 mois</option>
                                <option value="2">+ 2 mois</option>
                                <option value="3">+ 3 mois</option>
                            </Select>

                            <Input
                                type="date"
                                id="nouvelleDateFin"
                                name="nouvelleDateFin"
                                label="Nouvelle date de fin d'essai"
                                value={nouvelleDateFin}
                                onChange={e => setNouvelleDateFin(e.target.value)}
                                required
                            />
                        </div>

                        <Input
                            type="text"
                            id="motifRenouvellement"
                            name="motif"
                            label="Motif du renouvellement"
                            value={motif}
                            onChange={e => setMotif(e.target.value)}
                            required
                        />

                        <Textarea
                            id="commentaireRenouvellement"
                            name="commentaire"
                            label="Commentaire / justification RH"
                            value={commentaire}
                            onChange={e => setCommentaire(e.target.value)}
                            rows={2}
                            placeholder="Précisions sur les compétences à valider durant la prolongation..."
                        />
                    </form>

                    {/* Aperçu avenant */}
                    <div className="md:w-[300px] flex-shrink-0 border-t md:border-t-0 md:border-l border-ink-150 dark:border-ink-700 bg-ink-50/60 dark:bg-ink-950/40 p-4">
                        <div className="flex items-center gap-2 mb-2 text-[11px] font-bold uppercase tracking-wider text-ink-400">
                            <FileSignature size={13} /> Aperçu de l'avenant
                        </div>
                        <DocumentFrame watermark="Avenant">
                            <DocumentHeader eyebrow="Renouvellement d'essai" title="Avenant au Contrat" />
                            <div className="pt-3 text-[12.5px] leading-relaxed space-y-2">
                                <p>
                                    La période d'essai de <strong>{nomEmploye || '……………'}</strong> est prolongée d'une durée unique.
                                </p>
                                <Clause label="Prolongation" value={dureeProlongationMois ? `+ ${dureeProlongationMois} mois` : ''} />
                                <Clause label="Fin d'essai initiale" value={fmtDateFr(dateFinInitiale)} />
                                <Clause label="Nouvelle fin d'essai" value={fmtDateFr(nouvelleDateFin)} className="text-brand-500" />
                            </div>
                            {motif && (
                                <div className="pt-2">
                                    <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">Motif</p>
                                    <p className="text-ink-600 dark:text-ink-300 italic text-[12px]">{motif}</p>
                                </div>
                            )}
                            <DocumentFooter status="Brouillon (en attente de validation)" />
                        </DocumentFrame>
                    </div>
                </div>

                <div className="px-5 py-3 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-2 flex-shrink-0">
                    <Button type="button" onClick={onClose} variant="ghost" size="sm">
                        Annuler
                    </Button>
                    <Button type="button" onClick={handleSubmit} variant="warning" size="sm" loading={submitting} iconLeft={<RefreshCw size={16} />}>
                        Valider le Renouvellement
                    </Button>
                </div>
            </div>
        </div>
    );
}
