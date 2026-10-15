import SharedButton from '@/components/ui/forms/Button';
import { useState } from 'react';
import { Edit2, Trash2, Calendar, MapPin, CheckCircle, Clock } from 'lucide-react';
import Button from '@/components/ui/forms/Button';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Badge from '@/components/ui/data-display/Badge';
import ConfirmDialog from '@/components/ui/overlays/ConfirmDialog';
import { fmtDate } from '@/utils/dateUtils';

export default function PeriodeChomeeList({ periodes, sites, onOpenCreate, onOpenEdit, onDelete }) {
    const [deleteTarget, setDeleteTarget] = useState(null);
    if (!periodes || periodes.length === 0) {
        return (
            <EmptyState
                title="Aucune période exceptionnelle"
                description="Déclarez ici les suspensions d'activité (cyclones, force majeure) s'étalant sur plusieurs jours."
                actionLabel="Déclarer une période"
                onAction={onOpenCreate}
            />
        );
    }

    const getSiteName = (idSite) => {
        if (!idSite) return 'Nationale';
        const site = sites.find(s => String(s.idSite) === String(idSite));
        return site ? site.libelle : `Site #${idSite}`;
    };

    return (
        <div className="bg-[var(--color-surface)] rounded-none border border-[var(--color-border-subtle)] shadow-sm p-6 space-y-4">
            <div className="flex justify-between items-center pb-3 border-b border-[var(--color-border-subtle)]">
                <div>
                    <h3 className="font-bold text-[var(--color-fg)] text-base flex items-center gap-2">
                        <Calendar size={18} className="text-[var(--color-brand-500)]" />
                        Périodes Exceptionnelles ({periodes.length})
                    </h3>
                    <p className="text-xs text-[var(--color-fg-muted)]">
                        Gestion des suspensions (Cyclones, Événements majeurs)
                    </p>
                </div>
                <Button onClick={onOpenCreate} variant="primary" size="sm" iconLeft={<Calendar size={16} />}>
                    Déclarer une période
                </Button>
            </div>
            
            <div className="overflow-x-auto">
                <Table bordered={false}>
                    <Thead>
                        <Tr>
                            <Th>Événement</Th>
                            <Th>Portée</Th>
                            <Th>Dates (Début - Fin)</Th>
                            <Th>Statut</Th>
                            <Th>Traitement Paie</Th>
                            <Th className="text-right">Actions</Th>
                        </Tr>
                    </Thead>
                    <Tbody>
                        {periodes.map(p => {
                            const estEnCours = !p.dateFin;
                            
                            return (
                                <Tr key={p.idPeriode} className="hover:bg-[var(--color-surface-hover)]">
                                    <Td className="font-semibold text-sm text-[var(--color-fg)]">
                                        {p.libelle}
                                    </Td>
                                    <Td>
                                        {p.idSite ? (
                                            <div className="flex items-center gap-1.5 text-[var(--color-fg-muted)]">
                                                <MapPin size={14} /> <span className="text-xs font-bold">{getSiteName(p.idSite)}</span>
                                            </div>
                                        ) : (
                                            <Badge variant="brand" size="sm">Nationale</Badge>
                                        )}
                                    </Td>
                                    <Td className="font-data text-xs text-[var(--color-fg-subtle)]">
                                        Du <strong>{fmtDate(p.dateDebut)}</strong>
                                        <br/>
                                        {p.dateFin ? (
                                            <>Au <strong>{fmtDate(p.dateFin)}</strong></>
                                        ) : (
                                            <span className="text-[var(--color-warning-500)] font-semibold">Jusqu'à nouvel ordre</span>
                                        )}
                                    </Td>
                                    <Td>
                                        {estEnCours ? (
                                            <Badge variant="warning" dot size="sm" icon={<Clock size={12}/>}>En cours</Badge>
                                        ) : (
                                            <Badge variant="success" size="sm" icon={<CheckCircle size={12}/>}>Terminée</Badge>
                                        )}
                                    </Td>
                                    <Td>
                                        <div className="flex items-center gap-2">
                                            {p.estPaye ? (
                                                <Badge variant="success" size="sm">Payé ({p.tauxPaiement}%)</Badge>
                                            ) : (
                                                <Badge variant="danger" size="sm">Non Payé</Badge>
                                            )}
                                        </div>
                                    </Td>
                                    <Td>
                                        <div className="flex items-center justify-end gap-1.5">
                                            <SharedButton plain
                                                onClick={() => onOpenEdit(p)}
                                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-brand-500)] hover:bg-[var(--color-bg-subtle)] rounded-none cursor-pointer transition-colors"
                                                title="Modifier"
                                                aria-label={`Modifier période ${p.libelle}`}
                                            >
                                                <Edit2 size={16} />
                                            </SharedButton>
                                            <SharedButton plain
                                                onClick={() => setDeleteTarget(p)}
                                                className="min-h-[44px] min-w-[44px] inline-flex items-center justify-center p-1.5 text-[var(--color-fg-muted)] hover:text-[var(--color-danger-500)] hover:bg-[var(--color-danger-500)]/10 rounded-none cursor-pointer transition-colors"
                                                title="Supprimer"
                                                aria-label={`Supprimer période ${p.libelle}`}
                                            >
                                                <Trash2 size={16} />
                                            </SharedButton>
                                        </div>
                                    </Td>
                                </Tr>
                            );
                        })}
                    </Tbody>
                </Table>
            </div>

            <ConfirmDialog
                isOpen={Boolean(deleteTarget)}
                onClose={() => setDeleteTarget(null)}
                onConfirm={() => {
                    if (deleteTarget) {
                        onDelete(deleteTarget.idPeriode);
                        setDeleteTarget(null);
                    }
                }}
                title="Supprimer cette période ?"
                message={deleteTarget ? `Voulez-vous vraiment supprimer la période exceptionnelle "${deleteTarget.libelle}" ?` : ''}
                confirmLabel="Supprimer"
                cancelLabel="Conserver"
                variant="danger"
            />
        </div>
    );
}
