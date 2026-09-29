import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Edit2, Eye, Plus, Shield, ShieldCheck, ListTodo } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import referentielService from '@/features/administration/api/referentielService';
import Tabs from '@/components/ui/navigation/Tabs';
import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import Modal from '@/components/ui/overlays/Modal';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { useToast } from '@/components/ui/feedback/Toast';
import { useMenu } from '@/hooks/useMenu';


function RoleFormModal({ isOpen, onClose, onSubmit, editingRole, formData, setFormData, roleModalError, submitting = false }) {
    return <Modal isOpen={isOpen} onClose={onClose} title={editingRole ? 'Modifier le Rôle' : 'Nouveau Rôle'} icon={<Shield size={18} />} size="md" footer={<><Button type="button" onClick={onClose} variant="ghost" size="sm" disabled={submitting}>Annuler</Button><Button type="submit" form="role-form" variant="primary" size="sm" loading={submitting}>{editingRole ? 'Mettre à jour' : 'Créer le rôle'}</Button></>}><form id="role-form" onSubmit={onSubmit} className="space-y-4">{roleModalError && <FormError message={roleModalError} />}<Input label="Code du rôle (ex: ADMIN_PAIE, SUPERVISEUR)" required disabled={!!editingRole} value={formData.codeRole} onChange={(event) => setFormData({ ...formData, codeRole: event.target.value.toUpperCase() })} placeholder="ADMIN_RH" /><Input label="Libellé complet" required value={formData.libelle} onChange={(event) => setFormData({ ...formData, libelle: event.target.value })} placeholder="Administrateur RH" /></form></Modal>;
}

function RoleMenusTree({ selectedRole, menusTree = [], selectedMenuIds = [], handleToggleMenu, handleSaveMenus }) {
    const renderMenuItem = (menuItem) => <div key={menuItem.idMenu} className="space-y-1"><label className="flex items-center gap-3 cursor-pointer py-1.5 px-2 hover:bg-[var(--color-surface-hover)] rounded-none"><input type="checkbox" checked={selectedMenuIds.includes(menuItem.idMenu)} onChange={() => handleToggleMenu(menuItem.idMenu)} className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]" /><span className="text-sm font-medium text-[var(--color-fg)]">{menuItem.libelle}</span></label>{menuItem.sousMenus?.length > 0 && <div className="pl-6 ml-2 border-l border-[var(--color-border-subtle)] space-y-1">{menuItem.sousMenus.map(renderMenuItem)}</div>}</div>;
    return <div className="space-y-4"><div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-[var(--color-border-subtle)]"><div><h4 className="font-bold text-[var(--color-fg)] text-base">Menus Visibles pour <span className="text-[var(--color-brand-500)]">{selectedRole.libelle}</span></h4><p className="text-xs text-[var(--color-fg-muted)]">Sélectionnez les rubriques de navigation affichées dans la barre latérale pour ce rôle.</p></div><Button onClick={handleSaveMenus} variant="primary" size="sm" iconLeft={<ListTodo size={16} />}>Enregistrer l'accès aux menus</Button></div><div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none max-h-[500px] overflow-y-auto space-y-2">{menusTree.map(renderMenuItem)}</div></div>;
}

