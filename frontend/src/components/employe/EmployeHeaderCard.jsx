import { User, Edit2, Power, RotateCcw } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { getFullPhotoUrl } from '@/utils/fileUtils';

export default function EmployeHeaderCard({
    emp,
    canWrite,
    onEdit,
    onDesactiver,
    onReactiver,
}) {
    if (!emp) return null;

    return (
        <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-6 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
            <div className="flex items-center gap-5">
                <div className="w-20 h-20 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none overflow-hidden flex items-center justify-center text-[var(--color-fg-subtle)] shrink-0">
                    {emp.photoUrl ? (
                        <img 
                            src={getFullPhotoUrl(emp.photoUrl)} 
                            alt="Photo" 
                            loading="lazy" 
                            decoding="async" 
                            className="w-full h-full object-cover" 
                            onError={(e) => {
                                e.currentTarget.onerror = null;
                                e.currentTarget.style.display = 'none';
                                if (e.currentTarget.nextElementSibling) {
                                    e.currentTarget.nextElementSibling.classList.remove('hidden');
                                }
                            }}
                        />
                    ) : null}
                    <User size={36} className={emp.photoUrl ? 'hidden' : ''} />
                </div>
                <div className="space-y-1.5">
                    <div className="flex items-center gap-3 flex-wrap">
                        <h1 className="text-2xl font-bold tracking-tight text-[var(--color-fg)]">
                            {emp.nom} {emp.prenom}
                        </h1>
                        {emp.estActif ? (
                            <Badge variant="success" size="md" dot>Actif</Badge>
                        ) : (
                            <Badge variant="default" size="md" dot>Inactif</Badge>
                        )}
                        {emp.alerteContratTexte && (
                            <Badge 
                                variant={emp.alerteContratNiveau === 'DANGER' ? 'danger' : emp.alerteContratNiveau === 'WARNING' ? 'warning' : 'brand'} 
                                size="sm" 
                                dot
                            >
                                {emp.alerteContratTexte}
                            </Badge>
                        )}
                    </div>
                    <div className="flex items-center gap-3 text-xs text-[var(--color-fg-muted)] flex-wrap">
                        <span>Matricule : <strong className="font-mono text-[var(--color-brand-500)]">{emp.matricule}</strong></span>
                        <span>•</span>
                        <span>CIN : <strong className="font-mono text-[var(--color-fg)]">{emp.cinNumero || '-'}</strong></span>
                        <span>•</span>
                        <span>Nationalité : <strong className="text-[var(--color-fg)]">{emp.nationalite || '-'}</strong></span>
                    </div>
                </div>
            </div>

            {canWrite && (
                <div className="flex items-center gap-2 self-stretch md:self-auto">
                    <Button
                        onClick={onEdit}
                        variant="outline"
                        size="sm"
                        iconLeft={<Edit2 size={15} />}
                    >
                        Modifier le profil
                    </Button>
                    {emp.estActif ? (
                        <Button
                            onClick={onDesactiver}
                            variant="ghost"
                            size="sm"
                            className="text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10"
                            iconLeft={<Power size={15} />}
                        >
                            Désactiver
                        </Button>
                    ) : (
                        <Button
                            onClick={onReactiver}
                            variant="primary"
                            size="sm"
                            iconLeft={<RotateCcw size={15} />}
                        >
                            Réactiver l'employé
                        </Button>
                    )}
                </div>
            )}
        </div>
    );
}
