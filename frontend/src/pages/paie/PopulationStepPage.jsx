import { useState, useEffect, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Users, AlertTriangle, UserX, CheckCircle2, ShieldAlert, Search } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';

/**
 * PopulationStepPage — Étape 2 : Population de paie.
 *
 * Affiche la liste des salariés inclus dans la campagne de paie active,
 * avec leur statut de décision (INCLUS, EXCLU, BLOQUÉ).
 */
export default function PopulationStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [population, setPopulation] = useState([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);

    const loadPopulation = useCallback(async () => {
        if (!numericPeriodId) return;
        setLoading(true);
        setError(null);
        try {
            const cockpit = await paieService.getCockpitPeriode(numericPeriodId, { light: true });
            setPopulation(Array.isArray(cockpit?.population) ? cockpit.population : []);
        } catch (err) {
            setError(err?.response?.data?.message || 'Erreur lors du chargement de la population.');
        } finally {
            setLoading(false);
        }
    }, [numericPeriodId]);

    useEffect(() => { loadPopulation(); }, [loadPopulation]);

    const decisionVariant = { INCLUS: 'success', EXCLU: 'default', BLOQUE: 'danger', WARNED: 'warning' };
    const decisionLabel = { INCLUS: 'Inclus', EXCLU: 'Exclu', BLOQUE: 'Bloqué', WARNED: 'Avertissement' };

    const translateReason = (code) => {
        if (!code) return '';
        const map = {
            CAREER_AMBIGUOUS: 'Configuration de carrière ambiguë ou affectations multiples',
            NO_ACTIVE_CONTRACT: 'Aucun contrat de travail actif pour cette période',
            CONTRACT_ENDED: 'Contrat clôturé avant le début de la période',
            INACTIVE_EMPLOYEE: 'Salarié inactif dans le référentiel',
        };
        return map[code] || code;
    };

    const [searchQuery, setSearchQuery] = useState('');
    const [filterDecision, setFilterDecision] = useState('ALL');

    const blockedCount = population.filter(p => p.decision === 'BLOQUE').length;
    const includedCount = population.filter(p => p.decision === 'INCLUS').length;
    const excludedCount = Math.max(0, population.length - includedCount - blockedCount);

    const filteredPopulation = useMemo(() => {
        return population.filter(membre => {
            if (filterDecision !== 'ALL' && membre.decision !== filterDecision) {
                return false;
            }
            if (!searchQuery.trim()) return true;
            const q = searchQuery.toLowerCase().trim();
            const empName = (membre.employeeName || membre.nomPrenom || '').toLowerCase();
            const mat = (membre.matricule || '').toLowerCase();
            const poste = (membre.intitulePoste || '').toLowerCase();
            const site = (membre.nomSite || '').toLowerCase();
            return empName.includes(q) || mat.includes(q) || poste.includes(q) || site.includes(q);
        });
    }, [population, filterDecision, searchQuery]);

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Population de paie | Paie GALAXAM SIRH"
                description="Liste des salariés inclus dans la campagne de paie active."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/population`)}
                activeStep="POPULATION"
                loading={ctxLoading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-5xl mx-auto px-4 py-6 space-y-6 w-full">

                {/* En-tête */}
                <div className="flex items-start gap-3">
                    <div className="w-10 h-10 bg-gradient-to-br from-[var(--color-brand-400)] to-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-xs">
                        <Users size={20} strokeWidth={2} />
                    </div>
                    <div>
                        <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                            Étape 2 — Population de paie
                        </h1>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                            Salariés inclus dans la campagne selon les contrats actifs et affectations de carrière.
                        </p>
                    </div>
                </div>

                {/* KPIs via StatCard */}
                {!loading && population.length > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                        <StatCard
                            title="Salariés Inclus"
                            value={includedCount}
                            subtext="Éligibles au calcul de paie"
                            icon={<CheckCircle2 size={18} />}
                            color="success"
                        />
                        <StatCard
                            title="Salariés Exclus"
                            value={excludedCount}
                            subtext="Hors champ de la campagne"
                            icon={<Users size={18} />}
                            color="neutral"
                        />
                        <StatCard
                            title="Salariés Bloqués"
                            value={blockedCount}
                            subtext={blockedCount > 0 ? 'Exigent une régularisation de carrière' : 'Aucun blocage'}
                            icon={<ShieldAlert size={18} />}
                            color={blockedCount > 0 ? 'danger' : 'success'}
                        />
                    </div>
                )}

                {/* Alerte population bloquée */}
                {blockedCount > 0 && (
                    <div className="flex items-center gap-2.5 p-3.5 bg-[var(--color-danger-500)]/8 border border-[var(--color-danger-500)]/30">
                        <AlertTriangle size={16} className="text-[var(--color-danger-500)] shrink-0" />
                        <p className="text-xs text-[var(--color-fg)]">
                            <span className="font-bold">{blockedCount} salarié(s) bloqué(s)</span> ne peuvent pas être calculés. 
                            Vérifiez leur contrat et leur affectation dans le dossier collaborateur.
                        </p>
                    </div>
                )}

                {/* Barre de filtres et recherche */}
                <div className="flex flex-wrap items-center gap-3">
                    <div className="flex-1 min-w-[240px]">
                        <Input
                            id="population-search"
                            type="search"
                            placeholder="Rechercher par salarié, matricule, poste, site…"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            iconLeft={<Search size={14} />}
                            aria-label="Rechercher dans la population"
                        />
                    </div>
                    <div className="w-56">
                        <Select
                            value={filterDecision}
                            onChange={val => setFilterDecision(val)}
                            options={[
                                { value: 'ALL', label: `Tous les salariés (${population.length})` },
                                { value: 'INCLUS', label: `Inclus (${includedCount})` },
                                { value: 'EXCLU', label: `Exclus (${excludedCount})` },
                                { value: 'BLOQUE', label: `Bloqués (${blockedCount})` },
                            ]}
                            aria-label="Filtrer par décision"
                        />
                    </div>
                </div>

                {/* Table population */}
                <div>
                    {loading ? (
                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                            <SkeletonTable rows={5} cols={4} />
                        </div>
                    ) : error ? (
                        <div className="p-6 text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                            <UserX size={24} className="text-[var(--color-danger-500)] mx-auto mb-2" />
                            <p className="text-xs text-[var(--color-danger-500)]">{error}</p>
                        </div>
                    ) : filteredPopulation.length === 0 ? (
                        <div className="p-8 text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                            <Users size={28} className="text-[var(--color-fg-subtle)] mx-auto mb-2 opacity-40" />
                            <p className="text-sm text-[var(--color-fg-muted)]">
                                {population.length === 0 ? 'Aucun salarié résolu pour cette période.' : 'Aucun résultat pour cette recherche ou ce filtre.'}
                            </p>
                            <p className="text-xs text-[var(--color-fg-subtle)] mt-1">
                                {population.length === 0 
                                    ? 'La population est calculée automatiquement selon les contrats actifs.'
                                    : 'Modifiez vos critères de recherche pour réinitialiser la vue.'}
                            </p>
                        </div>
                    ) : (
                        <Table aria-label="Population de paie">
                            <Thead>
                                <Tr>
                                    <Th>Matricule</Th>
                                    <Th>Salarié</Th>
                                    <Th>Contrat & Régime</Th>
                                    <Th className="text-right">Décision</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {filteredPopulation.map((membre, i) => {
                                    const empId = membre.employeeId || membre.idEmploye;
                                    const empName = membre.employeeName || membre.nomPrenom || (empId ? `Employé #${empId}` : `Salarié #${i + 1}`);
                                    const contractInfo = [
                                        membre.contractNumber ? `Contrat ${membre.contractNumber}` : null,
                                        membre.regime ? `Régime ${membre.regime}` : null,
                                        [membre.intitulePoste, membre.nomSite].filter(Boolean).join(' / ') || null
                                    ].filter(Boolean).join(' • ') || 'Standard';

                                    const reason = translateReason(membre.reasonCode || membre.raisonDecision);

                                    return (
                                        <Tr key={empId || i}>
                                            <Td className="font-mono text-[var(--color-fg-muted)] font-semibold">
                                                {membre.matricule || '—'}
                                            </Td>
                                            <Td className="font-semibold text-[var(--color-fg)]">
                                                {empName}
                                            </Td>
                                            <Td className="text-[var(--color-fg-muted)]">
                                                {contractInfo}
                                            </Td>
                                            <Td className="text-right">
                                                <Badge variant={decisionVariant[membre.decision] || 'default'} size="xs" dot>
                                                    {decisionLabel[membre.decision] || membre.decision || '—'}
                                                </Badge>
                                                {membre.decision === 'BLOQUE' && reason && (
                                                    <p className="text-[10px] text-[var(--color-danger-500)] mt-0.5">
                                                        {reason}
                                                    </p>
                                                )}
                                            </Td>
                                        </Tr>
                                    );
                                })}
                            </Tbody>
                        </Table>
                    )}
                </div>
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Préparation',
                    destination: `/paie/periodes/${numericPeriodId}/preparation`,
                }}
                nextStep={{
                    label: 'Variables de paie',
                    destination: `/paie/periodes/${numericPeriodId}/variables`,
                }}
            />
        </div>
    );
}
