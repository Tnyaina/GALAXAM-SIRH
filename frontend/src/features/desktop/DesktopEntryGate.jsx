import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import FirstRunWizard from './FirstRunWizard';

export default function DesktopEntryGate({ children }) {
  const isDesktop = typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!isDesktop) return;
    invoke('get_desktop_status').then((value) => {
      console.info('[GALAXAM desktop] React status received', { mode: value.mode, setupRequired: value.setup_required });
      setStatus(value);
    }).catch((reason) => setError(String(reason)));
  }, [isDesktop]);

  if (!isDesktop) return children;
  if (error) return <main className="min-h-screen grid place-items-center p-6"><section role="alert" className="max-w-xl border border-[var(--color-danger-500)] bg-[var(--color-surface)] p-6"><h1 className="text-lg font-bold">GALAXAM ne peut pas démarrer</h1><p className="mt-2 text-sm">{error}</p></section></main>;
  if (!status) return <main className="min-h-screen grid place-items-center" aria-live="polite">Chargement de GALAXAM…</main>;
  if (status.setup_required || status.mode === 'setup') return <FirstRunWizard initialStatus={status} onComplete={() => invoke('get_desktop_status').then(setStatus)} />;
  return children;
}
