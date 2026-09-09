import { useState, useEffect, useMemo, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import {
    CalendarCheck2,
    AlertTriangle,
    CheckCircle2,
    Clock,
    Send,
    BarChart2,
    Info,
    Lock,
    Layers,
    Calendar,
    Building2
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Select from '@/components/ui/forms/Select';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import SEO from '@/components/system/SEO';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';

/**
 * SuiviPeriodePage — Vue agent de suivi de période & transmission DAF.
 * Remplace l'ancien onglet "Vue Globale" (vue-globale) supprimé.
 * Les actions d'arbitrage et clôture DAF sont dans l'onglet Arbitrage DAF (/pointage?tab=validation).
 */
export default function SuiviPeriodePage({ activePeriode, onTabChange }) {
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
                    const active = found
                        || resPeriodes.find(p => p.statutCode === 'EN_COURS')
                        || resPeriodes[0];
                    setSelectedPeriode(active.idPeriodePaie);
                }
                if (resSites) {
                    setSites(resSites);
                    const paramSite = searchParams.get('site');
                    if (paramSite) setSelectedSite(Number(paramSite));
                    else if (resSites.length > 0) setSelectedSite(resSites[0].idSite);
                }
            } catch (err) {
                console.error('Erreur init SuiviPeriodePage:', err);
            }
        };
        init();
    }, []); // eslint-disable-line react-hooks/exhaustive-deps

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
        const totalAnomalies = semaines.reduce((acc, s) => acc + (s.nbAnomalies || 0), 0);
        const allValidees = totalSemaines > 0 && semainesValideesCount === totalSemaines;
        const progressPercent = totalSemaines > 0 ? Math.round((semainesValideesCount / totalSemaines) * 100) : 0;
        return { totalSemaines, semainesValideesCount, totalAnomalies, allValidees, progressPercent };
    }, [semaines]);

    // Action : Calculer toutes les semaines
    const handleCalculerTout = async () => {
        if (!selectedPeriode) return;
        setCalculating(true);
        try {
            const res = await pointageSyntheseService.calculerTout({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({
                    title: 'Synthèse calculée',
                    message: `${res.nbSemaines || 'Toutes les'} semaines ont été calculées avec succès.`,
                    type: 'success'
                });
                await loadSemaines();
            }
        } catch (err) {
            toast({
                title: 'Erreur de calcul',
                message: err?.response?.data?.error || 'Impossible de calculer la synthèse.',
                type: 'error'
            });
        } finally { setCalculating(false); }
    };

    // Action : Transmettre à la DAF
    const handleTransmettreDaf = async () => {
        if (!selectedPeriode) return;
        setTransmitting(true);
        try {
            const res = await pointageSyntheseService.transmettreDaf({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({
                    title: 'Période transmise à la DAF',
                    message: 'La synthèse a été transmise. La saisie passe en lecture seule jusqu\'à arbitrage.',
                    type: 'success'
                });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowTransmissionModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await loadSemaines();
            }
        } catch (err) {
            toast({
                title: 'Transmission impossible',
                message: err?.response?.data?.error || 'Impossible de transmettre la période à la DAF.',
                type: 'error'
            });
        } finally { setTransmitting(false); }
    };

    const periodeOptions = periodes.map(p => ({
        value: p.idPeriodePaie,
        label: `${p.libelle || p.code} (${p.statutLibelle || p.statutCode})`
    }));

    const siteOptions = [
        { value: 0, label: 'Tous les sites' },
        ...sites.map(s => ({ value: s.idSite, label: `${s.codeSite || s.code || `Site #${s.idSite}`} — ${s.libelle}` }))
    ];

    return (
        <>
            <SEO
                title="Suivi de Période | GALAXAM SIRH"
                description="Suivi du cycle de saisie mensuel, état des semaines et transmission à la DAF."
            />

            <div className="space-y-3 w-full">

                {/* ─── BARRE DE SÉLECTION UNIFIÉE & COMPACTE ──────────────────── */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-3 py-2 sm:px-3.5 sm:py-2.5 flex flex-wrap items-center justify-between gap-2.5 shadow-2xs">
                    <div className="flex flex-wrap items-center gap-2.5">
                        {/* 1. Sélecteur de Période */}
                        <div className="flex items-center gap-1.5 shrink-0">
                            <Calendar size={14} className="text-brand-500 shrink-0" />
                            <Select
                                options={periodeOptions}
                                value={selectedPeriode}
                                onChange={val => {
                                    setSelectedPeriode(val);
                                    setSearchParams(prev => { const n = new URLSearchParams(prev); n.set('periode', val); return n; });
                                }}
                                containerClassName="w-60 sm:w-72"
                                className="h-[32px] text-xs font-semibold"
                            />
                        </div>

                        <div className="h-5 w-px bg-[var(--color-border-subtle)] hidden sm:block shrink-0" />

                        {/* 2. Sélecteur de Site */}
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

                    <div className="flex items-center gap-2 shrink-0 ml-auto">
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

                {/* ─── BANNIÈRE REJET DAF ───────────────────────────────────── */}
                {currentPeriodeObj?.motifRejet && isEnCours && (
                    <div className="p-3.5 bg-amber-500/10 border border-amber-500/40 flex items-start gap-3 shadow-xs">
                        <AlertTriangle size={18} className="text-amber-600 shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <strong className="block text-xs uppercase font-bold tracking-wider text-amber-800 dark:text-amber-300">
                                Période renvoyée par la DAF pour correction
                            </strong>
                            <p className="text-xs mt-1 font-mono bg-amber-500/15 p-2 border border-amber-500/30">
                                {currentPeriodeObj.motifRejet}
                            </p>
                            <p className="text-[11.5px] text-amber-800 dark:text-amber-300 mt-1.5">
                                Régularisez les anomalies demandées, puis recalculez et re-transmettez à la DAF.
                            </p>
                        </div>
                    </div>
                )}

                {/* ─── BANDEAU TRANSMIS/VALIDÉ/CLOTURÉ ────────────────────── */}
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
                                {isCloturee ? 'Période clôturée — Archivée définitivement'
                                 : isValidee ? 'Période validée par la DAF — En attente de clôture'
                                 : 'Période transmise à la DAF — En cours d\'arbitrage'}
                            </strong>
                            <span className="text-[var(--color-fg-muted)]">
                                {isCloturee ? 'La saisie est verrouillée. Les bulletins de paie peuvent être générés.' :
                                 isValidee  ? 'Les données de pointage sont gelées.' :
                                 'La saisie des agents est verrouillée jusqu\'à la décision DAF.'}
                            </span>
                        </div>
                        {isAValider && (
                            <Link to="/pointages?tab=validation" className="text-xs font-bold text-brand-600 hover:underline shrink-0">
                                Arbitrage DAF →
                            </Link>
                        )}
                    </div>
                )}

                {/* ─── PROGRESSION DU MOIS ──────────────────────────────────── */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                    <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                            <BarChart2 size={16} className="text-brand-500" />
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">
                                Progression du Mois — {currentPeriodeObj?.libelle || 'Période active'}
                            </h3>
                        </div>
                        <div className="flex items-center gap-2">
                            {isEnCours && canWrite && (
                                <Button
                                    type="button"
                                    variant="outline"
                                    size="sm"
                                    loading={calculating}
                                    onClick={handleCalculerTout}
                                    className="font-bold text-xs flex items-center gap-1.5 shadow-xs"
                                    title="Calculer et actualiser toutes les semaines du mois"
                                >
                                    <Layers size={13} className={calculating ? 'animate-spin' : ''} />
                                    <span>Calculer tout</span>
                                </Button>
                            )}
                            {workflowStatus.allValidees && isEnCours && canWrite && (
                                <Button
                                    type="button"
                                    variant="primary"
                                    size="sm"
                                    loading={transmitting}
                                    onClick={() => setShowTransmissionModal(true)}
                                    className="font-bold text-xs flex items-center gap-1.5 shadow-xs"
                                >
                                    <Send size={13} />
                                    <span>Transmettre à la DAF</span>
                                </Button>
                            )}
                        </div>
                    </div>

                    {/* Barre de progression */}
                    <div className="mb-4">
                        <div className="flex items-center justify-between text-xs mb-1.5">
                            <span className="text-[var(--color-fg-muted)]">
                                {workflowStatus.semainesValideesCount} / {workflowStatus.totalSemaines} semaine(s) validée(s)
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

                    {/* Grille des semaines */}
                    {loading ? (
                        <div className="text-center text-xs text-[var(--color-fg-muted)] py-8 flex items-center justify-center gap-2">
                            <Clock size={14} className="animate-spin text-brand-500" />Chargement des semaines...
                        </div>
                    ) : semaines.length === 0 ? (
                        <div className="text-center text-xs text-[var(--color-fg-muted)] py-8">
                            Aucune semaine disponible pour cette période.
                        </div>
                    ) : (
                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
                            {semaines.map((sem) => {
                                let statusClass = 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)]';
                                let badgeVariant = 'default';
                                let badgeLabel = 'Saisie en cours';

                                if (sem.estValidee) {
                                    statusClass = 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30';
                                    badgeVariant = 'success';
                                    badgeLabel = 'Calculée';
                                } else if (sem.nbAnomalies > 0) {
                                    statusClass = 'bg-[var(--color-warning-500)]/8 border-[var(--color-warning-500)]/35';
                                    badgeVariant = 'warning';
                                    badgeLabel = `${sem.nbAnomalies} anomalie(s)`;
                                } else if (sem.totalPointages > 0) {
                                    statusClass = 'bg-brand-500/6 border-brand-500/25';
                                    badgeVariant = 'brand';
                                    badgeLabel = 'Prête à calculer';
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
                                        {/* Lien rapide vers la saisie de cette semaine */}
                                        {!isReadOnly && !sem.estValidee && onTabChange && (
                                            <button
                                                type="button"
                                                onClick={() => onTabChange('saisie', { date: sem.dateDebut })}
                                                className="text-[10px] text-brand-600 hover:underline font-semibold text-left cursor-pointer mt-0.5"
                                            >
                                                Saisir →
                                            </button>
                                        )}
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>

                {/* ─── CHECKLIST DE TRANSMISSION ───────────────────────────── */}
                {isEnCours && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4">
                        <div className="flex items-center gap-2 mb-3">
                            <CalendarCheck2 size={16} className="text-brand-500" />
                            <h3 className="text-sm font-bold text-[var(--color-fg)]">Checklist de Clôture Mensuelle</h3>
                        </div>
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                            {/* Étape 1 : Anomalies */}
                            <div className={`p-3 border flex items-start gap-2.5 ${
                                workflowStatus.totalAnomalies === 0
                                    ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30'
                                    : 'bg-[var(--color-warning-500)]/8 border-[var(--color-warning-500)]/35'
                            }`}>
                                {workflowStatus.totalAnomalies === 0
                                    ? <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    : <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />}
                                <div className="text-xs">
                                    <div className="font-bold">1. Anomalies résolues</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {workflowStatus.totalAnomalies === 0
                                            ? 'Aucune anomalie bloquante'
                                            : `${workflowStatus.totalAnomalies} anomalie(s) restante(s)`}
                                    </div>
                                    {workflowStatus.totalAnomalies > 0 && (
                                        <Link to="/pointages?tab=anomalies" className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block">
                                            Régulariser →
                                        </Link>
                                    )}
                                </div>
                            </div>

                            {/* Étape 2 : Semaines calculées */}
                            <div className={`p-3 border flex items-start gap-2.5 ${
                                workflowStatus.allValidees
                                    ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30'
                                    : 'bg-[var(--color-sky-500)]/8 border-[var(--color-sky-500)]/30'
                            }`}>
                                {workflowStatus.allValidees
                                    ? <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    : <Layers size={16} className="text-[var(--color-sky-600)] shrink-0 mt-0.5" />}
                                <div className="text-xs">
                                    <div className="font-bold">2. Semaines calculées</div>
                                    <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                        {workflowStatus.semainesValideesCount} sur {workflowStatus.totalSemaines} semaine(s) prête(s)
                                    </div>
                                    {!workflowStatus.allValidees && canWrite && (
                                        <button onClick={handleCalculerTout} disabled={calculating}
                                            className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block text-left cursor-pointer disabled:opacity-50">
                                            {calculating ? 'Calcul...' : 'Calculer tout →'}
                                        </button>
                                    )}
                                </div>
                            </div>

                            {/* Étape 3 : Transmission */}
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
                                            ? 'Prêt à transmettre !'
                                            : 'En attente des étapes 1 et 2'}
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

                {/* Info : Comment fonctionne ce workflow */}
                <div className="bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] p-3.5 flex items-start gap-2.5 text-xs text-[var(--color-fg-muted)]">
                    <Info size={15} className="text-brand-500 shrink-0 mt-0.5" />
                    <div>
                        <span className="font-semibold text-[var(--color-fg)]">Workflow mensuel en 3 étapes :</span>
                        <span className="ml-2">Résolvez les anomalies → Calculez les semaines → Transmettez à la DAF. La DAF valide ou renvoie depuis l'onglet <Link to="/pointages?tab=validation" className="text-brand-600 hover:underline font-semibold">Pointage → Arbitrage DAF</Link>.</span>
                    </div>
                </div>
            </div>

            {/* Modale de confirmation de transmission */}
            <ConfirmDialog
                isOpen={showTransmissionModal}
                onClose={() => setShowTransmissionModal(false)}
                onConfirm={handleTransmettreDaf}
                title={`Transmettre la période ${currentPeriodeObj?.libelle || ''} à la DAF ?`}
                message="Toutes les semaines ont été calculées. Cette action transmet la période à la DAF pour arbitrage et clôture. La saisie quotidienne sera verrouillée jusqu'à la décision DAF."
                confirmLabel="Confirmer et Transmettre"
                cancelLabel="Annuler"
                variant="primary"
                loading={transmitting}
            />
        </>
    );
}
