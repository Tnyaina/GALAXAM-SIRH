import { useState, useMemo, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Search, Clock, Layers, CalendarRange, CheckCircle2, AlertTriangle, ArrowRight, Info, Lock, Unlock } from 'lucide-react';
import Select from '@/components/ui/forms/Select';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import SEO from '@/components/system/SEO';
import PointageWorkflowNav from '@/components/pointage/PointageWorkflowNav';
import usePointageSynthese from '@/hooks/pointage/usePointageSynthese';

export default function SynthesePointagePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [sites, setSites] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    
    const activeTab = searchParams.get('tab') || 'hebdo';
    const setActiveTab = (tab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', tab);
            return next;
        });
    };

    const [semaines, setSemaines] = useState([]);
    const [selectedSemaine, setSelectedSemaine] = useState(null);
    const [semaineDetail, setSemaineDetail] = useState([]);
    const [loadingSemaines, setLoadingSemaines] = useState(false);
    const [loadingSemaineDetail, setLoadingSemaineDetail] = useState(false);

    // États de validation
    const [showValiderModal, setShowValiderModal] = useState(false);
    const [showDevaliderModal, setShowDevaliderModal] = useState(false);
    const [validatingSemaine, setValidatingSemaine] = useState(false);
    const [devalidatingSemaine, setDevalidatingSemaine] = useState(false);
    const [actionFeedback, setActionFeedback] = useState(null);

    const {
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        syntheseData,
        loading,
        error
    } = usePointageSynthese(1, 0);

    useEffect(() => {
        const fetchSites = async () => {
            try {
                const res = await organisationService.getSites();
                if (res) {
                    setSites(res);
                    const paramSite = searchParams.get('site');
                    if (paramSite) {
                        setSelectedSite(Number(paramSite));
                    }
                }
            } catch (err) {
                console.error('Erreur chargement sites:', err);
            }
        };
        fetchSites();
    }, [searchParams, setSelectedSite]);

    useEffect(() => {
        const fetchPeriodes = async () => {
            try {
                const list = await periodePaieService.getAllPeriodes();
                if (list && list.length > 0) {
                    setPeriodes(list);
                    const paramPeriode = searchParams.get('periode');
                    const found = list.find(p => String(p.idPeriodePaie) === paramPeriode);
                    if (found) {
                        setSelectedPeriode(found.idPeriodePaie);
                    } else if (list[0]?.idPeriodePaie) {
                        setSelectedPeriode(list[0].idPeriodePaie);
                    }
                }
            } catch (err) {
                console.error('Erreur chargement périodes:', err);
            }
        };
        fetchPeriodes();
    }, [setSelectedPeriode, searchParams]);

    // Charger les semaines backend pour la période sélectionnée
    const fetchSemaines = useCallback(async () => {
        if (!selectedPeriode) return;
        setLoadingSemaines(true);
        try {
            const res = await pointageSyntheseService.getSemaines({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.semaines) {
                setSemaines(res.semaines);
                if (res.semaines.length > 0) {
                    setSelectedSemaine(prev => {
                        if (!prev) return res.semaines[0];
                        const found = res.semaines.find(s => s.id === prev.id);
                        return found || res.semaines[0];
                    });
                }
            }
        } catch (err) {
            console.error('Erreur chargement semaines:', err);
        } finally {
            setLoadingSemaines(false);
        }
    }, [selectedPeriode, selectedSite]);

    useEffect(() => {
        fetchSemaines();
    }, [fetchSemaines]);

    // Charger le détail nominatif des salariés pour la semaine sélectionnée
    const fetchDetail = useCallback(async () => {
        if (!selectedPeriode || !selectedSemaine?.dateDebut || !selectedSemaine?.dateFin) return;
        setLoadingSemaineDetail(true);
        try {
            const res = await pointageSyntheseService.getSemaineDetail({
                idPeriodePaie: selectedPeriode,
                dateDebut: selectedSemaine.dateDebut,
                dateFin: selectedSemaine.dateFin,
                idSite: selectedSite || undefined
            });
            if (res?.detail) {
                setSemaineDetail(res.detail);
            }
        } catch (err) {
            console.error('Erreur chargement détail de la semaine:', err);
        } finally {
            setLoadingSemaineDetail(false);
        }
    }, [selectedPeriode, selectedSemaine, selectedSite]);

    useEffect(() => {
        fetchDetail();
    }, [fetchDetail]);

    // Action de validation de la semaine sélectionnée
    const handleValiderSemaine = async () => {
        if (!selectedPeriode || !selectedSemaine) return;
        setValidatingSemaine(true);
        setActionFeedback(null);
        try {
            const res = await pointageSyntheseService.validerSemaine({
                idPeriodePaie: selectedPeriode,
                dateDebut: selectedSemaine.dateDebut,
                dateFin: selectedSemaine.dateFin,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                setShowValiderModal(false);
                setActionFeedback({ type: 'success', message: `La ${selectedSemaine.title} a été validée et verrouillée avec succès !` });
                await fetchSemaines();
                await fetchDetail();
            }
        } catch (err) {
            console.error('Erreur validation semaine:', err);
            setActionFeedback({ type: 'danger', message: err?.response?.data?.error || 'Erreur lors de la validation de la semaine.' });
        } finally {
            setValidatingSemaine(false);
        }
    };

    // Action de dévalidation / déverrouillage de la semaine sélectionnée
    const handleDevaliderSemaine = async () => {
        if (!selectedPeriode || !selectedSemaine) return;
        setDevalidatingSemaine(true);
        setActionFeedback(null);
        try {
            const res = await pointageSyntheseService.devaliderSemaine({
                idPeriodePaie: selectedPeriode,
                dateDebut: selectedSemaine.dateDebut
            });
            if (res?.success) {
                setShowDevaliderModal(false);
                setActionFeedback({ type: 'success', message: `La ${selectedSemaine.title} a été déverrouillée.` });
                await fetchSemaines();
                await fetchDetail();
            }
        } catch (err) {
            console.error('Erreur déverrouillage semaine:', err);
            setActionFeedback({ type: 'danger', message: err?.response?.data?.error || 'Erreur lors du déverrouillage.' });
        } finally {
            setDevalidatingSemaine(false);
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

    const filteredData = useMemo(() => {
        if (!searchTerm.trim()) return syntheseData;
        const term = searchTerm.toLowerCase().trim();
        return syntheseData.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [syntheseData, searchTerm]);

    const filteredSemaineDetail = useMemo(() => {
        if (!searchTerm.trim()) return semaineDetail;
        const term = searchTerm.toLowerCase().trim();
        return semaineDetail.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [semaineDetail, searchTerm]);

    const fmtMoney = (val) => {
        if (!val || isNaN(val)) return '0 Ar';
        return new Intl.NumberFormat('fr-FR').format(Math.round(val)) + ' Ar';
    };

    // KPIs agrégés de la semaine sélectionnée
    const semKpis = useMemo(() => {
        const totalH = filteredSemaineDetail.reduce((acc, r) => acc + (r.totalHeuresTravaillees || 0), 0);
        const totalHs30 = filteredSemaineDetail.reduce((acc, r) => acc + (r.heuresHs30 || 0), 0);
        const totalHs50 = filteredSemaineDetail.reduce((acc, r) => acc + (r.heuresHs50 || 0), 0);
        const totalRetenues = filteredSemaineDetail.reduce((acc, r) => acc + (r.heuresAbsenceRetenues || 0), 0);
        return {
            totalH: Math.round(totalH * 100) / 100,
            totalHs: Math.round((totalHs30 + totalHs50) * 100) / 100,
            totalHs30: Math.round(totalHs30 * 100) / 100,
            totalHs50: Math.round(totalHs50 * 100) / 100,
            totalRetenues: Math.round(totalRetenues * 100) / 100,
            nbSalaries: filteredSemaineDetail.length
        };
    }, [filteredSemaineDetail]);

    return (
        <>
            <SEO
                title="Synthèse des Pointages & Compensations | GALAXAM SIRH"
                description="Consolidation hebdomadaire et mensuelle des heures travaillées, retards et indemnités pour la paie."
            />

            <div className="space-y-2 max-w-[1600px] mx-auto pb-8">
                {/* Mini titre + breadcrumb */}
                <div className="flex items-center justify-between">
                    <div>
                        <h1 className="text-[17px] font-bold text-[var(--color-fg)]">Synthèse &amp; Compensations</h1>
                        <p className="text-[11.5px] text-[var(--color-fg-muted)] mt-0.5">
                            Consolidez les semaines de travail, compensez les retards et préparez la clôture paie.
                        </p>
                    </div>
                </div>

                {/* Workflow Nav slim */}
                <PointageWorkflowNav />

                {/* Filtres compact (1 ligne) */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] px-3 py-2 shadow-xs flex items-center gap-3 flex-wrap lg:flex-nowrap">
                    <Select
                        options={periodeOptions}
                        value={selectedPeriode}
                        onChange={(val) => {
                            setSelectedPeriode(val);
                            setSearchParams(prev => {
                                const next = new URLSearchParams(prev);
                                if (val) next.set('periode', val);
                                else next.delete('periode');
                                return next;
                            });
                        }}
                        className="h-[32px] text-xs min-w-[200px] flex-1"
                    />
                    <Select
                        options={siteOptions}
                        value={selectedSite}
                        onChange={(val) => {
                            setSelectedSite(val);
                            setSearchParams(prev => {
                                const next = new URLSearchParams(prev);
                                if (val) next.set('site', val);
                                else next.delete('site');
                                return next;
                            });
                        }}
                        className="h-[32px] text-xs min-w-[180px] flex-1"
                    />
                    <div className="relative flex-1 min-w-[160px]">
                        <Search size={12} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Nom, matricule…"
                            value={searchTerm}
                            onChange={(e) => setSearchTerm(e.target.value)}
                            className="w-full pl-7 pr-3 text-xs border border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-brand-500 h-[32px]"
                        />
                    </div>
                </div>

                {/* Onglets Hebdo / Mensuel */}
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] flex shadow-xs">
                    <button
                        type="button"
                        onClick={() => setActiveTab('hebdo')}
                        className={`flex items-center gap-1.5 px-4 py-2 text-[11.5px] font-semibold border-r border-[var(--color-border-subtle)] transition-all cursor-pointer ${activeTab === 'hebdo'
                                ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] font-bold border-b-2 border-b-[var(--color-brand-500)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                            }`}
                    >
                        <Layers size={12} />
                        <span>Synthèse Hebdomadaire</span>
                        <span className="bg-[var(--color-brand-500)]/15 text-[var(--color-brand-500)] px-1.5 py-0.5 text-[10px] font-mono font-bold">
                            {semaines.length} sem.
                        </span>
                    </button>

                    <button
                        type="button"
                        onClick={() => setActiveTab('mensuel')}
                        className={`flex items-center gap-1.5 px-4 py-2 text-[11.5px] font-semibold transition-all cursor-pointer ${activeTab === 'mensuel'
                                ? 'bg-[var(--color-brand-500)]/10 text-[var(--color-brand-500)] font-bold border-b-2 border-b-[var(--color-brand-500)]'
                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)]'
                            }`}
                    >
                        <CalendarRange size={12} />
                        <span>Synthèse Mensuelle (Clôture DAF)</span>
                    </button>
                </div>

                {/* VUE 1 : Synthèse Hebdomadaire Séquentielle */}
                {activeTab === 'hebdo' && (
                    <div className="space-y-3.5">
                        {/* Cartes de Découpage Hebdomadaire */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                            {/* En-tête du bloc */}
                            <div className="px-4 py-2.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between">
                                <div className="flex items-center gap-2">
                                    <Layers size={13} className="text-brand-500" />
                                    <span className="text-[12px] font-bold text-[var(--color-fg)]">Découpage & Validation Séquentielle</span>
                                </div>
                                {selectedSemaine && (
                                    <span className="text-[11px] font-mono font-bold text-brand-700 dark:text-brand-300">
                                        {selectedSemaine.title} sélectionnée
                                    </span>
                                )}
                            </div>

                            {loadingSemaines && (
                                <div className="p-6 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                    <Clock size={14} className="animate-spin text-brand-500" />
                                    Chargement…
                                </div>
                            )}



                            {/* Lignes compactes des semaines — liste tabulaire */}
                            {!loadingSemaines && (
                                <div className="border-t border-[var(--color-border-subtle)]">
                                    {semaines.length === 0 ? (
                                        <div className="p-6 text-center text-xs text-[var(--color-fg-muted)]">
                                            Aucune semaine disponible pour cette période.
                                        </div>
                                    ) : (
                                        <div>
                                            {/* En-tête de la liste */}
                                            <div className="grid grid-cols-[auto_1fr_auto_auto_auto] gap-0 text-[9.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] px-3 py-1.5 bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)]">
                                                <span className="w-[90px]">Semaine</span>
                                                <span>Période</span>
                                                <span className="w-[130px] text-center">Statut</span>
                                                <span className="w-[90px] text-center">Réalisé / Objectif</span>
                                                <span className="w-[60px]"></span>
                                            </div>
                                            {semaines.map((sem) => {
                                                const isSelected = selectedSemaine?.id === sem.id;
                                                const statusColor = sem.estValidee
                                                    ? 'text-emerald-700'
                                                    : sem.ready
                                                        ? 'text-brand-700'
                                                        : sem.nbAnomalies > 0
                                                            ? 'text-amber-700'
                                                            : 'text-[var(--color-fg-muted)]';
                                                return (
                                                    <button
                                                        key={sem.id}
                                                        type="button"
                                                        onClick={() => setSelectedSemaine(sem)}
                                                        className={`w-full grid grid-cols-[auto_1fr_auto_auto_auto] gap-0 items-center px-3 py-1.5 border-b border-[var(--color-border-subtle)] text-left cursor-pointer transition-colors ${
                                                            isSelected
                                                                ? 'bg-[var(--color-brand-500)]/10 border-l-2 border-l-[var(--color-brand-500)]'
                                                                : 'hover:bg-[var(--color-bg-subtle)] border-l-2 border-l-transparent'
                                                        }`}
                                                    >
                                                        {/* Titre semaine */}
                                                        <span className="w-[90px] text-[11px] font-bold text-[var(--color-fg)]">{sem.title}</span>

                                                        {/* Dates */}
                                                        <span className="text-[11px] font-mono text-[var(--color-fg-muted)]">
                                                            {sem.dates}
                                                            <span className="ml-1.5 text-[10px] opacity-70">({sem.nbJoursOuvrables}j)</span>
                                                        </span>

                                                        {/* Statut */}
                                                        <span className={`w-[130px] flex items-center justify-center gap-1 text-[11px] font-semibold ${statusColor}`}>
                                                            {sem.estValidee ? (
                                                                <CheckCircle2 size={11} />
                                                            ) : sem.nbAnomalies > 0 ? (
                                                                <AlertTriangle size={11} />
                                                            ) : null}
                                                            {sem.statut}
                                                        </span>

                                                        {/* Réalisé / Objectif */}
                                                        <span className="w-[90px] text-center font-mono text-[11px] text-[var(--color-fg-muted)]">
                                                            <strong className="text-[var(--color-fg)]">{sem.totalHeures}h</strong>
                                                            <span className="opacity-50"> / {sem.seuilHeures}h</span>
                                                        </span>

                                                        {/* Indicateur sélection */}
                                                        <span className="w-[60px] flex justify-end">
                                                            {isSelected
                                                                ? <span className="text-[10px] font-bold text-brand-600">sélect.</span>
                                                                : <ArrowRight size={11} className="text-[var(--color-border-subtle)]" />
                                                            }
                                                        </span>
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            )}
                        </div>

                        {/* Action feedback */}
                        {actionFeedback && (
                            <Alert
                                variant={actionFeedback.type}
                                title={actionFeedback.type === 'success' ? 'Opération réussie' : 'Attention'}
                                onClose={() => setActionFeedback(null)}
                            >
                                {actionFeedback.message}
                            </Alert>
                        )}

                        {/* TABLEAU RÉCAPITULATIF DÉTAILLÉ DE LA SEMAINE SÉLECTIONNÉE */}
                        {selectedSemaine && (
                            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                                <div className="px-5 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex flex-wrap items-center justify-between gap-3">
                                    <div>
                                        <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] flex items-center gap-2">
                                            <span>Synthèse Nominative — {selectedSemaine.title} ({selectedSemaine.dates})</span>
                                            <span className="text-[11px] font-mono text-[var(--color-fg-muted)] font-normal">
                                                • {selectedSemaine.nbJoursOuvrables} jours ouvrés (seuil {selectedSemaine.seuilHeures}h)
                                            </span>
                                        </h3>
                                    </div>

                                    <div className="flex items-center gap-4 text-xs font-mono">
                                        <span>Total Heures : <strong className="text-[var(--color-fg)]">{semKpis.totalH}h</strong></span>
                                        <span>HS 30% : <strong className="text-indigo-600 font-bold">+{semKpis.totalHs30}h</strong></span>
                                        <span>HS 50% : <strong className="text-indigo-700 font-bold">+{semKpis.totalHs50}h</strong></span>
                                        <span>Retenues : <strong className="text-rose-600 font-bold">-{semKpis.totalRetenues}h</strong></span>
                                    </div>

                                    {/* Action de Validation de la semaine */}
                                    <div className="flex items-center gap-2">
                                        {selectedSemaine.estValidee ? (
                                            <div className="flex items-center gap-2">
                                                <span className="flex items-center gap-1.5 text-xs font-bold text-emerald-700 bg-emerald-50 dark:bg-emerald-950/40 border border-emerald-300 dark:border-emerald-800 px-3 py-1.5">
                                                    <CheckCircle2 size={15} />
                                                    Validée &amp; Verrouillée
                                                </span>
                                                <Button
                                                    variant="ghost"
                                                    size="sm"
                                                    onClick={() => setShowDevaliderModal(true)}
                                                    className="text-xs text-[var(--color-fg-muted)] hover:text-rose-600 cursor-pointer"
                                                >
                                                    <Unlock size={13} className="mr-1" />
                                                    Déverrouiller
                                                </Button>
                                            </div>
                                        ) : selectedSemaine.ready ? (
                                            <Button
                                                variant="primary"
                                                size="sm"
                                                onClick={() => setShowValiderModal(true)}
                                                className="cursor-pointer font-bold shadow-xs flex items-center gap-1.5"
                                            >
                                                <Lock size={14} />
                                                <span>Valider la {selectedSemaine.title}</span>
                                            </Button>
                                        ) : selectedSemaine.nbAnomalies > 0 ? (
                                            <span className="flex items-center gap-1.5 text-xs font-bold text-amber-700 bg-amber-50 dark:bg-amber-950/40 border border-amber-300 px-3 py-1.5">
                                                <AlertTriangle size={15} />
                                                {selectedSemaine.nbAnomalies} anomalie(s) à résoudre
                                            </span>
                                        ) : (() => {
                                            // Identifier les semaines précédentes non validées
                                            const semaineIdx = semaines.findIndex(s => s.id === selectedSemaine.id);
                                            const blocking = semaines.slice(0, semaineIdx).filter(s => !s.estValidee);
                                            const blockingMsg = blocking.length > 0
                                                ? `${blocking.map(s => s.title).join(', ')} doit${blocking.length > 1 ? ' doivent' : ''} être validée${blocking.length > 1 ? 's' : ''} en premier`
                                                : 'Saisie incomplète — pointages quotidiens manquants';
                                            return (
                                                <span className="text-[11px] text-[var(--color-fg-muted)] ">
                                                    {blockingMsg}
                                                </span>
                                            );
                                        })()}
                                    </div>
                                </div>

                                <div className="px-5 py-2.5 bg-[var(--color-brand-500)]/5 border-b border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)] flex items-center gap-2">
                                    <Info size={14} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span>
                                        <strong>Principe de Compensation Hebdomadaire 1:1 :</strong> Le <em>Surplus Brut</em> (Réalisées − Base) éponge d'abord les <em>Retards</em> de la semaine. Le surplus net restant est ensuite distribué en <strong>HS 30 %</strong> (jusqu'à 8h) puis en <strong>HS 50 %</strong>.
                                    </span>
                                </div>

                                {loadingSemaineDetail ? (
                                    <div className="p-10 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                        <Clock size={16} className="animate-spin text-brand-500" />
                                        Chargement du détail de la {selectedSemaine.title}...
                                    </div>
                                ) : filteredSemaineDetail.length === 0 ? (
                                    <div className="p-8 text-center text-xs text-[var(--color-fg-muted)]">
                                        Aucune donnée saisie pour cette semaine.
                                    </div>
                                ) : (
                                    <div className="overflow-x-auto">
                                        <Table className="w-full text-left border-collapse text-[12px]">
                                            <Thead>
                                                <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                                    <Th className="py-2.5 px-3">Salarié</Th>
                                                    <Th className="py-2.5 px-2">Site</Th>
                                                    <Th className="py-2.5 px-1.5 text-center">Jours</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40">Base Normale</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-[var(--color-bg-subtle)]/40">Réalisées</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-[var(--color-bg-subtle)]/40 font-bold text-brand-600">Surplus Brut</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)]">Retards</Th>
                                                    <Th className="py-2.5 px-2 text-center text-emerald-600 font-bold">Compensé 1:1</Th>
                                                    <Th className="py-2.5 px-2 text-center text-rose-600 font-bold">Déficit Restant</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)] text-indigo-600 font-bold">HS 30 %</Th>
                                                    <Th className="py-2.5 px-2 text-center text-indigo-700 font-bold">HS 50 %</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                                    <Th className="py-2.5 px-2 text-center">Déplact</Th>
                                                </Tr>
                                            </Thead>
                                            <Tbody>
                                                {filteredSemaineDetail.map((row) => (
                                                    <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                        <Td className="py-2 px-3 font-semibold">
                                                            {row.nom} {row.prenom}
                                                            <span className="block text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                                {row.matricule} {row.estCadre && '· CADRE'}
                                                            </span>
                                                        </Td>
                                                        <Td className="py-2 px-2 text-xs text-[var(--color-fg-muted)]">
                                                            {row.siteNom || `Site #${row.idSite}`}
                                                        </Td>
                                                        <Td className="py-2 px-1.5 text-center font-mono text-xs">
                                                            {row.nbJoursPresents}j
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                                            {row.heuresNormales || row.nbJoursOuvrables * 8}h
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs font-bold text-[var(--color-fg)]">
                                                            {row.totalHeuresTravaillees}h
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs font-bold text-brand-600">
                                                            {row.surplusBrut > 0 ? `+${row.surplusBrut}h` : '0h'}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)] text-amber-600">
                                                            {row.totalMinutesRetard > 0 ? `${row.totalMinutesRetard}m` : '0m'}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs font-bold text-emerald-600">
                                                            {row.retardCompense > 0 ? (
                                                                <span className="bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 px-1.5 py-0.5 text-[11px] font-mono">
                                                                    ✓ {Math.round(row.retardCompense * 60)}m
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">0m</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs font-bold">
                                                            {row.heuresAbsenceRetenues > 0 ? (
                                                                <span className="text-rose-600">-{row.heuresAbsenceRetenues}h</span>
                                                            ) : (
                                                                <span className="text-emerald-600">0h</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)] text-indigo-600 font-bold">
                                                            {row.heuresHs30 > 0 ? `+${row.heuresHs30}h` : '0h'}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs text-indigo-700 font-bold">
                                                            {row.heuresHs50 > 0 ? `+${row.heuresHs50}h` : '0h'}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)]">
                                                            {row.nbJoursRepas} j ({fmtMoney(row.montantRepas)})
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs">
                                                            {row.nbJoursDeplacement} j
                                                        </Td>
                                                    </Tr>
                                                ))}
                                            </Tbody>
                                        </Table>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                )}

                {/* VUE 2 : Synthèse Mensuelle Globale */}
                {activeTab === 'mensuel' && (
                    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                        <div className="px-5 py-3 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-between">
                            <h3 className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                Tableau Mensuel Consolidé pour la Paie
                            </h3>
                            <span className="text-xs font-mono text-[var(--color-fg-muted)]">
                                {filteredData.length} salarié(s)
                            </span>
                        </div>

                        {loading ? (
                            <div className="p-12 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                <Clock size={16} className="animate-spin text-brand-500" />
                                Chargement de la synthèse mensuelle...
                            </div>
                        ) : error ? (
                            <div className="p-4 text-xs font-bold text-rose-600 bg-rose-50 border-l-4 border-rose-500">
                                {error}
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
                                            <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Heures Totales</Th>
                                            <Th className="py-2.5 px-2 text-center">HS 30 %</Th>
                                            <Th className="py-2.5 px-2 text-center">HS 50 %</Th>
                                            <Th className="py-2.5 px-2 text-center">Nuit</Th>
                                            <Th className="py-2.5 px-2 text-center text-rose-600">Déficit Retenue</Th>
                                            <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                            <Th className="py-2.5 px-3 text-center">Déplacement</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {filteredData.map((row) => (
                                            <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                <Td className="py-2.5 px-4 font-semibold">
                                                    {row.nom} {row.prenom}
                                                    <span className="block text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                        {row.matricule} {row.estCadre && '· CADRE'}
                                                    </span>
                                                </Td>
                                                <Td className="py-2.5 px-3 text-xs text-[var(--color-fg-muted)]">
                                                    {row.siteNom || `Site #${row.idSite}`}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursPresents} j</Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursConge} j</Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">{row.nbJoursFerieChome} j</Td>
                                                <Td className="py-2.5 px-3 text-center font-mono font-bold border-l border-[var(--color-border-subtle)]">
                                                    {row.totalHeuresTravaillees} h
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono text-indigo-600 font-bold">
                                                    {row.heuresHs30 > 0 ? `+${row.heuresHs30}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono text-indigo-700 font-bold">
                                                    {row.heuresHs50 > 0 ? `+${row.heuresHs50}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono">
                                                    {row.heuresNuitTotal > 0 ? `${row.heuresNuitTotal}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono font-bold text-rose-600">
                                                    {row.heuresAbsenceRetenues > 0 ? `-${row.heuresAbsenceRetenues}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-3 text-center font-mono border-l border-[var(--color-border-subtle)]">
                                                    {row.nbJoursRepas} j ({fmtMoney(row.montantRepas)})
                                                </Td>
                                                <Td className="py-2.5 px-3 text-center font-mono">
                                                    {row.nbJoursDeplacement} j ({fmtMoney(row.montantDeplacement)})
                                                </Td>
                                            </Tr>
                                        ))}
                                    </Tbody>
                                </Table>
                            </div>
                        )}
                    </div>
                )}

                {/* Modale de confirmation : Valider la Semaine */}
                <ConfirmDialog
                    isOpen={showValiderModal}
                    onClose={() => setShowValiderModal(false)}
                    onConfirm={handleValiderSemaine}
                    title={`Valider et Verrouiller la ${selectedSemaine?.title} ?`}
                    message={`Cette action va consolider et figer les calculs d'heures réelles, compensations 1:1 et majorations HS (30 % / 50 %) pour la ${selectedSemaine?.title} (${selectedSemaine?.dates}). Elle permettra également de débloquer la semaine suivante.`}
                    confirmLabel="Oui, valider la semaine"
                    cancelLabel="Annuler"
                    variant="primary"
                    loading={validatingSemaine}
                />

                {/* Modale de confirmation : Déverrouiller la Semaine */}
                <ConfirmDialog
                    isOpen={showDevaliderModal}
                    onClose={() => setShowDevaliderModal(false)}
                    onConfirm={handleDevaliderSemaine}
                    title={`Déverrouiller la ${selectedSemaine?.title} ?`}
                    message={`Attention : Le déverrouillage de la ${selectedSemaine?.title} remettra cette semaine en statut modifiable. Vous pourrez à nouveau ajuster les pointages ou régularisations avant de la revalider.`}
                    confirmLabel="Oui, déverrouiller"
                    cancelLabel="Annuler"
                    variant="danger"
                    loading={devalidatingSemaine}
                />
            </div>
        </>
    );
}
