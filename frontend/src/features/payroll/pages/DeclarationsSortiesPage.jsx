import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { Archive, ArrowRight, CalendarDays, Download, FileSpreadsheet, FileText, FolderOpen } from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import paieService from '@/features/payroll/api/paieService';
import { getErrorMessage } from '@/api/apiClient';

const MONTHLY = [
    { id: 'bulletins', name: 'Bulletins individuels', detail: 'Un bulletin par salarié, consultable depuis la campagne.', format: 'PDF · Excel', ready: true },
    { id: 'grand-livre', name: 'Grand livre de paie', detail: 'État nominatif, détail des rubriques et contrôles réunis dans un classeur.', format: 'Excel', ready: true },
    { id: 'irsa', name: 'État IRSA', detail: 'Modèle fiscal renseigné avec les bulletins validés, à vérifier par la DAF.', format: 'Excel (.xls)', ready: true },
    { id: 'social', name: 'État social mensuel', detail: 'Totaux par organisme et cotisations par salarié pour le contrôle DAF.', format: 'Excel (.xlsx)', ready: true },
];
const QUARTERLY = [
    { id: 'consolidation', name: 'Consolidation sociale', detail: 'Synthèse et détail par salarié et par mois pour le contrôle DAF.', format: 'Excel (.xlsx)' },
    { id: 'cnaps', name: 'CNaPS · DNWEB', detail: 'Modèle nominatif sur trois mois, à compléter et vérifier avant dépôt.', format: 'Excel (.xlsx)' },
    { id: 'fmfp', name: 'CNaPS / FMFP', detail: 'Modèle combiné avec récapitulatif et trois feuilles mensuelles.', format: 'Excel (.xls)' },
    { id: 'ostie', name: 'OSTIE', detail: 'Modèle nominatif et récapitulatif trimestriel à vérifier.', format: 'Excel (.xlsx)' },
    { id: 'omsi', name: 'OMSI', detail: 'Modèle nominatif et récapitulatif trimestriel à vérifier.', format: 'Excel (.xlsx)' },
];
const YEARLY = [
    { name: 'Récapitulatif annuel de paie', detail: 'Cumuls par salarié et détail mensuel à partir des périodes retenues.' },
];
const TABS = [
    { id: 'mensuel', label: 'Mensuel' },
    { id: 'trimestriel', label: 'Trimestriel' },
    { id: 'annuel', label: 'Annuel' },
];
const MONTH_NAMES = ['Janvier', 'Février', 'Mars', 'Avril', 'Mai', 'Juin', 'Juillet', 'Août', 'Septembre', 'Octobre', 'Novembre', 'Décembre'];
const VALIDATED_STATES = new Set(['VALIDEE', 'PAIE_VALIDEE', 'CLOTUREE']);

const monthKey = (period) => {
    const code = period?.code?.match(/^(\d{4})-(0[1-9]|1[0-2])$/)?.[0];
    return code || period?.dateFin?.slice(0, 7) || null;
};
const quarterKey = (month) => month ? `${month.slice(0, 4)}-T${Math.ceil(Number(month.slice(5, 7)) / 3)}` : null;
const periodState = (period) => period?.workflowStatuses?.paie?.code || period?.statutCode || period?.statut || period?.state;
const monthStatus = (period) => !period
    ? { label: 'Période absente', variant: 'secondary' }
    : VALIDATED_STATES.has(periodState(period))
        ? { label: periodState(period) === 'CLOTUREE' ? 'Clôturée' : 'Paie validée', variant: 'success' }
        : { label: period.workflowStatuses?.paie?.label || 'À valider', variant: 'warning' };

