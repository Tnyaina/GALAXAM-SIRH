import { useState } from 'react';
import { Building, Plus, Edit2, Eye, Search, Power } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function DepartementsList({
    departements = [],
    selectedDepartement,
    showInactive = false,
    onToggleShowInactive,
    onSelectDepartement,
    onOpenCreate,
    onOpenCreateDept,
    onOpenEdit,
    onOpenEditDept,
    onToggleStatus,
    onToggleDeptStatus,
}) {
    const [search, setSearch] = useState('');

    const handleCreate = onOpenCreateDept || onOpenCreate;
    const handleEdit = onOpenEditDept || onOpenEdit;
    const handleToggle = onToggleDeptStatus || onToggleStatus;

    const filtered = departements.filter(d =>
        d.codeDepartement?.toLowerCase().includes(search.toLowerCase()) ||
        d.libelle?.toLowerCase().includes(search.toLowerCase())
    );

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-4 space-y-3.5 flex flex-col h-full">
            {/* Header */}
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div className="flex items-center gap-2">
                    <Building size={18} className="text-[var(--color-brand-500)]" />
                    <h3 className="font-bold text-[var(--color-fg)] text-sm sm:text-base">
                        Départements ({departements.length})
                    </h3>
                </div>
                <div className="flex items-center gap-2">
                    {onToggleShowInactive && (
                        <label className="flex items-center gap-1 text-[11px] font-semibold text-[var(--color-fg-muted)] cursor-pointer hover:text-[var(--color-fg)] px-1.5 py-1 rounded-none hover:bg-[var(--color-bg-subtle)]">
                            <input
                                type="checkbox"
                                checked={showInactive}
                                onChange={onToggleShowInactive}
                                className="w-3.5 h-3.5 rounded-none accent-[var(--color-brand-500)]"
                            />
                            <Eye size={12} />
                            <span className="hidden sm:inline">Inactifs</span>
                        </label>
                    )}
                    <button
                        onClick={handleCreate}
                        className="p-1.5 hover:bg-[var(--color-brand-500)] hover:text-white bg-[var(--color-bg-subtle)] text-[var(--color-brand-500)] rounded-none cursor-pointer transition-colors border border-[var(--color-border-subtle)]"
                        title="Créer un département"
                        aria-label="Créer un nouveau département"
                    >
                        <Plus size={15} />
                    </button>
                </div>
            </div>

            {/* Barre de recherche instantanée */}
            <div className="relative">
                <Search className="absolute left-2.5 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" size={14} />
                <input
                    type="text"
                    placeholder="Filtrer par nom, code..."
                    value={search}
                    onChange={(e) => setSearch(e.target.value)}
                    className="w-full pl-8 pr-3 py-1 text-xs border border-[var(--color-border)] rounded-none bg-[var(--color-surface)] text-[var(--color-fg)] focus:outline-none focus:border-[var(--color-brand-500)]"
                />
            </div>

            {/* Liste des cartes départements */}
            <div className="space-y-1.5 overflow-y-auto pr-1 flex-1 max-h-[520px]">
                {filtered.length > 0 ? (
                    filtered.map((dept) => {
                        const isSelected = selectedDepartement?.idDepartement === dept.idDepartement;
                        return (
                            <div
                                key={dept.idDepartement}
                                onClick={() => onSelectDepartement(dept)}
                                className={`
                                    w-full flex items-center justify-between p-2.5 sm:p-3 rounded-none border text-left cursor-pointer transition-all
                                    ${isSelected
                                        ? 'bg-[var(--color-brand-500)]/10 border-[var(--color-brand-500)] text-[var(--color-brand-500)] font-semibold shadow-xs'
                                        : 'bg-[var(--color-surface)] border-[var(--color-border-subtle)] text-[var(--color-fg)] hover:bg-[var(--color-surface-hover)]'
                                    }
                                `}
                            >
                                <div className="min-w-0 flex-1">
                                    <div className="flex items-center gap-2">
                                        <p className="text-xs sm:text-sm font-bold truncate">{dept.libelle}</p>
                                    </div>
                                    <div className="flex items-center gap-2 mt-0.5">
                                        <span className="text-[11px] font-mono text-[var(--color-fg-muted)]">{dept.codeDepartement}</span>
                                        <Badge variant={dept.estActif ? 'success' : 'default'} size="sm" dot>
                                            {dept.estActif ? "Actif" : "Inactif"}
                                        </Badge>
                                    </div>
                                </div>
                                <div className="flex items-center gap-1 pl-2 shrink-0">
                                    <button
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleEdit?.(dept, e);
                                        }}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                        title="Modifier le département"
                                        aria-label={`Modifier ${dept.libelle}`}
                                    >
                                        <Edit2 size={14} />
                                    </button>
                                    <button
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            handleToggle?.(dept, e);
                                        }}
                                        className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                            dept.estActif
                                                ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                        }`}
                                        title={dept.estActif ? "Désactiver le département" : "Réactiver le département"}
                                        aria-label={dept.estActif ? `Désactiver ${dept.libelle}` : `Activer ${dept.libelle}`}
                                    >
                                        <Power size={14} />
                                    </button>
                                </div>
                            </div>
                        );
                    })
                ) : (
                    <div className="py-8">
                        <EmptyState
                            icon={Building}
                            title={search ? "Aucun département correspondant" : "Aucun département"}
                            description={search ? "Essayez avec d'autres termes de recherche." : "Créez votre premier département pour regrouper vos services."}
                            actionLabel={!search ? "Créer un département" : undefined}
                            onAction={!search ? handleCreate : undefined}
                        />
                    </div>
                )}
            </div>
        </div>
    );
}