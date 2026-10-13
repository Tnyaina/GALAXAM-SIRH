import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Building2, Check, Edit2, MapPin, Plus, Power, ShieldCheck, Eye } from 'lucide-react';
import paieConfigService from '@/features/administration/api/paieConfigService';
import organisationService from '@/features/organization/api/organisationService';
import { fmtDate, fmtDateInput } from '@/utils/dateUtils';
import SideTabs from '@/components/ui/navigation/SideTabs';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import FormModal from '@/components/ui/overlays/FormModal';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import useConfirm from '@/hooks/useConfirm';
import { useToast } from '@/components/ui/feedback/Toast';
import { getErrorMessage } from '@/api/apiClient';


function SiteCotisationFormModal({ isOpen, onClose, onSubmit, formData, setFormData, sites = [], cotisations = [], organismes = [], actionError, submitting = false }) {
    const siteOptions = sites
        .filter((site) => (site.estActif !== false && site.actif !== false) || String(site.idSite) === String(formData.idSite))
        .map((site) => ({
            value: String(site.idSite),
            label: site.libelle,
            sublabel: site.codeSite
        }));

    const cotisationOptions = cotisations
        .filter((cotisation) => !cotisation.portee || cotisation.portee === 'SITE')
        .map((cotisation) => ({
            value: String(cotisation.idTypeCotisation),
            label: `${cotisation.code} - ${cotisation.libelle}`,
            sublabel: `Salarial: ${cotisation.tauxSalarial}%, Patronal: ${cotisation.tauxPatronal}%${cotisation.organismeCode ? ` (${cotisation.organismeCode})` : ''}`
        }));

    const organismeOptions = [
        { value: '', label: '-- Hérité du type de cotisation --' },
        ...organismes.map((org) => ({
            value: String(org.idOrganisme),
            label: `${org.code} - ${org.libelle}`
        }))
    ];

    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title="Associer une Cotisation à un Site"
            icon={<MapPin size={18} />}
            size="lg"
            submitLabel="Associer"
            loading={submitting}
            error={actionError}
        >
            <div className="space-y-4 py-1 min-h-[220px]">
                <SearchSelect
                    label="Site d'Exploitation"
                    required
                    value={formData.idSite}
                    onChange={(val) => setFormData({ ...formData, idSite: val })}
                    options={siteOptions}
                    placeholder="-- Sélectionner un site d'exploitation --"
                />
                <SearchSelect
                    label="Type de Cotisation"
                    required
                    value={formData.idTypeCotisation}
                    onChange={(val) => setFormData({ ...formData, idTypeCotisation: val })}
                    options={cotisationOptions}
                    placeholder="-- Sélectionner un organisme ou cotisation --"
                />
                <SearchSelect
                    label="Organisme Déclaratif (Optionnel)"
                    value={formData.idOrganisme ? String(formData.idOrganisme) : ''}
                    onChange={(val) => setFormData({ ...formData, idOrganisme: val ? parseInt(val, 10) : null })}
                    options={organismeOptions}
                    placeholder="-- Hérité du type de cotisation si non renseigné --"
                />
                <p className="text-[11px] text-[var(--color-fg-muted)]">
                    Par exemple, associer un organisme sanitaire spécifique (ex: AMIT) pour ce site d'exploitation.
                </p>
            </div>
        </FormModal>
    );
}

