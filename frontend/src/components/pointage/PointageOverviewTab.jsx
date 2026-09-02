import { useState, useEffect, useMemo, useCallback } from 'react';
import { 
    Clock, 
    Layers, 
    CalendarRange, 
    CheckCircle2, 
    AlertTriangle, 
    Send, 
    Lock, 
    ArrowRight, 
    Users, 
    Calendar, 
    CheckCircle, 
    XCircle,
    TrendingUp,
    ShieldAlert,
    ClockCheck,
    Search,
    FileSpreadsheet,
    Eye,
    ChevronDown,
    ChevronUp
} from 'lucide-react';
import { Link } from 'react-router-dom';
import StatCard from '@/components/ui/data-display/StatCard';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';

export default function PointageOverviewTab({ onTabChange }) {
    const { hasPermission, user } = useAuth();
    const { toast } = useToast();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [sites, setSites] = useState([]);
    const [selectedSite, setSelectedSite] = useState(0);

    const [semaines, setSemaines] = useState([]);
    const [syntheseData, setSyntheseData] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [isSemainesExpanded, setIsSemainesExpanded] = useState(false);

    // Modales DAF & Transmission
    const [showValiderPeriodeModal, setShowValiderPeriodeModal] = useState(false);
    const [showCloturerPeriodeModal, setShowCloturerPeriodeModal] = useState(false);
    const [showRejetModal, setShowRejetModal] = useState(false);
    const [showTransmissionModal, setShowTransmissionModal] = useState(false);
    const [motifRejet, setMotifRejet] = useState('');
    const [processingAction, setProcessingAction] = useState(false);

    // 1. Charger périodes et sites
    useEffect(() => {
        const init = async () => {
            try {
                const [resPeriodes, resSites] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    organisationService.getSites()
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    // Période active ou première période
                    const activeP = resPeriodes.find(p => p.statutCode === 'EN_COURS' || p.statutCode === 'A_VALIDER') || resPeriodes[0];
                    setSelectedPeriode(activeP.idPeriodePaie);
                }
                if (resSites) setSites(resSites);
            } catch (err) {
                console.error('Erreur chargement init:', err);
            }
        };
        init();
    }, []);

    // 2. Charger les semaines et données de synthèse pour la période active
    const loadData = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        try {
            const [resSemaines, resSynthese] = await Promise.all([
                pointageSyntheseService.getSemaines({ idPeriodePaie: selectedPeriode, idSite: selectedSite || undefined }),
                pointageSyntheseService.getSynthese({ idPeriodePaie: selectedPeriode, idSite: selectedSite || undefined })
            ]);
            if (resSemaines?.semaines) {
                setSemaines(resSemaines.semaines);
            }
            if (resSynthese?.synthese) {
                setSyntheseData(resSynthese.synthese);
            }
        } catch (err) {
            console.error('Erreur chargement données cockpit:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite]);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const currentPeriodeObj = useMemo(() => {
        return periodes.find(p => p.idPeriodePaie === Number(selectedPeriode)) || null;
    }, [periodes, selectedPeriode]);

    // Analyse du workflow
    const workflowStatus = useMemo(() => {
        const totalSemaines = semaines.length;
        const semainesValideesCount = semaines.filter(s => s.estValidee).length;
        const totalAnomalies = semaines.reduce((acc, s) => acc + (s.nbAnomalies || 0), 0);
        const allValidees = totalSemaines > 0 && semainesValideesCount === totalSemaines;
        const st = currentPeriodeObj?.statutCode;

        return {
            totalSemaines,
            semainesValideesCount,
            totalAnomalies,
            allValidees,
            isEnCours: st === 'EN_COURS' || !st,
            isAValider: st === 'A_VALIDER' || st === 'TRANSMIS_DAF',
            isValidee: st === 'VALIDEE',
            isCloturee: st === 'CLOTUREE'
        };
    }, [semaines, currentPeriodeObj]);

    // KPIs mensuels agrégés
    const statsMensuelles = useMemo(() => {
        const totalSalaries = syntheseData.length;
        const totalHeures = syntheseData.reduce((acc, r) => acc + (r.totalHeuresTravaillees || 0), 0);
        const totalHs30 = syntheseData.reduce((acc, r) => acc + (r.heuresHs30 || 0), 0);
        const totalHs50 = syntheseData.reduce((acc, r) => acc + (r.heuresHs50 || 0), 0);
        const totalAbsences = syntheseData.reduce((acc, r) => acc + (r.heuresAbsenceRetenues || 0), 0);
        const totalRetards = syntheseData.reduce((acc, r) => acc + (r.totalMinutesRetard || 0), 0);

        return {
            totalSalaries,
            totalHeures: Math.round(totalHeures),
            totalHs: Math.round(totalHs30 + totalHs50),
            totalHs30: Math.round(totalHs30),
            totalHs50: Math.round(totalHs50),
            totalAbsences: Math.round(totalAbsences),
            totalRetardsMins: totalRetards
        };
    }, [syntheseData]);

    const filteredSyntheseData = useMemo(() => {
        if (!searchTerm.trim()) return syntheseData;
        const term = searchTerm.toLowerCase().trim();
        return syntheseData.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [syntheseData, searchTerm]);

    const fmtMoney = (val) => {
        if (!val || isNaN(val)) return '0 Ar';
        return new Intl.NumberFormat('fr-FR').format(Math.round(val)) + ' Ar';
    };

    // Actions DAF
    const handleUpdateStatut = async (nouveauStatut, motif = '') => {
        if (!selectedPeriode) return;
        setProcessingAction(true);
        try {
            await periodePaieService.updateStatut(selectedPeriode, nouveauStatut);
            toast({
                title: nouveauStatut === 'VALIDEE'
                    ? 'Période validée par la DAF'
                    : nouveauStatut === 'CLOTUREE'
                    ? 'Période clôturée définitivement'
                    : 'Période renvoyée en cours',
                message: nouveauStatut === 'EN_COURS'
                    ? `Période renvoyée aux agents pour révision. Motif : ${motif || 'Non précisé'}`
                    : `Statut mis à jour (${nouveauStatut}).`,
                type: nouveauStatut === 'EN_COURS' ? 'warning' : 'success'
            });
            setShowValiderPeriodeModal(false);
            setShowCloturerPeriodeModal(false);
            setShowRejetModal(false);
            setMotifRejet('');
            const list = await periodePaieService.getAllPeriodes();
            if (list) setPeriodes(list);
            await loadData();
        } catch (err) {
            console.error('Erreur mise à jour statut:', err);
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || 'Impossible de mettre à jour le statut.',
                type: 'error'
            });
        } finally {
            setProcessingAction(false);
        }
    };

    // Transmission Agent à la DAF
    const handleTransmettreDaf = async () => {
        if (!selectedPeriode) return;
        setProcessingAction(true);
        try {
            await periodePaieService.updateStatut(selectedPeriode, 'A_VALIDER');
            toast({
                title: 'Transmission effectuée',
                message: 'Toutes les semaines ont été verrouillées. La période a été transmise à la DAF pour arbitrage.',
                type: 'success'
            });
            setShowTransmissionModal(false);
            const list = await periodePaieService.getAllPeriodes();
            if (list) setPeriodes(list);
            await loadData();
        } catch (err) {
            console.error('Erreur transmission DAF:', err);
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || 'Impossible de transmettre la période.',
                type: 'error'
            });
        } finally {
            setProcessingAction(false);
        }
    };

    return (
        <div className="space-y-4">
            {/* Barre de sélection de période & site */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 flex flex-wrap items-center justify-between gap-3 shadow-xs">
                <div className="flex items-center gap-3 flex-wrap flex-1">
                    <div className="min-w-[220px]">
                        <Select
                            options={periodes.map(p => ({
                                value: p.idPeriodePaie,
                                label: `${p.libelle || p.code} (${p.statutLibelle || p.statutCode})`
                            }))}
                            value={selectedPeriode}
                            onChange={(val) => setSelectedPeriode(val)}
                            className="text-xs h-[32px]"
                        />
                    </div>
                    <div className="min-w-[180px]">
                        <Select
                            options={[
                                { value: 0, label: 'Tous les sites' },
                                ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))
                            ]}
                            value={selectedSite}
                            onChange={(val) => setSelectedSite(Number(val))}
                            className="text-xs h-[32px]"
                        />
                    </div>
                </div>

                <div className="flex items-center gap-2">
                    {currentPeriodeObj && (
                        <Badge 
                            variant={
                                workflowStatus.isCloturee ? 'success' :
                                workflowStatus.isValidee ? 'primary' :
                                workflowStatus.isAValider ? 'warning' : 'info'
                            } 
                            size="sm"
                            className="font-bold uppercase tracking-wider"
                        >
                            {currentPeriodeObj.statutLibelle || currentPeriodeObj.statutCode}
                        </Badge>
                    )}
                </div>
            </div>

            {/* SECTION 1 : BANNIÈRE DAF D'ARBITRAGE RAPIDE (Si période A_VALIDER ou VALIDEE) */}
            {isDaf && (workflowStatus.isAValider || workflowStatus.isValidee) && (
                <div className="bg-[var(--color-surface)] border-2 border-brand-500/40 p-4 shadow-sm relative overflow-hidden">
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                        <div className="space-y-1">
                            <div className="flex items-center gap-2">
                                <span className="flex h-2.5 w-2.5 rounded-full bg-amber-500 animate-ping" />
                                <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                    {workflowStatus.isAValider ? 'Arbitrage DAF Requis : Période Transmise par les Agents' : 'Période Validée DAF : Prête pour Clôture Définitive'}
                                </h3>
                            </div>
                            <p className="text-xs text-[var(--color-fg-muted)]">
                                {workflowStatus.isAValider 
                                    ? 'Toutes les semaines ont été consolidées par l\'exploitation. Examinez les totaux ci-dessous puis validez ou renvoyez pour correction.'
                                    : 'Les fiches de pointage sont scellées. Vous pouvez procéder à la clôture immuable pour lancer le calcul des bulletins de salaire.'
                                }
                            </p>
                        </div>

                        <div className="flex items-center gap-2 shrink-0">
                            {workflowStatus.isAValider && (
                                <>
                                    <Button
                                        type="button"
                                        variant="outline"
                                        size="sm"
                                        onClick={() => setShowRejetModal(true)}
                                        className="font-bold text-[var(--color-danger-500)] border-[var(--color-danger-500)]/30 hover:bg-[var(--color-danger-500)]/10 flex items-center gap-1.5"
                                    >
                                        <XCircle size={14} />
                                        <span>Rejeter avec motif</span>
                                    </Button>
                                    <Button
                                        type="button"
                                        variant="primary"
                                        size="sm"
                                        onClick={() => setShowValiderPeriodeModal(true)}
                                        className="font-bold flex items-center gap-1.5 shadow-xs"
                                    >
                                        <CheckCircle size={14} />
                                        <span>Valider la période</span>
                                    </Button>
                                </>
                            )}

                            {workflowStatus.isValidee && (
                                <Button
                                    type="button"
                                    variant="primary"
                                    size="sm"
                                    onClick={() => setShowCloturerPeriodeModal(true)}
                                    className="font-bold !bg-emerald-600 hover:!bg-emerald-700 text-white flex items-center gap-1.5 shadow-xs"
                                >
                                    <Lock size={14} />
                                    <span>Clôturer définitivement pour Paie</span>
                                </Button>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* SECTION 2 : FRISE CHRONOLOGIQUE DU MOIS & WORKFLOW SÉQUENTIEL (Rétractable) */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                <button
                    type="button"
                    onClick={() => setIsSemainesExpanded(prev => !prev)}
                    className="w-full px-4 py-2.5 bg-[var(--color-bg-subtle)]/40 hover:bg-[var(--color-bg-subtle)] flex items-center justify-between border-b border-[var(--color-border-subtle)] cursor-pointer transition-colors text-left"
                >
                    <div className="flex items-center gap-2.5">
                        <Layers size={14} className="text-brand-600 shrink-0" />
                        <div>
                            <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                Cycle de Vie du Mois — {currentPeriodeObj?.libelle || 'Période Active'}
                            </span>
                            <span className="text-[11px] text-[var(--color-fg-muted)] ml-2 hidden sm:inline">
                                ({workflowStatus.semainesValideesCount} sur {workflowStatus.totalSemaines} semaine(s) validée(s))
                            </span>
                        </div>
                    </div>

                    <div className="flex items-center gap-2">
                        <Badge 
                            variant={workflowStatus.allValidees ? 'success' : 'warning'} 
                            size="xs" 
                            className="font-mono font-bold"
                        >
                            {workflowStatus.semainesValideesCount} / {workflowStatus.totalSemaines} sem.
                        </Badge>
                        {isSemainesExpanded ? (
                            <ChevronUp size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                        ) : (
                            <ChevronDown size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                        )}
                    </div>
                </button>

                {/* Contenu déroulant des semaines */}
                {isSemainesExpanded && (
                    <div className="p-3.5 space-y-3">
                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-2.5">
                            {semaines.map((sem, idx) => {
                                const isPrevValidee = idx === 0 || semaines[idx - 1]?.estValidee;
                                return (
                                    <div 
                                        key={sem.id}
                                        className={`p-2.5 border transition-all relative ${
                                            sem.estValidee
                                                ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30'
                                                : isPrevValidee
                                                ? 'bg-[var(--color-surface)] border-brand-500/40 shadow-xs'
                                                : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] opacity-70'
                                        }`}
                                    >
                                        <div className="flex items-center justify-between">
                                            <span className="text-xs font-bold text-[var(--color-fg)]">
                                                {sem.title}
                                            </span>
                                            {sem.estValidee ? (
                                                <Badge variant="success" size="xs" className="font-bold flex items-center gap-1">
                                                    <CheckCircle2 size={10} />
                                                    <span>Validée</span>
                                                </Badge>
                                            ) : (
                                                <Badge variant={isPrevValidee ? 'warning' : 'secondary'} size="xs" className="font-bold">
                                                    {isPrevValidee ? 'À valider' : 'En attente'}
                                                </Badge>
                                            )}
                                        </div>

                                        <div className="text-[10.5px] text-[var(--color-fg-muted)] mt-0.5 font-mono">
                                            Du {sem.dateDebut} au {sem.dateFin}
                                        </div>

                                        <div className="mt-2 pt-1.5 border-t border-[var(--color-border-subtle)] flex items-center justify-between text-[11px]">
                                            <span className="text-[var(--color-fg-muted)]">Anomalies :</span>
                                            <span className={`font-mono font-bold ${sem.nbAnomalies > 0 ? 'text-[var(--color-warning-600)]' : 'text-[var(--color-success-600)]'}`}>
                                                {sem.nbAnomalies}
                                            </span>
                                        </div>

                                        <div className="mt-2">
                                            <button
                                                type="button"
                                                onClick={() => onTabChange('synthese', { semaine: sem.id, subTab: 'hebdo' })}
                                                className="w-full py-0.5 text-center text-[10.5px] font-bold text-brand-600 hover:text-brand-700 hover:bg-brand-500/10 transition-colors border border-brand-500/20"
                                            >
                                                {sem.estValidee ? 'Consulter le détail →' : 'Consolider cette semaine →'}
                                            </button>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>

                        {/* Bouton de transmission DAF si 100% validé */}
                        {workflowStatus.allValidees && workflowStatus.isEnCours && (
                            <div className="p-3 bg-brand-500/8 border border-brand-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                                <div className="flex items-center gap-2">
                                    <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0" />
                                    <div>
                                        <span className="text-xs font-bold text-[var(--color-fg)]">
                                            Toutes les semaines sont verrouillées et prêtes pour transmission !
                                        </span>
                                        <p className="text-[11px] text-[var(--color-fg-muted)]">
                                            La transmission à la DAF passe la période en statut À VALIDER et fige la saisie des agents.
                                        </p>
                                    </div>
                                </div>

                                <Button
                                    type="button"
                                    variant="primary"
                                    size="xs"
                                    onClick={() => setShowTransmissionModal(true)}
                                    className="font-bold flex items-center gap-1.5 shadow-xs shrink-0"
                                >
                                    <Send size={12} />
                                    <span>Transmettre à la DAF</span>
                                </Button>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* SECTION 3 : INDICATEURS CLÉS DU MOIS (KPIs) */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                <StatCard
                    title="Effectif Saisi"
                    value={statsMensuelles.totalSalaries}
                    subtitle="Collaborateurs"
                    icon={<Users size={18} />}
                    color="brand"
                />
                <StatCard
                    title="Volume Heures"
                    value={`${statsMensuelles.totalHeures}h`}
                    subtitle="Cumul mensuel"
                    icon={<Clock size={18} />}
                    color="info"
                />
                <StatCard
                    title="HS 30% / 50%"
                    value={`+${statsMensuelles.totalHs}h`}
                    subtitle={`30%: ${statsMensuelles.totalHs30}h | 50%: ${statsMensuelles.totalHs50}h`}
                    icon={<TrendingUp size={18} />}
                    color="success"
                />
                <StatCard
                    title="Retards Cumulés"
                    value={`${statsMensuelles.totalRetardsMins} min`}
                    subtitle="Compensables"
                    icon={<ClockCheck size={18} />}
                    color="warning"
                />
                <StatCard
                    title="Retenues Absences"
                    value={`${statsMensuelles.totalAbsences}h`}
                    subtitle="Décompte Paie"
                    icon={<AlertTriangle size={18} />}
                    color="danger"
                />
                <StatCard
                    title="Anomalies"
                    value={workflowStatus.totalAnomalies}
                    subtitle="À régulariser"
                    icon={<ShieldAlert size={18} />}
                    color={workflowStatus.totalAnomalies > 0 ? 'warning' : 'success'}
                />
            </div>

            {/* SECTION 4 : GRAND TABLEAU RÉCAPITULATIF NOMINATIF DU MOIS */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                <div className="px-4 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex flex-col sm:flex-row sm:items-center justify-between gap-2.5">
                    <div className="flex items-center gap-2">
                        <FileSpreadsheet size={15} className="text-brand-600 shrink-0" />
                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                            Détail Nominatif des Salariés &amp; Éléments Consolidés pour la Paie
                        </h3>
                        <Badge variant="secondary" size="xs" className="font-mono font-semibold">
                            {filteredSyntheseData.length} salarié(s)
                        </Badge>
                    </div>

                    <div className="relative min-w-[200px] max-w-xs">
                        <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Filtrer par nom, matricule..."
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full pl-7 pr-3 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 h-[28px]"
                        />
                    </div>
                </div>

                {loading ? (
                    <div className="p-12 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                        <Clock size={16} className="animate-spin text-brand-500" />
                        Chargement des fiches salariés consolidées...
                    </div>
                ) : filteredSyntheseData.length === 0 ? (
                    <div className="p-8 text-center text-xs text-[var(--color-fg-muted)]">
                        Aucun collaborateur trouvé pour cette période ou ce filtre.
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <Table className="w-full text-left border-collapse text-[12px]">
                            <Thead>
                                <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                    <Th className="py-2.5 px-3">Salarié</Th>
                                    <Th className="py-2.5 px-2">Site</Th>
                                    <Th className="py-2.5 px-2 text-center">Présences</Th>
                                    <Th className="py-2.5 px-2 text-center">Congés</Th>
                                    <Th className="py-2.5 px-2 text-center">Fériés</Th>
                                    <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)] font-bold">Heures Réelles</Th>
                                    <Th className="py-2.5 px-2 text-center text-emerald-600 font-bold">HS 30%</Th>
                                    <Th className="py-2.5 px-2 text-center text-emerald-700 font-bold">HS 50%</Th>
                                    <Th className="py-2.5 px-2 text-center text-indigo-600">Nuit</Th>
                                    <Th className="py-2.5 px-2 text-center text-[var(--color-danger-500)] font-bold">Retenues</Th>
                                    <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Repas (Ar)</Th>
                                    <Th className="py-2.5 px-3 text-center">Déplacement (Ar)</Th>
                                    <Th className="py-2.5 px-2 text-center">Fiche</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {filteredSyntheseData.map((row) => (
                                    <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/50 transition-colors">
                                        <Td className="py-2.5 px-3 font-semibold text-[var(--color-fg)]">
                                            <div className="flex flex-col">
                                                <span>{row.nom} {row.prenom}</span>
                                                <span className="text-[10.5px] text-[var(--color-fg-muted)] font-mono">#{row.matricule}</span>
                                            </div>
                                        </Td>
                                        <Td className="py-2.5 px-2 text-[11px] font-mono text-[var(--color-fg-muted)]">
                                            {row.siteCode || row.siteNom || '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono font-bold text-emerald-600">
                                            {row.nbJoursPresents || 0} j
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono text-sky-600">
                                            {row.nbJoursConge ? `${row.nbJoursConge} j` : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono text-amber-600">
                                            {(row.nbJoursFerieChome || 0) + (row.nbJoursFerieTravaille || 0) > 0 
                                                ? `${(row.nbJoursFerieChome || 0) + (row.nbJoursFerieTravaille || 0)} j`
                                                : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)] font-mono font-bold text-[var(--color-fg)]">
                                            {row.totalHeuresTravaillees || 0}h
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono text-emerald-600 font-bold">
                                            {row.heuresHs30 ? `+${row.heuresHs30}h` : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono text-emerald-700 font-bold">
                                            {row.heuresHs50 ? `+${row.heuresHs50}h` : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono text-indigo-600">
                                            {row.heuresNuitTotal ? `${row.heuresNuitTotal}h` : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center font-mono font-bold text-[var(--color-danger-500)]">
                                            {row.heuresAbsenceRetenues ? `-${row.heuresAbsenceRetenues}h` : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)] font-mono text-xs">
                                            {row.montantRepas ? fmtMoney(row.montantRepas) : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-3 text-center font-mono text-xs">
                                            {row.montantDeplacement ? fmtMoney(row.montantDeplacement) : '-'}
                                        </Td>
                                        <Td className="py-2.5 px-2 text-center">
                                            <Link
                                                to={`/employes/${row.idEmploye}?tab=pointage`}
                                                className="inline-flex items-center justify-center p-1 text-[var(--color-fg-muted)] hover:text-brand-600 hover:bg-brand-500/10 rounded transition-colors"
                                                title="Consulter la fiche individuelle"
                                            >
                                                <Eye size={13} />
                                            </Link>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                )}
            </div>

            {/* MODALES DAF & TRANSMISSION */}
            <ConfirmDialog
                isOpen={showTransmissionModal}
                onClose={() => setShowTransmissionModal(false)}
                onConfirm={handleTransmettreDaf}
                title={`Transmettre la période ${currentPeriodeObj?.libelle || ''} à la DAF ?`}
                message="Toutes les semaines du mois ont été validées et consolidées. Cette action transmet la période à la DAF (statut A_VALIDER) et verrouille la saisie quotidienne des agents."
                confirmLabel="Confirmer et Transmettre"
                cancelLabel="Annuler"
                variant="primary"
                loading={processingAction}
            />

            <ConfirmDialog
                isOpen={showValiderPeriodeModal}
                onClose={() => setShowValiderPeriodeModal(false)}
                onConfirm={() => handleUpdateStatut('VALIDEE')}
                title={`Valider la période ${currentPeriodeObj?.libelle || ''} ?`}
                message="En tant que DAF, cette action valide l'exactitude des heures consolidées. La période passe en statut VALIDÉE (prête pour clôture paie)."
                confirmLabel="Valider la période"
                cancelLabel="Annuler"
                variant="primary"
                loading={processingAction}
            />

            <Modal
                isOpen={showRejetModal}
                onClose={() => { setShowRejetModal(false); setMotifRejet(''); }}
                title={`Rejeter la période ${currentPeriodeObj?.libelle || ''}`}
                size="md"
                footer={
                    <div className="flex items-center justify-end gap-2">
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => { setShowRejetModal(false); setMotifRejet(''); }}
                            disabled={processingAction}
                        >
                            Annuler
                        </Button>
                        <Button
                            type="button"
                            variant="primary"
                            size="sm"
                            onClick={() => handleUpdateStatut('EN_COURS', motifRejet)}
                            disabled={!motifRejet.trim() || processingAction}
                            loading={processingAction}
                            className="font-bold !bg-red-600 hover:!bg-red-700 text-white"
                        >
                            Confirmer le renvoi
                        </Button>
                    </div>
                }
            >
                <div className="space-y-3">
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        La période repassera en statut <strong className="text-amber-600">EN COURS</strong> pour permettre aux agents d'effectuer les rectifications nécessaires.
                    </p>
                    <div>
                        <label className="block text-xs font-bold text-[var(--color-fg)] mb-1">
                            Motif du renvoi / Corrections requises <span className="text-red-500">*</span>
                        </label>
                        <textarea
                            placeholder="Précisez les anomalies constatées ou les corrections attendues par les agents..."
                            value={motifRejet}
                            onChange={(e) => setMotifRejet(e.target.value)}
                            rows={3}
                            className="w-full p-2.5 text-xs bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] focus:border-brand-500 focus:ring-1 focus:ring-brand-500 outline-none rounded-none text-[var(--color-fg)] resize-none"
                        />
                    </div>
                </div>
            </Modal>

            <ConfirmDialog
                isOpen={showCloturerPeriodeModal}
                onClose={() => setShowCloturerPeriodeModal(false)}
                onConfirm={() => handleUpdateStatut('CLOTUREE')}
                title={`Clôturer définitivement la période pour la Paie ?`}
                message="Attention : La clôture est une action immuable qui archive définitivement la période. Elle atteste que les bulletins de salaire sont prêts à être générés."
                confirmLabel="Clôturer définitivement"
                cancelLabel="Annuler"
                variant="danger"
                loading={processingAction}
            />
        </div>
    );
}
