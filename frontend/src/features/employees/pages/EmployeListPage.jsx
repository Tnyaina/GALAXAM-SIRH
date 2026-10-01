import { useState, useEffect, useCallback, useRef } from 'react';
import { UserPlus, Users, UserCheck, UserX, RefreshCw } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import employeService from '@/features/employees/api/employeService';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import StatCard from '@/components/ui/data-display/StatCard';
import Card from '@/components/ui/data-display/Card';
import { Select } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { useContractReferentiels } from '@/features/employees/hooks/useContractReferentiels';
import useDebounce from '@/hooks/useDebounce';
import { getErrorMessage } from '@/api/apiClient';

import EmployeFilterBar from '@/features/employees/components/EmployeFilterBar';
import EmployeTable from '@/features/employees/components/EmployeTable';
import EmployeeFormModal from '@/features/employees/components/modals/EmployeeFormModal';
import Pagination from '@/components/ui/navigation/Pagination';
import PageHeader from '@/components/ui/navigation/PageHeader';
import SEO from '@/components/system/SEO';

export default function EmployeListPage() {
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const { hasPermission } = useAuth();
    const { sites = [], departements = [], services = [] } = useContractReferentiels(true);
    const toast = useToast();

    // État local des employés et compteurs serveur
    const [employes, setEmployes] = useState([]);
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

    // Tri serveur
    const [columnSortConfig, setColumnSortConfig] = useState({
        key: searchParams.get('sort') || '',
        direction: searchParams.get('direction') || 'asc'
    });

    // Pagination serveur
    const [currentPage, setCurrentPage] = useState(Number(searchParams.get('page')) || 1);
    const [pageSize, setPageSize] = useState(Number(searchParams.get('size')) || 10);
    const requestSequence = useRef(0);

    // Modales & Actions
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [selectedEmployeForEdit, setSelectedEmployeForEdit] = useState(null);

    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [employeToDesactiver, setEmployeToDesactiver] = useState(null);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');

    const [isReactiverModalOpen, setIsReactiverModalOpen] = useState(false);
    const [employeToReactiver, setEmployeToReactiver] = useState(null);

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');

    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');

    // Chargement des statistiques globales (compteurs d'effectif)
    const loadStats = useCallback(async () => {
        try {
            const stats = await employeService.getEmployeStats();
            if (stats) setStatsCounts(stats);
        } catch (err) {
            console.error('Erreur chargement indicateurs employés:', err);
        }
    }, []);

    // Chargement paginé et filtré côté serveur (PostgreSQL)
    const loadEmployes = useCallback(async () => {
        const requestId = ++requestSequence.current;
        setLoading(true);
        try {
            const data = await employeService.getEmployes({
                q: debouncedSearch.trim() || undefined,
                actif: statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined,
                site: siteFilter || undefined,
                departement: departementFilter || undefined,
                service: serviceFilter || undefined,
                sort: columnSortConfig.key || 'HIERARCHICAL',
                direction: columnSortConfig.direction,
                page: currentPage,
                size: pageSize
            });

            if (requestId !== requestSequence.current) return;
            const list = data?.content || (Array.isArray(data) ? data : []);
            setEmployes(list);
            setTotalElements(data?.totalElements ?? list.length);
            setError('');
        } catch (err) {
            if (requestId !== requestSequence.current) return;
            console.error('Erreur chargement employés:', err);
            setError('Impossible de charger la liste des employés.');
        } finally {
            if (requestId === requestSequence.current) setLoading(false);
        }
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter, columnSortConfig, currentPage, pageSize]);

    // Déclencheurs de chargement
    useEffect(() => {
        loadEmployes();
        return () => { requestSequence.current += 1; };
    }, [loadEmployes]);

    useEffect(() => {
        loadStats();
    }, [loadStats]);

    // Synchronisation de l'URL avec les paramètres actifs
    useEffect(() => {
        const params = new URLSearchParams();
        if (debouncedSearch.trim()) params.set('q', debouncedSearch.trim());
        if (statusFilter !== 'ALL') params.set('status', statusFilter);
        if (siteFilter) params.set('site', siteFilter);
        if (departementFilter) params.set('dept', departementFilter);
        if (serviceFilter) params.set('service', serviceFilter);
        if (columnSortConfig.key) {
            params.set('sort', columnSortConfig.key);
            params.set('direction', columnSortConfig.direction);
        }
        if (currentPage > 1) params.set('page', String(currentPage));
        if (pageSize !== 10) params.set('size', String(pageSize));

        setSearchParams(params, { replace: true });
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter, columnSortConfig, currentPage, pageSize, setSearchParams]);

    const isFirstMount = useRef(true);
    // Réinitialisation de la page sur changement de filtres (sauf au montage initial)
    useEffect(() => {
        if (isFirstMount.current) {
            isFirstMount.current = false;
            return;
        }
        setCurrentPage(1);
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter]);

    const totalPages = Math.ceil(totalElements / pageSize) || 1;
    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(1);
        }
    }, [totalPages, currentPage]);

    const handleSortChange = (key, direction) => {
        setColumnSortConfig({ key, direction });
    };

    const handleResetFilters = () => {
        setSearchQuery('');
        setStatusFilter('ALL');
        setSiteFilter('');
        setDepartementFilter('');
        setServiceFilter('');
        setColumnSortConfig({ key: '', direction: 'asc' });
        setCurrentPage(1);
    };

    const handlePageSizeChange = (size) => {
        setPageSize(size);
        setCurrentPage(1);
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
                loadStats();
            } else {
                const created = await employeService.createEmploye(formData);
                toast({ 
                    title: 'Employé créé avec succès', 
                    message: 'Redirection vers la fiche carrière pour configurer le contrat initial...', 
                    type: 'success' 
                });
                setIsFormOpen(false);
                loadStats();
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
            loadStats();
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
            loadStats();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(getErrorMessage(err, 'Impossible de réactiver cet employé.'));
        } finally {
            setSubmitting(false);
        }
    };

    const displayTotal = statsCounts.total ?? totalElements;
    const totalActifs = statsCounts.actifs;
    const totalInactifs = statsCounts.inactifs;
    const pctActifs = displayTotal > 0 ? Math.round((totalActifs / displayTotal) * 100) : 0;
    const pctInactifs = displayTotal > 0 ? Math.round((totalInactifs / displayTotal) * 100) : 0;

    const hasActiveFilters = Boolean(
        searchQuery.trim() ||
        statusFilter !== 'ALL' ||
        siteFilter ||
        departementFilter ||
        serviceFilter
    );

    return (
        <div className="space-y-3.5 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <SEO title="Fiches Employés" description="Répertoire et gestion des collaborateurs." />
            
            <PageHeader
                title="Fiches Employés"
                subtitle={displayTotal > 0 
                    ? `${displayTotal} collaborateur${displayTotal > 1 ? 's' : ''} répertorié${displayTotal > 1 ? 's' : ''}${sites.length > 0 ? ` · ${sites.length} site${sites.length > 1 ? 's' : ''}` : ''}`
                    : "Répertoire et gestion des fiches du personnel"
                }
                icon={Users}
                breadcrumbs={[
                    { label: 'Tableau de bord', href: '/dashboard' },
                    { label: 'Collaborateurs' }
                ]}
                actions={
                    <>
                        <Button
                            onClick={() => { loadEmployes(); loadStats(); }}
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

            {/* Notifications / Alertes */}
            {error && (
                <Alert type="error" onClose={() => setError('')}>
                    {error}
                </Alert>
            )}

            {/* Indicateurs interactifs de statut */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
                <StatCard 
                    title="Total Collaborateurs" 
                    value={displayTotal} 
                    subtext={displayTotal > 0 ? `${totalActifs} actif${totalActifs > 1 ? 's' : ''} · ${totalInactifs} inactif${totalInactifs > 1 ? 's' : ''}` : 'Aucun collaborateur'} 
                    icon={<Users size={18} />} 
                    color="brand" 
                    active={statusFilter === 'ALL'} 
                    onClick={() => setStatusFilter('ALL')} 
                />
                <StatCard 
                    title="Collaborateurs actifs" 
                    value={totalActifs} 
                    subtext={displayTotal > 0 ? `${pctActifs} % de l'effectif` : '0 %'} 
                    icon={<UserCheck size={18} />} 
                    color="success" 
                    active={statusFilter === 'ACTIVE'} 
                    onClick={() => setStatusFilter('ACTIVE')} 
                />
                <StatCard 
                    title="Collaborateurs inactifs" 
                    value={totalInactifs} 
                    subtext={displayTotal > 0 ? `${pctInactifs} % de l'effectif` : '0 %'} 
                    icon={<UserX size={18} />} 
                    color="danger" 
                    active={statusFilter === 'INACTIVE'} 
                    onClick={() => setStatusFilter('INACTIVE')} 
                />
            </div>

            {/* Barre de filtres serveur */}
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
                onResetFilters={handleResetFilters}
                totalFound={totalElements}
            />

            {/* Tableau paginé côté serveur */}
            <Card className="flex flex-col overflow-hidden p-0" shadow="sm">
                <EmployeTable
                    employes={employes}
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
                
                {/* Pagination serveur */}
                {!loading && totalElements > 0 && (
                    <Pagination
                        currentPage={currentPage}
                        totalItems={totalElements}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                        onPageSizeChange={handlePageSizeChange}
                        className="border-t border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-none"
                    />
                )}
            </Card>

            {/* Modale d'édition / création d'employé */}
            {isFormOpen && (
                <EmployeeFormModal
                    isOpen={isFormOpen}
                    onClose={() => setIsFormOpen(false)}
                    onSave={handleSaveForm}
                    employeData={selectedEmployeForEdit}
                    isSubmitting={submitting}
                />
            )}

            {/* Modale de désactivation */}
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
