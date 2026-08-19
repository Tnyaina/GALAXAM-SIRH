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
            // Quand le département change, reset le service (il peut ne pas appartenir au nouveau dept)
            if (field === 'idDepartement') {
                updated.idService = '';
            }
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

    const typeEvObj = typesEvenementList.find(te => te.code === formData.codeTypeEvenement) || typeEvenement;

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
        exigeNouveauContrat, isEditMode,
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
