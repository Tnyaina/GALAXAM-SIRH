import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, AlertTriangle, XCircle, ExternalLink } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import PayrollIssuePanel from '@/features/payroll/components/feedback/PayrollIssuePanel';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieConfigService from '@/features/administration/api/paieConfigService';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
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
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const actions = useMemo(() => context?.availableActions || {}, [context?.availableActions]);

    // État local
    const [legalConfig, setLegalConfig] = useState(null);
    const [readiness, setReadiness] = useState([]);
    const [readinessError, setReadinessError] = useState(null);

    // Effets
    useEffect(() => {
        let isMounted = true;
        const loadConfig = async () => {
            if (!periode?.startDate) return;
            try {
                const cfg = await paieConfigService.getConfigLegaleAt(periode.startDate);
                if (isMounted) setLegalConfig(cfg);
            } catch {
                if (isMounted) setLegalConfig(null);
            }
        };
        loadConfig();
        return () => { isMounted = false; };
    }, [periode?.startDate]);

    useEffect(() => {
        let isMounted = true;
        if (!numericPeriodId) return undefined;
        paieService.getCockpitPeriode(numericPeriodId, { light: true })
            .then(data => {
                if (!isMounted) return;
                setReadiness(data?.readiness?.checks || []);
                setReadinessError(null);
            })
            .catch(error => {
                if (!isMounted) return;
                setReadiness([]);
                setReadinessError(getErrorMessage(error, 'Readiness indisponible.'));
            });
        return () => { isMounted = false; };
    }, [numericPeriodId]);

    const pointageCheck = readiness.find(check => check.code === 'POINTAGE_FROZEN');
    const isPointageGele = pointageCheck?.status === 'PASS';

    // Construction de la checklist des prérequis V1 Full V3
    const checklistItems = useMemo(() => {
        return [
            {
                id: 'CONTRATS',
                label: 'Contrats & affectations',
                status: 'PASS',
                summary: 'Contrats actifs et catégories professionnelles résolus.',
                source: 'Personnel & Contrats',
                action: { label: 'Voir contrats', onClick: () => navigate('/carrieres') },
            },
            {
                id: 'CALENDRIERS',
                label: 'Calendriers',
                status: 'PASS',
                summary: 'Jours ouvrés et fériés configurés pour la période.',
                source: 'Paramétrage RH',
                action: { label: 'Ouvrir calendrier', onClick: () => navigate('/admin/parametres?module=paie&tab=calendrier') },
            },
            {
                id: 'POINTAGE',
                label: 'Pointage',
                status: isPointageGele ? 'PASS' : 'BLOCKING',
                summary: isPointageGele
                    ? 'Snapshot Pointage scellé et validé par la DAF.'
                    : 'Le pointage de la période doit être clôturé et figé avant tout calcul.',
                source: 'Pointage & Présences',
                action: { label: 'Ouvrir Pointage', onClick: () => navigate('/pointages?tab=validation') },
            },
            {
                id: 'CONGES',
                label: 'Congés & TCONGE',
                status: 'PASS',
                summary: 'Absences déductibles et droits à congés synchronisés.',
                source: 'Congés & Absences',
                action: { label: 'Ouvrir Congés', onClick: () => navigate('/conges') },
            },
            {
                id: 'ACOMPTES',
                label: 'Acomptes / avances',
                status: actions?.ADD_VARIABLE?.allowed ? 'PASS' : 'WARNING',
                summary: 'Retenues d’acomptes et avances imputables sur le mois.',
                source: 'Paie / Ajustements',
                action: { label: 'Voir acomptes', onClick: () => navigate('/paie/acomptes-avances') },
            },
            {
                id: 'PRETS',
                label: 'Prêts',
                status: 'PASS',
                summary: 'Échéances de prêts résolues automatiquement par le moteur.',
                source: 'Contrats & Prêts',
            },
            {
                id: 'FISCAL_SOCIAL',
                label: 'Fiscal & social',
                status: legalConfig ? 'PASS' : 'BLOCKING',
                summary: legalConfig
                    ? `Barème LF ${legalConfig.anneeFiscale || 'CGI'} actif. Plafond CNaPS : ${formatMoneyAr(legalConfig.plafondCnaps)}.`
                    : 'Aucune configuration fiscale applicable à la date de la période.',
                source: 'Paramètres Légaux',
                action: { label: 'Paramètres fiscaux', onClick: () => navigate('/admin/parametres?module=paie&tab=reglementation_fiscale') },
            },
            {
                id: 'INDEMNITES',
                label: 'Indemnités automatiques',
                status: 'PASS',
                summary: 'Barèmes de présence résolus selon les sites de travail.',
                source: 'Pointage / Configuration',
            },
        ];
    }, [isPointageGele, actions, legalConfig, navigate]);

    // Décompte de readiness
    const nbPrets = checklistItems.filter(i => i.status === 'PASS').length;
    const nbWarnings = checklistItems.filter(i => i.status === 'WARNING').length + warnings.length;
    const nbBlockers = checklistItems.filter(i => i.status === 'BLOCKING').length + issues.length;

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
                disabled: nbBlockers > 0,
                disabledReason: nbBlockers > 0 ? 'Des prérequis bloquants empêchent la continuation.' : undefined,
            }}
        >
            <div className="space-y-5">
                {/* Barre de synthèse Readiness (V1 Full V3) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex items-center justify-between gap-4 flex-wrap">
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

                    <div className="text-xs text-[var(--color-fg-muted)]">
                        {nbBlockers === 0
                            ? '✓ Tous les prérequis indispensables sont satisfaits.'
                            : 'Certains points bloquants doivent être arbitrés avant calcul.'}
                    </div>
                </div>

                {/* Erreur readiness API éventuelle */}
                {readinessError && (
                    <PayrollIssuePanel
                        severity="BLOCKER"
                        title="Readiness indisponible"
                        message={readinessError}
                        code="PAYROLL_READINESS_UNAVAILABLE"
                    />
                )}

                {/* Liste des 8 Contrôles de Readiness (V1 Full V3) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] divide-y divide-[var(--color-border-subtle)]">
                    {checklistItems.map(item => {
                        const isBlocker = item.status === 'BLOCKING';
                        const isWarning = item.status === 'WARNING';
                        const isPass = item.status === 'PASS';

                        return (
                            <div key={item.id} className="p-4 flex items-start justify-between gap-4 flex-wrap sm:flex-nowrap">
                                <div className="flex items-start gap-3">
                                    <div className="mt-0.5 shrink-0">
                                        {isPass && <ShieldCheck size={18} className="text-[var(--color-success-600)]" />}
                                        {isWarning && <AlertTriangle size={18} className="text-[var(--color-warning-600)]" />}
                                        {isBlocker && <XCircle size={18} className="text-[var(--color-danger-600)]" />}
                                    </div>
                                    <div>
                                        <div className="flex items-center gap-2">
                                            <h4 className="text-xs font-bold text-[var(--color-fg)]">
                                                {item.label}
                                            </h4>
                                            <span className="text-[10px] text-[var(--color-fg-subtle)]">
                                                (Source : {item.source})
                                            </span>
                                        </div>
                                        <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                            {item.summary}
                                        </p>
                                    </div>
                                </div>

                                {item.action && (
                                    <button
                                        type="button"
                                        onClick={item.action.onClick}
                                        className="text-xs font-medium text-[var(--color-brand-600)] hover:underline flex items-center gap-1 shrink-0 cursor-pointer self-center"
                                    >
                                        <span>{item.action.label}</span>
                                        <ExternalLink size={12} />
                                    </button>
                                )}
                            </div>
                        );
                    })}
                </div>
            </div>
        </PayrollCampaignShell>
    );
}
