import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
    CalendarRange,
    Plus,
    CheckCircle,
    Play,
    ArrowRight,
    Archive,
    Sparkles,
    ChevronDown,
    ChevronUp
} from 'lucide-react';
import { usePeriodePaie } from '@/features/attendance/hooks/usePeriodePaie';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Input from '@/components/ui/forms/Input';
import DatePicker from '@/components/ui/forms/DatePicker';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import HasPermission from '@/features/auth/components/HasPermission';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import PayrollWorkflowStatusBadges from '@/features/payroll/components/workflow/PayrollWorkflowStatusBadges';

const STATUT_CONFIG = {
    'BROUILLON': {
        variant: 'secondary',
        label: 'Brouillon',
        desc: 'Période configurée mais non active'
    },
    'EN_COURS': {
        variant: 'info',
        label: 'Saisie active (Pointage)',
        desc: 'Pointages ouverts aux agents'
    },
    'A_VALIDER': {
        variant: 'warning',
        label: 'Transmise pour arbitrage',
        desc: 'Saisie terminée, arbitrage en cours'
    },
    'TRANSMIS_DAF': {
        variant: 'warning',
        label: 'Transmise pour arbitrage',
        desc: 'Saisie terrain clôturée, arbitrage en cours'
    },
    'VALIDEE': {
        variant: 'primary',
        label: 'Pointage Scellé & Figé',
        desc: 'Snapshot scellé — Prêt pour calcul Paie'
    },
    'PAIE_VALIDEE': {
        variant: 'success',
        label: 'Paie Validée & Approuvée',
        desc: 'Bulletins approuvés — Prête pour virement et clôture'
    },
    'CLOTUREE': {
        variant: 'secondary',
        label: 'Clôturée définitivement',
        desc: 'Période archivée, immuable'
    }
};

const WORKFLOW_ACTIONS = {
    OPEN: { targetState: 'EN_COURS', label: 'Ouvrir la saisie', title: 'Ouvrir la période à la saisie', variant: 'outline', Icon: Play },
    SUBMIT: { targetState: 'A_VALIDER', label: 'Transmettre pour validation', title: 'Transmettre la période pour validation', variant: 'outline', Icon: ArrowRight },
    APPROVE: { targetState: 'VALIDEE', label: 'Valider', title: 'Validation de la période & gel des données Paie', variant: 'primary', Icon: CheckCircle },
    CLOSE: { targetState: 'CLOTUREE', label: 'Clôturer', title: 'Clôture définitive de la période', variant: 'danger', Icon: Archive }
};

const MOIS_FR = [
    'Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin',
    'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'
];

const computeNextPeriodeSuggestion = (periodesList) => {
    // Cycle de paie standard : du 26 du mois M-1 au 25 du mois M
    if (!periodesList || periodesList.length === 0) {
        const now = new Date();
        const y = now.getFullYear();
        const m = now.getMonth(); // 0 à 11
        const d = now.getDate();

        let targetY = y;
        let targetM = d >= 26 ? m + 1 : m;
        if (targetM > 11) {
            targetM = 0;
            targetY += 1;
        }

        const prevM = targetM === 0 ? 11 : targetM - 1;
        const prevY = targetM === 0 ? targetY - 1 : targetY;

        const startPad = String(prevM + 1).padStart(2, '0');
        const endPad = String(targetM + 1).padStart(2, '0');

        return {
            code: `${targetY}-${endPad}`,
            libelle: `${MOIS_FR[targetM]} ${targetY}`,
            dateDebut: `${prevY}-${startPad}-26`,
            dateFin: `${targetY}-${endPad}-25`,
            baseLibelle: null
        };
    }

    // Trouver la période la plus récente par dateFin ou dateDebut
    const sorted = [...periodesList].sort((a, b) => {
        const dFinA = a.dateFin || a.date_fin || '';
        const dFinB = b.dateFin || b.date_fin || '';
        return dFinB.localeCompare(dFinA);
    });

    const latest = sorted[0];
    let nextYear, nextMonth;

    if (latest.dateFin) {
        const parts = latest.dateFin.split('-');
        if (parts.length === 3) {
            let y = parseInt(parts[0], 10);
            let m = parseInt(parts[1], 10) - 1; // 0 à 11
            m += 1;
            if (m > 11) {
                m = 0;
                y += 1;
            }
            nextYear = y;
            nextMonth = m;
        }
    }

    if (!nextYear) {
        const now = new Date();
        nextYear = now.getFullYear();
        nextMonth = now.getMonth();
    }

    // Cycle 26 au 25 pour le mois nextMonth
    const prevMonth = nextMonth === 0 ? 11 : nextMonth - 1;
    const prevYear = nextMonth === 0 ? nextYear - 1 : nextYear;

    const startPad = String(prevMonth + 1).padStart(2, '0');
    const endPad = String(nextMonth + 1).padStart(2, '0');

    return {
        code: `${nextYear}-${endPad}`,
        libelle: `${MOIS_FR[nextMonth]} ${nextYear}`,
        dateDebut: `${prevYear}-${startPad}-26`,
        dateFin: `${nextYear}-${endPad}-25`,
        baseLibelle: latest.libelle || latest.code
    };
};

