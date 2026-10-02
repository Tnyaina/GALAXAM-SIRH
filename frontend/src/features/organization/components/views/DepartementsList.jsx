import { useState } from 'react';
import { Building, Plus, Edit2, Eye, Search, Power } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Input from '@/components/ui/forms/Input';
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
    // État local
    const [search, setSearch] = useState('');

    // Données dérivées : compatibilité avec les deux signatures de props
    const handleCreate = onOpenCreateDept || onOpenCreate;
    const handleEdit = onOpenEditDept || onOpenEdit;
    const handleToggle = onToggleDeptStatus || onToggleStatus;

    const filtered = departements.filter(d =>
        d.codeDepartement?.toLowerCase().includes(search.toLowerCase()) ||
        d.libelle?.toLowerCase().includes(search.toLowerCase())
    );

    // Rendu
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
                    {departements.length > 0 && (
                        <Button
                            onClick={handleCreate}
                            variant="secondary"
                            size="xs"
                            iconLeft={<Plus size={14} />}
                            title="Créer un département"
                            aria-label="Créer un nouveau département"
                        >
                            Nouveau
                        </Button>
                    )}
                </div>
            </div>

            {/* Barre de recherche instantanée */}
            <Input
                placeholder="Filtrer par nom, code..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                icon={<Search size={14} />}
            />

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
                                        className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
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
                                        className={`min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 rounded-none cursor-pointer transition-colors ${
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
