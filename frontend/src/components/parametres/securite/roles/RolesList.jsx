import { Shield, Plus, Edit2 } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

export default function RolesList({
    roles = [],
    selectedRole,
    onSelectRole,
    onOpenCreateRole,
    onOpenEditRole,
    onToggleRoleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-4 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                    <Shield size={18} className="text-[var(--color-brand-500)]" />
                    Rôles ({roles.length})
                </h3>
                <button
                    onClick={onOpenCreateRole}
                    className="p-1.5 hover:bg-[var(--color-bg-subtle)] rounded-none  text-[var(--color-brand-500)] cursor-pointer"
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
                            onClick={() => onSelectRole(role)}
                            className={`
                                w-full flex items-center justify-between p-3 rounded-none border text-left cursor-pointer 
                                ${isSelected
                                    ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)] text-[var(--color-brand-500)] font-semibold'
                                    : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
                                }
                            `}
                        >
                            <div className="min-w-0 flex-1">
                                <p className="text-sm truncate font-bold">{role.libelle}</p>
                                <p className="text-xs font-mono text-[var(--color-fg-muted)] truncate">{role.codeRole}</p>
                            </div>
                            <div className="flex items-center gap-2 pl-2 shrink-0">
                                <button
                                    onClick={(e) => onOpenEditRole(role, e)}
                                    className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                    title="Modifier"
                                >
                                    <Edit2 size={14} />
                                </button>
                                <button
                                    onClick={(e) => onToggleRoleStatus(role, e)}
                                    className="cursor-pointer"
                                    title={role.estActif ? "Désactiver logiquement" : "Activer"}
                                >
                                    <Badge variant={role.estActif ? 'success' : 'default'} size="sm">
                                        {role.estActif ? "Actif" : "Inactif"}
                                    </Badge>
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}