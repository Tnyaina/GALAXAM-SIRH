import { default as SharedInput } from '@/components/ui/forms/Input';
import { useCallback, useEffect, useRef, useState } from 'react';
import { FileText, Plus, Users, FileCheck2, ReceiptText, Search, ChevronDown, X } from 'lucide-react';
import apiClient, { getErrorMessage } from '@/api/apiClient';
import { useAuth } from '@/context/AuthContext';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input, { Select } from '@/components/ui/forms/Input';
import MoneyInput from '@/components/ui/forms/MoneyInput';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Alert from '@/components/ui/feedback/Alert';
import SEO from '@/components/system/SEO';
import FormModal from '@/components/ui/overlays/FormModal';
import { Card, CardHeader, CardTitle, CardSubtitle, CardBody } from '@/components/ui/data-display/Card';
import { Tabs } from '@/components/ui/navigation/Tabs';
import StatCard from '@/components/ui/data-display/StatCard';
import EmptyState from '@/components/ui/data-display/EmptyState';
import StatusBadge from '@/components/ui/data-display/StatusBadge';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';

const today = getTodayDateString;
const PDF_MAX_BYTES = 10_000_000;

function verifierPdf(file) {
    if (!file || file.type !== 'application/pdf' || file.size > PDF_MAX_BYTES) {
        throw new Error('Sélectionnez un PDF de 10 Mo maximum.');
    }
}

function nomPiecePrestataire(type, reference, date) {
    const slug = String(reference || '').normalize('NFD').replace(/[\u0300-\u036f]/g, '')
        .toLocaleLowerCase('fr').replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 80).replace(/-$/g, '');
    const dateIso = date ? String(date).slice(0, 10) : '';
    return `${type}-${slug || 'sans-reference'}${dateIso ? `-${dateIso}` : ''}.pdf`;
}

function filenameDepuisReponse(response, fallback) {
    const header = response.headers?.['content-disposition'] || '';
    const encodedName = header.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plainName = header.match(/filename="?([^";]+)"?/i)?.[1];
    const candidate = encodedName ? decodeURIComponent(encodedName) : plainName;
    return candidate || fallback;
}

function PdfAttach({ label, filename, busy, onUpload }) {
    const inputRef = useRef(null);
    const [file, setFile] = useState(null);
    return <div className="flex flex-wrap items-center gap-1.5">
        <SharedInput plain ref={inputRef} type="file" accept="application/pdf" className="sr-only"
            aria-label={`${label}, PDF de 10 Mo maximum`} onChange={e => setFile(e.target.files?.[0] || null)} />
        <Button type="button" variant="outline" size="xs" disabled={busy} onClick={() => inputRef.current?.click()}>
            {file ? (filename || file.name) : label}
        </Button>
        {file && <Button type="button" size="xs" disabled={busy} onClick={async () => {
            const uploadFile = filename
                ? new File([file], filename, { type: file.type, lastModified: file.lastModified })
                : file;
            const ok = await onUpload(uploadFile);
            if (ok) { setFile(null); if (inputRef.current) inputRef.current.value = ''; }
        }}>Joindre</Button>}
    </div>;
}

