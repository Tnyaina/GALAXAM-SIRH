import { useCallback, useEffect, useMemo, useState } from 'react';
import { Banknote, RefreshCw, Plus, SlidersHorizontal, Search } from 'lucide-react';
import { useNavigate, Link } from 'react-router-dom';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import { useToast } from '@/components/ui/feedback/Toast';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import SlideOver from '@/components/ui/overlays/SlideOver';
import DemandeAcompteAvanceModal from '@/features/employees/components/modals/DemandeAcompteAvanceModal';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { useAuth } from '@/context/AuthContext';
import paieService from '@/features/payroll/api/paieService';
import { periodePaieService } from '@/features/attendance/api/attendanceApi';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';

const DEFAULT_MODES = [
    { code: 'VIREMENT', libelle: 'Virement bancaire' },
    { code: 'ESPECES', libelle: 'Espèces / Caisse' },
    { code: 'CHEQUE', libelle: 'Chèque bancaire' },
    { code: 'MOBILE_MONEY', libelle: 'Mobile Money' },
];

const ETATS = {
    A_APPROUVER: { label: 'À approuver', variant: 'warning' },
    A_VERSER: { label: 'À verser', variant: 'info' },
    VERSEMENT_A_VERIFIER: { label: 'Versement historique à vérifier', variant: 'warning' },
    VERSE_A_IMPUTER: { label: 'Versé · à imputer', variant: 'primary' },
    REMBOURSEMENT_EN_COURS: { label: 'Remboursement en cours', variant: 'primary' },
    SOLDE: { label: 'Soldé', variant: 'success' },
    REJETE: { label: 'Rejeté', variant: 'danger' },
    ANNULE: { label: 'Annulé', variant: 'default' },
};

