import { useState, useEffect, createElement } from 'react';
import { Search, Plus, Edit2, Trash2, ShieldAlert, CheckCircle, XCircle } from 'lucide-react';
import referentielService from '@/api/referentielService';
import { getMenuIcon } from '@/utils/iconRegistry';

const AVAILABLE_ICONS = [
    'Circle', 'LayoutDashboard', 'Users', 'Clock', 'UserCheck',
    'Truck', 'Package', 'Boxes', 'Shield', 'Settings',
    'Menu', 'Home', 'Wrench', 'Info', 'WifiOff'
];

export default function MenuManagementTab() {
    const [menus, setMenus] = useState([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [actionError, setActionError] = useState(null);
    const [actionSuccess, setActionSuccess] = useState(null);

    // États pour les modales
    const [showMenuModal, setShowMenuModal] = useState(false);
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [selectedMenu, setSelectedMenu] = useState(null);

    // Formulaire de menu
    const [formData, setFormData] = useState({
        idMenu: '',
        libelle: '',
        icone: 'Circle',
        href: '',
        rang: 1,
        niveau: 1,
        idPere: ''
    });

    const loadData = async () => {
        setLoading(true);
        setError(null);
        try {
            const data = await referentielService.getMenusFlat();
            setMenus(data);
        } catch (err) {
            setError("Erreur lors du chargement des menus. Veuillez réessayer.");
            console.error(err);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        loadData();
    }, []);

    const handleOpenCreateModal = () => {
        setSelectedMenu(null);
        setFormData({
            idMenu: '',
            libelle: '',
            icone: 'Circle',
            href: '',
            rang: 1,
            niveau: 1,
            idPere: ''
        });
        setActionError(null);
        setShowMenuModal(true);
    };

    const handleOpenEditModal = (menu) => {
        setSelectedMenu(menu);
        setFormData({
            idMenu: menu.idMenu,
            libelle: menu.libelle,
            icone: menu.icone || 'Circle',
            href: menu.href || '',
            rang: menu.rang || 1,
            niveau: menu.niveau || 1,
            idPere: menu.idPere || ''
        });
        setActionError(null);
        setShowMenuModal(true);
    };

    const handleOpenDeleteModal = (menu) => {
        setSelectedMenu(menu);
        setActionError(null);
        setShowDeleteModal(true);
    };

    const handleSaveMenu = async (e) => {
        e.preventDefault();
        setActionError(null);
        setActionSuccess(null);

        if (!formData.idMenu.trim()) {
            setActionError("L'identifiant du menu (Code) est obligatoire.");
            return;
        }
        if (!formData.libelle.trim()) {
            setActionError("Le libellé du menu est obligatoire.");
            return;
        }

        const payload = {
            idMenu: formData.idMenu.trim().toUpperCase(),
            libelle: formData.libelle.trim(),
            icone: formData.icone,
            href: formData.href.trim() || null,
            rang: parseInt(formData.rang) || 1,
            niveau: parseInt(formData.niveau) || 1,
            idPere: formData.idPere ? formData.idPere : null
        };

        if (payload.niveau === 1) {
            payload.idPere = null;
        } else if (payload.niveau > 1 && !payload.idPere) {
            setActionError("Un menu de sous-niveau doit obligatoirement avoir un menu père assigné.");
            return;
        }

        try {
            if (selectedMenu) {
                await referentielService.updateMenu(selectedMenu.idMenu, payload);
                setActionSuccess(`Le menu "${payload.libelle}" a été modifié.`);
            } else {
                await referentielService.createMenu(payload);
                setActionSuccess(`Le menu "${payload.libelle}" a été créé.`);
            }
            setShowMenuModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Une erreur est survenue lors de l'enregistrement.");
        }
    };

    const handleDeleteMenu = async () => {
        setActionError(null);
        setActionSuccess(null);
        try {
            await referentielService.deleteMenu(selectedMenu.idMenu);
            setActionSuccess(`Le menu "${selectedMenu.libelle}" et tous ses sous-menus associés ont été supprimés.`);
            setShowDeleteModal(false);
            loadData();
        } catch (err) {
            setActionError(err.response?.data || "Erreur lors de la suppression.");
        }
    };

    // Construire une liste de menus ordonnée hiérarchiquement (Père puis ses Enfants par Rang)
    const getOrderedMenus = () => {
        const parents = menus.filter(m => !m.idPere || m.niveau === 1);
        parents.sort((a, b) => (a.rang || 0) - (b.rang || 0));

        const result = [];
        parents.forEach(parent => {
            result.push(parent);
            const children = menus.filter(m => m.idPere === parent.idMenu);
            children.sort((a, b) => (a.rang || 0) - (b.rang || 0));
            result.push(...children);
        });

        // Si des menus n'ont pas été triés (ex: orphelins ou incohérence), les rajouter à la fin
        const processedIds = result.map(r => r.idMenu);
        const remaining = menus.filter(m => !processedIds.includes(m.idMenu));

        return [...result, ...remaining];
    };

    const potentialParents = menus.filter(m => m.niveau === 1 && m.idMenu !== selectedMenu?.idMenu);
    const orderedMenus = getOrderedMenus();

    const filteredMenus = orderedMenus.filter(m =>
        m.idMenu.toLowerCase().includes(search.toLowerCase()) ||
        m.libelle.toLowerCase().includes(search.toLowerCase())
    );

    if (loading && menus.length === 0) {
        return (
            <div className="flex justify-center items-center py-12">
                <div className="w-8 h-8 border-4 border-brand-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
        );
    }

    return (
        <div className="space-y-6">
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

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-white dark:bg-ink-900 p-4 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-ink-400" size={18} />
                    <input
                        type="text"
                        placeholder="Rechercher par identifiant, libellé..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm text-ink-900 dark:text-white focus:outline-none focus:border-brand-500"
                    />
                </div>
                <button
                    onClick={handleOpenCreateModal}
                    className="inline-flex items-center justify-center gap-2 px-4 py-2.5 text-sm font-semibold bg-brand-500 hover:bg-brand-600 text-white rounded-sm transition-colors cursor-pointer"
                >
                    <Plus size={16} />
                    Nouveau Menu
                </button>
            </div>

            {/* Tableau des menus */}
            <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full border-collapse text-left text-sm">
                        <thead>
                            <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-semibold uppercase text-xs">
                                <th className="px-6 py-4">Code / Identifiant</th>
                                <th className="px-6 py-4">Libellé (Hiérarchie)</th>
                                <th className="px-6 py-4">Icône Visuelle</th>
                                <th className="px-6 py-4">Chemin </th>
                                <th className="px-6 py-4">Position / Ordre</th>
                                <th className="px-6 py-4">Menu Père</th>
                                <th className="px-6 py-4 text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                            {filteredMenus.length > 0 ? (
                                filteredMenus.map((menu) => {
                                    const isChild = menu.niveau > 1 || menu.idPere;
                                    const IconComponent = getMenuIcon(menu.icone);
                                    return (
                                        <tr key={menu.idMenu} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                            <td className="px-6 py-4 font-mono text-xs font-semibold text-brand-500">{menu.idMenu}</td>
                                            <td className={`px-6 py-4 font-medium ${isChild ? 'pl-12' : ''}`}>
                                                {isChild ? (
                                                    <span className="inline-flex items-center gap-2 text-ink-500 dark:text-ink-400">
                                                        <span className="text-ink-300 dark:text-ink-700 font-bold">↳</span>
                                                        {menu.libelle}
                                                    </span>
                                                ) : (
                                                    <span className="font-bold text-ink-900 dark:text-white">
                                                        {menu.libelle}
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className="inline-flex items-center gap-2 font-mono text-xs text-ink-600 dark:text-ink-300 bg-ink-50 dark:bg-ink-950 px-2.5 py-1 border border-ink-100 dark:border-ink-850 rounded-sm">
                                                    <IconComponent size={14} className="text-brand-500 shrink-0" />
                                                    {menu.icone || 'Circle'}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 font-mono text-xs text-ink-500">{menu.href || '-'}</td>
                                            <td className="px-6 py-4">
                                                {isChild ? (
                                                    <span className="text-xs px-2 py-0.5 bg-ink-50 dark:bg-ink-950 text-ink-400 dark:text-ink-500 border border-ink-100 dark:border-ink-850 rounded-sm">
                                                        Sous-menu / Rang {menu.rang}
                                                    </span>
                                                ) : (
                                                    <span className="text-xs font-semibold px-2 py-0.5 bg-brand-500/10 text-brand-500 rounded-sm">
                                                        Racine / Rang {menu.rang}
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4">
                                                {menu.idPere ? (
                                                    <span className="text-xs font-semibold px-2 py-0.5 bg-brand-500/5 text-brand-500 border border-brand-500/10 rounded-sm">
                                                        {menu.idPere}
                                                    </span>
                                                ) : (
                                                    <span className="text-ink-300">-</span>
                                                )}
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="flex justify-end gap-2">
                                                    <button
                                                        onClick={() => handleOpenEditModal(menu)}
                                                        className="p-1.5 text-ink-500 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-all cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={16} />
                                                    </button>
                                                    <button
                                                        onClick={() => handleOpenDeleteModal(menu)}
                                                        className="p-1.5 text-danger-500 hover:bg-danger-500/10 rounded-sm transition-all cursor-pointer"
                                                        title="Supprimer"
                                                    >
                                                        <Trash2 size={16} />
                                                    </button>
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })
                            ) : (
                                <tr>
                                    <td colSpan="7" className="px-6 py-12 text-center text-ink-400">
                                        Aucun menu configuré en base de données.
                                    </td>
                                </tr>
                            )}
                        </tbody>
                    </table>
                </div>
            </div>

            {/* Modale d'ajout/modification menu */}
            {showMenuModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            {selectedMenu ? "Modifier le Menu" : "Créer un nouveau Menu"}
                        </div>
                        <form onSubmit={handleSaveMenu}>
                            <div className="p-6 space-y-4 max-h-[450px] overflow-y-auto">
                                {actionError && (
                                    <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                        <ShieldAlert size={14} />
                                        {actionError}
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Code Identifiant (Unique)</label>
                                    <input
                                        type="text"
                                        value={formData.idMenu}
                                        onChange={(e) => setFormData({ ...formData, idMenu: e.target.value })}
                                        disabled={!!selectedMenu}
                                        placeholder="Ex: EMPLOYES, CONGES_LIST"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500 disabled:opacity-50"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Libellé</label>
                                    <input
                                        type="text"
                                        value={formData.libelle}
                                        onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                        placeholder="Ex: Gestion des congés"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Niveau</label>
                                        <select
                                            value={formData.niveau}
                                            onChange={(e) => setFormData({ ...formData, niveau: parseInt(e.target.value) })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        >
                                            <option value={1}>1 (Racine)</option>
                                            <option value={2}>2 (Sous-menu)</option>
                                        </select>
                                    </div>
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Rang (Ordre)</label>
                                        <input
                                            type="number"
                                            value={formData.rang}
                                            onChange={(e) => setFormData({ ...formData, rang: parseInt(e.target.value) || 1 })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        />
                                    </div>
                                </div>
                                {formData.niveau > 1 && (
                                    <div className="space-y-1">
                                        <label className="text-xs font-semibold text-ink-400 uppercase">Menu Père</label>
                                        <select
                                            value={formData.idPere}
                                            onChange={(e) => setFormData({ ...formData, idPere: e.target.value })}
                                            className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        >
                                            <option value="">Sélectionner un menu parent</option>
                                            {potentialParents.map((parent) => (
                                                <option key={parent.idMenu} value={parent.idMenu}>
                                                    {parent.libelle} ({parent.idMenu})
                                                </option>
                                            ))}
                                        </select>
                                    </div>
                                )}
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Chemin (Route Href)</label>
                                    <input
                                        type="text"
                                        value={formData.href}
                                        onChange={(e) => setFormData({ ...formData, href: e.target.value })}
                                        placeholder="Ex: /conges ou /admin/config"
                                        className="w-full px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                    />
                                </div>
                                <div className="space-y-1">
                                    <label className="text-xs font-semibold text-ink-400 uppercase">Icône Lucide</label>
                                    <div className="flex gap-3">
                                        <select
                                            value={formData.icone}
                                            onChange={(e) => setFormData({ ...formData, icone: e.target.value })}
                                            className="flex-1 px-3 py-2 border border-ink-200 dark:border-ink-700 rounded-sm bg-ink-50 dark:bg-ink-950 text-sm focus:outline-none focus:border-brand-500"
                                        >
                                            {AVAILABLE_ICONS.map((ico) => (
                                                <option key={ico} value={ico}>
                                                    {ico}
                                                </option>
                                            ))}
                                        </select>
                                        <div className="w-10 h-10 border border-ink-200 dark:border-ink-700 bg-ink-50 dark:bg-ink-950 flex items-center justify-center rounded-sm shrink-0">
                                            {createElement(getMenuIcon(formData.icone), { size: 20, className: "text-brand-500" })}
                                        </div>
                                    </div>
                                </div>
                            </div>
                            <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                                <button
                                    type="button"
                                    onClick={() => setShowMenuModal(false)}
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

            {/* Modale de suppression cascade */}
            {showDeleteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-[2px] p-4">
                    <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-ink-150 dark:border-ink-700 font-bold text-lg text-ink-900 dark:text-white">
                            Confirmer la suppression
                        </div>
                        <div className="p-6 space-y-3">
                            {actionError && (
                                <div className="bg-danger-500/10 text-danger-600 dark:text-danger-500 border border-danger-500/20 rounded-sm p-3 text-xs flex items-center gap-1.5">
                                    <ShieldAlert size={14} />
                                    {actionError}
                                </div>
                            )}
                            <p className="text-sm text-ink-500 dark:text-ink-400">
                                Êtes-vous sûr de vouloir supprimer définitivement le menu <strong>{selectedMenu?.libelle}</strong> ?
                            </p>
                            <p className="text-xs text-danger-500 font-semibold bg-danger-500/5 p-3 rounded-sm border border-danger-500/10">
                                ⚠️ ATTENTION : Si ce menu possède des sous-menus associés ou s'il est configuré pour des rôles, la suppression s'effectuera EN CASCADE.
                            </p>
                        </div>
                        <div className="px-6 py-4 bg-ink-50 dark:bg-ink-950 border-t border-ink-150 dark:border-ink-700 flex justify-end gap-3">
                            <button
                                onClick={() => setShowDeleteModal(false)}
                                className="px-4 py-2 text-sm font-semibold hover:bg-ink-200 dark:hover:bg-ink-850 rounded-sm transition-colors cursor-pointer"
                            >
                                Annuler
                            </button>
                            <button
                                onClick={handleDeleteMenu}
                                className="px-4 py-2 text-sm font-semibold bg-danger-500 hover:bg-danger-600 text-white rounded-sm transition-colors cursor-pointer"
                            >
                                Supprimer définitivement
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
