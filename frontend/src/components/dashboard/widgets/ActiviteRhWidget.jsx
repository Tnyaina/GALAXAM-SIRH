import { memo } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import EmptyState from '@/components/ui/data-display/EmptyState';
import ExpandableList from './ExpandableList';
import { fmtRelative } from '../DashboardUtils';
import SkeletonListRows from './SkeletonListRows';

const ActiviteRow = memo(function ActiviteRow({ ev }) {
    const isValide = ev.statut === 'Valide et applique par la DAF' || ev.statut?.toLowerCase().includes('valid');
    return (
        <Link
            to={`/employes/${ev.idEmploye}?tab=carriere`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] group"
        >
            <div className={`w-7 h-7 rounded-none flex items-center justify-center shrink-0 text-white text-[10px] font-bold ${isValide ? 'bg-[var(--color-success-500)]' : 'bg-[var(--color-fg-subtle)]'}`}>
                {ev.nomPrenom?.charAt(0) || '?'}
            </div>
            <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{ev.nomPrenom}</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{ev.typeEvenement}</p>
            </div>
            <div className="text-right shrink-0">
                <Badge variant={isValide ? 'success' : 'default'} size="sm">
                    {isValide ? 'Validé' : 'Annulé'}
                </Badge>
                <p className="text-[10px] text-[var(--color-fg-subtle)] mt-0.5 font-data">{fmtRelative(ev.updatedAt)}</p>
            </div>
        </Link>
    );
});

export default function ActiviteRhWidget({ loading, activite = [] }) {
    return (
        <Card>
            <CardHeader>
                <div className="flex items-center gap-2">
                    <TrendingUp size={16} className="text-[var(--color-brand-500)]" />
                    <CardTitle>Activité Récente & Mouvements RH</CardTitle>
                </div>
            </CardHeader>
            <CardBody>
                {loading && <SkeletonListRows count={3} />}
                {!loading && activite.length === 0 && (
                    <EmptyState description="Aucun mouvement RH récent." />
                )}
                {!loading && activite.length > 0 && (
                    <ExpandableList
                        items={activite}
                        initialCount={5}
                        renderItem={(ev, i) => <ActiviteRow key={i} ev={ev} />}
                    />
                )}
            </CardBody>
        </Card>
    );
}
