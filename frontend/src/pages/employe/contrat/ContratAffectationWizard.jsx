import { useState, useEffect, useCallback } from 'react';
import { Briefcase, UserCheck, CheckCircle2, ChevronRight, ChevronLeft } from 'lucide-react';
import SlideOver from '@/components/ui/SlideOver';
import Stepper from '@/components/ui/Stepper';
import Button from '@/components/ui/Button';
import Input, { Select, Textarea, Checkbox } from '@/components/ui/Input';
import Badge from '@/components/ui/Badge';
import Alert from '@/components/ui/Alert';
import referentielService from '@/api/referentielService';
import contratService from '@/api/contratService';
import employeService from '@/api/employeService';

const WIZARD_STEPS = [
    { id: 'contrat', label: '1. Contrat de travail' },
    { id: 'affectation', label: '2. Affectation & Poste' },
    { id: 'recap', label: '3. Récapitulatif & Validation' },
];

export default function ContratAffectationWizard({
    isOpen,
    onClose,
    onSuccess,
    idEmploye,
    employeNomComplet = ''
}) {
    const [currentStep, setCurrentStep] = useState(0);
    const [loading, setLoading] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    // Référentiels
    const [filiales, setFiliales] = useState([]);
    const [typesContrat, setTypesContrat] = useState([]);
    const [typesIntervenant, setTypesIntervenant] = useState([]);
    const [sites, setSites] = useState([]);
    const [departements, setDepartements] = useState([]);
    const [services, setServices] = useState([]);
    const [fonctions, setFonctions] = useState([]);
    const [motifsMouvement, setMotifsMouvement] = useState([]);
    const [categoriesPro, setCategoriesPro] = useState([]);
    const [modesPaie, setModesPaie] = useState([]);
    const [responsablesN1, setResponsablesN1] = useState([]);
    const [existingContrats, setExistingContrats] = useState([]);

    // Form Data - Étape 1 : Contrat
    const [contratForm, setContratForm] = useState({
        idFiliale: '',
        idTypeContrat: '',
        idTypeIntervenant: '',
        numeroContrat: '',
        dateSignature: new Date().toISOString().split('T')[0],
        dateDebut: new Date().toISOString().split('T')[0],
        dateFin: '',
        hasEssai: false,
        dateFinEssai: '',
        essaiRenouvele: false,
        comptabiliseEffectif: true,
        estActif: true,
        idContratPrecedent: ''
    });

    // Form Data - Étape 2 : Affectation
    const [affectationForm, setAffectationForm] = useState({
        idMotifMouvement: '',
        idSite: '',
        idDepartement: '',
        idService: '',
        idFonction: '',
        idResponsableHierarchique: '',
        idCategorieProfessionnelle: '',
        idModeCalculPaie: '',
        salaireBase: '',
        dateDebut: new Date().toISOString().split('T')[0],
        commentaire: ''
    });

    const loadData = useCallback(async () => {
        if (!isOpen || !idEmploye) return;
        setLoading(true);
        setError('');
        try {
            const [
                filRes, tcRes, tiRes, siteList, depList, srvList, fncList, empList, carRes, contratsRes
            ] = await Promise.all([
                referentielService.getFiliales(true),
                referentielService.getTypesContrat(true),
                referentielService.getTypesIntervenant(true),
                referentielService.getSites(true),
                referentielService.getDepartements(true),
                referentielService.getServices(true),
                referentielService.getFonctions(true),
                employeService.getEmployes({ actif: true }),
                contratService.getAllReferentielsCarriere(),
                contratService.getContratsByEmploye(idEmploye)
            ]);

            setFiliales(filRes || []);
            setTypesContrat(tcRes || []);
            setTypesIntervenant(tiRes || []);
            setSites(siteList || []);
            setDepartements(depList || []);
            setServices(srvList || []);
            setFonctions(fncList || []);
            setResponsablesN1((empList || []).filter(e => String(e.idEmploye) !== String(idEmploye)));
            setExistingContrats(contratsRes || []);

            if (carRes) {
                setMotifsMouvement(carRes.motifsMouvement || []);
                setCategoriesPro(carRes.categoriesPro || []);
                setModesPaie(carRes.modesPaie || []);
            }

            // Définir valeurs par défaut
            const activePrev = (contratsRes || []).find(c => c.estActif);
            setContratForm(prev => ({
                ...prev,
                idFiliale: prev.idFiliale || filRes[0]?.idFiliale || '',
                idTypeContrat: prev.idTypeContrat || tcRes[0]?.idTypeContrat || '',
                idTypeIntervenant: prev.idTypeIntervenant || tiRes[0]?.idTypeIntervenant || '',
                idContratPrecedent: activePrev ? activePrev.idContrat : ''
            }));

            if (carRes) {
                setAffectationForm(prev => ({
                    ...prev,
                    idMotifMouvement: prev.idMotifMouvement || carRes.motifsMouvement?.[0]?.idMotifMouvement || '',
                    idSite: prev.idSite || siteList[0]?.idSite || '',
                    idDepartement: prev.idDepartement || depList[0]?.idDepartement || '',
                    idCategorieProfessionnelle: prev.idCategorieProfessionnelle || carRes.categoriesPro?.[0]?.idCategorieProfessionnelle || '',
                    idModeCalculPaie: prev.idModeCalculPaie || carRes.modesPaie?.[0]?.idModeCalculPaie || ''
                }));
            }
        } catch (err) {
            console.error('Erreur chargement assistant contrat/affectation:', err);
            setError('Impossible de charger les données de référence.');
        } finally {
            setLoading(false);
        }
    }, [isOpen, idEmploye]);

    useEffect(() => {
        if (isOpen) {
            setCurrentStep(0);
            loadData();
        }
    }, [isOpen, loadData]);

    const filteredServices = affectationForm.idDepartement
        ? services.filter(s => String(s.idDepartement) === String(affectationForm.idDepartement))
        : services;

    const selectedTypeContrat = typesContrat.find(t => String(t.idTypeContrat) === String(contratForm.idTypeContrat));
    const codeUpper = (selectedTypeContrat?.code || '').toUpperCase();
    const libUpper = (selectedTypeContrat?.libelle || '').toUpperCase();
    const isCDI = codeUpper.includes('CDI') || libUpper.includes('INDÉTERMINÉE') || libUpper.includes('INDETERMINEE');
    const isCDD = !isCDI && (codeUpper.includes('CDD') || libUpper.includes('DÉTERMINÉE') || libUpper.includes('DETERMINEE'));

    // Validation par étape
    const validateStep1 = () => {
        if (!contratForm.idFiliale) return 'Veuillez sélectionner la filiale de rattachement.';
        if (!contratForm.idTypeContrat) return 'Veuillez sélectionner le type de contrat.';
        if (!contratForm.idTypeIntervenant) return "Veuillez sélectionner le type d'intervenant.";
        if (!contratForm.dateDebut) return 'La date de début du contrat est obligatoire.';
        if (isCDD && !contratForm.dateFin) return 'La date de fin est obligatoire pour un contrat à durée déterminée (CDD).';
        if (contratForm.hasEssai && !contratForm.dateFinEssai) return 'Veuillez indiquer la date de fin de la période d’essai.';
        return null;
    };

    const validateStep2 = () => {
        if (!affectationForm.idMotifMouvement) return 'Veuillez sélectionner le motif de mouvement (Embauche, Promotion...).';
        if (!affectationForm.idSite) return 'Veuillez sélectionner le site d’affectation.';
        if (!affectationForm.idDepartement) return 'Veuillez sélectionner le département.';
        if (!affectationForm.idService) return 'Veuillez sélectionner le service.';
        if (!affectationForm.idFonction) return 'Veuillez sélectionner la fonction / poste.';
        if (!affectationForm.idCategorieProfessionnelle) return 'Veuillez sélectionner la catégorie professionnelle.';
        if (!affectationForm.idModeCalculPaie) return 'Veuillez sélectionner le mode de calcul de paie.';
        if (!affectationForm.salaireBase || Number(affectationForm.salaireBase) <= 0) return 'Le salaire de base brut doit être supérieur à 0.';
        return null;
    };

    const handleNext = () => {
        setError('');
        if (currentStep === 0) {
            const err = validateStep1();
            if (err) { setError(err); return; }
            setCurrentStep(1);
        } else if (currentStep === 1) {
            const err = validateStep2();
            if (err) { setError(err); return; }
            setCurrentStep(2);
        }
    };

    const handleBack = () => {
        setError('');
        if (currentStep > 0) setCurrentStep(prev => prev - 1);
    };

    const handleFinalSubmit = async () => {
        setError('');
        setSubmitting(true);
        try {
            // 1. Créer le contrat de travail
            const contratPayload = {
                idFiliale: Number(contratForm.idFiliale),
                idTypeContrat: Number(contratForm.idTypeContrat),
                idTypeIntervenant: Number(contratForm.idTypeIntervenant),
                numeroContrat: contratForm.numeroContrat || null,
                dateSignature: contratForm.dateSignature || null,
                dateDebut: contratForm.dateDebut,
                dateFin: contratForm.dateFin || null,
                dateFinEssai: contratForm.hasEssai ? contratForm.dateFinEssai : null,
                essaiRenouvele: contratForm.hasEssai ? contratForm.essaiRenouvele : false,
                comptabiliseEffectif: contratForm.comptabiliseEffectif,
                estActif: contratForm.estActif,
                idContratPrecedent: contratForm.idContratPrecedent ? Number(contratForm.idContratPrecedent) : null
            };

            const newContrat = await contratService.createContrat(idEmploye, contratPayload);
            const idContratCree = newContrat.idContrat;

            // 2. Créer l'affectation rattachée à ce nouveau contrat
            const affectationPayload = {
                idEmploye: Number(idEmploye),
                idContrat: Number(idContratCree),
                idMotifMouvement: Number(affectationForm.idMotifMouvement),
                idSite: Number(affectationForm.idSite),
                idDepartement: Number(affectationForm.idDepartement),
                idService: Number(affectationForm.idService),
                idFonction: Number(affectationForm.idFonction),
                idResponsableHierarchique: affectationForm.idResponsableHierarchique ? Number(affectationForm.idResponsableHierarchique) : null,
                idCategorieProfessionnelle: Number(affectationForm.idCategorieProfessionnelle),
                idModeCalculPaie: Number(affectationForm.idModeCalculPaie),
                salaireBase: Number(affectationForm.salaireBase),
                dateDebut: affectationForm.dateDebut || contratForm.dateDebut,
                commentaire: affectationForm.commentaire || null
            };

            await contratService.createAffectation(idEmploye, affectationPayload);

            if (typeof onSuccess === 'function') onSuccess();
            onClose();
        } catch (err) {
            console.error('Erreur soumission wizard contrat/affectation:', err);
            setError(err.response?.data || 'Erreur lors de la validation du dossier contrat & affectation.');
        } finally {
            setSubmitting(false);
        }
    };

    // Obtenir libellés pour le récapitulatif
    const getLibelle = (list, key) => {
        if (!key || !list) return '-';
        const kStr = String(key);
        const item = (list || []).find(x => 
            String(x.idTypeIntervenant) === kStr ||
            String(x.idMotifMouvement) === kStr ||
            String(x.idCategorieProfessionnelle) === kStr ||
            String(x.idModeCalculPaie) === kStr ||
            String(x.idFiliale) === kStr ||
            String(x.idTypeContrat) === kStr ||
            String(x.idSite) === kStr ||
            String(x.idDepartement) === kStr ||
            String(x.idService) === kStr ||
            String(x.idFonction) === kStr ||
            String(x[Object.keys(x)[0]]) === kStr
        );
        return item ? (item.libelle || item.raisonSociale || item.nomComplet || item.code || '-') : '-';
    };

    const footerElement = (
        <div className="flex items-center justify-between w-full">
            <Button
                type="button"
                variant="ghost"
                size="sm"
                onClick={currentStep === 0 ? onClose : handleBack}
                disabled={submitting}
                iconLeft={currentStep > 0 ? <ChevronLeft size={16} /> : undefined}
            >
                {currentStep === 0 ? 'Annuler' : 'Étape précédente'}
            </Button>

            {currentStep < 2 ? (
                <Button
                    type="button"
                    variant="primary"
                    size="sm"
                    onClick={handleNext}
                    iconRight={<ChevronRight size={16} />}
                >
                    Continuer
                </Button>
            ) : (
                <Button
                    type="button"
                    variant="success"
                    size="sm"
                    onClick={handleFinalSubmit}
                    loading={submitting}
                    iconLeft={<CheckCircle2 size={16} />}
                >
                    Valider et Enregistrer
                </Button>
            )}
        </div>
    );

    return (
        <SlideOver
            isOpen={isOpen}
            onClose={onClose}
            title="Assistant Carrière & Contrat de Travail"
            subtitle={`Gestion contractuelle et affectation pour ${employeNomComplet}`}
            icon={<Briefcase size={20} />}
            width="2xl"
            footer={footerElement}
        >
            <Stepper
                steps={WIZARD_STEPS}
                currentStep={currentStep}
                onStepClick={(index) => {
                    if (index < currentStep) setCurrentStep(index);
                }}
            />

            <div className="p-6 space-y-6">
                {error && (
                    <Alert type="error" onClose={() => setError('')}>
                        {error}
                    </Alert>
                )}

                {loading ? (
                    <div className="py-12 text-center text-xs text-ink-400">Chargement des référentiels...</div>
                ) : (
                    <>
                        {/* ÉTAPE 1 : CONTRAT DE TRAVAIL */}
                        {currentStep === 0 && (
                            <div className="space-y-6">
                                <div className="p-4 bg-brand-500/5 border border-brand-500/20">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-600 dark:text-brand-400">Étape 1 sur 3 — Définition du Contrat de Travail</h3>
                                    <p className="text-xs text-ink-400 mt-1">Renseignez la filiale d'embauche, le type de contrat et les dates clés d'engagement.</p>
                                </div>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <Select
                                        label="Filiale de Rattachement"
                                        required
                                        value={contratForm.idFiliale}
                                        onChange={e => setContratForm({ ...contratForm, idFiliale: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner une filiale --</option>
                                        {filiales.map(f => (
                                            <option key={f.idFiliale} value={f.idFiliale}>
                                                {f.raisonSociale} ({f.codeFiliale})
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Type de Contrat (CDI, CDD, Stage...)"
                                        required
                                        value={contratForm.idTypeContrat}
                                        onChange={e => setContratForm({ ...contratForm, idTypeContrat: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner un type --</option>
                                        {typesContrat.map(tc => (
                                            <option key={tc.idTypeContrat} value={tc.idTypeContrat}>
                                                {tc.libelle} ({tc.code})
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Type d'Intervenant"
                                        required
                                        value={contratForm.idTypeIntervenant}
                                        onChange={e => setContratForm({ ...contratForm, idTypeIntervenant: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner --</option>
                                        {typesIntervenant.map(ti => (
                                            <option key={ti.idTypeIntervenant} value={ti.idTypeIntervenant}>
                                                {ti.libelle}
                                            </option>
                                        ))}
                                    </Select>

                                    <Input
                                        label="Numéro de Contrat / Réf"
                                        value={contratForm.numeroContrat}
                                        onChange={e => setContratForm({ ...contratForm, numeroContrat: e.target.value })}
                                        placeholder="Ex: CT-2026-0045"
                                    />
                                </div>

                                <div className="pt-4 border-t border-ink-150 dark:border-ink-700 space-y-4">
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">Dates d'Effet & Échéance</h4>
                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                        <Input
                                            label="Date de Signature"
                                            type="date"
                                            value={contratForm.dateSignature}
                                            onChange={e => setContratForm({ ...contratForm, dateSignature: e.target.value })}
                                        />

                                        <Input
                                            label="Date de Début (Prise d'effet)"
                                            type="date"
                                            required
                                            value={contratForm.dateDebut}
                                            onChange={e => setContratForm({ ...contratForm, dateDebut: e.target.value })}
                                        />

                                        <div className="space-y-1">
                                            <Input
                                                label={isCDD ? "Date de Fin (Obligatoire CDD)" : "Date de Fin (Vide si CDI)"}
                                                type="date"
                                                value={contratForm.dateFin}
                                                onChange={e => setContratForm({ ...contratForm, dateFin: e.target.value })}
                                            />
                                            <p className="text-[11px] text-ink-400">
                                                {isCDD ? 'Saisir la date d’échéance du contrat déterminé.' : 'Laisser vide pour un contrat à durée indéterminée.'}
                                            </p>
                                        </div>

                                        {existingContrats.length > 0 && (
                                            <Select
                                                label="Suite au Contrat Précédent (Si Renouvellement)"
                                                value={contratForm.idContratPrecedent}
                                                onChange={e => setContratForm({ ...contratForm, idContratPrecedent: e.target.value })}
                                            >
                                                <option value="">-- Aucun (Nouveau contrat autonome) --</option>
                                                {existingContrats.map(c => (
                                                    <option key={c.idContrat} value={c.idContrat}>
                                                        {c.numeroContrat || `N°${c.idContrat}`} ({c.typeContratLibelle}) — {c.estActif ? 'Actif' : 'Clôturé'}
                                                    </option>
                                                ))}
                                            </Select>
                                        )}
                                    </div>
                                </div>

                                <div className="pt-4 border-t border-ink-150 dark:border-ink-700 space-y-3">
                                    <div className="flex items-center justify-between bg-ink-50 dark:bg-ink-950 p-3 border border-ink-150 dark:border-ink-800">
                                        <div>
                                            <p className="text-xs font-bold text-ink-900 dark:text-white">Ce contrat comporte-t-il une période d'essai ?</p>
                                            <p className="text-[11px] text-ink-400">Cocher pour renseigner la période de test initiale.</p>
                                        </div>
                                        <Checkbox
                                            checked={contratForm.hasEssai}
                                            onChange={e => setContratForm({ ...contratForm, hasEssai: e.target.checked })}
                                            label={contratForm.hasEssai ? 'Oui (Avec essai)' : 'Non (Sans essai)'}
                                        />
                                    </div>

                                    {contratForm.hasEssai && (
                                        <div className="p-4 bg-ink-50 dark:bg-ink-950 border border-ink-150 dark:border-ink-800 grid grid-cols-1 sm:grid-cols-2 gap-4">
                                            <Input
                                                label="Date Fin Période d'Essai"
                                                type="date"
                                                value={contratForm.dateFinEssai}
                                                onChange={e => setContratForm({ ...contratForm, dateFinEssai: e.target.value })}
                                            />
                                            <div className="flex items-center pt-5">
                                                <Checkbox
                                                    label="Période d'essai renouvelée (Prolongée)"
                                                    checked={contratForm.essaiRenouvele}
                                                    onChange={e => setContratForm({ ...contratForm, essaiRenouvele: e.target.checked })}
                                                />
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* ÉTAPE 2 : AFFECTATION & POSTE */}
                        {currentStep === 1 && (
                            <div className="space-y-6">
                                <div className="p-4 bg-brand-500/5 border border-brand-500/20">
                                    <h3 className="text-xs font-bold uppercase tracking-wider text-brand-600 dark:text-brand-400">Étape 2 sur 3 — Affectation de Poste & Salaire de Base</h3>
                                    <p className="text-xs text-ink-400 mt-1">Rattachez l'employé à un poste, un site, une structure hiérarchique et définissez sa rémunération brute.</p>
                                </div>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                    <Select
                                        label="Motif du Mouvement"
                                        required
                                        value={affectationForm.idMotifMouvement}
                                        onChange={e => setAffectationForm({ ...affectationForm, idMotifMouvement: e.target.value })}
                                    >
                                        {motifsMouvement.map(mm => (
                                            <option key={mm.idMotifMouvement} value={mm.idMotifMouvement}>
                                                {mm.libelle} ({mm.code})
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Fonction / Poste Occupé"
                                        required
                                        value={affectationForm.idFonction}
                                        onChange={e => setAffectationForm({ ...affectationForm, idFonction: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner une fonction --</option>
                                        {fonctions.map(f => (
                                            <option key={f.idFonction} value={f.idFonction}>
                                                {f.libelle} ({f.codeFonction})
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Site d'Affectation"
                                        required
                                        value={affectationForm.idSite}
                                        onChange={e => setAffectationForm({ ...affectationForm, idSite: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner un site --</option>
                                        {sites.map(s => (
                                            <option key={s.idSite} value={s.idSite}>
                                                {s.libelle} ({s.codeSite})
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Département"
                                        required
                                        value={affectationForm.idDepartement}
                                        onChange={e => setAffectationForm({ ...affectationForm, idDepartement: e.target.value, idService: '' })}
                                    >
                                        <option value="">-- Sélectionner un département --</option>
                                        {departements.map(d => (
                                            <option key={d.idDepartement} value={d.idDepartement}>
                                                {d.libelle}
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Service"
                                        required
                                        value={affectationForm.idService}
                                        onChange={e => setAffectationForm({ ...affectationForm, idService: e.target.value })}
                                    >
                                        <option value="">-- Sélectionner un service --</option>
                                        {filteredServices.map(s => (
                                            <option key={s.idService} value={s.idService}>
                                                {s.libelle}
                                            </option>
                                        ))}
                                    </Select>

                                    <Select
                                        label="Responsable Direct (N+1)"
                                        value={affectationForm.idResponsableHierarchique}
                                        onChange={e => setAffectationForm({ ...affectationForm, idResponsableHierarchique: e.target.value })}
                                    >
                                        <option value="">-- Direction générale / Aucun --</option>
                                        {responsablesN1.map(r => (
                                            <option key={r.idEmploye} value={r.idEmploye}>
                                                {r.nomComplet || `${r.nom} ${r.prenom}`} ({r.matricule})
                                            </option>
                                        ))}
                                    </Select>
                                </div>

                                <div className="pt-4 border-t border-ink-150 dark:border-ink-700 space-y-4">
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">Rémunération & Classification</h4>
                                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                                        <Select
                                            label="Catégorie Professionnelle"
                                            required
                                            value={affectationForm.idCategorieProfessionnelle}
                                            onChange={e => setAffectationForm({ ...affectationForm, idCategorieProfessionnelle: e.target.value })}
                                        >
                                            {categoriesPro.map(cp => (
                                                <option key={cp.idCategorieProfessionnelle} value={cp.idCategorieProfessionnelle}>
                                                    {cp.libelle} ({cp.code})
                                                </option>
                                            ))}
                                        </Select>

                                        <Select
                                            label="Mode de Paie"
                                            required
                                            value={affectationForm.idModeCalculPaie}
                                            onChange={e => setAffectationForm({ ...affectationForm, idModeCalculPaie: e.target.value })}
                                        >
                                            {modesPaie.map(mp => (
                                                <option key={mp.idModeCalculPaie} value={mp.idModeCalculPaie}>
                                                    {mp.libelle}
                                                </option>
                                            ))}
                                        </Select>

                                        <Input
                                            label="Salaire de Base Brut (MGA)"
                                            type="number"
                                            required
                                            value={affectationForm.salaireBase}
                                            onChange={e => setAffectationForm({ ...affectationForm, salaireBase: e.target.value })}
                                            placeholder="Ex: 1500000"
                                        />
                                    </div>

                                    <Textarea
                                        label="Commentaire / Notes d'affectation"
                                        rows={2}
                                        value={affectationForm.commentaire}
                                        onChange={e => setAffectationForm({ ...affectationForm, commentaire: e.target.value })}
                                        placeholder="Précisions sur la prise de fonction ou les conditions particulières..."
                                    />
                                </div>
                            </div>
                        )}

                        {/* ÉTAPE 3 : RÉCAPITULATIF & CONFIRMATION */}
                        {currentStep === 2 && (
                            <div className="space-y-6">
                                <div className="p-4 bg-success-500/10 border border-success-500/20 flex items-center gap-3">
                                    <CheckCircle2 size={20} className="text-success-500 shrink-0" />
                                    <div>
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-success-600 dark:text-success-400">Étape 3 sur 3 — Récapitulatif et Validation Finale</h3>
                                        <p className="text-xs text-ink-400">Vérifiez les données du dossier avant enregistrement définitif.</p>
                                    </div>
                                </div>

                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    {/* Bloc 1 : Contrat */}
                                    <div className="border border-ink-150 dark:border-ink-700 p-5 space-y-3">
                                        <div className="flex items-center justify-between pb-2 border-b border-ink-150 dark:border-ink-700">
                                            <span className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-1.5">
                                                <Briefcase size={14} /> Contrat de Travail
                                            </span>
                                            <Badge variant="primary" size="sm">Contrat Actif</Badge>
                                        </div>
                                        <div className="space-y-2 text-xs">
                                            <p><span className="text-ink-400">Filiale :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(filiales, contratForm.idFiliale, 'raisonSociale')}</strong></p>
                                            <p><span className="text-ink-400">Type de Contrat :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(typesContrat, contratForm.idTypeContrat)}</strong></p>
                                            <p><span className="text-ink-400">Type Intervenant :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(typesIntervenant, contratForm.idTypeIntervenant)}</strong></p>
                                            <p><span className="text-ink-400">N° Contrat :</span> <strong className="text-ink-800 dark:text-white font-mono">{contratForm.numeroContrat || 'Généré automatiquement'}</strong></p>
                                            {contratForm.idContratPrecedent && (
                                                <p><span className="text-ink-400">Suite au Contrat :</span> <strong className="text-brand-500 font-mono">{existingContrats.find(c => String(c.idContrat) === String(contratForm.idContratPrecedent))?.numeroContrat || `N°${contratForm.idContratPrecedent}`}</strong></p>
                                            )}
                                            <p><span className="text-ink-400">Date Prise d'Effet :</span> <strong className="text-ink-800 dark:text-white">{contratForm.dateDebut}</strong></p>
                                            <p><span className="text-ink-400">Date de Fin :</span> <strong className="text-ink-800 dark:text-white">{contratForm.dateFin || 'Indéterminée (CDI)'}</strong></p>
                                            <p><span className="text-ink-400">Période d'Essai :</span> <strong className="text-ink-800 dark:text-white">{contratForm.hasEssai ? `Jusqu'au ${contratForm.dateFinEssai}` : 'Sans essai'}</strong></p>
                                        </div>
                                    </div>

                                    {/* Bloc 2 : Affectation */}
                                    <div className="border border-ink-150 dark:border-ink-700 p-5 space-y-3">
                                        <div className="flex items-center justify-between pb-2 border-b border-ink-150 dark:border-ink-700">
                                            <span className="text-xs font-bold uppercase tracking-wider text-brand-500 flex items-center gap-1.5">
                                                <UserCheck size={14} /> Affectation & Poste
                                            </span>
                                            <Badge variant="success" size="sm">Poste Actuel</Badge>
                                        </div>
                                        <div className="space-y-2 text-xs">
                                            <p><span className="text-ink-400">Motif Mouvement :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(motifsMouvement, affectationForm.idMotifMouvement)}</strong></p>
                                            <p><span className="text-ink-400">Fonction / Poste :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(fonctions, affectationForm.idFonction)}</strong></p>
                                            <p><span className="text-ink-400">Site & Structure :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(sites, affectationForm.idSite)} ➔ {getLibelle(departements, affectationForm.idDepartement)} / {getLibelle(services, affectationForm.idService)}</strong></p>
                                            <p><span className="text-ink-400">Classification :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(categoriesPro, affectationForm.idCategorieProfessionnelle)}</strong></p>
                                            <p><span className="text-ink-400">Salaire de Base Brut :</span> <strong className="text-brand-500 font-mono text-sm">{Number(affectationForm.salaireBase).toLocaleString()} MGA</strong></p>
                                            <p><span className="text-ink-400">Mode de Paie :</span> <strong className="text-ink-800 dark:text-white">{getLibelle(modesPaie, affectationForm.idModeCalculPaie)}</strong></p>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        )}
                    </>
                )}
            </div>
        </SlideOver>
    );
}
