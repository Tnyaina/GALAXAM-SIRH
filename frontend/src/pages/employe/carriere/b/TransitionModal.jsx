import { useState, useEffect } from 'react';
import { RefreshCw, ShieldAlert, Save, X, Eye, EyeOff, FileSignature, ArrowRight, Building2, MessageSquareText } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import contratService from '@/api/employe/contratService';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import { fmtDateInput } from '@/utils/carriereUtils';

import ContratFieldsSection from '@/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/components/carriere/form/AffectationFieldsSection';
import PeriodeEssaiFieldsSection from '@/components/carriere/form/PeriodeEssaiFieldsSection';
import AvenantPreviewDocument from '@/components/carriere/preview/AvenantPreviewDocument';

// En-tête de section réutilisable : numérote les étapes du formulaire
// pour que la progression (mouvement -> contrat -> affectation -> motif) soit visible.
function FormSectionHeader({ step, icon: Icon, title }) {
    return (
        <div className="flex items-center gap-2">
            <span className="flex items-center justify-center w-5 h-5 rounded-none bg-brand-500/10 text-brand-500 text-[10px] font-bold shrink-0">
                {step}
            </span>
            <h3 className="flex items-center gap-1.5 text-xs font-bold text-ink-600 dark:text-ink-300 uppercase tracking-wider">
                {Icon && <Icon size={13} className="text-brand-500" />}
                {title}
            </h3>
        </div>
    );
}

