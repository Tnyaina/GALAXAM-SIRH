import { Plus, Trash2, MapPin } from 'lucide-react';
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

export default function SiteFeriesList({
    siteFeries = [],
    selectedYear,
    onOpenCreate,
    onDelete,
}) {
    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm border border-ink-150 dark:border-ink-700 shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-ink-150 dark:border-ink-800">
                <div>
                    <h3 className="font-bold text-ink-900 dark:text-white text-md flex items-center gap-2">
                        <MapPin size={18} className="text-brand-500" />
                        Jours Fériés Régionaux par Site ({siteFeries.length})
                    </h3>
                    <p className="text-xs text-ink-400">Jours fériés spécifiques aux fêtes patronales ou régionales par site d'exploitation</p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Plus size={16} />}>
                    Affecter Férié à un Site
                </Button>
            </div>

            <div className="overflow-x-auto">
                <table className="w-full text-left text-sm border-collapse">
                    <thead>
                        <tr className="border-b border-ink-150 dark:border-ink-800 text-xs font-bold text-ink-500 uppercase tracking-wider bg-ink-50/50 dark:bg-ink-950/40">
                            <th className="py-3 px-4">Site d'Exploitation</th>
                            <th className="py-3 px-4">Date Férié</th>
                            <th className="py-3 px-4">Fête / Événement</th>
                            <th className="py-3 px-4 text-center">Chômé</th>
                            <th className="py-3 px-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-150 dark:divide-ink-800">
                        {siteFeries.length === 0 ? (
                            <tr>
                                <td colSpan="5" className="py-8 text-center text-ink-400 text-xs">
                                    Aucune association de jour férié spécifique par site pour {selectedYear}.
                                </td>
                            </tr>
                        ) : (
                            siteFeries.map(sf => (
                                <tr key={sf.idSiteFerie} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 transition-colors">
                                    <td className="py-3 px-4 font-bold text-ink-900 dark:text-ink-100 flex items-center gap-2">
                                        <MapPin size={14} className="text-brand-500" />
                                        {sf.libelleSite || `Site #${sf.idSite}`}
                                    </td>
                                    <td className="py-3 px-4 font-mono font-bold text-brand-500">
                                        {formatDateDisplay(sf.dateFerie)}
                                    </td>
                                    <td className="py-3 px-4 font-medium text-ink-800 dark:text-ink-200">
                                        {sf.libelleFerie || '-'}
                                    </td>
                                    <td className="py-3 px-4 text-center">
                                        <span className={`px-2 py-0.5 rounded-sm text-xs font-semibold ${sf.estChome !== false ? 'bg-success-500/10 text-success-500' : 'bg-ink-100 dark:bg-ink-800 text-ink-400'}`}>
                                            {sf.estChome !== false ? "Oui" : "Non"}
                                        </span>
                                    </td>
                                    <td className="py-3 px-4 text-right">
                                        <button
                                            onClick={() => onDelete(sf.idSiteFerie)}
                                            className="p-1 hover:bg-danger-500/10 rounded-sm text-danger-500 cursor-pointer"
                                            title="Supprimer l'association"
                                        >
                                            <Trash2 size={15} />
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
