import { Building, Plus, Edit2 } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';

export default function DepartementsList({
    departements = [],
    selectedDepartement,
    onSelectDepartement,
    onOpenCreate,
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-4 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                    <Building size={18} className="text-[var(--color-brand-500)]" />
                    Départements ({departements.length})
                </h3>
                <button
                    onClick={onOpenCreate}
                    className="p-1 hover:bg-[var(--color-bg-subtle)] rounded-none transition-colors text-[var(--color-brand-500)] cursor-pointer"
                    title="Créer un département"
                >
                    <Plus size={18} />
                </button>
            </div>

            <div className="space-y-1.5 max-h-[500px] overflow-y-auto pr-1">
                {departements.map((dept) => {
                    const isSelected = selectedDepartement?.idDepartement === dept.idDepartement;
                    return (
                        <div
                            key={dept.idDepartement}
                            onClick={() => onSelectDepartement(dept)}
                            className={`
                                w-full flex items-center justify-between p-3 rounded-none border text-left cursor-pointer transition-all
                                ${isSelected
                                    ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)] text-[var(--color-brand-500)] font-semibold'
                                    : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
                                }
                            `}
                        >
                            <div className="min-w-0 flex-1">
                                <p className="text-sm font-bold truncate">{dept.libelle}</p>
                                <p className="text-xs font-mono text-[var(--color-fg-muted)] truncate">{dept.codeDepartement}</p>
                            </div>
                            <div className="flex items-center gap-2 pl-2 shrink-0">
                                <button
                                    onClick={(e) => onOpenEdit(dept, e)}
                                    className="p-1 text-[var(--color-fg-muted)] hover:text-[var(--color-fg)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer"
                                    title="Modifier"
                                >
                                    <Edit2 size={14} />
                                </button>
                                <button
                                    onClick={(e) => onToggleStatus(dept, e)}
                                    className="cursor-pointer"
                                    title={dept.estActif ? "Désactiver logiquement" : "Activer"}
                                >
                                    <Badge variant={dept.estActif ? 'success' : 'default'} size="sm">
                                        {dept.estActif ? "Actif" : "Inactif"}
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