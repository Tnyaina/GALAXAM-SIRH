import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { Search, CheckCircle2, UserX, AlertTriangle } from 'lucide-react';
import PayrollCampaignShell from '@/features/payroll/components/workflow/PayrollCampaignShell';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';

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
    if (!code) return 'Éligible';
    const labels = {
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
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterDecision, setFilterDecision] = useState('ALL');

    // Données dérivées
    const blockedCount = population.filter(p => p.decision === 'BLOQUE' || p.decision === 'BLOCKED').length;
    const includedCount = population.filter(p => p.decision === 'INCLUS' || p.decision === 'INCLUDED').length;
    const excludedCount = Math.max(0, population.length - includedCount - blockedCount);

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
        });
    }, [population, filterDecision, searchQuery]);

    // Effets
    const loadPopulation = useCallback(async () => {
        if (!numericPeriodId) return;
        setLoading(true);
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { light: true });
            setPopulation(Array.isArray(cockpit?.population) ? cockpit.population : []);
        } catch {
            setPopulation([]);
        } finally {
            setLoading(false);
        }
    }, [numericPeriodId]);

    useEffect(() => { loadPopulation(); }, [loadPopulation]);

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
                label: 'Variables exceptionnelles',
                destination: `/paie/periodes/${numericPeriodId}/variables`,
                disabled: blockedCount > 0,
                disabledReason: blockedCount > 0 ? `${blockedCount} salarié(s) bloqué(s) doivent être résolus.` : undefined,
            }}
        >
            <div className="space-y-4">
                {/* Barre de synthèse Population (V1 Full V3) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                    <div className="flex items-center gap-4 text-xs flex-wrap">
                        <span className="flex items-center gap-1.5 font-bold text-[var(--color-success-700)] dark:text-[var(--color-success-400)]">
                            <CheckCircle2 size={16} />
                            {includedCount} inclus
                        </span>
                        <span className="flex items-center gap-1.5 font-medium text-[var(--color-fg-muted)]">
                            <UserX size={16} />
                            {excludedCount} exclu{excludedCount > 1 ? 's' : ''}
                        </span>
                        <span className={`flex items-center gap-1.5 font-bold ${blockedCount > 0 ? 'text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-subtle)]'}`}>
                            <AlertTriangle size={16} />
                            {blockedCount} bloqué{blockedCount > 1 ? 's' : ''}
                        </span>
                    </div>

                    {/* Filtres & Recherche */}
                    <div className="flex items-center gap-2 flex-wrap sm:flex-nowrap">
                        <div className="w-48">
                            <Input
                                placeholder="Rechercher salarié..."
                                value={searchQuery}
                                onChange={(e) => setSearchQuery(e.target.value)}
                                iconLeft={<Search size={14} />}
                                size="sm"
                            />
                        </div>
                        <select
                            value={filterDecision}
                            onChange={(e) => setFilterDecision(e.target.value)}
                            className="text-xs bg-[var(--color-bg)] border border-[var(--color-border-subtle)] px-2.5 py-1.5 text-[var(--color-fg)] focus:outline-none"
                        >
                            <option value="ALL">Tous ({population.length})</option>
                            <option value="INCLUS">Inclus ({includedCount})</option>
                            <option value="EXCLU">Exclus ({excludedCount})</option>
                            {blockedCount > 0 && <option value="BLOQUE">Bloqués ({blockedCount})</option>}
                        </select>
                    </div>
                </div>

                {/* Table compacte V1 Full V3 : Salarié, Matricule, Contrat, Site/filiale, Situation, Décision, Raison */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    {loading ? (
                        <div className="p-4"><SkeletonTable rows={5} cols={7} /></div>
                    ) : filteredPopulation.length === 0 ? (
                        <div className="p-8 text-center text-xs text-[var(--color-fg-muted)]">
                            Aucun salarié ne correspond aux critères de filtre.
                        </div>
                    ) : (
                        <Table density="compact" bordered={false}>
                            <Thead>
                                <Tr>
                                    <Th>Salarié</Th>
                                    <Th>Matricule</Th>
                                    <Th>Contrat</Th>
                                    <Th>Site / Filiale</Th>
                                    <Th>Situation</Th>
                                    <Th>Décision</Th>
                                    <Th>Raison / Détail</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {filteredPopulation.map((p, idx) => {
                                    const dec = p.decision || 'INCLUS';
                                    const variant = DECISION_VARIANTS[dec] || 'default';
                                    const label = DECISION_LABELS[dec] || dec;

                                    return (
                                        <Tr key={p.idEmploye || idx}>
                                            <Td className="font-semibold text-[var(--color-fg)]">
                                                {p.employeeName || p.nomPrenom || '–'}
                                            </Td>
                                            <Td className="font-mono text-xs text-[var(--color-fg-subtle)]">
                                                {p.matricule || '–'}
                                            </Td>
                                            <Td className="text-xs">
                                                <Badge variant="secondary" size="sm">
                                                    {p.typeContrat || p.contrat || 'CDI'}
                                                </Badge>
                                            </Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)]">
                                                {p.nomSite || p.site || '–'}
                                            </Td>
                                            <Td className="text-xs">
                                                {p.statutEmploye || p.situation || 'Actif'}
                                            </Td>
                                            <Td>
                                                <Badge variant={variant} size="sm">
                                                    {label}
                                                </Badge>
                                            </Td>
                                            <Td className="text-xs text-[var(--color-fg-muted)] max-w-xs truncate" title={p.motif || p.reason}>
                                                {getPopulationReasonLabel(p.reason || p.motif)}
                                            </Td>
                                        </Tr>
                                    );
                                })}
                            </Tbody>
                        </Table>
                    )}
                </div>
            </div>
        </PayrollCampaignShell>
    );
}
