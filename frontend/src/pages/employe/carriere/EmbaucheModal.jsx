import { useState, useEffect, useCallback } from 'react';
import { Briefcase, Building, Landmark, ShieldAlert, Save, X, DollarSign, FileSignature, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/Button';
import Input, { Textarea, Select } from '@/components/ui/Input';
import Loading from '@/components/ui/Loading';
import contratService from '@/api/employe/contratService';
import referentielService from '@/api/admin/referentielService';
import employeService from '@/api/employe/employeService';
import { Clause, ArticleHeader, DocumentFrame, DocumentHeader, ArticleBlock, DocumentFooter } from '@/components/carriere/ContractDocument';
import { fmtDateInput, fmtDateFr, fmtMoneyMGA as fmtMoney, resolveId } from '@/utils/carriereUtils';

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
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);

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
        setShowPreviewMobile(false);
    }, [isOpen, loadReferentiels]);

    // Remplissage en mode modification.
    // IMPORTANT : l'objet initialData peut être soit un brouillon "brut" (avec idFiliale, idSite...),
    // soit une vue de workflow déjà enrichie qui ne porte que des libellés résolus
    // (libelleFiliale, libelleSite...). On attend que les référentiels soient chargés
    // pour pouvoir retrouver l'id correspondant à un libellé quand l'id brut est absent.
    useEffect(() => {
        if (!initialData || loadingRef) return;

        const idFiliale = resolveId(filiales, initialData.idFiliale ?? initialData.id_filiale, 'idFiliale', initialData.libelleFiliale, ['raisonSociale', 'raison_sociale', 'libelle']);
        const idTypeContrat = resolveId(typesContrat, initialData.idTypeContrat ?? initialData.id_type_contrat, 'idTypeContrat', initialData.libelleTypeContrat, ['libelle']);
        const idTypeIntervenant = resolveId(typesIntervenant, initialData.idTypeIntervenant ?? initialData.id_type_intervenant, 'idTypeIntervenant', initialData.libelleTypeIntervenant, ['libelle']);
        const idSite = resolveId(sites, initialData.idSite ?? initialData.id_site, 'idSite', initialData.libelleSite, ['libelle']);
        const idDepartement = resolveId(departements, initialData.idDepartement ?? initialData.id_departement, 'idDepartement', initialData.libelleDepartement, ['libelle']);
        const idService = resolveId(services, initialData.idService ?? initialData.id_service, 'idService', initialData.libelleService, ['libelle']);
        const idFonction = resolveId(fonctions, initialData.idFonction ?? initialData.id_fonction, 'idFonction', initialData.libelleFonction, ['libelle']);
        const idCategorieProfessionnelle = resolveId(categoriesPro, initialData.idCategorieProfessionnelle ?? initialData.id_categorie_professionnelle, 'idCategorieProfessionnelle', initialData.libelleCategoriePro, ['libelle']);
        const idModeCalculPaie = resolveId(modesPaie, initialData.idModeCalculPaie ?? initialData.id_mode_calcul_paie, 'idModeCalculPaie', initialData.libelleModeCalculPaie, ['libelle']);
        const idResponsableHierarchique = resolveId(
            responsables,
            initialData.idResponsableHierarchique ?? initialData.id_responsable_hierarchique,
            'idEmploye',
            initialData.nomPrenomResponsable,
            // nom+prenom concaténés côté back -> on tente une correspondance sur "nom prenom"
        ) || (() => {
            const full = initialData.nomPrenomResponsable;
            if (!full) return '';
            const found = responsables.find(r => `${r.nom} ${r.prenom}`.trim().toLowerCase() === String(full).trim().toLowerCase());
            return found ? String(found.idEmploye) : '';
        })();

        setFormData({
            idFiliale,
            idTypeContrat,
            idTypeIntervenant,
            dateDebut: fmtDateInput(initialData.dateDebutContrat || initialData.dateDebut || initialData.date_debut),
            dateFin: fmtDateInput(initialData.dateFinContrat || initialData.dateFin || initialData.date_fin),
            dateSignature: fmtDateInput(initialData.dateSignature || initialData.date_signature),
            comptabiliseEffectif: initialData.comptabiliseEffectif !== undefined ? initialData.comptabiliseEffectif : true,
            numeroContrat: initialData.numeroContratCible || initialData.numeroContrat || '',
            description: initialData.description || '',
            commentaire: initialData.commentaire || '',
            // Affectation
            idSite,
            idDepartement,
            idService,
            idFonction,
            idResponsableHierarchique,
            idCategorieProfessionnelle,
            idModeCalculPaie,
            salaireBase: initialData.salaireBase ?? initialData.salaire_base ?? '',
            descriptionAffectation: initialData.descriptionAffectation || '',
            commentaireAffectation: initialData.commentaireAffectation || '',
            // Workflow
            dateEffet: fmtDateInput(initialData.dateEffet || initialData.date_effet),
            motif: initialData.motif || '',
        });
    }, [initialData, loadingRef, filiales, sites, departements, services, fonctions, typesContrat, typesIntervenant, categoriesPro, modesPaie, responsables]);

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value,
            // Si on change le département, le service précédemment choisi peut ne plus être valide
            ...(name === 'idDepartement' ? { idService: '' } : {}),
        }));
    };

    // Services filtrés selon le département sélectionné
    const filteredServices = formData.idDepartement
        ? services.filter(s => String(s.idDepartement || s.id_departement) === String(formData.idDepartement))
        : services;

    // Type de contrat sélectionné
    const selectedTypeContratObj = typesContrat.find(t => String(t.idTypeContrat) === String(formData.idTypeContrat));
    const exigeDateFin = selectedTypeContratObj?.exigeDateFin;

    // --- Libellés dérivés pour l'aperçu contrat (les <select> ne stockent que des ids) ---
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

        // Validations
        if (!formData.idFiliale) return setErrorMsg('Veuillez sélectionner la filiale.');
        if (!formData.idTypeContrat) return setErrorMsg('Veuillez sélectionner le type de contrat.');
        if (!formData.idTypeIntervenant) return setErrorMsg('Veuillez sélectionner le type d\'intervenant.');
        
        // Validation de cohérence Stagiaire / Stage
        const selectedTypeIntervenantObj = typesIntervenant.find(t => String(t.idTypeIntervenant) === String(formData.idTypeIntervenant));
        if (selectedTypeIntervenantObj?.code === 'STAGIAIRE' && selectedTypeContratObj?.code !== 'STAGE') {
            return setErrorMsg('Un intervenant Stagiaire doit obligatoirement être sous un contrat de type Convention / Contrat de Stage.');
        }
        if (selectedTypeIntervenantObj?.code !== 'STAGIAIRE' && selectedTypeContratObj?.code === 'STAGE') {
            return setErrorMsg('Le contrat de Stage est réservé aux stagiaires.');
        }

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
        } finally {
            setSubmitting(false);
        }
    };

    // Complétude globale du formulaire, pour donner un retour visuel dans l'aperçu
    const champsObligatoires = [
        formData.idFiliale, formData.idTypeContrat, formData.idTypeIntervenant, formData.dateDebut,
        formData.idSite, formData.idDepartement, formData.idService, formData.idFonction,
        formData.idCategorieProfessionnelle, formData.idModeCalculPaie, formData.salaireBase,
    ];
    const nbRemplis = champsObligatoires.filter(v => v !== '' && v !== null && v !== undefined).length;
    const progressPct = Math.round((nbRemplis / champsObligatoires.length) * 100);

    if (!isOpen) return null;

    const ContractPreview = ({ className = '' }) => (
        <DocumentFrame className={className}>
            <DocumentHeader
                eyebrow={libFiliale?.raisonSociale || libFiliale?.raison_sociale || 'Filiale employeuse'}
                title="Contrat de Travail"
                numero={formData.numeroContrat}
            />

            <div className="pt-4 text-[13px] leading-relaxed space-y-3">
                <p>
                    Entre <strong>{libFiliale?.raisonSociale || libFiliale?.raison_sociale || '……………'}</strong>, ci-après « l'Employeur »,
                    {' '}et <strong>{nomEmploye || '……………'}</strong>, ci-après « le Salarié », il est convenu ce qui suit :
                </p>

                <ArticleBlock n="1" title="Nature & Durée">
                    <Clause label="Type de contrat" value={libTypeContrat?.libelle} />
                    <Clause label="Profil d'intervenant" value={libTypeIntervenant?.libelle} />
                    <Clause label="Prise d'effet" value={fmtDateFr(formData.dateDebut)} />
                    <Clause label="Terme" value={fmtDateFr(formData.dateFin)} placeholder={exigeDateFin ? '……………………' : 'Durée indéterminée'} />
                    <Clause label="Signature le" value={fmtDateFr(formData.dateSignature)} />
                    <Clause label="Effectif légal" value={formData.comptabiliseEffectif ? 'Salarié comptabilisé' : 'Hors effectif'} />
                </ArticleBlock>

                <ArticleBlock n="2" title="Affectation">
                    <Clause label="Site" value={libSite?.libelle} />
                    <Clause label="Département" value={libDepartement?.libelle} />
                    <Clause label="Service" value={libService?.libelle} />
                    <Clause label="Fonction occupée" value={libFonction?.libelle} />
                    <Clause label="Catégorie pro." value={libCategorie?.libelle} />
                    <Clause label="Rattaché à" value={libResponsable ? `${libResponsable.nom} ${libResponsable.prenom}` : ''} placeholder="Aucun N+1" />
                </ArticleBlock>

                <ArticleBlock n="3" title="Rémunération">
                    <Clause label="Mode de calcul" value={libModePaie?.libelle} />
                    <Clause label="Salaire de base" value={formData.salaireBase ? fmtMoney(formData.salaireBase) : ''} className="text-brand-500" />
                    <Clause label="Date d'effet (dossier)" value={fmtDateFr(formData.dateEffet || formData.dateDebut)} />
                </ArticleBlock>

                {formData.motif && (
                    <div className="pt-2">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-brand-500">Article 4 — Motif</p>
                        <p className="text-ink-600 dark:text-ink-300 italic text-[12.5px]">{formData.motif}</p>
                    </div>
                )}
            </div>

            <DocumentFooter />
        </DocumentFrame>
    );

    return (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/50 backdrop-blur-[2px]">
            {/* Overlay cliquable pour fermer */}
            <div className="absolute inset-0" onClick={onClose} />

            {/* Panneau Slide-Over depuis la droite */}
            <div
                className={`
                    relative bg-white dark:bg-ink-900 border-l border-ink-150 dark:border-ink-700 shadow-2xl
                    w-full max-w-6xl h-full overflow-hidden flex flex-col
                    transform transition-transform duration-300 ease-out
                    ${mounted ? 'translate-x-0' : 'translate-x-full'}
                `}
            >
                {/* Header (fixe) */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/40 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <Briefcase className="text-brand-500" size={20} />
                            <span className="font-bold text-lg text-ink-900 dark:text-white">
                                {isEditMode ? 'Modifier le Brouillon d\'Embauche' : 'Initialiser l\'Embauche'}
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
                                <X size={18} />
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

                {/* Corps : formulaire + aperçu contrat côte à côte */}
                <div className="flex-1 overflow-hidden flex flex-col lg:flex-row min-h-0">

                    {/* Colonne formulaire */}
                    <form onSubmit={handleSubmit} className="flex-1 min-w-0 overflow-y-auto p-6 space-y-6">
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
                                {showPreviewMobile && <ContractPreview className="lg:hidden" />}

                                {/* ARTICLE 1 : CONTRAT DE TRAVAIL */}
                                <div className="space-y-4">
                                    <ArticleHeader
                                        n={1}
                                        icon={<Building size={14} />}
                                        title="Nature & Durée du Contrat"
                                        subtitle="Employeur, type de contrat et dates clés de la relation de travail."
                                    />

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            label="Filiale employeuse"
                                            name="idFiliale"
                                            required
                                            value={formData.idFiliale}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner la filiale --</option>
                                            {filiales.map(f => (
                                                <option key={f.idFiliale} value={f.idFiliale}>
                                                    {f.raisonSociale || f.raison_sociale} ({f.codeFiliale || f.code_filiale})
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Type de contrat"
                                            name="idTypeContrat"
                                            required
                                            value={formData.idTypeContrat}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le type --</option>
                                            {typesContrat.map(t => (
                                                <option key={t.idTypeContrat} value={t.idTypeContrat}>
                                                    {t.libelle} ({t.code})
                                                </option>
                                            ))}
                                        </Select>

                                        <div className="sm:col-span-2">
                                            <Select
                                                label="Profil d'intervenant"
                                                name="idTypeIntervenant"
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
                                            <p className="text-[10px] text-ink-400 italic mt-1">Détermine le régime applicable (salarié classique, stagiaire, consultant...).</p>
                                        </div>

                                        <Input
                                            label="Date de début du contrat"
                                            type="date"
                                            name="dateDebut"
                                            required
                                            value={formData.dateDebut}
                                            onChange={handleChange}
                                        />

                                        <Input
                                            label={`Date de fin du contrat ${exigeDateFin ? '(obligatoire)' : '(optionnelle)'}`}
                                            type="date"
                                            name="dateFin"
                                            required={exigeDateFin}
                                            value={formData.dateFin}
                                            onChange={handleChange}
                                        />

                                        <div className="sm:col-span-2">
                                            <Input
                                                label="Date de signature du contrat"
                                                type="date"
                                                name="dateSignature"
                                                value={formData.dateSignature}
                                                onChange={handleChange}
                                            />
                                        </div>
                                    </div>

                                    <label htmlFor="comptabiliseEffectif" className="flex items-start gap-2 pt-1 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            id="comptabiliseEffectif"
                                            name="comptabiliseEffectif"
                                            checked={formData.comptabiliseEffectif}
                                            onChange={handleChange}
                                            className="w-4 h-4 mt-0.5 text-brand-500 rounded border-ink-300 focus:ring-brand-500"
                                        />
                                        <span>
                                            <span className="text-xs font-semibold text-ink-800 dark:text-ink-200 block">
                                                Comptabiliser cet employé dans l'effectif légal de l'entreprise
                                            </span>
                                            <span className="text-[10px] text-ink-400 italic">
                                                À décocher pour les stagiaires ou consultants non salariés.
                                            </span>
                                        </span>
                                    </label>
                                </div>

                                {/* ARTICLE 2 : AFFECTATION */}
                                <div className="space-y-4 pt-2">
                                    <ArticleHeader
                                        n={2}
                                        icon={<Landmark size={14} />}
                                        title="Affectation & Poste"
                                        subtitle="Où et pour quel poste le salarié est affecté."
                                    />

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            label="Site d'affectation"
                                            name="idSite"
                                            required
                                            value={formData.idSite}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le site --</option>
                                            {sites.map(s => (
                                                <option key={s.idSite} value={s.idSite}>
                                                    {s.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Département"
                                            name="idDepartement"
                                            required
                                            value={formData.idDepartement}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le département --</option>
                                            {departements.map(d => (
                                                <option key={d.idDepartement} value={d.idDepartement}>
                                                    {d.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Service"
                                            name="idService"
                                            required
                                            value={formData.idService}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le service --</option>
                                            {filteredServices.map(srv => (
                                                <option key={srv.idService} value={srv.idService}>
                                                    {srv.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Fonction / poste occupé"
                                            name="idFonction"
                                            required
                                            value={formData.idFonction}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner la fonction --</option>
                                            {fonctions.map(f => (
                                                <option key={f.idFonction} value={f.idFonction}>
                                                    {f.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Catégorie professionnelle"
                                            name="idCategorieProfessionnelle"
                                            required
                                            value={formData.idCategorieProfessionnelle}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner la catégorie --</option>
                                            {categoriesPro.map(c => (
                                                <option key={c.idCategorieProfessionnelle} value={c.idCategorieProfessionnelle}>
                                                    {c.libelle} ({c.code})
                                                </option>
                                            ))}
                                        </Select>

                                        <div>
                                            <Select
                                                label="Responsable hiérarchique (N+1)"
                                                name="idResponsableHierarchique"
                                                value={formData.idResponsableHierarchique}
                                                onChange={handleChange}
                                            >
                                                <option value="">-- Aucun N+1 --</option>
                                                {responsables.map(r => (
                                                    <option key={r.idEmploye} value={r.idEmploye}>
                                                        {r.nom} {r.prenom} ({r.matricule})
                                                    </option>
                                                ))}
                                            </Select>
                                        </div>
                                    </div>
                                </div>

                                {/* ARTICLE 3 : RÉMUNÉRATION & WORKFLOW */}
                                <div className="space-y-4 pt-2">
                                    <ArticleHeader
                                        n={3}
                                        icon={<DollarSign size={14} />}
                                        title="Rémunération & Prise d'effet"
                                        subtitle="Conditions financières et déclenchement du workflow RH."
                                    />

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Select
                                            label="Mode de calcul de la paie"
                                            name="idModeCalculPaie"
                                            required
                                            value={formData.idModeCalculPaie}
                                            onChange={handleChange}
                                        >
                                            <option value="">-- Sélectionner le mode --</option>
                                            {modesPaie.map(m => (
                                                <option key={m.idModeCalculPaie} value={m.idModeCalculPaie}>
                                                    {m.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Input
                                            label="Salaire de base (MGA)"
                                            type="number"
                                            step="0.01"
                                            name="salaireBase"
                                            required
                                            placeholder="Ex : 1 500 000"
                                            value={formData.salaireBase}
                                            onChange={handleChange}
                                        />

                                        <div className="sm:col-span-2">
                                            <Input
                                                label="Date d'effet du workflow"
                                                type="date"
                                                name="dateEffet"
                                                value={formData.dateEffet}
                                                onChange={handleChange}
                                                placeholder="Par défaut : date de début du contrat"
                                            />
                                            <p className="text-[10px] text-ink-400 italic mt-1">
                                                Date à laquelle le contrat deviendra actif une fois validé par la DAF. Laisser vide pour reprendre la date de début.
                                            </p>
                                        </div>
                                    </div>

                                    <Textarea
                                        label="Motif de l'embauche"
                                        rows={2}
                                        name="motif"
                                        value={formData.motif}
                                        onChange={handleChange}
                                        placeholder="Ex : Première embauche suite au processus de recrutement N° 2025-04"
                                    />
                                </div>
                            </>
                        )}
                    </form>

                    {/* Colonne aperçu contrat (desktop) */}
                    <div className="hidden lg:block w-[400px] flex-shrink-0 border-l border-ink-150 dark:border-ink-700 bg-ink-50/60 dark:bg-ink-950/40 overflow-y-auto p-5">
                        <div className="flex items-center gap-2 mb-3 text-xs font-bold uppercase tracking-wider text-ink-400">
                            <FileSignature size={14} /> Aperçu du contrat en temps réel
                        </div>
                        <ContractPreview />
                    </div>
                </div>

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
