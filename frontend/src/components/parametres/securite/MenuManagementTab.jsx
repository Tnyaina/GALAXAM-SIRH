import { useState, useEffect, createElement } from 'react';
import { Search, Plus, Edit2, Trash2, ShieldAlert, CheckCircle, XCircle, X } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';
import { getMenuIcon } from '@/utils/iconRegistry';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

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
            <div className="p-4">
                <SkeletonTable rows={8} columns={7} />
            </div>
        );
    }

    return (
        <div className="space-y-6">
            {/* Notifications */}
            {actionSuccess && (
                <div className="bg-[var(--color-success-500)]/10 text-[var(--color-success-500)] border border-[var(--color-success-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <CheckCircle size={16} />
                    {actionSuccess}
                </div>
            )}
            {error && (
                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-4 text-sm flex items-center gap-2">
                    <XCircle size={16} />
                    {error}
                </div>
            )}

            {/* Barre d'outils */}
            <div className="flex flex-col sm:flex-row gap-4 justify-between items-stretch sm:items-center bg-[var(--color-surface)] p-4 rounded-none border border-[var(--color-border-subtle)] shadow-sm">
                <div className="relative flex-1 max-w-md">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" size={16} />
                    <input
                        type="text"
                        placeholder="Rechercher par identifiant, libellé..."
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        className="w-full pl-9 pr-4 py-2 border border-[var(--color-border)] rounded-none bg-[var(--color-surface)] text-sm text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                    />
                </div>
                <Button
                    onClick={handleOpenCreateModal}
                    variant="primary"
                    size="sm"
                    iconLeft={<Plus size={16} />}
                >
                    Nouveau Menu
                </Button>
            </div>

            {/* Tableau des menus */}
            <Table>
                <Thead>
                    <Th>Code / Identifiant</Th>
                    <Th>Libellé (Hiérarchie)</Th>
                    <Th>Icône Visuelle</Th>
                    <Th>Chemin</Th>
                    <Th>Position / Ordre</Th>
                    <Th>Menu Père</Th>
                    <Th className="text-right">Actions</Th>
                </Thead>
                <Tbody>
                    {filteredMenus.length > 0 ? (
                        filteredMenus.map((menu) => {
                            const isChild = menu.niveau > 1 || menu.idPere;
                            const IconComponent = getMenuIcon(menu.icone);
                            return (
                                <Tr key={menu.idMenu}>
                                    <Td isData className="font-bold text-[var(--color-brand-500)]">{menu.idMenu}</Td>
                                    <Td className={isChild ? 'pl-8' : ''}>
                                        {isChild ? (
                                            <span className="inline-flex items-center gap-2 text-[var(--color-fg-muted)]">
                                                <span className="text-[var(--color-fg-subtle)] font-bold">↳</span>
                                                {menu.libelle}
                                            </span>
                                        ) : (
                                            <span className="font-bold text-[var(--color-fg)]">
                                                {menu.libelle}
                                            </span>
                                        )}
                                    </Td>
                                    <Td>
                                        <span className="inline-flex items-center gap-2 font-mono text-xs text-[var(--color-fg)] bg-[var(--color-bg-subtle)] px-2.5 py-1 rounded-none">
                                            <IconComponent size={14} className="text-[var(--color-brand-500)] shrink-0" />
                                            {menu.icone || 'Circle'}
                                        </span>
                                    </Td>
                                    <Td isData muted className="text-xs">{menu.href || '-'}</Td>
                                    <Td>
                                        <Badge variant={isChild ? 'default' : 'primary'} size="sm">
                                            {isChild ? `Sous-menu / Rang ${menu.rang}` : `Racine / Rang ${menu.rang}`}
                                        </Badge>
                                    </Td>
                                    <Td>
                                        {menu.idPere ? (
                                            <Badge variant="primary" size="sm">{menu.idPere}</Badge>
                                        ) : (
                                            <span className="text-[var(--color-fg-subtle)]">-</span>
                                        )}
                                    </Td>
                                    <Td className="text-right">
                                        <div className="flex justify-end gap-2">
                                            <button
                                                onClick={() => handleOpenEditModal(menu)}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none  cursor-pointer"
                                                title="Modifier"
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                            <button
                                                onClick={() => handleOpenDeleteModal(menu)}
                                                className="p-1.5 text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none  cursor-pointer"
                                                title="Supprimer"
                                            >
                                                <Trash2 size={16} />
                                            </button>
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })
                    ) : (
                        <Tr>
                            <Td colSpan={7} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                Aucun menu configuré en base de données.
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale d'ajout/modification menu */}
            {showMenuModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Plus size={18} className="text-[var(--color-brand-500)]" />
                                {selectedMenu ? "Modifier le Menu" : "Créer un nouveau Menu"}
                            </h3>
                            <button onClick={() => setShowMenuModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <form onSubmit={handleSaveMenu} className="p-6 space-y-4 max-h-[500px] overflow-y-auto">
                            {actionError && (
                                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                                    <ShieldAlert size={14} />
                                    {actionError}
                                </div>
                            )}
                            <Input
                                label="Code Identifiant (Unique)"
                                required
                                disabled={!!selectedMenu}
                                value={formData.idMenu}
                                onChange={(e) => setFormData({ ...formData, idMenu: e.target.value.toUpperCase() })}
                                placeholder="Ex: EMPLOYES, CONGES_LIST"
                            />
                            <Input
                                label="Libellé"
                                required
                                value={formData.libelle}
                                onChange={(e) => setFormData({ ...formData, libelle: e.target.value })}
                                placeholder="Ex: Gestion des congés"
                            />
                            <div className="grid grid-cols-2 gap-4">
                                <Select
                                    label="Niveau"
                                    required
                                    value={formData.niveau}
                                    onChange={(e) => setFormData({ ...formData, niveau: parseInt(e.target.value) })}
                                >
                                    <option value={1}>1 (Racine)</option>
                                    <option value={2}>2 (Sous-menu)</option>
                                </Select>
                                <Input
                                    label="Rang (Ordre)"
                                    type="number"
                                    required
                                    value={formData.rang}
                                    onChange={(e) => setFormData({ ...formData, rang: parseInt(e.target.value) || 1 })}
                                />
                            </div>
                            {formData.niveau > 1 && (
                                <Select
                                    label="Menu Père"
                                    required
                                    value={formData.idPere}
                                    onChange={(e) => setFormData({ ...formData, idPere: e.target.value })}
                                >
                                    <option value="">-- Sélectionner un menu parent --</option>
                                    {potentialParents.map((parent) => (
                                        <option key={parent.idMenu} value={parent.idMenu}>
                                            {parent.libelle} ({parent.idMenu})
                                        </option>
                                    ))}
                                </Select>
                            )}
                            <Input
                                label="Chemin (Route Href)"
                                value={formData.href}
                                onChange={(e) => setFormData({ ...formData, href: e.target.value })}
                                placeholder="Ex: /conges ou /admin/config"
                            />
                            <div className="space-y-1">
                                <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg)]">
                                    Icône Lucide
                                </label>
                                <div className="flex gap-3 items-center">
                                    <select
                                        value={formData.icone}
                                        onChange={(e) => setFormData({ ...formData, icone: e.target.value })}
                                        className="w-full bg-[var(--color-surface)] border-2 border-[var(--color-border)] rounded-none text-sm text-[var(--color-fg)] px-4 py-2.5 outline-none focus:border-[var(--color-brand-500)] cursor-pointer"
                                    >
                                        {AVAILABLE_ICONS.map((ico) => (
                                            <option key={ico} value={ico}>
                                                {ico}
                                            </option>
                                        ))}
                                    </select>
                                    <div className="w-10 h-10 border-2 border-[var(--color-border)] bg-[var(--color-bg-subtle)] flex items-center justify-center rounded-none shrink-0">
                                        {createElement(getMenuIcon(formData.icone), { size: 20, className: "text-[var(--color-brand-500)]" })}
                                    </div>
                                </div>
                            </div>
                            <div className="flex justify-end gap-3 pt-4 border-t border-[var(--color-border-subtle)]">
                                <Button type="button" onClick={() => setShowMenuModal(false)} variant="ghost" size="sm">
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

            {/* Modale de suppression cascade */}
            {showDeleteModal && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-[2px] p-4">
                    <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-2xl max-w-md w-full overflow-hidden">
                        <div className="px-6 py-4 border-b border-[var(--color-border-subtle)] flex justify-between items-center bg-[var(--color-bg-subtle)]">
                            <h3 className="font-bold text-[var(--color-fg)] flex items-center gap-2">
                                <Trash2 size={18} className="text-[var(--color-danger-500)]" />
                                Confirmer la suppression
                            </h3>
                            <button onClick={() => setShowDeleteModal(false)} className="text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] p-1 rounded-none">
                                <X size={18} />
                            </button>
                        </div>
                        <div className="p-6 space-y-3">
                            {actionError && (
                                <div className="bg-[var(--color-danger-500)]/10 text-[var(--color-danger-500)] border border-[var(--color-danger-500)]/20 rounded-none p-3 text-xs flex items-center gap-1.5">
                                    <ShieldAlert size={14} />
                                    {actionError}
                                </div>
                            )}
                            <p className="text-sm text-[var(--color-fg-muted)]">
                                Êtes-vous sûr de vouloir supprimer définitivement le menu <strong className="text-[var(--color-fg)]">{selectedMenu?.libelle}</strong> ?
                            </p>
                            <p className="text-xs text-[var(--color-danger-500)] font-semibold bg-[var(--color-danger-500)]/10 p-3 rounded-none border border-[var(--color-danger-500)]/20">
                                ⚠️ ATTENTION : Si ce menu possède des sous-menus associés ou s'il est configuré pour des rôles, la suppression s'effectuera EN CASCADE.
                            </p>
                        </div>
                        <div className="px-6 py-4 bg-[var(--color-bg-subtle)] border-t border-[var(--color-border-subtle)] flex justify-end gap-3">
                            <Button onClick={() => setShowDeleteModal(false)} variant="ghost" size="sm">
                                Annuler
                            </Button>
                            <Button onClick={handleDeleteMenu} variant="danger" size="sm">
                                Supprimer définitivement
                            </Button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}