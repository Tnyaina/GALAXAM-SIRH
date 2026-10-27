import { useCallback, useEffect, useRef, useState } from 'react';
import { Banknote, History, Wallet } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import SlideOver from '@/components/ui/overlays/SlideOver';
import Button from '@/components/ui/forms/Button';
import Input, { Textarea } from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import Select from '@/components/ui/forms/Select';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import Badge from '@/components/ui/data-display/Badge';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';
import bulletinReglementService from '@/features/payroll/api/bulletinReglementService';

const modes = [
    { value: 'VIREMENT', label: 'Virement bancaire' },
    { value: 'CHEQUE', label: 'Chèque' },
    { value: 'ESPECES', label: 'Espèces' },
    { value: 'MOBILE_MONEY', label: 'Mobile money' },
];
const modeDetails = {
    VIREMENT: { reference: 'Référence du virement', proof: 'Avis de virement ou relevé bancaire' },
    CHEQUE: { reference: 'Numéro du chèque', proof: 'Copie du chèque ou reçu de remise' },
    ESPECES: { reference: 'Numéro du reçu de caisse', proof: 'Reçu de caisse daté et signé' },
    MOBILE_MONEY: { reference: 'Référence de la transaction', proof: 'Reçu ou capture de la transaction' },
};

export default function BulletinReglementSlideOver({ isOpen, onClose, idBulletin, onChanged, canWrite = true }) {
    const toast = useToast();
    const navigate = useNavigate();
    const [situation, setSituation] = useState(null);
    const [history, setHistory] = useState([]);
    const [form, setForm] = useState({ montant: '', mode: 'VIREMENT', date: getTodayDateString(), reference: '', commentaire: '' });
    const [file, setFile] = useState(null);
    const [fileKey, setFileKey] = useState(0);
    const [cancelId, setCancelId] = useState(null);
    const [cancelReason, setCancelReason] = useState('');
    const [loading, setLoading] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [formError, setFormError] = useState('');
    const loadRequest = useRef(0);
    const busyRef = useRef(false);

    const load = useCallback(async () => {
        if (!idBulletin) return;
        const request = ++loadRequest.current;
        setSituation(null);
        setHistory([]);
        setLoading(true);
        try {
            const [s, h] = await Promise.all([
                bulletinReglementService.situation(idBulletin),
                bulletinReglementService.historique(idBulletin),
            ]);
            if (request !== loadRequest.current) return;
            setSituation(s);
            setHistory(h);
            setForm({ montant: String(s.reste), mode: s.modePaiement || 'VIREMENT', date: getTodayDateString(), reference: '', commentaire: '' });
            setError('');
            setFormError('');
        } catch (cause) { if (request === loadRequest.current) { setSituation(null); setHistory([]); setError(getErrorMessage(cause, 'Situation du bulletin indisponible.')); } }
        finally { if (request === loadRequest.current) setLoading(false); }
    }, [idBulletin]);

    useEffect(() => { if (isOpen) load(); else loadRequest.current += 1; }, [isOpen, load]);

    const save = async (event) => {
        event.preventDefault();
        if (busyRef.current) return;
        if (Number(form.montant) <= 0 || Number(form.montant) > Number(situation?.reste)) {
            setFormError('Le montant doit être positif et ne pas dépasser le reste à payer.');
            return;
        }
        if (!file) { setFormError('Joignez un justificatif du versement.'); return; }
        busyRef.current = true;
        setBusy(true);
        try {
            await bulletinReglementService.enregistrer({ idBulletin, ...form, montant: Number(form.montant), file });
            toast.success('Règlement enregistré.');
            await load();
            setFile(null);
            setFileKey(value => value + 1);
            onChanged?.();
        } catch (cause) { setFormError(getErrorMessage(cause, 'Règlement impossible.')); }
        finally { busyRef.current = false; setBusy(false); }
    };
    const cancel = async () => {
        if (!cancelReason.trim() || busyRef.current) return;
        busyRef.current = true;
        setBusy(true);
        try {
            await bulletinReglementService.annuler(cancelId, cancelReason.trim());
            toast.success('Règlement annulé.');
            setCancelId(null);
            setCancelReason('');
            await load();
            onChanged?.();
        } catch (cause) { setError(getErrorMessage(cause, 'Annulation impossible.')); }
        finally { busyRef.current = false; setBusy(false); }
    };
    const downloadProof = async item => {
        try {
            const blob = await bulletinReglementService.justificatif(idBulletin, item.id);
            const url = URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = item.nomJustificatif || `justificatif-${item.id}`;
            link.click();
            window.setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (cause) { setError(getErrorMessage(cause, 'Justificatif indisponible.')); }
    };

    const currentSituation = situation?.idBulletin === idBulletin ? situation : null;
    const balance = Number(currentSituation?.reste || 0);
    const paymentStatus = currentSituation?.virementEnAttente ? 'Virement en attente'
        : balance === 0 ? 'Payé' : Number(currentSituation?.verse || 0) > 0 ? 'Partiellement payé' : 'À payer';
    return <SlideOver isOpen={isOpen} onClose={onClose} width="xl" title="Règlement du bulletin"
        subtitle={currentSituation ? `${currentSituation.nomPrenom} · ${currentSituation.referenceBulletin}` : 'Situation financière'} icon={<Wallet size={17} />}>
        <div className="space-y-5">
            {error && <Alert type="error" message={error} />}
            {loading && !currentSituation ? <p className="text-sm text-[var(--color-fg-muted)]">Chargement de la situation…</p> : currentSituation && <>
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <div className="flex items-center justify-between gap-3 border-b border-[var(--color-border-subtle)] px-4 py-3">
                        <span className="text-sm font-semibold">Situation du bulletin</span>
                        <Badge variant={balance === 0 ? 'success' : situation.virementEnAttente ? 'info' : 'warning'} size="xs">{paymentStatus}</Badge>
                    </div>
                    <dl className="grid grid-cols-1 sm:grid-cols-3 sm:divide-x divide-y sm:divide-y-0 divide-[var(--color-border-subtle)] text-sm">
                        <div className="p-3"><dt className="text-xs text-[var(--color-fg-muted)]">Net dû</dt><dd className="mt-1 font-semibold tabular-nums">{formatMoneyAr(situation.netAPayer)}</dd></div>
                        <div className="p-3"><dt className="text-xs text-[var(--color-fg-muted)]">Versé</dt><dd className="mt-1 font-semibold tabular-nums">{formatMoneyAr(situation.verse)}</dd></div>
                        <div className="p-3"><dt className="text-xs text-[var(--color-fg-muted)]">Reste</dt><dd className="mt-1 font-bold tabular-nums">{formatMoneyAr(balance)}</dd></div>
                    </dl>
                </div>
                {situation.virementEnAttente && <div className="space-y-2"><Alert type="info" message="Ce bulletin figure dans un lot de virements en attente. Enregistrez le paiement déjà effectué ou signalez un rejet depuis ce lot avant un autre règlement." />{situation.idPeriodePaie && <Button variant="outline" size="sm" onClick={() => { onClose(); navigate(`/paie/periodes/${situation.idPeriodePaie}/reglements`); }}>Voir les lots de la période</Button>}</div>}
                {situation.inclusDansStc && <div className="space-y-2"><Alert type="warning" title="Bulletin repris dans un STC actif" message="Ce bulletin est inclus dans un Solde de Tout Compte actif. Son versement doit être enregistré et justifié depuis le dossier de sortie du salarié." /></div>}
                {canWrite && balance > 0 && !situation.virementEnAttente && !situation.inclusDansStc && <form onSubmit={save} className="space-y-3 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-4">
                    <div className="flex items-center gap-2"><Banknote size={16} className="text-[var(--color-brand-600)]" /><h3 className="text-sm font-semibold">Marquer un montant déjà payé</h3></div>
                    <p className="text-xs text-[var(--color-fg-muted)]">Indiquez le mode réellement utilisé, même s'il diffère du mode prévu sur le bulletin, et joignez la preuve du paiement déjà effectué. Un paiement individuel par chèque, espèces ou mobile money ne nécessite pas de RIB.</p>
                    <FormError message={formError} onClose={() => setFormError('')} />
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <MoneyInput label="Montant versé (Ar)" required min={0.01} max={balance} value={form.montant} onValueChange={value => setForm(f => ({ ...f, montant: value }))} />
                        <Select label="Mode de règlement" required options={modes} value={form.mode} onChange={value => setForm(f => ({ ...f, mode: value }))} />
                        <Input type="date" label="Date effective" required max={getTodayDateString()} value={form.date} onChange={e => setForm(f => ({ ...f, date: e.target.value }))} />
                        <Input label={modeDetails[form.mode]?.reference || 'Référence du paiement'} required value={form.reference} onChange={e => setForm(f => ({ ...f, reference: e.target.value }))} />
                    </div>
                    <Textarea label="Commentaire (facultatif)" rows={2} value={form.commentaire} onChange={e => setForm(f => ({ ...f, commentaire: e.target.value }))} />
                    <Input key={fileKey} type="file" label="Justificatif du versement" required accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" onChange={e => setFile(e.target.files?.[0] || null)} hint={`${modeDetails[form.mode]?.proof || 'Preuve du paiement'} · PDF, PNG ou JPEG · 5 Mo maximum`} />
                    <div className="flex justify-end"><Button type="submit" disabled={busy}>Enregistrer le paiement</Button></div>
                </form>}
                <section className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <div className="flex items-center gap-2 border-b border-[var(--color-border-subtle)] px-4 py-3"><History size={16} className="text-[var(--color-brand-600)]" /><h3 className="text-sm font-semibold">Historique des versements</h3></div>
                    {history.length === 0 ? <p className="p-4 text-sm text-[var(--color-fg-muted)]">Aucun versement enregistré.</p> : history.map(item => <div key={item.id} className="border-b border-[var(--color-border-subtle)] px-4 py-3 last:border-b-0">
                        <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                            <div><strong className={item.annuleAt ? 'text-[var(--color-fg-muted)]' : ''}>{formatMoneyAr(item.montant)}</strong><p className="text-xs text-[var(--color-fg-muted)]">{fmtDate(item.date)} · {modes.find(m => m.value === item.mode)?.label || item.mode} · {item.reference}</p></div>
                            {item.annuleAt ? <Badge variant="secondary" size="xs">Annulé</Badge> : canWrite && !item.idLigneVirement && !item.idStcReglement && <Button variant="outline" size="xs" onClick={() => { setCancelId(item.id); setCancelReason(''); }}>Annuler</Button>}
                        </div>
                        {item.annuleAt && item.motifAnnulation && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Motif : {item.motifAnnulation}</p>}
                        {!item.annuleAt && item.idLigneVirement && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Paiement enregistré depuis un lot de virements.</p>}
                        {!item.annuleAt && item.idStcReglement && <p className="mt-1 text-xs text-[var(--color-fg-muted)]">Part du versement STC n° {item.idStcReglement} affectée à ce bulletin. L'annulation se fait dans le dossier de sortie.</p>}
                        {item.nomJustificatif
                            ? <Button variant="outline" size="xs" className="mt-2" onClick={() => downloadProof(item)}>Télécharger le justificatif</Button>
                            : <p className="mt-2 text-xs font-medium text-[var(--color-warning-700)]">Versement historique sans justificatif joint — à rapprocher d’une preuve réelle.</p>}
                        {cancelId === item.id && <div className="mt-3 space-y-2"><Textarea label="Motif de l'annulation" required rows={2} value={cancelReason} onChange={e => setCancelReason(e.target.value)} /><div className="flex gap-2 justify-end"><Button variant="outline" size="sm" onClick={() => setCancelId(null)}>Conserver</Button><Button size="sm" disabled={busy || !cancelReason.trim()} onClick={cancel}>Confirmer l'annulation</Button></div></div>}
                    </div>)}
                </section>
            </>}
        </div>
    </SlideOver>;
}
