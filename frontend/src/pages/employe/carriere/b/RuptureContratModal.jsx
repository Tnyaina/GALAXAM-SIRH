import { useState, useEffect, useCallback } from 'react';
import { UserMinus, ShieldAlert, X, FileSignature, Eye, EyeOff, Calendar, AlertOctagon } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import referentielService from '@/api/admin/referentielService';
import {
    DocumentFrame, DocumentHeader, Clause, ArticleBlock, DocumentFooter
} from '@/components/carriere/ContractDocument';
import { fmtDateFr, fmtDateInput } from '@/utils/carriereUtils';

// ── COMPOSANT APERÇU LETTRE DE RUPTURE / NOTIFICATION OFFBOARDING ────────────
function RupturePreview({
    className = '',
    nomEmploye,
    contratActif,
    affectationActive,
    workflowActif,
    formData,
    libTypeRupture,
}) {
    const libFonction = affectationActive?.libelleFonction || workflowActif?.libelleFonction || '-';
    const libSite = affectationActive?.libelleSite || workflowActif?.libelleSite || '-';
    const libDept = affectationActive?.libelleDepartement || workflowActif?.libelleDepartement || '-';
    const libTypeContrat = contratActif?.libelleTypeContrat || workflowActif?.libelleTypeContrat || 'Contrat en cours';
    const numContrat = contratActif?.numeroContrat || workflowActif?.numeroContratCible || workflowActif?.numeroContratSource || 'CTR-ACTIF';

    return (
        <DocumentFrame className={className} watermark="Rupture">
            <DocumentHeader
                eyebrow={libTypeRupture || 'Fin de Contrat'}
                title="Notification de Fin de Contrat & Rupture"
                numero={numContrat}
            />

            <div className="pt-4 text-[13px] leading-relaxed space-y-3 font-normal">
                <p>
                    Le contrat de travail N° <strong>{numContrat}</strong> de <strong>{nomEmploye || '……………'}</strong> prend fin selon les termes ci-dessous.
                </p>

                <ArticleBlock n="1" title="Motif & Nature de la Rupture">
                    <Clause label="Type de rupture" value={libTypeRupture || 'Rupture du contrat'} className="text-danger-600 font-bold" />
                    <Clause label="Type de contrat" value={libTypeContrat} />
                    <Clause label="Date d'entrée initiale" value={fmtDateFr(contratActif?.dateDebut || workflowActif?.dateDebutContrat)} />
                    <Clause label="Date de notification" value={fmtDateFr(formData.dateNotification)} />
                    <Clause label="Prise d'effet (Sortie)" value={fmtDateFr(formData.dateEffet)} className="font-bold text-ink-900 dark:text-white" />
                </ArticleBlock>

                <ArticleBlock n="2" title="Préavis & Postes Occupés">
                    <Clause label="Dernière fonction" value={libFonction} />
                    <Clause label="Site / Département" value={`${libSite} / ${libDept}`} />
                    <Clause label="Préavis applicable" value={formData.dureePreavis || 'Conforme à la convention'} />
                    <Clause label="Exécution du préavis" value={formData.preavisEffectue ? 'Préavis effectué par le salarié' : 'Dispense de préavis (Payé)'} />
                </ArticleBlock>

                {formData.motif && (
                    <div className="pt-2 border-t border-ink-150 dark:border-ink-800">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-danger-500">Motif de la rupture / Remarques</p>
                        <p className="text-ink-600 dark:text-ink-300 text-[12.5px] mt-0.5">{formData.motif}</p>
                    </div>
                )}
            </div>

            <DocumentFooter status="Brouillon (en attente de validation RH)" />
        </DocumentFrame>
    );
}

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
export default function RuptureContratModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    workflowActif,
    initialData,
    onSuccess,
}) {
    const [mounted, setMounted] = useState(false);
    const [loadingRef, setLoadingRef] = useState(true);
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const [typesEvenement, setTypesEvenement] = useState([]);

    const [formData, setFormData] = useState({
        codeTypeEvenement: 'DEMISSION',
        dateNotification: fmtDateInput(new Date()),
        dateEffet: fmtDateInput(new Date()),
        dureePreavis: '1 Mois',
        preavisEffectue: true,
        motif: 'Démission du salarié pour convenances personnelles',
        commentaire: '',
    });

    const loadReferentiels = useCallback(async () => {
        setLoadingRef(true);
        try {
            const tevRes = await referentielService.getTypesEvenementRh();
            const ruptures = (tevRes || []).filter(t => 
                ['DEMISSION', 'LICENCIEMENT', 'RUPTURE_CONVENTIONNELLE', 'FIN_CONTRAT', 'RUPTURE_ESSAI', 'DEPART_RETRAITE'].includes(t.code)
            );
            setTypesEvenement(ruptures.length > 0 ? ruptures : [
                { code: 'DEMISSION', libelle: 'Démission du salarié' },
                { code: 'LICENCIEMENT', libelle: 'Licenciement' },
                { code: 'RUPTURE_CONVENTIONNELLE', libelle: 'Rupture conventionnelle' },
                { code: 'FIN_CONTRAT', libelle: 'Fin de CDD / Terme du contrat' },
                { code: 'RUPTURE_ESSAI', libelle: 'Rupture de la période d\'essai' },
                { code: 'DEPART_RETRAITE', libelle: 'Départ à la retraite' },
            ]);
        } catch {
            setTypesEvenement([
                { code: 'DEMISSION', libelle: 'Démission du salarié' },
                { code: 'LICENCIEMENT', libelle: 'Licenciement' },
                { code: 'RUPTURE_CONVENTIONNELLE', libelle: 'Rupture conventionnelle' },
                { code: 'FIN_CONTRAT', libelle: 'Fin de CDD / Terme du contrat' },
                { code: 'RUPTURE_ESSAI', libelle: 'Rupture de la période d\'essai' },
                { code: 'DEPART_RETRAITE', libelle: 'Départ à la retraite' },
            ]);
        } finally {
            setLoadingRef(false);
        }
    }, []);

    useEffect(() => {
        if (isOpen) {
            loadReferentiels();
            const targetData = initialData || (workflowActif?.codeStatutWorkflow === 'BROUILLON' ? workflowActif : null);
            if (targetData) {
                setFormData({
                    codeTypeEvenement: targetData.codeTypeEvenement || 'DEMISSION',
                    dateNotification: fmtDateInput(targetData.dateDemande || targetData.dateNotification || new Date()),
                    dateEffet: fmtDateInput(targetData.dateEffet || new Date()),
                    dureePreavis: targetData.dureePreavis || '1 Mois',
                    preavisEffectue: targetData.preavisEffectue !== undefined ? targetData.preavisEffectue : true,
                    motif: targetData.motif ? targetData.motif.replace(/^\[.*?\]\s*/, '') : 'Démission du salarié pour convenances personnelles',
                    commentaire: targetData.commentaire || '',
                });
            }
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
        setShowPreviewMobile(false);
    }, [isOpen, loadReferentiels, initialData, workflowActif]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
    };

    const typeEvenementActuel = typesEvenement.find(t => t.code === formData.codeTypeEvenement);
    const libTypeRupture = typeEvenementActuel?.libelle || formData.codeTypeEvenement;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!formData.dateEffet) return setErrorMsg('La date de prise d\'effet de la rupture est obligatoire.');
        if (!contratActif?.idContrat) return setErrorMsg('Impossible de rompre : aucun contrat actif trouvé pour cet employé.');

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: formData.codeTypeEvenement,
                idContratSource: contratActif.idContrat,
                idAffectationSource: affectationActive?.idAffectation || null,
                dateEffet: formData.dateEffet,
                motif: `[${libTypeRupture}] ${formData.motif || ''}`.trim(),
                commentaireAffectation: formData.commentaire || null,
                descriptionContrat: `Préavis : ${formData.dureePreavis} (${formData.preavisEffectue ? 'Effectué' : 'Dispense payée'})`,
            };

            const res = await contratService.initierTransition(idEmploye, payload);
            if (onSuccess) onSuccess('Brouillon de rupture de contrat créé avec succès !', res);
            onClose();
        } catch (err) {
            setErrorMsg(err.response?.data?.message || err.message || 'Erreur lors de la création de la rupture.');
        } finally {
            setSubmitting(false);
        }
    };

    const previewProps = {
        nomEmploye,
        contratActif,
        affectationActive,
        workflowActif,
        formData,
        libTypeRupture,
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px]">
            <div className="absolute inset-0" onClick={onClose} />

            {/* Modal Centré Split-Screen 50 / 50 */}
            <div
                className={`
                    relative bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 rounded-lg shadow-2xl
                    w-full max-w-[94vw] h-[90vh] overflow-hidden flex flex-col
                    transform transition-all duration-300 ease-out
                    ${mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'}
                `}
            >
                {/* Header (fixe) */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-danger-500/10 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 font-bold text-lg text-danger-700 dark:text-danger-400">
                            <UserMinus size={22} />
                            <span>Rupture & Clôture de Contrat RH</span>
                            {nomEmploye && (
                                <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowPreviewMobile(v => !v)}
                                className="lg:hidden flex items-center gap-1.5 text-xs font-semibold text-danger-600 border border-danger-500/30 rounded-sm px-2.5 py-1.5"
                            >
                                {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                                {showPreviewMobile ? 'Masquer l\'aperçu' : 'Voir le document'}
                            </button>
                            <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                                <X size={20} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* Corps : Split Screen 50 / 50 */}
                <div className="flex-1 overflow-hidden flex flex-col lg:flex-row min-h-0">
                    
                    {/* COLONNE GAUCHE (50%) : Formulaire */}
                    <form onSubmit={handleSubmit} className="w-full lg:w-1/2 min-w-0 overflow-y-auto p-6 space-y-6 border-b lg:border-b-0 lg:border-r border-ink-150 dark:border-ink-700">
                        {errorMsg && (
                            <div className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-4 rounded-sm text-xs font-semibold flex items-center gap-2">
                                <ShieldAlert size={16} className="flex-shrink-0" />
                                <span>{errorMsg}</span>
                            </div>
                        )}

                        {loadingRef ? (
                            <div className="py-12 flex justify-center">
                                <Loading size="lg" label="Chargement des motifs de rupture..." />
                            </div>
                        ) : (
                            <>
                                {showPreviewMobile && <RupturePreview {...previewProps} className="lg:hidden mb-6" />}

                                {/* ÉTAPE 1 : CHOIX DU TYPE DE RUPTURE */}
                                <div className="space-y-3">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-danger-600 dark:text-danger-400 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <AlertOctagon size={14} /> 1. Nature & Motif de Sortie
                                    </h3>

                                    <Select
                                        id="codeTypeEvenement"
                                        name="codeTypeEvenement"
                                        label="Type de Rupture / Motif *"
                                        required
                                        value={formData.codeTypeEvenement}
                                        onChange={handleChange}
                                    >
                                        {typesEvenement.map(t => (
                                            <option key={t.code} value={t.code}>{t.libelle}</option>
                                        ))}
                                    </Select>
                                </div>

                                {/* ÉTAPE 2 : DATES & PRÉAVIS */}
                                <div className="space-y-4">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-danger-600 dark:text-danger-400 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <Calendar size={14} /> 2. Dates d'Effet & Gestion du Préavis
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Input
                                            type="date"
                                            id="dateNotification"
                                            name="dateNotification"
                                            label="Date de Notification / Signature"
                                            value={formData.dateNotification}
                                            onChange={handleChange}
                                        />

                                        <Input
                                            type="date"
                                            id="dateEffet"
                                            name="dateEffet"
                                            label="Date de Prise d'Effet (Fin de contrat) *"
                                            required
                                            value={formData.dateEffet}
                                            onChange={handleChange}
                                        />
                                    </div>

                                    <div className="bg-danger-500/5 p-4 rounded-sm border border-danger-500/20 space-y-3">
                                        <Select
                                            id="dureePreavis"
                                            name="dureePreavis"
                                            label="Durée du Préavis Applicable"
                                            value={formData.dureePreavis}
                                            onChange={handleChange}
                                        >
                                            <option value="Sans préavis">Sans préavis</option>
                                            <option value="15 Jours">15 Jours</option>
                                            <option value="1 Mois">1 Mois</option>
                                            <option value="2 Mois">2 Mois</option>
                                            <option value="3 Mois">3 Mois</option>
                                        </Select>

                                        <div className="flex items-center gap-2 pt-1">
                                            <input
                                                type="checkbox"
                                                id="preavisEffectue"
                                                name="preavisEffectue"
                                                checked={Boolean(formData.preavisEffectue)}
                                                onChange={handleChange}
                                                className="w-4 h-4 text-danger-600 rounded border-ink-300 focus:ring-danger-500 cursor-pointer"
                                            />
                                            <label htmlFor="preavisEffectue" className="text-xs font-bold text-ink-900 dark:text-white cursor-pointer">
                                                Le préavis est exécuté/effectué par le salarié
                                            </label>
                                        </div>
                                    </div>
                                </div>

                                {/* ÉTAPE 3 : JUSTIFICATION RH */}
                                <div className="space-y-3">
                                    <Textarea
                                        id="motif"
                                        name="motif"
                                        label="Motif Détaillé de la Rupture *"
                                        required
                                        rows={3}
                                        value={formData.motif}
                                        onChange={handleChange}
                                        placeholder="Ex: Démission déposée le 10/08 par lettre remise en main propre..."
                                    />

                                    <Textarea
                                        id="commentaire"
                                        name="commentaire"
                                        label="Remarques et Consignes d'Offboarding (Optionnel)"
                                        rows={2}
                                        value={formData.commentaire}
                                        onChange={handleChange}
                                        placeholder="Ex: Restitution du matériel informatique et badge au dernier jour..."
                                    />
                                </div>
                            </>
                        )}
                    </form>

                    {/* COLONNE DROITE (50%) : Aperçu du document de sortie */}
                    <div className="hidden lg:block w-1/2 flex-shrink-0 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-6 flex flex-col items-center">
                        <div className="w-full max-w-[560px]">
                            <div className="flex items-center gap-2 mb-4 text-xs font-bold uppercase tracking-wider text-ink-400">
                                <FileSignature size={14} /> Aperçu du document de rupture
                            </div>
                            <RupturePreview {...previewProps} />
                        </div>
                    </div>
                </div>

                {/* Footer Actions (fixe) */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex items-center justify-between gap-3">
                    <p className="text-[11px] text-ink-400">
                        Cette rupture sera enregistrée en <span className="font-bold text-danger-600">BROUILLON</span> pour validation DAF / Direction.
                    </p>
                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="outline" size="md">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="danger"
                            size="md"
                            loading={submitting}
                            iconLeft={<UserMinus size={16} />}
                        >
                            Enregistrer le Brouillon de Rupture
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
