import { useState, useEffect } from 'react';
import { Search, UserPlus, Edit2, ShieldAlert, CheckCircle, XCircle } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';

export default function UserManagementTab() {
    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // États pour les modales
    const [showUserModal, setShowUserModal] = useState(false);
    const [showDeactivateModal, setShowDeactivateModal] = useState(false);
    const [selectedUser, setSelectedUser] = useState(null);

    // Formulaire utilisateur (création / modif)
    const [formData, setFormData] = useState({
        identifiant: '',
        motDePasse: '',
        confirmPassword: '',
        idRole: ''
    });

    const [showPassword, setShowPassword] = useState(false);

    // Formulaire désactivation
    const [deactivateMotif, setDeactivateMotif] = useState('');

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [usersData, rolesData] = await Promise.all([
                referentielService.getUsers(),
                referentielService.getRoles()
            ]);
            setUsers(usersData);
            setRoles(rolesData.filter(r => r.estActif)); // N'autoriser que les rôles actifs pour l'affectation
        } catch (err) {
            setError("Erreur lors du chargement des données. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreateModal = () => {
        setSelectedUser(null);
        setFormData({
            identifiant: '',
            motDePasse: '',
            confirmPassword: '',
            idRole: roles.length > 0 ? roles[0].idRole : ''
        });
        setShowPassword(false);
        setActionError(null);
        setShowUserModal(true);
    };

    const handleOpenEditModal = (user) => {
        setSelectedUser(user);
        setFormData({
            identifiant: user.identifiant,
            motDePasse: '', // Vide par défaut
            confirmPassword: '',
            idRole: user.idRole
        });
        setShowPassword(false);
        setActionError(null);
        setShowUserModal(true);
    };

    const handleOpenDeactivateModal = (user) => {
        setSelectedUser(user);
        setDeactivateMotif('');
        setActionError(null);
        setShowDeactivateModal(true);
    };

    const handleSaveUser = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!formData.identifiant.trim()) {
            setActionError("L'identifiant est obligatoire.");
            return;
        }

        // Si création
        if (!selectedUser) {
            if (!formData.motDePasse) {
                setActionError("Le mot de passe est obligatoire pour la création.");
                return;
            }
            if (formData.motDePasse !== formData.confirmPassword) {
                setActionError("La confirmation du mot de passe ne correspond pas.");
                return;
            }
        } else {
            // Si modification avec saisie d'un nouveau mot de passe
            if (formData.motDePasse && formData.motDePasse !== formData.confirmPassword) {
                setActionError("La confirmation du mot de passe ne correspond pas.");
                return;
            }
        }

        if (!formData.idRole) {
            setActionError("Le rôle est obligatoire.");
            return;
        }

        try {
            if (selectedUser) {
                // Modification
                const updatePayload = {
                    identifiant: formData.identifiant,
                    idRole: parseInt(formData.idRole)
                };
                if (formData.motDePasse) {
                    updatePayload.motDePasse = formData.motDePasse;
                }
                await referentielService.updateUser(selectedUser.idUtilisateur, updatePayload);
                setActionSuccess(`L'utilisateur "${formData.identifiant}" a été modifié avec succès.`);
            } else {
                // Création
                const createPayload = {
                    identifiant: formData.identifiant,
                    motDePasse: formData.motDePasse,
                    idRole: parseInt(formData.idRole)
                };
                await referentielService.createUser(createPayload);
                setActionSuccess(`L'utilisateur "${formData.identifiant}" a été créé avec succès.`);
            }
            setShowUserModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Une erreur est survenue lors de l'enregistrement.");
        }
    };

    const handleDeactivate = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!deactivateMotif.trim()) {
            setActionError("Le motif de désactivation est obligatoire.");
            return;
        }

        try {
            await referentielService.deactivateUser(selectedUser.idUtilisateur, deactivateMotif);
            setActionSuccess(`Le compte de "${selectedUser.identifiant}" a été désactivé.`);
            setShowDeactivateModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Une erreur est survenue lors de la désactivation.");
        }
    };

    const handleActivate = async (user) => {
        setActionError(null);
        setActionSuccess(null);
        try {
            await referentielService.activateUser(user.idUtilisateur);
            setActionSuccess(`Le compte de "${user.identifiant}" a été réactivé.`);
            loadData();
        } catch (err) {
            setError(err.response?.data || "Impossible de réactiver l'utilisateur.");
        }
    };

    const filteredUsers = users.filter(u =>
        u.identifiant.toLowerCase().includes(search.toLowerCase()) ||
        u.roleLibelle.toLowerCase().includes(search.toLowerCase())
    );

    if (loading && users.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications d'action */}
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

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={18} />
                    <input
                        type="text"
                        placeholder="Rechercher un identifiant, rôle..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm text-ink-900 dark:text-white focus:outline-none focus:border-brand-500"
                    />
                </div>
                <button
                    onClick={handleOpenCreateModal}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <UserPlus size={16} />
                    Nouvel Utilisateur
                </button>
            </div>

            {/* Tableau des utilisateurs */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                <th className="px-6 py-4">Identifiant</th>
                                <th className="px-6 py-4">Rôle</th>
                                <th className="px-6 py-4">Statut</th>
                                <th className="px-6 py-4">Dernière Désactivation</th>
                                <th className="px-6 py-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {filteredUsers.length > 0 ? (
                                filteredUsers.map((user) => (
                                    <tr key={user.idUtilisateur} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                        <td className="px-6 py-4 font-semibold">{user.identifiant}</td>
                                        <td className="px-6 py-4">
                                            <span className="inline-flex items-center px-2.5 py-0.5 rounded-sm text-xs font-semibold bg-brand-500/10 text-brand-500">
                                                {user.roleLibelle}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4">
                                            {user.estActif ? (
                                                <span className="inline-flex items-center gap-1.5 text-success-500 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-success-500"></span>
                                                    Actif
                                                </span>
                                            ) : (
                                                <span className="inline-flex items-center gap-1.5 text-ink-400 font-semibold text-xs">
                                                    <span className="w-1.5 h-1.5 rounded-full bg-ink-400"></span>
                                                    Désactivé
                                                </span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-xs text-ink-400 max-w-xs truncate">
                                            {!user.estActif && user.dateDesactivation ? (
                                                <div>
                                                    <p>{new Date(user.dateDesactivation).toLocaleString()}</p>
                                                    <p className="italic text-danger-500 font-medium">Motif : {user.motifDesactivation}</p>
                                                </div>
                                            ) : (
                                                <span className="text-ink-300">-</span>
                                            )}
                                        </td>
                                        <td className="px-6 py-4 text-right">
                                            <div className="flex justify-end gap-2">
                                                <button
                                                    onClick={() => handleOpenEditModal(user)}
                                                    className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                    title="Modifier"
                                                >
                                                    <Edit2 size={16} />
                                                </button>
                                                {user.estActif ? (
                                                    <button
                                                        onClick={() => handleOpenDeactivateModal(user)}
                                                        className="px-2 py-1.5 text-xs font-bold text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                                    >
                                                        Désactiver
                                                    </button>
                                                ) : (
                                                    <button
                                                        onClick={() => handleActivate(user)}
                                                        className="px-2 py-1.5 text-xs font-bold text-success-500 hover:bg-success-500/10 rounded-sm transition-all cursor-pointer"
                                                    >
                                                        Activer
                                                    </button>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                ))
                            ) : (
                                <tr>
                                    <td colSpan="5" className="px-6 py-12 text-center text-ink-400">
                                        Aucun utilisateur trouvé.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Modale d'ajout/modification utilisateur */}
            {showUserModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedUser ? "Modifier l'utilisateur" : "Nouvel utilisateur"}
                        </div>
                        <form onSubmit={handleSaveUser}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Identifiant</label>
                                    <input
                                        type="text"
                                        value={formData.identifiant}
                                        onChange={(e) => setFormData({ ...formData, identifiant: e.target.value })}
                                        disabled={!!selectedUser}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                        placeholder="Ex: jean.dupont"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">
                                        Mot de passe {selectedUser && "(Laisser vide pour ne pas modifier)"}
                                    </label>
                                    <div className="relative">
                                        <input
                                            type={showPassword ? "text" : "password"}
                                            value={formData.motDePasse}
                                            onChange={(e) => setFormData({ ...formData, motDePasse: e.target.value })}
                                            className="w-full pl-3 pr-16 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                            placeholder="Mot de passe"
                                        />
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="absolute right-2 top-1/2 -translate-y-1/2 text-xs font-bold text-brand-500 hover:text-brand-600 cursor-pointer"
                                        >
                                            {showPassword ? "Masquer" : "Afficher"}
                                        </button>
                                    </div>
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">
                                        Confirmer le mot de passe
                                    </label>
                                    <input
                                        type={showPassword ? "text" : "password"}
                                        value={formData.confirmPassword}
                                        onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        placeholder="Confirmation"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Rôle</label>
                                    <select
                                        value={formData.idRole}
                                        onChange={(e) => setFormData({ ...formData, idRole: e.target.value })}
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    >
                                        <option value="" disabled>Sélectionner un rôle</option>
                                        {roles.map((role) => (
                                            <option key={role.idRole} value={role.idRole}>
                                                {role.libelle}
                                            </option>
                                        ))}
                                    </select>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowUserModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                                >
                                    Enregistrer
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale de désactivation logique */}
            {showDeactivateModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            Confirmer la désactivation
                        </div>
                        <form onSubmit={handleDeactivate}>
                            <div className="p-6 space-y-4">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {actionError}
                                    </div>
                                )}
                                <p className="text-sm text-ink-500 dark:text-ink-400">
                                    Vous vous apprêtez à désactiver le compte de <strong>{selectedUser?.identifiant}</strong>. 
                                    L'utilisateur ne pourra plus s'authentifier.
                                </p>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Motif de la désactivation (Obligatoire)</label>
                                    <textarea
                                        rows="3"
                                        value={deactivateMotif}
                                        onChange={(e) => setDeactivateMotif(e.target.value)}
                                        placeholder="Ex: Fin de contrat, Départ de l'entreprise..."
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 resize-none"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowDeactivateModal(false)}
                                    className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                                >
                                    Annuler
                                </button>
                                <button
                                    type="submit"
                                    className="px-4 py-2 text-sm font-semibold bg-danger-500 hover:bg-danger-600 text-white rounded-sm transition-colors cursor-pointer"
                                >
                                    Désactiver le compte
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
