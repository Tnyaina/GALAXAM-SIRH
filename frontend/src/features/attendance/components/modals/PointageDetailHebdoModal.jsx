import { useState, useEffect, useCallback } from 'react';
import { 
    Clock, 
    Calendar, 
    TrendingUp, 
    AlertTriangle, 
    Scale, 
    Banknote, 
    Edit3, 
    History,
    FileText
} from 'lucide-react';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Loading from '@/components/ui/feedback/Loading';
import Alert from '@/components/ui/feedback/Alert';
import { attendanceService } from '@/features/attendance/api/attendanceApi';
import { formatMonnaie, formatHeuresMinutes, formatHeuresDecimal, getStatutPointageBadge } from '@/features/attendance/utils/attendanceDisplayUtils';
import { fmtDate } from '@/utils/dateUtils';
import WeeklyPointageCalculationTable from '@/features/attendance/components/WeeklyPointageCalculationTable';

export default function PointageDetailHebdoModal({
    isOpen,
    onClose,
    employe = null,
    idPeriodePaie = null,
    periodeLibelle = '',
    onAjusterPointage = null
}) {
    // État local
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [ficheData, setFicheData] = useState(null);
    const [activeTab, setActiveTab] = useState('hebdo'); // 'hebdo' | 'quotidien'

    // Données dérivées du collaborateur et de la fiche retournée par l'API.
    const idEmploye = employe?.idEmploye;
    const employeNom = employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : 'Collaborateur';
    const matricule = employe?.matricule || '—';

    // Chargement de la fiche détaillée
    const loadData = useCallback(async () => {
        if (!isOpen || !idEmploye) return;
        setLoading(true);
        setError(null);
        try {
            const params = {};
            if (idPeriodePaie) {
                params.idPeriode = idPeriodePaie;
            }
            const res = await attendanceService.getFicheEmploye(idEmploye, params);
            setFicheData(res);
        } catch (err) {
            console.error('Erreur chargement détail pointage:', err);
            setError('Impossible de charger les détails de pointage de ce collaborateur.');
        } finally {
            setLoading(false);
        }
    }, [isOpen, idEmploye, idPeriodePaie]);

    // Effet : recharge et réinitialise l'onglet à chaque ouverture.
    useEffect(() => {
        if (isOpen) {
            loadData();
            setActiveTab('hebdo');
        } else {
            setFicheData(null);
            setError(null);
        }
    }, [isOpen, loadData]);

    // Données dérivées de la fiche
    const recap = ficheData?.recap || ficheData?.recapitulatif || {};
    const kpis = ficheData?.kpis || {};
    const bilansHebdo = ficheData?.bilansHebdo || [];
    const pointages = ficheData?.pointagesData || ficheData?.pointages || [];

    const totalIndemnitesStr = kpis.totalIndemnitesMois || recap.totalIndemnites;
    const allowanceCodes = [...new Set(pointages.flatMap((pointage) =>
        Object.values(pointage.indemnites || {}).filter((allowance) => allowance.accordee)
            .map((allowance) => allowance.code)))];

    // Rendu
    return (
        <Modal
            isOpen={isOpen}
            onClose={onClose}
            title={
                <div className="flex items-center gap-2">
                    <Scale size={18} className="text-[var(--color-brand-500)] shrink-0" />
                    <div>
                        <div className="text-sm font-bold text-[var(--color-fg)]">
                            Audit &amp; Décomposition Pointage — {employeNom}
                        </div>
                        <div className="text-[11px] font-normal text-[var(--color-fg-muted)] flex items-center gap-2 mt-0.5">
                            <span className="font-mono font-semibold">Matricule : {matricule}</span>
                            <span>•</span>
                            <span>{periodeLibelle || ficheData?.libellePeriode || 'Période en cours'}</span>
                            {employe?.dispensePointage && (
                                <Badge variant="neutral" size="xs">Autonome</Badge>
                            )}
                            {employe?.estCadre && !employe?.dispensePointage && (
                                <Badge variant="info" size="xs">Cadre</Badge>
                            )}
                        </div>
                    </div>
                </div>
            }
            size="4xl"
            footer={
                <div className="flex items-center justify-between w-full">
                    <div className="text-xs text-[var(--color-fg-muted)]">
                        Réf. Période : {ficheData?.codePeriode || '—'}
                    </div>
                    <div className="flex items-center gap-2">
                        {onAjusterPointage && (
                            <Button
                                variant="secondary"
                                size="sm"
                                onClick={() => {
                                    onClose();
                                    onAjusterPointage(employe);
                                }}
                                className="flex items-center gap-1.5"
                            >
                                <Edit3 size={13} />
                                Ajuster un pointage
                            </Button>
                        )}
                        <Button variant="default" size="sm" onClick={onClose}>
                            Fermer
                        </Button>
                    </div>
                </div>
            }
        >
            <div className="p-4 space-y-4 max-h-[75vh] overflow-y-auto">
                {loading ? (
                    <div className="flex flex-col items-center justify-center py-12 gap-3">
                        <Loading size="lg" />
                        <span className="text-xs text-[var(--color-fg-muted)]">Chargement de la décomposition des heures...</span>
                    </div>
                ) : error ? (
                    <Alert variant="danger" title="Erreur">
                        {error}
                    </Alert>
                ) : (
                    <>
                        {/* 1. CARTES DE SYNTHÈSE DU MOIS */}
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
                            {/* Présence */}
                            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1">
                                    <Calendar size={11} /> Jours
                                </div>
                                <div className="text-base font-bold text-[var(--color-fg)] mt-1 font-mono">
                                    {kpis.joursTravailles || (recap.totalJoursPresents != null ? `${recap.totalJoursPresents}j` : '—')}
                                </div>
                                <div className="text-[9.5px] text-[var(--color-fg-subtle)] mt-0.5">
                                    {kpis.joursPrevus || ((recap.totalJoursConge || 0) > 0 ? `+${recap.totalJoursConge}j congé` : 'Travaillés')}
                                </div>
                            </div>

                            {/* Heures faites */}
                            <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] flex items-center gap-1">
                                    <Clock size={11} /> Heures faites
                                </div>
                                <div className="text-base font-bold text-[var(--color-fg)] mt-1 font-mono">
                                    {kpis.heuresRealisees || recap.tempsRealise || formatHeuresMinutes(recap.totalHeuresFaites)}
                                </div>
                                <div className="text-[9.5px] text-[var(--color-fg-subtle)] mt-0.5 font-mono">
                                    {kpis.heuresObjectif ? `objectif ${kpis.heuresObjectif.replace(/^objectif\s*/i, '')}` : formatHeuresDecimal(recap.totalHeuresFaites)}
                                </div>
                            </div>

                            {/* Heures Sup */}
                            <div className="attendance-overtime p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                                    <TrendingUp size={11} /> Heures Sup
                                </div>
                                <div className="text-base font-bold mt-1 attendance-duration">
                                    {kpis.heuresSupp || formatHeuresMinutes((Number(recap.heuresSupp30) || 0) + (Number(recap.heuresSupp50) || 0))}
                                </div>
                                <div className="attendance-duration-secondary mt-0.5">
                                    {kpis.heuresHs30 && kpis.heuresHs30 !== '0.0h' ? `${kpis.heuresHs30} à 30%` : ''}
                                    {kpis.heuresHs50 && kpis.heuresHs50 !== '0.0h' ? ` • ${kpis.heuresHs50} à 50%` : ''}
                                    {!kpis.heuresHs30 && !kpis.heuresHs50 && !Number(recap.heuresSupp30) && !Number(recap.heuresSupp50) ? (kpis.heuresSuppStatut || 'Aucune') : ''}
                                </div>
                            </div>

                            {/* Retards (ROUGE) */}
                            <div className="attendance-retention p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                                    <History size={11} /> Retards
                                </div>
                                <div className="text-base font-bold mt-1 attendance-duration">
                                    {kpis.retardCumule || recap.retards || formatHeuresMinutes(recap.totalMinutesRetard ? recap.totalMinutesRetard / 60 : 0)}
                                </div>
                                <div className="attendance-duration-secondary mt-0.5">
                                    {kpis.retardOccurrences || (recap.totalMinutesRetard ? `${recap.totalMinutesRetard} min cumul` : 'Ponctuel')}
                                </div>
                            </div>

                            {/* Retenue sur salaire */}
                            <div className="attendance-retention p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                                    <AlertTriangle size={11} /> Retenue Paie
                                </div>
                                <div className="text-base font-bold mt-1 attendance-duration">
                                    {kpis.heuresDeficit && kpis.heuresDeficit !== '00h00'
                                        ? `-${kpis.heuresDeficit}`
                                        : (recap.heuresAbsenceRetenues > 0 ? `-${formatHeuresMinutes(recap.heuresAbsenceRetenues)}` : '00h00')}
                                </div>
                                <div className="attendance-duration-secondary mt-0.5">
                                    {kpis.heuresDeficit && kpis.heuresDeficit !== '00h00'
                                        ? `Déficit net déduit`
                                        : (recap.heuresAbsenceRetenues > 0 ? `-${formatHeuresDecimal(recap.heuresAbsenceRetenues)}` : 'Aucune retenue')}
                                </div>
                            </div>

                            {/* Indemnités */}
                            <div className="attendance-allowance p-2.5">
                                <div className="text-[10px] font-bold uppercase tracking-wider flex items-center gap-1">
                                    <Banknote size={11} /> Indemnités
                                </div>
                                <div className="text-base font-bold mt-1 attendance-duration">
                                    {totalIndemnitesStr || '—'}
                                </div>
                                <div className="attendance-duration-secondary mt-0.5 truncate">
                                    {allowanceCodes.join(' · ') || 'Aucune indemnité accordée'}
                                </div>
                            </div>
                        </div>

                        {/* 2. BANNIÈRE EXPLICATIVE DU MÉCANISME DE COMPENSATION */}
                        <div className="attendance-info p-3 text-xs">
                            <div className="flex items-start gap-2">
                                <Scale size={16} className="shrink-0 mt-0.5" />
                                <div className="space-y-1">
                                    <p className="font-bold text-[12px]">
                                        Comprendre le calcul et la compensation hebdomadaire :
                                    </p>
                                    <p className="text-[11px] leading-relaxed">
                                        Comparez les heures réalisées à l&apos;objectif de la semaine, ajusté pour les congés et jours fériés. Le résultat paie indique les heures supplémentaires ou le déficit retenu après compensation au sein de la semaine. Les retards sont un indicateur de ponctualité : ils ne sont pas déduits une seconde fois des heures réalisées.
                                    </p>
                                </div>
                            </div>
                        </div>

                        {/* 3. SÉLECTEUR D'ONGLETS */}
                        <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] pb-1">
                            <button
                                type="button"
                                onClick={() => setActiveTab('hebdo')}
                                className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border-b-2 -mb-1 transition-all cursor-pointer ${
                                    activeTab === 'hebdo'
                                        ? 'border-[var(--color-brand-500)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold'
                                        : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                <Scale size={13} />
                                Calcul par semaine ({bilansHebdo.length} semaines)
                            </button>
                            <button
                                type="button"
                                onClick={() => setActiveTab('quotidien')}
                                className={`flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold border-b-2 -mb-1 transition-all cursor-pointer ${
                                    activeTab === 'quotidien'
                                        ? 'border-[var(--color-brand-500)] text-[var(--color-brand-600)] dark:text-[var(--color-brand-400)] font-bold'
                                        : 'border-transparent text-[var(--color-fg-muted)] hover:text-[var(--color-fg)]'
                                }`}
                            >
                                <FileText size={13} />
                                Journal Quotidien des Pointages ({pointages.length} jours)
                            </button>
                        </div>

                        {/* 4. CONTENU ONGLET 1 : DÉCOMPOSITION HEBDOMADAIRE */}
                        {activeTab === 'hebdo' && (
                            <WeeklyPointageCalculationTable weeks={bilansHebdo} pointages={pointages} />
                        )}

                        {/* 5. CONTENU ONGLET 2 : JOURNAL QUOTIDIEN */}
                        {activeTab === 'quotidien' && (
                            <div className="space-y-3">
                                <Table density="compact" divided>
                                    <Thead>
                                        <Tr>
                                            <Th className="w-24">Date</Th>
                                            <Th className="w-20">Jour</Th>
                                            <Th className="text-center w-16">Entrée</Th>
                                            <Th className="text-center w-16">Pause Dép.</Th>
                                            <Th className="text-center w-16">Pause Ret.</Th>
                                            <Th className="text-center w-16">Sortie</Th>
                                            <Th className="text-center w-18 text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">Retard</Th>
                                            <Th className="text-center w-20">Fait</Th>
                                            <Th className="text-center w-20">Écart</Th>
                                            <Th className="text-center w-28">Statut</Th>
                                            <Th className="text-center min-w-32">Indemnités accordées</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {pointages.length === 0 ? (
                                            <Tr>
                                                <Td colSpan={11} className="text-center py-6 text-xs text-[var(--color-fg-muted)]">
                                                    Aucun pointage quotidien disponible.
                                                </Td>
                                            </Tr>
                                        ) : (
                                            pointages.map((ptg, idx) => {
                                                const dtComp = ptg.dateComplete || ptg.date;
                                                const nomJour = ptg.jour || ptg.nomJour || '—';
                                                const entree = ptg.entree || '—';
                                                const sortiePause = ptg.sortiePause || ptg.sortieMatin || '—';
                                                const retourPause = ptg.retourPause || ptg.entreeAm || '—';
                                                const sortie = ptg.sortie || ptg.sortieAm || '—';

                                                const retMin = ptg.minutesRetard || 0;
                                                const faitStr = ptg.heures || (ptg.totalHeures ? formatHeuresMinutes(ptg.totalHeures) : (ptg.totalHeuresDecimal ? formatHeuresMinutes(ptg.totalHeuresDecimal) : '—'));
                                                const ecartStr = ptg.ecart || (ptg.ecart8h ? (ptg.ecart8h > 0 ? `+${formatHeuresDecimal(ptg.ecart8h)}` : `-${formatHeuresDecimal(Math.abs(ptg.ecart8h))}`) : '00 min');
                                                const isEcartPos = ptg.ecartPositif ?? (ptg.ecart8h ? ptg.ecart8h >= 0 : true);

                                                const statutCode = ptg.statut || ptg.statutBadge || 'PRESENT';
                                                const dailyAllowances = Object.values(ptg.indemnites || {})
                                                    .filter((allowance) => allowance.accordee);

                                                return (
                                                    <Tr key={dtComp || idx}>
                                                        <Td className="font-mono text-xs">
                                                            {fmtDate(dtComp)}
                                                        </Td>
                                                        <Td className="text-xs font-semibold capitalize">
                                                            {nomJour}
                                                        </Td>
                                                        <Td className="text-center font-mono text-xs">
                                                            {entree}
                                                        </Td>
                                                        <Td className="text-center font-mono text-xs">
                                                            {sortiePause}
                                                        </Td>
                                                        <Td className="text-center font-mono text-xs">
                                                            {retourPause}
                                                        </Td>
                                                        <Td className="text-center font-mono text-xs">
                                                            {sortie}
                                                        </Td>
                                                        {/* Retard (Avertissement) */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {retMin > 0 ? (
                                                                <span className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-300)]">
                                                                    +{retMin}m
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
                                                        </Td>
                                                        {/* Heures faites */}
                                                        <Td className="text-center font-mono text-xs font-semibold">
                                                            {faitStr}
                                                        </Td>
                                                        {/* Écart vs Norme */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {ecartStr && ecartStr !== '00 min' && ecartStr !== '0.00h' && ecartStr !== '—' ? (
                                                                <span className={isEcartPos ? "text-[var(--color-info-700)] dark:text-[var(--color-info-300)] font-bold" : "text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)] font-bold"}>
                                                                    {ecartStr}
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">00 min</span>
                                                            )}
                                                        </Td>
                                                        {/* Statut Badge */}
                                                        <Td className="text-center">
                                                            {getStatutPointageBadge(statutCode, retMin)}
                                                        </Td>
                                                        <Td className="text-center text-xs">
                                                            {dailyAllowances.length > 0
                                                                ? dailyAllowances.map((allowance) => (
                                                                    <div key={allowance.code} className="font-semibold">
                                                                        {allowance.code} · {formatMonnaie(allowance.montant || 0)}
                                                                    </div>
                                                                ))
                                                                : <span className="text-[var(--color-fg-subtle)]">—</span>}
                                                        </Td>
                                                    </Tr>
                                                );
                                            })
                                        )}
                                    </Tbody>
                                </Table>
                            </div>
                        )}
                    </>
                )}
            </div>
        </Modal>
    );
}
