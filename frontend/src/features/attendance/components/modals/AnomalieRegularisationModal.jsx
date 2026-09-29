import FormError from '@/components/ui/feedback/FormError';
import { useState, useEffect, useRef, useCallback } from 'react';
import { getErrorMessage } from '@/api/apiClient';
import { 
    Clock, 
    Palmtree, 
    CheckCircle2, 
    FileText, 
    Shield, 
    Info, 
    HeartHandshake,
    Wallet,
    Sun,
    Sunset
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { fmtDate, shiftIsoDate } from '@/utils/dateUtils';
import { attendanceService } from '@/features/attendance/api/attendanceApi';

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
    onSuccess
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
    const [allowancesLoading, setAllowancesLoading] = useState(false);
    const [allowancesError, setAllowancesError] = useState('');

    // Formulaire Mode 1 : Horaires
    const [modePresence, setModePresence] = useState('JOURNEE'); // 'JOURNEE', 'MATIN_SEUL', 'APRES_MIDI_SEUL', 'CONTINU'
    const [optionAutreDemiJournee, setOptionAutreDemiJournee] = useState('AUCUNE'); // 'AUCUNE', 'CONGE_PAYE', 'ABSENCE_DEDUITE'
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
        
        const isAbsence = anomalie.typeAnomalie === 'ABSENCE' || anomalie.statutCode === 'ABSENCE_NON_JUSTIFIEE' || anomalie.statutCode === 'ABSENCE_JUSTIFIEE';
        setActiveTab(isAbsence ? 'ABSENCE' : 'HEURES');

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

        setOptionAutreDemiJournee('AUCUNE');

        setOptionAbsence('CONGE_PAYE');
        setCongeDuree('1.0');
        setDateReprise(shiftIsoDate(anomalie.datePointage, 1));
        setDemiJourneeMoment('MATIN');
        setMotifFamilial(MOTIFS_FAMILIAUX[0].value);
        setStatutAbsenceDeduite('ABSENCE_JUSTIFIEE');

        setMotifAjustement(anomalie.motifAjustement || '');
    }, [anomalie, isOpen]);

    useEffect(() => {
        if (!isOpen || !anomalie?.idSite || Number(anomalie.idSite) <= 0 || !anomalie?.datePointage) return undefined;
        let cancelled = false;
        setAllowancesLoading(true);
        setAllowancesError('');
        attendanceService.getGrilleSaisie({ idSite: anomalie.idSite, date: anomalie.datePointage })
            .then((grid) => {
                if (cancelled) return;
                const row = grid?.grilleData?.find((item) => Number(item.idEmploye) === Number(anomalie.idEmploye));
                if (!row) throw new Error('La journée du salarié est introuvable dans la grille.');
                const definitions = grid.siteIndemnites || [];
                const selections = {};
                definitions.forEach((allowance) => {
                    const current = row.dynamicIndemnites?.[String(allowance.idIndemnite)] || {};
                    selections[String(allowance.idIndemnite)] = {
                        accordee: Boolean(current.accordee),
                        matin: Boolean(current.matin),
                        am: Boolean(current.am)
                    };
                });
                setAvailableAllowances(definitions);
                setDynamicAllowances(selections);
            })
            .catch((cause) => {
                if (!cancelled) setAllowancesError(cause?.message || 'Indemnités du site indisponibles.');
            })
            .finally(() => {
                if (!cancelled) setAllowancesLoading(false);
            });
        return () => { cancelled = true; };
    }, [isOpen, anomalie?.idSite, anomalie?.datePointage, anomalie?.idEmploye]);

    // Les soldes sont informatifs ; CongeService valide l'éligibilité au moment de la régularisation.
    const soldeRestant = anomalie?.soldeCongesRestant ?? 0;
    const soldeAcquis = anomalie?.soldeCongesAcquis ?? 0;
    const soldePris = anomalie?.soldeCongesPris ?? 0;

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
                payload.dynamicIndemnites = dynamicAllowances;
                if (optionAbsence === 'CONGE_PAYE') {
                    payload.statutCode = 'CONGE';
                    payload.nbJoursCongeDecompte = nbJoursAImputer;
                    payload.dateReprise = congeDuree === '0.5' ? shiftIsoDate(anomalie.datePointage, 1) : dateReprise;
                    payload.estDemiJournee = (congeDuree === '0.5');
                    payload.momentDemiJournee = demiJourneeMoment;
                } else if (optionAbsence === 'PERMISSION_EXCEPTIONNELLE') {
                    payload.statutCode = 'CONGE';
                    payload.motifAjustement = `[Permission Art. 88 : ${motifFamilial}] ${motifAjustement.trim()}`;
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
        dynamicAllowances, congeDuree, dateReprise, demiJourneeMoment, motifFamilial,
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

    if (!isOpen || !anomalie) return null;

    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title="Régularisation du pointage"
            size="2xl"
            footer={
                <div className="flex items-center justify-between w-full">
                    <div className="text-xs text-[var(--color-fg-muted)] flex items-center gap-1.5">
                        <Shield className="w-4 h-4 text-[var(--color-fg-muted)]" />
                        <span>Traçabilité SIRH &amp; conformité</span>
                    </div>
                    <div className="flex items-center gap-2">
                        <Button 
                            variant="outline" 
                            onClick={onClose}
                            disabled={saving}
                            className="rounded-none"
                        >
                            Annuler
                        </Button>
                        <Button 
                            variant="primary" 
                            onClick={handleSubmit}
                            loading={saving}
                            className="rounded-none"
                        >
                            {activeTab === 'RETARD_REEL' ? 'Confirmer le retard réel' : 'Enregistrer la régularisation'}
                            <span className="ml-2 text-[10px] font-normal opacity-70">Entrée ↵</span>
                        </Button>
                    </div>
                </div>
            }
        >
            <div className="space-y-5">
                {/* Bandeau d'en-tête collaborateur & contexte */}
                <div className="p-3.5 bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] rounded-none flex items-center justify-between gap-4">
                    <div className="flex items-center gap-3">
                        <Avatar
                            src={anomalie.photoUrl}
                            name={`${anomalie.nom || ''} ${anomalie.prenom || ''}`}
                            size="md"
                            className="rounded-none"
                        />
                        <div>
                            <div className="font-semibold text-sm text-[var(--color-fg)]">
                                {anomalie.nom} {anomalie.prenom}
                            </div>
                            <div className="text-xs text-[var(--color-fg-muted)] font-mono">
                                {anomalie.matricule} • {anomalie.siteNom || 'Site TNR'}
                            </div>
                        </div>
                    </div>
                    <div className="text-right">
                        <div className="text-xs font-semibold text-[var(--color-fg)]">
                            {fmtDate(anomalie.datePointage)}
                        </div>
                        <div className="flex items-center gap-1.5 justify-end mt-1">
                            <span className="text-[11px] font-mono font-medium px-1.5 py-0.5 rounded-none bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                {anomalie.semaineCode || 'S1'}
                            </span>
                            <Badge 
                                variant={
                                    anomalie.severite === 'CRITIQUE' ? 'error' :
                                    anomalie.severite === 'HAUTE' ? 'warning' :
                                    anomalie.severite === 'MOYENNE' ? 'info' : 'neutral'
                                }
                                size="sm"
                                className="rounded-none font-bold"
                            >
                                {anomalie.typeAnomalie || anomalie.statutLibelle}
                            </Badge>
                        </div>
                    </div>
                </div>

                {error && (
                    <FormError title="Action requise" className="rounded-none">
                        {error}
                    </FormError>
                )}

                {/* Onglets de mode */}
                <div className="flex border-b border-[var(--color-border-subtle)]">
                    {anomalie.typeAnomalie === 'RETARD' && (
                        <button
                            type="button"
                            data-regularisation-selected={activeTab === 'RETARD_REEL'}
                            onClick={() => setActiveTab('RETARD_REEL')}
                            className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors rounded-none ${activeTab === 'RETARD_REEL'
                                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}
                        >
                            <CheckCircle2 className="w-3.5 h-3.5" />
                            <span>Confirmer le pointage réel</span>
                        </button>
                    )}
                    <button
                        type="button"
                        data-regularisation-selected={activeTab === 'HEURES'}
                        onClick={() => setActiveTab('HEURES')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors rounded-none ${
                            activeTab === 'HEURES'
                                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Clock className="w-3.5 h-3.5" />
                        <span>Saisie / Oubli de pointage (Présence réelle)</span>
                    </button>
                    <button
                        type="button"
                        data-regularisation-selected={activeTab === 'ABSENCE'}
                        onClick={() => setActiveTab('ABSENCE')}
                        className={`flex items-center gap-2 px-4 py-2.5 text-xs font-semibold border-b-2 transition-colors rounded-none ${
                            activeTab === 'ABSENCE'
                                ? 'border-[var(--color-primary)] text-[var(--color-primary)]'
                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                        }`}
                    >
                        <Palmtree className="w-3.5 h-3.5" />
                        <span>Régularisation d&apos;Absence (Congé / Déduction)</span>
                    </button>
                </div>

                {activeTab === 'RETARD_REEL' && (
                    <div className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-xs text-[var(--color-fg)] space-y-2">
                        <p className="font-semibold">Les horaires saisis sont exacts et le retard de {anomalie.minutesRetard} min est réel.</p>
                        <p>Confirmer marque ce pointage « Régularisé ». Les horaires et les minutes de retard restent inchangés.</p>
                    </div>
                )}

                {/* CONTENU ONGLET 1 : HEURES RÉELLES */}
                {activeTab === 'HEURES' && (
                    <div className="space-y-4">
                        {/* Sélecteur de mode de présence */}
                        <div>
                            <label className="block text-xs font-semibold text-[var(--color-fg)] mb-2">
                                Mode d&apos;activité de la journée :
                            </label>
                            <div className="grid grid-cols-4 gap-2">
                                <button
                                    type="button"
                                    data-regularisation-selected={modePresence === 'JOURNEE'}
                                    onClick={() => handleModeChange('JOURNEE')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'JOURNEE'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Clock className="w-3.5 h-3.5" />
                                        <span>Standard</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Matin + Après-midi
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    data-regularisation-selected={modePresence === 'MATIN_SEUL'}
                                    onClick={() => handleModeChange('MATIN_SEUL')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'MATIN_SEUL'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Sun className="w-3.5 h-3.5 text-[var(--color-warning-500)]" />
                                        <span>Matinée seule</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Demi-journée (matin)
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    data-regularisation-selected={modePresence === 'APRES_MIDI_SEUL'}
                                    onClick={() => handleModeChange('APRES_MIDI_SEUL')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'APRES_MIDI_SEUL'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Sunset className="w-3.5 h-3.5 text-[var(--color-orange-500)]" />
                                        <span>Après-midi seul</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Demi-journée (AM)
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    data-regularisation-selected={modePresence === 'CONTINU'}
                                    onClick={() => handleModeChange('CONTINU')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'CONTINU'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Clock className="w-3.5 h-3.5 text-[var(--color-info-500)]" />
                                        <span>Continue</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Sans pause dépointée
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Formulaires d'horaires selon le mode choisi */}
                        {modePresence === 'CONTINU' ? (
                            <div className="p-3 bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-3">
                                <div className="grid grid-cols-2 gap-3">
                                    <Input
                                        label="Heure de Prise de Poste"
                                        type="time"
                                        value={heureEntreeMatin}
                                        onChange={(e) => setHeureEntreeMatin(e.target.value)}
                                        className="rounded-none"
                                    />
                                    <Input
                                        label="Heure de Fin de Poste"
                                        type="time"
                                        value={heureSortieAm}
                                        onChange={(e) => setHeureSortieAm(e.target.value)}
                                        className="rounded-none"
                                    />
                                </div>
                            </div>
                        ) : (
                            <div className="grid grid-cols-2 gap-4">
                                {/* Bloc Matinée */}
                                <div className={`p-3 rounded-none space-y-3 border ${
                                    modePresence === 'APRES_MIDI_SEUL'
                                        ? 'bg-[var(--color-surface)] border-dashed border-[var(--color-border)] opacity-60'
                                        : 'bg-[var(--color-surface-subtle)] border-[var(--color-border-subtle)]'
                                }`}>
                                    <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center justify-between">
                                        <div className="flex items-center gap-1.5">
                                            <Sun className="w-3.5 h-3.5 text-[var(--color-warning-500)]" />
                                            <span>Matinée</span>
                                        </div>
                                        {modePresence === 'APRES_MIDI_SEUL' && (
                                            <span className="text-[10px] font-mono text-[var(--color-fg-muted)] bg-[var(--color-surface-subtle)] px-1.5 py-0.5 rounded-none">
                                                Non travaillée
                                            </span>
                                        )}
                                    </div>

                                    {modePresence !== 'APRES_MIDI_SEUL' ? (
                                        <div className="grid grid-cols-2 gap-2">
                                            <Input
                                                label="Entrée Matin"
                                                type="time"
                                                value={heureEntreeMatin}
                                                onChange={(e) => setHeureEntreeMatin(e.target.value)}
                                                className="rounded-none"
                                            />
                                            <Input
                                                label="Sortie Pause"
                                                type="time"
                                                value={heureSortieMatin}
                                                onChange={(e) => setHeureSortieMatin(e.target.value)}
                                                className="rounded-none"
                                            />
                                        </div>
                                    ) : (
                                        <div className="text-xs text-[var(--color-fg-muted)] py-4 text-center">
                                            Absence sur la matinée
                                        </div>
                                    )}
                                </div>

                                {/* Bloc Après-midi */}
                                <div className={`p-3 rounded-none space-y-3 border ${
                                    modePresence === 'MATIN_SEUL'
                                        ? 'bg-[var(--color-surface)] border-dashed border-[var(--color-border)] opacity-60'
                                        : 'bg-[var(--color-surface-subtle)] border-[var(--color-border-subtle)]'
                                }`}>
                                    <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center justify-between">
                                        <div className="flex items-center gap-1.5">
                                            <Sunset className="w-3.5 h-3.5 text-[var(--color-orange-500)]" />
                                            <span>Après-midi</span>
                                        </div>
                                        {modePresence === 'MATIN_SEUL' && (
                                            <span className="text-[10px] font-mono text-[var(--color-fg-muted)] bg-[var(--color-surface-subtle)] px-1.5 py-0.5 rounded-none">
                                                Non travaillée
                                            </span>
                                        )}
                                    </div>

                                    {modePresence !== 'MATIN_SEUL' ? (
                                        <div className="grid grid-cols-2 gap-2">
                                            <Input
                                                label="Retour Pause"
                                                type="time"
                                                value={heureEntreeAm}
                                                onChange={(e) => setHeureEntreeAm(e.target.value)}
                                                className="rounded-none"
                                            />
                                            <Input
                                                label="Sortie Soir"
                                                type="time"
                                                value={heureSortieAm}
                                                onChange={(e) => setHeureSortieAm(e.target.value)}
                                                className="rounded-none"
                                            />
                                        </div>
                                    ) : (
                                        <div className="text-xs text-[var(--color-fg-muted)] py-4 text-center">
                                            Absence sur l&apos;après-midi
                                        </div>
                                    )}
                                </div>
                            </div>
                        )}

                        {/* Traitement de l'autre demi-journée en mode demi-journée */}
                        {(modePresence === 'MATIN_SEUL' || modePresence === 'APRES_MIDI_SEUL') && (() => {
                            const labelDemiJournee = modePresence === 'MATIN_SEUL' ? "l'après-midi" : "la matinée";
                            return (
                                <div className="p-3 bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                                    <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                        <Info className="w-3.5 h-3.5 text-[var(--color-primary)]" />
                                        <span>Traitement de la demi-journée non travaillée ({modePresence === 'MATIN_SEUL' ? 'Après-midi' : 'Matin'}) :</span>
                                    </div>
                                    <div className="grid grid-cols-3 gap-2">
                                        <button
                                            type="button"
                                            data-regularisation-selected={optionAutreDemiJournee === 'AUCUNE'}
                                            onClick={() => setOptionAutreDemiJournee('AUCUNE')}
                                            className={`p-2 rounded-none text-left border text-xs transition-all cursor-pointer ${
                                                optionAutreDemiJournee === 'AUCUNE'
                                                    ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                    : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                            }`}
                                        >
                                            <div className="font-bold">Départ autorisé</div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">Payé au réel (sans solde) • Congé intact</div>
                                        </button>
                                        <button
                                            type="button"
                                            data-regularisation-selected={optionAutreDemiJournee === 'CONGE_PAYE'}
                                            onClick={() => setOptionAutreDemiJournee('CONGE_PAYE')}
                                            className={`p-2 rounded-none text-left border text-xs transition-all cursor-pointer ${
                                                optionAutreDemiJournee === 'CONGE_PAYE'
                                                    ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                    : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                            }`}
                                        >
                                            <div className="font-bold">Congé payé (0.5 j)</div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">Impact de solde confirmé par le module Congés.</div>
                                        </button>
                                        <button
                                            type="button"
                                            data-regularisation-selected={optionAutreDemiJournee === 'ABSENCE_DEDUITE'}
                                            onClick={() => setOptionAutreDemiJournee('ABSENCE_DEDUITE')}
                                            className={`p-2 rounded-none text-left border text-xs transition-all cursor-pointer ${
                                                optionAutreDemiJournee === 'ABSENCE_DEDUITE'
                                                    ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                    : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                            }`}
                                        >
                                            <div className="font-bold">Absence déduite</div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">Retenue horaire sur salaire</div>
                                        </button>
                                    </div>

                                    {/* Explication contextuelle dynamique de l'impact */}
                                    <div className="text-[11px] p-2 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] leading-relaxed">
                                        {optionAutreDemiJournee === 'AUCUNE' && (
                                            <div>
                                                <strong className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">Impact : </strong>
                                                L'absence sur {labelDemiJournee} est autorisée par la hiérarchie.
                                            </div>
                                        )}
                                        {optionAutreDemiJournee === 'CONGE_PAYE' && (
                                            <div>
                                                <strong className="text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">Impact : </strong>
                                                Le module Congés confirmera le débit de solde et le traitement de cette demi-journée.
                                            </div>
                                        )}
                                        {optionAutreDemiJournee === 'ABSENCE_DEDUITE' && (
                                            <div>
                                                <strong className="text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]">Impact : </strong>
                                                Absence non excusée. Les heures non faites de {labelDemiJournee} feront l'objet d'une retenue sur le bulletin de paie.
                                            </div>
                                        )}
                                    </div>
                                </div>
                            );
                        })()}

                        {/* Synthèse calculée des heures */}
                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none flex items-center justify-between text-xs">
                            <span className="text-[var(--color-fg-muted)]">
                                Total d&apos;heures de travail calculé :
                            </span>
                            <span className="font-mono font-bold text-sm text-[var(--color-fg)]">
                                {previewPending ? 'Calcul…' : dayPreview?.totalHeures != null ? `${dayPreview.totalHeures} h` : '—'}
                            </span>
                        </div>

                        {/* Indemnités associées */}
                        <div className="p-3 border border-[var(--color-border-subtle)] rounded-none space-y-2">
                            <div className="text-xs font-semibold text-[var(--color-fg)]">
                                Indemnités du jour
                            </div>
                            {allowancesLoading && <p className="text-xs">Chargement des indemnités du site…</p>}
                            {allowancesError && <p className="text-xs text-[var(--color-danger-700)]">{allowancesError}</p>}
                            {!allowancesLoading && !allowancesError && availableAllowances.length === 0 && (
                                <p className="text-xs">Aucune indemnité journalière configurée pour ce site.</p>
                            )}
                            <div className="flex flex-wrap gap-4 text-xs">
                                {availableAllowances.map((allowance) => {
                                    const key = String(allowance.idIndemnite);
                                    const selection = dynamicAllowances[key] || {};
                                    const fractionable = allowance.estFractionnable ?? allowance.fractionnable;
                                    const updateSelection = (field, value) => setDynamicAllowances((previous) => ({
                                        ...previous,
                                        [key]: { ...previous[key], [field]: value }
                                    }));
                                    return (
                                        <div key={key} className="flex items-center gap-2 text-[var(--color-fg)]">
                                            <span>{allowance.libelle || allowance.code}</span>
                                            {fractionable ? (
                                                <>
                                                    <label className="flex items-center gap-1">
                                                        <input type="checkbox" checked={Boolean(selection.matin)}
                                                            onChange={(event) => updateSelection('matin', event.target.checked)} /> Matin
                                                    </label>
                                                    <label className="flex items-center gap-1">
                                                        <input type="checkbox" checked={Boolean(selection.am)}
                                                            onChange={(event) => updateSelection('am', event.target.checked)} /> Après-midi
                                                    </label>
                                                </>
                                            ) : (
                                                <label className="flex items-center gap-1">
                                                    <input type="checkbox" checked={Boolean(selection.accordee)}
                                                        onChange={(event) => updateSelection('accordee', event.target.checked)} /> Accorder
                                                </label>
                                            )}
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    </div>
                )}

                {/* CONTENU ONGLET 2 : RÉGULARISATION D'ABSENCE */}
                {activeTab === 'ABSENCE' && (
                    <div className="space-y-4">
                        {/* Solde de congés actuel */}
                        <div className="p-3.5 bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] rounded-none flex items-center justify-between">
                            <div className="space-y-0.5">
                                <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                    <Wallet className="w-3.5 h-3.5 text-[var(--color-primary)]" />
                                    <span>Solde de Congés Payés du collaborateur</span>
                                </div>
                                <div className="text-[11px] text-[var(--color-fg-muted)]">
                                    Acquis : {soldeAcquis} j. | Consommé : {soldePris} j.
                                </div>
                            </div>
                            <div className="text-right">
                                <div className="text-[11px] text-[var(--color-fg-muted)]">Disponible net</div>
                                <div className={`font-mono text-base font-bold ${soldeRestant > 0 ? 'text-[var(--color-success)]' : 'text-[var(--color-error)]'}`}>
                                    {soldeRestant} j.
                                </div>
                            </div>
                        </div>

                        {/* Choix du mode d'absence */}
                        <div className="space-y-2">
                            <label className="text-xs font-medium text-[var(--color-fg)]">
                                Modalité de traitement de l&apos;absence :
                            </label>
                            <div className="grid grid-cols-3 gap-2">
                                <button
                                    type="button"
                                    data-regularisation-selected={optionAbsence === 'CONGE_PAYE'}
                                    onClick={() => setOptionAbsence('CONGE_PAYE')}
                                    className={`p-3 rounded-none border text-left transition-all ${
                                        optionAbsence === 'CONGE_PAYE'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="font-semibold text-xs flex items-center gap-1.5">
                                        <Palmtree className="w-3.5 h-3.5" />
                                        <span>Congé Payé Annuel</span>
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-1">
                                        Imputation directe sur le solde de congés
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    data-regularisation-selected={optionAbsence === 'PERMISSION_EXCEPTIONNELLE'}
                                    onClick={() => setOptionAbsence('PERMISSION_EXCEPTIONNELLE')}
                                    className={`p-3 rounded-none border text-left transition-all ${
                                        optionAbsence === 'PERMISSION_EXCEPTIONNELLE'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="font-semibold text-xs flex items-center gap-1.5">
                                        <HeartHandshake className="w-3.5 h-3.5" />
                                        <span>Permission (Art. 88)</span>
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-1">
                                        Événement familial 10j/an (Salaire maintenu)
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    data-regularisation-selected={optionAbsence === 'ABSENCE_DEDUITE'}
                                    onClick={() => setOptionAbsence('ABSENCE_DEDUITE')}
                                    className={`p-3 rounded-none border text-left transition-all ${
                                        optionAbsence === 'ABSENCE_DEDUITE'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="font-semibold text-xs flex items-center gap-1.5">
                                        <FileText className="w-3.5 h-3.5" />
                                        <span>Absence Déduite</span>
                                    </div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-1">
                                        Retenue automatique sur salaire (Taux horaire)
                                    </div>
                                </button>
                            </div>
                        </div>

                        {/* Options détaillées selon modalité choisie */}
                        {optionAbsence === 'CONGE_PAYE' && (
                            <div className="p-3.5 border border-[var(--color-border-subtle)] rounded-none space-y-3">
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-semibold text-[var(--color-fg)]">
                                        Volume à imputer sur le solde :
                                    </span>
                                    <div className="flex gap-2">
                                        <button
                                            type="button"
                                            data-regularisation-selected={congeDuree === '1.0'}
                                            onClick={() => setCongeDuree('1.0')}
                                            className={`px-3 py-1 rounded-none text-xs font-semibold border ${
                                                congeDuree === '1.0'
                                                    ? 'bg-[var(--color-primary)] text-[var(--color-on-color)] border-[var(--color-primary)]'
                                                    : 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
                                            }`}
                                        >
                                            Journée(s) entière(s)
                                        </button>
                                        <button
                                            type="button"
                                            data-regularisation-selected={congeDuree === '0.5'}
                                            onClick={() => setCongeDuree('0.5')}
                                            className={`px-3 py-1 rounded-none text-xs font-semibold border ${
                                                congeDuree === '0.5'
                                                    ? 'bg-[var(--color-primary)] text-[var(--color-on-color)] border-[var(--color-primary)]'
                                                    : 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
                                            }`}
                                        >
                                            Demi-journée (0.5 j)
                                        </button>
                                    </div>
                                </div>

                                {congeDuree !== '0.5' && (
                                    <div className="space-y-1">
                                        <label htmlFor="regularisation-date-reprise" className="block text-xs font-semibold">Date de reprise prévue</label>
                                        <Input id="regularisation-date-reprise" type="date" value={dateReprise}
                                            min={shiftIsoDate(anomalie?.datePointage, 1) || undefined}
                                            onChange={(event) => setDateReprise(event.target.value)} required />
                                        {nbJoursAImputer > 0 && <p className="text-xs text-[var(--color-fg-muted)]">
                                            Congé du {fmtDate(anomalie.datePointage)} au {fmtDate(dernierJourConge)} inclus : <strong>{nbJoursAImputer} jour{nbJoursAImputer > 1 ? 's' : ''} calendaires débités</strong>. La reprise n&apos;est pas débitée.
                                        </p>}
                                    </div>
                                )}

                                {congeDuree === '0.5' && (
                                    <div className="flex items-center gap-3 text-xs pt-1">
                                        <span className="text-[var(--color-fg-muted)]">Moment :</span>
                                        <label className="flex items-center gap-1.5 cursor-pointer">
                                            <input
                                                type="radio"
                                                name="moment"
                                                checked={demiJourneeMoment === 'MATIN'}
                                                onChange={() => setDemiJourneeMoment('MATIN')}
                                            />
                                            <span>Matinée</span>
                                        </label>
                                        <label className="flex items-center gap-1.5 cursor-pointer">
                                            <input
                                                type="radio"
                                                name="moment"
                                                checked={demiJourneeMoment === 'APRES_MIDI'}
                                                onChange={() => setDemiJourneeMoment('APRES_MIDI')}
                                            />
                                            <span>Après-midi</span>
                                        </label>
                                    </div>
                                )}

                                {/* Diagnostic solde après imputation */}
                                <div className="p-2.5 rounded-none text-xs flex items-center justify-between bg-[var(--color-info-subtle)] text-[var(--color-info)]">
                                    <div className="flex items-center gap-1.5">
                                        <Info className="w-4 h-4 shrink-0" />
                                        <span>
                                            {nbJoursAImputer > 1
                                                ? 'Le module Congés vérifiera le solde pour toute la période. Si le solde est insuffisant, aucune journée ne sera régularisée.'
                                                : 'Le module Congés vérifiera le solde disponible et confirmera le nombre de jours couverts.'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        )}

                        {optionAbsence === 'PERMISSION_EXCEPTIONNELLE' && (
                            <div className="p-3.5 border border-[var(--color-border-subtle)] rounded-none space-y-3">
                                <label className="block text-xs font-semibold text-[var(--color-fg)]">
                                    Motif légal de l&apos;événement familial (Art. 88) :
                                </label>
                                <select
                                    value={motifFamilial}
                                    onChange={(e) => setMotifFamilial(e.target.value)}
                                    className="w-full text-xs p-2 rounded-none border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)]"
                                >
                                    {MOTIFS_FAMILIAUX.map((m) => (
                                        <option key={m.value} value={m.value}>
                                            {m.label}
                                        </option>
                                    ))}
                                </select>
                                <div className="text-[11px] text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <CheckCircle2 className="w-3.5 h-3.5 text-[var(--color-success)] shrink-0" />
                                    <span>Conforme au droit malgache : salaire maintenu intégralement sans impact sur le solde annuel.</span>
                                </div>
                            </div>
                        )}

                        {optionAbsence === 'ABSENCE_DEDUITE' && (
                            <div className="p-3.5 border border-[var(--color-border-subtle)] rounded-none space-y-3">
                                <label className="block text-xs font-semibold text-[var(--color-fg)]">
                                    Qualification de l&apos;absence :
                                </label>
                                <div className="grid grid-cols-2 gap-2">
                                    <label className="flex items-center gap-2 p-2 rounded-none border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-xs cursor-pointer">
                                        <input
                                            type="radio"
                                            name="statutDeduite"
                                            value="ABSENCE_JUSTIFIEE"
                                            checked={statutAbsenceDeduite === 'ABSENCE_JUSTIFIEE'}
                                            onChange={() => setStatutAbsenceDeduite('ABSENCE_JUSTIFIEE')}
                                        />
                                        <span>Absence Justifiée (Certificat / Accord)</span>
                                    </label>
                                    <label className="flex items-center gap-2 p-2 rounded-none border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-xs cursor-pointer">
                                        <input
                                            type="radio"
                                            name="statutDeduite"
                                            value="ABSENCE_NON_JUSTIFIEE"
                                            checked={statutAbsenceDeduite === 'ABSENCE_NON_JUSTIFIEE'}
                                            onChange={() => setStatutAbsenceDeduite('ABSENCE_NON_JUSTIFIEE')}
                                        />
                                        <span>Absence Non Justifiée</span>
                                    </label>
                                </div>
                                <div className="text-[11px] text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                    <Info className="w-3.5 h-3.5 text-[var(--color-info)] shrink-0" />
                                    <span>La déduction est calculée par la Paie avec le diviseur légal applicable à la période.</span>
                                </div>
                            </div>
                        )}

                        {/* Indemnités lors de la régularisation d'absence */}
                        <div className="p-3 border border-[var(--color-border-subtle)] rounded-none space-y-2">
                            <div className="flex items-center justify-between">
                                <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                    <Wallet className="w-3.5 h-3.5 text-[var(--color-fg-muted)]" />
                                    <span>Indemnités du jour</span>
                                </div>
                                <span className="text-[10px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] bg-[var(--color-warning-500)]/10 px-1.5 py-0.5 font-semibold">
                                    Décochées par défaut — exception manuelle
                                </span>
                            </div>
                            {allowancesLoading && <p className="text-xs text-[var(--color-fg-muted)]">Chargement des indemnités du site…</p>}
                            {allowancesError && <p className="text-xs text-[var(--color-danger-700)]">{allowancesError}</p>}
                            {!allowancesLoading && !allowancesError && availableAllowances.length === 0 && (
                                <p className="text-xs text-[var(--color-fg-muted)]">Aucune indemnité journalière configurée pour ce site.</p>
                            )}
                            {!allowancesLoading && !allowancesError && availableAllowances.length > 0 && (
                                <div className="flex flex-wrap gap-4 text-xs">
                                    {availableAllowances.map((allowance) => {
                                        const key = String(allowance.idIndemnite);
                                        const selection = dynamicAllowances[key] || {};
                                        const fractionable = allowance.estFractionnable ?? allowance.fractionnable;
                                        const updateSelection = (field, value) => setDynamicAllowances((previous) => ({
                                            ...previous,
                                            [key]: { ...previous[key], [field]: value }
                                        }));
                                        return (
                                            <div key={key} className="flex items-center gap-2 text-[var(--color-fg)]">
                                                <span className="font-medium">{allowance.libelle || allowance.code}</span>
                                                {fractionable ? (
                                                    <>
                                                        <label className="flex items-center gap-1 cursor-pointer">
                                                            <input type="checkbox" checked={Boolean(selection.matin)}
                                                                onChange={(event) => updateSelection('matin', event.target.checked)}
                                                                className="w-3.5 h-3.5 accent-brand-600" />
                                                            <span>Aller</span>
                                                        </label>
                                                        <label className="flex items-center gap-1 cursor-pointer">
                                                            <input type="checkbox" checked={Boolean(selection.am)}
                                                                onChange={(event) => updateSelection('am', event.target.checked)}
                                                                className="w-3.5 h-3.5 accent-brand-600" />
                                                            <span>Retour</span>
                                                        </label>
                                                    </>
                                                ) : (
                                                    <label className="flex items-center gap-1 cursor-pointer" title={`Accorder une exception d'indemnité sur ce jour d'absence — ${allowance.libelle || allowance.code}`}>
                                                        <input type="checkbox" checked={Boolean(selection.accordee)}
                                                            onChange={(event) => updateSelection('accordee', event.target.checked)}
                                                            className="w-3.5 h-3.5 accent-brand-600" />
                                                        <span className={selection.accordee ? 'text-[var(--color-success-600)] font-bold' : 'text-[var(--color-fg-muted)]'}>
                                                            {selection.accordee ? 'Accordée' : 'Non accordée'}
                                                        </span>
                                                    </label>
                                                )}
                                            </div>
                                        );
                                    })}
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* Motif / Commentaire obligatoire */}
                {activeTab !== 'RETARD_REEL' && <div className="space-y-1.5">
                    <label className="text-xs font-semibold text-[var(--color-fg)] flex items-center justify-between">
                        <span>Motif / Note explicative de régularisation <span className="text-[var(--color-error)]">*</span></span>
                        <span className="text-[10px] text-[var(--color-fg-muted)]">Minimum 3 caractères</span>
                    </label>
                    <textarea
                        value={motifAjustement}
                        onChange={(e) => {
                            if (error) setError('');
                            setMotifAjustement(e.target.value);
                        }}
                        rows={3}
                        placeholder="Précisez le motif (ex: Oubli badgeage sortie, justificatif médical reçu, permission accordée...)"
                        className="w-full text-xs p-2.5 rounded-none border border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:ring-1 focus:ring-[var(--color-primary)] placeholder:text-[var(--color-fg-muted)]"
                    />
                </div>}
            </div>
        </Modal>
    );
}
