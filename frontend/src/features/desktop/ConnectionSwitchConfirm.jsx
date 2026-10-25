import { ArrowRight, Database, Globe2, X } from 'lucide-react';
import Button from '@/components/ui/forms/Button';

export default function ConnectionSwitchConfirm({ target, busy, onCancel, onConfirm }) {
  const remote = target.mode === 'remote';
  return (
    <div className="fixed inset-0 z-[120] grid place-items-center bg-black/50 p-4" role="presentation">
      <section role="alertdialog" aria-modal="true" aria-labelledby="connection-switch-title" aria-describedby="connection-switch-description" className="w-full max-w-lg space-y-5 rounded-none border border-[var(--color-border)] bg-[var(--color-surface)] p-5 shadow-xl sm:p-6">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 id="connection-switch-title" className="text-lg font-semibold">Changer la connexion</h2>
            <p className="mt-1 text-sm text-[var(--color-fg-muted)]">Vérifiez la destination avant de continuer.</p>
          </div>
          <button type="button" onClick={onCancel} disabled={busy} aria-label="Annuler" className="rounded-none p-2 text-[var(--color-fg-muted)] hover:bg-[var(--color-bg-subtle)] disabled:opacity-50"><X size={18} /></button>
        </div>
        <div className="flex items-center gap-3 rounded-none border border-[var(--color-border)] bg-[var(--color-bg-subtle)] p-3">
          {remote ? <Globe2 size={20} aria-hidden="true" /> : <Database size={20} aria-hidden="true" />}
          <div className="min-w-0"><p className="font-semibold">{remote ? 'Serveur distant' : 'Base locale'}</p>{remote && <p className="truncate text-sm text-[var(--color-fg-muted)]">{target.address}</p>}</div>
        </div>
        <div id="connection-switch-description" className="space-y-2 text-sm text-[var(--color-fg-muted)]">
          <p>Votre session actuelle sera fermée. Vous devrez vous reconnecter sur {remote ? 'le serveur distant' : 'la base locale'}.</p>
          <p>Les données locales et distantes restent séparées : aucune donnée ne sera effacée, copiée ou transférée.</p>
          <p>GALAXAM redémarrera pour appliquer ce changement.</p>
        </div>
        <div className="flex flex-col-reverse justify-end gap-2 sm:flex-row">
          <Button autoFocus variant="secondary" onClick={onCancel} disabled={busy}>Annuler</Button>
          <Button onClick={onConfirm} disabled={busy} iconRight={<ArrowRight size={15} />}>{busy ? 'Changement en cours…' : `Passer ${remote ? 'au serveur distant' : 'à la base locale'}`}</Button>
        </div>
      </section>
    </div>
  );
}
