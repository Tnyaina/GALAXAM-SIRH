import { ListTodo } from 'lucide-react';
import Button from '@/components/ui/Button';

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

    return (
        <div className="space-y-4">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h4 className="font-bold text-ink-900 dark:text-white text-base">
                        Menus Visibles pour <span className="text-brand-500">{selectedRole.libelle}</span>
                    </h4>
                    <p className="text-xs text-ink-400">
                        Sélectionnez les rubriques de navigation affichées dans la barre latérale pour ce rôle.
                    </p>
                </div>
                <Button onClick={handleSaveMenus} variant="primary" size="sm" iconLeft={<ListTodo size={16} />}>
                    Enregistrer l'accès aux menus
                </Button>
            </div>

            <div className="bg-ink-50/50 dark:bg-ink-950/30 p-4 border border-ink-150 dark:border-ink-800 rounded-sm max-h-[500px] overflow-y-auto space-y-2">
                {menusTree.map(menuItem => renderMenuItem(menuItem))}
            </div>
        </div>
    );
}
