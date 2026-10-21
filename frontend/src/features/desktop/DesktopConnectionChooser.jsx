import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { Database, Globe2, RefreshCw, X } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import ConnectionSwitchConfirm from './ConnectionSwitchConfirm';

/** Public desktop-only selector: lets a user choose the data source before signing in. */
export default function DesktopConnectionChooser({ onClose }) {
  const [status, setStatus] = useState(null);
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [tested, setTested] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [pendingSwitch, setPendingSwitch] = useState(null);

  useEffect(() => {
    invoke('get_desktop_status').then((value) => {
      setStatus(value);
      if (value.mode === 'remote' && value.connection_target) setUrl(value.connection_target);
    }).catch((reason) => setError(String(reason)));
  }, []);

  useEffect(() => {
    const closeOnEscape = (event) => {
      if (event.key !== 'Escape' || busy) return;
      event.preventDefault();
      if (pendingSwitch) setPendingSwitch(null);
      else onClose();
    };
    window.addEventListener('keydown', closeOnEscape);
    return () => window.removeEventListener('keydown', closeOnEscape);
  }, [busy, onClose, pendingSwitch]);

  const testRemote = async () => {
    setBusy(true); setError(''); setMessage(''); setTested(false);
    try {
      await invoke('test_remote_api', { url });
      setTested(true);
      setMessage('L’API GALAXAM répond. Vous pouvez utiliser ce serveur.');
    } catch (reason) { setError(String(reason)); }
    finally { setBusy(false); }
  };

  const useRemote = async () => {
    setBusy(true); setError('');
    try { await invoke('apply_remote_mode', { url }); }
    catch (reason) { setError(String(reason)); setBusy(false); setPendingSwitch(null); }
  };

  const useLocal = async () => {
    if (!status?.local_available) return;
    setBusy(true); setError('');
    try { await invoke('switch_to_local_mode'); }
    catch (reason) { setError(String(reason)); setBusy(false); setPendingSwitch(null); }
  };

  return <div className="fixed inset-0 z-[100] grid place-items-center bg-black/45 p-4" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose(); }}>
    <section role="dialog" aria-modal="true" aria-labelledby="desktop-connection-title" className="w-full max-w-xl space-y-5 rounded-md border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-xl sm:p-7">
      <div className="flex items-start justify-between gap-4"><div><h2 id="desktop-connection-title" className="text-xl font-semibold">Choisir une connexion</h2><p className="mt-1 text-sm text-[var(--color-fg-muted)]">Choisissez les données à utiliser avant de vous connecter.</p></div><button type="button" onClick={onClose} aria-label="Fermer" autoFocus className="rounded p-2 hover:bg-[var(--color-bg-subtle)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]"><X size={18}/></button></div>
      {error && <p role="alert" className="border border-[var(--color-danger-500)] p-3 text-sm">{error}</p>}
      {!status ? <p aria-live="polite" className="text-sm">Chargement des connexions…</p> : <>
        <div className="rounded border border-[var(--color-border)] p-4"><div className="flex items-center gap-3"><span className="rounded-full bg-[var(--color-bg-subtle)] p-2">{status.mode === 'remote' ? <Globe2 size={18}/> : <Database size={18}/>}</span><div><p className="font-medium">Connexion actuelle : {status.mode === 'remote' ? 'serveur distant' : 'base locale'}</p><p className="text-sm text-[var(--color-fg-muted)]">{status.mode === 'remote' ? status.connection_target : 'Données enregistrées sur cet ordinateur'}</p></div></div></div>
        {status.mode === 'remote' && <div className="space-y-3 rounded border border-[var(--color-border)] p-4"><h3 className="font-semibold">Revenir aux données locales</h3><p className="text-sm text-[var(--color-fg-muted)]">La base locale est conservée séparément. Aucun transfert ni réinitialisation n’est effectué.</p><Button variant="secondary" onClick={() => setPendingSwitch({ mode: 'local' })} disabled={busy || !status.local_available}><RefreshCw size={15}/>Utiliser la base locale</Button></div>}
        <div className="space-y-3 rounded border border-[var(--color-border)] p-4"><h3 className="font-semibold">Utiliser un serveur distant</h3><p className="text-sm text-[var(--color-fg-muted)]">Saisissez l’adresse HTTPS de l’API GALAXAM. Aucun identifiant de base de données n’est demandé.</p><Input label="Adresse HTTPS de l’API" type="url" autoComplete="url" placeholder="https://sirh.exemple.mg" value={url} onChange={(event) => { setUrl(event.target.value); setTested(false); setMessage(''); }} /><div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={testRemote} disabled={busy || !url}>{busy ? 'Vérification…' : 'Tester la connexion'}</Button><Button onClick={() => setPendingSwitch({ mode: 'remote', address: url })} disabled={busy || !tested}>{busy ? 'Application…' : 'Utiliser ce serveur'}</Button></div>{message && <p role="status" className="text-sm text-[var(--color-success-600)]">{message}</p>}</div>
      </>}
      <p className="text-xs text-[var(--color-fg-muted)]">Changer de connexion ferme la session en cours. Vous devrez vous reconnecter au compte de la destination choisie. Les données des deux bases restent séparées.</p>
    </section>
    {pendingSwitch && <ConnectionSwitchConfirm target={pendingSwitch} busy={busy} onCancel={() => setPendingSwitch(null)} onConfirm={pendingSwitch.mode === 'remote' ? useRemote : useLocal} />}
  </div>;
}
