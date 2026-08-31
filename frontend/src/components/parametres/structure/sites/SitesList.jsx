import { Edit2, MapPin, Power } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function SitesList({
    sites = [],
    onOpenEdit,
    onToggleStatus,
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
                                        <span className="text-xs text-[var(--color-fg-subtle)] italic">Non assignée</span>
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
                                    <Badge variant={s.estActif ? 'success' : 'default'} size="sm" dot>
                                        {s.estActif ? "Actif" : "Désactivé"}
                                    </Badge>
                                </Td>
                                <Td className="text-right">
                                    <div className="flex justify-end items-center gap-1.5">
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
                                            className={`p-1.5 rounded-none cursor-pointer transition-colors ${
                                                s.estActif
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
                        <Td colSpan={7} className="p-0">
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