import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
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
import SearchSelect from '@/components/ui/forms/SearchSelect';
import contratService from '@/features/employees/api/contratService';
import { optionsMotifsSortie } from '@/features/employees/utils/motifsSortie';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { Table, Thead, Th, Tbody, Tr, Td, Tfoot } from '@/components/ui/data-display/Table';
import { formatMoneyAr, formatLeaveRateAr } from '@/utils/moneyUtils';
import { formatStcAnciennete } from '@/features/payroll/utils/stcDisplay';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';
import StcRecuModal from './StcRecuModal';

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
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    } catch {
        return '';
    }
};

/**
 * Modale de Préparation, Simulation et Émission du Solde de Tout Compte (STC).
 * Workflow : PRÉPARATION -> SIMULATION, puis validation définitive si autorisée.
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
    simulationOnly = false,
    onStcValide,
}) {
    // État local : étape, préparation, calcul, formulaire et reçu.
    // Étape UI courante : 'PREPARATION' | 'SIMULATION' | 'VALIDE'
    const [step, setStep] = useState('PREPARATION');
    const [loadingPrep, setLoadingPrep] = useState(false);
    const [refreshingPrep, setRefreshingPrep] = useState(false);
    const [prepSelectionKey, setPrepSelectionKey] = useState('');
    const [submitting, setSubmitting] = useState(false);
    const [errorMsg, setErrorMsg] = useState(null);

    // Données préparatoires renvoyées par le backend
    const [prepData, setPrepData] = useState(null);

    // Résultat du calcul de simulation ou validation
    const [calculResult, setCalculResult] = useState(null);

    // Contrôle d'affichage du décompte imprimable
    const [showRecuModal, setShowRecuModal] = useState(false);
    const [showConfirmValider, setShowConfirmValider] = useState(false);
    const [dateReglement, setDateReglement] = useState('');
    const [referenceReglement, setReferenceReglement] = useState('');
    const [montantRegle, setMontantRegle] = useState('');
    const [partBulletinMensuel, setPartBulletinMensuel] = useState('');
    const [modeReglement, setModeReglement] = useState('VIREMENT');
    const [paymentFile, setPaymentFile] = useState(null);
    const [motifsSortie, setMotifsSortie] = useState([]);

    useEffect(() => {
        if (!isOpen) return;
        contratService.getMotifsSortie()
            .then(data => setMotifsSortie(optionsMotifsSortie(data)))
            .catch(() => setErrorMsg('Impossible de charger les motifs de sortie. Vérifiez le référentiel RH.'));
    }, [isOpen]);

    // Formulaire des paramètres utilisateur (StcCalculRequest)
    const [form, setForm] = useState({
        dateDepart: '',
        motifRupture: 'DEMISSION',
        moisPreavis: '0',
        joursTravaillesDernierMois: '',
        justificationSalaireDernierMois: '',
        montantBulletinDejaPaye: '',
        datePaiementBulletin: '',
        referencePaiementBulletin: '',
        indemnitePreavis: '',
        indemniteLicenciement: '',
        remunerationReferenceLicenciement: '',
        justificationIndemnites: '',
        autresIndemnites: '',
        motifAutresIndemnites: '',
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
            const dateRef = fmtDateInput(dateDepartInitiale) || getTodayDateString();
            const data = await stcService.preparerStc(idEmploye, dateRef, motifInitial);
            setPrepData(data);
            setPrepSelectionKey(`${dateRef}|${data.motifRupture || motifInitial || 'DEMISSION'}`);

            // Pré-remplissage 100% backend-driven
            setForm({
                dateDepart: fmtDateInput(data.dateDepart || dateRef),
                motifRupture: data.motifRupture || motifInitial || 'DEMISSION',
                moisPreavis: modePreavisInitial
                    ? (modePreavisInitial === 'DISPENSE_PAYEE' ? String(moisPreavisInitial ?? 0) : '0')
                    : (moisPreavisInitial !== null ? String(moisPreavisInitial) : String(data.moisPreavisSuggere ?? '0')),
                joursTravaillesDernierMois: data.joursTravaillesEstimes != null ? String(data.joursTravaillesEstimes) : '',
                justificationSalaireDernierMois: '',
                montantBulletinDejaPaye: '',
                datePaiementBulletin: '',
                referencePaiementBulletin: '',
                indemnitePreavis: modePreavisInitial
                    ? (modePreavisInitial === 'DISPENSE_PAYEE'
                        ? String(indemnitePreavisInitiale ?? data.indemnitePreavisEstimee ?? 0)
                        : '0')
                    : ((indemnitePreavisInitiale !== null && Number(indemnitePreavisInitiale) >= 0)
                        ? String(indemnitePreavisInitiale)
                        : (data.indemnitePreavisEstimee != null && Number(data.indemnitePreavisEstimee) > 0 ? String(data.indemnitePreavisEstimee) : '')),
                indemniteLicenciement: '',
                remunerationReferenceLicenciement: '',
                justificationIndemnites: '',
                autresIndemnites: '',
                motifAutresIndemnites: '',
                modeReglement: 'VIREMENT',
                commentaire: '',
                desactiverEmploye: false,
            });
            setStep('PREPARATION');
            setCalculResult(null);
            setDateReglement('');
            setReferenceReglement('');
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
            setPrepSelectionKey('');
            setCalculResult(null);
            setErrorMsg(null);
            setStep('PREPARATION');
        }
    }, [isOpen, idEmploye, chargerDonneesPreparatoires]);

    // Le contexte de paie dépend de la date et du motif affichés, pas seulement de l'ouverture.
    useEffect(() => {
        if (!isOpen || !prepData || !form.dateDepart || !form.motifRupture) return;
        const selectionKey = `${fmtDateInput(form.dateDepart)}|${form.motifRupture}`;
        if (selectionKey === prepSelectionKey) return;

        let active = true;
        const timer = window.setTimeout(async () => {
            setRefreshingPrep(true);
            setErrorMsg(null);
            try {
                const data = await stcService.preparerStc(idEmploye, fmtDateInput(form.dateDepart), form.motifRupture);
                if (!active) return;
                setPrepData(data);
                setPrepSelectionKey(selectionKey);
                setForm(prev => ({
                    ...prev,
                    joursTravaillesDernierMois: data.joursTravaillesEstimes != null
                        ? String(data.joursTravaillesEstimes) : '',
                }));
            } catch (err) {
                if (active) setErrorMsg(getErrorMessage(err, 'Impossible de mettre à jour le contexte de paie pour cette date.'));
            } finally {
                if (active) setRefreshingPrep(false);
            }
        }, 250);
        return () => {
            active = false;
            window.clearTimeout(timer);
        };
    }, [isOpen, idEmploye, prepData, prepSelectionKey, form.dateDepart, form.motifRupture]);

    const handleFormChange = (field, val) => {
        setForm(prev => ({
            ...prev,
            [field]: val,
            ...(field === 'dateDepart' ? { joursTravaillesDernierMois: '', justificationSalaireDernierMois: '',
                montantBulletinDejaPaye: '', datePaiementBulletin: '', referencePaiementBulletin: '' } : {}),
        }));
    };

    const buildPayload = (simulationMode = true) => {
        return {
            idEmploye: Number(idEmploye),
            // La période est résolue par le serveur à la date de départ courante.
            idPeriodePaie: null,
            dateDepart: fmtDateInput(form.dateDepart),
            motifRupture: form.motifRupture,
            joursTravaillesDernierMois: form.joursTravaillesDernierMois ? Number(form.joursTravaillesDernierMois) : null,
            justificationSalaireDernierMois: form.justificationSalaireDernierMois || null,
            montantBulletinDejaPaye: form.montantBulletinDejaPaye === '' ? 0 : Number(form.montantBulletinDejaPaye),
            datePaiementBulletin: form.datePaiementBulletin || null,
            referencePaiementBulletin: form.referencePaiementBulletin || null,
            moisPreavis: modePreavisInitial && modePreavisInitial !== 'DISPENSE_PAYEE'
                ? 0
                : (form.moisPreavis !== '' ? Number(form.moisPreavis) : 0),
            indemnitePreavis: modePreavisInitial && modePreavisInitial !== 'DISPENSE_PAYEE'
                ? 0
                : (form.indemnitePreavis !== '' ? Number(form.indemnitePreavis) : null),
            indemniteLicenciement: form.indemniteLicenciement ? Number(form.indemniteLicenciement) : null,
            remunerationReferenceLicenciement: form.remunerationReferenceLicenciement ? Number(form.remunerationReferenceLicenciement) : null,
            justificationIndemnites: form.justificationIndemnites || null,
            autresIndemnites: form.autresIndemnites ? Number(form.autresIndemnites) : null,
            motifAutresIndemnites: form.motifAutresIndemnites || null,
            modeReglement: form.modeReglement || 'VIREMENT',
            commentaire: form.commentaire || null,
            simulation: simulationMode,
            desactiverEmploye: Boolean(form.desactiverEmploye),
        };
    };

    // 1. Déclenche la simulation
    const handleSimuler = async (e) => {
        if (e) e.preventDefault();
        if (!prepData || prepSelectionKey !== `${fmtDateInput(form.dateDepart)}|${form.motifRupture}`) {
            setErrorMsg('Attendez la mise à jour du contexte de paie pour la date choisie.');
            return;
        }
        if (!form.dateDepart) {
            setErrorMsg('La date de départ est obligatoire.');
            return;
        }
        if (!motifsSortie.some(m => m.value === form.motifRupture)) {
            setErrorMsg('Choisissez un motif de sortie actif dans le référentiel RH.');
            return;
        }

        setSubmitting(true);
        setErrorMsg(null);
        try {
            const preparationCourante = await stcService.preparerStc(idEmploye, fmtDateInput(form.dateDepart), form.motifRupture);
            setPrepData(preparationCourante);
            if (preparationCourante.idBulletinMensuel
                && !['VALIDE', 'CLOTURE'].includes(preparationCourante.statutBulletinMensuel)) {
                setErrorMsg('Le bulletin mensuel est encore calculé. Validez la paie d’août, puis actualisez cette simulation.');
                return;
            }
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

    // 2. Valide le calcul et émet le décompte numéroté
    const handleValiderDefinitivement = async () => {
        if (simulationOnly) return;
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

    const handleEnregistrerReglement = async () => {
        const zeroNet = Number(calculResult?.netAPayerStc || 0) === 0;
        if (!dateReglement || !referenceReglement.trim() || (!zeroNet && (!paymentFile || Number(montantRegle) <= 0))) {
            setErrorMsg('Le montant, la date, la référence et la preuve réelle du paiement sont obligatoires.');
            return;
        }
        setSubmitting(true);
        setErrorMsg(null);
        try {
            const situation = await stcService.enregistrerReglement(calculResult.idStc, {
                dateReglement,
                referenceReglement: referenceReglement.trim(),
                montantRegle: zeroNet ? 0 : montantRegle,
                modeReglement,
                partBulletinMensuel: zeroNet ? 0 : partBulletinMensuel || 0,
            }, paymentFile);
            const regle = { ...calculResult, statut: situation?.statutStc || 'VALIDE' };
            setCalculResult(regle);
            onStcValide?.(regle);
        } catch (err) {
            setErrorMsg(getErrorMessage(err, 'Impossible de constater le règlement du STC.'));
        } finally {
            setSubmitting(false);
        }
    };

    if (!isOpen) return null;

    const modalTitle = simulationOnly && step !== 'VALIDE'
        ? `Aperçu prévisionnel du STC — ${prepData?.nomComplet || nomEmploye || ''}`
        : step === 'VALIDE'
        ? `STC validé — ${calculResult?.numeroRecu || 'Décompte émis'}`
        : step === 'SIMULATION'
        ? `Simulation STC — ${prepData?.nomComplet || nomEmploye || ''}`
        : `Préparation Solde de Tout Compte — ${prepData?.nomComplet || nomEmploye || ''}`;
    const dateDepartFuture = Boolean(form.dateDepart && form.dateDepart > getTodayDateString());
    const contexteAJour = Boolean(prepData && prepSelectionKey === `${fmtDateInput(form.dateDepart)}|${form.motifRupture}`);

    // Rendu
    return (
        <>
            <Modal
                isOpen={isOpen}
                onClose={step === 'VALIDE' ? onClose : submitting ? () => {} : onClose}
                title={modalTitle}
                icon={<Calculator size={18} className="text-[var(--color-brand-600)]" />}
                size={simulationOnly ? '3xl' : '4xl'}
                closeOnBackdrop={false}
                isDirty={!simulationOnly && (step === 'SIMULATION' || Boolean(form.indemniteLicenciement || form.autresIndemnites || form.justificationIndemnites))}
                dirtyWarningTitle="Calcul de STC en cours"
                dirtyWarningMessage="Une préparation ou simulation de Solde de Tout Compte est en cours. Si vous quittez maintenant, les paramètres calculés seront réinitialisés. Voulez-vous vraiment quitter ?"
                footer={
                    <div className="flex justify-between items-center gap-3 w-full">
                        {step === 'PREPARATION' && (
                            <>
                                <Button type="button" variant="outline" size="sm" onClick={onClose} disabled={submitting}>
                                    {simulationOnly ? 'Fermer' : 'Annuler'}
                                </Button>
                                {errorMsg && <span role="alert" className="min-w-0 flex-1 text-xs text-[var(--color-danger-700)]">{errorMsg}</span>}
                                <Button
                                    type="button"
                                    variant="primary"
                                    size="sm"
                                    onClick={handleSimuler}
                                    loading={submitting}
                                    disabled={!contexteAJour || refreshingPrep}
                                    iconRight={<ArrowRight size={15} />}
                                >
                                    {simulationOnly ? 'Calculer la simulation' : 'Simuler le STC'}
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
                                {simulationOnly && <span className="text-xs text-[var(--color-fg-muted)]">Aperçu · rien n’est enregistré</span>}
                                <Button
                                    type="button"
                                    variant={simulationOnly ? 'primary' : 'danger'}
                                    size="sm"
                                    onClick={simulationOnly ? onClose : () => setShowConfirmValider(true)}
                                    loading={!simulationOnly && submitting}
                                    disabled={!simulationOnly && dateDepartFuture}
                                    iconLeft={simulationOnly ? undefined : <FileCheck size={15} />}
                                >
                                    {simulationOnly ? 'Fermer' : 'Valider le calcul et le bulletin'}
                                </Button>
                            </>
                        )}

                        {step === 'VALIDE' && (
                            <>
                                <div className="flex items-center gap-1.5 text-xs text-[var(--color-success-600)] font-semibold">
                                    <CheckCircle2 size={16} /> Décompte {calculResult?.numeroRecu} enregistré
                                </div>
                                <div className="flex gap-2">
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setShowRecuModal(true)}
                                        iconLeft={<FileCheck size={15} />}
                                    >
                                        Consulter le décompte
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
                    <div className="stc-flow space-y-4">
                        <p className="stc-flow-intro">
                            {simulationOnly
                                ? 'Estimation uniquement · aucun STC ni paiement enregistré.'
                                : 'Préparez les données, contrôlez le calcul, puis validez le décompte.'}
                        </p>
                        {/* Indicateur d'étapes */}
                        <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-3 text-xs">
                            <div className={`flex items-center gap-1.5 font-bold ${step === 'PREPARATION' ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                <span className={`w-5 h-5 rounded-none flex items-center justify-center text-[10px] ${step === 'PREPARATION' ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>1</span>
                                <span>{simulationOnly ? 'Paramètres' : 'Paramètres & données'}</span>
                            </div>
                            <div className={`flex items-center gap-1.5 font-bold ${step === 'SIMULATION' ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                <span className={`w-5 h-5 rounded-none flex items-center justify-center text-[10px] ${step === 'SIMULATION' ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>2</span>
                                <span>{simulationOnly ? 'Résultat' : 'Simulation & contrôle'}</span>
                            </div>
                            {!simulationOnly && (
                                <div className={`flex items-center gap-1.5 font-bold ${step === 'VALIDE' ? 'text-[var(--color-success-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                    <span className={`w-5 h-5 rounded-none flex items-center justify-center text-[10px] ${step === 'VALIDE' ? 'bg-[var(--color-success-600)] text-[var(--color-on-color)]' : 'bg-[var(--color-bg-subtle)]'}`}>3</span>
                                    <span>Validation & décompte</span>
                                </div>
                            )}
                        </div>

                        {/* Synthèse Préremplie des Droits Reconnus (Backend-driven) */}
                        <div className="stc-context-grid">
                            <div>
                                <span className="stc-context-label">Collaborateur</span>
                                <strong className="text-[var(--color-fg)]">{prepData.matricule}</strong>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block truncate">{prepData.intitulePoste}</span>
                            </div>
                            <div>
                                <span className="stc-context-label">Ancienneté</span>
                                <strong className="text-[var(--color-fg)]">{formatStcAnciennete(prepData.ancienneteAnnees, prepData.ancienneteMois)}</strong>
                                <span className="text-[11px] text-[var(--color-fg-muted)] block">{fmtDate(prepData.dateEmbauche)}</span>
                            </div>
                            <div>
                                <span className="stc-context-label">Salaire de base</span>
                                <strong className="font-data text-[var(--color-fg)]">{formatMoneyAr(prepData.salaireBase)}</strong>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-data block">({formatMoneyAr(prepData.tauxHoraire)}/h)</span>
                            </div>
                            <div>
                                <span className="stc-context-label">Congés restants</span>
                                <strong className="text-[var(--color-fg)]">{prepData.soldeCongesRestant} jours</strong>
                                <span className="text-[10px] text-[var(--color-fg-muted)] font-data block">({formatLeaveRateAr(prepData.tauxJournalierConge)}/j)</span>
                            </div>
                        </div>
                        {!contexteAJour && <p className="text-xs text-[var(--color-brand-700)]" role="status">Mise à jour du contexte de paie pour la date choisie…</p>}
                        {contexteAJour && <div className="flex flex-wrap gap-x-5 gap-y-1 border-b border-[var(--color-border-subtle)] pb-3 text-xs text-[var(--color-fg-muted)]">
                            <span>Période : <strong className="text-[var(--color-fg)]">{prepData.codePeriodePaie || 'Non déterminée'}</strong></span>
                            <span>Pointage : <strong className="text-[var(--color-fg)]">{prepData.sourcePointage === 'POINTAGE_REEL' ? 'Données disponibles' : 'Absent ou incomplet'}</strong></span>
                            <span>Bulletin : <strong className="text-[var(--color-fg)]">{prepData.statutBulletinMensuel || 'Non calculé'}</strong></span>
                        </div>}
                        {contexteAJour && prepData.codePeriodePaie === 'HORS_CALENDRIER' && <Alert type="warning"
                            title="Aucune période de paie ne couvre cette date"
                            message="Le prorata part du 1er du mois civil. Vérifiez le cycle 26–25 avant toute validation." />}
                        {contexteAJour && prepData.idBulletinMensuel && !['VALIDE', 'CLOTURE'].includes(prepData.statutBulletinMensuel) && (
                            <Alert type="warning" title="Valider le bulletin avant la simulation STC"
                                message={`Le bulletin #${prepData.idBulletinMensuel} est ${prepData.statutBulletinMensuel?.toLowerCase() || 'en attente'}. Le calcul STC doit reprendre un net définitif. Validez la paie de cette période, puis revenez calculer la simulation.`}
                                action={prepData.idPeriodePaie && <Link className="text-xs font-semibold text-[var(--color-brand-700)] underline" to={`/paie/periodes/${prepData.idPeriodePaie}/validation`}>Ouvrir la validation de la paie</Link>} />
                        )}
                        {!simulationOnly && prepData.controlesApplication?.length > 0 && (
                            <div className="space-y-2" aria-label="Contrôles avant application du STC">
                                {prepData.controlesApplication.map((controle, index) => (
                                    <Alert key={`${index}-${controle}`} type="warning"
                                        title="À contrôler avant application" message={controle} />
                                ))}
                            </div>
                        )}

                        {/* VUE 1 : PRÉPARATION & SAISIE DES PARAMÈTRES */}
                        {step === 'PREPARATION' && (
                            <form onSubmit={handleSimuler} className="space-y-4">
                                {contexteAJour && prepData.sourcePointage !== 'POINTAGE_REEL' && (
                                    <Alert
                                        type={simulationOnly ? 'info' : 'warning'}
                                        title="Pointage absent"
                                        message={prepData.idBulletinMensuel
                                            ? 'Le bulletin mensuel existe déjà. Vérifiez son pointage et ses variables dans la paie avant validation du STC.'
                                            : simulationOnly
                                            ? 'Le salaire est estimé sur les jours sous contrat. Vérifiez absences et primes avant le dossier officiel.'
                                            : 'Aucun pointage disponible pour la période finale. Justifiez obligatoirement les jours rémunérés ci-dessous, puis confirmez les absences et variables dans la paie avant validation.'}
                                    />
                                )}
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
                                        message="Le calcul peut être simulé dès maintenant. Sa validation attendra la date de départ effective."
                                    />
                                )}

                                <section className="stc-form-section" aria-labelledby="stc-depart-title">
                                <div className="stc-section-heading"><span className="stc-section-number">1</span><div><h4 id="stc-depart-title">Départ</h4><p>Définissez la date et le motif du calcul.</p></div></div>
                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                    <Input
                                        type="date"
                                        label={simulationOnly ? 'Date de départ simulée' : 'Date de départ effective'}
                                        hint="Base du salaire, des congés et de l’ancienneté."
                                        required
                                        value={form.dateDepart}
                                        onChange={(e) => handleFormChange('dateDepart', e.target.value)}
                                    />
                                    <SearchSelect
                                        label="Motif de sortie"
                                        required
                                        value={form.motifRupture}
                                        onChange={(val) => handleFormChange('motifRupture', val)}
                                        options={motifsSortie}
                                    />
                                </div>
                                </section>
                                {prepData.idBulletinMensuel && (
                                    <section className="stc-form-section space-y-3" aria-labelledby="stc-paie-title">
                                        <div className="stc-section-heading"><span className="stc-section-number">2</span><div><h4 id="stc-paie-title">Dernière paie</h4><p>Indiquez seulement les sommes déjà versées.</p></div></div>
                                        <p className="text-xs text-[var(--color-fg-muted)]">
                                            Bulletin mensuel #{prepData.idBulletinMensuel} · état {prepData.statutBulletinMensuel} ·
                                            net {formatMoneyAr(prepData.netBulletinMensuel)}. Seul son solde impayé sera ajouté au STC.
                                        </p>
                                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                                            <MoneyInput label="Montant déjà payé (Ar)" hint="Vérifiez la preuve de paiement."
                                                value={form.montantBulletinDejaPaye}
                                                onValueChange={(value) => handleFormChange('montantBulletinDejaPaye', value)} />
                                            <Input type="date" label="Date du paiement"
                                                required={Number(form.montantBulletinDejaPaye) > 0}
                                                value={form.datePaiementBulletin}
                                                onChange={(event) => handleFormChange('datePaiementBulletin', event.target.value)} />
                                            <Input label="Référence du paiement"
                                                required={Number(form.montantBulletinDejaPaye) > 0}
                                                value={form.referencePaiementBulletin}
                                                onChange={(event) => handleFormChange('referencePaiementBulletin', event.target.value)} />
                                        </div>
                                    </section>
                                )}

                                {!prepData.idBulletinMensuel && (
                                    <section className="stc-form-section" aria-labelledby="stc-salaire-title">
                                        <div className="stc-section-heading"><span className="stc-section-number">2</span><div><h4 id="stc-salaire-title">Salaire du dernier mois</h4><p>Vérifiez les jours à rémunérer et indiquez la source utilisée.</p></div></div>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                                            <Input
                                                type="number"
                                                label={simulationOnly ? 'Jours rémunérés estimés (sur 30)' : 'Jours à payer (sur 30)'}
                                                min="0"
                                                max="30"
                                                step="0.5"
                                                placeholder={`Estimation : ${prepData.joursTravaillesEstimes || 0}`}
                                                hint={`Proposition : ${prepData.joursTravaillesEstimes || 0} jours. Ajustez si nécessaire.`}
                                                value={form.joursTravaillesDernierMois}
                                                onChange={(e) => handleFormChange('joursTravaillesDernierMois', e.target.value)}
                                            />
                                            <Input
                                                label="Justificatif des jours rémunérés"
                                                required={!simulationOnly && prepData.sourcePointage !== 'POINTAGE_REEL'}
                                                placeholder="Pointage, absence ou régularisation RH"
                                                hint={prepData.sourcePointage === 'POINTAGE_REEL'
                                                    ? `Pointage disponible : ${prepData.joursPointageReels} j · ${prepData.totalHeuresPointage} h.`
                                                    : 'Requis pour le STC officiel en l’absence de pointage.'}
                                                value={form.justificationSalaireDernierMois}
                                                onChange={(e) => handleFormChange('justificationSalaireDernierMois', e.target.value)}
                                            />
                                        </div>
                                    </section>
                                )}

                                {(!modePreavisInitial || modePreavisInitial === 'DISPENSE_PAYEE') && (
                                    <section className="stc-form-section" aria-labelledby="stc-preavis-title">
                                        <div className="stc-section-heading"><span className="stc-section-number">3</span><div><h4 id="stc-preavis-title">Préavis indemnisé</h4><p>Renseignez le préavis non exécuté après vérification RH.</p></div></div>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                                            <Input
                                                type="number"
                                                label="Durée indemnisée (mois)"
                                                min="0"
                                                max="6"
                                                step="0.5"
                                                value={form.moisPreavis}
                                                onChange={(e) => handleFormChange('moisPreavis', e.target.value)}
                                            />
                                            <MoneyInput
                                                label="Indemnité compensatrice (Ar)"
                                                placeholder="Montant vérifié par la GRH"
                                                hint={modePreavisInitial === 'DISPENSE_PAYEE'
                                                    ? 'Montant repris de la rupture ; ajustez si nécessaire.'
                                                    : 'Montant vérifié du préavis non exécuté.'}
                                                value={form.indemnitePreavis}
                                                onValueChange={(value) => handleFormChange('indemnitePreavis', value)}
                                            />
                                        </div>
                                    </section>
                                )}

                                {!simulationOnly && (
                                    <section className="stc-form-section" aria-labelledby="stc-reglement-title">
                                        <div className="stc-section-heading"><span className="stc-section-number">{!modePreavisInitial || modePreavisInitial === 'DISPENSE_PAYEE' ? '4' : '3'}</span><div><h4 id="stc-reglement-title">Règlement prévu</h4><p>Le paiement effectif sera constaté après validation du décompte.</p></div></div>
                                        <div className="max-w-md">
                                            <SearchSelect label="Mode de règlement" required value={form.modeReglement}
                                                onChange={(val) => handleFormChange('modeReglement', val)} options={MODES_REGLEMENT} />
                                        </div>
                                    </section>
                                )}

                                <details className="stc-details" open={!simulationOnly}>
                                    <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-[var(--color-fg)] marker:text-[var(--color-brand-500)]">
                                        Autres indemnités et primes
                                        <span className="ml-2 text-xs font-normal text-[var(--color-fg-muted)]">À compléter si applicable</span>
                                    </summary>
                                    <div className="space-y-4 border-t border-[var(--color-border-subtle)] p-4">
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                                            <MoneyInput
                                                label="Indemnité de licenciement (Ar)"
                                                placeholder="Montant validé par la GRH"
                                                hint="Saisissez un montant vérifié et documenté."
                                                value={form.indemniteLicenciement}
                                                onValueChange={(value) => handleFormChange('indemniteLicenciement', value)}
                                            />
                                            {(form.motifRupture === 'LICENCIEMENT_ECONOMIQUE' || form.motifRupture === 'CESSATION_ACTIVITE') && (
                                                <MoneyInput
                                                    label="Rémunération de référence (Ar)"
                                                    hint="Salaire et accessoires, hors remboursements de frais."
                                                    value={form.remunerationReferenceLicenciement}
                                                    onValueChange={(value) => handleFormChange('remunerationReferenceLicenciement', value)}
                                                />
                                            )}
                                        </div>
                                        <Input
                                            label="Référence de la décision et du calcul"
                                            hint="À renseigner si une indemnité est saisie."
                                            value={form.justificationIndemnites}
                                            onChange={(e) => handleFormChange('justificationIndemnites', e.target.value)}
                                        />
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 items-start">
                                            <MoneyInput
                                                label="Autres gains ou primes de départ (Ar)"
                                                placeholder="0 Ar"
                                                value={form.autresIndemnites}
                                                onValueChange={(value) => handleFormChange('autresIndemnites', value)}
                                            />
                                            {Number(form.autresIndemnites) > 0 && (
                                                <Input
                                                    label="Motif de ces gains"
                                                    placeholder="Précisez la prime ou le gain"
                                                    value={form.motifAutresIndemnites}
                                                    onChange={(e) => handleFormChange('motifAutresIndemnites', e.target.value)}
                                                />
                                            )}
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)]">Les retenues éventuelles sont traitées séparément, avec leur justificatif, avant la paie de sortie.</p>
                                    </div>
                                </details>

                                {/* Créances à examiner et acomptes à imputer */}
                                {Number(prepData.avancesEnAttente) > 0 && (
                                    <div className="p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 text-xs text-[var(--color-fg)] space-y-1">
                                        <p className="font-bold text-[var(--color-warning-600)] flex items-center gap-1.5">
                                            <AlertTriangle size={14} /> Engagements Financiers en cours détectés
                                        </p>
                                        <p className="text-[11px] text-[var(--color-fg-muted)]">
                                            Acomptes versés et avances pour ce collaborateur :
                                        </p>
                                        <ul className="list-disc pl-5 space-y-0.5 text-[11px] font-data">
                                            <li>Avance(s) et acompte(s) non imputés : <strong>{formatMoneyAr(prepData.avancesEnAttente)}</strong></li>
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
                                        title={simulationOnly ? 'Aperçu de simulation' : 'Simulation'}
                                        message={simulationOnly
                                            ? 'Aucun montant n’est enregistré. Pour poursuivre, ouvrez le dossier de sortie dans le menu Paie.'
                                            : 'Aucun calcul ni paiement n’est enregistré. Vérifiez les éléments avant validation.'}
                                    />
                                )}

                                {step === 'SIMULATION' && <div className="border-l-4 border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)] px-4 py-3">
                                    <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Net estimé à payer</p>
                                    <p className="font-data mt-1 text-2xl font-bold text-[var(--color-fg)]">{formatMoneyAr(calculResult.netAPayerStc ?? calculResult.netAPayer)}</p>
                                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Montant provisoire : contrôlez les rubriques avant d’ouvrir le dossier officiel.</p>
                                </div>}

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
                                        title={simulationOnly ? 'Date de départ prévisionnelle' : 'Validation différée'}
                                        message={simulationOnly
                                            ? 'Le résultat est estimatif pour cette date future.'
                                            : 'La simulation est disponible. La validation officielle du STC sera activée à la date de départ effective.'}
                                    />
                                )}

                                {step === 'VALIDE' && (
                                    <Alert
                                        type="success"
                                        title="Solde de Tout Compte Validé avec Succès"
                                        message={calculResult.statut === 'REGLE'
                                            ? `Le paiement du STC ${calculResult.numeroRecu} a été constaté. La remise des documents de sortie reste à vérifier.`
                                            : `Le calcul ${calculResult.numeroRecu} a été validé. Le paiement et la remise des documents doivent encore être constatés.`}
                                    />
                                )}

                                {step === 'VALIDE' && calculResult.statut !== 'REGLE' && (
                                    <div className="p-3 border border-[var(--color-border)] space-y-3">
                                        <h4 className="font-semibold text-xs">Constater le paiement réellement effectué</h4>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                            <Input type="date" label="Date du règlement" value={dateReglement}
                                                onChange={(e) => setDateReglement(e.target.value)} />
                                            <Input label="Référence du règlement (virement, chèque ou reçu caisse)"
                                                value={referenceReglement}
                                                onChange={(e) => setReferenceReglement(e.target.value)} />
                                            <MoneyInput label="Montant déjà versé (Ar)" min={0.01} value={montantRegle} onValueChange={value => { setMontantRegle(value); setPartBulletinMensuel(Math.min(Number(value || 0), Number(calculResult?.soldeBulletinARegler || 0))); }} />
                                            <Select label="Mode de règlement" value={modeReglement} onChange={setModeReglement} options={[{ value: 'VIREMENT', label: 'Virement' }, { value: 'CHEQUE', label: 'Chèque' }, { value: 'ESPECES', label: 'Espèces' }, { value: 'MOBILE_MONEY', label: 'Mobile money' }]} />
                                            {Number(calculResult?.soldeBulletinARegler || 0) > 0 && <MoneyInput label="Part du bulletin mensuel (Ar)" min={0} max={Math.min(Number(montantRegle || 0), Number(calculResult.soldeBulletinARegler))} value={partBulletinMensuel} onValueChange={setPartBulletinMensuel} />}
                                            <Input label="Preuve réelle du paiement" type="file" required accept=".pdf,.png,.jpg,.jpeg" onChange={e => setPaymentFile(e.target.files?.[0] || null)} />
                                        </div>
                                        <div className="flex flex-wrap items-center justify-between gap-2">
                                            <Button type="button" variant="primary" size="sm" loading={submitting}
                                                onClick={handleEnregistrerReglement}>Enregistrer le règlement</Button>
                                            <a href="/paie/dossiers-sortie" className="text-xs text-[var(--color-brand-600)] hover:underline">
                                                Suivi complet avec pièce jointe via le dossier de sortie &rarr;
                                            </a>
                                        </div>
                                    </div>
                                )}

                                {/* Tableau décomposé du calcul STC */}
                                <Table density="compact" bordered rowHover={false} className="overflow-hidden">
                                    <Thead>
                                        <Tr>
                                            <Th>Élément de Liquidation STC</Th>
                                            <Th className="text-right">Montant</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody className="font-data text-[11px]">
                                        {Number(calculResult.salaireProrata) > 0 && (
                                            <Tr>
                                                <Td className="font-sans">
                                                    Salaire du dernier mois ({calculResult.joursTravaillesDernierMois || 0}/30)
                                                </Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.salaireProrata)}</Td>
                                            </Tr>
                                        )}
                                        <Tr>
                                            <Td className="font-sans">
                                                Indemnité de Congés Payés (ICCP)
                                                <span className="text-[10px] text-[var(--color-fg-muted)] block font-normal">
                                                    {calculResult.soldeCongesRestant} jours · somme des valeurs restantes des lots
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
                                        {calculResult.idBulletinMensuel && (
                                            <Tr>
                                                <Td className="font-sans">+ Solde du bulletin mensuel #{calculResult.idBulletinMensuel}
                                                    <span className="text-[10px] text-[var(--color-fg-muted)] block font-normal">
                                                        Net {formatMoneyAr(calculResult.netBulletinMensuel)} · déjà payé {formatMoneyAr(calculResult.montantBulletinDejaPaye)}
                                                    </span>
                                                </Td>
                                                <Td className="text-right">{formatMoneyAr(calculResult.soldeBulletinARegler)}</Td>
                                            </Tr>
                                        )}
                                    </Tbody>
                                    <Tfoot>
                                        <Tr className="font-bold" style={{ backgroundColor: 'var(--color-ink-950)' }}>
                                            <Td className="p-3 font-sans uppercase tracking-wider text-xs" style={{ color: 'var(--color-on-color)' }}>
                                                NET À PAYER AU SALARIÉ
                                                <span className="text-[10px] text-[var(--color-ink-200)] block font-normal">
                                                    Arrondi à la dizaine d'Ariary
                                                </span>
                                            </Td>
                                            <Td className="p-3 text-right text-lg font-data font-black" style={{ color: 'var(--color-on-color)' }}>
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

            {/* Décompte imprimable */}
            {calculResult?.idStc && (
                <StcRecuModal
                    isOpen={showRecuModal}
                    onClose={() => setShowRecuModal(false)}
                    idStc={calculResult.idStc}
                />
            )}

            <ConfirmDialog
                isOpen={showConfirmValider && !simulationOnly}
                onClose={() => setShowConfirmValider(false)}
                onConfirm={handleValiderDefinitivement}
                title="Validation définitive du STC"
                message="Le calcul STC et son bulletin de sortie seront enregistrés. Vérifiez les droits, les justificatifs, les congés et les acomptes avant de valider. Le paiement reste à constater séparément."
                confirmLabel="Valider définitivement"
                variant="danger"
                loading={submitting}
            />
        </>
    );
}
