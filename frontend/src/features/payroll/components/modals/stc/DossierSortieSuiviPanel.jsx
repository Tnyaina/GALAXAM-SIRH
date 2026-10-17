import { useEffect, useRef, useState } from 'react';
import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import HasPermission from '@/features/auth/components/HasPermission';
import { getErrorMessage } from '@/api/apiClient';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { Download, FileCheck2, Wallet } from 'lucide-react';

const documentOptions = [
    ['decompte', 'Décompte STC scellé'],
    ['recuProjet', 'Reçu STC — projet'],
    ['certificatPdf', 'Certificat PDF'],
    ['certificatDocx', 'Certificat DOCX'],
    ['bordereau', 'Bordereau de remise'],
];

export default function DossierSortieSuiviPanel({ dossier, onDossierChange }) {
    const id = dossier?.idDossierSortie;
    const [situation, setSituation] = useState(null);
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const pendingPayment = useRef(null);
    const busyRef = useRef(false);
    const [confirmation, setConfirmation] = useState(null);
    const [cancellation, setCancellation] = useState(null);
    const [payment, setPayment] = useState({ dateReglement: getTodayDateString(), referenceReglement: '', montantRegle: '', modeReglement: 'VIREMENT' });
    const [closure, setClosure] = useState({ motifCloture: '', derogationReglementIncomplet: false, justificationDerogation: '', autoriteDecisionDerogation: '', referenceDecisionDerogation: '', dateDecisionDerogation: '', confirmationFormalites: false });

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
        const fingerprint = JSON.stringify({ ...payment, zeroNetAttestation });
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
        }, pendingPayment.current.key);
        pendingPayment.current = null;
        try { sessionStorage.removeItem(storageKey); } catch { /* stockage indisponible */ }
        setSituation(data);
        setPayment(prev => ({ ...prev, referenceReglement: '', montantRegle: '' }));
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
        if (kind.startsWith('certificat')) return showCertificate;
        if (kind === 'bordereau') return Boolean(id);
        return Boolean(dossier?.idStc);
    });

    return <div className="space-y-5 text-sm">
        {error && <div role="alert" className="border border-[var(--color-danger-500)] bg-[var(--color-danger-50)] p-3 text-[var(--color-danger-700)]">{error}</div>}
        <div className="grid gap-3 sm:grid-cols-3" aria-label="États distincts du départ">
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Contrat</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{dossier?.statutContrat || 'À vérifier'}</p>
                <p className="mt-1 text-xs text-[var(--color-fg-muted)]">{contractEnded ? `Fin effective : ${fmtDate(dossier.dateFinContrat)}` : `Fin : ${fmtDate(dossier?.dateFinContrat) || 'non enregistrée'}`}</p>
            </div>
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">STC financier</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{dossier?.idStc ? situation?.statutStc || 'Scellé' : 'Non scellé'}</p>
                {dossier?.idStc && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Reste à payer : {formatMoneyAr(situation?.resteAPayer ?? dossier.soldeRestantAPayer ?? 0)}</p>}
            </div>
            <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
                <p className="text-xs font-semibold uppercase tracking-wide text-[var(--color-fg-muted)]">Dossier administratif</p>
                <p className="mt-2 text-base font-bold text-[var(--color-fg)]">{dossier?.statutDossier || 'En préparation'}</p>
                {closed && Number(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0) > 0 && <p className="mt-1 text-xs font-semibold text-[var(--color-warning-700)]">Clôturé avec dette restante</p>}
            </div>
        </div>
        {dossier?.derogationReglement && <div className="border border-[var(--color-warning-500)] bg-[var(--color-warning-50)] p-3">
            <strong>Clôture dérogatoire avec solde dû</strong>
            <p>{dossier.justificationDerogation}</p>
            <p>Décision : {dossier.autoriteDecisionDerogation} · {dossier.referenceDecisionDerogation} · {fmtDate(dossier.dateDecisionDerogation)}</p>
            <p>Le solde restant reste dû.</p>
        </div>}
        {dossier?.idStc && <section className="space-y-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
            <div className="flex items-center gap-2"><Wallet size={18} className="text-[var(--color-brand-600)]" /><h3 className="text-base font-bold text-[var(--color-fg)]">Règlements du STC</h3></div>
            <div className="grid gap-2 sm:grid-cols-3">
                {[['Net scellé', situation?.netAPayerStc ?? dossier.netAPayerStc ?? 0], ['Versé', situation?.totalRegle ?? dossier.montantTotalRegle ?? 0], ['Reste à payer', situation?.resteAPayer ?? dossier.soldeRestantAPayer ?? 0]].map(([label, amount]) =>
                    <div key={label} className="border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3">
                        <p className="text-xs text-[var(--color-fg-muted)]">{label}</p><p className="mt-1 font-data text-lg font-bold text-[var(--color-fg)]">{formatMoneyAr(amount)}</p>
                    </div>)}
            </div>
            <p className="text-xs text-[var(--color-fg-muted)]">Chaque versement apparaît dans le journal. Un document téléchargé ne constate aucun nouveau paiement.</p>
            {(situation?.reglements || dossier.reglements || []).map(row => <div key={row.idReglement} className="flex flex-wrap items-center justify-between gap-2 border-t border-[var(--color-border-subtle)] py-3">
                <span>{fmtDate(row.dateReglement)} · {row.referenceReglement} · {formatMoneyAr(row.montantRegle)} · {row.modeReglement} {row.estAnnule && <strong className="text-[var(--color-danger-700)]">ANNULÉ</strong>}</span>
                <div className="flex gap-3"><button type="button" disabled={busy} className="min-h-10 border border-[var(--color-border-subtle)] px-3 font-semibold text-[var(--color-brand-700)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-50" onClick={() => download('quittance', row.idReglement)}>Justificatif PDF</button>
                    {!row.estAnnule && financialReady && !closed && <HasPermission authority="PAIE_WRITE"><button type="button" disabled={busy} className="underline text-[var(--color-danger-700)]" onClick={() => setCancellation({ idReglement: row.idReglement, motifAnnulation: '' })}>Annuler ce règlement</button></HasPermission>}
                </div>
            </div>)}
            {mayPay && <HasPermission authority="PAIE_WRITE"><form className="grid gap-3 border-t border-[var(--color-border-subtle)] pt-4 sm:grid-cols-2" onSubmit={e => { e.preventDefault(); if (!busyRef.current) setConfirmation('payment'); }}>
                <h4 className="text-sm font-bold text-[var(--color-fg)] sm:col-span-2">Nouveau règlement</h4>
                <label className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">Date du règlement
                    <input aria-label="Date du règlement" type="date" required min={String(dossier.dateDepart || '').slice(0, 10) || undefined} max={getTodayDateString()} value={payment.dateReglement} onChange={e => setPayment(p => ({ ...p, dateReglement: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal" />
                </label>
                <label className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">Mode
                    <select aria-label="Mode du règlement" value={payment.modeReglement} onChange={e => setPayment(p => ({ ...p, modeReglement: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal"><option value="VIREMENT">Virement</option><option value="CHEQUE">Chèque</option><option value="ESPECES">Espèces</option></select>
                </label>
                <label className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">Référence du paiement
                    <input aria-label="Référence du règlement" required placeholder="Ex. référence bancaire" value={payment.referenceReglement} onChange={e => setPayment(p => ({ ...p, referenceReglement: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal" />
                </label>
                <label className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">Montant en ariary
                    <input aria-label="Montant du règlement" type="number" step="0.01" min={zeroNetAttestation ? '0' : '0.01'} max={situation?.resteAPayer ?? undefined} required value={zeroNetAttestation ? '0' : payment.montantRegle} disabled={zeroNetAttestation} onChange={e => setPayment(p => ({ ...p, montantRegle: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal disabled:opacity-60" />
                </label>
                <div className="flex justify-end sm:col-span-2"><button disabled={busy} className="min-h-10 bg-[var(--color-brand-700)] px-4 font-semibold text-white hover:bg-[var(--color-brand-800)] disabled:opacity-50">{zeroNetAttestation ? 'Attester le STC à solde nul' : 'Vérifier puis enregistrer'}</button></div>
            </form></HasPermission>}
        </section>}
        <section className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
            <div className="flex items-center gap-2"><FileCheck2 size={18} className="text-[var(--color-brand-600)]" /><h3 className="text-base font-bold text-[var(--color-fg)]">Documents disponibles</h3></div>
            <p className="text-xs text-[var(--color-fg-muted)]">Le reçu STC reste un projet. Télécharger un document ne constate ni remise ni signature.</p>
            {documents.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Les documents apparaîtront selon l'état du contrat et du STC.</p>}
            <div className="grid gap-2 sm:grid-cols-2">{documents.map(([kind, label]) => <button key={kind} type="button" disabled={busy} onClick={() => download(kind)} className="flex min-h-11 items-center justify-between gap-3 border border-[var(--color-border-subtle)] px-3 text-left text-sm font-semibold text-[var(--color-fg)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-50"><span>{label}</span><Download size={15} className="shrink-0 text-[var(--color-brand-600)]" /></button>)}</div>
        </section>
        {mayClose && <HasPermission authority="PAIE_WRITE"><section className="space-y-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
            <h3 className="text-base font-bold text-[var(--color-fg)]">Clôture administrative</h3>
            <p className="text-xs text-[var(--color-fg-muted)]">La fin du contrat, les formalités et les contrôles bloquants seront revérifiés par le serveur. La clôture ne signifie pas forcément que la dette est éteinte.</p>
            <label className="block space-y-1 text-xs font-semibold text-[var(--color-fg)]">Motif de clôture
                <input aria-label="Motif de clôture" placeholder="Précisez le motif de clôture" value={closure.motifCloture} onChange={e => setClosure(p => ({ ...p, motifCloture: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal" />
            </label>
            {!situation?.estIntegralementRegle && <div className="space-y-3 border border-[var(--color-warning-200)] bg-[var(--color-warning-50)] p-3">
                <p className="text-xs font-semibold text-[var(--color-warning-900)]">Solde restant dû : {formatMoneyAr(situation?.resteAPayer ?? dossier?.soldeRestantAPayer ?? 0)}</p>
                <label className="flex min-h-10 items-center gap-2 text-sm text-[var(--color-fg)]"><input type="checkbox" checked={closure.derogationReglementIncomplet} onChange={e => setClosure(p => ({ ...p, derogationReglementIncomplet: e.target.checked }))} />Décision dérogatoire documentée pour clôturer avec ce solde</label>
                {closure.derogationReglementIncomplet && <div className="grid gap-2 sm:grid-cols-2">
                    {[
                        ['justificationDerogation', 'Justification de la dérogation'],
                        ['autoriteDecisionDerogation', 'Autorité décisionnaire'],
                        ['referenceDecisionDerogation', 'Référence de la décision'],
                    ].map(([field, label]) => <label key={field} className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">{label}<input required aria-label={label} placeholder={label} value={closure[field]} onChange={e => setClosure(p => ({ ...p, [field]: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal" /></label>)}
                    <label className="space-y-1 text-xs font-semibold text-[var(--color-fg)]">Date de décision<input required aria-label="Date de décision" type="date" value={closure.dateDecisionDerogation} onChange={e => setClosure(p => ({ ...p, dateDecisionDerogation: e.target.value }))} className="min-h-10 w-full border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-2 font-normal" /></label>
                </div>}
            </div>}
            <label className="flex min-h-10 items-center gap-2 text-sm text-[var(--color-fg)]"><input type="checkbox" checked={closure.confirmationFormalites} onChange={e => setClosure(p => ({ ...p, confirmationFormalites: e.target.checked }))} />Je confirme les formalités administratives enregistrées</label>
            <div className="flex justify-end"><button type="button" disabled={busy || !closure.confirmationFormalites || (!situation?.estIntegralementRegle && !closure.derogationReglementIncomplet)} onClick={() => setConfirmation('closure')} className="min-h-10 bg-[var(--color-brand-700)] px-4 font-semibold text-white hover:bg-[var(--color-brand-800)] disabled:opacity-50">Vérifier puis clôturer</button></div>
        </section></HasPermission>}
        {cancellation && <ConfirmDialog isOpen onClose={() => setCancellation(null)}
            onConfirm={() => { if (cancellation.motifAnnulation.trim()) cancelPayment(); }}
            title="Annuler le règlement" message="L’écriture restera visible dans le journal avec son motif d’annulation."
            confirmLabel="Confirmer l’annulation" variant="danger" loading={busy}>
            <input aria-label="Motif de l’annulation" required className="w-full border p-2" placeholder="Motif de l’annulation"
                value={cancellation.motifAnnulation} onChange={e => setCancellation(prev => ({ ...prev, motifAnnulation: e.target.value }))} />
            {!cancellation.motifAnnulation.trim() && <p className="text-xs text-[var(--color-danger-700)]">Un motif est obligatoire.</p>}
        </ConfirmDialog>}
        <ConfirmDialog isOpen={Boolean(confirmation)} onClose={() => setConfirmation(null)}
            onConfirm={() => { const action = confirmation; setConfirmation(null); if (action === 'payment') registerPayment(); else closeDossier(); }}
            title={confirmation === 'payment' ? 'Confirmer le règlement' : 'Confirmer la clôture administrative'}
            message={confirmation === 'payment' ? `Enregistrer le règlement ${payment.referenceReglement} de ${formatMoneyAr(zeroNetAttestation ? 0 : payment.montantRegle)} ?` : 'Cette clôture administrative sera définitive. Le solde restant reste dû en cas de dérogation.'}
            confirmLabel={confirmation === 'payment' ? 'Enregistrer le règlement' : 'Clôturer le dossier'}
            variant="warning" loading={busy} />
    </div>;
}
