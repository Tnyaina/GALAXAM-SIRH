import { Search, Plus, RefreshCw, Clock } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import SearchSelect from '@/components/ui/forms/SearchSelect';
import Button from '@/components/ui/forms/Button';

export default function ValidationFilters({
  activeStatut,
  setActiveStatut,
  selectedTypeConge,
  setSelectedTypeConge,
  referentiels,
  displayTotal,
  searchRaw,
  setSearchRaw,
  loading,
  onRefresh,
  onNewDemande
}) {
  const typesConges = referentiels?.typesConge || referentiels?.typesConges || referentiels?.types || [];
  const DEFAULT_STATUTS = [
    { code: 'EN_ATTENTE', libelle: 'En attente de validation' },
    { code: 'VALIDE',     libelle: 'Validées'                 },
    { code: 'REFUSE',     libelle: 'Refusées'                 },
    { code: 'ANNULE',     libelle: 'Annulées'                 },
    { code: 'BROUILLON',  libelle: 'Brouillon'                },
  ];
  const sanitizeStatutLibelle = (code, rawLibelle) => {
    if (!rawLibelle) return code === 'ALL' ? 'Toutes' : code;
    return rawLibelle
      .replace(/\s*\(saisie RH en cours\)/gi, '')
      .replace(/\s+DAF\b/gi, '')
      .replace(/\s+RH\b/gi, '')
      .trim();
  };

  const statuts = (referentiels?.statutsDemande && referentiels.statutsDemande.length > 0)
    ? referentiels.statutsDemande
    : DEFAULT_STATUTS;
  const statusOptions = [
    { code: 'ALL', libelle: 'Toutes' },
    ...statuts.slice().sort((a, b) => Number(b.code === 'EN_ATTENTE') - Number(a.code === 'EN_ATTENTE'))
  ];

  return (
    <div className="bg-[var(--color-surface)] border border-[var(--color-border-subtle)] shadow-xs p-4 space-y-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-bold text-[var(--color-fg)]">Demandes de congés et d'absences</h2>
          <p className="text-xs text-[var(--color-fg-muted)] mt-0.5">
            {displayTotal} demande{displayTotal > 1 ? 's' : ''} dans la sélection · ouvrez une demande pour consulter le contexte et décider.
          </p>
        </div>
        {onNewDemande && <Button variant="primary" size="sm" iconLeft={<Plus size={14} />} onClick={onNewDemande}>
          Nouvelle demande
        </Button>}
      </div>

      <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Filtrer les demandes par statut">
        {statusOptions.map((status) => (
          <Button key={status.code} type="button" size="xs"
            variant={activeStatut === status.code ? 'primary' : 'outline'}
            iconLeft={status.code === 'EN_ATTENTE' ? <Clock size={12} /> : undefined}
            onClick={() => setActiveStatut(status.code)} aria-pressed={activeStatut === status.code}>
            {sanitizeStatutLibelle(status.code, status.libelle)}
          </Button>
        ))}
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-[minmax(0,1fr)_minmax(0,260px)_auto] gap-2 items-center border-t border-[var(--color-border-subtle)] pt-3">
        <Input placeholder="Rechercher un collaborateur…" value={searchRaw}
          onChange={(e) => setSearchRaw(e.target.value)} iconLeft={<Search size={14} />}
          aria-label="Rechercher un collaborateur" />
        <SearchSelect
          value={selectedTypeConge ? String(selectedTypeConge) : ''}
          onChange={(val) => setSelectedTypeConge(val || '')}
          placeholder="Tous les types d'absence"
          options={typesConges
            .filter((t) => t.estActif !== false && t.actif !== false)
            .map((t) => {
              const code = t.code || t.codeTypeConge;
              const libelle = t.libelle || t.libelleTypeConge;
              return {
                value: String(t.idTypeConge ?? t.id),
                label: [code, libelle].filter(Boolean).join(' — ') || String(t.idTypeConge ?? t.id),
                sublabel: t.estPaye ? 'Rémunéré' : (t.estPaye === false ? 'Sans solde' : undefined)
              };
            })}
          className="w-full"
        />
        <Button variant="outline" size="sm" iconLeft={<RefreshCw size={13} />} loading={loading} onClick={onRefresh}>
          Actualiser
        </Button>
      </div>
    </div>
  );
}
