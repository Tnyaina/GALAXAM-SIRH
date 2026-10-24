import { useState, useEffect, useMemo, useCallback } from 'react';
import contratService from '@/features/employees/api/contratService';
import { useContractReferentiels } from './useContractReferentiels';
import { fmtDateInput } from '@/utils/dateUtils';
import { 
    EVENT_TARGET_CONFIG, 
    detectSourceContractCode, 
    getRelevantEventTypes 
} from '@/features/employees/utils/carriereTransitionMatrix';
import { getErrorMessage } from '@/api/apiClient';

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
    // Données dérivées et contexte de référentiels.
    const isEditMode = !!initialData;

    const {
        filiales = [], sites = [], departements = [], services = [], fonctions = [],
        typesContrat = [], typesIntervenant = [], categoriesPro = [], modesPaie = [], responsables = [],
        typesEvenementRh = [], loading: loadingRef,
    } = useContractReferentiels(isOpen, idEmploye);

    const typesEvenementList = useMemo(() => typesEvenementRh || [], [typesEvenementRh]);

    // État local : formulaire, aperçu et soumission.
    const [formData, setFormData] = useState({
        codeTypeEvenement: '', idFiliale: '', idTypeContrat: '', idTypeIntervenant: '',
        dateEffet: '', dateDebut: '', dateFin: '', dateSignature: '', motifCddCode: '', comptabiliseEffectif: true,
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
    const [isDirty, setIsDirty] = useState(false);

    useEffect(() => {
        if (isOpen) {
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        } else {
            setMounted(false);
        }
    }, [isOpen]);

    // 1. Détecter le code du contrat source (STAGE, CDD, CDI, etc.) délégué à l'utilitaire
    const sourceContractCode = useMemo(() => {
        return detectSourceContractCode(contratActif, typesContrat);
    }, [contratActif, typesContrat]);

    // 2. Filtrer et annoter la liste des événements pertinents délégué à la matrice
    const relevantEvTypes = useMemo(() => {
        return getRelevantEventTypes(typesEvenementList, sourceContractCode);
    }, [typesEvenementList, sourceContractCode]);

    // 3. Helper pour appliquer l'auto-sélection de la matrice
    const applyTargetEventConfig = useCallback((evCode, currentFormData) => {
        const config = EVENT_TARGET_CONFIG[evCode];
        if (!config) return currentFormData;

        const updated = { ...currentFormData };

        if (config.defaultTypeContrat && typesContrat.length > 0) {
            const currentTc = typesContrat.find(t => String(t.idTypeContrat) === String(updated.idTypeContrat));
            const isAllowed = config.allowedTypesContrat ? config.allowedTypesContrat.includes(currentTc?.code) : true;
            if (!updated.idTypeContrat || config.lockTypeContrat || !isAllowed) {
                const tc = typesContrat.find(t => t.code === config.defaultTypeContrat);
                if (tc) {
                    updated.idTypeContrat = String(tc.idTypeContrat);
                }
            }
        }

        if (config.defaultTypeIntervenant && typesIntervenant.length > 0) {
            const currentTi = typesIntervenant.find(t => String(t.idTypeIntervenant) === String(updated.idTypeIntervenant));
            const isAllowed = config.allowedTypesIntervenant ? config.allowedTypesIntervenant.includes(currentTi?.code) : true;
            if (!updated.idTypeIntervenant || config.lockTypeIntervenant || !isAllowed) {
                const ti = typesIntervenant.find(t => t.code === config.defaultTypeIntervenant);
                if (ti) {
                    updated.idTypeIntervenant = String(ti.idTypeIntervenant);
                }
            }
        }

        if (config.autoCheckEssai !== undefined) {
            updated.aPeriodeEssai = config.autoCheckEssai;
            updated.dureeEssaiMois = config.defaultEssaiMois || '0';
            if (!config.autoCheckEssai) {
                updated.dateFinEssai = '';
            }
        }

        return updated;
    }, [typesContrat, typesIntervenant]);

    const typeEvObj = typesEvenementList.find(te => te.code === formData.codeTypeEvenement) || typeEvenement;
    const exigeNouveauContrat = typeEvObj?.exigeNouveauContrat ?? typeEvObj?.isExigeNouveauContrat ?? ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT', 'REPRISE_ANCIENNETE'].includes(formData.codeTypeEvenement);
    const autorisePeriodeEssai = typeEvObj?.autorisePeriodeEssai ?? typeEvObj?.isAutorisePeriodeEssai ?? (exigeNouveauContrat && formData.codeTypeEvenement !== 'RENOUVELLEMENT_CDD');

    // 4. Initialisation du formulaire
    useEffect(() => {
        if (!isOpen) return;

        // Déterminer l'événement par défaut éligible
        let defaultTypeEv = typeEvenement?.code;
        if (!defaultTypeEv) {
            const firstEligible = relevantEvTypes.find(e => e.isEligible);
            defaultTypeEv = firstEligible ? firstEligible.code : 'PROMOTION';
        }

        const today = fmtDateInput(new Date());

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || initialData || contratActif || {};
            const a = initialData.affectationCible || initialData.affectationSource || initialData || affectationActive || {};
            const wf = initialData.workflow || initialData || {};

            const eventCode = wf.codeTypeEvenement || defaultTypeEv;
            const isCddRenew = eventCode === 'RENOUVELLEMENT_CDD';
            const isNewContractTransition = ['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT', 'REPRISE_ANCIENNETE'].includes(eventCode);
            const dureeEssai = isCddRenew ? null : (c.dureeEssaiMois ?? initialData.dureeEssaiMois);

            setFormData({
                codeTypeEvenement: wf.codeTypeEvenement || defaultTypeEv,
                idFiliale: c.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: c.idTypeContrat || '',
                idTypeIntervenant: c.idTypeIntervenant || '',
                dateEffet: isNewContractTransition
                    ? fmtDateInput(c.dateDebut) || fmtDateInput(wf.dateEffet || wf.dateDebutAffectation) || today
                    : fmtDateInput(wf.dateEffet || wf.dateDebutAffectation) || today,
                dateDebut: fmtDateInput(c.dateDebut) || today,
                dateFin: fmtDateInput(c.dateFin) || '',
                dateSignature: fmtDateInput(c.dateSignature) || '',
                motifCddCode: c.motifCddCode || '',
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

            let initData = {
                codeTypeEvenement: defaultTypeEv,
                idFiliale: baseContrat.idFiliale || filiales[0]?.idFiliale || '',
                idTypeContrat: baseContrat.idTypeContrat || '',
                idTypeIntervenant: baseContrat.idTypeIntervenant || '',
                dateEffet: suggestedDateDebut,
                dateDebut: suggestedDateDebut,
                dateFin: '',
                dateSignature: '',
                motifCddCode: '',
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
            };

            // Appliquer la matrice d'auto-sélection
            initData = applyTargetEventConfig(defaultTypeEv, initData);
            setFormData(initData);
        }
        setIsDirty(false);
        setErrorMsg('');
    }, [isOpen, typeEvenement, initialData, contratActif, affectationActive, filiales, sites, departements, services, fonctions, categoriesPro, modesPaie, relevantEvTypes, applyTargetEventConfig]);

    // 5. Synchronisation asynchrone dès que les types de contrat ou intervenants sont reçus
    useEffect(() => {
        if (!isOpen || initialData || !formData.codeTypeEvenement) return;
        const config = EVENT_TARGET_CONFIG[formData.codeTypeEvenement];
        if (!config) return;

        setFormData(prev => {
            let changed = false;
            const next = { ...prev };

            const currentTc = typesContrat.find(tc => String(tc.idTypeContrat) === String(prev.idTypeContrat));
            const isTcAllowed = config.allowedTypesContrat ? config.allowedTypesContrat.includes(currentTc?.code) : true;
            if (config.defaultTypeContrat && (!prev.idTypeContrat || config.lockTypeContrat || !isTcAllowed) && typesContrat.length > 0) {
                const foundTc = typesContrat.find(tc => tc.code === config.defaultTypeContrat);
                if (foundTc && String(prev.idTypeContrat) !== String(foundTc.idTypeContrat)) {
                    next.idTypeContrat = String(foundTc.idTypeContrat);
                    changed = true;
                }
            }

            const currentTi = typesIntervenant.find(ti => String(ti.idTypeIntervenant) === String(prev.idTypeIntervenant));
            const isTiAllowed = config.allowedTypesIntervenant ? config.allowedTypesIntervenant.includes(currentTi?.code) : true;
            if (config.defaultTypeIntervenant && (!prev.idTypeIntervenant || config.lockTypeIntervenant || !isTiAllowed) && typesIntervenant.length > 0) {
                const foundTi = typesIntervenant.find(ti => ti.code === config.defaultTypeIntervenant);
                if (foundTi && String(prev.idTypeIntervenant) !== String(foundTi.idTypeIntervenant)) {
                    next.idTypeIntervenant = String(foundTi.idTypeIntervenant);
                    changed = true;
                }
            }

            return changed ? next : prev;
        });
    }, [isOpen, initialData, formData.codeTypeEvenement, typesContrat, typesIntervenant]);

    // 6. Gestion des modifications de champs avec auto-sélections réactives
    const setField = (field, val) => {
        setIsDirty(true);
        setFormData(prev => {

            let updated = { ...prev, [field]: val };
            if (field === 'idDepartement') {
                updated.idService = '';
            }
            if (field === 'idTypeContrat' && String(val) !== String(prev.idTypeContrat)) {
                updated.motifCddCode = '';
            }
            if (field === 'codeTypeEvenement') {
                // Déclencher l'auto-sélection selon l'événement
                updated = applyTargetEventConfig(val, updated);
                if (['TRANSFORMATION_CDI', 'RENOUVELLEMENT_CDD', 'EMBAUCHE_APRES_STAGE', 'REPRISE_CONSULTANT', 'REPRISE_ANCIENNETE'].includes(val)) {
                    updated.dateEffet = updated.dateDebut || updated.dateEffet;
                }
            }
            if (field === 'dateEffet' && (!prev.dateDebut || prev.dateDebut === prev.dateEffet)) {
                updated.dateDebut = val;
            }
            if (field === 'dateDebut' && exigeNouveauContrat) {
                updated.dateEffet = val;
            }
            if (field === 'dateDebut' || field === 'aPeriodeEssai' || field === 'dureeEssaiMois') {
                const dateDeb = field === 'dateDebut' ? val : updated.dateDebut;
                const aEssai = field === 'aPeriodeEssai' ? val : updated.aPeriodeEssai;
                const dur = field === 'dureeEssaiMois' ? val : updated.dureeEssaiMois;

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

    // 7. Filtrage et verrouillage des options de contrat et intervenant selon l'événement
    const currentTargetConfig = EVENT_TARGET_CONFIG[formData.codeTypeEvenement];

    const filteredTypesContrat = useMemo(() => {
        if (!currentTargetConfig?.allowedTypesContrat) return typesContrat;
        return typesContrat.filter(tc => currentTargetConfig.allowedTypesContrat.includes(tc.code));
    }, [typesContrat, currentTargetConfig]);

    const filteredTypesIntervenant = useMemo(() => {
        if (!currentTargetConfig?.allowedTypesIntervenant) return typesIntervenant;
        return typesIntervenant.filter(ti => currentTargetConfig.allowedTypesIntervenant.includes(ti.code));
    }, [typesIntervenant, currentTargetConfig]);

    const lockTypeContrat = Boolean(currentTargetConfig?.lockTypeContrat);
    const lockTypeIntervenant = Boolean(currentTargetConfig?.lockTypeIntervenant);

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

            if (selectedTypeContrat?.code === 'CDD' && !formData.motifCddCode) {
                setErrorMsg('Sélectionnez le motif légal correspondant à ce CDD.');
                return;
            }

            if (formData.dateFin && formData.dateDebut && formData.dateFin <= formData.dateDebut) {
                setErrorMsg("La date de fin de contrat doit être strictement postérieure à la date de début.");
                return;
            }

            if (formData.aPeriodeEssai && formData.dateFinEssai && formData.dateDebut && formData.dateFinEssai <= formData.dateDebut) {
                setErrorMsg("La date de fin d'essai doit être strictement postérieure à la date de début de contrat.");
                return;
            }

            if (formData.dateFin && formData.aPeriodeEssai && formData.dateFinEssai && formData.dateFinEssai > formData.dateFin) {
                setErrorMsg("La période d'essai ne peut pas se prolonger au-delà du terme du contrat.");
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
            dateDebut: formData.dateDebut || null,
            dateFin: formData.dateFin || null,
            dateSignature: formData.dateSignature || null,
            motifCddCode: formData.motifCddCode || null,
            comptabiliseEffectif: Boolean(formData.comptabiliseEffectif),
            numeroContrat: formData.numeroContrat || null,
            descriptionContrat: formData.descriptionContrat || null,
            commentaireContrat: formData.commentaireContrat || null,
            aPeriodeEssai: Boolean(formData.aPeriodeEssai),
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : 0,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
            idSite: formData.idSite ? parseInt(formData.idSite) : null,
            idDepartement: formData.idDepartement ? parseInt(formData.idDepartement) : null,
            idService: formData.idService ? parseInt(formData.idService) : null,
            idFonction: formData.idFonction ? parseInt(formData.idFonction) : null,
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null,
            idCategorieProfessionnelle: formData.idCategorieProfessionnelle ? parseInt(formData.idCategorieProfessionnelle) : null,
            idModeCalculPaie: formData.idModeCalculPaie ? parseInt(formData.idModeCalculPaie) : null,
            salaireBase: formData.salaireBase ? parseFloat(formData.salaireBase) : null,
            descriptionAffectation: formData.descriptionAffectation || null,
            commentaireAffectation: formData.commentaireAffectation || null,
            motif: formData.motif || null,
        };

        setSubmitting(true);
        try {
            let res;
            if (isEditMode && initialData?.workflow?.idWorkflow) {
                res = await contratService.modifierBrouillonWorkflow(
                    initialData.workflow.idWorkflow,
                    payload
                );
            } else {
                res = await contratService.creerBrouillonTransition(idEmploye, payload);
            }

            if (onSuccess) {
                onSuccess(res);
            }
            onClose();
        } catch (err) {
            console.error('Erreur soumission transition:', err);
            setErrorMsg(getErrorMessage(err, 'Erreur lors de l\'enregistrement de la transition.'));
        } finally {
            setSubmitting(false);
        }
    };

    // Calculs de libellés pour l'aperçu
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
        isDirty,
        exigeNouveauContrat, autorisePeriodeEssai, isEditMode,
        loadingRef, handleSubmit,

        // Données d'aperçu & matrice
        relevantEvTypes, typeEvObj, sourceContractCode,
        avantTypeContrat, apresTypeContrat,
        avantSite, apresSite,
        avantDepartement, apresDepartement,
        avantService, apresService,
        avantFonction, apresFonction,
        avantCategorie, apresCategorie,
        avantResponsable, apresResponsable,
        avantModePaie, apresModePaie,
        avantSalaire,

        // Référentiels filtrés et verrouillages
        filiales, sites, departements,
        services, fonctions, categoriesPro, modesPaie, responsables,
        typesContrat: filteredTypesContrat,
        typesIntervenant: filteredTypesIntervenant,
        lockTypeContrat,
        lockTypeIntervenant
    };
}
