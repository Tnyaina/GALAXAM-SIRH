import { useEffect, useRef, useState } from 'react';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import HasPermission from '@/features/auth/components/HasPermission';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import Alert from '@/components/ui/feedback/Alert';
import { Card } from '@/components/ui/data-display/Card';
import { Download, FileCheck2, Wallet, Eye } from 'lucide-react';
import DocumentOfficielModal from '@/features/employees/components/carriere/modals/workflow/DocumentOfficielModal';

const documentOptions = [
    ['decompte', 'Décompte STC scellé'],
    ['recuProjet', 'Reçu STC — projet'],
    ['certificatDocx', 'Certificat DOCX'],
];

const labelsStatut = {
    ROMPU: 'Terminé', CLOTURE: 'Clôturé', TERMINE: 'Terminé',
    VALIDE: 'Validé', REGLE: 'Réglé', PRET_A_VALIDER: 'Prêt à clôturer',
    EN_PREPARATION: 'En préparation', ANNULE: 'Annulé',
};
const libelleStatut = (code) => labelsStatut[code] || code || 'À vérifier';
const libelleReference = (reference) => {
    const value = String(reference || '').trim();
    return !value || ['non', 'n/a', 'na', '-'].includes(value.toLowerCase())
        ? 'Non renseignée'
        : value;
};

