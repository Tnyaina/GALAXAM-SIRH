import { Users, Briefcase, Calendar, UserCheck, Clock, FileText } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import { SkeletonStatCard } from '@/components/ui/feedback/Skeleton';

export default function KpiGrid({ 
    loading, 
    kpis, 
    demandesCongeAValiderCount = 0, 
    absentsAujourdhuiCount = 0 
}) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-6 gap-4">
            {loading ? (
                Array.from({ length: 6 }).map((_, i) => <SkeletonStatCard key={i} />)
            ) : (
                <>
                    <StatCard
                        title="Effectif actif"
                        value={kpis?.totalEffectifActif ?? '—'}
                        icon={<Users size={18} />}
                        color="brand"
                    />
                    <StatCard
                        title="Comptabilisé"
                        value={kpis?.effectifComptabilise ?? '—'}
                        icon={<Briefcase size={18} />}
                        color="brand"
                    />
                    <StatCard
                        title="Congés à valider"
                        value={demandesCongeAValiderCount}
                        icon={<Calendar size={18} />}
                        color={demandesCongeAValiderCount > 0 ? 'warning' : 'brand'}
                    />
                    <StatCard
                        title="Absents ce jour"
                        value={absentsAujourdhuiCount}
                        icon={<UserCheck size={18} />}
                        color={absentsAujourdhuiCount > 0 ? 'info' : 'brand'}
                    />
                    <StatCard
                        title="Expirations 30j"
                        value={kpis?.contratsExpirant30j ?? '—'}
                        icon={<Clock size={18} />}
                        color={kpis?.contratsExpirant30j > 0 ? 'warning' : 'brand'}
                    />
                    <StatCard
                        title="Brouillons DAF"
                        value={kpis?.brouillonsEnAttente ?? '—'}
                        icon={<FileText size={18} />}
                        color={kpis?.brouillonsEnAttente > 0 ? 'warning' : 'brand'}
                    />
                </>
            )}
        </div>
    );
}
