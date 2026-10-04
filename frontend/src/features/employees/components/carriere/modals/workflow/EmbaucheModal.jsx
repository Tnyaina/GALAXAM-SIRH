import { useState, useEffect } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { Briefcase, Save, UserPlus, RotateCcw, Handshake } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';
import Loading from '@/components/ui/feedback/Loading';
import SlideOver from '@/components/ui/overlays/SlideOver';
import contratService from '@/features/employees/api/contratService';
import { useContractReferentiels } from '@/features/employees/hooks/useContractReferentiels';
import { fmtDateInput, fmtDateFr } from '@/utils/dateUtils';
import { formatMoneyAr } from '@/utils/moneyUtils';

import ContratFieldsSection, { PeriodeEssaiFieldsSection } from '@/features/employees/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/features/employees/components/carriere/form/AffectationFieldsSection';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';


export default function EmbaucheModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratsList = [],
    initialData = null,
    onSuccess,
}) {
    // Données dérivées de l'historique contractuel.
    const isEditMode = !!initialData;
    const hasHistory = Array.isArray(contratsList) && contratsList.length > 0;
    const dernierContrat = hasHistory
        ? (contratsList.find(ct => ct && ct.codeStatutContrat !== 'BROUILLON' && ct.codeStatutContrat !== 'ANNULE') || contratsList[0])
        : null;

    // Contexte : référentiels nécessaires à l'embauche.
    const {
        filiales, sites, departements, services, fonctions,
        typesContrat, typesIntervenant, categoriesPro, modesPaie, responsables,
        loading: loadingRef,
    } = useContractReferentiels(isOpen, idEmploye);

    // État local : formulaire et soumission.
    const [formData, setFormData] = useState({
        codeTypeEvenement: hasHistory ? 'REPRISE_ANCIENNETE' : 'EMBAUCHE_INITIALE',
        idFiliale: '', idTypeContrat: '', idTypeIntervenant: '', dateDebut: '', dateFin: '',
        dateSignature: '', comptabiliseEffectif: true, numeroContrat: '', description: '',
        commentaire: '', aPeriodeEssai: false, dureeEssaiMois: '3', dateFinEssai: '',
        idSite: '', idDepartement: '', idService: '', idFonction: '', idResponsableHierarchique: '',
        idCategorieProfessionnelle: '', idModeCalculPaie: '', salaireBase: '',
        descriptionAffectation: '', commentaireAffectation: '', dateEffet: '', motif: '',
    });

    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [isDirty, setIsDirty] = useState(false);

    useUnsavedChanges(isOpen && isDirty);

    // Initialisation du formulaire selon le mode création ou modification.
    useEffect(() => {
        if (!isOpen) return;

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || initialData;
            const a = initialData.affectationCible || initialData.affectationSource || initialData;
            const w = initialData.workflow || initialData;

            const dureeEssai = c.dureeEssaiMois ?? initialData.dureeEssaiMois;

            setFormData({
                codeTypeEvenement: w.codeTypeEvenement || (hasHistory ? 'REPRISE_ANCIENNETE' : 'EMBAUCHE_INITIALE'),
                idFiliale: c.idFiliale || initialData.idFiliale || '',
                idTypeContrat: c.idTypeContrat || initialData.idTypeContrat || '',
                idTypeIntervenant: c.idTypeIntervenant || initialData.idTypeIntervenant || '',
                dateDebut: fmtDateInput(c.dateDebutContrat || c.dateDebut || initialData.dateDebutContrat || initialData.dateDebut),
                dateFin: fmtDateInput(c.dateFinContrat || c.dateFin || initialData.dateFinContrat || initialData.dateFin),
                dateSignature: fmtDateInput(c.dateSignature || initialData.dateSignature),
                comptabiliseEffectif: c.comptabiliseEffectif !== undefined ? c.comptabiliseEffectif : (initialData.comptabiliseEffectif !== undefined ? initialData.comptabiliseEffectif : true),
                numeroContrat: c.numeroContratCible || c.numeroContratSource || c.numeroContrat || initialData.numeroContratCible || initialData.numeroContratSource || '',
                description: c.descriptionContrat || c.description || initialData.descriptionContrat || initialData.description || '',
                commentaire: c.commentaireContrat || c.commentaire || initialData.commentaireContrat || initialData.commentaire || '',
                aPeriodeEssai: dureeEssai != null && Number(dureeEssai) > 0,
                dureeEssaiMois: dureeEssai ? String(dureeEssai) : '3',
                dateFinEssai: fmtDateInput(c.dateFinEssai || initialData.dateFinEssai),
                idSite: a.idSite || initialData.idSite || '',
                idDepartement: a.idDepartement || initialData.idDepartement || '',
                idService: a.idService || initialData.idService || '',
                idFonction: a.idFonction || initialData.idFonction || '',
                idResponsableHierarchique: a.idResponsableHierarchique || initialData.idResponsableHierarchique || '',
                idCategorieProfessionnelle: a.idCategorieProfessionnelle || initialData.idCategorieProfessionnelle || '',
                idModeCalculPaie: a.idModeCalculPaie || initialData.idModeCalculPaie || '',
                salaireBase: a.salaireBase ? String(a.salaireBase) : (initialData.salaireBase ? String(initialData.salaireBase) : ''),
                descriptionAffectation: a.descriptionAffectation || a.description || initialData.descriptionAffectation || '',
                commentaireAffectation: a.commentaireAffectation || a.commentaire || initialData.commentaireAffectation || '',
                dateEffet: fmtDateInput(w.dateEffet || initialData.dateEffet || c.dateDebutContrat || c.dateDebut || initialData.dateDebutContrat),
                motif: w.motif || initialData.motif || '',
            });
        } else {
            const today = fmtDateInput(new Date());
            setFormData({
                codeTypeEvenement: hasHistory ? 'REPRISE_ANCIENNETE' : 'EMBAUCHE_INITIALE',
                idFiliale: '', idTypeContrat: '', idTypeIntervenant: '',
                dateDebut: today, dateFin: '', dateSignature: '',
                comptabiliseEffectif: true, numeroContrat: '', description: '',
                commentaire: '', aPeriodeEssai: false, dureeEssaiMois: '3',
                dateFinEssai: '', idSite: '', idDepartement: '', idService: '',
                idFonction: '', idResponsableHierarchique: '',
                idCategorieProfessionnelle: '', idModeCalculPaie: '',
                salaireBase: '', descriptionAffectation: '', commentaireAffectation: '',
                dateEffet: '', motif: '',
            });
        }
        setIsDirty(false);
        setErrorMsg('');
    }, [isOpen, initialData, hasHistory]);

    const setField = (field, value) => {
        setIsDirty(true);
        setFormData(prev => {

            const updated = { ...prev, [field]: value };
            if (field === 'idDepartement' && String(value) !== String(prev.idDepartement)) {
                updated.idService = '';
            }
            if (field === 'idTypeContrat' && String(value) !== String(prev.idTypeContrat)) {
                updated.dateFin = '';
            }
            if (field === 'dateDebut' || field === 'dureeEssaiMois' || field === 'aPeriodeEssai') {
                const aEssai = field === 'aPeriodeEssai' ? value : prev.aPeriodeEssai;
                const dateDeb = field === 'dateDebut' ? value : prev.dateDebut;
                const dur = field === 'dureeEssaiMois' ? value : prev.dureeEssaiMois;

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
        if (submitting) return;
        setErrorMsg('');

        if (!formData.idFiliale || !formData.idTypeContrat || !formData.idTypeIntervenant ||
            !formData.dateDebut || !formData.idSite ||
            !formData.idDepartement || !formData.idService || !formData.idFonction ||
            !formData.idCategorieProfessionnelle || !formData.idModeCalculPaie || !formData.salaireBase) {
            setErrorMsg("Veuillez remplir tous les champs obligatoires (marqués d'une astérisque).");
            return;
        }

        const typeCtObj = typesContrat.find(tc => String(tc.idTypeContrat) === String(formData.idTypeContrat));
        if (typeCtObj?.exigeDateFin && !formData.dateFin) {
            setErrorMsg(`La date de fin est obligatoire pour le type de contrat "${typeCtObj.libelle || typeCtObj.code}".`);
            return;
        }

        const idContratPrec = (formData.codeTypeEvenement === 'REPRISE_ANCIENNETE' || formData.codeTypeEvenement === 'REPRISE_CONSULTANT')
            ? (dernierContrat?.idContrat || null)
            : null;

        const payload = {
            codeTypeEvenement: formData.codeTypeEvenement || 'EMBAUCHE_INITIALE',
            idContratPrecedent: idContratPrec,
            idFiliale: formData.idFiliale ? parseInt(formData.idFiliale) : null,
            idTypeContrat: formData.idTypeContrat ? parseInt(formData.idTypeContrat) : null,
            idTypeIntervenant: formData.idTypeIntervenant ? parseInt(formData.idTypeIntervenant) : null,
            dateDebut: formData.dateDebut || null,
            dateEffet: formData.dateDebut || null,
            dateFin: formData.dateFin ? formData.dateFin : null,
            dateSignature: formData.dateSignature || null,
            comptabiliseEffectif: formData.comptabiliseEffectif,
            numeroContrat: formData.numeroContrat ? formData.numeroContrat.trim() : null,
            descriptionContrat: formData.description ? formData.description.trim() : null,
            commentaireContrat: formData.commentaire ? formData.commentaire.trim() : null,
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : null,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
            idSite: formData.idSite ? parseInt(formData.idSite) : null,
            idDepartement: formData.idDepartement ? parseInt(formData.idDepartement) : null,
            idService: formData.idService ? parseInt(formData.idService) : null,
            idFonction: formData.idFonction ? parseInt(formData.idFonction) : null,
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null,
            idCategorieProfessionnelle: formData.idCategorieProfessionnelle ? parseInt(formData.idCategorieProfessionnelle) : null,
            idModeCalculPaie: formData.idModeCalculPaie ? parseInt(formData.idModeCalculPaie) : null,
            salaireBase: formData.salaireBase ? parseFloat(String(formData.salaireBase).replace(/\s/g, '').replace(/,/g, '.')) : 0,
            descriptionAffectation: formData.descriptionAffectation ? formData.descriptionAffectation.trim() : null,
            commentaireAffectation: formData.commentaireAffectation ? formData.commentaireAffectation.trim() : null,
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
                await contratService.creerBrouillonEmbauche(idEmploye, payload);
            }
            if (onSuccess) onSuccess();
            onClose();
        } catch (err) {
            console.error("Erreur enregistrement embauche:", err);
            setErrorMsg(getErrorMessage(err, "Erreur lors de l'enregistrement de l'embauche."));
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen) return null;

    // Objets référentiels pour la synthèse récapitulative
    const filialeObj = filiales.find(f => String(f.idFiliale) === String(formData.idFiliale));
    const siteObj = sites.find(s => String(s.idSite) === String(formData.idSite));
    const foncObj = fonctions.find(f => String(f.idFonction) === String(formData.idFonction));
    const typeCtObj = typesContrat.find(tc => String(tc.idTypeContrat) === String(formData.idTypeContrat));

    const optionsNature = [
        {
            code: 'EMBAUCHE_INITIALE',
            title: 'Nouvelle embauche',
            icon: UserPlus,
            desc: 'Nouveau cycle contractuel (compteurs et ancienneté réinitialisés).',
        },
        {
            code: 'REPRISE_ANCIENNETE',
            title: 'Réintégration / Reprise d\'ancienneté',
            icon: RotateCcw,
            desc: `Continuité avec le contrat précédent (N° ${dernierContrat?.numeroContrat || 'Historique'}) et maintien des droits acquis.`,
        },
        {
            code: 'REPRISE_CONSULTANT',
            title: 'Consultant / Prestation',
            icon: Handshake,
            desc: `Continuité d'ancienneté (N° ${dernierContrat?.numeroContrat || 'Historique'}) sous statut de prestation.`,
        },
    ];

    const slideOverFooter = (
        <div className="flex items-center justify-between w-full flex-wrap gap-3">
            <p className="text-xs text-[var(--color-fg-muted)]">
                Statut initial : <span className="font-semibold text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]">Brouillon</span> (validation requise).
            </p>
            <div className="flex items-center gap-2">
                <Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>
                    Annuler
                </Button>
                <Button
                    type="submit"
                    form="embauche-form"
                    variant="primary"
                    size="sm"
                    loading={submitting}
                    iconLeft={<Save size={15} />}
                >
                    {isEditMode ? 'Enregistrer les modifications' : 'Créer le brouillon d\'embauche'}
                </Button>
            </div>
        </div>
    );

    return (
        <SlideOver
            isOpen={isOpen}
            onClose={onClose}
            closeOnBackdrop={false}
            isDirty={isDirty}
            title={
                isEditMode
                    ? 'Modifier le brouillon d\'embauche'
                    : hasHistory
                        ? 'Contractualisation (réembauche)'
                        : 'Nouvelle embauche'
            }
            subtitle={`Contrat et affectation pour ${nomEmploye}`}
            icon={<Briefcase size={18} />}
            width="2xl"
            footer={slideOverFooter}
        >
            {loadingRef ? (
                <Loading display="page" label="Chargement des référentiels..." />
            ) : (
                <form id="embauche-form" onSubmit={handleSubmit} className="space-y-6 [&_label]:normal-case [&_label]:tracking-normal [&_label]:text-xs [&_label]:font-medium">
                    {errorMsg && <FormError message={errorMsg} />}

                    {/* Nature de la contractualisation si historique existant */}
                    {hasHistory && !isEditMode && (
                        <section className="space-y-3 pb-5 border-b border-[var(--color-border-subtle)]">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                    Nature de la contractualisation
                                </span>
                                <span className="text-[11px] text-[var(--color-fg-muted)]">
                                    Dernier contrat : {dernierContrat?.numeroContrat || 'Clôturé'}
                                </span>
                            </div>
                            <div className="grid grid-cols-1 gap-2.5">
                                {optionsNature.map((opt) => {
                                    const isSel = formData.codeTypeEvenement === opt.code;
                                    return (
                                        <div
                                            key={opt.code}
                                            role="button"
                                            tabIndex={0}
                                            onClick={() => setField('codeTypeEvenement', opt.code)}
                                            onKeyDown={(e) => {
                                                if (e.key === 'Enter' || e.key === ' ') {
                                                    e.preventDefault();
                                                    setField('codeTypeEvenement', opt.code);
                                                }
                                            }}
                                            className={`p-3 border rounded-none cursor-pointer transition-all ${
                                                isSel
                                                    ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 ring-1 ring-[var(--color-brand-500)]/30'
                                                    : 'border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)]/50 bg-[var(--color-surface)]'
                                            }`}
                                        >
                                            <div className="flex items-center justify-between">
                                                <span className="text-xs font-bold text-[var(--color-fg)] flex items-center gap-2">
                                                    {opt.icon && <opt.icon size={15} className={isSel ? 'text-[var(--color-brand-500)]' : 'text-[var(--color-fg-muted)]'} />}
                                                    {opt.title}
                                                </span>
                                                <input
                                                    type="radio"
                                                    name="codeTypeEvenement"
                                                    checked={isSel}
                                                    onChange={() => setField('codeTypeEvenement', opt.code)}
                                                    className="text-[var(--color-brand-500)] focus:ring-[var(--color-brand-500)]"
                                                />
                                            </div>
                                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-1 leading-normal pl-6">
                                                {opt.desc}
                                            </p>
                                        </div>
                                    );
                                })}
                            </div>
                        </section>
                    )}

                    {/* Section 1 : Contrat de travail */}
                    <ContratFieldsSection
                        formData={formData}
                        setField={setField}
                        signatureOptional
                        filiales={filiales}
                        typesContrat={typesContrat}
                        typesIntervenant={typesIntervenant}
                    />

                    {/* Section Période d'essai */}
                    <PeriodeEssaiFieldsSection
                        formData={formData}
                        setField={setField}
                    />

                    {/* Section 2 : Affectation & Poste */}
                    <AffectationFieldsSection
                        formData={formData}
                        setField={setField}
                        sites={sites}
                        departements={departements}
                        services={services}
                        fonctions={fonctions}
                        categoriesPro={categoriesPro}
                        modesPaie={modesPaie}
                        responsables={responsables}
                    />

                    {/* Section 3 : Remarques */}
                    <div className="space-y-3 pt-2">
                        <Textarea
                            label="Commentaires administratifs"
                            rows={2}
                            value={formData.commentaire}
                            onChange={(e) => setField('commentaire', e.target.value)}
                            placeholder="Remarque interne sur la négociation du contrat..."
                        />
                    </div>

                    {/* Section 4 : Synthèse contractuelle récapitulative */}
                    <div className="p-4 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs space-y-2.5">
                        <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                            <span className="font-semibold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Synthèse de la contractualisation
                            </span>
                            <span className="px-2 py-0.5 border border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-semibold text-[11px]">
                                Brouillon
                            </span>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-2 pt-1">
                            <div>
                                <span className="text-[var(--color-fg-muted)] block">Contrat & Filiale :</span>
                                <span className="font-medium text-[var(--color-fg)]">
                                    {typeCtObj?.libelle || typeCtObj?.code || 'Type à définir'} · {filialeObj?.codeFiliale || filialeObj?.raisonSociale || 'Filiale à définir'}
                                </span>
                            </div>
                            <div>
                                <span className="text-[var(--color-fg-muted)] block">Poste & Site :</span>
                                <span className="font-medium text-[var(--color-fg)]">
                                    {foncObj?.libelle || 'Fonction à définir'} {siteObj?.libelle ? `· ${siteObj.libelle}` : ''}
                                </span>
                            </div>
                            <div>
                                <span className="text-[var(--color-fg-muted)] block">Période d'effet :</span>
                                <span className="font-medium text-[var(--color-fg)]">
                                    {formData.dateDebut ? fmtDateFr(formData.dateDebut) : 'Date début non définie'}
                                    {formData.dateFin ? ` au ${fmtDateFr(formData.dateFin)}` : typeCtObj?.exigeDateFin ? ' · Date de fin à renseigner' : ' (Durée indéterminée)'}
                                </span>
                            </div>
                            <div>
                                <span className="text-[var(--color-fg-muted)] block">Rémunération de base :</span>
                                <span className="font-medium text-[var(--color-fg)] font-mono">
                                    {formData.salaireBase ? formatMoneyAr(formData.salaireBase) : 'Non renseigné'}
                                </span>
                            </div>
                        </div>
                    </div>
                </form>
            )}
        </SlideOver>
    );
}
