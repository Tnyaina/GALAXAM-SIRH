import { useState, useEffect, useCallback } from 'react';
import {
    Calculator,
    CheckCircle2,
    AlertTriangle,
    ArrowRight,
    ArrowLeft,
    FileCheck
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { Table, Thead, Th, Tbody, Tr, Td, Tfoot } from '@/components/ui/data-display/Table';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';
import StcRecuModal from './StcRecuModal';

const MOTIFS_RUPTURE = [
    { value: 'DEMISSION', label: 'Démission volontaire' },
    { value: 'LICENCIEMENT', label: 'Licenciement (conventionnel / économique)' },
    { value: 'FIN_CONTRAT', label: 'Arrivée à terme du contrat (CDD / Essai)' },
    { value: 'RUPTURE_CONVENTIONNELLE', label: 'Rupture conventionnelle d’un commun accord' },
    { value: 'RETRAITE', label: 'Départ à la retraite' },
    { value: 'AUTRE', label: 'Autre motif légal' },
];

const MODES_REGLEMENT = [
    { value: 'VIREMENT', label: 'Virement bancaire' },
    { value: 'CHEQUE', label: 'Chèque bancaire de clôture' },
    { value: 'ESPECES', label: 'Espèces (Caisse régie)' },
];

const fmtDateInput = (val) => {
    if (!val) return '';
    if (typeof val === 'string') {
        const parts = val.split('T');
        if (parts[0] && parts[0].length === 10) return parts[0];
    }
    try {
        const d = new Date(val);
        if (isNaN(d.getTime())) return '';
        return d.toISOString().split('T')[0];
    } catch {
        return '';
    }
};

/**
 * Modale de Préparation, Simulation et Émission du Solde de Tout Compte (STC).
 * Workflow strict à 3 étapes : PRÉPARATION -> SIMULATION -> VALIDÉ.
 */
export default function StcPreparationModal({
    isOpen,
    onClose,
    idEmploye,
    nomEmploye,
    dateDepartInitiale = null,
    motifInitial = 'DEMISSION',
    modePreavisInitial = null,
    moisPreavisInitial = null,
    indemnitePreavisInitiale = null,
    onStcValide,
}) {
    // État local : étape, préparation, calcul, formulaire et reçu.
    // Étape UI courante : 'PREPARATION' | 'SIMULATION' | 'VALIDE'
    const [step, setStep] = useState('PREPARATION');
    const [loadingPrep, setLoadingPrep] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState(null);

    // Données préparatoires renvoyées par le backend
    const [prepData, setPrepData] = useState(null);

    // Résultat du calcul de simulation ou validation
    const [calculResult, setCalculResult] = useState(null);

    // Contrôle d'affichage de la quittance imprimable
    const [showRecuModal, setShowRecuModal] = useState(false);
    const [showConfirmValider, setShowConfirmValider] = useState(false);

    // Formulaire des paramètres utilisateur (StcCalculRequest)
    const [form, setForm] = useState({
        dateDepart: '',
        motifRupture: 'DEMISSION',
        moisPreavis: '0',
        joursTravaillesDernierMois: '',
        indemnitePreavis: '',
        indemniteLicenciement: '',
        autresIndemnites: '',
        motifAutresIndemnites: '',
        autresDeductions: '',
        motifAutresDeductions: '',
        modeReglement: 'VIREMENT',
        commentaire: '',
        desactiverEmploye: false,
    });

    // Chargement et actions utilisateur
    const chargerDonneesPreparatoires = useCallback(async () => {
        if (!idEmploye) return;
        setLoadingPrep(true);
        setErrorMsg(null);
        try {
            const dateRef = fmtDateInput(dateDepartInitiale) || new Date().toISOString().split('T')[0];
            const data = await stcService.preparerStc(idEmploye, dateRef, motifInitial);
            setPrepData(data);

            // Pré-remplissage 100% backend-driven
            setForm({
                dateDepart: fmtDateInput(data.dateDepart || dateRef),
                motifRupture: data.motifRupture || motifInitial || 'DEMISSION',
                moisPreavis: modePreavisInitial
                    ? (modePreavisInitial === 'DISPENSE_PAYEE' ? String(moisPreavisInitial ?? 0) : '0')
                    : (moisPreavisInitial !== null ? String(moisPreavisInitial) : String(data.moisPreavisSuggere ?? '0')),
                joursTravaillesDernierMois: data.joursTravaillesEstimes != null ? String(data.joursTravaillesEstimes) : '',
                indemnitePreavis: modePreavisInitial
                    ? (modePreavisInitial === 'DISPENSE_PAYEE'
                        ? String(indemnitePreavisInitiale ?? data.indemnitePreavisEstimee ?? 0)
                        : '0')
                    : ((indemnitePreavisInitiale !== null && Number(indemnitePreavisInitiale) >= 0)
                        ? String(indemnitePreavisInitiale)
                        : (data.indemnitePreavisEstimee != null && Number(data.indemnitePreavisEstimee) > 0 ? String(data.indemnitePreavisEstimee) : '')),
                indemniteLicenciement: data.indemniteLicenciementEstimee != null && Number(data.indemniteLicenciementEstimee) > 0 ? String(data.indemniteLicenciementEstimee) : '',
                autresIndemnites: '',
                motifAutresIndemnites: '',
                autresDeductions: '',
                motifAutresDeductions: '',
                modeReglement: 'VIREMENT',
                commentaire: '',
                desactiverEmploye: false,
            });
            setStep('PREPARATION');
            setCalculResult(null);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible de collecter les données préparatoires du STC.'));
        } finally {
            setLoadingPrep(false);
        }
    }, [idEmploye, dateDepartInitiale, motifInitial, modePreavisInitial, moisPreavisInitial, indemnitePreavisInitiale]);

    // Effet : charge la préparation du STC à l'ouverture.
    useEffect(() => {
        if (isOpen && idEmploye) {
            chargerDonneesPreparatoires();
        } else {
            setPrepData(null);
            setCalculResult(null);
            setErrorMsg(null);
            setStep('PREPARATION');
        }
    }, [isOpen, idEmploye, chargerDonneesPreparatoires]);

    const handleFormChange = (field, val) => {
        setForm(prev => ({ ...prev, [field]: val }));
    };

    const buildPayload = (simulationMode = true) => {
        return {
            idEmploye: Number(idEmploye),
            idPeriodePaie: prepData?.idPeriodePaie || null,
            dateDepart: fmtDateInput(form.dateDepart),
            motifRupture: form.motifRupture,
            joursTravaillesDernierMois: form.joursTravaillesDernierMois ? Number(form.joursTravaillesDernierMois) : null,
            moisPreavis: modePreavisInitial && modePreavisInitial !== 'DISPENSE_PAYEE'
                ? 0
                : (form.moisPreavis !== '' ? Number(form.moisPreavis) : 0),
            indemnitePreavis: modePreavisInitial && modePreavisInitial !== 'DISPENSE_PAYEE'
                ? 0
                : (form.indemnitePreavis !== '' ? Number(form.indemnitePreavis) : null),
            indemniteLicenciement: form.indemniteLicenciement ? Number(form.indemniteLicenciement) : null,
            autresIndemnites: form.autresIndemnites ? Number(form.autresIndemnites) : null,
            motifAutresIndemnites: form.motifAutresIndemnites || null,
            autresDeductions: form.autresDeductions ? Number(form.autresDeductions) : null,
            motifAutresDeductions: form.motifAutresDeductions || null,
            modeReglement: form.modeReglement || 'VIREMENT',
            commentaire: form.commentaire || null,
            simulation: simulationMode,
            desactiverEmploye: Boolean(form.desactiverEmploye),
        };
    };

    // 1. Déclenche la simulation
    const handleSimuler = async (e) => {
        if (e) e.preventDefault();
        if (!form.dateDepart) {
            setErrorMsg('La date de cessation effective est obligatoire.');
            return;
        }

        setSubmitting(true);
        setErrorMsg(null);
        try {
            const payload = buildPayload(true);
            const result = await stcService.simulerStc(payload);
            setCalculResult(result);
            setStep('SIMULATION');
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors de la simulation du Solde de Tout Compte.'));
        } finally {
            setSubmitting(false);
        }
    };

    // 2. Déclenche la validation définitive et émission du reçu numéroté
    const handleValiderDefinitivement = async () => {
        setShowConfirmValider(false);
        setSubmitting(true);
        setErrorMsg(null);
        try {
            const payload = buildPayload(false);
            const result = await stcService.validerStc(payload);
            setCalculResult(result);
            setStep('VALIDE');
            if (onStcValide) {
                onStcValide(result);
            }
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Erreur lors de la validation officielle du STC.'));
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen) return null;

    const modalTitle = step === 'VALIDE'
        ? `STC Validé — ${calculResult?.numeroRecu || 'Reçu émis'}`
        : step === 'SIMULATION'
        ? `Simulation STC — ${prepData?.nomComplet || nomEmploye || ''}`
        : `Préparation Solde de Tout Compte — ${prepData?.nomComplet || nomEmploye || ''}`;
    const dateDepartFuture = Boolean(form.dateDepart && form.dateDepart > fmtDateInput(new Date()));

    // Rendu
    return (
        <>
            <Modal
                isOpen={isOpen}
                onClose={step === 'VALIDE' ? onClose : submitting ? () => {} : onClose}
                title={modalTitle}
                icon={<Calculator size={18} className="text-[var(--color-brand-600)]" />}
                size="3xl"
                footer={
                    <div className="flex justify-between items-center w-full">
                        {step === 'PREPARATION' && (
                            <>
                                <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={submitting}>
                                    Annuler
                                </Button>
                                <Button
                                    type="button"
                                    variant="primary"
                                    size="sm"
                                    onClick={handleSimuler}
                                    loading={submitting}
                                    iconRight={<ArrowRight size={15} />}
                                >
                                    Simuler le calcul STC
                                </Button>
                            </>
                        )}

                        {step === 'SIMULATION' && (
                            <>
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    onClick={() => setStep('PREPARATION')}
                                    disabled={submitting}
                                    iconLeft={<ArrowLeft size={15} />}
                                >
                                    Modifier les paramètres
                                </Button>
                                <Button
                                    type="button"
                                    variant="danger"
                                    size="sm"
                                    onClick={() => setShowConfirmValider(true)}
                                    loading={submitting}
                                    disabled={dateDepartFuture}
                                    iconLeft={<FileCheck size={15} />}
                                >
                                    Valider et Émettre le Reçu Officiel
                                </Button>
                            </>
                        )}

                        {step === 'VALIDE' && (
                            <>
                                <div className="flex items-center gap-1.5 text-xs text-[var(--color-success-600)] font-semibold">
                                    <CheckCircle2 size={16} /> Reçu officiel {calculResult?.numeroRecu} enregistré
                                </div>
                                <div className="flex gap-2">
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setShowRecuModal(true)}
                                        iconLeft={<FileCheck size={15} />}
                                    >
                                        Consulter la Quittance Libératoire
                                    </Button>
                                    <Button type="button" variant="primary" size="sm" onClick={onClose}>
                                        Terminer
                                    </Button>
                                </div>
                            </>
                        )}
                    </div>
                }
            >
                {loadingPrep && (
                    <div className="py-16 text-center text-xs text-[var(--color-fg-muted)]">
                        Collecte des données administratives, congés et prêts du salarié...
                    </div>
                )}

                {errorMsg && (
                    <div className="mb-4">
                        <FormError title="Erreur STC" message={errorMsg} onClose={() => setErrorMsg(null)} />
                    </div>
                )}

                {!loadingPrep && prepData && (
                    <div className="space-y-4">
                        {/* Indicateur d'étapes */}
                        <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3 text-xs">
                            <div className={`flex items-center gap-1.5 font-bold ${step === 'PREPARATION' ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] ${step === 'PREPARATION' ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>1</span>
                                <span>Paramètres & Données</span>
                            </div>
                            <div className={`flex items-center gap-1.5 font-bold ${step === 'SIMULATION' ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] ${step === 'SIMULATION' ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>2</span>
                                <span>Simulation & Contrôle</span>
                            </div>
                            <div className={`flex items-center gap-1.5 font-bold ${step === 'VALIDE' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                <span className={`w-5 h-5 rounded-full flex items-center justify-center text-[10px] ${step === 'VALIDE' ? 'bg-[var(--color-success-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>3</span>
                                <span>Validation & Reçu</span>
                            </div>
                        </div>

                        {/* Synthèse Préremplie des Droits Reconnus (Backend-driven) */}
                        <div className="bg-[var(--color-bg-subtle)]/40 p-3 border border-[var(--color-border-subtle)] grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
                            <div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">Matricule & Poste</span>
                                <strong className="text-[var(--color-fg)]">{prepData.matricule}</strong>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block truncate">{prepData.intitulePoste}</span>
                            </div>
                            <div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">Ancienneté & Embauche</span>
                                <strong className="text-[var(--color-fg)]">{prepData.ancienneteAnnees} an(s) {prepData.ancienneteMois} mois</strong>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">{fmtDate(prepData.dateEmbauche)}</span>
                            </div>
                            <div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">Salaire Base Contractuel</span>
                                <strong className="font-mono text-[var(--color-fg)]">{formatMoneyAr(prepData.salaireBase)}</strong>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-mono block">({formatMoneyAr(prepData.tauxHoraire)}/h)</span>
                            </div>
                            <div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">Solde Congés & Taux congés</span>
                                <strong className="text-[var(--color-fg)]">{prepData.soldeCongesRestant} jours</strong>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-mono block">({formatMoneyAr(prepData.tauxJournalierConge)}/j)</span>
                            </div>
                        </div>

                        {/* VUE 1 : PRÉPARATION & SAISIE DES PARAMÈTRES */}
                        {step === 'PREPARATION' && (
                            <form onSubmit={handleSimuler} className="space-y-4">
                                {modePreavisInitial && (
                                    <Alert
                                        type={modePreavisInitial === 'DISPENSE_PAYEE' ? 'warning' : 'info'}
                                        title="Préavis repris de la rupture"
                                        message={modePreavisInitial === 'DISPENSE_PAYEE'
                                            ? `Dispense payée · une indemnité compensatrice estimée à ${formatMoneyAr(form.indemnitePreavis || 0)} est incluse. Vérifiez le montant avant simulation.`
                                            : modePreavisInitial === 'EFFECTUE'
                                                ? 'Préavis travaillé · aucune indemnité compensatrice n’est ajoutée au STC. Le salaire du préavis reste versé normalement.'
                                                : 'Dispense demandée par le salarié · aucune indemnité compensatrice de préavis n’est ajoutée au STC.'}
                                    />
                                )}

                                {dateDepartFuture && (
                                    <Alert
                                        type="warning"
                                        title="Départ planifié"
                                        message="Le calcul peut être simulé dès maintenant. Le reçu officiel et le règlement ne pourront être validés qu’à partir de la date de départ effective."
                                    />
                                )}

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                    <Input
                                        type="date"
                                        label="Date de Départ Effective"
                                        hint="Date utilisée pour calculer le salaire final, les congés et l’ancienneté."
                                        required
                                        value={form.dateDepart}
                                        onChange={(e) => handleFormChange('dateDepart', e.target.value)}
                                    />
                                    <Select
                                        label="Motif Légal de Rupture"
                                        required
                                        value={form.motifRupture}
                                        onChange={(e) => handleFormChange('motifRupture', e.target.value)}
                                        options={MOTIFS_RUPTURE}
                                    />
                                </div>

                                <div className={`grid grid-cols-1 ${modePreavisInitial && modePreavisInitial !== 'DISPENSE_PAYEE' ? 'sm:grid-cols-2' : 'sm:grid-cols-3'} gap-3`}>
                                    <Input
                                        type="number"
                                        label="Jours Travaillés Dernier Mois"
                                        step="0.5"
                                        placeholder={`Estimé: ${prepData.joursTravaillesEstimes || 0}`}
                                        hint={`Estimation automatique : ${prepData.joursTravaillesEstimes || 0} jour(s). Ajustez selon les jours réellement travaillés.`}
                                        value={form.joursTravaillesDernierMois}
                                        onChange={(e) => handleFormChange('joursTravaillesDernierMois', e.target.value)}
                                    />
                                    {modePreavisInitial === 'DISPENSE_PAYEE' && (
                                        <Input
                                            type="number"
                                            label="Durée de préavis indemnisée (mois)"
                                            min="0"
                                            max="6"
                                            step="0.5"
                                            hint="Reprise de la rupture. Vérifiez la durée indemnisée."
                                            value={form.moisPreavis}
                                            onChange={(e) => handleFormChange('moisPreavis', e.target.value)}
                                        />
                                    )}
                                    <Select
                                        label="Mode de Règlement"
                                        required
                                        value={form.modeReglement}
                                        onChange={(e) => handleFormChange('modeReglement', e.target.value)}
                                        options={MODES_REGLEMENT}
                                    />
                                </div>

                                {/* Ajustements Exceptionnels & Surcharges Autorégulées */}
                                <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-3">
                                    <h4 className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        Indemnités et Déductions Spécifiques
                                    </h4>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        <MoneyInput
                                            label="Indemnité de licenciement (Ar)"
                                            placeholder={`Suggéré: ${formatMoneyAr(prepData.indemniteLicenciementEstimee)}`}
                                            hint="Montant estimé automatiquement. Ajustez uniquement si une régularisation est nécessaire."
                                            value={form.indemniteLicenciement}
                                            onValueChange={(value) => handleFormChange('indemniteLicenciement', value)}
                                        />
                                        {(!modePreavisInitial || modePreavisInitial === 'DISPENSE_PAYEE') && (
                                            <MoneyInput
                                                label={modePreavisInitial === 'DISPENSE_PAYEE'
                                                    ? 'Indemnité compensatrice de préavis (Ar)'
                                                    : 'Indemnité compensatrice de préavis (Ar)'}
                                                placeholder={`Suggéré: ${formatMoneyAr(prepData.indemnitePreavisEstimee)}`}
                                                hint={modePreavisInitial === 'DISPENSE_PAYEE'
                                                    ? 'Montant transmis depuis la rupture. Ajustez-le si nécessaire.'
                                                    : 'Montant estimé selon le motif. Vérifiez ou ajustez avant simulation.'}
                                                value={form.indemnitePreavis}
                                                onValueChange={(value) => handleFormChange('indemnitePreavis', value)}
                                            />
                                        )}
                                    </div>

                                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                        <div className="space-y-1">
                                            <MoneyInput
                                                label="Autres Indemnités / Primes départ (Ar)"
                                                placeholder="0 Ar"
                                                value={form.autresIndemnites}
                                                onValueChange={(value) => handleFormChange('autresIndemnites', value)}
                                            />
                                            {Number(form.autresIndemnites) > 0 && (
                                                <Input
                                                    type="text"
                                                    placeholder="Motif de l'indemnité exceptionnelle..."
                                                    value={form.motifAutresIndemnites}
                                                    onChange={(e) => handleFormChange('motifAutresIndemnites', e.target.value)}
                                                />
                                            )}
                                        </div>

                                        <div className="space-y-1">
                                            <MoneyInput
                                                label="Autres Déductions / Retenues diverses (Ar)"
                                                placeholder="0 Ar"
                                                value={form.autresDeductions}
                                                onValueChange={(value) => handleFormChange('autresDeductions', value)}
                                            />
                                            {Number(form.autresDeductions) > 0 && (
                                                <Input
                                                    type="text"
                                                    placeholder="Motif de la déduction..."
                                                    value={form.motifAutresDeductions}
                                                    onChange={(e) => handleFormChange('motifAutresDeductions', e.target.value)}
                                                />
                                            )}
                                        </div>
                                    </div>
                                </div>

                                {/* Prêts et Avances à apurer automatiquement */}
                                {(Number(prepData.capitalRestantPrets) > 0 || Number(prepData.avancesEnAttente) > 0) && (
                                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs text-[var(--color-fg)] space-y-1">
                                        <p className="font-bold text-[var(--color-warning-600)] flex items-center gap-1.5">
                                            <AlertTriangle size={14} /> Engagements Financiers en cours détectés
                                        </p>
                                        <p className="text-[11px] text-[var(--color-fg-muted)]">
                                            Lors de la validation définitive du STC, le système apurera automatiquement :
                                        </p>
                                        <ul className="list-disc pl-5 space-y-0.5 text-[11px] font-mono">
                                            {Number(prepData.capitalRestantPrets) > 0 && (
                                                <li>Prêt(s) d'entreprise en cours : <strong>{formatMoneyAr(prepData.capitalRestantPrets)}</strong> (passera en SOLDE)</li>
                                            )}
                                            {Number(prepData.avancesEnAttente) > 0 && (
                                                <li>Avance(s) et acompte(s) non imputés : <strong>{formatMoneyAr(prepData.avancesEnAttente)}</strong> (passera en REGLE)</li>
                                            )}
                                        </ul>
                                    </div>
                                )}
                            </form>
                        )}

                        {/* VUE 2 : RÉSULTAT DE LA SIMULATION DU CALCUL */}
                        {(step === 'SIMULATION' || step === 'VALIDE') && calculResult && (
                            <div className="space-y-3">
                                {step === 'SIMULATION' && (
                                    <Alert
                                        type="info"
                                        title="Mode Simulation"
                                        message="Ce résultat est purement indicatif. Aucun enregistrement n'a été créé et aucun prêt n'a été clôturé. Cliquez sur 'Valider et Émettre le Reçu Officiel' pour officialiser le solde de tout compte."
                                    />
                                )}

                                {modePreavisInitial && (
                                    <Alert
                                        type={modePreavisInitial === 'DISPENSE_PAYEE' ? 'warning' : 'info'}
                                        title="Contrôle du préavis"
                                        message={modePreavisInitial === 'DISPENSE_PAYEE'
                                            ? `Vérifiez la ligne d’indemnité compensatrice : ${formatMoneyAr(calculResult.indemnitePreavis || 0)}.`
                                            : `Aucune indemnité compensatrice ne doit apparaître pour ce préavis. Montant calculé : ${formatMoneyAr(calculResult.indemnitePreavis || 0)}.`}
                                    />
                                )}

                                {dateDepartFuture && (
                                    <Alert
                                        type="warning"
                                        title="Validation différée"
                                        message="La simulation est disponible. La validation officielle du STC sera activée à la date de départ effective."
                                    />
                                )}

                                {step === 'VALIDE' && (
                                    <Alert
                                        type="success"
                                        title="Solde de Tout Compte Validé avec Succès"
                                        message={`Le reçu numéroté ${calculResult.numeroRecu} a été émis. Les prêts et avances ont été soldés conformément à la législation en vigueur.`}
                                    />
                                )}

                                {/* Tableau décomposé du calcul STC */}
                                <Table density="compact" bordered className="overflow-hidden">
                                    <Thead>
                                        <Tr>
                                            <Th>Élément de Liquidation STC</Th>
                                            <Th className="text-right">Montant</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody className="font-mono text-[11px]">
                                        {Number(calculResult.salaireProrata) > 0 && (
                                            <Tr>
                                                <Td className="font-sans">
                                                    Salaire du dernier mois (Prorata {calculResult.joursTravaillesDernierMois || 0} j)
                                                </Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.salaireProrata)}</Td>
                                            </Tr>
                                        )}
                                        <Tr>
                                            <Td className="font-sans">
                                                Indemnité de Congés Payés (ICCP)
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block font-normal">
                                                    {calculResult.soldeCongesRestant} jours × {formatMoneyAr(calculResult.tauxJournalierConge)}
                                                </span>
                                            </Td>
                                            <Td className="text-right">{formatMoneyAr(calculResult.indemniteCongesPayes)}</Td>
                                        </Tr>
                                        {Number(calculResult.indemnitePreavis) > 0 && (
                                            <Tr>
                                                <Td className="font-sans">Indemnité de Préavis ({calculResult.moisPreavis} mois)</Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.indemnitePreavis)}</Td>
                                            </Tr>
                                        )}
                                        {Number(calculResult.indemniteLicenciement) > 0 && (
                                            <Tr>
                                                <Td className="font-sans">Indemnité de Licenciement</Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.indemniteLicenciement)}</Td>
                                            </Tr>
                                        )}
                                        {Number(calculResult.autresIndemnites) > 0 && (
                                            <Tr>
                                                <Td className="font-sans">Autres indemnités ({calculResult.motifAutresIndemnites || 'Complément'})</Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.autresIndemnites)}</Td>
                                            </Tr>
                                        )}
                                        <Tr className="bg-[var(--color-bg-subtle)]/50 font-bold">
                                            <Td className="font-sans uppercase">TOTAL BRUT DU STC</Td>
                                            <Td className="text-right">{formatMoneyAr(calculResult.totalBrutStc ?? calculResult.totalBrut)}</Td>
                                        </Tr>
                                        <Tr>
                                            <Td className="font-sans text-[var(--color-fg-muted)]">- Cotisations Sociales (CNaPS + Santé)</Td>
                                            <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(calculResult.totalCotisationsSociales)}</Td>
                                        </Tr>
                                        <Tr>
                                            <Td className="font-sans text-[var(--color-fg-muted)]">- Retenue Fiscale IRSA</Td>
                                            <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(calculResult.irsaNet)}</Td>
                                        </Tr>
                                        {Number(calculResult.soldePretsDeduit) > 0 && (
                                            <Tr>
                                                <Td className="font-sans text-[var(--color-fg-muted)]">- Apurement Prêts d'Entreprise</Td>
                                                <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(calculResult.soldePretsDeduit)}</Td>
                                            </Tr>
                                        )}
                                        {Number(calculResult.soldeAvancesDeduit) > 0 && (
                                            <Tr>
                                                <Td className="font-sans text-[var(--color-fg-muted)]">- Solde Avances / Acomptes en attente</Td>
                                                <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(calculResult.soldeAvancesDeduit)}</Td>
                                            </Tr>
                                        )}
                                        {Number(calculResult.autresDeductions) > 0 && (
                                            <Tr>
                                                <Td className="font-sans text-[var(--color-fg-muted)]">- Autres Déductions ({calculResult.motifAutresDeductions || 'Retenues'})</Td>
                                                <Td className="text-right text-[var(--color-danger-600)]">-{formatMoneyAr(calculResult.autresDeductions)}</Td>
                                            </Tr>
                                        )}
                                    </Tbody>
                                    <Tfoot>
                                        <Tr className="bg-[var(--color-ink-950)] text-[var(--color-on-color)] font-bold">
                                            <Td className="p-3 font-sans uppercase tracking-wider text-xs text-inherit">
                                                NET À PAYER AU SALARIÉ
                                                <span className="text-[10px] text-[var(--color-ink-200)] block font-normal">
                                                    Arrondi légal à la dizaine d'Ariary
                                                </span>
                                            </Td>
                                            <Td className="p-3 text-right text-lg font-mono font-black text-inherit">
                                                {formatMoneyAr(calculResult.netAPayerStc ?? calculResult.netAPayer)}
                                            </Td>
                                        </Tr>
                                    </Tfoot>
                                </Table>
                            </div>
                        )}
                    </div>
                )}
            </Modal>

            {/* Modal Quittance Libératoire Imprimable */}
            {calculResult?.idStc && (
                <StcRecuModal
                    isOpen={showRecuModal}
                    onClose={() => setShowRecuModal(false)}
                    idStc={calculResult.idStc}
                />
            )}

            <ConfirmDialog
                isOpen={showConfirmValider}
                onClose={() => setShowConfirmValider(false)}
                onConfirm={handleValiderDefinitivement}
                title="Validation définitive du STC"
                message="ATTENTION : Cette action est irréversible. Le STC sera définitivement validé, un reçu officiel numéroté sera émis, et les éventuels prêts et avances du salarié seront intégralement soldés dans le GRH."
                confirmLabel="Valider définitivement"
                variant="danger"
                loading={submitting}
            />
        </>
    );
}
