import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import {
    Banknote, RefreshCw, Plus, SlidersHorizontal, Search,
    Eye, ChevronDown, ChevronRight,
    Filter, X as IconX,
} from 'lucide-react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import DataSectionHeader from '@/components/ui/data-display/DataSectionHeader';
import Alert from '@/components/ui/feedback/Alert';
import InfoDisclosure from '@/components/ui/feedback/InfoDisclosure';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Pagination from '@/components/ui/navigation/Pagination';
import DemandeAcompteAvanceModal from '@/features/employees/components/modals/DemandeAcompteAvanceModal';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useAuth } from '@/context/AuthContext';
import paieService from '@/features/payroll/api/paieService';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';

/* ─── Constantes ──────────────────────────────────────────────────────────── */

const DEFAULT_MODES = [
    { code: 'VIREMENT', libelle: 'Virement bancaire' },
    { code: 'ESPECES', libelle: 'Espèces / Caisse' },
    { code: 'CHEQUE', libelle: 'Chèque bancaire' },
    { code: 'MOBILE_MONEY', libelle: 'Mobile Money' },
];

const ETATS = {
    A_APPROUVER:           { label: 'À approuver',               variant: 'warning' },
    A_VERSER:              { label: 'À verser',                   variant: 'info' },
    VERSEMENT_A_VERIFIER:  { label: 'Versement à vérifier',       variant: 'warning' },
    VERSE_A_IMPUTER:       { label: 'Versé · à imputer',          variant: 'primary' },
    REMBOURSEMENT_EN_COURS:{ label: 'En recouvrement',            variant: 'primary' },
    SOLDE:                 { label: 'Soldé',                      variant: 'success' },
    REJETE:                { label: 'Rejeté',                     variant: 'danger' },
    ANNULE:                { label: 'Annulé',                     variant: 'default' },
};

const PAGE_SIZE_OPTIONS = [10, 25, 50, 100];
const GROUPS_PER_PAGE = 5;
const ROWS_PER_GROUP = 25;

/* ─── Helpers ─────────────────────────────────────────────────────────────── */

/** Extrait le code de période d'un dossier (ex : "2026-08"). */
function getPeriodeCode(item) {
    return item.codePeriode || item.periode || item.periodePaie?.code || item.periodePaie || '—';
}

/* ─── Sous-composant : ligne de résumé de période ─────────────────────────── */
function PeriodeGroupHeader({ code, items, isOpen, onToggle }) {
    const total = items.reduce((s, d) => s + (d.montantInitial || 0), 0);
    const nbApprouver = items.filter(d => d.etatDossier === 'A_APPROUVER').length;
    const nbVerser    = items.filter(d => d.etatDossier === 'A_VERSER').length;
    const nbRecouvrement = items.filter(d =>
        ['REMBOURSEMENT_EN_COURS', 'VERSE_A_IMPUTER'].includes(d.etatDossier)).length;

    return (
        <tr role="row" className="bg-[var(--color-bg-subtle)] border-y border-[var(--color-border)]">
            <td colSpan={9} className="py-2 px-4">
                <button type="button" onClick={onToggle} aria-expanded={isOpen} className="flex min-h-11 w-full flex-wrap items-center gap-3 text-left hover:text-[var(--color-brand-700)] focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]">
                    <span className="text-[var(--color-fg-muted)] text-[11px] shrink-0">
                        {isOpen ? <ChevronDown size={13} className="inline mr-0.5" /> : <ChevronRight size={13} className="inline mr-0.5" />}
                    </span>
                    <span className="font-bold text-sm text-[var(--color-fg)]">
                        Période&nbsp;{code}
                    </span>
                    <span className="text-[11px] text-[var(--color-fg-muted)]">
                        {items.length} dossier{items.length > 1 ? 's' : ''}
                        {' · '}
                        <span className="font-data font-semibold text-[var(--color-fg)]">{formatMoneyAr(total)}</span>
                    </span>
                    <div className="flex gap-1.5 ml-auto flex-wrap">
                        {nbApprouver > 0 && (
                            <Badge variant="warning" size="xs">{nbApprouver} à approuver</Badge>
                        )}
                        {nbVerser > 0 && (
                            <Badge variant="info" size="xs">{nbVerser} à verser</Badge>
                        )}
                        {nbRecouvrement > 0 && (
                            <Badge variant="primary" size="xs">{nbRecouvrement} en recouvrement</Badge>
                        )}
                    </div>
                </button>
            </td>
        </tr>
    );
}

/* ─── Composant principal ─────────────────────────────────────────────────── */