export default function AcomptesAvancesPage() {
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const toast = useToast();
    const canWrite = hasPermission('PAIE_WRITE');
    const [dossiers, setDossiers] = useState([]);
    const [synthese, setSynthese] = useState(null);
    const [modes, setModes] = useState(DEFAULT_MODES);
    const [periodes, setPeriodes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [search, setSearch] = useState('');
    const [filter, setFilter] = useState('TOUS');
    const [selectedId, setSelectedId] = useState(null);
    const [imputations, setImputations] = useState([]);
    const [imputationError, setImputationError] = useState(null);
    const [decision, setDecision] = useState(null);
    const [dateAccord, setDateAccord] = useState(getTodayDateString());
    const [dateAccordError, setDateAccordError] = useState(null);
    const [reason, setReason] = useState('');
    const [versement, setVersement] = useState(null);
    const [dateVersement, setDateVersement] = useState(getTodayDateString());
    const [modeReglement, setModeReglement] = useState('');
    const [referenceVersement, setReferenceVersement] = useState('');
    const [versementErrors, setVersementErrors] = useState({});
    const [versementFormError, setVersementFormError] = useState(null);
    const [decisionFormError, setDecisionFormError] = useState(null);
    const [decisionReasonError, setDecisionReasonError] = useState(null);
    const [submitting, setSubmitting] = useState(false);

    // Modal de saisie de nouvelle demande
    const [isDemandeModalOpen, setIsDemandeModalOpen] = useState(false);

    const selected = dossiers.find(item => item.idAvance === selectedId) || null;

    const charger = useCallback(async () => {
        setLoading(true); setError(null);
        try {
            const [liste, resume, modesResult, periodesResult] = await Promise.all([
                paieService.listerAjustements(null, null, 'ACOMPTE_AVANCE').catch(() => []),
                paieService.syntheseAcomptesAvances().catch(() => null),
                paieService.listerModesReglement().catch(() => DEFAULT_MODES),
                periodePaieService.getAllPeriodes().catch(() => []),
            ]);
            setDossiers(Array.isArray(liste) ? liste : []);
            setSynthese(resume);
            const modesList = Array.isArray(modesResult) && modesResult.length > 0 ? modesResult : DEFAULT_MODES;
            setModes(modesList);
            const rawPeriodes = Array.isArray(periodesResult) ? periodesResult : (periodesResult?.content || []);
            const allPeriodes = rawPeriodes.map(p => {
                const id = p.idPeriodePaie ?? p.id_periode_paie ?? p.id;
                const code = p.code ?? p.codePeriode ?? '';
                const libelle = p.libelle ?? '';
                const statut = p.statutLibelle ?? p.statutCode ?? p.statut ?? '';
                const displayLabel = `${code}${libelle ? ` — ${libelle}` : ''}${statut ? ` (${statut})` : ''}`.trim() || `Période #${id}`;
                return {
                    ...p,
                    id,
                    idPeriodePaie: id,
                    code,
                    codePeriode: code,
                    libelle,
                    statut,
                    statutCode: p.statutCode ?? statut,
                    label: displayLabel,
                };
            });
            setPeriodes(allPeriodes);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger les acomptes et avances.'));
        } finally { setLoading(false); }
    }, []);

    useEffect(() => { charger(); }, [charger]);

    const activePeriod = useMemo(() => {
        return periodes.find(p => ['PREPARATION', 'EN_COURS', 'OUVERTE'].includes(p.statutCode || p.statut)) || periodes[0] || null;
    }, [periodes]);
    useEffect(() => {
        if (!selectedId) return;
        let active = true;
        setImputationError(null);
        paieService.listerImputationsAcompteAvance(selectedId)
            .then(items => { if (active) setImputations(Array.isArray(items) ? items : []); })
            .catch(err => { if (active) setImputationError(getErrorMessage(err, 'Historique indisponible.')); });
        return () => { active = false; };
    }, [selectedId, dossiers]);

    const visible = useMemo(() => dossiers.filter(item => {
        const needle = search.trim().toLocaleLowerCase('fr');
        const matchesSearch = !needle || [item.nomComplet, item.matricule, item.typeLibelle, item.referenceVersement]
            .some(value => String(value || '').toLocaleLowerCase('fr').includes(needle));
        return matchesSearch && (filter === 'TOUS' || item.etatDossier === filter);
    }), [dossiers, search, filter]);

    const validerVersement = () => {
        const errors = {};
        if (!dateVersement) errors.dateVersement = 'La date du versement est obligatoire.';
        if (!modeReglement) errors.modeReglement = 'Sélectionnez un mode de règlement.';
        if (!referenceVersement.trim()) errors.referenceVersement = 'La référence du versement est obligatoire.';
        setVersementErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const executeDecision = async event => {
        event.preventDefault();
        if (!decision) return;
        if (decision.command === 'REJECT' && !reason.trim()) {
            setDecisionReasonError('Le motif du rejet est obligatoire.');
            setDecisionFormError(null);
            return;
        }
        if (decision.command === 'APPROVE') {
            if (!dateAccord) {
                setDateAccordError("La date d'approbation est obligatoire.");
                return;
            }
        }
        setDateAccordError(null);
        setDecisionReasonError(null); setDecisionFormError(null); setSubmitting(true);
        try {
            await paieService.executerCommandeAjustement(decision.idAvance, decision.command,
                decision.command === 'REJECT' ? reason.trim() : null, decision.version,
                decision.command === 'APPROVE' ? dateAccord : null);
            setDecision(null); setReason('');
            toast.success(decision.command === 'APPROVE' ? 'Demande approuvée. Le versement reste à confirmer.' : 'Demande rejetée.');
            await charger();
        } catch (err) { setDecisionFormError(getErrorMessage(err, 'Décision impossible.')); }
        finally { setSubmitting(false); }
    };
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
            if (message.toLocaleLowerCase('fr').includes('ne peut pas précéder l’approbation')
                || message.toLocaleLowerCase('fr').includes("ne peut pas précéder l'approbation")) {
                setVersementErrors(current => ({ ...current, dateVersement: message }));
            } else {
                setVersementFormError(message);
            }
        }
        finally { setSubmitting(false); }
    };

    return <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
        <SEO title="Acomptes et avances | GALAXAM GRH" description="Décisions, versements et remboursements des salariés." />
        <PageHeader title="Acomptes et avances" subtitle="Suivi des demandes, versements et remboursements sur bulletin."
            icon={Banknote} breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Acomptes et avances' }]} />
        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
        {synthese?.aVerifier > 0 && <Alert type="warning" message={`${synthese.aVerifier} dossier(s) historique(s) ont des retenues sans confirmation de versement fiable. Vérifiez les pièces avant toute nouvelle action.`} />}
        <div className="flex flex-wrap justify-between items-center gap-2">
            <p className="text-xs text-[var(--color-fg-muted)]">La fiche salarié ou cette interface permet d’enregistrer une demande. Seul un versement confirmé peut être retenu sur le bulletin.</p>
            <div className="flex flex-wrap gap-2 items-center">
                {canWrite && <Button
                    variant="primary"
                    size="sm"
                    onClick={() => setIsDemandeModalOpen(true)}
                    iconLeft={<Plus size={14} />}
                >
                    Saisir une demande
                </Button>}
                {activePeriod?.id && (
                    <Button
                        variant="secondary"
                        size="sm"
                        onClick={() => navigate(`/paie/periodes/${activePeriod.id}/variables`)}
                        iconLeft={<SlidersHorizontal size={14} />}
                        title="Voir l'étape Variables de la campagne de paie"
                    >
                        Variables campagne ({activePeriod.code || activePeriod.codePeriode || activePeriod.libelle || ''})
                    </Button>
                )}
                <Button variant="outline" size="sm" onClick={charger} iconLeft={<RefreshCw size={14} />}>
                    Actualiser
                </Button>
            </div>
        </div>
        {synthese && <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <StatCard title="À approuver" value={synthese.aApprouver} color="warning" subtext="En attente de décision" />
            <StatCard title="À verser" value={synthese.aVerser} subtext={synthese.aVerifier ? `${synthese.aVerifier} historique(s) à vérifier` : "Approuvés en attente de caisse"} color="info" />
            <StatCard title="En recouvrement" value={synthese.enRecouvrement} color="brand" subtext="Imputations sur paie en cours" />
            <StatCard title="Encours versé" value={formatMoneyAr(synthese.encoursVerse)} color="neutral" subtext="Total restant à recouvrer" />
        </div>}
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 space-y-3">
            <div className="flex flex-col sm:flex-row gap-2 items-stretch sm:items-center justify-between">
                <h2 className="text-sm font-bold text-[var(--color-fg)]">Dossiers</h2>
                <div className="flex flex-wrap gap-2 items-center w-full sm:w-auto">
                    <div className="w-full sm:w-64">
                        <Input
                            placeholder="Rechercher salarié, matricule..."
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            iconLeft={<Search size={14} />}
                            className="text-xs"
                        />
                    </div>
                    <SearchSelect
                        aria-label="Filtrer les dossiers"
                        value={filter}
                        onChange={(val) => setFilter(val || 'TOUS')}
                        placeholder="Tous les états"
                        options={[
                            { value: 'TOUS', label: 'Tous les états' },
                            ...Object.entries(ETATS).map(([value, meta]) => ({
                                value,
                                label: `${value} — ${meta.label}`
                            }))
                        ]}
                        className="min-w-52 text-xs"
                    />
                </div>
            </div>
            {loading ? <SkeletonTable rows={5} columns={8} bordered={false} />
                : visible.length === 0 ? <EmptyState title="Aucun dossier" description={search || filter !== 'TOUS' ? 'Modifiez la recherche ou le filtre.' : 'Aucune demande enregistrée.'} />
                : <div className="overflow-x-auto"><Table className="w-full text-xs" bordered={false}><Thead><Tr><Th>Salarié</Th><Th>Nature</Th><Th>Demandé</Th><Th>Versé le</Th><Th className="text-right">Imputé</Th><Th className="text-right">Reste</Th><Th>État</Th><Th className="text-right">Détail</Th></Tr></Thead><Tbody>
                    {visible.map(item => <Tr key={item.idAvance}>
                        <Td><span className="font-semibold">{item.nomComplet}</span><span className="block text-[var(--color-fg-muted)]">{item.matricule}</span></Td>
                        <Td>{item.typeLibelle}</Td><Td className="font-data">{formatMoneyAr(item.montantInitial)}</Td>
                        <Td>{item.dateVersementConfirme ? fmtDate(item.dateVersementConfirme) : '—'}</Td>
                        <Td className="text-right font-data">{formatMoneyAr(item.montantImpute)}</Td>
                        <Td className="text-right font-data font-bold">{formatMoneyAr(item.soldeRestant)}</Td>
                        <Td><Badge variant={ETATS[item.etatDossier]?.variant || 'default'} size="xs">{ETATS[item.etatDossier]?.label || item.statut}</Badge></Td>
                        <Td className="text-right"><Button variant="outline" size="xs" onClick={() => setSelectedId(item.idAvance)}>Voir</Button></Td>
                    </Tr>)}
                </Tbody></Table></div>}
        </div>
        <SlideOver isOpen={Boolean(selected)} onClose={() => setSelectedId(null)} title={selected ? `${selected.typeLibelle} · ${selected.nomComplet}` : 'Dossier'} width="lg">
            {selected && <div className="space-y-4 text-sm">
                <div className="grid grid-cols-2 gap-2 bg-[var(--color-bg-subtle)] p-3">
                    <span>Demandé</span><strong className="text-right">{formatMoneyAr(selected.montantInitial)}</strong>
                    <span>Approuvé le</span><strong className="text-right">{selected.dateAccord ? fmtDate(selected.dateAccord) : '—'}</strong>
                    <span>Versé le</span><strong className="text-right">{selected.dateVersementConfirme ? fmtDate(selected.dateVersementConfirme) : 'Non confirmé'}</strong>
                    <span>Mode / référence</span><strong className="text-right">{selected.dateVersementConfirme ? `${selected.modeReglement} · ${selected.referenceVersement}` : '—'}</strong>
                    <span>Déjà retenu</span><strong className="text-right">{formatMoneyAr(selected.montantImpute)}</strong>
                    <span>Reste dû</span><strong className="text-right">{formatMoneyAr(selected.soldeRestant)}</strong>
                </div>
                <p className="text-xs text-[var(--color-fg-muted)]">Motif : {selected.motif}</p>
                {canWrite && <div className="flex flex-wrap gap-2">
                    {selected.actions?.APPROVE && <Button size="sm" onClick={() => { setDateAccord(getTodayDateString()); setDateAccordError(null); setDecisionFormError(null); setDecision({ ...selected, command: 'APPROVE' }); }}>Approuver</Button>}
                    {selected.actions?.REJECT && <Button variant="outline" size="sm" onClick={() => setDecision({ ...selected, command: 'REJECT' })}>Rejeter</Button>}
                    {selected.actions?.CONFIRM_VERSEMENT && <Button size="sm" onClick={() => { setVersement(selected); setModeReglement(selected.modeReglement || ''); }}>{selected.etatDossier === 'VERSEMENT_A_VERIFIER' ? 'Rapprocher le versement' : 'Confirmer le versement'}</Button>}
                </div>}
                <div className="border-t border-[var(--color-border-subtle)] pt-3 space-y-2"><h3 className="text-xs font-bold uppercase">Retenues sur bulletins</h3>
                    {imputationError ? <Alert type="error" message={imputationError} /> : imputations.length ? imputations.map(row =>
                        <div key={row.idBulletin} className="flex justify-between gap-2 border-b border-[var(--color-border-subtle)] py-1 text-xs"><span>{row.codePeriode} · bulletin #{row.idBulletin} ({row.statutBulletin})</span><strong>{formatMoneyAr(row.montant)}</strong></div>)
                        : <p className="text-xs text-[var(--color-fg-muted)]">Aucune retenue détaillée enregistrée.</p>}
                    {Number(selected.montantImpute) > 0 && imputations.length === 0 && <p className="text-xs text-[var(--color-warning-600)]">Des imputations anciennes ne disposent pas d’un détail par bulletin.</p>}
                </div>
                <Link to={`/employes/${selected.idEmploye}/paie`} className="text-xs text-[var(--color-brand-600)] underline">Voir la fiche paie du salarié</Link>
            </div>}
        </SlideOver>
        {isDemandeModalOpen && (
            <DemandeAcompteAvanceModal
                onClose={() => setIsDemandeModalOpen(false)}
                onCreated={() => {
                    toast.success('Demande enregistrée avec succès. Elle est désormais en attente d’approbation.');
                    charger();
                }}
            />
        )}
        {decision && <Modal isOpen title={decision.command === 'APPROVE' ? 'Approuver la demande' : 'Rejeter la demande'} onClose={() => { setDecision(null); setDecisionFormError(null); setDecisionReasonError(null); setDateAccordError(null); }}>
            <form className="space-y-3" onSubmit={executeDecision}>
                <FormError title="Décision impossible" message={decisionFormError} onClose={() => setDecisionFormError(null)} />
                <p className="text-xs">{decision.typeLibelle} de {formatMoneyAr(decision.montantInitial)} pour {decision.nomComplet}.</p>
                {decision.command === 'APPROVE' ? <>
                    <Alert type="info" message="L’approbation n’atteste pas le versement. Confirmez celui-ci séparément après paiement réel." />
                    <Input label="Date d’approbation" type="date" required value={dateAccord} error={dateAccordError} onChange={event => { setDateAccord(event.target.value); setDateAccordError(null); setDecisionFormError(null); }} />
                </>
                    : <Input label="Motif du rejet" required value={reason} error={decisionReasonError} onChange={event => { setReason(event.target.value); if (event.target.value.trim()) setDecisionReasonError(null); }} placeholder="Expliquer la décision…" />}
                <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setDecision(null)}>Annuler</Button><Button type="submit" disabled={submitting}>{decision.command === 'APPROVE' ? 'Approuver' : 'Rejeter'}</Button></div>
            </form>
        </Modal>}
        {versement && <Modal isOpen title="Confirmer le versement réel" onClose={() => { setVersement(null); setVersementErrors({}); setVersementFormError(null); }}>
            <form className="space-y-3" onSubmit={confirmerVersement}>
                <Alert type="warning" message="Confirmez cette opération uniquement après mise à disposition réelle des fonds. La date et la référence seront conservées pour l’audit." />
                <FormError title="Confirmation impossible" message={versementFormError} onClose={() => setVersementFormError(null)} />
                <Input label="Date du versement" type="date" required value={dateVersement} error={versementErrors.dateVersement} onChange={event => { setDateVersement(event.target.value); setVersementErrors(current => ({ ...current, dateVersement: undefined })); setVersementFormError(null); }} />
                <SearchSelect label="Mode de règlement" required value={modeReglement} error={versementErrors.modeReglement} onChange={value => { setModeReglement(value); setVersementErrors(current => ({ ...current, modeReglement: undefined })); setVersementFormError(null); }} placeholder="Choisir un mode…" options={modes.map(mode => ({ value: mode.code, label: mode.libelle }))} />
                <Input label="Référence du versement" required placeholder="Ex. virement, chèque ou pièce de caisse" value={referenceVersement} error={versementErrors.referenceVersement} onChange={event => { setReferenceVersement(event.target.value); if (event.target.value.trim()) setVersementErrors(current => ({ ...current, referenceVersement: undefined })); setVersementFormError(null); }} />
                <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setVersement(null)}>Annuler</Button><Button type="submit" disabled={submitting || !modeReglement || !referenceVersement.trim()}>Confirmer</Button></div>
            </form>
        </Modal>}
    </div>;
}
