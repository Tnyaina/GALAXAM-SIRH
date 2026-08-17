import { Calendar, Clock, ArrowDownCircle, ShieldAlert } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';

export default function SoldeCongeWidget({ soldeData, consommationPool }) {
  if (!soldeData) return null;

  const soldeActuel = Number(soldeData.soldeActuel || 0);
  const totalAcquis = Number(soldeData.totalAcquis || 0);
  const totalPris = Number(soldeData.totalPris || 0);
  const totalBonification = Number(soldeData.totalBonification || 0);
  const totalExpire = Number(soldeData.totalExpire || 0);

  // Pool partagé permissions exceptionnelles (10j / an max)
  const poolJoursConsommes = consommationPool && consommationPool.length > 0
    ? Number(consommationPool[0].joursConsommes || 0)
    : 0;

  const getSoldeColor = () => {
    if (soldeActuel < 0) return 'danger';
    if (soldeActuel <= 3) return 'warning';
    return 'success';
  };

  const getSoldeLabel = () => {
    if (soldeActuel < 0) return 'Solde négatif (Anticipation)';
    if (soldeActuel <= 3) return 'Solde faible';
    return 'Jours disponibles';
  };

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6">
      <StatCard 
        title="Solde Actuel" 
        value={soldeActuel > 0 ? `+${soldeActuel}` : soldeActuel} 
        icon={<Calendar size={24} />} 
        color={getSoldeColor()} 
        changeLabel={getSoldeLabel()}
      />
      
      <StatCard 
        title="Droits Acquis" 
        value={`+${totalAcquis + totalBonification}`} 
        icon={<Clock size={24} />} 
        color="info" 
      />
      
      <StatCard 
        title="Jours Posés" 
        value={totalPris} 
        icon={<ArrowDownCircle size={24} />} 
        color="brand" 
      />
      
      <StatCard 
        title="Évènements Familiaux" 
        value={`${poolJoursConsommes} / 10`} 
        icon={<ShieldAlert size={24} />} 
        color={poolJoursConsommes >= 10 ? 'danger' : poolJoursConsommes >= 7 ? 'warning' : 'success'} 
        changeLabel="Jours consommés"
      />
    </div>
  );
}
