import { Link } from 'react-router-dom';
import { 
    Clock, 
    Briefcase, 
    AlertTriangle, 
    Calendar,
    Eye,
    TrendingUp,
    FileText
} from 'lucide-react';
import Card, { CardBody } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Pagination from '@/components/ui/navigation/Pagination';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';

export default function SyntheseMensuelleTab({
    syntheseList,
    paginatedList,
    stats,
    loading,
    canViewEmploye,
    sortBy,
    sortOrder,
    toggleSort,
    currentPage,
    setCurrentPage,
    pageSize,
    setPageSize,
    onOpenTroncons
}) {
    return (
        <div className="space-y-6">
            {/* Cartes KPI Mensuels */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                <StatCard
                    title="Effectif Saisi"
                    value={stats.totalSalaries}
                    subtitle="Collaborateurs"
                    icon={<Briefcase size={20} />}
                    color="brand"
                />
                <StatCard
                    title="Volume Heures"
                    value={`${stats.totalHeures.toFixed(0)}h`}
                    subtitle="Cumul mensuel"
                    icon={<Clock size={20} />}
                    color="info"
                />
                <StatCard
                    title="HS 30% / 50%"
                    value={`+${(stats.totalHs30 + stats.totalHs50).toFixed(0)}h`}
                    subtitle={`30%: ${stats.totalHs30.toFixed(0)}h | 50%: ${stats.totalHs50.toFixed(0)}h`}
                    icon={<TrendingUp size={20} />}
                    color="success"
                />
                <StatCard
                    title="Absences"
                    value={`${stats.totalAbsences}j`}
                    subtitle="Jours non travaillés"
                    icon={<Calendar size={20} />}
                    color="warning"
                />
                <StatCard
                    title="Déplacement"
                    value={`${stats.totalDeplacement.toLocaleString('fr-FR')} Ar`}
                    subtitle="Forfaits proratisés"
                    icon={<Briefcase size={20} />}
                    color="brand"
                />
                <StatCard
                    title="Repas"
                    value={`${stats.totalRepas.toLocaleString('fr-FR')} Ar`}
                    subtitle="Indemnités + Soir"
                    icon={<AlertTriangle size={20} />}
                    color={stats.totalAnomalies > 0 ? 'danger' : 'success'}
                />
            </div>

            {/* Tableau Mensuel */}
            <Card className="rounded-none shadow-sm border border-[var(--color-border-subtle)]">
                <CardBody className="p-0">
                    {loading ? (
                        <div className="p-6">
                            <SkeletonTable rows={8} columns={10} />
                        </div>
                    ) : syntheseList.length === 0 ? (
                        <div className="p-12">
                            <EmptyState
                                icon={FileText}
                                title="Aucune donnée de synthèse"
                                description="Aucun pointage n'a été trouvé pour les filtres sélectionnés."
                            />
                        </div>
                    ) : (
                        <div className="overflow-x-auto">
                            <Table>
                                <Thead>
                                    <Th sortable sorted={sortBy === 'matricule' ? sortOrder : null} onSort={() => toggleSort('matricule')}>
                                        Salarié
                                    </Th>
                                    <Th>Site & Catégorie</Th>
                                    <Th className="text-center">Assiduité (P / A / C)</Th>
                                    <Th sortable sorted={sortBy === 'heures' ? sortOrder : null} onSort={() => toggleSort('heures')} className="text-right">
                                        H. Normales
                                    </Th>
                                    <Th sortable sorted={sortBy === 'hs' ? sortOrder : null} onSort={() => toggleSort('hs')} className="text-right">
                                        HS 30%
                                    </Th>
                                    <Th className="text-right">HS 50%</Th>
                                    <Th className="text-right">Dim. / Férié</Th>
                                    <Th sortable sorted={sortBy === 'absences' ? sortOrder : null} onSort={() => toggleSort('absences')} className="text-right">
                                        Retenue Absence
                                    </Th>
                                    <Th className="text-right" title="Forfait mensuel proratisé sur les jours ouvrables (1 jour = 2 trajets)">
                                        Déplacement (Trajets)
                                    </Th>
                                    <Th className="text-right" title="Indemnité journalière de repas">
                                        Repas (Jours)
                                    </Th>
                                    <Th className="text-center">Détail</Th>
                                </Thead>
                                <Tbody>
                                    {paginatedList.map((row) => (
                                        <Tr key={row.idEmploye}>
                                            {/* Salarié */}
                                            <Td>
                                                <div className="flex items-center gap-3">
                                                    <Avatar
                                                        src={row.photoUrl}
                                                        name={`${row.prenom || ''} ${row.nom || ''}`}
                                                        size="sm"
                                                        shape="square"
                                                        status="online"
                                                    />
                                                    <div className="min-w-0">
                                                        {canViewEmploye ? (
                                                            <Link
                                                                to={`/employes/${row.idEmploye}?tab=pointage`}
                                                                className="font-bold text-[var(--color-fg)] hover:text-[var(--color-brand-500)] transition-colors block truncate"
                                                            >
                                                                {row.nom} {row.prenom}
                                                            </Link>
                                                        ) : (
                                                            <span className="font-bold text-[var(--color-fg)] block truncate">
                                                                {row.nom} {row.prenom}
                                                            </span>
                                                        )}
                                                        <span className="text-[11px] text-[var(--color-fg-subtle)] font-mono">#{row.matricule}</span>
                                                    </div>
                                                </div>
                                            </Td>

                                            {/* Site & Catégorie */}
                                            <Td>
                                                <div className="flex flex-col gap-1">
                                                    <span className="text-xs text-[var(--color-fg)] font-medium">{row.siteNom}</span>
                                                    <Badge
                                                        variant={row.estCadre ? 'secondary' : 'info'}
                                                        size="sm"
                                                        className="w-fit text-[10px]"
                                                    >
                                                        {row.estCadre ? 'Cadre (Forfait)' : 'Non-Cadre'}
                                                    </Badge>
                                                </div>
                                            </Td>

                                            {/* Assiduité */}
                                            <Td className="text-center">
                                                <div className="flex items-center justify-center gap-1 font-mono text-xs">
                                                    <span className="text-emerald-700 dark:text-emerald-400 font-bold bg-emerald-500/10 px-1.5 py-0.5" title="Jours Présents">
                                                        {row.nbJoursPresents}j
                                                    </span>
                                                    <span className="text-[var(--color-fg-subtle)]">/</span>
                                                    <span className={`px-1.5 py-0.5 ${row.nbJoursAbsence > 0 ? 'text-rose-700 dark:text-rose-400 font-bold bg-rose-500/10' : 'text-[var(--color-fg-muted)]'}`} title="Jours Absences">
                                                        {row.nbJoursAbsence}j
                                                    </span>
                                                    <span className="text-[var(--color-fg-subtle)]">/</span>
                                                    <span className="text-sky-700 dark:text-sky-400 font-medium bg-sky-500/10 px-1.5 py-0.5" title="Jours Congés">
                                                        {row.nbJoursConge}j
                                                    </span>
                                                </div>
                                            </Td>

                                            {/* Heures Normales */}
                                            <Td className="text-right font-mono font-bold text-[var(--color-fg)]">
                                                {row.heuresNormales?.toFixed(1)}h
                                            </Td>

                                            {/* HS 30% */}
                                            <Td className="text-right font-mono">
                                                {row.heuresHs30 > 0 ? (
                                                    <span className="bg-sky-500/10 text-sky-700 dark:text-sky-400 font-bold px-2 py-0.5">
                                                        +{row.heuresHs30.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* HS 50% */}
                                            <Td className="text-right font-mono">
                                                {row.heuresHs50 > 0 ? (
                                                    <span className="bg-purple-500/10 text-purple-700 dark:text-purple-400 font-bold px-2 py-0.5">
                                                        +{row.heuresHs50.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Dimanche & Férié */}
                                            <Td className="text-right font-mono">
                                                {row.heuresDimancheTotal > 0 || row.heuresFerieTotal > 0 ? (
                                                    <span className="bg-amber-500/10 text-amber-700 dark:text-amber-400 font-semibold px-2 py-0.5">
                                                        {(row.heuresDimancheTotal + row.heuresFerieTotal).toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Retenues Absences */}
                                            <Td className="text-right font-mono">
                                                {row.heuresAbsenceRetenues > 0 ? (
                                                    <span className="bg-rose-500/10 text-rose-700 dark:text-rose-400 font-bold px-2 py-0.5">
                                                        -{row.heuresAbsenceRetenues.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Déplacement */}
                                            <Td className="text-right font-mono">
                                                {row.montantDeplacement > 0 ? (
                                                    <div>
                                                        <span className="font-bold text-[var(--color-fg)]">{row.montantDeplacement.toLocaleString('fr-FR')} Ar</span>
                                                        <span className="block text-[10px] text-[var(--color-fg-muted)]">
                                                            {row.nbJoursDeplacement}j • {row.nbJoursDeplacement * 2} trajets
                                                        </span>
                                                    </div>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Repas */}
                                            <Td className="text-right font-mono">
                                                {row.montantRepas > 0 ? (
                                                    <div>
                                                        <span className="font-bold text-[var(--color-fg)]">{row.montantRepas.toLocaleString('fr-FR')} Ar</span>
                                                        <span className="block text-[10px] text-[var(--color-fg-muted)]">{row.nbJoursRepas} jour(s)</span>
                                                    </div>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Action Détail Tronçons */}
                                            <Td className="text-center">
                                                <Button
                                                    size="xs"
                                                    variant="ghost"
                                                    icon={<Eye size={14} />}
                                                    onClick={() => onOpenTroncons(row)}
                                                    title="Voir la décomposition par semaine"
                                                >
                                                    Hebdo
                                                </Button>
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    )}

                    {/* Pagination */}
                    {syntheseList.length > 0 && (
                        <div className="p-4 border-t border-[var(--color-border-subtle)] flex flex-col sm:flex-row items-center justify-between gap-4">
                            <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)]">
                                <span>Lignes par page :</span>
                                <select
                                    value={pageSize}
                                    onChange={(e) => {
                                        setPageSize(Number(e.target.value));
                                        setCurrentPage(1);
                                    }}
                                    className="bg-[var(--color-surface)] border border-[var(--color-border)] px-2 py-1 text-xs rounded-none text-[var(--color-fg)]"
                                >
                                    <option value={10}>10</option>
                                    <option value={25}>25</option>
                                    <option value={50}>50</option>
                                    <option value={100}>100</option>
                                </select>
                            </div>

                            <Pagination
                                currentPage={currentPage}
                                pageSize={pageSize}
                                totalItems={syntheseList.length}
                                onPageChange={setCurrentPage}
                            />
                        </div>
                    )}
                </CardBody>
            </Card>
        </div>
    );
}