function CotisationFormModal({ isOpen, onClose, onSubmit, editingCotisation, formData, setFormData, organismes = [], actionError, submitting = false }) {
    const [localError, setLocalError] = useState(null);
    const submit = (event) => {
        event.preventDefault();
        setLocalError(null);
        const salarial = parseFloat(formData.tauxSalarial);
        const patronal = parseFloat(formData.tauxPatronal);
        if (isNaN(salarial) || salarial < 0 || salarial > 100) return setLocalError('Le taux salarial doit être compris entre 0% et 100%.');
        if (isNaN(patronal) || patronal < 0 || patronal > 100) return setLocalError('Le taux patronal doit être compris entre 0% et 100%.');
        if (formData.dateFin && formData.dateDebut && formData.dateFin < formData.dateDebut) return setLocalError('La date de fin ne peut pas être antérieure à la date de début.');
        onSubmit(event);
    };
    const portee = formData.portee || 'SITE';
    const categorie = formData.categorie || 'SANTE';

    const organismeOptions = [
        { value: '', label: '-- Aucun organisme associé --' },
        ...organismes.map((org) => ({
            value: String(org.idOrganisme),
            label: `${org.code} - ${org.libelle}`,
            sublabel: org.estActif ? 'Actif' : 'Inactif'
        }))
    ];

    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={submit}
            title={editingCotisation ? 'Modifier la Cotisation Sociale' : 'Nouvelle Cotisation Sociale'}
            icon={<ShieldCheck size={18} />}
            size="2xl"
            isEdit={!!editingCotisation}
            loading={submitting}
            error={localError || actionError}
        >
            <div className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Input
                        label="Code Cotisation"
                        required
                        disabled={!!editingCotisation}
                        value={formData.code}
                        onChange={(event) => setFormData({ ...formData, code: event.target.value.toUpperCase() })}
                        placeholder="Ex: CNAPS, OSTIE, AMIT"
                    />
                    <SearchSelect
                        label="Catégorie / Nature"
                        required
                        value={categorie}
                        onChange={(val) => {
                            const cat = val;
                            const autoPortee = (cat === 'PREVOYANCE' || cat === 'FORMATION') ? 'ENTREPRISE' : formData.portee;
                            setFormData({ ...formData, categorie: cat, portee: autoPortee });
                        }}
                        options={[
                            { value: 'SANTE', label: 'Santé au Travail' },
                            { value: 'PREVOYANCE', label: 'Prévoyance / Retraite (CNaPS)' },
                            { value: 'FORMATION', label: 'Formation Professionnelle (FMFP)' }
                        ]}
                        placeholder="Sélectionner une catégorie"
                    />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Input
                        label="Libellé Complet"
                        required
                        value={formData.libelle}
                        onChange={(event) => setFormData({ ...formData, libelle: event.target.value })}
                        placeholder="Ex: Caisse Nationale de Prévoyance Sociale"
                    />
                    <SearchSelect
                        label="Organisme Déclaratif Associé"
                        value={formData.idOrganisme ? String(formData.idOrganisme) : ''}
                        onChange={(val) => setFormData({ ...formData, idOrganisme: val ? parseInt(val, 10) : null })}
                        options={organismeOptions}
                        placeholder="Associer à un organisme (CNaPS, OSTIE...)"
                    />
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 items-start">
                    <div>
                        <label className="block text-xs font-bold uppercase tracking-wide text-[var(--color-fg-muted)] mb-1">
                            Périmètre d'application <span className="text-[var(--color-danger-500)]">*</span>
                        </label>
                        <div className="flex gap-2">
                            {[['SITE', MapPin, 'Par site'], ['ENTREPRISE', Building2, 'Entreprise entière']].map(([value, Icon, label]) => (
                                <button
                                    key={value}
                                    type="button"
                                    onClick={() => setFormData({ ...formData, portee: value })}
                                    className={`flex-1 flex items-center justify-center gap-1.5 px-2.5 py-1.5 border text-xs font-semibold transition-all cursor-pointer ${
                                        portee === value
                                            ? 'bg-[var(--color-success-500)]/10 border-[var(--color-success-500)]/40 text-[var(--color-success-600)]'
                                            : 'border-[var(--color-border-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)]'
                                    }`}
                                >
                                    <Icon size={13} />
                                    {label}
                                </button>
                            ))}
                        </div>
                        <p className="text-[10px] text-[var(--color-fg-muted)] mt-1 truncate">
                            {portee === 'ENTREPRISE' ? 'Applicable à tous les salariés.' : 'Affectation par site requise.'}
                        </p>
                    </div>

                    <div className="grid grid-cols-2 gap-2">
                        <Input
                            label="Part Salariale (%)"
                            type="number"
                            min="0"
                            max="100"
                            step="0.01"
                            required
                            value={formData.tauxSalarial}
                            onChange={(event) => setFormData({ ...formData, tauxSalarial: parseFloat(event.target.value) || 0 })}
                        />
                        <Input
                            label="Part Patronale (%)"
                            type="number"
                            min="0"
                            max="100"
                            step="0.01"
                            required
                            value={formData.tauxPatronal}
                            onChange={(event) => setFormData({ ...formData, tauxPatronal: parseFloat(event.target.value) || 0 })}
                        />
                    </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <Input
                        label="Date Début (Prise d'effet)"
                        type="date"
                        required
                        value={fmtDateInput(formData.dateDebut)}
                        onChange={(event) => setFormData({ ...formData, dateDebut: event.target.value })}
                    />
                    <Input
                        label="Date Fin (Optionnel)"
                        type="date"
                        min={fmtDateInput(formData.dateDebut)}
                        value={fmtDateInput(formData.dateFin)}
                        onChange={(event) => setFormData({ ...formData, dateFin: event.target.value })}
                    />
                </div>
            </div>
        </FormModal>
    );
}

