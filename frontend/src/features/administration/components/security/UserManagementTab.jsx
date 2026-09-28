import { useState, useEffect } from 'react';
import { Search, UserPlus, Edit2, ShieldAlert, Eye, UserCheck, Power } from 'lucide-react';
import referentielService from '@/features/administration/api/referentielService';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select, Textarea } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';
import useDebounce from '@/hooks/useDebounce';

export default function UserManagementTab() {
    // État local : utilisateurs, recherche, formulaires et confirmations.
    const [users, setUsers] = useState([]);
    const [roles, setRoles] = useState([]);
    const [search, setSearch] = useState('');
    const debouncedSearch = useDebounce(search, 350);
    const [showInactive, setShowInactive] = useState(false);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();

    // États pour les modales
    const [showUserModal, setShowUserModal] = useState(false);
    const [showDeactivateModal, setShowDeactivateModal] = useState(false);
    const [selectedUser, setSelectedUser] = useState(null);

    // Confirmation dialog pour réactivation
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'primary',
        onConfirm: null,
        loading: false
    });

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

    // Effet : charge utilisateurs et rôles à l'ouverture.
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
        setModalError(null);
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
        setModalError(null);
        setShowUserModal(true);
    };

    const handleOpenDeactivateModal = (user) => {
        setSelectedUser(user);
        setDeactivateMotif('');
        setModalError(null);
        setShowDeactivateModal(true);
    };

    const handleSaveUser = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);

        if (!formData.identifiant.trim()) {
            setModalError("L'identifiant est obligatoire.");
            return;
        }

        if (!selectedUser) {
            if (!formData.motDePasse) {
                setModalError("Le mot de passe est obligatoire pour la création.");
                return;
            }
            if (formData.motDePasse !== formData.confirmPassword) {
                setModalError("La confirmation du mot de passe ne correspond pas.");
                return;
            }
        } else {
            if (formData.motDePasse && formData.motDePasse !== formData.confirmPassword) {
                setModalError("La confirmation du mot de passe ne correspond pas.");
                return;
            }
        }

        if (!formData.idRole) {
            setModalError("Veuillez sélectionner un rôle.");
            return;
        }

        setSubmitting(true);
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
                toast({ title: 'Succès', message: `L'utilisateur "${formData.identifiant}" a été modifié.`, type: 'success' });
            } else {
                const createPayload = {
                    identifiant: formData.identifiant,
                    motDePasse: formData.motDePasse,
                    idRole: parseInt(formData.idRole)
                };
                await referentielService.createUser(createPayload);
                toast({ title: 'Succès', message: `L'utilisateur "${formData.identifiant}" a été créé.`, type: 'success' });
            }
            setShowUserModal(false);
            loadData();
        } catch (err) {
            setModalError(err.response?.data || "Une erreur est survenue lors de l'enregistrement.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleDeactivate = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);

        if (!deactivateMotif.trim()) {
            setModalError("Le motif de désactivation est obligatoire.");
            return;
        }

        setSubmitting(true);
        try {
            await referentielService.deactivateUser(selectedUser.idUtilisateur, deactivateMotif);
            toast({ title: 'Succès', message: `Le compte de "${selectedUser.identifiant}" a été désactivé.`, type: 'success' });
            setShowDeactivateModal(false);
            loadData();
        } catch (err) {
            setModalError(err.response?.data || "Une erreur est survenue lors de la désactivation.");
        } finally {
            setSubmitting(false);
        }
    };

    const requestActivate = (user) => {
        setConfirmState({
            isOpen: true,
            title: `Réactiver l'utilisateur "${user.identifiant}" ?`,
            message: `Le collaborateur pourra de nouveau se connecter au portail SIRH avec ses identifiants habituels.`,
            confirmLabel: "Réactiver le compte",
            variant: "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    await referentielService.activateUser(user.idUtilisateur);
                    toast({ title: 'Succès', message: `Le compte de "${user.identifiant}" a été réactivé.`, type: 'success' });
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    loadData();
                } catch (err) {
                    setError(err.response?.data || "Impossible de réactiver l'utilisateur.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                }
            }
        });
    };

    const safeUsersList = Array.isArray(users) ? users : (users?.items || users?.content || []);
    const filteredUsers = safeUsersList.filter(u =>
        (showInactive || u.estActif !== false) &&
        (u.identifiant?.toLowerCase().includes(debouncedSearch.toLowerCase()) ||
        (u.roleLibelle && u.roleLibelle.toLowerCase().includes(debouncedSearch.toLowerCase())))
    );

    if (loading && users.length === 0) {
        return (
            <div className="p-4">
                <SkeletonTable rows={8} columns={5} />
            </div>
        );
    }

    // Rendu
    return (
        <div className="space-y-6">
            {/* Notifications */}
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
                <div className="flex items-center gap-3 flex-wrap">
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer px-2 py-1.5 hover:bg-[var(--color-bg-subtle)] rounded-none whitespace-nowrap">
                        <input
                            type="checkbox"
                            checked={showInactive}
                            onChange={(e) => setShowInactive(e.target.checked)}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        <Eye size={14} />
                        <span>Afficher désactivés</span>
                    </label>
                    <Button
                        onClick={handleOpenCreateModal}
                        variant="primary"
                        size="sm"
                        iconLeft={<UserPlus size={16} />}
                    >
                        Nouvel Utilisateur
                    </Button>
                </div>
            </div>

            {/* Tableau des utilisateurs */}
            <Table>
                <Thead>
                    <Th>Identifiant</Th>
                    <Th>Rôle</Th>
                    <Th>Statut</Th>
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
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-2">
                                        <button
                                            onClick={() => handleOpenEditModal(user)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier l'utilisateur"
                                            aria-label={`Modifier ${user.identifiant}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => user.estActif ? handleOpenDeactivateModal(user) : requestActivate(user)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                user.estActif
                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                            }`}
                                            title={user.estActif ? "Désactiver l'utilisateur" : "Réactiver l'utilisateur"}
                                            aria-label={user.estActif ? `Désactiver ${user.identifiant}` : `Activer ${user.identifiant}`}
                                        >
                                            <Power size={15} />
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        ))
                    ) : (
                        <Tr>
                            <Td colSpan={4} className="p-0">
                                <EmptyState
                                    icon={UserCheck}
                                    title="Aucun utilisateur trouvé"
                                    description="Aucun compte utilisateur ne correspond à votre recherche."
                                    actionLabel="Créer un utilisateur"
                                    onAction={handleOpenCreateModal}
                                />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale d'ajout/modification utilisateur */}
            <Modal
                isOpen={showUserModal}
                onClose={() => setShowUserModal(false)}
                title={selectedUser ? "Modifier l'utilisateur" : "Nouvel utilisateur"}
                icon={<UserPlus size={18} />}
                size="lg"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowUserModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="user-form" variant="primary" size="sm" loading={submitting}>
                            {selectedUser ? "Enregistrer" : "Créer le compte"}
                        </Button>
                    </>
                }
            >
                <form id="user-form" onSubmit={handleSaveUser} className="space-y-4">
                    {modalError && <Alert type="error" message={modalError} />}
                    <Input
                        label="Identifiant / Login"
                        required
                        disabled={!!selectedUser}
                        autoComplete="username"
                        value={formData.identifiant}
                        onChange={(e) => setFormData({ ...formData, identifiant: e.target.value })}
                        placeholder="Ex: prenom.nom"
                    />
                    <div className="space-y-1">
                        <Input
                            label={`Mot de passe ${selectedUser ? "(Laisser vide pour ne pas modifier)" : ""}`}
                            type={showPassword ? "text" : "password"}
                            autoComplete="new-password"
                            required={!selectedUser}
                            value={formData.motDePasse}
                            onChange={(e) => setFormData({ ...formData, motDePasse: e.target.value })}
                            placeholder="Mot de passe confidentiel"
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
                        autoComplete="new-password"
                        required={!selectedUser && !!formData.motDePasse}
                        value={formData.confirmPassword}
                        onChange={(e) => setFormData({ ...formData, confirmPassword: e.target.value })}
                        placeholder="Répétez le mot de passe"
                    />
                    <Select
                        label="Rôle et Habilitations"
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
                </form>
            </Modal>

            {/* Modale de désactivation logique avec motif */}
            <Modal
                isOpen={showDeactivateModal}
                onClose={() => setShowDeactivateModal(false)}
                title="Confirmer la désactivation"
                icon={<ShieldAlert size={18} className="text-[var(--color-danger-500)]" />}
                size="md"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowDeactivateModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="deactivate-form" variant="danger" size="sm" loading={submitting}>
                            Désactiver le compte
                        </Button>
                    </>
                }
            >
                <form id="deactivate-form" onSubmit={handleDeactivate} className="space-y-4">
                    {modalError && <Alert type="error" message={modalError} />}
                    <p className="text-sm text-[var(--color-fg-muted)]">
                        Vous vous apprêtez à désactiver le compte de <strong className="text-[var(--color-fg)]">{selectedUser?.identifiant}</strong>. 
                        L'utilisateur ne pourra plus s'authentifier sur le SIRH.
                    </p>
                    <Textarea
                        label="Motif de la désactivation (Obligatoire pour audit)"
                        required
                        rows={3}
                        value={deactivateMotif}
                        onChange={(e) => setDeactivateMotif(e.target.value)}
                        placeholder="Ex: Fin de mission, Départ de l'entreprise..."
                    />
                </form>
            </Modal>

            {/* Dialogue de confirmation de réactivation */}
            <ConfirmDialog
                isOpen={confirmState.isOpen}
                onClose={() => setConfirmState(prev => ({ ...prev, isOpen: false }))}
                onConfirm={confirmState.onConfirm}
                title={confirmState.title}
                message={confirmState.message}
                confirmLabel={confirmState.confirmLabel}
                variant={confirmState.variant}
                loading={confirmState.loading}
            />
        </div>
    );
}
