import { useState, useEffect, useCallback } from 'react';
import { Briefcase, Building, Landmark, ShieldAlert, Save, X, DollarSign } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import referentielService from '@/api/admin/referentielService';
import employeService from '@/api/employe/employeService';

function fmtDateInput(d) {
    if (!d) return '';
    if (typeof d === 'string') return d.substring(0, 10);
    return new Date(d).toISOString().substring(0, 10);
}

export default function EmbaucheModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    initialData = null, // Si fourni -> mode modification de brouillon
    onSuccess,
}) {
    const isEditMode = !!initialData;

    // Référentiels
    const [loadingRef, setLoadingRef] = useState(true);
    const [filiales, setFiliales] = useState([]);
    const [sites, setSites] = useState([]);
    const [departements, setDepartements] = useState([]);
    const [services, setServices] = useState([]);
    const [fonctions, setFonctions] = useState([]);
    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [responsables, setResponsables] = useState([]);

    // Formulaire
    const [formData, setFormData] = useState({
        // Contrat
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
        // Affectation
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
        // Workflow
        dateEffet: '',
        motif: '',
    });

    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');

    // Contrôle de l'animation d'entrée du panneau (Slide-Over depuis la droite)
    const [mounted, setMounted] = useState(false);

    const loadReferentiels = useCallback(async () => {
        setLoadingRef(true);
        try {
            const [
                filRes, siteRes, depRes, srvRes, fnRes,
                tcRes, tiRes, catRes, paieRes, empRes
            ] = await Promise.all([
                referentielService.getFiliales(true),
                referentielService.getSites(true),
                referentielService.getDepartements(true),
                referentielService.getServices(true),
                referentielService.getFonctions(true),
                referentielService.getTypesContrat(true),
                referentielService.getTypesIntervenant(true),
                contratService.getCategoriesPro(true),
                contratService.getModesPaie(),
                employeService.getEmployes(),
            ]);

            setFiliales(filRes || []);
            setSites(siteRes || []);
            setDepartements(depRes || []);
            setServices(srvRes || []);
            setFonctions(fnRes || []);
            setTypesContrat(tcRes || []);
            setTypesIntervenant(tiRes || []);
            setCategoriesPro(catRes || []);
            setModesPaie(paieRes || []);
            const empArray = Array.isArray(empRes) ? empRes : (empRes?.content || []);
            setResponsables(empArray.filter(e => Number(e.idEmploye || e.id_employe) !== Number(idEmploye)));
        } catch (err) {
            console.error('Erreur chargement référentiels carrière:', err);
            setErrorMsg('Erreur lors du chargement des tables de référence.');
        } finally {
            setLoadingRef(false);
        }
    }, [idEmploye]);

    useEffect(() => {
        if (isOpen) {
            loadReferentiels();
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
    }, [isOpen, loadReferentiels]);

    useEffect(() => {
        if (!initialData) return;

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

        // Fallbacks par libellé si les ID ne sont pas directement présents dans l'objet initialData
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
            const match = responsables.find(r => `${r.nom} ${r.prenom}`.trim() === String(initialData.nomPrenomResponsable).trim());
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
            // Affectation
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
            // Workflow
            dateEffet: fmtDateInput(initialData.dateEffet || initialData.date_effet),
            motif: initialData.motif || '',
        });
    }, [
        initialData, filiales, typesContrat, typesIntervenant, sites,
        departements, services, fonctions, categoriesPro, modesPaie, responsables
    ]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
        }));
    };

    // Services filtrés selon le département sélectionné
    const filteredServices = formData.idDepartement
        ? services.filter(s => String(s.idDepartement || s.id_departement) === String(formData.idDepartement))
        : services;

    // Type de contrat sélectionné
    const selectedTypeContratObj = typesContrat.find(t => String(t.idTypeContrat) === String(formData.idTypeContrat));
    const exigeDateFin = selectedTypeContratObj?.exigeDateFin;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setErrorMsg('');

        // Validations
        if (!formData.idFiliale) return setErrorMsg('Veuillez sélectionner la filiale.');
        if (!formData.idTypeContrat) return setErrorMsg('Veuillez sélectionner le type de contrat.');
        if (!formData.idTypeIntervenant) return setErrorMsg('Veuillez sélectionner le type d\'intervenant.');
        if (!formData.dateDebut) return setErrorMsg('La date de début de contrat est obligatoire.');
        if (exigeDateFin && !formData.dateFin) {
            return setErrorMsg(`La date de fin est obligatoire pour le type de contrat ${selectedTypeContratObj?.libelle || ''}.`);
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
                comptabiliseEffectif: Boolean(formData.comptabiliseEffectif),
                numeroContrat: formData.numeroContrat || null,
                description: formData.description || null,
                commentaire: formData.commentaire || null,
                // Affectation
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
                // Workflow
                dateEffet: formData.dateEffet || formData.dateDebut,
                motif: formData.motif || 'Saisie Embauche Initiale',
            };

            let res;
            if (isEditMode) {
                const idContrat = initialData.idContratCible || initialData.idContrat;
                res = await contratService.modifierBrouillonContrat(idEmploye, idContrat, payload);
            } else {
                res = await contratService.creerBrouillonEmbauche(idEmploye, payload);
            }

            if (onSuccess) onSuccess(res);
            onClose();
        } catch (err) {
            console.error('Erreur soumission embauche:', err);
            setErrorMsg(err.response?.data || 'Erreur lors de la sauvegarde de l\'embauche.');
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-[2px]">
            {/* Overlay cliquable pour fermer */}
            <div className="absolute inset-0" onClick={onClose} />

            {/* Panneau Slide-Over depuis la droite */}
            <div
                className={`
                    relative bg-white dark:bg-ink-900 border-l border-ink-150 dark:border-ink-700 shadow-2xl
                    w-full max-w-4xl h-full overflow-hidden flex flex-col
                    transform transition-transform duration-300 ease-out
                    ${mounted ? 'translate-x-0' : 'translate-x-full'}
                `}
            >

                {/* Header (fixe) */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center justify-between bg-ink-50/50 dark:bg-ink-950/40 flex-shrink-0">
                    <div className="flex items-center gap-2">
                        <Briefcase className="text-brand-500" size={20} />
                        <span>{isEditMode ? 'Modifier le Brouillon d\'Embauche' : 'Initialiser l\'Embauche (Nouveau Contrat)'}</span>
                        {nomEmploye && (
                            <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                        )}
                    </div>
                    <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-sm">
                        <X size={18} />
                    </button>
                </div>

                {/* Form Content */}
                <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-8">
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

                            {/* VOLET 1 : CONTRAT DE TRAVAIL */}
                            <div className="space-y-4">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-brand-500 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                    <Building size={14} /> 1. Informations Juridiques du Contrat
                                </h3>

                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                                    <Input
                                        id="numeroContrat"
                                        name="numeroContrat"
                                        label="N° de Contrat (Optionnel)"
                                        placeholder="Ex: CTR-2026-001 (auto-généré si vide)"
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

                                <div className="flex items-center gap-2 pt-2">
                                    <input
                                        type="checkbox"
                                        id="comptabiliseEffectif"
                                        name="comptabiliseEffectif"
                                        checked={formData.comptabiliseEffectif}
                                        onChange={handleChange}
                                        className="w-4 h-4 text-brand-500 rounded border-ink-300 focus:ring-brand-500"
                                    />
                                    <label htmlFor="comptabiliseEffectif" className="text-xs font-semibold text-ink-800 dark:text-ink-200 cursor-pointer">
                                        Comptabiliser cet employé dans l'effectif légal de l'entreprise
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

                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
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

                                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
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

                                    <Input
                                        id="dateEffet"
                                        name="dateEffet"
                                        label="Date d'Effet du Workflow"
                                        type="date"
                                        value={formData.dateEffet}
                                        onChange={handleChange}
                                        placeholder="Par défaut = Date début contrat"
                                    />
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

                {/* Footer Actions (fixe) */}
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                    <p className="text-[11px] text-ink-400 italic">
                        Ces données seront enregistrées à l'état <span className="font-bold text-warning-600">BROUILLON</span> et ne seront actives qu'après validation DAF.
                    </p>
                    <div className="flex justify-end gap-3 flex-shrink-0">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="primary"
                            size="sm"
                            loading={submitting}
                            disabled={loadingRef}
                            iconLeft={<Save size={16} />}
                        >
                            {isEditMode ? 'Enregistrer les Modifications' : 'Enregistrer le Brouillon DAF'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}