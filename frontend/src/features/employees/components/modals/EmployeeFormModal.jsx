import FormError from '@/components/ui/feedback/FormError';
import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { User, CreditCard, Award, Phone, Users, Shield, ShieldAlert, Landmark, Building, Check, ArrowRight, ArrowLeft } from 'lucide-react';
import organisationService from '@/features/organization/api/organisationService';
import employeService from '@/features/employees/api/employeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import Input, { AutocompleteInput } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { SkeletonForm } from '@/components/ui/feedback/Skeleton';
import { AGENCES_BANCAIRES, VILLES_MADAGASCAR, NATIONALITES_STANDARDS } from '@/constants/madagascarGeo';

// Lazy loading des étapes du formulaire collaborateur pour optimiser la taille du bundle et le temps de chargement
const CiviliteStep = lazy(() => import('@/features/employees/components/form/CiviliteStep'));
const CoordonneesPhotoStep = lazy(() => import('@/features/employees/components/form/CoordonneesPhotoStep'));
const FamilleFormStep = lazy(() => import('@/features/employees/components/form/FamilleFormStep'));
const UrgenceFormStep = lazy(() => import('@/features/employees/components/form/UrgenceFormStep'));

import { formaterDateEmploye, validerEtapeEmploye, validerToutesEtapesEmploye } from '@/features/employees/utils/employeFormValidation';

const STEPS = [
    { id: 'civilite', label: 'Identité', icon: User, required: true },
    { id: 'cin', label: 'CIN & Permis', icon: CreditCard, required: true },
    { id: 'coordonnees', label: 'Coordonnées & Photo', icon: Phone, required: true },
    { id: 'famille', label: 'Famille', icon: Users, required: false },
    { id: 'urgence', label: 'Urgence', icon: ShieldAlert, required: false },
    { id: 'banque', label: 'CNaPS & Banque', icon: Building, required: false }
];

