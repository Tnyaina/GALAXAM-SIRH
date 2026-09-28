import { Edit2, MapPin, Power, Clock } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function SitesList({
    sites = [],
    onOpenEdit,
    onToggleStatus,
    onTogglePlancher,
    onOpenCreate,
}) {
    return (
        <Table>
            <Thead>
                <Th>Code Site</Th>
                <Th>Nom du Site</Th>
                <Th>Filiale Juridique</Th>
                <Th>Régime Horaire</Th>
                <Th>Plages Horaires Applicables</Th>
                <Th>Plancher d'Arrivée</Th>
                <Th>Statut</Th>
                <Th className="text-right">Actions</Th>
            </Thead>
            <Tbody>
                {sites.length > 0 ? (
                    sites.map((s) => {
                        const isContinu = s.horaireContinu !== false;
                        const dMat = s.heureDebutMatin ? s.heureDebutMatin.substring(0, 5) : '08:00';
                        const fMat = s.heureFinMatin ? s.heureFinMatin.substring(0, 5) : '12:00';
                        const dPm = s.heureDebutApresMidi ? s.heureDebutApresMidi.substring(0, 5) : '13:00';
                        const fPm = s.heureFinApresMidi ? s.heureFinApresMidi.substring(0, 5) : '17:00';

                        return (
                            <Tr key={s.idSite}>
                                <Td isData className="font-bold text-[var(--color-brand-500)]">{s.codeSite}</Td>
                                <Td className="font-bold text-[var(--color-fg)]">
                                    <div className="flex items-center gap-2">
                                        <MapPin size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                                        <span>{s.libelle}</span>
                                    </div>
                                </Td>
                                <Td>
                                    {s.filialeRaisonSociale ? (
                                        <Badge variant="primary" size="sm">
                                            {s.filialeRaisonSociale}
                                        </Badge>
                                    ) : (
                                        <span className="text-xs text-[var(--color-fg-subtle)] ">Non assignée</span>
                                    )}
                                </Td>
                                <Td>
                                    <Badge variant={isContinu ? 'brand' : 'warning'} size="sm">
                                        {isContinu ? "Continu" : "Discontinu"}
                                    </Badge>
                                </Td>
                                <Td className="whitespace-nowrap">
                                    <div className="flex flex-col gap-1.5 py-0.5">
                                        <div className="flex items-center gap-2">
                                            <span className="inline-flex items-center justify-center px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] min-w-[48px] rounded-none">
                                                Matin
                                            </span>
                                            <span className="font-mono text-xs font-semibold text-[var(--color-fg)]">
                                                {dMat} <span className="text-[var(--color-fg-subtle)] font-normal px-0.5">—</span> {fMat}
                                            </span>
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <span className="inline-flex items-center justify-center px-1.5 py-0.5 text-[9px] font-bold uppercase tracking-wider text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] min-w-[48px] rounded-none">
                                                A-Midi
                                            </span>
                                            <span className="font-mono text-xs font-semibold text-[var(--color-fg)]">
                                                {dPm} <span className="text-[var(--color-fg-subtle)] font-normal px-0.5">—</span> {fPm}
                                            </span>
                                        </div>
                                    </div>
                                </Td>
                                <Td>
                                    {s.plancherArriveeActif ? (
                                        <span className="inline-flex items-center gap-1.5 font-mono text-xs font-bold text-[var(--color-brand-600)] bg-[var(--color-brand-500)]/10 border border-[var(--color-brand-500)]/20 px-2.5 py-1 rounded-none">
                                            <Clock size={12} className="text-[var(--color-brand-500)]" />
                                            {s.heureEntreeMinimaleValorisee ? s.heureEntreeMinimaleValorisee.substring(0, 5) : '07:30'}
                                        </span>
                                    ) : (
                                        <span className="inline-flex items-center gap-1 text-[11px] text-[var(--color-fg-muted)] bg-[var(--color-bg-subtle)] border border-[var(--color-border-subtle)] px-2 py-0.5 rounded-none">
                                            <Clock size={12} className="opacity-40" />
                                            Désactivé
                                        </span>
                                    )}
                                </Td>
                                <Td>
                                    <Badge variant={s.estActif ? 'success' : 'default'} size="sm" dot>
                                        {s.estActif ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
                                        <button
                                            onClick={() => onTogglePlancher && onTogglePlancher(s)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                s.plancherArriveeActif
                                                    ? 'text-[var(--color-brand-500)] hover:text-[var(--color-warning-600)] hover:bg-[var(--color-warning-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-brand-500)]/10'
                                            }`}
                                            title={s.plancherArriveeActif ? "Désactiver le plancher d'arrivée" : "Activer le plancher d'arrivée"}
                                            aria-label={s.plancherArriveeActif ? `Désactiver le plancher pour ${s.libelle}` : `Activer le plancher pour ${s.libelle}`}
                                        >
                                            <Clock size={15} />
                                        </button>
                                        <button
                                            onClick={() => onOpenEdit(s)}
                                            className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                            title="Modifier le site"
                                            aria-label={`Modifier le site ${s.libelle}`}
                                        >
                                            <Edit2 size={15} />
                                        </button>
                                        <button
                                            onClick={() => onToggleStatus(s)}
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${s.estActif
                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                }`}
                                            title={s.estActif ? "Désactiver le site" : "Réactiver le site"}
                                            aria-label={s.estActif ? `Désactiver ${s.libelle}` : `Activer ${s.libelle}`}
                                        >
                                            <Power size={15} />
                                        </button>
                                    </div>
                                </Td>
                            </Tr>
                        );
                    })
                ) : (
                    <Tr>
                        <Td colSpan={8} className="p-0">
                            <EmptyState
                                icon={MapPin}
                                title="Aucun site d'exploitation enregistré"
                                description="Créez votre premier site pour lui attribuer des horaires et y affecter des collaborateurs."
                                actionLabel="Créer un site"
                                onAction={onOpenCreate}
                            />
                        </Td>
                    </Tr>
                )}
            </Tbody>
        </Table>
    );
}