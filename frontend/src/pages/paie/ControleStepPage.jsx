import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
    ShieldCheck, AlertTriangle, Download, Eye, ArrowUpRight, ArrowDownRight,
    Search, CheckCircle2, FileText
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PayrollCampaignHeader from '@/components/paie/PayrollCampaignHeader';
import PayrollNavigationFooter from '@/components/paie/PayrollNavigationFooter';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import StatCard from '@/components/ui/data-display/StatCard';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { usePayrollCampaign } from '@/hooks/paie/usePayrollCampaign';
import paieService from '@/api/paie/paieService';

const BulletinPaieDrawer = lazy(() => import('@/components/paie/BulletinPaieDrawer'));

/**
 * ControleStepPage — Étape 5 : Contrôle des bulletins.
 *
 * Affiche la liste des bulletins calculés avec :
 * - Indicateurs d'anomalies et d'écarts M-1
 * - Filtres par statut, alerte, site
 * - Ouverture du détail d'un bulletin en drawer latéral
 * - Export CSV de l'état de contrôle
 */
export default function ControleStepPage() {
    const { periodId } = useParams();
    const navigate = useNavigate();
    const numericPeriodId = Number(periodId) || null;
    const { periodes, context, loading: ctxLoading, refresh } = usePayrollCampaign(numericPeriodId);

    const [cockpit, setCockpit] = useState(null);
    const [loading, setLoading] = useState(false);
    const [searchQuery, setSearchQuery] = useState('');
    const [filterAlert, setFilterAlert] = useState('ALL');
    const [feedback, setFeedback] = useState(null);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);

    const chargerBulletins = useCallback(async () => {
        if (!numericPeriodId) return;
        setLoading(true);
        setFeedback(null);
        try {
            const data = await paieService.getCockpitPeriode(numericPeriodId, {
                q: searchQuery || undefined,
                filter: filterAlert === 'ALL' ? undefined : filterAlert,
                size: 100,
            });
            setCockpit(data);
        } catch (err) {
            setFeedback({ type: 'error', text: err?.response?.data?.message || 'Erreur de chargement des bulletins.' });
        } finally {
            setLoading(false);
        }
    }, [numericPeriodId, searchQuery, filterAlert]);

    useEffect(() => { chargerBulletins(); }, [chargerBulletins]);

    const bulletins = Array.isArray(cockpit?.bulletins) ? cockpit.bulletins : [];
    const nbAlertes = bulletins.filter(b => b.aAlerte).length;
    const nbTotal = bulletins.length;
    const periode = context?.selectedPeriod || context?.currentPeriod;

    const formatAr = (v) => v != null
        ? Number(v).toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 0 }) + ' Ar'
        : '—';

    const ecartClass = (pct) => {
        if (pct == null) return 'text-[var(--color-fg-muted)]';
        if (Math.abs(pct) > 10) return 'text-[var(--color-danger-500)] font-bold';
        if (Math.abs(pct) > 3) return 'text-[var(--color-warning-600)]';
        return 'text-[var(--color-fg-muted)]';
    };

    const handleExportCsv = () => {
        if (!bulletins.length) return;
        const headers = ['Matricule', 'Salarie', 'Poste', 'Salaire_Base', 'Brut', 'Net', 'IRSA', 'Ecart_M1_Pct', 'Statut'];
        const rows = bulletins.map(b => [
            b.matricule, `"${b.nomPrenom || b.employeeName || ''}"`, `"${b.intitulePoste || b.nomSite || ''}"`,
            b.salaireBase, b.totalGainsBruts, b.netAPayer, b.irsaNet,
            b.ecartM1Pct ?? '', b.statut
        ]);
        const csv = [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `controle_paie_${periode?.code || numericPeriodId}.csv`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="flex-1 flex flex-col min-h-full">
            <SEO
                title="Contrôle des bulletins | Paie GALAXAM SIRH"
                description="Contrôle des bulletins de paie calculés : vérification des anomalies et écarts."
            />
            <PayrollCampaignHeader
                context={context}
                periodes={periodes}
                onSelectPeriod={(newId) => navigate(`/paie/periodes/${newId}/controle`)}
                activeStep="CONTROL"
                loading={ctxLoading}
                onRefresh={refresh}
            />

            <div className="flex-1 max-w-7xl mx-auto px-4 py-6 space-y-5 w-full">

                {/* En-tête */}
                <div className="flex items-start justify-between gap-4 flex-wrap">
                    <div className="flex items-start gap-3">
                        <div className="w-10 h-10 bg-[var(--color-brand-600)] text-white flex items-center justify-center shrink-0 shadow-sm">
                            <ShieldCheck size={20} strokeWidth={2} />
                        </div>
                        <div>
                            <h1 className="text-base font-bold text-[var(--color-fg)] tracking-tight">
                                Étape 5 — Contrôle des bulletins
                            </h1>
                            <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                Vérifiez les anomalies et les écarts significatifs avant validation définitive.
                            </p>
                        </div>
                    </div>
                    <Button
                        variant="outline"
                        size="sm"
                        iconLeft={<Download size={13} />}
                        onClick={handleExportCsv}
                        disabled={!bulletins.length}
                        id="btn-export-controle-csv"
                    >
                        Exporter CSV
                    </Button>
                </div>

                {/* KPIs via StatCard */}
                {!loading && nbTotal > 0 && (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        <StatCard
                            title="Total Bulletins"
                            value={nbTotal}
                            subtext="Collaborateurs calculés"
                            icon={<FileText size={18} />}
                            color="brand"
                        />
                        <StatCard
                            title="Alertes Détectées"
                            value={nbAlertes}
                            subtext={nbAlertes > 0 ? 'Écarts > 20% ou net négatif' : 'Aucune alerte critique'}
                            icon={<AlertTriangle size={18} />}
                            color={nbAlertes > 0 ? 'warning' : 'success'}
                        />
                        <StatCard
                            title="Statut Calculé"
                            value={bulletins.filter(b => b.statut === 'CALCULE').length}
                            subtext="En attente de validation DAF"
                            icon={<ShieldCheck size={18} />}
                            color="info"
                        />
                        <StatCard
                            title="Statut Validé"
                            value={bulletins.filter(b => b.statut === 'VALIDE').length}
                            subtext="Prêts pour clôture"
                            icon={<CheckCircle2 size={18} />}
                            color="success"
                        />
                    </div>
                )}

                {/* Feedback */}
                {feedback && <Alert type={feedback.type} message={feedback.text} onClose={() => setFeedback(null)} />}

                {/* Barre de filtres via composants design system */}
                <div className="flex flex-wrap items-center gap-3">
                    <div className="flex-1 min-w-[240px]">
                        <Input
                            id="controle-search"
                            type="search"
                            placeholder="Rechercher un salarié, matricule…"
                            value={searchQuery}
                            onChange={e => setSearchQuery(e.target.value)}
                            iconLeft={<Search size={14} />}
                            aria-label="Rechercher dans les bulletins"
                        />
                    </div>
                    <div className="w-56">
                        <Select
                            value={filterAlert}
                            onChange={val => setFilterAlert(val)}
                            options={[
                                { value: 'ALL', label: 'Tous les bulletins' },
                                { value: 'ALERTS', label: 'Alertes uniquement' },
                                { value: 'CALCULE', label: 'Calculés' },
                                { value: 'VALIDE', label: 'Validés' },
                                { value: 'STC', label: 'STC inclus' },
                            ]}
                            aria-label="Filtrer par statut"
                        />
                    </div>
                </div>

                {/* Table bulletins */}
                <div>
                    {loading ? (
                        <div className="p-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                            <SkeletonTable rows={8} cols={7} />
                        </div>
                    ) : bulletins.length === 0 ? (
                        <div className="py-12 text-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                            <ShieldCheck size={32} className="text-[var(--color-fg-subtle)] mx-auto mb-3 opacity-40" />
                            <p className="text-sm text-[var(--color-fg-muted)]">Aucun bulletin calculé.</p>
                            <p className="text-xs text-[var(--color-fg-subtle)] mt-1">Lancez le calcul de la paie pour voir les bulletins.</p>
                        </div>
                    ) : (
                        <Table aria-label="Tableau de contrôle des bulletins">
                            <Thead>
                                <Tr>
                                    <Th>Matricule</Th>
                                    <Th>Salarié</Th>
                                    <Th>Poste</Th>
                                    <Th className="text-right">Salaire Base</Th>
                                    <Th className="text-right">Brut</Th>
                                    <Th className="text-right">Net à Payer</Th>
                                    <Th className="text-right">Écart M-1</Th>
                                    <Th>Statut</Th>
                                    <Th></Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {bulletins.map((b) => {
                                    const nomAffiche = b.nomPrenom || b.employeeName || (b.matricule ? `Matricule ${b.matricule}` : 'Collaborateur');
                                    return (
                                        <Tr
                                            key={b.idBulletin}
                                            onClick={() => setSelectedBulletinId(b.idBulletin)}
                                            className={`cursor-pointer ${
                                                b.aAlerte ? 'bg-[var(--color-warning-500)]/5' : ''
                                            } ${selectedBulletinId === b.idBulletin ? 'ring-1 ring-inset ring-[var(--color-brand-500)]/30' : ''}`}
                                            aria-selected={selectedBulletinId === b.idBulletin}
                                        >
                                            <Td className="font-mono text-[var(--color-fg-muted)]">{b.matricule || '—'}</Td>
                                            <Td className="font-semibold text-[var(--color-fg)] whitespace-nowrap">
                                                {b.aAlerte && <AlertTriangle size={11} className="inline mr-1.5 text-[var(--color-warning-500)]" aria-label="Alerte" />}
                                                {nomAffiche}
                                            </Td>
                                            <Td className="text-[var(--color-fg-muted)] whitespace-nowrap">{b.intitulePoste || '—'}</Td>
                                            <Td className="font-mono text-right text-[var(--color-fg-muted)]">{formatAr(b.salaireBase)}</Td>
                                            <Td className="font-mono text-right text-[var(--color-fg)]">{formatAr(b.totalGainsBruts)}</Td>
                                            <Td className="font-mono text-right font-bold text-[var(--color-fg)]">{formatAr(b.netAPayer)}</Td>
                                            <Td className={`font-mono text-right ${ecartClass(b.ecartM1Pct)}`}>
                                                {b.ecartM1Pct != null ? (
                                                    <span className="inline-flex items-center gap-0.5">
                                                        {b.ecartM1Pct > 0 ? <ArrowUpRight size={10} /> : b.ecartM1Pct < 0 ? <ArrowDownRight size={10} /> : null}
                                                        {Math.abs(b.ecartM1Pct).toFixed(1)}%
                                                    </span>
                                                ) : '—'}
                                            </Td>
                                            <Td>
                                                <Badge
                                                    variant={b.statut === 'VALIDE' ? 'success' : b.statut === 'CALCULE' ? 'info' : 'default'}
                                                    size="xs"
                                                    dot
                                                >
                                                    {b.typeBulletin === 'STC' ? 'STC' : b.statut || '—'}
                                                </Badge>
                                            </Td>
                                            <Td>
                                                <button
                                                    type="button"
                                                    onClick={(e) => { e.stopPropagation(); setSelectedBulletinId(b.idBulletin); }}
                                                    className="p-1 hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] rounded-none transition-colors"
                                                    aria-label={`Voir le bulletin de ${nomAffiche}`}
                                                    id={`btn-voir-bulletin-${b.idBulletin}`}
                                                >
                                                    <Eye size={13} />
                                                </button>
                                            </Td>
                                        </Tr>
                                    );
                                })}
                            </Tbody>
                        </Table>
                    )}
                </div>

                {/* Message d'état alertes */}
                {nbAlertes === 0 && nbTotal > 0 && (
                    <div className="flex items-center gap-2 p-3 bg-[var(--color-success-500)]/5 border border-[var(--color-success-500)]/20">
                        <CheckCircle2 size={15} className="text-[var(--color-success-500)] shrink-0" />
                        <p className="text-xs font-semibold text-[var(--color-fg)]">
                            Aucune alerte détectée. Les bulletins sont prêts pour validation.
                        </p>
                    </div>
                )}
            </div>

            <PayrollNavigationFooter
                prevStep={{
                    label: 'Calcul de la paie',
                    destination: `/paie/periodes/${numericPeriodId}/calcul`,
                }}
                nextStep={{
                    label: 'Validation & Clôture',
                    destination: `/paie/periodes/${numericPeriodId}/validation`,
                    disabled: nbTotal === 0,
                    disabledReason: 'Aucun bulletin disponible pour validation.',
                }}
            />

            {/* Drawer Bulletin */}
            <Suspense fallback={null}>
                <BulletinPaieDrawer
                    isOpen={selectedBulletinId !== null}
                    idBulletin={selectedBulletinId}
                    onClose={() => setSelectedBulletinId(null)}
                />
            </Suspense>
        </div>
    );
}
