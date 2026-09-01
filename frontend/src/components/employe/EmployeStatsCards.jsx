import { Users, UserCheck, UserX } from 'lucide-react';
import Card, { CardBody } from '@/components/ui/data-display/Card';

export default function EmployeStatsCards({ totalEmployes = 0, totalActifs = 0, totalInactifs = 0 }) {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
            <Card bordered shadow="sm" className="hover:border-[var(--color-brand-500)]/30 transition-colors">
                <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                    <div className="w-9 h-9 bg-[var(--color-brand-500)]/10 text-[var(--color-brand-600)] flex items-center justify-center shrink-0">
                        <Users size={18} />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-fg-muted)]">Total Collaborateurs</p>
                        <p className="text-xl font-bold font-data text-[var(--color-fg)]">{totalEmployes}</p>
                    </div>
                </CardBody>
            </Card>
            <Card bordered shadow="sm" className="hover:border-success-500/30 transition-colors">
                <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                    <div className="w-9 h-9 bg-success-500/10 text-success-600 dark:text-success-400 flex items-center justify-center shrink-0">
                        <UserCheck size={18} />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-fg-muted)]">Comptes Actifs</p>
                        <p className="text-xl font-bold font-data text-success-600 dark:text-success-400">{totalActifs}</p>
                    </div>
                </CardBody>
            </Card>
            <Card bordered shadow="sm" className="hover:border-danger-500/30 transition-colors">
                <CardBody className="p-3.5 sm:p-4 flex items-center gap-3.5">
                    <div className="w-9 h-9 bg-danger-500/10 text-danger-600 dark:text-danger-400 flex items-center justify-center shrink-0">
                        <UserX size={18} />
                    </div>
                    <div className="min-w-0">
                        <p className="text-[10px] font-bold uppercase tracking-widest text-[var(--color-fg-muted)]">Comptes Inactifs</p>
                        <p className="text-xl font-bold font-data text-danger-600 dark:text-danger-400">{totalInactifs}</p>
                    </div>
                </CardBody>
            </Card>
        </div>
    );
}
