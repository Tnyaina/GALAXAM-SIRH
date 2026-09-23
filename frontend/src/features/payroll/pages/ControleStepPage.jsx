import { useState, useEffect, useCallback, useMemo, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    Download, ArrowRight, Search, CheckCircle2, ChevronRight
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getErrorMessage } from '@/api/apiClient';

const BulletinPaieDrawer = lazy(() => import('@/features/payroll/components/views/BulletinPaieDrawer'));

/**
 * ControleStepPage — Étape 5 : État nominatif & Cockpit DAF (Maquette V1 Full V3).
 *
 * Fonctionnalités :
 * - KPIs de revue : Conformes, À examiner, Examinés, Bloqueurs
 * - Filtres d'état rapides : [À examiner] [Tous] [Contrôlés]
 * - Sélecteur de vues : Synthèse (par défaut), Temps, Fiscal & Social
 * - Table nominative compacte avec colonnes utiles (Salarié, Brut, Cotisations, IRSA/ISI, Net, Signal, Revue, >)
 * - Clic sur un salarié -> Ouvre le Drawer de revue salarié sans changer de page
 * - Action principale unique : Continuer vers Validation
 */
export default function ControleStepPage() {
    // Contexte et hook métier
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    // État local
    const [cockpit, setCockpit] = useState(null);
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterTab, setFilterTab] = useState('ALL'); // 'ALL' | 'TO_EXAMINE' | 'REVIEWED'
    const [activeView, setActiveView] = useState('SYNTHESE'); // 'SYNTHESE' | 'TEMPS' | 'FISCAL'
    const [reviewedIds, setReviewedIds] = useState(new Set());
    const [feedback, setFeedback] = useState(null);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);

    // Données dérivées
    const bulletins = useMemo(() => Array.isArray(cockpit?.bulletins) ? cockpit.bulletins : [], [cockpit]);
    const periode = context?.selectedPeriod || context?.currentPeriod;
    const actions = context?.availableActions || {};
    const canContinueToValidation = actions?.VALIDATE?.allowed === true;

    // Calcul des compteurs de revue
    const countTotal = bulletins.length;
    // PRESENTATION_ONLY : le seuil de 10% est utilisé uniquement pour
    // mettre en évidence visuelle les bulletins à examiner en priorité.
    // Il ne bloque pas la validation — la décision appartient au backend (VALIDATE.allowed).
    // Source : backend aAlerte (champ structuré) ou calcul écart M1 fourni par le cockpit.
    const toExamineBulletins = useMemo(() => {
        return bulletins.filter(b => b.aAlerte || (b.ecartM1Pct != null && Math.abs(b.ecartM1Pct) > 10));
    }, [bulletins]);
    const countToExamine = toExamineBulletins.length;
    const countReviewed = reviewedIds.size;
    const countConform = Math.max(0, countTotal - countToExamine);
    const countBlockers = context?.issueSummary?.blockerCount ?? 0;

    // Filtrage des bulletins selon l'onglet actif
    const filteredBulletins = useMemo(() => {
        return bulletins.filter(b => {
            const isToExamine = b.aAlerte || (b.ecartM1Pct != null && Math.abs(b.ecartM1Pct) > 10);
            if (filterTab === 'TO_EXAMINE') {
                return isToExamine;
            }
            if (filterTab === 'REVIEWED') {
                return reviewedIds.has(b.idBulletin);
            }
            return true;
        });
    }, [bulletins, filterTab, reviewedIds]);

    // Effets
    const chargerBulletins = useCallback(async () => {
        if (!numericPeriodId) return;
        setLoading(true);
        setFeedback(null);
        try {
            const data = await paieService.getCockpitPeriode(numericPeriodId, {
                q: searchQuery || undefined,
                size: 200,
            });
            setCockpit(data);
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur de chargement des bulletins de paie.') });
        } finally {
            setLoading(false);
        }
    }, [numericPeriodId, searchQuery]);

    useEffect(() => {
        chargerBulletins();
    }, [chargerBulletins]);

    // Actions
    const handleSelectBulletin = (id) => {
        setSelectedBulletinId(id);
        // Marquer comme examiné dans la session
        setReviewedIds(prev => new Set([...prev, id]));
    };

    const handleExportCsv = () => {
        if (!bulletins.length) return;
        const headers = ['Matricule', 'Salarie', 'Poste', 'Salaire_Base', 'Brut', 'Cotisations', 'IRSA', 'Net', 'Ecart_M1_Pct', 'Statut'];
        const rows = bulletins.map(b => [
            b.matricule,
            `"${b.nomPrenom || b.employeeName || ''}"`,
            `"${b.intitulePoste || b.nomSite || ''}"`,
            b.salaireBase,
            b.totalGainsBruts,
            b.totalCotisationsSalariales || '',
            b.irsaNet,
            b.netAPayer,
            b.ecartM1Pct ?? '',
            b.statut
        ]);
        const csv = [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `etat_nominatif_${periode?.code || numericPeriodId}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    // Rendu
    return (
        <PayrollCampaignShell
            context={context}
            periodes={periodes}
            activeStep="CONTROL"
            loading={ctxLoading}
            onRefresh={refresh}
            title="État nominatif & Contrôle"
            description="Cockpit de revue de conformité et validation des écarts avant scellement officiel."
            primaryAction={{
                label: 'Continuer vers Validation',
                onClick: () => navigate(`/paie/periodes/${numericPeriodId}/validation`),
                disabled: !canContinueToValidation,
                icon: <ArrowRight size={14} />
            }}
            secondaryAction={{
                label: 'Exporter CSV',
                onClick: handleExportCsv,
                icon: <Download size={14} />,
                disabled: bulletins.length === 0
            }}
        >
            <SEO
                title="État nominatif & Contrôle | GALAXAM SIRH"
                description="Contrôle DAF et revue des bulletins de paie."
            />

            <div className="space-y-5">
                {feedback && (
                    <Alert
                        type={feedback.type}
                        title={feedback.type === 'error' ? 'Erreur' : 'Information'}
                        message={feedback.text}
                        onClose={() => setFeedback(null)}
                    />
                )}

                {/* 1. KPIs DE REVUE DAF */}
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Conformes
                        </span>
                        <div className="flex items-baseline gap-2 mt-1">
                            <span className="text-xl font-black font-mono text-[var(--color-success-600)]">
                                {countConform}
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)]">/ {countTotal}</span>
                        </div>
                    </div>

                    <div className={`bg-[var(--color-surface)] border p-3 rounded-none ${countToExamine > 0 ? 'border-[var(--color-warning-500)]/60 bg-[var(--color-warning-50)]/15' : 'border-[var(--color-border-subtle)]'}`}>
                        <span className="text-[11px] font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] uppercase tracking-wider block">
                            À examiner
                        </span>
                        <div className="flex items-baseline gap-2 mt-1">
                            <span className="text-xl font-black font-mono text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                {countToExamine}
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)]">écarts / alertes</span>
                        </div>
                    </div>

                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 rounded-none">
                        <span className="text-[11px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                            Examinés
                        </span>
                        <div className="flex items-baseline gap-2 mt-1">
                            <span className="text-xl font-black font-mono text-[var(--color-brand-600)]">
                                {countReviewed}
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)]">consultés</span>
                        </div>
                    </div>

                    <div className={`bg-[var(--color-surface)] border p-3 rounded-none ${countBlockers > 0 ? 'border-[var(--color-danger-500)] bg-[var(--color-danger-50)]/20' : 'border-[var(--color-border-subtle)]'}`}>
                        <span className="text-[11px] font-bold text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)] uppercase tracking-wider block">
                            Bloquants
                        </span>
                        <div className="flex items-baseline gap-2 mt-1">
                            <span className={`text-xl font-black font-mono ${countBlockers > 0 ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-fg-muted)]'}`}>
                                {countBlockers}
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)]">bloqueurs</span>
                        </div>
                    </div>
                </div>

                {/* 2. BARRE D'OUTILS : FILTRES D'ÉTAT & SÉLECTEUR DE VUES — conforme V1 Full V3 */}
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 py-1.5">
                    {/* Filtres par statut */}
                    <div className="flex items-center gap-1.5">
                        <button
                            type="button"
                            onClick={() => setFilterTab('TO_EXAMINE')}
                            className={`px-3 py-1 text-xs font-semibold rounded transition-colors flex items-center gap-1.5 ${filterTab === 'TO_EXAMINE'
                                ? 'bg-[var(--color-warning-500)] text-white shadow-xs'
                                : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            À examiner
                            {countToExamine > 0 && (
                                <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-white/25">
                                    {countToExamine}
                                </span>
                            )}
                        </button>
                        <button
                            type="button"
                            onClick={() => setFilterTab('ALL')}
                            className={`px-3 py-1 text-xs font-semibold rounded transition-colors flex items-center gap-1.5 ${filterTab === 'ALL'
                                ? 'bg-[var(--color-brand-600)] text-white shadow-xs'
                                : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            Tous
                            <span className="text-[10px] opacity-80">({countTotal})</span>
                        </button>
                        <button
                            type="button"
                            onClick={() => setFilterTab('REVIEWED')}
                            className={`px-3 py-1 text-xs font-semibold rounded transition-colors flex items-center gap-1.5 ${filterTab === 'REVIEWED'
                                ? 'bg-[var(--color-brand-600)] text-white shadow-xs'
                                : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'}`}
                        >
                            Contrôlés
                            <span className="text-[10px] opacity-80">({countReviewed})</span>
                        </button>
                    </div>

                    {/* Vues et Recherche */}
                    <div className="flex items-center gap-2">
                        {/* Sélecteur de vue */}
                        <div className="flex items-center border border-[var(--color-border-subtle)] rounded overflow-hidden text-xs">
                            <button
                                type="button"
                                onClick={() => setActiveView('SYNTHESE')}
                                className={`px-2.5 py-1 font-medium transition-colors ${activeView === 'SYNTHESE' ? 'bg-[var(--color-bg-subtle)] font-bold text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]/50'}`}
                            >
                                Synthèse
                            </button>
                            <button
                                type="button"
                                onClick={() => setActiveView('TEMPS')}
                                className={`px-2.5 py-1 font-medium border-l border-[var(--color-border-subtle)] transition-colors ${activeView === 'TEMPS' ? 'bg-[var(--color-bg-subtle)] font-bold text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]/50'}`}
                            >
                                Temps
                            </button>
                            <button
                                type="button"
                                onClick={() => setActiveView('FISCAL')}
                                className={`px-2.5 py-1 font-medium border-l border-[var(--color-border-subtle)] transition-colors ${activeView === 'FISCAL' ? 'bg-[var(--color-bg-subtle)] font-bold text-[var(--color-brand-600)]' : 'text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]/50'}`}
                            >
                                Fiscal & Social
                            </button>
                        </div>

                        {/* Recherche */}
                        <div className="w-44 sm:w-56">
                            <Input
                                size="xs"
                                placeholder="Recherche nom/matricule..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                leftIcon={<Search size={13} />}
                            />
                        </div>
                    </div>
                </div>

                {/* 3. TABLE NOMINATIVE COMPACTE */}
                {loading ? (
                    <SkeletonTable rows={8} cols={6} />
                ) : filteredBulletins.length === 0 ? (
                    <Card className="p-8 text-center border border-[var(--color-border-subtle)]">
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            {filterTab === 'TO_EXAMINE'
                                ? 'Aucun bulletin nécessitant un examen particulier. Tous les éléments sont conformes.'
                                : 'Aucun bulletin ne correspond aux critères.'}
                        </p>
                    </Card>
                ) : (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] rounded-none overflow-hidden">
                        <div className="overflow-x-auto">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>Salarié</Th>
                                        {activeView === 'SYNTHESE' && (
                                            <>
                                                <Th className="text-right">Brut</Th>
                                                <Th className="text-right">Cotisations</Th>
                                                <Th className="text-right">IRSA / ISI</Th>
                                                <Th className="text-right">Net</Th>
                                                <Th className="text-center">Signal</Th>
                                                <Th className="text-center">Revue</Th>
                                            </>
                                        )}
                                        {activeView === 'TEMPS' && (
                                            <>
                                                <Th className="text-right">Base</Th>
                                                <Th className="text-right">Absences</Th>
                                                <Th className="text-right">Congés</Th>
                                                <Th className="text-right">HS / Primes</Th>
                                                <Th className="text-right">Brut</Th>
                                                <Th className="text-right font-bold">Net</Th>
                                            </>
                                        )}
                                        {activeView === 'FISCAL' && (
                                            <>
                                                <Th className="text-right">Brut</Th>
                                                <Th className="text-right">CNaPS (1%)</Th>
                                                <Th className="text-right">Santé (1%)</Th>
                                                <Th className="text-right">IRSA (DGI)</Th>
                                                <Th className="text-right font-bold">Net à Payer</Th>
                                            </>
                                        )}
                                        <Th className="text-center w-10"></Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {filteredBulletins.map((b) => {
                                        const hasAlert = b.aAlerte;
                                        const ecart = b.ecartM1Pct;
                                        const isReviewed = reviewedIds.has(b.idBulletin);

                                        return (
                                            <Tr
                                                key={b.idBulletin}
                                                onClick={() => handleSelectBulletin(b.idBulletin)}
                                                className="hover:bg-[var(--color-bg-subtle)] cursor-pointer transition-colors"
                                            >
                                                <Td className="font-medium text-[var(--color-fg)]">
                                                    <div className="flex items-center gap-1.5">
                                                        <span>{b.nomPrenom || b.employeeName || b.intitulePoste}</span>
                                                    </div>
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-mono">
                                                        {b.matricule} • {b.nomSite || 'Siège'}
                                                    </div>
                                                </Td>

                                                {/* VUE SYNTHÈSE */}
                                                {activeView === 'SYNTHESE' && (
                                                    <>
                                                        <Td className="text-right font-mono font-medium text-[var(--color-fg)]">
                                                            {formatMoneyAr(b.totalGainsBruts)}
                                                        </Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.totalCotisationsSalariales ? `-${formatMoneyAr(b.totalCotisationsSalariales)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.irsaNet ? `-${formatMoneyAr(b.irsaNet)}` : '0 Ar'}
                                                        </Td>
                                                        <Td className="text-right font-mono font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                                            {formatMoneyAr(b.netAPayer)}
                                                        </Td>
                                                        <Td className="text-center">
                                                            {hasAlert ? (
                                                                <Badge variant="warning" size="xs">Anomalie</Badge>
                                                            ) : ecart != null && Math.abs(ecart) > 10 ? (
                                                                <Badge variant="warning" size="xs">
                                                                    {ecart > 0 ? `+${ecart}%` : `${ecart}%`}
                                                                </Badge>
                                                            ) : (
                                                                <span className="text-[10px] text-[var(--color-success-600)]">Conforme</span>
                                                            )}
                                                        </Td>
                                                        <Td className="text-center">
                                                            {isReviewed ? (
                                                                <span className="inline-flex items-center gap-1 text-[10px] text-[var(--color-brand-600)] font-medium">
                                                                    <CheckCircle2 size={12} /> Vu
                                                                </span>
                                                            ) : (
                                                                <span className="text-[10px] text-[var(--color-fg-muted)]">—</span>
                                                            )}
                                                        </Td>
                                                    </>
                                                )}

                                                {/* VUE TEMPS */}
                                                {activeView === 'TEMPS' && (
                                                    <>
                                                        <Td className="text-right font-mono">{formatMoneyAr(b.salaireBase)}</Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.retenueAbsence ? `-${formatMoneyAr(b.retenueAbsence)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono">
                                                            {b.indemniteConge ? `+${formatMoneyAr(b.indemniteConge)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono text-[var(--color-fg)]">
                                                            {b.majorationHeuresSupplementaires ? `+${formatMoneyAr(b.majorationHeuresSupplementaires)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono font-medium">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                                        <Td className="text-right font-mono font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                                            {formatMoneyAr(b.netAPayer)}
                                                        </Td>
                                                    </>
                                                )}

                                                {/* VUE FISCAL */}
                                                {activeView === 'FISCAL' && (
                                                    <>
                                                        <Td className="text-right font-mono font-medium">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.cotisationCnapsSalariale ? `-${formatMoneyAr(b.cotisationCnapsSalariale)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.cotisationSanteSalariale ? `-${formatMoneyAr(b.cotisationSanteSalariale)}` : '—'}
                                                        </Td>
                                                        <Td className="text-right font-mono text-[var(--color-danger-600)]">
                                                            {b.irsaNet ? `-${formatMoneyAr(b.irsaNet)}` : '0 Ar'}
                                                        </Td>
                                                        <Td className="text-right font-mono font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                                                            {formatMoneyAr(b.netAPayer)}
                                                        </Td>
                                                    </>
                                                )}

                                                <Td className="text-center text-[var(--color-fg-muted)]">
                                                    <ChevronRight size={14} />
                                                </Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                    </div>
                )}
            </div>

            {/* Modal Drawer du bulletin de paie complet (3 niveaux) */}
            <Suspense fallback={null}>
                {selectedBulletinId && (
                    <BulletinPaieDrawer
                        isOpen={Boolean(selectedBulletinId)}
                        onClose={() => setSelectedBulletinId(null)}
                        idBulletin={selectedBulletinId}
                        onRecalculateSuccess={async () => {
                            await refresh();
                            await chargerBulletins();
                        }}
                    />
                )}
            </Suspense>
        </PayrollCampaignShell>
    );
}
