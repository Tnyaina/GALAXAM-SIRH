import { ListTodo } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

export default function RoleMenusTree({
    selectedRole,
    menusTree = [],
    selectedMenuIds = [],
    handleToggleMenu,
    handleSaveMenus,
}) {
    const renderMenuItem = (menuItem) => {
        const isChecked = selectedMenuIds.includes(menuItem.idMenu);
        return (
            <div key={menuItem.idMenu} className="space-y-1">
                <label className="flex items-center gap-3 cursor-pointer py-1.5 px-2 hover:bg-[var(--color-surface-hover)] rounded-none transition-colors">
                    <input
                        type="checkbox"
                        checked={isChecked}
                        onChange={() => handleToggleMenu(menuItem.idMenu)}
                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                    />
                    <span className="text-sm font-medium text-[var(--color-fg)]">
                        {menuItem.libelle}
                    </span>
                </label>
                {menuItem.sousMenus && menuItem.sousMenus.length > 0 && (
                    <div className="pl-6 ml-2 border-l border-[var(--color-border-subtle)] space-y-1">
                        {menuItem.sousMenus.map(child => renderMenuItem(child))}
                    </div>
                )}
            </div>
        );
    };

    return (
        <div className="space-y-4">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h4 className="font-bold text-[var(--color-fg)] text-base">
                        Menus Visibles pour <span className="text-[var(--color-brand-500)]">{selectedRole.libelle}</span>
                    </h4>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Sélectionnez les rubriques de navigation affichées dans la barre latérale pour ce rôle.
                    </p>
                </div>
                <Button onClick={handleSaveMenus} variant="primary" size="sm" iconLeft={<ListTodo size={16} />}>
                    Enregistrer l'accès aux menus
                </Button>
            </div>

            <div className="bg-[var(--color-bg-subtle)] p-4 border border-[var(--color-border-subtle)] rounded-none max-h-[500px] overflow-y-auto space-y-2">
                {menusTree.map(menuItem => renderMenuItem(menuItem))}
            </div>
        </div>
    );
}