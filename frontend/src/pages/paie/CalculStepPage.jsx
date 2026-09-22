import { useState, useCallback, useEffect, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Calculator, Play, CheckCircle2, AlertTriangle, RefreshCw, Eye,
    Users, ArrowRight, Sparkles, ChevronDown, ChevronUp
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/components/paie/PayrollCampaignShell';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';

const BulletinPaieDrawer = lazy(() => import('@/components/paie/BulletinPaieDrawer'));

/**
 * CalculStepPage — Étape 4 : Calcul de la paie (Maquette V1 Full V3).
 *
 * États opérationnels :
 * 1. AVANT CALCUL : Campagne prête à calculer (X salariés, 0 blocker, actions [Simuler] et [Calculer])
 * 2. APRES CALCUL : Calcul vN (X/X calculés, compteurs, KPIs financiers consolidés, [Voir les résultats])
 * 3. STALE (Calcul obsolète) : Signal clair, liste des salariés affectés (ex: snapshot pointage), validation verrouillée, action [Recalculer]
 *
 * Règle stricte : Zéro recalcul mathématique dans React.
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
    const [existingBulletins, setExistingBulletins] = useState([]);
    const [showResultsTable, setShowResultsTable] = useState(false);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [feedback, setFeedback] = useState(null);

    const actions = context?.availableActions || {};
    const canCalculate = actions?.CALCULATE?.allowed === true;
    const canSimulate = actions?.SIMULATE?.allowed === true;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;
    const isStale = recalcRequired;
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const isReadOnly = Boolean(context?.isReadOnly ?? context?.readOnly ?? ['PAIE_VALIDEE', 'CLOTUREE', 'ARCHIVEE'].includes(periode?.state));

    // Charger les résultats existants de la période
    const loadExistingCalculation = useCallback(async () => {
        if (!numericPeriodId) return;
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { size: 100 });
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

            if (Array.isArray(cockpit?.bulletins) && cockpit.bulletins.length > 0) {
                setExistingBulletins(cockpit.bulletins);
            } else {
                setExistingBulletins([]);
            }
        } catch {
            setExistingKpis(null);
            setExistingBulletins([]);
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
            setShowResultsTable(true);
            setFeedback({
                type: 'info',
                text: 'Simulation collective exécutée en mémoire. Aucun bulletin n’a été persisté.'
            });
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de la simulation.' });
        } finally {
            setSimLoading(false);
        }
    }, [numericPeriodId]);

    const handleCalculer = useCallback(async () => {
        if (!numericPeriodId) return;
        if (!window.confirm('Confirmer le calcul réel de la paie pour l’ensemble des collaborateurs éligibles ?')) return;
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
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de l’exécution du calcul.' });
        } finally {
            setCalcLoading(false);
        }
    }, [numericPeriodId, refresh, loadExistingCalculation]);

    const hasPersistedBulletins = Boolean((existingKpis?.bulletins ?? 0) > 0 || (result && !result.isSimulation && (result?.bulletins ?? 0) > 0));
    const isSimulationActive = Boolean(result?.isSimulation && !hasPersistedBulletins);
    const hasBulletins = hasPersistedBulletins || isSimulationActive;
    const displayKpis = result || existingKpis;
    const populationCount = context?.population?.eligibleCount ?? context?.periodContext?.nbSalariesTotal ?? existingBulletins.length;
    const calculationVersion = context?.periodContext?.calculationSnapshotVersion || context?.snapshotVersion || (existingKpis ? 'v1' : null);

    // Salariés affectés par une obsolescence STALE (si fournis par le contexte)
    const affectedEmployees = context?.staleAffectedEmployees || context?.affectedEmployees || [];

    const handlePrimaryClick = () => {
        if (isStale || !hasPersistedBulletins) {
            handleCalculer();
        } else {
            navigate(`/paie/periodes/${numericPeriodId}/controle`);
        }
    };

    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="CALCULATION"
            loading={ctxLoading}
            onRefresh={refresh}
            title="Calcul de la paie"
            description="Exécution du moteur de paie backend, simulations collectives et détection d'obsolescence."
            primaryAction={{
                label: isStale ? 'Recalculer la paie' : (hasPersistedBulletins ? 'Continuer vers Contrôle' : 'Calculer la paie'),
                onClick: handlePrimaryClick,
                loading: calcLoading,
                disabled: isReadOnly ? true : (!hasPersistedBulletins && !canCalculate),
                icon: isStale ? <RefreshCw size={14} /> : (hasPersistedBulletins ? <ArrowRight size={14} /> : <Play size={14} />)
            }}
            secondaryAction={!isReadOnly && canSimulate ? {
                label: 'Simuler en mémoire',
                onClick: handleSimuler,
                loading: simLoading,
                disabled: calcLoading,
                icon: <Sparkles size={14} />
            } : null}
        >
            <SEO
                title="Calcul de la paie | GALAXAM SIRH"
                description="Calcul et simulation du moteur de paie DAF GALAXAM."
            />

            <div className="space-y-6">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        title={feedback.type === 'error' ? 'Erreur de calcul' : 'Notification'}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* 1. ÉTAT STALE (CALCUL OBSOLÈTE) */}
                {isStale && (
                    <div className="p-4 bg-[var(--color-warning-500)]/10 border-2 border-[var(--color-warning-500)] rounded-md space-y-3">
                        <div className="flex items-start gap-3">
                            <AlertTriangle size={20} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                            <div className="flex-1">
                                <div className="flex items-center gap-2">
                                    <h3 className="text-sm font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] uppercase tracking-wide">
                                        Calcul obsolète (STALE)
                                    </h3>
                                    <Badge variant="warning" size="xs">Validation verrouillée</Badge>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                                    {recalcReason === 'CALCULATION_INPUTS_CHANGED'
                                        ? 'Des modifications ont été apportées aux variables ou aux snapshots de pointage depuis le dernier calcul. Un recalcul complet est requis avant validation.'
                                        : (recalcReason || 'Les données sources de paie ont changé. Les bulletins actuels ne reflètent plus l’état réel.')}
                                </p>
                            </div>
                        </div>

                        {affectedEmployees.length > 0 && (
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded text-xs space-y-2">
                                <span className="font-semibold text-[var(--color-fg)]">
                                    {affectedEmployees.length} collaborateur(s) directement affecté(s) :
                                </span>
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
                                    {affectedEmployees.map((emp, idx) => (
                                        <div key={idx} className="flex items-center justify-between p-2 bg-[var(--color-bg-subtle)] rounded border border-[var(--color-border-subtle)]">
                                            <span className="font-medium text-[var(--color-fg)]">{emp.nomPrenom || emp.matricule}</span>
                                            <span className="text-[11px] text-[var(--color-fg-muted)] font-mono">
                                                {emp.source || 'Pointage'} {emp.detail || 'snapshot màj'}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        )}

                        <div className="flex items-center justify-end gap-2 pt-1">
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={handleCalculer}
                                loading={calcLoading}
                                iconLeft={<RefreshCw size={14} />}
                            >
                                Recalculer les salariés affectés
                            </Button>
                        </div>
                    </div>
                )}

                {/* 2. AVANT CALCUL : CAMPAGNE PRÊTE */}
                {!hasBulletins && !isStale && (
                    <Card className="p-8 text-center space-y-4 border border-[var(--color-border)]">
                        <div className="w-14 h-14 bg-[var(--color-brand-50)] dark:bg-[var(--color-brand-950)] text-[var(--color-brand-600)] rounded-full flex items-center justify-center mx-auto shadow-sm">
                            <Calculator size={28} />
                        </div>
                        <div className="max-w-md mx-auto space-y-1">
                            <h3 className="text-base font-bold text-[var(--color-fg)]">
                                Campagne prête pour le calcul
                            </h3>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                Les contrôles de préparation sont validés et la population de {populationCount} salarié(s) est arrêtée.
                            </p>
                        </div>

                        <div className="flex items-center justify-center gap-6 py-2 text-xs">
                            <div className="flex items-center gap-2">
                                <Users size={16} className="text-[var(--color-brand-600)]" />
                                <span className="font-semibold text-[var(--color-fg)]">{populationCount} salariés inclus</span>
                            </div>
                            <div className="flex items-center gap-2">
                                <CheckCircle2 size={16} className="text-[var(--color-success-600)]" />
                                <span className="font-semibold text-[var(--color-fg)]">0 bloquant de préparation</span>
                            </div>
                        </div>

                        {!isReadOnly && (
                            <div className="flex items-center justify-center gap-3 pt-2">
                                <Button
                                    variant="secondary"
                                    size="md"
                                    onClick={handleSimuler}
                                    loading={simLoading}
                                    iconLeft={<Sparkles size={16} />}
                                >
                                    Simuler en mémoire
                                </Button>
                                <Button
                                    variant="primary"
                                    size="md"
                                    onClick={handleCalculer}
                                    loading={calcLoading}
                                    disabled={!canCalculate}
                                    iconLeft={<Play size={16} />}
                                >
                                    Calculer la paie
                                </Button>
                            </div>
                        )}
                    </Card>
                )}

                {/* 3. APRÈS CALCUL : SYNTHÈSE DES RÉSULTATS */}
                {hasBulletins && (
                    <div className="space-y-4">
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-md flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                            <div className="flex items-center gap-3">
                                <div className="w-10 h-10 bg-[var(--color-success-50)] dark:bg-[var(--color-success-950)] text-[var(--color-success-600)] rounded-full flex items-center justify-center shrink-0">
                                    <CheckCircle2 size={20} />
                                </div>
                                <div>
                                    <div className="flex items-center gap-2">
                                        <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                            Calcul {calculationVersion ? calculationVersion : 'effectué'}
                                        </h3>
                                        {result?.isSimulation && <Badge variant="warning" size="xs">Simulation en mémoire</Badge>}
                                        {!isStale && <Badge variant="success" size="xs">À jour</Badge>}
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        {displayKpis?.bulletins || existingBulletins.length} bulletin(s) généré(s) • 0 erreur bloquante
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-center gap-2">
                                <Button
                                    variant="secondary"
                                    size="sm"
                                    onClick={() => setShowResultsTable(!showResultsTable)}
                                    iconRight={showResultsTable ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
                                >
                                    {showResultsTable ? 'Masquer la décomposition' : 'Voir les résultats détaillés'}
                                </Button>
                                {isSimulationActive ? (
                                    <Button
                                        variant="primary"
                                        size="sm"
                                        onClick={handleCalculer}
                                        loading={calcLoading}
                                        disabled={!canCalculate}
                                        iconLeft={<Play size={14} />}
                                    >
                                        Calculer la paie
                                    </Button>
                                ) : (
                                    <Button
                                        variant="primary"
                                        size="sm"
                                        onClick={() => navigate(`/paie/periodes/${numericPeriodId}/controle`)}
                                        iconRight={<ArrowRight size={14} />}
                                    >
                                        Cockpit de contrôle
                                    </Button>
                                )}
                            </div>
                        </div>

                        {/* 4 KPIs Clés consolidés */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 rounded-md">
                                <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                                    Masse Salariale Brute
                                </span>
                                <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                                    {formatMoneyAr(displayKpis?.brut)}
                                </span>
                            </div>
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 rounded-md">
                                <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                                    Cotisations Salariales
                                </span>
                                <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                                    {formatMoneyAr(displayKpis?.cotisationsSalariales)}
                                </span>
                            </div>
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3.5 rounded-md">
                                <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                                    Retenue IRSA (DGI)
                                </span>
                                <span className="text-base font-black font-mono text-[var(--color-fg)] mt-1 block">
                                    {formatMoneyAr(displayKpis?.irsa)}
                                </span>
                            </div>
                            <div className="bg-[var(--color-surface)] border-2 border-[var(--color-success-500)]/40 p-3.5 rounded-md bg-[var(--color-success-50)]/20">
                                <span className="text-[11px] font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)] uppercase tracking-wider block">
                                    Total Net à Payer
                                </span>
                                <span className="text-base font-black font-mono text-[var(--color-success-700)] dark:text-[var(--color-success-300)] mt-1 block">
                                    {formatMoneyAr(displayKpis?.net)}
                                </span>
                            </div>
                        </div>

                        {/* Décomposition technique masquée par défaut pour éviter l'encombrement cognitif */}
                        {showResultsTable && (
                            <Card className="border border-[var(--color-border-subtle)] overflow-hidden">
                                <div className="p-3 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                                    <span className="text-xs font-bold text-[var(--color-fg)]">
                                        Décomposition individuelle ({existingBulletins.length} bulletins)
                                    </span>
                                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                                        Cliquez sur un collaborateur pour ouvrir son dossier
                                    </span>
                                </div>
                                <div className="overflow-x-auto">
                                    <Table className="w-full text-xs">
                                        <Thead>
                                            <Tr>
                                                <Th>Salarié</Th>
                                                <Th className="text-right">Salaire Base</Th>
                                                <Th className="text-right">Brut</Th>
                                                <Th className="text-right">Cotisations</Th>
                                                <Th className="text-right">IRSA</Th>
                                                <Th className="text-right">Net à Payer</Th>
                                                <Th className="text-center w-12">Action</Th>
                                            </Tr>
                                        </Thead>
                                        <Tbody>
                                            {existingBulletins.map((b) => (
                                                <Tr key={b.idBulletin} className="hover:bg-[var(--color-bg-subtle)] transition-colors">
                                                    <Td className="font-medium text-[var(--color-fg)]">
                                                        <div>{b.nomPrenom || b.intitulePoste}</div>
                                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-mono">{b.matricule}</div>
                                                    </Td>
                                                    <Td className="text-right font-mono">{formatMoneyAr(b.salaireBase)}</Td>
                                                    <Td className="text-right font-mono font-medium">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                                    <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                        {b.totalCotisationsSalariales ? `-${formatMoneyAr(b.totalCotisationsSalariales)}` : '—'}
                                                    </Td>
                                                    <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                        {b.irsaNet ? `-${formatMoneyAr(b.irsaNet)}` : '—'}
                                                    </Td>
                                                    <Td className="text-right font-mono font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                                        {formatMoneyAr(b.netAPayer)}
                                                    </Td>
                                                    <Td className="text-center">
                                                        <Button
                                                            variant="ghost"
                                                            size="xs"
                                                            onClick={() => setSelectedBulletinId(b.idBulletin)}
                                                            iconLeft={<Eye size={13} />}
                                                        />
                                                    </Td>
                                                </Tr>
                                            ))}
                                        </Tbody>
                                    </Table>
                                </div>
                            </Card>
                        )}
                    </div>
                )}
            </div>

            {/* Modal Drawer du bulletin de paie complet */}
            <Suspense fallback={null}>
                {selectedBulletinId && (
                    <BulletinPaieDrawer
                        isOpen={Boolean(selectedBulletinId)}
                        onClose={() => setSelectedBulletinId(null)}
                        idBulletin={selectedBulletinId}
                        onRecalculateSuccess={async () => {
                            await refresh();
                            await loadExistingCalculation();
                        }}
                    />
                )}
            </Suspense>
        </PayrollCampaignShell>
    );
}
