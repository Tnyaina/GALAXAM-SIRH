import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { CheckCircle2, UserX, AlertTriangle } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Badge from '@/components/ui/data-display/Badge';
import { Tr, Td } from '@/components/ui/data-display/Table';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import PayrollStepTable, { PayrollEmployeeCell } from '@/features/payroll/components/workflow/PayrollStepTable';

const DECISION_VARIANTS = {
    INCLUS: 'success',
    INCLUDED: 'success',
    EXCLU: 'default',
    EXCLUDED: 'default',
    BLOQUE: 'danger',
    BLOCKED: 'danger',
    WARNED: 'warning',
};

const DECISION_LABELS = {
    INCLUS: 'Inclus',
    INCLUDED: 'Inclus',
    EXCLU: 'Exclu',
    EXCLUDED: 'Exclu',
    BLOQUE: 'Bloqué',
    BLOCKED: 'Bloqué',
    WARNED: 'Avertissement',
};

const getPopulationReasonLabel = (code) => {
    if (!code) return 'Éligible selon les critères de la campagne';
    const labels = {
        NO_APPLICABLE_ASSIGNMENT: 'Aucune affectation ne couvre la période',
        AMBIGUOUS_ASSIGNMENT: 'Plusieurs affectations couvrent la période',
        NO_APPLICABLE_CONTRACT: 'Contrat ou régime d’intervenant manquant pour la période',
        CAREER_AMBIGUOUS: 'Configuration de carrière ambiguë',
        NO_ACTIVE_CONTRACT: 'Aucun contrat actif sur la période',
        CONTRACT_ENDED: 'Contrat expiré avant la période',
        INACTIVE_EMPLOYEE: 'Salarié inactif',
    };
    return labels[code] || code;
};

/**
 * PopulationStepPage — Étape 2 : Population de la campagne (V1 Full V3).
 */
