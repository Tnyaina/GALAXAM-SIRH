import { useEffect, useState, useMemo } from 'react';
import { ChevronDown } from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import useUnsavedChanges from '@/hooks/useUnsavedChanges';
import paieService from '@/features/payroll/api/paieService';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getTodayDateString } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';

const FALLBACK_MODES = [
    { code: 'VIREMENT', label: 'Virement bancaire' },
    { code: 'MOBILE_MONEY', label: 'Mobile Money' },
    { code: 'ESPECES', label: 'Espèces / Caisse' },
    { code: 'CHEQUE', label: 'Chèque' },
];

const SUGGESTIONS_MOTIFS = [
    'Acompte de quinzaine habituel',
    'Frais médicaux urgents',
    'Scolarité et rentrée',
    'Dépense imprévue du foyer',
];

export default function DemandeAcompteAvanceModal({ idEmploye, employe, synthese, onClose, onCreated }) {
    const { periodes, context, selectedPeriodeId, selectPeriode, loading: loadingPeriod, error: periodError } = usePayrollCampaign();

    // État pour la sélection autonome d'employé (quand non injecté en prop)
    const [selectedEmpId, setSelectedEmpId] = useState(idEmploye ? String(idEmploye) : '');
    const [selectedEmp, setSelectedEmp] = useState(employe || null);
    const [fetchedSynthese, setFetchedSynthese] = useState(null);
    const [loadingEmpSynthese, setLoadingEmpSynthese] = useState(false);

    useEffect(() => {
        if (idEmploye) {
            setSelectedEmpId(String(idEmploye));
            setSelectedEmp(employe || null);
            setFetchedSynthese(null);
            return;
        }
        if (!selectedEmpId) {
            setFetchedSynthese(null);
            return;
        }
        let active = true;
        setLoadingEmpSynthese(true);
        paieService.getSynthesePaieEmploye(selectedEmpId)
            .then(res => {
                if (active) setFetchedSynthese(res);
            })
            .catch(() => {
                if (active) setFetchedSynthese(null);
            })
            .finally(() => {
                if (active) setLoadingEmpSynthese(false);
            });
        return () => { active = false; };
    }, [idEmploye, employe, selectedEmpId]);

    const effectiveIdEmploye = idEmploye || selectedEmpId;
    const effectiveEmploye = employe || selectedEmp;
    const effectiveSynthese = synthese || fetchedSynthese;

    // 1. Référentiels dynamiques chargés depuis le Backend (aucune valeur en dur)
    const [types, setTypes] = useState([]);
    const [typeCode, setTypeCode] = useState('');
    const [loadingTypes, setLoadingTypes] = useState(true);
    const [typesError, setTypesError] = useState(null);

    const [modes, setModes] = useState(FALLBACK_MODES);
    const [modeReglement, setModeReglement] = useState('');

    const [montant, setMontant] = useState('');
    const [motif, setMotif] = useState('');
    const [dateDemande, setDateDemande] = useState(getTodayDateString());
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState(null);

    // Chiffres repères financiers de l'employé fournis par le backend
    const salaireBase = effectiveSynthese?.salaireBaseContractuel || 0;
    const modeCalculPaie = effectiveSynthese?.modeCalculPaie;
    const salaireMensuel = modeCalculPaie === 'MENSUEL';
    const plafondAcompte = salaireMensuel && salaireBase > 0 ? Math.round(salaireBase * 0.5) : 0;
    const encoursAcomptes = (effectiveSynthese?.acomptesRestants || 0) + (effectiveSynthese?.avancesRestantes || 0);

    // Chargement dynamique des types de mouvements autorisés pour la période sélectionnée
    useEffect(() => {
        let active = true;
        setLoadingTypes(true);
        setTypesError(null);

        paieService.listerTypesMouvements(selectedPeriodeId)
            .then(result => {
                if (!active) return;
                const options = (Array.isArray(result) ? result : [])
                    .filter(item => ['ACOMPTE', 'AVANCE'].includes(item.categorie));
                setTypes(options);
                // Sélectionne automatiquement le premier type disponible s'il n'est pas encore défini
                setTypeCode(current => {
                    if (current && options.some(item => item.code === current)) return current;
                    return options[0]?.code || '';
                });
                setLoadingTypes(false);
            })
            .catch(err => {
                if (active) {
                    setTypesError(getErrorMessage(err, 'Types de demande indisponibles.'));
                    setLoadingTypes(false);
                }
            });

        return () => { active = false; };
    }, [selectedPeriodeId]);

    // Chargement dynamique des modes de règlement configurés en base
    useEffect(() => {
        let active = true;
        paieService.listerModesReglement()
            .then(result => {
                if (!active) return;
                const list = Array.isArray(result) && result.length > 0
                    ? result.map(m => ({ code: m.code, label: m.libelle }))
                    : FALLBACK_MODES;
                setModes(list);
                setModeReglement(curr => curr || list[0]?.code || 'VIREMENT');
            })
            .catch(() => {
                if (active) setModeReglement('VIREMENT');
            });
        return () => { active = false; };
    }, []);

    // Périodes formatées avec statut métier humain (évite d'exposer les codes techniques SQL)
    const periodOptions = useMemo(() => {
        return periodes.map(period => {
            const id = period.idPeriodePaie || period.id_periode_paie || period.id;
            const code = period.statutCode || period.statut || '';
            const statutHumain = code === 'VALIDEE'
                ? 'Pointage figé · Paie à préparer'
                : code === 'EN_COURS'
                    ? 'Pointage en cours'
                    : code === 'PAIE_VALIDEE'
                        ? 'Paie validée'
                        : code === 'CLOTUREE'
                            ? 'Clôturée'
                            : period.statutLibelle || code || '';
            return {
                value: String(id),
                label: [period.code, period.libelle].filter(Boolean).join(' · ') || String(id),
                sublabel: statutHumain,
                statutCode: code,
            };
        }).filter(p => p.value && p.value !== 'undefined');
    }, [periodes]);

    const activePeriod = periodOptions.find(p => p.value === String(selectedPeriodeId));

    // Autorisation souveraine du workflow backend
    // Si context.availableActions.ADD_VARIABLE existe, on respecte la décision du backend
    const workflowAction = context?.availableActions?.ADD_VARIABLE;
    const isVariableAllowed = workflowAction ? workflowAction.allowed === true : true;
    const workflowDenialReason = workflowAction?.allowed === false ? workflowAction.reason : null;

    const numMontant = Number(montant) || 0;
    const isMontantValide = numMontant > 0;
    const hasMotif = Boolean(motif.trim());

    // La soumission ne dépend que des champs obligatoires et de l'autorisation workflow backend
    const canSubmit = isVariableAllowed && !loadingPeriod && !loadingTypes && !submitting
        && Boolean(effectiveIdEmploye) && Boolean(selectedPeriodeId) && Boolean(typeCode) && isMontantValide && hasMotif;

    // Raccourcis de calcul ergonomiques (purement indicatifs, aucune règle bloquante)
    const quickAmounts = useMemo(() => {
        if (!salaireMensuel || !salaireBase || salaireBase <= 0) {
            return [50000, 100000, 200000].map(value => ({
                label: formatMoneyAr(value),
                value,
            }));
        }
        const p25 = Math.round(salaireBase * 0.25);
        const p50 = Math.round(salaireBase * 0.5);
        const p75 = Math.round(salaireBase * 0.75);
        return [
            { label: '25 %', value: p25, sub: formatMoneyAr(p25) },
            { label: '50 % (Quinzaine)', value: p50, sub: formatMoneyAr(p50) },
            { label: '75 %', value: p75, sub: formatMoneyAr(p75) },
            { label: '100 000 Ar', value: 100000, sub: 'Forfait' },
        ];
    }, [salaireBase, salaireMensuel]);

    const submit = async event => {
        event.preventDefault();
        if (!canSubmit) return;
        setSubmitting(true);
        setError(null);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(effectiveIdEmploye),
                idPeriodePaie: Number(selectedPeriodeId),
                typeMouvement: typeCode,
                montant: numMontant,
                motif: motif.trim(),
                modeReglement,
                dateDemande,
            });
            onCreated?.();
            onClose();
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible d’enregistrer la demande.'));
        } finally {
            setSubmitting(false);
        }
    };

    const isDirty = Boolean((!idEmploye && selectedEmpId) || montant || motif || modeReglement);
    useUnsavedChanges(isDirty);

    const selectedType = types.find(type => type.code === typeCode);
    const selectedMode = modes.find(mode => mode.code === modeReglement);
    const beneficiaryName = effectiveEmploye?.nomComplet
        || [effectiveEmploye?.nom, effectiveEmploye?.prenom].filter(Boolean).join(' ')
        || (effectiveIdEmploye ? 'Collaborateur sélectionné' : 'Non sélectionné');
    const footerMessage = !isMontantValide
        ? 'Saisissez un montant supérieur à 0 Ar.'
        : !hasMotif
            ? 'Renseignez un motif pour enregistrer.'
            : !effectiveIdEmploye
                ? 'Sélectionnez un collaborateur.'
                : 'Demande prête à enregistrer.';

    return (
        <Modal
            isOpen
            onClose={onClose}
            closeOnBackdrop={false}
            isDirty={isDirty}
            title="Demande d’acompte ou d’avance sur salaire"
            size="full"
            className="advance-request-modal"
            bodyClassName="!p-0 !space-y-0"
            footer={
                <div className="flex w-full flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <span className="text-xs text-[var(--color-fg-muted)]">{footerMessage}</span>
                    <div className="flex flex-wrap gap-2">
                        <Button type="button" variant="outline" size="md" onClick={onClose}>Annuler</Button>
                        <Button type="submit" form="advance-request-form" size="md" disabled={!canSubmit} loading={submitting}>
                            {submitting ? 'Enregistrement…' : 'Enregistrer la demande'}
                        </Button>
                    </div>
                </div>
            }
        >
            <form id="advance-request-form" onSubmit={submit} className="grid lg:grid-cols-[minmax(0,2.1fr)_minmax(19rem,1fr)] text-sm">
                <div className="min-w-0 space-y-6 p-5 sm:p-7">
                    {(error || periodError || typesError) && (
                        <div className="space-y-2">
                            {error && <FormError message={error} />}
                            {periodError && <Alert type="error" message={periodError} />}
                            {typesError && <Alert type="error" message={typesError} />}
                        </div>
                    )}

                    {!idEmploye && (
                        <EmployeSearchSelect
                            required
                            label="Collaborateur bénéficiaire"
                            value={selectedEmpId}
                            onChange={(val, empObj) => {
                                setSelectedEmpId(val ? String(val) : '');
                                setSelectedEmp(empObj || null);
                            }}
                            placeholder="Rechercher par nom, prénom ou matricule…"
                        />
                    )}

                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="min-w-0">
                            <SearchSelect
                                label="Période de paie ciblée"
                                required
                                value={selectedPeriodeId || ''}
                                onChange={value => value && selectPeriode(Number(value))}
                                options={periodOptions}
                                placeholder={loadingPeriod ? 'Chargement des périodes…' : 'Sélectionner la période…'}
                                disabled={loadingPeriod || periodOptions.length === 0}
                            />
                            {workflowDenialReason && (
                                <div className="mt-2"><Alert type="warning" message={workflowDenialReason} /></div>
                            )}
                            {activePeriod && !workflowDenialReason && (
                                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{activePeriod.sublabel}</p>
                            )}
                        </div>
                        <Input
                            label="Date de la demande"
                            type="date"
                            required
                            value={dateDemande}
                            onChange={e => setDateDemande(e.target.value)}
                        />
                    </div>

                    <fieldset className="min-w-0">
                        <legend className="mb-2 font-semibold text-[var(--color-fg)]">Nature de la demande <span className="text-[var(--color-danger-500)]">*</span></legend>
                        {loadingTypes ? (
                            <p className="border border-dashed border-[var(--color-border)] p-3 text-[var(--color-fg-muted)]">Chargement des types…</p>
                        ) : types.length === 0 ? (
                            <Alert type="warning" message="Aucun type d’acompte ou d’avance actif." />
                        ) : (
                            <div className="grid gap-2 sm:grid-cols-2">
                                {types.map(type => {
                                    const selected = typeCode === type.code;
                                    return (
                                        <label key={type.code} className={`flex cursor-pointer items-center gap-3 border px-4 py-3 transition-colors ${selected
                                            ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-50)] text-[var(--color-fg)]'
                                            : 'border-[var(--color-border)] bg-[var(--color-surface)] text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'}`}>
                                            <input
                                                type="radio"
                                                name="advance-request-type"
                                                value={type.code}
                                                checked={selected}
                                                onChange={() => setTypeCode(type.code)}
                                                className="h-4 w-4 accent-[var(--color-brand-600)]"
                                            />
                                            <span className="font-semibold">{type.libelle}</span>
                                        </label>
                                    );
                                })}
                            </div>
                        )}
                        {selectedType && (
                            <p className="mt-2 text-xs text-[var(--color-fg-muted)]">
                                {selectedType.categorie === 'ACOMPTE'
                                    ? (selectedType.description || 'Acompte versé puis imputé sur la période ciblée.')
                                    : 'Remboursement sans plafond en pourcentage, selon le solde dû et le net disponible.'}
                            </p>
                        )}
                    </fieldset>

                    <div className="grid gap-4 sm:grid-cols-2">
                        <div className="min-w-0">
                            <MoneyInput
                                label="Montant de la demande"
                                min="1"
                                required
                                placeholder="Ex. 100 000"
                                value={montant}
                                onValueChange={setMontant}
                                iconRight={<span className="font-semibold text-[var(--color-fg)]">Ar</span>}
                            />
                            <div className="mt-2 flex flex-wrap gap-1.5" aria-label="Montants suggérés">
                                {quickAmounts.map(q => (
                                    <button
                                        key={q.value}
                                        type="button"
                                        onClick={() => setMontant(String(q.value))}
                                        className={`border px-2.5 py-1 text-xs transition-colors ${numMontant === q.value
                                            ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-50)] text-[var(--color-brand-700)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] hover:border-[var(--color-brand-500)]'}`}
                                    >
                                        {q.sub ? `${q.label} · ${q.sub}` : q.label}
                                    </button>
                                ))}
                            </div>
                        </div>
                        <div className="min-w-0">
                            <label htmlFor="advance-payment-mode" className="mb-1 block font-semibold text-[var(--color-fg)]">Mode de versement</label>
                            <div className="relative">
                                <select
                                    id="advance-payment-mode"
                                    value={modeReglement}
                                    onChange={event => setModeReglement(event.target.value)}
                                    className="ui-toolbar-control w-full appearance-none border border-[var(--color-border)] bg-[var(--color-surface)] px-3 pr-9 text-sm text-[var(--color-fg)] outline-none focus:border-[var(--color-brand-500)] focus:ring-1 focus:ring-[var(--color-brand-500)]"
                                >
                                    {modes.map(mode => <option key={mode.code} value={mode.code}>{mode.label}</option>)}
                                </select>
                                <ChevronDown size={16} aria-hidden="true" className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                            </div>
                        </div>
                    </div>

                    <div>
                        <Input
                            label="Motif ou justification"
                            required
                            placeholder="Ex. Dépense imprévue à couvrir"
                            value={motif}
                            onChange={e => setMotif(e.target.value)}
                        />
                        <div className="mt-2 flex flex-wrap items-center gap-1.5">
                            <span className="mr-1 text-xs text-[var(--color-fg-muted)]">Suggestions :</span>
                            {SUGGESTIONS_MOTIFS.map(suggestion => (
                                <button
                                    key={suggestion}
                                    type="button"
                                    onClick={() => setMotif(suggestion)}
                                    className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-2.5 py-1 text-xs text-[var(--color-fg)] hover:border-[var(--color-brand-500)]"
                                >
                                    {suggestion}
                                </button>
                            ))}
                        </div>
                    </div>
                </div>

                <aside className="min-w-0 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-5 sm:p-7 lg:border-l lg:border-t-0" aria-label="Résumé de la demande">
                    <h4 className="mb-3 text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Résumé</h4>
                    <dl className="divide-y divide-[var(--color-border-subtle)] text-sm">
                        <div className="flex justify-between gap-3 py-2"><dt className="text-[var(--color-fg-muted)]">Bénéficiaire</dt><dd className="text-right font-semibold text-[var(--color-fg)]">{beneficiaryName}</dd></div>
                        <div className="flex justify-between gap-3 py-2"><dt className="text-[var(--color-fg-muted)]">Période</dt><dd className="text-right font-semibold text-[var(--color-fg)]">{activePeriod?.label || 'Non sélectionnée'}</dd></div>
                        <div className="flex justify-between gap-3 py-2"><dt className="text-[var(--color-fg-muted)]">Nature</dt><dd className="text-right font-semibold text-[var(--color-fg)]">{selectedType?.libelle || 'Non sélectionnée'}</dd></div>
                        <div className="flex justify-between gap-3 py-2"><dt className="text-[var(--color-fg-muted)]">Versement</dt><dd className="text-right font-semibold text-[var(--color-fg)]">{selectedMode?.label || 'Non sélectionné'}</dd></div>
                    </dl>

                    <div className="mt-5 border border-[var(--color-brand-200)] bg-[var(--color-brand-50)] p-4">
                        <p className="text-xs text-[var(--color-fg-muted)]">Montant demandé</p>
                        <p className="mt-1 text-2xl font-bold text-[var(--color-fg)]">{formatMoneyAr(numMontant)}</p>
                    </div>

                    {effectiveIdEmploye ? (
                        <dl className="mt-4 space-y-2 text-xs">
                            <div className="flex justify-between gap-3"><dt className="text-[var(--color-fg-muted)]">{modeCalculPaie === 'JOURNALIER' ? 'Taux journalier' : modeCalculPaie === 'HORAIRE' ? 'Taux horaire' : 'Salaire de base'}</dt><dd className="font-semibold text-[var(--color-fg)]">{loadingEmpSynthese ? 'Chargement…' : salaireBase > 0 ? formatMoneyAr(salaireBase) : 'Non renseigné'}</dd></div>
                            <div className="flex justify-between gap-3"><dt className="text-[var(--color-fg-muted)]">Repère 50 %</dt><dd className="font-semibold text-[var(--color-fg)]">{loadingEmpSynthese ? 'Chargement…' : plafondAcompte > 0 ? formatMoneyAr(plafondAcompte) : 'Selon paie acquise'}</dd></div>
                            <div className="flex justify-between gap-3"><dt className="text-[var(--color-fg-muted)]">Encours</dt><dd className="font-semibold text-[var(--color-fg)]">{loadingEmpSynthese ? 'Chargement…' : formatMoneyAr(encoursAcomptes)}</dd></div>
                        </dl>
                    ) : (
                        <p className="mt-4 text-xs text-[var(--color-fg-muted)]">Salaire de base, repère 50 % et encours après sélection du collaborateur.</p>
                    )}

                    <h4 className="mb-3 mt-6 text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">Circuit de traitement</h4>
                    <ol className="space-y-3 text-sm">
                        {['Saisie de la demande', 'Approbation', 'Confirmation du versement', 'Déduction sur bulletin'].map((step, index) => (
                            <li key={step} className="flex items-center gap-3">
                                <span className={`flex h-6 w-6 shrink-0 items-center justify-center text-xs font-bold ${index === 0
                                    ? 'bg-[var(--color-brand-600)] text-[var(--color-on-color)]'
                                    : 'bg-[var(--color-border)] text-[var(--color-fg-muted)]'}`}>{index + 1}</span>
                                <span className={index === 0 ? 'font-semibold text-[var(--color-fg)]' : 'text-[var(--color-fg-muted)]'}>{step}</span>
                            </li>
                        ))}
                    </ol>
                </aside>
            </form>
        </Modal>
    );
}
