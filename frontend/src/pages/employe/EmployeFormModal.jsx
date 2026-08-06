import { useState, useEffect } from 'react';
import { X, User, CreditCard, Phone, Users, ShieldAlert, Building, Plus, Trash2, Upload, Camera } from 'lucide-react';
import referentielService from '@/api/referentielService';
import employeService from '@/api/employeService';
import Input, { Textarea, Select, Checkbox } from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import { getFullPhotoUrl } from '@/utils/fileUtils';

// Liste standardisée des nationalités pour éviter les incohérences (Malgache, Malagasy, malgache...)
const NATIONALITES_STANDARDS = [
    { value: 'Malgache', label: 'Malgache' },
    { value: 'Française', label: 'Française' },
    { value: 'Comorienne', label: 'Comorienne' },
    { value: 'Indienne', label: 'Indienne' },
    { value: 'Chinoise', label: 'Chinoise' },
    { value: 'Mauricienne', label: 'Mauricienne' },
    { value: 'Autre', label: 'Autre (Saisie libre...)' },
];

// Liste des principales villes de Madagascar pour standardiser les lieux
const VILLES_STANDARDS = [
    'Antananarivo', 'Toamasina', 'Antsirabe', 'Fianarantsoa', 
    'Mahajanga', 'Toliara', 'Antsiranana', 'Nosy Be', 'Taolagnaro', 'Morondava', 'Autre'
];

