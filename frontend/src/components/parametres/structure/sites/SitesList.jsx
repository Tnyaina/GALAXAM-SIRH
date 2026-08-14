import { Edit2 } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';

export default function SitesList({
    sites = [],
    onOpenEdit,
    onToggleStatus,
}) {
    return (
        <Table>
            <Thead>
                <Th>Code Site</Th>
                <Th>Nom du Site</Th>
                <Th>Filiale Rattachement</Th>
                <Th>Régime Horaire</Th>
                <Th>Horaires Matin / Après-midi</Th>
                <Th>Statut</Th>
                <Th className="text-right">Actions</Th>
            </Thead>
            <Tbody>
                {sites.length > 0 ? (
                    sites.map((s) => (
                        <Tr key={s.idSite}>
                            <Td isData className="font-bold text-[var(--color-brand-500)]">{s.codeSite}</Td>
                            <Td className="font-bold text-[var(--color-fg)]">{s.libelle}</Td>
                            <Td>
                                {s.filialeRaisonSociale ? (
                                    <Badge variant="primary" size="sm">
                                        {s.filialeRaisonSociale}
                                    </Badge>
                                ) : (
                                    <span className="text-[var(--color-fg-subtle)]">-</span>
                                )}
                            </Td>
                            <Td>
                                <Badge variant={s.horaireContinu ? 'info' : 'warning'} size="sm">
                                    {s.horaireContinu ? "Horaire Continu" : "Discontinu (Pause)"}
                                </Badge>
                            </Td>
                            <Td isData muted className="text-xs font-mono">
                                <div>Matin : {s.heureDebutMatin ? s.heureDebutMatin.substring(0, 5) : '--'} - {s.heureFinMatin ? s.heureFinMatin.substring(0, 5) : '--'}</div>
                                <div>A-Midi : {s.heureDebutApresMidi ? s.heureDebutApresMidi.substring(0, 5) : '--'} - {s.heureFinApresMidi ? s.heureFinApresMidi.substring(0, 5) : '--'}</div>
                            </Td>
                            <Td>
                                <Badge variant={s.estActif ? 'success' : 'default'} size="sm" dot>
                                    {s.estActif ? "Actif" : "Désactivé"}
                                </Badge>
                            </Td>
                            <Td className="text-right">
                                <div className="flex justify-end items-center gap-2">
                                    <button
                                        onClick={() => onOpenEdit(s)}
                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none transition-all cursor-pointer"
                                        title="Modifier"
                                    >
                                        <Edit2 size={16} />
                                    </button>
                                    <button
                                        onClick={() => onToggleStatus(s)}
                                        className={`px-2 py-1 text-xs font-bold rounded-none transition-all cursor-pointer uppercase ${
                                            s.estActif
                                                ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                        }`}
                                    >
                                        {s.estActif ? "Désactiver" : "Activer"}
                                    </button>
                                </div>
                            </Td>
                        </Tr>
                    ))
                ) : (
                    <Tr>
                        <Td colSpan={7} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                            Aucun site d'exploitation enregistré.
                        </Td>
                    </Tr>
                )}
            </Tbody>
        </Table>
    );
}