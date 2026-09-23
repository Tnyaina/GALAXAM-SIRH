import { memo } from 'react';
import { Users, UserCheck, UserX } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';

function EmployeStatsCards({
    totalEmployes = 0,
    totalActifs = 0,
    totalInactifs = 0,
    selectedStatus = 'ALL',
    onStatusSelect
}) {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <StatCard
                title="Total Collaborateurs"
                value={totalEmployes}
                subtext="Effectif global de l'entreprise"
                icon={<Users size={18} />}
                color="brand"
                active={selectedStatus === 'ALL'}
                onClick={onStatusSelect ? () => onStatusSelect('ALL') : undefined}
            />

            <StatCard
                title="Collaborateurs Actifs"
                value={totalActifs}
                subtext="En poste actuellement"
                icon={<UserCheck size={18} />}
                color="success"
                active={selectedStatus === 'ACTIVE'}
                onClick={onStatusSelect ? () => onStatusSelect('ACTIVE') : undefined}
            />

            <StatCard
                title="Comptes Inactifs"
                value={totalInactifs}
                subtext="Dossiers archivés ou sortis"
                icon={<UserX size={18} />}
                color="danger"
                active={selectedStatus === 'INACTIVE'}
                onClick={onStatusSelect ? () => onStatusSelect('INACTIVE') : undefined}
            />
        </div>
    );
}

export default memo(EmployeStatsCards);
