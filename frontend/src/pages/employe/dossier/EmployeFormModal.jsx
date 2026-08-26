import { useState, useEffect } from 'react';
import { X, User, CreditCard, Phone, Users, ShieldAlert, Building } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';
import employeService from '@/api/employe/employeService';
import Button from '@/components/ui/forms/Button';
import { VILLES_MADAGASCAR, NATIONALITES_STANDARDS } from '@/constants/madagascarGeo';

import CiviliteStep from '@/components/employe/form/CiviliteStep';
import CinPermisStep from '@/components/employe/form/CinPermisStep';
import CoordonneesPhotoStep from '@/components/employe/form/CoordonneesPhotoStep';
import FamilleFormStep from '@/components/employe/form/FamilleFormStep';
import UrgenceFormStep from '@/components/employe/form/UrgenceFormStep';
import CnapsBanqueStep from '@/components/employe/form/CnapsBanqueStep';

const STEPS = [
    { id: 'civilite', label: 'Identité', icon: User },
    { id: 'cin', label: 'CIN & Permis', icon: CreditCard },
    { id: 'coordonnees', label: 'Coordonnées & Photo', icon: Phone },
    { id: 'famille', label: 'Famille', icon: Users },
    { id: 'urgence', label: 'Urgence', icon: ShieldAlert },
    { id: 'banque', label: 'CNaPS & Banque', icon: Building }
];

const EMPTY_FORM = {
    matricule: '', nom: '', prenom: '', sexe: 'M',
    dateNaissance: '', lieuNaissance: 'Antananarivo', nationalite: 'Malgache',
    pereNomPrenom: '', mereNomPrenom: '', situationFamiliale: 'Celibataire',
    cinNumero: '', cinDateDelivrance: '', cinLieuDelivrance: 'Antananarivo',
    cinDuplicataDate: '', cinDuplicataLieu: '',
    permisConduireNumero: '', permisConduireDateDelivrance: '', permisConduireLieuDelivrance: '',
    adresse: '', ville: 'Antananarivo', telephone: '', email: '',
    numAffiliationCnaps: '', photoUrl: '', dateDeclaration: '',
    conjoint: { nom: '', prenom: '', dateNaissance: '', lieuNaissance: '', societe: '', fonction: '' },
    enfants: [],
    contactsUrgence: [{ nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }],
    compteBanquePrincipal: { idBanque: '', agence: '', numCompteRib: '', titulaireCompte: '' }
};

