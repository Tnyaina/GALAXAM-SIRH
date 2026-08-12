import { Building, Plus, Edit2 } from 'lucide-react';

export default function DepartementsList({
    departements = [],
    selectedDepartement,
    onSelectDepartement,
    onOpenCreate,
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-4 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-ink-100 dark:border-ink-800">
                <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                    <Building size={18} className="text-brand-500" />
                    Départements ({departements.length})
                </h3>
                <button
                    onClick={onOpenCreate}
                    className="p-1 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm transition-colors text-brand-500 cursor-pointer"
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
                                w-full flex items-center justify-between p-3 rounded-sm border text-left cursor-pointer transition-all
                                ${isSelected
                                    ? 'bg-brand-500/10 border-brand-500 text-brand-600 dark:text-brand-500 font-semibold'
                                    : 'bg-white dark:bg-ink-900 border-ink-150 dark:border-ink-800 text-ink-900 dark:text-ink-100 hover:bg-ink-50 dark:hover:bg-ink-950/30'
                                }
                            `}
                        >
                            <div className="min-w-0 flex-1">
                                <p className="text-sm truncate">{dept.libelle}</p>
                                <p className="text-xs text-ink-400 font-normal truncate">{dept.codeDepartement}</p>
                            </div>
                            <div className="flex items-center gap-1.5 pl-2 shrink-0">
                                <button
                                    onClick={(e) => onOpenEdit(dept, e)}
                                    className="p-1 hover:bg-ink-200 dark:hover:bg-ink-800 rounded-sm text-ink-400 hover:text-ink-900 dark:hover:text-white cursor-pointer"
                                    title="Modifier"
                                >
                                    <Edit2 size={13} />
                                </button>
                                <button
                                    onClick={(e) => onToggleStatus(dept, e)}
                                    className={`px-1.5 py-0.5 rounded-sm text-[10px] font-bold uppercase transition-colors cursor-pointer ${
                                        dept.estActif
                                            ? 'bg-success-500/10 text-success-500 hover:bg-danger-500/10 hover:text-danger-500'
                                            : 'bg-ink-200 dark:bg-ink-800 text-ink-400 hover:bg-success-500/10 hover:text-success-500'
                                    }`}
                                >
                                    {dept.estActif ? "Actif" : "Inactif"}
                                </button>
                            </div>
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
