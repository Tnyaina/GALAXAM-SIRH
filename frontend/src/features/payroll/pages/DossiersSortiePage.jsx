import { useState, useEffect, useMemo, useCallback } from 'react';
import {
    AlertTriangle,
    ArrowRight,
    Award,
    BadgeCheck,
    Banknote,
    Building2,
    Calendar,
    CheckCircle2,
    Clock,
    FileCheck,
    FileSpreadsheet,
    FileText,
    FolderCheck,
    Plus,
    RefreshCw,
    Search,
    ShieldAlert,
    SlidersHorizontal,
    User,
    UserMinus,
    UserX,
    XCircle
} from 'lucide-react';
import { useNavigate } from 'react-router-dom';
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
import DossierSortieModal from '@/features/payroll/components/modals/stc/DossierSortieModal';
import StcPreparationModal from '@/features/payroll/components/modals/stc/StcPreparationModal';

const FILTRES_STATUTS = [
    { value: 'TOUS', label: 'Tous les dossiers' },
    { value: 'EN_PREPARATION', label: 'En préparation' },
    { value: 'PRET_A_VALIDER', label: 'Prêts à valider' },
    { value: 'VALIDE', label: 'Validés / Réglés' },
    { value: 'CLOTURE', label: 'Clôturés' },
];

