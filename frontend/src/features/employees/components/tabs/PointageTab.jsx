import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { Link } from 'react-router-dom';
import {
    Clock,
    Printer,
    FileDown,
    FileSpreadsheet,
    FileText,
    TrendingUp,
    Info,
    ChevronLeft,
    ChevronRight,
    Calendar,
    Utensils,
    Bus,
    DollarSign,
    Moon,
    RotateCcw,
    ExternalLink,
    Scale,
    ChevronDown,
    ChevronUp,
    FastForward,
    AlertTriangle,
    Award,
    CheckCircle2,
    XCircle
} from 'lucide-react';
import attendanceService from '@/features/attendance/api/attendanceService';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import { useToast } from '@/components/ui/feedback/Toast';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Card } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { getTodayDateString } from '@/utils/dateUtils';
import { getPeriodeLabelForFilename } from '@/features/attendance/utils/attendanceDisplayUtils';

const PointageFichePrintModal = lazy(() => import('@/features/attendance/components/modals/PointageFichePrintModal'));

export default function PointageTab({ idEmploye, employe = null }) {
    // Contexte
    const { toast } = useToast();

    // État local : période, fiche, détail et export.
    const [periodView, setPeriodView] = useState('periode');
    const [selectedDate, setSelectedDate] = useState(() => getTodayDateString());
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
    const [periodesList, setPeriodesList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [ficheData, setFicheData] = useState(null);
    const [showHebdoDetail, setShowHebdoDetail] = useState(true);

    // États du dropdown d'export et de la modale PDF
    const [exportDropdownOpen, setExportDropdownOpen] = useState(false);
    const [isPrintModalOpen, setIsPrintModalOpen] = useState(false);
    const [exportingExcel, setExportingExcel] = useState(false);
    const dropdownRef = useRef(null);

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

    const formatHeuresMinutes = (val, withSign = false) => {
        if (val === null || val === undefined || isNaN(val)) return '00h00';
        const num = Number(val);
        const absVal = Math.abs(num);
        const hours = Math.floor(absVal);
        const minutes = Math.round((absVal - hours) * 60);
        const finalHours = minutes === 60 ? hours + 1 : hours;
        const finalMinutes = minutes === 60 ? 0 : minutes;

        const formatted = `${String(finalHours).padStart(2, '0')}h${String(finalMinutes).padStart(2, '0')}`;
        if (num < 0) return `-${formatted}`;
        if (withSign && num > 0) return `+${formatted}`;
        return formatted;
    };

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

    // 1. Charger la liste des périodes de paie officielles
    useEffect(() => {
        const fetchPeriodes = async () => {
            try {
                const data = await periodePaieService.getAllPeriodes();
                if (data && Array.isArray(data) && data.length > 0) {
                    setPeriodesList(data);
                    // Déterminer la période courante ou la plus récente
                    const todayStr = getTodayDateString();
                    const activeP = data.find(p => p.dateDebut <= todayStr && p.dateFin >= todayStr) || data[0];
                    if (activeP) {
                        setSelectedPeriodeId(activeP.idPeriodePaie);
                    }
                }
            } catch (err) {
                console.error('Erreur chargement liste des périodes de paie:', err);
            }
        };
        fetchPeriodes();
    }, []);

    const loadFiche = useCallback(async () => {
        if (!idEmploye) return;
        setLoading(true);
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
            setFicheData(res);
            if (res?.idPeriode && periodView === 'periode') {
                setSelectedPeriodeId(res.idPeriode);
            }
        } catch (err) {
            console.error('Erreur chargement fiche pointage employe:', err);
            setError('Impossible de charger les données de pointage pour cet employé.');
        } finally {
            setLoading(false);
        }
    }, [idEmploye, selectedDate, periodView, selectedPeriodeId]);

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

    const handleOpenPdfModal = () => {
        setExportDropdownOpen(false);
        setIsPrintModalOpen(true);
    };

    const handleExportExcel = async () => {
        setExportDropdownOpen(false);
        setExportingExcel(true);
        try {
            const params = {
                date: selectedDate,
                period: periodView
            };
            if (periodView === 'periode' && selectedPeriodeId) {
                params.idPeriode = selectedPeriodeId;
            }
            const blobData = await attendanceService.exportExcel(idEmploye, params);
            const blob = new Blob([blobData], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            });
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            const nom = employe?.nom || (ficheData?.nom ? ficheData.nom : (ficheData?.nomComplet ? ficheData.nomComplet.split(' ')[0] : 'Employe'));
            const prenom = employe?.prenom || (ficheData?.prenom ? ficheData.prenom : (ficheData?.nomComplet ? ficheData.nomComplet.split(' ').slice(1).join(' ') : ''));
            const pInitial = prenom ? prenom.trim().charAt(0).toUpperCase() : 'P';
            const matStr = ficheData?.matricule || employe?.matricule || idEmploye;
            const currentPeriode = periodesList.find(p => p.idPeriode === selectedPeriodeId);
            const perLibelle = getPeriodeLabelForFilename(ficheData?.libellePeriode || currentPeriode?.libelle, ficheData?.codePeriode || currentPeriode?.code);
            const cleanNom = nom.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-zA-Z0-9_-]/g, '_');
            link.download = `Pointage_${cleanNom}_${pInitial}_${matStr}_${perLibelle}.xlsx`;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
            toast({
                title: 'Export Excel réussi',
                message: 'Le fichier Excel (.xlsx) avec formules dynamiques a été téléchargé.',
                type: 'success'
            });
        } catch (err) {
            console.error('Erreur export Excel:', err);
            toast({
                title: 'Erreur d’exportation',
                message: 'Impossible de générer le fichier Excel de pointage.',
                type: 'error'
            });
        } finally {
            setExportingExcel(false);
        }
    };

    const getStatutBadge = (statut) => {
        switch (statut?.toUpperCase()) {
            case 'PRESENT':
                return <Badge variant="success" size="sm" className="font-bold">PRÉSENT</Badge>;
            case 'DEMI_JOURNEE':
                return <Badge variant="brand" size="sm" className="font-bold">DEMI-JOURNÉE</Badge>;
            case 'DISPENSE_POINTAGE':
                return <Badge variant="brand" size="sm" className="font-bold">AUTONOME</Badge>;
            case 'RETARD':
                return <Badge variant="warning" size="sm" className="font-bold">RETARD</Badge>;
            case 'CONGE':
                return <Badge variant="info" size="sm" className="font-bold">CONGÉ</Badge>;
            case 'FERIE_CHOME':
                return <Badge variant="neutral" size="sm" className="font-bold">FÉRIÉ CHÔMÉ</Badge>;
            case 'FERIE_TRAVAILLE':
                return <Badge variant="purple" size="sm" className="font-bold">FÉRIÉ +100%</Badge>;
            case 'SAMEDI_TRAVAILLE':
                return <Badge variant="brand" size="sm" className="font-bold">SAMEDI TRAVAILLÉ</Badge>;
            case 'DIMANCHE_TRAVAILLE':
                return <Badge variant="purple" size="sm" className="font-bold">DIMANCHE +40%</Badge>;
            case 'REPOS':
                return <Badge variant="neutral" size="sm" className="font-bold">REPOS</Badge>;
            case 'ABSENT':
            case 'ABSENCE_NON_JUSTIFIEE':
                return <Badge variant="danger" size="sm" className="font-bold">ABSENT</Badge>;
            case 'NON_CONCERNE':
                return <Badge variant="neutral" size="sm" className="text-[10px]">NON CONCERNÉ</Badge>;
            case 'PLANIFIE':
                return <Badge variant="neutral" size="sm" className="font-semibold text-[var(--color-fg-muted)]">PLANIFIÉ</Badge>;
            case 'NON_ECHU':
                return <span className="text-[11px] text-[var(--color-fg-subtle)]">—</span>;
            default:
                return <Badge variant="neutral" size="sm">{statut || '—'}</Badge>;
        }
    };

    const kpis = ficheData?.kpis || {};
    const recap = ficheData?.recap || {};
    const regles = ficheData?.reglesAppliquees || {};
    const pointages = ficheData?.pointagesData || [];

    const hs30Val = parseFloat(kpis?.heuresHs30) || 0;
    const hs50Val = parseFloat(kpis?.heuresHs50) || 0;
    const nuitVal = parseFloat(kpis?.heuresNuit) || 0;
    const dimVal = parseFloat(kpis?.heuresDimanche) || 0;
    const ferVal = parseFloat(kpis?.heuresFerie) || 0;
    const hasMajorations = (hs30Val > 0 || hs50Val > 0 || nuitVal > 0 || dimVal > 0 || ferVal > 0);
    const seuilAssiduiteMinutes = parseInt(kpis?.seuilAssiduiteMinutes || regles?.seuilAssiduiteMinutes, 10) || 45;
    const dureeHebdoLegale = regles?.dureeHebdoLegale || kpis?.dureeHebdoLegale || '40';
    const dureeJourNormale = Number(dureeHebdoLegale) > 0 ? Number(dureeHebdoLegale) / 5 : null;
    const isAssiduiteEligible = kpis?.primeAssiduiteEligible === 'true' ||
        (kpis?.primeAssiduiteEligible !== 'false' && (parseHhMmToDecimal(kpis?.retardCumule) * 60 < seuilAssiduiteMinutes));
    const hasRetardNet = Boolean(recap?.retardNet && recap.retardNet !== '00h00' && recap.retardNet !== '0h');
    const hasHsNettes = Boolean(recap?.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' && recap.heuresSuppNettes !== '0h');

    const totalRetardMinutes = pointages.reduce((acc, p) => acc + (p.minutesRetard || 0), 0);
    const totalRetardHhMm = kpis?.retardCumule || (totalRetardMinutes > 0 ? formatHeuresMinutes(totalRetardMinutes / 60) : '00h00');

    const totalEcartMinutes = pointages.reduce((acc, p) => {
        if (!p.ecart || p.ecart === '—' || p.ecart === '-') return acc;
        const clean = p.ecart.trim();
        if (clean.includes('h')) {
            const sign = clean.startsWith('-') ? -1 : 1;
            const parts = clean.replace(/[+-]/g, '').split('h');
            const h = parseInt(parts[0], 10) || 0;
            const m = parseInt(parts[1], 10) || 0;
            return acc + sign * (h * 60 + m);
        }
        return acc;
    }, 0);

    const computedEcartStr = formatHeuresMinutes(totalEcartMinutes / 60, true);
    const displayEcart = (recap?.ecartCumule && recap.ecartCumule !== '—') ? recap.ecartCumule : computedEcartStr;

    const workedDaysCount = pointages.filter(p => {
        const st = (p.statut || '').toUpperCase();
        return ['PRESENT', 'DISPENSE_POINTAGE', 'DEMI_JOURNEE', 'RETARD', 'SAMEDI_TRAVAILLE', 'DIMANCHE_TRAVAILLE', 'FERIE_TRAVAILLE'].includes(st);
    }).length;

    const totalTransportUnits = pointages.reduce((acc, p) => acc + (p.deplacementMatin ? 0.5 : 0) + (p.deplacementAm ? 0.5 : 0), 0);
    const totalRepasUnits = pointages.reduce((acc, p) => acc + (p.nbRepas || (p.indemniteRepas ? 1 : 0)), 0);

    const currIndexInList = periodesList.findIndex(p => p.idPeriodePaie === selectedPeriodeId);

    // Rendu
    return (
        <div className="space-y-6 print:m-0 print:p-0">
            {/* 1. Barre d'outils supérieure : Sélecteur de Période, Bascule Période/Mois/Semaine/Jour et Actions */}
            <Card padding bordered className="flex flex-col md:flex-row md:items-center justify-between gap-4 print:hidden">
                <div className="flex flex-wrap items-center gap-3">
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                            const todayStr = getTodayDateString();
                            setSelectedDate(todayStr);
                            const activeP = periodesList.find(p => p.dateDebut <= todayStr && p.dateFin >= todayStr);
                            if (activeP) setSelectedPeriodeId(activeP.idPeriodePaie);
                        }}
                        className="text-xs font-bold flex items-center gap-1.5"
                    >
                        <RotateCcw size={14} />
                        Aujourd'hui
                    </Button>

                    {/* Navigation temporelle selon le mode */}
                    {periodView === 'periode' ? (
                        <div className="inline-flex items-center bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                            <button
                                type="button"
                                onClick={() => handleNavigatePeriode(-1)}
                                disabled={currIndexInList < 0 || currIndexInList >= periodesList.length - 1}
                                className={`px-3 py-2 text-[var(--color-fg)] transition-colors cursor-pointer ${currIndexInList < 0 || currIndexInList >= periodesList.length - 1 ? 'opacity-40 cursor-not-allowed' : 'hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                title="Période précédente"
                            >
                                <ChevronLeft size={16} />
                            </button>
                            <span className="h-5 w-[1px] bg-[var(--color-border-subtle)]" />
                            <div className="flex items-center gap-2 px-3 py-1.5">
                                <Calendar size={15} className="text-[var(--color-brand-500)] shrink-0" />
                                <select
                                    value={selectedPeriodeId || ''}
                                    onChange={(e) => {
                                        const pId = parseInt(e.target.value, 10);
                                        setSelectedPeriodeId(pId);
                                        const found = periodesList.find(p => p.idPeriodePaie === pId);
                                        if (found?.dateDebut) setSelectedDate(found.dateDebut);
                                    }}
                                    className="bg-transparent text-sm font-bold text-[var(--color-fg)] border-none outline-none cursor-pointer pr-2"
                                >
                                    {periodesList.length === 0 ? (
                                        <option value="" disabled>Sélectionner une période...</option>
                                    ) : (
                                        <>
                                            {!selectedPeriodeId && (
                                                <option value="" disabled>Sélectionner une période...</option>
                                            )}
                                            {periodesList.map((p) => (
                                                <option key={p.idPeriodePaie} value={p.idPeriodePaie} className="bg-[var(--color-surface)] text-[var(--color-fg)] font-medium">
                                                    {p.libelle || p.code} ({p.dateDebut} au {p.dateFin})
                                                </option>
                                            ))}
                                        </>
                                    )}
                                </select>
                            </div>
                            <span className="h-5 w-[1px] bg-[var(--color-border-subtle)]" />
                            <button
                                type="button"
                                onClick={() => handleNavigatePeriode(1)}
                                disabled={currIndexInList <= 0}
                                className={`px-3 py-2 text-[var(--color-fg)] transition-colors cursor-pointer ${currIndexInList <= 0 ? 'opacity-40 cursor-not-allowed' : 'hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                title="Période suivante"
                            >
                                <ChevronRight size={16} />
                            </button>
                        </div>
                    ) : (
                        <PeriodNavigator
                            viewPeriod={periodView}
                            selectedDate={selectedDate}
                            onDateChange={(newDate) => setSelectedDate(newDate)}
                        />
                    )}

                    {/* Bascule Vue : PÉRIODE DE PAIE | MOIS | SEMAINE | JOUR */}
                    <div className="flex border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-0.5 rounded-none">
                        {[
                            { key: 'periode', label: 'Période de Paie' },
                            { key: 'mois', label: 'Mois Civil' },
                            { key: 'semaine', label: 'Semaine' },
                        ].map(({ key, label }) => (
                            <button
                                key={key}
                                type="button"
                                onClick={() => setPeriodView(key)}
                                className={`px-3 py-1 text-xs font-bold uppercase transition-colors cursor-pointer ${periodView === key
                                    ? 'bg-[var(--color-brand-500)] text-white shadow-xs'
                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                    }`}
                            >
                                {label}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="flex items-center gap-2 self-end md:self-auto">
                    <Link
                        to="/pointages?tab=saisie"
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] bg-[var(--color-brand-500)]/10 hover:bg-[var(--color-brand-500)]/20 border border-[var(--color-brand-500)]/30 transition-colors"
                        title="Ouvrir le module de saisie quotidienne pour toute l'équipe"
                    >
                        <span>Saisie d'équipe</span>
                        <ExternalLink size={13} />
                    </Link>
                    <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={handleOpenPdfModal}
                        className="text-xs font-bold flex items-center gap-1.5 cursor-pointer"
                    >
                        <Printer size={14} />
                        Imprimer
                    </Button>

                    {/* Menu Déroulant Exporter (Excel / PDF) */}
                    <div className="relative inline-block" ref={dropdownRef}>
                        <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            loading={exportingExcel}
                            onClick={() => setExportDropdownOpen(prev => !prev)}
                            className="text-xs font-bold flex items-center gap-1.5 shadow-xs cursor-pointer"
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
                                        <div className="text-[10px] text-[var(--color-fg-muted)]">Classeur avec formules actives</div>
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
                        <span className="text-xs font-bold text-[var(--color-fg)] uppercase tracking-wider">
                            {ficheData.libellePeriode || 'Période de présence'}
                        </span>
                        {ficheData.dateDebut && ficheData.dateFin && (
                            <span className="text-xs text-[var(--color-fg-muted)] font-mono">
                                (du {ficheData.dateDebut} au {ficheData.dateFin})
                            </span>
                        )}
                    </div>
                    {ficheData.statutPeriode && (
                        <Badge
                            variant={ficheData.statutPeriode === 'CLOTUREE' || ficheData.statutPeriode === 'VALIDEE' ? 'success' : 'warning'}
                            size="sm"
                            className="font-bold"
                        >
                            {ficheData.statutPeriode}
                        </Badge>
                    )}
                </div>
            )}

            {error && <Alert type="error" message={error} />}

            {loading ? (
                <div className="space-y-4">
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {[1, 2, 3, 4].map((i) => (
                            <div key={i} className="h-24 bg-[var(--color-bg-subtle)] animate-pulse" />
                        ))}
                    </div>
                    <SkeletonTable rows={10} cols={8} />
                </div>
            ) : (
                <>
                    {/* 2. Cartes d'indicateurs KPI réagencées selon les 3 Piliers (Présence, Gain Paie, Retenue Paie, Primes RH) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {/* KPI 1 : Présence & Temps Réalisé */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                                        <Clock size={14} className="text-[var(--color-brand-500)]" />
                                        Temps Travaillé
                                    </span>
                                    <Badge variant="brand" size="sm" className="font-mono font-bold">PRÉSENCE</Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className="text-2xl font-bold font-mono text-[var(--color-fg)]">
                                            {kpis.heuresRealisees || '00h00'}
                                        </span>
                                        <span className="text-xs font-mono text-[var(--color-fg-muted)] font-semibold">
                                            ({formatDecimalHours(parseHhMmToDecimal(kpis.heuresRealisees))})
                                        </span>
                                    </div>
                                    {kpis.heuresObjectif ? (
                                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 font-medium">
                                            objectif {kpis.heuresObjectif.replace(/^objectif\s*/i, '')}
                                        </p>
                                    ) : null}
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>{kpis.joursTravailles || '0 j'} travaillés</span>
                                <span>{kpis.joursPrevus || '0 prévus'}</span>
                            </div>
                        </div>

                        {/* KPI 2 : Heures Sup Nettes (Impact Paie Direct) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                                        <TrendingUp size={14} className="text-[var(--color-success-600)]" />
                                        Heures Sup. (Paie)
                                    </span>
                                    <Badge
                                        variant={hasHsNettes ? 'success' : 'neutral'}
                                        size="sm"
                                        className="font-mono font-bold"
                                    >
                                        {hasHsNettes ? 'NET PAYÉ' : '0 HS'}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-mono ${hasHsNettes ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                            {hasHsNettes ? `+${recap.heuresSuppNettes}` : '00h00'}
                                        </span>
                                        <span className="text-xs font-mono text-[var(--color-fg-muted)] font-semibold">
                                            ({hasHsNettes ? `+${formatDecimalHours(parseHhMmToDecimal(recap.heuresSuppNettes), false)}` : '0.00h'})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 font-medium truncate" title={`Surplus brut : ${recap.heuresSuppBrutes || '00h00'}, dont ${recap.retardCompense || '00h00'} absorbé en compensation`}>
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
                                <span>HS 30% : {kpis.heuresHs30 || '0.0h'}</span>
                                <span>HS 50% : {kpis.heuresHs50 || '0.0h'}</span>
                            </div>
                        </div>

                        {/* KPI 3 : Retenue sur Salaire (Déficit Net en Paie) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                                        <AlertTriangle size={14} className={hasRetardNet ? 'text-[var(--color-danger-500)]' : 'text-[var(--color-success-500)]'} />
                                        Retenue Salaire
                                    </span>
                                    <Badge
                                        variant={hasRetardNet ? 'danger' : 'success'}
                                        size="sm"
                                        className="font-mono font-bold"
                                    >
                                        {hasRetardNet ? 'DÉDUCTION PAIE' : '0 RETENUE'}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-mono ${hasRetardNet ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]'}`}>
                                            {hasRetardNet ? `-${recap.retardNet}` : '00h00'}
                                        </span>
                                        <span className="text-xs font-mono text-[var(--color-fg-muted)] font-semibold">
                                            ({hasRetardNet ? `-${formatDecimalHours(parseHhMmToDecimal(recap.retardNet), false)}` : '0.00h'})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-1 font-medium truncate" title={`Déficit d'heures sous le seuil hebdo : ${recap.retardNet || '00h00'} (après compensation intra-semaine)`}>
                                        {recap.retardCompense && recap.retardCompense !== '00h00' ? (
                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-medium">
                                                Heures compensées : <span className="font-bold">{recap.retardCompense}</span>
                                            </span>
                                        ) : hasRetardNet ? (
                                            'Déficit net sous le seuil hebdo déduit'
                                        ) : (
                                            'Aucun déficit horaire sur la période'
                                        )}
                                    </p>
                                </div>
                            </div>
                            <div className="mt-3 pt-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-subtle)] flex items-center justify-between">
                                <span>Déficit net : {recap.retardNet || '00h00'}</span>
                                <span className={recap.retardCompense && recap.retardCompense !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-bold' : ''}>
                                    Compensé : {recap.retardCompense || '00h00'}
                                </span>
                            </div>
                        </div>

                        {/* KPI 4 : Assiduité & Primes RH (Retard Brut) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden flex flex-col justify-between">
                            <div>
                                <div className="flex items-center justify-between">
                                    <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider flex items-center gap-1.5">
                                        <Award size={14} className={isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'} />
                                        Assiduité & Primes
                                    </span>
                                    <Badge
                                        variant={isAssiduiteEligible ? 'success' : 'warning'}
                                        size="sm"
                                        className="font-bold"
                                    >
                                        {isAssiduiteEligible ? 'ÉLIGIBLE' : 'HORS CRITÈRE'}
                                    </Badge>
                                </div>
                                <div className="mt-2.5">
                                    <div className="flex items-baseline gap-2">
                                        <span className={`text-2xl font-bold font-mono ${isAssiduiteEligible ? 'text-[var(--color-fg)]' : 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]'}`}>
                                            {kpis.retardCumule || '00h00'}
                                        </span>
                                        <span className="text-xs font-mono text-[var(--color-fg-muted)] font-semibold">
                                            brut
                                        </span>
                                    </div>
                                    <div className="mt-1 flex items-center gap-1 text-xs">
                                        {isAssiduiteEligible ? (
                                            <span className="text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-medium flex items-center gap-1">
                                                <CheckCircle2 size={12} /> Prime assiduité accordée
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
                                <span>Ponctualité brute</span>
                                <span>Max toléré : {seuilAssiduiteMinutes} min</span>
                            </div>
                        </div>
                    </div>

                    {/* 3. Tableau Détail Journalier (Pleine Largeur) */}
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
                        <div className="p-4 border-b border-[var(--color-border-subtle)] flex items-center justify-between">
                            <div>
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">Détail journalier</h3>
                                <p className="text-xs text-[var(--color-fg-muted)]">Pointages et calculs automatiques</p>
                            </div>
                        </div>

                        <div className="overflow-x-auto">
                            <Table>
                                <Thead>
                                    <Th className="text-xs font-bold uppercase py-3 px-3">Date</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3">Jour</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Entrée</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Sortie Pause</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Retour</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Sortie</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-2 text-center" title="Cumul des retards constatés sur la journée (matin et après-midi)">Retard</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center" title="Temps de travail effectif de la journée">Heures faites</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center" title="Différence par rapport aux 8h journalières : surplus (+) ou manque (-)">Écart (vs 8h)</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center" title="Statut de la journée (Présent, Congé, Férié, Repos...)">Statut</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-2 text-center border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10 font-mono tracking-wider" title="Indemnité transport / déplacement (1 = Aller-Retour, 0.5 = 1 trajet, — = aucun)">Transport</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-2 text-center border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10 font-mono tracking-wider" title="Panier repas accordé sur site">Repas</Th>
                                </Thead>
                                <Tbody>
                                    {pointages.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={12} className="text-center py-8 text-xs text-[var(--color-fg-muted)]">
                                                Aucune donnée de pointage pour cette période.
                                            </Td>
                                        </Tr>
                                    ) : (
                                        pointages.map((ptg, idx) => {
                                            const st = (ptg.statut || '').toUpperCase();
                                            const isConge = st === 'CONGE' || st === 'CONGE_PAYE';
                                            const isFerie = st === 'FERIE_CHOME' || (st === 'FERIE' && (!ptg.heures || ptg.heures === '00h00' || ptg.heures === '—'));
                                            const isRepos = st === 'REPOS';
                                            const isNonEchu = st === 'NON_ECHU';
                                            const isMerged = isConge || isFerie || isRepos || isNonEchu;

                                            const valDplt = (ptg.deplacementMatin ? 0.5 : 0) + (ptg.deplacementAm ? 0.5 : 0);
                                            const valRepas = ptg.nbRepas || (ptg.indemniteRepas ? 1 : 0);
                                            const isContinu = Boolean(
                                                ptg.horaireContinuJour ||
                                                ptg.horaireContinu ||
                                                (ptg.sortiePause === '12:00' && ptg.retourPause === '12:00')
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
                                                    <Td className="font-mono text-xs font-bold text-[var(--color-fg)] py-2.5 px-3">
                                                        {ptg.date}
                                                    </Td>
                                                    <Td className="text-xs text-[var(--color-fg-muted)] py-2.5 px-3">
                                                        {ptg.jour}
                                                    </Td>

                                                    {/* Colonnes 3 à 9 : Fusionnées si Congé / Férié / Repos / Non Échu */}
                                                    {isMerged ? (
                                                        <Td colSpan={7} className="text-center py-2.5 px-3">
                                                            {isConge ? (
                                                                <div className="flex items-center justify-center gap-2 py-0.5">
                                                                    <span className="font-bold text-xs text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)] uppercase tracking-wide">
                                                                        Congé Payé
                                                                    </span>
                                                                    <span className="text-[11px] text-[var(--color-sky-600)] dark:text-[var(--color-sky-400)] font-sans">
                                                                        · Jour d'absence légale (Objectif hebdomadaire abaissé de {dureeJourNormale || 8}h)
                                                                    </span>
                                                                </div>
                                                            ) : isFerie ? (
                                                                <div className="flex items-center justify-center gap-2 py-0.5">
                                                                    <span className="font-bold text-xs text-[var(--color-purple-700)] dark:text-[var(--color-purple-300)] uppercase tracking-wide">
                                                                        Férié Chômé Légal
                                                                    </span>
                                                                    <span className="text-[11px] text-[var(--color-purple-600)] dark:text-[var(--color-purple-400)] font-sans">
                                                                        · Jour chômé payé (Objectif hebdomadaire abaissé de {dureeJourNormale || 8}h)
                                                                    </span>
                                                                </div>
                                                            ) : isRepos ? (
                                                                <span className="text-xs text-[var(--color-fg-subtle)] ">
                                                                    Repos hebdomadaire
                                                                </span>
                                                            ) : (
                                                                <span className="text-xs text-[var(--color-fg-subtle)] opacity-40">—</span>
                                                            )}
                                                        </Td>
                                                    ) : (
                                                        <>
                                                            <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                                <div>{ptg.entree}</div>
                                                            </Td>
                                                            {isContinu ? (
                                                                <Td colSpan={2} className="text-center py-2.5 px-2 bg-[var(--color-orange-500)]/[0.04]">
                                                                    <span className="inline-flex items-center gap-1.5 text-[11px] font-medium text-[var(--color-orange-700)] dark:text-[var(--color-orange-200)] bg-[var(--color-orange-500)]/15 px-2 py-0.5 rounded border border-[var(--color-orange-500)]/25" title="Journée continue sans pause">
                                                                        <FastForward size={11} className="shrink-0" /> Continu
                                                                    </span>
                                                                </Td>
                                                            ) : (
                                                                <>
                                                                    <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                                        {ptg.sortiePause}
                                                                    </Td>
                                                                    <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                                        {ptg.retourPause}
                                                                    </Td>
                                                                </>
                                                            )}
                                                            <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                                {ptg.sortie}
                                                            </Td>
                                                            <Td className="font-mono text-xs text-center py-2.5 px-2">
                                                                {ptg.minutesRetard > 0 ? (
                                                                    <span className="font-semibold text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)] bg-[var(--color-danger-50)] dark:bg-[var(--color-danger-950)]/40 px-1.5 py-0.5 rounded border border-[var(--color-danger-200)] dark:border-[var(--color-danger-800)]/50" title={`Retard cumulé sur la journée : ${ptg.minutesRetard} min`}>
                                                                        {ptg.minutesRetard} min
                                                                    </span>
                                                                ) : (
                                                                    <span className="text-[var(--color-fg-subtle)] opacity-40">—</span>
                                                                )}
                                                            </Td>
                                                            <Td className="font-mono text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                                <div>{ptg.heures}</div>
                                                                {ptg.heures && ptg.heures !== '—' && ptg.heures !== '00h00' && (
                                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-mono">
                                                                        ({ptg.totalHeuresDecimal != null ? ptg.totalHeuresDecimal.toFixed(2) : parseHhMmToDecimal(ptg.heures).toFixed(2)}h)
                                                                    </div>
                                                                )}
                                                            </Td>
                                                            <Td className="font-mono text-xs text-center py-2.5 px-3">
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
                                                                        {ptg.ecart}
                                                                    </span>
                                                                )}
                                                            </Td>
                                                        </>
                                                    )}

                                                    {/* Colonne 10 : Statut */}
                                                    <Td className="text-center py-2.5 px-3">
                                                        {getStatutBadge(ptg.statut)}
                                                    </Td>

                                                    {/* Colonne 11 : DPLT (Déplacement : 1, 0.5 ou 0) */}
                                                    <Td className="font-mono text-xs text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/20 bg-[var(--color-orange-500)]/[0.04]">
                                                        {valDplt > 0 ? (
                                                            <span className="font-bold text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)]" title={`Déplacement : ${valDplt === 1 ? 'Matin + Soir (1)' : ptg.deplacementMatin ? 'Matin (0.5)' : 'Soir (0.5)'}`}>
                                                                {valDplt}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] opacity-40">
                                                                {isRepos || isConge || isFerie ? '—' : '0'}
                                                            </span>
                                                        )}
                                                    </Td>

                                                    {/* Colonne 12 : REPAS (Paniers repas : 1, 2 ou 0) */}
                                                    <Td className="font-mono text-xs text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/20 bg-[var(--color-orange-500)]/[0.04]">
                                                        {valRepas > 0 ? (
                                                            <span className="font-bold text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)]" title={`Repas : ${valRepas} ${valRepas > 1 ? 'repas' : 'repas'}`}>
                                                                {valRepas}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] opacity-40">
                                                                {isRepos || isConge || isFerie ? '—' : '0'}
                                                            </span>
                                                        )}
                                                    </Td>
                                                </Tr>
                                            );
                                        })
                                    )}
                                </Tbody>
                                {pointages.length > 0 && (
                                    <tfoot className="border-t-2 border-[var(--color-border)] bg-[var(--color-bg-subtle)]/70 font-bold">
                                        <tr>
                                            {/* Cols 1 à 6 : Date à Sortie */}
                                            <td colSpan={6} className="py-2.5 px-3 text-xs uppercase font-bold text-[var(--color-fg-muted)]">
                                                Total Période
                                            </td>

                                            {/* Col 7 : Retard Total */}
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-2 text-[var(--color-fg)]">
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
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                <div>{recap.tempsRealise || '00h00'}</div>
                                                {recap.tempsRealise && recap.tempsRealise !== '00h00' && (
                                                    <div className="text-[10px] text-[var(--color-fg-muted)] font-normal">
                                                        ({parseHhMmToDecimal(recap.tempsRealise).toFixed(2)}h)
                                                    </div>
                                                )}
                                            </td>

                                            {/* Col 9 : Écart Cumulé (vs 8h) */}
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-3">
                                                <span className={displayEcart && displayEcart.startsWith('-') ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : displayEcart && displayEcart.startsWith('+') ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}>
                                                    {displayEcart || '—'}
                                                </span>
                                            </td>

                                            {/* Col 10 : Statut (Jours Travaillés) */}
                                            <td className="text-center py-2.5 px-3 text-xs font-bold text-[var(--color-fg-muted)]">
                                                {kpis.joursTravailles ? `${kpis.joursTravailles} travaillés` : `${workedDaysCount} j travaillés`}
                                            </td>

                                            {/* Col 11 : Transport */}
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10">
                                                <div>{totalTransportUnits}</div>
                                                {kpis.montantDeplacementTotal ? (
                                                    <div className="text-[9.5px] font-sans font-normal text-[var(--color-orange-600)] dark:text-[var(--color-orange-300)]">
                                                        {kpis.montantDeplacementTotal}
                                                    </div>
                                                ) : null}
                                            </td>

                                            {/* Col 12 : Repas */}
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-2 border-l border-[var(--color-orange-500)]/30 text-[var(--color-orange-700)] dark:text-[var(--color-orange-400)] bg-[var(--color-orange-500)]/10">
                                                <div>{totalRepasUnits}</div>
                                                {kpis.montantRepasTotal ? (
                                                    <div className="text-[9.5px] font-sans font-normal text-[var(--color-orange-600)] dark:text-[var(--color-orange-300)]">
                                                        {kpis.montantRepasTotal}
                                                    </div>
                                                ) : null}
                                            </td>
                                        </tr>
                                    </tfoot>
                                )}
                            </Table>
                        </div>
                    </div>

                    {/* 3.1 Volet Déroulant : Bilan par Tronçon Hebdomadaire (Compensation 40h - ) */}
                    {showHebdoDetail && ficheData?.bilansHebdo && ficheData.bilansHebdo.length > 0 && (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-2.5">
                                <div className="flex items-center gap-2">
                                    <Scale size={16} className="text-[var(--color-brand-500)] shrink-0" />
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                        Décomposition Hebdomadaire &amp; Compensation
                                    </h4>
                                </div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-mono">
                                    Base légale {dureeHebdoLegale}h/semaine · Compensation intra-semaine
                                </span>
                            </div>

                            {/* Bannière explicative règle légale */}
                            <div className="attendance-info p-2.5 text-xs flex items-center gap-2">
                                <Scale size={15} className="shrink-0" />
                                <div>
                                    <strong>Règle {dureeHebdoLegale}h/semaine :</strong> les heures au-delà de {dureeJourNormale || 'la durée prévue'}h par jour compensent d&apos;abord les heures manquantes de la semaine. L&apos;objectif est ajusté pour les congés et jours fériés. Le solde devient une <span className="font-semibold text-[var(--color-success-700)] dark:text-[var(--color-success-300)]">heure supplémentaire</span> ou une <span className="font-semibold text-[var(--color-danger-700)] dark:text-[var(--color-danger-300)]">retenue sur salaire</span>.
                                </div>
                            </div>

                            <div className="overflow-x-auto">
                                <Table>
                                    <Thead>
                                        <Th className="text-xs py-2.5 px-3 font-bold uppercase">Semaine</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Jours</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase" title="Heures à réaliser sur la semaine (abaissé pour congés et fériés)">Objectif semaine</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase" title="Total des heures effectivement travaillées">Heures faites</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]" title="Cumul des retards constatés sur la semaine (matin et après-midi)">Retards</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase text-[var(--color-info-700)] dark:text-[var(--color-info-200)]" title="Heures réalisées au-delà de la durée normale d'une journée">Heures en plus</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase text-[var(--color-success-600)] dark:text-[var(--color-success-400)]" title="Surplus d'un jour ayant comblé le manque d'un autre jour de la même semaine">Compensé</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase" title="Conséquence nette sur le bulletin de paie">Impact Paie</Th>
                                    </Thead>
                                    <Tbody>
                                        {ficheData.bilansHebdo.map((b, idx) => {
                                            const hasDeficit = b.heuresDeficit > 0;
                                            const hasHs = b.heuresHsNettes > 0;
                                            return (
                                                <Tr key={idx}>
                                                    <Td className="font-mono text-xs font-bold py-2 px-3">
                                                        Semaine {b.numeroSemaine || idx + 1} ({b.debutSemaine} au {b.finSemaine})
                                                    </Td>
                                                    <Td className="text-xs text-center py-2 px-3">{b.nbJoursTroncon} j</Td>
                                                    <Td className="font-mono text-xs text-center py-2 px-3">
                                                        <div className="font-bold">{formatHeuresMinutes(b.seuilHeuresTroncon)}</div>
                                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-mono">({formatDecimalHours(b.seuilHeuresTroncon)})</div>
                                                        {(b.nbJoursConge > 0 || b.nbJoursFerie > 0) && (
                                                            <div className="flex flex-wrap items-center justify-center gap-1 mt-1">
                                                                {b.nbJoursConge > 0 && (
                                                                    <span className="text-[9px] px-1 py-0.5 rounded bg-[var(--color-sky-50)] dark:bg-[var(--color-sky-950)]/40 text-[var(--color-sky-700)] dark:text-[var(--color-sky-300)] border border-[var(--color-sky-200)] font-sans font-medium" title={`${b.nbJoursConge}j de congé déduit(s) du seuil attendu`}>
                                                                        -{b.nbJoursConge}j congé
                                                                    </span>
                                                                )}
                                                                {b.nbJoursFerie > 0 && (
                                                                    <span className="text-[9px] px-1 py-0.5 rounded bg-[var(--color-warning-50)] dark:bg-[var(--color-warning-950)]/40 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-200)] font-sans font-medium" title={`${b.nbJoursFerie}j férié chômé non dû`}>
                                                                        -{b.nbJoursFerie}j férié
                                                                    </span>
                                                                )}
                                                            </div>
                                                        )}
                                                    </Td>
                                                    <Td className="font-mono text-xs text-center font-bold py-2 px-3">
                                                        <div>{formatHeuresMinutes(b.totalHeuresTravaillees)}</div>
                                                        <div className="text-[10px] text-[var(--color-fg-muted)] font-normal font-mono">({formatDecimalHours(b.totalHeuresTravaillees)})</div>
                                                    </Td>
                                                    <Td className={`attendance-duration text-xs text-center py-2 px-3 ${b.heuresRetard > 0 ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold' : 'text-[var(--color-fg-muted)] opacity-50'}`}>
                                                        {b.heuresRetard > 0 ? (
                                                            <div>
                                                                <div>{formatHeuresMinutes(b.heuresRetard)}</div>
                                                                <div className="text-[9.5px] opacity-70 font-normal">({formatDecimalHours(b.heuresRetard)})</div>
                                                            </div>
                                                        ) : '—'}
                                                    </Td>
                                                    <Td className="attendance-duration text-xs text-center py-2 px-3 font-semibold text-[var(--color-info-700)] dark:text-[var(--color-info-200)]" title={b.heuresHsBrutes > 0 ? `${formatHeuresMinutes(b.heuresHsBrutes)} au-delà de la durée normale d'une journée` : 'Aucune heure en plus cette semaine'}>
                                                        {b.heuresHsBrutes > 0 ? (
                                                            <div>
                                                                <div>+{formatHeuresMinutes(b.heuresHsBrutes)}</div>
                                                                <div className="text-[9.5px] opacity-70 font-normal">(+{formatDecimalHours(b.heuresHsBrutes)})</div>
                                                            </div>
                                                        ) : <span className="opacity-40 font-normal">—</span>}
                                                    </Td>
                                                    <Td className="attendance-duration text-xs text-center py-2 px-3 font-semibold text-[var(--color-success-600)] dark:text-[var(--color-success-400)]" title={b.heuresCompensees > 0 ? `${formatHeuresMinutes(b.heuresCompensees)} de surplus ont comblé un manque de cette semaine` : 'Aucune compensation requise'}>
                                                        {b.heuresCompensees > 0 ? (
                                                            <div>
                                                                <div>{formatHeuresMinutes(b.heuresCompensees)}</div>
                                                                <div className="text-[9.5px] opacity-70 font-normal">({formatDecimalHours(b.heuresCompensees)})</div>
                                                            </div>
                                                        ) : (
                                                            <span className="opacity-40 font-normal">—</span>
                                                        )}
                                                    </Td>
                                                    <Td className="text-center py-2 px-3">
                                                        {hasDeficit ? (
                                                            <Badge variant="danger" size="sm" className="font-mono font-bold">
                                                                Retenue : -{formatHeuresMinutes(b.heuresDeficit)} (-{formatDecimalHours(b.heuresDeficit)})
                                                            </Badge>
                                                        ) : hasHs ? (
                                                            <Badge variant="success" size="sm" className="font-mono font-bold">
                                                                Heures Sup : +{formatHeuresMinutes(b.heuresHsNettes)} (+{formatDecimalHours(b.heuresHsNettes)})
                                                            </Badge>
                                                        ) : (
                                                            <Badge variant="neutral" size="sm" className="font-mono text-[10px]">
                                                                Semaine validée (0h)
                                                            </Badge>
                                                        )}
                                                    </Td>
                                                </Tr>
                                            );
                                        })}
                                    </Tbody>
                                </Table>
                            </div>
                        </div>
                    )}

                    {/* 4. Grille de Synthèse Mensuelle, Indemnités & Règles (Pied de page modulaire aéré) */}
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 items-stretch pt-2">
                        {/* Carte 1 : Impact sur le Bulletin de Paie (Miroir exact des rubriques de paie) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col justify-between space-y-3">
                            <div>
                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-1.5">
                                        <FileText size={14} className="text-[var(--color-brand-500)]" />
                                        Impact Bulletin de Paie
                                    </h4>
                                    {ficheData?.bilansHebdo && ficheData.bilansHebdo.length > 0 && (
                                        <button
                                            type="button"
                                            onClick={() => setShowHebdoDetail(prev => !prev)}
                                            className="text-[11px] font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] hover:underline flex items-center gap-0.5 cursor-pointer"
                                            title="Afficher ou masquer la décomposition par semaine"
                                        >
                                            <Scale size={12} />
                                            <span>{showHebdoDetail ? 'Masquer tronçons' : 'Détail hebdo'}</span>
                                            {showHebdoDetail ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                                        </button>
                                    )}
                                </div>
                                <div className="space-y-2 text-xs pt-2">
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Base contractuelle (Théorique)</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsTheorique || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Temps total réalisé</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsRealise || '00h00'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Retenue salaire (Déficit net)</span>
                                        <span className={`font-mono font-bold ${recap.retardNet && recap.retardNet !== '00h00' ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.retardNet && recap.retardNet !== '00h00' ? `-${recap.retardNet}` : '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Heures sup. nettes payées</span>
                                        <span className={`font-mono font-bold ${recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? `+${recap.heuresSuppNettes}` : '00h00'}
                                        </span>
                                    </div>

                                    {/* Bloc Réconciliation & Compensation Hebdomadaire */}
                                    <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/70 p-2.5 space-y-1.5">
                                        <div className="flex items-center justify-between text-[10.5px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                            <span className="flex items-center gap-1">
                                                <Scale size={12} className="text-[var(--color-brand-500)]" />
                                                Réconciliation &amp; Terrain
                                            </span>
                                            <span className="text-[10px] text-[var(--color-success-600)] dark:text-[var(--color-success-400)] font-mono font-bold">
                                                {recap.retardCompense && recap.retardCompense !== '00h00' ? `${recap.retardCompense} compensé` : ''}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs" title="Total des retards constatés sur la période (matin et après-midi)">
                                            <span className="text-[var(--color-fg-muted)]">Retards constatés</span>
                                            <span className={`font-mono ${(kpis.retardCumule && kpis.retardCumule !== '00h00') ? 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)] font-bold' : 'text-[var(--color-fg)]'}`}>
                                                {kpis.retardCumule || recap.retards || '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs" title="Heures travaillées au-delà de 8h/jour dans les semaines">
                                            <span className="text-[var(--color-fg-muted)]">Heures en plus sur la journée</span>
                                            <span className="font-mono text-[var(--color-fg)]">
                                                {recap.heuresSuppBrutes || '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs text-[var(--color-success-600)] dark:text-[var(--color-success-400)]" title="Surplus absorbés pour combler les heures manquantes de la même semaine">
                                            <span>Heures utilisées pour compenser</span>
                                            <span className="font-mono font-bold">
                                                {recap.retardCompense && recap.retardCompense !== '00h00' ? `-${recap.retardCompense}` : '00h00'}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Carte 2 : Indemnités & Majorations du Mois (Relevé structuré & aéré) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col justify-between space-y-3">
                            <div>
                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2.5">
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-1.5">
                                        <DollarSign size={14} className="text-[var(--color-orange-600)]" />
                                        Indemnités &amp; Majorations
                                    </h4>
                                    <span className="font-mono text-sm font-black text-[var(--color-orange-600)] dark:text-[var(--color-orange-400)]">
                                        {kpis.totalIndemnitesMois || kpis.montantRepasTotal || '0 Ar'}
                                    </span>
                                </div>

                                <div className="space-y-2.5 pt-2.5">
                                    {/* 1. Tuile Panier Repas */}
                                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1.5">
                                        <div className="flex items-center justify-between gap-2">
                                            <div className="flex items-center gap-2 min-w-0">
                                                <div className="w-6 h-6 rounded flex items-center justify-center bg-[var(--color-orange-500)]/10 text-[var(--color-orange-600)] shrink-0">
                                                    <Utensils size={13} />
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="text-xs font-bold text-[var(--color-fg)]">Panier Repas</div>
                                                    <div className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                        {kpis.totalRepas || '0 repas'} validés{kpis.tarifRepas ? ` · ${kpis.tarifRepas} / repas` : ''}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="text-right shrink-0">
                                                <span className="font-mono text-sm font-bold text-[var(--color-orange-600)] dark:text-[var(--color-orange-400)]">
                                                    {kpis.montantRepasTotal || '0 Ar'}
                                                </span>
                                            </div>
                                        </div>

                                        {/* Détail 2e Repas si existant */}
                                        {recap.repas2eDetail && (
                                            <div className="text-[10.5px] text-purple-700 dark:text-purple-300 pt-1 border-t border-[var(--color-border-subtle)] flex items-center justify-between font-mono">
                                                <span className="flex items-center gap-1"><Moon size={10} /> 2ᵉ Repas (Nuit / HS)</span>
                                                <span className="font-bold">{recap.repas2eDetail}</span>
                                            </div>
                                        )}
                                    </div>

                                    {/* 2. Tuile Transport & Déplacement */}
                                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1">
                                        <div className="flex items-center justify-between gap-2">
                                            <div className="flex items-center gap-2 min-w-0">
                                                <div className="w-6 h-6 rounded flex items-center justify-center bg-sky-500/10 text-sky-600 shrink-0">
                                                    <Bus size={13} />
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="text-xs font-bold text-[var(--color-fg)]">Transport &amp; Déplacement</div>
                                                    <div className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                        {kpis.totalTrajets || '0 trajet'} (AR) · Prorata {kpis.nbJoursOuvrables || 21} j ouvrables{kpis.nbJoursFerieChome > 0 ? ` (${kpis.nbJoursFerieChome} j férié déduit)` : ''}
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="text-right shrink-0">
                                                <span className="font-mono text-sm font-bold text-sky-600">
                                                    {kpis.montantDeplacementTotal || '0 Ar'}
                                                </span>
                                            </div>
                                        </div>
                                    </div>

                                    {/* 3. Majorations applicables */}
                                    <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-1">
                                        <div className="text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                            Majorations applicables
                                        </div>

                                        {hasMajorations ? (
                                            <div className="flex flex-wrap gap-1.5 pt-0.5">
                                                {hs30Val > 0 && (
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)] border border-[var(--color-warning-500)]/20">
                                                        +{hs30Val}h à 30%
                                                    </span>
                                                )}
                                                {hs50Val > 0 && (
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-orange-500/10 text-orange-700 dark:text-orange-300 border border-orange-500/20">
                                                        +{hs50Val}h à 50%
                                                    </span>
                                                )}
                                                {nuitVal > 0 && (
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-500/20">
                                                        +{nuitVal}h nuit
                                                    </span>
                                                )}
                                                {dimVal > 0 && (
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-500/20">
                                                        +{dimVal}h dimanche
                                                    </span>
                                                )}
                                                {ferVal > 0 && (
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-[var(--color-success-500)]/10 text-[var(--color-success-700)] dark:text-[var(--color-success-300)] border border-[var(--color-success-500)]/20">
                                                        +{ferVal}h férié
                                                    </span>
                                                )}
                                            </div>
                                        ) : (
                                            <div className="text-[11px] text-[var(--color-fg-muted)]">
                                                Aucune majoration sur cette période
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Carte 3 : Règles Appliquées & Assiduité (RH) */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col justify-between space-y-3">
                            <div className="space-y-2.5">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                    <Info size={14} className="text-[var(--color-info-500)]" />
                                    Règles Appliquées &amp; Assiduité
                                </h4>
                                <div className="space-y-1.5 text-xs">
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Horaire journalier</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.horaireJournalier || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Pause déjeuner</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.pause || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Prise de poste théorique</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.debutNormal || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Heure d'arrivée maximale</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.plafondArrivee || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Tolérance de retard</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.toleranceRetard != null ? regles.toleranceRetard : '—'}</span>
                                    </div>
                                </div>

                                {/* Encadré Prime d'Assiduité */}
                                <div className="mt-2 pt-2 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/70 p-2.5 space-y-1.5">
                                    <div className="flex items-center justify-between text-[10.5px] font-bold uppercase tracking-wider">
                                        <span className="flex items-center gap-1 text-[var(--color-fg-muted)]">
                                            <Award size={12} className={isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'} />
                                            Prime d'Assiduité
                                        </span>
                                        <span className={`text-[10px] font-mono font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                            {isAssiduiteEligible ? '✓ ÉLIGIBLE' : '✕ HORS CRITÈRE'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center text-xs">
                                        <span className="text-[var(--color-fg-muted)]">Seuil d'éligibilité</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{seuilAssiduiteMinutes} min max (brut)</span>
                                    </div>
                                    <div className="flex justify-between items-center text-xs">
                                        <span className="text-[var(--color-fg-muted)]">Cumul retards bruts</span>
                                        <span className={`font-mono font-bold ${isAssiduiteEligible ? 'text-[var(--color-success-600)]' : 'text-[var(--color-warning-600)]'}`}>
                                            {kpis.retardCumule || recap.retards || '00h00'}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Note d'observation intégrée */}
                            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-2.5 text-[11px] text-[var(--color-fg-muted)] leading-relaxed mt-1">
                                <span className="font-bold text-[var(--color-fg)] block text-[10px] uppercase tracking-wider mb-0.5">Observations</span>
                                Les heures et indemnités sont calculées à partir des pointages enregistrés. Toute correction doit être justifiée et validée par le responsable habilité.
                            </div>
                        </div>
                    </div>
                </>
            )}

            {/* Modale d'aperçu officiel et d'impression PDF format A4 (Lazy Loaded) */}
            {isPrintModalOpen && (
                <Suspense fallback={null}>
                    <PointageFichePrintModal
                        isOpen={isPrintModalOpen}
                        onClose={() => setIsPrintModalOpen(false)}
                        employe={employe}
                        ficheData={ficheData}
                    />
                </Suspense>
            )}
        </div>
    );
}
