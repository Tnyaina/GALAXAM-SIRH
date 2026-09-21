import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { CheckCircle2, XCircle, ClipboardList, Snowflake, ShieldCheck, Scale, Users } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieConfigService from '@/api/admin/paieConfigService';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * PreparationStepPage — Étape 1 : Vérification des prérequis de calcul.
 *
 * Affiche :
 * - Statut du pointage (gel / snapshot)
 * - Issues bloquantes éventuelles issues du contexte backend
 * - Résumé de la période (dates, code, statut)
 * - Paramètres réglementaires et fiscaux dynamiques résolus en base
 */
export default function PreparationStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading, refresh } = usePayrollCampaign(numericPeriodId);

    const issues = context?.issues || [];
    const warnings = context?.warnings || [];
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const actions = context?.availableActions || {};

    const [legalConfig, setLegalConfig] = useState(null);

    useEffect(() => {
        let isMounted = true;
        const loadConfig = async () => {
            if (!periode?.startDate) return;
            try {
                const cfg = await paieConfigService.getConfigLegaleAt(periode.startDate);
                if (isMounted) setLegalConfig(cfg);
            } catch {
                try {
                    const active = await paieConfigService.getConfigLegaleActive();
                    if (isMounted) setLegalConfig(active);
                } catch {
                    // Fallback gracieux si aucune config trouvée
                }
            }
        };
        loadConfig();
        return () => { isMounted = false; };
    }, [periode?.startDate]);

    const isPointageGele = periode?.state === 'VALIDEE' || periode?.state === 'PAIE_VALIDEE' || periode?.state === 'CLOTUREE';
    const canCalculate = actions?.CALCULATE?.allowed === true;
    const canAddVariable = actions?.ADD_VARIABLE?.allowed === true;

    const translateReason = (code) => {
        const map = {
            PAYROLL_PERIOD_PREREQUISITE_BLOCKED: 'Le pointage de la période doit être validé par la DAF avant de calculer la paie.',
            BLOCKING_CONFIGURATION_ERROR: 'Configuration de paie incomplète.',
            PERIOD_LOCKED: 'La période est verrouillée.',
            POPULATION_EMPTY: 'Aucun salarié éligible trouvé.',
        };
        return map[code] || code;
    };

    const checks = [
        {
            id: 'pointage_gele',
            label: 'Snapshot Pointage scellé & figé (DAF)',
            ok: isPointageGele,
            message: isPointageGele
                ? 'Le pointage de la période est validé et figé dans pointage_cumul_periode.'
                : 'Le pointage n’a pas encore été validé par la DAF. Rendez-vous dans le module Pointage pour arbitrer et sceller.',
            icon: Snowflake,
            action: !isPointageGele ? {
                label: 'Aller au module Pointage',
                onClick: () => navigate('/pointages?tab=validation')
            } : null,
        },
        {
            id: 'config_fiscale',
            label: 'Exercice fiscal & Barème IRSA actif',
            ok: Boolean(legalConfig),
            message: legalConfig
                ? `Exercice ${legalConfig.anneeFiscale || 'CGI'} résolu (Seuil ${formatMoneyAr(legalConfig.seuilExonerationIrsa || 350000)}, Min ${formatMoneyAr(legalConfig.minimumPerceptionIrsa || 2000)}).`
                : 'Configuration légale et fiscale introuvable pour la date de cette période.',
            icon: Scale,
            action: !legalConfig ? {
                label: 'Configurer les paramètres fiscaux',
                onClick: () => navigate('/admin/parametres')
            } : null,
        },
        {
            id: 'calc_autorise',
            label: 'Moteur de calcul prêt',
            ok: canCalculate,
            message: canCalculate
                ? 'Le snapshot de pointage est disponible et intègre pour le calcul officiel.'
                : (actions?.CALCULATE?.reasonCodes || []).map(translateReason).join(', ') || 'Snapshot manquant ou pointage non validé.',
            icon: CheckCircle2,
        },
        {
            id: 'variables_ok',
            label: 'Saisie des variables de paie',
            ok: canAddVariable,
            message: canAddVariable
                ? 'Les variables de paie (primes, acomptes, avances) peuvent être saisies et modifiées.'
                : 'La période est verrouillée — les variables sont consultables en lecture seule.',
            icon: ClipboardList,
        },
    ];

    const formatDate = (d) => d ? new Date(d).toLocaleDateString('fr-MG', { day: '2-digit', month: 'long', year: 'numeric' }) : '–';

    const statutVariant = { VALIDEE: 'success', PAIE_VALIDEE: 'primary', CLOTUREE: 'secondary', EN_COURS: 'warning', A_VALIDER: 'info', BROUILLON: 'default' };

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Préparation | Paie GALAXAM SIRH"
                description="Vérification des prérequis avant le calcul officiel de la paie."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/preparation`)}
                activeStep="PREPARATION"
                loading={loading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-4xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0">
                        <ClipboardList size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 1 — Préparation de la campagne
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Vérifiez que tous les prérequis sont satisfaits avant de procéder au calcul de la paie.
                        </p>
                    </div>
                </div>

                {/* Résumé de la période */}
                {periode && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 space-y-3">
                        <div>
                            <p className="text-[10px] uppercase tracking-wider text-[var(--color-fg-muted)] mb-1 font-bold">Période de Campagne</p>
                            <div className="flex items-center gap-3 flex-wrap">
                                <span className="text-sm font-bold font-mono text-[var(--color-fg)]">{periode.code}</span>
                                <Badge variant={statutVariant[periode.state] || 'default'} dot size="sm">
                                    {periode.state === 'VALIDEE' ? 'POINTAGE SCELLE' : periode.state === 'PAIE_VALIDEE' ? 'PAIE VALIDEE' : periode.state}
                                </Badge>
                                <span className="text-xs text-[var(--color-fg-muted)]">
                                    Cycle : <strong className="text-[var(--color-fg)] font-mono">{formatDate(periode.startDate)} → {formatDate(periode.endDate)}</strong>
                                </span>
                                {periode.label && (
                                    <span className="text-xs text-[var(--color-fg-subtle)]">— {periode.label}</span>
                                )}
                            </div>
                        </div>

                        {/* Paramètres réglementaires actifs résolus dynamiquement */}
                        <div className="pt-3 border-t border-[var(--color-border-subtle)] grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
                            <div className="bg-[var(--color-bg-subtle)]/50 p-2.5 border border-[var(--color-border-subtle)]">
                                <span className="text-[10px] font-bold uppercase text-[var(--color-fg-muted)] block">Cotisations Sociales</span>
                                <p className="font-semibold text-[var(--color-fg)] mt-0.5">
                                    CNaPS {((legalConfig?.tauxCnapsSalarial ?? 0.01) * 100).toFixed(0)}% sal. / {((legalConfig?.tauxCnapsPatronal ?? 0.13) * 100).toFixed(0)}% patr.
                                </p>
                                <p className="text-[11px] text-[var(--color-fg-subtle)]">
                                    Plafond 8x SME ({formatMoneyAr(legalConfig?.plafondCnaps || 2000000)})
                                </p>
                            </div>
                            <div className="bg-[var(--color-bg-subtle)]/50 p-2.5 border border-[var(--color-border-subtle)]">
                                <span className="text-[10px] font-bold uppercase text-[var(--color-fg-muted)] block">Santé & Formation</span>
                                <p className="font-semibold text-[var(--color-fg)] mt-0.5">
                                    OSTIE / OMSI • FMFP {((legalConfig?.tauxFmfp ?? 0.01) * 100).toFixed(0)}%
                                </p>
                                <p className="text-[11px] text-[var(--color-fg-subtle)]">
                                    Médecine du travail & formation continue
                                </p>
                            </div>
                            <div className="bg-[var(--color-bg-subtle)]/50 p-2.5 border border-[var(--color-border-subtle)]">
                                <span className="text-[10px] font-bold uppercase text-[var(--color-fg-muted)] block">Régime Fiscal IRSA</span>
                                <p className="font-semibold text-[var(--color-fg)] mt-0.5">
                                    {legalConfig?.anneeFiscale ? `Barème LF ${legalConfig.anneeFiscale}` : 'Barème Progressif CGI'}
                                </p>
                                <p className="text-[11px] text-[var(--color-fg-subtle)]">
                                    Seuil {formatMoneyAr(legalConfig?.seuilExonerationIrsa || 350000)} • Min {formatMoneyAr(legalConfig?.minimumPerceptionIrsa || 2000)}
                                </p>
                            </div>
                        </div>
                    </div>
                )}

                {/* Grille des prérequis */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                    <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                        <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            Prérequis de calcul
                        </h2>
                    </div>
                    <div className="divide-y divide-[var(--color-border-subtle)]">
                        {checks.map((check) => (
                            <div key={check.id} className="flex items-start gap-4 px-4 py-3">
                                    <div className={`mt-0.5 shrink-0 ${check.ok ? 'text-[var(--color-success-500)]' : 'text-[var(--color-danger-500)]'}`}>
                                        {check.ok
                                            ? <CheckCircle2 size={16} aria-hidden="true" />
                                            : <XCircle size={16} aria-hidden="true" />
                                        }
                                    </div>
                                    <div className="flex-1 min-w-0">
                                        <p className="text-xs font-semibold text-[var(--color-fg)]">{check.label}</p>
                                        <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">{check.message}</p>
                                    </div>
                                    <div className="flex items-center gap-2 shrink-0">
                                        {check.action && (
                                            <Button
                                                variant="outline"
                                                size="xs"
                                                onClick={check.action.onClick}
                                            >
                                                {check.action.label}
                                            </Button>
                                        )}
                                        <Badge variant={check.ok ? 'success' : 'danger'} size="xs">
                                            {check.ok ? 'OK' : 'REQUIS'}
                                        </Badge>
                                    </div>
                                </div>
                        ))}
                    </div>
                </div>

                {/* Problèmes bloquants (du backend) */}
                {issues.length > 0 && (
                    <div className="space-y-2">
                        {issues.map((issue, i) => (
                            <Alert
                                key={i}
                                type="error"
                                title={`Prérequis bloquant : ${translateReason(issue.code)}`}
                                message={issue.message || 'Ce point doit être résolu avant de pouvoir lancer le calcul de paie.'}
                            />
                        ))}
                    </div>
                )}

                {/* Avertissements non bloquants */}
                {warnings.length > 0 && (
                    <div className="space-y-2">
                        {warnings.map((w, i) => (
                            <Alert
                                key={i}
                                type="warning"
                                title="Point d’attention"
                                message={w.message || translateReason(w.code)}
                            />
                        ))}
                    </div>
                )}

                {/* Statut global */}
                {!loading && (
                    <Alert
                        type={isPointageGele && canCalculate ? 'success' : 'warning'}
                        title={isPointageGele && canCalculate ? 'Campagne prête' : 'Actions requises'}
                        message={isPointageGele && canCalculate
                            ? 'Tous les prérequis sont satisfaits. Vous pouvez vérifier la population et procéder au calcul.'
                            : 'Certains prérequis ne sont pas encore satisfaits. Validez les pointages pour débloquer le calcul.'}
                    />
                )}
            </div>

            <PayrollNavigationFooter
                prevStep={{ label: 'Accueil Paie', destination: '/paie' }}
                nextStep={{
                    label: 'Population éligible',
                    destination: `/paie/periodes/${numericPeriodId}/population`,
                }}
            />
        </div>
    );
}
