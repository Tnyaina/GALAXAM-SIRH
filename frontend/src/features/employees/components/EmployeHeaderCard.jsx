import { User, Edit2, Power, RotateCcw, Briefcase, Building2, Calendar, Phone, Mail } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import AuthenticatedImage from '@/components/ui/data-display/AuthenticatedImage';
import { fmtDateFr } from '@/utils/dateUtils';

export default function EmployeHeaderCard({
    emp,
    canWrite = true,
    canValidate = false,
    onEdit,
    onDesactiver,
    onReactiver,
}) {
    if (!emp) return null;

    const contratLibelle = emp.typeContrat || (emp.statutContrat && emp.statutContrat !== 'Aucun contrat' && emp.statutContrat !== 'BROUILLON' ? emp.statutContrat : null);
    const dateEntreeStr = emp.dateEmbauche ? fmtDateFr(emp.dateEmbauche) : null;

    return (
        <div className="bg-[var(--color-surface)] rounded-none shadow-xs border border-[var(--color-border-subtle)] border-t-2 border-t-[var(--color-brand-500)] p-5 sm:p-6 transition-all">
            <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-5">
                
                {/* Bloc Gauche : Photo + Identité + Attributs RH */}
                <div className="flex items-start gap-4 sm:gap-5 min-w-0">
                    {/* Photo de profil avec indicateur de statut */}
                    <div className="relative shrink-0">
                        <div className="w-18 h-18 sm:w-22 sm:h-22 bg-[var(--color-bg-subtle)] border border-[var(--color-border)] rounded-none overflow-hidden flex items-center justify-center text-[var(--color-fg-subtle)] shadow-2xs">
                            <AuthenticatedImage
                                src={emp.photoUrl}
                                alt={`Photo de ${emp.prenom || ''} ${emp.nom || ''}`.trim()}
                                loading="lazy"
                                decoding="async"
                                className="w-full h-full object-cover"
                                fallback={<User size={32} className="text-[var(--color-fg-subtle)]" />}
                            />
                        </div>
                        <span 
                            className={`absolute -bottom-1 -right-1 w-4 h-4 border-2 border-[var(--color-surface)] flex items-center justify-center ${
                                emp.estActif ? 'bg-[var(--color-success-500)]' : 'bg-[var(--color-danger-500)]'
                            }`}
                            title={emp.estActif ? 'Employé actif' : 'Employé inactif'}
                        />
                    </div>

                    {/* Informations principales */}
                    <div className="space-y-1.5 min-w-0 flex-1">
                        {/* Ligne 1 : Nom + Badges de statut & contrat */}
                        <div className="flex items-center gap-2.5 flex-wrap">
                            <h1 className="text-xl sm:text-2xl font-bold tracking-tight text-[var(--color-fg)] truncate">
                                {emp.nom} {emp.prenom}
                            </h1>

                            <span className="font-mono font-bold text-xs bg-[var(--color-bg-subtle)] text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] px-2 py-0.5 border border-[var(--color-border-subtle)]">
                                #{emp.matricule}
                            </span>

                            {emp.estActif ? (
                                <Badge variant="success" size="sm" dot>Actif</Badge>
                            ) : (
                                <Badge variant="danger" size="sm" dot>Inactif</Badge>
                            )}

                            {contratLibelle && (
                                <Badge variant="brand" size="sm">
                                    {contratLibelle}
                                </Badge>
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

                        {/* Ligne 2 : Poste & Affectation */}
                        <div className="flex items-center gap-3 text-xs sm:text-sm text-[var(--color-fg-muted)] flex-wrap">
                            <span className="font-semibold text-[var(--color-fg)] flex items-center gap-1.5">
                                <Briefcase size={14} className="text-[var(--color-brand-500)] shrink-0" />
                                {emp.nomFonction || 'Poste non renseigné'}
                            </span>

                            {(emp.nomSite || emp.nomDepartement) && (
                                <>
                                    <span className="text-[var(--color-fg-subtle)] hidden sm:inline">•</span>
                                    <span className="flex items-center gap-1 text-[var(--color-fg-muted)]">
                                        <Building2 size={14} className="text-[var(--color-fg-subtle)] shrink-0" />
                                        {emp.nomSite || ''}
                                        {emp.nomDepartement ? ` · ${emp.nomDepartement}` : ''}
                                    </span>
                                </>
                            )}
                        </div>

                        {/* Ligne 3 : Métadonnées administratives & Coordonnées rapides */}
                        <div className="flex items-center gap-x-4 gap-y-1.5 text-xs text-[var(--color-fg-muted)] flex-wrap pt-0.5">
                            {dateEntreeStr && (
                                <span className="flex items-center gap-1.5" title="Date d'embauche effective">
                                    <Calendar size={13} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span>Entrée le <strong className="text-[var(--color-fg)] font-medium">{dateEntreeStr}</strong></span>
                                    {emp.ancienneteEffectiveTexte && (
                                        <span className="text-[var(--color-brand-700)] dark:text-[var(--color-brand-300)] font-medium">
                                            ({emp.ancienneteEffectiveTexte})
                                        </span>
                                    )}
                                </span>
                            )}

                            {emp.cinNumero && (
                                <span className="flex items-center gap-1">
                                    <span className="text-[var(--color-fg-subtle)]">CIN :</span>
                                    <strong className="font-mono text-[var(--color-fg)] font-medium">{emp.cinNumero}</strong>
                                </span>
                            )}

                            {emp.telephone && (
                                <a 
                                    href={`tel:${emp.telephone}`} 
                                    className="flex items-center gap-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-600)] transition-colors"
                                    title="Appeler"
                                >
                                    <Phone size={12} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span className="font-mono">{emp.telephone}</span>
                                </a>
                            )}

                            {emp.email && (
                                <a 
                                    href={`mailto:${emp.email}`} 
                                    className="flex items-center gap-1 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-600)] transition-colors"
                                    title="Envoyer un email"
                                >
                                    <Mail size={12} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span>{emp.email}</span>
                                </a>
                            )}
                        </div>
                    </div>
                </div>

                {/* Bloc Droit : Indicateurs clés + Actions */}
                <div className="flex items-center lg:items-end flex-row lg:flex-col justify-between sm:justify-end gap-3 pt-3 lg:pt-0 border-t lg:border-t-0 border-[var(--color-border-subtle)] shrink-0">
                    {/* Barre de boutons d'action */}
                    <div className="flex items-center gap-2">
                        {canWrite && (
                            <Button
                                onClick={onEdit}
                                variant="outline"
                                size="sm"
                                iconLeft={<Edit2 size={14} />}
                                className="shadow-2xs"
                            >
                                Modifier
                            </Button>
                        )}

                        {canValidate && (
                            emp.estActif ? (
                                <Button
                                    onClick={onDesactiver}
                                    variant="ghost"
                                    size="sm"
                                    className="text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10"
                                    iconLeft={<Power size={14} />}
                                >
                                    Désactiver
                                </Button>
                            ) : (
                                <Button
                                    onClick={onReactiver}
                                    variant="primary"
                                    size="sm"
                                    iconLeft={<RotateCcw size={14} />}
                                >
                                    Réactiver
                                </Button>
                            )
                        )}
                    </div>
                </div>

            </div>
        </div>
    );
}
