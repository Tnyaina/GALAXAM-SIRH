import { memo } from 'react';
import { Link } from 'react-router-dom';
import { AlertTriangle, ChevronRight } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import ExpandableList from './ExpandableList';
import { fmtDate } from '../DashboardUtils';
import SkeletonListRows from './SkeletonListRows';

const AlerteRow = memo(function AlerteRow({ alerte }) {
    const isToday = alerte.joursRestants === 0;
    const isUrgent = alerte.joursRestants <= 7;
    return (
        <Link
            to={`/employes/${alerte.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] group"
        >
            <div className={`w-2 h-2 rounded-none shrink-0 ${isToday ? 'bg-[var(--color-danger-500)]' : isUrgent ? 'bg-[var(--color-warning-500)]' : 'bg-[var(--color-brand-500)]'}`} />
            <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">
                    {alerte.nomPrenom}
                </p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">
                    {alerte.typeContrat}
                    {alerte.numeroContrat ? ` · N°${alerte.numeroContrat}` : ''}
                    {' · '}<span className="font-data">{fmtDate(alerte.dateEcheance)}</span>
                </p>
            </div>
            <Badge variant={isToday ? 'danger' : isUrgent ? 'warning' : 'primary'} size="sm">
                {isToday ? 'Auj.' : `J-${alerte.joursRestants}`}
            </Badge>
            <ChevronRight size={13} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-500)] shrink-0" />
        </Link>
    );
});

export default function EcheancesWidget({ loading, alertesCritiques = [], alertes7j = [], alertes30j = [], totalAlertesContrats = 0, className = '' }) {
    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center">
                <div className="flex items-center justify-between w-full">
                    <div className="flex items-center gap-2">
                        <AlertTriangle size={16} className="text-danger-500 shrink-0" />
                        <CardTitle>Échéances de Contrats & Périodes d'Essai (7 à 30 jours)</CardTitle>
                    </div>
                    <Badge variant={totalAlertesContrats > 0 ? 'warning' : 'default'} size="sm" className="shrink-0">
                        {loading ? '…' : totalAlertesContrats} alerte(s)
                    </Badge>
                </div>
            </CardHeader>
            <CardBody className="flex-1 flex flex-col justify-center">
                {loading && <SkeletonListRows count={3} />}
                {!loading && alertesCritiques.length === 0 && alertes7j.length === 0 && alertes30j.length === 0 && (
                    <EmptyState compact description="Aucun contrat ni période d'essai n'arrive à échéance dans les 30 prochains jours." />
                )}
                {!loading && (alertesCritiques.length > 0 || alertes7j.length > 0 || alertes30j.length > 0) && (
                    <ExpandableList
                        items={[...alertesCritiques, ...alertes7j, ...alertes30j]}
                        initialCount={6}
                        renderItem={(a, i) => <AlerteRow key={i} alerte={a} />}
                    />
                )}
            </CardBody>
        </Card>
    );
}
