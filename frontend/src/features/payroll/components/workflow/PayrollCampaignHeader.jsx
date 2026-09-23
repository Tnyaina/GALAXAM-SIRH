import { useMemo } from 'react';
import { CalendarDays, RefreshCcw, AlertTriangle, LayoutDashboard, Users } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import PayrollPeriodStepper from '@/features/payroll/components/workflow/PayrollPeriodStepper';
import PayrollIssuePanel from '@/features/payroll/components/feedback/PayrollIssuePanel';

/**
 * PayrollCampaignHeader — Barre de commande supérieure pour les étapes de paie.
 *
 * Affiche :
 * - Le fil d'Ariane et le sélecteur réactif de période
 * - Le statut actuel avec badge coloré
 * - L'indicateur de recalcul requis (si variables modifiées ou anomalies)
 * - Le bouton "Recalculer" avec confirmation
 * - Le Stepper horizontal interactif (les 7 étapes du workflow)
 *
 * @prop {Object}   context           - Objet retourné par usePayrollCampaign (selectedPeriod, steps, etc.)
 * @prop {Array}    periodes          - Liste des périodes disponibles
 * @prop {Function} onSelectPeriod    - Callback lorsqu'une nouvelle période est choisie
 * @prop {string}   activeStep        - Code de l'étape actuellement affichée (page active)
 * @prop {boolean}  loading           - Affiche l'état de chargement dans le stepper
 * @prop {Function} onRefresh         - Callback déclenché au clic sur "Recalculer"
 * @prop {boolean}  refreshLoading   - Vrai pendant le recalcul
 */
const STATUT_VARIANT = {
    EN_COURS: 'warning',
    A_VALIDER: 'info',
    TRANSMIS_DAF: 'info',
    VALIDEE: 'primary',
    PAIE_VALIDEE: 'success',
    CLOTUREE: 'secondary',
    BROUILLON: 'default',
};

const STATUT_LABEL = {
    EN_COURS: 'Pointage En Cours',
    A_VALIDER: 'Transmise DAF',
    TRANSMIS_DAF: 'Transmise DAF',
    VALIDEE: 'Pointage Scellé & Figé',
    PAIE_VALIDEE: 'Paie Validée & Approuvée',
    CLOTUREE: 'Période Clôturée',
    BROUILLON: 'Brouillon',
};

