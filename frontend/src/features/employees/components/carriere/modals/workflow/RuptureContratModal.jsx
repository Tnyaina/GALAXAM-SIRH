import { useState, useEffect, useCallback, useMemo } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import {
    UserMinus, FileSignature, Calendar, AlertOctagon,
    Clock, ArrowRight, CheckCircle2, HelpCircle, CalendarDays
} from 'lucide-react';
import FormError from '@/components/ui/feedback/FormError';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea, Select } from '@/components/ui/forms/Input';
import Loading from '@/components/ui/feedback/Loading';
import Modal from '@/components/ui/overlays/Modal';
import contratService from '@/features/employees/api/contratService';
import { fmtDateFr, fmtDateInput } from '@/utils/dateUtils';
import { fmtMoneyMGA } from '@/utils/moneyUtils';

// ── OPTIONS DE DURÉE DE PRÉAVIS ─────────────────────────────────────────────
const DUREES_PREAVIS = [
    { key: 'AUCUN', label: 'Aucun préavis (0 j)', jours: 0, moisEquiv: 0, desc: 'Sortie immédiate sans préavis.' },
    { key: '15_JOURS', label: '15 jours', jours: 15, moisEquiv: 0.5, desc: 'Période d\'essai ou contrat court.' },
    { key: '1_MOIS', label: '1 mois (30 j)', jours: 30, moisEquiv: 1.0, desc: 'Ouvriers et employés standard.' },
    { key: '2_MOIS', label: '2 mois (60 j)', jours: 60, moisEquiv: 2.0, desc: 'Agents de maîtrise et techniciens.' },
    { key: '3_MOIS', label: '3 mois (90 j)', jours: 90, moisEquiv: 3.0, desc: 'Cadres et encadrement.' },
    { key: 'CUSTOM', label: 'Personnalisé (en jours)...', jours: null, moisEquiv: null, desc: 'Durée spécifique en jours calendaires.' },
];

