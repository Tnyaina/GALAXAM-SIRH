import FormError from '@/components/ui/feedback/FormError';
import { useState, useEffect, useCallback } from 'react';
import {
    User,
    CreditCard,
    Award,
    Phone,
    Users,
    Shield,
    ShieldAlert,
    Landmark,
    Building,
    Check,
    ArrowRight,
    ArrowLeft
} from 'lucide-react';
import organisationService from '@/features/organization/api/organisationService';
import employeService from '@/features/employees/api/employeService';
import { getErrorMessage } from '@/api/apiClient';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';
import Input, { AutocompleteInput } from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { Card } from '@/components/ui/data-display/Card';
import { AGENCES_BANCAIRES, VILLES_MADAGASCAR, NATIONALITES_STANDARDS } from '@/constants/madagascarGeo';

import {
    CiviliteStep,
    CoordonneesPhotoStep,
    FamilleFormStep,
    UrgenceFormStep
} from '@/features/employees/components/form/EmployeeFormSteps';

import { validerChampsEtape, validerToutesEtapesEmploye } from '@/features/employees/utils/employeFormValidation';
import { fmtDateInput } from '@/utils/dateUtils';
import { retirerElement, annulerSuppression, createSatelliteClientKey } from './satelliteFormChanges';

const STEPS = [
    { id: 'civilite', label: 'Identité', icon: User, required: true },
    { id: 'cin', label: 'CIN & Permis', icon: CreditCard, required: true },
    { id: 'coordonnees', label: 'Coordonnées', icon: Phone, required: true },
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
    supprimerConjoint: false,
    enfants: [],
    contactsUrgence: [{ clientKey: createSatelliteClientKey(), nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
    compteBanquePrincipal: { idBanque: '', agence: '', numCompteRib: '', titulaireCompte: '' },
    supprimerCompteBanque: false
};

const resolveStepIndex = (step) => {
    if (typeof step === 'number') return Math.max(0, Math.min(step, STEPS.length - 1));
    const idx = STEPS.findIndex(s => s.id === step);
    return idx >= 0 ? idx : 0;
};

export default function EmployeeFormModal({ isOpen, onClose, onSave, employeData, isSubmitting, initialStep = 0, bankOnly = false, canBankWrite = false }) {
    const [activeStep, setActiveStep] = useState(() => resolveStepIndex(initialStep));
    const [banques, setBanques] = useState([]);
    const [errorMsg, setErrorMsg] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const [customNationalite, setCustomNationalite] = useState('');
    const [isCustomNat, setIsCustomNat] = useState(false);

    const [generatingMatricule, setGeneratingMatricule] = useState(false);
    const [matriculeError, setMatriculeError] = useState('');

    const [formData, setFormData] = useState({ ...EMPTY_FORM });
    const [enfantsSupprimes, setEnfantsSupprimes] = useState([]);
    const [contactsSupprimes, setContactsSupprimes] = useState([]);
    const [isDirty, setIsDirty] = useState(false);
    const [showCancelConfirm, setShowCancelConfirm] = useState(false);

    // Alerte navigateur si l'utilisateur tente de fermer l'onglet avec un formulaire commencé
    useUnsavedChanges(isOpen && isDirty, 'Des informations ont été saisies pour ce collaborateur.');

    const loadBanques = async () => {
        try {
            const data = await organisationService.getBanques();
            setBanques(data || []);
        } catch (err) {
            console.error('Erreur chargement banques:', err);
        }
    };

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
                dateNaissance: fmtDateInput(e.dateNaissance),
                lieuNaissance: e.lieuNaissance || '',
                nationalite: isKnownNat ? nat : 'Autre',
                pereNomPrenom: e.pereNomPrenom || '',
                mereNomPrenom: e.mereNomPrenom || '',
                situationFamiliale: e.situationFamiliale || 'Celibataire',
                cinNumero: e.cinNumero || '',
                cinDateDelivrance: fmtDateInput(e.cinDateDelivrance),
                cinLieuDelivrance: e.cinLieuDelivrance || '',
                cinDuplicataDate: fmtDateInput(e.cinDuplicataDate),
                cinDuplicataLieu: e.cinDuplicataLieu || '',
                permisConduireNumero: e.permisConduireNumero || '',
                permisConduireDateDelivrance: fmtDateInput(e.permisConduireDateDelivrance),
                permisConduireLieuDelivrance: e.permisConduireLieuDelivrance || '',
                adresse: e.adresse || '',
                ville: e.ville || '',
                telephone: e.telephone || '',
                email: e.email || '',
                numAffiliationCnaps: e.numAffiliationCnaps || '',
                photoUrl: e.photoUrl || '',
                dateDeclaration: fmtDateInput(e.dateDeclaration),
                conjoint: {
                    idConjoint: employeData.conjoint?.idConjoint || null,
                    nom: employeData.conjoint?.nom || e.conjointNom || '',
                    prenom: employeData.conjoint?.prenom || e.conjointPrenom || '',
                    dateNaissance: fmtDateInput(employeData.conjoint?.dateNaissance),
                    lieuNaissance: employeData.conjoint?.lieuNaissance || '',
                    societe: employeData.conjoint?.societe || e.conjointSociete || '',
                    fonction: employeData.conjoint?.fonction || e.conjointFonction || ''
                },
                supprimerConjoint: false,
                enfants: (employeData.enfants || []).map(enf => ({
                    idEnfant: enf.idEnfant,
                    clientKey: enf.idEnfant ? undefined : createSatelliteClientKey(),
                    nom: enf.nom || '',
                    prenom: enf.prenom || '',
                    sexe: enf.sexe || 'M',
                    dateNaissance: fmtDateInput(enf.dateNaissance),
                    lieuNaissance: enf.lieuNaissance || '',
                    aCharge: enf.aCharge !== undefined ? enf.aCharge : true,
                    estEtudiant: enf.estEtudiant || false,
                    dateFinScolarite: fmtDateInput(enf.dateFinScolarite),
                    estHandicape: enf.estHandicape || false,
                    certificatFourni: enf.certificatFourni || false
                })),
                contactsUrgence: (employeData.contactsUrgence?.length > 0)
                    ? employeData.contactsUrgence.map(ct => ({
                        idContact: ct.idContact,
                        clientKey: ct.idContact ? undefined : createSatelliteClientKey(),
                        nom: ct.nom || '',
                        prenom: ct.prenom || '',
                        lienParente: ct.lienParente || 'Autre',
                        telephone: ct.telephone || '',
                        adresse: ct.adresse || '',
                        societe: ct.societe || ''
                    }))
                    : [{ clientKey: createSatelliteClientKey(), nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
                compteBanquePrincipal: employeData.comptesBancaires?.[0] || {
                    idBanque: '', agence: '', numCompteRib: '', titulaireCompte: ''
                },
                supprimerCompteBanque: false
            });
        } else {
            setFormData({ ...EMPTY_FORM });
            setIsCustomNat(false);
            setCustomNationalite('');
        }
        setActiveStep(resolveStepIndex(initialStep));
        setEnfantsSupprimes([]);
        setContactsSupprimes([]);
        setErrorMsg('');
        setFieldErrors({});
        setMatriculeError('');
        setIsDirty(false);
    }, [employeData, isOpen, initialStep]);

    const set = useCallback((field, val) => {
        setIsDirty(true);
        setFormData(prev => ({ ...prev, [field]: val }));
        setFieldErrors(prev => {
            if (!prev[field]) return prev;
            const next = { ...prev };
            delete next[field];
            return next;
        });
    }, []);

    const handleFieldBlur = useCallback((fieldName) => {
        const currentErrors = validerChampsEtape(
            STEPS[activeStep].id,
            formData,
            matriculeError,
            { customNationalite, isCustomNat }
        );
        setFieldErrors(prev => ({
            ...prev,
            [fieldName]: currentErrors[fieldName] || ''
        }));
    }, [activeStep, formData, matriculeError, customNationalite, isCustomNat]);

    const setNested = useCallback((parent, field, val) => {
        setIsDirty(true);
        setFormData(prev => ({
            ...prev,
            [parent]: { ...prev[parent], [field]: val }
        }));
        setFieldErrors(prev => {
            if (!prev[field]) return prev;
            const next = { ...prev };
            delete next[field];
            return next;
        });
    }, []);

    const toggleSuppressionConjoint = useCallback(() => {
        setIsDirty(true);
        setFormData(prev => ({ ...prev, supprimerConjoint: !prev.supprimerConjoint }));
    }, []);

    const addEnfant = useCallback(() => {
        setIsDirty(true);
        setFormData(prev => ({
            ...prev,
            enfants: [
                ...prev.enfants,
                {
                    clientKey: createSatelliteClientKey(),
                    nom: '', prenom: '', sexe: 'M',
                    dateNaissance: '', lieuNaissance: '',
                    aCharge: true, estEtudiant: false,
                    dateFinScolarite: '', estHandicape: false,
                    certificatFourni: false
                }
            ]
        }));
    }, []);

    const removeEnfant = useCallback((index) => {
        setIsDirty(true);
        const suivant = retirerElement(formData.enfants, enfantsSupprimes, index, 'idEnfant');
        setFormData(prev => ({ ...prev, enfants: suivant.elements }));
        setEnfantsSupprimes(suivant.suppressions);
    }, [formData.enfants, enfantsSupprimes]);

    const annulerSuppressionEnfant = useCallback((index) => {
        setIsDirty(true);
        const suivant = annulerSuppression(formData.enfants, enfantsSupprimes, index);
        setFormData(prev => ({ ...prev, enfants: suivant.elements }));
        setEnfantsSupprimes(suivant.suppressions);
    }, [formData.enfants, enfantsSupprimes]);

    const setEnfant = useCallback((index, field, val) => {
        setIsDirty(true);
        const errorKey = `enfant_${index}_${field}`;
        setFieldErrors(prev => {
            if (!(errorKey in prev)) return prev;
            const next = { ...prev };
            delete next[errorKey];
            return next;
        });
        setFormData(prev => {
            const copy = [...prev.enfants];
            copy[index] = { ...copy[index], [field]: val };
            return { ...prev, enfants: copy };
        });
    }, []);

    const addContact = useCallback(() => {
        setIsDirty(true);
        setFormData(prev => ({
            ...prev,
            contactsUrgence: [...prev.contactsUrgence, { clientKey: createSatelliteClientKey(), nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }]
        }));
    }, []);

    const removeContact = useCallback((index) => {
        setIsDirty(true);
        const suivant = retirerElement(formData.contactsUrgence, contactsSupprimes, index, 'idContact');
        setFormData(prev => ({ ...prev, contactsUrgence: suivant.elements }));
        setContactsSupprimes(suivant.suppressions);
    }, [formData.contactsUrgence, contactsSupprimes]);

    const annulerSuppressionContact = useCallback((index) => {
        setIsDirty(true);
        const suivant = annulerSuppression(formData.contactsUrgence, contactsSupprimes, index);
        setFormData(prev => ({ ...prev, contactsUrgence: suivant.elements }));
        setContactsSupprimes(suivant.suppressions);
    }, [formData.contactsUrgence, contactsSupprimes]);

    const setContact = useCallback((index, field, val) => {
        setIsDirty(true);
        const errorKey = `contact_${index}_${field}`;
        setFieldErrors(prev => {
            if (!(errorKey in prev)) return prev;
            const next = { ...prev };
            delete next[errorKey];
            return next;
        });
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
            const res = await employeService.uploadPhoto(file, {
                matricule: formData.matricule,
                nom: formData.nom,
                prenom: formData.prenom
            });
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
                setFieldErrors(prev => {
                    const next = { ...prev };
                    delete next.matricule;
                    return next;
                });
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
        if (!mat) {
            setFieldErrors(prev => ({ ...prev, matricule: 'Le matricule est obligatoire.' }));
            return;
        }

        if (employeData) {
            const oldMat = (employeData.employe || employeData).matricule;
            if (oldMat === mat) {
                setMatriculeError('');
                setFieldErrors(prev => {
                    const next = { ...prev };
                    delete next.matricule;
                    return next;
                });
                return;
            }
        }

        try {
            const isAvailable = await employeService.checkMatriculeAvailable(mat);
            if (!isAvailable) {
                setMatriculeError("Ce matricule est déjà attribué.");
                setFieldErrors(prev => ({ ...prev, matricule: "Ce matricule est déjà attribué." }));
            } else {
                setMatriculeError('');
                setFieldErrors(prev => {
                    const next = { ...prev };
                    delete next.matricule;
                    return next;
                });
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
        const errors = validerChampsEtape(stepId, formData, matriculeError, { customNationalite, isCustomNat });
        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return false;
        }
        setFieldErrors({});
        return true;
    };

    const handleNext = () => {
        if (activeStep >= STEPS.length - 1) return;
        if (validateStep(activeStep)) {
            setActiveStep(prev => Math.min(prev + 1, STEPS.length - 1));
            setErrorMsg('');
        }
    };

    const handlePrev = () => {
        setActiveStep(prev => Math.max(prev - 1, 0));
        setErrorMsg('');
    };

    const handleSave = async (e) => {
        if (e && e.preventDefault) e.preventDefault();

        if (!bankOnly && !employeData && activeStep < STEPS.length - 1) {
            handleNext();
            return;
        }

        if (isSubmitting) return;

        // Validation intégrale de l'ensemble des étapes
        const validationResult = bankOnly ? null : validerToutesEtapesEmploye(
            STEPS,
            formData,
            matriculeError,
            { customNationalite, isCustomNat }
        );

        if (validationResult) {
            setActiveStep(validationResult.stepIndex);
            const stepErrors = validerChampsEtape(
                validationResult.stepId,
                formData,
                matriculeError,
                { customNationalite, isCustomNat }
            );
            setFieldErrors(stepErrors);
            return;
        }

        setErrorMsg('');
        const finalNat = isCustomNat ? customNationalite : formData.nationalite;

        try {
            if (bankOnly) {
                if (!formData.supprimerCompteBanque) {
                    const bankErrors = validerChampsEtape('banque', formData, matriculeError, { customNationalite, isCustomNat });
                    if (!formData.compteBanquePrincipal?.idBanque) bankErrors.idBanque = 'Sélectionnez une banque.';
                    if (!formData.compteBanquePrincipal?.numCompteRib?.trim()) bankErrors.numCompteRib = 'Le numéro de compte RIB est obligatoire.';
                    if (Object.keys(bankErrors).length > 0) {
                        setFieldErrors(bankErrors);
                        return;
                    }
                }
                await onSave({
                    compteBanquePrincipal: formData.supprimerCompteBanque ? null : formData.compteBanquePrincipal,
                    supprimerCompteBanque: formData.supprimerCompteBanque
                });
                setIsDirty(false);
                return;
            }
            await onSave({
                ...formData,
                dateNaissance: fmtDateInput(formData.dateNaissance) || null,
                cinDateDelivrance: fmtDateInput(formData.cinDateDelivrance) || null,
                cinDuplicataDate: fmtDateInput(formData.cinDuplicataDate) || null,
                permisConduireDateDelivrance: fmtDateInput(formData.permisConduireDateDelivrance) || null,
                dateDeclaration: fmtDateInput(formData.dateDeclaration) || null,
                conjoint: formData.supprimerConjoint ? null : formData.conjoint ? {
                    nom: formData.conjoint.nom,
                    prenom: formData.conjoint.prenom,
                    dateNaissance: fmtDateInput(formData.conjoint.dateNaissance) || null,
                    lieuNaissance: formData.conjoint.lieuNaissance,
                    societe: formData.conjoint.societe,
                    fonction: formData.conjoint.fonction
                } : null,
                enfants: (formData.enfants || []).map(enf => {
                    const enfant = { ...enf };
                    delete enfant.clientKey;
                    return {
                        ...enfant,
                        dateNaissance: fmtDateInput(enf.dateNaissance) || null,
                        dateFinScolarite: fmtDateInput(enf.dateFinScolarite) || null
                    };
                }),
                contactsUrgence: (formData.contactsUrgence || []).map(contact => {
                    const donnees = { ...contact };
                    delete donnees.clientKey;
                    return donnees;
                }),
                enfantsASupprimer: enfantsSupprimes.map(enf => enf.idEnfant),
                contactsUrgenceASupprimer: contactsSupprimes.map(ct => ct.idContact),
                compteBanquePrincipal: undefined,
                supprimerCompteBanque: undefined,
                nationalite: finalNat,
                numAffiliationCnaps: formData.numAffiliationCnaps?.trim() || null,
                cinNumero: formData.cinNumero?.trim() || null,
                permisConduireNumero: formData.permisConduireNumero?.trim() || null,
                email: formData.email?.trim() || null
            });
            setIsDirty(false);
        } catch (err) {
            console.error("Erreur soumission formulaire employé:", err);
            const msg = getErrorMessage(err, "Erreur lors de l'enregistrement de la fiche.");
            setErrorMsg(msg);
        }
    };

    return (
        <>
            <Modal
                isOpen={isOpen}
                onClose={onClose}
                title={bankOnly ? 'Compte bancaire pour virement' : employeData ? `Modifier la Fiche — ${formData.matricule}` : 'Nouveau Collaborateur'}
                icon={<User size={18} />}
                size="3xl"
                closeOnBackdrop={false}
                isDirty={isDirty}
                dirtyWarningTitle="Formulaire collaborateur en cours"
                dirtyWarningMessage="Des informations ont été saisies pour ce collaborateur. Si vous fermez la modale sans valider, vos modifications seront perdues. Voulez-vous vraiment quitter ?"
                onConfirmDiscard={() => {
                    setIsDirty(false);
                    onClose();
                }}
                footer={
                    <div className="flex justify-between items-center w-full">
                        <Button
                            type="button"
                            onClick={() => {
                                if (isDirty) {
                                    setShowCancelConfirm(true);
                                } else {
                                    onClose();
                                }
                            }}
                            variant="ghost"
                            size="sm"
                            disabled={isSubmitting}
                        >
                            Annuler
                        </Button>
                    <div className="flex items-center gap-2">
                        {!bankOnly && activeStep > 0 && (
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
                        {!bankOnly && employeData && activeStep < STEPS.length - 1 && (
                            <Button
                                key="wizard-quick-save-btn"
                                type="button"
                                onClick={handleSave}
                                variant="outline"
                                size="sm"
                                loading={isSubmitting}
                                iconLeft={<Check size={14} />}
                            >
                                Enregistrer
                            </Button>
                        )}
                        {!bankOnly && activeStep < STEPS.length - 1 ? (
                            <Button
                                key="wizard-next-btn"
                                type="button"
                                onClick={handleNext}
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
                                onClick={handleSave}
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
                {/* Stepper Horizontal */}
                {!bankOnly && <div className="flex border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] overflow-x-auto">
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
                                        ? 'border-[var(--color-brand-500)] text-[var(--color-brand-600)] bg-[var(--color-surface)] shadow-2xs font-bold'
                                        : canClick
                                            ? 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] cursor-pointer'
                                            : 'border-transparent text-[var(--color-fg-subtle)]/50 cursor-not-allowed opacity-60'
                                }`}
                            >
                                <span className={`w-5 h-5 rounded-none flex items-center justify-center text-[10px] font-bold shrink-0 ${
                                    isCompleted
                                        ? 'bg-[var(--color-success-500)]/15 text-[var(--color-success-600)]'
                                        : isActive
                                            ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                            : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border)]'
                                }`}>
                                    {isCompleted ? <Check size={12} strokeWidth={3} /> : idx + 1}
                                </span>
                                <span>{step.label}</span>
                            </button>
                        );
                    })}
                </div>}

                {/* Formulaire & Contenu de l'étape */}
                <form id="employe-wizard-form" onSubmit={handleSave} className="p-2 space-y-4 max-h-[62vh] overflow-y-auto pr-3">
                    {/* FormError réservé aux erreurs globales de soumission */}
                    {errorMsg && (
                        <FormError message={errorMsg} />
                    )}

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
                            fieldErrors={fieldErrors}
                            onFieldBlur={handleFieldBlur}
                        />
                    )}

                    {/* Step 2: CIN & Permis */}
                    {STEPS[activeStep].id === 'cin' && (
                        <div className="space-y-4">
                            <Card padding className="space-y-4">
                                <div className="pb-2 border-b border-[var(--color-border-subtle)]">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <CreditCard size={14} className="text-[var(--color-brand-600)]" />
                                        Carte d'Identité Nationale (CIN)
                                    </h3>
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Input 
                                        label="N° CIN" 
                                        required 
                                        value={formData.cinNumero} 
                                        onChange={(event) => set('cinNumero', event.target.value)} 
                                        onBlur={() => handleFieldBlur('cinNumero')}
                                        error={fieldErrors.cinNumero}
                                        placeholder="Ex: 101 234 567 890" 
                                    />
                                    <Input 
                                        label="Date de Délivrance" 
                                        required 
                                        type="date" 
                                        value={formData.cinDateDelivrance} 
                                        onChange={(event) => set('cinDateDelivrance', event.target.value)} 
                                        onBlur={() => handleFieldBlur('cinDateDelivrance')}
                                        error={fieldErrors.cinDateDelivrance}
                                    />
                                    <AutocompleteInput 
                                        label="Lieu de Délivrance" 
                                        required 
                                        value={formData.cinLieuDelivrance} 
                                        onChange={(event) => set('cinLieuDelivrance', event.target.value)} 
                                        onBlur={() => handleFieldBlur('cinLieuDelivrance')}
                                        error={fieldErrors.cinLieuDelivrance}
                                        placeholder="Ex: Antananarivo, Toamasina..." 
                                        options={VILLES_MADAGASCAR} 
                                        listId="cin-villes-list" 
                                    />
                                    <Input 
                                        label="Duplicata – Date" 
                                        type="date" 
                                        value={formData.cinDuplicataDate} 
                                        onChange={(event) => set('cinDuplicataDate', event.target.value)} 
                                    />
                                    <div className="md:col-span-2">
                                        <AutocompleteInput 
                                            label="Duplicata – Lieu" 
                                            value={formData.cinDuplicataLieu} 
                                            onChange={(event) => set('cinDuplicataLieu', event.target.value)} 
                                            placeholder="Lieu de duplicata (Ex: Antananarivo)" 
                                            options={VILLES_MADAGASCAR} 
                                            listId="cin-duplicata-villes-list" 
                                        />
                                    </div>
                                </div>
                            </Card>
                            <Card padding className="space-y-4">
                                <div className="pb-2 border-b border-[var(--color-border-subtle)]">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <Award size={14} className="text-[var(--color-brand-600)]" />
                                        Permis de Conduire
                                    </h3>
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <Input 
                                        label="N° Permis" 
                                        value={formData.permisConduireNumero} 
                                        onChange={(event) => set('permisConduireNumero', event.target.value)} 
                                        placeholder="N° de permis" 
                                    />
                                    <Input 
                                        label="Date Délivrance" 
                                        type="date" 
                                        value={formData.permisConduireDateDelivrance} 
                                        onChange={(event) => set('permisConduireDateDelivrance', event.target.value)} 
                                    />
                                    <AutocompleteInput 
                                        label="Lieu Délivrance" 
                                        value={formData.permisConduireLieuDelivrance} 
                                        onChange={(event) => set('permisConduireLieuDelivrance', event.target.value)} 
                                        placeholder="Ex: Antananarivo" 
                                        options={VILLES_MADAGASCAR} 
                                        listId="permis-villes-list" 
                                    />
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
                            fieldErrors={fieldErrors}
                            onFieldBlur={handleFieldBlur}
                        />
                    )}

                    {/* Step 4: Famille */}
                    {STEPS[activeStep].id === 'famille' && (
                        <FamilleFormStep
                            formData={formData}
                            setNested={setNested}
                            onToggleSuppressionConjoint={toggleSuppressionConjoint}
                            addEnfant={addEnfant}
                            removeEnfant={removeEnfant}
                            enfantsSupprimes={enfantsSupprimes}
                            annulerSuppressionEnfant={annulerSuppressionEnfant}
                            setEnfant={setEnfant}
                            fieldErrors={fieldErrors}
                            villesStandards={VILLES_MADAGASCAR}
                        />
                    )}

                    {/* Step 5: Urgence */}
                    {STEPS[activeStep].id === 'urgence' && (
                        <UrgenceFormStep
                            formData={formData}
                            addContact={addContact}
                            removeContact={removeContact}
                            contactsSupprimes={contactsSupprimes}
                            annulerSuppressionContact={annulerSuppressionContact}
                            setContact={setContact}
                            fieldErrors={fieldErrors}
                        />
                    )}

                    {/* Step 6: CNaPS & Banque */}
                    {STEPS[activeStep].id === 'banque' && (
                        <div className="space-y-4">
                            {!bankOnly && <Card padding className="space-y-4">
                                <div className="pb-2 border-b border-[var(--color-border-subtle)]">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <Shield size={14} className="text-[var(--color-brand-600)]" />
                                        Sécurité Sociale (CNaPS)
                                    </h3>
                                </div>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Input 
                                        label="N° Affiliation CNaPS" 
                                        value={formData.numAffiliationCnaps} 
                                        onChange={(event) => set('numAffiliationCnaps', event.target.value)} 
                                        placeholder="N° CNaPS" 
                                    />
                                    <Input 
                                        label="Date Déclaration" 
                                        type="date" 
                                        value={formData.dateDeclaration} 
                                        onChange={(event) => set('dateDeclaration', event.target.value)} 
                                    />
                                </div>
                            </Card>}
                            {bankOnly && canBankWrite && <Card padding className="space-y-4">
                                <div className="pb-2 border-b border-[var(--color-border-subtle)]">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <Landmark size={14} className="text-[var(--color-brand-600)]" />
                                        Compte bancaire pour virement
                                    </h3>
                                    {formData.compteBanquePrincipal?.idCompteBanque && (
                                        <Button type="button" variant="ghost" size="xs"
                                            onClick={() => { setIsDirty(true); setFormData(prev => ({ ...prev, supprimerCompteBanque: !prev.supprimerCompteBanque })); }}>
                                            {formData.supprimerCompteBanque ? 'Annuler la suppression' : 'Supprimer le compte'}
                                        </Button>
                                    )}
                                </div>
                                {formData.supprimerCompteBanque && (
                                    <p className="text-xs text-[var(--color-danger-600)]">Le compte sera supprimé lors de l'enregistrement de la fiche.</p>
                                )}
                                {!formData.supprimerCompteBanque && (
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <SearchSelect 
                                        label="Banque" 
                                        value={formData.compteBanquePrincipal.idBanque} 
                                        onChange={(value) => setNested('compteBanquePrincipal', 'idBanque', value)} 
                                        options={banques.filter((bank) => bank.estActif !== false && bank.actif !== false || String(bank.idBanque) === String(formData.compteBanquePrincipal.idBanque)).map((bank) => ({ value: bank.idBanque, label: bank.nomBanque, sublabel: bank.codeBanque }))} 
                                        placeholder="Rechercher une banque…" 
                                        error={fieldErrors.idBanque}
                                    />
                                    <AutocompleteInput 
                                        label="Agence" 
                                        value={formData.compteBanquePrincipal.agence} 
                                        onChange={(event) => setNested('compteBanquePrincipal', 'agence', event.target.value)} 
                                        placeholder="Ex: Ankorondrano" 
                                        options={AGENCES_BANCAIRES} 
                                        listId="agences-bancaires-list" 
                                    />
                                    <Input 
                                        label="N° Compte / RIB" 
                                        value={formData.compteBanquePrincipal.numCompteRib} 
                                        onChange={(event) => setNested('compteBanquePrincipal', 'numCompteRib', event.target.value)} 
                                        onBlur={() => handleFieldBlur('numCompteRib')}
                                        error={fieldErrors.numCompteRib}
                                        placeholder="00000-00000-00000000000-00" 
                                    />
                                    <Input 
                                        label="Titulaire du Compte" 
                                        value={formData.compteBanquePrincipal.titulaireCompte} 
                                        onChange={(event) => setNested('compteBanquePrincipal', 'titulaireCompte', event.target.value)} 
                                        placeholder="Nom du titulaire" 
                                    />
                                </div>
                                )}
                            </Card>}
                        </div>
                    )}
                </form>
            </div>
        </Modal>

        {/* Boîte de confirmation d'abandon en cas de clic sur Annuler */}
        <ConfirmDialog
            isOpen={showCancelConfirm}
            onClose={() => setShowCancelConfirm(false)}
            onConfirm={() => {
                setShowCancelConfirm(false);
                setIsDirty(false);
                onClose();
            }}
            title="Formulaire collaborateur en cours"
            message="Des informations ont été saisies pour ce collaborateur. Si vous quittez maintenant, vos modifications seront perdues. Voulez-vous vraiment quitter ?"
            confirmLabel="Abandonner les modifications"
            cancelLabel="Poursuivre la saisie"
            variant="danger"
        />
        </>
    );
}
