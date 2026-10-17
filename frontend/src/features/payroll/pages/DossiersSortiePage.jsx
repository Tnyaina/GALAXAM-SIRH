import { useState, useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    ArrowRight,
    BadgeCheck,
    CheckCircle2,
    Clock,
    FolderCheck,
    Plus,
    RefreshCw,
    Search,
    User,
    UserMinus,
    XCircle
} from 'lucide-react';
import SEO from '@/components/system/SEO';
import PageHeader from '@/components/ui/navigation/PageHeader';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import EmployeSearchSelect from '@/components/ui/forms/EmployeSearchSelect';
import Badge from '@/components/ui/data-display/Badge';
import StatCard from '@/components/ui/data-display/StatCard';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { useToast } from '@/components/ui/feedback/Toast';
import { formatMoneyAr } from '@/utils/moneyUtils';
import { fmtDate, getTodayDateString } from '@/utils/dateUtils';
import { getErrorMessage } from '@/api/apiClient';

import dossierSortieService from '@/features/payroll/api/dossierSortieService';
import { useAuth } from '@/context/AuthContext';

const FILTRES_STATUTS = [
    { value: 'TOUS', label: 'Tous les dossiers' },
    { value: 'EN_PREPARATION', label: 'En préparation' },
    { value: 'PRET_A_VALIDER', label: 'Prêts à valider' },
    { value: 'VALIDE', label: 'STC validés' },
    { value: 'REGLE', label: 'STC réglés' },
    { value: 'CLOTURE', label: 'Clôturés' },
    { value: 'ANNULE', label: 'Annulés' },
];

