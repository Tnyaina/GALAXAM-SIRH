import { useCallback, useEffect, useState } from 'react';
import { FileText, Plus } from 'lucide-react';
import apiClient, { getErrorMessage } from '@/api/apiClient';
import { useAuth } from '@/context/AuthContext';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { getTodayDateString } from '@/utils/dateUtils';

const today = getTodayDateString;
const PDF_MAX_BYTES = 10_000_000;

function verifierPdf(file) {
    if (!file || file.type !== 'application/pdf' || file.size > PDF_MAX_BYTES) {
        throw new Error('Sélectionnez un PDF de 10 Mo maximum.');
    }
}

export default function PrestatairesPage() {
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');
    const canValidate = hasPermission('PAIE_VALIDATE');
    const [prestataires, setPrestataires] = useState([]);
    const [contrats, setContrats] = useState([]);
    const [factures, setFactures] = useState([]);
    const [error, setError] = useState('');
    const [message, setMessage] = useState('');
    const [busy, setBusy] = useState(false);
    const [prestataire, setPrestataire] = useState({ nom: '', nif: '', stat: '', adresse: '', email: '' });
    const [contrat, setContrat] = useState({ idPrestataire: '', reference: '', objet: '', dateDebut: today(), dateFin: '', tarifConvenu: '' });
    const [facture, setFacture] = useState({ idContratPrestataire: '', reference: '', dateFacture: today(), montant: '' });
    const [pieces, setPieces] = useState({});
    const [piecesContrat, setPiecesContrat] = useState({});
    const [paiements, setPaiements] = useState({});
    const [annulations, setAnnulations] = useState({});
    const contratsDocumentes = contrats.filter(c => Boolean(c.chemin_piece));
    const facturesEnAttente = factures.filter(f => f.statut === 'BROUILLON' || f.statut === 'VALIDEE');

    const charger = useCallback(async () => {
        try {
            const [p, c, f] = await Promise.all([
                apiClient.get('v1/prestataires'),
                apiClient.get('v1/prestataires/contrats'),
                apiClient.get('v1/prestataires/factures'),
            ]);
            setPrestataires(p.data); setContrats(c.data); setFactures(f.data);
        } catch (err) { setError(getErrorMessage(err, 'Impossible de charger les dossiers prestataires.')); throw err; }
    }, []);

    useEffect(() => { charger().catch(() => {}); }, [charger]);

    const agir = async (action, succes) => {
        if (busy) return;
        setBusy(true); setError(''); setMessage('');
        try { await action(); await charger(); setMessage(succes); }
        catch (err) { setError(getErrorMessage(err, 'Opération impossible.')); }
        finally { setBusy(false); }
    };

    const ajouterPrestataire = (event) => {
        event.preventDefault();
        agir(async () => {
            const { data } = await apiClient.post('v1/prestataires', prestataire);
            setContrat(previous => ({ ...previous, idPrestataire: String(data.idPrestataire) }));
            setPrestataire({ nom: '', nif: '', stat: '', adresse: '', email: '' });
        }, 'Prestataire enregistré. Créez maintenant son contrat.');
    };
    const ajouterContrat = (event) => {
        event.preventDefault();
        agir(async () => {
            const { data } = await apiClient.post('v1/prestataires/contrats', {
                ...contrat, idPrestataire: Number(contrat.idPrestataire),
                dateFin: contrat.dateFin || null, tarifConvenu: contrat.tarifConvenu || null,
            });
            setFacture(previous => ({ ...previous, idContratPrestataire: String(data.idContratPrestataire) }));
            setContrat({ idPrestataire: '', reference: '', objet: '', dateDebut: today(), dateFin: '', tarifConvenu: '' });
        }, 'Contrat enregistré. Joignez son PDF dans la liste des contrats, puis créez une facture.');
    };
    const ajouterFacture = (event) => {
        event.preventDefault();
        agir(async () => {
            await apiClient.post('v1/prestataires/factures', {
                ...facture, idContratPrestataire: Number(facture.idContratPrestataire), montant: Number(facture.montant),
            });
            setFacture({ idContratPrestataire: '', reference: '', dateFacture: today(), montant: '' });
        }, 'Facture créée en brouillon. Joignez son PDF dans la liste ci-dessous.');
    };
    const joindrePiece = (id) => agir(async () => {
        verifierPdf(pieces[id]);
        const data = new FormData(); data.append('file', pieces[id]);
        await apiClient.post(`v1/prestataires/factures/${id}/piece`, data, { headers: { 'Content-Type': 'multipart/form-data' } });
        setPieces(previous => ({ ...previous, [id]: null }));
    }, 'Pièce justificative enregistrée.');
    const joindreContrat = (id) => agir(async () => {
        verifierPdf(piecesContrat[id]);
        const data = new FormData(); data.append('file', piecesContrat[id]);
        await apiClient.post(`v1/prestataires/contrats/${id}/piece`, data, { headers: { 'Content-Type': 'multipart/form-data' } });
        setPiecesContrat(previous => ({ ...previous, [id]: null }));
    }, 'Contrat PDF enregistré.');
    const telechargerPiece = async (kind, id) => {
        try {
            const response = await apiClient.get(`v1/prestataires/${kind}/${id}/piece`, { responseType: 'blob' });
            const url = URL.createObjectURL(response.data);
            const link = document.createElement('a'); link.href = url; link.download = `${kind === 'contrats' ? 'contrat' : 'facture'}-prestataire-${id}.pdf`; link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (err) { setError(getErrorMessage(err, 'Téléchargement impossible.')); }
    };

    return (
        <div className="space-y-6 max-w-7xl mx-auto pb-12 px-3 sm:px-5">
            <PageHeader title="Prestataires indépendants" subtitle="Contrats, factures et paiements hors bulletin de paie et sans retenue ISI générale." icon={FileText}
                breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Prestataires indépendants' }]} />
            {error && <div role="alert"><Alert type="error" message={error} /></div>}
            {message && <div role="status"><Alert type="success" message={message} /></div>}
            <Alert type="info" message="Ce circuit est réservé aux indépendants disposant d'un NIF. Un consultant salarié doit être inscrit dans la paie et soumis aux règles salariales applicables." />
            <div className="grid gap-3 sm:grid-cols-3" aria-label="Étapes du parcours prestataire">
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                    <strong>1 · Identifier</strong><p className="text-xs text-[var(--color-fg-muted)]">Créer le dossier avec NIF, puis le contrat et son PDF.</p>
                </div>
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                    <strong>2 · Facturer</strong><p className="text-xs text-[var(--color-fg-muted)]">Créer une facture liée au contrat et joindre son PDF.</p>
                </div>
                <div className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] p-3">
                    <strong>3 · Valider et régler</strong><p className="text-xs text-[var(--color-fg-muted)]">Valider les pièces, puis enregistrer la date et la référence du paiement réel.</p>
                </div>
            </div>

            {canWrite && <div className="grid gap-4 lg:grid-cols-3">
                <form onSubmit={ajouterPrestataire} className="space-y-3 p-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <h2 className="font-bold">1. Dossier prestataire</h2>
                    <p className="text-xs text-[var(--color-fg-muted)]">Le NIF est obligatoire. Un consultant lié par contrat de travail relève du parcours salarié.</p>
                    <Input label="Nom ou raison sociale" required value={prestataire.nom} onChange={e => setPrestataire({ ...prestataire, nom: e.target.value })} />
                    <Input label="NIF" required value={prestataire.nif} onChange={e => setPrestataire({ ...prestataire, nif: e.target.value })} />
                    <Input label="STAT" value={prestataire.stat} onChange={e => setPrestataire({ ...prestataire, stat: e.target.value })} />
                    <Input label="Adresse" value={prestataire.adresse} onChange={e => setPrestataire({ ...prestataire, adresse: e.target.value })} />
                    <Input label="E-mail" type="email" value={prestataire.email} onChange={e => setPrestataire({ ...prestataire, email: e.target.value })} />
                    <Button type="submit" disabled={busy} iconLeft={<Plus size={16} />}>Enregistrer</Button>
                </form>
                <form onSubmit={ajouterContrat} className="space-y-3 p-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <h2 className="font-bold">2. Contrat de prestation</h2>
                    <p className="text-xs text-[var(--color-fg-muted)]">Sélectionnez le prestataire, puis joignez le PDF du contrat après sa création.</p>
                    <SearchSelect label="Prestataire" required value={contrat.idPrestataire} onChange={value => setContrat({ ...contrat, idPrestataire: value })}
                        options={prestataires.filter(p => p.est_actif).map(p => ({ value: String(p.id_prestataire), label: `${p.nom} · ${p.nif}` }))}
                        hint={prestataires.length === 0 ? 'Créez d’abord un dossier prestataire.' : null} />
                    <Input label="Référence du contrat" required value={contrat.reference} onChange={e => setContrat({ ...contrat, reference: e.target.value })} />
                    <Input label="Objet" required value={contrat.objet} onChange={e => setContrat({ ...contrat, objet: e.target.value })} />
                    <Input label="Début" type="date" required value={contrat.dateDebut} onChange={e => setContrat({ ...contrat, dateDebut: e.target.value })} />
                    <Input label="Fin éventuelle" type="date" value={contrat.dateFin} onChange={e => setContrat({ ...contrat, dateFin: e.target.value })} />
                    <Input label="Tarif convenu (Ar)" type="number" min="0" value={contrat.tarifConvenu} onChange={e => setContrat({ ...contrat, tarifConvenu: e.target.value })} />
                    <Button type="submit" disabled={busy || !contrat.idPrestataire} iconLeft={<Plus size={16} />}>Enregistrer</Button>
                </form>
                <form onSubmit={ajouterFacture} className="space-y-3 p-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
                    <h2 className="font-bold">3. Facture</h2>
                    <p className="text-xs text-[var(--color-fg-muted)]">Le contrat PDF doit être joint avant validation. Une facture peut être émise après la fin de la prestation.</p>
                    <SearchSelect label="Contrat" required value={facture.idContratPrestataire} onChange={value => setFacture({ ...facture, idContratPrestataire: value })}
                        options={contrats.map(c => ({ value: String(c.id_contrat_prestataire), label: `${c.prestataire} · ${c.reference}${c.chemin_piece ? '' : ' · PDF manquant'}` }))}
                        hint={contrats.length === 0 ? 'Créez d’abord un contrat de prestation.' : null} />
                    <Input label="Référence facture" required value={facture.reference} onChange={e => setFacture({ ...facture, reference: e.target.value })} />
                    <Input label="Date de facture" type="date" required value={facture.dateFacture} onChange={e => setFacture({ ...facture, dateFacture: e.target.value })} />
                    <Input label="Montant (Ar)" type="number" min="0.01" step="0.01" required value={facture.montant} onChange={e => setFacture({ ...facture, montant: e.target.value })} />
                    <Button type="submit" disabled={busy || !facture.idContratPrestataire} iconLeft={<Plus size={16} />}>Créer le brouillon</Button>
                </form>
            </div>}

            <section className="space-y-3">
                <h2 className="font-bold text-lg">Contrats de prestation</h2>
                <p className="text-xs text-[var(--color-fg-muted)]">{contratsDocumentes.length} contrat(s) avec PDF sur {contrats.length}. Le PDF du contrat et celui de la facture sont requis pour valider.</p>
                {contrats.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Aucun contrat enregistré.</p>}
                {contrats.map(c => <div key={c.id_contrat_prestataire} className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] flex flex-wrap items-center justify-between gap-3">
                    <div><strong>{c.prestataire}</strong> · {c.reference}<br /><span className="text-xs text-[var(--color-fg-muted)]">{c.objet} · {c.date_debut} à {c.date_fin || 'durée ouverte'} · {c.chemin_piece ? 'PDF joint' : 'PDF à joindre'}</span></div>
                    <div className="flex flex-wrap items-center gap-2">
                        {c.chemin_piece && <Button variant="outline" size="sm" onClick={() => telechargerPiece('contrats', c.id_contrat_prestataire)}>Voir le contrat PDF</Button>}
                        {canWrite && !c.chemin_piece && <>
                            <input type="file" accept="application/pdf" aria-label={`PDF du contrat ${c.reference}, 10 Mo maximum`} onChange={e => setPiecesContrat(previous => ({ ...previous, [c.id_contrat_prestataire]: e.target.files?.[0] }))} />
                            <Button size="sm" disabled={busy || !piecesContrat[c.id_contrat_prestataire]} onClick={() => joindreContrat(c.id_contrat_prestataire)}>Joindre le contrat</Button>
                        </>}
                    </div>
                </div>)}
            </section>

            <section className="space-y-3">
                <h2 className="font-bold text-lg">Factures et règlements</h2>
                <p className="text-xs text-[var(--color-fg-muted)]">{facturesEnAttente.length} facture(s) à compléter ou à régler. « Marquer payée » consigne un paiement déjà effectué et ne déclenche aucun virement.</p>
                {factures.length === 0 && <p className="text-sm text-[var(--color-fg-muted)]">Aucune facture enregistrée.</p>}
                {factures.map(f => {
                    const id = f.id_facture_prestataire;
                    const paiement = paiements[id] || { datePaiement: today(), referencePaiement: '' };
                    return <div key={id} className="p-4 border border-[var(--color-border-subtle)] bg-[var(--color-surface)] space-y-3">
                        <div className="flex flex-wrap justify-between gap-2">
                            <div><strong>{f.prestataire}</strong> · {f.reference} · contrat {f.contrat_reference}<br /><span className="text-sm text-[var(--color-fg-muted)]">{f.date_facture} · {f.statut === 'BROUILLON' ? 'Brouillon' : f.statut === 'VALIDEE' ? 'Validée, paiement à enregistrer' : f.statut === 'PAYEE' ? 'Payée' : 'Annulée'}</span></div>
                            <strong>{formatMoneyAr(f.montant)}</strong>
                        </div>
                        <div className="flex flex-wrap items-end gap-3">
                            {f.chemin_piece && <Button variant="outline" size="sm" onClick={() => telechargerPiece('factures', id)}>Voir le PDF</Button>}
                            {canWrite && f.statut === 'BROUILLON' && !f.chemin_piece && <>
                                <label className="text-xs">Facture PDF (10 Mo maximum) <input type="file" accept="application/pdf" onChange={e => setPieces(previous => ({ ...previous, [id]: e.target.files?.[0] }))} /></label>
                                <Button size="sm" disabled={busy || !pieces[id]} onClick={() => joindrePiece(id)}>Joindre</Button>
                            </>}
                            {canValidate && f.statut === 'BROUILLON' && <Button size="sm" disabled={busy || !f.chemin_piece || !contrats.find(c => c.id_contrat_prestataire === f.id_contrat_prestataire)?.chemin_piece}
                                onClick={() => agir(() => apiClient.post(`v1/prestataires/factures/${id}/valider`), 'Facture validée.')}>Valider</Button>}
                            {f.statut === 'BROUILLON' && (!f.chemin_piece || !contrats.find(c => c.id_contrat_prestataire === f.id_contrat_prestataire)?.chemin_piece) &&
                                <span className="text-xs text-[var(--color-fg-muted)]">Validation en attente : {!contrats.find(c => c.id_contrat_prestataire === f.id_contrat_prestataire)?.chemin_piece ? 'PDF du contrat' : 'PDF de la facture'} manquant.</span>}
                            {canWrite && f.statut === 'BROUILLON' && <>
                                <Input label="Motif d'annulation" value={annulations[id] || ''}
                                    onChange={e => setAnnulations(previous => ({ ...previous, [id]: e.target.value }))} />
                                <Button variant="outline" size="sm" disabled={busy || !annulations[id]?.trim()}
                                    onClick={() => agir(() => apiClient.post(`v1/prestataires/factures/${id}/annuler`, { motif: annulations[id].trim() }), 'Brouillon annulé. Vous pouvez créer une facture corrigée.')}>Annuler le brouillon</Button>
                            </>}
                            {canValidate && f.statut === 'VALIDEE' && <>
                                <Input label="Date réelle du paiement" type="date" min={f.date_facture} max={today()} value={paiement.datePaiement} onChange={e => setPaiements(previous => ({ ...previous, [id]: { ...paiement, datePaiement: e.target.value } }))} />
                                <Input label="Référence du paiement" value={paiement.referencePaiement} onChange={e => setPaiements(previous => ({ ...previous, [id]: { ...paiement, referencePaiement: e.target.value } }))} />
                                <Button size="sm" disabled={busy || !paiement.referencePaiement}
                                    onClick={() => agir(() => apiClient.post(`v1/prestataires/factures/${id}/payer`, paiement), 'Paiement enregistré.')}>Marquer payée</Button>
                            </>}
                            {f.statut === 'PAYEE' && <span className="text-xs">Payée le {f.date_paiement} · {f.reference_paiement}</span>}
                            {f.statut === 'ANNULEE' && <span className="text-xs">Annulée : {f.motif_annulation}</span>}
                        </div>
                    </div>;
                })}
            </section>
        </div>
    );
}
