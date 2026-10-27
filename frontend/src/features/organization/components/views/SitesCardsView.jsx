import { MapPin, Clock, Edit2, Power, Check } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function SitesCardsView({
    sites = [],
    onOpenEdit,
    onToggleStatus,
    onOpenCreate
}) {
    if (sites.length === 0) {
        return (
            <div className="p-8 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                <EmptyState
                    icon={MapPin}
                    title="Aucun site trouvé"
                    description="Aucun point d'exploitation ne correspond à vos critères."
                    actionLabel="Nouveau Site"
                    onAction={onOpenCreate}
                    bordered={false}
                />
            </div>
        );
    }

    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3.5">
            {sites.map((s) => {
                const isContinu = s.horaireContinu !== false;
                const dMat = s.heureDebutMatin ? s.heureDebutMatin.substring(0, 5) : '08:00';
                const fMat = s.heureFinMatin ? s.heureFinMatin.substring(0, 5) : '12:00';
                const dPm = s.heureDebutApresMidi ? s.heureDebutApresMidi.substring(0, 5) : '13:00';
                const fPm = s.heureFinApresMidi ? s.heureFinApresMidi.substring(0, 5) : '17:00';

                return (
                    <div
                        key={s.idSite}
                        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-3.5 space-y-2.5 transition-colors hover:border-[var(--color-brand-500)]/40 rounded-none"
                    >
                        {/* En-tête du site */}
                        <div className="flex items-start justify-between gap-2">
                            <div className="min-w-0">
                                <div className="flex items-center gap-1.5 flex-wrap">
                                    <MapPin size={15} className="text-[var(--color-brand-500)] shrink-0" />
                                    <span className="font-bold text-[var(--color-fg)] text-sm">
                                        {s.libelle}
                                    </span>
                                    <span className="font-mono text-xs font-semibold text-[var(--color-fg-muted)]">
                                        ({s.codeSite})
                                    </span>
                                </div>
                                <p className="text-xs text-[var(--color-fg-subtle)] mt-0.5 truncate">
                                    {s.filialeRaisonSociale || 'Sans filiale rattachée'}
                                </p>
                            </div>

                            <div className="flex items-center gap-1.5 shrink-0">
                                <Badge variant={s.estActif ? 'success' : 'default'} size="sm" dot>
                                    {s.estActif ? 'Actif' : 'Inactif'}
                                </Badge>
                                <button
                                    onClick={() => onOpenEdit(s)}
                                    className="ui-button-icon-sm inline-flex items-center justify-center text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                    title="Modifier le site"
                                    aria-label={`Modifier ${s.libelle}`}
                                >
                                    <Edit2 size={15} />
                                </button>
                                <button
                                    onClick={() => onToggleStatus(s)}
                                    className={`ui-button-icon-sm inline-flex items-center justify-center rounded-none cursor-pointer transition-colors ${
                                        s.estActif
                                            ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                            : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                    }`}
                                    title={s.estActif ? "Désactiver le site" : "Réactiver le site"}
                                    aria-label={s.estActif ? `Désactiver ${s.libelle}` : `Activer ${s.libelle}`}
                                >
                                    {s.estActif ? <Power size={15} /> : <Check size={15} />}
                                </button>
                            </div>
                        </div>

                        {/* Plages et régimes */}
                        <div className="flex items-center justify-between gap-3 text-xs pt-2 border-t border-[var(--color-border-subtle)]">
                            <div className="flex items-center gap-1.5 font-mono text-xs font-medium text-[var(--color-fg)]">
                                <Clock size={13} className="text-[var(--color-fg-muted)] shrink-0" />
                                <span>{dMat}–{fMat} / {dPm}–{fPm}</span>
                            </div>
                            <div className="flex items-center gap-1.5">
                                {s.plancherArriveeActif && (
                                    <span className="font-mono text-[11px] text-[var(--color-brand-600)] font-semibold" title="Plancher d'arrivée actif">
                                        Plancher: {s.heureEntreeMinimaleValorisee ? s.heureEntreeMinimaleValorisee.substring(0, 5) : '07:30'}
                                    </span>
                                )}
                                <Badge variant={isContinu ? 'brand' : 'warning'} size="xs">
                                    {isContinu ? 'Continu' : 'Discontinu'}
                                </Badge>
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
