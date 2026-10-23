import { Layers, Plus, Edit2, Building, Power, Check } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import Badge from '@/components/ui/data-display/Badge';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import { SkeletonTable } from '@/components/ui/feedback/Skeleton';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { Card, CardHeader } from '@/components/ui/data-display/Card';

export default function ServicesList({
    selectedDepartement,
    services = [],
    loadingServices = false,
    canWrite = false,
    onOpenCreateService,
    onOpenEditService,
    onToggleServiceStatus,
}) {
    return (
        <Card className="space-y-4 flex flex-col h-full p-0 overflow-hidden" shadow="sm">
            {selectedDepartement ? (
                <div className="space-y-4 flex-1 flex flex-col">
                    <CardHeader divided className="flex flex-col sm:flex-row justify-between sm:items-center gap-3">
                        <div>
                            <h4 className="font-bold text-sm sm:text-base text-[var(--color-fg)] flex items-center gap-2">
                                <Layers size={18} className="text-[var(--color-brand-500)]" />
                                Services de : <span className="text-[var(--color-brand-500)]">{selectedDepartement.libelle}</span>
                            </h4>
                            <p className="text-[11px] text-[var(--color-fg-muted)] mt-0.5">
                                Code Département : <span className="font-mono font-bold">{selectedDepartement.codeDepartement}</span>
                            </p>
                        </div>
                        {canWrite && services.length > 0 && (
                            <Button
                                onClick={onOpenCreateService}
                                variant="primary"
                                size="sm"
                                iconLeft={<Plus size={16} />}
                            >
                                Nouveau Service
                            </Button>
                        )}
                    </CardHeader>

                    {loadingServices ? (
                        <div className="p-4">
                            <SkeletonTable rows={5} columns={4} bordered={false} />
                        </div>
                    ) : (
                        <div className="flex-1 overflow-x-auto">
                            <Table bordered={false}>
                                <Thead>
                                    <Th>Code Service</Th>
                                    <Th>Nom du Service</Th>
                                    <Th>Statut</Th>
                                {canWrite && <Th className="text-right">Actions</Th>}
                                </Thead>
                                <Tbody>
                                    {services.length > 0 ? (
                                        services.map((srv) => (
                                            <Tr key={srv.idService}>
                                                <Td isData className="font-bold text-[var(--color-brand-500)]">{srv.codeService}</Td>
                                                <Td className="font-bold text-[var(--color-fg)]">
                                                    <div className="flex items-center gap-2">
                                                        <Layers size={14} className="text-[var(--color-fg-muted)] shrink-0" />
                                                        <span>{srv.libelle}</span>
                                                    </div>
                                                </Td>
                                                <Td>
                                                    <Badge variant={srv.estActif ? 'success' : 'default'} size="sm" dot>
                                                        {srv.estActif ? "Actif" : "Désactivé"}
                                                    </Badge>
                                                </Td>
                                                {canWrite && <Td className="text-right">
                                                    <div className="flex justify-end items-center gap-1.5">
                                                        <button
                                                            onClick={() => onOpenEditService(srv)}
                                                            className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                            title="Modifier le service"
                                                            aria-label={`Modifier le service ${srv.libelle}`}
                                                        >
                                                            <Edit2 size={14} />
                                                        </button>
                                                        <button
                                                            onClick={() => onToggleServiceStatus(srv)}
                                                            className={`min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 rounded-none cursor-pointer transition-colors ${
                                                                srv.estActif
                                                                    ? 'text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10'
                                                                    : 'text-[var(--color-fg-muted)] hover:text-[var(--color-success-500)] hover:bg-[var(--color-success-500)]/10'
                                                            }`}
                                                            title={srv.estActif ? "Désactiver le service" : "Réactiver le service"}
                                                            aria-label={srv.estActif ? `Désactiver ${srv.libelle}` : `Activer ${srv.libelle}`}
                                                        >
                                                            {srv.estActif ? <Power size={14} /> : <Check size={14} />}
                                                        </button>
                                                    </div>
                                                </Td>}
                                            </Tr>
                                        ))
                                    ) : (
                                        <Tr>
                                            <Td colSpan={canWrite ? 4 : 3} className="p-0">
                                                <EmptyState
                                                    icon={Layers}
                                                    title="Aucun service rattaché"
                                                    description={canWrite ? `Créez le premier service sous le département "${selectedDepartement.libelle}".` : `Aucun service n'est rattaché au département "${selectedDepartement.libelle}".`}
                                                    actionLabel={canWrite ? "Ajouter un service" : undefined}
                                                    onAction={canWrite ? onOpenCreateService : undefined}
                                                    bordered={false}
                                                />
                                            </Td>
                                        </Tr>
                                    )}
                                </Tbody>
                            </Table>
                        </div>
                    )}
                </div>
            ) : (
                <div className="flex flex-col items-center justify-center py-20 text-[var(--color-fg-muted)] flex-1">
                    <Building size={40} className="mb-3 opacity-30 text-[var(--color-fg-subtle)]" />
                    <p className="text-sm font-semibold">Aucun département sélectionné</p>
                    <p className="text-xs text-[var(--color-fg-muted)] mt-1">Sélectionnez un département à gauche pour afficher ses services rattachés.</p>
                </div>
            )}
        </Card>
    );
}
