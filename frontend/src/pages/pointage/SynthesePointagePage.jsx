import { useState, useMemo, useEffect, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { Search, Clock, Layers, CalendarRange, CheckCircle2, AlertTriangle, Info, Lock, Unlock, CheckCircle, XCircle, Send, Moon, Sun, Award, Check, RotateCw } from 'lucide-react';
import Select from '@/components/ui/forms/Select';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import organisationService from '@/api/admin/organisationService';
import periodePaieService from '@/services/periodePaieService';
import pointageSyntheseService from '@/api/pointage/pointageSyntheseService';
import SEO from '@/components/system/SEO';
import usePointageSynthese from '@/hooks/pointage/usePointageSynthese';
import useDebounce from '@/hooks/useDebounce';
import { Tabs } from '@/components/ui/navigation/Tabs';
import Badge from '@/components/ui/data-display/Badge';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMonnaie } from '@/utils/pointageUtils';
import { calculerKpisSemaine, evaluerConsolidationStatus } from '@/utils/pointageSyntheseUtils';

export default function SynthesePointagePage() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [sites, setSites] = useState([]);
    const [periodes, setPeriodes] = useState([]);
    const [searchTerm, setSearchTerm] = useState('');
    const debouncedSearch = useDebounce(searchTerm, 350);
    
    const activeTab = searchParams.get('subTab') || 'hebdo';
    const setActiveTab = (tab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('subTab', tab);
            return next;
        });
    };

    const [semaines, setSemaines] = useState([]);
    const [selectedSemaine, setSelectedSemaine] = useState(null);
    const [semaineDetail, setSemaineDetail] = useState([]);
    const [loadingSemaines, setLoadingSemaines] = useState(false);
    const [loadingSemaineDetail, setLoadingSemaineDetail] = useState(false);

    const { hasPermission } = useAuth();
    const { toast } = useToast();
    const canValidateDaf = hasPermission('POINTAGE_VALIDATE');

    // États de calcul et validation
    const [calculatingAll, setCalculatingAll] = useState(false);
    const [showValiderModal, setShowValiderModal] = useState(false);
    const [showDevaliderModal, setShowDevaliderModal] = useState(false);
    const [validatingSemaine, setValidatingSemaine] = useState(false);
    const [devalidatingSemaine, setDevalidatingSemaine] = useState(false);
    const [actionFeedback, setActionFeedback] = useState(null);

    // États d'actions DAF sur la période
    const [updatingPeriodeStatut, setUpdatingPeriodeStatut] = useState(false);
    const [transmittingDaf, setTransmittingDaf] = useState(false);
    const [showTransmissionModal, setShowTransmissionModal] = useState(false);
    const [showRejetModal, setShowRejetModal] = useState(false);
    const [showValiderPeriodeModal, setShowValiderPeriodeModal] = useState(false);
    const [showCloturerPeriodeModal, setShowCloturerPeriodeModal] = useState(false);
    const [motifRejet, setMotifRejet] = useState('');

    const {
        selectedPeriode,
        setSelectedPeriode,
        selectedSite,
        setSelectedSite,
        syntheseData,
        loading,
        error,
        loadSynthese
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
                    const paramSemaine = searchParams.get('semaine');
                    const targetSemaine = paramSemaine
                        ? res.semaines.find(s => String(s.id) === paramSemaine || s.dateDebut === paramSemaine)
                        : null;

                    setSelectedSemaine(prev => {
                        if (targetSemaine) return targetSemaine;
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
    }, [selectedPeriode, selectedSite, searchParams]);

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

    // Action de calcul global de toutes les semaines en 1 clic
    const handleCalculerTout = async () => {
        if (!selectedPeriode) return;
        setCalculatingAll(true);
        setActionFeedback(null);
        try {
            const res = await pointageSyntheseService.calculerTout({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({
                    title: 'Synthèse calculée et actualisée',
                    message: `${res.nbSemaines || 'Toutes les'} semaines ont été calculées avec succès. Vous pouvez consulter les résultats ci-dessous.`,
                    type: 'success'
                });
                await fetchSemaines();
                await fetchDetail();
                await loadSynthese();
            }
        } catch (err) {
            console.error('Erreur calcul global synthèse:', err);
            toast({
                title: 'Erreur de calcul',
                message: err?.response?.data?.error || 'Impossible de calculer la synthèse.',
                type: 'error'
            });
        } finally {
            setCalculatingAll(false);
        }
    };

    // Actions DAF directes sur la période
    const handleValiderDaf = async () => {
        if (!selectedPeriode) return;
        setUpdatingPeriodeStatut(true);
        try {
            const res = await pointageSyntheseService.validerDaf({ idPeriodePaie: selectedPeriode });
            if (res?.success) {
                toast({
                    title: 'Période validée par la DAF',
                    message: 'La synthèse de pointage a été validée et gelée pour la paie.',
                    type: 'success'
                });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowValiderPeriodeModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await fetchSemaines();
                await fetchDetail();
                await loadSynthese();
            }
        } catch (err) {
            console.error('Erreur validation DAF:', err);
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || 'Impossible de valider la période.',
                type: 'error'
            });
        } finally {
            setUpdatingPeriodeStatut(false);
        }
    };

    const handleRejeterDaf = async () => {
        if (!selectedPeriode || !motifRejet.trim()) return;
        setUpdatingPeriodeStatut(true);
        try {
            const res = await pointageSyntheseService.rejeterDaf({
                idPeriodePaie: selectedPeriode,
                motif: motifRejet.trim()
            });
            if (res?.success) {
                toast({
                    title: 'Période renvoyée pour correction',
                    message: 'La période a été réouverte pour corrections agents avec notification prioritaire.',
                    type: 'warning'
                });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowRejetModal(false);
                setMotifRejet('');
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await fetchSemaines();
                await fetchDetail();
                await loadSynthese();
            }
        } catch (err) {
            console.error('Erreur renvoi DAF:', err);
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || 'Impossible de renvoyer la période.',
                type: 'error'
            });
        } finally {
            setUpdatingPeriodeStatut(false);
        }
    };

    const handleUpdatePeriodeStatut = async (nouveauStatut, motif = '') => {
        if (nouveauStatut === 'VALIDEE') {
            await handleValiderDaf();
            return;
        }
        if (nouveauStatut === 'EN_COURS' && motif) {
            await handleRejeterDaf();
            return;
        }
        if (!selectedPeriode) return;
        setUpdatingPeriodeStatut(true);
        try {
            await periodePaieService.updateStatut(selectedPeriode, nouveauStatut);
            toast({
                title: nouveauStatut === 'CLOTUREE' ? 'Période clôturée définitivement' : 'Statut mis à jour',
                message: `Le statut de la période a été mis à jour avec succès (${nouveauStatut}).`,
                type: 'success'
            });
            setShowCloturerPeriodeModal(false);
            const list = await periodePaieService.getAllPeriodes();
            if (list) setPeriodes(list);
            await fetchSemaines();
            await loadSynthese();
        } catch (err) {
            console.error('Erreur mise à jour période:', err);
            toast({
                title: 'Erreur',
                message: err?.response?.data?.error || 'Impossible de mettre à jour le statut de la période.',
                type: 'error'
            });
        } finally {
            setUpdatingPeriodeStatut(false);
        }
    };

    // Transmission officielle de la période à la DAF par l'agent
    const handleTransmettreDaf = async () => {
        if (!selectedPeriode) return;
        setTransmittingDaf(true);
        try {
            const res = await pointageSyntheseService.transmettreDaf({
                idPeriodePaie: selectedPeriode,
                idSite: selectedSite || undefined
            });
            if (res?.success) {
                toast({
                    title: 'Période transmise à la DAF',
                    message: 'La synthèse a été transmise avec succès à la DAF. La saisie passe en lecture seule.',
                    type: 'success'
                });
                window.dispatchEvent(new Event('refresh-notifications'));
                setShowTransmissionModal(false);
                const list = await periodePaieService.getAllPeriodes();
                if (list) setPeriodes(list);
                await fetchSemaines();
                await fetchDetail();
                await loadSynthese();
            }
        } catch (err) {
            console.error('Erreur transmission DAF:', err);
            toast({
                title: 'Transmission impossible',
                message: err?.response?.data?.error || 'Impossible de transmettre la période à la DAF.',
                type: 'error'
            });
        } finally {
            setTransmittingDaf(false);
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
        if (!debouncedSearch.trim()) return syntheseData;
        const term = debouncedSearch.toLowerCase().trim();
        return syntheseData.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [syntheseData, debouncedSearch]);

    const filteredSemaineDetail = useMemo(() => {
        if (!debouncedSearch.trim()) return semaineDetail;
        const term = debouncedSearch.toLowerCase().trim();
        return semaineDetail.filter(r =>
            (r.nom && r.nom.toLowerCase().includes(term)) ||
            (r.prenom && r.prenom.toLowerCase().includes(term)) ||
            (r.matricule && r.matricule.toLowerCase().includes(term))
        );
    }, [semaineDetail, debouncedSearch]);

    // Utilitaire de formatage monétaire partagé
    const fmtMoney = formatMonnaie;

    // KPIs agrégés de la semaine sélectionnée délégués à l'utilitaire pur
    const semKpis = useMemo(() => {
        return calculerKpisSemaine(filteredSemaineDetail);
    }, [filteredSemaineDetail]);

    // Préréquis et contrôle de cohérence pour la Consolidation Mensuelle délégués à l'utilitaire pur
    const consolidationStatus = useMemo(() => {
        return evaluerConsolidationStatus(semaines, periodes, selectedPeriode);
    }, [semaines, periodes, selectedPeriode]);

    return (
        <>
            <SEO
                title="Synthèse des Pointages & Compensations | GALAXAM SIRH"
                description="Consolidation hebdomadaire et mensuelle des heures travaillées, retards et indemnités pour la paie."
            />

            <div className="space-y-2 max-w-[1600px] mx-auto pb-8">
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

                {/* BANNIÈRE D'ALERTE : Période renvoyée par la DAF pour correction */}
                {consolidationStatus.currentPeriodeObj?.motifRejet && consolidationStatus.isEnCours && (
                    <div className="p-3.5 bg-amber-500/10 border border-amber-500/40 text-amber-900 dark:text-amber-200 flex items-start gap-3 shadow-xs">
                        <AlertTriangle size={18} className="text-amber-600 shrink-0 mt-0.5" />
                        <div className="flex-1">
                            <strong className="block text-xs uppercase font-bold tracking-wider text-amber-800 dark:text-amber-300">
                                Période renvoyée par la DAF pour correction
                            </strong>
                            <p className="text-xs mt-1 font-mono bg-amber-500/15 p-2 border border-amber-500/30">
                                Motif communiqué par la DAF : {consolidationStatus.currentPeriodeObj.motifRejet}
                            </p>
                            <p className="text-[11.5px] text-amber-800 dark:text-amber-300 mt-1.5">
                                Effectuez les ajustements de saisie ou résolvez les anomalies demandées, puis cliquez sur <strong>Calculer / Actualiser la Synthèse</strong> ci-dessous avant de re-transmettre à la DAF.
                            </p>
                        </div>
                    </div>
                )}

                {/* Onglets Hebdo / Mensuel */}
                <Tabs
                    tabs={[
                        { id: 'hebdo', label: 'Synthèse Hebdomadaire', icon: <Layers size={12} />, count: semaines.length ? `${semaines.length} sem.` : undefined },
                        { id: 'mensuel', label: 'Synthèse Mensuelle (Clôture DAF)', icon: <CalendarRange size={12} /> },
                    ]}
                    activeTab={activeTab}
                    onChange={setActiveTab}
                    variant="underline"
                />

                {/* VUE 1 : Synthèse Hebdomadaire */}
                {activeTab === 'hebdo' && (
                    <div className="space-y-3.5">
                        {/* Cartes de Découpage Hebdomadaire */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                            {/* En-tête du bloc avec actions rapides */}
                            <div className="px-4 py-2.5 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex flex-wrap items-center justify-between gap-3">
                                <div className="flex items-center gap-2">
                                    <Layers size={13} className="text-brand-500" />
                                    <span className="text-[12px] font-bold text-[var(--color-fg)]">Découpage Hebdomadaire &amp; Synthèse des Tronçons (Loi 40h)</span>
                                </div>
                                
                                <div className="flex items-center gap-2">
                                    {consolidationStatus.isEnCours && (
                                        <>
                                            <Button
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                loading={calculatingAll}
                                                disabled={calculatingAll}
                                                onClick={handleCalculerTout}
                                                className="font-bold flex items-center gap-1.5 shadow-xs text-xs"
                                                title="Calcule et actualise l'ensemble des semaines du mois en 1 clic"
                                            >
                                                <RotateCw size={13} className={calculatingAll ? 'animate-spin' : ''} />
                                                <span>Calculer / Actualiser la Synthèse</span>
                                            </Button>

                                            <Button
                                                type="button"
                                                variant="primary"
                                                size="sm"
                                                loading={transmittingDaf}
                                                disabled={transmittingDaf}
                                                onClick={() => setShowTransmissionModal(true)}
                                                className="font-bold flex items-center gap-1.5 shadow-xs text-xs"
                                                title="Transmet la période à la DAF pour vérification et clôture"
                                            >
                                                <Send size={13} />
                                                <span>Transmettre à la DAF</span>
                                            </Button>
                                        </>
                                    )}

                                    {selectedSemaine && (
                                        <span className="text-[11px] font-mono font-bold text-brand-700 dark:text-brand-300 ml-2">
                                            {selectedSemaine.title} sélectionnée
                                        </span>
                                    )}
                                </div>
                            </div>

                            {loadingSemaines && (
                                <div className="p-6 text-center text-xs text-[var(--color-fg-muted)] flex items-center justify-center gap-2">
                                    <Clock size={14} className="animate-spin text-brand-500" />
                                    Chargement…
                                </div>
                            )}

                            {/* Grille de cartes horizontales des semaines (Semainier interactif) */}
                            {!loadingSemaines && (
                                <div className="p-3">
                                    {semaines.length === 0 ? (
                                        <div className="p-6 text-center text-xs text-[var(--color-fg-muted)]">
                                            Aucune semaine disponible pour cette période.
                                        </div>
                                    ) : (
                                        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2.5">
                                            {semaines.map((sem) => {
                                                const isSelected = selectedSemaine?.id === sem.id;
                                                
                                                let badgeVariant;
                                                let badgeLabel;

                                                if (sem.estValidee) {
                                                    badgeVariant = 'success';
                                                    badgeLabel = 'Calculée';
                                                } else if (!consolidationStatus.isEnCours) {
                                                    badgeVariant = 'default';
                                                    badgeLabel = consolidationStatus.isAValider ? 'Transmise DAF' : 'Période fermée';
                                                } else if (sem.nbAnomalies > 0) {
                                                    badgeVariant = 'warning';
                                                    badgeLabel = `${sem.nbAnomalies} anomalie(s)`;
                                                } else if (sem.totalPointages > 0) {
                                                    badgeVariant = 'brand';
                                                    badgeLabel = 'Prête à calculer';
                                                } else {
                                                    badgeVariant = 'default';
                                                    badgeLabel = 'Saisie en cours';
                                                }

                                                return (
                                                    <button
                                                        key={sem.id}
                                                        type="button"
                                                        onClick={() => setSelectedSemaine(sem)}
                                                        className={`p-2.5 text-left border transition-all cursor-pointer flex flex-col justify-between gap-1.5 relative ${
                                                            isSelected
                                                                ? 'bg-brand-500/10 border-brand-500 ring-1 ring-brand-500 shadow-xs'
                                                                : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] hover:border-[var(--color-border)]'
                                                        }`}
                                                    >
                                                        {/* Titre & Coche de validation */}
                                                        <div className="flex items-center justify-between gap-1">
                                                            <span className={`text-xs font-bold ${isSelected ? 'text-brand-700 dark:text-brand-300' : 'text-[var(--color-fg)]'}`}>
                                                                {sem.title}
                                                            </span>
                                                            {sem.estValidee && (
                                                                <CheckCircle2 size={13} className="text-[var(--color-success-600)] shrink-0" />
                                                            )}
                                                        </div>

                                                        {/* Dates & Jours ouvrés */}
                                                        <p className="text-[10.5px] font-mono text-[var(--color-fg-muted)] leading-tight truncate">
                                                            {sem.dates}
                                                        </p>

                                                        {/* Statut épuré et exact */}
                                                        <div className="pt-1 border-t border-[var(--color-border-subtle)]/60 flex items-center justify-between gap-1">
                                                            <Badge variant={badgeVariant} size="xs" className="!text-[9px] !px-1.5 font-bold truncate">
                                                                {badgeLabel}
                                                            </Badge>
                                                        </div>
                                                    </button>
                                                );
                                            })}
                                        </div>
                                    )}
                                </div>
                            )}

                            {/* BANNIÈRE DE TRANSMISSION DIRECTE À LA DAF (Dès que 100% des semaines sont validées) */}
                            {consolidationStatus.allValidees && consolidationStatus.isEnCours && (
                                <div className="mx-3 mb-3 p-3 bg-emerald-500/10 border border-emerald-500/30 flex flex-wrap items-center justify-between gap-3 shadow-xs">
                                    <div className="flex items-center gap-2.5">
                                        <CheckCircle2 size={18} className="text-emerald-600 shrink-0" />
                                        <div>
                                            <span className="text-xs font-bold text-emerald-900 dark:text-emerald-200">
                                                Toutes les semaines du mois sont validées ({consolidationStatus.semainesValideesCount}/{consolidationStatus.totalSemaines}) !
                                            </span>
                                            <p className="text-[11px] text-emerald-700 dark:text-emerald-300 mt-0.5">
                                                Vous pouvez désormais transmettre officiellement la période à la DAF pour arbitrage et clôture.
                                            </p>
                                        </div>
                                    </div>
                                    <Button
                                        type="button"
                                        variant="primary"
                                        size="sm"
                                        onClick={() => setShowTransmissionModal(true)}
                                        className="font-bold flex items-center gap-1.5 shadow-xs shrink-0"
                                    >
                                        <Send size={13} />
                                        <span>Transmettre à la DAF</span>
                                    </Button>
                                </div>
                            )}
                        </div>

                        {/* Action feedback */}
                        {actionFeedback && (
                            <Alert
                                type={actionFeedback.type}
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
                                        <span>HS 30% : <strong className="text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)] font-bold">+{semKpis.totalHs30}h</strong></span>
                                        <span>HS 50% : <strong className="text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)] font-bold">+{semKpis.totalHs50}h</strong></span>
                                        <span>Retenues : <strong className="text-[var(--color-danger-500)] font-bold">-{semKpis.totalRetenues}h</strong></span>
                                    </div>

                                    {/* Action de Validation de la semaine */}
                                    <div className="flex items-center gap-2">
                                        {selectedSemaine.estValidee ? (
                                            <div className="flex items-center gap-2">
                                                <span className="flex items-center gap-1.5 text-xs font-bold text-[var(--color-success-600)] bg-[var(--color-success-500)]/8 dark:bg-[var(--color-success-500)]/5 border border-[var(--color-success-500)]/40 dark:border-[var(--color-success-500)]/30 px-3 py-1.5">
                                                    <CheckCircle2 size={15} />
                                                    Validée &amp; Verrouillée
                                                </span>
                                                {consolidationStatus.isEnCours && (
                                                    <Button
                                                        variant="ghost"
                                                        size="sm"
                                                        onClick={() => setShowDevaliderModal(true)}
                                                        className="text-xs text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] cursor-pointer"
                                                        title="Déverrouiller cette semaine pour recalcul ou modifications (Période en cours)"
                                                    >
                                                        <Unlock size={13} className="mr-1" />
                                                        Déverrouiller
                                                    </Button>
                                                )}
                                            </div>
                                        ) : consolidationStatus.isEnCours && selectedSemaine.ready ? (
                                            <div className="flex items-center gap-2">
                                                {selectedSemaine.nbAnomalies > 0 && (
                                                    <span className="flex items-center gap-1 text-[11px] font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] bg-[var(--color-warning-500)]/10 px-2 py-1 border border-[var(--color-warning-500)]/30">
                                                        <AlertTriangle size={12} />
                                                        {selectedSemaine.nbAnomalies} anomalie(s)
                                                    </span>
                                                )}
                                                <Button
                                                    variant="primary"
                                                    size="sm"
                                                    onClick={() => setShowValiderModal(true)}
                                                    className="cursor-pointer font-bold shadow-xs flex items-center gap-1.5"
                                                >
                                                    <Lock size={14} />
                                                    <span>Valider la {selectedSemaine.title}</span>
                                                </Button>
                                            </div>
                                        ) : (!consolidationStatus.isEnCours) ? (
                                            <span className="text-[11px] font-bold text-[var(--color-fg-muted)]">
                                                Période {consolidationStatus.currentPeriodeObj?.statutLibelle || 'Verrouillée'} (Lecture seule)
                                            </span>
                                        ) : (
                                            <div className="flex items-center gap-2">
                                                <Button
                                                    variant="outline"
                                                    size="sm"
                                                    onClick={() => setShowValiderModal(true)}
                                                    className="cursor-pointer font-bold shadow-xs flex items-center gap-1.5 text-xs"
                                                >
                                                    <RotateCw size={13} />
                                                    <span>Calculer cette semaine</span>
                                                </Button>
                                            </div>
                                        )}
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
                                        <Table className="w-full text-left border-collapse text-[12.5px]">
                                            <Thead>
                                                <Tr className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] text-[10.5px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                                                    <Th className="py-2.5 px-3">Salarié</Th>
                                                    <Th className="py-2.5 px-2">Site</Th>
                                                    <Th className="py-2.5 px-1.5 text-center">Jours</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)]">Base</Th>
                                                    <Th className="py-2.5 px-2 text-center">Réalisées</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-brand-500/[0.06] border-x border-[var(--color-border-subtle)] text-brand-700 dark:text-brand-300 font-bold">Surplus</Th>
                                                    <Th className="py-2.5 px-2 text-center">Retards</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-[var(--color-bg-subtle)]/50">Compensé</Th>
                                                    <Th className="py-2.5 px-2 text-center">Déficit</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)] bg-[var(--color-sky-500)]/[0.04]">HS 30%</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-[var(--color-purple-500)]/[0.04]">HS 50%</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-indigo-500/[0.04] text-indigo-700 dark:text-indigo-400">Nuit</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-amber-500/[0.04] text-amber-700 dark:text-amber-400">Dim. +40%</Th>
                                                    <Th className="py-2.5 px-2 text-center bg-emerald-500/[0.04] text-emerald-700 dark:text-emerald-400">Férié +100%</Th>
                                                    <Th className="py-2.5 px-2 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                                    <Th className="py-2.5 px-2 text-center">Déplact</Th>
                                                </Tr>
                                            </Thead>
                                            <Tbody>
                                                {filteredSemaineDetail.map((row) => (
                                                    <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                        <Td className="py-2 px-3 font-semibold">
                                                            <div className="truncate max-w-[200px]">
                                                                <Link
                                                                    to={`/employes/${row.idEmploye}?tab=pointage`}
                                                                    className="hover:text-brand-600 hover:underline text-[var(--color-fg)]"
                                                                    title={`${row.nom} ${row.prenom}`}
                                                                >
                                                                    {row.nom} {row.prenom}
                                                                </Link>
                                                            </div>
                                                            <div className="flex items-center gap-1.5 mt-0.5 text-[10px] font-mono text-[var(--color-fg-muted)]">
                                                                <span>{row.matricule}</span>
                                                                {row.estCadre && (
                                                                    <span className="text-[9px] font-bold uppercase px-1 py-0.2 rounded bg-neutral-500/10 text-neutral-600 dark:text-neutral-300 border border-neutral-500/20">
                                                                        CADRE
                                                                    </span>
                                                                )}
                                                                {row.dispensePointage && (
                                                                    <span className="text-[9px] font-bold uppercase px-1 py-0.2 rounded bg-brand-500/10 text-brand-700 dark:text-brand-300 border border-brand-500/20">
                                                                        AUTONOME
                                                                    </span>
                                                                )}
                                                            </div>
                                                        </Td>
                                                        <Td className="py-2 px-2 text-xs text-[var(--color-fg-muted)]">
                                                            {row.siteNom || `Site #${row.idSite}`}
                                                        </Td>
                                                        <Td className="py-2 px-1.5 text-center font-mono text-xs">
                                                            {row.nbJoursPresents}j
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                                                            {row.heuresNormales !== undefined ? row.heuresNormales : (row.nbJoursOuvrables || 0) * 8}h
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs font-bold text-[var(--color-fg)]">
                                                            {row.totalHeuresTravaillees}h
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-brand-500/[0.06] border-x border-[var(--color-border-subtle)]">
                                                            {!row.estCadre && row.surplusBrut > 0 ? (
                                                                <span className="font-bold text-brand-700 dark:text-brand-300">+{row.surplusBrut}h</span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs">
                                                            {row.totalMinutesRetard > 0 ? (
                                                                <span className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)] bg-[var(--color-warning-500)]/10 px-1.5 py-0.5">{row.totalMinutesRetard}m</span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-[var(--color-bg-subtle)]/50">
                                                            {row.retardCompense > 0 ? (
                                                                <span className="inline-flex items-center gap-1 bg-[var(--color-success-500)]/15 text-[var(--color-success-700)] dark:text-[var(--color-success-400)] font-bold px-1.5 py-0.5 text-[11px] font-mono">
                                                                    <Check size={11} className="stroke-[3]" />
                                                                    {Math.round(row.retardCompense * 60)}m
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs">
                                                            {row.heuresAbsenceRetenues > 0 ? (
                                                                <span className="text-[var(--color-danger-700)] dark:text-[var(--color-danger-400)] font-bold bg-[var(--color-danger-500)]/10 px-1.5 py-0.5">-{row.heuresAbsenceRetenues}h</span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)] bg-[var(--color-sky-500)]/[0.04]">
                                                            {!row.estCadre && row.heuresHs30 > 0 ? (
                                                                <span className="text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)] font-bold bg-[var(--color-sky-500)]/15 px-1.5 py-0.5">+{row.heuresHs30}h</span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-[var(--color-purple-500)]/[0.04]">
                                                            {!row.estCadre && row.heuresHs50 > 0 ? (
                                                                <span className="text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)] font-bold bg-[var(--color-purple-500)]/15 px-1.5 py-0.5">+{row.heuresHs50}h</span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-indigo-500/[0.04]">
                                                            {row.heuresNuitTotal > 0 ? (
                                                                <span className="inline-flex items-center gap-1 font-bold text-indigo-700 dark:text-indigo-400 bg-indigo-500/10 px-1.5 py-0.5 rounded">
                                                                    <Moon size={10} />
                                                                    {row.heuresNuitTotal}h
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-amber-500/[0.04]">
                                                            {row.heuresDimancheTotal > 0 ? (
                                                                <span className="inline-flex items-center gap-1 font-bold text-amber-700 dark:text-amber-400 bg-amber-500/10 px-1.5 py-0.5 rounded">
                                                                    <Sun size={10} />
                                                                    +{row.heuresDimancheTotal}h
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs bg-emerald-500/[0.04]">
                                                            {row.heuresFerieTotal > 0 ? (
                                                                <span className="inline-flex items-center gap-1 font-bold text-emerald-700 dark:text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded">
                                                                    <Award size={10} />
                                                                    +{row.heuresFerieTotal}h
                                                                </span>
                                                            ) : (
                                                                <span className="text-[var(--color-fg-subtle)]">-</span>
                                                            )}
                                                        </Td>
                                                        <Td className="py-2 px-2 text-center font-mono text-xs border-l border-[var(--color-border-subtle)]">
                                                            {row.nbJoursRepas} j {row.montantRepas > 0 ? `(${fmtMoney(row.montantRepas)})` : ''}
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

                {/* VUE 2 : Synthèse Mensuelle Globale & Clôture Guidée */}
                {activeTab === 'mensuel' && (
                    <div className="space-y-3.5">
                        {/* 1. PANNEAU GUIDÉ : CHECKLIST DE CONSOLIDATION DU MOIS */}
                        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs">
                            <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                                <div>
                                    <h3 className="text-sm font-bold text-[var(--color-fg)] flex items-center gap-2">
                                        <CalendarRange size={16} className="text-brand-500" />
                                        <span>Consolidation Mensuelle &amp; Clôture Paie</span>
                                        {consolidationStatus.currentPeriodeObj && (
                                            <Badge variant={consolidationStatus.isCloturee ? 'success' : 'info'} size="sm">
                                                {consolidationStatus.currentPeriodeObj.statutLibelle || consolidationStatus.currentPeriodeObj.statutCode}
                                            </Badge>
                                        )}
                                    </h3>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
                                        La synthèse mensuelle consolide les heures réelles et les majorations calculées semaine par semaine.
                                    </p>
                                </div>

                                {canValidateDaf && (
                                    <div className="flex items-center gap-2 shrink-0">
                                        <Link to="/paie?tab=periodes">
                                            <Button variant="secondary" size="sm" icon={<CalendarRange size={13} />}>
                                                Gérer les Périodes
                                            </Button>
                                        </Link>
                                    </div>
                                )}
                            </div>

                            {/* ÉTAPES DU WORKFLOW GUIDÉ */}
                            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mt-3.5">
                                {/* Étape 1 : Anomalies */}
                                <div className={`p-3 border flex items-start gap-2.5 ${
                                    consolidationStatus.totalAnomalies === 0
                                        ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30 text-[var(--color-fg)]'
                                        : 'bg-[var(--color-warning-500)]/8 border-[var(--color-warning-500)]/35 text-[var(--color-fg)]'
                                }`}>
                                    {consolidationStatus.totalAnomalies === 0 ? (
                                        <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    ) : (
                                        <AlertTriangle size={16} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                                    )}
                                    <div className="text-xs">
                                        <div className="font-bold">1. Contrôle Saisie</div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                            {consolidationStatus.totalAnomalies === 0
                                                ? 'Aucune anomalie bloquante'
                                                : `${consolidationStatus.totalAnomalies} anomalie(s) restante(s)`}
                                        </div>
                                        {consolidationStatus.totalAnomalies > 0 && (
                                            <Link to="/pointages/anomalies" className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block">
                                                Régulariser →
                                            </Link>
                                        )}
                                    </div>
                                </div>

                                {/* Étape 2 : Semaines validées */}
                                <div className={`p-3 border flex items-start gap-2.5 ${
                                    consolidationStatus.allValidees
                                        ? 'bg-[var(--color-success-500)]/6 border-[var(--color-success-500)]/30 text-[var(--color-fg)]'
                                        : 'bg-[var(--color-sky-500)]/8 border-[var(--color-sky-500)]/30 text-[var(--color-fg)]'
                                }`}>
                                    {consolidationStatus.allValidees ? (
                                        <CheckCircle2 size={16} className="text-[var(--color-success-600)] shrink-0 mt-0.5" />
                                    ) : (
                                        <Layers size={16} className="text-[var(--color-sky-600)] shrink-0 mt-0.5" />
                                    )}
                                    <div className="text-xs">
                                        <div className="font-bold">2. Semaines Verrouillées</div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                            {consolidationStatus.semainesValideesCount} sur {consolidationStatus.totalSemaines} semaine(s) validée(s)
                                        </div>
                                        {consolidationStatus.allValidees && consolidationStatus.isEnCours && (
                                            <div className="mt-2 pt-2 border-t border-[var(--color-border-subtle)]">
                                                <Button
                                                    type="button"
                                                    variant="primary"
                                                    size="xs"
                                                    onClick={() => setShowTransmissionModal(true)}
                                                    className="font-bold flex items-center gap-1.5 shadow-xs"
                                                >
                                                    <Send size={12} />
                                                    <span>Transmettre à la DAF</span>
                                                </Button>
                                            </div>
                                        )}
                                        {!consolidationStatus.allValidees && (
                                            <button
                                                onClick={() => setActiveTab('hebdo')}
                                                className="text-[11px] text-brand-600 hover:underline font-bold mt-1 inline-block text-left cursor-pointer"
                                            >
                                                Valider l'hebdo →
                                            </button>
                                        )}
                                    </div>
                                </div>

                                {/* Étape 3 : Statut Paie & Décision DAF */}
                                <div className={`p-3 border flex items-start gap-2.5 ${
                                    consolidationStatus.isCloturee
                                        ? 'bg-[var(--color-success-500)]/8 border-[var(--color-success-500)]/30'
                                        : consolidationStatus.isValidee
                                        ? 'bg-[var(--color-brand-500)]/8 border-brand-500/35'
                                        : consolidationStatus.isAValider
                                        ? 'bg-[var(--color-warning-500)]/10 border-[var(--color-warning-500)]/40'
                                        : consolidationStatus.allValidees
                                        ? 'bg-brand-500/8 border-brand-500/35'
                                        : 'bg-[var(--color-bg-subtle)] border-[var(--color-border-subtle)] opacity-70'
                                }`}>
                                    <Lock size={16} className={consolidationStatus.isCloturee ? 'text-[var(--color-success-600)] shrink-0 mt-0.5' : 'text-[var(--color-fg-muted)] shrink-0 mt-0.5'} />
                                    <div className="text-xs flex-1">
                                        <div className="font-bold flex items-center justify-between">
                                            <span>3. Décision &amp; Clôture DAF</span>
                                            {consolidationStatus.isAValider && (
                                                <Badge variant="warning" size="xs" className="font-bold animate-pulse">
                                                    En attente DAF
                                                </Badge>
                                            )}
                                        </div>
                                        <div className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                            {consolidationStatus.isCloturee
                                                ? 'Période clôturée définitivement (archive paie)'
                                                : consolidationStatus.isValidee
                                                ? 'Période validée par la DAF (prête pour clôture)'
                                                : consolidationStatus.isAValider
                                                ? 'Période transmise par les agents pour arbitrage DAF'
                                                : consolidationStatus.allValidees
                                                ? 'Toutes les semaines sont prêtes pour validation DAF'
                                                : 'En attente de validation des semaines'}
                                        </div>

                                        {/* Actions rapides DAF directement accessibles sans quitter la page */}
                                        {canValidateDaf && (
                                            <div className="flex flex-wrap items-center gap-2 mt-2 pt-2 border-t border-[var(--color-border-subtle)]">
                                                {/* Bouton Valider Période (si A_VALIDER ou si toutes les semaines sont consolidées) */}
                                                {!consolidationStatus.isValidee && !consolidationStatus.isCloturee && (
                                                    <Button
                                                        type="button"
                                                        variant="primary"
                                                        size="xs"
                                                        onClick={() => setShowValiderPeriodeModal(true)}
                                                        className="font-bold flex items-center gap-1.5"
                                                    >
                                                        <CheckCircle size={12} />
                                                        <span>Valider la période</span>
                                                    </Button>
                                                )}

                                                {/* Bouton Rejeter / Renvoyer pour correction */}
                                                {(consolidationStatus.isAValider || consolidationStatus.isValidee) && (
                                                    <Button
                                                        type="button"
                                                        variant="outline"
                                                        size="xs"
                                                        onClick={() => setShowRejetModal(true)}
                                                        className="font-bold text-[var(--color-danger-500)] border-[var(--color-danger-500)]/30 hover:bg-[var(--color-danger-500)]/10 flex items-center gap-1"
                                                    >
                                                        <XCircle size={12} />
                                                        <span>Rejeter / Renvoyer</span>
                                                    </Button>
                                                )}

                                                {/* Bouton Clôture Définitive (quand validée) */}
                                                {consolidationStatus.isValidee && (
                                                    <Button
                                                        type="button"
                                                        variant="primary"
                                                        size="xs"
                                                        onClick={() => setShowCloturerPeriodeModal(true)}
                                                        className="font-bold !bg-emerald-600 hover:!bg-emerald-700 text-white flex items-center gap-1"
                                                    >
                                                        <Lock size={12} />
                                                        <span>Clôturer pour Paie</span>
                                                    </Button>
                                                )}
                                            </div>
                                        )}
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* 2. TABLEAU MENSUEL CONSOLIDÉ */}
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
                            <div className="p-4 text-xs font-bold text-[var(--color-danger-500)] bg-[var(--color-danger-500)]/8 border-l-4 border-[var(--color-danger-500)]">
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
                                            <Th className="py-2.5 px-2 text-center bg-indigo-500/[0.04] text-indigo-700 dark:text-indigo-400">Nuit</Th>
                                            <Th className="py-2.5 px-2 text-center bg-amber-500/[0.04] text-amber-700 dark:text-amber-400">Dim. +40%</Th>
                                            <Th className="py-2.5 px-2 text-center bg-emerald-500/[0.04] text-emerald-700 dark:text-emerald-400">Férié +100%</Th>
                                            <Th className="py-2.5 px-2 text-center text-[var(--color-danger-500)]">Déficit Retenue</Th>
                                            <Th className="py-2.5 px-3 text-center border-l border-[var(--color-border-subtle)]">Repas</Th>
                                            <Th className="py-2.5 px-3 text-center">Déplacement</Th>
                                        </Tr>
                                    </Thead>
                                    <Tbody>
                                        {filteredData.map((row) => (
                                            <Tr key={row.idEmploye} className="border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-bg-subtle)]/40 transition-colors">
                                                <Td className="py-2.5 px-4 font-semibold">
                                                    <Link
                                                        to={`/employes/${row.idEmploye}?tab=pointage`}
                                                        className="hover:text-brand-600 hover:underline text-[var(--color-fg)]"
                                                        title={`${row.nom} ${row.prenom}`}
                                                    >
                                                        {row.nom} {row.prenom}
                                                    </Link>
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
                                                <Td className="py-2.5 px-2 text-center font-mono text-[var(--color-sky-700)] dark:text-[var(--color-sky-400)] font-bold">
                                                    {row.heuresHs30 > 0 ? `+${row.heuresHs30}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono text-[var(--color-purple-700)] dark:text-[var(--color-purple-400)] font-bold">
                                                    {row.heuresHs50 > 0 ? `+${row.heuresHs50}h` : '0h'}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono bg-indigo-500/[0.04]">
                                                    {row.heuresNuitTotal > 0 ? (
                                                        <span className="inline-flex items-center gap-1 font-bold text-indigo-700 dark:text-indigo-400">
                                                            <Moon size={10} />
                                                            {row.heuresNuitTotal}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">0h</span>
                                                    )}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono bg-amber-500/[0.04]">
                                                    {row.heuresDimancheTotal > 0 ? (
                                                        <span className="inline-flex items-center gap-1 font-bold text-amber-700 dark:text-amber-400">
                                                            <Sun size={10} />
                                                            +{row.heuresDimancheTotal}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">0h</span>
                                                    )}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono bg-emerald-500/[0.04]">
                                                    {row.heuresFerieTotal > 0 ? (
                                                        <span className="inline-flex items-center gap-1 font-bold text-emerald-700 dark:text-emerald-400">
                                                            <Award size={10} />
                                                            +{row.heuresFerieTotal}h
                                                        </span>
                                                    ) : (
                                                        <span className="text-[var(--color-fg-subtle)]">0h</span>
                                                    )}
                                                </Td>
                                                <Td className="py-2.5 px-2 text-center font-mono font-bold text-[var(--color-danger-500)]">
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

                {/* Modale DAF : Validation Finale de la Période */}
                <ConfirmDialog
                    isOpen={showValiderPeriodeModal}
                    onClose={() => setShowValiderPeriodeModal(false)}
                    onConfirm={() => handleUpdatePeriodeStatut('VALIDEE')}
                    title={`Valider définitivement la période ${consolidationStatus.currentPeriodeObj?.libelle || ''} ?`}
                    message="Cette action valide l'ensemble des heures consolidées, compensations et indemnités pour la DAF. La saisie quotidienne et les synthèses seront entièrement verrouillées."
                    confirmLabel="Confirmer la validation DAF"
                    cancelLabel="Annuler"
                    variant="primary"
                    loading={updatingPeriodeStatut}
                />

                {/* Modale DAF : Rejet / Renvoi pour correction avec Motif */}
                <Modal
                    isOpen={showRejetModal}
                    onClose={() => setShowRejetModal(false)}
                    title="Renvoyer la période à l'agent pour correction"
                    icon={<XCircle className="text-[var(--color-danger-500)]" size={18} />}
                    size="md"
                    footer={
                        <div className="flex items-center justify-end gap-2">
                            <Button
                                type="button"
                                variant="ghost"
                                size="sm"
                                onClick={() => setShowRejetModal(false)}
                                disabled={updatingPeriodeStatut}
                            >
                                Annuler
                            </Button>
                            <Button
                                type="button"
                                variant="danger"
                                size="sm"
                                loading={updatingPeriodeStatut}
                                disabled={updatingPeriodeStatut || !motifRejet.trim()}
                                onClick={() => handleUpdatePeriodeStatut('EN_COURS', motifRejet)}
                                className="font-bold"
                            >
                                Confirmer le renvoi
                            </Button>
                        </div>
                    }
                >
                    <div className="space-y-3 p-1">
                        <p className="text-xs text-[var(--color-fg-muted)]">
                            La période sera réouverte en statut <strong>EN COURS</strong>. Une notification prioritaire contenant vos instructions sera immédiatement transmise aux agents de pointage.
                        </p>
                        <div>
                            <label className="block text-[11px] font-bold text-[var(--color-fg)] mb-1">
                                Motif du renvoi / Instructions de correction <span className="text-[var(--color-danger-500)]">*</span>
                            </label>
                            <textarea
                                placeholder="Précisez obligatoirement les anomalies constatées ou les corrections attendues..."
                                value={motifRejet}
                                onChange={(e) => setMotifRejet(e.target.value)}
                                rows={3}
                                className="w-full p-2.5 text-xs bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] focus:border-brand-500 focus:ring-1 focus:ring-brand-500 outline-none rounded-none text-[var(--color-fg)] resize-none"
                            />
                        </div>
                    </div>
                </Modal>

                {/* Modale Agent : Transmission de la Période à la DAF */}
                <ConfirmDialog
                    isOpen={showTransmissionModal}
                    onClose={() => setShowTransmissionModal(false)}
                    onConfirm={handleTransmettreDaf}
                    title={`Transmettre la période ${consolidationStatus.currentPeriodeObj?.libelle || ''} à la DAF ?`}
                    message="Toutes les semaines du mois ont été validées et consolidées. Cette action transmet la période à la DAF (statut A_VALIDER) et verrouille la saisie quotidienne des agents jusqu'à arbitrage."
                    confirmLabel="Confirmer et Transmettre"
                    cancelLabel="Annuler"
                    variant="primary"
                    loading={transmittingDaf}
                />

                {/* Modale DAF : Clôture Définitive pour la Paie */}
                <ConfirmDialog
                    isOpen={showCloturerPeriodeModal}
                    onClose={() => setShowCloturerPeriodeModal(false)}
                    onConfirm={() => handleUpdatePeriodeStatut('CLOTUREE')}
                    title={`Clôturer la période pour génération de la Paie ?`}
                    message="Attention : La clôture est une action immuable qui archive définitivement la période. Elle atteste que les bulletins de salaire sont prêts à être générés."
                    confirmLabel="Clôturer définitivement"
                    cancelLabel="Annuler"
                    variant="danger"
                    loading={updatingPeriodeStatut}
                />
            </div>
        </>
    );
}