export default function EmployeFormModal({ isOpen, onClose, onSave, employeData, isSubmitting }) {
    const [activeStep, setActiveStep] = useState(0);
    const [banques, setBanques] = useState([]);
    const [errorMsg, setErrorMsg] = useState('');
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const [customNationalite, setCustomNationalite] = useState('');
    const [isCustomNat, setIsCustomNat] = useState(false);

    const [generatingMatricule, setGeneratingMatricule] = useState(false);
    const [matriculeError, setMatriculeError] = useState('');

    const [formData, setFormData] = useState({ ...EMPTY_FORM });

    const fmtDate = (d) => {
        if (!d) return '';
        if (d instanceof Date) {
            if (isNaN(d.getTime())) return '';
            const yyyy = d.getFullYear();
            const mm = String(d.getMonth() + 1).padStart(2, '0');
            const dd = String(d.getDate()).padStart(2, '0');
            return `${yyyy}-${mm}-${dd}`;
        }
        if (typeof d === 'string') {
            const s = d.trim();
            if (s.includes('T')) return s.split('T')[0];
            if (s.includes(' ')) return s.split(' ')[0];
            if (/^\d{4}-\d{2}-\d{2}$/.test(s)) return s;
            const parsed = new Date(s);
            if (!isNaN(parsed.getTime())) {
                const yyyy = parsed.getFullYear();
                const mm = String(parsed.getMonth() + 1).padStart(2, '0');
                const dd = String(parsed.getDate()).padStart(2, '0');
                return `${yyyy}-${mm}-${dd}`;
            }
            return s.substring(0, 10);
        }
        return '';
    };

    const loadBanques = async () => { 
        try { 
            const data = await referentielService.getBanques();
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
                dateNaissance: fmtDate(e.dateNaissance), 
                lieuNaissance: e.lieuNaissance || 'Antananarivo',
                nationalite: isKnownNat ? nat : 'Autre',
                pereNomPrenom: e.pereNomPrenom || '', 
                mereNomPrenom: e.mereNomPrenom || '',
                situationFamiliale: e.situationFamiliale || 'Celibataire',
                cinNumero: e.cinNumero || '', 
                cinDateDelivrance: fmtDate(e.cinDateDelivrance),
                cinLieuDelivrance: e.cinLieuDelivrance || 'Antananarivo',
                cinDuplicataDate: fmtDate(e.cinDuplicataDate), 
                cinDuplicataLieu: e.cinDuplicataLieu || '',
                permisConduireNumero: e.permisConduireNumero || '',
                permisConduireDateDelivrance: fmtDate(e.permisConduireDateDelivrance),
                permisConduireLieuDelivrance: e.permisConduireLieuDelivrance || '',
                adresse: e.adresse || '', 
                ville: e.ville || 'Antananarivo', 
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
                    aCharge: enf.aCharge !== undefined ? enf.aCharge : true
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
                compteBanquePrincipal: {
                    idBanque: employeData.comptesBancaires?.find(cb => cb.estPrincipal)?.idBanque || e.idBanquePrincipal || '',
                    agence: employeData.comptesBancaires?.find(cb => cb.estPrincipal)?.agence || e.agenceBanquePrincipal || '',
                    numCompteRib: employeData.comptesBancaires?.find(cb => cb.estPrincipal)?.numCompteRib || e.numCompteRibPrincipal || '',
                    titulaireCompte: employeData.comptesBancaires?.find(cb => cb.estPrincipal)?.titulaireCompte || e.titulaireComptePrincipal || ''
                }
            });
        } else {
            setFormData({ ...EMPTY_FORM });
            setIsCustomNat(false);
            setCustomNationalite('');
        }
        setErrorMsg('');
        setMatriculeError('');
        setActiveStep(0);
    }, [employeData, isOpen]);

    if (!isOpen) return null;

    const set = (name, value) => setFormData(prev => ({ ...prev, [name]: value }));
    const setNested = (cat, field, value) => setFormData(prev => ({ ...prev, [cat]: { ...prev[cat], [field]: value } }));

    const handleNationaliteChange = (val) => {
        if (val === 'Autre') {
            setIsCustomNat(true);
            set('nationalite', customNationalite || 'Autre');
        } else {
            setIsCustomNat(false);
            set('nationalite', val);
        }
    };

    const handlePhotoSelect = async (e) => {
        const file = e.target.files?.[0];
        if (!file) return;

        if (file.size > 5 * 1024 * 1024) {
            setErrorMsg("La taille de l'image ne doit pas dépasser 5 Mo.");
            return;
        }

        setUploadingPhoto(true);
        setErrorMsg('');
        try {
            const res = await employeService.uploadPhoto(file);
            if (res?.photoUrl) {
                set('photoUrl', res.photoUrl);
            }
        } catch (err) {
            console.error("Erreur d'upload photo:", err);
            setErrorMsg("Erreur lors de l'upload de la photo.");
        } finally {
            setUploadingPhoto(false);
        }
    };

    const addEnfant = () => setFormData(prev => ({ ...prev, enfants: [...prev.enfants, { nom: '', prenom: '', sexe: 'M', dateNaissance: '', lieuNaissance: '', aCharge: true }] }));
    const removeEnfant = (i) => setFormData(prev => ({ ...prev, enfants: prev.enfants.filter((_, idx) => idx !== i) }));
    const setEnfant = (i, field, val) => { const u = [...formData.enfants]; u[i][field] = val; setFormData(prev => ({ ...prev, enfants: u })); };

    const addContact = () => setFormData(prev => ({ ...prev, contactsUrgence: [...prev.contactsUrgence, { nom: '', prenom: '', lienParente: 'Autre', telephone: '', adresse: '', societe: '' }] }));
    const removeContact = (i) => setFormData(prev => ({ ...prev, contactsUrgence: prev.contactsUrgence.filter((_, idx) => idx !== i) }));
    const setContact = (i, field, val) => { const u = [...formData.contactsUrgence]; u[i][field] = val; setFormData(prev => ({ ...prev, contactsUrgence: u })); };

    const handleGenerateMatricule = async () => {
        try {
            setGeneratingMatricule(true);
            setMatriculeError('');
            const nextMatricule = await employeService.getNextMatricule();
            setFormData(prev => ({ ...prev, matricule: nextMatricule }));
        } catch {
            setMatriculeError("Erreur génération matricule");
        } finally {
            setGeneratingMatricule(false);
        }
    };

    const handleBlurMatricule = async () => {
        if (!formData.matricule) return;
        try {
            if (employeData && employeData.matricule === formData.matricule) {
                setMatriculeError('');
                return;
            }
            const exists = await employeService.checkMatriculeExists(formData.matricule);
            if (exists) {
                setMatriculeError("Ce matricule est déjà utilisé.");
            } else {
                setMatriculeError('');
            }
        } catch (e) {
            console.error(e);
        }
    };

    const validateStep = (stepIdx) => {
        setErrorMsg('');
        const step = STEPS[stepIdx].id;
        if (step === 'civilite') {
            if (!formData.matricule || !formData.nom || !formData.prenom || !formData.dateNaissance) {
                setErrorMsg("L'identité et le matricule sont obligatoires.");
                return false;
            }
            if (matriculeError) {
                setErrorMsg("Veuillez corriger les erreurs de matricule.");
                return false;
            }
        }
        if (step === 'cin') {
            if (!formData.cinNumero) {
                setErrorMsg("Le numéro de CIN est obligatoire.");
                return false;
            }
        }
        if (step === 'coordonnees') {
            if (!formData.adresse || !formData.telephone) {
                setErrorMsg("L'adresse et le téléphone sont obligatoires.");
                return false;
            }
        }
        return true;
    };

    const handleNext = (e) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }
        if (validateStep(activeStep)) {
            setActiveStep(prev => prev + 1);
        }
    };

    const handlePrev = (e) => {
        if (e) {
            e.preventDefault();
            e.stopPropagation();
        }
        setActiveStep(prev => prev - 1);
        setErrorMsg('');
    };

    const handleSave = (e) => {
        e.preventDefault();
        
        // Si on n'est pas sur le dernier onglet, "Entrée" avance simplement à la suite
        if (activeStep < STEPS.length - 1) {
            handleNext();
            return;
        }

        if (isSubmitting) return;
        if (!validateStep(activeStep)) return;
        
        setErrorMsg('');
        const finalNat = isCustomNat ? customNationalite : formData.nationalite;

        if (formData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
            setErrorMsg("L'adresse email saisie est invalide.");
            return;
        }

        onSave({
            ...formData,
            nationalite: finalNat,
            numAffiliationCnaps: formData.numAffiliationCnaps?.trim() || null,
            cinNumero: formData.cinNumero?.trim() || null,
            permisConduireNumero: formData.permisConduireNumero?.trim() || null,
            email: formData.email?.trim() || null
        });
    };

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between">
                    <div>
                        <h2 className="font-bold text-lg text-ink-900 dark:text-white">
                            {employeData ? `Modifier — ${formData.matricule}` : 'Nouvelle Fiche Employé'}
                        </h2>
                        <p className="text-xs text-ink-400">Remplissez les informations de la fiche employé</p>
                    </div>
                    <button 
                        type="button"
                        onClick={onClose} 
                        className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-none cursor-pointer"
                    >
                        <X size={18} />
                    </button>
                </div>

                {/* Onglets Stepper */}
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-2 overflow-x-auto justify-between">
                    {STEPS.map((step, idx) => {
                        const Icon = step.icon;
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
                                className={`flex items-center gap-1.5 py-3 px-3 border-b-2 text-xs font-semibold whitespace-nowrap transition-colors ${
                                    isActive
                                        ? 'border-brand-500 text-brand-500'
                                        : canClick 
                                            ? 'border-transparent text-ink-600 dark:text-ink-300 hover:text-ink-900 dark:hover:text-white cursor-pointer' 
                                            : 'border-transparent text-ink-300 dark:text-ink-600 cursor-not-allowed opacity-60'
                                }`}
                            >
                                <Icon size={14} />
                                {step.label}
                            </button>
                        );
                    })}
                </div>

                {/* Body Formulaire */}
                <form onSubmit={handleSave} className="flex-1 overflow-y-auto p-6 space-y-5">
                    {errorMsg && (
                        <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                            <ShieldAlert size={14} />
                            {errorMsg}
                        </div>
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
                        />
                    )}

                    {/* Step 2: CIN & Permis */}
                    {STEPS[activeStep].id === 'cin' && (
                        <CinPermisStep
                            formData={formData}
                            set={set}
                            villesStandards={VILLES_MADAGASCAR}
                        />
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
                        <CnapsBanqueStep
                            formData={formData}
                            set={set}
                            setNested={setNested}
                            banques={banques}
                        />
                    )}

                    {/* Footer Boutons */}
                    <div className="flex justify-between items-center pt-4 border-t border-ink-150 dark:border-ink-700">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">Annuler</Button>
                        
                        <div className="flex items-center gap-3">
                            {activeStep > 0 && (
                                <Button type="button" onClick={handlePrev} variant="outline" size="sm">
                                    Précédent
                                </Button>
                            )}
                            
                            {activeStep < STEPS.length - 1 ? (
                                <button 
                                    type="button" 
                                    onClick={handleNext} 
                                    className="px-4 py-2 bg-brand-500 hover:bg-brand-600 text-white text-sm font-medium rounded-none transition-colors cursor-pointer"
                                >
                                    Suivant
                                </button>
                            ) : (
                                <Button type="submit" variant="primary" size="sm" loading={isSubmitting}>
                                    {employeData ? 'Enregistrer' : 'Créer la Fiche'}
                                </Button>
                            )}
                        </div>
                    </div>
                </form>
            </div>
        </div>
    );
}
