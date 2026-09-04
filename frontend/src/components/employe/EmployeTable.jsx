import { Edit2, Power, Mail, Phone, MapPin, Building, UserPlus, SearchX } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { getFullPhotoUrl } from '@/utils/fileUtils';

const formatDateEmbauche = (dateStr) => {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return '-';
    return d.toLocaleDateString('fr-FR');
};

export default function EmployeTable({
    employes = [],
    loading = false,
    canWrite = false,
    canValidate = false,
    hasActiveFilters = false,
    onOpenCreate,
    onOpenDetail,
    onOpenEdit,
    onToggleStatus,
    sortConfig = { key: '', direction: 'asc' },
    onSortChange,
    onResetFilters,
}) {
    if (loading) {
        return (
            <div className="p-4">
                <SkeletonTable rows={10} columns={7} />
            </div>
        );
    }

    if (employes.length === 0) {
        if (hasActiveFilters) {
            return (
                <EmptyState
                    icon={SearchX}
                    title="Aucun collaborateur trouvé"
                    description="Aucun collaborateur ne correspond à vos critères actuels de recherche ou de filtrage."
                    actionLabel={onResetFilters ? "Réinitialiser les filtres" : undefined}
                    onAction={onResetFilters}
                />
            );
        }

        return (
            <EmptyState
                icon={UserPlus}
                title="Aucun employé enregistré"
                description="Votre annuaire du personnel est actuellement vide. Commencez par ajouter votre premier collaborateur."
                actionLabel={canWrite && onOpenCreate ? "Nouvel Employé" : undefined}
                onAction={canWrite && onOpenCreate ? onOpenCreate : undefined}
                actionIcon={<UserPlus size={16} />}
            />
        );
    }

    const handleSort = (key) => {
        if (onSortChange) {
            const isAsc = sortConfig.key === key && sortConfig.direction === 'asc';
            onSortChange(key, isAsc ? 'desc' : 'asc');
        }
    };

    return (
        <Table divided className="border-none shadow-none">
            <Thead>
                <Tr>
                    <Th
                        sortable
                        sorted={sortConfig.key === 'matricule' ? sortConfig.direction : false}
                        onSort={() => handleSort('matricule')}
                        className="min-w-[220px]"
                    >
                        Collaborateur & Matricule
                    </Th>
                    <Th
                        sortable
                        sorted={sortConfig.key === 'fonction' ? sortConfig.direction : false}
                        onSort={() => handleSort('fonction')}
                        className="min-w-[200px]"
                    >
                        Fonction & Affectation
                    </Th>
                    <Th
                        sortable
                        sorted={sortConfig.key === 'site' ? sortConfig.direction : false}
                        onSort={() => handleSort('site')}
                        className="min-w-[130px]"
                    >
                        Site
                    </Th>
                    <Th className="min-w-[170px]">
                        Coordonnées
                    </Th>
                    <Th
                        sortable
                        sorted={sortConfig.key === 'dateEmbauche' ? sortConfig.direction : false}
                        onSort={() => handleSort('dateEmbauche')}
                        className="min-w-[110px]"
                    >
                        Embauche
                    </Th>
                    <Th
                        sortable
                        sorted={sortConfig.key === 'statut' ? sortConfig.direction : false}
                        onSort={() => handleSort('statut')}
                        className="min-w-[90px]"
                    >
                        Statut
                    </Th>
                    <Th className="text-right w-[85px]">
                        Actions
                    </Th>
                </Tr>
            </Thead>
            <Tbody>
                {employes.map((emp) => {
                    const fullName = emp.nomComplet || `${emp.prenom || ''} ${emp.nom || ''}`.trim() || `Employé #${emp.idEmploye}`;
                    const initial = (emp.prenom?.[0] || emp.nom?.[0] || 'E').toUpperCase();
                    const deptName = emp.nomDepartement || emp.libelleDepartement || emp.departement || null;
                    const serviceName = emp.nomService || emp.libelleService || emp.service || null;
                    const fonctionName = emp.nomFonction || emp.libelleFonction || emp.fonction || 'Poste non renseigné';
                    const siteName = emp.nomSite || emp.libelleSite || emp.site || null;
                    const dateRef = emp.dateEmbauche || emp.dateDeclaration || emp.createdAt;
                    const dateEmbaucheText = formatDateEmbauche(dateRef);

                    return (
                        <Tr
                            key={emp.idEmploye}
                            onClick={() => onOpenDetail(emp)}
                            className="group cursor-pointer hover:bg-[var(--color-bg-subtle)]/70 transition-colors"
                        >
                            {/* 1. Collaborateur & Identité */}
                            <Td>
                                <div className="flex items-center gap-3">
                                    <div className="relative shrink-0">
                                        {emp.photoUrl ? (
                                            <img
                                                src={getFullPhotoUrl(emp.photoUrl)}
                                                alt={fullName}
                                                className="w-9 h-9 rounded-none object-cover border border-[var(--color-border-subtle)]"
                                                onError={(e) => {
                                                    e.currentTarget.onerror = null;
                                                    e.currentTarget.style.display = 'none';
                                                    if (e.currentTarget.nextElementSibling) {
                                                        e.currentTarget.nextElementSibling.classList.remove('hidden');
                                                    }
                                                }}
                                            />
                                        ) : null}
                                        <div className={`w-9 h-9 bg-[var(--color-brand-500)] text-white flex items-center justify-center font-bold text-xs shrink-0 rounded-none shadow-xs ${emp.photoUrl ? 'hidden' : ''}`}>
                                            {initial}
                                        </div>
                                        <span
                                            className={`absolute -bottom-0.5 -right-0.5 w-2.5 h-2.5 rounded-full border-2 border-[var(--color-surface)] ${emp.estActif ? 'bg-[var(--color-success-500)]/80' : 'bg-[var(--color-ink-350)]'
                                                }`}
                                        />
                                    </div>
                                    <div className="min-w-0">
                                        <div
                                            className="font-bold text-[var(--color-fg)] group-hover:text-[var(--color-brand-500)] text-xs sm:text-sm leading-snug truncate transition-colors"
                                            title={fullName}
                                        >
                                            {fullName}
                                        </div>
                                        <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)] mt-0.5 flex-wrap">
                                            <span className="font-mono font-semibold text-[var(--color-brand-500)]">{emp.matricule}</span>
                                            {emp.cinNumero && (
                                                <span className="text-[10px] text-[var(--color-fg-subtle)] font-mono">CIN: {emp.cinNumero}</span>
                                            )}
                                        </div>
                                    </div>
                                </div>
                            </Td>

                            {/* 2. Fonction & Structure (Fusion ergonomique sans troncature) */}
                            <Td>
                                <div className="space-y-1">
                                    <div className="font-semibold text-xs text-[var(--color-fg)] leading-snug" title={fonctionName}>
                                        {fonctionName}
                                    </div>
                                    {(deptName || serviceName) ? (
                                        <div className="text-[11px] text-[var(--color-fg-muted)] flex items-center gap-1.5 flex-wrap">
                                            <Building size={11} className="text-[var(--color-brand-500)] shrink-0" />
                                            {deptName && <span className="font-medium">{deptName}</span>}
                                            {deptName && serviceName && <span className="text-[var(--color-fg-subtle)]">•</span>}
                                            {serviceName && <span>{serviceName}</span>}
                                        </div>
                                    ) : (
                                        <span className="text-[11px] text-[var(--color-fg-subtle)] ">Structure non affectée</span>
                                    )}
                                </div>
                            </Td>

                            {/* 3. Site */}
                            <Td>
                                {siteName ? (
                                    <Badge variant="brand" size="sm" className="inline-flex items-center gap-1 font-medium">
                                        <MapPin size={11} className="shrink-0" />
                                        <span>{siteName}</span>
                                    </Badge>
                                ) : (
                                    <span className="text-xs text-[var(--color-fg-subtle)]">-</span>
                                )}
                            </Td>

                            {/* 4. Coordonnées */}
                            <Td>
                                <div className="space-y-1 text-xs">
                                    {emp.telephone ? (
                                        <a
                                            href={`tel:${emp.telephone}`}
                                            onClick={(e) => e.stopPropagation()}
                                            className="flex items-center gap-1.5 text-[var(--color-fg)] hover:text-[var(--color-brand-500)] font-mono text-xs transition-colors"
                                        >
                                            <Phone size={11} className="shrink-0 text-[var(--color-fg-subtle)]" />
                                            <span>{emp.telephone}</span>
                                        </a>
                                    ) : null}
                                    {emp.email ? (
                                        <a
                                            href={`mailto:${emp.email}`}
                                            onClick={(e) => e.stopPropagation()}
                                            className="flex items-center gap-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] text-[11px] truncate transition-colors"
                                            title={emp.email}
                                        >
                                            <Mail size={11} className="shrink-0 text-[var(--color-fg-subtle)]" />
                                            <span className="truncate">{emp.email}</span>
                                        </a>
                                    ) : null}
                                    {!emp.email && !emp.telephone && (
                                        <span className="text-xs text-[var(--color-fg-subtle)]">-</span>
                                    )}
                                </div>
                            </Td>

                            {/* 5. Date d'embauche & Alerte Contrat */}
                            <Td isData className="text-xs">
                                <div className="space-y-1">
                                    <div className="font-mono font-medium text-[var(--color-fg)]">
                                        {dateEmbaucheText}
                                    </div>
                                    {emp.estActif && emp.alerteContratTexte && (
                                        <div>
                                            <Badge
                                                variant={emp.alerteContratNiveau === 'DANGER' ? 'danger' : emp.alerteContratNiveau === 'WARNING' ? 'warning' : 'brand'}
                                                size="xs"
                                                dot
                                            >
                                                {emp.alerteContratTexte}
                                            </Badge>
                                        </div>
                                    )}
                                </div>
                            </Td>

                            {/* 6. Statut */}
                            <Td className="whitespace-nowrap">
                                <div className="space-y-0.5">
                                    {emp.estActif ? (
                                        <Badge variant="success" size="sm" dot>Actif</Badge>
                                    ) : (
                                        <Badge variant="default" size="sm" dot>Inactif</Badge>
                                    )}
                                    {!emp.estActif && emp.motifDesactivation && (
                                        <div className="text-[10px] text-[var(--color-danger-500)] font-medium" title={emp.motifDesactivation}>
                                            {emp.motifDesactivation}
                                        </div>
                                    )}
                                </div>
                            </Td>

                            {/* 7. Actions Uniformisées (Modifier & Toggle Statut) */}
                            <Td className="text-right whitespace-nowrap">
                                <div
                                    className="flex justify-end items-center gap-1.5"
                                    onClick={(e) => e.stopPropagation()}
                                >
                                    {canWrite && (
                                        <button
                                            type="button"
                                            onClick={() => onOpenEdit(emp)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] cursor-pointer rounded-none transition-colors"
                                            title="Modifier la fiche employé"
                                            aria-label="Modifier l'employé"
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                    )}
                                    {canValidate && (
                                        <button
                                            type="button"
                                            onClick={() => onToggleStatus(emp)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${emp.estActif
                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                }`}
                                            title={emp.estActif ? "Désactiver l'employé" : "Réactiver l'employé"}
                                            aria-label={emp.estActif ? "Désactiver l'employé" : "Réactiver l'employé"}
                                        >
                                            <Power size={15} />
                                        </button>
                                    )}
                                </div>
                            </Td>
                        </Tr>
                    );
                })}
            </Tbody>
        </Table>
    );
}