const computePeriodeForDate = (dateStr) => {
    if (!dateStr) return null;
    try {
        const parts = dateStr.split('-');
        if (parts.length === 3) {
            const y = parseInt(parts[0], 10);
            const m = parseInt(parts[1], 10) - 1; // 0 à 11
            const d = parseInt(parts[2], 10);

            // Règle du cycle 26 -> 25 :
            // Si le jour >= 26, la date appartient à la paie du mois suivant (M+1)
            // Si le jour <= 25, la date appartient à la paie du mois en cours (M)
            let targetYear = y;
            let targetMonth = d >= 26 ? m + 1 : m;
            if (targetMonth > 11) {
                targetMonth = 0;
                targetYear += 1;
            }

            const startMonth = targetMonth === 0 ? 11 : targetMonth - 1;
            const startYear = targetMonth === 0 ? targetYear - 1 : targetYear;

            const startMonthPad = String(startMonth + 1).padStart(2, '0');
            const targetMonthPad = String(targetMonth + 1).padStart(2, '0');

            return {
                code: `${targetYear}-${targetMonthPad}`,
                libelle: `${MOIS_FR[targetMonth]} ${targetYear}`,
                dateDebut: `${startYear}-${startMonthPad}-26`,
                dateFin: `${targetYear}-${targetMonthPad}-25`,
                baseLibelle: null
            };
        }
    } catch {
        // Ignorer les erreurs de parsing de date
    }
    return null;
};