export default function PrestatairesPage() {
    const toast = useToast();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');
    const canValidate = hasPermission('PAIE_VALIDATE');
    const [prestataires, setPrestataires] = useState([]);
    const [contrats, setContrats] = useState([]);
    const [factures, setFactures] = useState([]);
    const [error, setError] = useState('');
    const [formError, setFormError] = useState('');
    const [fieldErrors, setFieldErrors] = useState({});
    const [busy, setBusy] = useState(false);
    const [prestataire, setPrestataire] = useState({ nom: '', nif: '', stat: '', adresse: '', email: '' });
    const [contrat, setContrat] = useState({ idPrestataire: '', reference: '', objet: '', dateDebut: today(), dateFin: '', tarifConvenu: '' });
    const [facture, setFacture] = useState({ idContratPrestataire: '', reference: '', dateFacture: today(), montant: '' });
    const [paiements, setPaiements] = useState({});
    const [annulations, setAnnulations] = useState({});
    const [activeTab, setActiveTab] = useState('prestataires');
    const [showWorkflowDetails, setShowWorkflowDetails] = useState(false);
    const [recherchePrestataire, setRecherchePrestataire] = useState('');
    const [rechercheContrat, setRechercheContrat] = useState('');
    const [filtreContratPdf, setFiltreContratPdf] = useState('tous');
    const [rechercheFacture, setRechercheFacture] = useState('');
    const [filtreFactureStatut, setFiltreFactureStatut] = useState('tous');
    const [filtreFacturePieces, setFiltreFacturePieces] = useState('toutes');
    const [modal, setModal] = useState(null);
    const [selectedFacture, setSelectedFacture] = useState(null);
    const contratsDocumentes = contrats.filter(c => Boolean(c.chemin_piece));
    const facturesEnAttente = factures.filter(f => f.statut === 'BROUILLON' || f.statut === 'VALIDEE');
    const prestatairesFiltres = prestataires.filter(p =>
        [p.nom, p.nif, p.stat, p.email].some(value => String(value || '').toLocaleLowerCase('fr').includes(recherchePrestataire.trim().toLocaleLowerCase('fr')))
    );
    const contratsFiltres = contrats.filter(c => {
        const query = rechercheContrat.trim().toLocaleLowerCase('fr');
        const textMatch = [c.prestataire, c.reference, c.objet].some(value => String(value || '').toLocaleLowerCase('fr').includes(query));
        const pdfMatch = filtreContratPdf === 'tous' || (filtreContratPdf === 'avec-pdf' ? Boolean(c.chemin_piece) : !c.chemin_piece);
        return textMatch && pdfMatch;
    });
    const facturesFiltrees = factures.filter(f => {
        const query = rechercheFacture.trim().toLocaleLowerCase('fr');
        const textMatch = [f.prestataire, f.reference, f.contrat_reference].some(value => String(value || '').toLocaleLowerCase('fr').includes(query));
        const statutMatch = filtreFactureStatut === 'tous' || f.statut === filtreFactureStatut;
        const contratLie = contrats.find(c => c.id_contrat_prestataire === f.id_contrat_prestataire);
        const manquePiece = !f.chemin_piece || !contratLie?.chemin_piece;
        const pieceMatch = filtreFacturePieces === 'toutes' || (filtreFacturePieces === 'incompletes' ? manquePiece : !manquePiece);
        return textMatch && statutMatch && pieceMatch;
    });

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

    const agir = async (action, succes, { form = false } = {}) => {
        if (busy) return false;
        setBusy(true);
        if (form) setFormError(''); else setError('');
        try { await action(); await charger(); toast.success(succes); return true; }
        catch (err) {
            const message = getErrorMessage(err, 'Opération impossible.');
            if (form) setFormError(message); else toast.error(message);
            return false;
        }
        finally { setBusy(false); }
    };

    const validerContrat = () => {
        const errors = {};
        if (!contrat.idPrestataire) errors.idPrestataire = 'Sélectionnez un prestataire.';
        if (!contrat.reference.trim()) errors.reference = 'La référence du contrat est obligatoire.';
        if (!contrat.objet.trim()) errors.objet = 'L’objet de la mission est obligatoire.';
        if (!contrat.dateDebut) errors.dateDebut = 'La date de début est obligatoire.';
        if (contrat.dateFin && contrat.dateFin < contrat.dateDebut) errors.dateFin = 'La date de fin doit être égale ou postérieure au début.';
        if (contrat.tarifConvenu && Number(contrat.tarifConvenu) < 0) errors.tarifConvenu = 'Le tarif ne peut pas être négatif.';
        setFieldErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const validerPaiement = () => {
        const errors = {};
        const value = paiements[selectedFacture?.id_facture_prestataire] || {};
        if (!value.datePaiement) errors.datePaiement = 'La date réelle du paiement est obligatoire.';
        if (selectedFacture?.date_facture && value.datePaiement && value.datePaiement < String(selectedFacture.date_facture).slice(0, 10)) {
            errors.datePaiement = 'La date du paiement ne peut pas précéder la date de facture.';
        }
        if (!value.referencePaiement?.trim()) errors.referencePaiement = 'La référence du paiement est obligatoire.';
        setFieldErrors(errors);
        return Object.keys(errors).length === 0;
    };

    const updateField = (setValue, key, value) => {
        setValue(previous => ({ ...previous, [key]: value }));
        setFieldErrors(previous => ({ ...previous, [key]: undefined }));
        setFormError('');
    };

    const fermerFormulaire = () => {
        setModal(null);
        setFormError('');
        setFieldErrors({});
    };

    const ajouterPrestataire = (event) => {
        event.preventDefault();
        const errors = {};
        if (!prestataire.nom.trim()) errors.nom = 'Le nom ou la raison sociale est obligatoire.';
        if (!prestataire.nif.trim()) errors.nif = 'Le NIF est obligatoire.';
        setFieldErrors(errors);
        setFormError('');
        if (Object.keys(errors).length) return;
        agir(async () => {
            const { data } = await apiClient.post('v1/prestataires', prestataire);
            setContrat(previous => ({ ...previous, idPrestataire: String(data.idPrestataire) }));
            setPrestataire({ nom: '', nif: '', stat: '', adresse: '', email: '' });
            setActiveTab('contrats');
            setModal('contrat');
        }, 'Prestataire enregistré. Créez maintenant son contrat.', { form: true });
    };
    const ajouterContrat = (event) => {
        event.preventDefault();
        setFormError('');
        if (!validerContrat()) return;
        agir(async () => {
            const { data } = await apiClient.post('v1/prestataires/contrats', {
                ...contrat, idPrestataire: Number(contrat.idPrestataire),
                dateFin: contrat.dateFin || null, tarifConvenu: contrat.tarifConvenu || null,
            });
            setFacture(previous => ({ ...previous, idContratPrestataire: String(data.idContratPrestataire) }));
            setContrat({ idPrestataire: '', reference: '', objet: '', dateDebut: today(), dateFin: '', tarifConvenu: '' });
            setActiveTab('contrats');
            setModal(null);
        }, 'Contrat enregistré. Joignez son PDF dans la liste des contrats, puis créez une facture.', { form: true });
    };
    const ajouterFacture = (event) => {
        event.preventDefault();
        const errors = {};
        if (!facture.idContratPrestataire) errors.idContratPrestataire = 'Sélectionnez un contrat.';
        if (!facture.reference.trim()) errors.reference = 'La référence de facture est obligatoire.';
        if (!facture.dateFacture) errors.dateFacture = 'La date de facture est obligatoire.';
        if (!facture.montant || Number(facture.montant) <= 0) errors.montantFacture = 'Le montant doit être supérieur à zéro.';
        setFieldErrors(errors);
        setFormError('');
        if (Object.keys(errors).length) return;
        agir(async () => {
            await apiClient.post('v1/prestataires/factures', {
                ...facture, idContratPrestataire: Number(facture.idContratPrestataire), montant: Number(facture.montant),
            });
            setFacture({ idContratPrestataire: '', reference: '', dateFacture: today(), montant: '' });
            setActiveTab('factures');
            setModal(null);
        }, 'Facture créée en brouillon. Joignez son PDF dans la liste ci-dessous.', { form: true });
    };
    const joindrePiece = (id, file) => agir(async () => {
        verifierPdf(file);
        const data = new FormData(); data.append('file', file);
        await apiClient.post(`v1/prestataires/factures/${id}/piece`, data, { headers: { 'Content-Type': 'multipart/form-data' } });
    }, 'Pièce justificative enregistrée.');
    const joindreContrat = (id, file) => agir(async () => {
        verifierPdf(file);
        const data = new FormData(); data.append('file', file);
        await apiClient.post(`v1/prestataires/contrats/${id}/piece`, data, { headers: { 'Content-Type': 'multipart/form-data' } });
    }, 'Contrat PDF enregistré.');
    const telechargerPiece = async (kind, id) => {
        try {
            const response = await apiClient.get(`v1/prestataires/${kind}/${id}/piece`, { responseType: 'blob' });
            const url = URL.createObjectURL(response.data);
            const contract = kind === 'contrats' ? contrats.find(item => item.id_contrat_prestataire === id) : null;
            const invoice = kind === 'factures' ? factures.find(item => item.id_facture_prestataire === id) : null;
            const fallback = contract
                ? nomPiecePrestataire('contrat-prestation', contract.reference, contract.date_debut)
                : invoice
                    ? nomPiecePrestataire('facture-prestataire', invoice.reference, invoice.date_facture)
                    : `${kind === 'contrats' ? 'contrat-prestation' : 'facture-prestataire'}-${id}.pdf`;
            const link = document.createElement('a'); link.href = url; link.download = filenameDepuisReponse(response, fallback); link.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (err) { toast.error(getErrorMessage(err, 'Téléchargement impossible.')); }
    };

    return (
        <div className="space-y-5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full pb-12 px-2 sm:px-4">
            <SEO title="Prestataires indépendants | GALAXAM GRH" description="Dossiers, contrats, factures et règlements des prestataires indépendants." />
            <PageHeader title="Prestataires indépendants" subtitle="Contrats, factures et règlements hors bulletin de paie." icon={FileText}
                breadcrumbs={[{ label: 'Paie', href: '/paie' }, { label: 'Prestataires indépendants' }]}
                />
            {error && <div role="alert"><Alert type="error" message={error} /></div>}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <StatCard title="Prestataires" value={prestataires.length} icon={<Users size={17} />} subtext="Dossiers fiscaux" />
                <StatCard title="Contrats avec PDF" value={`${contratsDocumentes.length} / ${contrats.length}`} icon={<FileCheck2 size={17} />} subtext="Pièce requise pour valider" color="info" />
                <StatCard title="Factures à traiter" value={facturesEnAttente.length} icon={<ReceiptText size={17} />} subtext="À compléter ou à régler" color="warning" />
            </div>

            <Card shadow="sm" className="overflow-hidden">
                <CardHeader className="px-5 py-4"><div><CardTitle size="lg">Dossiers prestataires</CardTitle><CardSubtitle>Suivez chaque dossier, du contrat au règlement.</CardSubtitle></div></CardHeader>
                <div className="border-y border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] px-5 py-3">
                    <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                        <div className="min-w-0">
                            <p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Parcours de règlement</p>
                            <ol className="mt-2 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs font-semibold text-[var(--color-fg)]">
                                {['Prestataire', 'Contrat', 'Facture', 'Validation', 'Paiement consigné'].map((step, index) => <li key={step} className="flex items-center gap-2"><span className={`inline-flex h-5 w-5 items-center justify-center border text-[10px] ${index < 3 ? 'border-[var(--color-brand-500)]/30 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]' : 'border-[var(--color-border-subtle)] bg-[var(--color-surface)] text-[var(--color-fg-muted)]'}`}>{index + 1}</span>{step}{index < 4 && <span aria-hidden="true" className="text-[var(--color-fg-subtle)]">›</span>}</li>)}
                            </ol>
                        </div>
                        <Button type="button" variant="ghost" size="xs" aria-expanded={showWorkflowDetails}
                            aria-controls="prestataire-workflow-details" iconRight={<ChevronDown size={14} className={`transition-transform ${showWorkflowDetails ? 'rotate-180' : ''}`} />}
                            onClick={() => setShowWorkflowDetails(value => !value)}>
                            {showWorkflowDetails ? 'Masquer les étapes' : 'Détail des étapes'}
                        </Button>
                    </div>
                    {showWorkflowDetails && <ol id="prestataire-workflow-details" className="mt-3 grid gap-3 border-t border-[var(--color-border-subtle)] pt-3 text-xs sm:grid-cols-2 lg:grid-cols-5">
                        <li><strong>1. Identifier</strong><span className="block text-[var(--color-fg-muted)]">Créer le prestataire et saisir son NIF.</span></li>
                        <li><strong>2. Contractualiser</strong><span className="block text-[var(--color-fg-muted)]">Enregistrer la mission et joindre le contrat signé en PDF.</span></li>
                        <li><strong>3. Facturer</strong><span className="block text-[var(--color-fg-muted)]">Saisir la facture reçue et joindre son PDF.</span></li>
                        <li><strong>4. Valider</strong><span className="block text-[var(--color-fg-muted)]">Contrôler les pièces et valider la facture.</span></li>
                        <li><strong>5. Régler</strong><span className="block text-[var(--color-fg-muted)]">Après paiement réel, consigner date et référence.</span></li>
                    </ol>}
                </div>
                <Tabs ariaLabel="Parcours prestataires" activeTab={activeTab} onChange={setActiveTab} tabs={[
                    { id: 'prestataires', label: 'Prestataires', icon: Users, count: prestataires.length },
                    { id: 'contrats', label: 'Contrats', icon: FileCheck2, count: contrats.length },
                    { id: 'factures', label: 'Factures et règlements', icon: ReceiptText, count: factures.length },
                ]} />
                <div id={`panel-${activeTab}`} role="tabpanel" aria-labelledby={`tab-${activeTab}`}>
                    {activeTab === 'prestataires' && <CardBody className="space-y-3">
                        {(prestataires.length > 0 || canWrite) && <div className="flex flex-wrap items-center justify-between gap-2">
                            {prestataires.length > 0 && <div className="w-full max-w-52">
                                <Input type="search" value={recherchePrestataire} onChange={e => setRecherchePrestataire(e.target.value)}
                                    placeholder="Rechercher…" aria-label="Rechercher un prestataire" iconLeft={<Search size={14} aria-hidden="true" />} />
                            </div>}{canWrite && <Button type="button" size="sm" iconLeft={<Plus size={15} />} onClick={() => setModal('prestataire')}>Ajouter un prestataire</Button>}
                        </div>}
                        {prestataires.length === 0 ? <EmptyState compact bordered={false} icon={Users} title="Aucun prestataire" description="Créez un dossier avec son NIF pour commencer."
                            actionLabel={canWrite ? 'Nouveau prestataire' : undefined} onAction={canWrite ? () => setModal('prestataire') : undefined} />
                            : prestatairesFiltres.length === 0 ? <EmptyState compact bordered={false} icon={Users} title="Aucun résultat" description="Aucun prestataire ne correspond à cette recherche." />
                            : prestatairesFiltres.map(p => <Card key={p.id_prestataire} shadow="none" padding className="border-l-4 border-l-[var(--color-brand-500)]">
                                <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><div className="flex min-w-0 items-start gap-3"><div className="flex h-9 w-9 shrink-0 items-center justify-center border border-[var(--color-brand-500)]/20 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-700)]"><Users size={17} /></div><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><strong className="text-sm">{p.nom}</strong><StatusBadge status={p.est_actif ? 'ACTIF' : 'FALSE'} /></div><div className="mt-1 flex flex-wrap gap-x-3 gap-y-1 text-xs text-[var(--color-fg-muted)]"><span>NIF {p.nif}</span>{p.stat && <span>STAT {p.stat}</span>}{p.email && <span>{p.email}</span>}</div></div></div>
                                <div className="flex items-center gap-2 sm:pl-12">{canWrite && p.est_actif && <Button type="button" variant="outline" size="sm" onClick={() => {
                                    setContrat(previous => ({ ...previous, idPrestataire: String(p.id_prestataire) })); setModal('contrat');
                                }}>Créer un contrat</Button>}</div></div>
                            </Card>)}
                    </CardBody>}

            {canWrite && <>
                <FormModal isOpen={modal === 'prestataire'} onClose={fermerFormulaire} onSubmit={ajouterPrestataire}
                    title="Nouveau prestataire indépendant" icon={<Users size={17} />} size="xl" loading={busy} error={formError} submitLabel="Enregistrer le prestataire" noValidate>
                    <p className="text-xs text-[var(--color-fg-muted)]">À utiliser pour tout fournisseur indépendant de services, consultant ou non. Reprenez les informations figurant sur sa facture.</p>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Input label="Nom ou raison sociale" required error={fieldErrors.nom} placeholder="Nom légal figurant sur la facture" value={prestataire.nom} onChange={e => updateField(setPrestataire, 'nom', e.target.value)} />
                        <Input label="NIF" required error={fieldErrors.nif} placeholder="NIF du prestataire" hint="Vérifiez-le sur les documents du fournisseur." value={prestataire.nif} onChange={e => updateField(setPrestataire, 'nif', e.target.value)} />
                        <Input label="STAT" placeholder="Numéro STAT, si disponible" value={prestataire.stat} onChange={e => setPrestataire({ ...prestataire, stat: e.target.value })} />
                        <Input label="E-mail" type="email" placeholder="contact@prestataire.mg" value={prestataire.email} onChange={e => setPrestataire({ ...prestataire, email: e.target.value })} />
                    </div>
                    <Input label="Adresse" placeholder="Adresse professionnelle" value={prestataire.adresse} onChange={e => setPrestataire({ ...prestataire, adresse: e.target.value })} />
                </FormModal>
                <FormModal isOpen={modal === 'contrat'} onClose={fermerFormulaire} onSubmit={ajouterContrat}
                    title="Nouveau contrat de prestation" icon={<FileCheck2 size={17} />} size="2xl" loading={busy} error={formError}
                    submitLabel="Enregistrer le contrat" noValidate>
                    <p className="text-xs text-[var(--color-fg-muted)]">Ce dossier rattache une mission indépendante au prestataire et servira de référence aux factures. Il ne crée pas un contrat de travail ni un bulletin. Enregistrez la mission, puis joignez le contrat de prestation signé en PDF dans l'onglet Contrats.</p>
                    <SearchSelect label="Prestataire" required error={fieldErrors.idPrestataire} value={contrat.idPrestataire} onChange={value => updateField(setContrat, 'idPrestataire', value)}
                        options={prestataires.filter(p => p.est_actif).map(p => ({ value: String(p.id_prestataire), label: `${p.nom} · ${p.nif}` }))}
                        hint={prestataires.length === 0 ? 'Créez d’abord un dossier prestataire.' : null} />
                    <Input label="Référence du contrat" required error={fieldErrors.reference} placeholder="Ex. PREST-2026-001" value={contrat.reference} onChange={e => updateField(setContrat, 'reference', e.target.value)} />
                    <Input label="Objet" required error={fieldErrors.objet} placeholder="Ex. mission de conseil, maintenance ou transport" value={contrat.objet} onChange={e => updateField(setContrat, 'objet', e.target.value)} />
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Input label="Début" type="date" required error={fieldErrors.dateDebut} value={contrat.dateDebut} onChange={e => updateField(setContrat, 'dateDebut', e.target.value)} />
                        <Input label="Fin éventuelle" type="date" min={contrat.dateDebut} error={fieldErrors.dateFin} value={contrat.dateFin} onChange={e => updateField(setContrat, 'dateFin', e.target.value)} />
                    </div>
                    <MoneyInput label="Tarif convenu (Ar)" min={0} maxDecimals={2} error={fieldErrors.tarifConvenu} placeholder="Montant prévu au contrat, si fixé" hint="Ce montant n'est pas un salaire calculé par la paie." value={contrat.tarifConvenu} onValueChange={value => updateField(setContrat, 'tarifConvenu', value)} />
                </FormModal>
                <FormModal isOpen={modal === 'facture'} onClose={fermerFormulaire} onSubmit={ajouterFacture}
                    title="Nouvelle facture prestataire" icon={<ReceiptText size={17} />} size="xl" loading={busy} error={formError}
                    submitLabel="Créer le brouillon" noValidate>
                    <p className="text-xs text-[var(--color-fg-muted)]">Saisissez le montant de la facture reçue. Le système ne calcule ni salaire ni retenue ISI générale. Le contrat PDF et la facture PDF sont requis avant validation.</p>
                    <SearchSelect label="Contrat" required error={fieldErrors.idContratPrestataire} value={facture.idContratPrestataire} onChange={value => updateField(setFacture, 'idContratPrestataire', value)}
                        options={contrats.map(c => ({ value: String(c.id_contrat_prestataire), label: `${c.prestataire} · ${c.reference}${c.chemin_piece ? '' : ' · PDF manquant'}` }))}
                        hint={contrats.length === 0 ? 'Créez d’abord un contrat de prestation.' : null} />
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                        <Input label="Référence facture" required error={fieldErrors.reference} placeholder="Numéro indiqué sur la facture PDF" value={facture.reference} onChange={e => updateField(setFacture, 'reference', e.target.value)} />
                        <Input label="Date de facture" type="date" required error={fieldErrors.dateFacture} value={facture.dateFacture} onChange={e => updateField(setFacture, 'dateFacture', e.target.value)} />
                    </div>
                    <MoneyInput label="Montant de la facture (Ar)" required min={0.01} maxDecimals={2} error={fieldErrors.montantFacture} placeholder="Montant à régler selon la facture" hint="Montant saisi depuis la facture ; aucune paie ni retenue automatique." value={facture.montant} onValueChange={value => updateField(setFacture, 'montant', value)} />
                </FormModal>
            </>}

            <FormModal isOpen={modal === 'annuler'} onClose={fermerFormulaire}
                onSubmit={async e => {
                    e.preventDefault();
                    const id = selectedFacture?.id_facture_prestataire;
                    if (!annulations[id]?.trim()) { setFieldErrors({ motif: 'Le motif d’annulation est obligatoire.' }); return; }
                    const ok = await agir(() => apiClient.post(`v1/prestataires/factures/${id}/annuler`, { motif: annulations[id]?.trim() }),
                        'Brouillon annulé. Vous pouvez créer une facture corrigée.', { form: true });
                    if (ok) setModal(null);
                }} title={`Annuler le brouillon · ${selectedFacture?.reference || ''}`}
                icon={<ReceiptText size={17} />} size="lg" loading={busy} error={formError} submitLabel="Confirmer l'annulation"
                noValidate>
                <p className="text-xs text-[var(--color-fg-muted)]">Le brouillon restera dans l'historique. Vous pourrez créer une facture corrigée.</p>
                <Input label="Motif d'annulation" required error={fieldErrors.motif} placeholder="Ex. erreur de référence ou de montant" value={annulations[selectedFacture?.id_facture_prestataire] || ''}
                    onChange={e => { setAnnulations(previous => ({ ...previous, [selectedFacture.id_facture_prestataire]: e.target.value })); if (e.target.value.trim()) setFieldErrors(previous => ({ ...previous, motif: undefined })); setFormError(''); }} />
            </FormModal>

            <FormModal isOpen={modal === 'payer'} onClose={fermerFormulaire}
                onSubmit={async e => {
                    e.preventDefault();
                    if (!validerPaiement()) return;
                    const id = selectedFacture?.id_facture_prestataire;
                    const ok = await agir(() => apiClient.post(`v1/prestataires/factures/${id}/payer`, paiements[id] || { datePaiement: today(), referencePaiement: '' }),
                        'Paiement enregistré.', { form: true });
                    if (ok) setModal(null);
                }} title={`Paiement · ${selectedFacture?.reference || ''}`}
                icon={<ReceiptText size={17} />} size="xl" loading={busy} error={formError} submitLabel="Enregistrer le paiement"
                noValidate>
                <p className="text-xs text-[var(--color-fg-muted)]">Consignez uniquement un paiement déjà effectué. Cette action ne déclenche aucun virement.</p>
                <Input label="Date réelle du paiement" type="date" required min={selectedFacture?.date_facture} max={today()} error={fieldErrors.datePaiement}
                    value={paiements[selectedFacture?.id_facture_prestataire]?.datePaiement || today()}
                    onChange={e => {
                        setPaiements(previous => ({ ...previous, [selectedFacture.id_facture_prestataire]: {
                            ...previous[selectedFacture.id_facture_prestataire], datePaiement: e.target.value,
                        } }));
                        setFieldErrors(previous => ({ ...previous, datePaiement: undefined }));
                        setFormError('');
                    }} />
                <Input label="Référence du paiement" required error={fieldErrors.referencePaiement} placeholder="Référence du virement ou du reçu" hint="Saisissez une preuve d'un règlement déjà effectué." value={paiements[selectedFacture?.id_facture_prestataire]?.referencePaiement || ''}
                    onChange={e => {
                        setPaiements(previous => ({ ...previous, [selectedFacture.id_facture_prestataire]: {
                            ...previous[selectedFacture.id_facture_prestataire], datePaiement: previous[selectedFacture.id_facture_prestataire]?.datePaiement || today(), referencePaiement: e.target.value,
                        } }));
                        if (e.target.value.trim()) setFieldErrors(previous => ({ ...previous, referencePaiement: undefined }));
                        setFormError('');
                    }} />
            </FormModal>

            {activeTab === 'contrats' && <CardBody className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-xs text-[var(--color-fg-muted)]">Le contrat décrit la mission du prestataire indépendant. Joignez son PDF signé, puis rattachez-y chaque facture. {contratsDocumentes.length} contrat(s) avec PDF sur {contrats.length}.</p>
                </div>
                {contrats.length > 0 && <div className="flex flex-col gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 sm:flex-row sm:items-center">
                    <div className="w-full sm:max-w-xs"><Input type="search" value={rechercheContrat} onChange={e => setRechercheContrat(e.target.value)} placeholder="Prestataire, référence, mission…" aria-label="Rechercher un contrat" iconLeft={<Search size={14} aria-hidden="true" />} /></div>
                    <div className="w-full sm:w-44"><Select value={filtreContratPdf} onChange={e => setFiltreContratPdf(e.target.value)} aria-label="Filtrer les contrats par pièce PDF"><option value="tous">Tous les contrats</option><option value="sans-pdf">PDF manquant</option><option value="avec-pdf">PDF joint</option></Select></div>
                    {(rechercheContrat || filtreContratPdf !== 'tous') && <Button type="button" variant="ghost" size="xs" iconLeft={<X size={13} />} onClick={() => { setRechercheContrat(''); setFiltreContratPdf('tous'); }}>Effacer</Button>}
                    <span className="ml-auto text-xs text-[var(--color-fg-muted)]">{contratsFiltres.length} / {contrats.length}</span>
                </div>}
                {contrats.length === 0 && <EmptyState compact bordered={false} icon={FileCheck2} title="Aucun contrat" description={prestataires.length ? 'Enregistrez la mission du prestataire, puis joignez le contrat signé.' : 'Commencez par créer un prestataire avec son NIF.'}
                    actionLabel={canWrite ? (prestataires.length ? 'Ajouter un contrat' : 'Créer un prestataire') : undefined}
                    onAction={canWrite ? () => setModal(prestataires.length ? 'contrat' : 'prestataire') : undefined} actionIcon={<Plus size={14} />} />}
                {contrats.length > 0 && contratsFiltres.length === 0 && <EmptyState compact bordered={false} icon={Search} title="Aucun contrat trouvé" description="Modifiez les critères de recherche ou de pièce jointe." />}
                {contratsFiltres.map(c => <Card key={c.id_contrat_prestataire} shadow="none" padding className="space-y-3.5 border-l-4 border-l-[var(--color-brand-500)]">
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"><div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><strong className="text-sm">{c.prestataire}</strong><StatusBadge status={c.chemin_piece ? 'ACTIF' : 'EN_ATTENTE'} label={c.chemin_piece ? 'PDF joint' : 'PDF manquant'} /></div><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Contrat {c.reference} <span className="px-1">·</span> {c.objet}</p><p className="mt-1 text-xs text-[var(--color-fg-subtle)]">Du {fmtDate(c.date_debut)} au {c.date_fin ? fmtDate(c.date_fin) : 'sans date de fin'}</p></div><div className="sm:text-right"><p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Tarif convenu</p><strong className="text-base">{c.tarif_convenu != null ? formatMoneyAr(c.tarif_convenu) : 'Non renseigné'}</strong></div></div>
                    <div className="flex flex-wrap items-center gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                        {c.chemin_piece && <Button variant="outline" size="sm" onClick={() => telechargerPiece('contrats', c.id_contrat_prestataire)}>Télécharger le contrat signé</Button>}
                        {canWrite && !c.chemin_piece && <PdfAttach label="Choisir le PDF du contrat" busy={busy}
                            filename={nomPiecePrestataire('contrat-prestation', c.reference, c.date_debut)}
                            onUpload={file => joindreContrat(c.id_contrat_prestataire, file)} />}
                        {canWrite && <Button type="button" variant="ghost" size="sm" onClick={() => {
                            setFacture(previous => ({ ...previous, idContratPrestataire: String(c.id_contrat_prestataire) })); setModal('facture');
                        }}>Créer une facture</Button>}
                    </div>
                </Card>)}
            </CardBody>}

            {activeTab === 'factures' && <CardBody className="space-y-3">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <p className="text-xs text-[var(--color-fg-muted)]">{facturesEnAttente.length} facture(s) à compléter ou à régler. « Marquer payée » consigne un paiement déjà effectué et ne déclenche aucun virement.</p>
                    {canWrite && factures.length > 0 && <Button type="button" size="sm" iconLeft={<Plus size={15} />} onClick={() => setModal(contrats.length ? 'facture' : prestataires.length ? 'contrat' : 'prestataire')}>Ajouter une facture</Button>}
                </div>
                {factures.length > 0 && <div className="flex flex-col gap-2 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] p-3 sm:flex-row sm:items-center">
                    <div className="w-full sm:max-w-xs"><Input type="search" value={rechercheFacture} onChange={e => setRechercheFacture(e.target.value)} placeholder="Prestataire, facture, contrat…" aria-label="Rechercher une facture" iconLeft={<Search size={14} aria-hidden="true" />} /></div>
                    <div className="w-full sm:w-44"><Select value={filtreFactureStatut} onChange={e => setFiltreFactureStatut(e.target.value)} aria-label="Filtrer les factures par statut"><option value="tous">Tous les statuts</option><option value="BROUILLON">Brouillon</option><option value="VALIDEE">Validée</option><option value="PAYEE">Payée</option><option value="ANNULEE">Annulée</option></Select></div>
                    <div className="w-full sm:w-44"><Select value={filtreFacturePieces} onChange={e => setFiltreFacturePieces(e.target.value)} aria-label="Filtrer les factures par pièces justificatives"><option value="toutes">Toutes les pièces</option><option value="incompletes">Pièces manquantes</option><option value="completes">Pièces complètes</option></Select></div>
                    {(rechercheFacture || filtreFactureStatut !== 'tous' || filtreFacturePieces !== 'toutes') && <Button type="button" variant="ghost" size="xs" iconLeft={<X size={13} />} onClick={() => { setRechercheFacture(''); setFiltreFactureStatut('tous'); setFiltreFacturePieces('toutes'); }}>Effacer</Button>}
                    <span className="ml-auto text-xs text-[var(--color-fg-muted)]">{facturesFiltrees.length} / {factures.length}</span>
                </div>}
                {factures.length === 0 && <EmptyState compact bordered={false} icon={ReceiptText} title="Aucune facture" description={contrats.length ? 'Créez une facture liée à un contrat de prestation.' : prestataires.length ? 'Créez d’abord un contrat de prestation.' : 'Commencez par créer un prestataire, puis son contrat.'}
                    actionLabel={canWrite ? (contrats.length ? 'Ajouter une facture' : prestataires.length ? 'Créer un contrat' : 'Créer un prestataire') : undefined}
                    onAction={canWrite ? () => setModal(contrats.length ? 'facture' : prestataires.length ? 'contrat' : 'prestataire') : undefined} actionIcon={<Plus size={14} />} />}
                {factures.length > 0 && facturesFiltrees.length === 0 && <EmptyState compact bordered={false} icon={Search} title="Aucune facture trouvée" description="Modifiez les critères de recherche, de statut ou de pièces." />}
                {facturesFiltrees.map(f => {
                    const id = f.id_facture_prestataire;
                    const contratLie = contrats.find(c => c.id_contrat_prestataire === f.id_contrat_prestataire);
                    const piecesManquantes = [!contratLie?.chemin_piece && 'contrat', !f.chemin_piece && 'facture'].filter(Boolean);
                    return <Card key={id} shadow="none" padding className="space-y-3.5 border-l-4 border-l-[var(--color-brand-500)]">
                        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                            <div className="min-w-0"><div className="flex flex-wrap items-center gap-2"><strong className="text-sm">{f.prestataire}</strong><StatusBadge status={f.statut} /></div><p className="mt-1 text-xs text-[var(--color-fg-muted)]">Facture {f.reference} <span className="px-1">·</span> Contrat {f.contrat_reference}</p><p className="mt-1 text-xs text-[var(--color-fg-subtle)]">Émise le {fmtDate(f.date_facture)}</p></div>
                            <div className="sm:text-right"><p className="text-[10px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">Montant de la facture</p><strong className="text-lg font-bold">{formatMoneyAr(f.montant)}</strong></div>
                        </div>
                        {f.statut === 'BROUILLON' && <div className={`flex flex-wrap items-center gap-2 border px-3 py-2 text-xs ${piecesManquantes.length ? 'border-[var(--color-warning-500)]/30 bg-[var(--color-warning-500)]/5 text-[var(--color-warning-700)]' : 'border-[var(--color-success-500)]/30 bg-[var(--color-success-500)]/5 text-[var(--color-success-700)]'}`}><FileCheck2 size={14} aria-hidden="true" />{piecesManquantes.length ? `À compléter : PDF ${piecesManquantes.join(' et ')} manquant${piecesManquantes.length > 1 ? 's' : ''}.` : 'Pièces justificatives prêtes pour validation.'}</div>}
                        <div className="flex flex-wrap items-center gap-2 border-t border-[var(--color-border-subtle)] pt-3">
                            {f.chemin_piece && <Button variant="outline" size="sm" onClick={() => telechargerPiece('factures', id)}>Voir le PDF</Button>}
                            {canWrite && f.statut === 'BROUILLON' && !f.chemin_piece && <PdfAttach label="Choisir le PDF de facture" busy={busy}
                                filename={nomPiecePrestataire('facture-prestataire', f.reference, f.date_facture)}
                                onUpload={file => joindrePiece(id, file)} />}
                            {canValidate && f.statut === 'BROUILLON' && <Button size="sm" disabled={busy || !f.chemin_piece || !contratLie?.chemin_piece}
                                onClick={() => agir(() => apiClient.post(`v1/prestataires/factures/${id}/valider`), 'Facture validée.')}>Valider</Button>}
                            {canWrite && f.statut === 'BROUILLON' && <Button type="button" variant="ghost" size="sm" onClick={() => { setSelectedFacture(f); setModal('annuler'); }}>Annuler le brouillon</Button>}
                            {canValidate && f.statut === 'VALIDEE' && <Button type="button" size="sm" onClick={() => { setSelectedFacture(f); setModal('payer'); }}>Marquer payée</Button>}
                            {f.statut === 'PAYEE' && <span className="text-xs">Payée le {fmtDate(f.date_paiement)} · {f.reference_paiement}</span>}
                            {f.statut === 'ANNULEE' && <span className="text-xs">Annulée : {f.motif_annulation}</span>}
                        </div>
                    </Card>;
                })}
            </CardBody>}
                </div>
            </Card>
        </div>
    );
}
