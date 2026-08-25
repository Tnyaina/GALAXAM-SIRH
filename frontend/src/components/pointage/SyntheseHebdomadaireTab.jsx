import { Link } from 'react-router-dom';
import { 
    Clock, 
    Calendar,
    AlertCircle,
    TrendingUp,
    CheckCircle2,
    CalendarRange,
    FileText
} from 'lucide-react';
import Card, { CardBody } from '@/components/ui/data-display/Card';
import StatCard from '@/components/ui/data-display/StatCard';
import { Table, Thead, Tbody, Tr, Th, Td } from '@/components/ui/data-display/Table';
import Badge from '@/components/ui/data-display/Badge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import Pagination from '@/components/ui/navigation/Pagination';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import { formatDate } from '@/utils/pointageUtils';

export default function SyntheseHebdomadaireTab({
    syntheseHebdoList,
    paginatedHebdoList,
    statsHebdo,
    loadingHebdo,
    canViewEmploye,
    selectedSemaine,
    setSelectedSemaine,
    hebdoSortBy,
    hebdoSortOrder,
    toggleHebdoSort,
    currentHebdoPage,
    setCurrentHebdoPage,
    hebdoPageSize,
    setHebdoPageSize
}) {
    return (
        <div className="space-y-6">
            {/* Filtre de semaine rapide */}
            <div className="flex flex-wrap items-center justify-between gap-4 bg-[var(--color-surface)] border border-[var(--color-border-subtle)] p-4 rounded-none">
                <div className="flex items-center gap-2">
                    <CalendarRange size={18} className="text-[var(--color-brand-500)]" />
                    <span className="text-sm font-bold text-[var(--color-fg)]">Tronçon de semaine :</span>
                    <div className="flex items-center gap-1">
                        <button
                            type="button"
                            onClick={() => setSelectedSemaine('')}
                            className={`px-3 py-1.5 text-xs font-semibold rounded-none border transition-colors ${
                                selectedSemaine === ''
                                    ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                    : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)]'
                            }`}
                        >
                            Toutes les semaines
                        </button>
                        {[1, 2, 3, 4, 5].map((sNum) => (
                            <button
                                key={sNum}
                                type="button"
                                onClick={() => setSelectedSemaine(String(sNum))}
                                className={`px-3 py-1.5 text-xs font-semibold rounded-none border transition-colors ${
                                    selectedSemaine === String(sNum)
                                        ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)]'
                                        : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border)] hover:bg-[var(--color-bg-subtle)]'
                                }`}
                            >
                                Semaine {sNum}
                            </button>
                        ))}
                    </div>
                </div>

                <div className="text-xs text-[var(--color-fg-muted)]">
                    <span className="font-semibold text-[var(--color-fg)]">{syntheseHebdoList.length}</span> tronçon(s) calculé(s)
                </div>
            </div>

            {/* Cartes KPI Hebdomadaires */}
            <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-3">
                <StatCard
                    title="Heures Hebdo"
                    value={`${statsHebdo.totalHeures.toFixed(0)}h`}
                    subtitle="Volume total travaillé"
                    icon={<Clock size={20} />}
                    color="brand"
                />
                <StatCard
                    title="Retards Bruts"
                    value={`${statsHebdo.totalRetards.toFixed(1)}h`}
                    subtitle="Retards constatés"
                    icon={<AlertCircle size={20} />}
                    color="warning"
                />
                <StatCard
                    title="Compensées"
                    value={`${statsHebdo.totalCompensees.toFixed(1)}h`}
                    subtitle="Retards absorbés"
                    icon={<CheckCircle2 size={20} />}
                    color="success"
                />
                <StatCard
                    title="HS 30% Nettes"
                    value={`+${statsHebdo.totalHs30.toFixed(1)}h`}
                    subtitle="8 premières HS"
                    icon={<TrendingUp size={20} />}
                    color="info"
                />
                <StatCard
                    title="HS 50% Nettes"
                    value={`+${statsHebdo.totalHs50.toFixed(1)}h`}
                    subtitle="Au-delà de 8 HS"
                    icon={<TrendingUp size={20} />}
                    color="brand"
                />
                <StatCard
                    title="Déficits Net"
                    value={`-${statsHebdo.totalDeficit.toFixed(1)}h`}
                    subtitle="Retenues résiduelles"
                    icon={<AlertCircle size={20} />}
                    color={statsHebdo.totalDeficit > 0 ? 'danger' : 'success'}
                />
            </div>

            {/* Tableau Hebdomadaire */}
            <Card className="rounded-none shadow-sm border border-[var(--color-border-subtle)]">
                <CardBody className="p-0">
                    {loadingHebdo ? (
                        <div className="p-6">
                            <SkeletonTable rows={8} columns={9} />
                        </div>
                    ) : syntheseHebdoList.length === 0 ? (
                        <div className="p-12">
                            <EmptyState
                                icon={FileText}
                                title="Aucun tronçon hebdomadaire"
                                description="Aucun pointage n'a été enregistré pour ce tronçon."
                            />
                        </div>
                    ) : (
                        <div className="overflow-x-auto">
                            <Table>
                                <Thead>
                                    <Th sortable sorted={hebdoSortBy === 'numeroSemaine' ? hebdoSortOrder : null} onSort={() => toggleHebdoSort('numeroSemaine')}>
                                        Semaine
                                    </Th>
                                    <Th sortable sorted={hebdoSortBy === 'nom' ? hebdoSortOrder : null} onSort={() => toggleHebdoSort('nom')}>
                                        Salarié
                                    </Th>
                                    <Th>Site & Catégorie</Th>
                                    <Th sortable sorted={hebdoSortBy === 'heures' ? hebdoSortOrder : null} onSort={() => toggleHebdoSort('heures')} className="text-right">
                                        Heures / Seuil
                                    </Th>
                                    <Th sortable sorted={hebdoSortBy === 'retard' ? hebdoSortOrder : null} onSort={() => toggleHebdoSort('retard')} className="text-right">
                                        Retard Brut
                                    </Th>
                                    <Th className="text-right" title="Heures sup utilisées pour absorber le retard">
                                        Compensé
                                    </Th>
                                    <Th className="text-right">HS 30%</Th>
                                    <Th className="text-right">HS 50%</Th>
                                    <Th className="text-right">Dim. / Férié</Th>
                                    <Th sortable sorted={hebdoSortBy === 'deficit' ? hebdoSortOrder : null} onSort={() => toggleHebdoSort('deficit')} className="text-right">
                                        Déficit (Retenue)
                                    </Th>
                                </Thead>
                                <Tbody>
                                    {paginatedHebdoList.map((row, idx) => (
                                        <Tr key={`${row.idEmploye}-${row.numeroSemaine}-${idx}`}>
                                            {/* Semaine */}
                                            <Td>
                                                <div className="flex flex-col">
                                                    <Badge variant="secondary" size="sm" className="w-fit font-bold font-mono">
                                                        Semaine {row.numeroSemaine}
                                                    </Badge>
                                                    <span className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                                        {formatDate(row.debutSemaine)} au {formatDate(row.finSemaine)}
                                                    </span>
                                                </div>
                                            </Td>

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

                                            {/* Heures Travaillées vs Seuil */}
                                            <Td className="text-right font-mono">
                                                <span className="font-bold text-[var(--color-fg)]">
                                                    {row.totalHeuresTravaillees?.toFixed(1)}h
                                                </span>
                                                <span className="text-[11px] text-[var(--color-fg-muted)] block">
                                                    / {row.seuilHeuresTroncon?.toFixed(0)}h ({row.nbJoursTroncon}j)
                                                </span>
                                            </Td>

                                            {/* Retards */}
                                            <Td className="text-right font-mono">
                                                {row.heuresRetard > 0 ? (
                                                    <span className="text-amber-700 dark:text-amber-400 font-semibold bg-amber-500/10 px-2 py-0.5">
                                                        {row.heuresRetard.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Compensées */}
                                            <Td className="text-right font-mono">
                                                {row.heuresCompensees > 0 ? (
                                                    <span className="text-emerald-700 dark:text-emerald-400 font-bold bg-emerald-500/10 px-2 py-0.5">
                                                        +{row.heuresCompensees.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* HS 30% */}
                                            <Td className="text-right font-mono">
                                                {row.heuresHs30 > 0 ? (
                                                    <span className="text-sky-700 dark:text-sky-400 font-bold bg-sky-500/10 px-2 py-0.5">
                                                        +{row.heuresHs30.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* HS 50% */}
                                            <Td className="text-right font-mono">
                                                {row.heuresHs50 > 0 ? (
                                                    <span className="text-purple-700 dark:text-purple-400 font-bold bg-purple-500/10 px-2 py-0.5">
                                                        +{row.heuresHs50.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Dimanche & Férié */}
                                            <Td className="text-right font-mono">
                                                {row.heuresDimanche > 0 || row.heuresFerie > 0 ? (
                                                    <span className="text-amber-700 dark:text-amber-400 font-semibold bg-amber-500/10 px-2 py-0.5">
                                                        {(row.heuresDimanche + row.heuresFerie).toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                                )}
                                            </Td>

                                            {/* Déficit (Retenue) */}
                                            <Td className="text-right font-mono">
                                                {row.heuresDeficit > 0 ? (
                                                    <span className="text-rose-700 dark:text-rose-400 font-bold bg-rose-500/10 px-2 py-0.5">
                                                        -{row.heuresDeficit.toFixed(1)}h
                                                    </span>
                                                ) : (
                                                    <span className="text-emerald-700 dark:text-emerald-400 font-semibold">0h</span>
                                                )}
                                            </Td>
                                        </Tr>
                                    ))}
                                </Tbody>
                            </Table>
                        </div>
                    )}

                    {/* Pagination */}
                    {syntheseHebdoList.length > 0 && (
                        <div className="p-4 border-t border-[var(--color-border-subtle)] flex flex-col sm:flex-row items-center justify-between gap-4">
                            <div className="flex items-center gap-2 text-xs text-[var(--color-fg-muted)]">
                                <span>Lignes par page :</span>
                                <select
                                    value={hebdoPageSize}
                                    onChange={(e) => {
                                        setHebdoPageSize(Number(e.target.value));
                                        setCurrentHebdoPage(1);
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
                                currentPage={currentHebdoPage}
                                pageSize={hebdoPageSize}
                                totalItems={syntheseHebdoList.length}
                                onPageChange={setCurrentHebdoPage}
                            />
                        </div>
                    )}
                </CardBody>
            </Card>
        </div>
    );
}
