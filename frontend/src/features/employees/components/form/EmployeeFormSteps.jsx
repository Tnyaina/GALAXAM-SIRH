import {
    Wand2,
    Upload,
    Camera,
    Trash2,
    Phone,
    Plus,
    Baby,
    Users,
    ShieldAlert
} from 'lucide-react';
import Input, { Select, Textarea, Checkbox, AutocompleteInput } from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import Loading from '@/components/ui/feedback/Loading';
import AuthenticatedImage from '@/components/ui/data-display/AuthenticatedImage';
import { fmtDateInput, getTodayDateString, calculateAge } from '@/utils/dateUtils';
import { VILLES_MADAGASCAR } from '@/constants/madagascarGeo';
import {
    GENRES_SEXE,
    SITUATIONS_FAMILIALES,
    REGLES_FISCALES_IRSA,
    GENRES_ENFANT,
    PROFESSIONS_CONJOINT_SUGGESTIONS,
    LIENS_PARENTE
} from '@/constants/employeConstants';
import useFiscalConfig from '@/features/employees/hooks/useFiscalConfig';

// ==========================================
// 1. ÉTAPE IDENTITÉ & ÉTAT CIVIL
// ==========================================
export function CiviliteStep({
    formData,
    set,
    villesStandards = [],
    nationalitesStandards = [],
    onGenerateMatricule,
    onBlurMatricule,
    matriculeError = '',
    generatingMatricule = false,
    fieldErrors = {},
    onFieldBlur,
}) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="flex flex-col gap-1">
                <label className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg)]">
                    Matricule <span className="text-[var(--color-danger-500)] ml-1">*</span>
                </label>
                <div className="flex gap-2">
                    <Input 
                        value={formData.matricule} 
                        onChange={e => set('matricule', e.target.value)} 
                        onBlur={onBlurMatricule}
                        error={fieldErrors.matricule || matriculeError}
                        placeholder="Ex: 0001" 
                        containerClassName="flex-1"
                    />
                    <Button 
                        type="button" 
                        variant="outline" 
                        onClick={onGenerateMatricule}
                        loading={generatingMatricule}
                        className="px-3 shrink-0 self-stretch flex items-center justify-center border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)]"
                        title="Générer automatiquement"
                    >
                        <Wand2 size={15} />
                    </Button>
                </div>
            </div>

            <Select 
                label="Sexe" 
                required 
                value={formData.sexe} 
                onChange={e => set('sexe', e.target.value)}
                error={fieldErrors.sexe}
            >
                {GENRES_SEXE.map(g => (
                    <option key={g.value} value={g.value}>{g.label}</option>
                ))}
            </Select>

            <Input 
                label="Nom" 
                required 
                value={formData.nom} 
                onChange={e => set('nom', e.target.value)} 
                onBlur={() => onFieldBlur?.('nom')}
                error={fieldErrors.nom}
                placeholder="Nom de famille" 
            />

            <Input 
                label="Prénom(s)" 
                required 
                value={formData.prenom} 
                onChange={e => set('prenom', e.target.value)} 
                onBlur={() => onFieldBlur?.('prenom')}
                error={fieldErrors.prenom}
                placeholder="Prénoms" 
            />

            <Input 
                label="Date de Naissance" 
                required 
                type="date" 
                max={fmtDateInput(new Date(new Date().setFullYear(new Date().getFullYear() - REGLES_FISCALES_IRSA.AGE_MIN_LEGAL_EMPLOYE)))}
                value={formData.dateNaissance} 
                onChange={e => set('dateNaissance', e.target.value)} 
                onBlur={() => onFieldBlur?.('dateNaissance')}
                error={fieldErrors.dateNaissance}
                hint={`Âge minimum légal : ${REGLES_FISCALES_IRSA.AGE_MIN_LEGAL_EMPLOYE} ans`}
            />
            
            <AutocompleteInput 
                label="Lieu de Naissance" 
                value={formData.lieuNaissance} 
                onChange={e => set('lieuNaissance', e.target.value)} 
                onBlur={() => onFieldBlur?.('lieuNaissance')}
                error={fieldErrors.lieuNaissance}
                placeholder="Ex: Antananarivo, Toamasina..."
                options={villesStandards}
                listId="villes-list"
            />

            <AutocompleteInput 
                label="Nationalité" 
                required 
                value={formData.nationalite} 
                onChange={e => set('nationalite', e.target.value)} 
                onBlur={() => onFieldBlur?.('nationalite')}
                error={fieldErrors.nationalite}
                placeholder="Ex: Malgache, Française..."
                options={nationalitesStandards}
                listId="nationalites-list"
            />

            <Select 
                label="Situation Familiale" 
                value={formData.situationFamiliale} 
                onChange={e => set('situationFamiliale', e.target.value)}
            >
                {SITUATIONS_FAMILIALES.map(s => (
                    <option key={s.value} value={s.value}>{s.label}</option>
                ))}
            </Select>

            <Input 
                label="Père" 
                value={formData.pereNomPrenom} 
                onChange={e => set('pereNomPrenom', e.target.value)} 
                placeholder="Nom et prénom du père" 
            />

            <Input 
                label="Mère" 
                value={formData.mereNomPrenom} 
                onChange={e => set('mereNomPrenom', e.target.value)} 
                placeholder="Nom et prénom de la mère" 
            />
        </div>
    );
}

