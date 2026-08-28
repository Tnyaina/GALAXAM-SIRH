import { Search, Plus, RefreshCw, FileText, Clock, CheckCircle, XCircle, Ban } from 'lucide-react';
import { Card, CardBody } from '@/components/ui/data-display/Card';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';

const STATUTS = [
  { key: 'ALL',        label: 'Toutes',     icon: FileText    },
  { key: 'EN_ATTENTE', label: 'En attente', icon: Clock       },
  { key: 'VALIDE',     label: 'Validées',   icon: CheckCircle },
  { key: 'REFUSE',     label: 'Refusées',   icon: XCircle     },
  { key: 'ANNULE',     label: 'Annulées',   icon: Ban         },
];

export default function ValidationFilters({
  activeStatut,
  setActiveStatut,
  selectedTypeConge,
  setSelectedTypeConge,
  referentiels,
  displayTotal,
  searchRaw,
  setSearchRaw,
  searchTerm,
  loading,
  onRefresh,
  onNewDemande
}) {
  const typesConges = referentiels?.typesConges || referentiels?.types || [];

  return (
    <Card>
      <CardBody className="p-3">
        <div className="flex flex-col sm:flex-row items-start sm:items-center gap-3">
          
          {/* Filtres-pilules par statut */}
          <div className="flex flex-wrap items-center gap-1.5 flex-1 min-w-0">
            {STATUTS.map((s) => {
              const count    = activeStatut === s.key ? displayTotal : '-';
              const isActive = activeStatut === s.key;
              const isUrgent = s.key === 'EN_ATTENTE' && isActive && displayTotal > 0;
              const Icon     = s.icon;
              return (
                <button
                  key={s.key}
                  onClick={() => setActiveStatut(s.key)}
                  className={`inline-flex items-center gap-1.5 px-2.5 py-1.5 text-xs font-semibold border focus:outline-none focus-visible:ring-2 focus-visible:ring-[var(--color-brand-400)] ${
                    isActive
                      ? 'bg-[var(--color-brand-500)] text-white border-[var(--color-brand-500)] shadow-sm'
                      : 'bg-[var(--color-surface)] text-[var(--color-fg-muted)] border-[var(--color-border-subtle)] hover:border-[var(--color-brand-400)] hover:text-[var(--color-brand-500)]'
                  }`}
                >
                  <Icon size={12} />
                  {s.label}
                  <span className={`inline-flex items-center justify-center min-w-[18px] h-[18px] rounded-none text-[10px] font-bold px-1 ${
                    isActive    ? 'bg-white/25 text-white'
                    : isUrgent  ? 'bg-warning-500 text-white'
                                : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)]'
                  }`}>
                    {count}
                  </span>
                </button>
              );
            })}
          </div>

          {/* Filtre Type de Congé + Recherche + Actions */}
          <div className="flex flex-wrap items-center gap-2 w-full sm:w-auto shrink-0">
            {/* Sélecteur dynamique Type de Congé */}
            {typesConges.length > 0 && (
              <div className="w-full sm:w-44">
                <select
                  value={selectedTypeConge || ''}
                  onChange={(e) => setSelectedTypeConge(e.target.value)}
                  className="w-full h-8 text-xs bg-[var(--color-surface)] border border-[var(--color-border-subtle)] text-[var(--color-fg)] rounded-none px-2 py-1 focus:outline-none focus:border-[var(--color-brand-500)] focus:ring-1 focus:ring-[var(--color-brand-500)] cursor-pointer"
                >
                  <option value="">Tous les types</option>
                  {typesConges.map((t) => (
                    <option key={t.idTypeConge} value={t.idTypeConge}>
                      {t.libelleTypeConge || t.codeTypeConge}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="relative w-full sm:w-52">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--color-fg-muted)] pointer-events-none" />
              <Input
                placeholder="Rechercher employé..."
                value={searchRaw}
                onChange={(e) => setSearchRaw(e.target.value)}
                className="pl-9 h-8 text-xs"
              />
              {/* Indicateur visuel de debounce en cours */}
              {searchRaw !== searchTerm && (
                <span className="absolute right-2.5 top-1/2 -translate-y-1/2 w-1.5 h-1.5 bg-[var(--color-brand-400)] rounded-none" title="Recherche en cours..." />
              )}
            </div>
            <Button variant="outline" size="sm" iconLeft={<RefreshCw size={15} />} loading={loading} onClick={onRefresh}>
                Rafraîchir
            </Button>
            <Button variant="primary" size="sm" iconLeft={<Plus size={15} />} onClick={onNewDemande}>
              Nouvelle demande
            </Button>
          </div>
        </div>
      </CardBody>
    </Card>
  );
}