export default function DossiersSortiePage() {
    const navigate = useNavigate();
    const toast = useToast();
    const { hasPermission, user } = useAuth();
    const canWrite = hasPermission('PAIE_WRITE');

    const [dossiers, setDossiers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [search, setSearch] = useState('');
    const [selectedStatut, setSelectedStatut] = useState('TOUS');

    // Modale de création d'un nouveau dossier
    const [showNewModal, setShowNewModal] = useState(false);
    const [newEmployeId, setNewEmployeId] = useState('');
    const [newDateDepart, setNewDateDepart] = useState(getTodayDateString());
    const [newMotif, setNewMotif] = useState('DEMISSION');

    const chargerDossiers = useCallback(async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await dossierSortieService.listerDossiers();
            setDossiers(Array.isArray(data) ? data : []);
        } catch (err) {
            setError(getErrorMessage(err, 'Impossible de charger les dossiers de sortie.'));
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        chargerDossiers();
    }, [chargerDossiers]);

    // Filtrage et recherche
    const dossiersFiltres = useMemo(() => {
        return dossiers.filter(d => {
            const matchStatut = selectedStatut === 'TOUS' || d.statutDossier === selectedStatut;
            const query = search.trim().toLowerCase();
            const matchQuery = !query ||
                (d.nomComplet && d.nomComplet.toLowerCase().includes(query)) ||
                (d.matricule && d.matricule.toLowerCase().includes(query)) ||
                (d.motifRupture && d.motifRupture.toLowerCase().includes(query)) ||
                (d.departement && d.departement.toLowerCase().includes(query));
            return matchStatut && matchQuery;
        });
    }, [dossiers, selectedStatut, search]);

    // KPIs calculés
    const stats = useMemo(() => {
        const total = dossiers.length;
        const prep = dossiers.filter(d => d.statutDossier === 'EN_PREPARATION').length;
        const prets = dossiers.filter(d => d.statutDossier === 'PRET_A_VALIDER').length;
        const valides = dossiers.filter(d => Boolean(d.idStc)).length;
        const clotures = dossiers.filter(d => d.statutDossier === 'CLOTURE').length;
        return { total, prep, prets, valides, clotures };
    }, [dossiers]);

    const handleOpenDossier = (id) => {
        navigate(`/paie/dossiers-sortie/${id}`);
    };

    const handleCreateDossier = () => {
        if (!newEmployeId) {
            toast.warning('Veuillez sélectionner un employé.');
            return;
        }
        const params = new URLSearchParams({ employeId: String(newEmployeId), dateDepart: newDateDepart, motif: newMotif });
        navigate(`/paie/dossiers-sortie/nouveau?${params.toString()}`);
    };

    return (
        <div className="space-y-6">
            <SEO title="Dossiers de sortie & STC — GALAXAM SIRH" />

            {/* En-tête de page */}
            <PageHeader
                title="Dossiers de sortie & STC"
                subtitle="Préparation des départs, suivi financier, formalités RH et documents de sortie."
                breadcrumbs={[
                    { label: 'Espace Paie', href: user?.role === 'ROLE_ADMIN' ? '/paie' : undefined },
                    { label: 'Dossiers de sortie' },
                ]}
                actions={
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={chargerDossiers}
                            loading={loading}
                            iconLeft={<RefreshCw size={14} />}
                        >
                            Actualiser
                        </Button>
                        {canWrite && <Button
                            variant="primary"
                            size="sm"
                            onClick={() => setShowNewModal(true)}
                            iconLeft={<Plus size={15} />}
                        >
                            Nouveau dossier de sortie
                        </Button>}
                    </div>
                }
            />

            {error && (
                <Alert type="error" title="Erreur de chargement" message={error} />
            )}

            {/* StatCards KPI */}
            {!loading && dossiers.length > 0 && <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
                <StatCard
                    title="Total départs enregistrés"
                    value={stats.total}
                    icon={<UserMinus size={18} />}
                    subtext="Actifs ou traités"
                    color="brand"
                />
                <StatCard
                    title="En préparation"
                    value={stats.prep}
                    icon={<Clock size={18} />}
                    color={stats.prep > 0 ? 'warning' : 'neutral'}
                    subtext="Vérifications en cours"
                />
                <StatCard
                    title="Prêts à valider"
                    value={stats.prets}
                    icon={<FolderCheck size={18} />}
                    color={stats.prets > 0 ? 'info' : 'neutral'}
                    subtext="Checklist conforme"
                />
                <StatCard
                    title="STC scellés"
                    value={stats.valides}
                    icon={<BadgeCheck size={18} />}
                    color="success"
                    subtext={`${stats.clotures} dossiers clos administrativement`}
                />
            </div>}

            {/* Barre de filtres et recherche */}
            {(loading || dossiers.length > 0) && <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
                <div className="flex-1 max-w-md relative">
                    <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                    <Input
                        type="text"
                        placeholder="Rechercher par salarié, matricule, motif..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="pl-9 w-full"
                    />
                </div>
                <div className="flex items-center gap-3">
                    <span className="text-xs font-semibold text-[var(--color-fg-muted)] whitespace-nowrap">Statut :</span>
                    <SearchSelect
                        options={FILTRES_STATUTS}
                        value={selectedStatut}
                        onChange={(val) => setSelectedStatut(val)}
                        className="w-48"
                    />
                </div>
            </div>}

            {/* Tableau principal des dossiers */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs overflow-hidden">
                {loading ? (
                    <SkeletonTable rows={5} columns={6} />
                ) : dossiersFiltres.length === 0 ? (
                    <EmptyState
                        icon={UserMinus}
                        title="Aucun dossier de sortie trouvé"
                        description={search || selectedStatut !== 'TOUS'
                            ? "Aucun dossier ne correspond à vos critères de recherche."
                            : "Commencez par choisir un salarié et une date de départ. Le dossier pourra ensuite être préparé et simulé."}
                        actionLabel={canWrite ? 'Initier un premier dossier' : undefined}
                        onAction={canWrite ? () => setShowNewModal(true) : undefined}
                        actionIcon={<Plus size={14} />}
                    />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Collaborateur</Th>
                                <Th>Départ et contrat</Th>
                                <Th>Dossier</Th>
                                <Th>Contrôles et certificat</Th>
                                <Th>STC financier</Th>
                                <Th className="text-right">Action</Th>
                            </Tr>
                        </Thead>
                        <Tbody>
                            {dossiersFiltres.map((d) => {
                                const totalCheck = d.checklist?.length || 0;
                                const nbConforme = d.checklist?.filter(c => c.statut === 'CONFORME').length || 0;
                                const nbBloquants = d.checklist?.filter(c => c.niveau === 'BLOQUANT' && c.statut === 'NON_CONFORME').length || 0;

                                return (
                                    <Tr key={d.idDossierSortie || d.idEmploye}>
                                        <Td>
                                            <div className="flex items-center gap-2.5">
                                                <div className="w-8 h-8 rounded-none bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] flex items-center justify-center font-bold text-[11px] text-[var(--color-brand-600)]">
                                                    {d.matricule ? d.matricule.slice(-3) : <User size={14} />}
                                                </div>
                                                <div>
                                                    <div className="font-bold text-xs text-[var(--color-fg)]">
                                                        {d.nomComplet || 'Salarié non identifié'}
                                                    </div>
                                                    <div className="text-[11px] text-[var(--color-fg-muted)]">
                                                        {d.matricule} · {d.intitulePoste || d.departement || 'Salarié'}
                                                    </div>
                                                </div>
                                            </div>
                                        </Td>
                                        <Td>
                                            <div className="text-xs font-semibold text-[var(--color-fg)]">
                                                {fmtDate(d.dateDepart)}
                                            </div>
                                            <div className="text-[11px] text-[var(--color-fg-muted)]">{d.motifRupture || 'Motif à vérifier'}</div>
                                            <div className="mt-1 text-[11px] text-[var(--color-fg-muted)]">
                                                {d.typeContrat || 'Contrat'} · {d.statutContrat || 'Statut à vérifier'}
                                            </div>
                                        </Td>
                                        <Td>
                                            <Badge
                                                variant={
                                                    d.statutDossier === 'CLOTURE' ? 'success' :
                                                    d.statutDossier === 'VALIDE' ? 'primary' :
                                                    d.statutDossier === 'PRET_A_VALIDER' ? 'info' : 'warning'
                                                }
                                                size="sm"
                                            >
                                                {d.statutDossier || 'EN_PREPARATION'}
                                            </Badge>
                                            {d.statutDossier === 'CLOTURE' && Number(d.soldeRestantAPayer) > 0 &&
                                                <div className="text-[10px] text-[var(--color-warning-700)] mt-1">Dette restante · dérogation</div>}
                                        </Td>
                                        <Td>
                                            {totalCheck > 0 ? (
                                                <div className="space-y-0.5">
                                                    <div className="flex items-center gap-1.5 text-xs font-semibold">
                                                        {nbBloquants > 0 ? (
                                                            <span className="text-[var(--color-danger-700)] flex items-center gap-1">
                                                                <XCircle size={13} /> {nbBloquants} bloquant(s)
                                                            </span>
                                                        ) : (
                                                            <span className="text-[var(--color-success-700)] flex items-center gap-1">
                                                                <CheckCircle2 size={13} /> Conforme
                                                            </span>
                                                        )}
                                                    </div>
                                                    <span className="text-[10px] text-[var(--color-fg-muted)]">
                                                        {nbConforme} / {totalCheck} points validés
                                                    </span>
                                                </div>
                                            ) : (
                                                <span className="text-xs text-[var(--color-fg-muted)]">À initialiser</span>
                                            )}
                                            <div className="mt-2">
                                                {d.certificatDelivre ? (
                                                    <Badge variant="success" size="xs">
                                                        Certificat remis le {fmtDate(d.dateDelivranceCertificat)}
                                                    </Badge>
                                                ) : (
                                                    <Badge variant="warning" size="xs">
                                                        Certificat non remis
                                                    </Badge>
                                                )}
                                            </div>
                                        </Td>
                                        <Td>
                                            {d.idStc ? (
                                                <div className="text-xs">
                                                    <strong className="text-[var(--color-success-700)] font-data">
                                                        {d.netAPayerStc != null ? formatMoneyAr(d.netAPayerStc) : 'Validé'}
                                                    </strong>
                                                    <span className="block text-[10px] text-[var(--color-fg-muted)]">
                                                        N° {d.numeroRecu || d.idStc}
                                                    </span>
                                                    <span className="block text-[10px] text-[var(--color-fg-muted)]">Reste : {formatMoneyAr(d.soldeRestantAPayer ?? 0)}</span>
                                                </div>
                                            ) : (
                                                <span className="text-xs text-[var(--color-fg-muted)] italic">
                                                    Non scellé
                                                </span>
                                            )}
                                        </Td>
                                        <Td className="text-right">
                                            <Button
                                                variant="outline"
                                                size="xs"
                                                onClick={() => handleOpenDossier(d.idDossierSortie)}
                                                iconRight={<ArrowRight size={13} />}
                                            >
                                                {canWrite ? 'Traiter' : 'Consulter'}
                                            </Button>
                                        </Td>
                                    </Tr>
                                );
                            })}
                        </Tbody>
                    </Table>
                )}
            </div>

            {/* Modale d'initialisation d'un nouveau dossier */}
            <Modal
                isOpen={showNewModal}
                onClose={() => setShowNewModal(false)}
                title="Ouvrir un dossier de sortie"
                size="xl"
                footer={
                    <div className="flex justify-end gap-2 w-full">
                        <Button variant="ghost" onClick={() => setShowNewModal(false)}>
                            Annuler
                        </Button>
                        <Button variant="primary" onClick={handleCreateDossier} disabled={!canWrite || !newEmployeId}>
                            Continuer vers le dossier
                        </Button>
                    </div>
                }
            >
                <div className="space-y-5">
                    <div className="grid grid-cols-3 gap-2 text-xs" aria-label="Parcours du dossier">
                        <div className="border-b-2 border-[var(--color-brand-500)] pb-2 font-semibold text-[var(--color-fg)]">1 · Départ</div>
                        <div className="border-b-2 border-[var(--color-border-subtle)] pb-2 text-[var(--color-fg-muted)]">2 · Vérifications</div>
                        <div className="border-b-2 border-[var(--color-border-subtle)] pb-2 text-[var(--color-fg-muted)]">3 · STC et suivi</div>
                    </div>
                    <div>
                        <h3 className="text-base font-bold text-[var(--color-fg)]">Qui quitte l’entreprise, et à quelle date ?</h3>
                        <p className="mt-1 text-sm text-[var(--color-fg-muted)]">Vous préparerez ensuite le dossier, sans rompre le contrat ni créer de STC.</p>
                    </div>

                    <div>
                        <EmployeSearchSelect
                            label="Salarié concerné"
                            value={newEmployeId}
                            onChange={(employeId) => setNewEmployeId(employeId)}
                            searchEmployes={dossierSortieService.rechercherEmployes}
                            required
                        />
                    </div>

                    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <div>
                        <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                            Date effective de sortie *
                        </label>
                        <Input
                            type="date"
                            value={newDateDepart}
                            onChange={(e) => setNewDateDepart(e.target.value)}
                        />
                    </div>

                    <div>
                        <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                            Motif de rupture légal *
                        </label>
                        <SearchSelect
                            options={[
                                { value: 'DEMISSION', label: 'Démission du salarié' },
                                { value: 'LICENCIEMENT_SIMPLE', label: 'Licenciement simple' },
                                { value: 'FIN_CONTRAT', label: 'Fin de contrat CDD' },
                                { value: 'RUPTURE_CONVENTIONNELLE', label: 'Rupture conventionnelle' },
                                { value: 'RETRAITE', label: 'Départ à la retraite' },
                                { value: 'FAUTE_LOURDE', label: 'Licenciement pour faute lourde' },
                            ]}
                            value={newMotif}
                            onChange={(val) => setNewMotif(val)}
                        />
                    </div>
                    </div>
                    <p className="border-l-2 border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)] px-3 py-2 text-sm text-[var(--color-fg-muted)]">
                        Ensuite : vérifier le contrat et la paie. La rupture se valide dans Carrière avant le scellement.
                    </p>
                </div>
            </Modal>

        </div>
    );
}
