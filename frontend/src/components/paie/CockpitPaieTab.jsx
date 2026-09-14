import { useState, useEffect, useMemo, useCallback } from 'react';
import { 
    Play, 
    CheckCheck, 
    Lock, 
    RotateCcw, 
    AlertTriangle, 
    CheckCircle2, 
    Search, 
    Eye, 
    ArrowUpRight, 
    ArrowDownRight,
    ShieldCheck, 
    Download,
    Plus,
    RefreshCw
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/api/paie/paieService';
import employeService from '@/api/employe/employeService';
import BulletinPaieDrawer from './BulletinPaieDrawer';

/**
 * Cockpit Principal de Paie DAF (GALAXAM LOGISTICS)
 * Conforme au Wireframe Écran 1 et Workflow du Rapport d'Audit.
 */
export default function CockpitPaieTab({ 
    idPeriodeActive, 
    setIdPeriodeActive, 
    periodes = [], 
    onReloadPeriodes 
}) {
    const [cockpit, setCockpit] = useState(null);
    const [loading, setLoading] = useState(false);
    const [actionLoading, setActionLoading] = useState(null); // 'simuler' | 'calculer' | 'valider' | 'cloturer' | 'rouvrir'
    const [searchQuery, setSearchQuery] = useState('');
    const [filterSite, setFilterSite] = useState('ALL');
    const [filterAlert, setFilterAlert] = useState('ALL'); // 'ALL' | 'ALERTS' | 'STC' | 'VALIDE' | 'CLOTURE'
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [feedback, setFeedback] = useState(null);

    // Modal Nouvelle Variable contextuelle
    const [isVariableModalOpen, setIsVariableModalOpen] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
    const [employesList, setEmployesList] = useState([]);
    const [submittingVariable, setSubmittingVariable] = useState(false);
    const [variableForm, setVariableForm] = useState({
        idEmploye: '',
        typeMouvement: 'PRIM_EXC',
        sens: 'GAIN',
        montant: '',
        motif: ''
    });

    // Modal de réouverture
    const [isReopenModalOpen, setIsReopenModalOpen] = useState(false);
    const [motifReouverture, setMotifReouverture] = useState('');

    const chargerCockpit = useCallback(async () => {
        setLoading(true);
        setFeedback(null);
        try {
            const data = await paieService.getCockpitPeriode(idPeriodeActive);
            setCockpit(data);
        } catch (err) {
            setFeedback({
                type: 'error',
                text: err.response?.data?.error || err.message || 'Erreur lors du chargement du cockpit de paie.'
            });
        } finally {
            setLoading(false);
        }
    }, [idPeriodeActive]);

    useEffect(() => {
        if (idPeriodeActive) {
            chargerCockpit();
        } else {
            setCockpit(null);
        }
    }, [idPeriodeActive, chargerCockpit]);

    const chargerReferentielsVariables = async () => {
        try {
            const [types, emps] = await Promise.all([
                paieService.listerTypesMouvements(),
                employeService.getEmployes({ size: 1000, actif: true })
            ]);
            setTypesMouvements(Array.isArray(types) ? types : (types?.content || types?.items || []));
            setEmployesList(Array.isArray(emps) ? emps : (emps?.content || emps?.items || []));
        } catch (err) {
            console.error('Erreur chargement listes variables :', err);
        }
    };

    const handleOpenVariableModal = () => {
        chargerReferentielsVariables();
        setIsVariableModalOpen(true);
    };

    const handleCreerVariable = async (e) => {
        e.preventDefault();
        if (!variableForm.idEmploye || !variableForm.montant) {
            alert('Veuillez renseigner un collaborateur et un montant.');
            return;
        }

        setSubmittingVariable(true);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(variableForm.idEmploye),
                idPeriodePaie: idPeriodeActive,
                typeMouvement: variableForm.typeMouvement,
                sens: variableForm.sens,
                montant: Number(variableForm.montant),
                motif: variableForm.motif || 'Variable Cockpit',
                statut: 'APPROUVE'
            });

            setIsVariableModalOpen(false);
            setVariableForm({
                idEmploye: '',
                typeMouvement: 'PRIM_EXC',
                sens: 'GAIN',
                montant: '',
                motif: ''
            });
            setFeedback({ type: 'success', text: 'Variable enregistrée avec succès. Recalculez la paie pour l\'appliquer.' });
            await chargerCockpit();
        } catch (err) {
            alert(err.response?.data?.error || 'Erreur lors de la création de la variable.');
        } finally {
            setSubmittingVariable(false);
        }
    };

    const handleCalculerPaie = async (simulation = false) => {
        if (!idPeriodeActive) return;
        setActionLoading(simulation ? 'simuler' : 'calculer');
        setFeedback(null);
        try {
            const res = await paieService.calculerPaiePeriode(idPeriodeActive, simulation);
            setFeedback({
                type: 'success',
                text: simulation
                    ? `Simulation terminée : ${res.nbBulletinsCalcules} bulletin(s) simulé(s). Données non persistées.`
                    : `Calcul réel exécuté : ${res.nbBulletinsCalcules} bulletin(s) calculé(s) et enregistrés en base.`
            });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            setFeedback({
                type: 'error',
                text: err.response?.data?.error || err.message || 'Erreur lors du calcul de la paie.'
            });
        } finally {
            setActionLoading(null);
        }
    };

    const handleValiderPaie = async () => {
        if (!idPeriodeActive) return;
        if (!window.confirm('Confirmer la validation de la paie pour cette période ?')) return;

        setActionLoading('valider');
        setFeedback(null);
        try {
            await paieService.validerPaiePeriode(idPeriodeActive);
            setFeedback({ type: 'success', text: 'Période de paie validée avec succès.' });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            setFeedback({
                type: 'error',
                text: err.response?.data?.error || err.message || 'Erreur lors de la validation.'
            });
        } finally {
            setActionLoading(null);
        }
    };

    const handleCloturerPaie = async () => {
        if (!idPeriodeActive) return;
        if (!window.confirm('ATTENTION : La clôture verrouille définitivement les bulletins (lecture seule). Confirmer ?')) return;

        setActionLoading('cloturer');
        setFeedback(null);
        try {
            await paieService.cloturerPaiePeriode(idPeriodeActive);
            setFeedback({ type: 'success', text: 'Période clôturée avec succès. Bulletins archivés immuables.' });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            setFeedback({
                type: 'error',
                text: err.response?.data?.error || err.message || 'Erreur lors de la clôture.'
            });
        } finally {
            setActionLoading(null);
        }
    };

    const handleRouvrirPaie = async (e) => {
        e.preventDefault();
        if (!motifReouverture || motifReouverture.trim().length < 5) {
            alert('Le motif de réouverture est obligatoire (min 5 caractères).');
            return;
        }

        setActionLoading('rouvrir');
        try {
            await paieService.rouvrirPaiePeriode(idPeriodeActive, motifReouverture.trim());
            setIsReopenModalOpen(false);
            setMotifReouverture('');
            setFeedback({ type: 'success', text: 'Période réouverte pour corrections.' });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            alert(err.response?.data?.error || 'Erreur lors de la réouverture.');
        } finally {
            setActionLoading(null);
        }
    };

    const formatAr = (val) => {
        if (val === null || val === undefined) return '0 Ar';
        return Number(val).toLocaleString('fr-MG', { minimumFractionDigits: 0, maximumFractionDigits: 0 }) + ' Ar';
    };

    const readiness = cockpit?.readiness || {};
    const kpis = cockpit?.kpis || {};
    const periode = cockpit?.periode || {};
    const bulletins = useMemo(() => Array.isArray(cockpit?.bulletins) ? cockpit.bulletins : [], [cockpit]);

    const isCloture = periode.statut === 'CLOTUREE';
    const isValide = periode.statut === 'VALIDEE';
    const pointageScelle = readiness.pointageScelle === true;

    // Liste unique des sites pour le filtre
    const sites = useMemo(() => {
        const set = new Set();
        bulletins.forEach(b => {
            if (b.nomSite) set.add(b.nomSite);
        });
        return Array.from(set);
    }, [bulletins]);

    // Filtrage employés
    const filteredBulletins = bulletins.filter(b => {
        const q = searchQuery.toLowerCase();
        const matchSearch = !q ||
            (b.nomPrenom && b.nomPrenom.toLowerCase().includes(q)) ||
            (b.matricule && b.matricule.toLowerCase().includes(q)) ||
            (b.intitulePoste && b.intitulePoste.toLowerCase().includes(q));

        const matchSite = filterSite === 'ALL' || b.nomSite === filterSite;

        let matchFilter = true;
        if (filterAlert === 'ALERTS') matchFilter = b.aAlerte === true;
        else if (filterAlert === 'STC') matchFilter = b.typeBulletin === 'STC';
        else if (filterAlert === 'VALIDE') matchFilter = b.statut === 'VALIDE';
        else if (filterAlert === 'CLOTURE') matchFilter = b.statut === 'CLOTURE';

        return matchSearch && matchSite && matchFilter;
    });

    const nbAlertes = bulletins.filter(b => b.aAlerte).length;

    // Export direct de l'état de contrôle CSV
    const handleExportEtatControleCsv = () => {
        if (!bulletins.length) return;
        const headers = ['Matricule', 'Salarie', 'Poste', 'Site', 'Salaire_Base', 'Brut', 'Cotis_Salariales', 'IRSA_Net', 'Net_A_Payer', 'Ecart_M1_Pct', 'Statut'];
        const rows = filteredBulletins.map(b => [
            b.matricule,
            `"${b.nomPrenom}"`,
            `"${b.intitulePoste || ''}"`,
            `"${b.nomSite || ''}"`,
            b.salaireBase,
            b.totalGainsBruts,
            b.totalCotisationsSalariales,
            b.irsaNet,
            b.netAPayer,
            b.ecartM1Pct ?? '',
            b.typeBulletin === 'STC' ? 'STC Inclus' : b.statut
        ]);

        const csvContent = [headers.join(';'), ...rows.map(r => r.join(';'))].join('\r\n');
        const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `etat_controle_paie_${periode.code || idPeriodeActive}.csv`;
        link.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="space-y-4">
            {/* Feedback message */}
            {feedback && (
                <div className={`p-3 text-xs flex items-center gap-2 border ${
                    feedback.type === 'success' 
                        ? 'bg-emerald-50 border-emerald-200 text-emerald-800' 
                        : 'bg-rose-50 border-rose-200 text-rose-800'
                }`}>
                    {feedback.type === 'success' ? <CheckCircle2 size={16} /> : <AlertTriangle size={16} />}
                    <span>{feedback.text}</span>
                </div>
            )}

            {/* En-tête Wireframe 1 : Exercice, Cycle d'activité & Statut */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4">
                <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-4 border-b border-[var(--color-border-subtle)] pb-3.5 mb-3.5">
                    <div>
                        <div className="flex items-center gap-2">
                            <h2 className="text-sm font-bold uppercase tracking-wide text-[var(--color-fg)]">
                                PAIE & RÉMUNÉRATION — GALAXAM LOGISTICS
                            </h2>
                            <span className="text-xs text-[var(--color-fg-muted)]">|</span>
                            <span className="text-xs font-semibold text-[var(--color-fg-muted)]">
                                Exercice :
                            </span>
                            <select
                                value={idPeriodeActive || ''}
                                onChange={(e) => setIdPeriodeActive(Number(e.target.value))}
                                className="text-xs font-bold font-mono px-2 py-0.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                            >
                                <option value="">Selectionnez une période</option>
                                {periodes.map(p => (
                                    <option key={p.idPeriodePaie || p.id_periode_paie} value={p.idPeriodePaie || p.id_periode_paie}>
                                        {p.code} ({p.codeStatut || p.statut || 'EN_COURS'})
                                    </option>
                                ))}
                            </select>
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)] mt-1 font-mono">
                            Cycle d'activité : <span className="font-semibold text-[var(--color-fg)]">{periode.dateDebut || '26/..'} → {periode.dateFin || '25/..'}</span> • Statut : <span className="font-bold text-[var(--color-brand-500)]">{periode.libelleStatut || periode.statut || 'EN_COURS'}</span>
                        </p>
                    </div>

                    {/* Toolbar Actions DAF (Wireframe 1) */}
                    <div className="flex flex-wrap items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={handleOpenVariableModal}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Plus size={13} className="text-[var(--color-brand-500)]" />
                            + Ajouter une Variable
                        </Button>

                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => handleCalculerPaie(true)}
                            disabled={actionLoading !== null}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Play size={13} className="text-blue-500" />
                            {actionLoading === 'simuler' ? 'Simulation...' : 'Simuler'}
                        </Button>

                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleCalculerPaie(false)}
                            disabled={actionLoading !== null || isCloture || !pointageScelle}
                            title={!pointageScelle ? 'Pointage non scellé : calcul réel bloqué' : 'Recalculer la paie de tous les collaborateurs'}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <RefreshCw size={13} className={actionLoading === 'calculer' ? 'animate-spin' : ''} />
                            {actionLoading === 'calculer' ? 'Calcul en cours...' : '⟳ Recalculer tout'}
                        </Button>

                        {!isCloture && (
                            <Button
                                variant="primary"
                                size="sm"
                                onClick={handleValiderPaie}
                                disabled={actionLoading !== null || isValide || bulletins.length === 0}
                                className="flex items-center gap-1.5 text-xs bg-emerald-700 hover:bg-emerald-800 border-emerald-800"
                            >
                                <CheckCheck size={14} />
                                {actionLoading === 'valider' ? 'Validation...' : 'VALIDER LA PAIE →'}
                            </Button>
                        )}

                        {!isCloture ? (
                            <Button
                                variant="danger"
                                size="sm"
                                onClick={handleCloturerPaie}
                                disabled={actionLoading !== null || bulletins.length === 0}
                                className="flex items-center gap-1.5 text-xs"
                            >
                                <Lock size={13} />
                                Clôturer
                            </Button>
                        ) : (
                            <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setIsReopenModalOpen(true)}
                                disabled={actionLoading !== null}
                                className="flex items-center gap-1.5 text-xs text-amber-600 border-amber-300"
                            >
                                <RotateCcw size={13} />
                                Réouvrir (Audit)
                            </Button>
                        )}
                    </div>
                </div>

                {/* Bandeau Dépendances & Contrôles Amont (Wireframe 1) */}
                <div className="flex flex-wrap items-center gap-3 text-xs">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 border font-medium ${
                        pointageScelle 
                            ? 'bg-emerald-50 text-emerald-800 border-emerald-200' 
                            : 'bg-rose-50 text-rose-800 border-rose-200'
                    }`}>
                        <CheckCircle2 size={13} className={pointageScelle ? 'text-emerald-600' : 'text-rose-500'} />
                        Pointage validé ({readiness.nbEmployesPointes ?? 0}/{readiness.effectifAttendu ?? 0})
                    </span>

                    <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-emerald-50 text-emerald-800 border border-emerald-200 font-medium">
                        <CheckCircle2 size={13} className="text-emerald-600" />
                        Congés synchronisés
                    </span>

                    {nbAlertes > 0 ? (
                        <button
                            onClick={() => setFilterAlert(filterAlert === 'ALERTS' ? 'ALL' : 'ALERTS')}
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 border font-medium cursor-pointer transition-colors ${
                                filterAlert === 'ALERTS'
                                    ? 'bg-amber-100 text-amber-900 border-amber-400 font-bold'
                                    : 'bg-amber-50 text-amber-800 border-amber-200 hover:bg-amber-100'
                            }`}
                        >
                            <AlertTriangle size={13} className="text-amber-600" />
                            ⚠ {nbAlertes} variation(s) atypique(s) détectée(s) &gt; 20%
                        </button>
                    ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-slate-50 text-slate-700 border border-slate-200 font-medium">
                            <ShieldCheck size={13} className="text-emerald-600" />
                            Aucune variation atypique
                        </span>
                    )}
                </div>
            </div>

            {/* KPIs DE LA PÉRIODE (Wireframe 1) */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2.5">
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                        Masse Brute
                    </span>
                    <span className="text-sm font-bold font-mono text-[var(--color-fg)] block mt-1">
                        {formatAr(kpis.totalBrut)}
                    </span>
                    <div className="flex items-center gap-1 text-[10px] mt-1 font-mono text-[var(--color-fg-muted)]">
                        {kpis.ecartBrutM1Pct !== undefined && kpis.ecartBrutM1Pct !== null && (
                            <span className={`flex items-center ${kpis.ecartBrutM1Pct >= 0 ? 'text-emerald-600' : 'text-rose-600'}`}>
                                {kpis.ecartBrutM1Pct >= 0 ? <ArrowUpRight size={12} /> : <ArrowDownRight size={12} />}
                                Écart M-1 : {kpis.ecartBrutM1Pct >= 0 ? '+' : ''}{Math.abs(kpis.ecartBrutM1Pct).toFixed(1)}%
                            </span>
                        )}
                    </div>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                        Charges Patronales
                    </span>
                    <span className="text-sm font-bold font-mono text-slate-700 block mt-1">
                        {formatAr(kpis.totalCotisationsPatronales)}
                    </span>
                    <span className="text-[10px] text-[var(--color-fg-muted)] block mt-1">
                        CNaPS (13%) + Santé + FMFP
                    </span>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                        Cotisations Salariales
                    </span>
                    <span className="text-sm font-bold font-mono text-rose-600 block mt-1">
                        -{formatAr(kpis.totalCotisationsSalariales)}
                    </span>
                    <span className="text-[10px] text-[var(--color-fg-muted)] block mt-1">
                        CNaPS (1%) + Santé (1%)
                    </span>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                        Retenue IRSA DGI
                    </span>
                    <span className="text-sm font-bold font-mono text-rose-600 block mt-1">
                        -{formatAr(kpis.totalIrsa)}
                    </span>
                    <span className="text-[10px] text-[var(--color-fg-muted)] block mt-1">
                        Barème progressif 6 tranches
                    </span>
                </div>

                <div className="bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/30 p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-brand-500)] uppercase tracking-wider block">
                        Net à Virer
                    </span>
                    <span className="text-base font-black font-mono text-[var(--color-brand-500)] block mt-1">
                        {formatAr(kpis.totalNetAPayer)}
                    </span>
                    <span className="text-[10px] text-[var(--color-brand-500)]/80 block mt-1">
                        Effectif rémunéré : {kpis.effectifCalcule ?? 0}
                    </span>
                </div>

                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                    <span className="text-[10px] font-semibold text-[var(--color-fg-muted)] uppercase tracking-wider block">
                        Coût Global Entreprise
                    </span>
                    <span className="text-sm font-bold font-mono text-[var(--color-fg)] block mt-1">
                        {formatAr((Number(kpis.totalBrut) || 0) + (Number(kpis.totalCotisationsPatronales) || 0))}
                    </span>
                    <span className="text-[10px] text-[var(--color-fg-muted)] block mt-1">
                        Brut + Charges patronales
                    </span>
                </div>
            </div>

            {/* Barre de Recherche & Filtres (Wireframe 1) */}
            <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
                    <div className="relative flex-1 sm:w-64">
                        <Search size={14} className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                        <input
                            type="text"
                            placeholder="Recherche : Matricule, nom, poste..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full text-xs pl-8 pr-3 py-1.5 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                        />
                    </div>

                    <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-[var(--color-fg-muted)]">Filtre Site :</span>
                        <select
                            value={filterSite}
                            onChange={(e) => setFilterSite(e.target.value)}
                            className="text-xs py-1.5 px-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                        >
                            <option value="ALL">Tous les sites</option>
                            {sites.map((s, idx) => (
                                <option key={idx} value={s}>{s}</option>
                            ))}
                        </select>
                    </div>

                    <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-[var(--color-fg-muted)]">Filtre Statut :</span>
                        <select
                            value={filterAlert}
                            onChange={(e) => setFilterAlert(e.target.value)}
                            className="text-xs py-1.5 px-2 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none focus:outline-none"
                        >
                            <option value="ALL">Tous les statuts</option>
                            <option value="ALERTS">⚠ Variations &gt; 20%</option>
                            <option value="STC">★ Sortants STC Inclus</option>
                            <option value="VALIDE">✓ Validés</option>
                            <option value="CLOTURE">✓ Clôturés</option>
                        </select>
                    </div>
                </div>

                <div className="text-xs text-[var(--color-fg-muted)] font-mono">
                    Affichage : <span className="font-bold text-[var(--color-fg)]">{filteredBulletins.length}</span> sur {bulletins.length} salariés
                </div>
            </div>

            {/* Table des Salariés (Wireframe 1) */}
            {loading ? (
                <SkeletonTable rows={8} cols={7} />
            ) : (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    <table className="w-full text-left text-xs border-collapse">
                        <thead>
                            <tr className="bg-[var(--color-bg-subtle)] border-b border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)] uppercase tracking-wider">
                                <th className="p-3 font-semibold">MATR.</th>
                                <th className="p-3 font-semibold">SALARIÉ</th>
                                <th className="p-3 font-semibold">POSTE & SITE</th>
                                <th className="p-3 font-semibold text-right">BRUT (Ar)</th>
                                <th className="p-3 font-semibold text-right bg-[var(--color-brand-500)]/5">NET (Ar)</th>
                                <th className="p-3 font-semibold text-center">ÉCART M-1</th>
                                <th className="p-3 font-semibold text-center">STATUT</th>
                                <th className="p-3 font-semibold text-right">ACTIONS</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--color-border-subtle)]">
                            {filteredBulletins.length === 0 ? (
                                <tr>
                                    <td colSpan={8} className="p-8 text-center text-xs text-[var(--color-fg-muted)] italic">
                                        Aucun salarié calculé pour cette période. Cliquez sur « ⟳ Recalculer tout » ou « Simuler ».
                                    </td>
                                </tr>
                            ) : (
                                filteredBulletins.map((b) => (
                                    <tr 
                                        key={b.idBulletin || b.idEmploye} 
                                        className="hover:bg-[var(--color-bg-subtle)]/50 transition-colors"
                                    >
                                        <td className="p-3 font-mono font-bold text-[var(--color-fg)]">
                                            {b.matricule}
                                        </td>
                                        <td className="p-3 font-semibold text-[var(--color-fg)]">
                                            {b.nomPrenom}
                                        </td>
                                        <td className="p-3 text-[var(--color-fg-muted)]">
                                            <span className="font-medium text-[var(--color-fg)] block">{b.intitulePoste || '-'}</span>
                                            <span className="text-[10px] text-[var(--color-fg-subtle)]">{b.nomSite || 'Siège'}</span>
                                        </td>
                                        <td className="p-3 text-right font-mono font-bold text-slate-800">
                                            {formatAr(b.totalGainsBruts)}
                                        </td>
                                        <td className="p-3 text-right font-mono font-black text-[var(--color-brand-500)] bg-[var(--color-brand-500)]/5 text-[13px]">
                                            {formatAr(b.netAPayer)}
                                        </td>
                                        <td className="p-3 text-center font-mono">
                                            {b.typeBulletin === 'STC' ? (
                                                <span className="text-[10px] font-bold text-blue-600">N/A</span>
                                            ) : b.ecartM1Pct !== null && b.ecartM1Pct !== undefined ? (
                                                <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 text-[10px] font-semibold border ${
                                                    b.aAlerte 
                                                        ? 'bg-amber-100 text-amber-900 border-amber-300 font-bold'
                                                        : b.ecartM1Pct >= 0 
                                                            ? 'bg-emerald-50 text-emerald-700 border-emerald-200' 
                                                            : 'bg-rose-50 text-rose-700 border-rose-200'
                                                }`}>
                                                    {b.ecartM1Pct >= 0 ? '+' : ''}{Number(b.ecartM1Pct).toFixed(1)}%
                                                    {b.aAlerte && ' ⚠️'}
                                                </span>
                                            ) : (
                                                <span className="text-[10px] text-[var(--color-fg-subtle)]">0.0%</span>
                                            )}
                                        </td>
                                        <td className="p-3 text-center">
                                            {b.typeBulletin === 'STC' ? (
                                                <span className="px-2 py-0.5 text-[10px] font-bold bg-blue-50 text-blue-800 border border-blue-200">
                                                    ★ STC Inclus
                                                </span>
                                            ) : b.aAlerte ? (
                                                <span className="px-2 py-0.5 text-[10px] font-bold bg-amber-50 text-amber-800 border border-amber-300">
                                                    ⚠ Contrôle
                                                </span>
                                            ) : b.statut === 'CLOTURE' ? (
                                                <span className="px-2 py-0.5 text-[10px] font-semibold bg-slate-100 text-slate-700 border border-slate-300">
                                                    ✓ Clôturé
                                                </span>
                                            ) : b.statut === 'VALIDE' ? (
                                                <span className="px-2 py-0.5 text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                                    ✓ Validé
                                                </span>
                                            ) : (
                                                <span className="px-2 py-0.5 text-[10px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
                                                    ✓ Prêt
                                                </span>
                                            )}
                                        </td>
                                        <td className="p-3 text-right">
                                            <button
                                                onClick={() => setSelectedBulletinId(b.idBulletin)}
                                                className="px-2.5 py-1 text-xs font-semibold bg-[var(--color-bg-subtle)] hover:bg-[var(--color-border)] text-[var(--color-fg)] border border-[var(--color-border)] cursor-pointer inline-flex items-center gap-1.5"
                                                title="Consulter le bulletin détaillé"
                                            >
                                                <Eye size={12} />
                                                Détail
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>

                    {/* Pied de tableau (Wireframe 1) */}
                    <div className="px-4 py-3 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex items-center justify-between">
                        <span className="text-xs text-[var(--color-fg-muted)] font-mono">
                            Page 1 / 1 ({filteredBulletins.length} salariés)
                        </span>

                        <Button
                            variant="outline"
                            size="sm"
                            onClick={handleExportEtatControleCsv}
                            disabled={!filteredBulletins.length}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Download size={13} />
                            Télécharger l'état de contrôle CSV
                        </Button>
                    </div>
                </div>
            )}

            {/* SlideOver Détail Bulletin & Formulaire d'ajustements contextuels (Wireframe 2) */}
            <BulletinPaieDrawer
                isOpen={!!selectedBulletinId}
                onClose={() => setSelectedBulletinId(null)}
                idBulletin={selectedBulletinId}
                onRecalculateSuccess={chargerCockpit}
            />

            {/* Modale d'ajout rapide de variable depuis le Cockpit */}
            <Modal
                isOpen={isVariableModalOpen}
                onClose={() => setIsVariableModalOpen(false)}
                title="Ajouter une Variable de Paie"
                size="md"
                icon={<Plus size={18} className="text-[var(--color-brand-500)]" />}
                footer={
                    <div className="flex items-center justify-end gap-2 w-full">
                        <Button variant="secondary" size="sm" onClick={() => setIsVariableModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button 
                            variant="primary" 
                            size="sm" 
                            onClick={handleCreerVariable}
                            disabled={submittingVariable}
                        >
                            {submittingVariable ? 'Enregistrement...' : 'Valider la variable'}
                        </Button>
                    </div>
                }
            >
                <form onSubmit={handleCreerVariable} className="space-y-3.5 text-xs">
                    <div>
                        <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                            Collaborateur *
                        </label>
                        <select
                            required
                            className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                            value={variableForm.idEmploye}
                            onChange={(e) => setVariableForm({ ...variableForm, idEmploye: e.target.value })}
                        >
                            <option value="">-- Choisir un collaborateur --</option>
                            {(Array.isArray(employesList) ? employesList : []).map((emp) => (
                                <option key={emp.idEmploye || emp.id_employe} value={emp.idEmploye || emp.id_employe}>
                                    [{emp.matricule}] {emp.nom} {emp.prenom}
                                </option>
                            ))}
                        </select>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Type de Variable *
                            </label>
                            <select
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={variableForm.typeMouvement}
                                onChange={(e) => setVariableForm({ ...variableForm, typeMouvement: e.target.value })}
                            >
                                <option value="PRIM_EXC">Prime exceptionnelle (Gain)</option>
                                <option value="ACOMPTE_15">Acompte quinzaine (Retenue)</option>
                                <option value="AVANCE_SPEC">Avance spéciale (Retenue)</option>
                                <option value="DED_DIV">Déduction diverse (Retenue)</option>
                                {(Array.isArray(typesMouvements) ? typesMouvements : []).map((t) => (
                                    <option key={t.idTypeMouvement || t.code} value={t.code}>
                                        {t.libelle} ({t.sens})
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Sens *
                            </label>
                            <select
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={variableForm.sens}
                                onChange={(e) => setVariableForm({ ...variableForm, sens: e.target.value })}
                            >
                                <option value="GAIN">+ Gain (Ajout au brut)</option>
                                <option value="RETENUE">- Retenue (Déduction du net)</option>
                            </select>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Montant en Ariary *
                            </label>
                            <input
                                type="number"
                                step="1000"
                                min="1000"
                                required
                                placeholder="Ex: 50000"
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none font-mono"
                                value={variableForm.montant}
                                onChange={(e) => setVariableForm({ ...variableForm, montant: e.target.value })}
                            />
                        </div>

                        <div>
                            <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                                Motif / Référence
                            </label>
                            <input
                                type="text"
                                placeholder="Ex: Prime transport projet"
                                className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                                value={variableForm.motif}
                                onChange={(e) => setVariableForm({ ...variableForm, motif: e.target.value })}
                            />
                        </div>
                    </div>
                </form>
            </Modal>

            {/* Modale Réouverture de Période (Audit d'urgence avec motif DGI) */}
            <Modal
                isOpen={isReopenModalOpen}
                onClose={() => setIsReopenModalOpen(false)}
                title="Réouverture Exceptionnelle de la Période"
                icon={<RotateCcw size={18} className="text-amber-600" />}
                size="md"
                footer={
                    <div className="flex items-center justify-end gap-2 w-full">
                        <Button variant="secondary" size="sm" onClick={() => setIsReopenModalOpen(false)}>
                            Annuler
                        </Button>
                        <Button 
                            variant="danger" 
                            size="sm" 
                            onClick={handleRouvrirPaie}
                            disabled={actionLoading === 'rouvrir'}
                        >
                            {actionLoading === 'rouvrir' ? 'Réouverture...' : 'Confirmer la réouverture'}
                        </Button>
                    </div>
                }
            >
                <form onSubmit={handleRouvrirPaie} className="space-y-3 text-xs">
                    <p className="text-slate-600 leading-relaxed">
                        La réouverture d'une période clôturée permet d'effectuer des corrections sur les variables ou les pointages. 
                        Cette action est strictement auditée et requiert la saisie d'un motif obligatoire.
                    </p>

                    <div>
                        <label className="text-[11px] font-semibold text-[var(--color-fg-muted)] block mb-1">
                            Motif réglementaire de réouverture * (min 5 caractères)
                        </label>
                        <textarea
                            required
                            rows={3}
                            placeholder="Ex: Erreur de saisie sur avance quinzaine nécessitant régularisation..."
                            className="w-full text-xs p-2 bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none focus:outline-none"
                            value={motifReouverture}
                            onChange={(e) => setMotifReouverture(e.target.value)}
                        />
                    </div>
                </form>
            </Modal>
        </div>
    );
}
