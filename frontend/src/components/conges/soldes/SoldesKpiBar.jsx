import { Clock, Users, CheckCircle, CalendarDays } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';

export default function SoldesKpiBar({
  kpiEnAttente,
  kpiAbsentsAuj,
  kpiValideCeMois,
  kpiTotalDemandes
}) {
  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
      <StatCard
        title="En attente de validation"
        value={kpiEnAttente}
        icon={<Clock size={20} />}
        color="warning"
        subtext="À traiter par le manager / RH"
        borderTop={false}
      />
      <StatCard
        title="Absents aujourd'hui"
        value={kpiAbsentsAuj}
        icon={<Users size={20} />}
        color="danger"
        subtext="Salariés actuellement en congé"
        borderTop={false}
      />
      <StatCard
        title="Validées ce mois"
        value={kpiValideCeMois}
        icon={<CheckCircle size={20} />}
        color="success"
        subtext="Demandes approuvées sur la période"
        borderTop={false}
      />
      <StatCard
        title="Total demandes"
        value={kpiTotalDemandes}
        icon={<CalendarDays size={20} />}
        color="brand"
        subtext="Toutes demandes confondues sur l'année"
        borderTop={false}
      />
    </div>
  );
}
