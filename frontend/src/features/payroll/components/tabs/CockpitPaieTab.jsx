import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { 
    Play, 
    CheckCheck, 
    Lock, 
    RotateCcw, 
    AlertTriangle, 
    CheckCircle2, 
    Search, 
    Eye, 
    ShieldCheck, 
    Download,
    Plus,
    RefreshCw
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Modal from '@/components/ui/overlays/Modal';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import paieService from '@/features/payroll/api/paieService';
import periodePaieService from '@/features/attendance/api/periodePaieService';
import BulletinPaieDrawer from './BulletinPaieDrawer';
import { getErrorMessage } from '@/api/apiClient';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Pagination from '@/components/ui/navigation/Pagination';
import useDebounce from '@/hooks/useDebounce';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import StatCard from '@/components/ui/data-display/StatCard';
import { Table, Thead, Th, Tbody, Tr, Td, TableEmpty } from '@/components/ui/data-display/Table';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { formatMoneyAr } from '@/utils/moneyUtils';

/**
 * Cockpit Principal de Paie DAF (GALAXAM LOGISTICS)
 * Conforme au Wireframe Écran 1 et Workflow du Rapport d'Audit.
 */
export default function CockpitPaieTab({ 
    idPeriodeActive, 
    setIdPeriodeActive, 
    periodes = [], 
    onReloadPeriodes,
    periodContext,
    view = 'overview',
    requestedEmployeeId = null
}) {
    const navigate = useNavigate();
    const [urlParams, setUrlParams] = useSearchParams();
    const [cockpit, setCockpit] = useState(null);
    const [loading, setLoading] = useState(false);
    const [actionLoading, setActionLoading] = useState(null); // 'simuler' | 'calculer' | 'valider' | 'cloturer' | 'rouvrir'
    const [searchQuery, setSearchQuery] = useState(urlParams.get('q') || '');
    const [filterSite, setFilterSite] = useState(urlParams.get('site') || 'ALL');
    const [filterAlert, setFilterAlert] = useState(urlParams.get('filter') || 'ALL'); // 'ALL' | 'ALERTS' | 'STC' | 'VALIDE' | 'CLOTURE'
    const [currentPage, setCurrentPage] = useState(Number(urlParams.get('page')) || 1);
    const [pageSize, setPageSize] = useState(Number(urlParams.get('size')) || 25);
    const debouncedSearch = useDebounce(searchQuery, 300);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [feedback, setFeedback] = useState(null);

    // Modal Nouvelle Variable contextuelle
    const [isVariableModalOpen, setIsVariableModalOpen] = useState(false);
    const [typesMouvements, setTypesMouvements] = useState([]);
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
            const data = await paieService.getCockpitPeriode(idPeriodeActive, {
                page: currentPage,
                size: pageSize,
                q: debouncedSearch || undefined,
                site: filterSite === 'ALL' ? undefined : filterSite,
                filter: filterAlert === 'ALL' ? undefined : filterAlert
            });
            setCockpit(data);
        } catch (err) {
            setFeedback({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors du chargement du cockpit de paie.')
            });
        } finally {
            setLoading(false);
        }
    }, [idPeriodeActive, currentPage, pageSize, debouncedSearch, filterSite, filterAlert]);

    useEffect(() => {
        setUrlParams(previous => {
            const next = new URLSearchParams(previous);
            const values = { q: debouncedSearch, site: filterSite, filter: filterAlert };
            Object.entries(values).forEach(([key, value]) => {
                if (value && value !== 'ALL') next.set(key, value); else next.delete(key);
            });
            if (currentPage > 1) next.set('page', String(currentPage)); else next.delete('page');
            if (pageSize !== 25) next.set('size', String(pageSize)); else next.delete('size');
            return next;
        }, { replace: true });
    }, [debouncedSearch, filterSite, filterAlert, currentPage, pageSize, setUrlParams]);

    useEffect(() => {
        setCurrentPage(1);
    }, [debouncedSearch, filterSite, filterAlert]);

    useEffect(() => {
        if (idPeriodeActive) {
            chargerCockpit();
        } else {
            setCockpit(null);
        }
    }, [idPeriodeActive, chargerCockpit]);

    const chargerReferentielsVariables = async () => {
        try {
            const types = await paieService.listerTypesMouvements(idPeriodeActive);
            setTypesMouvements(Array.isArray(types) ? types : (types?.content || types?.items || []));
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
            setFeedback({ type: 'error', text: 'Veuillez renseigner un collaborateur et un montant.' });
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
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la création de la variable.') });
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
                text: getErrorMessage(err, 'Erreur lors du calcul de la paie.')
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
                text: getErrorMessage(err, 'Erreur lors de la validation.')
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
            await periodePaieService.executeCommand(idPeriodeActive, 'CLOSE', {
                version: cockpit?.periode?.version
            });
            setFeedback({ type: 'success', text: 'Période clôturée avec succès. Bulletins archivés immuables.' });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            setFeedback({
                type: 'error',
                text: getErrorMessage(err, 'Erreur lors de la clôture.')
            });
        } finally {
            setActionLoading(null);
        }
    };

    const handleRouvrirPaie = async (e) => {
        e.preventDefault();
        if (!motifReouverture || motifReouverture.trim().length < 5) {
            setFeedback({ type: 'error', text: 'Le motif de réouverture est obligatoire (min 5 caractères).' });
            return;
        }

        setActionLoading('rouvrir');
        try {
            await periodePaieService.executeCommand(idPeriodeActive, 'REOPEN', {
                version: cockpit?.periode?.version,
                motif: motifReouverture.trim()
            });
            setIsReopenModalOpen(false);
            setMotifReouverture('');
            setFeedback({ type: 'success', text: 'Période réouverte pour corrections.' });
            await chargerCockpit();
            if (onReloadPeriodes) onReloadPeriodes();
        } catch (err) {
            setFeedback({ type: 'error', text: getErrorMessage(err, 'Erreur lors de la réouverture.') });
        } finally {
            setActionLoading(null);
        }
    };

    const formatAr = (val) => formatMoneyAr(val);

    const readiness = cockpit?.readiness || {};
    const kpis = cockpit?.kpis || {};
    const periode = cockpit?.periode || {};
    const bulletins = useMemo(() => Array.isArray(cockpit?.bulletins) ? cockpit.bulletins : [], [cockpit]);
    const population = Array.isArray(cockpit?.population) ? cockpit.population : [];
    const bulletinsPage = cockpit?.bulletinsPage || {};
    const validationSummary = cockpit?.validationSummary || {};

    const pointageScelle = readiness.pointageScelle === true;
    const decisionContext = cockpit?.context || periodContext;
    const availableActions = decisionContext?.availableActions || {};
    const actionAllowed = (action) => availableActions[action]?.allowed === true;
    const actionReason = (action) => (availableActions[action]?.reasonCodes || [])
        .map(code => decisionContext?.issues?.find(issue => issue.code === code)?.message || code)
        .join(' · ');

    useEffect(() => {
        if (!requestedEmployeeId || bulletins.length === 0) return;
        const requested = bulletins.find(item => item.idEmploye === requestedEmployeeId);
        if (requested?.idBulletin) setSelectedBulletinId(requested.idBulletin);
    }, [requestedEmployeeId, bulletins]);

    // Liste unique des sites pour le filtre
    const sites = Array.isArray(cockpit?.sites) ? cockpit.sites : [];
    const filteredBulletins = bulletins;

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
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <p className="text-[10px] uppercase tracking-wider text-[var(--color-fg-muted)]">Étape courante</p>
                <h2 className="text-sm font-bold text-[var(--color-fg)]">
                    {{
                        overview: 'Vue d’ensemble de la période',
                        readiness: 'Prérequis de calcul',
                        population: 'Population de paie',
                        results: 'Résultats et contrôles',
                        validation: 'Validation de la campagne'
                    }[view] || 'Vue d’ensemble de la période'}
                </h2>
                {view === 'readiness' && (
                    <div className="grid gap-2 mt-3" aria-live="polite">
                        {(readiness.checks || []).map(check => (
                            <div key={check.code} className="flex items-start justify-between gap-3 border border-[var(--color-border-subtle)] p-3">
                                <div>
                                    <p className="text-xs font-bold text-[var(--color-fg)]">{check.code}</p>
                                    <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">{check.message}</p>
                                </div>
                                <Badge variant={check.status === 'PASS' ? 'success' : check.status === 'BLOCK' ? 'danger' : 'warning'}>
                                    {check.status}
                                </Badge>
                            </div>
                        ))}
                    </div>
                )}
            </div>
            {/* Feedback message */}
            {feedback && <Alert type={feedback.type === 'success' ? 'success' : 'error'} message={feedback.text} />}

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
                            <SearchSelect
                                value={String(idPeriodeActive || '')}
                                onChange={(val) => setIdPeriodeActive(val ? Number(val) : null)}
                                options={[
                                    { value: '', label: 'Sélectionnez une période' },
                                    ...periodes.map(p => ({
                                        value: String(p.idPeriodePaie || p.id_periode_paie),
                                        label: [p.code, p.libelle].filter(Boolean).join(' — ') || String(p.idPeriodePaie || p.id_periode_paie),
                                        sublabel: p.codeStatut || p.statutCode || p.statut || 'EN_COURS'
                                    }))
                                ]}
                                className="w-64"
                            />
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
                            disabled={actionLoading !== null || !actionAllowed('ADD_VARIABLE')}
                            title={actionReason('ADD_VARIABLE')}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Plus size={13} className="text-[var(--color-brand-500)]" />
                            + Ajouter une Variable
                        </Button>

                        <Button
                            variant="secondary"
                            size="sm"
                            onClick={() => handleCalculerPaie(true)}
                            disabled={actionLoading !== null || !actionAllowed('SIMULATE')}
                            title={actionReason('SIMULATE')}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <Play size={13} className="text-[var(--color-info-500)]" />
                            {actionLoading === 'simuler' ? 'Simulation...' : 'Simuler'}
                        </Button>

                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => handleCalculerPaie(false)}
                            disabled={actionLoading !== null || !actionAllowed('CALCULATE')}
                            title={actionReason('CALCULATE') || 'Recalculer la paie de tous les collaborateurs'}
                            className="flex items-center gap-1.5 text-xs"
                        >
                            <RefreshCw size={13} className={actionLoading === 'calculer' ? 'animate-spin' : ''} />
                            {actionLoading === 'calculer' ? 'Calcul en cours...' : '⟳ Recalculer tout'}
                        </Button>

                        <Button
                                variant="primary"
                                size="sm"
                                onClick={handleValiderPaie}
                                disabled={actionLoading !== null || !actionAllowed('VALIDATE')}
                                title={actionReason('VALIDATE')}
                                className="flex items-center gap-1.5 text-xs"
                            >
                                <CheckCheck size={14} />
                                {actionLoading === 'valider' ? 'Validation...' : 'VALIDER LA PAIE →'}
                        </Button>

                        <Button
                                variant="danger"
                                size="sm"
                                onClick={handleCloturerPaie}
                                disabled={actionLoading !== null || !actionAllowed('CLOSE')}
                                title={actionReason('CLOSE')}
                                className="flex items-center gap-1.5 text-xs"
                            >
                                <Lock size={13} />
                                Clôturer
                        </Button>
                        <Button
                                variant="outline"
                                size="sm"
                                onClick={() => setIsReopenModalOpen(true)}
                                disabled={actionLoading !== null || !actionAllowed('REOPEN')}
                                title={actionReason('REOPEN')}
                                className="flex items-center gap-1.5 text-xs text-[var(--color-warning-600)] border-[var(--color-warning-500)]"
                            >
                                <RotateCcw size={13} />
                                Réouvrir (Audit)
                        </Button>
                    </div>
                </div>

                {/* Bandeau Dépendances & Contrôles Amont (Wireframe 1) */}
                {(view === 'overview' || view === 'readiness') && <div className="flex flex-wrap items-center gap-3 text-xs">
                    <span className={`inline-flex items-center gap-1.5 px-2.5 py-1 border font-medium ${
                        pointageScelle
                            ? 'bg-[var(--color-success-500)]/10 text-[var(--color-success-600)] border-[var(--color-success-500)]/30'
                            : 'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)] border-[var(--color-danger-500)]/30'
                    }`}>
                        <CheckCircle2 size={13} />
                        Pointage validé ({readiness.nbEmployesPointes ?? 0}/{readiness.effectifAttendu ?? 0})
                    </span>

                    {nbAlertes > 0 ? (
                        <button
                            onClick={() => setFilterAlert(filterAlert === 'ALERTS' ? 'ALL' : 'ALERTS')}
                            className={`inline-flex items-center gap-1.5 px-2.5 py-1 border font-medium cursor-pointer transition-colors ${
                                filterAlert === 'ALERTS'
                                    ? 'bg-[var(--color-warning-500)]/20 text-[var(--color-warning-700)] border-[var(--color-warning-500)] font-bold'
                                    : 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-700)] border-[var(--color-warning-500)]/30'
                            }`}
                        >
                            <AlertTriangle size={13} />
                            ⚠ {nbAlertes} variation(s) atypique(s) détectée(s) &gt; 20%
                        </button>
                    ) : (
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] border border-[var(--color-border-subtle)] font-medium">
                            <ShieldCheck size={13} className="text-[var(--color-success-600)]" />
                            Aucune variation atypique
                        </span>
                    )}
                </div>}
            </div>

            {/* KPIs DE LA PÉRIODE (Wireframe 1) */}
            {(view === 'overview' || view === 'results' || view === 'validation') && (
                <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2.5">
                    <StatCard
                        title="Masse Brute"
                        value={formatAr(kpis.totalBrut)}
                        color="neutral"
                        change={kpis.ecartBrutM1Pct}
                        changeLabel="Écart M-1"
                    />
                    <StatCard
                        title="Charges Patronales"
                        value={formatAr(kpis.totalCotisationsPatronales)}
                        subtext="CNaPS (13%) + Santé + FMFP"
                        color="warning"
                    />
                    <StatCard
                        title="Cotisations Salariales"
                        value={`-${formatAr(kpis.totalCotisationsSalariales)}`}
                        subtext="CNaPS (1%) + Santé (1%)"
                        color="danger"
                    />
                    <StatCard
                        title="Retenue IRSA DGI"
                        value={`-${formatAr(kpis.totalIrsa)}`}
                        subtext="Barème progressif 6 tranches"
                        color="danger"
                    />
                    <StatCard
                        title="Net à Virer"
                        value={formatAr(kpis.totalNetAPayer)}
                        subtext={`Effectif : ${kpis.effectifCalcule ?? 0} salariés`}
                        color="brand"
                    />
                    <StatCard
                        title="Coût Global Entreprise"
                        value={formatAr((Number(kpis.totalBrut) || 0) + (Number(kpis.totalCotisationsPatronales) || 0))}
                        subtext="Brut + Charges patronales"
                        color="neutral"
                    />
                </div>
            )}

            {view === 'validation' && (
                <section aria-label="Résumé avant validation" className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4">
                    <h3 className="text-xs font-bold uppercase text-[var(--color-fg)] mb-3">Résumé avant validation</h3>
                    <dl className="grid grid-cols-2 md:grid-cols-4 gap-3 text-xs">
                        <div><dt className="text-[var(--color-fg-muted)]">Population</dt><dd className="font-bold">{validationSummary.population ?? 0}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Net total</dt><dd className="font-bold font-mono">{formatAr(validationSummary.netTotal)}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Charges employeur</dt><dd className="font-bold font-mono">{formatAr(validationSummary.employerContributions)}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Impôts</dt><dd className="font-bold font-mono">{formatAr(validationSummary.taxes)}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Warnings</dt><dd className="font-bold">{validationSummary.warnings ?? 0}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Version campagne</dt><dd className="font-bold font-mono">v{validationSummary.version ?? 0}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Calculé par</dt><dd className="font-bold">{validationSummary.calculatedBy ?? '—'}</dd></div>
                        <div><dt className="text-[var(--color-fg-muted)]">Date du calcul</dt><dd className="font-bold font-mono">{validationSummary.calculatedAt || '—'}</dd></div>
                    </dl>
                </section>
            )}

            {view === 'population' && (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] overflow-x-auto">
                    <table className="w-full text-left text-xs">
                        <thead className="bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] uppercase">
                            <tr>
                                <th className="p-3">Matricule</th>
                                <th className="p-3">Employé</th>
                                <th className="p-3">Contrat applicable</th>
                                <th className="p-3">Régime</th>
                                <th className="p-3">Décision backend</th>
                                <th className="p-3">Raison</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-[var(--color-border-subtle)]">
                            {population.map(member => (
                                <tr key={member.employeeId}>
                                    <td className="p-3 font-mono font-bold">{member.matricule}</td>
                                    <td className="p-3">{member.employeeName}</td>
                                    <td className="p-3 font-mono">{member.contractNumber || '—'}</td>
                                    <td className="p-3">{member.regime || '—'}</td>
                                    <td className="p-3 font-bold">{member.decision}</td>
                                    <td className="p-3 text-[var(--color-fg-muted)]">{member.reasonCode || '—'}</td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Barre de Recherche & Filtres (Wireframe 1) */}
            {(view === 'overview' || view === 'results' || view === 'validation') && <div className="flex flex-col sm:flex-row items-center justify-between gap-3 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3">
                <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto">
                    <div className="w-full sm:w-64">
                        <Input
                            type="text"
                            placeholder="Recherche : Matricule, nom, poste..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            iconLeft={<Search size={14} />}
                        />
                    </div>

                    <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Site :</span>
                        <Select
                            value={filterSite}
                            onChange={(val) => setFilterSite(val)}
                            options={[
                                { value: 'ALL', label: 'Tous les sites' },
                                ...sites.map(s => ({ value: s, label: s }))
                            ]}
                            className="text-xs h-[30px] min-w-[130px]"
                        />
                    </div>

                    <div className="flex items-center gap-1.5">
                        <span className="text-[11px] text-[var(--color-fg-muted)] font-medium">Statut :</span>
                        <Select
                            value={filterAlert}
                            onChange={(val) => setFilterAlert(val)}
                            options={[
                                { value: 'ALL', label: 'Tous les statuts' },
                                { value: 'ALERTS', label: '⚠ Variations > 20%' },
                                { value: 'STC', label: '★ Sortants STC Inclus' },
                                { value: 'VALIDE', label: '✓ Validés' },
                                { value: 'CLOTURE', label: '✓ Clôturés' },
                            ]}
                            className="text-xs h-[30px] min-w-[150px]"
                        />
                    </div>
                </div>

                <div className="text-xs text-[var(--color-fg-muted)] font-mono">
                    Affichage : <span className="font-bold text-[var(--color-fg)]">{filteredBulletins.length}</span> sur {bulletinsPage.totalElements ?? bulletins.length} salariés
                </div>
            </div>}

            {/* Table des Salariés (Wireframe 1) */}
            {(view === 'overview' || view === 'results' || view === 'validation') && (loading ? (
                <SkeletonTable rows={8} cols={7} />
            ) : (
                <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                    <Table>
                        <Thead>
                            <tr>
                                <Th>MATR.</Th>
                                <Th>SALARIÉ</Th>
                                <Th>POSTE & SITE</Th>
                                <Th className="text-right">BRUT (Ar)</Th>
                                <Th className="text-right">NET (Ar)</Th>
                                <Th className="text-center">ÉCART M-1</Th>
                                <Th className="text-center">STATUT</Th>
                                <Th className="text-right">ACTIONS</Th>
                            </tr>
                        </Thead>
                        <Tbody>
                            {filteredBulletins.length === 0 ? (
                                <TableEmpty
                                    colSpan={8}
                                    title="Aucun salarié calculé pour cette période."
                                    description="Cliquez sur « ⟳ Recalculer tout » ou « Simuler » pour générer les bulletins."
                                />
                            ) : (
                                filteredBulletins.map((b) => (
                                    <Tr 
                                        key={b.idBulletin || b.idEmploye} 
                                    >
                                        <Td isData className="font-bold">
                                            {b.matricule}
                                        </Td>
                                        <Td className="font-semibold">
                                            {b.nomPrenom}
                                        </Td>
                                        <Td>
                                            <span className="font-medium text-[var(--color-fg)] block">{b.intitulePoste || '-'}</span>
                                            <span className="text-[10px] text-[var(--color-fg-subtle)]">{b.nomSite || 'Siège'}</span>
                                        </Td>
                                        <Td isData className="text-right font-bold">
                                            {formatAr(b.totalGainsBruts)}
                                        </Td>
                                        <Td isData className="text-right font-black text-[var(--color-brand-600)] text-[13px]">
                                            {formatAr(b.netAPayer)}
                                        </Td>
                                        <Td className="text-center">
                                            {b.typeBulletin === 'STC' ? (
                                                <Badge size="xs" variant="info">N/A</Badge>
                                            ) : b.ecartM1Pct !== null && b.ecartM1Pct !== undefined ? (
                                                <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 text-[10px] font-mono font-semibold border ${
                                                    b.aAlerte 
                                                        ? 'bg-[var(--color-warning-500)]/15 text-[var(--color-warning-700)] border-[var(--color-warning-500)]/30 font-bold'
                                                        : b.ecartM1Pct >= 0 
                                                            ? 'bg-[var(--color-success-500)]/15 text-[var(--color-success-600)] border-[var(--color-success-500)]/30'
                                                            : 'bg-[var(--color-danger-500)]/15 text-[var(--color-danger-600)] border-[var(--color-danger-500)]/30'
                                                }`}>
                                                    {b.ecartM1Pct >= 0 ? '+' : ''}{Number(b.ecartM1Pct).toFixed(1)}%
                                                    {b.aAlerte && ' ⚠️'}
                                                </span>
                                            ) : (
                                                <span className="text-[10px] font-mono text-[var(--color-fg-subtle)]">0.0%</span>
                                            )}
                                        </Td>
                                        <Td className="text-center">
                                            {b.typeBulletin === 'STC' ? (
                                                <Badge size="xs" variant="info">★ STC inclus</Badge>
                                            ) : b.aAlerte ? (
                                                <Badge size="xs" variant="warning">⚠ Contrôle</Badge>
                                            ) : b.statut === 'CLOTURE' ? (
                                                <Badge size="xs" variant="neutral">✓ Clôturé</Badge>
                                            ) : b.statut === 'VALIDE' ? (
                                                <Badge size="xs" variant="success">✓ Validé</Badge>
                                            ) : (
                                                <Badge size="xs" variant="success">✓ Prêt</Badge>
                                            )}
                                        </Td>
                                        <Td className="text-right">
                                            <button
                                                onClick={() => setSelectedBulletinId(b.idBulletin)}
                                                className="px-2.5 py-1 text-xs font-semibold bg-[var(--color-bg-subtle)] hover:bg-[var(--color-surface-hover)] text-[var(--color-fg)] border border-[var(--color-border-subtle)] cursor-pointer inline-flex items-center gap-1.5"
                                                title="Consulter le bulletin détaillé"
                                            >
                                                <Eye size={12} />
                                                Détail
                                            </button>
                                        </Td>
                                    </Tr>
                                ))
                            )}
                        </Tbody>
                    </Table>

                    <div className="px-4 py-3 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex flex-col gap-2">
                        <Pagination
                            currentPage={currentPage}
                            totalElements={bulletinsPage.totalElements ?? bulletins.length}
                            pageSize={pageSize}
                            onPageChange={setCurrentPage}
                            onPageSizeChange={(nextSize) => { setPageSize(nextSize); setCurrentPage(1); }}
                        />
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
            ))}

            {/* SlideOver Détail Bulletin & Formulaire d'ajustements contextuels (Wireframe 2) */}
            <BulletinPaieDrawer
                isOpen={!!selectedBulletinId}
                onClose={() => {
                    setSelectedBulletinId(null);
                    if (requestedEmployeeId) navigate(`/paie/periodes/${idPeriodeActive}/controle`, { replace: true });
                }}
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
                        <EmployeSearchSelect
                            required
                            value={variableForm.idEmploye}
                            onChange={(employeeId) => setVariableForm({ ...variableForm, idEmploye: employeeId })}
                            placeholder="Rechercher un collaborateur…"
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Select
                            label="Type de Variable"
                            required
                            value={variableForm.typeMouvement}
                            onChange={(val) => setVariableForm({ ...variableForm, typeMouvement: val })}
                            options={[
                                { value: 'PRIM_EXC', label: 'Prime exceptionnelle (Gain)' },
                                { value: 'ACOMPTE_15', label: 'Acompte quinzaine (Retenue)' },
                                { value: 'AVANCE_SPEC', label: 'Avance spéciale (Retenue)' },
                                { value: 'DED_DIV', label: 'Déduction diverse (Retenue)' },
                                ...(Array.isArray(typesMouvements) ? typesMouvements : []).map((t) => ({
                                    value: t.code,
                                    label: `${t.libelle} (${t.sens})`
                                }))
                            ]}
                        />

                        <Select
                            label="Sens"
                            required
                            value={variableForm.sens}
                            onChange={(val) => setVariableForm({ ...variableForm, sens: val })}
                            options={[
                                { value: 'GAIN', label: '+ Gain (Ajout au brut)' },
                                { value: 'RETENUE', label: '- Retenue (Déduction du net)' },
                            ]}
                        />
                    </div>

                    <div className="grid grid-cols-2 gap-3">
                        <Input
                            label="Montant en Ariary"
                            type="number"
                            step="1000"
                            min="1000"
                            required
                            placeholder="Ex: 50000"
                            value={variableForm.montant}
                            onChange={(e) => setVariableForm({ ...variableForm, montant: e.target.value })}
                            className="font-mono"
                        />

                        <Input
                            label="Motif / Référence"
                            type="text"
                            placeholder="Ex: Prime transport projet"
                            value={variableForm.motif}
                            onChange={(e) => setVariableForm({ ...variableForm, motif: e.target.value })}
                        />
                    </div>
                </form>
            </Modal>

            {/* Modale Réouverture de Période (Audit d'urgence avec motif DGI) */}
            <Modal
                isOpen={isReopenModalOpen}
                onClose={() => setIsReopenModalOpen(false)}
                title="Réouverture Exceptionnelle de la Période"
                icon={<RotateCcw size={18} className="text-[var(--color-warning-600)]" />}
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
                    <p className="text-[var(--color-fg-muted)] leading-relaxed">
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
