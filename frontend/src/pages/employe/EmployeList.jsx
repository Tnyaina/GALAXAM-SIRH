import { useState, useEffect } from 'react';
import { UserPlus, Search, Eye, Edit2, Power, Users, RefreshCw, CheckCircle, XCircle, ShieldAlert } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import employeService from '@/api/employeService';
import EmployeFormModal from './EmployeFormModal';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import Loading from '@/components/ui/Loading';

export default function EmployeList() {
    const navigate = useNavigate();
    const [employes, setEmployes] = useState([]);
    const [loading, setLoading] = useState(true);
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState('ALL'); // 'ALL', 'ACTIVE', 'INACTIVE'

    // Modals
    const [isFormOpen, setIsFormOpen] = useState(false);
    const [selectedEmployeForEdit, setSelectedEmployeForEdit] = useState(null);

    const [isDesactivationModalOpen, setIsDesactivationModalOpen] = useState(false);
    const [employeToDesactiver, setEmployeToDesactiver] = useState(null);
    const [motifDesactivation, setMotifDesactivation] = useState('Demission');

    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState('');
    const [actionSuccess, setActionSuccess] = useState('');

    useEffect(() => {
        loadEmployes();
    }, [searchQuery, statusFilter]);

    const loadEmployes = async () => {
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
            setError(err.response?.data || "Erreur lors de l'enregistrement de l'employé.");
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

    const handleDesactiverConfirm = async (e) => {
        e.preventDefault();
        if (!employeToDesactiver) return;
        setError('');
        try {
            await employeService.desactiverEmploye(employeToDesactiver.idEmploye, motifDesactivation);
            setIsDesactivationModalOpen(false);
            setActionSuccess(`L'employé "${employeToDesactiver.nomComplet || employeToDesactiver.matricule}" a été désactivé.`);
            loadEmployes();
        } catch (err) {
            console.error('Erreur désactivation:', err);
            setError(err.response?.data || 'Erreur lors de la désactivation.');
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
            setError(err.response?.data || 'Erreur lors de la réactivation.');
        }
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '-';
        return new Date(dateStr).toLocaleDateString('fr-FR');
    };

    // Stats
    const totalEmployes = employes.length;
    const totalActifs = employes.filter(e => e.estActif).length;
    const totalInactifs = employes.filter(e => !e.estActif).length;

    return (
        <div className="space-y-6">
            {/* En-tête de la page */}
            <div className="bg-white dark:bg-ink-900 rounded-sm shadow-sm border border-ink-150 dark:border-ink-700 p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div className="flex items-center gap-3">
                    <div className="w-12 h-12 bg-brand-500 text-white rounded-sm flex items-center justify-center shadow-md shadow-brand-500/10">
                        <Users size={24} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold tracking-tight text-ink-900 dark:text-white">Fiches Employés</h1>
                        <p className="text-xs text-ink-400 dark:text-ink-300">Répertoire du personnel — gestion des fiches individuelles</p>
                    </div>
                </div>

                <div className="flex items-center gap-3">
                    <button
                        onClick={loadEmployes}
                        className="p-2.5 hover:bg-ink-100 dark:hover:bg-ink-800 text-ink-400 rounded-sm transition-all cursor-pointer"
                        title="Actualiser"
                    >
                        <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
                    </button>
                    <Button
                        onClick={handleOpenCreate}
                        variant="primary"
                        size="sm"
                        iconLeft={<UserPlus size={16} />}
                    >
                        Nouvel Employé
                    </Button>
                </div>
            </div>

            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Stats rapides */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-blue-50 text-[#2563EB] flex items-center justify-center">
                        <Users size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-widest text-ink-400">Total</p>
                        <p className="text-2xl font-bold text-ink-800 dark:text-white">{totalEmployes}</p>
                    </div>
                </div>
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-emerald-50 text-emerald-600 flex items-center justify-center">
                        <CheckCircle size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-widest text-ink-400">Actifs</p>
                        <p className="text-2xl font-bold text-emerald-600">{totalActifs}</p>
                    </div>
                </div>
                <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-red-50 text-red-500 flex items-center justify-center">
                        <XCircle size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-widest text-ink-400">Inactifs</p>
                        <p className="text-2xl font-bold text-red-500">{totalInactifs}</p>
                    </div>
                </div>
            </div>

            {/* Barre d'outils : recherche + filtre */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={18} />
                    <input
                        type="text"
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        placeholder="Rechercher par nom, matricule, CIN..."
                        className="w-full pl-10 pr-4 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm text-ink-900 dark:text-white focus:outline-none focus:border-brand-500"
                    />
                </div>

                <div className="flex items-center gap-1 bg-ink-50 dark:bg-ink-950 border border-ink-200 dark:border-ink-700 p-1">
                    {[
                        { key: 'ALL', label: 'Tous' },
                        { key: 'ACTIVE', label: 'Actifs' },
                        { key: 'INACTIVE', label: 'Inactifs' },
                    ].map(f => (
                        <button
                            key={f.key}
                            onClick={() => setStatusFilter(f.key)}
                            className={`px-3 py-1.5 text-xs font-semibold uppercase tracking-wide transition-all cursor-pointer ${
                                statusFilter === f.key
                                    ? 'bg-white dark:bg-ink-800 text-brand-500 shadow-sm border border-ink-200 dark:border-ink-600'
                                    : 'text-ink-400 hover:text-ink-800 dark:hover:text-white border border-transparent'
                            }`}
                        >
                            {f.label}
                        </button>
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
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full border-collapse text-left text-sm">
                            <thead>
                                <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
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
                                            <span className="font-mono font-semibold text-brand-500">{emp.matricule}</span>
                                        </td>
                                        <td className="px-6 py-4 font-semibold">{emp.nomComplet}</td>
                                        <td className="px-6 py-4 font-mono text-xs">{emp.cinNumero}</td>
                                        <td className="px-6 py-4 text-xs">{emp.telephone}</td>
                                        <td className="px-6 py-4">
                                            <Badge variant="default" size="sm">{emp.situationFamiliale || '-'}</Badge>
                                        </td>
                                        <td className="px-6 py-4">
                                            {emp.estActif ? (
                                                <span className="inline-flex items-center gap-1.5 text-success-500 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-success-500"></span>
                                                    Actif
                                                </span>
                                            ) : (
                                                <span className="inline-flex items-center gap-1.5 text-ink-400 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-ink-400"></span>
                                                    Inactif
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleOpenDetail(emp)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Voir la fiche"
                                                >
                                                    <Eye size={16} />
                                                </button>
                                                <button
                                                    onClick={() => handleOpenEdit(emp)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                                {emp.estActif ? (
                                                    <button
                                                        onClick={() => handleToggleStatus(emp)}
                                                        className="px-2 py-1.5 text-xs font-bold text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                                    >
                                                        Désactiver
                                                    </button>
                                                ) : (
                                                    <button
                                                        onClick={() => handleToggleStatus(emp)}
                                                        className="px-2 py-1.5 text-xs font-bold text-success-500 hover:bg-success-500/10 rounded-sm transition-all cursor-pointer"
                                                    >
                                                        Activer
                                                    </button>
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

            {/* Modals */}
            <EmployeFormModal
                isOpen={isFormOpen}
                onClose={() => setIsFormOpen(false)}
                onSave={handleSaveForm}
                employeData={selectedEmployeForEdit}
                isSubmitting={submitting}
            />

            {/* Modale de désactivation */}
            {isDesactivationModalOpen && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white flex items-center gap-2">
                            <Power size={18} className="text-danger-500" />
                            Désactiver l'employé
                        </div>
                        <form onSubmit={handleDesactiverConfirm}>
                            <div className="p-6 space-y-4">
                                <p className="text-sm text-ink-500 dark:text-ink-400">
                                    Veuillez spécifier le motif de désactivation de l'employé{' '}
                                    <strong className="text-ink-800 dark:text-white">{employeToDesactiver?.nomComplet || employeToDesactiver?.matricule}</strong>.
                                </p>

                                <div className="space-y-1">
                                    <label className="text-xs font-bold uppercase tracking-wider text-ink-800 dark:text-ink-50">Motif de Désactivation</label>
                                    <select
                                        value={motifDesactivation}
                                        onChange={(e) => setMotifDesactivation(e.target.value)}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        <option value="Demission">Démission</option>
                                        <option value="Licenciement">Licenciement</option>
                                        <option value="Fin_contrat">Fin de contrat</option>
                                        <option value="Retraite">Retraite</option>
                                        <option value="Deces">Décès</option>
                                        <option value="Autre">Autre</option>
                                    </select>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setIsDesactivationModalOpen(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-danger-500 hover:bg-danger-600 text-white rounded-sm transition-colors cursor-pointer"
                                >
                                    Confirmer la Désactivation
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
