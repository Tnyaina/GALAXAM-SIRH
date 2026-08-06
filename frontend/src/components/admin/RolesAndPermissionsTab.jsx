import { useState, useEffect } from 'react';
import { Shield, Plus, Edit2, CheckCircle, XCircle, ShieldCheck, ListTodo } from 'lucide-react';
import referentielService from '@/api/referentielService';

export default function RolesAndPermissionsTab() {
    const [roles, setRoles] = useState([]);
    const [modules, setModules] = useState([]);
    const [menusTree, setMenusTree] = useState([]);

    const [selectedRole, setSelectedRole] = useState(null);
    const [loading, setLoading] = useState(true);
    const [loadingDetails, setLoadingDetails] = useState(false);
    const [error, setError] = useState(null);
    const [successMessage, setSuccessMessage] = useState(null);

    // Permissions et Menus du rôle sélectionné
    const [rolePermissions, setRolePermissions] = useState([]);
    const [selectedMenuIds, setSelectedMenuIds] = useState([]);

    // Sous-onglet actif à droite
    const [rightTab, setRightTab] = useState('permissions'); // 'permissions' | 'menus'

    // Formulaire Rôle (modale de création/édition de rôle)
    const [showRoleModal, setShowRoleModal] = useState(false);
    const [roleFormData, setRoleFormData] = useState({
        codeRole: '',
        libelle: ''
    });
    const [editingRole, setEditingRole] = useState(null);
    const [roleModalError, setRoleModalError] = useState(null);

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
            setModules(modulesData.filter(m => m.estActif)); // Uniquement les modules actifs
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
        setSuccessMessage(null);
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

    // Gestion du rôle (Créer / Modifier)
    const handleOpenCreateRole = () => {
        setEditingRole(null);
        setRoleFormData({ codeRole: '', libelle: '' });
        setRoleModalError(null);
        setShowRoleModal(true);
    };

    const handleOpenEditRole = (role, e) => {
        e.stopPropagation(); // Évite de changer de rôle lors du clic sur modifier
        setEditingRole(role);
        setRoleFormData({ codeRole: role.codeRole, libelle: role.libelle });
        setRoleModalError(null);
        setShowRoleModal(true);
    };

    const handleSaveRole = async (e) => {
        e.preventDefault();
        setRoleModalError(null);

        if (!roleFormData.codeRole.trim()) {
            setRoleModalError("Le code du rôle est obligatoire.");
            return;
        }
        if (!roleFormData.libelle.trim()) {
            setRoleModalError("Le libellé du rôle est obligatoire.");
            return;
        }

        try {
            if (editingRole) {
                const updated = await referentielService.updateRole(editingRole.idRole, {
                    codeRole: roleFormData.codeRole,
                    libelle: roleFormData.libelle
                });
                setSuccessMessage(`Rôle "${roleFormData.libelle}" modifié.`);
                if (selectedRole?.idRole === editingRole.idRole) {
                    setSelectedRole(updated);
                }
            } else {
                const created = await referentielService.createRole({
                    codeRole: roleFormData.codeRole,
                    libelle: roleFormData.libelle
                });
                setSuccessMessage(`Rôle "${roleFormData.libelle}" créé.`);
                setSelectedRole(created);
            }
            setShowRoleModal(false);
            // Recharger les rôles
            const rolesData = await referentielService.getRoles();
            setRoles(rolesData);
        } catch (err) {
            setRoleModalError(err.response?.data || "Erreur de sauvegarde.");
        }
    };

    const handleToggleRoleStatus = async (role, e) => {
        e.stopPropagation();
        setSuccessMessage(null);
        try {
            if (role.estActif) {
                await referentielService.deactivateRole(role.idRole);
                setSuccessMessage(`Rôle "${role.libelle}" désactivé.`);
            } else {
                await referentielService.activateRole(role.idRole);
                setSuccessMessage(`Rôle "${role.libelle}" réactivé.`);
            }
            const rolesData = await referentielService.getRoles();
            setRoles(rolesData);
            const currentSelected = rolesData.find(r => r.idRole === selectedRole?.idRole);
            if (currentSelected) setSelectedRole(currentSelected);
        } catch {
            setError("Erreur lors de la modification du statut du rôle.");
        }
    };

    // Gestion de la matrice des permissions
    const getPermissionForModule = (idModule) => {
        return rolePermissions.find(p => p.idModule === idModule) || {
            idModule,
            droitLecture: false,
            droitEcriture: false,
            droitValidation: false
        };
    };

    const handleCheckboxChange = (idModule, field) => {
        setRolePermissions(prev => {
            const index = prev.findIndex(p => p.idModule === idModule);
            if (index > -1) {
                const updated = [...prev];
                updated[index] = { ...updated[index], [field]: !updated[index][field] };
                return updated;
            } else {
                return [...prev, {
                    idModule,
                    droitLecture: field === 'droitLecture',
                    droitEcriture: field === 'droitEcriture',
                    droitValidation: field === 'droitValidation'
                }];
            }
        });
    };

    const handleSelectAllPermissions = () => {
        const allChecked = modules.every(m => {
            const perm = getPermissionForModule(m.idModule);
            return perm.droitLecture && perm.droitEcriture && perm.droitValidation;
        });

        if (allChecked) {
            // Tout décocher
            setRolePermissions(modules.map(m => ({
                idModule: m.idModule,
                droitLecture: false,
                droitEcriture: false,
                droitValidation: false
            })));
        } else {
            // Tout cocher
            setRolePermissions(modules.map(m => ({
                idModule: m.idModule,
                droitLecture: true,
                droitEcriture: true,
                droitValidation: true
            })));
        }
    };

    const handleSavePermissions = async () => {
        if (!selectedRole) return;
        setSuccessMessage(null);
        setError(null);
        try {
            const cleanPermissions = modules.map(m => {
                const perm = getPermissionForModule(m.idModule);
                return {
                    idModule: m.idModule,
                    droitLecture: perm.droitLecture,
                    droitEcriture: perm.droitEcriture,
                    droitValidation: perm.droitValidation
                };
            });

            await referentielService.updateRolePermissions(selectedRole.idRole, cleanPermissions);
            setSuccessMessage("Permissions mises à jour avec succès.");
            loadRoleConfiguration(selectedRole.idRole);
        } catch {
            setError("Erreur lors de l'enregistrement des permissions.");
        }
    };

    // Aplati récursif de l'arbre des menus
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

    // Gestion de la sélection robuste et récursive parent/fille
    const handleToggleMenu = (idMenu) => {
        setSelectedMenuIds(prev => {
            const isChecked = prev.includes(idMenu);
            const flat = flattenMenus(menusTree);

            if (isChecked) {
                // Décocher : enlever le menu lui-même et TOUS ses descendants
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
                // Cocher : ajouter le menu lui-même et TOUS ses ancêtres
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
        setSuccessMessage(null);
        setError(null);
        try {
            await referentielService.updateRoleMenus(selectedRole.idRole, selectedMenuIds);
            setSuccessMessage("Accès aux menus enregistré avec succès.");
            loadRoleConfiguration(selectedRole.idRole);
        } catch {
            setError("Erreur lors de la mise à jour de l'accès aux menus.");
        }
    };

    const renderMenuItem = (menuItem) => {
        const isChecked = selectedMenuIds.includes(menuItem.idMenu);
        return (
            <div key={menuItem.idMenu} className="space-y-1">
                <label className="flex items-center gap-3 cursor-pointer py-1.5 px-2 hover:bg-ink-50 dark:hover:bg-ink-950/40 rounded-sm transition-colors">
                    <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => handleToggleMenu(menuItem.idMenu)}
                        className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700 focus:ring-brand-500 focus:ring-offset-0 bg-white dark:bg-ink-950"
                    />
                    <span className="text-sm font-medium text-ink-900 dark:text-ink-100">
                        {menuItem.libelle}
                    </span>
                </label>
                {menuItem.sousMenus && menuItem.sousMenus.length > 0 && (
                    <div className="pl-6 ml-2 border-l border-ink-150 dark:border-ink-800 space-y-1">
                        {menuItem.sousMenus.map(child => renderMenuItem(child))}
                    </div>
                )}
            </div>
        );
    };

    if (loading) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {successMessage && (
                <div className="bg-success-500/10 text-success-600 dark:text-success-500 border border-success-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {successMessage}
                </div>
            )}
            {error && (
                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 items-start">
                
                {/* Colonne Gauche : Liste des Rôles */}
                <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-4 space-y-4">
                    <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                        <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                            <Shield size={18} className="text-brand-500" />
                            Rôles
                        </h3>
                        <button
                            onClick={handleOpenCreateRole}
                            className="p-1 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors text-brand-500 cursor-pointer"
                            title="Créer un rôle"
                        >
                            <Plus size={18} />
                        </button>
                    </div>

                    <div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1">
                        {roles.map((role) => {
                            const isSelected = selectedRole?.idRole === role.idRole;
                            return (
                                <div
                                    key={role.idRole}
                                    onClick={() => setSelectedRole(role)}
                                    className={`
                                        w-full flex items-center justify-between p-3 rounded-sm border text-left cursor-pointer transition-all
                                        ${isSelected
                                            ? 'bg-brand-500/10 border-brand-500 text-brand-600 dark:text-brand-500 font-semibold'
                                            : 'bg-white dark:bg-ink-900 border-ink-150 dark:border-ink-800 text-ink-900 dark:text-ink-100 hover:bg-ink-50 dark:hover:bg-ink-950/30'
                                        }
                                    `}
                                >
                                    <div className="min-w-0 flex-1">
                                        <p className="text-sm truncate">{role.libelle}</p>
                                        <p className="text-xs text-ink-400 font-normal truncate">{role.codeRole}</p>
                                    </div>
                                    <div className="flex items-center gap-1.5 pl-2 shrink-0">
                                        <button
                                            onClick={(e) => handleOpenEditRole(role, e)}
                                            className="p-1 hover:bg-ink-200 dark:hover:bg-ink-800 rounded-sm text-ink-400 hover:text-ink-900 dark:hover:text-white cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={13} />
                                        </button>
                                        <button
                                            onClick={(e) => handleToggleRoleStatus(role, e)}
                                            className={`px-1.5 py-0.5 rounded-sm text-[10px] font-bold uppercase transition-colors cursor-pointer ${
                                                role.estActif
                                                    ? 'bg-success-500/10 text-success-500 hover:bg-danger-500/10 hover:text-danger-500'
                                                    : 'bg-ink-200 dark:bg-ink-800 text-ink-400 hover:bg-success-500/10 hover:text-success-500'
                                            }`}
                                            title={role.estActif ? "Désactiver logiquement" : "Activer"}
                                        >
                                            {role.estActif ? "Actif" : "Inactif"}
                                        </button>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                </div>

                {/* Colonne Droite : Configuration Habilitations (Matrice & Menus) */}
                <div className="lg:col-span-2 bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                    {selectedRole ? (
                        <div>
                            {/* Onglets internes côté droit */}
                            <div className="flex border-b border-ink-150 dark:border-ink-700 bg-ink-50/50 dark:bg-ink-950/20 px-4">
                                <button
                                    onClick={() => setRightTab('permissions')}
                                    className={`
                                        flex items-center gap-2 py-4 px-3 border-b-2 text-sm font-semibold transition-all cursor-pointer
                                        ${rightTab === 'permissions'
                                            ? 'border-brand-500 text-brand-500'
                                            : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                        }
                                    `}
                                >
                                    <ShieldCheck size={16} />
                                    Matrice des Permissions
                                </button>
                                <button
                                    onClick={() => setRightTab('menus')}
                                    className={`
                                        flex items-center gap-2 py-4 px-3 border-b-2 text-sm font-semibold transition-all cursor-pointer
                                        ${rightTab === 'menus'
                                            ? 'border-brand-500 text-brand-500'
                                            : 'border-transparent text-ink-400 hover:text-ink-900 dark:hover:text-white'
                                        }
                                    `}
                                >
                                    <ListTodo size={16} />
                                    Accès aux Menus
                                </button>
                            </div>

                            {/* Contenu de la configuration */}
                            <div className="p-6">
                                {loadingDetails ? (
                                    <div className="flex justify-center items-center py-12">
                                        <div className="w-6 h-6 border-2 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
                                    </div>
                                ) : (
                                    <div className="space-y-6">
                                        
                                        {/* 1. Sous-onglet Permissions */}
                                        {rightTab === 'permissions' && (
                                            <div className="space-y-4">
                                                <div className="flex flex-col sm:flex-row gap-4 justify-between sm:items-center">
                                                    <div>
                                                        <h4 className="font-bold text-ink-900 dark:text-white">Droits d'accès par Module</h4>
                                                        <p className="text-xs text-ink-400">Configurez les droits CRUD pour chaque module pour le rôle <strong className="text-brand-500">{selectedRole.libelle}</strong>.</p>
                                                    </div>
                                                    <div className="flex gap-2 shrink-0">
                                                        <button
                                                            onClick={handleSelectAllPermissions}
                                                            type="button"
                                                            className="px-3.5 py-2 text-xs font-semibold border border-ink-300 dark:border-ink-700 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors cursor-pointer"
                                                        >
                                                            Tout Sélectionner
                                                        </button>
                                                        <button
                                                            onClick={handleSavePermissions}
                                                            className="px-3.5 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors shadow-sm cursor-pointer"
                                                        >
                                                            Enregistrer
                                                        </button>
                                                    </div>
                                                </div>

                                                <div className="border border-ink-150 dark:border-ink-800 rounded-sm overflow-hidden">
                                                    <table className="w-full border-collapse text-left text-sm">
                                                        <thead>
                                                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-800 text-ink-400 font-semibold uppercase text-xs">
                                                                <th className="px-4 py-3">Module</th>
                                                                <th className="px-4 py-3 text-center">Lecture</th>
                                                                <th className="px-4 py-3 text-center">Écriture</th>
                                                                <th className="px-4 py-3 text-center">Validation</th>
                                                            </tr>
                                                        </thead>
                                                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                                                            {modules.map((m) => {
                                                                const perm = getPermissionForModule(m.idModule);
                                                                return (
                                                                    <tr key={m.idModule} className="hover:bg-ink-50/30 dark:hover:bg-ink-950/10">
                                                                        <td className="px-4 py-3 font-semibold text-ink-900 dark:text-ink-100">
                                                                            {m.libelle}
                                                                            <span className="block text-[10px] text-ink-400 font-normal uppercase tracking-wider">{m.codeModule}</span>
                                                                        </td>
                                                                        <td className="px-4 py-3 text-center">
                                                                            <input
                                                                                type="checkbox"
                                                                                checked={perm.droitLecture}
                                                                                onChange={() => handleCheckboxChange(m.idModule, 'droitLecture')}
                                                                                className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700 focus:ring-brand-500 focus:ring-offset-0 bg-white dark:bg-ink-950"
                                                                            />
                                                                        </td>
                                                                        <td className="px-4 py-3 text-center">
                                                                            <input
                                                                                type="checkbox"
                                                                                checked={perm.droitEcriture}
                                                                                onChange={() => handleCheckboxChange(m.idModule, 'droitEcriture')}
                                                                                className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700 focus:ring-brand-500 focus:ring-offset-0 bg-white dark:bg-ink-950"
                                                                            />
                                                                        </td>
                                                                        <td className="px-4 py-3 text-center">
                                                                            <input
                                                                                type="checkbox"
                                                                                checked={perm.droitValidation}
                                                                                onChange={() => handleCheckboxChange(m.idModule, 'droitValidation')}
                                                                                className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700 focus:ring-brand-500 focus:ring-offset-0 bg-white dark:bg-ink-950"
                                                                            />
                                                                        </td>
                                                                    </tr>
                                                                );
                                                            })}
                                                        </tbody>
                                                    </table>
                                                </div>
                                            </div>
                                        )}

                                        {/* 2. Sous-onglet Menus */}
                                        {rightTab === 'menus' && (
                                            <div className="space-y-4">
                                                <div className="flex justify-between items-center">
                                                    <div>
                                                        <h4 className="font-bold text-ink-900 dark:text-white">Menus et Navigation Visibles</h4>
                                                        <p className="text-xs text-ink-400">Sélectionnez les pages qui apparaîtront dans la Sidebar pour le rôle <strong className="text-brand-500">{selectedRole.libelle}</strong>.</p>
                                                    </div>
                                                    <button
                                                        onClick={handleSaveMenus}
                                                        className="px-4 py-2 text-xs font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors shadow-sm cursor-pointer"
                                                    >
                                                        Enregistrer les Menus
                                                    </button>
                                                </div>

                                                <div className="border border-ink-150 dark:border-ink-800 rounded-sm p-4 bg-ink-50/20 dark:bg-ink-950/10 space-y-2">
                                                    {menusTree.length > 0 ? (
                                                        menusTree.map(rootMenu => renderMenuItem(rootMenu))
                                                    ) : (
                                                        <p className="text-sm text-ink-400 text-center py-6">Aucun menu disponible en base de données.</p>
                                                    )}
                                                </div>
                                            </div>
                                        )}
                                    </div>
                                )}
                            </div>
                        </div>
                    ) : (
                        <div className="flex flex-col items-center justify-center py-24 text-ink-400">
                            <Shield size={36} className="mb-2 opacity-50" />
                            <p className="text-sm">Veuillez sélectionner ou créer un rôle à gauche.</p>
                        </div>
                    )}
                </div>
            </div>

            {/* Modale Rôle (Créer/Modifier) */}
            {showRoleModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {editingRole ? "Modifier le Rôle" : "Créer un nouveau Rôle"}
                        </div>
                        <form onSubmit={handleSaveRole}>
                            <div className="p-6 space-y-4">
                                {roleModalError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <XCircle size={14} />
                                        {roleModalError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code du Rôle</label>
                                    <input
                                        type="text"
                                        value={roleFormData.codeRole}
                                        onChange={(e) => setRoleFormData({ ...roleFormData, codeRole: e.target.value })}
                                        disabled={!!editingRole}
                                        placeholder="Ex: ROLE_MANAGER"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé du Rôle</label>
                                    <input
                                        type="text"
                                        value={roleFormData.libelle}
                                        onChange={(e) => setRoleFormData({ ...roleFormData, libelle: e.target.value })}
                                        placeholder="Ex: Manager d'équipe"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowRoleModal(false)}
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
        </div>
    );
}
