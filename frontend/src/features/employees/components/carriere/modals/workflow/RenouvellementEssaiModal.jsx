import { useState, useEffect } from 'react';
import { RefreshCw, ShieldAlert, X, FileSignature, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import contratService from '@/features/employees/api/contratService';
import {
    DocumentFrame, DocumentHeader, Clause, DocumentFooter
} from '@/features/employees/components/carriere/ContractDocument';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';

// ── COMPOSANT APERÇU AVENANT (Déclaré hors du render) ──────────────────────
function AvenantPreview({
    className = '',
    nomEmploye,
    dureeProlongationMois,
    dateFinInitiale,
    dateEffet,
    nouvelleDateFin,
    motif,
}) {
    return (
        <DocumentFrame watermark="Avenant" className={className}>
            <DocumentHeader
                eyebrow="Renouvellement d'Essai"
                title="Avenant au Contrat de Travail"
            />

            <div className="pt-3 text-[13px] leading-relaxed space-y-3">
                <p>
                    La période d'essai de <strong>{nomEmploye || '……………'}</strong> est renouvelée et prolongée d'une durée unique.
                </p>

                <div className="space-y-1 pt-1">
                    <Clause
                        label="Prolongation accordée"
                        value={dureeProlongationMois ? `+ ${dureeProlongationMois} mois` : ''}
                    />
                    <Clause
                        label="Fin d'essai initiale"
                        value={fmtDateFr(dateFinInitiale)}
                    />
                    <Clause
                        label="Date de prise d'effet"
                        value={fmtDateFr(dateEffet)}
                    />
                    <Clause
                        label="Nouvelle fin d'essai"
                        value={fmtDateFr(nouvelleDateFin)}
                        className="text-brand-500 font-bold"
                    />
                </div>

                {motif && (
                    <div className="pt-2 border-t border-ink-150 dark:border-ink-800">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">
                            Motif du renouvellement
                        </p>
                        <p className="text-ink-600 dark:text-ink-300 text-[12px] mt-0.5">
                            {motif}
                        </p>
                    </div>
                )}
            </div>

            <DocumentFooter status="Brouillon (en attente de validation)" />
        </DocumentFrame>
    );
}

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
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
    const _wfId = initialData?.idWorkflow || initialData?.workflow?.idWorkflow;
    const isActionAllowed = isEditMode || etatEssai?.canRenew !== false;

    const [dureeProlongationMois, setDureeProlongationMois] = useState('2');
    const [dateFinInitiale, setDateFinInitiale] = useState('');
    const [dateEffet, setDateEffet] = useState('');
    const [nouvelleDateFin, setNouvelleDateFin] = useState('');
    const [motif, setMotif] = useState('Prolongation de la période d\'évaluation du collaborateur');
    const [commentaire, setCommentaire] = useState('');
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [mounted, setMounted] = useState(false);

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

            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
        setShowPreviewMobile(false);
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
                dateFinEssai: nouvelleDateFin ? nouvelleDateFin : null, // Securite chaine vide -> null
                idSite: affectationActive?.idSite || initialData?.idSite || null,
                idDepartement: affectationActive?.idDepartement || initialData?.idDepartement || null,
                idService: affectationActive?.idService || initialData?.idService || null,
                idFonction: affectationActive?.idFonction || initialData?.idFonction || null,
                idResponsableHierarchique: affectationActive?.idResponsableHierarchique || initialData?.idResponsableHierarchique || null,
                idCategorieProfessionnelle: affectationActive?.idCategorieProfessionnelle || initialData?.idCategorieProfessionnelle || null,
                idModeCalculPaie: affectationActive?.idModeCalculPaie || initialData?.idModeCalculPaie || null,
                salaireBase: affectationActive?.salaireBase || initialData?.salaireBase || null,
                dateEffet: dateEffet ? dateEffet : null, // Securite chaine vide -> null
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

    const previewProps = {
        nomEmploye,
        dureeProlongationMois,
        dateFinInitiale,
        dateEffet,
        nouvelleDateFin,
        motif,
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
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-warning-500/10 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 text-warning-600 dark:text-warning-400 font-bold text-lg">
                            <RefreshCw size={20} />
                            <span>Renouvellement de la Période d'Essai</span>
                            {nomEmploye && (
                                <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowPreviewMobile(v => !v)}
                                className="lg:hidden flex items-center gap-1.5 text-xs font-semibold text-warning-600 border border-warning-500/30 rounded-none px-2.5 py-1.5"
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
                            <AvenantPreview {...previewProps} className="lg:hidden mb-6" />
                        )}

                        <p className="text-xs text-ink-600 dark:text-ink-300">
                            Renouvellement (prolongation unique) de la période d'essai de <span className="font-bold text-ink-900 dark:text-white">{nomEmploye}</span>.
                        </p>

                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                            <Select
                                id="dureeProlongationMois"
                                name="dureeProlongationMois"
                                label="Prolongation *"
                                value={dureeProlongationMois}
                                onChange={e => setDureeProlongationMois(e.target.value)}
                            >
                                <option value="1">+ 1 Mois</option>
                                <option value="2">+ 2 Mois</option>
                                <option value="3">+ 3 Mois</option>
                            </Select>

                            <Input
                                type="date"
                                id="dateEffet"
                                name="dateEffet"
                                label="Prise d'Effet *"
                                value={dateEffet}
                                onChange={e => setDateEffet(e.target.value)}
                                required
                            />

                            <Input
                                type="date"
                                id="nouvelleDateFin"
                                name="nouvelleDateFin"
                                label="Nouvelle Fin d'Essai *"
                                value={nouvelleDateFin}
                                onChange={e => setNouvelleDateFin(e.target.value)}
                                required
                            />
                        </div>

                        <Input
                            type="text"
                            id="motifRenouvellement"
                            name="motif"
                            label="Motif du Renouvellement *"
                            value={motif}
                            onChange={e => setMotif(e.target.value)}
                            required
                        />

                        <Textarea
                            id="commentaireRenouvellement"
                            name="commentaire"
                            label="Commentaire / Justification RH"
                            value={commentaire}
                            onChange={e => setCommentaire(e.target.value)}
                            rows={3}
                            placeholder="Précisions sur les compétences à valider durant la prolongation..."
                        />
                    </form>

                    {/* COLONNE DROITE (50%) : Aperçu Avenant */}
                    <div className="hidden lg:block w-1/2 flex-shrink-0 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-4 sm:p-5 flex flex-col items-center">
                        <div className="w-full max-w-[560px]">
                            <div className="flex items-center gap-2 mb-4 text-xs font-bold uppercase tracking-wider text-ink-400">
                                <FileSignature size={14} /> Aperçu de l'avenant en temps réel
                            </div>
                            <AvenantPreview {...previewProps} />
                        </div>
                    </div>
                </div>

                {/* Footer Actions (fixe) */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex items-center justify-between gap-3">
                    <p className="text-[11px] text-ink-400">
                        Cette décision sera enregistrée en <span className="font-bold text-warning-600">BROUILLON</span> pour validation.
                    </p>
                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="outline" size="md">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="success"
                            size="md"
                            loading={submitting}
                            disabled={!isActionAllowed || submitting}
                            iconLeft={<RefreshCw size={16} />}
                        >
                            Valider le Renouvellement
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
