import SharedButton from '@/components/ui/forms/Button';
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
    ChevronUp,
    Search
} from 'lucide-react';
import { usePeriodePaie } from '@/features/attendance/hooks/usePeriodePaie';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { getErrorMessage } from '@/api/apiClient';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Input from '@/components/ui/forms/Input';
import { Select } from '@/components/ui/forms/Input';
import Pagination from '@/components/ui/navigation/Pagination';
import DatePicker from '@/components/ui/forms/DatePicker';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useToast } from '@/components/ui/feedback/Toast';
import HasPermission from '@/features/auth/components/HasPermission';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import PayrollWorkflowStatusBadges from '@/features/payroll/components/workflow/PayrollWorkflowStatusBadges';
import Badge from '@/components/ui/data-display/Badge';

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
        label: 'À arbitrer',
        desc: 'Saisie terminée, arbitrage en cours'
    },
    'TRANSMIS_DAF': {
        variant: 'warning',
        label: 'Transmise par le terrain',
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

const normalizeSearch = (value) => String(value || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().trim();

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
    const [campaignSearch, setCampaignSearch] = useState('');
    const [campaignStatus, setCampaignStatus] = useState('');
    const [campaignPage, setCampaignPage] = useState(1);
    const [campaignPageSize, setCampaignPageSize] = useState(10);

    // Effets
    useEffect(() => {
        let active = true;
        const loadVariables = async () => {
            try {
                const response = await paieService.listerAjustements();
                const items = Array.isArray(response) ? response : (response?.content || response?.items || []);
                const periodIds = new Set(periodes.map(period => period.idPeriodePaie || period.id));
                const grouped = Object.fromEntries([...periodIds].map(id => [id, []]));
                for (const item of items) {
                    const id = item.idPeriodePaie;
                    if (periodIds.has(id)) grouped[id].push(item);
                }
                if (active) setVariablesByPeriod(grouped);
            } catch {
                if (active) setVariablesByPeriod(Object.fromEntries(
                    periodes.map(period => [period.idPeriodePaie || period.id, []])
                ));
            }
        };
        if (periodes.length) loadVariables();
        else setVariablesByPeriod({});
        return () => { active = false; };
    }, [periodes]);

    // Données dérivées
    const [nextSuggestion, setNextSuggestion] = useState(null);
    useEffect(() => {
        let active = true;
        periodePaieService.suggererPeriode()
            .then(suggestion => { if (active) setNextSuggestion(suggestion); })
            .catch(() => { if (active) setNextSuggestion(null); });
        return () => { active = false; };
    }, [periodes]);
    const filteredPeriodes = useMemo(() => {
        const query = normalizeSearch(campaignSearch);
        return periodes.filter((period) => {
            const statusCode = period.statutCode || period.workflowStatuses?.pointage?.code || period.workflowStatuses?.paie?.code || '';
            const searchable = normalizeSearch([period.libelle, period.code, period.dateDebut, period.dateFin].join(' '));
            return (!query || searchable.includes(query)) && (!campaignStatus || statusCode === campaignStatus);
        });
    }, [periodes, campaignSearch, campaignStatus]);
    const visiblePeriodes = useMemo(() => {
        const start = (campaignPage - 1) * campaignPageSize;
        return filteredPeriodes.slice(start, start + campaignPageSize);
    }, [filteredPeriodes, campaignPage, campaignPageSize]);

    useEffect(() => { setCampaignPage(1); }, [campaignSearch, campaignStatus, campaignPageSize]);

    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isCustomCreate, setIsCustomCreate] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [newPeriode, setNewPeriode] = useState({
        code: '',
        libelle: '',
        dateDebut: '',
        dateFin: ''
    });

    // La suggestion de période et celle d'une date cible viennent du même service.
    useEffect(() => {
        const action = searchParams.get('action');
        if (action !== 'create' || !nextSuggestion) return;
        let active = true;
        const targetDate = searchParams.get('targetDate');
        const request = targetDate
            ? periodePaieService.suggererPeriode({ targetDate })
            : Promise.resolve(nextSuggestion);
        request.then(initial => {
            if (!active) return;
            setNewPeriode({ code: initial.code, libelle: initial.libelle,
                dateDebut: initial.dateDebut, dateFin: initial.dateFin });
            setIsCustomCreate(Boolean(targetDate));
            setIsCreateModalOpen(true);
            setSearchParams(prev => {
                const next = new URLSearchParams(prev);
                next.delete('action'); next.delete('targetDate');
                return next;
            }, { replace: true });
        }).catch(() => {});
        return () => { active = false; };
    }, [searchParams, nextSuggestion, setSearchParams]);

    // Actions
    const handleOpenCreateWithSuggestion = () => {
        if (!nextSuggestion) return;
        setIsCustomCreate(false);
        setNewPeriode({
            code: nextSuggestion?.code,
            libelle: nextSuggestion?.libelle,
            dateDebut: nextSuggestion?.dateDebut,
            dateFin: nextSuggestion?.dateFin
        });
        setIsCreateModalOpen(true);
    };

    const applyCycle = async (civil) => {
        const code = /^\d{4}-(0[1-9]|1[0-2])$/.test(newPeriode.code)
            ? newPeriode.code : newPeriode.dateFin?.slice(0, 7) || nextSuggestion?.code;
        if (!code) return;
        try {
            const suggestion = await periodePaieService.suggererPeriode({ code, civil });
            setNewPeriode(prev => ({ ...prev, dateDebut: suggestion.dateDebut,
                dateFin: suggestion.dateFin }));
        } catch (error) {
            toast({ title: 'Période invalide', message: getErrorMessage(error), type: 'error' });
        }
    };
    const handleApplyCycle2625 = () => applyCycle(false);
    const handleApplyCycleCivil = () => applyCycle(true);

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
                        <h2 className="mt-1 text-lg font-bold text-[var(--color-fg)]">{nextSuggestion?.libelle} <span className="font-data text-sm font-medium text-[var(--color-fg-muted)]">· {nextSuggestion?.code}</span></h2>
                        <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Du {nextSuggestion?.dateDebut} au {nextSuggestion?.dateFin} · Cycle du 26 au 25</p>
                        <p className="mt-2 text-xs text-[var(--color-fg-muted)]">Créée en brouillon ; la saisie du pointage s’ouvre ensuite.</p>
                    </div>
                    <HasPermission authority={['PAIE_WRITE', 'PAIE_VALIDATE', 'POINTAGE_VALIDATE']}>
                        <div className="flex flex-wrap items-center gap-2">
                            <Button onClick={handleOpenCreateWithSuggestion} disabled={!nextSuggestion} variant="primary" size="sm" iconLeft={<Plus size={14} />}>Créer la campagne</Button>
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
                            <h2 className="mt-1 text-lg font-bold text-[var(--color-fg)]">{activeCycle.libelle}</h2>
                            <p className="mt-1 font-data text-xs text-[var(--color-fg-muted)]">{activeCycle.dateDebut} → {activeCycle.dateFin}</p>
                        </div>
                        {(() => {
                            const dest = activeCycle.nextStep?.destination
                                || `/paie/periodes/${activeCycle.idPeriodePaie || activeCycle.id}/preparation`;
                            return (
                                <Button size="sm" variant="primary" iconRight={<ArrowRight size={14} />} onClick={() => navigate(dest)}>
                                    Continuer la campagne
                                </Button>
                            );
                        })()}
                    </div>
                    <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                        {[
                            ['Pointage', activeCycle.workflowStatuses?.pointage],
                            ['Paie', activeCycle.workflowStatuses?.paie],
                        ].map(([label, status]) => (
                            <div key={label} className="flex min-w-0 items-start justify-between gap-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-3 py-2.5">
                                <span className="text-xs font-medium text-[var(--color-fg-muted)]">{label}</span>
                                <Badge variant={status?.tone || 'neutral'} size="xs" className="max-w-[70%] whitespace-normal text-right normal-case tracking-normal">
                                    {status?.label || 'Statut indisponible'}
                                </Badge>
                            </div>
                        ))}
                    </div>
                    <p className="mt-3 text-xs text-[var(--color-fg-muted)]">
                        Variables rattachées à la campagne : <strong className="font-data text-[var(--color-fg)]">{(variablesByPeriod[activeCycle.idPeriodePaie || activeCycle.id] || []).length}</strong>
                    </p>
                </section>
            )}

            {error && <Alert type="danger" message={error} onClose={() => setError(null)} />}

            {/* Campagnes et actions de suivi */}
            <section className="space-y-3" aria-labelledby="campagnes-creees-title">
                <div className="flex flex-wrap items-end justify-between gap-2">
                    <div>
                        <h2 id="campagnes-creees-title" className="text-base font-bold text-[var(--color-fg)]">Campagnes créées</h2>
                        <p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">Recherchez une période, vérifiez son état et reprenez le travail à l’étape disponible.</p>
                    </div>
                    {periodes.length > 0 && <span className="text-xs tabular-nums text-[var(--color-fg-muted)]">{filteredPeriodes.length} / {periodes.length} période{periodes.length > 1 ? 's' : ''}</span>}
                </div>

                {loading && periodes.length === 0 ? (
                    <SkeletonTable rows={3} cols={3} />
                ) : periodes.length === 0 ? (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-8 text-center text-sm text-[var(--color-fg-muted)]">
                        Aucune campagne créée pour le moment.
                    </div>
                ) : (
                    <>
                    <div className="flex flex-col items-stretch gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 sm:flex-row sm:items-center">
                        <Input
                            aria-label="Rechercher une campagne"
                            placeholder="Rechercher par libellé, code ou dates…"
                            icon={<Search size={16} />}
                            value={campaignSearch}
                            onChange={(event) => setCampaignSearch(event.target.value)}
                            className="w-full sm:max-w-[560px]"
                        />
                        <Select
                            aria-label="Filtrer les campagnes par statut"
                            value={campaignStatus}
                            onChange={(event) => setCampaignStatus(event.target.value)}
                            className="w-full sm:w-[260px]"
                        >
                            <option value="">Tous les statuts</option>
                            {Object.entries(STATUT_CONFIG).map(([code, status]) => (
                                <option key={code} value={code}>{status.label}</option>
                            ))}
                        </Select>
                    </div>
                    <div className={`space-y-3 transition-opacity duration-200 ${loading ? 'opacity-50 pointer-events-none' : ''}`}>
                        {filteredPeriodes.length === 0 ? (
                            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-8 text-center text-sm text-[var(--color-fg-muted)]">
                                Aucune campagne ne correspond aux filtres.
                            </div>
                        ) : visiblePeriodes.map(p => {
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
                                                    <span className="font-data text-xs text-[var(--color-brand-600)]">{p.code}</span>
                                                </div>
                                                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                                                    <span className="font-data">{p.dateDebut} → {p.dateFin}</span>
                                                    <span className="mx-2" aria-hidden="true">·</span>
                                                    {p.nbJoursOuvrables ?? '—'} jours ouvrables
                                                    {p.nbJoursFeries > 0 && <span> · {p.nbJoursFeries} jour(s) férié(s)</span>}
                                                </p>
                                            </div>
                                            <div className="flex flex-wrap items-center gap-2 lg:justify-end">
                                                {(() => {
                                                    const isClosed = p.statutCode === 'CLOTUREE' || p.workflowStatuses?.paie?.code === 'CLOTUREE';
                                                    const targetDestination = p.nextStep?.destination
                                                        || (isClosed ? `/paie/periodes/${periodId}/sorties` : `/paie/periodes/${periodId}/preparation`);
                                                    if (isClosed) {
                                                        return (
                                                            <Button
                                                                size="sm"
                                                                variant="outline"
                                                                iconRight={<ArrowRight size={14} />}
                                                                onClick={() => navigate(targetDestination)}
                                                            >
                                                                Consulter
                                                            </Button>
                                                        );
                                                    }
                                                    return (
                                                        <Button
                                                            size="sm"
                                                            variant="primary"
                                                            iconRight={<ArrowRight size={14} />}
                                                            onClick={() => navigate(targetDestination)}
                                                        >
                                                            Continuer
                                                        </Button>
                                                    );
                                                })()}
                                                {mainAction && (() => {
                                                    const [command, config] = mainAction;
                                                    const ActionIcon = config.Icon;
                                                    return <Button size="sm" variant="secondary" icon={<ActionIcon size={14} />} onClick={() => triggerWorkflowAction(p, command)}>{config.label}</Button>;
                                                })()}
                                            </div>
                                        </div>
                                        <div className="mt-4 pt-3 border-t border-[var(--color-border-subtle)] flex flex-wrap items-center gap-x-5 gap-y-2 text-xs">
                                            <PayrollWorkflowStatusBadges statuses={p.workflowStatuses} size="xs" />
                                            <SharedButton plain
                                                type="button"
                                                aria-expanded={isExpanded}
                                                aria-controls={`campagne-details-${periodId}`}
                                                onClick={() => setExpandedPeriodId(isExpanded ? null : periodId)}
                                                className="ml-auto inline-flex items-center gap-1.5 font-semibold text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] focus-visible:outline-2 focus-visible:outline-[var(--color-brand-500)] cursor-pointer"
                                            >
                                                Variables ({variables.length}) et détails {isExpanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                                            </SharedButton>
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
                                                            <strong className="font-data whitespace-nowrap text-[var(--color-fg)]">{formatMoneyAr(variable.montant ?? variable.montantDemande ?? 0)}</strong>
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
                    <Pagination
                        currentPage={campaignPage}
                        totalItems={filteredPeriodes.length}
                        pageSize={campaignPageSize}
                        pageSizeOptions={[10, 25, 50]}
                        onPageChange={setCampaignPage}
                        onPageSizeChange={setCampaignPageSize}
                    />
                    </>
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
                        <div className="p-3 bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/25 border border-[var(--color-brand-200)] dark:border-[var(--color-brand-700)]/50 flex items-center justify-between gap-3 text-xs">
                            <div className="flex items-center gap-2">
                                <Sparkles size={16} className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-300)] shrink-0" />
                                <span className="text-[var(--color-fg)]">
                                    Suite suggérée : <strong>{nextSuggestion?.libelle}</strong> ({nextSuggestion?.dateDebut} au {nextSuggestion?.dateFin})
                                </span>
                            </div>
                            <Button
                                variant="secondary"
                                size="xs"
                                type="button"
                                onClick={() => setNewPeriode({
                                    code: nextSuggestion?.code,
                                    libelle: nextSuggestion?.libelle,
                                    dateDebut: nextSuggestion?.dateDebut,
                                    dateFin: nextSuggestion?.dateFin
                                })}
                            >
                                Appliquer la suggestion
                            </Button>
                        </div>
                    )}

                    <div className="space-y-1.5">
                        <div className="space-y-2">
                            <span className="text-xs font-semibold text-[var(--color-fg)]">Type de cycle</span>
                            <div className="flex flex-wrap items-center gap-1.5">
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    type="button"
                                    onClick={handleApplyCycle2625}
                                    title="Appliquer le cycle standard de paie du 26 au 25"
                                >
                                    Du 26 au 25
                                </Button>
                                <Button
                                    variant="secondary"
                                    size="xs"
                                    type="button"
                                    onClick={handleApplyCycleCivil}
                                    title="Appliquer le mois civil du 1er au dernier jour"
                                >
                                    Mois civil
                                </Button>
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
                        <div className="p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)] animate-pulse flex items-center gap-2">
                            <span className="w-2 h-2 rounded-full bg-[var(--color-brand-500)] animate-ping"></span>
                            Calcul officiel des jours ouvrables par le serveur...
                        </div>
                    )}

                    {simulation && !simulating && (
                        <div className="bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)]/40 border border-[var(--color-brand-200)] dark:border-[var(--color-brand-800)]/50 p-3.5 flex items-center justify-between gap-4 text-xs">
                            <div>
                                <span className="text-[var(--color-fg-muted)] block text-[11px] uppercase tracking-wider font-semibold">
                                    Jours Ouvrables Réels
                                </span>
                                <span className="font-bold text-lg text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-data">
                                    {simulation.nbJoursOuvrables} jours
                                </span>
                                <span className="text-[10px] text-[var(--color-fg-subtle)] block">
                                    (Lun - Ven hors fériés chômés)
                                </span>
                            </div>
                            <div className="text-right text-[11px] text-[var(--color-fg-muted)] space-y-0.5 border-l border-[var(--color-brand-200)] dark:border-[var(--color-brand-800)]/40 pl-4">
                                <div>Total calendaire : <strong className="font-data text-[var(--color-fg)]">{simulation.nbJoursCalendaires} j</strong></div>
                                <div>Fériés chômés : <strong className="font-data text-[var(--color-fg)]">{simulation.nbJoursFeries} j</strong></div>
                                <div>Week-ends : <strong className="font-data text-[var(--color-fg)]">{simulation.nbWeekends} j</strong></div>
                            </div>
                        </div>
                    )}

                    <p className="text-xs text-[var(--color-fg-muted)]">La campagne sera créée en <strong className="text-[var(--color-fg)]">brouillon</strong>. Vous pourrez ensuite ouvrir la saisie du pointage.</p>

                    <div className="pt-4 flex justify-end gap-3 border-t border-[var(--color-border-subtle)]">
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