/** Catalogue des documents de paie. Les exports futurs sont présentés sans action fictive. */
export default function DeclarationsSortiesPage() {
    const navigate = useNavigate();
    const [downloading, setDownloading] = useState(null);
    const [downloadError, setDownloadError] = useState('');
    const [quarterStatus, setQuarterStatus] = useState(null);
    const [quarterStatusError, setQuarterStatusError] = useState('');
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
    const validatedMonthCount = coveredMonths.filter(key => VALIDATED_STATES.has(periodState(periodsByMonth.get(key)))).length;
    useEffect(() => {
        if (activeTab !== 'trimestriel' || validatedMonthCount !== 3) { setQuarterStatus(null); setQuarterStatusError(''); return; }
        let cancelled = false;
        setQuarterStatus(null); setQuarterStatusError('');
        paieService.getQuarterlyExportStatus(Number(selectedQuarter.slice(0, 4)), Number(selectedQuarter.slice(-1)))
            .then(result => { if (!cancelled) setQuarterStatus(result); })
            .catch(error => { if (!cancelled) setQuarterStatusError(getErrorMessage(error, 'Impossible de vérifier les exports trimestriels.')); });
        return () => { cancelled = true; };
    }, [activeTab, selectedQuarter, validatedMonthCount]);
    const changeParam = (key, value) => {
        const next = new URLSearchParams(params);
        if (value) next.set(key, value); else next.delete(key);
        setParams(next);
    };
    const list = activeTab === 'mensuel' ? MONTHLY : activeTab === 'trimestriel' ? QUARTERLY : YEARLY;
    const canExport = context?.availableActions?.GENERATE_OUTPUT?.allowed === true;
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
        if (validatedMonthCount !== 3 || !quarterStatus?.[type]) return;
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

    return (
        <div className="max-w-7xl 2xl:max-w-[1600px] mx-auto w-full space-y-5 pb-8">
            <SEO title="Documents et exports de paie | GALAXAM GRH" description="Catalogue des documents mensuels, trimestriels et annuels de paie." />
            <PageHeader title="Documents et exports de paie" subtitle="Retrouvez les pièces par période. Les formats déclaratifs restent soumis à la revue de la DAF."
                icon={FolderOpen} backLink="/paie" backLabel="Centre de paie"
                actions={activeTab === 'mensuel' && periodId && <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/periodes/${periodId}/sorties`)}>Étape de clôture</Button>} />
            {downloadError && <Alert type="error" message={downloadError} onClose={() => setDownloadError('')} />}
            {quarterStatusError && <Alert type="error" message={quarterStatusError} />}

            <Card shadow="none">
                <CardBody className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="flex items-center gap-2"><CalendarDays size={17} className="text-[var(--color-brand-600)]" /><span className="text-sm font-semibold">{activeTab === 'mensuel' ? 'Période de paie' : activeTab === 'trimestriel' ? 'Trimestre' : 'Année'}</span><Badge variant="secondary" size="sm">{activeTab === 'mensuel' ? periodCode : activeTab === 'trimestriel' ? `${selectedQuarter.slice(5)} ${selectedQuarter.slice(0, 4)}` : selectedYear}</Badge></div>
                    <div className="flex items-center gap-2 min-w-[280px]">
                        <span className="text-xs text-[var(--color-fg-muted)] shrink-0">{activeTab === 'mensuel' ? 'Changer de période :' : activeTab === 'trimestriel' ? 'Choisir un trimestre :' : 'Choisir une année :'}</span>
                        <SearchSelect
                            placeholder={activeTab === 'mensuel' ? 'Rechercher une période…' : activeTab === 'trimestriel' ? 'Choisir un trimestre…' : 'Choisir une année…'}
                            value={activeTab === 'mensuel' ? periodId ? String(periodId) : '' : activeTab === 'trimestriel' ? selectedQuarter : selectedYear}
                            onChange={(val) => changeParam(activeTab === 'mensuel' ? 'periodId' : activeTab === 'trimestriel' ? 'trimestre' : 'annee', val)}
                            className="w-full min-w-[220px]"
                            options={activeTab === 'mensuel' ? periodes.map(p => {
                                const id = p.idPeriodePaie || p.id;
                                return {
                                    value: String(id),
                                    label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(id),
                                    sublabel: p.statutCode || p.statut || p.state || undefined,
                                };
                            }) : activeTab === 'trimestriel' ? quarterOptions : yearOptions}
                        />
                    </div>
                </CardBody>
            </Card>

            <nav aria-label="Périodicité des documents" className="flex border-b border-[var(--color-border-subtle)] gap-1">
                {TABS.map(tab => <button key={tab.id} type="button" onClick={() => changeParam('vue', tab.id)}
                    aria-current={activeTab === tab.id ? 'page' : undefined}
                    className={`px-4 py-2.5 text-sm font-semibold border-b-2 transition-colors ${activeTab === tab.id ? 'border-[var(--color-brand-600)] text-[var(--color-brand-700)]' : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'}`}>{tab.label}</button>)}
            </nav>

            {activeTab !== 'mensuel' && <Card shadow="none">
                <CardHeader><CardTitle size="sm">{activeTab === 'trimestriel' ? `Couverture de ${selectedQuarter.slice(5)} ${selectedQuarter.slice(0, 4)}` : `Couverture de ${selectedYear}`} · {validatedMonthCount}/{coveredMonths.length} périodes validées</CardTitle></CardHeader>
                <CardBody>
                    <div className={`grid gap-3 ${activeTab === 'trimestriel' ? 'md:grid-cols-3' : 'sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4'}`}>
                        {coveredMonths.map(key => {
                            const item = periodsByMonth.get(key);
                            const state = monthStatus(item);
                            return <div key={key} className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-4 py-3 min-w-0">
                                <div className="flex items-center justify-between gap-2"><span className="text-sm font-semibold">{MONTH_NAMES[Number(key.slice(5)) - 1]} {key.slice(0, 4)}</span><Badge variant={state.variant} size="xs">{state.label}</Badge></div>
                                {item && <button type="button" className="mt-2 text-xs font-semibold text-[var(--color-brand-700)] hover:underline" onClick={() => navigate(`/paie/periodes/${item.idPeriodePaie || item.id}/sorties`)}>Voir la campagne →</button>}
                            </div>;
                        })}
                    </div>
                    <p className="mt-3 text-xs text-[var(--color-fg-muted)]">Une période absente reste à qualifier : début d’activité, mois sans salarié ou données à reprendre. La page ne peut pas le déterminer automatiquement.</p>
                </CardBody>
            </Card>}

            <Card shadow="none">
                <CardHeader><CardTitle size="sm">{activeTab === 'mensuel' ? `Documents mensuels · ${periodCode}` : activeTab === 'trimestriel' ? `Documents trimestriels · ${selectedQuarter.slice(5)} ${selectedQuarter.slice(0, 4)}` : `Documents annuels · ${selectedYear}`}</CardTitle></CardHeader>
                <div className="divide-y divide-[var(--color-border-subtle)]">
                    {list.map(item => {
                        const needsValidation = item.id === 'grand-livre' || item.id === 'irsa' || item.id === 'social';
                        const available = activeTab === 'trimestriel' ? validatedMonthCount === 3 && Number(quarterStatus?.[item.id]) > 0 : item.ready && Boolean(periodId) && (!needsValidation || canExport);
                        const status = activeTab === 'trimestriel' ? validatedMonthCount !== 3 ? '3 mois requis' : quarterStatus == null ? 'Vérification' : available ? 'À contrôler' : 'Aucune cotisation' : needsValidation && !available ? 'À valider' : available ? 'Disponible' : 'À construire';
                        const Icon = activeTab === 'mensuel' ? item.id === 'bulletins' ? FileText : FileSpreadsheet : activeTab === 'trimestriel' ? CalendarDays : Archive;
                        return <div key={item.id || item.name} className="px-4 sm:px-5 py-4 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                            <div className="flex items-start gap-3 min-w-0">
                                <Icon size={19} className="mt-0.5 shrink-0 text-[var(--color-brand-600)]" />
                                <div><div className="flex flex-wrap items-center gap-2"><h3 className="text-sm font-semibold text-[var(--color-fg)]">{item.name}</h3><Badge variant={available ? 'success' : 'secondary'} size="xs">{status}</Badge></div>
                                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{item.detail}</p>
                                    {item.format && <p className="mt-1 text-[11px] text-[var(--color-fg-subtle)]">{item.ready || activeTab === 'trimestriel' ? 'Format' : 'Format prévu'} : {item.format}</p>}
                                </div>
                            </div>
                            {available && item.id === 'bulletins' && <Button variant="outline" size="sm" iconRight={<ArrowRight size={14} />} onClick={() => navigate(`/paie/periodes/${periodId}/controle`)}>Choisir un salarié</Button>}
                            {available && needsValidation && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} loading={downloading === item.id} onClick={() => downloadExcel(item.id)}>Télécharger Excel</Button>}
                            {activeTab === 'trimestriel' && available && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} loading={downloading === item.id} onClick={() => downloadQuarterly(item.id)}>Télécharger le modèle</Button>}
                        </div>;
                    })}
                </div>
            </Card>
            {activeTab === 'trimestriel' && <p className="text-xs text-[var(--color-fg-muted)]">Ces modèles sont des brouillons à revoir par la DAF. Les identifiants préremplis du modèle sont conservés ; les champs d’adhésion vides restent à compléter. L’acceptation sur les portails officiels n’a pas été vérifiée.</p>}
            {activeTab === 'annuel' && <p className="text-xs text-[var(--color-fg-muted)]">Le récapitulatif annuel sera alimenté par les campagnes vérifiées ; aucun export annuel n’est disponible actuellement.</p>}
        </div>
    );
}
