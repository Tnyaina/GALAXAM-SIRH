import { useEffect, useState, useMemo } from 'react';
import {
    Banknote,
    Receipt,
    Wallet,
    Calendar,
    CheckCircle2,
    AlertCircle,
    ArrowRight,
    User,
    Sparkles,
    CreditCard,
    Smartphone,
    Coins,
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
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

const MODE_ICONS = {
    VIREMENT: CreditCard,
    MOBILE_MONEY: Smartphone,
    ESPECES: Coins,
    CHEQUE: Receipt,
};

export default function DemandeAcompteAvanceModal({ idEmploye, employe, synthese, onClose, onCreated }) {
    const { periodes, context, selectedPeriodeId, selectPeriode, loading: loadingPeriod, error: periodError } = usePayrollCampaign();

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
    const salaireBase = synthese?.salaireBaseContractuel || 0;
    const plafondAcompte = salaireBase > 0 ? Math.round(salaireBase * 0.5) : 0;
    const encoursAcomptes = (synthese?.acomptesRestants || 0) + (synthese?.avancesRestantes || 0);

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
        && Boolean(selectedPeriodeId) && Boolean(typeCode) && isMontantValide && hasMotif;

    // Raccourcis de calcul ergonomiques (purement indicatifs, aucune règle bloquante)
    const quickAmounts = useMemo(() => {
        if (!salaireBase || salaireBase <= 0) return [50000, 100000, 200000];
        const p25 = Math.round(salaireBase * 0.25);
        const p50 = Math.round(salaireBase * 0.5);
        const p75 = Math.round(salaireBase * 0.75);
        return [
            { label: '25 %', value: p25, sub: formatMoneyAr(p25) },
            { label: '50 % (Quinzaine)', value: p50, sub: formatMoneyAr(p50) },
            { label: '75 %', value: p75, sub: formatMoneyAr(p75) },
            { label: '100 000 Ar', value: 100000, sub: 'Forfait' },
        ];
    }, [salaireBase]);

    const submit = async event => {
        event.preventDefault();
        if (!canSubmit) return;
        setSubmitting(true);
        setError(null);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(idEmploye),
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

    return (
        <Modal isOpen onClose={onClose} title="Demande d’acompte ou d’avance sur salaire" size="2xl">
            <form onSubmit={submit} className="space-y-4 text-xs">
                <p className="text-xs text-[var(--color-fg-muted)]">
                    La demande sera enregistrée en attente d’approbation dans la campagne de paie sélectionnée.
                </p>
                {error && <FormError message={error} />}
                {periodError && <Alert type="error" message={periodError} />}
                {typesError && <Alert type="error" message={typesError} />}

                {/* 1. Carte collaborateur & repères financiers contextuels */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-2 mb-2.5">
                        <div className="flex items-center gap-2">
                            <span className="w-7 h-7 rounded-none bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] flex items-center justify-center font-bold text-xs">
                                <User size={14} />
                            </span>
                            <div>
                                <span className="font-bold text-[var(--color-fg)] text-xs">
                                    {employe?.nomComplet || 'Collaborateur sélectionné'}
                                </span>
                                {employe?.matricule && (
                                    <span className="ml-1.5 text-[11px] text-[var(--color-fg-muted)] font-mono">
                                        #{employe.matricule}
                                    </span>
                                )}
                                {(employe?.fonction || employe?.poste) && (
                                    <span className="ml-2 text-[11px] text-[var(--color-fg-subtle)]">
                                        · {employe.fonction || employe.poste}
                                    </span>
                                )}
                            </div>
                        </div>
                        <Badge variant="outline" size="xs">Dossier Paie</Badge>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                        <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                            <span className="text-[10px] text-[var(--color-fg-muted)] block uppercase tracking-wider font-semibold">
                                Salaire contractuel de base
                            </span>
                            <span className="text-xs font-bold text-[var(--color-fg)] font-mono mt-0.5 block">
                                {salaireBase > 0 ? formatMoneyAr(salaireBase) : 'Non renseigné'}
                            </span>
                        </div>
                        <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                            <span className="text-[10px] text-[var(--color-fg-muted)] block uppercase tracking-wider font-semibold">
                                Repère quinzaine (50%)
                            </span>
                            <span className="text-xs font-bold text-[var(--color-brand-700)] font-mono mt-0.5 block">
                                {plafondAcompte > 0 ? formatMoneyAr(plafondAcompte) : '—'}
                            </span>
                        </div>
                        <div className="bg-[var(--color-surface)] p-2 border border-[var(--color-border-subtle)]">
                            <span className="text-[10px] text-[var(--color-fg-muted)] block uppercase tracking-wider font-semibold">
                                Encours acomptes / avances
                            </span>
                            <span className={`text-xs font-bold font-mono mt-0.5 block ${encoursAcomptes > 0 ? 'text-[var(--color-warning-600)]' : 'text-[var(--color-success-600)]'}`}>
                                {formatMoneyAr(encoursAcomptes)}
                                {encoursAcomptes === 0 && <span className="ml-1 text-[10px] font-normal text-[var(--color-fg-subtle)]">(Aucun encours)</span>}
                            </span>
                        </div>
                    </div>
                </div>

                {/* 2. Période de paie ciblée */}
                <div className="space-y-1">
                    <SearchSelect
                        label="Période de paie ciblée"
                        required
                        value={selectedPeriodeId || ''}
                        onChange={value => value && selectPeriode(Number(value))}
                        options={periodOptions}
                        placeholder={loadingPeriod ? 'Chargement des périodes…' : 'Sélectionner la période…'}
                        disabled={loadingPeriod || periodOptions.length === 0}
                    />

                    {/* Retour visuel basé sur l'état réel et le workflow backend */}
                    {workflowDenialReason && (
                        <Alert type="warning" message={`Saisie verrouillée par le workflow de paie : ${workflowDenialReason}`} />
                    )}

                    {activePeriod && !workflowDenialReason && (
                        <div className="flex items-center gap-1.5 pt-0.5 text-[11px]">
                            {activePeriod.statutCode === 'VALIDEE' ? (
                                <span className="inline-flex items-center gap-1 text-[var(--color-brand-700)] font-medium">
                                    <CheckCircle2 size={12} className="text-[var(--color-brand-600)]" />
                                    Pointage figé — Période prête pour la saisie des variables et acomptes.
                                </span>
                            ) : activePeriod.statutCode === 'EN_COURS' ? (
                                <span className="inline-flex items-center gap-1 text-[var(--color-fg-muted)] font-medium">
                                    <Calendar size={12} /> Pointage en cours — Les demandes seront déduites sur ce cycle.
                                </span>
                            ) : null}
                        </div>
                    )}
                </div>

                {/* 3. Sélecteur de Type : Alimenté dynamiquement par le référentiel Backend */}
                <div className="space-y-1.5">
                    <label className="block text-xs font-semibold text-[var(--color-fg)]">
                        Nature de la demande <span className="text-[var(--color-danger-500)]">*</span>
                    </label>

                    {loadingTypes ? (
                        <div className="p-3 text-center text-xs text-[var(--color-fg-muted)] border border-dashed border-[var(--color-border-subtle)]">
                            Chargement des types de demande autorisés…
                        </div>
                    ) : types.length === 0 ? (
                        <Alert type="warning" message="Aucun type d’acompte ou avance actif trouvé dans le référentiel de paie." />
                    ) : (
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                            {types.map((type) => {
                                const isAcompte = type.categorie === 'ACOMPTE';
                                const isSelected = typeCode === type.code;
                                const IconComp = isAcompte ? Wallet : Banknote;

                                return (
                                    <button
                                        key={type.code}
                                        type="button"
                                        onClick={() => setTypeCode(type.code)}
                                        className={`p-3 text-left border transition-all rounded-none relative ${isSelected
                                            ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 ring-1 ring-[var(--color-brand-600)]'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] hover:bg-[var(--color-bg-subtle)]'
                                            }`}
                                    >
                                        <div className="flex items-start justify-between gap-2 mb-1.5">
                                            <div className="flex items-center gap-2">
                                                <span className={`p-1.5 rounded-none ${isSelected ? 'bg-[var(--color-brand-500)]/20 text-[var(--color-brand-700)]' : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'}`}>
                                                    <IconComp size={15} />
                                                </span>
                                                <div>
                                                    <span className="font-bold text-xs text-[var(--color-fg)] block">
                                                        {type.libelle}
                                                    </span>
                                                </div>
                                            </div>
                                            <Badge variant={isSelected ? 'brand' : 'outline'} size="xs">
                                                {type.categorie}
                                            </Badge>
                                        </div>
                                        <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                                            {type.description || (isAcompte
                                                ? 'Versement anticipé pour le travail déjà accompli. Déduit sur le bulletin de la période.'
                                                : 'Avance exceptionnelle de trésorerie avec modalités de retenue définies en concertation.')}
                                        </p>
                                    </button>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* 4. Montant & Raccourcis Quick-Picks (simples suggestions d'aide à la saisie) */}
                <div className="space-y-1.5 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <label className="block text-xs font-semibold text-[var(--color-fg)]">
                            Montant de la demande <span className="text-[var(--color-danger-500)]">*</span>
                        </label>
                        {salaireBase > 0 && (
                            <span className="text-[11px] text-[var(--color-fg-muted)]">
                                Suggestions rapides (base : {formatMoneyAr(salaireBase)}) :
                            </span>
                        )}
                    </div>

                    <div className="flex flex-wrap gap-1.5 mb-2">
                        {quickAmounts.map((q, idx) => (
                            <button
                                key={idx}
                                type="button"
                                onClick={() => setMontant(String(q.value))}
                                className={`px-2.5 py-1 text-[11px] font-medium border transition-colors ${numMontant === q.value
                                    ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] font-bold'
                                    : 'border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] hover:border-[var(--color-brand-500)]'
                                    }`}
                            >
                                {q.label} {q.sub ? `(${q.sub})` : ''}
                            </button>
                        ))}
                    </div>

                    <MoneyInput
                        min="1"
                        required
                        placeholder="Ex. 100 000"
                        value={montant}
                        onValueChange={setMontant}
                    />

                    {/* Retours indicatifs d'aide visuelle (non bloquants) */}
                    {isMontantValide && salaireBase > 0 && (
                        <div className="pt-1">
                            {numMontant <= plafondAcompte ? (
                                <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                    <CheckCircle2 size={13} className="shrink-0" />
                                    <span>Montant dans le repère standard de la quinzaine (≤ 50 % du salaire).</span>
                                </div>
                            ) : numMontant <= salaireBase ? (
                                <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                    <AlertCircle size={13} className="shrink-0" />
                                    <span>Montant supérieur à 50 % du salaire mensuel. Soumis à accord.</span>
                                </div>
                            ) : (
                                <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-danger-600)] font-medium">
                                    <AlertCircle size={13} className="shrink-0" />
                                    <span>Information : le montant dépasse le salaire mensuel de base ({formatMoneyAr(salaireBase)}).</span>
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* 5. Mode de règlement & Date souhaitée (Alimentés dynamiquement) */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div className="space-y-1.5">
                        <label className="block text-xs font-semibold text-[var(--color-fg)]">
                            Mode de versement
                        </label>
                        <div className="grid grid-cols-2 gap-1.5">
                            {modes.map((mode) => {
                                const IconComponent = MODE_ICONS[mode.code] || CreditCard;
                                const isSelected = modeReglement === mode.code;
                                return (
                                    <button
                                        key={mode.code}
                                        type="button"
                                        onClick={() => setModeReglement(mode.code)}
                                        className={`flex items-center gap-1.5 px-2.5 py-1.5 border text-left transition-colors rounded-none ${isSelected
                                            ? 'border-[var(--color-brand-600)] bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] font-semibold'
                                            : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                            }`}
                                    >
                                        <IconComponent size={13} className={isSelected ? 'text-[var(--color-brand-600)]' : 'text-[var(--color-fg-subtle)]'} />
                                        <span className="text-[11px] truncate">{mode.label}</span>
                                    </button>
                                );
                            })}
                        </div>
                    </div>

                    <div className="space-y-1.5">
                        <Input
                            label="Date de la demande"
                            type="date"
                            required
                            value={dateDemande}
                            onChange={e => setDateDemande(e.target.value)}
                        />
                    </div>
                </div>

                {/* 6. Motif & Suggestions rapides */}
                <div className="space-y-1.5">
                    <div className="flex items-center justify-between">
                        <label className="block text-xs font-semibold text-[var(--color-fg)]">
                            Motif ou justification <span className="text-[var(--color-danger-500)]">*</span>
                        </label>
                        <span className="text-[11px] text-[var(--color-fg-muted)]">Suggestions rapides :</span>
                    </div>

                    <div className="flex flex-wrap gap-1.5 mb-1.5">
                        {SUGGESTIONS_MOTIFS.map((sug, i) => (
                            <button
                                key={i}
                                type="button"
                                onClick={() => setMotif(sug)}
                                className="px-2 py-0.5 text-[10px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-700)] transition-colors rounded-none"
                            >
                                + {sug}
                            </button>
                        ))}
                    </div>

                    <Input
                        required
                        placeholder="Ex. Dépense imprévue à couvrir"
                        value={motif}
                        onChange={e => setMotif(e.target.value)}
                    />
                </div>

                {/* 7. Workflow Timeline rassurant */}
                <div className="bg-[var(--color-bg-subtle)]/70 border border-[var(--color-border-subtle)] p-2.5 rounded-none">
                    <span className="text-[10px] text-[var(--color-fg-subtle)] uppercase tracking-wider font-bold block mb-1">
                        Circuit de traitement de la demande
                    </span>
                    <div className="flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                        <span className="font-semibold text-[var(--color-brand-700)]">1. Saisie (Enregistrement)</span>
                        <ArrowRight size={11} className="text-[var(--color-fg-subtle)]" />
                        <span>2. Approbation</span>
                        <ArrowRight size={11} className="text-[var(--color-fg-subtle)]" />
                        <span>3. Mise en paiement</span>
                        <ArrowRight size={11} className="text-[var(--color-fg-subtle)]" />
                        <span>4. Déduction bulletin</span>
                    </div>
                </div>

                {/* 8. Footer avec bouton d'action */}
                <div className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                        {!isMontantValide
                            ? 'Veuillez saisir un montant supérieur à 0 Ar.'
                            : !hasMotif
                                ? 'Veuillez renseigner un motif pour soumettre.'
                                : 'Prêt à être transmis pour approbation.'}
                    </span>
                    <div className="flex items-center gap-2">
                        <Button type="button" variant="outline" size="sm" onClick={onClose}>
                            Annuler
                        </Button>
                        <Button
                            type="submit"
                            variant="primary"
                            size="sm"
                            disabled={!canSubmit}
                            iconLeft={<Sparkles size={14} />}
                        >
                            {submitting ? 'Enregistrement…' : 'Enregistrer la demande'}
                        </Button>
                    </div>
                </div>
            </form>
        </Modal>
    );
}