export default function PeriodePaiePage() {
    // Contexte, navigation et hook métier
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const {
        periodes,
        loading,
        error,
        setError,
        createPeriode,
        executeCommand
    } = usePeriodePaie();

    // État local
    const toast = useToast();
    const [variablesByPeriod, setVariablesByPeriod] = useState({});
    const [expandedPeriodId, setExpandedPeriodId] = useState(null);

    // Effets
    useEffect(() => {
        let active = true;
        const loadVariables = async () => {
            const rows = await Promise.all(periodes.map(async (period) => {
                const id = period.idPeriodePaie || period.id;
                try {
                    const response = await paieService.listerAjustements(id);
                    const items = Array.isArray(response) ? response : (response?.content || response?.items || []);
                    return [id, items];
                } catch {
                    return [id, []];
                }
            }));
            if (active) setVariablesByPeriod(Object.fromEntries(rows));
        };
        if (periodes.length) loadVariables();
        else setVariablesByPeriod({});
        return () => { active = false; };
    }, [periodes]);

    // Données dérivées
    const nextSuggestion = useMemo(() => computeNextPeriodeSuggestion(periodes), [periodes]);

    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isCustomCreate, setIsCustomCreate] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [newPeriode, setNewPeriode] = useState({
        code: '',
        libelle: '',
        dateDebut: '',
        dateFin: ''
    });

    // Effets de synchronisation URL
    useEffect(() => {
        const action = searchParams.get('action');
        if (action === 'create') {
            const targetDate = searchParams.get('targetDate');
            const customForDate = targetDate ? computePeriodeForDate(targetDate) : null;
            const initial = customForDate || nextSuggestion;
            setNewPeriode({
                code: initial.code,
                libelle: initial.libelle,
                dateDebut: initial.dateDebut,
                dateFin: initial.dateFin
            });
            setIsCustomCreate(Boolean(customForDate));
            setIsCreateModalOpen(true);

            // Nettoyer les paramètres d'URL pour ne pas ré-ouvrir au refresh/close
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.delete('action');
                next.delete('targetDate');
                return next;
            }, { replace: true });
        }
    }, [searchParams, nextSuggestion, setSearchParams]);

    // Actions
    const handleOpenCreateWithSuggestion = () => {
        setIsCustomCreate(false);
        setNewPeriode({
            code: nextSuggestion.code,
            libelle: nextSuggestion.libelle,
            dateDebut: nextSuggestion.dateDebut,
            dateFin: nextSuggestion.dateFin
        });
        setIsCreateModalOpen(true);
    };

    const handleApplyCycle2625 = () => {
        let y, m;
        if (newPeriode.code && newPeriode.code.includes('-')) {
            const parts = newPeriode.code.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else if (newPeriode.dateFin) {
            const parts = newPeriode.dateFin.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else {
            const now = new Date();
            y = now.getFullYear();
            m = now.getMonth();
        }
        const prevM = m === 0 ? 11 : m - 1;
        const prevY = m === 0 ? y - 1 : y;
        const startPad = String(prevM + 1).padStart(2, '0');
        const endPad = String(m + 1).padStart(2, '0');
        setNewPeriode(prev => ({
            ...prev,
            dateDebut: `${prevY}-${startPad}-26`,
            dateFin: `${y}-${endPad}-25`
        }));
    };

    const handleApplyCycleCivil = () => {
        let y, m;
        if (newPeriode.code && newPeriode.code.includes('-')) {
            const parts = newPeriode.code.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else if (newPeriode.dateFin) {
            const parts = newPeriode.dateFin.split('-');
            y = parseInt(parts[0], 10);
            m = parseInt(parts[1], 10) - 1;
        } else {
            const now = new Date();
            y = now.getFullYear();
            m = now.getMonth();
        }
        const mPad = String(m + 1).padStart(2, '0');
        const lastDay = new Date(y, m + 1, 0).getDate();
        setNewPeriode(prev => ({
            ...prev,
            dateDebut: `${y}-${mPad}-01`,
            dateFin: `${y}-${mPad}-${String(lastDay).padStart(2, '0')}`
        }));
    };

    const handleOpenCreateBlank = () => {
        setIsCustomCreate(true);
        setNewPeriode({
            code: '',
            libelle: '',
            dateDebut: '',
            dateFin: ''
        });
        setIsCreateModalOpen(true);
    };

    const [simulation, setSimulation] = useState(null);
    const [simulating, setSimulating] = useState(false);

    useEffect(() => {
        if (newPeriode.dateDebut && newPeriode.dateFin) {
            if (new Date(newPeriode.dateDebut) <= new Date(newPeriode.dateFin)) {
                let active = true;
                setSimulating(true);
                periodePaieService.simulerPeriode(newPeriode.dateDebut, newPeriode.dateFin)
                    .then(data => {
                        if (active) setSimulation(data);
                    })
                    .catch(() => {
                        if (active) setSimulation(null);
                    })
                    .finally(() => {
                        if (active) setSimulating(false);
                    });
                return () => { active = false; };
            } else {
                setSimulation(null);
            }
        } else {
            setSimulation(null);
        }
    }, [newPeriode.dateDebut, newPeriode.dateFin]);

    const [confirmAction, setConfirmAction] = useState({
        isOpen: false,
        periode: null,
        command: null,
        title: '',
        message: '',
        variant: 'primary',
        confirmLabel: 'Confirmer'
    });

    const activeCycle = useMemo(() => periodes.find((period) =>
        ['EN_COURS', 'A_VALIDER', 'TRANSMIS_DAF', 'VALIDEE', 'PAIE_VALIDEE'].includes(period.statutCode)
    ) || null, [periodes]);

    const handleCreate = async (e) => {
        e.preventDefault();
        setSubmitting(true);
        const success = await createPeriode(newPeriode);
        setSubmitting(false);
        if (success) {
            toast({
                title: 'Période créée',
                message: `La période "${newPeriode.libelle}" (${simulation?.nbJoursOuvrables ? simulation.nbJoursOuvrables + 'j ouvrables' : ''}) a été créée avec succès.`,
                type: 'success'
            });
            setIsCreateModalOpen(false);
            setNewPeriode({ code: '', libelle: '', dateDebut: '', dateFin: '' });
            setSimulation(null);
        } else {
            toast({
                title: 'Erreur',
                message: error || 'Impossible de créer la période de paie.',
                type: 'danger'
            });
        }
    };

    const triggerWorkflowAction = (periode, command) => {
        const config = WORKFLOW_ACTIONS[command];
        setConfirmAction({
            isOpen: true,
            periode,
            command,
            title: config.title,
            message: `Confirmez-vous l'action « ${config.label} » pour la période "${periode.libelle}" ?`,
            variant: config.variant,
            confirmLabel: config.label
        });
    };

    const handleExecuteWorkflow = async (periode, command) => {
        const config = WORKFLOW_ACTIONS[command];
        setSubmitting(true);
        const success = await executeCommand(periode, command);
        setSubmitting(false);
        setConfirmAction(prev => ({ ...prev, isOpen: false }));
        if (success) {
            toast({
                title: 'Statut mis à jour',
                message: `La période est désormais au statut : ${STATUT_CONFIG[config.targetState]?.label || config.targetState}`,
                type: 'success'
            });
        } else {
            toast({
                title: 'Erreur',
                message: error || 'Impossible de mettre à jour le statut.',
                type: 'danger'
            });
        }
    };

    // Rendu
    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-8 px-2 sm:px-4">
            <SEO title="Périodes de Paie & Clôture" description="Gestion des périodes mensuelles de pointage et cycle de paie." />

            <PageHeader
                title="Campagnes de paie"
                subtitle="Créez la prochaine période, puis suivez son passage du pointage à la paie."
                icon={CalendarRange}
                breadcrumbs={[{ label: 'Tableau de bord', href: '/dashboard' }, { label: 'Paie & Rémunération', href: '/paie' }, { label: 'Périodes de paie' }]}
            />

            <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] border-l-4 border-l-[var(--color-brand-500)] p-4 sm:p-5 shadow-xs">
                <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                    <div>
                        <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)]">Prochaine campagne suggérée</p>
                        <h2 className="mt-1 text-lg font-bold text-[var(--color-fg)]">{nextSuggestion.libelle} <span className="font-mono text-sm font-medium text-[var(--color-fg-muted)]">· {nextSuggestion.code}</span></h2>
                        <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Du {nextSuggestion.dateDebut} au {nextSuggestion.dateFin} · Cycle du 26 au 25</p>
                        <p className="mt-2 text-xs text-[var(--color-fg-muted)]">Créée en brouillon ; la saisie du pointage s’ouvre ensuite.</p>
                    </div>
                    <HasPermission authority={['PAIE_WRITE', 'PAIE_VALIDATE', 'POINTAGE_VALIDATE']}>
                        <div className="flex flex-wrap items-center gap-2">
                            <Button onClick={handleOpenCreateWithSuggestion} variant="primary" size="sm" iconLeft={<Plus size={14} />}>Créer la campagne</Button>
                            <Button onClick={handleOpenCreateBlank} variant="outline" size="sm">Choisir d’autres dates</Button>
                        </div>
                    </HasPermission>
                </div>
            </section>

            {activeCycle && (
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4 sm:p-5">
                    <div className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-4">
                        <div>
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-brand-600)]">Cycle actuellement piloté</p>
                            <h2 className="mt-1 text-lg font-bold text-[var(--color-fg)]">{activeCycle.libelle} <span className="font-mono text-sm font-medium text-[var(--color-fg-muted)]">· {activeCycle.dateDebut} → {activeCycle.dateFin}</span></h2>
                        </div>
                        {activeCycle.nextStep?.destination && (
                            <Button size="sm" variant="primary" onClick={() => navigate(activeCycle.nextStep.destination)}>
                                Continuer la campagne
                            </Button>
                        )}
                    </div>
                    <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 border border-[var(--color-border-subtle)] divide-y sm:divide-y-0 sm:divide-x divide-[var(--color-border-subtle)]">
                        <div className="p-3">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">1. Pointage</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{activeCycle.workflowStatuses?.pointage?.label || '—'}</p>
                        </div>
                        <div className="p-3">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">2. Paie</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{activeCycle.workflowStatuses?.paie?.label || '—'}</p>
                        </div>
                        <div className="p-3">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-subtle)]">3. Variables</p>
                            <p className="mt-1 text-sm font-semibold text-[var(--color-fg)]">{(variablesByPeriod[activeCycle.idPeriodePaie] || []).length} élément(s) rattaché(s)</p>
                        </div>
                    </div>
                </section>
            )}

            {error && <Alert type="danger" message={error} onClose={() => setError(null)} />}

            {/* Campagnes et actions de suivi */}
            <section className="space-y-3" aria-labelledby="campagnes-creees-title">

                {loading && periodes.length === 0 ? (
                    <SkeletonTable rows={3} cols={3} />
                ) : periodes.length === 0 ? (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 text-center text-sm text-[var(--color-fg-muted)]">
                        Aucune campagne créée pour le moment.
                    </div>
                ) : (
                    <div className={`space-y-3 transition-opacity duration-200 ${loading ? 'opacity-50 pointer-events-none' : ''}`}>
                        {periodes.map(p => {
                            const periodId = p.idPeriodePaie || p.id;
                            const variables = variablesByPeriod[periodId] || [];
                            const isExpanded = expandedPeriodId === periodId;
                            const availableActions = Object.entries(WORKFLOW_ACTIONS).filter(([command]) => p.availableActions?.[command]?.allowed === true);
                            const mainAction = availableActions.find(([command]) => command !== 'CLOSE');
                            const secondaryActions = availableActions.filter(([command]) => command === 'CLOSE');

                            return (
                                <article key={periodId} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                                    <div className="p-4 sm:p-5">
                                        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
                                            <div className="min-w-0">
                                                <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                                                    <h3 className="text-base font-bold text-[var(--color-fg)]">{p.libelle}</h3>
                                                    <span className="font-mono text-xs text-[var(--color-brand-600)]">{p.code}</span>
                                                </div>
                                                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                                                    <span className="font-mono">{p.dateDebut} → {p.dateFin}</span>
                                                    <span className="mx-2" aria-hidden="true">·</span>
                                                    {p.nbJoursOuvrables ?? '—'} jours ouvrables
                                                    {p.nbJoursFeries > 0 && <span> · {p.nbJoursFeries} jour(s) férié(s)</span>}
                                                </p>
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                                                {p.nextStep?.destination && (
                                                    <Button size="sm" variant="primary" onClick={() => navigate(p.nextStep.destination)}>Continuer</Button>
                                                )}
                                                {mainAction && (() => {
                                                    const [command, config] = mainAction;
                                                    const ActionIcon = config.Icon;
                                                    return <Button size="sm" variant="primary" icon={<ActionIcon size={14} />} onClick={() => triggerWorkflowAction(p, command)}>{config.label}</Button>;
                                                })()}
                                            </div>
                                        </div>
                                        <div className="mt-4 pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap items-center gap-x-5 gap-y-2 text-xs">
                                            <PayrollWorkflowStatusBadges statuses={p.workflowStatuses} size="xs" />
                                            <button
                                                type="button"
                                                aria-expanded={isExpanded}
                                                aria-controls={`campagne-details-${periodId}`}
                                                onClick={() => setExpandedPeriodId(isExpanded ? null : periodId)}
                                                className="ml-auto inline-flex items-center gap-1.5 font-semibold text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)] cursor-pointer"
                                            >
                                                Variables ({variables.length}) et détails {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                                            </button>
                                        </div>
                                    </div>
                                    {isExpanded && (
                                        <div id={`campagne-details-${periodId}`} className="border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-3 sm:px-5">
                                            <div className="flex flex-wrap items-center justify-between gap-2">
                                                <h4 className="text-xs font-bold text-[var(--color-fg)]">Éléments variables</h4>
                                                <Button size="xs" variant="outline" onClick={() => navigate(`/paie/periodes/${periodId}/variables`)}>Gérer les variables</Button>
                                            </div>
                                            {variables.length === 0 ? (
                                                <p className="mt-2 text-xs text-[var(--color-fg-muted)]">Aucun élément variable enregistré.</p>
                                            ) : (
                                                <ul className="mt-2 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-2">
                                                    {variables.map(variable => (
                                                        <li key={variable.idAvance || variable.idAjustement || variable.id} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-3 py-2 text-xs flex items-center justify-between gap-3">
                                                            <span className="min-w-0"><strong className="block truncate text-[var(--color-fg)]">{variable.libelle || variable.typeMouvement || 'Variable de paie'}</strong><span className="text-[var(--color-fg-muted)]">{variable.nomEmploye || variable.matricule || 'Population'}</span></span>
                                                            <strong className="font-mono whitespace-nowrap text-[var(--color-fg)]">{formatMoneyAr(variable.montant ?? variable.montantDemande ?? 0)}</strong>
                                                        </li>
                                                    ))}
                                                </ul>
                                            )}
                                            {secondaryActions.length > 0 && (
                                                <div className="mt-3 pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap items-center gap-2">
                                                    <span className="text-xs text-[var(--color-fg-muted)]">Gestion du cycle</span>
                                                    {secondaryActions.map(([command, config]) => {
                                                        const ActionIcon = config.Icon;
                                                        return <Button key={command} size="xs" variant={config.variant} icon={<ActionIcon size={13} />} onClick={() => triggerWorkflowAction(p, command)}>{config.label}</Button>;
                                                    })}
                                                </div>
                                            )}
                                        </div>
                                    )}
                                </article>
                            );
                        })}
                    </div>
                )}
            </section>

            {/* Modale de Création de Période */}
            <Modal
                isOpen={isCreateModalOpen}
                onClose={() => setIsCreateModalOpen(false)}
                title="Créer une campagne de paie"
                icon={<CalendarRange size={18} />}
                size="lg"
            >
                <form onSubmit={handleCreate} className="space-y-4">
                    {isCustomCreate && nextSuggestion && (
                        <div className="p-3 bg-[var(--color-warning-500)]/10 dark:bg-[var(--color-warning-950)]/25 border border-[var(--color-warning-500)]/30 flex items-center justify-between gap-3 text-xs">
                            <div className="flex items-center gap-2">
                                <Sparkles size={16} className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] shrink-0" />
                                <span className="text-[var(--color-warning-950)] dark:text-[var(--color-warning-200)]">
                                    Suite suggérée : <strong>{nextSuggestion.libelle}</strong> ({nextSuggestion.dateDebut} au {nextSuggestion.dateFin})
                                </span>
                            </div>
                            <button
                                type="button"
                                onClick={() => setNewPeriode({
                                    code: nextSuggestion.code,
                                    libelle: nextSuggestion.libelle,
                                    dateDebut: nextSuggestion.dateDebut,
                                    dateFin: nextSuggestion.dateFin
                                })}
                                className="px-2 py-1 bg-[var(--color-warning-500)]/20 hover:bg-[var(--color-warning-500)]/30 text-[var(--color-warning-900)] dark:text-[var(--color-warning-200)] font-bold text-[11px] border border-[var(--color-warning-500)]/40 cursor-pointer whitespace-nowrap transition-colors"
                            >
                                Appliquer la suggestion
                            </button>
                        </div>
                    )}

                    <div className="space-y-1.5">
                        <div className="space-y-2">
                            <span className="text-xs font-semibold text-[var(--color-fg)]">Type de cycle</span>
                            <div className="flex flex-wrap items-center gap-1.5">
                                <button
                                    type="button"
                                    onClick={handleApplyCycle2625}
                                    className="px-2 py-0.5 text-[10.5px] font-medium bg-[var(--color-bg-subtle)] text-[var(--color-fg)] border border-[var(--color-border)] hover:border-[var(--color-brand-500)] transition-colors cursor-pointer"
                                    title="Appliquer le cycle standard de paie du 26 au 25"
                                >
                                    Du 26 au 25
                                </button>
                                <button
                                    type="button"
                                    onClick={handleApplyCycleCivil}
                                    className="px-2 py-0.5 text-[10.5px] font-medium bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)] hover:text-[var(--color-fg)] transition-colors cursor-pointer"
                                    title="Appliquer le mois civil du 1er au dernier jour"
                                >
                                    Mois civil
                                </button>
                            </div>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 pt-2">
                            <DatePicker
                                label="Date de début"
                                value={newPeriode.dateDebut}
                                onChange={val => setNewPeriode({ ...newPeriode, dateDebut: val })}
                                required
                            />
                            <DatePicker
                                label="Date de fin"
                                value={newPeriode.dateFin}
                                onChange={val => setNewPeriode({ ...newPeriode, dateFin: val })}
                                required
                            />
                        </div>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 border-t border-[var(--color-border-subtle)] pt-4">
                        <Input
                            label="Code de période"
                            placeholder="AAAA-MM"
                            value={newPeriode.code}
                            onChange={e => setNewPeriode({ ...newPeriode, code: e.target.value })}
                            required
                        />
                        <Input
                            label="Libellé"
                            placeholder="Septembre 2026"
                            value={newPeriode.libelle}
                            onChange={e => setNewPeriode({ ...newPeriode, libelle: e.target.value })}
                            required
                        />
                    </div>

                    {/* Bloc Synthèse Dynamique Calculée Côté Serveur (Backend-Driven) */}
                    {simulating && (
                        <div className="p-3 bg-ink-50 dark:bg-[var(--color-surface)]/40 border border-ink-200 dark:border-[var(--color-border-subtle)] text-xs text-ink-500 animate-pulse flex items-center gap-2">
                            <span className="w-2 h-2 rounded-full bg-brand-500 animate-ping"></span>
                            Calcul officiel des jours ouvrables par le serveur...
                        </div>
                    )}

                    {simulation && !simulating && (
                        <div className="bg-brand-50/60 dark:bg-brand-950/30 border border-brand-200 dark:border-brand-800/50 p-3.5 flex items-center justify-between gap-4 text-xs">
                            <div>
                                <span className="text-ink-600 dark:text-[var(--color-fg-muted)] block text-[11px] uppercase tracking-wider font-semibold">
                                    Jours Ouvrables Réels
                                </span>
                                <span className="font-bold text-lg text-brand-600 dark:text-brand-400 font-data">
                                    {simulation.nbJoursOuvrables} jours
                                </span>
                                <span className="text-[10px] text-ink-500 block">
                                    (Lun - Ven hors fériés chômés)
                                </span>
                            </div>
                            <div className="text-right text-[11px] text-ink-600 dark:text-[var(--color-fg-muted)] space-y-0.5 border-l border-brand-200 dark:border-brand-800/40 pl-4">
                                <div>Total calendaire : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbJoursCalendaires} j</strong></div>
                                <div>Fériés chômés : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbJoursFeries} j</strong></div>
                                <div>Week-ends : <strong className="font-data text-ink-900 dark:text-ink-100">{simulation.nbWeekends} j</strong></div>
                            </div>
                        </div>
                    )}

                    <p className="text-xs text-[var(--color-fg-muted)]">La campagne sera créée en <strong className="text-[var(--color-fg)]">brouillon</strong>. Vous pourrez ensuite ouvrir la saisie du pointage.</p>

                    <div className="pt-4 flex justify-end gap-3 border-t border-ink-150 dark:border-[var(--color-border)]">
                        <Button type="button" variant="ghost" size="sm" onClick={() => setIsCreateModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button type="submit" variant="primary" size="sm" loading={submitting}>
                            Créer la campagne
                        </Button>
                    </div>
                </form>
            </Modal>

            {/* Modale de Confirmation d'Action (Rouvrir / Valider / Clôturer) */}
            <ConfirmDialog
                isOpen={confirmAction.isOpen}
                onClose={() => setConfirmAction(prev => ({ ...prev, isOpen: false }))}
                onConfirm={() => handleExecuteWorkflow(confirmAction.periode, confirmAction.command)}
                title={confirmAction.title}
                message={confirmAction.message}
                confirmLabel={confirmAction.confirmLabel}
                cancelLabel="Annuler"
                variant={confirmAction.variant}
                loading={submitting}
            />
        </div>
    );
}
