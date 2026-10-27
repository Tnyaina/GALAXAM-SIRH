import { useCallback, useEffect, useState } from 'react';
import Alert from '@/components/ui/feedback/Alert';
import Button from '@/components/ui/forms/Button';
import EmptyState from '@/components/ui/data-display/EmptyState';
import Loading from '@/components/ui/feedback/Loading';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import { canEditLeaveDemand } from '@/features/leave/utils/leaveDemandActions';

export default function MesDemandesPanel({ onNewRequest, onEdit, refreshKey = 0 }) {
  const [page, setPage] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPage(await congeService.getMesDemandes({ page: 1, size: 50 }));
    } catch (e) {
      setError(getErrorMessage(e, 'Impossible de charger vos demandes de congé.'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load, refreshKey]);

  return (
    <section className="space-y-4" aria-label="Mes demandes de congé">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-[var(--color-fg)]">Mes demandes de congé</h2>
          <p className="text-sm text-[var(--color-fg-muted)]">Consultez vos demandes et leur statut.</p>
        </div>
        <Button onClick={onNewRequest}>Nouvelle demande</Button>
      </div>
      {error && <Alert type="error" message={error} actionLabel="Réessayer" onAction={load} />}
      {loading ? <Loading label="Chargement de vos demandes…" /> : null}
      {!loading && !error && (page?.content || []).length === 0 && (
        <EmptyState title="Aucune demande" description="Vos demandes de congé apparaîtront ici." />
      )}
      {!loading && !error && (page?.content || []).length > 0 && (
        <div className="overflow-x-auto border border-[var(--color-border-subtle)] bg-[var(--color-surface)]">
          <table className="w-full text-left text-sm">
            <thead className="bg-[var(--color-bg-subtle)] text-xs uppercase text-[var(--color-fg-muted)]">
              <tr><th className="px-3 py-2">Type</th><th className="px-3 py-2">Période</th><th className="px-3 py-2">Jours</th><th className="px-3 py-2">Statut</th><th className="px-3 py-2">Actions</th></tr>
            </thead>
            <tbody>
              {page.content.map(demand => (
                <tr key={demand.idDemandeConge} className="border-t border-[var(--color-border-subtle)]">
                  <td className="px-3 py-2">{demand.libelleTypeConge || demand.codeTypeCongeConge || demand.codeTypeConge}</td>
                  <td className="px-3 py-2">{demand.dateDebut?.slice?.(0, 10) || demand.dateDebut} – {demand.dateFin?.slice?.(0, 10) || demand.dateFin}</td>
                  <td className="px-3 py-2">{demand.nbJoursDecomptes ?? demand.nbJours}</td>
                  <td className="px-3 py-2">{demand.libelleStatutDemande || demand.libelleStatutDemandeConge || demand.codeStatutDemande}</td>
                  <td className="px-3 py-2">
                    {canEditLeaveDemand(demand) && onEdit && (
                      <Button variant="outline" size="sm" onClick={() => onEdit(demand)}>Modifier</Button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div className="sr-only" aria-live="polite">{page?.totalElements || 0} demandes.</div>
    </section>
  );
}
