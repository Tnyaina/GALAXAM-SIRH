import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { CheckCircle2, Database, Globe2, Power, RefreshCw } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card, CardBody, CardHeader, CardTitle } from '@/components/ui/data-display/Card';
import ConnectionSwitchConfirm from './ConnectionSwitchConfirm';

const normalizeAddress = (value) => value.trim().replace(/\/+$/, '');

export default function ConnectionSettingsPage() {
  const [status, setStatus] = useState(null);
  const [url, setUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [connected, setConnected] = useState(null);
  const [testedAddress, setTestedAddress] = useState('');
  const [pendingSwitch, setPendingSwitch] = useState(null);

  const reload = () => invoke('get_desktop_status').then(async (value) => {
    setStatus(value);
    if (value.connection_target) {
      if (value.mode === 'remote') setUrl(value.connection_target);
      try { await invoke('test_remote_api', { url: value.connection_target }); setConnected(true); }
      catch { setConnected(false); }
    } else setConnected(false);
  }).catch((reason) => setError(String(reason)));
  useEffect(() => { reload(); }, []);

  const test = async () => {
    setBusy(true); setError(''); setMessage(''); setTestedAddress('');
    try {
      await invoke('test_remote_api', { url });
      setTestedAddress(normalizeAddress(url));
      setMessage('Connexion vérifiée : l’API GALAXAM répond.');
    }
    catch (reason) { setError(String(reason)); }
    finally { setBusy(false); }
  };

  const useRemote = async () => {
    setBusy(true); setError(''); setMessage('Changement de connexion en cours…');
    try {
      localStorage.removeItem('galaxam-user');
      sessionStorage.clear();
      await invoke('apply_remote_mode', { url });
    }
    catch (reason) { setError(String(reason)); setMessage(''); setBusy(false); setPendingSwitch(null); }
  };

  const useLocal = async () => {
    setBusy(true); setError(''); setMessage('Retour aux données locales en cours…');
    try {
      localStorage.removeItem('galaxam-user');
      sessionStorage.clear();
      await invoke('switch_to_local_mode');
    }
    catch (reason) { setError(String(reason)); setMessage(''); setBusy(false); setPendingSwitch(null); }
  };

  const stopLocalServices = async () => {
    if (!window.confirm('Arrêter Spring Boot et PostgreSQL locaux ? Les tâches planifiées seront suspendues jusqu’au prochain démarrage de GALAXAM. Vos données seront conservées.')) return;
    setBusy(true); setError(''); setMessage('');
    try {
      await invoke('stop_local_services');
      setConnected(false);
      setMessage('Les services locaux sont arrêtés. Ils redémarreront à la prochaine ouverture de GALAXAM.');
    } catch (reason) { setError(String(reason)); }
    finally { setBusy(false); }
  };

  if (!status) return <div className="p-6" aria-live="polite">Chargement des paramètres…</div>;
  return <div className="mx-auto max-w-4xl space-y-5 p-4 sm:p-6" aria-labelledby="connection-settings-title">
    <div><h2 id="connection-settings-title" className="text-xl font-bold">Connexion et données</h2><p className="mt-1 text-sm text-[var(--color-fg-muted)]">Choisissez l’installation à laquelle cette application se connecte.</p></div>
    {error && <p role="alert" className="border border-[var(--color-danger-500)] p-3 text-sm">{error}</p>}
    {message && <p role="status" className="border border-[var(--color-success-500)] p-3 text-sm">{message}</p>}
    <Card><CardHeader><CardTitle>Connexion actuelle</CardTitle></CardHeader><CardBody className="flex flex-wrap items-center justify-between gap-4"><div className="flex items-center gap-3">{status.mode === 'remote' ? <Globe2/> : <Database/>}<div><p className="font-semibold">{status.mode === 'remote' ? 'Serveur distant' : 'Base locale'}</p><p className="text-sm text-[var(--color-fg-muted)]">{status.mode === 'remote' ? status.connection_target : 'Données conservées sur cet ordinateur'}</p></div></div><span className="inline-flex items-center gap-2 text-sm" role="status">{connected === null ? 'Vérification…' : connected ? <><CheckCircle2 className="text-[var(--color-success-600)]"/>API disponible</> : 'API indisponible'}</span></CardBody></Card>
    {status.mode === 'local' && <Card><CardHeader><CardTitle>Services locaux en arrière-plan</CardTitle></CardHeader><CardBody className="flex flex-wrap items-center justify-between gap-4"><p className="max-w-xl text-sm text-[var(--color-fg-muted)]">Spring Boot et PostgreSQL restent actifs quand vous fermez la fenêtre, afin que les traitements planifiés puissent continuer.</p><Button variant="danger" onClick={stopLocalServices} disabled={busy}><Power size={15}/>Arrêter les services locaux</Button></CardBody></Card>}
    <Card><CardHeader><CardTitle>Se connecter à un serveur distant</CardTitle></CardHeader><CardBody className="space-y-3"><p className="text-sm text-[var(--color-fg-muted)]">Saisissez l’adresse HTTPS de l’API GALAXAM. Aucun identifiant de base de données ne vous sera demandé.</p><Input label="Adresse de l’API" type="url" value={url} onChange={(event) => { setUrl(event.target.value); setTestedAddress(''); setMessage(''); setError(''); }} placeholder="https://sirh.exemple.mg"/><div className="flex flex-wrap gap-2"><Button variant="secondary" onClick={test} disabled={busy}>{busy ? 'Vérification…' : 'Tester la connexion'}</Button><Button onClick={() => setPendingSwitch({ mode: 'remote', address: url })} disabled={busy || !url.trim() || testedAddress !== normalizeAddress(url)}>Utiliser ce serveur</Button></div><p className="text-xs text-[var(--color-fg-muted)]">Testez d’abord l’adresse. Après confirmation, GALAXAM fermera la session, appliquera le changement et redémarrera.</p></CardBody></Card>
    {status.mode === 'remote' && <Card><CardHeader><CardTitle>Revenir aux données locales</CardTitle></CardHeader><CardBody className="flex flex-wrap items-center justify-between gap-4"><p className="max-w-xl text-sm text-[var(--color-fg-muted)]">La base locale reste séparée et est conservée sur cet ordinateur. Seule une installation locale déjà existante et vérifiable peut être réutilisée.</p><Button variant="secondary" onClick={() => setPendingSwitch({ mode: 'local' })} disabled={busy || !status.local_available}><RefreshCw size={15}/>Utiliser la base locale</Button></CardBody></Card>}
    {pendingSwitch && <ConnectionSwitchConfirm target={pendingSwitch} busy={busy} onCancel={() => setPendingSwitch(null)} onConfirm={pendingSwitch.mode === 'remote' ? useRemote : useLocal} />}
  </div>;
}
