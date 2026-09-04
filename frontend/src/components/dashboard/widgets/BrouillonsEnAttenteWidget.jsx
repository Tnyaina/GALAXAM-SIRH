import { Link } from 'react-router-dom';
import { FileText } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { fmtDate } from '../DashboardUtils';
import SkeletonListRows from './SkeletonListRows';

export default function BrouillonsEnAttenteWidget({ loading, brouillons = [], className = '' }) {
    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <FileText size={16} className="text-warning-500 shrink-0" />
                        <CardTitle>Brouillons en attente de validation DAF</CardTitle>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        <Badge variant={brouillons.length > 0 ? 'warning' : 'default'} size="sm">
                            {loading ? '…' : brouillons.length} en attente
                        </Badge>
                    </div>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && brouillons.length === 0 && (
                    <EmptyState compact description="Aucun brouillon de contrat ou d'avenant en attente." />
                )}
                {!loading && brouillons.length > 0 && (
                    <div className="divide-y divide-[var(--color-border-subtle)]">
                        {brouillons.slice(0, 5).map((b) => (
                            <div key={b.idWorkflow} className="py-3 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {b.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">(#{b.matricule})</span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {b.typeEvenement} · Date d'effet prévue : <span className="font-data">{fmtDate(b.dateEffet)}</span>
                                    </p>
                                </div>
                                <div className="flex items-center gap-3 shrink-0">
                                    <span className={`font-data font-extrabold text-xs ${b.joursAttente > 2 ? 'text-danger-500' : 'text-warning-500'}`}>
                                        {b.joursAttente}j d'attente
                                    </span>
                                    <Link
                                        to={`/employes/${b.idEmploye}?tab=carriere`}
                                        className="px-2.5 py-1 rounded-none text-[11px] font-semibold uppercase tracking-wide bg-[var(--color-brand-500)]/15 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/30 hover:bg-[var(--color-brand-500)] hover:text-white"
                                    >
                                        Consulter
                                    </Link>
                                </div>
                            </div>
                        ))}
                    </div>
                )}
            </CardBody>
        </Card>
    );
}
