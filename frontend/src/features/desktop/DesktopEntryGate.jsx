import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { LoaderCircle } from 'lucide-react';
import FirstRunWizard from './FirstRunWizard';

export default function DesktopEntryGate({ children }) {
  const isDesktop = typeof window !== 'undefined' && Boolean(window.__TAURI_INTERNALS__);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState('');
  const [progress, setProgress] = useState('Démarrage des services locaux…');
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!isDesktop) return;
    let active = true;
    let disposeStartup;
    let disposeProgress;
    const reload = () => invoke('get_desktop_status').then((value) => {
      if (active) { setStatus(value); setError(''); }
    }).catch((reason) => { if (active) setError(String(reason)); });
    Promise.all([
      listen('desktop-startup-complete', reload),
      listen('setup-progress', (event) => {
        if (!active) return;
        if (event.payload === 'service-copy') setProgress('Préparation du service local…');
        if (event.payload === 'api-wait') setProgress('Attente de l’API locale…');
      }),
    ]).then(([startup, progressListener]) => {
      if (!active) { startup(); progressListener(); return; }
      disposeStartup = startup;
      disposeProgress = progressListener;
      reload();
    }).catch((reason) => { if (active) setError(String(reason)); });
    return () => { active = false; disposeStartup?.(); disposeProgress?.(); };
  }, [isDesktop]);

  useEffect(() => {
    if (!status?.starting) return undefined;
    const timer = window.setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
    return () => window.clearInterval(timer);
  }, [status?.starting]);

  if (!isDesktop) return children;
  if (error) return <main className="min-h-screen grid place-items-center p-6"><section role="alert" className="max-w-xl border border-[var(--color-danger-500)] bg-[var(--color-surface)] p-6"><h1 className="text-lg font-bold">GALAXAM ne peut pas démarrer</h1><p className="mt-2 text-sm">{error}</p></section></main>;
  if (!status) return <main className="min-h-screen grid place-items-center" aria-live="polite">Chargement de GALAXAM…</main>;
  if (status.starting) return <main className="min-h-screen grid place-items-center p-6"><div className="flex max-w-md items-center gap-3"><LoaderCircle className="shrink-0 animate-spin" aria-hidden="true"/><div><h1 className="font-semibold">Préparation de GALAXAM</h1><p className="text-sm text-[var(--color-fg-muted)]" role="status">{progress}</p><p className="mt-1 text-xs text-[var(--color-fg-muted)]" aria-hidden="true">Temps écoulé : {Math.floor(elapsed / 60)} min {String(elapsed % 60).padStart(2, '0')} s</p></div></div></main>;
  if (status.setup_required || status.mode === 'setup') return <FirstRunWizard initialStatus={status} onComplete={() => invoke('get_desktop_status').then(setStatus)} />;
  return children;
}
