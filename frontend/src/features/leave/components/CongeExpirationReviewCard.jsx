import { useCallback, useEffect, useState } from 'react';
import { AlertTriangle, CalendarClock, Check, RotateCcw } from 'lucide-react';
import congeService from '@/features/leave/api/congeService';
import { getErrorMessage } from '@/api/apiClient';
import Alert from '@/components/ui/feedback/Alert';
import Badge from '@/components/ui/data-display/Badge';
import Button from '@/components/ui/forms/Button';
import Input, { Checkbox, Textarea } from '@/components/ui/forms/Input';
import Modal from '@/components/ui/overlays/Modal';
import Loading from '@/components/ui/feedback/Loading';
import { useToast } from '@/components/ui/feedback/Toast';

const formatDate = (value) => {
  if (!value) return '—';
  const [year, month, day] = String(value).slice(0, 10).split('-').map(Number);
  if (!year || !month || !day) return value;
  return new Date(year, month - 1, day).toLocaleDateString('fr-FR');
};
const formatDays = (value) => Number(value || 0).toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 });

export default function CongeExpirationReviewCard({ idEmploye, canValidate = false, onSuccess }) {
  const toast = useToast();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [action, setAction] = useState(null);
  const [motif, setMotif] = useState('');
  const [prochaineDate, setProchaineDate] = useState('');
  const [planificationVerifiee, setPlanificationVerifiee] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await congeService.getExamenExpiration(idEmploye));
      setError('');
    } catch (err) {
      setError(getErrorMessage(err, 'Impossible de charger les droits à examiner.'));
    } finally {
      setLoading(false);
    }
  }, [idEmploye]);

  useEffect(() => { load(); }, [load]);

  const close = () => {
    if (saving) return;
    setAction(null);
    setMotif('');
    setProchaineDate('');
    setPlanificationVerifiee(false);
  };

  const submit = async (event) => {
    event.preventDefault();
    if (motif.trim().length < 10) return;
    if (action === 'REPORTER' && !prochaineDate) return;
    setSaving(true);
    try {
      const payload = { motif: motif.trim() };
      const result = action === 'REPORTER'
        ? await congeService.reporterExamenExpiration(idEmploye, { ...payload, prochaineDate })
        : await congeService.confirmerExpiration(idEmploye, { ...payload, planificationVerifiee });
      setData(result);
      setError('');
      setAction(null);
      setMotif('');
      setProchaineDate('');
      setPlanificationVerifiee(false);
      toast.success(action === 'REPORTER' ? 'Examen reporté avec motif enregistré.' : 'Expiration confirmée et mouvement enregistré.');
      onSuccess?.();
    } catch (err) {
      setError(getErrorMessage(err, 'La décision n’a pas pu être enregistrée.'));
    } finally {
      setSaving(false);
    }
  };

  const jours = Number(data?.joursAExaminer || 0);
  const reporte = data?.derniereDecision === 'REPORTE' && data?.prochaineDateExamen > data?.dateReference;
  const expire = data?.derniereDecision === 'EXPIRE';
  const dateMinimum = new Date();
  dateMinimum.setDate(dateMinimum.getDate() + 1);
  const minDate = new Date(dateMinimum.getTime() - dateMinimum.getTimezoneOffset() * 60000).toISOString().slice(0, 10);

  if (loading) return <div className="border border-[var(--color-border)] bg-[var(--color-surface)] p-4"><Loading size="sm" label="Vérification des droits à échéance…" /></div>;
  if (error && !data) return <Alert type="error" title="Examen des droits indisponible" message={error} />;
  if (!data || (jours <= 0 && !data.derniereDecision)) return null;

  return (
    <section className="border border-[var(--color-warning-500)]/40 bg-[var(--color-surface)]" aria-label="Droits de congé à examiner">
      <div className="flex flex-col gap-3 p-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="flex min-w-0 gap-3">
          <AlertTriangle size={19} className="mt-0.5 shrink-0 text-[var(--color-warning-500)]" />
          <div className="min-w-0 space-y-2">
            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-bold text-[var(--color-fg)]">{jours > 0 ? 'Droits à examiner' : 'Historique d’examen des droits'}</h3>
              <Badge variant={reporte ? 'warning' : expire ? 'success' : 'danger'}>
                {reporte ? 'Examen reporté' : expire ? 'Expiration confirmée' : 'Échéance atteinte'}
              </Badge>
            </div>
            {jours > 0 ? (
              <p className="text-xs text-[var(--color-fg-muted)]">
                <strong className="text-[var(--color-fg)]">{formatDays(jours)} j</strong>
                {' '}de droits disponibles acquis avant le {formatDate(data.dateSeuilPrescription)}.
              </p>
            ) : (
              <p className="text-xs text-[var(--color-fg-muted)]">
                Aucun droit n’est actuellement à examiner. La dernière décision reste conservée dans le registre.
              </p>
            )}
            {reporte && (
              <p className="flex items-start gap-1.5 text-xs text-[var(--color-fg-muted)]">
                <CalendarClock size={14} className="mt-0.5 shrink-0" />
                Prochaine revue : <strong className="text-[var(--color-fg)]">{formatDate(data.prochaineDateExamen)}</strong>
                {data.motifDerniereDecision && <span> · Motif : {data.motifDerniereDecision}</span>}
              </p>
            )}
            {expire && data.motifDerniereDecision && (
              <p className="text-xs text-[var(--color-fg-muted)]">Motif de la dernière décision : {data.motifDerniereDecision}</p>
            )}
            {jours > 0 && (
              <p className="text-xs text-[var(--color-fg-muted)]">
                L’échéance ne retire pas automatiquement ces jours. La décision doit être examinée par une personne habilitée.
              </p>
            )}
          </div>
        </div>
        {canValidate && jours > 0 && (
          <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
            <Button variant="outline" size="sm" onClick={() => setAction('REPORTER')} iconLeft={<RotateCcw size={14} />}>
              Reporter l’examen
            </Button>
            <Button variant="danger" size="sm" onClick={() => setAction('CONFIRMER')} iconLeft={<Check size={14} />}>
              Examiner l’expiration
            </Button>
          </div>
        )}
      </div>
      {Array.isArray(data.droitsCandidats) && data.droitsCandidats.length > 0 && (
        <div className="border-t border-[var(--color-border-subtle)]">
          <div className="hidden sm:grid grid-cols-[minmax(0,1fr)_minmax(0,1fr)_8rem] gap-3 bg-[var(--color-bg-subtle)] px-4 py-2 text-[10px] font-bold uppercase tracking-wide text-[var(--color-fg-muted)]">
            <span>Période acquise</span><span>Échéance calculée</span><span className="text-right">Jours concernés</span>
          </div>
          <div className="divide-y divide-[var(--color-border-subtle)]">
            {data.droitsCandidats.map((droit) => (
              <div key={`${droit.dateAcquisition}-${droit.dateEcheance}`} className="grid gap-1 px-4 py-2.5 text-xs sm:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_8rem] sm:items-center sm:gap-3">
                <span className="text-[var(--color-fg)]"><span className="sm:hidden text-[10px] font-semibold uppercase text-[var(--color-fg-muted)]">Acquis le · </span>{formatDate(droit.dateAcquisition)}</span>
                <span className="text-[var(--color-fg-muted)]"><span className="sm:hidden text-[10px] font-semibold uppercase">Échéance · </span>{formatDate(droit.dateEcheance)}</span>
                <span className="text-left font-semibold text-[var(--color-fg)] sm:text-right">{formatDays(droit.joursDisponibles)} j</span>
              </div>
            ))}
          </div>
          <p className="border-t border-[var(--color-border-subtle)] px-4 py-2 text-[11px] text-[var(--color-fg-muted)]">
            Les décisions s’appliquent à l’ensemble des droits candidats affichés ci-dessus.
          </p>
        </div>
      )}
      {error && <div className="px-4 pb-4"><Alert type="error" message={error} /></div>}

      <Modal
        isOpen={Boolean(action)}
        onClose={close}
        title={action === 'REPORTER' ? 'Reporter l’examen des droits' : 'Confirmer l’expiration des droits'}
        icon={action === 'REPORTER' ? <RotateCcw size={17} /> : <AlertTriangle size={17} />}
        footer={(
          <>
            <Button variant="outline" onClick={close} disabled={saving}>Annuler</Button>
            <Button variant={action === 'CONFIRMER' ? 'danger' : 'primary'} type="submit" form="conge-expiration-form"
              loading={saving} disabled={saving || motif.trim().length < 10 || (action === 'REPORTER' && !prochaineDate)
                || (action === 'CONFIRMER' && !planificationVerifiee)}>
              {action === 'REPORTER' ? 'Enregistrer le report' : 'Confirmer et débiter le solde'}
            </Button>
          </>
        )}
      >
        <form id="conge-expiration-form" onSubmit={submit} className="space-y-4">
          <Alert type={action === 'CONFIRMER' ? 'warning' : 'info'}>
            {action === 'CONFIRMER'
              ? <>La confirmation créera un mouvement d’expiration de <strong>{formatDays(jours)} j</strong>. Cette écriture sera conservée dans le registre des mouvements.</>
              : 'Le solde restera inchangé. Indiquez quand la DAF devra revoir ce dossier.'}
          </Alert>
          {action === 'REPORTER' && (
            <Input label="Prochaine date d’examen" type="date" required value={prochaineDate}
              min={minDate}
              onChange={(event) => setProchaineDate(event.target.value)} />
          )}
          {action === 'CONFIRMER' && (
            <Checkbox
              checked={planificationVerifiee}
              onChange={(event) => setPlanificationVerifiee(event.target.checked)}
              label="J’ai vérifié la planification des congés et les éléments de service ; aucun empêchement imputable à l’employeur n’est identifié."
            />
          )}
          <Textarea label="Motif de la décision" required minLength={10} rows={3} value={motif}
            placeholder={action === 'REPORTER' ? 'Ex. Congés non planifiés en raison des besoins de service…' : 'Expliquez les éléments examinés avant de confirmer…'}
            onChange={(event) => setMotif(event.target.value)}
            hint="Au moins 10 caractères. Le motif sera conservé dans l’historique de décision." />
        </form>
      </Modal>
    </section>
  );
}
