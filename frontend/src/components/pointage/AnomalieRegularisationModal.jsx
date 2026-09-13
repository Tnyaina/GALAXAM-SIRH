import { useState, useEffect, useMemo } from 'react';
import { 
    Clock, 
    Palmtree, 
    CheckCircle2, 
    FileText, 
    Shield, 
    Info, 
    HeartHandshake,
    Wallet,
    Bus,
    Utensils,
    Sun,
    Sunset
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import { fmtDate } from '@/utils/carriereUtils';

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
    const [activeTab, setActiveTab] = useState('HEURES'); // 'HEURES' ou 'ABSENCE'
    const [saving, setSaving] = useState(false);
    const [error, setError] = useState('');

    // Formulaire Mode 1 : Horaires
    const [modePresence, setModePresence] = useState('JOURNEE'); // 'JOURNEE', 'MATIN_SEUL', 'APRES_MIDI_SEUL', 'CONTINU'
    const [optionAutreDemiJournee, setOptionAutreDemiJournee] = useState('AUCUNE'); // 'AUCUNE', 'CONGE_PAYE', 'ABSENCE_DEDUITE'
    const [heureEntreeMatin, setHeureEntreeMatin] = useState('08:00');
    const [heureSortieMatin, setHeureSortieMatin] = useState('12:00');
    const [heureEntreeAm, setHeureEntreeAm] = useState('13:00');
    const [heureSortieAm, setHeureSortieAm] = useState('17:00');
    const [deplacementMatin, setDeplacementMatin] = useState(true);
    const [deplacementAm, setDeplacementAm] = useState(true);
    const [indemniteRepas, setIndemniteRepas] = useState(true);

    // Formulaire Mode 2 : Absence
    const [optionAbsence, setOptionAbsence] = useState('CONGE_PAYE'); // 'CONGE_PAYE', 'PERMISSION_EXCEPTIONNELLE', 'ABSENCE_DEDUITE'
    const [congeDuree, setCongeDuree] = useState('1.0'); // '1.0' ou '0.5'
    const [demiJourneeMoment, setDemiJourneeMoment] = useState('MATIN');
    const [motifFamilial, setMotifFamilial] = useState(MOTIFS_FAMILIAUX[0].value);
    const [statutAbsenceDeduite, setStatutAbsenceDeduite] = useState('ABSENCE_JUSTIFIEE');

    // Motif global obligatoire
    const [motifAjustement, setMotifAjustement] = useState('');

    const siteEstContinu = Boolean(anomalie?.siteHoraireContinu);

    const handleModeChange = (mode) => {
        setModePresence(mode);
        if (mode === 'MATIN_SEUL') {
            setDeplacementMatin(true);
            setDeplacementAm(false);
            setIndemniteRepas(false);
        } else if (mode === 'APRES_MIDI_SEUL') {
            setDeplacementMatin(false);
            setDeplacementAm(true);
            setIndemniteRepas(false);
        } else if (mode === 'CONTINU') {
            setDeplacementMatin(true);
            setDeplacementAm(true);
            // Journée continue : repas pris sur site par défaut (-1000 Ar transport si site discontinu)
            setIndemniteRepas(true);
        } else {
            setDeplacementMatin(true);
            setDeplacementAm(true);
            // Journée coupée normale : repas par défaut si site continu (TNR), sinon décoché (TMM)
            setIndemniteRepas(siteEstContinu);
        }
    };

    // Initialisation lors de l'ouverture
    useEffect(() => {
        if (!anomalie) return;
        setError('');
        
        const isAbsence = anomalie.typeAnomalie === 'ABSENCE' || anomalie.statutCode === 'ABSENCE_NON_JUSTIFIEE' || anomalie.statutCode === 'ABSENCE_JUSTIFIEE';
        setActiveTab(isAbsence ? 'ABSENCE' : 'HEURES');

        const em = anomalie.heureEntreeMatin || '';
        const sm = anomalie.heureSortieMatin || '';
        const ea = anomalie.heureEntreeAm || '';
        const sa = anomalie.heureSortieAm || '';

        setHeureEntreeMatin(em || '08:00');
        setHeureSortieMatin(sm || '12:00');
        setHeureEntreeAm(ea || '13:00');
        setHeureSortieAm(sa || '17:00');

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

        if (isMatinSeul) {
            setDeplacementMatin(anomalie.deplacementMatin ?? true);
            setDeplacementAm(false);
            setIndemniteRepas(false);
        } else if (isAmSeul) {
            setDeplacementMatin(false);
            setDeplacementAm(anomalie.deplacementAm ?? true);
            setIndemniteRepas(false);
        } else if (isContinu) {
            setDeplacementMatin(anomalie.deplacementMatin ?? true);
            setDeplacementAm(anomalie.deplacementAm ?? true);
            setIndemniteRepas(anomalie.indemniteRepasRecue !== undefined && anomalie.indemniteRepasRecue !== null ? Boolean(anomalie.indemniteRepasRecue) : true);
        } else {
            setDeplacementMatin(anomalie.deplacementMatin ?? true);
            setDeplacementAm(anomalie.deplacementAm ?? true);
            setIndemniteRepas(anomalie.indemniteRepasRecue !== undefined && anomalie.indemniteRepasRecue !== null ? Boolean(anomalie.indemniteRepasRecue) : siteEstContinu);
        }

        setOptionAbsence('CONGE_PAYE');
        setCongeDuree('1.0');
        setDemiJourneeMoment('MATIN');
        setMotifFamilial(MOTIFS_FAMILIAUX[0].value);
        setStatutAbsenceDeduite('ABSENCE_JUSTIFIEE');

        setMotifAjustement(anomalie.motifAjustement || '');
    }, [anomalie, isOpen]);

    // Calcul des heures travaillées en temps réel (Mode 1)
    const calculHeures = useMemo(() => {
        const toMins = (t) => {
            if (!t || !t.includes(':')) return null;
            const [h, m] = t.split(':').map(Number);
            return h * 60 + m;
        };

        const em = toMins(heureEntreeMatin);
        const sm = toMins(heureSortieMatin);
        const ea = toMins(heureEntreeAm);
        const sa = toMins(heureSortieAm);

        if (modePresence === 'CONTINU') {
            if (em !== null && sa !== null && sa > em) {
                return { totalH: Math.round(((sa - em) / 60) * 100) / 100, valid: true };
            }
            return { totalH: 0, valid: false };
        }

        if (modePresence === 'MATIN_SEUL') {
            if (em !== null && sm !== null && sm > em) {
                return { totalH: Math.round(((sm - em) / 60) * 100) / 100, valid: true };
            }
            return { totalH: 0, valid: false };
        }

        if (modePresence === 'APRES_MIDI_SEUL') {
            if (ea !== null && sa !== null && sa > ea) {
                return { totalH: Math.round(((sa - ea) / 60) * 100) / 100, valid: true };
            }
            return { totalH: 0, valid: false };
        }

        let totMins = 0;
        let valid = true;
        if (em !== null && sm !== null) {
            if (sm > em) totMins += (sm - em);
            else valid = false;
        } else {
            valid = false;
        }
        if (ea !== null && sa !== null) {
            if (sa > ea) totMins += (sa - ea);
            else valid = false;
        } else {
            valid = false;
        }

        return { totalH: Math.round((totMins / 60) * 100) / 100, valid };
    }, [heureEntreeMatin, heureSortieMatin, heureEntreeAm, heureSortieAm, modePresence]);

    // Solde de congés actuel
    const soldeRestant = anomalie?.soldeCongesRestant ?? 0;
    const soldeAcquis = anomalie?.soldeCongesAcquis ?? 0;
    const soldePris = anomalie?.soldeCongesPris ?? 0;

    // Simulation après imputation
    const nbJoursAImputer = parseFloat(congeDuree) || 1.0;
    const soldeSuffisant = soldeRestant >= nbJoursAImputer;
    const soldeApresImputation = Math.round((soldeRestant - nbJoursAImputer) * 100) / 100;

    const handleSubmit = async (e) => {
        if (e) e.preventDefault();
        setError('');

        if (!motifAjustement || motifAjustement.trim().length < 3) {
            setError('Un motif explicatif est obligatoire pour régulariser un pointage (minimum 3 caractères).');
            return;
        }

        setSaving(true);
        try {
            const payload = {
                version: anomalie.version || 1,
                motifAjustement: motifAjustement.trim(),
                typeRegularisation: activeTab,
                idEmploye: anomalie.idEmploye,
                idPeriodePaie: anomalie.idPeriodePaie,
                idSite: anomalie.idSite,
                datePointage: anomalie.datePointage,
                horaireContinuJour: activeTab === 'HEURES' && modePresence === 'CONTINU'
            };

            if (activeTab === 'HEURES') {
                if (!calculHeures.valid || calculHeures.totalH <= 0) {
                    setError('Veuillez renseigner des tranches horaires valides.');
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

                payload.deplacementMatin = deplacementMatin;
                payload.deplacementAm = deplacementAm;
                payload.indemniteRepasRecue = indemniteRepas;
            } else {
                // Régularisation d'absence
                payload.optionAbsence = optionAbsence;
                if (optionAbsence === 'CONGE_PAYE') {
                    payload.statutCode = 'CONGE';
                    payload.nbJoursCongeDecompte = nbJoursAImputer;
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
            setSaving(false);
        }
    };

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
                            Enregistrer la régularisation
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
                            src={getFullPhotoUrl(anomalie.photoUrl)}
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
                    <Alert type="error" title="Action requise" className="rounded-none">
                        {error}
                    </Alert>
                )}

                {/* Onglets de mode */}
                <div className="flex border-b border-[var(--color-border-subtle)]">
                    <button
                        type="button"
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
                                    onClick={() => handleModeChange('MATIN_SEUL')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'MATIN_SEUL'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Sun className="w-3.5 h-3.5 text-amber-500" />
                                        <span>Matinée seule</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Demi-journée (matin)
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    onClick={() => handleModeChange('APRES_MIDI_SEUL')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'APRES_MIDI_SEUL'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Sunset className="w-3.5 h-3.5 text-orange-500" />
                                        <span>Après-midi seul</span>
                                    </div>
                                    <div className="text-[10px] text-[var(--color-fg-muted)] mt-0.5">
                                        Demi-journée (AM)
                                    </div>
                                </button>

                                <button
                                    type="button"
                                    onClick={() => handleModeChange('CONTINU')}
                                    className={`p-2.5 rounded-none border text-left transition-all ${
                                        modePresence === 'CONTINU'
                                            ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold ring-1 ring-[var(--color-primary)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                    }`}
                                >
                                    <div className="text-xs font-semibold flex items-center gap-1.5">
                                        <Clock className="w-3.5 h-3.5 text-indigo-500" />
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
                                            <Sun className="w-3.5 h-3.5 text-amber-500" />
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
                                            <Sunset className="w-3.5 h-3.5 text-orange-500" />
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
                        {(modePresence === 'MATIN_SEUL' || modePresence === 'APRES_MIDI_SEUL') && (
                            <div className="p-3 bg-[var(--color-surface-subtle)] border border-[var(--color-border-subtle)] rounded-none space-y-2.5">
                                <div className="text-xs font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                    <Info className="w-3.5 h-3.5 text-[var(--color-primary)]" />
                                    <span>Traitement de la demi-journée non travaillée ({modePresence === 'MATIN_SEUL' ? 'Après-midi' : 'Matin'}) :</span>
                                </div>
                                <div className="grid grid-cols-3 gap-2">
                                    <button
                                        type="button"
                                        onClick={() => setOptionAutreDemiJournee('AUCUNE')}
                                        className={`p-2 rounded-none text-left border text-xs transition-all ${
                                            optionAutreDemiJournee === 'AUCUNE'
                                                ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)]'
                                        }`}
                                    >
                                        <div className="font-semibold">Absence autorisée</div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Payé au réel travaillé</div>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setOptionAutreDemiJournee('CONGE_PAYE')}
                                        className={`p-2 rounded-none text-left border text-xs transition-all ${
                                            optionAutreDemiJournee === 'CONGE_PAYE'
                                                ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)]'
                                        }`}
                                    >
                                        <div className="font-semibold">Décompte congé 0.5j</div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Solde : {soldeRestant} j. → {Math.max(0, Math.round((soldeRestant - 0.5) * 100) / 100)} j.</div>
                                    </button>
                                    <button
                                        type="button"
                                        onClick={() => setOptionAutreDemiJournee('ABSENCE_DEDUITE')}
                                        className={`p-2 rounded-none text-left border text-xs transition-all ${
                                            optionAutreDemiJournee === 'ABSENCE_DEDUITE'
                                                ? 'border-[var(--color-primary)] bg-[var(--color-primary-subtle)] text-[var(--color-primary)] font-semibold'
                                                : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)]'
                                        }`}
                                    >
                                        <div className="font-semibold">Absence déduite</div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Déduction horaire en paie</div>
                                    </button>
                                </div>
                            </div>
                        )}

                        {/* Synthèse calculée des heures */}
                        <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none flex items-center justify-between text-xs">
                            <span className="text-[var(--color-fg-muted)]">
                                Total d&apos;heures de travail calculé :
                            </span>
                            <span className="font-mono font-bold text-sm text-[var(--color-fg)]">
                                {calculHeures.totalH} h
                            </span>
                        </div>

                        {/* Indemnités associées */}
                        <div className="p-3 border border-[var(--color-border-subtle)] rounded-none space-y-2">
                            <div className="text-xs font-semibold text-[var(--color-fg)]">
                                Indemnités du jour
                            </div>
                            <div className="flex flex-wrap gap-4 text-xs">
                                <label className="flex items-center gap-2 cursor-pointer text-[var(--color-fg)]">
                                    <input
                                        type="checkbox"
                                        checked={deplacementMatin}
                                        onChange={(e) => setDeplacementMatin(e.target.checked)}
                                        className="rounded-none text-[var(--color-primary)]"
                                    />
                                    <Bus className="w-3.5 h-3.5 text-[var(--color-fg-muted)]" />
                                    <span>Déplacement Aller</span>
                                </label>
                                <label className="flex items-center gap-2 cursor-pointer text-[var(--color-fg)]">
                                    <input
                                        type="checkbox"
                                        checked={deplacementAm}
                                        onChange={(e) => setDeplacementAm(e.target.checked)}
                                        className="rounded-none text-[var(--color-primary)]"
                                    />
                                    <Bus className="w-3.5 h-3.5 text-[var(--color-fg-muted)]" />
                                    <span>Déplacement Retour</span>
                                </label>
                                <label className="flex items-center gap-2 cursor-pointer text-[var(--color-fg)]">
                                    <input
                                        type="checkbox"
                                        checked={indemniteRepas}
                                        onChange={(e) => setIndemniteRepas(e.target.checked)}
                                        className="rounded-none text-[var(--color-primary)]"
                                    />
                                    <Utensils className="w-3.5 h-3.5 text-[var(--color-fg-muted)]" />
                                    <span>Panier Repas (2 000 Ar)</span>
                                </label>
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
                                            onClick={() => setCongeDuree('1.0')}
                                            className={`px-3 py-1 rounded-none text-xs font-semibold border ${
                                                congeDuree === '1.0'
                                                    ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]'
                                                    : 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
                                            }`}
                                        >
                                            Journée entière (1.0 j)
                                        </button>
                                        <button
                                            type="button"
                                            onClick={() => setCongeDuree('0.5')}
                                            className={`px-3 py-1 rounded-none text-xs font-semibold border ${
                                                congeDuree === '0.5'
                                                    ? 'bg-[var(--color-primary)] text-white border-[var(--color-primary)]'
                                                    : 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)]'
                                            }`}
                                        >
                                            Demi-journée (0.5 j)
                                        </button>
                                    </div>
                                </div>

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
                                <div className={`p-2.5 rounded-none text-xs flex items-center justify-between ${
                                    soldeSuffisant
                                        ? 'bg-[var(--color-success-subtle)] text-[var(--color-success)]'
                                        : 'bg-[var(--color-warning-subtle)] text-[var(--color-warning)]'
                                }`}>
                                    <div className="flex items-center gap-1.5">
                                        <Info className="w-4 h-4 shrink-0" />
                                        <span>
                                            {soldeSuffisant 
                                                ? `Solde suffisant. Nouveau solde restant projeté : ${soldeApresImputation} j.`
                                                : `Solde insuffisant (${soldeRestant} j.). Dérogation ou retenue partielle sur le salaire.`}
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
                                    <span>Formule Paie : Déduction nette = (Salaire Base ÷ 173.33) × Heures non travaillées.</span>
                                </div>
                            </div>
                        )}
                    </div>
                )}

                {/* Motif / Commentaire obligatoire */}
                <div className="space-y-1.5">
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
                </div>
            </div>
        </Modal>
    );
}
