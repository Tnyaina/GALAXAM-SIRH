import { Search, Plus, RefreshCw, FileText, Clock, CheckCircle, XCircle, Ban } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import Select from '@/components/ui/forms/Select';
import Button from '@/components/ui/forms/Button';

const STATUTS = [
  { key: 'EN_ATTENTE', label: 'En attente', icon: Clock       },
  { key: 'VALIDE',     label: 'Validées',   icon: CheckCircle },
  { key: 'REFUSE',     label: 'Refusées',   icon: XCircle     },
  { key: 'ANNULE',     label: 'Annulées',   icon: Ban         },
  { key: 'ALL',        label: 'Toutes',     icon: FileText    },
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
    <div className="bg-[var(--color-surface)] border border-[var(--color-border)] shadow-xs p-4 sm:p-5 space-y-3.5">
      <div className="flex flex-col lg:flex-row items-start lg:items-center justify-between gap-4">
        
        {/* Pilules de filtrage rapide par statut */}
        <div className="flex flex-wrap items-center gap-2 flex-1 min-w-0">
          {STATUTS.map((s) => {
            const count    = activeStatut === s.key ? displayTotal : null;
            const isActive = activeStatut === s.key;
            const isUrgent = s.key === 'EN_ATTENTE' && count > 0;
            const Icon     = s.icon;

            return (
              <button
                key={s.key}
                type="button"
                onClick={() => setActiveStatut(s.key)}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold transition-all cursor-pointer rounded-none ${
                  isActive
                    ? 'bg-[var(--color-brand-500)] text-white shadow-xs'
                    : 'bg-[var(--color-bg-subtle)] text-[var(--color-fg-muted)] hover:bg-[var(--color-surface-hover)] hover:text-[var(--color-fg)] border border-[var(--color-border-subtle)]'
                }`}
              >
                <Icon size={13} className={isUrgent ? 'text-[var(--color-warning-400)]' : ''} />
                <span>{s.label}</span>
                {count !== null && (
                  <span className={`inline-flex items-center justify-center min-w-[18px] h-[18px] text-[10px] font-bold px-1.5 ml-0.5 ${
                    isActive
                      ? 'bg-white/20 text-white'
                      : isUrgent
                        ? 'bg-[var(--color-warning-500)] text-white'
                        : 'bg-[var(--color-bg)] text-[var(--color-fg-muted)]'
                  }`}>
                    {count}
                  </span>
                )}
              </button>
            );
          })}
        </div>

        {/* Filtres secondaires : Type, Recherche, Rafraîchir, Nouvelle demande */}
        <div className="flex flex-wrap items-center gap-2.5 w-full lg:w-auto shrink-0">
          {/* Sélecteur dynamique Type de Congé */}
          {typesConges.length > 0 && (
            <div className="w-full sm:w-48">
              <Select
                value={selectedTypeConge || ''}
                onChange={(val) => setSelectedTypeConge(val)}
                placeholder="Tous les types d'absence"
                options={typesConges
                  .filter((t) => t.estActif !== false && t.actif !== false)
                  .map((t) => ({
                    value: t.idTypeConge,
                    label: t.libelleTypeConge || t.codeTypeConge
                  }))
                }
              />
            </div>
          )}

          <div className="w-full sm:w-52">
            <Input
              placeholder="Rechercher collaborateur…"
              value={searchRaw}
              onChange={(e) => setSearchRaw(e.target.value)}
              icon={<Search size={14} />}
            />
          </div>

          <Button
            variant="outline"
            size="xs"
            iconLeft={<RefreshCw size={13} />}
            loading={loading}
            onClick={onRefresh}
            className="h-8 text-xs font-semibold"
          >
            Rafraîchir
          </Button>

          {onNewDemande && (
            <Button
              variant="primary"
              size="xs"
              iconLeft={<Plus size={14} />}
              onClick={onNewDemande}
              className="h-8 text-xs font-semibold"
            >
              Nouvelle demande
            </Button>
          )}
        </div>
      </div>
    </div>
  );
}
