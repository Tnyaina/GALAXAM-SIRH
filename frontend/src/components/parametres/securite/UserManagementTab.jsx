import { useState, useEffect } from 'react';
import { Search, UserPlus, Edit2, ShieldAlert, X } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select, Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import { fmtDate } from '@/utils/paieUtils';

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

    // Formulaire utilisateur
    const [formData, setFormData] = useState({
        identifiant: '',
        motDePasse: '',
        confirmPassword: '',
        idRole: ''
    });

    const [showPassword, setShowPassword] = useState(false);
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
            setRoles(rolesData.filter(r => r.estActif));
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
            idRole: ''
        });
        setShowPassword(false);
        setActionError(null);
        setShowUserModal(true);
    };

    const handleOpenEditModal = (user) => {
        setSelectedUser(user);
        setFormData({
            identifiant: user.identifiant,
            motDePasse: '',
            confirmPassword: '',
            idRole: String(user.idRole)
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
            if (formData.motDePasse && formData.motDePasse !== formData.confirmPassword) {
                setActionError("La confirmation du mot de passe ne correspond pas.");
                return;
            }
        }

        if (!formData.idRole) {
            setActionError("Veuillez sélectionner un rôle.");
            return;
        }

        try {
            if (selectedUser) {
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
        (u.roleLibelle && u.roleLibelle.toLowerCase().includes(search.toLowerCase()))
    );

    if (loading && users.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={8} columns={5} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && <Alert type="success" message={actionSuccess} />}
            {error && <Alert type="error" message={error} />}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" size={16} />
                    <input
                        type="text"
                        placeholder="Rechercher par identifiant, rôle..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="w-full pl-9 pr-4 py-2 border border-[var(--color-border)] rounded-none bg-[var(--color-surface)] text-sm text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                    />
                </div>
                <Button
                    onClick={handleOpenCreateModal}
                    variant="primary"
                    size="sm"
                    iconLeft={<UserPlus size={16} />}
                >
                    Nouvel Utilisateur
                </Button>
            </div>

            {/* Tableau des utilisateurs */}
            <Table>
                <Thead>
                    <Th>Identifiant</Th>
                    <Th>Rôle</Th>
                    <Th>Statut</Th>
                    <Th>Dernière Désactivation</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {filteredUsers.length > 0 ? (
                        filteredUsers.map((user) => (
                            <Tr key={user.idUtilisateur}>
                                <Td className="font-bold text-[var(--color-fg)]">{user.identifiant}</Td>
                                <Td>
                                    <Badge variant="primary" size="sm">
                                        {user.roleLibelle}
                                    </Badge>
                                </Td>
                                <Td>
                                    <Badge variant={user.estActif ? 'success' : 'default'} size="sm" dot>
                                        {user.estActif ? 'Actif' : 'Désactivé'}
                                    </Badge>
                                </Td>
                                <Td muted className="text-xs max-w-xs truncate">
                                    {!user.estActif && user.dateDesactivation ? (
                                        <div>
                                            <p>{fmtDate(user.dateDesactivation)}</p>
                                            <p className="text-[var(--color-danger-500)] font-medium">Motif : {user.motifDesactivation}</p>
                                        </div>
                                    ) : (
                                        <span>-</span>
                                    )}
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-2">
                                        <button
                                            onClick={() => handleOpenEditModal(user)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={16} />
                                        </button>
                                        {user.estActif ? (
                                            <button
                                                onClick={() => handleOpenDeactivateModal(user)}
                                                className="px-2 py-1 text-xs font-bold text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none  cursor-pointer uppercase"
                                            >
                                                Désactiver
                                            </button>
                                        ) : (
                                            <button
                                                onClick={() => handleActivate(user)}
                                                className="px-2 py-1 text-xs font-bold text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10 rounded-none  cursor-pointer uppercase"
                                            >
                                                Activer
                                            </button>
                                        )}
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={5} className="p-0">
                                <EmptyState title="Aucun utilisateur" description="Aucun utilisateur trouvé." />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale d'ajout/modification utilisateur */}
            {showUserModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <UserPlus size={18} className="text-[var(--color-brand-500)]" />
                                {selectedUser ? "Modifier l'utilisateur" : "Nouvel utilisateur"}
                            </h3>
                            <button onClick={() => setShowUserModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSaveUser} className="p-6 space-y-4">
                            {actionError && <Alert type="error" message={actionError} />}
                            <Input
                                label="Identifiant"
                                required
                                disabled={!!selectedUser}
                                value={formData.identifiant}
                                onChange={(e) => setFormData({ ...formData, identifiant: e.target.value })}
                                placeholder="Ex: jean.dupont"
                            />
                            <div className="space-y-1">
                                <Input
                                    label={`Mot de passe ${selectedUser ? "(Laisser vide pour ne pas modifier)" : ""}`}
                                    type={showPassword ? "text" : "password"}
                                    required={!selectedUser}
                                    value={formData.motDePasse}
                                    onChange={(e) => setFormData({ ...formData, motDePasse: e.target.value })}
                                    placeholder="Mot de passe"
                                    iconRight={
                                        <button
                                            type="button"
                                            onClick={() => setShowPassword(!showPassword)}
                                            className="text-xs font-bold text-[var(--color-brand-500)] hover:text-[var(--color-brand-600)] cursor-pointer"
                                        >
                                            {showPassword ? "Masquer" : "Afficher"}
                                        </button>
                                    }
                                />
                            </div>
                            <Input
                                label="Confirmer le mot de passe"
                                type={showPassword ? "text" : "password"}
                                required={!selectedUser && !!formData.motDePasse}
                                value={formData.confirmPassword}
                                onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                                placeholder="Confirmation"
                            />
                            <Select
                                label="Rôle"
                                required
                                value={formData.idRole}
                                onChange={(e) => setFormData({ ...formData, idRole: e.target.value })}
                            >
                                <option value="">-- Sélectionner un rôle --</option>
                                {roles.map((role) => (
                                    <option key={role.idRole} value={role.idRole}>
                                        {role.libelle} ({role.codeRole})
                                    </option>
                                ))}
                            </Select>

                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowUserModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="primary" size="sm">
                                    Enregistrer
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}

            {/* Modale de désactivation logique */}
            {showDeactivateModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <ShieldAlert size={18} className="text-[var(--color-danger-500)]" />
                                Confirmer la désactivation
                            </h3>
                            <button onClick={() => setShowDeactivateModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleDeactivate} className="p-6 space-y-4">
                            {actionError && <Alert type="error" message={actionError} />}
                            <p className="text-sm text-[var(--color-fg-muted)]">
                                Vous vous apprêtez à désactiver le compte de <strong className="text-[var(--color-fg)]">{selectedUser?.identifiant}</strong>. 
                                L'utilisateur ne pourra plus s'authentifier.
                            </p>
                            <Textarea
                                label="Motif de la désactivation"
                                required
                                rows={3}
                                value={deactivateMotif}
                                onChange={(e) => setDeactivateMotif(e.target.value)}
                                placeholder="Ex: Fin de contrat, Départ de l'entreprise..."
                            />
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowDeactivateModal(false)} variant="ghost" size="sm">
                                    Annuler
                                </Button>
                                <Button type="submit" variant="danger" size="sm">
                                    Désactiver le compte
                                </Button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}