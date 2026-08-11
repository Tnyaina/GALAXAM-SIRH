import { useState, useEffect, useCallback, lazy, Suspense } from 'react';
import { UserPlus, Search, Eye, Edit2, Users, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import employeService from '@/api/employe/employeService';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';
import Alert from '@/components/ui/Alert';
import Input, { Select } from '@/components/ui/Input';
import Card, { CardBody } from '@/components/ui/Card';
import ConfirmDialog from '@/components/ui/ConfirmDialog';
import { useAuth } from '@/context/AuthContext';

const EmployeFormModal = lazy(() => import('./dossier/EmployeFormModal'));

export default function EmployeList() {
    const navigate = useNavigate();
    const { hasPermission } = useAuth();
    const canWrite = hasPermission('EMPLOYE_WRITE');
    const canValidate = hasPermission('EMPLOYE_VALIDATE');
    const [employes, setEmployes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'

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
            const params = {};
            if (searchQuery.trim()) params.q = searchQuery.trim();
            if (statusFilter === 'ACTIVE') params.actif = true;
            if (statusFilter === 'INACTIVE') params.actif = false;

            const data = await employeService.getEmployes(params);
            setEmployes(data || []);
            setError('');
        } catch (err) {
            console.error('Erreur chargement employés:', err);
            setError('Impossible de charger la liste des employés.');
        } finally {
            setLoading(false);
        }
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

    // Stats
    const totalEmployes = employes.length;
    const totalActifs = employes.filter(e => e.estActif).length;
    const totalInactifs = employes.filter(e => !e.estActif).length;

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
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-brand-50 dark:bg-brand-900/30 text-brand-500 flex items-center justify-center">
                            <Users size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Total</p>
                            <p className="text-2xl font-bold text-ink-800 dark:text-white">{totalEmployes}</p>
                        </div>
                    </CardBody>
                </Card>
                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-success-500/10 text-success-500 flex items-center justify-center">
                            <Users size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Actifs</p>
                            <p className="text-2xl font-bold text-success-500">{totalActifs}</p>
                        </div>
                    </CardBody>
                </Card>
                <Card bordered shadow="sm">
                    <CardBody className="p-5 flex items-center gap-4">
                        <div className="w-11 h-11 bg-danger-500/10 text-danger-500 flex items-center justify-center">
                            <Users size={20} />
                        </div>
                        <div>
                            <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Inactifs</p>
                            <p className="text-2xl font-bold text-danger-500">{totalInactifs}</p>
                        </div>
                    </CardBody>
                </Card>
            </div>

            {/* Barre d'outils : recherche + filtre */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="flex-1 max-w-md">
                    <Input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Rechercher par nom, matricule, CIN..."
                        icon={<Search size={18} />}
                    />
                </div>

                <div className="flex items-center gap-1 bg-ink-50 dark:bg-ink-950 border border-ink-200 dark:border-ink-700 p-1">
                    {[
                        { key: 'ALL', label: 'Tous' },
                        { key: 'ACTIVE', label: 'Actifs' },
                        { key: 'INACTIVE', label: 'Inactifs' },
                    ].map(f => (
                        <Button
                            key={f.key}
                            size="xs"
                            variant={statusFilter === f.key ? 'primary' : 'ghost'}
                            onClick={() => setStatusFilter(f.key)}
                        >
                            {f.label}
                        </Button>
                    ))}
                </div>
            </div>

            {/* Tableau des employés */}
            {loading ? (
                <div className="flex justify-center items-center py-12">
                    <Loading size="lg" label="Chargement des employés..." />
                </div>
            ) : employes.length === 0 ? (
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-12 text-center">
                    <Users size={40} className="mx-auto text-ink-300 mb-3" />
                    <p className="text-sm font-semibold text-ink-800 dark:text-white">Aucun employé trouvé</p>
                    <p className="text-xs text-ink-400 mt-1">Aucun enregistrement ne correspond à vos critères.</p>
                </div>
            ) : (
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase text-[10px] tracking-widest">
                                    <th className="px-6 py-4">Matricule</th>
                                    <th className="px-6 py-4">Nom complet</th>
                                    <th className="px-6 py-4">CIN</th>
                                    <th className="px-6 py-4">Téléphone</th>
                                    <th className="px-6 py-4">Situation</th>
                                    <th className="px-6 py-4">Statut</th>
                                    <th className="px-6 py-4 text-right">Actions</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                {employes.map((emp) => (
                                    <tr key={emp.idEmploye} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-6 py-4">
                                            <span className="font-mono font-bold text-brand-500">{emp.matricule}</span>
                                        </td>
                                        <td className="px-6 py-4 font-semibold">{emp.nomComplet}</td>
                                        <td className="px-6 py-4 font-mono text-xs">{emp.cinNumero}</td>
                                        <td className="px-6 py-4 text-xs">{emp.telephone}</td>
                                        <td className="px-6 py-4">
                                            <Badge variant="default" size="sm">{emp.situationFamiliale || '-'}</Badge>
                                        </td>
                                        <td className="px-6 py-4">
                                            {emp.estActif ? (
                                                <Badge variant="success" size="sm" dot>Actif</Badge>
                                            ) : (
                                                <Badge variant="default" size="sm" dot>Inactif</Badge>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end items-center gap-2">
                                                <button
                                                    onClick={() => handleOpenDetail(emp)}
                                                    className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors cursor-pointer"
                                                    title="Voir la fiche"
                                                >
                                                    <Eye size={16} />
                                                </button>
                                                {canWrite && (
                                                    <button
                                                        onClick={() => handleOpenEdit(emp)}
                                                        className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={16} />
                                                    </button>
                                                )}
                                                {canValidate && (
                                                    emp.estActif ? (
                                                        <Button
                                                            size="xs"
                                                            variant="danger"
                                                            onClick={() => handleToggleStatus(emp)}
                                                        >
                                                            Désactiver
                                                        </Button>
                                                    ) : (
                                                        <Button
                                                            size="xs"
                                                            variant="success"
                                                            onClick={() => handleToggleStatus(emp)}
                                                        >
                                                            Réactiver
                                                        </Button>
                                                    )
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}

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
