import { Link } from 'react-router-dom';
import { AlertTriangle, CalendarCheck, ChevronRight, ClipboardCheck, FileText, ShieldAlert } from 'lucide-react';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import SkeletonListRows from './SkeletonListRows';
import { useAuth } from '@/context/AuthContext';

const ICONS = { CONGES_A_VALIDER: CalendarCheck, BROUILLONS_RH: FileText, ANOMALIES_POINTAGE: AlertTriangle, ECHEANCES_CONTRATS: AlertTriangle };

export default function OperationalQueueWidget({ loading, actions = [], className = '' }) {
    const { hasPermission } = useAuth();
    const items = actions.filter((item) => !item.requiredAuthority || hasPermission(item.requiredAuthority));
    const totalActions = items.reduce((sum, item) => sum + (Number(item.count) || 0), 0);

    if (!loading && items.length === 0) {
        return (
            <Card className={`flex flex-col ${className}`}>
                <CardHeader className="min-h-[56px] flex items-center justify-between">
                    <div className="flex items-center gap-2"><ShieldAlert size={16} className="text-[var(--color-brand-600)]" /><CardTitle>Priorités opérationnelles</CardTitle></div>
                    <Badge variant="success" size="sm">0 à suivre</Badge>
                </CardHeader>
                <CardBody className="flex-1 flex items-center justify-center">
                    <EmptyState compact icon={ClipboardCheck} title="Aucune priorité opérationnelle" description="Les décisions et contrôles de votre périmètre sont à jour." />
                </CardBody>
            </Card>
        );
    }

    return (
        <Card className={`flex flex-col ${className}`}>
            <CardHeader className="min-h-[56px] flex items-center justify-between">
                <div className="flex items-center gap-2">
                    <ShieldAlert size={16} className="text-[var(--color-brand-600)]" />
                    <CardTitle>Priorités opérationnelles</CardTitle>
                </div>
                <Badge variant="warning" size="sm">{loading ? '…' : `${totalActions} à traiter`}</Badge>
            </CardHeader>
            <CardBody className="flex-1">
                {loading && <SkeletonListRows count={3} />}
                {!loading && items.map((item) => {
                    const Icon = ICONS[item.code] || ShieldAlert;
                    const danger = item.severity === 'DANGER';
                    return <Link key={item.code} to={item.destination} className="group flex items-center gap-3 border-b last:border-b-0 border-[var(--color-border-subtle)] py-3 first:pt-0 last:pb-0 hover:bg-[var(--color-bg-subtle)]">
                        <span className={`p-2 ${danger ? 'bg-[var(--color-danger-500)]/10 text-[var(--color-danger-600)]' : 'bg-[var(--color-warning-500)]/10 text-[var(--color-warning-600)]'}`}><Icon size={16} /></span>
                        <span className="min-w-0 flex-1"><span className="block text-xs font-bold text-[var(--color-fg)]">{item.label}</span><span className="block text-[11px] text-[var(--color-fg-muted)] mt-0.5">{item.description}</span></span>
                        <Badge variant={danger ? 'danger' : 'warning'} size="sm">{item.count}</Badge>
                        <ChevronRight size={14} className="text-[var(--color-fg-subtle)] group-hover:text-[var(--color-brand-600)]" />
                    </Link>;
                })}
            </CardBody>
        </Card>
    );
}
