import { Users } from 'lucide-react';
import Card, { CardBody } from '@/components/ui/Card';

export default function EmployeStatsCards({ totalEmployes = 0, totalActifs = 0, totalInactifs = 0 }) {
    return (
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card bordered shadow="sm">
                <CardBody className="p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-brand-50 dark:bg-brand-900/30 text-brand-500 flex items-center justify-center">
                        <Users size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Total</p>
                        <p className="text-2xl font-bold text-ink-800 dark:text-white">{totalEmployes}</p>
                    </div>
                </CardBody>
            </Card>
            <Card bordered shadow="sm">
                <CardBody className="p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-success-500/10 text-success-500 flex items-center justify-center">
                        <Users size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Actifs</p>
                        <p className="text-2xl font-bold text-success-500">{totalActifs}</p>
                    </div>
                </CardBody>
            </Card>
            <Card bordered shadow="sm">
                <CardBody className="p-5 flex items-center gap-4">
                    <div className="w-11 h-11 bg-danger-500/10 text-danger-500 flex items-center justify-center">
                        <Users size={20} />
                    </div>
                    <div>
                        <p className="text-xs font-bold uppercase tracking-widest text-ink-400">Inactifs</p>
                        <p className="text-2xl font-bold text-danger-500">{totalInactifs}</p>
                    </div>
                </CardBody>
            </Card>
        </div>
    );
}
