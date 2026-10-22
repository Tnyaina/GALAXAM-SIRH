import { useEffect, useState } from 'react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { Check, Database, Eye, EyeOff, Globe2, LoaderCircle, ShieldCheck } from 'lucide-react';
import Input from '@/components/ui/forms/Input';
import Button from '@/components/ui/forms/Button';
import { Card } from '@/components/ui/data-display/Card';
import logo from '@/assets/logo/galaxam-logo.png';

const setupMessages = {
  database: 'Vérification du stockage local…',
  cluster: 'Création du cluster PostgreSQL…',
  schema: 'Création de la base et des tables…',
  'database-check': 'Vérification de la base existante…',
  backend: 'Démarrage sécurisé de l’API locale…',
  'service-copy': 'Installation des composants du service local…',
  'api-wait': 'Attente du démarrage de l’API locale…',
};

export default function FirstRunWizard({ initialStatus, onComplete }) {
  const [mode, setMode] = useState(initialStatus.mode === 'local' ? 'local' : '');
  const [step, setStep] = useState(initialStatus.mode === 'local' && !initialStatus.startup_error ? 'admin' : 'welcome');
  const [progress, setProgress] = useState('');
  const [elapsed, setElapsed] = useState(0);
  const [apiUrl, setApiUrl] = useState('https://');
  const [remoteTested, setRemoteTested] = useState(false);
  const [account, setAccount] = useState({ identifiant: '', email: '', password: '', confirm: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(initialStatus.startup_error || '');

  useEffect(() => {
    let unlisten;
    let mounted = true;
    listen('setup-progress', (event) => setProgress(setupMessages[event.payload] || 'Préparation de GALAXAM…')).then((dispose) => { if (mounted) unlisten = dispose; else dispose(); });
    return () => { mounted = false; unlisten?.(); };
  }, []);

  useEffect(() => {
    if (step !== 'preparing') return undefined;
    const timer = window.setInterval(() => setElapsed((seconds) => seconds + 1), 1000);
    return () => window.clearInterval(timer);
  }, [step]);

  const configureLocal = async () => {
    setBusy(true); setError(''); setElapsed(0); setProgress('Préparation des données locales…'); setStep('preparing');
    try {
      const result = await invoke('start_local_setup');
      if (result.setup_required) { setMode('local'); setStep('admin'); }
      else { setStep('complete'); }
    } catch (reason) { setStep('welcome'); setError(String(reason)); }
    finally { setBusy(false); }
  };

  const testRemote = async () => {
    setBusy(true); setError(''); setRemoteTested(false);
    try { await invoke('test_remote_api', { url: apiUrl }); setRemoteTested(true); }
    catch (reason) { setError(String(reason)); }
    finally { setBusy(false); }
  };

  const configureRemote = async () => {
    if (!remoteTested) return;
    setBusy(true); setError('');
    try { await invoke('apply_remote_mode', { url: apiUrl }); }
    catch (reason) { setError(String(reason)); setBusy(false); }
  };

  const createAdmin = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    if (account.password !== account.confirm) { setError('Les deux mots de passe ne correspondent pas.'); setBusy(false); return; }
    try {
      await invoke('create_initial_admin', { input: { identifiant: account.identifiant, email: account.email, motDePasse: account.password } });
      setAccount({ identifiant: '', email: '', password: '', confirm: '' });
      setStep('complete');
    } catch (reason) { setError(String(reason)); }
    finally { setBusy(false); }
  };

  return <main className="min-h-screen bg-[var(--color-bg)] px-4 py-8 sm:px-8" aria-labelledby="setup-title">
    <div className="mx-auto max-w-3xl">
      <header className="mb-8 flex items-center gap-3"><img src={logo} alt="" className="h-12 w-12 object-contain"/><div><p className="text-xs uppercase tracking-[.18em] text-[var(--color-fg-muted)]">GALAXAM SIRH</p><h1 id="setup-title" className="text-2xl font-bold">Bienvenue</h1></div></header>
      <nav aria-label="Étapes de configuration" className="mb-6 flex flex-wrap gap-2 text-xs">{['Bienvenue', 'Connexion', 'Administrateur', 'Terminé'].map((label, index) => <span key={label} aria-current={(step === 'welcome' && index === 0) || (['remote','preparing'].includes(step) && index === 1) || (step === 'admin' && index === 2) || (step === 'complete' && index === 3) ? 'step' : undefined} className="border border-[var(--color-border)] px-3 py-2">{index + 1}. {label}</span>)}</nav>
      {error && <div role="alert" className="mb-4 border border-[var(--color-danger-500)] p-3 text-sm">{error}</div>}
      {step === 'welcome' && <Card padding className="space-y-5">
        <div><h2 className="text-xl font-semibold">Configurez votre espace de travail</h2><p className="mt-2 text-sm text-[var(--color-fg-muted)]">Choisissez une base locale sur cet ordinateur ou une connexion au serveur de votre organisation. Vous pourrez changer de connexion plus tard.</p></div>
        <div className="grid gap-3 sm:grid-cols-2">
          <button type="button" onClick={() => setMode('local')} aria-pressed={mode === 'local'} className={`border p-4 text-left focus-visible:outline focus-visible:outline-2 ${mode === 'local' ? 'border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)]' : 'border-[var(--color-border)]'}`}><Database/><strong className="mt-3 block">Cet ordinateur <span className="text-xs">Recommandé</span></strong><span className="mt-1 block text-sm text-[var(--color-fg-muted)]">Base locale privée, prête à l'emploi.</span></button>
          <button type="button" onClick={() => setMode('remote')} aria-pressed={mode === 'remote'} className={`border p-4 text-left focus-visible:outline focus-visible:outline-2 ${mode === 'remote' ? 'border-[var(--color-brand-500)] bg-[var(--color-bg-subtle)]' : 'border-[var(--color-border)]'}`}><Globe2/><strong className="mt-3 block">Serveur de l'organisation</strong><span className="mt-1 block text-sm text-[var(--color-fg-muted)]">Connexion à une API GALAXAM existante.</span></button>
        </div>
        {mode === 'remote' && <div className="space-y-3"><Input label="Adresse HTTPS de l'API" type="url" value={apiUrl} onChange={(event) => { setApiUrl(event.target.value); setRemoteTested(false); }} placeholder="https://sirh.exemple.mg" autoComplete="url"/><Button onClick={testRemote} disabled={busy}>{busy ? 'Vérification…' : 'Tester la connexion'}</Button>{remoteTested && <p role="status" className="text-sm text-[var(--color-success-600)]">API GALAXAM disponible.</p>}</div>}
        <div className="flex justify-end">{mode === 'local' ? <Button onClick={configureLocal} disabled={busy}>{busy ? 'Préparation…' : 'Continuer'}</Button> : <Button onClick={configureRemote} disabled={busy || !remoteTested}>{busy ? 'Enregistrement…' : 'Utiliser ce serveur'}</Button>}</div>
      </Card>}
      {step === 'preparing' && <Card padding className="space-y-4" role="status" aria-live="polite"><div className="flex items-center gap-3"><LoaderCircle className="shrink-0 animate-spin"/><div><h2 className="font-semibold">Préparation de GALAXAM</h2><p className="text-sm text-[var(--color-fg-muted)]">{progress}</p></div></div><p className="text-xs text-[var(--color-fg-muted)]">Temps écoulé : {Math.floor(elapsed / 60)} min {String(elapsed % 60).padStart(2, '0')} s. La première installation peut prendre plusieurs minutes.</p><ol className="list-decimal space-y-2 pl-6 text-sm"><li>Vérification du stockage privé</li><li>Initialisation neuve ou vérification de la base</li><li>Démarrage de l'API locale</li></ol></Card>}
      {step === 'admin' && <Card padding><div className="mb-5 flex gap-3"><ShieldCheck/><div><h2 className="text-xl font-semibold">Créez le compte administrateur</h2><p className="mt-1 text-sm text-[var(--color-fg-muted)]">Ce compte protège l'accès à cette installation locale. Aucun mot de passe n'est prérempli ni enregistré en clair.</p></div></div><form onSubmit={createAdmin} className="space-y-4"><Input label="Nom d'utilisateur" required maxLength={100} autoComplete="username" placeholder="Ex. admin.galaxam" value={account.identifiant} onChange={(e) => setAccount({...account, identifiant:e.target.value})}/><Input label="Adresse e-mail" required type="email" autoComplete="email" placeholder="nom@entreprise.mg" value={account.email} onChange={(e) => setAccount({...account, email:e.target.value})}/><Input label="Mot de passe" required type={showPassword ? 'text' : 'password'} autoComplete="new-password" placeholder="Choisissez un mot de passe sécurisé" hint="Au moins 8 caractères, avec une majuscule, une minuscule et un chiffre." iconRight={<button type="button" onClick={() => setShowPassword((visible) => !visible)} className="flex min-h-10 min-w-10 items-center justify-center rounded-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]" aria-label={showPassword ? 'Masquer le mot de passe' : 'Afficher le mot de passe'} aria-pressed={showPassword}>{showPassword ? <EyeOff size={16}/> : <Eye size={16}/>}</button>} value={account.password} onChange={(e) => setAccount({...account, password:e.target.value})}/><Input label="Confirmer le mot de passe" required type={showConfirm ? 'text' : 'password'} autoComplete="new-password" placeholder="Saisissez à nouveau le mot de passe" iconRight={<button type="button" onClick={() => setShowConfirm((visible) => !visible)} className="flex min-h-10 min-w-10 items-center justify-center rounded-sm focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--color-focus-ring)]" aria-label={showConfirm ? 'Masquer la confirmation du mot de passe' : 'Afficher la confirmation du mot de passe'} aria-pressed={showConfirm}>{showConfirm ? <EyeOff size={16}/> : <Eye size={16}/>}</button>} value={account.confirm} onChange={(e) => setAccount({...account, confirm:e.target.value})}/><div className="flex justify-end"><Button type="submit" disabled={busy}>{busy ? 'Création…' : 'Créer mon compte'}</Button></div></form></Card>}
      {step === 'complete' && <Card padding className="space-y-4 text-center"><Check size={38} className="mx-auto text-[var(--color-success-600)]"/><h2 className="text-xl font-semibold">Configuration terminée</h2><p className="text-sm text-[var(--color-fg-muted)]">{mode === 'remote' ? 'Le serveur distant est configuré.' : 'Votre espace local est prêt et le compte administrateur est créé.'} Connectez-vous pour commencer.</p><Button onClick={onComplete}>Continuer vers la connexion</Button></Card>}
    </div>
  </main>;
}
