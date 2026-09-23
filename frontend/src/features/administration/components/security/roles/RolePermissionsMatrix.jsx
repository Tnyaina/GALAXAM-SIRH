import { ShieldCheck } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

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
            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3 pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h4 className="font-bold text-[var(--color-fg)] text-base">
                        Matrice des Permissions pour <span className="text-[var(--color-brand-500)]">{selectedRole.libelle}</span>
                    </h4>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Cochez les droits d'accès (Lecture, Écriture, Validation) pour chaque module fonctionnel.
                    </p>
                </div>
                <div className="flex items-center gap-3">
                    <label className="flex items-center gap-2 text-xs font-semibold text-[var(--color-fg-muted)] cursor-pointer">
                        <input
                            type="checkbox"
                            checked={isGlobalAllChecked}
                            onChange={handleToggleGlobalAllPermissions}
                            className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                        />
                        Tout autoriser
                    </label>
                    <Button onClick={handleSavePermissions} variant="primary" size="sm" iconLeft={<ShieldCheck size={16} />}>
                        Enregistrer
                    </Button>
                </div>
            </div>

            <Table>
                <Thead>
                    <Th>Module</Th>
                    <Th className="text-center">Lecture (Consulter)</Th>
                    <Th className="text-center">Écriture (Saisir / Éditer)</Th>
                    <Th className="text-center">Validation (Approuver)</Th>
                    <Th className="text-center">Tout le module</Th>
                </Thead>
                <Tbody>
                    {modules.map(mod => {
                        const perm = getPermissionForModule(mod.idModule);
                        const isAllModChecked = perm.droitLecture && perm.droitEcriture && perm.droitValidation;

                        return (
                            <Tr key={mod.idModule}>
                                <Td className="font-medium text-[var(--color-fg)]">
                                    {mod.libelle}
                                    <span className="block text-xs font-mono text-[var(--color-fg-muted)] font-normal">{mod.codeModule}</span>
                                </Td>

                                <Td className="text-center">
                                    <input
                                        type="checkbox"
                                        checked={perm.droitLecture}
                                        onChange={() => handleTogglePermission(mod.idModule, 'droitLecture')}
                                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                                    />
                                </Td>

                                <Td className="text-center">
                                    <input
                                        type="checkbox"
                                        checked={perm.droitEcriture}
                                        onChange={() => handleTogglePermission(mod.idModule, 'droitEcriture')}
                                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                                    />
                                </Td>

                                <Td className="text-center">
                                    <input
                                        type="checkbox"
                                        checked={perm.droitValidation}
                                        onChange={() => handleTogglePermission(mod.idModule, 'droitValidation')}
                                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                                    />
                                </Td>

                                <Td className="text-center">
                                    <input
                                        type="checkbox"
                                        checked={isAllModChecked}
                                        onChange={() => handleToggleAllModulePermissions(mod.idModule)}
                                        className="w-4 h-4 rounded-none accent-[var(--color-brand-500)]"
                                    />
                                </Td>
                            </Tr>
                        );
                    })}
                </Tbody>
            </Table>
        </div>
    );
}