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
import pointageEmployeService from '@/api/pointage/pointageEmployeService';
import { formatMonnaie, formatHeuresMinutes, formatHeuresDecimal, formatDualHours, getStatutPointageBadge } from '@/utils/pointageUtils';
import { fmtDate } from '@/utils/carriereUtils';

export default function PointageDetailHebdoModal({
    isOpen,
    onClose,
    employe = null,
    idPeriodePaie = null,
    periodeLibelle = '',
    onAjusterPointage = null
}) {
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [ficheData, setFicheData] = useState(null);
    const [activeTab, setActiveTab] = useState('hebdo'); // 'hebdo' | 'quotidien'

    const idEmploye = employe?.idEmploye;
    const employeNom = employe ? `${employe.nom || ''} ${employe.prenom || ''}`.trim() : 'Collaborateur';
    const matricule = employe?.matricule || '—';

    const loadData = useCallback(async () => {
        if (!isOpen || !idEmploye) return;
        setLoading(true);
        setError(null);
        try {
            const params = {};
            if (idPeriodePaie) {
                params.idPeriode = idPeriodePaie;
            }
            const res = await pointageEmployeService.getFicheEmploye(idEmploye, params);
            setFicheData(res);
        } catch (err) {
            console.error('Erreur chargement détail pointage:', err);
            setError('Impossible de charger les détails de pointage de ce collaborateur.');
        } finally {
            setLoading(false);
        }
    }, [isOpen, idEmploye, idPeriodePaie]);

    useEffect(() => {
        if (isOpen) {
            loadData();
            setActiveTab('hebdo');
        } else {
            setFicheData(null);
            setError(null);
        }
    }, [isOpen, loadData]);

    const recap = ficheData?.recap || ficheData?.recapitulatif || {};
    const kpis = ficheData?.kpis || {};
    const regles = ficheData?.reglesAppliquees || {};
    const bilansHebdo = ficheData?.bilansHebdo || [];
    const pointages = ficheData?.pointagesData || ficheData?.pointages || [];
    const dureeHebdo = Number(regles.dureeHebdoLegale || kpis.dureeHebdoLegale || 0);
    const dureeJour = dureeHebdo > 0 ? dureeHebdo / 5 : null;

    const totalIndemnitesStr = kpis.totalIndemnitesMois || recap.totalIndemnites;
    const totalIndemnites = (Number(recap.montantTotalRepas) || 0) + (Number(recap.montantTotalDeplacement) || 0);

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
                                    {kpis.heuresObjectif || formatHeuresDecimal(recap.totalHeuresFaites)}
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
                                    {totalIndemnitesStr || formatMonnaie(totalIndemnites)}
                                </div>
                                <div className="attendance-duration-secondary mt-0.5 truncate">
                                    {kpis.montantRepasTotal ? `${kpis.montantRepasTotal} repas` : (recap.montantTotalRepas ? `${formatMonnaie(recap.montantTotalRepas)} repas` : '0 Ar repas')}
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
                                        L&apos;objectif est de {dureeHebdo ? <strong>{dureeHebdo} heures par semaine</strong> : 'la durée hebdomadaire configurée'} et il est réduit pour les congés et jours fériés.
                                        Les heures réalisées au-delà de {dureeJour ? `${dureeJour} h sur une journée` : 'la durée d’une journée'} compensent d&apos;abord les heures manquantes de cette même semaine.
                                        Seul le solde devient une heure supplémentaire ou une retenue. Les retards restent visibles séparément pour le suivi de l&apos;assiduité.
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
                                Décomposition Hebdomadaire &amp; Compensation ({bilansHebdo.length} semaines)
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
                            <div className="space-y-3">
                                <Table size="sm" divided>
                                    <Thead>
                                        <Tr>
                                            <Th className="w-32">Semaine</Th>
                                            <Th className="text-center w-14">Jours</Th>
                                            <Th className="text-center w-24">Objectif semaine</Th>
                                            <Th className="text-center w-28">Heures faites</Th>
                                            <Th className="text-center w-20 text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]" title="Retards observés, indépendamment de la compensation">Retards</Th>
                                            <Th className="text-center w-20 text-[var(--color-info-700)] dark:text-[var(--color-info-200)]" title="Temps au-delà de la durée normale d'une journée">Heures en plus</Th>
                                            <Th className="text-center w-24 text-[var(--color-success-600)] dark:text-[var(--color-success-400)]" title="Part des heures en plus utilisée pour couvrir un manque de la semaine">Utilisées</Th>
                                            <Th className="text-center w-32">Résultat paie</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {bilansHebdo.length === 0 ? (
                                            <Tr>
                                                <Td colSpan={8} className="text-center py-6 text-xs text-[var(--color-fg-muted)]">
                                                    Aucune semaine calculée pour cette période.
                                                </Td>
                                            </Tr>
                                        ) : (
                                            bilansHebdo.map((b, idx) => {
                                                const dDeb = b.debutSemaine || b.dateDebut;
                                                const dFin = b.finSemaine || b.dateFin;
                                                const numSem = b.numeroSemaine ?? b.numSemaine ?? (idx + 1);
                                                const joursOuv = b.nbJoursTroncon ?? b.joursOuvrables;
                                                const seuil = b.seuilHeuresTroncon ?? b.heuresAttendues ?? 40;
                                                const faitVal = b.totalHeuresTravaillees ?? b.heuresFaites ?? 0;
                                                const faitHhMm = formatHeuresMinutes(faitVal);
                                                const faitDec = formatHeuresDecimal(faitVal);

                                                // Retards
                                                const retHeures = b.heuresRetard != null ? b.heuresRetard : (b.minutesRetard ? b.minutesRetard / 60 : 0);
                                                const retMin = b.minutesRetard != null ? b.minutesRetard : Math.round(retHeures * 60);

                                                // Surplus & Compensation
                                                const surplus = b.heuresHsBrutes ?? b.heuresSuppBrutes ?? 0;
                                                const compense = b.heuresCompensees ?? 0;
                                                const hs30 = b.heuresHs30 ?? b.heuresSuppNettes30 ?? 0;
                                                const hs50 = b.heuresHs50 ?? b.heuresSuppNettes50 ?? 0;
                                                const retenue = b.heuresDeficit ?? b.heuresAbsenceRetenues ?? 0;
                                                const totalHs = b.heuresHsNettes != null ? b.heuresHsNettes : (hs30 + hs50);

                                                return (
                                                    <Tr key={dDeb || idx}>
                                                        {/* Semaine */}
                                                        <Td className="font-mono text-xs">
                                                            <div className="font-bold text-[var(--color-fg)]">
                                                                Semaine {numSem}
                                                            </div>
                                                            <div className="text-[10px] text-[var(--color-fg-muted)]">
                                                                du {fmtDate(dDeb)} au {fmtDate(dFin)}
                                                            </div>
                                                        </Td>

                                                        {/* Jours ouvrés */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {joursOuv != null ? `${joursOuv}j` : '—'}
                                                        </Td>

                                                        {/* Objectif */}
                                                        <Td className="text-center font-mono text-xs font-semibold text-[var(--color-fg-muted)]">
                                                            {formatDualHours(seuil, { primary: 'hhmm' })}
                                                        </Td>

                                                        {/* Heures faites */}
                                                        <Td className="text-center font-mono text-xs">
                                                            <span className="font-bold text-[var(--color-fg)]">{faitHhMm}</span>
                                                            <span className="block text-[9px] text-[var(--color-fg-muted)]">({faitDec})</span>
                                                        </Td>

                                                        {/* Retards (Rouge) */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {retMin > 0 ? (
                                                                <span className="font-bold text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]">
                                                                    {formatDualHours(retHeures, { primary: 'hhmm' })}
                                                                    <span className="block text-[9px] font-normal">{retMin} min</span>
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
                                                        </Td>

                                                        {/* Surplus brut journalier */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {surplus > 0 ? (
                                                                <span className="font-bold text-[var(--color-info-700)] dark:text-[var(--color-info-200)]">
                                                                    +{formatDualHours(surplus, { primary: 'hhmm' })}
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
                                                        </Td>

                                                        {/* Compensé */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {compense > 0 ? (
                                                                <span 
                                                                    className="attendance-compensation inline-block px-1.5 py-0.5 font-bold"
                                                                    title={`${formatHeuresMinutes(compense)} ont compensé des heures manquantes de cette semaine.`}
                                                                >
                                                                    {formatDualHours(compense, { primary: 'hhmm' })}
                                                                    <span className="block text-[9px] font-normal font-sans">compensées</span>
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
                                                        </Td>

                                                        {/* Impact Paie */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {totalHs > 0 ? (
                                                                <div className="flex flex-col items-center">
                                                                    <span className="attendance-overtime px-1.5 py-0.5 font-bold">
                                                                        +{formatDualHours(totalHs, { primary: 'hhmm' })} HS
                                                                    </span>
                                                                    <span className="attendance-duration-secondary mt-0.5">
                                                                        {hs30 > 0 ? `(${formatHeuresDecimal(hs30)} à 30%)` : ''}
                                                                        {hs50 > 0 ? ` (${formatHeuresDecimal(hs50)} à 50%)` : ''}
                                                                    </span>
                                                                </div>
                                                            ) : retenue > 0 ? (
                                                                <span className="attendance-retention inline-block px-1.5 py-0.5 font-bold">
                                                                    -{formatDualHours(retenue, { primary: 'hhmm' })} Retenue
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)] font-sans text-xs">
                                                                    0h00 (Équilibré)
                                                                </span>
                                                            )}
                                                        </Td>
                                                    </Tr>
                                                );
                                            })
                                        )}
                                    </Tbody>
                                </Table>
                            </div>
                        )}

                        {/* 5. CONTENU ONGLET 2 : JOURNAL QUOTIDIEN */}
                        {activeTab === 'quotidien' && (
                            <div className="space-y-3">
                                <Table size="sm" divided>
                                    <Thead>
                                        <Tr>
                                            <Th className="w-24">Date</Th>
                                            <Th className="w-20">Jour</Th>
                                            <Th className="text-center w-16">Entrée</Th>
                                            <Th className="text-center w-16">Pause Dép.</Th>
                                            <Th className="text-center w-16">Pause Ret.</Th>
                                            <Th className="text-center w-16">Sortie</Th>
                                            <Th className="text-center w-18 text-rose-700 dark:text-rose-400">Retard</Th>
                                            <Th className="text-center w-20">Fait</Th>
                                            <Th className="text-center w-20">Écart</Th>
                                            <Th className="text-center w-28">Statut</Th>
                                            <Th className="text-center w-20">Repas</Th>
                                            <Th className="text-center w-20">Déplac.</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {pointages.length === 0 ? (
                                            <Tr>
                                                <Td colSpan={12} className="text-center py-6 text-xs text-[var(--color-fg-muted)]">
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
                                                const hasRepas = ptg.indemniteRepas || ptg.indemniteRepasRecue;
                                                const hasDepMatin = ptg.deplacementMatin;
                                                const hasDepAm = ptg.deplacementAm;
                                                const hasDep = hasDepMatin || hasDepAm || ptg.indemniteDeplacement;

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
                                                        {/* Retard (Rouge) */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {retMin > 0 ? (
                                                                <span className="font-bold text-rose-700 dark:text-rose-400">
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
                                                                <span className={isEcartPos ? "text-indigo-700 dark:text-indigo-300 font-bold" : "text-rose-700 dark:text-rose-400 font-bold"}>
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
                                                        {/* Repas */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {hasRepas ? (
                                                                <span className="text-amber-700 dark:text-amber-300 font-bold" title={ptg.montantRepas ? formatMonnaie(ptg.montantRepas) : 'Repas accordé'}>
                                                                    Oui
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
                                                        </Td>
                                                        {/* Déplacement */}
                                                        <Td className="text-center font-mono text-xs">
                                                            {hasDep ? (
                                                                <span className="text-emerald-700 dark:text-emerald-300 font-bold" title={ptg.montantDeplacement ? formatMonnaie(ptg.montantDeplacement) : 'Indemnité transport'}>
                                                                    {hasDepMatin && hasDepAm ? '2/2' : (hasDepMatin || hasDepAm ? '1/2' : 'Oui')}
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">—</span>
                                                            )}
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
