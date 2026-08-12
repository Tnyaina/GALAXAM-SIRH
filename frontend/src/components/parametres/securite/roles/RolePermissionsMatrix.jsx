import { ShieldCheck } from 'lucide-react';
import Button from '@/components/ui/Button';

export default function RolePermissionsMatrix({
    selectedRole,
    modules = [],
    getPermissionForModule,
    handleTogglePermission,
    handleToggleAllModulePermissions,
    handleToggleGlobalAllPermissions,
    handleSavePermissions,
}) {
    const isGlobalAllChecked = modules.length > 0 && modules.every(m => {
        const p = getPermissionForModule(m.idModule);
        return p.droitLecture && p.droitEcriture && p.droitValidation;
    });

    return (
        <div className="space-y-4">
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h4 className="font-bold text-ink-900 dark:text-white text-base">
                        Matrice des Permissions pour <span className="text-brand-500">{selectedRole.libelle}</span>
                    </h4>
                    <p className="text-xs text-ink-400">
                        Cochez les droits d'accès (Lecture, Écriture, Validation) pour chaque module fonctionnel.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <label className="flex items-center gap-2 text-xs font-semibold text-ink-600 dark:text-ink-300 cursor-pointer">
                        <input
                            type="checkbox"
                            checked={isGlobalAllChecked}
                            onChange={handleToggleGlobalAllPermissions}
                            className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700"
                        />
                        Tout autoriser
                    </label>
                    <Button onClick={handleSavePermissions} variant="primary" size="sm" iconLeft={<ShieldCheck size={16} />}>
                        Enregistrer
                    </Button>
                </div>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                    <thead>
                        <tr className="border-b border-ink-150 dark:border-ink-800 text-xs font-bold text-ink-500 uppercase tracking-wider bg-ink-50/50 dark:bg-ink-950/40">
                            <th className="py-3 px-4">Module</th>
                            <th className="py-3 px-4 text-center">Lecture (Consulter)</th>
                            <th className="py-3 px-4 text-center">Écriture (Saisir / Éditer)</th>
                            <th className="py-3 px-4 text-center">Validation (Approuver)</th>
                            <th className="py-3 px-4 text-center">Tout le module</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-150 dark:divide-ink-800">
                        {modules.map(mod => {
                            const perm = getPermissionForModule(mod.idModule);
                            const isAllModChecked = perm.droitLecture && perm.droitEcriture && perm.droitValidation;

                            return (
                                <tr key={mod.idModule} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 transition-colors">
                                    <td className="py-3 px-4 font-medium text-ink-900 dark:text-ink-100">
                                        {mod.libelle}
                                        <span className="block text-xs font-mono text-ink-400 font-normal">{mod.codeModule}</span>
                                    </td>

                                    <td className="py-3 px-4 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.droitLecture}
                                            onChange={() => handleTogglePermission(mod.idModule, 'droitLecture')}
                                            className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700"
                                        />
                                    </td>

                                    <td className="py-3 px-4 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.droitEcriture}
                                            onChange={() => handleTogglePermission(mod.idModule, 'droitEcriture')}
                                            className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700"
                                        />
                                    </td>

                                    <td className="py-3 px-4 text-center">
                                        <input
                                            type="checkbox"
                                            checked={perm.droitValidation}
                                            onChange={() => handleTogglePermission(mod.idModule, 'droitValidation')}
                                            className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700"
                                        />
                                    </td>

                                    <td className="py-3 px-4 text-center">
                                        <input
                                            type="checkbox"
                                            checked={isAllModChecked}
                                            onChange={() => handleToggleAllModulePermissions(mod.idModule)}
                                            className="w-4 h-4 rounded-sm text-brand-500 border-ink-300 dark:border-ink-700"
                                        />
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