export default function TransitionModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    typeEvenement = null,
    affectationActive = null,
    contratActif = null,
    initialData = null,
    onSuccess,
}) {
    const isEditMode = !!initialData;

    const {
        filiales = [], sites = [], departements = [], services = [], fonctions = [],
        typesContrat = [], typesIntervenant = [], categoriesPro = [], modesPaie = [], responsables = [],
        typesEvenementRh = [], loading: loadingRef,
    } = useContractReferentiels(isOpen, idEmploye);

    const typesEvenementList = typesEvenementRh || [];

    const [formData, setFormData] = useState({
        codeTypeEvenement: '', idFiliale: '', idTypeContrat: '', idTypeIntervenant: '',
        dateEffet: '', dateDebut: '', dateFin: '', dateSignature: '', comptabiliseEffectif: true,
        numeroContrat: '', descriptionContrat: '', commentaireContrat: '', aPeriodeEssai: false,
        dureeEssaiMois: '3', dateFinEssai: '', idSite: '', idDepartement: '', idService: '',
        idFonction: '', idResponsableHierarchique: '', idCategorieProfessionnelle: '',
        idModeCalculPaie: '', salaireBase: '', descriptionAffectation: '', commentaireAffectation: '',
        motif: '',
    });

    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [mounted, setMounted] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        } else {
            setMounted(false);
        }
    }, [isOpen]);

    const exigeNouveauContrat = ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT']
        .includes(formData.codeTypeEvenement);

    useEffect(() => {
        if (!isOpen) return;

        const defaultTypeEv = typeEvenement?.code || 'PROMOTION';
        const today = fmtDateInput(new Date());

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || initialData || contratActif || {};
            const a = initialData.affectationCible || initialData.affectationSource || initialData || affectationActive || {};
            const w = initialData.workflow || initialData || {};

            const dureeEssai = c.dureeEssaiMois ?? initialData.dureeEssaiMois;

            setFormData({
                codeTypeEvenement: w.codeTypeEvenement || w.typeEvenementCode || initialData.codeTypeEvenement || defaultTypeEv,
                idFiliale: c.idFiliale || initialData.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: c.idTypeContrat || initialData.idTypeContrat || '',
                idTypeIntervenant: c.idTypeIntervenant || initialData.idTypeIntervenant || '',
                dateEffet: fmtDateInput(w.dateEffet || initialData.dateEffet) || today,
                dateDebut: fmtDateInput(c.dateDebutContrat || c.dateDebut || initialData.dateDebutContrat || initialData.dateDebut) || today,
                dateFin: fmtDateInput(c.dateFinContrat || c.dateFin || initialData.dateFinContrat || initialData.dateFin) || '',
                dateSignature: fmtDateInput(c.dateSignature || c.dateDebutContrat || c.dateDebut || initialData.dateDebutContrat) || today,
                comptabiliseEffectif: c.comptabiliseEffectif !== undefined ? c.comptabiliseEffectif : (initialData.comptabiliseEffectif !== undefined ? initialData.comptabiliseEffectif : true),
                numeroContrat: c.numeroContratCible || c.numeroContratSource || c.numeroContrat || initialData.numeroContratCible || initialData.numeroContratSource || '',
                descriptionContrat: c.descriptionContrat || c.description || initialData.descriptionContrat || initialData.description || '',
                commentaireContrat: c.commentaireContrat || c.commentaire || initialData.commentaireContrat || initialData.commentaire || '',
                aPeriodeEssai: dureeEssai != null && Number(dureeEssai) > 0,
                dureeEssaiMois: dureeEssai ? String(dureeEssai) : '3',
                dateFinEssai: fmtDateInput(c.dateFinEssai || initialData.dateFinEssai) || '',
                idSite: a.idSite || initialData.idSite || sites[0]?.idSite || '',
                idDepartement: a.idDepartement || initialData.idDepartement || departements[0]?.idDepartement || '',
                idService: a.idService || initialData.idService || services[0]?.idService || '',
                idFonction: a.idFonction || initialData.idFonction || fonctions[0]?.idFonction || '',
                idResponsableHierarchique: a.idResponsableHierarchique || initialData.idResponsableHierarchique || '',
                idCategorieProfessionnelle: a.idCategorieProfessionnelle || initialData.idCategorieProfessionnelle || categoriesPro[0]?.idCategorie || '',
                idModeCalculPaie: a.idModeCalculPaie || initialData.idModeCalculPaie || modesPaie[0]?.idModeCalcul || '',
                salaireBase: a.salaireBase ? String(a.salaireBase) : (initialData.salaireBase ? String(initialData.salaireBase) : ''),
                descriptionAffectation: a.descriptionAffectation || a.description || initialData.descriptionAffectation || '',
                commentaireAffectation: a.commentaireAffectation || a.commentaire || initialData.commentaireAffectation || '',
                motif: w.motif || initialData.motif || '',
            });
        } else {
            const baseContrat = contratActif || {};
            const baseAff = affectationActive || {};

            setFormData({
                codeTypeEvenement: defaultTypeEv,
                idFiliale: baseContrat.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: baseContrat.idTypeContrat || '',
                idTypeIntervenant: baseContrat.idTypeIntervenant || '',
                dateEffet: today,
                dateDebut: today,
                dateFin: '',
                dateSignature: today,
                comptabiliseEffectif: true,
                numeroContrat: '',
                descriptionContrat: '',
                commentaireContrat: '',
                aPeriodeEssai: false,
                dureeEssaiMois: '3',
                dateFinEssai: '',
                idSite: baseAff.idSite || sites[0]?.idSite || '',
                idDepartement: baseAff.idDepartement || departements[0]?.idDepartement || '',
                idService: baseAff.idService || services[0]?.idService || '',
                idFonction: baseAff.idFonction || fonctions[0]?.idFonction || '',
                idResponsableHierarchique: baseAff.idResponsableHierarchique || '',
                idCategorieProfessionnelle: baseAff.idCategorieProfessionnelle || categoriesPro[0]?.idCategorie || '',
                idModeCalculPaie: baseAff.idModeCalculPaie || modesPaie[0]?.idModeCalcul || '',
                salaireBase: baseAff.salaireBase ? String(baseAff.salaireBase) : '',
                descriptionAffectation: '',
                commentaireAffectation: '',
                motif: typeEvenement?.libelle || 'Changement de situation',
            });
        }
        setErrorMsg('');
    }, [isOpen, typeEvenement, initialData, contratActif, affectationActive, filiales, sites, departements, services, fonctions, categoriesPro, modesPaie]);

    const setField = (field, val) => {
        setFormData(prev => {
            const updated = { ...prev, [field]: val };
            if (field === 'dateEffet' && (!prev.dateDebut || prev.dateDebut === prev.dateEffet)) {
                updated.dateDebut = val;
            }
            if (field === 'dateDebut' || field === 'aPeriodeEssai' || field === 'dureeEssaiMois') {
                const dateDeb = field === 'dateDebut' ? val : prev.dateDebut;
                const aEssai = field === 'aPeriodeEssai' ? val : prev.aPeriodeEssai;
                const dur = field === 'dureeEssaiMois' ? val : prev.dureeEssaiMois;

                if (aEssai && dateDeb && dur) {
                    try {
                        const d = new Date(dateDeb);
                        d.setMonth(d.getMonth() + parseInt(dur));
                        updated.dateFinEssai = fmtDateInput(d);
                    } catch (err) {
                        console.warn("Erreur calcul date fin essai:", err);
                    }
                } else if (!aEssai) {
                    updated.dateFinEssai = '';
                }
            }
            return updated;
        });
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!formData.codeTypeEvenement || !formData.dateEffet || !formData.idSite ||
            !formData.idDepartement || !formData.idService || !formData.idFonction ||
            !formData.idCategorieProfessionnelle || !formData.idModeCalculPaie || !formData.salaireBase) {
            setErrorMsg('Veuillez remplir tous les champs obligatoires (*).');
            return;
        }

        if (exigeNouveauContrat) {
            if (!formData.idTypeContrat || !formData.idTypeIntervenant) {
                setErrorMsg('Le type de contrat et le type d\'intervenant de destination sont obligatoires.');
                return;
            }
        }

        const selectedTypeContrat = typesContrat.find(tc => String(tc.idTypeContrat) === String(formData.idTypeContrat));
        const selectedTypeIntervenant = typesIntervenant.find(ti => String(ti.idTypeIntervenant) === String(formData.idTypeIntervenant));

        if (selectedTypeIntervenant?.code === 'STAGIAIRE' && selectedTypeContrat?.code !== 'STAGE') {
            setErrorMsg("Un intervenant Stagiaire doit obligatoirement avoir un contrat de type Stage / Convention.");
            return;
        }

        if (selectedTypeContrat?.exigeDateFin && !formData.dateFin) {
            setErrorMsg(`La date de fin est obligatoire pour le type de contrat "${selectedTypeContrat.libelle || selectedTypeContrat.code}".`);
            return;
        }

        const payload = {
            codeTypeEvenement: formData.codeTypeEvenement,
            dateEffet: formData.dateEffet,
            idContratSource: contratActif?.idContrat || initialData?.workflow?.idContratSource || null,
            idAffectationSource: affectationActive?.idAffectation || initialData?.workflow?.idAffectationSource || null,
            idFiliale: formData.idFiliale ? parseInt(formData.idFiliale) : null,
            idTypeContrat: formData.idTypeContrat ? parseInt(formData.idTypeContrat) : null,
            idTypeIntervenant: formData.idTypeIntervenant ? parseInt(formData.idTypeIntervenant) : null,
            dateDebut: formData.dateDebut || formData.dateEffet,
            dateFin: formData.dateFin || null,
            dateSignature: formData.dateSignature || formData.dateEffet,
            comptabiliseEffectif: formData.comptabiliseEffectif,
            numeroContrat: formData.numeroContrat || null,
            descriptionContrat: formData.descriptionContrat || null,
            commentaireContrat: formData.commentaireContrat || null,
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : null,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
            idSite: parseInt(formData.idSite),
            idDepartement: parseInt(formData.idDepartement),
            idService: parseInt(formData.idService),
            idFonction: parseInt(formData.idFonction),
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null,
            idCategorieProfessionnelle: parseInt(formData.idCategorieProfessionnelle),
            idModeCalculPaie: parseInt(formData.idModeCalculPaie),
            salaireBase: formData.salaireBase ? parseFloat(String(formData.salaireBase).replace(/\s/g, '').replace(/,/g, '.')) : 0,
            descriptionAffectation: formData.descriptionAffectation || null,
            commentaireAffectation: formData.commentaireAffectation || null,
            motif: formData.motif || null,
        };

        setSubmitting(true);
        try {
            if (isEditMode) {
                const wfId = initialData.idWorkflow || initialData.workflow?.idWorkflow;
                if (!wfId) {
                    throw new Error("Impossible de déterminer l'identifiant du workflow (idWorkflow) à modifier.");
                }
                await contratService.modifierBrouillonWorkflow(wfId, payload);
            } else {
                await contratService.creerBrouillonTransition(idEmploye, payload);
            }
            if (onSuccess) onSuccess();
            onClose();
        } catch (err) {
            console.error("Erreur enregistrement :", err);
            setErrorMsg(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || "Erreur lors de l'enregistrement de la .");
        } finally {
            setSubmitting(false);
        }
    };

    const EXCLUDED_CODES = [
        'EMBAUCHE_INITIALE', 'RUPTURE_CONTRAT', 'DEMISSION', 'LICENCIEMENT',
        'RETRAITE', 'FIN_CONTRAT', 'CONFIRMATION_ESSAI', 'RENOUVELLEMENT_ESSAI',
        'DECES', 'ABANDON_POSTE', 'RUPTURE_NEGOCIEE', 'LICENCIEMENT_FAUTE',
        'LICENCIEMENT_ECONOMIQUE', 'FIN_PERIODE_ESSAI', 'DEPART_RETRAITE'
    ];

    const DEFAULT_RELEVANT_EVENTS = [
        { code: 'MUTATION', libelle: 'Mutation (Site / Dépt / Srv)' },
        { code: 'PROMOTION', libelle: 'Promotion / Changement Poste' },
        { code: 'REVALORISATION', libelle: 'Revalorisation Salariale' },
        { code: 'TRANSFORMATION_CDI', libelle: 'Transformation CDD → CDI' },
        { code: 'RENOUVELLEMENT_CDD', libelle: 'Renouvellement de CDD' },
        { code: 'EMBAUCHE_APRES_STAGE', libelle: 'Passage Stage → CDD/CDI' },
        { code: 'REORGANISATION', libelle: 'Réorganisation Interne' },
    ];

    const availableEvTypes = (typesEvenementList || []).filter(ev => {
        const code = (ev.code || '').toUpperCase();
        if (code === 'EMBAUCHE_APRES_STAGE') return true;
        if (EXCLUDED_CODES.includes(code)) return false;
        if (code.startsWith('RUPTURE') || code.startsWith('ESSAI') || code.startsWith('FIN_') || (code.startsWith('EMBAUCHE') && code !== 'EMBAUCHE_APRES_STAGE') || code.startsWith('DEMISSION') || code.startsWith('LICENCIEMENT')) {
            return false;
        }
        return true;
    });
    const relevantEvTypes = availableEvTypes.length > 0 ? availableEvTypes : DEFAULT_RELEVANT_EVENTS;

    const typeEvObj = (typesEvenementList || []).find(te => te.code === formData.codeTypeEvenement) || typeEvenement;

    // Résolution des libellés Avant/Après pour l'aperçu
    const avantTypeContrat = contratActif ? typesContrat.find(tc => String(tc.idTypeContrat) === String(contratActif.idTypeContrat))?.libelle : null;
    const apresTypeContrat = typesContrat.find(tc => String(tc.idTypeContrat) === String(formData.idTypeContrat))?.libelle;

    const avantSite = affectationActive ? sites.find(s => String(s.idSite) === String(affectationActive.idSite))?.libelle : null;
    const apresSite = sites.find(s => String(s.idSite) === String(formData.idSite))?.libelle;

    const avantDepartement = affectationActive ? departements.find(d => String(d.idDepartement) === String(affectationActive.idDepartement))?.libelle : null;
    const apresDepartement = departements.find(d => String(d.idDepartement) === String(formData.idDepartement))?.libelle;

    const avantService = affectationActive ? services.find(s => String(s.idService) === String(affectationActive.idService))?.libelle : null;
    const apresService = services.find(s => String(s.idService) === String(formData.idService))?.libelle;

    const avantFonction = affectationActive ? fonctions.find(f => String(f.idFonction) === String(affectationActive.idFonction))?.libelle : null;
    const apresFonction = fonctions.find(f => String(f.idFonction) === String(formData.idFonction))?.libelle;

    const avantCategorie = affectationActive ? categoriesPro.find(c => String(c.idCategorieProfessionnelle || c.idCategorie) === String(affectationActive.idCategorieProfessionnelle))?.libelle : null;
    const apresCategorie = categoriesPro.find(c => String(c.idCategorieProfessionnelle || c.idCategorie) === String(formData.idCategorieProfessionnelle))?.libelle;

    const avantRespObj = affectationActive ? responsables.find(r => String(r.idEmploye) === String(affectationActive.idResponsableHierarchique)) : null;
    const avantResponsable = avantRespObj ? (avantRespObj.nomComplet || `${avantRespObj.nom || ''} ${avantRespObj.prenom || ''}`) : null;

    const apresRespObj = responsables.find(r => String(r.idEmploye) === String(formData.idResponsableHierarchique));
    const apresResponsable = apresRespObj ? (apresRespObj.nomComplet || `${apresRespObj.nom || ''} ${apresRespObj.prenom || ''}`) : null;

    const avantModePaie = affectationActive ? modesPaie.find(m => String(m.idModeCalculPaie || m.idModeCalcul) === String(affectationActive.idModeCalculPaie))?.libelle : null;
    const apresModePaie = modesPaie.find(m => String(m.idModeCalculPaie || m.idModeCalcul) === String(formData.idModeCalculPaie))?.libelle;

    const avantSalaire = affectationActive?.salaireBase;

    // Numérotation dynamique des étapes : le bloc "Nouveau Contrat" n'existe
    // que pour certains types de mouvement, donc les étapes suivantes se décalent.
    const stepContrat = 2;
    const stepAffectation = exigeNouveauContrat ? 3 : 2;
    const stepMotif = exigeNouveauContrat ? 4 : 3;

    return (
        <div className={`fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-2 sm:p-4 overflow-y-auto   ${mounted ? 'opacity-100' : 'opacity-0'}`}>
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-[1440px] max-h-[95vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-7 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between bg-ink-50/50 dark:bg-ink-950/40">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-brand-500 text-white rounded-none flex items-center justify-center shadow-md">
                            <RefreshCw size={20} />
                        </div>
                        <div>
                            <h2 className="font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                                {isEditMode ? 'Corriger le Brouillon de Transition' : (typeEvObj?.libelle || 'Transition de Carrière')}
                                <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 border border-amber-500/20 font-mono rounded-none">
                                    BROUILLON
                                </span>
                            </h2>
                            <p className="text-xs text-ink-400">
                                Mouvement RH pour <strong className="text-ink-800 dark:text-white">{nomEmploye}</strong>
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setShowPreviewMobile(!showPreviewMobile)}
                            className="lg:hidden p-2 text-xs font-semibold bg-ink-100 dark:bg-ink-800 text-ink-700 dark:text-ink-200 rounded-none flex items-center gap-1"
                        >
                            {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                            {showPreviewMobile ? 'Masquer Aperçu' : 'Aperçu'}
                        </button>
                        <button onClick={onClose} className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-none  cursor-pointer">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Body split 2 colonnes 50/50 : Formulaire (6/12) - Aperçu Avenant (6/12) */}
                <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-ink-150 dark:divide-ink-700">
                    {/* Formulaire (6/12) */}
                    <div className={`p-7 space-y-6 lg:col-span-6 ${showPreviewMobile ? 'hidden lg:block' : 'block'}`}>
                        {loadingRef ? (
                            <div className="py-12 flex justify-center"><Loading size="md" label="Chargement des référentiels..." /></div>
                        ) : (
                            <form id="transition-form" onSubmit={handleSubmit} className="space-y-6">
                                {errorMsg && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-none p-3 text-xs flex items-center gap-2">
                                        <ShieldAlert size={16} />
                                        {errorMsg}
                                    </div>
                                )}
                                {/* Sélection par petites cartes rectangulaires des événements pertinents */}
                                <section className="space-y-4 pb-6 border-b border-ink-150 dark:border-ink-700">
                                    <FormSectionHeader step={1} icon={RefreshCw} title="Type de Mouvement RH & Dates" />

                                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
                                        {relevantEvTypes.map((t) => {
                                            const descMap = {
                                                MUTATION: 'Garde le contrat actif, modifie le site/service',
                                                PROMOTION: 'Changement de fonction ou classification',
                                                REVALORISATION: 'Ajustement du salaire de base',
                                                TRANSFORMATION_CDI: 'Clôture l\'ancien CDD et active un nouveau CDI',
                                                RENOUVELLEMENT_CDD: 'Avenant de prolongation du CDD',
                                                EMBAUCHE_APRES_STAGE: 'Transformation de stage vers CDD/CDI',
                                                REORGANISATION: 'Réorganisation interne / restructuration',
                                            };
                                            const displayDesc = t.description || descMap[t.code] || 'Mouvement de carrière RH';
                                            const isSelected = formData.codeTypeEvenement === t.code;

                                            return (
                                                <div
                                                    key={t.code}
                                                    role="button"
                                                    tabIndex={0}
                                                    aria-checked={isSelected}
                                                    onClick={() => setField('codeTypeEvenement', t.code)}
                                                    onKeyDown={(e) => {
                                                        if (e.key === 'Enter' || e.key === ' ') {
                                                            e.preventDefault();
                                                            setField('codeTypeEvenement', t.code);
                                                        }
                                                    }}
                                                    className={`
                                                        p-3.5 border rounded-none cursor-pointer  flex flex-col justify-between focus:outline-none focus:ring-2 focus:ring-brand-500
                                                        ${isSelected
                                                            ? 'border-brand-500 bg-brand-500/10 shadow-sm ring-1 ring-brand-500/40'
                                                            : 'border-ink-200 dark:border-ink-800 hover:border-brand-400 bg-white dark:bg-ink-950/30'}
                                                    `}
                                                >
                                                    <div>
                                                        <p className="font-bold text-xs text-ink-900 dark:text-white flex items-center justify-between gap-1">
                                                            <span className="truncate">{t.libelle}</span>
                                                            {isSelected && <ArrowRight size={14} className="text-brand-500 shrink-0" />}
                                                        </p>
                                                        <p className="text-[11px] text-ink-400 mt-1 leading-normal">{displayDesc}</p>
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>

                                    {/* Grille dédiée pour la Date d'Effet & la Date de Signature */}
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2 bg-ink-50/60 dark:bg-ink-900/30 p-3.5 border border-ink-200 dark:border-ink-800 rounded-none">
                                        <Input
                                            label="Date de Prise d'Effet (Obligatoire)"
                                            type="date"
                                            required
                                            value={formData.dateEffet}
                                            onChange={(e) => setField('dateEffet', e.target.value)}
                                            hint="Date d'application effective de cette décision RH"
                                        />
                                        <Input
                                            label="Date de Signature de l'Avenant"
                                            type="date"
                                            value={formData.dateSignature}
                                            onChange={(e) => setField('dateSignature', e.target.value)}
                                            hint="Date d'émargement / signature du document"
                                        />
                                    </div>
                                </section>

                                {/* Nouveau Contrat si nécessaire */}
                                {exigeNouveauContrat && (
                                    <section className="space-y-5 pb-6 border-b border-ink-150 dark:border-ink-700">
                                        <FormSectionHeader step={stepContrat} icon={FileSignature} title="Nouveau Contrat" />
                                        <ContratFieldsSection
                                            formData={formData}
                                            setField={setField}
                                            filiales={filiales}
                                            typesContrat={typesContrat}
                                            typesIntervenant={typesIntervenant}
                                        />

                                        <PeriodeEssaiFieldsSection
                                            formData={formData}
                                            setField={setField}
                                        />
                                    </section>
                                )}

                                {/* Affectation Carrière */}
                                <section className="space-y-5 pb-6 border-b border-ink-150 dark:border-ink-700">
                                    <FormSectionHeader step={stepAffectation} icon={Building2} title="Affectation" />
                                    <AffectationFieldsSection
                                        formData={formData}
                                        setField={setField}
                                        sites={sites}
                                        departements={departements}
                                        services={services}
                                        fonctions={fonctions}
                                        categoriesPro={categoriesPro}
                                        modesPaie={modesPaie}
                                        responsables={responsables}
                                    />
                                </section>

                                {/* Motif */}
                                <section className="space-y-3">
                                    <FormSectionHeader step={stepMotif} icon={MessageSquareText} title="Motif du Mouvement" />
                                    <Textarea
                                        label="Motif / Justification du Mouvement"
                                        rows={2}
                                        value={formData.motif}
                                        onChange={(e) => setField('motif', e.target.value)}
                                        placeholder="Motif détaillé de la promotion ou mutation..."
                                    />
                                </section>
                            </form>
                        )}
                    </div>

                    {/* Aperçu Avenant (6/12) */}
                    <div className={`p-7 bg-ink-50/50 dark:bg-ink-950/30 lg:col-span-6 overflow-y-auto space-y-4 ${showPreviewMobile ? 'block' : 'hidden lg:block'}`}>
                        <div className="flex items-center justify-between pb-2 border-b border-ink-200 dark:border-ink-700">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-ink-500 dark:text-ink-400 flex items-center gap-2">
                                <FileSignature size={14} className="text-brand-500" />
                                Aperçu de l'Avenant RH
                            </h3>
                            <span className="text-[10px] font-mono font-bold text-brand-500 uppercase tracking-widest bg-brand-500/10 px-2 py-0.5 rounded-none">
                                Temps Réel
                            </span>
                        </div>

                        <AvenantPreviewDocument
                            typeEvenementActuel={typeEvObj}
                            nomEmploye={nomEmploye}
                            formData={formData}
                            exigeNouveauContrat={exigeNouveauContrat}
                            avantTypeContrat={avantTypeContrat}
                            apresTypeContrat={apresTypeContrat}
                            avantSite={avantSite}
                            apresSite={apresSite}
                            avantDepartement={avantDepartement}
                            apresDepartement={apresDepartement}
                            avantService={avantService}
                            apresService={apresService}
                            avantFonction={avantFonction}
                            apresFonction={apresFonction}
                            avantCategorie={avantCategorie}
                            apresCategorie={apresCategorie}
                            avantResponsable={avantResponsable}
                            apresResponsable={apresResponsable}
                            avantModePaie={avantModePaie}
                            apresModePaie={apresModePaie}
                            avantSalaire={avantSalaire}
                        />
                    </div>
                </div>

                {/* Footer Boutons */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-between items-center">
                    <p className="text-xs text-ink-400 hidden sm:block">
                        Le mouvement RH reste au statut <strong className="text-amber-500">BROUILLON</strong> tant qu'il n'est pas validé.
                    </p>
                    <div className="flex gap-3 ml-auto">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            form="transition-form"
                            variant="primary"
                            size="sm"
                            loading={submitting}
                            iconLeft={<Save size={16} />}
                        >
                            {isEditMode ? 'Enregistrer les modifications' : 'Créer le Brouillon de Transition'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}