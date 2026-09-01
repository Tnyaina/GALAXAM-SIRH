import { User, Edit2, Power, RotateCcw, Briefcase, MapPin } from 'lucide-react';
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
        <div className="bg-[var(--color-surface)] rounded-none shadow-sm border border-[var(--color-border-subtle)] p-4 sm:p-4.5 flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
            <div className="flex items-center gap-3.5">
                <div className="w-14 h-14 bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] rounded-none overflow-hidden flex items-center justify-center text-[var(--color-fg-subtle)] shrink-0">
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
                    <User size={26} className={emp.photoUrl ? 'hidden' : ''} />
                </div>
                <div className="space-y-1">
                    <div className="flex items-center gap-2.5 flex-wrap">
                        <h1 className="text-lg sm:text-xl font-bold tracking-tight text-[var(--color-fg)]">
                            {emp.nom} {emp.prenom}
                        </h1>
                        {emp.estActif ? (
                            <Badge variant="success" size="sm" dot>Actif</Badge>
                        ) : (
                            <Badge variant="default" size="sm" dot>Inactif</Badge>
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
                    {(emp.nomFonction || emp.nomSite || emp.statutContrat) && (
                        <div className="flex items-center gap-3 text-xs text-[var(--color-fg-muted)] flex-wrap pt-0.5">
                            {emp.nomFonction && (
                                <span className="font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                    <Briefcase size={13} className="text-[var(--color-brand-500)]" />
                                    {emp.nomFonction}
                                </span>
                            )}
                            {emp.nomSite && (
                                <span className="flex items-center gap-1">
                                    <MapPin size={13} className="text-[var(--color-fg-subtle)]" />
                                    {emp.nomSite}{emp.nomDepartement ? ` · ${emp.nomDepartement}` : ''}
                                </span>
                            )}
                            {emp.statutContrat && emp.statutContrat !== 'Aucun contrat' && (
                                <span className="px-2 py-0.5 bg-[var(--color-bg-subtle)] text-[var(--color-fg)] font-medium text-[11px] border border-[var(--color-border-subtle)]">
                                    {emp.statutContrat}
                                </span>
                            )}
                        </div>
                    )}
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