// ==========================================
// 2. ÉTAPE COORDONNÉES & PHOTO
// ==========================================
export function CoordonneesPhotoStep({
    formData,
    set,
    villesStandards = [],
    uploadingPhoto = false,
    handlePhotoSelect,
    fieldErrors = {},
    onFieldBlur,
}) {
    return (
        <div className="space-y-4">
            {/* Zone Téléversement Photo */}
            <Card padding className="bg-[var(--color-bg-subtle)]/60">
                <div className="pb-2 mb-3 border-b border-[var(--color-border-subtle)]">
                    <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Camera size={14} className="text-[var(--color-brand-600)]" />
                        Photo de profil
                    </label>
                </div>
                <div className="flex items-center gap-6">
                    <div className="w-20 h-20 bg-[var(--color-surface)] border-2 border-dashed border-[var(--color-border)] flex flex-col items-center justify-center relative overflow-hidden flex-shrink-0">
                        <AuthenticatedImage
                            src={formData.photoUrl}
                            alt="Aperçu de la photo"
                            className="w-full h-full object-cover"
                            fallback={(
                                <div className="text-center text-[var(--color-fg-subtle)]">
                                    <Camera size={24} className="mx-auto mb-1" />
                                    <span className="text-[9px] uppercase font-semibold">Aucune</span>
                                </div>
                            )}
                        />
                        {uploadingPhoto && (
                            <div className="absolute inset-0 bg-[var(--color-overlay)]/40 flex items-center justify-center">
                                <Loading type="spinner" color="brand" size="md" />
                            </div>
                        )}
                    </div>
                    <div className="flex-1 space-y-2">
                        <div className="flex items-center gap-3">
                            <label className="inline-flex items-center gap-2 px-3 py-1.5 text-xs font-semibold uppercase tracking-wider bg-[var(--color-brand-500)] hover:bg-[var(--color-brand-600)] text-[var(--color-on-color)] rounded-none cursor-pointer transition-colors">
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
                                <Button 
                                    type="button" 
                                    variant="ghost" 
                                    size="xs"
                                    onClick={() => set('photoUrl', '')} 
                                    className="text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10"
                                    iconLeft={<Trash2 size={13} />}
                                >
                                    Supprimer
                                </Button>
                            )}
                        </div>
                        <p className="text-[11px] text-[var(--color-fg-muted)]">Formats acceptés : PNG, JPG, WEBP (max. 5 Mo).</p>
                    </div>
                </div>
            </Card>

            <Card padding className="space-y-4">
                <div className="pb-2 border-b border-[var(--color-border-subtle)]">
                    <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Phone size={14} className="text-[var(--color-brand-600)]" />
                        Coordonnées & Résidence
                    </h3>
                </div>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    <div className="md:col-span-2">
                        <Textarea 
                            label="Adresse" 
                            required 
                            rows={2} 
                            value={formData.adresse} 
                            onChange={e => set('adresse', e.target.value)} 
                            onBlur={() => onFieldBlur?.('adresse')}
                            error={fieldErrors.adresse}
                            placeholder="Adresse complète de résidence" 
                        />
                    </div>
                    <AutocompleteInput 
                        label="Ville" 
                        value={formData.ville} 
                        onChange={e => set('ville', e.target.value)} 
                        onBlur={() => onFieldBlur?.('ville')}
                        error={fieldErrors.ville}
                        placeholder="Ex: Antananarivo, Toamasina..."
                        options={villesStandards}
                        listId="coord-villes-list"
                    />
                    <Input 
                        label="Téléphone" 
                        required 
                        value={formData.telephone} 
                        onChange={e => set('telephone', e.target.value)} 
                        onBlur={() => onFieldBlur?.('telephone')}
                        error={fieldErrors.telephone}
                        placeholder="Ex: 034 12 345 67" 
                    />
                    <Input 
                        label="Email" 
                        type="email" 
                        value={formData.email} 
                        onChange={e => set('email', e.target.value)} 
                        onBlur={() => onFieldBlur?.('email')}
                        error={fieldErrors.email}
                        placeholder="email@domaine.mg" 
                    />
                </div>
            </Card>
        </div>
    );
}