function SiteCotisationsList({ siteCotisations = [], onOpenCreate, onToggleStatus }) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <MapPin size={18} className="text-[var(--color-brand-500)]" />
                        Cotisations rattachées aux sites ({siteCotisations.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Organismes sanitaires et prévoyances affectés par site géographique.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Associer un site
                </Button>
            </div>
            {siteCotisations.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={MapPin}
                        title="Aucune association"
                        description="Aucune association site–cotisation n'a été enregistrée."
                        actionLabel="Associer un site"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table bordered={false}>
                    <Thead>
                        <Th>Nom du site</Th>
                        <Th>Cotisation rattachée</Th>
                        <Th>Organisme</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {siteCotisations.map((item) => (
                            <Tr key={item.idSiteCotisation}>
                                <Td className="font-bold text-[var(--color-fg)]">
                                    {item.siteLibelle} {item.siteCode ? `(${item.siteCode})` : ''}
                                </Td>
                                <Td isData className="font-semibold text-[var(--color-brand-500)]">
                                    {item.cotisationCode} - {item.cotisationLibelle}
                                </Td>
                                <Td>
                                    {item.organismeCode ? (
                                        <Badge variant="primary" size="sm">{item.organismeCode}</Badge>
                                    ) : (
                                        <span className="text-xs text-[var(--color-fg-muted)] italic">Hérité</span>
                                    )}
                                </Td>
                                <Td>
                                    <Badge variant={item.estActif ? 'success' : 'default'} size="sm" dot>
                                        {item.estActif ? 'Actif' : 'Désactivé'}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <button
                                        onClick={() => onToggleStatus(item)}
                                        className={`p-1.5 rounded-none cursor-pointer ${item.estActif ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10' : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'}`}
                                        title={item.estActif ? "Désactiver l'association" : "Réactiver l'association"}
                                    >
                                        {item.estActif ? <Power size={16} /> : <Check size={16} />}
                                    </button>
                                </Td>
                            </Tr>
                        ))}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}

function CotisationsList({ cotisations = [], onOpenCreate, onOpenEdit, onToggleStatus }) {
    const categorieBadge = (cat) => {
        if (cat === 'PREVOYANCE') return <Badge variant="primary" size="sm">Prévoyance</Badge>;
        if (cat === 'FORMATION') return <Badge variant="warning" size="sm">Formation</Badge>;
        return <Badge variant="success" size="sm">Santé</Badge>;
    };

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <ShieldCheck size={18} className="text-[var(--color-brand-500)]" />
                        Règles et cotisations sociales ({cotisations.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Taux salariaux et patronaux applicables avec rattachement aux organismes déclaratifs.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvelle cotisation
                </Button>
            </div>
            {cotisations.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={ShieldCheck}
                        title="Aucune cotisation configurée"
                        description="Ajoutez les règles et cotisations sociales pour la paie."
                        actionLabel="Nouvelle cotisation"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table bordered={false}>
                    <Thead>
                        <Th>Code</Th>
                        <Th>Libellé</Th>
                        <Th>Organisme</Th>
                        <Th>Catégorie</Th>
                        <Th>Périmètre</Th>
                        <Th className="text-right">Salarial</Th>
                        <Th className="text-right">Patronal</Th>
                        <Th>Période</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {cotisations.map((item) => {
                            const active = item.estActif !== false;
                            return (
                                <Tr key={item.idTypeCotisation}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">{item.code}</Td>
                                    <Td className="font-semibold text-[var(--color-fg)]">{item.libelle}</Td>
                                    <Td>
                                        {item.organismeCode ? (
                                            <Badge variant="primary" size="sm">{item.organismeCode}</Badge>
                                        ) : (
                                            <span className="text-xs text-[var(--color-fg-muted)] italic">Non lié</span>
                                        )}
                                    </Td>
                                    <Td>{categorieBadge(item.categorie)}</Td>
                                    <Td><Badge variant={item.portee === 'ENTREPRISE' ? 'primary' : 'success'} size="sm">{item.portee === 'ENTREPRISE' ? 'Entreprise' : 'Par site'}</Badge></Td>
                                    <Td isData className="text-right">{item.tauxSalarial}%</Td>
                                    <Td isData className="text-right">{item.tauxPatronal}%</Td>
                                    <Td isData muted className="text-xs">Du {fmtDate(item.dateDebut)} {item.dateFin ? `au ${fmtDate(item.dateFin)}` : '(actif)'}</Td>
                                    <Td><Badge variant={active ? 'success' : 'default'} size="sm" dot>{active ? 'Actif' : 'Désactivé'}</Badge></Td>
                                    <Td className="text-right">
                                        <button onClick={() => onOpenEdit(item)} className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer" title="Modifier"><Edit2 size={16} /></button>
                                        <button onClick={() => onToggleStatus(item)} className={`p-1.5 rounded-none cursor-pointer ${active ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10' : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'}`} title={active ? 'Désactiver' : 'Réactiver'}>{active ? <Power size={16} /> : <Check size={16} />}</button>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}

function OrganismesSociauxList({ organismes = [], onOpenCreate, onToggleStatus }) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden space-y-4">
            <div className="p-4 flex justify-between items-center border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Building2 size={18} className="text-[var(--color-brand-500)]" />
                        Organismes sociaux déclaratifs ({organismes.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Référentiel des caisses et organismes pour le regroupement dynamique des états et consolidations sociales.
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Nouvel organisme
                </Button>
            </div>
            {organismes.length === 0 ? (
                <div className="p-8">
                    <EmptyState
                        icon={Building2}
                        title="Aucun organisme configuré"
                        description="Ajoutez les caisses et organismes déclaratifs (ex: CNaPS, OSTIE, AMIT, FMFP)."
                        actionLabel="Nouvel organisme"
                        onAction={onOpenCreate}
                    />
                </div>
            ) : (
                <Table bordered={false}>
                    <Thead>
                        <Th>Code</Th>
                        <Th>Libellé officiel</Th>
                        <Th>Statut</Th>
                        <Th className="text-right">Actions</Th>
                    </Thead>
                    <Tbody>
                        {organismes.map((item) => {
                            const active = item.estActif !== false;
                            return (
                                <Tr key={item.idOrganisme}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">{item.code}</Td>
                                    <Td className="font-semibold text-[var(--color-fg)]">{item.libelle}</Td>
                                    <Td>
                                        <Badge variant={active ? 'success' : 'default'} size="sm" dot>
                                            {active ? 'Actif' : 'Désactivé'}
                                        </Badge>
                                    </Td>
                                    <Td className="text-right">
                                        <button
                                            onClick={() => onToggleStatus(item)}
                                            className={`p-1.5 rounded-none cursor-pointer ${active ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10' : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'}`}
                                            title={active ? 'Désactiver' : 'Réactiver'}
                                        >
                                            {active ? <Power size={16} /> : <Check size={16} />}
                                        </button>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            )}
        </div>
    );
}

function OrganismeFormModal({ isOpen, onClose, onSubmit, formData, setFormData, actionError, submitting = false }) {
    return (
        <FormModal
            isOpen={isOpen}
            onClose={onClose}
            onSubmit={onSubmit}
            title="Nouvel Organisme Social Déclaratif"
            icon={<Building2 size={18} />}
            size="md"
            submitLabel="Enregistrer"
            loading={submitting}
            error={actionError}
        >
            <div className="space-y-3 py-1">
                <Input
                    label="Code Organisme"
                    required
                    value={formData.code}
                    onChange={(e) => setFormData({ ...formData, code: e.target.value.toUpperCase() })}
                    placeholder="Ex: AMIT, SMIE, OSTIE"
                />
                <Input
                    label="Libellé Officiel"
                    required
                    value={formData.libelle}
                    onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                    placeholder="Ex: Association Médicale Interentreprises de Tananarive"
                />
            </div>
        </FormModal>
    );
}

export default function CotisationsIrsaTab() {
    // Contexte, navigation et confirmation
    const [searchParams, setSearchParams] = useSearchParams();
    const requestedSubSection = searchParams.get('subtab');
    const subSection = requestedSubSection === 'irsa'
        ? 'cotisations'
        : ['cotisations', 'site_cotisations', 'organismes'].includes(requestedSubSection) ? requestedSubSection : 'cotisations';

    const setSubSection = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'cotisations_irsa');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const todayStr = fmtDateInput(new Date());

    // État local : données, filtres et modales.
    const [cotisations, setCotisations] = useState([]);
    const [siteCotisations, setSiteCotisations] = useState([]);
    const [sites, setSites] = useState([]);
    const [organismes, setOrganismes] = useState([]);

    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();
    const [showInactive, setShowInactive] = useState(false);

    const { confirm, ConfirmModal } = useConfirm();

    // Modale Cotisation
    const [showCotisationModal, setShowCotisationModal] = useState(false);
    const [editingCotisation, setEditingCotisation] = useState(null);
    const [cotisationFormData, setCotisationFormData] = useState({
        code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, portee: 'SITE', categorie: 'SANTE', idOrganisme: null, dateDebut: todayStr, dateFin: ''
    });

    // Modale Site Cotisation
    const [showSiteCotisationModal, setShowSiteCotisationModal] = useState(false);
    const [siteCotisationFormData, setSiteCotisationFormData] = useState({
        idSite: '', idTypeCotisation: '', idOrganisme: null
    });

    // Modale Organisme Social
    const [showOrganismeModal, setShowOrganismeModal] = useState(false);
    const [organismeFormData, setOrganismeFormData] = useState({
        code: '', libelle: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [cotData, siteCotData, sitesData, orgData] = await Promise.all([
                paieConfigService.getCotisations(),
                paieConfigService.getSiteCotisations(),
                organisationService.getSites(),
                paieConfigService.getOrganismesSociaux()
            ]);
            setCotisations(cotData);
            setSiteCotisations(siteCotData);
            setSites(sitesData);
            setOrganismes(orgData || []);
        } catch (err) {
            setError("Erreur lors du chargement des données. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    // Effet : charge les cotisations et leurs déclinaisons par site.
    useEffect(() => {
        loadData();
    }, []);

    // Handlers Cotisation
    const handleOpenCreateCotisation = () => {
        setEditingCotisation(null);
        setCotisationFormData({ code: '', libelle: '', tauxSalarial: 0, tauxPatronal: 0, portee: 'SITE', categorie: 'SANTE', idOrganisme: null, dateDebut: todayStr, dateFin: '' });
        setActionError(null);
        setShowCotisationModal(true);
    };

    const handleOpenEditCotisation = (c) => {
        setEditingCotisation(c);
        setCotisationFormData({
            code: c.code,
            libelle: c.libelle,
            tauxSalarial: c.tauxSalarial,
            tauxPatronal: c.tauxPatronal,
            portee: c.portee || 'SITE',
            categorie: c.categorie || 'SANTE',
            idOrganisme: c.idOrganisme || null,
            dateDebut: fmtDateInput(c.dateDebut) || todayStr,
            dateFin: fmtDateInput(c.dateFin) || ''
        });
        setActionError(null);
        setShowCotisationModal(true);
    };

    const handleSaveCotisation = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
        try {
            if (editingCotisation) {
                await paieConfigService.updateCotisation(editingCotisation.idTypeCotisation, cotisationFormData);
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.code}" modifiée.`, type: 'success' });
            } else {
                await paieConfigService.createCotisation(cotisationFormData);
                toast({ title: 'Succès', message: `Cotisation "${cotisationFormData.code}" créée.`, type: 'success' });
            }
            setShowCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de sauvegarde."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Site Cotisation
    const handleOpenCreateSiteCotisation = () => {
        setSiteCotisationFormData({ idSite: '', idTypeCotisation: '', idOrganisme: null });
        setActionError(null);
        setShowSiteCotisationModal(true);
    };

    const handleSaveSiteCotisation = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
        try {
            await paieConfigService.createSiteCotisation({
                idSite: parseInt(siteCotisationFormData.idSite, 10),
                idTypeCotisation: parseInt(siteCotisationFormData.idTypeCotisation, 10),
                idOrganisme: siteCotisationFormData.idOrganisme ? parseInt(siteCotisationFormData.idOrganisme, 10) : null
            });
            toast({ title: 'Succès', message: 'Cotisation rattachée au site.', type: 'success' });
            setShowSiteCotisationModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur de rattachement."));
        } finally {
            setSubmitting(false);
        }
    };

    // Handlers Organisme Social
    const handleOpenCreateOrganisme = () => {
        setOrganismeFormData({ code: '', libelle: '' });
        setActionError(null);
        setShowOrganismeModal(true);
    };

    const handleSaveOrganisme = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setActionError(null);
        setSubmitting(true);
        try {
            await paieConfigService.createOrganismeSocial(organismeFormData);
            toast({ title: 'Succès', message: `Organisme "${organismeFormData.code}" enregistré.`, type: 'success' });
            setShowOrganismeModal(false);
            loadData();
        } catch (err) {
            setActionError(getErrorMessage(err, "Erreur lors de l'enregistrement de l'organisme."));
        } finally {
            setSubmitting(false);
        }
    };

    const requestToggleOrganismeStatus = (item) => {
        const isActif = item.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} l'organisme social`,
            message: `Êtes-vous sûr de vouloir ${actionText.toLowerCase()} l'organisme "${item.libelle}" (${item.code}) ?`,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    await paieConfigService.setOrganismeSocialActif(item.idOrganisme, !isActif);
                    toast({
                        title: 'Statut mis à jour',
                        message: `L'organisme "${item.code}" a été ${isActif ? 'désactivé' : 'activé'}.`,
                        type: 'success'
                    });
                    await loadData();
                } catch (err) {
                    setError("Erreur lors de la modification du statut de l'organisme.");
                    console.error(err);
                }
            }
        });
    };

    const requestToggleCotisationStatus = (item) => {
        const isActif = item.estActif !== false;
        const actionText = isActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} la cotisation`,
            message: isActif
                ? `Êtes-vous sûr de vouloir désactiver la cotisation "${item.libelle}" (${item.code}) ?\n\nATTENTION : Cela désactivera automatiquement toutes ses instances rattachées aux sites d'exploitation.`
                : `Êtes-vous sûr de vouloir réactiver la cotisation "${item.libelle}" (${item.code}) ?`,
            confirmLabel: actionText,
            variant: isActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    if (isActif) {
                        await paieConfigService.deactivateCotisation(item.idTypeCotisation);
                        toast({
                            title: 'Cotisation désactivée',
                            message: `La cotisation "${item.code}" et ses rattachements par site ont été désactivés.`,
                            type: 'success'
                        });
                    } else {
                        await paieConfigService.activateCotisation(item.idTypeCotisation);
                        toast({
                            title: 'Cotisation réactivée',
                            message: `La cotisation "${item.code}" a été réactivée avec succès.`,
                            type: 'success'
                        });
                    }
                    await loadData();
                } catch (err) {
                    setError("Erreur lors de la modification du statut de la cotisation.");
                    console.error(err);
                }
            }
        });
    };

    const requestToggleSiteCotisationStatus = (item) => {
        const actionText = item.estActif ? 'Désactiver' : 'Activer';
        confirm({
            title: `${actionText} l'association`,
            message: `Êtes-vous sûr de vouloir ${actionText.toLowerCase()} la cotisation "${item.cotisationLibelle}" pour le site "${item.siteLibelle}" ?`,
            confirmLabel: actionText,
            variant: item.estActif ? 'danger' : 'primary',
            onConfirm: async () => {
                try {
                    if (item.estActif) {
                        await paieConfigService.deactivateSiteCotisation(item.idSiteCotisation);
                        toast({ title: 'Succès', message: `Association "${item.siteLibelle} - ${item.cotisationLibelle}" désactivée.`, type: 'success' });
                    } else {
                        await paieConfigService.activateSiteCotisation(item.idSiteCotisation);
                        toast({ title: 'Succès', message: `Association "${item.siteLibelle} - ${item.cotisationLibelle}" réactivée.`, type: 'success' });
                    }
                    setSiteCotisations(prev =>
                        prev.map(sc =>
                            sc.idSiteCotisation === item.idSiteCotisation
                                ? { ...sc, estActif: !item.estActif }
                                : sc
                        )
                    );
                } catch (err) {
                    setError("Erreur lors de la modification du statut.");
                    console.error(err);
                }
            }
        });
    };

    const filteredSiteCotisations = showInactive
        ? siteCotisations
        : siteCotisations.filter(sc => sc.estActif !== false);

    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={5} columns={5} />
            </div>
        );
    }

    // Rendu
    return (
        <SideTabs
            tabs={[
                { id: 'cotisations', label: 'Cotisations sociales', icon: <ShieldCheck size={16} />, count: cotisations.length },
                { id: 'site_cotisations', label: 'Rattachement par site', icon: <MapPin size={16} />, count: filteredSiteCotisations.length },
                { id: 'organismes', label: 'Organismes déclaratifs', icon: <Building2 size={16} />, count: organismes.length }
            ]}
            activeTab={subSection}
            onChange={setSubSection}
            className="space-y-6"
        >
            {error && <Alert type="error" message={error} />}

            <div className="flex justify-end">
                {subSection === 'site_cotisations' && (
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1 hover:bg-[var(--color-bg-subtle)] rounded-none">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 accent-[var(--color-brand-500)] rounded-none"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                )}
            </div>

            {(subSection === 'cotisations' || subSection === 'irsa') && (
                <CotisationsList
                    cotisations={cotisations}
                    onOpenCreate={handleOpenCreateCotisation}
                    onOpenEdit={handleOpenEditCotisation}
                    onToggleStatus={requestToggleCotisationStatus}
                />
            )}

            {subSection === 'site_cotisations' && (
                <SiteCotisationsList
                    siteCotisations={filteredSiteCotisations}
                    onOpenCreate={handleOpenCreateSiteCotisation}
                    onToggleStatus={requestToggleSiteCotisationStatus}
                />
            )}

            {subSection === 'organismes' && (
                <OrganismesSociauxList
                    organismes={organismes}
                    onOpenCreate={handleOpenCreateOrganisme}
                    onToggleStatus={requestToggleOrganismeStatus}
                />
            )}

            <CotisationFormModal
                isOpen={showCotisationModal}
                onClose={() => setShowCotisationModal(false)}
                onSubmit={handleSaveCotisation}
                editingCotisation={editingCotisation}
                formData={cotisationFormData}
                setFormData={setCotisationFormData}
                organismes={organismes}
                actionError={actionError}
                submitting={submitting}
            />

            <SiteCotisationFormModal
                isOpen={showSiteCotisationModal}
                onClose={() => setShowSiteCotisationModal(false)}
                onSubmit={handleSaveSiteCotisation}
                formData={siteCotisationFormData}
                setFormData={setSiteCotisationFormData}
                sites={sites}
                cotisations={cotisations}
                organismes={organismes}
                actionError={actionError}
                submitting={submitting}
            />

            <OrganismeFormModal
                isOpen={showOrganismeModal}
                onClose={() => setShowOrganismeModal(false)}
                onSubmit={handleSaveOrganisme}
                formData={organismeFormData}
                setFormData={setOrganismeFormData}
                actionError={actionError}
                submitting={submitting}
            />

            {/* Boîte de confirmation */}
            <ConfirmModal />
        </SideTabs>
    );
}