export default function DossierSortieSuiviPanel({ dossier, onDossierChange }) {
    const id = dossier?.idDossierSortie;
    const [situation, setSituation] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const pendingPayment = useRef(null);
    const busyRef = useRef(false);
    const [confirmation, setConfirmation] = useState(null);
    const [cancellation, setCancellation] = useState(null);
    const [showCertificatModal, setShowCertificatModal] = useState(false);
    const [paymentFile, setPaymentFile] = useState(null);
    const [historicalAllocation, setHistoricalAllocation] = useState(null);
    const [payment, setPayment] = useState({ dateReglement: getTodayDateString(), referenceReglement: '', montantRegle: '', partBulletinMensuel: '', modeReglement: 'VIREMENT' });
    const [closure, setClosure] = useState({ motifCloture: '', derogationReglementIncomplet: false, justificationDerogation: '', autoriteDecisionDerogation: '', referenceDecisionDerogation: '', dateDecisionDerogation: '', confirmationFormalites: true });

    useEffect(() => {
        if (!id || !dossier.idStc) { setSituation(null); return; }
        let active = true;
        dossierSortieService.obtenirSituationReglements(id)
            .then(data => { if (active) setSituation(data); })
            .catch(err => { if (active) setError(getErrorMessage(err, 'Situation des règlements indisponible.')); });
        return () => { active = false; };
    }, [id, dossier?.idStc]);

    const run = async (action, fallback) => {
        if (busyRef.current) return;
        busyRef.current = true;
        setBusy(true);
        setError('');
        try { await action(); }
        catch (err) { setError(getErrorMessage(err, fallback)); }
        finally { busyRef.current = false; setBusy(false); }
    };
    const download = (kind, idReglement) => run(
        () => dossierSortieService.telechargerDocument(id, kind, idReglement),
        'Téléchargement impossible.'
    );
    const registerPayment = () => run(async () => {
        const fingerprint = JSON.stringify({ ...payment, fileName: paymentFile?.name, fileSize: paymentFile?.size, zeroNetAttestation });
        const storageKey = `galaxam:stc:reglement:${id}`;
        if (pendingPayment.current?.fingerprint !== fingerprint) {
            let stored = null;
            try { stored = JSON.parse(sessionStorage.getItem(storageKey)); } catch { /* stockage indisponible */ }
            pendingPayment.current = stored?.fingerprint === fingerprint
                ? stored : { fingerprint, key: crypto.randomUUID() };
            try { sessionStorage.setItem(storageKey, JSON.stringify(pendingPayment.current)); } catch { /* stockage indisponible */ }
        }
        const data = await dossierSortieService.enregistrerReglement(id, {
            ...payment,
            montantRegle: zeroNetAttestation ? 0 : payment.montantRegle,
        }, pendingPayment.current.key, ...(paymentFile ? [paymentFile] : []));
        pendingPayment.current = null;
        try { sessionStorage.removeItem(storageKey); } catch { /* stockage indisponible */ }
        setSituation(data);
        setPayment(prev => ({ ...prev, referenceReglement: '', montantRegle: '', partBulletinMensuel: '' }));
        setPaymentFile(null);
        try { onDossierChange?.(await dossierSortieService.obtenirDossier(id)); }
        catch { setError('Règlement enregistré. Actualisation du dossier indisponible : rechargez la page pour voir son état.'); }
    }, 'Règlement refusé.');
    const closeDossier = () => run(async () => {
        const updated = await dossierSortieService.cloturerDossier(id, closure);
        onDossierChange?.(updated);
    }, 'Clôture refusée.');
    const cancelPayment = () => run(async () => {
        const data = await dossierSortieService.annulerReglement(id, cancellation.idReglement, cancellation.motifAnnulation.trim());
        setSituation(data);
        setCancellation(null);
        onDossierChange?.(await dossierSortieService.obtenirDossier(id));
    }, 'Annulation refusée.');
    const reconcileHistorical = () => run(async () => {
        const data = await dossierSortieService.affecterReglementHistorique(id, historicalAllocation.idReglement,
            historicalAllocation.partBulletinMensuel, historicalAllocation.file);
        setSituation(data);
        setHistoricalAllocation(null);
        onDossierChange?.(await dossierSortieService.obtenirDossier(id));
    }, 'Rapprochement refusé.');

    const closed = dossier?.statutDossier === 'CLOTURE';
    const financialReady = situation && ['VALIDE', 'REGLE'].includes(situation.statutStc || 'VALIDE');
    const netScelle = situation?.netAPayerStc ?? dossier?.netAPayerStc;
    const zeroNetAttestation = netScelle != null && Number(netScelle) === 0
        && !situation?.estIntegralementRegle;
    const mayPay = financialReady && (!closed || dossier?.derogationReglement)
        && (Number(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0) > 0 || zeroNetAttestation);
    const mayClose = financialReady && !closed && dossier?.statutDossier !== 'ANNULE' && dossier?.pretAValider;
    const contractEnded = ['ROMPU', 'CLOTURE', 'TERMINE'].includes(dossier?.statutContrat)
        && dossier?.dateFinContrat && String(dossier.dateFinContrat).slice(0, 10) <= getTodayDateString();
    const showCertificate = Boolean(contractEnded);
    const documents = documentOptions.filter(([kind]) => {
        if (kind === 'certificatDocx') return showCertificate;
        return Boolean(dossier?.idStc);
    });
    const monthlyDue = Math.max(0, Number(dossier?.soldeBulletinARegler || 0));
    const monthlyAllocated = (situation?.reglements || []).filter(row => !row.estAnnule)
        .reduce((total, row) => total + Number(row.partBulletinMensuel || 0), 0);
    const monthlyRemaining = Math.max(0, monthlyDue - monthlyAllocated);

    return <div className="space-y-5 text-sm">
        {error && <Alert type="error" message={error} />}
        <div className="grid gap-3 sm:grid-cols-3" aria-label="États distincts du départ">
            <Card shadow="none" className="p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Contrat</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{libelleStatut(dossier?.statutContrat)}</p>
                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{contractEnded ? `Fin effective : ${fmtDate(dossier.dateFinContrat)}` : `Fin : ${fmtDate(dossier?.dateFinContrat) || 'non enregistrée'}`}</p>
            </Card>
            <Card shadow="none" className="p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">STC financier</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{dossier?.idStc ? libelleStatut(situation?.statutStc || 'VALIDE') : 'Non scellé'}</p>
                {dossier?.idStc && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Reste à payer : {formatMoneyAr(situation?.resteAPayer ?? dossier.soldeRestantAPayer ?? 0)}</p>}
                {dossier?.idBulletinMensuel && Number(dossier.soldeBulletinARegler || 0) > 0 && (
                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                        {situation?.estIntegralementRegle || dossier?.estIntegralementRegle ? 'Bulletin mensuel payé via le STC' : 'Bulletin mensuel inclus dans le STC'} : {formatMoneyAr(dossier.soldeBulletinARegler)}
                    </p>
                )}
            </Card>
            <Card shadow="none" className="p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Dossier administratif</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{libelleStatut(dossier?.statutDossier || 'EN_PREPARATION')}</p>
                {closed && Number(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0) > 0 && <p className="mt-1 text-xs font-semibold text-[var(--color-warning-700)]">Clôturé avec dette restante</p>}
            </Card>
        </div>
        {dossier?.derogationReglement && <Alert type="warning" title="Clôture dérogatoire avec solde dû" message={`${dossier.justificationDerogation || ''} · Décision : ${dossier.autoriteDecisionDerogation || ''} · ${dossier.referenceDecisionDerogation || ''} · ${fmtDate(dossier.dateDecisionDerogation)}. Le solde restant reste dû.`} />}
        {dossier?.idStc && <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
            <div className="border-b border-[var(--color-border-subtle)] p-4">
                <div className="flex items-center gap-2"><Wallet size={18} className="text-[var(--color-brand-600)]" /><h3 className="text-base font-bold text-[var(--color-fg)]">Règlements du STC</h3></div>
                <p className="mt-1.5 text-sm leading-5 text-[var(--color-fg-muted)]">Les paiements enregistrés sont listés ici. Télécharger un justificatif n’enregistre pas un nouveau paiement.</p>
            </div>
            <div className="grid gap-px bg-[var(--color-border-subtle)] sm:grid-cols-3">
                {[
                    { label: 'Total dû', amount: situation?.netAPayerStc ?? dossier.netAPayerStc ?? 0 },
                    { label: 'Total versé', amount: situation?.totalRegle ?? dossier.montantTotalRegle ?? 0 },
                    { label: 'Reste à payer', amount: situation?.resteAPayer ?? dossier.soldeRestantAPayer ?? 0, remaining: true },
                ].map(({ label, amount, remaining }) => {
                    const settled = remaining && Number(amount) <= 0;
                    return <div key={label} className={`flex min-h-20 flex-col items-start justify-center gap-1 px-4 py-3 ${settled ? 'bg-[var(--color-success-50)]' : 'bg-[var(--color-surface)]'}`}>
                        <div className="flex w-full items-center justify-between gap-2">
                            <span className="text-sm font-semibold text-[var(--color-fg-muted)]">{label}</span>
                            {settled && <span className="border border-[var(--color-success-200)] px-2 py-1 text-[11px] font-bold uppercase tracking-wide text-[var(--color-success-700)]">Soldé</span>}
                        </div>
                        <span className={`font-data text-xl font-bold ${settled ? 'text-[var(--color-success-700)]' : 'text-[var(--color-fg)]'}`}>{formatMoneyAr(amount)}</span>
                    </div>;
                })}
            </div>
            <div className="p-4">
                <h4 className="mb-3 text-sm font-bold text-[var(--color-fg)]">Historique des versements</h4>
                {(situation?.reglements || dossier.reglements || []).length === 0 ? (
                    <p className="border border-dashed border-[var(--color-border-subtle)] px-3 py-4 text-sm text-[var(--color-fg-muted)]">Aucun versement enregistré pour le moment.</p>
                ) : <div className="divide-y divide-[var(--color-border-subtle)] border-y border-[var(--color-border-subtle)]">
                    {(situation?.reglements || dossier.reglements || []).map(row => <div key={row.idReglement} className="grid gap-3 py-4 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
                        <div className="grid gap-2 sm:grid-cols-[140px_minmax(0,1fr)_minmax(130px,auto)] sm:items-center">
                            <span className="text-sm font-medium text-[var(--color-fg)]">{fmtDate(row.dateReglement)}</span>
                            <span className="text-sm leading-5 text-[var(--color-fg)]">
                                <span className="font-semibold">{row.modeReglement || 'Mode non renseigné'}</span>
                                <span className="block text-[var(--color-fg-muted)]">Référence : {libelleReference(row.referenceReglement)}</span>
                                {Number(row.partBulletinMensuel || 0) > 0 && <span className="block text-[var(--color-fg-muted)]">Affecté au bulletin mensuel : {formatMoneyAr(row.partBulletinMensuel)}</span>}
                                {row.estAnnule && <strong className="ml-2 text-xs font-bold text-[var(--color-danger-700)]">Annulé</strong>}
                            </span>
                            <strong className="font-data text-lg font-bold text-[var(--color-fg)] sm:text-right">{formatMoneyAr(row.montantRegle)}</strong>
                        </div>
                        <div className="flex flex-wrap items-center gap-2 sm:justify-end">
                            <Button type="button" variant="outline" size="xs" disabled={busy} onClick={() => download('quittance', row.idReglement)}>Quittance PDF</Button>
                            {row.nomJustificatif && <Button type="button" variant="outline" size="xs" disabled={busy} onClick={() => run(() => dossierSortieService.telechargerPreuveReglement(id, row.idReglement), 'Preuve indisponible.')}>Preuve</Button>}
                            {!row.estAnnule && (!row.nomJustificatif || (monthlyRemaining > 0 && Number(row.partBulletinMensuel || 0) === 0)) && <HasPermission authority="PAIE_WRITE"><Button type="button" variant="outline" size="xs" disabled={busy} onClick={() => setHistoricalAllocation({ idReglement: row.idReglement, montant: row.montantRegle, partBulletinMensuel: Math.min(Number(row.montantRegle), monthlyRemaining), hasProof: Boolean(row.nomJustificatif), file: null })}>Rapprocher</Button></HasPermission>}
                            {!row.estAnnule && financialReady && !closed && <HasPermission authority="PAIE_WRITE"><Button type="button" variant="ghost" size="xs" disabled={busy} onClick={() => setCancellation({ idReglement: row.idReglement, motifAnnulation: '' })}>Annuler ce règlement</Button></HasPermission>}
                        </div>
                    </div>)}
                </div>}
            </div>
            {historicalAllocation && <HasPermission authority="PAIE_WRITE"><form className="grid gap-3 border-t border-[var(--color-border-subtle)] p-4 sm:grid-cols-2" onSubmit={e => { e.preventDefault(); if (historicalAllocation.file || historicalAllocation.hasProof) reconcileHistorical(); }}>
                <h4 className="text-sm font-bold sm:col-span-2">Rapprocher un versement déjà enregistré</h4>
                <MoneyInput label="Part affectée au bulletin mensuel (Ar)" min={0} max={Math.min(Number(historicalAllocation.montant), monthlyRemaining)} value={historicalAllocation.partBulletinMensuel} onValueChange={value => setHistoricalAllocation(p => ({ ...p, partBulletinMensuel: value }))} />
                {!historicalAllocation.hasProof && <Input label="Preuve réelle du paiement" type="file" required accept=".pdf,.png,.jpg,.jpeg" onChange={e => setHistoricalAllocation(p => ({ ...p, file: e.target.files?.[0] || null }))} />}
                {historicalAllocation.hasProof && <p className="text-sm text-[var(--color-fg-muted)]">La preuve déjà enregistrée sera réutilisée.</p>}
                <div className="flex gap-2 sm:col-span-2"><Button type="submit" disabled={busy || (!historicalAllocation.file && !historicalAllocation.hasProof)}>Enregistrer le rapprochement</Button><Button type="button" variant="outline" onClick={() => setHistoricalAllocation(null)}>Annuler</Button></div>
            </form></HasPermission>}
            {mayPay && <HasPermission authority="PAIE_WRITE"><form className="grid gap-3 border-t border-[var(--color-border-subtle)] p-4 sm:grid-cols-2" onSubmit={e => { e.preventDefault(); if (!busyRef.current && (zeroNetAttestation || paymentFile)) setConfirmation('payment'); }}>
                <h4 className="text-sm font-bold text-[var(--color-fg)] sm:col-span-2">Nouveau règlement</h4>
                <Input label="Date du règlement" type="date" required min={String(dossier.dateDepart || '').slice(0, 10) || undefined} max={getTodayDateString()} value={payment.dateReglement} onChange={e => setPayment(p => ({ ...p, dateReglement: e.target.value }))} />
                <Select label="Mode du règlement" value={payment.modeReglement} onChange={value => setPayment(p => ({ ...p, modeReglement: value }))} options={[{ value: 'VIREMENT', label: 'Virement' }, { value: 'CHEQUE', label: 'Chèque' }, { value: 'ESPECES', label: 'Espèces' }, { value: 'MOBILE_MONEY', label: 'Mobile money' }]} />
                <Input label="Référence du règlement" aria-label="Référence du règlement" required placeholder="Ex. référence bancaire" value={payment.referenceReglement} onChange={e => setPayment(p => ({ ...p, referenceReglement: e.target.value }))} />
                <MoneyInput label="Montant du règlement (Ar)" aria-label="Montant du règlement" min={zeroNetAttestation ? 0 : 0.01} max={situation?.resteAPayer ?? undefined} required value={zeroNetAttestation ? 0 : payment.montantRegle} disabled={zeroNetAttestation} onValueChange={value => setPayment(p => ({ ...p, montantRegle: value, partBulletinMensuel: Math.min(Number(value || 0), monthlyRemaining) }))} />
                {monthlyRemaining > 0 && !zeroNetAttestation && <MoneyInput label="Part affectée au bulletin mensuel (Ar)" min={0} max={Math.min(Number(payment.montantRegle || 0), monthlyRemaining)} value={payment.partBulletinMensuel} onValueChange={value => setPayment(p => ({ ...p, partBulletinMensuel: value }))} hint={`Solde mensuel inclus dans le STC : ${formatMoneyAr(monthlyRemaining)}. Ce montant ne crée pas un second paiement.`} />}
                {!zeroNetAttestation && (
                    <div className="sm:col-span-2">
                        <Input label="Justificatif réel du versement (PDF, PNG, JPEG ; 5 Mo maximum)" type="file" required accept=".pdf,.png,.jpg,.jpeg" onChange={e => setPaymentFile(e.target.files?.[0] || null)} />
                    </div>
                )}
                <div className="flex justify-end sm:col-span-2"><Button type="submit" variant="primary" disabled={busy}>{zeroNetAttestation ? 'Attester le STC à solde nul' : 'Vérifier puis enregistrer'}</Button></div>
            </form></HasPermission>}
        </section>}
        <section className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2"><FileCheck2 size={18} className="text-[var(--color-brand-600)]" /><h3 className="text-base font-bold text-[var(--color-fg)]">Documents disponibles</h3></div>
                {showCertificate && dossier?.idContrat && (
                    <Button
                        type="button"
                        variant="ghost"
                        size="xs"
                        onClick={() => setShowCertificatModal(true)}
                        iconLeft={<Eye size={13} />}
                    >
                        Aperçu officiel (Word/Écran)
                    </Button>
                )}
            </div>
            <p className="text-xs text-[var(--color-fg-muted)]">Le reçu STC reste un projet. Télécharger un document ne constate ni remise ni signature.</p>
            {documents.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Les documents apparaîtront selon l'état du contrat et du STC.</p>}
            <div className="grid gap-2 sm:grid-cols-3">{documents.map(([kind, label]) => <Button key={kind} type="button" variant="outline" disabled={busy} onClick={() => download(kind)} className="min-h-11 justify-between text-left"><span>{label}</span><Download size={15} className="shrink-0" /></Button>)}</div>
        </section>
        {mayClose && <HasPermission authority="PAIE_WRITE"><section className="space-y-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
            <h3 className="text-base font-bold text-[var(--color-fg)]">Clôture administrative</h3>
            <p className="text-xs text-[var(--color-fg-muted)]">La fin du contrat et les contrôles bloquants seront revérifiés par le serveur. La clôture ne signifie pas forcément que la dette est éteinte.</p>
            <Input label="Motif de clôture" placeholder="Précisez le motif de clôture" value={closure.motifCloture} onChange={e => setClosure(p => ({ ...p, motifCloture: e.target.value }))} />
            {!situation?.estIntegralementRegle && <div className="space-y-3 border border-[var(--color-warning-200)] bg-[var(--color-warning-50)] p-3">
                <p className="text-xs font-semibold text-[var(--color-warning-900)]">Solde restant dû : {formatMoneyAr(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0)}</p>
                <label className="flex min-h-10 items-center gap-2 text-sm text-[var(--color-fg)]"><input type="checkbox" checked={closure.derogationReglementIncomplet} onChange={e => setClosure(p => ({ ...p, derogationReglementIncomplet: e.target.checked }))} />Décision dérogatoire documentée pour clôturer avec ce solde</label>
                {closure.derogationReglementIncomplet && <div className="grid gap-2 sm:grid-cols-2">
                    {[
                        ['justificationDerogation', 'Justification de la dérogation'],
                        ['autoriteDecisionDerogation', 'Autorité décisionnaire'],
                        ['referenceDecisionDerogation', 'Référence de la décision'],
                    ].map(([field, label]) => <Input key={field} required label={label} aria-label={label} placeholder={label} value={closure[field]} onChange={e => setClosure(p => ({ ...p, [field]: e.target.value }))} />)}
                    <Input required label="Date de décision" aria-label="Date de décision" type="date" value={closure.dateDecisionDerogation} onChange={e => setClosure(p => ({ ...p, dateDecisionDerogation: e.target.value }))} />
                </div>}
            </div>}
            <div className="flex justify-end"><Button type="button" variant="primary" disabled={busy || (!situation?.estIntegralementRegle && !closure.derogationReglementIncomplet)} onClick={() => setConfirmation('closure')}>Vérifier puis clôturer</Button></div>
        </section></HasPermission>}
        {cancellation && <ConfirmDialog isOpen onClose={() => setCancellation(null)}
            onConfirm={() => { if (cancellation.motifAnnulation.trim()) cancelPayment(); }}
            title="Annuler le règlement" message="L’écriture restera visible dans le journal avec son motif d’annulation."
            confirmLabel="Confirmer l’annulation" variant="danger" loading={busy}>
            <Input label="Motif de l’annulation" aria-label="Motif de l’annulation" required placeholder="Motif de l’annulation"
                value={cancellation.motifAnnulation} onChange={e => setCancellation(prev => ({ ...prev, motifAnnulation: e.target.value }))} />
            {!cancellation.motifAnnulation.trim() && <p className="text-xs text-[var(--color-danger-700)]">Un motif est obligatoire.</p>}
        </ConfirmDialog>}
        <ConfirmDialog isOpen={Boolean(confirmation)} onClose={() => setConfirmation(null)}
            onConfirm={() => { const action = confirmation; setConfirmation(null); if (action === 'payment') registerPayment(); else closeDossier(); }}
            title={confirmation === 'payment' ? 'Confirmer le règlement' : 'Confirmer la clôture administrative'}
            message={confirmation === 'payment' ? `Enregistrer le règlement ${payment.referenceReglement} de ${formatMoneyAr(zeroNetAttestation ? 0 : payment.montantRegle)} ?` : 'Cette clôture administrative sera définitive. Le solde restant reste dû en cas de dérogation.'}
            confirmLabel={confirmation === 'payment' ? 'Enregistrer le règlement' : 'Clôturer le dossier'}
            variant="warning" loading={busy} />
        {showCertificatModal && dossier?.idContrat && (
            <DocumentOfficielModal
                isOpen={showCertificatModal}
                onClose={() => setShowCertificatModal(false)}
                typeDocument="CERTIFICAT"
                contrat={{ idContrat: dossier?.idContrat, idEmploye: dossier?.idEmploye }}
                employe={{ idEmploye: dossier?.idEmploye, matricule: dossier?.matricule, nom: dossier?.nom, prenom: dossier?.prenom }}
            />
        )}
    </div>;
}