// ==========================================
// 3. ÉTAPE FAMILLE & CHARGES
// ==========================================
function evaluateIrsaEligibilityWithConfig(enf, fiscalConfig) {
    if (!enf.aCharge) {
        return { eligible: false, age: null, motif: 'Non déclaré à charge du foyer' };
    }
    if (enf.estHandicape) {
        return { eligible: true, age: null, motif: 'Éligible sans limite d\'âge (Situation de handicap attestée)' };
    }
    if (!enf.dateNaissance) {
        return { eligible: false, age: null, motif: 'Date de naissance requise pour calcul fiscal' };
    }
    const age = calculateAge(enf.dateNaissance);
    if (age === null) {
        return { eligible: false, age: null, motif: 'Date de naissance invalide' };
    }
    const now = new Date();
    const ageLimiteDefaut = fiscalConfig?.ageLimiteDefaut ?? 21;
    const ageLimiteEtudiant = fiscalConfig?.ageLimiteEtudiant ?? 25;

    if (age < ageLimiteDefaut) {
        return { eligible: true, age, motif: `Éligible d'office (< ${ageLimiteDefaut} ans révolus : ${age} ans)` };
    }
    if (age < ageLimiteEtudiant && enf.estEtudiant) {
        if (!enf.certificatFourni) {
            return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat de scolarité officiel requis` };
        }
        if (enf.dateFinScolarite) {
            const dFin = new Date(enf.dateFinScolarite);
            if (dFin < now) {
                return { eligible: false, age, motif: `Non déductible (${age} ans) : Certificat de scolarité expiré` };
            }
        }
        return { eligible: true, age, motif: `Éligible étudiant majeur (${ageLimiteDefaut}-${ageLimiteEtudiant} ans : ${age} ans avec certificat valide)` };
    }
    return { eligible: false, age, motif: `Non déductible fiscalement (${age} ans, limite légale de ${ageLimiteDefaut} ans atteinte)` };
}

export function FamilleFormStep({
    formData,
    setNested,
    addEnfant,
    removeEnfant,
    setEnfant,
    villesStandards = VILLES_MADAGASCAR,
}) {
    const { fiscalConfig } = useFiscalConfig();
    const evaluateIrsa = (enf) => evaluateIrsaEligibilityWithConfig(enf, fiscalConfig);
    const enfantsEligiblesCount = formData.enfants.filter(e => evaluateIrsa(e).eligible).length;
    const totalDeductionAr = enfantsEligiblesCount * fiscalConfig.deductionParCharge;

    const sitFam = (formData.situationFamiliale || '').toUpperCase();
    const hasConjoint = sitFam.includes('MARIE') || sitFam.includes('CONCUBIN');

    return (
        <div className="space-y-6">
            {/* Conjoint affiché uniquement si marié ou en concubinage */}
            {hasConjoint && (
                <Card padding className="bg-[var(--color-bg-subtle)]/60">
                    <div className="pb-2 mb-3 border-b border-[var(--color-border-subtle)]">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <Users size={14} className="text-[var(--color-brand-500)]" />
                            Conjoint
                        </h3>
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <Input label="Nom" value={formData.conjoint.nom} onChange={e => setNested('conjoint', 'nom', e.target.value)} placeholder="Nom du conjoint" />
                        <Input label="Prénom" value={formData.conjoint.prenom} onChange={e => setNested('conjoint', 'prenom', e.target.value)} placeholder="Prénom du conjoint" />
                        <Input label="Date Naissance" type="date" max={fmtDateInput(new Date(new Date().setFullYear(new Date().getFullYear() - 16)))} value={formData.conjoint.dateNaissance} onChange={e => setNested('conjoint', 'dateNaissance', e.target.value)} />
                        <AutocompleteInput 
                            label="Lieu de Naissance" 
                            value={formData.conjoint.lieuNaissance || ''} 
                            onChange={e => setNested('conjoint', 'lieuNaissance', e.target.value)} 
                            placeholder="Ex: Antananarivo, Toamasina..." 
                            options={villesStandards}
                        />
                        <Input label="Société / Employeur" value={formData.conjoint.societe} onChange={e => setNested('conjoint', 'societe', e.target.value)} placeholder="Société du conjoint" />
                        <AutocompleteInput 
                            label="Fonction / Profession" 
                            value={formData.conjoint.fonction || ''} 
                            onChange={e => setNested('conjoint', 'fonction', e.target.value)} 
                            placeholder="Ex: Enseignant, Comptable..." 
                            options={PROFESSIONS_CONJOINT_SUGGESTIONS}
                        />
                    </div>
                </Card>
            )}

            {/* Enfants */}
            <div className="space-y-3">
                <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <Baby size={14} className="text-[var(--color-brand-500)]" />
                            Enfants ({formData.enfants.length})
                        </h3>
                        {enfantsEligiblesCount > 0 ? (
                            <Badge variant="success" size="xs" dot>
                                {enfantsEligiblesCount} déductible{enfantsEligiblesCount > 1 ? 's' : ''} (-{totalDeductionAr.toLocaleString('fr-FR')} Ar/mois)
                            </Badge>
                        ) : (
                            <Badge variant="brand" size="xs">
                                IRSA : -{fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar / enfant éligible
                            </Badge>
                        )}
                    </div>
                    {formData.enfants.length > 0 && (
                        <Button type="button" onClick={addEnfant} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                            Ajouter un enfant
                        </Button>
                    )}
                </div>

                {formData.enfants.length === 0 ? (
                    <EmptyState
                        icon={Baby}
                        title="Aucun enfant déclaré"
                        description="Renseignez les enfants à charge du foyer pour appliquer les déductions d'impôt sur salaire."
                        compact
                        actionLabel="Ajouter un enfant"
                        onAction={addEnfant}
                        actionIcon={<Plus size={14} />}
                    />
                ) : (
                    <div className="space-y-4">
                        {formData.enfants.map((enf, idx) => {
                            const evalIrsa = evaluateIrsa(enf);
                            return (
                                <Card key={idx} padding className="space-y-4 relative">
                                    <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2 pr-8">
                                        <div className="flex items-center gap-2">
                                            <span className="text-xs font-bold text-[var(--color-fg)]">Enfant #{idx + 1}</span>
                                            {evalIrsa.age !== null && (
                                                <Badge variant="default" size="xs">
                                                    {evalIrsa.age} ans
                                                </Badge>
                                            )}
                                        </div>
                                        <Button
                                            type="button"
                                            onClick={() => removeEnfant(idx)}
                                            variant="ghost"
                                            size="sm"
                                            className="absolute top-2 right-2 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
                                            aria-label={`Supprimer l'enfant ${idx + 1}`}
                                            icon={<Trash2 size={16} />}
                                        />
                                    </div>

                                    {/* Données d'état civil */}
                                    <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-6 gap-3">
                                        <div className="md:col-span-2">
                                            <Input label="Nom" value={enf.nom} onChange={e => setEnfant(idx, 'nom', e.target.value)} placeholder="Nom" />
                                        </div>
                                        <div className="md:col-span-2">
                                            <Input label="Prénom" value={enf.prenom} onChange={e => setEnfant(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                        </div>
                                        <div className="md:col-span-2">
                                            <Select label="Sexe" value={enf.sexe} onChange={e => setEnfant(idx, 'sexe', e.target.value)}>
                                                {GENRES_ENFANT.map(g => (
                                                    <option key={g.value} value={g.value}>{g.label}</option>
                                                ))}
                                            </Select>
                                        </div>
                                        <div className="md:col-span-3">
                                            <Input label="Date Naissance" type="date" max={getTodayDateString()} value={enf.dateNaissance} onChange={e => setEnfant(idx, 'dateNaissance', e.target.value)} />
                                        </div>
                                        <div className="md:col-span-3">
                                            <AutocompleteInput 
                                                label="Lieu de Naissance" 
                                                value={enf.lieuNaissance || ''} 
                                                onChange={e => setEnfant(idx, 'lieuNaissance', e.target.value)} 
                                                placeholder="Ex: Antananarivo, Toamasina..." 
                                                options={villesStandards}
                                            />
                                        </div>
                                    </div>

                                    {/* Statuts & Déductibilité fiscale */}
                                    <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-3">
                                        <div className="flex flex-wrap items-center gap-5 text-xs">
                                            <Checkbox
                                                label="Enfant à charge du foyer"
                                                checked={enf.aCharge}
                                                onChange={e => setEnfant(idx, 'aCharge', e.target.checked)}
                                            />
                                            <Checkbox
                                                label={`Étudiant majeur (18 - ${fiscalConfig.ageLimiteEtudiant} ans)`}
                                                checked={enf.estEtudiant}
                                                onChange={e => setEnfant(idx, 'estEtudiant', e.target.checked)}
                                            />
                                            <Checkbox
                                                label="En situation de handicap / infirmité"
                                                checked={enf.estHandicape}
                                                onChange={e => setEnfant(idx, 'estHandicape', e.target.checked)}
                                            />
                                        </div>

                                        {/* Champs conditionnels si étudiant */}
                                        {enf.estEtudiant && (
                                            <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] grid grid-cols-1 md:grid-cols-2 gap-3 items-center">
                                                <Input
                                                    label="Date fin de scolarité"
                                                    type="date"
                                                    value={enf.dateFinScolarite || ''}
                                                    onChange={e => setEnfant(idx, 'dateFinScolarite', e.target.value)}
                                                />
                                                <div className="flex items-end pb-1 pt-4 md:pt-0">
                                                    <Checkbox
                                                        label="Certificat de scolarité officiel fourni"
                                                        checked={enf.certificatFourni}
                                                        onChange={e => setEnfant(idx, 'certificatFourni', e.target.checked)}
                                                    />
                                                </div>
                                            </div>
                                        )}

                                        {/* Indicateur visuel d'éligibilité fiscale IRSA */}
                                        <Alert
                                            type={evalIrsa.eligible ? 'success' : 'warning'}
                                            title={evalIrsa.eligible ? `Déduction IRSA active (-${fiscalConfig.deductionParCharge.toLocaleString('fr-FR')} Ar/mois • ${fiscalConfig.codeVersion})` : 'Déduction fiscale non applicable'}
                                            message={evalIrsa.motif}
                                        />
                                    </div>
                                </Card>
                            );
                        })}
                    </div>
                )}
            </div>
        </div>
    );
}

