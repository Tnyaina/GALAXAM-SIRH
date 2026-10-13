import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import {
    Banknote,
    FileText,
    Receipt,
    Eye,
    TrendingUp,
    TrendingDown,
    Calculator,
    ChevronDown,
    ChevronRight,
    AlertCircle,
    Search,
} from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Alert from '@/components/ui/feedback/Alert';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card } from '@/components/ui/data-display/Card';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Pagination from '@/components/ui/navigation/Pagination';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate } from '@/utils/dateUtils';
import paieService from '@/features/payroll/api/paieService';
import stcService from '@/features/payroll/api/stcService';
import { getErrorMessage } from '@/api/apiClient';

import DemandeAcompteAvanceModal from '@/features/employees/components/modals/DemandeAcompteAvanceModal';

// Lazy loading des lourds modules et modales de paie externes
const BulletinPaieDrawer = lazy(() => import('@/features/payroll/components/views/BulletinPaieDrawer'));
const StcPreparationModal = lazy(() => import('@/features/payroll/components/modals/stc/StcPreparationModal'));
const StcRecuModal = lazy(() => import('@/features/payroll/components/modals/stc/StcRecuModal'));

function AccordionSection({ id, icon, title, badge = null, children, defaultOpen = true, open, onToggle, variant = 'default' }) {
    const [internalOpen, setInternalOpen] = useState(defaultOpen);
    const isOpen = open ?? internalOpen;
    const variantBorder = variant === 'warning' ? 'border-[var(--color-warning-500)]/40' : variant === 'danger' ? 'border-[var(--color-danger-500)]/40' : 'border-[var(--color-border-subtle)]';
    const iconColor = variant === 'warning' ? 'text-[var(--color-warning-600)]' : variant === 'danger' ? 'text-[var(--color-danger-600)]' : 'text-[var(--color-brand-600)]';
    return (
        <div className={`bg-[var(--color-surface)] border ${variantBorder} rounded-none overflow-hidden`}>
            <button
                type="button"
                id={id}
                onClick={() => onToggle ? onToggle(!isOpen) : setInternalOpen(v => !v)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-[var(--color-bg-subtle)]/60 transition-colors text-left group"
                aria-expanded={isOpen}
            >
                <div className="flex items-center gap-2.5 min-w-0">
                    <span className={`shrink-0 ${iconColor}`}>{icon}</span>
                    <span className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)] truncate">{title}</span>
                    {badge}
                </div>
                <span className="shrink-0 text-[var(--color-fg-muted)] group-hover:text-[var(--color-fg)] transition-colors">
                    {isOpen ? <ChevronDown size={15} /> : <ChevronRight size={15} />}
                </span>
            </button>
            {isOpen && (
                <div className="border-t border-[var(--color-border-subtle)] px-4 py-4">{children}</div>
            )}
        </div>
    );
}

