import { memo } from 'react';
import { Link } from 'react-router-dom';
import { TrendingUp } from 'lucide-react';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import Badge from '@/components/ui/data-display/Badge';
import { Avatar } from '@/components/ui/data-display/Avatar';
import EmptyState from '@/components/ui/data-display/EmptyState';
import ExpandableList from './ExpandableList';
import { fmtRelative } from '@/utils/dateUtils';
import SkeletonListRows from './SkeletonListRows';

const ActiviteRow = memo(function ActiviteRow({ ev }) {
    const statutCode = (ev.statutCode || '').toUpperCase();
    const isPlanifie = statutCode === 'PLANIFIE';
    const isValide = statutCode === 'ACTIF' || statutCode === 'VALIDE' || (ev.statutLibelle || ev.statut || '')?.toLowerCase().includes('valid');
    const isRejete = statutCode === 'ANNULE' || statutCode === 'REFUSE' || statutCode === 'REJETE' || (ev.statutLibelle || ev.statut || '')?.toLowerCase().includes('rejet');

    let badgeVariant = 'default';
    let statutText = 'En cours';

    if (isPlanifie) {
        badgeVariant = 'info';
        const dEffet = new Date(ev.dateEffet);
        const dEffetStr = !isNaN(dEffet.getTime()) ? dEffet.toLocaleDateString('fr-FR') : ev.dateEffet;
        statutText = `Planifié (${dEffetStr})`;
    } else if (isValide) {
        badgeVariant = 'success';
        statutText = 'Validé';
    } else if (isRejete) {
        badgeVariant = 'danger';
        statutText = 'Rejeté';
    }

    const targetTab = ev.sourceType === 'CONGE' ? 'conges' : 'carriere';
    return (
        <Link
            to={`/employes/${ev.idEmploye}?tab=${targetTab}`}
            className="flex items-center gap-3 py-3 px-1 hover:bg-[var(--color-bg-subtle)] group"
        >
            <Avatar name={ev.nomPrenom} size="sm" shape="square" />
            <div className="flex-1 min-w-0">
                <p className="text-xs font-semibold text-[var(--color-fg)] truncate">{ev.nomPrenom}</p>
                <p className="text-[11px] text-[var(--color-fg-muted)] truncate">{ev.typeEvenement}</p>
            </div>
            <div className="text-right shrink-0">
                <Badge variant={badgeVariant} size="sm">
                    {statutText}
                </Badge>
                <p className="text-[10px] text-[var(--color-fg-subtle)] mt-0.5 font-data">{fmtRelative(ev.dateAction || ev.updatedAt)}</p>
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
                    <EmptyState compact description="Aucun mouvement RH récent." />
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
