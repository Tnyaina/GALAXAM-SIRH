import { ArrowLeft } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import SEO from '@/components/system/SEO';
import Badge from '@/components/ui/data-display/Badge';
import PayrollPeriodStepper from '@/components/paie/PayrollPeriodStepper';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import PayrollIssuePanel from '@/components/paie/PayrollIssuePanel';
import SearchSelect from '@/components/ui/forms/SearchSelect';

const STATUT_VARIANTS = {
    VALIDEE: 'success',
    PAIE_VALIDEE: 'success',
    CLOTUREE: 'secondary',
    EN_COURS: 'warning',
    A_VALIDER: 'info',
    BROUILLON: 'default',
};

/**
 * PayrollCampaignShell — Shell visuel invariant pour les 7 étapes de campagne de paie.
 *
 * Structure cible V1 Full V3 :
 * 1. Breadcrumb hiérarchique
 * 2. Header campagne (Mois, dates, statut backend, population, version utile)
 * 3. Stepper de campagne compact (7 étapes)
 * 4. Titre de l'étape + description courte
 * 5. Zone de contenu spécifique
 * 6. Action Bar de bas de page (1 seule action principale contextuelle)
 */
export default function PayrollCampaignShell({
    title,
    subtitle,
    activeStep,
    context,
    periodes = [],
    loading = false,
    prevStep,
    nextStep,
    primaryAction,
    footerCenter,
    children,
}) {
    const navigate = useNavigate();

    const activePeriod = context?.selectedPeriod || context?.currentPeriod;
    const campaignSteps = context?.campaignSteps || [];
    const currentStepCode = context?.currentStep;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;
    const activeStepObj = campaignSteps.find(s => s.code === activeStep);
    const population = context?.populationCount ?? context?.employeeCount ?? activePeriod?.employeeCount;

    const formatDate = (d) => {
        if (!d) return '–';
        return new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: 'short', year: 'numeric' });
    };

    const periodCode = activePeriod?.code || activePeriod?.label || 'Période';
    const periodDates = activePeriod?.startDate && activePeriod?.endDate
        ? `${formatDate(activePeriod.startDate)} → ${formatDate(activePeriod.endDate)}`
        : null;

    return (
        <div className="flex-1 flex flex-col min-h-full max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-6">
            <SEO
                title={`${title || 'Campagne de Paie'} | GALAXAM SIRH`}
                description="Workflow de campagne de paie strict et guidé par le backend."
            />

            {/* 1. Breadcrumb épuré */}
            <nav aria-label="Fil d'Ariane" className="flex items-center gap-2 text-xs text-[var(--color-fg-subtle)] mb-3">
                <Link to="/paie" className="hover:text-[var(--color-brand-600)] transition-colors flex items-center gap-1">
                    <ArrowLeft size={13} className="shrink-0" />
                    <span>Centre de paie</span>
                </Link>
                <span>/</span>
                <span className="text-[var(--color-fg-muted)]">Campagne {periodCode}</span>
                {title && (
                    <>
                        <span>/</span>
                        <span className="font-semibold text-[var(--color-fg)]">{title}</span>
                    </>
                )}
            </nav>

            {/* 2. Header Invariant de Campagne */}
            <header className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none shadow-xs p-4 sm:p-5 mb-3">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
                    <div className="space-y-1">
                        <div className="flex items-center gap-2.5 flex-wrap">
                            <span className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                Campagne Active
                            </span>
                            <h1 className="text-xl sm:text-2xl font-bold text-[var(--color-fg)] tracking-tight">
                                {periodCode}
                            </h1>
                            {activePeriod?.state && (
                                <Badge variant={STATUT_VARIANTS[activePeriod.state] || 'default'} size="sm">
                                    {activePeriod.state}
                                </Badge>
                            )}
                            {activeStepObj?.readOnly && (
                                <Badge variant="secondary" size="sm">Lecture seule</Badge>
                            )}
                        </div>

                        <div className="flex items-center gap-4 text-xs text-[var(--color-fg-muted)] flex-wrap pt-0.5">
                            {periodDates && (
                                <span>{periodDates}</span>
                            )}
                            {population !== undefined && population !== null && (
                                <span>· <strong className="text-[var(--color-fg)] font-mono">{population}</strong> salariés</span>
                            )}
                            {context?.snapshotPointageVersion && (
                                <span>· Snapshot Pointage : <code className="font-mono text-[11px] text-[var(--color-brand-600)]">v{context.snapshotPointageVersion}</code></span>
                            )}
                        </div>
                    </div>

                    {/* Sélecteur rapide de période si multiple */}
                    {periodes.length > 0 && (
                        <div className="shrink-0 flex items-center gap-2">
                            <label htmlFor="select-periode-shell" className="text-xs text-[var(--color-fg-subtle)]">Période :</label>
                            <SearchSelect
                                id="select-periode-shell"
                                placeholder="Rechercher une période…"
                                value={activePeriod?.id || ''}
                                onChange={(newId) => {
                                    if (newId) navigate(`/paie/periodes/${newId}/${activeStep ? activeStep.toLowerCase() : 'preparation'}`);
                                }}
                                className="w-56"
                                options={periodes.map(p => {
                                    const pId = p.idPeriodePaie || p.id_periode_paie || p.id;
                                    return {
                                        value: String(pId),
                                        label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(pId),
                                        sublabel: p.statutCode || p.statut || p.state || 'EN_COURS',
                                    };
                                })}
                            />
                        </div>
                    )}
                </div>

                {/* Bannière de Recalcul si STALE */}
                {recalcRequired && (
                    <div className="mt-3 p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/30 flex items-center justify-between gap-3 text-xs text-[var(--color-warning-800)] dark:text-[var(--color-warning-200)] flex-wrap">
                        <div className="flex items-center gap-2">
                            <span className="font-bold">⚠ Calcul obsolète :</span>
                            <span>{recalcReason || 'Des sources de pointage ou variables ont été modifiées depuis le dernier calcul.'}</span>
                        </div>
                        {activePeriod?.id && activeStep !== 'CALCUL' && activeStep !== 'CALCULATION' && (
                            <button
                                type="button"
                                onClick={() => navigate(`/paie/periodes/${activePeriod.id}/calcul`)}
                                className="px-2.5 py-1 text-xs font-semibold bg-[var(--color-warning-500)] text-white hover:opacity-90 transition-opacity cursor-pointer shrink-0"
                            >
                                Aller au calcul
                            </button>
                        )}
                    </div>
                )}

                {/* 3. Stepper Invariant 7 étapes */}
                {campaignSteps.length > 0 && (
                    <div className="mt-4 pt-3 border-t border-[var(--color-border-subtle)]">
                        <PayrollPeriodStepper
                            steps={campaignSteps}
                            currentStep={currentStepCode}
                            activeCode={activeStep}
                            loading={loading}
                        />
                    </div>
                )}
            </header>

            {/* 4. En-tête de l'étape active */}
            {(title || subtitle) && (
                <div className="mb-4 px-1">
                    <h2 className="text-lg font-bold text-[var(--color-fg)]">{title}</h2>
                    {subtitle && <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{subtitle}</p>}
                </div>
            )}

            {/* Alerte lecture seule si applicable */}
            {activeStepObj?.readOnly && (
                <div className="mb-4">
                    <PayrollIssuePanel
                        severity="INFO"
                        title="Consultation en lecture seule"
                        message="Cette campagne est validée ou clôturée. Les données sont archivées et scellées, aucune modification financière n’est possible."
                        code="PAYROLL_READ_ONLY"
                    />
                </div>
            )}

            {/* 5. Contenu Spécifique de l'étape */}
            <main id="step-content" className="flex-1 flex flex-col min-h-0">
                {children}
            </main>

            {/* 6. Action Bar basse unique */}
            <PayrollNavigationFooter
                prevStep={prevStep}
                nextStep={nextStep}
                primaryAction={primaryAction}
                centerContent={footerCenter}
            />
        </div>
    );
}