export default function EmployePaieTab({ idEmploye, employe, canWrite }) {
    // État local : synthèse, documents ouverts et retours d'action.
    const [loading, setLoading] = useState(true);
    const [sectionErrors, setSectionErrors] = useState({});
    const [feedbackMsg, setFeedbackMsg] = useState(null);
    const [synthese, setSynthese] = useState(null);
    const [bulletins, setBulletins] = useState([]);
    const [mouvements, setMouvements] = useState([]);
    const [stcList, setStcList] = useState([]);
    const [selectedBulletinId, setSelectedBulletinId] = useState(null);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [isStcPrepOpen, setIsStcPrepOpen] = useState(false);
    const [stcSimulationOnly, setStcSimulationOnly] = useState(true);
    const [selectedStcRecuId, setSelectedStcRecuId] = useState(null);
    const [isDemandeOpen, setIsDemandeOpen] = useState(false);
    const [bulletinQuery, setBulletinQuery] = useState('');
    const [bulletinStatus, setBulletinStatus] = useState('ALL');
    const [bulletinType, setBulletinType] = useState('ALL');
    const [bulletinPage, setBulletinPage] = useState(1);
    const [bulletinPageSize, setBulletinPageSize] = useState(10);
    const [movementQuery, setMovementQuery] = useState('');
    const [movementCategory, setMovementCategory] = useState('ALL');
    const [movementStatus, setMovementStatus] = useState('ALL');
    const [movementPage, setMovementPage] = useState(1);
    const [movementPageSize, setMovementPageSize] = useState(10);
    const [bulletinsOpen, setBulletinsOpen] = useState(true);
    const [movementsOpen, setMovementsOpen] = useState(false);

    const chargerDonnees = useCallback(async ({ silent = false } = {}) => {
        if (!idEmploye) return;
        if (!silent) setLoading(true);
        setSectionErrors({});
        try {
            const [synthRes, bListRes, mvtRes, stcRes] = await Promise.allSettled([
                paieService.getSynthesePaieEmploye(idEmploye),
                paieService.listerBulletinsEmploye(idEmploye),
                paieService.listerAjustements(null, idEmploye),
                stcService.listerStcEmploye(idEmploye),
            ]);
            const errors = {};
            if (synthRes.status === 'fulfilled') setSynthese(synthRes.value);
            else { setSynthese(null); errors.synthese = getErrorMessage(synthRes.reason, 'Synthèse indisponible.'); }
            if (bListRes.status === 'fulfilled') setBulletins(Array.isArray(bListRes.value) ? bListRes.value : []);
            else { setBulletins([]); errors.bulletins = getErrorMessage(bListRes.reason, 'Bulletins indisponibles.'); }
            if (mvtRes.status === 'fulfilled') {
                const value = mvtRes.value;
                setMouvements(Array.isArray(value) ? value : (value?.content || value?.items || []));
            } else { setMouvements([]); errors.mouvements = getErrorMessage(mvtRes.reason, 'Mouvements indisponibles.'); }
            if (stcRes.status === 'fulfilled') setStcList(Array.isArray(stcRes.value) ? stcRes.value : []);
            else { setStcList([]); errors.stc = getErrorMessage(stcRes.reason, 'Soldes de tout compte indisponibles.'); }
            setSectionErrors(errors);
        } catch (err) {
            setSectionErrors({ synthese: getErrorMessage(err, "Erreur lors du chargement du dossier paie de l'employé.") });
        } finally {
            if (!silent) setLoading(false);
        }
    }, [idEmploye]);

    // Effet : charge le dossier paie du collaborateur sélectionné.
    useEffect(() => { chargerDonnees(); }, [chargerDonnees]);

    const handleVoirBulletin = (idBulletin) => { setSelectedBulletinId(idBulletin); setIsDrawerOpen(true); };

    const hasStcNeeded = synthese?.stcActionRequise === true;

    const normalizeSearch = (value) => String(value ?? '').normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();
    const getMovementCategory = (movement) => {
        if (movement.isAcompte) return 'ACOMPTE';
        if (movement.isAvance) return 'AVANCE';
        const value = normalizeSearch([movement.typeMouvement, movement.typeLibelle, movement.categorie, movement.domaine].join(' '));
        if (value.includes('acompte')) return 'ACOMPTE';
        if (value.includes('avance')) return 'AVANCE';
        return 'AUTRE';
    };
    const bulletinTypes = [...new Set(bulletins.map((item) => item.typeBulletin || 'MENSUEL'))];
    const bulletinStatuses = [...new Set(bulletins.map((item) => item.statut).filter(Boolean))];
    const movementStatuses = [...new Set(mouvements.map((item) => item.etatDossier || item.statut).filter(Boolean))];
    const filteredBulletins = bulletins
        .filter((item) => {
            const query = normalizeSearch(bulletinQuery);
            const searchable = normalizeSearch([item.codePeriode, item.libellePeriode, item.typeBulletin, item.statut].join(' '));
            return (!query || searchable.includes(query))
                && (bulletinStatus === 'ALL' || item.statut === bulletinStatus)
                && (bulletinType === 'ALL' || (item.typeBulletin || 'MENSUEL') === bulletinType);
        })
        .sort((a, b) => String(b.codePeriode || b.dateCalcul || '').localeCompare(String(a.codePeriode || a.dateCalcul || '')));
    const filteredMovements = mouvements
        .filter((item) => {
            const query = normalizeSearch(movementQuery);
            const searchable = normalizeSearch([item.typeLibelle, item.typeMouvement, item.motif, item.etatDossier, item.statut, item.idBulletin].join(' '));
            const status = item.etatDossier || item.statut || '';
            return (!query || searchable.includes(query))
                && (movementCategory === 'ALL' || getMovementCategory(item) === movementCategory)
                && (movementStatus === 'ALL' || status === movementStatus);
        })
        .sort((a, b) => String(b.dateDemande || b.datePaiement || '').localeCompare(String(a.dateDemande || a.datePaiement || '')));
    const visibleBulletins = filteredBulletins.slice((bulletinPage - 1) * bulletinPageSize, bulletinPage * bulletinPageSize);
    const visibleMovements = filteredMovements.slice((movementPage - 1) * movementPageSize, movementPage * movementPageSize);
    const acompteCount = mouvements.filter((item) => getMovementCategory(item) === 'ACOMPTE').length;
    const avanceCount = mouvements.filter((item) => getMovementCategory(item) === 'AVANCE').length;

    useEffect(() => { setBulletinPage(1); }, [bulletinQuery, bulletinStatus, bulletinType, bulletinPageSize]);
    useEffect(() => { setMovementPage(1); }, [movementQuery, movementCategory, movementStatus, movementPageSize]);
    useEffect(() => {
        if (movementsOpen) document.getElementById('paie-mouvements')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, [movementsOpen]);

    const focusMovements = (category) => {
        setMovementCategory(category);
        setMovementStatus('ALL');
        setMovementQuery('');
        setMovementPage(1);
        setMovementsOpen(true);
    };

    if (loading) return <div className="space-y-4 p-4"><SkeletonTable rows={4} columns={4} /></div>;

    // Rendu
    return (
        <div className="space-y-3">
            {feedbackMsg && <Alert type={feedbackMsg.type} message={feedbackMsg.message} onClose={() => setFeedbackMsg(null)} />}
            {sectionErrors.synthese && <Alert type="error" title="Synthèse indisponible" message={sectionErrors.synthese} />}

            {hasStcNeeded && (
                <div className="flex items-start gap-2.5 p-3 bg-[var(--color-warning-500)]/10 border border-[var(--color-warning-500)]/40 rounded-none text-xs">
                    <AlertCircle size={15} className="text-[var(--color-warning-600)] shrink-0 mt-0.5" />
                    <div className="space-y-1 flex-1">
                        <p className="font-bold text-[var(--color-warning-700)] dark:text-[var(--color-warning-400)]">Solde de Tout Compte à établir</p>
                        <p className="text-[var(--color-fg-muted)]">Le contrat est terminé et aucun solde de tout compte en cours n’a été trouvé.</p>
                    </div>
                    {canWrite && (
                        <Button type="button" variant="warning" size="xs" iconLeft={<Calculator size={13} />} onClick={() => { setStcSimulationOnly(false); setIsStcPrepOpen(true); }} className="shrink-0">
                            Etablir le STC
                        </Button>
                    )}
                </div>
            )}

            <div className="flex flex-wrap items-center justify-between gap-3">
                <div><h2 className="text-base font-bold text-[var(--color-fg)]">Paie et rémunération</h2><p className="text-xs text-[var(--color-fg-muted)]">Situation du salarié et historique des bulletins</p></div>
                <div className="flex flex-wrap gap-2">
                    {canWrite && <Button type="button" variant="outline" size="xs" iconLeft={<Calculator size={13} />} onClick={() => { setStcSimulationOnly(true); setIsStcPrepOpen(true); }}>Simuler un STC</Button>}
                    {canWrite && <Button type="button" variant="primary" size="xs" onClick={() => setIsDemandeOpen(true)}>Demander un acompte / une avance</Button>}
                </div>
            </div>

            {/* Synthèse lisible en un coup d'œil, fournie par le backend. */}
            {synthese && <>
                <Card padding shadow="none" className="space-y-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                        <div><h3 className="text-sm font-bold">Dernier bulletin</h3><p className="text-xs text-[var(--color-fg-muted)]">{synthese.dernierePeriodeLibelle || synthese.dernierePeriodeCode || 'Aucun bulletin'}{synthese.derniereDateCalcul ? ` · calculé le ${fmtDate(synthese.derniereDateCalcul)}` : ''}</p></div>
                        {synthese.dernierStatut && <Badge variant={synthese.dernierBadgeVariant || (synthese.isDernierValide ? 'success' : synthese.isDernierCalcule ? 'info' : 'default')} size="xs">{synthese.dernierStatut}</Badge>}
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-4 gap-2">
                        <StatCard title="Salaire de base" value={formatMoneyAr(synthese.salaireBaseContractuel)} subtext="Montant contractuel de l'affectation" icon={<Banknote size={16} />} color="brand" bordered={false} />
                        <StatCard title="Net du dernier bulletin" value={synthese.dernierBulletinId ? formatMoneyAr(synthese.dernierNet) : 'Aucun bulletin'} subtext={synthese.dernierBulletinId ? `Brut : ${formatMoneyAr(synthese.dernierBrut)}` : 'Aucun bulletin calculé'} icon={<FileText size={16} />} color="success" bordered={false} />
                        <StatCard title="Acomptes à imputer" value={formatMoneyAr(synthese.acomptesRestants)} subtext={`${acompteCount} mouvement(s) · solde approuvé`} icon={<Receipt size={16} />} color="neutral" bordered={false} onClick={() => focusMovements('ACOMPTE')} />
                        <StatCard title="Avances en cours" value={formatMoneyAr(synthese.avancesRestantes)} subtext={`${avanceCount} mouvement(s) · solde restant`} icon={<Receipt size={16} />} color="neutral" bordered={false} onClick={() => focusMovements('AVANCE')} />
                    </div>
                    {(synthese.demandesAcompteAvanceEnAttente ?? 0) > 0 && <p className="text-xs text-[var(--color-fg-muted)]">{synthese.demandesAcompteAvanceEnAttente} demande(s) en attente d’approbation · détail dans les mouvements ci-dessous.</p>}
                </Card>
                <AccordionSection id="paie-parametres" icon={<Calculator size={15} />} title="Paramètres de calcul et prêts" defaultOpen={false}>
                    <dl className="grid grid-cols-1 sm:grid-cols-2 gap-x-6 text-xs">
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Taux horaire</dt><dd className="font-semibold text-right">{formatMoneyAr(synthese.tauxHoraire)} / h{synthese.diviseurHeures ? ` · base ${synthese.diviseurHeures} h` : ''}</dd></div>
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Taux congé journalier</dt><dd className="font-semibold text-right" title={synthese.tauxJournalierCongeErreur || undefined}>{synthese.tauxJournalierConge == null ? 'À régulariser' : formatMoneyAr(synthese.tauxJournalierConge)}</dd></div>
                        <div className="flex justify-between gap-3 border-b border-[var(--color-border-subtle)] py-2"><dt className="text-[var(--color-fg-muted)]">Capital restant des prêts</dt><dd className="font-semibold text-right">{formatMoneyAr(synthese.capitalRestantPrets)}</dd></div>
                    </dl>
                </AccordionSection>
            </>}

            {/* ===== BULLETINS ===== */}
            <AccordionSection
                id="paie-bulletins"
                icon={<FileText size={15} />}
                title="Bulletins de paie"
                badge={bulletins.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-brand-500)]/15 text-[var(--color-brand-700)] rounded-none">
                        {bulletins.length}
                    </span>
                )}
                open={bulletinsOpen}
                onToggle={setBulletinsOpen}
            >
                {sectionErrors.bulletins ? <Alert type="error" message={sectionErrors.bulletins} /> : bulletins.length === 0 ? (
                    <EmptyState title="Aucun bulletin de paie" description="Aucune paie n'a encore ete calculee ou cloturee pour ce salarie." />
                ) : (
                    <div className="space-y-3">
                        <div className="grid grid-cols-1 gap-2 md:grid-cols-[minmax(220px,1fr)_190px_190px]">
                            <Input
                                aria-label="Rechercher un bulletin par période"
                                placeholder="Rechercher une période…"
                                value={bulletinQuery}
                                onChange={(event) => setBulletinQuery(event.target.value)}
                                iconLeft={<Search size={14} />}
                            />
                            <SearchSelect
                                aria-label="Filtrer par statut de bulletin"
                                value={bulletinStatus}
                                onChange={(val) => setBulletinStatus(val || 'ALL')}
                                placeholder="Tous les statuts"
                                options={[
                                    { value: 'ALL', label: 'Tous les statuts' },
                                    ...bulletinStatuses.map((status) => ({ value: status, label: status })),
                                ]}
                            />
                            <SearchSelect
                                aria-label="Filtrer par type de bulletin"
                                value={bulletinType}
                                onChange={(val) => setBulletinType(val || 'ALL')}
                                placeholder="Tous les types"
                                options={[
                                    { value: 'ALL', label: 'Tous les types' },
                                    ...bulletinTypes.map((type) => ({ value: type, label: type })),
                                ]}
                            />
                        </div>
                        {filteredBulletins.length === 0 ? (
                            <EmptyState title="Aucun bulletin correspondant" description="Modifiez la période, le statut ou le type sélectionné." />
                        ) : <>
                    <div className="overflow-x-auto -mx-4">
                        <Table className="w-full text-xs">
                            <Thead>
                                <Tr>
                                    <Th>Periode</Th>
                                    <Th>Type</Th>
                                        <Th>Calculé le</Th>
                                        <Th className="text-right">Brut</Th>
                                        <Th className="text-right font-bold">Net à payer</Th>
                                    <Th>Statut</Th>
                                    <Th className="text-right">Action</Th>
                                </Tr>
                            </Thead>
                            <Tbody>
                                {visibleBulletins.map((b) => (
                                    <Tr key={b.idBulletin} className="hover:bg-[var(--color-bg-subtle)] transition-colors">
                                        <Td>
                                            <div className="font-semibold text-[var(--color-fg)] leading-tight">{b.codePeriode}</div>
                                            <span className="text-[10px] text-[var(--color-fg-muted)] block truncate max-w-[140px]">{b.libellePeriode}</span>
                                        </Td>
                                        <Td><Badge variant={b.isStc || b.typeBulletin === 'STC' ? 'warning' : 'default'} size="xs">{b.typeBulletin || 'MENSUEL'}</Badge></Td>
                                        <Td className="font-data text-[var(--color-fg-muted)]">{fmtDate(b.dateCalcul)}</Td>
                                        <Td className="text-right font-data">{formatMoneyAr(b.totalGainsBruts)}</Td>
                                        <Td className="text-right font-data font-bold text-[var(--color-fg)]">{formatMoneyAr(b.netAPayer)}</Td>
                                        <Td>
                                            <Badge variant={b.badgeVariant || (b.isValide || b.isCloture ? 'success' : b.isCalcule ? 'info' : 'default')} size="xs">{b.statut}</Badge>
                                        </Td>
                                        <Td className="text-right">
                                            <Button type="button" variant="outline" size="xs" iconLeft={<Eye size={12} />} onClick={() => handleVoirBulletin(b.idBulletin)}>Voir</Button>
                                        </Td>
                                    </Tr>
                                ))}
                            </Tbody>
                        </Table>
                    </div>
                    <Pagination
                        currentPage={bulletinPage}
                        totalItems={filteredBulletins.length}
                        pageSize={bulletinPageSize}
                        onPageChange={setBulletinPage}
                        onPageSizeChange={setBulletinPageSize}
                    />
                        </>}
                    </div>
                )}
            </AccordionSection>

            {/* ===== MOUVEMENTS ===== */}
            <AccordionSection
                id="paie-mouvements"
                icon={<Receipt size={15} />}
                title="Mouvements et ajustements"
                badge={mouvements.length > 0 && (
                    <span className="ml-1 inline-flex items-center justify-center min-w-[20px] h-[20px] px-1.5 text-[10px] font-bold bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] rounded-none">
                        {mouvements.length}
                    </span>
                )}
                open={movementsOpen}
                onToggle={setMovementsOpen}
            >
                {sectionErrors.mouvements ? <Alert type="error" message={sectionErrors.mouvements} /> : mouvements.length === 0 ? (
                    <EmptyState title="Aucun mouvement de paie" description="Aucun acompte, avance, prime ou note de frais n'a ete enregistre pour ce salarie." />
                ) : (
                    <div className="space-y-3">
                        <p className="text-[11px] text-[var(--color-fg-muted)]">Historique en lecture seule · saisie depuis la campagne de paie.</p>
                        <div className="grid grid-cols-1 gap-2 md:grid-cols-[minmax(220px,1fr)_190px_190px]">
                            <Input
                                aria-label="Rechercher un mouvement"
                                placeholder="Rechercher un acompte, une avance, un motif…"
                                value={movementQuery}
                                onChange={(event) => setMovementQuery(event.target.value)}
                                iconLeft={<Search size={14} />}
                            />
                            <SearchSelect
                                aria-label="Filtrer par nature de mouvement"
                                value={movementCategory}
                                onChange={(val) => setMovementCategory(val || 'ALL')}
                                placeholder="Toutes les natures"
                                options={[
                                    { value: 'ALL', label: 'Toutes les natures' },
                                    { value: 'ACOMPTE', label: `ACOMPTE — Acomptes (${acompteCount})` },
                                    { value: 'AVANCE', label: `AVANCE — Avances (${avanceCount})` },
                                    { value: 'AUTRE', label: 'AUTRE — Autres mouvements' },
                                ]}
                            />
                            <SearchSelect
                                aria-label="Filtrer par statut de mouvement"
                                value={movementStatus}
                                onChange={(val) => setMovementStatus(val || 'ALL')}
                                placeholder="Tous les statuts"
                                options={[
                                    { value: 'ALL', label: 'Tous les statuts' },
                                    ...movementStatuses.map((status) => ({ value: status, label: status })),
                                ]}
                            />
                        </div>
                        {filteredMovements.length === 0 ? (
                            <EmptyState title="Aucun mouvement correspondant" description="Modifiez les filtres ou la recherche." />
                        ) : <>
                        <div className="overflow-x-auto -mx-4">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>Date</Th>
                                        <Th>Type de Mouvement</Th>
                                        <Th>Nature</Th>
                                        <Th className="text-right">Montant</Th>
                                        <Th>Motif</Th>
                                        <Th>Statut</Th>
                                        <Th>Imputation</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {visibleMovements.map((m) => {
                                        const isGain = Boolean(m.isGain ?? (m.sens === 'GAIN'));
                                        return (
                                            <Tr key={m.idAvance || m.idAjustement}>
                                                <Td className="font-data text-[var(--color-fg-muted)]">{fmtDate(m.dateDemande || m.datePaiement)}</Td>
                                                <Td>
                                                    <div className="font-semibold text-[var(--color-fg)]">{m.typeLibelle || m.typeMouvement}</div>
                                                    <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono">{m.typeMouvement}</span>
                                                </Td>
                                                <Td>
                                                    <Badge variant={isGain ? 'success' : 'danger'} size="xs" className="flex items-center gap-1 w-fit">
                                                        {isGain ? <TrendingUp size={10} /> : <TrendingDown size={10} />}
                                                        {isGain ? 'GAIN' : 'RETENUE'}
                                                    </Badge>
                                                </Td>
                                                <Td className={`text-right font-data font-bold ${isGain ? 'text-[var(--color-success-600)]' : 'text-[var(--color-danger-600)]'}`}>
                                                    {isGain ? '+' : '-'}{formatMoneyAr(m.montant)}
                                                </Td>
                                                <Td className="text-[var(--color-fg-muted)] max-w-[160px] truncate">{m.motif || 'Non renseigné'}</Td>
                                                <Td>
                                                    <Badge variant={m.etatDossier === 'SOLDE' || m.statut === 'VALIDE' || m.statut === 'REGLE' ? 'success' : m.etatDossier === 'A_APPROUVER' || m.etatDossier === 'VERSEMENT_A_VERIFIER' ? 'warning' : 'default'} size="xs">
                                                        {({ A_APPROUVER: 'À approuver', A_VERSER: 'À verser', VERSEMENT_A_VERIFIER: 'Versement à vérifier', VERSE_A_IMPUTER: 'Versé, à retenir', REMBOURSEMENT_EN_COURS: 'Remboursement en cours', SOLDE: 'Soldé', REJETE: 'Rejeté', ANNULE: 'Annulé' })[m.etatDossier] || m.statut}
                                                    </Badge>
                                                </Td>
                                                <Td>
                                                    {m.etatDossier
                                                        ? <span className="text-[var(--color-fg-muted)]">Solde : {formatMoneyAr(m.soldeRestant)}</span>
                                                        : m.idBulletin
                                                            ? <span className="text-[var(--color-success-600)] font-semibold font-mono">#{m.idBulletin}</span>
                                                            : <span className="text-[var(--color-warning-600)]">En attente</span>
                                                    }
                                                </Td>
                                            </Tr>
                                        );
                                    })}
                                </Tbody>
                            </Table>
                        </div>
                        <Pagination
                            currentPage={movementPage}
                            totalItems={filteredMovements.length}
                            pageSize={movementPageSize}
                            onPageChange={setMovementPage}
                            onPageSizeChange={setMovementPageSize}
                        />
                        </>}
                    </div>
                )}
            </AccordionSection>

            {/* ===== STC (conditionnel) ===== */}
            {(hasStcNeeded || stcList.length > 0 || sectionErrors.stc) && (
                <AccordionSection
                    id="paie-stc"
                    icon={<Calculator size={15} />}
                    title="Solde de Tout Compte (STC)"
                    badge={hasStcNeeded && <Badge variant="warning" size="xs" className="ml-1">À établir</Badge>}
                    defaultOpen={stcList.length > 0}
                    variant={hasStcNeeded ? 'warning' : 'default'}
                >
                    {sectionErrors.stc ? <Alert type="error" message={sectionErrors.stc} /> : stcList.length > 0 ? (
                        <div className="overflow-x-auto -mx-4">
                            <Table className="w-full text-xs">
                                <Thead>
                                    <Tr>
                                        <Th>N Recu STC</Th>
                                        <Th>Date Depart</Th>
                                        <Th>Motif</Th>
                                        <Th className="text-right">Total Brut</Th>
                                        <Th className="text-right">Prets Apures</Th>
                                        <Th className="text-right">Net STC</Th>
                                        <Th>Statut</Th>
                                        <Th className="text-right">Action</Th>
                                    </Tr>
                                </Thead>
                                <Tbody>
                                    {stcList.map((s) => (
                                        <Tr key={s.idStc}>
                                            <Td className="font-mono font-bold text-[var(--color-fg)]">{s.numeroRecu || `STC-#${s.idStc}`}</Td>
                                            <Td>{fmtDate(s.dateDepart)}</Td>
                                            <Td className="text-[var(--color-fg-muted)]">{s.motifRupture}</Td>
                                            <Td className="text-right font-data">{formatMoneyAr(s.totalBrutStc)}</Td>
                                            <Td className="text-right font-data text-[var(--color-danger-600)]">{formatMoneyAr(s.soldePretsDeduit)}</Td>
                                            <Td className="text-right font-data font-bold text-[var(--color-fg)]">{formatMoneyAr(s.netAPayerStc)}</Td>
                                            <Td><Badge variant={s.statut === 'VALIDE' ? 'success' : 'warning'} size="xs">{s.statut}</Badge></Td>
                                            <Td className="text-right">
                                                <Button type="button" variant="outline" size="xs" iconLeft={<FileText size={12} />} onClick={() => setSelectedStcRecuId(s.idStc)}>Quittance</Button>
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    ) : (
                        <p className="text-xs text-[var(--color-fg-muted)] py-2">Aucun solde de tout compte enregistré.</p>
                    )}
                </AccordionSection>
            )}

            {/* ===== MODALES (Lazy) ===== */}
            <Suspense fallback={null}>
                {isDrawerOpen && (
                    <BulletinPaieDrawer
                        isOpen={isDrawerOpen}
                        onClose={() => { setIsDrawerOpen(false); setSelectedBulletinId(null); }}
                        idBulletin={selectedBulletinId}
                        onRecalculateSuccess={() => chargerDonnees({ silent: true })}
                    />
                )}
                {isStcPrepOpen && (
                    <StcPreparationModal
                        isOpen={isStcPrepOpen}
                        onClose={() => setIsStcPrepOpen(false)}
                        idEmploye={idEmploye}
                        nomEmploye={synthese?.nomComplet || employe?.nom || ''}
                        dateDepartInitiale={synthese?.dateFinContrat || null}
                        motifInitial="DEMISSION"
                        simulationOnly={stcSimulationOnly}
                        onStcValide={() => chargerDonnees({ silent: true })}
                    />
                )}
                {selectedStcRecuId && (
                    <StcRecuModal isOpen={Boolean(selectedStcRecuId)} onClose={() => setSelectedStcRecuId(null)} idStc={selectedStcRecuId} />
                )}
                {isDemandeOpen && (
                    <DemandeAcompteAvanceModal
                        idEmploye={idEmploye}
                        employe={employe}
                        synthese={synthese}
                        onClose={() => setIsDemandeOpen(false)}
                        onCreated={() => { setFeedbackMsg({ type: 'success', message: 'Demande enregistrée en attente d’approbation.' }); chargerDonnees({ silent: true }); }}
                    />
                )}
            </Suspense>
        </div>
    );
}