// ── MODALITÉS D'EXÉCUTION DU PRÉAVIS ────────────────────────────────────────
const MODES_PREAVIS = [
    {
        key: 'EFFECTUE',
        titre: 'Préavis effectué et travaillé',
        badge: 'En poste jusqu\'au terme',
        badgeClass: 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border-[var(--color-brand-500)]/30',
        impactTitre: 'Sortie différée au terme du préavis',
        impactStc: '0 Ar (salaire versé normalement chaque mois)',
        desc: 'Activité maintenue jusqu\'au terme du préavis. Rémunération standard sans indemnité compensatrice au STC.'
    },
    {
        key: 'DISPENSE_PAYEE',
        titre: 'Dispensé par l\'employeur (Payé)',
        badge: 'Sortie immédiate indemnisée',
        badgeClass: 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] border-[var(--color-warning-500)]/30',
        impactTitre: 'Sortie effective immédiate',
        impactStc: 'Indemnité compensatrice versée au STC',
        desc: 'Dispense de travail accordée par l\'employeur avec versement d\'une indemnité compensatrice au STC.'
    },
    {
        key: 'DISPENSE_NON_PAYEE',
        titre: 'Dispensé à la demande du salarié (Non payé)',
        badge: 'Sortie immédiate sans indemnité',
        badgeClass: 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)]',
        impactTitre: 'Sortie effective immédiate',
        impactStc: '0 Ar (dispense demandée par le salarié)',
        desc: 'Dispense de travail à la demande du salarié sans indemnité compensatrice de préavis.'
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
    const [loadingRef, setLoadingRef] = useState(true);
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
        }
    }, [isOpen, loadReferentiels, initialData, workflowActif, contratActif, affectationActive]);

    // Détermination de la durée en mois pour le calcul STC
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
        let suggestedDuree = formData.dureePreavisKey;
        let suggestedMode = formData.modePreavis;

        if (newCode === 'FIN_CONTRAT') {
            suggestedDuree = 'AUCUN';
            suggestedMode = 'DISPENSE_NON_PAYEE';
        } else if (newCode === 'RUPTURE_ESSAI') {
            suggestedDuree = '15_JOURS';
            suggestedMode = 'EFFECTUE';
        } else if (newCode === 'LICENCIEMENT') {
            suggestedMode = 'DISPENSE_PAYEE';
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

    // Détection si la date d'effet est future
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

            setSavedDraftResult(res);
            if (onSuccess) onSuccess('Brouillon de rupture de contrat enregistré avec succès.', res);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors de la sauvegarde de la rupture.'));
        } finally {
            setSubmitting(false);
        }
    };

    const handleLancerSimulationStc = () => {
        if (onOpenStc) {
            onOpenStc({
                dateDepart: formData.dateEffet,
                motifRupture: formData.codeTypeEvenement,
                modePreavis: formData.modePreavis,
                moisPreavis: moisIndemnite,
                indemnitePreavis: indemniteEstimee > 0 ? indemniteEstimee : null,
            });
        }
    };

    if (!isOpen) return null;

    // Rendu en cas de confirmation d'enregistrement
    if (savedDraftResult) {
        return (
            <Modal
                isOpen={isOpen}
                onClose={onClose}
                title="Rupture de contrat enregistrée"
                icon={<CheckCircle2 size={20} className="text-[var(--color-success-600)]" />}
                size="xl"
                footer={
                    <div className="flex items-center justify-end gap-3 w-full">
                        <Button
                            type="button"
                            variant="outline"
                            size="md"
                            onClick={onClose}
                        >
                            Terminer et fermer
                        </Button>
                        {onOpenStc && (
                            <Button
                                type="button"
                                variant="brand"
                                size="md"
                                iconRight={<ArrowRight size={16} />}
                                onClick={handleLancerSimulationStc}
                            >
                                Préparer le Solde de Tout Compte
                            </Button>
                        )}
                    </div>
                }
            >
                <div className="space-y-5 py-2">
                    <div className="flex items-start gap-4 p-4 bg-[var(--color-success-500)]/10 border border-[var(--color-success-500)]/30">
                        <CheckCircle2 size={24} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                        <div className="space-y-1">
                            <h3 className="text-sm font-semibold text-[var(--color-fg)]">
                                Brouillon de fin de contrat créé
                            </h3>
                            <p className="text-xs text-[var(--color-fg-muted)] leading-relaxed">
                                Le motif <strong className="text-[var(--color-fg)]">{libTypeRupture}</strong> avec date de sortie au <strong className="text-[var(--color-fg)]">{fmtDateFr(formData.dateEffet)}</strong> a été enregistré.
                                {isDateEffetFuture
                                    ? ' Après validation finale, l\'offboarding sera planifié jusqu\'au terme du préavis.'
                                    : ' L\'application prendra effet immédiatement lors de la validation finale.'}
                            </p>
                        </div>
                    </div>

                    {/* Synthèse du préavis et impact STC */}
                    <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 space-y-2.5 text-xs">
                        <div className="flex justify-between border-b border-[var(--color-border-subtle)] pb-2">
                            <span className="text-[var(--color-fg-muted)]">Collaborateur</span>
                            <span className="font-semibold text-[var(--color-fg)]">{nomEmploye || 'Non renseigné'}</span>
                        </div>
                        <div className="flex justify-between border-b border-[var(--color-border-subtle)] pb-2">
                            <span className="text-[var(--color-fg-muted)]">Date de notification</span>
                            <span className="font-semibold text-[var(--color-fg)]">{fmtDateFr(formData.dateNotification)}</span>
                        </div>
                        <div className="flex justify-between border-b border-[var(--color-border-subtle)] pb-2">
                            <span className="text-[var(--color-fg-muted)]">Date de sortie effective</span>
                            <span className="font-bold text-[var(--color-fg)]">{fmtDateFr(formData.dateEffet)}</span>
                        </div>
                        <div className="flex justify-between border-b border-[var(--color-border-subtle)] pb-2">
                            <span className="text-[var(--color-fg-muted)]">Régime de préavis</span>
                            <span className="font-semibold text-[var(--color-fg)]">{libelleDuree} · {modePreavisInfo.titre}</span>
                        </div>
                        <div className="flex justify-between items-center pt-1">
                            <span className="text-[var(--color-fg-muted)]">Impact préavis au STC</span>
                            <span className={`font-semibold ${formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0 ? 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' : 'text-[var(--color-fg)]'}`}>
                                {formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0
                                    ? `${fmtMoneyMGA(indemniteEstimee)} d'indemnité compensatrice`
                                    : '0 Ar (aucune indemnité compensatrice)'}
                            </span>
                        </div>
                    </div>
                </div>
            </Modal>
        );
    }

    // Modal du formulaire de rupture
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={nomEmploye ? `Rupture de contrat · ${nomEmploye}` : 'Rupture de contrat'}
            icon={<UserMinus size={20} className="text-[var(--color-danger-500)]" />}
            size="2xl"
            footer={
                <div className="flex w-full flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)]">
                        <HelpCircle size={14} className="text-[var(--color-fg-subtle)]" />
                        <span>Brouillon · validation requise</span>
                    </div>
                    <div className="flex flex-wrap items-center justify-end gap-2">
                        <Button type="button" onClick={onClose} variant="outline" size="md">
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            onClick={handleSubmit}
                            variant="primary"
                            size="md"
                            loading={submitting}
                            iconLeft={<UserMinus size={16} />}
                        >
                            Enregistrer le brouillon
                        </Button>
                    </div>
                </div>
            }
        >
            <form onSubmit={handleSubmit} className="space-y-5 py-1">
                {errorMsg && <FormError message={errorMsg} />}

                {loadingRef ? (
                    <div className="py-12 flex justify-center">
                        <Loading size="lg" label="Chargement des motifs de rupture..." />
                    </div>
                ) : (
                    <>
                        {/* SECTION 1 : NATURE ET MOTIF DE SORTIE */}
                        <div className="space-y-3">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-2">
                                <AlertOctagon size={14} /> 1. Type de rupture
                            </h3>

                            <Select
                                id="codeTypeEvenement"
                                name="codeTypeEvenement"
                                label="Type de rupture"
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

                        {/* SECTION 2 : CALENDRIER ET PRÉAVIS */}
                        <div className="space-y-4">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-2">
                                <Calendar size={14} /> 2. Calendrier et préavis
                            </h3>

                            {/* Date notification & Délai préavis */}
                            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                                <Input
                                    id="dateNotification"
                                    name="dateNotification"
                                    label="Date de notification"
                                    type="date"
                                    required
                                    value={formData.dateNotification}
                                    onChange={(e) => handleNotificationDateChange(e.target.value)}
                                />

                                <div>
                                    <Select
                                        id="dureePreavisKey"
                                        name="dureePreavisKey"
                                        label="Délai de préavis"
                                        required
                                        value={formData.dureePreavisKey}
                                        onChange={(e) => handleDureePreavisChange(e.target.value)}
                                    >
                                        {DUREES_PREAVIS.map(d => (
                                            <option key={d.key} value={d.key}>{d.label}</option>
                                        ))}
                                    </Select>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] mt-1">{dureeInfo.desc}</p>
                                </div>
                            </div>

                            {/* Saisie personnalisée si "CUSTOM" */}
                            {formData.dureePreavisKey === 'CUSTOM' && (
                                <div className="bg-[var(--color-bg-subtle)] p-3 border border-[var(--color-border-subtle)]">
                                    <Input
                                        id="customJours"
                                        name="customJours"
                                        type="number"
                                        min="1"
                                        label="Nombre de jours calendaires du préavis"
                                        value={formData.customJours}
                                        onChange={(e) => handleCustomJoursChange(e.target.value)}
                                        placeholder="Ex. 45"
                                    />
                                </div>
                            )}

                            {/* CHOIX DU RÉGIME D'EXÉCUTION DU PRÉAVIS */}
                            <fieldset className="space-y-2 pt-1">
                                <legend className="text-xs font-bold text-[var(--color-fg)]">
                                    <Clock size={13} className="text-[var(--color-danger-500)]" />
                                    Modalité d'exécution du préavis *
                                </legend>

                                <div className="grid grid-cols-1 gap-2" role="radiogroup" aria-label="Modalité d'exécution du préavis">
                                    {MODES_PREAVIS.map(mode => {
                                        const isSelected = formData.modePreavis === mode.key;
                                        return (
                                            <label
                                                key={mode.key}
                                                className={`
                                                    cursor-pointer p-3 border transition-all text-left flex items-start gap-3
                                                    ${isSelected
                                                        ? 'border-[var(--color-brand-500)] bg-[var(--color-brand-500)]/10 ring-1 ring-[var(--color-brand-500)]/20'
                                                        : 'border-[var(--color-border-subtle)] hover:border-[var(--color-border)] bg-[var(--color-surface)]'}
                                                `}
                                            >
                                                <input
                                                    type="radio"
                                                    name="modePreavisRadio"
                                                    value={mode.key}
                                                    checked={isSelected}
                                                    onChange={() => handleModePreavisChange(mode.key)}
                                                    className="mt-1 cursor-pointer focus:ring-[var(--color-focus-ring)]"
                                                    style={{ accentColor: 'var(--color-brand-600)' }}
                                                    required
                                                />
                                                <div className="flex-1 space-y-1">
                                                    <div className="flex items-center justify-between gap-2 flex-wrap">
                                                        <span className="text-xs font-bold text-[var(--color-fg)]">
                                                            {mode.titre}
                                                        </span>
                                                        <span className={`text-[10px] font-semibold uppercase px-1.5 py-0.5 border ${mode.badgeClass}`}>
                                                            {mode.badge}
                                                        </span>
                                                    </div>
                                                    <p className="text-[11.5px] text-[var(--color-fg-muted)] leading-snug">
                                                        {mode.desc}
                                                    </p>
                                                </div>
                                            </label>
                                        );
                                    })}
                                </div>
                            </fieldset>

                            {/* Date de sortie effective */}
                            <Input
                                id="dateEffet"
                                name="dateEffet"
                                label="Date de sortie effective"
                                type="date"
                                required
                                value={formData.dateEffet}
                                onChange={(e) => setFormData(prev => ({ ...prev, dateEffet: e.target.value }))}
                            />

                            {/* SYNTHÈSE OPÉRATIONNELLE ET FINANCIÈRE */}
                            <div className="p-3.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-2 text-xs">
                                <div className="flex items-center gap-2 font-bold text-[var(--color-fg)] text-[11.5px]">
                                    <CalendarDays size={14} className="text-[var(--color-danger-500)]" />
                                    <span>Synthèse de la sortie</span>
                                </div>
                                <div className="space-y-1.5 text-[11.5px] text-[var(--color-fg-muted)]">
                                    <div className="flex items-start justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-1.5">
                                        <span className="text-[var(--color-fg-muted)]">Sortie effective</span>
                                        <span className="font-semibold text-[var(--color-fg)]">
                                            {fmtDateFr(formData.dateEffet)} ({isDateEffetFuture ? 'Planifiée' : 'Immédiate'})
                                        </span>
                                    </div>
                                    <div className="flex items-start justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-1.5">
                                        <span className="text-[var(--color-fg-muted)]">Régime retenu</span>
                                        <span className="font-semibold text-[var(--color-fg)]">
                                            {libelleDuree} · {modePreavisInfo.titre}
                                        </span>
                                    </div>
                                    <div className="flex items-start justify-between gap-2 pt-0.5">
                                        <span className="text-[var(--color-fg-muted)]">Impact Solde de Tout Compte</span>
                                        <span className={formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0 ? 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-bold' : 'font-semibold text-[var(--color-fg)]'}>
                                            {formData.modePreavis === 'DISPENSE_PAYEE' && indemniteEstimee > 0
                                                ? `${fmtMoneyMGA(indemniteEstimee)} d'indemnité compensatrice`
                                                : '0 Ar (aucune indemnité compensatrice)'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* SECTION 3 : MOTIF ET CONSIGNES RH */}
                        <div className="space-y-3 pt-1">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] pb-2 border-b border-[var(--color-border-subtle)] flex items-center gap-2">
                                <FileSignature size={14} /> 3. Motif et consignes RH
                            </h3>

                            <Textarea
                                id="motif"
                                name="motif"
                                label="Motif officiel de la rupture"
                                required
                                rows={2}
                                value={formData.motif}
                                onChange={(e) => setFormData(prev => ({ ...prev, motif: e.target.value }))}
                                placeholder="Motif officiel..."
                            />

                            <Textarea
                                id="commentaire"
                                name="commentaire"
                                label="Consignes de passation et clôture (Optionnel)"
                                rows={2}
                                value={formData.commentaire}
                                onChange={(e) => setFormData(prev => ({ ...prev, commentaire: e.target.value }))}
                                placeholder="Restitution matériel, passation des dossiers, badge d'accès..."
                            />
                        </div>
                    </>
                )}
            </form>
        </Modal>
    );
}
