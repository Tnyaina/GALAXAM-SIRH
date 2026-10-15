import { User, Search, ChevronRight } from 'lucide-react';
import Card from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';

export default function PeriodeEmployeeEmptyState({
    employeeSearchFilter,
    setEmployeeSearchFilter,
    employeeSearchQuery,
    employeeListLoading,
    employeeListError,
    employesList,
    employeeListPage,
    employeeListTotalPages,
    employeeListTotal,
    loadMoreEmployees,
    onSelectEmploye,
    setEmployeeListReload
}) {
    return (
        <Card padding={false} className="border border-[var(--color-border-subtle)] bg-[var(--color-surface)] shadow-xs overflow-hidden">
            <div className="p-6 border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]/40 flex flex-col md:flex-row md:items-center justify-between gap-4">
                <div>
                    <h2 className="text-base font-bold text-[var(--color-fg)] flex items-center gap-2">
                        <User size={18} className="text-[var(--color-brand-600)]" />
                        <span>Sélectionnez un collaborateur</span>
                    </h2>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">
                        Choisissez un salarié pour ouvrir sa feuille de pointage mensuelle détaillée, ajuster ses heures ou déclarer des exceptions.
                    </p>
                </div>

                {/* Recherche rapide */}
                <div className="relative min-w-[280px]">
                    <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)]" />
                    <input
                        type="text"
                        value={employeeSearchFilter}
                        onChange={(e) => setEmployeeSearchFilter(e.target.value)}
                        placeholder="Rechercher par nom, prénom ou matricule…"
                        aria-label="Rechercher un collaborateur par nom, prénom ou matricule"
                        className="w-full pl-9 pr-3 py-1.5 text-xs bg-[var(--color-surface)] border border-[var(--color-border)] focus:outline-none focus:border-[var(--color-brand-500)] text-[var(--color-fg)]"
                    />
                </div>
            </div>

            {/* Liste des salariés de la période / site */}
            <div className="p-4">
                {employeeListError && (
                    <Alert
                        type="error"
                        title="Liste indisponible"
                        message={employeeListError}
                        actionLabel="Réessayer"
                        onAction={() => setEmployeeListReload?.(v => v + 1)}
                    />
                )}
                {employeeListLoading && employesList.length === 0 ? (
                    <div className="text-center py-12 text-xs text-[var(--color-fg-muted)]" role="status">
                        Chargement des collaborateurs de la période…
                    </div>
                ) : employesList.length === 0 ? (
                    <div className="text-center py-12 text-xs text-[var(--color-fg-muted)]">
                        {employeeListError
                            ? 'La liste reste indisponible. Réessayez après avoir vérifié votre connexion.'
                            : employeeSearchQuery && employeeSearchQuery.length < 2
                            ? 'Saisissez au moins deux caractères pour lancer la recherche.'
                            : employeeSearchQuery
                            ? `Aucun collaborateur ne correspond à « ${employeeSearchQuery} » pour cette période et ce site.`
                            : 'Aucun collaborateur trouvé pour cette période et ce site.'}
                    </div>
                ) : (
                    <>
                        <div className="mb-3 flex items-center justify-between text-[11px] text-[var(--color-fg-muted)]">
                            <span>Affichage de {employesList.length} sur {employeeListTotal} collaborateur{employeeListTotal === 1 ? '' : 's'}</span>
                            {employeeListLoading && <span role="status">Chargement…</span>}
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                            {employesList.map((emp) => {
                                const isEmpAutonome = Boolean(emp.estAutonome || emp.isCadre || emp.categorie === 'CADRE');
                                return (
                                    <div
                                        key={emp.idEmploye}
                                        onClick={() => onSelectEmploye(emp.idEmploye)}
                                        className="p-3 border border-[var(--color-border-subtle)] hover:border-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)]/60 transition-all cursor-pointer bg-[var(--color-surface)] flex items-center justify-between group shadow-2xs"
                                    >
                                        <div className="flex items-center gap-3 min-w-0">
                                            <Avatar
                                                src={emp.photoUrl}
                                                name={`${emp.nom} ${emp.prenom || ''}`}
                                                size="md"
                                                shape="square"
                                            />
                                            <div className="min-w-0">
                                                <div className="font-bold text-xs text-[var(--color-fg)] group-hover:text-[var(--color-brand-600)] transition-colors truncate">
                                                    {emp.nom} {emp.prenom}
                                                </div>
                                                <div className="flex items-center gap-1.5 text-[11px] text-[var(--color-fg-muted)] font-data mt-0.5">
                                                    <span>{emp.matricule}</span>
                                                    <span>·</span>
                                                    <span className="truncate">{emp.siteNom || emp.siteCode || 'Site'}</span>
                                                </div>
                                                <div className="mt-1">
                                                    {isEmpAutonome ? (
                                                        <Badge variant="purple" size="sm" className="text-[10px]">
                                                            Cadre autonome (8h présumé)
                                                        </Badge>
                                                    ) : (
                                                        <Badge variant="info" size="sm" className="text-[10px]">
                                                            Standard 40h
                                                        </Badge>
                                                    )}
                                                </div>
                                            </div>
                                        </div>

                                        <ChevronRight size={16} className="text-[var(--color-fg-muted)] group-hover:text-[var(--color-brand-600)] shrink-0 transition-transform group-hover:translate-x-0.5" />
                                    </div>
                                );
                            })}
                        </div>
                        {employeeListPage < employeeListTotalPages && (
                            <div className="flex justify-center pt-4">
                                <Button
                                    variant="secondary"
                                    size="sm"
                                    onClick={loadMoreEmployees}
                                    loading={employeeListLoading}
                                    disabled={employeeListLoading}
                                >
                                    {employeeListLoading ? 'Chargement…' : `Charger la suite (${Math.max(0, employeeListTotal - employesList.length)} restant${employeeListTotal - employesList.length === 1 ? '' : 's'})`}
                                </Button>
                            </div>
                        )}
                    </>
                )}
            </div>
        </Card>
    );
}
