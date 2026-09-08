import { useState, useEffect, useMemo, useCallback } from 'react';
import {
    CheckCircle,
    XCircle,
    Lock,
    AlertTriangle,
    CheckCircle2,
    Clock,
    Users,
    TrendingUp,
    Moon,
    Sun,
    Award,
    Search,
    ClipboardCheck,
    FileSpreadsheet,
    Edit3
} from 'lucide-react';
import { Link } from 'react-router-dom';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Select from '@/components/ui/forms/Select';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import { formatMonnaie } from '@/utils/pointageUtils';
import { exportPointageValidationToExcel } from '@/utils/excelExportUtils';
import DafAjustementPointageModal from '@/components/pointage/DafAjustementPointageModal';

export default function PointageValidationDafTab() {
    const { hasPermission } = useAuth();
    const { toast } = useToast();
    const isDaf = hasPermission('POINTAGE_VALIDATE');

    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [selectedEmployeToAdjust, setSelectedEmployeToAdjust] = useState(null);
    const [sites, setSites] = useState([]);
    const [selectedSite, setSelectedSite] = useState(0);

    const [syntheseData, setSyntheseData] = useState([]);
    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchTerm, setSearchTerm] = useState('');
    const [alertFilter, setAlertFilter] = useState('ALL');

    const [showValiderModal, setShowValiderModal] = useState(false);
    const [showRejetModal, setShowRejetModal] = useState(false);
    const [showCloturerModal, setShowCloturerModal] = useState(false);
    const [motifRejet, setMotifRejet] = useState('');
    const [processing, setProcessing] = useState(false);

    useEffect(() => {
        const init = async () => {
            try {
                const [resPeriodes, resSites] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    organisationService.getSites()
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    const activeP = resPeriodes.find(p => p.statutCode === 'A_VALIDER' || p.statutCode === 'TRANSMIS_DAF')
                        || resPeriodes.find(p => p.statutCode === 'EN_COURS')
                        || resPeriodes[0];
                    setSelectedPeriode(activeP.idPeriodePaie);
                }
                if (resSites) setSites(resSites);
            } catch (err) {
                console.error('Erreur init PointageValidationDafTab:', err);
            }
        };
        init();
    }, []);

    const loadData = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        try {
            const [resSemaines, resSynthese] = await Promise.all([
                pointageSyntheseService.getSemaines({ idPeriodePaie: selectedPeriode, idSite: selectedSite || undefined }),
                pointageSyntheseService.getSynthese({ idPeriodePaie: selectedPeriode, idSite: selectedSite || undefined })
            ]);
            if (resSemaines?.semaines) setSemaines(resSemaines.semaines);
            if (resSynthese?.synthese) setSyntheseData(resSynthese.synthese);
        } catch (err) {
            console.error('Erreur chargement validation DAF:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite]);

    useEffect(() => { loadData(); }, [loadData]);

    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => p.idPeriodePaie === Number(selectedPeriode)) || null,
    [periodes, selectedPeriode]);

    const stCode = currentPeriodeObj?.statutCode;
    const isAValider = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isValidee = stCode === 'VALIDEE';
    const isCloturee = stCode === 'CLOTUREE';
    const isEnCours = stCode === 'EN_COURS' || !stCode;

    const workflowStatus = useMemo(() => {
        const totalSemaines = semaines.length;
        const semainesValideesCount = semaines.filter(s => s.estValidee).length;
        const totalAnomalies = semaines.reduce((acc, s) => acc + (s.nbAnomalies || 0), 0);
        return { totalSemaines, semainesValideesCount, totalAnomalies, allValidees: totalSemaines > 0 && semainesValideesCount === totalSemaines };
    }, [semaines]);

    const kpis = useMemo(() => ({
        totalSalaries: syntheseData.length,
        totalHeures: Math.round(syntheseData.reduce((a, r) => a + (r.totalHeuresTravaillees || 0), 0)),
        totalHs30: Math.round(syntheseData.reduce((a, r) => a + (r.heuresHs30 || 0), 0)),
        totalHs50: Math.round(syntheseData.reduce((a, r) => a + (r.heuresHs50 || 0), 0)),
        totalRetardMins: syntheseData.reduce((a, r) => a + (r.totalMinutesRetard || 0), 0)
    }), [syntheseData]);

    const alertCounts = useMemo(() => ({
        all: syntheseData.length,
        hs: syntheseData.filter(r => (r.heuresHs30 || 0) > 0 || (r.heuresHs50 || 0) > 0).length,
        nuit: syntheseData.filter(r => (r.heuresNuitTotal || 0) > 0).length,
        retard: syntheseData.filter(r => (r.totalMinutesRetard || 0) > 0).length,
        absence: syntheseData.filter(r => (r.heuresAbsenceRetenues || 0) > 0).length,
    }), [syntheseData]);

    const filteredData = useMemo(() => {
        let list = syntheseData;
        if (alertFilter === 'HS') {
            list = list.filter(r => (r.heuresHs30 || 0) > 0 || (r.heuresHs50 || 0) > 0);
        } else if (alertFilter === 'NUIT') {
            list = list.filter(r => (r.heuresNuitTotal || 0) > 0);
        } else if (alertFilter === 'RETARD') {
            list = list.filter(r => (r.totalMinutesRetard || 0) > 0);
        } else if (alertFilter === 'ABSENCE') {
            list = list.filter(r => (r.heuresAbsenceRetenues || 0) > 0);
        }

        if (!searchTerm.trim()) return list;
        const t = searchTerm.toLowerCase();
        return list.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(t)) ||
            (r.prenom && r.prenom.toLowerCase().includes(t)) ||
            (r.matricule && r.matricule.toLowerCase().includes(t))
        );
    }, [syntheseData, alertFilter, searchTerm]);

    const handleExportExcel = () => {
        if (!filteredData || filteredData.length === 0) {
            toast({ title: 'Export impossible', message: 'Aucune donnée disponible à exporter pour cette sélection.', type: 'warning' });
            return;
        }
        const siteObj = sites.find(s => s.idSite === selectedSite);
        const siteNom = siteObj ? (siteObj.libelle || siteObj.codeSite) : 'Tous les sites';
        try {
            exportPointageValidationToExcel({
                periode: currentPeriodeObj,
                siteNom,
                syntheseData: filteredData
            });
            toast({ title: 'Export Excel généré', message: `${filteredData.length} collaborateurs exportés avec succès.`, type: 'success' });
        } catch (err) {
            toast({ title: 'Erreur lors de l’export', message: err?.message || 'Une erreur est survenue.', type: 'error' });
        }
    };

    const handleValiderPeriode = async () => {
        if (!selectedPeriode) return;
        setProcessing(true);
        try {
            const res = await pointageSyntheseService.validerDaf({ idPeriodePaie: selectedPeriode });
            if (res?.success) {
                toast({ title: 'Période validée par la DAF', message: 'La synthèse est gelée. Les agents sont verrouillés.', type: 'success' });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowValiderModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de valider.', type: 'error' });
        } finally { setProcessing(false); }
    };

    const handleRejeterPeriode = async () => {
        if (!selectedPeriode || !motifRejet.trim()) return;
        setProcessing(true);
        try {
            const res = await pointageSyntheseService.rejeterDaf({ idPeriodePaie: selectedPeriode, motif: motifRejet.trim() });
            if (res?.success) {
                toast({ title: 'Période renvoyée', message: 'Les agents ont été notifiés avec votre motif.', type: 'warning' });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowRejetModal(false);
                setMotifRejet('');
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadData();
            }
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de renvoyer.', type: 'error' });
        } finally { setProcessing(false); }
    };

    const handleCloturerPeriode = async () => {
        if (!selectedPeriode) return;
        setProcessing(true);
        try {
            await periodePaieService.updateStatut(selectedPeriode, 'CLOTUREE');
            toast({ title: 'Période clôturée', message: 'La période est archivée. Les bulletins peuvent être générés.', type: 'success' });
            window.dispatchEvent(new Event('refresh-notifications'));
            setShowCloturerModal(false);
            const list = await periodePaieService.getAllPeriodes();
            if (list) setPeriodes(list);
            await loadData();
        } catch (err) {
            toast({ title: 'Erreur', message: err?.response?.data?.error || 'Impossible de clôturer.', type: 'error' });
        } finally { setProcessing(false); }
    };

    if (!isDaf) {
        return (
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-12 text-center text-sm text-[var(--color-fg-muted)]">
                <Lock size={32} className="mx-auto mb-3 text-[var(--color-fg-subtle)] opacity-50" />
                <p className="font-semibold">Accès restreint</p>
                <p className="text-xs mt-1">Cet onglet est réservé à la Direction Administrative et Financière (DAF).</p>
            </div>
        );
    }

    const fmtMoney = formatMonnaie;

    return (
        <>
            <SEO title="Validation Pointage DAF | GALAXAM SIRH" description="Tableau de bord DAF pour la validation et clôture des périodes." />
            <div className="space-y-3 max-w-[1600px] mx-auto">

                {/* Barre de contrôle bien structurée */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 flex flex-wrap items-center justify-between gap-3 shadow-xs">
                    <div className="flex flex-wrap items-center gap-3 flex-1 min-w-[320px]">
                        <Select
                            options={periodes.map(p => ({ value: p.idPeriodePaie, label: `${p.libelle || p.code} — ${p.statutLibelle || p.statutCode}` }))}
                            value={selectedPeriode}
                            onChange={val => setSelectedPeriode(val)}
                            className="h-[32px] text-xs min-w-[220px]"
                        />
                        <Select
                            options={[{ value: 0, label: 'Tous les sites' }, ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))]}
                            value={selectedSite}
                            onChange={val => setSelectedSite(Number(val))}
                            className="h-[32px] text-xs min-w-[180px]"
                        />
                        <div className="relative min-w-[180px] flex-1">
                            <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                            <input type="text" placeholder="Nom, matricule…" value={searchTerm} onChange={e => setSearchTerm(e.target.value)}
                                className="w-full pl-7 pr-3 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 h-[32px]" />
                        </div>
                    </div>

                    <div className="flex items-center gap-3 shrink-0">
                        {currentPeriodeObj && (
                            <Badge variant={isCloturee ? 'success' : isValidee ? 'primary' : isAValider ? 'warning' : 'info'} size="sm" className="font-bold uppercase tracking-wider">
                                {currentPeriodeObj.statutLibelle || currentPeriodeObj.statutCode}
                            </Badge>
                        )}
                        <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={handleExportExcel}
                            icon={<FileSpreadsheet size={14} className="text-emerald-600 dark:text-emerald-400" />}
                            className="text-xs h-[32px] font-medium"
                            title="Exporter les données affichées au format Excel (CSV universel)"
                        >
                            Exporter Excel
                        </Button>
                    </div>
                </div>

                {/* Motif rejet si renvoyé */}
                {currentPeriodeObj?.motifRejet && isEnCours && (
                    <div className="p-3.5 bg-amber-500/10 border border-amber-500/40 flex items-start gap-3">
                        <AlertTriangle size={18} className="text-amber-600 shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <strong className="block text-xs font-bold text-amber-800 dark:text-amber-300">Dernier renvoi agents — motif consigné</strong>
                            <p className="text-xs mt-1 font-mono bg-amber-500/15 p-2 border border-amber-500/30">{currentPeriodeObj.motifRejet}</p>
                        </div>
                    </div>
                )}

                {/* Bandeau d'arbitrage DAF */}
                {(isAValider || isValidee) && (
                    <div className={`border-2 p-4 shadow-sm ${isAValider ? 'bg-amber-500/8 border-amber-500/40' : 'bg-emerald-500/8 border-emerald-500/40'}`}>
                        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                            <div>
                                <div className="flex items-center gap-2 mb-1">
                                    {isAValider && <span className="flex h-2.5 w-2.5 rounded-full bg-amber-500 animate-ping" />}
                                    <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                        {isAValider ? '⚡ Arbitrage requis — Période transmise par les agents' : '✅ Période validée DAF — Prête pour clôture'}
                                    </h3>
                                </div>
                                <p className="text-xs text-[var(--color-fg-muted)]">
                                    {isAValider
                                        ? `${workflowStatus.semainesValideesCount}/${workflowStatus.totalSemaines} semaines consolidées. Examinez les totaux nominatifs puis décidez.`
                                        : 'Les données de pointage sont gelées. Procédez à la clôture immuable pour calculer les rémunérations.'}
                                </p>
                            </div>
                            <div className="flex items-center gap-2 shrink-0">
                                {isAValider && (
                                    <>
                                        <Button type="button" variant="outline" size="sm" onClick={() => setShowRejetModal(true)}
                                            className="font-bold text-[var(--color-danger-500)] border-[var(--color-danger-500)]/30 hover:bg-[var(--color-danger-500)]/10 flex items-center gap-1.5">
                                            <XCircle size={14} /><span>Rejeter avec motif</span>
                                        </Button>
                                        <Button type="button" variant="primary" size="sm" onClick={() => setShowValiderModal(true)}
                                            className="font-bold flex items-center gap-1.5 shadow-xs">
                                            <CheckCircle size={14} /><span>Valider la période</span>
                                        </Button>
                                    </>
                                )}
                                {isValidee && (
                                    <Button type="button" variant="primary" size="sm" onClick={() => setShowCloturerModal(true)}
                                        className="font-bold !bg-emerald-600 hover:!bg-emerald-700 text-white flex items-center gap-1.5 shadow-xs">
                                        <Lock size={14} /><span>Clôturer définitivement pour Paie</span>
                                    </Button>
                                )}
                            </div>
                        </div>
                    </div>
                )}

                {/* Période en cours non transmise */}
                {isEnCours && !currentPeriodeObj?.motifRejet && (
                    <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3.5 flex items-center gap-3 text-xs text-[var(--color-fg-muted)]">
                        <Clock size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                        <div>
                            <span className="font-semibold text-[var(--color-fg)]">Saisie en cours</span>
                            <span className="ml-2">— La période n'a pas encore été transmise par les agents. Aucune action DAF disponible.</span>
                        </div>
                        <Link to="/pointages?tab=synthese" className="ml-auto text-brand-600 hover:underline font-semibold shrink-0 text-[11px]">
                            Voir la synthèse agent →
                        </Link>
                    </div>
                )}

                {/* KPIs mensuels */}
                {!loading && syntheseData.length > 0 && (
                    <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-2.5">
                        {[
                            { label: 'Salariés', value: kpis.totalSalaries, icon: Users, color: 'text-[var(--color-brand-600)]' },
                            { label: 'Heures travaillées', value: `${kpis.totalHeures} h`, icon: Clock, color: 'text-[var(--color-fg)]' },
                            { label: 'HS 30%', value: `+${kpis.totalHs30} h`, icon: TrendingUp, color: 'text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)]' },
                            { label: 'HS 50%', value: `+${kpis.totalHs50} h`, icon: TrendingUp, color: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)]' },
                            { label: 'Retards cumulés', value: kpis.totalRetardMins > 0 ? `${kpis.totalRetardMins} min` : '—', icon: AlertTriangle, color: kpis.totalRetardMins > 0 ? 'text-[var(--color-warning-600)]' : 'text-[var(--color-fg-muted)]' },
                        ].map(card => (
                            <div key={card.label} className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 shadow-xs flex items-center gap-3">
                                <card.icon size={20} className={`${card.color} shrink-0 opacity-80`} />
                                <div>
                                    <div className={`text-base font-bold font-mono ${card.color}`}>{card.value}</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)]">{card.label}</div>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {/* Tableau nominatif mensuel avec vision différentielle & alertes DAF */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                    <div className="px-4 py-2 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex flex-wrap items-center justify-between gap-2">
                        {/* Filtres de vision différentielle DAF */}
                        <div className="flex items-center gap-1.5 overflow-x-auto">
                            {[
                                { id: 'ALL', label: 'Tous', count: alertCounts.all },
                                { id: 'HS', label: 'Heures Sup', count: alertCounts.hs, color: 'text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)]' },
                                { id: 'NUIT', label: 'Nuit', count: alertCounts.nuit, color: 'text-indigo-600 dark:text-indigo-400' },
                                { id: 'RETARD', label: 'Retards', count: alertCounts.retard, color: 'text-amber-600 dark:text-amber-400' },
                                { id: 'ABSENCE', label: 'Déficit / Absences', count: alertCounts.absence, color: 'text-[var(--color-danger-600)] dark:text-[var(--color-danger-400)]' },
                            ].map(tab => {
                                const isActive = alertFilter === tab.id;
                                return (
                                    <button
                                        key={tab.id}
                                        type="button"
                                        onClick={() => setAlertFilter(tab.id)}
                                        className={`px-2.5 py-1 text-[11px] font-medium transition-all cursor-pointer border h-[26px] flex items-center shrink-0 ${
                                            isActive
                                                ? 'bg-brand-600 border-brand-600 text-white font-bold'
                                                : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:border-[var(--color-border)]'
                                        }`}
                                    >
                                        <span>{tab.label}</span>
                                        <span className={`ml-1.5 font-mono text-[10px] ${isActive ? 'opacity-95 font-bold' : tab.count > 0 ? (tab.color || 'font-bold') : 'opacity-60'}`}>
                                            {tab.count}
                                        </span>
                                    </button>
                                );
                            })}
                        </div>

                        <span className="text-xs font-mono text-[var(--color-fg-muted)]">{filteredData.length} salarié(s) affiché(s)</span>
                    </div>
                    {loading ? (
                        <div className="p-12 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                            <Clock size={16} className="animate-spin text-brand-500" />Chargement...
                        </div>
                    ) : filteredData.length === 0 ? (
                        <div className="p-10 text-center text-xs text-[var(--color-fg-muted)]">
                            {syntheseData.length === 0 ? 'Aucune donnée disponible pour cette période.' : 'Aucun résultat pour cette recherche.'}
                        </div>
                    ) : (
                        <div className="overflow-x-auto">
                            <Table className="w-full text-left border-collapse text-[12.5px]">
                                <Thead>
                                    <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                        <Th className="py-2.5 px-4">Salarié</Th>
                                        <Th className="py-2.5 px-3">Site</Th>
                                        <Th className="py-2.5 px-2 text-center">Présences</Th>
                                        <Th className="py-2.5 px-2 text-center">Congés</Th>
                                        <Th className="py-2.5 px-2 text-center">Fériés</Th>
                                        <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">H. Totales</Th>
                                        <Th className="py-2.5 px-2 text-center text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)]">HS 30%</Th>
                                        <Th className="py-2.5 px-2 text-center text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)]">HS 50%</Th>
                                        <Th className="py-2.5 px-2 text-center bg-indigo-500/[0.04] text-indigo-700 dark:text-indigo-400">Nuit</Th>
                                        <Th className="py-2.5 px-2 text-center bg-amber-500/[0.04] text-amber-700 dark:text-amber-400">Dim.</Th>
                                        <Th className="py-2.5 px-2 text-center bg-emerald-500/[0.04] text-emerald-700 dark:text-emerald-400">Férié</Th>
                                        <Th className="py-2.5 px-2 text-center text-[var(--color-danger-500)]">Déficit</Th>
                                        <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                        <Th className="py-2.5 px-3 text-center">Déplact</Th>
                                        <Th className="py-2.5 px-3 text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {filteredData.map((row) => (
                                        <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                            <Td className="py-2.5 px-4 font-semibold">
                                                <Link to={`/employes/${row.idEmploye}?tab=pointage`} className="hover:text-brand-600 hover:underline text-[var(--color-fg)]" title={`${row.nom} ${row.prenom}`}>
                                                    {row.nom} {row.prenom}
                                                </Link>
                                                <span className="block text-[10px] font-mono text-[var(--color-fg-muted)]">{row.matricule} {row.estCadre && '· CADRE'}</span>
                                            </Td>
                                            <Td className="py-2.5 px-3 text-xs text-[var(--color-fg-muted)]">{row.siteNom || `Site #${row.idSite}`}</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursPresents} j</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursConge} j</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursFerieChome} j</Td>
                                            <Td className="py-2.5 px-3 text-center font-mono font-bold border-l border-[var(--color-border-subtle)]">{row.totalHeuresTravaillees} h</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)] font-bold">{row.heuresHs30 > 0 ? `+${row.heuresHs30}h` : '0h'}</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)] font-bold">{row.heuresHs50 > 0 ? `+${row.heuresHs50}h` : '0h'}</Td>
                                            <Td className="py-2.5 px-2 text-center font-mono bg-indigo-500/[0.04]">
                                                {row.heuresNuitTotal > 0 ? <span className="inline-flex items-center gap-1 font-bold text-indigo-700 dark:text-indigo-400"><Moon size={10} />{row.heuresNuitTotal}h</span> : <span className="text-[var(--color-fg-subtle)]">0h</span>}
                                            </Td>
                                            <Td className="py-2.5 px-2 text-center font-mono bg-amber-500/[0.04]">
                                                {row.heuresDimancheTotal > 0 ? <span className="inline-flex items-center gap-1 font-bold text-amber-700 dark:text-amber-400"><Sun size={10} />+{row.heuresDimancheTotal}h</span> : <span className="text-[var(--color-fg-subtle)]">0h</span>}
                                            </Td>
                                            <Td className="py-2.5 px-2 text-center font-mono bg-emerald-500/[0.04]">
                                                {row.heuresFerieTotal > 0 ? <span className="inline-flex items-center gap-1 font-bold text-emerald-700 dark:text-emerald-400"><Award size={10} />+{row.heuresFerieTotal}h</span> : <span className="text-[var(--color-fg-subtle)]">0h</span>}
                                            </Td>
                                            <Td className="py-2.5 px-2 text-center font-mono">
                                                {row.heuresAbsenceRetenues > 0 ? <span className="text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)] font-bold bg-[var(--color-danger-500)]/10 px-1.5 py-0.5">-{row.heuresAbsenceRetenues}h</span> : <span className="text-[var(--color-fg-subtle)]">—</span>}
                                            </Td>
                                            <Td className="py-2.5 px-3 text-center font-mono border-l border-[var(--color-border-subtle)]">{row.nbJoursRepas}j {row.montantRepas > 0 ? `(${fmtMoney(row.montantRepas)})` : ''}</Td>
                                            <Td className="py-2.5 px-3 text-center font-mono">{row.nbJoursDeplacement}j {row.montantDeplacement > 0 ? `(${fmtMoney(row.montantDeplacement)})` : ''}</Td>
                                            <Td className="py-2.5 px-3 text-right">
                                                <Button
                                                    type="button"
                                                    variant="outline"
                                                    size="xs"
                                                    disabled={isCloturee}
                                                    onClick={() => setSelectedEmployeToAdjust(row)}
                                                    icon={<Edit3 size={11} />}
                                                    className="h-6 px-2 text-[11px] font-semibold"
                                                    title="Rectifier directement les pointages de ce salarié"
                                                >
                                                    Rectifier
                                                </Button>
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    )}
                </div>

                {/* Vignettes semaines */}
                {semaines.length > 0 && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-3">
                        <p className="text-[11px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] mb-2">Semaines consolidées</p>
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2">
                            {semaines.map(sem => (
                                <div key={sem.id} className={`p-2.5 border text-xs ${sem.estValidee ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30' : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]'}`}>
                                    <div className="flex items-center justify-between">
                                        <span className="font-semibold truncate">{sem.title}</span>
                                        {sem.estValidee && <CheckCircle2 size={12} className="text-[var(--color-success-600)] shrink-0" />}
                                    </div>
                                    <p className="text-[10px] font-mono text-[var(--color-fg-muted)] mt-0.5">{sem.dates}</p>
                                    {sem.nbAnomalies > 0 && (
                                        <span className="inline-flex items-center gap-0.5 mt-1 text-[10px] font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">
                                            <AlertTriangle size={10} />{sem.nbAnomalies} anomalie(s)
                                        </span>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                )}
            </div>

            {/* Modales d'action DAF */}
            <ConfirmDialog
                isOpen={showValiderModal}
                onClose={() => setShowValiderModal(false)}
                onConfirm={handleValiderPeriode}
                title={`Valider la période ${currentPeriodeObj?.libelle || ''} ?`}
                message="Cette action valide et gèle les heures consolidées pour la paie. La saisie agents sera entièrement verrouillée."
                confirmLabel="Confirmer la validation DAF"
                cancelLabel="Annuler"
                variant="primary"
                loading={processing}
            />

            <Modal
                isOpen={showRejetModal}
                onClose={() => setShowRejetModal(false)}
                title="Renvoyer la période pour correction"
                icon={<XCircle className="text-[var(--color-danger-500)]" size={18} />}
                size="md"
                footer={
                    <div className="flex items-center justify-end gap-2">
                        <Button variant="ghost" size="sm" onClick={() => setShowRejetModal(false)} disabled={processing}>Annuler</Button>
                        <Button variant="danger" size="sm" loading={processing} disabled={processing || !motifRejet.trim()} onClick={handleRejeterPeriode} className="font-bold">
                            Confirmer le renvoi
                        </Button>
                    </div>
                }
            >
                <div className="space-y-3 p-1">
                    <p className="text-xs text-[var(--color-fg-muted)]">La période sera réouverte et les agents notifiés immédiatement avec votre motif.</p>
                    <div>
                        <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">Motif du renvoi <span className="text-[var(--color-danger-500)]">*</span></label>
                        <textarea
                            placeholder="Précisez les anomalies constatées ou les corrections attendues..."
                            value={motifRejet}
                            onChange={e => setMotifRejet(e.target.value)}
                            rows={4}
                            className="w-full p-2.5 text-xs bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] focus:border-brand-500 outline-none rounded-none text-[var(--color-fg)] resize-none"
                        />
                    </div>
                </div>
            </Modal>

            <ConfirmDialog
                isOpen={showCloturerModal}
                onClose={() => setShowCloturerModal(false)}
                onConfirm={handleCloturerPeriode}
                title="Clôturer la période pour génération de la Paie ?"
                message="⚠️ Action irréversible. La clôture archive définitivement la période et déclenche la génération des bulletins de salaire."
                confirmLabel="Clôturer définitivement"
                cancelLabel="Annuler"
                variant="danger"
                loading={processing}
            />

            {/* Modale d'ajustement direct par la DAF */}
            {selectedEmployeToAdjust && (
                <DafAjustementPointageModal
                    isOpen={Boolean(selectedEmployeToAdjust)}
                    onClose={() => setSelectedEmployeToAdjust(null)}
                    idEmploye={selectedEmployeToAdjust.idEmploye}
                    employeNom={`${selectedEmployeToAdjust.nom} ${selectedEmployeToAdjust.prenom}`}
                    matricule={selectedEmployeToAdjust.matricule}
                    idPeriode={Number(selectedPeriode)}
                    onSuccess={loadData}
                />
            )}
        </>
    );
}
