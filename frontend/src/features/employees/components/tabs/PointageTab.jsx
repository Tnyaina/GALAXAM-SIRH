import { useState, useEffect, useCallback, useMemo, useRef, lazy, Suspense } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
    Clock,
    FileDown,
    FileSpreadsheet,
    FileText,
    TrendingUp,
    Info,
    ChevronLeft,
    ChevronRight,
    Calendar,
    CalendarX,
    DollarSign,
    ExternalLink,
    Scale,
    ChevronDown,
    FastForward,
    AlertTriangle,
    Award,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import { attendanceService, periodePaieService } from '@/features/attendance/api/attendanceApi';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import PeriodeNavigator from '@/components/ui/navigation/PeriodeNavigator';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { getTodayDateString, fmtDateFr } from '@/utils/dateUtils';
import { formatHeuresDecimal, formatHeuresMinutes } from '@/features/attendance/utils/attendanceDisplayUtils';
import WeeklyPointageCalculationTable from '@/features/attendance/components/WeeklyPointageCalculationTable';

const DafAjustementPointageModal = lazy(() => import('@/features/attendance/components/modals/DafAjustementPointageModal'));
const PointageSoldePeriodeModal = lazy(() => import('@/features/attendance/components/modals/PointageSoldePeriodeModal'));


