import { useState, useEffect, useCallback, useMemo, lazy, Suspense } from 'react';
import { UserPlus, Users, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import employeService from '@/api/employe/employeService';
import Button from '@/components/ui/forms/Button';
import Alert from '@/components/ui/feedback/Alert';
import { Select } from '@/components/ui/forms/Input';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';
import { useToast } from '@/components/ui/feedback/Toast';
import { useContractReferentiels } from '@/hooks/useContractReferentiels';
import useDebounce from '@/hooks/useDebounce';

import EmployeStatsCards from '@/components/employe/EmployeStatsCards';
import EmployeFilterBar from '@/components/employe/EmployeFilterBar';
import EmployeTable from '@/components/employe/EmployeTable';
import Pagination from '@/components/ui/navigation/Pagination';
import PageHeader from '@/components/ui/navigation/PageHeader';

const EmployeFormModal = lazy(() => import('./dossier/EmployeFormModal'));

export default function EmployeList() {
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');

    // Référentiels de structure (Sites, Départements, Services)
    const { sites = [], departements = [], services = [] } = useContractReferentiels(true);

    const [rawEmployes, setRawEmployes] = useState([]);
    const [statsCounts, setStatsCounts] = useState({ total: 0, actifs: 0, inactifs: 0 });
    const [loading, setLoading] = useState(true);

    // Filtres
    const [searchQuery, setSearchQuery] = useState('');
    const debouncedSearch = useDebounce(searchQuery, 350);
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'
    const [siteFilter, setSiteFilter] = useState('');
    const [departementFilter, setDepartementFilter] = useState('');
    const [serviceFilter, setServiceFilter] = useState('');

    // Tri (Preset par défaut : Hiérarchique par Département -> Service -> Nom)
    const [sortPreset, setSortPreset] = useState('HIERARCHICAL');
    const [columnSortConfig, setColumnSortConfig] = useState({ key: '', direction: 'asc' });

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
    const toast = useToast();

    const loadEmployes = useCallback(async () => {
        setLoading(true);
        try {
            // Charger la liste globale pour permettre le multi-filtrage et multi-tri instantané
            const [data, stats] = await Promise.all([
                employeService.getEmployes({ size: 1000 }),
                employeService.getEmployeStats()
            ]);

            const list = data?.content || (Array.isArray(data) ? data : []);
            setRawEmployes(list);

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
    }, []);

    useEffect(() => {
        loadEmployes();
    }, [loadEmployes]);

    // Réinitialiser la pagination si les filtres changent
    useEffect(() => {
        setCurrentPage(1);
    }, [searchQuery, statusFilter, siteFilter, departementFilter, serviceFilter]);

    // 1. Filtrage combiné en mémoire
    const filteredEmployes = useMemo(() => {
        return rawEmployes.filter((emp) => {
            // Recherche texte
            if (searchQuery.trim()) {
                const q = searchQuery.trim().toLowerCase();
                const fullName = (emp.nomComplet || `${emp.prenom || ''} ${emp.nom || ''}`).toLowerCase();
                const matricule = (emp.matricule || '').toLowerCase();
                const cin = (emp.cinNumero || '').toLowerCase();
                const tel = (emp.telephone || '').toLowerCase();
                const fn = (emp.nomFonction || emp.fonction || '').toLowerCase();
                const matches = fullName.includes(q) || matricule.includes(q) || cin.includes(q) || tel.includes(q) || fn.includes(q);
                if (!matches) return false;
            }

            // Statut
            if (statusFilter === 'ACTIVE' && !emp.estActif) return false;
            if (statusFilter === 'INACTIVE' && emp.estActif) return false;

            // Site
            if (siteFilter) {
                const empSiteId = emp.idSite || emp.id_site;
                if (String(empSiteId) !== String(siteFilter)) return false;
            }

            // Département
            if (departementFilter) {
                const empDeptId = emp.idDepartement || emp.id_departement;
                if (String(empDeptId) !== String(departementFilter)) return false;
            }

            // Service
            if (serviceFilter) {
                const empSrvId = emp.idService || emp.id_service;
                if (String(empSrvId) !== String(serviceFilter)) return false;
            }

            return true;
        });
    }, [rawEmployes, searchQuery, statusFilter, siteFilter, departementFilter, serviceFilter]);

    // 2. Multi-Tri Logique et Hiérarchique
    const sortedEmployes = useMemo(() => {
        const list = [...filteredEmployes];

        // Si l'utilisateur a cliqué sur un en-tête de colonne spécifique
        if (columnSortConfig.key) {
            const { key, direction } = columnSortConfig;
            const factor = direction === 'asc' ? 1 : -1;

            return list.sort((a, b) => {
                let valA;
                let valB;

                switch (key) {
                    case 'matricule':
                        valA = a.matricule || '';
                        valB = b.matricule || '';
                        break;
                    case 'departement':
                        valA = a.nomDepartement || a.departement || '';
                        valB = b.nomDepartement || b.departement || '';
                        break;
                    case 'fonction':
                        valA = a.nomFonction || a.fonction || '';
                        valB = b.nomFonction || b.fonction || '';
                        break;
                    case 'site':
                        valA = a.nomSite || a.site || '';
                        valB = b.nomSite || b.site || '';
                        break;
                    case 'dateEmbauche':
                        valA = a.dateEmbauche || a.dateDeclaration || a.createdAt || '';
                        valB = b.dateEmbauche || b.dateDeclaration || b.createdAt || '';
                        break;
                    case 'statut':
                        valA = a.estActif ? 'A' : 'Z';
                        valB = b.estActif ? 'A' : 'Z';
                        break;
                    default:
                        valA = a.nomComplet || `${a.nom || ''} ${a.prenom || ''}`;
                        valB = b.nomComplet || `${b.nom || ''} ${b.prenom || ''}`;
                }

                return valA.localeCompare(valB, 'fr', { numeric: true, sensitivity: 'base' }) * factor;
            });
        }

        // Sinon appliquer le preset sélectionné
        return list.sort((a, b) => {
            const getDept = (emp) => emp.nomDepartement || emp.departement || '';
            const getService = (emp) => emp.nomService || emp.service || '';
            const getSite = (emp) => emp.nomSite || emp.site || '';
            const getNom = (emp) => emp.nomComplet || `${emp.nom || ''} ${emp.prenom || ''}`;
            const getMatricule = (emp) => emp.matricule || '';
            const getDate = (emp) => emp.dateEmbauche || emp.dateDeclaration || emp.createdAt || '';

            switch (sortPreset) {
                case 'HIERARCHICAL':
                    // Département → Service → Nom
                    return (
                        getDept(a).localeCompare(getDept(b), 'fr') ||
                        getService(a).localeCompare(getService(b), 'fr') ||
                        getNom(a).localeCompare(getNom(b), 'fr')
                    );
                case 'SITE_SERVICE':
                    // Site → Service → Nom
                    return (
                        getSite(a).localeCompare(getSite(b), 'fr') ||
                        getService(a).localeCompare(getService(b), 'fr') ||
                        getNom(a).localeCompare(getNom(b), 'fr')
                    );
                case 'NOM_ASC':
                    return getNom(a).localeCompare(getNom(b), 'fr');
                case 'NOM_DESC':
                    return getNom(b).localeCompare(getNom(a), 'fr');
                case 'MATRICULE_ASC':
                    return getMatricule(a).localeCompare(getMatricule(b), 'fr', { numeric: true });
                case 'MATRICULE_DESC':
                    return getMatricule(b).localeCompare(getMatricule(a), 'fr', { numeric: true });
                case 'ANCIENNETE_DESC':
                    return getDate(a).localeCompare(getDate(b));
                case 'ANCIENNETE_ASC':
                    return getDate(b).localeCompare(getDate(a));
                default:
                    return getNom(a).localeCompare(getNom(b), 'fr');
            }
        });
    }, [filteredEmployes, sortPreset, columnSortConfig]);

    const totalPages = Math.ceil(sortedEmployes.length / pageSize) || 1;

    // Sécurité ajustement : si la page courante dépasse le total de pages
    useEffect(() => {
        if (currentPage > totalPages && totalPages > 0) {
            setCurrentPage(1);
        }
    }, [totalPages, currentPage]);

    // 3. Découpage pagination
    const paginatedEmployes = useMemo(() => {
        const start = (currentPage - 1) * pageSize;
        return sortedEmployes.slice(start, start + pageSize);
    }, [sortedEmployes, currentPage, pageSize]);

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
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la désactivation.');
        } finally {
            setSubmitting(false);
        }
    };

    const handleReactiver = async (id) => {
        if (submitting) return;
        setError('');
        setSubmitting(true);
        try {
            await employeService.reactiverEmploye(id);
            toast({ title: 'Succès', message: 'Employé réactivé avec succès.', type: 'success' });
            loadEmployes();
        } catch (err) {
            console.error('Erreur réactivation:', err);
            setError(err.response?.data?.message || (typeof err.response?.data === 'string' ? err.response.data : null) || 'Erreur lors de la réactivation.');
        } finally {
            setSubmitting(false);
        }
    };

    const displayTotal = statsCounts.total || rawEmployes.length;
    const totalActifs = statsCounts.actifs;
    const totalInactifs = statsCounts.inactifs;

    return (
        <div className="space-y-6 max-w-7xl 2xl:max-w-[1600px] mx-auto w-full font-sans">
            <PageHeader
                title="Fiches Employés"
                subtitle="Répertoire du personnel — gestion des fiches individuelles, carrières et documents"
                icon={Users}
                backLink="/dashboard"
                backLabel="Retour au Tableau de bord"
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

            {/* Stats rapides */}
            <EmployeStatsCards
                totalEmployes={displayTotal}
                totalActifs={totalActifs}
                totalInactifs={totalInactifs}
            />

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
                totalFound={sortedEmployes.length}
            />

            {/* Tableau des employés */}
            <div className="flex flex-col border border-ink-150 dark:border-ink-700 bg-white dark:bg-ink-900 shadow-sm">
                <EmployeTable
                    employes={paginatedEmployes}
                    loading={loading}
                    canWrite={canWrite}
                    canValidate={canValidate}
                    onOpenDetail={handleOpenDetail}
                    onOpenEdit={handleOpenEdit}
                    onToggleStatus={handleToggleStatus}
                    sortConfig={columnSortConfig}
                    onSortChange={handleSortChange}
                />
                
                {/* Pagination */}
                {!loading && sortedEmployes.length > 0 && (
                    <Pagination
                        currentPage={currentPage}
                        totalItems={sortedEmployes.length}
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
