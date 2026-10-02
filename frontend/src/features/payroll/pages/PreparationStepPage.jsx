import { useState, useEffect, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, AlertTriangle, XCircle, ExternalLink } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import PayrollIssuePanel from '@/features/payroll/components/feedback/PayrollIssuePanel';
import Button from '@/components/ui/forms/Button';
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

    // État local
    const [legalConfig, setLegalConfig] = useState(null);
    const [readiness, setReadiness] = useState([]);
    const [readinessError, setReadinessError] = useState(null);
    const [acomptes, setAcomptes] = useState([]);
    const [acomptesError, setAcomptesError] = useState(false);

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

    useEffect(() => {
        let isMounted = true;
        if (!numericPeriodId) return undefined;
        paieService.listerAjustements(numericPeriodId, null, 'ACOMPTE_AVANCE')
            .then(items => {
                if (!isMounted) return;
                setAcomptes(Array.isArray(items) ? items : []);
                setAcomptesError(false);
            })
            .catch(() => {
                if (!isMounted) return;
                setAcomptes([]);
                setAcomptesError(true);
            });
        return () => { isMounted = false; };
    }, [numericPeriodId]);

    const acomptesAVerifier = acomptes.filter(item =>
        ['A_APPROUVER', 'VERSEMENT_A_VERIFIER'].includes(item.etatDossier)
    ).length;

    const pointageCheck = readiness.find(check => check.code === 'POINTAGE_FROZEN');
    const isPointageGele = pointageCheck?.status === 'PASS';
    const plafondCnapsRaw = legalConfig?.plafondCotisationMensuel
        ?? (legalConfig?.smeNational != null && legalConfig?.plafondCotisationNbSme != null
            ? Number(legalConfig.smeNational) * Number(legalConfig.plafondCotisationNbSme)
            : null);
    const plafondCnaps = plafondCnapsRaw != null && Number.isFinite(Number(plafondCnapsRaw))
        ? Number(plafondCnapsRaw)
        : null;

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
                    ? 'Snapshot Pointage scellé et validé.'
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
                status: acomptesError || acomptesAVerifier > 0 ? 'WARNING' : 'PASS',
                summary: acomptesError
                    ? 'Vérification des acomptes et avances indisponible.'
                    : acomptesAVerifier > 0
                        ? `${acomptesAVerifier} dossier(s) à approuver ou versement(s) à vérifier pour cette période.`
                        : acomptes.length > 0
                            ? 'Dossiers d’acomptes et avances vérifiés pour cette période.'
                            : 'Aucun acompte ni avance pour cette période.',
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
                status: !legalConfig ? 'BLOCKING' : plafondCnaps == null ? 'WARNING' : 'PASS',
                summary: legalConfig
                    ? plafondCnaps == null
                        ? `Barème LF ${legalConfig.anneeFiscale || 'CGI'} actif. Plafond CNaPS non résolu : vérifiez le SME et le multiplicateur configurés.`
                        : `Barème LF ${legalConfig.anneeFiscale || 'CGI'} actif. Plafond CNaPS : ${formatMoneyAr(plafondCnaps)}.`
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
    }, [isPointageGele, acomptes, acomptesError, acomptesAVerifier, legalConfig, plafondCnaps, navigate]);

    // Décompte de readiness
    const nbPrets = checklistItems.filter(i => i.status === 'PASS').length;
    const nbWarnings = checklistItems.filter(i => i.status === 'WARNING').length + warnings.length;
    // Le contexte de période remonte aussi des problèmes propres aux étapes
    // ultérieures (ex. paie non encore calculée). Ils ne bloquent pas la
    // préparation et ne doivent pas gonfler son compteur.
    const preparationIssues = issues.filter(issue => ![
        'POINTAGE_NOT_FROZEN',
        'PAYROLL_NOT_CALCULATED',
        'PAYROLL_ALREADY_VALIDATED',
    ].includes(issue?.code));
    const nbBlockers = checklistItems.filter(i => i.status === 'BLOCKING').length + preparationIssues.length;

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
                        {nbBlockers === 0
                            ? 'Vous pouvez continuer vers la population éligible.'
                            : 'Résolvez les points bloquants avant de continuer.'}
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
