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
    const totalDue = Number(situation?.netAPayerStc ?? dossier?.netAPayerStc ?? 0);
    const totalPaid = Number(situation?.totalRegle ?? dossier?.montantTotalRegle ?? 0);
    const remainingDue = Number(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0);

    return <div className="space-y-4 text-sm">
        {error && <Alert type="error" message={error} />}
        <div className="border border-[var(--color-border-subtle)] border-l-[3px] border-l-[var(--color-brand-600)] bg-[var(--color-surface)] px-3 py-3 sm:px-4">
            <h3 className="flex items-center gap-2 text-sm font-bold text-[var(--color-fg)]"><Wallet size={17} aria-hidden="true" />{dossier?.idStc ? 'Règlement du STC' : 'Règlement disponible après confirmation du STC'}</h3>
            <p className="mt-1 text-xs leading-relaxed text-[var(--color-fg-muted)]">{mayPay ? 'Saisissez le versement ci-dessous avec sa date, sa référence et son justificatif. La part du bulletin mensuel est affectée au même règlement.' : dossier?.idStc ? 'Consultez les versements et les justificatifs enregistrés ci-dessous.' : 'Terminez les contrôles et confirmez le décompte dans « Estimation ». Le formulaire de règlement apparaîtra ensuite ici.'}</p>
        </div>
        {dossier?.idBulletinStc && <section className="flex flex-col gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
                <h3 className="text-base font-bold text-[var(--color-fg)]">Bulletin de paie du départ</h3>
                <p className="mt-1 text-sm text-[var(--color-fg-muted)]">Le bulletin STC final est disponible en PDF.</p>
            </div>
            <Button type="button" variant="primary" disabled={busy}
                onClick={() => run(() => dossierSortieService.telechargerBulletinStc(dossier.idBulletinStc), 'Téléchargement du bulletin impossible.')}>
                <Download size={16} /> Télécharger le bulletin PDF
            </Button>
        </section>}
        <div className="grid gap-2 sm:grid-cols-3" aria-label="États distincts du départ">
            <Card shadow="none" className="border-t-[3px] border-t-[var(--color-brand-500)] p-3">
                <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]"><FileCheck2 size={15} className="text-[var(--color-brand-600)]" aria-hidden="true" />Contrat</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{libelleStatut(dossier?.statutContrat)}</p>
                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{contractEnded ? `Fin effective : ${fmtDate(dossier.dateFinContrat)}` : `Fin : ${fmtDate(dossier?.dateFinContrat) || 'non enregistrée'}`}</p>
            </Card>
            <Card shadow="none" className="border-t-[3px] border-t-[var(--color-brand-700)] p-3">
                <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]"><Wallet size={15} className="text-[var(--color-brand-700)]" aria-hidden="true" />STC financier</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{dossier?.idStc ? libelleStatut(situation?.statutStc || 'VALIDE') : 'Non scellé'}</p>
                {dossier?.idStc && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Reste à payer : {formatMoneyAr(situation?.resteAPayer ?? dossier.soldeRestantAPayer ?? 0)}</p>}
                {dossier?.idBulletinMensuel && Number(dossier.soldeBulletinARegler || 0) > 0 && (
                    <p className="mt-1 text-xs text-[var(--color-fg-muted)]">
                        {situation?.estIntegralementRegle || dossier?.estIntegralementRegle ? 'Bulletin mensuel payé via le STC' : 'Bulletin mensuel inclus dans le STC'} : {formatMoneyAr(dossier.soldeBulletinARegler)}
                    </p>
                )}
            </Card>
            <Card shadow="none" className="border-t-[3px] border-t-[var(--color-border-strong)] p-3">
                <p className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]"><FileCheck2 size={15} className="text-[var(--color-fg-muted)]" aria-hidden="true" />Dossier administratif</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{libelleStatut(dossier?.statutDossier || 'EN_PREPARATION')}</p>
                {closed && Number(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0) > 0 && <p className="mt-1 text-xs font-semibold text-[var(--color-danger-700)]">Clôturé avec dette restante</p>}
            </Card>
        </div>
        {dossier?.derogationReglement && <Alert type="warning" title="Clôture dérogatoire avec solde dû" message={`${dossier.justificationDerogation || ''} · Décision : ${dossier.autoriteDecisionDerogation || ''} · ${dossier.referenceDecisionDerogation || ''} · ${fmtDate(dossier.dateDecisionDerogation)}. Le solde restant reste dû.`} />}
        {dossier?.idStc && <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
            <div className="border-b border-[var(--color-border-subtle)] px-4 py-3">
                <div className="flex items-center gap-2"><Wallet size={18} className="text-[var(--color-brand-600)]" /><h3 className="text-base font-bold text-[var(--color-fg)]">Règlements du STC</h3></div>
                <p className="mt-1 text-xs leading-5 text-[var(--color-fg-muted)]">Suivez le solde, puis enregistrez chaque versement avec sa preuve.</p>
            </div>
            <div className="border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-4 py-3">
                <div className="grid gap-3 sm:grid-cols-[minmax(0,1.2fr)_minmax(0,1fr)_minmax(0,1fr)] sm:items-center">
                    <div className="border-l-[3px] border-l-[var(--color-brand-600)] bg-[var(--color-surface)] px-3 py-2">
                        <span className="text-xs font-semibold text-[var(--color-fg-muted)]">Reste à payer</span>
                        <strong className="font-data mt-0.5 block text-xl font-bold text-[var(--color-fg)]">{formatMoneyAr(remainingDue)}</strong>
                        <span className="text-xs text-[var(--color-fg-muted)]">{remainingDue <= 0 ? 'Solde réglé' : 'À répartir sur un ou plusieurs versements'}</span>
                    </div>
                    <div className="px-1"><span className="text-xs text-[var(--color-fg-muted)]">Montant du STC</span><strong className="font-data block text-base text-[var(--color-fg)]">{formatMoneyAr(totalDue)}</strong></div>
                    <div className="px-1"><span className="text-xs text-[var(--color-fg-muted)]">Déjà versé</span><strong className="font-data block text-base text-[var(--color-fg)]">{formatMoneyAr(totalPaid)}</strong></div>
                </div>
            </div>
            <div className="px-4 py-3">
                <h4 className="mb-2 text-sm font-bold text-[var(--color-fg)]">Historique des versements</h4>
                {(situation?.reglements || dossier.reglements || []).length === 0 ? (
                    <p className="border-l-2 border-l-[var(--color-border-strong)] bg-[var(--color-bg-subtle)] px-3 py-2 text-xs text-[var(--color-fg-muted)]">Aucun versement enregistré. Le premier apparaîtra ici après validation du formulaire.</p>
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
                <MoneyInput label="Part affectée au bulletin mensuel (Ar)" placeholder="Ex. 250 000" min={0} max={Math.min(Number(historicalAllocation.montant), monthlyRemaining)} value={historicalAllocation.partBulletinMensuel} onValueChange={value => setHistoricalAllocation(p => ({ ...p, partBulletinMensuel: value }))} />
                {!historicalAllocation.hasProof && <Input label="Preuve réelle du paiement" type="file" required accept=".pdf,.png,.jpg,.jpeg" onChange={e => setHistoricalAllocation(p => ({ ...p, file: e.target.files?.[0] || null }))} />}
                {historicalAllocation.hasProof && <p className="text-sm text-[var(--color-fg-muted)]">La preuve déjà enregistrée sera réutilisée.</p>}
                <div className="flex gap-2 sm:col-span-2"><Button type="submit" disabled={busy || (!historicalAllocation.file && !historicalAllocation.hasProof)}>Enregistrer le rapprochement</Button><Button type="button" variant="outline" onClick={() => setHistoricalAllocation(null)}>Annuler</Button></div>
            </form></HasPermission>}
            {mayPay && <HasPermission authority="PAIE_WRITE"><form className="border-t border-[var(--color-border-subtle)] px-4 py-4" onSubmit={e => { e.preventDefault(); if (!busyRef.current && (zeroNetAttestation || paymentFile)) setConfirmation('payment'); }}>
                <div className="mb-4 border-l-[3px] border-l-[var(--color-brand-600)] bg-[var(--color-bg-subtle)] px-3 py-2"><h4 className="text-sm font-bold text-[var(--color-fg)]">Nouveau règlement</h4><p className="mt-0.5 text-xs text-[var(--color-fg-muted)]">Renseignez le paiement réellement effectué. Un versement partiel laisse le solde restant ouvert.</p></div>
                <div className="grid gap-x-4 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
                <Input label="Date du règlement" type="date" required min={String(dossier.dateDepart || '').slice(0, 10) || undefined} max={getTodayDateString()} value={payment.dateReglement} onChange={e => setPayment(p => ({ ...p, dateReglement: e.target.value }))} />
                <Select label="Mode du règlement" value={payment.modeReglement} onChange={value => setPayment(p => ({ ...p, modeReglement: value }))} options={[{ value: 'VIREMENT', label: 'Virement' }, { value: 'CHEQUE', label: 'Chèque' }, { value: 'ESPECES', label: 'Espèces' }, { value: 'MOBILE_MONEY', label: 'Mobile money' }]} />
                <Input label="Référence du règlement" aria-label="Référence du règlement" required placeholder="Ex. VIR-2026-001, n° chèque ou reçu de caisse" value={payment.referenceReglement} onChange={e => setPayment(p => ({ ...p, referenceReglement: e.target.value }))} />
                <MoneyInput label="Montant du règlement (Ar)" aria-label="Montant du règlement" placeholder="Ex. 250 000" min={zeroNetAttestation ? 0 : 0.01} max={situation?.resteAPayer ?? undefined} required value={zeroNetAttestation ? 0 : payment.montantRegle} disabled={zeroNetAttestation} onValueChange={value => setPayment(p => ({ ...p, montantRegle: value, partBulletinMensuel: Math.min(Number(value || 0), monthlyRemaining) }))} />
                {monthlyRemaining > 0 && !zeroNetAttestation && <MoneyInput label="Part affectée au bulletin mensuel (Ar)" placeholder="Ex. 250 000" min={0} max={Math.min(Number(payment.montantRegle || 0), monthlyRemaining)} value={payment.partBulletinMensuel} onValueChange={value => setPayment(p => ({ ...p, partBulletinMensuel: value }))} hint={`Solde mensuel inclus dans le STC : ${formatMoneyAr(monthlyRemaining)}. Ce montant ne crée pas un second paiement.`} />}
                {!zeroNetAttestation && (
                    <div className="sm:col-span-2 lg:col-span-1">
                        <Input label="Justificatif réel du versement (PDF, PNG, JPEG ; 5 Mo maximum)" type="file" required accept=".pdf,.png,.jpg,.jpeg" onChange={e => setPaymentFile(e.target.files?.[0] || null)} />
                    </div>
                )}
                <div className="flex justify-end border-t border-[var(--color-border-subtle)] pt-3 sm:col-span-2 lg:col-span-3"><Button type="submit" variant="primary" disabled={busy}>{zeroNetAttestation ? 'Attester le STC à solde nul' : 'Vérifier puis enregistrer'}</Button></div>
                </div>
            </form></HasPermission>}
        </section>}
        <section className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
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
        {mayClose && <HasPermission authority="PAIE_WRITE"><details className="group border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
            <summary className="flex cursor-pointer list-none items-center justify-between gap-2 p-3 text-sm font-bold text-[var(--color-fg)] marker:hidden hover:bg-[var(--color-bg-subtle)] focus-visible:outline-2 focus-visible:outline-offset-[-2px] focus-visible:outline-[var(--color-brand-600)]">
                <span>Clôture administrative <span className="ml-2 font-normal text-[var(--color-fg-muted)]">Après les règlements et documents</span></span><span aria-hidden="true" className="text-[var(--color-brand-700)] group-open:rotate-180">⌄</span>
            </summary>
            <div className="space-y-3 border-t border-[var(--color-border-subtle)] p-3">
            <p className="text-xs text-[var(--color-fg-muted)]">La fin du contrat et les contrôles bloquants seront revérifiés par le serveur. La clôture ne signifie pas forcément que la dette est éteinte.</p>
            <Input label="Motif de clôture" placeholder="Ex. Documents remis et règlement vérifié" value={closure.motifCloture} onChange={e => setClosure(p => ({ ...p, motifCloture: e.target.value }))} />
            {!situation?.estIntegralementRegle && <div className="space-y-3 border border-[var(--color-danger-200)] bg-[var(--color-danger-50)] p-3">
                <p className="text-xs font-semibold text-[var(--color-danger-800)]">Solde restant dû : {formatMoneyAr(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0)}</p>
                <label className="flex min-h-10 items-center gap-2 text-sm text-[var(--color-fg)]"><input type="checkbox" checked={closure.derogationReglementIncomplet} onChange={e => setClosure(p => ({ ...p, derogationReglementIncomplet: e.target.checked }))} />Décision dérogatoire documentée pour clôturer avec ce solde</label>
                {closure.derogationReglementIncomplet && <div className="grid gap-2 sm:grid-cols-2">
                    {[
                        ['justificationDerogation', 'Justification de la dérogation', 'Ex. Solde restant à recouvrer selon échéancier signé'],
                        ['autoriteDecisionDerogation', 'Autorité décisionnaire', 'Ex. Direction RH'],
                        ['referenceDecisionDerogation', 'Référence de la décision', 'Ex. DEC-RH-2026-001'],
                    ].map(([field, label, placeholder]) => <Input key={field} required label={label} aria-label={label} placeholder={placeholder} value={closure[field]} onChange={e => setClosure(p => ({ ...p, [field]: e.target.value }))} />)}
                    <Input required label="Date de décision" aria-label="Date de décision" type="date" value={closure.dateDecisionDerogation} onChange={e => setClosure(p => ({ ...p, dateDecisionDerogation: e.target.value }))} />
                </div>}
            </div>}
            <div className="flex justify-end"><Button type="button" variant="primary" disabled={busy || (!situation?.estIntegralementRegle && !closure.derogationReglementIncomplet)} onClick={() => setConfirmation('closure')}>Vérifier puis clôturer</Button></div>
            </div>
        </details></HasPermission>}
        {cancellation && <ConfirmDialog isOpen onClose={() => setCancellation(null)}
            onConfirm={() => { if (cancellation.motifAnnulation.trim()) cancelPayment(); }}
            title="Annuler le règlement" message="L’écriture restera visible dans le journal avec son motif d’annulation."
            confirmLabel="Confirmer l’annulation" variant="danger" loading={busy}>
            <Input label="Motif de l’annulation" aria-label="Motif de l’annulation" required placeholder="Ex. Référence erronée ou versement saisi en double"
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
