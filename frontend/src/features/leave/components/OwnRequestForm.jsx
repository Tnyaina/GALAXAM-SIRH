import { useCallback, useEffect, useState } from 'react';
import Alert from '@/components/ui/feedback/Alert';
import congeService from '@/features/leave/api/congeService';
import DemandeCongeModal from './DemandeCongeModal';

export default function OwnRequestForm({ referentiels: referentielsInitiaux, initialDemande, onClose, onSuccess }) {
  const [employeeId, setEmployeeId] = useState(null);
  const [referentiels, setReferentiels] = useState(referentielsInitiaux || null);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    try {
    const [self, refs] = await Promise.all([
      congeService.getMonDossier(),
      referentielsInitiaux || congeService.getAllReferentiels(),
    ]);
    setEmployeeId(self.idEmploye);
    setReferentiels(refs?.data || refs);
    } catch (err) {
      setError(err?.response?.data?.message || err.message || 'Impossible de préparer le formulaire.');
    }
  }, [referentielsInitiaux]);

  useEffect(() => { load(); }, [load]);

  if (error) return <div role="dialog" aria-modal="true" aria-labelledby="own-leave-error-title" className="fixed inset-0 z-50 grid place-items-center bg-black/20 p-4"><div className="ui-dialog-viewport w-full max-w-lg overflow-y-auto bg-[var(--color-surface)] p-5"><h2 id="own-leave-error-title" className="sr-only">Chargement du formulaire impossible</h2><Alert type="error" message={error} /><button className="mt-3 underline" onClick={onClose}>Fermer</button></div></div>;
  if (!employeeId || !referentiels) return <div role="status" className="fixed inset-0 z-50 grid place-items-center bg-black/20" aria-live="polite">Chargement du formulaire de demande…</div>;

  return <DemandeCongeModal
    isOpen
    idEmploye={initialDemande?.idEmploye || employeeId}
    initialDemande={initialDemande}
    employeeContext={{ nom: 'Mon dossier' }}
    referentiels={referentiels}
    onClose={onClose}
    onSuccess={onSuccess}
  />;
}
