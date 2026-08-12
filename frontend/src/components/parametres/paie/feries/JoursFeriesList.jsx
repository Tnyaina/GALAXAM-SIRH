import { Plus, Edit2, Calendar } from 'lucide-react';
import Button from '@/components/ui/Button';

function formatDateDisplay(dateStr) {
    if (!dateStr) return '-';
    const cleaned = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
    const parts = cleaned.split('-');
    if (parts.length === 3) {
        return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    return dateStr;
}

export default function JoursFeriesList({
    feries = [],
    selectedYear,
    search,
    onOpenCreate,
    onOpenEdit,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                        <Calendar size={18} className="text-brand-500" />
                        Jours Fériés Officiels ({feries.length})
                    </h3>
                    <p className="text-xs text-ink-400">Jours fériés chômés et payés de l'année {selectedYear}</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Ajouter un Jour Férié
                </Button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                    <thead>
                        <tr className="border-b border-ink-150 dark:border-ink-800 text-xs font-bold text-ink-500 uppercase tracking-wider bg-ink-50/50 dark:bg-ink-950/40">
                            <th className="py-3 px-4">Date</th>
                            <th className="py-3 px-4">Libellé de la Fête / Commémoration</th>
                            <th className="py-3 px-4 text-center">Chômé & Payé</th>
                            <th className="py-3 px-4 text-center">Portée</th>
                            <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-150 dark:divide-ink-800">
                        {feries.length === 0 ? (
                            <tr>
                                <td colSpan="5" className="py-8 text-center text-ink-400 text-xs">
                                    Aucun jour férié trouvé pour {selectedYear} {search ? `correspondant à "${search}"` : ''}.
                                </td>
                            </tr>
                        ) : (
                            feries.map(f => (
                                <tr key={f.idFerie} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 transition-colors">
                                    <td className="py-3 px-4 font-mono font-bold text-brand-500">
                                        {formatDateDisplay(f.dateFerie)}
                                    </td>
                                    <td className="py-3 px-4 font-medium text-ink-900 dark:text-ink-100">
                                        {f.libelle}
                                    </td>
                                    <td className="py-3 px-4 text-center">
                                        <span className={`px-2 py-0.5 rounded-sm text-xs font-semibold ${f.estChome ? 'bg-success-500/10 text-success-500' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'}`}>
                                            {f.estChome ? "Oui (Chômé)" : "Non (Travaillé)"}
                                        </span>
                                    </td>
                                    <td className="py-3 px-4 text-center">
                                        <span className={`px-2 py-0.5 rounded-sm text-xs font-semibold ${f.estNational !== false ? 'bg-brand-500/10 text-brand-500' : 'bg-amber-500/10 text-amber-500'}`}>
                                            {f.estNational !== false ? "National (Tous sites)" : "Local / Régional"}
                                        </span>
                                    </td>
                                    <td className="py-3 px-4 text-right">
                                        <button
                                            onClick={() => onOpenEdit(f)}
                                            className="p-1 hover:bg-ink-100 dark:hover:bg-ink-800 rounded-sm text-ink-400 hover:text-ink-900 dark:hover:text-white cursor-pointer"
                                            title="Modifier"
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                    </td>
                                </tr>
                            ))
                        )}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