export default function PointageTab({ idEmploye, employe = null }) {
    // Contexte
    const { toast } = useToast();
    const navigate = useNavigate();

    // État local : période, fiche, détail et export.
    const [periodView, setPeriodView] = useState('periode');
    const [selectedDate, setSelectedDate] = useState(() => getTodayDateString());
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
    const [periodesList, setPeriodesList] = useState([]);
    const [periodsReady, setPeriodsReady] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [noPeriodError, setNoPeriodError] = useState(false);
    const [ficheData, setFicheData] = useState(null);
    const [loadedSelection, setLoadedSelection] = useState(null);
    const [correctionDate, setCorrectionDate] = useState(null);
    const [showBalance, setShowBalance] = useState(false);

    // État du menu et du téléchargement des documents
    const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
    const [exportingExcel, setExportingExcel] = useState(false);
    const dropdownRef = useRef(null);
    const loadRequest = useRef(0);
    const selectionKey = periodView === 'periode' && selectedPeriodeId
        ? `${idEmploye}:periode:${selectedPeriodeId}`
        : `${idEmploye}:${periodView}:${selectedDate}`;
    const hasCurrentFiche = ficheData && loadedSelection === selectionKey;

    // Effets : chargent les périodes, la fiche et synchronisent la sélection.
    useEffect(() => {
        const handleClickOutside = (e) => {
            if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
                setExportDropdownOpen(false);
            }
        };
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    const formatDecimalHours = (val, withSign = false) => {
        if (val === null || val === undefined || isNaN(val)) return '0.00h';
        const num = Number(val);
        const rounded = Math.round(num * 100) / 100;
        const sign = rounded > 0 && withSign ? '+' : '';
        return `${sign}${rounded.toFixed(2)}h`;
    };

    const parseHhMmToDecimal = (str) => {
        if (!str || typeof str !== 'string' || !str.includes('h')) return 0.0;
        const parts = str.split('h');
        const h = parseFloat(parts[0]) || 0;
        const m = parseFloat(parts[1]) || 0;
        return Math.round((h + m / 60.0) * 100.0) / 100.0;
    };

    const formatHhMmWithDecimal = (str, forceSign = '') => {
        if (!str || typeof str !== 'string' || !str.includes('h') || str === '—' || str === '-') return '0.00h';
        const isNeg = str.startsWith('-') || str.startsWith('−') || forceSign === '-';
        const isPos = str.startsWith('+') || forceSign === '+';
        const clean = str.replace(/^[+−-]/, '').trim();
        const parts = clean.split('h');
        const h = parseFloat(parts[0]) || 0;
        const m = parseFloat(parts[1]) || 0;
        const dec = (h + m / 60.0).toFixed(2);
        if (dec === '0.00') return '0.00h';
        return `${isNeg ? '-' : isPos ? '+' : ''}${dec}h`;
    };

    const parseEcartToDecimal = (str) => {
        if (!str || typeof str !== 'string' || str === '—' || str === '-' || str === '00 min' || str === '00h00' || str.includes('(')) return null;
        const isNeg = str.startsWith('-') || str.startsWith('−');
        const isPos = str.startsWith('+');
        const clean = str.replace(/^[+−-]/, '').trim();
        if (clean.includes('h')) {
            const parts = clean.split('h');
            const h = parseFloat(parts[0]) || 0;
            const m = parseFloat(parts[1]) || 0;
            const dec = (h + m / 60.0).toFixed(2);
            return `${isNeg ? '−' : isPos ? '+' : ''}${dec}h`;
        }
        if (clean.includes('min')) {
            const m = parseFloat(clean.replace('min', '').trim()) || 0;
            const dec = (m / 60.0).toFixed(2);
            return `${isNeg ? '−' : isPos ? '+' : ''}${dec}h`;
        }
        return null;
    };

    // 1. Charger la liste des périodes de paie officielles
    useEffect(() => {
        const fetchPeriodes = async () => {
            try {
                const data = await periodePaieService.getPeriodOptions();
                if (data && Array.isArray(data) && data.length > 0) {
                    setPeriodesList(data);
                    const active = data.find(p => p.statutCode === 'EN_COURS' || p.codeStatut === 'EN_COURS') || data[0];
                    if (active?.idPeriodePaie) {
                        setSelectedPeriodeId(prev => prev || active.idPeriodePaie);
                        const today = getTodayDateString();
                        if (active.dateDebut && active.dateFin && (today < active.dateDebut || today > active.dateFin)) {
                            setSelectedDate(active.dateDebut);
                        }
                    }
                }
            } catch (err) {
                console.error('Erreur chargement liste des périodes de paie:', err);
            } finally {
                setPeriodsReady(true);
            }
        };
        fetchPeriodes();
    }, []);

    const loadFiche = useCallback(async ({ background = false } = {}) => {
        if (!idEmploye || !periodsReady) return;
        const requestId = ++loadRequest.current;
        if (!background) {
            setLoading(true);
        }
        setError(null);
        try {
            const params = {
                date: selectedDate,
                period: periodView
            };
            if (periodView === 'periode' && selectedPeriodeId) {
                params.idPeriode = selectedPeriodeId;
            }
            const res = await attendanceService.getFicheEmploye(idEmploye, params);
            if (requestId !== loadRequest.current) return;
            setFicheData(res);
            setLoadedSelection(periodView === 'periode' && (selectedPeriodeId || res?.idPeriode)
                ? `${idEmploye}:periode:${selectedPeriodeId || res.idPeriode}`
                : `${idEmploye}:${periodView}:${selectedDate}`);
            setNoPeriodError(false);
            setError(null);
            if (res?.idPeriode && periodView === 'periode') {
                setSelectedPeriodeId(res.idPeriode);
            }
        } catch (err) {
            if (requestId !== loadRequest.current) return;
            setFicheData(null);
            console.error('Erreur chargement fiche pointage employe:', err);
            const errCode = err.response?.data?.errorCode || err.response?.data?.code;
            const errMsg = (err.response?.data?.message || err.message || '').toLowerCase();
            if (errCode === 'POINTAGE_PERIOD_CONTEXT_REQUIRED' || (errMsg.includes('période') && errMsg.includes('introuvable'))) {
                setNoPeriodError(true);
                setError(null);
            } else {
                setNoPeriodError(false);
                setError('Impossible de charger les données de pointage pour cet employé.');
            }
        } finally {
            if (requestId === loadRequest.current) setLoading(false);
        }
    }, [idEmploye, selectedDate, periodView, selectedPeriodeId, periodsReady]);

    useEffect(() => {
        loadFiche();
    }, [loadFiche]);

    // Navigation entre périodes de paie
    const handleNavigatePeriode = (delta) => {
        if (!periodesList || periodesList.length === 0) return;
        const currIndex = periodesList.findIndex(p => p.idPeriodePaie === selectedPeriodeId);
        if (currIndex === -1) return;
        const newIndex = currIndex - delta; // Liste triée DESC (0 = plus récent)
        if (newIndex >= 0 && newIndex < periodesList.length) {
            const nextP = periodesList[newIndex];
            setSelectedPeriodeId(nextP.idPeriodePaie);
            if (nextP.dateDebut) setSelectedDate(nextP.dateDebut);
        }
    };

    const handleExportDocument = async (format) => {
        setExportDropdownOpen(false);
        setExportingExcel(true);
        try {
            const params = { date: selectedDate, period: 'periode' };
            if (selectedPeriodeId) params.idPeriode = selectedPeriodeId;
            const { blob, filename } = await attendanceService[format === 'pdf' ? 'exportPdf' : 'exportExcel'](idEmploye, params);
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            link.click();
            setTimeout(() => window.URL.revokeObjectURL(url), 1000);
            toast({ title: 'Document téléchargé', message: format === 'pdf' ? 'Ouvrez le PDF pour le consulter ou l’imprimer.' : 'Le fichier utilise les données calculées par le GRH.', type: 'success' });
        } catch (err) {
            let message = err.response?.data?.message;
            if (err.response?.data instanceof Blob) {
                try { message = JSON.parse(await err.response.data.text()).message; } catch { /* Réponse non JSON */ }
            }
            toast({ title: 'Erreur d’exportation', message: message || 'Impossible de générer le document de pointage.', type: 'error' });
        } finally {
            setExportingExcel(false);
        }
    };
    const handleExportExcel = () => handleExportDocument('excel');
    const handleOpenPdfModal = () => handleExportDocument('pdf');


    const getStatutBadge = (target) => {
        const ptg = typeof target === 'object' && target !== null ? target : null;
        const statut = (ptg ? ptg.statut : target)?.toUpperCase();

        if (ptg?.isNonEchu || statut === 'NON_ECHU') {
            return <span className="text-[11px] text-[var(--color-fg-subtle)]">-</span>;
        }
        if (ptg?.isConge || statut === 'CONGE' || statut === 'CONGE_PAYE') {
            return <Badge variant="info" size="sm">Congé</Badge>;
        }
        if (ptg?.isFerieTravaille || statut === 'FERIE_TRAVAILLE') {
            return <Badge variant="purple" size="sm">Férié +100%</Badge>;
        }
        if (ptg?.isFerieChome || statut === 'FERIE_CHOME') {
            return <Badge variant="neutral" size="sm">Férié chômé</Badge>;
        }
        if (ptg?.isDimancheTravaille || statut === 'DIMANCHE_TRAVAILLE') {
            return <Badge variant="purple" size="sm">Dimanche +40%</Badge>;
        }
        if (ptg?.isSamediTravaille || statut === 'SAMEDI_TRAVAILLE') {
            return <Badge variant="sky" size="sm">Samedi travaillé</Badge>;
        }
        if (ptg?.isDispensePointage || statut === 'DISPENSE_POINTAGE') {
            return <Badge variant="info" size="sm">Autonome</Badge>;
        }
        if (ptg?.isDemiJournee || statut === 'DEMI_JOURNEE') {
            return <Badge variant="sky" size="sm">Demi-journée</Badge>;
        }
        if (ptg?.isPresent || statut === 'PRESENT') {
            return <Badge variant="success" size="sm">Présent</Badge>;
        }
        if (ptg?.isRetard || statut === 'RETARD') {
            return <Badge variant="warning" size="sm">Retard</Badge>;
        }
        if (ptg?.isAbsent || statut === 'ABSENT' || statut === 'ABSENCE_NON_JUSTIFIEE') {
            return <Badge variant="danger" size="sm">Absent</Badge>;
        }
        if (ptg?.isRepos || statut === 'REPOS') {
            return <Badge variant="neutral" size="sm">Repos</Badge>;
        }
        if (ptg?.isPlanifie || statut === 'PLANIFIE') {
            return <Badge variant="neutral" size="sm" className="text-[var(--color-fg-muted)]">Planifié</Badge>;
        }
        if (ptg?.isNonConcerne || statut === 'NON_CONCERNE') {
            return <Badge variant="neutral" size="sm" className="text-[10px]">Non concerné</Badge>;
        }
        return <Badge variant="neutral" size="sm">{statut || '-'}</Badge>;
    };

    const kpis = ficheData?.kpis || {};
    const recap = ficheData?.recap || {};
    const regles = ficheData?.reglesAppliquees || {};
    const pointages = ficheData?.pointagesData || [];
    const configuredAllowances = Object.values(ficheData?.indemnitesDisponibles || {})
        .map((allowance) => [allowance.code, {
            code: allowance.code,
            periodicite: allowance.periodicite,
            libelle: allowance.libelle,
        }]);
    const recordedAllowances = pointages.flatMap((pointage) => Object.values(pointage.indemnites || {})
        .map((allowance) => [allowance.code, { code: allowance.code }]));
    const dynamicAllowances = Array.from(new Map([...recordedAllowances, ...configuredAllowances]).values())
        .sort((a, b) => a.code.localeCompare(b.code, 'fr'));

    const hs30Val = parseFloat(kpis?.heuresHs30) || 0;
    const hs50Val = parseFloat(kpis?.heuresHs50) || 0;
    const nuitVal = parseFloat(kpis?.heuresNuit) || 0;
    const dimVal = parseFloat(kpis?.heuresDimanche) || 0;
    const ferVal = parseFloat(kpis?.heuresFerie) || 0;
    const hasMajorations = ficheData?.hasMajorations ?? (hs30Val > 0 || hs50Val > 0 || nuitVal > 0 || dimVal > 0 || ferVal > 0);
    const isAssiduiteRuleDisabled = ficheData?.isPrimeAssiduiteActive !== undefined
        ? !ficheData.isPrimeAssiduiteActive
        : kpis?.primeAssiduiteEligible === 'DISABLED';
    const seuilAssiduiteMinutes = kpis.seuilAssiduiteMinutes || regles.seuilAssiduiteMinutes || '-';
    const dureeHebdoLegale = regles.dureeHebdoLegale || kpis.dureeHebdoLegale || '-';
    const dureeJourNormale = regles.dureeJourNormale;
    const isAssiduiteEligible = ficheData?.isPrimeAssiduiteEligible !== undefined
        ? Boolean(ficheData.isPrimeAssiduiteEligible)
        : kpis.primeAssiduiteEligible === 'true';
    const hasRetardNet = ficheData?.hasRetardNet !== undefined
        ? Boolean(ficheData.hasRetardNet)
        : Boolean(recap?.retardNet && recap.retardNet !== '00h00' && recap.retardNet !== '0h');
    const hasHsNettes = ficheData?.hasHsNettes !== undefined
        ? Boolean(ficheData.hasHsNettes)
        : Boolean(recap?.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' && recap.heuresSuppNettes !== '0h');

    const totalRetardMinutes = Number(kpis.retardBrutMinutes || 0);
    const totalRetardHhMm = kpis.retardCumule || '-';
    const displayEcart = recap.ecartCumule || '-';
    const dynamicAllowanceTotals = ficheData?.totauxIndemnites || {};
    const fixedMonthlyAllowances = Object.entries(ficheData?.indemnitesFixesMensuelles || {});
    const heuresQualifiees = (ficheData?.bilansHebdo || []).reduce((totals, week) => ({
        nuit: totals.nuit + Number(week.heuresNuit || 0),
        dimanche: totals.dimanche + Number(week.heuresDimanche || 0),
        ferie: totals.ferie + Number(week.heuresFerie || 0),
    }), { nuit: 0, dimanche: 0, ferie: 0 });
    const actions = !loading && !error ? ficheData?.availableActions || {} : {};
    const currIndexInList = periodesList.findIndex(p => p.idPeriodePaie === selectedPeriodeId);

    // Rendu
    return (
        <div className="space-y-4 print:m-0 print:p-0">
            <p className="text-xs text-[var(--color-fg-muted)]">Consultez la période, vérifiez les journées, puis contrôlez le bilan et les régularisations.</p>
            {/* 1. Barre d'outils supérieure : Sélecteur de Période, Bascule Période/Mois/Semaine/Jour et Actions */}
            <Card padding={false} bordered={false} shadow="none" className="flex flex-col md:flex-row md:items-center justify-between gap-4 py-2 print:hidden">
                <div className="flex flex-wrap items-center gap-3">

                    {/* Navigation temporelle selon le mode */}
                    {periodView === 'periode' ? (
                        <PeriodeNavigator
                            periodesList={periodesList}
                            selectedPeriodeId={selectedPeriodeId}
                            onSelectPeriode={(pId, target) => {
                                setSelectedPeriodeId(pId);
                                if (target?.dateDebut) setSelectedDate(target.dateDebut);
                            }}
                        />
                    ) : (
                        <PeriodNavigator
                            viewPeriod={periodView}
                            selectedDate={selectedDate}
                            onDateChange={(newDate) => setSelectedDate(newDate)}
                            className="h-9"
                        />
                    )}

                    {/* Bascule Vue : Période de paie | Semaine */}
                    <div className="flex h-9 items-center border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-0.5 rounded-none">
                        {[
                            { key: 'periode', label: 'Période de paie' },
                            { key: 'semaine', label: 'Semaine' },
                        ].map(({ key, label }) => (
                            <button
                                key={key}
                                type="button"
                                onClick={() => setPeriodView(key)}
                                className={`px-3 py-1 text-xs font-semibold transition-colors cursor-pointer ${periodView === key
                                    ? 'bg-[var(--color-brand-500)] text-[var(--color-on-color)] shadow-xs'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                    }`}
                            >
                                {label}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex items-center gap-2 self-end md:self-auto">
                    {actions.TEAM_ENTRY && (
                        <>
                            <Link
                                to={`/pointages?tab=saisie&mode=employe&employe=${idEmploye}${selectedPeriodeId ? `&periode=${selectedPeriodeId}` : ''}`}
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-[var(--color-on-color)] bg-[var(--color-brand-600)] hover:bg-[var(--color-brand-700)] border border-[var(--color-brand-600)] transition-colors shadow-xs"
                                title="Ouvrir la grille de saisie pour ce collaborateur"
                            >
                                <span>Modifier les pointages</span>
                                <ExternalLink size={13} />
                            </Link>
                            <Link
                                to="/pointages?tab=saisie"
                                className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 border border-[var(--color-brand-500)]/30 transition-colors"
                                title="Ouvrir le module de saisie quotidienne pour toute l'équipe"
                            >
                                <span>Saisie d'équipe</span>
                                <ExternalLink size={13} />
                            </Link>
                        </>
                    )}
                    {/* Menu Déroulant Exporter (Excel / PDF) */}
                    <div className="relative inline-block" ref={dropdownRef}>
                        <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            disabled={!ficheData?.idPeriode}
                            loading={exportingExcel}
                            onClick={() => setExportDropdownOpen(prev => !prev)}
                            className="text-xs font-semibold flex items-center gap-1.5 shadow-xs cursor-pointer"
                        >
                            <FileDown size={14} />
                            <span>Exporter</span>
                            <ChevronDown size={13} className={`transition-transform duration-200 ${exportDropdownOpen ? 'rotate-180' : ''}`} />
                        </Button>

                        {exportDropdownOpen && (
                            <div className="absolute right-0 mt-1 w-56 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-lg z-30 py-1 rounded-none text-left animate-in fade-in zoom-in-95 duration-150">
                                <button
                                    type="button"
                                    onClick={handleExportExcel}
                                    className="w-full px-3 py-2 text-xs font-medium flex items-center gap-2.5 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer text-left"
                                >
                                    <FileSpreadsheet size={15} className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] shrink-0" />
                                    <div>
                                        <div className="font-semibold">Format Excel (.xlsx)</div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Quantités, montants et bilan du GRH</div>
                                    </div>
                                </button>
                                <div className="my-1 border-t border-[var(--color-border-subtle)]" />
                                <button
                                    type="button"
                                    onClick={handleOpenPdfModal}
                                    className="w-full px-3 py-2 text-xs font-medium flex items-center gap-2.5 text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors cursor-pointer text-left"
                                >
                                    <FileText size={15} className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] shrink-0" />
                                    <div>
                                        <div className="font-semibold">Fiche individuelle PDF</div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Format A4 prêt à imprimer et émarger</div>
                                    </div>
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </Card>

            {/* Bandeau d'en-tête de la Période affichée */}
            {ficheData && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-4 py-3 flex flex-wrap items-center justify-between gap-3 shadow-xs">
                    <div className="flex items-center gap-2.5">
                        <TrendingUp size={16} className="text-[var(--color-brand-500)]" />
                        <span className="text-xs font-semibold text-[var(--color-fg)]">
                            {ficheData.libellePeriode || 'Période de présence'}
                        </span>
                        {ficheData.dateDebut && ficheData.dateFin && (
                            <span className="text-xs text-[var(--color-fg-muted)] font-data">
                                (du {ficheData.dateDebut} au {ficheData.dateFin})
                            </span>
                        )}
                    </div>
                    {ficheData.statutPeriode && (
                        <Badge
                            variant={ficheData.isPeriodeCloturee || ficheData.isPeriodeValidee || ficheData.statutPeriode === 'CLOTUREE' || ficheData.statutPeriode === 'VALIDEE' ? 'success' : 'warning'}
                            size="sm"
                        >
                            {ficheData.isPeriodeCloturee || ficheData.statutPeriode === 'CLOTUREE' ? 'Clôturée' : ficheData.isPeriodeValidee || ficheData.statutPeriode === 'VALIDEE' ? 'Validée' : ficheData.isPeriodeEnCours || ficheData.statutPeriode === 'EN_COURS' ? 'En cours' : ficheData.statutPeriode}
                        </Badge>
                    )}
                </div>
            )}

            {error && <Alert type="error" message={error} />}

            {loading && hasCurrentFiche && (
                <p className="py-2 text-xs text-[var(--color-fg-muted)]" role="status">Actualisation du pointage…</p>
            )}
            {loading && !hasCurrentFiche ? (
                <div className="space-y-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {[1, 2, 3, 4].map((i) => (
                            <div key={i} className="h-24 bg-[var(--color-bg-subtle)] animate-pulse" />
                        ))}
                    </div>
                    <SkeletonTable rows={10} cols={8} />
                </div>
            ) : noPeriodError ? (
                <EmptyState
                    icon={CalendarX}
                    title="Aucune période de paie active"
                    description="Aucune période de paie n'englobe actuellement la date sélectionnée. Créez ou ouvrez une période dans le module Paie pour démarrer la saisie et le calcul des présences."
                    actionLabel="Gérer les périodes de paie"
                    onAction={() => navigate('/paie?tab=periodes')}
                    actionIcon={<ExternalLink size={14} />}
                    className="my-4 py-12"
                />
            ) : ficheData ? (
                <>
                    {/* 2. Cartes d'indicateurs KPI réagencées selon les 3 Piliers (Présence, Gain Paie, Retenue Paie, Primes RH) */}
                    {/* 2. Cartes d'indicateurs KPI réagencées selon les 4 Piliers */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {/* KPI 1 : Présence & Temps Réalisé */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <Clock size={14} className="text-[var(--color-brand-500)]" />
                                        Temps travaillé
                                    </span>
                                    <Badge variant="brand" size="xs">Présence</Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className="text-2xl font-bold font-data text-[var(--color-fg)]">
                                            {kpis.heuresRealisees || '00h00'}
                                        </span>
                                        <span className="text-xs font-data text-[var(--color-fg-muted)]">
                                            ({formatDecimalHours(parseHhMmToDecimal(kpis.heuresRealisees))})
                                        </span>
                                    </div>
                                    {kpis.heuresObjectif ? (
                                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 font-medium">
                                            Objectif : {kpis.heuresObjectif.replace(/^objectif\s*/i, '')}
                                        </p>
                                    ) : null}
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>{kpis.joursTravailles || '0 j'} travaillés</span>
                                <span>{kpis.joursPrevus || '0 prévus'}</span>
                            </div>
                        </div>

                        {/* KPI 2 : Heures Sup Nettes */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <TrendingUp size={14} className="text-[var(--color-success-600)]" />
                                        Heures sup. finales
                                    </span>
                                    <Badge
                                        variant={hasHsNettes ? 'success' : 'neutral'}
                                        size="xs"
                                    >
                                        {hasHsNettes ? 'Solde positif' : '0h HS'}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-data ${hasHsNettes ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                            {hasHsNettes ? `+${recap.heuresSuppNettes}` : '00h00'}
                                        </span>
                                        <span className="text-xs font-data text-[var(--color-fg-muted)]">
                                            ({hasHsNettes ? `+${formatDecimalHours(parseHhMmToDecimal(recap.heuresSuppNettes), false)}` : '0.00h'})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 truncate" title={`Surplus brut : ${recap.heuresSuppBrutes || '00h00'}, dont ${recap.retardCompense || '00h00'} absorbé en compensation`}>
                                        {recap.heuresSuppBrutes && recap.heuresSuppBrutes !== '00h00' && recap.heuresSuppBrutes !== recap.heuresSuppNettes ? (
                                            <span className="text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)]">
                                                Surplus brut : {recap.heuresSuppBrutes} (dont {recap.retardCompense} compensé)
                                            </span>
                                        ) : (
                                            'Majoration légale +30% / +50%'
                                        )}
                                    </p>
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>HS 30% : {kpis.heuresHs30 ? kpis.heuresHs30.replace(',', '.') : '0.0h'}</span>
                                <span>HS 50% : {kpis.heuresHs50 ? kpis.heuresHs50.replace(',', '.') : '0.0h'}</span>
                            </div>
                        </div>

                        {/* KPI 3 : Déficit Net (Retenue sur Salaire) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <AlertTriangle size={14} className={hasRetardNet ? 'text-[var(--color-danger-500)]' : 'text-[var(--color-success-500)]'} />
                                        Déficit final
                                    </span>
                                    <Badge
                                        variant={hasRetardNet ? 'danger' : 'success'}
                                        size="xs"
                                    >
                                        {hasRetardNet ? 'Déduction paie' : '0h retenue'}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-data ${hasRetardNet ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                                            {hasRetardNet ? `-${recap.retardNet}` : '00h00'}
                                        </span>
                                        <span className="text-xs font-data text-[var(--color-fg-muted)]">
                                            ({hasRetardNet ? `-${formatDecimalHours(parseHhMmToDecimal(recap.retardNet), false)}` : '0.00h'})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 truncate">
                                        {recap.ajustements && recap.ajustements !== '00h00' && recap.ajustements !== '—' && recap.ajustements !== '-' ? (
                                            <span>
                                                Régularisation : <strong className="font-semibold">{recap.ajustements.replace('−', '-')}</strong>
                                            </span>
                                        ) : recap.retardCompense && recap.retardCompense !== '00h00' ? (
                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                                Heures compensées : <strong className="font-semibold">{recap.retardCompense}</strong>
                                            </span>
                                        ) : hasRetardNet ? (
                                            'Déficit net sous le seuil hebdo'
                                        ) : (
                                            'Aucun déficit horaire sur la période'
                                        )}
                                    </p>
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>Déficit hebdo : {recap.deficitHebdo && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' ? recap.deficitHebdo : '00h00'}</span>
                                <span className={recap.ajustements && recap.ajustements !== '00h00' && recap.ajustements !== '—' && recap.ajustements !== '-' ? 'font-medium' : recap.retardCompense && recap.retardCompense !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-medium' : ''}>
                                    {recap.ajustements && recap.ajustements !== '00h00' && recap.ajustements !== '—' && recap.ajustements !== '-' ? `Régul. : ${recap.ajustements.replace('−', '-')}` : `Compensé : ${recap.retardCompense || '00h00'}`}
                                </span>
                            </div>
                        </div>

                        {/* KPI 4 : Assiduité & Primes RH */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] flex items-center gap-1.5">
                                        <Award size={14} className={isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'} />
                                        Assiduité & Primes
                                    </span>
                                    <Badge
                                        variant={isAssiduiteRuleDisabled ? 'neutral' : (isAssiduiteEligible ? 'success' : 'warning')}
                                        size="xs"
                                    >
                                        {isAssiduiteRuleDisabled ? 'Inactive' : (isAssiduiteEligible ? 'Éligible' : 'Hors critère')}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-data ${isAssiduiteEligible ? 'text-[var(--color-fg)]' : 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]'}`}>
                                            {kpis.retardCumule || '00h00'}
                                        </span>
                                        <span className="text-xs font-data text-[var(--color-fg-muted)]">
                                            de retard
                                        </span>
                                    </div>
                                    <div className="mt-1 flex items-center gap-1 text-xs">
                                        {isAssiduiteRuleDisabled ? (
                                            <span className="text-[var(--color-fg-muted)] font-medium">Règle d'assiduité désactivée</span>
                                        ) : isAssiduiteEligible ? (
                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-medium flex items-center gap-1">
                                                <CheckCircle2 size={12} /> Éligible à la prime d’assiduité
                                            </span>
                                        ) : (
                                            <span className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-medium flex items-center gap-1" title={`La prime d'assiduité est accordée si le retard brut est inférieur à ${seuilAssiduiteMinutes} min`}>
                                                <XCircle size={12} /> Seuil {seuilAssiduiteMinutes} min dépassé
                                            </span>
                                        )}
                                    </div>
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>Retards constatés</span>
                                <span>{isAssiduiteRuleDisabled ? 'Aucun seuil appliqué' : `Tolérance : ${seuilAssiduiteMinutes} min`}</span>
                            </div>
                        </div>
                    </div>

                    {fixedMonthlyAllowances.length > 0 && (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4">
                            <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">Indemnités fixes de la période</h3>
                                <span className="text-xs text-[var(--color-fg-muted)]">{ficheData?.indemnitesFixesEstimees ? 'Estimation avant validation' : 'Montants validés'}</span>
                            </div>
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
                                {fixedMonthlyAllowances.map(([code, amount]) => (
                                    <div key={code} className="flex justify-between gap-3 p-3 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-sm">
                                        <span className="text-[var(--color-fg-muted)]">{code}</span>
                                        <strong className="text-[var(--color-fg)]">{Math.round(Number(amount)).toLocaleString('fr-FR')} Ar</strong>
                                    </div>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* 3. Tableau Détail Journalier (Pleine Largeur) */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                        <div className="p-4 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                            <div>
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">Journal quotidien</h3>
                                <p className="text-xs text-[var(--color-fg-muted)]">Pointages et calculs automatiques</p>
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <Table bordered={false}>
                                <Thead>
                                    <Th className="text-xs font-semibold py-3 px-3">Date</Th>
                                    <Th className="text-xs font-semibold py-3 px-3">Jour</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center">Entrée</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center">Pause</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center">Retour</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center">Sortie</Th>
                                    <Th className="text-xs font-semibold py-3 px-2 text-center" title="Cumul des retards constatés sur la journée (matin et après-midi)">Retard</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center" title="Temps de travail effectif de la journée">Heures faites</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center" title="Écart calculé par le serveur par rapport à la durée attendue">Écart</Th>
                                    <Th className="text-xs font-semibold py-3 px-3 text-center" title="Statut de la journée (Présent, Congé, Férié, Repos...)">Statut</Th>
                                    {dynamicAllowances.map((allowance) => (
                                        <Th key={allowance.code} className="text-xs font-semibold py-3 px-2 text-center border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10" title={allowance.code}>
                                            <span className="flex flex-col items-center">
                                                <span>{allowance.libelle || allowance.code}</span>
                                                {allowance.periodicite && (
                                                    <span className="text-[9px] font-normal normal-case opacity-75">
                                                        {allowance.periodicite.replaceAll('_', ' ').toLowerCase()}
                                                    </span>
                                                )}
                                            </span>
                                        </Th>
                                    ))}
                                </Thead>
                                <Tbody>
                                    {pointages.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={10 + dynamicAllowances.length} className="text-center py-8 text-xs text-[var(--color-fg-muted)]">
                                                Aucune donnée de pointage pour cette période.
                                            </Td>
                                        </Tr>
                                    ) : (
                                        pointages.map((ptg, idx) => {
                                            const st = (ptg.statut || '').toUpperCase();
                                            const isConge = Boolean(ptg.isConge ?? (st === 'CONGE' || st === 'CONGE_PAYE'));
                                            const isFerie = Boolean(ptg.isFerieChome ?? (st === 'FERIE_CHOME' || (st === 'FERIE' && (!ptg.heures || ptg.heures === '00h00' || ptg.heures === '—'))));
                                            const isRepos = Boolean(ptg.isRepos ?? (st === 'REPOS'));
                                            const isNonEchu = Boolean(ptg.isNonEchu ?? (st === 'NON_ECHU'));
                                            const isMerged = Boolean(ptg.isMerged ?? (isConge || isFerie || isRepos || isNonEchu));

                                            const isContinu = Boolean(
                                                ptg.isContinu ?? (
                                                    ptg.horaireContinuJour ||
                                                    ptg.horaireContinu ||
                                                    (ptg.sortiePause === '12:00' && ptg.retourPause === '12:00')
                                                )
                                            );
                                            const isWeekendDay = Boolean(
                                                ptg.weekend ?? (
                                                    ptg.jour?.startsWith('Sam') ||
                                                    ptg.jour?.startsWith('Dim') ||
                                                    (ptg.dateComplete && [0, 6].includes(new Date(`${ptg.dateComplete}T00:00:00`).getDay()))
                                                )
                                            );

                                            return (
                                                <Tr
                                                    key={`${ptg.dateComplete || ptg.date}-${idx}`}
                                                    className={
                                                        isConge
                                                            ? 'bg-[var(--color-sky-500)]/[0.04]'
                                                            : isFerie
                                                                ? 'bg-[var(--color-purple-500)]/[0.04]'
                                                                : isRepos
                                                                    ? 'bg-[var(--color-bg-subtle)]/40'
                                                                    : ''
                                                    }
                                                >
                                                    <Td className="font-data text-xs font-bold text-[var(--color-fg)] py-2.5 px-3">
                                                        {ptg.date}
                                                        {ptg.canCorrect && ptg.dateComplete && (
                                                            <button type="button" onClick={() => setCorrectionDate(ptg.dateComplete)} className="block mt-1 text-[11px] font-sans text-[var(--color-brand-600)] hover:underline" aria-label={`Corriger la journée du ${ptg.date}`}>Corriger</button>
                                                        )}
                                                    </Td>
                                                    <Td className="text-xs text-[var(--color-fg-muted)] py-2.5 px-3">
                                                        {ptg.jour}
                                                    </Td>

                                                    {/* Colonnes 3 à 9 : Fusionnées si Congé / Férié / Repos / Non Échu */}
                                                    {isMerged ? (
                                                        <Td colSpan={7} className="text-center py-2.5 px-3">
                                                            {isConge ? (
                                                                <div className="flex items-center justify-center gap-2 py-0.5">
                                                                    <span className="font-semibold text-xs text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)]">
                                                                        Congé payé
                                                                    </span>
                                                                    <span className="text-[11px] text-[var(--color-sky-600)] dark:text-[var(--color-sky-400)] font-sans">
                                                                        {isWeekendDay
                                                                            ? "· Jour non ouvré"
                                                                            : `· Absence légale (-${dureeJourNormale || '8'}h)`
                                                                        }
                                                                    </span>
                                                                </div>
                                                            ) : isFerie ? (
                                                                <div className="flex items-center justify-center gap-2 py-0.5">
                                                                    <span className="font-semibold text-xs text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)]">
                                                                        Férié chômé
                                                                    </span>
                                                                    <span className="text-[11px] text-[var(--color-purple-600)] dark:text-[var(--color-purple-400)] font-sans">
                                                                        {isWeekendDay
                                                                            ? "· Jour non ouvré"
                                                                            : `· Chômé payé (-${dureeJourNormale || '8'}h)`
                                                                        }
                                                                    </span>
                                                                </div>
                                                            ) : isRepos ? (
                                                                <span className="text-xs text-[var(--color-fg-muted)]">
                                                                    Repos hebdomadaire
                                                                </span>
                                                            ) : (
                                                                <span className="text-xs text-[var(--color-fg-subtle)] opacity-40">-</span>
                                                            )}
                                                        </Td>
                                                    ) : (
                                                        <>
                                                            <Td className="font-data text-xs text-center py-2.5 px-3">
                                                                <div>{ptg.entree}</div>
                                                            </Td>
                                                            {isContinu ? (
                                                                <Td colSpan={2} className="text-center py-2.5 px-2 bg-[var(--color-orange-500)]/[0.04]">
                                                                    <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-[var(--color-orange-700)] dark:text-[var(--color-orange-200)] bg-[var(--color-orange-500)]/15 px-2 py-0.5 rounded-none border border-[var(--color-orange-500)]/25" title="Journée continue sans pause">
                                                                        <FastForward size={11} className="shrink-0" /> Continu
                                                                    </span>
                                                                </Td>
                                                            ) : (
                                                                <>
                                                                    <Td className="font-data text-xs text-center py-2.5 px-3">
                                                                        {ptg.sortiePause}
                                                                    </Td>
                                                                    <Td className="font-data text-xs text-center py-2.5 px-3">
                                                                        {ptg.retourPause}
                                                                    </Td>
                                                                </>
                                                            )}
                                                            <Td className="font-data text-xs text-center py-2.5 px-3">
                                                                {ptg.sortie}
                                                            </Td>
                                                            <Td className="font-data text-xs text-center py-2.5 px-2">
                                                                {ptg.minutesRetard > 0 ? (
                                                                    <span className="font-semibold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] bg-[var(--color-warning-50)] dark:bg-[var(--color-warning-950)]/40 px-1.5 py-0.5 rounded-none border border-[var(--color-warning-200)] dark:border-[var(--color-warning-800)]/50" title={`Retard cumulé sur la journée : ${ptg.minutesRetard} min`}>
                                                                        {ptg.minutesRetard} min
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-[var(--color-fg-subtle)] opacity-40">-</span>
                                                                )}
                                                            </Td>
                                                            <Td className="font-data text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                                <div>{ptg.heures}</div>
                                                                {ptg.heures && ptg.heures !== '-' && ptg.heures !== '—' && ptg.heures !== '00h00' && (
                                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-data">
                                                                        ({ptg.totalHeuresDecimal != null ? ptg.totalHeuresDecimal.toFixed(2) : parseHhMmToDecimal(ptg.heures).toFixed(2)}h)
                                                                    </div>
                                                                )}
                                                            </Td>
                                                            <Td className="font-data text-xs text-center py-2.5 px-3">
                                                                <div>
                                                                    {ptg.ecart && ptg.ecart.startsWith('-') ? (
                                                                        <span className="text-[var(--color-danger-500)] dark:text-[var(--color-danger-400)] font-bold">
                                                                            {ptg.ecart}
                                                                        </span>
                                                                    ) : ptg.ecart && ptg.ecart.startsWith('+') ? (
                                                                        <span className="text-[var(--color-success-500)] dark:text-[var(--color-success-400)] font-bold">
                                                                            {ptg.ecart}
                                                                        </span>
                                                                    ) : (
                                                                        <span className="text-[var(--color-fg-muted)]">
                                                                            {ptg.ecart || '-'}
                                                                        </span>
                                                                    )}
                                                                </div>
                                                                {parseEcartToDecimal(ptg.ecart) && (
                                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-data">
                                                                        ({parseEcartToDecimal(ptg.ecart)})
                                                                    </div>
                                                                )}
                                                            </Td>
                                                        </>
                                                    )}

                                                    {/* Colonne 10 : Statut */}
                                                    <Td className="text-center py-2.5 px-3">
                                                        {getStatutBadge(ptg)}
                                                    </Td>

                                                    {dynamicAllowances.map((allowance) => {
                                                        const value = ptg.indemnites?.[allowance.code];
                                                        return (
                                                            <Td key={allowance.code} className="font-data text-xs text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/20 bg-[var(--color-orange-500)]/[0.04]" title={value?.accordee ? allowance.code : ''}>
                                                                {value?.accordee && Number(value.montant) > 0 ? `${Number(value.montant).toLocaleString('fr-FR')} Ar` : '-'}
                                                            </Td>
                                                        );
                                                    })}
                                                </Tr>
                                            );
                                        })
                                    )}
                                </Tbody>
                                {pointages.length > 0 && (
                                    <tfoot className="border-t-2 border-[var(--color-border)] bg-[var(--color-bg-subtle)]/70 font-bold">
                                        <tr>
                                            {/* Cols 1 à 6 : Date à Sortie */}
                                            <td colSpan={6} className="py-2.5 px-3 text-xs font-semibold text-[var(--color-fg-muted)]">
                                                Total période
                                            </td>

                                            {/* Col 7 : Retard Total */}
                                            <td className="font-data text-xs font-bold text-center py-2.5 px-2 text-[var(--color-fg)]">
                                                {totalRetardMinutes > 0 ? (
                                                    <span className="text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                        {totalRetardHhMm}
                                                        <span className="block text-[10px] font-normal font-sans opacity-75">
                                                            ({totalRetardMinutes} min)
                                                        </span>
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)] opacity-60">00h00</span>
                                                )}
                                            </td>

                                            {/* Col 8 : Heures Réalisées */}
                                            <td className="font-data text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                <div>{recap.tempsRealise || '00h00'}</div>
                                                {recap.tempsRealise && recap.tempsRealise !== '00h00' && (
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                                                        ({parseHhMmToDecimal(recap.tempsRealise).toFixed(2)}h)
                                                    </div>
                                                )}
                                            </td>

                                            {/* Col 9 : Écart Cumulé (vs 8h) */}
                                            <td className="font-data text-xs font-bold text-center py-2.5 px-3">
                                                <div className={displayEcart && displayEcart.startsWith('-') ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : displayEcart && displayEcart.startsWith('+') ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}>
                                                    {displayEcart || '-'}
                                                </div>
                                                {parseEcartToDecimal(displayEcart) && (
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-data">
                                                        ({parseEcartToDecimal(displayEcart)})
                                                    </div>
                                                )}
                                            </td>

                                            {/* Col 10 : Statut (Jours Travaillés) */}
                                            <td className="text-center py-2.5 px-3 text-xs font-semibold text-[var(--color-fg-muted)]">
                                                {kpis.joursTravailles ? `${kpis.joursTravailles} travaillés` : '-'}
                                            </td>

                                            {dynamicAllowances.map((allowance) => (
                                                <td key={allowance.code} className="font-data text-xs font-bold text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10">
                                                    {Math.round(Number(dynamicAllowanceTotals[allowance.code] || 0)).toLocaleString('fr-FR')} Ar
                                                </td>
                                            ))}
                                        </tr>
                                    </tfoot>
                                )}
                            </Table>
                        </div>
                    </div>

                    {ficheData?.soldePeriode && (
                        <Card padding bordered>
                            <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
                                <div>
                                    <h3 className="text-sm font-bold text-[var(--color-fg)]">Bilan final de la période</h3>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">Synthèse consolidée et incidence sur la paie.</p>
                                </div>
                                {ficheData.idPeriode && (
                                    <Button variant="outline" size="sm" onClick={() => setShowBalance(true)}>
                                        {actions.ADJUST_BALANCE ? 'Ajuster le solde de période' : 'Voir les régularisations'}
                                    </Button>
                                )}
                            </div>

                            {/* Rapprochement arithmétique direct du solde */}
                            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/50 p-3 mb-3">
                                <div className="text-[11px] font-semibold text-[var(--color-fg-muted)] mb-2">
                                    Rapprochement du solde
                                </div>
                                <div className="grid grid-cols-2 sm:grid-cols-[1fr_auto_1fr_auto_1fr_auto_1.2fr] items-center gap-2">
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 text-center">
                                        <div className="text-[10px] text-[var(--color-fg-muted)] truncate">HS hebdomadaires</div>
                                        <div className="font-data text-sm font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)] mt-0.5">
                                            {recap.hsTotalHebdo && recap.hsTotalHebdo !== '—' && recap.hsTotalHebdo !== '-' ? `+${recap.hsTotalHebdo}` : '00h00'}
                                        </div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                            ({formatHhMmWithDecimal(recap.hsTotalHebdo, recap.hsTotalHebdo && recap.hsTotalHebdo !== '00h00' ? '+' : '')})
                                        </div>
                                    </div>
                                    <div className="hidden sm:flex text-base font-bold text-[var(--color-fg-muted)]">−</div>
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 text-center">
                                        <div className="text-[10px] text-[var(--color-fg-muted)] truncate">Déficits restants</div>
                                        <div className={`font-data text-sm font-bold mt-0.5 ${recap.deficitHebdo && recap.deficitHebdo !== '00h00' && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg)]'}`}>
                                            {recap.deficitHebdo && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' ? recap.deficitHebdo : '00h00'}
                                        </div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                            ({formatHhMmWithDecimal(recap.deficitHebdo)})
                                        </div>
                                    </div>
                                    <div className="hidden sm:flex text-base font-bold text-[var(--color-fg-muted)]">+</div>
                                    <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 text-center">
                                        <div className="text-[10px] text-[var(--color-fg-muted)] truncate">Régularisation</div>
                                        <div className={`font-data text-sm font-bold mt-0.5 ${
                                            recap.ajustements && (recap.ajustements.startsWith('-') || recap.ajustements.startsWith('−')) && recap.ajustements !== '-00h00' && recap.ajustements !== '−00h00'
                                                ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'
                                                : recap.ajustements && recap.ajustements.startsWith('+') && recap.ajustements !== '+00h00'
                                                ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'
                                                : 'text-[var(--color-fg)]'
                                        }`}>
                                            {recap.ajustements && recap.ajustements !== '—' && recap.ajustements !== '-' ? recap.ajustements.replace('−', '-') : '00h00'}
                                        </div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                            ({formatHhMmWithDecimal(recap.ajustements)})
                                        </div>
                                    </div>
                                    <div className="hidden sm:flex text-base font-bold text-[var(--color-fg-muted)]">=</div>
                                    <div className="border-2 border-[var(--color-brand-500)]/40 bg-[var(--color-brand-500)]/5 p-2.5 text-center">
                                        <div className="text-[10px] font-semibold text-[var(--color-fg-muted)] truncate">Solde final</div>
                                        <div className={`font-data text-base sm:text-lg font-bold mt-0.5 ${String(recap.soldeFinal || '').startsWith('-') || String(recap.soldeFinal || '').startsWith('−') ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                                            {recap.soldeFinal || '-'}
                                        </div>
                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                            ({formatHhMmWithDecimal(recap.soldeFinal)})
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Deux volets côte à côte : Incidence Paie & Heures Qualifiées */}
                            <div className="grid grid-cols-1 lg:grid-cols-2 gap-3">
                                {/* Colonne 1 : Découpage pour la Paie */}
                                <div className="border border-[var(--color-border-subtle)] p-3">
                                    <h4 className="text-xs font-semibold text-[var(--color-fg-muted)] mb-2">Incidence sur la paie</h4>
                                    <div className="grid grid-cols-3 gap-2">
                                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-2 text-center">
                                            <div className="text-[10px] text-[var(--color-fg-muted)] truncate">Retenue absence</div>
                                            <div className={`font-data text-sm font-bold mt-1 ${recap.absenceFinale && recap.absenceFinale !== '00h00' && recap.absenceFinale !== '—' && recap.absenceFinale !== '-' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg)]'}`}>
                                                {recap.absenceFinale && recap.absenceFinale !== '—' && recap.absenceFinale !== '-' && recap.absenceFinale !== '00h00' ? `-${recap.absenceFinale}` : (recap.absenceFinale || '00h00')}
                                            </div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                                ({formatHhMmWithDecimal(recap.absenceFinale, recap.absenceFinale && recap.absenceFinale !== '00h00' ? '-' : '')})
                                            </div>
                                        </div>
                                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-2 text-center">
                                            <div className="text-[10px] text-[var(--color-fg-muted)] truncate">HS à 30 %</div>
                                            <div className={`font-data text-sm font-bold mt-1 ${recap.hs30Finales && recap.hs30Finales !== '00h00' && recap.hs30Finales !== '—' && recap.hs30Finales !== '-' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                                {recap.hs30Finales && recap.hs30Finales !== '—' && recap.hs30Finales !== '-' && recap.hs30Finales !== '00h00' ? `+${recap.hs30Finales}` : (recap.hs30Finales || '00h00')}
                                            </div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                                ({formatHhMmWithDecimal(recap.hs30Finales, recap.hs30Finales && recap.hs30Finales !== '00h00' ? '+' : '')})
                                            </div>
                                        </div>
                                        <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-2 text-center">
                                            <div className="text-[10px] text-[var(--color-fg-muted)] truncate">HS à 50 %</div>
                                            <div className={`font-data text-sm font-bold mt-1 ${recap.hs50Finales && recap.hs50Finales !== '00h00' && recap.hs50Finales !== '—' && recap.hs50Finales !== '-' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                                {recap.hs50Finales && recap.hs50Finales !== '—' && recap.hs50Finales !== '-' && recap.hs50Finales !== '00h00' ? `+${recap.hs50Finales}` : (recap.hs50Finales || '00h00')}
                                            </div>
                                            <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                                ({formatHhMmWithDecimal(recap.hs50Finales, recap.hs50Finales && recap.hs50Finales !== '00h00' ? '+' : '')})
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Colonne 2 : Heures Qualifiées */}
                                <div className="border border-[var(--color-border-subtle)] p-3">
                                    <h4 className="text-xs font-semibold text-[var(--color-fg-muted)] mb-2">Heures qualifiées sur la période</h4>
                                    <div className="grid grid-cols-3 gap-2">
                                        {[
                                            { label: 'Nuit', value: heuresQualifiees.nuit },
                                            { label: 'Dimanche', value: heuresQualifiees.dimanche },
                                            { label: 'Jour férié', value: heuresQualifiees.ferie },
                                        ].map(({ label, value }) => (
                                            <div key={label} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-2 text-center">
                                                <div className="text-[10px] text-[var(--color-fg-muted)]">{label}</div>
                                                <div className="font-data text-sm font-bold text-[var(--color-fg)] mt-1">
                                                    {formatHeuresMinutes(value)}
                                                </div>
                                                <div className="text-[10px] text-[var(--color-fg-muted)] font-data">
                                                    ({formatHeuresDecimal(value)})
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </div>

                            {recap.regleVentilation && (
                                <p className="text-[11px] text-[var(--color-fg-muted)] mt-2.5 font-data">
                                    {recap.regleVentilation}
                                </p>
                            )}

                            {recap.alerteSolde && <div className="mt-3"><Alert type="warning" message={recap.alerteSolde} /></div>}
                            {!ficheData.idPeriode && <p className="text-xs text-[var(--color-fg-muted)] mt-2">Sélectionnez une période de paie pour consulter ses régularisations.</p>}
                        </Card>
                    )}

                    {/* 3.1 Volet Déroulant : Bilan par Tronçon Hebdomadaire (Compensation 40h) */}
                    {ficheData?.bilansHebdo?.length > 0 && (
                        <details className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                            <summary className="cursor-pointer text-xs sm:text-sm font-semibold flex flex-wrap items-center justify-between gap-2 select-none">
                                <span className="flex items-center gap-2">
                                    <Scale size={16} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span className="text-[var(--color-fg)]">Détail du calcul par semaine</span>
                                </span>
                                <div className="flex flex-wrap items-center gap-2.5 font-data text-xs">
                                    <span className="text-[var(--color-fg-muted)] font-sans">{ficheData.bilansHebdo.length} sem.</span>
                                    <span className="text-[var(--color-border-subtle)]">·</span>
                                    <span className="flex items-center gap-1.5">
                                        <span className="text-[var(--color-fg-muted)] font-sans">HS hebdo :</span>
                                        <span className="font-bold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                            {recap.hsTotalHebdo && recap.hsTotalHebdo !== '—' && recap.hsTotalHebdo !== '-' ? `+${recap.hsTotalHebdo}` : '00h00'}
                                        </span>
                                    </span>
                                    <span className="text-[var(--color-border-subtle)]">·</span>
                                    <span className="flex items-center gap-1.5">
                                        <span className="text-[var(--color-fg-muted)] font-sans">Déficit hebdo :</span>
                                        <span className={`font-bold ${recap.deficitHebdo && recap.deficitHebdo !== '00h00' && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.deficitHebdo && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' && recap.deficitHebdo !== '00h00' ? `-${recap.deficitHebdo}` : '00h00'}
                                        </span>
                                    </span>
                                </div>
                            </summary>

                            {/* Bannière concise règle 40h */}
                            <div className="attendance-info p-2.5 text-xs flex items-center justify-between gap-2">
                                <span className="flex items-center gap-2">
                                    <Scale size={14} className="shrink-0 text-[var(--color-brand-500)]" />
                                    <span><strong>Compensation 40h/semaine :</strong> les surplus journaliers couvrent les journées courtes de la même semaine.</span>
                                </span>
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-data shrink-0 hidden sm:inline">
                                    Base légale {dureeHebdoLegale}h/sem.
                                </span>
                            </div>

                            <WeeklyPointageCalculationTable weeks={ficheData.bilansHebdo} pointages={pointages} />
                        </details>
                    )}

                    {/* 4. Volet de Synthèse : Paie, Indemnités & Règles (Déroulant unique avec triptyque synchronisé) */}
                    <details className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                        <summary className="cursor-pointer text-xs sm:text-sm font-semibold flex flex-wrap items-center justify-between gap-2 select-none">
                            <span className="flex items-center gap-2">
                                <Scale size={16} className="text-[var(--color-brand-500)] shrink-0" />
                                <span className="text-[var(--color-fg)]">Détail du bilan, indemnités et règles</span>
                            </span>
                            <div className="flex flex-wrap items-center gap-2.5 font-data text-xs">
                                <span className="flex items-center gap-1.5">
                                    <span className="text-[var(--color-fg-muted)] font-sans">Solde paie :</span>
                                    <span className={`font-bold ${recap.retardNet && recap.retardNet !== '00h00' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                        {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? `+${recap.heuresSuppNettes}` : '00h00'}
                                    </span>
                                </span>
                                <span className="text-[var(--color-border-subtle)]">·</span>
                                <span className="flex items-center gap-1.5">
                                    <span className="text-[var(--color-fg-muted)] font-sans">Indemnités de pointage :</span>
                                    <span className="font-bold text-[var(--color-orange-600)] dark:text-[var(--color-orange-400)]">
                                        {kpis.totalIndemnitesMois || '0 Ar'}
                                    </span>
                                </span>
                                <span className="text-[var(--color-border-subtle)]">·</span>
                                <span className="flex items-center gap-1.5">
                                    <span className="text-[var(--color-fg-muted)] font-sans">Assiduité :</span>
                                    <span className={`font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                        {isAssiduiteEligible ? 'Éligible' : 'Hors critère'}
                                    </span>
                                </span>
                            </div>
                        </summary>

                        {/* Contenu déroulé : Triptyque 3 colonnes synchronisé */}
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-3 pt-3 border-t border-[var(--color-border-subtle)] items-stretch">
                            {/* Carte 1 : Impact sur le Bulletin de Paie */}
                            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-3.5 flex flex-col justify-between h-full space-y-3">
                                <div>
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)] text-xs font-semibold text-[var(--color-fg)]">
                                        <span className="flex items-center gap-1.5">
                                            <FileText size={14} className="text-[var(--color-brand-500)] shrink-0" />
                                            Incidence sur la paie
                                        </span>
                                        <span className={`font-data text-xs font-bold ${recap.retardNet && recap.retardNet !== '00h00' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? `+${recap.heuresSuppNettes}` : '00h00'}
                                        </span>
                                    </div>
                                    <div className="pt-2 space-y-2 text-xs">
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Base contractuelle</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{recap.tempsTheorique || '-'}</span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Temps total réalisé</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{recap.tempsRealise || '00h00'}</span>
                                        </div>
                                        {/* Déficit hebdomadaire si présent */}
                                        {recap.deficitHebdo && recap.deficitHebdo !== '00h00' && recap.deficitHebdo !== '—' && recap.deficitHebdo !== '-' && (
                                            <div className="flex justify-between items-center">
                                                <span className="text-[var(--color-fg-muted)]">Déficit hebdomadaire</span>
                                                <span className="font-data font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                    -{recap.deficitHebdo}
                                                </span>
                                            </div>
                                        )}
                                        {/* Régularisation */}
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Régularisation</span>
                                            <span className={`font-data font-bold ${
                                                recap.ajustements && (recap.ajustements.startsWith('-') || recap.ajustements.startsWith('−')) && recap.ajustements !== '-00h00' && recap.ajustements !== '−00h00'
                                                    ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]'
                                                    : recap.ajustements && recap.ajustements.startsWith('+') && recap.ajustements !== '+00h00'
                                                    ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'
                                                    : 'text-[var(--color-fg-muted)]'
                                            }`}>
                                                {recap.ajustements && recap.ajustements !== '—' && recap.ajustements !== '-'
                                                    ? recap.ajustements.replace('−', '-')
                                                    : '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Retenue salaire (Déficit net)</span>
                                            <span className={`font-data font-bold ${recap.retardNet && recap.retardNet !== '00h00' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Heures sup. finales</span>
                                            <span className={`font-data font-bold ${recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                {recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? `+${recap.heuresSuppNettes}` : '00h00'}
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                {/* Bloc Réconciliation & Compensation Hebdomadaire */}
                                <div className="mt-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 space-y-1.5 text-xs">
                                    <div className="flex items-center justify-between text-[11px] font-semibold text-[var(--color-fg-muted)]">
                                        <span className="flex items-center gap-1">
                                            <Scale size={12} className="text-[var(--color-brand-500)]" />
                                            Compensation hebdo
                                        </span>
                                        <span className="text-[10px] text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-data font-bold">
                                            {recap.retardCompense && recap.retardCompense !== '00h00' ? `${recap.retardCompense} compensé` : ''}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Retards constatés</span>
                                        <span className={`font-data ${(kpis.retardCumule && kpis.retardCumule !== '00h00') ? 'text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] font-bold' : 'text-[var(--color-fg)]'}`}>
                                            {kpis.retardCumule || recap.retards || '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Surplus journalier</span>
                                        <span className="font-data text-[var(--color-fg)]">
                                            {recap.heuresSuppBrutes || '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center text-[var(--color-success-600)] dark:text-[var(--color-success-400)]">
                                        <span>Heures compensées</span>
                                        <span className="font-data font-bold">
                                            {recap.retardCompense && recap.retardCompense !== '00h00' ? `-${recap.retardCompense}` : '00h00'}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Carte 2 : Indemnités & Majorations du Mois */}
                            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-3.5 flex flex-col justify-between h-full space-y-3">
                                <div>
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)] text-xs font-semibold text-[var(--color-fg)]">
                                        <span className="flex items-center gap-1.5">
                                            <DollarSign size={14} className="text-[var(--color-orange-600)] shrink-0" />
                                            Indemnités de pointage & majorations
                                        </span>
                                        <span className="font-data text-xs font-bold text-[var(--color-orange-600)] dark:text-[var(--color-orange-400)]">
                                            {kpis.totalIndemnitesMois || '0 Ar'}
                                        </span>
                                    </div>
                                    <div className="pt-2 space-y-1.5 text-xs">
                                        {dynamicAllowances.length === 0 ? (
                                            <p className="text-xs text-[var(--color-fg-muted)] py-2">Aucune indemnité configurée.</p>
                                        ) : (
                                            dynamicAllowances.map((allowance) => (
                                                <div key={allowance.code} className="p-2 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex items-center justify-between gap-2">
                                                    <div className="min-w-0">
                                                        <div className="text-xs font-semibold text-[var(--color-fg)] truncate">{allowance.libelle || allowance.code}</div>
                                                        {allowance.periodicite && (
                                                            <div className="text-[10px] text-[var(--color-fg-muted)]">
                                                                {allowance.periodicite.replaceAll('_', ' ').toLowerCase()}
                                                            </div>
                                                        )}
                                                    </div>
                                                    <span className="font-data text-xs font-bold text-[var(--color-orange-600)] dark:text-[var(--color-orange-400)] shrink-0">
                                                        {Math.round(Number(dynamicAllowanceTotals[allowance.code] || 0)).toLocaleString('fr-FR')} Ar
                                                    </span>
                                                </div>
                                            ))
                                        )}
                                    </div>
                                </div>

                                {/* Majorations applicables encadrées de façon homogène */}
                                <div className="mt-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 space-y-1.5 text-xs">
                                    <div className="text-[11px] font-semibold text-[var(--color-fg-muted)]">
                                        Majorations applicables
                                    </div>

                                    {hasMajorations ? (
                                        <div className="flex flex-wrap gap-1.5 pt-0.5">
                                            {hs30Val > 0 && (
                                                <span className="px-2 py-0.5 text-[10.5px] font-data font-bold bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-500)]/20">
                                                    +{hs30Val}h à 30%
                                                </span>
                                            )}
                                            {hs50Val > 0 && (
                                                <span className="px-2 py-0.5 text-[10.5px] font-data font-bold bg-[var(--color-danger-500)]/10 text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)] border border-[var(--color-danger-500)]/20">
                                                    +{hs50Val}h à 50%
                                                </span>
                                            )}
                                            {nuitVal > 0 && (
                                                <span className="px-2 py-0.5 text-[10.5px] font-data font-bold bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] border border-[var(--color-brand-500)]/20">
                                                    +{nuitVal}h nuit
                                                </span>
                                            )}
                                            {dimVal > 0 && (
                                                <span className="px-2 py-0.5 text-[10.5px] font-data font-bold bg-[var(--color-info-500)]/10 text-[var(--color-info-700)] dark:text-[var(--color-info-300)] border border-[var(--color-info-500)]/20">
                                                    +{dimVal}h dimanche
                                                </span>
                                            )}
                                            {ferVal > 0 && (
                                                <span className="px-2 py-0.5 text-[10.5px] font-data font-bold bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-300)] border border-[var(--color-success-500)]/20">
                                                    +{ferVal}h férié
                                                </span>
                                            )}
                                        </div>
                                    ) : (
                                        <div className="text-[11px] text-[var(--color-fg-muted)] py-0.5">
                                            Aucune majoration sur cette période
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Carte 3 : Règles Appliquées & Assiduité */}
                            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 p-3.5 flex flex-col justify-between h-full space-y-3">
                                <div>
                                    <div className="flex items-center justify-between pb-2 border-b border-[var(--color-border-subtle)] text-xs font-semibold text-[var(--color-fg)]">
                                        <span className="flex items-center gap-1.5">
                                            <Info size={14} className="text-[var(--color-info-500)] shrink-0" />
                                            Règles & assiduité
                                        </span>
                                        <span className={`text-[10px] font-data font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                            {isAssiduiteEligible ? 'Éligible' : 'Hors critère'}
                                        </span>
                                    </div>
                                    <div className="pt-2 space-y-2 text-xs">
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Horaire journalier</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{regles.horaireJournalier || '-'}</span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Pause déjeuner</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{regles.pause || '-'}</span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Prise de poste théorique</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{regles.debutNormal || '-'}</span>
                                        </div>
                                        <div className="flex justify-between items-center">
                                            <span className="text-[var(--color-fg-muted)]">Heure min. comptabilisée</span>
                                            <span className="font-data font-bold text-[var(--color-fg)]">{regles.plafondArrivee || 'Désactivée'}</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Encadré Prime d'Assiduité */}
                                <div className="mt-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2.5 space-y-1.5 text-xs">
                                    <div className="flex items-center justify-between text-[11px] font-semibold">
                                        <span className="flex items-center gap-1 text-[var(--color-fg-muted)]">
                                            <Award size={12} className={isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'} />
                                            Prime d'assiduité
                                        </span>
                                        <span className={`text-[10px] font-data font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                            {isAssiduiteEligible ? 'Éligible' : 'Hors critère'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Seuil d'éligibilité</span>
                                        <span className="font-data font-bold text-[var(--color-fg)]">{seuilAssiduiteMinutes} min max (brut)</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Cumul retards bruts</span>
                                        <span className={`font-data font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                            {kpis.retardCumule || recap.retards || '00h00'}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <p className="text-[11px] text-[var(--color-fg-muted)] text-center pt-1 border-t border-[var(--color-border-subtle)]/60">
                            Les heures et indemnités sont calculées à partir des pointages enregistrés. Toute correction requiert une validation habilitée.
                        </p>
                    </details>
                </>
            ) : null}

            {correctionDate && ficheData?.idPeriode && <Suspense fallback={null}>
                <DafAjustementPointageModal isOpen onClose={() => setCorrectionDate(null)} employe={employe || ficheData} idEmploye={idEmploye} idPeriode={ficheData.idPeriode} initialDate={correctionDate} onSuccess={async () => { setCorrectionDate(null); await loadFiche({ background: true }); }} />
            </Suspense>}
            {showBalance && ficheData?.idPeriode && <Suspense fallback={null}>
                <PointageSoldePeriodeModal employe={ficheData.soldePeriode} idPeriodePaie={ficheData.idPeriode} readOnly={!actions.ADJUST_BALANCE} onClose={() => setShowBalance(false)} onChanged={() => loadFiche({ background: true })} />
            </Suspense>}

        </div>
    );
}
