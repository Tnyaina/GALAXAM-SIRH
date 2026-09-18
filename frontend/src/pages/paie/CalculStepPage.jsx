import { useState, useCallback, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Calculator, Play, CheckCircle2, BarChart3, DollarSign, Banknote, ShieldAlert, FileText } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * CalculStepPage — Étape 4 : Calcul de la paie.
 *
 * Permet de :
 * - Lancer le calcul en simulation (contrôle préalable sans persistance)
 * - Lancer le calcul réel (persisté)
 * - Voir le résultat immédiat (nb bulletins, montant total, cotisations)
 */
export default function CalculStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [calcLoading, setCalcLoading] = useState(false);
    const [simLoading, setSimLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [existingKpis, setExistingKpis] = useState(null);
    const [feedback, setFeedback] = useState(null);

    const actions = context?.availableActions || {};
    const canCalculate = actions?.CALCULATE?.allowed === true;
    const canSimulate = actions?.SIMULATE?.allowed === true;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;

    // Charger les KPI existants de la période pour afficher les données dès l'arrivée sur la page
    const loadExistingCalculation = useCallback(async () => {
        if (!numericPeriodId) return;
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { size: 1 });
            if (cockpit?.kpis && (cockpit.kpis.totalBulletins > 0 || cockpit.kpis.bulletinsCount > 0 || cockpit.kpis.totalNetAPayer > 0)) {
                setExistingKpis({
                    bulletins: cockpit.kpis.totalBulletins ?? cockpit.kpis.bulletinsCount ?? cockpit.kpis.effectifCalcule ?? 0,
                    brut: cockpit.kpis.totalBrut ?? cockpit.kpis.masseSalarialeBrute ?? 0,
                    cotisationsSalariales: cockpit.kpis.totalCotisationsSalariales ?? 0,
                    irsa: cockpit.kpis.totalIrsa ?? 0,
                    net: cockpit.kpis.totalNetAPayer ?? cockpit.kpis.masseNetAPayer ?? 0,
                    patronales: cockpit.kpis.totalCotisationsPatronales ?? 0,
                    coutGlobal: cockpit.kpis.coutGlobalEmployeur ?? ((Number(cockpit.kpis.totalBrut) || 0) + (Number(cockpit.kpis.totalCotisationsPatronales) || 0)),
                });
            } else {
                setExistingKpis(null);
            }
        } catch {
            setExistingKpis(null);
        }
    }, [numericPeriodId]);

    useEffect(() => {
        loadExistingCalculation();
    }, [loadExistingCalculation]);

    const handleSimuler = useCallback(async () => {
        if (!numericPeriodId) return;
        setSimLoading(true);
        setFeedback(null);
        try {
            const res = await paieService.calculerPaiePeriode(numericPeriodId, true);
            setResult({
                isSimulation: true,
                bulletins: res.nbBulletinsCalcules ?? res.bulletinsCount ?? res.totalBulletins ?? 0,
                brut: res.totalMasseSalarialeBrute ?? res.totalBrut ?? res.masseSalarialeBrute ?? 0,
                cotisationsSalariales: res.totalCotisationsSalariales ?? res.totalSalariales ?? 0,
                irsa: res.totalIrsa ?? 0,
                net: res.totalNetAPayer ?? res.totalNet ?? res.masseNetAPayer ?? 0,
                patronales: res.totalChargesPatronales ?? res.totalCotisationsPatronales ?? 0,
                coutGlobal: res.totalCoutGlobal ?? res.coutGlobalEmployeur ?? 0,
            });
            setFeedback({ type: 'success', text: 'Simulation terminée avec succès. Résultats prévisionnels ci-dessous.' });
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de la simulation.' });
        } finally {
            setSimLoading(false);
        }
    }, [numericPeriodId]);

    const handleCalculer = useCallback(async () => {
        if (!numericPeriodId) return;
        if (!window.confirm('Confirmer le calcul réel de la paie ? Les bulletins seront créés ou mis à jour.')) return;
        setCalcLoading(true);
        setFeedback(null);
        try {
            const res = await paieService.calculerPaiePeriode(numericPeriodId, false);
            setResult({
                isSimulation: false,
                bulletins: res.nbBulletinsCalcules ?? res.bulletinsCount ?? res.totalBulletins ?? 0,
                brut: res.totalMasseSalarialeBrute ?? res.totalBrut ?? res.masseSalarialeBrute ?? 0,
                cotisationsSalariales: res.totalCotisationsSalariales ?? res.totalSalariales ?? 0,
                irsa: res.totalIrsa ?? 0,
                net: res.totalNetAPayer ?? res.totalNet ?? res.masseNetAPayer ?? 0,
                patronales: res.totalChargesPatronales ?? res.totalCotisationsPatronales ?? 0,
                coutGlobal: res.totalCoutGlobal ?? res.coutGlobalEmployeur ?? 0,
            });
            setFeedback({ type: 'success', text: 'Calcul de paie réel effectué et persisté avec succès.' });
            await refresh();
            await loadExistingCalculation();
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors du calcul.' });
        } finally {
            setCalcLoading(false);
        }
    }, [numericPeriodId, refresh, loadExistingCalculation]);

    const formatAr = (val) => formatMoneyAr(val);

    const displayKpis = result || existingKpis;
    const hasBulletins = Boolean((result?.bulletins ?? 0) > 0 || (existingKpis?.bulletins ?? 0) > 0);

    const translateReason = (code) => {
        const dict = {
            PAYROLL_PERIOD_PREREQUISITE_BLOCKED: 'Le pointage de la période doit être validé par la DAF avant de calculer la paie.',
            PERIOD_LOCKED: 'La période est verrouillée ou clôturée.',
            POPULATION_EMPTY: 'Aucun collaborateur éligible dans la population de cette période.',
            BLOCKING_CONFIGURATION_ERROR: 'La configuration fiscale ou sociale est incomplète.',
            CALCULATION_INPUTS_CHANGED: 'Des modifications ont été apportées aux variables ou pointages depuis le dernier calcul.',
            PAYROLL_NOT_VALIDATED: 'Les bulletins sont en attente de validation.',
            PAYROLL_ALREADY_VALIDATED: 'Campagne de paie déjà validée définitivement.',
        };
        return dict[code] || 'Prérequis en attente de conformité.';
    };

    const recalcMessage = recalcReason === 'CALCULATION_INPUTS_CHANGED'
        ? 'Des modifications ont été apportées aux variables ou aux pointages depuis le dernier calcul. Un recalcul est nécessaire pour mettre à jour les bulletins.'
        : (recalcReason || 'Des données de paie ont été modifiées. Il est conseillé de relancer le calcul.');

    const calculateReasonText = canCalculate
        ? 'La période est prête pour le calcul. Le snapshot de pointage est disponible et validé.'
        : (actions?.CALCULATE?.reasonCodes || []).map(translateReason).join(' ') || 'Calcul non disponible pour cette période.';

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Calcul de la paie | Paie GALAXAM SIRH"
                description="Lancement du calcul de la campagne de paie : simulation et calcul réel."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/calcul`)}
                activeStep="CALCULATION"
                loading={ctxLoading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-4xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-xs">
                        <Calculator size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 4 — Calcul de la paie
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Exécutez la simulation pour vérifier les montants avant de lancer le calcul réel officiel.
                        </p>
                    </div>
                </div>

                {/* Alerte recalcul si pointage/variables modifiés */}
                {recalcRequired && (
                    <Alert type="warning" title="Recalcul nécessaire" message={recalcMessage} />
                )}

                {/* Feedback */}
                {feedback && <Alert type={feedback.type} message={feedback.text} onClose={() => setFeedback(null)} />}

                {/* Carte de contrôle du calcul */}
                <Card padding className="space-y-4 shadow-xs">
                    <div className="flex items-center justify-between gap-3 flex-wrap">
                        <div>
                            <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-1">
                                Autorisation de calcul
                            </h2>
                            <p className="text-xs text-[var(--color-fg)]">
                                {calculateReasonText}
                            </p>
                        </div>
                        <Badge variant={canCalculate ? 'success' : 'danger'} dot size="sm">
                            {canCalculate ? 'Prêt pour calcul' : 'Prérequis manquants'}
                        </Badge>
                    </div>

                    {/* Actions de calcul */}
                    <div className="flex flex-wrap gap-3 pt-3 border-t border-[var(--color-border-subtle)]">
                        <Button
                            variant="outline"
                            size="md"
                            disabled={!canSimulate || simLoading || calcLoading}
                            loading={simLoading}
                            iconLeft={<BarChart3 size={15} />}
                            onClick={handleSimuler}
                            id="btn-simuler-paie"
                        >
                            Simuler (sans persistance)
                        </Button>
                        <Button
                            variant="primary"
                            size="md"
                            disabled={!canCalculate || calcLoading || simLoading}
                            loading={calcLoading}
                            iconLeft={<Play size={15} />}
                            onClick={handleCalculer}
                            id="btn-calculer-paie"
                        >
                            Lancer le calcul réel
                        </Button>
                    </div>
                </Card>

                {/* Résultats du calcul via StatCard */}
                {displayKpis && (
                    <div className="space-y-3">
                        <div className="flex items-center gap-2">
                            {result?.isSimulation ? (
                                <>
                                    <BarChart3 size={16} className="text-[var(--color-info-500)] shrink-0" />
                                    <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                                        Résultats de la simulation (prévisionnels)
                                    </h2>
                                </>
                            ) : (
                                <>
                                    <CheckCircle2 size={16} className="text-[var(--color-success-500)] shrink-0" />
                                    <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        {result ? 'Résultats du calcul réel' : 'Dernier état calculé en base'}
                                    </h2>
                                </>
                            )}
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
                            <StatCard
                                title="Bulletins Générés"
                                value={displayKpis.bulletins}
                                subtext="Salariés éligibles calculés"
                                icon={<FileText size={18} />}
                                color="neutral"
                            />
                            <StatCard
                                title="Masse Brute"
                                value={formatAr(displayKpis.brut)}
                                subtext="Base + Heures Supp + Primes"
                                icon={<DollarSign size={18} />}
                                color="info"
                            />
                            <StatCard
                                title="Cotisations Salariales"
                                value={`-${formatAr(displayKpis.cotisationsSalariales)}`}
                                subtext="CNaPS (1%) + Santé (1%)"
                                icon={<ShieldAlert size={18} />}
                                color="danger"
                            />
                            <StatCard
                                title="Retenue IRSA DGI"
                                value={`-${formatAr(displayKpis.irsa)}`}
                                subtext="Impôt sur le revenu retenu"
                                icon={<ShieldAlert size={18} />}
                                color="danger"
                            />
                            <StatCard
                                title="Net à Payer Global"
                                value={formatAr(displayKpis.net)}
                                subtext="Total virements bancaires"
                                icon={<Banknote size={18} />}
                                color="success"
                            />
                            <StatCard
                                title="Coût Global Employeur"
                                value={formatAr(displayKpis.coutGlobal)}
                                subtext={`Dont ${formatAr(displayKpis.patronales)} charges patr.`}
                                icon={<DollarSign size={18} />}
                                color="brand"
                            />
                        </div>
                    </div>
                )}
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Variables de paie',
                    destination: `/paie/periodes/${numericPeriodId}/variables`,
                }}
                nextStep={{
                    label: 'Contrôle & Revue',
                    destination: `/paie/periodes/${numericPeriodId}/controle`,
                    disabled: !hasBulletins,
                    disabledReason: 'Veuillez d’abord lancer le calcul de la paie pour passer au contrôle.',
                }}
            />
        </div>
    );
}
