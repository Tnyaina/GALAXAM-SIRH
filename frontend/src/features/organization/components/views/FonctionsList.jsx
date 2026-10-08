import { Briefcase, Edit2, Power, Check } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function FonctionsList({
    fonctions = [],
    onEdit,
    onToggleStatus,
    onCreate
}) {
    return (
        <Table bordered={false}>
            <Thead>
                <Th>Code Poste</Th>
                <Th>Intitulé du Poste / Fonction</Th>
                <Th>Régime Pointage</Th>
                <Th>Statut</Th>
                <Th className="text-right">Actions</Th>
            </Thead>
            <Tbody>
                {fonctions.length > 0 ? (
                    fonctions.map((f) => (
                        <Tr key={f.idFonction}>
                            <Td isData className="font-bold text-[var(--color-brand-500)]">{f.codeFonction}</Td>
                            <Td className="font-bold text-[var(--color-fg)]">
                                <div className="flex items-center gap-2">
                                    <Briefcase size={15} className="text-[var(--color-fg-muted)] shrink-0" />
                                    <span>{f.libelle}</span>
                                </div>
                            </Td>
                            <Td>
                                {f.dispensePointage ? (
                                    <Badge variant="warning" size="sm">
                                        Dispense de pointage
                                    </Badge>
                                ) : (
                                    <Badge variant="default" size="sm">
                                        Pointage standard
                                    </Badge>
                                )}
                            </Td>
                            <Td>
                                <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                    {f.estActif ? "Actif" : "Désactivé"}
                                </Badge>
                            </Td>
                            <Td className="text-right">
                                <div className="flex justify-end items-center gap-1.5">
                                    <button
                                        onClick={() => onEdit(f)}
                                        className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                        title="Modifier le poste"
                                        aria-label={`Modifier ${f.libelle}`}
                                    >
                                        <Edit2 size={15} />
                                    </button>
                                    <button
                                        onClick={() => onToggleStatus(f)}
                                        className={`min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 rounded-none cursor-pointer transition-colors ${
                                            f.estActif
                                                ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                        }`}
                                        title={f.estActif ? "Désactiver le poste" : "Réactiver le poste"}
                                        aria-label={f.estActif ? `Désactiver ${f.libelle}` : `Activer ${f.libelle}`}
                                    >
                                        {f.estActif ? <Power size={15} /> : <Check size={15} />}
                                    </button>
                                </div>
                            </Td>
                        </Tr>
                    ))
                ) : (
                    <Tr>
                        <Td colSpan={5} className="p-0">
                            <EmptyState
                                icon={Briefcase}
                                title="Aucun poste ou fonction trouvé"
                                description="Ajoutez les fonctions et postes de l'organigramme pour les lier aux contrats."
                                actionLabel="Créer un poste"
                                onAction={onCreate}
                            />
                        </Td>
                    </Tr>
                )}
            </Tbody>
        </Table>
    );
}
