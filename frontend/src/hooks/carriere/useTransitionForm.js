import { useState, useEffect } from 'react';
import contratService from '@/api/employe/contratService';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import { fmtDateInput } from '@/utils/carriereUtils';

export function useTransitionForm({
    isOpen,
    onClose,
    idEmploye,
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

    const typeEvObj = typesEvenementList.find(te => te.code === formData.codeTypeEvenement) || typeEvenement;
    const exigeNouveauContrat = typeEvObj?.exigeNouveauContrat ?? typeEvObj?.isExigeNouveauContrat ?? ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT'].includes(formData.codeTypeEvenement);
    const autorisePeriodeEssai = typeEvObj?.autorisePeriodeEssai ?? typeEvObj?.isAutorisePeriodeEssai ?? (exigeNouveauContrat && formData.codeTypeEvenement !== 'RENOUVELLEMENT_CDD');

    useEffect(() => {
        if (!isOpen) return;

        const defaultTypeEv = typeEvenement?.code || 'PROMOTION';
        const today = fmtDateInput(new Date());

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || initialData || contratActif || {};
            const a = initialData.affectationCible || initialData.affectationSource || initialData || affectationActive || {};
            const wf = initialData.workflow || initialData || {};

            const isCddRenew = (wf.codeTypeEvenement || defaultTypeEv) === 'RENOUVELLEMENT_CDD';
            const dureeEssai = isCddRenew ? null : (c.dureeEssaiMois ?? initialData.dureeEssaiMois);

            setFormData({
                codeTypeEvenement: wf.codeTypeEvenement || defaultTypeEv,
                idFiliale: c.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: c.idTypeContrat || '',
                idTypeIntervenant: c.idTypeIntervenant || '',
                dateEffet: fmtDateInput(wf.dateEffet || wf.dateDebutAffectation) || today,
                dateDebut: fmtDateInput(c.dateDebut) || today,
                dateFin: fmtDateInput(c.dateFin) || '',
                dateSignature: fmtDateInput(c.dateSignature) || today,
                comptabiliseEffectif: c.comptabiliseEffectif ?? true,
                numeroContrat: c.numeroContrat || '',
                descriptionContrat: c.description || '',
                commentaireContrat: c.commentaire || '',
                aPeriodeEssai: !isCddRenew && Boolean(dureeEssai && parseInt(dureeEssai) > 0),
                dureeEssaiMois: isCddRenew ? '0' : (dureeEssai ? String(dureeEssai) : '3'),
                dateFinEssai: isCddRenew ? '' : (fmtDateInput(c.dateFinEssai) || ''),
                idSite: a.idSite || sites[0]?.idSite || '',
                idDepartement: a.idDepartement || departements[0]?.idDepartement || '',
                idService: a.idService || services[0]?.idService || '',
                idFonction: a.idFonction || fonctions[0]?.idFonction || '',
                idResponsableHierarchique: a.idResponsableHierarchique || a.id_responsable_hierarchique || '',
                idCategorieProfessionnelle: a.idCategorieProfessionnelle || categoriesPro[0]?.idCategorie || '',
                idModeCalculPaie: a.idModeCalculPaie || modesPaie[0]?.idModeCalcul || '',
                salaireBase: a.salaireBase ? String(a.salaireBase) : '',
                descriptionAffectation: a.description || '',
                commentaireAffectation: a.commentaire || '',
                motif: wf.motif || typeEvenement?.libelle || '',
            });
        } else {
            const baseContrat = contratActif || {};
            const baseAff = affectationActive || {};
            const isExigeNouveau = ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT'].includes(defaultTypeEv);

            let suggestedDateDebut = today;
            if (isExigeNouveau && baseContrat.dateFin) {
                try {
                    const dFin = new Date(baseContrat.dateFin);
                    dFin.setDate(dFin.getDate() + 1);
                    suggestedDateDebut = fmtDateInput(dFin) || today;
                } catch {
                    suggestedDateDebut = today;
                }
            }

            const isCddRenew = defaultTypeEv === 'RENOUVELLEMENT_CDD';

            setFormData({
                codeTypeEvenement: defaultTypeEv,
                idFiliale: baseContrat.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: baseContrat.idTypeContrat || '',
                idTypeIntervenant: baseContrat.idTypeIntervenant || '',
                dateEffet: suggestedDateDebut,
                dateDebut: suggestedDateDebut,
                dateFin: '',
                dateSignature: today,
                comptabiliseEffectif: true,
                numeroContrat: '',
                descriptionContrat: '',
                commentaireContrat: '',
                aPeriodeEssai: false,
                dureeEssaiMois: isCddRenew ? '0' : '3',
                dateFinEssai: '',
                idSite: baseAff.idSite || sites[0]?.idSite || '',
                idDepartement: baseAff.idDepartement || departements[0]?.idDepartement || '',
                idService: baseAff.idService || services[0]?.idService || '',
                idFonction: baseAff.idFonction || fonctions[0]?.idFonction || '',
                idResponsableHierarchique: baseAff.idResponsableHierarchique || baseAff.id_responsable_hierarchique || '',
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
            // Quand le département change, reset le service (il peut ne pas appartenir au nouveau dept)
            if (field === 'idDepartement') {
                updated.idService = '';
            }
            if (field === 'codeTypeEvenement' && val === 'RENOUVELLEMENT_CDD') {
                updated.aPeriodeEssai = false;
                updated.dureeEssaiMois = '0';
                updated.dateFinEssai = '';
            }
            if (field === 'dateEffet' && (!prev.dateDebut || prev.dateDebut === prev.dateEffet)) {
                updated.dateDebut = val;
            }
            if (field === 'dateDebut' || field === 'aPeriodeEssai' || field === 'dureeEssaiMois') {
                const dateDeb = field === 'dateDebut' ? val : prev.dateDebut;
                const aEssai = field === 'aPeriodeEssai' ? val : prev.aPeriodeEssai;
                const dur = field === 'dureeEssaiMois' ? val : prev.dureeEssaiMois;

                if (aEssai && dateDeb && dur && updated.codeTypeEvenement !== 'RENOUVELLEMENT_CDD') {
                    try {
                        const d = new Date(dateDeb);
                        d.setMonth(d.getMonth() + parseInt(dur));
                        updated.dateFinEssai = fmtDateInput(d);
                    } catch (err) {
                        console.warn("Erreur calcul date fin essai:", err);
                    }
                } else {
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
        }

        const payload = {
            codeTypeEvenement: formData.codeTypeEvenement,
            dateEffet: formData.dateEffet || null,
            idContratSource: contratActif?.idContrat || initialData?.workflow?.idContratSource || null,
            idAffectationSource: affectationActive?.idAffectation || initialData?.workflow?.idAffectationSource || null,
            idFiliale: formData.idFiliale ? parseInt(formData.idFiliale) : null,
            idTypeContrat: formData.idTypeContrat ? parseInt(formData.idTypeContrat) : null,
            idTypeIntervenant: formData.idTypeIntervenant ? parseInt(formData.idTypeIntervenant) : null,
            dateDebut: formData.dateDebut || formData.dateEffet || null,
            dateFin: formData.dateFin ? formData.dateFin : null, // Sécurité : chaîne vide -> null
            dateSignature: formData.dateSignature || formData.dateEffet || null,
            comptabiliseEffectif: formData.comptabiliseEffectif ?? true,
            numeroContrat: formData.numeroContrat ? formData.numeroContrat.trim() : null,
            descriptionContrat: formData.descriptionContrat ? formData.descriptionContrat.trim() : null,
            commentaireContrat: formData.commentaireContrat ? formData.commentaireContrat.trim() : null,
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : null,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null, // Sécurité : chaîne vide -> null
            idSite: formData.idSite ? parseInt(formData.idSite) : null,
            idDepartement: formData.idDepartement ? parseInt(formData.idDepartement) : null,
            idService: formData.idService ? parseInt(formData.idService) : null,
            idFonction: formData.idFonction ? parseInt(formData.idFonction) : null,
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null, // Sécurité
            idCategorieProfessionnelle: formData.idCategorieProfessionnelle ? parseInt(formData.idCategorieProfessionnelle) : null,
            idModeCalculPaie: formData.idModeCalculPaie ? parseInt(formData.idModeCalculPaie) : null,
            salaireBase: formData.salaireBase ? parseFloat(String(formData.salaireBase).replace(/\s/g, '').replace(/,/g, '.')) : 0,
            descriptionAffectation: formData.descriptionAffectation ? formData.descriptionAffectation.trim() : null,
            commentaireAffectation: formData.commentaireAffectation ? formData.commentaireAffectation.trim() : null,
            motif: formData.motif ? formData.motif.trim() : null,
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
            setErrorMsg(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || "Erreur lors de l'enregistrement de la transition.");
        } finally {
            setSubmitting(false);
        }
    };

    // Calculs de libellés pour l'aperçu
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

    const availableEvTypes = typesEvenementList.filter(ev => {
        const code = (ev.code || '').toUpperCase();
        if (code === 'EMBAUCHE_APRES_STAGE') return true;
        if (EXCLUDED_CODES.includes(code)) return false;
        if (code.startsWith('RUPTURE') || code.startsWith('ESSAI') || code.startsWith('FIN_') || (code.startsWith('EMBAUCHE') && code !== 'EMBAUCHE_APRES_STAGE') || code.startsWith('DEMISSION') || code.startsWith('LICENCIEMENT')) {
            return false;
        }
        return true;
    });
    const relevantEvTypes = availableEvTypes.length > 0 ? availableEvTypes : DEFAULT_RELEVANT_EVENTS;

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

    return {
        formData, setField,
        submitting, errorMsg, setErrorMsg,
        showPreviewMobile, setShowPreviewMobile,
        mounted,
        exigeNouveauContrat, autorisePeriodeEssai, isEditMode,
        loadingRef, handleSubmit,

        // Données d'aperçu
        relevantEvTypes, typeEvObj,
        avantTypeContrat, apresTypeContrat,
        avantSite, apresSite,
        avantDepartement, apresDepartement,
        avantService, apresService,
        avantFonction, apresFonction,
        avantCategorie, apresCategorie,
        avantResponsable, apresResponsable,
        avantModePaie, apresModePaie,
        avantSalaire,

        // Référentiels
        filiales, typesContrat, typesIntervenant, sites, departements,
        services, fonctions, categoriesPro, modesPaie, responsables
    };
}