export default function DossiersSortiePage() {
    const navigate = useNavigate();
    const toast = useToast();

    const [dossiers, setDossiers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [search, setSearch] = useState('');
    const [selectedStatut, setSelectedStatut] = useState('TOUS');

    // Modale de traitement de dossier
    const [selectedDossierId, setSelectedDossierId] = useState(null);
    const [showDossierModal, setShowDossierModal] = useState(false);

    // Modale de création d'un nouveau dossier
    const [showNewModal, setShowNewModal] = useState(false);
    const [newEmployeId, setNewEmployeId] = useState('');
    const [newDateDepart, setNewDateDepart] = useState(getTodayDateString());
    const [newMotif, setNewMotif] = useState('DEMISSION');

    // Modale STC Financier (enchaînement fluide)
    const [showStcModal, setShowStcModal] = useState(false);
    const [stcContext, setStcContext] = useState(null);

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
        const valides = dossiers.filter(d => d.statutDossier === 'VALIDE' || d.statutDossier === 'REGLE').length;
        const clotures = dossiers.filter(d => d.statutDossier === 'CLOTURE').length;
        return { total, prep, prets, valides, clotures };
    }, [dossiers]);

    const handleOpenDossier = (id) => {
        setSelectedDossierId(id);
        setShowDossierModal(true);
    };

    const handleCreateDossier = () => {
        if (!newEmployeId) {
            toast.show('Veuillez sélectionner un employé.', 'warning');
            return;
        }
        setShowNewModal(false);
        setSelectedDossierId(null);
        setShowDossierModal(true);
    };

    const handleDossierSaved = () => {
        chargerDossiers();
    };

    const handleOpenStcFromDossier = (dossierData) => {
        setShowDossierModal(false);
        setStcContext({
            idEmploye: dossierData.idEmploye,
            nomEmploye: dossierData.nomComplet,
            dateDepart: dossierData.dateDepart,
            motifRupture: dossierData.motifRupture,
        });
        setShowStcModal(true);
    };

    return (
        <div className="space-y-6">
            <SEO title="Dossiers de sortie & STC — GALAXAM SIRH" />

            {/* En-tête de page */}
            <PageHeader
                title="Dossiers de sortie & STC"
                subtitle="Orchestration du départ des collaborateurs, conformité légale (Loi 2024-004), checklist métier et préparation du solde de tout compte."
                breadcrumbs={[
                    { label: 'Espace Paie', href: '/paie' },
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
                        <Button
                            variant="primary"
                            size="sm"
                            onClick={() => setShowNewModal(true)}
                            iconLeft={<Plus size={15} />}
                        >
                            Nouveau dossier de sortie
                        </Button>
                    </div>
                }
            />

            {error && (
                <Alert type="error" title="Erreur de chargement" message={error} />
            )}

            {/* StatCards KPI */}
            <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
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
                    title="Validés / Clôturés"
                    value={stats.valides + stats.clotures}
                    icon={<BadgeCheck size={18} />}
                    color="success"
                    subtext={`${stats.clotures} définitivement clos`}
                />
            </div>

            {/* Barre de filtres et recherche */}
            <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 shadow-xs flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
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
            </div>

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
                            : "Aucun collaborateur n'est actuellement en cours de sortie."}
                        actionLabel="Initier un premier dossier"
                        onAction={() => setShowNewModal(true)}
                        actionIcon={<Plus size={14} />}
                    />
                ) : (
                    <Table>
                        <Thead>
                            <Tr>
                                <Th>Collaborateur</Th>
                                <Th>Départ effectif</Th>
                                <Th>Motif de rupture</Th>
                                <Th>Statut dossier</Th>
                                <Th>Checklist légale</Th>
                                <Th>Certificat (Art. 50)</Th>
                                <Th>STC scellé</Th>
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
                                            <div className="text-[10px] text-[var(--color-fg-muted)]">
                                                {d.typeContrat || 'CDI'}
                                            </div>
                                        </Td>
                                        <Td>
                                            <span className="text-xs text-[var(--color-fg)]">
                                                {d.motifRupture || 'DEMISSION'}
                                            </span>
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
                                        </Td>
                                        <Td>
                                            {totalCheck > 0 ? (
                                                <div className="space-y-0.5">
                                                    <div className="flex items-center gap-1.5 text-xs font-semibold">
                                                        {nbBloquants > 0 ? (
                                                            <span className="text-red-700 flex items-center gap-1">
                                                                <XCircle size={13} /> {nbBloquants} bloquant(s)
                                                            </span>
                                                        ) : (
                                                            <span className="text-emerald-700 flex items-center gap-1">
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
                                        </Td>
                                        <Td>
                                            {d.certificatDelivre ? (
                                                <Badge variant="success" size="xs">
                                                    Remis le {fmtDate(d.dateDelivranceCertificat)}
                                                </Badge>
                                            ) : (
                                                <Badge variant="warning" size="xs">
                                                    Non délivré
                                                </Badge>
                                            )}
                                        </Td>
                                        <Td>
                                            {d.idStc ? (
                                                <div className="text-xs">
                                                    <strong className="text-emerald-700 font-data">
                                                        {d.netAPayerStc ? formatMoneyAr(d.netAPayerStc) : 'Validé'}
                                                    </strong>
                                                    <span className="block text-[10px] text-[var(--color-fg-muted)]">
                                                        N° {d.numeroRecu || d.idStc}
                                                    </span>
                                                </div>
                                            ) : (
                                                <span className="text-xs text-[var(--color-fg-muted)] italic">
                                                    Non calculé
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
                                                Traiter
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
                title="Initier un dossier de sortie de collaborateur"
                size="md"
                footer={
                    <div className="flex justify-end gap-2 w-full">
                        <Button variant="ghost" onClick={() => setShowNewModal(false)}>
                            Annuler
                        </Button>
                        <Button variant="primary" onClick={handleCreateDossier} disabled={!newEmployeId}>
                            Ouvrir le dossier
                        </Button>
                    </div>
                }
            >
                <div className="space-y-4">
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Sélectionnez le salarié concerné. Les données d'emploi, de pointage, de congés et de paie seront automatiquement synchronisées en lecture seule.
                    </p>

                    <div>
                        <label className="block text-xs font-semibold text-[var(--color-fg)] mb-1">
                            Salarié concerné *
                        </label>
                        <EmployeSearchSelect
                            value={newEmployeId}
                            onChange={(e) => setNewEmployeId(e.target.value)}
                            required
                        />
                    </div>

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
            </Modal>

            {/* Modale de traitement complet du dossier de sortie */}
            {showDossierModal && (
                <DossierSortieModal
                    isOpen={showDossierModal}
                    onClose={() => setShowDossierModal(false)}
                    idDossier={selectedDossierId}
                    idEmploye={!selectedDossierId ? Number(newEmployeId) : null}
                    dateDepartInitiale={newDateDepart}
                    motifInitial={newMotif}
                    onSaved={handleDossierSaved}
                    onOpenStcModal={handleOpenStcFromDossier}
                />
            )}

            {/* Modale STC existante (enchaînement fluide sans modification de StcService) */}
            {showStcModal && stcContext && (
                <StcPreparationModal
                    isOpen={showStcModal}
                    onClose={() => setShowStcModal(false)}
                    idEmploye={stcContext.idEmploye}
                    nomEmploye={stcContext.nomEmploye}
                    dateDepartInitiale={stcContext.dateDepart}
                    motifInitial={stcContext.motifRupture}
                    onStcValide={() => {
                        setShowStcModal(false);
                        chargerDossiers();
                    }}
                />
            )}
        </div>
    );
}
