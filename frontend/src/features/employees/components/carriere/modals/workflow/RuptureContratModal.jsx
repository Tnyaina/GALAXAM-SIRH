import { useState, useEffect, useCallback, useMemo } from 'react';
import {
    UserMinus, ShieldAlert, X, FileSignature, Eye, EyeOff, Calendar, AlertOctagon,
    Clock, Calculator, ArrowRight, CheckCircle2, HelpCircle, CalendarDays
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import contratService from '@/features/employees/api/contratService';
import {
    DocumentFrame, DocumentHeader, Clause, ArticleBlock, DocumentFooter
} from '@/features/employees/components/carriere/ContractDocument';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';
import { fmtMoneyMGA } from '@/utils/moneyUtils';

// ── OPTIONS DE DURÉE DE PRÉAVIS ─────────────────────────────────────────────
const DUREES_PREAVIS = [
    { key: 'AUCUN', label: 'Aucun préavis (0 j)', jours: 0, moisEquiv: 0, desc: 'Sortie immédiate sans préavis (Faute, fin de CDD ou accord immédiat)' },
    { key: '15_JOURS', label: '15 jours', jours: 15, moisEquiv: 0.5, desc: 'Période d\'essai ou contrat de courte durée' },
    { key: '1_MOIS', label: '1 mois (30 j)', jours: 30, moisEquiv: 1.0, desc: 'Ouvriers & Employés standard (Code du travail Malagasy)' },
    { key: '2_MOIS', label: '2 mois (60 j)', jours: 60, moisEquiv: 2.0, desc: 'Agents de maîtrise & Techniciens (> 3 ans ancienneté)' },
    { key: '3_MOIS', label: '3 mois (90 j)', jours: 90, moisEquiv: 3.0, desc: 'Cadres & Cadres dirigeants' },
    { key: 'CUSTOM', label: 'Personnalisé (en jours)...', jours: null, moisEquiv: null, desc: 'Nombre spécifique de jours calendaires' },
];

// ── MODALITÉS D'EXÉCUTION DU PRÉAVIS ────────────────────────────────────────
const MODES_PREAVIS = [
    {
        key: 'EFFECTUE',
        titre: 'Préavis effectué / travaillé',
        badge: 'En poste jusqu\'au départ',
        badgeClass: 'bg-brand-500/10 text-brand-700 dark:text-brand-300 border-brand-500/30',
        impactTitre: 'Sortie différée au terme du préavis',
        impactStc: '0 Ar (Salaire normal versé chaque mois jusqu\'à la fin du préavis)',
        desc: 'Le salarié continue son activité normale. La date de sortie effective est projetée à la fin du préavis. Aucun montant compensateur n\'est dû au STC car les mois sont rémunérés normalement.'
    },
    {
        key: 'DISPENSE_PAYEE',
        titre: 'Dispensé par l\'employeur (Payé)',
        badge: 'Sortie immédiate indemnisée',
        badgeClass: 'bg-amber-500/10 text-amber-700 dark:text-amber-400 border-amber-500/30',
        impactTitre: 'Sortie effective immédiate',
        impactStc: 'Indemnité compensatrice versée au STC (salaire de base × mois de préavis)',
        desc: 'L\'employeur libère le salarié immédiatement de son obligation de travail, mais prend à sa charge l\'intégralité du préavis sous forme d\'Indemnité Compensatrice dans le STC.'
    },
    {
        key: 'DISPENSE_NON_PAYEE',
        titre: 'Dispensé à la demande du salarié (Non payé)',
        badge: 'Sortie immédiate sans indemnité',
        badgeClass: 'bg-ink-500/10 text-ink-600 dark:text-ink-400 border-ink-500/30',
        impactTitre: 'Sortie effective immédiate',
        impactStc: '0 Ar (Renonciation amiable, pas d\'indemnité)',
        desc: 'Le salarié demande à quitter l\'entreprise dès la notification. L\'employeur accepte sans verser d\'indemnité compensatrice de préavis.'
    },
];

// Helper de calcul de date d'effet projetée
function calculerDateEffet(dateNotifStr, dureeKey, modePreavis, customJours = 0) {
    if (!dateNotifStr) return '';
    if (modePreavis !== 'EFFECTUE' || dureeKey === 'AUCUN') {
        return dateNotifStr;
    }
    const [y, m, d] = dateNotifStr.split('-').map(Number);
    if (!y || !m || !d) return dateNotifStr;
    const date = new Date(y, m - 1, d);

    if (dureeKey === '15_JOURS') {
        date.setDate(date.getDate() + 15);
    } else if (dureeKey === '1_MOIS') {
        date.setMonth(date.getMonth() + 1);
    } else if (dureeKey === '2_MOIS') {
        date.setMonth(date.getMonth() + 2);
    } else if (dureeKey === '3_MOIS') {
        date.setMonth(date.getMonth() + 3);
    } else if (dureeKey === 'CUSTOM') {
        date.setDate(date.getDate() + (Number(customJours) || 0));
    }
    return fmtDateInput(date);
}

// ── COMPOSANT APERÇU LETTRE DE RUPTURE / NOTIFICATION OFFBOARDING ────────────
function RupturePreview({
    className = '',
    nomEmploye,
    contratActif,
    affectationActive,
    workflowActif,
    formData,
    libTypeRupture,
    libelleDuree,
    modePreavisInfo,
    indemniteEstimee,
}) {
    const libFonction = affectationActive?.libelleFonction || workflowActif?.libelleFonction || '-';
    const libSite = affectationActive?.libelleSite || workflowActif?.libelleSite || '-';
    const libDept = affectationActive?.libelleDepartement || workflowActif?.libelleDepartement || '-';
    const libTypeContrat = contratActif?.libelleTypeContrat || workflowActif?.libelleTypeContrat || 'Contrat en cours';
    const numContrat = contratActif?.numeroContrat || workflowActif?.numeroContratCible || workflowActif?.numeroContratSource || 'CTR-ACTIF';

    return (
        <DocumentFrame className={className} watermark="Rupture">
            <DocumentHeader
                eyebrow={libTypeRupture || 'Fin de Contrat'}
                title="Notification de Fin de Contrat & Rupture"
                numero={numContrat}
            />

            <div className="pt-4 text-[13px] leading-relaxed space-y-3 font-normal">
                <p>
                    Le contrat de travail N° <strong>{numContrat}</strong> de <strong>{nomEmploye || '……………'}</strong> prend fin selon les termes ci-dessous.
                </p>

                <ArticleBlock n="1" title="Motif & Calendrier de Rupture">
                    <Clause label="Nature de la rupture" value={libTypeRupture || 'Rupture du contrat'} className="text-danger-600 font-bold" />
                    <Clause label="Type de contrat" value={libTypeContrat} />
                    <Clause label="Date d'entrée initiale" value={fmtDateFr(contratActif?.dateDebut || workflowActif?.dateDebutContrat)} />
                    <Clause label="Date de notification" value={fmtDateFr(formData.dateNotification)} />
                    <Clause label="Sortie effective (Prise d'effet)" value={fmtDateFr(formData.dateEffet)} className="font-bold text-ink-900 dark:text-white" />
                </ArticleBlock>

                <ArticleBlock n="2" title="Modalités du Préavis & Sortie">
                    <Clause label="Dernière fonction" value={libFonction} />
                    <Clause label="Site / Département" value={`${libSite} / ${libDept}`} />
                    <Clause label="Délai de préavis" value={libelleDuree} />
                    <Clause label="Régime d'exécution" value={modePreavisInfo?.titre || 'Préavis'} className="font-medium text-ink-800 dark:text-ink-200" />
                    {formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0 && (
                        <Clause
                            label="Indemnité préavis (STC)"
                            value={`${fmtMoneyMGA(indemniteEstimee)} (à régler au STC)`}
                            className="text-amber-600 dark:text-amber-400 font-bold"
                        />
                    )}
                </ArticleBlock>

                {formData.motif && (
                    <div className="pt-2 border-t border-ink-150 dark:border-ink-800">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-danger-500">Motif de la rupture / Circonstances</p>
                        <p className="text-ink-600 dark:text-ink-300 text-[12.5px] mt-0.5">{formData.motif}</p>
                    </div>
                )}

                {formData.commentaire && (
                    <div className="pt-2 border-t border-ink-150 dark:border-ink-800">
                        <p className="font-sans text-[10px] font-bold uppercase tracking-wider text-ink-400">Consignes d'offboarding & Clôture</p>
                        <p className="text-ink-500 text-[12px] mt-0.5">{formData.commentaire}</p>
                    </div>
                )}
            </div>

            <DocumentFooter status="Brouillon (en attente de validation RH)" />
        </DocumentFrame>
    );
}

// ── COMPOSANT PRINCIPAL MODAL ────────────────────────────────────────────────
export default function RuptureContratModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    contratActif,
    affectationActive,
    workflowActif,
    initialData,
    onSuccess,
    onOpenStc,
}) {
    // État local : références, formulaire de rupture, aperçu et soumission.
    const [mounted, setMounted] = useState(false);
    const [loadingRef, setLoadingRef] = useState(true);
    const [showPreviewMobile, setShowPreviewMobile] = useState(false);
    const [errorMsg, setErrorMsg] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // État après enregistrement réussi (permet d'enchaîner directement avec le STC)
    const [savedDraftResult, setSavedDraftResult] = useState(null);

    const [typesEvenement, setTypesEvenement] = useState([]);

    const [formData, setFormData] = useState({
        codeTypeEvenement: 'DEMISSION',
        dateNotification: fmtDateInput(new Date()),
        dateEffet: fmtDateInput(new Date()),
        dureePreavisKey: '1_MOIS',
        customJours: '30',
        modePreavis: 'EFFECTUE',
        motif: 'Démission du salarié pour convenances personnelles',
        commentaire: '',
    });

    // Chargement dynamique des types d'événements de rupture depuis les référentiels
    const loadReferentiels = useCallback(async () => {
        setLoadingRef(true);
        try {
            const tevRes = await contratService.getTypesEvenementRh();
            const list = Array.isArray(tevRes) ? tevRes : (tevRes?.items || tevRes?.content || []);
            const ruptures = list.filter(t =>
                t && ['DEMISSION', 'LICENCIEMENT', 'RUPTURE_CONVENTIONNELLE', 'FIN_CONTRAT', 'RUPTURE_ESSAI', 'DEPART_RETRAITE'].includes(t.code)
            );
            setTypesEvenement(ruptures.length > 0 ? ruptures : [
                { code: 'DEMISSION', libelle: 'Démission du salarié' },
                { code: 'LICENCIEMENT', libelle: 'Licenciement' },
                { code: 'RUPTURE_CONVENTIONNELLE', libelle: 'Rupture conventionnelle' },
                { code: 'FIN_CONTRAT', libelle: 'Fin de CDD / Terme du contrat' },
                { code: 'RUPTURE_ESSAI', libelle: 'Rupture de la période d\'essai' },
                { code: 'DEPART_RETRAITE', libelle: 'Départ à la retraite' },
            ]);
        } catch {
            setTypesEvenement([
                { code: 'DEMISSION', libelle: 'Démission du salarié' },
                { code: 'LICENCIEMENT', libelle: 'Licenciement' },
                { code: 'RUPTURE_CONVENTIONNELLE', libelle: 'Rupture conventionnelle' },
                { code: 'FIN_CONTRAT', libelle: 'Fin de CDD / Terme du contrat' },
                { code: 'RUPTURE_ESSAI', libelle: 'Rupture de la période d\'essai' },
                { code: 'DEPART_RETRAITE', libelle: 'Départ à la retraite' },
            ]);
        } finally {
            setLoadingRef(false);
        }
    }, []);

    // Initialisation intelligente des valeurs par défaut selon le profil
    // Effets : chargent les références et initialisent le brouillon à l'ouverture.
    useEffect(() => {
        if (isOpen) {
            loadReferentiels();
            setSavedDraftResult(null);
            setErrorMsg('');

            const targetData = initialData || (workflowActif?.isBrouillon ? workflowActif : null);
            const dateNotif = fmtDateInput(targetData?.dateDemande || targetData?.dateNotification || new Date());

            // Détection du profil pour suggestion automatique du préavis
            const catPro = String(contratActif?.categorieProfessionnelle || affectationActive?.categorieProfessionnelle || '').toUpperCase();
            const isCadre = catPro.includes('CADRE') || catPro.includes('DIRIGEANT');
            const defaultDuree = isCadre ? '3_MOIS' : '1_MOIS';

            if (targetData) {
                // Si reprise d'un brouillon existant
                const parsedDateEffet = fmtDateInput(targetData.dateEffet || dateNotif);
                const descContrat = String(targetData.descriptionContrat || '');
                let resolvedDuree = defaultDuree;
                let resolvedMode = 'EFFECTUE';

                if (descContrat.includes('Dispense payée') || descContrat.includes('DISPENSE_PAYEE')) {
                    resolvedMode = 'DISPENSE_PAYEE';
                } else if (descContrat.includes('Dispense non payée') || descContrat.includes('DISPENSE_NON_PAYEE')) {
                    resolvedMode = 'DISPENSE_NON_PAYEE';
                }

                if (descContrat.includes('3 mois')) resolvedDuree = '3_MOIS';
                else if (descContrat.includes('2 mois')) resolvedDuree = '2_MOIS';
                else if (descContrat.includes('15 jours')) resolvedDuree = '15_JOURS';
                else if (descContrat.includes('Aucun')) resolvedDuree = 'AUCUN';

                setFormData({
                    codeTypeEvenement: targetData.codeTypeEvenement || 'DEMISSION',
                    dateNotification: dateNotif,
                    dateEffet: parsedDateEffet,
                    dureePreavisKey: resolvedDuree,
                    customJours: '30',
                    modePreavis: resolvedMode,
                    motif: targetData.motif ? targetData.motif.replace(/^\[.*?\]\s*/, '') : 'Démission du salarié pour convenances personnelles',
                    commentaire: targetData.commentaire || '',
                });
            } else {
                // Nouveau workflow : calcul automatique dateEffet
                const computedEffet = calculerDateEffet(dateNotif, defaultDuree, 'EFFECTUE');
                setFormData({
                    codeTypeEvenement: 'DEMISSION',
                    dateNotification: dateNotif,
                    dateEffet: computedEffet,
                    dureePreavisKey: defaultDuree,
                    customJours: '30',
                    modePreavis: 'EFFECTUE',
                    motif: 'Démission du salarié pour convenances personnelles',
                    commentaire: '',
                });
            }

            const t = setTimeout(() => setMounted(true), 10);
            return () => clearTimeout(t);
        }
        setMounted(false);
        setShowPreviewMobile(false);
    }, [isOpen, loadReferentiels, initialData, workflowActif, contratActif, affectationActive]);

    // Fermeture avec Échap
    useEffect(() => {
        if (!isOpen) return;
        const handleKeyDown = (e) => {
            if (e.key === 'Escape') onClose();
        };
        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, onClose]);

    // Détermination de la durée en mois pour le calcul STC
    // Données dérivées : ancienneté et indemnité estimée.
    const { dureeInfo, moisIndemnite, libelleDuree } = useMemo(() => {
        const d = DUREES_PREAVIS.find(item => item.key === formData.dureePreavisKey) || DUREES_PREAVIS[2];
        let mois;
        let libelle = d.label;

        if (formData.dureePreavisKey === 'CUSTOM') {
            const j = Number(formData.customJours) || 0;
            mois = Number((j / 30).toFixed(2));
            libelle = `${j} jour(s) calendaire(s)`;
        } else {
            mois = d.moisEquiv || 0;
        }

        return {
            dureeInfo: d,
            moisIndemnite: formData.modePreavis === 'DISPENSE_PAYEE' ? mois : 0,
            libelleDuree: libelle,
        };
    }, [formData.dureePreavisKey, formData.customJours, formData.modePreavis]);

    const modePreavisInfo = useMemo(() => {
        return MODES_PREAVIS.find(m => m.key === formData.modePreavis) || MODES_PREAVIS[0];
    }, [formData.modePreavis]);

    // Estimation financière de l'indemnité compensatrice de préavis (si applicable)
    const salaireBase = Number(contratActif?.salaireBase || 0);
    const indemniteEstimee = useMemo(() => {
        if (formData.modePreavis !== 'DISPENSE_PAYEE' || moisIndemnite <= 0) return 0;
        return Math.round(salaireBase * moisIndemnite);
    }, [formData.modePreavis, moisIndemnite, salaireBase]);

    // Gestion du changement des dates et modalités avec projection automatique de dateEffet
    const handleNotificationDateChange = (newDateNotif) => {
        const newEffet = calculerDateEffet(newDateNotif, formData.dureePreavisKey, formData.modePreavis, formData.customJours);
        setFormData(prev => ({
            ...prev,
            dateNotification: newDateNotif,
            dateEffet: newEffet,
        }));
    };

    const handleDureePreavisChange = (newDureeKey) => {
        const newEffet = calculerDateEffet(formData.dateNotification, newDureeKey, formData.modePreavis, formData.customJours);
        setFormData(prev => ({
            ...prev,
            dureePreavisKey: newDureeKey,
            dateEffet: newEffet,
        }));
    };

    const handleCustomJoursChange = (newJours) => {
        const newEffet = calculerDateEffet(formData.dateNotification, 'CUSTOM', formData.modePreavis, newJours);
        setFormData(prev => ({
            ...prev,
            customJours: newJours,
            dateEffet: newEffet,
        }));
    };

    const handleModePreavisChange = (newMode) => {
        const newEffet = calculerDateEffet(formData.dateNotification, formData.dureePreavisKey, newMode, formData.customJours);
        setFormData(prev => ({
            ...prev,
            modePreavis: newMode,
            dateEffet: newEffet,
        }));
    };

    const handleTypeEvenementChange = (newCode) => {
        // Adaptation automatique des préavis selon le type de rupture choisi
        let suggestedDuree = formData.dureePreavisKey;
        let suggestedMode = formData.modePreavis;

        if (newCode === 'FIN_CONTRAT') {
            suggestedDuree = 'AUCUN';
            suggestedMode = 'DISPENSE_NON_PAYEE';
        } else if (newCode === 'RUPTURE_ESSAI') {
            suggestedDuree = '15_JOURS';
            suggestedMode = 'EFFECTUE';
        } else if (newCode === 'LICENCIEMENT') {
            suggestedMode = 'DISPENSE_PAYEE'; // souvent dispensé avec indemnité
        }

        const newEffet = calculerDateEffet(formData.dateNotification, suggestedDuree, suggestedMode, formData.customJours);

        setFormData(prev => ({
            ...prev,
            codeTypeEvenement: newCode,
            dureePreavisKey: suggestedDuree,
            modePreavis: suggestedMode,
            dateEffet: newEffet,
        }));
    };

    const typeEvenementActuel = typesEvenement.find(t => t.code === formData.codeTypeEvenement);
    const libTypeRupture = typeEvenementActuel?.libelle || formData.codeTypeEvenement;

    // Détection si la date d'effet est future (donc workflow planifié)
    const isDateEffetFuture = useMemo(() => {
        if (!formData.dateEffet) return false;
        const todayStr = fmtDateInput(new Date());
        return formData.dateEffet > todayStr;
    }, [formData.dateEffet]);

    // Envoi du brouillon de rupture au backend
    const handleSubmit = async (e) => {
        if (e) e.preventDefault();
        if (submitting) return;
        setErrorMsg('');

        const contratSourceId = contratActif?.idContrat || initialData?.idContratSource || (workflowActif?.isBrouillon ? workflowActif.idContratSource : null);

        if (!formData.dateEffet) return setErrorMsg('La date de prise d\'effet de la rupture est obligatoire.');
        if (!contratSourceId) return setErrorMsg('Impossible de rompre : aucun contrat source trouvé pour cet employé.');

        setSubmitting(true);
        try {
            const payload = {
                codeTypeEvenement: formData.codeTypeEvenement,
                idContratSource: contratSourceId,
                idAffectationSource: affectationActive?.idAffectation || initialData?.idAffectationSource || (workflowActif?.isBrouillon ? workflowActif.idAffectationSource : null),
                dateEffet: formData.dateEffet,
                motif: `[${libTypeRupture}] ${formData.motif || ''}`.trim(),
                commentaireAffectation: formData.commentaire || null,
                descriptionContrat: `Préavis : ${libelleDuree} (${modePreavisInfo.titre})`,
            };

            const wfId = initialData?.idWorkflow || (workflowActif?.isBrouillon ? workflowActif.idWorkflow : null);
            let res;
            if (wfId) {
                res = await contratService.modifierBrouillonWorkflow(wfId, payload);
            } else {
                res = await contratService.creerBrouillonTransition(idEmploye, payload);
            }

            // Mettre en mémoire le résultat et proposer l'enchaînement avec le STC
            setSavedDraftResult(res);
            if (onSuccess) onSuccess('Brouillon de rupture de contrat enregistré avec succès !', res);
        } catch (err) {
            setErrorMsg(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || err.message || 'Erreur lors de la sauvegarde de la rupture.');
        } finally {
            setSubmitting(false);
        }
    };

    // Transition directe vers la simulation de STC avec les données pré-remplies
    const handleLancerSimulationStc = () => {
        if (onOpenStc) {
            onOpenStc({
                dateDepart: formData.dateEffet,
                motifRupture: formData.codeTypeEvenement,
                moisPreavis: moisIndemnite,
                indemnitePreavis: indemniteEstimee > 0 ? indemniteEstimee : null,
            });
        }
    };

    const previewProps = {
        nomEmploye,
        contratActif,
        affectationActive,
        workflowActif,
        formData,
        libTypeRupture,
        libelleDuree,
        modePreavisInfo,
        indemniteEstimee,
    };

    if (!isOpen) return null;

    // Rendu
    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-3 lg:p-5 bg-black/60 backdrop-blur-[3px]">
            <div className="absolute inset-0" onClick={onClose} />
            <div
                className={`
                    relative bg-white dark:bg-ink-900 rounded-none border border-ink-150 dark:border-ink-700 shadow-2xl
                    w-full max-w-[1440px] max-h-[95vh] overflow-hidden flex flex-col
                    transform transition-all duration-200
                    ${mounted ? 'scale-100 opacity-100' : 'scale-95 opacity-0'}
                `}
            >
                {/* Header (fixe) */}
                <div className="px-7 py-4 border-b border-ink-150 dark:border-ink-700 bg-danger-500/10 flex-shrink-0">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2 font-bold text-lg text-danger-700 dark:text-danger-400">
                            <UserMinus size={22} />
                            <span>Rupture & Clôture de Contrat RH</span>
                            {nomEmploye && (
                                <span className="text-xs font-normal text-ink-400"> — {nomEmploye}</span>
                            )}
                        </div>
                        <div className="flex items-center gap-2">
                            <button
                                type="button"
                                onClick={() => setShowPreviewMobile(v => !v)}
                                className="lg:hidden flex items-center gap-1.5 text-xs font-semibold text-danger-600 border border-danger-500/30 rounded-none px-2.5 py-1.5"
                            >
                                {showPreviewMobile ? <EyeOff size={14} /> : <Eye size={14} />}
                                {showPreviewMobile ? 'Masquer l\'aperçu' : 'Voir le document'}
                            </button>
                            <button onClick={onClose} className="text-ink-400 hover:text-ink-900 dark:hover:text-white p-1 rounded-none">
                                <X size={20} />
                            </button>
                        </div>
                    </div>
                </div>

                {/* MODAL ÉCRAN DE SUCCÈS / ENCHAÎNEMENT AVEC STC */}
                {savedDraftResult ? (
                    <div className="p-8 md:p-12 flex flex-col items-center justify-center text-center max-w-2xl mx-auto space-y-6">
                        <div className="w-16 h-16 bg-emerald-500/10 border border-emerald-500/30 text-emerald-600 flex items-center justify-center rounded-none shadow-sm">
                            <CheckCircle2 size={36} />
                        </div>
                        <div className="space-y-2">
                            <h2 className="text-xl font-bold text-ink-900 dark:text-white">
                                Brouillon de Rupture Enregistré
                            </h2>
                            <p className="text-xs text-ink-500 dark:text-ink-400 leading-relaxed">
                                Le motif <strong>{libTypeRupture}</strong> avec date d'effet au <strong>{fmtDateFr(formData.dateEffet)}</strong> a été enregistré en brouillon.
                                {isDateEffetFuture
                                    ? ' Une fois validé, il sera planifié jusqu\'à la date de sortie effective.'
                                    : ' L\'application prendra effet immédiatement lors de la validation finale.'}
                            </p>
                        </div>

                        {/* Récapitulatif préavis & impact STC */}
                        <div className="w-full bg-ink-50 dark:bg-ink-950/60 border border-ink-200 dark:border-ink-800 p-4 text-left space-y-2 text-xs">
                            <div className="flex justify-between border-b border-ink-150 dark:border-ink-800 pb-1.5">
                                <span className="text-ink-400">Date de notification :</span>
                                <span className="font-semibold">{fmtDateFr(formData.dateNotification)}</span>
                            </div>
                            <div className="flex justify-between border-b border-ink-150 dark:border-ink-800 pb-1.5">
                                <span className="text-ink-400">Date de sortie effective :</span>
                                <span className="font-bold text-ink-900 dark:text-white">{fmtDateFr(formData.dateEffet)}</span>
                            </div>
                            <div className="flex justify-between border-b border-ink-150 dark:border-ink-800 pb-1.5">
                                <span className="text-ink-400">Modalité du préavis :</span>
                                <span className="font-semibold text-ink-800 dark:text-ink-200">{libelleDuree} — {modePreavisInfo.titre}</span>
                            </div>
                            <div className="flex justify-between pt-1">
                                <span className="text-ink-400">Impact Solde de Tout Compte :</span>
                                <span className="font-bold text-amber-600 dark:text-amber-400">
                                    {formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0
                                        ? `${fmtMoneyMGA(indemniteEstimee)} d'indemnité compensatrice`
                                        : '0 Ar (Aucune indemnité compensatrice de préavis)'}
                                </span>
                            </div>
                        </div>

                        {/* Actions d'enchaînement */}
                        <div className="flex flex-col sm:flex-row gap-3 w-full justify-center pt-2">
                            <Button
                                type="button"
                                variant="outline"
                                size="md"
                                onClick={onClose}
                                className="w-full sm:w-auto"
                            >
                                Terminer & Fermer
                            </Button>
                            {onOpenStc && (
                                <Button
                                    type="button"
                                    variant="brand"
                                    size="md"
                                    iconRight={<ArrowRight size={16} />}
                                    onClick={handleLancerSimulationStc}
                                    className="w-full sm:w-auto font-bold shadow-md"
                                >
                                    Préparer & Simuler le STC de sortie
                                </Button>
                            )}
                        </div>
                    </div>
                ) : (
                    /* Corps Split Screen 50/50 Formulaire (6/12) - Aperçu Document (6/12) */
                    <div className="flex-1 overflow-y-auto grid grid-cols-1 lg:grid-cols-12 divide-y lg:divide-y-0 lg:divide-x divide-ink-150 dark:divide-ink-700">

                        {/* COLONNE GAUCHE (6/12) : Formulaire interactif */}
                        <form onSubmit={handleSubmit} className={`p-4 sm:p-5 space-y-5 lg:col-span-6 overflow-y-auto ${showPreviewMobile ? 'hidden lg:block' : 'block'}`}>
                            {errorMsg && (
                                <div className="bg-danger-500/10 border border-danger-500/30 text-danger-600 dark:text-danger-400 p-4 rounded-none text-xs font-semibold flex items-center gap-2">
                                    <ShieldAlert size={16} className="flex-shrink-0" />
                                    <span>{errorMsg}</span>
                                </div>
                            )}

                            {loadingRef ? (
                                <div className="py-12 flex justify-center">
                                    <Loading size="lg" label="Chargement des motifs de rupture..." />
                                </div>
                            ) : (
                                <>
                                    {/* SECTION 1 : NATURE & MOTIF DE RUPTURE */}
                                    <div className="space-y-3">
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-danger-600 dark:text-danger-400 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                            <AlertOctagon size={14} /> 1. Nature & Motif de Sortie
                                        </h3>

                                        <Select
                                            id="codeTypeEvenement"
                                            name="codeTypeEvenement"
                                            label="Type de Rupture / Motif *"
                                            required
                                            value={formData.codeTypeEvenement}
                                            onChange={(e) => handleTypeEvenementChange(e.target.value)}
                                        >
                                            {typesEvenement
                                                .filter(t => t.estActif !== false && t.actif !== false || String(t.code) === String(formData.codeTypeEvenement))
                                                .map(t => (
                                                    <option key={t.code} value={t.code}>{t.libelle}</option>
                                                ))}
                                        </Select>
                                    </div>

                                    {/* SECTION 2 : CALENDRIER & MODALITÉS DU PRÉAVIS */}
                                    <div className="space-y-4">
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-danger-600 dark:text-danger-400 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                            <Calendar size={14} /> 2. Calendrier & Régime du Préavis
                                        </h3>

                                        {/* Date de notification */}
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                            <div>
                                                <Input
                                                    id="dateNotification"
                                                    name="dateNotification"
                                                    label="Date de Notification *"
                                                    type="date"
                                                    required
                                                    value={formData.dateNotification}
                                                    onChange={(e) => handleNotificationDateChange(e.target.value)}
                                                />
                                                <p className="text-[10.5px] text-ink-400 mt-1">Date à laquelle la rupture est formellement notifiée.</p>
                                            </div>

                                            <div>
                                                <Select
                                                    id="dureePreavisKey"
                                                    name="dureePreavisKey"
                                                    label="Délai de Préavis Légal / Conventionnel *"
                                                    value={formData.dureePreavisKey}
                                                    onChange={(e) => handleDureePreavisChange(e.target.value)}
                                                >
                                                    {DUREES_PREAVIS.map(d => (
                                                        <option key={d.key} value={d.key}>{d.label}</option>
                                                    ))}
                                                </Select>
                                                <p className="text-[10.5px] text-ink-400 mt-1">{dureeInfo.desc}</p>
                                            </div>
                                        </div>

                                        {/* Saisie personnalisée si "CUSTOM" */}
                                        {formData.dureePreavisKey === 'CUSTOM' && (
                                            <div className="bg-ink-50 dark:bg-ink-950 p-3 border border-ink-200 dark:border-ink-800">
                                                <Input
                                                    id="customJours"
                                                    name="customJours"
                                                    type="number"
                                                    min="1"
                                                    label="Nombre de jours calendaires du préavis *"
                                                    value={formData.customJours}
                                                    onChange={(e) => handleCustomJoursChange(e.target.value)}
                                                    placeholder="Ex: 45"
                                                />
                                            </div>
                                        )}

                                        {/* CHOIX DU RÉGIME D'EXÉCUTION DU PRÉAVIS (3 OPTIONS CLAIRES) */}
                                        <div className="space-y-2 pt-1">
                                            <label className="text-xs font-bold text-ink-700 dark:text-ink-300 flex items-center gap-1.5">
                                                <Clock size={13} className="text-danger-500" />
                                                Modalité d'Exécution du Préavis *
                                            </label>

                                            <div className="grid grid-cols-1 gap-2">
                                                {MODES_PREAVIS.map(mode => {
                                                    const isSelected = formData.modePreavis === mode.key;
                                                    return (
                                                        <div
                                                            key={mode.key}
                                                            onClick={() => handleModePreavisChange(mode.key)}
                                                            className={`
                                                                cursor-pointer p-3 border transition-all text-left flex items-start gap-3
                                                                ${isSelected
                                                                    ? 'border-danger-500 bg-danger-500/5 ring-1 ring-danger-500/20'
                                                                    : 'border-ink-200 dark:border-ink-800 hover:border-ink-300 dark:hover:border-ink-700 bg-white dark:bg-ink-900'}
                                                            `}
                                                        >
                                                            <input
                                                                type="radio"
                                                                name="modePreavisRadio"
                                                                checked={isSelected}
                                                                onChange={() => handleModePreavisChange(mode.key)}
                                                                className="mt-1 text-danger-600 focus:ring-danger-500 cursor-pointer"
                                                            />
                                                            <div className="flex-1 space-y-1">
                                                                <div className="flex items-center justify-between gap-2 flex-wrap">
                                                                    <span className="text-xs font-bold text-ink-900 dark:text-white">
                                                                        {mode.titre}
                                                                    </span>
                                                                    <span className={`text-[10px] font-semibold uppercase px-1.5 py-0.5 border ${mode.badgeClass}`}>
                                                                        {mode.badge}
                                                                    </span>
                                                                </div>
                                                                <p className="text-[11.5px] text-ink-500 dark:text-ink-400 leading-snug">
                                                                    {mode.desc}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    );
                                                })}
                                            </div>
                                        </div>

                                        {/* Date de sortie effective (Calculée et interactive) */}
                                        <div className="pt-2">
                                            <Input
                                                id="dateEffet"
                                                name="dateEffet"
                                                label="Date de Sortie Effective (Prise d'effet du départ) *"
                                                type="date"
                                                required
                                                value={formData.dateEffet}
                                                onChange={(e) => setFormData(prev => ({ ...prev, dateEffet: e.target.value }))}
                                            />
                                            <p className="text-[10.5px] text-ink-400 mt-1">
                                                Calculée automatiquement selon la date de notification et le préavis. Vous pouvez la réajuster si nécessaire.
                                            </p>
                                        </div>

                                        {/* CALLOUT INTERACTIF : IMPACT CONTRAT, CALENDRIER & PAIE / STC */}
                                        <div className="p-3.5 bg-ink-50 dark:bg-ink-950/80 border border-ink-200 dark:border-ink-800 space-y-2 text-xs">
                                            <div className="flex items-center gap-2 font-bold text-ink-800 dark:text-ink-200 text-[11.5px]">
                                                <CalendarDays size={14} className="text-danger-500" />
                                                <span>Synthèse de l'impact opérationnel & financier :</span>
                                            </div>
                                            <ul className="space-y-1.5 text-[11.5px] text-ink-600 dark:text-ink-300">
                                                <li className="flex items-start gap-2">
                                                    <span className="font-semibold text-ink-700 dark:text-ink-200 w-32 flex-shrink-0">• Sortie effective :</span>
                                                    <span><strong>{fmtDateFr(formData.dateEffet)}</strong> ({isDateEffetFuture ? 'Départ futur / planifié' : 'Départ immédiat'})</span>
                                                </li>
                                                <li className="flex items-start gap-2">
                                                    <span className="font-semibold text-ink-700 dark:text-ink-200 w-32 flex-shrink-0">• Statut SIRH :</span>
                                                    <span>
                                                        {isDateEffetFuture
                                                            ? 'Le contrat reste ACTIF jusqu\'au départ. Workflow PLANIFIÉ pour clôture automatique.'
                                                            : 'Le contrat passe en CLOS dès la validation du workflow.'}
                                                    </span>
                                                </li>
                                                <li className="flex items-start gap-2">
                                                    <span className="font-semibold text-ink-700 dark:text-ink-200 w-32 flex-shrink-0">• Impact STC :</span>
                                                    <span className={formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0 ? 'text-amber-600 dark:text-amber-400 font-bold' : ''}>
                                                        {formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0
                                                            ? `${libelleDuree} compensé(s) soit env. ${fmtMoneyMGA(indemniteEstimee)} d'indemnité compensatrice dans le STC.`
                                                            : '0 Ar d\'indemnité de préavis (les mois effectués sont payés sous forme de salaires réguliers).'}
                                                    </span>
                                                </li>
                                            </ul>
                                        </div>
                                    </div>

                                    {/* SECTION 3 : JUSTIFICATION RH */}
                                    <div className="space-y-3 pt-2">
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-danger-600 dark:text-danger-400 pb-2 border-b border-ink-150 dark:border-ink-700 flex items-center gap-2">
                                            <FileSignature size={14} /> 3. Justification RH & Offboarding
                                        </h3>

                                        <Textarea
                                            id="motif"
                                            name="motif"
                                            label="Motif Détaillé de la Rupture *"
                                            required
                                            rows={2}
                                            value={formData.motif}
                                            onChange={(e) => setFormData(prev => ({ ...prev, motif: e.target.value }))}
                                            placeholder="Ex: Démission déposée le 10/08 par lettre remise en main propre..."
                                        />

                                        <Textarea
                                            id="commentaire"
                                            name="commentaire"
                                            label="Consignes d'Offboarding & Clôture (Optionnel)"
                                            rows={2}
                                            value={formData.commentaire}
                                            onChange={(e) => setFormData(prev => ({ ...prev, commentaire: e.target.value }))}
                                            placeholder="Ex: Restitution du badge d'accès, ordinateur portable et dossier de passation..."
                                        />
                                    </div>
                                </>
                            )}
                        </form>

                        {/* COLONNE DROITE (6/12) : Aperçu du document de sortie */}
                        <div className={`p-7 bg-ink-50/50 dark:bg-ink-950/30 lg:col-span-6 overflow-y-auto space-y-4 ${showPreviewMobile ? 'block' : 'hidden lg:block'}`}>
                            <div className="flex items-center justify-between pb-2 border-b border-ink-200 dark:border-ink-700">
                                <h3 className="text-xs font-bold uppercase tracking-wider text-ink-500 dark:text-ink-400 flex items-center gap-2">
                                    <FileSignature size={14} className="text-danger-500" />
                                    Aperçu de la Lettre / Notification de Rupture
                                </h3>
                            </div>
                            <RupturePreview {...previewProps} />
                        </div>
                    </div>
                )}

                {/* Footer Actions (fixe) */}
                {!savedDraftResult && (
                    <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex-shrink-0 flex items-center justify-between gap-3 flex-wrap">
                        <div className="flex items-center gap-2 text-[11px] text-ink-400">
                            <HelpCircle size={14} className="text-ink-400" />
                            <span>
                                La rupture sera enregistrée en <span className="font-bold text-danger-600">BROUILLON</span> modifiable avant validation.
                            </span>
                        </div>
                        <div className="flex items-center gap-3">
                            <Button type="button" onClick={onClose} variant="outline" size="md">
                                Annuler
                            </Button>
                            {onOpenStc && (
                                <Button
                                    type="button"
                                    onClick={handleLancerSimulationStc}
                                    variant="secondary"
                                    size="md"
                                    iconLeft={<Calculator size={16} />}
                                    title="Ouvre la simulation financière sans sauvegarder"
                                >
                                    Simuler le STC
                                </Button>
                            )}
                            <Button
                                type="button"
                                onClick={handleSubmit}
                                variant="danger"
                                size="md"
                                loading={submitting}
                                iconLeft={<UserMinus size={16} />}
                            >
                                Enregistrer le Brouillon de Rupture
                            </Button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
