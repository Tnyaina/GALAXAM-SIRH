import { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
    CalendarCheck2, AlertTriangle, CheckCircle2, Clock,
    Send, BarChart2, Info, Lock, Layers, Calendar, Building2,
    Search, ShieldAlert, Wrench
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Select from '@/components/ui/forms/Select';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { Avatar } from '@/components/ui/data-display/Avatar';
import { getFullPhotoUrl } from '@/utils/fileUtils';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import usePointageAnomalies from '@/hooks/pointage/usePointageAnomalies';
import { filtrerAnomaliesParType } from '@/utils/pointageUtils';

/**
 * SuiviAnomaliesPage — Etape 2 du cockpit Pointage.
 * Agent : progression mensuelle + anomalies inline + transmission DAF.
 * DAF   : lecture seule (transmission deja faite).
 */
export default function SuiviAnomaliesPage({ activePeriode, onSelectPeriode, onTabChange }) {
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const { toast } = useToast();
    const canWrite = hasPermission('POINTAGE_WRITE');

    const [periodes, setPeriodes] = useState([]);
    const [selectedPeriode, setSelectedPeriode] = useState('');
    const [sites, setSites] = useState([]);
    const [selectedSite, setSelectedSite] = useState(0);

    const [semaines, setSemaines] = useState([]);
    const [loading, setLoading] = useState(true);
    const [transmitting, setTransmitting] = useState(false);
    const [calculating, setCalculating] = useState(false);
    const [showTransmissionModal, setShowTransmissionModal] = useState(false);

    // Anomalies
    const {
        anomalies,
        stats: anomalieStats,
        filters: _anomalieFilters,
        loading: loadingAnomalies,
        updateFilters: updateAnomalieFilters,
        regulariser
    } = usePointageAnomalies();

    const [typeFilter, setTypeFilter] = useState('ALL');
    const [searchAnomalie, setSearchAnomalie] = useState('');
    const [selectedAnomalie, setSelectedAnomalie] = useState(null);
    const [isRegModal, setIsRegModal] = useState(false);
    const [regForm, setRegForm] = useState({
        statutCode: 'PRESENT',
        heureEntreeMatin: '08:00',
        heureSortieMatin: '12:00',
        heureEntreeAm: '13:00',
        heureSortieAm: '17:00',
        motifAjustement: '',
        deplacementMatin: false,
        deplacementAm: false,
        indemniteRepasRecue: false
    });
    const [saving, setSaving] = useState(false);

    // Chargement initial
    useEffect(() => {
        const init = async () => {
            try {
                const [resPeriodes, resSites] = await Promise.all([
                    periodePaieService.getAllPeriodes(),
                    organisationService.getSites()
                ]);
                if (resPeriodes && resPeriodes.length > 0) {
                    setPeriodes(resPeriodes);
                    const paramPeriode = searchParams.get('periode');
                    const found = paramPeriode ? resPeriodes.find(p => String(p.idPeriodePaie) === paramPeriode) : null;
                    const active = (activePeriode ? resPeriodes.find(p => p.idPeriodePaie === activePeriode.idPeriodePaie) : null)
                        || found
                        || resPeriodes.find(p => p.statutCode === 'EN_COURS')
                        || resPeriodes[0];
                    setSelectedPeriode(active.idPeriodePaie);
                    updateAnomalieFilters({ idPeriodePaie: active.idPeriodePaie });
                }
                if (resSites) {
                    setSites(resSites);
                    const paramSite = searchParams.get('site');
                    if (paramSite) setSelectedSite(Number(paramSite));
                    else if (resSites.length > 0) setSelectedSite(resSites[0].idSite);
                }
            } catch (err) {
                console.error('Erreur init SuiviAnomaliesPage:', err);
            }
        };
        init();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

    // Synchronisation réactive avec activePeriode provenant du parent
    useEffect(() => {
        if (activePeriode?.idPeriodePaie && String(activePeriode.idPeriodePaie) !== String(selectedPeriode)) {
            setSelectedPeriode(activePeriode.idPeriodePaie);
            updateAnomalieFilters({ idPeriodePaie: activePeriode.idPeriodePaie });
        }
    }, [activePeriode?.idPeriodePaie]); // eslint-disable-line react-hooks/exhaustive-deps

    const loadSemaines = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoading(true);
        try {
            const res = await pointageSyntheseService.getSemaines({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.semaines) setSemaines(res.semaines);
        } catch (err) {
            console.error('Erreur chargement semaines:', err);
        } finally {
            setLoading(false);
        }
    }, [selectedPeriode, selectedSite]);

    useEffect(() => { loadSemaines(); }, [loadSemaines]);

    // Sync filtres anomalies avec periode/site selectionnes
    useEffect(() => {
        if (selectedPeriode) {
            updateAnomalieFilters({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || 0,
                search: searchAnomalie
            });
        }
    }, [selectedPeriode, selectedSite, searchAnomalie]); // eslint-disable-line react-hooks/exhaustive-deps

    const currentPeriodeObj = useMemo(() =>
        periodes.find(p => p.idPeriodePaie === Number(selectedPeriode)) || null,
    [periodes, selectedPeriode]);

    const stCode = currentPeriodeObj?.statutCode;
    const isEnCours = stCode === 'EN_COURS' || !stCode;
    const isAValider = stCode === 'A_VALIDER' || stCode === 'TRANSMIS_DAF';
    const isValidee = stCode === 'VALIDEE';
    const isCloturee = stCode === 'CLOTUREE';
    const isReadOnly = isAValider || isValidee || isCloturee;

    const workflowStatus = useMemo(() => {
        const totalSemaines = semaines.length;
        const semainesValideesCount = semaines.filter(s => s.estValidee).length;
        const totalAnomalies = anomalieStats?.total || 0;
        const allValidees = totalSemaines > 0 && semainesValideesCount === totalSemaines;
        const progressPercent = totalSemaines > 0 ? Math.round((semainesValideesCount / totalSemaines) * 100) : 0;
        return { totalSemaines, semainesValideesCount, totalAnomalies, allValidees, progressPercent };
    }, [semaines, anomalieStats]);

    const filteredAnomalies = useMemo(() =>
        filtrerAnomaliesParType(anomalies, typeFilter),
    [anomalies, typeFilter]);

    const handleCalculerTout = async () => {
        if (!selectedPeriode) return;
        setCalculating(true);
        try {
            const res = await pointageSyntheseService.calculerTout({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({ title: 'Synthese calculee', message: `${res.nbSemaines || 'Toutes les'} semaines calculees avec succes.`, type: 'success' });
                await loadSemaines();
            }
        } catch (err) {
            toast({ title: 'Erreur de calcul', message: err?.response?.data?.error || 'Impossible de calculer.', type: 'error' });
        } finally { setCalculating(false); }
    };

    const handleTransmettreDaf = async () => {
        if (!selectedPeriode) return;
        setTransmitting(true);
        try {
            const res = await pointageSyntheseService.transmettreDaf({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({ title: 'Periode transmise a la DAF', message: 'La saisie passe en lecture seule jusqu\'a validation.', type: 'success' });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowTransmissionModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadSemaines();
            }
        } catch (err) {
            toast({ title: 'Transmission impossible', message: err?.response?.data?.error || 'Impossible de transmettre.', type: 'error' });
        } finally { setTransmitting(false); }
    };

    const openReg = (anom) => {
        setSelectedAnomalie(anom);
        const isAbsent = anom.typeAnomalie === 'ABSENCE' || anom.statutCode === 'ABSENCE_NON_JUSTIFIEE' || anom.statutCode === 'NON_SAISI';
        setRegForm({
            statutCode: isAbsent ? 'PRESENT' : (anom.statutCode || 'PRESENT'),
            heureEntreeMatin: anom.heureEntreeMatin || '08:00',
            heureSortieMatin: anom.heureSortieMatin || '12:00',
            heureEntreeAm: anom.heureEntreeAm || '13:00',
            heureSortieAm: anom.heureSortieAm || '17:00',
            motifAjustement: ''
        });
        setIsRegModal(true);
    };

    const handleReg = async () => {
        if (!selectedAnomalie) return;
        setSaving(true);
        const ok = await regulariser(selectedAnomalie.idPointage, regForm);
        setSaving(false);
        if (ok) {
            toast({ title: 'Anomalie regularisee', type: 'success' });
            setIsRegModal(false);
        } else {
            toast({ title: 'Erreur lors de la regularisation', type: 'error' });
        }
    };

    const periodeOptions = periodes.map(p => ({
        value: p.idPeriodePaie,
        label: `${p.libelle || p.code} (${p.statutLibelle || p.statutCode})`
    }));

    const siteOptions = [
        { value: 0, label: 'Tous les sites' },
        ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))
    ];

    const typeOptions = [
        { value: 'ALL', label: `Toutes (${anomalieStats?.total || 0})` },
        { value: 'INCOMPLET', label: `Incomplets (${anomalieStats?.incomplets || 0})` },
        { value: 'RETARD', label: `Retards (${anomalieStats?.retards || 0})` },
        { value: 'ABSENCE', label: `Absences (${anomalieStats?.absences || 0})` },
    ];

    return (
        <>
            <SEO
                title="Suivi & Anomalies | GALAXAM SIRH"
                description="Suivi de progression mensuel, gestion des anomalies et transmission a la DAF."
            />

            <div className="space-y-3 w-full">

                {/* Barre de selection */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-3 py-2 flex flex-wrap items-center justify-between gap-2.5 shadow-2xs">
                    <div className="flex flex-wrap items-center gap-2.5">
                        <div className="flex items-center gap-1.5 shrink-0">
                            <Calendar size={14} className="text-brand-500 shrink-0" />
                            <Select
                                options={periodeOptions}
                                value={selectedPeriode}
                                onChange={val => {
                                    setSelectedPeriode(val);
                                    if (onSelectPeriode) {
                                        onSelectPeriode(val);
                                    } else {
                                        setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', val); return n; });
                                    }
                                }}
                                containerClassName="w-60 sm:w-72"
                                className="h-[32px] text-xs font-semibold"
                            />
                        </div>
                        <div className="h-5 w-px bg-[var(--color-border-subtle)] hidden sm:block shrink-0" />
                        <div className="flex items-center gap-1.5 shrink-0">
                            <Building2 size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                            <Select
                                options={siteOptions}
                                value={selectedSite}
                                onChange={val => {
                                    setSelectedSite(Number(val));
                                    setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('site', val); return n; });
                                }}
                                containerClassName="w-44 sm:w-52"
                                className="h-[32px] text-xs font-semibold"
                            />
                        </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        {currentPeriodeObj && (
                            <Badge
                                variant={isCloturee ? 'success' : isValidee ? 'primary' : isAValider ? 'warning' : 'info'}
                                size="sm"
                                className="font-bold uppercase tracking-wider"
                            >
                                {currentPeriodeObj.statutLibelle || currentPeriodeObj.statutCode}
                            </Badge>
                        )}
                    </div>
                </div>

                {/* Banniere rejet DAF */}
                {currentPeriodeObj?.motifRejet && isEnCours && (
                    <div className="p-3.5 bg-amber-500/10 border border-amber-500/40 flex items-start gap-3 shadow-xs">
                        <AlertTriangle size={18} className="text-amber-600 shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <strong className="block text-xs uppercase font-bold tracking-wider text-amber-800 dark:text-amber-300">
                                Periode renvoyee par la DAF pour correction
                            </strong>
                            <p className="text-xs mt-1 font-mono bg-amber-500/15 p-2 border border-amber-500/30">
                                {currentPeriodeObj.motifRejet}
                            </p>
                            <p className="text-[11.5px] text-amber-800 dark:text-amber-300 mt-1.5">
                                Regularisez les anomalies, recalculez et re-transmettez a la DAF.
                            </p>
                        </div>
                    </div>
                )}

                {/* Bandeau transmis/valide/cloture */}
                {isReadOnly && (
                    <div className={`p-3.5 border flex items-center gap-3 ${
                        isCloturee ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30' :
                        isValidee  ? 'bg-brand-500/8 border-brand-500/30' :
                                     'bg-amber-500/8 border-amber-500/30'
                    }`}>
                        {isCloturee ? <CheckCircle2 size={18} className="text-[var(--color-success-600)] shrink-0" /> :
                         isValidee  ? <Lock size={18} className="text-brand-500 shrink-0" /> :
                                      <Send size={18} className="text-amber-600 shrink-0" />}
                        <div className="flex-1 text-xs">
                            <strong className="block font-bold text-[var(--color-fg)]">
                                {isCloturee ? 'Periode cloturee'
                                 : isValidee ? 'Periode validee par la DAF'
                                 : 'Periode transmise a la DAF — En cours d\'arbitrage'}
                            </strong>
                            <span className="text-[var(--color-fg-muted)]">
                                {isCloturee ? 'Bulletins de paie disponibles.' :
                                 isValidee  ? 'Les donnees de pointage sont gelees.' :
                                 'La saisie est verrouillee jusqu\'a la decision DAF.'}
                            </span>
                        </div>
                    </div>
                )}

                {/* ─── PROGRESSION DU MOIS ─── */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <BarChart2 size={16} className="text-brand-500" />
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                Progression — {currentPeriodeObj?.libelle || 'Periode active'}
                            </h3>
                        </div>
                        <div className="flex items-center gap-2">
                            {isEnCours && canWrite && (
                                <Button type="button" variant="outline" size="sm" loading={calculating} onClick={handleCalculerTout}
                                    className="font-bold text-xs flex items-center gap-1.5 shadow-xs">
                                    <Layers size={13} className={calculating ? 'animate-spin' : ''} />
                                    <span>Calculer tout</span>
                                </Button>
                            )}
                            {workflowStatus.allValidees && isEnCours && canWrite && (
                                <Button type="button" variant="primary" size="sm" loading={transmitting}
                                    onClick={() => setShowTransmissionModal(true)}
                                    className="font-bold text-xs flex items-center gap-1.5 shadow-xs">
                                    <Send size={13} />
                                    <span>Transmettre a la DAF</span>
                                </Button>
                            )}
                        </div>
                    </div>

                    {/* Barre de progression */}
                    <div className="mb-4">
                        <div className="flex items-center justify-between text-xs mb-1.5">
                            <span className="text-[var(--color-fg-muted)]">
                                {workflowStatus.semainesValideesCount} / {workflowStatus.totalSemaines} semaine(s) calculee(s)
                            </span>
                            <span className={`font-bold font-mono ${workflowStatus.allValidees ? 'text-[var(--color-success-600)]' : 'text-brand-600'}`}>
                                {workflowStatus.progressPercent}%
                            </span>
                        </div>
                        <div className="w-full h-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] overflow-hidden">
                            <div
                                className={`h-full transition-all duration-500 ${workflowStatus.allValidees ? 'bg-[var(--color-success-500)]' : 'bg-brand-500'}`}
                                style={{ width: `${workflowStatus.progressPercent}%` }}
                            />
                        </div>
                    </div>

                    {/* Cards semaines */}
                    {loading ? (
                        <div className="text-center text-xs text-[var(--color-fg-muted)] py-8 flex items-center justify-center gap-2">
                            <Clock size={14} className="animate-spin text-brand-500" />Chargement...
                        </div>
                    ) : semaines.length === 0 ? (
                        <div className="text-center text-xs text-[var(--color-fg-muted)] py-8">
                            Aucune semaine disponible. Cliquez sur "Calculer tout" pour demarrer.
                        </div>
                    ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
                            {semaines.map((sem) => {
                                let statusClass = 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]';
                                let badgeVariant = 'default';
                                let badgeLabel = 'En saisie';
                                if (sem.estValidee) {
                                    statusClass = 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30';
                                    badgeVariant = 'success';
                                    badgeLabel = 'Calculee';
                                } else if (sem.nbAnomalies > 0) {
                                    statusClass = 'bg-amber-500/8 border-amber-500/35';
                                    badgeVariant = 'warning';
                                    badgeLabel = `${sem.nbAnomalies} anomalie(s)`;
                                } else if (sem.totalPointages > 0) {
                                    statusClass = 'bg-brand-500/6 border-brand-500/25';
                                    badgeVariant = 'brand';
                                    badgeLabel = 'Prete';
                                }
                                return (
                                    <div key={sem.id} className={`p-2.5 border flex flex-col gap-1.5 ${statusClass}`}>
                                        <div className="flex items-center justify-between gap-1">
                                            <span className="text-xs font-bold text-[var(--color-fg)] truncate">{sem.title}</span>
                                            {sem.estValidee && <CheckCircle2 size={13} className="text-[var(--color-success-600)] shrink-0" />}
                                        </div>
                                        <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] truncate">{sem.dates}</p>
                                        <div className="pt-1 border-t border-[var(--color-border-subtle)]/60">
                                            <Badge variant={badgeVariant} size="xs" className="!text-[9px] !px-1.5 font-bold">
                                                {badgeLabel}
                                            </Badge>
                                        </div>
                                        {!isReadOnly && !sem.estValidee && onTabChange && (
                                            <button type="button" onClick={() => onTabChange('saisie', { date: sem.dateDebut })}
                                                className="text-[10px] text-brand-600 hover:underline font-semibold text-left cursor-pointer mt-0.5">
                                                Saisir →
                                            </button>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* ─── ANOMALIES INLINE ─── */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                    <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
                        <div className="flex items-center gap-2">
                            <AlertTriangle size={16} className="text-amber-600" />
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                Anomalies de Pointage
                                {workflowStatus.totalAnomalies > 0 && (
                                    <span className="ml-2 px-2 py-0.5 text-[10px] font-black border bg-amber-500/10 text-amber-700 dark:text-amber-300 border-amber-500/40">
                                        {workflowStatus.totalAnomalies}
                                    </span>
                                )}
                            </h3>
                        </div>
                        <div className="flex items-center gap-2 flex-wrap">
                            <div className="relative">
                                <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                                <input
                                    type="text"
                                    value={searchAnomalie}
                                    onChange={e => setSearchAnomalie(e.target.value)}
                                    placeholder="Rechercher employe..."
                                    className="pl-7 pr-3 h-[30px] text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-muted)] focus:outline-none focus:border-[var(--color-brand-500)] w-40"
                                />
                            </div>
                            <Select
                                options={typeOptions}
                                value={typeFilter}
                                onChange={setTypeFilter}
                                containerClassName="w-40"
                                className="h-[30px] text-xs"
                            />
                        </div>
                    </div>

                    {loadingAnomalies ? (
                        <div className="text-center text-xs text-[var(--color-fg-muted)] py-8 flex items-center justify-center gap-2">
                            <Clock size={14} className="animate-spin text-brand-500" />Chargement des anomalies...
                        </div>
                    ) : filteredAnomalies.length === 0 ? (
                        <div className="flex items-center justify-center gap-3 py-8 text-xs text-[var(--color-fg-muted)]">
                            <CheckCircle2 size={18} className="text-[var(--color-success-600)]" />
                            <span>Aucune anomalie{typeFilter !== 'ALL' ? ' de ce type' : ''} pour cette periode.</span>
                        </div>
                    ) : (
                        <div className="divide-y divide-[var(--color-border-subtle)]">
                            {filteredAnomalies.slice(0, 20).map((anom) => (
                                <div key={anom.idPointage} className="flex items-center gap-3 py-2.5 hover:bg-[var(--color-bg-subtle)] px-1 transition-colors">
                                    <Avatar
                                        src={getFullPhotoUrl(anom.photoUrl)}
                                        name={`${anom.nom} ${anom.prenom}`}
                                        size="sm"
                                    />
                                    <div className="flex-1 min-w-0">
                                        <div className="text-xs font-semibold text-[var(--color-fg)] truncate">
                                            {anom.nom} {anom.prenom}
                                        </div>
                                        <div className="text-[10.5px] text-[var(--color-fg-muted)] flex items-center gap-1.5 flex-wrap mt-0.5">
                                            <span className="font-mono">{anom.datePointage}</span>
                                            {anom.typeAnomalie && (
                                                <span className={`px-1.5 py-0.5 text-[9px] font-bold border ${
                                                    anom.typeAnomalie === 'ABSENCE' ? 'bg-[var(--color-danger-500)]/10 border-[var(--color-danger-500)]/40 text-[var(--color-danger-600)]'
                                                    : anom.typeAnomalie === 'RETARD' ? 'bg-orange-500/10 border-orange-500/40 text-orange-700'
                                                    : 'bg-amber-500/10 border-amber-500/40 text-amber-700'
                                                }`}>
                                                    {anom.typeAnomalie}
                                                </span>
                                            )}
                                            {(anom.descriptionAnomalie || anom.description) && (
                                                <span className="opacity-70 truncate max-w-[240px]">
                                                    {anom.descriptionAnomalie || anom.description}
                                                </span>
                                            )}
                                        </div>
                                    </div>
                                    {canWrite && !isReadOnly && (
                                        <button
                                            type="button"
                                            onClick={() => openReg(anom)}
                                            className="flex items-center gap-1 px-2.5 py-1 text-[10px] font-bold border border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] transition-all cursor-pointer shrink-0"
                                        >
                                            <Wrench size={10} />
                                            Regulariser
                                        </button>
                                    )}
                                </div>
                            ))}
                            {filteredAnomalies.length > 20 && (
                                <div className="text-center text-xs text-[var(--color-fg-muted)] pt-3">
                                    + {filteredAnomalies.length - 20} anomalie(s) supplementaire(s). Affinez les filtres pour les voir.
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* ─── CHECKLIST DE CLOTURE ─── */}
                {isEnCours && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                        <div className="flex items-center gap-2 mb-3">
                            <CalendarCheck2 size={16} className="text-brand-500" />
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">Checklist de Transmission</h3>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                            {/* Etape 1 */}
                            <div className={`p-3 border flex items-start gap-2.5 ${
                                workflowStatus.totalAnomalies === 0
                                    ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30'
                                    : 'bg-amber-500/8 border-amber-500/35'
                            }`}>
                                {workflowStatus.totalAnomalies === 0
                                    ? <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    : <AlertTriangle size={16} className="text-amber-600 shrink-0 mt-0.5" />}
                                <div className="text-xs">
                                    <div className="font-bold">1. Anomalies resolues</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {workflowStatus.totalAnomalies === 0 ? 'Aucune anomalie' : `${workflowStatus.totalAnomalies} a regulariser`}
                                    </div>
                                </div>
                            </div>
                            {/* Etape 2 */}
                            <div className={`p-3 border flex items-start gap-2.5 ${
                                workflowStatus.allValidees
                                    ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30'
                                    : 'bg-sky-500/8 border-sky-500/30'
                            }`}>
                                {workflowStatus.allValidees
                                    ? <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    : <Layers size={16} className="text-sky-600 shrink-0 mt-0.5" />}
                                <div className="text-xs">
                                    <div className="font-bold">2. Semaines calculees</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {workflowStatus.semainesValideesCount}/{workflowStatus.totalSemaines} pretes
                                    </div>
                                    {!workflowStatus.allValidees && canWrite && (
                                        <button onClick={handleCalculerTout} disabled={calculating}
                                            className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block text-left cursor-pointer disabled:opacity-50">
                                            {calculating ? 'Calcul...' : 'Calculer tout →'}
                                        </button>
                                    )}
                                </div>
                            </div>
                            {/* Etape 3 */}
                            <div className={`p-3 border flex items-start gap-2.5 ${
                                workflowStatus.allValidees && workflowStatus.totalAnomalies === 0
                                    ? 'bg-brand-500/8 border-brand-500/35'
                                    : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] opacity-60'
                            }`}>
                                <Send size={16} className={`${workflowStatus.allValidees ? 'text-brand-600' : 'text-[var(--color-fg-muted)]'} shrink-0 mt-0.5`} />
                                <div className="text-xs">
                                    <div className="font-bold">3. Transmission DAF</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {workflowStatus.allValidees && workflowStatus.totalAnomalies === 0
                                            ? 'Pret a transmettre'
                                            : 'En attente etapes 1 et 2'}
                                    </div>
                                    {workflowStatus.allValidees && workflowStatus.totalAnomalies === 0 && canWrite && (
                                        <button onClick={() => setShowTransmissionModal(true)}
                                            className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block cursor-pointer">
                                            Transmettre →
                                        </button>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* Info workflow */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3.5 flex items-start gap-2.5 text-xs text-[var(--color-fg-muted)]">
                    <Info size={15} className="text-brand-500 shrink-0 mt-0.5" />
                    <div>
                        <span className="font-semibold text-[var(--color-fg)]">Workflow :</span>
                        <span className="ml-2">Regularisez les anomalies, calculez toutes les semaines, puis transmettez a la DAF. La DAF examine les details et valide depuis l'etape Arbitrage DAF.</span>
                    </div>
                </div>
            </div>

            {/* Modale confirmation transmission */}
            <ConfirmDialog
                isOpen={showTransmissionModal}
                onClose={() => setShowTransmissionModal(false)}
                onConfirm={handleTransmettreDaf}
                title={`Transmettre ${currentPeriodeObj?.libelle || ''} a la DAF ?`}
                message="Toutes les semaines sont calculees. La saisie sera verrouillee jusqu'a la decision de la DAF."
                confirmLabel="Confirmer et Transmettre"
                cancelLabel="Annuler"
                variant="primary"
                loading={transmitting}
            />

            {/* Modale de regularisation d'anomalie */}
            <Modal
                isOpen={isRegModal}
                onClose={() => setIsRegModal(false)}
                title={`Regulariser — ${selectedAnomalie?.nom} ${selectedAnomalie?.prenom}`}
                size="md"
            >
                {selectedAnomalie && (
                    <div className="space-y-3 text-xs">
                        <div className="bg-amber-500/10 border border-amber-500/30 p-2.5 flex items-center gap-2">
                            <ShieldAlert size={14} className="text-amber-600 shrink-0" />
                            <span className="font-semibold text-amber-800 dark:text-amber-300">
                                {selectedAnomalie.datePointage} — {selectedAnomalie.typeAnomalie || selectedAnomalie.description || 'Anomalie'}
                            </span>
                        </div>
                        <div className="grid grid-cols-2 gap-2">
                            <div>
                                <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Entree matin</label>
                                <input type="time" value={regForm.heureEntreeMatin}
                                    onChange={e => setRegForm(f => ({ ...f, heureEntreeMatin: e.target.value }))}
                                    className="w-full h-8 px-2 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]" />
                            </div>
                            <div>
                                <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Sortie matin</label>
                                <input type="time" value={regForm.heureSortieMatin}
                                    onChange={e => setRegForm(f => ({ ...f, heureSortieMatin: e.target.value }))}
                                    className="w-full h-8 px-2 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]" />
                            </div>
                            <div>
                                <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Entree apres-midi</label>
                                <input type="time" value={regForm.heureEntreeAm}
                                    onChange={e => setRegForm(f => ({ ...f, heureEntreeAm: e.target.value }))}
                                    className="w-full h-8 px-2 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]" />
                            </div>
                            <div>
                                <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Sortie apres-midi</label>
                                <input type="time" value={regForm.heureSortieAm}
                                    onChange={e => setRegForm(f => ({ ...f, heureSortieAm: e.target.value }))}
                                    className="w-full h-8 px-2 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]" />
                            </div>
                        </div>
                        <div>
                            <label className="block text-[10.5px] font-semibold text-[var(--color-fg-muted)] mb-1">Motif de regularisation</label>
                            <textarea value={regForm.motifAjustement}
                                onChange={e => setRegForm(f => ({ ...f, motifAjustement: e.target.value }))}
                                rows={2}
                                placeholder="Ex: Oubli de badgeage, presence confirmee par responsable..."
                                className="w-full px-2 py-1.5 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[var(--color-fg)] placeholder:text-[var(--color-fg-muted)] focus:outline-none focus:border-[var(--color-brand-500)] resize-none"
                            />
                        </div>
                        <div className="flex justify-end gap-2 pt-1">
                            <Button type="button" variant="ghost" size="sm" onClick={() => setIsRegModal(false)}>
                                Annuler
                            </Button>
                            <Button type="button" variant="primary" size="sm" loading={saving} onClick={handleReg}>
                                Regulariser
                            </Button>
                        </div>
                    </div>
                )}
            </Modal>
        </>
    );
}
