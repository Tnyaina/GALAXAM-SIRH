import { Building2, Edit2, Power, Check } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function FilialesCardsView({
    filiales = [],
    sites = [],
    onOpenEdit,
    onToggleStatus,
    onOpenCreate
}) {
    if (filiales.length === 0) {
        return (
            <div className="p-8 bg-[var(--color-surface)] border border-[var(--color-border-subtle)]">
                <EmptyState
                    icon={Building2}
                    title="Aucune filiale enregistrée"
                    description="Créez la première entité juridique de votre organisation pour commencer."
                    actionLabel="Nouvelle Filiale"
                    onAction={onOpenCreate}
                    bordered={false}
                />
            </div>
        );
    }

    return (
        <div className="space-y-3">
            {filiales.map((f) => {
                const sitesDeLaFiliale = sites.filter(s => String(s.idFilialeRattachement) === String(f.idFiliale));

                return (
                    <div
                        key={f.idFiliale}
                        className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-3.5 space-y-3 transition-colors hover:border-[var(--color-brand-500)]/40 rounded-none"
                    >
                        {/* Header filiale */}
                        <div className="flex items-start justify-between gap-2">
                            <div className="flex items-center gap-2.5 min-w-0">
                                <div className="w-8 h-8 rounded-none bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] flex items-center justify-center shrink-0">
                                    <Building2 size={16} />
                                </div>
                                <div className="min-w-0">
                                    <div className="flex items-center gap-1.5 flex-wrap">
                                        <h4 className="font-bold text-[var(--color-fg)] text-sm truncate">
                                            {f.raisonSociale}
                                        </h4>
                                        <span className="font-mono text-xs font-semibold text-[var(--color-fg-muted)]">
                                            ({f.codeFiliale})
                                        </span>
                                    </div>
                                    <p className="text-xs text-[var(--color-fg-subtle)] truncate mt-0.5">
                                        {f.adresseSiege || 'Siège non renseigné'}
                                    </p>
                                </div>
                            </div>

                            <div className="flex items-center gap-1.5 shrink-0">
                                <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                    {f.estActif ? 'Actif' : 'Inactif'}
                                </Badge>
                                <button
                                    onClick={() => onOpenEdit(f)}
                                    className="ui-button-icon-sm inline-flex items-center justify-center text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                    title="Modifier la filiale"
                                    aria-label={`Modifier ${f.raisonSociale}`}
                                >
                                    <Edit2 size={15} />
                                </button>
                                <button
                                    onClick={() => onToggleStatus(f)}
                                    className={`ui-button-icon-sm inline-flex items-center justify-center rounded-none cursor-pointer transition-colors ${
                                        f.estActif
                                            ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                            : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                    }`}
                                    title={f.estActif ? "Désactiver la filiale" : "Réactiver la filiale"}
                                    aria-label={f.estActif ? `Désactiver ${f.raisonSociale}` : `Activer ${f.raisonSociale}`}
                                >
                                    {f.estActif ? <Power size={15} /> : <Check size={15} />}
                                </button>
                            </div>
                        </div>

                        {/* Identifiants fiscaux concis & sites rattachés */}
                        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1.5 pt-2 border-t border-[var(--color-border-subtle)] text-xs text-[var(--color-fg-muted)]">
                            <div className="flex flex-wrap items-center gap-x-3 gap-y-1 font-mono text-xs">
                                <span>NIF: <strong className="text-[var(--color-fg)]">{f.nif || '—'}</strong></span>
                                <span className="text-[var(--color-fg-subtle)]">•</span>
                                <span>STAT: <strong className="text-[var(--color-fg)]">{f.stat || '—'}</strong></span>
                                <span className="text-[var(--color-fg-subtle)]">•</span>
                                <span>RCS: <strong className="text-[var(--color-fg)]">{f.rcs || '—'}</strong></span>
                            </div>
                            <div className="text-xs">
                                <span className="text-[var(--color-fg-muted)]">{sitesDeLaFiliale.length} site{sitesDeLaFiliale.length > 1 ? 's' : ''}</span>
                            </div>
                        </div>
                    </div>
                );
            })}
        </div>
    );
}
