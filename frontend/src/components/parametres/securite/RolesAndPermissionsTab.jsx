import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ShieldCheck, ListTodo } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';

import RolesList from './roles/RolesList';
import RoleFormModal from './roles/RoleFormModal';
import RolePermissionsMatrix from './roles/RolePermissionsMatrix';
import RoleMenusTree from './roles/RoleMenusTree';

export default function RolesAndPermissionsTab() {
    const [searchParams, setSearchParams] = useSearchParams();
    const rightTab = searchParams.get('subtab') || 'permissions'; // 'permissions' | 'menus'

    const setRightTab = (newSubTab) => {
        setSearchParams(prev => {
            const next = new URLSearchParams(prev);
            next.set('tab', 'roles');
            next.set('subtab', newSubTab);
            return next;
        });
    };

    const [roles, setRoles] = useState([]);
    const [showInactive, setShowInactive] = useState(false);
    const [modules, setModules] = useState([]);
    const [menusTree, setMenusTree] = useState([]);

    const [selectedRole, setSelectedRole] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadingDetails, setLoadingDetails] = useState(false);
    const [error, setError] = useState(null);
    const [submittingRole, setSubmittingRole] = useState(false);
    const toast = useToast();

    const [rolePermissions, setRolePermissions] = useState([]);
    const [selectedMenuIds, setSelectedMenuIds] = useState([]);

    const [showRoleModal, setShowRoleModal] = useState(false);
    const [roleFormData, setRoleFormData] = useState({ codeRole: '', libelle: '' });
    const [editingRole, setEditingRole] = useState(null);
    const [roleModalError, setRoleModalError] = useState(null);

    // Confirmation dialog state
    const [confirmState, setConfirmState] = useState({
        isOpen: false,
        title: '',
        message: '',
        confirmLabel: '',
        variant: 'danger',
        onConfirm: null,
        loading: false
    });

    const loadMasterData = async () => {
        setLoading(true);
        setError(null);
        try {
            const [rolesData, modulesData, menusTreeData] = await Promise.all([
                referentielService.getRoles(),
                referentielService.getModules(),
                referentielService.getMenusTree()
            ]);
            setRoles(rolesData);
            setModules(modulesData.filter(m => m.estActif));
            setMenusTree(menusTreeData);

            if (rolesData.length > 0) {
                setSelectedRole(rolesData[0]);
            }
        } catch (err) {
            setError("Impossible de charger les données de configuration.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    const loadRoleConfiguration = async (idRole) => {
        setLoadingDetails(true);
        try {
            const [perms, assignedMenus] = await Promise.all([
                referentielService.getRolePermissions(idRole),
                referentielService.getRoleMenus(idRole)
            ]);
            setRolePermissions(perms);
            setSelectedMenuIds(assignedMenus.map(m => m.idMenu));
        } catch (err) {
            setError("Erreur lors de la lecture des configurations du rôle.");
            console.error(err);
        } finally {
            setLoadingDetails(false);
        }
    };

    useEffect(() => {
        loadMasterData();
    }, []);

    useEffect(() => {
        if (selectedRole) {
            loadRoleConfiguration(selectedRole.idRole);
        }
    }, [selectedRole]);

    // Handlers Rôle
    const handleOpenCreateRole = () => {
        setEditingRole(null);
        setRoleFormData({ codeRole: '', libelle: '' });
        setRoleModalError(null);
        setShowRoleModal(true);
    };

    const handleOpenEditRole = (role, e) => {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
        setEditingRole(role);
        setRoleFormData({ codeRole: role.codeRole, libelle: role.libelle });
        setRoleModalError(null);
        setShowRoleModal(true);
    };

    const handleSaveRole = async (e) => {
        e.preventDefault();
        setRoleModalError(null);
        setSubmittingRole(true);
        try {
            if (editingRole) {
                await referentielService.updateRole(editingRole.idRole, { libelle: roleFormData.libelle });
                toast({ title: 'Succès', message: `Rôle "${roleFormData.libelle}" mis à jour.`, type: 'success' });
            } else {
                await referentielService.createRole(roleFormData);
                toast({ title: 'Succès', message: `Rôle "${roleFormData.libelle}" créé.`, type: 'success' });
            }
            setShowRoleModal(false);
            loadMasterData();
        } catch (err) {
            setRoleModalError(err.response?.data || "Erreur lors de l'enregistrement.");
        } finally {
            setSubmittingRole(false);
        }
    };

    const requestToggleRoleStatus = (role, e) => {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();
        const isDeactivating = role.estActif;

        setConfirmState({
            isOpen: true,
            title: isDeactivating ? `Désactiver le rôle "${role.libelle}" ?` : `Réactiver le rôle "${role.libelle}" ?`,
            message: isDeactivating
                ? `Les utilisateurs disposant de ce rôle ne pourront plus effectuer d'actions nécessitant ses privilèges.`
                : `Les utilisateurs associés retrouveront immédiatement leurs accès.`,
            confirmLabel: isDeactivating ? "Désactiver le rôle" : "Réactiver",
            variant: isDeactivating ? "danger" : "primary",
            loading: false,
            onConfirm: async () => {
                setConfirmState(prev => ({ ...prev, loading: true }));
                try {
                    if (isDeactivating) {
                        await referentielService.deactivateRole(role.idRole);
                        toast({ title: 'Succès', message: `Rôle "${role.libelle}" désactivé.`, type: 'success' });
                    } else {
                        await referentielService.activateRole(role.idRole);
                        toast({ title: 'Succès', message: `Rôle "${role.libelle}" réactivé.`, type: 'success' });
                    }
                    setRoles(prev => prev.map(r => r.idRole === role.idRole ? { ...r, estActif: !r.estActif } : r));
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                } catch (err) {
                    setError("Erreur lors du changement de statut du rôle.");
                    setConfirmState(prev => ({ ...prev, isOpen: false, loading: false }));
                    console.error(err);
                }
            }
        });
    };

    // Handlers Permissions
    const getPermissionForModule = (idModule) => {
        const found = rolePermissions.find(p => p.idModule === idModule);
        return found || { droitLecture: false, droitEcriture: false, droitValidation: false };
    };

    const handleTogglePermission = (idModule, field) => {
        setRolePermissions(prev => {
            const existingIndex = prev.findIndex(p => p.idModule === idModule);
            if (existingIndex >= 0) {
                const updated = [...prev];
                const item = { ...updated[existingIndex] };
                item[field] = !item[field];
                updated[existingIndex] = item;
                return updated;
            } else {
                const newItem = { idModule, droitLecture: false, droitEcriture: false, droitValidation: false };
                newItem[field] = true;
                return [...prev, newItem];
            }
        });
    };

    const handleToggleAllModulePermissions = (idModule) => {
        const current = getPermissionForModule(idModule);
        const allChecked = current.droitLecture && current.droitEcriture && current.droitValidation;

        setRolePermissions(prev => {
            const existingIndex = prev.findIndex(p => p.idModule === idModule);
            const newValue = { idModule, droitLecture: !allChecked, droitEcriture: !allChecked, droitValidation: !allChecked };
            if (existingIndex >= 0) {
                const updated = [...prev];
                updated[existingIndex] = newValue;
                return updated;
            } else {
                return [...prev, newValue];
            }
        });
    };

    const handleToggleGlobalAllPermissions = () => {
        const isGlobalAllChecked = modules.length > 0 && modules.every(m => {
            const p = getPermissionForModule(m.idModule);
            return p.droitLecture && p.droitEcriture && p.droitValidation;
        });

        if (isGlobalAllChecked) {
            setRolePermissions(modules.map(m => ({ idModule: m.idModule, droitLecture: false, droitEcriture: false, droitValidation: false })));
        } else {
            setRolePermissions(modules.map(m => ({ idModule: m.idModule, droitLecture: true, droitEcriture: true, droitValidation: true })));
        }
    };

    const handleSavePermissions = async () => {
        if (!selectedRole) return;
        setError(null);
        try {
            const cleanPermissions = modules.map(m => {
                const perm = getPermissionForModule(m.idModule);
                return { idModule: m.idModule, droitLecture: perm.droitLecture, droitEcriture: perm.droitEcriture, droitValidation: perm.droitValidation };
            });
            await referentielService.updateRolePermissions(selectedRole.idRole, cleanPermissions);
            toast({ title: 'Succès', message: "Matrice des permissions enregistrée.", type: 'success' });
            loadRoleConfiguration(selectedRole.idRole);
        } catch {
            setError("Erreur lors de l'enregistrement des permissions.");
        }
    };

    // Handlers Menus
    const flattenMenus = (nodes) => {
        let flat = [];
        nodes.forEach(node => {
            flat.push(node);
            if (node.sousMenus && node.sousMenus.length > 0) {
                flat = flat.concat(flattenMenus(node.sousMenus));
            }
        });
        return flat;
    };

    const handleToggleMenu = (idMenu) => {
        setSelectedMenuIds(prev => {
            const isChecked = prev.includes(idMenu);
            const flat = flattenMenus(menusTree);

            if (isChecked) {
                const toRemove = [idMenu];
                const getDescendants = (id) => {
                    flat.forEach(m => {
                        if (m.idPere === id) {
                            toRemove.push(m.idMenu);
                            getDescendants(m.idMenu);
                        }
                    });
                };
                getDescendants(idMenu);
                return prev.filter(id => !toRemove.includes(id));
            } else {
                const toAdd = [idMenu];
                const getAncestors = (id) => {
                    const item = flat.find(m => m.idMenu === id);
                    if (item && item.idPere) {
                        toAdd.push(item.idPere);
                        getAncestors(item.idPere);
                    }
                };
                getAncestors(idMenu);

                const newSelection = [...prev];
                toAdd.forEach(id => {
                    if (!newSelection.includes(id)) {
                        newSelection.push(id);
                    }
                });
                return newSelection;
            }
        });
    };

    const handleSaveMenus = async () => {
        if (!selectedRole) return;
        setError(null);
        try {
            await referentielService.updateRoleMenus(selectedRole.idRole, selectedMenuIds);
            toast({ title: 'Succès', message: "Accès aux menus enregistré.", type: 'success' });
            loadRoleConfiguration(selectedRole.idRole);
        } catch {
            setError("Erreur lors de la mise à jour de l'accès aux menus.");
        }
    };

    if (loading) {
        return (
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start p-4">
                <SkeletonCard className="lg:col-span-1 h-[400px]" />
                <div className="lg:col-span-2">
                    <SkeletonTable rows={8} columns={4} />
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                {/* Colonne Gauche : Liste des Rôles */}
                <RolesList
                    roles={roles.filter(r => showInactive || r.estActif)}
                    selectedRole={selectedRole}
                    showInactive={showInactive}
                    onToggleShowInactive={() => setShowInactive(!showInactive)}
                    onSelectRole={setSelectedRole}
                    onOpenCreateRole={handleOpenCreateRole}
                    onOpenEditRole={handleOpenEditRole}
                    onToggleRoleStatus={requestToggleRoleStatus}
                />

                {/* Colonne Droite : Configuration Habilitations */}
                <div className="lg:col-span-2 bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm overflow-hidden">
                    {selectedRole ? (
                        <div>
                            <Tabs
                                tabs={[
                                    { id: 'permissions', label: 'Matrice des Permissions', icon: <ShieldCheck size={16} /> },
                                    { id: 'menus', label: 'Accès aux Menus Navigation', icon: <ListTodo size={16} /> }
                                ]}
                                activeTab={rightTab}
                                onChange={setRightTab}
                                variant="underline"
                            />

                            {/* Contenu */}
                            <div className="p-6">
                                {loadingDetails ? (
                                    <div className="p-4">
                                        <SkeletonTable rows={5} columns={4} />
                                    </div>
                                ) : rightTab === 'permissions' ? (
                                    <RolePermissionsMatrix
                                        selectedRole={selectedRole}
                                        modules={modules}
                                        getPermissionForModule={getPermissionForModule}
                                        handleTogglePermission={handleTogglePermission}
                                        handleToggleAllModulePermissions={handleToggleAllModulePermissions}
                                        handleToggleGlobalAllPermissions={handleToggleGlobalAllPermissions}
                                        handleSavePermissions={handleSavePermissions}
                                    />
                                ) : (
                                    <RoleMenusTree
                                        selectedRole={selectedRole}
                                        menusTree={menusTree}
                                        selectedMenuIds={selectedMenuIds}
                                        handleToggleMenu={handleToggleMenu}
                                        handleSaveMenus={handleSaveMenus}
                                    />
                                )}
                            </div>
                        </div>
                    ) : (
                        <div className="p-12 text-center text-[var(--color-fg-muted)] text-sm">
                            Veuillez sélectionner un rôle à gauche pour afficher ses habilitations.
                        </div>
                    )}
                </div>
            </div>

            {/* Modale Rôle */}
            <RoleFormModal
                isOpen={showRoleModal}
                onClose={() => setShowRoleModal(false)}
                onSubmit={handleSaveRole}
                editingRole={editingRole}
                formData={roleFormData}
                setFormData={setRoleFormData}
                roleModalError={roleModalError}
                submitting={submittingRole}
            />

            {/* Dialogue de confirmation de changement de statut */}
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