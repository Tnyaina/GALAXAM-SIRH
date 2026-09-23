import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
import { UserPlus, Users, UserCheck, UserX, RefreshCw } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import employeService from '@/features/employees/api/employeService';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import StatCard from '@/components/ui/data-display/StatCard';
import { Select } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { useContractReferentiels } from '@/features/employees/hooks/useContractReferentiels';
import useDebounce from '@/hooks/useDebounce';
import { getErrorMessage } from '@/api/apiClient';

import EmployeFilterBar from '@/features/employees/components/EmployeFilterBar';
import EmployeTable from '@/features/employees/components/EmployeTable';
import Pagination from '@/components/ui/navigation/Pagination';
import PageHeader from '@/components/ui/navigation/PageHeader';

const EmployeeFormModal = lazy(() => import('@/features/employees/components/modals/EmployeeFormModal'));

export default function EmployeList() {
    // Contexte, navigation et hook métier
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const { sites = [], departements = [], services = [] } = useContractReferentiels(true);

    // État local
    const [rawEmployes, setRawEmployes] = useState([]);
    const [totalElements, setTotalElements] = useState(0);
    const [statsCounts, setStatsCounts] = useState({ total: 0, actifs: 0, inactifs: 0 });
    const [loading, setLoading] = useState(true);

    // Filtres synchronisés avec l'URL
    const [searchQuery, setSearchQuery] = useState(searchParams.get('q') || '');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [statusFilter, setStatusFilter] = useState(searchParams.get('status') || 'ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'
    const [siteFilter, setSiteFilter] = useState(searchParams.get('site') || '');
    const [departementFilter, setDepartementFilter] = useState(searchParams.get('dept') || '');
    const [serviceFilter, setServiceFilter] = useState(searchParams.get('service') || '');

    // Tri (Preset par défaut : Hiérarchique par Département -> Service -> Nom)
    const [sortPreset, setSortPreset] = useState('HIERARCHICAL');
    const [columnSortConfig, setColumnSortConfig] = useState({ key: '', direction: 'asc' });

    // Pagination
    const [currentPage, setCurrentPage] = useState(Number(searchParams.get('page')) || 1);
    const [pageSize, setPageSize] = useState(10);

    // Modales
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [selectedEmployeForEdit, setSelectedEmployeForEdit] = useState(null);

    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [employeToDesactiver, setEmployeToDesactiver] = useState(null);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');

    const [isReactiverModalOpen, setIsReactiverModalOpen] = useState(false);
    const [employeToReactiver, setEmployeToReactiver] = useState(null);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const toast = useToast();

    // Données dérivées
    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');

    // Effets
    const loadEmployes = useCallback(async () => {
        setLoading(true);
        try {
            const [data, stats] = await Promise.all([
                employeService.getEmployes({
                    q: debouncedSearch.trim() || undefined,
                    actif: statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined,
                    site: siteFilter || undefined,
                    departement: departementFilter || undefined,
                    service: serviceFilter || undefined,
                    sort: columnSortConfig.key || sortPreset,
                    direction: columnSortConfig.direction,
                    page: currentPage,
                    size: pageSize
                }),
                employeService.getEmployeStats()
            ]);

            const list = data?.content || (Array.isArray(data) ? data : []);
            setRawEmployes(list);
            setTotalElements(data?.totalElements ?? list.length);

            if (stats) {
                setStatsCounts(stats);
            }
            setError('');
        } catch (err) {
            console.error('Erreur chargement employés:', err);
            setError('Impossible de charger la liste des employés.');
        } finally {
            setLoading(false);
        }
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter,
        columnSortConfig, sortPreset, currentPage, pageSize]);

    useEffect(() => {
        loadEmployes();
    }, [loadEmployes]);

    // Synchronisation de l'état avec l'URL (débouncée)
    useEffect(() => {
        const params = {};
        if (debouncedSearch.trim()) params.q = debouncedSearch.trim();
        if (statusFilter && statusFilter !== 'ALL') params.status = statusFilter;
        if (siteFilter) params.site = siteFilter;
        if (departementFilter) params.dept = departementFilter;
        if (serviceFilter) params.service = serviceFilter;
        if (currentPage > 1) params.page = String(currentPage);
        setSearchParams(params, { replace: true });
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter, currentPage, setSearchParams]);

    const isFirstMount = useRef(true);

    // Réinitialiser la pagination si les filtres changent (sauf au premier montage pour préserver ?page=X)
    useEffect(() => {
        if (isFirstMount.current) {
            isFirstMount.current = false;
            return;
        }
        setCurrentPage(1);
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter]);

    const totalPages = Math.ceil(totalElements / pageSize) || 1;

    // Sécurité ajustement : si la page courante dépasse le total de pages
    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(1);
        }
    }, [totalPages, currentPage]);

    const paginatedEmployes = rawEmployes;

    const handleSortChange = (key, direction) => {
        setColumnSortConfig({ key, direction });
    };

    const handleResetFilters = () => {
        setSearchQuery('');
        setStatusFilter('ALL');
        setSiteFilter('');
        setDepartementFilter('');
        setServiceFilter('');
        setSortPreset('HIERARCHICAL');
        setColumnSortConfig({ key: '', direction: 'asc' });
    };

    const handleOpenCreate = () => {
        setSelectedEmployeForEdit(null);
        setIsFormOpen(true);
    };

    const handleOpenEdit = async (emp) => {
        try {
            const empId = emp.idEmploye || emp.employe?.idEmploye;
            const fullDetail = await employeService.getEmployeFicheDetail(empId);
            setSelectedEmployeForEdit(fullDetail);
            setIsFormOpen(true);
        } catch (err) {
            console.error('Erreur chargement détails pour modification:', err);
        }
    };

    const handleOpenDetail = (emp) => {
        const empId = emp.idEmploye || emp.employe?.idEmploye;
        navigate(`/employes/${empId}`);
    };

    const handleSaveForm = async (formData) => {
        if (submitting) return;
        setSubmitting(true);
        try {
            if (selectedEmployeForEdit) {
                const empId = selectedEmployeForEdit.employe?.idEmploye || selectedEmployeForEdit.idEmploye;
                await employeService.updateEmploye(empId, formData);
                toast({ title: 'Succès', message: 'Fiche employé modifiée avec succès.', type: 'success' });
                setIsFormOpen(false);
                loadEmployes();
            } else {
                const created = await employeService.createEmploye(formData);
                toast({ 
                    title: 'Employé créé avec succès', 
                    message: 'Redirection vers la fiche carrière pour configurer le contrat initial...', 
                    type: 'success' 
                });
                setIsFormOpen(false);
                const newEmpId = created?.idEmploye || created?.employe?.idEmploye;
                if (newEmpId) {
                    navigate(`/employes/${newEmpId}?tab=carriere&action=new_contract`);
                } else {
                    loadEmployes();
                }
            }
        } catch (err) {
            console.error('Erreur sauvegarde employé:', err);
            throw err;
        } finally {
            setSubmitting(false);
        }
    };

    const handleToggleStatus = (emp) => {
        const empView = emp.employe || emp;
        if (empView.estActif) {
            setEmployeToDesactiver(empView);
            setIsDesactivationModalOpen(true);
        } else {
            setEmployeToReactiver(empView);
            setIsReactiverModalOpen(true);
        }
    };

    const handleDesactiverConfirm = async () => {
        if (!employeToDesactiver || submitting) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.desactiverEmploye(employeToDesactiver.idEmploye, motifDesactivation);
            setIsDesactivationModalOpen(false);
            toast({ title: 'Succès', message: `L'employé "${employeToDesactiver.nomComplet || employeToDesactiver.matricule}" a été désactivé.`, type: 'success' });
            loadEmployes();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(getErrorMessage(err, 'Impossible de désactiver cet employé.'));
        } finally {
            setSubmitting(false);
        }
    };

    const handleReactiverConfirm = async () => {
        if (!employeToReactiver || submitting) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.reactiverEmploye(employeToReactiver.idEmploye);
            setIsReactiverModalOpen(false);
            toast({ title: 'Succès', message: `L'employé "${employeToReactiver.nomComplet || employeToReactiver.matricule}" a été réactivé.`, type: 'success' });
            loadEmployes();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(getErrorMessage(err, 'Impossible de réactiver cet employé.'));
        } finally {
            setSubmitting(false);
        }
    };

    const displayTotal = statsCounts.total || rawEmployes.length;
    const totalActifs = statsCounts.actifs;
    const totalInactifs = statsCounts.inactifs;

    const hasActiveFilters = Boolean(
        searchQuery.trim() ||
        statusFilter !== 'ALL' ||
        siteFilter ||
        departementFilter ||
        serviceFilter
    );

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <PageHeader
                title="Fiches Employés"
                subtitle="Répertoire du personnel — gestion des fiches individuelles, carrières et documents"
                icon={Users}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Collaborateurs' }
                ]}
                actions={
                    <>
                        <Button
                            onClick={loadEmployes}
                            variant="ghost"
                            size="sm"
                            loading={loading}
                            iconLeft={<RefreshCw size={16} />}
                        >
                            Actualiser
                        </Button>
                        {canWrite && (
                            <Button
                                onClick={handleOpenCreate}
                                variant="primary"
                                size="sm"
                                iconLeft={<UserPlus size={16} />}
                            >
                                Nouvel Employé
                            </Button>
                        )}
                    </>
                }
            />

            {/* Notifications */}
            {error && (
                <Alert type="error" onClose={() => setError('')}>
                    {error}
                </Alert>
            )}

            {/* Indicateurs et filtres de statut de cette liste. */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
                <StatCard title="Total Collaborateurs" value={displayTotal} subtext="Effectif global de l'entreprise" icon={<Users size={18} />} color="brand" active={statusFilter === 'ALL'} onClick={() => setStatusFilter('ALL')} />
                <StatCard title="Collaborateurs Actifs" value={totalActifs} subtext="En poste actuellement" icon={<UserCheck size={18} />} color="success" active={statusFilter === 'ACTIVE'} onClick={() => setStatusFilter('ACTIVE')} />
                <StatCard title="Comptes Inactifs" value={totalInactifs} subtext="Dossiers archivés ou sortis" icon={<UserX size={18} />} color="danger" active={statusFilter === 'INACTIVE'} onClick={() => setStatusFilter('INACTIVE')} />
            </div>

            {/* Barre d'outils : recherche + filtres de structure + multi-tri */}
            <EmployeFilterBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                statusFilter={statusFilter}
                onStatusFilterChange={setStatusFilter}
                siteFilter={siteFilter}
                onSiteFilterChange={setSiteFilter}
                departementFilter={departementFilter}
                onDepartementFilterChange={setDepartementFilter}
                serviceFilter={serviceFilter}
                onServiceFilterChange={setServiceFilter}
                sites={sites}
                departements={departements}
                services={services}
                sortPreset={sortPreset}
                onSortPresetChange={(preset) => {
                    setSortPreset(preset);
                    setColumnSortConfig({ key: '', direction: 'asc' });
                }}
                onResetFilters={handleResetFilters}
                totalFound={totalElements}
            />

            {/* Tableau des employés */}
            <div className="flex flex-col border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-2xs">
                <EmployeTable
                    employes={paginatedEmployes}
                    loading={loading}
                    canWrite={canWrite}
                    canValidate={canValidate}
                    hasActiveFilters={hasActiveFilters}
                    onOpenCreate={handleOpenCreate}
                    onOpenDetail={handleOpenDetail}
                    onOpenEdit={handleOpenEdit}
                    onToggleStatus={handleToggleStatus}
                    sortConfig={columnSortConfig}
                    onSortChange={handleSortChange}
                    onResetFilters={handleResetFilters}
                />
                
                {/* Pagination */}
                {!loading && totalElements > 0 && (
                    <Pagination
                        currentPage={currentPage}
                        totalItems={totalElements}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                        onPageSizeChange={setPageSize}
                        className="border-t border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-none"
                    />
                )}
            </div>

            {/* Modale d'édition / création d'employé */}
            {isFormOpen && (
                <Suspense fallback={null}>
                    <EmployeeFormModal
                        isOpen={isFormOpen}
                        onClose={() => setIsFormOpen(false)}
                        onSave={handleSaveForm}
                        employeData={selectedEmployeForEdit}
                        isSubmitting={submitting}
                    />
                </Suspense>
            )}

            {/* Modale de désactivation réutilisable */}
            <ConfirmDialog
                isOpen={isDesactivationModalOpen}
                onClose={() => setIsDesactivationModalOpen(false)}
                onConfirm={handleDesactiverConfirm}
                title="Désactiver la fiche employé"
                message={`Spécifiez le motif de désactivation de l'employé ${employeToDesactiver?.nomComplet || employeToDesactiver?.matricule || ''}.`}
                confirmLabel="Confirmer la désactivation"
                cancelLabel="Annuler"
                variant="danger"
                loading={submitting}
            >
                <Select
                    label="Motif de Désactivation"
                    value={motifDesactivation}
                    onChange={(e) => setMotifDesactivation(e.target.value)}
                >
                    <option value="Demission">Démission</option>
                    <option value="Licenciement">Licenciement</option>
                    <option value="Fin_contrat">Fin de contrat</option>
                    <option value="Retraite">Retraite</option>
                    <option value="Deces">Décès</option>
                    <option value="Autre">Autre</option>
                </Select>
            </ConfirmDialog>

            {/* Modale de réactivation */}
            <ConfirmDialog
                isOpen={isReactiverModalOpen}
                onClose={() => setIsReactiverModalOpen(false)}
                onConfirm={handleReactiverConfirm}
                title="Réactiver le collaborateur"
                message={`Êtes-vous sûr de vouloir réactiver la fiche de "${employeToReactiver?.nomComplet || employeToReactiver?.matricule || ''}" ? Son profil repassera en statut Actif.`}
                confirmLabel="Réactiver le profil"
                cancelLabel="Annuler"
                variant="primary"
                loading={submitting}
            />
        </div>
    );
}