export default function AcomptesAvancesPage() {
    const navigate = useNavigate();
    const location = useLocation();
    const { hasPermission, hasRole } = useAuth();
    const toast = useToast();
    const canWrite = hasPermission('PAIE_WRITE');

    /* État global */
    const [dossiers, setDossiers]         = useState([]);
    const [synthese, setSynthese]          = useState(null);
    const [modes, setModes]               = useState(DEFAULT_MODES);
    const [periodes, setPeriodes]          = useState([]);
    const [loading, setLoading]           = useState(true);
    const [error, setError]               = useState(null);

    /* Filtres */
    const [search, setSearch]             = useState('');
    const [filterEtat, setFilterEtat]     = useState('TOUS');
    const [filterNature, setFilterNature] = useState('TOUS');
    const [filterPeriode, setFilterPeriode] = useState('TOUS');
    const [groupByPeriode, setGroupByPeriode] = useState(true);
    const [openPeriodes, setOpenPeriodes] = useState({});  // { [code]: bool }
    const [groupRowsShown, setGroupRowsShown] = useState({});

    /* Pagination */
    const [page, setPage]         = useState(1);
    const [pageSize, setPageSize] = useState(10);

    /* Sélection / actions */
    const [selectedId, setSelectedId]     = useState(() => Number(location.state?.openAdvanceId) || null);
    const [imputations, setImputations]   = useState([]);
    const [imputationError, setImputationError] = useState(null);
    const [decision, setDecision]         = useState(null);
    const [dateAccord, setDateAccord]     = useState(getTodayDateString());
    const [dateAccordError, setDateAccordError] = useState(null);
    const [reason, setReason]             = useState('');
    const [versement, setVersement]       = useState(null);
    const [dateVersement, setDateVersement] = useState(getTodayDateString());
    const [modeReglement, setModeReglement] = useState('');
    const [referenceVersement, setReferenceVersement] = useState('');
    const [versementErrors, setVersementErrors] = useState({});
    const [versementFormError, setVersementFormError] = useState(null);
    const [decisionFormError, setDecisionFormError] = useState(null);
    const [decisionReasonError, setDecisionReasonError] = useState(null);
    const [submitting, setSubmitting]     = useState(false);
    const [regleAvances, setRegleAvances] = useState(null);
    const [plafondSaisi, setPlafondSaisi] = useState('');
    const [isDemandeModalOpen, setIsDemandeModalOpen] = useState(false);

    const selected = dossiers.find(item => item.idAvance === selectedId) || null;

    /* ── Chargement ─────────────────────────────────────────────────────── */
    const charger = useCallback(async () => {
        setLoading(true); setError(null);
        try {
            const [liste, resume, modesResult, periodesResult, regleResult] = await Promise.all([
                paieService.listerAjustements(null, null, 'ACOMPTE_AVANCE').catch(() => []),
                paieService.syntheseAcomptesAvances().catch(() => null),
                paieService.listerModesReglement().catch(() => DEFAULT_MODES),
                periodePaieService.getAllPeriodes().catch(() => []),
                paieService.getRegleAvances().catch(() => null),
            ]);
            setDossiers(Array.isArray(liste) ? liste : []);
            setSynthese(resume);
            setRegleAvances(regleResult);
            if (regleResult?.plafondSalaireBasePct != null) setPlafondSaisi(String(regleResult.plafondSalaireBasePct));
            const modesList = Array.isArray(modesResult) && modesResult.length > 0 ? modesResult : DEFAULT_MODES;
            setModes(modesList);
            const rawPeriodes = Array.isArray(periodesResult) ? periodesResult : (periodesResult?.content || []);
            const allPeriodes = rawPeriodes.map(p => {
                const id = p.idPeriodePaie ?? p.id_periode_paie ?? p.id;
                const code = p.code ?? p.codePeriode ?? '';
                const libelle = p.libelle ?? '';
                const statut = p.statutLibelle ?? p.statutCode ?? p.statut ?? '';
                const displayLabel = `${code}${libelle ? ` — ${libelle}` : ''}${statut ? ` (${statut})` : ''}`.trim() || `Période #${id}`;
                return { ...p, id, idPeriodePaie: id, code, codePeriode: code, libelle, statut, statutCode: p.statutCode ?? statut, label: displayLabel };
            });
            setPeriodes(allPeriodes);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger les acomptes et avances.'));
        } finally { setLoading(false); }
    }, []);

    useEffect(() => { charger(); }, [charger]);

    /* ── Période active ─────────────────────────────────────────────────── */
    const activePeriod = useMemo(() =>
        periodes.find(p => ['PREPARATION', 'EN_COURS', 'OUVERTE'].includes(p.statutCode || p.statut)) || periodes[0] || null
    , [periodes]);

    /* ── Imputations du dossier sélectionné ─────────────────────────────── */
    useEffect(() => {
        if (!selectedId) return;
        let active = true;
        setImputationError(null);
        paieService.listerImputationsAcompteAvance(selectedId)
            .then(items => { if (active) setImputations(Array.isArray(items) ? items : []); })
            .catch(err => { if (active) setImputationError(getErrorMessage(err, 'Historique indisponible.')); });
        return () => { active = false; };
    }, [selectedId, dossiers]);

    /* ── Options de filtre dynamiques ───────────────────────────────────── */
    const periodeCodes = useMemo(() => {
        const codes = [...new Set(dossiers.map(getPeriodeCode))].filter(c => c && c !== '—').sort().reverse();
        return codes;
    }, [dossiers]);

    const natureCodes = useMemo(() => {
        const map = new Map();
        dossiers.forEach(d => { if (d.typeLibelle) map.set(d.typeCode || d.typeLibelle, d.typeLibelle); });
        return [...map.entries()];
    }, [dossiers]);

    /* ── Filtrage ───────────────────────────────────────────────────────── */
    const visible = useMemo(() => {
        const needle = search.trim().toLocaleLowerCase('fr');
        return dossiers.filter(item => {
            const matchSearch = !needle || [item.nomComplet, item.matricule, item.typeLibelle, item.referenceVersement]
                .some(v => String(v || '').toLocaleLowerCase('fr').includes(needle));
            const matchEtat    = filterEtat    === 'TOUS' || item.etatDossier === filterEtat;
            const matchNature  = filterNature  === 'TOUS' || (item.typeCode || item.typeLibelle) === filterNature;
            const matchPeriode = filterPeriode === 'TOUS' || getPeriodeCode(item) === filterPeriode;
            return matchSearch && matchEtat && matchNature && matchPeriode;
        });
    }, [dossiers, search, filterEtat, filterNature, filterPeriode]);

    /* Remise à la première page quand les filtres changent */
    useEffect(() => { setPage(1); }, [search, filterEtat, filterNature, filterPeriode]);

    /* ── Groupement par période ──────────────────────────────────────────── */
    const groupedByPeriode = useMemo(() => {
        if (!groupByPeriode) return null;
        const map = new Map();
        visible.forEach(item => {
            const code = getPeriodeCode(item);
            if (!map.has(code)) map.set(code, []);
            map.get(code).push(item);
        });
        // Tri décroissant par code période
        return [...map.entries()].sort(([a], [b]) => b.localeCompare(a));
    }, [visible, groupByPeriode]);

    const defaultOpenPeriod = groupedByPeriode?.some(([code]) => code === activePeriod?.code)
        ? activePeriod.code : groupedByPeriode?.[0]?.[0];
    const isPeriodeOpen = code => openPeriodes[code] ?? code === defaultOpenPeriod;
    const togglePeriode = (code) =>
        setOpenPeriodes(prev => ({ ...prev, [code]: !(prev[code] ?? code === defaultOpenPeriod) }));

    /* ── Pagination des dossiers ou des périodes selon la vue ───────────── */
    const totalItems  = visible.length;
    const paginatedItems = useMemo(() => {
        const start = (page - 1) * pageSize;
        return visible.slice(start, start + pageSize);
    }, [visible, page, pageSize]);
    const periodPageCount = Math.max(1, Math.ceil((groupedByPeriode?.length ?? 0) / GROUPS_PER_PAGE));
    const paginatedPeriods = groupedByPeriode?.slice((page - 1) * GROUPS_PER_PAGE, page * GROUPS_PER_PAGE) ?? [];
    useEffect(() => {
        setPage(1);
    }, [groupByPeriode]);
    useEffect(() => {
        if (groupByPeriode && page > periodPageCount) setPage(periodPageCount);
    }, [groupByPeriode, page, periodPageCount]);

    /* ── Badges de filtres actifs ───────────────────────────────────────── */
    const activeFilters = useMemo(() => {
        const chips = [];
        if (filterEtat !== 'TOUS')    chips.push({ key: 'etat',    label: ETATS[filterEtat]?.label || filterEtat,     clear: () => setFilterEtat('TOUS') });
        if (filterNature !== 'TOUS')  chips.push({ key: 'nature',  label: natureCodes.find(([k]) => k === filterNature)?.[1] || filterNature, clear: () => setFilterNature('TOUS') });
        if (filterPeriode !== 'TOUS') chips.push({ key: 'periode', label: `Période ${filterPeriode}`, clear: () => setFilterPeriode('TOUS') });
        return chips;
    }, [filterEtat, filterNature, filterPeriode, natureCodes]);

    const resetFilters = () => { setSearch(''); setFilterEtat('TOUS'); setFilterNature('TOUS'); setFilterPeriode('TOUS'); };

    /* ── Validation versement ───────────────────────────────────────────── */
    const validerVersement = () => {
        const errors = {};
        if (!dateVersement) errors.dateVersement = 'La date du versement est obligatoire.';
        if (!modeReglement) errors.modeReglement = 'Sélectionnez un mode de règlement.';
        if (!referenceVersement.trim()) errors.referenceVersement = 'La référence du versement est obligatoire.';
        setVersementErrors(errors);
        return Object.keys(errors).length === 0;
    };

    /* ── Décision (approbation / rejet) ─────────────────────────────────── */
    const executeDecision = async event => {
        event.preventDefault();
        if (!decision) return;
        if (decision.command === 'REJECT' && !reason.trim()) {
            setDecisionReasonError('Le motif du rejet est obligatoire.');
            setDecisionFormError(null); return;
        }
        if (decision.command === 'APPROVE' && !dateAccord) {
            setDateAccordError("La date d'approbation est obligatoire."); return;
        }
        setDateAccordError(null); setDecisionReasonError(null); setDecisionFormError(null); setSubmitting(true);
        try {
            await paieService.executerCommandeAjustement(
                decision.idAvance, decision.command,
                decision.command === 'REJECT' ? reason.trim() : null, decision.version,
                decision.command === 'APPROVE' ? dateAccord : null,
            );
            setDecision(null); setReason('');
            toast.success(decision.command === 'APPROVE'
                ? 'Demande approuvée. Le versement reste à confirmer.'
                : 'Demande rejetée.');
            await charger();
        } catch (err) { setDecisionFormError(getErrorMessage(err, 'Décision impossible.')); }
        finally { setSubmitting(false); }
    };

    /* ── Confirmation du versement ──────────────────────────────────────── */
    const confirmerVersement = async event => {
        event.preventDefault();
        if (!versement) return;
        setVersementFormError(null);
        if (!validerVersement()) return;
        setSubmitting(true);
        try {
            await paieService.confirmerVersementAcompteAvance(versement.idAvance, {
                dateVersement, modeReglement, referenceVersement: referenceVersement.trim(), expectedVersion: versement.version,
            });
            setVersement(null); setReferenceVersement('');
            toast.success('Versement confirmé. Le solde sera proposé aux bulletins éligibles.');
            await charger();
        } catch (err) {
            const message = getErrorMessage(err, 'Confirmation du versement impossible.');
            if (message.toLocaleLowerCase('fr').includes("ne peut pas précéder l'approbation")) {
                setVersementErrors(cur => ({ ...cur, dateVersement: message }));
            } else {
                setVersementFormError(message);
            }
        }
        finally { setSubmitting(false); }
    };

    /* ── Rendu d'une ligne de dossier ───────────────────────────────────── */
    const renderRow = (item) => {
        const etatMeta = ETATS[item.etatDossier] || { label: item.etatDossier, variant: 'default' };
        const periodeCode = getPeriodeCode(item);

        return (
            <Tr key={item.idAvance} className="group border-b border-[var(--color-border-subtle)] hover:bg-[var(--color-surface-hover)] [&>td]:!py-3.5 [&>td]:align-middle">
                <Td>
                    <span className="font-semibold text-sm">{item.nomComplet}</span>
                    <span className="block mt-0.5 text-xs text-[var(--color-fg-muted)] font-data">{item.matricule}</span>
                </Td>
                <Td>
                    <span className="inline-block px-1.5 py-0.5 text-[11px] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] text-[var(--color-fg-muted)]">
                        {item.typeLibelle}
                    </span>
                </Td>
                {/* Colonne période uniquement si pas de groupement */}
                {!groupByPeriode && (
                    <Td>
                        <span className="font-data text-xs text-[var(--color-fg-muted)]">{periodeCode}</span>
                    </Td>
                )}
                <Td className="font-data text-sm">{formatMoneyAr(item.montantInitial)}</Td>
                <Td className="text-xs">
                    {item.dateVersementConfirme
                        ? <span className="text-[var(--color-success-700)] font-semibold">{fmtDate(item.dateVersementConfirme)}</span>
                        : <span className="text-[var(--color-fg-muted)]">—</span>}
                </Td>
                <Td className="text-right">
                    {item.montantImpute > 0 ? (
                        <div className="flex flex-col items-end gap-0.5">
                            <span className="font-data text-xs font-semibold">{formatMoneyAr(item.montantImpute)}</span>
                            {item.montantInitial > 0 && (
                                <div className="w-16 h-1 bg-[var(--color-border-subtle)] overflow-hidden">
                                    <div
                                        className="h-full bg-[var(--color-brand-500)]"
                                        style={{ width: `${Math.min(100, Math.round((item.montantImpute / item.montantInitial) * 100))}%` }}
                                    />
                                </div>
                            )}
                        </div>
                    ) : <span className="font-data text-xs text-[var(--color-fg-muted)]">0 Ar</span>}
                </Td>
                <Td className="text-right font-data text-sm font-semibold">
                    {item.soldeRestant > 0
                        ? <span className="text-[var(--color-fg)]">{formatMoneyAr(item.soldeRestant)}</span>
                        : <span className="text-[var(--color-fg-muted)]">0 Ar</span>}
                </Td>
                <Td>
                    <Badge variant={etatMeta.variant} size="xs">{etatMeta.label}</Badge>
                </Td>
                <Td className="text-right">
                    <div className="flex items-center justify-end gap-1">
                        <Button
                            variant="outline"
                            size="xs"
                            onClick={() => setSelectedId(item.idAvance)}
                            iconLeft={<Eye size={12} />}
                        >
                            Voir
                        </Button>
                        {canWrite && item.actions?.APPROVE && (
                            <Button
                                variant="primary"
                                size="xs"
                                onClick={() => { setDateAccord(getTodayDateString()); setDateAccordError(null); setDecisionFormError(null); setDecision({ ...item, command: 'APPROVE' }); }}
                            >
                                Approuver
                            </Button>
                        )}
                        {canWrite && item.actions?.CONFIRM_VERSEMENT && (
                            <Button
                                size="xs"
                                onClick={() => { setVersement(item); setModeReglement(item.modeReglement || ''); }}
                            >
                                Verser
                            </Button>
                        )}
                    </div>
                </Td>
            </Tr>
        );
    };

    /* ── Rendu principal ────────────────────────────────────────────────── */
    return (
        <div className="space-y-5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
            <SEO title="Acomptes et avances | GALAXAM GRH" description="Décisions, versements et remboursements des salariés." />

            {/* En-tête */}
            <PageHeader
                title="Acomptes et avances"
                subtitle="Suivi des demandes, versements et remboursements sur bulletin."
                icon={Banknote}
                breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Acomptes et avances' }]}
                actions={
                    <div className="flex flex-wrap gap-2 items-center">
                        {regleAvances && (
                            <div className="flex items-center gap-1 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] px-3 py-1.5 text-xs">
                                <span className="text-[var(--color-fg-muted)] uppercase tracking-wide font-bold text-[10px]">Plafond avances :</span>
                                {hasRole?.('ROLE_ADMIN') ? (
                                    <>
                                        <Input
                                            type="number" min="0" max="500" step="0.01"
                                            aria-label="Plafond des avances en pourcentage"
                                            value={plafondSaisi}
                                            onChange={e => setPlafondSaisi(e.target.value)}
                                            className="w-16 h-6 text-xs px-1"
                                        />
                                        <span className="text-[var(--color-fg-muted)]">%</span>
                                        <Button
                                            size="xs" variant="outline"
                                            disabled={submitting || plafondSaisi === String(regleAvances.plafondSalaireBasePct)}
                                            onClick={async () => {
                                                setSubmitting(true);
                                                try { const saved = await paieService.modifierRegleAvances({ plafondSalaireBasePct: Number(plafondSaisi) }); setRegleAvances(saved); toast.success('Plafond enregistré.'); }
                                                catch (err) { toast.error(getErrorMessage(err, 'Plafond non enregistré.')); }
                                                finally { setSubmitting(false); }
                                            }}
                                        >Enregistrer</Button>
                                    </>
                                ) : (
                                    <strong className="font-data font-bold">{regleAvances.plafondSalaireBasePct} %</strong>
                                )}
                            </div>
                        )}
                        {activePeriod?.id && (
                            <Button
                                variant="outline" size="sm"
                                onClick={() => navigate(`/paie/periodes/${activePeriod.id}/variables`)}
                                iconLeft={<SlidersHorizontal size={14} />}
                                title="Variables de la campagne de paie"
                            >
                                Variables campagne ({activePeriod.code || activePeriod.codePeriode || activePeriod.libelle || ''})
                            </Button>
                        )}
                        {canWrite && (
                            <Button
                                variant="primary" size="sm"
                                onClick={() => setIsDemandeModalOpen(true)}
                                iconLeft={<Plus size={14} />}
                            >
                                Saisir une demande
                            </Button>
                        )}
                    </div>
                }
            />

            {/* Alertes */}
            {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
            {synthese?.aVerifier > 0 && (
                <Alert type="warning" message={`${synthese.aVerifier} dossier(s) historique(s) ont des retenues sans confirmation de versement fiable. Vérifiez les pièces avant toute nouvelle action.`} />
            )}

            {/* KPI — StatCards cliquables pour filtre rapide */}
            {synthese && (
                <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
                    <StatCard
                        title="À approuver"
                        value={synthese.aApprouver}
                        color="warning"
                        subtext="En attente de décision"
                        borderTop
                        active={filterEtat === 'A_APPROUVER'}
                        onClick={() => setFilterEtat(filterEtat === 'A_APPROUVER' ? 'TOUS' : 'A_APPROUVER')}
                    />
                    <StatCard
                        title="À verser"
                        value={synthese.aVerser}
                        color="info"
                        subtext={synthese.aVerifier ? `${synthese.aVerifier} historique(s) à vérifier` : 'Approuvés, en attente de caisse'}
                        borderTop
                        active={filterEtat === 'A_VERSER'}
                        onClick={() => setFilterEtat(filterEtat === 'A_VERSER' ? 'TOUS' : 'A_VERSER')}
                    />
                    <StatCard
                        title="En recouvrement"
                        value={synthese.enRecouvrement}
                        color="brand"
                        subtext="Imputations sur paie en cours"
                        borderTop
                        active={filterEtat === 'REMBOURSEMENT_EN_COURS'}
                        onClick={() => setFilterEtat(filterEtat === 'REMBOURSEMENT_EN_COURS' ? 'TOUS' : 'REMBOURSEMENT_EN_COURS')}
                    />
                    <StatCard
                        title="Encours à recouvrer"
                        value={formatMoneyAr(synthese.encoursVerse)}
                        color="neutral"
                        subtext="Total restant"
                        borderTop
                    />
                </div>
            )}

            {/* Info contextuelle */}
            <p className="text-xs text-[var(--color-fg-muted)]">
                La fiche salarié ou cette interface permet d'enregistrer une demande. Seul un versement confirmé peut être retenu sur le bulletin.
            </p>

            {/* Tableau principal */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                <DataSectionHeader
                    title="Dossiers d'avances et d'acomptes"
                    description={groupByPeriode
                        ? 'Dossiers regroupés par période ; la période courante est ouverte. Les états distinguent décision, versement et imputation sur la paie.'
                        : 'Tous les dossiers sont affichés à plat. Utilisez les filtres pour isoler une étape du traitement.'}
                    count={visible.length}
                    countLabel={`dossier${visible.length > 1 ? 's' : ''}`}
                    tone={(synthese?.aApprouver || 0) > 0 ? 'warning' : 'brand'}
                />

                {/* Barre de filtres */}
                <div className="p-3 border-b border-[var(--color-border-subtle)] space-y-2">
                    <div className="flex flex-col sm:flex-row gap-2 items-stretch sm:items-center justify-between">
                        <p className="text-xs font-semibold text-[var(--color-fg-muted)]">Rechercher ou filtrer les dossiers</p>
                        <div className="flex flex-wrap gap-2 items-center">
                            {/* Recherche */}
                            <div className="w-full sm:w-56">
                                <Input
                                    placeholder="Salarié ou matricule..."
                                    value={search}
                                    onChange={(e) => setSearch(e.target.value)}
                                    iconLeft={<Search size={13} />}
                                    className="text-xs"
                                />
                            </div>

                            {/* Filtre état */}
                            <SearchSelect
                                aria-label="Filtrer par état"
                                value={filterEtat}
                                onChange={val => setFilterEtat(val || 'TOUS')}
                                placeholder="État ↓"
                                options={[
                                    { value: 'TOUS', label: 'Tous les états' },
                                    ...Object.entries(ETATS).map(([value, meta]) => ({ value, label: meta.label }))
                                ]}
                                className="min-w-40 text-xs"
                            />

                            {/* Filtre nature */}
                            <SearchSelect
                                aria-label="Filtrer par nature"
                                value={filterNature}
                                onChange={val => setFilterNature(val || 'TOUS')}
                                placeholder="Nature ↓"
                                options={[
                                    { value: 'TOUS', label: 'Toutes natures' },
                                    ...natureCodes.map(([k, label]) => ({ value: k, label }))
                                ]}
                                className="min-w-40 text-xs"
                            />

                            {/* Filtre période */}
                            <SearchSelect
                                aria-label="Filtrer par période"
                                value={filterPeriode}
                                onChange={val => setFilterPeriode(val || 'TOUS')}
                                placeholder="Période ↓"
                                options={[
                                    { value: 'TOUS', label: 'Toutes périodes' },
                                    ...periodeCodes.map(c => ({ value: c, label: `Période ${c}` }))
                                ]}
                                className="min-w-44 text-xs"
                            />

                            {/* Toggle groupement */}
                            <Button
                                variant={groupByPeriode ? 'primary' : 'outline'}
                                size="sm"
                                onClick={() => setGroupByPeriode(g => !g)}
                                iconLeft={<Filter size={13} />}
                                title={groupByPeriode ? 'Afficher en liste plate' : 'Grouper par période'}
                            >
                                Par période
                            </Button>

                            <Button variant="outline" size="sm" onClick={charger} iconLeft={<RefreshCw size={13} />}>
                                Actualiser
                            </Button>
                        </div>
                    </div>

                    {/* Chips des filtres actifs */}
                    {activeFilters.length > 0 && (
                        <div className="flex flex-wrap gap-1.5 items-center">
                            <span className="text-[11px] text-[var(--color-fg-muted)]">Filtres :</span>
                            {activeFilters.map(f => (
                                <button
                                    key={f.key}
                                    onClick={f.clear}
                                    className="inline-flex items-center gap-1 px-2 py-0.5 text-[11px] font-semibold bg-[var(--color-brand-100)] text-[var(--color-brand-700)] border border-[var(--color-brand-200)] hover:bg-[var(--color-brand-200)] transition-colors"
                                >
                                    {f.label}
                                    <IconX size={10} />
                                </button>
                            ))}
                            <button
                                onClick={resetFilters}
                                className="text-[11px] text-[var(--color-fg-muted)] underline hover:text-[var(--color-fg)] ml-1"
                            >
                                Réinitialiser
                            </button>
                        </div>
                    )}
                </div>

                {/* Corps du tableau */}
                {loading ? (
                    <div className="p-3"><SkeletonTable rows={6} columns={8} bordered={false} /></div>
                ) : visible.length === 0 ? (
                    <div className="p-6">
                        <EmptyState
                            title="Aucun dossier"
                            description={activeFilters.length > 0 || search
                                ? 'Modifiez la recherche ou les filtres.'
                                : 'Aucune demande enregistrée.'}
                        />
                    </div>
                ) : (
                    <div className="overflow-x-auto">
                        <Table className="w-full text-xs" bordered={false}>
                            <Thead>
                                <Tr>
                                    <Th>Salarié ↕</Th>
                                    <Th>Nature</Th>
                                    {!groupByPeriode && <Th>Période</Th>}
                                    <Th>Demandé ↕</Th>
                                    <Th>Versé le ↕</Th>
                                    <Th className="text-right">Imputé</Th>
                                    <Th className="text-right">Reste ↕</Th>
                                    <Th>État</Th>
                                    <Th className="text-right">Action</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {groupByPeriode && groupedByPeriode
                                    ? paginatedPeriods.map(([code, items]) => (
                                        <Fragment key={code}>
                                            <PeriodeGroupHeader
                                                code={code}
                                                items={items}
                                                isOpen={isPeriodeOpen(code)}
                                                onToggle={() => togglePeriode(code)}
                                            />
                                            {isPeriodeOpen(code) && items.slice(0, groupRowsShown[code] || ROWS_PER_GROUP).map(item => renderRow(item))}
                                            {isPeriodeOpen(code) && items.length > (groupRowsShown[code] || ROWS_PER_GROUP) && (
                                                <Tr>
                                                    <Td colSpan={8} className="text-center">
                                                        <Button type="button" variant="ghost" size="sm" onClick={() => setGroupRowsShown(prev => ({ ...prev, [code]: (prev[code] || ROWS_PER_GROUP) + ROWS_PER_GROUP }))}>
                                                            Afficher 25 dossiers supplémentaires · {Math.min(groupRowsShown[code] || ROWS_PER_GROUP, items.length)} sur {items.length}
                                                        </Button>
                                                    </Td>
                                                </Tr>
                                            )}
                                        </Fragment>
                                    ))
                                    : paginatedItems.map(item => renderRow(item))
                                }
                            </Tbody>
                        </Table>
                    </div>
                )}

                {/* Pagination (mode liste plate) */}
                {!groupByPeriode && !loading && visible.length > 0 && (
                    <Pagination
                        currentPage={page}
                        totalItems={totalItems}
                        pageSize={pageSize}
                        pageSizeOptions={PAGE_SIZE_OPTIONS}
                        onPageChange={setPage}
                        onPageSizeChange={(size) => { setPageSize(size); setPage(1); }}
                    />
                )}

                {/* Résumé mode groupé */}
                {groupByPeriode && !loading && visible.length > 0 && (
                    <div className="px-3 py-2 border-t border-[var(--color-border-subtle)] text-[11px] text-[var(--color-fg-muted)] flex flex-wrap items-center justify-between gap-2">
                        <span>
                            <span className="font-data font-bold text-[var(--color-fg)]">{visible.length}</span> dossier{visible.length > 1 ? 's' : ''} sur{' '}
                            <span className="font-data font-bold text-[var(--color-fg)]">{groupedByPeriode?.length ?? 0}</span> période{groupedByPeriode?.length > 1 ? 's' : ''}
                        </span>
                        <div className="flex items-center gap-2">
                            <Button type="button" variant="outline" size="xs" disabled={page <= 1} onClick={() => setPage(page - 1)}>Précédent</Button>
                            <span>Page {page} sur {periodPageCount}</span>
                            <Button type="button" variant="outline" size="xs" disabled={page >= periodPageCount} onClick={() => setPage(page + 1)}>Suivant</Button>
                        </div>
                    </div>
                )}
            </div>

            {/* ── SlideOver détail dossier ──────────────────────────────── */}
            <SlideOver
                isOpen={Boolean(selected)}
                onClose={() => setSelectedId(null)}
                title={selected ? `${selected.typeLibelle} · ${selected.nomComplet}` : 'Dossier'}
                width="lg"
            >
                {selected && (
                    <div className="space-y-4 text-sm">
                        <div className="grid grid-cols-2 gap-2 bg-[var(--color-bg-subtle)] p-3">
                            <span>Demandé</span>       <strong className="text-right font-data">{formatMoneyAr(selected.montantInitial)}</strong>
                            <span>Approuvé le</span>   <strong className="text-right">{selected.dateAccord ? fmtDate(selected.dateAccord) : '—'}</strong>
                            <span>Versé le</span>      <strong className="text-right">{selected.dateVersementConfirme ? fmtDate(selected.dateVersementConfirme) : 'Non confirmé'}</strong>
                            <span>Mode / référence</span><strong className="text-right">{selected.dateVersementConfirme ? `${selected.modeReglement} · ${selected.referenceVersement}` : '—'}</strong>
                            <span>Déjà retenu</span>   <strong className="text-right font-data">{formatMoneyAr(selected.montantImpute)}</strong>
                            <span>Reste dû</span>      <strong className="text-right font-data">{formatMoneyAr(selected.soldeRestant)}</strong>
                            {selected.isAvance && (
                                <><span>Remboursement prévu</span><strong className="text-right">{selected.nombreMensualites} mensualité(s)</strong></>
                            )}
                        </div>
                        <p className="text-xs text-[var(--color-fg-muted)]">Motif : {selected.motif}</p>
                        {canWrite && (
                            <div className="flex flex-wrap gap-2">
                                {selected.actions?.APPROVE && (
                                    <Button size="sm" onClick={() => { setDateAccord(getTodayDateString()); setDateAccordError(null); setDecisionFormError(null); setDecision({ ...selected, command: 'APPROVE' }); }}>
                                        Approuver
                                    </Button>
                                )}
                                {selected.actions?.REJECT && (
                                    <Button variant="outline" size="sm" onClick={() => setDecision({ ...selected, command: 'REJECT' })}>
                                        Rejeter
                                    </Button>
                                )}
                                {selected.actions?.CONFIRM_VERSEMENT && (
                                    <Button size="sm" onClick={() => { setVersement(selected); setModeReglement(selected.modeReglement || ''); }}>
                                        {selected.etatDossier === 'VERSEMENT_A_VERIFIER' ? 'Rapprocher le versement' : 'Confirmer le versement'}
                                    </Button>
                                )}
                            </div>
                        )}
                        <div className="border-t border-[var(--color-border-subtle)] pt-3 space-y-2">
                            <h3 className="text-xs font-bold uppercase tracking-wide">Retenues sur bulletins</h3>
                            {imputationError ? (
                                <Alert type="error" message={imputationError} />
                            ) : imputations.length ? (
                                imputations.map(row => (
                                    <div key={row.idBulletin} className="flex justify-between gap-2 border-b border-[var(--color-border-subtle)] py-1 text-xs">
                                        <span>{row.codePeriode} · bulletin #{row.idBulletin} ({row.statutBulletin})</span>
                                        <strong className="font-data">{formatMoneyAr(row.montant)}</strong>
                                    </div>
                                ))
                            ) : (
                                <p className="text-xs text-[var(--color-fg-muted)]">Aucune retenue détaillée enregistrée.</p>
                            )}
                            {Number(selected.montantImpute) > 0 && imputations.length === 0 && (
                                <p className="text-xs text-[var(--color-warning-600)]">Des imputations anciennes ne disposent pas d'un détail par bulletin.</p>
                            )}
                        </div>
                        <Link to={`/employes/${selected.idEmploye}/paie`} className="text-xs text-[var(--color-brand-600)] underline">
                            Voir la fiche paie du salarié
                        </Link>
                    </div>
                )}
            </SlideOver>

            {/* ── Modal nouvelle demande ──────────────────────────────────── */}
            {isDemandeModalOpen && (
                <DemandeAcompteAvanceModal
                    onClose={() => setIsDemandeModalOpen(false)}
                    onCreated={() => {
                        toast.success("Demande enregistrée avec succès. Elle est désormais en attente d'approbation.");
                        charger();
                    }}
                />
            )}

            {/* ── Modal décision (approbation / rejet) ─────────────────── */}
            {decision && (
                <Modal
                    isOpen
                    title={decision.command === 'APPROVE' ? 'Approuver la demande' : 'Rejeter la demande'}
                    onClose={() => { setDecision(null); setDecisionFormError(null); setDecisionReasonError(null); setDateAccordError(null); }}
                >
                    <form className="space-y-3" onSubmit={executeDecision}>
                        <FormError title="Décision impossible" message={decisionFormError} onClose={() => setDecisionFormError(null)} />
                        <p className="text-xs">{decision.typeLibelle} de {formatMoneyAr(decision.montantInitial)} pour {decision.nomComplet}.</p>
                        {decision.command === 'APPROVE' ? (
                            <>
                                <InfoDisclosure label="Approbation et versement" message="L'approbation n'atteste pas le versement. Confirmez celui-ci séparément après paiement réel." />
                                <Input label="Date d'approbation" type="date" required value={dateAccord} error={dateAccordError}
                                    onChange={e => { setDateAccord(e.target.value); setDateAccordError(null); setDecisionFormError(null); }} />
                            </>
                        ) : (
                            <Input label="Motif du rejet" required value={reason} error={decisionReasonError}
                                onChange={e => { setReason(e.target.value); if (e.target.value.trim()) setDecisionReasonError(null); }}
                                placeholder="Expliquer la décision…" />
                        )}
                        <div className="ui-modal-actions">
                            <Button type="button" variant="outline" onClick={() => setDecision(null)}>Annuler</Button>
                            <Button type="submit" disabled={submitting}>
                                {decision.command === 'APPROVE' ? 'Approuver' : 'Rejeter'}
                            </Button>
                        </div>
                    </form>
                </Modal>
            )}

            {/* ── Modal confirmation de versement ──────────────────────── */}
            {versement && (
                <Modal
                    isOpen
                    title="Confirmer le versement réel"
                    onClose={() => { setVersement(null); setVersementErrors({}); setVersementFormError(null); }}
                >
                    <form className="space-y-3" onSubmit={confirmerVersement}>
                        <Alert type="warning" message="Confirmez cette opération uniquement après mise à disposition réelle des fonds. La date et la référence seront conservées pour l'audit." />
                        <FormError title="Confirmation impossible" message={versementFormError} onClose={() => setVersementFormError(null)} />
                        <Input label="Date du versement" type="date" required value={dateVersement} error={versementErrors.dateVersement}
                            onChange={e => { setDateVersement(e.target.value); setVersementErrors(cur => ({ ...cur, dateVersement: undefined })); setVersementFormError(null); }} />
                        <SearchSelect label="Mode de règlement" required value={modeReglement} error={versementErrors.modeReglement}
                            onChange={value => { setModeReglement(value); setVersementErrors(cur => ({ ...cur, modeReglement: undefined })); setVersementFormError(null); }}
                            placeholder="Choisir un mode…"
                            options={modes.map(m => ({ value: m.code, label: m.libelle }))} />
                        <Input label="Référence du versement" required placeholder="Ex. virement, chèque ou pièce de caisse"
                            value={referenceVersement} error={versementErrors.referenceVersement}
                            onChange={e => { setReferenceVersement(e.target.value); if (e.target.value.trim()) setVersementErrors(cur => ({ ...cur, referenceVersement: undefined })); setVersementFormError(null); }} />
                        <div className="ui-modal-actions">
                            <Button type="button" variant="outline" onClick={() => setVersement(null)}>Annuler</Button>
                            <Button type="submit" disabled={submitting || !modeReglement || !referenceVersement.trim()}>Confirmer</Button>
                        </div>
                    </form>
                </Modal>
            )}
        </div>
    );
}
