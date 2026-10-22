import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Download, Send, Wallet } from 'lucide-react';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox, Textarea } from '@/components/ui/forms/Input';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Pagination from '@/components/ui/navigation/Pagination';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import bulletinReglementService from '@/features/payroll/api/bulletinReglementService';
import BulletinReglementSlideOver from '@/features/payroll/components/views/BulletinReglementSlideOver';
import { usePayrollCampaign } from '@/features/payroll/hooks/usePayrollCampaign';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { useAuth } from '@/context/AuthContext';

const eligible = b => b.modePaiement === 'VIREMENT' && Number(b.reste) > 0 && !b.virementEnAttente && !b.inclusDansStc && b.rib?.trim() && b.banque?.trim();
const status = b => b.inclusDansStc ? ['Inclus dans le STC', 'info'] : b.virementEnAttente ? ['Virement en attente', 'info'] : Number(b.reste) === 0 ? ['Payé', 'success'] : Number(b.verse) > 0 ? ['Partiellement payé', 'warning'] : ['À payer', 'secondary'];
const modeLabel = { VIREMENT: 'Virement bancaire', CHEQUE: 'Chèque', ESPECES: 'Espèces', MOBILE_MONEY: 'Mobile money' };

export default function ReglementsBulletinsPage() {
    const { periodId } = useParams();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');
    const { context } = usePayrollCampaign(Number(periodId) || null);
    const period = context?.selectedPeriod || context?.currentPeriod;
    const navigate = useNavigate();
    const toast = useToast();
    const [bulletins, setBulletins] = useState([]);
    const [lots, setLots] = useState([]);
    const [selected, setSelected] = useState([]);
    const [activeBulletin, setActiveBulletin] = useState(null);
    const [activeLot, setActiveLot] = useState(null);
    const [lines, setLines] = useState([]);
    const [decision, setDecision] = useState(null);
    const [reference, setReference] = useState('');
    const [date, setDate] = useState(getTodayDateString());
    const [motif, setMotif] = useState('');
    const [proof, setProof] = useState(null);
    const [proofKey, setProofKey] = useState(0);
    const [query, setQuery] = useState('');
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [busy, setBusy] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const loadRequest = useRef(0);
    const linesRequest = useRef(0);
    const busyRef = useRef(false);

    const load = useCallback(async ({ silent = false } = {}) => {
        const request = ++loadRequest.current;
        if (!silent) setLoading(true);
        try {
            const [b, l] = await Promise.all([bulletinReglementService.periode(periodId), bulletinReglementService.lots(periodId)]);
            if (request !== loadRequest.current) return false;
            setBulletins(b);
            setLots(l);
            setError('');
            return true;
        } catch (cause) {
            if (request === loadRequest.current) {
                if (!silent) { setBulletins([]); setLots([]); }
                setError(getErrorMessage(cause, 'Règlements indisponibles.'));
            }
            return false;
        } finally { if (request === loadRequest.current) setLoading(false); }
    }, [periodId]);
    const loadLines = useCallback(async id => {
        const request = ++linesRequest.current;
        setLines([]);
        try { const result = await bulletinReglementService.lignes(id); if (request === linesRequest.current) setLines(result); }
        catch (cause) { if (request === linesRequest.current) setError(getErrorMessage(cause, 'Lignes du lot indisponibles.')); }
    }, []);
    useEffect(() => { load(); }, [load]);
    useEffect(() => { if (activeLot) loadLines(activeLot); }, [activeLot, loadLines]);
    useEffect(() => { setSelected([]); setActiveBulletin(null); setActiveLot(null); setDecision(null); setQuery(''); setCurrentPage(1); }, [periodId]);
    const run = async (action, message) => {
        if (busyRef.current) return false;
        busyRef.current = true;
        setBusy(true);
        try { await action(); await load({ silent: true }); if (activeLot) await loadLines(activeLot); toast.success(message); return true; }
        catch (cause) { toast.error(getErrorMessage(cause, 'Opération impossible.')); return false; }
        finally { busyRef.current = false; setBusy(false); }
    };
    const createLot = () => run(async () => {
        const result = await bulletinReglementService.preparerLot(periodId, selectedEligible);
        setActiveLot(result.idLot);
        setSelected([]);
    }, 'Lot préparé. Vérifiez son fichier avant transmission.');
    const download = async id => {
        try {
            const blob = await bulletinReglementService.fichierLot(id);
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a'); a.href = url; a.download = `lot_virements_${id}.csv`; a.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (cause) { toast.error(getErrorMessage(cause, 'Téléchargement impossible.')); }
    };
    const saveDecision = async event => {
        event.preventDefault();
        const ok = await run(() => decision.kind === 'CONFIRMER'
            ? bulletinReglementService.confirmerLigne(decision.line.id, { date, reference: reference.trim(), file: proof })
            : bulletinReglementService.rejeterLigne(decision.line.id, motif.trim()),
        decision.kind === 'CONFIRMER' ? 'Paiement par virement enregistré.' : 'Virement rejeté. Le bulletin redevient payable.');
        if (ok) { setDecision(null); setReference(''); setMotif(''); setProof(null); setProofKey(key => key + 1); }
    };
    const visible = useMemo(() => bulletins.filter(b => `${b.nomPrenom} ${b.matricule} ${b.referenceBulletin}`.toLowerCase().includes(query.toLowerCase())), [bulletins, query]);
    const pageCount = Math.max(1, Math.ceil(visible.length / pageSize));
    const page = Math.min(currentPage, pageCount);
    const displayed = visible.slice((page - 1) * pageSize, page * pageSize);
    const eligibleIds = useMemo(() => new Set(bulletins.filter(eligible).map(b => b.idBulletin)), [bulletins]);
    const selectedEligible = selected.filter(id => eligibleIds.has(id));
    const visibleEligibleIds = visible.filter(eligible).map(b => b.idBulletin);
    const selectedOutsideFilter = selectedEligible.filter(id => !visibleEligibleIds.includes(id)).length;
    const allVisibleSelected = visibleEligibleIds.length > 0 && visibleEligibleIds.every(id => selectedEligible.includes(id));
    const toggleAllVisible = checked => setSelected(previous => {
        const withoutVisible = previous.filter(id => !visibleEligibleIds.includes(id));
        return checked ? [...withoutVisible, ...visibleEligibleIds] : withoutVisible;
    });
    const lot = lots.find(l => l.id === activeLot);
    const total = bulletins.reduce((s, b) => s + Number(b.netAPayer), 0);
    const paid = bulletins.reduce((s, b) => s + Number(b.verse), 0);

    return <div className="max-w-7xl 2xl:max-w-[1600px] mx-auto w-full space-y-4 p-4 sm:p-6">
        <PageHeader title="Règlements des bulletins"
            subtitle={`Paiements déjà effectués et enregistrés pour ${period?.code || `la période ${periodId}`}${period?.startDate && period?.endDate ? ` · ${fmtDate(period.startDate)} au ${fmtDate(period.endDate)}` : ''}.`}
            breadcrumbs={[{ label: 'Centre de paie', to: '/paie' }, { label: period?.code || `Période ${periodId}`, to: `/paie/periodes/${periodId}/sorties` }, { label: 'Règlements' }]}
            actions={<Button variant="outline" size="sm" iconLeft={<ArrowLeft size={14} />} onClick={() => navigate(`/paie/periodes/${periodId}/sorties`)}>Retour à la période</Button>} />
        {error && <Alert type="error" message={error} />}
        {loading ? <SkeletonTable columns={canWrite ? 8 : 7} rows={4} /> : <>
        <div className="grid gap-3 sm:grid-cols-3">
            <StatCard title="Net à payer" value={formatMoneyAr(total)} subtext="Bulletins mensuels validés" color="neutral" />
            <StatCard title="Paiements enregistrés" value={formatMoneyAr(paid)} subtext="Montants déjà payés" color="success" />
            <StatCard title="Reste à payer" value={formatMoneyAr(total - paid)} subtext="Inclut les virements non enregistrés" color="brand" />
        </div>
        <Card shadow="none"><CardHeader><CardTitle size="sm">Bulletins individuels</CardTitle></CardHeader><CardBody className="space-y-3">
            {canWrite && <Alert type="info" message="Pour enregistrer un salaire déjà versé par virement, chèque, espèces ou mobile money, ouvrez « Enregistrer un paiement » sur le bulletin. Les cases de sélection servent uniquement à préparer un fichier de virements bancaires." />}
            <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
                <Input label="Rechercher" value={query} onChange={e => { setQuery(e.target.value); setCurrentPage(1); }} placeholder="Nom, matricule ou référence" containerClassName="w-full sm:max-w-sm" />
                {canWrite && <Button disabled={!selectedEligible.length || busy} onClick={createLot}>Préparer un lot de virements ({selectedEligible.length})</Button>}
            </div>
            {canWrite && <Checkbox label={query ? `Tout sélectionner pour le lot bancaire parmi les ${visibleEligibleIds.length} bulletins filtrés` : `Tout sélectionner pour le lot bancaire parmi les ${visibleEligibleIds.length} bulletins de la période`} checked={allVisibleSelected} disabled={!visibleEligibleIds.length} onChange={e => toggleAllVisible(e.target.checked)} />}
            {canWrite && selectedEligible.length > 0 && <div className="flex flex-wrap items-center gap-2 text-xs text-[var(--color-fg-muted)]"><span>{selectedEligible.length} bulletin(s) sélectionné(s){selectedOutsideFilter > 0 ? `, dont ${selectedOutsideFilter} hors de la recherche` : ''}.</span><Button variant="ghost" size="xs" onClick={() => setSelected([])}>Effacer la sélection</Button></div>}
            <div className="divide-y divide-[var(--color-border-subtle)] border-y border-[var(--color-border-subtle)] sm:hidden">
                {displayed.map(b => <article key={b.idBulletin} className="space-y-3 py-4">
                    <div className="flex items-start justify-between gap-3">
                        <div className="flex items-start gap-2 min-w-0">
                            {canWrite && <input type="checkbox" aria-label={`Ajouter ${b.nomPrenom} au lot de virements`} disabled={!eligible(b)} checked={selectedEligible.includes(b.idBulletin)} onChange={e => setSelected(s => e.target.checked ? [...s, b.idBulletin] : s.filter(id => id !== b.idBulletin))} className="mt-1 accent-[var(--color-brand-600)]" />}
                            <div className="min-w-0"><p className="text-sm font-semibold break-words">{b.nomPrenom}</p><p className="text-xs text-[var(--color-fg-muted)] break-all">{b.matricule} · {b.referenceBulletin}</p></div>
                        </div>
                        <Badge variant={status(b)[1]} size="xs">{status(b)[0]}</Badge>
                    </div>
                    <dl className="grid grid-cols-3 gap-2 text-xs"><div><dt className="text-[var(--color-fg-muted)]">Net dû</dt><dd className="font-semibold tabular-nums">{formatMoneyAr(b.netAPayer)}</dd></div><div><dt className="text-[var(--color-fg-muted)]">Payé</dt><dd className="font-semibold tabular-nums">{formatMoneyAr(b.verse)}</dd></div><div><dt className="text-[var(--color-fg-muted)]">Reste</dt><dd className="font-bold tabular-nums">{formatMoneyAr(b.reste)}</dd></div></dl>
                    {b.inclusDansStc && <p className="text-xs text-[var(--color-info-700)]">Le solde mensuel est intégré au STC. Enregistrez le versement depuis le dossier de sortie pour éviter un double paiement.</p>}
                    <div className="flex items-center justify-between gap-2"><span className="text-xs text-[var(--color-fg-muted)]">{modeLabel[b.modePaiement] || b.modePaiement || '—'}</span><Button variant="outline" size="xs" onClick={() => setActiveBulletin(b.idBulletin)}>{canWrite && Number(b.reste) > 0 && !b.virementEnAttente && !b.inclusDansStc ? 'Enregistrer un paiement' : 'Consulter'}</Button></div>
                </article>)}
            </div>
            <div className="hidden sm:block"><Table bordered={false} density="comfortable" aria-label="Règlements des bulletins mensuels"><Thead><Tr>
                {canWrite && <Th>Lot bancaire</Th>}<Th>Salarié</Th><Th>Mode prévu</Th><Th className="text-right">Net dû</Th><Th className="text-right">Payé</Th><Th className="text-right">Reste</Th><Th>Situation</Th><Th className="text-right">Action</Th>
            </Tr></Thead><Tbody>{displayed.map(b => <Tr key={b.idBulletin}>
                {canWrite && <Td><input type="checkbox" aria-label={`Ajouter ${b.nomPrenom} au lot de virements`} disabled={!eligible(b)} checked={selectedEligible.includes(b.idBulletin)} onChange={e => setSelected(s => e.target.checked ? [...s, b.idBulletin] : s.filter(id => id !== b.idBulletin))} className="accent-[var(--color-brand-600)]" /></Td>}
                <Td><span className="font-semibold">{b.nomPrenom}</span><span className="block text-xs text-[var(--color-fg-muted)]">{b.matricule} · {b.referenceBulletin}</span></Td>
                <Td>{modeLabel[b.modePaiement] || b.modePaiement || '—'}</Td><Td className="text-right tabular-nums">{formatMoneyAr(b.netAPayer)}</Td><Td className="text-right tabular-nums">{formatMoneyAr(b.verse)}</Td><Td className="text-right font-semibold tabular-nums">{formatMoneyAr(b.reste)}</Td>
                <Td><Badge variant={status(b)[1]} size="xs">{status(b)[0]}</Badge></Td>
                <Td className="text-right"><Button variant="outline" size="xs" onClick={() => setActiveBulletin(b.idBulletin)}>{canWrite && Number(b.reste) > 0 && !b.virementEnAttente && !b.inclusDansStc ? 'Enregistrer un paiement' : 'Consulter'}</Button></Td>
            </Tr>)}</Tbody></Table></div>
            {!visible.length && <EmptyState compact title="Aucun bulletin" description={query ? 'Aucun bulletin ne correspond à cette recherche.' : 'Aucun bulletin validé pour cette période.'} />}
            {visible.length > 0 && <Pagination currentPage={page} totalItems={visible.length} pageSize={pageSize} pageSizeOptions={[10, 25, 50, 100]} onPageChange={setCurrentPage} onPageSizeChange={size => { setPageSize(size); setCurrentPage(1); }} />}
            {canWrite && bulletins.some(b => b.modePaiement === 'VIREMENT' && Number(b.reste) > 0 && !b.virementEnAttente && !eligible(b)) && <p className="text-xs text-[var(--color-fg-muted)]">Sans banque ou RIB, un bulletin ne peut pas rejoindre un lot bancaire. Un paiement déjà effectué peut être enregistré individuellement avec son mode réel et son justificatif.</p>}
        </CardBody></Card>
        <Card shadow="none"><CardHeader><CardTitle size="sm">Lots de virements</CardTitle></CardHeader><CardBody className="space-y-0">
            {lots.map(l => <div key={l.id} className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] py-3 last:border-0">
                <div className="text-sm"><span className="font-semibold">Lot n° {l.id}</span><span className="ml-2"><Badge variant={l.statut === 'TRANSMIS' ? 'info' : l.statut === 'ANNULE' ? 'secondary' : 'warning'} size="xs">{l.statut === 'TRANSMIS' ? 'Transmis' : l.statut === 'ANNULE' ? 'Annulé' : 'Préparé'}</Badge></span><p className="text-xs text-[var(--color-fg-muted)] mt-1">{l.lignes} bulletin(s) · Montant initial : {formatMoneyAr(l.montant)}</p></div>
                <Button variant="outline" size="sm" onClick={() => setActiveLot(l.id)}>Ouvrir le lot</Button>
            </div>)}
            {!lots.length && <EmptyState compact title="Aucun lot de virements" description="Sélectionnez les bulletins éligibles pour préparer un fichier de virements." />}
        </CardBody></Card>
        </>}
        <BulletinReglementSlideOver isOpen={activeBulletin !== null} idBulletin={activeBulletin} canWrite={canWrite} onClose={() => setActiveBulletin(null)} onChanged={() => load({ silent: true })} />
        <SlideOver isOpen={activeLot !== null} onClose={() => { linesRequest.current += 1; setActiveLot(null); setDecision(null); setLines([]); }} title={lot ? `Lot de virements n° ${lot.id}` : 'Lot de virements'} subtitle="Préparation du fichier et suivi des paiements" icon={<Wallet size={17} />} width="xl">
            {lot && <div className="space-y-4">
                <div className="flex flex-wrap items-center justify-between gap-2 border border-[var(--color-border-subtle)] p-3 text-sm"><span><strong>{formatMoneyAr(lot.montant)}</strong> initialement · {lot.lignes} ligne(s) · {lot.statut}</span>{lot.statut !== 'ANNULE' && lines.some(line => line.statut === 'EN_ATTENTE') && <Button variant="outline" size="sm" iconLeft={<Download size={14} />} onClick={() => download(lot.id)}>Télécharger le CSV</Button>}</div>
                {lot.statut === 'PREPARE' && <div className="space-y-3"><Alert type="info" message="Le fichier prépare les ordres de virement. Son téléchargement ne marque aucun bulletin comme payé." />{canWrite && <div className="flex flex-wrap gap-2"><Button iconLeft={<Send size={14} />} disabled={busy} onClick={() => run(() => bulletinReglementService.transmettreLot(lot.id), 'Lot marqué transmis. Enregistrez ensuite les virements réellement effectués.')}>Marquer transmis</Button><Button variant="outline" disabled={busy} onClick={() => run(() => bulletinReglementService.annulerLot(lot.id), 'Lot annulé.')}>Annuler le lot</Button></div>}</div>}
                {lot.statut === 'TRANSMIS' && <Alert type="info" message="Enregistrez chaque virement déjà effectué avec sa date, sa référence et son justificatif. Signalez les virements non réalisés comme rejetés." />}
                <section className="border border-[var(--color-border-subtle)]"><h3 className="border-b border-[var(--color-border-subtle)] px-3 py-2 text-sm font-semibold">Lignes du lot</h3>{lines.map(line => <div key={line.id} className="border-b border-[var(--color-border-subtle)] p-3 last:border-0">
                    <div className="flex flex-wrap justify-between gap-2 text-sm"><div><strong>{line.beneficiaire}</strong><p className="text-xs text-[var(--color-fg-muted)]">{line.referenceBulletin} · {line.banque} · {line.rib}</p></div><div className="text-right"><strong className="tabular-nums">{formatMoneyAr(line.montant)}</strong><div><Badge variant={line.statut === 'CONFIRME' ? 'success' : line.statut === 'REJETE' ? 'danger' : 'secondary'} size="xs">{{ CONFIRME: 'Payé', REJETE: 'Rejeté', ANNULE: 'Annulé', EN_ATTENTE: 'À enregistrer' }[line.statut] || line.statut}</Badge></div></div></div>
                    {line.motifRejet && <p className="mt-2 text-xs text-[var(--color-danger-600)]">Rejet : {line.motifRejet}</p>}
                    {canWrite && line.statut === 'EN_ATTENTE' && lot.statut === 'TRANSMIS' && <div className="mt-3 flex gap-2"><Button size="sm" onClick={() => { setDecision({ line, kind: 'CONFIRMER' }); setReference(''); setDate(getTodayDateString()); setProof(null); setProofKey(key => key + 1); }}>Enregistrer comme payé</Button><Button variant="outline" size="sm" onClick={() => { setDecision({ line, kind: 'REJETER' }); setMotif(''); }}>Signaler un rejet</Button></div>}
                </div>)}</section>
                {decision && <form onSubmit={saveDecision} className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-4"><h3 className="text-sm font-semibold">{decision.kind === 'CONFIRMER' ? 'Enregistrer le paiement' : 'Rejeter le virement'} · {decision.line.beneficiaire}</h3>{decision.kind === 'CONFIRMER' ? <><div className="grid gap-3 sm:grid-cols-2"><Input type="date" label="Date d'exécution" required max={getTodayDateString()} value={date} onChange={e => setDate(e.target.value)} /><Input label="Référence bancaire" required value={reference} onChange={e => setReference(e.target.value)} /></div><Input key={proofKey} type="file" label="Justificatif du virement" required accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={e => setProof(e.target.files?.[0] || null)} hint="Avis d'exécution ou relevé bancaire · PDF, PNG ou JPEG · 5 Mo maximum" /></> : <Textarea label="Motif du rejet" rows={2} required value={motif} onChange={e => setMotif(e.target.value)} />}<div className="flex justify-end gap-2"><Button type="button" variant="outline" onClick={() => { setDecision(null); setProof(null); setProofKey(key => key + 1); }}>Retour</Button><Button type="submit" disabled={busy || (decision.kind === 'CONFIRMER' ? !reference.trim() || !proof : !motif.trim())}>Enregistrer</Button></div></form>}
            </div>}
        </SlideOver>
    </div>;
}
