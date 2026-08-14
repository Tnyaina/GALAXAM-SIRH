import { User, Edit2, Power } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { getFullPhotoUrl } from '@/utils/fileUtils';

export default function EmployeHeaderCard({
    emp,
    canWrite,
    onEdit,
    onDesactiver,
}) {
    if (!emp) return null;

    return (
        <div className="bg-white dark:bg-ink-900 rounded-sm shadow-sm border border-ink-150 dark:border-ink-700 p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="flex items-center gap-5">
                <div className="w-20 h-20 bg-ink-50 dark:bg-ink-800 border-2 border-ink-200 dark:border-ink-700 rounded-sm overflow-hidden flex items-center justify-center text-ink-300 flex-shrink-0">
                    {emp.photoUrl ? (
                        <img src={getFullPhotoUrl(emp.photoUrl)} alt="Photo" loading="lazy" decoding="async" className="w-full h-full object-cover" />
                    ) : (
                        <User size={36} />
                    )}
                </div>
                <div className="space-y-1">
                    <div className="flex items-center gap-3">
                        <h1 className="text-2xl font-bold tracking-tight text-ink-900 dark:text-white">
                            {emp.nom} {emp.prenom}
                        </h1>
                        {emp.estActif ? (
                            <Badge variant="success" size="md" dot>Actif</Badge>
                        ) : (
                            <Badge variant="danger" size="md" dot>Inactif</Badge>
                        )}
                    </div>
                    <div className="flex items-center gap-4 text-xs text-ink-400">
                        <span>Matricule : <strong className="font-mono text-brand-500">{emp.matricule}</strong></span>
                        <span>•</span>
                        <span>CIN : <strong className="font-mono text-ink-700 dark:text-ink-200">{emp.cinNumero || '-'}</strong></span>
                        <span>•</span>
                        <span>Nationalité : <strong>{emp.nationalite}</strong></span>
                    </div>
                </div>
            </div>

            {canWrite && (
                <div className="flex items-center gap-2 self-stretch md:self-auto">
                    <Button
                        onClick={onEdit}
                        variant="outline"
                        size="sm"
                        iconLeft={<Edit2 size={16} />}
                    >
                        Modifier le profil
                    </Button>
                    {emp.estActif && (
                        <Button
                            onClick={onDesactiver}
                            variant="ghost"
                            size="sm"
                            className="text-danger-600 hover:bg-danger-500/10"
                            iconLeft={<Power size={16} />}
                        >
                            Désactiver
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}
