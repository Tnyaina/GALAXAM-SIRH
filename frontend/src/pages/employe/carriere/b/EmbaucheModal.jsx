import { useState, useEffect } from 'react';
import { Briefcase, ShieldAlert, Save, X, FileSignature, Eye, EyeOff } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Textarea } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import contratService from '@/api/employe/contratService';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import { ContractPreview } from '@/components/carriere/ContractDocument';
import { fmtDateInput } from '@/utils/carriereUtils';

import ContratFieldsSection from '@/components/carriere/form/ContratFieldsSection';
import AffectationFieldsSection from '@/components/carriere/form/AffectationFieldsSection';
import PeriodeEssaiFieldsSection from '@/components/carriere/form/PeriodeEssaiFieldsSection';

export default function EmbaucheModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    initialData = null,
    onSuccess,
}) {
    const isEditMode = !!initialData;

    const {
        filiales, sites, departements, services, fonctions,
        typesContrat, typesIntervenant, categoriesPro, modesPaie, responsables,
        loading: loadingRef,
    } = useContractReferentiels(isOpen, idEmploye);

    const [formData, setFormData] = useState({
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

        if (initialData) {
            const c = initialData.contratCible || initialData.contratSource || {};
            const a = initialData.affectationCible || initialData.affectationSource || {};
            const w = initialData.workflow || {};

            setFormData({
                idFiliale: c.idFiliale || '', idTypeContrat: c.idTypeContrat || '', idTypeIntervenant: c.idTypeIntervenant || '',
                dateDebut: fmtDateInput(c.dateDebut), dateFin: fmtDateInput(c.dateFin), dateSignature: fmtDateInput(c.dateSignature),
                comptabiliseEffectif: c.comptabiliseEffectif !== false, numeroContrat: c.numeroContrat || '',
                description: c.description || '', commentaire: c.commentaire || '',
                aPeriodeEssai: c.dureeEssaiMois != null && c.dureeEssaiMois > 0,
                dureeEssaiMois: c.dureeEssaiMois ? String(c.dureeEssaiMois) : '3',
                dateFinEssai: fmtDateInput(c.dateFinEssai), idSite: a.idSite || '', idDepartement: a.idDepartement || '',
                idService: a.idService || '', idFonction: a.idFonction || '', idResponsableHierarchique: a.idResponsableHierarchique || '',
                idCategorieProfessionnelle: a.idCategorieProfessionnelle || '', idModeCalculPaie: a.idModeCalculPaie || '',
                salaireBase: a.salaireBase ? String(a.salaireBase) : '', descriptionAffectation: a.description || '',
                commentaireAffectation: a.commentaire || '', dateEffet: fmtDateInput(w.dateEffet), motif: w.motif || '',
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
        setErrorMsg('');

        if (!formData.idFiliale || !formData.idTypeContrat || !formData.idTypeIntervenant ||
            !formData.dateDebut || !formData.dateSignature || !formData.idSite ||
            !formData.idDepartement || !formData.idService || !formData.idFonction ||
            !formData.idCategorieProfessionnelle || !formData.idModeCalculPaie || !formData.salaireBase) {
            setErrorMsg("Veuillez remplir tous les champs obligatoires (marqués d'une astérisque).");
            return;
        }

        const payload = {
            idFiliale: parseInt(formData.idFiliale),
            idTypeContrat: parseInt(formData.idTypeContrat),
            idTypeIntervenant: parseInt(formData.idTypeIntervenant),
            dateDebut: formData.dateDebut,
            dateFin: formData.dateFin || null,
            dateSignature: formData.dateSignature,
            comptabiliseEffectif: formData.comptabiliseEffectif,
            numeroContrat: formData.numeroContrat || null,
            descriptionContrat: formData.description || null,
            commentaireContrat: formData.commentaire || null,
            dureeEssaiMois: formData.aPeriodeEssai && formData.dureeEssaiMois ? parseInt(formData.dureeEssaiMois) : null,
            dateFinEssai: formData.aPeriodeEssai && formData.dateFinEssai ? formData.dateFinEssai : null,
            idSite: parseInt(formData.idSite),
            idDepartement: parseInt(formData.idDepartement),
            idService: parseInt(formData.idService),
            idFonction: parseInt(formData.idFonction),
            idResponsableHierarchique: formData.idResponsableHierarchique ? parseInt(formData.idResponsableHierarchique) : null,
            idCategorieProfessionnelle: parseInt(formData.idCategorieProfessionnelle),
            idModeCalculPaie: parseInt(formData.idModeCalculPaie),
            salaireBase: parseFloat(formData.salaireBase),
            descriptionAffectation: formData.descriptionAffectation || null,
            commentaireAffectation: formData.commentaireAffectation || null,
        };

        setSubmitting(true);
        try {
            if (isEditMode) {
                const wfId = initialData.workflow?.idWorkflow;
                await contratService.modifierBrouillonWorkflow(wfId, payload);
            } else {
                await contratService.creerBrouillonEmbauche(idEmploye, payload);
            }
            if (onSuccess) onSuccess();
            onClose();
        } catch (err) {
            console.error("Erreur enregistrement embauche:", err);
            setErrorMsg(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || "Erreur lors de l'enregistrement de l'embauche.");
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

    return (
        <div className={`fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-2 sm:p-4 overflow-y-auto transition-opacity duration-200 ${mounted ? 'opacity-100' : 'opacity-0'}`}>
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-2xl w-full max-w-[1440px] max-h-[95vh] flex flex-col overflow-hidden">
                {/* Header */}
                <div className="px-7 py-4 border-b border-ink-150 dark:border-ink-700 flex items-center justify-between bg-ink-50/50 dark:bg-ink-950/40">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-brand-500 text-white rounded-sm flex items-center justify-center shadow-md">
                            <Briefcase size={20} />
                        </div>
                        <div>
                            <h2 className="font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                                {isEditMode ? 'Corriger le Brouillon d\'Embauche' : 'Nouvelle Embauche Initiale'}
                                <span className="text-xs px-2 py-0.5 bg-amber-500/10 text-amber-500 border border-amber-500/20 font-mono rounded-sm">
                                    BROUILLON
                                </span>
                            </h2>
                            <p className="text-xs text-ink-400">
                                Saisie du contrat et affectation pour <strong className="text-ink-800 dark:text-white">{nomEmploye}</strong>
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <button
                            type="button"
                            onClick={() => setShowPreviewMobile(!showPreviewMobile)}
                            className="lg:hidden p-2 text-xs font-semibold bg-ink-100 dark:bg-ink-800 text-ink-700 dark:text-ink-200 rounded-sm flex items-center gap-1"
                        >
                            {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                            {showPreviewMobile ? 'Masquer Aperçu' : 'Aperçu'}
                        </button>
                        <button onClick={onClose} className="p-1.5 text-ink-400 hover:text-ink-800 dark:hover:text-white hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors cursor-pointer">
                            <X size={20} />
                        </button>
                    </div>
                </div>

                {/* Body split 2 colonnes 50/50 : Formulaire (6/12) - Aperçu Contrat (6/12) */}
                <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-ink-150 dark:divide-ink-700">
                    {/* Colonne 1 : Formulaire de Saisie (6/12) */}
                    <div className={`p-7 space-y-6 lg:col-span-6 ${showPreviewMobile ? 'hidden lg:block' : 'block'}`}>
                        {loadingRef ? (
                            <div className="py-12 flex justify-center"><Loading size="md" label="Chargement des référentiels..." /></div>
                        ) : (
                            <form id="embauche-form" onSubmit={handleSubmit} className="space-y-6">
                                {errorMsg && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-2">
                                        <ShieldAlert size={16} />
                                        {errorMsg}
                                    </div>
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
                                        label="Commentaires DAF / RH"
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
                    <div className={`p-7 bg-ink-50/50 dark:bg-ink-950/30 lg:col-span-6 overflow-y-auto space-y-4 ${showPreviewMobile ? 'block' : 'hidden lg:block'}`}>
                        <div className="flex items-center justify-between pb-2 border-b border-ink-200 dark:border-ink-700">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-ink-500 dark:text-ink-400 flex items-center gap-2">
                                <FileSignature size={14} className="text-brand-500" />
                                Aperçu de la Convention / Contrat
                            </h3>
                            <span className="text-[10px] font-mono font-bold text-brand-500 uppercase tracking-widest bg-brand-500/10 px-2 py-0.5 rounded-sm">
                                Temps Réel
                            </span>
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
                <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-between items-center">
                    <p className="text-xs text-ink-400 hidden sm:block">
                        Le contrat et l'affectation restent au statut <strong className="text-amber-500">BROUILLON</strong> tant qu'ils ne sont pas validés.
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
                            {isEditMode ? 'Enregistrer les modifications' : 'Créer le Brouillon d\'Embauche'}
                        </Button>
                    </div>
                </div>
            </div>
        </div>
    );
}