export default function EmployeFormModal({ isOpen, onClose, onSave, employeData, isSubmitting }) {
    const [activeTab, setActiveTab] = useState('civilite');
    const [banques, setBanques] = useState([]);
    const [errorMsg, setErrorMsg] = useState('');
    const [uploadingPhoto, setUploadingPhoto] = useState(false);
    const [customNationalite, setCustomNationalite] = useState('');
    const [isCustomNat, setIsCustomNat] = useState(false);

    const emptyForm = {
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

    const [formData, setFormData] = useState({ ...emptyForm });

    useEffect(() => { loadBanques(); }, []);

    useEffect(() => {
        if (employeData) {
            const e = employeData.employe || employeData;
            const nat = e.nationalite || 'Malgache';
            const isKnownNat = NATIONALITES_STANDARDS.some(n => n.value === nat);

            if (!isKnownNat && nat) {
                setIsCustomNat(true);
                setCustomNationalite(nat);
            } else {
                setIsCustomNat(false);
                setCustomNationalite('');
            }

            setFormData({
                matricule: e.matricule || '', nom: e.nom || '', prenom: e.prenom || '',
                sexe: e.sexe || 'M',
                dateNaissance: fmtDate(e.dateNaissance), lieuNaissance: e.lieuNaissance || 'Antananarivo',
                nationalite: isKnownNat ? nat : 'Autre',
                pereNomPrenom: e.pereNomPrenom || '', mereNomPrenom: e.mereNomPrenom || '',
                situationFamiliale: e.situationFamiliale || 'Celibataire',
                cinNumero: e.cinNumero || '', cinDateDelivrance: fmtDate(e.cinDateDelivrance),
                cinLieuDelivrance: e.cinLieuDelivrance || 'Antananarivo',
                cinDuplicataDate: fmtDate(e.cinDuplicataDate), cinDuplicataLieu: e.cinDuplicataLieu || '',
                permisConduireNumero: e.permisConduireNumero || '',
                permisConduireDateDelivrance: fmtDate(e.permisConduireDateDelivrance),
                permisConduireLieuDelivrance: e.permisConduireLieuDelivrance || '',
                adresse: e.adresse || '', ville: e.ville || 'Antananarivo', telephone: e.telephone || '', email: e.email || '',
                numAffiliationCnaps: e.numAffiliationCnaps || '', photoUrl: e.photoUrl || '',
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
                    nom: enf.nom || '', prenom: enf.prenom || '', sexe: enf.sexe || 'M',
                    dateNaissance: fmtDate(enf.dateNaissance), lieuNaissance: enf.lieuNaissance || '',
                    aCharge: enf.aCharge !== undefined ? enf.aCharge : true
                })),
                contactsUrgence: (employeData.contactsUrgence?.length > 0)
                    ? employeData.contactsUrgence.map(ct => ({
                        nom: ct.nom || '', prenom: ct.prenom || '', lienParente: ct.lienParente || 'Autre',
                        telephone: ct.telephone || '', adresse: ct.adresse || '', societe: ct.societe || ''
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
            setFormData({ ...emptyForm });
            setIsCustomNat(false);
            setCustomNationalite('');
        }
        setErrorMsg('');
        setActiveTab('civilite');
    }, [employeData, isOpen]);

    const fmtDate = (d) => { if (!d) return ''; try { return new Date(d).toISOString().split('T')[0]; } catch { return ''; } };
    const loadBanques = async () => { try { setBanques(await referentielService.getBanques() || []); } catch {} };

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

    const handleSubmit = (e) => {
        e.preventDefault();
        setErrorMsg('');

        const finalNat = isCustomNat ? customNationalite : formData.nationalite;

        if (!formData.matricule || !formData.nom || !formData.prenom || !formData.dateNaissance || !formData.cinNumero || !formData.adresse || !formData.telephone) {
            setErrorMsg('Veuillez remplir tous les champs obligatoires (*).');
            return;
        }

        // Validation email basique si renseigné
        if (formData.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
            setErrorMsg("L'adresse email saisie est invalide.");
            return;
        }

        onSave({
            ...formData,
            nationalite: finalNat
        });
    };

    const tabs = [
        { id: 'civilite', label: 'Identité', icon: User },
        { id: 'cin', label: 'CIN & Permis', icon: CreditCard },
        { id: 'coordonnees', label: 'Coordonnées & Photo', icon: Phone },
        { id: 'famille', label: 'Famille', icon: Users },
        { id: 'urgence', label: 'Urgence', icon: ShieldAlert },
        { id: 'banque', label: 'CNaPS & Banque', icon: Building }
    ];

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4 overflow-y-auto">
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl w-full max-w-4xl max-h-[90vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between">
                    <div>
                        <h2 className="font-bold text-lg text-ink-900 dark:text-white">
                            {employeData ? `Modifier — ${formData.matricule}` : 'Nouvelle Fiche Employé'}
                        </h2>
                        <p className="text-xs text-ink-400">Remplissez les informations de la fiche employé</p>
                    </div>
                    <button onClick={onClose} className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors cursor-pointer">
                        <X size={18} />
                    </button>
                </div>

                {/* Onglets */}
                <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-2 overflow-x-auto justify-between">
                    {tabs.map(tab => {
                        const Icon = tab.icon;
                        return (
                            <button
                                key={tab.id}
                                type="button"
                                onClick={() => setActiveTab(tab.id)}
                                className={`flex items-center gap-1.5 py-3 px-3 border-b-2 text-xs font-semibold transition-all whitespace-nowrap cursor-pointer ${
                                    activeTab === tab.id
                                        ? 'border-brand-500 text-brand-500'
                                        : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                }`}
                            >
                                <Icon size={14} />
                                {tab.label}
                            </button>
                        );
                    })}
                </div>

                {/* Body */}
                <form onSubmit={handleSubmit} className="flex-1 overflow-y-auto p-6 space-y-5">
                    {errorMsg && (
                        <div className="bg-danger-500/10 text-danger-600 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                            <ShieldAlert size={14} />
                            {errorMsg}
                        </div>
                    )}

                    {/* Identité */}
                    {activeTab === 'civilite' && (
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                            <Input 
                                label="Matricule" 
                                required 
                                value={formData.matricule} 
                                onChange={e => set('matricule', e.target.value)} 
                                placeholder="Ex: 101, 1002 ou EMP-001" 
                                hint="Saisie libre (code unique du dossier personnel)"
                            />
                            <Select label="Sexe" required value={formData.sexe} onChange={e => set('sexe', e.target.value)}>
                                <option value="M">Masculin (M)</option>
                                <option value="F">Féminin (F)</option>
                            </Select>
                            <Input label="Nom" required value={formData.nom} onChange={e => set('nom', e.target.value)} placeholder="Nom de famille" />
                            <Input label="Prénom(s)" required value={formData.prenom} onChange={e => set('prenom', e.target.value)} placeholder="Prénoms" />
                            <Input label="Date de Naissance" required type="date" value={formData.dateNaissance} onChange={e => set('dateNaissance', e.target.value)} />
                            
                            {/* Lieu de Naissance avec liste des villes */}
                            <Select label="Lieu de Naissance" required value={formData.lieuNaissance} onChange={e => set('lieuNaissance', e.target.value)}>
                                {VILLES_STANDARDS.map(v => <option key={v} value={v}>{v}</option>)}
                            </Select>

                            {/* Nationalité avec Select standardisé */}
                            <div>
                                <Select 
                                    label="Nationalité" 
                                    value={isCustomNat ? 'Autre' : formData.nationalite} 
                                    onChange={e => handleNationaliteChange(e.target.value)}
                                >
                                    {NATIONALITES_STANDARDS.map(n => <option key={n.value} value={n.value}>{n.label}</option>)}
                                </Select>
                                {isCustomNat && (
                                    <div className="mt-2">
                                        <Input 
                                            label="Préciser la nationalité" 
                                            value={customNationalite} 
                                            onChange={e => {
                                                setCustomNationalite(e.target.value);
                                                set('nationalite', e.target.value);
                                            }}
                                            placeholder="Ex: Canadienne" 
                                        />
                                    </div>
                                )}
                            </div>

                            <Select label="Situation Familiale" value={formData.situationFamiliale} onChange={e => set('situationFamiliale', e.target.value)}>
                                <option value="Celibataire">Célibataire</option>
                                <option value="Marie">Marié(e)</option>
                                <option value="Divorce">Divorcé(e)</option>
                                <option value="Veuf">Veuf / Veuve</option>
                            </Select>
                            <Input label="Nom & Prénom du Père" value={formData.pereNomPrenom} onChange={e => set('pereNomPrenom', e.target.value)} placeholder="Nom du père" />
                            <Input label="Nom & Prénom de la Mère" value={formData.mereNomPrenom} onChange={e => set('mereNomPrenom', e.target.value)} placeholder="Nom de la mère" />
                        </div>
                    )}

                    {/* CIN & Permis */}
                    {activeTab === 'cin' && (
                        <div className="space-y-6">
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Carte d'Identité Nationale (CIN)</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Input label="N° CIN" required value={formData.cinNumero} onChange={e => set('cinNumero', e.target.value)} placeholder="Ex: 101 234 567 890" />
                                    <Input label="Date de Délivrance" required type="date" value={formData.cinDateDelivrance} onChange={e => set('cinDateDelivrance', e.target.value)} />
                                    <Select label="Lieu de Délivrance" required value={formData.cinLieuDelivrance} onChange={e => set('cinLieuDelivrance', e.target.value)}>
                                        {VILLES_STANDARDS.map(v => <option key={v} value={v}>{v}</option>)}
                                    </Select>
                                    <Input label="Duplicata – Date" type="date" value={formData.cinDuplicataDate} onChange={e => set('cinDuplicataDate', e.target.value)} />
                                    <div className="md:col-span-2">
                                        <Input label="Duplicata – Lieu" value={formData.cinDuplicataLieu} onChange={e => set('cinDuplicataLieu', e.target.value)} placeholder="Lieu de duplicata" />
                                    </div>
                                </div>
                            </div>
                            <div className="border-t border-ink-150 dark:border-ink-700 pt-4">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Permis de Conduire</h3>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    <Input label="N° Permis" value={formData.permisConduireNumero} onChange={e => set('permisConduireNumero', e.target.value)} placeholder="N° de permis" />
                                    <Input label="Date Délivrance" type="date" value={formData.permisConduireDateDelivrance} onChange={e => set('permisConduireDateDelivrance', e.target.value)} />
                                    <Input label="Lieu Délivrance" value={formData.permisConduireLieuDelivrance} onChange={e => set('permisConduireLieuDelivrance', e.target.value)} placeholder="Lieu" />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Coordonnées & Téléversement de Photo */}
                    {activeTab === 'coordonnees' && (
                        <div className="space-y-6">
                            {/* Zone Téléversement Photo */}
                            <div className="bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 p-4">
                                <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50 block mb-2">Photo de l'employé</label>
                                <div className="flex items-center gap-6">
                                    <div className="w-20 h-20 bg-white dark:bg-ink-900 border-2 border-dashed border-ink-200 dark:border-ink-700 flex flex-col items-center justify-center relative overflow-hidden flex-shrink-0">
                                        {formData.photoUrl ? (
                                            <img src={getFullPhotoUrl(formData.photoUrl)} alt="Aperçu" className="w-full h-full object-cover" />
                                        ) : (
                                            <div className="text-center text-ink-300">
                                                <Camera size={24} className="mx-auto mb-1" />
                                                <span className="text-[9px] uppercase font-semibold">Aucune</span>
                                            </div>
                                        )}
                                        {uploadingPhoto && (
                                            <div className="absolute inset-0 bg-black/40 flex items-center justify-center">
                                                <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                                            </div>
                                        )}
                                    </div>
                                    <div className="flex-1 space-y-2">
                                        <div className="flex items-center gap-3">
                                            <label className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold uppercase tracking-wider bg-brand-500 hover:bg-brand-600 text-white rounded-sm cursor-pointer transition-colors">
                                                <Upload size={14} />
                                                {uploadingPhoto ? 'Téléversement...' : 'Parcourir un fichier'}
                                                <input 
                                                    type="file" 
                                                    accept="image/png, image/jpeg, image/webp" 
                                                    onChange={handlePhotoSelect} 
                                                    disabled={uploadingPhoto} 
                                                    className="hidden" 
                                                />
                                            </label>
                                            {formData.photoUrl && (
                                                <button 
                                                    type="button" 
                                                    onClick={() => set('photoUrl', '')} 
                                                    className="text-xs font-semibold text-danger-500 hover:underline cursor-pointer"
                                                >
                                                    Supprimer
                                                </button>
                                            )}
                                        </div>
                                        <p className="text-[11px] text-ink-400">Formats acceptés : PNG, JPG, WEBP. Taille max : 5 Mo.</p>
                                    </div>
                                </div>
                            </div>

                            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                <div className="md:col-span-2">
                                    <Textarea label="Adresse Domicile" required rows={2} value={formData.adresse} onChange={e => set('adresse', e.target.value)} placeholder="Adresse complète" />
                                </div>
                                <Select label="Ville" value={formData.ville} onChange={e => set('ville', e.target.value)}>
                                    {VILLES_STANDARDS.map(v => <option key={v} value={v}>{v}</option>)}
                                </Select>
                                <Input label="Téléphone" required value={formData.telephone} onChange={e => set('telephone', e.target.value)} placeholder="Ex: 034 12 345 67" />
                                <Input label="Email" type="email" value={formData.email} onChange={e => set('email', e.target.value)} placeholder="email@domaine.mg" />
                            </div>
                        </div>
                    )}

                    {/* Famille & Enfants */}
                    {activeTab === 'famille' && (
                        <div className="space-y-6">
                            {/* Conjoint */}
                            <div className="bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 p-4">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Conjoint (Si marié)</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Input label="Nom Conjoint" value={formData.conjoint.nom} onChange={e => setNested('conjoint', 'nom', e.target.value)} placeholder="Nom" />
                                    <Input label="Prénom Conjoint" value={formData.conjoint.prenom} onChange={e => setNested('conjoint', 'prenom', e.target.value)} placeholder="Prénom" />
                                    <Input label="Date Naissance" type="date" value={formData.conjoint.dateNaissance} onChange={e => setNested('conjoint', 'dateNaissance', e.target.value)} />
                                    <Input label="Société" value={formData.conjoint.societe} onChange={e => setNested('conjoint', 'societe', e.target.value)} placeholder="Employeur" />
                                </div>
                            </div>

                            {/* Enfants */}
                            <div>
                                <div className="flex items-center justify-between mb-3">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400">Enfants ({formData.enfants.length})</h3>
                                    <Button type="button" onClick={addEnfant} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                                        Ajouter
                                    </Button>
                                </div>
                                {formData.enfants.length === 0 ? (
                                    <p className="text-xs text-ink-400 italic text-center py-6 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700">
                                        Aucun enfant enregistré.
                                    </p>
                                ) : (
                                    <div className="space-y-3">
                                        {formData.enfants.map((enf, idx) => (
                                            <div key={idx} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 relative">
                                                <button type="button" onClick={() => removeEnfant(idx)} className="absolute top-3 right-3 text-danger-500 hover:bg-danger-500/10 p-1 rounded-sm transition-colors cursor-pointer">
                                                    <Trash2 size={14} />
                                                </button>
                                                <div className="grid grid-cols-1 md:grid-cols-3 gap-3 pr-8">
                                                    <Input label="Nom" value={enf.nom} onChange={e => setEnfant(idx, 'nom', e.target.value)} placeholder="Nom" />
                                                    <Input label="Prénom" value={enf.prenom} onChange={e => setEnfant(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                                    <Input label="Date Naissance" type="date" value={enf.dateNaissance} onChange={e => setEnfant(idx, 'dateNaissance', e.target.value)} />
                                                    <Select label="Sexe" value={enf.sexe} onChange={e => setEnfant(idx, 'sexe', e.target.value)}>
                                                        <option value="M">Garçon (M)</option>
                                                        <option value="F">Fille (F)</option>
                                                    </Select>
                                                    <div className="flex items-end pb-1">
                                                        <Checkbox label="Enfant à charge" checked={enf.aCharge} onChange={e => setEnfant(idx, 'aCharge', e.target.checked)} />
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                )}
                            </div>
                        </div>
                    )}

                    {/* Urgence */}
                    {activeTab === 'urgence' && (
                        <div>
                            <div className="flex items-center justify-between mb-3">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400">Contacts d'urgence</h3>
                                <Button type="button" onClick={addContact} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                                    Ajouter
                                </Button>
                            </div>
                            <div className="space-y-3">
                                {formData.contactsUrgence.map((ct, idx) => (
                                    <div key={idx} className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-700 relative">
                                        {formData.contactsUrgence.length > 1 && (
                                            <button type="button" onClick={() => removeContact(idx)} className="absolute top-3 right-3 text-danger-500 hover:bg-danger-500/10 p-1 rounded-sm transition-colors cursor-pointer">
                                                <Trash2 size={14} />
                                            </button>
                                        )}
                                        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                            <Input label="Nom" value={ct.nom} onChange={e => setContact(idx, 'nom', e.target.value)} placeholder="Nom" />
                                            <Input label="Prénom" value={ct.prenom} onChange={e => setContact(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                            <Select label="Lien de Parenté" value={ct.lienParente} onChange={e => setContact(idx, 'lienParente', e.target.value)}>
                                                <option value="Conjoint">Conjoint</option>
                                                <option value="Pere">Père</option>
                                                <option value="Mere">Mère</option>
                                                <option value="Frere">Frère</option>
                                                <option value="Soeur">Sœur</option>
                                                <option value="Enfant">Enfant</option>
                                                <option value="Autre">Autre</option>
                                            </Select>
                                            <Input label="Téléphone" value={ct.telephone} onChange={e => setContact(idx, 'telephone', e.target.value)} placeholder="Téléphone" />
                                            <div className="md:col-span-2">
                                                <Input label="Adresse" value={ct.adresse} onChange={e => setContact(idx, 'adresse', e.target.value)} placeholder="Adresse" />
                                            </div>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* CNaPS & Banque */}
                    {activeTab === 'banque' && (
                        <div className="space-y-6">
                            <div>
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Sécurité Sociale</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Input label="N° Affiliation CNaPS" value={formData.numAffiliationCnaps} onChange={e => set('numAffiliationCnaps', e.target.value)} placeholder="N° CNaPS" />
                                    <Input label="Date Déclaration" type="date" value={formData.dateDeclaration} onChange={e => set('dateDeclaration', e.target.value)} />
                                </div>
                            </div>
                            <div className="border-t border-ink-150 dark:border-ink-700 pt-4">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-400 mb-3">Compte Bancaire Principal</h3>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                                    <Select label="Banque" value={formData.compteBanquePrincipal.idBanque} onChange={e => setNested('compteBanquePrincipal', 'idBanque', e.target.value)}>
                                        <option value="">-- Sélectionner --</option>
                                        {banques.map(b => <option key={b.idBanque} value={b.idBanque}>{b.codeBanque} - {b.nomBanque}</option>)}
                                    </Select>
                                    <Input label="Agence" value={formData.compteBanquePrincipal.agence} onChange={e => setNested('compteBanquePrincipal', 'agence', e.target.value)} placeholder="Agence" />
                                    <Input label="N° Compte / RIB" value={formData.compteBanquePrincipal.numCompteRib} onChange={e => setNested('compteBanquePrincipal', 'numCompteRib', e.target.value)} placeholder="00000-00000-00000000000-00" />
                                    <Input label="Titulaire du Compte" value={formData.compteBanquePrincipal.titulaireCompte} onChange={e => setNested('compteBanquePrincipal', 'titulaireCompte', e.target.value)} placeholder="Nom du titulaire" />
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Footer Boutons */}
                    <div className="flex justify-end gap-3 pt-4 border-t border-ink-150 dark:border-ink-700">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">Annuler</Button>
                        <Button type="submit" variant="primary" size="sm" loading={isSubmitting}>
                            {employeData ? 'Enregistrer' : 'Créer la Fiche'}
                        </Button>
                    </div>
                </form>
            </div>
        </div>
    );
}