// ==========================================
// 4. ÉTAPE CONTACTS D'URGENCE
// ==========================================
export function UrgenceFormStep({
    formData,
    addContact,
    removeContact,
    setContact,
}) {
    return (
        <div className="space-y-3">
            <div className="flex items-center justify-between">
                <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                    <ShieldAlert size={14} className="text-[var(--color-brand-500)]" />
                    Contacts d'urgence ({formData.contactsUrgence.length})
                </h3>
                {formData.contactsUrgence.length > 0 && (
                    <Button type="button" onClick={addContact} variant="outline" size="xs" iconLeft={<Plus size={14} />}>
                        Ajouter un contact
                    </Button>
                )}
            </div>

            {formData.contactsUrgence.length === 0 ? (
                <EmptyState
                    icon={ShieldAlert}
                    title="Aucun contact d'urgence"
                    description="Ajoutez les coordonnées des proches à joindre en cas d'incident."
                    compact
                    actionLabel="Ajouter un contact"
                    onAction={addContact}
                    actionIcon={<Plus size={14} />}
                />
            ) : (
                <div className="space-y-3">
                    {formData.contactsUrgence.map((ct, idx) => (
                        <Card key={idx} padding className="space-y-3 relative">
                            <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2 pr-8">
                                <span className="text-xs font-bold text-[var(--color-fg)]">Contact #{idx + 1}</span>
                                {formData.contactsUrgence.length > 1 && (
                                    <Button
                                        type="button"
                                        onClick={() => removeContact(idx)}
                                        variant="ghost"
                                        size="sm"
                                        className="absolute top-2 right-2 text-[var(--color-danger-500)] hover:text-[var(--color-danger-600)] hover:bg-[var(--color-danger-500)]/10 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
                                        aria-label={`Supprimer le contact d'urgence ${idx + 1}`}
                                        icon={<Trash2 size={16} />}
                                    />
                                )}
                            </div>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
                                <Input label="Nom" value={ct.nom} onChange={e => setContact(idx, 'nom', e.target.value)} placeholder="Nom" />
                                <Input label="Prénom" value={ct.prenom} onChange={e => setContact(idx, 'prenom', e.target.value)} placeholder="Prénom" />
                                <Select 
                                    label="Lien de Parenté" 
                                    value={ct.lienParente || 'Autre'} 
                                    onChange={e => setContact(idx, 'lienParente', e.target.value)}
                                >
                                    {LIENS_PARENTE.map(l => (
                                        <option key={l.value} value={l.value}>{l.label}</option>
                                    ))}
                                </Select>
                                <Input label="Téléphone" value={ct.telephone} onChange={e => setContact(idx, 'telephone', e.target.value)} placeholder="Téléphone" />
                                <div className="md:col-span-2">
                                    <Input label="Adresse" value={ct.adresse} onChange={e => setContact(idx, 'adresse', e.target.value)} placeholder="Adresse" />
                                </div>
                            </div>
                        </Card>
                    ))}
                </div>
            )}
        </div>
    );
}

export default {
    CiviliteStep,
    CoordonneesPhotoStep,
    FamilleFormStep,
    UrgenceFormStep
};
