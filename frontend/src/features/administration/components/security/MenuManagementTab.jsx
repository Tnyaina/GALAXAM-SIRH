import { useState, useEffect } from 'react';
import { Search, Plus, Edit2, Trash2 } from 'lucide-react';
import { getErrorMessage } from '@/api/apiClient';
import referentielService from '@/features/administration/api/referentielService';
import { getMenuIcon } from '@/utils/iconRegistry';
import IconPicker from '@/components/ui/forms/IconPicker';
import Card from '@/components/ui/data-display/Card';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Alert from '@/components/ui/feedback/Alert';
import FormError from '@/components/ui/feedback/FormError';
import Modal from '@/components/ui/overlays/Modal';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { useToast } from '@/components/ui/feedback/Toast';
import { useMenu } from '@/hooks/useMenu';

export default function MenuManagementTab() {
    // Contexte : actualisation du menu global après une modification.
    const { refreshMenu } = useMenu();

    // État local : menus, recherche, formulaire et confirmation.
    const [menus, setMenus] = useState([]);
    const [search, setSearch] = useState('');
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [modalError, setModalError] = useState(null);
    const [fieldErrors, setFieldErrors] = useState({});
    const [submitting, setSubmitting] = useState(false);
    const { toast } = useToast();

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

    // Effet : charge les menus à l'ouverture.
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
        setFieldErrors({});
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
        setFieldErrors({});
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

        const errors = {};
        if (!formData.idMenu.trim()) {
            errors.idMenu = "L'identifiant du menu (Code) est obligatoire.";
        }
        if (!formData.libelle.trim()) {
            errors.libelle = "Le libellé du menu est obligatoire.";
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
            errors.idPere = "Un menu de sous-niveau doit obligatoirement avoir un menu père assigné.";
        }

        if (Object.keys(errors).length > 0) {
            setFieldErrors(errors);
            return;
        }
        setFieldErrors({});

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
            refreshMenu?.();
        } catch (err) {
            setModalError(getErrorMessage(err, "Une erreur est survenue lors de l'enregistrement."));
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
            refreshMenu?.();
        } catch (err) {
            setError(getErrorMessage(err, "Erreur lors de la suppression du menu."));
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

    // Rendu
    return (
        <div className="space-y-4">
            {/* Notifications */}
            {error && <Alert type="error" message={error} />}

            {/* Barre d'outils */}
            <Card className="overflow-hidden p-0" shadow="none">
            <div className="flex flex-col sm:flex-row gap-3 justify-between items-stretch sm:items-center p-3.5 border-b border-[var(--color-border-subtle)]">
                <div className="flex-1 max-w-md"><Input aria-label="Rechercher" placeholder="Rechercher par code ou libellé…" value={search} onChange={event => setSearch(event.target.value)} iconLeft={<Search size={16} />} className="min-h-[44px]" /></div>
                {menus.length > 0 && (
                    <Button
                        onClick={handleOpenCreateModal}
                        variant="primary"
                        size="sm"
                        iconLeft={<Plus size={16} />}
                    >
                        Nouveau Menu
                    </Button>
                )}
            </div>

            {/* Tableau des menus */}
            <Table bordered={false}>
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
                                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier le menu"
                                                aria-label={`Modifier ${menu.libelle}`}
                                            >
                                                <Edit2 size={15} />
                                            </button>
                                            <button
                                                onClick={() => handleOpenDeleteModal(menu)}
                                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
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
            </Card>

            {/* Modale d'ajout/modification menu */}
            <Modal
                isOpen={showMenuModal}
                onClose={() => setShowMenuModal(false)}
                title={selectedMenu ? "Modifier le Menu" : "Créer un nouveau Menu"}
                icon={<Plus size={18} />}
                size="lg"
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
                    {modalError && <FormError message={modalError} />}
                    <Input
                        label="Code Identifiant (Unique)"
                        required
                        disabled={!!selectedMenu}
                        value={formData.idMenu}
                        error={fieldErrors.idMenu}
                        onChange={(e) => {
                            setFormData({ ...formData, idMenu: e.target.value.toUpperCase() });
                            if (fieldErrors.idMenu) setFieldErrors(prev => ({ ...prev, idMenu: undefined }));
                        }}
                        placeholder="Ex: EMPLOYES, CONGES_LIST"
                    />
                    <Input
                        label="Libellé"
                        required
                        value={formData.libelle}
                        error={fieldErrors.libelle}
                        onChange={(e) => {
                            setFormData({ ...formData, libelle: e.target.value });
                            if (fieldErrors.libelle) setFieldErrors(prev => ({ ...prev, libelle: undefined }));
                        }}
                        placeholder="Ex: Gestion des congés"
                    />
                    <div className="grid grid-cols-2 gap-3">
                        <SearchSelect
                            label="Niveau"
                            required
                            value={String(formData.niveau)}
                            onChange={(val) => setFormData({ ...formData, niveau: parseInt(val) || 1 })}
                            options={[
                                { value: '1', label: '1 (Racine)' },
                                { value: '2', label: '2 (Sous-menu)' }
                            ]}
                            placeholder="-- Choisir le niveau --"
                        />
                        <Input
                            label="Rang (Ordre)"
                            type="number"
                            required
                            value={formData.rang}
                            onChange={(e) => setFormData({ ...formData, rang: parseInt(e.target.value) || 1 })}
                        />
                    </div>
                    {formData.niveau > 1 && (
                        <SearchSelect
                            label="Menu Père"
                            required
                            value={String(formData.idPere || '')}
                            error={fieldErrors.idPere}
                            onChange={(val) => {
                                setFormData({ ...formData, idPere: val });
                                if (fieldErrors.idPere) setFieldErrors(prev => ({ ...prev, idPere: undefined }));
                            }}
                            options={potentialParents.map((parent) => ({
                                value: String(parent.idMenu),
                                label: `${parent.libelle} (${parent.idMenu})`
                            }))}
                            placeholder="-- Sélectionner un menu parent --"
                        />
                    )}
                    <Input
                        label="Chemin (Route Href)"
                        value={formData.href}
                        onChange={(e) => setFormData({ ...formData, href: e.target.value })}
                        placeholder="Ex: /conges ou /admin/config"
                    />
                    <IconPicker
                        label="Icône Graphique"
                        value={formData.icone}
                        onChange={(ico) => setFormData({ ...formData, icone: ico })}
                    />
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
