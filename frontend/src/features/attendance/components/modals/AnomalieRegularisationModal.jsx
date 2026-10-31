import FormError from '@/components/ui/feedback/FormError';
import { useState, useEffect, useRef, useCallback } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { ChevronDown } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import { fmtDate, shiftIsoDate } from '@/utils/dateUtils';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { loadPointageDayContext, selectPointageDayAllowances } from '@/features/attendance/utils/pointageDayContext';
import PointageAllowanceSelector from '@/features/attendance/components/controls/PointageAllowanceSelector';
import congeService from '@/features/leave/api/congeService';

const MOTIFS_FAMILIAUX = [
    { label: 'Mariage du salarié (3 jours)', value: 'Mariage du salarié' },
    { label: 'Naissance ou adoption d\'un enfant (2 jours)', value: 'Naissance d\'un enfant' },
    { label: 'Décès conjoint ou enfant (3 jours)', value: 'Décès conjoint ou enfant' },
    { label: 'Décès parent ou beau-parent (2 jours)', value: 'Décès parent' },
    { label: 'Autre événement familial autorisé', value: 'Événement familial' }
];

export default function AnomalieRegularisationModal({
    isOpen,
    onClose,
    anomalie,
    onSuccess,
    directDayFlow = false
}) {
    // État local : onglet, soumission et formulaire de régularisation.
    const [activeTab, setActiveTab] = useState('HEURES'); // 'HEURES', 'ABSENCE' ou 'RETARD_REEL'
    const [saving, setSaving] = useState(false);
    const submissionInFlight = useRef(false);
    const [error, setError] = useState('');
    const [dayPreview, setDayPreview] = useState(null);
    const [previewPending, setPreviewPending] = useState(false);
    const [availableAllowances, setAvailableAllowances] = useState([]);
    const [dynamicAllowances, setDynamicAllowances] = useState({});
    const [absenceAllowances, setAbsenceAllowances] = useState({});
    const [allowancesLoading, setAllowancesLoading] = useState(false);
    const [allowancesError, setAllowancesError] = useState('');
    const [leaveSituation, setLeaveSituation] = useState(null);
    const [leaveSituationLoading, setLeaveSituationLoading] = useState(false);

    // Formulaire Mode 1 : Horaires
    const [modePresence, setModePresence] = useState('JOURNEE'); // 'JOURNEE', 'MATIN_SEUL', 'APRES_MIDI_SEUL', 'CONTINU'
    const [optionAutreDemiJournee, setOptionAutreDemiJournee] = useState('ABSENCE_DEDUITE'); // Par défaut : ABSENCE_DEDUITE
    const [heureEntreeMatin, setHeureEntreeMatin] = useState('');
    const [heureSortieMatin, setHeureSortieMatin] = useState('');
    const [heureEntreeAm, setHeureEntreeAm] = useState('');
    const [heureSortieAm, setHeureSortieAm] = useState('');

    // Formulaire Mode 2 : Absence
    const [optionAbsence, setOptionAbsence] = useState('CONGE_PAYE'); // 'CONGE_PAYE', 'PERMISSION_EXCEPTIONNELLE', 'ABSENCE_DEDUITE'
    const [congeDuree, setCongeDuree] = useState('1.0'); // '1.0' ou '0.5'
    const [dateReprise, setDateReprise] = useState('');
    const [demiJourneeMoment, setDemiJourneeMoment] = useState('MATIN');
    const [motifFamilial, setMotifFamilial] = useState(MOTIFS_FAMILIAUX[0].value);
    const [statutAbsenceDeduite, setStatutAbsenceDeduite] = useState('ABSENCE_JUSTIFIEE');

    // Motif global obligatoire
    const [motifAjustement, setMotifAjustement] = useState('');

    // Actions utilisateur
    const handleModeChange = (mode) => {
        setModePresence(mode);
    };

    // Effet : initialise le formulaire à chaque anomalie ouverte.
    useEffect(() => {
        if (!anomalie) return;
        setError('');
        
        const hasHours = Boolean(anomalie.heureEntreeMatin || anomalie.heureSortieMatin || anomalie.heureEntreeAm || anomalie.heureSortieAm);
        const isAbsence = anomalie.typeAnomalie === 'ABSENCE'
            || ['ABSENCE_NON_JUSTIFIEE', 'ABSENCE_JUSTIFIEE', 'CONGE'].includes(anomalie.statutCode)
            || (directDayFlow && !hasHours && anomalie.typeAnomalie !== 'RETARD');
        setActiveTab(anomalie.regularisationIntent ? 'ABSENCE'
            : anomalie.importHalfDayMoment ? 'HEURES' : isAbsence ? 'ABSENCE' : 'HEURES');

        const em = anomalie.heureEntreeMatin || '';
        const sm = anomalie.heureSortieMatin || '';
        const ea = anomalie.heureEntreeAm || '';
        const sa = anomalie.heureSortieAm || '';

        setHeureEntreeMatin(em || anomalie.heureDebutMatinSite || '');
        setHeureSortieMatin(sm || anomalie.heureFinMatinSite || '');
        setHeureEntreeAm(ea || anomalie.heureDebutAmSite || '');
        setHeureSortieAm(sa || anomalie.heureFinAmSite || '');

        const isContinu = Boolean(anomalie.horaireContinuJour || anomalie.horaireContinu);
        const isMatinSeul = (em && sm && !ea && !sa) || (anomalie.statutCode === 'DEMI_JOURNEE' && em && sm);
        const isAmSeul = (!em && !sm && ea && sa) || (anomalie.statutCode === 'DEMI_JOURNEE' && ea && sa);

        if (isContinu) {
            setModePresence('CONTINU');
        } else if (isMatinSeul) {
            setModePresence('MATIN_SEUL');
        } else if (isAmSeul) {
            setModePresence('APRES_MIDI_SEUL');
        } else {
            setModePresence('JOURNEE');
        }

        setOptionAutreDemiJournee(anomalie.importHalfDayMoment
            ? (anomalie.importEventCode === 'C' ? 'CONGE_PAYE' : 'ABSENCE_DEDUITE') : 'ABSENCE_DEDUITE');

        setOptionAbsence(anomalie.regularisationIntent
            || (anomalie.importEventCode === 'A' ? 'ABSENCE_DEDUITE' : 'CONGE_PAYE'));
        setCongeDuree(anomalie.importHalfDayMoment ? '0.5' : '1.0');
        setDateReprise(anomalie.importEventCode === 'C' && !anomalie.importHalfDayMoment
            ? '' : shiftIsoDate(anomalie.datePointage, 1));
        setDemiJourneeMoment(anomalie.importHalfDayMoment || 'MATIN');
        setMotifFamilial(MOTIFS_FAMILIAUX[0].value);
        setStatutAbsenceDeduite('ABSENCE_JUSTIFIEE');

        setMotifAjustement(anomalie.motifAjustement || '');
    }, [anomalie, isOpen, directDayFlow]);

    useEffect(() => {
        if (!isOpen || !anomalie?.idSite || Number(anomalie.idSite) <= 0 || !anomalie?.datePointage) return undefined;
        let cancelled = false;
        setAllowancesLoading(true);
        setAllowancesError('');
        loadPointageDayContext(attendanceService.getGrilleSaisie, {
            idSite: anomalie.idSite, date: anomalie.datePointage, idEmploye: anomalie.idEmploye
        }).then(({ row, allowances }) => {
                if (cancelled) return;
                const importedWithoutHours = Boolean(anomalie.importEventCode)
                    && !anomalie.heureEntreeMatin && !anomalie.heureSortieMatin
                    && !anomalie.heureEntreeAm && !anomalie.heureSortieAm;
                setAvailableAllowances(allowances);
                setDynamicAllowances(selectPointageDayAllowances(allowances, row, {
                    overrides: anomalie.importAllowanceOverrides,
                    clearWithoutHours: importedWithoutHours
                }));
                setAbsenceAllowances(selectPointageDayAllowances(allowances, row, {
                    overrides: anomalie.importAllowanceOverrides,
                    clearWithoutHours: true
                }));
            })
            .catch((cause) => {
                if (!cancelled) setAllowancesError(cause?.message || 'Indemnités du site indisponibles.');
            })
            .finally(() => {
                if (!cancelled) setAllowancesLoading(false);
            });
        return () => { cancelled = true; };
    }, [isOpen, anomalie?.idSite, anomalie?.datePointage, anomalie?.idEmploye,
        anomalie?.heureEntreeMatin, anomalie?.heureSortieMatin, anomalie?.heureEntreeAm,
        anomalie?.heureSortieAm, anomalie?.importAllowanceOverrides, anomalie?.importEventCode]);

    useEffect(() => {
        if (!isOpen || !anomalie?.idEmploye) return undefined;
        let cancelled = false;
        setLeaveSituation(null);
        setLeaveSituationLoading(true);
        congeService.getSituationEmploye(anomalie.idEmploye)
            .then((situation) => { if (!cancelled) setLeaveSituation(situation); })
            .catch(() => { if (!cancelled) setLeaveSituation(null); })
            .finally(() => { if (!cancelled) setLeaveSituationLoading(false); });
        return () => { cancelled = true; };
    }, [isOpen, anomalie?.idEmploye, anomalie?.datePointage]);

    // Source situation conges (avec repli immediat sur les compteurs attaches a l'anomalie)
    const formatJours = (val) => {
        if (val == null || val === '') return null;
        const num = Number(val);
        if (Number.isNaN(num)) return null;
        return Number.isInteger(num) ? String(num) : num.toFixed(2);
    };

    const rawSoldeRestant = leaveSituation?.soldeJours ?? anomalie?.soldeCongesRestant;
    const rawSoldeAcquis = leaveSituation?.totalAcquis ?? anomalie?.soldeCongesAcquis;
    const rawSoldePris = leaveSituation?.totalPris ?? anomalie?.soldeCongesPris;

    const soldeRestant = formatJours(rawSoldeRestant);
    const soldeAcquis = formatJours(rawSoldeAcquis);
    const soldePris = formatJours(rawSoldePris);

    const dernierJourConge = shiftIsoDate(dateReprise, -1);
    const nbJoursAImputer = congeDuree === '0.5' ? 0.5 :
        anomalie?.datePointage && dateReprise > anomalie.datePointage
            ? Math.round((Date.parse(`${dateReprise}T00:00:00Z`) - Date.parse(`${anomalie.datePointage}T00:00:00Z`)) / 86400000)
            : 0;

    useEffect(() => {
        if (!isOpen || !anomalie || activeTab !== 'HEURES') return undefined;
        let cancelled = false;
        setPreviewPending(true);
        const timer = setTimeout(async () => {
            try {
                const preview = await attendanceService.previewDay({
                    idEmploye: anomalie.idEmploye,
                    idSite: anomalie.idSite,
                    datePointage: anomalie.datePointage,
                    heureEntreeMatin: modePresence === 'APRES_MIDI_SEUL' ? null : heureEntreeMatin || null,
                    heureSortieMatin: modePresence === 'APRES_MIDI_SEUL' || modePresence === 'CONTINU' ? null : heureSortieMatin || null,
                    heureEntreeAm: modePresence === 'MATIN_SEUL' || modePresence === 'CONTINU' ? null : heureEntreeAm || null,
                    heureSortieAm: modePresence === 'MATIN_SEUL' ? null : heureSortieAm || null,
                    horaireContinu: modePresence === 'CONTINU',
                    codeStatut: modePresence === 'MATIN_SEUL' || modePresence === 'APRES_MIDI_SEUL' ? 'DEMI_JOURNEE' : 'PRESENT',
                    typeCongeJour: modePresence === 'MATIN_SEUL' ? 'APRES_MIDI' : modePresence === 'APRES_MIDI_SEUL' ? 'MATIN' : null
                });
                if (!cancelled) setDayPreview(preview);
            } catch (previewError) {
                if (!cancelled) setDayPreview({ hasValidationError: true, validationMessages: [getErrorMessage(previewError, 'Prévisualisation indisponible.')] });
            } finally {
                if (!cancelled) setPreviewPending(false);
            }
        }, 250);
        return () => { cancelled = true; clearTimeout(timer); };
    }, [isOpen, anomalie, activeTab, modePresence, heureEntreeMatin, heureSortieMatin, heureEntreeAm, heureSortieAm]);

    // Action de sauvegarde
    const handleSubmit = useCallback(async (e) => {
        if (e) e.preventDefault();
        if (saving || submissionInFlight.current) return;
        setError('');

        if (activeTab !== 'RETARD_REEL' && (!motifAjustement || motifAjustement.trim().length < 3)) {
            setError('Un motif explicatif est obligatoire pour régulariser un pointage (minimum 3 caractères).');
            return;
        }
        if (activeTab === 'ABSENCE' && optionAbsence === 'CONGE_PAYE' && nbJoursAImputer <= 0) {
            setError('La date de reprise doit être postérieure au premier jour de congé.');
            return;
        }
        submissionInFlight.current = true;
        setSaving(true);
        try {
            const payload = {
                version: anomalie.version,
                motifAjustement: activeTab === 'RETARD_REEL' ? 'Retard réel confirmé' : motifAjustement.trim(),
                typeRegularisation: activeTab,
                idEmploye: anomalie.idEmploye,
                idPeriodePaie: anomalie.idPeriodePaie,
                idSite: anomalie.idSite,
                datePointage: anomalie.datePointage,
                horaireContinuJour: activeTab === 'HEURES' && modePresence === 'CONTINU'
            };

            if (activeTab === 'RETARD_REEL') {
                // Les horaires et les minutes déjà enregistrés restent inchangés.
            } else if (activeTab === 'HEURES') {
                if (allowancesLoading || allowancesError) {
                    setError(allowancesError || 'Chargement des indemnités en cours.');
                    setSaving(false);
                    return;
                }
                if (previewPending || !dayPreview) {
                    setError('La prévisualisation des heures est en cours.');
                    setSaving(false);
                    return;
                }
                if (dayPreview.hasValidationError) {
                    setError(dayPreview.validationMessages?.join(' ') || 'Les horaires ne sont pas valides.');
                    setSaving(false);
                    return;
                }

                if (modePresence === 'MATIN_SEUL') {
                    payload.statutCode = 'DEMI_JOURNEE';
                    payload.heureEntreeMatin = heureEntreeMatin;
                    payload.heureSortieMatin = heureSortieMatin;
                    payload.heureEntreeAm = null;
                    payload.heureSortieAm = null;
                    payload.horaireContinuJour = false;
                    payload.optionAutreDemiJournee = optionAutreDemiJournee;
                } else if (modePresence === 'APRES_MIDI_SEUL') {
                    payload.statutCode = 'DEMI_JOURNEE';
                    payload.heureEntreeMatin = null;
                    payload.heureSortieMatin = null;
                    payload.heureEntreeAm = heureEntreeAm;
                    payload.heureSortieAm = heureSortieAm;
                    payload.horaireContinuJour = false;
                    payload.optionAutreDemiJournee = optionAutreDemiJournee;
                } else if (modePresence === 'CONTINU') {
                    payload.statutCode = 'PRESENT';
                    payload.heureEntreeMatin = heureEntreeMatin;
                    payload.heureSortieMatin = null;
                    payload.heureEntreeAm = null;
                    payload.heureSortieAm = heureSortieAm;
                    payload.horaireContinuJour = true;
                } else {
                    payload.statutCode = 'PRESENT';
                    payload.heureEntreeMatin = heureEntreeMatin;
                    payload.heureSortieMatin = heureSortieMatin;
                    payload.heureEntreeAm = heureEntreeAm;
                    payload.heureSortieAm = heureSortieAm;
                    payload.horaireContinuJour = false;
                }

                payload.dynamicIndemnites = dynamicAllowances;
            } else {
                // Régularisation d'absence
                payload.optionAbsence = optionAbsence;
                payload.dynamicIndemnites = absenceAllowances;
                if (optionAbsence === 'CONGE_PAYE') {
                    payload.statutCode = 'CONGE';
                    payload.nbJoursCongeDecompte = nbJoursAImputer;
                    payload.dateReprise = congeDuree === '0.5' ? shiftIsoDate(anomalie.datePointage, 1) : dateReprise;
                    payload.estDemiJournee = (congeDuree === '0.5');
                    payload.momentDemiJournee = demiJourneeMoment;
                } else if (optionAbsence === 'PERMISSION_EXCEPTIONNELLE') {
                    payload.statutCode = 'CONGE';
                    payload.motifAjustement = `[Permission : ${motifFamilial}] ${motifAjustement.trim()}`;
                } else {
                    // Absence déduite de la paie
                    payload.statutCode = statutAbsenceDeduite;
                }
            }

            if (onSuccess) {
                const result = await onSuccess(payload);
                if (result === true || (result && result.success)) {
                    onClose();
                } else if (result && (result.error || result.message)) {
                    setError(result.error || result.message);
                }
            }
        } catch (err) {
            console.error('Erreur régularisation pointage:', err);
            setError(err?.message || 'Erreur lors de la sauvegarde de la régularisation.');
        } finally {
            submissionInFlight.current = false;
            setSaving(false);
        }
    }, [saving, activeTab, motifAjustement, optionAbsence, nbJoursAImputer, anomalie,
        modePresence, heureEntreeMatin, heureSortieMatin, heureEntreeAm, heureSortieAm,
        allowancesLoading, allowancesError, previewPending, dayPreview, optionAutreDemiJournee,
        dynamicAllowances, absenceAllowances, congeDuree, dateReprise, demiJourneeMoment, motifFamilial,
        statutAbsenceDeduite, onSuccess, onClose]);

    useEffect(() => {
        if (!isOpen || saving) return undefined;
        const handleEnter = (event) => {
            if (event.key !== 'Enter' || event.repeat || event.isComposing ||
                event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
            const target = event.target;
            if (!(target instanceof Element) || !target.closest('[role="dialog"]') ||
                target.closest('textarea, select, [role="combobox"], [contenteditable="true"]')) return;
            const focusedButton = target.closest('button');
            if (focusedButton && focusedButton.dataset.regularisationSelected !== 'true') return;
            event.preventDefault();
            handleSubmit();
        };
        window.addEventListener('keydown', handleEnter);
        return () => window.removeEventListener('keydown', handleEnter);
    }, [isOpen, saving, handleSubmit]);

    const formatDayDate = (isoDate) => {
        if (!isoDate) return '—';
        const parts = String(isoDate).split('-');
        if (parts.length === 3) return `${parts[2]}/${parts[1]}/${parts[0]}`;
        return fmtDate(isoDate);
    };

    const getInitials = (nom, prenom) => {
        const n = (nom || '').trim();
        const p = (prenom || '').trim();
        if (n && p) return `${n[0]}${p[0]}`.toUpperCase();
        if (n) return n.slice(0, 2).toUpperCase();
        return 'AC';
    };

    const formatIndemnitesSummary = (selections, allowances) => {
        if (!selections || !allowances || allowances.length === 0) return 'Aucune';
        const parts = [];
        for (const a of allowances) {
            const s = selections[String(a.idIndemnite)];
            if (!s) continue;
            const fractionable = a.estFractionnable ?? a.fractionnable;
            const name = (a.libelle || a.code || '').replace(/^Indemnité de\s+/i, '').trim();
            if (fractionable) {
                const count = (s.matin ? 1 : 0) + (s.am ? 1 : 0);
                if (count === 2) {
                    parts.push(`${name} ×2`);
                } else if (count === 1) {
                    parts.push(s.matin ? `${name} (matin)` : `${name} (après-midi)`);
                }
            } else if (s.accordee) {
                parts.push(name);
            }
        }
        return parts.length > 0 ? parts.join(', ') : 'Aucune';
    };

    const getSoldeApresDisplay = (opt, rawSolde, duree, nbJours) => {
        if (rawSolde == null) return '—';
        const numSolde = Number(rawSolde);
        if (Number.isNaN(numSolde)) return '—';
        if (opt !== 'CONGE_PAYE') {
            return `${formatJours(numSolde)} j`;
        }
        const aDebiter = duree === '0.5' ? 0.5 : nbJours;
        const reste = Math.max(0, Math.round((numSolde - aDebiter) * 100) / 100);
        return `${formatJours(numSolde)} → ${formatJours(reste)} j`;
    };

    if (!isOpen || !anomalie) return null;

    const hasRetardTab = anomalie.typeAnomalie === 'RETARD' || Number(anomalie.minutesRetard) > 0;
    const labelDemiJournee = modePresence === 'MATIN_SEUL' ? "l'après-midi" : "la matinée";

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Régularisation du pointage"
            size="2xl"
            footer={
                <div className="flex items-center justify-between w-full">
                    <div className="text-xs text-[var(--color-fg-muted)]">
                        Traçabilité GRH &amp; conformité
                    </div>
                    <div className="flex items-center gap-2.5">
                        <Button 
                            variant="outline" 
                            onClick={onClose}
                            disabled={saving}
                            className="rounded-none font-bold text-xs uppercase px-4 py-2"
                        >
                            ANNULER
                        </Button>
                        <Button 
                            variant="primary" 
                            onClick={handleSubmit}
                            loading={saving}
                            className="rounded-none font-bold text-xs uppercase px-5 py-2"
                        >
                            {activeTab === 'RETARD_REEL' ? 'CONFIRMER LE RETARD RÉEL' : 'ENREGISTRER'}
                        </Button>
                    </div>
                </div>
            }
        >
            <div className="space-y-4">
                {/* Bandeau d'en-tête collaborateur & contexte */}
                <div className="p-3 border border-[var(--color-border)] bg-[var(--color-surface)] flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-[var(--color-brand-600)] text-white font-bold text-sm flex items-center justify-center shrink-0">
                            {getInitials(anomalie.nom, anomalie.prenom)}
                        </div>
                        <div>
                            <div className="font-bold text-sm text-[var(--color-fg)] uppercase tracking-wide">
                                {anomalie.nom} {anomalie.prenom}
                            </div>
                            <div className="text-xs text-[var(--color-fg-muted)]">
                                {anomalie.matricule} · {anomalie.siteNom || 'Siège'}
                            </div>
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        <div className="border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 py-1 text-xs font-data text-[var(--color-fg)]">
                            {formatDayDate(anomalie.datePointage)}
                        </div>
                        <div className="border border-[var(--color-border)] bg-[var(--color-surface)] px-2.5 py-1 text-xs font-data text-[var(--color-fg)]">
                            {anomalie.semaineCode || 'S3'}
                        </div>
                        <div className="bg-[#ffedd5] text-[#c2410c] px-2.5 py-1 text-xs font-bold uppercase tracking-wider">
                            {anomalie.typeAnomalie === 'RETARD' ? 'RETARD' : (anomalie.statutCode || anomalie.typeAnomalie || anomalie.statutLibelle || 'INCOMPLET')}
                        </div>
                    </div>
                </div>

                {error && (
                    <FormError title="Action requise" className="rounded-none">
                        {error}
                    </FormError>
                )}

                {/* Onglets de mode */}
                <div className="flex border-b border-[var(--color-border-subtle)] gap-8">
                    {hasRetardTab && (
                        <button
                            type="button"
                            onClick={() => setActiveTab('RETARD_REEL')}
                            className={`pb-2.5 text-xs font-bold transition-all border-b-2 cursor-pointer ${
                                activeTab === 'RETARD_REEL'
                                    ? 'text-[var(--color-brand-600)] border-[var(--color-brand-600)]'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] border-transparent'
                            }`}
                        >
                            Confirmer
                        </button>
                    )}
                    <button
                        type="button"
                        onClick={() => setActiveTab('HEURES')}
                        className={`pb-2.5 text-xs font-bold transition-all border-b-2 cursor-pointer ${
                            activeTab === 'HEURES'
                                ? 'text-[var(--color-brand-600)] border-[var(--color-brand-600)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] border-transparent'
                        }`}
                    >
                        Présence réelle
                    </button>
                    <button
                        type="button"
                        onClick={() => setActiveTab('ABSENCE')}
                        className={`pb-2.5 text-xs font-bold transition-all border-b-2 cursor-pointer ${
                            activeTab === 'ABSENCE'
                                ? 'text-[var(--color-brand-600)] border-[var(--color-brand-600)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] border-transparent'
                        }`}
                    >
                        Absence
                    </button>
                </div>

                {/* CONTENU ONGLET RETARD REEL */}
                {activeTab === 'RETARD_REEL' && (
                    <div className="space-y-4">
                        <div className="p-4 border border-[var(--color-border)] bg-[var(--color-surface)] space-y-1">
                            <p className="font-bold text-xs text-[var(--color-fg)]">
                                Les horaires saisis sont exacts et le retard de {anomalie.minutesRetard || 5} min est réel.
                            </p>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Confirmer marque ce pointage « Régularisé ». Les horaires et les minutes de retard restent inchangés.
                            </p>
                        </div>

                        {/* Récapitulatif Tab Retard */}
                        <div className="border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] grid grid-cols-3 divide-x divide-[var(--color-brand-200)]">
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">STATUT</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">Régularisé</div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">RETARD</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">{anomalie.minutesRetard || 5} min, inchangé</div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">HORAIRES</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">Inchangés</div>
                            </div>
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 1 : PRÉSENCE RÉELLE */}
                {activeTab === 'HEURES' && (
                    <div className="space-y-4">
                        {/* Mode d'activité de la journée */}
                        <div>
                            <label className="block text-xs font-bold text-[var(--color-fg)] mb-2">
                                Mode d&apos;activité de la journée
                            </label>
                            <div className="inline-flex border border-[var(--color-border)] divide-x divide-[var(--color-border)]">
                                {[
                                    { key: 'JOURNEE', label: 'Standard' },
                                    { key: 'MATIN_SEUL', label: 'Matinée seule' },
                                    { key: 'APRES_MIDI_SEUL', label: 'Après-midi seul' },
                                    { key: 'CONTINU', label: 'Continue' }
                                ].map((item) => (
                                    <button
                                        key={item.key}
                                        type="button"
                                        onClick={() => handleModeChange(item.key)}
                                        className={`px-4 py-2 text-xs font-semibold transition-colors cursor-pointer ${
                                            modePresence === item.key
                                                ? 'bg-[var(--color-brand-600)] text-white'
                                                : 'bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                                        }`}
                                    >
                                        {item.label}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Formulaires d'horaires selon le mode choisi */}
                        {modePresence === 'CONTINU' ? (
                            <div className="p-3 border border-[var(--color-border)] bg-[var(--color-surface)] grid grid-cols-2 gap-3">
                                <div className="space-y-1">
                                    <label className="text-xs text-[var(--color-fg-muted)]">Prise de poste</label>
                                    <input
                                        type="time"
                                        value={heureEntreeMatin}
                                        onChange={(e) => setHeureEntreeMatin(e.target.value)}
                                        className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs text-[var(--color-fg-muted)]">Fin de poste</label>
                                    <input
                                        type="time"
                                        value={heureSortieAm}
                                        onChange={(e) => setHeureSortieAm(e.target.value)}
                                        className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                    />
                                </div>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 gap-4">
                                {/* Bloc Matinée */}
                                {modePresence === 'APRES_MIDI_SEUL' ? (
                                    <div className="border border-dashed border-[var(--color-border)] bg-[var(--color-surface)] p-3 flex flex-col items-center justify-center min-h-[72px]">
                                        <div className="font-bold text-xs text-[var(--color-fg)]">Matinée</div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">Non travaillée</div>
                                    </div>
                                ) : (
                                    <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-3 grid grid-cols-2 gap-3">
                                        <div className="space-y-1">
                                            <label className="text-xs text-[var(--color-fg-muted)]">Entrée matin</label>
                                            <input
                                                type="time"
                                                value={heureEntreeMatin}
                                                onChange={(e) => setHeureEntreeMatin(e.target.value)}
                                                className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-[var(--color-fg-muted)]">Sortie pause</label>
                                            <input
                                                type="time"
                                                value={heureSortieMatin}
                                                onChange={(e) => setHeureSortieMatin(e.target.value)}
                                                className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                            />
                                        </div>
                                    </div>
                                )}

                                {/* Bloc Après-midi */}
                                {modePresence === 'MATIN_SEUL' ? (
                                    <div className="border border-dashed border-[var(--color-border)] bg-[var(--color-surface)] p-3 flex flex-col items-center justify-center min-h-[72px]">
                                        <div className="font-bold text-xs text-[var(--color-fg)]">Après-midi</div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">Non travaillée</div>
                                    </div>
                                ) : (
                                    <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-3 grid grid-cols-2 gap-3">
                                        <div className="space-y-1">
                                            <label className="text-xs text-[var(--color-fg-muted)]">Retour pause</label>
                                            <input
                                                type="time"
                                                value={heureEntreeAm}
                                                onChange={(e) => setHeureEntreeAm(e.target.value)}
                                                className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                            />
                                        </div>
                                        <div className="space-y-1">
                                            <label className="text-xs text-[var(--color-fg-muted)]">Sortie soir</label>
                                            <input
                                                type="time"
                                                value={heureSortieAm}
                                                onChange={(e) => setHeureSortieAm(e.target.value)}
                                                className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-1.5 focus:border-[var(--color-brand-500)] focus:outline-none"
                                            />
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}

                        {/* Traitement de l'autre demi-journée */}
                        {(modePresence === 'MATIN_SEUL' || modePresence === 'APRES_MIDI_SEUL') && (
                            <div>
                                <label className="block text-xs font-bold text-[var(--color-fg)] mb-2">
                                    Traitement de {labelDemiJournee}
                                </label>
                                <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                                    {[
                                        {
                                            value: 'AUCUNE',
                                            label: 'Départ autorisé',
                                            description: 'Payé au réel · congé intact'
                                        },
                                        {
                                            value: 'CONGE_PAYE',
                                            label: 'Congé payé (0,5 j)',
                                            description: 'Impact sur le solde de congés'
                                        },
                                        {
                                            value: 'ABSENCE_DEDUITE',
                                            label: 'Absence déduite',
                                            description: 'Retenue horaire sur salaire'
                                        }
                                    ].map((opt) => {
                                        const isSelected = optionAutreDemiJournee === opt.value;
                                        return (
                                            <div
                                                key={opt.value}
                                                onClick={() => setOptionAutreDemiJournee(opt.value)}
                                                className={`p-3 border cursor-pointer transition-all rounded-none ${
                                                    isSelected
                                                        ? 'border-2 border-[var(--color-brand-600)] bg-[var(--color-brand-50)]'
                                                        : 'border border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border-strong)]'
                                                }`}
                                            >
                                                <div className="flex items-center gap-2">
                                                    <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0 ${
                                                        isSelected ? 'border-[var(--color-brand-600)]' : 'border-[var(--color-border-strong)]'
                                                    }`}>
                                                        {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-600)]" />}
                                                    </div>
                                                    <span className="font-bold text-xs text-[var(--color-fg)]">{opt.label}</span>
                                                </div>
                                                <div className="text-[11px] text-[var(--color-fg-muted)] ml-5.5 mt-1 leading-snug">
                                                    {opt.description}
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        )}

                        {/* Indemnités associées */}
                        <div className="space-y-1.5">
                            <label className="block text-xs font-bold text-[var(--color-fg)]">
                                Indemnités
                            </label>
                            {allowancesLoading && <p className="text-xs text-[var(--color-fg-muted)]">Chargement des indemnités…</p>}
                            {allowancesError && <p className="text-xs text-[var(--color-danger-700)]">{allowancesError}</p>}
                            {!allowancesLoading && !allowancesError && (
                                <PointageAllowanceSelector
                                    allowances={availableAllowances}
                                    selections={dynamicAllowances}
                                    onChange={setDynamicAllowances}
                                />
                            )}
                        </div>

                        {/* Motif */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                                <label className="text-xs font-bold text-[var(--color-fg)]">
                                    Motif <span className="text-[var(--color-danger-500)]">*</span>
                                </label>
                                <span className="text-[11px] text-[var(--color-fg-muted)]">3 caractères minimum</span>
                            </div>
                            <textarea
                                value={motifAjustement}
                                onChange={(e) => {
                                    if (error) setError('');
                                    setMotifAjustement(e.target.value);
                                }}
                                rows={2}
                                placeholder="Ex : oubli de badgeage sortie, permission accordée..."
                                className="w-full text-xs p-2.5 rounded-none border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)] placeholder:text-[var(--color-fg-muted)]"
                            />
                        </div>

                        {/* Récapitulatif Tab Présence réelle */}
                        <div className="border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] grid grid-cols-4 divide-x divide-[var(--color-brand-200)]">
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">HEURES</div>
                                <div className="text-sm font-bold font-data text-[var(--color-fg)] mt-0.5">
                                    {previewPending ? 'Calcul…' : dayPreview?.totalHeures != null ? `${String(dayPreview.totalHeures).replace('.', ',')} h` : '—'}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">PAIE</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {modePresence === 'MATIN_SEUL' || modePresence === 'APRES_MIDI_SEUL'
                                        ? (optionAutreDemiJournee === 'ABSENCE_DEDUITE' ? 'Retenue horaire' : 'Payé au réel')
                                        : 'Payé au réel'}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">CONGÉS</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {optionAutreDemiJournee === 'CONGE_PAYE' ? '0,5 j décompté' : 'Intacts'}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">INDEMNITÉS</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {formatIndemnitesSummary(dynamicAllowances, availableAllowances)}
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 2 : ABSENCE */}
                {activeTab === 'ABSENCE' && (
                    <div className="space-y-4">
                        {/* Solde de congés actuel */}
                        <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-3.5 flex items-center justify-between">
                            <div>
                                <div className="text-xs font-bold text-[var(--color-fg)]">Solde de congés payés</div>
                                <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                    {leaveSituationLoading && soldeAcquis == null ? 'Chargement…' : `Acquis ${soldeAcquis ?? '—'} j · Consommé ${soldePris ?? '—'} j`}
                                </div>
                            </div>
                            <div className="text-right">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">DISPONIBLE NET</div>
                                <div className="text-2xl font-bold font-data text-[var(--color-brand-800)] mt-0.5">
                                    {soldeRestant != null ? `${soldeRestant} j` : '—'}
                                </div>
                            </div>
                        </div>

                        {/* Modalité de traitement */}
                        <div>
                            <label className="block text-xs font-bold text-[var(--color-fg)] mb-2">
                                Modalité de traitement
                            </label>
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                                {[
                                    {
                                        value: 'CONGE_PAYE',
                                        label: 'Congé payé annuel',
                                        description: 'Imputé sur le solde de congés'
                                    },
                                    {
                                        value: 'PERMISSION_EXCEPTIONNELLE',
                                        label: 'Permission',
                                        description: 'Événement familial, 10 j/an · salaire maintenu'
                                    },
                                    {
                                        value: 'ABSENCE_DEDUITE',
                                        label: 'Absence déduite',
                                        description: 'Retenue automatique sur salaire'
                                    }
                                ].map((opt) => {
                                    const isSelected = optionAbsence === opt.value;
                                    return (
                                        <div
                                            key={opt.value}
                                            onClick={() => setOptionAbsence(opt.value)}
                                            className={`p-3 border cursor-pointer transition-all rounded-none ${
                                                isSelected
                                                    ? 'border-2 border-[var(--color-brand-600)] bg-[var(--color-brand-50)]'
                                                    : 'border border-[var(--color-border)] bg-[var(--color-surface)] hover:border-[var(--color-border-strong)]'
                                            }`}
                                        >
                                            <div className="flex items-center gap-2">
                                                <div className={`w-3.5 h-3.5 rounded-full border flex items-center justify-center shrink-0 ${
                                                    isSelected ? 'border-[var(--color-brand-600)]' : 'border-[var(--color-border-strong)]'
                                                }`}>
                                                    {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-[var(--color-brand-600)]" />}
                                                </div>
                                                <span className="font-bold text-xs text-[var(--color-fg)]">{opt.label}</span>
                                            </div>
                                            <div className="text-[11px] text-[var(--color-fg-muted)] ml-5.5 mt-1 leading-snug">
                                                {opt.description}
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>

                        {/* Volume à imputer pour Congé payé */}
                        {optionAbsence === 'CONGE_PAYE' && (
                            <div className="space-y-2">
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                                    <div>
                                        <label className="text-xs font-bold text-[var(--color-fg)] mb-1.5 block">
                                            Volume à imputer
                                        </label>
                                        <div className="inline-flex border border-[var(--color-border)] divide-x divide-[var(--color-border)] w-full">
                                            <button
                                                type="button"
                                                onClick={() => setCongeDuree('1.0')}
                                                className={`flex-1 py-2 text-xs font-semibold text-center transition-colors cursor-pointer ${
                                                    congeDuree === '1.0'
                                                        ? 'bg-[var(--color-brand-600)] text-white'
                                                        : 'bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                                                }`}
                                            >
                                                Journée(s) entière(s)
                                            </button>
                                            <button
                                                type="button"
                                                onClick={() => setCongeDuree('0.5')}
                                                className={`flex-1 py-2 text-xs font-semibold text-center transition-colors cursor-pointer ${
                                                    congeDuree === '0.5'
                                                        ? 'bg-[var(--color-brand-600)] text-white'
                                                        : 'bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                                                }`}
                                            >
                                                Demi-journée (0,5 j)
                                            </button>
                                        </div>
                                    </div>

                                    {congeDuree !== '0.5' ? (
                                        <div>
                                            <label htmlFor="regularisation-date-reprise" className="text-xs font-bold text-[var(--color-fg-muted)] mb-1.5 block">
                                                Date de reprise prévue
                                            </label>
                                            <input
                                                id="regularisation-date-reprise"
                                                type="date"
                                                value={dateReprise}
                                                min={shiftIsoDate(anomalie?.datePointage, 1) || undefined}
                                                onChange={(e) => setDateReprise(e.target.value)}
                                                className="w-full border border-[var(--color-border)] bg-[var(--color-surface)] text-xs p-2 focus:border-[var(--color-brand-500)] focus:outline-none"
                                                required
                                            />
                                        </div>
                                    ) : (
                                        <div>
                                            <label className="text-xs font-bold text-[var(--color-fg-muted)] mb-1.5 block">
                                                Moment
                                            </label>
                                            <div className="flex items-center gap-4 text-xs pt-2">
                                                <label className="flex items-center gap-1.5 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="moment"
                                                        checked={demiJourneeMoment === 'MATIN'}
                                                        onChange={() => setDemiJourneeMoment('MATIN')}
                                                        className="w-3.5 h-3.5 accent-[var(--color-brand-600)]"
                                                    />
                                                    <span className="text-[var(--color-fg)]">Matinée</span>
                                                </label>
                                                <label className="flex items-center gap-1.5 cursor-pointer">
                                                    <input
                                                        type="radio"
                                                        name="moment"
                                                        checked={demiJourneeMoment === 'APRES_MIDI'}
                                                        onChange={() => setDemiJourneeMoment('APRES_MIDI')}
                                                        className="w-3.5 h-3.5 accent-[var(--color-brand-600)]"
                                                    />
                                                    <span className="text-[var(--color-fg)]">Après-midi</span>
                                                </label>
                                            </div>
                                        </div>
                                    )}
                                </div>

                                {congeDuree !== '0.5' && nbJoursAImputer > 0 && (
                                    <p className="text-[11px] text-[var(--color-fg-muted)]">
                                        Du {formatDayDate(anomalie.datePointage)} au {formatDayDate(dernierJourConge)} inclus :{' '}
                                        <strong className="text-[var(--color-fg)]">
                                            {nbJoursAImputer} jour{nbJoursAImputer > 1 ? 's' : ''} calendaire{nbJoursAImputer > 1 ? 's' : ''} débité{nbJoursAImputer > 1 ? 's' : ''}
                                        </strong>
                                        . La reprise n&apos;est pas débitée.
                                    </p>
                                )}
                            </div>
                        )}

                        {/* Permission légale */}
                        {optionAbsence === 'PERMISSION_EXCEPTIONNELLE' && (
                            <div className="p-3 border border-[var(--color-border)] bg-[var(--color-surface)] space-y-2">
                                <label className="block text-xs font-bold text-[var(--color-fg)]">
                                    Motif légal de l&apos;événement familial
                                </label>
                                <div className="relative">
                                    <select
                                        value={motifFamilial}
                                        onChange={(e) => setMotifFamilial(e.target.value)}
                                        className="w-full appearance-none text-xs py-2 pl-3 pr-10 border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)]"
                                    >
                                        {MOTIFS_FAMILIAUX.map((m) => (
                                            <option key={m.value} value={m.value}>
                                                {m.label}
                                            </option>
                                        ))}
                                    </select>
                                    <ChevronDown aria-hidden="true" className="pointer-events-none absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                                </div>
                            </div>
                        )}

                        {/* Qualification Absence Déduite */}
                        {optionAbsence === 'ABSENCE_DEDUITE' && (
                            <div className="p-3 border border-[var(--color-border)] bg-[var(--color-surface)] space-y-2">
                                <label className="block text-xs font-bold text-[var(--color-fg)]">
                                    Qualification de l&apos;absence
                                </label>
                                <div className="grid grid-cols-2 gap-2">
                                    <label className="flex items-center gap-2 p-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-xs cursor-pointer">
                                        <input
                                            type="radio"
                                            name="statutDeduite"
                                            value="ABSENCE_JUSTIFIEE"
                                            checked={statutAbsenceDeduite === 'ABSENCE_JUSTIFIEE'}
                                            onChange={() => setStatutAbsenceDeduite('ABSENCE_JUSTIFIEE')}
                                            className="w-3.5 h-3.5 accent-[var(--color-brand-600)]"
                                        />
                                        <span>Absence Justifiée</span>
                                    </label>
                                    <label className="flex items-center gap-2 p-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-xs cursor-pointer">
                                        <input
                                            type="radio"
                                            name="statutDeduite"
                                            value="ABSENCE_NON_JUSTIFIEE"
                                            checked={statutAbsenceDeduite === 'ABSENCE_NON_JUSTIFIEE'}
                                            onChange={() => setStatutAbsenceDeduite('ABSENCE_NON_JUSTIFIEE')}
                                            className="w-3.5 h-3.5 accent-[var(--color-brand-600)]"
                                        />
                                        <span>Absence Non Justifiée</span>
                                    </label>
                                </div>
                            </div>
                        )}

                        {/* Indemnités pendant l'absence */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                                <label className="text-xs font-bold text-[var(--color-fg)]">
                                    Indemnités pendant l&apos;absence
                                </label>
                                <span className="text-[10px] font-semibold bg-[#ffedd5] text-[#9a3412] px-2 py-0.5">
                                    Exception à justifier
                                </span>
                            </div>
                            <PointageAllowanceSelector
                                allowances={availableAllowances}
                                selections={absenceAllowances}
                                onChange={setAbsenceAllowances}
                                absence
                            />
                        </div>

                        {/* Motif */}
                        <div className="space-y-1.5">
                            <div className="flex items-center justify-between">
                                <label className="text-xs font-bold text-[var(--color-fg)]">
                                    Motif <span className="text-[var(--color-danger-500)]">*</span>
                                </label>
                                <span className="text-[11px] text-[var(--color-fg-muted)]">3 caractères minimum</span>
                            </div>
                            <textarea
                                value={motifAjustement}
                                onChange={(e) => {
                                    if (error) setError('');
                                    setMotifAjustement(e.target.value);
                                }}
                                rows={2}
                                placeholder="Ex : justificatif médical reçu, permission accordée..."
                                className="w-full text-xs p-2.5 rounded-none border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:ring-1 focus:ring-[var(--color-brand-500)] placeholder:text-[var(--color-fg-muted)]"
                            />
                        </div>

                        {/* Récapitulatif Tab Absence */}
                        <div className="border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] grid grid-cols-4 divide-x divide-[var(--color-brand-200)]">
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">JOURS DÉBITÉS</div>
                                <div className="text-sm font-bold font-data text-[var(--color-fg)] mt-0.5">
                                    {congeDuree === '0.5' ? '0,5 j' : `${nbJoursAImputer} j`}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">SOLDE APRÈS</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {getSoldeApresDisplay(optionAbsence, rawSoldeRestant, congeDuree, nbJoursAImputer)}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">PAIE</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {optionAbsence === 'ABSENCE_DEDUITE' ? 'Déduite' : 'Maintenue'}
                                </div>
                            </div>
                            <div className="p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-700)]">INDEMNITÉS</div>
                                <div className="text-xs font-bold text-[var(--color-fg)] mt-0.5">
                                    {formatIndemnitesSummary(absenceAllowances, availableAllowances)}
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </Modal>
    );
}
