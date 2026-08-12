import { Eye, Edit2, Users } from 'lucide-react';
import Badge from '@/components/ui/Badge';
import Button from '@/components/ui/Button';
import Loading from '@/components/ui/Loading';

export default function EmployeTable({
    employes = [],
    loading = false,
    canWrite = false,
    canValidate = false,
    onOpenDetail,
    onOpenEdit,
    onToggleStatus,
}) {
    if (loading) {
        return (
            <div className="flex justify-center items-center py-12">
                <Loading size="lg" label="Chargement des employés..." />
            </div>
        );
    }

    if (employes.length === 0) {
        return (
            <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm p-12 text-center">
                <Users size={40} className="mx-auto text-ink-300 mb-3" />
                <p className="text-sm font-semibold text-ink-800 dark:text-white">Aucun employé trouvé</p>
                <p className="text-xs text-ink-400 mt-1">Aucun enregistrement ne correspond à vos critères.</p>
            </div>
        );
    }

    return (
        <div className="bg-white dark:bg-ink-900 border border-ink-150 dark:border-ink-700 shadow-sm overflow-hidden">
            <div className="overflow-x-auto">
                <table className="w-full border-collapse text-left text-sm">
                    <thead>
                        <tr className="bg-ink-50 dark:bg-ink-950 border-b border-ink-150 dark:border-ink-700 text-ink-400 font-bold uppercase text-[10px] tracking-widest">
                            <th className="px-6 py-4">Matricule</th>
                            <th className="px-6 py-4">Nom complet</th>
                            <th className="px-6 py-4">CIN</th>
                            <th className="px-6 py-4">Téléphone</th>
                            <th className="px-6 py-4">Situation</th>
                            <th className="px-6 py-4">Statut</th>
                            <th className="px-6 py-4 text-right">Actions</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-ink-100 dark:divide-ink-800">
                        {employes.map((emp) => (
                            <tr key={emp.idEmploye} className="hover:bg-ink-50/50 dark:hover:bg-ink-950/20 text-ink-900 dark:text-ink-100">
                                <td className="px-6 py-4">
                                    <span className="font-mono font-bold text-brand-500">{emp.matricule}</span>
                                </td>
                                <td className="px-6 py-4 font-semibold">{emp.nomComplet}</td>
                                <td className="px-6 py-4 font-mono text-xs">{emp.cinNumero}</td>
                                <td className="px-6 py-4 text-xs">{emp.telephone}</td>
                                <td className="px-6 py-4">
                                    <Badge variant="default" size="sm">{emp.situationFamiliale || '-'}</Badge>
                                </td>
                                <td className="px-6 py-4">
                                    {emp.estActif ? (
                                        <Badge variant="success" size="sm" dot>Actif</Badge>
                                    ) : (
                                        <Badge variant="default" size="sm" dot>Inactif</Badge>
                                    )}
                                </td>
                                <td className="px-6 py-4 text-right">
                                    <div className="flex justify-end items-center gap-2">
                                        <button
                                            onClick={() => onOpenDetail(emp)}
                                            className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors cursor-pointer"
                                            title="Voir la fiche"
                                        >
                                            <Eye size={16} />
                                        </button>
                                        {canWrite && (
                                            <button
                                                onClick={() => onOpenEdit(emp)}
                                                className="p-1.5 text-ink-400 hover:text-brand-500 hover:bg-ink-100 dark:hover:bg-ink-800 transition-colors cursor-pointer"
                                                title="Modifier"
                                            >
                                                <Edit2 size={16} />
                                            </button>
                                        )}
                                        {canValidate && (
                                            emp.estActif ? (
                                                <Button
                                                    size="xs"
                                                    variant="danger"
                                                    onClick={() => onToggleStatus(emp)}
                                                >
                                                    Désactiver
                                                </Button>
                                            ) : (
                                                <Button
                                                    size="xs"
                                                    variant="success"
                                                    onClick={() => onToggleStatus(emp)}
                                                >
                                                    Réactiver
                                                </Button>
                                            )
                                        )}
                                    </div>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );
}