const EMPTY_FORM = {
    matricule: '', nom: '', prenom: '', sexe: 'M',
    dateNaissance: '', lieuNaissance: '', nationalite: 'Malgache',
    pereNomPrenom: '', mereNomPrenom: '', situationFamiliale: 'Celibataire',
    cinNumero: '', cinDateDelivrance: '', cinLieuDelivrance: '',
    cinDuplicataDate: '', cinDuplicataLieu: '',
    permisConduireNumero: '', permisConduireDateDelivrance: '', permisConduireLieuDelivrance: '',
    adresse: '', ville: '', telephone: '', email: '',
    numAffiliationCnaps: '', photoUrl: '', dateDeclaration: '',
    conjoint: { nom: '', prenom: '', dateNaissance: '', lieuNaissance: '', societe: '', fonction: '' },
    enfants: [],
    contactsUrgence: [{ nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
    compteBanquePrincipal: { idBanque: '', agence: '', numCompteRib: '', titulaireCompte: '' }
};

// Formatage de date délégué à l'utilitaire pur (défini au niveau module pour la stabilité des hooks)
const fmtDate = formaterDateEmploye;

export default function EmployeeFormModal({ isOpen, onClose, onSave, employeData, isSubmitting }) {
    // État local : progression, données du formulaire, photo et génération du matricule.
    const [activeStep, setActiveStep] = useState(0);
    const [banques, setBanques] = useState([]);
    const [errorMsg, setErrorMsg] = useState('');
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const [customNationalite, setCustomNationalite] = useState('');
    const [isCustomNat, setIsCustomNat] = useState(false);

    const [generatingMatricule, setGeneratingMatricule] = useState(false);
    const [matriculeError, setMatriculeError] = useState('');

    const [formData, setFormData] = useState({ ...EMPTY_FORM });

    const loadBanques = async () => {
        try {
            const data = await organisationService.getBanques();
            setBanques(data || []);
        } catch (err) {
            console.error('Erreur chargement banques:', err);
        }
    };

    // Effets : initialisent le formulaire et chargent les banques à l'ouverture.
    useEffect(() => {
        loadBanques();
    }, []);

    useEffect(() => {
        if (employeData) {
            const e = employeData.employe || employeData;
            const nat = e.nationalite || 'Malgache';
            const isKnownNat = NATIONALITES_STANDARDS.includes(nat);

            if (!isKnownNat && nat) {
                setIsCustomNat(true);
                setCustomNationalite(nat);
            } else {
                setIsCustomNat(false);
                setCustomNationalite('');
            }

            setFormData({
                matricule: e.matricule || '',
                nom: e.nom || '',
                prenom: e.prenom || '',
                sexe: e.sexe || 'M',
                dateNaissance: fmtDate(e.dateNaissance),
                lieuNaissance: e.lieuNaissance || '',
                nationalite: isKnownNat ? nat : 'Autre',
                pereNomPrenom: e.pereNomPrenom || '',
                mereNomPrenom: e.mereNomPrenom || '',
                situationFamiliale: e.situationFamiliale || 'Celibataire',
                cinNumero: e.cinNumero || '',
                cinDateDelivrance: fmtDate(e.cinDateDelivrance),
                cinLieuDelivrance: e.cinLieuDelivrance || '',
                cinDuplicataDate: fmtDate(e.cinDuplicataDate),
                cinDuplicataLieu: e.cinDuplicataLieu || '',
                permisConduireNumero: e.permisConduireNumero || '',
                permisConduireDateDelivrance: fmtDate(e.permisConduireDateDelivrance),
                permisConduireLieuDelivrance: e.permisConduireLieuDelivrance || '',
                adresse: e.adresse || '',
                ville: e.ville || '',
                telephone: e.telephone || '',
                email: e.email || '',
                numAffiliationCnaps: e.numAffiliationCnaps || '',
                photoUrl: e.photoUrl || '',
                dateDeclaration: fmtDate(e.dateDeclaration),
                conjoint: {
                    nom: employeData.conjoint?.nom || e.conjointNom || '',
                    prenom: employeData.conjoint?.prenom || e.conjointPrenom || '',
                    dateNaissance: fmtDate(employeData.conjoint?.dateNaissance),
                    lieuNaissance: employeData.conjoint?.lieuNaissance || '',
                    societe: employeData.conjoint?.societe || e.conjointSociete || '',
                    fonction: employeData.conjoint?.fonction || e.conjointFonction || ''
                },
                enfants: (employeData.enfants || []).map(enf => ({
                    nom: enf.nom || '',
                    prenom: enf.prenom || '',
                    sexe: enf.sexe || 'M',
                    dateNaissance: fmtDate(enf.dateNaissance),
                    lieuNaissance: enf.lieuNaissance || '',
                    aCharge: enf.aCharge !== undefined ? enf.aCharge : true,
                    estEtudiant: enf.estEtudiant || false,
                    dateFinScolarite: fmtDate(enf.dateFinScolarite),
                    estHandicape: enf.estHandicape || false,
                    certificatFourni: enf.certificatFourni || false
                })),
                contactsUrgence: (employeData.contactsUrgence?.length > 0)
                    ? employeData.contactsUrgence.map(ct => ({
                        nom: ct.nom || '',
                        prenom: ct.prenom || '',
                        lienParente: ct.lienParente || 'Autre',
                        telephone: ct.telephone || '',
                        adresse: ct.adresse || '',
                        societe: ct.societe || ''
                    }))
                    : [{ nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
                compteBanquePrincipal: employeData.comptesBancaires?.find(c => c.estPrincipal) || {
                    idBanque: '', agence: '', numCompteRib: '', titulaireCompte: ''
                }
            });
        } else {
            setFormData({ ...EMPTY_FORM });
            setIsCustomNat(false);
            setCustomNationalite('');
        }
        setActiveStep(0);
        setErrorMsg('');
        setMatriculeError('');
    }, [employeData, isOpen]);

    const set = useCallback((field, val) => {
        setFormData(prev => ({ ...prev, [field]: val }));
    }, []);

    const setNested = useCallback((parent, field, val) => {
        setFormData(prev => ({
            ...prev,
            [parent]: { ...prev[parent], [field]: val }
        }));
    }, []);

    const addEnfant = useCallback(() => {
        setFormData(prev => ({
            ...prev,
            enfants: [
                ...prev.enfants,
                {
                    nom: '',
                    prenom: '',
                    sexe: 'M',
                    dateNaissance: '',
                    lieuNaissance: '',
                    aCharge: true,
                    estEtudiant: false,
                    dateFinScolarite: '',
                    estHandicape: false,
                    certificatFourni: false
                }
            ]
        }));
    }, []);

    const removeEnfant = useCallback((index) => {
        setFormData(prev => ({
            ...prev,
            enfants: prev.enfants.filter((_, i) => i !== index)
        }));
    }, []);

    const setEnfant = useCallback((index, field, val) => {
        setFormData(prev => {
            const copy = [...prev.enfants];
            copy[index] = { ...copy[index], [field]: val };
            return { ...prev, enfants: copy };
        });
    }, []);

    const addContact = useCallback(() => {
        setFormData(prev => ({
            ...prev,
            contactsUrgence: [...prev.contactsUrgence, { nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }]
        }));
    }, []);

    const removeContact = useCallback((index) => {
        setFormData(prev => ({
            ...prev,
            contactsUrgence: prev.contactsUrgence.filter((_, i) => i !== index)
        }));
    }, []);

    const setContact = useCallback((index, field, val) => {
        setFormData(prev => {
            const copy = [...prev.contactsUrgence];
            copy[index] = { ...copy[index], [field]: val };
            return { ...prev, contactsUrgence: copy };
        });
    }, []);

    const handlePhotoSelect = async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (file.size > 5 * 1024 * 1024) {
            setErrorMsg("La taille du fichier photo ne doit pas dépasser 5 Mo.");
            return;
        }

        setUploadingPhoto(true);
        setErrorMsg('');
        try {
            const res = await employeService.uploadPhoto(file);
            set('photoUrl', res.url || res.path || res.photoUrl);
        } catch (err) {
            console.error("Erreur téléversement photo:", err);
            setErrorMsg("Impossible de téléverser la photo.");
        } finally {
            setUploadingPhoto(false);
        }
    };

    const handleGenerateMatricule = async () => {
        setGeneratingMatricule(true);
        setMatriculeError('');
        try {
            const nextMat = await employeService.getNextMatricule();
            if (nextMat) {
                set('matricule', nextMat);
            }
        } catch (err) {
            console.error("Erreur génération matricule:", err);
            setMatriculeError("Génération automatique indisponible.");
        } finally {
            setGeneratingMatricule(false);
        }
    };

    const handleBlurMatricule = async () => {
        const mat = formData.matricule?.trim();
        if (!mat) return;

        if (employeData) {
            const oldMat = (employeData.employe || employeData).matricule;
            if (oldMat === mat) {
                setMatriculeError('');
                return;
            }
        }

        try {
            const isAvailable = await employeService.checkMatriculeAvailable(mat);
            if (!isAvailable) {
                setMatriculeError("Ce matricule est déjà attribué.");
            } else {
                setMatriculeError('');
            }
        } catch (err) {
            console.error("Erreur vérification matricule:", err);
        }
    };

    const handleNationaliteChange = (e) => {
        const val = e.target.value;
        if (val === 'Autre') {
            setIsCustomNat(true);
            set('nationalite', 'Autre');
        } else {
            setIsCustomNat(false);
            set('nationalite', val);
        }
    };

    const validateStep = (stepIdx) => {
        setErrorMsg('');
        const stepId = STEPS[stepIdx].id;
        const err = validerEtapeEmploye(stepId, formData, matriculeError, { customNationalite, isCustomNat });
        if (err) {
            setErrorMsg(err);
            return false;
        }
        return true;
    };

    const handleNext = () => {
        if (activeStep >= STEPS.length - 1) return;
        if (validateStep(activeStep)) {
            setActiveStep(prev => Math.min(prev + 1, STEPS.length - 1));
        }
    };

    const handlePrev = () => {
        setActiveStep(prev => Math.max(prev - 1, 0));
        setErrorMsg('');
    };

    const handleSave = async (e) => {
        if (e && e.preventDefault) e.preventDefault();

        // En création, la touche Entrée avant le dernier onglet avance simplement d'une étape
        if (!employeData && activeStep < STEPS.length - 1) {
            handleNext();
            return;
        }

        if (isSubmitting) return;

        // Validation intégrale et exhaustive de l'ensemble des étapes
        const validationResult = validerToutesEtapesEmploye(
            STEPS,
            formData,
            matriculeError,
            { customNationalite, isCustomNat }
        );

        if (validationResult) {
            setActiveStep(validationResult.stepIndex);
            setErrorMsg(validationResult.error);
            return;
        }

        setErrorMsg('');
        const finalNat = isCustomNat ? customNationalite : formData.nationalite;

        try {
            await onSave({
                ...formData,
                nationalite: finalNat,
                numAffiliationCnaps: formData.numAffiliationCnaps?.trim() || null,
                cinNumero: formData.cinNumero?.trim() || null,
                permisConduireNumero: formData.permisConduireNumero?.trim() || null,
                email: formData.email?.trim() || null
            });
        } catch (err) {
            console.error("Erreur soumission formulaire employé:", err);
            const msg = getErrorMessage(err, "Erreur lors de l'enregistrement de la fiche.");
            setErrorMsg(msg);
        }
    };

    // Rendu
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={employeData ? `Modifier la Fiche — ${formData.matricule}` : 'Nouveau Collaborateur'}
            icon={<User size={18} />}
            size="3xl"
            footer={
                <div className="flex justify-between items-center w-full">
                    <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={isSubmitting}>
                        Annuler
                    </Button>
                    <div className="flex items-center gap-2">
                        {activeStep > 0 && (
                            <Button
                                key="wizard-prev-btn"
                                type="button"
                                onClick={handlePrev}
                                variant="outline"
                                size="sm"
                                iconLeft={<ArrowLeft size={14} />}
                                disabled={isSubmitting}
                            >
                                {STEPS[activeStep - 1]?.label}
                            </Button>
                        )}
                        {/* Bouton de sauvegarde directe en mode modification */}
                        {employeData && activeStep < STEPS.length - 1 && (
                            <Button
                                key="wizard-quick-save-btn"
                                type="button"
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    handleSave(e);
                                }}
                                variant="outline"
                                size="sm"
                                loading={isSubmitting}
                                iconLeft={<Check size={14} />}
                            >
                                Enregistrer
                            </Button>
                        )}
                        {activeStep < STEPS.length - 1 ? (
                            <Button
                                key="wizard-next-btn"
                                type="button"
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    handleNext();
                                }}
                                variant="primary"
                                size="sm"
                                iconRight={<ArrowRight size={14} />}
                            >
                                {STEPS[activeStep + 1]?.label}
                            </Button>
                        ) : (
                            <Button
                                key="wizard-submit-btn"
                                type="button"
                                onClick={(e) => {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    handleSave(e);
                                }}
                                variant="primary"
                                size="sm"
                                loading={isSubmitting}
                                iconRight={<Check size={14} />}
                            >
                                {employeData ? 'Enregistrer les modifications' : 'Créer la fiche collaborateur'}
                            </Button>
                        )}
                    </div>
                </div>
            }
        >
            <div className="space-y-4">
                {/* Stepper Horizontal Moderne & Explicite */}
                <div className="flex border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] overflow-x-auto">
                    {STEPS.map((step, idx) => {
                        const isCompleted = idx < activeStep;
                        const isActive = idx === activeStep;
                        const canClick = employeData || isCompleted;

                        return (
                            <button
                                key={step.id}
                                type="button"
                                onClick={() => {
                                    if (canClick) {
                                        setActiveStep(idx);
                                        setErrorMsg('');
                                    }
                                }}
                                className={`flex items-center gap-2 py-2.5 px-3 border-b-2 text-xs font-semibold whitespace-nowrap transition-all rounded-none ${
                                    isActive
                                        ? 'border-[var(--color-brand-500)] text-[var(--color-brand-500)] bg-[var(--color-surface)] shadow-2xs font-bold'
                                        : canClick
                                            ? 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] cursor-pointer'
                                            : 'border-transparent text-[var(--color-fg-subtle)]/50 cursor-not-allowed opacity-60'
                                }`}
                            >
                                <span className={`w-5 h-5 rounded-none flex items-center justify-center text-[10px] font-bold shrink-0 ${
                                    isCompleted
                                        ? 'bg-success-500/15 text-success-600 dark:text-success-400'
                                        : isActive
                                            ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                            : 'bg-ink-200 dark:bg-ink-800 text-[var(--color-fg-muted)]'
                                }`}>
                                    {isCompleted ? <Check size={12} strokeWidth={3} /> : idx + 1}
                                </span>
                                <span>{step.label}</span>
                                {!step.required && (
                                    <span className="text-[9px] font-normal text-[var(--color-fg-subtle)] px-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)]">
                                        Opt.
                                    </span>
                                )}
                            </button>
                        );
                    })}
                </div>

                {/* Formulaire & Contenu de l'étape */}
                <form id="employe-wizard-form" onSubmit={handleSave} className="p-2 space-y-4 max-h-[62vh] overflow-y-auto pr-3">
                    {errorMsg && (
                        <FormError message={errorMsg} />
                    )}

                    <Suspense fallback={<SkeletonForm fields={6} />}>
                        {/* Step 1: Identité */}
                        {STEPS[activeStep].id === 'civilite' && (
                            <CiviliteStep
                                formData={formData}
                                set={set}
                                villesStandards={VILLES_MADAGASCAR}
                                nationalitesStandards={NATIONALITES_STANDARDS}
                                isCustomNat={isCustomNat}
                                customNationalite={customNationalite}
                                setCustomNationalite={setCustomNationalite}
                                handleNationaliteChange={handleNationaliteChange}
                                onGenerateMatricule={handleGenerateMatricule}
                                onBlurMatricule={handleBlurMatricule}
                                matriculeError={matriculeError}
                                generatingMatricule={generatingMatricule}
                            />
                        )}

                        {/* Step 2: CIN & Permis */}
                        {STEPS[activeStep].id === 'cin' && (
                            <div className="space-y-4">
                                <Card padding className="space-y-4">
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]"><h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5"><CreditCard size={14} className="text-brand-500" />Carte d'Identité Nationale (CIN)</h3><Badge variant="primary" size="xs">Obligatoire</Badge></div>
                                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                        <Input label="N° CIN" required value={formData.cinNumero} onChange={(event) => set('cinNumero', event.target.value)} placeholder="Ex: 101 234 567 890" />
                                        <Input label="Date de Délivrance" required type="date" value={formData.cinDateDelivrance} onChange={(event) => set('cinDateDelivrance', event.target.value)} />
                                        <AutocompleteInput label="Lieu de Délivrance" required value={formData.cinLieuDelivrance} onChange={(event) => set('cinLieuDelivrance', event.target.value)} placeholder="Ex: Antananarivo, Toamasina..." options={VILLES_MADAGASCAR} listId="cin-villes-list" />
                                        <Input label="Duplicata – Date" type="date" value={formData.cinDuplicataDate} onChange={(event) => set('cinDuplicataDate', event.target.value)} />
                                        <div className="md:col-span-2"><AutocompleteInput label="Duplicata – Lieu" value={formData.cinDuplicataLieu} onChange={(event) => set('cinDuplicataLieu', event.target.value)} placeholder="Lieu de duplicata (Ex: Antananarivo)" options={VILLES_MADAGASCAR} listId="cin-duplicata-villes-list" /></div>
                                    </div>
                                </Card>
                                <Card padding className="space-y-4">
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]"><h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5"><Award size={14} className="text-brand-500" />Permis de Conduire</h3><Badge variant="default" size="xs">Optionnel</Badge></div>
                                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                        <Input label="N° Permis" value={formData.permisConduireNumero} onChange={(event) => set('permisConduireNumero', event.target.value)} placeholder="N° de permis" />
                                        <Input label="Date Délivrance" type="date" value={formData.permisConduireDateDelivrance} onChange={(event) => set('permisConduireDateDelivrance', event.target.value)} />
                                        <AutocompleteInput label="Lieu Délivrance" value={formData.permisConduireLieuDelivrance} onChange={(event) => set('permisConduireLieuDelivrance', event.target.value)} placeholder="Ex: Antananarivo" options={VILLES_MADAGASCAR} listId="permis-villes-list" />
                                    </div>
                                </Card>
                            </div>
                        )}

                        {/* Step 3: Coordonnées & Photo */}
                        {STEPS[activeStep].id === 'coordonnees' && (
                            <CoordonneesPhotoStep
                                formData={formData}
                                set={set}
                                villesStandards={VILLES_MADAGASCAR}
                                uploadingPhoto={uploadingPhoto}
                                handlePhotoSelect={handlePhotoSelect}
                            />
                        )}

                        {/* Step 4: Famille */}
                        {STEPS[activeStep].id === 'famille' && (
                            <FamilleFormStep
                                formData={formData}
                                setNested={setNested}
                                addEnfant={addEnfant}
                                removeEnfant={removeEnfant}
                                setEnfant={setEnfant}
                                villesStandards={VILLES_MADAGASCAR}
                            />
                        )}

                        {/* Step 5: Urgence */}
                        {STEPS[activeStep].id === 'urgence' && (
                            <UrgenceFormStep
                                formData={formData}
                                addContact={addContact}
                                removeContact={removeContact}
                                setContact={setContact}
                            />
                        )}

                        {/* Step 6: CNaPS & Banque */}
                        {STEPS[activeStep].id === 'banque' && (
                            <div className="space-y-4">
                                <Card padding className="space-y-4"><div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]"><h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5"><Shield size={14} className="text-brand-500" />Sécurité Sociale (CNaPS)</h3><Badge variant="default" size="xs">Optionnel</Badge></div><div className="grid grid-cols-1 md:grid-cols-2 gap-4"><Input label="N° Affiliation CNaPS" value={formData.numAffiliationCnaps} onChange={(event) => set('numAffiliationCnaps', event.target.value)} placeholder="N° CNaPS" /><Input label="Date Déclaration" type="date" value={formData.dateDeclaration} onChange={(event) => set('dateDeclaration', event.target.value)} /></div></Card>
                                <Card padding className="space-y-4"><div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]"><h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5"><Landmark size={14} className="text-brand-500" />Compte Bancaire Principal</h3><Badge variant="default" size="xs">Optionnel</Badge></div><div className="grid grid-cols-1 md:grid-cols-2 gap-4"><SearchSelect label="Banque" value={formData.compteBanquePrincipal.idBanque} onChange={(value) => setNested('compteBanquePrincipal', 'idBanque', value)} options={banques.filter((bank) => bank.estActif !== false && bank.actif !== false || String(bank.idBanque) === String(formData.compteBanquePrincipal.idBanque)).map((bank) => ({ value: bank.idBanque, label: bank.nomBanque, sublabel: bank.codeBanque }))} placeholder="Rechercher une banque…" /><AutocompleteInput label="Agence" value={formData.compteBanquePrincipal.agence} onChange={(event) => setNested('compteBanquePrincipal', 'agence', event.target.value)} placeholder="Ex: Ankorondrano" options={AGENCES_BANCAIRES} listId="agences-bancaires-list" /><Input label="N° Compte / RIB" value={formData.compteBanquePrincipal.numCompteRib} onChange={(event) => setNested('compteBanquePrincipal', 'numCompteRib', event.target.value)} placeholder="00000-00000-00000000000-00" /><Input label="Titulaire du Compte" value={formData.compteBanquePrincipal.titulaireCompte} onChange={(event) => setNested('compteBanquePrincipal', 'titulaireCompte', event.target.value)} placeholder="Nom du titulaire" /></div></Card>
                            </div>
                        )}
                    </Suspense>
                </form>
            </div>
        </Modal>
    );
}
