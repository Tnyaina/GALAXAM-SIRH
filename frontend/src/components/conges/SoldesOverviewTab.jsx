import { useState } from 'react';
import { Users, CheckCircle, Clock, CalendarDays, RefreshCw, ChevronLeft, ChevronRight } from 'lucide-react';
import StatCard from '@/components/ui/data-display/StatCard';
import { Card, CardHeader, CardTitle, CardBody } from '@/components/ui/data-display/Card';
import { Table, Thead, Th, Tbody, Tr, Td } from '@/components/ui/data-display/Table';
import EmptyState from '@/components/ui/data-display/EmpyState';
import { SkeletonCard, SkeletonTable } from '@/components/ui/feedback/Skeleton';
import Badge from '@/components/ui/data-display/Badge';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';

// Utility to format timestamp to readable date
const formatDate = (timestamp) => {
  if (!timestamp) return '-';
  const date = new Date(timestamp);
  return date.toLocaleDateString('fr-FR');
};

export default function SoldesOverviewTab({ loading, demandesGlobales = [], onReload }) {
  const [currentMonth, setCurrentMonth] = useState(new Date().toISOString().slice(0, 7)); // YYYY-MM
  
  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
          <SkeletonCard />
        </div>
        <SkeletonCard />
        <SkeletonTable rows={5} />
      </div>
    );
  }

  // Generate days of the month for Gantt
  const [yearStr, monthStr] = currentMonth.split('-');
  const year = parseInt(yearStr, 10);
  const month = parseInt(monthStr, 10);
  const daysInMonth = new Date(year, month, 0).getDate();
  const daysArray = Array.from({ length: daysInMonth }, (_, i) => i + 1);

  const handlePrevMonth = () => {
    let m = month - 1;
    let y = year;
    if (m < 1) { m = 12; y--; }
    setCurrentMonth(`${y}-${m.toString().padStart(2, '0')}`);
  };

  const handleNextMonth = () => {
    let m = month + 1;
    let y = year;
    if (m > 12) { m = 1; y++; }
    setCurrentMonth(`${y}-${m.toString().padStart(2, '0')}`);
  };

  // Compute Gantt Data based on the real endpoint
  const ganttData = (demandesGlobales || []).filter(d => {
    // Only display VALIDE or EN_ATTENTE requests on the Gantt (we can filter out REFUSE, ANNULE)
    if (d.codeStatutDemande === 'REFUSE' || d.codeStatutDemande === 'ANNULE') return false;

    const start = new Date(d.dateDebut);
    const end = new Date(d.dateFin);
    const monthStart = new Date(year, month - 1, 1);
    const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);

    // Keep if the period overlaps with the current month
    return start <= monthEnd && end >= monthStart;
  }).map(d => {
    const start = new Date(d.dateDebut);
    const end = new Date(d.dateFin);
    const monthStart = new Date(year, month - 1, 1);
    const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);

    const viewStart = start < monthStart ? 1 : start.getDate();
    const viewEnd = end > monthEnd ? daysInMonth : end.getDate();

    return {
      employe: `${d.prenom || ''} ${d.nom || ''}`.trim() || `Employé #${d.idEmploye}`,
      id: d.idDemandeConge,
      type: d.codeTypeConge || 'ANNUEL',
      startDay: viewStart,
      endDay: viewEnd,
      statut: d.codeStatutDemande,
      originalStart: d.dateDebut,
      originalEnd: d.dateFin,
      dureeJours: d.nbJoursDecomptes
    };
  });

  // Calculate KPIs
  const kpiEnAttente = (demandesGlobales || []).filter(d => d.codeStatutDemande === 'EN_ATTENTE').length;
  
  const kpiValideCeMois = (demandesGlobales || []).filter(d => {
    if (d.codeStatutDemande !== 'VALIDE') return false;
    const end = new Date(d.dateFin);
    const monthStart = new Date(year, month - 1, 1);
    const monthEnd = new Date(year, month - 1, daysInMonth, 23, 59, 59);
    // Count if it ended in this month
    return end >= monthStart && end <= monthEnd;
  }).length;

  const today = new Date();
  today.setHours(0,0,0,0);
  const kpiAbsentsAuj = (demandesGlobales || []).filter(d => {
    if (d.codeStatutDemande !== 'VALIDE') return false;
    const start = new Date(d.dateDebut);
    start.setHours(0,0,0,0);
    const end = new Date(d.dateFin);
    end.setHours(23,59,59,999);
    return today >= start && today <= end;
  }).length;

  // Since we don't have everyone's solde globally fetched yet, we can hide or mock "Solde Moyen".
  // Or display total demands processed.
  const kpiTotalDemandes = (demandesGlobales || []).length;

  return (
    <div className="space-y-6 ">
      {/* Barre de KPIs avec espacement ajuste */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 lg:gap-6 mb-2">
        <div className="rounded-none">
          <StatCard title="Demandes en attente" value={kpiEnAttente} icon={<Clock size={24} />} color="warning" />
        </div>
        <div className="rounded-none">
          <StatCard title="Absents aujourd'hui" value={kpiAbsentsAuj} icon={<Users size={24} />} color="danger" />
        </div>
        <div className="rounded-none">
          <StatCard title="Validees ce mois" value={kpiValideCeMois} icon={<CheckCircle size={24} />} color="success" />
        </div>
        <div className="rounded-none">
          <StatCard title="Total demandes" value={kpiTotalDemandes} icon={<CalendarDays size={24} />} color="info" />
        </div>
      </div>

      {/* Planning Gantt CSS */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between w-full">
            <CardTitle>Planning des Absences (Gantt)</CardTitle>
            <div className="flex items-center gap-4">
              <div className="flex items-center rounded-none border border-[var(--color-border)] shadow-sm bg-[var(--color-surface)] overflow-hidden">
                <Button variant="ghost" size="sm" iconLeft={<ChevronLeft size={18} />} onClick={handlePrevMonth} className="!px-2 !rounded-none !border-0" title="Mois précédent" />
                <div className="w-[1px] h-5 bg-[var(--color-border-subtle)]" />
                <Input 
                  type="month" 
                  value={currentMonth}
                  onChange={(e) => setCurrentMonth(e.target.value)}
                  className="w-36 !border-0 !shadow-none !bg-transparent text-center font-semibold focus:!ring-0 rounded-none h-8"
                />
                <div className="w-[1px] h-5 bg-[var(--color-border-subtle)]" />
                <Button variant="ghost" size="sm" iconLeft={<ChevronRight size={18} />} onClick={handleNextMonth} className="!px-2 !rounded-none !border-0" title="Mois suivant" />
              </div>
              <Button variant="outline" size="sm" iconLeft={<RefreshCw size={16}/>} onClick={onReload}>
                Actualiser
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardBody className="p-0 overflow-x-auto">
          {ganttData.length === 0 ? (
            <EmptyState title="Aucun congé prévu" description="Il n'y a pas d'absences prévues sur ce mois." />
          ) : (
            <div className="min-w-[800px] border-b border-[var(--color-border-subtle)] pb-4">
              <div className="flex border-b border-[var(--color-border-subtle)] bg-[var(--color-bg-subtle)]">
                <div className="w-48 shrink-0 p-3 text-xs font-bold uppercase text-[var(--color-fg-muted)] border-r border-[var(--color-border-subtle)]">
                  Employé
                </div>
                <div className="flex-1 flex">
                  {daysArray.map((day) => {
                    const dateObj = new Date(year, month - 1, day);
                    const dayOfWeek = dateObj.toLocaleDateString('fr-FR', { weekday: 'short' }).charAt(0).toUpperCase();
                    const isToday = dateObj.toDateString() === new Date().toDateString();
                    
                    return (
                      <div key={day} className={`flex-1 min-w-[24px] text-center p-1 text-[10px] font-data border-r border-[var(--color-border-subtle)] last:border-0 ${isToday ? 'bg-[var(--color-brand-100)] text-[var(--color-brand-700)] border-b-2 border-b-[var(--color-brand-500)]' : 'text-[var(--color-fg-subtle)]'}`}>
                        <div className={`font-bold ${isToday ? 'opacity-100' : 'opacity-50'}`}>{dayOfWeek}</div>
                        <div className={isToday ? 'font-bold' : ''}>{day}</div>
                      </div>
                    );
                  })}
                </div>
              </div>
              
              {ganttData.map((item, idx) => (
                <div key={idx} className="flex border-b border-[var(--color-border-subtle)] last:border-0 hover:bg-[var(--color-surface-hover)] ">
                  <div className="w-48 shrink-0 p-3 text-sm font-semibold text-[var(--color-fg)] border-r border-[var(--color-border-subtle)] truncate">
                    {item.employe}
                  </div>
                  <div className="flex-1 flex relative">
                    {daysArray.map((day) => {
                      const dateObj = new Date(year, month - 1, day);
                      const isWeekend = dateObj.getDay() === 0 || dateObj.getDay() === 6;
                      const isToday = dateObj.toDateString() === new Date().toDateString();
                      
                      return (
                        <div key={day} className={`flex-1 min-w-[24px] border-r border-[var(--color-border-subtle)] last:border-0 ${isWeekend ? 'bg-[var(--color-bg-subtle)]/50' : ''} ${isToday ? 'bg-[var(--color-brand-50)]/50 border-x-[var(--color-brand-200)] shadow-inner' : ''}`}>
                        </div>
                      );
                    })}
                    {/* Barre du Gantt superposée */}
                    <div 
                      className={`absolute top-1.5 bottom-1.5 rounded-none shadow-sm flex items-center px-2 overflow-hidden border  hover:brightness-110 cursor-help ${
                        item.statut === 'EN_ATTENTE' 
                          ? 'bg-warning-500 border-warning-600 text-white' 
                          : 'bg-[var(--color-brand-500)] border-[var(--color-brand-600)] text-white'
                      }`}
                      style={{
                        left: `${((item.startDay - 1) / daysInMonth) * 100}%`,
                        width: `${((item.endDay - item.startDay + 1) / daysInMonth) * 100}%`,
                        backgroundImage: item.statut === 'EN_ATTENTE' 
                          ? 'repeating-linear-gradient(45deg, rgba(255,255,255,0.15), rgba(255,255,255,0.15) 10px, transparent 10px, transparent 20px)' 
                          : 'none'
                      }}
                      title={`${item.type} - ${item.statut} (${item.dureeJours} jours)`}
                    >
                      {(item.endDay - item.startDay + 1) >= 2 && (
                        <span className="text-[10px] font-bold truncate drop-shadow-md">
                           {item.type} {item.statut === 'EN_ATTENTE' && '(En attente)'}
                        </span>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardBody>
      </Card>

      {/* Tableau Récapitulatif Consolide */}
      <Card>
        <CardHeader>
          <CardTitle>Dernières Absences (Mois Actuel)</CardTitle>
        </CardHeader>
        <CardBody className="p-0">
          {ganttData.length === 0 ? (
            <EmptyState title="Aucune donnée" description="Aucune absence validée ou en attente ce mois-ci." />
          ) : (
            <Table>
              <Thead>
                <Tr>
                  <Th>Employé</Th>
                  <Th>Type</Th>
                  <Th>Du</Th>
                  <Th>Au</Th>
                  <Th>Jours</Th>
                  <Th>Statut</Th>
                </Tr>
              </Thead>
              <Tbody>
                {ganttData.map((item, idx) => (
                  <Tr key={idx}>
                    <Td className="font-semibold text-[var(--color-fg)]">{item.employe}</Td>
                    <Td><Badge variant="brand">{item.type}</Badge></Td>
                    <Td className="font-data">{formatDate(item.originalStart)}</Td>
                    <Td className="font-data">{formatDate(item.originalEnd)}</Td>
                    <Td className="font-data">{item.dureeJours}</Td>
                    <Td>
                      <Badge variant={item.statut === 'VALIDE' ? 'success' : 'warning'} dot>
                        {item.statut}
                      </Badge>
                    </Td>
                  </Tr>
                ))}
              </Tbody>
            </Table>
          )}
        </CardBody>
      </Card>
    </div>
  );
}