export default function PayrollCampaignHeader({
    context,
    periodes = [],
    onSelectPeriod,
    activeStep,
    loading = false,
    onRefresh,
    refreshLoading = false,
}) {
    const navigate = useNavigate();
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const steps = context?.campaignSteps || [];
    const currentStep = context?.currentStep;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;
    const completedSteps = steps.filter(step => step.state === 'COMPLETED').length;
    const progress = steps.length > 0 ? Math.round((completedSteps / steps.length) * 100) : 0;
    const blockers = context?.issues || [];
    const activeStepContext = steps.find(step => step.code === activeStep);
    const population = context?.populationCount ?? context?.employeeCount ?? periode?.employeeCount;

    const formatDate = (d) => {
        if (!d) return '–';
        return new Date(d).toLocaleDateString('fr-MG', { day: '2-digit', month: 'short', year: 'numeric' });
    };

    const activePeriodId = periode?.id;

    const periodOptions = useMemo(() => {
        return (periodes || []).map(p => {
            const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
            const pCode = p.code || '';
            const pLib = p.libelle || p.statutLibelle || '';
            const pStat = p.statutCode || p.codeStatut || p.statut || '';
            const readableStat = STATUT_LABEL[pStat] || pStat || 'Pointage En Cours';
            return {
                value: String(pId),
                label: `${pCode}${pLib ? ` — ${pLib}` : ''} (${readableStat})`
            };
        });
    }, [periodes]);

    return (
        <div className="sticky top-0 z-30 bg-[var(--color-bg)] border-b border-[var(--color-border-subtle)] shadow-sm">

            <div className="px-4 pt-2 text-[10px] text-[var(--color-fg-muted)]" aria-label="Fil d’Ariane">
                <button type="button" onClick={() => navigate('/paie')} className="hover:text-[var(--color-brand-600)] focus:outline-none focus:underline">Paie</button>
                <span aria-hidden="true"> / </span>
                <span>{periode?.label || periode?.code || 'Période'}</span>
                <span aria-hidden="true"> / </span>
                <span className="font-semibold text-[var(--color-fg)]">{steps.find(step => step.code === activeStep)?.label || 'Campagne'}</span>
            </div>

            {/* Ligne de titre période & sélecteur */}
            <div className="px-4 py-2.5 flex items-center justify-between gap-3 flex-wrap">
                <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 bg-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-sm">
                        <CalendarDays size={16} strokeWidth={2.2} />
                    </div>
                    <div className="min-w-0 flex items-center gap-2.5 flex-wrap">
                        {/* Sélecteur de période réactif */}
                        {periodOptions.length > 0 ? (
                            <SearchSelect
                                value={activePeriodId ? String(activePeriodId) : ''}
                                onChange={(val) => onSelectPeriod && onSelectPeriod(Number(val))}
                                options={periodOptions}
                                className="w-64"
                            />
                        ) : (
                            <h2 className="text-sm font-bold text-[var(--color-fg)] truncate">
                                {periode?.code ? `Période ${periode.code}` : 'Campagne de paie'}
                            </h2>
                        )}

                        {/* Badge statut période */}
                        {periode?.state && (
                            <Badge
                                variant={STATUT_VARIANT[periode.state] || 'default'}
                                size="sm"
                                dot
                            >
                                {STATUT_LABEL[periode.state] || periode.state || 'En cours'}
                            </Badge>
                        )}

                        {(periode?.startDate || periode?.endDate) && (
                            <span className="text-[11px] text-[var(--color-fg-muted)] font-mono truncate hidden sm:inline">
                                {formatDate(periode.startDate)} → {formatDate(periode.endDate)}
                                {periode.label && (
                                    <span className="ml-1 text-[var(--color-fg-subtle)]">({periode.label})</span>
                                )}
                            </span>
                        )}
                        {population != null && (
                            <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)]">
                                <Users size={12} aria-hidden="true" /> {population} salariés
                            </span>
                        )}
                        {blockers.length > 0 && (
                            <Badge variant="danger" size="sm">{blockers.length} bloquant{blockers.length > 1 ? 's' : ''}</Badge>
                        )}
                    </div>
                </div>

                {/* Actions rapides */}
                <div className="flex items-center gap-2 shrink-0">
                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<LayoutDashboard size={13} />}
                        onClick={() => navigate(activePeriodId ? `/paie?periodId=${activePeriodId}` : '/paie')}
                        aria-label="Retour au tableau de bord paie"
                    >
                        Tableau de bord
                    </Button>
                    <Button
                        variant="ghost"
                        size="xs"
                        iconLeft={<RefreshCcw size={12} className={refreshLoading ? 'animate-spin' : ''} />}
                        onClick={onRefresh}
                        disabled={refreshLoading}
                        aria-label="Actualiser le contexte de la campagne"
                    >
                        Actualiser
                    </Button>
                </div>
            </div>

            {/* Stepper horizontal */}
            <PayrollPeriodStepper
                steps={steps}
                currentStep={currentStep}
                activeCode={activeStep}
                loading={loading}
            />

            {activeStepContext?.readOnly && (
                <div className="border-t border-[var(--color-border-subtle)] p-2">
                    <PayrollIssuePanel
                        severity="INFO"
                        title="Consultation en lecture seule"
                        message="Cette campagne est verrouillée. Les données restent consultables, mais aucune mutation n’est autorisée."
                        code="PAYROLL_CAMPAIGN_READ_ONLY"
                    />
                </div>
            )}

            {steps.length > 0 && (
                <div className="px-4 py-1.5 bg-[var(--color-surface)] border-t border-[var(--color-border-subtle)] flex items-center gap-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)]">Progression {progress} %</span>
                    <div className="h-1 flex-1 bg-[var(--color-border-subtle)]" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow={progress}>
                        <div className="h-full bg-[var(--color-brand-500)] transition-[width] duration-150 motion-reduce:transition-none" style={{ width: `${progress}%` }} />
                    </div>
                </div>
            )}

            {/* Bannière recalcul */}
            {recalcRequired && (
                <div
                    role="alert"
                    className="flex items-center gap-2.5 px-4 py-2 bg-[var(--color-warning-500)]/10 border-t border-[var(--color-warning-500)]/30"
                >
                    <AlertTriangle size={14} className="text-[var(--color-warning-600)] shrink-0" aria-hidden="true" />
                    <p className="text-[11px] text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] flex-1">
                        <span className="font-bold">Recalcul recommandé.</span>
                        {recalcReason && <span className="ml-1">{recalcReason}</span>}
                    </p>
                    {activePeriodId && (
                        <Button
                            variant="outline"
                            size="xs"
                            onClick={() => navigate(`/paie/periodes/${activePeriodId}/calcul`)}
                            className="border-[var(--color-warning-500)]/50 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]"
                        >
                            Aller au calcul
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}
