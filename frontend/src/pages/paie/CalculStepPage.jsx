import { useState, useCallback, useEffect, useMemo, lazy, Suspense, Fragment } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Calculator, Play, CheckCircle2, BarChart3, DollarSign, Banknote, ShieldAlert,
    FileText, Search, ChevronDown, ChevronUp, Eye, Layers
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';

const BulletinPaieDrawer = lazy(() => import('@/components/paie/BulletinPaieDrawer'));

/**
 * CalculStepPage — Étape 4 : Calcul de la paie & Simulation détaillée.
 *
 * Expose la simulation et le calcul de paie avec la cascade canonique :
 * Base - Absence - Déduction Congé + Indemnité Congé + HS + Primes = Brut
 * - CNaPS - Santé = Net Imposable
 * - IRSA - Avances/Acomptes = Net à Payer
 *
 * Règle d'or : Zéro recalcul mathématique côté React.
 * Toutes les valeurs proviennent directement du moteur backend.
 */
export default function CalculStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [calcLoading, setCalcLoading] = useState(false);
    const [simLoading, setSimLoading] = useState(false);
    const [result, setResult] = useState(null);
    const [outcomes, setOutcomes] = useState([]);
    const [existingKpis, setExistingKpis] = useState(null);
    const [existingBulletins, setExistingBulletins] = useState([]);
    const [detailsCache, setDetailsCache] = useState({});
    const [expandedRowKey, setExpandedRowKey] = useState(null);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [feedback, setFeedback] = useState(null);

    const actions = context?.availableActions || {};
    const canCalculate = actions?.CALCULATE?.allowed === true;
    const canSimulate = actions?.SIMULATE?.allowed === true;
    const recalcRequired = context?.recalculationRequired === true;
    const recalcReason = context?.recalculationReason;

    // Charger les données de la période (KPIs et bulletins existants)
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
            if (Array.isArray(res.employeeOutcomes)) {
                setOutcomes(res.employeeOutcomes);
            }
            setFeedback({
                type: 'success',
                text: 'Simulation collective exécutée avec succès en mémoire. Examinez la décomposition individuelle ci-dessous.'
            });
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors de la simulation.' });
        } finally {
            setSimLoading(false);
        }
    }, [numericPeriodId]);

    const handleCalculer = useCallback(async () => {
        if (!numericPeriodId) return;
        if (!window.confirm('Confirmer le calcul réel de la paie ? Les bulletins seront officiellement créés ou mis à jour en base.')) return;
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
            if (Array.isArray(res.employeeOutcomes)) {
                setOutcomes(res.employeeOutcomes);
            }
            setFeedback({ type: 'success', text: 'Calcul de paie réel effectué et persisté avec succès.' });
            await refresh();
            await loadExistingCalculation();
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur lors du calcul.' });
        } finally {
            setCalcLoading(false);
        }
    }, [numericPeriodId, refresh, loadExistingCalculation]);

    // Charger les détails complets d'un bulletin existant pour afficher le waterfall si nécessaire
    const toggleExpandRow = async (key, idBulletin) => {
        if (expandedRowKey === key) {
            setExpandedRowKey(null);
            return;
        }
        setExpandedRowKey(key);
        if (idBulletin && !detailsCache[idBulletin]) {
            try {
                const complet = await paieService.getBulletinComplet(idBulletin);
                setDetailsCache(prev => ({ ...prev, [idBulletin]: complet }));
            } catch (err) {
                console.error('Erreur chargement détail bulletin:', err);
            }
        }
    };

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

    // Normalisation des lignes pour le tableau de simulation / décomposition
    const simulationRows = useMemo(() => {
        if (outcomes.length > 0) {
            return outcomes.map((o, idx) => {
                const bpc = o.result || {};
                const b = bpc.bulletin || {};
                const expl = bpc.explication || {};
                const lignes = Array.isArray(bpc.lignes) ? bpc.lignes : [];

                const findLigne = (codes) => {
                    const list = Array.isArray(codes) ? codes : [codes];
                    return lignes.find(l => list.includes(l.codeRubrique));
                };

                const salaireBase = b.salaireBase ?? expl.salaireBaseContractuel ?? 0;
                const montantAbsence = expl.montantRetenueAbsence ?? findLigne(['ABS_HEURE', 'RETENUE_ABSENCE'])?.montantSalarial ?? 0;
                const deductionConge = expl.deductionCongeTrentieme ?? findLigne(['DED_CONGE_BASE', 'DEDUCTION_CONGE_BASE'])?.montantSalarial ?? 0;
                const indemniteConge = expl.indemniteConge ?? findLigne(['INDEMNITE_CONGE', 'INDEMNITE_CONGE_PAYE'])?.montantSalarial ?? 0;
                const hs30 = expl.montantHs30 ?? findLigne(['HS_30', 'HEURES_SUP_30'])?.montantSalarial ?? 0;
                const hs50 = expl.montantHs50 ?? findLigne(['HS_50', 'HEURES_SUP_50'])?.montantSalarial ?? 0;
                const totalHs = b.totalHeuresSupplementaires ?? b.montantHeuresSupplementaires ?? null;
                const brut = b.totalGainsBruts ?? 0;
                const primesEtAutres = b.totalPrimesEtAutres ?? b.totalAutresGainsBruts ?? null;
                const cnapsSal = b.cotisationCnapsSalariale ?? findLigne(['CNAPS_SAL', 'COTISATION_CNAPS_SAL'])?.montantSalarial ?? 0;
                const santeSal = b.cotisationSanteSalariale ?? findLigne(['SANTE_SAL', 'OSTIE_SAL', 'COTISATION_OSTIE_SAL'])?.montantSalarial ?? 0;
                const netImposable = b.revenuNetImposable ?? b.salaireNetImposable ?? 0;
                const irsa = b.irsaNet ?? 0;
                const avancesAcomptes = b.totalRetenuesAcomptePret ?? 0;
                const netAPayer = b.netAPayer ?? 0;

                return {
                    rowKey: `outcome-${o.employeeId || idx}`,
                    idEmploye: o.employeeId || b.idEmploye,
                    idBulletin: b.idBulletin,
                    matricule: b.matricule || `EMP-${o.employeeId}`,
                    nomPrenom: b.nomPrenom || `Employé #${o.employeeId}`,
                    status: o.status,
                    message: o.message,
                    salaireBase,
                    heuresAbsence: expl.heuresAbsenceRetenues,
                    montantAbsence,
                    joursConge: expl.nbJoursConge,
                    deductionConge,
                    indemniteConge,
                    hs30,
                    hs50,
                    totalHs,
                    primesEtAutres,
                    brut,
                    cnapsSal,
                    santeSal,
                    cotisationsSalariales: b.totalCotisationsSalariales ?? null,
                    netImposable,
                    irsa,
                    avancesAcomptes,
                    netAPayer,
                    complet: bpc,
                };
            });
        }

        // Sinon, utiliser les bulletins existants en base
        return existingBulletins.map((b) => {
            const complet = detailsCache[b.idBulletin];
            const expl = complet?.explication || {};
            const lignes = Array.isArray(complet?.lignes) ? complet.lignes : [];

            const findLigne = (codes) => {
                const list = Array.isArray(codes) ? codes : [codes];
                return lignes.find(l => list.includes(l.codeRubrique));
            };

            const salaireBase = b.salaireBase ?? 0;
            const montantAbsence = expl.montantRetenueAbsence ?? findLigne(['ABS_HEURE', 'RETENUE_ABSENCE'])?.montantSalarial ?? 0;
            const deductionConge = expl.deductionCongeTrentieme ?? findLigne(['DED_CONGE_BASE', 'DEDUCTION_CONGE_BASE'])?.montantSalarial ?? 0;
            const indemniteConge = expl.indemniteConge ?? findLigne(['INDEMNITE_CONGE', 'INDEMNITE_CONGE_PAYE'])?.montantSalarial ?? 0;
            const hs30 = expl.montantHs30 ?? findLigne(['HS_30', 'HEURES_SUP_30'])?.montantSalarial ?? 0;
            const hs50 = expl.montantHs50 ?? findLigne(['HS_50', 'HEURES_SUP_50'])?.montantSalarial ?? 0;
            const totalHs = b.totalHeuresSupplementaires ?? b.montantHeuresSupplementaires ?? null;
            const brut = b.totalGainsBruts ?? 0;
            const primesEtAutres = b.totalPrimesEtAutres ?? b.totalAutresGainsBruts ?? null;
            const cnapsSal = complet?.bulletin?.cotisationCnapsSalariale ?? findLigne(['CNAPS_SAL', 'COTISATION_CNAPS_SAL'])?.montantSalarial ?? 0;
            const santeSal = complet?.bulletin?.cotisationSanteSalariale ?? findLigne(['SANTE_SAL', 'OSTIE_SAL', 'COTISATION_OSTIE_SAL'])?.montantSalarial ?? 0;
            const cotisationsSalariales = b.totalCotisationsSalariales ?? null;
            const netImposable = b.revenuNetImposable ?? b.salaireNetImposable ?? null;
            const irsa = b.irsaNet ?? 0;
            const avancesAcomptes = complet?.bulletin?.totalRetenuesAcomptePret ?? 0;
            const netAPayer = b.netAPayer ?? 0;

            return {
                rowKey: `bulletin-${b.idBulletin}`,
                idEmploye: b.idEmploye,
                idBulletin: b.idBulletin,
                matricule: b.matricule,
                nomPrenom: b.nomPrenom || b.intitulePoste,
                status: 'OK',
                salaireBase,
                heuresAbsence: expl.heuresAbsenceRetenues,
                montantAbsence,
                joursConge: expl.nbJoursConge,
                deductionConge,
                indemniteConge,
                hs30,
                hs50,
                totalHs,
                primesEtAutres,
                brut,
                cnapsSal,
                santeSal,
                cotisationsSalariales,
                netImposable,
                irsa,
                avancesAcomptes,
                netAPayer,
                complet,
            };
        });
    }, [outcomes, existingBulletins, detailsCache]);

    const filteredRows = useMemo(() => {
        if (!searchQuery) return simulationRows;
        const q = searchQuery.toLowerCase();
        return simulationRows.filter(r =>
            (r.matricule && r.matricule.toLowerCase().includes(q)) ||
            (r.nomPrenom && r.nomPrenom.toLowerCase().includes(q))
        );
    }, [simulationRows, searchQuery]);

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Calcul de la paie & Simulation | Paie GALAXAM SIRH"
                description="Lancement du calcul de la campagne de paie : simulation waterfall et calcul réel."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/calcul`)}
                activeStep="CALCULATION"
                loading={ctxLoading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-7xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-sm">
                        <Calculator size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 4 — Calcul de la paie & Simulation détaillée
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Exécutez la simulation pour vérifier la cascade canonique avant de lancer le calcul réel officiel.
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
                                Statut d'éligibilité du calcul
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

                {/* Résultats globaux du calcul via StatCard */}
                {displayKpis && (
                    <div className="space-y-3">
                        <div className="flex items-center gap-2">
                            {result?.isSimulation ? (
                                <>
                                    <BarChart3 size={16} className="text-[var(--color-info-500)] shrink-0" />
                                    <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                                        Résultats de la simulation (prévisionnels en mémoire)
                                    </h2>
                                </>
                            ) : (
                                <>
                                    <CheckCircle2 size={16} className="text-[var(--color-success-500)] shrink-0" />
                                    <h2 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        {result ? 'Résultats du calcul réel persisté' : 'Dernier état calculé en base'}
                                    </h2>
                                </>
                            )}
                        </div>

                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3.5">
                            <StatCard
                                title="Bulletins Prêts"
                                value={displayKpis.bulletins}
                                subtext="Salariés éligibles calculés"
                                icon={<FileText size={18} />}
                                color="neutral"
                            />
                            <StatCard
                                title="Masse Salariale Brute"
                                value={formatAr(displayKpis.brut)}
                                subtext="Base + HS + Primes - Absences/Congés"
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
                                title="Retenue IRSA (DGI)"
                                value={`-${formatAr(displayKpis.irsa)}`}
                                subtext="Impôt sur les revenus net retenu"
                                icon={<ShieldAlert size={18} />}
                                color="danger"
                            />
                            <StatCard
                                title="Net à Payer Global"
                                value={formatAr(displayKpis.net)}
                                subtext="Total virements bancaires collaborateurs"
                                icon={<Banknote size={18} />}
                                color="success"
                            />
                            <StatCard
                                title="Coût Global Employeur"
                                value={formatAr(displayKpis.coutGlobal)}
                                subtext={`Dont ${formatAr(displayKpis.patronales)} charges patronales`}
                                icon={<DollarSign size={18} />}
                                color="brand"
                            />
                        </div>
                    </div>
                )}

                {/* Section 13 : Décomposition individuelle de la simulation / calcul */}
                {simulationRows.length > 0 && (
                    <Card padding className="space-y-4 shadow-xs">
                        <div className="flex items-center justify-between gap-4 flex-wrap">
                            <div className="flex items-center gap-2">
                                <Layers size={18} className="text-[var(--color-brand-500)]" />
                                <div>
                                    <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                        Décomposition individuelle du calcul ({simulationRows.length} salariés)
                                    </h3>
                                    <p className="text-xs text-[var(--color-fg-muted)]">
                                        Compréhension transparente de la cascade : Base $\to$ Brut $\to$ Net Imposable $\to$ Net à Payer.
                                    </p>
                                </div>
                            </div>
                            <div className="w-full sm:w-64">
                                <Input
                                    size="sm"
                                    placeholder="Rechercher par matricule ou nom..."
                                    value={searchQuery}
                                    onChange={(e) => setSearchQuery(e.target.value)}
                                    iconLeft={<Search size={14} />}
                                />
                            </div>
                        </div>

                        <div className="border border-[var(--color-border-subtle)] overflow-x-auto">
                            <Table>
                                <Thead>
                                    <Tr>
                                        <Th>Matricule & Collaborateur</Th>
                                        <Th className="text-right">Salaire Base</Th>
                                        <Th className="text-right">Brut</Th>
                                        <Th className="text-right">Cotis. Salariales</Th>
                                        <Th className="text-right">Net Imposable</Th>
                                        <Th className="text-right">IRSA</Th>
                                        <Th className="text-right">Net à Payer</Th>
                                        <Th className="text-center">Cascade de Calcul</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {filteredRows.map((r) => {
                                        const isExpanded = expandedRowKey === r.rowKey;
                                        return (
                                            <Fragment key={r.rowKey}>
                                                <Tr className={isExpanded ? 'bg-[var(--color-surface-hover)]' : ''}>
                                                    <Td className="font-medium text-[var(--color-fg)]">
                                                        <div className="flex flex-col">
                                                            <span className="font-semibold text-xs text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                                                {r.matricule}
                                                            </span>
                                                            <span className="text-xs text-[var(--color-fg)]">
                                                                {r.nomPrenom}
                                                            </span>
                                                        </div>
                                                    </Td>
                                                    <Td className="text-right text-xs font-mono">{formatAr(r.salaireBase)}</Td>
                                                    <Td className="text-right text-xs font-mono font-medium text-[var(--color-info-600)] dark:text-[var(--color-info-400)]">
                                                        {formatAr(r.brut)}
                                                    </Td>
                                                    <Td className="text-right text-xs font-mono text-[var(--color-danger-500)]">
                                                        -{formatAr(r.cotisationsSalariales)}
                                                    </Td>
                                                    <Td className="text-right text-xs font-mono font-medium">
                                                        {formatAr(r.netImposable)}
                                                    </Td>
                                                    <Td className="text-right text-xs font-mono text-[var(--color-danger-500)]">
                                                        -{formatAr(r.irsa)}
                                                    </Td>
                                                    <Td className="text-right text-xs font-mono font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                        {formatAr(r.netAPayer)}
                                                    </Td>
                                                    <Td className="text-center">
                                                        <div className="flex items-center justify-center gap-1.5">
                                                            <Button
                                                                variant="outline"
                                                                size="xs"
                                                                iconRight={isExpanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                                                                onClick={() => toggleExpandRow(r.rowKey, r.idBulletin)}
                                                            >
                                                                {isExpanded ? 'Masquer' : 'Détail Waterfall'}
                                                            </Button>
                                                            {r.idBulletin && (
                                                                <Button
                                                                    variant="ghost"
                                                                    size="xs"
                                                                    iconLeft={<Eye size={12} />}
                                                                    onClick={() => setSelectedBulletinId(r.idBulletin)}
                                                                    title="Voir le bulletin complet"
                                                                />
                                                            )}
                                                        </div>
                                                    </Td>
                                                </Tr>

                                                {/* Ligne dépliée : Cascade Waterfall conforme au point 13 */}
                                                {isExpanded && (
                                                    <Tr>
                                                        <Td colSpan={8} className="p-0 border-b border-[var(--color-border-subtle)] bg-[var(--color-surface-subtle)]">
                                                            <div className="p-4 space-y-4">
                                                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                                                    <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                                                        <Layers size={14} className="text-[var(--color-brand-500)]" />
                                                                        Explication mathématique canonique — {r.matricule} ({r.nomPrenom})
                                                                    </span>
                                                                    {r.idBulletin && (
                                                                        <Button
                                                                            variant="outline"
                                                                            size="xs"
                                                                            iconLeft={<FileText size={12} />}
                                                                            onClick={() => setSelectedBulletinId(r.idBulletin)}
                                                                        >
                                                                            Ouvrir Bulletin Officiel
                                                                        </Button>
                                                                    )}
                                                                </div>

                                                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                                                    {/* Étape 1 : Construction du Brut */}
                                                                    <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                                                        <div className="text-xs font-bold text-[var(--color-fg)] pb-1 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                                                                            <span>1. Total Brut</span>
                                                                            <span className="text-[var(--color-info-600)] font-mono">{formatAr(r.brut)}</span>
                                                                        </div>
                                                                        <div className="text-xs space-y-1 font-mono text-[var(--color-fg-muted)]">
                                                                            <div className="flex justify-between">
                                                                                <span>Salaire contractuel</span>
                                                                                <span className="text-[var(--color-fg)]">+{formatAr(r.salaireBase)}</span>
                                                                            </div>
                                                                            {Number(r.montantAbsence) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                    <span>Retenue absence ({r.heuresAbsence || '—'} h)</span>
                                                                                    <span>-{formatAr(r.montantAbsence)}</span>
                                                                                </div>
                                                                            )}
                                                                            {Number(r.deductionConge) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                    <span>Déduction congé base 1/30</span>
                                                                                    <span>-{formatAr(r.deductionConge)}</span>
                                                                                </div>
                                                                            )}
                                                                            {Number(r.indemniteConge) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-success-600)]">
                                                                                    <span>Indemnité congé (TCONGE)</span>
                                                                                    <span>+{formatAr(r.indemniteConge)}</span>
                                                                                </div>
                                                                            )}
                                                                            {Number(r.totalHs) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-success-600)]">
                                                                                    <span>Heures supp (30% + 50%)</span>
                                                                                    <span>+{formatAr(r.totalHs)}</span>
                                                                                </div>
                                                                            )}
                                                                            {Number(r.primesEtAutres) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-success-600)]">
                                                                                    <span>Primes & Majos</span>
                                                                                    <span>+{formatAr(r.primesEtAutres)}</span>
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    </div>

                                                                    {/* Étape 2 : Cotisations & Net Imposable */}
                                                                    <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                                                        <div className="text-xs font-bold text-[var(--color-fg)] pb-1 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                                                                            <span>2. Net Imposable</span>
                                                                            <span className="text-[var(--color-fg)] font-mono">{formatAr(r.netImposable)}</span>
                                                                        </div>
                                                                        <div className="text-xs space-y-1 font-mono text-[var(--color-fg-muted)]">
                                                                            <div className="flex justify-between">
                                                                                <span>Total Brut</span>
                                                                                <span className="text-[var(--color-fg)]">{formatAr(r.brut)}</span>
                                                                            </div>
                                                                            <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                <span>CNaPS Salarié (1%)</span>
                                                                                <span>-{formatAr(r.cnapsSal)}</span>
                                                                            </div>
                                                                            <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                <span>Santé / Mutuelle (1%)</span>
                                                                                <span>-{formatAr(r.santeSal)}</span>
                                                                            </div>
                                                                        </div>
                                                                    </div>

                                                                    {/* Étape 3 : Retenues & Net à Payer */}
                                                                    <div className="p-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] space-y-2">
                                                                        <div className="text-xs font-bold text-[var(--color-fg)] pb-1 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                                                                            <span>3. Net à Payer</span>
                                                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-mono font-bold">
                                                                                {formatAr(r.netAPayer)}
                                                                            </span>
                                                                        </div>
                                                                        <div className="text-xs space-y-1 font-mono text-[var(--color-fg-muted)]">
                                                                            <div className="flex justify-between">
                                                                                <span>Net Imposable</span>
                                                                                <span className="text-[var(--color-fg)]">{formatAr(r.netImposable)}</span>
                                                                            </div>
                                                                            <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                <span>Retenue IRSA (DGI)</span>
                                                                                <span>-{formatAr(r.irsa)}</span>
                                                                            </div>
                                                                            {Number(r.avancesAcomptes) > 0 && (
                                                                                <div className="flex justify-between text-[var(--color-danger-500)]">
                                                                                    <span>Acomptes & Avances</span>
                                                                                    <span>-{formatAr(r.avancesAcomptes)}</span>
                                                                                </div>
                                                                            )}
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                        </Td>
                                                    </Tr>
                                                )}
                                            </Fragment>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                    </Card>
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
        </div>
    );
}
