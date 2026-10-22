import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Archive, ArrowRight, CalendarDays, Download, FileSpreadsheet, FileText, FolderOpen } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Tabs from '@/components/ui/navigation/Tabs';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';
import { periodState, isPayrollValidated } from '@/features/payroll/utils/documentPeriodState';

const TABS = [
    { id: 'mensuel', label: 'Mensuel' },
    { id: 'trimestriel', label: 'Trimestriel' },
    { id: 'annuel', label: 'Annuel' },
];
const MONTH_NAMES = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];

const monthKey = (period) => {
    const code = period?.code?.match(/^(\d{4})-(0[1-9]|1[0-2])$/)?.[0];
    return code || period?.dateFin?.slice(0, 7) || null;
};
const quarterKey = (month) => month ? `${month.slice(0, 4)}-T${Math.ceil(Number(month.slice(5, 7)) / 3)}` : null;
const monthStatus = (period) => !period
    ? { label: 'Période absente', variant: 'secondary' }
    : isPayrollValidated(period)
        ? { label: periodState(period) === 'CLOTUREE' ? 'Clôturée' : 'Paie validée', variant: 'success' }
        : { label: period.workflowStatuses?.paie?.label || 'Paie non validée', variant: 'warning' };

/** Catalogue des documents de paie. Les exports futurs sont présentés sans action fictive. */
export default function DeclarationsSortiesPage() {
    const navigate = useNavigate();
    const [downloading, setDownloading] = useState(null);
    const [downloadError, setDownloadError] = useState('');
    const [catalog, setCatalog] = useState(null);
    const [catalogError, setCatalogError] = useState('');
    const [params, setParams] = useSearchParams();
    const requestedPeriodId = Number(params.get('periodId')) || null;
    const { periodes, context, selectedPeriodeId } = usePayrollCampaign(requestedPeriodId);
    const activeTab = TABS.some(tab => tab.id === params.get('vue')) ? params.get('vue') : 'mensuel';
    const period = context?.selectedPeriod || context?.currentPeriod;
    const periodId = selectedPeriodeId || period?.id || requestedPeriodId;
    const periodCode = period?.code || periodes.find(p => Number(p.idPeriodePaie || p.id) === Number(periodId))?.code || 'Période à sélectionner';
    const today = new Date();
    const anchorMonth = monthKey(period) || monthKey(periodes[0]) || `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}`;
    const availableYears = [...new Set([anchorMonth.slice(0, 4), ...periodes.map(p => monthKey(p)?.slice(0, 4))].filter(Boolean))]
        .sort((a, b) => b.localeCompare(a));
    const quarterOptions = availableYears.flatMap(year => [4, 3, 2, 1].map(quarter => ({
        value: `${year}-T${quarter}`,
        label: `T${quarter} ${year}`,
        sublabel: `${MONTH_NAMES[(quarter - 1) * 3]} – ${MONTH_NAMES[quarter * 3 - 1]}`,
    })));
    const yearOptions = availableYears.map(year => ({ value: year, label: year }));
    const selectedQuarter = quarterOptions.some(option => option.value === params.get('trimestre'))
        ? params.get('trimestre') : quarterKey(anchorMonth);
    const selectedYear = yearOptions.some(option => option.value === params.get('annee'))
        ? params.get('annee') : anchorMonth.slice(0, 4);
    const coveredMonths = activeTab === 'trimestriel'
        ? Array.from({ length: 3 }, (_, index) => `${selectedQuarter.slice(0, 4)}-${String((Number(selectedQuarter.slice(-1)) - 1) * 3 + index + 1).padStart(2, '0')}`)
        : activeTab === 'annuel'
            ? Array.from({ length: 12 }, (_, index) => `${selectedYear}-${String(index + 1).padStart(2, '0')}`)
            : [];
    const periodsByMonth = new Map(periodes.map(p => [monthKey(p), p]).filter(([key]) => key));
    useEffect(() => {
        let cancelled = false;
        setCatalog(null);
        setCatalogError('');
        const options = activeTab === 'mensuel' ? { periodId }
            : activeTab === 'trimestriel' ? { year: Number(selectedQuarter.slice(0, 4)), quarter: Number(selectedQuarter.slice(-1)) }
                : { year: Number(selectedYear) };
        paieService.getDocumentCatalog(activeTab, options)
            .then(result => { if (!cancelled) setCatalog(result); })
            .catch(error => { if (!cancelled) setCatalogError(getErrorMessage(error, 'Catalogue indisponible.')); });
        return () => { cancelled = true; };
    }, [activeTab, periodId, selectedQuarter, selectedYear]);
    const changeParam = (key, value) => {
        const next = new URLSearchParams(params);
        if (value) next.set(key, value); else next.delete(key);
        setParams(next);
    };
    const list = catalog?.documents || [];
    const canExport = activeTab === 'mensuel' && list.some(item => item.available);
    const availableCount = list.filter(item => item.available).length;
    const downloadExcel = async (type) => {
        if (!periodId || !canExport) return;
        setDownloading(type);
        setDownloadError('');
        try {
            const { blob, filename } = type === 'irsa'
                ? await paieService.getIrsaExcel(periodId)
                : type === 'social'
                    ? await paieService.getEtatSocialMensuel(periodId)
                    : await paieService.getGrandLivre(periodId);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) {
            setDownloadError(getErrorMessage(error, `Impossible de générer ${type === 'irsa' ? "l’état IRSA" : type === 'social' ? "l’état social" : 'le grand livre'}.`));
        } finally {
            setDownloading(null);
        }
    };
    const downloadQuarterly = async (type) => {
        if (!list.find(item => item.id === type)?.available) return;
        setDownloading(type); setDownloadError('');
        try {
            const { blob, filename } = await paieService.getQuarterlyExport(Number(selectedQuarter.slice(0, 4)), Number(selectedQuarter.slice(-1)), type);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url; link.download = filename; link.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) {
            setDownloadError(getErrorMessage(error, `Impossible de générer le fichier ${type.toUpperCase()}.`));
        } finally { setDownloading(null); }
    };
    const downloadAnnual = async () => {
        if (!list.find(item => item.id === 'recap-paie')?.available) return;
        setDownloading('recap-paie'); setDownloadError('');
        try {
            const { blob, filename } = await paieService.getAnnualPayrollRecap(Number(selectedYear));
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url; link.download = filename; link.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (error) {
            setDownloadError(getErrorMessage(error, 'Impossible de générer le récapitulatif annuel.'));
        } finally { setDownloading(null); }
    };

    return (
        <div className="max-w-7xl 2xl:max-w-[1600px] mx-auto w-full space-y-5 pb-8">
            <SEO title="États de paie | GALAXAM GRH" description="États Excel mensuels, trimestriels et annuels pour la gestion interne." />
            <PageHeader title="États de paie" subtitle="Consultez les bulletins et téléchargez les états Excel utiles au contrôle interne."
                icon={FolderOpen} backLink="/paie" backLabel="Centre de paie" />
            {downloadError && <Alert type="error" message={downloadError} onClose={() => setDownloadError('')} />}
            {catalogError && <Alert type="error" message={catalogError} />}

            <Card shadow="none">
                <CardBody className="space-y-4">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                        <div className="flex items-center gap-2"><CalendarDays size={17} className="text-[var(--color-brand-600)]" /><span className="text-sm font-semibold">{activeTab === 'mensuel' ? 'Période de paie' : activeTab === 'trimestriel' ? 'Trimestre' : 'Année'}</span></div>
                        <SearchSelect
                            placeholder={activeTab === 'mensuel' ? 'Rechercher une période…' : activeTab === 'trimestriel' ? 'Choisir un trimestre…' : 'Choisir une année…'}
                            value={activeTab === 'mensuel' ? periodId ? String(periodId) : '' : activeTab === 'trimestriel' ? selectedQuarter : selectedYear}
                            onChange={(val) => changeParam(activeTab === 'mensuel' ? 'periodId' : activeTab === 'trimestriel' ? 'trimestre' : 'annee', val)}
                            className="w-full sm:w-80"
                            options={activeTab === 'mensuel' ? periodes.map(p => {
                                const id = p.idPeriodePaie || p.id;
                                return {
                                    value: String(id),
                                    label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(id),
                                    sublabel: p.workflowStatuses?.paie?.label || 'Statut de paie indisponible',
                                };
                            }) : activeTab === 'trimestriel' ? quarterOptions : yearOptions}
                        />
                    </div>
                    <Tabs tabs={TABS} activeTab={activeTab} onChange={(id) => changeParam('vue', id)} />
                    <div className="flex flex-wrap items-center gap-2 border-t border-[var(--color-border-subtle)] pt-3 text-xs text-[var(--color-fg-muted)]" aria-live="polite">
                        {activeTab === 'mensuel' && <Badge variant={isPayrollValidated(period) ? 'success' : 'warning'} size="sm">{monthStatus(period).label}</Badge>}
                        <strong className="text-[var(--color-fg)]">{availableCount} document{availableCount > 1 ? 's' : ''} disponible{availableCount > 1 ? 's' : ''}</strong>
                        <span>· {list.length - availableCount} à débloquer ou vérifier</span>
                    </div>
                </CardBody>
            </Card>

            {activeTab !== 'mensuel' && <Card shadow="none">
                <CardHeader><CardTitle size="sm">{activeTab === 'trimestriel' ? `Couverture de ${selectedQuarter.slice(5)} ${selectedQuarter.slice(0, 4)}` : `Couverture de ${selectedYear}`} · {catalog?.coveredMonths ?? 0}/{catalog?.totalMonths ?? coveredMonths.length} périodes validées</CardTitle></CardHeader>
                <CardBody>
                    <div className={`grid gap-1 ${activeTab === 'trimestriel' ? 'grid-cols-3' : 'grid-cols-3 sm:grid-cols-6 lg:grid-cols-12'}`}>
                        {coveredMonths.map(key => {
                            const item = periodsByMonth.get(key);
                            const state = monthStatus(item);
                            return <div key={key} className={`min-w-0 border-t-2 px-2 py-2.5 bg-[var(--color-bg-subtle)]/60 ${state.variant === 'success' ? 'border-[var(--color-success-500)]' : 'border-[var(--color-warning-500)]'}`}>
                                <span className="block text-xs font-semibold truncate" title={`${MONTH_NAMES[Number(key.slice(5)) - 1]} ${key.slice(0, 4)}`}>{activeTab === 'trimestriel' ? MONTH_NAMES[Number(key.slice(5)) - 1] : MONTH_NAMES[Number(key.slice(5)) - 1].slice(0, 3)}</span>
                                <span className="block mt-1 text-[11px] text-[var(--color-fg-muted)]">{state.label}</span>
                                {item && <Button variant="link" size="xs" className="mt-1 p-0 h-auto text-[11px]" onClick={() => navigate(state.variant === 'success' ? `/paie/periodes/${item.idPeriodePaie || item.id}/sorties` : `/paie?periodId=${item.idPeriodePaie || item.id}`)}>{state.variant === 'success' ? 'Ouvrir' : 'Voir la campagne'}</Button>}
                            </div>;
                        })}
                    </div>
                    <p className="mt-3 text-xs text-[var(--color-fg-muted)]">Une période absente reste à qualifier : début d’activité, mois sans salarié ou données à reprendre. La page ne peut pas le déterminer automatiquement.</p>
                </CardBody>
            </Card>}

            <Card shadow="none">
                <CardHeader><CardTitle size="sm">{activeTab === 'mensuel' ? `Documents mensuels · ${periodCode}` : activeTab === 'trimestriel' ? `Documents trimestriels · ${selectedQuarter.slice(5)} ${selectedQuarter.slice(0, 4)}` : `Documents annuels · ${selectedYear}`}</CardTitle></CardHeader>
                <div className="divide-y divide-[var(--color-border-subtle)]">
                    {!catalog && !catalogError && <p className="px-5 py-4 text-xs text-[var(--color-fg-muted)]">Vérification des documents disponibles…</p>}
                    {list.map(item => {
                        const needsValidation = item.id === 'grand-livre' || item.id === 'irsa' || item.id === 'social';
                        const { available, label: status } = item;
                        const Icon = activeTab === 'mensuel' ? item.id === 'bulletins' ? FileText : FileSpreadsheet : activeTab === 'trimestriel' ? CalendarDays : Archive;
                        return <div key={item.id || item.name}>
                            {activeTab === 'mensuel' && (item.id === 'bulletins' || item.id === 'grand-livre') && <div className="px-4 sm:px-5 py-2 bg-[var(--color-bg-subtle)] text-[11px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">{item.id === 'bulletins' ? 'Bulletins' : 'États de paie'}</div>}
                            <div className="px-4 sm:px-5 py-4 grid gap-3 sm:grid-cols-[minmax(0,1fr)_minmax(150px,auto)_minmax(160px,auto)] sm:items-center">
                                <div className="flex items-start gap-3 min-w-0">
                                    <Icon size={19} className="mt-0.5 shrink-0 text-[var(--color-brand-600)]" />
                                    <div className="min-w-0"><h3 className="text-sm font-semibold text-[var(--color-fg)]">{item.name}</h3>
                                        <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{item.detail}</p>
                                        {item.format && <p className="mt-1 text-[11px] text-[var(--color-fg-subtle)]">Format : {item.format}</p>}
                                    </div>
                                </div>
                                <Badge variant={available ? 'success' : 'secondary'} size="xs" className="justify-self-start">{status}</Badge>
                                <div className="sm:justify-self-end">
                                    {available && item.id === 'bulletins' && <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/periodes/${periodId}/controle`)}>Choisir un salarié</Button>}
                                    {available && needsValidation && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} loading={downloading === item.id} onClick={() => downloadExcel(item.id)}>Télécharger Excel</Button>}
                                    {activeTab === 'trimestriel' && available && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} loading={downloading === item.id} onClick={() => downloadQuarterly(item.id)}>Télécharger Excel</Button>}
                                    {activeTab === 'annuel' && available && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} loading={downloading === item.id} onClick={downloadAnnual}>Télécharger Excel</Button>}
                                </div>
                            </div>
                        </div>;
                    })}
                </div>
            </Card>
            {activeTab === 'mensuel' && periodId && canExport && <div className="flex justify-end border-t border-[var(--color-border-subtle)] pt-4">
                <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/periodes/${periodId}/sorties`)}>Voir l’étape de clôture</Button>
            </div>}
            {activeTab === 'trimestriel' && <p className="text-xs text-[var(--color-fg-muted)]">Cette synthèse sert au contrôle interne des cotisations. Elle ne constitue pas une preuve de déclaration auprès d’un organisme.</p>}
            {activeTab === 'annuel' && <p className="text-xs text-[var(--color-fg-muted)]">Le récapitulatif utilise uniquement les campagnes de paie validées. Si l’année est incomplète, les mois inclus sont indiqués dans le classeur ; les mois absents ou non validés n’y figurent pas.</p>}
        </div>
    );
}
