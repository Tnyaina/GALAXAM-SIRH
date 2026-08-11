import { useState, useEffect, useRef } from 'react';
import { RefreshCw, Building, Landmark, ShieldAlert, Save, X, ArrowRight, UserCheck, Clock, Eye, EyeOff, FileSignature } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import {
    DocumentFrame, DocumentHeader, Clause, ClauseDiff, ArticleBlock, DocumentFooter
} from '@/components/carriere/ContractDocument';
import { fmtDateFr, fmtMoney } from '@/utils/carriereUtils';

function fmtDateInput(d) {
    if (!d) return '';
    if (typeof d === 'string') return d.substring(0, 10);
    return new Date(d).toISOString().substring(0, 10);
}

// ── COMPOSANT APERÇU AVENANT (Déclaré hors du render pour préserver le state) ──
function AvenantPreview({
    className = '',
    typeEvenementActuel,
    nomEmploye,
    formData,
    exigeNouveauContrat,
    avantTypeContrat,
    apresTypeContrat,
    avantSite,
    apresSite,
    avantDepartement,
    apresDepartement,
    avantService,
    apresService,
    avantFonction,
    apresFonction,
    avantCategorie,
    apresCategorie,
    avantResponsable,
    apresResponsable,
    avantModePaie,
    apresModePaie,
    avantSalaire,
}) {
    return (
        <DocumentFrame className={className} watermark="Avenant">
            <DocumentHeader
                eyebrow={typeEvenementActuel?.libelle || 'Mouvement RH'}
                title="Avenant au Contrat de Travail"
            />

            <div className="pt-4 text-[13px] leading-relaxed space-y-3 font-normal">
                <p>
                    Le présent avenant modifie les conditions applicables à <strong>{nomEmploye || '……………'}</strong>, à effet du{' '}
                    <strong>{fmtDateFr(formData.dateEffet) || '……………'}</strong>.
                </p>

                {exigeNouveauContrat && (
                    <ArticleBlock n="1" title="Nouveau Contrat">
                        <ClauseDiff label="Type de contrat" before={avantTypeContrat} after={apresTypeContrat} />
                        <Clause label="Prise d'effet" value={fmtDateFr(formData.dateDebut || formData.dateEffet)} />
                        <Clause label="Terme" value={fmtDateFr(formData.dateFin)} placeholder="Durée indéterminée" />
                        {formData.aPeriodeEssai && (
                            <Clause label="Période d'essai" value={formData.dateFinEssai ? `Jusqu'au ${fmtDateFr(formData.dateFinEssai)}` : ''} />
                        )}
                    </ArticleBlock>
                )}

                <ArticleBlock n={exigeNouveauContrat ? '2' : '1'} title="Affectation">
                    <ClauseDiff label="Site" before={avantSite} after={apresSite} />
                    <ClauseDiff label="Département" before={avantDepartement} after={apresDepartement} />
                    <ClauseDiff label="Service" before={avantService} after={apresService} />
                    <ClauseDiff label="Fonction" before={avantFonction} after={apresFonction} />
                    <ClauseDiff label="Catégorie pro." before={avantCategorie} after={apresCategorie} />
                    <ClauseDiff label="Rattaché à" before={avantResponsable} after={apresResponsable ? `${apresResponsable.nom} ${apresResponsable.prenom}` : ''} />
                </ArticleBlock>

                <ArticleBlock n={exigeNouveauContrat ? '3' : '2'} title="Rémunération">
                    <ClauseDiff label="Mode de calcul" before={avantModePaie} after={apresModePaie} />
                    <ClauseDiff
                        label="Salaire de base"
                        before={avantSalaire ? fmtMoney(avantSalaire) : ''}
                        after={formData.salaireBase ? fmtMoney(formData.salaireBase) : ''}
                    />
                </ArticleBlock>

                {formData.motif && (
                    <div className="pt-2 border-t border-ink-150 dark:border-ink-800">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">Motif</p>
                        <p className="text-ink-600 dark:text-ink-300 text-[12.5px] mt-0.5">{formData.motif}</p>
                    </div>
                )}
            </div>

            <DocumentFooter status="Brouillon (en attente de validation)" />
        </DocumentFrame>
    );
}

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
export default function TransitionModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    workflowActif,
    initialData = null,
    onSuccess,
}) {
    const [mounted, setMounted] = useState(false);
    const errorAlertRef = useRef(null);

    // Référentiels via Custom Hook avec cache & lazy loading
    const {
        sites,
        departements,
        services,
        fonctions,
        typesContrat,
        categoriesPro,
        modesPaie,
        responsables,
        typesIntervenant,
        typesEvenementRh,
        loading: loadingRef,
        error: refError,
    } = useContractReferentiels(isOpen, idEmploye);

    // Type de transition sélectionné
    const [codeTypeEvenement, setCodeTypeEvenement] = useState('MUTATION');

    // Formulaire de transition
    const [formData, setFormData] = useState({
        idFiliale: '',
        idTypeContrat: '',
        idTypeIntervenant: '',
        dateDebut: '',
        dateFin: '',
        dateSignature: '',
        comptabiliseEffectif: true,
        numeroContrat: '',
        descriptionContrat: '',
        commentaireContrat: '',
        aPeriodeEssai: false,
        dureeEssaiMois: '3',
        dateFinEssai: '',

        idSite: '',
        idDepartement: '',
        idService: '',
        idFonction: '',
        idResponsableHierarchique: '',
        idCategorieProfessionnelle: '',
        idModeCalculPaie: '',
        salaireBase: '',
        descriptionAffectation: '',
        commentaireAffectation: '',

        dateEffet: fmtDateInput(new Date()),
        motif: '',
    });

    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);

    // Filtrer les événements de type transition
    const typesEvenement = (typesEvenementRh || []).filter(t =>
        !['EMBAUCHE_INITIALE', 'FIN_CONTRAT', 'DEPART_RETRAITE', 'RUPTURE_ESSAI', 'RENOUVELLEMENT_ESSAI', 'CONFIRMATION_ESSAI'].includes(t.code)
    );


    useEffect(() => {
        if (isOpen) {
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
        setShowPreviewMobile(false);
    }, [isOpen]);

    // Scroll automatique vers l'erreur lorsqu'elle survient
    useEffect(() => {
        if (errorMsg && errorAlertRef.current) {
            errorAlertRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }, [errorMsg]);

    useEffect(() => {
        if (!isOpen) return;

        const targetData = initialData || (workflowActif?.codeStatutWorkflow === 'BROUILLON' ? workflowActif : null);

        if (targetData?.codeTypeEvenement) {
            setCodeTypeEvenement(targetData.codeTypeEvenement);
        }

        let filialeId = targetData?.idFiliale || targetData?.id_filiale || contratActif?.idFiliale || workflowActif?.idFiliale || '';
        let typeContratId = targetData?.idTypeContrat || targetData?.id_type_contrat || contratActif?.idTypeContrat || workflowActif?.idTypeContrat || '';
        let typeIntervenantId = targetData?.idTypeIntervenant || targetData?.id_type_intervenant || contratActif?.idTypeIntervenant || workflowActif?.idTypeIntervenant || '';
        let siteId = targetData?.idSite || targetData?.id_site || affectationActive?.idSite || workflowActif?.idSite || '';
        let departementId = targetData?.idDepartement || targetData?.id_departement || affectationActive?.idDepartement || workflowActif?.idDepartement || '';
        let serviceId = targetData?.idService || targetData?.id_service || affectationActive?.idService || workflowActif?.idService || '';
        let fonctionId = targetData?.idFonction || targetData?.id_fonction || affectationActive?.idFonction || workflowActif?.idFonction || '';
        let respId = targetData?.idResponsableHierarchique || targetData?.id_responsable_hierarchique || affectationActive?.idResponsableHierarchique || workflowActif?.idResponsableHierarchique || '';
        let catProId = targetData?.idCategorieProfessionnelle || targetData?.id_categorie_professionnelle || affectationActive?.idCategorieProfessionnelle || workflowActif?.idCategorieProfessionnelle || '';
        let modePaieId = targetData?.idModeCalculPaie || targetData?.id_mode_calcul_paie || affectationActive?.idModeCalculPaie || workflowActif?.idModeCalculPaie || '';

        let salaire = targetData?.salaireBase !== undefined && targetData?.salaireBase !== null && targetData?.salaireBase !== ''
            ? targetData.salaireBase
            : (affectationActive?.salaireBase !== undefined ? affectationActive.salaireBase : (workflowActif?.salaireBase || ''));

        let dateEffetVal = targetData?.dateEffet ? fmtDateInput(targetData.dateEffet) : fmtDateInput(new Date());
        let motifVal = targetData?.motif || '';

        setFormData(prev => ({
            ...prev,
            idFiliale: filialeId ? String(filialeId) : prev.idFiliale,
            idTypeContrat: typeContratId ? String(typeContratId) : prev.idTypeContrat,
            idTypeIntervenant: typeIntervenantId ? String(typeIntervenantId) : prev.idTypeIntervenant,
            idSite: siteId ? String(siteId) : prev.idSite,
            idDepartement: departementId ? String(departementId) : prev.idDepartement,
            idService: serviceId ? String(serviceId) : prev.idService,
            idFonction: fonctionId ? String(fonctionId) : prev.idFonction,
            idResponsableHierarchique: respId ? String(respId) : prev.idResponsableHierarchique,
            idCategorieProfessionnelle: catProId ? String(catProId) : prev.idCategorieProfessionnelle,
            idModeCalculPaie: modePaieId ? String(modePaieId) : prev.idModeCalculPaie,
            salaireBase: salaire !== '' ? String(salaire) : prev.salaireBase,
            dateEffet: dateEffetVal,
            motif: motifVal,
            comptabiliseEffectif: targetData?.comptabiliseEffectif !== undefined ? Boolean(targetData.comptabiliseEffectif) : (contratActif?.comptabiliseEffectif !== undefined ? Boolean(contratActif.comptabiliseEffectif) : true),
        }));
    }, [isOpen, contratActif, affectationActive, workflowActif, initialData]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setErrorMsg(''); // Effacer l'erreur lors de la saisie
        setFormData(prev => {
            const next = { ...prev, [name]: type === 'checkbox' ? checked : value };
            if (next.aPeriodeEssai && (name === 'aPeriodeEssai' || name === 'dureeEssaiMois' || name === 'dateDebut' || name === 'dateEffet')) {
                const dateDeb = next.dateDebut || next.dateEffet;
                const duree = Number(next.dureeEssaiMois || 3);
                if (dateDeb && !isNaN(duree)) {
                    const d = new Date(dateDeb);
                    if (!isNaN(d.getTime())) {
                        d.setMonth(d.getMonth() + duree);
                        next.dateFinEssai = d.toISOString().substring(0, 10);
                    }
                }
            }
            return next;
        });
    };

    const exigeNouveauContrat = ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT'].includes(codeTypeEvenement);

    const apresSite = sites.find(s => String(s.idSite) === String(formData.idSite))?.libelle;
    const apresDepartement = departements.find(d => String(d.idDepartement) === String(formData.idDepartement))?.libelle;
    const apresService = services.find(s => String(s.idService) === String(formData.idService))?.libelle;
    const apresFonction = fonctions.find(f => String(f.idFonction) === String(formData.idFonction))?.libelle;
    const apresCategorie = categoriesPro.find(c => String(c.idCategorieProfessionnelle) === String(formData.idCategorieProfessionnelle))?.libelle;
    const apresModePaie = modesPaie.find(m => String(m.idModeCalculPaie) === String(formData.idModeCalculPaie))?.libelle;
    const apresResponsable = responsables.find(r => String(r.idEmploye) === String(formData.idResponsableHierarchique));
    const apresTypeContrat = typesContrat.find(t => String(t.idTypeContrat) === String(formData.idTypeContrat))?.libelle;

    const avantSite = affectationActive?.libelleSite || workflowActif?.libelleSite || '';
    const avantDepartement = affectationActive?.libelleDepartement || workflowActif?.libelleDepartement || '';
    const avantService = affectationActive?.libelleService || workflowActif?.libelleService || '';
    const avantFonction = affectationActive?.libelleFonction || workflowActif?.libelleFonction || '';
    const avantCategorie = affectationActive?.libelleCategoriePro || workflowActif?.libelleCategoriePro || '';
    const avantModePaie = affectationActive?.libelleModeCalculPaie || workflowActif?.libelleModeCalculPaie || '';
    const avantResponsable = affectationActive?.nomPrenomResponsable || workflowActif?.nomPrenomResponsable || '';
    const avantSalaire = affectationActive?.salaireBase ?? workflowActif?.salaireBase ?? '';
    const avantTypeContrat = contratActif?.libelleTypeContrat || workflowActif?.libelleTypeContrat || '';

    const typeEvenementActuel = (typesEvenement.length > 0 ? typesEvenement : []).find(t => t.code === codeTypeEvenement);

    // ── AMÉLIORATION UX : Calcul dynamique des champs obligatoires réels ──
    const champsObligatoires = [
        formData.dateEffet, 
        formData.idSite, 
        formData.idDepartement, 
        formData.idService, 
        formData.idFonction, 
        formData.idCategorieProfessionnelle, 
        formData.idModeCalculPaie, 
        formData.salaireBase,
        ...(exigeNouveauContrat ? [formData.idTypeContrat, formData.idTypeIntervenant] : [])
    ];
    const nbRemplis = champsObligatoires.filter(v => v !== '' && v !== null && v !== undefined).length;
    const progressPct = Math.round((nbRemplis / champsObligatoires.length) * 100);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!formData.dateEffet) {
            setErrorMsg('La date d\'effet du mouvement est obligatoire.');
            return;
        }

        if (!formData.idSite || !formData.idDepartement || !formData.idService || !formData.idFonction || !formData.idCategorieProfessionnelle || !formData.idModeCalculPaie || !formData.salaireBase) {
            setErrorMsg('Veuillez remplir tous les champs obligatoires de la nouvelle affectation.');
            return;
        }

        // ── AMÉLIORATION BUG : Validation front des champs du nouveau contrat ──
        if (exigeNouveauContrat && (!formData.idTypeContrat || !formData.idTypeIntervenant)) {
            setErrorMsg('Veuillez sélectionner le nouveau type de contrat et le profil d\'intervenant.');
            return;
        }

        setSubmitting(true);

        try {
            const payload = {
                codeTypeEvenement,
                idContratSource: contratActif?.idContrat || null,
                idAffectationSource: affectationActive?.idAffectation || null,

                idFiliale: exigeNouveauContrat ? Number(formData.idFiliale || contratActif?.idFiliale) : null,
                idTypeContrat: exigeNouveauContrat ? Number(formData.idTypeContrat) : null,
                idTypeIntervenant: exigeNouveauContrat ? Number(formData.idTypeIntervenant || contratActif?.idTypeIntervenant) : null,
                dateDebut: exigeNouveauContrat ? (formData.dateDebut || formData.dateEffet) : null,
                dateFin: exigeNouveauContrat ? (formData.dateFin || null) : null,
                dateSignature: exigeNouveauContrat ? (formData.dateSignature || null) : null,
                dureeEssaiMois: exigeNouveauContrat && formData.aPeriodeEssai && formData.dureeEssaiMois ? Number(formData.dureeEssaiMois) : null,
                dateFinEssai: exigeNouveauContrat && formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
                comptabiliseEffectif: exigeNouveauContrat ? Boolean(formData.comptabiliseEffectif) : null,
                numeroContrat: exigeNouveauContrat ? (formData.numeroContrat || null) : null,
                descriptionContrat: exigeNouveauContrat ? (formData.descriptionContrat || null) : null,
                commentaireContrat: exigeNouveauContrat ? (formData.commentaireContrat || null) : null,

                idSite: Number(formData.idSite),
                idDepartement: Number(formData.idDepartement),
                idService: Number(formData.idService),
                idFonction: Number(formData.idFonction),
                idResponsableHierarchique: formData.idResponsableHierarchique ? Number(formData.idResponsableHierarchique) : null,
                idCategorieProfessionnelle: Number(formData.idCategorieProfessionnelle),
                idModeCalculPaie: Number(formData.idModeCalculPaie),
                salaireBase: Number(formData.salaireBase),
                descriptionAffectation: formData.descriptionAffectation || null,
                commentaireAffectation: formData.commentaireAffectation || null,

                dateEffet: formData.dateEffet,
                motif: formData.motif || `Mouvement RH : ${codeTypeEvenement}`,
            };

            const res = await contratService.creerBrouillonTransition(idEmploye, payload);
            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error('Erreur création transition:', err);
            const errData = err.response?.data;
            const msg = typeof errData === 'string' ? errData : (errData?.message || 'Erreur lors de la création de la demande de transition.');
            setErrorMsg(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const previewProps = {
        typeEvenementActuel,
        nomEmploye,
        formData,
        exigeNouveauContrat,
        avantTypeContrat,
        apresTypeContrat,
        avantSite,
        apresSite,
        avantDepartement,
        apresDepartement,
        avantService,
        apresService,
        avantFonction,
        apresFonction,
        avantCategorie,
        apresCategorie,
        avantResponsable,
        apresResponsable,
        avantModePaie,
        apresModePaie,
        avantSalaire,
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px]">
            {/* Overlay cliquable */}
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
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/40 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 font-bold text-lg text-ink-900 dark:text-white">
                            <RefreshCw className="text-brand-500" size={20} />
                            <span>Mouvement de Carrière & Transition RH</span>
                            {nomEmploye && (
                                <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowPreviewMobile(v => !v)}
                                className="lg:hidden flex items-center gap-1.5 text-xs font-semibold text-brand-500 border border-brand-500/30 rounded-sm px-2.5 py-1.5"
                            >
                                {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                                {showPreviewMobile ? 'Masquer l\'avenant' : 'Voir l\'avenant'}
                            </button>
                            <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                                <X size={20} />
                            </button>
                        </div>
                    </div>

                    {!loadingRef && (
                        <div className="mt-3 flex items-center gap-3">
                            <div className="flex-1 h-1.5 bg-ink-100 dark:bg-ink-800 rounded-full overflow-hidden">
                                <div
                                    className={`h-full rounded-full transition-all duration-300 ${progressPct === 100 ? 'bg-success-500' : 'bg-brand-500'}`}
                                    style={{ width: `${progressPct}%` }}
                                />
                            </div>
                            <span className="text-[11px] font-semibold text-ink-400 whitespace-nowrap">
                                {nbRemplis}/{champsObligatoires.length} champs obligatoires
                            </span>
                        </div>
                    )}
                </div>

                {/* Corps : Split Screen 50 / 50 */}
                <div className="flex-1 overflow-hidden flex flex-col lg:flex-row min-h-0">
                    
                    {/* COLONNE GAUCHE (50%) : Formulaire */}
                    <form onSubmit={handleSubmit} className="w-full lg:w-1/2 min-w-0 overflow-y-auto p-6 space-y-6 border-b lg:border-b-0 lg:border-r border-ink-150 dark:border-ink-700">
                        {errorMsg && (
                            <div ref={errorAlertRef} className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-4 rounded-sm text-xs font-semibold flex items-center gap-2">
                                <ShieldAlert size={16} className="flex-shrink-0" />
                                <span>{errorMsg}</span>
                            </div>
                        )}

                        {loadingRef ? (
                            <div className="py-12 flex justify-center">
                                <Loading size="lg" label="Chargement des référentiels..." />
                            </div>
                        ) : (
                            <>
                                {showPreviewMobile && <AvenantPreview {...previewProps} className="lg:hidden mb-6" />}

                                {/* ÉTAPE 1 : CHOIX DU TYPE DE TRANSITION */}
                                <div className="space-y-3">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <UserCheck size={14} /> 1. Sélection du Mouvement RH
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                                        {(typesEvenement.length > 0 ? typesEvenement : [
                                            { code: 'MUTATION', libelle: 'Mutation (Site / Dépt / Srv)' },
                                            { code: 'PROMOTION', libelle: 'Promotion / Changement Poste' },
                                            { code: 'REVALORISATION', libelle: 'Revalorisation Salariale' },
                                            { code: 'TRANSFORMATION_CDI', libelle: 'Transformation CDD → CDI' },
                                            { code: 'RENOUVELLEMENT_CDD', libelle: 'Renouvellement de CDD' },
                                        ]).map((t) => {
                                            const descMap = {
                                                MUTATION: 'Garde le contrat actif, modifie le site/service',
                                                PROMOTION: 'Changement de fonction ou classification',
                                                REVALORISATION: 'Ajustement du salaire de base',
                                                TRANSFORMATION_CDI: 'Clôture l\'ancien CDD et active un nouveau CDI',
                                                RENOUVELLEMENT_CDD: 'Avenant de prolongation du CDD',
                                                EMBAUCHE_APRES_STAGE: 'Transformation de stage vers CDD/CDI',
                                            };
                                            const displayDesc = t.description || descMap[t.code] || 'Mouvement de carrière RH';
                                            const isSelected = codeTypeEvenement === t.code;

                                            return (
                                                /* ── AMÉLIORATION ACCESSIBILITÉ CLAVIER ── */
                                                <div
                                                    key={t.code}
                                                    role="button"
                                                    tabIndex={0}
                                                    aria-checked={isSelected}
                                                    onClick={() => setCodeTypeEvenement(t.code)}
                                                    onKeyDown={(e) => {
                                                        if (e.key === 'Enter' || e.key === ' ') {
                                                            e.preventDefault();
                                                            setCodeTypeEvenement(t.code);
                                                        }
                                                    }}
                                                    className={`
                                                        p-3 border rounded-sm cursor-pointer transition-all flex flex-col justify-between focus:outline-none focus:ring-2 focus:ring-brand-500
                                                        ${isSelected
                                                            ? 'border-brand-500 bg-brand-500/10 shadow-sm'
                                                            : 'border-ink-200 dark:border-ink-800 hover:border-ink-400 bg-white dark:bg-ink-950/30'}
                                                    `}
                                                >
                                                    <div>
                                                        <p className="font-bold text-xs text-ink-900 dark:text-white flex items-center justify-between">
                                                            {t.libelle}
                                                            {isSelected && <ArrowRight size={14} className="text-brand-500" />}
                                                        </p>
                                                        <p className="text-[11px] text-ink-400 mt-1">{displayDesc}</p>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>

                                {/* ÉTAPE 2 : NOUVELLE AFFECTATION & PAIE */}
                                <div className="space-y-4">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <Landmark size={14} /> 2. Nouvelle Affectation & Rémunération
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            id="idSite"
                                            name="idSite"
                                            label="Nouveau Site *"
                                            required
                                            value={formData.idSite}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le Site --</option>
                                            {sites.map(s => <option key={s.idSite} value={s.idSite}>{s.libelle}</option>)}
                                        </Select>

                                        <Select
                                            id="idDepartement"
                                            name="idDepartement"
                                            label="Nouveau Département *"
                                            required
                                            value={formData.idDepartement}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le Département --</option>
                                            {departements.map(d => <option key={d.idDepartement} value={d.idDepartement}>{d.libelle}</option>)}
                                        </Select>

                                        <Select
                                            id="idService"
                                            name="idService"
                                            label="Nouveau Service *"
                                            required
                                            value={formData.idService}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le Service --</option>
                                            {services.map(s => <option key={s.idService} value={s.idService}>{s.libelle}</option>)}
                                        </Select>

                                        <Select
                                            id="idFonction"
                                            name="idFonction"
                                            label="Nouvelle Fonction / Poste *"
                                            required
                                            value={formData.idFonction}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner la Fonction --</option>
                                            {fonctions.map(f => <option key={f.idFonction} value={f.idFonction}>{f.libelle}</option>)}
                                        </Select>

                                        <Select
                                            id="idCategorieProfessionnelle"
                                            name="idCategorieProfessionnelle"
                                            label="Catégorie Professionnelle *"
                                            required
                                            value={formData.idCategorieProfessionnelle}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner la Catégorie --</option>
                                            {categoriesPro.map(c => <option key={c.idCategorieProfessionnelle} value={c.idCategorieProfessionnelle}>{c.libelle}</option>)}
                                        </Select>

                                        <Select
                                            id="idResponsableHierarchique"
                                            name="idResponsableHierarchique"
                                            label="Nouveau Supérieur N+1"
                                            value={formData.idResponsableHierarchique}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Aucun / Inchangé --</option>
                                            {responsables.map(r => (
                                                <option key={r.idEmploye} value={r.idEmploye}>
                                                    {r.matricule} - {r.nom} {r.prenom}
                                                </option>
                                            ))}
                                        </Select>
                                    </div>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-ink-150 dark:border-ink-800 pt-4">
                                        <Select
                                            id="idModeCalculPaie"
                                            name="idModeCalculPaie"
                                            label="Mode de Calcul de Paie *"
                                            required
                                            value={formData.idModeCalculPaie}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le Mode --</option>
                                            {modesPaie.map(m => <option key={m.idModeCalculPaie} value={m.idModeCalculPaie}>{m.libelle}</option>)}
                                        </Select>

                                        <Input
                                            id="salaireBase"
                                            name="salaireBase"
                                            label="Nouveau Salaire de Base (MGA) *"
                                            type="number"
                                            step="0.01"
                                            required
                                            value={formData.salaireBase}
                                            onChange={handleChange}
                                        />
                                    </div>
                                </div>

                                {/* ÉTAPE 3 : RENSEIGNEMENTS DU NOUVEAU CONTRAT (Si Transformation / Renouvellement) */}
                                {exigeNouveauContrat && (
                                    <div className="space-y-4 bg-brand-500/5 p-4 rounded-sm border border-brand-500/20">
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-brand-600 dark:text-brand-400 flex items-center gap-2">
                                            <Building size={14} /> 3. Nouveau Contrat (Bascule Automatique & Clôture de l'ancien)
                                        </h3>

                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                            <Select
                                                id="idTypeContrat"
                                                name="idTypeContrat"
                                                label="Nouveau Type de Contrat *"
                                                required
                                                value={formData.idTypeContrat}
                                                onChange={handleChange}
                                            >
                                                <option value="">-- Sélectionner le Contrat --</option>
                                                {typesContrat.map(tc => <option key={tc.idTypeContrat} value={tc.idTypeContrat}>{tc.libelle}</option>)}
                                            </Select>

                                            <Select
                                                id="idTypeIntervenant"
                                                name="idTypeIntervenant"
                                                label="Nouveau Profil d'intervenant *"
                                                required
                                                value={formData.idTypeIntervenant}
                                                onChange={handleChange}
                                            >
                                                <option value="">-- Sélectionner le profil --</option>
                                                {typesIntervenant.map(ti => (
                                                    <option key={ti.idTypeIntervenant} value={ti.idTypeIntervenant}>
                                                        {ti.libelle}
                                                    </option>
                                                ))}
                                            </Select>

                                            <Input
                                                id="numeroContrat"
                                                name="numeroContrat"
                                                label="Numéro du Nouveau Contrat"
                                                placeholder="Ex: CTR-2026-009"
                                                value={formData.numeroContrat}
                                                onChange={handleChange}
                                            />

                                            <Input
                                                id="dateDebut"
                                                name="dateDebut"
                                                label="Date de Début *"
                                                type="date"
                                                required
                                                value={formData.dateDebut || formData.dateEffet}
                                                onChange={handleChange}
                                            />

                                            <Input
                                                id="dateFin"
                                                name="dateFin"
                                                label="Date de Fin (Optionnel si CDI)"
                                                type="date"
                                                value={formData.dateFin}
                                                onChange={handleChange}
                                            />

                                            <Input
                                                id="dateSignature"
                                                name="dateSignature"
                                                label="Date de Signature"
                                                type="date"
                                                value={formData.dateSignature}
                                                onChange={handleChange}
                                            />
                                        </div>

                                        {/* PERIODE D'ESSAI */}
                                        <div className="flex items-center gap-2 pt-2 border-t border-brand-500/20">
                                            <input
                                                type="checkbox"
                                                id="aPeriodeEssai"
                                                name="aPeriodeEssai"
                                                checked={Boolean(formData.aPeriodeEssai)}
                                                onChange={handleChange}
                                                className="w-4 h-4 text-brand-500 rounded border-ink-300 focus:ring-brand-500 cursor-pointer"
                                            />
                                            <label htmlFor="aPeriodeEssai" className="text-xs font-bold text-ink-900 dark:text-white cursor-pointer flex items-center gap-1.5">
                                                <Clock size={14} className="text-brand-500" />
                                                Soumettre le nouveau contrat à une Période d'Essai
                                            </label>
                                        </div>

                                        {formData.aPeriodeEssai && (
                                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                                <Select
                                                    id="dureeEssaiMois"
                                                    name="dureeEssaiMois"
                                                    label="Durée de l'essai *"
                                                    value={formData.dureeEssaiMois}
                                                    onChange={handleChange}
                                                >
                                                    <option value="1">1 Mois</option>
                                                    <option value="2">2 Mois</option>
                                                    <option value="3">3 Mois</option>
                                                    <option value="4">4 Mois</option>
                                                    <option value="6">6 Mois</option>
                                                </Select>

                                                <Input
                                                    id="dateFinEssai"
                                                    name="dateFinEssai"
                                                    label="Date de Fin d'Essai"
                                                    type="date"
                                                    value={formData.dateFinEssai}
                                                    onChange={handleChange}
                                                />
                                            </div>
                                        )}
                                    </div>
                                )}

                                {/* ÉTAPE 4 : DATES ET MOTIF WORKFLOW */}
                                <div className="space-y-4 border-t border-ink-150 dark:border-ink-700 pt-4">
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Input
                                            id="dateEffet"
                                            name="dateEffet"
                                            label="Date de Prise d'Effet *"
                                            type="date"
                                            required
                                            value={formData.dateEffet}
                                            onChange={handleChange}
                                        />

                                        <Textarea
                                            id="motif"
                                            name="motif"
                                            label="Motif / Raison du Changement"
                                            rows={2}
                                            value={formData.motif}
                                            onChange={handleChange}
                                            placeholder="Ex: Promotion suite à l'évaluation annuelle 2025"
                                        />
                                    </div>
                                </div>
                            </>
                        )}
                    </form>

                    {/* COLONNE DROITE (50%) : Aperçu Avenant */}
                    <div className="hidden lg:block w-1/2 flex-shrink-0 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-6 flex flex-col items-center">
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
                    <p className="text-[11px] text-ink-400 font-normal">
                        Le brouillon sera soumis à la validation <span className="font-bold text-warning-600">RH / Direction</span>.
                    </p>
                    <div className="flex justify-end gap-3">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="primary"
                            size="sm"
                            loading={submitting}
                            disabled={loadingRef || progressPct < 100}
                            iconLeft={<Save size={16} />}
                        >
                            Enregistrer le Brouillon de Transition
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}