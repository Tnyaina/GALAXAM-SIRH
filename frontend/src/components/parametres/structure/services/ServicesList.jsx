import { Layers, Plus, Edit2, Building } from 'lucide-react';
import Button from '@/components/ui/Button';
import Badge from '@/components/ui/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/Table';
import Loading from '@/components/ui/Loading';

export default function ServicesList({
    selectedDepartement,
    services = [],
    loadingServices = false,
    onOpenCreateService,
    onOpenEditService,
    onToggleServiceStatus,
}) {
    return (
        <div className="lg:col-span-2 bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            {selectedDepartement ? (
                <div className="space-y-4">
                    <div className="flex flex-col sm:flex-row justify-between sm:items-center pb-4 border-b border-[var(--color-border-subtle)] gap-3">
                        <div>
                            <h4 className="font-bold text-base text-[var(--color-fg)] flex items-center gap-2">
                                <Layers size={18} className="text-[var(--color-brand-500)]" />
                                Services rattachés à : <span className="text-[var(--color-brand-500)]">{selectedDepartement.libelle}</span>
                            </h4>
                            <p className="text-xs text-[var(--color-fg-muted)]">Code Département : {selectedDepartement.codeDepartement}</p>
                        </div>
                        <Button
                            onClick={onOpenCreateService}
                            variant="primary"
                            size="sm"
                            iconLeft={<Plus size={16} />}
                        >
                            Ajouter un Service
                        </Button>
                    </div>

                    {loadingServices ? (
                        <div className="flex justify-center items-center py-12">
                            <Loading size="sm" label="Chargement des services..." />
                        </div>
                    ) : (
                        <Table>
                            <Thead>
                                <Th>Code Service</Th>
                                <Th>Nom du Service</Th>
                                <Th>Statut</Th>
                                <Th className="text-right">Actions</Th>
                            </Thead>
                            <Tbody>
                                {services.length > 0 ? (
                                    services.map((srv) => (
                                        <Tr key={srv.idService}>
                                            <Td isData className="font-bold text-[var(--color-brand-500)]">{srv.codeService}</Td>
                                            <Td className="font-semibold text-[var(--color-fg)]">{srv.libelle}</Td>
                                            <Td>
                                                <Badge variant={srv.estActif ? 'success' : 'default'} size="sm" dot>
                                                    {srv.estActif ? "Actif" : "Désactivé"}
                                                </Badge>
                                            </Td>
                                            <Td className="text-right">
                                                <div className="flex justify-end items-center gap-2">
                                                    <button
                                                        onClick={() => onOpenEditService(srv)}
                                                        className="p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none transition-all cursor-pointer"
                                                        title="Modifier"
                                                    >
                                                        <Edit2 size={15} />
                                                    </button>
                                                    <button
                                                        onClick={() => onToggleServiceStatus(srv)}
                                                        className={`px-2 py-1 text-xs font-bold rounded-none transition-all cursor-pointer uppercase ${
                                                            srv.estActif
                                                                ? 'text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                                : 'text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                        }`}
                                                    >
                                                        {srv.estActif ? "Désactiver" : "Activer"}
                                                    </button>
                                                </div>
                                            </Td>
                                        </Tr>
                                    ))
                                ) : (
                                    <Tr>
                                        <Td colSpan={4} className="py-8 text-center text-[var(--color-fg-muted)] text-xs">
                                            Aucun service rattaché à ce département.
                                        </Td>
                                    </Tr>
                                )}
                            </Tbody>
                        </Table>
                    )}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-20 text-[var(--color-fg-muted)]">
                    <Building size={36} className="mb-2 opacity-40 text-[var(--color-fg-subtle)]" />
                    <p className="text-sm">Veuillez sélectionner un département à gauche.</p>
                </div>
            )}
        </div>
    );
}