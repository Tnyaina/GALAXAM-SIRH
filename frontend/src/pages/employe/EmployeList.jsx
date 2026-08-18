import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { UserPlus, Users, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import employeService from '@/api/employe/employeService';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Select } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';

import EmployeStatsCards from '@/components/employe/EmployeStatsCards';
import EmployeFilterBar from '@/components/employe/EmployeFilterBar';
import EmployeTable from '@/components/employe/EmployeTable';
import Pagination from '@/components/ui/navigation/Pagination';

const EmployeFormModal = lazy(() => import('./dossier/EmployeFormModal'));

export default function EmployeList() {
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');
    const [employes, setEmployes] = useState([]);
    const [totalEmployes, setTotalEmployes] = useState(0);
    const [statsCounts, setStatsCounts] = useState({ total: 0, actifs: 0, inactifs: 0 });
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'

    // Pagination
    const [currentPage, setCurrentPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);

    // Modales
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [selectedEmployeForEdit, setSelectedEmployeForEdit] = useState(null);

    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [employeToDesactiver, setEmployeToDesactiver] = useState(null);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    const loadEmployes = useCallback(async () => {
        setLoading(true);
        try {
            const params = {
                page: currentPage,
                size: pageSize
            };
            if (searchQuery.trim()) params.q = searchQuery.trim();
            if (statusFilter === 'ACTIVE') params.actif = true;
            if (statusFilter === 'INACTIVE') params.actif = false;

            const [data, stats] = await Promise.all([
                employeService.getEmployes(params),
                employeService.getEmployeStats({ q: searchQuery.trim() || undefined })
            ]);

            setEmployes(data?.content || []);
            setTotalEmployes(data?.totalElements || 0);
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
    }, [searchQuery, statusFilter, currentPage, pageSize]);

    // Réinitialiser la pagination si les filtres changent
    useEffect(() => {
        setCurrentPage(1);
    }, [searchQuery, statusFilter]);

    useEffect(() => {
        loadEmployes();
    }, [loadEmployes]);

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
        setSubmitting(true);
        setActionSuccess('');
        try {
            if (selectedEmployeForEdit) {
                const empId = selectedEmployeForEdit.employe?.idEmploye || selectedEmployeForEdit.idEmploye;
                await employeService.updateEmploye(empId, formData);
                setActionSuccess('Fiche employé modifiée avec succès.');
            } else {
                await employeService.createEmploye(formData);
                setActionSuccess('Nouvel employé créé avec succès.');
            }
            setIsFormOpen(false);
            loadEmployes();
        } catch (err) {
            console.error('Erreur sauvegarde employé:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || "Erreur lors de l'enregistrement de l'employé.");
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
            handleReactiver(empView.idEmploye);
        }
    };

    const handleDesactiverConfirm = async () => {
        if (!employeToDesactiver) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.desactiverEmploye(employeToDesactiver.idEmploye, motifDesactivation);
            setIsDesactivationModalOpen(false);
            setActionSuccess(`L'employé "${employeToDesactiver.nomComplet || employeToDesactiver.matricule}" a été désactivé.`);
            loadEmployes();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la désactivation.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleReactiver = async (id) => {
        setError('');
        try {
            await employeService.reactiverEmploye(id);
            setActionSuccess('Employé réactivé avec succès.');
            loadEmployes();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la réactivation.');
        }
    };

    // Stats globales réelles calculées côté serveur
    const displayTotal = statsCounts.total || totalEmployes;
    const totalActifs = statsCounts.actifs;
    const totalInactifs = statsCounts.inactifs;

    return (
        <div className="space-y-6">
            {/* En-tête de la page */}
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-brand-500 text-white flex items-center justify-center">
                        <Users size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold tracking-tight text-ink-900 dark:text-white">Fiches Employés</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Répertoire du personnel — gestion des fiches individuelles</p>
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <Button
                        onClick={loadEmployes}
                        variant="ghost"
                        size="sm"
                        iconLeft={<RefreshCw size={16} className={loading ? 'animate-spin' : ''} />}
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
                </div>
            </div>

            {/* Notifications */}
            {actionSuccess && (
                <Alert type="success" onClose={() => setActionSuccess('')}>
                    {actionSuccess}
                </Alert>
            )}
            {error && (
                <Alert type="error" onClose={() => setError('')}>
                    {error}
                </Alert>
            )}

            {/* Stats rapides */}
            <EmployeStatsCards
                totalEmployes={displayTotal}
                totalActifs={totalActifs}
                totalInactifs={totalInactifs}
            />

            {/* Barre d'outils : recherche + filtre */}
            <EmployeFilterBar
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                statusFilter={statusFilter}
                onStatusFilterChange={setStatusFilter}
            />

            {/* Tableau des employés */}
            <div className="flex flex-col border border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 shadow-sm">
                <EmployeTable
                    employes={employes}
                    loading={loading}
                    canWrite={canWrite}
                    canValidate={canValidate}
                    onOpenDetail={handleOpenDetail}
                    onOpenEdit={handleOpenEdit}
                    onToggleStatus={handleToggleStatus}
                />
                
                {/* Pagination */}
                {!loading && employes.length > 0 && (
                    <Pagination
                        currentPage={currentPage}
                        totalItems={totalEmployes}
                        pageSize={pageSize}
                        onPageChange={setCurrentPage}
                        onPageSizeChange={setPageSize}
                        className="border-none shadow-none bg-ink-50 dark:bg-ink-950"
                    />
                )}
            </div>

            {/* Modale d'édition / création d'employé */}
            {isFormOpen && (
                <Suspense fallback={null}>
                    <EmployeFormModal
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
        </div>
    );
}
