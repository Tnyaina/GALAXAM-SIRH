import { useState, useEffect, createElement } from 'react';
import { Search, Plus, Edit2, Trash2 } from 'lucide-react';
import referentielService from '@/api/admin/referentielService';
import { getMenuIcon } from '@/utils/iconRegistry';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input, { Select } from '@/components/ui/forms/Input';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';

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
    const [modalError, setModalError] = useState(null);
    const [submitting, setSubmitting] = useState(false);
    const toast = useToast();

    // États pour les modales
    const [showMenuModal, setShowMenuModal] = useState(false);
    const [selectedMenu, setSelectedMenu] = useState(null);

    // Confirmation dialog pour suppression
    const [confirmDelete, setConfirmDelete] = useState({
        isOpen: false,
        menu: null,
        loading: false
    });

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
        setModalError(null);
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
        setModalError(null);
        setShowMenuModal(true);
    };

    const handleOpenDeleteModal = (menu) => {
        setConfirmDelete({
            isOpen: true,
            menu,
            loading: false
        });
    };

    const handleSaveMenu = async (e) => {
        e.preventDefault();
        if (submitting) return;
        setModalError(null);

        if (!formData.idMenu.trim()) {
            setModalError("L'identifiant du menu (Code) est obligatoire.");
            return;
        }
        if (!formData.libelle.trim()) {
            setModalError("Le libellé du menu est obligatoire.");
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
            setModalError("Un menu de sous-niveau doit obligatoirement avoir un menu père assigné.");
            return;
        }

        setSubmitting(true);
        try {
            if (selectedMenu) {
                await referentielService.updateMenu(selectedMenu.idMenu, payload);
                toast({ title: 'Succès', message: `Le menu "${payload.libelle}" a été modifié.`, type: 'success' });
            } else {
                await referentielService.createMenu(payload);
                toast({ title: 'Succès', message: `Le menu "${payload.libelle}" a été créé.`, type: 'success' });
            }
            setShowMenuModal(false);
            loadData();
        } catch (err) {
            setModalError(err.response?.data || "Une erreur est survenue lors de l'enregistrement.");
        } finally {
            setSubmitting(false);
        }
    };

    const handleDeleteMenu = async () => {
        if (!confirmDelete.menu || confirmDelete.loading) return;
        setConfirmDelete(prev => ({ ...prev, loading: true }));
        try {
            await referentielService.deleteMenu(confirmDelete.menu.idMenu);
            toast({ title: 'Succès', message: `Le menu "${confirmDelete.menu.libelle}" a été supprimé.`, type: 'success' });
            setConfirmDelete({ isOpen: false, menu: null, loading: false });
            loadData();
        } catch (err) {
            setError(err.response?.data || "Erreur lors de la suppression du menu.");
            setConfirmDelete(prev => ({ ...prev, loading: false, isOpen: false }));
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
            {error && <Alert type="error" message={error} />}

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
                                        <div className="flex justify-end gap-1.5">
                                            <button
                                                onClick={() => handleOpenEditModal(menu)}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier le menu"
                                                aria-label={`Modifier ${menu.libelle}`}
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                            <button
                                                onClick={() => handleOpenDeleteModal(menu)}
                                                className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                title="Supprimer le menu"
                                                aria-label={`Supprimer ${menu.libelle}`}
                                            >
                                                <Trash2 size={15} />
                                            </button>
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })
                    ) : (
                        <Tr>
                            <Td colSpan={7} className="p-0">
                                <EmptyState
                                    icon={Plus}
                                    title="Aucun menu configuré"
                                    description="Aucun menu ne correspond à votre recherche."
                                    actionLabel="Créer un menu"
                                    onAction={handleOpenCreateModal}
                                />
                            </Td>
                        </Tr>
                    )}
                </Tbody>
            </Table>

            {/* Modale d'ajout/modification menu */}
            <Modal
                isOpen={showMenuModal}
                onClose={() => setShowMenuModal(false)}
                title={selectedMenu ? "Modifier le Menu" : "Créer un nouveau Menu"}
                icon={<Plus size={18} />}
                size="md"
                footer={
                    <>
                        <Button type="button" onClick={() => setShowMenuModal(false)} variant="ghost" size="sm" disabled={submitting}>
                            Annuler
                        </Button>
                        <Button type="submit" form="menu-form" variant="primary" size="sm" loading={submitting}>
                            {selectedMenu ? "Enregistrer" : "Créer le menu"}
                        </Button>
                    </>
                }
            >
                <form id="menu-form" onSubmit={handleSaveMenu} className="space-y-4 max-h-[70vh] overflow-y-auto pr-1">
                    {modalError && <Alert type="error" message={modalError} />}
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
                    <div className="grid grid-cols-2 gap-3">
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
                        <label className="text-xs font-bold uppercase tracking-wider text-[var(--color-fg-muted)]">
                            Icône Graphique
                        </label>
                        <div className="flex gap-3 items-center">
                            <select
                                value={formData.icone}
                                onChange={(e) => setFormData({ ...formData, icone: e.target.value })}
                                className="w-full bg-[var(--color-surface)] border border-[var(--color-border)] rounded-none text-sm text-[var(--color-fg)] px-3 py-2 outline-none focus:border-[var(--color-brand-500)] cursor-pointer"
                            >
                                {AVAILABLE_ICONS.map((ico) => (
                                    <option key={ico} value={ico}>
                                        {ico}
                                    </option>
                                ))}
                            </select>
                            <div className="w-9 h-9 border border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)] flex items-center justify-center rounded-none shrink-0">
                                {createElement(getMenuIcon(formData.icone), { size: 18, className: "text-[var(--color-brand-500)]" })}
                            </div>
                        </div>
                    </div>
                </form>
            </Modal>

            {/* Dialogue de confirmation de suppression */}
            <ConfirmDialog
                isOpen={confirmDelete.isOpen}
                onClose={() => setConfirmDelete({ isOpen: false, menu: null, loading: false })}
                onConfirm={handleDeleteMenu}
                title={`Supprimer définitivement le menu "${confirmDelete.menu?.libelle}" ?`}
                message={`ATTENTION : Si ce menu possède des sous-menus associés ou s'il est configuré pour des rôles, la suppression s'effectuera EN CASCADE.`}
                confirmLabel="Supprimer définitivement"
                variant="danger"
                loading={confirmDelete.loading}
            />
        </div>
    );
}