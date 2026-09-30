import { useState, useEffect, useCallback, useRef, lazy, Suspense } from 'react';
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
    const [sortPreset, setSortPreset] = useState(searchParams.get('sort') || 'HIERARCHICAL');
    const [columnSortConfig, setColumnSortConfig] = useState({ key: '', direction: searchParams.get('direction') || 'asc' });

    // Pagination
    const [currentPage, setCurrentPage] = useState(Number(searchParams.get('page')) || 1);
    const [pageSize, setPageSize] = useState(Number(searchParams.get('size')) || 10);
    const requestSequence = useRef(0);

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
        const requestId = ++requestSequence.current;
        setLoading(true);
        try {
            const data = await employeService.getEmployes({
                    q: debouncedSearch.trim() || undefined,
                    actif: statusFilter === 'ACTIVE' ? true : statusFilter === 'INACTIVE' ? false : undefined,
                    site: siteFilter || undefined,
                    departement: departementFilter || undefined,
                    service: serviceFilter || undefined,
                    sort: columnSortConfig.key || sortPreset,
                    direction: columnSortConfig.direction,
                    page: currentPage,
                    size: pageSize
                });

            const list = data?.content || (Array.isArray(data) ? data : []);
            if (requestId !== requestSequence.current) return;
            setRawEmployes(list);
            setTotalElements(data?.totalElements ?? list.length);

            setError('');
        } catch (err) {
            if (requestId !== requestSequence.current) return;
            console.error('Erreur chargement employés:', err);
            setError('Impossible de charger la liste des employés.');
        } finally {
            if (requestId === requestSequence.current) setLoading(false);
        }
    }, [debouncedSearch, statusFilter, siteFilter, departementFilter, serviceFilter,
        columnSortConfig, sortPreset, currentPage, pageSize]);

    useEffect(() => {
        loadEmployes();
        return () => { requestSequence.current += 1; };
    }, [loadEmployes]);

    useEffect(() => {
        let active = true;
        employeService.getEmployeStats()
            .then(stats => { if (active && stats) setStatsCounts(stats); })
            .catch(err => console.error('Erreur chargement indicateurs employés:', err));
        return () => { active = false; };
    }, []);

    // Synchronisation de l'état avec l'URL (débouncée)
    useEffect(() => {
        const params = new URLSearchParams(searchParams);
        ['q', 'status', 'site', 'dept', 'service', 'page', 'sort', 'direction', 'size'].forEach(key => params.delete(key));
        if (searchQuery.trim()) params.set('q', searchQuery.trim());
        if (statusFilter && statusFilter !== 'ALL') params.set('status', statusFilter);
        if (siteFilter) params.set('site', siteFilter);
        if (departementFilter) params.set('dept', departementFilter);
        if (serviceFilter) params.set('service', serviceFilter);
        if (currentPage > 1) params.set('page', String(currentPage));
        if (columnSortConfig.key) {
            params.set('sort', columnSortConfig.key);
            params.set('direction', columnSortConfig.direction);
        } else if (sortPreset !== 'HIERARCHICAL') {
            params.set('sort', sortPreset);
        }
        if (pageSize !== 10) params.set('size', String(pageSize));
        if (params.toString() !== searchParams.toString()) {
            setSearchParams(params, { replace: true });
        }
    }, [searchQuery, statusFilter, siteFilter, departementFilter, serviceFilter, currentPage, columnSortConfig, sortPreset, pageSize, searchParams, setSearchParams]);

    useEffect(() => {
        setSearchQuery(searchParams.get('q') || '');
        setStatusFilter(searchParams.get('status') || 'ALL');
        setSiteFilter(searchParams.get('site') || '');
        setDepartementFilter(searchParams.get('dept') || '');
        setServiceFilter(searchParams.get('service') || '');
        setCurrentPage(Number(searchParams.get('page')) || 1);
        setPageSize(Number(searchParams.get('size')) || 10);
        const sort = searchParams.get('sort') || 'HIERARCHICAL';
        const isColumnSort = ['FONCTION', 'SITE', 'DATEEMBAUCHE', 'DEPARTEMENT', 'STATUT', 'MATRICULE'].includes(sort.toUpperCase());
        const nextSortPreset = isColumnSort ? 'COLUMN' : (sort.includes('_') ? sort : 'HIERARCHICAL');
        setSortPreset(previous => previous === nextSortPreset ? previous : nextSortPreset);
        const nextColumnSort = ['NOM_ASC', 'NOM_DESC', 'MATRICULE_ASC', 'MATRICULE_DESC', 'ANCIENNETE_ASC', 'ANCIENNETE_DESC'].includes(sort)
            ? { key: '', direction: 'asc' }
            : { key: sort === 'HIERARCHICAL' || sort === 'SITE_SERVICE' ? '' : sort, direction: searchParams.get('direction') || 'asc' };
        setColumnSortConfig(previous => previous.key === nextColumnSort.key && previous.direction === nextColumnSort.direction ? previous : nextColumnSort);
    }, [searchParams]);

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
        setSortPreset('COLUMN');
    };

    const handleResetFilters = () => {
        setSearchQuery('');
        setStatusFilter('ALL');
        setSiteFilter('');
        setDepartementFilter('');
        setServiceFilter('');
        setSortPreset('HIERARCHICAL');
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

            {/* Barre d'outils : recherche (à gauche) + filtres (à droite) */}
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

            {/* Tableau des employés */}
            <Card className="flex flex-col overflow-hidden p-0" shadow="sm">
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
                        onPageSizeChange={handlePageSizeChange}
                        className="border-t border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-none"
                    />
                )}
            </Card>

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
