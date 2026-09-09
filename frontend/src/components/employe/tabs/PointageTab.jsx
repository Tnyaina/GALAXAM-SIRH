import { useState, useEffect, useCallback } from 'react';
import { Link } from 'react-router-dom';
import {
    Clock,
    Printer,
    FileDown,
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
    CheckCircle2,
    ChevronDown,
    ChevronUp
} from 'lucide-react';
import pointageEmployeService from '@/api/pointage/pointageEmployeService';
import periodePaieService from '@/services/periodePaieService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import { getTodayDateString } from '@/utils/dateUtils';

export default function PointageTab({ idEmploye }) {
    const [periodView, setPeriodView] = useState('periode');
    const [selectedDate, setSelectedDate] = useState(() => getTodayDateString());
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
    const [periodesList, setPeriodesList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [ficheData, setFicheData] = useState(null);
    const [showHebdoDetail, setShowHebdoDetail] = useState(true);

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
            const res = await pointageEmployeService.getFicheEmploye(idEmploye, params);
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

    const handlePrint = () => {
        window.print();
    };

    const handleExportPdf = () => {
        window.print();
    };

    const getStatutBadge = (statut) => {
        switch (statut?.toUpperCase()) {
            case 'PRESENT':
                return <Badge variant="success" size="sm" className="font-bold">PRÉSENT</Badge>;
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
                return <Badge variant="neutral" size="sm" className="font-semibold text-neutral-600 dark:text-neutral-300">PLANIFIÉ</Badge>;
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

    const currIndexInList = periodesList.findIndex(p => p.idPeriodePaie === selectedPeriodeId);

    return (
        <div className="space-y-6 print:m-0 print:p-0">
            {/* 1. Barre d'outils supérieure : Sélecteur de Période, Bascule Période/Mois/Semaine/Jour et Actions */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-4 print:hidden">
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
                            { key: 'jour', label: 'Jour' }
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
                        onClick={handlePrint}
                        className="text-xs font-bold flex items-center gap-1.5"
                    >
                        <Printer size={14} />
                        Imprimer
                    </Button>
                    <Button
                        type="button"
                        variant="primary"
                        size="sm"
                        onClick={handleExportPdf}
                        className="text-xs font-bold flex items-center gap-1.5 shadow-xs"
                    >
                        <FileDown size={14} />
                        Exporter PDF
                    </Button>
                </div>
            </div>

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
                    {/* 2. Cartes d'indicateurs KPI (Heures réalisées, Retard, Heures sup, Absences) */}
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
                        {/* KPI 1 : Heures Réalisées */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                    Heures Réalisées
                                </span>
                                <Clock size={18} className="text-[var(--color-brand-500)]" />
                            </div>
                            <div className="mt-2">
                                <span className="text-2xl font-bold font-mono text-[var(--color-fg)]">
                                    {kpis.heuresRealisees || '00h00'}
                                </span>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                    {kpis.heuresObjectif || 'objectif 168h00'}
                                </p>
                            </div>
                        </div>

                        {/* KPI 2 : Retard Cumulé & Compensation */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                    Retard Cumulé
                                </span>
                                <div className={`w-2.5 h-2.5 rounded-full ${kpis.retardCumule && kpis.retardCumule !== '00h00' && kpis.retardCumule !== '0h' ? 'bg-[var(--color-warning-500)]' : 'bg-[var(--color-border)]'}`} />
                            </div>
                            <div className="mt-2">
                                <span className={`text-2xl font-bold font-mono ${kpis.retardCumule && kpis.retardCumule !== '00h00' && kpis.retardCumule !== '0h' ? 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' : 'text-[var(--color-fg)]'}`}>
                                    {kpis.retardCumule || '00h00'}
                                </span>
                                <div className="text-xs text-[var(--color-fg-muted)] mt-0.5 truncate" title={recap.retardCompense && recap.retardCompense !== '00h00' ? `${recap.retardCompense} compensé · ${recap.retardNet || recap.heuresDeficit} déduit en paie` : undefined}>
                                    {recap.retardCompense && recap.retardCompense !== '00h00' ? (
                                        <span className="text-emerald-600 dark:text-emerald-400 font-medium">
                                            {recap.retardCompense} compensé · <span className="text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-bold">{recap.retardNet || recap.heuresDeficit} déduit</span>
                                        </span>
                                    ) : (
                                        kpis.retardOccurrences || '0 occurrence'
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* KPI 3 : Heures Supplémentaires Nettes Payées */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                    Heures Sup. Nettes
                                </span>
                                <div className={`w-2.5 h-2.5 rounded-full ${kpis.heuresSupp && kpis.heuresSupp !== '00h00' && kpis.heuresSupp !== '0h' ? 'bg-[var(--color-success-500)]' : 'bg-[var(--color-border)]'}`} />
                            </div>
                            <div className="mt-2">
                                <span className={`text-2xl font-bold font-mono ${kpis.heuresSupp && kpis.heuresSupp !== '00h00' && kpis.heuresSupp !== '0h' ? 'text-[var(--color-success-500)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                    {kpis.heuresSupp || '00h00'}
                                </span>
                                <div className="text-xs text-[var(--color-fg-muted)] mt-0.5 truncate">
                                    {recap.heuresSuppBrutes && recap.heuresSuppBrutes !== '00h00' && recap.heuresSuppBrutes !== kpis.heuresSupp ? (
                                        <span className="text-[var(--color-info-600)] dark:text-[var(--color-info-300)]" title={`Surplus brut : ${recap.heuresSuppBrutes}, absorbé à hauteur de ${recap.retardCompense} par les retards`}>
                                            Surplus brut : {recap.heuresSuppBrutes}
                                        </span>
                                    ) : (
                                        kpis.heuresSuppStatut || 'à valider'
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* KPI 4 : Absences & Congés */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                    Absences & Congés
                                </span>
                                <div className="w-2.5 h-2.5 rounded-full bg-[var(--color-border)]" />
                            </div>
                            <div className="mt-2">
                                <span className="text-2xl font-bold font-mono text-[var(--color-fg)]">
                                    {kpis.absences || '00 j'}
                                </span>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                    {kpis.absencesStatut || 'aucune'}
                                </p>
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
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Heures</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Écart</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-3 text-center">Statut</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-2 text-center border-l border-amber-500/30 text-amber-700 dark:text-amber-400 bg-amber-500/10 font-mono tracking-wider" title="Déplacement (trajets ou journées : 1, 0.5, 0)">DPLT</Th>
                                    <Th className="text-xs font-bold uppercase py-3 px-2 text-center border-l border-amber-500/30 text-amber-700 dark:text-amber-400 bg-amber-500/10 font-mono tracking-wider" title="Paniers repas (1, 2, 0)">REPAS</Th>
                                </Thead>
                                <Tbody>
                                    {pointages.length === 0 ? (
                                        <Tr>
                                            <Td colSpan={11} className="text-center py-8 text-xs text-[var(--color-fg-muted)]">
                                                Aucune donnée de pointage pour cette période.
                                            </Td>
                                        </Tr>
                                    ) : (
                                        pointages.map((ptg, idx) => {
                                            const isWeekend = ptg.statut === 'REPOS';
                                            const valDplt = (ptg.deplacementMatin ? 0.5 : 0) + (ptg.deplacementAm ? 0.5 : 0);
                                            const isContinu = Boolean(
                                                ptg.horaireContinuJour || 
                                                ptg.horaireContinu || 
                                                (ptg.sortiePause === '12:00' && ptg.retourPause === '12:00')
                                            );

                                            return (
                                                <Tr
                                                    key={`${ptg.dateComplete || ptg.date}-${idx}`}
                                                    className={isWeekend ? 'bg-[var(--color-bg-subtle)]/40' : ''}
                                                >
                                                    <Td className="font-mono text-xs font-bold text-[var(--color-fg)] py-2.5 px-3">
                                                        {ptg.date}
                                                    </Td>
                                                    <Td className="text-xs text-[var(--color-fg-muted)] py-2.5 px-3">
                                                        {ptg.jour}
                                                    </Td>
                                                    <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                        {ptg.entree}
                                                    </Td>
                                                    {isContinu ? (
                                                        <Td colSpan={2} className="text-center py-2.5 px-2 bg-amber-500/[0.04]">
                                                            <span className="inline-flex items-center gap-1 text-[11px] font-medium text-amber-800 dark:text-amber-200 bg-amber-500/15 px-2 py-0.5 rounded border border-amber-500/25" title="Journée continue sans pause">
                                                                ⚡ Continu
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
                                                    <Td className="font-mono text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                        {ptg.heures}
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
                                                    <Td className="text-center py-2.5 px-3">
                                                        {getStatutBadge(ptg.statut)}
                                                    </Td>

                                                    {/* Colonne DPLT (Déplacement : 1, 0.5 ou 0) - Surlignée en Orange */}
                                                    <Td className="font-mono text-xs text-center py-2.5 px-2 border-l border-amber-500/20 bg-amber-500/[0.04]">
                                                        {valDplt > 0 ? (
                                                            <span className="font-bold text-amber-700 dark:text-amber-400" title={`Déplacement : ${valDplt === 1 ? 'Matin + Soir (1)' : ptg.deplacementMatin ? 'Matin (0.5)' : 'Soir (0.5)'}`}>
                                                                {valDplt}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] opacity-40">
                                                                {isWeekend ? '—' : '0'}
                                                            </span>
                                                        )}
                                                    </Td>

                                                    {/* Colonne REPAS (Paniers repas : 1, 2 ou 0) - Surlignée en Orange */}
                                                    <Td className="font-mono text-xs text-center py-2.5 px-2 border-l border-amber-500/20 bg-amber-500/[0.04]">
                                                        {valRepas > 0 ? (
                                                            <span className="font-bold text-amber-700 dark:text-amber-400" title={`Repas : ${valRepas} ${valRepas > 1 ? 'repas' : 'repas'}`}>
                                                                {valRepas}
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-fg-subtle)] opacity-40">
                                                                {isWeekend ? '—' : '0'}
                                                            </span>
                                                        )}
                                                    </Td>
                                                </Tr>
                                            );
                                        })
                                    )}
                                </Tbody>
                                {pointages.length > 0 && (
                                    <tfoot className="border-t-2 border-[var(--color-border)] bg-[var(--color-bg-subtle)]/60 font-bold">
                                        <tr>
                                            <td colSpan={6} className="py-2.5 px-3 text-xs uppercase font-bold text-[var(--color-fg-muted)]">
                                                Total Période
                                            </td>
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-3 text-[var(--color-fg)]">
                                                {recap.tempsRealise || '—'}
                                            </td>
                                            <td className="font-mono text-xs text-center py-2.5 px-3 text-[var(--color-fg-muted)]">
                                                {recap.ecartCumule || '—'}
                                            </td>
                                            <td className="text-center py-2.5 px-3 text-xs font-bold text-[var(--color-fg-muted)]">
                                                {pointages.filter(p => p.statut === 'PRESENT' || p.statut === 'DISPENSE_POINTAGE').length} j ouvrés
                                            </td>
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-2 border-l border-amber-500/30 text-amber-700 dark:text-amber-400 bg-amber-500/10">
                                                {pointages.reduce((acc, p) => acc + (p.deplacementMatin ? 0.5 : 0) + (p.deplacementAm ? 0.5 : 0), 0)}
                                            </td>
                                            <td className="font-mono text-xs font-bold text-center py-2.5 px-2 border-l border-amber-500/30 text-amber-700 dark:text-amber-400 bg-amber-500/10">
                                                {pointages.reduce((acc, p) => acc + (p.nbRepas || (p.indemniteRepas ? 1 : 0)), 0)}
                                            </td>
                                        </tr>
                                    </tfoot>
                                )}
                            </Table>
                        </div>
                    </div>

                    {/* 3.1 Volet Déroulant : Bilan par Tronçon Hebdomadaire (Compensation 40h - RG-PTG-05) */}
                    {showHebdoDetail && ficheData?.bilansHebdo && ficheData.bilansHebdo.length > 0 && (
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[var(--color-border-subtle)] pb-2.5">
                                <div className="flex items-center gap-2">
                                    <Scale size={16} className="text-[var(--color-brand-500)] shrink-0" />
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                        Décomposition Hebdomadaire &amp; Compensation 1:1 (Règle RG-PTG-05)
                                    </h4>
                                </div>
                                <span className="text-[11px] text-[var(--color-fg-muted)] font-mono">
                                    Base 40h/semaine · Surplus brut compense retards
                                </span>
                            </div>
                            <div className="overflow-x-auto">
                                <Table>
                                    <Thead>
                                        <Th className="text-xs py-2.5 px-3 font-bold uppercase">Tronçon Hebdo</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Jours Ovr.</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Seuil (40h)</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Réalisé</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Retards</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Surplus Brut</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase text-emerald-600">Compensé (1:1)</Th>
                                        <Th className="text-xs py-2.5 px-3 text-center font-bold uppercase">Solde Net</Th>
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
                                                    <Td className="font-mono text-xs text-center py-2 px-3">{formatHeuresMinutes(b.seuilHeuresTroncon)}</Td>
                                                    <Td className="font-mono text-xs text-center font-bold py-2 px-3">{formatHeuresMinutes(b.totalHeuresTravaillees)}</Td>
                                                    <Td className={`font-mono text-xs text-center py-2 px-3 ${b.heuresRetard > 0 ? 'text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)] font-bold' : 'text-[var(--color-fg-muted)]'}`}>
                                                        {b.heuresRetard > 0 ? formatHeuresMinutes(b.heuresRetard) : '00h00'}
                                                    </Td>
                                                    <Td className={`font-mono text-xs text-center py-2 px-3 ${b.heuresHsBrutes > 0 ? 'text-[var(--color-info-600)] dark:text-[var(--color-info-300)] font-bold' : 'text-[var(--color-fg-muted)]'}`}>
                                                        {b.heuresHsBrutes > 0 ? formatHeuresMinutes(b.heuresHsBrutes, true) : '00h00'}
                                                    </Td>
                                                    <Td className="font-mono text-xs text-center py-2 px-3 font-bold text-emerald-600 dark:text-emerald-400">
                                                        {b.heuresCompensees > 0 ? `-${formatHeuresMinutes(b.heuresCompensees)}` : '—'}
                                                    </Td>
                                                    <Td className="text-center py-2 px-3">
                                                        {hasDeficit ? (
                                                            <Badge variant="danger" size="sm" className="font-mono font-bold">
                                                                -{formatHeuresMinutes(b.heuresDeficit)} RETENUE
                                                            </Badge>
                                                        ) : hasHs ? (
                                                            <Badge variant="success" size="sm" className="font-mono font-bold">
                                                                +{formatHeuresMinutes(b.heuresHsNettes)} NETTES
                                                            </Badge>
                                                        ) : (
                                                            <Badge variant="neutral" size="sm" className="font-mono text-[10px]">
                                                                ÉQUILIBRÉ (00h00)
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
                        {/* Carte 1 : Temps de Présence & Bilan Compensation */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col justify-between space-y-3">
                            <div>
                                <div className="flex items-center justify-between border-b border-[var(--color-border-subtle)] pb-2">
                                    <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-1.5">
                                        <TrendingUp size={14} className="text-[var(--color-brand-500)]" />
                                        Temps de Présence
                                    </h4>
                                    {ficheData?.bilansHebdo && ficheData.bilansHebdo.length > 0 && (
                                        <button
                                            type="button"
                                            onClick={() => setShowHebdoDetail(prev => !prev)}
                                            className="text-[11px] font-bold text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] hover:underline flex items-center gap-0.5 cursor-pointer"
                                            title="Afficher ou masquer la décomposition par semaine"
                                        >
                                            <Scale size={12} />
                                            <span>{showHebdoDetail ? 'Masquer tronçons' : 'Détail 40h'}</span>
                                            {showHebdoDetail ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
                                        </button>
                                    )}
                                </div>
                                <div className="space-y-1.5 text-xs pt-2">
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Temps théorique</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsTheorique || '168h00'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Temps réalisé</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{recap.tempsRealise || '00h00'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Temps d'absence</span>
                                        <span className={`font-mono ${recap.tempsAbsence && recap.tempsAbsence !== '00h00' && recap.tempsAbsence !== '0h' ? 'font-bold text-[var(--color-danger-500)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.tempsAbsence || '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Retards constatés</span>
                                        <span className={`font-mono ${recap.retards && recap.retards !== '00h00' && recap.retards !== '0h' ? 'font-bold text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.retards || '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Surplus brut (HS brutes)</span>
                                        <span className={`font-mono ${recap.heuresSuppBrutes && recap.heuresSuppBrutes !== '00h00' ? 'font-bold text-[var(--color-info-600)] dark:text-[var(--color-info-300)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.heuresSuppBrutes || recap.heuresSupp || '00h00'}
                                        </span>
                                    </div>

                                    {/* Bloc Compensation Hebdomadaire RG-PTG-05 */}
                                    <div className="mt-2.5 pt-2 border-t border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/70 p-2.5 rounded-none space-y-1.5">
                                        <div className="flex items-center justify-between text-[10.5px] font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                            <span className="flex items-center gap-1">
                                                <Scale size={12} className="text-[var(--color-brand-500)]" />
                                                Compensation Hebdo
                                            </span>
                                            <span className="text-[10px] text-[var(--color-fg-subtle)] font-normal font-mono"></span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs">
                                            <span className="text-[var(--color-fg-muted)]">Retard compensé</span>
                                            <span className="font-mono font-bold text-emerald-600 dark:text-emerald-400">
                                                {recap.retardCompense && recap.retardCompense !== '00h00' ? `-${recap.retardCompense}` : '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs">
                                            <span className="text-[var(--color-fg-muted)]">Déficit net (Retenue Paie)</span>
                                            <span className={`font-mono font-bold ${recap.retardNet && recap.retardNet !== '00h00' ? 'text-[var(--color-danger-500)] dark:text-[var(--color-danger-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                                {recap.retardNet || recap.heuresDeficit || '00h00'}
                                            </span>
                                        </div>
                                        <div className="flex justify-between items-center text-xs pt-1 border-t border-[var(--color-border-subtle)]/60">
                                            <span className="font-semibold text-[var(--color-fg)]">Heures sup. nettes payées</span>
                                            <span className={`font-mono font-bold ${recap.heuresSuppNettes && recap.heuresSuppNettes !== '00h00' ? 'text-[var(--color-success-600)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                                {recap.heuresSuppNettes || recap.heuresSupp || '00h00'}
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
                                        <DollarSign size={14} className="text-emerald-600" />
                                        Indemnités du Mois
                                    </h4>
                                    <span className="font-mono text-sm font-black text-emerald-600">
                                        {kpis.totalIndemnitesMois || kpis.montantRepasTotal || '0 Ar'}
                                    </span>
                                </div>

                                <div className="space-y-2.5 pt-2.5">
                                    {/* 1. Tuile Panier Repas */}
                                    <div className="p-2.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] space-y-1.5">
                                        <div className="flex items-center justify-between gap-2">
                                            <div className="flex items-center gap-2 min-w-0">
                                                <div className="w-6 h-6 rounded flex items-center justify-center bg-emerald-500/10 text-emerald-600 shrink-0">
                                                    <Utensils size={13} />
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="text-xs font-bold text-[var(--color-fg)]">Panier Repas</div>
                                                    <div className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                                        {kpis.totalRepas || '0 repas'} validés · Tarif : 2 000 Ar / repas
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="text-right shrink-0">
                                                <span className="font-mono text-sm font-bold text-emerald-600">
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
                                                        {kpis.totalTrajets || '0 trajet'} (AR) · Prorata 21 j ouvrables
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
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-amber-500/10 text-amber-700 dark:text-amber-300 border border-amber-500/20">
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
                                                    <span className="px-2 py-0.5 text-[10.5px] font-mono font-bold bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/20">
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

                        {/* Carte 3 : Règles Appliquées & Observations */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col justify-between space-y-3">
                            <div className="space-y-2.5">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                    <Info size={14} className="text-[var(--color-info-500)]" />
                                    Règles Appliquées
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
                                        <span className="text-[var(--color-fg-muted)]">Heure minimale d'arrivée</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.plafondArrivee || '—'}</span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Tolérance de retard</span>
                                        <span className="font-mono font-bold text-[var(--color-fg)]">{regles.toleranceRetard != null ? regles.toleranceRetard : '—'}</span>
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
        </div>
    );
}