function RolesList({ roles = [], selectedRole, showInactive = false, onToggleShowInactive, onSelectRole, onOpenCreateRole, onOpenEditRole, onToggleRoleStatus }) {
    return <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-4 space-y-4"><div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]"><h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2"><Shield size={18} className="text-[var(--color-brand-500)]" />Rôles ({roles.length})</h3><div className="flex items-center gap-3"><label className="flex items-center gap-1.5 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer hover:text-[var(--color-fg)] px-1.5 py-0.5 rounded-none hover:bg-[var(--color-bg-subtle)]"><input type="checkbox" checked={showInactive} onChange={onToggleShowInactive} className="w-3.5 h-3.5 rounded-none accent-[var(--color-brand-500)]" /><Eye size={13} /><span className="hidden sm:inline">Afficher masqués</span></label><button onClick={onOpenCreateRole} className="p-1.5 hover:bg-[var(--color-bg-subtle)] rounded-none text-[var(--color-brand-500)] cursor-pointer" title="Créer un rôle"><Plus size={18} /></button></div></div><div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1">{roles.map((role) => <div key={role.idRole} onClick={() => onSelectRole(role)} className={`w-full flex items-center justify-between p-3 rounded-none border text-left cursor-pointer ${selectedRole?.idRole === role.idRole ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)] text-[var(--color-brand-500)] font-semibold' : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'}`}><div className="min-w-0 flex-1"><p className="text-sm truncate font-bold">{role.libelle}</p><p className="text-xs font-mono text-[var(--color-fg-muted)] truncate">{role.codeRole}</p></div><div className="flex items-center gap-2 pl-2 shrink-0"><button onClick={(event) => { event.stopPropagation(); onOpenEditRole(role, event); }} className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer" title="Modifier"><Edit2 size={14} /></button><button onClick={(event) => { event.stopPropagation(); onToggleRoleStatus(role, event); }} className="cursor-pointer" title={role.estActif ? 'Désactiver logiquement' : 'Activer'}><Badge variant={role.estActif ? 'success' : 'default'} size="sm">{role.estActif ? 'Actif' : 'Inactif'}</Badge></button></div></div>)}</div></div>;
}

function RolePermissionsMatrix({ selectedRole, modules = [], getPermissionForModule, handleTogglePermission, handleToggleAllModulePermissions, handleToggleGlobalAllPermissions, handleSavePermissions }) {
    const allChecked = modules.length > 0 && modules.every((module) => { const permission = getPermissionForModule(module.idModule); return permission.droitLecture && permission.droitEcriture && permission.droitValidation; });
    return <div className="space-y-4"><div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-[var(--color-border-subtle)]"><div><h4 className="font-bold text-[var(--color-fg)] text-base">Matrice des Permissions pour <span className="text-[var(--color-brand-500)]">{selectedRole.libelle}</span></h4><p className="text-xs text-[var(--color-fg-muted)]">Cochez les droits d'accès (Lecture, Écriture, Validation) pour chaque module fonctionnel.</p></div><div className="flex items-center gap-3"><label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer"><input type="checkbox" checked={allChecked} onChange={handleToggleGlobalAllPermissions} className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]" />Tout autoriser</label><Button onClick={handleSavePermissions} variant="primary" size="sm" iconLeft={<ShieldCheck size={16} />}>Enregistrer</Button></div></div><Table><Thead><Th>Module</Th><Th className="text-center">Lecture (Consulter)</Th><Th className="text-center">Écriture (Saisir / Éditer)</Th><Th className="text-center">Validation (Approuver)</Th><Th className="text-center">Tout le module</Th></Thead><Tbody>{modules.map((module) => { const permission = getPermissionForModule(module.idModule); const checked = permission.droitLecture && permission.droitEcriture && permission.droitValidation; return <Tr key={module.idModule}><Td className="font-medium text-[var(--color-fg)]">{module.libelle}<span className="block text-xs font-mono text-[var(--color-fg-muted)] font-normal">{module.codeModule}</span></Td>{['droitLecture', 'droitEcriture', 'droitValidation'].map((field) => <Td key={field} className="text-center"><input type="checkbox" checked={permission[field]} onChange={() => handleTogglePermission(module.idModule, field)} className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]" /></Td>)}<Td className="text-center"><input type="checkbox" checked={checked} onChange={() => handleToggleAllModulePermissions(module.idModule)} className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]" /></Td></Tr>; })}</Tbody></Table></div>;
}

export default function RolesAndPermissionsTab() {
    // Contexte et navigation
    const { refreshMenu } = useMenu();
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

    // État local : rôles, permissions, formulaire et confirmation.
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

    // Effets : chargent les rôles puis le détail du rôle sélectionné.
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
            setRoleModalError(getErrorMessage(err, "Erreur lors de l'enregistrement."));
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
                    refreshMenu?.();
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
            refreshMenu?.();
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

    // Rendu
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
