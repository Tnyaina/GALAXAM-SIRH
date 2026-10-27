import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, AlertTriangle, XCircle, ExternalLink } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import PayrollIssuePanel from '@/features/payroll/components/feedback/PayrollIssuePanel';
import Button from '@/components/ui/forms/Button';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';

/**
 * PreparationStepPage — Étape 1 : Vérification des prérequis de calcul (V1 Full V3).
 */
export default function PreparationStepPage() {
    // Contexte et hook métier
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading } = usePayrollCampaign(numericPeriodId);

    // Données dérivées
    const issues = context?.issues || [];
    const warnings = context?.warnings || [];

    // État local
    const [readiness, setReadiness] = useState([]);
    const [readinessError, setReadinessError] = useState(null);
    const [readinessLoaded, setReadinessLoaded] = useState(false);

    useEffect(() => {
        let isMounted = true;
        if (!numericPeriodId) return undefined;
        setReadinessLoaded(false);
        paieService.getCockpitPeriode(numericPeriodId, { light: true })
            .then(data => {
                if (!isMounted) return;
                const checks = data?.readiness?.checks;
                if (!Array.isArray(checks) || !['PAYROLL_POPULATION', 'POINTAGE_FROZEN',
                    'FISCAL_SOCIAL', 'ACOMPTES'].every(code => checks.some(check => check.code === code))) {
                    throw new Error('Contrôles de préparation incomplets. Rechargez la campagne.');
                }
                setReadiness(checks);
                setReadinessError(null);
                setReadinessLoaded(true);
            })
            .catch(error => {
                if (!isMounted) return;
                setReadiness([]);
                setReadinessError(getErrorMessage(error, 'Contrôles indisponibles.'));
                setReadinessLoaded(true);
            });
        return () => { isMounted = false; };
    }, [numericPeriodId]);

    const presentation = {
        PAYROLL_POPULATION: { label: 'Contrats & affectations', source: 'Personnel & Contrats',
            action: { label: 'Voir contrats', destination: '/carrieres' } },
        POINTAGE_FROZEN: { label: 'Pointage', source: 'Pointage & Présences',
            action: { label: 'Ouvrir Pointage', destination: '/pointages?tab=validation' } },
        FISCAL_SOCIAL: { label: 'Fiscal & social', source: 'Paramètres légaux',
            action: { label: 'Paramètres fiscaux', destination: '/admin/parametres?module=paie&tab=reglementation_fiscale' } },
        ACOMPTES: { label: 'Acomptes / avances', source: 'Paie / Ajustements',
            action: { label: 'Voir acomptes', destination: '/paie/acomptes-avances' } },
        CALENDRIERS: { label: 'Calendriers', source: 'Paramétrage RH',
            action: { label: 'Ouvrir calendrier', destination: '/admin/parametres?module=paie&tab=calendrier' } },
        CONGES: { label: 'Congés & TCONGE', source: 'Congés & Absences',
            action: { label: 'Ouvrir Congés', destination: '/conges' } },
        INDEMNITES: { label: 'Indemnités automatiques', source: 'Pointage / Configuration' },
    };
    const checklistItems = readiness
        .filter(check => presentation[check.code])
        .map(check => ({ id: check.code, label: presentation[check.code].label,
            status: check.status === 'BLOCK' ? 'BLOCKING' : check.status,
            summary: check.message, source: presentation[check.code].source,
            action: presentation[check.code].action
                ? { ...presentation[check.code].action,
                    onClick: () => navigate(presentation[check.code].action.destination) } : null }));
    const nbPrets = checklistItems.filter(i => i.status === 'PASS').length;
    const nbWarnings = checklistItems.filter(i => i.status === 'WARNING').length + warnings.length;
    const preparationIssues = issues.filter(issue => ![
        'POINTAGE_NOT_FROZEN', 'PAYROLL_NOT_CALCULATED', 'PAYROLL_ALREADY_VALIDATED',
    ].includes(issue?.code));
    const nbBlockers = checklistItems.filter(i => i.status === 'BLOCKING').length + preparationIssues.length;
    const cannotContinue = !readinessLoaded || Boolean(readinessError) || nbBlockers > 0;

    // Rendu
    return (
        <PayrollCampaignShell
            title="Étape 1 — Préparation de la campagne"
            subtitle="Contrôle des prérequis techniques, présence et cadre réglementaire avant calcul."
            activeStep="PREPARATION"
            context={context}
            periodes={periodes}
            loading={loading}
            prevStep={{ label: 'Centre de paie', destination: '/paie' }}
            nextStep={{
                label: 'Population éligible',
                destination: `/paie/periodes/${numericPeriodId}/population`,
                disabled: cannotContinue,
                disabledReason: cannotContinue ? 'Les contrôles de préparation doivent être disponibles et sans blocage.' : undefined,
            }}
        >
            <div className="space-y-4 pb-4">
                <section className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between gap-4 flex-wrap" aria-label="État de préparation">
                    <div>
                        <span className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            État de préparation
                        </span>
                        <div className="flex items-center gap-4 mt-1 text-xs">
                            <span className="flex items-center gap-1.5 text-[var(--color-success-700)] dark:text-[var(--color-success-400)] font-semibold">
                                <ShieldCheck size={16} />
                                {nbPrets} contrôles prêts
                            </span>
                            {nbWarnings > 0 && (
                                <span className="flex items-center gap-1.5 text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] font-semibold">
                                    <AlertTriangle size={16} />
                                    {nbWarnings} avertissement{nbWarnings > 1 ? 's' : ''}
                                </span>
                            )}
                            <span className={`flex items-center gap-1.5 font-semibold ${nbBlockers > 0 ? 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-subtle)]'}`}>
                                <XCircle size={16} />
                                {nbBlockers} bloquant{nbBlockers > 1 ? 's' : ''}
                            </span>
                        </div>
                    </div>

                    <p className="text-xs text-[var(--color-fg-muted)]">
                        {!readinessLoaded ? 'Vérification des prérequis…' : cannotContinue
                            ? 'Résolvez les points bloquants avant de continuer.'
                            : 'Vous pouvez continuer vers la population éligible.'}
                    </p>
                </section>

                {/* Erreur readiness API éventuelle */}
                {readinessError && (
                    <PayrollIssuePanel
                        severity="BLOCKER"
                        title="Readiness indisponible"
                        message={readinessError}
                        code="PAYROLL_READINESS_UNAVAILABLE"
                    />
                )}

                {preparationIssues.map(issue => (
                    <PayrollIssuePanel
                        key={issue.code || issue.message}
                        severity="BLOCKER"
                        title="Prérequis de préparation à vérifier"
                        message={issue.message}
                        code={issue.code}
                    />
                ))}

                <section aria-labelledby="preparation-checks-title">
                    <h3 id="preparation-checks-title" className="text-sm font-bold text-[var(--color-fg)] mb-2">Contrôles de préparation</h3>
                    <div className="grid grid-cols-1 lg:grid-cols-2 gap-2">
                    {checklistItems.map(item => {
                        const isBlocker = item.status === 'BLOCKING';
                        const isWarning = item.status === 'WARNING';
                        const isPass = item.status === 'PASS';

                        return (
                            <article key={item.id} className={`bg-[var(--color-surface)] border p-3 flex items-start justify-between gap-3 ${isBlocker ? 'border-[var(--color-danger-300)]' : isWarning ? 'border-[var(--color-warning-300)]' : 'border-[var(--color-border-subtle)]'}`}>
                                <div className="flex items-start gap-2.5 min-w-0">
                                    <div className="mt-0.5 shrink-0">
                                        {isPass && <ShieldCheck size={18} className="text-[var(--color-success-600)]" />}
                                        {isWarning && <AlertTriangle size={18} className="text-[var(--color-warning-600)]" />}
                                        {isBlocker && <XCircle size={18} className="text-[var(--color-danger-600)]" />}
                                    </div>
                                    <div className="min-w-0">
                                        <div className="flex items-center gap-x-2 gap-y-0.5 flex-wrap">
                                            <h4 className="text-xs font-bold text-[var(--color-fg)]">
                                                {item.label}
                                            </h4>
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 leading-snug">
                                            {item.summary}
                                        </p>
                                        <p className="text-[10px] text-[var(--color-fg-subtle)] mt-1">Source : {item.source}</p>
                                    </div>
                                </div>

                                {item.action && (
                                    <Button
                                        variant="ghost"
                                        size="xs"
                                        onClick={item.action.onClick}
                                        iconRight={<ExternalLink size={12} />}
                                        className="shrink-0 self-start text-[var(--color-brand-600)]"
                                    >
                                        {item.action.label}
                                    </Button>
                                )}
                            </article>
                        );
                    })}
                    </div>
                </section>
            </div>
        </PayrollCampaignShell>
    );
}
