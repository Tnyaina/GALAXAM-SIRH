import { Edit2, Building2, Power, Check } from 'lucide-react';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';

export default function FilialesList({
    filiales = [],
    onOpenEdit,
    onToggleStatus,
    onOpenCreate,
}) {
    return (
        <Table bordered={false}>
            <Thead>
                <Th>Code</Th>
                <Th>Société et siège</Th>
                <Th>NIF</Th>
                <Th>STAT</Th>
                <Th>RCS</Th>
                <Th>Statut</Th>
                <Th className="text-right">Actions</Th>
            </Thead>
            <Tbody>
                {filiales.length > 0 ? (
                    filiales.map((f) => (
                        <Tr key={f.idFiliale}>
                            <Td isData className="font-bold text-[var(--color-brand-500)]">{f.codeFiliale}</Td>
                            <Td className="font-bold text-[var(--color-fg)]">
                                <div className="flex items-center gap-2">
                                    <Building2 size={16} className="text-[var(--color-fg-muted)] shrink-0" />
                                    <span>{f.raisonSociale}</span>
                                </div>
                                <div className="text-xs font-normal text-[var(--color-fg-muted)] mt-1">{f.adresseSiege || 'Siège à renseigner'}</div>
                            </Td>
                            <Td isData muted className="text-xs">{f.nif || '-'}</Td>
                            <Td isData muted className="text-xs">{f.stat || '-'}</Td>
                            <Td isData muted className="text-xs">{f.rcs || 'À renseigner'}</Td>
                            <Td>
                                <Badge variant={f.estActif ? 'success' : 'default'} size="sm" dot>
                                    {f.estActif ? "Actif" : "Désactivé"}
                                </Badge>
                            </Td>
                            <Td className="text-right">
                                <div className="flex justify-end items-center gap-1.5">
                                    <button
                                        onClick={() => onOpenEdit(f)}
                                        className="ui-button-icon-xs inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                        title="Modifier la filiale"
                                        aria-label={`Modifier la filiale ${f.raisonSociale}`}
                                    >
                                        <Edit2 size={15} />
                                    </button>
                                    <button
                                        onClick={() => onToggleStatus(f)}
                                        className={`ui-button-icon-xs inline-flex items-center justify-center p-1.5 rounded-none cursor-pointer transition-colors ${
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
                            </Td>
                        </Tr>
                    ))
                ) : (
                    <Tr>
                        <Td colSpan={7} className="p-0">
                            <EmptyState
                                icon={Building2}
                                title="Aucune filiale enregistrée"
                                description="Créez la première entité juridique de votre organisation pour commencer."
                                actionLabel="Créer une filiale"
                                onAction={onOpenCreate}
                            />
                        </Td>
                    </Tr>
                )}
            </Tbody>
        </Table>
    );
}
