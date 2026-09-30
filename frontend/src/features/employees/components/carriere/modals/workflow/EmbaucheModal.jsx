import { useState, useEffect } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { Briefcase, Save, X, FileSignature, Eye, EyeOff, UserPlus, RotateCcw, Handshake } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import FormError from '@/components/ui/feedback/FormError';
import Loading from '@/components/ui/feedback/Loading';
import contratService from '@/features/employees/api/contratService';
import { useContractReferentiels } from '@/features/employees/hooks/useContractReferentiels';
import { ContractPreview } from '@/features/employees/components/carriere/ContractDocument';
import { fmtDateInput } from '@/utils/dateUtils';

import ContratFieldsSection from '@/features/employees/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/features/employees/components/carriere/form/AffectationFieldsSection';
import PeriodeEssaiFieldsSection from '@/features/employees/components/carriere/form/PeriodeEssaiFieldsSection';

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

    // État local : formulaire, aperçu et soumission.
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
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [mounted, setMounted] = useState(false);

    // Effets : initialise le formulaire, les référentiels et l'animation d'ouverture.
    useEffect(() => {
        if (isOpen) {
            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        } else {
            setMounted(false);
        }
    }, [isOpen]);

    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    useEffect(() => {
        if (!isOpen) return;

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || initialData;
            const a = initialData.affectationCible || initialData.affectationSource || initialData;
            const w = initialData.workflow || initialData;

            const dureeEssai = c.dureeEssaiMois ?? initialData.dureeEssaiMois;

            setFormData({
                idFiliale: c.idFiliale || initialData.idFiliale || '',
                idTypeContrat: c.idTypeContrat || initialData.idTypeContrat || '',
                idTypeIntervenant: c.idTypeIntervenant || initialData.idTypeIntervenant || '',
                dateDebut: fmtDateInput(c.dateDebutContrat || c.dateDebut || initialData.dateDebutContrat || initialData.dateDebut),
                dateFin: fmtDateInput(c.dateFinContrat || c.dateFin || initialData.dateFinContrat || initialData.dateFin),
                dateSignature: fmtDateInput(c.dateSignature || c.dateDebutContrat || c.dateDebut || w.dateEffet || initialData.dateEffet),
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
                idFiliale: '', idTypeContrat: '', idTypeIntervenant: '',
                dateDebut: today, dateFin: '', dateSignature: today,
                comptabiliseEffectif: true, numeroContrat: '', description: '',
                commentaire: '', aPeriodeEssai: false, dureeEssaiMois: '3',
                dateFinEssai: '', idSite: '', idDepartement: '', idService: '',
                idFonction: '', idResponsableHierarchique: '',
                idCategorieProfessionnelle: '', idModeCalculPaie: '',
                salaireBase: '', descriptionAffectation: '', commentaireAffectation: '',
                dateEffet: '', motif: '',
            });
        }
    }, [isOpen, initialData]);

    const setField = (field, value) => {
        setFormData(prev => {
            const updated = { ...prev, [field]: value };
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
            !formData.dateDebut || !formData.dateSignature || !formData.idSite ||
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
            dateFin: formData.dateFin ? formData.dateFin : null, // Sécurité : chaîne vide -> null
            dateSignature: formData.dateSignature || null,
            comptabiliseEffectif: formData.comptabiliseEffectif,
            numeroContrat: formData.numeroContrat ? formData.numeroContrat.trim() : null,
            descriptionContrat: formData.description ? formData.description.trim() : null,
            commentaireContrat: formData.commentaire ? formData.commentaire.trim() : null,
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : null,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null, // Sécurité : chaîne vide -> null
            idSite: formData.idSite ? parseInt(formData.idSite) : null,
            idDepartement: formData.idDepartement ? parseInt(formData.idDepartement) : null,
            idService: formData.idService ? parseInt(formData.idService) : null,
            idFonction: formData.idFonction ? parseInt(formData.idFonction) : null,
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null, // Sécurité
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

    // Objets référentiels pour l'aperçu en temps réel
    const filialeObj = filiales.find(f => String(f.idFiliale) === String(formData.idFiliale));
    const siteObj = sites.find(s => String(s.idSite) === String(formData.idSite));
    const deptObj = departements.find(d => String(d.idDepartement) === String(formData.idDepartement));
    const servObj = services.find(srv => String(srv.idService) === String(formData.idService));
    const foncObj = fonctions.find(f => String(f.idFonction) === String(formData.idFonction));
    const typeCtObj = typesContrat.find(tc => String(tc.idTypeContrat) === String(formData.idTypeContrat));
    const typeTiObj = typesIntervenant.find(ti => String(ti.idTypeIntervenant) === String(formData.idTypeIntervenant));
    const catObj = categoriesPro.find(c => String(c.idCategorieProfessionnelle || c.idCategorie) === String(formData.idCategorieProfessionnelle));
    const paieObj = modesPaie.find(m => String(m.idModeCalculPaie || m.idModeCalcul) === String(formData.idModeCalculPaie));
    const respObj = responsables.find(r => String(r.idEmploye) === String(formData.idResponsableHierarchique));

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

    return (
        <div 
            onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
            className={`fixed inset-0 z-50 flex items-center justify-center bg-[var(--color-overlay)]/60 backdrop-blur-[2px] p-2 sm:p-4 overflow-y-auto ${mounted ? 'opacity-100' : 'opacity-0'}`}
        >
            <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl w-full max-w-[1440px] max-h-[95vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-7 py-4 border-b border-[var(--color-border-subtle)] flex items-center justify-between bg-[var(--color-bg-subtle)]">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-[var(--color-brand-500)] text-[var(--color-on-color)] rounded-none flex items-center justify-center shadow-md">
                            <Briefcase size={20} />
                        </div>
                        <div>
                            <h2 className="font-bold text-lg text-[var(--color-fg)] flex items-center gap-2">
                                {isEditMode
                                    ? 'Modifier le brouillon de contrat'
                                    : hasHistory
                                        ? 'Contractualisation (réembauche / réintégration)'
                                        : 'Nouvelle embauche'}
                                <span className="text-xs px-2 py-0.5 bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] border border-[var(--color-warning-500)]/20 font-mono rounded-none">
                                    Brouillon
                                </span>
                            </h2>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Contrat et affectation pour <strong className="text-[var(--color-fg)]">{nomEmploye}</strong>
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setShowPreviewMobile(!showPreviewMobile)}
                            className="lg:hidden p-2 text-xs font-semibold bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-[var(--color-border-subtle)] rounded-none flex items-center gap-1"
                        >
                            {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                            {showPreviewMobile ? 'Masquer l\'aperçu' : 'Aperçu'}
                        </button>
                        <button onClick={onClose} className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Body split 2 colonnes 50/50 : Formulaire (6/12) - Aperçu Contrat (6/12) */}
                <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-[var(--color-border-subtle)]">
                    {/* Colonne 1 : Formulaire de Saisie (6/12) */}
                    <div className={`p-4 sm:p-5 space-y-4 lg:col-span-6 ${showPreviewMobile ? 'hidden lg:block' : 'block'}`}>
                        {loadingRef ? (
                            <Loading display="page" label="Chargement des référentiels..." />
                        ) : (
                            <form id="embauche-form" onSubmit={handleSubmit} className="space-y-4">
                                {errorMsg && <FormError message={errorMsg} />}

                                {/* Nature de la contractualisation si collaborateur inactif avec historique */}
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

                                {/* Section 1 : Contrat */}
                                <ContratFieldsSection
                                    formData={formData}
                                    setField={setField}
                                    filiales={filiales}
                                    typesContrat={typesContrat}
                                    typesIntervenant={typesIntervenant}
                                />

                                {/* Section Période d'essai */}
                                <PeriodeEssaiFieldsSection
                                    formData={formData}
                                    setField={setField}
                                />

                                {/* Section 2 : Affectation Carrière */}
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
                            </form>
                        )}
                    </div>

                    {/* Colonne 2 : Aperçu en Temps Réel (6/12) */}
                    <div className={`p-7 bg-[var(--color-bg-subtle)] lg:col-span-6 overflow-y-auto space-y-4 ${showPreviewMobile ? 'block' : 'hidden lg:block'}`}>
                        <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)]">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-2">
                                <FileSignature size={14} className="text-[var(--color-brand-500)]" />
                                Aperçu du contrat
                            </h3>
                        </div>

                        <ContractPreview
                            libFiliale={filialeObj}
                            nomEmploye={nomEmploye || 'L\'EMPLOYÉ(E)'}
                            formData={formData}
                            libTypeContrat={typeCtObj}
                            libTypeIntervenant={typeTiObj}
                            libSite={siteObj}
                            libDepartement={deptObj}
                            libService={servObj}
                            libFonction={foncObj}
                            libCategorie={catObj}
                            libModePaie={paieObj}
                            libResponsable={respObj}
                        />
                    </div>
                </div>

                {/* Footer Boutons */}
                <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex justify-between items-center">
                    <p className="text-xs text-[var(--color-fg-muted)] hidden sm:block">
                        Statut initial : Brouillon (validation requise).
                    </p>
                    <div className="flex gap-3 ml-auto">
                        <Button type="button" onClick={onClose} variant="ghost" size="sm">
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            form="embauche-form"
                            variant="primary"
                            size="sm"
                            loading={submitting}
                            iconLeft={<Save size={16} />}
                        >
                            {isEditMode ? 'Enregistrer les modifications' : 'Créer le brouillon d\'embauche'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}
