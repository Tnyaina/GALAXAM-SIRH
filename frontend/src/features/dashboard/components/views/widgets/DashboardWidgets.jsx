import { memo, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
    AlertTriangle,
    ArrowRight,
    CalendarCheck,
    CalendarRange,
    ChevronRight,
    CircleAlert,
    ClipboardCheck,
    FileText,
    ShieldAlert,
    TrendingUp,
    UserCheck,
    WalletCards,
} from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Skeleton } from '@/components/ui/feedback/Skeleton';
import { useAuth } from '@/context/AuthContext';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import { fmtDate } from '@/features/dashboard/utils/dashboardUtils';
import { fmtRelative } from '@/utils/dateUtils';
import { ExpandableList, SkeletonListRows } from './WidgetPrimitives';

/**
 * AbsentsWidget — Liste des salariés actuellement absents ou en congé ce jour.
 */
export function AbsentsWidget({ loading, absentsAujourdhui = [], className = '' }) {
    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <UserCheck size={16} className="text-[var(--color-info-500)] shrink-0" />
                        <CardTitle>Salariés Actuellement Absents / En Congé</CardTitle>
                    </div>
                    <Badge variant={absentsAujourdhui.length > 0 ? 'info' : 'default'} size="sm" className="shrink-0">
                        {loading ? '…' : absentsAujourdhui.length} absent(s)
                    </Badge>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && absentsAujourdhui.length === 0 && (
                    <EmptyState compact description="Aucun collaborateur en congé ou absent aujourd'hui." />
                )}
                {!loading && absentsAujourdhui.length > 0 && (
                    <ExpandableList
                        items={absentsAujourdhui}
                        initialCount={5}
                        renderItem={(absence) => (
                            <div key={absence.idDemandeConge} className="py-3 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {absence.nomPrenom}{' '}
                                        <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">
                                            (#{absence.matricule})
                                        </span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {absence.typeConge} · Retour prévu :{' '}
                                        <span className="font-data font-semibold text-[var(--color-success-500)]">
                                            {fmtDate(absence.dateFin)}
                                        </span>
                                    </p>
                                </div>
                                <span className="px-2 py-0.5 rounded-none text-[10px] font-data font-bold bg-[var(--color-info-500)]/10 text-[var(--color-info-500)] border border-[var(--color-info-500)]/20 shrink-0">
                                    {absence.nbJoursDecomptes}j posés
                                </span>
                            </div>
                        )}
                    />
                )}
            </CardBody>
        </Card>
    );
}

/**
 * ActiviteRow — Ligne d'activité récente pilotée par les statuts et booléens backend.
 */
const ActiviteRow = memo(function ActiviteRow({ ev }) {
    // Statut et variant pilotés par le backend (POO DashboardService)
    const badgeVariant = ev.badgeVariant || (ev.isValide ? 'success' : ev.isPlanifie ? 'info' : ev.isRejete ? 'danger' : 'default');
    const statutText = ev.statutLibelleAffichage || ev.statutLibelle || (ev.isValide ? 'Validé' : ev.isRejete ? 'Rejeté' : 'En cours');

    return (
        <Link
            to={`/employes/${ev.idEmploye}?tab=${ev.sourceType === 'CONGE' ? 'conges' : 'carriere'}`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] group transition-colors"
        >
            <Avatar name={ev.nomPrenom} size="sm" shape="square" />
            <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{ev.nomPrenom}</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{ev.typeEvenement}</p>
            </div>
            <div className="text-right shrink-0">
                <Badge variant={badgeVariant} size="sm">{statutText}</Badge>
                <p className="text-[10px] text-[var(--color-fg-subtle)] mt-0.5 font-data">
                    {fmtRelative(ev.dateAction || ev.updatedAt)}
                </p>
            </div>
        </Link>
    );
});

/**
 * ActiviteRhWidget — Journal d'activité récente & mouvements RH.
 */
