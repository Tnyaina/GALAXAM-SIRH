import { useCallback, useEffect, useMemo, useState } from 'react';
import { Banknote, RefreshCw, Plus, SlidersHorizontal, AlertCircle } from 'lucide-react';
import { Link } from 'react-router-dom';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import Alert from '@/components/ui/feedback/Alert';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Modal from '@/components/ui/overlays/Modal';
import SlideOver from '@/components/ui/overlays/SlideOver';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
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

const DEFAULT_TYPES_ACOMPTES = [
    { code: 'ACOMPTE_15', libelle: 'Acompte sur salaire' },
    { code: 'AVANCE_SPECIALE', libelle: 'Avance exceptionnelle sur salaire' },
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
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE') || hasPermission('ROLE_ADMIN');
    const [dossiers, setDossiers] = useState([]);
    const [synthese, setSynthese] = useState(null);
    const [modes, setModes] = useState(DEFAULT_MODES);
    const [periodes, setPeriodes] = useState([]);
    const [typesAcomptes, setTypesAcomptes] = useState(DEFAULT_TYPES_ACOMPTES);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [feedback, setFeedback] = useState(null);
    const [search, setSearch] = useState('');
    const [filter, setFilter] = useState('TOUS');
    const [selectedId, setSelectedId] = useState(null);
    const [imputations, setImputations] = useState([]);
    const [imputationError, setImputationError] = useState(null);
    const [decision, setDecision] = useState(null);
    const [reason, setReason] = useState('');
    const [versement, setVersement] = useState(null);
    const [dateVersement, setDateVersement] = useState(getTodayDateString());
    const [modeReglement, setModeReglement] = useState('');
    const [referenceVersement, setReferenceVersement] = useState('');
    const [submitting, setSubmitting] = useState(false);

    // Modal de saisie de nouvelle demande
    const [isDemandeModalOpen, setIsDemandeModalOpen] = useState(false);
    const [demandeSubmitting, setDemandeSubmitting] = useState(false);
    const [demandeError, setDemandeError] = useState(null);
    const [demandeForm, setDemandeForm] = useState({
        idEmploye: '',
        idPeriodePaie: '',
        typeMouvement: 'ACOMPTE_15',
        montant: '',
        motif: '',
        dateDemande: getTodayDateString(),
        modeReglement: 'VIREMENT',
    });

    const selected = dossiers.find(item => item.idAvance === selectedId) || null;

    const charger = useCallback(async () => {
        setLoading(true); setError(null);
        try {
            const [liste, resume, modesResult, periodesResult, typesResult] = await Promise.all([
                paieService.listerAjustements(null, null, 'ACOMPTE_AVANCE').catch(() => []),
                paieService.syntheseAcomptesAvances().catch(() => null),
                paieService.listerModesReglement().catch(() => DEFAULT_MODES),
                periodePaieService.getAllPeriodes().catch(() => []),
                paieService.listerTypesMouvements().catch(() => DEFAULT_TYPES_ACOMPTES),
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

            const filteredTypes = (Array.isArray(typesResult) ? typesResult : [])
                .filter(t => ['ACOMPTE', 'AVANCE'].includes(t.categorie) || t.sens === 'RETENUE');
            const typesList = filteredTypes.length > 0 ? filteredTypes : DEFAULT_TYPES_ACOMPTES;
            setTypesAcomptes(typesList);

            // Initialiser la période et le type par défaut pour la modale
            const active = allPeriodes.find(p => ['PREPARATION', 'EN_COURS', 'OUVERTE'].includes(p.statutCode || p.statut)) || allPeriodes[0];
            setDemandeForm(prev => ({
                ...prev,
                idPeriodePaie: active?.id ? String(active.id) : (allPeriodes[0]?.id ? String(allPeriodes[0].id) : ''),
                typeMouvement: typesList[0]?.code || 'ACOMPTE_15',
                modeReglement: modesList[0]?.code || 'VIREMENT',
            }));
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

    const executeDecision = async event => {
        event.preventDefault();
        if (!decision) return;
        setSubmitting(true); setError(null);
        try {
            await paieService.executerCommandeAjustement(decision.idAvance, decision.command,
                decision.command === 'REJECT' ? reason.trim() : null, decision.version);
            setDecision(null); setReason('');
            setFeedback({ type: 'success', message: decision.command === 'APPROVE' ? 'Demande approuvée. Le versement reste à confirmer.' : 'Demande rejetée.' });
            await charger();
        } catch (err) { setError(getErrorMessage(err, 'Décision impossible.')); }
        finally { setSubmitting(false); }
    };
    const confirmerVersement = async event => {
        event.preventDefault();
        if (!versement) return;
        setSubmitting(true); setError(null);
        try {
            await paieService.confirmerVersementAcompteAvance(versement.idAvance, {
                dateVersement, modeReglement, referenceVersement: referenceVersement.trim(), expectedVersion: versement.version,
            });
            setVersement(null); setReferenceVersement('');
            setFeedback({ type: 'success', message: 'Versement confirmé. Le solde sera proposé aux bulletins éligibles.' });
            await charger();
        } catch (err) { setError(getErrorMessage(err, 'Confirmation du versement impossible.')); }
        finally { setSubmitting(false); }
    };

    const soumettreDemande = async event => {
        event.preventDefault();
        setDemandeError(null);
        if (!demandeForm.idEmploye) {
            setDemandeError('Veuillez sélectionner un salarié.');
            return;
        }
        const montantNum = parseFloat(demandeForm.montant);
        if (isNaN(montantNum) || montantNum <= 0) {
            setDemandeError('Veuillez renseigner un montant valide supérieur à 0 (jusqu’à 2 décimales autorisées).');
            return;
        }
        if (!demandeForm.motif || demandeForm.motif.trim().length < 5) {
            setDemandeError('Le motif doit contenir au moins 5 caractères.');
            return;
        }

        setDemandeSubmitting(true);
        try {
            await paieService.creerAjustement({
                idEmploye: Number(demandeForm.idEmploye),
                idPeriodePaie: demandeForm.idPeriodePaie ? Number(demandeForm.idPeriodePaie) : (activePeriod?.id || null),
                typeMouvement: demandeForm.typeMouvement,
                sens: 'RETENUE',
                montant: Number(montantNum.toFixed(2)),
                motif: demandeForm.motif.trim(),
                dateDemande: demandeForm.dateDemande || getTodayDateString(),
                modeReglement: demandeForm.modeReglement || 'VIREMENT',
            });
            setIsDemandeModalOpen(false);
            setFeedback({
                type: 'success',
                message: 'Demande enregistrée avec succès. Elle est désormais en attente d’approbation.',
            });
            setDemandeForm(prev => ({
                ...prev,
                idEmploye: '',
                montant: '',
                motif: '',
            }));
            await charger();
        } catch (err) {
            setDemandeError(getErrorMessage(err, 'Impossible d’enregistrer la demande.'));
        } finally {
            setDemandeSubmitting(false);
        }
    };

    return <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
        <SEO title="Acomptes et avances | GALAXAM SIRH" description="Décisions, versements et remboursements des salariés." />
        <PageHeader title="Acomptes et avances" subtitle="Demande → approbation → versement confirmé → retenues sur bulletin → solde"
            icon={Banknote} breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Acomptes et avances' }]} />
        {feedback && <Alert type={feedback.type} message={feedback.message} onClose={() => setFeedback(null)} />}
        {error && <Alert type="error" message={error} onClose={() => setError(null)} />}
        {synthese?.aVerifier > 0 && <Alert type="warning" message={`${synthese.aVerifier} dossier(s) historique(s) ont des retenues sans confirmation de versement fiable. Vérifiez les pièces avant toute nouvelle action.`} />}
        <div className="flex flex-wrap justify-between items-center gap-2">
            <p className="text-xs text-[var(--color-fg-muted)]">La fiche salarié ou cette interface permet d’enregistrer une demande. Seul un versement confirmé peut être retenu sur le bulletin.</p>
            <div className="flex flex-wrap gap-2 items-center">
                <Button
                    variant="primary"
                    size="sm"
                    onClick={() => { setDemandeError(null); setIsDemandeModalOpen(true); }}
                    iconLeft={<Plus size={14} />}
                >
                    Saisir une demande
                </Button>
                {activePeriod?.id && (
                    <Link
                        to={`/paie/periodes/${activePeriod.id}/variables`}
                        className="inline-flex items-center gap-1.5 border border-[var(--color-border)] bg-[var(--color-bg)] px-3 py-2 text-xs font-semibold text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] transition-colors"
                        title="Voir l'étape Variables de la campagne de paie"
                    >
                        <SlidersHorizontal size={13} />
                        Variables campagne ({activePeriod.code || activePeriod.codePeriode || activePeriod.libelle || ''})
                    </Link>
                )}
                <Button variant="outline" size="sm" onClick={charger} iconLeft={<RefreshCw size={14} />}>
                    Actualiser
                </Button>
            </div>
        </div>
        {synthese && <div className="grid grid-cols-2 lg:grid-cols-4 gap-2">
            <StatCard title="À approuver" value={synthese.aApprouver} color="warning" />
            <StatCard title="À verser" value={synthese.aVerser} subtext={synthese.aVerifier ? `${synthese.aVerifier} historique(s) à vérifier` : undefined} color="info" />
            <StatCard title="En recouvrement" value={synthese.enRecouvrement} color="brand" />
            <StatCard title="Encours versé" value={formatMoneyAr(synthese.encoursVerse)} color="neutral" />
        </div>}
        <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-3 space-y-3">
            <div className="flex flex-wrap gap-2 items-center justify-between">
                <h2 className="text-sm font-bold text-[var(--color-fg)]">Dossiers</h2>
                <div className="flex flex-wrap gap-2 w-full sm:w-auto">
                    <Input aria-label="Rechercher un dossier" placeholder="Nom, matricule ou référence…" value={search} onChange={event => setSearch(event.target.value)} />
                    <Select aria-label="Filtrer les dossiers" value={filter} onChange={setFilter} options={[{ value: 'TOUS', label: 'Tous les états' }, ...Object.entries(ETATS).map(([value, meta]) => ({ value, label: meta.label }))]} containerClassName="min-w-44" />
                </div>
            </div>
            {loading ? <p className="text-xs text-[var(--color-fg-muted)] py-6">Chargement des dossiers…</p>
                : visible.length === 0 ? <EmptyState title="Aucun dossier" description={search || filter !== 'TOUS' ? 'Modifiez la recherche ou le filtre.' : 'Aucune demande enregistrée.'} />
                : <div className="overflow-x-auto"><Table className="w-full text-xs" bordered={false}><Thead><Tr><Th>Salarié</Th><Th>Nature</Th><Th>Demandé</Th><Th>Versé le</Th><Th className="text-right">Imputé</Th><Th className="text-right">Reste</Th><Th>État</Th><Th className="text-right">Détail</Th></Tr></Thead><Tbody>
                    {visible.map(item => <Tr key={item.idAvance}>
                        <Td><span className="font-semibold">{item.nomComplet}</span><span className="block text-[var(--color-fg-muted)]">{item.matricule}</span></Td>
                        <Td>{item.typeLibelle}</Td><Td className="font-mono">{formatMoneyAr(item.montantInitial)}</Td>
                        <Td>{item.dateVersementConfirme ? fmtDate(item.dateVersementConfirme) : '—'}</Td>
                        <Td className="text-right font-mono">{formatMoneyAr(item.montantImpute)}</Td>
                        <Td className="text-right font-mono font-bold">{formatMoneyAr(item.soldeRestant)}</Td>
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
                    {selected.actions?.APPROVE && <Button size="sm" onClick={() => setDecision({ ...selected, command: 'APPROVE' })}>Approuver</Button>}
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
        {isDemandeModalOpen && <Modal isOpen title="Saisir une demande d'acompte ou d'avance" onClose={() => setIsDemandeModalOpen(false)} size="lg">
            <form className="space-y-4 text-xs" onSubmit={soumettreDemande}>
                {demandeError && (
                    <div role="alert" className="flex items-start gap-2 border border-[var(--color-danger-500)]/35 bg-[var(--color-danger-500)]/10 p-3 text-[var(--color-danger-700)]">
                        <AlertCircle size={15} className="mt-0.5 shrink-0" />
                        <span>{demandeError}</span>
                    </div>
                )}
                <div>
                    <EmployeSearchSelect
                        required
                        label="Collaborateur bénéficiaire"
                        value={demandeForm.idEmploye}
                        onChange={(empId) => {
                            setDemandeError(null);
                            setDemandeForm(prev => ({ ...prev, idEmploye: empId }));
                        }}
                        placeholder="Rechercher par nom, prénom ou matricule…"
                    />
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Select
                        label="Nature de la demande"
                        required
                        value={demandeForm.typeMouvement}
                        onChange={(val) => setDemandeForm(prev => ({ ...prev, typeMouvement: val }))}
                        options={typesAcomptes.length > 0 ? typesAcomptes.map(t => ({
                            value: t.code,
                            label: t.libelle
                        })) : DEFAULT_TYPES_ACOMPTES.map(t => ({
                            value: t.code,
                            label: t.libelle
                        }))}
                    />
                    <div>
                        <Select
                            label="Période de rattachement"
                            required
                            value={demandeForm.idPeriodePaie}
                            onChange={(val) => setDemandeForm(prev => ({ ...prev, idPeriodePaie: val }))}
                            options={periodes.map(p => ({
                                value: String(p.id ?? p.idPeriodePaie),
                                label: p.label || `${p.code || p.codePeriode || 'Période'} (${p.statutLibelle || p.statutCode || p.statut || ''})`
                            }))}
                            placeholder={periodes.length === 0 ? "Chargement des périodes…" : "Choisir une période…"}
                        />
                        <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Période de paie sur laquelle le dossier est ouvert.</p>
                    </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                        <Input
                            label="Montant demandé en Ariary"
                            type="number"
                            step="0.01"
                            min="0.01"
                            required
                            placeholder="Ex. 50000.00"
                            value={demandeForm.montant}
                            onChange={(e) => setDemandeForm(prev => ({ ...prev, montant: e.target.value }))}
                        />
                        <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Montant en Ariary (jusqu'à 2 décimales, ex: 2000.50).</p>
                    </div>
                    <div>
                        <Input
                            label="Date de la demande"
                            type="date"
                            required
                            value={demandeForm.dateDemande}
                            onChange={(e) => setDemandeForm(prev => ({ ...prev, dateDemande: e.target.value }))}
                        />
                        <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Date de dépôt de la demande par le collaborateur.</p>
                    </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                        <Select
                            label="Mode de versement prévisionnel"
                            required
                            value={demandeForm.modeReglement}
                            onChange={(val) => setDemandeForm(prev => ({ ...prev, modeReglement: val }))}
                            options={(modes.length > 0 ? modes : DEFAULT_MODES).map(m => ({
                                value: m.code,
                                label: m.libelle
                            }))}
                        />
                        <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Moyen de règlement (virement, espèces en caisse, chèque...).</p>
                    </div>
                    <div>
                        <Input
                            label="Motif / Justification"
                            type="text"
                            required
                            minLength={5}
                            placeholder="Ex. Avance sur salaire exceptionnelle"
                            value={demandeForm.motif}
                            onChange={(e) => setDemandeForm(prev => ({ ...prev, motif: e.target.value }))}
                        />
                        <p className="mt-1 text-[11px] text-[var(--color-fg-muted)]">Justification obligatoire (au moins 5 caractères).</p>
                    </div>
                </div>
                <div className="flex justify-end gap-2 pt-2 border-t border-[var(--color-border-subtle)]">
                    <Button type="button" variant="outline" size="sm" onClick={() => setIsDemandeModalOpen(false)}>
                        Annuler
                    </Button>
                    <Button type="submit" variant="primary" size="sm" disabled={demandeSubmitting}>
                        {demandeSubmitting ? 'Enregistrement…' : 'Enregistrer la demande'}
                    </Button>
                </div>
            </form>
        </Modal>}
        {decision && <Modal isOpen title={decision.command === 'APPROVE' ? 'Approuver la demande' : 'Rejeter la demande'} onClose={() => setDecision(null)}>
            <form className="space-y-3" onSubmit={executeDecision}>
                <p className="text-xs">{decision.typeLibelle} de {formatMoneyAr(decision.montantInitial)} pour {decision.nomComplet}.</p>
                {decision.command === 'APPROVE' ? <Alert type="info" message="L’approbation n’atteste pas le versement. Confirmez celui-ci séparément après paiement réel." />
                    : <Input label="Motif du rejet" required value={reason} onChange={event => setReason(event.target.value)} placeholder="Expliquer la décision…" />}
                <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setDecision(null)}>Annuler</Button><Button type="submit" disabled={submitting}>{decision.command === 'APPROVE' ? 'Approuver' : 'Rejeter'}</Button></div>
            </form>
        </Modal>}
        {versement && <Modal isOpen title="Confirmer le versement réel" onClose={() => setVersement(null)}>
            <form className="space-y-3" onSubmit={confirmerVersement}>
                <Alert type="warning" message="Confirmez cette opération uniquement après mise à disposition réelle des fonds. La date et la référence seront conservées pour l’audit." />
                <Input label="Date du versement" type="date" required max={getTodayDateString()} value={dateVersement} onChange={event => setDateVersement(event.target.value)} />
                <Select label="Mode de règlement" value={modeReglement} onChange={setModeReglement} placeholder="Choisir un mode…" options={modes.map(mode => ({ value: mode.code, label: mode.libelle }))} />
                <Input label="Référence du versement" required placeholder="Ex. virement, chèque ou pièce de caisse" value={referenceVersement} onChange={event => setReferenceVersement(event.target.value)} />
                <div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => setVersement(null)}>Annuler</Button><Button type="submit" disabled={submitting || !modeReglement || !referenceVersement.trim()}>Confirmer</Button></div>
            </form>
        </Modal>}
    </div>;
}
