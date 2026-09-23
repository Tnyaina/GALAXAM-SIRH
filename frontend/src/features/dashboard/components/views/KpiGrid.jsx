import { Users, ClipboardCheck, UserCheck, Clock } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import { SkeletonStatCard } from '@/components/ui/feedback/Skeleton';

export default function KpiGrid({ 
    loading, 
    kpis, 
    actionsCount = 0,
    absentsAujourdhuiCount = 0
}) {
    return (
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {loading ? (
                Array.from({ length: 4 }).map((_, i) => <SkeletonStatCard key={i} />)
            ) : (
                <>
                    <StatCard
                        title="Effectif actif"
                        value={kpis?.totalEffectifActif ?? '—'}
                        subtext={`${kpis?.nouvellesEmbauchesMois ?? 0} arrivée(s) ce mois`}
                        icon={<Users size={18} />}
                        color="brand"
                    />
                    <StatCard
                        title="Actions à traiter"
                        value={actionsCount}
                        subtext={actionsCount > 0 ? 'Ouvrir les priorités opérationnelles' : 'Aucune action en attente'}
                        icon={<ClipboardCheck size={18} />}
                        color={actionsCount > 0 ? 'warning' : 'brand'}
                    />
                    <StatCard
                        title="Absents ce jour"
                        value={absentsAujourdhuiCount}
                        subtext="Congés validés en cours"
                        icon={<UserCheck size={18} />}
                        color={absentsAujourdhuiCount > 0 ? 'info' : 'brand'}
                    />
                    <StatCard
                        title="Échéances à 30 jours"
                        value={kpis?.contratsExpirant30j ?? '—'}
                        subtext={`${kpis?.essaisEnCours ?? 0} période(s) d’essai en cours`}
                        icon={<Clock size={18} />}
                        color={kpis?.contratsExpirant30j > 0 ? 'warning' : 'brand'}
                    />
                </>
            )}
        </div>
    );
}
