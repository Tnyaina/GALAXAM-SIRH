import { Link } from 'react-router-dom';
import { Calendar, ArrowRight } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import { fmtDate } from '../DashboardUtils';
import SkeletonListRows from './SkeletonListRows';

export default function CongesAValiderWidget({ loading, demandesCongeAValider = [], className = '' }) {
    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <Calendar size={16} className="text-warning-500 shrink-0" />
                        <CardTitle>Demandes de Congé à Valider (DAF / RH)</CardTitle>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                        <Badge variant={demandesCongeAValider.length > 0 ? 'warning' : 'default'} size="sm">
                            {loading ? '…' : demandesCongeAValider.length} en attente
                        </Badge>
                        <Link
                            to="/conges"
                            className="text-xs font-bold text-[var(--color-brand-500)] hover:text-[var(--color-brand-300)] flex items-center gap-1"
                        >
                            Gérer <ArrowRight size={12} />
                        </Link>
                    </div>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && demandesCongeAValider.length === 0 && (
                    <EmptyState compact description="Aucune demande de congé en attente de validation." />
                )}
                {!loading && demandesCongeAValider.length > 0 && (
                    <div className="max-h-[280px] overflow-y-auto pr-1 divide-y divide-[var(--color-border-subtle)]">
                        {demandesCongeAValider.slice(0, 5).map((d) => (
                            <div key={d.idDemandeConge} className="py-3 flex items-center justify-between gap-3 text-xs">
                                <div className="min-w-0">
                                    <p className="font-bold text-[var(--color-fg)] truncate">
                                        {d.nomPrenom} <span className="font-data text-[10px] text-[var(--color-fg-subtle)]">(#{d.matricule})</span>
                                    </p>
                                    <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                                        {d.typeConge} · Du <span className="font-data">{fmtDate(d.dateDebut)}</span> au <span className="font-data">{fmtDate(d.dateFin)}</span>
                                    </p>
                                </div>
                                <div className="flex items-center gap-3 shrink-0">
                                    <span className="font-data font-extrabold text-[var(--color-brand-500)] text-xs">
                                        {d.nbJoursDecomptes}j
                                    </span>
                                    <Link
                                        to="/conges?tab=demandes"
                                        className="px-2.5 py-1 rounded-none text-[11px] font-semibold uppercase tracking-wide bg-[var(--color-brand-500)]/15 text-[var(--color-brand-500)] border border-[var(--color-brand-500)]/30 hover:bg-[var(--color-brand-500)] hover:text-white "
                                    >
                                        Valider
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
