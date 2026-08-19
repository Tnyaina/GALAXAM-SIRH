import { Clock, Users, CheckCircle, CalendarDays } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';

export default function SoldesKpiBar({
  kpiEnAttente,
  kpiAbsentsAuj,
  kpiValideCeMois,
  kpiTotalDemandes
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-2">
      <div className="rounded-none">
        <StatCard title="Demandes en attente" value={kpiEnAttente} icon={<Clock size={24} />} color="warning" />
      </div>
      <div className="rounded-none">
        <StatCard title="Absents aujourd'hui" value={kpiAbsentsAuj} icon={<Users size={24} />} color="danger" />
      </div>
      <div className="rounded-none">
        <StatCard title="Validées ce mois" value={kpiValideCeMois} icon={<CheckCircle size={24} />} color="success" />
      </div>
      <div className="rounded-none">
        <StatCard title="Total demandes" value={kpiTotalDemandes} icon={<CalendarDays size={24} />} color="info" />
      </div>
    </div>
  );
}