export function ActiviteRhWidget({ loading, activite = [] }) {
    return (
        <Card>
            <CardHeader>
                <div className="flex items-center gap-2">
                    <TrendingUp size={16} className="text-[var(--color-brand-600)]" />
                    <CardTitle>Activité Récente & Mouvements RH</CardTitle>
                </div>
            </CardHeader>
            <CardBody>
                {loading && <SkeletonListRows count={3} />}
                {!loading && activite.length === 0 && (
                    <EmptyState compact description="Aucun mouvement RH récent." />
                )}
                {!loading && activite.length > 0 && (
                    <ExpandableList
                        items={activite}
                        initialCount={5}
                        renderItem={(ev, index) => (
                            <ActiviteRow
                                key={ev.sourceType ? `${ev.sourceType}-${ev.id}` : (ev.id ?? index)}
                                ev={ev}
                            />
                        )}
                    />
                )}
            </CardBody>
        </Card>
    );
}

/**
 * AlerteRow — Ligne d'alerte contrat / période d'essai pilotée par les booléens backend.
 */
const AlerteRow = memo(function AlerteRow({ alerte }) {
    // Booléens & sévérité fournis directement par le backend
    const isToday = alerte.isToday ?? (alerte.joursRestants === 0);
    const isUrgent = alerte.isUrgent ?? (alerte.joursRestants <= 7);
    const badgeVariant = alerte.badgeVariant || (isToday ? 'danger' : isUrgent ? 'warning' : 'primary');

    return (
        <Link
            to={`/employes/${alerte.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] group transition-colors"
        >
            <div
                className={`w-2 h-2 rounded-none shrink-0 ${
                    isToday
                        ? 'bg-[var(--color-danger-500)]'
                        : isUrgent
                        ? 'bg-[var(--color-warning-500)]'
                        : 'bg-[var(--color-brand-500)]'
                }`}
            />
            <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{alerte.nomPrenom}</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                    {alerte.typeContrat}
                    {alerte.numeroContrat ? ` · N°${alerte.numeroContrat}` : ''}
                    {' · '}
                    <span className="font-data">{fmtDate(alerte.dateEcheance)}</span>
                </p>
            </div>
            <Badge variant={badgeVariant} size="sm">
                {isToday ? 'Auj.' : `J-${alerte.joursRestants}`}
            </Badge>
            <ChevronRight
                size={13}
                className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-600)] shrink-0 transition-transform"
            />
        </Link>
    );
});

/**
 * EcheancesWidget — Alertes sur les contrats et périodes d'essai arrivant à échéance.
 */
export function EcheancesWidget({
    loading,
    alertesCritiques = [],
    alertes7j = [],
    alertes30j = [],
    totalAlertesContrats = 0,
    className = '',
}) {
    const alertes = [...alertesCritiques, ...alertes7j, ...alertes30j];
    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <AlertTriangle size={16} className="text-[var(--color-danger-500)] shrink-0" />
                        <CardTitle>Échéances de Contrats & Périodes d'Essai (7 à 30 jours)</CardTitle>
                    </div>
                    <Badge variant={totalAlertesContrats > 0 ? 'warning' : 'default'} size="sm" className="shrink-0">
                        {loading ? '…' : totalAlertesContrats} alerte(s)
                    </Badge>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && alertes.length === 0 && (
                    <EmptyState
                        compact
                        description="Aucun contrat ni période d'essai n'arrive à échéance dans les 30 prochains jours."
                    />
                )}
                {!loading && alertes.length > 0 && (
                    <ExpandableList
                        items={alertes}
                        initialCount={6}
                        renderItem={(alerte, index) => <AlerteRow key={index} alerte={alerte} />}
                    />
                )}
            </CardBody>
        </Card>
    );
}

const queueIcons = {
    CONGES_A_VALIDER: CalendarCheck,
    BROUILLONS_RH: FileText,
    ANOMALIES_POINTAGE: AlertTriangle,
    ECHEANCES_CONTRATS: AlertTriangle,
};

/**
 * OperationalQueueWidget — File d'actions et priorités opérationnelles unifiée.
 */
export function OperationalQueueWidget({ loading, actions = [], className = '' }) {
    const { hasPermission } = useAuth();
    const items = actions.filter((item) => !item.requiredAuthority || hasPermission(item.requiredAuthority));
    const totalActions = items.reduce((sum, item) => sum + (Number(item.count) || 0), 0);

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <ShieldAlert size={16} className="text-[var(--color-brand-600)]" />
                    <CardTitle>Priorités opérationnelles</CardTitle>
                </div>
                <Badge variant={!loading && items.length === 0 ? 'success' : 'warning'} size="sm">
                    {!loading && items.length === 0 ? '0 à suivre' : loading ? '…' : `${totalActions} à traiter`}
                </Badge>
            </CardHeader>
            <CardBody className="flex-1">
                {loading && <SkeletonListRows count={3} />}
                {!loading && items.length === 0 && (
                    <div className="h-full flex items-center justify-center">
                        <EmptyState
                            compact
                            icon={ClipboardCheck}
                            title="Aucune priorité opérationnelle"
                            description="Les décisions et contrôles de votre périmètre sont à jour."
                        />
                    </div>
                )}
                {!loading &&
                    items.map((item) => {
                        const Icon = queueIcons[item.code] || ShieldAlert;
                        const danger = item.isDanger ?? (item.severity === 'DANGER');
                        return (
                            <Link
                                key={item.code}
                                to={item.destination}
                                className="group flex items-center gap-3 border-b last:border-b-0 border-[var(--color-border-subtle)] py-3 first:pt-0 last:pb-0 hover:bg-[var(--color-bg-subtle)] transition-colors"
                            >
                                <span
                                    className={`p-2 rounded-none ${
                                        danger
                                            ? 'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)]'
                                            : 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)]'
                                    }`}
                                >
                                    <Icon size={16} />
                                </span>
                                <span className="min-w-0 flex-1">
                                    <span className="block text-xs font-bold text-[var(--color-fg)]">{item.label}</span>
                                    <span className="block text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {item.description}
                                    </span>
                                </span>
                                <Badge variant={danger ? 'danger' : 'warning'} size="sm">
                                    {item.count}
                                </Badge>
                                <ChevronRight
                                    size={14}
                                    className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-600)] transition-transform"
                                />
                            </Link>
                        );
                    })}
            </CardBody>
        </Card>
    );
}

const stateVariant = (state) =>
    ({
        COMPLETED: 'success',
        CURRENT: 'primary',
        WARNING: 'warning',
        STALE: 'warning',
        BLOCKED: 'danger',
        LOCKED: 'neutral',
    }[state] || 'neutral');

/**
 * PayrollCycleWidget — Synthèse du cycle Pointage → Paie et prochaine étape recommandée.
 */
export function PayrollCycleWidget({ className = '' }) {
    const { context, loading } = usePayrollCampaign();
    const period = context?.selectedPeriod || context?.currentPeriod;
    const nextStep = context?.nextStep;
    const visibleSteps = (context?.campaignSteps || []).slice(0, 5);
    const primaryIssue = (context?.issues || [])[0];
    const issueMessage =
        typeof primaryIssue === 'string'
            ? primaryIssue
            : primaryIssue?.message || primaryIssue?.label || primaryIssue?.code || null;

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <WalletCards size={16} className="text-[var(--color-brand-600)]" />
                    <CardTitle>Cycle Pointage → Paie</CardTitle>
                </div>
                {period && <Badge variant="primary" size="sm">{period.code || period.libelle}</Badge>}
            </CardHeader>
            <CardBody className="flex-1">
                {loading && (
                    <div className="space-y-3">
                        <Skeleton className="h-5 w-2/3" />
                        <Skeleton className="h-14 w-full" />
                    </div>
                )}
                {!loading && !period && (
                    <EmptyState
                        compact
                        description="Aucune période de paie sélectionnée. Créez ou ouvrez une période pour démarrer le cycle."
                    />
                )}
                {!loading && period && (
                    <>
                        <p className="text-xs text-[var(--color-fg-muted)] flex items-center gap-1.5">
                            <CalendarRange size={14} /> {period.startDate || '—'} → {period.endDate || '—'}
                        </p>
                        <div className="mt-4 grid grid-cols-2 sm:grid-cols-5 border border-[var(--color-border-subtle)]">
                            {visibleSteps.map((step) => (
                                <div
                                    key={step.code}
                                    className="p-2.5 border-b sm:border-b-0 sm:border-r last:border-r-0 border-[var(--color-border-subtle)]"
                                >
                                    <Badge variant={stateVariant(step.state)} size="xs">
                                        {step.state === 'COMPLETED'
                                            ? 'Terminé'
                                            : step.state === 'CURRENT'
                                            ? 'En cours'
                                            : 'À venir'}
                                    </Badge>
                                    <p
                                        className="mt-1 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg)] truncate"
                                        title={step.label}
                                    >
                                        {step.label}
                                    </p>
                                </div>
                            ))}
                        </div>
                        {issueMessage && (
                            <p className="mt-3 flex gap-2 text-[11px] text-[var(--color-danger-600)]">
                                <CircleAlert size={14} className="shrink-0" />
                                {issueMessage}
                            </p>
                        )}
                        {nextStep?.destination && (
                            <Link
                                to={nextStep.destination}
                                className="mt-4 inline-flex items-center gap-2 text-xs font-bold text-[var(--color-brand-600)] hover:text-[var(--color-brand-700)] transition-colors"
                            >
                                {nextStep.label || 'Reprendre le cycle'} <ArrowRight size={14} />
                            </Link>
                        )}
                    </>
                )}
            </CardBody>
        </Card>
    );
}

/**
 * PlanningQuinzaineWidget — Supervision des congés sur 15 jours avec filtrage par site.
 */
export function PlanningQuinzaineWidget({ loading, absences = [], sites = [], className = '' }) {
    const [selectedSiteFilter, setSelectedSiteFilter] = useState('ALL');
    const filteredAbsences = useMemo(
        () =>
            selectedSiteFilter === 'ALL'
                ? absences
                : absences.filter((absence) => String(absence.idSite || absence.nomSite) === String(selectedSiteFilter)),
        [absences, selectedSiteFilter]
    );

    const hasAbsences = filteredAbsences.length > 0;

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between gap-3 w-full">
                    <div className="flex items-center gap-2">
                        <CalendarRange size={16} className="text-[var(--color-brand-600)] shrink-0" />
                        <CardTitle>Supervision des Départs & Congés (15 Jours)</CardTitle>
                    </div>
                    {/* N'affiche les filtres et le lien secondaire que lorsqu'il y a du contenu */}
                    {hasAbsences && (
                        <div className="flex items-center gap-2">
                            {sites.length > 0 && (
                                <div className="flex items-center gap-1 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] p-0.5 text-xs">
                                    <button
                                        onClick={() => setSelectedSiteFilter('ALL')}
                                        className={`px-2 py-0.5 font-semibold transition-colors ${
                                            selectedSiteFilter === 'ALL'
                                                ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                                : 'text-[var(--color-fg-muted)]'
                                        }`}
                                    >
                                        Tous
                                    </button>
                                    {sites.map((site) => (
                                        <button
                                            key={site.idSite}
                                            onClick={() => setSelectedSiteFilter(site.idSite)}
                                            className={`px-2 py-0.5 font-semibold transition-colors ${
                                                String(selectedSiteFilter) === String(site.idSite)
                                                    ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)]'
                                                    : 'text-[var(--color-fg-muted)]'
                                            }`}
                                        >
                                            {site.codeSite || site.nomSite}
                                        </button>
                                    ))}
                                </div>
                            )}
                            <Link
                                to="/conges?tab=soldes"
                                className="text-xs font-bold text-[var(--color-brand-600)] hover:underline flex items-center gap-1"
                            >
                                Planning <ArrowRight size={12} />
                            </Link>
                        </div>
                    )}
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && !hasAbsences && (
                    <EmptyState
                        compact
                        description="Aucun départ en congé programmé sur la quinzaine."
                        actionLabel="Consulter le planning"
                        onAction={() => { window.location.href = '/conges?tab=soldes'; }}
                    />
                )}
                {!loading && hasAbsences && (
                    <div className="max-h-[300px] overflow-y-auto pr-1 divide-y divide-[var(--color-border-subtle)]">
                        {filteredAbsences.map((absence, index) => (
                            <div key={index} className="py-2.5 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {absence.nomPrenom}{' '}
                                        <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">
                                            ({absence.nomSite || 'TNR'})
                                        </span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {absence.typeConge || 'Congé'} · Du{' '}
                                        <span className="font-data">{fmtDate(absence.dateDebut)}</span> au{' '}
                                        <span className="font-data text-[var(--color-brand-600)] font-semibold">
                                            {fmtDate(absence.dateFin)}
                                        </span>
                                    </p>
                                </div>
                                <span className="font-data font-bold text-xs bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] px-2 py-0.5 border border-[var(--color-brand-500)]/20">
                                    {absence.nbJoursDecomptes || absence.nbJours || 1}j
                                </span>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