export default function PopulationStepPage() {
    // Contexte et hook métier
    const { periodId } = useParams();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading } = usePayrollCampaign(numericPeriodId);

    // État local
    const [population, setPopulation] = useState([]);
    const [loading, setLoading] = useState(true);
    const [refreshError, setRefreshError] = useState(false);
    const requestIdRef = useRef(0);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterDecision, setFilterDecision] = useState('ALL');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Données dérivées
    const blockedCount = population.filter(p => p.decision === 'BLOQUE' || p.decision === 'BLOCKED').length;
    const includedCount = population.filter(p => p.decision === 'INCLUS' || p.decision === 'INCLUDED').length;
    const excludedCount = population.filter(p => p.decision === 'EXCLU' || p.decision === 'EXCLUDED').length;

    const filteredPopulation = useMemo(() => {
        return population.filter(membre => {
            if (filterDecision !== 'ALL') {
                const dec = membre.decision;
                const isMatch = (filterDecision === 'INCLUS' && (dec === 'INCLUS' || dec === 'INCLUDED'))
                    || (filterDecision === 'EXCLU' && (dec === 'EXCLU' || dec === 'EXCLUDED'))
                    || (filterDecision === 'BLOQUE' && (dec === 'BLOQUE' || dec === 'BLOCKED'))
                    || dec === filterDecision;
                if (!isMatch) return false;
            }
            if (!searchQuery.trim()) return true;
            const q = searchQuery.toLowerCase().trim();
            const empName = (membre.employeeName || membre.nomPrenom || '').toLowerCase();
            const mat = (membre.matricule || '').toLowerCase();
            const contrat = (membre.typeContrat || membre.contrat || '').toLowerCase();
            const site = (membre.nomSite || membre.site || '').toLowerCase();
            return empName.includes(q) || mat.includes(q) || contrat.includes(q) || site.includes(q);
        }).sort((a, b) => {
            const rank = { BLOQUE: 0, BLOCKED: 0, EXCLU: 1, EXCLUDED: 1, WARNED: 2, INCLUS: 3, INCLUDED: 3 };
            return (rank[a.decision] ?? 3) - (rank[b.decision] ?? 3);
        });
    }, [population, filterDecision, searchQuery]);
    const pagedPopulation = filteredPopulation.slice((currentPage - 1) * pageSize, currentPage * pageSize);

    // Effets
    const loadPopulation = useCallback(async ({ silent = false } = {}) => {
        if (!numericPeriodId) return;
        const requestId = ++requestIdRef.current;
        if (!silent) setLoading(true);
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { projection: 'population' });
            if (requestIdRef.current !== requestId) return;
            setPopulation(Array.isArray(cockpit?.population) ? cockpit.population : []);
            setRefreshError(false);
        } catch {
            if (requestIdRef.current === requestId) setRefreshError(true);
        } finally {
            if (requestIdRef.current === requestId) setLoading(false);
        }
    }, [numericPeriodId]);

    useEffect(() => {
        loadPopulation();
        return () => { requestIdRef.current += 1; };
    }, [loadPopulation]);

    useEffect(() => {
        const refreshIfVisible = () => {
            if (document.visibilityState === 'visible') loadPopulation({ silent: true });
        };
        const onVisibilityChange = () => refreshIfVisible();
        const timer = window.setInterval(refreshIfVisible, 60000);
        document.addEventListener('visibilitychange', onVisibilityChange);
        return () => {
            window.clearInterval(timer);
            document.removeEventListener('visibilitychange', onVisibilityChange);
        };
    }, [loadPopulation]);

    // Rendu
    return (
        <PayrollCampaignShell
            title="Étape 2 — Population de la campagne"
            subtitle="Vérification des salariés inclus et analyse des exclusions ou blocages."
            activeStep="POPULATION"
            context={context}
            periodes={periodes}
            loading={ctxLoading}
            prevStep={{
                label: 'Préparation',
                destination: `/paie/periodes/${numericPeriodId}/preparation`,
            }}
            nextStep={{
                label: 'Variables par salarié',
                destination: `/paie/periodes/${numericPeriodId}/variables`,
                disabled: loading || (refreshError && population.length === 0) || blockedCount > 0,
                disabledReason: blockedCount > 0 ? `${blockedCount} salarié(s) bloqué(s) doivent être résolus.` : loading ? 'Chargement de la population.' : refreshError ? 'Population indisponible.' : undefined,
            }}
        >
            <div className="space-y-4">
                <details className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-3 text-xs text-[var(--color-fg-muted)]">
                    <summary className="font-semibold text-[var(--color-fg)] cursor-pointer">Règles d’inclusion de la population</summary>
                    <p className="mt-2 leading-relaxed">
                        La période est évaluée du {context?.selectedPeriod?.startDate || 'début'} au {context?.selectedPeriod?.endDate || 'terme'}.
                        Un salarié actif est inclus si une seule affectation couvre cette période et si son contrat et son type d’intervenant sont résolus.
                        Sans affectation applicable, il est exclu. Plusieurs affectations, ou un contrat/type d’intervenant manquant, bloquent sa ligne.
                    </p>
                </details>

                {refreshError && (
                    <p role="status" className="text-xs text-[var(--color-warning-700)] bg-[var(--color-warning-50)] border border-[var(--color-warning-200)] px-3 py-2">
                        {population.length > 0 ? 'Mise à jour indisponible. Les dernières données chargées restent affichées.' : 'Population indisponible pour le moment.'}
                    </p>
                )}

                {/* Table unifiée Population */}
                <PayrollStepTable
                    title="Population de la campagne"
                    badgeCount={population.length}
                    searchValue={searchQuery}
                    onSearchChange={(q) => { setSearchQuery(q); setCurrentPage(1); }}
                    searchPlaceholder="Rechercher salarié…"
                    filterSlot={
                        <div className="flex items-center gap-1 text-xs flex-wrap" aria-label="Filtrer par décision">
                            {[
                                { id: 'ALL', label: `Tous (${population.length})` },
                                { id: 'INCLUS', label: `${includedCount} inclus`, icon: CheckCircle2, color: 'text-[var(--color-success-600)]' },
                                { id: 'EXCLU', label: `${excludedCount} exclu${excludedCount > 1 ? 's' : ''}`, icon: UserX },
                                { id: 'BLOQUE', label: `${blockedCount} bloqué${blockedCount > 1 ? 's' : ''}`, icon: AlertTriangle, color: 'text-[var(--color-danger-600)]' },
                            ].map(tab => {
                                const Icon = tab.icon;
                                const active = filterDecision === tab.id;
                                return (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        onClick={() => { setFilterDecision(tab.id); setCurrentPage(1); }}
                                        aria-pressed={active}
                                        className={`px-2.5 py-1 text-xs font-semibold inline-flex items-center gap-1.5 transition-colors cursor-pointer border ${
                                            active
                                                ? 'bg-[var(--color-surface)] text-[var(--color-fg)] border-[var(--color-border-subtle)] border-b-2 border-b-[var(--color-brand-500)] shadow-xs'
                                                : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-surface)]/60'
                                        }`}
                                    >
                                        {Icon && <Icon size={12} className={active && tab.color ? tab.color : 'text-[var(--color-fg-muted)]'} />}
                                        <span>{tab.label}</span>
                                    </button>
                                );
                            })}
                        </div>
                    }
                    columns={[
                        { key: 'salarie', label: 'Salarié', width: 'w-60' },
                        { key: 'contrat', label: 'Contrat et affectation' },
                        { key: 'decision', label: 'Décision', align: 'center', width: 'w-28' },
                        { key: 'motif', label: 'Motif' },
                    ]}
                    loading={loading}
                    emptyMessage={population.length === 0 && !refreshError ? 'Aucun salarié dans cette campagne.' : 'Aucun salarié ne correspond aux filtres.'}
                    pagination={population.length > 0 ? {
                        currentPage,
                        totalItems: filteredPopulation.length,
                        pageSize,
                        pageSizeOptions: [10, 25, 50],
                        onPageChange: setCurrentPage,
                        onPageSizeChange: (size) => { setPageSize(size); setCurrentPage(1); },
                    } : null}
                    ariaLabel="Population de la campagne"
                >
                    {pagedPopulation.map((p, idx) => {
                        const dec = p.decision || 'INCLUS';
                        const variant = DECISION_VARIANTS[dec] || 'default';
                        const label = DECISION_LABELS[dec] || dec;
                        return (
                            <Tr key={p.idEmploye || p.employeeId || idx}>
                                <Td>
                                    <PayrollEmployeeCell
                                        name={p.employeeName || p.nomPrenom}
                                        matricule={p.matricule}
                                        subtitle={p.statutEmploye}
                                    />
                                </Td>
                                <Td>
                                    <div className="text-xs font-medium text-[var(--color-fg)]">{p.typeContrat || 'Contrat non renseigné'}</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {[p.contractNumber, p.nomSite || 'Site non renseigné'].filter(Boolean).join(' · ')}
                                    </div>
                                </Td>
                                <Td className="text-center">
                                    <Badge variant={variant} size="xs">{label}</Badge>
                                </Td>
                                <Td className="text-[11px] text-[var(--color-fg-muted)] max-w-xs">
                                    {getPopulationReasonLabel(p.reasonCode || p.reason || p.motif)}
                                </Td>
                            </Tr>
                        );
                    })}
                </PayrollStepTable>
            </div>
        </PayrollCampaignShell>
    );
}
