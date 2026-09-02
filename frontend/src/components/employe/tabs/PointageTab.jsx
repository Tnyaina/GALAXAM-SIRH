import { useState, useEffect, useCallback } from 'react';
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
    RotateCcw
} from 'lucide-react';
import pointageEmployeService from '@/api/pointage/pointageEmployeService';
import periodePaieService from '@/services/periodePaieService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import PeriodNavigator from '@/components/ui/navigation/PeriodNavigator';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';

export default function PointageTab({ idEmploye }) {
    const [periodView, setPeriodView] = useState('periode');
    const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().split('T')[0]);
    const [selectedPeriodeId, setSelectedPeriodeId] = useState(null);
    const [periodesList, setPeriodesList] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [ficheData, setFicheData] = useState(null);

    // 1. Charger la liste des périodes de paie officielles
    useEffect(() => {
        const fetchPeriodes = async () => {
            try {
                const data = await periodePaieService.getAllPeriodes();
                if (data && Array.isArray(data) && data.length > 0) {
                    setPeriodesList(data);
                    // Déterminer la période courante ou la plus récente
                    const todayStr = new Date().toISOString().split('T')[0];
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
                            setSelectedDate(new Date().toISOString().split('T')[0]);
                            const todayStr = new Date().toISOString().split('T')[0];
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
                                disabled={currIndexInList >= periodesList.length - 1}
                                className={`px-3 py-2 text-[var(--color-fg)] transition-colors cursor-pointer ${currIndexInList >= periodesList.length - 1 ? 'opacity-40 cursor-not-allowed' : 'hover:bg-[var(--color-bg-subtle)]'
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
                                    {periodesList.map((p) => (
                                        <option key={p.idPeriodePaie} value={p.idPeriodePaie} className="bg-[var(--color-surface)] text-[var(--color-fg)] font-medium">
                                            {p.libelle || p.code} ({p.dateDebut} au {p.dateFin})
                                        </option>
                                    ))}
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

                        {/* KPI 2 : Retard Cumulé */}
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
                                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                    {kpis.retardOccurrences || '0 occurrence'}
                                </p>
                            </div>
                        </div>

                        {/* KPI 3 : Heures Supplémentaires */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs relative overflow-hidden">
                            <div className="flex items-center justify-between">
                                <span className="text-xs font-bold text-[var(--color-fg-muted)] uppercase tracking-wider">
                                    Heures Supplémentaires
                                </span>
                                <div className={`w-2.5 h-2.5 rounded-full ${kpis.heuresSupp && kpis.heuresSupp !== '00h00' && kpis.heuresSupp !== '0h' ? 'bg-[var(--color-success-500)]' : 'bg-[var(--color-border)]'}`} />
                            </div>
                            <div className="mt-2">
                                <span className={`text-2xl font-bold font-mono ${kpis.heuresSupp && kpis.heuresSupp !== '00h00' && kpis.heuresSupp !== '0h' ? 'text-[var(--color-success-500)] dark:text-[var(--color-success-400)]' : 'text-[var(--color-fg)]'}`}>
                                    {kpis.heuresSupp || '00h00'}
                                </span>
                                <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                    {kpis.heuresSuppStatut || 'à valider'}
                                </p>
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

                    {/* 3. Grille Principale (Détail journalier à gauche + Panneaux récapitulatifs à droite) */}
                    <div className="grid grid-cols-1 lg:grid-cols-4 gap-6 items-start">
                        {/* A. Tableau Détail Journalier (3 colonnes de large) */}
                        <div className="lg:col-span-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs">
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
                                        <Th className="text-xs font-bold uppercase py-3 px-3 text-center border-l border-[var(--color-border-subtle)]">Indemnités</Th>
                                    </Thead>
                                    <Tbody>
                                        {pointages.length === 0 ? (
                                            <Tr>
                                                <Td colSpan={10} className="text-center py-8 text-xs text-[var(--color-fg-muted)]">
                                                    Aucune donnée de pointage pour cette période.
                                                </Td>
                                            </Tr>
                                        ) : (
                                            pointages.map((ptg, idx) => {
                                                const isWeekend = ptg.statut === 'REPOS';
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
                                                        <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                            {ptg.sortiePause}
                                                        </Td>
                                                        <Td className="font-mono text-xs text-center py-2.5 px-3">
                                                            {ptg.retourPause}
                                                        </Td>
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

                                                        {/* Colonne Indemnités Reçues (Repas, 2e Repas, Déplacement) */}
                                                        <Td className="py-2.5 px-3 border-l border-[var(--color-border-subtle)] text-center">
                                                            <div className="flex items-center justify-center gap-1.5 flex-wrap">
                                                                {ptg.indemniteRepas ? (
                                                                    <span
                                                                        className={`inline-flex items-center gap-1 px-1.5 py-0.5 text-[10.5px] font-bold rounded ${ptg.nbRepas === 2
                                                                                ? 'bg-purple-500/10 text-purple-700 dark:text-purple-300 border border-purple-500/30'
                                                                                : 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border border-emerald-500/30'
                                                                            }`}
                                                                        title={ptg.nbRepas === 2 ? '2 Repas (Travail prolongé / Nuit)' : `1 Repas standard (${ptg.montantRepas ? Math.round(ptg.montantRepas) + ' Ar' : '2 000 Ar'})`}
                                                                    >
                                                                        <Utensils size={10} />
                                                                        <span>{ptg.nbRepas === 2 ? 'Repas x2' : 'Repas'}</span>
                                                                    </span>
                                                                ) : null}

                                                                {(ptg.deplacementMatin || ptg.deplacementAm) ? (
                                                                    <span
                                                                        className="inline-flex items-center gap-1 px-1.5 py-0.5 text-[10.5px] font-bold rounded bg-sky-500/10 text-sky-700 dark:text-sky-300 border border-sky-500/30"
                                                                        title={`Déplacement : ${ptg.deplacementMatin && ptg.deplacementAm ? '2 trajets (Matin + Soir)' : ptg.deplacementMatin ? '1 trajet (Matin)' : '1 trajet (Soir)'}`}
                                                                    >
                                                                        <Bus size={10} />
                                                                        <span>
                                                                            {ptg.deplacementMatin && ptg.deplacementAm ? 'Trajets (M+S)' : ptg.deplacementMatin ? 'Trajet (M)' : 'Trajet (S)'}
                                                                        </span>
                                                                    </span>
                                                                ) : null}

                                                                {!ptg.indemniteRepas && !ptg.deplacementMatin && !ptg.deplacementAm && (
                                                                    <span className="text-[var(--color-fg-muted)] text-xs">—</span>
                                                                )}
                                                            </div>
                                                        </Td>
                                                    </Tr>
                                                );
                                            })
                                        )}
                                    </Tbody>
                                </Table>
                            </div>
                        </div>

                        {/* B. Volet Latéral Droit : Récapitulatif, Indemnités & Règles (1 colonne de large) */}
                        <div className="space-y-4">
                            {/* Carte Récapitulatif Temps */}
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                    <TrendingUp size={14} className="text-[var(--color-brand-500)]" />
                                    Temps de Présence
                                </h4>
                                <div className="space-y-2 text-xs">
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
                                        <span className="text-[var(--color-fg-muted)]">Heures supplémentaires</span>
                                        <span className={`font-mono ${recap.heuresSupp && recap.heuresSupp !== '00h00' && recap.heuresSupp !== '0h' ? 'font-bold text-[var(--color-info-600)] dark:text-[var(--color-info-200)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.heuresSupp || '00h00'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center">
                                        <span className="text-[var(--color-fg-muted)]">Retards</span>
                                        <span className={`font-mono ${recap.retards && recap.retards !== '00h00' && recap.retards !== '0h' ? 'font-bold text-[var(--color-warning-600)] dark:text-[var(--color-warning-400)]' : 'text-[var(--color-fg-muted)]'}`}>
                                            {recap.retards || '00h00'}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Carte Indemnités Reçues de la Période avec Détails de Calcul */}
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center justify-between">
                                    <span className="flex items-center gap-1.5">
                                        <DollarSign size={14} className="text-emerald-600" />
                                        Indemnités du Mois
                                    </span>
                                    <span className="font-mono text-xs font-bold text-emerald-600">
                                        {kpis.totalIndemnitesMois || kpis.montantRepasTotal || '0 Ar'}
                                    </span>
                                </h4>

                                <div className="space-y-3 text-xs">
                                    {/* 1. Décomposition Repas */}
                                    <div className="space-y-1.5">
                                        <div className="flex justify-between items-center text-[var(--color-fg)] font-semibold">
                                            <span className="flex items-center gap-1">
                                                <Utensils size={12} className="text-emerald-600" />
                                                Panier Repas ({kpis.totalRepas || '0'})
                                            </span>
                                            <span className="font-mono text-emerald-600 font-bold">
                                                {kpis.montantRepasTotal || '0 Ar'}
                                            </span>
                                        </div>

                                        {/* Détail 1er Repas */}
                                        {recap.repasStdDetail && (
                                            <div className="flex justify-between items-center text-[11px] text-[var(--color-fg-muted)] pl-3 border-l-2 border-emerald-500/30">
                                                <span>1ᵉʳ Repas (Standard)</span>
                                                <span className="font-mono">{recap.repasStdDetail}</span>
                                            </div>
                                        )}

                                        {/* Détail 2e Repas (Nuit / Long shift) */}
                                        {recap.repas2eDetail && (
                                            <div className="flex justify-between items-center text-[11px] text-purple-700 dark:text-purple-300 pl-3 border-l-2 border-purple-500/40 bg-purple-500/5 py-0.5 px-1 rounded-r">
                                                <span className="flex items-center gap-1">
                                                    <Moon size={10} />
                                                    2ᵉ Repas (Nuit / Heures sup)
                                                </span>
                                                <span className="font-mono font-bold">{recap.repas2eDetail}</span>
                                            </div>
                                        )}
                                    </div>

                                    {/* 2. Déplacement Proratisé & Fractionnable */}
                                    <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-1.5">
                                        <div className="flex justify-between items-center text-[var(--color-fg)] font-semibold">
                                            <span className="flex items-center gap-1.5">
                                                <Bus size={13} className="text-sky-600" />
                                                Transport Déplacement
                                            </span>
                                            <span className="font-mono text-sky-600 font-bold">
                                                {kpis.montantDeplacementTotal || '0 Ar'}
                                            </span>
                                        </div>

                                        {recap.deplacementDetail ? (
                                            <div className="flex justify-between items-center text-[11px] text-[var(--color-fg-muted)] pl-3 border-l-2 border-sky-500/30">
                                                <span>Prorata ({kpis.totalTrajets || '0 trajet'})</span>
                                                <span className="font-mono text-[var(--color-fg)] font-semibold">{recap.deplacementDetail}</span>
                                            </div>
                                        ) : (
                                            <p className="text-[10.5px] text-[var(--color-fg-muted)] pl-3">
                                                Proratisé ({kpis.totalTrajets || '0 trajet'})
                                            </p>
                                        )}

                                        {recap.deplacementFormule && (
                                            <p className="text-[10px] text-[var(--color-fg-muted)] pl-3 italic">
                                                {recap.deplacementFormule}
                                            </p>
                                        )}
                                    </div>

                                    {/* 3. Majorations de Travail Spécifique si existantes */}
                                    {(recap.hs30Detail || recap.hs50Detail || recap.nuitDetail || recap.dimancheDetail || recap.ferieDetail) && (
                                        <div className="pt-2 border-t border-[var(--color-border-subtle)] space-y-1">
                                            <span className="text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                                Majorations applicables
                                            </span>
                                            <div className="space-y-0.5 text-[11px] text-[var(--color-fg-muted)] pl-1 font-mono">
                                                {recap.hs30Detail && <div>• {recap.hs30Detail}</div>}
                                                {recap.hs50Detail && <div>• {recap.hs50Detail}</div>}
                                                {recap.nuitDetail && <div className="text-indigo-600 dark:text-indigo-400">• {recap.nuitDetail}</div>}
                                                {recap.dimancheDetail && <div className="text-amber-600 dark:text-amber-400">• {recap.dimancheDetail}</div>}
                                                {recap.ferieDetail && <div className="text-emerald-600 dark:text-emerald-400">• {recap.ferieDetail}</div>}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* Carte Règles Appliquées */}
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-3">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] border-b border-[var(--color-border-subtle)] pb-2 flex items-center gap-1.5">
                                    <Info size={14} className="text-[var(--color-info-500)]" />
                                    Règles Appliquées
                                </h4>
                                <div className="space-y-2 text-xs">
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

                            {/* Carte Observations */}
                            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-4 shadow-xs space-y-2">
                                <h4 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                    Observations
                                </h4>
                                <p className="text-[11px] text-[var(--color-fg-muted)] leading-relaxed">
                                    Les heures et indemnités sont calculées à partir des pointages enregistrés. Toute correction doit être justifiée et validée par le responsable habilité.
                                </p>
                            </div>
                        </div>
                    </div>
                </>
            )}
        </div>
    );
}
