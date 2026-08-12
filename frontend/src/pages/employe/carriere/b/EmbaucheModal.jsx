import { useState, useEffect } from 'react';
import { Briefcase, Building, Landmark, ShieldAlert, Save, X, DollarSign, Clock, FileSignature, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import {
    Clause, DocumentFrame, DocumentHeader, ArticleBlock, DocumentFooter, ContractPreview
} from '@/components/carriere/ContractDocument';

import {
    fmtDateInput, fmtDateFr, fmtMoney,
} from '@/utils/carriereUtils';

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
export default function EmbaucheModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    initialData = null,
    onSuccess,
}) {
    const isEditMode = !!initialData;

    // Référentiels via Custom Hook avec cache & lazy loading
    const {
        filiales,
        sites,
        departements,
        services,
        fonctions,
        typesContrat,
        typesIntervenant,
        categoriesPro,
        modesPaie,
        responsables,
        loading: loadingRef,
        error: refError,
    } = useContractReferentiels(isOpen, idEmploye);

    // Formulaire
    const [formData, setFormData] = useState({
        idFiliale: '',
        idTypeContrat: '',
        idTypeIntervenant: '',
        dateDebut: '',
        dateFin: '',
        dateSignature: '',
        comptabiliseEffectif: true,
        numeroContrat: '',
        description: '',
        commentaire: '',
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
        dateEffet: '',
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
        }
        setMounted(false);
        setShowPreviewMobile(false);
    }, [isOpen]);


    useEffect(() => {
        if (!isOpen || !initialData) return;

        let filialeId = initialData.idFiliale || initialData.id_filiale || '';
        let typeContratId = initialData.idTypeContrat || initialData.id_type_contrat || '';
        let typeIntervenantId = initialData.idTypeIntervenant || initialData.id_type_intervenant || '';
        let siteId = initialData.idSite || initialData.id_site || '';
        let departementId = initialData.idDepartement || initialData.id_departement || '';
        let serviceId = initialData.idService || initialData.id_service || '';
        let fonctionId = initialData.idFonction || initialData.id_fonction || '';
        let respId = initialData.idResponsableHierarchique || initialData.id_responsable_hierarchique || '';
        let catProId = initialData.idCategorieProfessionnelle || initialData.id_categorie_professionnelle || '';
        let modePaieId = initialData.idModeCalculPaie || initialData.id_mode_calcul_paie || '';

        if (!filialeId && initialData.libelleFiliale && filiales.length > 0) {
            const match = filiales.find(f => (f.raisonSociale || f.raison_sociale) === initialData.libelleFiliale);
            if (match) filialeId = match.idFiliale || match.id_filiale;
        }

        if (!typeContratId && initialData.libelleTypeContrat && typesContrat.length > 0) {
            const match = typesContrat.find(t => t.libelle === initialData.libelleTypeContrat);
            if (match) typeContratId = match.idTypeContrat || match.id_type_contrat;
        }

        if (!typeIntervenantId && initialData.libelleTypeIntervenant && typesIntervenant.length > 0) {
            const match = typesIntervenant.find(ti => ti.libelle === initialData.libelleTypeIntervenant);
            if (match) typeIntervenantId = match.idTypeIntervenant || match.id_type_intervenant;
        }

        if (!siteId && initialData.libelleSite && sites.length > 0) {
            const match = sites.find(s => s.libelle === initialData.libelleSite);
            if (match) siteId = match.idSite || match.id_site;
        }

        if (!departementId && initialData.libelleDepartement && departements.length > 0) {
            const match = departements.find(d => d.libelle === initialData.libelleDepartement);
            if (match) departementId = match.idDepartement || match.id_departement;
        }

        if (!serviceId && initialData.libelleService && services.length > 0) {
            const match = services.find(s => s.libelle === initialData.libelleService);
            if (match) serviceId = match.idService || match.id_service;
        }

        if (!fonctionId && initialData.libelleFonction && fonctions.length > 0) {
            const match = fonctions.find(f => f.libelle === initialData.libelleFonction);
            if (match) fonctionId = match.idFonction || match.id_fonction;
        }

        if (!catProId && initialData.libelleCategoriePro && categoriesPro.length > 0) {
            const match = categoriesPro.find(c => c.libelle === initialData.libelleCategoriePro);
            if (match) catProId = match.idCategorieProfessionnelle || match.id_categorie_professionnelle;
        }

        if (!modePaieId && initialData.libelleModeCalculPaie && modesPaie.length > 0) {
            const match = modesPaie.find(m => m.libelle === initialData.libelleModeCalculPaie);
            if (match) modePaieId = match.idModeCalculPaie || match.id_mode_calcul_paie;
        }

        if (!respId && initialData.nomPrenomResponsable && responsables.length > 0) {
            const match = responsables.find(r => `${r.nom} ${r.prenom}`.trim().toLowerCase() === String(initialData.nomPrenomResponsable).trim().toLowerCase());
            if (match) respId = match.idEmploye || match.id_employe;
        }

        setFormData({
            idFiliale: filialeId ? String(filialeId) : '',
            idTypeContrat: typeContratId ? String(typeContratId) : '',
            idTypeIntervenant: typeIntervenantId ? String(typeIntervenantId) : '',
            dateDebut: fmtDateInput(initialData.dateDebutContrat || initialData.dateDebut || initialData.date_debut),
            dateFin: fmtDateInput(initialData.dateFinContrat || initialData.dateFin || initialData.date_fin),
            dateSignature: fmtDateInput(initialData.dateSignature || initialData.date_signature),
            comptabiliseEffectif: initialData.comptabiliseEffectif !== undefined ? Boolean(initialData.comptabiliseEffectif) : true,
            numeroContrat: initialData.numeroContratCible || initialData.numeroContrat || '',
            description: initialData.description || '',
            commentaire: initialData.commentaire || '',
            aPeriodeEssai: Boolean(initialData.aPeriodeEssai || initialData.dureeEssaiMois || initialData.dateFinEssai),
            dureeEssaiMois: initialData.dureeEssaiMois ? String(initialData.dureeEssaiMois) : '3',
            dateFinEssai: fmtDateInput(initialData.dateFinEssai),
            idSite: siteId ? String(siteId) : '',
            idDepartement: departementId ? String(departementId) : '',
            idService: serviceId ? String(serviceId) : '',
            idFonction: fonctionId ? String(fonctionId) : '',
            idResponsableHierarchique: respId ? String(respId) : '',
            idCategorieProfessionnelle: catProId ? String(catProId) : '',
            idModeCalculPaie: modePaieId ? String(modePaieId) : '',
            salaireBase: initialData.salaireBase !== undefined && initialData.salaireBase !== null ? String(initialData.salaireBase) : '',
            descriptionAffectation: initialData.descriptionAffectation || '',
            commentaireAffectation: initialData.commentaireAffectation || '',
            dateEffet: fmtDateInput(initialData.dateEffet || initialData.date_effet),
            motif: initialData.motif || '',
        });
    }, [isOpen, initialData, loadingRef]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => {
            const next = {
                ...prev,
                [name]: type === 'checkbox' ? checked : value,
                ...(name === 'idDepartement' ? { idService: '' } : {}),
            };

            if (next.aPeriodeEssai && (name === 'aPeriodeEssai' || name === 'dureeEssaiMois' || name === 'dateDebut')) {
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

    const filteredServices = formData.idDepartement
        ? services.filter(s => String(s.idDepartement || s.id_departement) === String(formData.idDepartement))
        : services;

    const selectedTypeContratObj = typesContrat.find(t => String(t.idTypeContrat) === String(formData.idTypeContrat));
    const exigeDateFin = selectedTypeContratObj?.exigeDateFin;

    const libFiliale = filiales.find(f => String(f.idFiliale) === String(formData.idFiliale));
    const libTypeContrat = selectedTypeContratObj;
    const libTypeIntervenant = typesIntervenant.find(t => String(t.idTypeIntervenant) === String(formData.idTypeIntervenant));
    const libSite = sites.find(s => String(s.idSite) === String(formData.idSite));
    const libDepartement = departements.find(d => String(d.idDepartement) === String(formData.idDepartement));
    const libService = services.find(s => String(s.idService) === String(formData.idService));
    const libFonction = fonctions.find(f => String(f.idFonction) === String(formData.idFonction));
    const libCategorie = categoriesPro.find(c => String(c.idCategorieProfessionnelle) === String(formData.idCategorieProfessionnelle));
    const libResponsable = responsables.find(r => String(r.idEmploye) === String(formData.idResponsableHierarchique));
    const libModePaie = modesPaie.find(m => String(m.idModeCalculPaie) === String(formData.idModeCalculPaie));

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        if (!formData.idFiliale) return setErrorMsg('Veuillez sélectionner la filiale.');
        if (!formData.idTypeContrat) return setErrorMsg('Veuillez sélectionner le type de contrat.');
        if (!formData.idTypeIntervenant) return setErrorMsg('Veuillez sélectionner le type d\'intervenant.');
        if (!formData.dateDebut) return setErrorMsg('La date de début de contrat est obligatoire.');
        if (exigeDateFin && !formData.dateFin) {
            return setErrorMsg(`La date de fin est obligatoire pour le type de contrat ${selectedTypeContratObj?.libelle || ''}.`);
        }
        if (formData.dateFin && formData.dateDebut && formData.dateFin <= formData.dateDebut) {
            return setErrorMsg('La date de fin de contrat doit être strictement postérieure à la date de début.');
        }
        if (formData.dateEffet && formData.dateDebut && formData.dateEffet < formData.dateDebut) {
            return setErrorMsg('La date d\'effet de l\'affectation ne peut pas être antérieure à la date de début de contrat.');
        }

        if (!formData.idSite) return setErrorMsg('Veuillez sélectionner le site d\'affectation.');
        if (!formData.idDepartement) return setErrorMsg('Veuillez sélectionner le département.');
        if (!formData.idService) return setErrorMsg('Veuillez sélectionner le service.');
        if (!formData.idFonction) return setErrorMsg('Veuillez sélectionner la fonction/poste.');
        if (!formData.idCategorieProfessionnelle) return setErrorMsg('Veuillez sélectionner la catégorie professionnelle.');
        if (!formData.idModeCalculPaie) return setErrorMsg('Veuillez sélectionner le mode de calcul de la paie.');
        if (!formData.salaireBase || Number(formData.salaireBase) <= 0) {
            return setErrorMsg('Le salaire de base est obligatoire et doit être supérieur à 0.');
        }

        setSubmitting(true);
        try {
            const payload = {
                idFiliale: Number(formData.idFiliale),
                idTypeContrat: Number(formData.idTypeContrat),
                idTypeIntervenant: Number(formData.idTypeIntervenant),
                dateDebut: formData.dateDebut,
                dateFin: formData.dateFin || null,
                dateSignature: formData.dateSignature || null,
                dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? Number(formData.dureeEssaiMois) : null,
                dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
                comptabiliseEffectif: Boolean(formData.comptabiliseEffectif),
                numeroContrat: formData.numeroContrat || null,
                description: formData.description || null,
                commentaire: formData.commentaire || null,
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
                dateEffet: formData.dateEffet || formData.dateDebut,
                motif: formData.motif || 'Saisie Embauche Initiale',
            };

            let res;
            if (isEditMode) {
                const brouillonWfId = initialData.idWorkflow || initialData.id_workflow;
                if (brouillonWfId) {
                    res = await contratService.modifierBrouillonWorkflow(brouillonWfId, payload);
                } else {
                    const idContrat = initialData.idContratCible || initialData.idContrat;
                    res = await contratService.modifierBrouillonContrat(idEmploye, idContrat, payload);
                }
            } else {
                res = await contratService.creerBrouillonEmbauche(idEmploye, payload);
            }

            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error('Erreur soumission embauche:', err);
            const errData = err.response?.data;
            const msg = typeof errData === 'string'
                ? errData
                : (errData?.message || errData?.error || err.message || 'Erreur lors de la sauvegarde de l\'embauche.');
            setErrorMsg(msg);
        } finally {
            setSubmitting(false);
        }
    };

    const champsObligatoires = [
        formData.idFiliale, formData.idTypeContrat, formData.idTypeIntervenant, formData.dateDebut,
        formData.idSite, formData.idDepartement, formData.idService, formData.idFonction,
        formData.idCategorieProfessionnelle, formData.idModeCalculPaie, formData.salaireBase,
    ];
    const nbRemplis = champsObligatoires.filter(v => v !== '' && v !== null && v !== undefined).length;
    const progressPct = Math.round((nbRemplis / champsObligatoires.length) * 100);

    const previewProps = {
        libFiliale,
        nomEmploye,
        formData,
        libTypeContrat,
        libTypeIntervenant,
        exigeDateFin,
        libSite,
        libDepartement,
        libService,
        libFonction,
        libCategorie,
        libResponsable,
        libModePaie,
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px]">
            {/* Overlay cliquable pour fermer */}
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
                        <div className="flex items-center gap-2">
                            <Briefcase className="text-brand-500" size={20} />
                            <span className="font-bold text-lg text-ink-900 dark:text-white">
                                {isEditMode ? 'Modifier le Brouillon d\'Embauche' : 'Initialiser l\'Embauche (Nouveau Contrat)'}
                            </span>
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
                                {showPreviewMobile ? 'Masquer l\'aperçu' : 'Voir le contrat'}
                            </button>
                            <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                                <X size={20} />
                            </button>
                        </div>
                    </div>

                    {/* Barre de progression de saisie */}
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

                {/* Corps : Vrai Split Screen 50 / 50 */}
                <div className="flex-1 overflow-hidden flex flex-col lg:flex-row min-h-0">
                    
                    {/* COLONNE GAUCHE (50% de la largeur) : Formulaire */}
                    <form onSubmit={handleSubmit} className="w-full lg:w-1/2 min-w-0 overflow-y-auto p-6 space-y-8 border-b lg:border-b-0 lg:border-r border-ink-150 dark:border-ink-700">
                        {loadingRef ? (
                            <div className="py-12 flex justify-center">
                                <Loading size="md" label="Chargement des tables de référence..." />
                            </div>
                        ) : (
                            <>
                                {errorMsg && (
                                    <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-4 text-xs flex items-center gap-2 font-medium">
                                        <ShieldAlert size={16} className="flex-shrink-0" />
                                        <span>{errorMsg}</span>
                                    </div>
                                )}

                                {/* Aperçu contrat, replié sur mobile */}
                                {showPreviewMobile && <ContractPreview {...previewProps} className="lg:hidden mb-6" />}

                                {/* VOLET 1 : CONTRAT DE TRAVAIL */}
                                <div className="space-y-4">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <Building size={14} /> 1. Informations Juridiques du Contrat
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Input
                                            id="numeroContrat"
                                            name="numeroContrat"
                                            label="N° de Contrat (Optionnel)"
                                            placeholder="Ex: CTR-2026-001"
                                            value={formData.numeroContrat}
                                            onChange={handleChange}
                                        />

                                        <Select
                                            id="idFiliale"
                                            name="idFiliale"
                                            label="Filiale Employeuse *"
                                            required
                                            value={formData.idFiliale}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Filiale --</option>
                                            {filiales.map(f => (
                                                <option key={f.idFiliale} value={f.idFiliale}>
                                                    {f.raisonSociale || f.raison_sociale} ({f.codeFiliale || f.code_filiale})
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idTypeContrat"
                                            name="idTypeContrat"
                                            label="Type de Contrat *"
                                            required
                                            value={formData.idTypeContrat}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Contrat --</option>
                                            {typesContrat.map(t => (
                                                <option key={t.idTypeContrat} value={t.idTypeContrat}>
                                                    {t.libelle} ({t.code})
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idTypeIntervenant"
                                            name="idTypeIntervenant"
                                            label="Type d'Intervenant *"
                                            required
                                            value={formData.idTypeIntervenant}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Profil --</option>
                                            {typesIntervenant.map(ti => (
                                                <option key={ti.idTypeIntervenant} value={ti.idTypeIntervenant}>
                                                    {ti.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Input
                                            id="dateDebut"
                                            name="dateDebut"
                                            label="Date de Début *"
                                            type="date"
                                            required
                                            value={formData.dateDebut}
                                            onChange={handleChange}
                                        />

                                        <Input
                                            id="dateFin"
                                            name="dateFin"
                                            label={`Date de Fin ${exigeDateFin ? '*' : '(Optionnel)'}`}
                                            type="date"
                                            required={exigeDateFin}
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

                                    {/* BLOC PERIODE D'ESSAI */}
                                    <div className="bg-brand-500/5 p-4 rounded-sm border border-brand-500/20 space-y-3">
                                        <div className="flex items-center gap-2">
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
                                                Ce contrat est soumis à une Période d'Essai
                                            </label>
                                        </div>

                                        {formData.aPeriodeEssai && (
                                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-1">
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

                                    <div className="flex items-center gap-2 pt-2">
                                        <input
                                            type="checkbox"
                                            id="comptabiliseEffectif"
                                            name="comptabiliseEffectif"
                                            checked={Boolean(formData.comptabiliseEffectif)}
                                            onChange={handleChange}
                                            className="w-4 h-4 text-brand-500 rounded border-ink-300 focus:ring-brand-500 cursor-pointer"
                                        />
                                        <label htmlFor="comptabiliseEffectif" className="text-xs font-semibold text-ink-800 dark:text-ink-200 cursor-pointer">
                                            Comptabiliser cet employé dans l'effectif légal
                                        </label>
                                    </div>

                                    <Textarea
                                        id="commentaire"
                                        name="commentaire"
                                        label="Remarques ou Notes Contractuelles (Optionnel)"
                                        rows={2}
                                        value={formData.commentaire}
                                        onChange={handleChange}
                                        placeholder="Ex: Contrat signé sous condition suspensive..."
                                    />
                                </div>

                                {/* VOLET 2 : AFFECTATION & CARRIÈRE */}
                                <div className="space-y-4">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <Landmark size={14} /> 2. Affectation Organisationnelle & Remodulation
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            id="idSite"
                                            name="idSite"
                                            label="Site / Implantation *"
                                            required
                                            value={formData.idSite}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Site --</option>
                                            {sites.map(s => (
                                                <option key={s.idSite} value={s.idSite}>
                                                    {s.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idDepartement"
                                            name="idDepartement"
                                            label="Département *"
                                            required
                                            value={formData.idDepartement}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Département --</option>
                                            {departements.map(d => (
                                                <option key={d.idDepartement} value={d.idDepartement}>
                                                    {d.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idService"
                                            name="idService"
                                            label="Service *"
                                            required
                                            value={formData.idService}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Service --</option>
                                            {filteredServices.map(srv => (
                                                <option key={srv.idService} value={srv.idService}>
                                                    {srv.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idFonction"
                                            name="idFonction"
                                            label="Fonction / Poste Occupé *"
                                            required
                                            value={formData.idFonction}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Fonction --</option>
                                            {fonctions.map(f => (
                                                <option key={f.idFonction} value={f.idFonction}>
                                                    {f.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idCategorieProfessionnelle"
                                            name="idCategorieProfessionnelle"
                                            label="Catégorie Professionnelle *"
                                            required
                                            value={formData.idCategorieProfessionnelle}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner Catégorie --</option>
                                            {categoriesPro.map(c => (
                                                <option key={c.idCategorieProfessionnelle} value={c.idCategorieProfessionnelle}>
                                                    {c.libelle} ({c.code})
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            id="idResponsableHierarchique"
                                            name="idResponsableHierarchique"
                                            label="Responsable Hiérarchique (N+1)"
                                            value={formData.idResponsableHierarchique}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Aucun / Supérieur N+1 --</option>
                                            {responsables.map(r => (
                                                <option key={r.idEmploye} value={r.idEmploye}>
                                                    {r.nom} {r.prenom} ({r.matricule})
                                                </option>
                                            ))}
                                        </Select>
                                    </div>
                                </div>

                                {/* VOLET 3 : CONDITIONS FINANCIÈRES & WORKFLOW */}
                                <div className="space-y-4">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                        <DollarSign size={14} /> 3. Conditions Financières & Workflow
                                    </h3>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            id="idModeCalculPaie"
                                            name="idModeCalculPaie"
                                            label="Mode de Calcul de Paie *"
                                            required
                                            value={formData.idModeCalculPaie}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Mode Paie --</option>
                                            {modesPaie.map(m => (
                                                <option key={m.idModeCalculPaie} value={m.idModeCalculPaie}>
                                                    {m.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Input
                                            id="salaireBase"
                                            name="salaireBase"
                                            label="Salaire de Base (MGA) *"
                                            type="number"
                                            step="0.01"
                                            required
                                            placeholder="Ex: 1500000"
                                            value={formData.salaireBase}
                                            onChange={handleChange}
                                        />

                                        <div className="sm:col-span-2">
                                            <Input
                                                id="dateEffet"
                                                name="dateEffet"
                                                label="Date de Prise d'Effet"
                                                type="date"
                                                value={formData.dateEffet}
                                                onChange={handleChange}
                                                placeholder="Par défaut = Date de début de contrat"
                                            />
                                        </div>
                                    </div>

                                    <Textarea
                                        id="motif"
                                        name="motif"
                                        label="Motif de l'Embauche / Justification"
                                        rows={2}
                                        value={formData.motif}
                                        onChange={handleChange}
                                        placeholder="Ex: Première embauche suite au processus de recrutement N° 2025-04"
                                    />
                                </div>
                            </>
                        )}
                    </form>

                    {/* COLONNE DROITE (50% de la largeur) : Aperçu du contrat */}
                    <div className="hidden lg:block w-1/2 flex-shrink-0 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-6 flex flex-col items-center">
                        <div className="w-full max-w-[560px]">
                            <div className="flex items-center gap-2 mb-4 text-xs font-bold uppercase tracking-wider text-ink-400">
                                <FileSignature size={14} /> Aperçu du contrat en temps réel
                            </div>
                            <ContractPreview {...previewProps} />
                        </div>
                    </div>
                </div>

                {/* Footer Actions (fixe) */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                    <p className="text-[11px] text-ink-400">
                        Ces données seront enregistrées à l'état <span className="font-bold text-warning-600">BROUILLON</span> et ne seront actives qu'après validation RH / Direction.
                    </p>
                    <div className="flex justify-end gap-3 flex-shrink-0">
                        <Button type="button" onClick={onClose} variant="outline" size="md">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="primary"
                            size="md"
                            loading={submitting}
                            disabled={loadingRef}
                            iconLeft={<Save size={16} />}
                        >
                            {isEditMode ? 'Enregistrer les Modifications' : 'Enregistrer le Brouillon'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}