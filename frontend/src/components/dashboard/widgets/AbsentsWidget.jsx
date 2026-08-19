import { UserCheck } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { fmtDate } from '../DashboardUtils';
import SkeletonListRows from './SkeletonListRows';

export default function AbsentsWidget({ loading, absentsAujourdhui = [] }) {
    return (
        <Card>
            <CardHeader>
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <UserCheck size={16} className="text-info-500" />
                        <CardTitle>Salariés Actuellement Absents / En Congé</CardTitle>
                    </div>
                    <Badge variant={absentsAujourdhui.length > 0 ? 'info' : 'default'} size="sm">
                        {loading ? '…' : absentsAujourdhui.length} absent(s)
                    </Badge>
                </div>
            </CardHeader>
            <CardBody>
                {loading && <SkeletonListRows count={3} />}
                {!loading && absentsAujourdhui.length === 0 && (
                    <EmptyState description="Aucun collaborateur en congé ou absent aujourd'hui." />
                )}
                {!loading && absentsAujourdhui.length > 0 && (
                    <div className="divide-y divide-[var(--color-border-subtle)]">
                        {absentsAujourdhui.slice(0, 5).map((a) => (
                            <div key={a.idDemandeConge} className="py-3 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {a.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">(#{a.matricule})</span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {a.typeConge} · Retour prévu : <span className="font-data font-semibold text-success-500">{fmtDate(a.dateFin)}</span>
                                    </p>
                                </div>
                                <span className="px-2 py-0.5 rounded-none text-[10px] font-data font-bold bg-info-500/10 text-info-500 border border-info-500/20 shrink-0">
                                    {a.nbJoursDecomptes}j posés
                                </span>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
